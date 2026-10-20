package sgspe.deliverable

import sgspe.privacy.Block
import sgspe.privacy.Disclosure
import sgspe.privacy.Fact
import sgspe.privacy.FactRow
import sgspe.privacy.PhotoRef
import sgspe.privacy.ReportModel
import java.math.BigDecimal
import java.util.Base64

// el material de una presentación (deliverables.md §16.3): lo que un modelo ya divulgado para un nivel permite contar, como
// historias. cada historia sale de un hecho tipado o de un bloque por su clave (typeVersion 2), dice a qué parte del relato
// pertenece, cuánto le importa a cada público y, si los datos lo permiten, su hallazgo: una afirmación calculable, nunca
// causal. no calcula indicadores nuevos: cuenta, ordena y compara lo que el modelo ya trae. en PUBLICO no suma ni deriva
// totales (ASM-088): usa las filas tal como las dejó SmallCells
data class DeckStory(
    val id: String,
    val beat: Beat,
    val weights: Map<Audience, Int>,
    val title: String,
    // el título corto, si el afirmativo no cabe en dos líneas
    val shortTitle: String,
    val finding: String? = null,
    // el mismo hallazgo en palabras simples, para la comunidad (sin decimales ni jerga); sin él, el de siempre
    val plain: String? = null,
    val headline: LabeledFigure? = null,
    val source: String? = null,
    val limitations: List<String> = emptyList(),
    val nextSteps: List<String> = emptyList(),
    val build: (Audience, String) -> List<DeckSlide>
) {
    fun weight(audience: Audience): Int = weights[audience] ?: 0

    fun finding(audience: Audience): String? = if (audience == Audience.COMUNIDAD) plain ?: finding else finding
}

class DeckMaterial(
    private val model: ReportModel,
    private val level: Disclosure,
    // los bytes de las fotos por id de evidencia, leídos por JOB-09 como los lee JOB-05
    private val photoBytes: Map<String, ByteArray> = emptyMap()
) {
    val assets = linkedMapOf<String, DeckAsset>()
    private val public = level == Disclosure.PUBLICO
    private val facts: Map<String, Fact> = model.facts.orEmpty().associateBy { it.key }

    private fun blocks(): Sequence<Pair<String, Block>> = model.sections.asSequence().flatMap { s -> s.blocks.asSequence().map { s.key to it } }

    private fun table(key: String): Block.Table? = blocks().map { it.second }.filterIsInstance<Block.Table>().firstOrNull { it.key == key }

    private fun period(fact: Fact): String = fact.period?.let { ", ${it.replaceFirstChar(Char::lowercase)}" }.orEmpty()

    // la fuente de una lámina: la del hecho o, sin ella, el registro de SGSPE con el título del hecho, y su periodo
    private fun source(fact: Fact): String = (fact.source ?: "Registro de SGSPE: ${fact.title.replaceFirstChar(Char::lowercase)}") + period(fact)

    // las historias del tipo del modelo; un tipo o una versión que no conoce no da ninguna (falla cerrado)
    fun stories(): List<DeckStory> =
        when {
            model.meta.typeVersion < 2 || model.facts == null -> emptyList()
            model.meta.type == SocialDiagnosisType.NAME -> diagnosis()
            model.meta.type == FieldReportType.NAME -> fieldReport()
            else -> emptyList()
        }

    private fun w(vararg pairs: Pair<Audience, Int>) = pairs.toMap()

    // --- historias comunes ---

    // la comparación de un conteo por categoría: las filas de mayor a menor, sin ceros salvo en PUBLICO (donde un cero ya es
    // «reservado» y no se distingue); lo reservado va al final y se dice cuántas categorías se reservaron
    private fun countComparison(
        fact: Fact,
        max: Int
    ): Pair<List<ComparisonItem>, String?> {
        val rows = fact.rows.filter { public || it.reserved == true || (it.count ?: 0) > 0 }
        val visible = rows.filter { it.reserved != true }.sortedByDescending { it.count ?: 0 }
        val reserved = rows.filter { it.reserved == true }
        val shown = visible.take(max)
        val items = shown.mapIndexed { i, row -> ComparisonItem(row.labels.joinToString(" · "), DeckFormat.figure(row), focus = i == 0) }
        val notes =
            listOfNotNull(
                (visible.size - shown.size).takeIf { it > 0 }?.let { "$it categorías más figuran en el informe." },
                reserved.size.takeIf { it > 0 }?.let { "$it categorías con menos de 5 casos se reservan." }
            ).joinToString(" ").ifEmpty { null }
        return items to notes
    }

    private fun maxOf(items: List<ComparisonItem>): String =
        items
            .mapNotNull { DeckFormat.number(it.figure.value) }
            .maxOrNull()
            ?.let { niceCeiling(it) }
            ?.toPlainString() ?: "1"

    // el tope del eje: el máximo redondeado hacia arriba a 1, 2, 2.5 o 5 por potencia de 10
    private fun niceCeiling(value: BigDecimal): BigDecimal {
        if (value.signum() <= 0) return BigDecimal.ONE
        val exponent = value.precision() - value.scale() - 1
        val power = BigDecimal.TEN.pow(maxOf(exponent, 0))
        return listOf("1", "2", "2.5", "5", "10").map { BigDecimal(it).multiply(power) }.first { it >= value }.stripTrailingZeros()
    }

    private fun comparisonSlide(
        id: String,
        story: String,
        purpose: String,
        title: String,
        fact: Fact,
        audience: Audience,
        unit: String,
        reading: String? = null
    ): DeckSlide? {
        val (items, note) = countComparison(fact, audience.maxItems)
        if (items.size < 2) return null
        return DeckSlide(id, purpose, title, SlideData.Comparison(unit, items, maxOf(items), reading), chapter = story, source = source(fact), note = note)
    }

    // --- T1 ---

    private fun diagnosis(): List<DeckStory> =
        listOfNotNull(
            coverage(),
            fieldworkByLocality(),
            trust(),
            shares(),
            interactions("interacciones_por_tipo", "Interacciones del periodo"),
            commitments(),
            risks(),
            riskList(),
            perceptions(),
            population(),
            map(DiagnosisStudy.AREAS_MAP, Beat.CONTEXT),
            monitoring(),
            method(),
            narrativeSteps("necesidades", "Necesidades que plantea el informe")
        )

    private fun localities(fact: Fact): List<String> {
        val index = fact.dimensions.indexOf("Localidad").takeIf { it >= 0 } ?: return emptyList()
        return fact.rows.mapNotNull { it.labels.getOrNull(index) }.distinct()
    }

    // los meses del periodo del informe, de su inicio a su fin
    private fun months(): Int? {
        val start =
            model.meta.periodStart
                ?.take(7)
                ?.let { runCatching { java.time.YearMonth.parse(it) }.getOrNull() } ?: return null
        val end =
            model.meta.periodEnd
                ?.take(7)
                ?.let { runCatching { java.time.YearMonth.parse(it) }.getOrNull() } ?: return null
        return (
            java.time.temporal.ChronoUnit.MONTHS
                .between(start, end) + 1
        ).toInt().takeIf { it > 0 }
    }

    private fun coverage(): DeckStory? {
        val fact = facts["trabajo_de_campo"] ?: return null
        val index = fact.dimensions.indexOf("Localidad")
        val all = localities(fact).filter { it != ReportFacts.NOT_AVAILABLE && it != ReportFacts.NO_DATA }
        if (all.isEmpty()) return null
        val total = all.size
        // fuera de PUBLICO: las localidades con al menos un instrumento aplicado y cuántos se aplicaron
        val visited = if (public) null else all.count { loc -> fact.rows.any { it.labels.getOrNull(index) == loc && (it.count ?: 0) > 0 } }
        val applied = if (public) null else fact.rows.sumOf { it.count ?: 0 }
        val interactions = if (public) null else facts["interacciones_por_tipo"]?.rows?.sumOf { it.count ?: 0 }
        val items =
            listOfNotNull(
                if (visited != null) {
                    LabeledFigure(DeckFigure(DeckFormat.count(visited), "$visited"), "Localidades con trabajo de campo", "De $total en las áreas de influencia")
                } else {
                    LabeledFigure(DeckFigure(DeckFormat.count(total), "$total"), "Localidades en las áreas de influencia")
                },
                applied?.let { LabeledFigure(DeckFigure(DeckFormat.count(it), "$it"), "Entrevistas y observaciones", "Instrumentos aplicados en el estudio") },
                // en PUBLICO, sin conteos de personas: los meses de registro, que no dicen nada de nadie
                months()?.takeIf { public }?.let { LabeledFigure(DeckFigure(DeckFormat.count(it), "$it"), "Meses de registro", "Del periodo del informe") },
                interactions?.let {
                    LabeledFigure(
                        DeckFigure(DeckFormat.count(it), "$it"),
                        "Interacciones registradas",
                        "Reuniones, visitas y talleres del periodo"
                    )
                }
            )
        val title =
            when {
                visited == null -> if (total == 1) "El estudio abarcó una localidad" else "El estudio abarcó $total localidades"
                visited == total ->
                    if (total ==
                        1
                    ) {
                        "El trabajo de campo llegó a la localidad del área"
                    } else {
                        "El trabajo de campo llegó a las $total localidades del área"
                    }
                else -> "El trabajo de campo llegó a $visited de $total localidades"
            }
        return DeckStory(
            "cobertura",
            Beat.SCOPE,
            w(Audience.CAMPO to 9, Audience.INVESTIGACION to 9, Audience.INSTITUCIONES to 9, Audience.COMUNIDAD to 9, Audience.DIRECCION to 4),
            title,
            "Cobertura del trabajo de campo",
            finding = title,
            headline = items.first(),
            source = source(fact)
        ) { audience, chapter ->
            listOf(
                DeckSlide(
                    "cobertura",
                    "Mostrar el alcance del trabajo de campo",
                    title,
                    SlideData.Figures(
                        items.take(
                            if (audience ==
                                Audience.COMUNIDAD
                            ) {
                                2
                            } else {
                                3
                            }
                        )
                    ),
                    chapter,
                    source(fact)
                )
            )
        }
    }

    private fun fieldworkByLocality(): DeckStory? {
        if (public) return null
        val fact = facts["trabajo_de_campo"] ?: return null
        val index = fact.dimensions.indexOf("Localidad").takeIf { it >= 0 } ?: return null
        val sums =
            fact.rows
                .groupBy { it.labels[index] }
                .mapValues { (_, rows) -> rows.sumOf { it.count ?: 0 } }
                .filterValues { it > 0 }
        if (sums.size < 2) return null
        val derived = fact.copy(rows = sums.map { (label, sum) -> FactRow(listOf(label), count = sum) })
        val top = sums.maxBy { it.value }
        val title = "${top.key} concentró la mayor parte del trabajo de campo"
        return DeckStory(
            "campo-por-localidad",
            Beat.SCOPE,
            w(Audience.CAMPO to 8, Audience.INVESTIGACION to 7),
            title,
            "Trabajo de campo por localidad",
            source = source(fact)
        ) { audience, chapter ->
            listOfNotNull(
                comparisonSlide(
                    "campo-por-localidad",
                    chapter,
                    "Ver dónde se concentró el trabajo de campo",
                    title,
                    derived,
                    audience,
                    "Entrevistas y observaciones"
                )
            )
        }
    }

    private fun trust(): DeckStory? {
        val fact = facts["confianza_por_mes"] ?: return null
        val known = fact.rows.filter { it.reserved != true && DeckFormat.plain(it.value) != null }
        if (known.size < 2) return null
        val first = known.first()
        val last = known.last()
        val a = DeckFormat.number(first.value)!!
        val b = DeckFormat.number(last.value)!!
        val from = DeckFormat.month(first.labels.first(), capital = false)
        val to = DeckFormat.month(last.labels.first(), capital = false)
        val delta = b - a
        val title =
            when {
                delta >= BigDecimal("0.10") -> "La confianza promedio subió de ${DeckFormat.decimal(a, 2)} a ${DeckFormat.decimal(b, 2)} entre $from y $to"
                delta <= BigDecimal("-0.10") -> "La confianza promedio bajó de ${DeckFormat.decimal(a, 2)} a ${DeckFormat.decimal(b, 2)} entre $from y $to"
                else -> "La confianza promedio se mantuvo cerca de ${DeckFormat.decimal(b, 2)} entre $from y $to"
            }
        val plain =
            when {
                delta >= BigDecimal("0.10") -> "La confianza en el proyecto subió entre $from y $to"
                delta <= BigDecimal("-0.10") -> "La confianza en el proyecto bajó entre $from y $to"
                else -> "La confianza en el proyecto se mantuvo entre $from y $to"
            }
        val absent = fact.rows.filter { it.reserved != true && DeckFormat.plain(it.value) == null }
        val missing = absent.map { DeckFormat.month(it.labels.first(), capital = false) }
        val reserved = fact.rows.filter { it.reserved == true }.map { DeckFormat.month(it.labels.first(), capital = false) }
        val reading =
            listOfNotNull(
                missing.takeIf { it.isNotEmpty() }?.let { "Sin evaluaciones con puntaje en ${it.joinToString(", ")}." },
                reserved.takeIf { it.isNotEmpty() }?.let { "En ${it.joinToString(", ")} hubo menos de 5 evaluaciones: no se publica." }
            ).joinToString(" ").ifEmpty { null }
        val headline =
            LabeledFigure(
                DeckFormat.figure(last, 2),
                "Confianza promedio en ${DeckFormat.month(last.labels.first(), capital = false)}, de 1 a 5 (${last.n?.let(DeckFormat::cases) ?: ""})"
            )
        return DeckStory(
            "confianza",
            Beat.FINDINGS,
            w(Audience.DIRECCION to 9, Audience.CAMPO to 6, Audience.INVESTIGACION to 8, Audience.INSTITUCIONES to 7, Audience.COMUNIDAD to 9),
            title,
            "La confianza promedio por mes",
            finding = title,
            plain = plain,
            headline = headline,
            source = source(fact),
            limitations =
                listOfNotNull(
                    "La confianza es el promedio de quienes fueron evaluados, no de toda la población.",
                    missing.takeIf { it.isNotEmpty() }?.let { "Los meses sin evaluaciones no se interpolan." },
                    "Una coincidencia en el tiempo no prueba una causa."
                )
        ) { audience, chapter ->
            val digits = if (audience == Audience.COMUNIDAD) 1 else 2
            val series =
                TrendSeries(
                    if (audience == Audience.COMUNIDAD) "Confianza" else "Confianza promedio",
                    fact.rows.map { if (it.reserved == true) null else DeckFormat.plain(it.value) },
                    fact.rows.map {
                        when {
                            it.reserved == true -> DeckFormat.RESERVED
                            DeckFormat.plain(it.value) == null -> null
                            else -> DeckFormat.decimal(DeckFormat.number(it.value)!!, digits)
                        }
                    },
                    if (audience == Audience.COMUNIDAD) null else fact.rows.map { it.n }
                )
            listOf(
                DeckSlide(
                    "confianza",
                    "Mostrar cómo cambió la confianza de los grupos de interés",
                    if (audience == Audience.COMUNIDAD) plain else title,
                    SlideData.Trend(
                        if (audience == Audience.COMUNIDAD) "Confianza, de 1 (poca) a 5 (mucha)" else "Confianza promedio, de 1 a 5",
                        fact.rows.map { DeckFormat.month(it.labels.first()) },
                        listOf(series),
                        "1",
                        "5",
                        reading.takeIf { audience != Audience.COMUNIDAD }
                    ),
                    chapter,
                    source(fact),
                    note = if (audience == Audience.COMUNIDAD) reading else "Promedio mensual de las evaluaciones con puntaje; n, las evaluaciones de cada mes."
                )
            )
        }
    }

    private fun shares(): DeckStory? {
        val keys = listOf("comunicacion_suficiente", "informacion_adecuada", "cambio_de_postura")
        val found = keys.mapNotNull { facts[it] }
        if (found.isEmpty() || found.all { f -> f.rows.count { it.reserved != true && DeckFormat.plain(it.value) != null } < 2 }) return null
        val title = "Cómo vieron la comunicación y la información los grupos evaluados"
        return DeckStory(
            "indices",
            Beat.FINDINGS,
            w(Audience.INVESTIGACION to 7, Audience.CAMPO to 4, Audience.INSTITUCIONES to 3),
            title,
            title,
            source = source(found.first()),
            limitations = listOf("Cada porcentaje se calcula sobre las evaluaciones que responden esa pregunta.")
        ) { _, chapter ->
            val months = found.first().rows.map { DeckFormat.month(it.labels.first()) }
            listOf(
                DeckSlide(
                    "indices",
                    "Mostrar la evolución de los indicadores de la evaluación",
                    title,
                    SlideData.Trend(
                        "% de las evaluaciones que responden",
                        months,
                        found.take(3).map { f ->
                            TrendSeries(
                                f.title.removeSuffix(" por mes"),
                                f.rows.map { if (it.reserved == true) null else DeckFormat.plain(it.value) },
                                f.rows.map { r -> if (r.reserved == true || DeckFormat.plain(r.value) == null) null else DeckFormat.percent(r.value!!) }
                            )
                        },
                        "0",
                        "100"
                    ),
                    chapter,
                    source(found.first())
                )
            )
        }
    }

    private fun interactions(
        key: String,
        unit: String
    ): DeckStory? {
        val fact = facts[key] ?: return null
        val (items, _) = countComparison(fact, 10)
        if (items.size < 2) return null
        val top = items.first()
        val title =
            if (public) {
                "«${top.label}» fue el tipo de interacción más frecuente"
            } else {
                val total = fact.rows.sumOf { it.count ?: 0 }
                "Se registraron ${DeckFormat.count(total)} interacciones; el tipo más frecuente fue «${top.label}»"
            }
        return DeckStory(
            "interacciones",
            Beat.FINDINGS,
            w(Audience.DIRECCION to 5, Audience.CAMPO to 8, Audience.INVESTIGACION to 6, Audience.INSTITUCIONES to 7, Audience.COMUNIDAD to 7),
            title,
            "Interacciones por tipo",
            finding = title,
            source = source(fact)
        ) { audience, chapter ->
            listOfNotNull(comparisonSlide("interacciones", chapter, "Mostrar cómo se relacionó el proyecto con los grupos", title, fact, audience, unit))
        }
    }

    private fun commitments(): DeckStory? {
        val byTerm = facts["compromisos_por_plazo"]
        val byState = facts["compromisos_por_estado"] ?: return null
        val overdue =
            byTerm?.rows?.firstOrNull { row ->
                row.labels.any {
                    it.contains("vencid", ignoreCase = true) &&
                        !it.contains("por vencer", ignoreCase = true)
                }
            }
        val total = if (public) null else byState.rows.sumOf { it.count ?: 0 }
        if (total == 0) return null
        val title =
            when {
                overdue?.count == null || overdue.count == 0 -> "El estado de los compromisos"
                total != null && overdue.count == total -> if (total == 1) "El único compromiso está vencido" else "Los $total compromisos están vencidos"
                total != null -> "${DeckFormat.count(
                    overdue.count
                )} de ${DeckFormat.count(total)} compromisos ${if (overdue.count == 1) "está vencido" else "están vencidos"}"
                else -> if (overdue.count == 1) "Un compromiso está vencido" else "${DeckFormat.count(overdue.count)} compromisos están vencidos"
            }
        val headline = overdue?.let { LabeledFigure(DeckFormat.figure(it), "Compromisos vencidos al corte") }
        return DeckStory(
            "compromisos",
            Beat.COMMITMENTS,
            w(Audience.DIRECCION to 9, Audience.CAMPO to 7, Audience.INSTITUCIONES to 9, Audience.COMUNIDAD to 8, Audience.INVESTIGACION to 4),
            title,
            "Compromisos por estado",
            finding = title.takeIf { (overdue?.count ?: 0) > 0 },
            headline = headline,
            source = source(byState)
        ) { audience, chapter ->
            // para la comunidad, los plazos como cifras grandes: vencidos, por vencer y al día, cada uno con su reserva
            val figures =
                byTerm?.takeIf { audience == Audience.COMUNIDAD }?.rows?.filter {
                    it.labels.none { l ->
                        l == ReportFacts.NOT_AVAILABLE ||
                            l == ReportFacts.NO_DATA
                    }
                }
            if (figures != null && figures.isNotEmpty()) {
                return@DeckStory listOf(
                    DeckSlide(
                        "compromisos",
                        "Mostrar si el proyecto cumple sus plazos",
                        title,
                        SlideData.Figures(
                            // lo vencido primero; el rótulo del plazo en plural (un compromiso vencido, muchos compromisos vencidos)
                            figures.sortedByDescending { r -> r.labels.any { it.contains("vencid", true) && !it.contains("por vencer", true) } }.take(3).map {
                                LabeledFigure(DeckFormat.figure(it), "Compromisos ${plural(it.labels.first())}")
                            }
                        ),
                        chapter,
                        source(byTerm)
                    )
                )
            }
            listOfNotNull(
                comparisonSlide("compromisos-estado", chapter, "Mostrar en qué estado están los compromisos", title, byState, audience, "Compromisos"),
                byTerm?.takeIf { audience == Audience.CAMPO || audience == Audience.INSTITUCIONES }?.let {
                    comparisonSlide(
                        "compromisos-plazo",
                        chapter,
                        "Mostrar los plazos de los compromisos",
                        "Los compromisos según su plazo",
                        it,
                        audience,
                        "Compromisos"
                    )
                }
            )
        }
    }

    // el rótulo de un plazo en plural: «Vencido» es «vencidos»; «Al día» y «Por vencer» no cambian
    private fun plural(label: String): String = label.lowercase().let { if (it.endsWith("ido") || it.endsWith("ado")) it + "s" else it }

    // el nivel de una celda de la matriz por el color que le dio el tipo (DiagnosisStrategy.shade)
    private fun tone(shade: String?): String =
        when (shade?.uppercase()) {
            "FF7C80" -> "CRITICAL"
            "F4B183" -> "HIGH"
            "FFE699" -> "MEDIUM"
            "C6E0B4" -> "LOW"
            else -> "NONE"
        }

    private fun risks(): DeckStory? {
        val table = table("matriz_de_riesgos") ?: return null
        // filas: impacto de 5 a 1, la primera celda es el impacto; columnas: probabilidad de 1 a 5
        val rows = table.rows.map { it.drop(1) }
        if (rows.size != 5 || rows.any { it.size != 5 }) return null
        val count = rows.flatten().sumOf { it.text.toIntOrNull() ?: 0 }
        if (count == 0) return null
        val high = rows.flatten().filter { tone(it.shade) in setOf("HIGH", "CRITICAL") }.sumOf { it.text.toIntOrNull() ?: 0 }
        val title =
            when {
                count == 1 -> if (high == 1) "El único riesgo evaluado está en nivel alto o muy alto" else "El único riesgo evaluado no está en nivel alto"
                high == 0 -> "Ninguno de los $count riesgos evaluados está en nivel alto"
                high == 1 -> "1 de $count riesgos evaluados está en nivel alto o muy alto"
                else -> "$high de $count riesgos evaluados están en nivel alto o muy alto"
            }
        return DeckStory(
            "riesgos",
            Beat.RISKS,
            w(Audience.DIRECCION to 8, Audience.CAMPO to 6, Audience.INVESTIGACION to 6, Audience.INSTITUCIONES to 8),
            title,
            "Matriz de riesgos sociales",
            finding = title,
            headline = LabeledFigure(DeckFigure(DeckFormat.count(high), "$high"), "Riesgos en nivel alto o muy alto"),
            source = "Matriz de riesgos sociales" + (facts["riesgos_matriz"]?.let(::period) ?: "")
        ) { _, chapter ->
            val cells =
                rows.map { row ->
                    row.map { cell ->
                        val n = cell.text.toIntOrNull()
                        MatrixCell(if (n == null || n == 0) DeckFigure("", "0") else DeckFigure(DeckFormat.count(n), "$n"), tone(cell.shade))
                    }
                }
            listOf(
                DeckSlide(
                    "riesgos",
                    "Ubicar los riesgos por probabilidad e impacto",
                    title,
                    SlideData.Matrix(
                        "Impacto",
                        "Probabilidad",
                        table.rows.map { it.first().text },
                        (1..5).map { "$it" },
                        cells,
                        listOf(MatrixLegend("LOW", "Bajo"), MatrixLegend("MEDIUM", "Medio"), MatrixLegend("HIGH", "Alto"), MatrixLegend("CRITICAL", "Muy alto"))
                    ),
                    chapter,
                    "Matriz de riesgos sociales" + (facts["riesgos_matriz"]?.let(::period) ?: "")
                )
            )
        }
    }

    private fun riskList(): DeckStory? {
        val table = table("riesgos") ?: return null
        if (table.rows.isEmpty()) return null
        val title = "Los riesgos sociales identificados"
        return DeckStory(
            "riesgos-lista",
            Beat.RISKS,
            w(Audience.CAMPO to 5, Audience.INVESTIGACION to 5),
            title,
            title,
            source = "Registro de riesgos sociales"
        ) { audience, chapter ->
            table.rows.chunked(minOf(8, audience.maxItems)).mapIndexed { i, chunk ->
                DeckSlide(
                    "riesgos-lista-${i + 1}",
                    "Nombrar los riesgos sociales del periodo",
                    if (i == 0) title else "$title (continúa)",
                    SlideData.Table(table.header, table.header.map { "LEFT" }, chunk.map { r -> r.map { it.text } }),
                    chapter,
                    "Registro de riesgos sociales"
                )
            }
        }
    }

    // las citas de las percepciones: «cita» — cargo, en los párrafos de las secciones de percepciones que el nivel dejó
    private val quote = Regex("""^\s*[-*]\s*(?:\(([^)]*)\)\s*)?(.*?)\s*«([^»]{12,400})»\s*[—–-]\s*(.+?)\s*$""")

    private fun perceptions(): DeckStory? {
        if (public) return null
        val found =
            blocks()
                .filter { (section, _) -> section.startsWith("percepciones") }
                .map { it.second }
                .filterIsInstance<Block.Paragraph>()
                .flatMap { it.markdown.lines().asSequence() }
                .mapNotNull { quote.matchEntire(it) }
                .map { m -> Triple(m.groupValues[2].trim().trimEnd(':', '.', ','), m.groupValues[3].trim(), m.groupValues[4].trim()) }
                .toList()
        if (found.isEmpty()) return null
        return DeckStory(
            "percepciones",
            Beat.EVIDENCE,
            w(Audience.CAMPO to 9, Audience.INVESTIGACION to 8, Audience.INSTITUCIONES to 5, Audience.DIRECCION to 3),
            "Lo que dicen los grupos de interés",
            "Percepciones",
            source = "Percepciones registradas en el estudio de campo",
            limitations = listOf("Cada cita es la percepción de un grupo de interés, no la de toda la comunidad.")
        ) { audience, chapter ->
            found.take(if (audience == Audience.CAMPO) 3 else 2).mapIndexed { i, (statement, text, who) ->
                DeckSlide(
                    "percepcion-${i + 1}",
                    "Mostrar una percepción con su alcance",
                    // el título dice que es una percepción: nunca la presenta como un hecho (§16.5)
                    statement.takeIf { it.isNotBlank() }?.let { "Percepción registrada: ${it.replaceFirstChar(Char::lowercase)}" }
                        ?: "Lo que dicen los grupos de interés",
                    SlideData.Quote(text, who, "PERCEPCION", "Percepción de un grupo de interés; no representa a toda la comunidad."),
                    chapter,
                    "Percepciones registradas en el estudio de campo"
                )
            }
        }
    }

    private fun population(): DeckStory? {
        val fact = facts["poblacion"] ?: return null
        val rows = fact.rows.filter { it.reserved != true && DeckFormat.plain(it.value) != null }
        if (rows.size < 2) return null
        val sorted = rows.sortedByDescending { DeckFormat.number(it.value) }
        val title = "Las localidades más pobladas del área de influencia"
        return DeckStory(
            "poblacion",
            Beat.CONTEXT,
            w(Audience.CAMPO to 7, Audience.INVESTIGACION to 7, Audience.INSTITUCIONES to 5),
            title,
            title,
            source = source(fact)
        ) { audience, chapter ->
            val shown = sorted.take(audience.maxItems)
            val items = shown.mapIndexed { i, r -> ComparisonItem(r.labels.last(), DeckFormat.figure(r, 0), focus = i == 0) }
            val absent = fact.rows.count { DeckFormat.plain(it.value) == null }
            listOf(
                DeckSlide(
                    "poblacion",
                    "Dar el contexto demográfico del área",
                    title,
                    SlideData.Comparison("Población", items, maxOf(items)),
                    chapter,
                    source(fact),
                    note = absent.takeIf { it > 0 }?.let { "$it localidades sin dato de población." }
                )
            )
        }
    }

    private fun map(
        key: String,
        beat: Beat
    ): DeckStory? {
        val figure = blocks().map { it.second }.filterIsInstance<Block.MapFigure>().firstOrNull { it.key == key } ?: return null
        val image = model.maps[key]?.internal ?: return null
        val bytes = Base64.getDecoder().decode(image.pngBase64)
        val size = pngSize(bytes) ?: return null
        val asset = "mapa-$key".lowercase().replace('_', '-')
        assets[asset] = DeckAsset("image/png", size.first, size.second, figure.title, image.pngBase64)
        return DeckStory(
            asset,
            beat,
            w(Audience.CAMPO to 9, Audience.INSTITUCIONES to 9, Audience.INVESTIGACION to 5, Audience.DIRECCION to 2),
            figure.title,
            figure.title,
            source = figure.source ?: "Cartografía del informe"
        ) { _, chapter ->
            listOf(
                DeckSlide(
                    asset,
                    "Ubicar el territorio",
                    figure.title,
                    SlideData.MapView(asset, if (image.omittedLayers) "Algunas capas se omiten en este nivel de divulgación." else null),
                    chapter,
                    figure.source ?: "Cartografía del informe"
                )
            )
        }
    }

    private fun monitoring(): DeckStory? {
        val fact = facts["monitoreo"] ?: return null
        if (fact.rows.isEmpty()) return null
        val title = "Los indicadores de monitoreo del periodo"
        return DeckStory(
            "monitoreo",
            Beat.FINDINGS,
            w(Audience.INVESTIGACION to 6, Audience.INSTITUCIONES to 5, Audience.DIRECCION to 3),
            title,
            title,
            source = source(fact)
        ) { audience, chapter ->
            fact.rows.chunked(minOf(7, audience.maxItems)).mapIndexed { i, chunk ->
                DeckSlide(
                    "monitoreo-${i + 1}",
                    "Reportar los indicadores de la Tabla 9.5",
                    if (i == 0) title else "$title (continúa)",
                    SlideData.Table(
                        listOf("Indicador", "Valor", "Meta"),
                        listOf("LEFT", "RIGHT", "RIGHT"),
                        chunk.map { r -> listOf(r.labels.joinToString(" "), r.value ?: DeckFormat.ABSENT, r.target ?: "Sin meta") }
                    ),
                    chapter,
                    source(fact)
                )
            }
        }
    }

    private fun method(): DeckStory? {
        val fact = facts["trabajo_de_campo"] ?: return null
        val instrument = fact.dimensions.indexOf("Instrumento").takeIf { it >= 0 } ?: return null
        val locality = fact.dimensions.indexOf("Localidad")
        val instruments =
            fact.rows
                .map { it.labels[instrument] }
                .distinct()
                .filter { it != ReportFacts.NOT_AVAILABLE }
        if (instruments.isEmpty()) return null
        val steps =
            instruments.take(4).map { name ->
                val rows = fact.rows.filter { it.labels[instrument] == name }
                val detail =
                    if (public) {
                        "Aplicado en las localidades del área de influencia."
                    } else {
                        val places =
                            rows
                                .filter { (it.count ?: 0) > 0 }
                                .map { it.labels.getOrNull(locality) }
                                .distinct()
                                .size
                        "${DeckFormat.count(rows.sumOf { it.count ?: 0 })} aplicaciones en $places localidades."
                    }
                MethodStep(name.replaceFirstChar(Char::uppercase), detail)
            }
        val title = "Cómo se recogió la información"
        return DeckStory("metodo", Beat.METHOD, w(Audience.INVESTIGACION to 9, Audience.CAMPO to 5), title, title, source = source(fact)) { _, chapter ->
            // las cifras que respaldan el método: las localidades, los meses y, fuera de PUBLICO, las evaluaciones con puntaje
            val evaluations = if (public) null else facts["confianza_por_mes"]?.rows?.sumOf { it.n ?: 0 }?.takeIf { it > 0 }
            val support =
                listOfNotNull(
                    localities(fact).count { it != ReportFacts.NOT_AVAILABLE && it != ReportFacts.NO_DATA }.takeIf { it > 0 }?.let {
                        LabeledFigure(DeckFigure(DeckFormat.count(it), "$it"), "Localidades en el estudio")
                    },
                    months()?.let { LabeledFigure(DeckFigure(DeckFormat.count(it), "$it"), "Meses del periodo") },
                    evaluations?.let { LabeledFigure(DeckFigure(DeckFormat.count(it), "$it"), "Evaluaciones con puntaje") }
                )
            listOf(DeckSlide("metodo", "Explicar el método del estudio", title, SlideData.Method(steps, support), chapter, source(fact)))
        }
    }

    // los puntos de una sección narrativa (lista numerada o con viñetas) tal como los escribió el equipo: el texto humano del
    // informe, sin resumirlo. solo fuera de PUBLICO, que no lleva párrafos
    private fun narrativeSteps(
        section: String,
        title: String
    ): DeckStory? {
        val points = narrativePoints(section)
        if (points.isEmpty()) return null
        return DeckStory(
            "pasos-$section",
            Beat.NEXT,
            w(Audience.DIRECCION to 7, Audience.CAMPO to 7, Audience.INSTITUCIONES to 8),
            title,
            title,
            nextSteps = points
        ) { _, _ -> emptyList() }
    }

    private fun narrativePoints(section: String): List<String> {
        val item = Regex("""^\s*(?:[-*]|\d+[.)])\s+(.+)$""")
        return model.sections
            .firstOrNull { it.key == section }
            ?.blocks
            ?.filterIsInstance<Block.Paragraph>()
            ?.flatMap { p ->
                p.markdown.lines().mapNotNull {
                    item
                        .matchEntire(it)
                        ?.groupValues
                        ?.get(1)
                        ?.trim()
                }
            }.orEmpty()
            .map { it.replace(Regex("""\*\*|__|`"""), "") }
            .filter { it.length in 8..260 }
    }

    // --- T2 ---

    private fun fieldReport(): List<DeckStory> =
        listOfNotNull(
            interactions("actividades_por_tipo", "Actividades del periodo"),
            visits(),
            networks(),
            support(),
            supportPhotos(),
            concessions(),
            map(FieldReportType.LAND_MAP, Beat.CONTEXT),
            conclusions()
        )

    private fun visits(): DeckStory? {
        if (public) return null
        val table = table("visitas_de_campo") ?: return null
        if (table.rows.size < 2) return null
        val title = "Las visitas de campo del periodo"
        return DeckStory(
            "visitas",
            Beat.SCOPE,
            w(Audience.CAMPO to 9, Audience.INSTITUCIONES to 6, Audience.DIRECCION to 4),
            title,
            title,
            source = "Registro de visitas de campo"
        ) { audience, chapter ->
            table.rows.take(minOf(8, audience.maxItems)).let { rows ->
                listOf(
                    DeckSlide(
                        "visitas",
                        "Mostrar la cronología del trabajo de campo",
                        title,
                        SlideData.Timeline(
                            rows.map { r ->
                                TimelineEvent(
                                    r[0].text.take(40),
                                    r
                                        .getOrNull(1)
                                        ?.text
                                        .orEmpty()
                                        .take(120),
                                    r
                                        .getOrNull(3)
                                        ?.text
                                        ?.take(200)
                                        ?.ifBlank { null }
                                )
                            }
                        ),
                        chapter,
                        "Registro de visitas de campo"
                    )
                )
            }
        }
    }

    private fun networks(): DeckStory? {
        val fact = facts["redes_y_nodos"] ?: return null
        if (fact.rows.isEmpty()) return null
        val items = fact.rows.map { LabeledFigure(DeckFormat.figure(it), it.labels.joinToString(" ")) }
        val title = "Las redes comunitarias identificadas"
        return DeckStory(
            "redes",
            Beat.FINDINGS,
            w(Audience.CAMPO to 7, Audience.INSTITUCIONES to 6, Audience.DIRECCION to 5, Audience.COMUNIDAD to 5),
            title,
            title,
            headline = items.first(),
            source = source(fact)
        ) { _, chapter ->
            listOf(DeckSlide("redes", "Dimensionar las redes de la comunidad", title, SlideData.Figures(items.take(4)), chapter, source(fact)))
        }
    }

    private fun support(): DeckStory? {
        val total = facts["monto_del_aporte"] ?: return null
        val row = total.rows.firstOrNull() ?: return null
        val items = facts["items_del_aporte"]
        val currency = row.unit ?: total.unit.orEmpty()
        val figure = DeckFormat.figure(row, 2).let { it.copy(display = "${it.display} $currency".trim()) }
        val title = "El apoyo entregado sumó ${figure.display}"
        return DeckStory(
            "apoyo",
            Beat.FINDINGS,
            w(Audience.DIRECCION to 7, Audience.INSTITUCIONES to 8, Audience.CAMPO to 6, Audience.COMUNIDAD to 6),
            title,
            "El apoyo entregado",
            finding = title.takeIf { figure.value != null },
            headline = LabeledFigure(figure, "Apoyo entregado"),
            source = source(total)
        ) { audience, chapter ->
            val byKind = items?.takeIf { !public }?.rows?.filter { DeckFormat.plain(it.value) != null }
            listOfNotNull(
                if (byKind != null && byKind.size >= 2) {
                    val list =
                        byKind.sortedByDescending { DeckFormat.number(it.value) }.take(audience.maxItems).mapIndexed { i, r ->
                            ComparisonItem(
                                r.labels.joinToString(" "),
                                DeckFormat.figure(r, 2),
                                i == 0
                            )
                        }
                    DeckSlide(
                        "apoyo",
                        "Mostrar en qué consistió el apoyo",
                        title,
                        SlideData.Comparison("Monto ($currency)", list, maxOf(list)),
                        chapter,
                        source(items)
                    )
                } else {
                    DeckSlide(
                        "apoyo",
                        "Mostrar el apoyo entregado",
                        title,
                        SlideData.Figures(listOf(LabeledFigure(figure, "Apoyo entregado en el periodo"))),
                        chapter,
                        source(total)
                    )
                }
            )
        }
    }

    private fun supportPhotos(): DeckStory? {
        val evidence =
            blocks()
                .filter { it.first == "apoyo" }
                .map { it.second }
                .filterIsInstance<Block.Photos>()
                .flatMap { it.evidence.asSequence() }
                .mapNotNull { model.photos[it] }
                .filter { it.kind == PhotoRef.PHOTO }
                .toList()
        val ids =
            evidence.take(2).mapNotNull { photo ->
                val bytes = photoBytes[photo.evidence] ?: return@mapNotNull null
                val resized = PhotoResize.jpeg(bytes, 1600, 82, 40) ?: return@mapNotNull null
                val id = "foto-${photo.sha256.take(12)}"
                assets[id] = DeckAsset("image/jpeg", resized.width, resized.height, photo.alt, Base64.getEncoder().encodeToString(resized.jpeg))
                id
            }
        if (ids.isEmpty()) return null
        val title = "La entrega del apoyo"
        return DeckStory(
            "fotos-apoyo",
            Beat.EVIDENCE,
            w(Audience.INSTITUCIONES to 7, Audience.CAMPO to 6, Audience.COMUNIDAD to 4),
            title,
            title,
            source = "Evidencias del aporte"
        ) { _, chapter ->
            listOf(
                DeckSlide(
                    "fotos-apoyo",
                    "Mostrar la evidencia de la entrega",
                    title,
                    SlideData.Photos(ids, "Registro fotográfico de la entrega del apoyo."),
                    chapter,
                    "Evidencias del aporte"
                )
            )
        }
    }

    private fun concessions(): DeckStory? {
        val fact = facts["concesiones"] ?: return null
        val rows = fact.rows.filter { DeckFormat.plain(it.value) != null }
        if (rows.isEmpty()) return null
        val title = "Las concesiones del proyecto"
        return DeckStory(
            "concesiones",
            Beat.CONTEXT,
            w(Audience.INSTITUCIONES to 7, Audience.CAMPO to 5, Audience.INVESTIGACION to 5),
            title,
            title,
            source = source(fact)
        ) { audience, chapter ->
            listOf(
                DeckSlide(
                    "concesiones",
                    "Dimensionar las concesiones",
                    title,
                    SlideData.Table(
                        listOf("Concesión", "Área inscrita (ha)"),
                        listOf("LEFT", "RIGHT"),
                        rows.take(audience.maxItems).map {
                            listOf(it.labels.joinToString(" "), DeckFormat.figure(it, 2).display)
                        }
                    ),
                    chapter,
                    source(fact)
                )
            )
        }
    }

    private fun conclusions(): DeckStory? {
        val points = narrativePoints("conclusiones")
        val next = narrativePoints("recomendaciones")
        if (points.isEmpty() && next.isEmpty()) return null
        return DeckStory(
            "conclusiones-informe",
            Beat.NEXT,
            w(Audience.DIRECCION to 8, Audience.CAMPO to 8, Audience.INSTITUCIONES to 8, Audience.INVESTIGACION to 5),
            "Conclusiones y recomendaciones del informe",
            "Conclusiones",
            nextSteps = next,
            limitations = emptyList()
        ) { _, _ -> emptyList() }.also { reportConclusions = points }
    }

    // las conclusiones que escribió el equipo (T2), para la lámina de conclusiones
    var reportConclusions: List<String> = emptyList()
        private set

    companion object {
        // el ancho y el alto de un PNG por su cabecera
        fun pngSize(bytes: ByteArray): Pair<Int, Int>? {
            if (bytes.size < 24 || bytes[0] != 0x89.toByte() || bytes[1] != 'P'.code.toByte()) return null

            fun int(at: Int) =
                ((bytes[at].toInt() and 0xff) shl 24) or ((bytes[at + 1].toInt() and 0xff) shl 16) or ((bytes[at + 2].toInt() and 0xff) shl 8) or
                    (bytes[at + 3].toInt() and 0xff)
            return int(16) to int(20)
        }
    }
}
