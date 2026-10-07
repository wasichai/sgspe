# Historia de cambios

La entrada más reciente va primero. El razonamiento de arquitectura está en [`adr/`](adr/README.md); este archivo
registra lo entregado.

## 2026-10-07 — Fase 0: descubrimiento y diseño

La Fase 0 entrega el diseño completo de SGSPE, sin código, sobre wasichai 0.3.2 (backend) y `@wasichai/*` 0.5.0 (UI).

### Fuentes y requisitos

- **Fuentes analizadas.** Se citan por su ID y no se versionan:
  - DOC-01: diagnóstico social de áreas de influencia de un proyecto de exploración, 2018;
  - DOC-02: informe de trabajo de campo y avance, 2026;
  - DOC-03: captura de ArcMap con sondajes, AID y predios;
  - PRM-01: el prompt maestro;
  - USR: los pedidos del usuario.
- **Pedidos del usuario incorporados** (USR):
  - generar entregables como DOC-01 (DOCX) y DOC-02 (PDF);
  - ver la situación en GIS y en tableros;
  - construir sobre wasichai como srtm-backend y srtm-ui;
  - nombres técnicos en inglés;
  - anonimizar los datos personales.
- **[`requirements-analysis.md`](requirements-analysis.md):** 480 IDs, entre ellos:
  - requisitos funcionales, no funcionales, reglas y RQ de GIS, entregables, tableros, seguridad, auditoría e IA;
  - 60 contradicciones, 32 vacíos, 72 supuestos y 18 brechas de wasichai (UPS).
  - [`glossary.md`](glossary.md) da la equivalencia español–inglés.

### Diseño

- **Dominio.** [`domain-model.md`](domain-model.md) define 20 contextos delimitados y 89 objetos, con sus reglas, guardas,
  campos derivados, listeners, paquetes `sgspe.*` y trabajos programados. Es la base de los demás documentos:
  - [`architecture.md`](architecture.md), [`platform-fit.md`](platform-fit.md) y [`api.md`](api.md): arquitectura sobre
    wasichai, encaje con la plataforma y API del portal `/api/sgspe/**`;
  - [`database-model.md`](database-model.md): modelo de metadata (fichas de objeto, relaciones, índices, banderas y
    migraciones);
  - [`workflows.md`](workflows.md): workflows WF-A a WF-P, destinatarios por rol, escalamiento y JOB-01 a JOB-08;
  - [`security.md`](security.md): 17 roles, matriz de permisos y de campos, alcance por proyecto y datos personales;
  - [`gis.md`](gis.md): capas LYR-01 a LYR-24, SRID 4326 con CRS UTM por proyecto, importación con reconciliación y
    GeoServer;
  - [`deliverables.md`](deliverables.md): entregables DOCX primero, PDF por Gotenberg, niveles de divulgación y emisión
    congelada;
  - [`dashboards.md`](dashboards.md): catálogo de KPIs calculados en vivo y tableros;
  - [`ai-architecture.md`](ai-architecture.md): IA con validación humana, lista cerrada de aceptación y
    seudonimización.
- **Decisiones.** [`adr/`](adr/README.md) registra ADR-0001 a ADR-0026. Las decisiones de plataforma se citan como
  `WAS-ADR-00NN`.
- **Trazabilidad y plan.** [`requirements-traceability.md`](requirements-traceability.md) cubre 298 requisitos y
  [`roadmap.md`](roadmap.md) las fases F0 a F8, el plan de la Fase 1 y las decisiones del checkpoint.
- **Planes.** [`superpowers/plans/`](superpowers/plans/2026-10-06-sgspe-fase-0-y-1.md) guarda el plan aprobado de las
  fases 0 y 1 y el plan ejecutable de la Fase 0.

### Proceso

- Cada documento lo escribió un subagente y lo revisó otro.
- Los documentos derivados se escribieron en paralelo y se conciliaron entre sí.
- Una verificación automática comprueba que:
  - los IDs, las secciones, los objetos, los roles y los enlaces sean coherentes;
  - no aparezca ningún nombre real de las fuentes;
  - no se versione ningún insumo transitorio.
- La Fase 1 empieza solo después del checkpoint con el usuario.
