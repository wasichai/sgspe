# ADR-0009: Nombres técnicos en inglés; documentación y valores de ENUM en español

## Estado

Aceptado · 2026-10-07

## Contexto

- El prompt nombra sus entidades, estados, roles y rutas en inglés [PRM-01 §5, §7, §11]. El usuario decidió nombres
  técnicos en inglés, como en el prompt, y etiquetas, documentos y README en español [USR-09] (RNF-MNT-004).
- srtm y caja, las apps de referencia, usan identificadores en español (CON-049).
- En wasichai una opción de ENUM no tiene etiqueta: la interfaz muestra el valor tal cual, y su validador admite letras,
  dígitos, espacio, `_`, `.` y `-`, hasta 64 caracteres ([database-model.md §3.5](../database-model.md)). Las etiquetas de
  la metadata tienen un solo idioma (CON-051).
- Un borrador previo del diseño proponía valores de ENUM en inglés con etiquetas en la interfaz; la elección quedó como
  decisión pendiente del checkpoint, con recomendación en español (CON-050, ASM-025).
- Los usuarios son equipos sociales en el Perú, y las fuentes usan un vocabulario propio (posturas, grupos de influencia,
  tipos de actor) que debe significar lo mismo en todo el sistema (RF-CAT-001).

## Opciones consideradas

### Todo en español, como srtm y caja

- Ventajas: un solo idioma; continuidad con las apps de referencia.
- Desventajas: contradice USR-09; los nombres del prompt y de su trazabilidad dejarían de coincidir con el código.

### Identificadores en inglés y valores de ENUM en inglés con etiquetas en la interfaz

- Ventajas: datos neutrales al idioma; consistencia con los identificadores.
- Desventajas: wasichai no tiene etiquetas por opción: `/admin`, las exportaciones, los entregables y la auditoría
  mostrarían el valor en inglés, o habría que traducirlo en cada salida.

### Identificadores en inglés; etiquetas, documentos y valores de ENUM en español (elegida)

- Ventajas: cumple USR-09; el valor que ve el usuario es el que se guarda, en todas las salidas, sin capa de traducción.
- Desventajas: los datos quedan en español; cambiar de idioma después exige migrar datos.

## Decisión

| Elemento | Idioma y forma | Ejemplo |
|---|---|---|
| Objetos, campos, relaciones, estados y transiciones | Inglés, `snake_case` | `stakeholder_assessment`, `deadline_status`, `pending_approval`, `register_fulfillment` |
| Roles y acciones declaradas | Inglés, UPPER_SNAKE | `COMMUNITY_RELATIONS_OFFICER`, `APPROVE_OVER_CAP` |
| Paquetes y rutas propias | `sgspe.*`; `/api/sgspe/**` en plural y kebab-case | `sgspe.attention`, `/api/sgspe/commitment-candidates` |
| JSON de la API | Sobre y parámetros en camelCase; campos del modelo con su nombre; constantes propias en UPPER_SNAKE | `createdAt`, `due_date`, `NEW_VERSION` |
| Etiquetas de objetos, campos, relaciones y estados | Español, en la metadata | "Compromiso", "Pendiente de aprobación" |
| Valores de ENUM | Español, MAYÚSCULAS, sin tildes, comas, barras ni paréntesis | `EN CONTRA PERO ABIERTO A ESCUCHAR`, `AL DIA` |
| Documentos, README, ADR, `detail` de los errores propios y prompts | Español (es-PE) | — |
| Interfaz | Español por defecto e inglés, con números y fechas es-PE; la metadata solo en español | — |

- **Excepción única** de origen español: `ubigeo_code`, nombre oficial del código del INEI. Los demás términos locales se
  traducen: `populated_center_code`, `registered_members_count` ([domain-model.md §4.1](../domain-model.md)).
- **Nombres reservados.** Ningún campo usa las columnas de sistema, los reservados de wasichai ni palabras clave de SQL;
  por eso existen `sort_order`, `version_number`, `end_date` y geometrías con nombre por su papel.
- **Catálogos.** Los valores se listan solo en [requirements-analysis.md §10](../requirements-analysis.md); los que no
  vienen de las fuentes van como "(propuesto)" (ASM-041). Un mismo catálogo se reutiliza en todos los campos que lo
  nombran, con una sola clave en `enums`, interna de `model.json`. Lo jerárquico, largo o editable es un objeto catálogo
  y no un ENUM: `topic`, `administrative_unit` e `indicator` (RF-CAT-001).
- **Puente ES ↔ EN.** El [glosario](../glossary.md) fija cada término del negocio con su identificador; "posición" nunca
  es un identificador, porque DOC-01 la usa para la postura (`stance`) y el cargo es `position`.
- **Claves internas** de una salida estructurada de IA (`item`, `kind` y sus valores) van en inglés y la interfaz no las
  muestra; las salidas estructuradas usan los nombres técnicos del modelo.
- **Idioma de los valores de ENUM.** Se aplica el español MAYÚSCULAS recomendado mientras el checkpoint resuelve
  CON-050; si decide el inglés, un ADR nuevo reemplaza este y la migración sigue
  [database-model.md §24](../database-model.md).

## Consecuencias

### Positivas

- El código, el modelo y la trazabilidad usan los nombres del prompt; los usuarios ven español en el portal, `/admin`,
  las exportaciones y los entregables.
- Un vocabulario único: el mismo valor de catálogo significa lo mismo en todo el sistema.

### Negativas y riesgos

- Dos idiomas conviven en el repositorio; el glosario es obligatorio para leerlo.
- Renombrar una opción es una migración de datos, porque `apply.py` no renombra; en un objeto `appendOnly`, además un
  ADR (ASM-025).
- Las etiquetas de la metadata no tienen inglés: la interfaz en inglés muestra la metadata en español (CON-051).
- Los contenidos en quechua dependen de GAP-013.

### Seguimiento

- Checkpoint de F0: CON-050. Los valores "(propuesto)" se ajustan con los usuarios en F2 (ASM-041).
- `test_model.py` verifica nombres, límites, reservados, claves de `enums` y opciones de ENUM
  ([database-model.md §24](../database-model.md)); `apply.py --validate-only` aplica las mismas reglas sin red.

## Referencias

- Requisitos: RNF-MNT-004, RNF-I18N-001, RF-CAT-001, CON-049, CON-050, CON-051, ASM-025, ASM-041, GAP-013.
- Documentos: [glossary.md](../glossary.md) (Plataforma y Distinciones obligatorias);
  [requirements-analysis.md §10](../requirements-analysis.md); [domain-model.md](../domain-model.md) §4.1 y §4.2;
  [database-model.md](../database-model.md) §3.2, §3.5, §3.8 y §24; [api.md §3](../api.md);
  [ai-architecture.md §4.2 y §10](../ai-architecture.md).
- wasichai: WAS-ADR-0003 (*Metadata-driven architecture*), WAS-ADR-0042 (*An object declares its own actions, and they
  are granted, checked and listed like CRUD*).
- Fuentes: [USR-09], [PRM-01 §5, §7, §11, §13], [DOC-01 §6].
