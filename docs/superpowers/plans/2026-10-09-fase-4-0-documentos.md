# SGSPE · Fase 4, plan 4.0 (documentos): plan de implementación

> **Para agentes:** SUB-SKILL OBLIGATORIA: usar superpowers:subagent-driven-development (recomendada) o
> superpowers:executing-plans para ejecutar este plan tarea por tarea. Los pasos usan casillas (`- [ ]`) para el
> seguimiento. Cada tarea sigue el ciclo de superpowers:test-driven-development aplicado a documentos: una comprobación
> que falla, los cambios exactos, la comprobación y las pruebas del modelo en verde, commit.

**Goal:** Dejar `docs/` al día con la especificación de F4 antes de tocar el modelo y el código: los IDs nuevos
(ASM-095 a ASM-104, UPS-25 y UPS-26) con los totales de §20.1; los huecos C1 (documentos de QCS frente a UPS-23), C7
(días hábiles), C10 y C6 (los seis tipos de regla de alerta) y C12 (contenido de `attention-register` y
`risk-register`); las contradicciones menores C2 a C5, C8, C9, C11 y C13 a C17; el `RecordReadScope` de las QCS
confidenciales en `security.md` y en su ADR (ADR-0028); el tablero de riesgos con su propio id (S62); y el roadmap, la
trazabilidad y la historia al día. Los planes 4.1, 4.2a, 4.2b y 4.3 leen estos documentos como fuente de verdad.

**Architecture:** Solo documentos y una prueba del lector de documentos. Cada tarea reemplaza texto exacto de uno o más
documentos (bloques «reemplazar … por …») y empieza con una comprobación en Python de solo biblioteca estándar que falla
mientras falte el cambio; las pruebas del modelo (`model/test_design_docs.py`, `test_model.py`, `test_apply_roles.py`),
`apply.py --validate-only` y `tools/check_traceability.py` siguen en verde después de cada tarea, porque
`model/design_docs.py` lee las fichas, los catálogos, la matriz de permisos, los workflows, las automatizaciones y los
tipos de documento de estos mismos archivos. Ningún archivo de `src/` ni de `model/*.json` cambia: el modelo de F4 es el
plan 4.1.

**Tech Stack:** Markdown (sin prettier: `.prettierignore` excluye `docs/` y `*.md`); Python 3.11 o superior, solo
biblioteca estándar (`model/` y `tools/`); `yarn format:check` (prettier 3.9.9) como compuerta del repositorio; husky y
commitlint para los mensajes.

**Spec:** `docs/superpowers/specs/2026-10-09-fase-4-design.md`, §6 («Cambios en los documentos (plan 4.0)») y las
secciones que cita: §2 (decisiones), §3.1 a §3.9, §4 (los seis tipos de regla), §5 (exportaciones) y §8 (riesgos).
Fuentes: `.superpowers/sdd/fase-4/readiness.md` (huecos C1 a C18) y `.superpowers/sdd/fase-4/decisiones.md`
(decisiones del usuario del 2026-10-09, vinculantes).

## Global Constraints

- **Repositorio y rama.** Todo en `~/IdeaProjects/sgspe`, en la rama `docs/fase-4-documentos` creada desde `main`
  (`621a6c9`) con la especificación de F4 ya integrada; si la rama `docs/fase-4-diseno` todavía no está en `main`, desde
  ella (solo agrega la especificación y los planes de F4, así que los bloques de este plan aplican igual). Las tareas
  paralelas (grupo 1 de «Orden y paralelismo») trabajan cada una en su worktree desde la rama ya con la Tarea 1.
- **El diseño manda.** `docs/` es la fuente de verdad y `docs/requirements-analysis.md` el único lugar donde se definen
  IDs: los ASM y UPS nuevos los define la Tarea 1 y las demás solo los citan.
- **Texto exacto.** Cada bloque «reemplazar» aparece una sola vez en el documento en el momento de aplicarlo; si no
  aparece, la rama no es la esperada: se detiene la tarea y se avisa, nunca se adapta el bloque a mano. Los bloques
  «por» ya están envueltos a 120 columnas en la prosa; las filas de tabla van en una línea, como en los documentos.
- **Idioma.** Español (es-PE) en documentos, comentarios y mensajes; nombres técnicos en inglés (ADR-0009); valores ENUM
  en español MAYÚSCULAS sin tildes.
- **Datos.** Ningún nombre real de persona; los ejemplos son ficticios.
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
- **Push y PR** solo en la Tarea 9.

## Review Focus

1. **Lo que lee `design_docs.py`.** Las tablas que parsea el lector (fichas de `database-model.md`, catálogos de §10, la
   matriz de `security.md` §7, las tablas de transiciones, de automatizaciones y de tipos de documento de
   `workflows.md`) cambian de contenido pero no de forma, y las filas de F4 no cambian los totales que fija
   `test_design_docs.py` para F2 y F3. Pruebas: `test_automatizaciones_y_tipos_de_documento` (Tarea 2, con los tres
   tipos de documento de QCS y AUT-06 a AUT-08 «F4, tras UPS-23») y la suite completa del modelo en cada tarea.
2. **Una QCS confidencial en todas las rutas.** `security.md` §9.6, `workflows.md` («Avisos», regla 7 de «Destinatarios
   por rol»), `api.md` §4.6, `dashboards.md` §8, `deliverables.md` §10 y `ux/screens.md` §8 dicen lo mismo: quién la ve,
   que los trabajos leen sin el alcance y filtran la salida, que tableros y reporte dicen `confidentialHidden` sin
   conteo, que EXTERNO la excluye también para `GRIEVANCE_MANAGER` y que el nivel 2 genérico no lleva datos.
   Comprobación: las de las Tareas 3, 5 y 6 y la lectura cruzada del Paso 1 de la Tarea 9.
3. **IDs y totales.** §20.1 suma 520 (ASM 104, UPS 26) y la trazabilidad pasa a 236 Diseñado, 10 Decisión pendiente y 22
   Upstream, contados de las filas, no copiados: el resumen de la trazabilidad ya decía 230 y 24 cuando las filas eran
   231 y 23. Pruebas: la comprobación de la Tarea 1 y el conteo del Paso 1 de la Tarea 9.
4. **Fase de cada pieza.** Nada de lo que este plan agrega a una ficha de F2 o F3 entra antes de su fase:
   `engagement_question.referred_at` lleva «Desde F4» en su descripción y ningún texto nuevo de una fila de F3 dice
   «desde F…» antes de su marca de fase (`design_docs.FROM_PHASE` toma la primera). Prueba:
   `test_totales_de_las_fichas`.

## Decisiones de este plan frente a la especificación y los documentos

| # | Hallazgo | Decisión |
|---|---|---|
| 1 | La especificación pide que §20.4 «consolide» los IDs nuevos, pero §20.4 lista IDs retirados y ninguno de F4 retira otro | §20.4 no gana filas; sus «Observaciones» suman que ASM-098 amplía ASM-090 y ASM-095 reemplaza la respuesta del checkpoint «sin restringir en el MVP» (Tarea 1) |
| 2 | La especificación no nombra la fuente de wasichai-notifications de `RiskNoticeListener` ni de `WarningNoticeListener` (F3 usa `sgspe.approvals` y `sgspe.deadlines`) | `sgspe.risks` y `sgspe.warnings`; `approval_pending` de una queja grave sigue en `sgspe.approvals` y `qcs_confidential_overdue` en `sgspe.deadlines` (Tarea 2, «Avisos») |
| 3 | El resumen de estados de la trazabilidad no coincide con sus filas (dice 230 Diseñado y 24 Upstream; las filas son 231 y 23) | Los totales se recalculan de las filas después de los cambios: 236, 10 y 22 (Tarea 7) |
| 4 | `ux/screens.md` usa S36 para la queja y para la consulta y la solicitud; faltan fichas de menciones, señales, reglas e impactos | Los ids siguientes libres: S57 (consulta y solicitud), S58 (menciones), S59 (señales), S60 (reglas) y S61 (impactos); se actualizan las referencias de `screens.md` y `ux/README.md` (Tarea 6) |
| 5 | La especificación nombra la sección «Tipos de regla de JOB-04» sin decir dónde va | Subsección de «Trabajos programados», después de la tabla de trabajos, con el paso a paso de JOB-04 de la especificación §3.7 y la tabla de §4 (Tarea 2) |
| 6 | La especificación pide la confidencialidad en `security.md` §9 sin número de subsección | §9.6 «QCS confidenciales: `RecordReadScope` (F4)», que citan los demás documentos (Tarea 3) |
| 7 | RNF-I18N-002 cubre zona, moneda y feriados, y su fila de trazabilidad decía «Upstream (UPS-15)» por los valores por defecto | Pasa a «Diseñado», como pide la especificación (C7), con `BusinessDaysTest` y `QcsDueDatesApiTest`; la fila de UPS-15 en el análisis sigue citándola, porque la mitigación de los valores por defecto no cambia (Tarea 7) |
| 8 | `deliverables.md` y `api.md` debían «ganar el contrato» de las dos exportaciones | Las hojas y columnas por nivel van una sola vez, en `deliverables.md` §10; `api.md` §4.13 lleva permiso, cuerpo, filtros, topes, errores y `export_log`, y remite a §10 (Tarea 5) |
| 9 | La especificación decide `400 FIELD_REQUIRED` para las reglas de F4, mientras que el `FIELD_REQUIRED` de F2 y F3 de la API genérica está en la fila `409` de `api.md` §3.3 | Se documenta como dice la especificación, en la fila `400` con «Desde F4»; la fila `409` no cambia (Tarea 5) |
| 10 | `ux/screens.md` usa S38 para el registro y matriz de riesgos (con S39) y para el tablero de riesgos, y `ux-architecture.md` §9 llama S38 al tablero | Decisión del usuario (2026-10-09): S38 sigue siendo el registro y matriz; el tablero de riesgos pasa a S62, el siguiente id libre tras S57 a S61. Cambian `screens.md` (la ficha y sus referencias), `ux/README.md` y el inventario de `ux-architecture.md` §9, que suma S57 a S62. `dashboards.md`, la trazabilidad y el roadmap no citan pantallas por id; la entrada de 2026-10-07 de `HISTORY.md` queda como se escribió (Tareas 6 y 8) |
| 11 | La especificación pone la confidencialidad de las QCS en `security.md` §9 sin un ADR, y es una decisión de arquitectura que reabre una respuesta del checkpoint | Decisión del usuario (2026-10-09): ADR propio, ADR-0028 «QCS confidenciales con `RecordReadScope`» (MADR, aceptado), con `ConfidentialAttentionScope`, `READ_CONFIDENTIAL` y la excepción `ConfidentialRelease` de `RequestClosureListener` (decisión 4 del plan 4.2a, aceptada por el usuario); `docs/adr/README.md` lo indexa y §9.6, §15 de `security.md` y la fila de RF-GRV-001 de la trazabilidad lo citan (Tareas 3 y 7) |

Huecos y contradicciones que cierra cada tarea: C1 (Tareas 2, 3, 4 y 7), C2 (7), C3 (7), C4 (2), C5 (2), C6 y C10 (1, 2
y 4), C7 (4 y 7), C8 (2), C9 (2), C11 (6), C12 (5), C13 (2 y 5), C14 (6), C15 (2 y 4), C16 (2 y 7) y C17 (7). C18
(tiempo del túnel) no es de documentos: lo recogen los riesgos del roadmap (Tarea 7).

---

## Mapa de archivos

| Ruta | Qué cambia | Tarea |
|---|---|---|
| `docs/requirements-analysis.md` | §10 (reglas sin CAMBIO DE POSTURA; documentos de QCS tras UPS-23), §18.2 (UPS-01, UPS-25, UPS-26), §19 (ASM-095 a ASM-104), §20.1 y §20.4 | 1 |
| `docs/workflows.md` | Reglas de SGSPE, transiciones de comando (`assess`), WF-E, WF-F, WF-J, WF-M, automatizaciones y tipos de documento, trabajos y «Tipos de regla de JOB-04», listeners, avisos, destinatarios, plazos y pruebas | 2 |
| `model/test_design_docs.py` | Los tres tipos de documento de QCS y AUT-06 a AUT-08 tras UPS-23 | 2 |
| `docs/security.md` | §5.3, §7.6, §9.6 (nueva), §11.3, §11.4, §14 y §15 | 3 |
| `docs/adr/0028-qcs-confidenciales-con-recordreadscope.md` (nuevo), `docs/adr/README.md` | ADR-0028 y su fila del índice | 3 |
| `docs/gis.md`, `docs/glossary.md` | LYR-20; «QCS confidencial» y «Día hábil» | 3 |
| `docs/database-model.md` | `project`, `grievance`, `inquiry`, `stakeholder_request`, `engagement_question`, `social_risk` (`review_every_days`), `early_warning_rule` y `early_warning` | 4 |
| `docs/domain-model.md`, `docs/architecture.md` | §6.12, §7.3, §7.4, §7.5, §8.1.1 y §8.2; §5.2, §8.2, §10.2 y §13 | 4 |
| `docs/api.md`, `docs/deliverables.md` | §3.3, §4.4, §4.6, §4.7, §4.9, §4.11 y §4.13; §10 | 5 |
| `docs/dashboards.md`, `docs/ux/screens.md`, `docs/ux/README.md`, `docs/ux/ux-architecture.md` | §3.3, §4.3, §4.5 y §8; §8, §9, §12, §14 y §16 de las pantallas, con S57 a S61 y el tablero de riesgos en S62; el inventario de §9 | 6 |
| `docs/roadmap.md`, `docs/requirements-traceability.md` | F3 «Estado», fila F4, brechas por fase y respuestas del checkpoint; estados y totales | 7 |
| `docs/HISTORY.md` | Entrada del plan 4.0 | 8 |

## Interfaces con los otros planes de F4

Lo que este plan deja para los siguientes (todos parten de `main` con 4.0 integrado):

- **4.1 (modelo).** `design_docs.automations()` devuelve AUT-06, AUT-07 y AUT-08 con `phase` `F4` y `after` `UPS-23`, y
  `document_types()` seis filas (las tres nuevas con `after` `UPS-23`): con `PHASE = "F4"`, la prueba
  `test_las_automatizaciones_son_las_de_workflows_md_hasta_la_fase_salvo_las_que_esperan_una_brecha` debe aceptar en
  `waiting` también `AUT-06`, `AUT-07` y `AUT-08`. Las fichas traen `READ_CONFIDENTIAL` en las acciones de las tres QCS,
  `early_warning.generator_key` (TEXT, único) y `engagement_question.referred_at` (derivado, «Desde F4»); la matriz de
  `security.md` §7.6 da `READ_CONFIDENTIAL` a `GRIEVANCE_MANAGER`. `workflow_transitions("E", "stakeholder_request")`
  devuelve `SOCIAL_INVESTMENT_MANAGER` en `accept` y `close_accepted` (con «desde F5» en la celda): el filtro por fase
  de `test_model.py` lo deja fuera de `stakeholder_request.json` en F4. `workflows.md` declara `assess` de WF-F como
  transición de comando, que `WorkflowMatrixApiTest` difiere.
- **4.2a (QCS).** Los códigos de error de `api.md` §3.3 y §4.4 a §4.6, el contrato del reporte unificado, la
  confidencialidad de `security.md` §9.6 y ADR-0028 (que ya registra `ConfidentialRelease`), los avisos y destinatarios de `workflows.md` (con las fuentes `sgspe.risks` y
  `sgspe.warnings`, decisión 2), el calendario de `architecture.md` §10.2 y el contenido de `attention-register` en
  `deliverables.md` §10.
- **4.2b (riesgos y alertas).** «Tipos de regla de JOB-04» en `workflows.md`, el contrato de revisiones de `api.md`
  §4.7, las notas de `early_warning_rule` y `early_warning` en `database-model.md` §14 y el contenido de
  `risk-register`.
- **4.3 (portal).** `ux/screens.md` §8 y §9 con S57 a S61, el tablero de riesgos como S62 (§12; S38 es el registro y
  matriz), «Registro reservado», el interruptor de confidencialidad de S37 y «Subir cargo firmado» y «Subir carta
  firmada».

## Orden y paralelismo

Cada tarea del grupo 1 puede hacerse en su propio worktree cuando la Tarea 1 está integrada en
`docs/fase-4-documentos`: tocan archivos distintos y ningún bloque «reemplazar» de una depende del texto que deja otra
(se comprobó aplicando cada una sola sobre la Tarea 1, con las pruebas del modelo y la trazabilidad en verde).

| Grupo | Tareas | Necesita | Por qué juntas |
|---|---|---|---|
| 0 | 1 | La rama creada | Define los IDs que citan las demás |
| 1 | 2, 3, 4, 5, 6, 7 | La Tarea 1 integrada | Archivos disjuntos: `workflows.md` y `test_design_docs.py`; `security.md`, `gis.md`, `glossary.md` y `adr/`; `database-model.md`, `domain-model.md` y `architecture.md`; `api.md` y `deliverables.md`; `dashboards.md` y `ux/`; `roadmap.md` y la trazabilidad |
| 2 | 8, después 9 | Todas las anteriores | La historia resume lo integrado; la 9 verifica, hace push y abre el PR |

---

### Tarea 1: IDs de F4 en el análisis

Los demás documentos citan los IDs nuevos, así que el análisis va primero: §10 quita CAMBIO DE POSTURA y marca el cargo
y la carta tras UPS-23, §18.2 suma UPS-25 y UPS-26 (y la adopción de `RecordReadScope` en UPS-01), §19 suma ASM-095 a
ASM-104 y §20.1 pasa a 520 IDs.

**Archivos:**
- Modificar: `docs/requirements-analysis.md` (§10, §18.2, §19, §20.1, §20.4)
- Probar: `tools/check_traceability.py`, `model/test_design_docs.py` (`catalogs()` lee §10)

**Interfaces:**
- Consume: la especificación §6 («IDs nuevos»).
- Produce: ASM-095 (confidencialidad), ASM-096 (días hábiles), ASM-097 (reglas de alerta), ASM-098 (documentos tras
  UPS-23), ASM-099 (revisiones de riesgo), ASM-100 (plazos de QCS), ASM-101 (exportaciones), ASM-102 (avisos de QCS
  confidenciales y de queja grave), ASM-103 (incidentes), ASM-104 (registro por el equipo social), UPS-25 y UPS-26.

- [ ] **Paso 1: La comprobación falla**

```bash
python3 - <<'PY'
checks = [
    ('docs/requirements-analysis.md', '| ASM-095 |', True),
    ('docs/requirements-analysis.md', '| ASM-104 |', True),
    ('docs/requirements-analysis.md', '| UPS-25 |', True),
    ('docs/requirements-analysis.md', '| UPS-26 |', True),
    ('docs/requirements-analysis.md', '| Total | 520 |', True),
    ('docs/requirements-analysis.md', 'NIVEL DE RIESGO · CAMBIO DE POSTURA', False),
]
bad = [f"{p}: {'falta' if want else 'sobra'} {s!r}" for p, s, want in checks
       if (s in open(p, encoding='utf-8').read()) != want]
print('\n'.join(bad) or 'ok')
raise SystemExit(1 if bad else 0)
PY
```

Esperado: salida 1 con las seis líneas (`falta '| ASM-095 |'`, …, `sobra 'NIVEL DE RIESGO · CAMBIO DE POSTURA'`).

- [ ] **Paso 2: Los catálogos de §10: sin CAMBIO DE POSTURA y los documentos de QCS tras UPS-23**

En `docs/requirements-analysis.md`, reemplazar:

```markdown
| Tipo de regla de alerta | CONTEO DE SEÑALES · CONTEO DE QCS · COMPROMISOS VENCIDOS · MENCIONES NEGATIVAS · INCIDENTES · NIVEL DE RIESGO · CAMBIO DE POSTURA (propuestos) | `early_warning_rule` | [PRM-01 §5] [DOC-01 §9.3.2.2] |
```

por:

```markdown
| Tipo de regla de alerta | CONTEO DE SEÑALES · CONTEO DE QCS · COMPROMISOS VENCIDOS · MENCIONES NEGATIVAS · INCIDENTES · NIVEL DE RIESGO (propuestos; la semántica de cada uno está en `workflows.md`, «Tipos de regla de JOB-04»; CAMBIO DE POSTURA queda fuera del MVP porque la alerta publicaría la postura, un dato restringido, ASM-097) | `early_warning_rule` | [PRM-01 §5] [DOC-01 §9.3.2.2] |
```

En `docs/requirements-analysis.md`, reemplazar:

```markdown
| Tipo de documento administrativo | ACTA DE ENTREGA · ACTA DE RECEPCION · MINUTA DE REUNION · LISTA DE ASISTENTES · CONSTANCIA · CARGO DE QUEJA · CARTA DE RESPUESTA · FICHA DE GRUPO DE INTERES · FICHA DE LOCALIDAD (propuestos los cinco últimos) | documentos emitidos de wasichai-documents | [DOC-01 §9.3.1.2, Tabla 9.5] [DOC-02 p.4] |
```

por:

```markdown
| Tipo de documento administrativo | ACTA DE ENTREGA · ACTA DE RECEPCION · MINUTA DE REUNION · LISTA DE ASISTENTES · CONSTANCIA · CARGO DE QUEJA (tras UPS-23) · CARTA DE RESPUESTA (tras UPS-23) · FICHA DE GRUPO DE INTERES · FICHA DE LOCALIDAD (propuestos los cinco últimos; el cargo y la carta se suben firmados como evidencia mientras UPS-23 siga abierta, ASM-098) | documentos emitidos de wasichai-documents | [DOC-01 §9.3.1.2, Tabla 9.5] [DOC-02 p.4] |
```

- [ ] **Paso 3: Las brechas UPS-25 y UPS-26 y el alcance de las QCS confidenciales en UPS-01**

En `docs/requirements-analysis.md`, reemplazar:

```markdown
| UPS-01 | Alcance de lectura por proyecto, territorio o región en todas las rutas de lectura (listas, detalle, relacionados, historial, features GIS, agente) y su aplicación en escritura | Sin ella no se cumple la autorización por ámbito del prompt | Escritura acotada por asignación con una guarda y lectura a nivel de tenant: opción A de CON-041, recomendada para el MVP | Antes de la producción con varios proyectos (crítica) | RQ-SEC-006, RQ-GIS-017 |
```

por:

```markdown
| UPS-01 | Alcance de lectura por proyecto, territorio o región en todas las rutas de lectura (listas, detalle, relacionados, historial, features GIS, agente) y su aplicación en escritura | Sin ella no se cumple la autorización por ámbito del prompt | Escritura acotada por asignación con una guarda y lectura a nivel de tenant: opción A de CON-041, recomendada para el MVP. Desde F4, el SPI `RecordReadScope` de wasichai 0.6.0 (WAS-ADR-0048) acota la lectura solo de las QCS confidenciales (ASM-095); el alcance por proyecto sigue en la opción A | Antes de la producción con varios proyectos (crítica) | RQ-SEC-006, RQ-GIS-017 |
```

En `docs/requirements-analysis.md`, reemplazar:

```markdown
| UPS-24 | Un enganche de lectura para el descriptor de un campo `FILE`, que deje ocultar o reemplazar `name` según el registro que lo lee (por ejemplo, su clasificación) o un permiso de campo: wasichai-files 0.6.0 devuelve `{ id, name, contentType, size, sha256 }` a todo rol con READ sobre el objeto, en el registro, su historial y la lista | El nombre original del archivo de una evidencia CONFIDENCIAL o de DATOS PERSONALES puede llevar datos personales y lo lee todo rol con READ sobre `evidence`, aunque la descarga exija `DOWNLOAD_RESTRICTED` (RQ-SEC-010) | El portal sube toda evidencia restringida con un nombre neutro (`evidencia-<aaaammdd-hhmmss>.<ext>`) y no muestra el nombre de una restringida a quien no la descarga; el nombre de una evidencia subida antes o por la API queda visible a sus lectores, un riesgo aceptado (ASM-093, `security.md` §14) | F3 | RF-DOC-003, RQ-SEC-010 |
```

por:

```markdown
| UPS-24 | Un enganche de lectura para el descriptor de un campo `FILE`, que deje ocultar o reemplazar `name` según el registro que lo lee (por ejemplo, su clasificación) o un permiso de campo: wasichai-files 0.6.0 devuelve `{ id, name, contentType, size, sha256 }` a todo rol con READ sobre el objeto, en el registro, su historial y la lista | El nombre original del archivo de una evidencia CONFIDENCIAL o de DATOS PERSONALES puede llevar datos personales y lo lee todo rol con READ sobre `evidence`, aunque la descarga exija `DOWNLOAD_RESTRICTED` (RQ-SEC-010) | El portal sube toda evidencia restringida con un nombre neutro (`evidencia-<aaaammdd-hhmmss>.<ext>`) y no muestra el nombre de una restringida a quien no la descarga; el nombre de una evidencia subida antes o por la API queda visible a sus lectores, un riesgo aceptado (ASM-093, `security.md` §14) | F3 | RF-DOC-003, RQ-SEC-010 |
| UPS-25 | Candidata: un alta pública acotada por tenant y objeto, sin usuario, con su cadena de seguridad, captcha y límite de tasa de la plataforma, para que alguien sin cuenta registre una QCS en un proyecto. Hoy la cadena de seguridad de wasichai solo abre el login, el token y la salud, y toda escritura HTTP lleva un usuario o una cuenta de servicio | Quien no se acerca al equipo social no puede plantear una QCS | Solo el equipo social registra QCS, por el canal en que llegaron (ASM-104); sin issue hasta la aprobación del usuario | Después del MVP | RF-GRV-001 |
| UPS-26 | Candidata: avisos de wasichai-notifications a destinatarios que no son usuarios (correo, SMS o WhatsApp). Hoy `DeliveryChannel` (WAS-ADR-0060) entrega solo a usuarios de la organización | Quien plantea una QCS no recibe por un canal electrónico el acuse ni la respuesta | El cargo y la carta se entregan en papel y, firmados, se suben como evidencia de la QCS (ASM-098); sin issue hasta la aprobación del usuario | Después del MVP | RF-GRV-001, RF-NTF-001 |
```

- [ ] **Paso 4: Los supuestos ASM-095 a ASM-104**

En `docs/requirements-analysis.md`, reemplazar:

```markdown
| ASM-094 | Una tarea vencida sin asignado y sin un registro vinculado que tenga transición de referencia (solo su `project`) no avisa a nadie en los niveles 0 y 1: el primer aviso es el del nivel 2, a los roles de `approve_senior` de WF-D | El nivel 0 va al asignado y el nivel 1 a los roles de la transición de referencia del registro vinculado (ASM-030); sin ninguno de los dos no hay a quién avisar sin fijar un rol en el código (RQ-SEC-005) | Avisar en el nivel 1 a los roles de una transición fija de WF-N o a quien creó la tarea | RF-WFL-004, RF-NTF-001, ASM-030, ASM-070, `docs/workflows.md` («Destinatarios por rol») |
```

por:

```markdown
| ASM-094 | Una tarea vencida sin asignado y sin un registro vinculado que tenga transición de referencia (solo su `project`) no avisa a nadie en los niveles 0 y 1: el primer aviso es el del nivel 2, a los roles de `approve_senior` de WF-D | El nivel 0 va al asignado y el nivel 1 a los roles de la transición de referencia del registro vinculado (ASM-030); sin ninguno de los dos no hay a quién avisar sin fijar un rol en el código (RQ-SEC-005) | Avisar en el nivel 1 a los roles de una transición fija de WF-N o a quien creó la tarea | RF-WFL-004, RF-NTF-001, ASM-030, ASM-070, `docs/workflows.md` («Destinatarios por rol») |
| ASM-095 | Una QCS con `confidential` verdadero la leen solo `ADMIN`, quien tiene la acción declarada `READ_CONFIDENTIAL` sobre el objeto (hoy `GRIEVANCE_MANAGER`), su responsable, quien la recibió y, si es una queja ALTA o CRITICA, quien tiene `APPROVE` sobre `grievance` (`APPROVER`, que debe resolverla). Lo aplica `ConfidentialAttentionScope`, un `RecordReadScope` de `sgspe.scope` que wasichai usa en listas, detalle, relacionados, historial, auditoría, transiciones, features y agente, y que falla cerrado. Quien registra una QCS confidencial queda como quien la recibió, salvo que tenga `READ_CONFIDENTIAL`; quitar la marca o cambiar su responsable o quien la recibió exige esa acción. Los trabajos leen sin el alcance y filtran lo que avisan; los tableros, el reporte y las exportaciones leen como quien llama y dicen `confidentialHidden` sin el número de excluidas | Decisión del usuario del 2026-10-09, que reabre la respuesta del checkpoint «sin restringir en el MVP»: wasichai 0.6.0 ofrece ahora `RecordReadScope` (WAS-ADR-0048), y `confidential` no restringía la lectura | Quitar el alcance y volver al riesgo residual de `security.md` §11.3, o generalizarlo con la opción B de CON-041 | RF-GRV-001, RQ-SEC-004, RQ-SEC-010, CON-041, UPS-01, `docs/security.md` §9.6 |
| ASM-096 | Un día es hábil si es de lunes a viernes y no es un feriado nacional de la lista de su año en `sgspe.calendar.holidays`, una por despliegue (no por organización); si falta el año, ese año cuenta de lunes a viernes y se advierte en el log una vez por año y proceso, y al arrancar si falta el año en curso. El acuse de una queja vence el n-ésimo día hábil posterior a la fecha de recepción en Lima, que no cuenta; los plazos se calculan al recibir y no se recalculan si después cambia la lista o un parámetro | ASM-005 mide el acuse en días hábiles y wasichai no tiene calendario; GAP-005 no fija feriados | Un catálogo de feriados por organización (ASM-017), o días calendario para todo | RF-GRV-006, RNF-I18N-002, ASM-005, ASM-017 |
| ASM-097 | JOB-04 evalúa seis tipos de regla (CONTEO DE SEÑALES, CONTEO DE QCS, COMPROMISOS VENCIDOS, MENCIONES NEGATIVAS, INCIDENTES y NIVEL DE RIESGO) con la semántica de `workflows.md` («Tipos de regla de JOB-04»): `threshold` entero, ventana de `window_days` días de Lima (7 si está vacía), disparo con el valor medido mayor o igual que el umbral; una regla del proyecto reemplaza a la del tenant del mismo tipo; `window_days` es también el silencio tras una alerta de la misma regla y proyecto; una regla del tenant crea una alerta por proyecto. CAMBIO DE POSTURA queda fuera del MVP | Las fuentes no definen qué cuenta cada tipo; CAMBIO DE POSTURA leería `stance`, restringido (GAP-024), y la alerta lo publicaría a todos los lectores de `early_warning` | Otra semántica por tipo, parámetros propios por regla, o CAMBIO DE POSTURA cuando la alerta pueda llevar campos restringidos o un alcance propio | RF-EWS-004, RN-EWS-001, GAP-024, ASM-006 |
| ASM-098 | «Cargo de queja» y «Carta de respuesta» no se emiten mientras UPS-23 siga abierta, porque las tres QCS restringen `person`: AUT-06, AUT-07 y AUT-08 esperan; el cargo y la carta se imprimen fuera de SGSPE, se entregan en papel y, firmados, se suben como evidencia FORMULARIO (cargo) o CARTA (carta) vinculada a la QCS. Sin avisos a quien plantea en el MVP (UPS-26). Amplía ASM-090 | Un documento emitido congela el registro entero sin permisos de campo (UPS-23) | Emitirlos cuando wasichai-documents congele solo lo que la plantilla nombra | RQ-REP-029, RF-GRV-002, RF-GRV-003, RF-GRV-004, UPS-23, UPS-26, ASM-090 |
| ASM-099 | Una revisión de riesgo (`risk_assessment`) es `appendOnly` y `apiOnly` y se corrige con otra; `assess` es una transición de comando que aplica el endpoint de revisiones en la primera revisión, que no puede ser residual; las revisiones de un tipo, residual o no, van en orden de fecha; la cadencia cuenta días desde la última revisión (`next_review_date` = fecha de la revisión + `review_every_days` o `risk_review_days`); `review_status` pasa a POR REVISAR el día anterior a `next_review_date` y a REVISION VENCIDA el día siguiente; JOB-01 crea la tarea `risk_review:<id>:<next_review_date>`, asignada al responsable, con esa fecha | RN-RSK-001 pide la revisión semanal sin fijar cómo se cuenta ni quién la hace | Cadencia por semanas de calendario, o revisiones editables por un plazo | RN-RSK-001, RN-RSK-002, RF-RSK-001, RF-RSK-002 |
| ASM-100 | Los plazos de las QCS se miden desde la fecha de recepción en Lima: el acuse en días hábiles (ASM-096), la respuesta y la resolución en días calendario; no se pausan en `in_investigation`; las solicitudes no tienen plazo en el MVP, y su `response_due_date` y su `deadline_status` quedan vacíos; `grievance.response_due_date` queda siempre vacío. POR VENCER empieza `due_soon_days` antes del plazo, pero nunca más de la mitad de los días calendario del plazo, redondeada hacia abajo | ASM-005 fija los plazos sin decir cómo se cuentan; con 7 días de aviso, un acuse de 5 días hábiles nacería POR VENCER | Pausar el plazo en la investigación o dar un plazo a las solicitudes (GAP-005) | RF-GRV-006, RN-WFL-001, RQ-DSH-022, ASM-005, ASM-006 |
| ASM-101 | `attention-register` exige READ y `EXPORT` sobre las tres QCS y `risk-register`, sobre `social_risk`; en EXTERNO no salen las QCS confidenciales (aunque quien exporta las lea), el texto libre, la persona, quien recibió ni el responsable; en PUBLICO salen conteos sobre la grilla completa con celdas pequeñas, y «Notas» dice si incluyen las confidenciales. El contenido de cada nivel está en `deliverables.md` §10 | Las fuentes no fijan el contenido de estas salidas; un registro unificado con un tipo de menos engañaría | Otro reparto de columnas por nivel | RQ-REP-022, RQ-REP-014, RF-GRV-005, ASM-037, ASM-088 |
| ASM-102 | Un aviso sobre una QCS confidencial (`qcs_deadline`, `approval_pending` de una queja grave y `task_overdue` de una tarea vinculada a ella) solo llega a quien la vería: su responsable, quien la recibió, los asignados cuyo rol tiene `READ_CONFIDENTIAL` sobre el objeto y, en una queja ALTA o CRITICA, los que tienen `APPROVE` sobre `grievance`. En el nivel 2, los roles de `approve_senior` de WF-D que no la ven reciben `qcs_confidential_overdue`, un aviso genérico sin código, título, tipo, fecha ni enlace. Una queja ALTA o CRITICA en `in_action` avisa a los roles de `resolve_high_severity` | El escalamiento no puede quedarse sin aviso, y el aviso con datos le mostraría la QCS a quien no la lee | Sin aviso de nivel 2 para quien no la ve, o un rol de escalamiento propio de las QCS | RF-NTF-001, RF-WFL-004, RN-GRV-002, ASM-030, ASM-070, ASM-095 |
| ASM-103 | Los incidentes no tienen severidad, motivo obligatorio (`requiresReason`) ni escalamiento en el MVP: escalan las QCS y los riesgos que originan | DOC-02 relata incidentes sin escala de gravedad, y WF-M no tiene plazos | Agregar severidad y plazos a `incident` | RF-EWS-001 |
| ASM-104 | Solo el equipo social (`COMMUNITY_RELATIONS_OFFICER` y `GRIEVANCE_MANAGER`) registra QCS, por el canal en que llegaron (`CONTRATISTA` incluido, ASM-043); no hay canal público ni recepción en línea en el MVP | Un canal público necesita una cadena de seguridad propia, captcha y límite de tasa que la plataforma no ofrece (UPS-25) | Un formulario público cuando exista UPS-25 | RF-GRV-001, ASM-043, UPS-25 |
```

- [ ] **Paso 5: Los totales de §20.1 y la nota de §20.4**

En `docs/requirements-analysis.md`, reemplazar:

```markdown
| `UPS` | 24 |
| `ASM` | 94 |
| Total | 508 |
```

por:

```markdown
| `UPS` | 26 |
| `ASM` | 104 |
| Total | 520 |
```

En `docs/requirements-analysis.md`, reemplazar:

```markdown
- Las nueve decisiones del usuario (USR-01 a USR-09) están desplegadas según §6.0; USR-04 y USR-08 se cumplen en el
```

por:

```markdown
- Los IDs de F4 (ASM-095 a ASM-104, UPS-25 y UPS-26) no retiran ninguno: ASM-098 amplía ASM-090, y ASM-095 reemplaza
  la respuesta del checkpoint «sin restringir en el MVP» sobre la protección de quien plantea una QCS.
- Las nueve decisiones del usuario (USR-01 a USR-09) están desplegadas según §6.0; USR-04 y USR-08 se cumplen en el
```

- [ ] **Paso 6: Verificar**

Repetir la comprobación del Paso 1 y correr las compuertas de «Global Constraints».

Además:

```bash
grep -c '^| ASM-[0-9]\{3\} ' docs/requirements-analysis.md; grep -c '^| UPS-[0-9]\{2\} ' docs/requirements-analysis.md
```

Esperado: `ok` en la comprobación, las compuertas en verde, `104` y `26`.

- [ ] **Paso 7: Commit**

```bash
git add docs/requirements-analysis.md
git commit -F - <<'MSG'
docs: supuestos y brechas de la fase 4 en el análisis

ASM-095 a ASM-104 fijan la confidencialidad de las QCS, los días hábiles, las reglas de alerta, los
documentos tras UPS-23, las revisiones de riesgo, los plazos, las exportaciones, los avisos, los
incidentes y el registro por el equipo social; UPS-25 y UPS-26 quedan como candidatas. §10 quita
CAMBIO DE POSTURA y §20.1 suma 520 IDs.

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_017MAgYcNauGp6cC3ekX1h7U
MSG
```

---

### Tarea 2: `workflows.md` de F4

Cierra C1 en las automatizaciones y los tipos de documento, C4 (origen de cada aviso), C5 (WF-E3 sin
`SOCIAL_INVESTMENT_MANAGER` hasta F5), C6 y C10 (la sección «Tipos de regla de JOB-04» y la fila de JOB-04), C8 (la
tarea de revisión de riesgo en JOB-01), C9 (`assess` de WF-F como transición de comando), C13 (la regla «mismo
proyecto» en las reglas de SGSPE), C15 (plazos sin solicitudes y `grievance.response_due_date` vacío) y C16 (incidentes
sin escalamiento), y agrega los avisos de F4: `approval_pending` de queja grave y `qcs_confidential_overdue`.

**Archivos:**
- Modificar: `model/test_design_docs.py` (`test_automatizaciones_y_tipos_de_documento`)
- Modificar: `docs/workflows.md`
- Probar: `model/test_design_docs.py`, `model/test_model.py`

**Interfaces:**
- Consume: los IDs de la Tarea 1.
- Produce: las tablas que lee `design_docs.py` (automatizaciones con AUT-06 a AUT-08 «F4, tras UPS-23» y tres tipos de
  documento nuevos: `grievance-receipt` `CQ`, `inquiry-answer-letter` `CC`, `request-answer-letter` `CS`); la
  subsección `### Tipos de regla de JOB-04`, que citan las Tareas 4 y 6; las fuentes `sgspe.risks` y `sgspe.warnings`.

- [ ] **Paso 1: La prueba del lector pide los tres tipos de documento de QCS (falla)**

En `model/test_design_docs.py`, reemplazar:

```python
        self.assertEqual((automations["AUT-03"]["phase"], automations["AUT-03"]["after"]), ("F3", "UPS-23"))
        self.assertEqual(automations["AUT-11"]["trigger"], "RECORD_UPDATED")
```

por:

```python
        self.assertEqual((automations["AUT-03"]["phase"], automations["AUT-03"]["after"]), ("F3", "UPS-23"))
        # ASM-098: el cargo de queja y las cartas de respuesta esperan a UPS-23, porque las QCS restringen person
        for aut in ("AUT-06", "AUT-07", "AUT-08"):
            self.assertEqual((automations[aut]["phase"], automations[aut]["after"]), ("F4", "UPS-23"), aut)
        self.assertEqual(automations["AUT-11"]["trigger"], "RECORD_UPDATED")
```

En `model/test_design_docs.py`, reemplazar:

```python
                         [("delivery-certificate", "AE", "delivery", "F3", "UPS-23"),
                          ("meeting-minutes", "MR", "engagement", "F3", None),
                          ("attendance-list", "LA", "engagement", "F3", None)])
```

por:

```python
                         [("delivery-certificate", "AE", "delivery", "F3", "UPS-23"),
                          ("meeting-minutes", "MR", "engagement", "F3", None),
                          ("attendance-list", "LA", "engagement", "F3", None),
                          ("grievance-receipt", "CQ", "grievance", "F4", "UPS-23"),
                          ("inquiry-answer-letter", "CC", "inquiry", "F4", "UPS-23"),
                          ("request-answer-letter", "CS", "stakeholder_request", "F4", "UPS-23")])
```

- [ ] **Paso 2: La prueba y la comprobación fallan**

```bash
(cd model && python3 -m unittest test_design_docs 2>&1 | tail -1)
python3 - <<'PY'
checks = [
    ('docs/workflows.md', '### Tipos de regla de JOB-04', True),
    ('docs/workflows.md', '| `assess` | WF-F |', True),
    ('docs/workflows.md', '`risk_review:<id>:<next_review_date>`', True),
    ('docs/workflows.md', '| `qcs_confidential_overdue` |', True),
    ('docs/workflows.md', '| Cargo de queja | `grievance-receipt` | `CQ` |', True),
    ('docs/workflows.md', 'Un listener de `sgspe.notification` (F4)', False),
]
bad = [f"{p}: {'falta' if want else 'sobra'} {s!r}" for p, s, want in checks
       if (s in open(p, encoding='utf-8').read()) != want]
print('\n'.join(bad) or 'ok')
raise SystemExit(1 if bad else 0)
PY
```

Esperado: `FAILED (failures=1)` en `test_automatizaciones_y_tipos_de_documento` (el lector todavía no ve los tipos de
documento de QCS) y salida 1 con las seis líneas de la comprobación.

- [ ] **Paso 3: Reglas de SGSPE y transiciones de comando**

En `docs/workflows.md`, reemplazar:

```markdown
6. **Cada efecto por su vía.** Cambios simples sin lógica: automatizaciones de metadata. Avisos:
   `ApprovalNoticeListener`, `DeadlineNotices` y, desde F4, los listeners de avisos de `sgspe.notification`, nunca las
   entidades (RN-NTF-001). Efectos por tiempo: trabajos. Efectos sobre varios registros: endpoints o listeners de
   transición.
```

por:

```markdown
6. **Cada efecto por su vía.** Cambios simples sin lógica: automatizaciones de metadata. Avisos:
   `ApprovalNoticeListener`, `DeadlineNotices` y, desde F4, `RiskNoticeListener` y `WarningNoticeListener` de
   `sgspe.notification`, nunca las entidades (RN-NTF-001). Efectos por tiempo: trabajos. Efectos sobre varios registros:
   endpoints o listeners de transición.
```

En `docs/workflows.md`, reemplazar:

```markdown
10. **Alcance por proyecto y registros propios.** Una transición es una escritura, así que `ProjectScopeGuard`
    (`sgspe.scope`) la limita a quien está asignado al proyecto (RQ-SEC-006); el alcance de lectura espera UPS-01 (CON-041).
    Ningún rol usa `own_records_only` (ASM-069). `FIELD_AGENT` puede agregar participantes u observaciones a cualquier
    interacción o visita que pueda leer, y solo cambia, borra o mueve lo que creó (RQ-SEC-008):
```

por:

```markdown
10. **Alcance por proyecto y registros propios.** Una transición es una escritura, así que `ProjectScopeGuard`
    (`sgspe.scope`) la limita a quien está asignado al proyecto (RQ-SEC-006); el alcance de lectura por proyecto espera
    UPS-01 (CON-041). Desde F4, las QCS confidenciales tienen su propio alcance de lectura
    (`ConfidentialAttentionScope`, ASM-095, `security.md` §9.6), y `ProjectLinkGuard` (`sgspe.scope`) rechaza con `409
    PROJECT_MISMATCH` toda relación de un objeto de F4 hacia un registro de otro proyecto. Ningún rol usa
    `own_records_only` (ASM-069). `FIELD_AGENT` puede agregar participantes u observaciones a cualquier interacción o
    visita que pueda leer, y solo cambia, borra o mueve lo que creó (RQ-SEC-008):
```

En `docs/workflows.md`, reemplazar:

```markdown
| `close_accepted` | WF-E3 | `RequestClosureListener`, al cerrar el compromiso o, desde F5, el aporte | El cierre de la solicitud aceptada | RN-GRV-001, ASM-068 |
```

por:

```markdown
| `close_accepted` | WF-E3 | `RequestClosureListener`, al cerrar el compromiso o, desde F5, el aporte | El cierre de la solicitud aceptada | RN-GRV-001, ASM-068 |
| `assess` | WF-F | `POST /api/sgspe/social-risks/{id}/assessments`, en la primera revisión | La `risk_assessment`, los valores vigentes y `next_review_date` del riesgo | RF-RSK-001, ASM-099 |
```

En `docs/workflows.md`, reemplazar:

```markdown
Otras transiciones las aplica normalmente un endpoint:

- `assess` de WF-F la aplica el endpoint de revisiones en la primera revisión, y `RiskWriteGuard` la admite por cualquier vía
  si se cumplen sus condiciones;
- `mark_duplicate` de `commitment_candidate` la aplica `POST /api/sgspe/commitment-candidates/{id}/duplicate`, que escribe
  `duplicate_of`. Como el objeto es `apiOnly`, ninguna otra vía puede informar ese campo, y `CommitmentWriteGuard` rechaza
  la transición sin él.
```

por:

```markdown
Otra transición la aplica normalmente un endpoint sin ser de comando: `mark_duplicate` de `commitment_candidate` la
aplica `POST /api/sgspe/commitment-candidates/{id}/duplicate`, que escribe `duplicate_of`. Como el objeto es `apiOnly`,
ninguna otra vía puede informar ese campo, y `CommitmentWriteGuard` rechaza la transición sin él.
```

- [ ] **Paso 4: WF-E: registro, confidencialidad, días hábiles, documentos y roles por fase**

En `docs/workflows.md`, reemplazar:

```markdown
- Una QCS anónima no lleva `person` ni `stakeholder` (`AttentionWriteGuard`, RF-GRV-001, GAP-005).
```

por:

```markdown
- Una QCS anónima no lleva `person` ni `stakeholder` (`AttentionWriteGuard`, RF-GRV-001, GAP-005).
- **Registro (ASM-104).** Solo el equipo social registra QCS (`COMMUNITY_RELATIONS_OFFICER` y `GRIEVANCE_MANAGER`), por
  el canal en que llegaron; no hay canal público ni avisos a quien plantea en el MVP (UPS-25, UPS-26).
- **Confidencial (ASM-095).** Una QCS con `confidential` verdadero solo la leen `ADMIN`, quien tiene `READ_CONFIDENTIAL`
  sobre el objeto (`GRIEVANCE_MANAGER`), su responsable, quien la recibió y, en una queja ALTA o CRITICA, quien tiene
  `APPROVE` sobre `grievance` (`ConfidentialAttentionScope`, `security.md` §9.6). Quien no la lee tampoco la cambia ni
  la transiciona. `AttentionWriteGuard` exige que el alta de una QCS confidencial lleve `received_by_user_id` igual a
  quien escribe, salvo con `READ_CONFIDENTIAL` (`400 CONFIDENTIAL_RECEIVER_REQUIRED`), y esa acción para quitar la marca
  o cambiar `owner_user_id` o `received_by_user_id` de una confidencial (`403 ACTION_REQUIRED`).
- **Días hábiles (ASM-096).** El acuse de una queja cuenta días hábiles con `BusinessDays` (`sgspe.common`): lunes a
  viernes menos los feriados nacionales configurados por año; la respuesta y la resolución, días calendario. El día de
  recepción no cuenta.
- **Documentos (ASM-098).** «Cargo de queja» y «Carta de respuesta» esperan a UPS-23 (AUT-06 a AUT-08): mientras tanto
  se imprimen fuera de SGSPE, se entregan en papel y, firmados, se suben como evidencia FORMULARIO (cargo) o CARTA
  (carta) vinculada a la QCS.
```

En `docs/workflows.md`, reemplazar:

```markdown
  terreno; `APPROVER` resuelve las quejas graves (RN-GRV-002).
```

por:

```markdown
  terreno; `APPROVER` resuelve las quejas graves (RN-GRV-002) y recibe `approval_pending` de una queja ALTA o CRITICA en
  `in_action` (ASM-102).
```

En `docs/workflows.md`, reemplazar:

```markdown
| `acknowledge` (Acusar recibo) | `received` | `acknowledged` | `GRIEVANCE_MANAGER`, `COMMUNITY_RELATIONS_OFFICER` | — | Obligatorio (`requiresReason`) | AUT-06: «Cargo de queja» como documento emitido; `deadline_status` pasa a medir `resolution_due_date` | [PRM-01 §5, §8] ACKNOWLEDGED; plazo de ASM-005 |
```

por:

```markdown
| `acknowledge` (Acusar recibo) | `received` | `acknowledged` | `GRIEVANCE_MANAGER`, `COMMUNITY_RELATIONS_OFFICER` | — | Obligatorio (`requiresReason`) | AUT-06 (tras UPS-23): «Cargo de queja» como documento emitido; hasta entonces, el cargo firmado se sube como evidencia FORMULARIO (ASM-098); `deadline_status` pasa a medir `resolution_due_date` | [PRM-01 §5, §8] ACKNOWLEDGED; plazo de ASM-005 |
```

En `docs/workflows.md`, reemplazar:

```markdown
| `start_action` (Atender) | `in_assessment` | `in_action` | `GRIEVANCE_MANAGER` | `AttentionWriteGuard`: `category` y `severity` informadas | Obligatorio (`requiresReason`) | — | [PRM-01 §5, §8] ACTION_REQUIRED y Action |
| `start_action_after_investigation` (Atender) | `in_investigation` | `in_action` | `GRIEVANCE_MANAGER` | — | Obligatorio (`requiresReason`) | — | Ídem |
```

por:

```markdown
| `start_action` (Atender) | `in_assessment` | `in_action` | `GRIEVANCE_MANAGER` | `AttentionWriteGuard`: `category` y `severity` informadas | Obligatorio (`requiresReason`) | `ApprovalNoticeListener`: `approval_pending` a los roles de `resolve_high_severity` si la queja es ALTA o CRITICA y `policy_high_severity_requires_approver` está activo (ASM-102) | [PRM-01 §5, §8] ACTION_REQUIRED y Action |
| `start_action_after_investigation` (Atender) | `in_investigation` | `in_action` | `GRIEVANCE_MANAGER` | `AttentionWriteGuard`: `category` y `severity` informadas | Obligatorio (`requiresReason`) | Como `start_action` | Ídem |
```

En `docs/workflows.md`, reemplazar:

```markdown
| `reject_received` (Rechazar) | `received` | `rejected` | `GRIEVANCE_MANAGER` | `AttentionWriteGuard`: `response` y `responded_at` informados, porque el rechazo se comunica; un duplicado lleva `duplicate_of` | Obligatorio (`requiresReason`) | `closed_date`; con `responded_at`, cuenta como absuelta | [PRM-01 §5] REJECTED |
```

por:

```markdown
| `reject_received` (Rechazar) | `received` | `rejected` | `GRIEVANCE_MANAGER` | `AttentionWriteGuard`: `response` y `responded_at` informados, porque el rechazo se comunica; un duplicado lleva `duplicate_of`, que no puede ser la misma queja (`400 INVALID_LINK`) | Obligatorio (`requiresReason`) | `closed_date`; con `responded_at`, cuenta como absuelta | [PRM-01 §5] REJECTED |
```

En `docs/workflows.md`, reemplazar:

```markdown
Al recibir una queja (alta, no transición) corren `QcsDueDatesListener` (plazos) y AUT-02 (tarea de acuse). El aviso de plazo
próximo a incumplirse y el escalamiento los genera JOB-01.
```

por:

```markdown
Al recibir una queja (alta, no transición) corren `QcsDueDatesListener` (plazos) y AUT-02 (tarea de acuse). La situación
de plazo y las tareas de escalamiento las escribe JOB-01, y los avisos los publica `DeadlineNotices`.
```

En `docs/workflows.md`, reemplazar:

```markdown
| `answer` (Responder) | `received` | `answered` | `GRIEVANCE_MANAGER`, `COMMUNITY_RELATIONS_OFFICER` | `AttentionWriteGuard`: `response` y `responded_at` informados | Obligatorio (`requiresReason`) | AUT-07: «Carta de respuesta» si llegó por CARTA; con `responded_at`, cuenta como absuelta (RQ-DSH-023) | [DOC-01 Tabla 9.5] consultas absueltas |
```

por:

```markdown
| `answer` (Responder) | `received` | `answered` | `GRIEVANCE_MANAGER`, `COMMUNITY_RELATIONS_OFFICER` | `AttentionWriteGuard`: `response` y `responded_at` informados | Obligatorio (`requiresReason`) | AUT-07 (tras UPS-23): «Carta de respuesta» si llegó por CARTA; hasta entonces, la carta firmada se sube como evidencia CARTA (ASM-098); con `responded_at`, cuenta como absuelta (RQ-DSH-023) | [DOC-01 Tabla 9.5] consultas absueltas |
```

En `docs/workflows.md`, reemplazar:

```markdown
  Se revierte cerrando solo por el compromiso, como dice RN-GRV-001, y dejando el resto a mano.
```

por:

```markdown
  Se revierte cerrando solo por el compromiso, como dice RN-GRV-001, y dejando el resto a mano.
- **Fase de cada rol.** `SOCIAL_INVESTMENT_MANAGER` entra en `accept` y `close_accepted` desde F5, cuando recibe
  permisos (`security.md` §7.1), como en WF-N: en F4, `model/workflows/stakeholder_request.json` no lo lleva.
```

En `docs/workflows.md`, reemplazar:

```markdown
| `accept` (Aceptar) | `under_evaluation` | `accepted` | `SOCIAL_MANAGER`, `COMMITMENT_MANAGER`, `SOCIAL_INVESTMENT_MANAGER` | `CommandTransitionGuard`: solo desde el endpoint de aceptación, que crea el compromiso en `draft` (o, desde F5, el aporte en `requested`) y guarda `created_commitment` o `created_contribution`. `AttentionWriteGuard`: `response` y `responded_at` de la solicitud informados, porque la aceptación es la respuesta y el cierre que sigue es automático | Obligatorio (`requiresReason`) | El compromiso nuevo sigue WF-D, con su aprobación; el aporte, WF-P. Con `responded_at`, la solicitud cuenta como absuelta | [DOC-01 §9.3.1.2] medio de solicitud (RF-GRV-004) |
```

por:

```markdown
| `accept` (Aceptar) | `under_evaluation` | `accepted` | `SOCIAL_MANAGER`, `COMMITMENT_MANAGER`, `SOCIAL_INVESTMENT_MANAGER` (desde F5) | `CommandTransitionGuard`: solo desde el endpoint de aceptación, que crea el compromiso en `draft` (o, desde F5, el aporte en `requested`) y guarda `created_commitment` o `created_contribution`. `AttentionWriteGuard`: `response` y `responded_at` de la solicitud informados, porque la aceptación es la respuesta y el cierre que sigue es automático | Obligatorio (`requiresReason`) | El compromiso nuevo sigue WF-D, con su aprobación; el aporte, WF-P. Con `responded_at`, la solicitud cuenta como absuelta | [DOC-01 §9.3.1.2] medio de solicitud (RF-GRV-004) |
```

En `docs/workflows.md`, reemplazar:

```markdown
| `decline` (Declinar) | `under_evaluation` | `declined` | `GRIEVANCE_MANAGER`, `SOCIAL_MANAGER` | `AttentionWriteGuard`: `response` informada (RF-GRV-004), p. ej. por RN-INV-001 | Obligatorio (`requiresReason`) | AUT-08: «Carta de respuesta» si llegó por CARTA DE SOLICITUD | RF-GRV-004 |
```

por:

```markdown
| `decline` (Declinar) | `under_evaluation` | `declined` | `GRIEVANCE_MANAGER`, `SOCIAL_MANAGER` | `AttentionWriteGuard`: `response` informada (RF-GRV-004), p. ej. por RN-INV-001 | Obligatorio (`requiresReason`) | AUT-08 (tras UPS-23): «Carta de respuesta» si llegó por CARTA DE SOLICITUD; hasta entonces, la carta firmada se sube como evidencia CARTA (ASM-098) | RF-GRV-004 |
```

- [ ] **Paso 5: WF-F con `assess` de comando, WF-J y WF-M**

En `docs/workflows.md`, reemplazar:

```markdown
- **Revisiones:** `POST /api/sgspe/social-risks/{id}/assessments` crea la `risk_assessment` (inmutable), recalcula los valores
  vigentes y el nivel con los umbrales del proyecto (RN-RSK-002, ASM-004) y aplica `assess` en la primera revisión. Una
  revisión residual lleva `residual` en verdadero.
```

por:

```markdown
- **Revisiones (ASM-099):** `POST /api/sgspe/social-risks/{id}/assessments` crea la `risk_assessment` (`appendOnly` y
  `apiOnly`: una revisión se corrige con otra), recalcula los valores vigentes o los residuales y el nivel con los
  umbrales del proyecto (RN-RSK-002, ASM-004), fija `next_review_date` en la fecha de la revisión más
  `review_every_days` (o `risk_review_days`) y aplica `assess` en la primera revisión, que no puede ser residual. Las
  revisiones de un tipo, residual o no, van en orden de fecha. Una revisión residual lleva `residual` en verdadero.
```

En `docs/workflows.md`, reemplazar:

```markdown
| `assess` (Evaluar) | `identified` | `assessed` | `SOCIAL_RISK_MANAGER` | `RiskWriteGuard`: probabilidad, impacto y responsable informados (RF-RSK-001) | Obligatorio (`requiresReason`) | La aplica el endpoint de revisiones en la primera revisión; aviso de riesgo escalado si el nivel es ALTO o CRITICO; AUT-11 si es CRITICO | [DOC-01 §9.3.2.1] evaluación |
```

por:

```markdown
| `assess` (Evaluar) | `identified` | `assessed` | `SOCIAL_RISK_MANAGER` | `CommandTransitionGuard`: solo desde el endpoint de revisiones, en la primera revisión (ASM-099). `RiskWriteGuard`: probabilidad, impacto y responsable informados (RF-RSK-001) | Obligatorio (`requiresReason`) | La aplica el endpoint de revisiones; `RiskNoticeListener` publica `risk_escalated` si el nivel es ALTO o CRITICO; AUT-11 si es CRITICO | [DOC-01 §9.3.2.1] evaluación |
```

En `docs/workflows.md`, reemplazar:

```markdown
Las revisiones siguientes no cambian el estado: actualizan los valores vigentes, `next_review_date` y la tendencia. Si
el nivel sube a ALTO o CRITICO, un listener de `sgspe.notification` publica el aviso de riesgo escalado (F4).
```

por:

```markdown
Las revisiones siguientes no cambian el estado: actualizan los valores vigentes, `next_review_date` y la tendencia. Si
el nivel sube a ALTO o CRITICO, `RiskNoticeListener` (`sgspe.notification`) publica `risk_escalated` en la transacción
del endpoint (F4).
```

En `docs/workflows.md`, reemplazar:

```markdown
- **Origen de las alertas:** las crea en `detected` `WarningRulesJob` (JOB-04) o una persona. `ai_detected` indica que la
  señal la detectó la IA, pero la IA no escribe la alerta ni aplica transiciones: la registra una persona (RQ-AI-001,
  RQ-AI-003). Ningún tipo de sugerencia de IA crea alertas.
```

por:

```markdown
- **Origen de las alertas:** las crea en `detected` `WarningRulesJob` (JOB-04, con los «Tipos de regla de JOB-04») o una
  persona. `ai_detected` indica que la señal la detectó la IA, pero la IA no escribe la alerta ni aplica transiciones:
  la registra una persona (RQ-AI-001, RQ-AI-003). Ningún tipo de sugerencia de IA crea alertas.
```

En `docs/workflows.md`, reemplazar:

```markdown
Al crear una alerta (alta, no transición) corren AUT-09 (tarea de triaje) y el aviso de alerta nueva.
```

por:

```markdown
Al crear una alerta (alta, no transición) corren AUT-09 (tarea de triaje) y `WarningNoticeListener`, que publica
`warning_new`, también cuando la crea JOB-04 como la plataforma.
```

En `docs/workflows.md`, reemplazar:

```markdown
- **Fase:** F4. Sin `requiresReason`.
- **Requisitos:** RF-EWS-001.
```

por:

```markdown
- **Fase:** F4. Sin `requiresReason`.
- **Sin severidad ni escalamiento (ASM-103):** un incidente no tiene `severity`, motivo obligatorio ni plazo; escalan
  las QCS y los riesgos que origina.
- **Requisitos:** RF-EWS-001.
```

- [ ] **Paso 6: Automatizaciones y tipos de documento de QCS tras UPS-23**

En `docs/workflows.md`, reemplazar:

```markdown
| AUT-06 | `aut-06-grievance-receipt` | `grievance` | `STATE_ENTERED` `acknowledged` | — | `GENERATE_DOCUMENT` tipo «Cargo de queja» | F4 | RQ-REP-029 |
| AUT-07 | `aut-07-inquiry-answer-letter` | `inquiry` | `STATE_ENTERED` `answered` | `channel` `EQUALS` CARTA | `GENERATE_DOCUMENT` tipo «Carta de respuesta» | F4 | RQ-REP-029 |
| AUT-08 | `aut-08-request-decline-letter` | `stakeholder_request` | `STATE_ENTERED` `declined` | `request_medium` `EQUALS` CARTA DE SOLICITUD | `GENERATE_DOCUMENT` tipo «Carta de respuesta» | F4 | RQ-REP-029, RF-GRV-004 |
```

por:

```markdown
| AUT-06 | `aut-06-grievance-receipt` | `grievance` | `STATE_ENTERED` `acknowledged` | — | `GENERATE_DOCUMENT` tipo «Cargo de queja» (`grievance-receipt`) | F4, tras UPS-23 | RQ-REP-029, ASM-098 |
| AUT-07 | `aut-07-inquiry-answer-letter` | `inquiry` | `STATE_ENTERED` `answered` | `channel` `EQUALS` CARTA | `GENERATE_DOCUMENT` tipo «Carta de respuesta» (`inquiry-answer-letter`) | F4, tras UPS-23 | RQ-REP-029, ASM-098 |
| AUT-08 | `aut-08-request-decline-letter` | `stakeholder_request` | `STATE_ENTERED` `declined` | `request_medium` `EQUALS` CARTA DE SOLICITUD | `GENERATE_DOCUMENT` tipo «Carta de respuesta» (`request-answer-letter`) | F4, tras UPS-23 | RQ-REP-029, RF-GRV-004, ASM-098 |
```

En `docs/workflows.md`, reemplazar:

```markdown
Un documento emitido no se filtra por permisos de campo, y su copia congelada lleva todos los campos del registro y todas
las columnas de cada tabla relacionada que nombra la plantilla, aunque la plantilla no las imprima (código de
wasichai-documents 0.6.0, UPS-23). Por eso un tipo de documento solo se declara sobre un objeto sin campos restringidos
y sin tablas relacionadas de objetos con campos restringidos (`security.md` §11.4); `test_model.py` lo comprueba. El acta
de entrega espera a UPS-23, porque `delivery` restringe `recipients` y `conformity_by`; hasta entonces el acta firmada se
sube como evidencia ACTA DE ENTREGA, que D9 ya exige.
```

por:

```markdown
Un documento emitido no se filtra por permisos de campo, y su copia congelada lleva todos los campos del registro y
todas las columnas de cada tabla relacionada que nombra la plantilla, aunque la plantilla no las imprima (código de
wasichai-documents 0.6.0, UPS-23). Por eso un tipo de documento solo se declara sobre un objeto sin campos restringidos
y sin tablas relacionadas de objetos con campos restringidos (`security.md` §11.4); `test_model.py` lo comprueba. El
acta de entrega espera a UPS-23, porque `delivery` restringe `recipients` y `conformity_by`; hasta entonces el acta
firmada se sube como evidencia ACTA DE ENTREGA, que D9 ya exige. Por la misma regla esperan el «Cargo de queja» y las
dos «Carta de respuesta» (ASM-098): las tres QCS restringen `person`, así que el cargo y la carta se imprimen fuera de
SGSPE, se entregan en papel y, firmados, se suben como evidencia FORMULARIO o CARTA vinculada a la QCS.
```

En `docs/workflows.md`, reemplazar:

```markdown
| Lista de asistentes | `attendance-list` | `LA` | `engagement` | F3 | Título, tipo, fecha, lugar y total de asistentes; quince filas en blanco para el nombre, el cargo u organización y la firma de cada asistente, que se llenan a mano |
```

por:

```markdown
| Lista de asistentes | `attendance-list` | `LA` | `engagement` | F3 | Título, tipo, fecha, lugar y total de asistentes; quince filas en blanco para el nombre, el cargo u organización y la firma de cada asistente, que se llenan a mano |
| Cargo de queja | `grievance-receipt` | `CQ` | `grievance` | F4, tras UPS-23 | Código, fecha y hora de recepción, canal, categoría y plazo de acuse; espacios para el nombre y la firma de quien plantea y de quien recibe |
| Carta de respuesta | `inquiry-answer-letter` | `CC` | `inquiry` | F4, tras UPS-23 | Código, fecha de recepción, respuesta y fecha de respuesta; espacio para el nombre y la firma de quien recibe |
| Carta de respuesta | `request-answer-letter` | `CS` | `stakeholder_request` | F4, tras UPS-23 | Código, fecha de recepción, recurso pedido, respuesta y fecha de respuesta; espacio para el nombre y la firma de quien recibe |
```

- [ ] **Paso 7: JOB-01 y JOB-04, y la sección «Tipos de regla de JOB-04»**

En `docs/workflows.md`, reemplazar:

```markdown
- **Registros de wasichai como la plataforma:** JOB-01 a JOB-05, JOB-07 y el paso de borradores de JOB-08 los leen o los
  escriben con `RecordService.asPlatform(organizationId)`, una organización por vez. Para recorrer las organizaciones
  usan `Organizations` (`sgspe.common`), que envuelve `TenantDirectory` (UPS-17, [WAS-ADR-0057]). JOB-01, JOB-02 y
  JOB-03 escriben con `asPlatform(organización, source = "job:…")` (UPS-03). JOB-07 solo recorre la lista de
  organizaciones con la IA activada (`ai-architecture.md` §12.1).
```

por:

```markdown
- **Registros de wasichai como la plataforma:** JOB-01 a JOB-05, JOB-07 y el paso de borradores de JOB-08 los leen o los
  escriben con `RecordService.asPlatform(organizationId)`, una organización por vez. Para recorrer las organizaciones
  usan `Organizations` (`sgspe.common`), que envuelve `TenantDirectory` (UPS-17, [WAS-ADR-0057]); desde F4, JOB-01 y
  JOB-04 recorren solo las organizaciones que tienen el objeto que miden (`Organizations.having`), así un tenant que
  todavía no tiene el modelo de F4 sigue con F3. JOB-01, JOB-02, JOB-03 y JOB-04 escriben con `asPlatform(organización,
  source = "job:…")` (UPS-03). JOB-07 solo recorre la lista de organizaciones con la IA activada (`ai-architecture.md`
  §12.1).
```

En `docs/workflows.md`, reemplazar:

```markdown
| JOB-01 | `DeadlineJob` (`sgspe.deadline`) | Cada hora (ASM-006) | `deadline_status` de `commitment`, QCS y `task`; `validity_status` de `agreement`; `review_status` de `social_risk`, y `next_review_date` si cambia `review_every_days`; tareas de escalamiento (`escalation:commitment:<id>:<fecha del plazo>:<nivel>`) y de revisión de riesgos; los avisos de plazo los publica `DeadlineNotices`, una fuente de wasichai-notifications que lee lo que este trabajo escribe, así que llegan a lo sumo una hora después | Cambiar `workflow_state`: un compromiso vencido sigue en `in_progress` o en `blocked` | RN-WFL-001, RF-WFL-004, RN-RSK-001, ASM-006, ASM-030, ASM-070 | F3 (compromisos, acuerdos, tareas), F4 (QCS, riesgos) |
```

por:

```markdown
| JOB-01 | `DeadlineJob` (`sgspe.deadline`) | Cada hora (ASM-006) | `deadline_status` de `commitment`, QCS y `task`; `validity_status` de `agreement`; `review_status` de `social_risk`, y `next_review_date` si cambia `review_every_days`; tareas de escalamiento (`escalation:<objeto>:<id>:<fecha del plazo>:<nivel>`, de compromisos y, desde F4, de quejas y consultas, sin asignado ni fecha) y, desde F4, de revisión de riesgos (`risk_review:<id>:<next_review_date>`, asignada al responsable del riesgo, con fecha `next_review_date` y título «Revisar el riesgo <código>», al pasar a POR REVISAR o REVISION VENCIDA, ASM-099); los avisos de plazo los publica `DeadlineNotices`, una fuente de wasichai-notifications que lee lo que este trabajo escribe, así que llegan a lo sumo una hora después | Cambiar `workflow_state`: un compromiso vencido sigue en `in_progress` o en `blocked` | RN-WFL-001, RF-WFL-004, RN-RSK-001, ASM-006, ASM-030, ASM-070 | F3 (compromisos, acuerdos, tareas), F4 (QCS, riesgos) |
```

En `docs/workflows.md`, reemplazar:

```markdown
| JOB-04 | `WarningRulesJob` (`sgspe.warning`) | Cada hora, configurable (ASM-006), como los plazos, para que una alerta no espere un día | `early_warning` en `detected` cuando una regla habilitada supera su umbral (`threshold`) en su ventana (`window_days`); no crea otra si ya hay una abierta de la misma regla y proyecto | Confirmar o descartar una alerta (RN-EWS-001) | RF-EWS-004 | F4 |
```

por:

```markdown
| JOB-04 | `WarningRulesJob` (`sgspe.warning`) | Cada hora, configurable (ASM-006), como los plazos, para que una alerta no espere un día | `early_warning` en `detected` cuando una regla habilitada alcanza su umbral (`threshold`) en su ventana (`window_days`), según «Tipos de regla de JOB-04»: por proyecto en `active` o `suspended`, con las reglas del proyecto y, de cada tipo que el proyecto no tiene, las del tenant; sin otra alerta si hay una abierta (fuera de `dismissed` y `closed`) de la misma regla y proyecto (y riesgo, en NIVEL DE RIESGO) o una creada en los últimos `window_days` días; con `generator_key` `warning_rule:<regla>:<proyecto>[:<riesgo>]:<fecha de Lima>`, único, y el motivo `alerta por regla (JOB-04 WarningRulesJob)` | Confirmar o descartar una alerta (RN-EWS-001); aplicar transiciones; escribir datos personales, texto libre de otros registros o valores de campos restringidos | RF-EWS-004, ASM-097 | F4 |
```

En `docs/workflows.md`, reemplazar:

```markdown
JOB-08 corre una vez al día, como fija `architecture.md`, y además del número N de borradores (ASM-028) usa un margen
configurable: solo borra archivos sin referencia más antiguos que ese margen, porque los de una generación o una emisión en
curso todavía no tienen un registro que los apunte.
```

por:

```markdown
JOB-08 corre una vez al día, como fija `architecture.md`, y además del número N de borradores (ASM-028) usa un margen
configurable: solo borra archivos sin referencia más antiguos que ese margen, porque los de una generación o una emisión
en curso todavía no tienen un registro que los apunte.

### Tipos de regla de JOB-04

`WarningRulesJob` corre cada hora en una réplica (`ClusterLock` con la clave `sgspe.warning`), recorre
`Organizations.having("early_warning_rule")` y escribe como la plataforma de cada organización con
`source = job:warning-rules`; una organización que falla se registra y no detiene a las demás. Por organización:

1. lee las reglas con `enabled` verdadero y los proyectos en `active` o `suspended`;
2. para cada proyecto toma sus reglas y, de cada tipo que el proyecto no tiene, las del tenant (sin `project`): una
   regla del proyecto reemplaza a la del tenant del mismo tipo (ASM-097);
3. evalúa cada regla con su evaluador puro y con conteos (`Records.count`), nunca cargando los registros;
4. si dispara, crea la alerta salvo que haya una abierta (fuera de `dismissed` y `closed`) de la misma regla y proyecto
   (y riesgo, en NIVEL DE RIESGO) o una creada en los últimos `window_days` días;
5. la alerta nace en `detected` con `title` «<nombre de la regla>: <tipo>», `trigger_description` con el conteo, el
   umbral y la ventana, `severity` de la regla (MEDIO si está vacía), `detected_at` ahora, `early_warning_rule`,
   `project`, `ai_detected` falso y `generator_key` `warning_rule:<regla>:<proyecto>[:<riesgo>]:<fecha de Lima>`, cuyo
   único frena una carrera entre réplicas, como en las tareas de los trabajos.

Nunca aplica transiciones ni confirma (RN-EWS-001), y ningún texto que escribe lleva datos personales, texto libre de
otros registros ni valores de campos restringidos.

Reglas comunes:

- `threshold` es un número entero; un valor con parte fraccionaria se rechaza (`WarningWriteGuard`, `400 OUT_OF_RANGE`),
  igual que `window_days` fuera de 1 a 365. `window_days` vacío vale 7.
- La ventana de los tipos por eventos son los `window_days` días de Lima que terminan hoy: desde el inicio del día
  `hoy − window_days + 1` hasta ahora.
- En todos los tipos, `window_days` es también el silencio tras una alerta de la misma regla y proyecto (y riesgo, en
  NIVEL DE RIESGO).
- Dispara cuando el valor medido es mayor o igual que `threshold`.
- Una regla del tenant crea una alerta por cada proyecto que alcanza el umbral; una del proyecto, solo para el suyo.

| Tipo | Entrada (proyecto P) | `threshold` | Ventana | Deduplicación | Crea |
|---|---|---|---|---|---|
| CONTEO DE SEÑALES | `early_warning_signal` de P con `received_at` en la ventana, de cualquier fuente | Conteo, entero desde 1 | Sí | Regla y proyecto | Alerta con «<n> señales en <W> días (umbral <T>)» |
| CONTEO DE QCS | Planteamientos de P (las tres QCS contadas con `AttentionCounting`, confidenciales incluidas) con `received_at` en la ventana | Conteo, entero desde 1 | Sí | Regla y proyecto | Alerta con «<n> QCS recibidas en <W> días (umbral <T>)», sin desglose |
| COMPROMISOS VENCIDOS | `commitment` de P en `approved`, `in_progress` o `blocked` con `deadline_status` VENCIDO en el momento de la corrida | Conteo, entero desde 1 | No (solo silencio) | Regla y proyecto | Alerta con «<n> compromisos vencidos (umbral <T>)» |
| MENCIONES NEGATIVAS | `media_mention` de P con `tone` NEGATIVO y `published_date` en la ventana | Conteo, entero desde 1 | Sí | Regla y proyecto | Alerta con «<n> menciones negativas en <W> días (umbral <T>)» |
| INCIDENTES | `incident` de P con `occurred_at` en la ventana, de cualquier tipo y estado | Conteo, entero desde 1 | Sí | Regla y proyecto | Alerta con «<n> incidentes en <W> días (umbral <T>)» |
| NIVEL DE RIESGO | Cada `social_risk` de P en `assessed`, `mitigating` o `monitoring` con `score` vigente | Puntaje P × I mínimo, entero de 1 a 25 | No (solo silencio) | Regla, proyecto y riesgo | Una alerta por riesgo, con `social_risk`, su `locality` y su `community`, y «Riesgo <código> con puntaje <s> (umbral <T>)» |

**Por qué no CAMBIO DE POSTURA.** Esa regla leería `stakeholder.stance` o `stakeholder_assessment.stance`, campos
restringidos a `SOCIAL_MANAGER` y `STAKEHOLDER_MANAGER` (GAP-024). JOB-04 lee como la plataforma, sin permisos de campo,
y la alerta diría en su título o en `trigger_description` qué grupo de interés cambió de postura y hacia dónde.
`early_warning` lo leen todos los roles operativos y `warning_new` lo llevaría a la bandeja: la regla publicaría un dato
restringido. Queda fuera del MVP (ASM-097) y el valor sale del catálogo; vuelve cuando la alerta pueda llevar campos
restringidos o un alcance por registro propio.
```

- [ ] **Paso 8: Listeners**

En `docs/workflows.md`, reemplazar:

```markdown
Los listeners son los nueve de §7.5 del modelo de dominio, con `ApprovalNoticeListener` en lugar de
`NotificationListener` desde la decisión D2 y `DeadlineStatusListener` desde F3. Corren como quien escribió y son
atómicos con la escritura solo dentro de una transición o de un endpoint con transacción. Esta tabla agrega su relación
con los workflows.
```

por:

```markdown
Los listeners son los once de §7.5 del modelo de dominio, con `ApprovalNoticeListener` en lugar de
`NotificationListener` desde la decisión D2, `DeadlineStatusListener` desde F3 y `RiskNoticeListener` y
`WarningNoticeListener` desde F4. Corren como quien escribió y son atómicos con la escritura solo dentro de una
transición o de un endpoint con transacción. Esta tabla agrega su relación con los workflows.
```

En `docs/workflows.md`, reemplazar:

```markdown
| `QcsDueDatesListener` (`sgspe.attention`) | Alta de `grievance`, `inquiry` o `stakeholder_request` | Ninguna | `ack_due_date`, `response_due_date`, `resolution_due_date` del mismo registro, con la marca | Solo si el alta llega por un endpoint con transacción | No falla el alta; JOB-02 completa | Quien registra QCS necesita `UPDATE` sobre el objeto, con motivo |
```

por:

```markdown
| `QcsDueDatesListener` (`sgspe.attention`) | Alta de `grievance`, `inquiry` o `stakeholder_request` | Ninguna | `ack_due_date` (días hábiles con `BusinessDays`, ASM-096) y `resolution_due_date` de la queja y `response_due_date` de la consulta, del mismo registro, con la marca; la solicitud no tiene plazo en el MVP (ASM-100) | Solo si el alta llega por un endpoint con transacción | No falla el alta; JOB-02 completa | Quien registra QCS necesita `UPDATE` sobre el objeto, con motivo |
```

En `docs/workflows.md`, reemplazar:

```markdown
| `ApprovalNoticeListener` (`sgspe.notification`) | Envío a aprobación (`submit_for_review` de WF-A, `submit_minutes` de WF-C, `submit` de WF-D) y toda transición que sale del estado pendiente | Las mismas | El módulo wasichai-notifications (`Notifications.publish` y `resolve`), en la transacción de la transición | Sí | Un aviso mal formado se registra y la transición sigue; un fallo de la base al guardarlo revierte la transición; sin asignaciones que coincidan, una advertencia en el log | Lee `project_assignment` como quien envió, así que todo rol que envía a aprobación tiene `READ` sobre ella (`security.md`). Si quien envía un compromiso no puede leer lo que decide el nivel (D5), el aviso va a los roles de `approve` y de `approve_senior` |
```

por:

```markdown
| `ApprovalNoticeListener` (`sgspe.notification`) | Envío a aprobación (`submit_for_review` de WF-A, `submit_minutes` de WF-C, `submit` de WF-D) y toda transición que sale del estado pendiente; desde F4, una queja ALTA o CRITICA que entra en `in_action` con `policy_high_severity_requires_approver` activo, un cambio de `severity` a ALTA o CRITICA en `in_action` y toda transición que sale de `in_action` (ASM-102) | Las mismas; desde F4, `start_action`, `start_action_after_investigation`, `resolve_high_severity` y `withdraw_in_action` de WF-E1 | El módulo wasichai-notifications (`Notifications.publish` y `resolve`), en la transacción de la transición | Sí | Un aviso mal formado se registra y la transición sigue; un fallo de la base al guardarlo revierte la transición; sin asignaciones que coincidan, una advertencia en el log | Lee `project_assignment` como quien envió, así que todo rol que envía a aprobación tiene `READ` sobre ella (`security.md`). Si quien envía un compromiso no puede leer lo que decide el nivel (D5), el aviso va a los roles de `approve` y de `approve_senior` |
```

En `docs/workflows.md`, reemplazar:

```markdown
## Avisos

Los avisos salen de eventos, nunca de las entidades (RN-NTF-001): `ApprovalNoticeListener` al enviar a aprobación y
`DeadlineNotices` (`sgspe.deadline`), una fuente de wasichai-notifications que cada hora devuelve todos los avisos de
plazo vigentes según lo que escribió JOB-01. Los guarda y los entrega el módulo (bandeja, SSE y correo, ADR-0018,
ASM-012). Cada aviso tiene una clave estable dentro de su fuente (`sgspe.approvals` o `sgspe.deadlines`): publicarla
otra vez no duplica (RF-NTF-001), y un aviso que deja de ser cierto se resuelve (el envío deja el estado pendiente; el
compromiso deja de estar vencido). Un aviso de aprobación es `ACTION`; uno por vencer, `WARNING`; uno vencido o de
escalamiento, `ACTION`. Quién recibe cada aviso lo fija «Destinatarios por rol».
```

por:

```markdown
| `RiskNoticeListener` (`sgspe.notification`) | Revisión no residual que sube `risk_level` a ALTO o CRITICO (de un nivel menor, o de ALTO a CRITICO), en la transacción del endpoint de revisiones | Ninguna (la primera revisión aplica `assess` en la misma transacción) | El módulo wasichai-notifications: publica `risk_escalated` del nivel y resuelve el anterior del mismo riesgo | Sí: corre en la transacción del endpoint | Un aviso mal formado se registra y la revisión sigue; un fallo de la base al guardarlo revierte la revisión; sin asignaciones que coincidan, una advertencia en el log | Lee `project_assignment` como quien revisa, que tiene `READ` sobre ella |
| `WarningNoticeListener` (`sgspe.notification`) | Alta de `early_warning`, por una persona o por JOB-04 como la plataforma; `start_triage` resuelve el aviso | `start_triage` | El módulo wasichai-notifications: publica `warning_new` y lo resuelve | Solo si el alta llega por un endpoint con transacción o por JOB-04, que crea la alerta en la suya | Un aviso mal formado se registra y el alta sigue | Lee `project_assignment` como quien crea la alerta; en las de JOB-04, como la plataforma |

Si una alerta que JOB-04 crea como la plataforma no llegara a `WarningNoticeListener` ni a AUT-09, JOB-04 publica
`warning_new` con la misma clave, sin duplicar, y crea la tarea de triaje con la clave `warning_triage:<id>`; lo decide
la prueba de integración del plan 4.2b.

## Avisos

Los avisos salen de eventos, nunca de las entidades (RN-NTF-001): `ApprovalNoticeListener` al enviar a aprobación y
`DeadlineNotices` (`sgspe.deadline`), una fuente de wasichai-notifications que cada hora devuelve todos los avisos de
plazo vigentes según lo que escribió JOB-01. Los guarda y los entrega el módulo (bandeja, SSE y correo, ADR-0018,
ASM-012). Cada aviso tiene una clave estable dentro de su fuente (`sgspe.approvals` o `sgspe.deadlines`): publicarla
otra vez no duplica (RF-NTF-001), y un aviso que deja de ser cierto se resuelve (el envío deja el estado pendiente; el
compromiso deja de estar vencido). Un aviso de aprobación es `ACTION`; uno por vencer, `WARNING`; uno vencido o de
escalamiento, `ACTION`. Quién recibe cada aviso lo fija «Destinatarios por rol».
```

- [ ] **Paso 9: Avisos y destinatarios de F4**

En `docs/workflows.md`, reemplazar:

```markdown
Los avisos salen de eventos, nunca de las entidades (RN-NTF-001): `ApprovalNoticeListener` al enviar a aprobación y
`DeadlineNotices` (`sgspe.deadline`), una fuente de wasichai-notifications que cada hora devuelve todos los avisos de
plazo vigentes según lo que escribió JOB-01. Los guarda y los entrega el módulo (bandeja, SSE y correo, ADR-0018,
ASM-012). Cada aviso tiene una clave estable dentro de su fuente (`sgspe.approvals` o `sgspe.deadlines`): publicarla
```

por:

```markdown
Los avisos salen de eventos, nunca de las entidades (RN-NTF-001): `ApprovalNoticeListener` al enviar a aprobación,
`DeadlineNotices` (`sgspe.deadline`), una fuente de wasichai-notifications que cada hora devuelve todos los avisos de
plazo vigentes según lo que escribió JOB-01, y, desde F4, `RiskNoticeListener` al subir el nivel de un riesgo y
`WarningNoticeListener` al crear una alerta. Los guarda y los entrega el módulo (bandeja, SSE y correo, ADR-0018,
ASM-012). Cada aviso tiene una clave estable dentro de su fuente (`sgspe.approvals` o `sgspe.deadlines` y, desde F4,
`sgspe.risks` y `sgspe.warnings`): publicarla
```

En `docs/workflows.md`, reemplazar:

```markdown
| Aprobación pendiente (RF-NTF-001) | `approval_pending` | `ApprovalNoticeListener` (WF-G, WF-H y `social_contribution` suman su paso a `ApprovalNoticeListener.STEPS` en su fase) | WF-A `submit_for_review`; WF-C `submit_minutes`; WF-D `submit`; WF-G `submit`; WF-H `submit_for_review` y `resubmit`; alta de `social_contribution` en `requested` | Los roles de la transición de aprobación que sigue | `approval_pending:<objeto>:<id>`: volver a enviar reabre el mismo aviso |
```

por:

```markdown
| Aprobación pendiente (RF-NTF-001) | `approval_pending` | `ApprovalNoticeListener` (WF-G, WF-H y `social_contribution` suman su paso a `ApprovalNoticeListener.STEPS` en su fase) | WF-A `submit_for_review`; WF-C `submit_minutes`; WF-D `submit`; WF-G `submit`; WF-H `submit_for_review` y `resubmit`; alta de `social_contribution` en `requested`; desde F4, `start_action` o `start_action_after_investigation` de una queja ALTA o CRITICA con `policy_high_severity_requires_approver` activo, o un cambio de `severity` a ALTA o CRITICA en `in_action` (ASM-102) | Los roles de la transición de aprobación que sigue; en la queja, los de `resolve_high_severity` | `approval_pending:<objeto>:<id>`: volver a enviar reabre el mismo aviso; el de una queja se resuelve al salir de `in_action` o si la severidad baja de ALTA |
```

En `docs/workflows.md`, reemplazar:

```markdown
| Plazo de QCS próximo a incumplirse (queja: RF-NTF-001; consulta: RF-WFL-004) | `qcs_deadline` | JOB-01 | `deadline_status` de una queja o una consulta pasa a POR VENCER o a VENCIDO (nivel 0: acuse, respuesta o resolución); luego, los niveles 1 y 2 de atraso | Nivel 0: el responsable o, sin responsable, los roles de la transición que sigue; nivel 1: los de `verify` de WF-E1; nivel 2: los de `approve_senior` de WF-D, porque WF-E no tiene aprobación superior. Una tarea de escalamiento en los niveles 1 y 2 | `qcs_deadline:<objeto>:<id>:<fecha del plazo>:<situación o nivel>` |
| Riesgo escalado (RF-NTF-001) | `risk_escalated` | Un listener de `sgspe.notification` (F4); JOB-01 | El endpoint de revisiones sube `risk_level` a ALTO o CRITICO; o la revisión vence (`review_status` REVISION VENCIDA, nivel 0) y escala a los `escalation_first_days` (nivel 1) y `escalation_second_days` (nivel 2) días de atraso (RF-WFL-004) | Al subir el nivel: el responsable del riesgo y los roles de la transición de tratamiento que sigue. Revisión vencida: nivel 0, el responsable; nivel 1, esos roles de tratamiento; nivel 2, los de `approve_senior` de WF-D | `risk_escalated:<id>:<nivel o situación>:<fecha>` |
```

por:

```markdown
| Plazo de QCS próximo a incumplirse (queja: RF-NTF-001; consulta: RF-WFL-004) | `qcs_deadline` | `DeadlineNotices` | `deadline_status` de una queja o una consulta en POR VENCER o en VENCIDO (nivel 0: acuse, respuesta o resolución); luego, los niveles 1 y 2 de atraso | Nivel 0: el responsable o, sin responsable, los roles de la transición que sigue; nivel 1: los de `verify` de WF-E1; nivel 2: los de `approve_senior` de WF-D, porque WF-E no tiene aprobación superior; en una QCS confidencial, solo los que la verían (ASM-102). Una tarea de escalamiento en los niveles 1 y 2 | `qcs_deadline:<objeto>:<id>:<fecha del plazo>:<POR VENCER o nivel>`; se resuelve cuando la situación deja de ser cierta |
| Riesgo escalado por nivel (RF-NTF-001) | `risk_escalated` | `RiskNoticeListener` (F4) | Una revisión no residual sube `risk_level` a ALTO o CRITICO (de un nivel menor, o de ALTO a CRITICO) | El responsable del riesgo y los roles de la transición de tratamiento que sigue | `risk_escalated:<id>:<nivel de riesgo>:<assessment_date>`; se resuelve con la revisión siguiente o el cierre del riesgo |
| Riesgo con revisión vencida (RF-NTF-001, RF-WFL-004) | `risk_escalated` | `DeadlineNotices` (F4) | `review_status` REVISION VENCIDA (nivel 0); luego, a los `escalation_first_days` (nivel 1) y `escalation_second_days` (nivel 2) días de atraso | Nivel 0, el responsable; nivel 1, los roles de la transición de tratamiento que sigue; nivel 2, los de `approve_senior` de WF-D | `risk_escalated:<id>:<nivel>:<next_review_date>`; se resuelve con la revisión que mueve `next_review_date` |
```

En `docs/workflows.md`, reemplazar:

```markdown
| Alerta nueva (RF-NTF-001) | `warning_new` | Un listener de `sgspe.notification` (F4) | Alta de `early_warning`, también la que crea JOB-04 como la plataforma | El responsable de la alerta, si lo tiene, y los roles de `start_triage` | `warning_new:<id>` |
```

por:

```markdown
| Alerta nueva (RF-NTF-001) | `warning_new` | `WarningNoticeListener` (F4) | Alta de `early_warning`, también la que crea JOB-04 como la plataforma | El responsable de la alerta, si lo tiene, y los roles de `start_triage` | `warning_new:<id>`; se resuelve con `start_triage` |
| QCS confidencial en el nivel 2 (RF-WFL-004) | `qcs_confidential_overdue` | `DeadlineNotices` (F4) | Nivel 2 de `qcs_deadline` de una QCS confidencial | Los asignados con un rol de `approve_senior` de WF-D que no la verían (los que sí la ven reciben `qcs_deadline`) (ASM-102) | `qcs_confidential_overdue:<proyecto>:<huella>`, donde `<huella>` son los 16 primeros dígitos hexadecimales del SHA-256 de `<objeto>:<id>:<fecha del plazo>`: un aviso por QCS y plazo sin su id, tipo ni fecha; se resuelve cuando la QCS deja el nivel 2 o el estado con plazo |

Los textos de los avisos (`NoticeTexts`) llevan el código, el tipo y la fecha; nunca descripción, persona ni texto
libre. El de `qcs_confidential_overdue` es fijo salvo el nombre del proyecto: «Una QCS confidencial del proyecto
<proyecto> superó su plazo. Pida el detalle al equipo de quejas.» No lleva código, título, tipo, fecha, nivel, categoría
ni enlace al registro, y es `ACTION`. La tarea de escalamiento del nivel 2 se crea igual, con su clave
`escalation:<objeto>:<id>:<fecha>:2`, y su título solo lleva el código de la QCS.
```

En `docs/workflows.md`, reemplazar:

```markdown
4. **Sin coincidencias.** Si ninguna asignación vigente coincide, no se crea el aviso y quien lo pidió deja una
   advertencia en el log. La tarea de escalamiento, si la hay, la crea JOB-01 igual, con la clave
   `escalation:commitment:<id>:<fecha del plazo>:<nivel>`, y queda visible en las tareas del proyecto.
```

por:

```markdown
4. **Sin coincidencias.** Si ninguna asignación vigente coincide, no se crea el aviso y quien lo pidió deja una
   advertencia en el log. La tarea de escalamiento, si la hay, la crea JOB-01 igual, con la clave
   `escalation:<objeto>:<id>:<fecha del plazo>:<nivel>`, y queda visible en las tareas del proyecto.
```

En `docs/workflows.md`, reemplazar:

```markdown
6. **Tareas de escalamiento.** Un aviso va a varias personas y una tarea tiene un solo asignado. Por eso, en los niveles 1 y
   2 de un compromiso o de una QCS, JOB-01 crea la tarea vinculada al registro vencido, sin asignado ni fecha y con
   `generator_key` `escalation:<objeto>:<id>:<fecha del plazo>:<nivel>`; la toma uno de los destinatarios. Una tarea vencida
   y una revisión de riesgo vencida escalan solo con avisos: la tarea ya existe, y el riesgo ya tiene su tarea de revisión.
```

por:

```markdown
6. **Tareas de escalamiento.** Un aviso va a varias personas y una tarea tiene un solo asignado. Por eso, en los niveles
   1 y 2 de un compromiso o de una QCS, JOB-01 crea la tarea vinculada al registro vencido, sin asignado ni fecha y con
   `generator_key` `escalation:<objeto>:<id>:<fecha del plazo>:<nivel>`; la toma uno de los destinatarios. Una tarea
   vencida y una revisión de riesgo vencida escalan solo con avisos: la tarea ya existe, y el riesgo ya tiene su tarea
   de revisión.
7. **QCS confidenciales (ASM-102).** Un aviso sobre una QCS confidencial (`qcs_deadline`, `approval_pending` de una
   queja grave y `task_overdue` de una tarea vinculada a ella) solo llega a quien la vería por `security.md` §9.6: su
   responsable, quien la recibió, los asignados cuyo `role_name` tiene `READ_CONFIDENTIAL` sobre ese objeto en
   `model/roles.json` y, en una queja ALTA o CRITICA, los que tienen `APPROVE` sobre `grievance`. `ModelCatalog` da las
   concesiones de cada rol, así que el filtro no compara nombres de rol. En el nivel 2, los roles de `approve_senior` de
   WF-D que no la verían reciben `qcs_confidential_overdue`, sin datos del registro.
```

En `docs/workflows.md`, reemplazar:

```markdown
| `qcs_deadline` | Nivel 2 | Roles de `approve_senior` de WF-D, porque WF-E no tiene aprobación superior |
```

por:

```markdown
| `qcs_deadline` | Nivel 2 | Roles de `approve_senior` de WF-D, porque WF-E no tiene aprobación superior; en una QCS confidencial, solo los que la verían |
| `qcs_confidential_overdue` | Nivel 2 de una QCS confidencial | Los roles de `approve_senior` de WF-D que no la verían (regla 7) |
| `approval_pending` | Queja ALTA o CRITICA en `in_action` con `policy_high_severity_requires_approver` activo | Roles de `resolve_high_severity`; en una queja confidencial, quienes tienen `APPROVE` sobre `grievance` la leen (regla 7) |
```

- [ ] **Paso 10: Plazos y escalamiento de QCS y riesgos**

En `docs/workflows.md`, reemplazar:

```markdown
- Los indicadores de cumplimiento a tiempo usan las fechas de la transición (`fulfilled_date` frente a `due_date`,
  RQ-DSH-020); `reopen_execution` vacía `fulfilled_date`.
```

por:

```markdown
- Los indicadores de cumplimiento a tiempo usan las fechas de la transición (`fulfilled_date` frente a `due_date`,
  RQ-DSH-020); `reopen_execution` vacía `fulfilled_date`.
- **QCS (ASM-100).** Los plazos se calculan al recibir, desde la fecha de recepción en Lima: el acuse en días hábiles
  con el calendario de feriados (ASM-096), la respuesta y la resolución en días calendario; no se recalculan si después
  cambia la lista de feriados o un parámetro, y no se pausan en `in_investigation`. Una queja mide `ack_due_date` en
  `received` y `resolution_due_date` después; su `response_due_date` queda siempre vacío, porque el campo común solo lo
  usan las consultas. POR VENCER empieza `min(due_soon_days, ⌊d / 2⌋)` días antes del plazo, donde `d` son los días
  calendario entre la fecha de recepción y el plazo: con los valores por defecto, el acuse de 5 días hábiles no nace POR
  VENCER.
- **Riesgos (ASM-099).** `review_status` es AL DIA; POR REVISAR desde el día anterior a `next_review_date` y ese día; y
  REVISION VENCIDA desde el día siguiente. Sus niveles 1 y 2 cuentan los días de atraso desde `next_review_date`.
- **Incidentes (ASM-103).** No tienen plazo ni escalamiento.
```

En `docs/workflows.md`, reemplazar:

```markdown
| `grievance` | `deadline_status` | `ack_due_date` en `received`; `resolution_due_date` después | `received`, `acknowledged`, `in_assessment`, `in_investigation`, `in_action` | `resolved`, `verified`, `closed`, `rejected`, `withdrawn` | `grievance_ack_days`, `grievance_resolution_days`, `due_soon_days`, `escalation_first_days`, `escalation_second_days` | Nivel 1: roles de `verify` de WF-E1; nivel 2: roles de `approve_senior` de WF-D (RF-WFL-004, ASM-030, ASM-070) |
```

por:

```markdown
| `grievance` | `deadline_status` | `ack_due_date` en `received`; `resolution_due_date` después; `response_due_date` siempre vacío | `received`, `acknowledged`, `in_assessment`, `in_investigation`, `in_action` | `resolved`, `verified`, `closed`, `rejected`, `withdrawn` | `grievance_ack_days`, `grievance_resolution_days`, `due_soon_days`, `escalation_first_days`, `escalation_second_days` | Nivel 1: roles de `verify` de WF-E1; nivel 2: roles de `approve_senior` de WF-D (RF-WFL-004, ASM-030, ASM-070) |
```

En `docs/workflows.md`, reemplazar:

```markdown
| `stakeholder_request` | `deadline_status` | `response_due_date`, vacío mientras GAP-005 no fije un plazo | `received`, `under_evaluation` | `accepted`, `declined`, `closed` | — | — |
```

por:

```markdown
| `stakeholder_request` | `deadline_status` | Ninguna en el MVP: `response_due_date` y `deadline_status` quedan vacíos mientras GAP-005 no fije un plazo de solicitudes (ASM-100) | Ninguno | Todos | — | — |
```

En `docs/workflows.md`, reemplazar:

```markdown
| `social_risk` | `review_status` | `next_review_date` | `assessed`, `mitigating`, `monitoring` | `identified`, `closed` | `risk_review_days` (RN-RSK-001), `escalation_first_days`, `escalation_second_days` | Tarea de revisión para el responsable y aviso de riesgo escalado; nivel 1: roles de la transición de tratamiento que sigue; nivel 2: roles de `approve_senior` de WF-D (ASM-030, ASM-070) |
```

por:

```markdown
| `social_risk` | `review_status` | `next_review_date` | `assessed`, `mitigating`, `monitoring` | `identified`, `closed` | `risk_review_days` (RN-RSK-001), `escalation_first_days`, `escalation_second_days` | Tarea de revisión `risk_review:<id>:<next_review_date>` para el responsable, con fecha `next_review_date` (ASM-099), y aviso de riesgo escalado; nivel 1: roles de la transición de tratamiento que sigue; nivel 2: roles de `approve_senior` de WF-D (ASM-030, ASM-070) |
```

- [ ] **Paso 11: Pruebas del modelo y de trabajos**

En `docs/workflows.md`, reemplazar:

```markdown
- las automatizaciones y los tipos de documento son los de la tabla de su fase, salvo los que esperan una brecha
  («F3, tras UPS-23»), y ningún tipo de documento se declara sobre un objeto con campos restringidos ni nombra una tabla
  relacionada de uno (`security.md` §11.4);
```

por:

```markdown
- las automatizaciones y los tipos de documento son los de la tabla de su fase, salvo los que esperan una brecha («F3,
  tras UPS-23» o «F4, tras UPS-23»), y ningún tipo de documento se declara sobre un objeto con campos restringidos ni
  nombra una tabla relacionada de uno (`security.md` §11.4);
```

En `docs/workflows.md`, reemplazar:

```markdown
- **Listeners:** un fallo de `CommitmentDerivationsListener`, también en `reopen_execution`, o de
  `RequestClosureListener` revierte la transición; un aviso mal formado de `ApprovalNoticeListener` no aborta la
  transición; un alta por la API genérica con un listener de derivados que falla queda guardada y JOB-02 la corrige.
```

por:

```markdown
- **JOB-04 (ASM-097):** cada tipo de regla dispara con el valor medido igual al umbral y no con uno menos; la regla del
  proyecto reemplaza a la del tenant del mismo tipo; una alerta abierta, o creada dentro de `window_days`, silencia la
  regla; una regla del tenant crea una alerta por proyecto; ningún texto de la alerta lleva datos personales ni valores
  restringidos; la alerta creada por JOB-04 dispara AUT-09 y `warning_new`.
- **Revisiones de riesgo (ASM-099):** JOB-01 crea una sola tarea `risk_review:<id>:<next_review_date>` por fecha,
  asignada al responsable; la escritura de `risk_level` del endpoint de revisiones dispara AUT-11.
- **Listeners:** un fallo de `CommitmentDerivationsListener`, también en `reopen_execution`, o de
  `RequestClosureListener` revierte la transición; un aviso mal formado de `ApprovalNoticeListener` no aborta la
  transición; un alta por la API genérica con un listener de derivados que falla queda guardada y JOB-02 la corrige.
```

- [ ] **Paso 12: Verificar**

Repetir los dos comandos del Paso 2 y correr las compuertas de «Global Constraints».

Esperado: `OK` en `test_design_docs`, `ok` en la comprobación y las compuertas en verde.

- [ ] **Paso 13: Commit**

```bash
git add docs/workflows.md model/test_design_docs.py
git commit -F - <<'MSG'
docs: workflows de la fase 4 con reglas de alerta, avisos y plazos

workflows.md fija la semántica de los seis tipos de regla de JOB-04, la clave de la tarea de
revisión de riesgo, assess como transición de comando, el origen de cada aviso de F4 con el filtro
de las QCS confidenciales y los plazos sin solicitudes; AUT-06 a AUT-08 esperan a UPS-23.

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_017MAgYcNauGp6cC3ekX1h7U
MSG
```

---

### Tarea 3: Seguridad: `READ_CONFIDENTIAL` y el `RecordReadScope` de las QCS confidenciales

`security.md` suma la acción declarada `READ_CONFIDENTIAL` (§5.3, §7.6), la subsección §9.6 con quién ve una QCS
confidencial, el componente y dónde rige (CON-041 sigue en la opción A para el alcance por proyecto), reemplaza el
riesgo residual de §11.3, suma a §11.4 las salidas de F4 y los documentos de QCS tras UPS-23 (C1), a §14 las pruebas y a
§15 la respuesta del usuario. `gis.md` y el glosario lo reflejan, y ADR-0028 registra la decisión (decisión 11).

**Archivos:**
- Crear: `docs/adr/0028-qcs-confidenciales-con-recordreadscope.md`
- Modificar: `docs/security.md`, `docs/gis.md` (LYR-20), `docs/glossary.md` (QCS), `docs/adr/README.md`
- Probar: `model/test_apply_roles.py` y `model/test_design_docs.py` (leen la matriz de §7)

**Interfaces:**
- Consume: ASM-095, ASM-098, ASM-101, ASM-102, ASM-104, UPS-25 y UPS-26 de la Tarea 1.
- Produce: §9.6 («QCS confidenciales: `RecordReadScope` (F4)»), que citan `workflows.md`, `api.md`, `database-model.md`
  y el roadmap; `READ_CONFIDENTIAL` de `GRIEVANCE_MANAGER` en la matriz, que lee el plan 4.1; ADR-0028, que citan §9.6,
  §15, la trazabilidad (Tarea 7) y el plan 4.2a.

- [ ] **Paso 1: La comprobación falla**

```bash
python3 - <<'PY'
checks = [
    ('docs/security.md', '| `READ_CONFIDENTIAL` | `grievance`, `inquiry`, `stakeholder_request` |', True),
    ('docs/security.md', '### 9.6 QCS confidenciales: `RecordReadScope` (F4)', True),
    ('docs/security.md', '| `ConfidentialAttentionApiTest` |', True),
    ('docs/security.md', '`confidential` es un dato que no restringe la lectura', False),
    ('docs/gis.md', 'hereda su `RecordReadScope` (ASM-095)', True),
    ('docs/glossary.md', '| Día hábil |', True),
    ('docs/adr/README.md', '| [0028](0028-qcs-confidenciales-con-recordreadscope.md) |', True),
]
bad = [f"{p}: {'falta' if want else 'sobra'} {s!r}" for p, s, want in checks
       if (s in open(p, encoding='utf-8').read()) != want]
print('\n'.join(bad) or 'ok')
raise SystemExit(1 if bad else 0)
PY
```

Esperado: salida 1 con las siete líneas.

- [ ] **Paso 2: La acción declarada `READ_CONFIDENTIAL` y la matriz de F4**

En `docs/security.md`, reemplazar:

```markdown
| `EDIT_ANY` | `engagement`, `engagement_participant`, `field_visit`, `field_observation`, `evidence` | Cambiar, transicionar, vincular o borrar un registro que creó otra persona. Agregar participantes u observaciones a una interacción o visita ajena no la exige | `EditAnyGuard` (`sgspe.scope`, §5.5) | Todo rol que escribe esos objetos, salvo `FIELD_AGENT` | RQ-SEC-008, ASM-069 |
```

por:

```markdown
| `EDIT_ANY` | `engagement`, `engagement_participant`, `field_visit`, `field_observation`, `evidence` | Cambiar, transicionar, vincular o borrar un registro que creó otra persona. Agregar participantes u observaciones a una interacción o visita ajena no la exige | `EditAnyGuard` (`sgspe.scope`, §5.5) | Todo rol que escribe esos objetos, salvo `FIELD_AGENT` | RQ-SEC-008, ASM-069 |
| `READ_CONFIDENTIAL` | `grievance`, `inquiry`, `stakeholder_request` | Leer toda QCS confidencial del objeto; quitarle la marca `confidential` o cambiar su responsable o quien la recibió; registrar una confidencial recibida por otra persona | `ConfidentialAttentionScope` (`sgspe.scope`, §9.6), `AttentionWriteGuard` y el filtro de destinatarios de `sgspe.notification` | `GRIEVANCE_MANAGER` | RQ-SEC-004, RQ-SEC-010, ASM-095, ASM-102 |
```

En `docs/security.md`, reemplazar:

```markdown
| `stakeholder`, `inquiry`, `stakeholder_request`, `social_risk` | `EXPORT` |
```

por:

```markdown
| `stakeholder`, `social_risk` | `EXPORT` |
| `inquiry`, `stakeholder_request` | `EXPORT`, `READ_CONFIDENTIAL` |
```

En `docs/security.md`, reemplazar:

```markdown
| `grievance` | `APPROVE`, `EXPORT` |
```

por:

```markdown
| `grievance` | `APPROVE`, `EXPORT`, `READ_CONFIDENTIAL` |
```

En `docs/security.md`, reemplazar:

```markdown
| `grievance` | — | R | RU `EXPORT` | CRU | R | R | CRU `EXPORT` | R | R | R | R | Ru `APPROVE` | R `EXPORT` | R |
| `inquiry` | — | R | R `EXPORT` | CRU | R | R | CRU `EXPORT` | R | R | R | R | R | R `EXPORT` | R |
| `stakeholder_request` | — | R | RU `EXPORT` | CRU | R | RU | CRU `EXPORT` | R | RU | R | R | R | R `EXPORT` | R |
```

por:

```markdown
| `grievance` | — | R | RU `EXPORT` | CRU | R | R | CRU `EXPORT` `READ_CONFIDENTIAL` | R | R | R | R | Ru `APPROVE` | R `EXPORT` | R |
| `inquiry` | — | R | R `EXPORT` | CRU | R | R | CRU `EXPORT` `READ_CONFIDENTIAL` | R | R | R | R | R | R `EXPORT` | R |
| `stakeholder_request` | — | R | RU `EXPORT` | CRU | R | RU | CRU `EXPORT` `READ_CONFIDENTIAL` | R | RU | R | R | R | R `EXPORT` | R |
```

En `docs/security.md`, reemplazar:

```markdown
- Las alertas las crea `WarningRulesJob` en `detected`; confirmarlas o descartarlas es siempre humano (RN-EWS-001).
```

por:

```markdown
- Las alertas las crean una persona o `WarningRulesJob` (JOB-04) en `detected`; confirmarlas o descartarlas es siempre
  humano (RN-EWS-001).
- CREATE sobre las QCS es solo de `COMMUNITY_RELATIONS_OFFICER` y `GRIEVANCE_MANAGER`: solo el equipo social registra
  QCS, sin canal público (ASM-104, UPS-25).
- `READ_CONFIDENTIAL` sobre las tres QCS es solo de `GRIEVANCE_MANAGER` (ASM-095): con ella lee toda QCS confidencial.
  Los demás roles con READ ven una confidencial solo si son su responsable o quien la recibió, y `APPROVER`, por su
  `APPROVE` sobre `grievance`, también una queja ALTA o CRITICA (§9.6).
- Nadie tiene D sobre las raíces de F4. `SOCIAL_RISK_MANAGER` borra medidas, impactos y reglas; una regla con alertas no
  se borra (`409 HAS_DEPENDENTS`) y se desactiva con `enabled` en falso.
```

- [ ] **Paso 3: §9.6: el `RecordReadScope` de las QCS confidenciales**

En `docs/security.md`, reemplazar:

```markdown
## 10. GeoServer
```

por:

```markdown
### 9.6 QCS confidenciales: `RecordReadScope` (F4)

wasichai 0.6.0 trae el SPI `RecordReadScope` (WAS-ADR-0048): para cada usuario y objeto, la app devuelve un criterio que
la plataforma aplica en listas, detalle, relacionados, historial, auditoría, transiciones, features GIS y agente, y que
no se aplica al `ADMIN` ni a la plataforma. F4 lo adopta **solo** para las QCS confidenciales (ASM-095, decisión del
usuario del 2026-10-09); el alcance por proyecto sigue en la opción A de CON-041 (§9.2) hasta que el checkpoint decida
UPS-01, que este mismo SPI resolvería del lado de SGSPE. La decisión, sus alternativas y sus consecuencias están en
[ADR-0028](adr/0028-qcs-confidenciales-con-recordreadscope.md).

| Quien llama | Ve una QCS confidencial (`grievance`, `inquiry`, `stakeholder_request`) |
|---|---|
| `ADMIN` (también `SUPER_ADMIN` y `ORGANIZATION_ADMIN`) | Siempre: wasichai no aplica los `RecordReadScope` al `ADMIN` |
| Con `READ_CONFIDENTIAL` sobre el objeto (`GRIEVANCE_MANAGER`) | Siempre |
| Su responsable (`owner_user_id` igual a quien llama) | Siempre |
| Quien la recibió (`received_by_user_id` igual a quien llama) | Siempre |
| Con `APPROVE` sobre `grievance` (`APPROVER`) | Solo una queja con `severity` ALTA o CRITICA, que debe resolver (RN-GRV-002) |
| Cualquier otro rol con READ | Nunca |

Una QCS sin `confidential` verdadero la ven todos los que tienen READ, como hasta F3.

- **Componente.** `ConfidentialAttentionScope` (`sgspe.scope`) implementa `RecordReadScope.criterion(user, definition)`:
  para un objeto que no es una de las tres QCS, o con `READ_CONFIDENTIAL` sobre el objeto, no restringe; si no, devuelve
  `confidential IS NOT TRUE OR owner_user_id = <quien llama> OR received_by_user_id = <quien llama>` y, en `grievance`
  con `APPROVE`, además `OR severity IN ('ALTA', 'CRITICA')`, con los nombres de columna de la definición y valores
  enlazados, nunca concatenados. Las acciones de quien llama salen de la misma consulta de permisos de
  `sgspe.common.Permissions`, memorizada por petición (`RequestMemo`).
- **Falla cerrado.** Si la definición no trae `confidential`, `owner_user_id` o `received_by_user_id`, o si la consulta
  de permisos falla, devuelve una condición que no admite ningún registro del objeto y deja una advertencia en el log
  sin datos del registro.
- **Escrituras.** Quien no lee una QCS no la cambia ni la transiciona: wasichai la trata como inexistente.
  `AttentionWriteGuard` cubre la marca y el responsable: el alta de una confidencial lleva `received_by_user_id` igual a
  quien escribe, salvo con `READ_CONFIDENTIAL` (`400 CONFIDENTIAL_RECEIVER_REQUIRED`), y quitar la marca o cambiar
  `owner_user_id` o `received_by_user_id` de una confidencial exige esa acción (`403 ACTION_REQUIRED`). Así quien
  registra siempre la ve, no la pierde al guardarla y los listeners, que escriben como esa persona, la pueden
  actualizar.
- **Vínculos desde otros objetos.** Una tarea, una evidencia, un compromiso (`origin_grievance`) o una pregunta pueden
  apuntar a una QCS que quien lee no ve: el id es visible y el registro no, y el portal muestra «Registro reservado».
  Los títulos de las tareas automáticas solo llevan el código de la QCS. Una evidencia vinculada a una QCS confidencial
  debe ser CONFIDENCIAL o DATOS PERSONALES (`409 EVIDENCE_NOT_RESTRICTED`).
- **Trabajos.** JOB-01, JOB-02, JOB-04 y `DeadlineNotices` leen como la plataforma, sin el alcance: lo que producen se
  filtra en la salida (avisos, ASM-102) o es agregado (conteos de JOB-04).
- **Indicadores, reporte y exportaciones.** Leen como quien llama y excluyen lo que no ve. El tablero `attention`, cada
  KPI-GRV, KPI-ENG-05 y el reporte unificado llevan `confidentialHidden: true` cuando quien llama no es `ADMIN` ni tiene
  `READ_CONFIDENTIAL` sobre los tres objetos; nunca el número de excluidas. EXTERNO excluye toda QCS confidencial,
  también para `GRIEVANCE_MANAGER` (§11.4).
- **Capa LYR-20.** Lee como quien llama: hereda el alcance (`gis.md` §2.2).

## 10. GeoServer
```

- [ ] **Paso 4: Minimización, salidas, pruebas y decisiones**

En `docs/security.md`, reemplazar:

```markdown
- **Riesgo residual del MVP:** la protección de quien plantea una QCS confidencial es parcial:
  - su `person` está restringida (§7.9), pero el vínculo `stakeholder` no lo está, y de él puede inferirse la persona
    cuando el grupo de interés la representa;
  - `confidential` es un dato que no restringe la lectura, porque la plataforma no restringe por registro (GAP-024);
  - con la opción A, todos los roles operativos menos `PROJECT_ADMIN`, `FIELD_AGENT` incluido, leen todas las QCS (§9.2);
  - el checkpoint puede restringir el vínculo `stakeholder` de las QCS a los roles que leen su `person`, a costa de que
    los demás no vean las QCS de cada grupo de interés (§15).
```

por:

```markdown
- **QCS confidenciales (ASM-095).** Una QCS con `confidential` verdadero la leen solo `ADMIN`, `GRIEVANCE_MANAGER`
  (`READ_CONFIDENTIAL`), su responsable, quien la recibió y, si es una queja ALTA o CRITICA, `APPROVER`; el alcance rige
  en todas las rutas de lectura de wasichai (§9.6). Su `person` está además restringida (§7.9).
- **Riesgo residual del MVP:** en una QCS **no** confidencial, el vínculo `stakeholder` no está restringido, y de él
  puede inferirse la persona cuando el grupo de interés la representa; con la opción A, todos los roles operativos menos
  `PROJECT_ADMIN`, `FIELD_AGENT` incluido, leen todas las QCS no confidenciales (§9.2). Quien teme esa exposición pide
  que su QCS se registre como confidencial o anónima.
```

En `docs/security.md`, reemplazar:

```markdown
- **Entregables:** se generan como la plataforma (domain-model §8.3), así que su contenido lo define su tipo y no los
  permisos de campo de quien lo pide. Por eso:
```

por:

```markdown
- **QCS y riesgos (F4, ASM-101).** `attention-register` exige READ y `EXPORT` sobre las tres QCS, y `risk-register`,
  sobre `social_risk`. INTERNO incluye las QCS confidenciales que quien exporta lee. EXTERNO excluye toda QCS
  confidencial, también para `GRIEVANCE_MANAGER`, y todo texto libre: en las QCS `description`, `response`, `resolution`
  y `requested_resource`; en los riesgos `risk_source`, `treatment_rationale`, `current_controls`, `monitoring_sources`
  y `close_reason`; en las revisiones `notes`; en medidas e impactos `description`. Tampoco lleva la persona, quien
  recibió, el responsable ni correos, y cita los grupos de interés por código `GI-`. PUBLICO cuenta sobre la grilla
  completa con `SmallCells`, y la hoja «Notas» de `attention-register` dice si el conteo incluye las confidenciales
  (`deliverables.md` §10).
- **Entregables:** se generan como la plataforma (domain-model §8.3), así que su contenido lo define su tipo y no los
  permisos de campo de quien lo pide. Por eso:
```

En `docs/security.md`, reemplazar:

```markdown
- **Documentos emitidos** de wasichai-documents (RQ-REP-029): no se filtran por permisos de campo, porque quien ve el
  documento lo ve entero [WAS-DOC api/rest.md] [WAS-ADR-0023]. Además, la copia congelada lleva **todos** los campos del
  registro y todas las columnas de cada tabla relacionada que nombra la plantilla, aunque no los imprima, y
  `GET /api/documents/{id}` la devuelve a todo rol con READ sobre el objeto (código de wasichai-documents 0.6.0, UPS-23).
  Regla: un tipo de documento solo se declara sobre un objeto sin campos restringidos y sin tablas relacionadas de
  objetos con campos restringidos; `test_model.py` lo comprueba. Por eso, hasta UPS-23, el acta de entrega (`delivery`
  restringe `recipients` y `conformity_by`) no es un documento emitido, y la lista de asistentes imprime el total y deja
  filas en blanco para los nombres y las firmas en lugar de la tabla de `engagement_participant`.
```

por:

```markdown
- **Documentos emitidos** de wasichai-documents (RQ-REP-029): no se filtran por permisos de campo, porque quien ve el
  documento lo ve entero [WAS-DOC api/rest.md] [WAS-ADR-0023]. Además, la copia congelada lleva **todos** los campos del
  registro y todas las columnas de cada tabla relacionada que nombra la plantilla, aunque no los imprima, y `GET
  /api/documents/{id}` la devuelve a todo rol con READ sobre el objeto (código de wasichai-documents 0.6.0, UPS-23).
  Regla: un tipo de documento solo se declara sobre un objeto sin campos restringidos y sin tablas relacionadas de
  objetos con campos restringidos; `test_model.py` lo comprueba. Por eso, hasta UPS-23, el acta de entrega (`delivery`
  restringe `recipients` y `conformity_by`) no es un documento emitido, y la lista de asistentes imprime el total y deja
  filas en blanco para los nombres y las firmas en lugar de la tabla de `engagement_participant`. Por la misma regla, el
  «Cargo de queja» y las «Carta de respuesta» de consultas y solicitudes esperan a UPS-23, porque las tres QCS
  restringen `person`: el cargo y la carta se entregan en papel y, firmados, se suben como evidencia FORMULARIO o CARTA
  vinculada a la QCS, con clasificación DATOS PERSONALES por defecto (ASM-098).
```

En `docs/security.md`, reemplazar:

```markdown
| Cuentas de servicio | No confirman ni descartan alertas, se rechazan en la administración y quedan auditadas con `serviceAccount` | Integración | F4 | RQ-SEC-014, RN-EWS-001 |
```

por:

```markdown
| Cuentas de servicio | No confirman ni descartan alertas, se rechazan en la administración y quedan auditadas con `serviceAccount` | Integración | F4 | RQ-SEC-014, RN-EWS-001 |
| `ConfidentialAttentionScopeTest` | La condición de `ConfidentialAttentionScope` por caso (con `READ_CONFIDENTIAL`, responsable, quien la recibió, `APPROVE` en una queja grave, otro rol), con valores enlazados, y que falla cerrado sin las columnas o sin la consulta de permisos | Unitaria | F4 | RQ-SEC-010, ASM-095 |
| `ConfidentialAttentionApiTest` | Un relacionista que no es responsable ni la recibió no ve una QCS confidencial en lista, detalle, historial, relacionados ni transiciones; el responsable, quien la recibió, el gestor de quejas y el `ADMIN` sí; el aprobador solo la grave; el tablero la excluye con `confidentialHidden`; EXTERNO la excluye también para el gestor; un aviso con datos no llega a quien no la ve y el aviso genérico del nivel 2 no lleva código, título ni enlace; quitar la marca sin la acción responde `403` | Integración | F4 | RQ-SEC-010, RQ-SEC-004, ASM-095, ASM-102 |
```

En `docs/security.md`, reemplazar:

```markdown
| Protección de quien plantea una QCS | GAP-005, GAP-024 | ¿Se restringe el vínculo `stakeholder` de las QCS a los roles que leen su `person`? | Sin restringir: riesgo residual del MVP (§11.3) |
```

por:

```markdown
| Protección de quien plantea una QCS | GAP-005, GAP-024, ASM-095 | ¿Se restringe el vínculo `stakeholder` de las QCS a los roles que leen su `person`? | Decidido el 2026-10-09: una QCS confidencial solo la leen quienes fija §9.6 (`RecordReadScope`, ADR-0028); el vínculo `stakeholder` de una QCS no confidencial sigue sin restringir (§11.3) |
```

En `docs/security.md`, reemplazar:

```markdown
| Brechas upstream | UPS-01, UPS-02, UPS-03, UPS-05, UPS-06, UPS-08, UPS-09, UPS-10, UPS-12, UPS-13, UPS-14, UPS-23, UPS-24 | Aprobar la apertura de los issues | Mitigaciones de este documento mientras tanto (GAP-028) |
```

por:

```markdown
| Brechas upstream | UPS-01, UPS-02, UPS-03, UPS-05, UPS-06, UPS-08, UPS-09, UPS-10, UPS-12, UPS-13, UPS-14, UPS-23, UPS-24; UPS-25 y UPS-26, candidatas posteriores al MVP sin issue | Aprobar la apertura de los issues | Mitigaciones de este documento mientras tanto (GAP-028) |
```

- [ ] **Paso 5: La capa LYR-20 y el glosario**

En `docs/gis.md`, reemplazar:

```markdown
| LYR-20 | Quejas, consultas y solicitudes | `location` de `grievance`, `inquiry` y `stakeholder_request`, o su `locality` o `community` | POINT | Registros de SGSPE | Datos personales (el punto puede ser una vivienda) | GeoJSON por API; mapa de calor; en EXTERNO se generaliza a la localidad | F4 (datos), F6 (capa) | [PRM-01 §6, §15]; RF-GRV-001 |
```

por:

```markdown
| LYR-20 | Quejas, consultas y solicitudes | `location` de `grievance`, `inquiry` y `stakeholder_request`, o su `locality` o `community` | POINT | Registros de SGSPE | Datos personales (el punto puede ser una vivienda); una QCS confidencial solo la ve quien la lee, porque la capa lee como quien llama y hereda su `RecordReadScope` (ASM-095) | GeoJSON por API; mapa de calor; en EXTERNO se generaliza a la localidad | F4 (datos), F6 (capa) | [PRM-01 §6, §15]; RF-GRV-001 |
```

En `docs/glossary.md`, reemplazar:

```markdown
| Absuelta | — | — | Consulta o queja respondida, con su `responded_at`; base del indicador "porcentaje absuelto" | [DOC-01 Tabla 9.5] | respondida | cerrada |
```

por:

```markdown
| Absuelta | — | — | Consulta o queja respondida, con su `responded_at`; base del indicador "porcentaje absuelto" | [DOC-01 Tabla 9.5] | respondida | cerrada |
| QCS confidencial | campo `confidential` de QCS | — | QCS que solo leen `ADMIN`, quien tiene `READ_CONFIDENTIAL` (`GRIEVANCE_MANAGER`), su responsable, quien la recibió y, si es una queja ALTA o CRITICA, `APPROVER` (ASM-095) | [PRM-01 §5] | reservada | anónima (sin persona ni grupo de interés) |
| Día hábil | `BusinessDays` (`sgspe.common`) | — | Día de lunes a viernes que no es un feriado nacional de la lista configurada de su año; sin la lista del año, de lunes a viernes. Mide el plazo de acuse de una queja (ASM-005, ASM-096) | [PRM-01 §17] | día laborable | día calendario |
```

- [ ] **Paso 6: El ADR de la confidencialidad (decisión 11)**

Crear `docs/adr/0028-qcs-confidenciales-con-recordreadscope.md`:

```markdown
# ADR-0028: QCS confidenciales con `RecordReadScope`

## Estado

Aceptado · 2026-10-09 · decisión del usuario para F4, que reabre la respuesta del checkpoint «sin restringir en el
MVP»; el alcance por proyecto de ADR-0006 no cambia (sigue la opción A de CON-041)

## Contexto

- Las QCS llevan la marca `confidential` desde su diseño ([domain-model.md §6.7](../domain-model.md)), pero la marca no
  restringía la lectura: la plataforma no tenía alcance por registro (GAP-024) y, con la opción A de CON-041, todos los
  roles operativos menos `PROJECT_ADMIN` leen todas las QCS ([security.md §9.2](../security.md)). El checkpoint lo
  aceptó como riesgo residual del MVP.
- Quien plantea una QCS confidencial puede temer represalias. Su `person` está restringida (`security.md` §7.9), pero
  el registro, con su descripción y su vínculo `stakeholder`, lo leía todo rol con READ.
- wasichai 0.6.0 trae el SPI `RecordReadScope` [WAS-ADR-0048]: para cada usuario y objeto, la app devuelve un criterio
  que la plataforma aplica en listas, detalle, relacionados, historial, auditoría, transiciones, features GIS y agente.
  No se aplica al `ADMIN` ni a la plataforma.
- El usuario decidió el 2026-10-09 (decisión 2 de la especificación de F4) que una QCS confidencial la lean solo
  `GRIEVANCE_MANAGER`, su responsable y `ADMIN`, y la amplió a quien la recibió y, en una queja ALTA o CRITICA, a
  `APPROVER`, que debe resolverla (RN-GRV-002).
- Ningún rol se nombra en el código (RQ-SEC-005): quién ve una QCS confidencial tiene que salir de `model/roles.json`.
- Al cerrarse un compromiso, `RequestClosureListener` cierra la solicitud aceptada que lo originó (RN-GRV-001, ASM-068)
  y corre como quien cierra. Si esa solicitud es confidencial y quien cierra no la lee, wasichai no la muestra y
  quedaría en `accepted` para siempre. wasichai no deja ser la plataforma dentro de una petición
  (`RecordService.asPlatform` lanza) ni aplicar una transición sin usuario (`WorkflowService.apply`), así que ni un
  trabajo de fondo podría aplicar `close_accepted`.

## Opciones consideradas

### `confidential` sin efecto en la lectura, como respondió el checkpoint

- A favor: nada propio; ninguna ruta de lectura cambia.
- En contra: no protege a quien plantea y contradice la decisión del usuario.

### Filtrar en SGSPE con `RecordQueryContributor` y guardas

- A favor: no depende de un SPI nuevo.
- En contra: `RecordQueryContributor` solo estrecha listas con su parámetro; el detalle, el historial, los
  relacionados, las features y el agente seguirían mostrando la QCS ([security.md §9.1](../security.md)).

### El alcance por proyecto completo, con la opción B de CON-041

- A favor: el mismo SPI resolvería UPS-01.
- En contra: cambia la lectura de todos los objetos y espera la decisión del checkpoint sobre UPS-01; es mucho más que
  lo que pide F4.

### Un `RecordReadScope` solo para las QCS confidenciales, con una acción declarada (elegida)

- A favor: rige en todas las rutas de lectura de wasichai; el resto del tenant sigue en la opción A; quién ve sale de
  `roles.json` (`READ_CONFIDENTIAL` y `APPROVE`), sin literales de rol.
- En contra: un componente propio que debe fallar cerrado; los tableros, el reporte y las exportaciones cuentan
  distinto según quién llama; los trabajos y los avisos tienen que filtrar lo que producen.

## Decisión

- **Quién ve una QCS confidencial** (`grievance`, `inquiry` o `stakeholder_request` con `confidential` verdadero):
  `ADMIN`, porque wasichai no le aplica los alcances; quien tiene la acción declarada `READ_CONFIDENTIAL` sobre el
  objeto (en `roles.json`, solo `GRIEVANCE_MANAGER`); su responsable (`owner_user_id`); quien la recibió
  (`received_by_user_id`); y, en una queja ALTA o CRITICA, quien tiene `APPROVE` sobre `grievance` (`APPROVER`). Nadie
  más (ASM-095). Una QCS no confidencial la ven todos los que tienen READ, como hasta F3.
- **Componente.** `ConfidentialAttentionScope` (`sgspe.scope`) implementa `RecordReadScope.criterion(user,
  definition)`. Fuera de las tres QCS, o con `READ_CONFIDENTIAL` sobre el objeto, no restringe. Si no, devuelve
  `confidential IS NOT TRUE OR owner_user_id = <quien llama> OR received_by_user_id = <quien llama>` y, en `grievance`
  con `APPROVE`, además `OR severity IN ('ALTA', 'CRITICA')`, con las columnas de la definición y valores enlazados,
  nunca concatenados. Las acciones de quien llama salen de `sgspe.common.Permissions`.
- **Falla cerrado.** Sin esas columnas en la definición, o si la consulta de permisos falla, devuelve una condición que
  no admite ningún registro del objeto y deja una advertencia en el log sin datos del registro.
- **Escrituras.** Quien no lee una QCS no la cambia ni la transiciona: wasichai la trata como inexistente.
  `AttentionWriteGuard` cubre la marca: el alta de una confidencial lleva como receptor a quien escribe, salvo con
  `READ_CONFIDENTIAL` (`400 CONFIDENTIAL_RECEIVER_REQUIRED`), y quitar la marca o cambiar el responsable o el receptor
  exige esa acción (`403 ACTION_REQUIRED`). Una evidencia vinculada a una QCS confidencial es CONFIDENCIAL o DATOS
  PERSONALES (`409 EVIDENCE_NOT_RESTRICTED`).
- **Trabajos, avisos y cifras.** JOB-01, JOB-02, JOB-04 y `DeadlineNotices` leen como la plataforma, sin el alcance, y
  filtran lo que producen: un aviso con datos solo llega a quien vería la QCS (ASM-102), el aviso genérico del nivel 2
  (`qcs_confidential_overdue`) no lleva datos del registro y JOB-04 solo cuenta. Los tableros, el reporte unificado y
  las exportaciones leen como quien llama y dicen `confidentialHidden` sin el número de excluidas; EXTERNO excluye toda
  QCS confidencial, también para `GRIEVANCE_MANAGER` (ASM-101).
- **Vínculos.** El id de una QCS que quien lee no ve es visible desde una tarea, una evidencia, un compromiso o una
  pregunta; el registro no. El portal muestra «Registro reservado».
- **La única excepción dentro de una petición: `ConfidentialRelease`.** `RequestClosureListener` corre como quien
  cierra el compromiso, dentro de `ConfidentialRelease` (`sgspe.common`): mientras dura el bloque,
  `ConfidentialAttentionScope` admite además las solicitudes cuyo `created_commitment` es ese compromiso, y solo ellas.
  La auditoría lleva el origen `listener:request-closure` [WAS-ADR-0050] y el motivo «RN-GRV-001: compromiso <código>
  cerrado», con el usuario de quien cerró; sigue en la transacción del cierre, que se revierte si la solicitud no se
  puede cerrar. Es el sustituto de una acción del sistema que wasichai no permite en una petición, aceptado por el
  usuario el 2026-10-09 (plan 4.2a, decisión 4). `ArchitectureTest` vigila que solo `RequestClosureListener` lo use.

## Consecuencias

### Positivas

- Una QCS confidencial queda protegida en todas las rutas de lectura de wasichai, no solo en las listas.
- Quién la ve se cambia en `roles.json`, sin tocar el código.
- El mismo SPI, del lado de SGSPE, resolvería UPS-01 si el checkpoint elige la opción B de CON-041.

### Negativas y riesgos

- Las cifras de los tableros y del reporte dependen de quién llama; `confidentialHidden` lo avisa sin decir cuántas.
- PUBLICO de `attention-register` solo es completo si lo genera quien ve las confidenciales, y «Notas» lo dice.
- KPI-ENG-05 cuenta de menos para quien no ve la QCS a la que se derivó una pregunta.
- Que wasichai trate como inexistente una QCS que no se lee también en las escrituras, y que el alta la devuelva a
  quien la recibió, lo prueba la primera tarea de integración del plan 4.2a (riesgo 1 de la especificación de F4).
- `ConfidentialRelease` es una puerta en el alcance: una sola, acotada a un objeto, un campo y un valor, y vigilada.

### Seguimiento

- Pruebas: `ConfidentialAttentionScopeTest` y `ConfidentialReleaseTest` (unitarias); `ConfidentialAttentionApiTest`,
  `StakeholderRequestWorkflowApiTest` (cierre de una solicitud confidencial por su compromiso), `QcsNoticesApiTest`,
  `AttentionDashboardApiTest` y `AttentionRegisterApiTest` (integración); `ArchitectureTest`.
- Si el checkpoint decide UPS-01 con la opción B de CON-041, este alcance se integra en el del proyecto.
- Se revierte quitando el `RecordReadScope` y volviendo al riesgo residual de `security.md` §11.3 (ASM-095).

## Referencias

- Requisitos: RF-GRV-001, RN-GRV-001, RN-GRV-002, RQ-SEC-004, RQ-SEC-005, RQ-SEC-010; CON-041; GAP-024; UPS-01;
  ASM-068, ASM-095, ASM-101, ASM-102.
- Documentos: [security.md §9.6, §11.3 y §11.4](../security.md), [workflows.md](../workflows.md) («Listeners»,
  «Avisos» y «Destinatarios por rol»), [api.md §4.6](../api.md), [dashboards.md §8](../dashboards.md),
  [deliverables.md §10](../deliverables.md), [ux/screens.md §8](../ux/screens.md).
- ADR de SGSPE: ADR-0006, ADR-0022.
- WAS-ADR-0042 (acciones declaradas por la app), WAS-ADR-0048 (`RecordReadScope`, el alcance de lectura que aporta la
  app), WAS-ADR-0050 (origen de cada escritura en la auditoría).
```

En `docs/adr/README.md`, reemplazar:

```markdown
| [0027](0027-if-match-con-saltos-exactos-de-derivados.md) | `If-Match` con saltos exactos de derivados y la comparación de wasichai | Aceptado | 2026-10-09 | 0038, 0040, 0051 |
```

por:

```markdown
| [0027](0027-if-match-con-saltos-exactos-de-derivados.md) | `If-Match` con saltos exactos de derivados y la comparación de wasichai | Aceptado | 2026-10-09 | 0038, 0040, 0051 |
| [0028](0028-qcs-confidenciales-con-recordreadscope.md) | QCS confidenciales con `RecordReadScope` | Aceptado | 2026-10-09 | 0042, 0048, 0050 |
```

- [ ] **Paso 7: Verificar**

Repetir la comprobación del Paso 1 y correr las compuertas de «Global Constraints».

Además:

```bash
cd model && python3 -c 'import design_docs as d; print(d.matrix()["grievance"]["GRIEVANCE_MANAGER"])'
cd .. && python3 -c 'import sys; print([n for n, l in enumerate(open(sys.argv[1], encoding="utf-8"), 1) if len(l.rstrip()) > 120])' \
  docs/adr/0028-qcs-confidenciales-con-recordreadscope.md
```

Esperado: `ok`, las compuertas en verde, `['READ', 'CREATE', 'UPDATE', 'EXPORT', 'READ_CONFIDENTIAL']` y `[]` (ninguna
línea del ADR de más de 120 columnas).

- [ ] **Paso 8: Commit**

```bash
git add docs/security.md docs/gis.md docs/glossary.md docs/adr/0028-qcs-confidenciales-con-recordreadscope.md docs/adr/README.md
git commit -F - <<'MSG'
docs: seguridad de la fase 4 con las QCS confidenciales

security.md suma READ_CONFIDENTIAL al catálogo y a GRIEVANCE_MANAGER, explica en §9.6 el
RecordReadScope de las QCS confidenciales con CON-041 todavía en la opción A, cambia el riesgo
residual de §11.3, agrega las salidas de F4 y sus pruebas; gis.md y el glosario lo reflejan, y
ADR-0028 registra la decisión con la excepción de ConfidentialRelease.

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_017MAgYcNauGp6cC3ekX1h7U
MSG
```

---

### Tarea 4: Modelo de datos, modelo de dominio y arquitectura

Cierra C7 (`grievance_ack_days`, `HolidayCalendar` y `BusinessDays`, la configuración de feriados), C10 en el modelo
(`early_warning.generator_key` y las notas de `early_warning_rule`), C15 en las fichas y C1 en el modelo de dominio;
suma `READ_CONFIDENTIAL` a las fichas y a §8.1.1, `engagement_question.referred_at`, los rangos de los parámetros de F4
y los listeners `RiskNoticeListener` y `WarningNoticeListener`.

**Archivos:**
- Modificar: `docs/database-model.md`, `docs/domain-model.md`, `docs/architecture.md`
- Probar: `model/test_design_docs.py` (fichas y totales), `model/test_model.py`

**Interfaces:**
- Consume: los IDs de la Tarea 1; los nombres `ConfidentialAttentionScope`, `ProjectLinkGuard`, `AttentionCounting`,
  `HolidayCalendar`, `BusinessDays`, `RiskLevels`, `RiskNoticeListener` y `WarningNoticeListener` de la especificación.
- Produce: las fichas que el plan 4.1 convierte en `model.json` (`generator_key` único de `early_warning`,
  `referred_at` derivado «Desde F4», acciones con `READ_CONFIDENTIAL`); la fila «Feriados» de `architecture.md` §10.2,
  que el plan 4.2a implementa con `sgspe.calendar.holidays`.

- [ ] **Paso 1: La comprobación falla**

```bash
python3 - <<'PY'
checks = [
    ('docs/database-model.md', '| `referred_at` | DATETIME |', True),
    ('docs/database-model.md', '| `generator_key` | TEXT | no | sí | Clave idempotente de una alerta', True),
    ('docs/database-model.md', '- **Acciones declaradas:** `APPROVE`, `EXPORT`, `READ_CONFIDENTIAL`.', True),
    ('docs/domain-model.md', '| `RiskNoticeListener` (`sgspe.notification`) |', True),
    ('docs/architecture.md', '| Feriados | `sgspe.calendar.holidays`', True),
    ('docs/database-model.md', '| `review_every_days` | INTEGER | no | — | Días entre revisiones, de 1 a 365', True),
]
bad = [f"{p}: {'falta' if want else 'sobra'} {s!r}" for p, s, want in checks
       if (s in open(p, encoding='utf-8').read()) != want]
print('\n'.join(bad) or 'ok')
raise SystemExit(1 if bad else 0)
PY
```

Esperado: salida 1 con las seis líneas.

- [ ] **Paso 2: Las fichas de `project`, las QCS y `engagement_question`**

En `docs/database-model.md`, reemplazar:

```markdown
| `grievance_ack_days` | INTEGER | no | — | Plazo de acuse de quejas, en días hábiles (ASM-005). Desde F4. Por defecto: 5. | RF-GRV-006 |
```

por:

```markdown
| `grievance_ack_days` | INTEGER | no | — | Plazo de acuse de quejas, en días hábiles: de lunes a viernes, menos los feriados nacionales configurados por año en `sgspe.calendar.holidays`; el día de recepción no cuenta (`BusinessDays`, ASM-005, ASM-096). Desde F4. Por defecto: 5. | RF-GRV-006 |
```

En `docs/database-model.md`, reemplazar:

```markdown
- Los parámetros y políticas llegan con el módulo que los usa (columna "Descripción"); `apply.py` agrega cada campo en
  su fase. Su `defaultValue` lo aplica el código de SGSPE, porque wasichai no lo aplica (§3.6, UPS-15).
```

por:

```markdown
- Los parámetros y políticas llegan con el módulo que los usa (columna "Descripción"); `apply.py` agrega cada campo en
  su fase. Su `defaultValue` lo aplica el código de SGSPE, porque wasichai no lo aplica (§3.6, UPS-15).
- Los parámetros de F4: `ProjectWriteGuard` rechaza con `400 OUT_OF_RANGE` un plazo o una cadencia
  (`grievance_ack_days`, `inquiry_response_days`, `grievance_resolution_days`, `risk_review_days`) menor que 1 o mayor
  que 365, y umbrales de riesgo fuera de 1 a 25 o que no crezcan estrictamente (`risk_medium_min_score` <
  `risk_high_min_score` < `risk_critical_min_score`).
```

En `docs/database-model.md`, reemplazar:

```markdown
| `ack_due_date` | DATE | no | — | (derivado) Plazo de acuse, en días hábiles (ASM-005). | RF-GRV-006 |
```

por:

```markdown
| `ack_due_date` | DATE | no | — | (derivado) Plazo de acuse: el n-ésimo día hábil posterior a la fecha de recepción en Lima (`BusinessDays`, ASM-005, ASM-096). En la queja, `response_due_date` queda siempre vacío (ASM-100). | RF-GRV-006 |
```

En `docs/database-model.md`, reemplazar:

```markdown
- **Acciones declaradas:** `APPROVE`, `EXPORT`.
- **Workflow:** WF-E1.
- **Fase:** F4 · Release 1 (MVP).
```

por:

```markdown
- **Acciones declaradas:** `APPROVE`, `EXPORT`, `READ_CONFIDENTIAL`.
- **Workflow:** WF-E1.
- **Fase:** F4 · Release 1 (MVP).
- Confidencialidad por registro (ASM-095): con `confidential` verdadero, la queja solo la leen `ADMIN`, quien tiene
  `READ_CONFIDENTIAL`, su responsable, quien la recibió y, si es ALTA o CRITICA, quien tiene `APPROVE`
  (`ConfidentialAttentionScope`, `security.md` §9.6). El alta de una confidencial lleva `received_by_user_id` igual a
  quien escribe.
- La queja mide `ack_due_date` en `received` y `resolution_due_date` después; `response_due_date` queda siempre vacío
  (ASM-100).
```

En `docs/database-model.md`, reemplazar:

```markdown
- **Acciones declaradas:** `EXPORT`.
- **Workflow:** WF-E2.
- **Fase:** F4 · Release 1 (MVP).
```

por:

```markdown
- **Acciones declaradas:** `EXPORT`, `READ_CONFIDENTIAL`.
- **Workflow:** WF-E2.
- **Fase:** F4 · Release 1 (MVP).
- Confidencialidad por registro (ASM-095): con `confidential` verdadero, la consulta solo la leen `ADMIN`, quien tiene
  `READ_CONFIDENTIAL`, su responsable y quien la recibió (`ConfidentialAttentionScope`, `security.md` §9.6). El alta de
  una confidencial lleva `received_by_user_id` igual a quien escribe.
```

En `docs/database-model.md`, reemplazar:

```markdown
- **Acciones declaradas:** `EXPORT`.
- **Workflow:** WF-E3.
- **Fase:** F4 · Release 1 (MVP).
```

por:

```markdown
- **Acciones declaradas:** `EXPORT`, `READ_CONFIDENTIAL`.
- **Workflow:** WF-E3.
- **Fase:** F4 · Release 1 (MVP).
- Confidencialidad por registro (ASM-095): con `confidential` verdadero, la solicitud solo la leen `ADMIN`, quien tiene
  `READ_CONFIDENTIAL`, su responsable y quien la recibió (`ConfidentialAttentionScope`, `security.md` §9.6). El alta de
  una confidencial lleva `received_by_user_id` igual a quien escribe.
- `response_due_date` y `deadline_status` quedan vacíos mientras GAP-005 no fije un plazo de solicitudes (ASM-100).
```

En `docs/database-model.md`, reemplazar:

```markdown
| `answered_at` | DATETIME | no | — | Momento de la respuesta posterior. | RF-ENG-003 |
```

por:

```markdown
| `answered_at` | DATETIME | no | — | Momento de la respuesta posterior. | RF-ENG-003 |
| `referred_at` | DATETIME | no | — | (derivado) Momento en que la pregunta se derivó a una QCS; lo escribe el endpoint de derivación con la marca, y con él `EngagementWriteGuard` la cuenta como atendida sin leer la QCS, que puede ser confidencial. Desde F4. | RN-ENG-005 |
```

En `docs/database-model.md`, reemplazar:

```markdown
- La derivación a `inquiry`, `stakeholder_request` o `grievance` se guarda en la QCS (`origin_question`) desde F4
  (RN-ENG-005).
```

por:

```markdown
- La derivación a `inquiry`, `stakeholder_request` o `grievance` se guarda en la QCS (`origin_question`) desde F4
  (RN-ENG-005), y la pregunta guarda `referred_at`: una pregunta ya derivada no se vuelve a derivar
  (`409 ALREADY_REFERRED`).
```

- [ ] **Paso 3: Las fichas de `early_warning_rule` y `early_warning`, y la cadencia propia de `social_risk`**

En `docs/database-model.md`, reemplazar:

```markdown
| `threshold` | DECIMAL | no | — | Umbral. | RF-EWS-004 |
| `window_days` | INTEGER | no | — | Ventana en días. | RF-EWS-004 |
```

por:

```markdown
| `threshold` | DECIMAL | no | — | Umbral entero; su sentido depende de `rule_kind`: un conteo de 1 en adelante, o un puntaje de 1 a 25 en NIVEL DE RIESGO (ASM-097). | RF-EWS-004 |
| `window_days` | INTEGER | no | — | Ventana en días de Lima que terminan hoy, de 1 a 365; vacía vale 7. Es también el silencio tras una alerta de la regla (ASM-097). | RF-EWS-004 |
```

En `docs/database-model.md`, reemplazar:

```markdown
| `early_warning_rule_project` | MANY_TO_ONE | `project` | `project` | no | Vacío = todo el tenant |
```

por:

```markdown
| `early_warning_rule_project` | MANY_TO_ONE | `project` | `project` | no | Vacío = regla del tenant, que un proyecto reemplaza con una propia del mismo tipo |
```

En `docs/database-model.md`, reemplazar:

```markdown
- **Workflow:** ninguno.
- **Fase:** F4 · Release 1 (MVP).

### `early_warning`
```

por:

```markdown
- **Workflow:** ninguno.
- **Fase:** F4 · Release 1 (MVP).
- Una regla sin `project` es del tenant: aplica a cada proyecto en `active` o `suspended`, salvo a los que tienen una
  regla propia del mismo tipo, que la reemplaza (ASM-097). `ProjectScopeGuard` acota solo las reglas con proyecto.
- `WarningWriteGuard` rechaza con `400 OUT_OF_RANGE` un `threshold` vacío, no entero o fuera del rango de su tipo, y un
  `window_days` fuera de 1 a 365. Qué cuenta cada tipo está en `workflows.md`, «Tipos de regla de JOB-04».
- Una regla con alertas no se borra (`409 HAS_DEPENDENTS`): se desactiva con `enabled` en falso, para no perder el
  origen de sus alertas.

### `early_warning`
```

En `docs/database-model.md`, reemplazar:

```markdown
| `location` | GEOMETRY (POINT) | no | — | Ubicación. | RF-EWS-004 |

| Relación | Tipo | Campo | Destino | Req. | Nota |
|---|---|---|---|---|---|
| `early_warning_project` | MANY_TO_ONE | `project` | `project` | sí | — |
```

por:

```markdown
| `location` | GEOMETRY (POINT) | no | — | Ubicación. | RF-EWS-004 |
| `generator_key` | TEXT | no | sí | Clave idempotente de una alerta creada por JOB-04: `warning_rule:<regla>:<proyecto>[:<riesgo>]:<fecha de Lima>`; vacía en una alerta creada por una persona. Solo la escribe JOB-04 (`GeneratorKeyGuard`, ASM-097). | RF-EWS-004 |

| Relación | Tipo | Campo | Destino | Req. | Nota |
|---|---|---|---|---|---|
| `early_warning_project` | MANY_TO_ONE | `project` | `project` | sí | — |
```

En `docs/database-model.md`, reemplazar:

```markdown
- **Únicos:** ninguno.
- **Índices declarados:** ninguno.
- **Acciones declaradas:** `CONFIRM_ALERT`.
```

por:

```markdown
- **Únicos:** `unique` en `generator_key`.
- **Índices declarados:** ninguno.
- **Acciones declaradas:** `CONFIRM_ALERT`.
```

En `docs/database-model.md`, reemplazar:

```markdown
- `WarningRulesJob` la crea en `detected`; `WarningWriteGuard` rechaza `confirmed` y `dismissed` sin usuario o desde una
  cuenta de servicio (RN-EWS-001, RQ-SEC-014).
```

por:

```markdown
- `WarningRulesJob` la crea en `detected`; `WarningWriteGuard` rechaza `confirmed` y `dismissed` sin usuario o desde una
  cuenta de servicio (RN-EWS-001, RQ-SEC-014).
- `generator_key` solo la escribe JOB-04: una persona que la fija, la cambia o la vacía recibe
  `403 GENERATOR_KEY_PLATFORM_ONLY`, como en `task`. Su único frena una carrera entre réplicas.
```

La cadencia propia de un riesgo va de 1 a 365 días, como la del proyecto (decisión del usuario del 2026-10-09; la aplica
`RiskWriteGuard` en el plan 4.2b). El plan 4.1 copia esta descripción a `model.json`; su etiqueta sigue siendo «Días entre
revisiones».

En `docs/database-model.md`, reemplazar:

```markdown
| `review_every_days` | INTEGER | no | — | Días entre revisiones; vacío = `risk_review_days` del proyecto. | RN-RSK-001 |
```

por:

```markdown
| `review_every_days` | INTEGER | no | — | Días entre revisiones, de 1 a 365 (`400 OUT_OF_RANGE` fuera de rango); vacío = `risk_review_days` del proyecto. | RN-RSK-001 |
```

- [ ] **Paso 4: El modelo de dominio: documentos, derivados, listeners, acciones y paquetes**

En `docs/domain-model.md`, reemplazar:

```markdown
- **Documentos emitidos** (wasichai-documents) no son objetos de SGSPE: son tipos de documento por objeto (catálogo "Tipo de
  documento administrativo"), emitidos congelados y con correlativo (RQ-REP-029) [WAS-ADR-0023]. Se emiten desde `delivery`
  (acta de entrega o de recepción), `engagement` (minuta y lista de asistentes), `grievance` (cargo de queja), `inquiry` y
  `stakeholder_request` (carta de respuesta), `stakeholder` (ficha de grupo de interés, tras UPS-23: `stance` es restringido) y `locality` (ficha de localidad). Un
  documento emitido no se filtra por permisos de campo y su copia congelada lleva el registro entero (UPS-23): solo se
  declara sobre objetos sin campos restringidos (`security.md` §11.4), y el acta de entrega (y la de recepción, AUT-04) y la ficha de grupo de interés esperan a UPS-23.
```

por:

```markdown
- **Documentos emitidos** (wasichai-documents) no son objetos de SGSPE: son tipos de documento por objeto (catálogo
  "Tipo de documento administrativo"), emitidos congelados y con correlativo (RQ-REP-029) [WAS-ADR-0023]. Se emiten
  desde `delivery` (acta de entrega o de recepción), `engagement` (minuta y lista de asistentes), `grievance` (cargo de
  queja), `inquiry` y `stakeholder_request` (carta de respuesta), `stakeholder` (ficha de grupo de interés, tras UPS-23:
  `stance` es restringido) y `locality` (ficha de localidad). Un documento emitido no se filtra por permisos de campo y
  su copia congelada lleva el registro entero (UPS-23): solo se declara sobre objetos sin campos restringidos
  (`security.md` §11.4), y el acta de entrega (y la de recepción, AUT-04), la ficha de grupo de interés, el cargo de
  queja y las cartas de respuesta (las tres QCS restringen `person`, ASM-098) esperan a UPS-23.
```

En `docs/domain-model.md`, reemplazar:

```markdown
- Efectos simples como automatizaciones de metadata: al llegar un compromiso a `approved` se crea su tarea de ejecución; al
  recibir una queja, la tarea de acuse; al crear una `delivery`, el acta de entrega o de recepción como documento emitido
  (RQ-REP-029), cuando se resuelva UPS-23. `workflows.md` los numera.
```

por:

```markdown
- Efectos simples como automatizaciones de metadata: al llegar un compromiso a `approved` se crea su tarea de ejecución;
  al recibir una queja, la tarea de acuse; al crear una `delivery`, el acta de entrega o de recepción como documento
  emitido (RQ-REP-029), cuando se resuelva UPS-23; también tras UPS-23, al acusar recibo de una queja, el cargo de
  queja, y al responder una consulta o declinar una solicitud por carta, la carta de respuesta (ASM-098). `workflows.md`
  los numera.
```

En `docs/domain-model.md`, reemplazar:

```markdown
| `ack_due_date`, `response_due_date` y `resolution_due_date` de QCS | `received_at` (en un registro reclasificado, el del primer registro de la cadena, ASM-072) más `grievance_ack_days` (días hábiles), `inquiry_response_days` o `grievance_resolution_days` del proyecto | `QcsDueDatesListener`; `DerivedFieldsJob` | RF-GRV-006, ASM-005, ASM-072 |
```

por:

```markdown
| `ack_due_date`, `response_due_date` y `resolution_due_date` de QCS | La fecha de recepción en Lima (`received_at`; en un registro reclasificado, el del primer registro de la cadena, ASM-072) más `grievance_ack_days` días hábiles (`BusinessDays`, ASM-096), `inquiry_response_days` o `grievance_resolution_days` del proyecto. La queja no usa `response_due_date`, y la solicitud no tiene plazo en el MVP (ASM-100) | `QcsDueDatesListener`; `DerivedFieldsJob` | RF-GRV-006, ASM-005, ASM-072, ASM-096 |
| `engagement_question.referred_at` | Momento en que la pregunta se derivó a una QCS | Endpoint de derivación, en su transacción | RN-ENG-005 |
```

En `docs/domain-model.md`, reemplazar:

```markdown
| `QcsDueDatesListener` (`sgspe.attention`) | Alta de `grievance`, `inquiry` o `stakeholder_request`, también la que crea una reclasificación | Los `*_due_date` del mismo registro, desde su `received_at`, que en una reclasificación es el del primer registro de la cadena (ASM-072) | UPDATE sobre ese objeto, con motivo porque es `requiresReason` | No falla el alta; `DerivedFieldsJob` completa |
```

por:

```markdown
| `QcsDueDatesListener` (`sgspe.attention`) | Alta de `grievance`, `inquiry` o `stakeholder_request`, también la que crea una reclasificación | Los `*_due_date` del mismo registro, desde su `received_at`, que en una reclasificación es el del primer registro de la cadena (ASM-072); el acuse, en días hábiles (`BusinessDays`, ASM-096) | UPDATE sobre ese objeto, con motivo porque es `requiresReason` | No falla el alta; `DerivedFieldsJob` completa |
```

En `docs/domain-model.md`, reemplazar:

```markdown
| `ApprovalNoticeListener` (`sgspe.notification`) | Envío a aprobación y salida del estado pendiente (WF-A, WF-C, WF-D) | Avisos de wasichai-notifications (`publish` y `resolve`), no registros de wasichai | READ sobre `project_assignment`, que lee como quien envía (ASM-070) | Un aviso mal formado se registra; un fallo de la base revierte la transición |
```

por:

```markdown
| `ApprovalNoticeListener` (`sgspe.notification`) | Envío a aprobación y salida del estado pendiente (WF-A, WF-C, WF-D); desde F4, una queja ALTA o CRITICA en `in_action` con `policy_high_severity_requires_approver` activo y su salida de `in_action` (ASM-102) | Avisos de wasichai-notifications (`publish` y `resolve`), no registros de wasichai | READ sobre `project_assignment`, que lee como quien envía (ASM-070) | Un aviso mal formado se registra; un fallo de la base revierte la transición |
| `RiskNoticeListener` (`sgspe.notification`) | Revisión no residual que sube `risk_level` a ALTO o CRITICO, en la transacción del endpoint de revisiones (desde F4) | `risk_escalated` de wasichai-notifications | READ sobre `project_assignment`, que lee como quien revisa | Un aviso mal formado se registra; un fallo de la base revierte la revisión |
| `WarningNoticeListener` (`sgspe.notification`) | Alta de `early_warning`, por una persona o por JOB-04 como la plataforma, y `start_triage` (desde F4) | `warning_new` de wasichai-notifications | READ sobre `project_assignment`, que lee como quien crea la alerta | Un aviso mal formado se registra y el alta sigue |
```

En `docs/domain-model.md`, reemplazar:

```markdown
| `grievance` | `APPROVE`, `EXPORT` | Resolver una queja ALTA o CRITICA (RN-GRV-002) y exportar |
| `inquiry`, `stakeholder_request`, `social_risk` | `EXPORT` | Exportar |
```

por:

```markdown
| `grievance` | `APPROVE`, `EXPORT`, `READ_CONFIDENTIAL` | Resolver una queja ALTA o CRITICA (RN-GRV-002), exportar y leer y gestionar las quejas confidenciales (ASM-095) |
| `inquiry`, `stakeholder_request` | `EXPORT`, `READ_CONFIDENTIAL` | Exportar y leer y gestionar las QCS confidenciales (ASM-095) |
| `social_risk` | `EXPORT` | Exportar |
```

En `docs/domain-model.md`, reemplazar:

```markdown
| `sgspe.common` | Marca de escritura `SgspeWrite`; guardas transversales de §7.1; envoltura de `RecordService` para el código propio; correlativos de `code`; `Idempotency-Key` con el contrato nativo (RNF-INT-002); precondición sobre `updated_at` con los saltos de `derived_version` (RNF-INT-003, ASM-035, ASM-091); `AmountFieldsGuard` (ASM-092), `GeneratorKeyGuard` y `DependentsDeleteGuard`; filtros estrictos con 422 (ASM-054); fechas en America/Lima y feriados (RNF-I18N-002); contrato de los recalculadores de campos derivados y `DerivedFieldsJob` (§7.4, §7.5); tabla fija `idempotency_key` con su migración y su purga por JOB-08 (`RetentionJob`) (desde F3, ASM-065) | Transversal | F1 | Son reglas y convenciones de API que la plataforma no ofrece |
```

por:

```markdown
| `sgspe.common` | Marca de escritura `SgspeWrite`; guardas transversales de §7.1; envoltura de `RecordService` para el código propio; correlativos de `code`; `Idempotency-Key` con el contrato nativo (RNF-INT-002); precondición sobre `updated_at` con los saltos de `derived_version` (RNF-INT-003, ASM-035, ASM-091); `AmountFieldsGuard` (ASM-092), `GeneratorKeyGuard` y `DependentsDeleteGuard`; filtros estrictos con 422 (ASM-054); fechas en America/Lima; desde F4, días hábiles con `HolidayCalendar` y `BusinessDays` (RNF-I18N-002, ASM-096) y el conteo de planteamientos de QCS `AttentionCounting`; contrato de los recalculadores de campos derivados y `DerivedFieldsJob` (§7.4, §7.5); tabla fija `idempotency_key` con su migración y su purga por JOB-08 (`RetentionJob`) (desde F3, ASM-065) | Transversal | F1 | Son reglas y convenciones de API que la plataforma no ofrece |
```

En `docs/domain-model.md`, reemplazar:

```markdown
| `sgspe.scope` | `ProjectScopeGuard`, `ProjectWriteGuard`, `AssignmentRoleGuard` (`role_name` de `project_assignment` contra los roles configurados, ASM-070) y `EditAnyGuard` (a quien no tiene `EDIT_ANY` sobre el objeto le rechaza cambiar, transicionar, vincular o borrar registros ajenos, ASM-069); lectura de `project_assignment`; `IssueDocumentFilter` (emitir un documento a mano pide lo mismo que cambiar el registro); adaptador de la extensión de alcance cuando exista | BC-01, BC-16 | F2 | wasichai no tiene alcance por proyecto (CON-041, UPS-01) |
```

por:

```markdown
| `sgspe.scope` | `ProjectScopeGuard`, `ProjectWriteGuard`, `AssignmentRoleGuard` (`role_name` de `project_assignment` contra los roles configurados, ASM-070) y `EditAnyGuard` (a quien no tiene `EDIT_ANY` sobre el objeto le rechaza cambiar, transicionar, vincular o borrar registros ajenos, ASM-069); lectura de `project_assignment`; `IssueDocumentFilter` (emitir un documento a mano pide lo mismo que cambiar el registro); desde F4, `ProjectLinkGuard` (mismo proyecto en las relaciones, `409 PROJECT_MISMATCH`) y `ConfidentialAttentionScope` (`RecordReadScope` de las QCS confidenciales, ASM-095); adaptador de la extensión de alcance cuando exista | BC-01, BC-16 | F2 | wasichai no tiene alcance por proyecto (CON-041, UPS-01) |
```

En `docs/domain-model.md`, reemplazar:

```markdown
| `sgspe.notification` | Destinatarios por `project_assignment.role_name` y la definición del workflow (ASM-070) y textos de los avisos; `ApprovalNoticeListener`; publica en wasichai-notifications | BC-14 | F3–F4 | El módulo guarda, entrega y purga los avisos; SGSPE decide quién y qué (ASM-012) |
```

por:

```markdown
| `sgspe.notification` | Destinatarios por `project_assignment.role_name` y la definición del workflow (ASM-070), con el filtro de las QCS confidenciales (ASM-102), y textos de los avisos; `ApprovalNoticeListener`; desde F4, `RiskNoticeListener` y `WarningNoticeListener`; publica en wasichai-notifications | BC-14 | F3–F4 | El módulo guarda, entrega y purga los avisos; SGSPE decide quién y qué (ASM-012) |
```

- [ ] **Paso 5: La arquitectura: paquetes, JOB-04, configuración y pruebas**

En `docs/architecture.md`, reemplazar:

```markdown
| `sgspe.common` | Marca de escritura `SgspeWrite`; guardas transversales (`DerivedFieldsGuard`, `TransitionReasonGuard`, `ExactlyOneGuard`, `UserReferenceGuard`, `FieldChangeReasonGuard`, `CommandTransitionGuard`); envoltura de `RecordService`; correlativos; `Idempotency-Key` de los comandos con la tabla fija `idempotency_key` y el contrato nativo (ASM-065) y su purga por JOB-08 (`RetentionJob`); precondición sobre `updated_at` con los saltos de `derived_version` (ADR-0027); `AmountFieldsGuard` (montos, ASM-092), `GeneratorKeyGuard` (`generator_key` solo de un trabajo) y `DependentsDeleteGuard`; los topes del multipart (`FILE_TOO_LARGE`, `PART_TOO_LARGE`); filtros estrictos con 422; fechas en America/Lima y feriados; valores por defecto de los parámetros (UPS-15); `DerivedFieldsJob` | Transversal | F1 |
```

por:

```markdown
| `sgspe.common` | Marca de escritura `SgspeWrite`; guardas transversales (`DerivedFieldsGuard`, `TransitionReasonGuard`, `ExactlyOneGuard`, `UserReferenceGuard`, `FieldChangeReasonGuard`, `CommandTransitionGuard`); envoltura de `RecordService`; correlativos; `Idempotency-Key` de los comandos con la tabla fija `idempotency_key` y el contrato nativo (ASM-065) y su purga por JOB-08 (`RetentionJob`); precondición sobre `updated_at` con los saltos de `derived_version` (ADR-0027); `AmountFieldsGuard` (montos, ASM-092), `GeneratorKeyGuard` (`generator_key` solo de un trabajo) y `DependentsDeleteGuard`; los topes del multipart (`FILE_TOO_LARGE`, `PART_TOO_LARGE`); filtros estrictos con 422; fechas en America/Lima; desde F4, `HolidayCalendar` (`@ConfigurationProperties("sgspe.calendar")`: los feriados nacionales por año y despliegue, validados al arrancar) y `BusinessDays` (días hábiles, ASM-096), y `AttentionCounting` (cada planteamiento de QCS cuenta una vez); valores por defecto de los parámetros (UPS-15); `DerivedFieldsJob` | Transversal | F1 |
```

En `docs/architecture.md`, reemplazar:

```markdown
| `sgspe.scope` | `ProjectScopeGuard`, `ProjectWriteGuard`, `AssignmentRoleGuard` (`role_name` de `project_assignment` contra los roles configurados, ASM-070) y `EditAnyGuard` (sin `EDIT_ANY`, solo se cambia o borra lo propio, ASM-069); `IssueDocumentFilter` (emitir un documento a mano pide lo mismo que cambiar el registro); lectura de `project_assignment`; adaptador de la extensión de alcance cuando exista | BC-01, BC-16 | F2 |
```

por:

```markdown
| `sgspe.scope` | `ProjectScopeGuard`, `ProjectWriteGuard`, `AssignmentRoleGuard` (`role_name` de `project_assignment` contra los roles configurados, ASM-070) y `EditAnyGuard` (sin `EDIT_ANY`, solo se cambia o borra lo propio, ASM-069); `IssueDocumentFilter` (emitir un documento a mano pide lo mismo que cambiar el registro); lectura de `project_assignment`; desde F4, `ProjectLinkGuard` (mismo proyecto en las relaciones) y `ConfidentialAttentionScope` (`RecordReadScope` de las QCS confidenciales, ASM-095); adaptador de la extensión de alcance cuando exista | BC-01, BC-16 | F2 |
```

En `docs/architecture.md`, reemplazar:

```markdown
| `sgspe.attention` | `QcsDueDatesListener` (plazos al recibir), `QcsTransitionDatesListener` (`resolved_date` y `closed_date`), reclasificación, aceptación de solicitudes, `RequestClosureListener` (cierra la solicitud al cerrarse su compromiso o, desde F5, su aporte, ASM-068), reporte unificado de QCS | BC-07 | F4 |
```

por:

```markdown
| `sgspe.attention` | `AttentionWriteGuard`, `QcsDueDatesListener` (plazos al recibir), `QcsTransitionDatesListener` (`resolved_date` y `closed_date`), reclasificación, aceptación de solicitudes, `RequestClosureListener` (cierra la solicitud al cerrarse su compromiso o, desde F5, su aporte, ASM-068), reporte unificado de QCS | BC-07 | F4 |
```

En `docs/architecture.md`, reemplazar:

```markdown
| `sgspe.risk` | Endpoint de revisiones (puntaje, nivel, tendencia y residual), `RiskWriteGuard` | BC-08 | F4 |
| `sgspe.warning` | `WarningRulesJob` (evaluador de reglas) y `WarningWriteGuard` (decisión humana) | BC-09 | F4 |
```

por:

```markdown
| `sgspe.risk` | Endpoint de revisiones (puntaje, nivel, tendencia y residual), `RiskLevels` (función pura), `RiskWriteGuard` | BC-08 | F4 |
| `sgspe.warning` | `WarningRulesJob` (un evaluador puro por cada uno de los seis tipos de regla, ASM-097) y `WarningWriteGuard` (decisión humana, reglas válidas) | BC-09 | F4 |
```

En `docs/architecture.md`, reemplazar:

```markdown
| `sgspe.notification` | Destinatarios por asignación y textos de los avisos sobre wasichai-notifications; `ApprovalNoticeListener` | BC-14 | F3–F4 |
```

por:

```markdown
| `sgspe.notification` | Destinatarios por asignación, con el filtro de las QCS confidenciales (ASM-102), y textos de los avisos sobre wasichai-notifications; `ApprovalNoticeListener`; desde F4, `RiskNoticeListener` y `WarningNoticeListener` | BC-14 | F3–F4 |
```

En `docs/architecture.md`, reemplazar:

```markdown
| JOB-04 `WarningRulesJob` | `sgspe.warning` | Evalúa las reglas de alerta y crea alertas en `detected` | Cada hora, configurable | F4 | RF-EWS-004, RN-EWS-001, ASM-006 |
```

por:

```markdown
| JOB-04 `WarningRulesJob` | `sgspe.warning` | Evalúa los seis tipos de regla por proyecto, con la regla del proyecto en lugar de la del tenant del mismo tipo, y crea alertas en `detected` con `generator_key`; nunca confirma ni aplica transiciones (`workflows.md`, «Tipos de regla de JOB-04») | Cada hora, configurable | F4 | RF-EWS-004, RN-EWS-001, ASM-006, ASM-097 |
```

En `docs/architecture.md`, reemplazar:

```markdown
| IA | Desactivada por defecto; se activa por organización | RQ-AI-009, ASM-015 |
```

por:

```markdown
| IA | Desactivada por defecto; se activa por organización | RQ-AI-009, ASM-015 |
| Feriados | `sgspe.calendar.holidays` con la lista de feriados nacionales del año en curso y del siguiente, verificada contra la norma publicada en El Peruano; operación agrega cada año el que sigue. Sin el año en curso, el arranque lo advierte y el acuse cuenta de lunes a viernes; una fecha que no es de su año o repetida impide arrancar | RNF-I18N-002, ASM-096 |
```

En `docs/architecture.md`, reemplazar:

```markdown
| Unitarias | Reglas puras: clasificación interés × poder, priorización, puntaje de riesgo, plazos en días hábiles, numeración de leyendas | JUnit, sin Docker | RNF-TST-001 |
```

por:

```markdown
| Unitarias | Reglas puras: clasificación interés × poder, priorización, puntaje de riesgo, plazos en días hábiles (`BusinessDaysTest`), evaluadores de reglas de alerta, numeración de leyendas | JUnit, sin Docker | RNF-TST-001 |
```

- [ ] **Paso 6: Verificar**

Repetir la comprobación del Paso 1 y correr las compuertas de «Global Constraints». Además:

```bash
cd model && python3 -c '
import design_docs as d
c = d.cards()
f = {x["name"]: x for x in c["engagement_question"].fields}["referred_at"]
print(f["phase"], f["derived"], c["early_warning"].unique, c["stakeholder_request"].actions)'
```

Esperado: `ok`, las compuertas en verde (los totales de `test_totales_de_las_fichas` no cambian) y
`F4 True ['generator_key'] ['EXPORT', 'READ_CONFIDENTIAL']`.

- [ ] **Paso 7: Commit**

```bash
git add docs/database-model.md docs/domain-model.md docs/architecture.md
git commit -F - <<'MSG'
docs: modelo de datos, dominio y arquitectura de la fase 4

Las fichas suman READ_CONFIDENTIAL en las QCS, engagement_question.referred_at,
early_warning.generator_key, las notas de las reglas de alerta y el rango de review_every_days; el
dominio y la arquitectura nombran HolidayCalendar, BusinessDays, AttentionCounting,
ConfidentialAttentionScope, ProjectLinkGuard y los listeners de avisos de riesgos y alertas.

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_017MAgYcNauGp6cC3ekX1h7U
MSG
```

---

### Tarea 5: Contratos de la API y exportaciones

Cierra C12 (contenido de `attention-register` y `risk-register`) y C13 en la API (`PROJECT_MISMATCH` en las relaciones
de F4), y escribe los contratos de la derivación, la reclasificación, la aceptación, el reporte unificado y las
revisiones de riesgo, con los códigos nuevos de §3.3.

**Archivos:**
- Modificar: `docs/api.md` (§3.3, §4.4, §4.6, §4.7, §4.9, §4.11, §4.13), `docs/deliverables.md` (§10)
- Probar: `tools/check_traceability.py`

**Interfaces:**
- Consume: ASM-095, ASM-097, ASM-099, ASM-101 y ASM-054 del análisis; §9.6 de `security.md` (Tarea 3), que citan sin
  depender de su texto.
- Produce: los códigos `ANONYMOUS_WITH_IDENTITY`, `CONFIDENTIAL_RECEIVER_REQUIRED`, `CONFORMITY_GIVEN`,
  `INVALID_TARGET`, `ALREADY_REFERRED`, `ASSESSMENT_OUT_OF_ORDER`, `FIRST_ASSESSMENT_NOT_RESIDUAL`,
  `RISK_THRESHOLDS_INVALID`, `MEASURE_OWNER_REQUIRED`, `RESIDUAL_ASSESSMENT_REQUIRED`, `HUMAN_DECISION_REQUIRED` y
  `EVIDENCE_NOT_RESTRICTED`, con su estado y su campo, que implementan 4.2a y 4.2b.

- [ ] **Paso 1: La comprobación falla**

```bash
python3 - <<'PY'
checks = [
    ('docs/api.md', '`409 ALREADY_REFERRED`', True),
    ('docs/api.md', '`409 FIRST_ASSESSMENT_NOT_RESIDUAL`', True),
    ('docs/api.md', '`200 { totals, byType, confidentialHidden, page }`', True),
    ('docs/api.md', '**`attention-register` (F4).**', True),
    ('docs/deliverables.md', '| PUBLICO | «Por nivel y categoría», «Por tratamiento y nivel» |', True),
]
bad = [f"{p}: {'falta' if want else 'sobra'} {s!r}" for p, s, want in checks
       if (s in open(p, encoding='utf-8').read()) != want]
print('\n'.join(bad) or 'ok')
raise SystemExit(1 if bad else 0)
PY
```

Esperado: salida 1 con las cinco líneas.

- [ ] **Paso 2: Los códigos de error de F4 en §3.3**

En `docs/api.md`, reemplazar:

```markdown
| `400` | `DERIVED_FIELD` (se envió un campo derivado), `REASON_REQUIRED` (la transición exige `X-Change-Reason`), `FIELD_REASON_REQUIRED` (cambiar `stage` o `exploration_phase` de un proyecto exige motivo), `EXACTLY_ONE` (debe venir exactamente uno de un grupo de campos), `USER_REFERENCE` (el par `<rol>_user_id` y `<rol>_user_email` no nombra a un usuario de la organización), `UNKNOWN_ROLE` (`role_name` de una asignación no es un rol declarado), `REPRESENTED_PEOPLE` (`represented_people_count` menor que 1), `POSITION_WITHOUT_PERSON` (`position_status`: un cargo sin persona solo puede estar `VACANTE` o `PREVISTO`), `INVALID_NUMBER` (un monto que no es un número) y `OUT_OF_RANGE` (un monto negativo, con más de 15 dígitos enteros o con más de 4 decimales tras quitar los ceros finales), sobre el campo: `exploration_campaign.social_support_cap`, `commitment.amount`, `delivery.delivered_amount` y `agreement.amount`, en el alta y en todo cambio de su valor (`AmountFieldsGuard`, ASM-092), `JUSTIFICATION_REQUIRED` (`origin_justification`: un compromiso que nace de un taller o de una reunión informativa, RN-ENG-001) y `DEPENDENCY_SELF` (`depends_on`: un compromiso no depende de sí mismo) |
```

por:

```markdown
| `400` | `DERIVED_FIELD` (se envió un campo derivado), `REASON_REQUIRED` (la transición exige `X-Change-Reason`), `FIELD_REASON_REQUIRED` (cambiar `stage` o `exploration_phase` de un proyecto exige motivo), `EXACTLY_ONE` (debe venir exactamente uno de un grupo de campos), `USER_REFERENCE` (el par `<rol>_user_id` y `<rol>_user_email` no nombra a un usuario de la organización), `UNKNOWN_ROLE` (`role_name` de una asignación no es un rol declarado), `REPRESENTED_PEOPLE` (`represented_people_count` menor que 1), `POSITION_WITHOUT_PERSON` (`position_status`: un cargo sin persona solo puede estar `VACANTE` o `PREVISTO`), `INVALID_NUMBER` (un monto que no es un número) y `OUT_OF_RANGE` (un monto negativo, con más de 15 dígitos enteros o con más de 4 decimales tras quitar los ceros finales), sobre el campo: `exploration_campaign.social_support_cap`, `commitment.amount`, `delivery.delivered_amount` y `agreement.amount`, en el alta y en todo cambio de su valor (`AmountFieldsGuard`, ASM-092), `JUSTIFICATION_REQUIRED` (`origin_justification`: un compromiso que nace de un taller o de una reunión informativa, RN-ENG-001) y `DEPENDENCY_SELF` (`depends_on`: un compromiso no depende de sí mismo). Desde F4: `ANONYMOUS_WITH_IDENTITY` (`person` o `stakeholder` de una QCS anónima), `CONFIDENTIAL_RECEIVER_REQUIRED` (`received_by_user_id`: el alta de una QCS confidencial, sin `READ_CONFIDENTIAL`, lleva como receptor a quien escribe), `INVALID_LINK` (`duplicate_of`: una queja no es duplicado de sí misma), `FIELD_REQUIRED` (`category` y `severity` para atender una queja; `resolution` y `response` para resolverla; `responded_at` y `response` en las transiciones que comunican una respuesta; probabilidad, impacto y `owner_user_id` al evaluar un riesgo; `close_reason` al cerrarlo; `immediate_response` y `consequences` al cerrar un incidente) y `OUT_OF_RANGE` también en los plazos y la cadencia del proyecto (de 1 a 365), sus umbrales de riesgo (de 1 a 25 y crecientes) y `threshold` y `window_days` de una regla de alerta (ASM-097) |
```

En `docs/api.md`, reemplazar:

```markdown
| `403` | `COMMAND_ONLY` (la transición es de comando), `NOT_ASSIGNED` (sin asignación vigente al proyecto), `ACTION_REQUIRED` (falta la acción declarada: `APPROVE` de un proyecto o de un compromiso y `VERIFY` de un compromiso, sobre `record`; `DOWNLOAD_RESTRICTED` para bajar la clasificación de una evidencia restringida, sobre `classification`), `NOT_OWN_RECORD` (el objeto declara `EDIT_ANY`, quien escribe no lo tiene y no creó el registro: no lo cambia, mueve, vincula ni borra; `record`), `SEGREGATION_OF_DUTIES` (quien aprueba un compromiso lo registró, `record`; quien lo verifica es su responsable, `owner_user_id`; RN-COM-005), `FIELD_NOT_READABLE` (`delivery.conformity_by`: registrar el cumplimiento necesita leer quiénes dieron la conformidad), `GENERATOR_KEY_PLATFORM_ONLY` (`generator_key`: una persona fija, cambia o vacía la clave de una tarea, también con un `PUT` que la omite; reenviarla tal como se leyó no es cambiarla), `OBJECT_NOT_READABLE` (quien escribe no lee un objeto que la regla necesita: `project_party`, `project`, `engagement_participant`, `engagement_question` o `area_of_influence`; desde F3 también `commitment`, `engagement`, `exploration_campaign`, `delivery`, `evidence`, `commitment_dependency` o `stakeholder`, y `agreement_land_parcel` al activar un acuerdo; `field` es el objeto) |
```

por:

```markdown
| `403` | `COMMAND_ONLY` (la transición es de comando), `NOT_ASSIGNED` (sin asignación vigente al proyecto), `ACTION_REQUIRED` (falta la acción declarada: `APPROVE` de un proyecto o de un compromiso y `VERIFY` de un compromiso, sobre `record`; `DOWNLOAD_RESTRICTED` para bajar la clasificación de una evidencia restringida, sobre `classification`), `NOT_OWN_RECORD` (el objeto declara `EDIT_ANY`, quien escribe no lo tiene y no creó el registro: no lo cambia, mueve, vincula ni borra; `record`), `SEGREGATION_OF_DUTIES` (quien aprueba un compromiso lo registró, `record`; quien lo verifica es su responsable, `owner_user_id`; RN-COM-005), `FIELD_NOT_READABLE` (`delivery.conformity_by`: registrar el cumplimiento necesita leer quiénes dieron la conformidad), `GENERATOR_KEY_PLATFORM_ONLY` (`generator_key`: una persona fija, cambia o vacía la clave de una tarea, también con un `PUT` que la omite; reenviarla tal como se leyó no es cambiarla), `OBJECT_NOT_READABLE` (quien escribe no lee un objeto que la regla necesita: `project_party`, `project`, `engagement_participant`, `engagement_question` o `area_of_influence`; desde F3 también `commitment`, `engagement`, `exploration_campaign`, `delivery`, `evidence`, `commitment_dependency` o `stakeholder`, y `agreement_land_parcel` al activar un acuerdo; `field` es el objeto). Desde F4: `ACTION_REQUIRED` también sin `READ_CONFIDENTIAL` al quitar `confidential` o cambiar `owner_user_id` o `received_by_user_id` de una QCS confidencial (sobre el campo), sin `APPROVE` en `resolve_high_severity` y sin `CONFIRM_ALERT` en `confirm` y `dismiss` (`record`); `HUMAN_DECISION_REQUIRED` (`record`: confirmar o descartar una alerta sin usuario o con una cuenta de servicio, RN-EWS-001); `COMMAND_ONLY` también en `close_reclassified` y `close_reclassified_*`, `accept` y `close_accepted` de WF-E3 y `assess` de WF-F; `GENERATOR_KEY_PLATFORM_ONLY` también en `early_warning.generator_key` |
```

En `docs/api.md`, reemplazar:

```markdown
| `409` | `HAS_DEPENDENTS` (el registro tiene dependientes obligatorios, o uno opcional de un objeto `requiresReason`, y no se borra; `field` es `<objeto>.<campo>` del primer dependiente), `FROZEN_MEMBERS` (los miembros de una versión `approved` o `superseded` del área de influencia no cambian; `field`: `area_of_influence`), `FROZEN_AREA` (su proyecto, tipo y número de versión tampoco; `field` es el campo que cambia), `WORKING_CRS_REQUIRED`, `PARTY_REQUIRED`, `FIELD_REQUIRED` (`scheduled_at`, `held_at`, `minutes` o `cancel_reason` de una interacción), `ATTENDANCE_REQUIRED`, `PENDING_QUESTIONS`, `MINUTES_REQUIRED`, `PROFILE_INCOMPLETE` (`caracterizar` exige `stakeholder_type`, `stakeholder_scope`, `inclusion_reason` y `owner_user_id`; `field` es el primero que falta), `DUPLICATE_RULE` (`stakeholder_type`: ya hay una fila de `prioritization_rule` para cualquier tipo de actor con ese proyecto, grupo de influencia, postura y ámbito). Desde F3: `WORKSHOP_COMMITMENT_BLOCKED` (`origin_engagement`: el proyecto no admite compromisos de talleres ni reuniones informativas), `DUE_DATE_RESCHEDULE_ONLY` (`due_date`: desde `approved`, también en `cancelled`, la fecha solo cambia con una reprogramación), `DEPENDENCY_CYCLE` y `DEPENDENCY_GRAPH_TOO_LARGE` (`depends_on`: la dependencia cerraría un ciclo, o encadena más de 500 compromisos y no se puede verificar), `PROJECT_MISMATCH` (el campo de un compromiso o de un candidato que apunta a un registro de otro proyecto), `DELIVERY_FROZEN` (`commitment`: la entrega de un compromiso cumplido, verificado, cerrado o cancelado no cambia ni se vincula), `FIELD_REQUIRED` también con los campos de la matriz de compromisos y el responsable (el primero que falta), `resource_type` al registrar el cumplimiento, `signed_date`, `effective_date` y `community` de un acuerdo, y `duplicate_of`; `STAKEHOLDER_REQUIRED` (`commitment_stakeholder`), `SENIOR_APPROVAL_REQUIRED` (`origin_engagement` si nace de una mesa de diálogo, `commitment_stakeholder` si lo pide un grupo de interés POLITICO), `INTERNAL_APPROVAL_REQUIRED` (`project`), `SUPPORT_CAP_EXCEEDED` y `CAP_DATA_INVALID` (`exploration_campaign`: la aprobación supera el tope de la campaña, o un monto guardado de la campaña no es válido y el tope no se puede sumar), `DELIVERY_REQUIRED` (`delivery`), `EVIDENCE_REQUIRED` (`evidence_delivery`), `BLOCKING_DEPENDENCY` (`commitment_dependency`), `LAND_PARCEL_REQUIRED` (`agreement_land_parcel`), `ASSEMBLY_REQUIRED` (`assembly_engagement`) y, por los comandos de evidencias, `EVIDENCE_IMMUTABLE` (`file`, `replaces` o `import_batch_id`), `EVIDENCE_NOT_DELETABLE` (`record`) y `EVIDENCE_FILE_IN_USE` (`file`: el archivo ya es de otra evidencia) |
```

por:

```markdown
| `409` | `HAS_DEPENDENTS` (el registro tiene dependientes obligatorios, o uno opcional de un objeto `requiresReason`, y no se borra; `field` es `<objeto>.<campo>` del primer dependiente), `FROZEN_MEMBERS` (los miembros de una versión `approved` o `superseded` del área de influencia no cambian; `field`: `area_of_influence`), `FROZEN_AREA` (su proyecto, tipo y número de versión tampoco; `field` es el campo que cambia), `WORKING_CRS_REQUIRED`, `PARTY_REQUIRED`, `FIELD_REQUIRED` (`scheduled_at`, `held_at`, `minutes` o `cancel_reason` de una interacción), `ATTENDANCE_REQUIRED`, `PENDING_QUESTIONS`, `MINUTES_REQUIRED`, `PROFILE_INCOMPLETE` (`caracterizar` exige `stakeholder_type`, `stakeholder_scope`, `inclusion_reason` y `owner_user_id`; `field` es el primero que falta), `DUPLICATE_RULE` (`stakeholder_type`: ya hay una fila de `prioritization_rule` para cualquier tipo de actor con ese proyecto, grupo de influencia, postura y ámbito). Desde F3: `WORKSHOP_COMMITMENT_BLOCKED` (`origin_engagement`: el proyecto no admite compromisos de talleres ni reuniones informativas), `DUE_DATE_RESCHEDULE_ONLY` (`due_date`: desde `approved`, también en `cancelled`, la fecha solo cambia con una reprogramación), `DEPENDENCY_CYCLE` y `DEPENDENCY_GRAPH_TOO_LARGE` (`depends_on`: la dependencia cerraría un ciclo, o encadena más de 500 compromisos y no se puede verificar), `PROJECT_MISMATCH` (el campo de un compromiso o de un candidato que apunta a un registro de otro proyecto), `DELIVERY_FROZEN` (`commitment`: la entrega de un compromiso cumplido, verificado, cerrado o cancelado no cambia ni se vincula), `FIELD_REQUIRED` también con los campos de la matriz de compromisos y el responsable (el primero que falta), `resource_type` al registrar el cumplimiento, `signed_date`, `effective_date` y `community` de un acuerdo, y `duplicate_of`; `STAKEHOLDER_REQUIRED` (`commitment_stakeholder`), `SENIOR_APPROVAL_REQUIRED` (`origin_engagement` si nace de una mesa de diálogo, `commitment_stakeholder` si lo pide un grupo de interés POLITICO), `INTERNAL_APPROVAL_REQUIRED` (`project`), `SUPPORT_CAP_EXCEEDED` y `CAP_DATA_INVALID` (`exploration_campaign`: la aprobación supera el tope de la campaña, o un monto guardado de la campaña no es válido y el tope no se puede sumar), `DELIVERY_REQUIRED` (`delivery`), `EVIDENCE_REQUIRED` (`evidence_delivery`), `BLOCKING_DEPENDENCY` (`commitment_dependency`), `LAND_PARCEL_REQUIRED` (`agreement_land_parcel`), `ASSEMBLY_REQUIRED` (`assembly_engagement`) y, por los comandos de evidencias, `EVIDENCE_IMMUTABLE` (`file`, `replaces` o `import_batch_id`), `EVIDENCE_NOT_DELETABLE` (`record`) y `EVIDENCE_FILE_IN_USE` (`file`: el archivo ya es de otra evidencia). Desde F4: `PROJECT_MISMATCH` en toda relación de un objeto de F4 hacia un registro de otro proyecto (`origin_engagement`, `origin_question`, `stakeholder`, `social_risk`, `incident`, `duplicate_of`, `reclassified_from_*`, `created_commitment`, `area_of_influence`, `key_theme`, `origin_incident`, `commitment`, `media_mention`, `early_warning` y `early_warning_rule`; una regla del tenant no discrepa), en `commitment.origin_grievance` y `commitment.social_risk` y en los vínculos `social_risk_stakeholder` e `incident_stakeholder` (`ProjectLinkGuard`); `SENIOR_APPROVAL_REQUIRED` (`record`: `resolve` de una queja ALTA o CRITICA con `policy_high_severity_requires_approver` activo); `CONFORMITY_GIVEN` (`complainant_conformity_given`: no se reabre una queja con conformidad); `STATE_MISMATCH` (`created_commitment`: `close_unfulfilled` con el compromiso fuera de `cancelled`); `MEASURE_OWNER_REQUIRED` (`mitigation_measure`: mitigar sin una medida con responsable); `RESIDUAL_ASSESSMENT_REQUIRED` (`risk_assessment`: monitorear sin una revisión residual); `HAS_DEPENDENTS` también en una regla de alerta con alertas; y, por los vínculos de evidencias, `EVIDENCE_NOT_RESTRICTED` (`classification`: una evidencia que no es CONFIDENCIAL ni DATOS PERSONALES no se vincula a una QCS confidencial) |
```

En `docs/api.md`, reemplazar:

```markdown
La emisión manual de un documento (`POST /api/objects/{object}/records/{id}/documents/{type}`, §2) no es una escritura
de `RecordService`, pero archiva el documento vigente del tipo: `IssueDocumentFilter` (`sgspe.scope`) le pide lo mismo
que a cambiar el registro, UPDATE sobre el objeto, una asignación vigente al proyecto del registro y, en los objetos que
declaran `EDIT_ANY`, esa acción o haber creado el registro (`security.md` §11.4), con la misma forma de error: `403`
`ACTION_REQUIRED` (`record`: falta UPDATE), `OBJECT_NOT_READABLE` (`record`: quien emite no lee el registro),
`NOT_ASSIGNED` (el campo de alcance: sin asignación vigente, o el proyecto no se resuelve) y `NOT_OWN_RECORD` (`record`:
sin `EDIT_ANY`, el registro es de otra persona). Un registro que no existe responde `403` `OBJECT_NOT_READABLE` a quien
no es `ADMIN`, igual que uno que no puede leer, para no revelar si existe; `ADMIN` recibe el `404` de wasichai.
```

por:

```markdown
Los comandos de F4 suman sus propios códigos, con la misma forma: `INVALID_TARGET`, `ALREADY_REFERRED`,
`STATE_MISMATCH`, `ASSESSMENT_OUT_OF_ORDER`, `FIRST_ASSESSMENT_NOT_RESIDUAL` y `RISK_THRESHOLDS_INVALID`; §4.4, §4.6 y
§4.7 dicen cuándo.

La emisión manual de un documento (`POST /api/objects/{object}/records/{id}/documents/{type}`, §2) no es una escritura
de `RecordService`, pero archiva el documento vigente del tipo: `IssueDocumentFilter` (`sgspe.scope`) le pide lo mismo
que a cambiar el registro, UPDATE sobre el objeto, una asignación vigente al proyecto del registro y, en los objetos que
declaran `EDIT_ANY`, esa acción o haber creado el registro (`security.md` §11.4), con la misma forma de error: `403`
`ACTION_REQUIRED` (`record`: falta UPDATE), `OBJECT_NOT_READABLE` (`record`: quien emite no lee el registro),
`NOT_ASSIGNED` (el campo de alcance: sin asignación vigente, o el proyecto no se resuelve) y `NOT_OWN_RECORD` (`record`:
sin `EDIT_ANY`, el registro es de otra persona). Un registro que no existe responde `403` `OBJECT_NOT_READABLE` a quien
no es `ADMIN`, igual que uno que no puede leer, para no revelar si existe; `ADMIN` recibe el `404` de wasichai.
```

- [ ] **Paso 3: Derivación, QCS, reporte y revisiones de riesgo**

En `docs/api.md`, reemplazar:

```markdown
| `POST` | `/api/sgspe/engagement-questions/{id}/referral` | Deriva una pregunta pendiente a una consulta, una solicitud o una queja, creada y vinculada (`origin_question`) en un solo comando | CREATE sobre el objeto destino; READ sobre `engagement_question` | `{ target, attributes }` con `target` igual a `inquiry`, `stakeholder_request` o `grievance` → `201 { question, created }` | `IK`, `CR` | F4 |
```

por:

```markdown
| `POST` | `/api/sgspe/engagement-questions/{id}/referral` | Deriva una pregunta pendiente a una consulta, una solicitud o una queja, creada y vinculada (`origin_question`) en un solo comando | CREATE sobre el objeto destino; READ sobre `engagement_question` | `{ target, attributes }` con `target` igual a `inquiry`, `stakeholder_request` o `grievance` → `201 { question, created }` | `IK`, `CR` | F4 |

- **Derivación (F4).** En una transacción, crea la QCS de `target` con `attributes` (sujetos a `AttentionWriteGuard`)
  más `origin_question`, `origin_engagement` (la interacción de la pregunta), el `project` de la interacción, `channel`
  REUNION y `received_at` igual al `held_at` de la interacción, y escribe `referred_at` en la pregunta con la marca
  `SgspeWrite`. `EngagementWriteGuard` cuenta como atendida una pregunta con `referred_at` (RN-ENG-005) sin leer la QCS,
  que puede ser confidencial. Errores: `409 ALREADY_REFERRED` (`record`) si la pregunta ya tiene `referred_at`; `409
  STATE_MISMATCH` (`engagement`) si la interacción no está entre `held` y `minutes_approved`; `400 INVALID_TARGET`
  (`target`).
```

En `docs/api.md`, reemplazar:

```markdown
| `GET` | `/api/sgspe/attention/report` | Reporte unificado de quejas, consultas y solicitudes: totales por tipo, estado, canal y categoría, y la lista | READ sobre los tres objetos; lo que no se puede leer sale como no disponible | `?project=&periodStart=&periodEnd=&channel=&category=` → `200 { totals, byType, page }` | — | F4 |
```

por:

```markdown
| `GET` | `/api/sgspe/attention/report` | Reporte unificado de quejas, consultas y solicitudes: totales por tipo, estado, canal y categoría, y la lista | READ sobre los tres objetos; lo que no se puede leer sale como no disponible | `?project=&periodStart=&periodEnd=&type=&channel=&category=&page=&size=` → `200 { totals, byType, confidentialHidden, page }` | — | F4 |
```

En `docs/api.md`, reemplazar:

```markdown
- **Aceptar una solicitud es responderla.** El endpoint escribe `response` y `responded_at` en la `stakeholder_request`, en la
  misma transacción que la transición a `accepted` y el alta del compromiso o del aporte. Si falta `response` o
  `respondedAt`, `AttentionWriteGuard` rechaza la transición con un `400` que nombra el campo y no se escribe nada. Con
  `responded_at` informado, la solicitud cuenta como absuelta (RF-GRV-004, RQ-DSH-023) (`workflows.md`, WF-E3).

### 4.7 BC-08 Riesgos sociales
```

por:

```markdown
- **Aceptar una solicitud es responderla.** El endpoint escribe `response` y `responded_at` en la `stakeholder_request`,
  en la misma transacción que la transición a `accepted` y el alta del compromiso o del aporte. Si falta `response` o
  `respondedAt`, `AttentionWriteGuard` rechaza la transición con un `400` que nombra el campo y no se escribe nada. Con
  `responded_at` informado, la solicitud cuenta como absuelta (RF-GRV-004, RQ-DSH-023) (`workflows.md`, WF-E3).
- **Reclasificación, contrato.** En una transacción, crea la QCS de `targetType` copiando los campos comunes (canal,
  `received_at` del primer registro de la cadena, quien recibió, anonimato, confidencialidad, categoría, descripción,
  prioridad, responsable, ubicación, grupo de interés, persona, localidad, comunidad, tema, interacción y pregunta de
  origen) más `reclassified_from_<tipo>`; `attributes` puede completar o cambiar los campos no derivados salvo
  `received_at` y los vínculos de reclasificación; aplica `close_reclassified_*` al original con el motivo de la
  petición. Errores: `400 INVALID_TARGET` (`targetType`: el mismo tipo, o uno que no es de los tres) y
  `409 STATE_MISMATCH` (`record`: el original no está en un estado con transición de reclasificación).
- **Aceptación, errores.** `400 INVALID_TARGET` (`creates`: en F4 solo `commitment`; el aporte llega en F5) y
  `409 STATE_MISMATCH` (`record`) fuera de `under_evaluation`.
- **Reporte unificado.** Lee como quien llama. `totals` y cada fila de `byType` traen `received`, `open`, `closed`,
  `rejected`, `withdrawn`, `absolved` y `overdue`, contados con `AttentionCounting` (cada planteamiento una vez,
  `dashboards.md` §3.3); `byType` suma `byState`, `byChannel` y `byCategory`. `page.items` lleva `type`, `id`, `code`,
  `state`, `channel`, `category`, `severity`, `receivedAt`, `deadlineStatus`, `dueDate` (el plazo que corre en el
  estado), `ownerEmail`, `respondedAt` y `anonymous`: nunca texto libre ni persona. Un tipo que quien llama no lee sale
  `available: false`. `confidentialHidden` es `true` cuando quien llama no es `ADMIN` ni tiene `READ_CONFIDENTIAL` sobre
  los tres objetos: un booleano, nunca el número de excluidas. Tope: `sgspe.exports.max-rows`
  (`400 TOO_MANY_RECORDS`); un parámetro desconocido es `422` (ASM-054).
- **Confidencialidad (ASM-095).** Una QCS confidencial que quien llama no lee no aparece en ninguna ruta (lista,
  detalle, relacionados, historial, transiciones, features ni agente): wasichai le aplica `ConfidentialAttentionScope`
  (`security.md` §9.6), y quien no la lee tampoco la cambia ni la transiciona. Un vínculo desde otro objeto (tarea,
  evidencia, compromiso o pregunta) deja ver el id, no el registro.
- **Mismo proyecto.** Toda relación de una QCS hacia un registro con alcance de proyecto, también la que escribe un
  comando, debe ser del mismo proyecto: si no, `409 PROJECT_MISMATCH` sobre el campo (`ProjectLinkGuard`).
- **Idempotencia.** La derivación, las reclasificaciones y la aceptación honran `Idempotency-Key` (§3.5).

### 4.7 BC-08 Riesgos sociales
```

En `docs/api.md`, reemplazar:

```markdown
El nivel sale de los umbrales del proyecto (RN-RSK-002, ASM-004). Mitigar, monitorear y cerrar son transiciones genéricas con
`RiskWriteGuard` (RN-RSK-003).
```

por:

```markdown
El nivel sale de los umbrales del proyecto (RN-RSK-002, ASM-004). Mitigar, monitorear y cerrar son transiciones
genéricas con `RiskWriteGuard` (RN-RSK-003).

- **Revisiones, contrato (ASM-099).** En una transacción: (1) valida `probability` e `impact` enteros de 1 a 5
  (`400 OUT_OF_RANGE`), `assessment_date` no futura en Lima (`400 OUT_OF_RANGE`) y no anterior a la última revisión del
  mismo tipo, residual o no (`409 ASSESSMENT_OUT_OF_ORDER`); un riesgo `closed` responde `409 STATE_MISMATCH`, y la
  primera revisión no puede ser residual (`409 FIRST_ASSESSMENT_NOT_RESIDUAL`); (2) calcula `score` = P × I y el nivel
  con `RiskLevels`: CRITICO desde `risk_critical_min_score`, ALTO desde `risk_high_min_score`, MEDIO desde
  `risk_medium_min_score` y BAJO por debajo; umbrales inválidos guardados antes de la regla de `ProjectWriteGuard`
  responden `409 RISK_THRESHOLDS_INVALID`; (3) calcula `trend` frente a la revisión anterior del mismo tipo (AUMENTA, SE
  MANTIENE o DISMINUYE según el puntaje; vacía en la primera); (4) crea la `risk_assessment` con quien revisa; (5)
  actualiza los valores vigentes (no residual) o los residuales, y `next_review_date` = `assessment_date` +
  (`review_every_days` o `risk_review_days`), con la marca; (6) en la primera revisión aplica `assess`, una transición
  de comando, con el motivo de la petición.
- La subida de `risk_level` a ALTO o CRITICO publica `risk_escalated` (`RiskNoticeListener`) y, a CRITICO, dispara
  AUT-11. Un cambio de umbrales del proyecto no recalcula los niveles guardados: los cambia la revisión siguiente.
- `risk_assessment` es `appendOnly` y `apiOnly`: una revisión se corrige con otra, nunca se cambia ni se borra.
```

En `docs/api.md`, reemplazar:

```markdown
| `POST` | `/api/sgspe/evidence/{id}/links` | Vincula la evidencia con un registro de un destino `evidence_<objeto>` | UPDATE sobre los dos registros; `EDIT_ANY` sobre `evidence` si la subió otra persona (`EditAnyGuard`) | `{ object, recordId }` → `204`; un objeto que no es destino, `400 INVALID_LINK` (`object`); una entrega cumplida o cancelada, `409 DELIVERY_FROZEN` | `CR` si el destino es `requiresReason` (sin él, `400 REASON_REQUIRED`) | F3 |
```

por:

```markdown
| `POST` | `/api/sgspe/evidence/{id}/links` | Vincula la evidencia con un registro de un destino `evidence_<objeto>` | UPDATE sobre los dos registros; `EDIT_ANY` sobre `evidence` si la subió otra persona (`EditAnyGuard`) | `{ object, recordId }` → `204`; un objeto que no es destino, `400 INVALID_LINK` (`object`); una entrega cumplida o cancelada, `409 DELIVERY_FROZEN`; desde F4 acepta `grievance`, `inquiry`, `stakeholder_request`, `social_risk`, `incident` y `media_mention`, y una evidencia que no es CONFIDENCIAL ni DATOS PERSONALES no se vincula a una QCS confidencial (`409 EVIDENCE_NOT_RESTRICTED`, `classification`) | `CR` si el destino es `requiresReason` (sin él, `400 REASON_REQUIRED`) | F3 |
```

- [ ] **Paso 4: Tableros de F4 y las exportaciones `attention-register` y `risk-register`**

En `docs/api.md`, reemplazar:

```markdown
| `{dashboard}` | Tablero | Requisito | Desde |
```

por:

```markdown
- Los tableros `attention` y `risks` (F4) son internos y no aplican celdas pequeñas. La respuesta de `attention`, la de
  cada KPI-GRV y la de KPI-ENG-05 llevan `confidentialHidden: true` cuando quien llama no es `ADMIN` ni tiene
  `READ_CONFIDENTIAL` sobre los tres objetos de QCS; nunca el número de excluidas (`dashboards.md` §8). KPI-ENG-06 a
  KPI-ENG-08 solo se piden por `GET /api/sgspe/dashboards/indicators/{code}` hasta F8.

| `{dashboard}` | Tablero | Requisito | Desde |
```

En `docs/api.md`, reemplazar:

```markdown
| `attention-register` | Quejas, consultas y solicitudes | `grievance`, `inquiry` y `stakeholder_request` | F4 |
| `risk-register` | Matriz de riesgos con su última revisión | `social_risk` | F4 |
```

por:

```markdown
| `attention-register` | Quejas, consultas y solicitudes, con las confidenciales solo en INTERNO (ASM-101) | `grievance`, `inquiry` y `stakeholder_request` | F4 |
| `risk-register` | Matriz de riesgos con sus revisiones, medidas e impactos (ASM-101) | `social_risk` | F4 |
```

En `docs/api.md`, reemplazar:

```markdown
### 4.14 BC-18 IA y conocimiento
```

por:

```markdown
**`attention-register` (F4).**

- **Permiso.** READ y `EXPORT` sobre los tres objetos (hoy `SOCIAL_MANAGER`, `GRIEVANCE_MANAGER` y `ANALYST`) y CREATE
  sobre `export_log`; sin alguno, `403` sin código: un registro unificado con un tipo de menos engañaría.
- **Cuerpo.** `{ filters, disclosureLevel, checklistConfirmed }`; `filters` es `{ project: string[], periodStart,
  periodEnd, type: string[], state: string[], channel, category, includeReclassified }`. El periodo filtra `received_at`
  en fechas de Lima; `state` toma estados de los tres workflows; `includeReclassified` (falso por defecto) agrega los
  originales reclasificados. Un filtro inválido es `400 INVALID_FILTER` sobre el filtro. Los proyectos siguen la regla
  de `stakeholder-register`.
- **Contenido.** Las hojas y columnas de cada nivel están en `deliverables.md` §10. INTERNO incluye las QCS
  confidenciales que quien exporta lee; EXTERNO excluye toda QCS confidencial (aunque quien exporta la lea), todo texto
  libre, la persona, quien recibió y el responsable, y exige `checklistConfirmed`; PUBLICO cuenta planteamientos
  (`AttentionCounting`) sobre la grilla completa con la marca `reservado` (ASM-088), sin totales, territorio ni grupo de
  interés, y su hoja «Notas» dice si el conteo incluye las confidenciales.
- **Tope.** `sgspe.exports.max-rows` por conjunto y 2 000 registros cuyos vínculos se leen uno por uno, con 200 por
  registro (`400 TOO_MANY_RECORDS` sobre `filters.project`).
- **Errores.** Los de `stakeholder-register`; `export_log` lleva `object_name` `grievance,inquiry,stakeholder_request` y
  `row_count` cuenta planteamientos.

**`risk-register` (F4).**

- **Permiso.** READ y `EXPORT` sobre `social_risk` (`SOCIAL_MANAGER`, `SOCIAL_RISK_MANAGER` y `ANALYST`) y CREATE sobre
  `export_log`.
- **Cuerpo.** `{ filters, disclosureLevel, checklistConfirmed }`; `filters` es `{ project: string[], state: string[],
  category: string[], riskLevel: string[], includeClosed }`, con `includeClosed` falso por defecto (`400 INVALID_FILTER`
  sobre el filtro).
- **Contenido.** Las hojas y columnas de cada nivel están en `deliverables.md` §10: INTERNO con «Matriz», «Revisiones»,
  «Medidas» e «Impactos»; EXTERNO sin texto libre ni correos, con los grupos de interés por código `GI-` y las medidas
  contadas en la «Matriz»; PUBLICO con conteos de riesgos activos sobre la grilla completa y `SmallCells`.
- **Tope y errores.** Los de `attention-register`; `export_log` lleva `object_name` `social_risk` y `row_count` cuenta
  riesgos.

### 4.14 BC-18 IA y conocimiento
```

- [ ] **Paso 5: El contenido por nivel de las dos exportaciones en `deliverables.md` §10**

En `docs/deliverables.md`, reemplazar:

```markdown
- **Documento de identidad de los solicitantes.** La matriz de DOC-01 lo pide [DOC-01 §9.3.1.2], pero en SGSPE es solo un campo
  restringido y nunca se exporta, en ningún nivel de divulgación: la matriz exportada no tiene esa columna (RF-COM-001,
  RQ-REP-014, RQ-REP-022, CON-014).
```

por:

```markdown
- **Documento de identidad de los solicitantes.** La matriz de DOC-01 lo pide [DOC-01 §9.3.1.2], pero en SGSPE es solo
  un campo restringido y nunca se exporta, en ningún nivel de divulgación: la matriz exportada no tiene esa columna
  (RF-COM-001, RQ-REP-014, RQ-REP-022, CON-014).
- **Texto libre (F4, ASM-101).** Son los campos LONG_TEXT: en las QCS `description`, `response`, `resolution` y
  `requested_resource`; en los riesgos `risk_source`, `treatment_rationale`, `current_controls`, `monitoring_sources` y
  `close_reason`; en las revisiones `notes`; en medidas e impactos `description`. EXTERNO no lleva ninguno.
- **Quejas, consultas y solicitudes (`attention-register`, F4, RF-GRV-005).** Exige READ y `EXPORT` sobre los tres
  objetos. Filtros, topes y errores en `api.md` §4.13; `export_log` cuenta planteamientos.

| Nivel | Hojas | Columnas y exclusiones |
|---|---|---|
| INTERNO | «Quejas», «Consultas», «Solicitudes», «Resumen» | Comunes: código, canal, recibida (fecha y hora de Lima), recibida por (correo), anónima, confidencial, categoría, prioridad, estado, situación de plazo (solo en estados con plazo), plazo que corre, responsable (correo), grupo de interés (nombre), persona (nombres y apellidos si los lee; «anónima»; «no disponible»), localidad, comunidad, tema, descripción, respuesta, respondida, cerrada, reclasificada de (código). Queja: severidad, acuse, plazo de resolución, resuelta, resolución, conformidad. Solicitud: medio, recurso pedido, compromiso creado (código). «Resumen»: los conteos del reporte unificado. Las confidenciales salen si quien exporta las ve |
| EXTERNO | Las mismas | Sin QCS confidenciales (aunque quien exporta las vea), sin texto libre, sin persona, sin quien recibió ni responsable; grupo de interés por código `GI-`; una hoja «Notas» dice que las confidenciales y el texto libre no se incluyen, sin conteos |
| PUBLICO | «Por tipo y estado», «Por tipo y canal», «Por tipo y categoría», «Absueltas por tipo» | Conteos de planteamientos (`AttentionCounting`) del periodo: estado agrupado (abiertas, cerradas, rechazadas, desistidas, declinadas), canal y categoría sobre la grilla completa; «Absueltas por tipo» con recibidas y absueltas por tipo. Cuenta lo que quien exporta lee, confidenciales incluidas si las ve; «Notas» dice si el conteo incluye las confidenciales (sí o no). Sin territorio ni grupo de interés; con la marca `reservado` (ASM-088) |

- **Matriz de riesgos (`risk-register`, F4).** Exige READ y `EXPORT` sobre `social_risk`; `export_log` cuenta riesgos.

| Nivel | Hojas | Columnas y exclusiones |
|---|---|---|
| INTERNO | «Matriz», «Revisiones», «Medidas», «Impactos» | «Matriz» con los campos de DOC-01 §9.3.2: código, título, fuente, categoría, tipo y naturaleza del impacto, P, I, puntaje, nivel, tendencia y fecha de la última revisión, decisión de tratamiento y su razón, acciones actuales y su adecuación, fuentes de monitoreo, P, I, puntaje y nivel residuales, próxima revisión, situación de revisión (solo en estados que la calculan), estado, responsable (correo), localidad, comunidad, área de influencia, conflicto local, tema clave, incidente de origen (código), grupos de interés (nombres), motivo de cierre. «Revisiones»: todas, con quien revisó. «Medidas» e «Impactos» completos |
| EXTERNO | «Matriz», «Revisiones», «Impactos» | Sin texto libre ni correos; grupos de interés por código `GI-`; la «Matriz» suma medidas totales, completadas y atrasadas en lugar de la hoja «Medidas»; «Impactos» con actividad, tipo, naturaleza, signo y riesgo |
| PUBLICO | «Por nivel y categoría», «Por tratamiento y nivel» | Conteos de riesgos activos sobre la grilla completa (nivel × categoría; decisión de tratamiento, con «sin decidir», × nivel), con la marca `reservado` (ASM-088) |
```

- [ ] **Paso 6: Verificar**

Repetir la comprobación del Paso 1 y correr las compuertas de «Global Constraints». Además, cada código nuevo de la
especificación §6 aparece en `api.md`:

```bash
for c in ANONYMOUS_WITH_IDENTITY CONFIDENTIAL_RECEIVER_REQUIRED CONFORMITY_GIVEN INVALID_TARGET ALREADY_REFERRED \
  ASSESSMENT_OUT_OF_ORDER FIRST_ASSESSMENT_NOT_RESIDUAL RISK_THRESHOLDS_INVALID MEASURE_OWNER_REQUIRED \
  RESIDUAL_ASSESSMENT_REQUIRED HUMAN_DECISION_REQUIRED EVIDENCE_NOT_RESTRICTED; do
  grep -q "\`$c\`" docs/api.md || echo "falta $c"; done; echo listo
```

Esperado: `ok`, las compuertas en verde y solo `listo`.

- [ ] **Paso 7: Commit**

```bash
git add docs/api.md docs/deliverables.md
git commit -F - <<'MSG'
docs: contratos de la API y exportaciones de la fase 4

api.md suma los códigos de error de F4, los contratos de la derivación, la reclasificación, la
aceptación, el reporte unificado y las revisiones de riesgo, y attention-register y risk-register;
deliverables.md §10 fija las hojas y columnas de cada nivel.

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_017MAgYcNauGp6cC3ekX1h7U
MSG
```

---

### Tarea 6: Tableros y pantallas

Cierra C14 (los tableros son internos, sin celdas pequeñas, y excluyen lo que quien llama no ve, con
`confidentialHidden`) y C11 (la ficha de consulta y solicitud pasa a S57; fichas S58 a S61 de menciones, señales, reglas
e impactos; las medidas dentro de S40; «Crear compromiso desde esta queja» abre S30 con `origin_grievance`; el
interruptor de confidencialidad de S37 y «Registro reservado»), quita de S36 y S57 la emisión de documentos (C1) y da al
tablero de riesgos el id S62, porque S38 es el registro y matriz (decisión 10).

**Archivos:**
- Modificar: `docs/dashboards.md` (§3.3, §4.3, §4.5, §8), `docs/ux/screens.md` (§8, §9, §12, §14, §16 e introducción),
  `docs/ux/README.md` (etapa 3b y páginas 11 y 16 del lienzo), `docs/ux/ux-architecture.md` (§9, inventario)
- Probar: `tools/check_traceability.py`

**Interfaces:**
- Consume: ASM-095 a ASM-104; «Tipos de regla de JOB-04» (Tarea 2), que S60 cita por su nombre.
- Produce: los ids S57 a S62 y los textos que implementa el plan 4.3.

- [ ] **Paso 1: La comprobación falla**

```bash
python3 - <<'PY'
checks = [
    ('docs/dashboards.md', '- **QCS confidenciales (F4, ASM-095).**', True),
    ('docs/ux/screens.md', '### S57 · Ficha de consulta y ficha de solicitud', True),
    ('docs/ux/screens.md', '### S61 · Impactos potenciales', True),
    ('docs/ux/screens.md', '### S36 · Ficha de consulta y ficha de solicitud', False),
    ('docs/ux/README.md', 'Fichas de consulta y de solicitud (S57)', True),
    ('docs/ux/screens.md', '### S62 · Tablero de riesgos', True),
    ('docs/ux/screens.md', '### S38 · Tablero de riesgos', False),
    ('docs/ux/ux-architecture.md', '| S62 | Tablero de riesgos | `/risks` | F4, F8 | E3 |', True),
]
bad = [f"{p}: {'falta' if want else 'sobra'} {s!r}" for p, s, want in checks
       if (s in open(p, encoding='utf-8').read()) != want]
print('\n'.join(bad) or 'ok')
raise SystemExit(1 if bad else 0)
PY
```

Esperado: salida 1 con seis líneas «falta» y dos «sobra».

- [ ] **Paso 2: Tableros: conteo de planteamientos, confidencialidad y celdas pequeñas**

En `docs/dashboards.md`, reemplazar:

```markdown
  - El planteamiento cuenta una sola vez, como el último registro de la cadena: los originales a los que apunta otra QCS por
    `reclassified_from_grievance`, `reclassified_from_inquiry` o `reclassified_from_request` no cuentan como recibidos, cerrados
    ni atendidos.
```

por:

```markdown
  - El planteamiento cuenta una sola vez, como el último registro de la cadena: los originales a los que apunta otra QCS
    por `reclassified_from_grievance`, `reclassified_from_inquiry` o `reclassified_from_request` no cuentan como
    recibidos, cerrados ni atendidos.
  - Lo cuenta `AttentionCounting` (`sgspe.common`), la misma función pura en el reporte unificado, los KPI-GRV, la
    exportación PUBLICO de `attention-register` y la regla CONTEO DE QCS de JOB-04.
```

En `docs/dashboards.md`, reemplazar:

```markdown
  `due_soon_days` del proyecto, 7 días por defecto (ASM-006, RN-WFL-001).
```

por:

```markdown
  `due_soon_days` del proyecto, 7 días por defecto (ASM-006, RN-WFL-001); en una QCS, POR VENCER empieza a lo sumo a la
  mitad del plazo (ASM-100).
```

En `docs/dashboards.md`, reemplazar:

```markdown
| KPI-ENG-05 | #9 Inquietudes resueltas | Porcentaje de inquietudes resueltas en las reuniones informativas | Preguntas absueltas ÷ preguntas efectuadas [DOC-01 Tabla 9.5], sobre las `engagement_question` de reuniones informativas realizadas en el periodo. Absuelta: `answered_on_site` o con `answered_at`. Desde F4, una pregunta derivada cuenta cuando la QCS a la que se derivó tiene `responded_at`; si esa QCS se reclasificó, cuando lo tiene el último registro de la cadena (§3.3) | `engagement_question.answered_on_site`, `answered_at`, `engagement`; `origin_question`, `responded_at` y vínculos `reclassified_from_*` de las QCS | Proyecto, periodo, territorio; desglose por tema | RQ-DSH-014, RF-ENG-003, RN-ENG-005 | Vivo | F2 |
```

por:

```markdown
| KPI-ENG-05 | #9 Inquietudes resueltas | Porcentaje de inquietudes resueltas en las reuniones informativas | Preguntas absueltas ÷ preguntas efectuadas [DOC-01 Tabla 9.5], sobre las `engagement_question` de reuniones informativas realizadas en el periodo. Absuelta: `answered_on_site` o con `answered_at`. Desde F4, una pregunta derivada cuenta cuando la QCS a la que se derivó tiene `responded_at`; si esa QCS se reclasificó, cuando lo tiene el último registro de la cadena (§3.3). Lee como quien llama: una pregunta derivada a una QCS confidencial que no lee no cuenta como absuelta, y la respuesta lleva `confidentialHidden` (§8) | `engagement_question.answered_on_site`, `answered_at`, `engagement`; `origin_question`, `responded_at` y vínculos `reclassified_from_*` de las QCS | Proyecto, periodo, territorio; desglose por tema | RQ-DSH-014, RF-ENG-003, RN-ENG-005 | Vivo | F2 |
```

En `docs/dashboards.md`, reemplazar:

```markdown
Los indicadores de QCS leen los tres objetos: `grievance`, `inquiry` y `stakeholder_request`; no hay un objeto "QCS" genérico
(§6.7 del modelo de dominio).
```

por:

```markdown
Los indicadores de QCS leen los tres objetos: `grievance`, `inquiry` y `stakeholder_request`; no hay un objeto "QCS"
genérico (§6.7 del modelo de dominio). Leen como quien llama: una QCS confidencial que no lee no cuenta (ASM-095), y la
respuesta de cada KPI-GRV y del tablero `attention` lo dice con `confidentialHidden` (§8).
```

En `docs/dashboards.md`, reemplazar:

```markdown
| KPI-GRV-08 | Desgloses de QCS | Cómo se reparten las QCS recibidas y abiertas | KPI-GRV-01 y KPI-GRV-02, con su regla de reclasificación, por severidad (`grievance.severity`), categoría (`category`), territorio (`locality`, `community`), grupo de interés (`stakeholder`; las anónimas aparte) y canal, los 10 primeros más "Otros" | `severity`, `category`, `locality`, `community`, `stakeholder`, `anonymous`, `channel` | Proyecto, periodo, territorio | RQ-DSH-024 | Vivo | F4 |
```

por:

```markdown
| KPI-GRV-08 | Desgloses de QCS | Cómo se reparten las QCS recibidas y abiertas | KPI-GRV-01 y KPI-GRV-02, con su regla de reclasificación, por severidad (`grievance.severity`), categoría (`category`), territorio (`locality`, `community`), grupo de interés (`stakeholder`; las anónimas aparte) y canal, los 10 primeros más "Otros". El tablero es interno y no aplica celdas pequeñas: lee como quien llama, así que las QCS confidenciales que no ve ya quedan fuera (§8) | `severity`, `category`, `locality`, `community`, `stakeholder`, `anonymous`, `channel` | Proyecto, periodo, territorio | RQ-DSH-024 | Vivo | F4 |
```

En `docs/dashboards.md`, reemplazar:

```markdown
- **Registros propios.** Desde ASM-069, `FIELD_AGENT` no usa `own_records_only`: la guarda de `EDIT_ANY` limita lo que
  actualiza, no lo que lee (RQ-SEC-008). Si un rol usara `own_records_only`, la plataforma filtraría en silencio sus lecturas
  [WAS-ADR-0020] y sus cifras serían parciales. `security.md` decide qué roles ven tableros.
```

por:

```markdown
- **QCS confidenciales (F4, ASM-095).** Los KPI-GRV, el tablero `attention` y KPI-ENG-05 leen como quien llama, así que
  excluyen las QCS confidenciales que la persona no ve (`security.md` §9.6). Su respuesta lleva `confidentialHidden:
  true` cuando quien llama no es `ADMIN` ni tiene `READ_CONFIDENTIAL` sobre los tres objetos: un booleano, nunca el
  número de excluidas, y la interfaz avisa que la cifra puede no incluirlas.
- **Celdas pequeñas.** Los tableros son internos y no aplican `SmallCells`: la supresión de celdas de menos de 5 rige
  solo en las salidas PUBLICO (ASM-088). Un desglose de KPI-GRV-08 por grupo de interés o canal muestra lo que quien
  llama ya puede leer registro por registro.
- **Registros propios.** Desde ASM-069, `FIELD_AGENT` no usa `own_records_only`: la guarda de `EDIT_ANY` limita lo que
  actualiza, no lo que lee (RQ-SEC-008). Si un rol usara `own_records_only`, la plataforma filtraría en silencio sus
  lecturas [WAS-ADR-0020] y sus cifras serían parciales. `security.md` decide qué roles ven tableros.
```

- [ ] **Paso 3: Pantallas de QCS: S35, S37, S36 y la ficha nueva S57**

En `docs/ux/screens.md`, reemplazar:

```markdown
Fichas de implementación de las pantallas del portal (`sgspe-ui`). Cubren el inventario completo de
[`ux-architecture.md`](ux-architecture.md) §9: la **etapa 2** (proyectos y Project 360, territorio, grupos de interés,
relacionamiento, compromisos y acuerdos: S06 a S26 y S28 a S33), la **parte 3a** (quejas, consultas y solicitudes; riesgos y
alerta temprana; programas e inversión; documentos, tareas y aprobaciones: S35 a S48 y la ficha de incidente) y la **parte 3b**
(tableros, capa de IA, inicio y transversales, campo sin conexión: S01, S02, S04, S05, S27, S34, S38, S49 a S55), y S56
(gestionar vistas, de las respuestas del usuario a Q1, Q3, Q6 y Q7). La búsqueda
global (S03) está en la etapa 1 (`design-system.md` y `ux-architecture.md` §7.3).
```

por:

```markdown
Fichas de implementación de las pantallas del portal (`sgspe-ui`). Cubren el inventario completo de
[`ux-architecture.md`](ux-architecture.md) §9: la **etapa 2** (proyectos y Project 360, territorio, grupos de interés,
relacionamiento, compromisos y acuerdos: S06 a S26 y S28 a S33), la **parte 3a** (quejas, consultas y solicitudes;
riesgos y alerta temprana; programas e inversión; documentos, tareas y aprobaciones: S35 a S48, S57 a S61 y la ficha de
incidente) y la **parte 3b** (tableros, capa de IA, inicio y transversales, campo sin conexión: S01, S02, S04, S05, S27,
S34, S49 a S55 y S62, el tablero de riesgos), y S56 (gestionar vistas, de las respuestas del usuario a Q1, Q3, Q6 y Q7).
La búsqueda global (S03) está en la etapa 1 (`design-system.md` y `ux-architecture.md` §7.3).
```

En `docs/ux/screens.md`, reemplazar:

```markdown
- **Supuestos:** ASM-003, ASM-008, ASM-009, ASM-019, ASM-062, ASM-063, ASM-067, ASM-069, ASM-075, ASM-076, ASM-079, ASM-080.
  Parte 3a: ASM-004, ASM-005, ASM-011, ASM-030, ASM-036, ASM-068, ASM-070, ASM-071, ASM-072. Parte 3b: ASM-063, ASM-069,
  ASM-073, ASM-074, ASM-078, ASM-081.
```

por:

```markdown
- **Supuestos:** ASM-003, ASM-008, ASM-009, ASM-019, ASM-062, ASM-063, ASM-067, ASM-069, ASM-075, ASM-076, ASM-079,
  ASM-080. Parte 3a: ASM-004, ASM-005, ASM-011, ASM-030, ASM-036, ASM-068, ASM-070, ASM-071, ASM-072, ASM-095, ASM-096,
  ASM-097, ASM-098, ASM-102, ASM-104. Parte 3b: ASM-063, ASM-069, ASM-073, ASM-074, ASM-078, ASM-081.
```

En `docs/ux/screens.md`, reemplazar:

```markdown
Lienzo de la etapa 3, página 11. Tres objetos con ciclos distintos (WF-E1, WF-E2, WF-E3; ASM-011) que la lista une para
atender por plazo. El plazo es `deadline_status`, nunca un estado (RN-WFL-001).
```

por:

```markdown
Lienzo de la etapa 3, página 11. Tres objetos con ciclos distintos (WF-E1, WF-E2, WF-E3; ASM-011) que la lista une para
atender por plazo. El plazo es `deadline_status`, nunca un estado (RN-WFL-001).

- **Registro reservado (ASM-095).** Una QCS confidencial que la persona no lee no aparece en las listas, los tableros ni
  los reportes. Un vínculo a ella desde otra pantalla (tarea, evidencia, compromiso, pregunta de reunión) muestra
  «Registro reservado», sin enlace ni datos, y los tableros y el reporte avisan cuando la cifra excluye confidenciales
  (`confidentialHidden`).
```

En `docs/ux/screens.md`, reemplazar:

```markdown
- **Relacionadas y navegación:** S36 por fila; S37; el tablero de QCS (S34, etapa 3b); la capa LYR-20 en el mapa.
```

por:

```markdown
- **Relacionadas y navegación:** S36 (queja) o S57 (consulta y solicitud) por fila; S37; el tablero de QCS (S34, etapa
  3b); la capa LYR-20 en el mapa.
```

En `docs/ux/screens.md`, reemplazar:

```markdown
- **Roles:** CRO y GVM crean los tres objetos.
```

por:

```markdown
- **Roles:** CRO y GVM crean los tres objetos. Es la única entrada de QCS: no hay formulario público (ASM-104, UPS-25),
  y un contratista reporta a través del equipo social con el canal CONTRATISTA (ASM-043).
```

En `docs/ux/screens.md`, reemplazar:

```markdown
- **Disposición y componentes:** elección del tipo con su definición y sus plazos; "Cómo llegó y quién la plantea" (canal,
  fecha de recepción, interacción o pregunta de origen, anonimato, confidencialidad, grupo de interés, persona, lugar); "Qué
  plantea" (descripción, categoría, severidad en la queja, prioridad, responsable); adjuntos con tipo y clasificación de
  evidencia; a la derecha, "Qué pasará al registrar" con el código, el estado y los plazos calculados.
- **Datos y API:** `POST` genérico del objeto; plazos derivados desde `received_at` con los parámetros del proyecto (ASM-005);
  adjuntos con `POST /api/sgspe/evidence` (IK, CR por ser destinos `requiresReason`). AUT-02 crea la tarea de acusar recibo.
```

por:

```markdown
- **Disposición y componentes:** elección del tipo con su definición y sus plazos; "Cómo llegó y quién la plantea"
  (canal, fecha de recepción, interacción o pregunta de origen, anonimato, confidencialidad, grupo de interés, persona,
  lugar); "Qué plantea" (descripción, categoría, severidad en la queja, prioridad, responsable); adjuntos con tipo y
  clasificación de evidencia; a la derecha, "Qué pasará al registrar" con el código, el estado y los plazos calculados.
  El interruptor «Confidencial» explica quién podrá leerla (gestión de quejas, su responsable, quien la recibe y, si es
  grave, quien aprueba) y deja a quien registra como quien la recibió (`received_by_user_id`, ASM-095).
- **Datos y API:** `POST` genérico del objeto; plazos derivados desde `received_at` con los parámetros del proyecto
  (ASM-005): el plazo de acuse lo calcula el backend en días hábiles con los feriados configurados (ASM-096), y la ficha
  lo muestra al registrar; adjuntos con `POST /api/sgspe/evidence` (IK, CR por ser destinos `requiresReason`). AUT-02
  crea la tarea de acusar recibo.
```

En `docs/ux/screens.md`, reemplazar:

```markdown
  al registrar se abre S36.
```

por:

```markdown
  al registrar se abre S36 o S57.
```

En `docs/ux/screens.md`, reemplazar:

```markdown
├── IntakeSection (ChannelField · OriginPicker · AnonymityToggle · StakeholderPicker · PlacePicker)
```

por:

```markdown
├── IntakeSection (ChannelField · OriginPicker · AnonymityToggle · ConfidentialToggle · StakeholderPicker · PlacePicker)
```

En `docs/ux/screens.md`, reemplazar:

```markdown
- **Acciones:** la primaria es la transición siguiente del estado; secundarias "Rechazar…", "Registrar desistimiento…",
  "Reclasificar…", "Reabrir…" y "Crear compromiso desde esta queja".
```

por:

```markdown
- **Acciones:** la primaria es la transición siguiente del estado; secundarias "Rechazar…", "Registrar desistimiento…",
  "Reclasificar…", "Reabrir…", "Subir cargo firmado" y "Crear compromiso desde esta queja", que abre S30 con
  `origin_grievance` y el proyecto de la queja ya puestos (alta genérica del compromiso en `draft`).
```

En `docs/ux/screens.md`, reemplazar:

```markdown
  clasificación (AI-08). AUT-06 emite el cargo de queja al acusar recibo.
```

por:

```markdown
  clasificación (AI-08). El cargo de queja no se emite mientras UPS-23 siga abierta (ASM-098): "Subir cargo firmado" lo
  sube con `POST /api/sgspe/evidence` como evidencia FORMULARIO vinculada a la queja, con clasificación DATOS PERSONALES
  por defecto.
```

En `docs/ux/screens.md`, reemplazar:

```markdown
- **Estados:** con `policy_high_severity_requires_approver` activo, GVM no ve "Resolver" en una ALTA o CRITICA y se explica
  que la tiene aprobación; sin conformidad del reclamante se puede reabrir; aceptar un duplicado rechaza la más nueva con
  `duplicate_of` y respuesta.
```

por:

```markdown
- **Estados:** con `policy_high_severity_requires_approver` activo, GVM no ve "Resolver" en una ALTA o CRITICA y se
  explica que la tiene aprobación, y APR la encuentra en S48 (`approval_pending`, ASM-102); una queja confidencial
  muestra quién puede leerla; sin conformidad del reclamante se puede reabrir; aceptar un duplicado rechaza la más nueva
  con `duplicate_of` y respuesta.
```

En `docs/ux/screens.md`, reemplazar:

```markdown
### S36 · Ficha de consulta y ficha de solicitud
```

por:

```markdown
### S57 · Ficha de consulta y ficha de solicitud
```

En `docs/ux/screens.md`, reemplazar:

```markdown
- **Acciones:** consulta: "Responder", "Derivar a otra entidad…", "Reclasificar…". Solicitud: "Aceptar y crear el
  compromiso" (o el aporte), "Declinar con respuesta…", "Reclasificar…".
```

por:

```markdown
- **Acciones:** consulta: "Responder", "Derivar a otra entidad…", "Reclasificar…", "Subir carta firmada". Solicitud:
  "Aceptar y crear el compromiso" (el aporte, desde F5), "Declinar con respuesta…", "Reclasificar…", "Subir carta
  firmada".
```

En `docs/ux/screens.md`, reemplazar:

```markdown
  `draft` o `social_contribution` en `requested`); reclasificación por su endpoint. AUT-07 y AUT-08 emiten las cartas.
```

por:

```markdown
  `draft` o, desde F5, `social_contribution` en `requested`); reclasificación por su endpoint. Las cartas no se emiten
  mientras UPS-23 siga abierta (ASM-098): "Subir carta firmada" la sube como evidencia CARTA vinculada a la QCS, con
  clasificación DATOS PERSONALES por defecto.
```

En `docs/ux/screens.md`, reemplazar:

```markdown
- **Relacionadas y navegación:** S43, S44; solicitud de origen (S36); tablero de inversión (etapa 3b).
```

por:

```markdown
- **Relacionadas y navegación:** S43, S44; solicitud de origen (S57); tablero de inversión (etapa 3b).
```

En `docs/ux/screens.md`, reemplazar:

```markdown
- **Relacionadas y navegación:** solicitud de origen (S36), bandeja de aprobaciones (S48); al cerrar, la solicitud se cierra
  sola (ASM-068).
```

por:

```markdown
- **Relacionadas y navegación:** solicitud de origen (S57), bandeja de aprobaciones (S48); al cerrar, la solicitud se
  cierra sola (ASM-068).
```

En `docs/ux/README.md`, reemplazar:

```markdown
| 11 Quejas, consultas y solicitudes | Índice de la etapa · Lista unificada (S35) · Registrar QCS (S37) · Ficha de queja, interactiva (S36) · Fichas de consulta y de solicitud (S36) |
```

por:

```markdown
| 11 Quejas, consultas y solicitudes | Índice de la etapa · Lista unificada (S35) · Registrar QCS (S37) · Ficha de queja, interactiva (S36) · Fichas de consulta y de solicitud (S57) |
```

- [ ] **Paso 4: Riesgos y alerta temprana: medidas en S40, señales en S41 y fichas nuevas S58 a S61**

En `docs/ux/screens.md`, reemplazar:

```markdown
- **Acciones:** primaria "Nueva revisión"; secundarias "Pasar a monitoreo", "Volver a mitigar", "Cerrar…", "+ Medida".
```

por:

```markdown
- **Acciones:** primaria "Nueva revisión"; secundarias "Pasar a monitoreo", "Volver a mitigar", "Cerrar…", "+ Medida",
  "+ Impacto potencial" (S61).
- **Medidas de mitigación:** "+ Medida" abre un panel dentro de S40 con descripción, responsable, fecha prevista y
  `completed`; SRM las crea, cambia y borra por la API genérica, y decidir mitigar o volver a mitigar exige al menos una
  con responsable (`409 MEASURE_OWNER_REQUIRED`, RN-RSK-003). Una medida atrasada se marca en la lista.
```

En `docs/ux/screens.md`, reemplazar:

```markdown
- **Acciones:** en triaje, "Confirmar" (con vínculo opcional al riesgo) y "Descartar…" (motivo obligatorio); luego "Iniciar
  respuesta", "Resolver", "Cerrar".
```

por:

```markdown
- **Acciones:** en triaje, "Confirmar" (con vínculo opcional al riesgo), "Descartar…" (motivo obligatorio) y "Vincular
  señal…", que guarda `early_warning_signal.early_warning` en una señal del mismo proyecto (S59); luego "Iniciar
  respuesta", "Resolver", "Cerrar".
```

En `docs/ux/screens.md`, reemplazar:

````markdown
```text
IncidentPage
├── RecordHeader · StatePath
├── WhatHappenedSection · ResponseSection
└── SidePanel (GeneratedLinks · SamePlaceOpenRecords · EvidenceList)
```
````

por:

````markdown
```text
IncidentPage
├── RecordHeader · StatePath
├── WhatHappenedSection · ResponseSection
└── SidePanel (GeneratedLinks · SamePlaceOpenRecords · EvidenceList)
```

### S58 · Menciones en medios

- **Ruta y lienzo:** `/projects/:projectId/risks/mentions` y `/projects/:projectId/risks/mentions/:id` · sin hoja propia
  (sigue la anatomía de la ficha de incidente) · F4.
- **Propósito:** registrar a mano lo que dicen los medios del proyecto, con su tono, para los indicadores y las reglas
  de alerta.
- **Roles:** CRO y SRM crean y cambian; el resto lee.
- **Acciones:** primaria "Registrar mención"; secundarias "Guardar" y "Registrar señal desde esta mención" (S59).
- **Disposición y componentes:** lista con fecha, medio, titular y tono; la ficha con medio (organización o texto),
  fecha de publicación, titular, resumen, enlace, tema, tono y "Informa sobre un compromiso cumplido" con el compromiso.
- **Datos y API:** genérica sobre `media_mention`; KPI-ENG-07 y KPI-ENG-08; la regla MENCIONES NEGATIVAS de JOB-04.
- **Estados:** el enlace se muestra como texto con su dominio y se abre en otra pestaña; el servidor nunca lo descarga
  (ASM-050).
- **Permisos:** sin campos restringidos propios.
- **Responsive:** la lista pasa a tarjetas con fecha, titular y tono.
- **Relacionadas y navegación:** el compromiso (S29), la señal (S59), el centro de alerta (S41).

```text
MediaMentionPage
├── MentionList (MentionRow · ToneBadge)
└── MentionForm (OutletField · HeadlineField · ToneField · CommitmentPicker · UrlField)
```

### S59 · Señales de alerta

- **Ruta y lienzo:** `/projects/:projectId/risks/signals` y `/projects/:projectId/risks/signals/:id` · sin hoja propia ·
  F4.
- **Propósito:** registrar a mano lo que puede anticipar un conflicto, con su fuente, para que una persona lo lleve a
  una alerta o una regla lo cuente.
- **Roles:** CRO y SRM crean y cambian; quien hace el triaje la vincula a la alerta desde S41.
- **Acciones:** primaria "Registrar señal"; secundarias "Guardar" y "Abrir la alerta" si tiene una.
- **Disposición y componentes:** lista con fecha, fuente, lugar y alerta; la ficha con fuente, fecha de recepción,
  descripción, cómo llegó, lugar y los vínculos a mención, incidente y alerta.
- **Datos y API:** genérica sobre `early_warning_signal`; la regla CONTEO DE SEÑALES de JOB-04; los vínculos del mismo
  proyecto (`409 PROJECT_MISMATCH`).
- **Estados:** una señal sin alerta se marca "Sin alerta".
- **Permisos:** sin campos restringidos propios; el texto no lleva nombres de personas (RQ-SEC-010).
- **Responsive:** la lista pasa a tarjetas.
- **Relacionadas y navegación:** la mención (S58), el incidente, la alerta (S41).

```text
SignalPage
├── SignalList (SignalRow · SourceBadge)
└── SignalForm (SourceField · DescriptionField · PlacePicker · MentionPicker · IncidentPicker)
```

### S60 · Reglas de alerta

- **Ruta y lienzo:** `/risks/rules` (las del tenant) y `/projects/:projectId/risks/rules` · sin hoja propia · F4.
- **Propósito:** configurar qué dispara una alerta: el tipo de regla, su umbral y su ventana.
- **Roles:** SRM crea, cambia, desactiva y borra; el resto lee.
- **Acciones:** primaria "Nueva regla"; secundarias "Desactivar" y "Borrar" (una regla con alertas no se borra: se
  desactiva).
- **Disposición y componentes:** lista con nombre, tipo, umbral, ventana, severidad, alcance (tenant o proyecto) y
  estado; el formulario explica, para el tipo elegido, qué cuenta, qué significa el umbral y qué ventana usa
  (`workflows.md`, «Tipos de regla de JOB-04»), y que una regla del proyecto reemplaza a la del tenant del mismo tipo.
- **Datos y API:** genérica sobre `early_warning_rule`; `threshold` entero y `window_days` de 1 a 365
  (`400 OUT_OF_RANGE`); `409 HAS_DEPENDENTS` al borrar una con alertas.
- **Estados:** sin CAMBIO DE POSTURA en el MVP (ASM-097); una regla desactivada no se evalúa.
- **Permisos:** solo SRM escribe.
- **Responsive:** la lista pasa a tarjetas; el formulario, a una columna.
- **Relacionadas y navegación:** el centro de alerta (S41) filtra por regla.

```text
WarningRulesPage
├── RuleList (RuleRow · ScopeBadge)
└── RuleForm (RuleKindField · ThresholdField · WindowField · SeverityField · KindExplanation)
```

### S61 · Impactos potenciales

- **Ruta y lienzo:** `/projects/:projectId/risks/impacts` y el panel "Impactos" de S40 · sin hoja propia · F4.
- **Propósito:** registrar qué actividad puede causar qué impacto, de qué tipo, naturaleza y signo, y a qué riesgo
  alimenta.
- **Roles:** SRM crea, cambia y borra; el resto lee.
- **Acciones:** primaria "+ Impacto potencial"; secundarias "Guardar" y "Borrar".
- **Disposición y componentes:** tabla con actividad, tipo, naturaleza, signo, descripción y riesgo.
- **Datos y API:** genérica sobre `potential_impact`; el riesgo del mismo proyecto.
- **Estados:** un impacto sin riesgo se marca "Sin riesgo vinculado".
- **Permisos:** solo SRM escribe.
- **Responsive:** la tabla pasa a tarjetas.
- **Relacionadas y navegación:** el riesgo (S40).

```text
PotentialImpactsPage
└── ImpactTable (ImpactRow · ImpactForm)
```
````

- [ ] **Paso 5: El tablero de riesgos pasa a S62 (decisión 10)**

S38 queda para el registro y matriz de riesgos (con S39); el tablero de riesgos toma S62, el siguiente id libre después
de S57 a S61, y el inventario de `ux-architecture.md` §9 suma S57 a S62.

En `docs/ux/screens.md`, reemplazar:

```markdown
- **Ruta y lienzo:** `/projects/:projectId/risks/register` · hoja `Rsk-Registro` (interactiva) · F4. El tablero de riesgos
  (S38 completo, con tendencia) llega en la etapa 3b.
```

por:

```markdown
- **Ruta y lienzo:** `/projects/:projectId/risks/register` · hoja `Rsk-Registro` (interactiva) · F4. El tablero de
  riesgos (S62, con tendencia) llega en la etapa 3b.
```

En `docs/ux/screens.md`, reemplazar:

```markdown
- **Relacionadas y navegación:** cada cifra abre su lista filtrada; S27, S34, S38, monitoreo.
```

por:

```markdown
- **Relacionadas y navegación:** cada cifra abre su lista filtrada; S27, S34, S62, monitoreo.
```

En `docs/ux/screens.md`, reemplazar:

```markdown
### S38 · Tablero de riesgos
```

por:

```markdown
### S62 · Tablero de riesgos
```

En `docs/ux/screens.md`, reemplazar:

```markdown
- **Acciones:** "Ir al registro" (S39); abrir medidas atrasadas.
```

por:

```markdown
- **Acciones:** "Ir al registro" (S38 y S39); abrir medidas atrasadas.
```

En `docs/ux/screens.md`, reemplazar:

```markdown
- **Relacionadas y navegación:** S39, S40.
```

por:

```markdown
- **Relacionadas y navegación:** el registro y matriz (S38 y S39), S40.
```

En `docs/ux/screens.md`, reemplazar:

```markdown
- **Relacionadas y navegación:** S47, S48, S51, S38, S52.
```

por:

```markdown
- **Relacionadas y navegación:** S47, S48, S51, S62, S52.
```

En `docs/ux/README.md`, reemplazar:

```markdown
| E3 · parte 3b | Tableros (con S27), capa de IA, inicio, avisos, reportes y entregables, administración, campo sin conexión y handoff | Lienzo de la etapa 3, páginas 16 a 20 (25 hojas) | [`screens.md`](screens.md) completo (S01, S02, S04, S05, S27, S34, S38, S49–S55 y tableros), [`flows.md`](flows.md) (flujo 18 y flujo de campo), ASM-081 | Entregada, en revisión |
```

por:

```markdown
| E3 · parte 3b | Tableros (con S27), capa de IA, inicio, avisos, reportes y entregables, administración, campo sin conexión y handoff | Lienzo de la etapa 3, páginas 16 a 20 (25 hojas) | [`screens.md`](screens.md) completo (S01, S02, S04, S05, S27, S34, S49–S55, S62 y tableros), [`flows.md`](flows.md) (flujo 18 y flujo de campo), ASM-081 | Entregada, en revisión |
```

En `docs/ux/README.md`, reemplazar:

```markdown
| 16 Tableros | Revisión ejecutiva de 5 minutos (S51) · Monitoreo de la Tabla 9.5, interactiva · Compromisos (S27) · QCS (S34) · Riesgos (S38) · Territorial · Inversión social |
```

por:

```markdown
| 16 Tableros | Revisión ejecutiva de 5 minutos (S51) · Monitoreo de la Tabla 9.5, interactiva · Compromisos (S27) · QCS (S34) · Riesgos (S62) · Territorial · Inversión social |
```

En `docs/ux/ux-architecture.md`, reemplazar:

```markdown
Las 38 pantallas de PRM-02 §37, ajustadas a la Fase 0: 56 pantallas (S56 se suma con las vistas compartidas, ASM-083). "Etapa" es la etapa de diseño en que se dibujan; "Fase",
```

por:

```markdown
Las 38 pantallas de PRM-02 §37, ajustadas a la Fase 0: 56 pantallas (S56 se suma con las vistas compartidas, ASM-083), más S57 a S62 de la Fase 4 (plan 4.0): la ficha de consulta y solicitud, que compartía S36 con la queja, las de menciones, señales, reglas e impactos, y el tablero de riesgos, que compartía S38 con el registro y matriz. "Etapa" es la etapa de diseño en que se dibujan; "Fase",
```

En `docs/ux/ux-architecture.md`, reemplazar:

```markdown
| S36 | Ficha de queja, consulta o solicitud | `/projects/:projectId/grievances/:id` (y pares) | F4 | E3 |
```

por:

```markdown
| S36 | Ficha de queja | `/projects/:projectId/grievances/:id` | F4 | E3 |
```

En `docs/ux/ux-architecture.md`, reemplazar:

```markdown
| S38 | Tablero y matriz de riesgos | `/risks` | F4, F8 | E3 |
```

por:

```markdown
| S38 | Matriz de riesgos (con el registro) | `/risks/register` | F4 | E3 |
```

En `docs/ux/ux-architecture.md`, reemplazar:

```markdown
| S56 | Gestionar vistas (ASM-083) | `/views` y diálogo en cada lista | F2 | E3 |
```

por:

```markdown
| S56 | Gestionar vistas (ASM-083) | `/views` y diálogo en cada lista | F2 | E3 |
| S57 | Ficha de consulta y ficha de solicitud | `/projects/:projectId/inquiries/:id`, `/projects/:projectId/requests/:id` | F4 | E3 |
| S58 | Menciones en medios | `/projects/:projectId/risks/mentions` | F4 | Plan 4.0, sin hoja |
| S59 | Señales de alerta | `/projects/:projectId/risks/signals` | F4 | Plan 4.0, sin hoja |
| S60 | Reglas de alerta | `/risks/rules`, `/projects/:projectId/risks/rules` | F4 | Plan 4.0, sin hoja |
| S61 | Impactos potenciales | `/projects/:projectId/risks/impacts` | F4 | Plan 4.0, sin hoja |
| S62 | Tablero de riesgos | `/risks` | F4, F8 | E3 |
```

- [ ] **Paso 6: Verificar**

Repetir la comprobación del Paso 1 y correr las compuertas de «Global Constraints». Además, `grep -n 'S36'
docs/ux/screens.md` ya no lleva a S36 a una consulta ni a una solicitud (las líneas que quedan hablan de la queja o de
la anatomía de S36 que S57 comparte), y `grep -n 'S38' docs/ux/*.md` solo nombra el registro y matriz de riesgos.

Esperado: `ok` y las compuertas en verde.

- [ ] **Paso 7: Commit**

```bash
git add docs/dashboards.md docs/ux/screens.md docs/ux/README.md docs/ux/ux-architecture.md
git commit -F - <<'MSG'
docs: tableros y pantallas de la fase 4

dashboards.md dice que los KPI de QCS leen como quien llama, con confidentialHidden y sin celdas
pequeñas; screens.md da a la ficha de consulta y solicitud el id S57, agrega las fichas S58 a S61,
las medidas dentro de S40, el interruptor de confidencialidad de S37 y el «Registro reservado», y
el tablero de riesgos pasa de S38 a S62; ux-architecture.md suma S57 a S62 a su inventario.

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_017MAgYcNauGp6cC3ekX1h7U
MSG
```

---

### Tarea 7: Roadmap y trazabilidad

Cierra C2 (RF-GRV-001, RF-GRV-006, RF-RSK-001 y RN-RSK-002 pasan a «Diseñado»), C3 (F3 integrada), C7 en la trazabilidad
(RNF-I18N-002 «Diseñado» con `BusinessDaysTest` y `QcsDueDatesApiTest`), C16 y C17 (la fila F4 del roadmap con su
alcance, su definición de terminado y sus riesgos) y C1 en el roadmap; suma UPS-25 y UPS-26 a las brechas por fase y la
respuesta del usuario que reabre la protección de quien plantea una QCS.

**Archivos:**
- Modificar: `docs/roadmap.md`, `docs/requirements-traceability.md`
- Probar: `tools/check_traceability.py`

**Interfaces:**
- Consume: los IDs de la Tarea 1; §9.6 de `security.md` (Tarea 3), que el roadmap enlaza con el ancla
  `security.md#96-qcs-confidenciales-recordreadscope-f4`; ADR-0028 (Tarea 3), que cita la fila de RF-GRV-001.
- Produce: la definición de terminado de F4 que verifican los planes 4.1 a 4.3 (actualizar el tenant de desarrollo de
  F2 a F4, comprobar MinIO y decidir UPS-01 antes de la producción con varios proyectos).

- [ ] **Paso 1: La comprobación falla**

```bash
python3 - <<'PY'
checks = [
    ('docs/roadmap.md', '| Después del MVP (candidatas, sin issue hasta la aprobación del usuario) | UPS-25, UPS-26 |', True),
    ('docs/roadmap.md', '| Protección de quien plantea una QCS (reabierta el 2026-10-09, antes de F4) |', True),
    ('docs/requirements-traceability.md', '| Decisión pendiente | 10 |', True),
    ('docs/requirements-traceability.md', '| F4 | Decisión pendiente (GAP-005) |', False),
    ('docs/requirements-traceability.md', '| F2 | Upstream (UPS-15) |', False),
]
bad = [f"{p}: {'falta' if want else 'sobra'} {s!r}" for p, s, want in checks
       if (s in open(p, encoding='utf-8').read()) != want]
print('\n'.join(bad) or 'ok')
raise SystemExit(1 if bad else 0)
PY
```

Esperado: salida 1 con tres líneas «falta» y dos «sobra».

- [ ] **Paso 2: El roadmap: estado de F3, fila F4, brechas y respuestas del checkpoint**

En `docs/roadmap.md`, reemplazar:

```markdown
| Estado | Backend y modelo construidos en el plan 3.2 (`feat/fase-3-2-backend`); pendientes: actualizar el tenant de desarrollo de F2 a F3 y comprobar MinIO (necesitan el túnel), el original de cada importación como evidencia (ASM-060, sin plan) y el portal (plan 3.3) |
```

por:

```markdown
| Estado | Modelo, backend y portal integrados en `main` (planes 3.1 y 3.2, wasichai/sgspe#13; plan 3.3, wasichai/sgspe-ui#5); pendientes: actualizar el tenant de desarrollo de F2 a F3 y comprobar MinIO (necesitan el túnel; F4 los incluye en su definición de terminado) y el original de cada importación como evidencia (ASM-060), que pasa a F6 |
```

En `docs/roadmap.md`, reemplazar:

```markdown
| Alcance | QCS en tres objetos con flujo propio, reclasificación, aceptación de solicitudes y reporte unificado; derivación de preguntas de reuniones a QCS; riesgos con revisión semanal (puntaje, nivel, tendencia y residual), medidas e impactos potenciales; alerta temprana con incidentes, menciones en medios, señales, reglas y alertas con decisión humana; escalamiento de QCS y riesgos [PRM-01 §24] [DOC-01 §9.3.2] (ASM-011) |
```

por:

```markdown
| Alcance | QCS en tres objetos con flujo propio, reclasificación, aceptación de solicitudes y reporte unificado; derivación de preguntas de reuniones a QCS; riesgos con revisión semanal (puntaje, nivel, tendencia y residual), medidas e impactos potenciales; alerta temprana con incidentes, menciones en medios, señales, reglas de seis tipos (ASM-097) y alertas con decisión humana; escalamiento de QCS y riesgos (los incidentes no escalan, ASM-103); confidencialidad por registro de las QCS (ASM-095); días hábiles con feriados configurados (ASM-096); registro de QCS solo por el equipo social (ASM-104) [PRM-01 §24] [DOC-01 §9.3.2] (ASM-011) |
```

En `docs/roadmap.md`, reemplazar:

```markdown
| Código SGSPE | `sgspe.attention`: `QcsDueDatesListener`, `QcsTransitionDatesListener`, `RequestClosureListener`, reclasificación (conserva `received_at` del primer registro, ASM-072), aceptación de solicitudes, reporte unificado; guarda `AttentionWriteGuard`. `sgspe.risk`: endpoint de revisiones y `RiskWriteGuard`. `sgspe.warning`: `WarningRulesJob` (JOB-04) y `WarningWriteGuard`. `sgspe.deadline`: JOB-01 suma QCS y riesgos. `sgspe.engagement`: derivación de preguntas |
```

por:

```markdown
| Código SGSPE | `sgspe.attention`: `QcsDueDatesListener`, `QcsTransitionDatesListener`, `RequestClosureListener`, reclasificación (conserva `received_at` del primer registro, ASM-072), aceptación de solicitudes, reporte unificado; guarda `AttentionWriteGuard`. `sgspe.risk`: endpoint de revisiones y `RiskWriteGuard`. `sgspe.warning`: `WarningRulesJob` (JOB-04) y `WarningWriteGuard`. `sgspe.deadline`: JOB-01 suma QCS y riesgos. `sgspe.engagement`: derivación de preguntas y `referred_at`. `sgspe.common`: `HolidayCalendar`, `BusinessDays` y `AttentionCounting`. `sgspe.scope`: `ProjectLinkGuard` y `ConfidentialAttentionScope`. `sgspe.notification`: `RiskNoticeListener`, `WarningNoticeListener`, `approval_pending` de una queja grave y el filtro de destinatarios de las QCS confidenciales. `sgspe.privacy`: `attention-register` y `risk-register` |
```

En `docs/roadmap.md`, reemplazar:

```markdown
| Workflows | WF-E1 (`grievance`), WF-E2 (`inquiry`), WF-E3 (`stakeholder_request`), WF-F (`social_risk`), WF-J (`early_warning`), WF-M (`incident`); automatizaciones AUT-02, AUT-06, AUT-07, AUT-08, AUT-09 y AUT-11 |
```

por:

```markdown
| Workflows | WF-E1 (`grievance`), WF-E2 (`inquiry`), WF-E3 (`stakeholder_request`), WF-F (`social_risk`), WF-J (`early_warning`), WF-M (`incident`); automatizaciones AUT-02, AUT-09 y AUT-11, y AUT-06, AUT-07 y AUT-08 cuando se resuelva UPS-23 (ASM-098) |
```

En `docs/roadmap.md`, reemplazar:

```markdown
| Entregables | Exportaciones `attention-register` y `risk-register`; documentos «Cargo de queja» y «Carta de respuesta»; datos de T1 §9.3, §9.3.1 y §9.3.2 y de T2 (incidente y riesgo reputacional) |
```

por:

```markdown
| Entregables | Exportaciones `attention-register` y `risk-register` en INTERNO, EXTERNO y PUBLICO (ASM-101); «Cargo de queja» y «Carta de respuesta» cuando se resuelva UPS-23 (hasta entonces se entregan en papel y, firmados, se suben como evidencia FORMULARIO o CARTA, ASM-098); datos de T1 §9.3, §9.3.1 y §9.3.2 y de T2 (incidente y riesgo reputacional) |
```

En `docs/roadmap.md`, reemplazar:

```markdown
| Definición de terminado | La general, más: `GrievanceWorkflowApiTest`, `InquiryWorkflowApiTest`, `StakeholderRequestWorkflowApiTest`, `SocialRiskWorkflowApiTest`, `EarlyWarningWorkflowApiTest`, `IncidentWorkflowApiTest`; cuentas de servicio que no confirman ni descartan alertas (RN-EWS-001); cierre de la Release 1 (MVP) |
| Riesgos | Plazos y escalas por defecto sin norma (GAP-004, GAP-005); protección parcial de quien plantea una QCS ([Seguridad §11.3](security.md#113-minimización)); canal de contratistas sin decidir (GAP-020) |
| IDs abiertos | Condicionan: GAP-004, GAP-005, GAP-020, GAP-023, GAP-024; ASM-004, ASM-005, ASM-011, ASM-036, ASM-043, ASM-072 |
```

por:

```markdown
| Definición de terminado | La general, más: `GrievanceWorkflowApiTest`, `InquiryWorkflowApiTest`, `StakeholderRequestWorkflowApiTest`, `SocialRiskWorkflowApiTest`, `EarlyWarningWorkflowApiTest`, `IncidentWorkflowApiTest`; `ConfidentialAttentionScopeTest` y `ConfidentialAttentionApiTest`; `BusinessDaysTest` y `QcsDueDatesApiTest`; `AttentionDashboardApiTest` y `RisksDashboardApiTest`; `AttentionRegisterApiTest` y `RiskRegisterApiTest`; cuentas de servicio que no confirman ni descartan alertas (RN-EWS-001); el tenant de desarrollo actualizado de F2 a F4 y MinIO comprobado; UPS-01 decidida antes de la producción con varios proyectos; cierre de la Release 1 (MVP) |
| Riesgos | Plazos y escalas por defecto sin norma (GAP-004, GAP-005); el vínculo `stakeholder` de una QCS no confidencial sigue sin restringir ([Seguridad §11.3](security.md#113-minimización)); el alcance de las QCS confidenciales en las escrituras y en la respuesta de un alta, que prueba la primera tarea de integración del plan 4.2a; un año sin feriados configurados cuenta de lunes a viernes (ASM-096); doce objetos más alargan el aprovisionamiento y la matriz de permisos por el túnel; canal de contratistas por el equipo social (GAP-020, ASM-043) |
| IDs abiertos | Condicionan: GAP-004, GAP-005, GAP-020, GAP-023, GAP-024; UPS-01, UPS-23, UPS-25, UPS-26; ASM-004, ASM-005, ASM-011, ASM-036, ASM-043, ASM-072, ASM-095 a ASM-104 |
| Estado | Diseño en `docs/superpowers/specs/2026-10-09-fase-4-design.md`; planes 4.0 (documentos), 4.1 (modelo), 4.2a (QCS), 4.2b (riesgos y alertas) y 4.3 (portal, con sgspe#14) |
```

En `docs/roadmap.md`, reemplazar:

```markdown
| Endurecimiento | UPS-12 |
```

por:

```markdown
| Endurecimiento | UPS-12 |
| Después del MVP (candidatas, sin issue hasta la aprobación del usuario) | UPS-25, UPS-26 |
```

En `docs/roadmap.md`, reemplazar:

```markdown
| Valores por defecto | Aceptados todos, cada uno reversible como dice su ASM o su decisión: <ul><li>alcance A;</li><li>ENUM en español;</li><li>AI-05 solo para `ANALYST`;</li><li>plazos desde la primera recepción (ASM-072);</li><li>nivel 2 de escalamiento con `approve_senior` de WF-D;</li><li>releases (ASM-044);</li><li>los valores por defecto de GAP-001 a GAP-009 y de las demás filas de la tabla anterior</li></ul> |
```

por:

```markdown
| Protección de quien plantea una QCS (reabierta el 2026-10-09, antes de F4) | Ya no «sin restringir en el MVP»: una QCS confidencial la leen solo `ADMIN`, `GRIEVANCE_MANAGER` (`READ_CONFIDENTIAL`), su responsable, quien la recibió y, si es una queja ALTA o CRITICA, `APPROVER` (`RecordReadScope`, ASM-095, [Seguridad §9.6](security.md#96-qcs-confidenciales-recordreadscope-f4)); el vínculo `stakeholder` de una QCS no confidencial sigue sin restringir |
| Decisiones de F4 (2026-10-09) | Solo el equipo social registra QCS (ASM-104); cargo y carta tras UPS-23 (ASM-098); seis tipos de regla de alerta sin CAMBIO DE POSTURA (ASM-097); días hábiles con feriados por año en configuración (ASM-096); exportaciones en tres niveles (ASM-101); sin avisos a quien plantea (UPS-26); una queja grave avisa también a `APPROVER` (ASM-102). UPS-25 y UPS-26 quedan como candidatas sin issue |
| Valores por defecto | Aceptados todos, cada uno reversible como dice su ASM o su decisión: <ul><li>alcance A;</li><li>ENUM en español;</li><li>AI-05 solo para `ANALYST`;</li><li>plazos desde la primera recepción (ASM-072);</li><li>nivel 2 de escalamiento con `approve_senior` de WF-D;</li><li>releases (ASM-044);</li><li>los valores por defecto de GAP-001 a GAP-009 y de las demás filas de la tabla anterior</li></ul> |
```

- [ ] **Paso 3: La trazabilidad: estados de F4, días hábiles y totales**

En `docs/requirements-traceability.md`, reemplazar:

```markdown
Una fila puede llevar varios estados, separados por punto y coma. Cuatro decisiones del checkpoint no tienen GAP ni CON propio y
por eso no aparecen como Decisión pendiente: ofrecer AI-05 solo a roles sin acceso a campos restringidos mientras falte UPS-14
(`security.md` §15), que RF-AIK-005 muestra como Upstream (UPS-14); contar los plazos desde la primera recepción al reclasificar
una QCS (ASM-072), que RF-GRV-007 muestra como Diseñado; reutilizar `approve_senior` de WF-D en el nivel 2 de escalamiento
(ASM-030), que RF-WFL-004 muestra como Upstream (UPS-17); y el reparto en releases (ASM-044), que fija el estado «Fuera
del MVP».
```

por:

```markdown
Una fila puede llevar varios estados, separados por punto y coma. Cuatro decisiones del checkpoint no tienen GAP ni CON
propio y por eso no aparecen como Decisión pendiente: ofrecer AI-05 solo a roles sin acceso a campos restringidos
mientras falte UPS-14 (`security.md` §15), que RF-AIK-005 muestra como Upstream (UPS-14); contar los plazos desde la
primera recepción al reclasificar una QCS (ASM-072), que RF-GRV-007 muestra como Diseñado; reutilizar `approve_senior`
de WF-D en el nivel 2 de escalamiento (ASM-030), que RF-WFL-004 muestra como Upstream (UPS-17); y el reparto en releases
(ASM-044), que fija el estado «Fuera del MVP». El checkpoint aceptó los valores por defecto de GAP-004 y GAP-005, y F4
se diseñó con ellos: sus filas de F4 (RF-GRV-001, RF-GRV-006, RF-RSK-001 y RN-RSK-002) pasan a Diseñado.
```

En `docs/requirements-traceability.md`, reemplazar:

```markdown
| RF-GRV-001 | [DOC-01 Tabla 9.5, §9.1.1.2, §9.4.2] [PRM-01 §5] | BC-07 · `model/model.json`; `sgspe.attention` (`AttentionWriteGuard`, `QcsDueDatesListener`) | `grievance`, `inquiry`, `stakeholder_request` | `/api/objects/grievance/records`; `/api/objects/inquiry/records`; `/api/objects/stakeholder_request/records` | Portal › Quejas, consultas y solicitudes | `GrievanceWorkflowApiTest`; `InquiryWorkflowApiTest`; `StakeholderRequestWorkflowApiTest` | F4 | Decisión pendiente (GAP-005) |
```

por:

```markdown
| RF-GRV-001 | [DOC-01 Tabla 9.5, §9.1.1.2, §9.4.2] [PRM-01 §5] | BC-07 · `model/model.json`; `sgspe.attention` (`AttentionWriteGuard`, `QcsDueDatesListener`); `sgspe.scope` (`ConfidentialAttentionScope`, ADR-0028) | `grievance`, `inquiry`, `stakeholder_request` | `/api/objects/grievance/records`; `/api/objects/inquiry/records`; `/api/objects/stakeholder_request/records` | Portal › Quejas, consultas y solicitudes | `GrievanceWorkflowApiTest`; `InquiryWorkflowApiTest`; `StakeholderRequestWorkflowApiTest`; `ConfidentialAttentionApiTest` | F4 | Diseñado |
```

En `docs/requirements-traceability.md`, reemplazar:

```markdown
| RF-GRV-006 | [PRM-01 §5, §17] | BC-07 · `sgspe.attention` (`QcsDueDatesListener`); `sgspe.deadline` (`DeadlineJob`, JOB-01); `model/model.json` (plazos del proyecto) | `grievance`, `inquiry`, `stakeholder_request`, `task`, `project` | `/api/objects/grievance/records`; `/api/objects/inquiry/records` | Portal › Quejas, consultas y solicitudes; Portal › Tareas | `GrievanceWorkflowApiTest`; `workflows.md` §Pruebas «Trabajos» | F4 | Decisión pendiente (GAP-005) |
```

por:

```markdown
| RF-GRV-006 | [PRM-01 §5, §17] | BC-07 · `sgspe.attention` (`QcsDueDatesListener`); `sgspe.common` (`BusinessDays`); `sgspe.deadline` (`DeadlineJob`, JOB-01); `model/model.json` (plazos del proyecto) | `grievance`, `inquiry`, `stakeholder_request`, `task`, `project` | `/api/objects/grievance/records`; `/api/objects/inquiry/records` | Portal › Quejas, consultas y solicitudes; Portal › Tareas | `GrievanceWorkflowApiTest`; `QcsDueDatesApiTest`; `BusinessDaysTest`; `workflows.md` §Pruebas «Trabajos» | F4 | Diseñado |
```

En `docs/requirements-traceability.md`, reemplazar:

```markdown
| RF-RSK-001 | [DOC-01 §9.3.2, §9.3.2.1] [PRM-01 §5] | BC-08 · `model/model.json`; `model/workflows/social_risk.json` (WF-F); `sgspe.risk` (`RiskWriteGuard`) | `social_risk` (M2M `social_risk_stakeholder`) | `/api/objects/social_risk/records`; `/api/objects/social_risk/records/{id}/transitions/{name}` | Portal › Riesgos: ficha de `social_risk` | `SocialRiskWorkflowApiTest` | F4 | Decisión pendiente (GAP-004) |
```

por:

```markdown
| RF-RSK-001 | [DOC-01 §9.3.2, §9.3.2.1] [PRM-01 §5] | BC-08 · `model/model.json`; `model/workflows/social_risk.json` (WF-F); `sgspe.risk` (`RiskWriteGuard`) | `social_risk` (M2M `social_risk_stakeholder`) | `/api/objects/social_risk/records`; `/api/objects/social_risk/records/{id}/transitions/{name}` | Portal › Riesgos: ficha de `social_risk` | `SocialRiskWorkflowApiTest` | F4 | Diseñado |
```

En `docs/requirements-traceability.md`, reemplazar:

```markdown
| RN-RSK-002 | [DOC-01 §9.3.2.1] | BC-08 · `sgspe.risk` (endpoint de revisiones); `model/model.json` (`risk_medium_min_score`, `risk_high_min_score`, `risk_critical_min_score`) | `social_risk`, `risk_assessment` | `POST /api/sgspe/social-risks/{id}/assessments` | Portal › Riesgos: ficha de `social_risk` | `SocialRiskAssessmentApiTest` (propuesto); `architecture.md` §13 «Unitarias» | F4 | Decisión pendiente (GAP-004) |
```

por:

```markdown
| RN-RSK-002 | [DOC-01 §9.3.2.1] | BC-08 · `sgspe.risk` (endpoint de revisiones); `model/model.json` (`risk_medium_min_score`, `risk_high_min_score`, `risk_critical_min_score`) | `social_risk`, `risk_assessment` | `POST /api/sgspe/social-risks/{id}/assessments` | Portal › Riesgos: ficha de `social_risk` | `SocialRiskAssessmentApiTest` (propuesto); `architecture.md` §13 «Unitarias» | F4 | Diseñado |
```

En `docs/requirements-traceability.md`, reemplazar:

```markdown
| RNF-I18N-002 | [DOC-02 p.4] | Transversal · `sgspe.common` (America/Lima, feriados, valores por defecto) | `project` (`currency` por defecto) | — | — | `QcsDueDatesApiTest` (propuesto); `architecture.md` §13 «Unitarias» | F2 | Upstream (UPS-15) |
```

por:

```markdown
| RNF-I18N-002 | [DOC-02 p.4] | Transversal · `sgspe.common` (America/Lima; `HolidayCalendar` y `BusinessDays` para los días hábiles, ASM-096; valores por defecto) | `project` (`currency` por defecto) | — | — | `BusinessDaysTest`; `QcsDueDatesApiTest`; `architecture.md` §13 «Unitarias» | F2 | Diseñado |
```

En `docs/requirements-traceability.md`, reemplazar:

```markdown
| Diseñado | 230 |
```

por:

```markdown
| Diseñado | 236 |
```

En `docs/requirements-traceability.md`, reemplazar:

```markdown
| Decisión pendiente | 14 |
```

por:

```markdown
| Decisión pendiente | 10 |
```

En `docs/requirements-traceability.md`, reemplazar:

```markdown
| Upstream | 24 |
```

por:

```markdown
| Upstream | 22 |
```

- [ ] **Paso 4: Verificar**

Repetir la comprobación del Paso 1 y correr las compuertas de «Global Constraints». Además, contar los estados de las
filas de la matriz:

```bash
python3 - <<'PY'
import collections, re
text = open("docs/requirements-traceability.md", encoding="utf-8").read()
main = re.search(r"^## 2\..*?(?=^## 6\.)", text, re.M | re.S).group(0)
count = collections.Counter()
for line in main.splitlines():
    if re.match(r"^\|\s*(?:RF|RNF|RN|RQ)-[A-Z0-9]+-\d{3}\s*\|", line):
        status = line.rstrip().rstrip("|").split("|")[-1]
        for state in ("Diseñado", "Implementado", "Decisión pendiente", "Upstream", "Hueco", "Fuera del MVP"):
            count[state] += state in status
print(dict(count))
PY
```

Esperado: `ok`, las compuertas en verde y `{'Diseñado': 236, 'Implementado': 46, 'Decisión pendiente': 10, 'Upstream':
22, 'Hueco': 0, 'Fuera del MVP': 82}`, las mismas cifras de la tabla «Estado | Filas que lo llevan».

- [ ] **Paso 5: Commit**

```bash
git add docs/roadmap.md docs/requirements-traceability.md
git commit -F - <<'MSG'
docs: roadmap y trazabilidad de la fase 4

El roadmap registra F3 integrada, la fila de F4 con su código, sus documentos tras UPS-23 y su
definición de terminado, UPS-25 y UPS-26 como candidatas y las respuestas de F4; la trazabilidad
pasa a Diseñado las filas de GAP-004, GAP-005 y RNF-I18N-002 y corrige los totales por estado.

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_017MAgYcNauGp6cC3ekX1h7U
MSG
```

---

### Tarea 8: La historia

**Archivos:**
- Modificar: `docs/HISTORY.md`

**Interfaces:**
- Consume: las Tareas 1 a 7 integradas.
- Produce: la entrada «Plan 4.0: los documentos de F4».

- [ ] **Paso 1: La comprobación falla**

```bash
python3 - <<'PY'
checks = [
    ('docs/HISTORY.md', '## 2026-10-09 — Plan 4.0: los documentos de F4', True),
]
bad = [f"{p}: {'falta' if want else 'sobra'} {s!r}" for p, s, want in checks
       if (s in open(p, encoding='utf-8').read()) != want]
print('\n'.join(bad) or 'ok')
raise SystemExit(1 if bad else 0)
PY
```

Esperado: salida 1 con una línea.

- [ ] **Paso 2: La entrada de la historia**

En `docs/HISTORY.md`, reemplazar:

```markdown
## 2026-10-09 — Plan 3.2: el backend de F3
```

por:

```markdown
## 2026-10-09 — Plan 4.0: los documentos de F4

Los documentos de diseño quedan al día con la especificación de F4
(`docs/superpowers/specs/2026-10-09-fase-4-design.md`) antes de tocar el modelo y el código. Las decisiones están en
[Requisitos](requirements-analysis.md) (ASM-095 a ASM-104; UPS-25 y UPS-26, candidatas sin issue; 520 IDs).

- **QCS confidenciales.** `READ_CONFIDENTIAL` y el `RecordReadScope` de las QCS confidenciales (`security.md` §9.6 y
  [ADR-0028](adr/0028-qcs-confidenciales-con-recordreadscope.md), con la excepción de `ConfidentialRelease` para el
  cierre automático de una solicitud) reemplazan la respuesta del checkpoint «sin restringir en el MVP»; el alcance por
  proyecto sigue en la opción A.
- **Huecos cerrados.** Documentos de QCS tras UPS-23 (C1), días hábiles con feriados en configuración (C7), los seis
  tipos de regla de JOB-04 sin CAMBIO DE POSTURA (C10 y C6) y el contenido de `attention-register` y `risk-register`
  (C12); además, las contradicciones menores C2 a C5, C8, C9, C11 y C13 a C17.
- **Pantallas.** La ficha de consulta y solicitud pasa a S57, y S58 a S61 son las fichas de menciones, señales, reglas e
  impactos. El tablero de riesgos, que compartía S38 con el registro y matriz, pasa a S62; `ux-architecture.md` §9
  suma S57 a S62 a su inventario.

## 2026-10-09 — Plan 3.2: el backend de F3
```

- [ ] **Paso 3: Verificar y commit**

Repetir la comprobación del Paso 1 (esperado: `ok`) y:

```bash
git add docs/HISTORY.md
git commit -F - <<'MSG'
docs: historia del plan 4.0

La entrada resume los IDs nuevos, la confidencialidad de las QCS y los huecos que el plan cierra.

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_017MAgYcNauGp6cC3ekX1h7U
MSG
```

---

### Tarea 9: Verificación final, push y PR

**Archivos:** ninguno nuevo.

- [ ] **Paso 1: Todo lo local y la lectura cruzada**

```bash
cd ~/IdeaProjects/sgspe
(cd model && python3 -m unittest -v 2>&1 | tail -3 && python3 apply.py --validate-only && \
  python3 load_ubigeo.py --dry-run | tail -1)
python3 tools/check_traceability.py && yarn format:check
grep -rn 'CAMBIO DE POSTURA' docs/*.md | grep -v 'fuera del MVP\|Fuera del MVP\|Por qué no CAMBIO DE POSTURA\|sin CAMBIO DE POSTURA\|CAMBIO DE POSTURA queda\|Sin CAMBIO DE POSTURA'
grep -rn 'Un listener de `sgspe.notification`\|origen JOB-01\|Decisión pendiente (GAP-00[45])' docs/*.md
git status --short
```

Esperado: `Ran 197 tests … OK`, `ubigeo: 25 departamentos, 196 provincias, 1892 distritos`,
`ok: 298 requisitos, todos con su fila`, prettier sin cambios, los dos `grep` sin salida y un `git status` vacío.

Lectura cruzada (Review Focus 2): leer seguidos `security.md` §9.6, `workflows.md` («Avisos» y la regla 7 de
«Destinatarios por rol»), `api.md` §4.6 («Reporte unificado» y «Confidencialidad»), `dashboards.md` §8 y
`deliverables.md` §10, y comprobar que dicen lo mismo de quién ve una QCS confidencial, de `confidentialHidden` y de
EXTERNO. Una diferencia se corrige en el documento que se aparta de la especificación §3.3, con un commit
`docs: …` propio.

- [ ] **Paso 2: Push y PR**

```bash
cd ~/IdeaProjects/sgspe && git push -u origin docs/fase-4-documentos
gh pr create --base main --title "docs: fase 4, plan 4.0: documentos de F4" --body-file - <<'MSG'
## Qué trae

- ASM-095 a ASM-104 y UPS-25 y UPS-26 (candidatas, sin issue); §20.1 suma 520 IDs.
- QCS confidenciales: `READ_CONFIDENTIAL` y el `RecordReadScope` de `security.md` §9.6, con su ADR (ADR-0028); el
  alcance por proyecto sigue en la opción A.
- `workflows.md`: los seis tipos de regla de JOB-04 sin CAMBIO DE POSTURA, `assess` de WF-F como transición de comando,
  la tarea de revisión de riesgo, el origen y los destinatarios de cada aviso de F4 y los plazos sin solicitudes;
  AUT-06 a AUT-08 esperan a UPS-23.
- Días hábiles con feriados por año en configuración (`HolidayCalendar`, `BusinessDays`).
- Contratos de la API de F4 y el contenido por nivel de `attention-register` y `risk-register`.
- Pantallas S57 a S61, el tablero de riesgos como S62 (S38 queda para el registro y matriz), «Registro reservado» y el
  interruptor de confidencialidad.
- Roadmap y trazabilidad al día (F3 integrada; GAP-004 y GAP-005 diseñados; totales por estado recalculados).

## Pruebas

- Python: las 197 pruebas del modelo, con los tipos de documento de QCS en `test_design_docs.py`.
- `tools/check_traceability.py` y `yarn format:check`.

🤖 Generated with [Claude Code](https://claude.com/claude-code)

https://claude.ai/code/session_017MAgYcNauGp6cC3ekX1h7U
MSG
```

---

## Resumen

| Tarea | Entrega | Grupo |
|---|---|---|
| 1 | IDs de F4 en el análisis | 0 |
| 2 | `workflows.md` y la prueba de los tipos de documento | 1 |
| 3 | `security.md` (§9.6), ADR-0028, `gis.md`, glosario | 1 |
| 4 | Modelo de datos, dominio y arquitectura | 1 |
| 5 | API y exportaciones | 1 |
| 6 | Tableros y pantallas | 1 |
| 7 | Roadmap y trazabilidad | 1 |
| 8 | Historia | 2 |
| 9 | Verificación, push y PR | 2 |

## Cobertura de la especificación §6

| Punto de §6 | Dónde |
|---|---|
| ASM-095 a ASM-104, UPS-25, UPS-26; §20.1 (520) y §20.4 | Tarea 1 |
| C1: `workflows.md`, `roadmap.md` F4 «Entregables», `security.md` §11.4, `domain-model.md` §7.3; §10 del análisis | Tareas 2, 7, 3, 4 y 1 |
| C7: `architecture.md`, `database-model.md` (`grievance_ack_days`), RNF-I18N-002 «Diseñado» | Tareas 4 y 7 |
| C10 y C6: «Tipos de regla de JOB-04», fila de JOB-04, §10 sin CAMBIO DE POSTURA, `early_warning.generator_key` | Tareas 2, 1 y 4 |
| C12: `api.md` §4.13 y `deliverables.md` §10 | Tarea 5 |
| C2, C3, C16, C17 | Tarea 7 |
| C4, C5, C8, C9, C15 | Tarea 2 (C15 también en la Tarea 4) |
| C11 | Tarea 6 |
| C13: `api.md` §3.3 y §4.6 | Tarea 5 (y la regla 10 de `workflows.md`, Tarea 2) |
| C14: `dashboards.md` §4.5 y §8 | Tarea 6 |
| `security.md` §5.3, §7.6, §9, §11.3, §11.4, §14; ADR-0028 | Tarea 3 |
| `roadmap.md` «Respuestas del checkpoint» | Tarea 7 |
| `api.md`: contratos de comandos, reporte y revisiones; códigos de §3.3 | Tarea 5 |
| `database-model.md`: `referred_at`, `READ_CONFIDENTIAL`, notas de `early_warning_rule` | Tarea 4 |
| `domain-model.md` §8.1.1 y §7.5 | Tarea 4 |
| `gis.md` (LYR-20) y `glossary.md` («QCS confidencial», «día hábil») | Tarea 3 |
