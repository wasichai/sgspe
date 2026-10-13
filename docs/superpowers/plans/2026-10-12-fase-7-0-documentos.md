# SGSPE · Fase 7, plan 7.0 (documentos): plan de implementación

> **Para agentes:** SUB-SKILL OBLIGATORIA: usar superpowers:subagent-driven-development (recomendada) o
> superpowers:executing-plans para ejecutar este plan tarea por tarea. Los pasos usan casillas (`- [ ]`) para el
> seguimiento. Cada tarea sigue el ciclo de superpowers:test-driven-development aplicado a documentos: una comprobación
> que falla, los cambios, la comprobación y las pruebas del modelo en verde, commit.

**Goal:** Dejar `docs/` al día con la especificación de F7 antes de tocar el modelo y el código: los IDs nuevos (ASM-128
a ASM-132) con §20.1 en 553, GAP-015 y UPS-14 resueltas; AI-05 para todo rol con CREATE sobre `ai_interaction`,
seudonimizado, en `ai-architecture.md`, `security.md`, `api.md`, los ADR, el roadmap, la trazabilidad, el encaje con la
plataforma y las pantallas; los errores `AI_UNAVAILABLE`, `AI_INPUT_TOO_LARGE`, `AI_RUN_TRUNCATED` y `AI_NO_TEXT` en el
contrato; ADR-0003 aceptado y ADR-0005 aceptado en su etapa 1; Embabel 1.5.3 en todas partes. Los planes 7.1, 7.2a,
7.2b, 7.2c y 7.3 leen estos documentos como fuente de verdad.

**Architecture:** Solo documentos. Cada tarea trae el texto actual exacto que se reemplaza y el texto nuevo exacto (los
bloques nuevos ya vienen envueltos a 120 columnas; si una línea editada pasa de 120, se reenvuelve sin cambiar palabras),
y una comprobación en Python de solo biblioteca estándar que falla mientras falte el cambio. Cuando un texto actual
cruza un salto de línea del documento, se busca sin tener en cuenta el salto ni la sangría; en los reemplazos de una
línea, el texto va entre comillas invertidas simples o dobles del Markdown, y las comillas invertidas interiores son parte
del texto (en caso de duda manda la búsqueda `grep -n` de una parte sin comillas). Ningún archivo de `src/`, de
`model/` ni de `tools/` cambia. `model/design_docs.py` lee las fichas, los catálogos y la matriz de permisos de estos
mismos documentos, así que la suite del modelo corre en cada tarea: ninguna tarea toca celdas de la matriz de
`security.md` §7, fichas de `database-model.md` ni catálogos.

**Tech Stack:** Markdown (sin prettier: `.prettierignore` excluye `*.md` y `docs/`); Python 3.11 o superior, solo
biblioteca estándar (`model/` y `tools/`); prettier 3.9.9 como compuerta del repositorio; husky y commitlint para los
mensajes; `gh` para el PR.

**Spec:** `docs/superpowers/specs/2026-10-12-fase-7-design.md`, §6 («Cambios en los documentos (plan 7.0)»), y las
secciones de las que §6 sale: §1 (alcance), §2.1 (decisiones del usuario del 2026-10-12, vinculantes), §2.2 (elecciones
1, 2, 3, 4, 5, 6, 7, 9 y 11), §3.1 a §3.5 (nombres de las piezas), §5 (pruebas), §7 (reparto y definición de terminado) y
§8 (riesgos y consecuencias aceptadas).

## Global Constraints

- **Repositorio y rama.** Todo en `~/IdeaProjects/sgspe`, en la rama `docs/fase-7-0-documentos`, que ya existe y tiene
  la especificación (`604b14f`) y este plan. No se cambia de rama ni se crean worktrees: las tareas van en orden, una
  detrás de otra.
- **El diseño manda.** `docs/` es la fuente de verdad y `docs/requirements-analysis.md` el único lugar donde se definen
  IDs: ASM-128 a ASM-132 los define la Tarea 1 y las demás solo los citan. Ningún GAP, CON ni UPS nuevo.
- **Versiones** (especificación, «Restricciones globales»): wasichai `1.2.0` exacto, Embabel `1.5.3` por su BOM, sin
  fijar otra; Kotlin `2.4.20`, Spring Boot `4.1.1`, JDK `25`, Gradle `9.8.0`. Ningún documento nombra Embabel 1.5.2 al
  terminar.
- **Nombres fijos de la especificación** (los documentos los usan tal cual): `sgspe.ai.enabled-organizations`,
  `sgspe.ai.models.<caso>`, `sgspe.ai.prompts.<caso>`, `sgspe.ai.limits.*`, `ANTHROPIC_API_KEY`, `AiActivation`,
  `AiStatus`, `Pseudonymizer`, `PseudonymTable`, `Interactions`, `Prompts`, `StructuredModel`, `EmbabelStructuredModel`,
  `AssistantQuestions`, `AiAgentAccess`, `PseudonymizingResultFilter`, `RestoringAnswerFilter`,
  `InteractionRunListener`, `AgentHttpGate`, `RestrictedFields`, `AiRuns`, `TextExtraction`, `CitationCheck`,
  `SuggestionWriter`, `SuggestionAcceptance`, `KnowledgeIndexJob`, `KnowledgeSearch`, `KnowledgeQuestions`,
  `DuplicateCandidates`; los puntos de extensión de wasichai `AgentAccessPolicy`, `AgentResultFilter`,
  `AgentAnswerFilter`, `AgentRunListener` y `AgentUsage`; la etiqueta de pruebas `ai-live`.
- **Idioma.** Español (es-PE) en documentos, comentarios y mensajes; nombres técnicos en inglés (ADR-0009); valores ENUM
  en español MAYÚSCULAS sin tildes; prosa envuelta a 120 columnas; filas de tabla en una sola línea.
- **Datos.** Ningún nombre real de persona; ejemplos ficticios («Proyecto Ejemplo A», «Comunidad Ejemplo»,
  `@sgspe.test`) (RQ-SEC-018).
- **Compuertas de cada tarea** (desde `~/IdeaProjects/sgspe`):

  ```bash
  (cd model && python3 -m unittest 2>&1 | tail -1 | grep -x OK && python3 apply.py --validate-only) && \
    python3 tools/check_traceability.py && \
    git ls-files -z | xargs -0 yarn -s prettier --check --ignore-unknown
  ```

  Esperado: `OK`, nada de `apply.py --validate-only` (sale con 0), `ok: 298 requisitos, todos con su fila` y
  `All matched files use Prettier code style!`. La última línea es `yarn format:check` limitado a los archivos
  versionados: en este checkout, `yarn format:check` a secas falla por los `.yml` sin versionar de `.playwright-mcp/`, que
  no son del repositorio, no se versionan ni se borran (la CI corre sobre un checkout limpio). Además, cada tarea corre
  `yarn -s prettier --check` sobre sus archivos: como `.prettierignore` excluye `*.md` y `docs/`, responde
  `All matched files use Prettier code style!` sin revisar nada, y confirma que la tarea no tocó otro tipo de archivo.
- **Commits.** Conventional Commits con asunto en español; cuerpo de 100 columnas como máximo (contadas en caracteres);
  al final, tras una línea en blanco, `Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>` y
  `Claude-Session: https://claude.ai/code/session_01RmiD2JE3gkHdBNUraZkjgM`. `git add` solo con rutas explícitas. Nunca
  se versionan `initial-promp.md`, `arch-*.md`, `tech-landing.txt`, `.playwright-mcp/` ni nada de `~/Documents/gspe`.
- **Push y PR** solo en la Tarea 8.

## Review Focus

1. **Una restricción vieja de AI-05 que sobrevive.** Quien lea `ux/screens.md`, un ADR o `platform-fit.md` para el plan
   7.1 o 7.3 y encuentre «solo `ANALYST`» o «hasta UPS-14» construirá el portal o los roles al revés. Se espera que
   ninguna línea vigente diga que AI-05 está restringido por UPS-14. Prueba: la búsqueda cruzada de la Tarea 8, Paso 1,
   que marca toda línea con UPS-14 y una forma de «mientras falte» sin «resuelta», y toda línea con «solo `ANALYST`»
   fuera de la del ejecutivo.
2. **Un mismo error, la misma forma en todos los documentos.** 7.2a y 7.2b copian los códigos de `api.md` §3.3, de
   §4.14 y de `ai-architecture.md`; un estado o un campo distinto en uno de ellos se vuelve una prueba roja o un
   contrato roto con 7.3. Se espera `503 AI_UNAVAILABLE`, `422 AI_INPUT_TOO_LARGE`, `422 AI_NO_TEXT` y
   `502 AI_RUN_TRUNCATED` iguales en los tres lugares y en los ASM. Prueba: la comprobación de la Tarea 4 y la cruzada de
   la Tarea 8.
3. **IDs y totales.** §20.1 suma 553 (ASM 132); cada ASM nuevo se define una vez y lo cita al menos otro documento;
   GAP-015 y UPS-14 dicen «Resuelta» en el análisis y nadie las da por abiertas. Pruebas: la comprobación de la Tarea 1 y
   la cruzada de la Tarea 8.
4. **La lista de roles de AI-05 frente a la matriz.** `security.md` §11.5 nombra los roles que hoy tienen CREATE sobre
   `ai_interaction`; si no coincide con la fila de §7.8, 7.1 y 7.3 discrepan. Se espera exactamente `SOCIAL_MANAGER`,
   `COMMUNITY_RELATIONS_OFFICER`, `STAKEHOLDER_MANAGER`, `COMMITMENT_MANAGER`, `GRIEVANCE_MANAGER`,
   `SOCIAL_RISK_MANAGER` y `ANALYST`. Prueba: la comprobación de la Tarea 3, que lee la matriz y la compara con §11.5.
5. **El lector de documentos del modelo.** `model/design_docs.py` lee tablas de `security.md`, `domain-model.md` y el
   análisis; una fila rota (una barra de más, una fila partida en dos líneas) rompe `test_model.py` o
   `test_apply_roles.py`. Se espera la suite del modelo en verde en cada tarea. Prueba: las compuertas de «Global
   Constraints» en el último paso de cada tarea.

## Decisiones de este plan frente a la especificación y los documentos

| # | Hallazgo | Decisión |
|---|---|---|
| 1 | §6 pide ASM nuevos sin número; el último en uso es ASM-127 | ASM-128 (activación por lista del despliegue), ASM-129 (modelo por caso), ASM-130 (alcance de AI-05 por permiso con enmascarado), ASM-131 (límites de corrida) y ASM-132 (extracción sin OCR); §20.1 pasa a ASM 132 y total 553 (Tarea 1) |
| 2 | La especificación dice «UPS-14 está resuelta en wasichai 1.2.0 (wasichai#59, ADR-056)»; `platform-fit.md` §4.3 dice que el filtro llegó en 0.6.0 con WAS-ADR-0056 | Los documentos dicen «resuelta en wasichai (WAS-ADR-0056, wasichai/wasichai#59)» y que los cuatro puntos de extensión están en wasichai 1.2.0, la versión que usa SGSPE; se cita como `WAS-ADR-0056`, la forma que ya usa `platform-fit.md` (Tareas 1, 2, 3, 5 y 6) |
| 3 | §2.2.7 dice que POI «llega aquí» en F7; `build.gradle.kts` ya tiene `org.apache.poi:poi-ooxml:5.5.1` en `main` desde F2 (exportaciones XLSX, `platform-fit.md` §6) | ASM-132 dice que POI ya está en `main` y que F7 suma solo PDFBox a `main`; F8 reutiliza los dos (Tareas 1 y 6) |
| 4 | §2.2.6 dice que una corrida sin margen «no escribe nada», y P6 de `ai-architecture.md` exige una `ai_interaction` por llamada | No guarda sugerencias ni candidatos; la `ai_interaction` de cada llamada hecha queda registrada, como en AI-05 (`InteractionRunListener`, «también en corridas truncadas o fallidas») (Tareas 1 y 2) |
| 5 | La especificación da los estados de los cuatro errores nuevos, pero no su campo, y `api.md` §3.3 nombra el campo de cada código por fase | `AI_UNAVAILABLE` sobre `organization` (sin activación) o `provider` (sin proveedor); `AI_INPUT_TOO_LARGE` y `AI_NO_TEXT` sobre `target`; `AI_RUN_TRUNCATED` sobre `useCase`. 7.2a y 7.2b los usan así (Tarea 4) |
| 6 | §6 solo pide aceptar ADR-0005, pero ADR-0003 dice «se acepta al iniciar F7» y sus dos puntos abiertos (proveedor y AI-05) los cierran las decisiones 1 y 2 | ADR-0003 pasa a «Aceptado · 2026-10-12» con sus consecuencias al día; ADR-0005, a «Aceptado en parte» (etapa 1) (Tarea 5) |
| 7 | ADR-0005 y `ai-architecture.md` §13.1 fijan «la meta del criterio 2 al aceptar ADR-0005 en F7», y la especificación deja pgvector y el juego de evaluación fuera de F7 | La meta se fija con el juego de evaluación cuando se proponga la etapa 2; F7 no la fija (Tareas 2 y 5) |
| 8 | §2.2.4 da el modelo «por caso» con `sgspe.ai.models.<caso>`, pero AI-05 corre en `AgentService`, cuyo modelo es `wasichai.agent.model` | En AI-05, `wasichai.agent.model` sigue el mismo criterio de ASM-129; 7.2a lo fija en `application.yml` (Tareas 1 y 2) |
| 9 | La especificación §3.5 dice que JOB-07 no llama a un modelo en F7 ni deja `ai_interaction`; `security.md` §9, `domain-model.md` §8.3, `ai-architecture.md` §13.2 y ADR-0005 dicen que la crea | Esos cuatro lugares dicen que JOB-07 solo crearía `ai_interaction` al llamar a un modelo, lo que en F7 no hace; `workflows.md` ya lo dice así («cuando llama a un modelo») y no cambia (Tareas 2, 3, 5 y 6) |
| 10 | La trazabilidad §8 trae conteos por estado que no coinciden con sus filas desde antes de F5 (decisión 11 del plan 6.0) | Este plan cambia RF-AIK-005, RF-AIK-009 y RQ-AI-009 de «Upstream (UPS-14); Fuera del MVP» a «Diseñado; Fuera del MVP» y no toca §8; la comprobación de la Tarea 6 exige esa única diferencia en las filas (Upstream −3, Diseñado +3) |
| 11 | `ux-architecture.md` §5 («Asistente si es `ANALYST`») y `ux/screens.md` (inicio del ejecutivo) limitan el asistente a `ANALYST` en el contexto del ejecutivo (`READ_ONLY` o `ANALYST`) | Siguen siendo ciertas (de esos dos roles, solo `ANALYST` tiene CREATE sobre `ai_interaction`); la de `screens.md` se reescribe para decir por qué, y la de `ux-architecture.md` §5 no cambia (Tarea 7) |

---

## Mapa de archivos

| Ruta | Qué cambia | Tarea |
|---|---|---|
| `docs/requirements-analysis.md` | RF-AIK-005, RQ-AI-009, §18.1 (GAP-015), §18.2 (UPS-14), §19 (ASM-015 y ASM-128 a ASM-132), §20.1, §20.4 | 1 |
| `docs/ai-architecture.md` | §2 (P9), §6.1, §6.2, §8, §9.2, §11, §12.1, §12.2, §13.1, §13.2, §14, §15, §16, §18 | 2 |
| `docs/security.md` | §2 (amenaza 15), §9 (JOB-07), §11.5, §14, §15 | 3 |
| `docs/api.md` | §2 (asistente), §3.3 (estados y códigos de F7), §4.14 | 4 |
| `docs/adr/0003-abstraccion-de-ia.md`, `docs/adr/0005-rag-y-pgvector.md`, `docs/adr/0017-datos-personales-y-anonimizacion.md`, `docs/adr/0026-topologia-puertos-y-observabilidad.md`, `docs/adr/README.md` | Estados, contexto, decisión, consecuencias y referencias | 5 |
| `docs/roadmap.md`, `docs/requirements-traceability.md`, `docs/platform-fit.md`, `docs/architecture.md`, `docs/domain-model.md` | F7 (con «Estado») y F8; brechas, decisiones, riesgos y respuestas del checkpoint; tres filas de trazabilidad; encaje y versiones; riesgos y salidas; BC-18 y JOB-07 | 6 |
| `docs/ux/flows.md`, `docs/ux/ux-architecture.md`, `docs/ux/screens.md`, `docs/ux/design-system.md`, `docs/HISTORY.md` | Disponibilidad del asistente y estados; la entrada del plan 7.0 | 7 |

## Interfaces con los otros planes de F7

Lo que este plan deja para los siguientes (todos parten de `main` con 7.0 integrado):

- **7.1 (modelo).** ASM-130: el alcance de AI-05 lo da CREATE sobre `ai_interaction` en `model/roles.json`, con las
  filas de `security.md` §7.8 sin cambios; la regla de `evidence_quote` de §7.9 sin cambios; `RestrictedFields` sale de
  §7.9 (la prueba de 7.2a la compara).
- **7.2a (base y asistente).** ASM-128 (`sgspe.ai.enabled-organizations`, `503 AI_UNAVAILABLE` sobre `organization` o
  `provider`), ASM-129 (`sgspe.ai.models.<caso>` y `wasichai.agent.model`; anotar el modelo elegido en `roadmap.md` F7
  «Estado»), ASM-130 (los cuatro puntos de extensión con los nombres de `ai-architecture.md` §8), ASM-131
  (`sgspe.ai.limits.*`); el puerto `StructuredModel` (§6.2); las pruebas de `security.md` §14 «Asistente AI-05».
- **7.2b (casos con sugerencias).** ASM-131 (`422 AI_INPUT_TOO_LARGE` sobre `target`, `502 AI_RUN_TRUNCATED` sobre
  `useCase`) y ASM-132 (`422 AI_NO_TEXT` sobre `target`; PDFBox a `main`); la decisión 4 de este plan.
- **7.2c (conocimiento y duplicados).** `ai-architecture.md` §13.1 y §13.2: GIN sobre `to_tsvector('spanish', content)`,
  `websearch_to_tsquery`, `ts_rank_cd`, cinco fragmentos, unos 1 500 caracteres con solape; JOB-07 sin `ai_interaction`
  en F7 (decisión 9).
- **7.3 (portal).** `ux/*.md` con la disponibilidad nueva del asistente; los cuatro códigos para los mensajes de
  `design-system.md` §6.5.

## Orden

| Paso | Tareas | Por qué |
|---|---|---|
| 1 | 1 | Define los IDs que citan las demás |
| 2 | 2, 3, 4, 5, 6, 7, en ese orden | Archivos disjuntos; van en serie en la misma rama |
| 3 | 8 | Verifica todo, hace push y abre el PR |

---

### Tarea 1: IDs de F7 en el análisis

**Files:**
- Modify: `docs/requirements-analysis.md` (RF-AIK-005 ~l. 637, RQ-AI-009 ~l. 1146, GAP-015 ~l. 1255, UPS-14 ~l. 1294,
  ASM-015 ~l. 1335, tras ASM-127 ~l. 1447, §20.1 ~l. 1505-1506, §20.4 ~l. 1647)

**Interfaces:**
- Consume: la especificación §2.1 (decisiones 1 a 4), §2.2.1, §2.2.2, §2.2.6 y §2.2.7; las decisiones 1 a 4 y 8 de este
  plan.
- Produce: ASM-128 a ASM-132, que citan las Tareas 2 a 7; GAP-015 y UPS-14 resueltas; §20.1 con 553.

- [ ] **Paso 1: La comprobación falla**

```bash
cd ~/IdeaProjects/sgspe && python3 - <<'PY'
import re
t = open('docs/requirements-analysis.md', encoding='utf-8').read()
lines = t.splitlines()
def row(i):
    return next((l for l in lines if l.startswith(f'| {i} |')), '')
bad = []
for n in range(128, 133):
    if len(re.findall(rf'^\| ASM-{n} \|', t, re.M)) != 1:
        bad.append(f'ASM-{n}: no se define una sola vez')
if not re.search(r'^\| `ASM` \| 132 \|$', t, re.M):
    bad.append('§20.1: ASM no es 132')
if not re.search(r'^\| Total \| 553 \|$', t, re.M):
    bad.append('§20.1: el total no es 553')
if 'Resuelta' not in row('GAP-015') or 'Anthropic' not in row('GAP-015'):
    bad.append('GAP-015 no está resuelta con la API de Anthropic')
ups = row('UPS-14')
if 'Resuelta' not in ups or 'wasichai/wasichai#59' not in ups or 'WAS-ADR-0056' not in ups or 'ANALYST' in ups:
    bad.append('UPS-14 no está resuelta con WAS-ADR-0056 y wasichai/wasichai#59')
if 'hasta UPS-14' in row('RF-AIK-005') or 'ASM-130' not in row('RF-AIK-005'):
    bad.append('RF-AIK-005 sigue con «hasta UPS-14» o no cita ASM-130')
if 'se decide en GAP-015' in row('RQ-AI-009') or 'ASM-128' not in row('RQ-AI-009'):
    bad.append('RQ-AI-009 sigue con GAP-015 abierta o no cita ASM-128')
if 'ASM-128' not in row('ASM-015'):
    bad.append('ASM-015 no remite a ASM-128')
if 'Los IDs de F7 (ASM-128 a ASM-132) no retiran ninguno' not in t:
    bad.append('§20.4: falta la observación de F7')
for code, asm in (('AI_UNAVAILABLE', 'ASM-128'), ('AI_INPUT_TOO_LARGE', 'ASM-131'), ('AI_RUN_TRUNCATED', 'ASM-131'),
                  ('AI_NO_TEXT', 'ASM-132')):
    if code not in row(asm):
        bad.append(f'{asm} no nombra {code}')
print('\n'.join(bad) or 'ok')
raise SystemExit(1 if bad else 0)
PY
```

Esperado: salida 1 con las cinco líneas de los ASM, las dos de §20.1 y las de GAP-015, UPS-14, RF-AIK-005, RQ-AI-009,
ASM-015, §20.4 y los cuatro códigos.

- [ ] **Paso 2: §19, los supuestos nuevos**

Agregar, inmediatamente después de la fila de ASM-127 (columnas «ID | Supuesto | Por qué hace falta | Cómo revertirlo |
Afecta a»), estas cinco filas, una línea cada una:

```text
| ASM-128 | La IA se activa por organización con la lista `sgspe.ai.enabled-organizations` (slugs) de la configuración del despliegue, que el operador mantiene junto con la clave del proveedor (`ANTHROPIC_API_KEY`, solo por variable de entorno); `GET /api/sgspe/ai/status` responde siempre `{ enabled, providerAvailable, useCases[] }`, y sin activación o sin proveedor `runs`, `assistant/questions` y `knowledge/questions` responden `503 AI_UNAVAILABLE`, mientras aceptar, rechazar y marcar duplicado siguen funcionando | El modelo de dominio no tiene un objeto de ajustes de la organización y activar la IA depende del contrato con el proveedor (decisión del usuario del 2026-10-12); activar o desactivar pide redesplegar la configuración | Un objeto de ajustes del tenant con la marca, que un `ADMIN` cambie sin desplegar | RQ-AI-009, ASM-015, GAP-015, `ai-architecture.md` §12.1, `api.md` §3.3 y §4.14, ADR-0003 |
| ASM-129 | Un solo proveedor por despliegue, la API de Anthropic (GAP-015), con el modelo de cada caso en la configuración (`sgspe.ai.models.<caso>`, con valor por defecto en `application.yml`; en AI-05, el `wasichai.agent.model` del asistente, con el mismo criterio): `claude-haiku-4-5` para AI-07 y AI-08, y el modelo más capaz que reconozca el catálogo de Embabel 1.5.3 para AI-01 a AI-06; el plan 7.2a anota el elegido | Comparar pares y clasificar son tareas cortas que un modelo pequeño resuelve con menos costo y latencia; extraer, redactar y responder con citas piden el más capaz, y el catálogo de Embabel 1.5.3 no conoce los modelos Claude 5 (decisión del usuario del 2026-10-12) | Cambiar `sgspe.ai.models.<caso>` cuando el catálogo sume modelos; otro proveedor es otro starter de Embabel (RQ-AI-005) | RQ-AI-004, RQ-AI-005, `ai-architecture.md` §8 y §11, `architecture.md` §14 (riesgo 15), ADR-0003 |
| ASM-130 | AI-05 se ofrece a todo rol con CREATE sobre `ai_interaction` en una organización con la IA activada (`AiAgentAccess`, la `AgentAccessPolicy` de SGSPE, sin literales de rol): los resultados de las herramientas y el resumen de cada paso pasan por el `Pseudonymizer` con una tabla de equivalencias por pregunta; el valor de todo campo restringido de RQ-SEC-010 del objeto leído se reemplaza siempre por su marcador tipado, según `RestrictedFields` (la misma lista de los agentes propios, comparada con `security.md` §7.9); la respuesta se restaura salvo esos campos; sin tabla, el filtro falla cerrado y nada sale hacia el modelo; la `ai_interaction` lleva modelo y tokens. El diccionario de nombres es el de quien pregunta, y AI-05 no exige leer los nombres de `person` | UPS-14 está resuelta (WAS-ADR-0056) y el usuario decidió el 2026-10-12 ofrecer AI-05 a todos, seudonimizado; con la matriz de `security.md` §7.8 quedan fuera los roles sin CREATE sobre `ai_interaction` (entre ellos `READ_ONLY`, `AUDITOR` y `PROJECT_ADMIN`). Riesgo residual: un nombre escrito en un texto libre que no está registrado como `person` puede llegar al proveedor (RQ-REP-033) | Quitar CREATE sobre `ai_interaction` a un rol en `model/roles.json`; volver a una lista de roles exigiría una acción declarada nueva | RF-AIK-005, RF-AIK-009, RQ-AI-009, RQ-SEC-010, UPS-14, `ai-architecture.md` §8, `security.md` §11.5, `api.md` §4.14 |
| ASM-131 | Las corridas de IA son síncronas, dentro de la petición, con límites del servidor (`sgspe.ai.limits.*`): tamaño del texto de entrada (200 000 caracteres por defecto), tokens de salida y acciones por corrida. Una entrada más grande responde `422 AI_INPUT_TOO_LARGE` antes de llamar al modelo; una corrida que se queda sin margen de tokens o de acciones responde `502 AI_RUN_TRUNCATED`; en los dos casos no se guarda ninguna sugerencia ni candidato, y la `ai_interaction` de cada llamada hecha queda registrada | Una corrida dentro de la petición tiene que terminar en un tiempo acotado, y un documento largo puede pasar el contexto del modelo; F7 no tiene corridas asíncronas (`ai-architecture.md` §16) | Cambiar los valores por despliegue en `application.yml`; una variante asíncrona necesita un ADR que conserve el principio de leer como quien pide | RQ-AI-011, RQ-AI-014, `ai-architecture.md` §6.2 y §16, `api.md` §3.3 y §4.14 |
| ASM-132 | `TextExtraction` lee el texto de los PDF con capa de texto (Apache PDFBox) y de los DOCX (Apache POI), sin OCR: un PDF sin texto o un formato distinto responde `422 AI_NO_TEXT` en `POST /api/sgspe/ai/runs`, y JOB-07 no lo indexa. PDFBox pasa a dependencia de `main` en F7 (F8 lo preveía solo en pruebas); POI (`poi-ooxml`) ya está en `main` desde F2 por las exportaciones XLSX, y F8 reutiliza los dos | Las actas, convenios e informes llegan como PDF o Word generados; el OCR suma una dependencia nativa y errores de lectura que la cita literal (`CitationCheck`) no tolera | Sumar OCR con su prueba y marcar como reconocido el texto que produzca | RF-AIK-001, RF-AIK-003, RF-AIK-006, RQ-AI-008, `ai-architecture.md` §13.2 y §16, `roadmap.md` F7 y F8, `platform-fit.md` §6 |
```

- [ ] **Paso 3: Lo que cambia por los IDs nuevos**

**RF-AIK-005** (§16). Reemplazar la fila entera:

```text
| RF-AIK-005 | AI-05: responder preguntas sobre los datos del sistema con el asistente de solo lectura de wasichai-agent, que actúa como el usuario que pregunta; hasta UPS-14, solo para roles sin lectura de los campos restringidos | [PRM-01 §9] [WAS-ADR-0014] | [PRM] [PLT] | BC-18 | F7 |
```

por:

```text
| RF-AIK-005 | AI-05: responder preguntas sobre los datos del sistema con el asistente de solo lectura de wasichai-agent, que actúa como el usuario que pregunta, con los resultados de sus herramientas seudonimizados y los campos restringidos enmascarados, para todo rol con CREATE sobre `ai_interaction` (ASM-130) | [PRM-01 §9] [WAS-ADR-0014] | [PRM] [PLT] [ASM] | BC-18 | F7 |
```

**RQ-AI-009** (§17). Reemplazar la fila entera:

```text
| RQ-AI-009 | Seudonimizar antes de llamar al LLM; la IA viene desactivada por defecto y se activa por organización (ASM-015); los secretos nunca se guardan ni se registran; la residencia de datos se decide en GAP-015 | [PRM-01 §10] [USR-07] | [PRM] [USR] [ASM] | BC-18 | F7 |
```

por:

```text
| RQ-AI-009 | Seudonimizar antes de llamar al LLM, también los resultados de las herramientas del asistente (ASM-130); la IA viene desactivada por defecto y se activa por organización con la lista del despliegue (ASM-015, ASM-128); los secretos nunca se guardan ni se registran; el proveedor es la API de Anthropic y los datos seudonimizados se procesan fuera del Perú (GAP-015) | [PRM-01 §10] [USR-07] | [PRM] [USR] [ASM] | BC-18 | F7 |
```

**GAP-015** (§18.1). Sigue la convención de GAP-025: la resolución va en la columna «Valor por defecto». Reemplazar la
fila entera:

```text
| GAP-015 | No hay proveedor de IA ni política de residencia de datos | ¿Qué proveedor de LLM se usa y dónde pueden procesarse los datos? | Se decide antes de F7; IA desactivada por defecto (ASM-015) | RQ-AI-005, RQ-AI-009 |
```

por:

```text
| GAP-015 | No hay proveedor de IA ni política de residencia de datos | ¿Qué proveedor de LLM se usa y dónde pueden procesarse los datos? | Resuelta por la decisión del usuario del 2026-10-12: la API de Anthropic, proveedor por defecto de `wasichai-spring-boot-starter-agent`, con seudonimización antes de cada llamada y la IA desactivada por defecto (ASM-015, ASM-128); los datos seudonimizados se procesan fuera del Perú, una consecuencia aceptada (`ai-architecture.md` §11) | RQ-AI-005, RQ-AI-009 |
```

**UPS-14** (§18.2). Sigue la convención de UPS-24: la resolución va en «Mientras tanto». Reemplazar la fila entera:

```text
| UPS-14 | Puntos de extensión en wasichai-agent para seudonimizar los resultados de las herramientas antes del LLM, activarse por organización e informar los tokens de cada respuesta | AI-05 envía al proveedor, sin seudonimizar, lo que la persona puede leer, en contra de RQ-AI-009, y su `ai_interaction` queda sin tokens | AI-05 solo en organizaciones con la IA activada, con el proveedor decidido (GAP-015) y para roles sin lectura de los campos restringidos, hoy `ANALYST` (RQ-SEC-010), una consecuencia que se decide en el checkpoint; `POST /api/agent/ask` cerrado por HTTP | F7 | RF-AIK-005, RF-AIK-009, RQ-AI-009 |
```

por:

```text
| UPS-14 | Puntos de extensión en wasichai-agent para seudonimizar los resultados de las herramientas antes del LLM, activarse por organización e informar los tokens de cada respuesta | AI-05 envía al proveedor, sin seudonimizar, lo que la persona puede leer, en contra de RQ-AI-009, y su `ai_interaction` queda sin tokens | Resuelta en wasichai (WAS-ADR-0056, wasichai/wasichai#59): wasichai 1.2.0 trae `AgentAccessPolicy`, `AgentResultFilter`, `AgentAnswerFilter` y `AgentRunListener`. SGSPE la adopta en F7 (ASM-130): AI-05 para todo rol con CREATE sobre `ai_interaction` en una organización activada, con los resultados de las herramientas seudonimizados, los campos restringidos enmascarados, la respuesta restaurada y la `ai_interaction` con tokens; `POST /api/agent/ask` sigue cerrado por HTTP | F7 | RF-AIK-005, RF-AIK-009, RQ-AI-009 |
```

**ASM-015** (§19). Reemplazar la fila entera:

```text
| ASM-015 | La IA viene desactivada por defecto y se seudonimiza antes de llamar al LLM | Datos personales y falta de política de proveedor (GAP-015) | Activarla por organización | RQ-AI-009 |
```

por:

```text
| ASM-015 | La IA viene desactivada por defecto y se seudonimiza antes de llamar al LLM | Datos personales hacia un proveedor externo (GAP-015, resuelta con la API de Anthropic) | Activarla por organización con la lista del despliegue (ASM-128) | RQ-AI-009, ASM-128 |
```

**§20.1.** `| `ASM` | 127 |` pasa a `| `ASM` | 132 |` y `| Total | 548 |` a `| Total | 553 |`.

**§20.4.** Después de la viñeta «Los IDs de F6 (ASM-120 a ASM-127) no retiran ninguno: …» (que termina en «y ASM-125
adopta UPS-13 ya resuelta.»), agregar:

```text
- Los IDs de F7 (ASM-128 a ASM-132) no retiran ninguno: ASM-128 fija el mecanismo de activación de ASM-015, y
  ASM-130 adopta UPS-14 ya resuelta.
```

- [ ] **Paso 4: Verificar y commit**

Repetir la comprobación del Paso 1 (esperado: `ok`), correr `yarn -s prettier --check docs/requirements-analysis.md` y
las compuertas de «Global Constraints». Después:

```bash
git add docs/requirements-analysis.md
git commit -F - <<'MSG'
docs: IDs de la fase 7 (ASM-128 a ASM-132), GAP-015 y UPS-14 resueltas

Activación por la lista del despliegue, modelo por caso, AI-05 por permiso y seudonimizado,
límites de corrida y extracción de texto sin OCR; la API de Anthropic como proveedor; §20.1 pasa
a 553 IDs.

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01RmiD2JE3gkHdBNUraZkjgM
MSG
```

---

### Tarea 2: Arquitectura de IA

**Files:**
- Modify: `docs/ai-architecture.md` (§2 P9 ~l. 48; §6.1 ~l. 213; §6.2 ~l. 236-239; §8 ~l. 274-306; §9.2 ~l. 359; §11
  ~l. 404-418; §12.1 ~l. 426-434; §12.2 ~l. 440-441 y ~l. 476; §13.1 ~l. 508-516; §13.2 ~l. 535-542; §14 ~l. 587; §15
  ~l. 596-633; §16 ~l. 644-652; §18 ~l. 700)

**Interfaces:**
- Consume: ASM-128 a ASM-132 (Tarea 1); la especificación §2.2 y §3.1 a §3.5; las decisiones 4, 7, 8 y 9.
- Produce: los nombres de las piezas de AI-05 y el puerto `StructuredModel`, que citan `security.md`, `api.md`, los ADR y
  el roadmap (Tareas 3 a 6) y que 7.2a implementa.

- [ ] **Paso 1: La comprobación falla**

```bash
cd ~/IdeaProjects/sgspe && python3 - <<'PY'
t = open('docs/ai-architecture.md', encoding='utf-8').read()
bad = []
for old in ('hasta UPS-14', 'Mientras no exista', 'Solo `ANALYST`', 'hoy, solo', '1.5.2', '**Propuesta (ADR-0005):**',
            '**Propuesta (ADR-0003):**', 'el proveedor se decide antes (GAP-015)', 'No hay proveedor elegido',
            'que se fija al aceptar ADR-0005 en F7', 'deja una `ai_interaction` por documento cuando llama a un modelo'):
    if old in t:
        bad.append(f'queda «{old}»')
for new in ('AiAgentAccess', 'PseudonymizingResultFilter', 'RestoringAnswerFilter', 'InteractionRunListener',
            'AgentHttpGate', 'AssistantQuestions', 'AgentUsage', 'RestrictedFields', 'StructuredModel', '1.5.3',
            '`503 AI_UNAVAILABLE`', '`422 AI_INPUT_TOO_LARGE`', '`502 AI_RUN_TRUNCATED`', '`422 AI_NO_TEXT`',
            'ASM-128', 'ASM-129', 'ASM-130', 'ASM-131', 'ASM-132', '`ai-live`', 'websearch_to_tsquery', 'ts_rank_cd',
            'fuera del Perú', 'ANTHROPIC_API_KEY'):
    if new not in t:
        bad.append(f'falta {new}')
print('\n'.join(bad) or 'ok')
raise SystemExit(1 if bad else 0)
PY
```

Esperado: salida 1.

- [ ] **Paso 2: §2 (P9), §6.1 y §6.2**

En la fila P9 de §2, reemplazar `(AI-05 es la excepción hasta UPS-14, §8)` por
`(también los resultados de las herramientas del asistente AI-05, §8)`.

En la tabla de §6.1, en la fila `DocumentExtractionAgent`, reemplazar `Extracción del texto (PDF o DOCX con texto) y
segmentación` por `` Extracción del texto (PDF o DOCX con texto, con `TextExtraction` y sin OCR, ASM-132) y
segmentación ``.

En §6.2, reemplazar:

```text
- El servidor fija los límites de cada corrida: tamaño de la entrada, tokens y número de acciones. Embabel limita además a 20 las
  vueltas de herramientas [WAS-ADR-0015]. Una corrida que se queda sin margen termina sin escribir nada.
- La versión de Embabel es la del BOM de wasichai (1.5.2) [WAS-ADR-0015]; SGSPE no fija otra (RNF-MNT-001). El starter
  `wasichai-spring-boot-starter-agent` se agrega en F7.
```

por:

```text
- El servidor fija los límites de cada corrida (`sgspe.ai.limits.*`, ASM-131): tamaño del texto de entrada (200 000
  caracteres por defecto), tokens de salida y acciones por corrida. Embabel limita además a 20 las vueltas de
  herramientas [WAS-ADR-0015]. Una entrada más grande responde `422 AI_INPUT_TOO_LARGE` antes de llamar al modelo, y una
  corrida que se queda sin margen responde `502 AI_RUN_TRUNCATED`: ninguna de las dos guarda sugerencias ni candidatos,
  y la `ai_interaction` de cada llamada hecha queda registrada (P6).
- La llamada al modelo pasa por un puerto propio, `StructuredModel`: recibe el prompt ya seudonimizado, la versión del
  prompt, el modelo y los límites, y devuelve la salida tipada con el uso de tokens. `EmbabelStructuredModel` lo
  implementa sobre la plataforma de agentes, y las pruebas lo sustituyen (§15).
- La versión de Embabel es la del BOM de wasichai 1.2.0 (1.5.3) [WAS-ADR-0015]; SGSPE no fija otra (RNF-MNT-001). El
  starter `wasichai-spring-boot-starter-agent` se agrega en F7.
```

- [ ] **Paso 3: §8, el asistente con sus puntos de extensión**

En §8 la lista inicial («Lo que aporta wasichai-agent…», cinco viñetas) no cambia. Reemplazar todo lo que va desde el
párrafo «Los ejemplos del prompt ("¿Qué compromisos están vencidos en la Comunidad X?", …» hasta la fila de la tabla
`| Asistente AI-05 | UPS-14, RF-AIK-005 | … | Solo `ANALYST` |` inclusive (el bloque termina justo antes de
`## 9. Registros: sugerencias, candidatos e interacciones`), por:

```text
Los ejemplos del prompt ("¿Qué compromisos están vencidos en la Comunidad X?", "¿Qué stakeholders tuvieron reuniones durante los
últimos 90 días?") se responden con esas consultas y conteos [PRM-01 §9]. SGSPE lo integra con los cuatro puntos de
extensión de wasichai-agent, que resuelven UPS-14 (WAS-ADR-0056; en wasichai 1.2.0) (ASM-130):

1. La pantalla de asistente del portal pregunta por `POST /api/sgspe/ai/assistant/questions`. `AssistantQuestions`
   comprueba que la organización tenga la IA activada (§12.1), abre una tabla de equivalencias nueva (`PseudonymTable`)
   para la pregunta y la pone en el contexto de la corrutina, llama en proceso a `AgentService.ask` en ese mismo contexto
   y devuelve la respuesta ya restaurada, con sus pasos y la marca de truncado.
2. **`AiAgentAccess`** (`AgentAccessPolicy`) responde `Denied` si la organización no está activada o si la persona no
   tiene CREATE sobre `ai_interaction`. El alcance lo da el permiso, no una lista de roles ni un literal de rol: con la
   matriz de `security.md` §7.8 quedan fuera, entre otros, `READ_ONLY`, `AUDITOR` y `PROJECT_ADMIN`. Así
   `GET /api/agent/status` también dice `enabled=false` a quien no puede usarlo.
3. **`PseudonymizingResultFilter`** (`AgentResultFilter`) seudonimiza cada resultado de herramienta y el resumen de cada
   paso con la tabla de la pregunta y, para el objeto que la herramienta leyó (`input.objectName` o el `object` del
   registro), reemplaza el valor de todo campo restringido de RQ-SEC-010 por su marcador tipado. La lista de campos
   restringidos sale de una sola fuente, `RestrictedFields`, la misma de los agentes propios, y una prueba la compara con
   `security.md` §7.9. El texto que no es JSON solo se seudonimiza. Sin tabla en el contexto, el filtro falla cerrado:
   lanza y nada sale hacia el modelo.
4. **`RestoringAnswerFilter`** (`AgentAnswerFilter`) restaura los marcadores de la respuesta final con la tabla de la
   pregunta. Los de los campos restringidos enmascarados no se restauran: su marcador queda en la respuesta. La tabla no
   se guarda ni se registra.
5. **`InteractionRunListener`** (`AgentRunListener`) escribe la `ai_interaction` de AI-05 con el modelo y los tokens de
   `AgentUsage`, la latencia y el SHA-256 de la pregunta como `input_reference`, también en las corridas truncadas o
   fallidas.
6. **`AgentHttpGate`**, un `WebFilter` de `sgspe.ai`, responde 403 a `POST /api/agent/ask` cuando llega por HTTP, de
   modo que toda pregunta pasa por la activación, la seudonimización y el registro; `GET /api/agent/status` queda
   abierto. `/admin` no registra `agentModule()`.
7. El alcance por proyecto del asistente lo da `ProjectReadScope`, que incluye al agente entre las rutas de lectura
   (RQ-SEC-006, ADR-0029).

El diccionario de nombres es el de quien pregunta: si la persona no puede leer los nombres de `person`, las
herramientas ya no los devuelven, y por eso AI-05, a diferencia de `runs` y `knowledge/questions`, no exige leerlos. El
riesgo residual son los nombres escritos en textos libres que no están registrados como `person`, como en minutas y
descripciones (RQ-REP-033, §12.2). El modelo del asistente es `wasichai.agent.model`, con el criterio de ASM-129 (§11).
```

- [ ] **Paso 4: §9.2, §11 y §12**

En la tabla de §9.2, reemplazar la fila:

```text
| tokens | `input_tokens`, `output_tokens` | Los que informa el proveedor; vacíos en AI-05 hasta UPS-14 (§8) |
```

por:

```text
| tokens | `input_tokens`, `output_tokens` | Los que informa el proveedor; en AI-05, los de `AgentUsage`, que recibe `InteractionRunListener` (§8) |
```

En §11, reemplazar desde la viñeta «- **Un solo proveedor por despliegue.**» hasta la viñeta «- **Secretos.**» inclusive,
es decir:

```text
- **Un solo proveedor por despliegue.** Los agentes de `sgspe.ai` comparten la plataforma de agentes y la configuración del
  proveedor con wasichai-agent. El modelo de cada caso se configura (`sgspe.ai.models.<caso>`), dentro del catálogo del
  proveedor.
- **Catálogo de modelos.** El modelo por defecto de wasichai es `claude-haiku-4-5`, porque el catálogo de Embabel 1.5.2 no conoce
  `claude-opus-5`; wasichai lo registra como una regresión de capacidad que una nueva versión de Embabel puede resolver
  [WAS-ADR-0015].
- **Residencia de datos.** No hay proveedor elegido ni política de residencia (GAP-015), y sin fuente normativa ninguna regla se
  presenta como obligación legal (GAP-001). La decisión se toma antes de F7 (GAP-015), entre estas opciones:
  - un proveedor que procese en una región aceptable;
  - un modelo autoalojado detrás de un starter de proveedor de Embabel;
  - mantener la IA desactivada.
- **Secretos.** La clave llega por variable de entorno (`ANTHROPIC_API_KEY` o la del proveedor elegido) y nunca se guarda en la
  base, en `ai_interaction`, en los prompts ni en los logs (RQ-AI-006, RQ-AI-009).
```

por:

```text
- **Proveedor decidido.** La API de Anthropic, el proveedor por defecto de `wasichai-spring-boot-starter-agent` (GAP-015,
  decisión del usuario del 2026-10-12). Un solo proveedor por despliegue: los agentes de `sgspe.ai` comparten la
  plataforma de agentes y la configuración del proveedor con wasichai-agent.
- **Modelo por caso (ASM-129).** `sgspe.ai.models.<caso>`, con valor por defecto en `application.yml`: `claude-haiku-4-5`
  para AI-07 y AI-08, y el modelo más capaz que reconozca el catálogo de Embabel para AI-01 a AI-06 (en AI-05, el
  `wasichai.agent.model` del asistente, con el mismo criterio). El plan 7.2a anota el modelo elegido.
- **Catálogo de modelos.** La versión de Embabel es la del BOM de wasichai 1.2.0 (1.5.3), y su catálogo no conoce los
  modelos Claude 5: si una versión nueva los suma, se cambian por configuración [WAS-ADR-0015].
- **Residencia de datos.** Los datos seudonimizados se procesan en la infraestructura de Anthropic, fuera del Perú: es
  una consecuencia aceptada de la decisión del usuario, y sin fuente normativa ninguna regla se presenta como obligación
  legal (GAP-001). Lo que llega al proveedor es lo que la persona puede leer, sin campos restringidos y con los nombres
  registrados reemplazados (§12.2); por eso la IA viene desactivada y se activa por organización (§12.1).
- **Secretos.** La clave llega solo por la variable de entorno `ANTHROPIC_API_KEY` y nunca se guarda en la base, en
  `ai_interaction`, en los prompts, en los logs ni en un archivo del repositorio (RQ-AI-006, RQ-AI-009).
```

En §12.1:

- reemplazar `- **Propuesta (ADR-0003):** el mecanismo es una lista` por `- **Decidido (ASM-128, ADR-0003):** el mecanismo
  es una lista`;
- después de la subviñeta `  - Si se quiere activar desde la aplicación, el modelo de dominio necesita ese objeto.`,
  agregar la subviñeta `  - Activar o desactivar una organización pide redesplegar la configuración.`;
- reemplazar `Solo los endpoints que llaman a un modelo responden 503 si la organización no tiene la IA activada o el
  proveedor no responde,` por `` Solo los endpoints que llaman a un modelo responden `503 AI_UNAVAILABLE` si la
  organización no tiene la IA activada o no hay proveedor, `` (la frase sigue con «como el asistente de wasichai sin
  clave:»).

En §12.2:

- en el primer párrafo, reemplazar `propios, fragmentos recuperados para AI-06` por `propios y del asistente AI-05 (§8),
  fragmentos recuperados para AI-06`;
- en la última viñeta, reemplazar `Por eso la IA viene desactivada, el proveedor se decide antes (GAP-015) y solo se
  envían los fragmentos necesarios.` por `Por eso la IA viene desactivada, el proveedor quedó decidido antes de F7
  (GAP-015) y solo se envían los fragmentos necesarios.`

- [ ] **Paso 5: §13, §14, §15, §16 y §18**

En §13.1, reemplazar:

```text
(RQ-AI-008, CON-035) [PRM-01 §20]. **Propuesta (ADR-0005):** AI-06 empieza sin vectores, en dos etapas.

- **Etapa 1 (F7):** la tabla fija `knowledge_chunk` guarda fragmentos de texto con un índice de búsqueda de texto completo de
  PostgreSQL en español, sin extensiones nuevas. La recuperación aplica los filtros de §13.3 y la respuesta cita sus fuentes.
```

por:

```text
(RQ-AI-008, CON-035) [PRM-01 §20]. **Decidido (ADR-0005, aceptado en su etapa 1):** AI-06 empieza sin vectores, en dos
etapas.

- **Etapa 1 (F7):** la tabla fija `knowledge_chunk` guarda fragmentos de texto con un índice GIN sobre
  `to_tsvector('spanish', content)`, sin extensiones nuevas. La consulta usa `websearch_to_tsquery('spanish', :q)` y
  ordena por `ts_rank_cd`; los cinco mejores fragmentos que pasan los filtros de §13.3 llegan al modelo, y la respuesta
  cita sus fuentes. La columna `embedding` no se crea en F7.
```

En el mismo apartado, reemplazar `(deben cumplirse todas; la decisión queda en ADR-0005, que se acepta en F7):` por
`(deben cumplirse todas; la decisión queda en ADR-0005):`, y en el criterio 2, `que se fija al aceptar ADR-0005 en F7.`
por `que se fija con ese juego cuando se proponga la etapa 2 (F7 no lo construye).`

En §13.2, reemplazar:

```text
  - extrae el texto, lo segmenta y, en la etapa 2, lo seudonimiza y lo vectoriza;
  - escribe los fragmentos con SQL propio;
  - deja una `ai_interaction` por documento cuando llama a un modelo, sin `requested_by_user_id` y con `run_by_platform` (§9.2).
```

por:

```text
  - extrae el texto con `TextExtraction` (ASM-132), lo segmenta en fragmentos de unos 1 500 caracteres con solape y, en
    la etapa 2, lo seudonimiza y lo vectoriza;
  - escribe los fragmentos con SQL propio;
  - en F7 no llama a un modelo, así que no deja `ai_interaction`; en la etapa 2 dejará una por documento, sin
    `requested_by_user_id` y con `run_by_platform` (§9.2).
```

y en la última viñeta de §13.2, `no hay OCR en F7 (§16).` por `no hay OCR en F7 (§16, ASM-132).`

En §14, reemplazar `y solo ellos responden 503 cuando la IA no está` por `` y solo ellos responden `503 AI_UNAVAILABLE`
cuando la IA no está ``.

En §15:

- en la fila FakeModel, reemplazar `del puerto propio de `sgspe.ai` que pide` por `` del puerto propio de `sgspe.ai`,
  `StructuredModel`, que pide ``;
- reemplazar las viñetas «Unitarias» y «Del agente, con StubModel»:

```text
- **Unitarias** (Kotlin puro): `Pseudonymizer` (marcadores, formatos y paso a marcadores estables), verificador de citas
  literales sobre el fragmento seudonimizado, esquemas de salida y valores de catálogo, y manifiesto de prompts (versiones
  publicadas inmutables).
- **Del agente, con StubModel:** una corrida de AI-03 sobre un acta ficticia produce candidatos con cita y confianza; una corrida
  que agota sus límites termina sin escribir.
```

por:

```text
- **Unitarias** (Kotlin puro): `Pseudonymizer` y `PseudonymTable` (marcadores, formatos, paso a marcadores estables y
  restauración), `CitationCheck` (citas literales sobre el fragmento seudonimizado), esquemas de salida y valores de
  catálogo, manifiesto de prompts (`PromptManifestTest`: versiones publicadas inmutables), `RestrictedFields` frente a
  `security.md` §7.9, `DuplicateCandidates` y `TextExtraction` con un PDF y un DOCX ficticios (un PDF sin texto da
  `AI_NO_TEXT`).
- **Con FakeModel y MockModel:** lo que sale hacia el modelo está seudonimizado y nunca lleva un número de documento, un
  teléfono ni un correo; versión de prompt y límites.
- **Del agente, con StubModel:** una corrida de AI-03 sobre un acta ficticia produce candidatos con cita y confianza; una
  corrida que agota sus límites no guarda sugerencias ni candidatos. AI-05 completo: el modelo recibe los resultados de
  las herramientas seudonimizados y sin valores de campos restringidos, la respuesta vuelve restaurada, un filtro que
  falla no envía nada y la `ai_interaction` queda con tokens.
```

- en «De integración», reemplazar `` `runs`, `assistant/questions` y `knowledge/questions` responden 503, `` por
  `` `runs`, `assistant/questions` y `knowledge/questions` responden `503 AI_UNAVAILABLE`, ``, y después de la subviñeta
  `  - una llamada HTTP directa a `POST /api/agent/ask` recibe 403.` agregar:

```text
  - `GET /api/agent/status` dice `enabled=false` a un rol sin CREATE sobre `ai_interaction` y en una organización sin IA;
  - WF-K de `ai_suggestion`, transición por transición y rol por rol (`AiSuggestionWorkflowApiTest`).
```

- reemplazar `- **Contra un proveedor real:** una suite con su propia etiqueta, fuera de la integración continua por
  defecto,` por `` - **Contra un proveedor real:** una suite con su propia etiqueta (`ai-live`), fuera de la integración
  continua, ``;
- reemplazar la última viñeta `- Ningún perfil se llama `test` [WAS-ADR-0015] [WAS-DOC modules/testing.md], y los
  fixtures son ficticios (RQ-SEC-018).` por:

```text
- Ningún perfil se llama `test` [WAS-ADR-0015] [WAS-DOC modules/testing.md], los fixtures son ficticios (RQ-SEC-018) y
  las variables de la clave quedan en blanco en `integrationTest`, así que ninguna prueba de la CI llama al proveedor.
```

En §16, reemplazar:

```text
- **AI-05:** sin seudonimización ni activación por organización propias del módulo, y sin tokens, hasta que exista UPS-14. Por
  eso se ofrece solo a roles sin acceso a campos restringidos (hoy `ANALYST`), salvo que el checkpoint decida otra cosa (§8).
- **Sin OCR en F7:** un documento escaneado sin texto no se procesa.
```

por:

```text
- **AI-05:** se ofrece a todo rol con CREATE sobre `ai_interaction` en una organización activada (ASM-130). Un nombre
  escrito en un texto libre que no está registrado como `person` puede llegar al proveedor (RQ-REP-033), y un fallo del
  filtro de resultados cierra la corrida sin enviar nada (§8).
- **Sin OCR en F7 (ASM-132):** un documento escaneado sin texto no se procesa (`422 AI_NO_TEXT`) ni se indexa.
```

y en el mismo §16, `- **Corridas síncronas:**` por `- **Corridas síncronas (ASM-131):**` y `- **Restricciones de
Embabel 1.5.2:**` por `- **Restricciones de Embabel 1.5.3:**`.

En la tabla de §18, después de la fila `| UPS-14 | §8, §9.2, §16 |`, agregar:

```text
| GAP-015 | §11 |
| ASM-128 | §12.1 |
| ASM-129 | §11 |
| ASM-130 | §8, §16 |
| ASM-131 | §6.2, §16 |
| ASM-132 | §6.1, §13.2, §16 |
```

- [ ] **Paso 6: Verificar y commit**

Repetir la comprobación del Paso 1 (esperado: `ok`), correr `yarn -s prettier --check docs/ai-architecture.md` y las
compuertas de «Global Constraints». Después:

```bash
git add docs/ai-architecture.md
git commit -F - <<'MSG'
docs: arquitectura de IA de F7 con AI-05 seudonimizado

Los cuatro puntos de extensión del asistente, el puerto StructuredModel, la API de Anthropic con
el modelo por caso, Embabel 1.5.3, los límites y los códigos de error, la etapa 1 de AI-06 y sus
pruebas.

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01RmiD2JE3gkHdBNUraZkjgM
MSG
```

---

### Tarea 3: Seguridad

**Files:**
- Modify: `docs/security.md` (§2 amenaza 15 ~l. 124; §9 ~l. 904; §11.5 ~l. 1427-1437; §14 fila «IA» ~l. 1585; §15
  ~l. 1602, 1603 y 1607)

**Interfaces:**
- Consume: ASM-128 y ASM-130 (Tarea 1); `ai-architecture.md` §8 (Tarea 2); la decisión 9.
- Produce: la lista de roles de AI-05 y la fila «Asistente AI-05» de §14, que citan la trazabilidad (Tarea 6) y 7.2a.

- [ ] **Paso 1: La comprobación falla**

```bash
cd ~/IdeaProjects/sgspe && python3 - <<'PY'
import re
t = open('docs/security.md', encoding='utf-8').read()
bad = []
# Review Focus 4: los roles de §11.5 son los que tienen CREATE sobre ai_interaction en §7.8
abbr = dict(re.findall(r'^\| ([A-Z]{3}) \| `([A-Z_]+)` \|', t, re.M))
hdr = next(l for l in t.splitlines() if l.startswith('| Objeto | PAD |'))
row = next(l for l in t.splitlines() if l.startswith('| `ai_interaction` |'))
cols = [c.strip() for c in hdr.strip('|').split('|')][1:]
cells = [c.strip() for c in row.strip('|').split('|')][1:]
creators = {abbr[c] for c, v in zip(cols, cells) if 'C' in v}
sec = t.split('### 11.5 IA', 1)[1].split('\n### ', 1)[0]
ai05 = next((p for p in sec.split('\n- ') if 'AI-05' in p), '')
named = set(re.findall(r'`([A-Z_]{5,})`', ai05.split('no lo usan')[0])) - {'READ_ONLY', 'AUDITOR', 'PROJECT_ADMIN'}
if named != creators:
    bad.append(f'§11.5 nombra {sorted(named)}; la matriz da {sorted(creators)}')
for old in ('hoy, solo `ANALYST`', 'Solo `ANALYST` (§11.5)', 'UPS-12, UPS-14, UPS-23', 'se deciden en GAP-015',
            'asistente solo para roles sin campos restringidos', 'que crea `ai_interaction` y escribe'):
    if old in t:
        bad.append(f'queda «{old}»')
for new in ('AiAgentAccess', 'RestrictedFields', 'ASM-128', 'ASM-130', '`503 AI_UNAVAILABLE`', 'WAS-ADR-0056',
            'fuera del Perú'):
    if new not in t:
        bad.append(f'falta {new}')
if len(re.findall(r'^\| Asistente AI-05 \|', t, re.M)) != 2:
    bad.append('falta la fila «Asistente AI-05» en §14 o en §15')
print('\n'.join(bad) or 'ok')
raise SystemExit(1 if bad else 0)
PY
```

Esperado: salida 1.

- [ ] **Paso 2: §2 y §9**

En la tabla de amenazas de §2, reemplazar la fila:

```text
| 15 | I | Datos personales enviados al proveedor de LLM | IA desactivada por defecto, seudonimización en los agentes propios y asistente solo para roles sin campos restringidos (§11.5) | GAP-015 |
```

por:

```text
| 15 | I | Datos personales enviados al proveedor de LLM | IA desactivada por defecto y activada por organización (ASM-128); seudonimización antes de cada llamada, también de los resultados de las herramientas de AI-05, con los campos restringidos enmascarados siempre (§11.5, ASM-130) | GAP-015 (resuelta) |
```

En §9 (trabajos como la plataforma), reemplazar `(JOB-05) y `KnowledgeIndexJob` (JOB-07), que crea `ai_interaction` y
escribe la tabla fija `knowledge_chunk`.` por:

```text
(JOB-05) y `KnowledgeIndexJob` (JOB-07), que escribe la tabla fija `knowledge_chunk` y solo crearía `ai_interaction` al
llamar a un modelo, lo que en F7 no hace (`ai-architecture.md` §13.2).
```

- [ ] **Paso 3: §11.5**

Reemplazar la primera viñeta:

```text
- La IA viene desactivada y se activa por organización (ASM-015, RQ-AI-009). El proveedor y la residencia de los datos
  se deciden en GAP-015.
```

por:

```text
- La IA viene desactivada y se activa por organización con la lista `sgspe.ai.enabled-organizations` del despliegue
  (ASM-015, ASM-128, RQ-AI-009). El proveedor es la API de Anthropic (GAP-015, decisión del usuario del 2026-10-12): los
  datos seudonimizados se procesan fuera del Perú, una consecuencia aceptada (`ai-architecture.md` §11).
```

Reemplazar la tercera viñeta:

```text
- El asistente de wasichai-agent (AI-05) actúa como el usuario que pregunta, así que se aplican sus permisos y su
  visibilidad de campos, y no tiene herramientas de escritura [WAS-ADR-0014]. Pero no tiene dónde seudonimizar lo que leen
  sus herramientas (UPS-14). Mientras tanto se ofrece solo en organizaciones con la IA activada y a roles sin acceso a
  ningún campo restringido de §7.9 que puedan registrar la `ai_interaction`: hoy, solo `ANALYST`. `READ_ONLY`, `AUDITOR`
  y `PROJECT_ADMIN` tampoco leen campos restringidos, pero no tienen CREATE sobre `ai_interaction`.
```

por:

```text
- El asistente de wasichai-agent (AI-05) actúa como el usuario que pregunta, así que se aplican sus permisos y su
  visibilidad de campos, y no tiene herramientas de escritura [WAS-ADR-0014]. Con los puntos de extensión de
  wasichai-agent (UPS-14, resuelta en WAS-ADR-0056) se ofrece a todo rol con CREATE sobre `ai_interaction` en una
  organización activada (`AiAgentAccess`, ASM-130): hoy `SOCIAL_MANAGER`, `COMMUNITY_RELATIONS_OFFICER`,
  `STAKEHOLDER_MANAGER`, `COMMITMENT_MANAGER`, `GRIEVANCE_MANAGER`, `SOCIAL_RISK_MANAGER` y `ANALYST` (§7.8); `READ_ONLY`,
  `AUDITOR`, `PROJECT_ADMIN` y los demás roles sin ese permiso no lo usan. Los resultados de sus herramientas pasan por
  el `Pseudonymizer` con la tabla de equivalencias de la pregunta, el valor de todo campo restringido de §7.9 se
  reemplaza siempre por su marcador tipado (`RestrictedFields`) y la respuesta se restaura sin esos campos; sin tabla, el
  filtro falla cerrado y nada sale hacia el modelo. A diferencia de los agentes propios, AI-05 no exige leer los nombres
  de `person`: su diccionario es el de quien pregunta, y un nombre escrito en un texto libre que no está registrado como
  `person` puede llegar al proveedor (RQ-REP-033). `POST /api/agent/ask` sigue cerrado por HTTP.
```

- [ ] **Paso 4: §14 y §15**

En §14, reemplazar la fila «IA»:

```text
| IA | El modelo falso recibe el texto sin nombres; instrucciones inyectadas en un documento no producen escrituras; la IA nace desactivada; una corrida o una pregunta documental de quien no lee los nombres de `person` responde 403; una sugerencia guarda referencias y marcadores, también en su cita; la API genérica solo devuelve marcadores; la interfaz los muestra resueltos solo a quien lee el campo, y la cita solo a quien puede descargar la evidencia fuente | Unitaria e integración | F7 | RQ-AI-009, RQ-AI-011, RQ-AI-007, RF-DOC-003 |
```

por estas dos filas:

```text
| IA | El modelo falso recibe el texto sin nombres; instrucciones inyectadas en un documento no producen escrituras; la IA nace desactivada, y sin activación o sin clave `runs`, `assistant/questions` y `knowledge/questions` responden `503 AI_UNAVAILABLE`; una corrida o una pregunta documental de quien no lee los nombres de `person` responde 403; una sugerencia guarda referencias y marcadores, también en su cita; la API genérica solo devuelve marcadores; la interfaz los muestra resueltos solo a quien lee el campo, y la cita solo a quien puede descargar la evidencia fuente | Unitaria e integración | F7 | RQ-AI-009, RQ-AI-011, RQ-AI-007, RF-DOC-003 |
| Asistente AI-05 | Con `ScriptedLlmOperations`: el modelo recibe los resultados de las herramientas seudonimizados y con los campos restringidos de §7.9 enmascarados (`RestrictedFields` coincide con §7.9); la respuesta vuelve restaurada, salvo esos campos; sin tabla de equivalencias el filtro falla cerrado y nada sale hacia el modelo; la `ai_interaction` queda con modelo y tokens, también en una corrida truncada. De integración: en una organización sin IA, `assistant/questions` responde `503 AI_UNAVAILABLE`, y a un rol sin CREATE sobre `ai_interaction`, `403`; los dos leen `enabled=false` en `GET /api/agent/status`; `POST /api/agent/ask` por HTTP responde 403 | Unitaria e integración | F7 | RF-AIK-005, RF-AIK-009, RQ-AI-009, RQ-SEC-010, ASM-130 |
```

En §15, reemplazar las filas:

```text
| Proveedor de IA | GAP-015 | Proveedor y residencia de datos | IA desactivada (§11.5) |
| Asistente AI-05 | UPS-14, RF-AIK-005 | Hasta UPS-14, wasichai-agent no seudonimiza: ¿se acepta ofrecer AI-05 solo a roles sin acceso a campos restringidos, o se amplía aceptando que el proveedor reciba esos datos? | Solo `ANALYST` (§11.5) |
```

por:

```text
| Proveedor de IA | GAP-015 | Decidido el 2026-10-12: la API de Anthropic, con seudonimización antes de cada llamada; los datos seudonimizados se procesan fuera del Perú | IA desactivada por defecto y activada por la lista del despliegue (§11.5, ASM-128) |
| Asistente AI-05 | UPS-14, RF-AIK-005 | Decidido el 2026-10-12: UPS-14 está resuelta y AI-05 se ofrece seudonimizado a todo rol con CREATE sobre `ai_interaction` | Los roles con CREATE sobre `ai_interaction` de §7.8, con los campos restringidos enmascarados (§11.5, ASM-130) |
```

En la fila «Brechas upstream» de §15, reemplazar `UPS-12, UPS-14, UPS-23 (UPS-13, resuelta con wasichai-ui#38 en
`@wasichai/gis` 1.0.0)` por `` UPS-12, UPS-23 (UPS-13, resuelta con wasichai-ui#38 en `@wasichai/gis` 1.0.0; UPS-14,
resuelta en wasichai con WAS-ADR-0056 y adoptada en F7) ``.

- [ ] **Paso 5: Verificar y commit**

Repetir la comprobación del Paso 1 (esperado: `ok`), correr `yarn -s prettier --check docs/security.md` y las compuertas
de «Global Constraints» (`test_apply_roles.py` lee la matriz de §7: debe seguir en verde). Después:

```bash
git add docs/security.md
git commit -F - <<'MSG'
docs: seguridad de la IA en F7, AI-05 para todo rol con CREATE

Proveedor decidido, activación por la lista del despliegue, AI-05 seudonimizado con los campos
restringidos enmascarados, sus pruebas en §14 y las decisiones de §15 cerradas.

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01RmiD2JE3gkHdBNUraZkjgM
MSG
```

---

### Tarea 4: Contrato de la API de F7

**Files:**
- Modify: `docs/api.md` (§2 fila «Asistente» ~l. 51; §3.3 ~l. 125-145 y el párrafo de F6 ~l. 248-264; §4.14
  ~l. 1377-1404)

**Interfaces:**
- Consume: ASM-128, ASM-130, ASM-131 y ASM-132 (Tarea 1); la decisión 5.
- Produce: los cuatro códigos con su estado y su campo, que 7.2a, 7.2b y 7.3 usan tal cual.

- [ ] **Paso 1: La comprobación falla**

```bash
cd ~/IdeaProjects/sgspe && python3 - <<'PY'
import re
t = open('docs/api.md', encoding='utf-8').read()
s33 = t.split('### 3.3 Errores', 1)[1].split('\n### 3.4', 1)[0]
s414 = t.split('### 4.14 BC-18', 1)[1].split('\n### 4.15', 1)[0]
bad = []
for old in ('Mientras no exista UPS-14', 'mientras wasichai-agent no tenga sus puntos de extensión', 'hoy, `ANALYST`'):
    if old in t:
        bad.append(f'queda «{old}»')
want = {'AI_UNAVAILABLE': '503', 'AI_INPUT_TOO_LARGE': '422', 'AI_NO_TEXT': '422', 'AI_RUN_TRUNCATED': '502'}
for code, status in want.items():
    if not re.search(rf'^\| `{status}` \|[^\n]*`{code}`', s33, re.M):
        bad.append(f'§3.3: {code} no está en la fila `{status}`')
    if f'`{status}` `{code}`' not in s33:
        bad.append(f'§3.3: falta el párrafo de F7 con `{status}` `{code}`')
    if f'{status} {code}' not in s414:
        bad.append(f'§4.14: falta {status} {code}')
for field in ('(`organization`', '`provider`', '(`target`', '(`useCase`'):
    if field not in s33:
        bad.append(f'§3.3: falta el campo {field}')
if 'AgentHttpGate' not in t or 'ASM-130' not in t:
    bad.append('falta AgentHttpGate o ASM-130')
if '`502`' not in s33.split('|---|---|', 1)[1].split('\n\n', 1)[0]:
    bad.append('§3.3: falta la fila `502` en la tabla de estados')
print('\n'.join(bad) or 'ok')
raise SystemExit(1 if bad else 0)
PY
```

Esperado: salida 1.

- [ ] **Paso 2: §2, el asistente**

En la fila «Asistente» de la tabla de §2, reemplazar:

```text
por HTTP, un filtro de `sgspe.ai` responde `403`, para que toda pregunta pase por la activación por organización y quede registrada, mientras wasichai-agent no tenga sus puntos de extensión (UPS-14, `ai-architecture.md`)
```

por:

```text
por HTTP, `AgentHttpGate` de `sgspe.ai` responde `403` a `POST /api/agent/ask`, para que toda pregunta pase por la activación por organización, la seudonimización y el registro; `GET /api/agent/status` queda abierto y dice `enabled=false` a quien no puede usarlo (`AiAgentAccess`, ASM-130, `ai-architecture.md` §8)
```

- [ ] **Paso 3: §3.3, estados y códigos**

En el párrafo inicial de §3.3, reemplazar `subclases propias de `WasichaiException` para `412`, `422`, `428` y `503`` por
``subclases propias de `WasichaiException` para `412`, `422`, `428`, `502` y `503` ``.

En la tabla de estados:

- en la fila `422`, reemplazar el final `` (`WMS_REQUEST_NOT_ALLOWED`, `WMS_PARAMETER_NOT_ALLOWED`, ASM-121) |`` por
  `` (`WMS_REQUEST_NOT_ALLOWED`, `WMS_PARAMETER_NOT_ALLOWED`, ASM-121); desde F7, una fuente de IA cuyo texto pasa el
  tamaño de `sgspe.ai.limits` (`AI_INPUT_TOO_LARGE`, ASM-131) o que no tiene texto que extraer, como un PDF escaneado
  (`AI_NO_TEXT`, ASM-132) |`` (la fila sigue en una sola línea);
- después de la fila `500`, agregar:

```text
| `502` | Desde F7, una corrida de IA que se queda sin margen de tokens o de acciones y no guarda sugerencias ni candidatos (`AI_RUN_TRUNCATED`, ASM-131) |
```

- en la fila `503`, reemplazar `la IA no está activada para la organización o su proveedor no responde, como el
  asistente de wasichai sin clave` por `` la IA no está activada para la organización o no hay proveedor
  (`AI_UNAVAILABLE`, desde F7, ASM-128), como el asistente de wasichai sin clave ``.

Después del párrafo «Los endpoints de F6 (§4.2 y §4.11) usan la misma forma. …», que termina en «El detalle está en
§4.2.», y antes del párrafo «Los comandos de F3 (§4.5 y §4.9) responden además …», agregar:

```text
Los endpoints de F7 (§4.14) usan la misma forma. `runs`, `assistant/questions` y `knowledge/questions` responden
`503` `AI_UNAVAILABLE` (`organization` si la organización no tiene la IA activada, `provider` si no hay
proveedor; ASM-128). `runs` responde además `422` `AI_INPUT_TOO_LARGE` (`target`: el texto de la fuente pasa el tamaño
de `sgspe.ai.limits`, antes de llamar al modelo; ASM-131), `422` `AI_NO_TEXT` (`target`: un PDF sin texto o un formato
que no es PDF ni DOCX; ASM-132) y `502` `AI_RUN_TRUNCATED` (`useCase`: la corrida se quedó sin margen de tokens o de
acciones y no guardó sugerencias ni candidatos; ASM-131). El detalle está en §4.14.
```

- [ ] **Paso 4: §4.14**

En el párrafo inicial de §4.14, en la línea que empieza con `` `knowledge/questions`) responden ``, reemplazar
`` responden `503` (RQ-AI-009, ASM-015); `` por `` responden `503 AI_UNAVAILABLE` (RQ-AI-009, ASM-015, ASM-128); ``.

En la fila `POST /api/sgspe/ai/assistant/questions`, reemplazar la celda «Propósito»:

```text
AI-05: pregunta al asistente de solo lectura de wasichai-agent, como la persona que pregunta, y registra la interacción. Mientras no exista UPS-14, se ofrece solo a roles sin acceso a campos restringidos (hoy, `ANALYST`), una decisión del checkpoint (`security.md` §15)
```

por:

```text
AI-05: pregunta al asistente de solo lectura de wasichai-agent, como la persona que pregunta, con los resultados de sus herramientas seudonimizados y los campos restringidos enmascarados, y registra la interacción con sus tokens; la respuesta vuelve con los marcadores restaurados, salvo los de los campos restringidos (ASM-130, `ai-architecture.md` §8)
```

y la celda «Permiso o acción declarada» `CREATE sobre `ai_interaction`` de esa misma fila por `` CREATE sobre
`ai_interaction` (sin él, `403`); no exige leer los nombres de `person` ``.

Después del párrafo «Los candidatos a compromiso que produce la IA se aceptan o se marcan como duplicados …» (que
termina en «(RQ-AI-015).»), agregar:

```text
**Errores.** Además de los de §3.3, los tres endpoints que llaman a un modelo responden `503 AI_UNAVAILABLE` sin
activación o sin proveedor, y `runs` responde `422 AI_INPUT_TOO_LARGE`, `422 AI_NO_TEXT` y `502 AI_RUN_TRUNCATED` con los
campos de §3.3 (ASM-128, ASM-131, ASM-132). Ninguno de esos errores guarda una sugerencia o un candidato; la
`ai_interaction` de cada llamada hecha queda registrada.
```

- [ ] **Paso 5: Verificar y commit**

Repetir la comprobación del Paso 1 (esperado: `ok`), correr `yarn -s prettier --check docs/api.md` y las compuertas de
«Global Constraints». Después:

```bash
git add docs/api.md
git commit -F - <<'MSG'
docs: contrato de la IA de F7 con AI-05 abierto y sus errores

El asistente sin la restricción de UPS-14 y con AgentHttpGate; AI_UNAVAILABLE, AI_INPUT_TOO_LARGE,
AI_NO_TEXT y AI_RUN_TRUNCATED con su estado y su campo en §3.3 y §4.14.

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01RmiD2JE3gkHdBNUraZkjgM
MSG
```

---

### Tarea 5: Los ADR

**Files:**
- Modify: `docs/adr/0003-abstraccion-de-ia.md`, `docs/adr/0005-rag-y-pgvector.md`,
  `docs/adr/0017-datos-personales-y-anonimizacion.md` (~l. 73-75, 103-104, 113-114),
  `docs/adr/0026-topologia-puertos-y-observabilidad.md` (~l. 117-118), `docs/adr/README.md` (filas 0003 y 0005)

**Interfaces:**
- Consume: ASM-128 a ASM-132 (Tarea 1); `ai-architecture.md` §8, §11 y §13 (Tarea 2); las decisiones 6, 7 y 9.
- Produce: ADR-0003 aceptado y ADR-0005 aceptado en su etapa 1, que cita el roadmap (Tarea 6).

- [ ] **Paso 1: La comprobación falla**

```bash
cd ~/IdeaProjects/sgspe && python3 - <<'PY'
import glob
docs = {p: open(p, encoding='utf-8').read() for p in glob.glob('docs/adr/*.md')}
a3, a5 = docs['docs/adr/0003-abstraccion-de-ia.md'], docs['docs/adr/0005-rag-y-pgvector.md']
readme = docs['docs/adr/README.md']
bad = []
if not a3.split('## Estado', 1)[1].lstrip().startswith('Aceptado · 2026-10-12'):
    bad.append('ADR-0003 no está aceptado')
if not a5.split('## Estado', 1)[1].lstrip().startswith('Aceptado en parte · 2026-10-12 (F7)'):
    bad.append('ADR-0005 no está aceptado en su etapa 1')
for p, t in docs.items():
    for old in ('1.5.2', 'hasta UPS-14', 'solo `ANALYST`', 'hoy, solo', 'hoy `ANALYST`', 'mientras no exista, AI-05'):
        if old in t:
            bad.append(f'{p}: queda «{old}»')
for old in ('Propuesto (se acepta en F7)',):
    if old in readme:
        bad.append(f'README: queda «{old}»')
for want in ('ASM-128', 'ASM-129', 'ASM-130', 'AiAgentAccess', 'WAS-ADR-0056', '503 AI_UNAVAILABLE', 'Anthropic'):
    if want not in a3:
        bad.append(f'ADR-0003: falta {want}')
for want in ('ASM-132', 'ProjectReadScope', 'websearch_to_tsquery', 'cuando se proponga la etapa 2'):
    if want not in a5:
        bad.append(f'ADR-0005: falta {want}')
if 'al aceptar este ADR en F7' in a5 or 'CON-041 (ver ADR-0006)' in a5:
    bad.append('ADR-0005: quedan la meta de F7 o el filtro de CON-041')
if 'GAP-015 (proveedor y' in docs['docs/adr/0026-topologia-puertos-y-observabilidad.md']:
    bad.append('ADR-0026: GAP-015 sigue abierta')
print('\n'.join(bad) or 'ok')
raise SystemExit(1 if bad else 0)
PY
```

Esperado: salida 1.

- [ ] **Paso 2: ADR-0003**

En `docs/adr/0003-abstraccion-de-ia.md`:

**Estado.** Reemplazar `Propuesto · 2026-10-07 · se acepta al iniciar F7` por:

```text
Aceptado · 2026-10-12 · al iniciar F7, con las decisiones del usuario del 2026-10-12: la API de Anthropic (GAP-015), la
lista del despliegue para activar la IA (ASM-128) y AI-05 seudonimizado con los puntos de extensión de wasichai-agent
(UPS-14, ASM-130)
```

**Contexto.** Reemplazar `el asistente de wasichai-agent corre sobre Embabel 1.5.2 (WAS-ADR-0015), llega a` por `el
asistente de wasichai-agent corre sobre Embabel 1.5.3, la del BOM de wasichai 1.2.0 (WAS-ADR-0015), llega a`. Reemplazar:

```text
- SGSPE necesita además lo que wasichai-agent no ofrece: activación por organización, seudonimización antes del LLM y
  registro con tokens (RQ-AI-009, RF-AIK-009). Esa brecha es UPS-14.
- No hay proveedor elegido ni política de residencia de datos (GAP-015), y la IA llega en F7 [USR-05].
```

por:

```text
- SGSPE necesita además activación por organización, seudonimización antes del LLM y registro con tokens (RQ-AI-009,
  RF-AIK-009). Esa brecha era UPS-14, resuelta en wasichai (WAS-ADR-0056): `AgentAccessPolicy`, `AgentResultFilter`,
  `AgentAnswerFilter` y `AgentRunListener`.
- El usuario eligió el 2026-10-12 la API de Anthropic (GAP-015), y la IA llega en F7 [USR-05].
```

**Opciones consideradas.** En «Embabel a través de wasichai-agent…», reemplazar `- Desventajas: hereda las limitaciones de
Embabel 1.5.2; AI-05 queda restringido hasta UPS-14.` por `- Desventajas: hereda las limitaciones de Embabel 1.5.3
(catálogo de modelos y tope de vueltas de herramientas).` La opción «Portal contra `POST /api/agent/ask`…» no cambia: es
la opción tal como se evaluó.

**Decisión.** Reemplazar la viñeta «Proveedor.»:

```text
- **Proveedor.** Es configuración de Embabel; un proveedor por despliegue, compartido por wasichai-agent y los agentes de
  `sgspe.ai`; el modelo de cada caso se configura con `sgspe.ai.models.<caso>`. La versión de Embabel es la del BOM de
  wasichai; el starter `wasichai-spring-boot-starter-agent` se agrega en F7 ([ai-architecture.md §11](../ai-architecture.md)).
```

por:

```text
- **Proveedor.** La API de Anthropic, el proveedor por defecto del starter (GAP-015); cambiarlo es configuración de
  Embabel. Un proveedor por despliegue, compartido por wasichai-agent y los agentes de `sgspe.ai`; el modelo de cada caso
  se configura con `sgspe.ai.models.<caso>` (ASM-129). La versión de Embabel es la del BOM de wasichai; el starter
  `wasichai-spring-boot-starter-agent` se agrega en F7 ([ai-architecture.md §11](../ai-architecture.md)).
```

En la viñeta «Activación por organización.», reemplazar `Propuesta de mecanismo: la lista` por `El mecanismo es la
lista`, `con el proveedor\n  ([ai-architecture.md §12.1](../ai-architecture.md)).` por `con el proveedor (ASM-128,\n
[ai-architecture.md §12.1](../ai-architecture.md)).` y `responden 503 sin activación o sin proveedor.` por `` responden
`503 AI_UNAVAILABLE` sin activación o sin proveedor. ``

En la tabla del registro, reemplazar la fila `| tokens | `input_tokens`, `output_tokens`; vacíos en AI-05 hasta UPS-14 |`
por `` | tokens | `input_tokens`, `output_tokens`; en AI-05, los de `AgentUsage` (`AgentRunListener`) | ``.

Reemplazar la viñeta «Consecuencia de UPS-14.»:

```text
- **Consecuencia de UPS-14.** Mientras wasichai-agent no pueda seudonimizar los resultados de sus herramientas, AI-05 se
  ofrece solo en organizaciones con la IA activada, con el proveedor decidido (GAP-015) y a roles sin lectura de ningún
  campo restringido que puedan registrar la `ai_interaction`: hoy, solo `ANALYST` ([security.md §7.8 y
  §11.5](../security.md)). Ampliarlo es una decisión del checkpoint ([security.md §15](../security.md)).
```

por:

```text
- **AI-05 con los puntos de extensión (ASM-130).** `AiAgentAccess` (`AgentAccessPolicy`) lo permite a quien tenga CREATE
  sobre `ai_interaction` en una organización activada, sin lista de roles; `PseudonymizingResultFilter`
  (`AgentResultFilter`) seudonimiza los resultados de las herramientas y enmascara siempre los campos restringidos;
  `RestoringAnswerFilter` (`AgentAnswerFilter`) restaura la respuesta, salvo esos campos; `InteractionRunListener`
  (`AgentRunListener`) registra la `ai_interaction` con tokens ([ai-architecture.md §8](../ai-architecture.md),
  [security.md §11.5](../security.md)).
```

**Consecuencias.** En «Negativas y riesgos», reemplazar:

```text
- El catálogo de Embabel 1.5.2 no conoce los modelos más nuevos: el modelo por defecto de wasichai es
  `claude-haiku-4-5` ([architecture.md §14](../architecture.md), riesgo 15); además, tope de 20 vueltas de herramientas
  y Jackson 2 en el classpath ([ai-architecture.md §16](../ai-architecture.md), WAS-ADR-0015).
- AI-05 solo para `ANALYST` y sin tokens hasta UPS-14; el riesgo residual son nombres escritos en textos libres.
```

por:

```text
- El catálogo de Embabel 1.5.3 no conoce los modelos Claude 5: cada caso usa el más capaz que reconoce, o
  `claude-haiku-4-5` en AI-07 y AI-08 (ASM-129; [architecture.md §14](../architecture.md), riesgo 15); además, tope de
  20 vueltas de herramientas y Jackson 2 en el classpath ([ai-architecture.md §16](../ai-architecture.md), WAS-ADR-0015).
- Los datos seudonimizados se procesan fuera del Perú (GAP-015); en AI-05 y en los agentes propios, el riesgo residual
  son los nombres escritos en textos libres que no están registrados como `person` (RQ-REP-033).
```

En «Seguimiento», reemplazar `- Antes de F7: GAP-015 (proveedor y residencia) y la decisión del checkpoint sobre AI-05;
UPS-14 upstream (GAP-028).` por:

```text
- Plan 7.2a: anotar el modelo elegido para cada caso (ASM-129) y adoptar los cuatro puntos de extensión de
  wasichai-agent (ASM-130).
```

y en la viñeta de pruebas, `modelo responden 503 y` por `` modelo responden `503 AI_UNAVAILABLE` y ``, y `ningún perfil
se llama `test`.` por `` AI-05 con `ScriptedLlmOperations` ([security.md §14](../security.md), «Asistente AI-05»);
ningún perfil se llama `test`. ``

**Referencias.** Reemplazar `` ASM-015, UPS-14.`` (final de la viñeta «Requisitos») por `` ASM-015, ASM-128, ASM-129,
ASM-130, UPS-14.``, y en la viñeta «wasichai», después de `WAS-ADR-0031 (*Where wasichai deliberately behaves
differently from the original*, D7)`, agregar `, WAS-ADR-0056 (los puntos de extensión de wasichai-agent: acceso, filtro
de resultados, filtro de la respuesta y escucha de la corrida)`.

- [ ] **Paso 3: ADR-0005**

En `docs/adr/0005-rag-y-pgvector.md`:

**Estado.** Reemplazar `Propuesto · 2026-10-07 · se acepta al iniciar F7` por:

```text
Aceptado en parte · 2026-10-12 (F7): la etapa 1 (`knowledge_chunk` con búsqueda de texto completo, JOB-07 y
recuperación con permisos); la etapa 2 (pgvector) sigue sujeta a los criterios de la Decisión
```

**Contexto.** Reemplazar `(RQ-AI-008). wasichai no tiene tipo de campo vector ni JSON, y el alcance de lectura por
proyecto depende de UPS-01.` por `` (RQ-AI-008). wasichai no tiene tipo de campo vector ni JSON, y el alcance de lectura
por proyecto lo da `ProjectReadScope` (ADR-0029). ``, y `- No hay corpus real, ni volúmenes (GAP-027), ni proveedor de
embeddings con política de residencia (GAP-015).` por:

```text
- No hay corpus real ni volúmenes (GAP-027), ni proveedor de embeddings: GAP-015 eligió la API de Anthropic para los
  modelos de lenguaje, y la etapa 2 elegirá el de embeddings con el criterio 4.
```

**Decisión, etapa 1.** En «Indexación (JOB-07).», reemplazar `Lee las `evidence` con texto creadas o cambiadas desde la
vuelta anterior, las segmenta y escribe los fragmentos con SQL propio;` por `` Lee las `evidence` con texto creadas o
cambiadas desde la vuelta anterior, extrae el texto con `TextExtraction` (PDFBox y POI, sin OCR, ASM-132), lo segmenta en
fragmentos de unos 1 500 caracteres con solape y escribe los fragmentos con SQL propio; ``, y `deja una `ai_interaction`
por documento cuando\n  llama a un modelo.` por `` en la etapa 1 no llama a un modelo, así que no deja
`ai_interaction`. `` En «Recuperación con permisos.», reemplazar `de proyecto, con el alcance que decida CON-041 (ver
ADR-0006);` por `` de proyecto, con `ProjectReadScope` (ADR-0029); ``, y después de `descartando todo fragmento cuya
evidencia no vuelve;` agregar `` búsqueda con `websearch_to_tsquery('spanish', …)` ordenada por `ts_rank_cd`, y los cinco
mejores fragmentos que pasan los filtros llegan al modelo; ``. En «Endpoint.», reemplazar `(ver ADR-0003 para la
activación y el 503)` por `` (ver ADR-0003 para la activación y el `503 AI_UNAVAILABLE`) ``.

**Decisión, etapa 2.** Reemplazar:

```text
- La Fase 0 no fija la meta numérica de cobertura y precisión, ni la dimensión ni el índice vectorial: la meta se fija al
  aceptar este ADR en F7, con el juego de evaluación; si la etapa 2 se adopta, un ADR nuevo registra la dimensión, el
  índice y el modelo de embeddings.
```

por:

```text
- Ni la Fase 0 ni F7 fijan la meta numérica de cobertura y precisión, la dimensión ni el índice vectorial: la meta se
  fija con el juego de evaluación cuando se proponga la etapa 2, que F7 no construye; si la etapa 2 se adopta, un ADR
  nuevo registra la dimensión, el índice y el modelo de embeddings.
```

**Consecuencias.** En «Negativas y riesgos», reemplazar:

```text
- Hasta que se resuelva UPS-01, el filtro de proyecto es el que decida CON-041; con la opción A, la lectura es de todo
  el tenant, acotada por objeto, campo y clasificación.
```

por:

```text
- El filtro de proyecto es `ProjectReadScope` (ADR-0029); territorio y región siguen sin filtro propio (UPS-01).
```

En «Seguimiento», reemplazar `- Al iniciar F7: fijar la meta del criterio 2; GAP-015, GAP-027, CON-041 y UPS-01.` por
`- Etapa 2: fijar la meta del criterio 2 con el juego de evaluación y elegir el proveedor de embeddings (criterio 4);
GAP-027.`, y `de `person` recibe 403 en `knowledge/questions`; sin IA activada, 503 (RQ-AI-007).` por `` de `person`
recibe 403 en `knowledge/questions`; sin IA activada, `503 AI_UNAVAILABLE` (RQ-AI-007). ``

**Referencias.** Reemplazar `GAP-015, GAP-026, GAP-027, ASM-006, UPS-01.` por `GAP-015, GAP-026, GAP-027, ASM-006,
ASM-132, UPS-01; ADR-0029.`

- [ ] **Paso 4: ADR-0017, ADR-0026 y el índice**

En `docs/adr/0017-datos-personales-y-anonimizacion.md`, reemplazar:

```text
   (ADR-0015). La IA se activa por organización. Los agentes propios de `sgspe.ai` seudonimizan antes de llamar al
   modelo (RQ-AI-009, ADR-0004); el asistente AI-05 de wasichai-agent no lo hace hasta UPS-14, y por eso solo se ofrece a
   roles sin acceso a campos restringidos, hoy `ANALYST` ([security.md §11.5 y §15](../security.md), ADR-0003).
```

por:

```text
   (ADR-0015). La IA se activa por organización. Los agentes propios de `sgspe.ai` y el asistente AI-05 seudonimizan
   antes de llamar al modelo (RQ-AI-009, ADR-0004); en AI-05, los resultados de las herramientas pasan por el
   `Pseudonymizer` y los campos restringidos se enmascaran siempre (UPS-14 resuelta, ASM-130,
   [security.md §11.5](../security.md), ADR-0003).
```

reemplazar `UPS-14: mientras no exista, AI-05 no seudonimiza y por eso\n  solo se ofrece a roles sin acceso a campos
restringidos (`security.md` §15).` por `UPS-14 quedó resuelta (WAS-ADR-0056): desde F7, AI-05\n  seudonimiza los
resultados de sus herramientas (ASM-130).`, y en «Referencias», `` GAP-031,\n  UPS-14.`` por `` GAP-031,\n  UPS-14,
ASM-130.``

En `docs/adr/0026-topologia-puertos-y-observabilidad.md`, reemplazar:

```text
- Abiertos: GAP-022 (destino, almacenamiento y respaldos), GAP-027, GAP-011 (GeoServer en producción), GAP-015 (proveedor y
  residencia de datos), UPS-03, ASM-059.
```

por:

```text
- Abiertos: GAP-022 (destino, almacenamiento y respaldos), GAP-027, GAP-011 (GeoServer en producción), UPS-03, ASM-059.
  GAP-015 quedó resuelta el 2026-10-12 (la API de Anthropic, `ai-architecture.md` §11).
```

En `docs/adr/README.md`, reemplazar las filas:

```text
| [0003](0003-abstraccion-de-ia.md) | Abstracción de IA sobre wasichai-agent | Propuesto (se acepta en F7) | 2026-10-07 | 0014, 0015, 0031 |
| [0005](0005-rag-y-pgvector.md) | RAG en F7 y pgvector solo con justificación medida | Propuesto (se acepta en F7) | 2026-10-07 | 0002, 0026, 0039 |
```

(no son contiguas: la fila 0004 va entre ellas y no cambia) por:

```text
| [0003](0003-abstraccion-de-ia.md) | Abstracción de IA sobre wasichai-agent | Aceptado | 2026-10-12 | 0014, 0015, 0031, 0056 |
| [0005](0005-rag-y-pgvector.md) | RAG en F7 y pgvector solo con justificación medida | Aceptado en parte (F7: la etapa 1, con `knowledge_chunk` y búsqueda de texto completo; pgvector, la etapa 2, sigue sujeto a sus criterios) | 2026-10-12 | 0002, 0026, 0039 |
```

- [ ] **Paso 5: Verificar y commit**

Repetir la comprobación del Paso 1 (esperado: `ok`), correr `yarn -s prettier --check docs/adr/` y las compuertas de
«Global Constraints». Después:

```bash
git add docs/adr/0003-abstraccion-de-ia.md docs/adr/0005-rag-y-pgvector.md \
  docs/adr/0017-datos-personales-y-anonimizacion.md docs/adr/0026-topologia-puertos-y-observabilidad.md \
  docs/adr/README.md
git commit -F - <<'MSG'
docs: ADR-0003 aceptado y ADR-0005 aceptado en su etapa 1

La API de Anthropic, la activación por lista y AI-05 con los puntos de extensión de
wasichai-agent; la etapa 1 de AI-06 sin ai_interaction en JOB-07; ADR-0017 y ADR-0026 sin UPS-14
ni GAP-015 abiertas.

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01RmiD2JE3gkHdBNUraZkjgM
MSG
```

---

### Tarea 6: Roadmap, trazabilidad, encaje, arquitectura y modelo de dominio

**Files:**
- Modify: `docs/roadmap.md` (F7 ~l. 373-388, F8 ~l. 396, brechas ~l. 471 y 495, decisiones ~l. 511, 515 y 530, riesgos
  ~l. 579-580, respuestas del checkpoint ~l. 645-647), `docs/requirements-traceability.md` (~l. 57-63, 266, 270, 451),
  `docs/platform-fit.md` (~l. 91, 180, 193, 418, 466, 555, 568), `docs/architecture.md` (~l. 118, 643-644, 745),
  `docs/domain-model.md` (~l. 309, 1074)

**Interfaces:**
- Consume: ASM-128 a ASM-132 (Tarea 1); los nombres de `ai-architecture.md` (Tarea 2); ADR-0003 y ADR-0005 (Tarea 5);
  las decisiones 2, 3, 9 y 10.
- Produce: la fila «Estado» de F7, a la que 7.2a suma el modelo elegido y el coordinador el humo con la clave real.

- [ ] **Paso 1: La comprobación falla**

Guardar antes los estados de la trazabilidad, para comparar después:

```bash
cd ~/IdeaProjects/sgspe && python3 - > "$TMPDIR/estados-antes-7-0.txt" <<'PY'
import re, collections
t = open('docs/requirements-traceability.md', encoding='utf-8').read()
main = re.search(r'^## 2\..*?(?=^## 6\.)', t, re.M | re.S).group(0)
c = collections.Counter()
for l in main.splitlines():
    if re.match(r'^\| (RF|RNF|RN|RQ)-', l):
        st = l.rstrip().rstrip('|').split('|')[-1]
        for s in ('Diseñado', 'Implementado', 'Decisión pendiente', 'Upstream', 'Hueco', 'Fuera del MVP'):
            c[s] += s in st
print(dict(sorted(c.items())))
PY
cat "$TMPDIR/estados-antes-7-0.txt"
python3 - <<'PY'
road = open('docs/roadmap.md', encoding='utf-8').read()
f7 = road.split('## F7 · IA', 1)[1].split('\n## ', 1)[0]
f8 = road.split('## F8 ·', 1)[1].split('\n## ', 1)[0]
trace = open('docs/requirements-traceability.md', encoding='utf-8').read()
fit = open('docs/platform-fit.md', encoding='utf-8').read()
arch = open('docs/architecture.md', encoding='utf-8').read()
dom = open('docs/domain-model.md', encoding='utf-8').read()
bad = []
for want in ('| Estado |', '2026-10-12-fase-7-design.md', '7.2a', '7.2b', '7.2c', '7.3', 'Embabel 1.5.3', 'ASM-128',
             'ASM-132', 'AiAgentAccess', 'ai-live', 'PDFBox'):
    if want not in f7:
        bad.append(f'roadmap F7: falta {want}')
for old in ('Embabel 1.5.2', 'Bloquean: GAP-015', 'sin seudonimizar, lo que la persona puede leer (UPS-14)'):
    if old in f7:
        bad.append(f'roadmap F7: queda «{old}»')
if 'PDFBox en pruebas' in f8:
    bad.append('roadmap F8: PDFBox sigue solo en pruebas')
for old in ('| F7 | UPS-14 |', 'AI-05 no seudonimiza mientras falte UPS-14', 'Decidir antes de F7; IA desactivada'):
    if old in road:
        bad.append(f'roadmap: queda «{old}»')
for rid in ('RF-AIK-005', 'RF-AIK-009', 'RQ-AI-009'):
    line = next(l for l in trace.splitlines() if l.startswith(f'| {rid} |'))
    if 'UPS-14' in line or not line.rstrip().endswith('| Diseñado; Fuera del MVP |'):
        bad.append(f'trazabilidad: {rid} no pasó a «Diseñado; Fuera del MVP»')
if 'mientras falte UPS-14' in trace:
    bad.append('trazabilidad §1.2: queda «mientras falte UPS-14»')
for old in ('1.5.2', '(hoy, `ANALYST`)', '| 3, solo en pruebas |', 'Disponible, sin uso | Diseño de AI-05 en F7'):
    if old in fit:
        bad.append(f'platform-fit.md: queda «{old}»')
for old in ('Embabel 1.5.2', 'La residencia de datos queda en GAP-015'):
    if old in arch:
        bad.append(f'architecture.md: queda «{old}»')
for old in ('hasta UPS-14', 'que crea registros de `ai_interaction` y escribe'):
    if old in dom:
        bad.append(f'domain-model.md: queda «{old}»')
print('\n'.join(bad) or 'ok')
raise SystemExit(1 if bad else 0)
PY
```

Esperado: el conteo de antes (`{'Decisión pendiente': 8, 'Diseñado': 239, 'Fuera del MVP': 82, 'Hueco': 0,
'Implementado': 46, 'Upstream': 20}`) y salida 1.

- [ ] **Paso 2: `roadmap.md`, F7 y F8**

En la tabla de F7, reemplazar las filas «Alcance», «Starters y capacidades», «Código SGSPE», «Definición de terminado»,
«Riesgos» e «IDs abiertos» (las demás no cambian) por estas, y agregar al final la fila «Estado»:

```text
| Alcance | Casos AI-01, AI-02, AI-03, AI-04 (solo el resumen ejecutivo), AI-05, AI-06, AI-07 y AI-08; candidatos a compromiso propuestos por la IA; sugerencias con cita y confianza; registro de cada interacción con tokens; seudonimización con marcadores, también en los resultados de las herramientas de AI-05, que se ofrece a todo rol con CREATE sobre `ai_interaction` (ASM-130); activación por la lista del despliegue (ASM-128); modelo por caso (ASM-129); corridas síncronas con límites (ASM-131); extracción de texto sin OCR (ASM-132); bandeja de revisión; prompts versionados; RAG en etapa 1 con búsqueda de texto completo; pgvector solo con justificación medida [PRM-01 §9, §10] (CON-035, CON-044) |
| Starters y capacidades | + `-agent` (Embabel 1.5.3, por el BOM de wasichai 1.2.0): asistente de solo lectura que actúa como la persona, con sus cuatro puntos de extensión; el proveedor es la API de Anthropic (GAP-015); Apache PDFBox pasa a `main` y Apache POI (ya en `main`) extrae el texto de los DOCX (ASM-132); sin paquete nuevo de frontend y sin `agentModule()` en `/admin` |
| Código SGSPE | `sgspe.ai`, paquete de salida: `AiActivation` y `AiStatus`; `Pseudonymizer` y `PseudonymTable`; `Interactions`; `Prompts`; el puerto `StructuredModel` con `EmbabelStructuredModel`; AI-05 con `AssistantQuestions`, `AiAgentAccess`, `PseudonymizingResultFilter`, `RestoringAnswerFilter`, `InteractionRunListener` y `AgentHttpGate`; `AiRuns`, un agente por caso, `TextExtraction`, `CitationCheck` y `SuggestionWriter`; `SuggestionAcceptance`; `KnowledgeIndexJob` (JOB-07), `KnowledgeSearch` y `KnowledgeQuestions`; `DuplicateCandidates`. `sgspe.commitment`: aceptación de los candidatos de IA |
| Definición de terminado | La general, más: AI-05 sin restricción de roles y con tokens; `AiSuggestionWorkflowApiTest`; las suites de [IA §15](ai-architecture.md#15-pruebas) con FakeModel, StubModel y MockModel, sin llamadas reales al LLM (RQ-AI-007), y la suite `ai-live` contra el proveedor, a pedido y fuera de la CI; las pruebas «IA» y «Asistente AI-05» de [Seguridad §14](security.md#14-pruebas-de-seguridad); `sgspe.ai` como paquete de salida y los objetos nuevos clasificados en `ProjectScope` (`ArchitectureTest`); el tenant `demo` en F7 con la IA activada; el humo con la clave real (AI-03 sobre un acta ficticia, AI-05 y AI-06 con una pregunta) y el del portal anotados en «Estado» |
| Riesgos | Catálogo de Embabel 1.5.3 sin los modelos Claude 5 (ASM-129); inyección de instrucciones en actas y documentos, acotada por herramientas de solo lectura, validación determinista y decisión humana; un fallo del filtro de AI-05 cierra la corrida sin enviar nada; costos sin presupuesto por organización, con los tokens registrados; tiempos de las corridas síncronas con documentos largos (ASM-131). Consecuencias aceptadas: los datos seudonimizados se procesan fuera del Perú (GAP-015); un nombre no registrado como `person` en un texto libre puede llegar al proveedor (RQ-REP-033); activar la IA pide redesplegar (ASM-128); sin OCR (ASM-132) |
| IDs abiertos | Condicionan: GAP-013; ASM-015, ASM-052, ASM-053, ASM-128, ASM-129, ASM-130, ASM-131, ASM-132. GAP-015 está resuelta (la API de Anthropic) y UPS-14 también (WAS-ADR-0056, ASM-130) |
| Estado | En curso. Diseñada: especificación `docs/superpowers/specs/2026-10-12-fase-7-design.md`, con las decisiones del usuario del 2026-10-12, y documentos al día (plan 7.0). Reparto: 7.0 (documentos), 7.1 (modelo), 7.2a (base de IA y asistente), 7.2b (casos con sugerencias), 7.2c (conocimiento y duplicados) y 7.3 (portal, en `sgspe-ui`); con 7.0 en `main`, 7.1 y 7.3 van en paralelo, y con 7.2a en `main`, 7.2b y 7.2c. La release 3.0.0 se corta al cerrar F8 (`Release-As: 3.0.0`, wasichai/sgspe#54 y wasichai/sgspe-ui#27) |
```

En la tabla de F8, reemplazar `Apache POI y commonmark-java; PDFBox en pruebas;` por `Apache POI (ya en `main`) y
commonmark-java; PDFBox, en `main` desde F7 (ASM-132) y también en las pruebas;`.

- [ ] **Paso 3: `roadmap.md`, brechas, decisiones, riesgos y respuestas del checkpoint**

- «Brechas upstream por fase»: `| F7 | UPS-14 |` pasa a `| F7 | Ninguna (UPS-14, resuelta en wasichai con WAS-ADR-0056 y
  adoptada en F7, ASM-130) |`.
- Tabla de issues: `| UPS-14 | [wasichai#59](https://github.com/wasichai/wasichai/issues/59) |` pasa a
  `| UPS-14 | [wasichai#59](https://github.com/wasichai/wasichai/issues/59) (resuelta; adoptada en F7 sobre wasichai 1.2.0) |`.
- «Decisiones pendientes para el checkpoint», fila «Abrir los issues upstream»: `UPS-13 (resuelta en F6 con
  wasichai-ui#38), UPS-14,` pasa a `UPS-13 (resuelta en F6 con wasichai-ui#38), UPS-14 (resuelta, adoptada en F7),`.
- Misma tabla, reemplazar la fila:

```text
| Asistente AI-05 hasta que exista UPS-14 | Solo roles sin acceso a campos restringidos; ampliarlo aceptando que el proveedor reciba esos datos | Solo `ANALYST` | UPS-14, RF-AIK-005, RQ-AI-009, GAP-015 (decisión registrada para el checkpoint, sin GAP ni CON) | F7 |
```

por:

```text
| Asistente AI-05 hasta que exista UPS-14 | Solo roles sin acceso a campos restringidos; ampliarlo aceptando que el proveedor reciba esos datos | Decidido el 2026-10-12: UPS-14 está resuelta y AI-05 se ofrece seudonimizado a todo rol con CREATE sobre `ai_interaction` (ASM-130) | UPS-14, RF-AIK-005, RQ-AI-009, GAP-015, ASM-130 | F7 |
```

- Misma tabla, en la fila «Proveedor de IA y residencia de datos», `| Decidir antes de F7; IA desactivada por defecto |`
  pasa a `| Decidido el 2026-10-12: la API de Anthropic, con seudonimización e IA desactivada por defecto (ASM-128) |`.
- «Registro de riesgos», reemplazar las filas 13 y 14:

```text
| 13 | La seudonimización no detecta nombres no registrados en textos libres | Datos personales hacia el proveedor de LLM | IA desactivada por defecto, proveedor decidido antes, solo fragmentos necesarios | RQ-AI-009, GAP-015 |
| 14 | AI-05 no seudonimiza mientras falte UPS-14 | El proveedor recibe lo que la persona puede leer | AI-05 solo para roles sin campos restringidos; `POST /api/agent/ask` cerrado por HTTP | UPS-14, RF-AIK-005 |
```

por:

```text
| 13 | La seudonimización no detecta nombres no registrados en textos libres | Datos personales hacia el proveedor de LLM | IA desactivada por defecto y activada por organización (ASM-128), proveedor decidido (GAP-015, la API de Anthropic) y solo los fragmentos necesarios | RQ-AI-009, GAP-015, RQ-REP-033 |
| 14 | Falla el filtro de resultados de AI-05 | Datos sin seudonimizar hacia el proveedor | El filtro falla cerrado: sin tabla de equivalencias lanza y nada sale hacia el modelo; los campos restringidos se enmascaran siempre; `POST /api/agent/ask` cerrado por HTTP (ASM-130) | RF-AIK-005, ASM-130 |
```

- «Respuestas del checkpoint»: después de la fila «Decisiones de F4 (2026-10-09)», agregar:

```text
| Proveedor de IA y asistente AI-05 (2026-10-12, antes de F7) | La API de Anthropic (GAP-015), con la IA desactivada por defecto y activada por la lista del despliegue (ASM-128) y el modelo por caso (ASM-129); AI-05 ya no «solo para `ANALYST`»: con UPS-14 resuelta, se ofrece seudonimizado a todo rol con CREATE sobre `ai_interaction` (ASM-130) |
```

  y en la fila «Valores por defecto», `<li>AI-05 solo para `ANALYST`;</li>` pasa a `<li>AI-05 solo para `ANALYST`
  (reemplazado el 2026-10-12, ver la fila del proveedor de IA);</li>`.

- [ ] **Paso 4: `requirements-traceability.md`**

En §1.2, reemplazar:

```text
Una fila puede llevar varios estados, separados por punto y coma. Cuatro decisiones del checkpoint no tienen GAP ni CON
propio y por eso no aparecen como Decisión pendiente: ofrecer AI-05 solo a roles sin acceso a campos restringidos
mientras falte UPS-14 (`security.md` §15), que RF-AIK-005 muestra como Upstream (UPS-14); contar los plazos desde la
primera recepción al reclasificar una QCS (ASM-072), que RF-GRV-007 muestra como Diseñado; reutilizar `approve_senior`
de WF-D en el nivel 2 de escalamiento (ASM-030), que RF-WFL-004 muestra como Upstream (UPS-17); y el reparto en releases
(ASM-044), que fija el estado «Fuera del MVP». El checkpoint aceptó los valores por defecto de GAP-004 y GAP-005, y F4
```

por:

```text
Una fila puede llevar varios estados, separados por punto y coma. Tres decisiones del checkpoint no tienen GAP ni CON
propio y por eso no aparecen como Decisión pendiente: contar los plazos desde la primera recepción al reclasificar una
QCS (ASM-072), que RF-GRV-007 muestra como Diseñado; reutilizar `approve_senior` de WF-D en el nivel 2 de escalamiento
(ASM-030), que RF-WFL-004 muestra como Upstream (UPS-17); y el reparto en releases (ASM-044), que fija el estado «Fuera
del MVP». La cuarta, ofrecer AI-05 solo a roles sin acceso a campos restringidos mientras faltara UPS-14 (ya resuelta),
la reemplazó la decisión del usuario del 2026-10-12 (ASM-130): RF-AIK-005, RF-AIK-009 y RQ-AI-009 pasan a Diseñado. El
checkpoint aceptó los valores por defecto de GAP-004 y GAP-005, y F4
```

(la línea siguiente, «se diseñó con ellos: …», no cambia). Reemplazar la fila RF-AIK-005 entera por:

```text
| RF-AIK-005 | [PRM-01 §9] [WAS-ADR-0014] | BC-18 · `sgspe.ai` (llamada en proceso al asistente de wasichai-agent con `AiAgentAccess`, `PseudonymizingResultFilter`, `RestoringAnswerFilter`, `InteractionRunListener` y `AgentHttpGate`; ASM-130) | `ai_interaction` | `POST /api/sgspe/ai/assistant/questions`; `GET /api/sgspe/ai/status` | Portal › pantalla del asistente (`architecture.md` §9.3) | `ai-architecture.md` §15 «Del agente» y «De integración»; `security.md` §14 «IA» y «Asistente AI-05» | F7 | Diseñado; Fuera del MVP |
```

En la fila RF-AIK-009, `| F7 | Upstream (UPS-14); Fuera del MVP |` pasa a `| F7 | Diseñado; Fuera del MVP |`. Reemplazar
la fila RQ-AI-009 entera por:

```text
| RQ-AI-009 | [PRM-01 §10] [USR-07] | BC-18 · `sgspe.ai` (seudonimización, también de los resultados de las herramientas de AI-05; activación por la lista del despliegue, ASM-128) | `ai_interaction`, `person` | `GET /api/sgspe/ai/status`; `POST /api/sgspe/ai/runs`; `POST /api/sgspe/ai/assistant/questions` | — | `security.md` §14 «IA» y «Asistente AI-05»; `test_apply_roles.py` | F7 | Diseñado; Fuera del MVP |
```

§8 no se toca (decisión 10).

- [ ] **Paso 5: `platform-fit.md`, `architecture.md` y `domain-model.md`**

`docs/platform-fit.md`:

- §2.7, fila «Abstracción de LLM» (~l. 91): `El proveedor es configuración de Embabel; Anthropic por defecto` pasa a `El
  proveedor es configuración de Embabel; Anthropic por defecto y elegido para SGSPE (GAP-015, resuelta el 2026-10-12)`,
  y la celda «Brecha o CON» `GAP-015` pasa a `—`.
- §2.7, fila «AI-05 preguntas sobre los datos del sistema» (~l. 180), reemplazar la fila entera por:

```text
| AI-05 preguntas sobre los datos del sistema | Plataforma + SGSPE | Asistente de solo lectura de wasichai-agent, como el usuario que pregunta, llamado en proceso por `sgspe.ai`, que verifica la activación por organización y registra la interacción con tokens (F7). Con los puntos de extensión de wasichai-agent, los resultados de las herramientas se seudonimizan, los campos restringidos se enmascaran y la respuesta se restaura; se ofrece a todo rol con CREATE sobre `ai_interaction` (ASM-130) | WAS-ADR-0014; WAS-ADR-0056 | ADR-0003 | — (UPS-14, resuelta) | RF-AIK-005, RQ-AI-009 |
```

- §2.8, fila «Cambiar entre Anthropic, OpenAI u otros…» (~l. 193): `Anthropic viene por defecto y se puede excluir` pasa
  a `Anthropic viene por defecto, es el elegido para SGSPE (GAP-015) y se puede excluir`, y la celda `GAP-015` pasa a `—`.
- §4.1, reemplazar la fila UPS-14 (~l. 418) por:

```text
| UPS-14 Puntos de extensión de wasichai-agent: seudonimizar los resultados de las herramientas, activarse por organización e informar los tokens | F7 | Resuelta en wasichai (WAS-ADR-0056, wasichai/wasichai#59) y adoptada en F7 (ASM-130): `AiAgentAccess`, `PseudonymizingResultFilter`, `RestoringAnswerFilter` e `InteractionRunListener` implementan los cuatro puntos de extensión; AI-05 para todo rol con CREATE sobre `ai_interaction` | `sgspe.ai` | Nada: `AgentHttpGate` sigue cerrando `POST /api/agent/ask` por HTTP para que toda pregunta pase por `sgspe.ai` |
```

- §4.3, fila UPS-14 (~l. 466): `| Disponible, sin uso | Diseño de AI-05 en F7 |` pasa a `| Resuelta y adoptada en F7
  (ASM-130) con los cuatro puntos de extensión de wasichai 1.2.0 | Nada |`.
- §6: `| Embabel | 1.5.2, a través de wasichai-agent | F7 | WAS-ADR-0015 |` pasa a `| Embabel | 1.5.3, a través de
  wasichai-agent (BOM de wasichai 1.2.0) | F7 | WAS-ADR-0015 |`, y `| PDFBox | 3, solo en pruebas | F8 | RQ-REP-017 |`
  pasa a `| PDFBox | 3, en `main` desde F7 (extracción de texto, ASM-132) y en las pruebas de F8 | F7 | RQ-REP-017,
  ASM-132 |`.

`docs/architecture.md`:

- ~l. 118: `| Proveedor de LLM | IA de F7, intercambiable y desactivada por defecto | RQ-AI-005, RQ-AI-009, GAP-015 |` pasa
  a `| Proveedor de LLM | IA de F7: la API de Anthropic (GAP-015), intercambiable por configuración de Embabel y
  desactivada por defecto | RQ-AI-005, RQ-AI-009, GAP-015, ASM-128 |`.
- ~l. 643-644: `(RQ-AI-009). La residencia de datos queda en GAP-015.` pasa a `` (RQ-AI-009). El proveedor es la API de
  Anthropic (GAP-015): los datos seudonimizados se procesan fuera del Perú, una consecuencia aceptada
  (`ai-architecture.md` §11). ``
- Riesgo 15 (~l. 745), reemplazar la fila por:

```text
| 15 | El catálogo de modelos de Embabel 1.5.3 no conoce los modelos Claude 5 | Calidad menor en la extracción | Modelo configurable por caso; se cambia por configuración cuando el catálogo los sume | [WAS-ADR-0015] ASM-129 |
```

`docs/domain-model.md`:

- Fila BC-18 (~l. 309): `Asistente de solo lectura de wasichai-agent para AI-05, que hasta UPS-14 solo se ofrece a roles
  sin acceso a campos restringidos (hoy `ANALYST`, decisión pendiente de `security.md` §15), y agentes propios` pasa a
  `` Asistente de solo lectura de wasichai-agent para AI-05, con los resultados de sus herramientas seudonimizados y los
  campos restringidos enmascarados, para todo rol con CREATE sobre `ai_interaction` (ASM-130), y agentes propios ``.
- §8.3 (~l. 1074): `la\n  indexación para RAG (JOB-07, F7), que crea registros de `ai_interaction` y escribe la tabla fija
  `knowledge_chunk`, y` pasa a `` la\n  indexación para RAG (JOB-07, F7), que escribe la tabla fija `knowledge_chunk` y
  solo crearía `ai_interaction` al llamar a un modelo, lo que en F7 no hace, y ``.

- [ ] **Paso 6: Verificar y commit**

Repetir la comprobación del Paso 1 (esperado: `ok`) y comparar los estados:

```bash
cd ~/IdeaProjects/sgspe && python3 - <<'PY'
import os, re, collections, ast
t = open('docs/requirements-traceability.md', encoding='utf-8').read()
main = re.search(r'^## 2\..*?(?=^## 6\.)', t, re.M | re.S).group(0)
c = collections.Counter()
for l in main.splitlines():
    if re.match(r'^\| (RF|RNF|RN|RQ)-', l):
        st = l.rstrip().rstrip('|').split('|')[-1]
        for s in ('Diseñado', 'Implementado', 'Decisión pendiente', 'Upstream', 'Hueco', 'Fuera del MVP'):
            c[s] += s in st
before = ast.literal_eval(open(os.path.join(os.environ['TMPDIR'], 'estados-antes-7-0.txt')).read())
diff = {k: c[k] - before.get(k, 0) for k in set(c) | set(before) if c[k] != before.get(k, 0)}
print(diff)
raise SystemExit(0 if diff == {'Diseñado': 3, 'Upstream': -3} else 1)
PY
```

Esperado: `{'Diseñado': 3, 'Upstream': -3}` (en cualquier orden) y salida 0. Correr `yarn -s prettier --check
docs/roadmap.md docs/requirements-traceability.md docs/platform-fit.md docs/architecture.md docs/domain-model.md` y las
compuertas de «Global Constraints». Después:

```bash
git add docs/roadmap.md docs/requirements-traceability.md docs/platform-fit.md docs/architecture.md \
  docs/domain-model.md
git commit -F - <<'MSG'
docs: roadmap, trazabilidad y encaje de la fase 7

F7 con la especificación, el reparto en seis planes y su «Estado»; GAP-015 y UPS-14 resueltas en el
roadmap, el encaje y la arquitectura; RF-AIK-005, RF-AIK-009 y RQ-AI-009 pasan a Diseñado.

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01RmiD2JE3gkHdBNUraZkjgM
MSG
```

---

### Tarea 7: Pantallas y la historia

**Files:**
- Modify: `docs/ux/flows.md` (~l. 284, 291), `docs/ux/ux-architecture.md` (~l. 94, 120, 241-243),
  `docs/ux/screens.md` (~l. 1479, 1640-1641), `docs/ux/design-system.md` (~l. 439), `docs/HISTORY.md` (entrada nueva al
  principio)

**Interfaces:**
- Consume: ASM-128 a ASM-132 (Tarea 1); los códigos de `api.md` (Tarea 4); la decisión 11.
- Produce: la disponibilidad del asistente en el portal que implementa 7.3.

- [ ] **Paso 1: La comprobación falla**

```bash
cd ~/IdeaProjects/sgspe && python3 - <<'PY'
paths = ('docs/ux/flows.md', 'docs/ux/ux-architecture.md', 'docs/ux/screens.md', 'docs/ux/design-system.md')
docs = {p: open(p, encoding='utf-8').read() for p in paths}
bad = []
for p, t in docs.items():
    for old in ('hasta UPS-14', 'Hasta UPS-14', 'Por rol (UPS-14)', 'rol sin AI-05', 'solo `ANALYST`, ASM-073'):
        if old in t:
            bad.append(f'{p}: queda «{old}»')
    if 'ASM-130' not in t:
        bad.append(f'{p}: no cita ASM-130')
hist = open('docs/HISTORY.md', encoding='utf-8').read()
first = hist.split('\n## ', 2)[1]
if 'Plan 7.0' not in first.splitlines()[0] or 'ASM-128 a ASM-132' not in first:
    bad.append('HISTORY.md: la primera entrada no es la del plan 7.0')
print('\n'.join(bad) or 'ok')
raise SystemExit(1 if bad else 0)
PY
```

Esperado: salida 1.

- [ ] **Paso 2: Los documentos de la interfaz**

`docs/ux/flows.md`, flujo 18:

- `- **Actor:** quien tenga la IA activada en su organización; "preguntar a los datos" (AI-05) solo `ANALYST` hasta
  UPS-14.` pasa a:

```text
- **Actor:** quien tenga la IA activada en su organización y pueda registrar `ai_interaction` (ASM-130); "preguntar a
  los documentos" (AI-06) exige además leer los nombres de `person`.
```

- en la fila 2, `| Sin IA activada, proveedor caído o rol sin AI-05: se muestra la razón y la alternativa (preguntar a
  los documentos) |` pasa a `` | Sin IA activada o sin proveedor (`503 AI_UNAVAILABLE`), o un rol que no puede registrar
  `ai_interaction`: se muestra la razón; quien no lee los nombres de `person` solo ve "Preguntar a los datos" | ``.

`docs/ux/ux-architecture.md`:

- ~l. 94, reemplazar la fila «Asistente para todos» por:

```text
| Asistente para todos | Global y contextual | AI-05 para todo rol que pueda registrar `ai_interaction`, seudonimizado (ASM-130) | Estado "no disponible para tu rol" diseñado para los demás (`READ_ONLY`, `AUDITOR`, `PROJECT_ADMIN`); resúmenes y propuestas revisables sí disponibles |
```

- ~l. 120, en la fila «Analista (`ANALYST`)», `Único rol con el asistente de preguntas hasta UPS-14` pasa a `Usa
  "Preguntar a los datos" (AI-05, ASM-130), no "Preguntar a los documentos", porque no lee los nombres de `person``.
- ~l. 241-243 (§7.5), reemplazar:

```text
"Explicar" o "Preguntar sobre…" abre el mismo panel con el registro como contexto. Hasta UPS-14, las preguntas libres solo están
disponibles para `ANALYST` (RF-AIK-005); el resto ve el estado "no disponible para tu rol" con la razón, y sigue viendo los
resúmenes y las propuestas revisables. El panel tiene dos modos: "Preguntar a los datos" (AI-05) y "Preguntar a los documentos"
```

por:

```text
"Explicar" o "Preguntar sobre…" abre el mismo panel con el registro como contexto. Las preguntas a los datos (AI-05) están
disponibles para todo rol que pueda registrar `ai_interaction` (RF-AIK-005, ASM-130), y las preguntas a los documentos
(AI-06), para quien además lee los nombres de `person`; el resto ve el estado "no disponible para tu rol" con la razón, y
sigue viendo los resúmenes y las propuestas revisables. El panel tiene dos modos: "Preguntar a los datos" (AI-05) y
"Preguntar a los documentos"
```

  (la línea siguiente, «(AI-06, con citas y nunca sin fuente); …», no cambia). La fila del ejecutivo de §5 («Asistente
  si es `ANALYST`, si no Tareas») no cambia (decisión 11).

`docs/ux/screens.md`:

- ~l. 1479 (inicio del ejecutivo), `y el asistente (solo `ANALYST`, ASM-073).` pasa a `` y el asistente (si el
  ejecutivo es `ANALYST`, que puede registrar `ai_interaction`; ASM-073, ASM-130). ``
- ~l. 1640-1641 (S05), reemplazar:

```text
- **Roles:** "Preguntar a los datos" (AI-05) solo `ANALYST` hasta UPS-14 (`ai-architecture.md` §8); "Preguntar a los
  documentos" (AI-06) a los roles que pueden registrar `ai_interaction`.
```

por:

```text
- **Roles:** "Preguntar a los datos" (AI-05) a los roles que pueden registrar `ai_interaction` (`ai-architecture.md` §8,
  ASM-130); "Preguntar a los documentos" (AI-06) a esos roles si además leen los nombres de `person`.
```

`docs/ux/design-system.md` (§6.5): `| IA no disponible | Por rol (UPS-14), por organización o por falla del proveedor,
con la razón |` pasa a `` | IA no disponible | Por rol (sin CREATE sobre `ai_interaction`, ASM-130), por organización o
por falla del proveedor (`503 AI_UNAVAILABLE`), con la razón | ``.

- [ ] **Paso 3: La entrada de la historia**

En `docs/HISTORY.md`, insertar antes de `## 2026-10-11 — wasichai 1.2.0: UPS-30 resuelta` (la entrada más reciente va
primero; la fecha es la del día en que se ejecuta la tarea, `date +%F`; aquí, 2026-10-13):

```text
## 2026-10-13 — Plan 7.0: los documentos de F7

Los documentos de diseño quedan al día con la especificación de F7
(`docs/superpowers/specs/2026-10-12-fase-7-design.md`) antes de tocar el modelo y el código. Las decisiones están en
[Requisitos](requirements-analysis.md) (ASM-128 a ASM-132; 553 IDs).

- **Proveedor.** La API de Anthropic (GAP-015 resuelta); los datos seudonimizados se procesan fuera del Perú, una
  consecuencia aceptada. La IA se activa por organización con la lista del despliegue (ASM-128) y el modelo es
  configurable por caso (ASM-129).
- **Asistente.** UPS-14 queda resuelta (WAS-ADR-0056): AI-05 se ofrece a todo rol con CREATE sobre `ai_interaction`,
  con los resultados de las herramientas seudonimizados, los campos restringidos enmascarados y tokens (ASM-130).
- **Corridas.** Límites del servidor con `AI_INPUT_TOO_LARGE` y `AI_RUN_TRUNCATED` (ASM-131); extracción de PDF y DOCX
  sin OCR, con `AI_NO_TEXT` (ASM-132); `AI_UNAVAILABLE` sin activación o sin proveedor.
- **ADR.** ADR-0003 aceptado y ADR-0005 aceptado en su etapa 1; Embabel 1.5.3 en todos los documentos.

```

- [ ] **Paso 4: Verificar y commit**

Repetir la comprobación del Paso 1 (esperado: `ok`), correr `yarn -s prettier --check docs/ux/ docs/HISTORY.md` y las
compuertas de «Global Constraints». Después:

```bash
git add docs/ux/flows.md docs/ux/ux-architecture.md docs/ux/screens.md docs/ux/design-system.md docs/HISTORY.md
git commit -F - <<'MSG'
docs: el asistente de IA para todo rol con CREATE en las pantallas

Flujo 18, S05, la navegación y los estados del asistente sin la restricción de UPS-14; la entrada
del plan 7.0 en la historia.

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01RmiD2JE3gkHdBNUraZkjgM
MSG
```

---

### Tarea 8: Verificación final, push y PR

**Files:** ninguno nuevo; si la comprobación cruzada encuentra algo, se corrige en el documento que se aparta, con un
commit `docs: …` propio.

**Interfaces:**
- Consume: las Tareas 1 a 7 en la rama.
- Produce: el PR hacia `main`, que habilita 7.1 y 7.3.

- [ ] **Paso 1: Todo lo local y la comprobación cruzada**

```bash
cd ~/IdeaProjects/sgspe && (cd model && python3 -m unittest 2>&1 | tail -3 && python3 apply.py --validate-only && \
  python3 load_ubigeo.py --dry-run | tail -1) && python3 tools/check_traceability.py && \
  git ls-files -z | xargs -0 yarn -s prettier --check --ignore-unknown && yarn -s format:check; \
  python3 - <<'PY'
import glob, re
docs = {p: open(p, encoding='utf-8').read() for p in glob.glob('docs/**/*.md', recursive=True)
        if '/superpowers/' not in p and not p.endswith('HISTORY.md')}
bad = []
stale = re.compile(r'hasta UPS-14|[Mm]ientras (no exista|falte|faltara) UPS-14|sin seudonimizar|no seudonimiza|ANALYST')
for p, t in docs.items():
    for line in t.splitlines():
        if '1.5.2' in line and 'Embabel' in line:
            bad.append(f'{p}: Embabel 1.5.2: {line.strip()[:90]}')
        if 'UPS-14' in line and stale.search(line) and 'esuelta' not in line:
            bad.append(f'{p}: UPS-14 vigente: {line.strip()[:90]}')
        if re.search(r'[Ss]olo `ANALYST`', line) and 'reemplazado el 2026-10-12' not in line and 'ya no' not in line:
            bad.append(f'{p}: «solo ANALYST»: {line.strip()[:90]}')
        if 'GAP-015' in line and re.search(r'[Ss]e decide|Decidir antes|Bloquean|No hay proveedor elegido', line) \
                and 'esuelta' not in line and 'Decidido' not in line:
            bad.append(f'{p}: GAP-015 abierta: {line.strip()[:90]}')
an = docs['docs/requirements-analysis.md']
for n in range(128, 133):
    asm = f'ASM-{n}'
    if len(re.findall(rf'^\| {asm} \|', an, re.M)) != 1:
        bad.append(f'{asm}: no se define una sola vez')
    if not any(asm in t for p, t in docs.items() if p != 'docs/requirements-analysis.md'):
        bad.append(f'{asm}: ningún otro documento lo cita')
# Review Focus 2: el mismo estado para cada código en api.md, ai-architecture.md y los ASM
for code, status in (('AI_UNAVAILABLE', '503'), ('AI_INPUT_TOO_LARGE', '422'), ('AI_NO_TEXT', '422'),
                     ('AI_RUN_TRUNCATED', '502')):
    for p in ('docs/api.md', 'docs/ai-architecture.md', 'docs/requirements-analysis.md'):
        t = docs[p]
        if code not in t:
            bad.append(f'{p}: falta {code}')
        for m in re.finditer(rf'`?(\d{{3}})`? `?{code}', t):
            if m.group(1) != status:
                bad.append(f'{p}: {code} con {m.group(1)} en lugar de {status}')
print('\n'.join(bad) or 'ok')
raise SystemExit(1 if bad else 0)
PY
git status --short
```

Esperado: `Ran 285 tests … OK`, `ubigeo: 25 departamentos, 196 provincias, 1892 distritos`,
`ok: 298 requisitos, todos con su fila`, `All matched files use Prettier code style!` para los archivos versionados;
`yarn -s format:check` puede quejarse solo de `.playwright-mcp/` (sin versionar, fuera del repositorio: no se toca);
luego `ok` de la comprobación cruzada y un `git status --short` que solo muestra `?? .playwright-mcp/`. Si una línea
nombra UPS-14, GAP-015 o `ANALYST` en el pasado o en una nota fechada que no lo dé por vigente, se reescribe para decir
«resuelta» o «reemplazado el 2026-10-12» (nunca se afloja la comprobación).

Lectura cruzada (Review Focus 2): leer seguidos `api.md` §3.3 (filas `422`, `502`, `503` y el párrafo de F7), §4.14,
`ai-architecture.md` §6.2, §8, §12.1 y §15, `security.md` §11.5 y §14, y ASM-128 a ASM-132, y comprobar que cada código
tiene el mismo estado, el mismo campo y el mismo endpoint, y que los nombres de las piezas de AI-05 son los de «Global
Constraints». Una diferencia se corrige en el documento que se aparta, con un commit `docs: …` propio.

- [ ] **Paso 2: Push y PR**

```bash
cd ~/IdeaProjects/sgspe && git push -u origin docs/fase-7-0-documentos
gh pr create --base main --title "docs: fase 7, plan 7.0: documentos de F7" --body-file - <<'MSG'
## Qué trae

- La especificación de F7 (`docs/superpowers/specs/2026-10-12-fase-7-design.md`) y este plan.
- ASM-128 a ASM-132; §20.1 suma 553 IDs; GAP-015 resuelta (la API de Anthropic) y UPS-14 resuelta (WAS-ADR-0056).
  Ningún GAP, CON ni UPS nuevo.
- AI-05 para todo rol con CREATE sobre `ai_interaction`, con los resultados de las herramientas seudonimizados, los
  campos restringidos enmascarados y tokens: `AiAgentAccess`, `PseudonymizingResultFilter`, `RestoringAnswerFilter`,
  `InteractionRunListener` y `AgentHttpGate`.
- Activación por la lista del despliegue, modelo por caso, Embabel 1.5.3, límites de corrida y extracción de PDF y DOCX
  sin OCR; los errores `AI_UNAVAILABLE`, `AI_INPUT_TOO_LARGE`, `AI_RUN_TRUNCATED` y `AI_NO_TEXT` en `api.md`.
- ADR-0003 aceptado y ADR-0005 aceptado en su etapa 1; roadmap F7 con su «Estado» y el reparto en 7.0, 7.1, 7.2a, 7.2b,
  7.2c y 7.3; trazabilidad, encaje, seguridad, pantallas e historia al día.

## Pruebas

- Python: las 285 pruebas del modelo, `apply.py --validate-only` y `load_ubigeo.py --dry-run`.
- `tools/check_traceability.py` y prettier sobre los archivos versionados.
- La comprobación cruzada del plan 7.0 (Tarea 8): ninguna restricción de AI-05 por UPS-14, ningún Embabel 1.5.2 y los
  cuatro códigos con el mismo estado en `api.md`, `ai-architecture.md` y el análisis.

🤖 Generated with [Claude Code](https://claude.com/claude-code)

https://claude.ai/code/session_01RmiD2JE3gkHdBNUraZkjgM
MSG
```

Esperar la CI del PR en verde antes de integrarlo; 7.1 y 7.3 parten de `main` con este PR integrado.

---

## Resumen

| Tarea | Entrega |
|---|---|
| 1 | IDs de F7 en el análisis (ASM-128 a ASM-132, GAP-015 y UPS-14 resueltas, 553 IDs) |
| 2 | Arquitectura de IA |
| 3 | Seguridad |
| 4 | Contrato de la API |
| 5 | ADR-0003, ADR-0005, ADR-0017, ADR-0026 y el índice |
| 6 | Roadmap, trazabilidad, encaje, arquitectura y modelo de dominio |
| 7 | Pantallas e historia |
| 8 | Verificación, push y PR |

## Cobertura de la especificación §6 y de sus consecuencias

| Punto | Dónde |
|---|---|
| `requirements-analysis.md`: GAP-015 y UPS-14 resueltas; RF-AIK-005 sin «hasta UPS-14»; ASM de la decisión 3, la decisión 4, §2.2.1 y §2.2.2, §2.2.6 y §2.2.7 | Tarea 1 (ASM-128, ASM-129, ASM-130, ASM-131 y ASM-132) |
| `ai-architecture.md` §8 con los cuatro puntos de extensión; §9.2 con tokens; §11 con proveedor, residencia y Embabel 1.5.3; §12.1 sin la excepción de AI-05; §13.1 con ADR-0005 aceptado; §16 sin la restricción | Tarea 2 (además §6.2, §12.2, §13.2, §14, §15 y §18) |
| `security.md` §11.5 y §15 con el nuevo alcance; §14 con las pruebas de AI-05 | Tarea 3 |
| `api.md`: el asistente sin «mientras no exista UPS-14»; los cuatro códigos | Tarea 4 |
| ADR-0005 «Aceptado» (etapa 1) | Tarea 5 (y ADR-0003, decisión 6) |
| `roadmap.md` F7 con la especificación y el reparto (§7); Embabel 1.5.3; sin «Bloquean: GAP-015» ni el riesgo de UPS-14 | Tarea 6 |
| `requirements-traceability.md` y `check_traceability.py` en verde | Tareas 6 y 8 (y las compuertas de cada tarea) |
| Decisión 6 (release 3.0.0 al cerrar F8) | Tarea 6 («Estado» de F7) |
| §8 de la especificación (riesgos y consecuencias aceptadas) | Tareas 2 (§11, §16), 3 (§11.5) y 6 (riesgos de F7 y registro de riesgos) |
| Otros documentos que daban UPS-14 por abierta o Embabel 1.5.2 (`platform-fit.md`, `architecture.md`, `domain-model.md`, ADR-0017, ADR-0026, `ux/*.md`) | Tareas 5, 6 y 7 |

## Dependencias de otros planes

Este plan no consume código de otro plan: parte de la rama con la especificación de F7. Lo que deja para otros:

- **Plan 7.1:** lee ASM-130 y `security.md` §7.8 y §7.9 sin cambios de celdas; agrega en `roles.json` las filas de
  `ai_interaction` y `ai_suggestion` tal como están.
- **Plan 7.2a:** usa los nombres de `ai-architecture.md` §6.2 y §8, los campos de los errores de `api.md` §3.3
  (decisión 5) y anota en `roadmap.md` F7 «Estado» el modelo elegido por caso (ASM-129).
- **Planes 7.2b y 7.2c:** leen ASM-131, ASM-132 y `ai-architecture.md` §13; si al implementar cambia un nombre o un
  campo, se corrige primero el documento.
- **Plan 7.3:** lee `ux/*.md` y los códigos de `api.md` para los estados del asistente.
