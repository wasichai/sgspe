# Rediseño integral de UX/UI del portal, plan de implementación

> **Para agentes:** SUB-SKILL REQUERIDA: superpowers:executing-plans, tarea por tarea. Los pasos usan casillas (`- [ ]`).
> Spec: [`docs/superpowers/specs/2026-10-20-rediseno-ux-design.md`](../specs/2026-10-20-rediseno-ux-design.md).
> **No se ejecuta hasta que el usuario apruebe el rediseño** (DP-05). Las fases y su porqué están en
> [`docs/ux/redesign/06-plan-de-implementacion.md`](../../ux/redesign/06-plan-de-implementacion.md). Los criterios
> `CA-N.N.N` están en [`05`](../../ux/redesign/05-especificacion-de-componentes.md).

**Objetivo:** que cada persona encuentre lo que le toca, entienda el estado de una situación y complete su tarea con el
mínimo esfuerzo razonable. Se recomponen piezas que ya son buenas; no se cambia la superficie por cambiarla.

**Arquitectura:**

```
fase 1  defectos P1 (fix)  ─►  fase 2  componentes con piloto  ─►  fase 3  tokens y shell
   ─►  fase 4  áreas  ─►  fase 5  administración propia  ─►  fase 6  dependencias (08)  ─►  fase 7  docs/ux
```

**Tecnologías:** React 19, TypeScript, Tailwind v4 con los tokens `sg-*`, `@wasichai/ui` y `@wasichai/core`, i18next
(es-PE y en), Vitest, Testing Library, `mockFetch` de `@wasichai/testing` y Playwright para e2e.

## Restricciones globales

- **Repositorio:** `sgspe-ui`, salvo la tarea 3.1 (tokens) y la fase 7, que empiezan en `sgspe/docs/ux/`.
- **Prueba primero:** cada tarea escribe la prueba, la ve fallar, implementa y la ve pasar.
- **Antes de cada commit:** `yarn format && yarn lint && yarn typecheck && yarn test && yarn build && yarn e2e`.
- **Commits:** Conventional Commits en español, `git add` con rutas explícitas.
- **Nada de esto cambia:**
  - las rutas ni los parámetros (`?view=`, `?peek=`, `?selected=`, `?territory=`);
  - las decisiones por permisos, nunca por nombre de rol (`boundaries.test.ts`);
  - la escritura por `datos/`;
  - la confidencialidad: `RegistroVinculado`, `NoEsDeSusProyectos` y `NotaDeConfidenciales` sin conteo;
  - los valores de los tokens `--sg-*` existentes.
- **Textos:** todo texto nuevo va en `textos/<módulo>.ts` en los dos idiomas y sin códigos (`textosSinCodigos.test.ts`).
- **Usuarios de prueba:** ficticios `@sgspe.test`.

## Tareas

### Fase 1. Defectos P1

#### 1.1 Pestaña y migas de las fichas fuera de su segmento (U-04)

Archivos: `src/portal/componentes/Migas.tsx`, `src/portal/contexto/ProyectoLayout.tsx` y
`src/portal/contexto/contexto.test.tsx`.

- [ ] Prueba en `contexto.test.tsx` con `montar(…, { ruta: '/projects/p1/grievances/q1' })`:
  - la pestaña con `aria-current="page"` es «QCS»;
  - la miga del medio dice «Quejas, consultas y solicitudes» y enlaza a `/projects/p1/attention`.
  
  Lo mismo con `requests/r1` (QCS) y `contributions/a1` (Programas). La prueba debe fallar hoy: la miga dice
  «grievances».
- [ ] Exportar de `Migas.tsx` una función `pestanaDeSegmento(segmento: string): string`, que use `PESTANA_DE_SEGMENTO`.
- [ ] En `ProyectoLayout.tsx`, calcular `pestanaActual` con `pestanaDeSegmento(pathname.split('/')[3] ?? 'overview')`, de
  modo que `marcada` y las migas reciban la pestaña.
- [ ] Revisar que la vista marcada (`v === pestanaActual`) y el título del documento siguen bien en `field-visits`,
  `incidents` y `areas`, con un caso de prueba cada uno.
- [ ] Commit: `fix(proyectos): marcar la pestaña y nombrar la miga de las fichas fuera de su segmento`.

#### 1.2 El inicio deja de elegir S51 por el catálogo (U-01)

Archivos: `src/portal/pages/InicioPage.tsx`, `src/portal/pages/Inicio.test.tsx` y `src/portal/textos/miDia.ts`.

- [ ] Prueba: una cuenta sin `READ` sobre `indicator`, con un catálogo que ofrece KPI-EXE, ve «Mi día» con
  `AtencionRequerida` y ningún texto «No disponible». Debe fallar hoy.
- [ ] Prueba: una cuenta con lectura de KPI-EXE ve «Mi día» y, debajo, el bloque «Visión ejecutiva» con su fecha de
  cálculo y el enlace a la revisión completa (la ruta vigente de S51, sin cambiarla).
- [ ] Implementar: `InicioPage` siempre devuelve `Bienvenida` («Mi día»), salvo variante de `inicios.ts`. El bloque
  ejecutivo se dibuja con `KpiCard` solo con las cifras `available`, y se omite si no queda ninguna.
- [ ] Actualizar el comentario del encabezado de `InicioPage.tsx` (la decisión 7 del plan 8.3 cambia por DD-01).
- [ ] Commit: `fix(inicio): mostrar «Mi día» a toda cuenta y la visión ejecutiva como bloque`.

#### 1.3 La primaria es la siguiente acción, sin componente nuevo (U-02)

Archivos: `compromisos/FichaCompromisoPage.tsx`, `riesgos/FichaRiesgoPage.tsx` y sus pruebas.

- [ ] Prueba: en un compromiso `in_progress` con las transiciones `fulfill`, `block` y `cancel`, la única `Button`
  primaria es «Registrar cumplimiento». Debe fallar hoy: la primaria es «Bloquear».
- [ ] Prueba: «Cancelar el compromiso» no está en la cabecera, sino en «Más acciones».
- [ ] Implementar con la información que ya da `PanelDeDecision` (la siguiente acción), sin cambiar `transiciones/`.
- [ ] Lo mismo en la ficha de riesgo: la siguiente acción es «Decidir…», no «Nueva revisión».
- [ ] Ajustar `e2e/` y regenerar los manuales afectados con su script.
- [ ] Commit: `fix(fichas): la acción primaria es la siguiente acción del flujo`.

### Fase 2. Componentes transversales

Cada componente vive en `src/portal/componentes/<Nombre>.tsx`, con `<Nombre>.test.tsx` al lado y sus textos en
`textos/comun.ts`. Se escribe con los CA de [05 §1](../../ux/redesign/05-especificacion-de-componentes.md#1-componentes-nuevos)
como pruebas, y se adopta en su piloto (06 §3) en el mismo PR.

#### 2.1 `Seccion`

- [ ] Pruebas CA-1.1.1 y CA-1.1.2.
- [ ] Implementar con `cn` y las clases `rounded-sg-lg border border-sg-border bg-sg-surface p-4`.
- [ ] Adoptar en `proyectos/ResumenPage.tsx`, sin cambios visuales salvo el anidado.
- [ ] Commit: `feat(componentes): Seccion para las superficies con título`.

#### 2.2 `CabeceraDeFicha`

- [ ] Pruebas CA-1.2.1 a CA-1.2.4. Se monta con `useAvailableTransitions` simulado por `mockFetch`, sobre
  `/workflows/…/transitions`, como en `PanelDeDecision.test.tsx`.
- [ ] Implementar sobre `EncabezadoDePagina`, `Button`, el menú «Más acciones» y `ConfirmDialog`. La lista de estados
  destructivos va en una constante con su prueba.
- [ ] Adoptar en la ficha de compromiso; reemplaza la solución de 1.3. Prueba e2e CA-1.2.5.
- [ ] Commit: `feat(componentes): CabeceraDeFicha con la regla de la acción principal`.

#### 2.3 `SelectorDeVista`

- [ ] Pruebas CA-1.3.1 y CA-1.3.2, y la del teclado (flechas, Home y End).
- [ ] Implementar. Reemplaza a `VistasDelModulo` en la lista de compromisos; `VistasDelModulo` queda hasta la fase 4.
- [ ] Commit: `feat(componentes): SelectorDeVista en una sola fila`.

#### 2.4 `BarraDeFiltros`

- [ ] Pruebas CA-1.4.1 a CA-1.4.3, con la URL leída de `useSearchParams`.
- [ ] Implementar sobre `FiltroDeLista` y `Drawer`.
- [ ] Adoptar en la lista de compromisos.
- [ ] Commit: `feat(componentes): BarraDeFiltros con chips y Limpiar`.

#### 2.5 `CampoFecha`

- [ ] Pruebas CA-1.5.1 y CA-1.5.2 (con `navigator.language` en `en-US`); las de entrada `20102026` y `20-10-2026`; la del
  error de formato; y las de la grilla con teclado.
- [ ] Implementar. La conversión usa `datos/fechas.ts`: ninguna aritmética de zona propia.
- [ ] Adoptar en registrar QCS.
- [ ] Commit: `feat(componentes): CampoFecha en dd/mm/aaaa y 24 h de Lima`.

#### 2.6 `TablaPriorizada`

- [ ] Prueba CA-1.6.1, la de `aria-sort` y las de Enter y Espacio en una fila.
- [ ] Implementar sobre `Table`, `Th` y `Td`.
- [ ] Adoptar en Grupos de interés. Prueba e2e CA-1.6.2.
- [ ] Commit: `feat(componentes): TablaPriorizada con prioridad de columnas`.

#### 2.7 `ListaPriorizada`

- [ ] Pruebas CA-1.7.1 y CA-1.7.2, y la de `total` nulo con `NotaDeConfidenciales`.
- [ ] Adoptar en la ficha 360 («Qué requiere atención»).
- [ ] Commit: `feat(componentes): ListaPriorizada en lugar de los muros de enlaces`.

#### 2.8 `EstadoSinAcceso`

- [ ] Pruebas CA-1.8.1 y CA-1.8.2.
- [ ] Adoptar en `shell/GuardaDeSeccion.tsx`.
- [ ] Commit: `feat(componentes): EstadoSinAcceso con a quién pedirlo y cómo volver`.

### Fase 3. Tokens y shell

#### 3.1 Tokens (U-39, U-40, U-41, U-43)

- [ ] En `sgspe/docs/ux/tokens.css`:
  - agregar `--sg-border-control` (alias de `--sg-border-strong`) y `--sg-surface-raised` (#ffffff en claro, #1d2420 en
    oscuro);
  - exponer `--sg-data-*`, `--sg-stance-*`, `--sg-risk-*` y `--sg-map-ground` en `@theme inline`.
- [ ] Agregar sus pares a `docs/ux/redesign/contraste.py` y verlos pasar.
- [ ] Copiar el archivo a `sgspe-ui/src/theme/tokens.css` y verificar con `cmp`. Esto cierra U-39, incluido el selector
  `sgspe-noche`.
- [ ] En `src/index.css`, el borde de `input`, `textarea` y `select` con `--sg-border-control`, y el texto de ejemplo
  con `--sg-text-muted`. Si `@wasichai/ui` gana el `data-slot` (08 W-07), se usa ese selector.
- [ ] Reemplazar los `var(--sg-…)` sueltos de las pantallas por las utilidades nuevas.
- [ ] Commit: `feat(tema): borde de control, superficie flotante y tokens de datos como utilidades`.

#### 3.2 Barra superior (U-12)

- [ ] Prueba e2e: a 1440 px, la barra tiene 6 controles; idioma y tema están en el menú de la cuenta.
- [ ] Pasar idioma y tema a `shell/Perfil.tsx`. El selector de proyecto pasa a combobox con búsqueda.
- [ ] Commit: `feat(shell): barra superior con seis controles`.

#### 3.3 Navegación (U-21)

- [ ] Prueba e2e: a 1024 px se ven las etiquetas y los títulos de bloque, y «Contraer» persiste al recargar.
- [ ] En `navTree.ts`, la etiqueta larga «Quejas y solicitudes»; la sigla queda en la pestaña.
- [ ] Commit: `feat(shell): navegación expandida desde lg con títulos de bloque`.

#### 3.4 Una sola fila de pestañas (U-18, U-19, U-20)

- [ ] Prueba: en Riesgos, «Alerta temprana», «Señales», «Menciones» e «Impactos» son vistas del `SelectorDeVista`, con
  sus rutas intactas.
- [ ] Quitar la segunda fila de `ProyectoLayout` (`nav` de las vistas, línea 150) a favor del selector de cada lista.
- [ ] Prueba e2e: la pestaña activa queda visible con desplazamiento automático (`PestanasDesplazables`).
- [ ] Commit: `feat(proyectos): una sola fila de pestañas y las vistas en el selector`.

### Fase 4. Áreas

Un PR por área. Cada una con sus pruebas de pantalla, su e2e y sus capturas de manual regeneradas.

- [ ] **4.1 Inicio «Mi trabajo»**: bloques de 02 §4, con «para tomar» en «Lo que me toca hoy» (U-13). CA de P-01.
- [ ] **4.2 Ficha 360 del proyecto**: franja de cifras y «Qué requiere atención» con `ListaPriorizada` (U-05). CA de
  P-03.
- [ ] **4.3 Listados**: Grupos de interés, Interacciones, QCS, Riesgos, Programas (U-35) y Avisos con `BarraDeFiltros` y
  `TablaPriorizada` (U-06, U-22). CA de P-02.
- [ ] **4.4 Fichas**: `CabeceraDeFicha` y `Seccion` en las fichas de grupo, QCS, riesgo, aporte, acuerdo e interacción;
  campos vacíos plegados (U-25, U-26, U-27, U-28). CA de P-09.
- [ ] **4.5 Formularios**: QCS por pasos (U-30); `CampoFecha` en los 28 sitios restantes (CA-1.5.3); evaluación por
  grupos de escalas (U-29). CA de P-04.
- [ ] **4.6 Bandeja de aprobaciones**: decisión con motivo en el panel (F-E, U-36). CA de P-05.
- [ ] **4.7 Mapa primero**: capas y leyenda superpuestas, lista plegable y el aviso de mapa base con dónde se configura
  (U-10, U-32). CA de P-06.
- [ ] **4.8 Revisión ejecutiva**: periodo y fecha de cálculo en el título, filtros plegados y gráficos sin trama (U-11,
  U-23). CA de P-07.
- [ ] **4.9 Textos**: los explicativos sin jerga de implementación (U-24, U-36), en los dos idiomas.
- [ ] **4.10 Tarjetas a mano**: `Seccion` en las 136 restantes (CA-1.1.3).

### Fase 5. Administración del lado de SGSPE

- [ ] **5.1** En S53 y en el puente hacia `/admin`, el aviso de que el modelo y los workflows viven en el repositorio
  (`model/*.json`, GAP-030) y de que lo editado en `/admin` se pierde al aprovisionar de nuevo (U-37, parcial).
- [ ] **5.2** El equipo del proyecto: «Terminar la asignación…» en el menú de la fila, con un `ConfirmDialog` que nombra
  a la persona (U-16). CA de P-08.

### Fase 6. Dependencias externas

Cada ítem se abre cuando su par de [08](../../ux/redesign/08-cambios-de-contrato-backend.md) esté publicado.

- [ ] **6.1** Etiquetas de estado en inglés (B-01, U-42): `EstadoChip` y el camino del flujo leen la etiqueta en el
  idioma activo.
- [ ] **6.2** Tema de `/admin` (W-01 a W-05, U-08): lema, rótulos de rol y permiso, y formato de fecha.
- [ ] **6.3** Superficie flotante (W-06) y `data-slot` de los controles (W-07): la regla de 3.1 se reemplaza por el
  token o el selector de wasichai.

### Fase 7. Documentación

- [ ] **7.1** Llevar a `docs/ux/` lo aprobado:
  - `ux-architecture.md`: shell, inicio, pestañas y estados;
  - `design-system.md`: tokens nuevos, espaciado y los ocho componentes;
  - `screens/` y `flows/` de las pantallas tocadas.
- [ ] **7.2** Regenerar todas las capturas de `docs/manuals/` y revisar los manuales.
- [ ] **7.3** Marcar `docs/ux/redesign/` como historia en su README.

## Verificación final

- [ ] Los siete indicadores de [06 §5](../../ux/redesign/06-plan-de-implementacion.md#5-cómo-se-mide-el-resultado),
  medidos antes y después, en el PR de cierre.
- [ ] `python3 docs/ux/redesign/contraste.py` sin fallas.
- [ ] La suite completa de `sgspe-ui` en verde, y `python3 tools/check_traceability.py` en `sgspe`.
