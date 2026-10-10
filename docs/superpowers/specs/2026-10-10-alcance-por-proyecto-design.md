# SGSPE · Plan 4.4: alcance de lectura por proyecto y separación de tenants, especificación de diseño

> **Para agentes:** esta especificación es la entrada de los planes 4.4a (documentos, modelo y backend) y 4.4b (portal).
> No define IDs: los que se nombran en §5 los agrega la primera tarea de 4.4a a `docs/requirements-analysis.md`, y hasta
> entonces `docs/` sigue siendo la fuente de verdad. Donde esta especificación y `docs/` difieren, esa tarea corrige
> `docs/` según lo que aquí se decide.

**Objetivo.** Cerrar UPS-01 por proyecto antes de F5: que cada persona lea solo los registros de los proyectos a los que
está asignada, en todas las rutas de lectura de wasichai y de SGSPE, con el SPI `RecordReadScope` de wasichai 0.6.0
(WAS-ADR-0048), y dejar el despliegue listo para varios clientes con UPS-10 (`MANAGE_TENANTS` y
`wasichai.organizations.separate-provisioning`, WAS-ADR-0055) y un aprovisionamiento por tenant con script.

**Arquitectura.** Un segundo `RecordReadScope` en `sgspe.scope`, `ProjectReadScope`, con su condición pura
`ProjectReadCondition`: una subconsulta SQL sobre `project_assignment` vigente de quien llama, sin lecturas desde Kotlin.
Una acción declarada nueva, `READ_ALL_PROJECTS` sobre `project`, exime de él sin literales de rol. `sgspe.common` gana
`ProjectReach` («mis proyectos» o todos), que comparten el alcance, los tableros y las exportaciones. `IssueDocumentFilter`
pasa a cubrir también la lectura de documentos emitidos. `ProjectScopeGuard` se queda como segunda línea en la escritura.
`model/` gana `provision_tenant.py` y `upgrade_tenants.py`.

**Fuentes.** `.superpowers/sdd/fase-4/ups-01-evaluacion.md` (evaluación de factibilidad del 2026-10-10, con evidencia en
SGSPE `main` 1dbe8a5 y wasichai `v0.6.0`); las decisiones del usuario del 2026-10-10 (§2.1, vinculantes);
`docs/security.md` (§4.1, §6.1, §6.2, §9.2, §9.5, §9.6, §10); ADR-0006 y ADR-0028; `docs/requirements-analysis.md`
(CON-041, GAP-025, RQ-SEC-006, UPS-01, UPS-06, UPS-10); WAS-ADR-0048 y WAS-ADR-0055 en la etiqueta `v0.6.0`.

## Restricciones globales

- Las de F4: wasichai `0.6.0` exacto, Kotlin `2.4.20`, Spring Boot `4.1.1`, JDK `25`, Gradle `9.8.0`; español (es-PE) en
  documentos, comentarios y `detail`, nombres técnicos en inglés (ADR-0009).
- Nunca código bajo el paquete `wasichai`; nunca `RecordStore`, `PhysicalTableRecordStore`, `AuditService` ni
  `ObjectSchemaManager`; `scope` y `privacy` solo dependen de `common`; los tenants solo con `Organizations`.
- Ningún literal de rol en `src/main/kotlin`: quién lee todo el tenant sale de `model/roles.json` por la acción
  declarada.
- Nunca `asPlatform` dentro de una petición. Los trabajos siguen corriendo como la plataforma, sin alcance.
- Toda condición SQL con valores enlazados (`bind`), nunca concatenados; los nombres de tabla y columna salen de la
  definición y van con `SqlIdentifier.quote`.
- Datos de prueba ficticios (`@sgspe.test`, «Proyecto Ejemplo A» y «Proyecto Ejemplo B»).

## 1. Propósito y alcance

Hoy rige la opción A de CON-041 (`security.md` §9.2): quien tiene READ sobre un objeto lo lee en todos los proyectos del
tenant, también en mapas, tableros, historial, documentos y archivos; solo la escritura está acotada por
`ProjectScopeGuard`. La evaluación concluye que el mismo SPI que ya acota las QCS confidenciales (ADR-0028) resuelve el
alcance por proyecto del lado de SGSPE, y que casi todas las lecturas de SGSPE lo heredan sin cambios porque pasan por
`Records` como quien llama.

| Subsistema | Alcance |
|---|---|
| Alcance de lectura | `ProjectReadScope` sobre `project`, `project_assignment`, las 29 raíces con `project` obligatorio, las 3 raíces con `project` opcional y los 19 hijos; los 20 maestros del tenant sin restricción |
| Exención | Acción declarada `READ_ALL_PROJECTS` sobre `project`, concedida a `AUDITOR` y `PROJECT_ADMIN`; `ADMIN`, la plataforma y las automatizaciones, exentos por wasichai |
| Brechas de wasichai | Documentos emitidos (`GET .../documents`, `GET /api/documents/{id}`) cubiertos por `IssueDocumentFilter`; GeoServer sin capas de proyecto |
| Ajustes | `ProjectReach` en tableros y exportaciones; códigos de error; `ArchitectureTest` del registro en `ProjectScope` |
| Pruebas | `ProjectReadConditionTest`, `ProjectReadScopeTest`, `ProjectReadScopeApiTest`; columna «otro proyecto» en las matrices; migración de la suite de integración |
| Varios clientes | UPS-10 en producción; `provision_tenant.py` y `upgrade_tenants.py`; medición de los trabajos con varios tenants |
| Portal | Errores 404 y 400 nuevos, «Registro reservado» general, selector de proyectos para quien lee todo |

Fuera de 4.4: el alcance por territorio y región (sin fuente de datos en el modelo; UPS-01 queda abierta solo en esa
parte); acotar los maestros del tenant (decisión 3); informar el alcance en `/api/auth/me/permissions` (UPS-06, upstream);
paralelizar los trabajos entre tenants (solo se mide, §3.10); la política de contraseñas (UPS-09).

## 2. Decisiones

### 2.1 Las decisiones del usuario (2026-10-10)

| # | Decisión | Modelo | Backend (4.4a) | Portal (4.4b) |
|---|---|---|---|---|
| 1 | Hacerlo ahora, como plan 4.4 antes de F5, con UPS-01 por `RecordReadScope` y UPS-10 (`MANAGE_TENANTS` y `separate-provisioning`): el `ADMIN` de un cliente no crea ni borra tenants; el aprovisionamiento y la subida de fase de cada tenant, por script | Ninguno por UPS-10 (el permiso no va en `roles.json`, §3.9) | `ProjectReadScope`; `separate-provisioning` en `application-prod.yml`; `provision_tenant.py` y `upgrade_tenants.py` | Ninguno por UPS-10 |
| 2 | `ANALYST` lee solo sus proyectos; la lectura de todo el tenant la da la acción declarada nueva `READ_ALL_PROJECTS` sobre `project` (en `roles.json`: `AUDITOR` y `PROJECT_ADMIN`; `ADMIN` exento por wasichai); ningún literal de rol en el código | `READ_ALL_PROJECTS` declarada en `project` y concedida a `AUDITOR` y `PROJECT_ADMIN` | `ProjectReach.readsAll` con `Permissions.has` | El selector muestra «Todos los proyectos» a quien tiene la acción |
| 3 | Los maestros del tenant (personas, organizaciones, comunidades, territorio, línea base, temas) siguen compartidos entre los proyectos del cliente; los datos sensibles de cada proyecto (evaluaciones, QCS, compromisos, riesgos, etc.) se acotan | Ninguno | `ProjectReadCondition` devuelve `null` para los 20 maestros | Ninguno |

### 2.2 Elecciones de esta especificación

1. **El criterio es SQL puro** (§3.1): una subconsulta sobre la tabla de `project_assignment`. Leer las asignaciones
   con `Records` dentro de `criterion` dispararía el mismo alcance sobre `project_assignment` (recursión) y se repetiría
   por cada objeto que toca una lectura; la subconsulta no necesita caché ni lecturas extra.
2. **Raíz opcional y padre opcional se leen como del tenant cuando no tienen proyecto**, igual que en la escritura
   (`security.md` §9.2, punto 4): una evidencia, una regla de alerta, una fila de `export_log`, una entrega o un perfil
   de localidad sin proyecto los ve todo quien tiene READ.
3. **Cada persona ve sus propias asignaciones** aunque estén vencidas, y las vigentes de sus equipos
   (`project_assignment`: `member_user_id = quien llama OR project IN (mis proyectos)`): el portal puede decirle por qué
   ya no ve un proyecto, y los listeners que avisan al equipo siguen viendo al equipo.
4. **`HAS_DEPENDENTS` se acepta como oráculo menor** (§3.6): cerrarlo exigiría contar los dependientes como quien llama, y
   entonces un padre con dependientes en otro proyecto se borraría y wasichai dejaría esos registros huérfanos
   (`ON DELETE SET NULL`). La integridad pesa más que la revelación de un sí o un no sin datos.
5. **`ProjectScopeGuard` se queda.** Con el alcance, casi todo lo que rechazaba lo rechaza antes wasichai (404 o 400),
   pero sigue siendo la única barrera para quien lee todo el tenant sin estar asignado (`PROJECT_ADMIN` escribiendo en
   un proyecto ajeno) y para el alta de raíces con `project` que la persona sí lee.
6. **Una sola pieza decide «mis proyectos»** (`ProjectReach`, `sgspe.common`): el alcance, los proyectos por defecto de
   tableros y exportaciones y la exención de `ExportProjects` usan la misma regla y la misma vigencia (`Validity`).
7. **El `MANAGE_TENANTS` del operador se siembra fuera de banda** (WAS-ADR-0055) en el rol `ADMIN` de la organización
   operadora, nunca en `roles.json`, que se aplica a cada tenant cliente (§3.9).
8. **El plan se divide en 4.4a (documentos, modelo y backend) y 4.4b (portal)**, con los documentos dentro de 4.4a
   (§6).

## 3. Diseño por subsistema

### 3.1 El criterio (`ProjectReadCondition`, `sgspe.scope`)

Función pura, sin base, como `ConfidentialCondition`: recibe el nombre del objeto, las tablas y columnas ya resueltas,
quien llama, la fecha de hoy en Lima y `bind`, y devuelve `null` (no restringe), una condición o `NOTHING` (`false`).

**Mis proyectos (`P`).** Con `A` la tabla física de `project_assignment` del tenant:

```sql
SELECT a."project" FROM <A> a
 WHERE a.organization_id = :org AND a."member_user_id" = :me AND a."active" IS TRUE
   AND (a."start_date" IS NULL OR a."start_date" <= :today)
   AND (a."end_date" IS NULL OR a."end_date" >= :today)
```

La misma vigencia que `Validity.inForce` y `ProjectScopeGuard` (`security.md` §9.2, punto 2). `:today` es la fecha de
Lima (`LIMA`) del reloj inyectado; los nombres de columna salen de la definición de `project_assignment`.

**Condición por grupo** (la columna del objeto leído va sin alias, porque el SQL del store no usa alias; las
subconsultas califican siempre las suyas):

| Grupo | Objetos | Condición |
|---|---|---|
| Proyecto | `project` | `"id" IN (P)` |
| Asignaciones | `project_assignment` | `"member_user_id" = :me OR "project" IN (P)` |
| Raíz obligatoria | Las 29 de `ProjectScope.ROOTS` con `project` obligatorio | `"project" IN (P)` |
| Raíz opcional | `evidence`, `early_warning_rule`, `export_log` | `"project" IS NULL OR "project" IN (P)` |
| Hijo | Los 19 de `ProjectScope.CHILDREN`, con `T` la tabla del padre | `"<padre>" IN (SELECT t."id" FROM <T> t WHERE t.organization_id = :org AND t."project" IN (P))` |
| Hijo de padre opcional | `delivery` (`commitment`), `locality_profile` (`study`) | `"<padre>" IS NULL OR` lo anterior |
| Maestro del tenant | Los 20 de `ProjectScope.TENANT` (§3.5) | `null` |

Si la raíz o el padre es opcional sale del catálogo (`ModelCatalog.isRequiredRelation`), como en `ProjectResolver`,
nunca de una lista aparte. Un objeto que no está en ningún grupo devuelve `NOTHING` (falla cerrado); `ArchitectureTest`
impide que eso llegue a `main` (§3.5).

**Falla cerrado.** Si la definición no trae la columna de alcance (`project`, el padre o, en `project_assignment`, las de
la vigencia), devuelve `NOTHING` y deja una advertencia sin datos del registro.

### 3.2 El componente (`ProjectReadScope`, `sgspe.scope`)

Implementa `RecordReadScope.criterion(caller, definition)`:

1. si el objeto es un maestro del tenant, `null` sin más consultas;
2. si quien llama tiene `READ_ALL_PROJECTS` sobre `project` (`ProjectReach.readsAll`, que pregunta a
   `Permissions.has` y memoriza por petición con `RequestMemo`), `null`;
3. si no, resuelve con `MetadataService` la definición de `project_assignment` y, en un hijo, la del padre (en caché de
   wasichai) y sus tablas físicas, como ya hacen `EvidenceWriteGuard` y `DependentsDeleteGuard`; y devuelve un
   `RecordCriterion` que arma la condición de §3.1 con el `bind` de la consulta;
4. si la consulta de permisos o la de definiciones falla, `NOTHING` con una advertencia (falla cerrado, como
   `ConfidentialAttentionScope`).

**A quién se aplica.** wasichai lo pide para toda persona y toda cuenta de servicio que no sea `ADMIN` (una cuenta de
servicio con el rol `ADMIN` sí se pregunta, WAS-ADR-0048); nunca para la plataforma ni para las automatizaciones
(`caller == null`). Una cuenta de servicio se asigna a sus proyectos como cualquier usuario (`security.md` §9.2, punto 6)
o recibe un rol con `READ_ALL_PROJECTS`.

**Dónde rige** (evaluación §1): listas, `totalElements`, búsqueda, filtros y orden; detalle, `PUT`, `PATCH` y `DELETE`
(404); relacionados en ambas direcciones y M2M, y `POST/DELETE .../related`; destino de toda `RELATION` en una escritura
(400 como registro inexistente, D30); historial y `GET /api/audit`; transiciones (listado y aplicación, 404); features GIS;
archivos (`GET .../files/{field}`, y por eso `EvidenceContent`); el agente (F7). Los documentos emitidos los cubre SGSPE
(§3.4).

### 3.3 Composición con las QCS confidenciales

- **Y lógico.** wasichai recoge todos los `RecordReadScope` y une sus criterios con AND, cada uno entre paréntesis, detrás
  del filtro de tenant (`RecordReadScopes.criteria`). Una QCS confidencial de otro proyecto queda oculta por los dos;
  `READ_CONFIDENTIAL` no abre otros proyectos y `READ_ALL_PROJECTS` no abre las confidenciales. El orden de los beans no
  cambia el resultado. `GRIEVANCE_MANAGER` lee las confidenciales solo de sus proyectos; `AUDITOR` lee todos los
  proyectos pero no las confidenciales que no le tocan.
- **`ConfidentialRelease`.** `RequestClosureListener` cierra la solicitud cuyo `created_commitment` es el compromiso que
  se cierra. `ProjectLinkGuard` ya exige que la solicitud y el compromiso sean del mismo proyecto, y quien cierra el
  compromiso debe estar asignado a ese proyecto, así que el alcance por proyecto admite la solicitud sin extender la
  excepción. Una prueba de integración lo fija (solicitud confidencial cerrada por un asignado que no la ve).
- **`DerivedRestore`.** Cambiar el `project` de un registro exige asignación a los dos proyectos, así que el registro
  sigue legible mientras `DerivedFieldsKeeper` lo repone. No se extiende.
- **`ProjectReach` no reemplaza a `Permissions` en `ConfidentialAttentionScope`**: cada alcance decide lo suyo.

### 3.4 Documentos emitidos

wasichai-documents comprueba solo READ sobre el objeto en `GET /api/objects/{o}/records/{id}/documents` y
`GET /api/documents/{id}`, y la respuesta lleva el `snapshot` congelado del registro: sin cubrirlas, el alcance se salta
leyendo el documento.

- **`IssueDocumentFilter` suma las dos rutas `GET`** (el `POST` ya lo cubre como edición, con `UPDATE`, asignación y
  `EDIT_ANY`).
  - `GET .../records/{id}/documents`: lee el registro con `Records.find` como quien llama; si no lo lee, `404` con el
    problema de wasichai, igual que el detalle del registro.
  - `GET /api/documents/{id}`: obtiene el objeto y el registro del documento con `DocumentService.byId` (que ya exige
    READ sobre el objeto) y después lee el registro como quien llama; si no lo lee, `404`. Si `DocumentService` no se
    puede inyectar en `sgspe.scope`, el filtro decora la respuesta: lee `objectName` y `recordId` del cuerpo antes de
    escribirlo y responde `404` si el registro no es legible. Lo decide la primera tarea de documentos de 4.4a.
- **Emisión por automatización** (AUT-05 y siguientes): corre como la plataforma, sin alcance, como hasta ahora.
- **Upstream candidata UPS-28** (§5): que wasichai-documents aplique `RecordReadScope` en sus tres rutas. Hasta
  entonces, el filtro.
- `IssuedDocumentsPrivacyTest` suma un caso: el documento de un compromiso de otro proyecto responde `404` en las dos
  rutas.

### 3.5 Registro de objetos (`ProjectScope` y `ArchitectureTest`)

- `ProjectScope` gana `TENANT`, la lista de los 20 maestros: territorio (`administrative_unit`, `locality`, `community`,
  `community_sector`, `mining_concession`, `land_parcel`, `land_right`, `environmental_feature`), actores (`person`,
  `organization`, `contact_method`, `position`, `actor_relationship`), línea base (`school`, `health_facility`,
  `road_access`, `festivity`, `state_program`, `local_conflict`) y `topic`. `mining_concession` se vincula a proyectos
  por M2M (`project_concession`) pero es un maestro.
- `project_assignment` sigue en `EXEMPT` para la escritura y tiene su propio caso en la lectura (§3.1).
- **Regla nueva de `ArchitectureTest`**: todo objeto de `model/model.json` está en exactamente uno de `project`,
  `project_assignment`, `ROOTS`, `CHILDREN` o `TENANT`; todo objeto con una relación `project` está en `ROOTS` (o es
  `project_assignment`); el padre de cada hijo es una raíz. Así una fase nueva (F5 a F8) no puede agregar un objeto sin
  clasificarlo: `indicator_measurement`, `ai_suggestion` y `ai_interaction` entran como raíces opcionales.
- **Otra regla**: solo `ProjectReadScope` construye un `ProjectReadCondition`, y solo `sgspe.common` y `sgspe.scope`
  llaman a `ProjectReach.readsAll`.
- `test_model.py` suma: todo rol con `CREATE` sobre `project` tiene `READ_ALL_PROJECTS` (si no, el alta respondería con
  un registro que quien lo crea no lee); `READ_ALL_PROJECTS` solo se concede sobre `project`.

### 3.6 Semántica de errores

| Caso | Antes (opción A) | Con 4.4 |
|---|---|---|
| Leer el detalle, el historial, los relacionados o las transiciones de un registro de otro proyecto | `200` | `404` |
| Cambiar, borrar o transicionar un registro de otro proyecto | `403 NOT_ASSIGNED` | `404` |
| Alta de una raíz con `project` ajeno | `403 NOT_ASSIGNED` sobre `project` | `400` de wasichai sobre `project` (registro inexistente) |
| Vincular por `RELATION` o M2M un registro de un proyecto en que no se está asignado | `409 PROJECT_MISMATCH` | `400` sobre el campo |
| Vincular dos registros de proyectos distintos, ambos legibles | `409 PROJECT_MISMATCH` | Igual |
| Escribir sin asignación teniendo `READ_ALL_PROJECTS` (`PROJECT_ADMIN`) | `403 NOT_ASSIGNED` | Igual (`ProjectScopeGuard`) |
| Asignación vencida | `403 NOT_ASSIGNED` al escribir | `404` al leer y al escribir |
| Exportar con un solo proyecto pedido ajeno | `403 NOT_ASSIGNED` sobre `filters.project` | Igual, salvo con `READ_ALL_PROJECTS` |
| Tablero o indicador con `filters.project` ajeno | Cifras del proyecto | Conjuntos vacíos, sin error |
| Borrar un maestro o un padre con dependientes en otro proyecto | `409 HAS_DEPENDENTS` | Igual: oráculo aceptado (elección 4) |
| Código único repetido en otro proyecto | `409` de wasichai | Igual: oráculo aceptado |

- `ProjectResolver` lee el padre como quien escribe: un padre de otro proyecto deja de leerse y la escritura termina en el
  `404` del registro o el `400` de la relación antes de llegar a la guarda. No cambia de código; sus pruebas cambian lo
  que esperan.
- `IssueAccessByScope` deja de distinguir `OBJECT_NOT_READABLE` de `NOT_ASSIGNED` en un registro fuera del alcance: ese
  registro no se lee y responde `404`, como wasichai. `NOT_ASSIGNED` queda solo para quien lo lee sin estar asignado.
- Los mensajes de `409 HAS_DEPENDENTS` no nombran el proyecto, el objeto dependiente ni cuántos hay.
- El portal no ve diferencia entre «no existe» y «no es de mis proyectos»: es deliberado (§3.11).

### 3.7 Tableros, indicadores, capas y exportaciones

- **Heredan el alcance sin cambios**: `DashboardData` carga con `Records` como quien llama; las capas LYR-01 a LYR-16 y
  LYR-20 (`territory/Layers.kt`) leen igual; `GeoSql` solo calcula sobre GeoJSON; el reporte unificado y las cuatro
  exportaciones leen como quien exporta.
- **Proyectos por defecto (`ProjectReach`, `sgspe.common`)**: `mine(user)` devuelve `null` (todos) para `ADMIN` y para
  quien tiene `READ_ALL_PROJECTS`, y si no, las asignaciones vigentes de quien llama. `Dashboards` deja su respaldo
  «sin asignaciones, todos los proyectos» (que con el alcance ya daba solo los legibles) y usa `mine`.
  `ExportProjects.assigned` pasa a `ProjectReach` (`privacy` solo depende de `common`).
- **Exención de `ExportProjects`**: con `READ_ALL_PROJECTS`, «mis proyectos» son todos y un proyecto pedido no exige
  asignación, como `ADMIN`. Hoy ningún rol tiene a la vez `EXPORT` y `READ_ALL_PROJECTS` (`AUDITOR` solo lee: su
  `"*": ["READ"]` no concede `EXPORT`), así que la regla rige para la concesión que se agregue después, y una prueba la
  fija con un rol de prueba. La fila de `export_log` de una exportación de varios proyectos va sin `project` (raíz
  opcional), así que `ProjectScopeGuard` no la frena; con un solo proyecto pedido por quien no está asignado, la fila
  llevaría ese `project` y la guarda respondería `403`: `ExportLog` deja `project` vacío cuando quien exporta no está
  asignado y el proyecto queda en `filters_description`.
- **`confidentialHidden`** no cambia: describe las QCS confidenciales, no los proyectos. No se agrega una marca de
  «proyectos ocultos»: el selector ya dice cuáles se miran.

### 3.8 Trabajos y avisos

- JOB-01, JOB-02, JOB-04, JOB-08, `DeadlineNotices`, `DerivedFieldsJob`, `StakeholderFollowUpJob` y `JobTasks` corren
  dentro de `records.asPlatform`: sin alcance, como deben. Los avisos ya se dirigen por las asignaciones del proyecto
  (`Assignments.recipients`).
- Los listeners de avisos dentro de una petición leen `project_assignment` como quien escribe; quien escribe está
  asignado al proyecto (o tiene `READ_ALL_PROJECTS`), así que ve el equipo entero (elección 3).
- La bandeja de wasichai-notifications no se filtra: el enlace de un aviso a un registro que ya no se lee responde `404` y
  el portal muestra «Registro reservado». Quien deja un proyecto conserva el texto de sus avisos viejos (código y tipo,
  nunca texto libre ni datos personales, ASM-102): residual aceptado.

### 3.9 Varios clientes: UPS-10 y aprovisionamiento

- **Interruptor.** `wasichai.organizations.separate-provisioning: true` en `application-prod.yml`. En desarrollo y en las
  pruebas queda apagado (el valor por defecto), porque `TenantIsolationApiTest` y el tenant de desarrollo crean
  organizaciones con el `ADMIN`; una prueba de integración propia lo enciende.
- **Quién tiene `MANAGE_TENANTS`.** El rol `ADMIN` de la organización operadora (`SUPER_ADMIN`), con una fila de
  `permissions` que siembra quien opera la base, una sola vez y antes de encender el interruptor (WAS-ADR-0055: el
  primer titular se siembra fuera de banda). El comando SQL y el orden van en `docs/develop/README.md` (operación). No va
  en `roles.json`: ese archivo se aplica a cada tenant cliente, y `apply_roles.py`, que entra con el `ADMIN` del
  cliente, no podría concederla de todos modos (solo un titular la entrega). Ninguna cuenta de servicio la tiene.
- **Efecto.** El `ADMIN` de un cliente conserva usuarios, roles, cuentas de servicio y el nombre del tenant, y recibe
  `403` en `POST /api/organizations` y `DELETE /api/organizations/current`.
- **`model/provision_tenant.py`.** Con las credenciales del operador (una persona, nunca una cuenta de servicio), llama
  `POST /api/organizations` con el nombre, el slug y el primer administrador; después corre, con las credenciales del
  administrador nuevo, `apply.py` → `apply_roles.py` → `apply.py --workflows` → `load_ubigeo.py`, en ese orden, y termina
  recordando que el cliente debe cambiar la contraseña inicial (`security.md` §4.1). Las contraseñas llegan por variable
  de entorno o por consola, nunca por argumento ni archivo.
- **`model/upgrade_tenants.py`.** Repite los pasos 2 a 5 de `security.md` §4.1 en cada tenant de un inventario JSON
  (slug y correo del administrador) que se pasa por argumento y que no se versiona (`model/tenants.example.json`, con
  datos ficticios `@sgspe.test`, muestra la forma). wasichai no lista organizaciones y así sigue: el inventario es del
  operador. Un tenant que falla se informa y no detiene a los demás; el código de salida es distinto de cero si alguno
  falló, y el resumen dice qué paso falló en cuál.
- **Pruebas.** `test_provision_tenant.py` y `test_upgrade_tenants.py` con `fake_core.py` (orden de pasos, credenciales
  que no salen en la salida, falla de un tenant que no detiene a los demás). `SeparateProvisioningApiTest` (integración,
  interruptor encendido): el `ADMIN` de un tenant cliente recibe `403` en las dos rutas, y el `ADMIN` de la operadora con
  la fila sembrada crea un tenant.

### 3.10 Rendimiento y medición

- **Índices.** wasichai indexa cada columna `RELATION` y `organization_id`; `member_user_id` es `indexed` en
  `model.json`. `P` devuelve las pocas filas de una persona y el `IN` sobre el padre es un semi-join por índice. No se
  agrega ningún índice salvo que la medición lo pida (y entonces va en `model.json`, nunca con SQL propio).
- **Medición de lecturas** (tarea de 4.4a): con «Proyecto Ejemplo A» y «Proyecto Ejemplo B» y un volumen representativo
  (10 proyectos, 5 000 grupos de interés y 50 000 evaluaciones en `sgspe_test`), `EXPLAIN ANALYZE` de una lista de
  `stakeholder_assessment` (hijo) y de `stakeholder` (raíz) con y sin alcance. Criterio: sin barrido secuencial de la
  tabla del padre y menos de 20 % más de tiempo en la lista paginada. Si la consulta de permisos de la exención pesa
  (una por objeto y lectura fuera de un comando), un `WebFilter` abre `RequestMemo` para toda petición de `/api`.
- **Medición de los trabajos con varios tenants.** Los trabajos recorren las organizaciones en serie
  (`Organizations.having`). 4.4a agrega a cada barrido una línea de log por organización con la duración (sin nombres)
  y mide JOB-01 y JOB-04 con tres tenants en `sgspe_test`. Si el barrido por tenant multiplicado por 20 clientes supera
  la mitad del intervalo más corto (JOB-04, cada hora), se abre una tarea de backlog para paralelizar con un tope; 4.4
  no paraleliza. El resultado se anota en el cierre de la Release 1 (§5, roadmap).

### 3.11 GeoServer y `/api/auth/me/permissions`

- **GeoServer (F6).** Lee la base con sus credenciales, sin permisos ni alcance (WAS-ADR-0048). Regla nueva en
  `security.md` §10.2 y en `gis.md`: ningún objeto de `ProjectScope` (raíces, hijos ni `project`) se publica, solo
  maestros de `ProjectScope.TENANT` sin campos restringidos. `publish_layers.py` (F6) lo comprueba contra la misma
  clasificación y `test_model.py` lo prueba; la Definición de terminado de F6 lo nombra.
- **`/api/auth/me/permissions`** no informa el alcance (UPS-06 sigue abierta). Sí lista `READ_ALL_PROJECTS` entre las
  acciones de `project`, porque wasichai informa las acciones declaradas como CRUD (WAS-ADR-0042). El portal lo usa para
  decidir si muestra «Todos los proyectos»; qué proyectos concretos ve lo sabe leyendo `project`, que ya viene acotado.

### 3.12 Portal (`../sgspe-ui`, plan 4.4b)

- **Selector de proyectos.** Lista los `project` que la cuenta lee (ya acotados) y, con `READ_ALL_PROJECTS`, ofrece
  «Todos los proyectos» como valor por defecto; sin asignaciones ni la acción, un estado vacío que dice «No tiene
  proyectos asignados. Pida una asignación al administrador de proyectos».
- **404 de detalle.** Una ficha que responde `404` dice «El registro no existe o no es de sus proyectos», sin distinguir.
- **«Registro reservado» general.** Hoy solo para una QCS; pasa a todo vínculo cuyo destino responde `404`
  (`LecturaConEstados`, `Estados`, `AbrirRegistroPage` de los avisos).
- **400 de `RELATION`.** Un formulario que recibe el `400` de wasichai sobre un campo de relación lo muestra en ese campo:
  «El registro elegido no existe o es de otro proyecto». Los selectores de relación ya listan solo lo legible.
- **`NOT_ASSIGNED`** se sigue mostrando como hoy, para el caso que queda (§3.6).
- **Tableros y exportaciones.** Sin cambios de contrato; S49 deja de exigir un proyecto a quien tiene
  `READ_ALL_PROJECTS`.
- **Pruebas** con Vitest por pantalla tocada, más una de `boundaries.test` que siga sin literales de rol.

### 3.13 Pruebas del backend

- **Unitarias.** `ProjectReadConditionTest`: `project`, `project_assignment`, raíz, raíz opcional, hijo, hijo con padre
  opcional, maestro (`null`), objeto sin clasificar (`NOTHING`), columna faltante (`NOTHING`), valores siempre
  enlazados. `ProjectReadScopeTest`: exención por `READ_ALL_PROJECTS`, permisos que fallan (`NOTHING`), definición del
  padre que falta (`NOTHING`), maestro sin consultas. `ProjectReachTest`.
- **Integración `ProjectReadScopeApiTest`** (sobre `F3ApiTest` o la base de F4): dos proyectos y una persona asignada solo
  a A. Por cada ruta de §3.2 (lista, `totalElements`, detalle, relacionados en ambos sentidos, historial, `/api/audit`,
  transiciones, features, archivo de una evidencia, documentos): ve A y no B. Más: KPI y tablero sin cifras de B;
  exportación sin filas de B; capa LYR de B vacía; alta con `project` B en `400`; vínculo a un registro de B en `400`;
  asignación vencida que oculta A; `AUDITOR` y `PROJECT_ADMIN` que ven los dos; `ADMIN` que ve los dos; una cuenta de
  servicio asignada a A; una QCS confidencial de A que un relacionista de B no ve aunque se le dé `READ_CONFIDENTIAL`;
  `ConfidentialRelease` dentro de A (§3.3).
- **Matrices.** `PermissionMatrixApiTest` asigna a cada usuario de la matriz al proyecto de la matriz y suma la columna
  «otro proyecto» (lectura `404`, alta `400`); `WorkflowMatrixApiTest` aplica cada transición como asignado y comprueba
  una de cada workflow como no asignado (`404`).
- **Migración de la suite** (el riesgo principal, §7): las 65 llamadas a `userWithRole` en 26 clases crean usuarios sin
  asignación, y 25 aserciones esperan `NOT_ASSIGNED`. Cada llamada se clasifica en una tabla del plan:
  1. el usuario debía leer el proyecto de la prueba → pasa a `member`;
  2. el usuario es `AUDITOR` o `PROJECT_ADMIN`, o la prueba es de un maestro → sin cambio;
  3. la prueba quería un no asignado → espera la fila de §3.6 que le toca (`404` o `400`).
  Se corren juntas las clases de cada lote y la suite entera una vez al final, por el túnel.

## 4. Cambios en el modelo

- `model.json`: `project` declara `READ_ALL_PROJECTS` («Leer todos los proyectos») junto a `APPROVE`.
- `roles.json`: `AUDITOR` gana `"project": ["READ_ALL_PROJECTS"]` junto a su `"*": ["READ"]` (que no concede acciones
  declaradas); `PROJECT_ADMIN`, `READ_ALL_PROJECTS` en `project`. Ningún otro rol: `ANALYST`, `GIS_ANALYST` y
  `READ_ONLY` leen sus proyectos (decisión 2; los maestros del territorio de `GIS_ANALYST` no se acotan).
- `test_model.py` y `test_apply_roles.py`: la acción en el catálogo, sus dos concesiones y las reglas de §3.5.
- **Orden de despliegue**: el tenant se actualiza (`apply.py` → `apply_roles.py` → `apply.py --workflows`) **antes** de
  desplegar el backend con `ProjectReadScope`. Al revés, `AUDITOR` y `PROJECT_ADMIN` dejarían de leer los proyectos en
  que no están asignados hasta aplicar los roles. El backend viejo ignora la acción.

## 5. Cambios en los documentos (primera tarea de 4.4a)

**IDs nuevos en `docs/requirements-analysis.md`** (siguientes libres: ASM-106, UPS-28 y ADR-0029; §20.1 pasa de ASM 105 a
109, de UPS 27 a 28 y de 522 a 527 en total):

| ID | Contenido |
|---|---|
| ASM-106 | Alcance de lectura por proyecto (§3.1 a §3.3): una persona lee `project`, sus raíces y sus hijos solo de sus asignaciones vigentes (los sin proyecto, de todo el tenant); ve sus propias asignaciones y las de sus equipos; `READ_ALL_PROJECTS` sobre `project` exime (`AUDITOR`, `PROJECT_ADMIN`); `ANALYST` solo sus proyectos; compone con ASM-095 por AND; los trabajos sin alcance; reversible quitando el `RecordReadScope` |
| ASM-107 | Los maestros del tenant (territorio, actores, línea base y `topic`, `ProjectScope.TENANT`) se comparten entre los proyectos de un cliente: acotarlos exigiría un vínculo con el proyecto que el modelo no tiene; un grupo de interés de un proyecto apunta a una persona que leen los demás |
| ASM-108 | Semántica de errores del alcance (§3.6): `404` y `400` en lugar de `403 NOT_ASSIGNED` fuera del alcance; `NOT_ASSIGNED` solo para quien lee sin estar asignado; `409 HAS_DEPENDENTS` y los únicos del tenant quedan como oráculos aceptados de sí o no, sin datos |
| ASM-109 | Varios clientes (§3.9): `separate-provisioning` encendido en producción; `MANAGE_TENANTS` solo en el `ADMIN` de la organización operadora, sembrado fuera de banda; alta y subida de fase por tenant con `provision_tenant.py` y `upgrade_tenants.py` sobre un inventario no versionado; trabajos en serie, medidos |
| UPS-28 | Candidata: que wasichai-documents aplique `RecordReadScope` en `GET .../documents`, `GET /api/documents/{id}` y en la emisión, y no devuelva el `snapshot` de un registro fuera del alcance; mitigación: `IssueDocumentFilter`; sin issue hasta la aprobación del usuario |

**Filas que cambian.**

- **CON-041**: «Decidido: opción B del lado de SGSPE con `RecordReadScope` (WAS-ADR-0048), ADR-0029»; estado Resuelto.
- **GAP-025**: resuelta por la decisión del usuario del 2026-10-10 (alcance antes de F5, ASM-106).
- **RQ-SEC-006**: la mitigación pasa a la descripción del diseño por proyecto; territorio y región siguen sin fuente.
- **UPS-01**: resuelta por proyecto del lado de SGSPE; queda abierta solo por territorio y región; wasichai#48 se
  comenta con el resultado (no se cierra sin el usuario).
- **UPS-10**: resuelta con wasichai 0.6.0 (WAS-ADR-0055) y ASM-109.
- **UPS-06**: sin cambio; su texto nombra `READ_ALL_PROJECTS` como la forma en que el portal sabe si lee todo.

**ADR-0029 «Alcance de lectura por proyecto con `RecordReadScope`»** (Aceptado, 2026-10-10): contexto (§9.2 y la
evaluación), opciones (A, como hasta F4; B con el SPI propio; listas de UUID leídas en Kotlin, descartada por la
recursión y el costo; acotar también los maestros, descartada por decisión 3), decisión (§3.1 a §3.7), consecuencias
(§3.6, §7) y seguimiento (pruebas de §3.13, regla de `ArchitectureTest`). ADR-0006 pasa a «Aceptado» entero, con el
alcance por proyecto «sustituido por ADR-0029»; ADR-0028 cambia su seguimiento («se integra en el del proyecto» por «se
compone por AND con ADR-0029»). `docs/adr/README.md` lista ADR-0029.

**Además.**

- `security.md`: §4.1 con los scripts y el interruptor; §5.3 suma `READ_ALL_PROJECTS` al catálogo de RQ-SEC-004; §6.1,
  columna «Ámbito que necesita», ya sin «dependen de UPS-01»; §6.2, UPS-10 resuelta; §7.2 y §7.8 con las dos
  concesiones; §9 reescrito: §9.2 queda como la opción A histórica, §9.5 pasa de «Recomendación» a «Decisión», y una
  §9.7 nueva describe el alcance por proyecto (§3 de esta especificación); §9.6 suma la composición; §10.2 suma la regla
  de GeoServer de §3.11; §14 suma las pruebas de §3.13; §15 cierra CON-041 y GAP-025.
- `api.md` §3.3: la semántica de §3.6 y los casos que quedan de `NOT_ASSIGNED`; §4.11 y §4.13: proyectos por defecto y
  exención con `READ_ALL_PROJECTS`; los dos `GET` de documentos con `404`.
- `dashboards.md` §3.1: proyectos por defecto con `ProjectReach`; un proyecto ajeno da conjuntos vacíos.
- `gis.md`: las capas heredan el alcance; ningún objeto de proyecto en GeoServer.
- `architecture.md` §5.2 (`ProjectReadScope`, `ProjectReach`), §14 riesgo 1 resuelto por proyecto.
- `domain-model.md` §4.7 (clasificación de los objetos, `TENANT`) y §8.1.1 (la acción).
- `database-model.md`: `READ_ALL_PROJECTS` en `project`.
- `ux/screens.md`: «Registro reservado» general y el selector de §3.12.
- `glossary.md`: «alcance de lectura por proyecto» y «maestro del tenant».
- `requirements-traceability.md`: RQ-SEC-006 pasa a «Diseñado» con `ProjectReadScopeApiTest`; UPS-10 con
  `SeparateProvisioningApiTest`.
- `roadmap.md`: plan 4.4 en F4 («Estado» y «Definición de terminado»); el cierre de la Release 1 exige 4.4a y 4.4b
  integrados, el tenant de desarrollo con la acción, la medición de §3.10 anotada y la siembra de `MANAGE_TENANTS`
  documentada; F5 a F8 suman a su Definición de terminado «objetos nuevos clasificados en `ProjectScope`»; el riesgo 1
  del registro de riesgos queda mitigado; «Brechas upstream» suma UPS-28.
- `CLAUDE.md`: el alcance y `ProjectReach` en «Dónde está cada cosa»; los dos scripts en «Comandos».
- `docs/develop/README.md`: la siembra de `MANAGE_TENANTS` y el uso de los scripts.

## 6. Reparto en planes

| Plan | Rama | Depende de | Tareas principales |
|---|---|---|---|
| 4.4a · Documentos, modelo y backend | `feat/fase-4-4a-alcance` desde `main` | Esta especificación | (1) documentos de §5 e IDs, `check_traceability.py` en verde; (2) `READ_ALL_PROJECTS` en `model.json` y `roles.json`, `test_model.py` y `test_apply_roles.py`; (3) `ProjectScope.TENANT` y las reglas de `ArchitectureTest`; (4) `ProjectReach` y su prueba; (5) `ProjectReadCondition` pura; (6) `ProjectReadScope`; (7) `IssueDocumentFilter` en las dos rutas `GET`; (8) `ExportProjects`, `Dashboards` y `ExportLog` con `ProjectReach`; (9) `IssueAccessByScope` y mensajes de `HAS_DEPENDENTS`; (10) `ProjectReadScopeApiTest`; (11) matrices de permisos y de workflows; (12) migración de la suite en lotes; (13) rendimiento y medición de trabajos; (14) UPS-10: interruptor, `SeparateProvisioningApiTest`, siembra documentada; (15) `provision_tenant.py` y `upgrade_tenants.py` con sus pruebas; (16) actualizar el tenant de desarrollo (coordinador, por el túnel); (17) suite completa, revisión final y PR |
| 4.4b · Portal | `feat/fase-4-4b-alcance` en `sgspe-ui` | 4.4a en `main` | Selector de proyectos y «Todos los proyectos»; 404 de detalle; «Registro reservado» general; 400 de `RELATION` en formularios; S49 sin proyecto obligatorio para quien lee todo; pruebas; PR; cierre de la Release 1 |

**¿Documentos aparte?** No: van como primera tarea de 4.4a. En F4 hubo un plan 4.0 porque los cambios tocaban quince
documentos y cuatro planes los esperaban; aquí la decisión es una sola, la consumen solo 4.4a y 4.4b y otro PR alargaría
un ciclo de revisión sin ganar nada. Los documentos entran primero en la rama, así que `docs/` sigue mandando.

**¿Se divide 4.4a?** No. Con 17 tareas es del tamaño de 4.2a, y su grueso (tareas 10 a 12) es una sola corrida de la
suite que no se paraleliza (`sgspe_sdd_test` es uno solo). El portal puede preparar sus pantallas con datos simulados
mientras 4.4a corre, pero se integra después.

## 7. Riesgos y consecuencias aceptadas

**Consecuencias aceptadas.**

1. **Maestros compartidos** (decisión 3, ASM-107): una persona, una organización o una comunidad que solo usa el proyecto
   B la ve quien trabaja en A, con sus campos restringidos protegidos por los permisos de campo.
2. **Oráculos** (ASM-108): un `409 HAS_DEPENDENTS` o un único repetido dicen sí o no sobre otro proyecto, sin datos.
3. **Ninguna diferencia entre «no existe» y «no es de mis proyectos»** en la API ni en el portal.
4. **El texto de los avisos viejos** queda en la bandeja de quien deja un proyecto (§3.8).
5. **Cifras por quien llama**: dos personas con asignaciones distintas ven tableros distintos del mismo tenant.

**Riesgos.**

1. **Migración de la suite de integración** (el principal): 65 llamadas a `userWithRole` y 25 aserciones de
   `NOT_ASSIGNED`, y una suite que tarda de 30 minutos a más de una hora por el túnel. Se mitiga con la clasificación de
   §3.13 en el plan, lotes por clase y una sola corrida completa al final.
2. **Contratos y portal**: los `404` y `400` nuevos rompen lo que hoy espera `403 NOT_ASSIGNED`; 4.4b va detrás de
   4.4a y el portal en producción no se despliega sin él.
3. **`DocumentService` desde `sgspe.scope`**: si no se puede inyectar, el filtro decora la respuesta (§3.4), más frágil;
   UPS-28 lo retira.
4. **Rendimiento**: la consulta de permisos de la exención fuera de un comando y los semi-joins de los hijos con mucho
   volumen; lo decide la medición de §3.10.
5. **Orden de despliegue** (§4): roles antes que backend, o `AUDITOR` y `PROJECT_ADMIN` pierden lectura un rato.
6. **Fases siguientes**: cada objeto nuevo debe clasificarse; la regla de `ArchitectureTest` lo hace obligatorio, pero un
   objeto mal clasificado como maestro quedaría abierto. La revisión de cada plan de modelo lo mira.
7. **Siembra de `MANAGE_TENANTS`**: si se enciende el interruptor sin la fila, nadie crea tenants hasta sembrarla; el
   procedimiento de `docs/develop/README.md` pone la siembra primero.
8. **Trabajos en serie**: con muchos clientes, un barrido puede no caber en su intervalo; §3.10 lo mide y no lo resuelve.

**Pregunta abierta para el usuario.** Solo una confirmación: la decisión 2 nombra `AUDITOR` y `PROJECT_ADMIN` «por
ejemplo». Esta especificación concede `READ_ALL_PROJECTS` solo a esos dos; `SOCIAL_MANAGER` («región si conduce varios
proyectos», `security.md` §6.1) queda acotado a sus asignaciones y se asigna a cada proyecto que conduce. Si debe leer
todo el tenant, basta sumarle la acción en `roles.json`.
