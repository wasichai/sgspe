# Cambios que tocan el contrato del backend o de `@wasichai/*`

> Rediseño integral de UX/UI, entregable 10. Fecha: 2026-10-21. Propuesta, pendiente de revisión.
> Separa lo que el portal resuelve solo de lo que pide otro repositorio. **Ningún ítem bloquea las fases 1 a 5 del plan**
> ([06](06-plan-de-implementacion.md)): cada uno trae su alternativa sin el cambio.

## 1. Solo visuales: el portal los resuelve sin contrato nuevo

Todo lo de las fases 1 a 5 usa lecturas, permisos y comandos que ya existen:

| Cambio | Lee o escribe con | Hallazgos |
|---|---|---|
| Pestaña y migas de las fichas | La ruta | U-04 |
| Inicio «Mi día» para toda cuenta, con la visión ejecutiva como bloque | `AtencionRequerida`, `SuTrabajo`, `MisProyectos`, `Atajos` y `GET /api/sgspe/dashboards/executive` (las cifras `available`) | U-01 |
| Regla de la acción principal | `useAvailableTransitions` y la siguiente acción del `PanelDeDecision` | U-02, U-03, U-15 |
| `Seccion`, `SelectorDeVista`, `BarraDeFiltros`, `TablaPriorizada` y `ListaPriorizada` | Las mismas listas y sus parámetros de URL | U-05, U-06, U-18, U-22, U-34 |
| `CampoFecha` | `datos/fechas.ts` (`ahoraEnLima`, `ZONA_DE_LIMA`): el mismo valor que hoy | U-07 |
| `EstadoSinAcceso` | `useAuth().can` y `project.manager` si se lee | U-09 |
| Barra superior, navegación y pestañas | `navTree.ts` y la oferta por permisos | U-12, U-19, U-20, U-21 |
| Mapa primero | `GET /api/sgspe/map/settings`, las capas actuales y el proxy WMS | U-10, U-32 |
| Tokens nuevos y bordes de control | `sgspe/docs/ux/tokens.css`, copiado con `cmp` | U-39, U-40, U-41, U-43 |
| Aviso de fuente de verdad en S53 | Texto del portal | U-37 (parcial) |

## 2. Backend de SGSPE (`sgspe`)

| ID | Cambio | Por qué | Sin el cambio | Prioridad |
|---|---|---|---|---|
| B-01 | **Etiquetas de estado y de transición en inglés.** `model/workflows/*.json` gana una etiqueta por idioma (por ejemplo `labels: { es, en }`), si la metadata de workflows de wasichai la admite; si no, depende de W-08. ✅ Hecho (2026-10-24) con wasichai 1.4.0: los 19 workflows llevan `labels` con `es` y `en` (`docs/workflows.md`, «Formato JSON de un workflow»). | U-42: con la interfaz en inglés, los estados siguen en español | El portal sigue mostrando la etiqueta en español, que es el comportamiento actual | Media |
| B-02 | **Disponibilidad por cuenta en el catálogo de indicadores.** El `available` de `GET /api/sgspe/dashboards/indicators` dice si la cuenta que llama puede leer el indicador, no solo si el tenant lo tiene. | U-01: el inicio decide si dibuja el bloque ejecutivo antes de pedir el tablero | El portal pide el tablero ejecutivo y dibuja el bloque solo con las cifras `available`: una lectura más en el inicio | Baja. ✅ Hecho en el código: `Dashboards.catalog()` decide `available` con `Readability`, por objeto y sin el alcance por proyecto |
| B-03 | **Conteos del proyecto en una sola lectura** (opcional), por ejemplo `GET /api/sgspe/projects/{id}/counts`, con el mismo alcance de lectura (ADR-0029) y sin contar QCS confidenciales ilegibles. | Hoy `useConteosDelProyecto` (`contexto/conteos.ts:30`) hace una lectura por pestaña y por clase | Se mantienen las lecturas actuales: funciona y es correcto | Baja (rendimiento). Abierto en wasichai/sgspe#161 |
| B-04 | **Tareas «para tomar» de todos los proyectos de la cuenta**, en la misma lectura que «Mis tareas». | U-13: la cola compartida solo aparece al elegir un proyecto | El bloque «Lo que me toca hoy» pide la cola de cada proyecto vigente de la cuenta, con un tope y «Ver todas» | Media. ✅ Resuelto en el portal sin cambio de contrato (`sgspe-ui` `31def4b`): el servidor ya acota `task` a los proyectos de la cuenta y la cola se lee en una sola consulta. Queda la decisión para las cuentas con alcance de tenant: wasichai/sgspe#162 |

**Verificado, no es contrato.** U-23 («Plazo» vacío y «Vencidas 0» con una tarea vencida) era el efecto de no haber corrido
JOB-01 sobre la demo, no un defecto (2026-10-24, wasichai/sgspe#157). Después de la vuelta de las 16:05 (23 situaciones y 7
tareas de escalamiento), la tarea del 17/10, la queja DEMO-Q-03 y el compromiso DEMO-C-04 quedaron en VENCIDO, y la lista
y el inicio los cuentan. Que JOB-01 no corriera venía de un hilo único de planificación, tapado por JOB-05 y JOB-09 con la
base inaccesible: queda en wasichai/sgspe#164.

Fuera de esta tabla quedaron dos pedidos de backend que salieron de la fase 4: el orden por columna en la cola de QCS
(wasichai/sgspe#160) y los feriados para el calendario (wasichai/sgspe#159).

Ninguno de B-01 a B-04 define requisitos nuevos aquí: si se aprueban, se registran en
`docs/requirements-analysis.md` y en `docs/api.md`, como manda `CLAUDE.md`.

## 3. `@wasichai/*` (wasichai-ui y wasichai core)

Son pedidos a otro repositorio. Se abren como issues allí cuando el rediseño se apruebe.

✅ Publicados (2026-10-24): W-01 a W-08 se pidieron en wasichai/wasichai-ui#69 y salieron en `@wasichai/*` 1.1.0 y
wasichai 1.4.0; el portal los usa desde `sgspe-ui` `128a56b` y `4602821`.

| ID | Paquete | Cambio | Hallazgo | Sin el cambio |
|---|---|---|---|---|
| W-01 | `@wasichai/core` (`WasichaiApp`) | Lema y marca configurables en `/admin` (hoy dice «Plataforma de aplicaciones geoespaciales configurables») | U-08 | `/admin` conserva el lema de wasichai |
| W-02 | `@wasichai/core` (administración) | Mostrar el nombre del rol (`label`) en lugar de su código (`STAKEHOLDER_MANAGER`) | U-08 | Se ven los códigos |
| W-03 | `@wasichai/core` (administración) | Verbos de permiso traducidos (`READ` → «Leer», `MANAGE_METADATA` → «Administrar el modelo») por i18n | U-08 | Se ven en inglés |
| W-04 | `@wasichai/core` (auditoría) | Fechas con la zona y el formato de `config.timeZone`, y UUID en una línea con copia | U-08 | Se ven partidos y sin zona |
| W-05 | `@wasichai/core` (metadata) | Un aviso configurable, o un modo de solo lectura por objeto y workflow, cuando la fuente de verdad es el repositorio (GAP-030); «Eliminar flujo» en «Más acciones» con confirmación | U-37 | El portal avisa en S53 y en el puente (fase 5). Dentro de `/admin` no hay aviso |
| W-06 | `@wasichai/ui` (`wasichai.css`) | Un token de superficie flotante para menús, popovers, drawers y diálogos, que SGSPE asigne a `--sg-surface-raised` | U-40 (oscuro), DD-15 | El portal aplica `--sg-surface-raised` por selector en `src/index.css` |
| W-07 | `@wasichai/ui` (`Input`, `Textarea`, `Select`) | Un `data-slot` (o una variable `--border-control`) para su borde, y el texto de ejemplo con 4.5:1 | U-40, U-41 | El portal fija el borde y el ejemplo por selector de elemento en `src/index.css` |
| W-08 | `@wasichai/core` (workflows) | Etiquetas por idioma en la metadata de estados y transiciones, si hoy no se admiten | U-42 | Ver B-01 |

## 4. Lo que no cambia

- Ningún endpoint existente cambia de forma ni de semántica.
- Ninguna ruta del portal cambia (07 §3).
- El alcance de lectura sigue en el servidor (ADR-0029). Ningún ítem le pide al portal filtrar por proyecto ni contar lo
  que no lee.
- `Idempotency-Key`, `If-Match` y las transiciones con motivo siguen igual.
