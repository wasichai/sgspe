package sgspe.privacy

// el modelo de un nivel (especificación de F8 §3.2, deliverables.md §9): no sabe de tipos, solo de marcas. INTERNO resuelve
// las menciones al nombre (en los textos, los títulos, las fuentes y el texto alternativo de las fotos) y no quita nada.
// EXTERNO (§2.2.9, §2.2.10, deliverables.md §9.2): quita los párrafos y las tablas marcados, vacía las celdas marcadas
// (nombres de titular o emisor, números de registro o de comprobante), cita a las personas por cargo o GI-, deja solo las
// fotos sin personas identificables o con consentimiento (un vacío no entra) y nunca la imagen de un comprobante, y toma la
// imagen EXTERNO de cada mapa. un bloque de fotos que queda vacío se quita. ningún tipo de F8 prepara PUBLICO: pedirlo es un
// error del programa, no un modelo vacío
object ReportDisclosure {
    fun apply(
        model: ReportModel,
        level: Disclosure
    ): ReportModel {
        require(level != Disclosure.PUBLICO) { "Ningún tipo de entregable de F8 prepara PUBLICO" }
        val external = level == Disclosure.EXTERNO
        // el texto alternativo también cita personas: se resuelve como el resto del texto
        val photos =
            (if (external) model.photos.filterValues(::publishable) else model.photos).mapValues { (_, photo) ->
                photo.copy(alt = Mentions.resolve(photo.alt, model.people, level))
            }
        val maps =
            model.maps.mapValues { (_, images) ->
                if (external) ReportMapImages(images.external, images.external) else ReportMapImages(images.internal, images.internal)
            }
        val sections =
            model.sections.map { section ->
                section.copy(blocks = section.blocks.mapNotNull { block(it, model.people, level, photos.keys) })
            }
        return model.copy(
            meta = model.meta.copy(level = level.name),
            sections = sections,
            people = emptyMap(),
            photos = photos,
            maps = maps
        )
    }

    // una foto entra en EXTERNO si no muestra personas identificables o tiene consentimiento; un comprobante, nunca (ASM-037)
    fun publishable(photo: PhotoRef): Boolean = photo.kind != PhotoRef.RECEIPT && (photo.identifiable == false || photo.consent == true)

    private fun block(
        block: Block,
        people: Map<String, MentionTarget>,
        level: Disclosure,
        photos: Set<String>
    ): Block? {
        val external = level == Disclosure.EXTERNO
        return when (block) {
            is Block.Paragraph ->
                if (external && block.mark != null) {
                    null
                } else {
                    block.copy(
                        markdown = Mentions.resolve(block.markdown, people, level),
                        footnotes = block.footnotes.map { Mentions.resolve(it, people, level) }
                    )
                }
            is Block.Table ->
                if (external && block.mark != null) {
                    null
                } else {
                    block.copy(
                        title = Mentions.resolve(block.title, people, level),
                        header = block.header.map { Mentions.resolve(it, people, level) },
                        source = block.source?.let { Mentions.resolve(it, people, level) },
                        rows =
                            block.rows.map { row ->
                                row.map { cell ->
                                    if (external && cell.mark != null) cell.copy(text = "") else cell.copy(text = Mentions.resolve(cell.text, people, level))
                                }
                            }
                    )
                }
            is Block.Photos ->
                block
                    .copy(
                        title = Mentions.resolve(block.title, people, level),
                        evidence = block.evidence.filter { it in photos },
                        source = block.source?.let { Mentions.resolve(it, people, level) }
                    ).takeIf { it.evidence.isNotEmpty() }
            is Block.Heading -> block.copy(text = Mentions.resolve(block.text, people, level))
            is Block.MapFigure ->
                block.copy(
                    title = Mentions.resolve(block.title, people, level),
                    source = block.source?.let { Mentions.resolve(it, people, level) }
                )
            is Block.SectionBreak -> block
        }
    }
}
