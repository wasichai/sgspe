# Plan de implementación por fases

> Rediseño integral de UX/UI, entregable 7. Fecha: 2026-10-21. Propuesta, pendiente de revisión.
> El detalle tarea por tarea, con casillas y prueba primero, está en
> [`docs/superpowers/plans/2026-10-21-rediseno-ux-portal.md`](../../superpowers/plans/2026-10-21-rediseno-ux-portal.md).
> Nada de esto se ejecuta hasta que el usuario apruebe el rediseño (DP-05).

## 1. Principios del orden

1. **Primero lo que hoy lleva a un error.** Los defectos P1 que se arreglan sin diseño nuevo van en la fase 1, cada uno
   en un commit `fix` propio: U-04 y la causa de U-01.
2. **Después las piezas, y luego su uso.** Un componente transversal se escribe, se prueba y se adopta en **una**
   pantalla piloto. Se replica a las demás en la fase 4. Así, si una pieza falla, se corrige una vez.
3. **Cada fase deja el portal entregable.** Cada fase termina con la suite completa en verde
   (`yarn format && yarn lint && yarn typecheck && yarn test && yarn build && yarn e2e`) y con un release propio por
   release-please.
4. **Sin cambios de contrato en el camino crítico.** Las fases 1 a 5 no dependen de backend ni de `@wasichai/*`. Lo que sí
   depende está en [08](08-cambios-de-contrato-backend.md) y entra en la fase 6, cuando llegue.

## 2. Fases

| Fase | Contenido | Hallazgos | Tamaño | Tipo de commit |
|---|---|---|---|---|
| **1. Defectos P1** | La pestaña y las migas de las fichas fuera de su segmento (`ProyectoLayout.tsx:74,88`, usando `PESTANA_DE_SEGMENTO` de `Migas.tsx:56`). El inicio deja de elegir S51 por el catálogo (`pages/InicioPage.tsx:22-30`): «Mi día» para toda cuenta, con S51 como bloque para quien lee KPI-EXE. La regla de la acción principal en las fichas de compromiso y de riesgo, sin componente nuevo aún. | U-04, U-01, U-02 | S | `fix` |
| **2. Componentes transversales** | `Seccion`, `CabeceraDeFicha`, `SelectorDeVista`, `BarraDeFiltros`, `CampoFecha`, `TablaPriorizada`, `ListaPriorizada` y `EstadoSinAcceso` ([05 §1](05-especificacion-de-componentes.md#1-componentes-nuevos)). Cada uno se adopta en su pantalla piloto: ficha de compromiso, lista de compromisos, registrar QCS y Sin acceso. | U-03, U-07, U-09, U-15, U-18, U-22, U-34 | L | `feat` |
| **3. Tokens y shell** | Copia exacta de `docs/ux/tokens.css` con `cmp` (U-39). `--sg-border-control` y `--sg-surface-raised`, y los tokens de datos expuestos en `@theme inline` (U-43). Barra superior de 6 controles; idioma y tema al menú de la cuenta; navegación expandida desde `lg` con títulos de bloque y «Quejas y solicitudes»; una sola fila de pestañas. | U-12, U-19, U-20, U-21, U-39, U-40, U-41, U-43 | M | `feat` |
| **4. Áreas** | Se replican los patrones: inicio «Mi trabajo» con sus bloques; ficha 360 con `ListaPriorizada`; listados con `BarraDeFiltros` y `TablaPriorizada`; QCS por pasos; bandeja con decisión en el panel; mapa primero; revisión ejecutiva compacta; fichas en secciones con los campos vacíos plegados; textos sin jerga. | U-05, U-06, U-10, U-11, U-13, U-14, U-24 a U-32, U-35, U-36 | XL | `feat` |
| **5. Administración del lado de SGSPE** | Lo que el portal controla sin tocar `@wasichai/*`: el aviso de fuente de verdad en S53 y en el puente hacia `/admin`, y el equipo del proyecto con «Terminar la asignación» en el menú de la fila. | U-16, U-37 (parcial), U-38 (parcial) | S | `feat` |
| **6. Dependencias externas** | Lo de [08](08-cambios-de-contrato-backend.md) a medida que llegue: etiquetas de estado en inglés, el tema de `/admin`, los rótulos de rol y permiso, y la superficie flotante. | U-08, U-37, U-42 | M | `feat` |
| **7. Documentación** | `docs/ux/` absorbe lo aprobado: `ux-architecture.md` (shell, inicio y pestañas), `design-system.md` (tokens nuevos, reglas de espaciado y componentes), `screens/` y `flows/`. `docs/ux/redesign/` queda como historia. | — | M | `docs` |

**Tamaños:** S hasta 1 día, M de 2 a 3 días, L hasta una semana y XL más de una semana, dividida por área.

## 3. Pantallas piloto

| Componente | Piloto | Por qué ahí |
|---|---|---|
| `CabeceraDeFicha` | Ficha de compromiso (S20) | Es donde está U-02, el defecto de la acción principal más visible |
| `SelectorDeVista` y `BarraDeFiltros` | Lista de compromisos | Tiene vistas con conteo, filtros y título largo: reúne U-18 y U-22 |
| `TablaPriorizada` | Grupos de interés | Es la tabla que desborda a 1440 px (U-06) |
| `CampoFecha` | Registrar QCS | Tiene fecha y hora, y su plazo se calcula en días hábiles |
| `ListaPriorizada` | Ficha 360 del proyecto | Es el muro de enlaces de U-05 |
| `EstadoSinAcceso` | `GuardaDeSeccion` | Una sola guarda la usa en todas las secciones |
| `Seccion` | Resumen del proyecto | Tiene más tarjetas a mano |

## 4. Riesgos y cómo se mitigan

| Riesgo | Mitigación |
|---|---|
| Cambiar la acción primaria puede chocar con las pruebas e2e y los manuales | Los manuales se regeneran con su script en la misma fase. Las pruebas e2e se ajustan por rol accesible, no por clase. |
| `CampoFecha` en 33 sitios puede alterar lo que se escribe | CA-1.5.1 compara el instante con `ahoraEnLima`. Se migra pantalla por pantalla, con la prueba de cada formulario. |
| Plegar la navegación cambia la memoria muscular | «Contraer» persiste. Las rutas y el orden de las secciones no cambian. |
| El inicio por bloques podría contar lo que no se lee | Cada bloque usa las lecturas que ya hacen `AtencionRequerida`, `SuTrabajo` y `MisProyectos`, acotadas por el servidor. Una prueba con una cuenta sin lectura de KPI-EXE verifica que no aparece ninguna cifra. |
| Una pantalla puede quedar con dos patrones durante la fase 4 | Cada área se migra completa en un mismo PR. |

## 5. Cómo se mide el resultado

Sin datos de uso, se miden indicadores observables en las pruebas y en las capturas de los manuales, antes y después:

- los pasos de los flujos F-A a F-G ([02 §6](02-arquitectura-de-informacion.md#6-flujos-prioritarios-rehechos));
- el número de controles de la barra superior (de 9 a 6);
- las pantallas con más de una `Button` primaria (meta: 0);
- las tarjetas a mano (de 136 a 0);
- los campos de fecha nativos (de 33 a 0);
- el desborde horizontal a 1440 px en las listas (meta: ninguno);
- los pares de contraste que fallan (meta: 0).

Medido sobre la demo viva el 2026-10-24 (wasichai/sgspe#157), con `docs/manuals/scripts/measure-flows.live-demo.mjs` y
`count-primaries.live-demo.mjs`:

- **Pasos de F-A a F-G:** cada «Medido» es igual o menor que su «Propuesto»:
  - F-A: 1 y 1;
  - F-B: 2;
  - F-C, volver a la lista: 1, con J y K;
  - F-D: 5 y 3 pasos;
  - F-E: 2;
  - F-F: 2;
  - F-G: 2.

  La excepción es el filtro por grupo de interés de F-C: la lista de Compromisos no tiene «Más filtros» (wasichai/sgspe-ui#77).
- **Pantallas con más de una primaria:** 0 de 19 medidas. La ficha de acuerdo no se midió, porque la demo no trae acuerdos.
- **Desborde a 390 px:** ninguno en 16 pantallas, con los seis usuarios. La barra inferior es la misma para todos.

Con uso real, se validan las hipótesis de la auditoría (estado **H**). Para eso se observa a una persona de cada perfil en
F-A, F-B y F-E.
