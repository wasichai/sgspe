# ADR-0011: Auditoría sobre el `audit_log` de wasichai

## Estado

Aceptado · 2026-10-07

## Contexto

- El prompt pide responder quién, qué, cuándo, por qué, desde y hacia en cada cambio importante, sin depender solo de
  logs, con un `AuditEvent` append-only que lleve también origen y `correlationId` [PRM-01 §5, §18].
- wasichai ya registra toda escritura de registros en `audit_log`, append-only, con usuario o cuenta de servicio, objeto,
  registro, operación, cambios antes y después filtrados por permisos de campo y motivo [WAS-ADR-0040] [WAS-ADR-0041]
  [WAS-ADR-0043]. Las transiciones son `UPDATE` con su motivo [WAS-ADR-0013], y emitir un documento es `ISSUE`
  [WAS-ADR-0023]. Esa decisión es de la plataforma; aquí no se repite.
- Lo que falta para el prompt son brechas de wasichai 0.3.2 (CON-057): no audita la administración de usuarios, roles ni
  metadata (UPS-02), no guarda correlation id ni origen (UPS-03), `/api/audit` no pagina ni filtra por fecha o usuario
  (UPS-05) y `audit_log` no es inmutable en la base (UPS-12). Tampoco audita lecturas (GAP-031) ni coordenadas: una
  geometría figura como cambiada, sin valores (RQ-AUD-013).
- Las salidas de datos (exportaciones XLSX, emisiones y descargas de entregables) no son escrituras de registros y no
  dejan rastro en `audit_log` por sí solas (RQ-AUD-011).

## Opciones consideradas

### Auditoría propia de SGSPE

- A favor: un `AuditEvent` con todos los campos del prompt, origen y correlación incluidos.
- En contra: duplica lo que la plataforma ya escribe en la misma llamada; exigiría escribir fuera de `RecordService`,
  con `AuditService` o tablas propias, lo que prohíben RNF-MNT-005 y la regla "sin Envers" (CON-039).

### Solo logs estructurados

- A favor: barato y ya necesario para operar.
- En contra: el prompt lo prohíbe expresamente y un log no es evidencia de negocio (RQ-AUD-014).

### `audit_log` de wasichai más convenciones y un registro de salidas propio

- A favor: una sola fuente para las escrituras, escrita por la plataforma en la misma llamada o transacción; SGSPE solo
  decide qué objetos exigen motivo o son historia, y agrega lo que la plataforma no cubre.
- En contra: hereda las brechas UPS-02, UPS-03, UPS-05 y UPS-12 hasta que se resuelvan upstream.

## Decisión

SGSPE audita con el `audit_log` de wasichai y agrega solo lo propio ([security.md §13](../security.md)):

| Pieza | Qué decide SGSPE | Requisitos |
|---|---|---|
| Motivo obligatorio | `requiresReason` en los objetos que registran decisiones o compromisos con terceros: `commitment`, `agreement`, `grievance`, `inquiry`, `stakeholder_request`, `social_risk`, `stakeholder_assessment`, `social_contribution` y `deliverable`. En los demás objetos, `TransitionReasonGuard` exige motivo en las transiciones de devolver, rechazar, suspender, cancelar, retirar y reabrir | RQ-AUD-002, ASM-058 |
| Historia inmutable | `appendOnly` en `stakeholder_assessment`, `commitment_reschedule`, `risk_assessment`, `program_measurement`, `indicator_measurement`, `deliverable_version`, `export_log` e `ai_interaction`, y un registro al que apunta uno de ellos no se borra, con la comprobación bajo candado de fila [WAS-ADR-0044]; `evidence` es `apiOnly` con guarda de inmutabilidad, por sus vínculos MANY_TO_MANY (ADR-0014) | RQ-AUD-003, RF-DOC-001 |
| Reglas por endpoint | Las escrituras con reglas de varios registros pasan por objetos `apiOnly` y endpoints de `/api/sgspe/**`, cuyas escrituras quedan auditadas como el usuario (ADR-0008) | RQ-AUD-004 |
| Registro de salidas | `export_log`, `appendOnly` y `apiOnly`: cada exportación, emisión y descarga de un archivo de entregable, con `export_kind`, quién, objeto, formato, nivel de divulgación, filtros, filas y versión cuando corresponde | RQ-AUD-011 |
| Origen del cambio | Hasta UPS-03 se codifica en el motivo: `importación <id>`, `automation '<nombre>'`, `derivado: <regla>` y el nombre del trabajo que escribe como la plataforma | RQ-AUD-007, ASM-051 |
| Correlación | `X-Correlation-Id` en peticiones, respuestas y logs, todavía no en la fila (ADR-0026) | RQ-AUD-006, ASM-034 |
| Decisiones de IA | La persona que acepta, rechaza o marca como duplicado, y el comando que ejecutó, quedan auditados (ADR-0004) | RQ-AUD-012 |
| Coordenadas | La historia de las posiciones se conserva con los datos de origen de cada importación (ADR-0019) | RQ-AUD-013 |

- **Nadie escribe la auditoría a mano.** Ningún código de SGSPE usa `AuditService` ni escribe en `audit_log`; una prueba de
  arquitectura lo verifica (RNF-MNT-005) y es la mitigación de UPS-12 (RQ-AUD-010).
- **El modelo y los roles cambian solo desde el repositorio** (ADR-0007), con su historia en git: es la mitigación de
  UPS-02 (RQ-AUD-008, GAP-030).
- **Las escrituras críticas se hacen desde el portal**, que pide el motivo; `/admin` no lo pide todavía (UPS-08).
- **Consulta:** `/api/audit` para `AUDITOR` y `ADMIN`, hasta 500 filas sin filtros por fecha o usuario (UPS-05); el resto
  ve el historial de cada registro en su ficha (RQ-AUD-005, RQ-AUD-009). `export_log` lo leen `SOCIAL_MANAGER` y `AUDITOR`.
- **Lecturas:** no se auditan (GAP-031); sí las salidas de datos, en `export_log`.

## Consecuencias

### Positivas

- Las seis preguntas del prompt se responden desde la base, para toda escritura y sin código de auditoría propio.
- Las salidas de datos personales quedan registradas aunque la plataforma no audite lecturas.
- Cada brecha tiene una mitigación que se retira sola cuando llega la solución upstream ([platform-fit.md §4.1](../platform-fit.md)).

### Negativas y riesgos

- La correlación y el origen viven en los logs y en un texto convencional del motivo, no en columnas (UPS-03).
- La administración de usuarios y permisos sigue sin auditar en la base (UPS-02); el alta de usuarios en `/admin` no deja
  rastro.
- `AUDITOR` no ve los campos restringidos ni sus cambios; decidirlo es parte de GAP-024.
- `audit_log` puede alterarse con acceso directo a la base hasta UPS-12.

### Seguimiento

- Abiertos: UPS-02, UPS-03, UPS-05, UPS-08, UPS-12, GAP-024, GAP-031; issues upstream solo con aprobación (GAP-028).
- Rige desde F1 (consulta y contrato), F2 (motivo, historia y `export_log`) y F8 (emisiones y descargas).
- Pruebas: `AuditApiTest` (seis preguntas, motivo con tildes, `appendOnly` con 409 sin auditar), `ObservabilityApiTest`,
  `ArchitectureTest`, y las de "Salidas" y "Descargas" de [security.md §14](../security.md).

## Referencias

- Requisitos: RQ-AUD-001, RQ-AUD-002, RQ-AUD-003, RQ-AUD-004, RQ-AUD-005, RQ-AUD-006, RQ-AUD-007, RQ-AUD-008, RQ-AUD-009,
  RQ-AUD-010, RQ-AUD-011, RQ-AUD-012, RQ-AUD-013, RQ-AUD-014, RNF-MNT-005, RF-DOC-001, CON-039, CON-057, ASM-034, ASM-051,
  ASM-058, UPS-02, UPS-03, UPS-05, UPS-08, UPS-12, GAP-024, GAP-028, GAP-030, GAP-031.
- Documentos: [security.md §13 y §14](../security.md), [architecture.md §7](../architecture.md),
  [platform-fit.md §2.13 y §4](../platform-fit.md), [domain-model.md §4.5 y §6.17](../domain-model.md),
  [deliverables.md §9.5](../deliverables.md), [requirements-analysis.md §15](../requirements-analysis.md).
- wasichai: `WAS-ADR-0013` (Workflow state lives on the record, transitions are the only way to move it), `WAS-ADR-0023`
  (A document is frozen when it is issued), `WAS-ADR-0040` (Append-only objects, a pre-write guard SPI, and api-only
  objects), `WAS-ADR-0041` (A change reason on record writes, required per object), `WAS-ADR-0043` (Service accounts sign
  in with client credentials, as a user row that is not a person), `WAS-ADR-0044` (The append-only delete check runs under
  a row lock, in one transaction with the delete).
- ADR de SGSPE: ADR-0004, ADR-0007, ADR-0008, ADR-0014, ADR-0019, ADR-0026.
- Fuentes: [PRM-01 §5, §18], [USR-07].
