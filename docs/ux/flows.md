# Flujos de usuario de SGSPE

Los flujos núcleo de PRM-02 §38, recorridos sobre las pantallas de [`screens.md`](screens.md) y las reglas de la Fase 0. Esta
versión cubre los 18 flujos: la **etapa 2** (flujos 1 a 11, 16 y 17), la **parte 3a** (flujos 12 a 15: quejas y riesgos) y la
**parte 3b** (flujo 18, preguntar a la IA, y el flujo de campo sin conexión, que agrega la decisión ASM-081).

- Los diagramas de la etapa 2 están en la página 10 de su lienzo (hojas `Flujos-Territorio` y `Flujos-Compromiso`):
  <https://claude.ai/artifact/7RUzqeAhtWx75vCNPCBRWe>. Los de la parte 3a, en la página 15 del lienzo de la etapa 3 (hojas
  `Flujos-QCS` y `Flujos-Riesgo`) y los de la parte 3b, en la página 20 (hojas `Flujos-IA` y `Flujos-Campo`):
  <https://claude.ai/artifact/LTNxbVoceQL56sJ1F8BERr>.
- Cada paso nombra la pantalla (`SNN`), la transición de workflow o el endpoint, y la regla que lo condiciona. Las transiciones
  van por la API genérica salvo que se indique un comando de `/api/sgspe/**` ([api.md](../api.md) §2 y §4).
- Principio común [USR-10]: ningún flujo saca a la persona del proyecto ni del territorio en contexto; el chip de territorio y
  el rastro viajan en la URL y en el historial ([`ux-architecture.md`](ux-architecture.md) §8).

## Flujo 1 · Crear proyecto

- **Actor:** administración del proyecto (`PROJECT_ADMIN`); la jefatura (`PROJECT_MANAGER`) completa y envía.
- **Disparador:** un proyecto nuevo de exploración debe empezar a gestionarse.

| # | Pantalla | Acción | Regla o resultado |
|---|---|---|---|
| 1 | S06 | "Nuevo proyecto" | Solo `PROJECT_ADMIN` crea `project` |
| 2 | S07 pasos 1–2 | Código, nombre, tipo, etapa, fase; ubicación y CRS de trabajo | `code` único; el CRS de trabajo es UTM WGS 84 (ASM-002) |
| 3 | S07 pasos 3–5 | Partes (CLIENTE, OPERADOR, CONSULTORA), campaña con tope, equipo por asignación | La jefatura es la asignación con `PROJECT_MANAGER` (ASM-080) |
| 4 | S07 paso 6 | Revisar parámetros y políticas | Todos traen valor por defecto (ASM-019) |
| 5 | S07 paso 7 | "Crear y enviar a revisión" → `submit_for_review` | Aviso de aprobación pendiente |
| 6 | Tarea de aprobación | La administración del proyecto, con `APPROVE`, aplica `approve` o devuelve con motivo | Exige CRS de trabajo y una parte CLIENTE u OPERADOR (ASM-062); sin revisión, la variante de ASM-010 aprueba desde `draft` |

- **Resultado:** proyecto `active` y su Project 360 (S08) disponible para el equipo asignado.

## Flujo 2 · Agregar comunidad

- **Actor:** relacionista comunitario o analista SIG (`COMMUNITY_RELATIONS_OFFICER`, `GIS_ANALYST`).
- **Disparador:** el proyecto empieza a relacionarse con una comunidad que no está en el maestro del territorio.

| # | Pantalla | Acción | Regla o resultado |
|---|---|---|---|
| 1 | S12 | "Nueva comunidad" desde el mapa territorial | La comunidad es del tenant, no del proyecto |
| 2 | Formulario de `community` | Tipo, nombres alternativos, partidas, título y emisor tal como figura, área inscrita, estado orgánico, junta inscrita | Estado orgánico e inscripción de la junta son datos separados (CON-013) |
| 3 | S15 o dibujo en el mapa | Territorio comunal | Importar exige CRS de origen (RQ-GIS-003); el área calculada se muestra junto a la inscrita (RQ-GIS-009) |
| 4 | S13 | Sectores comunales con delegados y sus localidades | `community_sector_locality` |
| 5 | S13 | Organización que la representa y sus cargos | `organization.community`; cargos vacantes permitidos |

- **Resultado:** ficha de comunidad (S13) visible en el mapa y lista para ser miembro de un área de influencia (S10).

## Flujo 3 · Registrar grupo de interés

- **Actor:** gestor de grupos de interés (`STAKEHOLDER_MANAGER`); el relacionista caracteriza.
- **Disparador:** una persona u organización es relevante para el proyecto.

| # | Pantalla | Acción | Regla o resultado |
|---|---|---|---|
| 1 | S16 o S13 | "Nuevo grupo de interés" (desde la ficha territorial, con el lugar precargado) | El chip de territorio fija la localidad |
| 2 | Buscador de actores | Buscar antes de crear; crear `person` u `organization` solo si no existe | Un actor por persona u organización en el tenant |
| 3 | Formulario de `stakeholder` | Tipo, ámbito, motivo de inclusión, responsable | Queda `identified`; único por proyecto y actor |
| 4 | S17 | `profile` | Guarda: tipo, ámbito, motivo y responsable informados (RF-STK-003) |
| 5 | S20 | Evaluación: 15 indicadores, postura, confianza, motivo | `POST /api/sgspe/stakeholders/{id}/assessments`; aplica `assess` (solo `STAKEHOLDER_MANAGER` y `SOCIAL_MANAGER`) |

- **Resultado:** grupo de interés `assessed`, con grupo de influencia y prioridad; aparece en la matriz (S19).

## Flujo 4 · Programar interacción

- **Actor:** relacionista comunitario, gestor de grupos de interés o agente de campo.
- **Disparador:** el plan de relacionamiento o un pedido exige reunirse.

| # | Pantalla | Acción | Regla o resultado |
|---|---|---|---|
| 1 | S21 | Revisar el mes: meta de reuniones y festividades de la línea base | KPI-ENG-02; las festividades evitan fechas conflictivas |
| 2 | S24 pasos 1–2 | Tipo, fecha y hora, lugar, grupos de interés invitados, convocante | Queda `planned` |
| 3 | S24 | "Programar" → `schedule` | Guarda: `scheduled_at` informado |
| 4 | S21 | Si no se puede realizar: `postpone` (motivo) y después `reschedule`, o `cancel_*` (motivo) | Cancelar crea la tarea de reprogramar o reemplazar (AUT-10) |

- **Resultado:** interacción `scheduled` en el calendario del equipo.

## Flujo 5 · Redactar el acta

- **Actor:** quien registró la interacción; la gerencia social aprueba.
- **Disparador:** la interacción se realizó.

| # | Pantalla | Acción | Regla o resultado |
|---|---|---|---|
| 1 | S24 paso 3 o S23 | Participantes (personas y grupos sin nombres) y `register_held` | Guarda: `held_at` y al menos un asistente; los conteos se calculan |
| 2 | S24 paso 4 | Preguntas con su respuesta en el lugar o pendientes; temas | Las pendientes impiden cerrar (RN-ENG-005) |
| 3 | S25 | Redactar con estructura e "Insertar asistentes" → `draft_minutes` | `minutes` no vacío |
| 4 | S25 | "Enviar a revisión" → `submit_minutes` | Aviso a la gerencia social |
| 5 | S23 | Gerencia social: `approve_minutes` o `return_minutes` (motivo) | Aprobar emite minuta y lista de asistentes (AUT-05) |
| 6 | S23 | Responder o derivar las preguntas pendientes y `close` | Derivar a una QCS desde F4; los tipos ligeros cierran sin acta (`close_light`, ASM-033) |

- **Resultado:** acta aprobada; la interacción queda lista para generar compromisos y cerrarse.

## Flujo 6 · La IA detecta posibles compromisos (F7)

- **Actor:** el agente AI-03, a pedido de una persona; en F3 la misma entrada es humana ("Proponer compromiso" en S25).
- **Disparador:** un acta aprobada o subida como evidencia ACTA.

| # | Pantalla | Acción | Regla o resultado |
|---|---|---|---|
| 1 | S25 | Una persona selecciona texto y "Proponer compromiso" (F3) | `POST /api/sgspe/commitment-candidates` con cita y valores sugeridos |
| 2 | — | AI-03 lee el acta y crea candidatos (F7) | `commitment_candidate` en `proposed` con `ai_generated`, cita y confianza; nunca un compromiso ("possible commitment") |
| 3 | S28 · Candidatos | Los candidatos aparecen con su procedencia | Marca de IA y confianza (`design-system.md` §4) |

- **Resultado:** candidatos `proposed` esperando decisión humana.

## Flujo 7 · Una persona valida el candidato

- **Actor:** gestor de compromisos, gerencia social o relacionista (`COMMITMENT_MANAGER`, `SOCIAL_MANAGER`,
  `COMMUNITY_RELATIONS_OFFICER`).
- **Disparador:** hay candidatos propuestos.

| # | Pantalla | Acción | Regla o resultado |
|---|---|---|---|
| 1 | S28 · Candidatos | Leer la cita del acta y la confianza | La cita es restringida (`security.md` §7.9) |
| 2a | Panel del candidato | "Aceptar y crear borrador" con correcciones | `POST /api/sgspe/commitment-candidates/{id}/accept`: crea el compromiso en `draft` y guarda `created_commitment` |
| 2b | Panel del candidato | "Es duplicado de…" y elegir el compromiso existente | `POST …/duplicate` con motivo; no se fusiona nada (RQ-AI-015) |
| 2c | Panel del candidato | "Rechazar" con motivo | Transición `reject` |

- **Resultado:** cada candidato termina `accepted`, `duplicate` o `rejected`, con la persona y el motivo en la auditoría.

## Flujo 8 · Aprobar el compromiso

- **Actor:** quien lo registró envía; aprueba otra persona con `APPROVER`, o la jefatura en el nivel superior.
- **Disparador:** un compromiso en `draft` está completo.

| # | Pantalla | Acción | Regla o resultado |
|---|---|---|---|
| 1 | S30 | Completar la matriz: qué, solicitantes, beneficiarios, fechas, monto, campaña, responsable | D1 y D2; monto obligatorio (RN-COM-004); justificación si nace de un taller o reunión informativa (RN-ENG-001) |
| 2 | S30 paso 7 | "Enviar a aprobación" → `submit` (motivo) | La tarea llega a la bandeja de aprobación |
| 3 | S31 | Revisar las reglas D3 a D7 con `transition-checks` | Quien registró no aprueba (D4); mesa de diálogo o tope superado van al nivel superior (D5, D7) |
| 4 | S31 | `approve` o `approve_senior` (motivo); o `return_to_draft`, o `cancel_pending_approval` | Aprobar crea la tarea de ejecución (AUT-01); desde aquí la fecha solo cambia por reprogramación |

- **Resultado:** compromiso `approved` con su responsable avisado.

## Flujo 9 · Ejecutar el compromiso

- **Actor:** responsable del compromiso (gestor de compromisos o relacionista).
- **Disparador:** la tarea de ejecución.

| # | Pantalla | Acción | Regla o resultado |
|---|---|---|---|
| 1 | S29 | `start` (motivo) | Queda `in_progress`; JOB-01 calcula la situación de plazo |
| 2 | S29 · Acciones e hitos | Registrar acciones, alcanzar hitos, informar riesgo de retraso | No cambian el estado |
| 3 | S29 · Reprogramaciones | Si hace falta: `POST /api/sgspe/commitments/{id}/reschedules` | Conserva la fecha anterior; solo `COMMITMENT_MANAGER` (RF-COM-003) |
| 4 | S29 | Si algo lo detiene: `block` y después `unblock` (motivo) | Cuenta como bloqueado en el tablero |

- **Resultado:** el compromiso avanza hasta tener algo que entregar.

## Flujo 10 · Registrar la entrega con evidencia

- **Actor:** responsable, gestor de compromisos o relacionista.
- **Disparador:** se entregó lo comprometido, total o parcialmente.

| # | Pantalla | Acción | Regla o resultado |
|---|---|---|---|
| 1 | S32 | Subir el acta de entrega y las fotos | `POST /api/sgspe/evidence` con tipo y clasificación |
| 2 | S32 | "Guardar entrega": fecha, lugar, qué, monto, conformidad y de quién | `POST /api/sgspe/commitments/{id}/deliveries`; recalcula el ejecutado; no cambia el estado |
| 3 | S32 | "Registrar cumplimiento" → `register_fulfillment` (motivo) | D9: conformidad y evidencia según el tipo de recurso (ASM-009); D10: sin dependencias bloqueantes abiertas |

- **Resultado:** compromiso `fulfilled` con `fulfilled_date`.

## Flujo 11 · Verificar el compromiso

- **Actor:** el verificador asignado: gestor de compromisos o gerencia social con `VERIFY`, que no sea la responsable.
- **Disparador:** aviso de compromiso cumplido.

| # | Pantalla | Acción | Regla o resultado |
|---|---|---|---|
| 1 | S32 | Revisar acta, fotos y lo entregado frente a lo comprometido | La evidencia restringida se descarga con `DOWNLOAD_RESTRICTED` |
| 2a | S32 | `verify` (motivo) | D11: `VERIFY` y no ser el responsable (RN-COM-005) |
| 2b | S32 | `reopen_execution` (motivo) si falta algo | Vacía `fulfilled_date`; deja de contar como cumplido |
| 3 | S29 | `close` (motivo) | Escribe `closed_date`; cierra la solicitud aceptada que lo originó (RN-GRV-001, desde F4) |

- **Resultado:** compromiso `closed`, con la cadena acta → compromiso → entrega → evidencia navegable.

## Flujo 12 · Crear una queja

- **Actor:** relacionista comunitario (`COMMUNITY_RELATIONS_OFFICER`) o gestión de QCS (`GRIEVANCE_MANAGER`).
- **Disparador:** una persona o un grupo plantea un daño o malestar atribuido al proyecto, en una reunión, en campo, por carta,
  por teléfono o a través de una contratista.
- **Diagrama:** hoja `Flujos-QCS` del lienzo de la etapa 3, página 15.

| # | Pantalla | Acción | Regla o resultado |
|---|---|---|---|
| 1 | S35, S23 o pregunta de reunión | "Registrar QCS" con el origen ya puesto | El proyecto y el territorio vienen del contexto |
| 2 | S37 | Tipo queja; canal, fecha de recepción, anonimato, persona, lugar, descripción, categoría, severidad | Una anónima no lleva persona ni grupo de interés; la persona es campo restringido (`security.md` §7.9) |
| 3 | S37 | "Registrar queja" → `grievance` en `received` | Plazos de acuse y resolución desde `received_at` (ASM-005); AUT-02 crea la tarea de acusar recibo |
| 4 | S36 | La IA sugiere categoría, prioridad y posibles duplicados (F7) | `ai_suggestion` de AI-08 y AI-07; nunca cambia la queja ni une registros |
| 5 | S36 | Aceptar o rechazar cada sugerencia; aceptar un duplicado lleva la más nueva a `rejected` con `duplicate_of` | El rechazo exige respuesta y `responded_at` |
| 6 | S36 | `acknowledge` (motivo) | AUT-06 emite el cargo de queja; `deadline_status` pasa a medir la resolución |
| 7 | S36 | `assess` (motivo): categoría, severidad y riesgo que alimenta | M2O `social_risk`; si se repite un patrón, se abre un riesgo (flujo 14) |

- **Resultado:** queja `in_assessment`, con su cargo emitido, su riesgo vinculado y su plazo de resolución corriendo.
- **Variantes:** si resulta ser una consulta o una solicitud, `POST /api/sgspe/grievances/{id}/reclassify` crea el registro
  nuevo con la misma fecha de recepción (ASM-072); el desistimiento y el rechazo cierran con motivo.

## Flujo 13 · Resolver una queja

- **Actor:** gestión de QCS; aprobación (`APPROVER`) cuando la severidad es ALTA o CRITICA; gestión de QCS o gerencia social
  para verificar.
- **Disparador:** la queja está evaluada y requiere acción.

| # | Pantalla | Acción | Regla o resultado |
|---|---|---|---|
| 1 | S36 | `investigate` (opcional) o `start_action` (motivo) | Exigen categoría y severidad |
| 2 | S36, S47 | Acciones con tareas y evidencia | Las tareas nacen con la queja como origen (RF-WFL-001) |
| 3a | S36 | `resolve` (motivo) con resolución y respuesta | Con la política activa, no se permite en ALTA o CRITICA (RN-GRV-002, ASM-036) |
| 3b | S48 → S36 | `resolve_high_severity` (motivo) | Solo `APPROVER`, con `APPROVE` sobre `grievance` |
| 4 | S36 | Comunicar la respuesta: `responded_at` y conformidad del reclamante | Sin conformidad, `reopen` vuelve a `in_assessment` y el plazo vuelve a medir |
| 5 | S36 | `verify` (opcional, motivo) | Gestión de QCS o gerencia social |
| 6 | S36 | `close_resolved` o `close` (motivo) | Exige `responded_at`; escribe `closed_date` |

- **Resultado:** queja `closed`, absuelta (KPI-GRV-07), con su respuesta comunicada.
- **Plazos:** POR VENCER y VENCIDO avisan al responsable; a los 7 y 15 días de atraso, JOB-01 escala con aviso y una tarea sin
  asignado a los roles de verificación y luego a los de nivel superior (ASM-030, ASM-070).

## Flujo 14 · Identificar un riesgo social

- **Actor:** gestión de riesgos (`SOCIAL_RISK_MANAGER`); la gerencia social también confirma alertas.
- **Disparador:** un patrón de quejas en un lugar y un tema, o una alerta temprana creada por una regla.
- **Diagrama:** hoja `Flujos-Riesgo` del lienzo de la etapa 3, página 15.

| # | Pantalla | Acción | Regla o resultado |
|---|---|---|---|
| 1 | S41 | La regla crea la alerta en `detected` (JOB-04); AUT-09 crea la tarea de triaje | Las reglas y la IA nunca deciden (RF-EWS-004) |
| 2a | S41 | `confirm` y vincular el riesgo que alimenta | `CONFIRM_ALERT`; solo una persona (RN-EWS-001) |
| 2b | S41 | `dismiss` (motivo obligatorio) | Las señales quedan registradas |
| 3 | S39 o S36 | "Identificar riesgo": fuente, categoría, tipo e impacto, lugar, grupos de interés, conflicto y tema | `social_risk` en `identified`; las QCS y alertas que lo alimentan apuntan a él |
| 4 | S40 | Primera revisión: probabilidad e impacto de 1 a 5 | `POST /api/sgspe/social-risks/{id}/assessments` calcula puntaje y nivel (ASM-004) y aplica `assess` |
| 5 | S40 | — | Si el nivel es ALTO o CRITICO, aviso de riesgo escalado; si es CRITICO, AUT-11 |

- **Resultado:** riesgo `assessed`, en el mapa de calor y en la capa LYR-21.

## Flujo 15 · Crear y seguir una mitigación

- **Actor:** gestión de riesgos o gerencia social; las personas responsables de cada medida.
- **Disparador:** el riesgo está evaluado y se decide tratarlo.

| # | Pantalla | Acción | Regla o resultado |
|---|---|---|---|
| 1 | S40 | "+ Medida" con descripción, responsable y fecha planificada | Solo SRM escribe `mitigation_measure` |
| 2 | S40 | Decisión de tratamiento MITIGAR y `decide_mitigation` (motivo) | Exige al menos una medida con responsable (RN-RSK-003) |
| 3 | S47 | Las responsables ejecutan con tareas y evidencia; marcan la medida hecha | Avance en KPI-RSK-05 |
| 4 | S40 | Nueva revisión residual | Cada revisión es inmutable; da la tendencia |
| 5 | S40 | `start_monitoring` (motivo) | Exige una revisión residual (RN-RSK-003) |
| 6 | S47 | Revisión periódica | JOB-01 mantiene `review_status` y crea la tarea de revisión (RN-RSK-001) |
| 7 | S40 | `resume_mitigation` si empeora, o `close_monitoring` con `close_reason` | Todo con motivo |

- **Resultado:** riesgo `monitoring` o `closed`, con su historia de revisiones y medidas.

## Flujo 16 · Ver el proyecto en el mapa

- **Actor:** cualquier rol con lectura del proyecto.
- **Disparador:** preparar una reunión, un informe o entender un cambio de diseño.

| # | Pantalla | Acción | Regla o resultado |
|---|---|---|---|
| 1 | S08 | Mini mapa → "Ver en el mapa" | Abre S09 con el proyecto |
| 2 | S09 | Activar capas por grupo | Capas sensibles con candado; grupos de interés agregados (LYR-17) |
| 3 | S09 | Seleccionar en el mapa o en la tabla | Sincronizados por `?selected=` |
| 4 | S09 | Filtrar por el área de influencia vigente | Usa la versión `approved` (WF-O) |
| 5 | S09 | "Capturar PNG" | Para un informe; las exportaciones con geometría llegan en F6 y quedan en `export_log` |

- **Resultado:** una vista del proyecto compartible por URL con sus capas y su selección.

## Flujo 17 · Investigar una comunidad

- **Actor:** gerencia social, relacionista o gestor de grupos de interés.
- **Disparador:** una alerta, una visita o una reunión con la comunidad.

| # | Pantalla | Acción | Regla o resultado |
|---|---|---|---|
| 1 | S12 | Seleccionar la comunidad en el mapa: ficha contextual con KPI-TER-01 (F6) | Las cifras son las mismas de los tableros; la postura, "no disponible" sin permiso |
| 2 | S13 | "Abrir la ficha": registros, sectores, organización que la representa, áreas que la incluyen | El chip de territorio queda fijado; el rastro anota "Mapa territorial" |
| 3 | S17 | Grupo de interés del lugar (por ejemplo, la junta directiva) | Evaluaciones como historia, pendientes y relaciones |
| 4 | S23 | La interacción donde se acordó algo | Participantes, preguntas, acta y acuerdos |
| 5 | S29 y S32 | El compromiso generado y su evidencia | "De dónde viene" y "Qué generó" en el panel de trazabilidad |

- **Resultado:** del lugar a la evidencia en cinco pasos, sin perder el proyecto ni el territorio, y con vuelta atrás por el
  rastro.

## Flujo 18 · Preguntar a la IA

- **Actor:** quien tenga la IA activada en su organización; "preguntar a los datos" (AI-05) solo `ANALYST` hasta UPS-14.
- **Disparador:** una duda sobre los datos o los documentos del proyecto, desde la barra superior o desde una ficha.
- **Diagrama:** hoja `Flujos-IA` del lienzo de la etapa 3, página 20.

| # | Pantalla | Acción | Regla o resultado |
|---|---|---|---|
| 1 | S05 | Abrir el asistente | Contextual: lleva el registro abierto y el contexto de proyecto y territorio (ASM-073, ASM-075) |
| 2 | S05 | `GET /api/sgspe/ai/status` | Sin IA activada, proveedor caído o rol sin AI-05: se muestra la razón y la alternativa (preguntar a los documentos) |
| 3 | S05 | Elegir modo y preguntar | AI-05: `POST /api/sgspe/ai/assistant/questions`; AI-06: `POST /api/sgspe/ai/knowledge/questions` |
| 4 | — | El servicio lee como la persona | Permisos, alcance y, en AI-06, solo clasificaciones descargables (RF-DOC-003) |
| 5 | — | Seudonimiza y llama al modelo | AI-06 y los agentes propios reemplazan nombres por marcadores (RQ-AI-009); una cita que no aparece literal se descarta |
| 6 | — | Registra la consulta | `ai_interaction` (RF-AIK-009) |
| 7 | S05 | Lee la respuesta con sus fuentes o "no encontré" | AI-06 nunca responde sin fuente; los marcadores vuelven a nombres solo para quien puede leerlos |
| 8 | Ficha citada | Abre el registro o la evidencia | El hilo de contexto se conserva (`ux-architecture.md` §8) |

- **Resultado:** la persona sabe más y no cambió ningún dato. Lo que cambia datos (propuestas de AI-01, AI-03, AI-07, AI-08) va
  a la bandeja de revisión (S52).

## Flujo de campo · Capturar sin señal y sincronizar

- **Actor:** agente de campo (`FIELD_AGENT`) o relacionista comunitario.
- **Disparador:** trabajo en lugares sin cobertura (decisión del usuario, ASM-081).
- **Diagrama:** hoja `Flujos-Campo` del lienzo de la etapa 3, página 20.

| # | Pantalla | Acción | Regla o resultado |
|---|---|---|---|
| 1 | S54 | Descargar antes de salir | Tareas y visitas de 7 días, lugares, grupos de interés y actores (nombre y cargo) y catálogos; ningún campo restringido |
| 2 | S55 | Capturar interacción, participantes, observación y fotos | Todo se guarda primero en la cola local cifrada, con un `Idempotency-Key` por elemento creado en el teléfono |
| 3 | Cola | Al volver la señal, enviar en orden | Interacción, lo que cuelga de ella y al final las fotos (reanudables; opción solo con wifi) |
| 4 | — | El servidor valida como en línea | Mismos permisos y guardas; un reintento con la misma clave no duplica (RNF-INT-002) |
| 5a | Cola | Enviado | El registro aparece en la oficina con su autoría y su hora de captura |
| 5b | Cola | Conflicto: `412`, `409` o `403` | El elemento queda en la cola con su razón; nada se pisa |
| 6 | Cola | Decidir: enviar como nota, guardar como captura nueva vinculada o descartar | Un registro ajeno nunca se cambia (`EditAnyGuard`, ASM-069) |
| 7 | S23, S26 | La oficina completa y revisa | Acta, clasificación y vínculos siguen sus flujos (4 y 5) |

- **Resultado:** lo capturado sin señal llega al sistema sin duplicados ni sobrescrituras, o queda a la vista con su razón.
- **Sesión:** si el token vence sin señal, la cola se conserva y se envía al volver a ingresar con la misma cuenta.

## Trazabilidad

- **Requisitos:** RF-PRJ-003, RF-TER-003 a RF-TER-005, RF-STK-003, RF-STK-005, RF-ENG-001 a RF-ENG-006, RF-COM-001 a
  RF-COM-010, RF-AIK-002, RF-AIK-003, RQ-AI-001, RQ-AI-003, RQ-AI-015, RQ-GIS-003, RQ-GIS-014, RN-ENG-001, RN-ENG-005,
  RN-COM-001, RN-COM-004, RN-COM-005, RN-GRV-001. Parte 3a: RF-GRV-001, RN-GRV-002, RF-RSK-001 a RF-RSK-003, RN-RSK-001,
  RN-RSK-003, RN-EWS-001, RF-EWS-004, RF-WFL-001. Parte 3b: RF-AIK-005, RF-AIK-006, RF-AIK-009, RQ-AI-009, RF-DOC-003,
  RF-FLD-001, RNF-INT-002.
- **Documentos:** [workflows.md](../workflows.md) (WF-A, WF-B, WF-C, WF-D, WF-K, WF-O; WF-E1, WF-F y WF-J en la parte 3a),
  [api.md](../api.md) §4,
  [security.md](../security.md) §7, [ai-architecture.md](../ai-architecture.md) §5, §8 y §13. Supuestos de la parte 3b:
  ASM-069, ASM-073, ASM-075, ASM-081.
- **Fuentes:** [PRM-02 §38, §39], [USR-10].
