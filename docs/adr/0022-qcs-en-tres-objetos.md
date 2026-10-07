# ADR-0022: Quejas, consultas y solicitudes en tres objetos

## Estado

Aceptado · 2026-10-07

## Contexto

- El prompt habla de "grievance" con un solo ciclo de vida [PRM-01 §4, §5, §8]; DOC-01 habla de quejas, consultas y
  solicitudes, pide el porcentaje absuelto y un reporte [DOC-01 Tabla 9.5] (CON-033).
- Los tres planteamientos siguen caminos distintos:
  - una queja se acusa, evalúa, investiga si hace falta, atiende, resuelve, verifica y cierra, y puede rechazarse, desistirse o
    reabrirse (RF-GRV-002); el prompt la describe con dos listas de pasos que no coinciden (CON-029);
  - una consulta se responde o se deriva, y se cierra (RF-GRV-003);
  - una solicitud se evalúa y, si se acepta, crea un compromiso o un aporte; su cierre sigue al de ese registro
    [DOC-01 §9.3.1.2] (RF-GRV-004, RN-GRV-001).
- wasichai admite un solo workflow por objeto ([workflows.md](../workflows.md), "Principios", punto 2; ASM-011), y su estado es
  una sola columna del registro [WAS-ADR-0013]. Los nombres de objeto en inglés los fija ADR-0009, y las reglas
  comunes de los workflows, ADR-0021.
- Un planteamiento puede llegar mal clasificado: una queja que en realidad es una solicitud (RF-GRV-007).

## Opciones consideradas

### Un objeto `grievance` con un campo de tipo y un solo workflow

- Ventajas: un solo formulario, una sola lista y el reporte unificado gratis; reclasificar sería cambiar un campo.
- Desventajas: un workflow por objeto obliga a mezclar los tres ciclos en una máquina con estados que no aplican a cada tipo, y
  sus guardas tendrían que decidir por tipo; los campos propios (severidad de queja, recurso solicitado, compromiso creado)
  quedarían vacíos en dos de cada tres registros.

### Solo quejas, como el prompt, y las solicitudes como compromisos en borrador

- Ventajas: menos objetos.
- Desventajas: borra la distinción de DOC-01 entre solicitud, compromiso y aporte (RF-GRV-004), y el porcentaje absuelto de
  consultas no tendría de dónde leerse (RQ-DSH-023).

### Tres objetos con campos comunes, un workflow cada uno y un reporte unificado (elegida)

- Ventajas: cada ciclo es simple y se prueba por separado; cada objeto lleva solo sus campos; el reporte y los indicadores leen
  los tres.
- Desventajas: reclasificar es crear otro registro; el reporte unificado es código propio.

## Decisión

- **Tres objetos** de BC-07 (ASM-011): `grievance` (queja, WF-E1), `inquiry` (consulta, WF-E2) y `stakeholder_request`
  (solicitud, WF-E3). Los tres son `requiresReason` y tienen único (`project`, `code`). No existe un objeto "QCS" genérico.
- **Campos y relaciones comunes** ([domain-model.md §6.7](../domain-model.md)): `code`, `channel`, `received_at`, `anonymous`,
  `confidential`, `category`, `priority`, `response`, `responded_at`, plazos y `deadline_status` derivados, `closed_date`
  derivado y `location`; M2O `project`, `stakeholder`, `person`, `locality`, `community`, `topic`, `origin_engagement` y
  `origin_question`. Los propios: `severity`, `resolution` y `complainant_conformity_given` en la queja; `request_medium`,
  `requested_resource` y `created_commitment` (y desde F5 `created_contribution`) en la solicitud.
- **Workflows:** WF-E1 une las dos listas del prompt en una (CON-029); WF-E2 es nuevo y mínimo; WF-E3 termina en el cierre por
  el compromiso o el aporte, o en `close_unfulfilled` (RN-GRV-001, ASM-068). Sus transiciones y guardas
  (`AttentionWriteGuard`) están en [workflows.md](../workflows.md), "WF-E".
- **Plazos** al recibir con `QcsDueDatesListener` y situación en `deadline_status`, nunca como estado (ADR-0012); la situación
  derivada y los escalamientos los mantiene JOB-01.
- **Reclasificación** (RF-GRV-007): `POST /api/sgspe/grievances/{id}/reclassify` y sus pares crean el registro del otro tipo con
  el vínculo `reclassified_from_*` y cierran el original con la transición de comando `close_reclassified_*`. El registro nuevo
  copia el `received_at` del primero de la cadena, así que sus plazos corren desde esa fecha (ASM-072).
- **Aceptación de una solicitud:** `POST /api/sgspe/stakeholder-requests/{id}/accept` crea el compromiso en `draft` (o, desde
  F5, el aporte) y aplica `accept` en la misma transacción; ninguno de los registros se convierte en otro
  ([domain-model.md §3](../domain-model.md)).
- **Anonimato:** una QCS anónima no lleva `person` ni `stakeholder` (GAP-005).
- **Reporte e indicadores:** el reporte unificado (RF-GRV-005) y el porcentaje absuelto (RQ-DSH-023) leen los tres objetos. Una
  QCS es absuelta cuando tiene `responded_at`, y los originales reclasificados no cuentan como recibidos, cerrados ni atendidos
  ([dashboards.md §3.3](../dashboards.md)).

## Consecuencias

### Positivas

- Cada flujo refleja lo que las fuentes y el prompt piden para su tipo, sin estados inaplicables.
- Un planteamiento reclasificado se cuenta una vez y no reinicia sus plazos.
- La solicitud, el compromiso y el aporte quedan vinculados sin confundirse.

### Negativas y riesgos

- Tres fichas, tres listas y tres juegos de permisos en el portal y en `security.md`.
- El reporte unificado y los indicadores deben leer los tres objetos y respetar la regla de los originales reclasificados.
- Que el original se cierre al reclasificar es un supuesto de `workflows.md`: RF-GRV-007 no fija su estado final. Se
  revierte dejándolo abierto hasta que una persona lo cierre.

### Seguimiento

- Abiertos: GAP-005 (plazos, anonimato y apelación, con los valores de ASM-005), ASM-011 (se revierte unificando en un objeto
  con tipo si los flujos convergen, migrando los registros), ASM-068, ASM-072.
- Aceptado; se implementa en F4 (aceptación como aporte, en F5).
- Pruebas de [workflows.md](../workflows.md), "Pruebas": `GrievanceWorkflowApiTest` (reclasificación con vínculo y
  `received_at` del primero de la cadena), `InquiryWorkflowApiTest` y `StakeholderRequestWorkflowApiTest` (`accept` y
  `close_accepted` rechazadas por la API genérica; cierre por el listener).

## Referencias

- Requisitos: RF-GRV-001, RF-GRV-002, RF-GRV-003, RF-GRV-004, RF-GRV-005, RF-GRV-007, RN-GRV-001, RQ-DSH-023; CON-029, CON-033;
  GAP-005; ASM-005, ASM-011, ASM-068, ASM-072.
- Documentos: [domain-model.md §3 y §6.7](../domain-model.md), [workflows.md](../workflows.md) ("WF-E · Quejas, consultas y
  solicitudes (QCS)"), [glossary.md](../glossary.md) ("QCS"), [dashboards.md §3.3 y §4.5](../dashboards.md),
  [api.md §4.6](../api.md).
- ADR de SGSPE: ADR-0009, ADR-0012, ADR-0021, ADR-0023.
- WAS-ADR-0013 (Workflow state lives on the record, transitions are the only way to move it), WAS-ADR-0040 (Append-only
  objects, a pre-write guard SPI, and api-only objects).
- Fuentes: [DOC-01 Tabla 9.5, §9.3.1.2], [PRM-01 §4, §5, §8].
