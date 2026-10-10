# Documentación de SGSPE

SGSPE es un Sistema de Gestión Social para Proyectos Extractivos. Organiza la relación de la empresa de un proyecto
extractivo (minería, hidrocarburos o energía) con su entorno social, en una sola herramienta:

- territorio y áreas de influencia;
- grupos de interés y relacionamiento;
- compromisos, quejas, consultas y solicitudes;
- riesgos y alertas;
- programas sociales;
- GIS, tableros y entregables DOCX/PDF.

Se construye sobre la plataforma [wasichai](https://github.com/wasichai/wasichai), con interfaz en
[`sgspe-ui`](https://github.com/wasichai/sgspe-ui).

Esta carpeta nació en la **Fase 0 (descubrimiento y diseño)**, sin código. La **Fase 1 (fundación)** agregó el código en
la raíz del repositorio; su guía de desarrollo está en [`develop/README.md`](develop/README.md) y su historia en
[`HISTORY.md`](HISTORY.md).

## Cómo leer

1. [`requirements-analysis.md`](requirements-analysis.md) es la fuente de verdad. Contiene las fuentes y su autoridad,
   los requisitos (RF, RNF, RN, RQ-*), las contradicciones (CON), los vacíos (GAP), los supuestos (ASM) y las brechas de
   wasichai (UPS). Es el único lugar donde se definen IDs.
2. [`glossary.md`](glossary.md) da los términos en español y su nombre técnico en inglés.
3. [`domain-model.md`](domain-model.md) describe los contextos delimitados, los 89 objetos, las reglas, las guardas, los
   campos derivados, los paquetes y los trabajos.
4. A partir de ahí, se lee el documento del tema que interese:

| Documento | Contenido |
|---|---|
| [`architecture.md`](architecture.md) | Arquitectura sobre wasichai: paquetes `sgspe.*`, aprovisionamiento, eventos, trabajos y despliegue |
| [`platform-fit.md`](platform-fit.md) | Qué da wasichai, qué se construye en SGSPE y qué falta upstream |
| [`api.md`](api.md) | API genérica de wasichai y API del portal `/api/sgspe/**` |
| [`database-model.md`](database-model.md) | Modelo de metadata: fichas por objeto, relaciones, índices, banderas y migraciones |
| [`workflows.md`](workflows.md) | Workflows, transiciones, guardas, avisos, escalamientos y trabajos programados |
| [`security.md`](security.md) | Roles, matriz de permisos, permisos de campo, alcance por proyecto y datos personales |
| [`gis.md`](gis.md) | Capas, CRS, importación con reconciliación, mapas y GeoServer |
| [`deliverables.md`](deliverables.md) | Entregables DOCX/PDF, niveles de divulgación y emisión congelada |
| [`dashboards.md`](dashboards.md) | Catálogo de KPIs y tableros |
| [`ai-architecture.md`](ai-architecture.md) | IA con validación humana y seudonimización |
| [`ux/`](ux/README.md) | Diseño UX/UI del portal: arquitectura de UX, navegación contextual, design system, tokens y, por etapas, pantallas y flujos |
| [`requirements-traceability.md`](requirements-traceability.md) | Matriz requisito–fuente–módulo–entidad–API–UI–prueba |
| [`roadmap.md`](roadmap.md) | Fases F0–F8, plan de la Fase 1 y decisiones pendientes del checkpoint |
| [`develop/README.md`](develop/README.md) | Desarrollo local: base en el VPS, túnel, arranque, modelo y pruebas |
| [`develop/releasing.md`](develop/releasing.md) | Releases con release-please: versiones, jar adjunto y secreto del repositorio |
| [`adr/`](adr/README.md) | Decisiones de arquitectura (ADR-0001 a ADR-0026) |
| [`HISTORY.md`](HISTORY.md) | Historia de cambios |
| [`superpowers/plans/`](superpowers/plans/2026-10-06-sgspe-fase-0-y-1.md) | Plan aprobado de las fases 0 y 1, y planes ejecutables de las fases 0 y 1 |

## Convenciones

- **Idioma.** Los documentos, las etiquetas y los valores de ENUM están en español. Los nombres técnicos están en
  inglés: objetos, campos, estados, roles, acciones, paquetes y rutas ([ADR-0009](adr/0009-nombres-tecnicos-en-ingles.md)).
- **IDs.** Se definen solo en `requirements-analysis.md` y los demás documentos los citan. No se usan rangos.
- **Citas de fuente.**
  - `[DOC-01 §x]` para el diagnóstico social de ejemplo.
  - `[DOC-02 p.x]` para el informe de campo de ejemplo.
  - `[DOC-03]` para la captura de ArcMap.
  - `[PRM-01 §x]` para el prompt maestro.
  - `[USR-0x]` para los pedidos del usuario.
  - `WAS-ADR-00NN` para los ADR de wasichai.
- **Supuestos.** Lo que no tiene fuente se marca como `**ASSUMPTION (ASM-NNN):**` y dice cómo se revierte.
- **Fuentes.** Las fuentes se citan pero no se versionan: los documentos de ejemplo y los insumos del prompt son
  transitorios.
- **Anonimización.** Ningún documento versionado contiene nombres de personas reales, documentos de identidad,
  teléfonos ni números de partida o de comprobante. Se usan cargos y nombres ficticios. Una verificación automática
  compara lo versionado con la lista de nombres de las fuentes, que vive fuera del repositorio.

## Estado de la Fase 0

| Paso | Estado |
|---|---|
| Análisis de requisitos, glosario y modelo de dominio | Escritos y revisados |
| Arquitectura, API, datos, workflows, seguridad, GIS, entregables, IA y tableros | Escritos, revisados y conciliados |
| ADR-0001 a ADR-0026 | Escritos y revisados |
| Trazabilidad y roadmap | Escritos |
| Validación de consistencia | Ver "Validación de la Fase 0" en el [roadmap](roadmap.md) |
| Checkpoint con el usuario | Pendiente |
| Diseño UX/UI ([`ux/`](ux/README.md)) | Etapas 1 y 2 de 3 entregadas (arquitectura de UX, navegación, design system; Project 360, territorio, grupos de interés, relacionamiento y compromisos, con fichas de pantalla y flujos); etapa 3 pendiente |
