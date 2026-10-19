# Prompt para implementar la revisión de UX

Una sesión de Claude Code por etapa de [`plan.md`](plan.md). Se pega el prompt general con el bloque de la etapa al final.
La etapa 0 va primero; las demás pueden ir en cualquier orden, aunque 1 → 2 → 3 → 4 es el que más rinde.

## Prompt general

```text
Trabajas en sgspe-ui (portal React de SGSPE). Lee primero CLAUDE.md y respétalo en todo: textos en es-PE e inglés por
módulo (src/portal/textos/), identificadores y comentarios en español, ningún nombre de rol en src/, tokens sg-* para
estilos, toda escritura por src/portal/datos, rutas con encodeURIComponent, pruebas primero con Vitest, Testing Library y
mockFetch, Conventional Commits en español y `git add` con rutas explícitas.

Vas a implementar una etapa de la revisión de UX que está en ../sgspe/docs/ux-revision/plan.md. Léelo completo: el diagnóstico (§1),
los defectos (§2), las inconsistencias (§3), la matriz de fichas (§4) y la etapa que te toca (§5). El diseño de referencia
está en ../sgspe/docs/ux/ (ux-architecture.md §7 y §8, design-system.md §5 y §6, screens.md para cada pantalla) y gana
sobre el plan donde difieran. Las decisiones de §6 están tomadas; si aparece otra que nadie tomó, no la tomes: implementa
lo que no depende de ella y deja la pregunta escrita en el PR.

Cómo trabajar:
1. Crea la rama `ux/etapa-N-<tema>` desde main.
2. Arma una lista de tareas con una entrada por pieza común y una por pantalla de la etapa.
3. Pieza común primero: su prueba, luego el componente, luego sus textos en los dos idiomas.
4. Después, pantalla por pantalla: lee la pantalla y su ficha en screens.md, escribe o ajusta su prueba para el
   comportamiento nuevo, migra, corre sus pruebas. No mezcles dos pantallas en un commit. No cambies el comportamiento de
   datos (permisos, If-Match, Idempotency-Key, lo ilegible como «Registro reservado»): esta revisión es de presentación y
   navegación.
5. Al terminar cada pantalla, mírala: un recorrido con Playwright sobre la build servida por `yarn build && yarn preview`
   (puerto 5182; `yarn e2e` la levanta solo) y la API simulada de e2e/api.ts (amplíala si la pantalla necesita datos), con
   capturas a 1440 y 390 px guardadas fuera del repositorio. Compárala con la anterior y con la
   hoja del lienzo que cita screens.md. Si la captura muestra algo que el plan no previó, anótalo en el PR.
6. Antes de cada commit: yarn format && yarn lint && yarn typecheck && yarn test && yarn build && yarn e2e.
7. Abre el PR con: qué etapa, qué pantallas, defectos de §2 que cierra y las preguntas abiertas. `gh` no sube imágenes:
   las capturas antes y después se adjuntan a mano en el PR.

Criterio de terminado: lo de «Aceptación» de la etapa, ninguna prueba existente rota sin una razón escrita en el PR, y
ningún texto nuevo con códigos de error ni identificadores de requisitos (textosSinCodigos.test.ts).
```

## Bloques por etapa

**Etapa 0**

```text
Te toca la etapa 0 (piezas comunes y red de seguridad). Crea en src/portal/componentes/: Pagina.tsx
(ContenedorDePagina, EncabezadoDePagina, useTituloDeDocumento), Migas.tsx, EnlaceDeRegistro.tsx, PestanasDeFicha.tsx,
PanelDeTrazabilidad.tsx (tres grupos: De dónde viene, Qué generó, Lo sustenta), PanelDeDecision.tsx, CaminoDelFlujo.tsx y
BotonCrear.tsx, cada una con su prueba. Agrega el errorElement de la raíz y del layout en PortalApp.tsx y un LimiteDeError
alrededor del panel del asistente, y corrige la causa (useCases?.includes en Asistente.tsx) (D1), con una prueba que haga
fallar un componente hijo y verifique que el resto del portal sigue. EnlaceDeRegistro solo se crea: RegistroVinculado y
EnlaceDeFicha no lo adoptan todavía. No migres pantallas, salvo las pestañas de la ficha de compromiso a PestanasDeFicha
como prueba de la pieza (D6).
```

**Etapa 1**

```text
Te toca la etapa 1 (shell y orientación): sección del módulo marcada dentro del proyecto, rótulo corto de QCS, insignias de
Tareas y Avisos, el selector de proyecto en el panel «Más» del teléfono y en el panel de la tableta, y un chip en la barra
superior (D2), migas en ProyectoLayout, el proyecto sin repetir en el título, las pestañas del proyecto en 9 + «Más» como
las registra ux-architecture §7.1 (decisión 2) con conteos por size=1 y totalElements (decisión 4), document.title en
todas las pantallas (D7) y «Programas sociales» como nombre único (D8, decisión 3).
```

**Etapa 2**

```text
Te toca la etapa 2 (inicio «Mi día», S02). Reordena pages/InicioPage.tsx: Atención requerida (vencidos y por vencer de
SuTrabajo, aprobaciones, avisos ACTION), atajos de creación con BotonCrear, Mis proyectos con tres cifras enlazadas; la
cuenta pasa al menú del perfil y la grilla de secciones solo queda cuando no hay nada de lo anterior. Registra en
inicios.ts las variantes por permisos que pide ux-architecture §5, sin nombres de rol.
```

**Etapa 3**

```text
Te toca la etapa 3 (listas). Migra, en el orden de la tabla de §5, cada lista a EncabezadoDePagina + VistasGuardadas con
conteo + ChipsDeFiltro + búsqueda + TablaDeRegistros + BotonCrear + vacío por filtros, con EnlaceDeRegistro, el proyecto
como «código · nombre» y Persona para el responsable (D11). Reemplaza los truncados por paginación real (D10); la QCS
global, paginada por objeto con pestañas Quejas, Consultas, Solicitudes y «Atención» (decisión 5). Una pantalla por commit.
```

**Etapa 4**

```text
Te toca la etapa 4 (fichas). Migra cada ficha, en el orden de §5, a la anatomía de design-system §6.1 con las piezas de la
etapa 0, cerrando lo que la matriz de §4 marca como «No» o «Propio». Escribe siguienteAccion(objeto, registro,
transiciones) por objeto como función pura con su prueba. Corrige D5 y D9 en la ficha de grupo.
```

**Etapa 5**

```text
Te toca la etapa 5 (crear y editar). Crea FormularioPorPasos (design-system §6.3) y migra Nuevo compromiso (con Origen
como primer paso), Registrar interacción, Registrar QCS, Nuevo proyecto e Importación. Agrega «Crear relacionado» en las
fichas con los valores precargados que lista §5 y unifica el patrón de edición (drawer para pocos campos, página para
muchos). Los códigos sugeridos (decisión 6) solo cuando el backend los ofrezca.
```

**Etapa 6**

```text
Te toca la etapa 6 (conectar módulos): vista previa con ?peek=objeto:id desde listas, mapa y trazabilidad; el rastro
alimentado por EnlaceDeRegistro y RegistroVinculado; cifras que llevan a su lista filtrada (ficha de grupo, Resumen del
proyecto, Mis tareas, tableros); «Ver en el mapa» en las fichas con lugar; el origen de cada tarea como enlace. Cierra con
un e2e del hilo completo de ux-architecture §8.3.
```

**Etapa 7**

```text
Te toca la etapa 7 (tableros y reportes): error compacto en las tarjetas de cifra y un solo aviso cuando falla todo el
tablero (D4), distinguiendo sin permiso de fallo; Reportes en tres vistas (Entregables, Exportaciones, Historial) con el
alcance de la barra superior y rótulos en lugar de códigos; «Lista · Tablero» como vistas en cada módulo, con Riesgos
aterrizando en la lista (decisión 7).
```

**Etapa 8**

```text
Te toca la etapa 8 (responsive y accesibilidad): ningún desplazamiento horizontal de página a 390 px (D3), con un e2e que
lo mida en cada pantalla principal; listas como tarjetas en el teléfono con estado, plazo y proyecto; fichas con las
acciones en una barra inferior fija; revisión de foco en diálogos y drawer.
```

## Para crear los issues

Si se prefieren issues antes que sesiones directas: un issue por etapa con el título «UX · Etapa N: <tema>», el cuerpo con
la sección de la etapa de §5, los defectos de §2 que cierra y su «Aceptación», y la etiqueta `ux`. Los defectos D1, D2 y D10
merecen su propio issue con prioridad alta, porque se pueden arreglar sin esperar a las piezas comunes.
