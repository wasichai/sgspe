# ADR-0017: Datos personales y anonimización

## Estado

Aceptado · 2026-10-07

## Contexto

- El usuario exige datos personales anonimizados en todo artefacto versionado [USR-07]. Las fuentes nombran a autoridades
  y dirigentes, y piden datos que son personales o sensibles: documentos de identidad de solicitantes, nombre, sexo y edad
  de beneficiarios, fotos de personas, postura, religión y proceso electoral [DOC-01 §5.1.8, §9.3.1.2, Tabla 9.5]
  [DOC-02 p.4] (CON-014).
- Los entregables salen de la organización hacia el cliente, autoridades o el público (RQ-REP-014), y las exportaciones
  XLSX también (RQ-REP-022).
- No hay fuente normativa: la Ley 29733 de protección de datos personales y su reglamento son parte de GAP-001, y la
  retención y el consentimiento, de GAP-014.
- wasichai protege por permisos de objeto, campo y acción, sin restringir registros dentro de un campo; un documento
  emitido no se filtra por permisos de campo [WAS-ADR-0023]; y el trabajo en segundo plano lee como la plataforma, sin
  permisos de campo [WAS-ADR-0039].

## Opciones consideradas

### Anonimizar el documento ya generado

- A favor: un generador de DOCX que no sabe de privacidad.
- En contra: tachar nombres en un DOCX o un PDF es frágil y no se puede probar sobre datos estructurados; una foto o un
  punto de mapa no se "tachan".

### Generar los entregables como quien los pide, con sus permisos de campo

- A favor: cada persona vería solo lo que puede leer, como en una exportación.
- En contra: el contenido de un entregable cambiaría según quién lo genera, y una versión emitida debe ser una sola y
  reproducible (RQ-REP-026).

### Transformar el modelo de datos por nivel de divulgación antes de generar

- A favor: las reglas se aplican sobre datos estructurados y se prueban con fixtures; el generador no sabe de niveles.
- En contra: el contenido de un entregable lo define su tipo, no los permisos de quien lo genera, así que preparar,
  aprobar, emitir y descargar lo INTERNO queda en pocos roles.

## Decisión

Se adopta la tercera opción, dentro de una política de cinco piezas ([security.md §7.9, §11 y §12](../security.md)):

1. **Permisos de campo y minimización en la aplicación.** Los campos "(restringido)" del modelo de dominio
   ([domain-model.md §4.9](../domain-model.md)) llevan, para todo rol fuera de la tabla de
   [security.md §7.9](../security.md), una entrada sin lectura ni escritura; la postura, la religión y el proceso
   electoral solo los leen `SOCIAL_MANAGER` y `STAKEHOLDER_MANAGER` (GAP-024) (RQ-SEC-010). Minimización: el padrón comunal
   sin nombres (ASM-018), asambleas con filas que representan grupos, citas atribuidas por cargo, QCS anónimas sin persona,
   ningún punto de persona en el mapa (RQ-GIS-015), y consentimiento por foto y por medio de contacto (RNF-PRIV-004).
2. **Niveles de divulgación sobre el modelo.** INTERNO, EXTERNO y PUBLICO (ASM-037) se aplican en `sgspe.privacy` al modelo
   de datos antes de generar un entregable (ADR-0013) o escribir una exportación (RQ-REP-014):

   | Nivel | Personas | Fotos | Ubicaciones | Sensibles |
   |---|---|---|---|---|
   | INTERNO | Por nombre | Todas | Como en el registro | Los que el tipo incluye |
   | EXTERNO | Por cargo y organización, o por código | Solo con consentimiento o sin personas identificables | Puntos de vivienda generalizados a la localidad | Sin religión, proceso electoral, razones de postura de personas ni nombres de beneficiarios |
   | PUBLICO | Ninguna, solo agregados | Ninguna con personas | Solo capas públicas y conteos | Ninguno |

   En ningún nivel salen el DNI ni los teléfonos, y el documento de identidad nunca se exporta (RF-COM-001). Las menciones
   de personas en la narrativa son referencias a registros que EXTERNO y PUBLICO sustituyen (RQ-REP-033), y toda emisión o
   exportación EXTERNO exige una lista de verificación que queda en `export_log` (ASM-037).
3. **Control de las salidas.** Las exportaciones leen como el usuario, exigen `EXPORT` y dejan su fila en `export_log`
   (RQ-AUD-011, ADR-0011). Los entregables se generan como la plataforma, así que las acciones declaradas `GENERATE`,
   `APPROVE`, `ISSUE` y `DOWNLOAD_RESTRICTED` sobre `deliverable` [WAS-ADR-0042] son solo de los roles que leen los
   campos sensibles; las versiones EXTERNO y PUBLICO se descargan con `EXPORT` y solo en PDF; el modelo congelado, que es
   nominal, nunca se sirve
   ([security.md §7.8 y §11.4](../security.md)). Una plantilla de wasichai-documents imprime un campo restringido solo si
   todo rol con READ sobre el objeto puede leerlo. Un indicador que necesita un campo ilegible muestra "no disponible"
   (RQ-DSH-032, ADR-0016).
4. **Fuera de los datos de negocio.** Los logs no llevan datos personales y la línea de acceso omite la cadena de consulta;
   los avisos nombran el registro y el evento, nunca valores restringidos; GeoServer no registra los cuerpos de GetMap
   (ADR-0015). La IA se activa por organización. Los agentes propios de `sgspe.ai` seudonimizan antes de llamar al
   modelo (RQ-AI-009, ADR-0004); el asistente AI-05 de wasichai-agent no lo hace hasta UPS-14, y por eso solo se ofrece a
   roles sin acceso a campos restringidos, hoy `ANALYST` ([security.md §11.5 y §15](../security.md), ADR-0003).
5. **Artefactos versionados sin datos reales** (RQ-SEC-018): ningún nombre real, DNI, teléfono, número de partida ni dato de
   comprobante en documentos, semillas, fixtures, pruebas, prompts ni ejemplos. Se usan cargos y nombres claramente
   ficticios ("Persona Ejemplo 1"), correos `@sgspe.test` y topónimos reales. Antes de cada commit, una búsqueda local
   contra una lista de nombres que vive fuera del repositorio da cero coincidencias; para F1 se propone un hook previo al
   commit y una comprobación en CI de patrones genéricos de DNI y celular.

## Consecuencias

### Positivas

- La anonimización se prueba sobre datos estructurados y fixtures, no sobre archivos.
- Una versión emitida es una sola por nivel, reproducible y con su registro de salida.
- El repositorio puede compartirse sin exponer a las personas de las fuentes.

### Negativas y riesgos

- Un permiso de campo vale para todos los registros: la postura de una organización queda tan restringida como la de una
  persona (GAP-024).
- Con la lectura a nivel de tenant (CON-041, ADR-0006), un rol lee los campos que su permiso le da en todos los proyectos.
- La protección de quien plantea una QCS confidencial es parcial: el vínculo `stakeholder` no está restringido y, con la
  lectura a nivel de tenant, casi todos los roles operativos leen las QCS ([security.md §11.3](../security.md)).
- Un nombre tecleado a mano en la narrativa no siempre se detecta: lo cubre la lista de verificación de EXTERNO.
- No se auditan las lecturas (GAP-031), y la retención y el cifrado de respaldos y archivos dependen de GAP-014 y GAP-022.

### Seguimiento

- Abiertos: GAP-001, GAP-014, GAP-022, GAP-024, GAP-031, CON-041 y la restricción del vínculo `stakeholder` de las QCS,
  que decide el checkpoint ([security.md §15](../security.md)). UPS-14: mientras no exista, AI-05 no seudonimiza y por eso
  solo se ofrece a roles sin acceso a campos restringidos (`security.md` §15).
- Rige desde F0 para los artefactos versionados, F2 para permisos de campo y exportaciones, F3 para evidencias y F8 para
  entregables.
- Pruebas: "Permisos de campo", "Salidas", "Descargas", "IA" y "Nombres reales" de [security.md §14](../security.md);
  `test_apply_roles.py`; los criterios 11, 12 y 21 de [deliverables.md §13.2](../deliverables.md).

## Referencias

- Requisitos: RQ-SEC-010, RQ-SEC-018, RQ-REP-014, RQ-REP-022, RQ-REP-026, RQ-REP-033, RQ-AUD-011, RQ-AI-009, RQ-GIS-015,
  RQ-DSH-032, RNF-PRIV-004, RF-COM-001, CON-014, CON-041, ASM-018, ASM-037, GAP-001, GAP-014, GAP-022, GAP-024, GAP-031,
  UPS-14.
- Documentos: [security.md §7.8, §7.9, §11, §12, §14 y §15](../security.md), [deliverables.md §2, §9 y §10](../deliverables.md),
  [domain-model.md §4.9](../domain-model.md), [database-model.md §25 y §26](../database-model.md),
  [requirements-analysis.md §14](../requirements-analysis.md).
- wasichai: `WAS-ADR-0023` (A document is frozen when it is issued), `WAS-ADR-0039` (Background work runs RecordService as
  the platform, and takes a cluster lock), `WAS-ADR-0042` (An object declares its own actions, and they are granted,
  checked and listed like CRUD).
- ADR de SGSPE: ADR-0003, ADR-0004, ADR-0006, ADR-0011, ADR-0013, ADR-0015, ADR-0016.
- Fuentes: [USR-07], [DOC-01 §5.1.8, §7.1, §9.3.1.2, Tabla 9.5], [DOC-02 p.4].
