# ADR-0004: La sugerencia de IA no es un comando de dominio

## Estado

Propuesto · 2026-10-07 · se acepta al iniciar F3

## Contexto

- El prompt pide que la IA complemente y no reemplace las decisiones de negocio, que ningún agente modifique la base sin
  comandos de dominio y autorización, flujos agénticos explícitos (documento → extracción → entidades → candidatos →
  validación → aprobación humana → comando) y herramientas como `createDraftCommitment` y `createDraftMeetingSummary`
  [PRM-01 §9, §26] (RQ-AI-001, RQ-AI-013, RQ-AI-014).
- El boceto del usuario lleva el acta a un candidato con evidencia y confianza que una persona revisa antes de crear el
  compromiso, y hace converger DATA, WORKFLOW e IA en la auditoría [ARQ-01] [ARQ-02].
- El agente de wasichai no tiene herramientas de escritura y actúa como la persona (WAS-ADR-0014): las herramientas de
  borrador del prompt no pueden escribir registros (CON-044).
- El candidato a compromiso existe desde F3, registrado por personas (RF-COM-010); los agentes llegan en F7. La abstracción
  del proveedor está en ADR-0003.

## Opciones consideradas

### Herramientas de escritura que crean borradores de dominio

- Ventajas: literal al prompt (`createDraftCommitment` crea un compromiso en borrador).
- Desventajas: un agente escribiría datos de dominio, en contra de WAS-ADR-0014 y RQ-AI-001; una instrucción inyectada
  en un documento podría crear registros (RQ-AI-011).

### El agente escribe sus sugerencias con una herramienta

- Ventajas: menos código en SGSPE.
- Desventajas: sigue siendo un agente que escribe; la validación quedaría en manos del modelo.

### Aceptación automática sobre un umbral de confianza

- Ventajas: menos trabajo de revisión.
- Desventajas: la confianza la informa el modelo y no está calibrada; RQ-AI-003 exige que solo una persona acepte.

### Agentes de solo lectura, sugerencias guardadas por código y aceptación humana (elegida)

- Ventajas: el agente solo devuelve datos estructurados; el código valida y guarda; una persona acepta con su propia
  autorización; el peor caso de una inyección es una sugerencia mala que se rechaza.
- Desventajas: más código propio (escritor, validación, lista permitida, endpoints) y una bandeja que alguien debe atender.

## Decisión

- **Los agentes solo leen.** Sus herramientas corren como la persona y sus resultados pasan por el `Pseudonymizer`.
  `createDraftCommitment` y `createDraftMeetingSummary` no son herramientas: son salidas estructuradas
  ([ai-architecture.md §7](../ai-architecture.md)).
- **El código guarda.** `SuggestionWriter` (`sgspe.ai`) valida cada salida en Kotlin, sin modelo (esquema, valores de
  catálogo, destino existente y legible, cita literal en el fragmento que recibió el modelo, duplicados) y la guarda como
  `ai_suggestion` o `commitment_candidate`, con la persona que pidió la corrida y la marca `SgspeWrite`. Una cita que no
  aparece literal descarta la propuesta.
- **Registros aparte.** `ai_suggestion` y `commitment_candidate` son `apiOnly`: la API genérica rechaza sus escrituras
  con 403 (WAS-ADR-0040). Cada uno tiene WF-K: `proposed` → `accepted`, `rejected` o `duplicate`.
  `CommandTransitionGuard` impide llegar a `accepted` fuera del endpoint de aceptación; rechazar y marcar como duplicado
  exigen motivo (`TransitionReasonGuard`) ([workflows.md, WF-K](../workflows.md)). Un candidato se marca como duplicado
  solo con `POST /api/sgspe/commitment-candidates/{id}/duplicate`, que escribe `duplicate_of`; el rechazo es la
  transición hacia `rejected` por la API de workflow de wasichai, que `apiOnly` no bloquea
  ([ai-architecture.md §14](../ai-architecture.md), [api.md §4.14](../api.md)).
- **La persona acepta.** `POST /api/sgspe/commitment-candidates/{id}/accept` (`sgspe.commitment`) crea el `commitment` en
  `draft`; `POST /api/sgspe/ai-suggestions/{id}/accept` (`sgspe.ai`) ejecuta el comando de la **lista permitida**:

| Regla de la aceptación | Fuente |
|---|---|
| La lista es cerrada: cada fila fija el tipo de salida, el `target_object`, el único campo o comando y lo que se exige a quien acepta; todo otro par se rechaza | [ai-architecture.md §4.2](../ai-architecture.md) |
| Nunca escribe un campo derivado, `workflow_state` ni un objeto `apiOnly` salvo por el comando de su paquete | RQ-AI-001 |
| Escribe los valores que la persona revisó en la petición, nunca los guardados, así que ningún marcador llega a un registro | RQ-AI-009 |
| Comando, vínculo y transición de WF-K en una sola transacción con `TransactionalOperator`, como la persona y con las guardas del destino | RQ-AI-003 (WAS-ADR-0038) |
| Se rechazan las cuentas de servicio; ninguna sugerencia se acepta sola, cualquiera sea su confianza | RQ-AI-003 |
| La auditoría guarda persona, comando y motivo, con el origen "IA aceptada" en el motivo hasta UPS-03 | RQ-AUD-012, ASM-051 |

- **Autorización.** El rol de la transición hacia `accepted` de WF-K más los permisos del comando destino. RQ-AI-003
  admite también una acción declarada, que no hace falta mientras el rol de la transición separe a quien acepta.
- **Dos decisiones humanas** del acta al compromiso: aceptar el candidato y, después, aprobar el compromiso en WF-D con
  segregación de funciones (RN-COM-005, ASM-008).
- **Textos.** BORRADOR DE ACTA y BORRADOR DE SECCION entran a un registro solo si una persona los copia o los edita;
  RESUMEN EJECUTIVO no se acepta mientras solo informa (RQ-AI-012).
- **Dirección de dependencias.** `sgspe.ai` depende de los paquetes de dominio a través de sus comandos y ningún paquete
  de dominio depende de `sgspe.ai`: BC-18 es una capa anticorrupción ([domain-model.md §8.2](../domain-model.md)).
- **Lo que la IA nunca hace:** escribir datos de dominio, aceptar sus sugerencias, aplicar transiciones, aprobar o
  verificar, confirmar alertas (RN-EWS-001), fusionar registros (RQ-AI-015, ASM-053), decidir la pertenencia a un área
  de influencia (RN-TER-004) ni emitir entregables (RQ-REP-026).

## Consecuencias

### Positivas

- Separación verificable entre el plano de IA y el de datos: el comando de dominio es el mismo que usa una persona sin
  IA, con sus guardas.
- La resistencia a la inyección de instrucciones no depende del modelo (RQ-AI-011, ASM-052).
- `commitment_candidate` sirve igual para candidatos humanos (F3) y de IA (F7).

### Negativas y riesgos

- Cada tipo de salida nuevo exige una fila nueva en la lista permitida y su prueba.
- Solo `grievance` y `commitment_candidate` tienen vínculo de duplicado; en `organization` solo se agregan nombres
  alternativos, y en `person`, `stakeholder`, `commitment`, `inquiry` y `stakeholder_request` solo se registra la
  decisión y la persona corrige con las operaciones normales.
- La bandeja de revisión puede acumular sugerencias sin atender; la confianza solo ordena.

### Seguimiento

- F3: `commitment_candidate` con alta humana, aceptación, rechazo y duplicado. F7: agentes, `ai_suggestion` y bandeja.
  F8: BORRADOR DE SECCION, cuando nace `deliverable_section`.
- Pruebas: `CommitmentCandidateWorkflowApiTest` y `AiSuggestionWorkflowApiTest` ([workflows.md](../workflows.md)); en
  [ai-architecture.md §15](../ai-architecture.md), la API genérica responde 403 sobre los tres objetos de IA,
  `CommandTransitionGuard` rechaza `accepted` fuera del endpoint, un par fuera de la lista o un campo derivado se
  rechaza, aceptar un candidato crea el compromiso en una sola transacción, y los documentos con instrucciones
  inyectadas no producen escrituras.
- UPS-03 retirará la convención de origen en el motivo.

## Referencias

- Requisitos: RQ-AI-001, RQ-AI-003, RQ-AI-004, RQ-AI-009, RQ-AI-011, RQ-AI-012, RQ-AI-013, RQ-AI-014, RQ-AI-015,
  RQ-AUD-012, RQ-REP-026, RF-COM-010, RF-AIK-003, RN-COM-005, RN-EWS-001, RN-TER-004, CON-044, ASM-008, ASM-051,
  ASM-052, ASM-053, UPS-03.
- Documentos: [ai-architecture.md](../ai-architecture.md) §2, §4, §5, §6.1, §7, §9 y §16; [workflows.md](../workflows.md)
  (transiciones de comando y WF-K); [domain-model.md](../domain-model.md) §2 y §8.2;
  [architecture.md §5.3 y §7](../architecture.md).
- wasichai: WAS-ADR-0014 (*The agent reaches data only through Wasichai's own services*), WAS-ADR-0038 (*RecordService
  joins the caller's transaction, and that is supported API*), WAS-ADR-0040 (*Append-only objects, a pre-write guard
  SPI, and api-only objects*).
- Fuentes: [PRM-01 §9, §26], [ARQ-01], [ARQ-02].
