package sgspe.deliverable

import sgspe.privacy.Disclosure
import sgspe.privacy.ReportModel
import java.io.ByteArrayInputStream
import java.util.Base64
import javax.imageio.ImageIO

// el planificador de una presentación (deliverables.md §16.3 y §16.5): del modelo ya divulgado para un nivel y de un público,
// la especificación. puro y determinista: los mismos bytes del modelo, el mismo público y las mismas versiones dan los mismos
// bytes de la especificación (RQ-REP-037). arma el arco del público con las historias de DeckMaterial, una tesis con el
// hallazgo más importante, las conclusiones con los hallazgos calculables y el texto que escribió el equipo, y las fuentes y
// los límites; mide cada título con las fuentes de las láminas y, si no cabe, usa el corto. nunca encoge la letra
object DeckPlanner {
    // sube con todo cambio que mueva una especificación ya generada (deliverable_presentation.planner_version)
    const val VERSION = "1"

    class NotAvailable(
        val code: String
    ) : RuntimeException(code)

    data class Input(
        val model: ReportModel,
        val level: Disclosure,
        val audience: Audience,
        val photoBytes: Map<String, ByteArray> = emptyMap(),
        val logo: ByteArray? = null
    )

    fun plan(input: Input): DeckSpec {
        val audience = input.audience
        val model = input.model
        if (!audience.admits(input.level)) throw NotAvailable("AUDIENCE_LEVEL_MISMATCH")
        if (model.meta.level != input.level.name) throw NotAvailable("LEVEL_NOT_APPLIED")
        val material = DeckMaterial(model, input.level, input.photoBytes)
        val stories = material.stories()
        if (stories.isEmpty()) throw NotAvailable(if (input.level == Disclosure.PUBLICO) "PUBLIC_NOT_AVAILABLE" else "PRESENTATION_NOT_AVAILABLE")
        val assets = linkedMapOf<String, DeckAsset>()
        val logo =
            input.logo
                ?.let { logoAsset(it) }
                ?.also { assets["logo"] = it }
                ?.let { "logo" }
        val chosen = stories.filter { it.weight(audience) > 0 }
        val ranked = chosen.sortedWith(compareByDescending<DeckStory> { it.weight(audience) }.thenBy { it.id })
        val thesis = ranked.firstOrNull { it.finding != null }
        val slides = mutableListOf<DeckSlide>()
        slides += cover(model, audience)
        val parts =
            audience.arc.map { beat ->
                val chapter = audience.chapter(beat)
                beat to
                    when (beat) {
                        Beat.INTRO -> listOf(intro(chapter))
                        Beat.THESIS -> listOfNotNull(thesis?.let { thesisSlide(it, ranked, audience, chapter) })
                        Beat.NEXT -> conclusions(material, chosen, ranked, thesis, audience, chapter)
                        Beat.GLOSSARY, Beat.LIMITS -> emptyList()
                        else ->
                            ranked
                                .filter { it.beat == beat }
                                .take(audience.perBeat)
                                .flatMap { story -> story.build(audience, chapter).map { titled(it, story, audience) } }
                    }
            }
        // los separadores abren las partes de dos o más láminas, y solo si hay al menos tres de ellas: en una presentación corta,
        // un separador por lámina corta el ritmo
        val long = parts.count { (beat, part) -> beat != Beat.THESIS && part.size >= 2 }
        var number = 0
        for ((beat, part) in parts) {
            if (part.isEmpty()) continue
            if (audience.separators && long >= 3 && beat != Beat.THESIS && part.size >= 2) {
                number++
                val chapter = audience.chapter(beat)
                slides += DeckSlide("parte-$number", "Abrir la parte «$chapter»", chapter, SlideData.Section("$number"))
            }
            slides += part
        }
        if (Beat.GLOSSARY in audience.arc) glossary(slides, audience)?.let { slides += it }
        if (Beat.LIMITS in audience.arc) slides += sources(model, chosen, slides.toList(), input.level, audience)
        slides += closing(model, audience)
        assets += material.assets.filterKeys { id -> slides.any { it.data.uses(id) } }
        return DeckSpec(meta(model, input.level, audience), DeckTheme(accent(model.meta.brand?.accent), "#FFFFFF", logo), assets, unique(slides))
    }

    private fun SlideData.uses(id: String): Boolean =
        when (this) {
            is SlideData.MapView -> image == id
            is SlideData.Photos -> id in images
            is SlideData.Cover -> image == id
            else -> false
        }

    // un id repetido (dos historias con la misma lámina) se numera: el esquema exige ids únicos
    private fun unique(slides: List<DeckSlide>): List<DeckSlide> {
        val seen = mutableMapOf<String, Int>()
        return slides.map { slide ->
            val n = seen.merge(slide.id, 1, Int::plus)!!
            if (n == 1) slide else slide.copy(id = "${slide.id}-$n")
        }
    }

    private fun face(audience: Audience): DeckDesign.Face =
        when (audience) {
            Audience.INVESTIGACION -> DeckDesign.Face.SERIF
            Audience.INSTITUCIONES -> DeckDesign.Face.SANS_BOLD
            else -> DeckDesign.Face.DISPLAY
        }

    fun titleFits(
        title: String,
        audience: Audience,
        lines: Int = 2,
        size: Int = DeckDesign.scale(audience).title
    ): Boolean = DeckDesign.fits(title, face(audience), size, DeckDesign.TITLE_WIDTH, lines)

    // el título afirmativo si cabe en dos líneas; si no, el corto de la historia
    private fun titled(
        slide: DeckSlide,
        story: DeckStory,
        audience: Audience
    ): DeckSlide = if (titleFits(slide.title, audience)) slide else slide.copy(title = story.shortTitle)

    private fun period(model: ReportModel): String? {
        val start = model.meta.periodStart?.let { DeckFormat.month(it.take(7), capital = true) }
        val end = model.meta.periodEnd?.let { DeckFormat.month(it.take(7), year = true, capital = false) }
        if (start == null || end == null) return null
        // un periodo de un solo mes: «setiembre de 2026», no «setiembre a setiembre»
        return if (model.meta.periodStart?.take(7) == model.meta.periodEnd?.take(7)) end.replaceFirstChar(Char::uppercase) else "$start a $end"
    }

    private fun meta(
        model: ReportModel,
        level: Disclosure,
        audience: Audience
    ): DeckMeta =
        DeckMeta(
            code = model.meta.code,
            title = model.meta.title,
            project = model.meta.project,
            revision = model.meta.issueRevision,
            level = level.name,
            audience = audience.name,
            dateLabel = model.meta.dateLabel,
            derivation = "Derivada de ${model.meta.code} rev. ${model.meta.issueRevision}, nivel ${level.name}; no es un documento emitido",
            period = period(model),
            organization = model.meta.preparedBy
        )

    private fun cover(
        model: ReportModel,
        audience: Audience
    ): DeckSlide {
        val title =
            if (audience == Audience.COMUNIDAD) {
                "Lo que encontramos en ${model.meta.project}"
            } else {
                model.meta.title
            }
        val subtitle =
            listOfNotNull(
                period(model)?.let { if (audience == Audience.COMUNIDAD) "Resultados de ${it.replaceFirstChar(Char::lowercase)}" else it },
                AUDIENCE_LINE[audience]
            ).joinToString(". ").ifEmpty { null }
        return DeckSlide(
            "portada",
            "Presentar el informe, su periodo y para quién es",
            title,
            SlideData.Cover(subtitle = subtitle, preparedFor = model.meta.preparedFor, preparedBy = model.meta.preparedBy)
        )
    }

    private val AUDIENCE_LINE =
        mapOf(
            Audience.DIRECCION to "Síntesis para la dirección",
            Audience.CAMPO to "Para el equipo de campo",
            Audience.INVESTIGACION to "Método, datos y límites",
            Audience.INSTITUCIONES to "Informe para las instituciones",
            Audience.COMUNIDAD to null
        )

    // la introducción para la comunidad: qué es este informe y cómo protege a las personas, con texto fijo
    private fun intro(chapter: String): DeckSlide =
        DeckSlide(
            "que-es",
            "Explicar qué es el informe y cómo protege a las personas",
            "Un informe sobre cómo se relaciona el proyecto con las comunidades",
            SlideData.Summary(
                listOf(
                    "Reúne lo que el equipo social registró en el periodo: visitas, reuniones y compromisos.",
                    "Solo muestra cifras de grupos: ninguna persona aparece con su nombre.",
                    "Cuando un grupo tiene menos de 5 casos, la cifra no se publica y dice «reservado»."
                )
            ),
            chapter
        )

    private fun thesisSlide(
        thesis: DeckStory,
        ranked: List<DeckStory>,
        audience: Audience,
        chapter: String
    ): DeckSlide {
        val scale = DeckDesign.scale(audience)
        val size = (scale.title * 1.2).toInt()
        // la tesis junta los dos hallazgos principales si caben en tres líneas; si no, el primero; si tampoco, su título corto
        val second = ranked.firstOrNull { it !== thesis && it.finding != null }?.finding
        val joined = second?.let { "${thesis.finding!!}, y ${it.replaceFirstChar(Char::lowercase)}" }
        val title =
            listOfNotNull(joined, thesis.finding).firstOrNull { titleFits(it, audience, 3, size) } ?: thesis.shortTitle
        val figures = (listOfNotNull(thesis.headline) + ranked.filter { it !== thesis }.mapNotNull { it.headline }).take(3)
        val support = if (thesis.limitations.any { it.contains("causa") }) "Los datos describen el periodo; no permiten afirmar qué lo causó." else null
        return DeckSlide("tesis", "Decir el hallazgo principal", title, SlideData.Thesis(support, figures), chapter, thesis.source)
    }

    // las conclusiones: lo que escribió el equipo (T2) o los hallazgos calculables, y los próximos pasos tal como los escribió
    // el equipo. ninguna recomendación sale del sistema
    private fun conclusions(
        material: DeckMaterial,
        chosen: List<DeckStory>,
        ranked: List<DeckStory>,
        thesis: DeckStory?,
        audience: Audience,
        chapter: String
    ): List<DeckSlide> {
        val findings = ranked.filter { it !== thesis || audience == Audience.COMUNIDAD }.mapNotNull { it.finding(audience) }.take(3)
        val points = material.reportConclusions.takeIf { it.isNotEmpty() && audience != Audience.COMUNIDAD }?.take(3) ?: findings
        val steps = if (audience == Audience.COMUNIDAD) emptyList() else chosen.filter { it.beat == Beat.NEXT }.flatMap { it.nextSteps }.take(3)
        if (points.isEmpty()) return emptyList()
        val title =
            when {
                audience == Audience.COMUNIDAD -> "Lo más importante"
                steps.isEmpty() -> "Lo que muestran los datos"
                else -> "Lo que muestran los datos y lo que sigue"
            }
        return columns(points, steps, audience).mapIndexed { i, (left, right) ->
            DeckSlide(
                if (i == 0) "conclusiones" else "conclusiones-${i + 1}",
                "Cerrar con lo que se puede concluir",
                if (i == 0) title else "$title (continúa)",
                SlideData.Conclusions(left, right),
                chapter
            )
        }
    }

    // reparte dos columnas de puntos en las láminas que hagan falta, midiendo cada punto con la letra del público: el alto de
    // una columna (src/layouts/content.tsx, Column) es su rótulo más cada punto con su relleno. nunca encoge la letra
    private fun columns(
        left: List<String>,
        right: List<String>,
        audience: Audience
    ): List<Pair<List<String>, List<String>>> {
        val scale = DeckDesign.scale(audience)
        val width = (DeckDesign.CONTENT_WIDTH - DeckDesign.span(1)) / 2
        val face = if (audience == Audience.INVESTIGACION) DeckDesign.Face.SERIF else DeckDesign.Face.SANS
        val available = 1080 - 104 - 76 - 40 - titleHeight(audience) - 40 - 8
        val label = 16 + scale.body * 1.25 + 3 + 24

        fun height(items: List<String>) = label + items.sumOf { 48 + DeckDesign.lines(it, face, scale.body, width) * scale.body * 1.34 }

        fun take(items: List<String>): Int {
            var n = items.size
            while (n > 1 && height(items.take(n)) > available) n--
            return n
        }
        val out = mutableListOf<Pair<List<String>, List<String>>>()
        var l = left
        var r = right
        while (l.isNotEmpty() || r.isNotEmpty()) {
            val a = take(l)
            val b = take(r)
            out += l.take(a) to r.take(b)
            l = l.drop(a)
            r = r.drop(b)
        }
        return out
    }

    // el alto del título de dos líneas de un público (src/components/Page.tsx, titleHeight)
    private fun titleHeight(audience: Audience): Double {
        val size = DeckDesign.scale(audience).title
        return if (audience == Audience.INSTITUCIONES) size * 1.14 * 2 + 28 else size * 1.12 * 2
    }

    // el glosario: los términos fijos que aparecen en las láminas, en palabras simples
    private fun glossary(
        slides: List<DeckSlide>,
        audience: Audience
    ): DeckSlide? {
        val text = slides.joinToString(" ") { "${it.title} ${it.data}" }.lowercase()
        val terms = GLOSSARY.filter { (term, _) -> term.lowercase() in text }.take(5).map { GlossaryTerm(it.first, it.second) }
        if (terms.isEmpty()) return null
        return DeckSlide(
            "glosario",
            "Explicar los términos técnicos",
            "Algunas palabras que usamos",
            SlideData.Glossary(terms),
            audience.chapter(Beat.GLOSSARY)
        )
    }

    private val GLOSSARY =
        listOf(
            "Área de influencia" to "Las localidades donde el proyecto puede cambiar algo en la vida diaria.",
            "Grupo de interés" to "Una organización o autoridad con la que el proyecto conversa.",
            "Compromiso" to "Algo que el proyecto prometió hacer, con una fecha.",
            "Confianza" to "Qué tanto confían en el proyecto las organizaciones evaluadas, de 1 (poca) a 5 (mucha).",
            "Interacción" to "Cada reunión, visita, taller o asamblea entre el proyecto y la comunidad.",
            "Reservado" to "Una cifra de menos de 5 casos, que no se publica para proteger a las personas."
        )

    private fun sources(
        model: ReportModel,
        chosen: List<DeckStory>,
        slides: List<DeckSlide>,
        level: Disclosure,
        audience: Audience
    ): List<DeckSlide> {
        val used = slides.mapNotNull { it.source }.distinct()
        // la comunidad no necesita el nombre de cada registro: una fuente en palabras simples, con el periodo
        val sources =
            if (audience == Audience.COMUNIDAD) {
                listOf("Los registros del equipo social del proyecto" + (period(model)?.let { ", de ${it.replaceFirstChar(Char::lowercase)}" } ?: "") + ".")
            } else {
                grouped(used)
            }
        val limitations =
            (
                chosen.filter { story -> slides.any { it.source == story.source } }.flatMap { it.limitations } +
                    listOfNotNull(
                        "Las cifras de menos de 5 casos se reservan para proteger a las personas.".takeIf { level == Disclosure.PUBLICO },
                        "Las personas se citan por su cargo, nunca por su nombre.".takeIf { level == Disclosure.EXTERNO }
                    )
            ).distinct()
        val title = if (audience == Audience.COMUNIDAD) "Lo que estos datos no dicen" else "De dónde salen estas cifras y qué no dicen"
        return columns(sources, limitations, audience).mapIndexed { i, (left, right) ->
            DeckSlide(
                if (i == 0) "fuentes" else "fuentes-${i + 1}",
                "Mostrar las fuentes y los límites",
                if (i == 0) title else "$title (continúa)",
                SlideData.Sources(left.ifEmpty { listOf("La versión emitida ${model.meta.code} rev. ${model.meta.issueRevision}.") }, right),
                audience.chapter(Beat.LIMITS)
            )
        }
    }

    // las fuentes del registro de SGSPE en una sola línea («Registro de SGSPE: a; b; c»): menos renglones, la misma información
    private fun grouped(sources: List<String>): List<String> {
        val prefix = "Registro de SGSPE: "
        val registry = sources.filter { it.startsWith(prefix) }.map { it.removePrefix(prefix) }
        if (registry.size < 2) return sources
        // en tramos de hasta 240 caracteres, el tope de una fuente en el esquema de las láminas
        val lines = mutableListOf<String>()
        var current = ""
        for (topic in registry) {
            val next = if (current.isEmpty()) prefix + topic else "$current; $topic"
            if (next.length + 1 > 240 && current.isNotEmpty()) {
                lines += "$current."
                current = prefix + topic
            } else {
                current = next
            }
        }
        lines += "$current."
        return lines + sources.filterNot { it.startsWith(prefix) }
    }

    private fun closing(
        model: ReportModel,
        audience: Audience
    ): DeckSlide {
        val (title, message) =
            when (audience) {
                Audience.COMUNIDAD ->
                    "Sus preguntas y comentarios" to
                        "Esta presentación resume el informe ${model.meta.code}. Pueden hacer sus preguntas ahora."
                else ->
                    "El informe completo" to
                        "La versión emitida ${model.meta.code} rev. ${model.meta.issueRevision} tiene el detalle, los anexos y la metodología."
            }
        return DeckSlide("cierre", "Cerrar y remitir al informe", title, SlideData.Closing(message, model.meta.preparedBy))
    }

    // el acento del tenant, oscurecido hasta que el blanco sobre él (y él sobre el papel) alcance 4.5:1 (WCAG); sin marca, el
    // de SGSPE
    fun accent(raw: String?): String {
        var hex = (raw?.uppercase()?.takeIf { Regex("^[0-9A-F]{6}$").matches(it) } ?: ReportDesign.ACCENT)
        repeat(40) {
            if (contrast(hex, "FFFFFF") >= 4.5) return "#$hex"
            val n = hex.toInt(16)
            hex = "%02X%02X%02X".format(((n shr 16) and 255) * 9 / 10, ((n shr 8) and 255) * 9 / 10, (n and 255) * 9 / 10)
        }
        return "#$hex"
    }

    fun contrast(
        a: String,
        b: String
    ): Double {
        fun channel(c: Int): Double = (c / 255.0).let { if (it <= 0.03928) it / 12.92 else Math.pow((it + 0.055) / 1.055, 2.4) }

        fun luminance(hex: String): Double {
            val n = hex.removePrefix("#").toInt(16)
            return 0.2126 * channel((n shr 16) and 255) + 0.7152 * channel((n shr 8) and 255) + 0.0722 * channel(n and 255)
        }
        val (x, y) = listOf(luminance(a), luminance(b)).sortedDescending()
        return (x + 0.05) / (y + 0.05)
    }

    // el logo como imagen incrustada: PNG o JPEG con sus dimensiones; otro formato no entra
    private fun logoAsset(bytes: ByteArray): DeckAsset? {
        val png = DeckMaterial.pngSize(bytes)
        if (png != null) return DeckAsset("image/png", png.first, png.second, "Logo de la organización", Base64.getEncoder().encodeToString(bytes))
        if (bytes.size < 3 || bytes[0] != 0xFF.toByte() || bytes[1] != 0xD8.toByte()) return null
        val image = runCatching { ImageIO.read(ByteArrayInputStream(bytes)) }.getOrNull() ?: return null
        return DeckAsset("image/jpeg", image.width, image.height, "Logo de la organización", Base64.getEncoder().encodeToString(bytes))
    }
}
