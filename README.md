# sgspe

SGSPE es un **Sistema de Gestión Social para Proyectos Extractivos**. Sirve a un equipo social para gestionar en un
solo lugar el territorio, las áreas de influencia, los grupos de interés y el relacionamiento con ellos. También cubre
los compromisos, las quejas, consultas y solicitudes, los riesgos y alertas, y los programas sociales. Ofrece vistas en
GIS, tableros y entregables DOCX y PDF.

## Repositorios

- **Este repositorio (`sgspe`):** el backend en Kotlin sobre la plataforma
  [wasichai](https://github.com/wasichai/wasichai) y toda la documentación de diseño.
- **[`sgspe-ui`](https://github.com/wasichai/sgspe-ui):** la interfaz web. Tiene un portal en `/` y la administración
  de wasichai en `/admin`.

## Estado

**Fase 0, descubrimiento y diseño:** terminada y pendiente de revisión en el checkpoint.

- El diseño está en [`docs/`](docs/README.md): requisitos, dominio, arquitectura, API, datos, workflows, seguridad,
  GIS, entregables, tableros, IA, ADRs, trazabilidad y roadmap.
- El código empieza en la Fase 1 (Foundation), después del checkpoint. El plan está en
  [`docs/roadmap.md`](docs/roadmap.md).

## Documentación

- [Índice de la documentación](docs/README.md)
- [Decisiones de arquitectura](docs/adr/README.md)
- [Historia de cambios](docs/HISTORY.md)
