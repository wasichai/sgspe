# ADR-0023: Separación de persona, cargo, grupo de interés, comunidad y área de influencia

## Estado

Aceptado · 2026-10-07

## Contexto

- El prompt trata como "Stakeholder" a personas, organizaciones, comunidades y entidades, y pide a la vez que el modelo no
  confunda comunidad, área geográfica, área de influencia y grupo de interés [PRM-01 §5] (CON-038).
- Las fuentes distinguen cinco cosas que el prompt junta:
  - **la persona y su cargo:** DOC-01 nombra a las autoridades por su cargo (teniente gobernador, agente municipal, presidente de
    ronda), con cargos vacantes y personas con dos cargos [DOC-01 §6, Tabla 7.1] (RN-STK-006);
  - **la postura frente al proyecto,** que DOC-01 llama "posición", como el cargo [DOC-01 §6];
  - **el grupo de interés,** que es el actor en relación con un proyecto y se evalúa con fecha [DOC-01 §6, §7.2];
  - **la comunidad campesina,** un colectivo con personería, territorio inscrito, junta directiva y sectores que se superponen con
    caseríos [DOC-02 p.3–p.5];
  - **el área de influencia,** definida por criterios y listas de localidades, no solo por un polígono [DOC-01 §4], mientras
    DOC-03 la dibuja como un polígono de 148 ha [DOC-03] (CON-025).
- DOC-01 además intercambia los rótulos AID y AII (CON-001), y el perfil del prompt (legitimidad, relevancia, sentimiento) no
  coincide con el de DOC-01 (CON-031).
- Los nombres técnicos en inglés los decide ADR-0009; el almacenamiento de geometrías, ADR-0002.

## Opciones consideradas

### Una entidad "Stakeholder" con tipo, como el prompt

- Ventajas: un solo maestro y una sola pantalla.
- Desventajas: una persona repetida por cada proyecto o con dos cargos; una comunidad y un caserío tratados como actores con
  postura; un área de influencia reducida a un atributo. Incumple RN-STK-006 y RN-STK-008.

### Actor y grupo de interés separados, pero comunidad y área de influencia como tipos de actor o como polígonos

- Ventajas: menos objetos que la opción elegida.
- Desventajas: la comunidad perdería su territorio, sus datos registrales y sus sectores; el área de influencia sería un polígono
  que decide la pertenencia sola, contra RN-TER-004.

### Objetos separados para cada concepto, con sus reglas (elegida)

- Ventajas: cada concepto tiene sus campos, su ciclo y sus permisos; las fuentes se modelan sin forzarlas.
- Desventajas: más objetos y más relaciones que recorrer en el portal y en los indicadores.

## Decisión

Se modela cada distinción con su objeto ([domain-model.md §3](../domain-model.md), [glossary.md](../glossary.md), "Distinciones
obligatorias"):

| Concepto | Objeto | Regla |
|---|---|---|
| Actor | `person`, `organization` | Maestro del tenant, registrado una vez con independencia de los proyectos; sus medios de contacto en `contact_method`, con consentimiento, sin una tabla genérica de contactos (RF-STK-001) |
| Cargo | `position` | Puesto en una `organization`, con titular opcional (vacante o previsto), periodo y estado; una persona puede tener varios a la vez (RF-STK-002) |
| Postura | campo `stance` de `stakeholder_assessment` y de `stakeholder` | Posición frente al proyecto; "posición" nunca es identificador |
| Grupo de interés | `stakeholder` y `stakeholder_assessment` | Exactamente una persona u organización, una sola vez por proyecto (RN-STK-006); evaluación fechada e inmutable (RF-STK-003, RF-STK-005) |
| Comunidad | `community` y `community_sector` | Colectivo con territorio y datos registrales; como actor participa mediante la `organization` que la representa (RN-STK-008, RF-TER-003) |
| Área geográfica | `administrative_unit`, `locality`, `environmental_feature` | El "dónde"; nunca es grupo de interés (RN-STK-008) |
| Área de influencia | `area_of_influence` y `area_of_influence_member` | Construcción del proyecto, versionada con WF-O; los miembros, curados, son localidades, comunidades o unidades administrativas (RN-TER-001); una sola versión aprobada por proyecto y tipo (RN-TER-003) |

- **El polígono solo sugiere.** La lista oficial de un área de influencia es la de sus miembros en la versión `approved`; el
  cálculo espacial propone miembros y una persona decide (RN-TER-004, CON-025). La geometría es opcional.
- **Tipos de área** en el catálogo "Tipo de área de influencia": AIDS y AIIS sociales, y AID AMBIENTAL y AII AMBIENTAL, estos
  dos como valores propuestos (CON-001, ASM-039). El área de interés es una geometría de `project`, no un área de influencia.
- **Mecanismos:** "exactamente uno de" con `ExactlyOneGuard`; "una vez por proyecto" con los únicos (`project`, `person`) y
  (`project`, `organization`); un `stakeholder` solo se relaciona con `person` u `organization` por metadata
  ([domain-model.md §7.2](../domain-model.md)).
- **Datos personales:** los nombres y documentos de `person` y la postura son campos restringidos; las citas usan el cargo
  (ADR-0017).
- El perfil del prompt se reemplaza por el de DOC-01: interés y poder de 1 a 5, cuatro posturas y confianza; la legitimidad no se
  registra mientras siga abierto GAP-029 (CON-031).

## Consecuencias

### Positivas

- Un dirigente con dos cargos es una persona; deja un cargo y el cargo queda vacante sin borrar a nadie.
- Una comunidad tiene territorio, sectores y junta, y opina a través de su organización comunal.
- Un área de influencia puede existir sin polígono, como en DOC-01, o con polígono, como en DOC-03, sin que el polígono decida.
- Las QCS, los compromisos y los riesgos se ubican por relación territorial, no por la ubicación de una persona (RQ-GIS-015).

### Negativas y riesgos

- Más objetos que crear y enlazar; el portal debe guiar el alta (actor, luego grupo de interés del proyecto).
- Los indicadores y entregables deben recorrer actor → grupo de interés → evaluación vigente.
- La comunidad como actor depende de que exista su `organization` comunal.

### Seguimiento

- Abiertos: GAP-029 (legitimidad), GAP-003 y ASM-003 (grupo vulnerable), GAP-024 (quién ve la postura).
- Aceptado; los objetos llegan en F2 (BC-02, BC-03).
- Pruebas: `StakeholderWorkflowApiTest` y `AreaOfInfluenceWorkflowApiTest` ([workflows.md](../workflows.md), "Pruebas": los
  miembros de una versión aprobada no cambian); "Sugerencia de miembros" de [gis.md §9](../gis.md) (sugiere y no escribe).

## Referencias

- Requisitos: RF-STK-001, RF-STK-002, RF-STK-003, RF-STK-005, RF-TER-003, RF-TER-005, RN-STK-006, RN-STK-008, RN-TER-001, RN-TER-003,
  RN-TER-004, RQ-GIS-015; CON-001, CON-025, CON-031, CON-038; GAP-003, GAP-024, GAP-029; ASM-003, ASM-039.
- Documentos: [domain-model.md §3, §6.2, §6.3 y §7.2](../domain-model.md), [glossary.md](../glossary.md) ("Actores",
  "Distinciones obligatorias"), [gis.md §1.2 y §2.4](../gis.md), [platform-fit.md §2.3](../platform-fit.md).
- ADR de SGSPE: ADR-0002, ADR-0009, ADR-0017, ADR-0022.
- WAS-ADR-0003 (Metadata-driven architecture), WAS-ADR-0037 (Composite unique constraints, and a repeated unique value as a
  409).
- Fuentes: [DOC-01 §4, §6, §7.2, Tabla 7.1], [DOC-02 p.3–p.5], [DOC-03], [PRM-01 §5].
