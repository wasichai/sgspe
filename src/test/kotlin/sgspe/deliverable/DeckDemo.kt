package sgspe.deliverable

import kotlinx.coroutines.runBlocking
import sgspe.privacy.CanonicalJson
import sgspe.privacy.ReportImage
import sgspe.privacy.ReportMapImages
import sgspe.privacy.ReportModel
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Font
import java.awt.Polygon
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.time.LocalDate
import java.util.Base64
import javax.imageio.ImageIO

// un T1 sintético con material suficiente para la revisión visual de las presentaciones (deliverables.md §16.9): el conjunto
// completo de T1Source con ocho localidades más, sus perfiles, seis meses de evaluaciones (uno con menos de 5 y otro sin
// ninguna), interacciones de varios tipos, trabajo de campo por localidad, once compromisos, cinco riesgos y percepciones con
// cita. determinista: sin azar ni fechas del reloj. todo ficticio (RQ-SEC-018)
object DeckDemo {
    private fun id(n: Int) = T1Source.id(n)

    private val PLACES =
        listOf(
            "Centro Poblado Alfa" to 1180,
            "Comunidad Beta" to 960,
            "Caserío Gamma" to 610,
            "Anexo Delta" to 420,
            "Caserío Épsilon" to 380,
            "Anexo Zeta" to 260,
            "Caserío Eta" to 190,
            "Anexo Theta" to 150
        )

    // cuántas evaluaciones con puntaje hay en cada mes de abril a setiembre, y su promedio en décimas
    private val MONTHS =
        listOf(
            "2026-04" to (9 to 32),
            "2026-05" to (3 to 29),
            "2026-06" to (7 to 34),
            "2026-07" to (0 to 0),
            "2026-08" to (18 to 37),
            "2026-09" to (12 to 39)
        )

    fun source(): FakeReportSource {
        val s = T1Source.full()
        var n = 1000
        val places =
            PLACES.mapIndexed { i, (name, population) ->
                val locality = id(n++)
                s.add("locality", locality, null, "name" to name, "locality_type" to "CASERIO", "recognition_status" to "RECONOCIDO")
                s.add(
                    "area_of_influence_member",
                    id(n++),
                    null,
                    "area_of_influence" to T1Source.AIDS,
                    "locality" to locality,
                    "member_condition" to "INCLUIDO",
                    "studied_in_diagnosis" to true
                )
                s.add(
                    "locality_profile",
                    id(n++),
                    null,
                    "locality" to locality,
                    "study" to T1Source.STUDY,
                    "families_count" to population / 4,
                    "population_count" to population
                )
                // el trabajo de campo: más entrevistas en las localidades grandes
                repeat(maxOf(1, 6 - i)) { k ->
                    s.add(
                        "engagement",
                        id(n++),
                        "held",
                        "project" to T1Source.PROJECT,
                        "study" to T1Source.STUDY,
                        "locality" to locality,
                        "engagement_type" to if (k % 3 == 2) "FICHA DE OBSERVACION" else "ENTREVISTA",
                        "held_at" to "2026-0${4 + k % 6}-1${k % 9}T10:00:00Z"
                    )
                }
                locality
            }
        // las interacciones del periodo (setiembre), de varios tipos
        val types = listOf("REUNION" to 14, "REUNION INFORMATIVA" to 9, "TALLER" to 6, "ASAMBLEA" to 4, "LLAMADA" to 3, "VISITA GUIADA" to 2)
        types.forEach { (type, count) ->
            repeat(count) { k ->
                s.add(
                    "engagement",
                    id(n++),
                    "held",
                    "project" to T1Source.PROJECT,
                    "locality" to places[k % places.size],
                    "engagement_type" to type,
                    "held_at" to "2026-09-%02dT15:00:00Z".format(1 + k)
                )
            }
        }
        // las evaluaciones: un grupo de interés por evaluación, con puntajes enteros que suman lo más cerca posible del promedio
        MONTHS.forEachIndexed { m, (month, spec) ->
            val (count, tenths) = spec
            val total = (count * tenths + 5) / 10
            repeat(count) { k ->
                val organization = id(n++)
                val stakeholder = id(n++)
                s.add("organization", organization, null, "name" to "Organización Ejemplo ${m + 1}-${k + 1}")
                s.add("stakeholder", stakeholder, null, "project" to T1Source.PROJECT, "organization" to organization, "stakeholder_scope" to "AIDS")
                val score = total / count + if (k < total % count) 1 else 0
                s.add(
                    "stakeholder_assessment",
                    id(n++),
                    null,
                    "stakeholder" to stakeholder,
                    "assessment_date" to "$month-15",
                    "trust_score" to score.coerceIn(1, 5),
                    "communication_sufficient" to (k % 3 != 0),
                    "information_adequate" to (k % 4 != 0),
                    "stance_changed" to (k % 5 == 0)
                )
            }
        }
        // los compromisos: siete vencidos (cinco en ejecución y dos bloqueados), uno por vencer y tres al día
        val commitments =
            listOf(
                Triple("VENCIDO", "in_progress", 5),
                Triple("VENCIDO", "blocked", 2),
                Triple("POR VENCER", "in_progress", 1),
                Triple("AL DIA", "approved", 3)
            )
        commitments.forEach { (status, state, count) ->
            repeat(count) { s.add("commitment", id(n++), state, "project" to T1Source.PROJECT, "deadline_status" to status) }
        }
        // los riesgos: cinco activos con su última evaluación
        listOf(Triple("RS-2", 4, 3), Triple("RS-3", 2, 3), Triple("RS-4", 3, 2), Triple("RS-5", 3, 2)).forEach { (code, probability, impact) ->
            val risk = id(n++)
            s.add("social_risk", risk, "active", "project" to T1Source.PROJECT, "code" to code, "title" to "Riesgo $code", "category" to "AGUA")
            s.add(
                "risk_assessment",
                id(n++),
                null,
                "social_risk" to risk,
                "assessment_date" to "2026-09-20",
                "probability" to probability,
                "impact" to impact,
                "residual" to false
            )
        }
        // percepciones con cita, atribuidas por cargo
        listOf(
            Triple(
                "PROYECTO",
                "Piden información antes de cada obra",
                "Nos enteramos de la obra cuando ya estaban las máquinas en el canal" to "Presidente de una junta de usuarios de riego"
            ),
            Triple("EMPRESA", "Valoran el empleo local, pero lo ven escaso", "Trabajan tres de la comunidad y el resto viene de fuera" to "Teniente gobernador")
        ).forEach { (subject, statement, quote) ->
            s.add(
                "perception",
                id(n++),
                null,
                "study" to T1Source.STUDY,
                "subject" to subject,
                "polarity" to "NEGATIVA",
                "perception_scope" to "AIDS",
                "statement" to statement,
                "quote" to quote.first,
                "attributed_role" to quote.second
            )
        }
        return s
    }

    fun input(): AssemblyInput =
        T1Source
            .input(
                mapOf(
                    "necesidades" to
                        "1. Sumar un relacionista comunitario para las localidades altas.\n" +
                        "2. Reprogramar con las comunidades los compromisos vencidos.\n" +
                        "3. Informar a las juntas de usuarios antes de cada obra en el canal."
                )
            ).let { input ->
                AssemblyInput(
                    input.deliverable.copy(
                        attributes =
                            input.deliverable.attributes + ("document_code" to "DEMO-T1-001") + ("title" to "Diagnóstico social de las áreas de influencia") +
                                ("period_start_date" to "2026-04-01")
                    ),
                    input.sections,
                    input.templateId,
                    input.templateSha256,
                    input.draftRevision,
                    input.issueRevision,
                    LocalDate.of(2026, 10, 15)
                )
            }

    // el modelo con un mapa de áreas sintético (en el ensamblado real lo dibuja JOB-05 con GeoServer)
    fun model(): ReportModel =
        runBlocking { SocialDiagnosisType().assemble(input(), source()) }.model.let { model ->
            val image = map()
            model.copy(maps = mapOf(DiagnosisStudy.AREAS_MAP to ReportMapImages(image, image)))
        }

    // un mapa de áreas dibujado con Java 2D: el AIDS relleno, el AIIS en línea, un río, las localidades y una barra de escala
    private fun map(): ReportImage {
        val width = 1600
        val height = 1000
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        val g = image.createGraphics()
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g.color = Color(0xEEF0EA)
            g.fillRect(0, 0, width, height)
            g.color = Color(0x9DB9D3)
            g.stroke = BasicStroke(6f)
            g.drawPolyline(intArrayOf(0, 300, 520, 800, 1100, 1600), intArrayOf(820, 700, 760, 600, 640, 420), 6)
            val aiis = Polygon(intArrayOf(180, 640, 1180, 1460, 1300, 700, 260), intArrayOf(240, 120, 160, 420, 860, 920, 700), 7)
            val aids = Polygon(intArrayOf(420, 760, 1040, 1120, 900, 520), intArrayOf(320, 260, 330, 600, 760, 640), 6)
            g.color = Color(0x1F, 0x4E, 0x79, 46)
            g.fillPolygon(aids)
            g.color = Color(0x1F4E79)
            g.stroke = BasicStroke(4f)
            g.drawPolygon(aids)
            g.stroke = BasicStroke(3f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f, floatArrayOf(18f, 12f), 0f)
            g.drawPolygon(aiis)
            g.font = Font(Font.SANS_SERIF, Font.PLAIN, 26)
            PLACES.forEachIndexed { i, (name, _) ->
                val x = 480 + (i % 4) * 160
                val y = 380 + (i / 4) * 200
                g.color = Color(0x17202B)
                g.fillOval(x - 9, y - 9, 18, 18)
                g.drawString(name, x + 16, y + 9)
            }
            g.stroke = BasicStroke(4f)
            g.drawLine(1240, 940, 1440, 940)
            g.drawString("0           2 km", 1236, 925)
        } finally {
            g.dispose()
        }
        val out = ByteArrayOutputStream()
        ImageIO.write(image, "png", out)
        val bytes = out.toByteArray()
        return ReportImage(Base64.getEncoder().encodeToString(bytes), CanonicalJson.sha256(bytes))
    }

    // un T2 sintético con material para la revisión visual: el de FieldReportTypeTest con visitas, actividades de varios tipos,
    // redes con sus integrantes, ítems del aporte, una foto sin personas, un periodo de tres meses y un mapa del predio
    fun t2(): Pair<ReportModel, Map<String, ByteArray>> {
        val fixture = FieldReportTypeTest()
        val s = fixture.source()
        val project = T1Source.id(1)
        val community = T1Source.id(2)
        var n = 5000
        listOf(
            Triple("2026-07-08", "Reconocimiento del caserío", "Se presentó el equipo a la junta directiva."),
            Triple("2026-07-22", "Visita a las familias del sector alto", "Se recogieron las preocupaciones por el agua."),
            Triple("2026-08-12", "Faena comunal", "El equipo acompañó la limpieza del canal."),
            Triple("2026-09-03", "Asamblea comunal", "Se presentó el avance de los compromisos."),
            Triple("2026-09-18", "Entrega del apoyo", "Se entregaron herramientas para la faena.")
        ).forEach { (day, title, outcome) ->
            s.add(
                "field_visit",
                T1Source.id(n++),
                "completed",
                "project" to project,
                "title" to title,
                "actual_start_date" to day,
                "outcome_summary" to outcome
            )
        }
        listOf("REUNION" to 7, "ASAMBLEA" to 3, "FAENA COMUNITARIA" to 2, "CONTACTO CARA A CARA" to 9, "LLAMADA" to 4).forEach { (type, count) ->
            repeat(count) { k ->
                s.add(
                    "engagement",
                    T1Source.id(n++),
                    "held",
                    "project" to project,
                    "community" to community,
                    "engagement_type" to type,
                    "title" to "Actividad $type ${k + 1}",
                    "held_at" to "2026-0${7 + k % 3}-1${k % 9}T10:00:00Z"
                )
            }
        }
        listOf("Red de regantes" to 6, "Red de mujeres" to 5).forEach { (name, members) ->
            val network = T1Source.id(n++)
            s.add("community_network", network, null, "project" to project, "community" to community, "name" to name, "contact_status" to "EN CONTACTO")
            repeat(members) { s.add("network_node", T1Source.id(n++), null, "community_network" to network, "node_role" to "INTEGRANTE") }
        }
        listOf("SERVICIO" to "Transporte de la faena" to "350", "BIEN" to "Víveres para la faena" to "420").forEach { (kind, amount) ->
            s.add(
                "contribution_item",
                T1Source.id(n++),
                null,
                "social_contribution" to T1Source.id(10),
                "item_kind" to kind.first,
                "description" to kind.second,
                "line_amount" to amount
            )
        }
        val input = fixture.input()
        val wide =
            AssemblyInput(
                input.deliverable.copy(attributes = input.deliverable.attributes + ("period_start_date" to "2026-07-01") + ("document_code" to "DEMO-T2-001")),
                input.sections +
                    SourceRow(
                        T1Source.id(n++),
                        null,
                        mapOf(
                            "section_key" to "recomendaciones",
                            "content" to
                                "1. Acordar con la junta el calendario de las faenas.\n2. Informar a la red de regantes antes de cada obra en el canal.",
                            "content_origin" to "HUMANO"
                        )
                    ),
                input.templateId,
                input.templateSha256,
                input.draftRevision,
                input.issueRevision,
                input.today
            )
        val model = runBlocking { FieldReportType().assemble(wide, s) }.model
        val image = map()
        // la foto con consentimiento del aporte, como un paisaje dibujado (sin personas)
        val photo =
            BufferedImage(1200, 800, BufferedImage.TYPE_INT_RGB).let { img ->
                val g = img.createGraphics()
                g.color = Color(0x8FB3D9)
                g.fillRect(0, 0, 1200, 400)
                g.color = Color(0x6E8B4E)
                g.fillPolygon(intArrayOf(0, 300, 620, 900, 1200, 1200, 0), intArrayOf(520, 300, 460, 280, 420, 800, 800), 7)
                g.dispose()
                ByteArrayOutputStream().also { ImageIO.write(img, "jpg", it) }.toByteArray()
            }
        val photos = model.photos.keys.associateWith { photo }
        return model.copy(
            maps = mapOf(FieldReportType.LAND_MAP to ReportMapImages(image, image), FieldReportType.COVER_MAP to ReportMapImages(image, image))
        ) to
            photos
    }
}
