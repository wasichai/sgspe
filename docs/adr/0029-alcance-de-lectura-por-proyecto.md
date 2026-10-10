# ADR-0029: Alcance de lectura por proyecto con `RecordReadScope`

## Estado

Aceptado · 2026-10-10 · decisión del usuario que cierra CON-041 y GAP-025 (opción B, del lado de SGSPE) y sustituye el
alcance por proyecto de ADR-0006

## Contexto

- Hasta F4 rige la opción A de CON-041 ([security.md §9.2](../security.md)): quien tiene READ sobre un objeto lo lee en
  todos los proyectos del tenant, también en mapas, tableros, historial, documentos y archivos; solo la escritura está
  acotada por `ProjectScopeGuard`. Es aceptable con un proyecto o con equipos que trabajan en todos; no con muchos proyectos
  por cliente y equipos separados, el caso que el usuario prevé.
- wasichai 0.6.0 trae el SPI `RecordReadScope` [WAS-ADR-0048], que SGSPE ya usa para las QCS confidenciales (ADR-0028):
  la plataforma aplica el criterio de la app en listas, totales, detalle, relacionados, destinos de `RELATION`, historial,
  auditoría, transiciones, features GIS, archivos y agente. No se aplica al `ADMIN`, a la plataforma ni a las
  automatizaciones.
- Casi todas las lecturas de SGSPE pasan por `Records` como quien llama, así que tableros, exportaciones, capas y reportes
  heredan el criterio sin cambios. wasichai-documents no aplica el SPI en sus lecturas (UPS-28).
- Ningún rol se nombra en el código (RQ-SEC-005): quién lee todo el tenant tiene que salir de `model/roles.json`.
- Evaluación de factibilidad: `.superpowers/sdd/fase-4/ups-01-evaluacion.md` (2026-10-10).

## Opciones consideradas

### A, como hasta F4

- A favor: nada nuevo.
- En contra: la lectura no tiene frontera por proyecto; inaceptable con equipos separados (§9.2, riesgo residual).

### B con un `RecordReadScope` propio y SQL puro (elegida)

- A favor: rige en todas las rutas de wasichai; el criterio es una subconsulta sobre `project_assignment`, sin lecturas
  desde Kotlin; reversible quitando el componente.
- En contra: un componente que debe fallar cerrado; cambia la semántica de errores (`404` y `400` en lugar de `403`); la
  suite de integración se migra.

### B con una lista de proyectos leída en Kotlin

- En contra: leer `project_assignment` con `Records` dentro del criterio dispara el mismo alcance sobre
  `project_assignment` (recursión), y fuera de un comando no hay `RequestMemo`: se repetiría por cada objeto de una lectura.

### Acotar también los maestros del tenant

- En contra: el modelo no vincula personas, organizaciones, territorio ni línea base con un proyecto; el usuario decidió
  compartirlos (decisión 3 del 2026-10-10, ASM-107).

## Decisión

- **Componente.** `ProjectReadScope` (`sgspe.scope`) implementa `RecordReadScope`, con la condición pura
  `ProjectReadCondition`. «Mis proyectos» es `SELECT project FROM project_assignment WHERE member_user_id = <quien llama>`
  con la vigencia de `Validity.inForce` (hoy en Lima). `project` se lee si está entre mis proyectos; `project_assignment`,
  las propias (aun vencidas) y las de mis proyectos; las 31 raíces con `project`, por él (las tres opcionales, también sin
  él); los 19 hijos, por el proyecto de su padre (los dos de padre opcional, también sin él); los 20 maestros del tenant
  (`ProjectScope.TENANT`), sin restricción. Valores enlazados, nombres de la definición.
- **Exención sin literales.** La acción declarada `READ_ALL_PROJECTS` sobre `project` exime; en `roles.json`, solo
  `AUDITOR` y `PROJECT_ADMIN` (confirmado por el usuario el 2026-10-10). `ANALYST`, `SOCIAL_MANAGER`, `GIS_ANALYST` y
  `READ_ONLY` leen sus proyectos. `ProjectReach` (`sgspe.common`) es la única pieza que decide «mis proyectos» o todos, para
  el alcance, los tableros, el reporte y las exportaciones.
- **Falla cerrado.** Un objeto sin clasificar, una definición sin sus columnas o una consulta de permisos o de definiciones
  que falla no admiten ningún registro y dejan una advertencia sin datos del registro. `ArchitectureTest` exige que cada
  objeto de `model.json` esté clasificado.
- **Documentos emitidos.** `IssueDocumentFilter` cubre `GET .../records/{id}/documents` y `GET /api/documents/{id}`: si quien
  llama no lee el registro del documento, `404` (hasta UPS-28).
- **Composición.** Con `ConfidentialAttentionScope` (ADR-0028), por AND: `READ_CONFIDENTIAL` no abre otros proyectos y
  `READ_ALL_PROJECTS` no abre las confidenciales.
- **Escritura.** `ProjectScopeGuard` se queda como segunda línea: es la única barrera para quien lee todo sin estar asignado.
- **Errores** (ASM-108). Fuera del alcance, `404`; un `RELATION` hacia un registro ilegible, el `400` de wasichai; un vínculo
  M2M con un extremo ilegible, `404`; `NOT_ASSIGNED`, para quien lee sin estar asignado.

## Consecuencias

### Positivas

- Equipos separados por proyecto dentro de un mismo cliente, en todas las rutas de lectura.
- Quién lee todo se cambia en `roles.json`, sin tocar el código.
- Los trabajos siguen como la plataforma, sin alcance; los avisos ya se dirigen por las asignaciones del proyecto.

### Negativas y riesgos

- Maestros compartidos (ASM-107) y oráculos de sí o no: `409 HAS_DEPENDENTS` y los únicos del tenant (ASM-108).
- Ninguna diferencia entre «no existe» y «no es de mis proyectos», en la API ni en el portal.
- Cifras por quien llama: dos personas con asignaciones distintas ven tableros distintos.
- Cada fase nueva clasifica sus objetos en `ProjectScope`; un objeto mal clasificado como maestro quedaría abierto.
- Orden de despliegue: los roles del tenant antes que el backend, o `AUDITOR` y `PROJECT_ADMIN` pierden lectura un rato.

### Seguimiento

- Pruebas: `ProjectReadConditionTest`, `ProjectReadScopeTest`, `ProjectReachTest`, `IssueDocumentFilterTest` (unitarias);
  `ProjectReadScopeApiTest`, las columnas «otro proyecto» de `PermissionMatrixApiTest` y «no asignado» de
  `WorkflowMatrixApiTest` (integración); `ArchitectureTest`; `test_apply_roles.py`.
- UPS-28 (que wasichai-documents aplique el SPI) retira la parte de documentos de `IssueDocumentFilter`.
- Se revierte quitando el `RecordReadScope`: vuelve la opción A de `security.md` §9.2.

## Referencias

- Requisitos: RQ-SEC-005, RQ-SEC-006; CON-041; GAP-025; UPS-01, UPS-06, UPS-28; ASM-095, ASM-106, ASM-107, ASM-108.
- Documentos: [security.md §9.2, §9.6 y §9.7](../security.md), [api.md §3.3](../api.md),
  [domain-model.md §4.7](../domain-model.md), especificación `docs/superpowers/specs/2026-10-10-alcance-por-proyecto-design.md`.
- ADRs: [ADR-0006](0006-autorizacion-y-alcance-por-proyecto.md), [ADR-0028](0028-qcs-confidenciales-con-recordreadscope.md);
  WAS-ADR-0042, WAS-ADR-0048.
