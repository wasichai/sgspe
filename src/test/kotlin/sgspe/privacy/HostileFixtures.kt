package sgspe.privacy

// un modelo con marcadores ficticios identificables en todo lugar sensible (ADR-0031, deliverables.md §16.4): un nombre en una
// mención, en un párrafo marcado, en una celda marcada y en una cita; un teléfono y un documento de identidad ficticios; el lugar
// de una entrega; un código GI- en un gráfico; fotos con y sin personas, y hechos tipados de cada clase. lo que PUBLICO o EXTERNO
// no permiten no debe sobrevivir. todo ficticio (RQ-SEC-018)
object HostileFixtures {
    const val NAME = "Zz Nombre Restringido"
    const val PHONE = "999 111 222"
    const val DNI = "40123456"
    const val PLACE = "Casa Zz Lugar"
    const val QUOTE = "Zz cita textual"
    const val CODE = "GI-ZZ0000000001"
    const val PERSON = "person:99999999-9999-9999-9999-999999999999"
    const val IDENTIFIABLE = "55555555-5555-5555-5555-555555555555"
    const val LANDSCAPE = "66666666-6666-6666-6666-666666666666"

    // los marcadores que nunca salen de INTERNO, salvo el código GI-, que EXTERNO admite como seudónimo
    val NEVER_PUBLIC = listOf(NAME, PHONE, DNI, PLACE, QUOTE, CODE, "Presidente Zz", IDENTIFIABLE)

    val stakeholdersByLocality =
        Fact(
            key = "grupos_por_localidad",
            kind = Fact.COUNT,
            title = "Grupos de interés por localidad",
            dimensions = listOf("Localidad"),
            measure = "Grupos de interés",
            rows =
                listOf(
                    FactRow(listOf("Localidad Alfa"), count = 12),
                    FactRow(listOf("Localidad Beta"), count = 3),
                    FactRow(listOf("Localidad Gamma"), count = 0),
                    FactRow(listOf("Localidad Delta"), count = 7)
                ),
            unit = "grupos de interés",
            period = "Abril a setiembre de 2026",
            source = "Registro de grupos de interés",
            forPublic = true
        )

    val trust =
        Fact(
            key = "confianza_por_mes",
            kind = Fact.MEAN,
            title = "Confianza promedio por mes",
            dimensions = listOf("Mes"),
            measure = "Confianza promedio (1 a 5)",
            rows =
                listOf(
                    FactRow(listOf("Abril"), value = "3.20", n = 9),
                    FactRow(listOf("Mayo"), value = "4.50", n = 2),
                    FactRow(listOf("Junio"), value = "3.80")
                ),
            forPublic = true
        )

    val stances =
        Fact(
            key = "posturas",
            kind = Fact.COUNT,
            title = "Grupos de interés por postura",
            dimensions = listOf("Postura"),
            measure = "Grupos de interés",
            rows = listOf(FactRow(listOf("A FAVOR"), count = 20), FactRow(listOf("EN CONTRA"), count = 8)),
            mark = Mark.restricted("stakeholder_assessment.stance"),
            forPublic = true
        )

    val internalOnly =
        Fact(
            key = "monitoreo",
            kind = Fact.VALUE,
            title = "Indicadores del periodo",
            dimensions = listOf("Indicador"),
            measure = "Valor",
            rows = listOf(FactRow(listOf("Reuniones con [[$PERSON]]"), value = "4", unit = "reuniones"))
        )

    val amount =
        Fact(
            key = "monto_del_aporte",
            kind = Fact.AMOUNT,
            title = "Monto del aporte",
            dimensions = listOf("Aporte"),
            measure = "Monto (PEN)",
            rows = listOf(FactRow(listOf("Total"), value = "500.00")),
            unit = "PEN",
            forPublic = true
        )

    val indicator =
        Fact(
            key = "indicadores_publicos",
            kind = Fact.VALUE,
            title = "Indicadores",
            dimensions = listOf("Indicador"),
            measure = "Valor",
            rows =
                listOf(
                    FactRow(listOf("Compromisos cumplidos (%)"), value = "80", n = 10, target = "90", unit = "%"),
                    FactRow(listOf("Sin denominador"), value = "50", unit = "%")
                ),
            forPublic = true
        )

    val unknownKind = Fact("raro", "OTRO", "Raro", listOf("A"), "B", listOf(FactRow(listOf("x"), count = 9)), forPublic = true)

    val emptyCount = Fact("vacio", Fact.COUNT, "Vacío", listOf("A"), "B", emptyList(), forPublic = true)

    val countWithoutCount = Fact("incompleto", Fact.COUNT, "Incompleto", listOf("A"), "B", listOf(FactRow(listOf("x"), value = "9")), forPublic = true)

    fun model(): ReportModel =
        ReportFixtures.model().let { base ->
            base.copy(
                meta = base.meta.copy(typeVersion = 2, coverMap = "predio"),
                sections =
                    listOf(
                        ReportSection(
                            "percepciones",
                            "Percepciones de [[$PERSON]]",
                            "MIXTA",
                            listOf(
                                Block.Heading(2, "Percepciones por ámbito"),
                                Block.Paragraph("Conversamos con [[$PERSON]]: «$QUOTE»."),
                                Block.Paragraph("Teléfono $PHONE, DNI $DNI.", Mark.restricted("contact_method.contact_value")),
                                Block.Paragraph("Lugar de la entrega: $PLACE.", Mark.restricted("delivery.place")),
                                Block.Table(
                                    "Clasificación",
                                    listOf("Nombre", "Cargo"),
                                    listOf(listOf(Cell(NAME, mark = Mark.restricted("person.name")), Cell("Presidente Zz"))),
                                    key = "clasificacion"
                                ),
                                Block.LineChart("Confianza", listOf("Abril"), listOf(ChartSeries("[[$PERSON]]", listOf("3.20"))), key = "confianza"),
                                Block.ScatterChart("Influencia", "Interés", "Poder", listOf(ChartPoint(CODE, "3", "4")), "1", "5", key = "influencia"),
                                Block.MapFigure("predio", "Mapa del predio"),
                                Block.Photos("Fotos", listOf(IDENTIFIABLE, LANDSCAPE)),
                                Block.Toc("Índice")
                            )
                        )
                    ),
                people = mapOf(PERSON to MentionTarget(NAME, "Presidente Zz", "Comunidad Zz", CODE, "Caserío Zz")),
                photos =
                    mapOf(
                        IDENTIFIABLE to
                            PhotoRef(IDENTIFIABLE, "f4", "d".repeat(64), "Entrega a [[$PERSON]]", PhotoRef.PHOTO, identifiable = true, consent = true),
                        LANDSCAPE to PhotoRef(LANDSCAPE, "f5", "e".repeat(64), "Vista del valle", PhotoRef.PHOTO, identifiable = false)
                    ),
                facts = listOf(stakeholdersByLocality, trust, stances, internalOnly, amount, indicator, unknownKind, emptyCount, countWithoutCount)
            )
        }
}
