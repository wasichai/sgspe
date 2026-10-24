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

- [x] Prueba en `contexto.test.tsx` con `montar(…, { ruta: '/projects/p1/grievances/q1' })`:
  - la pestaña con `aria-current="page"` es «QCS»;
  - la miga del medio dice «Quejas, consultas y solicitudes» y enlaza a `/projects/p1/attention`.
  
  Lo mismo con `requests/r1` (QCS) y `contributions/a1` (Programas). La prueba debe fallar hoy: la miga dice
  «grievances».
- [x] Exportar de `Migas.tsx` una función `pestanaDeSegmento(segmento: string): string`, que use `PESTANA_DE_SEGMENTO`.
- [x] En `ProyectoLayout.tsx`, calcular `pestanaActual` con `pestanaDeSegmento(pathname.split('/')[3] ?? 'overview')`, de
  modo que `marcada` y las migas reciban la pestaña.
- [x] Revisar que la vista marcada (`v === pestanaActual`) y el título del documento siguen bien en `field-visits`,
  `incidents` y `areas`, con un caso de prueba cada uno.
- [x] Commit: `fix(proyectos): marcar la pestaña y nombrar la miga de las fichas fuera de su segmento` (`sgspe-ui`
  `ccd1eb1`, 2026-10-21).

#### 1.2 El inicio deja de elegir S51 por el catálogo (U-01)

Archivos: `src/portal/pages/InicioPage.tsx`, `src/portal/pages/Inicio.test.tsx` y `src/portal/textos/miDia.ts`.

- [x] Prueba: una cuenta sin `READ` sobre `indicator`, con un catálogo que ofrece KPI-EXE, ve «Mi día» con
  `AtencionRequerida` y ningún texto «No disponible». Debe fallar hoy.
- [x] Prueba: una cuenta con lectura de KPI-EXE ve «Mi día» y, debajo, el bloque «Visión ejecutiva» con su fecha de
  cálculo y el enlace a la revisión completa.
- [x] S51 no tenía ruta propia: se llegaba a ella solo por el inicio. Gana `/dashboards/executive` (`pantallas.ts`,
  `RUTAS_ADICIONALES`), junto al monitoreo y al catálogo; ninguna ruta existente cambia (DD-19).
- [x] Implementar: `InicioPage` siempre devuelve `Bienvenida` («Mi día»), salvo variante de `inicios.ts`. El bloque
  ejecutivo se dibuja con `KpiCard` solo con las cifras `available`, y se omite si no queda ninguna.
- [x] Actualizar el comentario del encabezado de `InicioPage.tsx` (la decisión 7 del plan 8.3 cambia por DD-01).
- [x] Commit: `fix(inicio): mostrar «Mi día» a toda cuenta y la visión ejecutiva como bloque` (`6688a83`).
- [x] **DD-19, confirmada el 2026-10-21:** ASM-074, S02 y S51 de `docs/ux/screens.md`, `ux-architecture.md` §5, los
  manuales (`02-primeros-pasos.adoc`, `08-documentos-tareas-reportes.adoc`) y `generate-screenshots.mjs` dicen que el inicio
  es «Mi día» con la visión ejecutiva.
- [x] La captura `user/02-inicio` se regenera con las demás en 7.2 (necesita la demo viva).

#### 1.3 La primaria es la siguiente acción, sin componente nuevo (U-02)

Archivos: `compromisos/FichaCompromisoPage.tsx`, `riesgos/FichaRiesgoPage.tsx` y sus pruebas.

- [x] Prueba: en un compromiso `in_progress` con las transiciones `fulfill`, `block` y `cancel`, la única `Button`
  primaria es «Registrar cumplimiento». Debe fallar hoy: la primaria es «Bloquear».
- [x] Prueba: «Cancelar el compromiso» no está en la cabecera, sino en «Más acciones».
- [x] Implementar con `siguienteTransicion` (`fichas/siguienteAccion.ts`), la misma del `PanelDeDecision`. Se hizo en
  `transiciones/BotonesDeTransicion.tsx`, para que valga en todas las fichas, con `destructivasEnMenu` para «Más
  acciones».
- [x] Lo mismo en la ficha de riesgo: la siguiente acción es «Decidir…», no «Nueva revisión».
- [x] Ajustar `e2e/`: no hizo falta, las 60 pasan.
- [x] Los manuales afectados se regeneran en 7.2 (necesita la demo viva).
- [x] Commit: `fix(fichas): la acción primaria es la siguiente acción del flujo` (`aaad922`).

### Fase 2. Componentes transversales

Cada componente vive en `src/portal/componentes/<Nombre>.tsx`, con `<Nombre>.test.tsx` al lado y sus textos en
`textos/comun.ts`. Se escribe con los CA de [05 §1](../../ux/redesign/05-especificacion-de-componentes.md#1-componentes-nuevos)
como pruebas, y se adopta en su piloto (06 §3) en el mismo PR.

Hecha el 2026-10-21 con subagentes en worktrees aislados (dos tandas), integrada y en verde: 2243 pruebas unitarias y
60 e2e. Los commits son de `sgspe-ui`.

#### 2.1 `Seccion`

- [x] Pruebas CA-1.1.1 y CA-1.1.2, más el anidado por contexto (una `Seccion` dentro de otra pierde el borde y baja a `h3`).
- [x] Adoptada en `proyectos/ResumenPage.tsx`, sin cambio visual.
- [x] Commit `ec1c60b`.

#### 2.2 `CabeceraDeFicha`

- [x] Pruebas CA-1.2.1 a CA-1.2.4, la acción propia como primaria o secundaria y el orden «secundarias, Más acciones,
  primaria». La lógica de `BotonesDeTransicion` pasó al hook `useTransicionesOfrecidas`.
- [x] Adoptada en la ficha de compromiso: antetítulo en sans, sin el chip de estado (U-25) y lo que falta visible bajo las
  acciones. «Bloquear» queda en «Más acciones», porque «Editar» e «Informar riesgo» ocupan los dos lugares visibles.
- [x] Commit `7d6f46c`.
- [x] Prueba e2e CA-1.2.5 sobre un compromiso en ejecución: `f97f513` (`e2e/pendientes.spec.ts`).

#### 2.3 `SelectorDeVista`

- [x] Pruebas CA-1.3.1 y CA-1.3.2, y la del teclado (flechas, Home y End).
- [x] En la lista de compromisos reemplaza a `VistasGuardadas` (`componentes/Lista.tsx`), la segunda fila de pestañas con
  conteo. `VistasDelModulo` es el conmutador «Lista · Tablero» y sigue en la cabecera.
- [x] Commit `37cbd89`, y `68e265e` para que «Más vistas» (`MasAcciones`) se cierre al elegir.

#### 2.4 `BarraDeFiltros`

- [x] Pruebas CA-1.4.1 a CA-1.4.3, con la URL leída de `useSearchParams`.
- [x] Adoptada en la lista de compromisos: búsqueda, estado como filtro frecuente y chips.
- [x] Commit `253a19b`.
- [x] La espera de 300 ms de la búsqueda (05 §1.4): lo escrito se ve al instante y `q` llega 300 ms después de la última
  tecla, o con Enter. Commit `fa57db2`.

#### 2.5 `CampoFecha`

- [x] Pruebas CA-1.5.1 y CA-1.5.2 (con `navigator.language` en `en-US`), las de entrada `20102026` y `20-10-2026`, y la del
  error de formato.
- [x] La conversión usa `datos/fechas.ts` (`diaDeTexto`, `textoDeDia` y `horaDeTexto`, nuevas, sin aritmética de zona).
  Lo que llega al servidor no cambia.
- [x] Adoptado en registrar QCS.
- [x] Commit `42e1317`.
- [x] La grilla de calendario con teclado (patrón «Date Picker Dialog» de WAI-ARIA), con el botón que nombra su campo
  («Elegir Desde en el calendario»). Commits `9de0f80` y `bff1b26`.

#### 2.6 `TablaPriorizada`

- [x] Prueba CA-1.6.1, la de Enter en una fila y la de las secundarias desde `xl`.
- [x] Adoptada en Grupos de interés.
- [x] Commits `31df998` y `88dd777`: las opcionales se ven desde `2xl` mientras no exista «Columnas», para que la
  relación y la evaluación no queden sin forma de verse.
- [x] El selector «Columnas», recordado por tabla en el navegador (`4160d52`); el orden con `aria-sort`, adoptado solo en
  Alertas y sin el orden en la URL (`21213d0`); Espacio para la vista previa (`9629b9b`). CA-1.6.2 la cubre
  `e2e/grupos-interacciones-avisos.spec.ts`.

#### 2.7 `ListaPriorizada`

- [x] Pruebas CA-1.7.1 y CA-1.7.2, y la de `total` nulo sin número.
- [x] Adoptada en «Atención requerida» del resumen del proyecto, con límite 3 y el motivo de cada entrada. Solo «Sin
  contacto» tiene una lista a la que ir.
- [x] Commits `ddf91b8` y `16988e7`: sin una lista a la que ir, «Mostrar N más» despliega el resto en el lugar.

#### 2.8 `EstadoSinAcceso`

- [x] Pruebas CA-1.8.1 y CA-1.8.2.
- [x] Adoptado en `shell/GuardaDeSeccion.tsx`. Se pide a la administración de la organización.
- [x] Dentro de un proyecto, se pide a la jefatura por su nombre si la cuenta lee las asignaciones, y se vuelve al resumen
  del proyecto. Commit `f425b81`.
- [x] Commit `068e7a8`.

### Fase 3. Tokens y shell

Hecha el 2026-10-22 con subagentes, integrada y en verde: 2276 pruebas unitarias y 63 e2e. Los commits son de `sgspe-ui`;
los de `sgspe` (`4a4175b` y `d29d0f7`) llevan la fuente de los tokens y `contraste.py`.

#### 3.1 Tokens (U-39, U-40, U-41, U-43)

- [x] En `docs/ux/tokens.css`, `--sg-border-control` y `--sg-surface-raised` en los tres bloques de tema, y los colores de
  datos, postura, riesgo y suelo del mapa en `@theme inline`.
- [x] El borde de control en oscuro tiene valor propio (`#66726c`, DD-21): como alias del borde fuerte daba 2.81:1 sobre
  lo que flota.
- [x] Pares nuevos en `contraste.py`, todos obligatorios y en verde.
- [x] Copia a `sgspe-ui/src/theme/tokens.css` idéntica según `cmp`, con lo que se cierra U-39. Los selectores del oscuro
  son los de la fuente: `:root[data-theme="dark"]` (y `sgspe-noche`), y `:root:not([data-theme])` para el sistema.
  `ThemeProvider` siempre escribe `data-theme`.
- [x] En `src/index.css`, el borde de los controles de `@wasichai/ui` va por `data-slot` (sin tocar el inválido) y el
  texto de ejemplo con `--sg-text-muted`.
- [x] No quedaba ningún `var(--sg-…)` de datos dentro de una clase: los usos son estilos en línea, SVG, MapLibre y ECharts,
  y se conservan.
- [x] Commits `d5ad5e1` y `13572db`.

#### 3.2 Barra superior (U-12)

- [x] Seis controles a 1440 px (e2e `shell.spec.ts`): marca, proyecto, Buscar, Preguntar, avisos y cuenta. Idioma, tema
  y salir pasan al menú de la cuenta, y el lema queda solo desde 1536 px.
- [x] El selector de proyecto pasa a un combobox con búsqueda (`SelectorDeProyecto`).
- [x] Commits `8df2b61`, `730d404` y `e08a44b`. El último corrige el punto de corte: en SGSPE, `2xl` es 1440 px (DD-24).

#### 3.3 Navegación (U-21)

- [x] Expandida desde `lg`, con títulos de bloque (`BLOQUES` en `navTree.ts`) y «Contraer», que se recuerda por navegador
  (DD-22). Lo cubre el e2e `shell.spec.ts`.
- [x] «Quejas y solicitudes» se dice solo en la barra lateral (`seccionesEnLaBarra`), con el nombre completo en el `title`:
  las migas, el título del documento y la guarda siguen con «Quejas, consultas y solicitudes».
- [x] Commit `2e6b95a`.

#### 3.4 Una sola fila de pestañas (U-18, U-19, U-20)

- [x] Sin segunda fila en `ProyectoLayout`. Compromisos y Acuerdos, y las páginas de Riesgos (registro, alerta temprana,
  señales, menciones, impactos, incidentes y reglas), pasan a `SelectorDePagina`, que navega entre rutas sin cambiar
  ninguna (DD-23).
- [x] La pestaña activa queda a la vista también cuando las pestañas y sus conteos llegan después (e2e `shell.spec.ts` a
  480 px). Mientras la persona no desplaza la fila, se la trae otra vez con cada cambio.
- [x] Commits `d55760f`, `8d8c035` y `d8018a8`.
- [x] Las vistas del registro de riesgos y de la alerta temprana pasan al `SelectorDeVista` en la tarea 4.3
  (`5b21ea3…9ddc0c0`).

### Fase 4. Áreas

Un PR por área. Cada una con sus pruebas de pantalla, su e2e y sus capturas de manual regeneradas.

- [x] **4.1 Inicio «Mi trabajo»**: bloques de 02 §4, con «para tomar» en «Lo que me toca hoy» (U-13). CA de P-01.
  Commits `132855f` y `5bd9cb4`; el segundo hace que una fecha inválida no tumbe el inicio. La agenda muestra las
  interacciones de los proyectos vigentes, porque la interacción no tiene responsable (DD-26).
- [x] **4.2 Ficha 360 del proyecto**: franja de cifras y «Qué requiere atención» con `ListaPriorizada` (U-05). CA de
  P-03. Commit `91c8e70`. La franja tiñe solo lo que pide acción y la cifra de QCS lleva `NotaDeConfidenciales`.
- [x] **4.3 Listados**: Grupos de interés, Interacciones, QCS, Riesgos, Programas (U-35) y Avisos con `BarraDeFiltros` y
  `TablaPriorizada` (U-06, U-22). CA de P-02. Cubierto con e2e a 1440 y 390 px (`grupos-interacciones-avisos.spec.ts` y
  `listasF4.spec.ts`).
  - Commits de grupos, interacciones y avisos: `4fc6e2d…bf41f2b`. En la lista global de grupos, el responsable pasa a
    opcional para que la tabla quepa.
  - Commits de QCS, registro de riesgos, alertas y programas: `5b21ea3…9ddc0c0`. Las cifras de QCS se entienden (U-24),
    las alertas ganan búsqueda y severidad, y Programas y Aportes quedan en una sola vista (DD-27).
  - [x] Espacio en una fila para abrir la vista previa (02 §5), en Grupos, Riesgos, Programas, Aportes y la cola de
    Atención: `9629b9b`.
- [x] **4.4 Fichas**: `CabeceraDeFicha` y `Seccion` en las fichas de grupo, QCS, riesgo, aporte, acuerdo e interacción;
  campos vacíos plegados (U-25, U-26, U-27, U-28). CA de P-09.
  - Siete fichas, con `CamposPlegables` y `tituloQueDiceQueEs`.
  - Rechazar y desistir pasan a ser destructivas en todo el portal (DD-28).
  - [x] El camino del flujo, plegado dentro del `PanelDeDecision` («Ver el camino»), en las nueve fichas que lo dibujaban
    aparte (DD-06): `8c7fdb0`.
- [x] **4.5 Formularios**: QCS por pasos (U-30); `CampoFecha` en los 32 sitios restantes (CA-1.5.3); evaluación por
  grupos de escalas (U-29). CA de P-04.
  - Registrar QCS en tres pasos con «Qué pasará» al costado, y la evaluación en cinco temas con su avance (DD-29):
    `019eb39`, `70cf807` y `f26b3e3` (la e2e que declara los campos del alta).
  - `CampoFecha` en las 17 pantallas restantes y en los filtros de los tableros, con la guarda `camposDeFecha.test.ts`
    sin excepciones: commits `c10e0ce…caac05c` integrados y `84f329a`, que suma `ayuda` e `invalido` a `CampoFecha`.
  - [x] La grilla de calendario de `CampoFecha`: `9de0f80` y `bff1b26`.
- [x] **4.6 Bandeja de aprobaciones**: decisión con motivo en el panel (F-E, U-36). CA de P-05. Commits `d5a57b7` y
  `f47a2ac`. La queja grave, el acta y la revisión de un proyecto se eligen en la bandeja, pero se deciden en su ficha.
- [x] **4.7 Mapa primero**: capas y leyenda superpuestas, lista plegable y el aviso de mapa base con dónde se configura
  (U-10, U-32). CA de P-06. Commit `172c4c7`. El aviso dice quién configura el mapa base, sin enlace (DD-25).
- [x] **4.8 Revisión ejecutiva**: periodo y fecha de cálculo en el título, filtros plegados y gráficos sin trama (U-11,
  U-23). CA de P-07. Commits `fab8b23` (la trama se quita de todos los gráficos, DD-15) y `0a07ecb`. «Qué es crítico» dice
  dónde se atiende y no quién, porque el contrato del tablero no trae a la persona responsable.
- [x] **4.9 Textos**: los explicativos sin jerga de implementación (U-24, U-36), en los dos idiomas. Commit `901a5f0`, con
  la guarda `textosLlanos.test.ts`. Unos 30 textos reescritos en lenguaje llano.
  - [x] El asistente de IA dice el tipo y el rótulo del registro, nunca su id; al proveedor le sigue llegando la referencia
    técnica (decisión 6 del plan 7.3): `cceb0e0`.
- [x] **4.10 Tarjetas a mano**: `Seccion` en las restantes (CA-1.1.3). 54 superficies en 37 archivos, commits
  `…1a21d47`, con la guarda `superficies.test.ts` y cinco excepciones explicadas (tarjetas clicables, ítems de lista y un
  panel desplegable). `Seccion` acepta `elemento="aside"` para los paneles al costado.
- [x] Las capturas de los manuales de las áreas se regeneran en 7.2 (necesita la demo viva).

### Fase 5. Administración del lado de SGSPE

- [x] **5.1** S53 «Administración» como pantalla del portal en `/administration` (DD-20), con «En el portal», «En la
  consola de la plataforma» y el aviso «Cuidados» antes de «Abrir la consola», que abre `/admin` en otra pestaña (U-37,
  parcial). Commit `2741a42`.
- [x] **5.2** El equipo del proyecto: «Terminar la asignación…» en el menú de la fila, con un `ConfirmDialog` que nombra
  a la persona (U-16). La escritura no cambia. Commit `fd27d2d`.

### Fase 6. Dependencias externas

Cada ítem se abre cuando su par de [08](../../ux/redesign/08-cambios-de-contrato-backend.md) esté publicado.

- [x] **6.1** Etiquetas de estado en inglés (B-01, U-42): `EstadoChip` y el camino del flujo leen la etiqueta en el
  idioma activo. wasichai 1.4.0 y los `labels` de los 19 workflows (`sgspe` `4dbcab5`; el inglés es un borrador hasta
  wasichai/sgspe#163); `datos/flujos` en el portal (`sgspe-ui` `4602821`).
- [x] **6.2** Tema de `/admin` (W-01 a W-05, U-08): lema, rótulos de rol y permiso, y formato de fecha. @wasichai/* 1.1.0
  y `modelInRepository` (`sgspe-ui` `128a56b`).
- [x] **6.3** Superficie flotante (W-06) y `data-slot` de los controles (W-07): la regla de 3.1 se reemplaza por el
  token o el selector de wasichai. El puente `wasichai.css` asigna
  `--surface-raised`, `--border-control` y `--placeholder`, y sale la regla de `index.css` (`sgspe-ui` `128a56b`).

### Fase 7. Documentación

- [x] **7.1** Llevar a `docs/ux/` lo aprobado (2026-10-23, commits de `sgspe`):
  - `ux-architecture.md`: shell, inicio «Mi trabajo», pestañas, teléfono y estados (`2567361`);
  - `design-system.md`: tokens nuevos, espaciado, puntos de corte, los diez componentes, la acción principal y el contraste
    (`258faed` y `8b9bc50`);
  - `screens.md` y `flows.md` de las pantallas y los flujos tocados (`00a06b0`, `8b9bc50` y `06f8d8e`).
- [ ] **7.2** Regenerar todas las capturas de `docs/manuals/` y revisar los manuales.
- [x] **7.3** Marcar `docs/ux/redesign/` como historia en su README, con el enlace desde `docs/ux/README.md` (`ad44fd8`).

### Lo que quedaba de las fases 2 a 4 (2026-10-23)

- [x] «‹ Volver a <lista> (N)» y «Anterior» y «Siguiente» (K y J) en las fichas abiertas desde una lista, un aviso, la
  búsqueda o una tarea (02 §5, F-C): `sgspe-ui` `89d1877`.
- [x] F-D: el paso 1 de S24 se reduce a tipo, título, «¿Ya se realizó?» (dos opciones con su efecto) y fecha con hora, y
  el modo rápido es un conmutador de la cabecera: `sgspe-ui` `1d567d3`.
- [x] `AtajosDePeriodo` junto a los periodos de tableros, reportes de QCS y mapa: `sgspe-ui` `b69fead`.
- [x] El kit re-sincronizado con Claude Design (71 componentes, 10 nuevos): `sgspe-ui` `bc43c83`.
- [ ] Los feriados marcados en el calendario: esperan un endpoint de `sgspe` que los exponga.
- [ ] El orden por columna fuera de Alertas: espera que esas listas acepten `sort` y `dir` en su consulta.
- La validación sobre la demo viva (7.2 y los dos indicadores que faltan) es wasichai/sgspe#157; la fase 6 se hizo con
  wasichai/wasichai-ui#69 (2026-10-24).

## Verificación final

- [ ] Los siete indicadores de [06 §5](../../ux/redesign/06-plan-de-implementacion.md#5-cómo-se-mide-el-resultado),
  medidos antes y después, en el PR de cierre. Medidos en el código el 2026-10-23: seis controles en la barra superior a
  1440 px (`e2e/shell.spec.ts`), ninguna caja de sección a mano fuera de `Seccion` salvo las cinco excepciones de
  `superficies.test.ts`, ningún campo de fecha nativo (`camposDeFecha.test.ts`), ningún desborde a 1440 px en las listas
  de 4.3 (e2e) y ningún par de contraste que falle. Faltan los pasos de F-A a F-G y las pantallas con más de una
  primaria, que se cuentan sobre la demo viva, con las capturas de 7.2.
- [x] `python3 docs/ux/redesign/contraste.py` sin fallas (2026-10-23).
- [x] La suite completa de `sgspe-ui` en verde (2410 pruebas unitarias y 91 e2e), y `python3 tools/check_traceability.py`
  en `sgspe` (304 requisitos).
