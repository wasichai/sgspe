# ADR-0024: Interfaz: portal en `/` y administración en `/admin`

## Estado

Aceptado · 2026-10-07

## Contexto

- El usuario pidió construir sobre wasichai-ui "tal como lo hicieron srtm-backend y srtm-ui", con caja-ui como plantilla
  vigente (`@wasichai/*` 0.5.0) [USR-03].
- wasichai-ui ya decidió cómo se arma una interfaz: `WasichaiApp` es la consola de administración y diseño, y cada módulo se
  conecta a un registro [WAS-ADR-0028]; una ruta de un módulo no instalado responde `404` y la interfaz lo trata como "no
  instalado", y un `401` a mitad de sesión vuelve al login [WAS-ADR-0031]; el cliente puede preguntar qué puede hacer sin que
  eso le conceda nada [WAS-ADR-0020].
- El prompt pide una plataforma con aspecto enterprise, organizada por dominio y con una navegación principal de 13 secciones en
  un orden fijo [PRM-01 §13, §14] (RNF-USA-001), y que el portal muestre solo lo que el usuario puede usar (RF-IAM-002).
- Los flujos del equipo social atraviesan varios objetos (una reunión con participantes, preguntas y compromisos; una QCS que
  se reclasifica; un entregable con secciones), y muchos pasan por comandos de `/api/sgspe/**` (ADR-0008).
- Los repositorios los decide ADR-0010: la web vive en `sgspe-ui`.

## Opciones consideradas

### Solo `WasichaiApp`: toda la interfaz desde la metadata

- Ventajas: cero pantallas propias; vistas, formularios y páginas configurables en `/admin`.
- Desventajas: no tiene las 13 secciones del prompt ni pantallas de varios objetos; no pide el motivo de cambio (UPS-08), así que
  no puede escribir los objetos `requiresReason`; no llama a los comandos de `/api/sgspe/**`.

### Solo un portal propio, con la administración reimplementada

- Ventajas: una sola experiencia.
- Desventajas: reescribe usuarios, roles, permisos, auditoría, modelo y diseñadores que wasichai ya trae; cada versión de la
  plataforma obligaría a reponerlos (RNF-MNT-005).

### Dos aplicaciones en orígenes distintos

- Ventajas: despliegues independientes.
- Desventajas: dos logins o un token compartido entre orígenes, CORS y doble CSP; contradice el origen único de ADR-0026.

### Una aplicación Vite con dos partes: portal en `/` y `WasichaiApp` en `/admin` (elegida)

- Ventajas: es el patrón de caja-ui y srtm-ui; un solo login y un solo despliegue; el portal cubre los flujos principales y
  `/admin` lo de baja frecuencia (ASM-020).
- Desventajas: dos estilos de pantalla conviven; hay que decidir, por objeto, qué se hace en cada parte.

## Decisión

- **Una aplicación, dos partes** ([architecture.md §9.1](../architecture.md)): `src/main.tsx` elige según la ruta.

  | Parte | Ruta | Qué es | Para qué |
  |---|---|---|---|
  | Portal | `/` | Aplicación propia con `WasichaiProviders`, registro de módulos, i18n, TanStack Query y un router de datos, construida con `@wasichai/ui` | Los flujos principales del equipo social |
  | Consola de administración | `/admin` | `WasichaiApp` con `basename: '/admin'` y los módulos de los starters instalados, salvo el del asistente | Usuarios, roles, permisos, auditoría, modelo, catálogos y objetos de baja frecuencia |

- **Un solo login:** el mismo cliente (`apiBaseUrl: '/api'`) y el mismo `storagePrefix` `sgspe`, así que comparten el token
  `sgspe.token`, el tema y el idioma; la autenticación la decide ADR-0020.
- **Navegación del portal:** las 13 secciones del prompt en su orden, con etiquetas en español; una hoja se dibuja solo si tiene
  pantalla registrada y el usuario tiene sus permisos. Mientras no exista UPS-06, el portal deduce las capacidades de
  `GET /api/auth/me/permissions`. La sección Administración enlaza a `/admin` y solo la ve quien administra.
- **Las escrituras críticas se hacen en el portal,** que pide el motivo y lo envía en `X-Change-Reason`, porque `/admin` no lo
  pide todavía (UPS-08, RQ-AUD-002).
- **`/admin` no es fuente de verdad del modelo ni de los roles:** el repositorio lo es (ADR-0007, GAP-030); y no registra un
  módulo cuyo starter no está instalado, ni `agentModule()`.
- **Llegada por fase:** las secciones aparecen con su módulo, de F1 (bienvenida y Administración) a F8
  ([architecture.md §9.2](../architecture.md)); los paquetes `@wasichai/*` se agregan en la fase que los usa (§9.3).
- **Bibliotecas:** las versiones de caja-ui ([platform-fit.md §6](../platform-fit.md)); MapLibre y ECharts cargados de forma
  diferida solo en sus rutas (ADR-0016); React Hook Form y Zod desde los primeros formularios propios, en F2.
- **Idioma:** español por defecto e inglés, con números y fechas es-PE; las etiquetas de la metadata existen solo en español
  (RNF-I18N-001, CON-051), según ADR-0009.
- **Desarrollo:** Vite en el puerto 5182 reenvía `/api` al backend. En producción el proxy sirve `dist` con fallback de SPA,
  incluido `/admin/*`; ese proxy es la parte Propuesta de ADR-0026, que rige al responder GAP-022, y en F1 el fallback solo
  se verifica en desarrollo, hasta donde lo permite el servidor de Vite.

## Consecuencias

### Positivas

- Lo que wasichai ya resuelve (usuarios, roles, auditoría, modelo) no se reescribe, y mejora con cada versión de la plataforma.
- El equipo social trabaja en pantallas pensadas para su dominio, con los comandos y el motivo que exige la auditoría.
- Un solo origen, un solo login y un solo artefacto que desplegar.

### Negativas y riesgos

- Dos experiencias de uso: la consola de administración se ve distinta del portal.
- Mientras exista UPS-08, editar en `/admin` un objeto `requiresReason` falla con `400`.
- Editar roles en `/admin` borra sus acciones declaradas al guardar [WAS-ADR-0042]; los roles se administran solo desde el
  repositorio ([security.md §4.1](../security.md)).

### Seguimiento

- Abiertos: UPS-06, UPS-08, GAP-030, ASM-020. UPS-13 (mapa base en `MapView`) está resuelta con wasichai-ui#38 y la
  adopta ASM-125.
- Aceptado desde F1; cada fase agrega sus pantallas.
- Riesgo abierto: el fallback de SPA y la CSP del proxy de producción dependen de GAP-022 (ADR-0026).
- Pruebas ([architecture.md §13](../architecture.md)): Vitest, Testing Library y `mockFetch` para componentes, navegación por
  permisos e i18n; `boundaries.test` sin literales de rol ([security.md §14](../security.md)); prueba de humo del agente en F1
  (arranque, login, roles y `/admin`) y Playwright desde F2 (ASM-066).

## Referencias

- Requisitos: RF-IAM-001, RF-IAM-002, RNF-USA-001, RNF-I18N-001, RNF-MNT-001, RNF-MNT-005, RNF-TST-001, RQ-AUD-002; CON-051;
  UPS-06, UPS-08, UPS-13 (resuelta); GAP-022, GAP-030; ASM-020, ASM-066, ASM-125.
- Documentos: [architecture.md §4, §9, §11 y §13](../architecture.md), [platform-fit.md §2.11 y §6](../platform-fit.md),
  [security.md §3.1 y §4.1](../security.md).
- ADR de SGSPE: ADR-0007, ADR-0008, ADR-0009, ADR-0010, ADR-0016, ADR-0020, ADR-0026.
- WAS-ADR-0020 (The caller can ask what they may do, and asking grants nothing), WAS-ADR-0028 (Frontend modules plug into a
  registry), WAS-ADR-0031 (Where wasichai deliberately behaves differently from the original), WAS-ADR-0042 (An object
  declares its own actions, and they are granted, checked and listed like CRUD).
- Fuentes: [USR-03], [USR-08], [PRM-01 §13, §14].
