# Historia de cambios

La entrada más reciente va primero. El razonamiento de arquitectura está en [`adr/`](adr/README.md); este archivo
registra lo entregado.

## 2026-10-15 — Plan 8.2c: T1, mapas de informe, PDF y borradores de sección de IA

Los entregables de F8 quedan completos en el backend: los borradores reales llegan a LISTO.

- **PDF.** `GotenbergConverter` convierte cada DOCX con Gotenberg `8.37.0-libreoffice` (compose y CI, sin descargas
  remotas ni webhooks), con plazo de 180 s; título, código y revisión van en las propiedades del DOCX. Pruebas
  `gotenberg` contra el servicio real en la CI.
- **Mapas de informe.** `GeoServerReportMaps` encuadra en el UTM del proyecto, pide un GetMap por POST XML por nivel
  (estilos de la paleta a 300 dpi, geometrías en línea leídas por `SpatialGate` como la plataforma, EXTERNO con una
  lista explícita de capas) y `MapComposer` dibuja marco, grilla, leyenda, escala, norte, CRS y fuente. T2 queda
  cerrado.
  Un mapa fallido deja el borrador LISTO con `MAP_FAILED <clave>`; la causa va solo a los logs.
- **T1 · Diagnóstico social.** `SocialDiagnosisType` con 46 secciones, índice TOC, listas de tablas y figuras con
  `PAGEREF`, la Tabla 9.5 con el lector de la plataforma, la matriz 5 × 5, el cuadrante 2 × 2 y la tendencia nativa de
  Word.
  Pedirlo exige el AIDS aprobado. En EXTERNO, un párrafo `EXTERNAL_ONLY` avisa que se retuvo contenido restringido.
- **AI-04 de sección.** `POST /api/sgspe/ai/runs` sobre una `deliverable_section` marcada por el tipo redacta una
  BORRADOR DE SECCION con la sección leída como la persona; la aceptación la adopta (`[PERSONA:<id>]` pasa a una
  mención) y la sección queda ASISTIDO POR IA para siempre (ASM-137). Una RESUMEN EJECUTIVO también se acepta hacia una sección.
- **Suite lenta** (`slowTest`, a pedido): GetMap contra GeoServer real y un T1 de unas 100 páginas en menos de 180 s.

## 2026-10-15 — wasichai 1.3.0: UPS-31 y UPS-32 resueltas

El backend pasa a wasichai 1.3.0 (plan 8.2b), sin cambios incompatibles, con las mismas dependencias de terceros y las
mismas rutas HTTP.

- **UPS-31** (wasichai/wasichai#113, WAS-ADR-068). La plataforma sube, adjunta y abre archivos dentro de `asPlatform`:
  JOB-05 sube los siete archivos del borrador y los adjunta con LISTO en una sola escritura del mismo bloque
  (`PlatformDraftFiles`, sin cambios de código), y las pruebas de integración dejan el doble que adjuntaba como el
  admin. El respaldo de ASM-133 (`draft_storage_key` y un paso de JOB-08) no se usa.
- **UPS-32** (wasichai/wasichai#115). wasichai-documents congela tablas relacionadas con celdas nulas: la «Constancia
  de emisión» de AUT-12 vuelve a llevar la tabla de las versiones con sus SHA-256.

## 2026-10-13 — Plan 8.0: los documentos de F8

Los documentos de diseño quedan al día con la especificación de F8
(`docs/superpowers/specs/2026-10-13-fase-8-design.md`) antes de tocar el modelo y el código. Las decisiones están en
[Requisitos](requirements-analysis.md) (ASM-133 a ASM-138 y UPS-31; 560 IDs).

- **Alcance.** Solo T2 y T1, T2 primero; T3 a T10 y PUBLICO quedan fuera de F8 (ASM-026). La release 3.0.0 se corta al
  cerrar F8.
- **Archivos.** Los entregables viven en wasichai-files: el último borrador en siete campos `FILE` derivados, lo emitido
  en `deliverable_version` y las plantillas en el objeto nuevo `deliverable_template` (ASM-133); JOB-08 no gana un paso
  de borradores. Que la plataforma adjunte archivos es UPS-31, abierta como wasichai/wasichai#111.
- **Plantillas y mapas.** Plantillas Word por organización con marcadores fijos, estilos requeridos y fuentes
  permitidas (ASM-135, GAP-010 resuelta); mapas por GetMap con POST XML y las decoraciones dibujadas por SGSPE
  (ASM-134).
- **Indicadores.** Cierre de periodo con una cifra completa o ninguna y metas de DOC-01 editables (ASM-136, GAP-018
  resuelta); tableros `executive`, `project` y `monitoring` con `KpiReader`.
- **Emisión.** WF-H con `MODEL_MISMATCH`, `MAP_FAILED` y `SECTIONS_CHANGED`; AUT-12 activa con la «Constancia de
  emisión»; menciones `[[…:<id>]]` y el cerrojo de la asistencia de IA (ASM-137); topes de 64 MB y 20 MB (ASM-138).
- **ADR.** ADR-0013, ADR-0015 y ADR-0016 aceptados; ADR-0014 con los entregables en wasichai-files; Gotenberg en el
  puerto 8096 (ADR-0026).

## 2026-10-13 — Plan 7.0: los documentos de F7

Los documentos de diseño quedan al día con la especificación de F7
(`docs/superpowers/specs/2026-10-12-fase-7-design.md`) antes de tocar el modelo y el código. Las decisiones están en
[Requisitos](requirements-analysis.md) (ASM-128 a ASM-132; 553 IDs).

- **Proveedor.** La API de Anthropic (GAP-015 resuelta); los datos seudonimizados se procesan fuera del Perú, una
  consecuencia aceptada. La IA se activa por organización con la lista del despliegue (ASM-128) y el modelo es
  configurable por caso (ASM-129).
- **Asistente.** UPS-14 queda resuelta (WAS-ADR-0056): AI-05 se ofrece a todo rol con CREATE sobre `ai_interaction`,
  con los resultados de las herramientas seudonimizados, los campos restringidos enmascarados y tokens (ASM-130).
- **Corridas.** Límites del servidor con `AI_INPUT_TOO_LARGE` y `AI_RUN_TRUNCATED` (ASM-131); extracción de PDF y DOCX
  sin OCR, con `AI_NO_TEXT` (ASM-132); `AI_UNAVAILABLE` sin activación o sin proveedor.
- **ADR.** ADR-0003 aceptado y ADR-0005 aceptado en su etapa 1; Embabel 1.5.3 en todos los documentos.

## 2026-10-11 — wasichai 1.2.0: UPS-30 resuelta

El backend pasa a wasichai 1.2.0 (wasichai/wasichai#109, WAS-ADR-067), que responde desde la caché de definiciones lo
que antes leía en cada operación: workflows, automatizaciones, reglas de notificación, relaciones por nombre, campos
`FILE` y el destino de una relación. Sin cambios de código en SGSPE.

- **Medición local** (PostgreSQL 16 con PostGIS, sin túnel): el alta de una entrega con tres evidencias, de 81 a 48 a 52
  consultas (de 178 con wasichai 1.0.0); la lista de una analista, de 5 a 6 a 4 a 5, y la del admin, de 3 a 4 a 2 a 3;
  `transition-checks`, de 11 a 7 a 9.
- **Varios nodos.** Un workflow, una automatización o una regla cambiados llegan a otro nodo dentro de
  `wasichai.metadata.cache.ttl` (30 s), como el modelo (D50 de wasichai).
- **Lo que queda.** Del lado de SGSPE, el memo de las lecturas de registro por petición (el aporte, la entrega y las
  evidencias se releen en el alta).

## 2026-10-11 — Plan 6.0: los documentos de F6

Los documentos de diseño quedan al día con la especificación de F6
(`docs/superpowers/specs/2026-10-11-fase-6-design.md`) antes de tocar el modelo y el código. Las decisiones están en
[Requisitos](requirements-analysis.md) (ASM-120 a ASM-127; 548 IDs).

- **Consultas.** `SpatialGate` es la única puerta de SQL propio y aplica el alcance de lectura; la zona viaja como
  parámetro (`ZoneCriterion`) y las consultas tienen límites propios de tamaño y de tiempo.
- **GeoServer.** Capas públicas de alcance `TENANT`, un proxy WMS de solo imágenes con lista blanca de peticiones, el
  puerto 8095 en loopback con rol de solo lectura y WFS pospuesto.
- **Mapa base.** Se fija por organización; UPS-13 queda resuelta con wasichai-ui#38 en `@wasichai/gis` 1.0.0.
- **Importación y exportación.** GDAL lee Shapefile, KML, KMZ, GeoPackage, FileGDB y GPX; la importación suma los
  destinos `community` y `environmental_feature`, y la exportación aplica la divulgación por capa.
- **Calidad y pantallas.** La pantalla de calidad de datos (S63) y el tablero territorial con KPI-TER.
- **Huecos cerrados.** C1 a C9 de la especificación.
- **Lo que espera al modelo de F6.** Las acciones, los únicos, los índices y las celdas de `EXPORT` quedan como prosa
  hasta que el plan 6.1 los lleve a `model.json` y `roles.json`.

## 2026-10-11 — wasichai 1.1.0: UPS-29 resuelta y sin caché propia de definiciones

El backend pasa a wasichai 1.1.0 (wasichai/wasichai#106, WAS-ADR-066), que guarda las definiciones de objeto por nodo y
lee lo que los roles de quien llama dicen de un objeto una vez por petición (plan corto `2026-10-11-wasichai-1-1`).

- **Sin caché propia.** `ObjectDefinitions` queda como memo por petición (`current`); se retira `recent` (30 s entre
  peticiones), que se apilaba sobre la caché de wasichai sin enterarse de sus invalidaciones, y las claves
  `sgspe.metadata.definition-ttl` y `definition-max-entries`. `ProjectReadScope` y `Permissions.has` leen con `current`.
- **TTL.** `wasichai.metadata.cache.ttl: 30s`, la ventana que `security.md` §9.7 ya aceptaba: en el nodo que recibe un
  cambio del modelo, la petición siguiente lo ve; en otro, a lo sumo 30 s después. Con los 5 s por omisión, la primera
  lista tras una pausa volvía a cargar las definiciones (14 a 16 consultas).
- **Medición local** (PostgreSQL 16 con PostGIS, sin túnel): la lista de una analista, de 9 a 10 consultas a 5 a 6 (admin,
  de 5 a 6 a 3 a 4); `transition-checks`, de 25 a 11; el alta de una entrega con tres evidencias, de 178 a 81.
- **Lo que queda.** UPS-30 (wasichai/wasichai#108): workflows, relaciones, campos `FILE`, destino de una relación,
  reglas de notificación y automatizaciones que wasichai lee en cada operación (31 de las 81 consultas del alta); y,
  del lado de SGSPE, el memo de las lecturas de registro (19).

## 2026-10-11 — Plan 5.0: los documentos de F5

Los documentos de diseño quedan al día con la especificación de F5
(`docs/superpowers/specs/2026-10-11-fase-5-design.md`) antes de tocar el modelo y el código. Las decisiones están en
[Requisitos](requirements-analysis.md) (ASM-110 a ASM-119 y GAP-033; 538 IDs).

- **Aportes.** Los ítems, con su tipo (`item_kind`), se cargan antes de aprobar y su suma es lo que se aprueba y se
  compara con el tope, que cuenta lo comprometido de compromisos y aportes; ítems y términos aprobados quedan
  congelados; una sola entrega por aporte, con acta de recepción, foto y comprobante. WF-P se reescribe con P1 a P9
  (P9, los ítems al aprobar; P6, la sinergia, fuera de los lineamientos que se justifican), su orden y sus candados.
- **Lineamientos.** Mano de obra, donaciones monetarias y tema fuera de los temas priorizados del proyecto
  (`project_priority_topic`) se evalúan solos al aprobar y se justifican juntos; los caseríos sin beneficio laboral
  esperan su dato (GAP-033).
- **Programas.** WF-G gana G1 a G4 (sinergia con el socio como parte del acuerdo, ASM-115), las reglas de los hijos y
  `total_budget` en la moneda del programa; el ejecutado se registra a mano por año (ASM-113).
- **Alcance por proyecto.** Cadenas de hijos (`program_measurement`) y padres alternativos (`delivery`, por su
  compromiso o su aporte, nunca «sin padre, todos»): 33 raíces y 27 hijos desde F5 (ASM-114, `security.md` §9.7).
- **Huecos cerrados.** C1 a C15 de la especificación: entre ellos, AUT-04 y el «Acta de recepción» tras UPS-23 (el acta
  firmada es una evidencia), los permisos que no cerraban (SMG y CRO cargan ítems, CRO comprobantes y
  `COMMITMENT_MANAGER` acepta solo como compromiso) y la autoridad del tope distinta por objeto (ASM-119).
- **Lo que espera al modelo de F5.** El único de `delivery.social_contribution` y `u` sobre `topic` para
  `PROJECT_ADMIN` y `PROJECT_MANAGER` quedan como texto hasta que el plan 5.1 los lleve a `model.json` y `roles.json`.

## 2026-10-11 — UPS-24: el descriptor de una evidencia restringida sin nombre ni huella

El backend adopta la política de lectura de wasichai 1.0.0 y oculta el nombre y la huella del archivo de una evidencia
restringida, que hasta ahora solo reservaba el portal.

- **Política.** `EvidenceFileReadPolicy` implementa `FileDescriptorReadPolicy` (WAS-ADR-065): si la evidencia es
  CONFIDENCIAL o de DATOS PERSONALES, o le falta la clasificación (falla cerrado, como la descarga), quien no tiene
  `DOWNLOAD_RESTRICTED` sobre `evidence` lee el descriptor del campo `file` sin `name` ni `sha256`. ADMIN lo lee entero.
- **Alcance.** La plataforma la aplica en el registro, la lista, los vínculos, las respuestas de escritura, la historia
  y `/api/audit`; la respuesta de la subida (201, sin registro aún) no cambia. La acción se pregunta una vez por
  petición (`RequestMemo`). Vale también para las evidencias subidas antes o por la API.
- **Pruebas.** `EvidenceDescriptorApiTest` (integración) y `EvidenceRulesTest` (unitaria).
- **Documentos.** UPS-24 pasa a resuelta y ASM-093 se reescribe: ya no hay riesgo aceptado del nombre visible; el nombre
  neutro del portal queda como defensa adicional.

## 2026-10-11 — wasichai 1.0.0

Antes del release 1.0.0 de SGSPE, el backend pasa de wasichai 0.6.0 a 1.0.0 y `sgspe-ui` de `@wasichai/*` 0.6.0 a 1.0.0.
Ninguna de las dos trae cambios incompatibles. La 1.0.0 de wasichai suma la zona horaria opcional de los campos
`DATETIME` (wasichai/wasichai#92 y #101) y una política de lectura de los descriptores de los campos `FILE`
(wasichai/wasichai#102), el enganche que pedía UPS-24 para ocultar la huella SHA-256 de una evidencia restringida
también en el backend. La zona horaria queda disponible, sin adoptarla; la política de lectura se adopta en la entrada siguiente (UPS-24).

## 2026-10-10 — Releases con GitHub Actions

`sgspe` suma el soporte de releases de wasichai ([`develop/releasing.md`](develop/releasing.md)), sin cambiar el código de la
aplicación.

- **Versión y changelog.** `release-please.yml` abre el PR de release a partir de los commits convencionales de `main`;
  al mezclarlo crea el tag `vX.Y.Z` y el GitHub Release. La versión vive en `gradle.properties` y parte de `0.0.0`.
- **Jar del release.** `publish.yml` compila el `bootJar` con la versión del tag y lo adjunta al release como
  `sgspe-X.Y.Z.jar`. A diferencia de wasichai, que publica librerías Maven, `sgspe` es una aplicación; el despliegue
  sigue pendiente de GAP-022.
- **Pendiente del usuario.** El secreto `RELEASE_PLEASE_TOKEN`, para que el PR de release dispare la CI y el release
  dispare `publish.yml`.

## 2026-10-09 — Plan 4.0: los documentos de F4

Los documentos de diseño quedan al día con la especificación de F4
(`docs/superpowers/specs/2026-10-09-fase-4-design.md`) antes de tocar el modelo y el código. Las decisiones están en
[Requisitos](requirements-analysis.md) (ASM-095 a ASM-104; UPS-25 y UPS-26, candidatas sin issue; 520 IDs).

- **QCS confidenciales.** `READ_CONFIDENTIAL` y el `RecordReadScope` de las QCS confidenciales (`security.md` §9.6 y
  [ADR-0028](adr/0028-qcs-confidenciales-con-recordreadscope.md), con la excepción de `ConfidentialRelease` para el
  cierre automático de una solicitud) reemplazan la respuesta del checkpoint «sin restringir en el MVP»; el alcance por
  proyecto sigue en la opción A.
- **Huecos cerrados.** Documentos de QCS tras UPS-23 (C1), días hábiles con feriados en configuración (C7), los seis
  tipos de regla de JOB-04 sin CAMBIO DE POSTURA (C10 y C6) y el contenido de `attention-register` y `risk-register`
  (C12); además, las contradicciones menores C2 a C5, C8, C9, C11 y C13 a C17.
- **Pantallas.** La ficha de consulta y solicitud pasa a S57, y S58 a S61 son las fichas de menciones, señales, reglas e
  impactos. El tablero de riesgos, que compartía S38 con el registro y matriz, pasa a S62; `ux-architecture.md` §9
  suma S57 a S62 a su inventario.

## 2026-10-09 — Plan 3.2: el backend de F3

El backend de F3 (compromisos y evidencias) queda construido en la rama `feat/fase-3-2-backend`, en 24 tareas más las
correcciones que dejó la revisión. La verificación de integración corre en la CI y por el túnel; las decisiones están en
[Requisitos](requirements-analysis.md) (ASM-091 a ASM-094, UPS-24) y en [ADR-0027](adr/0027-if-match-con-saltos-exactos-de-derivados.md).

- **Paquetes nuevos.** `commitment`, `agreement`, `evidence`, `investment`, `deadline` y `notification`; en `common`,
  `Idempotency` (tabla `sgspe.idempotency_key`), JOB-08, `JobTasks` y `Amounts`. `ArchitectureTest` ve una fuente de cada uno.
- **Pérdida de actualización en `If-Match` (también en F2).** Desde wasichai 0.6.0, `IfMatch.onlyDerivedSince` decidía por
  tiempo si una escritura posterior era solo de derivados, y de forma intermitente dejaba pasar una escritura ajena. El
  arreglo (ADR-0027, ASM-091) guarda un salto de versión por escritura de derivados en `sgspe.derived_version` (siete días),
  y la caída sin condición hace un merge-patch solo de las claves derivadas, con `412` para versiones viejas (a costa de un
  `412` espurio bajo contención). Rige igual para los objetos de F2.
- **Montos y emisión.** Todo monto escrito se valida con `Amounts` en una guarda (ASM-092); emitir un documento exige la
  asignación al proyecto del registro y `EDIT_ANY`, porque emitir es editar ese registro.
- **Nombre de bean.** `Deliveries` pasó a `CommitmentDeliveries`: chocaba con el bean `deliveries` de wasichai-notifications.
- **Dos rondas de la CI.** La primera corrigió una exportación de un solo proyecto que respondía 500, pruebas con rutas que
  ya existen y el `409` sin código de los objetos solo de anexar, que ahora rige la plataforma; la segunda, solo pruebas
  (lectura de todo el tenant por CON-041, asistente de documentos, campos de ejemplo).
- **Pendiente.** (1) Actualizar el tenant de desarrollo de F2 a F3 y comprobar MinIO: necesitan el túnel del usuario.
  (2) El original de cada importación como evidencia (ASM-060, «desde F3») no tiene plan todavía. (3) El portal de F3 es el
  plan 3.3, que sube la evidencia restringida con un nombre neutro (el nombre del archivo no se enmascara en el backend,
  UPS-24, ASM-093 y ASM-094).

## 2026-10-09 — Adopción de UPS-03, UPS-09 y UPS-17

Tres soluciones de wasichai 0.6.0 pasan de disponibles a adoptadas ([Encaje con wasichai §4.3](platform-fit.md#43-lo-que-trae-wasichai-060)).

- **UPS-17.** `Organizations` lista los tenants con `TenantDirectory.organizationsWithObject`; `ArchitectureTest` rechaza
  `findAllOrganizations` en todo el código y `TenantDirectory` fuera de `Organizations`.
- **UPS-03.** JOB-02 y JOB-03 escriben con `Records.asPlatform(organización, source, …)`: `job:derived-fields` y
  `job:stakeholder-follow-up` quedan en `audit_log.source`. El motivo conserva el nombre del trabajo (ASM-051).
- **UPS-09.** `application-prod.yml` enciende la revocación de tokens, el límite de 5 y 20 intentos de login por 15 minutos y
  la política de contraseñas de 12 caracteres con mayúscula, minúscula, dígito y símbolo; `ProdProfileTest` los fija y
  [Seguridad §3.2](security.md#32-endurecimiento-de-sgspe) los documenta.

## 2026-10-09 — wasichai 0.6.0

El backend pasa de wasichai 0.3.2 a 0.6.0. La evaluación, brecha por brecha, está en
[Encaje con wasichai §4.3](platform-fit.md#43-lo-que-trae-wasichai-060): la actualización resuelve UPS-02, UPS-03, UPS-12 y,
para las altas, UPS-15, y deja disponibles, sin adoptarlas, las soluciones upstream de UPS-01, UPS-04 a UPS-07, UPS-09 a
UPS-11, UPS-14, UPS-16 a UPS-18 y UPS-21. Tres cambios de la plataforma tocaban código de SGSPE:

- **`If-Match`.** wasichai lo compara ahora en la propia escritura, con igualdad estricta, y respondería `412` donde SGSPE
  tolera escrituras posteriores de derivados. `IfMatchFilter` quita la cabecera después de guardarla, así que la
  precondición sigue siendo solo la de SGSPE, también para el `PATCH` nuevo.
- **`updated_at`.** Toma la hora de su sentencia (`clock_timestamp()`), ya no la de la transacción, así que la entrada de
  auditoría de una escritura dentro de una transacción cae antes de su `updatedAt`. `IfMatch.onlyDerivedSince` toma como
  versión la entrada más cercana a lo leído, antes o después; con la regla anterior, un cambio de geometría posterior a la
  escritura del área por un listener pasaba sin `412`.
- **`X-Correlation-Id`.** La plataforma tiene su propio filtro y guarda el identificador en cada fila de auditoría.
  `CorrelationIdFilter` corre después y usa ese, así la respuesta, la línea de acceso y la auditoría dicen lo mismo aunque
  el cliente no mande ninguno.

## 2026-10-07 — Diseño UX/UI: marca, idiomas, vistas compartidas, mapa base y temas

El usuario respondió las preguntas abiertas Q1, Q3, Q6 y Q7 de `ux/ux-architecture.md` §10.

- **Marca propia (ASM-082).** Página 22 del lienzo de la etapa 3: tres direcciones de isotipo; el usuario eligió la C,
  «Sendero». Sistema de marca y aplicaciones en `ux/design-system.md` §2.1; la paleta es la de «Pino y piedra» más una arena
  oscura para la versión invertida, y los tokens semánticos no cambian.
- **Idiomas (ASM-084).** Página 23: la interfaz del portal en español, inglés y quechua, con selector en el perfil, el login y
  el teléfono; `/admin`, metadata y entregables siguen en español. Reglas en `ux/design-system.md` §9.1. Responde GAP-013.
- **Vistas compartidas (ASM-083).** Página 24: guardar una vista con visibilidad solo yo, equipo del proyecto u organización,
  y la pantalla nueva S56 «Gestionar vistas». Reemplaza ASM-079 y pide un objeto `saved_view` a la sesión de implementación.
- **Mapa base (ASM-085).** Página 24 y `gis.md` §6.4: las librerías de srtm-ui; OpenStreetMap ráster solo en desarrollo y
  estilo configurable en producción, sin volver a OpenStreetMap.
- **Temas (ASM-086).** Página 25 y `ux/themes.css`: Pino y piedra (el actual, predeterminado), Cielo (blanco y azul) y Pino
  de noche (oscuro, distinto del de wasichai), cada uno con los 28 tokens de `@wasichai/ui` (WAS-ADR-034, WAS-ADR-035) y
  contraste AA medido en `ux/design-system.md` §3.9. UPS-19 pide elegir qué temas usa «Según el sistema»; se pidió en
  wasichai/wasichai-ui#39. Corrección posterior: guardar `sgspe-pino` como preferencia inicial no sirve, porque la
  preferencia del servidor manda; hasta UPS-19 el tema inicial es el de wasichai y cada persona elige el de SGSPE.
- El análisis pasa a 495 IDs.

## 2026-10-07 — Diseño UX/UI: pendientes de la etapa 3

- **Shell responsive.** Página 21 del lienzo de la etapa 3: el panel de navegación en tableta y la barra inferior del
  teléfono con sus cuatro destinos por rol, el menú «Más» y el aviso de pantalla de escritorio en el teléfono, que la Fase 1
  dejó como pendiente de diseño. Reglas en `ux/ux-architecture.md` §7.1 y `ux/design-system.md` §3.6 y §8.
- **Estados.** `ux/design-system.md` §4.1 suma acuerdo, entregable, visita de campo, incidente y aporte: los 18 workflows
  tienen tono e ícono. En el lienzo, el aporte cerrado pasa al tono «final».

## 2026-10-07 — Diseño UX/UI, etapa 3, parte 3b: tableros, capa de IA, transversales, campo sin conexión y handoff

Segunda parte de la última etapa, después de la revisión de la 3a. Con ella el inventario de 55 pantallas queda diseñado.

- **Lienzo de la etapa 3**, páginas 16 a 20, con 25 hojas: tableros (revisión ejecutiva de 5 minutos, monitoreo de la Tabla
  9.5 con cifra oficial o viva y "no disponible", compromisos, QCS, riesgos, territorial e inversión social), capa de IA (panel
  del asistente por rol y estado de la organización, respuesta con citas, bandeja de revisión, comparación de duplicados,
  resumen ejecutivo y borrador de sección), inicio y transversales (login, inicio por rol, avisos, reportes y exportaciones con
  nivel de divulgación, entregable por secciones, administración), campo sin conexión en el teléfono (tareas y visita,
  captura, cola y conflictos de sincronización) y handoff (flujo 18, flujo de campo, mapa de rutas y árbol de módulos).
- **[`ux/screens.md`](ux/screens.md):** fichas de S01, S02, S04, S05, S27, S34, S38, S49 a S55, del monitoreo, del tablero
  territorial, del de inversión, de la respuesta con citas, del duplicado, del resumen de IA y de la cola de campo.
- **[`ux/flows.md`](ux/flows.md):** flujo 18 (preguntar a la IA) y flujo de campo sin conexión.
- **Decisión del usuario:** la captura de campo sin conexión se diseña. ASM-081 la registra, responde GAP-008 y deja su
  implementación después del MVP, con un ADR de sincronización; el análisis pasa a 489 IDs. Se actualizan Q5 y las rutas en
  `ux-architecture.md`, los componentes de IA, conexión y sincronización en `design-system.md` y el roadmap.

## 2026-10-07 — Diseño UX/UI, etapa 3, parte 3a: QCS, riesgos, programas, documentos y tareas

Primera de las dos partes de la última etapa, en el orden de USR-10: QCS ← riesgos → programas, más las piezas transversales
de documentos, tareas y aprobaciones.

- **Lienzo de la etapa 3** con 18 hojas en cinco páginas: QCS (lista unificada por plazo, registro por tipo, ficha de queja
  interactiva con la segregación de la resolución ALTA y CRITICA, consulta y solicitud con aceptación como compromiso o
  aporte), riesgos (registro con mapa de calor interactivo, ficha con revisiones inmutables y mitigación, centro de alerta
  temprana interactivo con la confirmación solo por una persona, incidente), programas e inversión (lista con el tope de la
  campaña, ficha de programa, aporte con las políticas P1 a P8), documentos (biblioteca y ficha de evidencia con descarga
  restringida), tareas (lista, tablero y calendario interactivos; aprobaciones y escalamientos) y dos hojas de flujos.
- **[`ux/screens.md`](ux/screens.md):** fichas de S35 a S48 y de la ficha de incidente, con el mismo formato que la etapa 2.
- **[`ux/flows.md`](ux/flows.md):** flujos 12 a 15 (crear y resolver una queja, identificar un riesgo y mitigarlo).
- **Decisión del usuario para la parte 3b:** la captura de campo sin conexión entra al diseño; el supuesto que la registra y
  sus cambios en el análisis se agregan con esa parte.

## 2026-10-07 — Fase 1: fundación

La Fase 1 deja SGSPE compilando y ejecutable sobre wasichai 0.3.2 y `@wasichai/*` 0.5.0, todavía sin objetos de negocio:
el arranque de la app, los roles como datos, el contrato con la plataforma, la observabilidad y la CI de los dos
repositorios. El plan ejecutable está en
[`superpowers/plans/2026-10-07-fase-1-fundacion.md`](superpowers/plans/2026-10-07-fase-1-fundacion.md).

Estado: las pistas 0, A, B y C están en verde en local y en la CI de los dos PR (wasichai/sgspe#2: build, integración, modelo,
trazabilidad, formato y commits; wasichai/sgspe-ui#1: web y commits), el 2026-10-07. «Implementado» en la matriz de
trazabilidad significa que pasa en la CI de su fase. Dependabot de `sgspe` también necesita `PACKAGES_TOKEN`, como secreto de Dependabot del repositorio
(`docs/develop/README.md`, sección 1); sin él, sus actualizaciones de Gradle no leen GitHub Packages.

### Backend (`sgspe`)

- **App.** `sgspe.SgspeApplication` con `@WasichaiApplication` y los starters core, views, forms y pages; puerto 8094;
  sondas `/actuator/health/liveness` y `/readiness`; `compose.yml` con `postgis/postgis:18-3.6` en el 5435 del VPS.
- **Modelo como código.** `model.json` sin objetos; `roles.json` con los 15 roles declarados y su etiqueta en español, y
  `SUPER_ADMIN` y `ORGANIZATION_ADMIN` mapeados a `ADMIN`; solo `AUDITOR` lee todo el tenant. `apply_roles.py` entiende `"*"`,
  `u` y `"derived": true`, y deshace `ownRecordsOnly`; `apply.py --workflows` es el segundo pase del aprovisionamiento.
- **Contrato con la plataforma.** `TenantIsolationApiTest`, `TenantProvisioningApiTest`, `SecurityApiTest`,
  `RolesApiTest`, `AuditApiTest` y `ErrorContractApiTest`, sobre `SgspeApiTest`, que aprovisiona con los scripts reales.
- **Observabilidad y producción.** `X-Correlation-Id` antes de la seguridad, también en un 401, y una línea de acceso sin
  la cadena de consulta; `application-prod.yml` sin semilla, con secreto obligatorio, token de 4 horas y logs ECS, y
  `ProdGuard`, que con el perfil `prod` impide el arranque, antes de Flyway, si la semilla está encendida, si el secreto es el
  de desarrollo o si el token dura 8 horas o más.
- **`sgspe.common`.** La marca `SgspeWrite`, `Records` sobre `RecordService`, la hora de Lima y el 422 de parámetros
  desconocidos.
- **Arquitectura y CI.** `ArchitectureTest`; CI con build, integración, modelo, trazabilidad (`tools/check_traceability.py`)
  y formato.

### Interfaz (`sgspe-ui`)

- Una app Vite con el portal y `/admin` (`WasichaiApp` con views, forms y pages), login compartido en `sgspe.token`,
  `RequireSession` y `safeNext`; el tema «Pino y piedra» de `docs/ux/tokens.css` en claro y oscuro, sobre el de
  `@wasichai/ui` y con IBM Plex servida desde el mismo origen, con el script de arranque autorizado por hash en la CSP; el
  shell y la navegación de `docs/ux/ux-architecture.md` (las 13 secciones con sus rutas y su fase, «Preguntar»
  deshabilitado hasta F7); español e inglés con números y fechas es-PE; la bienvenida de F1 con el registro del inicio
  por rol; CI con `PACKAGES_TOKEN` y `boundaries.test`.

### Spike y hallazgos

- El spike de entregables y GetMap dejó su resultado en el «Seguimiento» de ADR-0013 y ADR-0015.
- wasichai 0.3.2 responde el 401 sin token con `WWW-Authenticate: Bearer` y cuerpo vacío, no con Problem Details;
  `ErrorContractApiTest` fija ese contrato.
- wasichai 0.3.2 ignora sin error un atributo que el objeto no tiene; `ErrorContractApiTest` también fija ese contrato.
- Un token emitido antes de deshabilitar a un usuario sigue valiendo hasta que vence (UPS-09, wasichai#55);
  `SecurityApiTest` lo fija.
- Las entradas de permisos de campo no se pueden borrar: un PUT con lista vacía no hace nada y no existe DELETE.
  `apply_roles.py` las neutraliza con lectura y escritura en verdadero.
- `apply.py` de caja rechazaba un modelo sin objetos; la copia de SGSPE lo acepta.

### Verificación

- Pista C: aprovisionamiento en orden y prueba de humo con Playwright MCP, con la app arrancada en unos 11 s contra el
  túnel y PostGIS. El aprovisionamiento es idempotente: `apply.py` sin altas, cambios ni omitidos, `apply_roles.py` con
  «15 skipped» y `--workflows` en cero.
- `X-Correlation-Id`: sin cabecera se genera un UUID, `humo-1` se conserva y un valor hostil se reemplaza por un UUID.
- Como `ADMIN`: login con «Pino y piedra» (fondo rgb 245, 246, 243), IBM Plex cargada y cero peticiones a otro host;
  Dashboard con la organización «Demo», el rol ADMIN, la tabla «Módulos por fase» de 13 filas, navegación con Dashboard y
  Administración y «Preguntar» deshabilitado; `/admin/admin/roles` abre sin otro login y muestra las 15 etiquetas en
  español más ADMIN.
- Como `AUDITOR`: la navegación muestra solo Dashboard y Administración dice «Solo para administración»;
  `/admin/admin/audit` carga sin error de permisos pero muestra «Sin resultados», porque el alta de usuarios y de roles no
  se audita (UPS-02, wasichai#49); por la API, auditoría 200, usuarios 403 y roles 403.
- CSP en solo informe en desarrollo, con el hash del script; sin violaciones en la consola con la compilación de
  producción (`vite preview`).
- Suites: `build` en verde; `integrationTest` con 34 pruebas y 0 fallas; modelo y herramientas de Python en verde;
  trazabilidad con 298 requisitos; prettier en verde; interfaz con lint y typecheck en verde, 11 archivos con 109 pruebas tras el cierre (107 en la prueba de humo) y
  `build` en verde.

## 2026-10-07 — Diseño UX/UI, etapa 2: Project 360, territorio, grupos de interés, relacionamiento y compromisos

Segunda de las tres etapas, en el orden que fijó el usuario (USR-10): los objetos que definen la navegación posterior.

- **Lienzo de la etapa 2** con 28 hojas en seis páginas: Project 360 (crear proyecto, Resumen, mapa, equipo y parámetros),
  territorio (mapa territorial interactivo, fichas de comunidad y de localidad con línea base, área de influencia con
  versiones y aprobación, importación geoespacial con CRS de origen y decisiones), grupos de interés (lista, matriz interés ×
  poder interactiva, ficha, evaluación con vista previa, ficha de actor), relacionamiento (calendario, ficha de interacción,
  registro con modo rápido de campo, editor de acta, visita de campo), compromisos (pestañas de la ficha, creación, aprobación,
  entrega → cumplimiento → verificación interactiva, candidatos, acuerdos) y dos hojas de flujos.
- **[`ux/screens.md`](ux/screens.md):** ficha de handoff de S06 a S26 y de S28 a S33: ruta, propósito, roles según la matriz de
  seguridad, acciones, disposición, datos y API, estados, permisos, responsive, navegación y árbol de componentes React.
- **[`ux/flows.md`](ux/flows.md):** flujos 1 a 11, 16 y 17, paso a paso, con pantalla, transición o endpoint y regla.
- **Ajustes:** rutas de lista bajo el proyecto para relacionamiento y compromisos, el tablero de compromisos (S27) pasa a la
  etapa 3 con los demás tableros, la acción masiva "Asignar verificador" en el patrón de lista y el supuesto ASM-080 (la
  jefatura del proyecto es la asignación activa con `PROJECT_MANAGER`) en el análisis (488 IDs).

## 2026-10-07 — Diseño UX/UI, etapa 1: arquitectura de UX, navegación y design system

El usuario pidió la UX/UI completa del portal con un prompt maestro de UX/UI (PRM-02) y fijó el orden de trabajo y el principio
de navegación contextual (USR-10). El diseño se entrega en tres etapas con revisión; esta es la primera.

- **Lienzo de diseño** con 17 hojas: visión UX, mapa del dominio, roles, tres contextos de navegación, inventario de 55
  pantallas, shell, navegación por rol, prototipo navegable del hilo de contexto (comunidad → grupo de interés → interacción →
  compromiso → evidencia), búsqueda global, fundamentos, sistema de estados, componentes y cuatro patrones núcleo.
- **[`ux/ux-architecture.md`](ux/ux-architecture.md):** visión, principios, decisiones heredadas de la Fase 0 donde PRM-02 choca
  con los documentos, roles y trabajos por hacer, arquitectura de información, rutas, navegación, hilo de contexto y
  trazabilidad, inventario de pantallas y preguntas abiertas.
- **[`ux/design-system.md`](ux/design-system.md) y [`ux/tokens.css`](ux/tokens.css):** dirección visual "Pino y piedra",
  tokens en claro y oscuro con contraste verificado, sistema de estados por workflow, situación derivada, nivel y procedencia,
  catálogo de componentes, patrones núcleo, accesibilidad WCAG 2.2 AA, responsive y guía de implementación.
- **[`requirements-analysis.md`](requirements-analysis.md):** fuente PRM-02, decisión USR-10, regla de precedencia para el diseño
  de la interfaz y supuestos ASM-073 a ASM-079 (487 IDs).

## 2026-10-07 — Fase 0: descubrimiento y diseño

La Fase 0 entrega el diseño completo de SGSPE, sin código, sobre wasichai 0.3.2 (backend) y `@wasichai/*` 0.5.0 (UI).

### Fuentes y requisitos

- **Fuentes analizadas.** Se citan por su ID y no se versionan:
  - DOC-01: diagnóstico social de áreas de influencia de un proyecto de exploración, 2018;
  - DOC-02: informe de trabajo de campo y avance, 2026;
  - DOC-03: captura de ArcMap con sondajes, AID y predios;
  - PRM-01: el prompt maestro;
  - USR: los pedidos del usuario.
- **Pedidos del usuario incorporados** (USR):
  - generar entregables como DOC-01 (DOCX) y DOC-02 (PDF);
  - ver la situación en GIS y en tableros;
  - construir sobre wasichai como srtm y caja (la plantilla vigente es caja-backend y caja-ui);
  - nombres técnicos en inglés;
  - anonimizar los datos personales.
- **[`requirements-analysis.md`](requirements-analysis.md):** 480 IDs, entre ellos:
  - requisitos funcionales, no funcionales, reglas y RQ de GIS, entregables, tableros, seguridad, auditoría e IA;
  - 60 contradicciones, 32 vacíos, 72 supuestos y 18 brechas de wasichai (UPS).
  - [`glossary.md`](glossary.md) da la equivalencia español–inglés.

### Diseño

- **Dominio.** [`domain-model.md`](domain-model.md) define 20 contextos delimitados y 89 objetos, con sus reglas, guardas,
  campos derivados, listeners, paquetes `sgspe.*` y trabajos programados. Es la base de los demás documentos:
  - [`architecture.md`](architecture.md), [`platform-fit.md`](platform-fit.md) y [`api.md`](api.md): arquitectura sobre
    wasichai, encaje con la plataforma y API del portal `/api/sgspe/**`;
  - [`database-model.md`](database-model.md): modelo de metadata (fichas de objeto, relaciones, índices, banderas y
    migraciones);
  - [`workflows.md`](workflows.md): workflows WF-A a WF-P, destinatarios por rol, escalamiento y JOB-01 a JOB-08;
  - [`security.md`](security.md): 17 roles, matriz de permisos y de campos, alcance por proyecto y datos personales;
  - [`gis.md`](gis.md): capas LYR-01 a LYR-24, SRID 4326 con CRS UTM por proyecto, importación con reconciliación y
    GeoServer;
  - [`deliverables.md`](deliverables.md): entregables DOCX primero, PDF por Gotenberg, niveles de divulgación y emisión
    congelada;
  - [`dashboards.md`](dashboards.md): catálogo de KPIs calculados en vivo y tableros;
  - [`ai-architecture.md`](ai-architecture.md): IA con validación humana, lista cerrada de aceptación y
    seudonimización.
- **Decisiones.** [`adr/`](adr/README.md) registra ADR-0001 a ADR-0026. Las decisiones de plataforma se citan como
  `WAS-ADR-00NN`.
- **Trazabilidad y plan.** [`requirements-traceability.md`](requirements-traceability.md) cubre 298 requisitos y
  [`roadmap.md`](roadmap.md) las fases F0 a F8, el plan de la Fase 1 y las decisiones del checkpoint.
- **Planes.** [`superpowers/plans/`](superpowers/plans/2026-10-06-sgspe-fase-0-y-1.md) guarda el plan aprobado de las
  fases 0 y 1 y el plan ejecutable de la Fase 0.

### Proceso

- Cada documento lo escribió un subagente y lo revisó otro.
- Los documentos derivados se escribieron en paralelo y se conciliaron entre sí.
- Una verificación automática comprueba que:
  - los IDs, las secciones, los objetos, los roles y los enlaces sean coherentes;
  - no aparezca ningún nombre real de las fuentes;
  - no se versione ningún insumo transitorio.
- La Fase 1 empieza solo después del checkpoint con el usuario.

### Checkpoint

El 2026-10-07 el usuario aprobó los documentos y autorizó F1, con todos los valores por defecto del diseño. También pidió
abrir los 18 issues upstream, preparar la BD de desarrollo en el VPS y hacer el spike de entregables y GetMap. Las respuestas
están en [`roadmap.md`](roadmap.md) ("Respuestas del checkpoint").
