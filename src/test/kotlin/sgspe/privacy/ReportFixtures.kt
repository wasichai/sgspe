package sgspe.privacy

// un modelo de informe de prueba con todo lo sensible marcado: una mención de «Persona Ejemplo 1», un párrafo restringido,
// una celda con el emisor de un comprobante, una foto con consentimiento, una identificable sin el dato, la imagen del
// comprobante y un mapa con sus dos niveles. todo ficticio (RQ-SEC-018)
object ReportFixtures {
    const val PERSON = "person:11111111-1111-1111-1111-111111111111"
    const val CONSENTED = "22222222-2222-2222-2222-222222222222"
    const val WITHOUT_CONSENT = "33333333-3333-3333-3333-333333333333"
    const val RECEIPT = "44444444-4444-4444-4444-444444444444"

    fun model(): ReportModel =
        ReportModel(
            meta =
                ReportMeta(
                    type = "INFORME DE CAMPO Y AVANCE",
                    typeVersion = 1,
                    code = "ENT-001",
                    title = "Informe de campo y avance",
                    draftRevision = "A",
                    issueRevision = "0",
                    project = "Proyecto Ejemplo A",
                    periodStart = "2026-09-01",
                    periodEnd = "2026-09-30",
                    dateLabel = "Setiembre, 2026",
                    templateSha256 = "0".repeat(64)
                ),
            sections =
                listOf(
                    ReportSection(
                        "relaciones",
                        "Construcción de relaciones",
                        "MIXTA",
                        listOf(
                            Block.Paragraph("Se conversó con [[$PERSON]] sobre la faena."),
                            Block.Paragraph("Religión de la localidad: católica.", Mark.restricted("locality_profile.religion")),
                            Block.Table(
                                "Comprobantes del apoyo",
                                listOf("Tipo", "Número", "Emisor", "Monto"),
                                listOf(
                                    listOf(
                                        Cell("BOLETA DE VENTA"),
                                        Cell("B001-123", mark = Mark.REGISTRY),
                                        Cell("Asociación Ejemplo", mark = Mark.HOLDER),
                                        Cell("500.00")
                                    )
                                )
                            ),
                            Block.Photos("Entrega del apoyo", listOf(CONSENTED, WITHOUT_CONSENT)),
                            Block.Photos("Comprobante", listOf(RECEIPT)),
                            Block.MapFigure("predio", "Mapa del predio")
                        )
                    )
                ),
            people = mapOf(PERSON to MentionTarget("Persona Ejemplo 1", "Presidente", "Comunidad Ejemplo", "GI-111111111111", "Caserío Ejemplo")),
            maps =
                mapOf(
                    "predio" to ReportMapImages(ReportImage("aW50ZXJubw==", "1".repeat(64)), ReportImage("ZXh0ZXJubw==", "2".repeat(64), omittedLayers = true))
                ),
            photos =
                mapOf(
                    CONSENTED to PhotoRef(CONSENTED, "f1", "a".repeat(64), "Entrega", PhotoRef.PHOTO, identifiable = true, consent = true),
                    WITHOUT_CONSENT to PhotoRef(WITHOUT_CONSENT, "f2", "b".repeat(64), "Entrega", PhotoRef.PHOTO, identifiable = true),
                    RECEIPT to PhotoRef(RECEIPT, "f3", "c".repeat(64), "Comprobante", PhotoRef.RECEIPT, identifiable = false)
                )
        )
}
