# ADR-0007: El modelo como código en `model/`

## Estado

Aceptado · 2026-10-07

## Contexto

- El prompt pide versionar cada cambio de esquema con Flyway [PRM-01 §20]. En wasichai la metadata es el programa: el
  DDL de negocio nace en tiempo de ejecución, lo emite solo `ObjectSchemaManager` y cada organización tiene su copia del
  modelo y sus tablas (WAS-ADR-0003, WAS-ADR-0004). La contradicción es CON-052.
- Cada módulo de la plataforma migra sus propias tablas con un Flyway propio registrado como `ModuleMigration`
  (WAS-ADR-0026).
- srtm y caja versionan su modelo en `model/model.json` y `model/roles.json` y lo aplican por REST con `apply.py` y
  `apply_roles.py`, en Python con solo la biblioteca estándar [USR-03] (RNF-MNT-002).
- wasichai no audita los cambios de metadata, usuarios ni roles (UPS-02); no se sabe si se permitirá editar la metadata
  en `/admin` en producción (GAP-030); guarda `defaultValue` pero no lo aplica (UPS-15).
- SGSPE tiene 90 objetos, workflows, roles, vistas, formularios, páginas, tipos de documento y automatizaciones que
  llegan por fase, y cuatro tablas fijas que no son Custom Objects.

## Opciones consideradas

### Flyway para todo el esquema (pila del prompt)

- Ventajas: historia lineal de migraciones SQL.
- Desventajas: imposible en wasichai: el código propio no emite DDL de negocio (RNF-MNT-005).

### El constructor de `/admin` como fuente de verdad

- Ventajas: sin herramientas propias; cambios inmediatos.
- Desventajas: sin versionado ni revisión; sin auditoría de metadata (UPS-02); cada tenant derivaría por su lado.

### `model/` versionado y aplicado por REST de forma idempotente (elegida)

- Ventajas: el patrón vigente de caja y srtm; el repositorio es la fuente de verdad, revisable y probado; un tenant
  nuevo se prepara con los mismos scripts.
- Desventajas: `apply.py` nunca renombra ni borra, así que esos cambios exigen migraciones de datos propias.

## Decisión

**El esquema de negocio versionado es `model/*.json`, aplicado con `apply.py`; Flyway propio solo para las tablas fijas
del esquema `sgspe`** ([database-model.md §3.1 y §24](../database-model.md), [architecture.md §5.4](../architecture.md)).

- **Contenido de `model/`.** `model.json` (claves `enums`, `objects` y `relationships`) y `roles.json`;
  `workflows/`, `views/`, `forms/` y `pages/` desde F2; `document-types/` y `automations/` desde F3; `apply.py`,
  `apply_roles.py`, `publish_layers.py` (F6), el importador del ubigeo y sus pruebas.
- **Campos derivados.** Llevan `"derived": true`, que solo leen las herramientas de SGSPE: `apply.py` no la envía, porque
  publica un conjunto fijo de claves. Nunca se marcan con `editable: false`, porque wasichai omite los campos no editables
  en toda escritura, también en las de la plataforma (`PhysicalTableRecordStore.kt`, líneas 40 y 86, en el código de
  wasichai-core 0.3.2; [database-model.md §3.1](../database-model.md)).
- **Permisos de `roles.json`.** `apply_roles.py` expande la notación `u` en UPDATE más una entrada de solo lectura para
  cada campo no derivado, y se regenera en cada fase para que un campo nuevo no quede escribible
  ([security.md §6.3](../security.md)). Rechaza declarar `ADMIN`, `SUPER_ADMIN` u `ORGANIZATION_ADMIN`.
- **Orden de aprovisionamiento de un tenant**, documentado por GAP-026:

| # | Paso | Por qué en ese orden |
|---|---|---|
| 1 | `POST /api/organizations`, que crea el tenant y su administrador | Los pasos siguientes usan esa cuenta |
| 2 | `apply.py`: objetos con flags, acciones declaradas, relaciones, únicos e índices | Los roles nombran objetos y acciones |
| 3 | `apply_roles.py`: roles, permisos, permisos de campo y acciones concedidas | Los workflows nombran roles |
| 4 | `apply.py --workflows`: workflows, tipos de documento, automatizaciones y vistas, formularios y páginas, cada uno desde su fase | wasichai rechaza un workflow con un rol inexistente |
| 5 | Catálogos de referencia: ubigeo, temas y, desde F8, indicadores | Datos públicos o de configuración |
| 6 | Desde F6, `publish_layers.py`: capas no sensibles y estilos SLD | Ver ADR-0015 |

- **Sincronización.** Idempotente: agrega objetos, campos, opciones, acciones y relaciones; sincroniza etiquetas,
  descripciones, `unique`, `indexed` y opciones; relaja `required`. **Nunca** renombra, cambia de tipo, vuelve requerido
  ni borra un campo, ni borra una opción en uso. `--validate-only` y `--dry-run` revisan sin escribir; `--drop` es solo
  para desarrollo. Una subida de fase repite los pasos 2 a 6.
- **Migraciones de datos.** Un objeto sin `apiOnly` ni `appendOnly` se migra con un script `model/migrar_<tema>.py`
  idempotente, con reporte y motivo que nombra la migración (ASM-051). Uno `apiOnly` se migra en proceso, en el paquete
  dueño, con `RecordService` como la plataforma y las guardas activas (WAS-ADR-0039). Uno `appendOnly` exige un ADR
  explícito o conservar la opción vieja (WAS-ADR-0040). Casos previstos: renombrar una opción (ASM-025), renombrar o
  cambiar de tipo un campo, cambiar un valor por defecto y volver requerido un campo.
- **Tablas fijas.** `idempotency_key`, `derived_version` y `knowledge_chunk` viven en el esquema `sgspe` con su
  `ModuleMigration` (`sgspe_common` en F3, `sgspe_ai` en F7), de orden mayor que 100; desde la decisión D2 de F3
  (ADR-0018), `notification`, `notification_delivery` y `sgspe_notification` se retiran: los avisos son de
  wasichai-notifications; una migración aplicada nunca se edita. Sin claves foráneas entre esquemas: la baja de un
  tenant borra antes sus filas por `organization_id` y después llama a `DELETE /api/organizations/current` (GAP-026).
- **Fuente de verdad.** El repositorio; `/admin` no lo es en producción y volver a aplicar resincroniza (GAP-030). Los
  valores por defecto los aplica el código de SGSPE mientras exista UPS-15 (ASM-019).

## Consecuencias

### Positivas

- Cada cambio del modelo pasa por revisión y CI; la deriva de un tenant se corrige volviendo a aplicar.
- Mitiga UPS-02: como el modelo y los roles cambian solo desde el repositorio, su historia es la de git.
- Un tenant nuevo y una subida de fase usan el mismo procedimiento.

### Negativas y riesgos

- Renombrar o cambiar de tipo es caro: campo nuevo, copia y retiro del viejo; en objetos `appendOnly`, además un ADR.
- `defaultValue` no se sincroniza: cambiarlo es un caso de migración.
- Ediciones manuales en `/admin` se pierden al volver a aplicar ([architecture.md §14](../architecture.md), riesgo 4).

### Seguimiento

- Pruebas: `test_model.py` (objetos del catálogo, claves de `enums`, nombres, límites, flags, únicos e índices),
  `test_apply.py` con el caso de tenant nuevo en el orden de arriba, `test_apply_roles.py` ([security.md
  §14](../security.md)) y pruebas de integración que aplican `model.json` a PostGIS con Testcontainers (RNF-TST-001).
- Pendientes: GAP-026 (quién opera el alta y la baja), GAP-030, UPS-02 y UPS-15.
- Si el checkpoint cambia el idioma de los valores ENUM (CON-050), la migración sigue las reglas de
  [database-model.md §24](../database-model.md), resumidas arriba en "Migraciones de datos"; la decisión de idioma está
  en ADR-0009.

## Referencias

- Requisitos: RNF-MNT-002, RNF-MNT-005, RNF-TST-001, CON-052, GAP-026, GAP-030, ASM-019, ASM-025, ASM-051, UPS-02,
  UPS-15, CON-050.
- Documentos: [database-model.md](../database-model.md) §2.1, §2.3, §3.1, §3.6, §24 y §27;
  [architecture.md](../architecture.md) §5.4 y §5.5; [security.md](../security.md) §4.1 y §6.3;
  [domain-model.md](../domain-model.md) §4.10 y §8.4.
- wasichai: WAS-ADR-0003 (*Metadata-driven architecture*), WAS-ADR-0004 (*One physical table per Custom Object*),
  WAS-ADR-0026 (*Each module owns its migrations and its Flyway history*), WAS-ADR-0039 (*Background work runs
  RecordService as the platform, and takes a cluster lock*), WAS-ADR-0040 (*Append-only objects, a pre-write guard SPI,
  and api-only objects*).
- Fuentes: [PRM-01 §8, §20], [USR-03].
