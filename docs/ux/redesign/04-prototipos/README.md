# Prototipos de alta fidelidad (etapa D)

> Rediseño integral de UX/UI, entregable 4. Fecha: 2026-10-21. Propuesta, pendiente de revisión.

**Cómo están hechos** (DP-02). Cada prototipo es una página React compuesta con los **componentes reales** del portal:
`EstadoChip`, `PanelDeDecision`, `KpiCard`, `Campo`, `FormularioPorPasos`, `Table` y los demás. Son los mismos que se
sincronizaron con Claude Design («SGSPE Design System»). Llevan los textos reales del portal en es-PE e inglés, y los
workflows del modelo.

Los componentes nuevos del rediseño (`Seccion`, `CabeceraDeFicha`, `SelectorDeVista`, `BarraDeFiltros`,
`ListaPriorizada`, `CampoFecha`, `EstadoSinAcceso`) están en versión de prototipo en
`sgspe-ui/.design-sync/prototipos/piezas.tsx`. Su versión de producto se especifica en
[05](../05-especificacion-de-componentes.md) y se escribe con pruebas según el plan
([06](../06-plan-de-implementacion.md)).

El código está en `sgspe-ui/.design-sync/prototipos/`. Para rehacer las capturas, desde `sgspe-ui`, con Node 26 y
Chromium:

```bash
node .design-sync/kit/build.mjs
node .design-sync/prototipos/fotografiar.mjs ../sgspe/docs/ux/redesign/04-prototipos/png
```

Los datos son los del conjunto ficticio de demostración (`model/demo/`, cuentas `@sgspe.test`), así que cada «antes» y
cada «después» muestran el mismo proyecto. El mapa de P-06 es ilustrativo: en el portal es MapLibre.

## Patrones

| Patrón | Después (claro, español) | Variantes | Antes | Resuelve |
|---|---|---|---|---|
| **P-01 Inicio «Mi trabajo»**, gestión social | [p01-inicio-claro-es](png/p01-inicio-claro-es.png) | [oscuro](png/p01-inicio-oscuro-es.png) · [inglés](png/p01-inicio-claro-en.png) | [c.02](../../../manuals/assets/screenshots/user/02-inicio.png) | U-01, U-05, U-11, U-13 |
| **P-01 Inicio**, agente de campo | [p01-inicio-agente-claro-es](png/p01-inicio-agente-claro-es.png) | [teléfono](png/p01-inicio-agente-telefono.png) | [c.41](../../../manuals/assets/screenshots/user/41-campo-inicio.png) | U-01 (el agente ya no ve un tablero de candados) |
| **P-02 Listado con filtros** (compromisos) | [p02-listado-claro-es](png/p02-listado-claro-es.png) | [oscuro](png/p02-listado-oscuro-es.png) · [inglés](png/p02-listado-claro-en.png) · [teléfono](png/p02-listado-telefono.png) | [c.16](../../../manuals/assets/screenshots/user/16-compromisos-lista.png) | U-06, U-17, U-18, U-22 |
| **P-03 Ficha 360 del proyecto** | [p03-proyecto-360-claro-es](png/p03-proyecto-360-claro-es.png) | — | [c.07](../../../manuals/assets/screenshots/user/07-proyecto-resumen.png) | U-05, U-19 |
| **P-04 Formulario complejo** (registrar QCS) | [p04-formulario-claro-es](png/p04-formulario-claro-es.png) | [inglés](png/p04-formulario-claro-en.png) | [c.22](../../../manuals/assets/screenshots/user/22-qcs-registrar.png) | U-07, U-29, U-30, U-40 |
| **P-05 Bandeja de trabajo** (aprobaciones) | [p05-bandeja-claro-es](png/p05-bandeja-claro-es.png) | [inglés](png/p05-bandeja-claro-en.png) | [c.33](../../../manuals/assets/screenshots/user/33-aprobaciones.png) | U-36, flujo F-E |
| **P-06 Visualización geográfica** | [p06-mapa-claro-es](png/p06-mapa-claro-es.png) | [oscuro](png/p06-mapa-oscuro-es.png) | [c.37](../../../manuals/assets/screenshots/user/37-territorio.png) | U-10, U-32 |
| **P-07 Tablero ejecutivo** | [p07-tablero-ejecutivo-claro-es](png/p07-tablero-ejecutivo-claro-es.png) | [oscuro](png/p07-tablero-ejecutivo-oscuro-es.png) | [c.02](../../../manuals/assets/screenshots/user/02-inicio.png), [c.39](../../../manuals/assets/screenshots/user/39-tablero-compromisos.png) | U-07, U-11, U-23 |
| **P-08 Administración** (S53 y equipo) | [p08-administracion-claro-es](png/p08-administracion-claro-es.png) | — | [a.01](../../../manuals/assets/screenshots/admin/01-equipo.png), [a.05](../../../manuals/assets/screenshots/admin/05-admin-inicio.png) | U-08, U-16, U-37 |
| **P-09 Ficha de registro** (compromiso) | [p09-ficha-claro-es](png/p09-ficha-claro-es.png) | [oscuro](png/p09-ficha-oscuro-es.png) · [teléfono](png/p09-ficha-telefono.png) | [c.17](../../../manuals/assets/screenshots/user/17-compromiso-ficha.png) | **U-02**, U-03, U-15, U-25, U-26, U-27 |
| **Estados del sistema** | [pe-estados-claro-es](png/pe-estados-claro-es.png) | — | [c.42](../../../manuals/assets/screenshots/user/42-sin-acceso.png) | U-09, U-33 |

## Qué mirar en cada uno

- **P-01.** El inicio contesta, en orden:
  1. «Lo que me toca hoy», vencido primero y con motivo;
  2. «Esperan su decisión»;
  3. la agenda de la semana;
  4. sus proyectos con lo que pide acción;
  5. la visión ejecutiva, solo con cifras legibles, fecha de cálculo y enlace a la revisión completa.

  El agente de campo ve sus bloques (su tarea vencida, sus visitas, lo que puede tomar y la cola de sincronización), sin
  ninguna cifra «No disponible».
- **P-02.**
  - Una sola fila de pestañas; las vistas («Abiertos 11 · Vencidos 1…») pasan al selector de la barra de filtros.
  - Los filtros aplicados se ven como chips con «Limpiar filtros».
  - La tabla trae el título en una línea con el código debajo, y se quitó «Responsable», que era igual en todas las
    filas.
  - El plazo va junto a la fecha.
  - «Vista previa» se ve al pasar sobre la fila.
  - En el teléfono, la lista pasa a tarjetas.
- **P-03.**
  - Cinco cifras en una franja, coloreadas solo cuando piden acción.
  - «Qué requiere atención» muestra dos entradas por grupo, con su motivo y «Ver los N», en lugar de 15 enlaces
    subrayados.
- **P-04.**
  - El mismo asistente por pasos que compromiso e interacción.
  - Fecha `20/10/2026` y hora `10:15` en 24 h, de Lima.
  - «Qué pasará al registrar» fijo al costado, con los plazos calculados.
  - La confidencialidad explicada junto a la casilla.
  - Bordes de campo a 3.5:1 (U-40).
- **P-05.**
  - La lista a la izquierda y, a la derecha, el aporte con sus ítems, el tope de la campaña, «Antes de aprobar» y la
    decisión con su motivo.
  - Se decide sin abrir la ficha.
- **P-06.**
  - El mapa ocupa el ancho.
  - Las capas (3 elegidas de 24) y la ficha del lugar flotan y se cierran.
  - «Sin mapa base» dice dónde se configura.
- **P-07.**
  - Periodo y fecha de cálculo en el título.
  - Una cifra no disponible se queda como tal, sin ocupar el centro.
  - «Qué es crítico y quién lo atiende» como tabla corta.
  - Barras sólidas, sin trama.
  - La tabla por proyecto no mezcla medidas en una escala.
- **P-08.**
  - El puente entre el portal y la consola, con el aviso de que el modelo vive en el repositorio (U-37).
  - En el equipo, «Terminar la asignación…» sale de las filas y entra a su menú.
- **P-09.**
  - La primaria es **«Registrar cumplimiento»**, la misma «Siguiente acción» del panel. Como está bloqueada, dice qué
    falta («Falta una entrega con conformidad») con el enlace que lo resuelve.
  - «Bloquear» y «Cancelar el compromiso…» pasan a «Más acciones».
  - El estado aparece una vez.
  - Los atributos van en secciones con título, y los vacíos se ocultan tras «Mostrar los 3 campos sin dato».
  - «‹ Volver a Compromisos (7)» conserva los filtros.
- **Estados.**
  - Sin acceso, con su porqué, a quién pedirlo y cómo volver.
  - Vacío con acción.
  - Error con «Reintentar».
  - Conflicto con «Recargar».
  - Reservado, restringido y no disponible.
  - Éxito que dice qué sigue.

## Límites

- Los prototipos no llaman al backend: los datos están escritos en cada pantalla.
- `bg-sg-surface-raised` y `--sg-border-control` (los tokens propuestos en [03](../03-direccion-visual-y-design-system.md)
  §2.2) están simulados en `prototipos/index.html`.
- Los colores de datos, postura y mapa se usan por `var(--sg-…)`, porque `tokens.css` no los expone como utilidades
  (U-43).
- En inglés, los estados de workflow siguen en español: es el comportamiento real del portal (U-42), no del prototipo.
