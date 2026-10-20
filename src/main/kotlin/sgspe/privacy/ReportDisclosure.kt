package sgspe.privacy

// el modelo de un nivel (especificación de F8 §3.2, deliverables.md §9): no sabe de tipos, solo de marcas. INTERNO resuelve
// las menciones al nombre (en los textos, los títulos, las fuentes y el texto alternativo de las fotos) y no quita nada.
// EXTERNO (§2.2.9, §2.2.10, deliverables.md §9.2): quita los párrafos y las tablas marcados, vacía las celdas marcadas
// (nombres de titular o emisor, números de registro o de comprobante), cita a las personas por cargo o GI-, deja solo las
// fotos sin personas identificables o con consentimiento (un vacío no entra) y nunca la imagen de un comprobante, y toma la
// imagen EXTERNO de cada mapa. un bloque de fotos que queda vacío se quita. ningún tipo de F8 genera un documento PUBLICO;
// PUBLICO es el nivel de las presentaciones a la comunidad (ASM-146), y sale del modelo nominal por una lista permitida
object ReportDisclosure {
    fun apply(
        model: ReportModel,
        level: Disclosure
    ): ReportModel {
        if (level == Disclosure.PUBLICO) return public(model)
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
        val facts = model.facts?.filter { !external || it.mark == null }?.map { resolved(it, model.people, level) }
        return model.copy(
            meta = model.meta.copy(level = level.name),
            sections = sections,
            people = emptyMap(),
            photos = photos,
            maps = maps,
            facts = facts
        )
    }

    // PUBLICO (ASM-146, deliverables.md §16.4): una lista permitida que falla cerrada. quedan los títulos, los índices, los saltos
    // de sección, las fotos sin personas identificables y los hechos aptos y sin marca, con las celdas pequeñas reservadas; no
    // quedan párrafos, tablas, gráficos, mapas ni menciones. el consentimiento de una foto para un informe no cubre una asamblea
    private fun public(model: ReportModel): ReportModel {
        val level = Disclosure.PUBLICO
        val photos =
            model.photos
                .filterValues { it.kind == PhotoRef.PHOTO && it.identifiable == false }
                .mapValues { (_, photo) -> photo.copy(alt = Mentions.resolve(photo.alt, model.people, level)) }
        val sections =
            model.sections.map { section ->
                section.copy(
                    title = Mentions.resolve(section.title, model.people, level),
                    blocks =
                        section.blocks.mapNotNull { block ->
                            when (block) {
                                is Block.Heading -> block.copy(text = Mentions.resolve(block.text, model.people, level))
                                is Block.Photos ->
                                    block
                                        .copy(
                                            title = Mentions.resolve(block.title, model.people, level),
                                            evidence = block.evidence.filter { it in photos },
                                            source = block.source?.let { Mentions.resolve(it, model.people, level) }
                                        ).takeIf { it.evidence.isNotEmpty() }
                                is Block.SectionBreak, is Block.Toc, is Block.CaptionList -> block
                                else -> null
                            }
                        }
                )
            }
        val facts =
            model.facts
                ?.filter { it.forPublic && it.mark == null }
                ?.mapNotNull { publicFact(it) }
                ?.map { resolved(it, model.people, level) }
        return model.copy(
            meta = model.meta.copy(level = level.name, coverMap = null),
            sections = sections,
            people = emptyMap(),
            photos = photos,
            maps = emptyMap(),
            facts = facts
        )
    }

    // un hecho en PUBLICO, o null si no puede salir: un conteo pasa por SmallCells sobre su grilla (sin filas o con una fila sin
    // conteo no sale); un promedio o un valor sin al menos MIN casos sale reservado; un monto, tal cual; otra clase, nunca
    private fun publicFact(fact: Fact): Fact? =
        when (fact.kind) {
            Fact.COUNT -> {
                if (fact.rows.isEmpty() || fact.rows.any { it.count == null }) {
                    null
                } else {
                    val shown = SmallCells.apply(fact.rows.map { it to it.count!! })
                    fact.copy(rows = shown.map { (row, count) -> if (count is Int) FactRow(row.labels, count = count) else reserved(row) })
                }
            }
            Fact.MEAN, Fact.VALUE ->
                fact.copy(
                    rows =
                        fact.rows.map { row ->
                            if (row.n == null || row.n < SmallCells.MIN || row.value == null) reserved(row) else row.copy(count = null, reserved = null)
                        }
                )
            Fact.AMOUNT -> fact.copy(rows = fact.rows.map { it.copy(count = null, n = null, reserved = null) })
            else -> null
        }

    private fun reserved(row: FactRow) = FactRow(row.labels, unit = row.unit, reserved = true)

    private fun resolved(
        fact: Fact,
        people: Map<String, MentionTarget>,
        level: Disclosure
    ): Fact =
        fact.copy(
            title = Mentions.resolve(fact.title, people, level),
            dimensions = fact.dimensions.map { Mentions.resolve(it, people, level) },
            source = fact.source?.let { Mentions.resolve(it, people, level) },
            rows = fact.rows.map { row -> row.copy(labels = row.labels.map { Mentions.resolve(it, people, level) }) }
        )

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
                // la nota EXTERNO solo sale en EXTERNO; cualquier otra marca se quita ahí (falla cerrado)
                if (block.mark?.kind == Mark.EXTERNAL_NOTE && !external) {
                    null
                } else if (external && block.mark != null && block.mark.kind != Mark.EXTERNAL_NOTE) {
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
            // el índice y las listas se arman con los títulos y las leyendas ya transformados
            is Block.Toc -> block
            is Block.CaptionList -> block
            is Block.LineChart ->
                block.copy(
                    title = Mentions.resolve(block.title, people, level),
                    categories = block.categories.map { Mentions.resolve(it, people, level) },
                    series = block.series.map { it.copy(name = Mentions.resolve(it.name, people, level)) },
                    source = block.source?.let { Mentions.resolve(it, people, level) }
                )
            is Block.ScatterChart ->
                block.copy(
                    title = Mentions.resolve(block.title, people, level),
                    xAxis = Mentions.resolve(block.xAxis, people, level),
                    yAxis = Mentions.resolve(block.yAxis, people, level),
                    points = block.points.map { it.copy(label = Mentions.resolve(it.label, people, level)) },
                    source = block.source?.let { Mentions.resolve(it, people, level) }
                )
        }
    }
}
