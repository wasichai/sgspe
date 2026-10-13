# CLAUDE.md

Guía para agentes que trabajan en este repositorio. El diseño manda: `docs/` es la fuente de verdad y
`docs/requirements-analysis.md` es el único lugar donde se definen IDs (RF, RNF, RN, RQ, CON, GAP, ASM, UPS).

## Qué hay aquí

- Backend de SGSPE en la raíz: Spring Boot 4.1.1 WebFlux sobre wasichai 1.2.0 (`wasichai-bom`), Kotlin 2.4.20, JDK 25,
  Gradle 9.8.0. Paquetes propios `sgspe.*` (`docs/architecture.md` §5.2): `common`, `observability`, `scope`, `engagement`,
  `stakeholder`, `territory`, `dashboard`, `privacy`, `prod` y, desde F3, `commitment`, `agreement`, `evidence`, `investment`,
  `deadline` y `notification` y, desde F4, `attention`, `risk` y `warning` y, desde F7, `ai`.
- `model/`: `model.json` (los 84 objetos de F2 a F7), `roles.json`, `workflows/` (los 18 de F2 a
  F7: WF-A, WF-B, WF-C, WF-L, WF-O, WF-D, WF-I, WF-K (de candidatos y, desde F7, de sugerencias de IA), WF-N, WF-E1,
  WF-E2, WF-E3, WF-F, WF-J, WF-M, WF-G y WF-P), `document-types/`, `automations/` y los
  scripts de Python (solo biblioteca estándar) que los aplican por REST, más `load_ubigeo.py` y `design_docs.py`, que lee
  los documentos para las pruebas del modelo.
- `docs/`: análisis, diseño, ADR, trazabilidad, roadmap y planes. La interfaz vive en `../sgspe-ui`.

## Reglas

- Español (es-PE) en documentos, comentarios y mensajes; nombres técnicos en inglés (ADR-0009).
- Nunca versionar `initial-promp.md`, `arch-*.md`, `tech-landing.txt` ni nada de `~/Documents/gspe`. `git add` siempre con
  rutas explícitas.
- Ningún nombre real de persona en nada versionado; usuarios de prueba ficticios `@sgspe.test`.
- Conventional Commits con asunto en español y cuerpo de 100 columnas como máximo (commitlint con husky).
- Los releases los corta release-please (`docs/develop/releasing.md`): nunca editar a mano `version=` de `gradle.properties`,
  `.release-please-manifest.json` ni `CHANGELOG.md`.
- Ningún código bajo el paquete `wasichai`; nunca `RecordStore`, `PhysicalTableRecordStore`, `AuditService` ni
  `ObjectSchemaManager`; ningún literal de rol en `src/main/kotlin`. `ArchitectureTest` lo vigila, y desde F2 también que
  `scope` y `privacy` solo dependan de `common`, que `findAllOrganizations` no aparezca en ningún archivo y que
  `TenantDirectory` solo viva en `common/Organizations.kt`.
- Roles solo en `model/roles.json` y modelo solo en `model/model.json`: `/admin` no es fuente de verdad (GAP-030).
  Aprovisionar siempre en el orden `apply.py` → `apply_roles.py` → `apply.py --workflows` (workflows, tipos de documento
  y automatizaciones) → `load_ubigeo.py` → `load_indicators.py` (desde F8, plan 8.2a) → `publish_layers.py` (omitible
  con `--skip-layers` si el despliegue no tiene GeoServer). Por el túnel, los tres primeros pasos tardan varios minutos
  y más con cada fase (más de 10 con el modelo de F5), y la suite de integración completa, de 30 minutos a más de una
  hora (la matriz de permisos, entre 11 y 58 minutos según la carga del túnel, aprovisionamiento incluido).
- Gradle: `GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew …`. El token nunca va a un archivo.
- Docker es remoto: las pruebas de integración corren en la CI o con `WASICHAI_TEST_DB_*` contra `sgspe_test` por el túnel
  del puerto 5435 (`docs/develop/README.md` §6).
- TDD: la prueba primero. `./gradlew ktlintFormat` antes de `build`; `yarn format` antes de commitear yaml o json.
- Puertos: backend 8094, interfaz 5182, PostGIS 5435; en desarrollo, MinIO 9095 (consola 9096) y Mailpit 1029 (web 8029).
- GeoServer 8095 y, desde F8, Gotenberg 8096 (solo loopback, por el túnel).
- Archivos: `evidence.file` es un campo `FILE` de wasichai-files (D1, ADR-0014); un documento emitido congela el registro
  entero (UPS-23): ningún tipo de documento sobre un objeto con campos restringidos.
  La descarga de un campo `FILE` con datos restringidos pasa por SGSPE (`EvidenceContent`), e `IssuedDocumentsPrivacyTest`
  vigila la regla de los documentos del lado de la aplicación. El descriptor de una evidencia restringida sale sin
  nombre ni huella a quien no tiene `DOWNLOAD_RESTRICTED` (`EvidenceFileReadPolicy`).
- IA (F7): la clave del proveedor solo por `ANTHROPIC_API_KEY` en el entorno, nunca en un archivo (tampoco en
  `develop/.env`); la IA se activa por organización con `SGSPE_AI_ENABLED_ORGANIZATIONS` (slugs). Ninguna prueba llama
  al proveedor: las variables de la clave quedan en blanco en `test` e `integrationTest`, y la única clase con clave
  (`AssistantRunApiTest`) la pone falsa, con `RecordingLlm` delante. Solo `sgspe.ai` importa `wasichai.agent` y
  `com.embabel`; los logs nunca llevan una pregunta, un prompt ni una respuesta.

## Comandos

| Para | Comando |
|---|---|
| Build y pruebas unitarias | `GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew build` |
| Integración (base externa) | `WASICHAI_TEST_DB_HOST=localhost WASICHAI_TEST_DB_PORT=5435 WASICHAI_TEST_DB_NAME=sgspe_test WASICHAI_TEST_DB_USERNAME=sgspe WASICHAI_TEST_DB_PASSWORD=sgspe GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew integrationTest --rerun` |
| Modelo | `cd model && python3 -m unittest -v && python3 apply.py --validate-only && python3 load_ubigeo.py --dry-run` |
| Alta de un tenant (operador) | `cd model && SGSPE_OPERATOR_PASSWORD=… SGSPE_TENANT_ADMIN_PASSWORD=… python3 provision_tenant.py --core URL --operator-email … --name … --slug … --admin-email …` |
| Subida de fase de los tenants | `cd model && python3 upgrade_tenants.py --core URL --inventory RUTA` (contraseñas por `SGSPE_TENANT_PASSWORD_<SLUG>` o consola) |
| Trazabilidad | `python3 tools/check_traceability.py` |
| Formato | `yarn format:check` |

## Dónde está cada cosa

| Tema | Ruta |
|---|---|
| App | `src/main/kotlin/sgspe/SgspeApplication.kt`, `src/main/resources/application*.yml` |
| Correlación y línea de acceso | `src/main/kotlin/sgspe/observability/` |
| Marca de escritura, `Records`, Lima, 422, catálogo del modelo, reglas y guardas transversales, `If-Match`, consulta en seco, `Positions`, `Readability`, `RequestMemo` (también el memo del comando que `RequestMemoFilter` abre en las escrituras de `/api/sgspe/`), el memo de definiciones por petición (`ObjectDefinitions.current`, sobre la caché de wasichai 1.1.0, `wasichai.metadata.cache.ttl` 30 s), JOB-02 | `src/main/kotlin/sgspe/common/` |
| Alcance por proyecto, asignaciones, destinatarios por rol, `EDIT_ANY`, WF-A | `src/main/kotlin/sgspe/scope/` |
| Evaluaciones, índices y grupo, prioridad, WF-B, JOB-03 | `src/main/kotlin/sgspe/stakeholder/` |
| WF-C, conteos de asistentes | `src/main/kotlin/sgspe/engagement/` |
| CRS, área oficial, áreas de influencia, importaciones, componentes, capas LYR-01 a LYR-16 y, desde el plan 6.2c, las de gestión LYR-17 a LYR-24 (`ManagementLayerCatalog`, `PlacedSql`, `LocalityAggregateSql`, `ManagementLayers`) (`sgspe.territory.import.*`, `sgspe.territory.layers.*`) | `src/main/kotlin/sgspe/territory/` |
| Consultas espaciales: `SpatialGate` (la única puerta de SQL propio, con `RecordReadScopes`), `ZoneCriterion` y `TerritoryZones`, límites `sgspe.territory.queries.*` (`TerritoryQueryProperties`, `QueryParams`), medidas (`SpatialSql`), los 13 endpoints (`ZoneQueries`, `MemberSuggestions`, `ProjectLandQueries`, `ProjectProximityQueries`, `TerritoryAggregates`, `TerritoryCards`) y `ComponentLand` | `src/main/kotlin/sgspe/territory/` |
| KPI-TER y el tablero `territory` (`TerritoryKpis`, `TerritorySummary`, `KpiEvaluation`) | `src/main/kotlin/sgspe/dashboard/` |
| Base de las pruebas de integración del plan 6.2a (geometrías en metros desde el origen sintético, áreas y miembros) | `src/test/kotlin/sgspe/F6QueryApiTest.kt` |
| Servicios SIG: mapa base (`MapSettings`), proxy WMS (`WmsRequestFilter`, `WmsProxy`), GDAL (`GdalCommand`, `GdalTools`, `GdalReader`, `ImportFormats`, `GdalWriter`), exportaciones geográficas (`ExportDisclosure`, `TerritoryExports`), validación (`GeometryChecks`) y calidad (`DataQuality`) | `src/main/kotlin/sgspe/territory/` |
| Capas públicas de GeoServer (`layers.json`), estilos SLD y paleta (`styles/`), publicación (`publish_layers.py`) | `model/` |
| KPI-STK, KPI-ENG y tableros | `src/main/kotlin/sgspe/dashboard/` |
| Exportaciones con nivel de divulgación, celdas pequeñas (`SmallCells`) y `export_log` | `src/main/kotlin/sgspe/privacy/` |
| Idempotencia de los comandos (`Idempotency`, tabla `sgspe.idempotency_key`), JOB-08, `JobTasks`, `Amounts` | `src/main/kotlin/sgspe/common/`, `src/main/resources/db/sgspe/common/` |
| Compromisos: `CommitmentWriteGuard` y sus reglas, reprogramaciones, entregas, candidatos, derivados | `src/main/kotlin/sgspe/commitment/` |
| Acuerdos (`AgreementWriteGuard`) | `src/main/kotlin/sgspe/agreement/` |
| Evidencias sobre wasichai-files, descarga con `DOWNLOAD_RESTRICTED`, descriptor sin nombre ni huella (`EvidenceFileReadPolicy`, UPS-24) | `src/main/kotlin/sgspe/evidence/` |
| Inversión social: tope de apoyo (`SupportPolicyGuard` y, desde F5, `ContributionCapRule`, con `SupportCap` y `CampaignCommitted`), `InvestmentWriteGuard` (WF-P y WF-G), `ContributionGuidelines`, entrega de aportes (`ContributionDeliveries`), `ContributionTotalsListener` y `ProgramTotalsListener`; comprobante del aporte congelado desde `delivered` (`ContributionReceiptRule`) | `src/main/kotlin/sgspe/investment/` |
| Programas sociales: nombres y reglas de WF-G (`Programs`, `ProgramRules`), G1 a G4 (`ProgramApprovalRule`), reglas de los hijos (`ProgramDataRule`), `total_budget` (`ProgramTotalsListener`, `ProgramTotalsRecalculator`) | `src/main/kotlin/sgspe/investment/` |
| KPI-INV y el tablero `investment` (`InvestmentKpis`, filtros `program` y `year`) | `src/main/kotlin/sgspe/dashboard/` |
| Base de las pruebas de integración del plan 5.2b (programas con sus hijos, acuerdos vigentes con partes, aportes hasta entregados) | `src/test/kotlin/sgspe/F5ProgramApiTest.kt` |
| JOB-01 y los avisos de plazo (`DeadlineNotices`), también la revisión de los riesgos y su tarea `risk_review` | `src/main/kotlin/sgspe/deadline/` |
| Avisos sobre wasichai-notifications (`Notices`, `ApprovalNoticeListener`, `GraveGrievanceNoticeListener`, `ConfidentialReaders`, `RiskNoticeListener`, `WarningNoticeListener`) | `src/main/kotlin/sgspe/notification/` |
| Riesgos: niveles y revisión (`Risks.kt`), `RiskWriteGuard`, el endpoint de revisiones (`RiskAssessments`) | `src/main/kotlin/sgspe/risk/` |
| Alerta temprana: `WarningWriteGuard`, JOB-04 (`WarningRulesJob`) y los seis tipos de regla (`WarningEvaluation`) | `src/main/kotlin/sgspe/warning/` |
| QCS: `AttentionWriteGuard`, plazos y fechas, reclasificación, aceptación, derivación, `RequestClosureListener`, reporte unificado | `src/main/kotlin/sgspe/attention/` |
| Días hábiles (`HolidayCalendar`, `BusinessDays`, feriados en `sgspe.calendar.holidays`), `Attention` y `AttentionCounting`, `NotReadable` y `readAs`, `ConfidentialRelease` (solo para `RequestClosureListener`) | `src/main/kotlin/sgspe/common/`, `src/main/resources/application.yml` |
| QCS confidenciales (`ConfidentialAttentionScope`, un `RecordReadScope` que honra `ConfidentialRelease`) y la regla «mismo proyecto» (`ProjectLinks`, `ProjectLinkGuard`) | `src/main/kotlin/sgspe/scope/` |
| Alcance de lectura por proyecto (`ProjectReadScope`, su condición pura `ProjectReadCondition`, la clasificación `ProjectScope.TENANT` y `readGroup`; desde F5, cadenas de hijos y padres alternativos: `ProjectScope.chain`, `parentOf` y `projectOf`), documentos emitidos (`IssueDocumentFilter`, `DocumentReadByScope`) | `src/main/kotlin/sgspe/scope/` |
| «Mis proyectos» o todos (`ProjectReach`, acción `READ_ALL_PROJECTS`), duración por organización de los trabajos (`SweepTiming`) | `src/main/kotlin/sgspe/common/` |
| Alta y subida de fase por tenant (`provision_tenant.py`, `upgrade_tenants.py`, `tenant_steps.py`; inventario con la forma de `tenants.example.json`, sin versionar) | `model/` |
| Base de las pruebas de integración del plan 3.2 (sobre `F3ApiTest`: evidencias y compromisos de los comandos) y de las de `Idempotency-Key` sin el modelo de F3 | `src/test/kotlin/sgspe/F3DomainApiTest.kt`, `src/test/kotlin/sgspe/common/AssessmentCommandApiTest.kt` |
| Base de las pruebas de integración de F2 | `src/test/kotlin/sgspe/DomainApiTest.kt` |
| Límites de las importaciones: tamaño, filas, plazo `IMPORT_TIMEOUT` y concurrencia | `src/test/kotlin/sgspe/territory/ImportTimeoutApiTest.kt`, `ImportLimitsApiTest.kt` |
| Base de las pruebas de contrato | `src/test/kotlin/sgspe/SgspeApiTest.kt` y `src/test/kotlin/sgspe/contract/` |
| Base de las pruebas de F2 (registros mínimos, asignaciones, transiciones) | `src/test/kotlin/sgspe/F2ApiTest.kt` |
| Base de las pruebas de F3 del modelo (compromisos, acuerdos y tareas listos para sus guardas, altas apiOnly como la plataforma, espera de automatizaciones) | `src/test/kotlin/sgspe/F3ApiTest.kt` |
| Base de las pruebas de F4 (QCS, riesgos, incidentes, menciones, señales, reglas y alertas listos para las guardas de 4.2; revisiones de riesgo y derivados como la plataforma) | `src/test/kotlin/sgspe/F4ApiTest.kt` |
| Base de las pruebas de F5 (aportes con ítems, aprobados y adquiridos, su entrega con acta, foto y comprobante, campañas, acuerdos vigentes, temas priorizados y programas con sus datos, listos para las guardas de 5.2) | `src/test/kotlin/sgspe/F5ApiTest.kt` |
| Activación y estado de la IA (`AiProperties`, `AiActivation`, `AiErrors`, `AiStatus`, `AiUseCaseOffer`), seudonimización (`Pseudonymizer`, `PseudonymTable`, `NameDictionary`, `PersonNames`, `RestrictedFields`), registro (`Interactions`), prompts versionados (`Prompts`, `<id>/<versión>.md` y `manifest.json`), el puerto `StructuredModel` (`EmbabelStructuredModel`, `ModelCalls`) | `src/main/kotlin/sgspe/ai/`, `src/main/resources/sgspe/ai/prompts/` |
| Asistente AI-05 sobre wasichai-agent (`AssistantQuestions`, `AssistantRun` y `QuestionAuthentication`, `AiAgentAccess`, `PseudonymizingResultFilter` con `ToolResults`, `RestoringAnswerFilter`, `InteractionRunListener`, `AgentHttpGate`) | `src/main/kotlin/sgspe/ai/` |
| Tabla fija del RAG (`sgspe.knowledge_chunk`, migración `sgspe_ai`, `KnowledgeMigration`) | `src/main/kotlin/sgspe/ai/`, `src/main/resources/db/sgspe/ai/` |
| Casos con sugerencias (plan 7.2b): `POST /api/sgspe/ai/runs` (`AiRuns`), un `RunCase` por caso (`ExtractionCase`, `MinutesCase`, `CommitmentCase`, `SummaryCase`, `ClassificationCase`) con `RunRequirements`, las fuentes (`RunSources`, `TextExtraction` con PDFBox y POI), los agentes (`ExtractionAgent`, `CommitmentAgent`, `DraftAgents`, `ClassificationAgent`) y su validación (`CitationCheck`, `Proposals`, `RunLookups`), `SuggestionWriter` | `src/main/kotlin/sgspe/ai/` |
| Aceptación de sugerencias (`SuggestionAcceptance` con la lista cerrada `AllowList`) y `SuggestionDecisionGuard` (una persona decide WF-K) | `src/main/kotlin/sgspe/ai/` |
| Dobles de las corridas (`FakeLookups`, `RunFixtures`, `TestDocuments`) y las pruebas de inyección (`InjectionTest`) | `src/test/kotlin/sgspe/ai/` |
| Modelo guionado de las pruebas de IA (`RecordingLlm`, sobre `ScriptedLlmOperations` de Embabel), dobles del puerto (`FakeModel`, `MockModel`, `FailingModel`), `RecordedInteractions` y `TestCallers` | `src/test/kotlin/sgspe/ai/` |
| Base de las pruebas de F7 (interacciones, sugerencias y candidatos de IA como la plataforma, listos para 7.2a a 7.2c; desde 7.2a, `asUser` para llamar a un servicio como la persona) | `src/test/kotlin/sgspe/F7ApiTest.kt` |
| Desde F8: indicadores (`model/indicators.json`, `model/load_indicators.py`), cierre de periodo (`IndicatorMeasurements`, `IndicatorWriteGuard`), `KpiReader`, KPI-EXE y los tableros `executive`, `project` y `monitoring` (`ExecutiveKpis`, `Dashboards`, `MonitoringTable`) | `model/`, `src/main/kotlin/sgspe/dashboard/` |
| Desde F8: el modelo del informe (`ReportModel`, `CanonicalJson`) y su divulgación por nivel (`ReportDisclosure`) | `src/main/kotlin/sgspe/privacy/` |
| Desde F8: entregables: tipos (`FieldReportType`, `SocialDiagnosisType`), JOB-05 (`DeliverableGenerationJob`), DOCX (`DocxWriter`), PDF (`PdfConverter`, `GotenbergConverter`; Gotenberg en 8096, solo loopback), mapas de informe (`ReportMaps`, `GetMapRequest`, `MapComposer`), plantillas (`TemplateValidator`, `DeliverableTemplates`), archivos y descargas (`DeliverableFiles`, `DeliverableContent`), emisión (`DeliverableIssue`), `DeliverableWriteGuard` y secciones (`DeliverableSections`) | `src/main/kotlin/sgspe/deliverable/` |
| Tipos de documento y automatizaciones (AUT-NN) | `model/document-types/`, `model/automations/`; `src/test/kotlin/sgspe/workflow/AutomationsApiTest.kt` |
| Workflows (`WorkflowMatrixApiTest` sale de `model/workflows/*.json`), matriz de permisos y permisos de campo | `src/test/kotlin/sgspe/workflow/`, `src/test/kotlin/sgspe/security/` |
| Modelo frente a los documentos | `model/test_model.py`, `model/design_docs.py` |
| Reglas de arquitectura | `src/test/kotlin/sgspe/ArchitectureTest.kt` |
| Plan de la fase en curso | `docs/superpowers/plans/` |
