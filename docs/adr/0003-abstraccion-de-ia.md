# ADR-0003: Abstracción de IA sobre wasichai-agent

## Estado

Aceptado · 2026-10-12 · al iniciar F7, con las decisiones del usuario del 2026-10-12: la API de Anthropic (GAP-015), la
lista del despliegue para activar la IA (ASM-128) y AI-05 seudonimizado con los puntos de extensión de wasichai-agent
(UPS-14, ASM-130)

## Contexto

- El prompt pide Spring AI y Embabel, una pasarela que cambie entre Anthropic, OpenAI u otros sin reescribir el dominio,
  el registro de cada interacción (proveedor, modelo, versión de prompt, tokens, latencia, confianza y validación),
  prompts versionados y ningún secreto guardado [PRM-01 §3, §9, §10]; y prohíbe acoplar el dominio al proveedor
  [PRM-01 §26] (RQ-AI-005, RQ-AI-006).
- La plataforma ya decidió su runtime: el asistente de wasichai-agent corre sobre Embabel 1.5.3, la del BOM de
  wasichai 1.2.0 (WAS-ADR-0015), llega a los datos solo por servicios de wasichai, con nueve herramientas de solo
  lectura y como la persona que pregunta (WAS-ADR-0014); `wasichai-agent` es neutral al proveedor y su starter agrega
  Anthropic por defecto (WAS-ADR-0031, D7).
- SGSPE necesita además activación por organización, seudonimización antes del LLM y registro con tokens (RQ-AI-009,
  RF-AIK-009). Esa brecha era UPS-14, resuelta en wasichai (WAS-ADR-0056): `AgentAccessPolicy`, `AgentResultFilter`,
  `AgentAnswerFilter` y `AgentRunListener`.
- El usuario eligió el 2026-10-12 la API de Anthropic (GAP-015), y la IA llega en F7 [USR-05].
- Este ADR fija la abstracción y el registro. Que la sugerencia no sea un comando: ver ADR-0004. El RAG: ver ADR-0005.

## Opciones consideradas

### Spring AI en SGSPE, al lado de la plataforma

- Ventajas: es lo que nombra el prompt.
- Desventajas: dos runtimes de agentes y dos configuraciones de proveedor en el mismo proceso; reemplaza lo que la
  plataforma ya resolvió (CON-039).

### SDK directo de un proveedor en `sgspe.ai`

- Ventajas: control total de la llamada y de los tokens.
- Desventajas: acopla el dominio al proveedor, en contra de RQ-AI-005; es el camino que wasichai abandonó al migrar a
  Embabel (WAS-ADR-0015).

### Portal contra `POST /api/agent/ask` de wasichai, sin capa propia

- Ventajas: nada que construir para AI-05.
- Desventajas: sin activación por organización, sin `ai_interaction` y sin seudonimización (UPS-14).

### Embabel a través de wasichai-agent, con `sgspe.ai` como única puerta (elegida)

- Ventajas: un solo proveedor y una sola plataforma de agentes por despliegue; cambiar de modelo o de proveedor es
  configuración; `sgspe.ai` agrega la activación, el registro y la seudonimización de sus propios agentes.
- Desventajas: hereda las limitaciones de Embabel 1.5.3
  (catálogo de modelos y tope de vueltas de herramientas).

## Decisión

- **Proveedor.** La API de Anthropic, el proveedor por defecto del starter (GAP-015); cambiarlo es configuración de
  Embabel. Un proveedor por despliegue, compartido por wasichai-agent y los agentes de `sgspe.ai`; el modelo de cada
  caso se configura con `sgspe.ai.models.<caso>` (ASM-129). La versión de Embabel es la del BOM de wasichai; el starter
  `wasichai-spring-boot-starter-agent` se agrega en F7 ([ai-architecture.md §11](../ai-architecture.md)).
- **Secretos.** La clave llega por variable de entorno y nunca se guarda en la base, en `ai_interaction`, en los prompts
  ni en los logs (RQ-AI-006, RQ-AI-009).
- **Activación por organización.** La IA nace desactivada (ASM-015). El mecanismo es la lista
  `sgspe.ai.enabled-organizations` de la configuración del despliegue, que mantiene el operador junto con la clave,
  porque el modelo no tiene un objeto de ajustes de la organización y activarla depende del contrato con el proveedor
  (ASM-128, [ai-architecture.md §12.1](../ai-architecture.md)). `GET /api/sgspe/ai/status` responde siempre; solo
  `POST /api/sgspe/ai/runs`, `POST /api/sgspe/ai/assistant/questions` y `POST /api/sgspe/ai/knowledge/questions`
  responden `503 AI_UNAVAILABLE` sin activación o sin proveedor. Aceptar o rechazar sugerencias guardadas sigue
  funcionando.
- **Registro.** Cada llamada a un modelo es una `ai_interaction` `appendOnly` y `apiOnly`:

| Pide el prompt | Campo de `ai_interaction` |
|---|---|
| provider, model | `provider`, `model`, `model_version` |
| promptVersion | `prompt_version` (`<id>@<versión>`) |
| timestamp, latency | `started_at`, `latency_ms` (medida por `sgspe.ai`) |
| inputReference | `input_reference`, nunca el contenido: `evidence:<id>` con su `sha256`, `engagement:<id>` con su `updated_at`, o el SHA-256 de una pregunta ([ai-architecture.md §9.2](../ai-architecture.md)) |
| output | `output_summary`, sin datos personales |
| tokens | `input_tokens`, `output_tokens`; en AI-05, los de `AgentUsage` (`AgentRunListener`) |
| confidence | `confidence` |
| humanValidation | Se lee del estado de la sugerencia o del candidato y de su transición auditada |

- **Más campos.** `use_case`, `project` y quién la pidió (vacío, con `run_by_platform`, cuando la corre un trabajo).
- **Prompts versionados.** Un archivo por versión en `src/main/resources/sgspe/ai/prompts/<id>/<versión>.md`; una versión
  publicada no cambia (una prueba compara su SHA-256 con un manifiesto); la versión en uso se elige por configuración
  (`sgspe.ai.prompts.<caso>`); los prompts van en español y sus ejemplos son ficticios.
- **Seudonimización.** Los agentes propios pasan por el `Pseudonymizer` antes de cada llamada (RQ-AI-009); el detalle de
  los marcadores está en [ai-architecture.md §12.2](../ai-architecture.md) y las reglas de datos personales, en
  ADR-0017.
- **AI-05.** El portal pregunta por `POST /api/sgspe/ai/assistant/questions`; `sgspe.ai` verifica la activación, llama en
  proceso al `AgentService` de wasichai-agent y registra la `ai_interaction`. Un filtro de `sgspe.ai` responde 403 a
  `POST /api/agent/ask` por HTTP y `/admin` no registra `agentModule()`.
- **AI-05 con los puntos de extensión (ASM-130).** `AiAgentAccess` (`AgentAccessPolicy`) lo permite a quien tenga CREATE
  sobre `ai_interaction` en una organización activada, sin lista de roles; `PseudonymizingResultFilter`
  (`AgentResultFilter`) seudonimiza los resultados de las herramientas y enmascara siempre los campos restringidos;
  `RestoringAnswerFilter` (`AgentAnswerFilter`) restaura la respuesta, salvo esos campos; `InteractionRunListener`
  (`AgentRunListener`) registra la `ai_interaction` con tokens ([ai-architecture.md §8](../ai-architecture.md),
  [security.md §11.5](../security.md)).

## Consecuencias

### Positivas

- El dominio no conoce al proveedor; volver atrás un prompt o cambiar de modelo es configuración.
- Toda pregunta y toda corrida pasan por la activación por organización y quedan registradas.
- Sin clave, el caso se informa no disponible y el resto de SGSPE sigue funcionando.

### Negativas y riesgos

- Claude Haiku 5.5, del catálogo de Embabel 1.5.4, en todos los casos: menos capacidad que Opus o Sonnet 5.5 a cambio de
  costo (ASM-129; [architecture.md §14](../architecture.md), riesgo 15); además, tope de
  20 vueltas de herramientas y Jackson 2 en el classpath ([ai-architecture.md §16](../ai-architecture.md),
  WAS-ADR-0015).
- Los datos seudonimizados se procesan fuera del Perú (GAP-015); en AI-05 y en los agentes propios, el riesgo residual
  son los nombres escritos en textos libres que no están registrados como `person` (RQ-REP-033).
- Las corridas son síncronas, dentro de la petición; una variante asíncrona necesita un ADR nuevo
  ([ai-architecture.md §16](../ai-architecture.md)).

### Seguimiento

- Plan 7.2a: anotar el modelo elegido para cada caso (ASM-129) y adoptar los cuatro puntos de extensión de
  wasichai-agent (ASM-130).
- Pruebas de [ai-architecture.md §15](../ai-architecture.md): FakeModel, StubModel (`ScriptedLlmOperations`) y
  MockModel, sin LLM real (RQ-AI-007); manifiesto de prompts; con la IA desactivada o sin clave, los tres endpoints que
  llaman a un modelo responden `503 AI_UNAVAILABLE` y `GET /api/sgspe/ai/status` sigue respondiendo;
  `POST /api/agent/ask` por HTTP recibe 403; AI-05 con `ScriptedLlmOperations` ([security.md §14](../security.md),
  «Asistente AI-05»); ningún perfil se llama `test`.

## Referencias

- Requisitos: RF-AIK-005, RF-AIK-009, RQ-AI-005, RQ-AI-006, RQ-AI-007, RQ-AI-009, RQ-AUD-003, CON-039, GAP-015, GAP-028,
  ASM-015, ASM-128, ASM-129, ASM-130, UPS-14.
- Documentos: [ai-architecture.md](../ai-architecture.md) §6.2, §8, §9.2, §10, §11, §12 y §15;
  [security.md](../security.md) §7.8, §11.5 y §15; [platform-fit.md §2.7 y §2.8](../platform-fit.md).
- wasichai: WAS-ADR-0014 (*The agent reaches data only through Wasichai's own services*), WAS-ADR-0015 (*Embabel as
  the agent runtime*), WAS-ADR-0031 (*Where wasichai deliberately behaves differently from the original*, D7),
  WAS-ADR-0056 (los puntos de extensión de wasichai-agent: acceso, filtro de resultados, filtro de la respuesta y
  escucha de la corrida).
- Fuentes: [PRM-01 §3, §9, §10, §21, §26], [USR-05], [USR-07].
