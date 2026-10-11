# SGSPE · Fase 5, plan 5.0 (documentos): plan de implementación

> **Para agentes:** SUB-SKILL OBLIGATORIA: usar superpowers:subagent-driven-development (recomendada) o
> superpowers:executing-plans para ejecutar este plan tarea por tarea. Los pasos usan casillas (`- [ ]`) para el
> seguimiento. Cada tarea sigue el ciclo de superpowers:test-driven-development aplicado a documentos: una comprobación
> que falla, los cambios exactos, la comprobación y las pruebas del modelo en verde, commit.

**Goal:** Dejar `docs/` al día con la especificación de F5 antes de tocar el modelo y el código: los IDs nuevos
(ASM-110 a ASM-119 y GAP-033) con los totales de §20.1 (538); las contradicciones C1 a C15 resueltas en cada documento;
el contrato completo de WF-P (P1 a P9, orden y candados), WF-G (G1 a G4), la entrega única del aporte, la aceptación de
una solicitud como aporte, el alcance por proyecto con cadenas y padres alternativos, los avisos de aprobación de F5,
los KPI-INV y el tablero `investment`; y el roadmap, la trazabilidad, la historia y `CLAUDE.md` al día. Los planes 5.1,
5.2a, 5.2b y 5.3 leen estos documentos como fuente de verdad.

**Architecture:** Solo documentos y una prueba del lector de documentos. Cada tarea reemplaza texto exacto de uno o más
documentos (bloques «reemplazar … por …») y empieza con una comprobación en Python de solo biblioteca estándar que falla
mientras falte el cambio; las pruebas del modelo (`model/test_design_docs.py`, `test_model.py`, `test_apply_roles.py`),
`apply.py --validate-only` y `tools/check_traceability.py` siguen en verde después de cada tarea, porque
`model/design_docs.py` lee las fichas, los catálogos, la matriz de permisos, los workflows, las automatizaciones y los
tipos de documento de estos mismos archivos, y `test_model.py` y `test_apply_roles.py` comparan el modelo de F4
(`model/model.json`, `model/roles.json`) con ellos. Ningún archivo de `src/` ni de `model/*.json` cambia: el modelo de
F5 es el plan 5.1.

**Tech Stack:** Markdown (sin prettier: `.prettierignore` excluye `docs/`, `*.md` y `model/`); Python 3.11 o superior,
solo biblioteca estándar (`model/` y `tools/`); `yarn format:check` (prettier) como compuerta del repositorio; husky y
commitlint para los mensajes.

**Spec:** `docs/superpowers/specs/2026-10-11-fase-5-design.md`, §6 («Cambios en los documentos (plan 5.0)») y las
secciones que cita: §2 (decisiones del usuario, valores aceptados y elecciones), §3.1 a §3.10, §4.2 (permisos), §5
(pruebas), §7 (reparto, medición y definición de terminado) y §8 (consecuencias y riesgos).

## Global Constraints

- **Repositorio y rama.** Todo en `~/IdeaProjects/sgspe`, en la rama `docs/fase-5-0-documentos` creada desde `main`
  con la especificación de F5 ya integrada; si la rama `docs/fase-5-spec` todavía no está en `main`, desde ella (solo
  agrega la especificación y los planes de F5, así que los bloques de este plan aplican igual). Las tareas paralelas
  (grupo 1 de «Orden y paralelismo») trabajan cada una en su worktree desde la rama ya con la Tarea 1.
- **El diseño manda.** `docs/` es la fuente de verdad y `docs/requirements-analysis.md` el único lugar donde se definen
  IDs: ASM-110 a ASM-119 y GAP-033 los define la Tarea 1 y las demás solo los citan. Ninguna brecha upstream nueva
  (UPS-29 queda libre).
- **Texto exacto.** Cada bloque «reemplazar» aparece una sola vez en el documento en el momento de aplicarlo; si no
  aparece, la rama no es la esperada: se detiene la tarea y se avisa, nunca se adapta el bloque a mano. Los bloques
  «por» ya están envueltos a 120 columnas en la prosa; las filas de tabla van en una línea, como en los documentos.
- **Lo que el modelo de F4 no puede ver todavía.** `test_model.py` (con `PHASE = "F4"`) compara sin filtro de fase la
  descripción de cada objeto y campo de F2 a F4, los únicos y las acciones de cada ficha, y `test_apply_roles.py`
  compara cada celda de la matriz de un objeto de F2 a F4 con `model/roles.json`. Por eso en las fichas de F2 a F4 solo
  se agregan viñetas de notas y filas de relación con «desde F5» en la nota, nunca se cambia la descripción de su
  objeto o de un campo ni su viñeta «Únicos», y en la matriz solo cambian celdas de objetos de F5. Lo que F5 cambia en
  esas formas leídas (el único de `delivery.social_contribution`, `u` sobre `topic` para `PROJECT_ADMIN` y
  `PROJECT_MANAGER`) se escribe como texto con su fase y lo pasa a la forma leída el plan 5.1, junto con `model.json` y
  `roles.json` («Dependencias de otros planes»).
- **Idioma.** Español (es-PE) en documentos, comentarios y mensajes; nombres técnicos en inglés (ADR-0009); valores ENUM
  en español MAYÚSCULAS sin tildes.
- **Datos.** Ningún nombre real de persona; los ejemplos son ficticios («Proyecto Ejemplo A», «Asociación Ejemplo»,
  «Programa Ejemplo», `@sgspe.test`).
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
  `Claude-Session: https://claude.ai/code/session_017MAgYcNauGp6cC3ekX1h7U`. `git add` solo con rutas explícitas. Nunca
  se versionan `initial-promp.md`, `arch-*.md`, `tech-landing.txt` ni nada de `~/Documents/gspe`.
- **Push y PR** solo en la Tarea 10.

## Review Focus

1. **Lo que leen `design_docs.py` y las pruebas del modelo de F4.** Una ficha de F2 a F4 que cambia la descripción de
   su objeto o de un campo, su viñeta «Únicos» o sus acciones, o una celda de la matriz de un objeto de F2 a F4, rompe
   `test_model.py` o `test_apply_roles.py` contra el modelo de F4. Lo que espera esas formas se escribe como texto con
   «desde F5» (decisión 2 de este plan). Pruebas: la suite completa del modelo en cada tarea y
   `test_las_fichas_de_f5_traen_lo_que_suma_el_plan_5_0` (Tarea 2), que fija lo que sí cambia en la forma leída
   (`item_kind`, `contribution_topic`, `project_priority_topic` con fase F5 y AUT-04 tras UPS-23).
2. **Un mismo código, el mismo campo en todos los documentos.** P1 a P9 y G1 a G4 dan un código y un campo
   (`ITEMS_REQUIRED` sobre `contribution_item`, `PRIORITY_NOT_MET` sobre `topic`, `DELIVERY_FROZEN` sobre
   `social_contribution`…) que `workflows.md`, `api.md` §3.3 y §4.1, `domain-model.md` §7.2 y `ux/screens.md` S44
   deben repetir igual, porque 5.2a, 5.2b y 5.3 los copian de documentos distintos. Prueba: la comprobación cruzada de
   códigos del Paso 1 de la Tarea 10.
3. **IDs y totales.** §20.1 suma 538 (GAP 33, ASM 119) contados de las filas, cada ASM nuevo se define una sola vez y
   lo cita al menos otro documento, y las filas de la trazabilidad no cambian sus estados: las de INV ya están en
   «Diseñado; Fuera del MVP» y este plan solo completa sus columnas (contadas por subcadena, 238 Diseñado, 46
   Implementado, 8 Decisión pendiente, 21 Upstream, 0 Hueco y 82 Fuera del MVP antes y después; decisión 11).
   Pruebas: la comprobación de la Tarea 1 y el conteo del Paso 1 de la Tarea 10.
4. **Fase de cada pieza.** Nada de lo que este plan agrega a una ficha, una tabla o una pantalla de F2 a F4 se lee como
   vigente en F4: la fila M2M `project_priority_topic` lleva «desde F5» en su nota (`design_docs.FROM_PHASE` toma la
   primera marca), y las notas nuevas de `project`, `delivery`, `task` y `stakeholder_request` dicen «desde F5». Prueba:
   `test_las_fichas_de_f5_traen_lo_que_suma_el_plan_5_0` (la relación de `project` en F5) y `test_totales_de_las_fichas`
   (los totales de F2 y F3 no cambian).
5. **El alcance de la entrega con dos padres dicho igual en todas partes.** `security.md` §9.7, `domain-model.md` §4.7,
   ADR-0029, la especificación 4.4 y ASM-106 y ASM-114 deben decir lo mismo: una entrega se lee por su compromiso o su
   aporte y una entrega sin ninguno de los dos solo la leen `ADMIN` y quien tiene `READ_ALL_PROJECTS`; «sin padre, del
   tenant» queda solo para `locality_profile`. Prueba: la búsqueda de «`delivery` sin compromiso» del Paso 1 de la
   Tarea 10, que solo admite las menciones con «hasta F4».

## Decisiones de este plan frente a la especificación y los documentos

| # | Hallazgo | Decisión |
|---|---|---|
| 1 | La especificación §7 llama a la rama de este plan `docs/fase-5-documentos`; el encargo del usuario la llama `docs/fase-5-0-documentos` | `docs/fase-5-0-documentos`, como pidió el usuario |
| 2 | El único de `delivery.social_contribution` (decisión 2) y `u` sobre `topic` para `PROJECT_ADMIN` y `PROJECT_MANAGER` (§4.2) cambian formas que `test_model.py` y `test_apply_roles.py` comparan con el modelo de F4 sin filtro de fase (la viñeta «Únicos» de una ficha de F3 y una celda de §7.4 de un objeto de F2) | Se escriben como texto con «desde F5» (en la viñeta «Únicos» de `delivery`, sin la forma «`unique` en» que lee el parser, y en una nota de §7.7); el plan 5.1 los pasa a la forma leída («`unique` en `social_contribution`», que el modelo declara como `"unique": true` en la relación, y `Ru` en §7.4) en el mismo commit que `model.json` y `roles.json` (plan 5.1, Tarea 3, Paso 4a; aquí, Tareas 2 y 5) |
| 3 | La especificación §3.9 nombra el filtro de campaña del tablero `investment` como `campaign`, pero el filtro común de todos los tableros es `explorationCampaign` (`api.md` §4.11, `dashboards.md` §3.1) | Los documentos usan `explorationCampaign`, el parámetro existente, y suman `program` y `year`, que solo aplican los KPI-INV; si 5.2b prefiriera `campaign`, sería un segundo nombre del mismo filtro (Tareas 6 y 7) |
| 4 | §6 pide que RF-INV-001 a 004 y RN-INV-001 a 006 «pasen a Diseñado con sus pruebas», pero ya están en «Diseñado» o «Diseñado; Fuera del MVP» | Los estados no cambian (los totales por estado tampoco); sus filas ganan el módulo, la entidad y las pruebas de F5, y RF-GRV-004, RQ-SEC-006 y RQ-DSH-030 también (Tarea 8) |
| 5 | ASM-106 dice que «`delivery` sin compromiso» la lee todo quien tiene READ, lo que C3 corrige desde F5 | ASM-106 no se retira: se acota a «hasta F5» y cita ASM-114; §20.4 lo dice en sus observaciones (Tarea 1) |
| 6 | C2 pide que «la especificación 4.4 §3.1» sume las cadenas, y ADR-0029 nombra 31 raíces, 19 hijos y dos hijos con padre opcional | La especificación 4.4 y ADR-0029 son historia: ganan una nota fechada de F5 que remite a ASM-114 y `security.md` §9.7, sin reescribir lo decidido (Tarea 5) |
| 7 | La especificación no nombra la fuente de wasichai-notifications de los avisos de aprobación de WF-G y WF-P | `sgspe.approvals`, la de todo `approval_pending` (Tarea 3, «Avisos») |
| 8 | La especificación no lista `deliverables.md` ni `platform-fit.md` en §6, pero el primero describe la sección «Apoyo entregado» de T2 y el segundo, la tabla de encaje de BC-10 | `deliverables.md` §6 (fila «Apoyo entregado») y `platform-fit.md` (fila BC-10) ganan los ítems por tipo, la entrega única y el acta tras UPS-23 (Tarea 8) |
| 9 | `register_procurement` de WF-P exigía ítems; la decisión 1 los exige al aprobar (P9) | Su guarda pasa a «—» con la explicación, y `ContributionTotalsListener` deja de «alimentar `register_procurement`»: escribe el total aprobado en `approve` y `approve_direct` (C13) (Tarea 3) |
| 10 | La especificación nombra `ProgramTotalsRecalculator` pero no el recalculador del total de un aporte | Los documentos dicen «los recalculadores de `sgspe.investment`» sin nombrar el del aporte; lo nombra 5.2a (Tareas 3 y 4) |
| 11 | La tabla de conteos de la trazabilidad (§8) dice 236 Diseñado, 10 Decisión pendiente y 22 Upstream, pero sus filas, contadas por subcadena del estado, dan 238, 8 y 21 desde antes de F5 | Este plan no cambia estados ni toca §8: la comprobación de la Tarea 8 exige que los conteos de las filas sigan siendo 238, 46, 8, 21, 0 y 82. Reconciliar §8 queda fuera de F5 |

Huecos y contradicciones que cierra cada tarea: C1 (Tareas 2, 3 y 4), C2 (4 y 5), C3 (2, 4 y 5), C4 (3, 4, 7 y 8), C5
(5 y 7), C6 (1 y 5), C7 (3 y 4), C8 (1, 2 y 3), C9 (1 y 6), C10 (6), C11 (3), C12 (4), C13 (3, 4 y 5), C14 (3, 4 y 5) y
C15 (7).

---

## Mapa de archivos

| Ruta | Qué cambia | Tarea |
|---|---|---|
| `docs/requirements-analysis.md` | RF-INV-002, RN-INV-005, §10 («Tipo de ítem del aporte»), §18.1 (GAP-033), §19 (ASM-092, ASM-106, ASM-110 a ASM-119), §20.1 y §20.4 | 1 |
| `docs/database-model.md` | §4 (`item_kind`); §15 (introducción, diagrama y las diez fichas); `delivery`, `project`, `topic`, `task` y `stakeholder_request` (notas y la relación M2M de F5) | 2 |
| `model/test_design_docs.py` | 79 claves ENUM y la prueba de lo que suma el plan 5.0 | 2 |
| `docs/workflows.md` | Regla 10, transiciones de comando, resumen, WF-E3, WF-G (G1 a G4), WF-P (P1 a P9, orden, candados, entrega), listeners, avisos, pruebas del modelo y por workflow | 3 |
| `docs/domain-model.md`, `docs/architecture.md` | §4.7, §6.10, §6.12, §7.2, §7.4, §7.5 y §8.2; §5.2 (`sgspe.investment`) | 4 |
| `docs/security.md`, `docs/adr/0029-alcance-de-lectura-por-proyecto.md`, `docs/superpowers/specs/2026-10-10-alcance-por-proyecto-design.md` | §5.3, §7.1, §7.6, §7.7, §8.1, §8.2 y §9.7; la nota de F5 del ADR y de la especificación 4.4 | 5 |
| `docs/api.md` | §3.3, §4.1, §4.5, §4.6, §4.8, §4.9 y §4.11 | 6 |
| `docs/dashboards.md`, `docs/ux/screens.md` | §3.1, §3.2, §4.7, §5.10 y §11; S11, S42, S43, S44, S48, S57 y el tablero de inversión social | 7 |
| `docs/roadmap.md`, `docs/requirements-traceability.md`, `docs/deliverables.md`, `docs/platform-fit.md`, `docs/glossary.md` | F5 y brechas por fase; filas de INV, RF-GRV-004, RQ-SEC-006 y RQ-DSH-030; «Apoyo entregado»; BC-10; «Comprometido», «Tema priorizado», «Desviación de lineamientos» y «Tipo de ítem del aporte» | 8 |
| `docs/HISTORY.md`, `CLAUDE.md` | Entrada del plan 5.0; fila de `sgspe.investment` en «Dónde está cada cosa» | 9 |

## Interfaces con los otros planes de F5

Lo que este plan deja para los siguientes (todos parten de `main` con 5.0 integrado):

- **5.1 (modelo y alcance).** Las fichas de §15 con `contribution_item.item_kind` (ENUM `item_kind`, requerido), la
  relación `contribution_topic` (`social_contribution.topic` → `topic`, opcional) y las notas de cada hijo; la fila
  `project_priority_topic` (MANY_TO_MANY de `project` a `topic`, «desde F5») y su inversa en `topic`; el catálogo «Tipo
  de ítem del aporte» (BIEN · SERVICIO · DINERO · MANO DE OBRA) y su clave `item_kind` en §4 (79 claves). Las tablas de
  WF-G y WF-P no cambian de forma ni de roles; WF-E3 nombra `SOCIAL_INVESTMENT_MANAGER` en `accept` y `close_accepted`
  sin «(desde F5)». La matriz de §7.7 da CRUD sobre `contribution_item` a SMG y CRO y CRU sobre `receipt` a CRO.
  `security.md` §9.7 fija las siete reglas de `ArchitectureTest` y la forma de `ProjectScope.CHILDREN`. AUT-04 sigue
  «F5, tras UPS-23».
- **5.2a (aportes).** Los códigos y campos de `workflows.md` WF-P (P1 a P9, «Otras reglas»), `api.md` §3.3, §4.1, §4.6
  y §4.8; los listeners y avisos de `workflows.md` y `domain-model.md` §7.5 (`ContributionTotalsListener` en
  `approve` y `approve_direct`, modo «al crear» de `ApprovalNoticeListener`, `RequestClosureListener` en `close` de
  WF-P); ASM-110 a ASM-112 y ASM-116 a ASM-119.
- **5.2b (programas y tablero).** WF-G (G1 a G4 y las reglas de los hijos), `ProgramTotalsListener`, KPI-INV-01 a 04
  (`dashboards.md` §4.7 y §5.10, filtros de §3.1), el conjunto «comprometido» de §3.2, ASM-113 a ASM-115 y la
  «Definición de terminado» de `roadmap.md` F5, que también pide anotar la medición en «Estado».
- **5.3 (portal).** `ux/screens.md` S11 (temas priorizados), S42, S43, S44 («Antes de aprobar» con P1 a P9, ítems por
  tipo, entrega única, «Subir acta de recepción firmada»), S48 y S57 (aceptar como aporte solo con CREATE sobre
  `social_contribution`) y el tablero de inversión social.

## Orden y paralelismo

Cada tarea del grupo 1 puede hacerse en su propio worktree cuando la Tarea 1 está integrada en
`docs/fase-5-0-documentos`: tocan archivos distintos y ningún bloque «reemplazar» de una depende del texto que deja otra
(los bloques se probaron aplicando cada tarea sola sobre la Tarea 1, con las pruebas del modelo y la trazabilidad en
verde). Al integrar cada worktree en la rama del plan, `git merge --no-ff` sin conflictos.

| Grupo | Tareas | Necesita | Por qué juntas |
|---|---|---|---|
| 0 | 1 | La rama creada | Define los IDs que citan las demás |
| 1 | 2, 3, 4, 5, 6, 7, 8 | La Tarea 1 integrada | Archivos disjuntos: `database-model.md` y `test_design_docs.py`; `workflows.md`; `domain-model.md` y `architecture.md`; `security.md`, ADR-0029 y la especificación 4.4; `api.md`; `dashboards.md` y `ux/screens.md`; `roadmap.md`, la trazabilidad, `deliverables.md`, `platform-fit.md` y el glosario |
| 2 | 9, después 10 | Todas las anteriores | La historia resume lo integrado; la 10 verifica, hace push y abre el PR |

---

### Tarea 1: IDs de F5 en el análisis

Los demás documentos citan los IDs nuevos, así que el análisis va primero: §10 suma el catálogo «Tipo de ítem del
aporte», RF-INV-002 nombra los temas priorizados, §18.1 suma GAP-033, §19 suma ASM-110 a ASM-119 y acota ASM-092 y
ASM-106, y §20.1 pasa a 538 IDs.

**Archivos:**
- Modificar: `docs/requirements-analysis.md` (RF-INV-002, RN-INV-005, §10, §18.1, §19, §20.1, §20.4)
- Probar: `tools/check_traceability.py`, `model/test_design_docs.py` (`catalogs()` lee §10)

**Interfaces:**
- Consume: la especificación §6 («IDs nuevos») y §2.
- Produce: ASM-110 (monto del aporte y lo comprometido), ASM-111 (una sola entrega), ASM-112 (lineamientos por tipo de
  ítem y tema), ASM-113 (ejecución de programas a mano), ASM-114 (alcance de hijos de hijos y de dos padres), ASM-115
  (sinergia del programa), ASM-116 (acta de recepción tras UPS-23), ASM-117 (aceptación como aporte), ASM-118 (montos y
  conteos de F5), ASM-119 (autoridad del tope), GAP-033 (caseríos sin beneficio laboral); el catálogo «Tipo de ítem del
  aporte» con BIEN · SERVICIO · DINERO · MANO DE OBRA.

- [ ] **Paso 1: La comprobación falla**

```bash
python3 - <<'PY'
import re
text = open('docs/requirements-analysis.md', encoding='utf-8').read()
checks = [('| ASM-%03d |' % n, True) for n in range(110, 120)] + [
    ('| GAP-033 |', True),
    ('| Tipo de ítem del aporte | BIEN · SERVICIO · DINERO · MANO DE OBRA', True),
    ('| `GAP` | 33 |', True), ('| `ASM` | 119 |', True), ('| Total | 538 |', True),
    ('`project_priority_topic`', True),
    ('; los de aportes son un vínculo desde F5 |', False),
]
bad = [f"{'falta' if want else 'sobra'} {s!r}" for s, want in checks if (s in text) != want]
rows = re.findall(r'^\| (ASM|GAP)-\d{3} \|', text, re.M)
if rows.count('ASM') != 119 or rows.count('GAP') != 33:
    bad.append(f"filas: {rows.count('ASM')} ASM y {rows.count('GAP')} GAP, se esperan 119 y 33")
print('\n'.join(bad) or 'ok')
raise SystemExit(1 if bad else 0)
PY
```

Esperado: salida 1 con las líneas de lo que falta (`falta '| ASM-110 |'`, …, `sobra '; los de aportes son un vínculo
desde F5 |'`) y `filas: 109 ASM y 32 GAP, se esperan 119 y 33`.

- [ ] **Paso 2: RF-INV-002 y RN-INV-005**

En `docs/requirements-analysis.md`, reemplazar:

```markdown
; los de aportes son un vínculo desde F5 |
```

por:

```markdown
; desde F5, los temas priorizados de los aportes son el vínculo `project_priority_topic` del proyecto con `topic` (ASM-112) |
```

En `docs/requirements-analysis.md`, reemplazar:

```markdown
Se priorizan los aportes en agua y medio ambiente y los caseríos sin beneficio laboral |
```

por:

```markdown
Se priorizan los aportes en agua y medio ambiente y los caseríos sin beneficio laboral; la prioridad se mide por los temas priorizados del proyecto (ASM-112) y los caseríos esperan su dato (GAP-033) |
```

- [ ] **Paso 3: El catálogo «Tipo de ítem del aporte» en §10**

En `docs/requirements-analysis.md`, reemplazar:

```markdown
| Tipo de aporte | DONACION · APOYO EN ESPECIE | `social_contribution` | [DOC-02 p.3] [DOC-01 §9.3.1.2] |
```

por:

```markdown
| Tipo de aporte | DONACION · APOYO EN ESPECIE | `social_contribution` | [DOC-02 p.3] [DOC-01 §9.3.1.2] |
| Tipo de ítem del aporte | BIEN · SERVICIO · DINERO · MANO DE OBRA (propuestos; los leen los lineamientos de apoyo al aprobar, ASM-112) | `contribution_item` | [DOC-01 Tabla 9.2, §9.3.1.2] [DOC-02 p.4] |
```

- [ ] **Paso 4: GAP-033**

En `docs/requirements-analysis.md`, reemplazar:

```markdown
| GAP-032 | No se define cómo medir el impacto de los programas sociales | ¿Qué indicadores de impacto, con qué línea base y con qué meta? | Indicadores de programa con línea base y meta | RF-INV-003 |
```

por:

```markdown
| GAP-032 | No se define cómo medir el impacto de los programas sociales | ¿Qué indicadores de impacto, con qué línea base y con qué meta? | Indicadores de programa con línea base y meta | RF-INV-003 |
| GAP-033 | RN-INV-005 prioriza «los caseríos sin beneficio laboral», pero ningún objeto registra qué caseríos tienen beneficio laboral | ¿Qué caseríos no tienen beneficio laboral, quién lo registra y cada cuánto cambia? | No se evalúa: la prioridad de RN-INV-005 se mide solo por tema (ASM-112) | RN-INV-005 |
```

- [ ] **Paso 5: ASM-092 y ASM-106 se acotan**

En `docs/requirements-analysis.md`, reemplazar:

```markdown
(`exploration_campaign.social_support_cap`, `commitment.amount`, `delivery.delivered_amount`, `agreement.amount`) es un
```

por:

```markdown
(`exploration_campaign.social_support_cap`, `commitment.amount`, `delivery.delivered_amount`, `agreement.amount` y, desde F5, los de ASM-118) es un
```

En `docs/requirements-analysis.md`, reemplazar:

```markdown
(`evidence`, `early_warning_rule`, `export_log`, `delivery` sin compromiso, `locality_profile` sin estudio)
```

por:

```markdown
(`evidence`, `early_warning_rule`, `export_log`, `locality_profile` sin estudio y, hasta F4, `delivery` sin compromiso; desde F5 la entrega se lee por su compromiso o su aporte, ASM-114)
```

- [ ] **Paso 6: Los supuestos ASM-110 a ASM-119**

En `docs/requirements-analysis.md`, reemplazar:

```markdown
| ASM-109 | Varios clientes en una instalación: `wasichai.organizations.separate-provisioning` encendido en producción; `MANAGE_TENANTS` solo en el rol `ADMIN` de la organización operadora, sembrado fuera de banda antes de encender el interruptor y nunca en `roles.json`; el alta y la subida de fase de cada tenant, con `provision_tenant.py` y `upgrade_tenants.py` sobre un inventario que no se versiona; los trabajos recorren los tenants en serie y se miden | Decisión del usuario del 2026-10-10 (decisión 1): el `ADMIN` de un cliente no crea ni borra tenants (WAS-ADR-0055) | Apagar el interruptor: el `ADMIN` de cada tenant vuelve a crear y borrar tenants | RQ-SEC-002, RQ-SEC-003, UPS-10 |
```

por:

```markdown
| ASM-109 | Varios clientes en una instalación: `wasichai.organizations.separate-provisioning` encendido en producción; `MANAGE_TENANTS` solo en el rol `ADMIN` de la organización operadora, sembrado fuera de banda antes de encender el interruptor y nunca en `roles.json`; el alta y la subida de fase de cada tenant, con `provision_tenant.py` y `upgrade_tenants.py` sobre un inventario que no se versiona; los trabajos recorren los tenants en serie y se miden | Decisión del usuario del 2026-10-10 (decisión 1): el `ADMIN` de un cliente no crea ni borra tenants (WAS-ADR-0055) | Apagar el interruptor: el `ADMIN` de cada tenant vuelve a crear y borrar tenants | RQ-SEC-002, RQ-SEC-003, UPS-10 |
| ASM-110 | El monto de un aporte es `total_amount`, la suma de `line_amount` de sus ítems, que se cargan en `requested`; la aprobación (`approve` o `approve_direct`) exige ítems (P9) y compara esa suma, leída en la transacción, con el tope de la campaña. Fuera de `requested` los ítems no se crean, cambian ni borran (`409 ITEMS_FROZEN`) y tampoco cambian los términos que juzgó la aprobación (`currency`, `contribution_type`, `topic`, `exploration_campaign`, `has_institutional_synergy`, `agreement`, `guideline_deviation_justification` y `project`, `409 CONTRIBUTION_FROZEN`): un cambio es otro aporte. Cuentan como comprometidos los aportes en `approved`, `procured`, `delivered` y `closed`, y el tope de la campaña suma los compromisos y los aportes comprometidos en su moneda, con el mismo candado `exploration_campaign:<id>` | Decisión del usuario del 2026-10-11 (decisión 1): WF-P exigía los ítems en `register_procurement`, después de aprobar, así que el tope se comparaba con un monto vacío | Guardar el monto aprobado en un campo propio y dejar que los ítems cambien después, con su propio control | RF-INV-001, RN-INV-003, RN-INV-006, ASM-063, `social_contribution`, `contribution_item`, `InvestmentWriteGuard`, `ContributionCapRule`, `CampaignCommitted`, `SupportPolicyGuard` |
| ASM-111 | Un aporte tiene como máximo una `delivery` (único `social_contribution`): se captura solo con el aporte en `procured` (`POST /api/sgspe/social-contributions/{id}/deliveries`), se corrige con `PATCH /api/sgspe/deliveries/{id}` mientras siga en `procured` y queda congelada, con sus vínculos de evidencia, desde `delivered` y en `cancelled` (`409 DELIVERY_FROZEN` sobre `social_contribution`). No lleva `delivered_amount` (`400 FIELD_NOT_ALLOWED`): el ejecutado de un aporte es su `total_amount`. Lo que se entrega en partes son aportes distintos | Decisión del usuario del 2026-10-11 (decisión 2): DOC-02 registra una entrega con su acta, foto y comprobante, y varias entregas pedirían un ejecutado parcial que ASM-063 no tiene | Varias entregas por aporte, con un ejecutado por entrega | RF-INV-001, ASM-063, `delivery`, `social_contribution`, `InvestmentWriteGuard` |
| ASM-112 | Los lineamientos de apoyo de un aporte se evalúan solos al aprobar. Cada ítem declara su tipo (`item_kind`: BIEN, SERVICIO, DINERO o MANO DE OBRA) y el aporte, su tema (`topic`); los temas priorizados del proyecto son el vínculo `project_priority_topic`, y un tema cuenta si es priorizado o desciende de uno (`parent_topic`, hasta 10 niveles). `policy_no_paid_labor` se incumple con un ítem MANO DE OBRA (`PAID_LABOR`), `policy_non_monetary_donations` con un ítem DINERO en una DONACION (`MONETARY_DONATION`) y `policy_priority_warning` con un aporte sin tema o con un tema que no cuenta (`PRIORITY_NOT_MET`); un proyecto sin temas priorizados no evalúa la prioridad y lo advierte (`PRIORITIES_NOT_CONFIGURED`). Cada incumplimiento bloquea sin `guideline_deviation_justification` y es advertencia con ella, que los cubre a todos; `contribution_type` se exige al aprobar. La sinergia no es un lineamiento: con `has_institutional_synergy` y `policy_synergy_requires_agreement` activa, el aporte exige un acuerdo en `active` y no VENCIDO (P6), sin justificación posible | Decisión del usuario del 2026-10-11 (decisión 3): RN-INV-001, RN-INV-002 y RN-INV-005 no tenían datos que leer | Una lista de lineamientos que marca a mano quien aprueba | RF-INV-002, RN-INV-001, RN-INV-002, RN-INV-004, RN-INV-005, RN-INV-006, CON-021, GAP-033, `contribution_item`, `social_contribution`, `project`, `topic`, `ContributionGuidelines`, `InvestmentWriteGuard` |
| ASM-113 | El ejecutado de un programa es el `executed_amount` que se registra a mano en cada `program_budget`, por año y moneda; KPI-INV-02 lo compara con `allocated_amount` sin convertir monedas. `social_program.total_budget` suma solo los presupuestos en la moneda del programa (sin moneda del programa, queda vacío), y los de otra moneda se muestran aparte. Un aporte no se vincula a un programa | Decisión del usuario del 2026-10-11 (decisión 4), que refuerza ASM-063: las fuentes no dicen cómo se ejecuta un programa | Derivar el ejecutado de un programa de los aportes o compromisos vinculados a él | RF-INV-003, RF-INV-004, RQ-DSH-030, ASM-063, `program_budget`, `social_program`, `ProgramTotalsListener` |
| ASM-114 | El alcance de lectura por proyecto (ASM-106) cubre los hijos de hijos y los hijos con varios padres. Un hijo cuyo padre es otro hijo se lee si la cadena, de a lo sumo dos saltos y sin ciclos, llega a una raíz de mis proyectos (`program_measurement` → `program_indicator` → `social_program`); los nietos no copian `project`. Un hijo con varios padres posibles, de los que lleva exactamente uno (`delivery`: `commitment` o `social_contribution`), se lee por el padre que tiene, nunca «sin padre, todos»: una entrega sin ninguno de los dos solo la leen `ADMIN` y quien tiene `READ_ALL_PROJECTS`. «Sin padre, del tenant» queda solo para `locality_profile`. La escritura (`ProjectResolver`) y los vínculos (`ProjectLinks`) suben la misma cadena | Con `program_measurement` y la entrega de un aporte, la regla del plan 4.4, vigente hasta F4 (el padre de un hijo es una raíz; `delivery` sin compromiso, del tenant), no clasificaría las mediciones y abriría las entregas de aportes a todo el tenant | Copiar `project` en los nietos y en `delivery`, con una regla que lo mantenga igual al del padre | RQ-SEC-006, ASM-106, ADR-0029, `ProjectScope`, `ProjectReadCondition`, `ProjectReadScope`, `ProjectResolver`, `ProjectLinks`, `ArchitectureTest`, `docs/security.md` §9.7 |
| ASM-115 | Un programa con `partner_organization` y con `policy_synergy_requires_agreement` activa llega a `planned` (`approve` o `plan_direct`) solo con un `agreement` en `active`, con `validity_status` distinto de VENCIDO y con un `agreement_party` cuya `organization` es el socio (`409 SYNERGY_AGREEMENT_REQUIRED` sobre `agreement`); un programa sin socio no lo exige | RN-INV-004 pide formalizar la sinergia en un acuerdo sin decir qué hace vigente al acuerdo ni quién debe ser parte | Exigir solo un acuerdo vinculado, en cualquier estado | RN-INV-004, RF-INV-003, `social_program`, `agreement`, `agreement_party`, `InvestmentWriteGuard` |
| ASM-116 | El documento «Acta de recepción» y AUT-04 esperan a UPS-23, como el acta de entrega (amplía ASM-090): `delivery` restringe `recipients` y `conformity_by`. Mientras tanto el acta se imprime fuera de SGSPE, se firma en la entrega y se sube como evidencia ACTA DE RECEPCION vinculada a la entrega, con clasificación DATOS PERSONALES por defecto si lleva nombres o firmas. `receipt` es opcional: `register_delivery` (P8) exige las evidencias ACTA DE RECEPCION, FOTO y COMPROBANTE, y ninguna regla compara `receipt.amount` con `total_amount` | Un documento emitido congela el registro entero (UPS-23), y el comprobante estructurado no siempre se tiene al entregar | Emitir el acta desde SGSPE cuando wasichai-documents respete los permisos de campo (UPS-23) | RF-INV-001, RQ-REP-029, UPS-23, ASM-090, AUT-04, `delivery`, `receipt`, `evidence` |
| ASM-117 | Aceptar una solicitud como aporte (`POST /api/sgspe/stakeholder-requests/{id}/accept` con `creates` igual a `social_contribution`) exige, además del rol de `accept` y UPDATE sobre `stakeholder_request`, CREATE sobre `social_contribution`: `COMMITMENT_MANAGER`, que tiene `accept` pero no ese CREATE, recibe `403` y acepta solo como compromiso. El aporte nace en `requested` con el proyecto de la solicitud, y lo «registró» quien aceptó (P5). `created_contribution` es un campo de comando (`403 COMMAND_ONLY`). Cerrar el aporte (`close` de WF-P) cierra la solicitud en la misma transacción (`RequestClosureListener`, ASM-068); con el aporte en `rejected` o `cancelled`, la solicitud se cierra a mano con `close_unfulfilled` | El diseño decía «desde F5» sin fijar quién crea el aporte ni cómo se protege el vínculo | Dar CREATE sobre `social_contribution` a `COMMITMENT_MANAGER` | RF-GRV-004, RN-GRV-001, ASM-068, `stakeholder_request`, `social_contribution`, `RequestClosureListener`, `AttentionCommandFieldsGuard` |
| ASM-118 | Los montos y conteos de F5 siguen ASM-092: `contribution_item.line_amount` y `unit_cost`, `receipt.amount` y `program_budget.allocated_amount` y `executed_amount` se validan en el alta y en todo cambio de su valor (`400 INVALID_NUMBER` u `OUT_OF_RANGE`); `contribution_item.quantity`, `program_indicator.baseline_value` y `target_value` y `program_measurement.measured_value` no son dinero. `beneficiaries_count`, `target_count` y `reached_count` son enteros no negativos, `budget_year` va de 2000 a 2100 y la fecha final de una medición, un programa o una iniciativa no es anterior a la inicial (`400 OUT_OF_RANGE`) | DECIMAL e INTEGER de wasichai no tienen mínimo, máximo ni escala | Escala, mínimo y máximo por campo en el modelo, si wasichai los ofrece | ASM-092, RF-INV-001, RF-INV-003, `AmountFieldsGuard`, `InvestmentWriteGuard` |
| ASM-119 | La autoridad para superar un tope que bloquea difiere por objeto: en compromisos la tiene `PROJECT_MANAGER` (`APPROVE_OVER_CAP` sobre `commitment`, en `approve_senior` de WF-D) y en aportes, `SOCIAL_MANAGER` (`APPROVE_OVER_CAP` sobre `social_contribution`, en `approve` de WF-P). Un aporte que registró o aceptó un `SOCIAL_MANAGER` y supera un tope que bloquea solo lo aprueba otro `SOCIAL_MANAGER` | GAP-006 sigue abierta, y `security.md` §7.5 y §7.7 y `workflows.md` D7 y P1 ya la nombraban por separado | Un solo rol con `APPROVE_OVER_CAP` en los dos objetos, o la matriz de autoridad que responda GAP-006 | RN-INV-003, GAP-006, ASM-067, RQ-SEC-004, `SupportPolicyGuard`, `ContributionCapRule` |
```

- [ ] **Paso 7: Los totales de §20.1 y las observaciones de §20.4**

En `docs/requirements-analysis.md`, reemplazar:

```markdown
| `GAP` | 32 |
| `UPS` | 28 |
| `ASM` | 109 |
| Total | 527 |
```

por:

```markdown
| `GAP` | 33 |
| `UPS` | 28 |
| `ASM` | 119 |
| Total | 538 |
```

En `docs/requirements-analysis.md`, reemplazar:

```markdown
- Las nueve decisiones del usuario (USR-01 a USR-09) están desplegadas según §6.0; USR-04 y USR-08 se cumplen en el
```

por:

```markdown
- Los IDs de F5 (ASM-110 a ASM-119 y GAP-033) no retiran ninguno: ASM-113 refuerza ASM-063, ASM-114 amplía ASM-106
  (que desde F5 ya no lee en todo el tenant una entrega sin compromiso), ASM-116 amplía ASM-090 y ASM-118 amplía
  ASM-092.
- Las nueve decisiones del usuario (USR-01 a USR-09) están desplegadas según §6.0; USR-04 y USR-08 se cumplen en el
```

- [ ] **Paso 8: Verificar y commit**

Repetir la comprobación del Paso 1 (esperado: `ok`) y las compuertas de «Global Constraints» (las 221 pruebas del
modelo en verde: `catalogs()` solo lee los catálogos que tienen clave en `database-model.md` §4, y la clave
`item_kind` llega con la Tarea 2). Después:

```bash
git add docs/requirements-analysis.md
git commit -F - <<'MSG'
docs: IDs de F5 en el análisis (ASM-110 a ASM-119, GAP-033)

Suma los supuestos de las decisiones del 2026-10-11 y de las elecciones de la especificación de F5,
GAP-033 (caseríos sin beneficio laboral), el catálogo «Tipo de ítem del aporte» y los totales de
§20.1 (538). ASM-092 y ASM-106 se acotan a lo que F5 cambia.

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_017MAgYcNauGp6cC3ekX1h7U
MSG
```

---

### Tarea 2: Modelo de datos: §15, `item_kind`, temas priorizados y la entrega con dos padres

Las fichas son lo que `design_docs.py` lee y lo que el plan 5.1 lleva a `model.json`: las de BC-10 (F5) cambian sus
campos, relaciones y notas; las de F2 a F3 (`project`, `topic`, `delivery`, `task`) y la de F4
(`stakeholder_request`) solo ganan notas y una relación con «desde F5» (decisión 2 de este plan). La prueba nueva fija
lo que sí cambia en la forma leída.

**Archivos:**
- Modificar: `docs/database-model.md` (§4; §15: introducción, diagrama y fichas; `delivery`, `project`, `topic`,
  `task`, `stakeholder_request`)
- Modificar: `model/test_design_docs.py` (79 claves ENUM; prueba nueva)
- Probar: `model/test_design_docs.py`, `model/test_model.py`, `model/test_apply_roles.py`

**Interfaces:**
- Consume: ASM-110 a ASM-114, ASM-116 a ASM-118 y el catálogo «Tipo de ítem del aporte» (Tarea 1).
- Produce: `design_docs.cards()["contribution_item"]` con el campo `item_kind` (`ENUM`, enum `item_kind`, requerido);
  `cards()["social_contribution"]` con la relación `contribution_topic` (campo `topic`, destino `topic`, opcional);
  `cards()["project"]` con la relación `project_priority_topic` (`MANY_TO_MANY`, destino `topic`, nota «desde F5») y
  `cards()["topic"]` con su inversa; `design_docs.enum_keys()` con 79 claves (`item_kind` →
  «Tipo de ítem del aporte»); `cards()["delivery"].unique_constraints` sigue vacío hasta el plan 5.1.

- [ ] **Paso 1: La prueba que falla**

En `model/test_design_docs.py`, reemplazar:

```python
class BrokenDocsTests(unittest.TestCase):
```

por:

```python
class F5CardsTests(unittest.TestCase):
    def test_las_fichas_de_f5_traen_lo_que_suma_el_plan_5_0(self):
        cards = design_docs.cards()
        # decisión 3 (ASM-112): el tipo de cada ítem es lo que leen los lineamientos al aprobar
        kind = next(f for f in cards["contribution_item"].fields if f["name"] == "item_kind")
        self.assertEqual((kind["type"], kind["enum"], kind["required"]), ("ENUM", "item_kind", True))
        self.assertEqual(design_docs.catalogs()["item_kind"], ["BIEN", "SERVICIO", "DINERO", "MANO DE OBRA"])
        topic = next(r for r in cards["social_contribution"].relationships if r["name"] == "contribution_topic")
        self.assertEqual((topic["type"], topic["field"], topic["target"], topic["required"]),
                         ("MANY_TO_ONE", "topic", "topic", False))
        # la M2M de los temas priorizados llega en F5 aunque project y topic sean de F2
        priority = next(r for r in cards["project"].relationships if r["name"] == "project_priority_topic")
        self.assertEqual((priority["type"], priority["target"], priority["inverse"]), ("MANY_TO_MANY", "topic", False))
        self.assertEqual(design_docs.relationship_phase(cards, cards["project"], priority), "F5")
        inverse = next(r for r in cards["topic"].relationships if r["name"] == "project_priority_topic")
        self.assertTrue(inverse["inverse"])
        # ASM-116: el acta de recepción espera a UPS-23, como el acta de entrega
        aut04 = next(a for a in design_docs.automations() if a["id"] == "AUT-04")
        self.assertEqual((aut04["object"], aut04["phase"], aut04["after"]), ("delivery", "F5", "UPS-23"))


class BrokenDocsTests(unittest.TestCase):
```

- [ ] **Paso 2: Correr la prueba y verla fallar**

Run: `cd model && python3 -m unittest test_design_docs.F5CardsTests -v`
Expected: FAIL con `StopIteration` (la ficha de `contribution_item` todavía no tiene `item_kind`).

- [ ] **Paso 3: La clave ENUM de §4 y el total de claves**

En `docs/database-model.md`, reemplazar:

```markdown
| Tipo de aporte | `contribution_type` | `social_contribution.contribution_type` |
```

por:

```markdown
| Tipo de aporte | `contribution_type` | `social_contribution.contribution_type` |
| Tipo de ítem del aporte | `item_kind` | `contribution_item.item_kind` |
```

En `model/test_design_docs.py`, reemplazar:

```python
        # 78 claves en §4; las 45 de F2 son las de los campos ENUM que llegan en F2
        self.assertEqual(len(design_docs.enum_keys()), 78)
```

por:

```python
        # 79 claves en §4 (item_kind desde el plan 5.0); las 45 de F2 son las de los campos ENUM que llegan en F2
        self.assertEqual(len(design_docs.enum_keys()), 79)
```

- [ ] **Paso 4: La introducción y el diagrama de §15**

En `docs/database-model.md`, reemplazar:

```markdown
indicadores e hitos (RF-INV-001, RF-INV-003). Los lineamientos de apoyo son políticas de `project` desde F3 (RF-INV-002,
ASM-019).
```

por:

```markdown
indicadores e hitos (RF-INV-001, RF-INV-003). Los lineamientos de apoyo son políticas de `project` desde F3 (RF-INV-002,
ASM-019); desde F5, la aprobación de un aporte los evalúa con el tipo de cada ítem (`item_kind`), el tema del aporte
(`topic`) y los temas priorizados del proyecto (`project_priority_topic`, ASM-112). Los hijos de BC-10 heredan el
proyecto de su raíz (`social_contribution` o `social_program`); `program_measurement`, por la cadena de su indicador
(ASM-114).
```

En `docs/database-model.md`, reemplazar:

```markdown
    social_contribution }o..o| agreement : "agreement"
```

por:

```markdown
    social_contribution }o..o| agreement : "agreement"
    social_contribution }o..o| topic : "topic"
```

- [ ] **Paso 5: La ficha de `social_contribution`**

En `docs/database-model.md`, reemplazar:

```markdown
| `contribution_type` | ENUM `contribution_type` | no | — | Tipo de aporte. | RF-INV-001 |
```

por:

```markdown
| `contribution_type` | ENUM `contribution_type` | no | — | Tipo de aporte; se exige al aprobar (P9, ASM-112). | RF-INV-001 |
```

En `docs/database-model.md`, reemplazar:

```markdown
| `total_amount` | DECIMAL | no | — | (derivado) Suma de `line_amount` de sus ítems. | RF-INV-001 |
```

por:

```markdown
| `total_amount` | DECIMAL | no | — | (derivado) Suma de `line_amount` de sus ítems: el monto que se aprueba y se compara con el tope de la campaña (ASM-110). | RF-INV-001 |
```

En `docs/database-model.md`, reemplazar:

```markdown
| `beneficiaries_count` | INTEGER | no | — | Beneficiarios. | RF-INV-001 |
| `has_institutional_synergy` | BOOLEAN | no | — | Forma parte de una sinergia institucional. | RN-INV-004 |
| `guideline_deviation_justification` | LONG_TEXT | no | — | Justificación cuando se aparta de los lineamientos. | RN-INV-006 |
```

por:

```markdown
| `beneficiaries_count` | INTEGER | no | — | Beneficiarios; entero no negativo (ASM-118). | RF-INV-001 |
| `has_institutional_synergy` | BOOLEAN | no | — | Forma parte de una sinergia institucional; con la política activa, exige un acuerdo vigente al aprobar (P6). | RN-INV-004 |
| `guideline_deviation_justification` | LONG_TEXT | no | — | Justificación cuando se aparta de los lineamientos; cubre todos los incumplidos (ASM-112). | RN-INV-006 |
```

En `docs/database-model.md`, reemplazar:

```markdown
| `contribution_agreement` | MANY_TO_ONE | `agreement` | `agreement` | no | Acuerdo de sinergia |
```

por:

```markdown
| `contribution_agreement` | MANY_TO_ONE | `agreement` | `agreement` | no | Acuerdo de sinergia |
| `contribution_topic` | MANY_TO_ONE | `topic` | `topic` | no | Tema del aporte, para la prioridad (RN-INV-005, ASM-112) |
```

En `docs/database-model.md`, reemplazar:

```markdown
- `ContributionTotalsListener` y `DerivedFieldsJob` recalculan `total_amount` desde cero (RF-INV-001).
```

por:

```markdown
- `ContributionTotalsListener` mantiene `total_amount` con cada alta, cambio o baja de un ítem y lo vuelve a escribir en
  `approve` y `approve_direct`, así lo aprobado queda escrito; `DerivedFieldsJob` lo recalcula desde cero (RF-INV-001,
  ASM-110).
- La aprobación (P1 a P9 de WF-P, `workflows.md`) exige ítems y `contribution_type`, evalúa los lineamientos con
  `item_kind`, `topic` y los temas priorizados del proyecto, y compara la suma de los ítems, leída en la transacción,
  con el tope de la campaña (ASM-110, ASM-112). Fuera de `requested` no cambian `currency`, `contribution_type`,
  `topic`, `exploration_campaign`, `has_institutional_synergy`, `agreement`, `guideline_deviation_justification` ni
  `project` (`409 CONTRIBUTION_FROZEN`).
- Un aporte tiene a lo sumo una `delivery` (ASM-111). `stakeholder_request.created_contribution` apunta al aporte que
  creó la aceptación de una solicitud, que cuenta como quien lo registró (ASM-117).
- Las relaciones hacia `exploration_campaign`, `stakeholder`, `commitment` y `agreement` son del mismo proyecto
  (`409 PROJECT_MISMATCH`, `ProjectLinkGuard`); `topic` es un maestro del tenant.
```

- [ ] **Paso 6: Las fichas de `contribution_item` y `receipt`**

En `docs/database-model.md`, reemplazar:

```markdown
**Ítem del aporte** (BC-10). Bien o servicio que compone un aporte, con cantidad y costo. Tabla física
`app_data.contribution_item__<org8>`. Requisitos: RF-INV-001.

| Campo | Tipo | Req. | Único | Descripción | Fuente |
|---|---|---|---|---|---|
| `description` | TEXT | sí | — | Bien o servicio. | RF-INV-001 |
| `quantity` | DECIMAL | no | — | Cantidad. | RF-INV-001 |
| `unit` | TEXT | no | — | Unidad. | RF-INV-001 |
| `unit_cost` | DECIMAL | no | — | Costo unitario. | RF-INV-001 |
| `line_amount` | DECIMAL | sí | — | Monto de la línea, en la moneda del aporte. | RF-INV-001 |
```

por:

```markdown
**Ítem del aporte** (BC-10). Bien, servicio, dinero o mano de obra que compone un aporte, con su tipo, cantidad y monto.
Tabla física `app_data.contribution_item__<org8>`. Requisitos: RF-INV-001, RN-INV-001, RN-INV-002, ASM-110, ASM-112.

| Campo | Tipo | Req. | Único | Descripción | Fuente |
|---|---|---|---|---|---|
| `description` | TEXT | sí | — | Bien o servicio. | RF-INV-001 |
| `item_kind` | ENUM `item_kind` | sí | — | Tipo de ítem; lo leen los lineamientos al aprobar (ASM-112). | RN-INV-001 |
| `quantity` | DECIMAL | no | — | Cantidad; informativa, no se compara con `line_amount`. | RF-INV-001 |
| `unit` | TEXT | no | — | Unidad. | RF-INV-001 |
| `unit_cost` | DECIMAL | no | — | Costo unitario; informativo. | RF-INV-001 |
| `line_amount` | DECIMAL | sí | — | Monto de la línea, en la moneda del aporte; es el que cuenta (ASM-110). | RF-INV-001 |
```

En `docs/database-model.md`, reemplazar:

```markdown
- **Fase:** F5 · Release 2 (Fase 2 de producto).

### `receipt`
```

por:

```markdown
- **Fase:** F5 · Release 2 (Fase 2 de producto).
- Fuera de `requested` del aporte, un ítem no se crea, cambia ni borra, y su `social_contribution` no cambia nunca
  (`409 ITEMS_FROZEN` sobre `social_contribution`, ASM-110); la regla toma el candado del aporte. `line_amount` y
  `unit_cost` son montos (ASM-092, ASM-118); `quantity` no lo es.

### `receipt`
```

En `docs/database-model.md`, reemplazar:

```markdown
- **Fase:** F5 · Release 2 (Fase 2 de producto).

### `social_program`
```

por:

```markdown
- **Fase:** F5 · Release 2 (Fase 2 de producto).
- Opcional: `register_delivery` (P8) exige la evidencia COMPROBANTE, no el `receipt`, y ninguna regla compara `amount`
  con el `total_amount` del aporte (ASM-116). `amount` es un monto (ASM-092, ASM-118).

### `social_program`
```

- [ ] **Paso 7: Las fichas del programa y sus hijos**

En `docs/database-model.md`, reemplazar:

```markdown
| `total_budget` | DECIMAL | no | — | (derivado) Suma de `allocated_amount` de sus presupuestos. | RF-INV-003 |
```

por:

```markdown
| `total_budget` | DECIMAL | no | — | (derivado) Suma de `allocated_amount` de sus presupuestos en la moneda del programa; sin moneda del programa, vacío (ASM-113). | RF-INV-003 |
```

En `docs/database-model.md`, reemplazar:

```markdown
- **Fase:** F5 · Release 2 (Fase 2 de producto).

### `social_initiative`
```

por:

```markdown
- **Fase:** F5 · Release 2 (Fase 2 de producto).
- `ProgramTotalsListener` mantiene `total_budget` con cada alta, cambio o baja de un `program_budget`, y
  `DerivedFieldsJob` lo recalcula (ASM-113). El ejecutado del programa no se deriva: es el `executed_amount` de cada
  presupuesto, registrado a mano.
- Con `partner_organization` y `policy_synergy_requires_agreement` activa, `approve` y `plan_direct` exigen un acuerdo
  en `active`, no VENCIDO y con el socio como parte (G3, ASM-115). `end_date` no es anterior a `start_date`
  (`400 OUT_OF_RANGE`). `agreement` es del mismo proyecto (`409 PROJECT_MISMATCH`).

### `social_initiative`
```

En `docs/database-model.md`, reemplazar:

```markdown
- **Fase:** F5 · Release 2 (Fase 2 de producto).

### `beneficiary_group`
```

por:

```markdown
- **Fase:** F5 · Release 2 (Fase 2 de producto).
- `end_date` no es anterior a `start_date` (`400 OUT_OF_RANGE`, ASM-118).

### `beneficiary_group`
```

En `docs/database-model.md`, reemplazar:

```markdown
- **Fase:** F5 · Release 2 (Fase 2 de producto).

### `program_budget`
```

por:

```markdown
- **Fase:** F5 · Release 2 (Fase 2 de producto).
- `social_initiative`, si se informa, es del mismo programa (`409 PROGRAM_MISMATCH` sobre `social_initiative`);
  `target_count` y `reached_count` son enteros no negativos (`400 OUT_OF_RANGE`, ASM-118).

### `program_budget`
```

En `docs/database-model.md`, reemplazar:

```markdown
- **Fase:** F5 · Release 2 (Fase 2 de producto).

### `program_indicator`
```

por:

```markdown
- **Fase:** F5 · Release 2 (Fase 2 de producto).
- `budget_year` va de 2000 a 2100 (`400 OUT_OF_RANGE`); `allocated_amount` y `executed_amount` son montos (ASM-092,
  ASM-118). El ejecutado se registra a mano por año y moneda (ASM-113).

### `program_indicator`
```

En `docs/database-model.md`, reemplazar:

```markdown
- **Fase:** F5 · Release 2 (Fase 2 de producto).

### `program_measurement`
```

por:

```markdown
- **Fase:** F5 · Release 2 (Fase 2 de producto).
- Un indicador con mediciones no se borra: `program_measurement` es `appendOnly` y wasichai rechaza la baja con un `409`
  sin código (`AppendOnlyReferences`), no `HAS_DEPENDENTS`. `baseline_value` y `target_value` no son dinero
  (ASM-118).

### `program_measurement`
```

En `docs/database-model.md`, reemplazar:

```markdown
- **Fase:** F5 · Release 2 (Fase 2 de producto).

### `program_milestone`
```

por:

```markdown
- **Fase:** F5 · Release 2 (Fase 2 de producto).
- Hijo de un hijo: se lee por el proyecto del programa de su indicador, sin copiar `project` (ASM-114). Una medición no
  se corrige: se registra otra de otro periodo; la del mismo periodo no se repite (único). `period_end_date` no es
  anterior a `period_start_date` (`400 OUT_OF_RANGE`, ASM-118).

### `program_milestone`
```

- [ ] **Paso 8: `delivery`: único desde F5, entrega del aporte y dos padres**

En `docs/database-model.md`, reemplazar:

```markdown
- **Únicos:** ninguno.
- **Índices declarados:** ninguno.
- **Acciones declaradas:** ninguna.
- **Workflow:** ninguno.
- **Fase:** F3 · Release 1 (MVP).
- `delivered_amount` es un monto
```

por:

```markdown
- **Únicos:** ninguno en F3 y F4; desde F5, uno sobre `social_contribution` (ASM-111), que las entregas de compromisos
  dejan vacío y no choca; si wasichai no lo admite sobre una relación opcional, queda solo la regla con candado de
  `InvestmentWriteGuard`.
- **Índices declarados:** ninguno.
- **Acciones declaradas:** ninguna.
- **Workflow:** ninguno.
- **Fase:** F3 · Release 1 (MVP).
- `delivered_amount` es un monto
```

En `docs/database-model.md`, reemplazar:

```markdown
  `place` se generaliza a la localidad del compromiso (`deliverables.md` §9.1).

### `commitment_candidate`
```

por:

```markdown
  `place` se generaliza a la localidad del compromiso (`deliverables.md` §9.1).
- Desde F5, la entrega de un aporte (ASM-111): una por aporte (`409 DELIVERY_EXISTS`), capturada solo con el aporte en
  `procured`, sin `delivered_amount` (`400 FIELD_NOT_ALLOWED`); desde `delivered` y en `cancelled` no cambia ni gana o
  pierde evidencias (`409 DELIVERY_FROZEN` sobre `social_contribution`), y su `social_contribution` no cambia nunca. El
  «Acta de recepción» (AUT-04) espera a UPS-23: el acta firmada es una evidencia ACTA DE RECEPCION (ASM-116).
- Desde F5, `delivery` es un hijo con dos padres posibles, de los que lleva exactamente uno: se lee por el proyecto del
  compromiso o del aporte que tiene, nunca «sin padre, todos»; una entrega sin ninguno de los dos solo la leen `ADMIN`
  y quien tiene `READ_ALL_PROJECTS` (ASM-114, `security.md` §9.7).

### `commitment_candidate`
```

- [ ] **Paso 9: Los temas priorizados en `project` y en `topic`**

En `docs/database-model.md`, reemplazar:

```markdown
| `project_concession` | MANY_TO_MANY | — | `mining_concession` | — | Concesiones del proyecto |
```

por:

```markdown
| `project_concession` | MANY_TO_MANY | — | `mining_concession` | — | Concesiones del proyecto |
| `project_priority_topic` | MANY_TO_MANY | — | `topic` | — | Temas priorizados para aportes (RN-INV-005, ASM-112); desde F5 |
```

En `docs/database-model.md`, reemplazar:

```markdown
  `risk_high_min_score` < `risk_critical_min_score`).

### `project_party`
```

por:

```markdown
  `risk_high_min_score` < `risk_critical_min_score`).
- Desde F5, los temas priorizados para aportes son el vínculo `project_priority_topic` (RF-INV-002, ASM-112): lo
  escriben quienes editan las políticas del proyecto (`PROJECT_ADMIN`, `PROJECT_MANAGER`), con UPDATE sobre `project` y
  `u` sobre `topic` (`security.md` §7.7); un tema cuenta si es priorizado o desciende de uno (`parent_topic`, hasta 10
  niveles). Sin temas priorizados, la prioridad de RN-INV-005 no se evalúa y se advierte. `topic` es un maestro del
  tenant: no hay regla «mismo proyecto».

### `project_party`
```

En `docs/database-model.md`, reemplazar:

```markdown
| `engagement_topic` | MANY_TO_MANY | — | `engagement` | — | Inversa; se define en `engagement` |
```

por:

```markdown
| `engagement_topic` | MANY_TO_MANY | — | `engagement` | — | Inversa; se define en `engagement` |
| `project_priority_topic` | MANY_TO_MANY | — | `project` | — | Inversa; se define en `project` |
```

- [ ] **Paso 10: Las notas de `task` y `stakeholder_request`**

En `docs/database-model.md`, reemplazar:

```markdown
  `escalation:<objeto>:<huella>:<fecha del plazo>:2` (`workflows.md`, «Avisos»).

## 18. BC-14 Notificaciones
```

por:

```markdown
  `escalation:<objeto>:<huella>:<fecha del plazo>:2` (`workflows.md`, «Avisos»).
- Desde F5, `social_program` entra en el grupo de «Exactamente uno de» y `ProjectLinkGuard` juzga los orígenes de una
  tarea: cada uno es del proyecto de la tarea (`409 PROJECT_MISMATCH` sobre el campo).

## 18. BC-14 Notificaciones
```

En `docs/database-model.md`, reemplazar:

```markdown
- Al cerrar el compromiso creado y, desde F5, el aporte creado, `RequestClosureListener` cierra la solicitud
  (RN-GRV-001, ASM-068). Si el compromiso o el aporte se cancela o se rechaza, la solicitud se cierra a mano con
  `close_unfulfilled`.
```

por:

```markdown
- Al cerrar el compromiso creado y, desde F5, el aporte creado, `RequestClosureListener` cierra la solicitud
  (RN-GRV-001, ASM-068). Si el compromiso o el aporte se cancela o se rechaza, la solicitud se cierra a mano con
  `close_unfulfilled`.
- `created_commitment` y, desde F5, `created_contribution` solo los escribe la aceptación: una persona que los fija,
  cambia o vacía por la API genérica recibe `403 COMMAND_ONLY` (`AttentionCommandFieldsGuard`, ASM-117).
```

- [ ] **Paso 11: Correr las pruebas y verlas pasar**

Run: `cd model && python3 -m unittest test_design_docs -v 2>&1 | tail -3 && python3 -m unittest 2>&1 | tail -1`
Expected: `OK` dos veces (222 pruebas: las 221 de antes más la nueva). Después, las compuertas de «Global
Constraints». `test_model.py` sigue en verde porque `item_kind`, `contribution_topic` y `project_priority_topic` son de
F5 (`relationship_phase` toma la marca «desde F5» de la nota) y la viñeta «Únicos» de `delivery` no lleva ninguna forma
que lea el parser (ni «`unique` en» ni `` `uniqueConstraints` ``): la pone el plan 5.1 con el modelo.

- [ ] **Paso 12: Commit**

```bash
git add docs/database-model.md model/test_design_docs.py
git commit -F - <<'MSG'
docs: fichas de BC-10 de F5 con item_kind, tema y temas priorizados

Suma item_kind, contribution_topic y project_priority_topic, las reglas de cada ficha de BC-10, la
entrega única del aporte y su lectura por dos padres; el único de delivery queda como texto hasta
el modelo de F5. test_design_docs.py fija lo que suma el plan 5.0.

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_017MAgYcNauGp6cC3ekX1h7U
MSG
```

---

### Tarea 3: `workflows.md` de F5

WF-P pasa a las reglas P1 a P9 de la especificación §3.3 (con P9, los ítems al aprobar, y P6 fuera de los
lineamientos), su orden y sus candados, las «Otras reglas» de `InvestmentWriteGuard` y la entrega única; WF-G gana G1 a
G4 y las reglas de los hijos; WF-E3 deja de marcar «desde F5»; los listeners, los avisos y las pruebas suman lo de F5.
Las tablas de transiciones no cambian de forma ni de roles: `design_docs.workflow_transitions` las lee igual.

**Archivos:**
- Modificar: `docs/workflows.md` (regla 10, transiciones de comando, resumen, WF-E3, WF-G, WF-P, listeners, avisos,
  pruebas)
- Probar: `model/test_design_docs.py` (`workflow_transitions`, `automations`), `model/test_model.py`

**Interfaces:**
- Consume: ASM-110 a ASM-119 (Tarea 1).
- Produce: los números P1 a P9 y G1 a G4 con sus códigos y campos, que citan `api.md` (Tarea 6), `domain-model.md`
  (Tarea 4), `ux/screens.md` (Tarea 7) y los planes 5.2a, 5.2b y 5.3; el orden de reglas «P7, P9, P4 y P5, P6, P2 y P3,
  P1» con el candado del aporte antes que el de la campaña; los casos de `SocialContributionWorkflowApiTest`,
  `SocialProgramWorkflowApiTest` y `StakeholderRequestWorkflowApiTest` de la especificación §5.

- [ ] **Paso 1: La comprobación falla**

```bash
python3 - <<'PY'
text = open('docs/workflows.md', encoding='utf-8').read()
checks = [
    ('- **P9. Ítems al aprobar**', True),
    ('- **G3. Sinergia**', True),
    ('**Orden y candados.**', True),
    ('`409 ITEMS_FROZEN`', True),
    ('`409 CONTRIBUTION_FROZEN`', True),
    ('`409 DELIVERY_EXISTS`', True),
    ('`409 PROGRAM_MISMATCH`', True),
    ('`ProgramTotalsListener`', True),
    ('`COMMITMENT_MANAGER` → `403`', True),
    ('«F5, tras UPS-23»', True),
    ('`SOCIAL_INVESTMENT_MANAGER` (desde F5)', False),
    ('`InvestmentWriteGuard`: al menos un `contribution_item`', False),
    ('y AUT-04 emite el acta de recepción', False),
    ('Ninguna (alimenta `register_procurement`)', False),
]
bad = [f"{'falta' if want else 'sobra'} {s!r}" for s, want in checks if (s in text) != want]
print('\n'.join(bad) or 'ok')
raise SystemExit(1 if bad else 0)
PY
```

Esperado: salida 1 con las catorce líneas.

- [ ] **Paso 2: Regla 10, transiciones de comando y resumen**

En `docs/workflows.md`, reemplazar:

```markdown
    `409 PROJECT_MISMATCH` toda relación de un objeto de F4 hacia un registro de otro proyecto. Ningún rol usa
```

por:

```markdown
    `409 PROJECT_MISMATCH` toda relación de un objeto de F4 o de F5 hacia un registro de otro proyecto, y desde F5
    también los orígenes de una tarea; una cadena de hijos y la entrega con dos padres se leen por el proyecto de su
    raíz (ASM-114). Ningún rol usa
```

En `docs/workflows.md`, reemplazar:

```markdown
| El compromiso en `draft` (o, desde F5, el aporte) y su vínculo | RF-GRV-004 |
```

por:

```markdown
| El compromiso en `draft` o, desde F5, el aporte en `requested` (ASM-117), y su vínculo | RF-GRV-004 |
```

En `docs/workflows.md`, reemplazar:

```markdown
| WF-P | `social_contribution` | F5 | 7 | 8 | — | `requiresReason` | `SupportPolicyGuard`, `InvestmentWriteGuard` |
```

por:

```markdown
| WF-P | `social_contribution` | F5 | 7 | 8 | — | `requiresReason` | `InvestmentWriteGuard` (`sgspe.investment`), con `ContributionCapRule` y `ContributionGuidelines` |
```

- [ ] **Paso 3: WF-E3 sin «desde F5»**

En `docs/workflows.md`, reemplazar:

```markdown
  - el listener pasa el motivo (`RN-GRV-001: compromiso <código> cerrado`), porque `stakeholder_request` es
    `requiresReason`;
```

por:

```markdown
  - el listener pasa el motivo (`RN-GRV-001: compromiso <código> cerrado` o `RN-GRV-001: aporte <código> cerrado`),
    porque `stakeholder_request` es `requiresReason`;
```

En `docs/workflows.md`, reemplazar:

```markdown
- **Fase de cada rol.** `SOCIAL_INVESTMENT_MANAGER` entra en `accept` y `close_accepted` desde F5, cuando recibe
  permisos (`security.md` §7.1), como en WF-N: en F4, `model/workflows/stakeholder_request.json` no lo lleva.
```

por:

```markdown
- **Fase de cada rol.** `SOCIAL_INVESTMENT_MANAGER` está en `accept` y `close_accepted` desde su fase, F5, cuando recibe
  permisos (`security.md` §7.1), como en WF-N: un tenant con el modelo de F4 no lo lleva en
  `model/workflows/stakeholder_request.json`.
- **Aceptar como aporte (ASM-117).** El endpoint de aceptación con `creates` igual a `social_contribution` exige además
  CREATE sobre `social_contribution`: `SOCIAL_MANAGER` y `SOCIAL_INVESTMENT_MANAGER` lo tienen y `COMMITMENT_MANAGER`
  no, así que este acepta solo como compromiso (`403`). El aporte nace en `requested` y lo «registró» quien aceptó, que
  no lo aprueba (P5 de WF-P).
```

En `docs/workflows.md`, reemplazar:

```markdown
| `accept` (Aceptar) | `under_evaluation` | `accepted` | `SOCIAL_MANAGER`, `COMMITMENT_MANAGER`, `SOCIAL_INVESTMENT_MANAGER` (desde F5) | `CommandTransitionGuard`: solo desde el endpoint de aceptación, que crea el compromiso en `draft` (o, desde F5, el aporte en `requested`) y guarda `created_commitment` o `created_contribution`; `created_commitment` no se escribe por otra vía (`AttentionCommandFieldsGuard`, `api.md` §3.3). `AttentionWriteGuard`: `response` y `responded_at` de la solicitud informados, porque la aceptación es la respuesta y el cierre que sigue es automático | Obligatorio (`requiresReason`) | El compromiso nuevo sigue WF-D, con su aprobación; el aporte, WF-P. Con `responded_at`, la solicitud cuenta como absuelta | [DOC-01 §9.3.1.2] medio de solicitud (RF-GRV-004) |
```

por:

```markdown
| `accept` (Aceptar) | `under_evaluation` | `accepted` | `SOCIAL_MANAGER`, `COMMITMENT_MANAGER`, `SOCIAL_INVESTMENT_MANAGER` | `CommandTransitionGuard`: solo desde el endpoint de aceptación, que crea el compromiso en `draft` o el aporte en `requested` (con CREATE sobre `social_contribution`, ASM-117) y guarda `created_commitment` o `created_contribution`; ninguno de los dos se escribe por otra vía (`AttentionCommandFieldsGuard`, `403 COMMAND_ONLY`, `api.md` §3.3). `AttentionWriteGuard`: `response` y `responded_at` de la solicitud informados, porque la aceptación es la respuesta y el cierre que sigue es automático | Obligatorio (`requiresReason`) | El compromiso nuevo sigue WF-D, con su aprobación; el aporte, WF-P, con el aviso de aprobación del alta. Con `responded_at`, la solicitud cuenta como absuelta | [DOC-01 §9.3.1.2] medio de solicitud (RF-GRV-004) |
```

En `docs/workflows.md`, reemplazar:

```markdown
| `close_accepted` (Cerrar por cierre del compromiso o del aporte) | `accepted` | `closed` | `COMMITMENT_MANAGER`, `SOCIAL_MANAGER`, `SOCIAL_INVESTMENT_MANAGER` (desde F5) | `CommandTransitionGuard`: solo desde `RequestClosureListener` | Obligatorio (`requiresReason`); lo pasa el listener | `closed_date` | [DOC-01 §9.3.1.2] (RN-GRV-001, ASM-068) |
| `close_unfulfilled` (Cerrar sin ejecución) | `accepted` | `closed` | `GRIEVANCE_MANAGER`, `SOCIAL_MANAGER` | `AttentionWriteGuard`: el compromiso creado está en `cancelled`, o el aporte en `rejected` o `cancelled`; `response` informada | Obligatorio (`requiresReason`) | `closed_date` | [DOC-01 §9.3.1.2] (ASM-068) |
```

por:

```markdown
| `close_accepted` (Cerrar por cierre del compromiso o del aporte) | `accepted` | `closed` | `COMMITMENT_MANAGER`, `SOCIAL_MANAGER`, `SOCIAL_INVESTMENT_MANAGER` | `CommandTransitionGuard`: solo desde `RequestClosureListener` | Obligatorio (`requiresReason`); lo pasa el listener | `closed_date` | [DOC-01 §9.3.1.2] (RN-GRV-001, ASM-068) |
| `close_unfulfilled` (Cerrar sin ejecución) | `accepted` | `closed` | `GRIEVANCE_MANAGER`, `SOCIAL_MANAGER` | `AttentionWriteGuard`: el compromiso creado está en `cancelled`, o el aporte en `rejected` o `cancelled`; si no, `409 STATE_MISMATCH` sobre `created_commitment` o `created_contribution`; `response` informada | Obligatorio (`requiresReason`) | `closed_date` | [DOC-01 §9.3.1.2] (ASM-068) |
```

- [ ] **Paso 4: WF-G con G1 a G4 y las reglas de los hijos**

En `docs/workflows.md`, reemplazar:

```markdown
- **Requisitos:** RF-INV-003, RN-INV-004, ASM-008, GAP-007, GAP-032.
```

por:

```markdown
- **Requisitos:** RF-INV-003, RN-INV-004, ASM-008, ASM-113, ASM-115, ASM-118, GAP-007, GAP-032.
```

En `docs/workflows.md`, reemplazar:

```markdown
| `submit` (Enviar a aprobación) | `draft` | `pending_approval` | `SOCIAL_INVESTMENT_MANAGER` | — | Opcional | Aviso de aprobación pendiente | [PRM-01 §8] Approval |
```

por:

```markdown
| `submit` (Enviar a aprobación) | `draft` | `pending_approval` | `SOCIAL_INVESTMENT_MANAGER` | — | Opcional | Aviso `approval_pending` a los roles de `approve` (`ApprovalNoticeListener`), que se resuelve al salir de `pending_approval` | [PRM-01 §8] Approval |
```

En `docs/workflows.md`, reemplazar:

```markdown
| `approve` (Aprobar) | `pending_approval` | `planned` | `APPROVER` | `InvestmentWriteGuard`: la acción declarada `APPROVE` (RF-WFL-002); quien aprueba no registró el programa (ASM-008); acuerdo de sinergia con el socio vigente, según `policy_synergy_requires_agreement` (RN-INV-004) | Opcional | — | [DOC-01 §9.3.1.2] sinergias en un acuerdo formal |
| `plan_direct` (Planificar sin aprobación interna) | `draft` | `planned` | `SOCIAL_INVESTMENT_MANAGER` | `InvestmentWriteGuard`: solo con `policy_internal_approval` en falso (ASM-008); el acuerdo de sinergia (RN-INV-004) | Opcional | — | ASM-008 |
```

por:

```markdown
| `approve` (Aprobar) | `pending_approval` | `planned` | `APPROVER` | `InvestmentWriteGuard`: G1 (`APPROVE`), G2 (segregación) y G3 (sinergia) | Opcional | — | [DOC-01 §9.3.1.2] sinergias en un acuerdo formal |
| `plan_direct` (Planificar sin aprobación interna) | `draft` | `planned` | `SOCIAL_INVESTMENT_MANAGER` | `InvestmentWriteGuard`: G4 (sin aprobación interna) y G3; sin G1 ni G2 | Opcional | — | ASM-008 |
```

En `docs/workflows.md`, reemplazar:

```markdown
| `cancel_suspended` (Cancelar) | `suspended` | `cancelled` | `SOCIAL_MANAGER`, `APPROVER` | — | Obligatorio (`TransitionReasonGuard`) | — | Ídem |

## WF-H · Entregable
```

por:

```markdown
| `cancel_suspended` (Cancelar) | `suspended` | `cancelled` | `SOCIAL_MANAGER`, `APPROVER` | — | Obligatorio (`TransitionReasonGuard`) | — | Ídem |

Reglas de las guardas de WF-G, citadas por número en la tabla y en las pruebas (`InvestmentWriteGuard`,
`sgspe.investment`):

- **G1. Doble llave:** la acción declarada `APPROVE` sobre `social_program` (RF-WFL-002); sin ella,
  `403 ACTION_REQUIRED` sobre `record`.
- **G2. Segregación:** quien aprueba no registró el programa (`created_by`, ASM-008); si no,
  `403 SEGREGATION_OF_DUTIES` sobre `record`.
- **G3. Sinergia** (RN-INV-004, ASM-115): con `partner_organization` informado y `policy_synergy_requires_agreement`
  activa, `agreement` informado, en `active`, con `validity_status` distinto de VENCIDO y con un `agreement_party` cuya
  `organization` es el socio; si no, `409 SYNERGY_AGREEMENT_REQUIRED` sobre `agreement`. El acuerdo es del mismo
  proyecto (`ProjectLinkGuard`).
- **G4. Sin aprobación interna:** solo con `policy_internal_approval` en falso (ASM-008); si no,
  `409 INTERNAL_APPROVAL_REQUIRED` sobre `project`.

**Reglas de los hijos del programa**, en la misma guarda: `beneficiary_group.social_initiative` es del mismo programa
(`409 PROGRAM_MISMATCH` sobre `social_initiative`); la fecha final de una medición, un programa o una iniciativa no es
anterior a la inicial, `budget_year` va de 2000 a 2100 y `target_count` y `reached_count` son enteros no negativos
(`400 OUT_OF_RANGE`, ASM-118). `SOCIAL_INVESTMENT_MANAGER` borra iniciativas, grupos, indicadores e hitos; un indicador
con mediciones no se borra (`409` de wasichai sin código, `AppendOnlyReferences`), y una medición (`appendOnly`) no se
corrige: se registra otra de otro periodo. `ProgramTotalsListener` mantiene `total_budget` en la moneda del programa
(ASM-113); el ejecutado se registra a mano en cada presupuesto. Los hitos no tienen plazo ni escalamiento: JOB-01 no los mide.

## WF-H · Entregable
```

- [ ] **Paso 5: WF-P con P1 a P9, orden, candados y entrega**

En `docs/workflows.md`, reemplazar:

```markdown
- **Requisitos:** RF-INV-001, RN-INV-001, RN-INV-002, RN-INV-003, RN-INV-004, RN-INV-005, RN-INV-006, ASM-008, ASM-063,
  CON-021, CON-022.
```

por:

```markdown
- **Requisitos:** RF-INV-001, RN-INV-001, RN-INV-002, RN-INV-003, RN-INV-004, RN-INV-005, RN-INV-006, ASM-008, ASM-063,
  ASM-110, ASM-111, ASM-112, ASM-116, ASM-117, ASM-118, ASM-119, CON-021, CON-022, GAP-033.
- **Monto que se aprueba:** la suma de `line_amount` de los ítems, que se cargan en `requested`; tras aprobar, los ítems
  y los términos aprobados quedan congelados y un cambio es otro aporte (ASM-110).
```

En `docs/workflows.md`, reemplazar:

```markdown
| `approve` (Aprobar) | `requested` | `approved` | `APPROVER`, `SOCIAL_MANAGER` | `SupportPolicyGuard`: P1 (tope de la campaña), P2 (mano de obra y donaciones) y P3 (lineamientos). `InvestmentWriteGuard`: P4 (`APPROVE`), P5 (segregación) y P6 (sinergia) | Obligatorio (`requiresReason`) | — | [DOC-01 §9.3.1.2] lineamientos; [DOC-02 p.3–p.4] |
| `approve_direct` (Aprobar sin revisión interna) | `requested` | `approved` | `SOCIAL_INVESTMENT_MANAGER` | `SupportPolicyGuard`: P1, P2 y P3; `SOCIAL_INVESTMENT_MANAGER` no tiene `APPROVE_OVER_CAP`, así que un aporte que supera un tope que bloquea lo aprueba `approve`, por `SOCIAL_MANAGER`. `InvestmentWriteGuard`: P7 (sin aprobación interna) y P6; sin P4 ni P5 | Obligatorio (`requiresReason`) | — | ASM-008 |
| `reject` (Rechazar) | `requested` | `rejected` | `APPROVER`, `SOCIAL_MANAGER` | — | Obligatorio (`requiresReason`) | — | [DOC-01 §9.3.1.2] |
| `register_procurement` (Registrar adquisición) | `approved` | `procured` | `SOCIAL_INVESTMENT_MANAGER` | `InvestmentWriteGuard`: al menos un `contribution_item` | Obligatorio (`requiresReason`) | `ContributionTotalsListener` mantiene `total_amount` con cada ítem | [DOC-02 p.4] compra |
| `register_delivery` (Registrar entrega) | `procured` | `delivered` | `SOCIAL_INVESTMENT_MANAGER`, `COMMUNITY_RELATIONS_OFFICER` | `InvestmentWriteGuard`: P8 (entrega con conformidad, acta de recepción, foto y comprobante) | Obligatorio (`requiresReason`) | Es la transición humana que sigue a la captura de la entrega, que no cambia el estado; el aporte cuenta como ejecutado (ASM-063) | [DOC-02 p.4] (RF-INV-001) |
| `close` (Cerrar) | `delivered` | `closed` | `SOCIAL_INVESTMENT_MANAGER`, `SOCIAL_MANAGER` | — | Obligatorio (`requiresReason`) | Desde F5, `RequestClosureListener` aplica `close_accepted` a la solicitud que lo originó, en la transacción (ASM-068) | [DOC-02 p.4] |
```

por:

```markdown
| `approve` (Aprobar) | `requested` | `approved` | `APPROVER`, `SOCIAL_MANAGER` | `InvestmentWriteGuard`: P9 (ítems y tipo de aporte), P4 (`APPROVE`), P5 (segregación), P6 (sinergia), P2 y P3 (lineamientos, `ContributionGuidelines`) y P1 (tope de la campaña, `ContributionCapRule`) | Obligatorio (`requiresReason`) | `ContributionTotalsListener` escribe el `total_amount` aprobado; se resuelve el aviso `approval_pending` del alta | [DOC-01 §9.3.1.2] lineamientos; [DOC-02 p.3–p.4] |
| `approve_direct` (Aprobar sin revisión interna) | `requested` | `approved` | `SOCIAL_INVESTMENT_MANAGER` | `InvestmentWriteGuard`: P7 (sin aprobación interna), P9, P6, P2 y P3 y P1; sin P4 ni P5. `SOCIAL_INVESTMENT_MANAGER` no tiene `APPROVE_OVER_CAP`, así que un aporte que supera un tope que bloquea lo aprueba `approve`, por `SOCIAL_MANAGER` (ASM-119) | Obligatorio (`requiresReason`) | Como `approve` | ASM-008 |
| `reject` (Rechazar) | `requested` | `rejected` | `APPROVER`, `SOCIAL_MANAGER` | — | Obligatorio (`requiresReason`) | Se resuelve el aviso `approval_pending` del alta | [DOC-01 §9.3.1.2] |
| `register_procurement` (Registrar adquisición) | `approved` | `procured` | `SOCIAL_INVESTMENT_MANAGER` | — (los ítems los exigió la aprobación, P9, y quedaron congelados, ASM-110) | Obligatorio (`requiresReason`) | — | [DOC-02 p.4] compra |
| `register_delivery` (Registrar entrega) | `procured` | `delivered` | `SOCIAL_INVESTMENT_MANAGER`, `COMMUNITY_RELATIONS_OFFICER` | `InvestmentWriteGuard`: P8 (la única entrega, con conformidad y las evidencias ACTA DE RECEPCION, FOTO y COMPROBANTE), con el candado del aporte | Obligatorio (`requiresReason`) | Es la transición humana que sigue a la captura de la entrega, que no cambia el estado; el aporte cuenta como ejecutado (ASM-063) y la entrega queda congelada (ASM-111) | [DOC-02 p.4] (RF-INV-001) |
| `close` (Cerrar) | `delivered` | `closed` | `SOCIAL_INVESTMENT_MANAGER`, `SOCIAL_MANAGER` | — | Obligatorio (`requiresReason`) | `RequestClosureListener` aplica `close_accepted` a la solicitud que lo originó, en la transacción (ASM-068, ASM-117) | [DOC-02 p.4] |
```

En `docs/workflows.md`, reemplazar:

```markdown
Reglas de las guardas de WF-P, citadas por número en la tabla y en las pruebas:

- **P1. Tope de la campaña** (`SupportPolicyGuard`): la regla D7 de WF-D, sobre los mismos montos aprobados de la campaña;
  con la política que bloquea, solo lo supera quien tiene `APPROVE_OVER_CAP` sobre `social_contribution`, que tiene
  `SOCIAL_MANAGER` (RN-INV-003, GAP-006, RQ-SEC-004, ASM-063).
- **P2. Mano de obra y donaciones:** no se financia mano de obra, y las donaciones son no monetarias (RN-INV-001,
  RN-INV-002).
- **P3. Lineamientos:** `guideline_deviation_justification` informado si el aporte se aparta de los lineamientos del
  proyecto (RN-INV-006).
- **P4. Doble llave:** la acción declarada `APPROVE` sobre `social_contribution` (RF-WFL-002).
- **P5. Segregación:** quien aprueba no registró el aporte (ASM-008).
- **P6. Sinergia:** un acuerdo vigente si `has_institutional_synergy` es verdadero (RN-INV-004).
- **P7. Sin aprobación interna:** solo con `policy_internal_approval` en falso (ASM-008).
- **P8. Entrega con conformidad y evidencia:** una `delivery` del aporte con `conformity_given` verdadero y `conformity_by`
  informado, vinculada a evidencias ACTA DE RECEPCION, FOTO y COMPROBANTE [DOC-02 p.4].

`POST /api/sgspe/social-contributions/{id}/deliveries` solo captura datos: crea la `delivery` y sus vínculos de evidencia en
una transacción, y AUT-04 emite el acta de recepción. El cambio de estado es la transición humana `register_delivery`
(modelo de dominio §7.2).
```

por:

```markdown
Reglas de las guardas de WF-P, citadas por número en la tabla y en las pruebas. Todas son de `InvestmentWriteGuard`
(`sgspe.investment`), que usa dos piezas del mismo paquete: `ContributionCapRule` (P1), con `SupportCap` y
`CampaignCommitted`, como `SupportPolicyGuard` en los compromisos, y la función pura `ContributionGuidelines` (P2 y P3).

- **P1. Tope de la campaña** (`ContributionCapRule`, ASM-110, ASM-119): la regla D7 de WF-D sobre el aporte. El
  candidato es la suma de `line_amount` de sus ítems, leída en la transacción y nunca del `total_amount` guardado, en la
  moneda del aporte; lo comprometido de la campaña (`CampaignCommitted`) suma los compromisos en `approved`,
  `in_progress`, `blocked`, `fulfilled`, `verified` o `closed` y los aportes en `approved`, `procured`, `delivered` o
  `closed`, sin el que se aprueba. Con `policy_cap_blocks_approval` activa, superarlo es `409 SUPPORT_CAP_EXCEEDED` (o
  `CAP_DATA_INVALID` si un monto guardado no es válido) sobre `exploration_campaign`, salvo para quien tiene
  `APPROVE_OVER_CAP` sobre `social_contribution` (`SOCIAL_MANAGER`), que recibe la advertencia; con la política en
  falso, siempre advertencia. Un monto en otra moneda o una campaña sin moneda dan `CAP_OTHER_CURRENCY`, siempre
  advertencia. Un aporte sin campaña o una campaña sin tope no se juzgan (RN-INV-003, GAP-006, RQ-SEC-004, ASM-063).
- **P2 y P3. Lineamientos** (`ContributionGuidelines`, ASM-112), con cada política activa del proyecto: `PAID_LABOR`
  (`policy_no_paid_labor`: algún ítem MANO DE OBRA) y `MONETARY_DONATION` (`policy_non_monetary_donations`: una
  DONACION con algún ítem DINERO), sobre `contribution_item`, y `PRIORITY_NOT_MET` (`policy_priority_warning`: el
  proyecto tiene temas priorizados y el aporte no tiene `topic` o su tema no es priorizado ni desciende de uno), sobre
  `topic`. Cada incumplimiento es `409` si `guideline_deviation_justification` está vacía y advertencia si está
  informada: la justificación cubre todos a la vez (RN-INV-006). Un proyecto sin temas priorizados no evalúa la
  prioridad y advierte `PRIORITIES_NOT_CONFIGURED` (`topic`), que nunca exige justificación. «Caseríos sin beneficio
  laboral» no se evalúa (GAP-033). Una política apagada no se evalúa (RN-INV-001, RN-INV-002, RN-INV-005).
- **P4. Doble llave:** la acción declarada `APPROVE` sobre `social_contribution` (RF-WFL-002); sin ella,
  `403 ACTION_REQUIRED` sobre `record`.
- **P5. Segregación:** quien aprueba no es `created_by` del aporte, quien lo registró o quien aceptó la solicitud que lo
  creó (ASM-008, ASM-117), como D4; si no, `403 SEGREGATION_OF_DUTIES` sobre `record`.
- **P6. Sinergia** (RN-INV-004, ASM-112): con `has_institutional_synergy` verdadero y
  `policy_synergy_requires_agreement` activa, un `agreement` en `active` y con `validity_status` distinto de VENCIDO;
  si no, `409 SYNERGY_AGREEMENT_REQUIRED` sobre `agreement`, también con justificación. No es un lineamiento que se
  justifique, y el aporte no tiene socio que deba ser parte (G3 de WF-G sí lo exige). El acuerdo es del mismo proyecto
  (`ProjectLinkGuard`).
- **P7. Sin aprobación interna:** solo con `policy_internal_approval` en falso (ASM-008); si no,
  `409 INTERNAL_APPROVAL_REQUIRED` sobre `project`.
- **P8. Entrega con conformidad y evidencia** [DOC-02 p.4] (ASM-111, ASM-116): la única `delivery` del aporte, con
  `conformity_given` verdadero y `conformity_by` informado, vinculada a al menos una evidencia ACTA DE RECEPCION, una
  FOTO y un COMPROBANTE del proyecto del aporte. Sin la entrega o sin la conformidad, `409 DELIVERY_REQUIRED` sobre
  `delivery`; si quien la aplica no lee `conformity_by`, `403 FIELD_NOT_READABLE` sobre `delivery.conformity_by`
  (como D9 de WF-D); sin las evidencias, `409 EVIDENCE_REQUIRED` sobre `evidence_delivery`. Toma el candado del aporte.
- **P9. Ítems al aprobar** (ASM-110, ASM-112): al menos un `contribution_item` (`409 ITEMS_REQUIRED` sobre
  `contribution_item`) y `contribution_type` informado (`400 FIELD_REQUIRED`).

**Orden y candados.** Una aprobación toma primero el candado del aporte (`social_contribution:<id>`), el mismo de las
escrituras de sus ítems y de su entrega, así un ítem no se cuela entre la suma y la transición; después corren P7
(`approve_direct`), P9, P4 y P5 (`approve`), P6, P2 y P3 y al final P1, la única que toma el candado de la campaña
(`exploration_campaign:<id>`). El orden de los candados es siempre aporte y después campaña. La escritura se detiene en
el primer bloqueo; la consulta en seco (`transition-checks`) los junta todos, en ese orden y sin tomar candados. Una
regla que no puede leer lo que necesita rechaza (`403 OBJECT_NOT_READABLE`): nunca deja pasar.

**Otras reglas de `InvestmentWriteGuard` sobre el aporte:**

- **Ítems congelados (ASM-110):** el alta, el cambio o la baja de un `contribution_item` cuyo aporte no está en
  `requested`, o el cambio del `social_contribution` de un ítem en cualquier estado, es `409 ITEMS_FROZEN` sobre
  `social_contribution`; la regla toma el candado del aporte.
- **Términos aprobados (ASM-110):** fuera de `requested`, un cambio de `currency`, `contribution_type`, `topic`,
  `exploration_campaign`, `has_institutional_synergy`, `agreement`, `guideline_deviation_justification` o `project` es
  `409 CONTRIBUTION_FROZEN` sobre el campo.
- **Conteos (ASM-118):** `beneficiaries_count` negativo o no entero es `400 OUT_OF_RANGE`.
- **Entrega (ASM-111):** una segunda entrega del aporte es `409 DELIVERY_EXISTS` sobre `social_contribution` (la guarda
  toma el candado del aporte; el único del modelo es la última barrera); con el aporte fuera de `procured`, un cambio de
  la entrega o el alta o la baja de un vínculo `evidence_delivery` es `409 DELIVERY_FROZEN` sobre
  `social_contribution`; `delivered_amount` informado es `400 FIELD_NOT_ALLOWED`. La regla de compromisos
  (`DELIVERY_FROZEN` sobre `commitment`, `sgspe.commitment`) solo mira las entregas con `commitment`.

`POST /api/sgspe/social-contributions/{id}/deliveries` solo captura datos, con el aporte en `procured`: crea la única
`delivery` y sus vínculos de evidencia en una transacción, sin cambiar el estado. El cambio de estado es la transición
humana `register_delivery` (modelo de dominio §7.2). El «Acta de recepción» (AUT-04) espera a UPS-23: el acta se
imprime fuera de SGSPE, se firma en la entrega y se sube como evidencia ACTA DE RECEPCION (ASM-116).
```

- [ ] **Paso 6: Listeners y avisos**

En `docs/workflows.md`, reemplazar:

```markdown
| `ContributionTotalsListener` (`sgspe.investment`) | Alta, cambio o baja de `contribution_item` | Ninguna (alimenta `register_procurement`) | `total_amount` de la `social_contribution` | Ídem | No falla; JOB-02 recalcula | `UPDATE` sobre `social_contribution`, con motivo |
```

por:

```markdown
| `ContributionTotalsListener` (`sgspe.investment`) | Alta, cambio o baja de `contribution_item`; `approve` y `approve_direct` de WF-P (ASM-110) | `approve` y `approve_direct`, en las que escribe el total aprobado | `total_amount` de la `social_contribution`, con la marca y solo si cambia | En las transiciones, sí; en un ítem, solo si llega por un endpoint con transacción | No falla; JOB-02 recalcula | `UPDATE` sobre `social_contribution`, con motivo: lo tienen quienes cargan ítems (`SOCIAL_INVESTMENT_MANAGER`, `SOCIAL_MANAGER`, `COMMUNITY_RELATIONS_OFFICER`) y quienes aprueban (`APPROVER`, con `u`) |
| `ProgramTotalsListener` (`sgspe.investment`, F5) | Alta, cambio o baja de `program_budget` | Ninguna | `total_budget` del `social_program`: suma de `allocated_amount` en la moneda del programa (ASM-113), con la marca y solo si cambia | Solo si la escritura llega por un endpoint con transacción | No falla; JOB-02 recalcula | `UPDATE` sobre `social_program`, que tiene `SOCIAL_INVESTMENT_MANAGER`, el único que escribe presupuestos |
```

En `docs/workflows.md`, reemplazar:

```markdown
| `ApprovalNoticeListener` (`sgspe.notification`) | Envío a aprobación (`submit_for_review` de WF-A, `submit_minutes` de WF-C, `submit` de WF-D) y toda transición que sale del estado pendiente | Las mismas |
```

por:

```markdown
| `ApprovalNoticeListener` (`sgspe.notification`) | Envío a aprobación (`submit_for_review` de WF-A, `submit_minutes` de WF-C, `submit` de WF-D y, desde F5, `submit` de WF-G) y toda transición que sale del estado pendiente; desde F5, también el alta de un `social_contribution` (modo «al crear» de `ApprovalStep`), cuyo aviso se resuelve al salir de `requested` | Las mismas; el alta del aporte no es una transición |
```

En `docs/workflows.md`, reemplazar:

```markdown
de QCS, riesgos y alertas llegan en F4 con sus objetos. La columna `notification_type` de la tabla siguiente es el
```

por:

```markdown
de QCS, riesgos y alertas llegan en F4 con sus objetos, y los de aprobación de programas y aportes, en F5, en la fuente
`sgspe.approvals`. La columna `notification_type` de la tabla siguiente es el
```

En `docs/workflows.md`, reemplazar:

```markdown
| `ApprovalNoticeListener` (WF-G, WF-H y `social_contribution` suman su paso a `ApprovalNoticeListener.STEPS` en su fase); el de una queja grave, `GraveGrievanceNoticeListener` (F4) |
```

por:

```markdown
| `ApprovalNoticeListener` (WF-G y el alta de `social_contribution` suman su paso a `ApprovalNoticeListener.STEPS` en F5, este con el modo «al crear» de `ApprovalStep`; WF-H, en F8); el de una queja grave, `GraveGrievanceNoticeListener` (F4) |
```

En `docs/workflows.md`, reemplazar:

```markdown
`approval_pending:<objeto>:<id>`: volver a enviar reabre el mismo aviso; el de una queja se resuelve al salir de `in_action` o si la severidad deja de ser ALTA o CRITICA |
```

por:

```markdown
`approval_pending:<objeto>:<id>`: volver a enviar reabre el mismo aviso; el de una queja se resuelve al salir de `in_action` o si la severidad deja de ser ALTA o CRITICA; el de un aporte, al salir de `requested` (llega al alta, antes de los ítems: quien aprueba ve el total al abrirlo, y la aprobación sin ítems responde `409 ITEMS_REQUIRED`) |
```

En `docs/workflows.md`, reemplazar:

```markdown
Los textos de los avisos (`NoticeTexts`) llevan el código, el tipo y la fecha; nunca descripción, persona ni texto
libre. El de `qcs_confidential_overdue` es fijo salvo el nombre del proyecto: «Una QCS confidencial del proyecto
```

por:

```markdown
Los textos de los avisos (`NoticeTexts`) llevan el código, el tipo y la fecha; nunca descripción, persona ni texto
libre. El de un programa lleva su código y su nombre, y el de un aporte, su código y su tipo; nunca propósito,
condiciones ni receptor. F5 no agrega avisos de plazo: los hitos de programa no escalan. El de
`qcs_confidential_overdue` es fijo salvo el nombre del proyecto: «Una QCS confidencial del proyecto
```

- [ ] **Paso 7: Pruebas del modelo y por workflow**

En `docs/workflows.md`, reemplazar:

```markdown
  tras UPS-23» o «F4, tras UPS-23»), y ningún tipo de documento se declara sobre un objeto con campos restringidos ni
```

por:

```markdown
  tras UPS-23», «F4, tras UPS-23» o «F5, tras UPS-23», como AUT-04), y ningún tipo de documento se declara sobre un
  objeto con campos restringidos ni
```

En `docs/workflows.md`, reemplazar:

```markdown
| WF-E3 | `StakeholderRequestWorkflowApiTest` | `accept` por la API genérica → rechazada; `accept` sin `response` o sin `responded_at` → rechazada; el endpoint crea el compromiso en `draft`; `close_accepted` por la API genérica → rechazada; el listener la cierra con motivo; desde F5, cerrar el aporte también la cierra; `close_unfulfilled` solo con el compromiso o el aporte cancelado o rechazado |
```

por:

```markdown
| WF-E3 | `StakeholderRequestWorkflowApiTest` | `accept` por la API genérica → rechazada; `accept` sin `response` o sin `responded_at` → rechazada; el endpoint crea el compromiso en `draft`; `close_accepted` por la API genérica → rechazada; el listener la cierra con motivo; `close_unfulfilled` solo con el compromiso cancelado. Desde F5 (ASM-117): aceptar como aporte por `SOCIAL_INVESTMENT_MANAGER` crea el aporte en `requested` con `created_contribution`; por `COMMITMENT_MANAGER` → `403`; `created_contribution` por la API genérica → `403 COMMAND_ONLY`; cerrar el aporte cierra la solicitud en la misma transacción, también una confidencial; `close_unfulfilled` con el aporte rechazado → permitida, con el aporte aprobado → `409` |
```

En `docs/workflows.md`, reemplazar:

```markdown
| WF-G | `SocialProgramWorkflowApiTest` | `approve` sin acuerdo de sinergia (política activa) o por quien registró → rechazada; `plan_direct` con la política activa → rechazada |
```

por:

```markdown
| WF-G | `SocialProgramWorkflowApiTest` | `approve` sin acuerdo de sinergia con socio (política activa, G3) → `409`; con acuerdo vencido o sin el socio como parte → `409`; por quien registró (G2) → `403`; `plan_direct` con la política de aprobación interna activa (G4) → `409`; `suspend` y los `cancel_*` sin motivo → `400`; `total_budget` solo suma la moneda del programa; un grupo de beneficiarios con una iniciativa de otro programa → `409 PROGRAM_MISMATCH` |
```

En `docs/workflows.md`, reemplazar:

```markdown
| WF-P | `SocialContributionWorkflowApiTest` | `approve` por `APPROVER` o `SOCIAL_MANAGER`; con un tope que bloquea (P1), `approve` por `APPROVER`, que no tiene `APPROVE_OVER_CAP` → rechazada, `approve` por `SOCIAL_MANAGER` → permitida y `approve_direct` → rechazada; aporte con sinergia sin acuerdo (P6) → rechazado; registrar la entrega no cambia el estado; `register_delivery` sin entrega con conformidad, acta, foto o comprobante (P8) → rechazada |

### Pruebas de trabajos, listeners y automatizaciones
```

por:

```markdown
| WF-P | `SocialContributionWorkflowApiTest` | `approve` por `APPROVER` y por `SOCIAL_MANAGER`; sin ítems (P9) → `409`; sin `contribution_type` → `400`; ítem nuevo o cambiado después de aprobar → `409 ITEMS_FROZEN`; cambiar la moneda o la campaña después de aprobar → `409 CONTRIBUTION_FROZEN`; con un tope que bloquea (P1): `approve` por `APPROVER` → `409`, por `SOCIAL_MANAGER` → permitido, `approve_direct` → `409`; el tope cuenta un compromiso comprometido y otro aporte aprobado de la misma campaña; MANO DE OBRA, DINERO en DONACION y tema no priorizado: sin justificación → `409` con el código de cada uno, con justificación → permitido con advertencias; aporte con sinergia sin acuerdo, con acuerdo fuera de `active` o VENCIDO (P6) → `409 SYNERGY_AGREEMENT_REQUIRED` también con justificación, y con acuerdo vigente → permitido; aprobar por quien registró (P5) → `403`; `approve_direct` con la aprobación interna activa (P7) → `409`; capturar la entrega fuera de `procured` → `409`; una segunda entrega → `409 DELIVERY_EXISTS`; `delivered_amount` informado → `400`; registrar la entrega no cambia el estado; `register_delivery` sin conformidad, sin acta, sin foto o sin comprobante (P8) → `409`; la entrega y sus vínculos de evidencia no cambian desde `delivered`; `close` cierra la solicitud de origen |

Las de F5 parten de la base `F5ApiTest` (sobre `F4ApiTest`): aportes con ítems, una campaña con tope, un acuerdo
vigente, una entrega con sus tres evidencias y programas con presupuesto, listos para las guardas. Además:
`InvestmentDashboardApiTest` (KPI-INV-01 a 04 con montos en dos monedas; un proyecto ajeno da conjuntos vacíos);
`ProjectReadScopeApiTest` con los casos de ASM-114 (la entrega del compromiso y la del aporte, y las mediciones, se leen
por su proyecto; una entrega huérfana no la lee nadie salvo `ADMIN` y `READ_ALL_PROJECTS`); las columnas «otro
proyecto» y «no asignado» de `PermissionMatrixApiTest` y `WorkflowMatrixApiTest` con los diez objetos de BC-10; y las
unitarias `ContributionGuidelinesTest`, `SupportCapTest` con aportes, `CampaignCommittedTest`,
`ProjectReadConditionTest`, `ProjectResolverTest` y `AmountFieldsGuardTest`.

### Pruebas de trabajos, listeners y automatizaciones
```

- [ ] **Paso 8: Verificar y commit**

Repetir la comprobación del Paso 1 (esperado: `ok`) y las compuertas de «Global Constraints». `test_design_docs.py`
sigue leyendo 8 transiciones en WF-E3, `accept` y `close_accepted` con `SOCIAL_INVESTMENT_MANAGER` (que la prueba de F4
filtra) y 12 automatizaciones con AUT-04 «F5, tras UPS-23». Después:

```bash
git add docs/workflows.md
git commit -F - <<'MSG'
docs: WF-P con P1 a P9 y WF-G con G1 a G4

WF-P exige los ítems al aprobar (P9), deja la sinergia fuera de los lineamientos (P6), fija el
orden y los candados de la aprobación, congela ítems, términos y la entrega única. WF-G gana G1 a
G4 y las reglas de los hijos; WF-E3, la aceptación como aporte; listeners, avisos y pruebas de F5.

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_017MAgYcNauGp6cC3ekX1h7U
MSG
```

---

### Tarea 4: Modelo de dominio y arquitectura

El modelo de dominio fija el alcance con cadenas y padres alternativos (§4.7), el catálogo de BC-10 (§6.10), el acta de
recepción tras UPS-23 (§6.12), los invariantes de BC-10 con P1 a P9 y G1 a G4 (§7.2), los derivados y los listeners
(§7.4, §7.5) y el contenido de `sgspe.investment` (§8.2). `architecture.md` §5.2 repite el paquete completo. §7.3 (los
estados que lee `design_docs.workflow_states`) no cambia.

**Archivos:**
- Modificar: `docs/domain-model.md` (§4.7, §6.10, §6.12, §7.2, §7.4, §7.5, §8.2)
- Modificar: `docs/architecture.md` (§5.2, fila de `sgspe.investment`)
- Probar: `model/test_design_docs.py` (`workflow_states` lee §7.3), `model/test_model.py`

**Interfaces:**
- Consume: ASM-110 a ASM-119 (Tarea 1).
- Produce: §4.7 con 33 raíces y 27 hijos desde F5 y la regla de cadenas y padres alternativos; §7.2 con las filas de
  BC-10 que citan P1 a P9 y G1 a G4; §7.5 con `ContributionTotalsListener` en `approve` y `approve_direct`,
  `ProgramTotalsListener` y el modo «al crear» de `ApprovalNoticeListener`; §8.2 con las piezas de `sgspe.investment`
  (`InvestmentWriteGuard`, `ContributionCapRule`, `ContributionGuidelines`, `SupportCap`, `CampaignCommitted`,
  `ContributionTotalsListener`, `ProgramTotalsListener`).

- [ ] **Paso 1: La comprobación falla**

```bash
python3 - <<'PY'
checks = [
    ('docs/domain-model.md', '27 desde F5', True),
    ('docs/domain-model.md', '`ProgramTotalsListener` (`sgspe.investment`)', True),
    ('docs/domain-model.md', '`ContributionCapRule`', True),
    ('docs/domain-model.md', '`ITEMS_FROZEN`', True),
    ('docs/domain-model.md', 'ACTA DE RECEPCION', True),
    ('docs/domain-model.md', '(`delivery` y `locality_profile` con padre opcional)', False),
    ('docs/domain-model.md', '| `social_contribution` | No se financia mano de obra, las donaciones no son monetarias y se advierte si no atiende las prioridades | RN-INV-001, RN-INV-002, RN-INV-005 | `SupportPolicyGuard`, según las políticas del proyecto |', False),
    ('docs/architecture.md', '`ContributionGuidelines`', True),
]
bad = [f"{p}: {'falta' if want else 'sobra'} {s[:80]!r}" for p, s, want in checks
       if (s in open(p, encoding='utf-8').read()) != want]
print('\n'.join(bad) or 'ok')
raise SystemExit(1 if bad else 0)
PY
```

Esperado: salida 1 con las ocho líneas.

- [ ] **Paso 2: §4.7, el alcance con cadenas y padres alternativos**

En `docs/domain-model.md`, reemplazar:

```markdown
  grupo: `project`; `project_assignment`; las 31 raíces con `project` (`evidence`, `early_warning_rule` y `export_log` con
  `project` opcional); los 19 hijos, por su padre (`delivery` y `locality_profile` con padre opcional); y los 20 maestros del
  tenant (`TENANT`), compartidos entre los proyectos (ASM-107). Un objeto nuevo se clasifica en su fase.
```

por:

```markdown
  grupo: `project`; `project_assignment`; las raíces con `project` (31 hasta F4 y 33 desde F5, con `social_contribution`
  y `social_program`; `evidence`, `early_warning_rule` y `export_log` con `project` opcional); los hijos, por sus padres
  posibles (19 hasta F4 y 27 desde F5); y los 20 maestros del tenant (`TENANT`), compartidos entre los proyectos
  (ASM-107). Un objeto nuevo se clasifica en su fase.
- Desde F5 (ASM-114), un hijo puede tener por padre a otro hijo si la cadena llega a una raíz en a lo sumo dos saltos y
  sin ciclos (`program_measurement` → `program_indicator` → `social_program`), y puede tener varios padres posibles, de
  los que lleva exactamente uno (`delivery`: `commitment` o `social_contribution`, con `ExactlyOneGuard`): se lee por el
  padre que tiene, nunca «sin padre, todos», y una entrega sin ninguno solo la leen `ADMIN` y quien tiene
  `READ_ALL_PROJECTS`. Los nietos no copian `project`. Hasta F4, `delivery` y `locality_profile` eran los hijos con
  padre opcional, que sin él se leen en todo el tenant; desde F5 lo es solo `locality_profile`.
```

- [ ] **Paso 3: §6.10 y §6.12**

En `docs/domain-model.md`, reemplazar:

```markdown
M2O `commitment`; M2O `agreement` (sinergia) | `requiresReason`; único (`project`, `code`) | WF-P |
```

por:

```markdown
M2O `commitment`; M2O `agreement` (sinergia); M2O `topic` (tema del aporte, ASM-112) | `requiresReason`; único (`project`, `code`); ítems y términos aprobados congelados fuera de `requested` (ASM-110) | WF-P |
```

En `docs/domain-model.md`, reemplazar:

```markdown
| `contribution_item` | `description` TEXT; `quantity` DECIMAL; `unit` TEXT; `unit_cost`, `line_amount` DECIMAL | M2O `social_contribution` (requerido) | — | — | RF-INV-001 |
```

por:

```markdown
| `contribution_item` | `description` TEXT; `item_kind` ENUM (Tipo de ítem del aporte, requerido); `quantity` DECIMAL; `unit` TEXT; `unit_cost`, `line_amount` DECIMAL | M2O `social_contribution` (requerido) | Congelado fuera de `requested` del aporte (ASM-110) | — | RF-INV-001, ASM-110, ASM-112 |
```

En `docs/domain-model.md`, reemplazar:

```markdown
- La entrega de un aporte es una `delivery` con su acta de recepción y sus fotos como `evidence`; el comprobante escaneado es
  una `evidence` de tipo COMPROBANTE y sus datos estructurados viven en `receipt` [DOC-02 p.4].
```

por:

```markdown
- La entrega de un aporte es su única `delivery` (ASM-111), con el acta de recepción firmada, las fotos y el comprobante
  escaneado como `evidence` de tipo ACTA DE RECEPCION, FOTO y COMPROBANTE; los datos estructurados del comprobante viven
  en `receipt`, que es opcional (ASM-116) [DOC-02 p.4].
- Los lineamientos de apoyo se evalúan al aprobar con el tipo de cada ítem (`item_kind`), el tema del aporte y los
  temas priorizados del proyecto (`project_priority_topic`, M2M de `project` con `topic`, ASM-112); el tope de la
  campaña se compara con lo comprometido de compromisos y aportes (ASM-110), y el ejecutado de un programa se registra a
  mano por año y moneda (ASM-113).
```

En `docs/domain-model.md`, reemplazar:

```markdown
  queja y las cartas de respuesta (las tres QCS restringen `person`, ASM-098) esperan a UPS-23.
```

por:

```markdown
  queja y las cartas de respuesta (las tres QCS restringen `person`, ASM-098) esperan a UPS-23; mientras tanto, las
  actas firmadas se suben como evidencias ACTA DE ENTREGA y ACTA DE RECEPCION (ASM-090, ASM-116).
```

- [ ] **Paso 4: §7.2, los invariantes de BC-10 y de la entrega**

En `docs/domain-model.md`, reemplazar:

```markdown
| `delivery` | Pertenece a exactamente un compromiso o un aporte; `delivered_amount` solo se llena en las entregas de compromisos | RF-COM-008, RF-INV-001, ASM-063 | `ExactlyOneGuard` y los endpoints de entregas |
```

por:

```markdown
| `delivery` | Pertenece a exactamente un compromiso o un aporte; `delivered_amount` solo se llena en las entregas de compromisos; un aporte tiene a lo sumo una entrega, que no cambia desde `delivered` (ASM-111); se lee por el proyecto del padre que tiene (ASM-114) | RF-COM-008, RF-INV-001, ASM-063, ASM-111, ASM-114 | `ExactlyOneGuard`, los endpoints de entregas, `InvestmentWriteGuard` (entrega única con el candado del aporte, `DELIVERY_EXISTS` y `DELIVERY_FROZEN`) y `ProjectReadScope` |
```

En `docs/domain-model.md`, reemplazar:

```markdown
| `social_contribution` (+ `contribution_item`, `receipt`, `delivery`) | Propósito, condiciones y pagador declarados; si se aparta de los lineamientos, justificación | RN-INV-006, CON-021, CON-022 | Metadata (`required`) y `SupportPolicyGuard` (`sgspe.investment`) |
| `social_contribution` | No se financia mano de obra, las donaciones no son monetarias y se advierte si no atiende las prioridades | RN-INV-001, RN-INV-002, RN-INV-005 | `SupportPolicyGuard`, según las políticas del proyecto |
| `social_contribution` | Llegar a `approved` verifica el mismo tope, contra los montos aprobados de la campaña | RN-INV-003, ASM-063 | `SupportPolicyGuard` |
| `social_contribution` | Un aporte con `has_institutional_synergy` exige un `agreement` vigente antes de llegar a `approved` | RN-INV-004 | `InvestmentWriteGuard`, según `policy_synergy_requires_agreement` |
| `social_contribution` | Llegar a `delivered` exige acta de recepción, foto y comprobante | RF-INV-001 [DOC-02 p.4] | El endpoint `POST /api/sgspe/social-contributions/{id}/deliveries` solo captura datos (entrega y vínculos de evidencia, en una transacción); el cambio de estado es la transición humana de WF-P hacia `delivered`, con `InvestmentWriteGuard` (`sgspe.investment`): exige una entrega con su acta de recepción, foto y comprobante |
| `social_contribution` | `total_amount` es la suma de `line_amount` de sus ítems, recalculada desde cero | RF-INV-001 | `ContributionTotalsListener` (`sgspe.investment`) y `DerivedFieldsJob` (§7.5) |
| `social_program` (+ iniciativas, beneficiarios, presupuesto, indicadores, hitos) | Llegar a `planned` exige el acuerdo de sinergia con el socio | RN-INV-004 | `InvestmentWriteGuard` |
| `program_budget`, `program_measurement` | Un presupuesto por año; mediciones inmutables | RF-INV-003, RQ-AUD-003 | Metadata (único compuesto, `appendOnly`) |
```

por:

```markdown
| `social_contribution` (+ `contribution_item`, `receipt`, `delivery`) | Propósito, condiciones y pagador declarados; si se aparta de los lineamientos, una justificación que cubre todos los incumplidos | RN-INV-006, CON-021, CON-022, ASM-112 | Metadata (`required`) e `InvestmentWriteGuard` (`sgspe.investment`), con `ContributionGuidelines` |
| `social_contribution` | No se financia mano de obra (ningún ítem MANO DE OBRA), las donaciones no son monetarias (ningún ítem DINERO en una DONACION) y el tema es un tema priorizado del proyecto o desciende de uno; cada incumplimiento bloquea sin justificación y es advertencia con ella; sin temas priorizados, solo se advierte | RN-INV-001, RN-INV-002, RN-INV-005, ASM-112, GAP-033 | `InvestmentWriteGuard` con `ContributionGuidelines` (P2 y P3), según las políticas del proyecto |
| `social_contribution` | Llegar a `approved` exige ítems y `contribution_type`, y verifica el tope de la campaña con la suma de los ítems, leída en la transacción, contra lo comprometido de compromisos y aportes | RN-INV-003, ASM-063, ASM-110, ASM-119 | `InvestmentWriteGuard` (P9) y `ContributionCapRule` con `CampaignCommitted` (P1), con los candados del aporte y de la campaña |
| `social_contribution` (+ `contribution_item`) | Fuera de `requested`, los ítems y los términos que juzgó la aprobación no cambian: un cambio es otro aporte | ASM-110 | `InvestmentWriteGuard` (`ITEMS_FROZEN`, `CONTRIBUTION_FROZEN`) |
| `social_contribution` | Un aporte con `has_institutional_synergy` exige un `agreement` en `active` y no VENCIDO antes de llegar a `approved`, sin justificación posible | RN-INV-004, ASM-112 | `InvestmentWriteGuard` (P6), según `policy_synergy_requires_agreement` |
| `social_contribution` | Llegar a `approved` por `approve` exige `APPROVE` y que quien aprueba no lo haya registrado ni aceptado su solicitud; `approve_direct`, la aprobación interna apagada | ASM-008, ASM-117, RF-WFL-002 | `InvestmentWriteGuard` (P4, P5 y P7) |
| `social_contribution` | Llegar a `delivered` exige su única entrega, con conformidad y las evidencias ACTA DE RECEPCION, FOTO y COMPROBANTE | RF-INV-001, ASM-111, ASM-116 [DOC-02 p.4] | El endpoint `POST /api/sgspe/social-contributions/{id}/deliveries` solo captura datos (la entrega y sus vínculos de evidencia, en una transacción, con el aporte en `procured`); el cambio de estado es la transición humana `register_delivery` de WF-P, con `InvestmentWriteGuard` (P8) |
| `social_contribution` | `total_amount` es la suma de `line_amount` de sus ítems, recalculada desde cero, y queda escrita en la aprobación | RF-INV-001, ASM-110 | `ContributionTotalsListener` (`sgspe.investment`) y `DerivedFieldsJob` (§7.5) |
| `social_program` (+ iniciativas, beneficiarios, presupuesto, indicadores, hitos) | Llegar a `planned` exige, con socio y la política activa, un acuerdo en `active`, no VENCIDO y con el socio como parte; por `approve`, `APPROVE` y que quien aprueba no lo haya registrado; por `plan_direct`, la aprobación interna apagada | RN-INV-004, ASM-008, ASM-115 | `InvestmentWriteGuard` (G1 a G4) |
| `beneficiary_group` | Su iniciativa es del mismo programa | RF-INV-003 | `InvestmentWriteGuard` (`PROGRAM_MISMATCH`) |
| `program_budget`, `program_measurement` | Un presupuesto por año; mediciones inmutables; `total_budget` del programa en su moneda | RF-INV-003, RQ-AUD-003, ASM-113 | Metadata (único compuesto, `appendOnly`); `ProgramTotalsListener` y `DerivedFieldsJob` |
```

- [ ] **Paso 5: §7.4 y §7.5, derivados y listeners**

En `docs/domain-model.md`, reemplazar:

```markdown
| `social_contribution.total_amount` | Suma de `line_amount` de sus ítems | `ContributionTotalsListener`; `DerivedFieldsJob` | RF-INV-001 |
| `social_program.total_budget` | Suma de `allocated_amount` de sus `program_budget` | `DerivedFieldsJob` | RF-INV-003 |
```

por:

```markdown
| `social_contribution.total_amount` | Suma de `line_amount` de sus ítems; se escribe también en `approve` y `approve_direct`, así lo aprobado queda escrito (ASM-110) | `ContributionTotalsListener`; `DerivedFieldsJob` | RF-INV-001, ASM-110 |
| `social_program.total_budget` | Suma de `allocated_amount` de sus `program_budget` en la moneda del programa; sin moneda del programa, vacío (ASM-113) | `ProgramTotalsListener`; `DerivedFieldsJob` | RF-INV-003, ASM-113 |
```

En `docs/domain-model.md`, reemplazar:

```markdown
| `ContributionTotalsListener` (`sgspe.investment`) | Alta, cambio o baja de `contribution_item` | `total_amount` de la `social_contribution` | UPDATE sobre `social_contribution`, con motivo porque es `requiresReason` | No falla la escritura; `DerivedFieldsJob` recalcula |
```

por:

```markdown
| `ContributionTotalsListener` (`sgspe.investment`) | Alta, cambio o baja de `contribution_item`; `approve` y `approve_direct` de WF-P (ASM-110) | `total_amount` de la `social_contribution` | UPDATE sobre `social_contribution`, con motivo porque es `requiresReason` | No falla la escritura; `DerivedFieldsJob` recalcula |
| `ProgramTotalsListener` (`sgspe.investment`) | Alta, cambio o baja de `program_budget` | `total_budget` del `social_program`, en su moneda (ASM-113) | UPDATE sobre `social_program` | No falla la escritura; `DerivedFieldsJob` recalcula |
```

En `docs/domain-model.md`, reemplazar:

```markdown
Envío a aprobación y salida del estado pendiente (WF-A, WF-C, WF-D);
```

por:

```markdown
Envío a aprobación y salida del estado pendiente (WF-A, WF-C, WF-D y, desde F5, WF-G); desde F5, el alta de un `social_contribution` y su salida de `requested` (modo «al crear» de `ApprovalStep`);
```

- [ ] **Paso 6: §8.2, el código propio por paquete**

En `docs/domain-model.md`, reemplazar:

```markdown
`ConfidentialAttentionScope` (`RecordReadScope` de las QCS confidenciales, ASM-095); adaptador
```

por:

```markdown
`ConfidentialAttentionScope` (`RecordReadScope` de las QCS confidenciales, ASM-095); desde el plan 4.4, `ProjectReadScope` y `ProjectScope`, que desde F5 admite cadenas de hijos y padres alternativos, con `ProjectResolver` y `ProjectLinks` (ASM-114); adaptador
```

En `docs/domain-model.md`, reemplazar:

```markdown
aceptación de solicitudes; `RequestClosureListener`
```

por:

```markdown
aceptación de solicitudes (como compromiso y, desde F5, como aporte, con `created_contribution` como campo de comando, ASM-117); `RequestClosureListener`
```

En `docs/domain-model.md`, reemplazar:

```markdown
`ApprovalNoticeListener`; desde F4, `RiskNoticeListener`
```

por:

```markdown
`ApprovalNoticeListener` (desde F5, con WF-G y el alta del aporte); desde F4, `RiskNoticeListener`
```

En `docs/domain-model.md`, reemplazar:

```markdown
| `sgspe.investment` | `SupportPolicyGuard` (tope de apoyo social por campaña sobre compromisos y aportes; lineamientos de aportes) e `InvestmentWriteGuard` (entrega de aportes con acta, foto y comprobante; sinergia antes de aprobar un programa); endpoint de entrega de aportes (solo captura datos; la entrega es una transición de WF-P); `ContributionTotalsListener`; recalculadores de `total_amount`, `total_budget`, `executed_social_amount` y `other_currency_items_count` (ASM-063) | BC-10 | F5 (tope y lineamientos desde F3, RF-INV-002) | Políticas del proyecto y sumas entre objetos (RN-INV-003, RN-INV-004, RN-INV-006, RF-INV-004) |
```

por:

```markdown
| `sgspe.investment` | `SupportPolicyGuard` (tope de apoyo social por campaña al aprobar compromisos, que desde F5 cuenta también los aportes comprometidos) e `InvestmentWriteGuard` (WF-P: P1 a P9 con `ContributionCapRule` y `ContributionGuidelines`, ítems y términos congelados, entrega única y congelada; WF-G: G1 a G4 y las reglas de los hijos del programa); `SupportCap` y `CampaignCommitted`, comunes a las dos reglas del tope; endpoint de entrega de aportes (`ContributionDeliveries`: solo captura datos; la entrega es una transición de WF-P); `ContributionTotalsListener` y `ProgramTotalsListener`; recalculadores de `total_amount`, `total_budget`, `executed_social_amount` y `other_currency_items_count` (ASM-063, ASM-110 a ASM-113) | BC-10 | F5 (tope y lineamientos desde F3, RF-INV-002) | Políticas del proyecto y sumas entre objetos (RN-INV-003, RN-INV-004, RN-INV-006, RF-INV-004) |
```

- [ ] **Paso 7: `architecture.md` §5.2**

En `docs/architecture.md`, reemplazar:

```markdown
| `sgspe.investment` | `SupportPolicyGuard` (desde F3, con el candado de la campaña), el recalculador de `executed_social_amount` de JOB-02 (desde F3), `InvestmentWriteGuard`, entrega de aportes, `ContributionTotalsListener` | BC-10 | F5 (tope y lineamientos en F3) |
```

por:

```markdown
| `sgspe.investment` | `SupportPolicyGuard` (desde F3, con el candado de la campaña) y el recalculador de `executed_social_amount` de JOB-02 (desde F3); desde F5, `InvestmentWriteGuard` (WF-P y WF-G), `ContributionCapRule`, `ContributionGuidelines`, `SupportCap` y `CampaignCommitted` (que también usa `SupportPolicyGuard`), el endpoint de entrega de aportes (`ContributionDeliveries`), `ContributionTotalsListener`, `ProgramTotalsListener` y sus recalculadores | BC-10 | F5 (tope y lineamientos en F3) |
```

- [ ] **Paso 8: Verificar y commit**

Repetir la comprobación del Paso 1 (esperado: `ok`) y las compuertas de «Global Constraints» (`workflow_states`
sigue leyendo 19 workflows y 113 estados de §7.3, que no cambia). Después:

```bash
git add docs/domain-model.md docs/architecture.md
git commit -F - <<'MSG'
docs: dominio de F5 con P1 a P9, G1 a G4 y el alcance de dos padres

El alcance por proyecto suma las cadenas de hijos y la entrega con dos padres (33 raíces y 27 hijos
desde F5); BC-10 gana sus invariantes, ProgramTotalsListener y el aviso al crear el aporte; el
paquete sgspe.investment se describe completo en el dominio y en la arquitectura.

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_017MAgYcNauGp6cC3ekX1h7U
MSG
```

---

### Tarea 5: Seguridad: permisos de BC-10, listeners y el alcance con cadenas y dos padres

`security.md` cierra C5 (permisos que no cerraban: §7.7, §8.2), C6 (la autoridad del tope, §5.3 y §7.7), C13 y C14
(escritores de `ContributionTotalsListener` y avisos al alta, §8.1) y C2 y C3 (el alcance con cadenas y padres
alternativos, §9.7, con su nota en ADR-0029 y en la especificación 4.4). La matriz solo cambia celdas de objetos de F5
(decisión 2 de este plan): `u` sobre `topic` para `PROJECT_ADMIN` y `PROJECT_MANAGER` va como nota de §7.7.

**Archivos:**
- Modificar: `docs/security.md` (§5.3, §7.1, §7.6, §7.7, §8.1, §8.2, §9.7)
- Modificar: `docs/adr/0029-alcance-de-lectura-por-proyecto.md` (nota de F5 en «Decisión»)
- Modificar: `docs/superpowers/specs/2026-10-10-alcance-por-proyecto-design.md` (notas de F5 en §3.1 y §3.5)
- Probar: `model/test_design_docs.py` (`matrix`, `role_phases`), `model/test_apply_roles.py`

**Interfaces:**
- Consume: ASM-110, ASM-112, ASM-114, ASM-117 y ASM-119 (Tarea 1).
- Produce: la matriz de §7.7 con `contribution_item` CRUD para SMG y CRO y `receipt` CRU para CRO (la lee el plan 5.1
  para `roles.json`); la nota de `u` sobre `topic` para PAD y PMG; las siete reglas de `ArchitectureTest` y la forma de
  `ProjectScope.CHILDREN` de §9.7 (las implementa el plan 5.1).

- [ ] **Paso 1: La comprobación falla**

```bash
python3 - <<'PY'
checks = [
    ('docs/security.md', '| `contribution_item` | — | R | CRUD | CRUD | R | R | R | R | CRUD | R | — | R | R | R |', True),
    ('docs/security.md', '| `receipt` | — | R | R | CRU | R | R | R | R | CRU | R | — | R | R | R |', True),
    ('docs/security.md', '`ProgramTotalsListener` (`sgspe.investment`)', True),
    ('docs/security.md', '**Cadenas y padres alternativos (desde F5, ASM-114):**', True),
    ('docs/security.md', '(ASM-119)', True),
    ('docs/security.md', 'Cuando F5 dé a', False),
    ('docs/adr/0029-alcance-de-lectura-por-proyecto.md', 'ASM-114', True),
    ('docs/superpowers/specs/2026-10-10-alcance-por-proyecto-design.md', 'ASM-114', True),
]
bad = [f"{p}: {'falta' if want else 'sobra'} {s[:80]!r}" for p, s, want in checks
       if (s in open(p, encoding='utf-8').read()) != want]
print('\n'.join(bad) or 'ok')
raise SystemExit(1 if bad else 0)
PY
```

Esperado: salida 1 con las ocho líneas.

- [ ] **Paso 2: §5.3 y §7.1**

En `docs/security.md`, reemplazar:

```markdown
  `SOCIAL_MANAGER` y la usa en `approve` de WF-P. Quien tiene la acción necesita también un rol de esa transición.
```

por:

```markdown
  `SOCIAL_MANAGER` y la usa en `approve` de WF-P: la diferencia entre los dos objetos es aceptada (ASM-119). Quien
  tiene la acción necesita también un rol de esa transición.
```

En `docs/security.md`, reemplazar:

```markdown
| CRO | `COMMUNITY_RELATIONS_OFFICER` | F2 | Territorio, actores, interacciones, compromisos, recepción de QCS, incidentes, entregas de aportes y secciones de entregables |
```

por:

```markdown
| CRO | `COMMUNITY_RELATIONS_OFFICER` | F2 | Territorio, actores, interacciones, compromisos, recepción de QCS, incidentes, aportes con sus ítems, comprobantes y entregas, y secciones de entregables |
```

En `docs/security.md`, reemplazar:

```markdown
| SIM | `SOCIAL_INVESTMENT_MANAGER` | F5 | Aportes y programas sociales |
```

por:

```markdown
| SIM | `SOCIAL_INVESTMENT_MANAGER` | F5 | Aportes y programas sociales; único dueño de los datos de los programas |
```

- [ ] **Paso 3: §7.6 y §7.7, aceptación como aporte y la matriz de BC-10**

En `docs/security.md`, reemplazar:

```markdown
  solicitudes y `RequestClosureListener` al cerrar un compromiso (§8.1); `SOCIAL_INVESTMENT_MANAGER` lo usa desde F5 para
  aceptar solicitudes de aporte.
```

por:

```markdown
  solicitudes y `RequestClosureListener` al cerrar un compromiso (§8.1); `SOCIAL_INVESTMENT_MANAGER` lo usa desde F5 para
  aceptar solicitudes como aporte, que exige además CREATE sobre `social_contribution`: `COMMITMENT_MANAGER` no lo
  tiene y acepta solo como compromiso (ASM-117).
```

En `docs/security.md`, reemplazar:

```markdown
| `contribution_item` | — | R | R | R | R | R | R | R | CRUD | R | — | R | R | R |
| `receipt` | — | R | R | R | R | R | R | R | CRU | R | — | R | R | R |
```

por:

```markdown
| `contribution_item` | — | R | CRUD | CRUD | R | R | R | R | CRUD | R | — | R | R | R |
| `receipt` | — | R | R | CRU | R | R | R | R | CRU | R | — | R | R | R |
```

En `docs/security.md`, reemplazar:

```markdown
- `COMMUNITY_RELATIONS_OFFICER` registra pedidos de apoyo y sus entregas; `SOCIAL_MANAGER` y `SOCIAL_INVESTMENT_MANAGER`
  crean el aporte al aceptar una solicitud.
- Aprueban los aportes `APPROVER` y `SOCIAL_MANAGER`; si el aporte supera el tope de la campaña y la política bloquea,
  hace falta `APPROVE_OVER_CAP`, que tiene `SOCIAL_MANAGER` (GAP-006).
- El tope de apoyo social por campaña lo verifica `SupportPolicyGuard` desde F3 (RN-INV-003, RF-INV-002).
```

por:

```markdown
- Registran el aporte, con sus ítems antes de aprobar, `SOCIAL_MANAGER`, `COMMUNITY_RELATIONS_OFFICER` y
  `SOCIAL_INVESTMENT_MANAGER` (CRUD sobre `contribution_item`); fuera de `requested`, `InvestmentWriteGuard` congela los
  ítems (ASM-110). `COMMUNITY_RELATIONS_OFFICER` y `SOCIAL_INVESTMENT_MANAGER` registran la entrega y su comprobante
  (CRU sobre `delivery` y `receipt`).
- Crean el aporte al aceptar una solicitud `SOCIAL_MANAGER` y `SOCIAL_INVESTMENT_MANAGER`, que tienen CREATE sobre
  `social_contribution`; `COMMITMENT_MANAGER`, que tiene `accept` pero no ese CREATE, acepta solo como compromiso
  (`403`, ASM-117).
- Aprueban los aportes `APPROVER` y `SOCIAL_MANAGER`; si el aporte supera el tope de la campaña y la política bloquea,
  hace falta `APPROVE_OVER_CAP`, que tiene `SOCIAL_MANAGER` (GAP-006). En compromisos la tiene `PROJECT_MANAGER`: la
  diferencia es aceptada (ASM-119).
- El tope de apoyo social por campaña lo verifica `SupportPolicyGuard` desde F3 (RN-INV-003, RF-INV-002) y, desde F5,
  `ContributionCapRule` en los aportes, con lo comprometido de los dos objetos (ASM-110).
- En los programas, `SOCIAL_MANAGER` conserva RU sobre `social_program` y R sobre sus hijos: conduce la evaluación, la
  suspensión y el cierre. Los datos del programa (presupuesto y ejecutado, indicadores, mediciones, iniciativas, hitos y
  beneficiarios) tienen un solo dueño, `SOCIAL_INVESTMENT_MANAGER`, que borra iniciativas, grupos, indicadores e hitos;
  un indicador con mediciones no se borra (`409` de wasichai sin código, porque `program_measurement` es `appendOnly`).
- Desde F5, `PROJECT_ADMIN` y `PROJECT_MANAGER`, que editan las políticas del proyecto, vinculan sus temas priorizados
  (`project_priority_topic`, ASM-112): el vínculo exige UPDATE sobre `project` y sobre `topic` (§5.6), así que pasan de
  R a `Ru` sobre `topic`. Como la matriz no lleva fase por celda y `topic` es de F2, la celda de §7.4 cambia junto con
  `model/roles.json` al aplicar el modelo de F5.
```

- [ ] **Paso 4: §8.1, listeners**

En `docs/security.md`, reemplazar:

```markdown
| `ContributionTotalsListener` (`sgspe.investment`) | `total_amount` de `social_contribution` | UPDATE sobre `social_contribution`, con motivo | `SOCIAL_INVESTMENT_MANAGER` | CRU sobre `social_contribution` | — |
```

por:

```markdown
| `ContributionTotalsListener` (`sgspe.investment`) | `total_amount` de `social_contribution`, con cada ítem y en `approve` y `approve_direct` (ASM-110) | UPDATE sobre `social_contribution`, con motivo | Ítems: `SOCIAL_INVESTMENT_MANAGER`, `SOCIAL_MANAGER`, `COMMUNITY_RELATIONS_OFFICER`; aprobación: `APPROVER`, `SOCIAL_MANAGER`, `SOCIAL_INVESTMENT_MANAGER` | CRU sobre `social_contribution`; `APPROVER` tiene `u`, y bajo `u` el derivado sigue escribible | Sin permisos sobre BC-10 |
| `ProgramTotalsListener` (`sgspe.investment`) | `total_budget` de `social_program` (ASM-113) | UPDATE sobre `social_program` | `SOCIAL_INVESTMENT_MANAGER`, el único que escribe presupuestos | CRU sobre `social_program` | Sin permisos sobre BC-10 |
```

En `docs/security.md`, reemplazar:

```markdown
| Los de las transiciones de envío: `submit_for_review` de WF-A, `submit_minutes` de WF-C y `submit` de WF-D |
```

por:

```markdown
| Los de las transiciones de envío: `submit_for_review` de WF-A, `submit_minutes` de WF-C, `submit` de WF-D y, desde F5, `submit` de WF-G (`SOCIAL_INVESTMENT_MANAGER`); desde F5, también quienes crean un `social_contribution` (`SOCIAL_MANAGER`, `COMMUNITY_RELATIONS_OFFICER`, `SOCIAL_INVESTMENT_MANAGER`), por la API genérica o al aceptar una solicitud |
```

- [ ] **Paso 5: §8.2, endpoints**

En `docs/security.md`, reemplazar:

```markdown
| `PATCH /api/sgspe/deliveries/{id}` (`sgspe.commitment`) | Corrige la entrega y recalcula `executed_amount`; nunca la de un compromiso cumplido o cancelado | UPDATE `delivery` y `commitment` | `COMMITMENT_MANAGER`, `COMMUNITY_RELATIONS_OFFICER` | F3 |
```

por:

```markdown
| `PATCH /api/sgspe/deliveries/{id}` (`sgspe.commitment`) | Corrige la entrega y recalcula `executed_amount`; nunca la de un compromiso cumplido o cancelado. Desde F5 corrige también la de un aporte en `procured`, sin recalcular ningún ejecutado (ASM-111) | UPDATE `delivery` y, en la de un compromiso, `commitment` | `COMMITMENT_MANAGER`, `COMMUNITY_RELATIONS_OFFICER`; desde F5, también `SOCIAL_INVESTMENT_MANAGER` | F3 |
```

En `docs/security.md`, reemplazar:

```markdown
| `POST /api/sgspe/stakeholder-requests/{id}/accept` (`sgspe.attention`) | Aplica `accepted` y crea el compromiso en `draft` o, desde F5, el aporte | UPDATE `stakeholder_request` y rol de la transición; CREATE `commitment` o, desde F5, `social_contribution` | `SOCIAL_MANAGER`, `COMMITMENT_MANAGER` y, desde F5, `SOCIAL_INVESTMENT_MANAGER` | F4 |
```

por:

```markdown
| `POST /api/sgspe/stakeholder-requests/{id}/accept` (`sgspe.attention`) | Aplica `accepted` y crea el compromiso en `draft` o, desde F5, el aporte en `requested` | UPDATE `stakeholder_request` y rol de la transición; CREATE del objeto que crea, `commitment` o, desde F5, `social_contribution` | Como compromiso: `SOCIAL_MANAGER`, `COMMITMENT_MANAGER`. Como aporte, desde F5: `SOCIAL_MANAGER`, `SOCIAL_INVESTMENT_MANAGER`; `COMMITMENT_MANAGER`, sin CREATE sobre `social_contribution`, recibe `403` (ASM-117) | F4 |
```

En `docs/security.md`, reemplazar:

```markdown
| `POST /api/sgspe/social-contributions/{id}/deliveries` (`sgspe.investment`) | Solo captura datos: crea la entrega del aporte y vincula acta, foto y comprobante; llevar el aporte a `delivered` es después la transición humana de entrega de WF-P | CREATE `delivery`; UPDATE `social_contribution` y `evidence` | `SOCIAL_INVESTMENT_MANAGER`, `COMMUNITY_RELATIONS_OFFICER` | F5 |
```

por:

```markdown
| `POST /api/sgspe/social-contributions/{id}/deliveries` (`sgspe.investment`) | Solo captura datos, con el aporte en `procured`: crea su única entrega y vincula las evidencias ACTA DE RECEPCION, FOTO y COMPROBANTE; llevar el aporte a `delivered` es después la transición humana `register_delivery` (ASM-111) | CREATE `delivery`; UPDATE `social_contribution` y `evidence` | `SOCIAL_INVESTMENT_MANAGER`, `COMMUNITY_RELATIONS_OFFICER` | F5 |
```

- [ ] **Paso 6: §9.7, cadenas y padres alternativos**

En `docs/security.md`, reemplazar:

```markdown
| Las 31 raíces de `ProjectScope.ROOTS` | Su `project` es de mis proyectos; `evidence`, `early_warning_rule` y `export_log` sin `project`, siempre |
| Los 19 hijos de `ProjectScope.CHILDREN` | Su padre es de mis proyectos; `delivery` sin compromiso y `locality_profile` sin estudio, siempre |
```

por:

```markdown
| Las raíces de `ProjectScope.ROOTS` (31 hasta F4; 33 desde F5, con `social_contribution` y `social_program`) | Su `project` es de mis proyectos; `evidence`, `early_warning_rule` y `export_log` sin `project`, siempre |
| Los hijos de `ProjectScope.CHILDREN` (19 hasta F4; 27 desde F5) | El padre que tienen es de mis proyectos, subiendo la cadena si ese padre es a su vez un hijo (`program_measurement`, ASM-114); `locality_profile` sin estudio, siempre; `delivery` sin compromiso, siempre hasta F4, y desde F5, sin compromiso ni aporte, solo para los exentos |
```

En `docs/security.md`, reemplazar:

```markdown
- **Hijos con padre opcional:** un hijo sin su padre se lee en todo el tenant. `ArchitectureTest` fija la lista exacta
  (`delivery` y `locality_profile`) y exige que ninguno tenga otra relación hacia un objeto de proyecto. Cuando F5 dé a
  `delivery` su segundo padre (`social_contribution`), la entrega se acota por el padre que tenga, nunca «sin padre,
  todos».
```

por:

```markdown
- **Hijos con padre opcional:** un hijo con un único padre opcional, sin él, se lee en todo el tenant.
  `ArchitectureTest` fija la lista exacta (hasta F4, `delivery` y `locality_profile`; desde F5, solo
  `locality_profile`) y exige que ninguno tenga otra relación hacia un objeto de proyecto.
- **Cadenas y padres alternativos (desde F5, ASM-114):** `ProjectScope.CHILDREN` da, por hijo, sus padres posibles en
  orden, como pares (campo RELATION, objeto padre); `ProjectScope.chain(child, field)` da los saltos hasta la raíz y
  `ProjectScope.parentOf(record)`, el padre presente. Un padre puede ser a su vez un hijo si la cadena llega a una raíz
  en a lo sumo dos saltos y sin ciclos (`program_measurement` → `program_indicator` → `social_program`): la condición
  anida una subconsulta por salto, con un alias por salto (`t1`, `t2`), valores enlazados y los nombres de tabla y
  columna de la definición. Un hijo con varios padres (`delivery`: `commitment` o `social_contribution`, exactamente uno
  por `ExactlyOneGuard`) se lee por la disyunción de un término por padre posible, cada uno entre paréntesis; un `NULL`
  en el campo de un término no lo cumple, así que una entrega huérfana (los dos vacíos, por el `ON DELETE SET NULL` de
  wasichai) solo la leen `ADMIN` y quien tiene `READ_ALL_PROJECTS`. Una definición de la cadena sin la columna de su
  salto o sin `project` no admite ningún registro (falla cerrado). `ReadColumns.parent` pasa a `parents`, una lista de
  `ParentPath` con la columna del hijo y los saltos (tabla y columna de cada uno) hasta la columna `project` de la raíz.
  `ProjectResolver` (escritura) toma el padre presente antes y después y sube la misma cadena leyendo como quien
  escribe: un registro existente con varios padres posibles y ninguno es `unresolved`, y un alta sin ninguno la rechaza
  antes `ExactlyOneGuard` (`400 EXACTLY_ONE`). `ProjectLinks.linkedProject` (vínculos) sube la cadena igual; un salto
  que no se lee es `NotReadable`.
- **`ArchitectureTest` desde F5:** (1) todo objeto de `model.json` está en un solo grupo y `ProjectScope` no nombra
  objetos ausentes; (2) los objetos con relación `project` son exactamente `ROOTS` más `project_assignment`; (3) cada
  padre posible `(f, P)` de un hijo es una relación `(hijo, f, P)` del modelo, con `P` en `ROOTS` o en `CHILDREN`; (4)
  desde cada hijo, toda cadena llega a una raíz en a lo sumo dos saltos y ningún objeto es su propio ancestro; (5) un
  hijo que es padre de otro (`program_indicator`) tiene un solo padre posible, con relación obligatoria; (6) un hijo con
  varios padres tiene todas esas relaciones opcionales, que forman exactamente un grupo de `ExactlyOneGuard` de ese
  objeto, y no tiene otra relación hacia un objeto de proyecto (hoy, solo `delivery`); (7) los hijos con un único padre
  opcional son exactamente `locality_profile`, sin otra relación hacia un objeto de proyecto.
```

- [ ] **Paso 7: La nota de F5 en ADR-0029 y en la especificación 4.4**

En `docs/adr/0029-alcance-de-lectura-por-proyecto.md`, reemplazar:

```markdown
  (`ProjectScope.TENANT`), sin restricción. Valores enlazados, nombres de la definición.
```

por:

```markdown
  (`ProjectScope.TENANT`), sin restricción. Valores enlazados, nombres de la definición.
- **Desde F5 (ASM-114, nota del 2026-10-11).** Un hijo puede tener por padre a otro hijo (a lo sumo dos saltos, sin
  ciclos: `program_measurement`) o varios padres posibles (`delivery`: `commitment` o `social_contribution`), y se lee
  por el padre que tiene, nunca «sin padre, todos»; quedan 33 raíces y 27 hijos, y el único hijo con padre opcional es
  `locality_profile` (`security.md` §9.7). La decisión de este ADR no cambia: es su extensión a BC-10.
```

En `docs/superpowers/specs/2026-10-10-alcance-por-proyecto-design.md`, reemplazar:

```markdown
Si la raíz o el padre es opcional sale del catálogo (`ModelCatalog.isRequiredRelation`), como en `ProjectResolver`,
```

por:

```markdown
> **Nota de F5 (2026-10-11, ASM-114).** Desde F5 un hijo puede tener por padre a otro hijo (a lo sumo dos saltos) y
> varios padres posibles: la condición de un hijo es la disyunción, por padre posible, de la forma «Hijo» con una
> subconsulta anidada por salto, y «Hijo de padre opcional» queda solo para `locality_profile`. La referencia es la
> especificación de F5 (§3.2) y `security.md` §9.7.

Si la raíz o el padre es opcional sale del catálogo (`ModelCatalog.isRequiredRelation`), como en `ProjectResolver`,
```

En `docs/superpowers/specs/2026-10-10-alcance-por-proyecto-design.md`, reemplazar:

```markdown
  `project_assignment`); el padre de cada hijo es una raíz.
```

por:

```markdown
  `project_assignment`); el padre de cada hijo es una raíz (desde F5, una raíz o un hijo cuya cadena llega a una raíz
  en a lo sumo dos saltos, ASM-114).
```

- [ ] **Paso 8: Verificar y commit**

Repetir la comprobación del Paso 1 (esperado: `ok`) y las compuertas de «Global Constraints»: `test_apply_roles.py`
sigue en verde porque las celdas que cambian son de objetos de F5 y la de `topic` no cambia. Después:

```bash
git add docs/security.md docs/adr/0029-alcance-de-lectura-por-proyecto.md \
  docs/superpowers/specs/2026-10-10-alcance-por-proyecto-design.md
git commit -F - <<'MSG'
docs: permisos de BC-10 y alcance por proyecto con cadenas y dos padres

SMG y CRO cargan ítems y CRO comprobantes; COMMITMENT_MANAGER acepta solo como compromiso; la
autoridad del tope difiere por objeto (ASM-119). §9.7 suma las cadenas, los padres alternativos y
las reglas de ArchitectureTest; ADR-0029 y la especificación 4.4 ganan su nota de F5.

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_017MAgYcNauGp6cC3ekX1h7U
MSG
```

---

### Tarea 6: Contratos de la API de F5

`api.md` suma los códigos de F5 a §3.3 (C9, C10 y los de P1 a P9 y G1 a G4) y a la consulta en seco de §4.1, el
contrato de la aceptación como aporte (§4.6), el de la entrega de aportes (§4.8, con la corrección de §4.5 y los
vínculos de §4.9) y los filtros del tablero `investment` (§4.11).

**Archivos:**
- Modificar: `docs/api.md` (§3.3, §4.1, §4.5, §4.6, §4.8, §4.9, §4.11)
- Probar: `tools/check_traceability.py` (no lee `api.md`; la compuerta es la comprobación del Paso 1)

**Interfaces:**
- Consume: ASM-110 a ASM-119 (Tarea 1); los números P1 a P9 y G1 a G4 (Tarea 3, que este documento cita por número y
  repite con el mismo código y campo).
- Produce: el contrato de `POST /api/sgspe/social-contributions/{id}/deliveries`
  (`{ attributes, geometries?, evidence? }` → `201 { delivery, contribution }`, `IK`, `IM` del aporte, `CR`) y de la
  aceptación con `creates` igual a `social_contribution` (`201 { request, contribution }` con `Location`); las filas de
  `transition-checks` de `ITEMS_REQUIRED`, `PAID_LABOR`, `MONETARY_DONATION`, `PRIORITY_NOT_MET`,
  `PRIORITIES_NOT_CONFIGURED` y `SYNERGY_AGREEMENT_REQUIRED`; los filtros `program` y `year` de los KPI-INV.

- [ ] **Paso 1: La comprobación falla**

```bash
python3 - <<'PY'
text = open('docs/api.md', encoding='utf-8').read()
codes = ['ITEMS_REQUIRED', 'ITEMS_FROZEN', 'CONTRIBUTION_FROZEN', 'DELIVERY_EXISTS', 'FIELD_NOT_ALLOWED',
         'PROGRAM_MISMATCH', 'PAID_LABOR', 'MONETARY_DONATION', 'PRIORITY_NOT_MET', 'PRIORITIES_NOT_CONFIGURED',
         'SYNERGY_AGREEMENT_REQUIRED']
checks = [(f'`{c}`', True) for c in codes] + [
    ('`stakeholder_request.created_contribution`', True),
    ('**Entrega, contrato (ASM-111).**', True),
    ('**Aceptación como aporte (ASM-117).**', True),
    ('`program` y `year`', True),
    ('el aporte llega en F5) y', False),
]
bad = [f"{'falta' if want else 'sobra'} {s!r}" for s, want in checks if (s in text) != want]
print('\n'.join(bad) or 'ok')
raise SystemExit(1 if bad else 0)
PY
```

Esperado: salida 1 con las dieciséis líneas.

- [ ] **Paso 2: §3.3, los códigos de F5 en la API genérica**

En `docs/api.md`, reemplazar:

```markdown
vacía vale `risk_review_days` del proyecto) |
```

por:

```markdown
vacía vale `risk_review_days` del proyecto). Desde F5: `INVALID_NUMBER` y `OUT_OF_RANGE` también en `contribution_item.line_amount` y `unit_cost`, `receipt.amount` y `program_budget.allocated_amount` y `executed_amount` (ASM-118); `OUT_OF_RANGE` en `beneficiaries_count`, `target_count` y `reached_count` (enteros no negativos), en `budget_year` (de 2000 a 2100) y en la fecha final de una medición, un programa o una iniciativa anterior a la inicial; `FIELD_REQUIRED` (`contribution_type`: aprobar un aporte sin tipo, P9); y `FIELD_NOT_ALLOWED` (`delivered_amount` en la entrega de un aporte, ASM-111) |
```

En `docs/api.md`, reemplazar:

```markdown
`GeneratorKeyGuard`, ronda branchfix de 4.2b) |
```

por:

```markdown
`GeneratorKeyGuard`, ronda branchfix de 4.2b). Desde F5: `COMMAND_ONLY` también en `stakeholder_request.created_contribution`, que solo escribe la aceptación (ASM-117); `ACTION_REQUIRED` y `SEGREGATION_OF_DUTIES` (`record`) al aprobar un aporte (P4, P5) o un programa (G1, G2) sin `APPROVE` o habiéndolo registrado (en el aporte, también habiendo aceptado la solicitud que lo creó); `FIELD_NOT_READABLE` (`delivery.conformity_by`) también en `register_delivery` de WF-P (P8); `OBJECT_NOT_READABLE` también sobre `social_contribution`, `contribution_item`, `agreement`, `agreement_party`, `topic` y `project_priority_topic`, que leen las reglas de WF-P y WF-G |
```

En `docs/api.md`, reemplazar:

```markdown
no baja a una clasificación no restringida por `PATCH /api/sgspe/evidence/{id}`) |
```

por:

```markdown
no baja a una clasificación no restringida por `PATCH /api/sgspe/evidence/{id}`). Desde F5 (`InvestmentWriteGuard`, `workflows.md` WF-P y WF-G): `ITEMS_REQUIRED` (`contribution_item`: aprobar un aporte sin ítems, P9); `ITEMS_FROZEN` (`social_contribution`: crear, cambiar o borrar un ítem de un aporte fuera de `requested`, o cambiar el aporte de un ítem); `CONTRIBUTION_FROZEN` (el campo: un término aprobado del aporte que cambia fuera de `requested`); `PAID_LABOR` y `MONETARY_DONATION` (`contribution_item`) y `PRIORITY_NOT_MET` (`topic`), lineamientos incumplidos sin `guideline_deviation_justification` (con ella son advertencias, y `PRIORITIES_NOT_CONFIGURED` lo es siempre, §4.1); `SYNERGY_AGREEMENT_REQUIRED` (`agreement`: P6 del aporte y G3 del programa); `DELIVERY_EXISTS` (`social_contribution`: una segunda entrega del aporte); `DELIVERY_FROZEN` también sobre `social_contribution` (la entrega de un aporte fuera de `procured` no cambia ni se vincula); `PROGRAM_MISMATCH` (`social_initiative`: un grupo de beneficiarios con una iniciativa de otro programa); `SUPPORT_CAP_EXCEEDED`, `CAP_DATA_INVALID`, `INTERNAL_APPROVAL_REQUIRED`, `DELIVERY_REQUIRED` y `EVIDENCE_REQUIRED` también en WF-P (P1, P7 y P8) e `INTERNAL_APPROVAL_REQUIRED` en `plan_direct` de WF-G (G4); `STATE_MISMATCH` también sobre `created_contribution` (`close_unfulfilled` con el aporte fuera de `rejected` y `cancelled`); un indicador de programa con mediciones no da `HAS_DEPENDENTS`: como `program_measurement` es `appendOnly`, wasichai rechaza antes su baja con un `409` sin código (`AppendOnlyReferences`); y `PROJECT_MISMATCH` en las relaciones de BC-10 hacia otro proyecto (`exploration_campaign`, `stakeholder`, `commitment` y `agreement` del aporte, `agreement` del programa) y en los orígenes de una tarea (`ProjectLinkGuard`) |
```

En `docs/api.md`, reemplazar:

```markdown
`STATE_MISMATCH`, `ASSESSMENT_OUT_OF_ORDER`, `FIRST_ASSESSMENT_NOT_RESIDUAL` y `RISK_THRESHOLDS_INVALID`; §4.4, §4.6 y
§4.7 dicen cuándo.
```

por:

```markdown
`STATE_MISMATCH`, `ASSESSMENT_OUT_OF_ORDER`, `FIRST_ASSESSMENT_NOT_RESIDUAL` y `RISK_THRESHOLDS_INVALID`; §4.4, §4.6 y
§4.7 dicen cuándo. El endpoint de entrega de aportes de F5 usa `STATE_MISMATCH`, `DELIVERY_EXISTS` y
`FIELD_NOT_ALLOWED` (§4.8).
```

- [ ] **Paso 3: §4.1, las reglas de F5 en la consulta en seco**

En `docs/api.md`, reemplazar:

```markdown
| `ASSEMBLY_REQUIRED` | bloqueo (`field`: `assembly_engagement`) | 409 | Una autorización de asamblea sin la interacción de tipo ASAMBLEA |

Las reglas de los demás contextos suman sus códigos a esta tabla junto con su guarda.
```

por:

```markdown
| `ASSEMBLY_REQUIRED` | bloqueo (`field`: `assembly_engagement`) | 409 | Una autorización de asamblea sin la interacción de tipo ASAMBLEA |
| `ITEMS_REQUIRED` | bloqueo (`field`: `contribution_item`) | 409 | `approve` o `approve_direct` de un aporte sin ítems (P9) |
| `PAID_LABOR` | bloqueo sin `guideline_deviation_justification`, advertencia con ella (`field`: `contribution_item`) | 409 | Algún ítem es MANO DE OBRA y `policy_no_paid_labor` está activa (P2, RN-INV-001) |
| `MONETARY_DONATION` | Como `PAID_LABOR` (`field`: `contribution_item`) | 409 | Una DONACION con algún ítem DINERO y `policy_non_monetary_donations` activa (P2, RN-INV-002) |
| `PRIORITY_NOT_MET` | Como `PAID_LABOR` (`field`: `topic`) | 409 | Con `policy_priority_warning` activa y temas priorizados en el proyecto, el aporte no tiene tema o su tema no es priorizado ni desciende de uno (P3, RN-INV-005) |
| `PRIORITIES_NOT_CONFIGURED` | advertencia (`field`: `topic`) | — | Con `policy_priority_warning` activa, el proyecto no tiene temas priorizados y la prioridad no se evalúa; nunca exige justificación |
| `SYNERGY_AGREEMENT_REQUIRED` | bloqueo (`field`: `agreement`) | 409 | Un aporte con sinergia (P6) o un programa con socio (G3), con `policy_synergy_requires_agreement` activa, sin un acuerdo en `active` y no VENCIDO (en el programa, además, con el socio como parte); la justificación no lo cubre |

Las reglas de los demás contextos suman sus códigos a esta tabla junto con su guarda. Desde F5, las de un aporte (P1 a
P9) y las de un programa (G1 a G4) usan también, con el mismo tipo y el estado de su escritura real,
`ACTION_REQUIRED` y `SEGREGATION_OF_DUTIES` (`record`), `INTERNAL_APPROVAL_REQUIRED` (`project`),
`SUPPORT_CAP_EXCEEDED`, `CAP_DATA_INVALID` y `CAP_OTHER_CURRENCY` (P1), `FIELD_REQUIRED` (`contribution_type`, P9,
`400`), `DELIVERY_REQUIRED`, `EVIDENCE_REQUIRED` y `FIELD_NOT_READABLE` (P8, con las evidencias ACTA DE RECEPCION, FOTO
y COMPROBANTE) y `OBJECT_NOT_READABLE`. **Las reglas de un aporte** corren en el orden de `workflows.md` (WF-P): P7,
P9, P4 y P5, P6, P2 y P3 y al final P1; la consulta las junta todas sin tomar los candados del aporte y de la campaña,
así que el portal muestra cada lineamiento incumplido y pide la justificación antes de aprobar.
```

- [ ] **Paso 4: §4.5, la corrección de la entrega de un aporte**

En `docs/api.md`, reemplazar:

```markdown
y la de un compromiso cancelado ya no cambia | UPDATE sobre `delivery` y sobre su compromiso |
```

por:

```markdown
y la de un compromiso cancelado ya no cambia. Desde F5 corrige también la entrega de un aporte, sin recalcular ningún ejecutado, mientras el aporte siga en `procured` (si no, `409 DELIVERY_FROZEN` sobre `social_contribution`) y sin `delivered_amount` (`400 FIELD_NOT_ALLOWED`); su `social_contribution` no cambia (ASM-111) | UPDATE sobre `delivery` y, en la de un compromiso, sobre su compromiso |
```

- [ ] **Paso 5: §4.6, la aceptación como aporte**

En `docs/api.md`, reemplazar:

```markdown
| UPDATE sobre `stakeholder_request`, CREATE sobre `commitment` o `social_contribution` y el rol de la transición de WF-E3 a `accepted` |
```

por:

```markdown
| UPDATE sobre `stakeholder_request`, CREATE sobre el objeto que crea (`commitment` o `social_contribution`) y el rol de la transición de WF-E3 a `accepted`: `COMMITMENT_MANAGER`, sin CREATE sobre `social_contribution`, acepta solo como compromiso (ASM-117) |
```

En `docs/api.md`, reemplazar:

```markdown
→ `201 { request, commitment }` o `201 { request, contribution }` |
```

por:

```markdown
→ `201 { request, commitment }` o `201 { request, contribution }`, con `Location` del registro creado |
```

En `docs/api.md`, reemplazar:

```markdown
- **Aceptación, errores.** `400 INVALID_TARGET` (`creates`: en F4 solo `commitment`; el aporte llega en F5) y
  `409 STATE_MISMATCH` (`record`) fuera de `under_evaluation`.
```

por:

```markdown
- **Aceptación, errores.** `400 INVALID_TARGET` (`creates` distinto de `commitment` y `social_contribution`; en un
  tenant que todavía no tiene el modelo de F5, todo lo distinto de `commitment`), `403` sin CREATE sobre el objeto que
  se crea y `409 STATE_MISMATCH` (`record`) fuera de `under_evaluation`.
- **Aceptación como aporte (ASM-117).** Valida `attributes` contra `social_contribution`, sin `project`; en una
  transacción escribe la respuesta, crea el aporte en `requested` con el proyecto de la solicitud, guarda
  `created_contribution` y aplica `accept`. El aporte creado lo «registró» quien aceptó, así que esa persona no lo
  aprueba (P5); sus ítems se cargan después, todavía en `requested`, y el aviso de aprobación sale al crearlo.
  `created_contribution` es un campo de comando: una persona que lo fija, cambia o vacía por la API genérica, también
  con un `PUT` que lo omite, recibe `403 COMMAND_ONLY`. Cerrar el aporte (`close` de WF-P) cierra la solicitud en la
  misma transacción (`RequestClosureListener`, dentro de `ConfidentialRelease`, con el motivo «RN-GRV-001: aporte
  <código> cerrado» y el origen `listener:request-closure`); con el aporte en `rejected` o `cancelled`, la solicitud se
  cierra a mano con `close_unfulfilled`.
```

- [ ] **Paso 6: §4.8, la entrega de aportes**

En `docs/api.md`, reemplazar:

```markdown
Paquete `sgspe.investment`. El tope de apoyo y los lineamientos los aplican las guardas al aprobar (RN-INV-003, desde F3).

| Método | Ruta | Propósito | Permiso o acción declarada | Cuerpo y respuesta | Encabezados | Fase |
|---|---|---|---|---|---|---|
| `POST` | `/api/sgspe/social-contributions/{id}/deliveries` | Solo captura datos: crea la entrega del aporte y sus vínculos de evidencia (acta de recepción, fotos y comprobante) en una transacción; no cambia el estado del aporte | CREATE sobre `delivery`, UPDATE sobre `social_contribution` y sobre las `evidence` vinculadas | `{ attributes, geometries, evidence[] }` → `201 { delivery, contribution }` | `IK`, `CR` | F5 |

Llevar el aporte a `delivered` es la transición humana y explícita `register_delivery` de WF-P, por la API genérica y con
motivo; `InvestmentWriteGuard` la rechaza si no existe una entrega con conformidad y su evidencia: acta de recepción, foto y
comprobante (RF-INV-001) [domain-model §7.2]. Aprobar un aporte por encima del tope de la campaña exige `APPROVE_OVER_CAP`
(RN-INV-003). Las correcciones de una entrega usan `PATCH /api/sgspe/deliveries/{id}` (§4.5).
```

por:

```markdown
Paquete `sgspe.investment`. El tope de apoyo y los lineamientos los aplican las guardas al aprobar (RN-INV-003): en
compromisos desde F3 (`SupportPolicyGuard`) y en aportes desde F5 (`InvestmentWriteGuard` con `ContributionCapRule` y
`ContributionGuidelines`, `workflows.md` WF-P).

| Método | Ruta | Propósito | Permiso o acción declarada | Cuerpo y respuesta | Encabezados | Fase |
|---|---|---|---|---|---|---|
| `POST` | `/api/sgspe/social-contributions/{id}/deliveries` | Solo captura datos: crea la única entrega del aporte y sus vínculos de evidencia (ACTA DE RECEPCION, FOTO y COMPROBANTE) en una transacción, con el candado del aporte; no cambia su estado | CREATE sobre `delivery`, UPDATE sobre `social_contribution` y sobre las `evidence` vinculadas | `{ attributes, geometries?, evidence?: [<id de evidence>] }` (sin `delivered_amount`, `commitment` ni `social_contribution`: el comando fija el aporte) → `201 { delivery, contribution }`, con `Location` y `ETag` de la entrega | `IK`, `IM` (del aporte), `CR` | F5 |

- **Entrega, contrato (ASM-111).** En una transacción y con el candado del aporte: (1) el aporte está en `procured`; si
  no, `409 STATE_MISMATCH` sobre `record`; (2) no tiene entrega; si no, `409 DELIVERY_EXISTS` sobre
  `social_contribution`; (3) `attributes` no trae `delivered_amount` (`400 FIELD_NOT_ALLOWED` sobre
  `attributes.delivered_amount`; por la API genérica, la guarda lo da sobre `delivered_amount`), porque el ejecutado de
  un aporte es su `total_amount` (ASM-063), ni `commitment` ni `social_contribution`; (4) crea la `delivery` con
  `social_contribution` y vincula cada evidencia, y `EvidenceWriteGuard` exige que sea del proyecto del aporte; (5) no
  cambia el estado.
- Llevar el aporte a `delivered` es la transición humana `register_delivery` de WF-P, por la API genérica y con motivo:
  `InvestmentWriteGuard` la rechaza sin la entrega con conformidad y sus evidencias ACTA DE RECEPCION, FOTO y
  COMPROBANTE (P8) [domain-model §7.2]. El «Acta de recepción» emitida (AUT-04) espera a UPS-23: el acta firmada es una
  evidencia ACTA DE RECEPCION (ASM-116).
- La entrega se corrige con `PATCH /api/sgspe/deliveries/{id}` (§4.5) mientras el aporte siga en `procured`; desde
  `delivered`, y en `cancelled`, ni ella ni sus vínculos de evidencia cambian (`409 DELIVERY_FROZEN` sobre
  `social_contribution`).
- Aprobar un aporte por encima de un tope que bloquea exige `APPROVE_OVER_CAP`, que tiene `SOCIAL_MANAGER` (RN-INV-003,
  ASM-119). Las reglas de la aprobación (P1 a P9), sus códigos y su orden están en `workflows.md` (WF-P) y en §4.1; las
  del programa (G1 a G4), en WF-G.
- Los ítems del aporte, sus términos, los programas y sus hijos se escriben por la API genérica, con
  `InvestmentWriteGuard`: `ITEMS_FROZEN`, `CONTRIBUTION_FROZEN`, `PROGRAM_MISMATCH` y los rangos de ASM-118 (§3.3).
- **Mismo proyecto.** Las relaciones del aporte hacia `exploration_campaign`, `stakeholder`, `commitment` y `agreement`,
  la del programa hacia `agreement` y los orígenes de una tarea son del mismo proyecto: si no, `409 PROJECT_MISMATCH`
  sobre el campo (`ProjectLinkGuard`). Los temas priorizados son el vínculo `project_priority_topic` del proyecto con
  `topic`, un maestro del tenant, sin esa regla (`/api/objects/project/records/{id}/related/project_priority_topic`).
- **Idempotencia.** El endpoint de entrega honra `Idempotency-Key` (§3.5), como la aceptación.
```

- [ ] **Paso 7: §4.9 y §4.11**

En `docs/api.md`, reemplazar:

```markdown
una entrega cumplida o cancelada, `409 DELIVERY_FROZEN`; desde F4 acepta
```

por:

```markdown
una entrega cumplida o cancelada, o desde F5 la de un aporte fuera de `procured`, `409 DELIVERY_FROZEN`; desde F5 acepta también `social_contribution` y `social_program`, con el proyecto del aporte para una entrega de aporte; desde F4 acepta
```

En `docs/api.md`, reemplazar:

```markdown
Desvincula; una entrega de un compromiso cumplido o cancelado no pierde evidencias (`409 DELIVERY_FROZEN`)
```

por:

```markdown
Desvincula; una entrega de un compromiso cumplido o cancelado, o desde F5 la de un aporte fuera de `procured`, no pierde evidencias (`409 DELIVERY_FROZEN`)
```

En `docs/api.md`, reemplazar:

```markdown
  KPI-ENG-08 solo se piden por `GET /api/sgspe/dashboards/indicators/{code}` hasta F8.
```

por:

```markdown
  KPI-ENG-08 solo se piden por `GET /api/sgspe/dashboards/indicators/{code}` hasta F8.
- El tablero `investment` (F5, `dashboards.md` §4.7 y §5.10) trae KPI-INV-01 a KPI-INV-04 (`InvestmentKpis`) y lee como
  quien llama, así que hereda el alcance; por defecto, «mis proyectos» de `ProjectReach`. Además de `project`,
  `periodStart`, `periodEnd`, `locality` y `explorationCampaign` (la campaña), el tablero y sus indicadores aceptan
  `program` y `year` (`dashboards.md` §3.1; en otro tablero o indicador son `422`); un filtro desconocido es `422`. La
  cifra titular de KPI-INV-01 a 03 va en PEN (`dashboards.md` §3.3) y cada desglose lleva su moneda. KPI-INV-01
  informa por campaña el tope, la moneda, lo comprometido (`CampaignCommitted`), lo ejecutado
  (`executed_social_amount`) y los conteos en otra moneda; KPI-INV-02, por programa, año y moneda, lo asignado y lo ejecutado
  registrado; KPI-INV-03, por proyecto y moneda, lo ejecutado por fuente frente a presupuesto y topes; KPI-INV-04, tres
  bloques que no se suman. Es interno y no aplica celdas pequeñas.
```

- [ ] **Paso 8: Verificar y commit**

Repetir la comprobación del Paso 1 (esperado: `ok`) y las compuertas de «Global Constraints». Después:

```bash
git add docs/api.md
git commit -F - <<'MSG'
docs: contratos de la API de F5 (aportes, entrega y aceptación)

§3.3 y §4.1 suman los códigos de P1 a P9 y G1 a G4; §4.6, la aceptación como aporte con su campo de
comando; §4.8, el contrato de la entrega única del aporte; §4.11, los filtros de los KPI-INV.

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_017MAgYcNauGp6cC3ekX1h7U
MSG
```

---

### Tarea 7: Tableros y pantallas

`dashboards.md` suma el conjunto «comprometido» de los aportes (C15), los filtros `program` y `year`, KPI-INV-01 con lo
comprometido y KPI-INV-04 en tres bloques; `ux/screens.md` corrige S42 a S44 y S57 con §3 y §4.2 de la especificación
(C4, C5), suma los temas priorizados a S11 y las aprobaciones de F5 a S48, y el tablero de inversión social con sus
filtros.

**Archivos:**
- Modificar: `docs/dashboards.md` (§3.1, §3.2, §4.7, §5.10, §11)
- Modificar: `docs/ux/screens.md` (S11, S42, S43, S44, S48, S57 y el tablero de inversión social)
- Probar: la comprobación del Paso 1 (ninguna prueba del modelo lee estos dos documentos)

**Interfaces:**
- Consume: ASM-110 a ASM-117 y ASM-119 (Tarea 1); P1 a P9 y G1 a G4 (Tarea 3).
- Produce: el conjunto «comprometido» de `social_contribution` (`approved`, `procured`, `delivered`, `closed`), que usa
  `CampaignCommitted` (5.2a) y KPI-INV-01 (5.2b); los filtros `program` y `year`; las fichas S11, S42, S43, S44, S48 y
  S57 que implementa el plan 5.3.

- [ ] **Paso 1: La comprobación falla**

```bash
python3 - <<'PY'
checks = [
    ('docs/dashboards.md', '| `social_contribution` | comprometido | `approved`, `procured`, `delivered`, `closed`', True),
    ('docs/dashboards.md', '| Programa | `program` |', True),
    ('docs/dashboards.md', '| Año | `year` |', True),
    ('docs/dashboards.md', 'Tres bloques que no se suman', True),
    ('docs/ux/screens.md', 'Subir acta de recepción firmada', True),
    ('docs/ux/screens.md', 'PolicyChecklist P1–P9', True),
    ('docs/ux/screens.md', 'PriorityTopicsList', True),
    ('docs/ux/screens.md', 'AUT-04 emite el acta de recepción', False),
    ('docs/ux/screens.md', 'SMG actualiza.', False),
]
bad = [f"{p}: {'falta' if want else 'sobra'} {s[:80]!r}" for p, s, want in checks
       if (s in open(p, encoding='utf-8').read()) != want]
print('\n'.join(bad) or 'ok')
raise SystemExit(1 if bad else 0)
PY
```

Esperado: salida 1 con las nueve líneas.

- [ ] **Paso 2: `dashboards.md` §3.1 y §3.2, filtros y estados que cuentan**

En `docs/dashboards.md`, reemplazar:

```markdown
desde F3 la aplican KPI-COM-01 a KPI-COM-05 y KPI-COM-10. Los demás
```

por:

```markdown
desde F3 la aplican KPI-COM-01 a KPI-COM-05 y KPI-COM-10, y desde F5 KPI-INV-01. Los demás
```

En `docs/dashboards.md`, reemplazar:

```markdown
| Responsable | `owner` |
```

por:

```markdown
| Programa | `program` | Desde F5, solo en el tablero `investment` y en los KPI-INV: un `social_program` (UUID; `400` si no lo es); lo aplican KPI-INV-02 y el bloque de programas de KPI-INV-04; los demás KPI-INV lo informan en `ignoredFilters`. En otro tablero o indicador es un parámetro desconocido (`422`) | RF-INV-003 |
| Año | `year` | Desde F5, solo en el tablero `investment` y en los KPI-INV: el año presupuestal (`program_budget.budget_year`), un entero de 2000 a 2100 (`400` si no); lo aplica KPI-INV-02; los demás KPI-INV lo informan en `ignoredFilters`. En otro tablero o indicador, `422` | RF-INV-004, ASM-113 |
| Responsable | `owner` |
```

En `docs/dashboards.md`, reemplazar:

```markdown
`approved`, `in_progress`, `blocked`, `fulfilled`, `verified`, `closed`: los montos aprobados de ASM-063 | KPI-COM-10, KPI-INV-03 |
```

por:

```markdown
`approved`, `in_progress`, `blocked`, `fulfilled`, `verified`, `closed`: los montos aprobados de ASM-063 | KPI-COM-10, KPI-INV-01, KPI-INV-03; P1 de WF-P y D7 de WF-D (`CampaignCommitted`) |
```

En `docs/dashboards.md`, reemplazar:

```markdown
| `social_contribution` | ejecutado | `delivered`, `closed` (ASM-063) | KPI-INV-03, KPI-INV-04 |
```

por:

```markdown
| `social_contribution` | comprometido | `approved`, `procured`, `delivered`, `closed`: los aportes aprobados y no anulados, cuyo `total_amount` cuenta para el tope de la campaña (ASM-110) | KPI-INV-01; P1 de WF-P y D7 de WF-D (`CampaignCommitted`) |
| `social_contribution` | ejecutado | `delivered`, `closed` (ASM-063) | KPI-INV-03, KPI-INV-04 |
```

- [ ] **Paso 3: `dashboards.md` §4.7, §5.10 y §11, los KPI-INV**

En `docs/dashboards.md`, reemplazar:

```markdown
| KPI-INV-01 | Ejecución de la campaña frente a su tope | Cuánto del tope de apoyo social de cada campaña ya se ejecutó | `executed_social_amount` ÷ `social_support_cap` por campaña, en la moneda de la campaña; `other_currency_items_count` se muestra como advertencia (ASM-063) | `exploration_campaign.social_support_cap`, `currency`, `executed_social_amount`, `other_currency_items_count` | Proyecto, campaña | RQ-DSH-030, RF-INV-004, RN-INV-003 | Vivo (derivado) | F5 (datos desde F3) |
```

por:

```markdown
| KPI-INV-01 | Ejecución de la campaña frente a su tope | Cuánto del tope de apoyo social de cada campaña ya se comprometió y cuánto se ejecutó | Por campaña, en su moneda: lo comprometido (compromisos y aportes comprometidos de §3.2, con `CampaignCommitted`, la misma lectura de P1 y D7) y lo ejecutado (`executed_social_amount`) frente a `social_support_cap`; son cifras distintas (ASM-063, ASM-110). `other_currency_items_count` y los montos comprometidos en otra moneda se muestran como advertencia (`otherCurrencyCount` y `committedOtherCurrencyCount`) | `exploration_campaign.social_support_cap`, `currency`, `executed_social_amount`, `other_currency_items_count`; `commitment.amount`, `currency`; `social_contribution.total_amount`, `currency` | Proyecto, campaña | RQ-DSH-030, RF-INV-004, RN-INV-003 | Vivo (derivado y lectura de lo comprometido) | F5 (datos desde F3) |
```

En `docs/dashboards.md`, reemplazar:

```markdown
Por programa, año y moneda: suma de `program_budget.executed_amount` ÷ suma de `allocated_amount` |
```

por:

```markdown
Por programa, año y moneda: suma de `program_budget.executed_amount` ÷ suma de `allocated_amount`; el ejecutado se registra a mano por año (ASM-113) y las monedas no se convierten |
```

En `docs/dashboards.md`, reemplazar:

```markdown
| Por programa: suma de `reached_count` frente a suma de `target_count` de sus `beneficiary_group`. Aparte, `beneficiaries_count` de los aportes ejecutados y de los compromisos cumplidos. Las fuentes no se suman entre sí, porque una misma persona puede estar en varias |
```

por:

```markdown
| Tres bloques que no se suman, porque una misma persona puede estar en varias fuentes: por programa, suma de `reached_count` frente a suma de `target_count` de sus `beneficiary_group`; los aportes ejecutados (§3.2), suma de `beneficiaries_count`; y los compromisos cumplidos, suma de `beneficiaries_count` |
```

En `docs/dashboards.md`, reemplazar:

```markdown
| Campañas | KPI-INV-01 | Un medidor por campaña frente a su tope, con la advertencia de otras monedas |
```

por:

```markdown
| Campañas | KPI-INV-01 | Un medidor por campaña frente a su tope, con la marca de lo comprometido y la de lo ejecutado y la advertencia de otras monedas |
```

En `docs/dashboards.md`, reemplazar:

```markdown
| Beneficiarios | KPI-INV-04 | Tabla por programa y fuente |
```

por:

```markdown
| Beneficiarios | KPI-INV-04 | Tres bloques sin sumar: por programa (meta y alcanzados), aportes ejecutados y compromisos cumplidos |

El tablero es interno y no aplica celdas pequeñas. Filtros: `project`, `explorationCampaign`, `program`, `year`,
`periodStart`, `periodEnd` y `locality` (§3.1); lee como quien llama y hereda el alcance (`InvestmentKpis`, §7). Prueba:
`InvestmentDashboardApiTest`.
```

En `docs/dashboards.md`, reemplazar:

```markdown
KPI-INV-01 usa los topes de campaña, que existen desde F3 (RF-INV-002) |
```

por:

```markdown
KPI-INV-01 usa los topes de campaña, que existen desde F3 (RF-INV-002), y lo comprometido de compromisos y aportes (ASM-110) |
```

- [ ] **Paso 4: S11, temas priorizados**

En `docs/ux/screens.md`, reemplazar:

```markdown
- **Roles:** lectura de todos. Asignaciones: PAD. Partes y campañas: PAD, PMG. Concesiones: PAD, GAN. Parámetros: PAD, PMG.
```

por:

```markdown
- **Roles:** lectura de todos. Asignaciones: PAD. Partes y campañas: PAD, PMG. Concesiones: PAD, GAN. Parámetros y,
  desde F5, temas priorizados para aportes: PAD, PMG.
```

En `docs/ux/screens.md`, reemplazar:

```markdown
  parámetros agrupados por módulo con la marca "por defecto" o "cambiado".
```

por:

```markdown
  parámetros agrupados por módulo con la marca "por defecto" o "cambiado"; desde F5, en "Parámetros y políticas", la
  lista "Temas priorizados para aportes" (`project_priority_topic`, ASM-112), que vincula y desvincula temas del
  catálogo y avisa que, sin temas, la prioridad de los aportes no se evalúa.
```

En `docs/ux/screens.md`, reemplazar:

```markdown
  (campos de parámetros).
```

por:

```markdown
  (campos de parámetros); desde F5, el vínculo `project_priority_topic`
  (`/api/objects/project/records/{id}/related/project_priority_topic`).
```

En `docs/ux/screens.md`, reemplazar:

```markdown
│   └── PoliciesTab (PolicyGroup × n)
```

por:

```markdown
│   └── PoliciesTab (PolicyGroup × n · PriorityTopicsList)
```

- [ ] **Paso 5: S42 y S43**

En `docs/ux/screens.md`, reemplazar:

```markdown
- **Roles:** SIM y SMG crean y llevan; CRO registra aportes; APR lee y aprueba; el resto lee.
```

por:

```markdown
- **Roles:** SIM crea y lleva los programas, y SMG conduce su evaluación, suspensión y cierre; SMG, CRO y SIM registran
  aportes; APR y SMG aprueban; el resto lee.
```

En `docs/ux/screens.md`, reemplazar:

```markdown
- **Disposición y componentes:** tres indicadores: tope de la campaña (aprobados frente al tope y ejecutado, con advertencia
  de otra moneda), presupuesto de programas ejecutado, beneficiarios; tabla de programas (socio, estado, presupuesto del año,
  ejecutado, beneficiarios, indicadores con medición); tabla de aportes de la campaña (tipo, quién recibe, estado, monto,
  origen).
- **Datos y API:** genérica sobre `social_program`, `program_budget`, `beneficiary_group` y `social_contribution`;
  KPI-INV-01, KPI-INV-02 y KPI-INV-04; `exploration_campaign.executed_social_amount` y `other_currency_items_count`.
- **Estados:** el tope se verifica al aprobar contra los montos aprobados de compromisos y aportes de la campaña; lo ejecutado
  es otra cifra (ASM-063). Los programas no pertenecen a una campaña y no cuentan para el tope.
```

por:

```markdown
- **Disposición y componentes:** tres indicadores: tope de la campaña (comprometido frente al tope y ejecutado, con
  advertencia de otra moneda), presupuesto de programas ejecutado, beneficiarios; tabla de programas (socio, estado,
  presupuesto del año, ejecutado, beneficiarios, indicadores con medición); tabla de aportes de la campaña (tipo, tema,
  quién recibe, estado, total de los ítems, origen).
- **Datos y API:** genérica sobre `social_program`, `program_budget`, `beneficiary_group` y `social_contribution`;
  KPI-INV-01, KPI-INV-02 y KPI-INV-04; `exploration_campaign.executed_social_amount` y `other_currency_items_count`.
- **Estados:** el tope se verifica al aprobar contra lo comprometido de compromisos y aportes de la campaña (ASM-110);
  lo ejecutado es otra cifra (ASM-063). Los programas no pertenecen a una campaña y no cuentan para el tope.
```

En `docs/ux/screens.md`, reemplazar:

```markdown
- **Roles:** SIM crea, envía y registra mediciones; APR aprueba (SoD); SMG actualiza.
```

por:

```markdown
- **Roles:** SIM crea y envía el programa y es el único dueño de sus datos (presupuesto y ejecutado, indicadores,
  mediciones, iniciativas, hitos y beneficiarios); APR aprueba (G1 y G2); SMG conduce la evaluación, la suspensión y el
  cierre, con RU sobre el programa y solo lectura de sus hijos (`security.md` §7.7).
```

En `docs/ux/screens.md`, reemplazar:

```markdown
- **Disposición y componentes:** encabezado con estado, socio, convenio de sinergia y aprobación; camino de estados;
  presupuesto por año y moneda; indicadores con línea base, meta y última medición con su evidencia; iniciativas e hitos; a la
  derecha grupos beneficiarios (meta y alcanzados), territorio y trazabilidad.
```

por:

```markdown
- **Disposición y componentes:** encabezado con estado, socio, convenio de sinergia y aprobación; camino de estados;
  presupuesto por año y moneda, con el ejecutado que SIM registra a mano (ASM-113) y los de otra moneda aparte;
  indicadores con línea base, meta y última medición con su evidencia; iniciativas e hitos; a la derecha grupos
  beneficiarios (meta y alcanzados), territorio y trazabilidad.
```

En `docs/ux/screens.md`, reemplazar:

```markdown
- **Estados:** un indicador sin mediciones se marca; el total del programa se deriva de sus presupuestos, por moneda.
- **Permisos:** las mediciones no se editan ni se borran.
```

por:

```markdown
- **Estados:** un indicador sin mediciones se marca; el total del programa (`total_budget`) suma solo los presupuestos
  en su moneda (ASM-113); con socio y la política activa, "Aprobar" y "Planificar" piden un convenio de sinergia vigente
  con el socio como parte (G3, ASM-115).
- **Permisos:** las mediciones no se editan ni se borran; un indicador con mediciones no se borra; SMG ve los datos del
  programa en solo lectura.
```

- [ ] **Paso 6: S44, el aporte**

En `docs/ux/screens.md`, reemplazar:

````markdown
### S44 · Aporte social

- **Ruta y lienzo:** `/projects/:projectId/contributions/:contributionId` · hoja `Inv-Aporte` · F5.
- **Propósito:** aprobar, adquirir y entregar un aporte puntual con las políticas a la vista.
- **Roles:** SMG, CRO y SIM registran; APR y SMG aprueban (`approve`); SIM aprueba sin revisión interna si la política lo
  permite (`approve_direct`); SIM registra la adquisición; SIM y CRO registran la entrega.
- **Acciones:** en `requested`, "Aprobar…" y "Rechazar…"; luego "Registrar adquisición", "Registrar entrega", "Cerrar",
  "Anular…"; todas con motivo.
- **Disposición y componentes:** encabezado con estado, tipo, campaña y quién registró; camino de estados; "Para qué y para
  quién" (propósito, ocasión o festividad, quién recibe, localidad, condiciones, beneficiarios); ítems con total derivado;
  entrega con sus cuatro requisitos; a la derecha "Antes de aprobar" con P1 a P6 y trazabilidad.
- **Datos y API:** genérica sobre `social_contribution`, `contribution_item` y `receipt`; `POST
  /api/sgspe/social-contributions/{id}/deliveries` captura la entrega (AUT-04 emite el acta de recepción) y luego
  `register_delivery` (P8).
- **Estados:** si supera el tope que bloquea, "Aprobar" solo aparece para SMG (`APPROVE_OVER_CAP`); quien registró no aprueba
  (P5); con sinergia, falta de acuerdo vigente bloquea (P6); apartarse de los lineamientos exige justificación (P3).
- **Permisos:** las fotos de la entrega con personas son DATOS PERSONALES.
- **Responsive:** "Antes de aprobar" sube antes de los ítems en teléfono.
- **Relacionadas y navegación:** solicitud de origen (S57), bandeja de aprobaciones (S48); al cerrar, la solicitud se
  cierra sola (ASM-068).

```text
ContributionPage
├── RecordHeader · StatePath
├── PurposeSection · ItemTable · DeliveryRequirements
└── SidePanel (PolicyChecklist P1–P6 · TraceList)
```
````

por:

````markdown
### S44 · Aporte social

- **Ruta y lienzo:** `/projects/:projectId/contributions/:contributionId` · hoja `Inv-Aporte` · F5.
- **Propósito:** registrar, aprobar, adquirir y entregar un aporte puntual con los lineamientos y el tope a la vista.
- **Roles:** SMG, CRO y SIM registran el aporte y cargan sus ítems; APR y SMG aprueban (`approve`); SIM aprueba sin
  revisión interna si la política lo permite (`approve_direct`); SIM registra la adquisición; SIM y CRO registran la
  entrega.
- **Acciones:** en `requested`, "Agregar ítem", "Aprobar…" y "Rechazar…"; luego "Registrar adquisición", "Registrar
  entrega" (una vez), "Subir acta de recepción firmada", "Cerrar" y "Anular…"; todas las transiciones con motivo.
- **Disposición y componentes:** encabezado con estado, tipo, tema, campaña y quién registró; camino de estados; "Para
  qué y para quién" (propósito, ocasión o festividad, quién recibe, localidad, condiciones, beneficiarios); ítems con su
  tipo (BIEN, SERVICIO, DINERO o MANO DE OBRA) y el total que se aprobará, editables solo en `requested`; la entrega con
  sus requisitos (conformidad, quiénes la dieron, acta de recepción, foto y comprobante); a la derecha "Antes de
  aprobar" y trazabilidad.
- **"Antes de aprobar":** `POST /api/sgspe/transition-checks` lista P1 a P9 con su estado: el tope de la campaña (P1,
  con lo comprometido y la advertencia de otra moneda), cada lineamiento incumplido (`PAID_LABOR`, `MONETARY_DONATION`,
  `PRIORITY_NOT_MET`) o la advertencia `PRIORITIES_NOT_CONFIGURED`, la sinergia (P6, `SYNERGY_AGREEMENT_REQUIRED`) y
  los ítems y el tipo de aporte (P9, `ITEMS_REQUIRED`). Si algún lineamiento se incumple, pide
  `guideline_deviation_justification`, que los cubre a todos; la sinergia no se justifica.
- **Datos y API:** genérica sobre `social_contribution`, `contribution_item` y `receipt`;
  `POST /api/sgspe/social-contributions/{id}/deliveries` captura la única entrega en `procured`, con sus evidencias, y
  luego `register_delivery` (P8). El "Acta de recepción" no se emite mientras UPS-23 siga abierta (ASM-116): "Subir acta
  de recepción firmada" la sube como evidencia ACTA DE RECEPCION vinculada a la entrega, con clasificación DATOS
  PERSONALES por defecto.
- **Estados:** si supera un tope que bloquea, "Aprobar" solo aparece para SMG (`APPROVE_OVER_CAP`, ASM-119); quien
  registró el aporte o aceptó la solicitud que lo creó no lo aprueba (P5); fuera de `requested`, los ítems y los
  términos aprobados son de solo lectura (`ITEMS_FROZEN`, `CONTRIBUTION_FROZEN`, ASM-110); "Registrar entrega" se
  ofrece una vez, solo en `procured`, y después muestra la entrega, que no cambia desde `delivered` (ASM-111).
- **Permisos:** las fotos de la entrega con personas y el acta firmada son DATOS PERSONALES; `recipients` y
  `conformity_by` son restringidos (`security.md` §7.9).
- **Responsive:** "Antes de aprobar" sube antes de los ítems en teléfono.
- **Relacionadas y navegación:** solicitud de origen (S57), bandeja de aprobaciones (S48); al cerrar, la solicitud se
  cierra sola (ASM-068, ASM-117).

```text
ContributionPage
├── RecordHeader · StatePath
├── PurposeSection · ItemTable (ItemKindSelect · TotalToApprove)
├── DeliverySection (DeliveryRequirements · SignedReceiptUpload)
└── SidePanel (PolicyChecklist P1–P9 · JustificationField · TraceList)
```
````

- [ ] **Paso 7: S48, S57 y el tablero de inversión social**

En `docs/ux/screens.md`, reemplazar:

```markdown
  en estados de aprobación cuyas transiciones el rol tiene, más los avisos `approval_pending`; los escalamientos vienen de
```

por:

```markdown
  en estados de aprobación cuyas transiciones el rol tiene, más los avisos `approval_pending` (desde F5, también los de
  `submit` de WF-G y los del alta de un aporte, que llegan antes de sus ítems); los escalamientos vienen de
```

En `docs/ux/screens.md`, reemplazar:

```markdown
  compromiso lo hacen SMG y CMM desde F4 (`security.md` §7.5).
```

por:

```markdown
  compromiso lo hacen SMG y CMM desde F4 (`security.md` §7.5), y como aporte, desde F5, SMG y SIM, que tienen CREATE
  sobre `social_contribution`; CMM solo acepta como compromiso (ASM-117).
```

En `docs/ux/screens.md`, reemplazar:

```markdown
  "Usar respuesta alineada" (mensajes del proyecto); en la solicitud, "Aceptar: ¿qué se crea?" con las dos opciones.
```

por:

```markdown
  "Usar respuesta alineada" (mensajes del proyecto); en la solicitud, "Aceptar: ¿qué se crea?" con las dos opciones;
  "Aporte" solo aparece a quien tiene CREATE sobre `social_contribution`.
```

En `docs/ux/screens.md`, reemplazar:

```markdown
- **Disposición y componentes:** un medidor por campaña frente a su tope (KPI-INV-01), con la marca de lo aprobado y la
  advertencia de otra moneda; barras por programa y año (KPI-INV-02); consolidado por moneda y fuente (KPI-INV-03);
  beneficiarios por fuente (KPI-INV-04).
```

por:

```markdown
- **Disposición y componentes:** un medidor por campaña frente a su tope (KPI-INV-01), con la marca de lo comprometido
  y la de lo ejecutado y la advertencia de otra moneda; barras por programa y año (KPI-INV-02); consolidado por moneda
  y fuente (KPI-INV-03); beneficiarios en tres bloques que no se suman (KPI-INV-04).
```

En `docs/ux/screens.md`, reemplazar:

```markdown
- **Datos y API:** tablero `investment` (`dashboards.md` §5.10); montos según ASM-063.
```

por:

```markdown
- **Datos y API:** tablero `investment` (`dashboards.md` §5.10), con los filtros de proyecto, campaña, programa, año,
  periodo y localidad; montos según ASM-063 y ASM-113.
```

- [ ] **Paso 8: Verificar y commit**

Repetir la comprobación del Paso 1 (esperado: `ok`) y las compuertas de «Global Constraints». Después:

```bash
git add docs/dashboards.md docs/ux/screens.md
git commit -F - <<'MSG'
docs: tablero de inversión y pantallas de F5 al día

dashboards.md suma lo comprometido de los aportes, los filtros program y year y KPI-INV-04 en tres
bloques; S42 a S44, S11, S48 y S57 siguen los permisos y las reglas de la especificación de F5, con
el acta de recepción como evidencia mientras UPS-23 siga abierta.

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_017MAgYcNauGp6cC3ekX1h7U
MSG
```

---

### Tarea 8: Roadmap, trazabilidad, entregables, encaje con la plataforma y glosario

El roadmap de F5 toma la definición de terminado de la especificación §7 y corrige AUT-04 (C4); la trazabilidad
completa las filas de INV y las de RF-GRV-004, RQ-SEC-006 y RQ-DSH-030 sin cambiar sus estados (decisión 4 de este
plan); `deliverables.md` §6 y `platform-fit.md` ganan lo de F5 (decisión 8); el glosario suma cuatro términos.

**Archivos:**
- Modificar: `docs/roadmap.md` (F5, brechas por fase, preguntas que no bloquean)
- Modificar: `docs/requirements-traceability.md` (RF-GRV-004, RF-INV-001 a 004, RF-DSH-010, RQ-DSH-030, RQ-SEC-006,
  RN-INV-001 a 006)
- Modificar: `docs/deliverables.md` (§6, «Apoyo entregado»), `docs/platform-fit.md` (fila BC-10), `docs/glossary.md`
  («Inversión»)
- Probar: `tools/check_traceability.py`

**Interfaces:**
- Consume: ASM-110 a ASM-119 y GAP-033 (Tarea 1); los nombres de prueba de `workflows.md` «Pruebas» (Tarea 3).
- Produce: la «Definición de terminado» de F5 que cierran 5.2b y el coordinador; la fila «Estado» de F5, donde 5.2b
  anota la medición de las subconsultas; los estados de las filas de la trazabilidad sin cambios (decisión 11).

- [ ] **Paso 1: La comprobación falla**

```bash
python3 - <<'PY'
import collections, re
checks = [
    ('docs/roadmap.md', '| Estado | Diseñada:', True),
    ('docs/roadmap.md', '| F5 | UPS-23 (AUT-04, ASM-116) |', True),
    ('docs/roadmap.md', '| GAP-033 |', True),
    ('docs/roadmap.md', '| Entregables | Documento «Acta de recepción»', False),
    ('docs/requirements-traceability.md', '`ContributionGuidelinesTest`', True),
    ('docs/requirements-traceability.md', '`InvestmentDashboardApiTest` (propuesto)', False),
    ('docs/deliverables.md', '`item_kind`', True),
    ('docs/platform-fit.md', 'tras UPS-23 (ASM-116)', True),
    ('docs/glossary.md', '| Tema priorizado |', True),
]
bad = [f"{p}: {'falta' if want else 'sobra'} {s!r}" for p, s, want in checks
       if (s in open(p, encoding='utf-8').read()) != want]
matrix = open('docs/requirements-traceability.md', encoding='utf-8').read()
main = re.search(r'^## 2\..*?(?=^## 6\.)', matrix, re.M | re.S).group(0)
states = collections.Counter()
for line in main.splitlines():
    if re.match(r'^\| (?:RF|RNF|RN|RQ)-[A-Z0-9]+-\d{3} \|', line):
        cell = line.rstrip().rstrip('|').split('|')[-1]
        for name in ('Diseñado', 'Implementado', 'Decisión pendiente', 'Upstream', 'Hueco', 'Fuera del MVP'):
            states[name] += name in cell
wanted = {'Diseñado': 238, 'Implementado': 46, 'Decisión pendiente': 8, 'Upstream': 21, 'Hueco': 0, 'Fuera del MVP': 82}
if dict(states) != wanted:
    bad.append(f'estados {dict(states)} != {wanted}')
print('\n'.join(bad) or 'ok')
raise SystemExit(1 if bad else 0)
PY
```

Esperado: salida 1 con las nueve líneas de texto. Los conteos por estado ya dan 238, 46, 8, 21, 0 y 82 y deben seguir
dándolos (decisión 11: §8 de la trazabilidad dice otra cosa desde antes de F5 y no se toca).

- [ ] **Paso 2: `roadmap.md` F5**

En `docs/roadmap.md`, reemplazar:

```markdown
| Alcance | Aportes sociales con ítems, comprobante y entrega con acta, foto y comprobante [DOC-02 p.3–p.4]; programas sociales, iniciativas, grupos de beneficiarios, presupuesto, indicadores, mediciones e hitos; consolidación de la ejecución frente a topes y presupuestos (ASM-063); cierre de la solicitud al cerrar su aporte (ASM-068) |
| Objetos (10) | BC-10: `social_contribution`, `contribution_item`, `receipt`, `social_program`, `social_initiative`, `beneficiary_group`, `program_budget`, `program_indicator`, `program_measurement`, `program_milestone` |
| Starters y capacidades | Ninguno nuevo |
| Código SGSPE | `sgspe.investment` completo: `InvestmentWriteGuard`, `ContributionTotalsListener`, entrega de aportes y recalculadores de montos; `RequestClosureListener` también al cerrar un aporte |
| Endpoints | `POST /api/sgspe/social-contributions/{id}/deliveries`; `POST /api/sgspe/stakeholder-requests/{id}/accept` como aporte; tablero `investment` |
| Workflows | WF-G (`social_program`), WF-P (`social_contribution`); automatización AUT-04 |
| Portal | Programas sociales (con los aportes) |
| Roles con permisos desde F5 | `SOCIAL_INVESTMENT_MANAGER` |
| KPIs | KPI-INV-01, KPI-INV-02, KPI-INV-03, KPI-INV-04; tablero `investment` |
| Entregables | Documento «Acta de recepción»; datos de la sección "Apoyo entregado" de T2 |
| Definición de terminado | La general, más: `SocialProgramWorkflowApiTest`, `SocialContributionWorkflowApiTest` y el caso de `StakeholderRequestWorkflowApiTest` que cierra la solicitud al cerrar su aporte; objetos nuevos clasificados en `ProjectScope` (`ArchitectureTest`); `delivery` con dos padres: se lee por el alcance de su `commitment` o de su `social_contribution`, nunca «sin padre, todos», con `ProjectReadCondition` y la regla de los hijos con padre opcional de `ArchitectureTest` actualizadas (security.md §9.7) |
| Riesgos | Estructura real de los programas desconocida (GAP-007); montos en otra moneda que no se suman (ASM-063) |
| IDs abiertos | Condicionan: GAP-006, GAP-007, GAP-032; ASM-019, ASM-063, ASM-068 |
```

por:

```markdown
| Alcance | Aportes sociales con ítems por tipo cargados antes de aprobar, tema, comprobante y una sola entrega con acta, foto y comprobante [DOC-02 p.3–p.4] (ASM-110, ASM-111); lineamientos de apoyo evaluados al aprobar con los temas priorizados del proyecto (ASM-112); programas sociales, iniciativas, grupos de beneficiarios, presupuesto por año con el ejecutado registrado a mano (ASM-113), indicadores con línea base y meta, mediciones inmutables e hitos; consolidación de lo comprometido y lo ejecutado frente a topes y presupuestos (ASM-063); aceptación de una solicitud como aporte y cierre de la solicitud al cerrar su aporte (ASM-068, ASM-117); alcance por proyecto de hijos de hijos y de la entrega con dos padres (ASM-114) |
| Objetos (10) | BC-10: `social_contribution`, `contribution_item`, `receipt`, `social_program`, `social_initiative`, `beneficiary_group`, `program_budget`, `program_indicator`, `program_measurement`, `program_milestone` |
| Starters y capacidades | Ninguno nuevo |
| Código SGSPE | `sgspe.investment` completo: `InvestmentWriteGuard` (P1 a P9 de WF-P, G1 a G4 de WF-G, ítems y términos congelados, entrega única), `ContributionCapRule` y `ContributionGuidelines`, `SupportCap` y `CampaignCommitted` (también en `SupportPolicyGuard`), `ContributionDeliveries`, `ContributionTotalsListener`, `ProgramTotalsListener` y sus recalculadores. `sgspe.scope`: `ProjectScope` con cadenas y padres alternativos, `ProjectReadCondition`, `ProjectResolver`, `ProjectLinks` y `ProjectLinkGuard` con BC-10 y los orígenes de la tarea. `sgspe.attention`: aceptación como aporte, `created_contribution` como campo de comando, `RequestClosureListener` en `close` de WF-P y `close_unfulfilled` con el aporte. `sgspe.notification`: avisos de aprobación de WF-G y del alta del aporte. `sgspe.dashboard`: `InvestmentKpis`. `sgspe.common`: montos y conteos de F5 (ASM-118) y orígenes de tareas |
| Endpoints | `POST /api/sgspe/social-contributions/{id}/deliveries`; `PATCH /api/sgspe/deliveries/{id}` también para la entrega de un aporte; `POST /api/sgspe/stakeholder-requests/{id}/accept` como aporte; tablero `investment` |
| Workflows | WF-G (`social_program`), WF-P (`social_contribution`); WF-E3 y WF-N suman `SOCIAL_INVESTMENT_MANAGER`; AUT-04 cuando se resuelva UPS-23 (ASM-116) |
| Portal | Programas sociales (con los aportes): S42, S43 y S44; S57 acepta como aporte; S11 con los temas priorizados; S48 con las aprobaciones de programas y aportes; tablero de inversión social |
| Roles con permisos desde F5 | `SOCIAL_INVESTMENT_MANAGER` |
| KPIs | KPI-INV-01, KPI-INV-02, KPI-INV-03, KPI-INV-04; tablero `investment` |
| Entregables | Datos de la sección «Apoyo entregado» de T2; el «Acta de recepción» cuando se resuelva UPS-23 (hasta entonces, el acta firmada se sube como evidencia ACTA DE RECEPCION, ASM-116) |
| Definición de terminado | La general, más: `SocialProgramWorkflowApiTest`, `SocialContributionWorkflowApiTest` y el caso de `StakeholderRequestWorkflowApiTest` que cierra la solicitud al cerrar su aporte; los objetos nuevos clasificados en `ProjectScope` (`ArchitectureTest`); `delivery` con dos padres, leída por el padre que tenga, nunca «sin padre, todos», y `program_measurement` por la cadena de su indicador (`ProjectReadScopeApiTest`, security.md §9.7); las columnas «otro proyecto» y «no asignado» de `PermissionMatrixApiTest` y `WorkflowMatrixApiTest` en verde con los diez objetos y la entrega de un aporte; `InvestmentDashboardApiTest`; la medición de las subconsultas anotada en «Estado»; el tenant de desarrollo en F5 (`upgrade_tenants.py`) |
| Riesgos | Estructura real de los programas y de sus indicadores desconocida (GAP-007, GAP-032); montos en otra moneda que no se suman (ASM-063); AUT-04 pospuesta mientras UPS-23 siga abierta (ASM-116); costo de las subconsultas anidadas y de la disyunción de `delivery` (lo decide la medición de la especificación de F5 §7, con la reescritura `UNION ALL` como respaldo); un único sobre una relación opcional de `delivery` que wasichai quizá no admita (queda la regla con candado); diez objetos más alargan el aprovisionamiento y la matriz de permisos por el túnel |
| IDs abiertos | Condicionan: GAP-006, GAP-007, GAP-032, GAP-033; UPS-23; ASM-019, ASM-063, ASM-068, ASM-110 a ASM-119 |
| Estado | Diseñada: especificación `docs/superpowers/specs/2026-10-11-fase-5-design.md` y documentos al día (plan 5.0). Quedan los planes 5.1 (modelo y alcance), 5.2a (aportes), 5.2b (programas y tablero) y 5.3 (portal); al cerrar, se anotan aquí la medición de las subconsultas y el tenant de desarrollo en F5 |
```

En `docs/roadmap.md`, reemplazar:

```markdown
| F4 | UPS-27, UPS-28 |
```

por:

```markdown
| F4 | UPS-27, UPS-28 |
| F5 | UPS-23 (AUT-04, ASM-116) |
```

En `docs/roadmap.md`, reemplazar:

```markdown
| GAP-032 | Indicadores de programa con línea base y meta | F5 |
```

por:

```markdown
| GAP-032 | Indicadores de programa con línea base y meta | F5 |
| GAP-033 | La prioridad de los aportes se mide solo por tema; los caseríos sin beneficio laboral no se evalúan (ASM-112) | F5 |
```

- [ ] **Paso 3: La trazabilidad de F5**

En `docs/requirements-traceability.md`, reemplazar:

```markdown
| RF-GRV-004 | [DOC-01 §9.3.1.2] | BC-07 · `model/workflows/stakeholder_request.json` (WF-E3); `sgspe.attention` (aceptación, `RequestClosureListener`); automatización AUT-08 | `stakeholder_request`, `commitment`, `social_contribution` (desde F5) | `POST /api/sgspe/stakeholder-requests/{id}/accept`; `/api/objects/stakeholder_request/records/{id}/transitions/{name}` | Portal › Quejas, consultas y solicitudes: ficha de `stakeholder_request` | `StakeholderRequestWorkflowApiTest` | F4 | Diseñado |
```

por:

```markdown
| RF-GRV-004 | [DOC-01 §9.3.1.2] | BC-07 · `model/workflows/stakeholder_request.json` (WF-E3); `sgspe.attention` (aceptación, `RequestClosureListener`; desde F5, aceptación como aporte y `created_contribution` como campo de comando, ASM-117); automatización AUT-08 | `stakeholder_request`, `commitment`, `social_contribution` (desde F5) | `POST /api/sgspe/stakeholder-requests/{id}/accept`; `/api/objects/stakeholder_request/records/{id}/transitions/{name}` | Portal › Quejas, consultas y solicitudes: ficha de `stakeholder_request` | `StakeholderRequestWorkflowApiTest` (con los casos de F5) | F4 | Diseñado |
```

En `docs/requirements-traceability.md`, reemplazar:

```markdown
| RF-INV-001 | [DOC-02 p.3–p.4] [DOC-01 §9.3.1.2] | BC-10 · `model/model.json`; `model/workflows/social_contribution.json` (WF-P); `sgspe.investment` (`InvestmentWriteGuard`, `ContributionTotalsListener`); automatización AUT-04 | `social_contribution`, `contribution_item`, `receipt`, `delivery`, `evidence` | `/api/objects/social_contribution/records`; `/api/objects/contribution_item/records`; `/api/objects/receipt/records`; `POST /api/sgspe/social-contributions/{id}/deliveries` | Portal › Programas sociales: ficha de `social_contribution` | `SocialContributionWorkflowApiTest` | F5 | Diseñado; Fuera del MVP |
| RF-INV-002 | [DOC-01 Tabla 9.2, §9.3.1.2] | BC-10 · `model/model.json` (políticas de `project`); `sgspe.investment` (`SupportPolicyGuard`) | `project`, `exploration_campaign` | `/api/objects/project/records`; `/api/objects/exploration_campaign/records` | Portal › Proyectos: ficha de `project` | `CommitmentWorkflowApiTest`; `SocialContributionWorkflowApiTest`; `SupportPolicyApiTest`, `SupportCapTest` | F3 | Implementado (tope de compromisos, F3); Diseñado (aportes, F5) |
| RF-INV-003 | [PRM-01 §5, §24] [DOC-01 §9.2, §9.3.1.2, Tabla 9.3] | BC-10 · `model/model.json`; `model/workflows/social_program.json` (WF-G); `sgspe.investment` (`InvestmentWriteGuard`) | `social_program`, `social_initiative`, `beneficiary_group`, `program_budget`, `program_indicator`, `program_measurement`, `program_milestone` | `/api/objects/social_program/records`; `/api/objects/social_initiative/records`; `/api/objects/program_budget/records`; `/api/objects/social_program/records/{id}/transitions/{name}` | Portal › Programas sociales: ficha de `social_program` | `SocialProgramWorkflowApiTest` | F5 | Diseñado; Fuera del MVP |
| RF-INV-004 | [DOC-01 §9.3.1.2] | BC-10 · `sgspe.investment` (recalculadores de montos); `sgspe.common` (`DerivedFieldsJob`, JOB-02); `sgspe.dashboard` | `exploration_campaign`, `commitment`, `social_contribution`, `program_budget` | `GET /api/sgspe/dashboards/investment`; `GET /api/sgspe/dashboards/indicators/{code}` | Portal › Programas sociales: tablero de inversión social (KPI-INV-01, KPI-INV-03) | `InvestmentDashboardApiTest` (propuesto); `workflows.md` §Pruebas «Trabajos» | F5 | Diseñado; Fuera del MVP |
```

por:

```markdown
| RF-INV-001 | [DOC-02 p.3–p.4] [DOC-01 §9.3.1.2] | BC-10 · `model/model.json` (`item_kind`, `contribution_topic`, único de `delivery.social_contribution`); `model/workflows/social_contribution.json` (WF-P); `sgspe.investment` (`InvestmentWriteGuard`, `ContributionCapRule`, `ContributionGuidelines`, `ContributionDeliveries`, `ContributionTotalsListener`); automatización AUT-04 (tras UPS-23, ASM-116) | `social_contribution`, `contribution_item`, `receipt`, `delivery`, `evidence` | `/api/objects/social_contribution/records`; `/api/objects/contribution_item/records`; `/api/objects/receipt/records`; `POST /api/sgspe/social-contributions/{id}/deliveries`; `PATCH /api/sgspe/deliveries/{id}` | Portal › Programas sociales: ficha de `social_contribution` (S44) | `SocialContributionWorkflowApiTest`; `ContributionGuidelinesTest`; `AmountFieldsGuardTest` | F5 | Diseñado; Fuera del MVP |
| RF-INV-002 | [DOC-01 Tabla 9.2, §9.3.1.2] | BC-10 · `model/model.json` (políticas de `project`; desde F5, `project_priority_topic`); `sgspe.investment` (`SupportPolicyGuard`; desde F5, `ContributionGuidelines`) | `project`, `exploration_campaign`, `topic` (desde F5) | `/api/objects/project/records`; `/api/objects/exploration_campaign/records`; `/api/objects/project/records/{id}/related/project_priority_topic` (desde F5) | Portal › Proyectos: ficha de `project` (S11, con los temas priorizados desde F5) | `CommitmentWorkflowApiTest`; `SocialContributionWorkflowApiTest`; `SupportPolicyApiTest`, `SupportCapTest`; `ContributionGuidelinesTest` (desde F5) | F3 | Implementado (tope de compromisos, F3); Diseñado (aportes, F5) |
| RF-INV-003 | [PRM-01 §5, §24] [DOC-01 §9.2, §9.3.1.2, Tabla 9.3] | BC-10 · `model/model.json`; `model/workflows/social_program.json` (WF-G); `sgspe.investment` (`InvestmentWriteGuard`: G1 a G4 y las reglas de los hijos; `ProgramTotalsListener`); `sgspe.scope` (`ProjectScope`: `program_measurement` por la cadena de su indicador, ASM-114) | `social_program`, `social_initiative`, `beneficiary_group`, `program_budget`, `program_indicator`, `program_measurement`, `program_milestone` | `/api/objects/social_program/records`; `/api/objects/social_initiative/records`; `/api/objects/program_budget/records`; `/api/objects/social_program/records/{id}/transitions/{name}` | Portal › Programas sociales: ficha de `social_program` (S43) | `SocialProgramWorkflowApiTest`; `ProjectReadScopeApiTest` (mediciones) | F5 | Diseñado; Fuera del MVP |
| RF-INV-004 | [DOC-01 §9.3.1.2] | BC-10 · `sgspe.investment` (recalculadores de montos, `CampaignCommitted`); `sgspe.common` (`DerivedFieldsJob`, JOB-02); `sgspe.dashboard` (`InvestmentKpis`) | `exploration_campaign`, `commitment`, `social_contribution`, `program_budget` | `GET /api/sgspe/dashboards/investment`; `GET /api/sgspe/dashboards/indicators/{code}` | Portal › Programas sociales: tablero de inversión social (KPI-INV-01, KPI-INV-03) | `InvestmentDashboardApiTest`; `CampaignCommittedTest`; `workflows.md` §Pruebas «Trabajos» | F5 | Diseñado; Fuera del MVP |
```

En `docs/requirements-traceability.md`, reemplazar:

```markdown
| `InvestmentDashboardApiTest` (propuesto); `InvestmentDashboard.test.tsx` (propuesto) | F5, F8 |
```

por:

```markdown
| `InvestmentDashboardApiTest`; `InvestmentDashboard.test.tsx` (propuesto) | F5, F8 |
```

En `docs/requirements-traceability.md`, reemplazar:

```markdown
| RQ-DSH-030 | [PRM-01 §16, §29] [DOC-01 §9.3.1.2] | BC-15 · `sgspe.dashboard` | `exploration_campaign`, `program_budget`, `social_contribution`, `beneficiary_group`, `commitment` | `GET /api/sgspe/dashboards/investment`; `GET /api/sgspe/dashboards/indicators/{code}` | Portal › Programas sociales: tablero de inversión social | `InvestmentDashboardApiTest` (propuesto) | F5 | Diseñado; Fuera del MVP |
```

por:

```markdown
| RQ-DSH-030 | [PRM-01 §16, §29] [DOC-01 §9.3.1.2] | BC-15 · `sgspe.dashboard` (`InvestmentKpis`) | `exploration_campaign`, `program_budget`, `social_contribution`, `beneficiary_group`, `commitment` | `GET /api/sgspe/dashboards/investment`; `GET /api/sgspe/dashboards/indicators/{code}` | Portal › Programas sociales: tablero de inversión social | `InvestmentDashboardApiTest` | F5 | Diseñado; Fuera del MVP |
```

En `docs/requirements-traceability.md`, reemplazar:

```markdown
| RQ-SEC-006 | [PRM-01 §7] | BC-16 · `sgspe.scope` (`ProjectScopeGuard`, `ProjectReadScope`, `IssueDocumentFilter`); `sgspe.common` (`ProjectReach`) | `project_assignment` | `/api/objects/{object}/records`; `/api/sgspe/**` | — | `ProjectReadScopeApiTest`; `ProjectReadConditionTest`; `ProjectReadScopeTest`; `ProjectScopeApiTest`; `PermissionMatrixApiTest` («otro proyecto») | F2 | Diseñado (ADR-0029); territorio y región, Upstream (UPS-01) |
```

por:

```markdown
| RQ-SEC-006 | [PRM-01 §7] | BC-16 · `sgspe.scope` (`ProjectScopeGuard`, `ProjectReadScope`, `IssueDocumentFilter`; desde F5, `ProjectScope` con cadenas y padres alternativos, ASM-114); `sgspe.common` (`ProjectReach`) | `project_assignment` | `/api/objects/{object}/records`; `/api/sgspe/**` | — | `ProjectReadScopeApiTest`; `ProjectReadConditionTest`; `ProjectReadScopeTest`; `ProjectScopeApiTest`; `PermissionMatrixApiTest` («otro proyecto»); `ArchitectureTest` (desde F5, cadenas y dos padres) | F2 | Diseñado (ADR-0029); territorio y región, Upstream (UPS-01) |
```

En `docs/requirements-traceability.md`, reemplazar:

```markdown
| RN-INV-001 | [DOC-01 Tabla 9.2, §9.3.1.2] | BC-10 · `sgspe.investment` (`SupportPolicyGuard`); `model/model.json` (`policy_no_paid_labor`) | `commitment`, `social_contribution` (desde F5), `social_initiative` (desde F5) | `/api/objects/commitment/records/{id}/transitions/{name}`; `/api/objects/social_contribution/records/{id}/transitions/{name}` (desde F5) | Portal › Compromisos; Programas sociales (desde F5) | `SupportPolicyGuardTest` (propuesto) | F3, F5 | Diseñado |
| RN-INV-002 | [DOC-01 §9.3.1.2] | BC-10 · `sgspe.investment` (`SupportPolicyGuard`); `model/model.json` (`policy_non_monetary_donations`) | `social_contribution`, `festivity` | `/api/objects/social_contribution/records/{id}/transitions/{name}` | Portal › Programas sociales: ficha de `social_contribution` | `SupportPolicyGuardTest` (propuesto) | F5 | Diseñado; Fuera del MVP |
| RN-INV-003 | [DOC-01 §9.3.1.2] | BC-10 · `sgspe.investment` (`SupportPolicyGuard`); `model/roles.json` (`APPROVE_OVER_CAP`) | `exploration_campaign`, `commitment`, `social_contribution` (desde F5) | `/api/objects/commitment/records/{id}/transitions/{name}`; `/api/objects/social_contribution/records/{id}/transitions/{name}` (desde F5); `POST /api/sgspe/transition-checks` | Portal › Compromisos; Programas sociales (desde F5) | `CommitmentWorkflowApiTest`; `SocialContributionWorkflowApiTest` | F3, F5 | Diseñado |
| RN-INV-004 | [DOC-01 §9.3.1.2] | BC-10 · `sgspe.investment` (`InvestmentWriteGuard`); `model/model.json` (`policy_synergy_requires_agreement`) | `social_program`, `social_contribution`, `agreement` | `/api/objects/social_program/records/{id}/transitions/{name}`; `/api/objects/social_contribution/records/{id}/transitions/{name}` | Portal › Programas sociales | `SocialProgramWorkflowApiTest`; `SocialContributionWorkflowApiTest` | F5 | Diseñado; Fuera del MVP |
| RN-INV-005 | [DOC-01 Tabla 9.2, §9.3.1.2] | BC-10 · `sgspe.investment` (`SupportPolicyGuard`); `model/model.json` (`policy_priority_warning`) | `social_contribution` | `/api/objects/social_contribution/records/{id}/transitions/{name}`; `POST /api/sgspe/transition-checks` | Portal › Programas sociales: ficha de `social_contribution` | `SupportPolicyGuardTest` (propuesto) | F5 | Diseñado; Fuera del MVP |
| RN-INV-006 | [DOC-02 p.3–p.4, p.6] [DOC-01 §9.3.1.2] | BC-10 · `model/model.json` (`declared_purpose`, `conditions`, `payer_organization` requeridos); `sgspe.investment` (`SupportPolicyGuard`) | `social_contribution`, `receipt` | `/api/objects/social_contribution/records`; `POST /api/sgspe/transition-checks` | Portal › Programas sociales: ficha de `social_contribution` | `SupportPolicyGuardTest` (propuesto) | F5 | Diseñado; Fuera del MVP |
```

por:

```markdown
| RN-INV-001 | [DOC-01 Tabla 9.2, §9.3.1.2] | BC-10 · `sgspe.investment` (`SupportPolicyGuard`; desde F5, `ContributionGuidelines` con `item_kind`, ASM-112); `model/model.json` (`policy_no_paid_labor`) | `commitment`, `social_contribution` (desde F5), `contribution_item` (desde F5), `social_initiative` (desde F5) | `/api/objects/commitment/records/{id}/transitions/{name}`; `/api/objects/social_contribution/records/{id}/transitions/{name}` (desde F5) | Portal › Compromisos; Programas sociales (desde F5) | `SupportPolicyGuardTest` (propuesto); `ContributionGuidelinesTest` y `SocialContributionWorkflowApiTest` (desde F5) | F3, F5 | Diseñado |
| RN-INV-002 | [DOC-01 §9.3.1.2] | BC-10 · `sgspe.investment` (`ContributionGuidelines`, ASM-112); `model/model.json` (`policy_non_monetary_donations`, `item_kind`) | `social_contribution`, `contribution_item`, `festivity` | `/api/objects/social_contribution/records/{id}/transitions/{name}` | Portal › Programas sociales: ficha de `social_contribution` | `ContributionGuidelinesTest`; `SocialContributionWorkflowApiTest` | F5 | Diseñado; Fuera del MVP |
| RN-INV-003 | [DOC-01 §9.3.1.2] | BC-10 · `sgspe.investment` (`SupportPolicyGuard`; desde F5, `ContributionCapRule` y `CampaignCommitted`, ASM-110, ASM-119); `model/roles.json` (`APPROVE_OVER_CAP`) | `exploration_campaign`, `commitment`, `social_contribution` (desde F5) | `/api/objects/commitment/records/{id}/transitions/{name}`; `/api/objects/social_contribution/records/{id}/transitions/{name}` (desde F5); `POST /api/sgspe/transition-checks` | Portal › Compromisos; Programas sociales (desde F5) | `CommitmentWorkflowApiTest`; `SocialContributionWorkflowApiTest`; `SupportCapTest`, `CampaignCommittedTest` (desde F5) | F3, F5 | Diseñado |
| RN-INV-004 | [DOC-01 §9.3.1.2] | BC-10 · `sgspe.investment` (`InvestmentWriteGuard`: P6 del aporte y G3 del programa, ASM-112, ASM-115); `model/model.json` (`policy_synergy_requires_agreement`) | `social_program`, `social_contribution`, `agreement`, `agreement_party` | `/api/objects/social_program/records/{id}/transitions/{name}`; `/api/objects/social_contribution/records/{id}/transitions/{name}` | Portal › Programas sociales | `SocialProgramWorkflowApiTest`; `SocialContributionWorkflowApiTest` | F5 | Diseñado; Fuera del MVP |
| RN-INV-005 | [DOC-01 Tabla 9.2, §9.3.1.2] | BC-10 · `sgspe.investment` (`ContributionGuidelines` con los temas priorizados, ASM-112; los caseríos, GAP-033); `model/model.json` (`policy_priority_warning`, `project_priority_topic`, `contribution_topic`) | `social_contribution`, `project`, `topic` | `/api/objects/social_contribution/records/{id}/transitions/{name}`; `POST /api/sgspe/transition-checks` | Portal › Programas sociales: ficha de `social_contribution`; S11 (temas priorizados) | `ContributionGuidelinesTest`; `SocialContributionWorkflowApiTest` | F5 | Diseñado; Fuera del MVP |
| RN-INV-006 | [DOC-02 p.3–p.4, p.6] [DOC-01 §9.3.1.2] | BC-10 · `model/model.json` (`declared_purpose`, `conditions`, `payer_organization` requeridos); `sgspe.investment` (`InvestmentWriteGuard` con `ContributionGuidelines`: una justificación cubre todos los lineamientos incumplidos, ASM-112) | `social_contribution`, `receipt` | `/api/objects/social_contribution/records`; `POST /api/sgspe/transition-checks` | Portal › Programas sociales: ficha de `social_contribution` | `ContributionGuidelinesTest`; `SocialContributionWorkflowApiTest` | F5 | Diseñado; Fuera del MVP |
```

- [ ] **Paso 4: `deliverables.md`, `platform-fit.md` y el glosario**

En `docs/deliverables.md`, reemplazar:

```markdown
| Apoyo entregado | Pedido, ocasión, propósito y condiciones declarados, ítems comprados y entrega con acta de recepción | `social_contribution` (`contribution_type`, `occasion`, `declared_purpose`, `conditions`, `payer_organization`, `recipient_organization`); `contribution_item`; `receipt` (`receipt_type`, `issuer_name`, `issue_date`, `amount`); `delivery` | Narrativa con tabla de ítems | Calculada y humana | F5 | F8 | RF-INV-001, RN-INV-006, CON-021, CON-022 [DOC-02 p.3–p.4] |
```

por:

```markdown
| Apoyo entregado | Pedido, ocasión, propósito y condiciones declarados, ítems comprados por tipo y la entrega con su acta de recepción (la evidencia ACTA DE RECEPCION firmada mientras UPS-23 siga abierta, ASM-116) | `social_contribution` (`contribution_type`, `occasion`, `declared_purpose`, `conditions`, `payer_organization`, `recipient_organization`, `topic`); `contribution_item` (`item_kind`, `line_amount`); `receipt` (`receipt_type`, `issuer_name`, `issue_date`, `amount`); `delivery` (la única del aporte, ASM-111) | Narrativa con tabla de ítems | Calculada y humana | F5 | F8 | RF-INV-001, RN-INV-006, CON-021, CON-022 [DOC-02 p.3–p.4] |
```

En `docs/platform-fit.md`, reemplazar:

```markdown
| BC-10 Inversión social | `social_contribution` `requiresReason`; `program_measurement` `appendOnly` | WF-G, WF-P | Acta de recepción al entregar el aporte | Ubicación de iniciativas | `SupportPolicyGuard`, `InvestmentWriteGuard`, `ContributionTotalsListener` | `sgspe.investment` | F5 (tope en F3) |
```

por:

```markdown
| BC-10 Inversión social | `social_contribution` `requiresReason`; `program_measurement` `appendOnly` | WF-G, WF-P | Acta de recepción al entregar el aporte, tras UPS-23 (ASM-116) | Ubicación de iniciativas | `SupportPolicyGuard`, `InvestmentWriteGuard`, `ContributionTotalsListener`, `ProgramTotalsListener`; alcance por proyecto con cadenas y dos padres (`ProjectScope`, ASM-114) | `sgspe.investment` | F5 (tope en F3) |
```

En `docs/glossary.md`, reemplazar:

```markdown
| Ítem del aporte | `contribution_item` | — | Bien o servicio que compone un aporte, con cantidad y costo | [DOC-02 p.4] | — | ítem de un compromiso |
```

por:

```markdown
| Ítem del aporte | `contribution_item` | — | Bien, servicio, dinero o mano de obra que compone un aporte, con su tipo, cantidad y monto (`line_amount`, el que cuenta, ASM-110) | [DOC-02 p.4] | — | ítem de un compromiso |
| Tipo de ítem del aporte | `contribution_item.item_kind` | — | BIEN, SERVICIO, DINERO o MANO DE OBRA; lo leen los lineamientos de apoyo al aprobar (ASM-112) | [DOC-01 Tabla 9.2] [DOC-02 p.4] | — | tipo de aporte (`contribution_type`) |
```

En `docs/glossary.md`, reemplazar:

```markdown
| Lineamiento de apoyo | política del proyecto | — | Regla configurable de compromisos y aportes: empleo limitado y rotativo, no pagar mano de obra, donaciones no monetarias, temas priorizados, tope por campaña | [DOC-01 Tabla 9.2, §9.3.1.2] | directriz, política | regla dura del sistema |
```

por:

```markdown
| Lineamiento de apoyo | política del proyecto | — | Regla configurable de compromisos y aportes: empleo limitado y rotativo, no pagar mano de obra, donaciones no monetarias, temas priorizados, tope por campaña | [DOC-01 Tabla 9.2, §9.3.1.2] | directriz, política | regla dura del sistema |
| Comprometido (monto) | conjunto de estados de `commitment` y `social_contribution` | — | Lo aprobado y no anulado de una campaña: compromisos de `approved` a `closed` y aportes en `approved`, `procured`, `delivered` o `closed`, en la moneda de la campaña; es lo que se compara con el tope (ASM-110) | [DOC-01 §9.3.1.2] | monto aprobado | ejecutado (lo entregado, ASM-063) |
| Tema priorizado | `project_priority_topic` (M2M de `project` con `topic`) | — | Tema del catálogo que el proyecto prioriza para sus aportes; un tema cuenta si es priorizado o desciende de uno (RN-INV-005, ASM-112) | [DOC-01 Tabla 9.2, §9.3.1.2] | prioridad | tema clave (`key_theme`) |
| Desviación de lineamientos | `guideline_deviation_justification` de `social_contribution` | — | Justificación con la que se aprueba un aporte que incumple uno o más lineamientos (mano de obra, donación monetaria o tema no priorizado); cubre todos a la vez y no cubre la sinergia sin acuerdo, que bloquea (RN-INV-006, ASM-112) | [DOC-01 §9.3.1.2] | justificación | sinergia sin acuerdo (P6) |
```

- [ ] **Paso 5: Verificar y commit**

Repetir la comprobación del Paso 1 (esperado: `ok`, con los mismos totales por estado) y las compuertas de «Global
Constraints» (`ok: 298 requisitos, todos con su fila`). Después:

```bash
git add docs/roadmap.md docs/requirements-traceability.md docs/deliverables.md docs/platform-fit.md docs/glossary.md
git commit -F - <<'MSG'
docs: roadmap, trazabilidad y glosario de F5

F5 toma la definición de terminado de la especificación y espera AUT-04 tras UPS-23; las filas de
INV, RF-GRV-004, RQ-SEC-006 y RQ-DSH-030 nombran las piezas y pruebas de F5 sin cambiar estados; el
glosario suma lo comprometido, el tema priorizado y la desviación de lineamientos.

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_017MAgYcNauGp6cC3ekX1h7U
MSG
```

---

### Tarea 9: La historia y `CLAUDE.md`

**Archivos:**
- Modificar: `docs/HISTORY.md`, `CLAUDE.md` («Dónde está cada cosa»)

**Interfaces:**
- Consume: las Tareas 1 a 8 integradas.
- Produce: la entrada «Plan 5.0: los documentos de F5» y la fila de `sgspe.investment` que leen los agentes de 5.1 a
  5.3.

- [ ] **Paso 1: La comprobación falla**

```bash
python3 - <<'PY'
checks = [
    ('docs/HISTORY.md', '## 2026-10-11 — Plan 5.0: los documentos de F5', True),
    ('CLAUDE.md', '`ContributionCapRule`', True),
    ('CLAUDE.md', '| Tope de apoyo social (`SupportPolicyGuard`) | `src/main/kotlin/sgspe/investment/` |', False),
]
bad = [f"{p}: {'falta' if want else 'sobra'} {s!r}" for p, s, want in checks
       if (s in open(p, encoding='utf-8').read()) != want]
print('\n'.join(bad) or 'ok')
raise SystemExit(1 if bad else 0)
PY
```

Esperado: salida 1 con las tres líneas.

- [ ] **Paso 2: La entrada de la historia**

En `docs/HISTORY.md`, reemplazar:

```markdown
## 2026-10-11 — UPS-24: el descriptor de una evidencia restringida sin nombre ni huella
```

por:

```markdown
## 2026-10-11 — Plan 5.0: los documentos de F5

Los documentos de diseño quedan al día con la especificación de F5
(`docs/superpowers/specs/2026-10-11-fase-5-design.md`) antes de tocar el modelo y el código. Las decisiones están en
[Requisitos](requirements-analysis.md) (ASM-110 a ASM-119 y GAP-033; 538 IDs).

- **Aportes.** Los ítems, con su tipo (`item_kind`), se cargan antes de aprobar y su suma es lo que se aprueba y se
  compara con el tope, que cuenta lo comprometido de compromisos y aportes; ítems y términos aprobados quedan
  congelados; una sola entrega por aporte, con acta de recepción, foto y comprobante. WF-P se reescribe con P1 a P9
  (P9, los ítems al aprobar; P6, la sinergia, fuera de los lineamientos que se justifican), su orden y sus candados.
- **Lineamientos.** Mano de obra, donaciones monetarias y tema fuera de los temas priorizados del proyecto
  (`project_priority_topic`) se evalúan solos al aprobar y se justifican juntos; los caseríos sin beneficio laboral
  esperan su dato (GAP-033).
- **Programas.** WF-G gana G1 a G4 (sinergia con el socio como parte del acuerdo, ASM-115), las reglas de los hijos y
  `total_budget` en la moneda del programa; el ejecutado se registra a mano por año (ASM-113).
- **Alcance por proyecto.** Cadenas de hijos (`program_measurement`) y padres alternativos (`delivery`, por su
  compromiso o su aporte, nunca «sin padre, todos»): 33 raíces y 27 hijos desde F5 (ASM-114, `security.md` §9.7).
- **Huecos cerrados.** C1 a C15 de la especificación: entre ellos, AUT-04 y el «Acta de recepción» tras UPS-23 (el acta
  firmada es una evidencia), los permisos que no cerraban (SMG y CRO cargan ítems, CRO comprobantes y
  `COMMITMENT_MANAGER` acepta solo como compromiso) y la autoridad del tope distinta por objeto (ASM-119).
- **Lo que espera al modelo de F5.** El único de `delivery.social_contribution` y `u` sobre `topic` para
  `PROJECT_ADMIN` y `PROJECT_MANAGER` quedan como texto hasta que el plan 5.1 los lleve a `model.json` y `roles.json`.

## 2026-10-11 — UPS-24: el descriptor de una evidencia restringida sin nombre ni huella
```

- [ ] **Paso 3: La fila de `sgspe.investment` en `CLAUDE.md`**

En `CLAUDE.md`, reemplazar:

```markdown
| Tope de apoyo social (`SupportPolicyGuard`) | `src/main/kotlin/sgspe/investment/` |
```

por:

```markdown
| Inversión social: tope de apoyo (`SupportPolicyGuard` y, desde F5, `ContributionCapRule`, con `SupportCap` y `CampaignCommitted`), `InvestmentWriteGuard` (WF-P y WF-G), `ContributionGuidelines`, entrega de aportes (`ContributionDeliveries`), `ContributionTotalsListener` y `ProgramTotalsListener` | `src/main/kotlin/sgspe/investment/` |
```

- [ ] **Paso 4: Verificar y commit**

Repetir la comprobación del Paso 1 (esperado: `ok`) y las compuertas de «Global Constraints». Después:

```bash
git add docs/HISTORY.md CLAUDE.md
git commit -F - <<'MSG'
docs: historia del plan 5.0 y sgspe.investment en CLAUDE.md

La entrada resume los IDs nuevos, los aportes, los lineamientos, los programas y el alcance de F5;
CLAUDE.md nombra las piezas de sgspe.investment que traen los planes 5.2a y 5.2b.

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_017MAgYcNauGp6cC3ekX1h7U
MSG
```

---

### Tarea 10: Verificación final, push y PR

**Archivos:** ninguno nuevo.

- [ ] **Paso 1: Todo lo local, la comprobación cruzada y la lectura cruzada**

```bash
cd ~/IdeaProjects/sgspe
(cd model && python3 -m unittest -v 2>&1 | tail -3 && python3 apply.py --validate-only && \
  python3 load_ubigeo.py --dry-run | tail -1)
python3 tools/check_traceability.py && yarn format:check
python3 - <<'PY'
import re
docs = {p: open(p, encoding='utf-8').read() for p in (
    'docs/workflows.md', 'docs/api.md', 'docs/domain-model.md', 'docs/ux/screens.md', 'docs/security.md',
    'docs/requirements-analysis.md', 'docs/database-model.md')}
bad = []
# Review Focus 2: cada código de F5 está en workflows.md y en api.md
for code in ('ITEMS_REQUIRED', 'ITEMS_FROZEN', 'CONTRIBUTION_FROZEN', 'DELIVERY_EXISTS', 'DELIVERY_FROZEN',
             'FIELD_NOT_ALLOWED', 'PROGRAM_MISMATCH', 'PAID_LABOR', 'MONETARY_DONATION', 'PRIORITY_NOT_MET',
             'PRIORITIES_NOT_CONFIGURED', 'SYNERGY_AGREEMENT_REQUIRED'):
    for p in ('docs/workflows.md', 'docs/api.md'):
        if f'`{code}' not in docs[p] and f' {code}' not in docs[p]:
            bad.append(f'{p}: falta {code}')
# Review Focus 3: cada ASM nuevo se define una vez y otro documento lo cita
for n in range(110, 120):
    asm = f'ASM-{n}'
    if len(re.findall(rf'^\| {asm} \|', docs['docs/requirements-analysis.md'], re.M)) != 1:
        bad.append(f'{asm}: no se define una sola vez')
    if not any(asm in text for p, text in docs.items() if p != 'docs/requirements-analysis.md'):
        bad.append(f'{asm}: ningún otro documento lo cita')
# Review Focus 5: «delivery sin compromiso» solo como lo que era hasta F4
for p, text in docs.items():
    for line in text.splitlines():
        if '`delivery` sin compromiso' in line and 'hasta F4' not in line:
            bad.append(f'{p}: {line.strip()[:100]}')
print('\n'.join(bad) or 'ok')
raise SystemExit(1 if bad else 0)
PY
git status --short
```

Esperado: `Ran 222 tests … OK`, `ubigeo: 25 departamentos, 196 provincias, 1892 distritos`,
`ok: 298 requisitos, todos con su fila`, prettier sin cambios, `ok` de la comprobación cruzada y un `git status` vacío.

Lectura cruzada (Review Focus 2 y 5): leer seguidos `workflows.md` WF-P («Reglas…», «Orden y candados», «Otras
reglas»), `api.md` §3.3 (filas `400`, `403` y `409`, «Desde F5») y §4.1, `domain-model.md` §7.2 (filas de BC-10) y
`ux/screens.md` S44, y comprobar que cada regla da el mismo código, el mismo campo y el mismo estado; después
`security.md` §9.7, `domain-model.md` §4.7, ASM-106, ASM-114 y la nota de ADR-0029, y comprobar que dicen lo mismo de
la entrega huérfana y de `locality_profile`. Una diferencia se corrige en el documento que se aparta de la
especificación §3, con un commit `docs: …` propio.

- [ ] **Paso 2: Push y PR**

```bash
cd ~/IdeaProjects/sgspe && git push -u origin docs/fase-5-0-documentos
gh pr create --base main --title "docs: fase 5, plan 5.0: documentos de F5" --body-file - <<'MSG'
## Qué trae

- ASM-110 a ASM-119 y GAP-033; §20.1 suma 538 IDs. Ninguna brecha upstream nueva.
- Aportes: ítems con `item_kind` cargados antes de aprobar y congelados después, lo comprometido de compromisos y
  aportes frente al tope, la entrega única con acta de recepción, foto y comprobante, y WF-P con P1 a P9, su orden y sus
  candados.
- Lineamientos evaluados al aprobar con los temas priorizados del proyecto (`project_priority_topic`); la sinergia,
  como bloqueo propio.
- Programas: WF-G con G1 a G4 y las reglas de los hijos; el ejecutado, a mano por año.
- Alcance por proyecto con cadenas de hijos y la entrega con dos padres (`security.md` §9.7, ASM-114).
- Contratos de la API de F5, el tablero `investment` y las pantallas S11, S42 a S44, S48 y S57.
- Roadmap, trazabilidad, glosario, historia y `CLAUDE.md` al día.

## Pruebas

- Python: las 222 pruebas del modelo, con `test_las_fichas_de_f5_traen_lo_que_suma_el_plan_5_0`.
- `tools/check_traceability.py` y `yarn format:check`.

🤖 Generated with [Claude Code](https://claude.com/claude-code)

https://claude.ai/code/session_017MAgYcNauGp6cC3ekX1h7U
MSG
```

---

## Resumen

| Tarea | Entrega | Grupo |
|---|---|---|
| 1 | IDs de F5 en el análisis | 0 |
| 2 | Fichas de BC-10, `item_kind`, temas priorizados y la prueba del lector | 1 |
| 3 | `workflows.md`: WF-P, WF-G, WF-E3, listeners, avisos y pruebas | 1 |
| 4 | Modelo de dominio y arquitectura | 1 |
| 5 | Seguridad, ADR-0029 y la especificación 4.4 | 1 |
| 6 | API | 1 |
| 7 | Tableros y pantallas | 1 |
| 8 | Roadmap, trazabilidad, entregables, encaje y glosario | 1 |
| 9 | Historia y `CLAUDE.md` | 2 |
| 10 | Verificación, push y PR | 2 |

## Cobertura de la especificación §6

| Punto de §6 | Dónde |
|---|---|
| ASM-110 a ASM-119, GAP-033; §20.1 (538) | Tarea 1 |
| C1 · Tope sin monto evaluable: WF-P (P9, ítems congelados), `domain-model.md` §7.2 y §8.2, `database-model.md` §15 | Tareas 3, 4 y 2 |
| C2 · `program_measurement` nieto: `security.md` §9.7, `domain-model.md` §4.7, especificación 4.4 §3.1 | Tareas 5 y 4 |
| C3 · `delivery` con dos padres; 33 raíces y 27 hijos | Tareas 5, 4 y 2 (y ASM-106 en la 1) |
| C4 · AUT-04 tras UPS-23: `roadmap.md` F5, S44, el párrafo final de WF-P, `domain-model.md` §6.12 | Tareas 8, 7, 3 y 4 |
| C5 · Permisos que no cerraban: `security.md` §7.7, S43, S44, S57 | Tareas 5 y 7 |
| C6 · Autoridad del tope distinta: `security.md` §5.3 y §7.7, ASM-119 | Tareas 5 y 1 |
| C7 · `InvestmentWriteGuard` completo: `domain-model.md` §8.2, `workflows.md` | Tareas 4 y 3 |
| C8 · Datos de P2, P3 y P5: `item_kind`, `contribution_topic`, `project_priority_topic`, `created_by`, GAP-033 | Tareas 2, 3 y 1 |
| C9 · Montos de F5: ASM-092 y ASM-118, `api.md` §3.3 | Tareas 1 y 6 |
| C10 · `COMMAND_ONLY` de `created_contribution`: `api.md` §3.3 | Tarea 6 (y `database-model.md`, Tarea 2) |
| C11 · WF-E3 sin «desde F5» | Tarea 3 |
| C12 · `RequestClosureListener` en `close` de WF-P: `domain-model.md` §7.5 (ya lo decía) y §8.2 | Tarea 4 |
| C13 · Escritores de `ContributionTotalsListener`: `security.md` §8.1 | Tareas 5, 3 y 4 |
| C14 · Aviso al alta: `domain-model.md` §7.5, `security.md` §8.1, «Avisos» | Tareas 4, 5 y 3 |
| C15 · Conjunto «comprometido» de aportes: `dashboards.md` §3.2, KPI-INV-01 | Tarea 7 |
| `api.md` §4.6, §4.8, §3.3 | Tarea 6 |
| `workflows.md`: WF-P, WF-G, «Pruebas», «Destinatarios por rol» (sus filas de WF-G y del alta del aporte ya existían) | Tarea 3 |
| `database-model.md`: §15, `delivery`, `project`, §4 | Tarea 2 |
| `requirements-analysis.md` §10 y RF-INV-002 | Tarea 1 |
| `dashboards.md` §4.7 y §5.10 | Tarea 7 |
| `ux/screens.md`: S42 a S44, S57, S11, S48 | Tarea 7 |
| `roadmap.md` F5 («Definición de terminado», «Estado»); `CLAUDE.md` | Tareas 8 y 9 |
| `requirements-traceability.md` | Tarea 8 |
| `glossary.md`: «comprometido», «tema priorizado», «desviación de lineamientos» | Tarea 8 |

## Dependencias de otros planes

Este plan no consume nada de otro plan de F5: parte de `main` con la especificación. Lo que deja pendiente para otros,
porque el modelo de F4 no puede verlo todavía (decisión 2):

- **Plan 5.1 (modelo y alcance), en el mismo commit que `model.json` y `roles.json`:**
  - cambiar la viñeta «Únicos» de `delivery` en `docs/database-model.md` a «`unique` en `social_contribution`» (el
    modelo lo declara como `"unique": true` en la relación `delivery_social_contribution`, porque wasichai 1.0.0 rechaza
    un `uniqueConstraints` de un solo campo; decisión 1 del plan 5.1, Tarea 3, Paso 4a), con `test_model.py` en
    `PHASE = "F5"`; si wasichai rechazara el `PUT` del campo, la viñeta vuelve a «ninguno» y queda solo la regla con
    candado;
  - cambiar en `docs/security.md` §7.4 las celdas de `topic` de PAD y PMG de `R` a `Ru`, junto con `roles.json`, y
    quitar de la nota de §7.7 la frase sobre la celda que cambia al aplicar el modelo;
  - hacer que `design_docs.py` lea lo nuevo que necesite (especificación §7) y actualizar los totales de
    `test_design_docs.py` que dependen de F5 (este plan deja 79 claves ENUM y 222 pruebas).
- **Plan 5.2b:** anotar en `docs/roadmap.md` F5 «Estado» la medición de las subconsultas y el tenant de desarrollo en
  F5, como pide la «Definición de terminado».
- **Planes 5.2a, 5.2b y 5.3:** leen los nombres y los códigos de estos documentos. Si al implementar cambia un nombre
  (por ejemplo, el filtro `explorationCampaign` frente al `campaign` de la especificación §3.9, decisión 3), se corrige
  primero el documento.
