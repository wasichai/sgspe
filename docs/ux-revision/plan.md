# Plan de mejoras de UX del portal

Revisión del 2026-10-19 sobre `main` de `sgspe-ui` (943fa4d); las rutas `src/…` y `e2e/…` son de ese repositorio. El objetivo: que el portal se entienda a la primera, que sea coherente de una
pantalla a otra, que crear y editar sea directo, que se pase de un registro a sus relacionados sin perder el hilo y que cada
persona vea de un vistazo el trabajo en curso.

## 1. Diagnóstico

La conclusión principal es que **el diseño ya resuelve casi todo y la implementación lo resolvió pantalla por pantalla**.
`docs/ux/` especifica una ficha con panel de decisión y stepper (design-system §6.1), migas y rastro (ux-architecture §8.1),
vista previa en drawer (`?peek=`), conteos en las pestañas del proyecto (§7.1), listas con vistas, chips y conteo (§6.4) e
inicio por rol (§5, ASM-078). El portal tiene las piezas sueltas, pero cada módulo armó su propio encabezado, su propia lista y
su propio panel de trazabilidad. Por eso cada pantalla se aprende de nuevo.

Seis causas explican la sensación de «no se entiende a la primera»:

1. **No hay un «dónde estoy».** No hay migas; el título de la pestaña del navegador es siempre «SGSPE»; dentro de una ficha
   la barra lateral marca «Proyectos» aunque se esté en un compromiso; hay dos filas de pestañas apiladas (las 14 del proyecto
   y las de la ficha); el proyecto se repite en la barra de contexto y en el título.
2. **El inicio no responde «¿qué hago ahora?».** `INICIOS` está vacío (`src/portal/inicios.ts`): quien no ve la revisión
   ejecutiva recibe la bienvenida, que dedica el primer bloque a su cuenta («Roles: 1 rol asignado») y repite como tarjetas las
   secciones de la barra lateral. «Su trabajo» (#49) es un buen comienzo, pero queda entre dos bloques que no ayudan a decidir.
3. **Cada pantalla resuelve a su manera.** 65 archivos dibujan su propio `<h1>`; de unas 15 listas, solo una usa
   `TablaDeRegistros` y tres `ChipsDeFiltro`; hay cuatro estilos de enlace a una ficha (nombre verde sin subrayar, título
   subrayado, código monoespaciado subrayado, «código · nombre» subrayado); hay al menos tres anchos de página (1440 px, el
   centrado de Avisos y el de Reportes, con otro margen); «Exportar» es un botón en Grupos y un enlace en Compromisos.
4. **Crear depende de un selector que no se ve.** Siete pantallas globales dicen «elija su proyecto en el selector» en lugar
   de ofrecer «Nuevo…»; en el teléfono el selector de proyecto no existe (`SelectorDeProyecto`, `hidden md:flex`).
5. **Las fichas no comparten anatomía.** Ninguna tiene el panel de decisión (estado desde cuándo, siguiente acción,
   responsable, vencimiento) ni el camino del flujo. La trazabilidad existe con cuatro nombres y formas («De dónde viene / Qué
   generó», «Lo alimentan», «Sustenta», «Pendientes») y falta en Acuerdo, Consulta y solicitud, Actor y Visita. La matriz de §4
   lo detalla.
6. **La trazabilidad todavía no es navegación.** Los conteos no enlazan («0 compromisos abiertos · 2 QCS abiertas» en la
   ficha de grupo), el rastro solo lo alimentan tres pantallas (`useSiguientePaso`), no hay vista previa sin salir de la lista,
   las pestañas no tienen conteos y las listas globales se truncan («Se muestran los primeros N…», 35 apariciones) en lugar de
   paginar.

## 2. Defectos

Errores que conviene corregir aunque no se haga nada más. La etapa que los cierra está en la última columna.

| # | Dónde | Qué pasa | Severidad | Etapa |
|---|---|---|---|---|
| D1 | Raíz del router (`PortalApp.tsx`) | Un error de render en cualquier componente tumba todo el portal con la pantalla cruda de React Router («Unexpected Application Error!» y la pila). Se reprodujo con una respuesta de `/api/sgspe/ai/status` sin `useCases` (fuera de contrato, pero basta un despliegue a medias): el panel del asistente (`Asistente.tsx:308`, `estado.data?.useCases.includes`), que vive en el shell, rompe todas las pantallas. La causa de fondo es que no hay `errorElement` en la ruta raíz ni en el layout | Alta | 0 |
| D2 | Teléfono, todas las listas globales | El selector de proyecto se oculta por debajo de `md`, pero Compromisos, QCS, Riesgos, Acuerdos, Programas y Tareas piden «elija un proyecto en el selector»: en el teléfono no hay forma de crear desde ahí | Alta | 1 |
| D3 | 390 px: lista de compromisos y ficha de compromiso | Desbordamiento horizontal de la página (la captura sale de 440 px); en la ficha, las pestañas internas y la barra de «Ejecutado» salen del viewport | Media | 8 |
| D4 | Tarjetas de cifras (Resumen del proyecto, QCS, Riesgos) | Un 404 de un tablero se dice «No se pudo cargar esta información. Los filtros se conservan. El registro no existe o no es de sus proyectos.» en rojo y en cada tarjeta: es el texto de una ficha, no de una cifra, y repetido cinco veces tapa la pantalla | Media | 7 |
| D5 | Ficha de grupo de interés (`FichaGrupoPage.tsx`, líneas 154 y 221) | «Interés NaN · Poder NaN» cuando el grupo no trae `interest_index`/`power_index`: `numeroPe(Number(undefined))`. Debe decir «—» | Media | 4 |
| D6 | Fichas de compromiso e interacción | Las pestañas de la ficha (`Tabs` de wasichai) se recortan («Activid…») sin flecha ni indicio de que hay más; el proyecto sí usa `PestanasDesplazables` | Media | 0 |
| D7 | Todas | `document.title` no cambia: el historial, las pestañas del navegador y los marcadores dicen «SGSPE» | Media | 1 |
| D8 | Barra lateral, pestaña del proyecto y título | La misma sección se llama «Programas sociales» (barra), «Programas e inversión» (pestaña y título); «Quejas, consultas y solicitudes» se trunca en la barra («Quejas, consultas y solici…») | Baja | 1 |
| D9 | Encabezado de la ficha de grupo | Un guion suelto «— Ver el actor» cuando no hay territorio | Baja | 4 |
| D10 | Listas globales (35 textos `truncado`) | Se muestran los primeros N registros del alcance y se pide elegir un proyecto para ver el resto: con datos reales, la lista global miente por omisión | Alta | 3 |
| D11 | Listas con responsable (Compromisos, QCS) | Sin nombre resuelto, se ve el correo partido en dos líneas («smg / @sgspe.test») | Baja | 3 |
| D12 | Biblioteca de documentos | La fila abre la evidencia, pero el título no parece enlace: no hay indicio de que se pueda abrir | Baja | 3 |

## 3. Inconsistencias que confunden

| Tema | Hoy | Debe ser |
|---|---|---|
| Ubicación | Sin migas; barra lateral marca «Proyectos» en cualquier ficha; «Compromisos del proyecto» como enlace suelto sobre el título | Migas derivadas de la ruta y la sección del módulo marcada en la barra lateral |
| Encabezado de página | Título, descripción, conteo y acciones con distinto orden y peso en cada pantalla; Territorio con cinco botones del mismo peso | `EncabezadoDePagina`: título, alcance y conteo, una acción primaria, secundarias y «Más» |
| Enlace a un registro | Cuatro estilos | `EnlaceDeRegistro`: código monoespaciado gris + título como enlace, en todas las listas y paneles |
| Listas | Filtros como selects sin «Limpiar»; búsqueda en unas sí y en otras no; vistas sin conteo; sin vacío por filtros | `TablaDeRegistros` + `VistasGuardadas` con conteo + `ChipsDeFiltro` + búsqueda, en todas |
| Crear | En el proyecto, «Nuevo…» arriba a la derecha; en lo global, un texto que manda al selector | `BotonCrear` en todas: dentro de un proyecto crea ahí; en lo global abre un diálogo que pide el proyecto (con los de la persona primero) |
| Formularios por pasos | Nuevo compromiso: título arriba, pasos a la izquierda, «Anterior» deshabilitado; Registrar interacción: título en la columna de pasos, sin «Anterior», sin «Cancelar»; ninguno tiene «Guardar y salir» | `FormularioPorPasos` común (design-system §6.3) |
| Aterrizaje del módulo | Riesgos abre el tablero; Compromisos y QCS abren la lista con un enlace al tablero | Todos abren la lista de trabajo con «Lista · Tablero» como vistas del mismo módulo |
| Fichas | Ver §4 | La anatomía de design-system §6.1 |
| Ancho de página | 1440 px en la mayoría; Avisos centrado y angosto; Reportes con otro margen | `ContenedorDePagina`: ancho único, con variante «lectura» solo para textos largos |
| Proyecto en listas | «PRJ-01» solo con el código | «PRJ-01 · Pukaqaqa», o solo el nombre si cabe, y enlazado al Resumen |
| Selector de proyecto duplicado | Territorio y Reportes tienen su propio «Proyecto» además del de la barra superior | Usan el alcance de la barra; si necesitan otro, lo dicen («Proyecto del mapa: el de la barra superior») |

## 4. Matriz de fichas

Lo que hoy tiene cada ficha frente a la anatomía de design-system §6.1. «Propio» quiere decir que existe pero con otra forma.

| Ficha | Migas | Encabezado común | Panel de decisión | Camino del flujo | Pestañas desplazables en la URL | Trazabilidad | Historia | Preguntar a la IA |
|---|---|---|---|---|---|---|---|---|
| Compromiso | No | Propio | No | No | No | Sí (aside) | Sí | No |
| Interacción | No | Propio | No | No | No | Sí | Sí | Sí |
| Grupo de interés | No | Propio | Propio (4 tarjetas) | No | Sin pestañas | Propio | No | No |
| Actor | No | Propio | No | — | Sin pestañas | No | No | No |
| Acuerdo | No | Propio | No | No | Sin pestañas | No | Sí | No |
| Queja | No | Propio | No | No | No | Sí | Sí | No |
| Consulta y solicitud | No | Propio | No | No | No | No | Sí | No |
| Riesgo | No | Propio | No | No | No | Propio («Lo alimentan») | Sí | No |
| Programa | No | Propio | No | No | No | Sí | Sí | No |
| Aporte | No | Propio | No | No | No | Sí | Sí | No |
| Evidencia | No | Propio | — | — | Sin pestañas | Propio («Sustenta») | No | No |
| Comunidad, localidad | Rastro | Propio | — | — | No | No | No | No |
| Visita de campo, área de influencia, entregable | No | Propio | No | Propio | Sin pestañas | No | No | No |

## 5. Plan por etapas

Cada etapa es una rama y un PR, con su prueba primero, y deja el portal mejor aunque las siguientes no lleguen. Las etapas 0 a
2 son las que más cambian la primera impresión; la 0 es requisito de las demás.

### Etapa 0 · Piezas comunes y red de seguridad

Objetivo: tener las piezas de patrón antes de tocar pantallas, para que cada migración sea reemplazar, no rediseñar.

- `componentes/Pagina.tsx`: `ContenedorDePagina` (ancho único `max-w-[1440px]`, variante `lectura`) y `EncabezadoDePagina`
  (`titulo`, `antetitulo` con tipo y código, `chips`, `descripcion`, `conteo` con alcance, `primaria`, `secundarias`, `mas`).
  Pone `document.title` como «Título · Proyecto · SGSPE» (`useTituloDeDocumento`).
- `componentes/Migas.tsx`: migas derivadas de la ruta y del registro abierto (Proyectos / PRJ-01 Pukaqaqa / Compromisos /
  COM-2026-031). Distintas del rastro, que sigue siendo el recorrido.
- `componentes/EnlaceDeRegistro.tsx`: un estilo único (código en `font-mono text-sg-text-muted` + título como enlace) que
  alimenta el rastro (`useSiguientePaso`) al navegar. En esta etapa solo se crea: `RegistroVinculado`, `EnlaceDeFicha` y las
  listas lo adoptan en las etapas 3, 4 y 6.
- `componentes/PestanasDeFicha.tsx`: sobre `PestanasDesplazables`, con la pestaña en la URL (`?tab=`), conteo opcional por
  pestaña y desplazamiento con flechas. Cierra D6.
- `componentes/PanelDeTrazabilidad.tsx`: un aside con tres grupos fijos, «De dónde viene», «Qué generó» y «Lo sustenta»
  (design-system §6.1 y ux-architecture §8.1), cada uno con secciones `{ titulo, objeto, registros | consulta, vacio }`; en el
  teléfono pasa bajo el contenido.
- `componentes/PanelDeDecision.tsx`: cuatro tarjetas fijas (estado actual desde cuándo y quién, siguiente acción con lo que
  falta, responsable, vencimiento con `SituacionDePlazo`). La siguiente acción la decide cada ficha con una función pura
  (`siguienteAccion(objeto, registro, transiciones)`), probada aparte. En esta etapa «desde cuándo» y «quién» llegan por
  props y se dibujan «—» si faltan; de dónde se leen se resuelve en la etapa 4.
- `componentes/CaminoDelFlujo.tsx`: el camino del workflow (estados de `GET /objects/:o/workflow`, el actual marcado, los
  terminales aparte), compacto y sin animación.
- `componentes/BotonCrear.tsx`: dentro de un proyecto navega a `…/new`; con el alcance global abre un diálogo que pide el
  proyecto (los de la persona primero, solo donde puede escribir con `useEscribeEnProyecto`) y luego navega.
- `PortalApp.tsx`: `errorElement` en la ruta raíz y en el layout con un estado de error en lenguaje del dominio, el id de
  correlación cuando el error es un `ErrorDeSgspe` (`datos/http.ts`; un error de render no lo tiene) y «Volver al inicio»;
  el panel del asistente envuelto en `LimiteDeError` para que su fallo no tumbe el shell, y la causa corregida
  (`useCases?.includes` en `Asistente.tsx`). Cierra D1.

Aceptación: cada pieza con su prueba en `componentes/*.test.tsx`; ninguna pantalla cambia todavía salvo el `errorElement` y
las pestañas de la ficha de compromiso (D6); `boundaries.test.ts` sigue verde.

### Etapa 1 · Shell y orientación

- **Barra lateral:** marca la sección del módulo también dentro del proyecto (`/projects/:id/commitments/…` marca
  Compromisos, no Proyectos). Rótulos cortos donde no caben: «QCS» con el nombre completo en `title` y para lectores (D8).
  Insignias con lo que pide acción: Tareas (vencidas y por vencer de la persona) y Avisos ACTION; el resto, después, si hay un
  conteo barato.
- **Nombres:** un solo nombre por sección en barra, pestaña y título: «Programas sociales» (decisión 3).
- **Proyecto en el teléfono y la tableta:** el panel «Más» (teléfono, < 640 px) y el panel del menú (tableta, 768 a 1023 px,
  ux-architecture §7.1) muestran el selector de proyecto arriba, y la barra superior un chip con el proyecto actual que lo
  abre. Cierra D2.
- **Dentro del proyecto:** migas en `ProyectoLayout` en lugar del nombre suelto; el título de la página ya no repite el
  proyecto; las 14 pestañas pasan a 9 visibles y «Más»: Resumen · Mapa · Grupos de interés · Relacionamiento · Compromisos
  (con Acuerdos como vista) · QCS · Riesgos · Programas · Documentos · «Más» (Tareas, Revisión de IA, Reportes, Equipo y
  parámetros), como registra ux-architecture §7.1 (decisión 2). Cada pestaña con dos conteos como dice §7.1, leídos con
  `size=1` y `totalElements` por objeto (decisión 4).
- `document.title` en todas las pantallas con `EncabezadoDePagina` (D7).

Aceptación: `Navigation.test.tsx` y `ShellResponsive.test.tsx` cubren la sección marcada en una ficha, el selector en el
panel del teléfono y el título del documento; e2e `navegador.spec.ts` recorre una ficha con migas.

### Etapa 2 · Inicio «Mi día»

Pantalla S02. Lo primero que se ve responde, en este orden: qué está vencido o por vencer a mi cargo, qué espera mi decisión,
qué cambió en mis proyectos, y cómo creo lo de siempre.

- Arriba, **Atención requerida**: vencidos y por vencer de «Su trabajo» (tareas, compromisos y QCS a cargo), aprobaciones
  pendientes y avisos ACTION sin leer, cada uno enlazado a su ficha. Con nada pendiente, una línea («Nada vence esta semana»).
- **Atajos de creación** según permisos (Registrar interacción, Registrar QCS, Nuevo compromiso…) con `BotonCrear`.
- **Mis proyectos**: una tarjeta por proyecto asignado con estado, jefatura y tres cifras que enlazan a su lista filtrada
  (compromisos vencidos, QCS abiertas, riesgos altos). Si son más de seis, los seis con más pendientes y «Ver todos».
- Fuera: la tarjeta «Su cuenta» (pasa al menú del perfil) y la grilla de secciones (duplica la barra lateral; se conserva
  solo si la cuenta no tiene nada de lo anterior, como hoy la bienvenida).
- Variantes por permisos en `inicios.ts` donde el diseño las pide (§5 de ux-architecture): gestión de grupos de interés abre
  con la matriz, SIG con Territorio, aprobación con Aprobaciones. Sin nombres de rol, como manda `boundaries.test.ts`.

Aceptación: `Inicio.test.tsx` y `SuTrabajo.test.tsx` cubren el orden (vencidos primero), los enlaces y el vacío; el e2e abre
el inicio y llega a una ficha en un clic.

### Etapa 3 · Listas

Migrar cada lista a `EncabezadoDePagina` + `VistasGuardadas` con conteo + filtros como chips con «Limpiar» + búsqueda +
`TablaDeRegistros` + `BotonCrear` + vacío por filtros («Sin el filtro de territorio hay 3»). Columnas con `EnlaceDeRegistro`,
el proyecto como «código · nombre» enlazado y el responsable con `Persona` (nombre; sin nombre, el correo en una línea, D11).
Paginación real en lo global en lugar de truncar (D10): el servidor ya pagina; la lista de QCS, que combina tres objetos, se
pagina por objeto con pestañas Quejas, Consultas, Solicitudes y «Atención» (decisión 5). La lista genérica filtraba solo por
igualdad de campo y no por estado, así que las vistas por estado («Abiertos», «Cerrados») no se podían pedir al servidor:
SGSPE suma `state=a,b` y `state_not=a,b` a esa lista (`StateQuery`, `api.md` §3.4).

Orden sugerido, de más usada a menos: Compromisos · QCS · Interacciones (con Calendario como vista) · Grupos de interés ·
Tareas (con Aprobaciones como vista) · Riesgos (con Alertas como vista) · Acuerdos · Programas y aportes · Documentos (D12) ·
Proyectos · Avisos.

Por pantalla:

| Lista | Cambios propios |
|---|---|
| Compromisos | Conteo en las vistas (Abiertos 12, Vencidos 3…); «Exportar» como acción secundaria en el mismo lugar que Grupos; candidatos como vista con su conteo |
| QCS | El enlace va en el código y el asunto (hoy solo el código); «Quién la plantea» con su rótulo, no en minúsculas; tablero como vista |
| Interacciones | Lista y calendario como vistas del mismo encabezado; filtro por grupo de interés y territorio como chips |
| Grupos de interés | Nombre del actor + tipo, como hoy, pero con `EnlaceDeRegistro`; columna de evaluación visible sin desplazar (ocultar Responsable en < 1440) |
| Tareas | Columna Proyecto; «Origen» con `RegistroVinculado`; las cifras de arriba son filtros (clic en «Vencidas» filtra); «Para tomar» visible también en lo global con el proyecto como columna |
| Riesgos | Aterriza en el registro; Tablero, Alertas, Reglas, Menciones, Señales, Impactos como vistas o «Más», no como páginas sueltas |
| Documentos | El título es enlace; vista previa en drawer (etapa 6) |
| Reportes | Ver etapa 7 |

Aceptación: por cada lista, su prueba cubre conteo, chips en la URL, «Limpiar», vacío por filtros y el crear global; el e2e
`hilo.spec.ts` sigue verde.

### Etapa 4 · Fichas

Migrar cada ficha a la anatomía común, en este orden: Compromiso · Queja · Consulta y solicitud · Interacción · Grupo de
interés · Riesgo · Acuerdo · Aporte · Programa · Evidencia · Actor · Comunidad y localidad · Visita · Área de influencia ·
Entregable.

1. Migas (etapa 0) y `EncabezadoDePagina` con antetítulo (tipo · código), título, chips de estado, plazo y prioridad, y a la
   derecha: la acción primaria (la siguiente transición más probable para quien mira, de `BotonesDeTransicion`), Editar como
   secundaria, «Preguntar sobre esto» y «Más».
2. `CaminoDelFlujo` bajo el encabezado.
3. `PanelDeDecision`. Reglas de «siguiente acción» por objeto, con la razón cuando no se puede dar («Falta al menos un
   solicitante para enviar a aprobación», que hoy vive como aviso amarillo al pie de la ficha de compromiso).
4. `PestanasDeFicha` con conteo (Entregas 2, Evidencias 5) y la pestaña en la URL.
5. `PanelDeTrazabilidad` con los grupos fijos. Cada ficha declara sus secciones; las que faltan hoy: Acuerdo (compromisos que
   lo implementan, partes, interacción de origen), Consulta y solicitud (pregunta de origen, compromiso o aporte creado),
   Actor (grupos de interés por proyecto), Visita (interacciones y observaciones), Riesgo (pasa «Lo alimentan» a «De dónde
   viene» y suma medidas en «Qué generó»).
6. Historia y auditoría legible al final (design-system §6.6), en todas.

«Desde cuándo» y «quién» del panel de decisión se leen de `state_changed_at` y `state_changed_by` (decisión 8); mientras el
backend no los tenga, «—». Como todo derivado, nunca van en una escritura del portal.

Defectos propios: D5 y D9 en Grupo de interés; en Compromiso, «Ejecutado: — de S/ 25,000.00» con una barra vacía se vuelve
«Sin ejecución registrada» sin barra hasta que haya monto.

Aceptación: una prueba por ficha que verifica encabezado, panel de decisión con su siguiente acción, conteos de pestañas y
cada sección de trazabilidad enlazada; `fichasFueraDeAlcance.test.tsx` sigue verde (lo ilegible sigue siendo «Registro
reservado»).

### Etapa 5 · Crear y editar

- `FormularioPorPasos` común (design-system §6.3): título y descripción arriba, pasos a la izquierda (arriba en el teléfono),
  «Anterior», «Siguiente», «Cancelar» y «Guardar y salir» siempre en el mismo lugar; aviso al salir con cambios; borrador
  guardado como hoy en el alta de compromiso. Migrar Nuevo compromiso, Registrar interacción, Registrar QCS, Nuevo proyecto
  y la importación geoespacial.
- Orden de pasos del compromiso como el diseño: Origen primero (de dónde nace condiciona el resto).
- **Crear desde el contexto**, con lo que ya se sabe precargado: desde una interacción, «Registrar compromiso» con
  `origin_engagement`; desde un grupo de interés, «Registrar interacción» con el grupo; desde una solicitud aceptada, el
  compromiso o el aporte; desde un riesgo, la medida; desde una comunidad en el mapa, la interacción con su lugar. Un menú
  «Crear relacionado» en el encabezado de la ficha reúne estas acciones.
- Editar: un solo patrón por tamaño. Pocos campos, drawer con `FormularioDeRegistro`; muchos, la página del formulario.
  Hoy conviven botones de texto gris («Editar la caracterización») con botones con borde.
- Códigos (`code` es TEXT obligatorio en commitment, agreement, grievance, social_risk): proponer el siguiente código en el
  campo (prefijo y año) en lugar de dejarlo vacío. El backend lo propone por proyecto y objeto (decisión 6); el portal lo
  muestra editable cuando el endpoint exista.

Aceptación: pruebas del formulario común (navegación, salida con cambios, borrador) y una por alta migrada; el e2e
`compromisos.spec.ts` crea desde una interacción y llega a la ficha con el origen enlazado.

### Etapa 6 · Conectar módulos

- **Vista previa en drawer** (`?peek=objeto:id`, ux-architecture §8.1) desde listas, mapa y panel de trazabilidad: encabezado,
  panel de decisión y «Abrir ficha». Un drawer reemplaza a otro, nunca se apila.
- **Rastro en todo enlace**: `EnlaceDeRegistro` y `RegistroVinculado` agregan el paso; el rastro se ve en todas las fichas.
- **Cifras que enlazan**: «Pendientes» de la ficha de grupo, las tarjetas del Resumen del proyecto, las de Mis tareas y las de
  los tableros llevan a la lista con el filtro puesto (design P8).
- «Ver en el mapa» en toda ficha con lugar; «Trabajar en el proyecto con este lugar» ya existe en Territorio y es el modelo.
- Tareas: el origen de cada tarea es un enlace con su tipo («Acta · Asamblea de agosto»).

Aceptación: e2e del hilo completo de ux-architecture §8.3 (mapa → comunidad → grupo → interacción → compromiso → evidencia
en drawer → volver por el rastro) sin perder proyecto ni territorio.

### Etapa 7 · Tableros y reportes

- Tarjeta de cifra con error compacto: «No disponible · Reintentar» y el detalle en `title`; un solo aviso por tablero cuando
  fallan todas (D4). Distinguir «sin permiso» (candado, «no disponible», RQ-DSH-032) de «falló».
- Reportes en tres vistas: Entregables · Exportaciones · Historial. Las exportaciones usan el alcance de la barra superior en
  lugar de su propio selector por tarjeta; los niveles de divulgación con su rótulo (sin «INTERNO», «PUBLICO», «GI-» en el
  texto); el tipo de entregable con su rótulo (no `FIELD_REPORT`); el título del entregable enlaza a su página.
- Cada módulo con «Lista · Tablero» como vistas del mismo encabezado (ver §3).

### Etapa 8 · Responsive y accesibilidad

- Sin desplazamiento horizontal de página a 390 px (D3): una prueba e2e por pantalla principal mide
  `document.documentElement.scrollWidth <= innerWidth`.
- Listas en el teléfono como tarjetas con título, estado, plazo y proyecto (hoy la tabla muestra solo código y título y pierde
  el estado).
- Fichas en el teléfono: acciones en una barra inferior fija, panel de decisión arriba, trazabilidad al final.
- Revisión de foco y orden de tabulación en los diálogos de transición y en el drawer.

## 6. Decisiones

Tomadas el 2026-10-19 con la propuesta de esta tabla; la 8, a partir de la etapa 0.

| # | Decisión | Tomada |
|---|---|---|
| 1 | Barra lateral dentro de un proyecto: ¿se contrae a íconos (ux-architecture §7.1) o queda igual y marca el módulo? | Marcar el módulo ya (etapa 1) y contraer después si sigue habiendo ruido |
| 2 | Agrupar las 14 pestañas del proyecto en 9 + «Más» | Sí; actualizar §7.1 de `docs/ux/ux-architecture.md` con la agrupación |
| 3 | Nombre de la sección 9 | «Programas sociales» en barra, pestaña y título (es el de RNF-USA-001) |
| 4 | Conteos en pestañas y en vistas | Primero con `size=1` y `totalElements` por objeto; si son muchas llamadas, un endpoint de conteos del proyecto en `sgspe` |
| 5 | Lista global de QCS paginada | Paginación por objeto con pestañas Quejas, Consultas, Solicitudes y «Atención» con los vencidos de los tres, o un endpoint unificado |
| 6 | Códigos sugeridos | El backend propone el siguiente código por proyecto y objeto; el portal lo muestra editable. Hecho: `GET /api/sgspe/codes/next` (`PREFIJO-AAAA-NNN`) |
| 7 | Aterrizaje de Riesgos | La lista (registro y matriz); el tablero como vista |
| 8 | «Desde cuándo y quién» del panel de decisión | Dos campos derivados (`"derived": true`) en cada objeto con workflow de `model.json`: `state_changed_at` (DATETIME) y `state_changed_by` (el correo de quien dio la transición; vacío si fue una automatización). Los escribe `sgspe` en cada transición y se llenan desde la auditoría para los registros que ya existen. Sirven a la ficha y a las listas («en este estado hace N días», orden). Es un plan del backend previo a la etapa 4 |

Las decisiones 1, 2 y 7 cambian `docs/ux/` (que gana sobre el portal) y ya están registradas en ux-architecture §7.1; el
panel de trazabilidad con tres grupos («Lo sustenta»), en design-system §6.1 y ux-architecture §8.1.

## 7. Medir que mejoró

- Recorridos guiados de 5 minutos con dos personas que no conozcan el portal: «registra una interacción con la junta
  directiva de Tunas Pampa», «¿qué compromisos están vencidos en Pukaqaqa y de quién son?», «desde la queja QCS-2026-007,
  llega a la evidencia que la cerró». Contar clics, vueltas atrás y dudas, antes y después de las etapas 0 a 3.
- En el código: archivos con `<h1>` propio (hoy 65) que no pasan por `EncabezadoDePagina`; listas fuera de
  `TablaDeRegistros` (hoy ~14); textos `eligeProyecto` (hoy 7) y `truncado` (hoy 35). Una prueba de límites puede vigilar los
  dos primeros cuando la migración termine, como `boundaries.test.ts`.
