# Matriz de trazabilidad de requisitos de SGSPE

Sistema de Gestión Social para Proyectos Extractivos · Fase 0 (descubrimiento) · versión 1.0 · 2026-10-07

Este documento es la matriz completa que pide el prompt maestro: documento → requisito → módulo → entidad → API → UI → test
[PRM-01 §23] (RNF-MNT-009). El [análisis de requisitos](requirements-analysis.md) define los IDs y su §20 trae el resumen;
aquí cada RF, RQ, RNF y RN tiene su fila, y después vienen la trazabilidad inversa por fuente y los huérfanos. Los nombres
salen del [modelo de dominio](domain-model.md), la [arquitectura](architecture.md), la [API](api.md), los
[workflows](workflows.md), la [seguridad](security.md), el [SIG](gis.md), los [entregables](deliverables.md), los
[tableros](dashboards.md) y la [arquitectura de IA](ai-architecture.md). Este documento no define IDs ni cambia los demás.

## 1. Cómo leer la matriz

### 1.1 Columnas

| Columna | Contenido | De dónde sale |
|---|---|---|
| Requirement | ID del requisito, tal como lo define el análisis | `requirements-analysis.md` §6 a §8 y §12 a §16 |
| Source | La celda de fuente del requisito, copiada sin cambios | `requirements-analysis.md` |
| Module | El bounded context (`BC-NN`) y la unidad que lo implementa: `model/model.json`, `model/roles.json`, un workflow `model/workflows/<objeto>.json` con su letra, una automatización `AUT-NN`, un trabajo `JOB-NN` o un paquete `sgspe.*` con la guarda, el listener o el endpoint que importa | `domain-model.md` §8.2, `architecture.md` §5, `workflows.md` |
| Entity | Objetos del catálogo canónico (`domain-model.md` §6) o tablas fijas del esquema `sgspe` | `domain-model.md` §6 |
| API | Ruta genérica de wasichai (`/api/objects/<objeto>/records`, transiciones, `/api/gis/...`) o endpoint de `/api/sgspe/**` tal como lo fija `api.md` | `api.md` §2 y §4 |
| UI | Sección del portal (`architecture.md` §9.2), tablero (`dashboards.md` §5), mapa o ficha (`gis.md` §6.5) o módulo de `/admin` | `architecture.md`, `dashboards.md`, `gis.md`, `ai-architecture.md` |
| Test | Prueba que nombra un documento de diseño; si ninguno la nombra, el nombre esperado con el patrón de los documentos y la marca (propuesto) | `workflows.md` §Pruebas, `security.md` §14, `gis.md` §9, `deliverables.md` §13, `ai-architecture.md` §15, `architecture.md` §13 |
| Fase | La fase de la fila del requisito (F0 a F8) | `requirements-analysis.md` §0.8 |
| Estado | Situación del requisito al cerrar la Fase 0 (§1.2) | Esta matriz |

Convenciones:

- **«Portal › Sección»** nombra la sección de la navegación principal (RNF-USA-001); «ficha de `objeto`» es la página de ficha que
  arma la metadata de wasichai (`domain-model.md` §8.1). Una pantalla con la marca (propuesto) no la nombra ningún documento.
- **Tests.** Los nombres entre comillas latinas («Permisos de campo», «Importación tipo DOC-03») son las filas de prueba de
  `security.md` §14, `gis.md` §9, `deliverables.md` §13.3, `workflows.md` §Pruebas y `ai-architecture.md` §15, que las nombran
  por lo que verifican. Los nombres con la marca (propuesto) siguen los patrones `*ApiTest`, `*GuardTest`, `test_*.py` y `*.test.tsx`.
- **Tests de tableros.** Una prueba de integración por tablero, `<Tablero>DashboardApiTest` (propuesto), con el nombre en
  inglés del `{dashboard}` de `api.md` §4.11 (`ExecutiveDashboardApiTest` hasta `InvestmentDashboardApiTest`, todos propuestos),
  más `DashboardPermissionsApiTest` (propuesto) para el «no disponible» (RQ-DSH-032).
- **«(desde Fn)»** marca, en las columnas Module, Entity, API y UI, una pieza que llega en una fase posterior a la primera fase
  de la fila: un objeto, un endpoint, una automatización, un trabajo, un paquete, una sección o un tablero. La fase de cada
  pieza es la de `domain-model.md` §5 y §8.2, `api.md` §4, `workflows.md`, `architecture.md` §9.2 y `dashboards.md` §5.
- **Fase de las RN.** Las reglas de negocio de §8 del análisis no tienen columna de fase. Su fase es la del objeto o la guarda
  que la aplica, según `domain-model.md` §5 y §8.2; cuando la regla cruza dos fases se dan las dos.
- **«—»** indica que la fila no tiene ese elemento; las filas así están en los huérfanos de §7.1 con lo que necesitan.

### 1.2 Estados

| Estado | Significado |
|---|---|
| Diseñado | Los documentos de la Fase 0 lo cubren: objeto, mecanismo, API, pantalla y prueba están decididos, o la fila dice con «—» que no los tiene por su naturaleza (§7.1, tipo «No aplica») |
| Implementado | El código y las pruebas que la fila cita existen y pasan en local (desde F1); la CI de la fase lo confirmará con el primer push, y entonces esta definición vuelve a «pasan en la CI de su fase». Si la fila lleva además Upstream, la brecha sigue abierta y su mitigación está implementada |
| Decisión pendiente | Depende de una decisión que el análisis lleva al checkpoint: CON-041, GAP-025, CON-050, GAP-001, GAP-002, GAP-003, GAP-004, GAP-005, GAP-009 (`requirements-analysis.md` §1). Está diseñado con el valor por defecto de su gap o con la recomendación de su contradicción. La fila lo lleva cuando la columna «Afecta a» del gap la nombra, cuando su fila del análisis cita el ID, o cuando otra fila que cita el ID remite a ella: RNF-MNT-004 cita CON-050 y remite a RF-CAT-001, que define los valores ENUM. Los requisitos que dependen de otros gaps se diseñan con su valor por defecto y quedan como Diseñado |
| Upstream | Su cumplimiento completo depende de una brecha de wasichai (UPS, `requirements-analysis.md` §18.2); la fila cita la brecha, y la mitigación de esa tabla ya está diseñada, salvo que la fila lleve además «Hueco» |
| Hueco | Le falta algo que ningún documento define: una prueba o el lugar de un dato. §7.1 dice qué necesita (tipo «Hueco») |
| Fuera del MVP | Todas sus fases son posteriores a Release 1 (F5 a F8, ASM-044); el diseño existe, pero no entra en el MVP |

Una fila puede llevar varios estados, separados por punto y coma. Cuatro decisiones del checkpoint no tienen GAP ni CON propio y
por eso no aparecen como Decisión pendiente: ofrecer AI-05 solo a roles sin acceso a campos restringidos mientras falte UPS-14
(`security.md` §15), que RF-AIK-005 muestra como Upstream (UPS-14); contar los plazos desde la primera recepción al reclasificar
una QCS (ASM-072), que RF-GRV-007 muestra como Diseñado; reutilizar `approve_senior` de WF-D en el nivel 2 de escalamiento
(ASM-030), que RF-WFL-004 muestra como Upstream (UPS-17); y el reparto en releases (ASM-044), que fija el estado «Fuera
del MVP».

## 2. Requisitos funcionales (RF)

Las 114 RF de `requirements-analysis.md` §6, en su orden. §6.18 (AUD) no tiene RF: sus obligaciones están en las RQ-AUD (§3.5).

### 2.1 Proyectos (PRJ)

| Requirement | Source | Module | Entity | API | UI | Test | Fase | Estado |
|---|---|---|---|---|---|---|---|---|
| RF-PRJ-001 | [PRM-01 §5] [DOC-01 §1] [DOC-02 p.1–p.2] | BC-01 · `model/model.json`; `sgspe.common` (correlativos de `code`) | `project` | `/api/objects/project/records` | Portal › Proyectos: ficha de `project` | `test_model.py`; `ProjectWorkflowApiTest` | F2 | Diseñado |
| RF-PRJ-002 | [PRM-01 §5] [DOC-01 portada, §1, §9.4.2] [DOC-02 p.4, p.6] | BC-01 · `model/model.json` | `project_party`, `organization` | `/api/objects/project_party/records` | Portal › Proyectos: ficha de `project` | `test_model.py`; `ProjectWorkflowApiTest` | F2 | Diseñado |
| RF-PRJ-003 | [PRM-01 §8] | BC-01 · `model/workflows/project.json` (WF-A); `sgspe.scope` (`ProjectWriteGuard`); `sgspe.common` (`FieldChangeReasonGuard`) | `project` | `/api/objects/project/records/{id}/transitions/{name}` | Portal › Proyectos: ficha de `project` (componente WORKFLOW) | `ProjectWorkflowApiTest`; `test_model.py` | F2 | Diseñado |
| RF-PRJ-004 | [DOC-01 §1, §9.3.1.2, Tabla 9.4] | BC-01 · `model/model.json` | `exploration_campaign` | `/api/objects/exploration_campaign/records` | Portal › Proyectos: ficha de `project` | `test_model.py` | F2 | Diseñado |
| RF-PRJ-005 | [PRM-01 §7] [DOC-01 §3.2, §9.4.1] | BC-01, BC-16 · `model/model.json`; `sgspe.scope` (`AssignmentRoleGuard`, `ProjectScopeGuard`) | `project_assignment` | `/api/objects/project_assignment/records` | Portal › Proyectos: ficha de `project`; `/admin` (usuarios) | `security.md` §14 «Alcance, opción A»; `security.md` §14 «Destinatarios por rol» | F2 | Diseñado |
| RF-PRJ-006 | [DOC-01 Anexo 1] [DOC-02 p.4] [DOC-03] | BC-01 · `model/model.json`; `sgspe.territory` (importación) | `mining_concession` (M2M `project_concession`) | `/api/objects/mining_concession/records`; `POST /api/sgspe/territory/imports` | Portal › Territorio: mapa del proyecto (LYR-07) | `gis.md` §9 «Importación tipo DOC-03»; `test_model.py` | F2 | Diseñado |

### 2.2 Territorio (TER)

| Requirement | Source | Module | Entity | API | UI | Test | Fase | Estado |
|---|---|---|---|---|---|---|---|---|
| RF-TER-001 | [DOC-01 §1, §4.2] [DOC-02 p.5] [PRM-01 §5] | BC-02, BC-20 · `model/model.json`; script de carga del ubigeo en `model/` | `administrative_unit` | `/api/objects/administrative_unit/records` | `/admin` (catálogos); Portal › Territorio: mapa del proyecto (LYR-01) | `test_load_ubigeo.py` (propuesto); `test_model.py` | F2 | Upstream (UPS-16) |
| RF-TER-002 | [DOC-01 §4.1, §5.1.1, §5.2.1] [DOC-02 p.3, p.5] | BC-02 · `model/model.json` | `locality` | `/api/objects/locality/records` | Portal › Territorio: ficha de `locality`; mapa del proyecto (LYR-02, LYR-03) | `test_model.py` | F2 | Upstream (UPS-16) |
| RF-TER-003 | [DOC-01 §4.1, §6.1.3.1] [DOC-02 p.4] | BC-02 · `model/model.json`; `sgspe.territory` (`AreaListener`) | `community` | `/api/objects/community/records` | Portal › Territorio: ficha de `community`; mapa del proyecto (LYR-05) | `test_model.py`; `gis.md` §9 «Área derivada» | F2 | Diseñado |
| RF-TER-004 | [DOC-02 p.4–p.5] | BC-02 · `model/model.json` | `community_sector` (M2M `community_sector_locality`) | `/api/objects/community_sector/records`; `/api/objects/community_sector/records/{id}/related/community_sector_locality` | Portal › Territorio: ficha de `community`; mapa del proyecto (LYR-06) | `test_model.py` | F2 | Diseñado |
| RF-TER-005 | [DOC-01 §3.1, §4, §4.1, §4.2] [DOC-03] | BC-02 · `model/model.json`; `model/workflows/area_of_influence.json` (WF-O); `sgspe.territory` (`TerritoryWriteGuard`) | `area_of_influence`, `area_of_influence_member` | `/api/objects/area_of_influence/records`; `/api/objects/area_of_influence_member/records`; `POST /api/sgspe/areas-of-influence/{id}/approve` | Portal › Territorio: ficha de `area_of_influence`; mapa del proyecto (LYR-13) | `AreaOfInfluenceWorkflowApiTest` | F2 | Diseñado |
| RF-TER-006 | [DOC-03] [DOC-01 §4.1] | BC-02 · `model/model.json`; `sgspe.territory` (importación y versiones) | `project_component` | `POST /api/sgspe/territory/imports`; `POST /api/sgspe/territory/imports/{importBatchId}/confirm`; `POST /api/sgspe/project-components`; `PATCH /api/sgspe/project-components/{id}` | Portal › Territorio: importación con vista previa en el mapa (`gis.md` §4.2); mapa del proyecto (LYR-10, LYR-11, LYR-12) | `gis.md` §9 «Importación tipo DOC-03» | F2 | Diseñado |
| RF-TER-007 | [DOC-03] [DOC-01 Anexo 1, Tabla 8.1] | BC-02 · `model/model.json`; `sgspe.common` (`ExactlyOneGuard`) | `land_parcel`, `land_right` | `/api/objects/land_parcel/records`; `/api/objects/land_right/records`; `POST /api/sgspe/territory/imports` | Portal › Territorio: mapa del proyecto (LYR-14, LYR-15) | `security.md` §14 «Permisos de campo»; `test_model.py` | F2 | Diseñado |
| RF-TER-008 | [DOC-01 §4.2, §7.1.2, Tabla 8.1, Tabla 9.4, nota 24] | BC-02 · `model/model.json` | `environmental_feature` | `/api/objects/environmental_feature/records` | Portal › Territorio: mapa del proyecto (LYR-08) | `test_model.py` | F2 | Diseñado |

### 2.3 Línea base social (LBS)

| Requirement | Source | Module | Entity | API | UI | Test | Fase | Estado |
|---|---|---|---|---|---|---|---|---|
| RF-LBS-001 | [DOC-01 §2, §3, Tabla 3.1, Tabla 10.1] | BC-19 · `model/model.json` | `study`, `study_instrument`, `engagement` | `/api/objects/study/records`; `/api/objects/study_instrument/records` | `/admin` (objetos de baja frecuencia, ASM-020) | `test_model.py` | F2 | Diseñado |
| RF-LBS-002 | [DOC-01 §5, Tablas 5.1–5.3, 5.7, 5.8, notas 1–2] | BC-19 · `model/model.json`; `model/roles.json` (permisos de campo) | `locality_profile` | `/api/objects/locality_profile/records` | `/admin` (objetos de baja frecuencia, ASM-020) | `test_model.py`; `security.md` §14 «Permisos de campo» | F2 | Diseñado |
| RF-LBS-003 | [DOC-01 §5.1.5, §5.1.6, §5.2.4, Tablas 5.4, 5.5, 5.9] | BC-19 · `model/model.json` | `school`, `health_facility`, `road_access` | `/api/objects/school/records`; `/api/objects/health_facility/records`; `/api/objects/road_access/records` | `/admin` (objetos de baja frecuencia, ASM-020); Portal › Territorio: mapa del proyecto (LYR-04, LYR-16) | `test_model.py` | F2 | Diseñado |
| RF-LBS-004 | [DOC-01 §5.1.9, Tabla 5.6, §9.3.1.2] | BC-19 · `model/model.json` | `festivity` | `/api/objects/festivity/records` | `/admin` (objetos de baja frecuencia, ASM-020) | `test_model.py` | F2 | Diseñado |
| RF-LBS-005 | [DOC-01 §5.2.5, Tabla 5.10] | BC-19 · `model/model.json` | `state_program` (M2M `state_program_locality`) | `/api/objects/state_program/records`; `/api/objects/state_program/records/{id}/related/state_program_locality` | `/admin` (objetos de baja frecuencia, ASM-020) | `test_model.py` | F2 | Diseñado |
| RF-LBS-006 | [DOC-01 §5.1.1, §5.2.1] | BC-19 · `model/model.json` | `local_conflict` (M2M `local_conflict_locality`) | `/api/objects/local_conflict/records`; `/api/objects/local_conflict/records/{id}/related/local_conflict_locality` | `/admin` (objetos de baja frecuencia, ASM-020) | `test_model.py` | F2 | Diseñado |
| RF-LBS-007 | [DOC-01 §7.1, notas 22–23] | BC-19 · `model/model.json` | `perception` | `/api/objects/perception/records` | `/admin` (objetos de baja frecuencia, ASM-020) | `test_model.py` | F2 | Diseñado |
| RF-LBS-008 | [DOC-01 §8, Tabla 8.1] | BC-19 · `model/model.json` | `key_theme` | `/api/objects/key_theme/records` | `/admin` (objetos de baja frecuencia, ASM-020) | `test_model.py` | F2 | Diseñado |
| RF-LBS-009 | [DOC-01 Tabla 9.5, Tabla 10.1] [PRM-01 §5] | BC-19, BC-20 · `model/model.json` | `topic` | `/api/objects/topic/records` | `/admin` (catálogos) | `test_model.py` | F2 | Diseñado |

### 2.4 Grupos de interés (STK)

| Requirement | Source | Module | Entity | API | UI | Test | Fase | Estado |
|---|---|---|---|---|---|---|---|---|
| RF-STK-001 | [DOC-01 §6, Tablas 6.1–6.3, 7.1–7.3] [DOC-02 p.3] [PRM-01 §5] | BC-03 · `model/model.json`; `sgspe.common` (`ExactlyOneGuard`) | `person`, `organization`, `contact_method` | `/api/objects/person/records`; `/api/objects/organization/records`; `/api/objects/contact_method/records` | Portal › Grupos de interés: fichas de `person` y `organization` | `security.md` §14 «Permisos de campo»; `test_model.py` | F2 | Diseñado |
| RF-STK-002 | [DOC-01 §6.1–§6.3, notas 12, 14–16] [DOC-02 p.3] | BC-03 · `model/model.json`; `sgspe.stakeholder` (`StakeholderWriteGuard`) | `position` | `/api/objects/position/records` | Portal › Grupos de interés: ficha de `organization` | `StakeholderWriteGuardTest` (propuesto) | F2 | Diseñado |
| RF-STK-003 | [DOC-01 Tablas 6.1–6.3, notas 13–19] | BC-03 · `model/model.json`; `model/workflows/stakeholder.json` (WF-B); `sgspe.stakeholder` | `stakeholder` | `/api/objects/stakeholder/records`; `/api/objects/stakeholder/records/{id}/transitions/{name}` | Portal › Grupos de interés: ficha de `stakeholder` | `StakeholderWorkflowApiTest` | F2 | Diseñado |
| RF-STK-004 | [DOC-01 §5.2.5, §6.1.2.7, §6.2.2.2, §6.2.4.2] | BC-03 · `model/model.json` | `organization` | `/api/objects/organization/records` | Portal › Grupos de interés: ficha de `organization` | `test_model.py` | F2 | Upstream (UPS-16) |
| RF-STK-005 | [DOC-01 §6, §6.2.4.1, §6.2.5, §7.2, §9.3.1.1, Anexo 3] | BC-03 · `model/model.json`; `sgspe.stakeholder` (endpoint de evaluaciones) | `stakeholder_assessment`, `stakeholder` | `POST /api/sgspe/stakeholders/{id}/assessments`; `POST /api/sgspe/stakeholders/{id}/assessments/preview` | Portal › Grupos de interés: ficha de `stakeholder` (evaluación con vista previa) | `StakeholderWorkflowApiTest`; `StakeholderAssessmentApiTest` (propuesto); `AuditApiTest` | F2 | Diseñado |
| RF-STK-006 | [DOC-01 Anexo 3, Gráfico 10.2, nota 25] | BC-03 · `sgspe.stakeholder` (`InfluenceClassifier`); `model/model.json` (indicadores y umbrales del proyecto) | `stakeholder_assessment`, `project` | `POST /api/sgspe/stakeholders/{id}/assessments`; `POST /api/sgspe/stakeholders/{id}/assessments/preview` | Portal › Grupos de interés: ficha de `stakeholder` (evaluación con vista previa) | `StakeholderAssessmentApiTest` (propuesto); `architecture.md` §13 «Unitarias» | F2 | Diseñado |
| RF-STK-007 | [DOC-01 Anexo 3, Gráfico 10.2, nota 26] | BC-03 · `sgspe.stakeholder` (`InfluenceClassifier`); `model/model.json` (indicadores y umbrales del proyecto) | `stakeholder_assessment`, `project` | `POST /api/sgspe/stakeholders/{id}/assessments`; `POST /api/sgspe/stakeholders/{id}/assessments/preview` | Portal › Grupos de interés: ficha de `stakeholder` (evaluación con vista previa) | `StakeholderAssessmentApiTest` (propuesto); `architecture.md` §13 «Unitarias» | F2 | Diseñado |
| RF-STK-008 | [DOC-01 Anexo 3, nota 25] | BC-03 · `sgspe.stakeholder` (`InfluenceClassifier`); `model/model.json` (indicadores y umbrales del proyecto) | `stakeholder_assessment`, `project` | `POST /api/sgspe/stakeholders/{id}/assessments`; `POST /api/sgspe/stakeholders/{id}/assessments/preview` | Portal › Grupos de interés: ficha de `stakeholder` (evaluación con vista previa) | `StakeholderAssessmentApiTest` (propuesto); `architecture.md` §13 «Unitarias» | F2 | Decisión pendiente (GAP-002) |
| RF-STK-009 | [DOC-01 §7.2, §7.2.1.1, §7.2.1.2, §7.2.1.3, Anexo 3, Gráfico 10.3, Tabla 9.1] | BC-03 · `sgspe.stakeholder` (`InfluenceClassifier`); `model/model.json` (indicadores y umbrales del proyecto) | `stakeholder_assessment`, `project` | `POST /api/sgspe/stakeholders/{id}/assessments`; `POST /api/sgspe/stakeholders/{id}/assessments/preview` | Portal › Grupos de interés: ficha de `stakeholder`; tablero de grupos de interés (KPI-STK-03) | `StakeholderAssessmentApiTest` (propuesto); `architecture.md` §13 «Unitarias» | F2 | Decisión pendiente (GAP-002, GAP-003) |
| RF-STK-010 | [DOC-01 §9.3.1.1, Tabla 9.5] | BC-03 · `sgspe.stakeholder` (endpoint de evaluaciones; `StakeholderFollowUpJob`, JOB-03) | `stakeholder_assessment`, `stakeholder`, `task` (desde F3) | `POST /api/sgspe/stakeholders/{id}/assessments` | Portal › Grupos de interés: ficha de `stakeholder`; Portal › Tareas (desde F3) | `StakeholderAssessmentApiTest` (propuesto); `workflows.md` §Pruebas «Trabajos» | F2 | Diseñado |
| RF-STK-011 | [DOC-01 §9.1.1.4, Tabla 9.1] | BC-03 · `model/model.json`; `sgspe.stakeholder` (`Prioritizer`, `StakeholderWriteGuard`) | `prioritization_rule`, `stakeholder` | `/api/objects/prioritization_rule/records`; `POST /api/sgspe/stakeholders/{id}/assessments` | `/admin` (catálogos); Portal › Grupos de interés: ficha de `stakeholder` | `StakeholderWriteGuardTest` (propuesto); `StakeholderAssessmentApiTest` (propuesto) | F2 | Diseñado |
| RF-STK-012 | [DOC-02 p.3, p.5] [DOC-01 §6] | BC-03 · `model/model.json`; `sgspe.common` (`ExactlyOneGuard`) | `community_network`, `network_node`, `actor_relationship` | `/api/objects/community_network/records`; `/api/objects/network_node/records`; `/api/objects/actor_relationship/records` | Portal › Grupos de interés: ficha de `organization` | `test_model.py` | F2 | Diseñado |
| RF-STK-013 | [DOC-01 §6] | BC-03 · `model/workflows/stakeholder.json` (WF-B); `sgspe.common` (`TransitionReasonGuard`) | `stakeholder` | `/api/objects/stakeholder/records/{id}/transitions/{name}` | Portal › Grupos de interés: ficha de `stakeholder` | `StakeholderWorkflowApiTest` | F2 | Diseñado |
| RF-STK-014 | [DOC-02 p.2] [PRM-01 §8] | BC-03 · `model/model.json`; `sgspe.stakeholder` (`StakeholderFollowUpJob`, JOB-03) | `stakeholder`, `task` (desde F3) | `/api/objects/stakeholder/records` | Portal › Grupos de interés: ficha de `stakeholder`; Portal › Tareas (desde F3) | `workflows.md` §Pruebas «Trabajos» | F2 | Diseñado |

### 2.5 Relacionamiento (ENG) y operaciones de campo (FLD)

| Requirement | Source | Module | Entity | API | UI | Test | Fase | Estado |
|---|---|---|---|---|---|---|---|---|
| RF-ENG-001 | [DOC-01 §3, §9.1.1, Tabla 9.5] [DOC-02 p.2–p.3] [PRM-01 §5] | BC-04 · `model/model.json`; `model/workflows/engagement.json` (WF-C); `sgspe.engagement` | `engagement` (M2M `engagement_stakeholder`) | `/api/objects/engagement/records`; `/api/objects/engagement/records/{id}/related/engagement_stakeholder` | Portal › Relacionamiento: ficha de `engagement` | `EngagementWorkflowApiTest` | F2 | Diseñado |
| RF-ENG-002 | [DOC-01 §9.1.1.1] | BC-04 · `model/workflows/engagement.json` (WF-C); `sgspe.stakeholder` (`StakeholderFollowUpJob`, JOB-03) | `engagement`, `stakeholder` | `/api/objects/engagement/records`; `/api/objects/engagement/records/{id}/transitions/{name}` | Portal › Relacionamiento: ficha de `engagement` | `EngagementWorkflowApiTest`; `workflows.md` §Pruebas «Trabajos» | F2 | Diseñado |
| RF-ENG-003 | [DOC-01 §9.1.1.2] | BC-04 · `model/model.json`; `sgspe.engagement` (`EngagementWriteGuard`; derivación de preguntas a QCS) | `engagement_question`, `aligned_answer` | `/api/objects/engagement_question/records`; `POST /api/sgspe/engagement-questions/{id}/referral` (desde F4) | Portal › Relacionamiento: ficha de `engagement` | `EngagementWorkflowApiTest` | F2 | Diseñado |
| RF-ENG-004 | [DOC-01 Tabla 9.5] | BC-04 · `model/model.json`; `sgspe.engagement` (`EngagementWriteGuard`, `AttendanceCountsListener`) | `engagement_participant`, `engagement` | `/api/objects/engagement_participant/records` | Portal › Relacionamiento: ficha de `engagement` | `EngagementWorkflowApiTest`; `security.md` §14 «Permisos de campo» | F2 | Diseñado |
| RF-ENG-005 | [DOC-01 Tabla 9.5] [PRM-01 §5, §8] | BC-04 · `model/workflows/engagement.json` (WF-C); automatización AUT-05 (desde F3); `sgspe.evidence` (desde F3) | `engagement`, `evidence` (desde F3) | `/api/objects/engagement/records/{id}/transitions/{name}`; `POST /api/objects/{object}/records/{id}/documents/{type}` (desde F3); `POST /api/sgspe/evidence` (desde F3) | Portal › Relacionamiento: ficha de `engagement`; Portal › Documentos (desde F3) | `EngagementWorkflowApiTest`; `workflows.md` §Pruebas «Automatizaciones» | F2 | Diseñado |
| RF-ENG-006 | [DOC-02 p.2] | BC-04 · `model/workflows/engagement.json` (WF-C); automatización AUT-10 (desde F3) | `engagement`, `task` (desde F3) | `/api/objects/engagement/records/{id}/transitions/{name}` | Portal › Relacionamiento: ficha de `engagement` | `EngagementWorkflowApiTest` | F2 | Diseñado |
| RF-ENG-007 | [DOC-01 §9, §9.1.1.3, Tabla 9.5] [DOC-02 p.2] | BC-04 · `model/model.json` | `engagement_plan`, `planned_activity` | `/api/objects/engagement_plan/records`; `/api/objects/planned_activity/records` | Portal › Relacionamiento: ficha de `engagement_plan` (propuesto) | `test_model.py` | F2 | Diseñado |
| RF-ENG-008 | [DOC-01 §9.1.2, Tabla 9.2] | BC-04 · `model/model.json` | `key_message` | `/api/objects/key_message/records` | Portal › Relacionamiento; `/admin` (objetos de baja frecuencia, ASM-020) | `test_model.py` | F2 | Diseñado |
| RF-ENG-009 | [DOC-01 §9.1.1.2] | BC-04 · `model/model.json` | `aligned_answer` | `/api/objects/aligned_answer/records` | Portal › Relacionamiento; `/admin` (objetos de baja frecuencia, ASM-020) | `test_model.py` | F2 | Diseñado |
| RF-ENG-010 | [DOC-01 §9.2, Tabla 9.3] | BC-04 · `model/model.json`; `sgspe.engagement` (recalculador de `formalized`) | `strategic_alliance`, `agreement` (desde F3) | `/api/objects/strategic_alliance/records` | Portal › Relacionamiento; `/admin` (objetos de baja frecuencia, ASM-020) | `test_model.py`; `workflows.md` §Pruebas «Trabajos» | F2 | Diseñado |
| RF-ENG-011 | [DOC-01 Tabla 9.5] | BC-04 · `model/model.json`; `sgspe.evidence` (desde F3) | `engagement`, `evidence` (desde F3) | `/api/objects/engagement/records`; `POST /api/sgspe/evidence` (desde F3) | Portal › Relacionamiento: ficha de `engagement` | `test_model.py` | F2 | Diseñado |
| RF-ENG-012 | [DOC-01 Tabla 9.5] [PRM-01 §5] | BC-04 · `model/model.json` | M2M `engagement_topic`; `topic` | `/api/objects/engagement/records/{id}/related/engagement_topic` | Portal › Relacionamiento: ficha de `engagement`; tablero de relacionamiento (KPI-ENG-04) | `test_model.py` | F2 | Diseñado |
| RF-FLD-001 | [DOC-01 §3.3] [DOC-02 p.2–p.3] [PRM-01 §5] | BC-04 (BC-11 fusionado) · `model/model.json`; `model/workflows/field_visit.json` (WF-L); `sgspe.scope` (`EditAnyGuard`) | `field_visit`, `field_observation`, `engagement` | `/api/objects/field_visit/records`; `/api/objects/field_observation/records`; `/api/objects/field_visit/records/{id}/transitions/{name}` | Portal › Relacionamiento: ficha de `field_visit` | `FieldVisitWorkflowApiTest` | F2 | Diseñado |

### 2.6 Compromisos (COM)

| Requirement | Source | Module | Entity | API | UI | Test | Fase | Estado |
|---|---|---|---|---|---|---|---|---|
| RF-COM-001 | [DOC-01 §9.3.1.2] | BC-05 · `model/model.json`; `sgspe.commitment` (`CommitmentWriteGuard`); `sgspe.privacy` (exportación) | `commitment` (M2M `commitment_stakeholder`); `person`, `position` | `/api/objects/commitment/records`; `POST /api/sgspe/exports/{kind}` (`commitment-matrix`) | Portal › Compromisos: ficha de `commitment`; Portal › Reportes (exportación) | `CommitmentWorkflowApiTest`; `security.md` §14 «Salidas»; `deliverables.md` §13.2, criterio 21 | F3 | Diseñado |
| RF-COM-002 | [PRM-01 §5, §8] [DOC-01 §9.3.1.2] | BC-05 · `model/workflows/commitment.json` (WF-D); `sgspe.commitment` (`CommitmentWriteGuard`, `CommitmentDerivationsListener`) | `commitment` | `/api/objects/commitment/records/{id}/transitions/{name}` | Portal › Compromisos: ficha de `commitment` (componente WORKFLOW) | `CommitmentWorkflowApiTest` | F3 | Diseñado |
| RF-COM-003 | [PRM-01 §8] | BC-05 · `sgspe.commitment` (endpoint de reprogramación); `sgspe.common` (`DerivedFieldsGuard`) | `commitment_reschedule`, `commitment` | `POST /api/sgspe/commitments/{id}/reschedules` | Portal › Compromisos: ficha de `commitment` | `CommitmentRescheduleApiTest` (propuesto) | F3 | Diseñado |
| RF-COM-004 | [PRM-01 §5, §8] | BC-05 · `model/model.json`; `sgspe.commitment` (`CommitmentWriteGuard`) | `commitment_dependency` | `/api/objects/commitment_dependency/records` | Portal › Compromisos: ficha de `commitment` | `CommitmentWorkflowApiTest` | F3 | Diseñado |
| RF-COM-005 | [PRM-01 §5] [DOC-01 §9.3.1.2, Tabla 9.5] | BC-05 · `model/model.json`; `sgspe.common` (`UserReferenceGuard`) | `commitment`, `engagement`, `agreement`, `agreement_clause`, `exploration_campaign` | `/api/objects/commitment/records` | Portal › Compromisos: ficha de `commitment` | `CommitmentWorkflowApiTest` | F3 | Diseñado |
| RF-COM-006 | [DOC-01 §9.3.1.2] [PRM-01 §5] | BC-05 · `model/model.json` | `commitment_action`, `commitment_milestone` | `/api/objects/commitment_action/records`; `/api/objects/commitment_milestone/records` | Portal › Compromisos: ficha de `commitment` | `test_model.py` | F3 | Diseñado |
| RF-COM-007 | [DOC-01 §9.3.1.2] | BC-05 · `model/model.json` | `commitment` | `/api/objects/commitment/records` | Portal › Compromisos: ficha de `commitment` | `test_model.py` | F3 | Diseñado |
| RF-COM-008 | [DOC-01 §9.3.1.2] | BC-05 · `sgspe.commitment` (endpoint de entregas); automatización AUT-03 | `delivery`, `evidence`, `commitment` | `POST /api/sgspe/commitments/{id}/deliveries`; `PATCH /api/sgspe/deliveries/{id}` | Portal › Compromisos: ficha de `commitment` | `CommitmentWorkflowApiTest`; `workflows.md` §Pruebas «Automatizaciones» | F3 | Diseñado |
| RF-COM-009 | [DOC-01 Tabla 9.5] | BC-05 · `model/model.json` | `commitment` | `/api/objects/commitment/records` | Portal › Compromisos: tablero de compromisos (KPI-COM-08) | `test_model.py` | F3 | Diseñado |
| RF-COM-010 | [ARQ-02] [PRM-01 §9] | BC-05 · `model/workflows/commitment_candidate.json` (WF-K); `sgspe.commitment` (alta y aceptación); `sgspe.ai` (desde F7) | `commitment_candidate`, `commitment` | `POST /api/sgspe/commitment-candidates`; `POST /api/sgspe/commitment-candidates/{id}/accept`; `POST /api/sgspe/commitment-candidates/{id}/duplicate`; `/api/objects/commitment_candidate/records/{id}/transitions/{name}` | Portal › Compromisos; bandeja de revisión (desde F7) (`ai-architecture.md` §9.3) | `CommitmentCandidateWorkflowApiTest` | F3 (humano), F7 (IA) | Diseñado |

### 2.7 Acuerdos y convenios (AGR)

| Requirement | Source | Module | Entity | API | UI | Test | Fase | Estado |
|---|---|---|---|---|---|---|---|---|
| RF-AGR-001 | [PRM-01 §5] [DOC-01 §9.1.1.3, §9.3.1.2] [DOC-02 p.6] | BC-06 · `model/model.json`; `model/workflows/agreement.json` (WF-I); `sgspe.agreement`; `sgspe.deadline` (vigencia) | `agreement`, `agreement_party`, `agreement_clause`, `evidence` | `/api/objects/agreement/records`; `/api/objects/agreement_party/records`; `/api/objects/agreement_clause/records`; `/api/objects/agreement/records/{id}/transitions/{name}` | Portal › Compromisos: ficha de `agreement` (propuesto) | `AgreementWorkflowApiTest` | F3 | Diseñado |
| RF-AGR-002 | [DOC-02 p.6] [DOC-01 §4.1, §9.3.1.2] | BC-06 · `model/model.json`; `sgspe.agreement` (`AgreementWriteGuard`) | `agreement`, `land_parcel`, `community` (M2M `agreement_land_parcel`) | `/api/objects/agreement/records/{id}/related/agreement_land_parcel` | Portal › Compromisos: ficha de `agreement` (propuesto) | `AgreementWorkflowApiTest` | F3 | Diseñado |
| RF-AGR-003 | [DOC-01 §6.1.1.7] | BC-06 · `sgspe.agreement` (`AgreementWriteGuard`) | `agreement`, `engagement` | `/api/objects/agreement/records`; `/api/objects/agreement/records/{id}/transitions/{name}` | Portal › Compromisos: ficha de `agreement` (propuesto) | `AgreementWorkflowApiTest` | F3 | Decisión pendiente (GAP-001) |

### 2.8 Quejas, consultas y solicitudes (GRV)

| Requirement | Source | Module | Entity | API | UI | Test | Fase | Estado |
|---|---|---|---|---|---|---|---|---|
| RF-GRV-001 | [DOC-01 Tabla 9.5, §9.1.1.2, §9.4.2] [PRM-01 §5] | BC-07 · `model/model.json`; `sgspe.attention` (`AttentionWriteGuard`, `QcsDueDatesListener`) | `grievance`, `inquiry`, `stakeholder_request` | `/api/objects/grievance/records`; `/api/objects/inquiry/records`; `/api/objects/stakeholder_request/records` | Portal › Quejas, consultas y solicitudes | `GrievanceWorkflowApiTest`; `InquiryWorkflowApiTest`; `StakeholderRequestWorkflowApiTest` | F4 | Decisión pendiente (GAP-005) |
| RF-GRV-002 | [PRM-01 §5, §8] | BC-07 · `model/workflows/grievance.json` (WF-E1); `sgspe.attention` (`QcsTransitionDatesListener`); automatizaciones AUT-02 y AUT-06 | `grievance` | `/api/objects/grievance/records/{id}/transitions/{name}` | Portal › Quejas, consultas y solicitudes: ficha de `grievance` | `GrievanceWorkflowApiTest` | F4 | Diseñado |
| RF-GRV-003 | [DOC-01 Tabla 9.5] | BC-07 · `model/workflows/inquiry.json` (WF-E2); automatización AUT-07 | `inquiry` | `/api/objects/inquiry/records/{id}/transitions/{name}` | Portal › Quejas, consultas y solicitudes: ficha de `inquiry`; tablero de QCS (KPI-GRV-07) | `InquiryWorkflowApiTest` | F4 | Diseñado |
| RF-GRV-004 | [DOC-01 §9.3.1.2] | BC-07 · `model/workflows/stakeholder_request.json` (WF-E3); `sgspe.attention` (aceptación, `RequestClosureListener`); automatización AUT-08 | `stakeholder_request`, `commitment`, `social_contribution` (desde F5) | `POST /api/sgspe/stakeholder-requests/{id}/accept`; `/api/objects/stakeholder_request/records/{id}/transitions/{name}` | Portal › Quejas, consultas y solicitudes: ficha de `stakeholder_request` | `StakeholderRequestWorkflowApiTest` | F4 | Diseñado |
| RF-GRV-005 | [DOC-01 Tabla 9.5] | BC-07 · `sgspe.attention` (reporte unificado); `sgspe.privacy` (exportación) | `grievance`, `inquiry`, `stakeholder_request` | `GET /api/sgspe/attention/report`; `POST /api/sgspe/exports/{kind}` (`attention-register`) | Portal › Quejas, consultas y solicitudes; Portal › Reportes | `AttentionReportApiTest` (propuesto); `security.md` §14 «Salidas» | F4 | Diseñado |
| RF-GRV-006 | [PRM-01 §5, §17] | BC-07 · `sgspe.attention` (`QcsDueDatesListener`); `sgspe.deadline` (`DeadlineJob`, JOB-01); `model/model.json` (plazos del proyecto) | `grievance`, `inquiry`, `stakeholder_request`, `task`, `project` | `/api/objects/grievance/records`; `/api/objects/inquiry/records` | Portal › Quejas, consultas y solicitudes; Portal › Tareas | `GrievanceWorkflowApiTest`; `workflows.md` §Pruebas «Trabajos» | F4 | Decisión pendiente (GAP-005) |
| RF-GRV-007 | [DOC-01 Tabla 9.5] | BC-07 · `sgspe.attention` (reclasificación) | `grievance`, `inquiry`, `stakeholder_request` | `POST /api/sgspe/grievances/{id}/reclassify`; `POST /api/sgspe/inquiries/{id}/reclassify`; `POST /api/sgspe/stakeholder-requests/{id}/reclassify` | Portal › Quejas, consultas y solicitudes: ficha de cada QCS | `GrievanceWorkflowApiTest` | F4 | Diseñado |

### 2.9 Riesgos sociales (RSK)

| Requirement | Source | Module | Entity | API | UI | Test | Fase | Estado |
|---|---|---|---|---|---|---|---|---|
| RF-RSK-001 | [DOC-01 §9.3.2, §9.3.2.1] [PRM-01 §5] | BC-08 · `model/model.json`; `model/workflows/social_risk.json` (WF-F); `sgspe.risk` (`RiskWriteGuard`) | `social_risk` (M2M `social_risk_stakeholder`) | `/api/objects/social_risk/records`; `/api/objects/social_risk/records/{id}/transitions/{name}` | Portal › Riesgos: ficha de `social_risk` | `SocialRiskWorkflowApiTest` | F4 | Decisión pendiente (GAP-004) |
| RF-RSK-002 | [DOC-01 §9.3.2.1] | BC-08 · `sgspe.risk` (endpoint de revisiones); `sgspe.deadline` (`review_status`) | `risk_assessment`, `social_risk` | `POST /api/sgspe/social-risks/{id}/assessments` | Portal › Riesgos: ficha de `social_risk` | `SocialRiskWorkflowApiTest`; `workflows.md` §Pruebas «Trabajos» | F4 | Diseñado |
| RF-RSK-003 | [DOC-01 §9.3.2.2] | BC-08 · `model/workflows/social_risk.json` (WF-F); `sgspe.risk` (`RiskWriteGuard`); automatización AUT-11 | `mitigation_measure`, `social_risk` | `/api/objects/mitigation_measure/records`; `/api/objects/social_risk/records/{id}/transitions/{name}` | Portal › Riesgos: ficha de `social_risk` | `SocialRiskWorkflowApiTest` | F4 | Diseñado |
| RF-RSK-004 | [DOC-01 §9.3, Tabla 9.4] | BC-08 · `model/model.json` | `potential_impact` | `/api/objects/potential_impact/records` | Portal › Riesgos | `test_model.py` | F4 | Diseñado |
| RF-RSK-005 | [DOC-01 §9.3.2] [DOC-02 p.6] | BC-08 · `model/model.json` | `social_risk`, `project_party` | `/api/objects/social_risk/records` | Portal › Riesgos: ficha de `social_risk` | `SocialRiskWorkflowApiTest` | F4 | Diseñado |

### 2.10 Alerta temprana e incidentes (EWS)

| Requirement | Source | Module | Entity | API | UI | Test | Fase | Estado |
|---|---|---|---|---|---|---|---|---|
| RF-EWS-001 | [DOC-02 p.2] [DOC-01 §9.3.2, Tabla 8.1] | BC-09 · `model/model.json`; `model/workflows/incident.json` (WF-M) | `incident` (M2M `incident_stakeholder`) | `/api/objects/incident/records`; `/api/objects/incident/records/{id}/transitions/{name}` | Portal › Riesgos: ficha de `incident` | `IncidentWorkflowApiTest` | F4 | Diseñado |
| RF-EWS-002 | [DOC-01 Tabla 9.5, §9.3.2.2, notas 20–21] | BC-09 · `model/model.json` | `media_mention` | `/api/objects/media_mention/records` | Portal › Riesgos | `test_model.py` | F4 | Diseñado |
| RF-EWS-003 | [DOC-01 §9.3.2.2, §9.4.2] | BC-09 · `model/model.json` | `early_warning_signal` | `/api/objects/early_warning_signal/records` | Portal › Riesgos | `test_model.py` | F4 | Diseñado |
| RF-EWS-004 | [PRM-01 §5] | BC-09 · `model/model.json`; `model/workflows/early_warning.json` (WF-J); `sgspe.warning` (`WarningRulesJob`, `WarningWriteGuard`); automatización AUT-09 | `early_warning_rule`, `early_warning` | `/api/objects/early_warning_rule/records`; `/api/objects/early_warning/records/{id}/transitions/{name}` | Portal › Riesgos: ficha de `early_warning` | `EarlyWarningWorkflowApiTest` | F4 | Diseñado |

### 2.11 Inversión social y programas (INV)

| Requirement | Source | Module | Entity | API | UI | Test | Fase | Estado |
|---|---|---|---|---|---|---|---|---|
| RF-INV-001 | [DOC-02 p.3–p.4] [DOC-01 §9.3.1.2] | BC-10 · `model/model.json`; `model/workflows/social_contribution.json` (WF-P); `sgspe.investment` (`InvestmentWriteGuard`, `ContributionTotalsListener`); automatización AUT-04 | `social_contribution`, `contribution_item`, `receipt`, `delivery`, `evidence` | `/api/objects/social_contribution/records`; `/api/objects/contribution_item/records`; `/api/objects/receipt/records`; `POST /api/sgspe/social-contributions/{id}/deliveries` | Portal › Programas sociales: ficha de `social_contribution` | `SocialContributionWorkflowApiTest` | F5 | Diseñado; Fuera del MVP |
| RF-INV-002 | [DOC-01 Tabla 9.2, §9.3.1.2] | BC-10 · `model/model.json` (políticas de `project`); `sgspe.investment` (`SupportPolicyGuard`) | `project`, `exploration_campaign` | `/api/objects/project/records`; `/api/objects/exploration_campaign/records` | Portal › Proyectos: ficha de `project` | `CommitmentWorkflowApiTest`; `SocialContributionWorkflowApiTest` | F3 | Upstream (UPS-15) |
| RF-INV-003 | [PRM-01 §5, §24] [DOC-01 §9.2, §9.3.1.2, Tabla 9.3] | BC-10 · `model/model.json`; `model/workflows/social_program.json` (WF-G); `sgspe.investment` (`InvestmentWriteGuard`) | `social_program`, `social_initiative`, `beneficiary_group`, `program_budget`, `program_indicator`, `program_measurement`, `program_milestone` | `/api/objects/social_program/records`; `/api/objects/social_initiative/records`; `/api/objects/program_budget/records`; `/api/objects/social_program/records/{id}/transitions/{name}` | Portal › Programas sociales: ficha de `social_program` | `SocialProgramWorkflowApiTest` | F5 | Diseñado; Fuera del MVP |
| RF-INV-004 | [DOC-01 §9.3.1.2] | BC-10 · `sgspe.investment` (recalculadores de montos); `sgspe.common` (`DerivedFieldsJob`, JOB-02); `sgspe.dashboard` | `exploration_campaign`, `commitment`, `social_contribution`, `program_budget` | `GET /api/sgspe/dashboards/investment`; `GET /api/sgspe/dashboards/indicators/{code}` | Portal › Programas sociales: tablero de inversión social (KPI-INV-01, KPI-INV-03) | `InvestmentDashboardApiTest` (propuesto); `workflows.md` §Pruebas «Trabajos» | F5 | Diseñado; Fuera del MVP |

### 2.12 Evidencias y documentos (DOC)

| Requirement | Source | Module | Entity | API | UI | Test | Fase | Estado |
|---|---|---|---|---|---|---|---|---|
| RF-DOC-001 | [DOC-01 Tabla 9.5, §9.3.1.2] [DOC-02 p.4] [PRM-01 §5] | BC-12 · `sgspe.evidence` (`EvidenceWriteGuard`, almacenamiento local o S3) | `evidence` | `POST /api/sgspe/evidence`; `GET /api/sgspe/evidence/{id}/content`; `PATCH /api/sgspe/evidence/{id}`; `POST /api/sgspe/evidence/{id}/links`; `DELETE /api/sgspe/evidence/{id}/links/{object}/{recordId}` | Portal › Documentos | `EvidenceApiTest` (propuesto); `deliverables.md` §13.3 «Almacenamiento» | F3 | Upstream (UPS-07) |
| RF-DOC-003 | [PRM-01 §5] [USR-07] | BC-12 · `sgspe.evidence`; `model/roles.json` (`DOWNLOAD_RESTRICTED`) | `evidence` | `GET /api/sgspe/evidence/{id}/content`; `PATCH /api/sgspe/evidence/{id}` | Portal › Documentos | `security.md` §14 «Descargas»; `test_apply_roles.py` | F3 | Diseñado |

### 2.13 Tareas y aprobaciones (WFL)

| Requirement | Source | Module | Entity | API | UI | Test | Fase | Estado |
|---|---|---|---|---|---|---|---|---|
| RF-WFL-001 | [PRM-01 §5] | BC-13 · `model/model.json`; `model/workflows/task.json` (WF-N); automatizaciones AUT-01, AUT-02 (desde F4), AUT-09 (desde F4), AUT-10, AUT-11 (desde F4) | `task` | `/api/objects/task/records` (filtro `assignee_user_id`); `/api/objects/task/records/{id}/transitions/{name}` | Portal › Tareas: bandeja «mis tareas» | `TaskWorkflowApiTest` | F3 | Diseñado |
| RF-WFL-002 | [PRM-01 §5, §8] [WAS-ADR-0042] | BC-13 · `model/workflows/*.json`; `model/roles.json` (`APPROVE`, `VERIFY`); guardas de cada paquete | `commitment`, `social_contribution` (desde F5), `social_program` (desde F5), `deliverable` (desde F8) | `/api/objects/commitment/records/{id}/transitions/{name}` | Portal › Compromisos: ficha de `commitment` (componente WORKFLOW) | `security.md` §14 «Transiciones»; `test_apply_roles.py` | F3 | Diseñado |
| RF-WFL-003 | [DOC-01 §9.3.1, §9.4.1] | BC-13 · `model/model.json` | `engagement_plan`, `planned_activity`, `task` | `/api/objects/planned_activity/records`; `/api/objects/task/records` | Portal › Tareas; Portal › Relacionamiento | `test_model.py` | F3 | Diseñado |
| RF-WFL-004 | [PRM-01 §5, §8, §17] | BC-13 · `sgspe.deadline` (`DeadlineJob`, JOB-01); `sgspe.notification` (destinatarios); `model/model.json` (`escalation_first_days`, `escalation_second_days`) | `task`, `commitment`, `project_assignment`; tabla fija `notification` | `/api/objects/task/records` | Portal › Tareas | `workflows.md` §Pruebas «Trabajos»; `workflows.md` §Pruebas «Destinatarios (ASM-070)» | F3 | Upstream (UPS-17) |

### 2.14 Notificaciones (NTF)

| Requirement | Source | Module | Entity | API | UI | Test | Fase | Estado |
|---|---|---|---|---|---|---|---|---|
| RF-NTF-001 | [PRM-01 §17] | BC-14 · `sgspe.notification` (`NotificationListener`, JOB-06); `sgspe.deadline` (JOB-01) | tablas fijas `notification` y `notification_delivery`; `project_assignment` | `GET /api/sgspe/notifications`; `GET /api/sgspe/notifications/unread-count`; `POST /api/sgspe/notifications/{id}/read`; `POST /api/sgspe/notifications/read-all` | Portal › Tareas: bandeja de avisos | `workflows.md` §Pruebas «Destinatarios (ASM-070)»; `NotificationApiTest` (propuesto) | F3 | Upstream (UPS-11, UPS-17) |

### 2.15 Reportes y entregables (REP)

| Requirement | Source | Module | Entity | API | UI | Test | Fase | Estado |
|---|---|---|---|---|---|---|---|---|
| RF-REP-001 | [USR-01] [DOC-01] | BC-15 · `sgspe.deliverable` (clase de T1); `model/workflows/deliverable.json` (WF-H) | `deliverable`, `deliverable_section`, `deliverable_version` | `POST /api/sgspe/deliverables/{id}/drafts`; `POST /api/sgspe/deliverables/{id}/issue` | Portal › Reportes | `deliverables.md` §13.2, criterio 1; `DeliverableWorkflowApiTest` | F8 | Diseñado; Fuera del MVP |
| RF-REP-002 | [USR-01] [DOC-02] | BC-15 · `sgspe.deliverable` (clase de T2); `model/workflows/deliverable.json` (WF-H) | `deliverable`, `deliverable_section`, `deliverable_version` | `POST /api/sgspe/deliverables/{id}/drafts`; `POST /api/sgspe/deliverables/{id}/issue` | Portal › Reportes | `deliverables.md` §13.2, criterio 2; `DeliverableWorkflowApiTest` | F8 | Diseñado; Fuera del MVP |

### 2.16 Tableros (DSH)

| Requirement | Source | Module | Entity | API | UI | Test | Fase | Estado |
|---|---|---|---|---|---|---|---|---|
| RF-DSH-001 | [PRM-01 §16] [USR-02] | BC-15 · `sgspe.dashboard` | `project`, `commitment`, `grievance`, `social_risk`, `engagement`, `early_warning`, `program_budget` | `GET /api/sgspe/dashboards/executive` | Portal › Dashboard: tablero ejecutivo | `ExecutiveDashboardApiTest` (propuesto); `ExecutiveDashboard.test.tsx` (propuesto) | F8 | Diseñado; Fuera del MVP |
| RF-DSH-002 | [PRM-01 §16] [USR-02] | BC-15 · `sgspe.dashboard` | `stakeholder`, `engagement`, `commitment`, `grievance`, `social_risk`, `program_budget` | `GET /api/sgspe/dashboards/project` | Portal › Dashboard y ficha del proyecto: tablero de proyecto | `ProjectDashboardApiTest` (propuesto); `ProjectDashboard.test.tsx` (propuesto) | F8 | Diseñado; Fuera del MVP |
| RF-DSH-003 | [PRM-01 §16] | BC-15 · `sgspe.dashboard` | `commitment`, `commitment_reschedule`, `delivery` | `GET /api/sgspe/dashboards/commitments` | Portal › Compromisos: tablero de compromisos | `CommitmentsDashboardApiTest` (propuesto); `CommitmentsDashboard.test.tsx` (propuesto) | F3 (indicadores del módulo), F8 | Diseñado |
| RF-DSH-004 | [PRM-01 §16] [DOC-01 Tabla 9.5] | BC-15 · `sgspe.dashboard` | `grievance`, `inquiry`, `stakeholder_request` | `GET /api/sgspe/dashboards/attention` | Portal › Quejas, consultas y solicitudes: tablero de QCS | `AttentionDashboardApiTest` (propuesto); `AttentionDashboard.test.tsx` (propuesto) | F4, F8 | Diseñado |
| RF-DSH-005 | [PRM-01 §16] [DOC-01 §9.3.2.1] | BC-15 · `sgspe.dashboard` | `social_risk`, `risk_assessment`, `mitigation_measure` | `GET /api/sgspe/dashboards/risks` | Portal › Riesgos: tablero de riesgos | `RisksDashboardApiTest` (propuesto); `RisksDashboard.test.tsx` (propuesto) | F4, F8 | Diseñado |
| RF-DSH-006 | [PRM-01 §15, §16] [USR-02] | BC-15 · `sgspe.dashboard`; `sgspe.territory` (geometría y filtros espaciales) | `locality`, `community`, `area_of_influence`, `area_of_influence_member`, `project_component` | `GET /api/sgspe/dashboards/territory`; `GET /api/sgspe/territory/localities/{id}/card`; `GET /api/sgspe/territory/communities/{id}/card` | Portal › Territorio: tablero territorial con mapa territorial y ficha contextual | `TerritoryDashboardApiTest` (propuesto); `TerritoryMap.test.tsx` (propuesto) | F6, F8 | Diseñado; Fuera del MVP |
| RF-DSH-007 | [DOC-01 §7.2, §9.3.1.1, Gráfico 10.3] | BC-15 · `sgspe.dashboard` | `stakeholder`, `stakeholder_assessment` | `GET /api/sgspe/dashboards/stakeholders` | Portal › Grupos de interés: tablero de grupos de interés | `StakeholdersDashboardApiTest` (propuesto); `StakeholdersDashboard.test.tsx` (propuesto) | F2, F8 | Diseñado |
| RF-DSH-008 | [DOC-01 Tabla 9.5] | BC-15 · `sgspe.dashboard` | `indicator`, `indicator_measurement`, `engagement`, `stakeholder`, `commitment`, `grievance`, `media_mention` | `GET /api/sgspe/dashboards/monitoring` | Portal › Dashboard: tablero de monitoreo de la Tabla 9.5 | `MonitoringDashboardApiTest` (propuesto); `MonitoringDashboard.test.tsx` (propuesto) | F8 | Diseñado; Fuera del MVP |
| RF-DSH-009 | [DOC-01 Tabla 9.5] [PRM-01 §16] | BC-15 · `sgspe.dashboard` | `engagement`, `engagement_participant`, `engagement_question`, `engagement_plan` | `GET /api/sgspe/dashboards/engagement` | Portal › Relacionamiento: tablero de relacionamiento | `EngagementDashboardApiTest` (propuesto); `EngagementDashboard.test.tsx` (propuesto) | F2, F8 | Diseñado |
| RF-DSH-010 | [PRM-01 §16, §29] [DOC-01 §9.3.1.2] | BC-15 · `sgspe.dashboard` | `exploration_campaign`, `program_budget`, `social_contribution`, `beneficiary_group` | `GET /api/sgspe/dashboards/investment` | Portal › Programas sociales: tablero de inversión social | `InvestmentDashboardApiTest` (propuesto); `InvestmentDashboard.test.tsx` (propuesto) | F5, F8 | Diseñado; Fuera del MVP |

### 2.17 Identidad y acceso (IAM)

| Requirement | Source | Module | Entity | API | UI | Test | Fase | Estado |
|---|---|---|---|---|---|---|---|---|
| RF-IAM-001 | [USR-03] [PRM-01 §14] | BC-16 · `model/roles.json`; `sgspe-ui` (`/admin` con `WasichaiApp`) | usuarios y roles de wasichai; `project_assignment` (desde F2) | `POST /api/auth/login`; `/api/users`; `/api/roles`; `/api/metadata/objects/**` | `/admin` (usuarios, roles, permisos y metadata) | `RolesApiTest`; `SecurityApiTest` | F1 | Implementado |
| RF-IAM-002 | [PRM-01 §14] [WAS-ADR-0020] | BC-16 · `sgspe-ui` (navegación del portal) | permisos de wasichai | `GET /api/auth/me/permissions`; `GET /api/sgspe/dashboards/indicators` (desde F2) | Portal › navegación principal | `Navigation.test.tsx` | F1 | Implementado; Upstream (UPS-06) |

### 2.18 IA y conocimiento (AIK)

| Requirement | Source | Module | Entity | API | UI | Test | Fase | Estado |
|---|---|---|---|---|---|---|---|---|
| RF-AIK-001 | [PRM-01 §9] | BC-18 · `sgspe.ai` (agentes Embabel); `model/workflows/ai_suggestion.json` (WF-K) | `ai_suggestion`, `ai_interaction`, `evidence` | `POST /api/sgspe/ai/runs`; `POST /api/sgspe/ai-suggestions/{id}/accept` | Portal › bandeja de revisión (`ai-architecture.md` §9.3) | `AiSuggestionWorkflowApiTest`; `ai-architecture.md` §15 «De integración» | F7 | Diseñado; Fuera del MVP |
| RF-AIK-002 | [PRM-01 §9] [ARQ-02] | BC-18 · `sgspe.ai` (agentes Embabel) | `ai_suggestion`, `engagement` | `POST /api/sgspe/ai/runs`; `POST /api/sgspe/ai-suggestions/{id}/accept` | Portal › bandeja de revisión; Relacionamiento: ficha de `engagement` | `AiSuggestionWorkflowApiTest` | F7 | Diseñado; Fuera del MVP |
| RF-AIK-003 | [PRM-01 §9] [ARQ-02] | BC-18 · `sgspe.ai` (agentes Embabel); `sgspe.commitment` (aceptación) | `commitment_candidate`, `ai_interaction` | `POST /api/sgspe/ai/runs`; `POST /api/sgspe/commitment-candidates/{id}/accept` | Portal › bandeja de revisión | `CommitmentCandidateWorkflowApiTest`; `ai-architecture.md` §15 «Del agente, con StubModel» | F7 | Diseñado; Fuera del MVP |
| RF-AIK-004 | [PRM-01 §9] [USR-01] | BC-18 · `sgspe.ai` (agentes Embabel); `sgspe.dashboard` (indicadores) | `ai_suggestion`, `project`, `deliverable_section` (desde F8) | `POST /api/sgspe/ai/runs`; `POST /api/sgspe/ai-suggestions/{id}/accept` | Portal › ficha del proyecto y editor de secciones (desde F8) (`ai-architecture.md` §9.3) | `AiSuggestionWorkflowApiTest`; `deliverables.md` §13.2, criterio 20 | F7 | Diseñado; Fuera del MVP |
| RF-AIK-005 | [PRM-01 §9] [WAS-ADR-0014] | BC-18 · `sgspe.ai` (llamada en proceso al asistente de wasichai-agent) | `ai_interaction` | `POST /api/sgspe/ai/assistant/questions`; `GET /api/sgspe/ai/status` | Portal › pantalla del asistente (`architecture.md` §9.3) | `ai-architecture.md` §15 «De integración»; `security.md` §14 «IA» | F7 | Upstream (UPS-14); Fuera del MVP |
| RF-AIK-006 | [PRM-01 §9] | BC-18 · `sgspe.ai` (agentes Embabel) (JOB-07, RAG) | `ai_interaction`, `evidence`; tabla fija `knowledge_chunk` | `POST /api/sgspe/ai/knowledge/questions` | Portal › Documentos: preguntas sobre documentos (propuesto) | `ai-architecture.md` §15 «De seguridad»; `workflows.md` §Pruebas «Trabajos» | F7 | Diseñado; Fuera del MVP |
| RF-AIK-007 | [PRM-01 §9] | BC-18 · `sgspe.ai` (agentes Embabel) (reglas deterministas y agente) | `ai_suggestion`, `grievance`, `organization` | `POST /api/sgspe/ai/runs`; `POST /api/sgspe/ai-suggestions/{id}/accept` | Portal › bandeja de revisión | `AiSuggestionWorkflowApiTest` | F7 | Diseñado; Fuera del MVP |
| RF-AIK-008 | [PRM-01 §9] | BC-18 · `sgspe.ai` (agentes Embabel) | `ai_suggestion`, `grievance`, `inquiry`, `stakeholder_request`, `engagement_question`, `media_mention` | `POST /api/sgspe/ai/runs`; `POST /api/sgspe/ai-suggestions/{id}/accept` | Portal › bandeja de revisión | `AiSuggestionWorkflowApiTest` | F7 | Diseñado; Fuera del MVP |
| RF-AIK-009 | [PRM-01 §10] | BC-18 · `sgspe.ai` (registro de interacciones) | `ai_interaction` | `POST /api/sgspe/ai/runs`; `POST /api/sgspe/ai/assistant/questions`; `POST /api/sgspe/ai/knowledge/questions` | `/admin` (lectura de `ai_interaction`) (propuesto) | `ai-architecture.md` §15 «De integración»; `AuditApiTest` | F7 | Upstream (UPS-14); Fuera del MVP |

### 2.19 Catálogos (CAT)

| Requirement | Source | Module | Entity | API | UI | Test | Fase | Estado |
|---|---|---|---|---|---|---|---|---|
| RF-CAT-001 | [USR-09] [DOC-01 §6, §7.2, Tabla 9.1] | BC-20 · `model/model.json` (ENUM compartidos y objetos catálogo) | `topic`, `administrative_unit`, `indicator` (desde F8) | `/api/objects/topic/records`; `/api/metadata/objects/**` | `/admin` (catálogos) | `test_model.py` | F2 | Decisión pendiente (CON-050) |

## 3. Requisitos de GIS, entregables, tableros, seguridad, auditoría e IA (RQ)

Las 133 RQ de `requirements-analysis.md` §12 a §16. Los huecos de numeración son IDs retirados al consolidar duplicados; ningún
documento los cita y su obligación vive en el ID vigente (`requirements-analysis.md` §20.4).

### 3.1 Requisitos GIS (RQ-GIS)

Fuente de las filas: `requirements-analysis.md` §12.

| Requirement | Source | Module | Entity | API | UI | Test | Fase | Estado |
|---|---|---|---|---|---|---|---|---|
| RQ-GIS-001 | [PRM-01 §15] [USR-02] [DOC-01 Anexo 1] [DOC-02 p.5] [DOC-03] | BC-02 · `sgspe.territory`; wasichai-gis | capas LYR-01 a LYR-24 de `gis.md` §2.2 | `GET /api/gis/objects/{object}/features`; `GET /api/sgspe/territory/layers/{layer}` | Portal › Territorio: mapa del proyecto; mapa territorial (desde F6) | `TerritoryLayersApiTest` (propuesto); `TerritoryMap.test.tsx` (propuesto) | F2 (básicas), F6 | Diseñado |
| RQ-GIS-002 | [PRM-01 §6] [DOC-01 §4.1, Tabla 9.2] [DOC-02 p.4] [DOC-03] | BC-02 · `sgspe.territory` (consultas espaciales con compuerta de permisos) | `area_of_influence`, `project_component`, `land_parcel`, `land_right`, `community`, `locality`, `environmental_feature`, `mining_concession` | `GET /api/sgspe/areas-of-influence/{id}/member-suggestions`; `GET /api/sgspe/territory/zones/{zoneObject}/{id}/records`; `GET /api/sgspe/territory/concentration`; `GET /api/sgspe/territory/projects/{id}/nearby-conflicts`; `GET /api/sgspe/territory/projects/{id}/component-parcels`; `GET /api/sgspe/territory/projects/{id}/component-communal-land`; `GET /api/sgspe/territory/projects/{id}/sensitive-distances`; `GET /api/sgspe/territory/projects/{id}/buffer-localities`; `GET /api/sgspe/territory/projects/{id}/concession-territories`; `GET /api/sgspe/territory/locate` | Portal › Territorio: mapa territorial | `gis.md` §9 «Componentes × predios»; `gis.md` §9 «Sugerencia de miembros» | F6 | Diseñado; Fuera del MVP |
| RQ-GIS-003 | [DOC-03] | BC-02 · `sgspe.territory` (importación con CRS de origen) | `project_component`, `land_parcel`, `mining_concession` | `POST /api/sgspe/territory/imports`; `POST /api/sgspe/territory/imports/{importBatchId}/confirm` | Portal › Territorio: importación con vista previa en el mapa | `gis.md` §9 «Importación tipo DOC-03»; `gis.md` §9 «CRS de origen» | F2 | Diseñado |
| RQ-GIS-004 | [PRM-01 §6] [DOC-01 Anexo 1] [DOC-03] | BC-01 · `model/model.json` (`working_crs`); `sgspe.territory` | `project` | `/api/objects/project/records` | Portal › Proyectos: ficha de `project` | `gis.md` §9 «Zona del centroide»; `ProjectWorkflowApiTest` | F2 | Diseñado |
| RQ-GIS-005 | [PRM-01 §15] [USR-02] | BC-02 · `sgspe.territory` (parte territorial); `sgspe.dashboard` (KPI-TER-01) | `locality`, `community` | `GET /api/sgspe/territory/localities/{id}/card`; `GET /api/sgspe/territory/communities/{id}/card`; `GET /api/sgspe/dashboards/indicators/{code}` | Portal › Territorio: mapa territorial y ficha contextual | `TerritoryCardApiTest` (propuesto); `TerritoryMap.test.tsx` (propuesto) | F6 | Diseñado; Fuera del MVP |
| RQ-GIS-006 | [WAS-ADR-0019] [DOC-03] | BC-02 · `model/model.json` (GEOMETRY en SRID 4326 y 2D) | campos GEOMETRY de `domain-model.md` §6 | `GET /api/gis/objects/{object}/features` | Portal › Territorio | `gis.md` §9 «Ida y vuelta UTM ↔ 4326»; `test_model.py` | F2 | Diseñado |
| RQ-GIS-007 | [DOC-03] | BC-02 · `sgspe.territory` (transformación con PostGIS) | `project_component`, `land_parcel`, `mining_concession` | `POST /api/sgspe/territory/imports` | Portal › Territorio: importación con vista previa en el mapa | `gis.md` §9 «PSAD56» | F2 | Diseñado |
| RQ-GIS-008 | [DOC-03] | BC-02 · `sgspe.territory`; `model/model.json` (`source_crs`, `source_reference`, `import_batch_id`) | `project_component`, `land_parcel`, `mining_concession`, `evidence` (desde F3) | `POST /api/sgspe/territory/imports/{importBatchId}/confirm` | Portal › Territorio: ficha de `project_component` | `gis.md` §9 «Confirmación» | F2 | Diseñado |
| RQ-GIS-009 | [DOC-03] [DOC-02 p.4] | BC-02 · `sgspe.territory` (`AreaListener`); `sgspe.common` (`DerivedFieldsJob`) | `area_of_influence`, `community`, `land_parcel`, `mining_concession` | `/api/objects/area_of_influence/records`; `GET /api/sgspe/areas-of-influence/{id}/summary` (desde F6) | Portal › Territorio: ficha de `area_of_influence` | `gis.md` §9 «Área de un cuadrado»; `gis.md` §9 «Área derivada» | F2 | Diseñado |
| RQ-GIS-010 | [DOC-03] | BC-02 · `sgspe.territory` (reconciliación y versiones) | `project_component` | `POST /api/sgspe/territory/imports`; `POST /api/sgspe/project-components` | Portal › Territorio: ficha de `project_component` | `gis.md` §9 «Importación tipo DOC-03» | F2 | Upstream (UPS-16) |
| RQ-GIS-011 | [WAS-ADR-0040] | BC-02 · `sgspe.territory` | geometrías de BC-02 | `POST /api/sgspe/territory/geometry-checks`; `GET /api/sgspe/territory/data-quality` | Portal › Territorio: listado de calidad (`gis.md` §8) | `gis.md` §9 «Polígono inválido» | F6 | Diseñado; Fuera del MVP |
| RQ-GIS-012 | [WAS-ADR-0006] [WAS-ADR-0007] | BC-02 · `sgspe.territory` (capas y proxy WMS) | capas LYR-NN públicas y temáticas | `GET /api/sgspe/territory/layers/{layer}`; `GET /api/sgspe/territory/wms` (desde F6) | Portal › Territorio: mapa territorial (desde F6) | `gis.md` §9 «Proxy WMS»; `security.md` §14 «Proxy WMS» | F2, F6 | Diseñado |
| RQ-GIS-013 | [PRM-01 §15] | BC-02 · `application.yml` por despliegue (`sgspe.map.base-style-url` desde F2 y `sgspe.map.base-style-url-by-organization` en F6, `gis.md` §6.4); `sgspe-ui` (mapa territorial) | — | — | Portal › Territorio: mapa territorial | `gis.md` §9 «Mapa base» | F6 | Upstream (UPS-13); Fuera del MVP |
| RQ-GIS-014 | [PRM-01 §15] [USR-02] [WAS-DOC modules/gis.md] | BC-02 · `sgspe-ui` (`@wasichai/gis`, MapLibre 6.10.0) | capas LYR-NN | `GET /api/sgspe/territory/layers/{layer}`; `GET /api/sgspe/territory/concentration` (desde F6) | Portal › Territorio: mapa territorial (desde F6) | `TerritoryMap.test.tsx` (propuesto) | F2 (básico), F6 | Diseñado |
| RQ-GIS-015 | [USR-07] | BC-02 · `sgspe.territory` (capas agregadas) | `stakeholder`, `locality`, `community` | `GET /api/sgspe/territory/layers/{layer}` | Portal › Territorio: mapa territorial (desde F6) (LYR-17) | `TerritoryLayersApiTest` (propuesto) | F2 | Diseñado |
| RQ-GIS-016 | [PRM-01 §6, §20] [WAS-ADR-0036] | BC-02 · `model/model.json` (`indexes`) | objetos con GEOMETRY y campos de filtro | — | — | `test_model.py` | F2 | Diseñado |
| RQ-GIS-017 | [WAS-ADR-0004] [WAS-ADR-0038] | BC-02 · `sgspe.territory` (compuerta de permisos) | `land_parcel`, `project_component`, `community` | `GET /api/sgspe/territory/projects/{id}/component-parcels`; `GET /api/sgspe/territory/zones/{zoneObject}/{id}/records` | Portal › Territorio: mapa territorial | `gis.md` §9 «Compuerta de permisos»; `security.md` §14 «Alcance, opción B» | F6 | Decisión pendiente (CON-041); Upstream (UPS-01); Fuera del MVP |
| RQ-GIS-018 | [DOC-03] | BC-02 · `sgspe.territory` (GDAL, exportaciones y WFS); `sgspe.privacy` | `export_log`, `project_component`, `land_parcel`, `mining_concession` | `POST /api/sgspe/territory/imports`; `POST /api/sgspe/territory/exports`; `GET /api/sgspe/territory/wfs` | Portal › Territorio | `security.md` §14 «Importaciones»; `TerritoryExportApiTest` (propuesto) | F6 | Diseñado; Fuera del MVP |
| RQ-GIS-019 | [DOC-01 Anexo 1] | BC-02 · `model/publish_layers.py`; `sgspe.deliverable` (mapas de informe) (desde F8) | capas LYR-01, LYR-02, LYR-03, LYR-04, LYR-05, LYR-07, LYR-09, LYR-10, LYR-14, LYR-15 | `/api/gis/layers/**` | Portal › Reportes (mapas de T1) | `gis.md` §9 «GetMap (suite lenta, etiquetada)» | F6, F8 | Diseñado; Fuera del MVP |
| RQ-GIS-020 | [PRM-01 §6] [WAS-DOC gis/geometry.md] | BC-02 · wasichai-gis; `sgspe.territory` (importación) | campos MULTIPOLYGON de `domain-model.md` §6 | `POST /api/sgspe/territory/imports`; `/api/objects/{object}/records` | Portal › Territorio | `gis.md` §9 «MULTI*» | F2 | Diseñado |
| RQ-GIS-021 | [PRM-01 §21] | BC-02 · `sgspe.territory` (pruebas) | geometrías sintéticas | — | — | `gis.md` §9 (todas) | F2, F6 | Diseñado |
| RQ-GIS-022 | [PRM-01 §6] | BC-02 · `sgspe.territory` (lee el SRID de la metadata) | campos GEOMETRY | — | — | `gis.md` §9 «SRID desde la metadata» | F2 | Diseñado |

### 3.2 Requisitos de entregables (RQ-REP)

Fuente de las filas: `requirements-analysis.md` §13.1.

| Requirement | Source | Module | Entity | API | UI | Test | Fase | Estado |
|---|---|---|---|---|---|---|---|---|
| RQ-REP-001 | [USR-01] [DOC-01] [DOC-02] | BC-15 · `sgspe.deliverable` (Apache POI y Gotenberg) | `deliverable`, `deliverable_section`, `deliverable_version` | `POST /api/sgspe/deliverables/{id}/drafts`; `GET /api/sgspe/deliverables/{id}/draft/content`; `GET /api/sgspe/deliverables/{id}/versions/{versionNumber}/content` | Portal › Reportes | `deliverables.md` §13.2, criterio 3; `deliverables.md` §13.3 «PDF» | F8 | Diseñado; Fuera del MVP |
| RQ-REP-002 | [USR-01] [DOC-01 §9.3.2, Tabla 9.5] [DOC-02] | BC-15 · `sgspe.deliverable` (una clase por tipo) | `deliverable`, `deliverable_section`, `deliverable_version` | `POST /api/sgspe/deliverables/{id}/drafts`; `GET /api/sgspe/deliverables/{id}/draft/content` | Portal › Reportes | `deliverables.md` §13.3 «Unitarias»; `deliverables.md` §13.2, criterios 1 y 2 | F8 | Diseñado; Fuera del MVP |
| RQ-REP-003 | [DOC-01 portada] [DOC-02 p.1] | BC-15 · `sgspe.deliverable` | `deliverable`, `organization` | `POST /api/sgspe/deliverables/{id}/drafts`; `GET /api/sgspe/deliverables/{id}/draft/content` | Portal › Reportes | `deliverables.md` §13.3 «Lectura del DOCX» | F8 | Diseñado; Fuera del MVP |
| RQ-REP-004 | [DOC-01 índice] | BC-15 · `sgspe.deliverable` | `deliverable` | `POST /api/sgspe/deliverables/{id}/drafts`; `GET /api/sgspe/deliverables/{id}/draft/content` | Portal › Reportes | `deliverables.md` §13.2, criterio 4 | F8 | Diseñado; Fuera del MVP |
| RQ-REP-005 | [DOC-01] | BC-15 · `sgspe.deliverable` | `deliverable_section` | `POST /api/sgspe/deliverables/{id}/drafts`; `GET /api/sgspe/deliverables/{id}/draft/content` | Portal › Reportes | `deliverables.md` §13.3 «Lectura del DOCX» | F8 | Diseñado; Fuera del MVP |
| RQ-REP-006 | [DOC-01 Tablas 5.10, 6.1, 7.1] | BC-15 · `sgspe.deliverable` | `deliverable` | `POST /api/sgspe/deliverables/{id}/drafts`; `GET /api/sgspe/deliverables/{id}/draft/content` | Portal › Reportes | `deliverables.md` §13.2, criterio 5 | F8 | Diseñado; Fuera del MVP |
| RQ-REP-007 | [DOC-01 Gráfico 10.2] [DOC-02 p.5] | BC-15 · `sgspe.deliverable` | `deliverable`, `evidence` | `POST /api/sgspe/deliverables/{id}/drafts`; `GET /api/sgspe/deliverables/{id}/draft/content` | Portal › Reportes | `deliverables.md` §13.3 «Unitarias»; `deliverables.md` §13.3 «Lectura del DOCX» | F8 | Diseñado; Fuera del MVP |
| RQ-REP-008 | [USR-01] [DOC-01 Anexo 1] [DOC-02 p.1, p.5] [DOC-03] | BC-15 · `sgspe.deliverable` (GetMap) | `deliverable`, `project` | `POST /api/sgspe/deliverables/{id}/drafts`; `GET /api/sgspe/deliverables/{id}/draft/content` | Portal › Reportes | `deliverables.md` §13.2, criterio 7 | F8 | Diseñado; Fuera del MVP |
| RQ-REP-009 | [DOC-01 §7.2, §9.3, Anexo 3] [PRM-01 §16] | BC-15 · `sgspe.deliverable`; `sgspe.dashboard` (mismo código de indicadores) | `stakeholder_assessment`, `social_risk` | `POST /api/sgspe/deliverables/{id}/drafts`; `GET /api/sgspe/deliverables/{id}/draft/content` | Portal › Reportes | `deliverables.md` §13.2, criterio 19; `DeliverableAssemblyApiTest` (propuesto) | F8 | Diseñado; Fuera del MVP |
| RQ-REP-010 | [DOC-02 p.4] [DOC-01] | BC-15 · `sgspe.deliverable` | `evidence` | `POST /api/sgspe/deliverables/{id}/drafts`; `GET /api/sgspe/deliverables/{id}/draft/content` | Portal › Reportes | `deliverables.md` §13.2, criterio 6 | F8 | Diseñado; Fuera del MVP |
| RQ-REP-011 | [DOC-01] [DOC-02 p.2, p.5–p.6] [PRM-01 §9] | BC-15 · `sgspe.deliverable` | `deliverable_section` | `/api/objects/deliverable_section/records` | Portal › Reportes: editor de secciones | `DeliverableWorkflowApiTest` | F8 | Diseñado; Fuera del MVP |
| RQ-REP-012 | [DOC-01 Tabla 3.1, Tablas 6.1–7.3, Tabla 9.1, Tabla 9.5] | BC-15 · `sgspe.deliverable`; `sgspe.dashboard` | `stakeholder`, `engagement`, `commitment`, `grievance`, `social_risk`, `indicator` | `POST /api/sgspe/deliverables/{id}/drafts`; `GET /api/sgspe/deliverables/{id}/draft/content` | Portal › Reportes | `deliverables.md` §13.2, criterio 19 | F8 | Diseñado; Fuera del MVP |
| RQ-REP-013 | [DOC-02 p.2] | BC-15 · `sgspe.deliverable` | `deliverable`, `area_of_influence`, `community`, `locality` | `/api/objects/deliverable/records`; `POST /api/sgspe/deliverables/{id}/drafts`; `GET /api/sgspe/deliverables/{id}/draft/content` | Portal › Reportes | `DeliverableAssemblyApiTest` (propuesto) | F8 | Diseñado; Fuera del MVP |
| RQ-REP-014 | [USR-07] [DOC-01 §7.1] | BC-15 · `sgspe.deliverable`; `sgspe.privacy` (niveles de divulgación) | `deliverable_version`, `export_log`, `perception` | `POST /api/sgspe/deliverables/{id}/issue`; `POST /api/sgspe/exports/{kind}` | Portal › Reportes | `deliverables.md` §13.2, criterios 11, 12 y 23; `security.md` §14 «Salidas» | F8 | Decisión pendiente (GAP-001); Fuera del MVP |
| RQ-REP-015 | [USR-01] [PRM-01 §8] | BC-15 · `model/workflows/deliverable.json` (WF-H); `sgspe.deliverable` (`DeliverableWriteGuard`) | `deliverable` | `/api/objects/deliverable/records/{id}/transitions/{name}` | Portal › Reportes | `DeliverableWorkflowApiTest` | F8 | Diseñado; Fuera del MVP |
| RQ-REP-016 | [DOC-01] | BC-15 · `sgspe.deliverable` | `deliverable` | `/api/objects/deliverable/records` | Portal › Reportes | `deliverables.md` §13.3 «Unitarias» | F8 | Diseñado; Fuera del MVP |
| RQ-REP-017 | [USR-01] [PRM-01 §18] | BC-15 · `sgspe.deliverable` (emisión) | `deliverable_version` | `POST /api/sgspe/deliverables/{id}/issue` | Portal › Reportes | `deliverables.md` §13.2, criterios 8 y 9 | F8 | Diseñado; Fuera del MVP |
| RQ-REP-019 | [DOC-01 portada] [DOC-02 p.5] | BC-15 · `sgspe.deliverable` (plantilla Word por organización) | `deliverable`, `organization` | `POST /api/sgspe/deliverable-templates`; `GET /api/sgspe/deliverable-templates` | Portal › Reportes | `DeliverableTemplateApiTest` (propuesto) | F8 | Decisión pendiente (GAP-009); Fuera del MVP |
| RQ-REP-020 | [DOC-01] [DOC-02 p.1] | BC-15 · `sgspe.deliverable` | `deliverable` | `POST /api/sgspe/deliverables/{id}/drafts`; `GET /api/sgspe/deliverables/{id}/draft/content` | Portal › Reportes | `deliverables.md` §13.2, criterio 15 | F8 | Diseñado; Fuera del MVP |
| RQ-REP-021 | [USR-01] [DOC-01] | BC-15 · `sgspe.deliverable` (JOB-05, trabajador de generación) | `deliverable`, `export_log` | `POST /api/sgspe/deliverables/{id}/drafts`; `GET /api/sgspe/deliverables/{id}/draft`; `GET /api/sgspe/deliverables/{id}/draft/content` | Portal › Reportes | `deliverables.md` §13.2, criterios 8 y 16; `deliverables.md` §13.3 «Guardas» | F8 | Diseñado; Fuera del MVP |
| RQ-REP-022 | [DOC-01 §9.3.1.2, Tabla 9.5] | BC-15 · `sgspe.privacy` (exportaciones XLSX) | `stakeholder`, `commitment` (desde F3), `grievance` (desde F4), `inquiry` (desde F4), `stakeholder_request` (desde F4), `social_risk` (desde F4), `export_log` | `POST /api/sgspe/exports/{kind}` | Portal › Reportes (exportaciones) | `security.md` §14 «Salidas»; `deliverables.md` §13.2, criterio 21 | F2–F4 | Diseñado |
| RQ-REP-023 | [DOC-01 Anexos 1–3] | BC-15 · `sgspe.deliverable` (recursos estáticos por tipo) | `deliverable` | `POST /api/sgspe/deliverables/{id}/drafts`; `GET /api/sgspe/deliverables/{id}/draft/content` | Portal › Reportes | `deliverables.md` §13.3 «Unitarias» | F8 | Diseñado; Fuera del MVP |
| RQ-REP-024 | [DOC-02 p.1–p.6] [DOC-01] | BC-15 · `sgspe.deliverable` | `deliverable` | `POST /api/sgspe/deliverables/{id}/drafts`; `GET /api/sgspe/deliverables/{id}/draft/content` | Portal › Reportes | `deliverables.md` §13.3 «PDF» | F8 | Diseñado; Fuera del MVP |
| RQ-REP-025 | [DOC-02 p.4] | BC-15 · `sgspe.deliverable` | `community`, `land_parcel` | `POST /api/sgspe/deliverables/{id}/drafts`; `GET /api/sgspe/deliverables/{id}/draft/content` | Portal › Reportes | `deliverables.md` §13.2, criterio 13 | F8 | Diseñado; Fuera del MVP |
| RQ-REP-026 | [USR-01] [PRM-01 §18] [WAS-ADR-0023] | BC-15 · `sgspe.deliverable` (emisión congelada); `model/workflows/deliverable.json` (WF-H) | `deliverable`, `deliverable_version` | `POST /api/sgspe/deliverables/{id}/issue` | Portal › Reportes | `deliverables.md` §13.2, criterio 10; `DeliverableWorkflowApiTest` | F8 | Diseñado; Fuera del MVP |
| RQ-REP-027 | [USR-01] | BC-15 · `sgspe.deliverable` (JOB-08, retención de borradores) | `deliverable`, `deliverable_version` | — | — | `workflows.md` §Pruebas «Trabajos» | F8 | Diseñado; Fuera del MVP |
| RQ-REP-028 | [PRM-01 §14] | BC-15 · `sgspe.deliverable` | `evidence` | `POST /api/sgspe/deliverables/{id}/drafts`; `GET /api/sgspe/deliverables/{id}/draft/content` | Portal › Reportes | `deliverables.md` §13.2, criterio 6 | F8 | Diseñado; Fuera del MVP |
| RQ-REP-029 | [DOC-01 §9.3.1.2, Tabla 9.5] [DOC-02 p.4] [WAS-ADR-0023] | BC-12 · wasichai-documents; automatizaciones AUT-03, AUT-04 (desde F5), AUT-05, AUT-06 (desde F4), AUT-07 (desde F4), AUT-08 (desde F4) y AUT-12 (desde F8) | `delivery`, `engagement`, `grievance` (desde F4), `inquiry` (desde F4), `stakeholder_request` (desde F4), `stakeholder`, `locality` | `POST /api/objects/{object}/records/{id}/documents/{type}`; `GET /api/documents/{id}` | Portal › Documentos | `workflows.md` §Pruebas «Automatizaciones» | F3 | Diseñado |
| RQ-REP-030 | [DOC-01 Anexo 1] [DOC-02 p.5] [WAS-ADR-0006] | BC-15 · `sgspe.deliverable` (GeoServer GetMap) | `deliverable` | `POST /api/sgspe/deliverables/{id}/drafts`; `GET /api/sgspe/deliverables/{id}/draft/content` | Portal › Reportes | `gis.md` §9 «GetMap (suite lenta, etiquetada)»; `deliverables.md` §13.2, criterio 7 | F8 | Diseñado; Fuera del MVP |
| RQ-REP-031 | [DOC-01 §7.2, §9.3.2, Gráfico 10.3] | BC-15 · `sgspe.deliverable` (gráficos nativos de Word) | `stakeholder_assessment`, `social_risk` | `POST /api/sgspe/deliverables/{id}/drafts`; `GET /api/sgspe/deliverables/{id}/draft/content` | Portal › Reportes | `deliverables.md` §13.3 «Lectura del DOCX» | F8 | Diseñado; Fuera del MVP |
| RQ-REP-032 | [DOC-01] [DOC-02] | BC-15 · `model/model.json`; `sgspe.deliverable` (`DeliverableWriteGuard`) | `deliverable_section` | `/api/objects/deliverable_section/records`; `GET /api/objects/{object}/records/{id}/history` | Portal › Reportes: editor de secciones | `DeliverableWorkflowApiTest` | F8 | Diseñado; Fuera del MVP |
| RQ-REP-033 | [USR-07] [DOC-01 §7.1] | BC-15 · `sgspe.privacy` (menciones como referencias) | `deliverable_section`, `deliverable_version` | `POST /api/sgspe/deliverables/{id}/issue`; `POST /api/sgspe/exports/{kind}` | Portal › Reportes | `deliverables.md` §13.2, criterio 11; `security.md` §14 «Salidas» | F8 | Diseñado; Fuera del MVP |

### 3.3 Requisitos de tableros e indicadores (RQ-DSH)

Fuente de las filas: `requirements-analysis.md` §13.2.

| Requirement | Source | Module | Entity | API | UI | Test | Fase | Estado |
|---|---|---|---|---|---|---|---|---|
| RQ-DSH-001 | [PRM-01 §16] [USR-02] | BC-15 · `sgspe.dashboard` | objetos de cada KPI de `dashboards.md` §4 | `GET /api/sgspe/dashboards/{dashboard}`; `GET /api/sgspe/dashboards/indicators/{code}` | Portal › todos los tableros (fila de filtros) | un `<Tablero>DashboardApiTest` (propuesto) por tablero (§1.1) | F2–F8 | Diseñado |
| RQ-DSH-002 | [PRM-01 §16] | BC-15 · `sgspe.dashboard` | `project`, `commitment`, `grievance`, `social_risk`, `engagement`, `early_warning`, `program_budget` | `GET /api/sgspe/dashboards/executive`; `GET /api/sgspe/dashboards/indicators/{code}` | Portal › Dashboard: tablero ejecutivo | `ExecutiveDashboardApiTest` (propuesto) | F8 | Diseñado; Fuera del MVP |
| RQ-DSH-003 | [PRM-01 §16] | BC-15 · `sgspe.dashboard` | `engagement`, `grievance`, `commitment`, `commitment_reschedule` | `GET /api/sgspe/dashboards/executive`; `GET /api/sgspe/dashboards/indicators/{code}` | Portal › Dashboard: tablero ejecutivo | `ExecutiveDashboardApiTest` (propuesto) | F8 | Diseñado; Fuera del MVP |
| RQ-DSH-004 | [DOC-01 §7.2, Tablas 7.1–7.3] | BC-15 · `sgspe.dashboard` | `stakeholder` | `GET /api/sgspe/dashboards/stakeholders`; `GET /api/sgspe/dashboards/indicators/{code}` | Portal › Grupos de interés: tablero de grupos de interés | `StakeholdersDashboardApiTest` (propuesto) | F2 | Diseñado |
| RQ-DSH-005 | [DOC-01 Anexo 3, Gráfico 10.3] | BC-15 · `sgspe.dashboard` | `stakeholder`, `stakeholder_assessment`, `project` | `GET /api/sgspe/dashboards/stakeholders`; `GET /api/sgspe/dashboards/indicators/{code}` | Portal › Grupos de interés: tablero de grupos de interés | `StakeholdersDashboardApiTest` (propuesto) | F2 | Diseñado |
| RQ-DSH-006 | [DOC-01 Tabla 9.5] | BC-15 · `sgspe.dashboard` | `engagement`, `engagement_participant`, `stakeholder`, `person`, `position` | `GET /api/sgspe/dashboards/monitoring` (desde F8); `GET /api/sgspe/dashboards/indicators/{code}` | Portal › Dashboard: tablero de monitoreo (desde F8); antes, detalle del KPI (propuesto) | `MonitoringDashboardApiTest` (propuesto) | F2 | Diseñado |
| RQ-DSH-007 | [DOC-01 Tabla 9.5] | BC-15 · `sgspe.dashboard` | `stakeholder_assessment`, `engagement` | `GET /api/sgspe/dashboards/monitoring` (desde F8); `GET /api/sgspe/dashboards/indicators/{code}` | Portal › Dashboard: tablero de monitoreo (desde F8); antes, detalle del KPI (propuesto) | `MonitoringDashboardApiTest` (propuesto) | F2 | Diseñado |
| RQ-DSH-008 | [DOC-01 Tabla 9.5, §9.3.1.1] | BC-15 · `sgspe.dashboard` | `stakeholder_assessment` | `GET /api/sgspe/dashboards/stakeholders`; `GET /api/sgspe/dashboards/indicators/{code}` | Portal › Grupos de interés: tablero de grupos de interés | `StakeholdersDashboardApiTest` (propuesto) | F2 | Diseñado |
| RQ-DSH-009 | [DOC-01 Tabla 9.5] | BC-15 · `sgspe.dashboard` | `engagement`, `engagement_participant`, `stakeholder` | `GET /api/sgspe/dashboards/monitoring` (desde F8); `GET /api/sgspe/dashboards/indicators/{code}` | Portal › Dashboard: tablero de monitoreo (desde F8); antes, detalle del KPI (propuesto) | `MonitoringDashboardApiTest` (propuesto) | F2 | Diseñado |
| RQ-DSH-010 | [DOC-01 §9.3.1.1] | BC-15 · `sgspe.dashboard` | `stakeholder_assessment` | `GET /api/sgspe/dashboards/stakeholders`; `GET /api/sgspe/dashboards/indicators/{code}` | Portal › Grupos de interés: tablero de grupos de interés | `StakeholdersDashboardApiTest` (propuesto) | F2 | Diseñado |
| RQ-DSH-011 | [DOC-01 Tabla 9.5] | BC-15 · `sgspe.dashboard` | `engagement`, `engagement_plan`, `project` | `GET /api/sgspe/dashboards/engagement`; `GET /api/sgspe/dashboards/indicators/{code}` | Portal › Relacionamiento: tablero de relacionamiento | `EngagementDashboardApiTest` (propuesto) | F2 | Diseñado |
| RQ-DSH-012 | [DOC-01 Tabla 9.5] | BC-15 · `sgspe.dashboard` | `engagement`, `engagement_participant` | `GET /api/sgspe/dashboards/engagement`; `GET /api/sgspe/dashboards/indicators/{code}` | Portal › Relacionamiento: tablero de relacionamiento | `EngagementDashboardApiTest` (propuesto) | F2 | Diseñado |
| RQ-DSH-013 | [DOC-01 Tabla 9.5] | BC-15 · `sgspe.dashboard` | `engagement_question`, `topic`; M2M `engagement_topic` | `GET /api/sgspe/dashboards/engagement`; `GET /api/sgspe/dashboards/indicators/{code}` | Portal › Relacionamiento: tablero de relacionamiento | `EngagementDashboardApiTest` (propuesto) | F2 | Diseñado |
| RQ-DSH-014 | [DOC-01 Tabla 9.5] | BC-15 · `sgspe.dashboard` | `engagement_question`, `grievance` (desde F4), `inquiry` (desde F4), `stakeholder_request` (desde F4) | `GET /api/sgspe/dashboards/engagement`; `GET /api/sgspe/dashboards/indicators/{code}` | Portal › Relacionamiento: tablero de relacionamiento | `EngagementDashboardApiTest` (propuesto) | F2 | Diseñado |
| RQ-DSH-015 | [DOC-01 Tabla 9.5] | BC-15 · `sgspe.dashboard` | `engagement`, `evidence` | `GET /api/sgspe/dashboards/monitoring` (desde F8); `GET /api/sgspe/dashboards/indicators/{code}` | Portal › Dashboard: tablero de monitoreo (desde F8); antes, detalle del KPI (propuesto) | `MonitoringDashboardApiTest` (propuesto) | F4 | Diseñado |
| RQ-DSH-016 | [DOC-01 Tabla 9.5] | BC-15 · `sgspe.dashboard` | `media_mention`, `commitment` | `GET /api/sgspe/dashboards/monitoring` (desde F8); `GET /api/sgspe/dashboards/indicators/{code}` | Portal › Dashboard: tablero de monitoreo (desde F8); antes, detalle del KPI (propuesto) | `MonitoringDashboardApiTest` (propuesto) | F4 | Diseñado |
| RQ-DSH-017 | [DOC-01 Tabla 9.5] | BC-15 · `sgspe.dashboard` | `media_mention` | `GET /api/sgspe/dashboards/monitoring` (desde F8); `GET /api/sgspe/dashboards/indicators/{code}` | Portal › Dashboard: tablero de monitoreo (desde F8); antes, detalle del KPI (propuesto) | `MonitoringDashboardApiTest` (propuesto) | F4 | Diseñado |
| RQ-DSH-018 | [PRM-01 §16] | BC-15 · `sgspe.dashboard` | `commitment`, `project` | `GET /api/sgspe/dashboards/commitments`; `GET /api/sgspe/dashboards/indicators/{code}` | Portal › Compromisos: tablero de compromisos | `CommitmentsDashboardApiTest` (propuesto) | F3 | Diseñado |
| RQ-DSH-019 | [PRM-01 §16] [DOC-01 Tabla 9.5] | BC-15 · `sgspe.dashboard` | `commitment` | `GET /api/sgspe/dashboards/commitments`; `GET /api/sgspe/dashboards/indicators/{code}` | Portal › Compromisos: tablero de compromisos | `CommitmentsDashboardApiTest` (propuesto) | F3 | Diseñado |
| RQ-DSH-020 | [PRM-01 §16] | BC-15 · `sgspe.dashboard` | `commitment`, `commitment_reschedule` | `GET /api/sgspe/dashboards/commitments`; `GET /api/sgspe/dashboards/indicators/{code}` | Portal › Compromisos: tablero de compromisos | `CommitmentsDashboardApiTest` (propuesto) | F3 | Diseñado |
| RQ-DSH-021 | [DOC-01 §9.3.1.2] | BC-15 · `sgspe.dashboard` | `commitment`, `delivery`, `exploration_campaign` | `GET /api/sgspe/dashboards/commitments`; `GET /api/sgspe/dashboards/indicators/{code}` | Portal › Compromisos: tablero de compromisos | `CommitmentsDashboardApiTest` (propuesto) | F3 | Diseñado |
| RQ-DSH-022 | [PRM-01 §16] | BC-15 · `sgspe.dashboard` | `grievance`, `inquiry`, `stakeholder_request` | `GET /api/sgspe/dashboards/attention`; `GET /api/sgspe/dashboards/indicators/{code}` | Portal › Quejas, consultas y solicitudes: tablero de QCS | `AttentionDashboardApiTest` (propuesto) | F4 | Diseñado |
| RQ-DSH-023 | [DOC-01 Tabla 9.5] | BC-15 · `sgspe.dashboard` | `grievance`, `inquiry`, `stakeholder_request` | `GET /api/sgspe/dashboards/attention`; `GET /api/sgspe/dashboards/indicators/{code}` | Portal › Quejas, consultas y solicitudes: tablero de QCS | `AttentionDashboardApiTest` (propuesto) | F4 | Diseñado |
| RQ-DSH-024 | [PRM-01 §16] | BC-15 · `sgspe.dashboard` | `grievance`, `inquiry`, `stakeholder_request` | `GET /api/sgspe/dashboards/attention`; `GET /api/sgspe/dashboards/indicators/{code}` | Portal › Quejas, consultas y solicitudes: tablero de QCS | `AttentionDashboardApiTest` (propuesto) | F4 | Diseñado |
| RQ-DSH-025 | [PRM-01 §16, §17] | BC-15 · `sgspe.dashboard` | `grievance`, `inquiry`, `stakeholder_request` | `GET /api/sgspe/dashboards/attention`; `GET /api/sgspe/dashboards/indicators/{code}` | Portal › Quejas, consultas y solicitudes: tablero de QCS | `AttentionDashboardApiTest` (propuesto) | F4 | Diseñado |
| RQ-DSH-026 | [PRM-01 §16] [DOC-01 §9.3.2.1] | BC-15 · `sgspe.dashboard` | `social_risk`, `risk_assessment`, `project` | `GET /api/sgspe/dashboards/risks`; `GET /api/sgspe/dashboards/indicators/{code}` | Portal › Riesgos: tablero de riesgos | `RisksDashboardApiTest` (propuesto) | F4 | Diseñado |
| RQ-DSH-027 | [PRM-01 §16] [DOC-01 §9.3.2.1] | BC-15 · `sgspe.dashboard` | `social_risk`, `risk_assessment`, `mitigation_measure` | `GET /api/sgspe/dashboards/risks`; `GET /api/sgspe/dashboards/indicators/{code}` | Portal › Riesgos: tablero de riesgos | `RisksDashboardApiTest` (propuesto) | F4 | Diseñado |
| RQ-DSH-029 | [DOC-03] [DOC-01 §4] | BC-15 · `sgspe.dashboard`; `sgspe.territory` (consulta espacial de KPI-TER-04) | `area_of_influence`, `area_of_influence_member`, `project_component`, `community`, `land_parcel` | `GET /api/sgspe/dashboards/territory`; `GET /api/sgspe/areas-of-influence/{id}/summary` | Portal › Territorio: tablero territorial | `TerritoryDashboardApiTest` (propuesto) | F6 | Diseñado; Fuera del MVP |
| RQ-DSH-030 | [PRM-01 §16, §29] [DOC-01 §9.3.1.2] | BC-15 · `sgspe.dashboard` | `exploration_campaign`, `program_budget`, `social_contribution`, `beneficiary_group`, `commitment` | `GET /api/sgspe/dashboards/investment`; `GET /api/sgspe/dashboards/indicators/{code}` | Portal › Programas sociales: tablero de inversión social | `InvestmentDashboardApiTest` (propuesto) | F5 | Diseñado; Fuera del MVP |
| RQ-DSH-031 | [WAS-ADR-0036] [WAS-ADR-0038] [PRM-01 §20] | BC-15 · `sgspe.dashboard` (conteos con `RecordService`, agregación en Kotlin) | — | `GET /api/sgspe/dashboards/{dashboard}`; `GET /api/sgspe/dashboards/indicators/{code}` | — | un `<Tablero>DashboardApiTest` (propuesto) por tablero (§1.1); `architecture.md` §13 «Unitarias» | F2–F8 | Diseñado |
| RQ-DSH-032 | [WAS-ADR-0020] | BC-15 · `sgspe.dashboard` (chequeo previo de lectura) | `stakeholder` (`stance`) | `GET /api/sgspe/dashboards/{dashboard}`; `GET /api/sgspe/dashboards/indicators/{code}` | Portal › todos los tableros («no disponible») | `DashboardPermissionsApiTest` (propuesto); `gis.md` §9 «Compuerta de permisos» | F2–F8 | Diseñado |
| RQ-DSH-034 | [USR-01] [USR-02] | BC-15 · `sgspe.dashboard`; `sgspe.deliverable` | `indicator` | `GET /api/sgspe/dashboards/indicators/{code}` | Portal › tableros y Reportes | `deliverables.md` §13.2, criterio 19 | F8 | Diseñado; Fuera del MVP |
| RQ-DSH-035 | [PRM-01 §16] | BC-15 · `sgspe-ui` (ECharts en las rutas de tableros) | — | — | Portal › todos los tableros (tabla «Ver datos») | `DashboardCharts.test.tsx` (propuesto) | F2 | Diseñado |
| RQ-DSH-036 | [USR-05] | BC-15 · `sgspe.dashboard` por fase (`dashboards.md` §11) | — | `GET /api/sgspe/dashboards/{dashboard}`; `GET /api/sgspe/dashboards/indicators` | Portal › tableros por sección | el `<Tablero>DashboardApiTest` (propuesto) de cada fase verifica que `GET /api/sgspe/dashboards/indicators` informa disponibles los KPI del módulo desde su fase (`dashboards.md` §11) | F2–F8 | Diseñado |
| RQ-DSH-037 | [DOC-01 §9.3.1, Tabla 9.5] [PRM-01 §16] | BC-15 · `sgspe.dashboard` (cierre de periodo); `model/model.json` (catálogo `indicator`) | `indicator`, `indicator_measurement` | `POST /api/sgspe/indicators/{id}/measurements`; `/api/objects/indicator/records` | `/admin` (catálogos); Portal › Dashboard: tablero de monitoreo | `IndicatorMeasurementApiTest` (propuesto) | F8 | Diseñado; Fuera del MVP |

### 3.4 Requisitos de seguridad (RQ-SEC)

Fuente de las filas: `requirements-analysis.md` §14.

| Requirement | Source | Module | Entity | API | UI | Test | Fase | Estado |
|---|---|---|---|---|---|---|---|---|
| RQ-SEC-001 | [USR-06] [WAS-ADR-0010] | BC-16 · wasichai core (JWT); proxy inverso (CSP); `sgspe-ui` (token `sgspe.token`) | usuarios de wasichai | `POST /api/auth/login`; `GET /api/auth/me` | login compartido del portal y `/admin` | `SecurityApiTest` | F1 | Implementado |
| RQ-SEC-002 | [WAS-ADR-0004] [WAS-ADR-0010] | BC-16 · wasichai core (tenant del token) | todas las tablas por organización | `GET /api/organizations/current`; `POST /api/organizations` | — | `TenantIsolationApiTest` | F1 | Implementado |
| RQ-SEC-003 | [PRM-01 §7] [USR-09] | BC-16 · `model/roles.json`; `apply_roles.py` | roles de wasichai | `/api/roles` | `/admin` (roles) | `test_apply_roles.py`; `RolesApiTest` | F1 | Implementado; Upstream (UPS-10) |
| RQ-SEC-004 | [PRM-01 §7] [WAS-ADR-0020] [WAS-ADR-0042] | BC-16 · `model/roles.json`; `model/model.json` (acciones declaradas) | objetos de `domain-model.md` §8.1.1 | `GET /api/auth/me/permissions`; `/api/objects/{object}/records` | `/admin` (permisos) | `test_apply_roles.py`; `RolesApiTest` | F1 | Implementado (F1: roles y permisos de `ADMIN` y `AUDITOR`); Diseñado (matriz completa y acciones declaradas, F2–F8) |
| RQ-SEC-005 | [PRM-01 §7] | BC-16 · `sgspe.*` y `sgspe-ui` (sin nombres de rol); `sgspe.notification` (destinatarios) (desde F3) | `project_assignment` (desde F2) | — | — | `ArchitectureTest`; `boundaries.test` | F1 | Implementado |
| RQ-SEC-006 | [PRM-01 §7] | BC-16 · `sgspe.scope` (`ProjectScopeGuard`) | `project_assignment` | `/api/objects/{object}/records`; `/api/sgspe/**` | — | `security.md` §14 «Alcance, opción A»; `security.md` §14 «Alcance, opción B» | F2 | Decisión pendiente (CON-041, GAP-025); Upstream (UPS-01) |
| RQ-SEC-007 | [PRM-01 §7] [WAS-DOC api/rest.md] | BC-16 · `model/roles.json` | roles `AUDITOR`, `ANALYST`, `READ_ONLY` | `GET /api/audit` | `/admin` (auditoría) | `RolesApiTest` | F1 | Implementado; Upstream (UPS-06) |
| RQ-SEC-008 | [PRM-01 §7] | BC-16 · `sgspe.scope` (`EditAnyGuard`); `model/roles.json` (`EDIT_ANY`) | `engagement`, `engagement_participant`, `field_visit`, `field_observation`, `evidence` (desde F3) | `/api/objects/engagement/records`; `/api/objects/engagement_participant/records` | Portal › Relacionamiento | `security.md` §14 «Edición de lo propio»; `EngagementWorkflowApiTest` | F2 | Diseñado |
| RQ-SEC-010 | [USR-07] [DOC-01 §5.1.8, §9.3.1.2, Tabla 9.5] | Transversal · `model/roles.json` (permisos de campo); `sgspe.privacy` | campos restringidos de `security.md` §7.9 | `/api/objects/{object}/records` | — | `security.md` §14 «Permisos de campo» | F2 | Decisión pendiente (GAP-001) |
| RQ-SEC-011 | [WAS-ADR-0006] | BC-02 · `sgspe.territory` (proxy WMS); `model/publish_layers.py` | capas públicas de `gis.md` §2.1 | `GET /api/sgspe/territory/wms`; `/api/gis/layers/**` | Portal › Territorio: mapa territorial | `security.md` §14 «Proxy WMS» | F6 | Diseñado; Fuera del MVP |
| RQ-SEC-014 | [WAS-ADR-0043] [DOC-01 §9.4.2] | BC-16 · cuentas de servicio de wasichai; `sgspe.warning` (`WarningWriteGuard`) | `early_warning` | `POST /api/auth/token`; `/api/service-accounts` | — | `security.md` §14 «Cuentas de servicio» | F4 | Diseñado |
| RQ-SEC-015 | [WAS-ADR-0010] | BC-16 · proxy inverso (límite de tasa) | — | `POST /api/auth/login`; `POST /api/auth/token` | — | `LoginRateLimitTest` (propuesto), prueba de despliegue (`security.md` §14) | F1 | Upstream (UPS-09) |
| RQ-SEC-016 | [WAS-ADR-0010] | BC-16 · `application-prod.yml` | — | — | — | `ProdProfileTest`; `ProdStartupTest`; `ProdGuardTest` | F1 | Implementado |
| RQ-SEC-017 | [PRM-01 §21] | BC-16 · pruebas de seguridad (`security.md` §14) | — | `/api/**` | — | `TenantIsolationApiTest`; `SecurityApiTest` | F1 | Implementado (F1: aislamiento y autenticación); Diseñado (permisos de campo, F2) |
| RQ-SEC-018 | [USR-07] | Transversal · documentos, seeds, fixtures y tests versionados | — | — | — | `security.md` §14 «Nombres reales» | F0–F8 | Diseñado |
| RQ-SEC-019 | [USR-01] | BC-15 · `sgspe.deliverable` (validación de plantillas) | `deliverable` | `POST /api/sgspe/deliverable-templates` | Portal › Reportes | `security.md` §14 «Plantillas Word»; `deliverables.md` §13.2, criterio 18 | F8 | Diseñado; Fuera del MVP |
| RQ-SEC-020 | [DOC-03] [DOC-01 Tabla 9.5] | BC-02 · `sgspe.territory` (carpeta temporal y límites) | — | `POST /api/sgspe/territory/imports` | — | `security.md` §14 «Importaciones» | F2 | Diseñado |

### 3.5 Requisitos de auditoría (RQ-AUD)

Fuente de las filas: `requirements-analysis.md` §15.

| Requirement | Source | Module | Entity | API | UI | Test | Fase | Estado |
|---|---|---|---|---|---|---|---|---|
| RQ-AUD-001 | [PRM-01 §5, §18] [WAS-ADR-0040] [WAS-ADR-0041] | BC-17 · `audit_log` de wasichai | `audit_log` | `GET /api/audit`; `GET /api/objects/{object}/records/{id}/history` | `/admin` (auditoría) | `AuditApiTest` | F1 | Implementado |
| RQ-AUD-002 | [PRM-01 §18] [WAS-ADR-0041] [WAS-DOC api/rest.md] | BC-17 · `model/model.json` (`requiresReason`); `sgspe.common` (`TransitionReasonGuard`); `sgspe-ui` (motivo) | `audit_log`; objetos `requiresReason` | `/api/objects/{object}/records` con `X-Change-Reason` | Portal › fichas con motivo de cambio | `AuditApiTest` | F2 | Upstream (UPS-08) |
| RQ-AUD-003 | [WAS-ADR-0040] [WAS-ADR-0044] | BC-17 · `model/model.json` (`appendOnly`) | `stakeholder_assessment`, `risk_assessment` (desde F4), `commitment_reschedule` (desde F3), `program_measurement` (desde F5), `indicator_measurement` (desde F8), `deliverable_version` (desde F8), `export_log`, `ai_interaction` (desde F7) | `/api/objects/{object}/records` | — | `AuditApiTest` | F2 | Diseñado |
| RQ-AUD-004 | [WAS-ADR-0040] | BC-17 · `model/model.json` (`apiOnly`); guardas `RecordWriteGuard` de cada paquete | objetos `apiOnly` de `domain-model.md` §6 | `/api/sgspe/**` | — | `security.md` §14 «Transiciones» | F2 | Diseñado |
| RQ-AUD-005 | [PRM-01 §18] [WAS-DOC api/rest.md] | BC-17 · `audit_log` de wasichai | `audit_log` | `GET /api/audit`; `GET /api/objects/{object}/records/{id}/history` | `/admin` (auditoría); Portal › fichas con historial (HISTORY) | `AuditApiTest`; `RolesApiTest` | F1 (`/admin`), F2 (fichas del portal) | Implementado en `/admin`; Diseñado (fichas, F2) |
| RQ-AUD-006 | [PRM-01 §5, §11] | BC-17 · `sgspe.observability` | — | `/api/**` con `X-Correlation-Id` | — | `ObservabilityApiTest` | F1 | Implementado; Upstream (UPS-03) |
| RQ-AUD-007 | [PRM-01 §5] [WAS-DOC api/rest.md] | BC-17 · convención de motivo (`sgspe.territory`, `sgspe.common`, automatizaciones) | `audit_log` | `/api/objects/{object}/records` | — | `workflows.md` §Pruebas «Automatizaciones»; `workflows.md` §Pruebas «Trabajos» | F2 | Upstream (UPS-03) |
| RQ-AUD-008 | [PRM-01 §18, §26] | BC-17 · `audit_log` de wasichai (sin cobertura de administración) | usuarios, roles y metadata de wasichai | `/api/users`; `/api/roles`; `/api/metadata/objects/**` | `/admin` | — | F1 | Implementado; Upstream (UPS-02) |
| RQ-AUD-009 | [PRM-01 §18] [WAS-DOC api/rest.md] | BC-17 · `audit_log` de wasichai | `audit_log` | `GET /api/audit` | `/admin` (auditoría) | — | F1 | Upstream (UPS-05) |
| RQ-AUD-010 | [PRM-01 §5] | BC-17 · `audit_log` de wasichai | `audit_log` | — | — | — | F1 | Upstream (UPS-12) |
| RQ-AUD-011 | [PRM-01 §18] [USR-07] | BC-17, BC-15 · `sgspe.privacy`; `sgspe.deliverable` (desde F8) | `export_log` | `POST /api/sgspe/exports/{kind}`; `POST /api/sgspe/deliverables/{id}/issue` (desde F8); `GET /api/sgspe/deliverables/{id}/draft/content` (desde F8); `GET /api/sgspe/deliverables/{id}/versions/{versionNumber}/content` (desde F8); `GET /api/sgspe/territory/wfs` (desde F6) | Portal › Reportes | `security.md` §14 «Salidas»; `security.md` §14 «Descargas»; `deliverables.md` §13.2, criterio 14 | F2 | Diseñado |
| RQ-AUD-012 | [ARQ-01] [PRM-01 §10] | BC-17, BC-18 · `sgspe.ai`; `sgspe.commitment` | `ai_suggestion`, `commitment_candidate`, `ai_interaction`; `audit_log` | `POST /api/sgspe/ai-suggestions/{id}/accept`; `POST /api/sgspe/commitment-candidates/{id}/accept` | Portal › bandeja de revisión | `AiSuggestionWorkflowApiTest`; `ai-architecture.md` §15 «De integración» | F7 | Diseñado; Fuera del MVP |
| RQ-AUD-013 | [WAS-DOC modules/gis.md] [DOC-03] | BC-17 · `sgspe.territory` (datos de origen de la importación) | `project_component`, `land_parcel`, `mining_concession`, `evidence` (desde F3) | `POST /api/sgspe/territory/imports/{importBatchId}/confirm` | — | `gis.md` §9 «Confirmación» | F2 | Diseñado |
| RQ-AUD-014 | [PRM-01 §18] | BC-17 · `audit_log` de wasichai | `audit_log` | `GET /api/audit` | — | `AuditApiTest` | F1 | Implementado |

### 3.6 Requisitos de IA (RQ-AI)

Fuente de las filas: `requirements-analysis.md` §16.

| Requirement | Source | Module | Entity | API | UI | Test | Fase | Estado |
|---|---|---|---|---|---|---|---|---|
| RQ-AI-001 | [PRM-01 §9, §26] [ARQ-01] [ARQ-04] [WAS-ADR-0014] [WAS-ADR-0015] | BC-18 · `sgspe.ai`; wasichai-agent (solo lectura) | `ai_suggestion`, `commitment_candidate`, `ai_interaction` | `POST /api/sgspe/ai/runs` | Portal › bandeja de revisión | `AiSuggestionWorkflowApiTest`; `ArchitectureTest` | F7 | Diseñado; Fuera del MVP |
| RQ-AI-003 | [PRM-01 §9, §26] [ARQ-02] | BC-18 · `sgspe.ai`; `model/workflows/ai_suggestion.json` (WF-K) | `ai_suggestion`, `commitment_candidate` | `POST /api/sgspe/ai-suggestions/{id}/accept`; `POST /api/sgspe/commitment-candidates/{id}/accept` | Portal › bandeja de revisión | `AiSuggestionWorkflowApiTest`; `CommitmentCandidateWorkflowApiTest` | F7 | Diseñado; Fuera del MVP |
| RQ-AI-004 | [PRM-01 §9] | BC-18 · `sgspe.ai` (seudonimización y re-identificación) | `ai_suggestion`, `commitment_candidate` | `POST /api/sgspe/ai/runs` | Portal › bandeja de revisión | `security.md` §14 «IA» | F7 | Diseñado; Fuera del MVP |
| RQ-AI-005 | [PRM-01 §10] [WAS-ADR-0015] | BC-18 · `sgspe.ai` (abstracción de wasichai-agent) | `ai_interaction` | `GET /api/sgspe/ai/status` | — | `ai-architecture.md` §15 «Contra un proveedor real» | F7 | Diseñado; Fuera del MVP |
| RQ-AI-006 | [PRM-01 §10] | BC-18 · `sgspe.ai` (prompts versionados) | `ai_interaction` | `POST /api/sgspe/ai/runs` | — | `ai-architecture.md` §15 «Unitarias» | F7 | Diseñado; Fuera del MVP |
| RQ-AI-007 | [PRM-01 §21] [WAS-ADR-0015] | BC-18 · `sgspe.ai` (FakeModel, StubModel, MockModel) | — | — | — | `ai-architecture.md` §15 «Unitarias»; `ai-architecture.md` §15 «Del agente, con StubModel» | F7 | Diseñado; Fuera del MVP |
| RQ-AI-008 | [PRM-01 §9, §20] [ARQ-03] | BC-18 · `sgspe.ai` (JOB-07 y recuperación) | tabla fija `knowledge_chunk`; `evidence` | `POST /api/sgspe/ai/knowledge/questions` | Portal › Documentos: preguntas sobre documentos (propuesto) | `ai-architecture.md` §15 «De seguridad» | F7 | Diseñado; Fuera del MVP |
| RQ-AI-009 | [PRM-01 §10] [USR-07] | BC-18 · `sgspe.ai` (seudonimización, activación por organización) | `ai_interaction`, `person` | `GET /api/sgspe/ai/status`; `POST /api/sgspe/ai/runs` | — | `security.md` §14 «IA»; `test_apply_roles.py` | F7 | Upstream (UPS-14); Fuera del MVP |
| RQ-AI-011 | [PRM-01 §26] | BC-18 · `sgspe.ai` (herramientas de solo lectura) | — | `POST /api/sgspe/ai/runs` | — | `ai-architecture.md` §15 «De inyección»; `security.md` §14 «IA» | F7 | Diseñado; Fuera del MVP |
| RQ-AI-012 | [PRM-01 §9] [USR-01] | BC-15 · `sgspe.deliverable` (`DeliverableWriteGuard`); `sgspe.ai` | `deliverable_section`, `deliverable_version`, `ai_suggestion` | `POST /api/sgspe/ai-suggestions/{id}/accept`; `/api/objects/deliverable_section/records` | Portal › Reportes: editor de secciones | `deliverables.md` §13.2, criterio 20; `DeliverableWorkflowApiTest` | F8 | Diseñado; Fuera del MVP |
| RQ-AI-013 | [PRM-01 §9] | BC-18 · `sgspe.ai` (herramientas de los agentes) | `stakeholder`, `commitment`, `project`, `evidence` | `POST /api/sgspe/ai/runs` | — | `ai-architecture.md` §15 «De seguridad» | F7 | Diseñado; Fuera del MVP |
| RQ-AI-014 | [PRM-01 §9] [WAS-ADR-0015] | BC-18 · `sgspe.ai` (flujos Embabel) | `commitment_candidate`, `ai_suggestion` | `POST /api/sgspe/ai/runs` | Portal › bandeja de revisión | `ai-architecture.md` §15 «Del agente, con StubModel» | F7 | Diseñado; Fuera del MVP |
| RQ-AI-015 | [PRM-01 §9] | BC-18 · `sgspe.ai`; `model/workflows/ai_suggestion.json` (WF-K) | `ai_suggestion`, `grievance`, `organization` | `POST /api/sgspe/ai-suggestions/{id}/accept` | Portal › bandeja de revisión | `AiSuggestionWorkflowApiTest` | F7 | Diseñado; Fuera del MVP |

## 4. Requisitos no funcionales (RNF)

Las 20 RNF de `requirements-analysis.md` §7. Varias son de proceso o de repositorio y no tienen entidad, API ni prueba (§7.1).

| Requirement | Source | Module | Entity | API | UI | Test | Fase | Estado |
|---|---|---|---|---|---|---|---|---|
| RNF-MNT-001 | [USR-03] [WAS-ADR-0024] | Transversal · app Spring Boot con `@WasichaiApplication` y los starters de `architecture.md` §5.1 | — | API genérica de wasichai (`api.md` §2) | `/admin` (`WasichaiApp`); portal con `@wasichai/ui` | `architecture.md` §13 «Contrato con la plataforma»; `ErrorContractApiTest` | F1 | Implementado |
| RNF-MNT-002 | [USR-03] [WAS-ADR-0003] | Transversal · `model/model.json`, `model/roles.json`, `model/workflows/*.json`, `apply.py`, `apply_roles.py` | los 89 objetos de `domain-model.md` §6 | `/api/objects`; `/api/metadata/objects/**`; `/api/roles` | `/admin` (solo lectura del modelo en producción) | `test_apply.py` | F1 | Implementado (F1: `apply.py` y `apply_roles.py`); Diseñado (`test_model.py`, con los objetos de F2, ADR-0007) |
| RNF-MNT-003 | [PRM-01 §24, §25] | Transversal · todos los paquetes y `model/` | — | — | — | Todas las suites de `architecture.md` §13 | F1–F8 | Diseñado |
| RNF-MNT-004 | [USR-09] | Transversal · `model/model.json`, `model/roles.json`, paquetes `sgspe.*`, rutas `/api/sgspe/**` | los 89 objetos de `domain-model.md` §6 | `/api/sgspe/**` | etiquetas en español en el portal y en `/admin` | `test_model.py`; `test_apply_roles.py` | F0 | Decisión pendiente (CON-050) |
| RNF-MNT-005 | [USR-03] [WAS-ADR-0003] [WAS-ADR-0024] [WAS-ADR-0025] [PRM-01 §19, §21] | Transversal · paquetes `sgspe.*` (`architecture.md` §5.2 y §5.3) | — | `/api/sgspe/**` | — | `ArchitectureTest`; `boundaries.test` | F1 | Implementado |
| RNF-MNT-006 | [USR-08] | Transversal · repositorios `wasichai/sgspe` y `wasichai/sgspe-ui` | — | — | — | — | F0 | Diseñado |
| RNF-MNT-007 | [USR-04] [USR-05] [PRM-01 §24] | Transversal · proceso por fases (`roadmap.md`) | — | — | — | — | F0 | Diseñado |
| RNF-MNT-008 | [PRM-01 §22, §26] | Transversal · `docs/adr/` | — | — | — | — | F0 | Diseñado |
| RNF-MNT-009 | [PRM-01 §0, §23, §27] | Transversal · `docs/requirements-traceability.md` | — | — | — | `tools/check_traceability.py` en la CI (`roadmap.md`, definición de terminado de F1) | F0–F8 | Diseñado |
| RNF-PRIV-004 | [USR-07] [DOC-01 §9.3.1.2, Tabla 9.5] [DOC-02 p.4] | Transversal · `model/model.json` (consentimientos); `sgspe.privacy` (salidas EXTERNO) | `contact_method`, `evidence` | `/api/objects/contact_method/records`; `PATCH /api/sgspe/evidence/{id}` | Portal › Documentos; Grupos de interés | `security.md` §14 «Salidas»; `deliverables.md` §13.2, criterio 11 | F3 | Diseñado |
| RNF-INT-001 | [PRM-01 §11] [WAS-DOC api/rest.md] | Transversal · wasichai core; `sgspe.common` | — | `/api/**` | — | `ErrorContractApiTest` | F1 | Implementado |
| RNF-INT-002 | [PRM-01 §11] | Transversal · `sgspe.common` (`Idempotency-Key`) | tabla fija `idempotency_key` | `/api/sgspe/**` con el encabezado `Idempotency-Key` (`api.md` §3.5; marca `IK` en las tablas de `api.md` §4) | — | `IdempotencyApiTest` (propuesto) | F3 | Upstream (UPS-18) |
| RNF-INT-003 | [PRM-01 §11, §20] | Transversal · `sgspe.common` (precondición sobre `updated_at`) | — | `/api/sgspe/**` y API genérica con `If-Match` (`api.md` §3.6) | portal (escrituras con `If-Match`) | `ConcurrencyApiTest` (propuesto) | F2 | Upstream (UPS-04) |
| RNF-INT-004 | [USR-09] [PRM-01 §11] | Transversal · `sgspe.common` (filtros estrictos con 422) | — | `/api/sgspe/**` | — | `StrictQueryParamsApiTest` (propuesto) | F2 | Diseñado |
| RNF-INT-005 | [WAS-ADR-0007] [WAS-ADR-0019] | BC-02 · wasichai-gis | campos GEOMETRY de BC-01, BC-02, BC-19 y de gestión | `GET /api/gis/objects/{object}/features`; `/api/objects/{object}/records` | Portal › Territorio: mapa del proyecto | `gis.md` §9 «Ida y vuelta UTM ↔ 4326» | F2 | Diseñado |
| RNF-I18N-001 | [PRM-01 §3, §13] [USR-09] | Transversal · `sgspe-ui` (i18next) | — | — | portal y `/admin` en español e inglés | `I18n.test.tsx`; `architecture.md` §13 «Frontend» | F1 | Implementado |
| RNF-I18N-002 | [DOC-02 p.4] | Transversal · `sgspe.common` (America/Lima, feriados, valores por defecto) | `project` (`currency` por defecto) | — | — | `QcsDueDatesApiTest` (propuesto); `architecture.md` §13 «Unitarias» | F2 | Upstream (UPS-15) |
| RNF-TST-001 | [PRM-01 §21] [WAS-ADR-0015] | Transversal · todas las suites (`architecture.md` §13) | — | — | — | `SgspeApiTest`; `test_model.py`; `ArchitectureTest`; Playwright | F1–F8 | Diseñado |
| RNF-OBS-001 | [PRM-01 §24, §25] | Transversal · `sgspe.observability` | — | `/actuator/health/liveness`; `/actuator/health/readiness` | — | `ObservabilityApiTest` | F1 | Implementado |
| RNF-USA-001 | [PRM-01 §14] [USR-09] | Transversal · `sgspe-ui` (navegación del portal) | — | `GET /api/auth/me/permissions` | Portal › navegación principal con las 13 secciones | `Navigation.test.tsx` | F1 | Implementado |

## 5. Reglas de negocio (RN)

Las 31 RN de `requirements-analysis.md` §8. Su fase se deriva como dice §1.1.

| Requirement | Source | Module | Entity | API | UI | Test | Fase | Estado |
|---|---|---|---|---|---|---|---|---|
| RN-PRJ-001 | [DOC-01 §1] [PRM-01 §5] | BC-01 · `sgspe.common` (`FieldChangeReasonGuard`); `model/workflows/project.json` (WF-A) | `project` | `/api/objects/project/records` | Portal › Proyectos: ficha de `project` | `ProjectWorkflowApiTest` | F2 | Diseñado |
| RN-TER-001 | [DOC-01 §4.1, §4.2] | BC-02 · `sgspe.common` (`ExactlyOneGuard`); `model/model.json` (únicos compuestos) | `area_of_influence_member` | `/api/objects/area_of_influence_member/records` | Portal › Territorio: ficha de `area_of_influence` | `ExactlyOneGuardTest` (propuesto) | F2 | Diseñado |
| RN-TER-003 | [DOC-01 §4.1] | BC-02 · `sgspe.territory` (`TerritoryWriteGuard`); `model/workflows/area_of_influence.json` (WF-O) | `area_of_influence` | `POST /api/sgspe/areas-of-influence/{id}/approve` | Portal › Territorio: ficha de `area_of_influence` | `AreaOfInfluenceWorkflowApiTest` | F2 | Diseñado |
| RN-TER-004 | [DOC-01 §4] | BC-02 · `sgspe.territory` (sugerencias de miembros) | `area_of_influence_member` | `GET /api/sgspe/areas-of-influence/{id}/member-suggestions` (desde F6) | Portal › Territorio: ficha de `area_of_influence` | `gis.md` §9 «Sugerencia de miembros» | F2, F6 | Diseñado |
| RN-STK-006 | [DOC-01 §6, Tabla 7.1] | BC-03 · `sgspe.common` (`ExactlyOneGuard`); `model/model.json` (únicos) | `stakeholder` | `/api/objects/stakeholder/records` | Portal › Grupos de interés: ficha de `stakeholder` | `test_model.py`; `ExactlyOneGuardTest` (propuesto) | F2 | Diseñado |
| RN-STK-007 | [DOC-01 Tabla 9.5] | BC-03 · `sgspe.dashboard` (KPI-STK-05, KPI-STK-06) | `stakeholder_assessment` | `GET /api/sgspe/dashboards/indicators/{code}` | Portal › Grupos de interés: tablero de grupos de interés (KPI-STK-06); Dashboard: tablero de monitoreo (desde F8) (KPI-STK-05) | `StakeholdersDashboardApiTest` (propuesto); `MonitoringDashboardApiTest` (propuesto) | F2 | Diseñado |
| RN-STK-008 | [DOC-01 §6.1.3, Tabla 6.1] [PRM-01 §5] | BC-03 · `model/model.json` (el actor solo es `person` u `organization`) | `stakeholder`, `organization`, `community` | `/api/objects/stakeholder/records` | Portal › Grupos de interés: ficha de `stakeholder` | `test_model.py` | F2 | Diseñado |
| RN-ENG-001 | [DOC-01 §9.1.1.2] | BC-04 · `sgspe.commitment` (`CommitmentWriteGuard`); `model/model.json` (`policy_workshop_commitments_blocked`) | `commitment`, `engagement` | `/api/objects/commitment/records`; `POST /api/sgspe/transition-checks` | Portal › Compromisos: ficha de `commitment` | `CommitmentWriteGuardTest` (propuesto) | F3 | Diseñado |
| RN-ENG-002 | [DOC-01 §9.1.1.3] | BC-04 · `model/workflows/commitment.json` (WF-D) (`approve_senior`); `sgspe.commitment` (`CommitmentWriteGuard`) | `commitment` | `/api/objects/commitment/records/{id}/transitions/{name}` | Portal › Compromisos: ficha de `commitment` | `CommitmentWorkflowApiTest` | F3 | Diseñado |
| RN-ENG-003 | [DOC-01 Tabla 9.5] | BC-04 · `model/model.json` (`meetings_per_month_target`); `sgspe.dashboard` (KPI-ENG-02) | `project`, `engagement_plan` | `GET /api/sgspe/dashboards/engagement` | Portal › Relacionamiento: tablero de relacionamiento | `EngagementDashboardApiTest` (propuesto) | F2 | Diseñado |
| RN-ENG-004 | [DOC-01 §9.1.1] [PRM-01 §8] | BC-04 · `sgspe.engagement` (`EngagementWriteGuard`); `model/model.json` (`policy_minutes_only_formal_types`) | `engagement` | `/api/objects/engagement/records/{id}/transitions/{name}` | Portal › Relacionamiento: ficha de `engagement` | `EngagementWorkflowApiTest` | F2 | Diseñado |
| RN-ENG-005 | [DOC-01 §9.1.1.2] | BC-04 · `sgspe.engagement` (`EngagementWriteGuard`) | `engagement`, `engagement_question` | `/api/objects/engagement/records/{id}/transitions/{name}`; `POST /api/sgspe/transition-checks` | Portal › Relacionamiento: ficha de `engagement` | `EngagementWorkflowApiTest` | F2 | Diseñado |
| RN-COM-001 | [DOC-01 §9.3.1.2] | BC-05 · `sgspe.commitment` (`CommitmentWriteGuard` en `register_fulfillment`) | `commitment`, `delivery`, `evidence` | `/api/objects/commitment/records/{id}/transitions/{name}` | Portal › Compromisos: ficha de `commitment` | `CommitmentWorkflowApiTest` | F3 | Diseñado |
| RN-COM-002 | [DOC-01 Tabla 9.2, §9.3.1.2] | BC-05 · `sgspe.commitment` (`CommitmentWriteGuard`); `model/model.json` (`policy_local_jobs_direct_area_only`, `local_jobs_min`, `local_jobs_max`) | `commitment`, `project` | `/api/objects/commitment/records/{id}/transitions/{name}` | Portal › Compromisos: ficha de `commitment` | `CommitmentWriteGuardTest` (propuesto) | F3 | Diseñado |
| RN-COM-003 | [DOC-01 §9.3.1.2] | BC-05 · `sgspe.commitment` (`CommitmentWriteGuard`); `model/model.json` (`policy_no_jobs_in_easements`) | `commitment`, `project` | `/api/objects/commitment/records/{id}/transitions/{name}` | Portal › Compromisos: ficha de `commitment` | `CommitmentWriteGuardTest` (propuesto) | F3 | Diseñado |
| RN-COM-004 | [DOC-01 §9.3.1.2] | BC-05 · `model/model.json` (`amount` requerido) | `commitment` | `/api/objects/commitment/records` | Portal › Compromisos: ficha de `commitment` | `test_model.py` | F3 | Diseñado |
| RN-COM-005 | [PRM-01 §5, §8] | BC-05 · `sgspe.commitment` (`CommitmentWriteGuard`); `model/workflows/commitment.json` (WF-D) | `commitment` | `/api/objects/commitment/records/{id}/transitions/{name}` | Portal › Compromisos: ficha de `commitment` | `CommitmentWorkflowApiTest`; `security.md` §14 «Transiciones» | F3 | Diseñado |
| RN-INV-001 | [DOC-01 Tabla 9.2, §9.3.1.2] | BC-10 · `sgspe.investment` (`SupportPolicyGuard`); `model/model.json` (`policy_no_paid_labor`) | `commitment`, `social_contribution` (desde F5), `social_initiative` (desde F5) | `/api/objects/commitment/records/{id}/transitions/{name}`; `/api/objects/social_contribution/records/{id}/transitions/{name}` (desde F5) | Portal › Compromisos; Programas sociales (desde F5) | `SupportPolicyGuardTest` (propuesto) | F3, F5 | Diseñado |
| RN-INV-002 | [DOC-01 §9.3.1.2] | BC-10 · `sgspe.investment` (`SupportPolicyGuard`); `model/model.json` (`policy_non_monetary_donations`) | `social_contribution`, `festivity` | `/api/objects/social_contribution/records/{id}/transitions/{name}` | Portal › Programas sociales: ficha de `social_contribution` | `SupportPolicyGuardTest` (propuesto) | F5 | Diseñado; Fuera del MVP |
| RN-INV-003 | [DOC-01 §9.3.1.2] | BC-10 · `sgspe.investment` (`SupportPolicyGuard`); `model/roles.json` (`APPROVE_OVER_CAP`) | `exploration_campaign`, `commitment`, `social_contribution` (desde F5) | `/api/objects/commitment/records/{id}/transitions/{name}`; `/api/objects/social_contribution/records/{id}/transitions/{name}` (desde F5); `POST /api/sgspe/transition-checks` | Portal › Compromisos; Programas sociales (desde F5) | `CommitmentWorkflowApiTest`; `SocialContributionWorkflowApiTest` | F3, F5 | Diseñado |
| RN-INV-004 | [DOC-01 §9.3.1.2] | BC-10 · `sgspe.investment` (`InvestmentWriteGuard`); `model/model.json` (`policy_synergy_requires_agreement`) | `social_program`, `social_contribution`, `agreement` | `/api/objects/social_program/records/{id}/transitions/{name}`; `/api/objects/social_contribution/records/{id}/transitions/{name}` | Portal › Programas sociales | `SocialProgramWorkflowApiTest`; `SocialContributionWorkflowApiTest` | F5 | Diseñado; Fuera del MVP |
| RN-INV-005 | [DOC-01 Tabla 9.2, §9.3.1.2] | BC-10 · `sgspe.investment` (`SupportPolicyGuard`); `model/model.json` (`policy_priority_warning`) | `social_contribution` | `/api/objects/social_contribution/records/{id}/transitions/{name}`; `POST /api/sgspe/transition-checks` | Portal › Programas sociales: ficha de `social_contribution` | `SupportPolicyGuardTest` (propuesto) | F5 | Diseñado; Fuera del MVP |
| RN-INV-006 | [DOC-02 p.3–p.4, p.6] [DOC-01 §9.3.1.2] | BC-10 · `model/model.json` (`declared_purpose`, `conditions`, `payer_organization` requeridos); `sgspe.investment` (`SupportPolicyGuard`) | `social_contribution`, `receipt` | `/api/objects/social_contribution/records`; `POST /api/sgspe/transition-checks` | Portal › Programas sociales: ficha de `social_contribution` | `SupportPolicyGuardTest` (propuesto) | F5 | Diseñado; Fuera del MVP |
| RN-GRV-001 | [DOC-01 §9.3.1.2] | BC-07 · `sgspe.attention` (`RequestClosureListener`); `model/workflows/stakeholder_request.json` (WF-E3) (`close_unfulfilled`) | `stakeholder_request`, `commitment`, `social_contribution` (desde F5) | `/api/objects/commitment/records/{id}/transitions/{name}`; `/api/objects/stakeholder_request/records/{id}/transitions/{name}` | Portal › Quejas, consultas y solicitudes: ficha de `stakeholder_request` | `StakeholderRequestWorkflowApiTest`; `CommitmentWorkflowApiTest` | F4 | Diseñado |
| RN-GRV-002 | [PRM-01 §5] | BC-07 · `sgspe.attention` (`AttentionWriteGuard`); `model/workflows/grievance.json` (WF-E1) (`resolve_high_severity`) | `grievance` | `/api/objects/grievance/records/{id}/transitions/{name}` | Portal › Quejas, consultas y solicitudes: ficha de `grievance` | `GrievanceWorkflowApiTest` | F4 | Diseñado |
| RN-RSK-001 | [DOC-01 §9.3.2.1] | BC-08 · `model/model.json` (`risk_review_days`); `sgspe.deadline` (`DeadlineJob`, JOB-01) | `social_risk`, `task` | `/api/objects/social_risk/records` | Portal › Riesgos; Tareas | `workflows.md` §Pruebas «Trabajos» | F4 | Diseñado |
| RN-RSK-002 | [DOC-01 §9.3.2.1] | BC-08 · `sgspe.risk` (endpoint de revisiones); `model/model.json` (`risk_medium_min_score`, `risk_high_min_score`, `risk_critical_min_score`) | `social_risk`, `risk_assessment` | `POST /api/sgspe/social-risks/{id}/assessments` | Portal › Riesgos: ficha de `social_risk` | `SocialRiskAssessmentApiTest` (propuesto); `architecture.md` §13 «Unitarias» | F4 | Decisión pendiente (GAP-004) |
| RN-RSK-003 | [DOC-01 §9.3.2.2] | BC-08 · `sgspe.risk` (`RiskWriteGuard`); `model/workflows/social_risk.json` (WF-F) | `social_risk`, `mitigation_measure` | `/api/objects/social_risk/records/{id}/transitions/{name}` | Portal › Riesgos: ficha de `social_risk` | `SocialRiskWorkflowApiTest` | F4 | Diseñado |
| RN-EWS-001 | [PRM-01 §5] | BC-09 · `sgspe.warning` (`WarningWriteGuard`); `model/roles.json` (`CONFIRM_ALERT`) | `early_warning` | `/api/objects/early_warning/records/{id}/transitions/{name}` | Portal › Riesgos: ficha de `early_warning` | `EarlyWarningWorkflowApiTest`; `security.md` §14 «Cuentas de servicio» | F4 | Diseñado |
| RN-WFL-001 | [PRM-01 §8] [WAS-ADR-0013] [WAS-ADR-0039] | BC-13 · `sgspe.deadline` (`DeadlineJob`, JOB-01); `model/workflows/*.json` (sin estados de plazo) | `commitment`, `grievance` (desde F4), `inquiry` (desde F4), `stakeholder_request` (desde F4), `task`, `agreement`, `social_risk` (desde F4) | `/api/objects/commitment/records` | Portal › Compromisos; Tareas | `test_model.py`; `workflows.md` §Pruebas «Trabajos» | F3, F4 | Upstream (UPS-17) |
| RN-NTF-001 | [PRM-01 §12, §17] | BC-14 · `sgspe.notification` (`NotificationListener`); `sgspe.deadline` (JOB-01) | tablas fijas `notification` y `notification_delivery` | `GET /api/sgspe/notifications` | Portal › Tareas: bandeja de avisos | `workflows.md` §Pruebas «Listeners» | F3, F4 | Diseñado |

## 6. Trazabilidad inversa por fuente

Se calcula desde la columna Source de las filas de §2 a §5: cada requisito aparece bajo cada parte de una fuente que su celda
cita. Por eso un requisito puede aparecer en varias filas.

### 6.1 DOC-01 por capítulo

Diagnóstico social del Proyecto Eposuyay [DOC-01]. Las tablas y los gráficos cuentan en el capítulo de su número; los anexos
forman el capítulo 10. Las notas al pie del DOCX van aparte, porque su número no dice a qué capítulo pertenecen.

| Capítulo | Partes citadas | Requisitos |
|---|---|---|
| Cap. 1. Introducción | §1 | RF-PRJ-001, RF-PRJ-002, RF-PRJ-004, RF-TER-001, RN-PRJ-001 |
| Cap. 2. Objetivos | §2 | RF-LBS-001 |
| Cap. 3. Metodología | §3, §3.1, §3.2, §3.3, Tabla 3.1 | RF-ENG-001, RF-FLD-001, RF-LBS-001, RF-PRJ-005, RF-TER-005, RQ-REP-012 |
| Cap. 4. Definición de áreas de influencia social | §4, §4.1, §4.2 | RF-AGR-002, RF-TER-001, RF-TER-002, RF-TER-003, RF-TER-005, RF-TER-006, RF-TER-008, RQ-DSH-029, RQ-GIS-002, RN-TER-001, RN-TER-003, RN-TER-004 |
| Cap. 5. Contexto socioeconómico | §5, §5.1.1, §5.1.5, §5.1.6, §5.1.8, §5.1.9, §5.2.1, §5.2.4, §5.2.5, Tablas 5.1–5.3, Tabla 5.4, Tabla 5.5, Tabla 5.6, Tabla 5.7, Tabla 5.8, Tabla 5.9, Tabla 5.10 | RF-LBS-002, RF-LBS-003, RF-LBS-004, RF-LBS-005, RF-LBS-006, RF-STK-004, RF-TER-002, RQ-REP-006, RQ-SEC-010 |
| Cap. 6. Identificación de grupos de interés | §6, §6.1.1.7, §6.1.2.7, §6.1.3, §6.1.3.1, §6.1–§6.3, §6.2.2.2, §6.2.4.1, §6.2.4.2, §6.2.5, Tablas 6.1–7.3 | RF-AGR-003, RF-CAT-001, RF-STK-001, RF-STK-002, RF-STK-003, RF-STK-004, RF-STK-005, RF-STK-012, RF-STK-013, RF-TER-003, RQ-REP-006, RQ-REP-012, RN-STK-006, RN-STK-008 |
| Cap. 7. Caracterización de los grupos de interés | §7.1, §7.1.2, §7.2, §7.2.1.1, §7.2.1.2, §7.2.1.3, Tablas 6.1–7.3 | RF-CAT-001, RF-DSH-007, RF-LBS-007, RF-STK-001, RF-STK-005, RF-STK-009, RF-TER-008, RQ-DSH-004, RQ-REP-006, RQ-REP-009, RQ-REP-012, RQ-REP-014, RQ-REP-031, RQ-REP-033, RN-STK-006 |
| Cap. 8. Definición de temas clave | §8, Tabla 8.1 | RF-EWS-001, RF-LBS-008, RF-TER-007, RF-TER-008 |
| Cap. 9. Estrategias sociales | §9, §9.1.1, §9.1.1.1, §9.1.1.2, §9.1.1.3, §9.1.1.4, §9.1.2, §9.2, §9.3, §9.3.1, §9.3.1.1, §9.3.1.2, §9.3.2, §9.3.2.1, §9.3.2.2, §9.4.1, §9.4.2, Tabla 9.1, Tabla 9.2, Tabla 9.3, Tabla 9.4, Tabla 9.5 | RF-AGR-001, RF-AGR-002, RF-CAT-001, RF-COM-001, RF-COM-002, RF-COM-005, RF-COM-006, RF-COM-007, RF-COM-008, RF-COM-009, RF-DOC-001, RF-DSH-004, RF-DSH-005, RF-DSH-007, RF-DSH-008, RF-DSH-009, RF-DSH-010, RF-ENG-001, RF-ENG-002, RF-ENG-003, RF-ENG-004, RF-ENG-005, RF-ENG-007, RF-ENG-008, RF-ENG-009, RF-ENG-010, RF-ENG-011, RF-ENG-012, RF-EWS-001, RF-EWS-002, RF-EWS-003, RF-GRV-001, RF-GRV-003, RF-GRV-004, RF-GRV-005, RF-GRV-007, RF-INV-001, RF-INV-002, RF-INV-003, RF-INV-004, RF-LBS-004, RF-LBS-009, RF-PRJ-002, RF-PRJ-004, RF-PRJ-005, RF-RSK-001, RF-RSK-002, RF-RSK-003, RF-RSK-004, RF-RSK-005, RF-STK-005, RF-STK-009, RF-STK-010, RF-STK-011, RF-TER-008, RF-WFL-003, RQ-DSH-006, RQ-DSH-007, RQ-DSH-008, RQ-DSH-009, RQ-DSH-010, RQ-DSH-011, RQ-DSH-012, RQ-DSH-013, RQ-DSH-014, RQ-DSH-015, RQ-DSH-016, RQ-DSH-017, RQ-DSH-019, RQ-DSH-021, RQ-DSH-023, RQ-DSH-026, RQ-DSH-027, RQ-DSH-030, RQ-DSH-037, RQ-GIS-002, RQ-REP-002, RQ-REP-009, RQ-REP-012, RQ-REP-022, RQ-REP-029, RQ-REP-031, RQ-SEC-010, RQ-SEC-014, RQ-SEC-020, RNF-PRIV-004, RN-COM-001, RN-COM-002, RN-COM-003, RN-COM-004, RN-ENG-001, RN-ENG-002, RN-ENG-003, RN-ENG-004, RN-ENG-005, RN-GRV-001, RN-INV-001, RN-INV-002, RN-INV-003, RN-INV-004, RN-INV-005, RN-INV-006, RN-RSK-001, RN-RSK-002, RN-RSK-003, RN-STK-007 |
| Cap. 10. Anexos (incluye el Gráfico 10.2 y la Tabla 10.1) | Tabla 10.1, Anexo 1, Anexos 1–3, Anexo 3, Gráfico 10.2, Gráfico 10.3 | RF-DSH-007, RF-LBS-001, RF-LBS-009, RF-PRJ-006, RF-STK-005, RF-STK-006, RF-STK-007, RF-STK-008, RF-STK-009, RF-TER-007, RQ-DSH-005, RQ-GIS-001, RQ-GIS-004, RQ-GIS-019, RQ-REP-007, RQ-REP-008, RQ-REP-009, RQ-REP-023, RQ-REP-030, RQ-REP-031 |
| Notas al pie | notas 1–2, nota 12, notas 13–19, notas 20–21, notas 22–23, nota 24, nota 25, nota 26 | RF-EWS-002, RF-LBS-002, RF-LBS-007, RF-STK-002, RF-STK-003, RF-STK-006, RF-STK-007, RF-STK-008, RF-TER-008 |
| Portada e índice | índice, portada | RF-PRJ-002, RQ-REP-003, RQ-REP-004, RQ-REP-019 |
| Todo el documento (sin sección) | DOC-01 (todo el documento) | RF-REP-001, RQ-REP-001, RQ-REP-005, RQ-REP-010, RQ-REP-011, RQ-REP-016, RQ-REP-020, RQ-REP-021, RQ-REP-024, RQ-REP-032 |

### 6.2 DOC-02 por página

Informe de trabajo de campo y avance del prospecto Blanca [DOC-02]; un rango de páginas cuenta en cada página.

| Página | Requisitos |
|---|---|
| p.1 | RF-PRJ-001, RQ-REP-003, RQ-REP-008, RQ-REP-020, RQ-REP-024 |
| p.2 | RF-ENG-001, RF-ENG-006, RF-ENG-007, RF-EWS-001, RF-FLD-001, RF-PRJ-001, RF-STK-014, RQ-REP-011, RQ-REP-013, RQ-REP-024 |
| p.3 | RF-ENG-001, RF-FLD-001, RF-INV-001, RF-STK-001, RF-STK-002, RF-STK-012, RF-TER-002, RQ-REP-024, RN-INV-006 |
| p.4 | RF-DOC-001, RF-INV-001, RF-PRJ-002, RF-PRJ-006, RF-TER-003, RF-TER-004, RQ-GIS-002, RQ-GIS-009, RQ-REP-010, RQ-REP-024, RQ-REP-025, RQ-REP-029, RNF-I18N-002, RNF-PRIV-004, RN-INV-006 |
| p.5 | RF-STK-012, RF-TER-001, RF-TER-002, RF-TER-004, RQ-GIS-001, RQ-REP-007, RQ-REP-008, RQ-REP-011, RQ-REP-019, RQ-REP-024, RQ-REP-030 |
| p.6 | RF-AGR-001, RF-AGR-002, RF-PRJ-002, RF-RSK-005, RQ-REP-011, RQ-REP-024, RN-INV-006 |
| Todo el documento (sin página) | RF-REP-002, RQ-REP-001, RQ-REP-002, RQ-REP-032 |

### 6.3 DOC-03

| Fuente | Requisitos |
|---|---|
| DOC-03 (captura de ArcMap) | RF-PRJ-006, RF-TER-005, RF-TER-006, RF-TER-007, RQ-AUD-013, RQ-DSH-029, RQ-GIS-001, RQ-GIS-002, RQ-GIS-003, RQ-GIS-004, RQ-GIS-006, RQ-GIS-007, RQ-GIS-008, RQ-GIS-009, RQ-GIS-010, RQ-GIS-018, RQ-REP-008, RQ-SEC-020 |

### 6.4 PRM-01 por sección

| Sección del prompt | Requisitos |
|---|---|
| §0 Regla fundamental | RNF-MNT-009 |
| §3 Arquitectura | RNF-I18N-001 |
| §5 Modelo de dominio | RF-AGR-001, RF-COM-002, RF-COM-004, RF-COM-005, RF-COM-006, RF-DOC-001, RF-DOC-003, RF-ENG-001, RF-ENG-005, RF-ENG-012, RF-EWS-004, RF-FLD-001, RF-GRV-001, RF-GRV-002, RF-GRV-006, RF-INV-003, RF-LBS-009, RF-PRJ-001, RF-PRJ-002, RF-RSK-001, RF-STK-001, RF-TER-001, RF-WFL-001, RF-WFL-002, RF-WFL-004, RQ-AUD-001, RQ-AUD-006, RQ-AUD-007, RQ-AUD-010, RN-COM-005, RN-EWS-001, RN-GRV-002, RN-PRJ-001, RN-STK-008 |
| §6 GIS / PostGIS | RQ-GIS-002, RQ-GIS-004, RQ-GIS-016, RQ-GIS-020, RQ-GIS-022 |
| §7 Roles y seguridad | RF-PRJ-005, RQ-SEC-003, RQ-SEC-004, RQ-SEC-005, RQ-SEC-006, RQ-SEC-007, RQ-SEC-008 |
| §8 Workflows | RF-COM-002, RF-COM-003, RF-COM-004, RF-ENG-005, RF-GRV-002, RF-PRJ-003, RF-STK-014, RF-WFL-002, RF-WFL-004, RQ-REP-015, RN-COM-005, RN-ENG-004, RN-WFL-001 |
| §9 AI + Spring AI + Embabel | RF-AIK-001, RF-AIK-002, RF-AIK-003, RF-AIK-004, RF-AIK-005, RF-AIK-006, RF-AIK-007, RF-AIK-008, RF-COM-010, RQ-AI-001, RQ-AI-003, RQ-AI-004, RQ-AI-008, RQ-AI-012, RQ-AI-013, RQ-AI-014, RQ-AI-015, RQ-REP-011 |
| §10 AI gateway | RF-AIK-009, RQ-AI-005, RQ-AI-006, RQ-AI-009, RQ-AUD-012 |
| §11 API | RQ-AUD-006, RNF-INT-001, RNF-INT-002, RNF-INT-003, RNF-INT-004 |
| §12 Eventos de dominio | RN-NTF-001 |
| §13 Frontend | RNF-I18N-001 |
| §14 UX | RF-IAM-001, RF-IAM-002, RQ-REP-028, RNF-USA-001 |
| §15 Mapa | RF-DSH-006, RQ-GIS-001, RQ-GIS-005, RQ-GIS-013, RQ-GIS-014 |
| §16 Dashboards | RF-DSH-001, RF-DSH-002, RF-DSH-003, RF-DSH-004, RF-DSH-005, RF-DSH-006, RF-DSH-009, RF-DSH-010, RQ-DSH-001, RQ-DSH-002, RQ-DSH-003, RQ-DSH-018, RQ-DSH-019, RQ-DSH-020, RQ-DSH-022, RQ-DSH-024, RQ-DSH-025, RQ-DSH-026, RQ-DSH-027, RQ-DSH-030, RQ-DSH-035, RQ-DSH-037, RQ-REP-009 |
| §17 Notificaciones | RF-GRV-006, RF-NTF-001, RF-WFL-004, RQ-DSH-025, RN-NTF-001 |
| §18 Auditabilidad | RQ-AUD-001, RQ-AUD-002, RQ-AUD-005, RQ-AUD-008, RQ-AUD-009, RQ-AUD-011, RQ-AUD-014, RQ-REP-017, RQ-REP-026 |
| §19 Calidad de código | RNF-MNT-005 |
| §20 Base de datos | RQ-AI-008, RQ-DSH-031, RQ-GIS-016, RNF-INT-003 |
| §21 Testing | RQ-AI-007, RQ-GIS-021, RQ-SEC-017, RNF-MNT-005, RNF-TST-001 |
| §22 Documentación | RNF-MNT-008 |
| §23 Trazabilidad | RNF-MNT-009 |
| §24 Roadmap de implementación | RF-INV-003, RNF-MNT-003, RNF-MNT-007, RNF-OBS-001 |
| §25 Definition of done | RNF-MNT-003, RNF-OBS-001 |
| §26 Reglas para Claude Code | RQ-AI-001, RQ-AI-003, RQ-AI-011, RQ-AUD-008, RNF-MNT-008 |
| §27 Orden exacto de ejecución | RNF-MNT-009 |
| §29 Resultado esperado | RF-DSH-010, RQ-DSH-030 |

### 6.5 Decisiones del usuario

| Decisión | Requisitos que la citan como fuente |
|---|---|
| USR-01 | RF-AIK-004, RF-REP-001, RF-REP-002, RQ-AI-012, RQ-DSH-034, RQ-REP-001, RQ-REP-002, RQ-REP-008, RQ-REP-015, RQ-REP-017, RQ-REP-021, RQ-REP-026, RQ-REP-027, RQ-SEC-019 |
| USR-02 | RF-DSH-001, RF-DSH-002, RF-DSH-006, RQ-DSH-001, RQ-DSH-034, RQ-GIS-001, RQ-GIS-005, RQ-GIS-014 |
| USR-03 | RF-IAM-001, RNF-MNT-001, RNF-MNT-002, RNF-MNT-005 |
| USR-04 | RNF-MNT-007 |
| USR-05 | RQ-DSH-036, RNF-MNT-007 |
| USR-06 | RQ-SEC-001 |
| USR-07 | RF-DOC-003, RQ-AI-009, RQ-AUD-011, RQ-GIS-015, RQ-REP-014, RQ-REP-033, RQ-SEC-010, RQ-SEC-018, RNF-PRIV-004 |
| USR-08 | RNF-MNT-006 |
| USR-09 | RF-CAT-001, RQ-SEC-003, RNF-I18N-001, RNF-INT-004, RNF-MNT-004, RNF-USA-001 |

### 6.6 Bocetos y plataforma

No las pide el encargo, pero completan la cobertura de la columna Source.

| Fuente | Requisitos |
|---|---|
| ARQ-01 | RQ-AI-001, RQ-AUD-012 |
| ARQ-02 | RF-AIK-002, RF-AIK-003, RF-COM-010, RQ-AI-003 |
| ARQ-03 | RQ-AI-008 |
| ARQ-04 | RQ-AI-001 |
| WAS-ADR-0003 | RNF-MNT-002, RNF-MNT-005 |
| WAS-ADR-0004 | RQ-GIS-017, RQ-SEC-002 |
| WAS-ADR-0006 | RQ-GIS-012, RQ-REP-030, RQ-SEC-011 |
| WAS-ADR-0007 | RQ-GIS-012, RNF-INT-005 |
| WAS-ADR-0010 | RQ-SEC-001, RQ-SEC-002, RQ-SEC-015, RQ-SEC-016 |
| WAS-ADR-0013 | RN-WFL-001 |
| WAS-ADR-0014 | RF-AIK-005, RQ-AI-001 |
| WAS-ADR-0015 | RQ-AI-001, RQ-AI-005, RQ-AI-007, RQ-AI-014, RNF-TST-001 |
| WAS-ADR-0019 | RQ-GIS-006, RNF-INT-005 |
| WAS-ADR-0020 | RF-IAM-002, RQ-DSH-032, RQ-SEC-004 |
| WAS-ADR-0023 | RQ-REP-026, RQ-REP-029 |
| WAS-ADR-0024 | RNF-MNT-001, RNF-MNT-005 |
| WAS-ADR-0025 | RNF-MNT-005 |
| WAS-ADR-0036 | RQ-DSH-031, RQ-GIS-016 |
| WAS-ADR-0038 | RQ-DSH-031, RQ-GIS-017 |
| WAS-ADR-0039 | RN-WFL-001 |
| WAS-ADR-0040 | RQ-AUD-001, RQ-AUD-003, RQ-AUD-004, RQ-GIS-011 |
| WAS-ADR-0041 | RQ-AUD-001, RQ-AUD-002 |
| WAS-ADR-0042 | RF-WFL-002, RQ-SEC-004 |
| WAS-ADR-0043 | RQ-SEC-014 |
| WAS-ADR-0044 | RQ-AUD-003 |
| WAS-DOC api/rest.md | RQ-AUD-002, RQ-AUD-005, RQ-AUD-007, RQ-AUD-009, RQ-SEC-007, RNF-INT-001 |
| WAS-DOC gis/geometry.md | RQ-GIS-020 |
| WAS-DOC modules/gis.md | RQ-AUD-013, RQ-GIS-014 |

## 7. Huérfanos

Cada huérfano dice lo que necesita. Esta matriz no corrige los demás documentos: los cambios se proponen en su documento dueño.

### 7.1 Requisitos sin entidad, API o test

Tipo: «No aplica» cuando el requisito, por su naturaleza, no tiene ese elemento; «Hueco» cuando le falta algo que ningún
documento define; «Upstream» cuando lo que falta llega con una brecha de wasichai.

| Requisito | Le falta | Tipo | Qué necesita |
|---|---|---|---|
| RNF-MNT-001 | entidad | No aplica | Es la base técnica de toda la app; no tiene objeto propio. La verifican la prueba de humo de F1 (ASM-066) y las pruebas de contrato con la plataforma |
| RNF-MNT-003 | entidad, API | No aplica | Es la definición de terminado de cada fase; no tiene objeto ni ruta. Se comprueba en la revisión de cierre de cada fase |
| RNF-MNT-005 | entidad | No aplica | Regla de código; no tiene objeto. La prueba `ArchitectureTest` ya la cubre |
| RNF-MNT-006 | entidad, API, test | No aplica | Decisión de repositorios; se verifica al crearlos en F1. No necesita objeto, ruta ni prueba |
| RNF-MNT-007 | entidad, API, test | No aplica | Regla de proceso; se cumple con la pausa de revisión al cerrar F0 |
| RNF-MNT-008 | entidad, API, test | No aplica | Regla de documentación; se verifica en la revisión de `docs/adr/` |
| RNF-MNT-009 | entidad, API | No aplica | Es una regla de documentación; no tiene objeto ni ruta. La prueba es `tools/check_traceability.py`, que en la CI verifica que toda RF, RNF, RN y RQ tiene su fila |
| RNF-INT-001 | entidad | No aplica | Aplica a toda respuesta de error; no tiene objeto propio |
| RNF-INT-003 | entidad | No aplica | Aplica a todo objeto que escribe el portal; no tiene objeto propio |
| RNF-INT-004 | entidad | No aplica | Convención de toda la API propia; no tiene objeto propio |
| RNF-I18N-001 | entidad, API | No aplica | Requisito de la interfaz; no tiene objeto ni ruta propia |
| RNF-I18N-002 | API | No aplica | Regla de cálculo interna de `sgspe.common`; no expone ruta |
| RNF-TST-001 | entidad, API | No aplica | Estrategia de pruebas; sus suites están en las demás filas |
| RNF-OBS-001 | entidad | No aplica | Salud y logs; no tiene objeto propio |
| RNF-USA-001 | entidad | No aplica | Navegación del portal; no tiene objeto propio |
| RQ-GIS-013 | entidad, API | No aplica | Es configuración de la aplicación, no un objeto ni una ruta: `sgspe.map.base-style-url` y, en F6, el mapa `sgspe.map.base-style-url-by-organization` de `application.yml` (`gis.md` §6.4); el mapa base de `MapView` sigue esperando UPS-13 |
| RQ-GIS-016 | API | No aplica | Son índices declarados en `model/model.json`; no exponen ruta |
| RQ-GIS-021 | API | No aplica | Es el conjunto de pruebas GIS; no expone ruta |
| RQ-GIS-022 | API | No aplica | Regla interna del código de `sgspe.territory`; no expone ruta |
| RQ-REP-027 | API | No aplica | La retención la hace el trabajo JOB-08; no expone ruta |
| RQ-DSH-031 | entidad | No aplica | Arquitectura de consulta; lee los objetos de cada KPI |
| RQ-DSH-035 | entidad, API | No aplica | Biblioteca de gráficos de la interfaz; no tiene objeto ni ruta |
| RQ-DSH-036 | entidad | No aplica | Es una regla de fases; no tiene objeto propio. La prueba es el `<Tablero>DashboardApiTest` (propuesto) de cada fase |
| RQ-SEC-005 | API | No aplica | Regla de código; no expone ruta |
| RQ-SEC-015 | entidad | No aplica | El límite vive en el proxy; no tiene objeto propio. Su prueba de despliegue es `LoginRateLimitTest` (propuesto); la revocación de tokens, los intentos de login y la política de contraseñas llegan con UPS-09 |
| RQ-SEC-016 | entidad, API | No aplica | Configuración de producción; no tiene objeto ni ruta |
| RQ-SEC-017 | entidad | No aplica | Es el conjunto de pruebas de seguridad; no tiene objeto propio |
| RQ-SEC-018 | entidad, API | No aplica | Regla sobre artefactos versionados; no tiene objeto ni ruta |
| RQ-SEC-020 | entidad | No aplica | Regla de proceso de archivos; no tiene objeto propio |
| RQ-AUD-006 | entidad | No aplica | Encabezado de toda petición; no tiene objeto propio |
| RQ-AUD-008 | test | Upstream | Su prueba llega con UPS-02. Mientras tanto se prueba la mitigación: modelo y roles solo desde el repositorio (`test_apply.py`) |
| RQ-AUD-009 | test | Upstream | Su prueba (paginación y filtros por fecha y usuario en `GET /api/audit`) llega con UPS-05; hoy `AuditApiTest` cubre el historial por registro |
| RQ-AUD-010 | API, test | Upstream | Su prueba (una escritura directa sobre `audit_log` en la base falla) llega con UPS-12; no tiene ruta propia |
| RQ-AI-007 | entidad, API | No aplica | Es la estrategia de pruebas de IA; no tiene objeto ni ruta |
| RQ-AI-011 | entidad | No aplica | Regla de los agentes; no tiene objeto propio |

### 7.2 Objetos canónicos que ningún requisito cita

De los 89 objetos de `domain-model.md` §6, se buscan los que no aparecen en el texto de ninguna fila RF, RNF, RN o RQ
del análisis.

Ninguno: cada objeto canónico aparece en al menos una fila de requisito del análisis.

Además, los 89 objetos aparecen en la columna Entity de esta matriz.

### 7.3 Endpoints de `api.md` sin requisito

Se comparan los 67 endpoints de `/api/sgspe/**` de `api.md` §4 con la columna API de esta matriz.

Ninguno: cada endpoint de `api.md` §4 aparece en al menos una fila.

### 7.4 KPIs sin RQ-DSH

Se revisa la columna Requisitos de los 53 KPI de `dashboards.md` §4: un KPI es huérfano si ningún requisito lo sostiene.

Ninguno: KPI-TER-01 lo sostienen RQ-GIS-005 y RF-DSH-006, y RQ-DSH-034 fija el mismo código de los tableros (decisión de la
Tarea 7).

## 8. Conteos

| Familia | Filas |
|---|---|
| `RF-PRJ` | 6 |
| `RF-TER` | 8 |
| `RF-LBS` | 9 |
| `RF-STK` | 14 |
| `RF-ENG` | 12 |
| `RF-FLD` | 1 |
| `RF-COM` | 10 |
| `RF-AGR` | 3 |
| `RF-GRV` | 7 |
| `RF-RSK` | 5 |
| `RF-EWS` | 4 |
| `RF-INV` | 4 |
| `RF-DOC` | 2 |
| `RF-WFL` | 4 |
| `RF-NTF` | 1 |
| `RF-REP` | 2 |
| `RF-DSH` | 10 |
| `RF-IAM` | 2 |
| `RF-AIK` | 9 |
| `RF-CAT` | 1 |
| `RNF-MNT` | 9 |
| `RNF-PRIV` | 1 |
| `RNF-INT` | 5 |
| `RNF-I18N` | 2 |
| `RNF-TST` | 1 |
| `RNF-OBS` | 1 |
| `RNF-USA` | 1 |
| `RN-PRJ` | 1 |
| `RN-TER` | 3 |
| `RN-STK` | 3 |
| `RN-ENG` | 5 |
| `RN-COM` | 5 |
| `RN-INV` | 6 |
| `RN-GRV` | 2 |
| `RN-RSK` | 3 |
| `RN-EWS` | 1 |
| `RN-WFL` | 1 |
| `RN-NTF` | 1 |
| `RQ-GIS` | 22 |
| `RQ-REP` | 32 |
| `RQ-DSH` | 35 |
| `RQ-SEC` | 17 |
| `RQ-AUD` | 14 |
| `RQ-AI` | 13 |
| Total | 298 (RF 114, RQ 133, RNF 20, RN 31) |

| Estado | Filas que lo llevan |
|---|---|
| Diseñado | 245 |
| Implementado | 22 |
| Decisión pendiente | 14 |
| Upstream | 28 |
| Hueco | 0 |
| Fuera del MVP | 82 |

Una fila con varios estados cuenta en cada uno. Las cifras por familia coinciden con `requirements-analysis.md` §20.1.
