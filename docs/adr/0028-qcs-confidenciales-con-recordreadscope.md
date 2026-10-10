# ADR-0028: QCS confidenciales con `RecordReadScope`

## Estado

Aceptado · 2026-10-09 · decisión del usuario para F4, que reabre la respuesta del checkpoint «sin restringir en el
MVP»; el alcance por proyecto de ADR-0006 no cambia (sigue la opción A de CON-041)

## Contexto

- Las QCS llevan la marca `confidential` desde su diseño ([domain-model.md §6.7](../domain-model.md)), pero la marca no
  restringía la lectura: la plataforma no tenía alcance por registro (GAP-024) y, con la opción A de CON-041, todos los
  roles operativos menos `PROJECT_ADMIN` leen todas las QCS ([security.md §9.2](../security.md)). El checkpoint lo
  aceptó como riesgo residual del MVP.
- Quien plantea una QCS confidencial puede temer represalias. Su `person` está restringida (`security.md` §7.9), pero
  el registro, con su descripción y su vínculo `stakeholder`, lo leía todo rol con READ.
- wasichai 0.6.0 trae el SPI `RecordReadScope` [WAS-ADR-0048]: para cada usuario y objeto, la app devuelve un criterio
  que la plataforma aplica en listas, detalle, relacionados, historial, auditoría, transiciones, features GIS y agente.
  No se aplica al `ADMIN` ni a la plataforma.
- El usuario decidió el 2026-10-09 (decisión 2 de la especificación de F4) que una QCS confidencial la lean solo
  `GRIEVANCE_MANAGER`, su responsable y `ADMIN`, y la amplió a quien la recibió y, en una queja ALTA o CRITICA, a
  `APPROVER`, que debe resolverla (RN-GRV-002).
- Ningún rol se nombra en el código (RQ-SEC-005): quién ve una QCS confidencial tiene que salir de `model/roles.json`.
- Al cerrarse un compromiso, `RequestClosureListener` cierra la solicitud aceptada que lo originó (RN-GRV-001, ASM-068)
  y corre como quien cierra. Si esa solicitud es confidencial y quien cierra no la lee, wasichai no la muestra y
  quedaría en `accepted` para siempre. wasichai no deja ser la plataforma dentro de una petición
  (`RecordService.asPlatform` lanza) ni aplicar una transición sin usuario (`WorkflowService.apply`), así que ni un
  trabajo de fondo podría aplicar `close_accepted`.

## Opciones consideradas

### `confidential` sin efecto en la lectura, como respondió el checkpoint

- A favor: nada propio; ninguna ruta de lectura cambia.
- En contra: no protege a quien plantea y contradice la decisión del usuario.

### Filtrar en SGSPE con `RecordQueryContributor` y guardas

- A favor: no depende de un SPI nuevo.
- En contra: `RecordQueryContributor` solo estrecha listas con su parámetro; el detalle, el historial, los
  relacionados, las features y el agente seguirían mostrando la QCS ([security.md §9.1](../security.md)).

### El alcance por proyecto completo, con la opción B de CON-041

- A favor: el mismo SPI resolvería UPS-01.
- En contra: cambia la lectura de todos los objetos y espera la decisión del checkpoint sobre UPS-01; es mucho más que
  lo que pide F4.

### Un `RecordReadScope` solo para las QCS confidenciales, con una acción declarada (elegida)

- A favor: rige en todas las rutas de lectura de wasichai; el resto del tenant sigue en la opción A; quién ve sale de
  `roles.json` (`READ_CONFIDENTIAL` y `APPROVE`), sin literales de rol.
- En contra: un componente propio que debe fallar cerrado; los tableros, el reporte y las exportaciones cuentan
  distinto según quién llama; los trabajos y los avisos tienen que filtrar lo que producen.

## Decisión

- **Quién ve una QCS confidencial** (`grievance`, `inquiry` o `stakeholder_request` con `confidential` verdadero):
  `ADMIN`, porque wasichai no le aplica los alcances; quien tiene la acción declarada `READ_CONFIDENTIAL` sobre el
  objeto (en `roles.json`, solo `GRIEVANCE_MANAGER`); su responsable (`owner_user_id`); quien la recibió
  (`received_by_user_id`); y, en una queja ALTA o CRITICA, quien tiene `APPROVE` sobre `grievance` (`APPROVER`). Nadie
  más (ASM-095). Una QCS no confidencial la ven todos los que tienen READ, como hasta F3.
- **Componente.** `ConfidentialAttentionScope` (`sgspe.scope`) implementa `RecordReadScope.criterion(user,
  definition)`. Fuera de las tres QCS, o con `READ_CONFIDENTIAL` sobre el objeto, no restringe. Si no, devuelve
  `confidential IS NOT TRUE OR owner_user_id = <quien llama> OR received_by_user_id = <quien llama>` y, en `grievance`
  con `APPROVE`, además `OR severity IN ('ALTA', 'CRITICA')`, con las columnas de la definición y valores enlazados,
  nunca concatenados. Las acciones de quien llama salen de `sgspe.common.Permissions`.
- **Falla cerrado.** Sin esas columnas en la definición, o si la consulta de permisos falla, devuelve una condición que
  no admite ningún registro del objeto y deja una advertencia en el log sin datos del registro.
- **Escrituras.** Quien no lee una QCS no la cambia ni la transiciona: wasichai la trata como inexistente.
  `AttentionWriteGuard` cubre la marca: el alta de una confidencial lleva como receptor a quien escribe, salvo con
  `READ_CONFIDENTIAL` (`400 CONFIDENTIAL_RECEIVER_REQUIRED`), y quitar la marca o cambiar el responsable o el receptor
  exige esa acción (`403 ACTION_REQUIRED`). Una evidencia vinculada a una QCS confidencial es CONFIDENCIAL o DATOS
  PERSONALES (`409 EVIDENCE_NOT_RESTRICTED`).
- **Trabajos, avisos y cifras.** JOB-01, JOB-02, JOB-04 y `DeadlineNotices` leen como la plataforma, sin el alcance, y
  filtran lo que producen: un aviso con datos solo llega a quien vería la QCS (ASM-102), el aviso genérico del nivel 2
  (`qcs_confidential_overdue`) no lleva datos del registro y JOB-04 solo cuenta. Los tableros, el reporte unificado y
  las exportaciones leen como quien llama y dicen `confidentialHidden` sin el número de excluidas; EXTERNO excluye toda
  QCS confidencial, también para `GRIEVANCE_MANAGER` (ASM-101).
- **Vínculos.** El id de una QCS que quien lee no ve es visible desde una tarea, una evidencia, un compromiso o una
  pregunta; el registro no. El portal muestra «Registro reservado».
- **La única excepción dentro de una petición: `ConfidentialRelease`.** `RequestClosureListener` corre como quien
  cierra el compromiso, dentro de `ConfidentialRelease` (`sgspe.common`): mientras dura el bloque,
  `ConfidentialAttentionScope` admite además las solicitudes cuyo `created_commitment` es ese compromiso, y solo ellas.
  La auditoría lleva el origen `listener:request-closure` [WAS-ADR-0050] y el motivo «RN-GRV-001: compromiso <código>
  cerrado», con el usuario de quien cerró; sigue en la transacción del cierre, que se revierte si la solicitud no se
  puede cerrar. Es el sustituto de una acción del sistema que wasichai no permite en una petición, aceptado por el
  usuario el 2026-10-09 (plan 4.2a, decisión 4). `ArchitectureTest` vigila que solo `RequestClosureListener` lo use.

## Consecuencias

### Positivas

- Una QCS confidencial queda protegida en todas las rutas de lectura de wasichai, no solo en las listas.
- Quién la ve se cambia en `roles.json`, sin tocar el código.
- El mismo SPI, del lado de SGSPE, resolvería UPS-01 si el checkpoint elige la opción B de CON-041.

### Negativas y riesgos

- Las cifras de los tableros y del reporte dependen de quién llama; `confidentialHidden` lo avisa sin decir cuántas.
- PUBLICO de `attention-register` solo es completo si lo genera quien ve las confidenciales, y «Notas» lo dice.
- KPI-ENG-05 no depende de quién llama en las preguntas derivadas: el plan 4.2a las cuenta por `referred_at` de la
  pregunta, sin leer la QCS a la que se derivaron (`dashboards.md` §4.3), y aun así lleva `confidentialHidden`.
- Que wasichai trate como inexistente una QCS que no se lee también en las escrituras, y que el alta la devuelva a
  quien la recibió, lo prueba la primera tarea de integración del plan 4.2a (riesgo 1 de la especificación de F4).
- `ConfidentialRelease` es una puerta en el alcance: una sola, acotada a un objeto, un campo y un valor, y vigilada.

### Seguimiento

- Pruebas: `ConfidentialAttentionScopeTest` y `ConfidentialReleaseTest` (unitarias); `ConfidentialAttentionApiTest`,
  `StakeholderRequestWorkflowApiTest` (cierre de una solicitud confidencial por su compromiso), `QcsNoticesApiTest`,
  `AttentionDashboardApiTest` y `AttentionRegisterApiTest` (integración); `ArchitectureTest`.
- Desde el plan 4.4 se compone por AND con el alcance por proyecto de ADR-0029: `READ_CONFIDENTIAL` no abre otros
  proyectos y `READ_ALL_PROJECTS` no abre las confidenciales.
- Se revierte quitando el `RecordReadScope` y volviendo al riesgo residual de `security.md` §11.3 (ASM-095).

## Referencias

- Requisitos: RF-GRV-001, RN-GRV-001, RN-GRV-002, RQ-SEC-004, RQ-SEC-005, RQ-SEC-010; CON-041; GAP-024; UPS-01;
  ASM-068, ASM-095, ASM-101, ASM-102.
- Documentos: [security.md §9.6, §11.3 y §11.4](../security.md), [workflows.md](../workflows.md) («Listeners»,
  «Avisos» y «Destinatarios por rol»), [api.md §4.6](../api.md), [dashboards.md §8](../dashboards.md),
  [deliverables.md §10](../deliverables.md), [ux/screens.md §8](../ux/screens.md).
- ADR de SGSPE: ADR-0006, ADR-0022.
- WAS-ADR-0042 (acciones declaradas por la app), WAS-ADR-0048 (`RecordReadScope`, el alcance de lectura que aporta la
  app), WAS-ADR-0050 (origen de cada escritura en la auditoría).
