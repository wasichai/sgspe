# Relevo del rediseño de UX del portal (2026-10-24)

> Nota para la sesión que continúe el trabajo. El plan con casillas es
> [`2026-10-21-rediseno-ux-portal.md`](2026-10-21-rediseno-ux-portal.md). La fuente de verdad de la interfaz es
> `docs/ux/`; las decisiones `DD-NN` viven en `docs/ux/redesign/07-decisiones-y-compatibilidad.md`.

## 1. Estado

- **Ramas.** Las dos están subidas:
  - `claude/wonderful-rubin-02oanj` en `wasichai/sgspe-ui`, con su PR [wasichai/sgspe-ui#76](https://github.com/wasichai/sgspe-ui/pull/76);
  - `claude/wonderful-rubin-02oanj` en `wasichai/sgspe`, con su PR [wasichai/sgspe#158](https://github.com/wasichai/sgspe/pull/158).
- **Hecho y subido.**
  - Fases 1 a 5 y 7.1/7.3 del plan, y los pendientes de la fase 4.
  - «‹ Volver a …» con J/K.
  - El paso 1 de S24 (F-D) y `AtajosDePeriodo`.
  - El orden por columna en Interacciones, Aportes, Programas, Compromisos, Acuerdos, Riesgos y Grupos de interés.
  - ASM-078 actualizado.
  - El kit re-sincronizado con Claude Design: 71 componentes, en el proyecto «SGSPE Design System».
- **Último integrado** (`sgspe-ui` `77b1daf` y `31def4b`): el arreglo de la prueba intermitente de
  `RegistrarInteraccionPage.test.tsx` y B-04 en el portal. Los dos pasaron la suite completa: 2453 pruebas unitarias y
  91 e2e.
  - Después se unió por merge (`c5128ad`) lo que otra sesión subió a la misma rama: la fase 6 con `@wasichai/*` 1.1.0 y
    B-01.
  - **Ese merge no se verificó localmente**, porque esta sesión no tenía token de GitHub Packages para instalar
    `@wasichai/*` 1.1.0. Lo verifica la CI del PR #76: si está roja, es lo primero que hay que arreglar.
  - La rama `claude/wonderful-rubin-02oanj-wip` del remoto es un respaldo que ya no hace falta: se puede borrar.
- **Causa de la prueba intermitente, demostrada.**
  1. React Router cambia `window.location` antes de confirmar su estado, dentro de `startTransition`.
  2. La prueba esperaba solo la URL y llamaba a `history.back()` sin `act`.
  3. Bajo carga, React descartaba la transición y el formulario conservaba lo escrito.

  El arreglo hace dos cosas:
  - las pruebas esperan el DOM de la pantalla de destino;
  - el componente limpia el borrador en `onSuccess`, porque si no, un retroceso durante la transición podía duplicar la interacción.

  La demostración determinista está en el cuerpo del commit. Los logs están en `flaky-antes.log` y `flaky-despues.log`: 6 vueltas completas de la suite sin fallas en cada caso.

## 2. Pendiente

| Qué | Dónde | Quién |
|---|---|---|
| Validar sobre la demo viva: capturas de los manuales (7.2), pasos F-A a F-G, una sola primaria, U-23 | [wasichai/sgspe#157](https://github.com/wasichai/sgspe/issues/157) | Otra sesión, en curso |
| Fase 6 (pedidos a wasichai): otra sesión la subió a las dos ramas (`@wasichai/*` 1.1.0, wasichai 1.4.0, B-01); falta confirmar que cierra todo lo de #69 | [wasichai/wasichai-ui#69](https://github.com/wasichai/wasichai-ui/issues/69) | Revisar |
| Feriados: endpoint y marca en el calendario | [wasichai/sgspe#159](https://github.com/wasichai/sgspe/issues/159) | Backend y portal |
| Orden por columna en la cola de QCS | [wasichai/sgspe#160](https://github.com/wasichai/sgspe/issues/160) | Backend y portal |
| Conteos del proyecto en una lectura (B-03) | [wasichai/sgspe#161](https://github.com/wasichai/sgspe/issues/161) | Backend y portal |
| «Para tomar» de cuentas con alcance de tenant (B-04, decisión) | [wasichai/sgspe#162](https://github.com/wasichai/sgspe/issues/162) | Decisión y backend |
| §5 del cierre: documentos (ver abajo) | `docs/ux/` y este plan | Quien releve |
| Llevar los PR #76 y #158 a verde y mergeables | GitHub | Quien releve |

**§5, documentos por actualizar en `sgspe`:**

- `docs/ux/redesign/08-cambios-de-contrato-backend.md` §2:
  - B-02 ya está hecho en el código: `Dashboards.catalog()` con `Readability`, aunque sin el alcance por proyecto;
  - B-04 quedó resuelto en el portal para las cuentas normales, y #162 queda para las de alcance de tenant;
  - B-03, el orden de QCS y los feriados se enlazan a #161, #160 y #159.
- `docs/ux/design-system.md`: en la fila de `TablaPriorizada`, decir en qué listas se ordena. Hoy dice «solo en Alertas».
- `docs/ux/screens.md`, en las listas tocadas (S04, S16, S22, S28, S35, S39 y S42):
  - qué columnas se ordenan;
  - que Compromisos y Acuerdos usan `TablaPriorizada`;
  - en Programas, la columna «Código» aparte.
- `docs/ux/screens.md` y `flows.md`: «Para tomar» sin elegir proyecto, y en el inicio «Para tomar en sus proyectos».
- Este plan: las casillas de lo nuevo, con sus commits, en «Lo que quedaba de las fases 2 a 4».
- Verificar con `python3 tools/check_traceability.py` («ok: 304 requisitos») y `yarn format:check`.

## 3. Entorno y reglas aprendidas

- **Node 26:** `export PATH=$HOME/.local/node26/bin:$PATH`. Chromium 1194, con el enlace 1243 → 1194, para Playwright.
- **Suite de `sgspe-ui`:**
  - Antes de las e2e, detener un `vite preview` viejo en 5182, que Playwright reutiliza (`reuseExistingServer`): `parar_preview.py` busca el proceso por el puerto. No usar `pkill -f`, porque se mata a sí mismo.
  - Comando: `(yarn -s format && yarn -s lint && yarn -s typecheck && yarn -s test --testTimeout=60000 && yarn -s build && yarn -s e2e) > log`.
  - Subir solo con código 0, y nunca encadenar el push detrás de un pipe.
- **Subagentes en worktrees:**
  - Crearlos con `git -C <repo> worktree add .claude/worktrees/<n> -b wt-<n> claude/wonderful-rubin-02oanj` y enlazar `node_modules`.
  - `.claude/worktrees/` y `/node_modules` están en `.git/info/exclude`.
  - Integrar por cherry-pick y borrar el worktree después.
- **Breakpoints:** en SGSPE, `2xl` es 1440 px. Lo que va desde 1536 px usa `min-[96rem]` (DD-24).
- **Commits:**
  - Conventional Commits en español, `git add` con rutas explícitas y el cuerpo de 100 columnas como máximo.
  - Ningún identificador de modelo, contraseña ni nombre real en lo versionado.
  - Los IDs solo se citan; se definen en `docs/requirements-analysis.md`.

## 4. Prompt para la sesión que releve

```text
Continúa el rediseño de UX de SGSPE. Lee primero sgspe/docs/superpowers/plans/2026-10-24-relevo-rediseno-ux.md y
los CLAUDE.md de sgspe y sgspe-ui. Trabaja en la rama claude/wonderful-rubin-02oanj de los dos repositorios
(PR wasichai/sgspe-ui#76 y wasichai/sgspe#158; suscríbete a ambos).

1. En sgspe-ui, corre la suite completa (§3 del relevo) sobre el merge c5128ad (@wasichai/* 1.1.0; hace falta el
   token de GitHub Packages en ~/.npmrc) y mira la CI del PR #76; arregla lo que falle. Borra la rama remota
   claude/wonderful-rubin-02oanj-wip.
2. Haz el §5 del relevo: los documentos de sgspe/docs/ux y el plan. Verifica con check_traceability.py y
   yarn format:check, y sube.
3. Lleva los PR #76 y #158 a verde y mergeables. Arregla la CI roja, resuelve conflictos con un merge desde main y
   atiende las revisiones. No hagas merge sin que el usuario lo pida.
4. No toques lo que está en issues de otras sesiones (#157, wasichai-ui#69, #159 a #162), salvo para enlazarlo.
Toma decisiones con criterio y anota cada una en el cuerpo del commit. Usa subagentes en worktrees para el código.
```
