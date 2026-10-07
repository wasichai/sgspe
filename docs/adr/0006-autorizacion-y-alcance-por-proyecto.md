# ADR-0006: Autorización por roles y alcance por proyecto

## Estado

Aceptado; el alcance por proyecto, Propuesto · 2026-10-07 · el alcance se acepta al iniciar F2, con la opción del checkpoint

## Contexto

- El prompt pide RBAC con 17 roles, autorización limitada por organización, proyecto, región, territorio, módulo y
  operación, y nada de `if role == ...` disperso por el código [PRM-01 §7] (RQ-SEC-003, RQ-SEC-005, RQ-SEC-006).
- La plataforma ya decide su modelo: tenant tomado del token y tablas por organización (WAS-ADR-0004, WAS-ADR-0010);
  permisos `(rol, objeto, acción)` con permisos de campo y `own_records_only` [WAS-DOC security/authentication.md];
  consulta de lo permitido que no concede nada (WAS-ADR-0020); y acciones declaradas por objeto (WAS-ADR-0042). `ADMIN` pasa todo permiso, pero no las reglas de
  los objetos ni las guardas (WAS-ADR-0040).
- wasichai no tiene alcance de lectura por proyecto, territorio ni región (UPS-01, CON-041). Del lado de la app no se
  resuelve: `RecordQueryContributor` solo estrecha listas con su parámetro, `RecordWriteGuard` no cubre lecturas y leer
  las tablas físicas es API interna ([security.md §9.1](../security.md)).
- Administrar usuarios y roles y aprovisionar tenants dependen del mismo permiso de `ADMIN` (CON-059).
- Con `own_records_only`, todo valor RELATION que escribe el usuario debe apuntar a un registro que él creó: un agente de
  campo no podría vincular su captura a un proyecto o a un grupo de interés de otra persona (ASM-069).

## Opciones consideradas

### RBAC propio de SGSPE (pila del prompt)

- Ventajas: libertad total de diseño, alcance por proyecto incluido.
- Desventajas: un segundo motor de permisos fuera de la plataforma, que la API genérica, `/admin` y el agente no
  conocerían (CON-039).

### Permisos de wasichai, acciones declaradas y guardas de SGSPE (elegida para roles y permisos)

- Ventajas: un solo motor de permisos en todas las rutas; las capacidades se informan al cliente; el código decide sin
  nombres de rol.
- Desventajas: sin alcance de lectura por proyecto; la administración de roles no se audita (UPS-02).

### Alcance, opción A: lectura de todo el tenant y escritura acotada por asignación

- Ventajas: no depende de wasichai; la guarda sigue sirviendo con B.
- Desventajas: quien tiene READ sobre un objeto lo lee en todos los proyectos, también en mapas, tableros, historial y
  asistente.

### Alcance, opción B: SPI de alcance de lectura en wasichai (UPS-01)

- Ventajas: cubre listas, detalle, relacionados, historial, features GIS, asistente y destinos RELATION.
- Desventajas: exige un ADR y un issue upstream que SGSPE no controla (GAP-028).

### Alcance, alternativas descartadas

| Alternativa | Por qué no |
|---|---|
| Un tenant por proyecto | Un usuario pertenece a una sola organización; se duplicarían actores y territorio (RQ-SEC-002) |
| Leer las tablas físicas con un filtro propio | Es API interna (RNF-MNT-005), y la API genérica y `/admin` quedarían abiertas |
| Filtrar solo en el portal | Un filtro de interfaz no es una frontera |

## Decisión

**Aceptado: roles y permisos** ([security.md §5 y §6](../security.md)).

- **Roles.** 15 roles declarados en `model/roles.json`, con etiqueta en español, y `SUPER_ADMIN` y `ORGANIZATION_ADMIN`
  en la lista `mappedToAdmin`, como `ADMIN` de la organización operadora y del tenant (ASM-024). Solo `AUDITOR` tiene un
  permiso de todo el tenant (`"*": ["READ"]`); ningún rol tiene MANAGE_METADATA ni la administración de la organización.
  Los roles existen desde F1 sin permisos, salvo `AUDITOR`, y reciben sus concesiones por fase.
- **Acciones declaradas.** Catálogo definitivo de RQ-SEC-004: `APPROVE`, `VERIFY`, `ISSUE`, `EXPORT`, `GENERATE`,
  `CONFIRM_ALERT`, `APPROVE_OVER_CAP`, `DOWNLOAD_RESTRICTED` y `EDIT_ANY`; qué objeto declara cuál y qué rol la recibe
  está solo en [security.md §5.3 y §7](../security.md). **Doble llave:** los roles de cada transición de aprobación,
  verificación o confirmación de alerta están en el workflow, y la guarda del paquete exige además la acción; una prueba
  del modelo verifica que coincidan.
- **Una regla que depende del rol** se resuelve con dos transiciones entre los mismos estados y roles distintos, como
  `approve_senior` (ASM-067) y `resolve_high_severity`, nunca comparando nombres de rol.
- **Permisos de campo** para los datos personales y sensibles de RQ-SEC-010 ([security.md §7.9](../security.md)); un
  permiso de campo es una restricción, así que cada rol que no debe ver un campo lleva su entrada. Quién ve los datos
  sensibles sigue abierto (GAP-024).
- **Edición de lo propio sin `own_records_only`.** `EditAnyGuard` (`sgspe.scope`) rechaza cambiar, transicionar, vincular
  o borrar un registro ajeno (`created_by`) a quien no tiene `EDIT_ANY` sobre `engagement`, `engagement_participant`,
  `field_visit`, `field_observation` o `evidence`; crear no es cambiar; deja pasar las escrituras derivadas con la marca
  de SGSPE. Tienen `EDIT_ANY` todos los roles que escriben esos objetos, salvo `FIELD_AGENT` (RQ-SEC-008, ASM-069).
- **Sin nombres de rol en el código.** Los nombres de rol viven solo en `roles.json` y en los workflows; el código decide
  con `requirePermission`, `isAdmin` y `serviceAccount` (RQ-SEC-005). `project_assignment.role_name` nombra un rol
  declarado que valida `AssignmentRoleGuard` y no concede permisos (ASM-070); su uso para los destinatarios de avisos:
  ver ADR-0018.
- **`roles.json` es la fuente de verdad.** Volver a aplicarlo pisa lo editado en `/admin` (ver ADR-0007). Además, la
  pantalla de roles de wasichai-ui borra las acciones declaradas de un rol al guardarlo, así que los roles se
  administran solo desde el repositorio ([security.md §4.1](../security.md), WAS-ADR-0042).

**Propuesto: alcance por proyecto.** Decisión pendiente del checkpoint con **recomendación A** (CON-041, GAP-025):

- **Opción A para el MVP con un solo cliente.** Lectura de todo el tenant, filtrada por objeto y campo; el filtro "mis
  proyectos" del portal es una comodidad, no una frontera. Escritura acotada por `ProjectScopeGuard` (`sgspe.scope`, F2)
  con `project_assignment` vigente, en altas, cambios, bajas, transiciones y vínculos de los objetos de un proyecto y sus
  hijos; cambiar el `project` de un registro exige asignación a ambos; quedan exentos `ADMIN`, la plataforma, las
  automatizaciones, el alta de un `project` y la escritura de `project_assignment` por quien puede formar equipos.
- **Opción B (UPS-01) antes de la producción con varios proyectos.** Un `RecordCriterion` por usuario y objeto en todas
  las rutas de lectura y en la escritura, con el proveedor en `sgspe.scope`; `ProjectScopeGuard` queda como segunda
  línea. Si UPS-01 llega antes, se adopta B sin quitar la guarda.
- Región y territorio no tienen fuente de datos en el modelo: `project_assignment` solo asigna proyectos. Sus criterios
  se agregan cuando se definan.

## Consecuencias

### Positivas

- La API genérica, `/admin`, los endpoints propios y el agente aplican los mismos permisos, porque el código propio
  escribe con `RecordService` como el usuario.
- Retirar una capacidad es quitar una acción en `roles.json`; `/api/auth/me/permissions` la informa al portal.

### Negativas y riesgos

- Con la opción A, un usuario lee los proyectos que no le corresponden; es aceptable en un piloto de un proyecto o con
  equipos que trabajan en todos, no con equipos separados ([architecture.md §14](../architecture.md), riesgo 1).
- Pocos `ADMIN` y conciencia de que los cambios de usuarios y roles no se auditan (UPS-02); separar la provisión de
  tenants es UPS-10.
- `/me/permissions` no informa capacidades de tenant: el portal las deduce (UPS-06).

### Seguimiento

- Checkpoint de F0: elegir A o B (CON-041, GAP-025) y aprobar los issues UPS-01, UPS-02, UPS-06 y UPS-10 (GAP-028).
- Pruebas de [security.md §14](../security.md): `TenantIsolationApiTest`, `RolesApiTest` y `test_apply_roles.py` (F1);
  matriz de permisos, permisos de campo, edición de lo propio, acciones declaradas, "Alcance, opción A" y destinatarios
  por rol (F2); "Alcance, opción B" con UPS-01; `ArchitectureTest` y `boundaries.test` sin literales de rol.

## Referencias

- Requisitos: RQ-SEC-002, RQ-SEC-003, RQ-SEC-004, RQ-SEC-005, RQ-SEC-006, RQ-SEC-007, RQ-SEC-008, RQ-SEC-010,
  RQ-SEC-017, RNF-MNT-005, CON-039, CON-041, CON-059, GAP-024, GAP-025, GAP-028, ASM-024, ASM-067, ASM-069, ASM-070,
  UPS-01, UPS-02, UPS-06, UPS-10.
- Documentos: [security.md](../security.md) §4, §5, §6, §7, §9, §14 y §15; [domain-model.md](../domain-model.md) §4.7 y
  §8.1.1; [platform-fit.md §2.5](../platform-fit.md); [gis.md §5.2](../gis.md) (compuerta de permisos).
- wasichai: WAS-ADR-0004 (*One physical table per Custom Object*), WAS-ADR-0010 (*Own JWT for the MVP, OIDC-ready*),
  WAS-ADR-0020 (*The caller can ask what they may do, and asking grants nothing*), WAS-ADR-0025 (*The core is extended
  through SPIs, never by knowing its modules*), WAS-ADR-0040 (*Append-only objects, a pre-write guard SPI, and api-only
  objects*), WAS-ADR-0042 (*An object declares its own actions, and they are granted, checked and listed like CRUD*).
- Fuentes: [PRM-01 §7], [USR-03], [USR-06].
