# SGSPE · Fase 4, plan 4.4b (portal): alcance de lectura por proyecto, plan de implementación

> **Para agentes:** SUB-SKILL OBLIGATORIA: usar superpowers:subagent-driven-development (recomendada) o
> superpowers:executing-plans para ejecutar este plan tarea por tarea. Los pasos usan casillas (`- [ ]`) para el
> seguimiento. Cada tarea de código sigue superpowers:test-driven-development: prueba que falla, implementación mínima,
> prueba en verde, commit.

**Goal:** Llevar al portal de `sgspe-ui` el alcance de lectura por proyecto que el plan 4.4a pone en el backend: el
selector de proyectos con «Todos los proyectos» para quien tiene `READ_ALL_PROJECTS` (y el aviso de una cuenta sin
proyectos), el `404` de una ficha dicho sin distinguir «no existe» de «no es de sus proyectos», «Registro reservado» en
todo vínculo cuyo destino responde `404`, el `400` de una `RELATION` dicho en su campo, S49 sin proyecto obligatorio para
quien lee todo, y con eso el cierre de la Release 1.

**Architecture:** El servidor acota lo que se lee (`ProjectReadScope`, ADR-0029): el portal no filtra por proyecto, solo
dice bien lo que el servidor responde. Una sola pieza decide si la cuenta lee todos los proyectos,
`useLeeTodosLosProyectos` (`contexto/proyectos.ts`, `can('project', 'READ_ALL_PROJECTS')`, verdadero para ADMIN), y la
consumen el selector de la barra superior, S06 y el selector de proyecto de S49. `componentes/Estados.tsx` gana
`esIlegible` (un `404` de la API) y `NoEsDeSusProyectos` (el `noExiste` de una ficha); `RegistroVinculado`,
`AbrirRegistroPage` y la bandeja de aprobaciones dicen «Registro reservado» por cualquier `404`; `componentes/errores.ts`
traduce la violación en inglés de wasichai sobre un campo `RELATION` («no record with this id») a un texto propio. Los
textos nuevos van todos en la Tarea 1; las Tareas 2 a 5 tocan archivos disjuntos y corren en paralelo.

**Tech Stack:** React 19.3.0, React Router 8.4.0, TanStack Query 5.104.0, TypeScript 7.0.2, Vite 8.3.1, Vitest 5.0.2,
Testing Library, i18next 26.4.2, `@wasichai/core`, `ui`, `forms`, `testing` y demás en 0.6.0, `@playwright/test` 1.63.0,
Node 26 y Yarn 1. Ninguna dependencia nueva.

**Spec:** `docs/superpowers/specs/2026-10-10-alcance-por-proyecto-design.md` (vinculante): §1 (fila «Portal»), §2.1
(columna «Portal (4.4b)» de las tres decisiones), §3.6 (semántica de errores), §3.7 (tableros y exportaciones), §3.8
(avisos), §3.11 (`/api/auth/me/permissions` lista `READ_ALL_PROJECTS`), §3.12 (portal), §6 (fila 4.4b) y §7
(consecuencia 3 y riesgo 2). La decisión del usuario del 2026-10-10: `READ_ALL_PROJECTS` (acción declarada sobre
`project`) solo para `AUDITOR` y `PROJECT_ADMIN`; `ADMIN` ve todo por wasichai. Los documentos como los deja la primera
tarea de 4.4a (`docs/ux/screens.md` con «Registro reservado» general y el selector; `api.md` §3.3 con la semántica de
§3.6), que ganan donde difieren.

## Global Constraints

- **Repositorio y rama.** Todo el código vive en `~/IdeaProjects/sgspe-ui`, en la rama `feat/fase-4-4b-alcance` creada
  desde `main` (hoy `4fff315`, el merge de `@wasichai/*` 0.6.0). El plan vive en `sgspe`; solo la Tarea 7, después del
  merge del PR del portal, cambia `sgspe` (la fila «Estado» de F4 en `docs/roadmap.md`).
- **Orden con 4.4a.** «4.4b se ejecuta después de que 4.4a (backend) esté en `main`» (spec §6). Las pruebas no necesitan
  el backend (`mockFetch` y `espiarRed` en Vitest, `page.route` en Playwright); el smoke del coordinador de la Tarea 7 sí,
  con el tenant de desarrollo ya actualizado por la tarea 16 de 4.4a. Si 4.4a cambió un contrato de «Lo que el portal
  supone del backend», la tarea que lo consume se enmienda antes de despacharla.
- **Versiones fijadas, sin rangos.** Las de `package.json` en `main`; `src/dependencias.test.ts` lo comprueba. Ninguna
  dependencia nueva.
- **Instalación.** `yarn install --frozen-lockfile` antes de la primera prueba (la copia local puede tener `@wasichai/*`
  0.5.1 en `node_modules`: con ella fallan dos pruebas de `FormularioDeRegistro.test.tsx` que ya están en `main`). El
  token de lectura de GitHub Packages vive en `~/.npmrc`, nunca en el repositorio ni en la salida de un comando.
- **Puerta antes de cada commit.** `yarn format && yarn lint && yarn typecheck && yarn test && yarn build && yarn e2e`
  (CLAUDE.md de `sgspe-ui`). `yarn e2e` levanta `vite preview` en el puerto 5182: un `yarn dev` en ese puerto se detiene
  antes.
- **Sin nombres de rol en `src/`** (`src/boundaries.test.ts`): «lee todos los proyectos» se decide solo con
  `useLeeTodosLosProyectos`, y la cadena `READ_ALL_PROJECTS` aparece en código solo en `src/portal/contexto/proyectos.ts`
  (regla nueva de `boundaries.test.ts`, Tarea 1).
- **`@wasichai/*` solo por la raíz del paquete** (`boundaries.test.ts`).
- **Idioma.** Identificadores y comentarios del portal en español; rutas, objetos, campos, endpoints y constantes de la
  API en inglés. Textos en español (es-PE) e inglés con las mismas claves (`I18n.test.tsx`); ningún texto de interfaz
  lleva un código de error, un ID de requisito ni un nombre de la API (`textosSinCodigos.test.ts`).
- **«No existe» y «no es de sus proyectos» no se distinguen** (spec §3.6, §7 consecuencia 3): un `404` nunca dice cuál de
  los dos es, ni en una ficha, ni en un vínculo, ni en un error de escritura.
- **Nunca un vacío ni un cero por una lectura que no terminó**, y un `403` sigue siendo «No disponible».
- **`NOT_ASSIGNED` se sigue mostrando como hoy** para el caso que queda (spec §3.12): quien lee todos los proyectos sin
  estar asignado (`PROJECT_ADMIN`) y escribe; sus textos de `comun.codigos`, `reportes.errores`, `evidencias` e
  `importacion` no cambian.
- **Pruebas sin esperas reales**; usuarios ficticios `@sgspe.test`; proyectos de prueba nuevos con nombres ficticios
  («Proyecto Ejemplo A», «Proyecto Ejemplo B»); ningún nombre real.
- **Commits.** Conventional Commits con asunto en español, cuerpo de 100 columnas como máximo y, al final, tras una
  línea en blanco, exactamente `Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>` y `Claude-Session:
  https://claude.ai/code/session_017MAgYcNauGp6cC3ekX1h7U`. `git add` con rutas explícitas.
- **Push y PR** solo en la Tarea 7.

## Review Focus

1. **Un enlace viejo a un registro que la cuenta ya no lee.** Un aviso de la bandeja (que no se filtra, spec §3.8), un
   marcador o una URL compartida llevan a un registro de otro proyecto o de una asignación vencida: no debe saltar a una
   ficha vacía ni ofrecer «Reintentar». `/r/…` dice «Registro reservado» por cualquier objeto y `/projects/<otro>/…` dice
   que no existe o no es de sus proyectos, con «Ir a todos los proyectos». Pruebas: «un registro que la cuenta no lee o
   que ya no existe (404) es «Registro reservado»…» (`avisos.test.tsx`, Tarea 4); «un proyecto que responde 404 dice que
   no existe o no es de sus proyectos…» (`contexto.test.tsx`, Tarea 2); el recorrido de Playwright (Tarea 6).
2. **Una asignación que vence con la ficha abierta.** La relectura de la ficha falla con `404` pero conserva lo cargado
   (`Consulta` solo dibuja el error sin datos); guardar o transicionar responde `404` y debe decirse con el texto
   genérico, nunca «Record … does not exist» ni «Reintentar» en bucle. Pruebas: «un 404 al guardar dice que el registro
   no existe o no es de sus proyectos» (`FormularioDeRegistro.test.tsx`, Tarea 1); las fichas de la Tarea 3
   (`fichasFueraDeAlcance.test.tsx`).
3. **Una cuenta nueva sin ninguna asignación.** El servidor no le devuelve proyectos: el selector no debe ser un `select`
   con solo «Todos mis proyectos» que lleva a listas vacías, y S06 no debe invitar a «Nuevo proyecto» a quien no puede
   crear. Pruebas: «sin proyectos que leer ni la acción de leer todos, dice que no tiene proyectos asignados…»
   (`contexto.test.tsx` y `ProyectosPage.test.tsx`, Tarea 2); el recorrido de Playwright (Tarea 6).
4. **Quien lee todo sin estar asignado (`AUDITOR`, `PROJECT_ADMIN`).** Ve «Todos los proyectos» y todos los proyectos,
   pero no debe ver «Todos mis proyectos» (que mentiría) ni el aviso de cuenta sin proyectos en un tenant vacío; dentro de
   un proyecto ajeno sigue el aviso de solo lectura y la escritura oculta (`useEscribeEnProyecto`). Pruebas: «con la
   acción de leer todos… «Todos los proyectos», por defecto, aunque no tenga asignaciones» (`contexto.test.tsx`), «con la
   acción de leer todos y ningún proyecto en la organización…» (`ProyectosPage.test.tsx`) y «con la acción de leer todos
   los proyectos, el alcance de la exportación es «Todos los proyectos»…» (`ReportesPage.test.tsx`, Tarea 2); la prueba
   existente «sin asignación al proyecto avisa que es de solo lectura» (`contexto.test.tsx`).
5. **Un selector de relación que ofreció un registro que dejó de leerse antes de guardar.** wasichai responde `400` con
   `errors[].message` = «no record with this id» en inglés y `detail` «Invalid value for 'stakeholder'»: ninguno de los
   dos debe llegar a la pantalla; el texto propio va junto al campo (o con su etiqueta en `FormularioDeRegistro`).
   Pruebas: «el 400 de una relación a un registro que no se lee dice su texto, no el inglés de wasichai»
   (`componentes.test.tsx`), «un 400 sobre una relación oculta se dice con la etiqueta del campo…»
   (`FormularioDeRegistro.test.tsx`) y «el grupo de interés que el servidor no deja vincular (400 sobre la relación)…»
   (`NuevaQcsPage.test.tsx`, Tarea 5).

## Decisiones de este plan frente a la especificación

| # | Decisión | Tarea |
|---|---|---|
| 1 | «Lee todos los proyectos» es `can('project', 'READ_ALL_PROJECTS')`: el `can` de `@wasichai/core` da verdadero a ADMIN, así que ADMIN entra sin un caso aparte | 1 |
| 2 | Para quien no tiene la acción, el alcance global sigue llamándose «Todos mis proyectos» (ahora es literal: el servidor solo le da sus proyectos); con la acción, «Todos los proyectos». Las dos opciones son el valor `''` de siempre, así que el valor por defecto no cambia | 2 |
| 3 | El aviso «No tiene proyectos asignados…» sale cuando la lista de `project` llegó vacía y la cuenta no tiene la acción; el selector deja de dibujarse en ese caso y S06 lo dice en lugar de «Un proyecto nace con…» si además no puede crear proyectos | 2 |
| 4 | `comun.errores.404` pasa a «El registro no existe o no es de sus proyectos.»: lo usan el `404` de una escritura, de una transición, de un vínculo M2M y de una emisión, que con el alcance significan lo mismo que el de una lectura | 1 |
| 5 | El `404` de una ficha se dice con `NoEsDeSusProyectos` (el mismo texto) en las siete fichas que hoy muestran un error con «Reintentar» (acuerdo, evidencia, grupo de interés, interacción, acta, aprobación, área de influencia); las seis que ya tenían su `noExiste` (queja, consulta, solicitud, compromiso, riesgo, incidente y alerta) cambian solo su texto, y las de las QCS siguen nombrando la confidencialidad, sin distinguir tampoco | 3 |
| 6 | Las fichas de comunidad y de localidad no cambian: son maestros del tenant (spec decisión 3, ASM-107), su `404` no viene del alcance | 3 |
| 7 | `Consulta` no gana un `404` por defecto: los tableros y las listas lo usan, y para ellos un `404` es otra cosa (un objeto que el tenant no tiene); cada ficha lo pide con `noExiste` | 1, 3 |
| 8 | «Registro reservado» por cualquier `404` vale en `RegistroVinculado`, `AbrirRegistroPage` y la fila de `AprobacionesPage`; su ayuda deja de hablar solo de las QCS. `FalloDeLectura` no cambia: lo usan 162 lecturas, y el `404` de una lista es un objeto ausente del tenant (S46, F3), no un vínculo | 4 |
| 9 | Los vínculos que una ficha lee a mano en su panel de trazabilidad (la interacción y el acuerdo de un compromiso, la visita de una interacción…) no pasan a `RegistroVinculado`: `ProjectLinkGuard` exige el mismo proyecto, así que el alcance no los vuelve ilegibles | 4 |
| 10 | La violación de wasichai sobre una `RELATION` se reconoce por su texto exacto, «no record with this id» (`RelationTargets.kt` de wasichai 0.6.0), y se dice «El registro elegido no existe o es de otro proyecto.» en el campo y en el aviso general | 5 |
| 11 | El `404` de `POST …/related` (vínculo M2M a un registro que no se lee) se dice con `comun.errores.404`: wasichai 0.6.0 responde `404` ahí, no el `400` que dice la spec §3.6 (ver «Lo que el portal supone») | 1 |
| 12 | S49 no exigía un proyecto en el portal: el cambio es la etiqueta «Todos los proyectos» y no ocultar nada a quien lee todo; el servidor deja de responder `NOT_ASSIGNED` con la acción (spec §3.7). Hoy ningún rol tiene a la vez `EXPORT` y la acción, así que solo ADMIN lo ve | 2 |
| 13 | El aviso de solo lectura de `ProyectoLayout` no cambia de texto: con el alcance solo lo ve quien lee todos los proyectos sin estar asignado, y sigue siendo verdad | 2 |
| 14 | `RegistrarInteraccionPage` y `VisitaDeCampoPage` siguen diciendo `NOT_ASSIGNED` sin asignación: es el caso de `PROJECT_ADMIN` que queda (spec §3.12) | — (confirmada por el usuario) |
| 15 | El nombre de una mina real en `ReportesPage.test.tsx` se cambia por uno ficticio (pedido del usuario) | 1 |
| 16 | El usuario confirmó (2026-10-10): el texto del `404` de las fichas de QCS que nombra la confidencialidad (decisión 5) y el cierre de la Release 1 como PR de documentos en sgspe dentro de la tarea 7 | 3, 7 |

## Lo que el portal supone del backend (contrato que 4.4a debe respetar)

1. `GET /api/auth/me/permissions` lista `READ_ALL_PROJECTS` en `objects.project` para `AUDITOR` y `PROJECT_ADMIN`
   (WAS-ADR-0042, spec §3.11) y `admin: true` para ADMIN; ningún otro rol la tiene.
2. `GET /api/objects/project/records` devuelve solo los proyectos legibles (asignaciones vigentes, o todos con la acción);
   `GET /api/objects/project_assignment/records?member_user_id=<yo>` devuelve las propias aunque estén vencidas
   (elección 3).
3. Detalle, historial, relacionados, transiciones, `PUT`, `PATCH`, `DELETE`, `GET …/documents` y `GET
   /api/documents/{id}` de un registro fuera del alcance responden `404` problem+json, igual que uno inexistente
   (spec §3.4 y §3.6).
4. El alta o el cambio con una `RELATION` a un registro ilegible responde `400` con `errors: [{ field: '<campo>',
   message: 'no record with this id' }]` (el nombre del campo sin `attributes.`), como `RelationTargets.kt:110-113` de
   wasichai 0.6.0. Si 4.4a o un wasichai posterior cambian ese texto, cambia `SIN_REGISTRO` en `componentes/errores.ts`.
5. `POST /api/objects/{o}/records/{id}/related/{rel}` con un `otherId` ilegible responde **`404`** («Record … does not
   exist», `RelatedRecordService.kt:278-281` de wasichai 0.6.0), no el `400` sobre el campo que dice la spec §3.6. El
   portal lo dice con `comun.errores.404` en cualquiera de los dos casos; 4.4a debería corregir la fila de §3.6 en
   `api.md` o documentar la diferencia.
6. Tableros e indicadores con un `filters.project` ajeno devuelven conjuntos vacíos sin error; las exportaciones con la
   acción no responden `403 NOT_ASSIGNED` (spec §3.7). Sin cambios de contrato en `/api/sgspe/**`.
7. `403 NOT_ASSIGNED` solo para quien lee sin estar asignado.

## Mapa de archivos

Rutas relativas a la raíz de `sgspe-ui`. «M» es un archivo que ya existe y cambia.

| Ruta | Tarea |
|---|---|
| `CLAUDE.md` (M), `HISTORY.md` (M), `README.md` (M) | 7 |
| `e2e/alcance.spec.ts` | 6 |
| `src/boundaries.test.ts` (M) | 1 |
| `src/portal/acuerdos/FichaAcuerdoPage.tsx` (M) | 3 |
| `src/portal/atencion/FichaConsultaPage.test.tsx` (M), `FichaQuejaPage.test.tsx` (M) | 3 |
| `src/portal/atencion/NuevaQcsPage.test.tsx` (M) | 5 |
| `src/portal/avisos/AbrirRegistroPage.tsx` (M) | 4 |
| `src/portal/avisos/avisos.test.tsx` (M) | 1, 4 |
| `src/portal/componentes/Estados.tsx` (M) | 1 |
| `src/portal/componentes/RegistroVinculado.tsx` (M) | 4 |
| `src/portal/componentes/componentes.test.tsx` (M) | 1, 5 |
| `src/portal/componentes/componentesF4.test.tsx` (M) | 1, 4 |
| `src/portal/componentes/errores.ts` (M) | 5 |
| `src/portal/componentes/FormularioDeRegistro.test.tsx` (M) | 1, 5 |
| `src/portal/compromisos/FichaCompromisoPage.test.tsx` (M) | 3 |
| `src/portal/contexto/ProyectoLayout.tsx` (M), `SelectorDeProyecto.tsx` (M) | 2 |
| `src/portal/contexto/contexto.test.tsx` (M) | 1, 2 |
| `src/portal/contexto/proyectos.ts` (M) | 1 |
| `src/portal/documentos/DocumentosEmitidos.tsx` (M, comentario) | 5 |
| `src/portal/documentos/FichaEvidenciaPage.tsx` (M) | 3 |
| `src/portal/fichasFueraDeAlcance.test.tsx` | 3 |
| `src/portal/grupos/FichaGrupoPage.tsx` (M) | 3 |
| `src/portal/proyectos/AreaDeInfluenciaPage.tsx` (M) | 3 |
| `src/portal/proyectos/ProyectosPage.tsx` (M), `ProyectosPage.test.tsx` (M) | 2 |
| `src/portal/relacionamiento/EditorDeActaPage.tsx` (M), `FichaInteraccionPage.tsx` (M) | 3 |
| `src/portal/reportes/exportacion.tsx` (M), `ReportesPage.test.tsx` (M) | 2 |
| `src/portal/riesgos/FichaRiesgoPage.test.tsx` (M), `alertas.test.tsx` (M), `incidentes.test.tsx` (M) | 3 |
| `src/portal/tareas/AprobacionPage.tsx` (M) | 3 |
| `src/portal/tareas/AprobacionesPage.tsx` (M), `aprobaciones.test.tsx` (M) | 4 |
| `src/portal/textos/comun.ts` (M), `contexto.ts` (M) | 1 |
| `src/portal/textos/reportes.ts` (M) | 2 |
| `src/portal/textos/alertas.ts`, `fichaCompromiso.ts`, `fichaConsulta.ts`, `fichaQueja.ts`, `fichaRiesgo.ts`, `incidentes.ts` (M) | 3 |
| `src/portal/textos/avisos.ts` (M), `piezas.ts` (M) | 4 |
| `../sgspe/docs/roadmap.md` (M, después del merge) | 7 |

## Orden y paralelismo

Cada implementador trabaja en su propio worktree. Una tarea paralela toca solo sus archivos (el mapa) y consume solo lo
que produjo la Tarea 1.

| Ola | Tareas | Parten de | Por qué así |
|---|---|---|---|
| 0 | 1 | `main` | La rama, la instalación, la pieza `useLeeTodosLosProyectos`, `esIlegible`, `NoEsDeSusProyectos`, la regla de `boundaries.test.ts` y todos los textos de `comun` y `contexto`: las demás los consumen |
| 1 | 2, 3, 4, 5 | el commit de la Tarea 1 | Cuatro tareas en archivos disjuntos (el selector y S49; las fichas; los vínculos; los errores de campo). Se mezclan en el orden de los números sin conflictos |
| 2 | 6 | la ola 1 mezclada | Playwright recorre el selector, S06 y `/r/…` ya cambiados |
| 3 | 7 | la Tarea 6 | Documentos de `sgspe-ui`, puerta completa, push, PR, smoke del coordinador y cierre de la Release 1 |

---

### Tarea 1: Bases del alcance: quién lee todo, el 404 ilegible y los textos

**Files:**

- Modify: `src/portal/contexto/proyectos.ts`
- Modify: `src/portal/componentes/Estados.tsx`
- Modify: `src/portal/textos/comun.ts`, `src/portal/textos/contexto.ts`
- Modify: `src/boundaries.test.ts`
- Test: `src/portal/contexto/contexto.test.tsx`, `src/portal/componentes/componentes.test.tsx`,
  `src/portal/componentes/FormularioDeRegistro.test.tsx`, `src/portal/componentes/componentesF4.test.tsx`,
  `src/portal/avisos/avisos.test.tsx`

**Interfaces:**

- Consumes: `useAuth().can(objeto, accion)` de `@wasichai/core` (verdadero para ADMIN); `ApiError` de `@wasichai/core`.
- Produces:
  - `useLeeTodosLosProyectos(): boolean` en `src/portal/contexto/proyectos.ts`.
  - `esIlegible(error: unknown): boolean` y `NoEsDeSusProyectos(): JSX.Element` en `src/portal/componentes/Estados.tsx`.
  - Textos: `comun.errores.404` = «El registro no existe o no es de sus proyectos.»; `comun.registroReservadoAyuda`
    general; `comun.codigosEnCampo.registroNoLegible` = «El registro elegido no existe o es de otro proyecto.»;
    `contexto.todosLosProyectos` = «Todos los proyectos»; `contexto.sinProyectos` = «No tiene proyectos asignados.
    Pida una asignación al administrador de proyectos.» (con sus versiones en inglés).

- [ ] **Step 1: Rama e instalación**

```bash
cd ~/IdeaProjects/sgspe-ui
git switch main && git pull --ff-only
git switch -c feat/fase-4-4b-alcance
yarn install --frozen-lockfile
node -p "require('./node_modules/@wasichai/core/package.json').version"
```

Expected: `0.6.0`.

- [ ] **Step 2: Escribir las pruebas que fallan**

En `src/portal/contexto/contexto.test.tsx`, cambiar la importación de `./proyectos`:

```tsx
import { useLeeTodosLosProyectos, useMisAsignaciones, useProyecto } from './proyectos'
```

y agregar, después del `describe('filtroDeTerritorio', …)`:

```tsx
// la acción declarada READ_ALL_PROJECTS sobre project exime del alcance de lectura por proyecto (ADR-0029); ADMIN la tiene siempre
describe('useLeeTodosLosProyectos', () => {
  function Sonda() {
    return <span>{useLeeTodosLosProyectos() ? 'lee todo' : 'lee lo suyo'}</span>
  }
  it.each([
    ['con la acción sobre project', permisos({ project: ['READ', 'READ_ALL_PROJECTS'] }), 'lee todo'],
    ['ADMIN', ADMIN_PERMISOS, 'lee todo'],
    ['solo READ sobre project', permisos({ project: ['READ'] }), 'lee lo suyo'],
    ['la acción sobre otro objeto no cuenta', permisos({ project: ['READ'], commitment: ['READ_ALL_PROJECTS'] }), 'lee lo suyo']
  ])('%s', async (_caso, p, texto) => {
    fetch = mockFetch([])
    montar(<Sonda />, { permisos: p })
    expect(await screen.findByText(texto)).toBeInTheDocument()
  })
})
```

En el mismo archivo, en el `it.each` de «un proyecto que responde %i…», cambiar la fila del 404:

```tsx
    [404, 'El registro no existe o no es de sus proyectos.'],
```

En `src/portal/componentes/componentes.test.tsx`, cambiar la importación de `./Estados`:

```tsx
import { Cargando, Consulta, ErrorDeCarga, esIlegible, ExplicacionDeColumna, NoDisponible, NoEsDeSusProyectos, Restringido, ThConExplicacion } from './Estados'
```

y agregar dentro de `describe('Consulta (revisión final, I1)', …)`, después de su último `it`:

```tsx
  it('con noExiste={<NoEsDeSusProyectos />}, un 404 dice que no existe o no es de sus proyectos, sin reintento ni el detalle', async () => {
    montar(
      <Consulta consulta={consulta(undefined, new ErrorDeSgspe(404, 'Record r9 does not exist', [], null))} noExiste={<NoEsDeSusProyectos />}>
        {() => null}
      </Consulta>
    )
    expect(await screen.findByText('El registro no existe o no es de sus proyectos.')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Reintentar' })).not.toBeInTheDocument()
    expect(screen.queryByText(/does not exist/)).not.toBeInTheDocument()
  })

  it('esIlegible es un 404 de la API y nada más', () => {
    expect(esIlegible(new ErrorDeSgspe(404, 'x', [], null))).toBe(true)
    expect(esIlegible(new ErrorDeSgspe(403, 'x', [], null))).toBe(false)
    expect(esIlegible(new Error('sin red'))).toBe(false)
    expect(esIlegible(null)).toBe(false)
  })
```

En `src/portal/componentes/FormularioDeRegistro.test.tsx`, agregar después del `it('muestra todos los errores de campo
reconocidos…')`:

```tsx
  it('un 404 al guardar dice que el registro no existe o no es de sus proyectos (alcance por proyecto), sin el detalle', async () => {
    fetch = mockFetch([{ path: '/metadata/objects/community_sector', body: definicion('community_sector', [['name', { label: 'Nombre' }]]) }])
    red = espiarRed([{ method: 'PUT', path: '/objects/community_sector/records/s1', responder: () => problema(404, 'Record s1 does not exist') }])
    montar(<FormularioDeRegistro objeto="community_sector" registro={registro('s1', { name: 'Sector alto' })} />)
    const nombre = await screen.findByLabelText('Nombre')
    await userEvent.clear(nombre)
    await userEvent.type(nombre, 'Sector bajo')
    await userEvent.click(screen.getByRole('button', { name: 'Guardar' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('El registro no existe o no es de sus proyectos.')
    expect(screen.queryByText(/does not exist/)).not.toBeInTheDocument()
  })
```

En `src/portal/componentes/componentesF4.test.tsx`, en «una QCS que la cuenta no lee (404) es «Registro reservado»…»,
cambiar la última aserción:

```tsx
    expect(screen.getByText('Registro reservado')).toHaveAccessibleDescription(/Su cuenta no lee este registro/)
```

En `src/portal/avisos/avisos.test.tsx`, en «una QCS confidencial que la cuenta no lee (404) es «Registro reservado»…»,
cambiar la segunda aserción:

```tsx
    expect(screen.getByText(/Su cuenta no lee este registro/)).toBeInTheDocument()
```

En `src/boundaries.test.ts`, agregar dentro de `describe('fronteras de la interfaz', …)`, después de «importa
@wasichai/* solo por la raíz del paquete»:

```ts
  // quién lee todos los proyectos lo decide una sola pieza (useLeeTodosLosProyectos): la acción declarada no se pregunta suelta
  // en las pantallas, como un rol no se nombra (spec 4.4 §3.12)
  it('la acción de leer todos los proyectos solo se nombra en contexto/proyectos.ts', () => {
    const lugares = hallazgos(/(?<![A-Za-z0-9_])READ_ALL_PROJECTS(?![A-Za-z0-9_])/).map((h) => h.split(':')[0])
    expect([...new Set(lugares)]).toEqual(['portal/contexto/proyectos.ts'])
  })
```

- [ ] **Step 3: Correr las pruebas y ver que fallan**

Run: `yarn vitest run src/portal/contexto/contexto.test.tsx src/portal/componentes/componentes.test.tsx src/portal/componentes/FormularioDeRegistro.test.tsx src/portal/componentes/componentesF4.test.tsx src/portal/avisos/avisos.test.tsx src/boundaries.test.ts`
Expected: FAIL: `useLeeTodosLosProyectos`, `esIlegible` y `NoEsDeSusProyectos` no se exportan; los textos nuevos no
aparecen; `boundaries.test.ts` encuentra `[]` en lugar de `['portal/contexto/proyectos.ts']`.

- [ ] **Step 4: Implementar**

En `src/portal/contexto/proyectos.ts`, reemplazar el comentario de `useMisAsignaciones`:

```ts
// los proyectos de la persona: los que tienen una asignación activa suya (RF-PRJ-005, RQ-SEC-006). es una comodidad
// para ordenar y filtrar, no un control de acceso: con la opción A se leen todos los del tenant (security.md §9)
```

por:

```ts
// las asignaciones de la persona (RF-PRJ-005, RQ-SEC-006), también las vencidas: el servidor se las deja leer (elección 3 de la
// spec 4.4). sirven para ordenar el selector y para la guarda de UI de la escritura; lo que se lee ya viene acotado por el
// servidor (alcance por proyecto, ADR-0029), así que el portal no filtra por proyecto lo que lee
```

y agregar al final del archivo:

```ts
// la acción declarada sobre project que exime del alcance de lectura por proyecto (ADR-0029, spec 4.4 §2.1 decisión 2): quien
// la tiene lee todos los proyectos del tenant. can de core da verdadero a ADMIN, así que ADMIN entra sin caso aparte. es el
// único lugar del portal que la nombra (boundaries.test.ts)
const LEE_TODOS_LOS_PROYECTOS = 'READ_ALL_PROJECTS'

export function useLeeTodosLosProyectos(): boolean {
  const { can } = useAuth()
  return can('project', LEE_TODOS_LOS_PROYECTOS)
}
```

En `src/portal/componentes/Estados.tsx`, después de `export const esProhibido = …`, agregar:

```tsx
// un registro que la cuenta no lee responde 404, igual que uno que no existe: el alcance de lectura por proyecto (ADR-0029) y
// el de las QCS confidenciales (ADR-0028) lo ocultan así, y el portal no distingue «no existe» de «no es de sus proyectos»
// (spec 4.4 §3.6)
export const esIlegible = (error: unknown): boolean => error instanceof ApiError && error.status === 404

// lo que dibuja la ficha de un registro que responde 404 (el noExiste de Consulta): que no existe o no es de sus proyectos, sin
// distinguir y sin un «Reintentar» que no sirve
export function NoEsDeSusProyectos() {
  const { t } = useTranslation(NS)
  return <Alert tone="warning">{t('comun.errores.404')}</Alert>
}
```

y cambiar el comentario de `Reservado`:

```tsx
// el vínculo a una QCS confidencial que quien mira no lee (ASM-095, screens.md §8): wasichai responde 404 por ella (security.md
// §9.6), así que se dice «Registro reservado», sin enlace ni datos, con el motivo a mano
```

por:

```tsx
// el vínculo a un registro que quien mira no lee: de un proyecto en que no está asignado (ADR-0029), una QCS confidencial
// (ASM-095) o uno borrado. wasichai responde 404 por los tres y el portal no los distingue: «Registro reservado», sin enlace ni
// datos, con el motivo a mano
```

En `src/portal/textos/comun.ts`, en `ES_COMUN`:

```ts
  registroReservadoAyuda: 'Es una QCS confidencial: solo la leen la gestión de quejas, su responsable y quien la recibió.',
```

pasa a:

```ts
  registroReservadoAyuda: 'Su cuenta no lee este registro: es de un proyecto al que no está asignada, es confidencial o ya no existe.',
```

```ts
    qcsNoLeida: 'Su cuenta no puede leer esa queja, consulta o solicitud (puede ser confidencial): no se le vincula nada.'
  },
```

pasa a:

```ts
    qcsNoLeida: 'Su cuenta no puede leer esa queja, consulta o solicitud (puede ser confidencial): no se le vincula nada.',
    // el 400 de wasichai sobre una relación cuyo destino la cuenta no lee: no existe o es de otro proyecto (spec 4.4 §3.6)
    registroNoLegible: 'El registro elegido no existe o es de otro proyecto.'
  },
```

```ts
    '404': 'El registro no existe o no es de su organización.',
```

pasa a:

```ts
    '404': 'El registro no existe o no es de sus proyectos.',
```

En `EN_COMUN`:

```ts
  registroReservadoAyuda: 'It is a confidential grievance, inquiry or request: only grievance management, its owner and whoever received it read it.',
```

pasa a:

```ts
  registroReservadoAyuda: 'Your account cannot read this record: it belongs to a project it is not assigned to, it is confidential or it no longer exists.',
```

```ts
    qcsNoLeida: 'Your account cannot read that grievance, inquiry or request (it may be confidential): nothing is linked to it.'
  },
```

pasa a:

```ts
    qcsNoLeida: 'Your account cannot read that grievance, inquiry or request (it may be confidential): nothing is linked to it.',
    registroNoLegible: 'The chosen record does not exist or belongs to another project.'
  },
```

```ts
    '404': 'The record does not exist or is not from your organization.',
```

pasa a:

```ts
    '404': 'The record does not exist or is not in your projects.',
```

En `src/portal/textos/contexto.ts`, en `ES_CONTEXTO`, después de `todos: 'Todos mis proyectos',`:

```ts
  // con la acción de leer todos los proyectos (o ADMIN), el alcance global no es «mis» proyectos (plan 4.4b)
  todosLosProyectos: 'Todos los proyectos',
  sinProyectos: 'No tiene proyectos asignados. Pida una asignación al administrador de proyectos.',
```

y en `EN_CONTEXTO`, después de `todos: 'All my projects',`:

```ts
  todosLosProyectos: 'All projects',
  sinProyectos: 'You have no assigned projects. Ask the project administrator for an assignment.',
```

- [ ] **Step 5: Correr las pruebas y ver que pasan**

Run: `yarn vitest run src/portal/contexto/contexto.test.tsx src/portal/componentes/componentes.test.tsx src/portal/componentes/FormularioDeRegistro.test.tsx src/portal/componentes/componentesF4.test.tsx src/portal/avisos/avisos.test.tsx src/boundaries.test.ts src/portal/I18n.test.tsx src/portal/textosSinCodigos.test.ts`
Expected: PASS.

- [ ] **Step 6: Puerta completa y commit**

Run: `yarn format && yarn lint && yarn typecheck && yarn test && yarn build && yarn e2e`
Expected: todo en verde.

```bash
git add src/portal/contexto/proyectos.ts src/portal/componentes/Estados.tsx src/portal/textos/comun.ts \
  src/portal/textos/contexto.ts src/boundaries.test.ts src/portal/contexto/contexto.test.tsx \
  src/portal/componentes/componentes.test.tsx src/portal/componentes/FormularioDeRegistro.test.tsx \
  src/portal/componentes/componentesF4.test.tsx src/portal/avisos/avisos.test.tsx
git commit -m "feat: bases del alcance de lectura por proyecto en el portal" -m "useLeeTodosLosProyectos dice si la cuenta tiene READ_ALL_PROJECTS sobre project (ADMIN
siempre) y es el único lugar que nombra la acción. esIlegible y NoEsDeSusProyectos dicen un
404 sin distinguir si el registro existe; el 404 de una escritura y la ayuda de «Registro
reservado» dejan de hablar solo de la organización y de las QCS.

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_017MAgYcNauGp6cC3ekX1h7U"
```

- [ ] **Step 7: Nombre ficticio en `ReportesPage.test.tsx`** (decisión 15)

La prueba usa el nombre de una mina real como nombre de proyecto. Se reemplaza por uno ficticio en sus cuatro apariciones
(líneas 32, 35, 204 y 223) y se comprueba que no quede ninguna.

```bash
sed -i '' "s/Antamina Norte/Wayra Norte/g" src/portal/reportes/ReportesPage.test.tsx
grep -rn "Antamina" src e2e || echo "sin coincidencias"
yarn vitest run src/portal/reportes/ReportesPage.test.tsx
```

Expected: `sin coincidencias` y la prueba en verde.

```bash
git add src/portal/reportes/ReportesPage.test.tsx
git commit -m "test: nombre de proyecto ficticio en la prueba de reportes" -m "Reemplaza el nombre de una mina real por uno ficticio: ningún nombre real en lo versionado.

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_017MAgYcNauGp6cC3ekX1h7U"
```

---

### Tarea 2: Selector de proyectos, S06 sin proyectos, el 404 del proyecto y S49

**Files:**

- Modify: `src/portal/contexto/SelectorDeProyecto.tsx`, `src/portal/contexto/ProyectoLayout.tsx`
- Modify: `src/portal/proyectos/ProyectosPage.tsx`
- Modify: `src/portal/reportes/exportacion.tsx`, `src/portal/textos/reportes.ts`
- Test: `src/portal/contexto/contexto.test.tsx`, `src/portal/proyectos/ProyectosPage.test.tsx`,
  `src/portal/reportes/ReportesPage.test.tsx`

**Interfaces:**

- Consumes (Tarea 1): `useLeeTodosLosProyectos()` de `../contexto/proyectos`; `esIlegible` y `NoEsDeSusProyectos` de
  `../componentes/Estados`; los textos `contexto.todosLosProyectos` y `contexto.sinProyectos`.
- Produces: `reportes.todosLosProyectos` («Todos los proyectos» / «All projects»). Nada que otra tarea importe.

- [ ] **Step 1: Escribir las pruebas que fallan**

En `src/portal/contexto/contexto.test.tsx`, dentro de `describe('selector de proyecto', …)`, después de «sin READ sobre
project no se dibuja»:

```tsx
  it.each([
    ['la acción de leer todos', permisos({ project: ['READ', 'READ_ALL_PROJECTS'], project_assignment: ['READ'] })],
    ['ADMIN', ADMIN_PERMISOS]
  ])('con %s, el alcance global es «Todos los proyectos», por defecto, aunque no tenga asignaciones', async (_caso, p) => {
    fetch = mockFetch([
      { path: '/objects/project/records', body: pagina([P1, P2]) },
      { path: '/objects/project_assignment/records', body: pagina([]) }
    ])
    montar(<SelectorDeProyecto />, { ruta: '/stakeholders', permisos: p })
    const selector = await screen.findByRole('combobox', { name: 'Proyecto' })
    await waitFor(() =>
      expect(screen.getAllByRole('option').map((o) => o.textContent)).toEqual(['Todos los proyectos', 'PRJ-01 · Pukaqaqa', 'PRJ-02 · Qorihuasi'])
    )
    expect(selector).toHaveValue('')
  })

  it('con la acción de leer todos y una organización sin proyectos, sigue el selector y no el aviso de asignación', async () => {
    fetch = mockFetch([
      { path: '/objects/project/records', body: pagina([]) },
      { path: '/objects/project_assignment/records', body: pagina([]) }
    ])
    montar(<SelectorDeProyecto />, { ruta: '/stakeholders', permisos: permisos({ project: ['READ', 'READ_ALL_PROJECTS'] }) })
    await waitFor(() => expect(screen.getAllByRole('option').map((o) => o.textContent)).toEqual(['Todos los proyectos']))
    expect(screen.queryByText(/No tiene proyectos asignados/)).not.toBeInTheDocument()
  })

  it('sin proyectos que leer ni la acción de leer todos, dice que no tiene proyectos asignados en lugar de un selector vacío', async () => {
    fetch = mockFetch([
      { path: '/objects/project/records', body: pagina([]) },
      { path: '/objects/project_assignment/records', body: pagina([]) }
    ])
    montar(<SelectorDeProyecto />, { ruta: '/stakeholders', permisos: LEE_PROYECTOS })
    expect(await screen.findByText('No tiene proyectos asignados. Pida una asignación al administrador de proyectos.')).toBeInTheDocument()
    expect(screen.queryByRole('combobox', { name: 'Proyecto' })).not.toBeInTheDocument()
  })
```

En el mismo archivo, reemplazar el bloque que dejó la Tarea 1:

```tsx
  it.each([
    [404, 'El registro no existe o no es de sus proyectos.'],
    [403, 'Su cuenta no tiene permiso para esta acción. Pídalo a la administración del proyecto.']
  ])('un proyecto que responde %i dice el error y ofrece reintentar y volver a /projects', async (status, texto) => {
    fetch = mockFetch([
      { path: '/objects/project/records/p9', status, body: { title: 'Error', status, detail: 'x', errors: [] } },
      { path: '/objects/project_assignment/records', body: pagina([]) }
    ])
    montar(LAYOUT, { ruta: '/projects/p9/overview', permisos: LEE_PROYECTOS })
    expect(await screen.findByText(texto)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Reintentar' })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Ir a todos los proyectos' })).toHaveAttribute('href', '/projects')
    // sin datos del proyecto no hay barra, pestañas ni contenido
    expect(screen.queryByLabelText('Motivo')).not.toBeInTheDocument()
    expect(screen.queryByRole('link', { name: 'Resumen' })).not.toBeInTheDocument()
  })
```

por:

```tsx
  // con el alcance por proyecto, un proyecto ajeno responde 404 como uno inexistente (spec 4.4 §3.6): no se distingue y
  // reintentar no sirve
  it('un proyecto que responde 404 dice que no existe o no es de sus proyectos, sin reintento, y ofrece volver a /projects', async () => {
    fetch = mockFetch([
      { path: '/objects/project/records/p9', status: 404, body: { title: 'Error', status: 404, detail: 'Record p9 does not exist', errors: [] } },
      { path: '/objects/project_assignment/records', body: pagina([]) }
    ])
    montar(LAYOUT, { ruta: '/projects/p9/overview', permisos: LEE_PROYECTOS })
    expect(await screen.findByText('El registro no existe o no es de sus proyectos.')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Reintentar' })).not.toBeInTheDocument()
    expect(screen.queryByText(/does not exist/)).not.toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Ir a todos los proyectos' })).toHaveAttribute('href', '/projects')
    expect(screen.queryByLabelText('Motivo')).not.toBeInTheDocument()
    expect(screen.queryByRole('link', { name: 'Resumen' })).not.toBeInTheDocument()
  })

  it('un proyecto que responde 403 dice el error y ofrece reintentar y volver a /projects', async () => {
    fetch = mockFetch([
      { path: '/objects/project/records/p9', status: 403, body: { title: 'Error', status: 403, detail: 'x', errors: [] } },
      { path: '/objects/project_assignment/records', body: pagina([]) }
    ])
    montar(LAYOUT, { ruta: '/projects/p9/overview', permisos: LEE_PROYECTOS })
    expect(await screen.findByText('Su cuenta no tiene permiso para esta acción. Pídalo a la administración del proyecto.')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Reintentar' })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Ir a todos los proyectos' })).toHaveAttribute('href', '/projects')
    expect(screen.queryByLabelText('Motivo')).not.toBeInTheDocument()
    expect(screen.queryByRole('link', { name: 'Resumen' })).not.toBeInTheDocument()
  })
```

En `src/portal/proyectos/ProyectosPage.test.tsx` (ya importa `mockFetch`, `definicion`, `montar`, `pagina` y `permisos`),
agregar al final de `describe('S06 · lista de proyectos', …)`:

```tsx
  // con el alcance por proyecto, una cuenta sin asignaciones vigentes no lee ningún proyecto (spec 4.4 §3.12)
  const SIN_PROYECTOS = [
    { path: '/objects/project/records', body: pagina([]) },
    { path: '/objects/project_assignment/records', body: pagina([]) },
    { path: '/objects/exploration_campaign/records', body: pagina([]) },
    { path: '/metadata/objects/project', body: definicion('project', []) }
  ]

  it('sin proyectos que leer ni la acción de leer todos, dice que no tiene proyectos asignados', async () => {
    fetch = mockFetch(SIN_PROYECTOS)
    montar(<ProyectosPage />, { ruta: '/projects', permisos: LECTOR })
    expect(await screen.findByText('No tiene proyectos asignados. Pida una asignación al administrador de proyectos.')).toBeInTheDocument()
    expect(screen.queryByText(/Un proyecto nace con/)).not.toBeInTheDocument()
    expect(screen.queryByText('0 proyectos')).not.toBeInTheDocument()
  })

  it('con la acción de leer todos y ningún proyecto en la organización, no dice que le falte una asignación', async () => {
    fetch = mockFetch(SIN_PROYECTOS)
    montar(<ProyectosPage />, {
      ruta: '/projects',
      permisos: permisos({ project: ['READ', 'READ_ALL_PROJECTS'], project_assignment: ['READ'], exploration_campaign: ['READ'] })
    })
    expect(await screen.findByText('Nada coincide con los filtros')).toBeInTheDocument()
    expect(screen.queryByText(/No tiene proyectos asignados/)).not.toBeInTheDocument()
  })
```

En `src/portal/reportes/ReportesPage.test.tsx`, agregar al final de `describe('S49 · exportación del registro de grupos de
interés', …)`:

```tsx
  // con READ_ALL_PROJECTS el servidor no pide asignación para un proyecto (spec 4.4 §3.7): el alcance es «Todos los proyectos» y
  // la interfaz no frena un proyecto sin asignación
  it('con la acción de leer todos los proyectos, el alcance de la exportación es «Todos los proyectos» y un proyecto sin asignación se exporta', async () => {
    fetch = abrirPortal(
      '/reports',
      GERENCIA_SOCIAL,
      permisos({ project: ['READ', 'READ_ALL_PROJECTS'], stakeholder: ['READ', 'EXPORT'], export_log: ['READ'] }),
      rutas
    )
    red = espiarRed([{ method: 'POST', path: '/sgspe/exports/stakeholder-register', responder: () => archivoXlsx('registro.xlsx') }])
    const seccion = await screen.findByRole('region', { name: 'Registro de grupos de interés' })
    const proyecto = within(seccion).getByRole('combobox', { name: 'Proyecto' })
    expect(within(proyecto).getAllByRole('option')[0]).toHaveTextContent('Todos los proyectos')
    await waitFor(() => expect(within(proyecto).getAllByRole('option').map((o) => o.getAttribute('value'))).toContain('p2'))
    await userEvent.selectOptions(proyecto, 'p2')
    await userEvent.click(within(seccion).getByRole('button', { name: 'Exportar XLSX' }))
    await waitFor(() => expect(red!.a('POST', '/sgspe/exports/stakeholder-register')).toHaveLength(1))
    expect(red.a('POST', '/sgspe/exports/stakeholder-register')[0].cuerpo).toMatchObject({ filters: { project: ['p2'] } })
  })

  it('sin la acción, el alcance de la exportación sigue siendo «Todos mis proyectos»', async () => {
    fetch = abrirPortal('/reports', GERENCIA_SOCIAL, permisos({ project: ['READ'], stakeholder: ['READ', 'EXPORT'], export_log: ['READ'] }), rutas)
    const seccion = await screen.findByRole('region', { name: 'Registro de grupos de interés' })
    expect(within(within(seccion).getByRole('combobox', { name: 'Proyecto' })).getAllByRole('option')[0]).toHaveTextContent('Todos mis proyectos')
  })
```

- [ ] **Step 2: Correr las pruebas y ver que fallan**

Run: `yarn vitest run src/portal/contexto/contexto.test.tsx src/portal/proyectos/ProyectosPage.test.tsx src/portal/reportes/ReportesPage.test.tsx`
Expected: FAIL: las opciones dicen «Todos mis proyectos»; no aparece «No tiene proyectos asignados…»; el 404 del
proyecto ofrece «Reintentar».

- [ ] **Step 3: Implementar**

Reemplazar `src/portal/contexto/SelectorDeProyecto.tsx` por:

```tsx
import { useAuth } from '@wasichai/core'
import { useTranslation } from 'react-i18next'
import { useLocation, useNavigate } from 'react-router'
import { atributo } from '../datos/registros'
import { NS } from '../i18n'
import { useContextFilters } from '../shell/context'
import { useLeeTodosLosProyectos, useMisProyectos, useProyectos } from './proyectos'
import { rutaEquivalente } from './rutas'

// el selector de proyecto de la barra superior (ux-architecture.md §7.1): el alcance global o un proyecto. lista los proyectos
// que la cuenta lee, ya acotados por el servidor a sus asignaciones vigentes (alcance por proyecto, ADR-0029); los de la persona
// van primero. quien lee todos los proyectos (useLeeTodosLosProyectos, ADMIN incluido) ve el alcance global como «Todos los
// proyectos», y los demás como «Todos mis proyectos»; es el valor por defecto. sin proyectos que leer ni la acción, dice que no
// tiene asignaciones en lugar de un selector que solo lleva a listas vacías (spec 4.4 §3.12). elegir otro alcance monta la misma
// página con ese alcance (ASM-076). sin READ sobre project no se dibuja
export function SelectorDeProyecto() {
  const { can } = useAuth()
  return can('project', 'READ') ? <Selector /> : null
}

function Selector() {
  const { t } = useTranslation(NS)
  const { scope } = useContextFilters()
  const { pathname } = useLocation()
  const navegar = useNavigate()
  const proyectos = useProyectos()
  const mios = useMisProyectos()
  const leeTodo = useLeeTodosLosProyectos()
  const actual = scope.kind === 'project' ? scope.projectId : ''
  const lista = [...(proyectos.data ?? [])].sort((a, b) => Number(mios.has(b.id)) - Number(mios.has(a.id)))
  if (proyectos.isSuccess && lista.length === 0 && !leeTodo)
    return <p className="hidden max-w-64 text-sm text-sg-text-muted md:block">{t('contexto.sinProyectos')}</p>
  return (
    <label className="hidden items-center gap-2 text-sm md:flex">
      <span className="sr-only">{t('contexto.proyecto')}</span>
      <select
        aria-label={t('contexto.proyecto')}
        value={actual}
        onChange={(e) => navegar(rutaEquivalente(pathname, e.target.value || null))}
        className="h-8 max-w-64 rounded-sg-md border border-sg-border-strong bg-sg-surface px-2 text-sm"
      >
        <option value="">{leeTodo ? t('contexto.todosLosProyectos') : t('contexto.todos')}</option>
        {lista.map((p) => (
          <option key={p.id} value={p.id}>
            {String(atributo(p, 'code') ?? '')} · {String(atributo(p, 'name') ?? '')}
          </option>
        ))}
      </select>
    </label>
  )
}
```

En `src/portal/contexto/ProyectoLayout.tsx`, cambiar la importación de `../componentes/Estados`:

```tsx
import { ErrorDeCarga, esIlegible, NoEsDeSusProyectos } from '../componentes/Estados'
```

cambiar el comentario de cabecera:

```tsx
// el ProjectLayout de screens.md §2: dentro de un proyecto, la barra de contexto y la sub-navegación de Project 360; por
// debajo de lg pasa a una fila desplazable. quien no está asignado lee en solo lectura (alcance A, design-system.md §6.5), salvo ADMIN, que no necesita asignación
```

por:

```tsx
// el ProjectLayout de screens.md §2: dentro de un proyecto, la barra de contexto y la sub-navegación de Project 360; por
// debajo de lg pasa a una fila desplazable. con el alcance por proyecto (ADR-0029) solo llega aquí sin asignación quien lee
// todos los proyectos (READ_ALL_PROJECTS): lee en solo lectura (design-system.md §6.5), salvo ADMIN, que no necesita asignación.
// un proyecto que responde 404 no existe o no es de sus proyectos, sin distinguir (spec 4.4 §3.6)
```

y reemplazar:

```tsx
        <ErrorDeCarga error={proyecto.error} alReintentar={() => void proyecto.refetch()} />
```

por:

```tsx
        {esIlegible(proyecto.error) ? <NoEsDeSusProyectos /> : <ErrorDeCarga error={proyecto.error} alReintentar={() => void proyecto.refetch()} />}
```

En `src/portal/proyectos/ProyectosPage.tsx`, cambiar dos importaciones:

```tsx
import { Cargando, ErrorDeCarga, NoDisponible, Vacio } from '../componentes/Estados'
```

```tsx
import { useLeeTodosLosProyectos, useMisAsignaciones } from '../contexto/proyectos'
```

después de `const campanas = useTodos('exploration_campaign', …)` agregar:

```tsx
  const leeTodo = useLeeTodosLosProyectos()
```

después de `const fallo = …` agregar:

```tsx
  // sin asignaciones vigentes ni la acción de leer todos, el servidor no devuelve ningún proyecto (alcance por proyecto, spec 4.4
  // §3.12): se dice que no tiene proyectos asignados, no «un proyecto nace con Nuevo proyecto» a quien no puede crearlo
  const sinProyectos = proyectos.isSuccess && proyectos.data.length === 0 && !leeTodo && !can('project', 'CREATE')
```

cambiar la línea del conteo:

```tsx
          {cargando || fallo ? null : <p className="text-sm text-sg-text-muted">{t('proyectos.conteo', { count: filtrados.length })}</p>}
```

por:

```tsx
          {cargando || fallo || sinProyectos ? null : <p className="text-sm text-sg-text-muted">{t('proyectos.conteo', { count: filtrados.length })}</p>}
```

y en el cuerpo, reemplazar:

```tsx
      ) : filtrados.length === 0 ? (
```

por:

```tsx
      ) : sinProyectos ? (
        <Vacio titulo={t('contexto.sinProyectos')} />
      ) : filtrados.length === 0 ? (
```

En `src/portal/reportes/exportacion.tsx`, cambiar la importación:

```tsx
import { useLeeTodosLosProyectos, useProyectos } from '../contexto/proyectos'
```

cambiar el comentario de `useProyectoElegido`:

```tsx
// el proyecto de la URL, salvo que la persona elija otro; '' es «todos mis proyectos». lo elegido vale solo para el proyecto
```

por:

```tsx
// el proyecto de la URL, salvo que la persona elija otro; '' es «todos mis proyectos» (o «todos los proyectos» para quien los
// lee todos, plan 4.4b). lo elegido vale solo para el proyecto
```

y en `SelectorDeProyecto` de ese archivo, después de `const proyectos = useProyectos()`:

```tsx
  const leeTodo = useLeeTodosLosProyectos()
```

y reemplazar:

```tsx
          <option value="">{t('reportes.todosMisProyectos')}</option>
```

por:

```tsx
          <option value="">{leeTodo ? t('reportes.todosLosProyectos') : t('reportes.todosMisProyectos')}</option>
```

En `src/portal/textos/reportes.ts`, después de `todosMisProyectos: 'Todos mis proyectos',` agregar
`todosLosProyectos: 'Todos los proyectos',`, y después de `todosMisProyectos: 'All my projects',` agregar
`todosLosProyectos: 'All projects',`.

- [ ] **Step 4: Correr las pruebas y ver que pasan**

Run: `yarn vitest run src/portal/contexto/contexto.test.tsx src/portal/proyectos/ProyectosPage.test.tsx src/portal/reportes/ReportesPage.test.tsx src/portal/I18n.test.tsx src/portal/textosSinCodigos.test.ts src/boundaries.test.ts`
Expected: PASS (también las pruebas de antes: «lista primero los proyectos de la persona…» sigue con «Todos mis
proyectos»).

- [ ] **Step 5: Puerta completa y commit**

Run: `yarn format && yarn lint && yarn typecheck && yarn test && yarn build && yarn e2e`
Expected: todo en verde.

```bash
git add src/portal/contexto/SelectorDeProyecto.tsx src/portal/contexto/ProyectoLayout.tsx \
  src/portal/contexto/contexto.test.tsx src/portal/proyectos/ProyectosPage.tsx \
  src/portal/proyectos/ProyectosPage.test.tsx src/portal/reportes/exportacion.tsx \
  src/portal/reportes/ReportesPage.test.tsx src/portal/textos/reportes.ts
git commit -m "feat: selector con todos los proyectos para quien los lee todos" -m "Con READ_ALL_PROJECTS (o ADMIN) el alcance global del selector y de S49 es «Todos los
proyectos»; sin proyectos ni la acción, el selector y S06 dicen que no hay asignaciones. Un
proyecto que responde 404 dice que no existe o no es de sus proyectos, sin reintento.

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_017MAgYcNauGp6cC3ekX1h7U"
```

---

### Tarea 3: El 404 de las fichas

**Files:**

- Modify: `src/portal/acuerdos/FichaAcuerdoPage.tsx`, `src/portal/documentos/FichaEvidenciaPage.tsx`,
  `src/portal/grupos/FichaGrupoPage.tsx`, `src/portal/relacionamiento/FichaInteraccionPage.tsx`,
  `src/portal/relacionamiento/EditorDeActaPage.tsx`, `src/portal/tareas/AprobacionPage.tsx`,
  `src/portal/proyectos/AreaDeInfluenciaPage.tsx`
- Modify: `src/portal/textos/fichaQueja.ts`, `fichaConsulta.ts`, `fichaCompromiso.ts`, `fichaRiesgo.ts`,
  `incidentes.ts`, `alertas.ts`
- Create: `src/portal/fichasFueraDeAlcance.test.tsx`
- Test: `src/portal/atencion/FichaQuejaPage.test.tsx`, `src/portal/atencion/FichaConsultaPage.test.tsx`,
  `src/portal/compromisos/FichaCompromisoPage.test.tsx`, `src/portal/riesgos/FichaRiesgoPage.test.tsx`,
  `src/portal/riesgos/incidentes.test.tsx`, `src/portal/riesgos/alertas.test.tsx`

**Interfaces:**

- Consumes (Tarea 1): `NoEsDeSusProyectos` de `../componentes/Estados`; `comun.errores.404`.
- Produces: nada que otra tarea importe.

- [ ] **Step 1: Escribir las pruebas que fallan**

Crear `src/portal/fichasFueraDeAlcance.test.tsx`:

```tsx
import { screen } from '@testing-library/react'
import { mockFetch, type FetchMock, type MockRoute } from '@wasichai/testing'
import type { ComponentType } from 'react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { definicion, montar } from '../test/montar'
import { FichaAcuerdoPage } from './acuerdos/FichaAcuerdoPage'
import { FichaEvidenciaPage } from './documentos/FichaEvidenciaPage'
import { FichaGrupoPage } from './grupos/FichaGrupoPage'
import { AreaDeInfluenciaPage } from './proyectos/AreaDeInfluenciaPage'
import { EditorDeActaPage } from './relacionamiento/EditorDeActaPage'
import { FichaInteraccionPage } from './relacionamiento/FichaInteraccionPage'
import { AprobacionPage } from './tareas/AprobacionPage'

vi.mock('./graficos/Grafico', () => import('../test/graficoFalso'))
vi.mock('./mapa/Mapa', () => import('../test/mapaFalso'))

// el alcance de lectura por proyecto (ADR-0029, spec 4.4 §3.6 y §3.12): la ficha de un registro de otro proyecto responde 404
// como la de uno que no existe, y el portal no los distingue. sin «Reintentar», que no sirve, ni el detalle en inglés
let fetch: FetchMock | null = null
beforeEach(() => localStorage.clear())
afterEach(() => {
  fetch?.restore()
  fetch = null
})

const ILEGIBLE: MockRoute = {
  path: /^\/objects\/[a-z_]+\/records\/r9$/,
  status: 404,
  body: { title: 'Not Found', status: 404, detail: 'Record r9 does not exist', errors: [] }
}

const FICHAS: [string, ComponentType, string, string, MockRoute[]][] = [
  ['acuerdo', FichaAcuerdoPage, '/projects/p1/agreements/r9', '/projects/:projectId/agreements/:agreementId', []],
  ['evidencia', FichaEvidenciaPage, '/projects/p1/documents/r9', '/projects/:projectId/documents/:documentId', []],
  ['grupo de interés', FichaGrupoPage, '/projects/p1/stakeholders/r9', '/projects/:projectId/stakeholders/:stakeholderId', []],
  ['interacción', FichaInteraccionPage, '/projects/p1/engagements/r9', '/projects/:projectId/engagements/:engagementId', []],
  [
    'acta',
    EditorDeActaPage,
    '/projects/p1/engagements/r9/minutes',
    '/projects/:projectId/engagements/:engagementId/minutes',
    [{ path: '/metadata/objects/engagement', body: definicion('engagement', ['title']) }]
  ],
  ['aprobación', AprobacionPage, '/tasks/approvals/commitment/r9', '/tasks/approvals/:object/:recordId', []],
  ['área de influencia', AreaDeInfluenciaPage, '/projects/p1/areas/r9', '/projects/:projectId/areas/:areaId', []]
]

describe('fichas de un registro que la cuenta no lee', () => {
  it.each(FICHAS)('la ficha de %s que responde 404 dice que no existe o no es de sus proyectos, sin reintento', async (_ficha, Ficha, ruta, camino, extra) => {
    fetch = mockFetch([...extra, ILEGIBLE])
    montar(<Ficha />, { ruta, camino })
    expect(await screen.findByText('El registro no existe o no es de sus proyectos.')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Reintentar' })).not.toBeInTheDocument()
    expect(screen.queryByText(/does not exist/)).not.toBeInTheDocument()
  })
})
```

Cambiar las aserciones de los textos que ya existían:

- `src/portal/atencion/FichaQuejaPage.test.tsx`: `'Esta queja no existe o es confidencial y su cuenta no la lee.'` →
  `'Esta queja no existe, no es de sus proyectos o es confidencial y su cuenta no la lee.'`
- `src/portal/atencion/FichaConsultaPage.test.tsx`: `'Esta consulta no existe o es confidencial y su cuenta no la
  lee.'` → `'Esta consulta no existe, no es de sus proyectos o es confidencial y su cuenta no la lee.'`
- `src/portal/compromisos/FichaCompromisoPage.test.tsx`: `'Este compromiso no existe o no es de su organización.'` →
  `'Este compromiso no existe o no es de sus proyectos.'`
- `src/portal/riesgos/FichaRiesgoPage.test.tsx`: `'Este riesgo no existe o no es de su organización.'` →
  `'Este riesgo no existe o no es de sus proyectos.'`
- `src/portal/riesgos/incidentes.test.tsx`: `'Este incidente no existe o no es de su organización.'` →
  `'Este incidente no existe o no es de sus proyectos.'`
- `src/portal/riesgos/alertas.test.tsx`: `'Esta alerta no existe o no es de su organización.'` →
  `'Esta alerta no existe o no es de sus proyectos.'`

- [ ] **Step 2: Correr las pruebas y ver que fallan**

Run: `yarn vitest run src/portal/fichasFueraDeAlcance.test.tsx src/portal/atencion/FichaQuejaPage.test.tsx src/portal/atencion/FichaConsultaPage.test.tsx src/portal/compromisos/FichaCompromisoPage.test.tsx src/portal/riesgos/FichaRiesgoPage.test.tsx src/portal/riesgos/incidentes.test.tsx src/portal/riesgos/alertas.test.tsx`
Expected: FAIL: las siete fichas dicen el error con «Reintentar»; las seis con texto propio dicen el texto viejo.

- [ ] **Step 3: Implementar**

En cada una de las siete páginas, agregar `NoEsDeSusProyectos` a la importación que ya trae `Consulta` de
`'../componentes/Estados'` y pasar `noExiste={<NoEsDeSusProyectos />}` a la `Consulta` de su registro:

- `src/portal/acuerdos/FichaAcuerdoPage.tsx`:
  `return <Consulta consulta={acuerdo} noExiste={<NoEsDeSusProyectos />}>{(a) => <Ficha key={a.id} acuerdo={a} />}</Consulta>`
- `src/portal/documentos/FichaEvidenciaPage.tsx`:
  `return <Consulta consulta={evidencia} noExiste={<NoEsDeSusProyectos />}>{(e) => <Ficha evidencia={e} />}</Consulta>`
- `src/portal/grupos/FichaGrupoPage.tsx`:
  `return <Consulta consulta={grupo} noExiste={<NoEsDeSusProyectos />}>{(g) => <Ficha grupo={g} />}</Consulta>`
- `src/portal/relacionamiento/FichaInteraccionPage.tsx`:
  `return <Consulta consulta={interaccion} noExiste={<NoEsDeSusProyectos />}>{(i) => <Ficha interaccion={i} />}</Consulta>`
- `src/portal/relacionamiento/EditorDeActaPage.tsx`:
  `return <Consulta consulta={interaccion} noExiste={<NoEsDeSusProyectos />}>{(i) => <Editor key={i.id} interaccion={i} definicion={definicion.data} />}</Consulta>`
- `src/portal/tareas/AprobacionPage.tsx`:
  `return <Consulta consulta={compromiso} noExiste={<NoEsDeSusProyectos />}>{(c) => <Decision key={c.id} compromiso={c} />}</Consulta>`
- `src/portal/proyectos/AreaDeInfluenciaPage.tsx`:
  `return <Consulta consulta={area} noExiste={<NoEsDeSusProyectos />}>{(a) => <Area area={a} refrescando={area.isFetching} />}</Consulta>`

Encima de cada uno de esos `return`, el comentario:

```tsx
  // un 404 es un registro que no existe o que no es de sus proyectos (alcance por proyecto, spec 4.4 §3.6): no se distingue
```

Cambiar los textos (español e inglés):

| Archivo | Clave | Español | Inglés |
|---|---|---|---|
| `textos/fichaQueja.ts` | `noExiste` | `Esta queja no existe, no es de sus proyectos o es confidencial y su cuenta no la lee.` | `This grievance does not exist, is not in your projects, or is confidential and your account cannot read it.` |
| `textos/fichaConsulta.ts` | `consulta.noExiste` | `Esta consulta no existe, no es de sus proyectos o es confidencial y su cuenta no la lee.` | `This inquiry does not exist, is not in your projects, or is confidential and your account cannot read it.` |
| `textos/fichaConsulta.ts` | `solicitud.noExiste` | `Esta solicitud no existe, no es de sus proyectos o es confidencial y su cuenta no la lee.` | `This request does not exist, is not in your projects, or is confidential and your account cannot read it.` |
| `textos/fichaCompromiso.ts` | `noExiste` | `Este compromiso no existe o no es de sus proyectos.` | `This commitment does not exist or is not in your projects.` |
| `textos/fichaRiesgo.ts` | `noExiste` | `Este riesgo no existe o no es de sus proyectos.` | `This risk does not exist or is not in your projects.` |
| `textos/incidentes.ts` | `ficha.noExiste` | `Este incidente no existe o no es de sus proyectos.` | `This incident does not exist or is not in your projects.` |
| `textos/alertas.ts` | `ficha.noExiste` | `Esta alerta no existe o no es de sus proyectos.` | `This alert does not exist or is not in your projects.` |

Los textos viejos que se reemplazan son, en el mismo orden: `'Esta queja no existe o es confidencial y su cuenta no la
lee.'` / `'This grievance does not exist, or it is confidential and your account cannot read it.'`; `'Esta consulta no
existe o es confidencial y su cuenta no la lee.'` / `'This inquiry does not exist, or it is confidential and your account
cannot read it.'`; `'Esta solicitud no existe o es confidencial y su cuenta no la lee.'` / `'This request does not
exist, or it is confidential and your account cannot read it.'`; y en los cuatro restantes `… no es de su
organización.'` / `… is not from your organization.'`.

- [ ] **Step 4: Correr las pruebas y ver que pasan**

Run: `yarn vitest run src/portal/fichasFueraDeAlcance.test.tsx src/portal/atencion src/portal/compromisos src/portal/riesgos src/portal/acuerdos src/portal/documentos src/portal/grupos src/portal/relacionamiento src/portal/tareas src/portal/proyectos src/portal/I18n.test.tsx src/portal/textosSinCodigos.test.ts`
Expected: PASS.

- [ ] **Step 5: Puerta completa y commit**

Run: `yarn format && yarn lint && yarn typecheck && yarn test && yarn build && yarn e2e`
Expected: todo en verde.

```bash
git add src/portal/fichasFueraDeAlcance.test.tsx src/portal/acuerdos/FichaAcuerdoPage.tsx \
  src/portal/documentos/FichaEvidenciaPage.tsx src/portal/grupos/FichaGrupoPage.tsx \
  src/portal/relacionamiento/FichaInteraccionPage.tsx src/portal/relacionamiento/EditorDeActaPage.tsx \
  src/portal/tareas/AprobacionPage.tsx src/portal/proyectos/AreaDeInfluenciaPage.tsx \
  src/portal/textos/fichaQueja.ts src/portal/textos/fichaConsulta.ts src/portal/textos/fichaCompromiso.ts \
  src/portal/textos/fichaRiesgo.ts src/portal/textos/incidentes.ts src/portal/textos/alertas.ts \
  src/portal/atencion/FichaQuejaPage.test.tsx src/portal/atencion/FichaConsultaPage.test.tsx \
  src/portal/compromisos/FichaCompromisoPage.test.tsx src/portal/riesgos/FichaRiesgoPage.test.tsx \
  src/portal/riesgos/incidentes.test.tsx src/portal/riesgos/alertas.test.tsx
git commit -m "feat: el 404 de una ficha no distingue si el registro es de otro proyecto" -m "Las fichas de acuerdo, evidencia, grupo de interés, interacción, acta, aprobación y área de
influencia dicen que el registro no existe o no es de sus proyectos, sin reintento; las de QCS,
compromiso, riesgo, incidente y alerta lo dicen con su propio texto.

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_017MAgYcNauGp6cC3ekX1h7U"
```

---

### Tarea 4: «Registro reservado» para todo vínculo que responde 404

**Files:**

- Modify: `src/portal/componentes/RegistroVinculado.tsx`, `src/portal/avisos/AbrirRegistroPage.tsx`,
  `src/portal/tareas/AprobacionesPage.tsx`
- Modify: `src/portal/textos/piezas.ts`, `src/portal/textos/avisos.ts`
- Test: `src/portal/componentes/componentesF4.test.tsx`, `src/portal/avisos/avisos.test.tsx`,
  `src/portal/tareas/aprobaciones.test.tsx`

**Interfaces:**

- Consumes (Tarea 1): `esIlegible`, `esProhibido`, `Reservado` y `NoDisponible` de `componentes/Estados.tsx`; la ayuda
  general `comun.registroReservadoAyuda`.
- Produces: nada que otra tarea importe. Quita las claves `piezas.vinculado.noExiste` y `avisos.abrirRegistro.noExiste`.

- [ ] **Step 1: Escribir las pruebas que fallan**

En `src/portal/componentes/componentesF4.test.tsx`, reemplazar el `it('otro objeto que ya no existe lo dice; uno que la
cuenta no lee es «No disponible»; sin id no dibuja nada', …)` entero por:

```tsx
  // con el alcance por proyecto (ADR-0029) un 404 es también un registro de otro proyecto, y no se distingue de uno borrado
  it('cualquier destino que responde 404 es «Registro reservado»; uno sin permiso es «No disponible»; sin id no dibuja nada', async () => {
    fetch = mockFetch([
      { path: '/objects/incident/records/n1', ...noEncontrado },
      { path: '/objects/social_risk/records/k1', status: 403, body: { title: 'Forbidden', status: 403 } }
    ])
    montar(
      <>
        <RegistroVinculado objeto="incident" id="n1" />
        <RegistroVinculado objeto="social_risk" id="k1" />
        <RegistroVinculado objeto="grievance" id={null} />
      </>
    )
    expect(await screen.findByText('Registro reservado')).toBeInTheDocument()
    expect(screen.getByText('Registro reservado')).toHaveAccessibleDescription(/Su cuenta no lee este registro/)
    expect(await screen.findByText('No disponible')).toBeInTheDocument()
    expect(screen.queryByRole('link')).not.toBeInTheDocument()
    expect(screen.queryByText('El registro vinculado ya no existe.')).not.toBeInTheDocument()
  })
```

En `src/portal/avisos/avisos.test.tsx`, dentro de `describe('/r/:object/:recordId', …)`, reemplazar el `it('un registro
que ya no existe (404) lo dice, sin reintento, y no salta', …)` por:

```tsx
  // la bandeja no se filtra por proyecto (spec 4.4 §3.8): un aviso viejo lleva a un registro que ya no se lee
  it('un registro que la cuenta no lee o que ya no existe (404) es «Registro reservado», sin reintento, y no salta', async () => {
    fetch = mockFetch([{ path: '/objects/commitment/records/c1', status: 404, body: { title: 'Not Found', status: 404 } }])
    montar(<AbrirRegistroPage />, { ruta: '/r/commitment/c1', camino: '/r/:object/:recordId' })
    expect(await screen.findByText('Registro reservado')).toBeInTheDocument()
    expect(screen.getByText(/Su cuenta no lee este registro/)).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Reintentar' })).not.toBeInTheDocument()
    expect(ubicacion.actual).toBe('/r/commitment/c1')
  })
```

y en el `it('una QCS confidencial que la cuenta no lee (404) es «Registro reservado», sin datos, y no salta', …)` quitar
la línea:

```tsx
    expect(screen.queryByText('Este registro ya no existe o no es de su organización.')).not.toBeInTheDocument()
```

En `src/portal/tareas/aprobaciones.test.tsx`, después de `it('la queja de un aviso que responde 404 es «Registro
reservado», no un fallo de lectura', …)`:

```tsx
  // con el alcance por proyecto (ADR-0029) un compromiso de un proyecto que la cuenta dejó de leer también responde 404
  it('un compromiso de un aviso que responde 404 también es «Registro reservado», sin enlace ni monto', async () => {
    fetch = mockFetch(
      rutas([
        {
          path: '/auth/me/notifications',
          body: { content: [aviso('n9', 'commitment', 'c9')], page: 0, size: 200, totalElements: 1, totalPages: 1 }
        },
        { path: '/objects/commitment/records/c9', status: 404, body: { title: 'Not Found', status: 404, detail: 'x' } }
      ])
    )
    montar(<AprobacionesPage />, {
      ruta: '/tasks/approvals',
      camino: '/tasks/approvals',
      usuario: APROBADORA,
      permisos: permisos({ commitment: ['READ', 'UPDATE', 'APPROVE'] })
    })
    const tabla = await screen.findByRole('table', { name: 'Por aprobar' })
    expect(await within(tabla).findByText('Registro reservado')).toBeInTheDocument()
    const fila = within(tabla).getByText('Registro reservado').closest('tr')!
    expect(within(fila).queryByRole('link')).not.toBeInTheDocument()
    expect(within(fila).queryByRole('button', { name: 'Reintentar' })).not.toBeInTheDocument()
  })
```

- [ ] **Step 2: Correr las pruebas y ver que fallan**

Run: `yarn vitest run src/portal/componentes/componentesF4.test.tsx src/portal/avisos/avisos.test.tsx src/portal/tareas/aprobaciones.test.tsx`
Expected: FAIL: el incidente dice «El registro vinculado ya no existe.», el compromiso de `/r/…` dice el texto de
«ya no existe» y el de la bandeja ofrece «Reintentar».

- [ ] **Step 3: Implementar**

Reemplazar `src/portal/componentes/RegistroVinculado.tsx` por:

```tsx
import { useTranslation } from 'react-i18next'
import { Link } from 'react-router'
import { rutaDeFicha, rutaParaAbrir } from '../datos/fichas'
import { texto, useRegistro, type Registro } from '../datos/registros'
import { NS } from '../i18n'
import { Cargando, esIlegible, esProhibido, NoDisponible, Reservado } from './Estados'
import { FalloDeLectura } from './LecturaConEstados'

// el nombre de un registro vinculado: su código y su título, nombre o titular; sin ninguno, un texto (nunca su id)
export function useRotuloDeRegistro(): (registro: Registro) => string {
  const { t } = useTranslation(NS)
  return (r) => [texto(r, 'code'), texto(r, 'title') || texto(r, 'name') || texto(r, 'headline')].filter(Boolean).join(' · ') || t('piezas.vinculado.sinNombre')
}

// un vínculo a otro registro (la queja de origen de un compromiso, el origen de una tarea, el riesgo de una alerta): lo lee como
// quien mira y enlaza su ficha. un destino que responde 404 es «Registro reservado», sin enlace ni datos: wasichai responde igual
// por un registro de un proyecto en que la cuenta no está asignada (alcance por proyecto, ADR-0029), por una QCS confidencial que
// no lee (ADR-0028) y por uno borrado, y el portal no los distingue (spec 4.4 §3.6 y §3.12). un 403, «No disponible»; otro fallo,
// con su reintento. sin id no dibuja nada
export function RegistroVinculado({ objeto, id, rotulo }: { objeto: string; id: string | null | undefined; rotulo?: (registro: Registro) => string }) {
  const registro = useRegistro(objeto, id ?? undefined)
  const rotuloPorDefecto = useRotuloDeRegistro()
  if (!id) return null
  if (registro.isPending) return <Cargando compacto />
  if (registro.isError && !registro.data) {
    if (esIlegible(registro.error)) return <Reservado />
    if (esProhibido(registro.error)) return <NoDisponible enLista />
    return <FalloDeLectura error={registro.error} alReintentar={() => void registro.refetch()} />
  }
  const r = registro.data as Registro
  return (
    <Link to={rutaDeFicha(objeto, r) ?? rutaParaAbrir(objeto, r.id)} className="text-sg-primary-text underline">
      {(rotulo ?? rotuloPorDefecto)(r)}
    </Link>
  )
}

// el enlace a la ficha de un registro ya leído; sin ruta (un registro de proyecto que llega sin proyecto), su nombre sin enlace,
// nunca un enlace a «#»
export function EnlaceDeFicha({ objeto, registro, children }: { objeto: string; registro: Registro; children: React.ReactNode }) {
  const ruta = rutaDeFicha(objeto, registro)
  return ruta ? (
    <Link to={ruta} className="text-sg-primary-text underline">
      {children}
    </Link>
  ) : (
    <span>{children}</span>
  )
}
```

Reemplazar `src/portal/avisos/AbrirRegistroPage.tsx` por:

```tsx
import { useTranslation } from 'react-i18next'
import { Navigate, useParams } from 'react-router'
import { Cargando, esIlegible, Vacio } from '../componentes/Estados'
import { FalloDeLectura } from '../componentes/LecturaConEstados'
import { rutaDeFicha } from '../datos/fichas'
import { useRegistro } from '../datos/registros'
import { NS } from '../i18n'

// /r/:object/:recordId: el destino de un aviso o de una tarea que solo trae el objeto y el id. lee el registro como quien
// mira y salta a su ficha bajo su proyecto (ux-architecture.md §6.3), reemplazando esta entrada del historial: volver atrás no
// rebota aquí. un 404 es «Registro reservado», sin datos ni «Reintentar»: la bandeja no se filtra por proyecto (spec 4.4 §3.8),
// así que un aviso viejo puede llevar a un registro de un proyecto que la cuenta ya no lee, a una QCS confidencial o a uno
// borrado, y el portal no los distingue. un 403 es «no disponible». un objeto sin ficha en el portal (o un registro de proyecto
// sin proyecto) da null en rutaDeFicha: es «No disponible» y no salta a ninguna parte
export function AbrirRegistroPage() {
  const { t } = useTranslation(NS)
  const { object = '', recordId = '' } = useParams()
  const registro = useRegistro(object, recordId)
  if (registro.isPending) return <Cargando etiqueta={t('avisos.abrirRegistro.abriendo')} />
  if (registro.isError && !registro.data && esIlegible(registro.error))
    return <Vacio titulo={t('comun.registroReservado')}>{t('comun.registroReservadoAyuda')}</Vacio>
  if (registro.isError && !registro.data) return <FalloDeLectura error={registro.error} alReintentar={() => void registro.refetch()} />
  const ruta = rutaDeFicha(object, registro.data)
  return ruta ? <Navigate to={ruta} replace /> : <Vacio titulo={t('comun.noDisponible')}>{t('avisos.abrirRegistro.sinFicha')}</Vacio>
}
```

En `src/portal/tareas/AprobacionesPage.tsx`: quitar las importaciones `import { ApiError } from '@wasichai/core'` y
`import { esQcs } from '../datos/atencion'`; cambiar `import { Cargando, Reservado, Vacio } from '../componentes/Estados'`
por `import { Cargando, esIlegible, Reservado, Vacio } from '../componentes/Estados'`; y reemplazar:

```tsx
  // nadie borra una QCS (security.md §7.6): su 404 es una confidencial que la cuenta no lee (§9.6). la fila no dice qué es ni de qué
  // trata, y no ofrece una ficha que solo repetiría «Registro reservado»
  const reservada = esQcs(objeto) && registro.error instanceof ApiError && registro.error.status === 404
```

por:

```tsx
  // un 404 es un registro que la cuenta no lee: de un proyecto en que no está asignada (ADR-0029), una QCS confidencial (§9.6) o uno
  // borrado, sin distinguir. la fila no dice qué es ni de qué trata, y no ofrece una ficha que solo repetiría «Registro reservado»
  const reservada = esIlegible(registro.error)
```

(Si `typecheck` dice que `ApiError` sigue usado en otra línea del archivo, se deja esa importación.)

En `src/portal/textos/piezas.ts`, quitar `noExiste: 'El registro vinculado ya no existe.',` de `ES_PIEZAS.vinculado` y
`noExiste: 'The linked record no longer exists.',` de `EN_PIEZAS.vinculado`.

En `src/portal/textos/avisos.ts`, en `abrirRegistro` de los dos idiomas, quitar la clave `noExiste` y la coma que queda
colgando en `sinFicha`:

```ts
  abrirRegistro: {
    abriendo: 'Abriendo el registro…',
    sinFicha: 'Este registro no tiene una ficha en el portal todavía.'
  }
```

```ts
  abrirRegistro: {
    abriendo: 'Opening the record…',
    sinFicha: 'This record has no page in the portal yet.'
  }
```

- [ ] **Step 4: Correr las pruebas y ver que pasan**

Run: `yarn vitest run src/portal/componentes src/portal/avisos src/portal/tareas src/portal/atencion src/portal/compromisos src/portal/riesgos src/portal/I18n.test.tsx`
Expected: PASS (también «una QCS que la cuenta no lee (404) es «Registro reservado»…» y las fichas que usan
`RegistroVinculado`).

- [ ] **Step 5: Puerta completa y commit**

Run: `yarn format && yarn lint && yarn typecheck && yarn test && yarn build && yarn e2e`
Expected: todo en verde (el recorrido «la tarea de una queja confidencial…» de `e2e/f4.spec.ts` sigue igual).

```bash
git add src/portal/componentes/RegistroVinculado.tsx src/portal/avisos/AbrirRegistroPage.tsx \
  src/portal/tareas/AprobacionesPage.tsx src/portal/textos/piezas.ts src/portal/textos/avisos.ts \
  src/portal/componentes/componentesF4.test.tsx src/portal/avisos/avisos.test.tsx \
  src/portal/tareas/aprobaciones.test.tsx
git commit -m "feat: «Registro reservado» para todo vínculo que responde 404" -m "Un vínculo, un aviso o una aprobación que llevan a un registro que la cuenta no lee (de otro
proyecto, una QCS confidencial o uno borrado) dicen «Registro reservado», sin enlace ni datos y
sin distinguir el caso.

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_017MAgYcNauGp6cC3ekX1h7U"
```

---

### Tarea 5: El 400 de una relación ilegible, en su campo

**Files:**

- Modify: `src/portal/componentes/errores.ts`
- Modify: `src/portal/documentos/DocumentosEmitidos.tsx` (solo el comentario)
- Test: `src/portal/componentes/componentes.test.tsx`, `src/portal/componentes/FormularioDeRegistro.test.tsx`,
  `src/portal/atencion/NuevaQcsPage.test.tsx`

**Interfaces:**

- Consumes (Tarea 1): `comun.codigosEnCampo.registroNoLegible`.
- Produces: `useMensajeDeError` y `useErroresDeCampo` (`componentes/errores.ts`, firmas sin cambio) dicen ese texto por
  la violación `{ field, message: 'no record with this id' }` de wasichai.

- [ ] **Step 1: Escribir las pruebas que fallan**

En `src/portal/componentes/componentes.test.tsx`, dentro de `describe('errores con varias violaciones', …)`, al final:

```tsx
  // wasichai 0.6.0 (RelationTargets) responde así una RELATION a un registro que la cuenta no lee: con el alcance por proyecto,
  // también uno de otro proyecto (spec 4.4 §3.6). el texto en inglés y el detail nunca llegan a la pantalla
  it('el 400 de una relación a un registro que no se lee dice su texto, no el inglés de wasichai', async () => {
    montar(<ErrorDeCarga error={new ErrorDeSgspe(400, "Invalid value for 'project'", [{ field: 'project', message: 'no record with this id' }], null)} />)
    expect(await screen.findByRole('alert')).toHaveTextContent('El registro elegido no existe o es de otro proyecto.')
    expect(screen.queryByText(/no record with this id|Invalid value/)).not.toBeInTheDocument()
  })
```

En `src/portal/componentes/FormularioDeRegistro.test.tsx`, después del `it('las relaciones fijas viajan ocultas y un
cambio lleva la versión leída', …)`:

```tsx
  it('un 400 sobre una relación oculta se dice con la etiqueta del campo y el texto propio, no el inglés de wasichai', async () => {
    fetch = mockFetch([
      {
        path: '/metadata/objects/stakeholder',
        body: definicion('stakeholder', [
          ['name', { label: 'Nombre' }],
          ['project', { label: 'Proyecto', type: 'RELATION', relationTarget: 'project' }]
        ])
      }
    ])
    red = espiarRed([
      {
        method: 'POST',
        path: '/objects/stakeholder/records',
        responder: () => problema(400, "Invalid value for 'project'", [{ field: 'project', message: 'no record with this id' }])
      }
    ])
    // las relaciones son UUID: Zod las valida aunque viajen ocultas
    montar(<FormularioDeRegistro objeto="stakeholder" fijos={{ project: '6f1c2d3e-4b5a-4c6d-8e9f-0a1b2c3d4e5f' }} />)
    await userEvent.type(await screen.findByLabelText('Nombre'), 'Ronda campesina')
    await userEvent.click(screen.getByRole('button', { name: 'Guardar' }))
    expect(await screen.findByText('Proyecto: El registro elegido no existe o es de otro proyecto.')).toBeInTheDocument()
    expect(screen.queryByText(/no record with this id|Invalid value/)).not.toBeInTheDocument()
  })
```

En `src/portal/atencion/NuevaQcsPage.test.tsx`, dentro de `describe('S37 · registrar QCS', …)`, después de «no manda
nada sin código, descripción ni motivo…»:

```tsx
  // el selector lista solo los grupos que la cuenta lee; si uno dejó de leerse antes de guardar, wasichai responde 400 sobre la
  // relación (spec 4.4 §3.12)
  it('el grupo de interés que el servidor no deja vincular (400 sobre la relación) se dice junto a su campo, sin el texto en inglés', async () => {
    fetch = mockFetch(rutas())
    red = espiarRed([
      {
        method: 'POST',
        path: '/objects/grievance/records',
        responder: () => problema(400, "Invalid value for 'stakeholder'", [{ field: 'stakeholder', message: 'no record with this id' }])
      }
    ])
    abrir()
    await llenar('Q-012')
    const grupo = screen.getByLabelText('Grupo de interés')
    await waitFor(() => expect(grupo.querySelector('option[value="s1"]')).not.toBeNull())
    await userEvent.selectOptions(grupo, 's1')
    await userEvent.click(screen.getByRole('button', { name: 'Registrar queja' }))
    await waitFor(() => expect(screen.getByLabelText('Grupo de interés')).toHaveAccessibleDescription('El registro elegido no existe o es de otro proyecto.'))
    expect(screen.queryByText(/no record with this id|Invalid value/)).not.toBeInTheDocument()
  })
```

- [ ] **Step 2: Correr las pruebas y ver que fallan**

Run: `yarn vitest run src/portal/componentes/componentes.test.tsx src/portal/componentes/FormularioDeRegistro.test.tsx src/portal/atencion/NuevaQcsPage.test.tsx`
Expected: FAIL: la pantalla dice «Invalid value for 'project'» o «no record with this id».

- [ ] **Step 3: Implementar**

En `src/portal/componentes/errores.ts`, después de `const campoDeComando = …`, agregar:

```ts
// la violación que wasichai 0.6.0 pone sobre un campo RELATION cuyo destino la cuenta no lee o no existe (RelationTargets, D30 de
// wasichai): con el alcance por proyecto (ADR-0029) es también un registro de otro proyecto. llega como texto libre en inglés, sin
// código, así que se reconoce por su texto exacto y se dice con comun.codigosEnCampo.registroNoLegible, en su campo
const SIN_REGISTRO = 'no record with this id'
```

En `useMensajeDeError`, reemplazar el comienzo del bucle:

```ts
    for (const v of error.violations) {
      if (!esCodigo(v.message)) continue
```

por:

```ts
    for (const v of error.violations) {
      if (v.message === SIN_REGISTRO) {
        dichos.add(t('comun.codigosEnCampo.registroNoLegible'))
        continue
      }
      if (!esCodigo(v.message)) continue
```

En `useErroresDeCampo`, reemplazar:

```ts
      const texto = !esCodigo(v.message)
        ? (detalleVisible(v.message) ?? t(claveDeEstado(error.status)))
        : enCampo
          ? t(enCampo)
          : CODIGOS.has(v.message)
            ? t(`comun.codigos.${v.message}`)
            : (detalleVisible(error.message) ?? t(claveDeEstado(error.status)))
```

por:

```ts
      const texto =
        v.message === SIN_REGISTRO
          ? t('comun.codigosEnCampo.registroNoLegible')
          : !esCodigo(v.message)
            ? (detalleVisible(v.message) ?? t(claveDeEstado(error.status)))
            : enCampo
              ? t(enCampo)
              : CODIGOS.has(v.message)
                ? t(`comun.codigos.${v.message}`)
                : (detalleVisible(error.message) ?? t(claveDeEstado(error.status)))
```

En `src/portal/documentos/DocumentosEmitidos.tsx`, reemplazar en el comentario de cabecera:

```tsx
// restringidos (ASM-090). el servidor exige además (IssueDocumentFilter, P62) la asignación vigente al proyecto del registro
// y, sin EDIT_ANY, haberlo creado; la ficha no sabe quién lo creó, así que cada 403 (ACTION_REQUIRED, NOT_ASSIGNED,
// NOT_OWN_RECORD, OBJECT_NOT_READABLE) se dice con el texto propio de la emisión. escribe es la guarda de UI de quien abre la
```

por:

```tsx
// restringidos (ASM-090). el servidor exige además (IssueDocumentFilter, P62) la asignación vigente al proyecto del registro
// y, sin EDIT_ANY, haberlo creado; la ficha no sabe quién lo creó, así que cada 403 (ACTION_REQUIRED, NOT_ASSIGNED,
// NOT_OWN_RECORD, OBJECT_NOT_READABLE) se dice con el texto propio de la emisión. con el alcance por proyecto (ADR-0029) un
// registro que la cuenta no lee responde 404 al listar o emitir, como su ficha; NOT_ASSIGNED queda para quien lee todos los
// proyectos sin estar asignado (spec 4.4 §3.6). escribe es la guarda de UI de quien abre la
```

- [ ] **Step 4: Correr las pruebas y ver que pasan**

Run: `yarn vitest run src/portal/componentes src/portal/atencion src/portal/compromisos src/portal/documentos src/portal/proyectos src/portal/riesgos`
Expected: PASS.

- [ ] **Step 5: Puerta completa y commit**

Run: `yarn format && yarn lint && yarn typecheck && yarn test && yarn build && yarn e2e`
Expected: todo en verde.

```bash
git add src/portal/componentes/errores.ts src/portal/documentos/DocumentosEmitidos.tsx \
  src/portal/componentes/componentes.test.tsx src/portal/componentes/FormularioDeRegistro.test.tsx \
  src/portal/atencion/NuevaQcsPage.test.tsx
git commit -m "feat: el 400 de una relación a un registro ilegible se dice en su campo" -m "La violación «no record with this id» de wasichai sobre un campo RELATION se dice «El registro
elegido no existe o es de otro proyecto.» junto al campo o con su etiqueta, nunca en inglés.

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_017MAgYcNauGp6cC3ekX1h7U"
```

---

### Tarea 6: Recorridos del alcance en Playwright

**Files:**

- Create: `e2e/alcance.spec.ts`

**Interfaces:**

- Consumes: `prepararApi`, `pagina` y `registro` de `e2e/api.ts`; lo de las Tareas 2 y 4 ya mezclado.
- Produces: nada que otra tarea importe.

- [ ] **Step 1: Escribir los recorridos**

Crear `e2e/alcance.spec.ts`:

```ts
import { expect, test, type Page } from '@playwright/test'
import { pagina, prepararApi, registro } from './api'

// el alcance de lectura por proyecto en un navegador de verdad (plan 4.4b): el selector con «Todos los proyectos» para quien lee
// todos, el aviso de una cuenta sin proyectos y un aviso que lleva a un registro que la cuenta no lee. la API se simula como en
// f4.spec.ts: estas rutas se registran después de las comunes y les ganan
const A = registro('pa', { code: 'PRJ-A', name: 'Proyecto Ejemplo A' }, 'active')
const B = registro('pb', { code: 'PRJ-B', name: 'Proyecto Ejemplo B' }, 'active')

async function conAlcance(page: Page, objetos: Record<string, string[]>, proyectos: unknown[]) {
  const json = (body: unknown, status = 200) => ({ status, contentType: 'application/json', body: JSON.stringify(body) })
  await page.route('**/api/**', async (route) => {
    if (route.request().method() !== 'GET') return route.fallback()
    const ruta = new URL(route.request().url()).pathname.replace(/^\/api/, '')
    if (ruta === '/auth/me/permissions') return route.fulfill(json({ admin: false, objects: objetos }))
    if (ruta === '/objects/project/records') return route.fulfill(json(pagina(proyectos)))
    if (ruta === '/objects/project_assignment/records') return route.fulfill(json(pagina([])))
    if (ruta === '/objects/commitment/records/c9') return route.fulfill(json({ title: 'Not Found', status: 404 }, 404))
    return route.fallback()
  })
}

test.beforeEach(async ({ page }) => {
  await prepararApi(page)
})

test('quien lee todos los proyectos ve «Todos los proyectos» por defecto y todos los proyectos', async ({ page }) => {
  await conAlcance(page, { project: ['READ', 'READ_ALL_PROJECTS'], project_assignment: ['READ'] }, [A, B])
  await page.goto('/projects')
  const selector = page.getByRole('combobox', { name: 'Proyecto' })
  await expect(selector).toHaveValue('')
  await expect(selector.locator('option')).toHaveText(['Todos los proyectos', 'PRJ-A · Proyecto Ejemplo A', 'PRJ-B · Proyecto Ejemplo B'])
})

test('una cuenta sin proyectos asignados lo dice en lugar de un selector vacío', async ({ page }) => {
  await conAlcance(page, { project: ['READ'], project_assignment: ['READ'] }, [])
  await page.goto('/projects')
  await expect(page.getByText('No tiene proyectos asignados. Pida una asignación al administrador de proyectos.').first()).toBeVisible()
  await expect(page.getByRole('combobox', { name: 'Proyecto' })).toHaveCount(0)
})

test('un aviso que lleva a un registro que la cuenta no lee dice «Registro reservado» y no salta', async ({ page }) => {
  await conAlcance(page, { project: ['READ'], project_assignment: ['READ'], commitment: ['READ'] }, [A])
  await page.goto('/r/commitment/c9')
  await expect(page.getByText('Registro reservado')).toBeVisible()
  await expect(page.getByText(/Su cuenta no lee este registro/)).toBeVisible()
  await expect(page).toHaveURL(/\/r\/commitment\/c9$/)
})
```

- [ ] **Step 2: Correr los recorridos**

Run: `yarn e2e e2e/alcance.spec.ts`
Expected: 3 recorridos en verde. (Antes de las Tareas 2 y 4 los dos primeros y el tercero fallaban; aquí se escriben
sobre lo ya mezclado, como los de `f4.spec.ts`.)

- [ ] **Step 3: Puerta completa y commit**

Run: `yarn format && yarn lint && yarn typecheck && yarn test && yarn build && yarn e2e`
Expected: todo en verde.

```bash
git add e2e/alcance.spec.ts
git commit -m "test: recorridos del alcance por proyecto en Playwright" -m "El selector con «Todos los proyectos» para quien los lee todos, el aviso de una cuenta sin
proyectos y «Registro reservado» al abrir un aviso de un registro que la cuenta no lee.

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_017MAgYcNauGp6cC3ekX1h7U"
```

---

### Tarea 7: Cierre documental, push, PR, smoke del coordinador y cierre de la Release 1

**Files:**

- Modify: `CLAUDE.md`, `HISTORY.md`, `README.md` (en `sgspe-ui`)
- Modify, después del merge: `docs/roadmap.md` (en `sgspe`)

**Interfaces:**

- Consumes: todo lo anterior mezclado; 4.4a en `main` de `sgspe`.
- Produces: el PR de `sgspe-ui` y el de la fila «Estado» de F4 en `sgspe`.

- [ ] **Step 1: Escribir los cambios de `sgspe-ui`**

En `CLAUDE.md`, reemplazar:

```markdown
- La guarda de UI de una escritura con alcance de proyecto es `useEscribeEnProyecto` (`contexto/ProyectoLayout.tsx`): ADMIN
  la salta y los demás necesitan una asignación vigente. Las escrituras de `project_assignment` están exentas
  (security.md §9.2, ítem 5) y dependen solo de `can(...)`.
```

por:

```markdown
- La guarda de UI de una escritura con alcance de proyecto es `useEscribeEnProyecto` (`contexto/ProyectoLayout.tsx`): ADMIN
  la salta y los demás necesitan una asignación vigente. Las escrituras de `project_assignment` están exentas
  (security.md §9.2, ítem 5) y dependen solo de `can(...)`.
- La lectura la acota el servidor (alcance por proyecto, ADR-0029): el portal no filtra por proyecto lo que lee. Si la cuenta
  lee todos los proyectos lo dice solo `useLeeTodosLosProyectos` (`contexto/proyectos.ts`, `READ_ALL_PROJECTS` sobre
  `project`; ADMIN siempre), el único lugar que nombra la acción (`boundaries.test.ts`). Un `404` nunca distingue «no
  existe» de «no es de sus proyectos»: la ficha lo dice con `NoEsDeSusProyectos` y su `noExiste`
  (`componentes/Estados.tsx`).
```

y en el párrafo de `RegistroVinculado`, reemplazar el fragmento:

```markdown
una QCS confidencial que
  la cuenta no lee responde 404 y se dice «Registro reservado», sin enlace ni datos.
```

por:

```markdown
un destino que responde 404
  (de otro proyecto, una QCS confidencial o borrado; `esIlegible`) se dice «Registro reservado», sin enlace ni datos, y
  `/r/:object/:recordId` hace lo mismo. El `400` de wasichai sobre una relación («no record with this id») se dice en su
  campo con `comun.codigosEnCampo.registroNoLegible` (`componentes/errores.ts`).
```

El resto del párrafo («`EnlaceDeFicha` (mismo archivo) lleva a la ficha de un registro ya leído. Una cifra o una lista de
QCS con `confidentialHidden`…») queda igual.

En `HISTORY.md`, después de «La entrada más reciente va primero. El diseño y los ADR están en el repositorio `sgspe`.»,
agregar (con la fecha del día del commit y las cifras que diga `yarn test` y `yarn e2e` en ese momento):

```markdown
## AAAA-MM-DD — Alcance de lectura por proyecto (plan 4.4b)

- El servidor acota lo que cada cuenta lee a sus proyectos (plan 4.4a, ADR-0029); el portal no filtra, dice bien lo que
  recibe. `useLeeTodosLosProyectos` (`contexto/proyectos.ts`) dice si la cuenta tiene `READ_ALL_PROJECTS` sobre `project`
  (`AUDITOR`, `PROJECT_ADMIN`; ADMIN siempre) y es el único lugar que nombra la acción.
- Selector de proyectos: «Todos los proyectos» para quien los lee todos, «Todos mis proyectos» para los demás; sin
  proyectos ni la acción, el selector y S06 dicen que no hay asignaciones. S49 usa la misma etiqueta.
- Un `404` no distingue «no existe» de «no es de sus proyectos»: las fichas lo dicen con `NoEsDeSusProyectos` o su texto
  propio, sin «Reintentar»; un vínculo, un aviso (`/r/…`) o una aprobación dicen «Registro reservado» por cualquier objeto.
- El `400` de wasichai sobre una relación a un registro ilegible se dice en su campo, nunca en inglés.
- Pruebas: N archivos con M pruebas en Vitest y R recorridos en Playwright (3 nuevos en `e2e/alcance.spec.ts`).
```

En `README.md`, después del párrafo «**Pantallas de F4:** …», agregar:

```markdown
- **Alcance por proyecto (plan 4.4b):** el servidor acota la lectura a los proyectos de cada cuenta (ADR-0029). El selector
  ofrece «Todos los proyectos» a quien los lee todos (`useLeeTodosLosProyectos`) y avisa a una cuenta sin asignaciones; un
  registro que responde 404 no se distingue de uno inexistente (`NoEsDeSusProyectos` en una ficha, «Registro reservado» en
  un vínculo), y el 400 de una relación ilegible se dice en su campo.
```

- [ ] **Step 2: Puerta completa y commit**

Run: `yarn format && yarn lint && yarn typecheck && yarn test && yarn build && yarn e2e`
Expected: todo en verde; las cifras de `yarn test` y `yarn e2e` van en la entrada de HISTORY.md (reemplazan N, M y R, y
`AAAA-MM-DD` la fecha del día).

```bash
git add CLAUDE.md HISTORY.md README.md
git commit -m "docs: cierre documental del plan 4.4b" -m "CLAUDE.md, README y HISTORY.md dicen el alcance de lectura por proyecto en el portal: quién
lee todos los proyectos, el 404 que no distingue, «Registro reservado» general y el 400 de una
relación ilegible en su campo.

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_017MAgYcNauGp6cC3ekX1h7U"
```

- [ ] **Step 3: Push y PR**

Con la puerta completa en verde y 4.4a ya en `main` de `sgspe` (`gh pr list -R wasichai/sgspe --state merged --head
feat/fase-4-4a-alcance` lo muestra):

```bash
git push -u origin feat/fase-4-4b-alcance
gh pr create --base main --head feat/fase-4-4b-alcance --title "Plan 4.4b: alcance de lectura por proyecto en el portal" --body-file - <<'MSG'
## Qué trae

El portal del alcance de lectura por proyecto (plan 4.4b de `sgspe`,
`docs/superpowers/plans/2026-10-10-fase-4-4b-portal-alcance.md`; el backend es el plan 4.4a). El servidor acota lo que
cada cuenta lee; el portal dice bien lo que recibe:

- Selector de proyectos con «Todos los proyectos» para quien tiene `READ_ALL_PROJECTS` (y ADMIN); una cuenta sin
  asignaciones lo ve dicho en el selector y en S06. S49 usa la misma etiqueta.
- La ficha de un registro que responde 404 dice que no existe o no es de sus proyectos, sin distinguir y sin reintento.
- «Registro reservado» para todo vínculo, aviso o aprobación cuyo destino responde 404.
- El 400 de wasichai sobre una relación a un registro ilegible se dice en su campo, nunca en inglés.
- `NOT_ASSIGNED` se sigue mostrando como hoy para quien lee todo sin estar asignado.

## Pruebas

- `yarn test` y `yarn e2e` en verde (3 recorridos nuevos en `e2e/alcance.spec.ts`).
- `boundaries.test.ts`: ningún rol en `src/` y `READ_ALL_PROJECTS` solo en `contexto/proyectos.ts`.
- Smoke del coordinador contra el tenant de desarrollo: en un comentario de este PR.

🤖 Generated with [Claude Code](https://claude.com/claude-code)

https://claude.ai/code/session_017MAgYcNauGp6cC3ekX1h7U
MSG
```

Expected: la CI del PR en verde (formato, lint, tipos, pruebas, build y Playwright).

- [ ] **Step 4: Smoke del coordinador contra el tenant de desarrollo**

Lo hace el coordinador, no un subagente: necesita el túnel, el backend de `main` con 4.4a y el tenant de desarrollo
actualizado por la tarea 16 de 4.4a (`apply.py` → `apply_roles.py` → `apply.py --workflows`, con `READ_ALL_PROJECTS`
concedida a `AUDITOR` y `PROJECT_ADMIN`).

1. Levantar el backend y el portal de la rama:

   ```bash
   cd ~/IdeaProjects/sgspe && git switch main && git pull --ff-only
   GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew bootRun
   # en otra terminal
   curl -s http://localhost:8094/actuator/health/readiness   # {"status":"UP"}
   cd ~/IdeaProjects/sgspe-ui && git switch feat/fase-4-4b-alcance && yarn dev   # http://localhost:5182
   ```

2. Cuentas ficticias `@sgspe.test` del tenant de desarrollo (si falta alguna, crearla en `/admin` con su rol): una con
   `COMMUNITY_RELATIONS_OFFICER` asignada solo a un proyecto (A) y no a otro (B); una con `AUDITOR` sin asignaciones; el
   ADMIN; y una nueva sin asignaciones ni la acción.
3. Con la cuenta asignada solo a A: el selector dice «Todos mis proyectos» y lista solo A; `/projects/<id de B>/overview`
   dice «El registro no existe o no es de sus proyectos.» con «Ir a todos los proyectos» y sin «Reintentar»;
   `/r/commitment/<id de un compromiso de B>` dice «Registro reservado»; la ficha de una QCS de A con un vínculo a otro
   proyecto (si existe) dice «Registro reservado» en ese vínculo; ningún texto en inglés ni código de error.
4. Con AUDITOR: el selector dice «Todos los proyectos» y lista A y B; dentro de B aparece el aviso de solo lectura y no
   hay botones de escritura.
5. Con ADMIN: «Todos los proyectos»; S49 abre con esa etiqueta en las cuatro exportaciones.
6. Con la cuenta sin asignaciones: el selector y S06 dicen «No tiene proyectos asignados. Pida una asignación al
   administrador de proyectos.».
7. Anotar el resultado en el PR:

   ```bash
   gh pr comment feat/fase-4-4b-alcance --body-file - <<'MSG'
   Smoke del coordinador contra el tenant de desarrollo (backend de `main` con 4.4a): selector con «Todos mis proyectos»
   y «Todos los proyectos» según la cuenta, aviso de cuenta sin proyectos, 404 de proyecto y de ficha sin distinguir,
   «Registro reservado» en `/r/…` y en los vínculos, solo lectura para AUDITOR en un proyecto ajeno. Sin textos en inglés
   ni códigos en pantalla.

   🤖 Generated with [Claude Code](https://claude.com/claude-code)

   https://claude.ai/code/session_017MAgYcNauGp6cC3ekX1h7U
   MSG
   ```

   Si algo falla, se corrige en la rama con su prueba (TDD) antes del merge y se repite el paso.

- [ ] **Step 5: Merge del PR del portal**

Con la CI en verde, el smoke anotado y el plan revisado: `gh pr merge feat/fase-4-4b-alcance --merge --delete-branch`.

- [ ] **Step 6: Cierre de la Release 1 en `sgspe`**

La Definición de terminado de F4 (`docs/roadmap.md`, la que 4.4a amplió) exige 4.4a y 4.4b integrados. Con los números
de los dos PR:

```bash
cd ~/IdeaProjects/sgspe && git switch main && git pull --ff-only
gh pr list -R wasichai/sgspe --state merged --head feat/fase-4-4a-alcance --json number -q '.[0].number'
gh pr list -R wasichai/sgspe-ui --state merged --head feat/fase-4-4b-alcance --json number -q '.[0].number'
git switch -c docs/cierre-release-1
```

En `docs/roadmap.md`, en la tabla de F4, la fila `| Estado | …` (la que sigue a `| Definición de terminado |` con
`GrievanceWorkflowApiTest`) pasa a decir, con los dos números que dieron los comandos en lugar de `<4.4a>` y `<4.4b>`:

```markdown
| Estado | Modelo, backend y portal integrados en `main` (planes 4.0 a 4.3; plan 4.4, alcance de lectura por proyecto y separación de tenants: 4.4a en wasichai/sgspe#<4.4a> y 4.4b en wasichai/sgspe-ui#<4.4b>); Release 1 (MVP) cerrada |
```

Si 4.4a dejó en esa fila algo que todavía falta (la medición de §3.10 o la siembra de `MANAGE_TENANTS`), se conserva
como pendiente al final de la fila y la Release 1 no se declara cerrada: se le dice al usuario.

```bash
yarn format
python3 tools/check_traceability.py
git add docs/roadmap.md
git commit -m "docs: cierre de la Release 1 con el alcance por proyecto" -m "La fila Estado de F4 dice los planes 4.4a y 4.4b integrados en main y la Release 1 (MVP)
cerrada.

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_017MAgYcNauGp6cC3ekX1h7U"
git push -u origin docs/cierre-release-1
gh pr create --base main --head docs/cierre-release-1 --title "docs: cierre de la Release 1" --body-file - <<'MSG'
La fila «Estado» de F4 en `docs/roadmap.md` dice integrados los planes 4.4a y 4.4b (alcance de lectura por proyecto) y
la Release 1 (MVP) cerrada.

🤖 Generated with [Claude Code](https://claude.com/claude-code)

https://claude.ai/code/session_017MAgYcNauGp6cC3ekX1h7U
MSG
```

Expected: `check_traceability.py` sin errores y la CI del PR en verde.

---

## Autorrevisión

**Cobertura de la especificación (§3.12 y §6, fila 4.4b).**

| Pide | Tarea |
|---|---|
| Selector que lista los `project` legibles y ofrece «Todos los proyectos» por defecto con `READ_ALL_PROJECTS` | 1 (`useLeeTodosLosProyectos`), 2 |
| Sin asignaciones ni la acción: «No tiene proyectos asignados. Pida una asignación al administrador de proyectos» | 1 (texto), 2 (selector y S06) |
| 404 de detalle: «El registro no existe o no es de sus proyectos», sin distinguir | 1 (`NoEsDeSusProyectos`, `comun.errores.404`), 2 (proyecto), 3 (fichas) |
| «Registro reservado» general (`LecturaConEstados`, `Estados`, `AbrirRegistroPage`) | 1 (`esIlegible`, ayuda general), 4 (`RegistroVinculado`, `AbrirRegistroPage`, aprobaciones); `FalloDeLectura` no cambia (decisión 8) |
| 400 de `RELATION` en el campo: «El registro elegido no existe o es de otro proyecto» | 1 (texto), 5 |
| `NOT_ASSIGNED` como hoy para el caso que queda | Global Constraints, decisiones 13 y 14, comentario de la Tarea 5 |
| Tableros y exportaciones sin cambios de contrato; S49 sin proyecto obligatorio para quien lee todo | 2 (decisión 12) |
| Pruebas con Vitest por pantalla tocada y una de `boundaries.test` sin literales de rol | 1 a 5; `boundaries.test.ts` en la Tarea 1 |
| PR y cierre de la Release 1 | 7 |

**Placeholders.** Quedan solo los valores que se conocen al ejecutar: la fecha y las cifras de la entrada de HISTORY.md
(Tarea 7, `AAAA-MM-DD`, N, M y R) y los números de los dos PR en la fila del roadmap (Tarea 7, `<4.4a>` y `<4.4b>`),
cada uno con el comando que lo da.

**Consistencia de nombres.** `useLeeTodosLosProyectos`, `esIlegible`, `NoEsDeSusProyectos`, `SIN_REGISTRO` y las claves
`comun.codigosEnCampo.registroNoLegible`, `contexto.todosLosProyectos`, `contexto.sinProyectos` y
`reportes.todosLosProyectos` se definen en la Tarea 1 (o en la 2, la de `reportes`) y se usan con el mismo nombre en las
demás. Las rutas y los parámetros de `fichasFueraDeAlcance.test.tsx` salen de los `useParams` de cada página
(`agreementId`, `documentId`, `stakeholderId`, `engagementId`, `recordId`, `areaId`).

**Review Focus.** Cada una de sus cinco líneas tiene su prueba en la tarea que posee el código (1, 2, 3, 4 y 5) y, para las
de navegación, un recorrido de Playwright (Tarea 6).
