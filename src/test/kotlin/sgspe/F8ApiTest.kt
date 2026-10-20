package sgspe

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import org.springframework.core.io.buffer.DefaultDataBufferFactory
import org.springframework.http.HttpStatus
import reactor.core.publisher.Flux
import sgspe.ai.TestDocuments
import sgspe.common.IfMatch
import sgspe.deliverable.DeckGenerationJob
import sgspe.deliverable.DeliverableDoubles
import sgspe.deliverable.DeliverableGenerationJob
import sgspe.deliverable.Deliverables
import sgspe.privacy.CanonicalJson
import sgspe.privacy.ReportMeta
import sgspe.privacy.ReportModel
import wasichai.core.common.WasichaiException
import wasichai.files.FileService
import wasichai.files.FileUpload
import java.security.MessageDigest
import java.util.HexFormat
import java.util.UUID

// la base de las pruebas de integración de F8 (plan 8.1; los planes 8.2b y 8.2c la usan): lo de F7ApiTest más los
// entregables como los escribirán sus endpoints. deliverable y deliverable_section se crean por la API genérica (no son
// apiOnly); deliverable_version y deliverable_template son apiOnly y appendOnly: su archivo lo sube una persona con
// FileService.stage y el registro lo crea esa misma persona en proceso (asUser), como hacen DeliverableIssue y
// DeliverableTemplates, que escriben como la persona. reportScenario arma el proyecto y los
// datos de T2 y T1 (§5 de la especificación de F8). todo ficticio (RQ-SEC-018). las ayudas son bloqueantes. desde el plan
// 8.2b, JOB-05 corre con los dobles de DeliverableDoubles (PDF y mapas) y adjunta como la plataforma: requestDraft y generate
@Import(DeliverableDoubles.Config::class)
abstract class F8ApiTest : F7ApiTest() {
    @Autowired
    private lateinit var files: FileService

    @Autowired
    protected lateinit var generationJob: DeliverableGenerationJob

    @Autowired
    protected lateinit var fakePdf: DeliverableDoubles.FakePdfRenderer

    @Autowired
    protected lateinit var fakeMaps: DeliverableDoubles.FakeReportMaps

    @Autowired
    protected lateinit var deckJob: DeckGenerationJob

    @Autowired
    protected lateinit var fakeSlides: DeliverableDoubles.FakeSlidesRenderer

    // el admin de la prueba como Account, para subir y crear como él
    protected fun admin(): Account = me().let { Account(token, it["email"].asString(), it["userId"].asString()) }

    // un entregable en draft creado por `creator` (el admin si es null), con motivo (requiresReason) y el periodo de setiembre
    protected fun deliverable(
        project: String,
        type: String = FIELD_REPORT,
        attributes: Map<String, Any?> = emptyMap(),
        creator: Account? = null
    ): String =
        createRecord(
            "deliverable",
            mapOf(
                "project" to project,
                "document_code" to uniqueName("ENT-"),
                "title" to "Informe de campo y avance de la Comunidad Ejemplo",
                "deliverable_type" to type,
                "period_start_date" to "2026-09-01",
                "period_end_date" to "2026-09-30",
                "cutoff_date" to "2026-09-30"
            ) + attributes,
            withReason("alta de prueba"),
            creator?.token ?: token
        )

    // una sección MIXTA de texto humano; la clave es única en su entregable. desde el plan 8.2b el alta de un T2 ya crea las del
    // tipo (sectionId): la clave por defecto es una que el tipo no declara
    protected fun section(
        deliverable: String,
        key: String = uniqueName("anexo-"),
        attributes: Map<String, Any?> = emptyMap(),
        creator: Account? = null
    ): String =
        createRecord(
            "deliverable_section",
            mapOf(
                "deliverable" to deliverable,
                "section_key" to key,
                "title" to "Antecedentes",
                "sort_order" to 2,
                "section_kind" to "MIXTA",
                "content" to "La Comunidad Ejemplo recibió la visita del equipo social en setiembre.",
                "content_origin" to "HUMANO"
            ) + attributes,
            token = creator?.token ?: token
        )

    // la sección `key` que el alta del entregable creó (DeliverableSectionsListener, plan 8.2b): su id
    protected fun sectionId(
        deliverable: String,
        key: String
    ): String =
        tree(send("GET", "/api/objects/deliverable_section/records?deliverable=$deliverable&section_key=$key", null, HttpStatus.OK))["content"]
            .values()
            .single()["id"]
            .asString()

    // `bytes` subidos para el campo FILE `field` de `objectName` como `account`, declarados `type`: el id de la subida, que
    // solo esa persona adjunta (StoredFileGuard)
    protected fun stage(
        account: Account,
        objectName: String,
        field: String,
        bytes: ByteArray,
        fileName: String,
        type: String
    ): String =
        asUser(account) {
            files.stage(objectName, field, FileUpload(fileName, type, Flux.just(DefaultDataBufferFactory.sharedInstance.wrap(bytes)))).id.toString()
        }

    // los códigos de una escritura en proceso rechazada (createAs): el `message` de cada violación de la WasichaiException, que
    // no está en su toString()
    protected fun codesOf(error: Throwable?): List<String> = (error as? WasichaiException)?.violations.orEmpty().map { it.message }

    // un alta en proceso como `account` (Records lleva la marca SgspeWrite): la vía de un objeto apiOnly con archivos
    protected fun createAs(
        account: Account,
        objectName: String,
        attributes: Map<String, Any?>,
        reason: String
    ): String = asUser(account) { platformRecords.create(objectName, attributes, reason) }.id.toString()

    // una plantilla .dotx con `templateKey`, subida declarada como DOCX (el sniffer de wasichai-files no conoce el tipo de
    // .dotx; ASM-135) y creada por `account`, como lo hará POST /api/sgspe/deliverable-templates después de validar
    protected fun deliverableTemplate(
        templateKey: String = "informe-campo",
        account: Account = admin()
    ): String {
        val file = stage(account, "deliverable_template", "file", TestDocuments.docx("Plantilla de la Asociación Ejemplo"), "plantilla-$templateKey.dotx", DOCX)
        return createAs(
            account,
            "deliverable_template",
            mapOf("template_key" to templateKey, "name" to "Plantilla de prueba", "file" to file),
            "plantilla de prueba"
        )
    }

    // una versión emitida como la creará DeliverableIssue (plan 8.2b): INTERNO con DOCX, PDF y modelo; EXTERNO solo con PDF y
    // `derivedFrom`, con el SHA-256 del modelo de su base. los SHA-256 son los de los archivos subidos
    protected fun deliverableVersion(
        deliverable: String,
        number: Int = 1,
        level: String = "INTERNO",
        derivedFrom: String? = null,
        account: Account = admin()
    ): String {
        val pdf = TestDocuments.pdf("Informe de campo y avance", "Nivel $level")
        val attributes =
            mutableMapOf<String, Any?>(
                "deliverable" to deliverable,
                "version_number" to number,
                "revision" to "0",
                "issued_at" to "2026-10-13T15:00:00Z",
                "issued_by_user_id" to account.id,
                "issued_by_user_email" to account.email,
                "disclosure_level" to level,
                "pdf" to stage(account, "deliverable_version", "pdf", pdf, "entregable-$number.pdf", "application/pdf"),
                "pdf_sha256" to sha256(pdf),
                "model_sha256" to sha256(MODEL),
                "template_sha256" to sha256(TEMPLATE),
                "derived_from" to derivedFrom
            )
        if (level == "INTERNO") {
            val docx = TestDocuments.docx("Informe de campo y avance")
            attributes["docx"] = stage(account, "deliverable_version", "docx", docx, "entregable-$number.docx", DOCX)
            attributes["docx_sha256"] = sha256(docx)
            attributes["model"] = stage(account, "deliverable_version", "model", MODEL, "modelo-$number.json", "text/plain")
        }
        return createAs(account, "deliverable_version", attributes.filterValues { it != null }, "emisión de prueba")
    }

    // el entregable llevado a `approved` por la API genérica (submit_for_review y approve de WF-H, con motivo) por una
    // gerencia social asignada a su proyecto, con un borrador LISTO (readyDraft): DeliverableWriteGuard lo pide desde 8.2b
    protected fun approvedDeliverable(
        project: String,
        manager: Account = member("SOCIAL_MANAGER", project),
        model: ByteArray = READY_MODEL
    ): String {
        val id = deliverable(project, creator = manager)
        readyDraft(id, model = model)
        move("deliverable", id, "submit_for_review", manager.token, "envío de prueba")
        move("deliverable", id, "approve", manager.token, "aprobación de prueba")
        return id
    }

    // un borrador LISTO sin pasar por JOB-05 (plan 8.2b): los siete archivos subidos y adjuntos por `account` en una escritura de
    // derivados con la marca, como lo hace la plataforma en JOB-05; el modelo es READY_MODEL u otro (un T1 con hechos tipados,
    // para las presentaciones). devuelve el SHA-256 del modelo
    protected fun readyDraft(
        deliverable: String,
        account: Account = admin(),
        model: ByteArray = READY_MODEL
    ): String {
        val docx = TestDocuments.docx("Informe de campo y avance")
        val bytes =
            mapOf(
                Deliverables.DRAFT_MODEL to Triple(model, "modelo.json", "text/plain"),
                Deliverables.DRAFT_INTERNAL_DOCX to Triple(docx, "borrador.docx", DOCX),
                Deliverables.DRAFT_INTERNAL_PDF to Triple(PDF, "borrador.pdf", "application/pdf"),
                Deliverables.DRAFT_EXTERNAL_PDF to Triple(PDF, "borrador-externo.pdf", "application/pdf"),
                Deliverables.CANDIDATE_INTERNAL_DOCX to Triple(docx, "candidata.docx", DOCX),
                Deliverables.CANDIDATE_INTERNAL_PDF to Triple(PDF, "candidata.pdf", "application/pdf"),
                Deliverables.CANDIDATE_EXTERNAL_PDF to Triple(PDF, "candidata-externa.pdf", "application/pdf")
            )
        val ids = bytes.mapValues { (field, file) -> stage(account, "deliverable", field, file.first, file.second, file.third) }
        val sha = sha256(model)
        asUser(account) {
            platformRecords.patch(
                "deliverable",
                UUID.fromString(deliverable),
                ids +
                    mapOf(
                        Deliverables.STATUS to Deliverables.READY,
                        Deliverables.GENERATED_AT to
                            java.time.Instant
                                .now()
                                .toString(),
                        Deliverables.MODEL_SHA256 to sha,
                        Deliverables.DRAFT_REVISION to "A"
                    ),
                "borrador de prueba"
            )
        }
        return sha
    }

    // emite por POST /api/sgspe/deliverables/{id}/issue como `account`, con motivo e If-Match
    protected fun issue(
        deliverable: String,
        account: Account,
        sha: String = record("deliverable", deliverable)["attributes"][Deliverables.MODEL_SHA256].asString(),
        checklist: Boolean = true
    ): Response =
        exchange(
            "POST",
            "/api/sgspe/deliverables/$deliverable/issue",
            mapOf("draftModelSha256" to sha, "checklistConfirmed" to checklist),
            account.token,
            mapOf(IfMatch.HEADER to "\"${record("deliverable", deliverable)["updatedAt"].asString()}\"") + withReason("emisión de prueba")
        )

    // pide un borrador por su endpoint (POST …/drafts con motivo) como `account`
    protected fun requestDraft(
        deliverable: String,
        account: Account = admin()
    ): Response = exchange("POST", "/api/sgspe/deliverables/$deliverable/drafts", null, account.token, withReason("borrador de prueba"))

    // una vuelta de JOB-05; los archivos los sube y adjunta la plataforma (PlatformDraftFiles)
    protected fun generate(): Int = runBlocking { generationJob.runOnce() } ?: 0

    // una vuelta de JOB-09 (ADR-0031); el compositor es el doble FakeSlidesRenderer
    protected fun compose(): Int = runBlocking { deckJob.runOnce() } ?: 0

    // un T2 listo para pedir su borrador: el escenario de reportScenario con su entregable INFORME DE CAMPO Y AVANCE
    protected fun requestedFieldReport(scenario: ReportScenario = reportScenario()): String {
        val response = requestDraft(scenario.fieldReport)
        assertEquals(HttpStatus.ACCEPTED, response.status, response.body)
        return scenario.fieldReport
    }

    // los datos de un T2 y un T1 de prueba (§5 de la especificación de F8): «Proyecto Ejemplo A» en UTM 17S, «Comunidad Ejemplo»
    // con territorio, un sector, una red con su nodo, un incidente EXPULSION, un aporte en `delivered` con su comprobante y
    // dos fotos de la entrega (una con consentimiento y otra identificable sin el dato), el AIDS aprobado con la comunidad,
    // un estudio con un instrumento y el entregable de cada tipo. todo como el admin, salvo el ciclo del aporte (quien lo
    // registra, SOCIAL_INVESTMENT_MANAGER, y APPROVER) y la aprobación del área (SOCIAL_MANAGER). desde el plan 8.2c, una localidad del AIDS con su perfil del estudio (capítulo 5 de T1). una foto sin consentimiento se crea sin el campo:
    // wasichai aplica el defaultValue de un campo omitido en el alta (UPS-15 resuelta), y photo_consent_given no tiene uno
    protected fun reportScenario(): ReportScenario {
        val project = project(mapOf("code" to uniqueName("PEA-"), "name" to "Proyecto Ejemplo A", "working_crs" to "EPSG 32717"))
        val community =
            runBlocking { communityWithTerritory("Comunidad Ejemplo", SyntheticGeo.multiSquare(SyntheticGeo.ORIGIN.first, SyntheticGeo.ORIGIN.second, 4000.0)) }
                .toString()
        val sector = createRecord("community_sector", mapOf("community" to community, "name" to "Sector Ejemplo Alto"))
        val network = createRecord("community_network", mapOf("project" to project, "community" to community, "name" to "Red de la Comunidad Ejemplo"))
        val node = createRecord("network_node", mapOf("community_network" to network, "person" to person("Persona Ejemplo 1")))
        val incident = incident(project, attributes = mapOf("incident_type" to "EXPULSION", "place" to "Comunidad Ejemplo"))
        val investor = member("SOCIAL_INVESTMENT_MANAGER", project)
        val contribution = approvedContribution(project, investor, procure = true)
        val delivery = contributionDelivery(contribution, project, listOf("ACTA DE RECEPCION", "COMPROBANTE"))
        val consented = photo(project, delivery, identifiable = true, consent = true)
        val withoutConsent = photo(project, delivery, identifiable = true, consent = null)
        val receipt =
            createRecord(
                "receipt",
                mapOf(
                    "social_contribution" to contribution,
                    "receipt_type" to "BOLETA DE VENTA",
                    "amount" to 500,
                    "currency" to "PEN"
                )
            )
        // P8 de WF-P: la entrega con conformidad, el acta de recepción, el comprobante y las fotos de la entrega
        move("social_contribution", contribution, "register_delivery", investor.token, "entrega de prueba")
        val area =
            createWith(
                "area_of_influence",
                mapOf("project" to project, "area_type" to "AIDS", "version_number" to 1),
                mapOf("boundary" to SyntheticGeo.multiSquare(SyntheticGeo.ORIGIN.first, SyntheticGeo.ORIGIN.second, 6000.0))
            )
        createRecord("area_of_influence_member", mapOf("area_of_influence" to area, "community" to community, "member_condition" to "INCLUIDO"))
        // plan 8.2c: una localidad del AIDS con su perfil del estudio, para el capítulo 5 de T1
        val locality = runBlocking { locality("Caserío Ejemplo Alto", SyntheticGeo.point(SyntheticGeo.ORIGIN.first, SyntheticGeo.ORIGIN.second)) }.toString()
        createRecord("area_of_influence_member", mapOf("area_of_influence" to area, "locality" to locality, "member_condition" to "INCLUIDO"))
        approveArea(area, project)
        val study = createRecord("study", mapOf("project" to project, "title" to "Diagnóstico social de la Comunidad Ejemplo"))
        fixture(
            "locality_profile",
            mapOf(
                "locality" to locality,
                "study" to study,
                "families_count" to 120,
                "religion" to "Católica.",
                "electoral_process" to "Dos movimientos locales."
            )
        )
        createRecord("study_instrument", mapOf("study" to study, "instrument_type" to Model.enums.getValue("instrument_type").first()))
        val fieldReport = deliverable(project, FIELD_REPORT, mapOf("community" to community))
        val diagnosis = deliverable(project, SOCIAL_DIAGNOSIS, mapOf("study" to study, "area_of_influence" to area))
        return ReportScenario(
            project,
            community,
            sector,
            network,
            node,
            incident,
            contribution,
            delivery,
            consented,
            withoutConsent,
            receipt,
            area,
            study,
            fieldReport,
            diagnosis,
            locality
        )
    }

    // una foto del aporte vinculada a su entrega: `consent` null es «sin dato», que EXTERNO trata como sin consentimiento
    private fun photo(
        project: String,
        delivery: String,
        identifiable: Boolean,
        consent: Boolean?
    ): String {
        val attributes =
            mapOf(
                "file" to stagedFile("evidence", "file"),
                "evidence_type" to "FOTO",
                "classification" to "INTERNA",
                "project" to project,
                "people_identifiable" to identifiable,
                "photo_consent_given" to consent
            ).filterValues { it != null }
        val response = createEvidence(attributes, listOf(mapOf("object" to "delivery", "recordId" to delivery)))
        check(response.status == HttpStatus.CREATED) { "${response.status} ${response.body}" }
        return tree(response.body)["id"].asString()
    }

    // la aprobación de una versión de área (WF-O) por su endpoint, como una gerencia social asignada al proyecto
    private fun approveArea(
        area: String,
        project: String
    ) {
        val manager = member("SOCIAL_MANAGER", project)
        send(
            "POST",
            "/api/sgspe/areas-of-influence/$area/approve",
            null,
            HttpStatus.OK,
            manager.token,
            mapOf(IfMatch.HEADER to "\"${record("area_of_influence", area)["updatedAt"].asString()}\"", REASON to reason("aprobación de prueba"))
        )
    }

    protected data class ReportScenario(
        val project: String,
        val community: String,
        val sector: String,
        val network: String,
        val node: String,
        val incident: String,
        val contribution: String,
        val delivery: String,
        val consentedPhoto: String,
        val photoWithoutConsent: String,
        val receipt: String,
        val area: String,
        val study: String,
        val fieldReport: String,
        val socialDiagnosis: String,
        val locality: String
    )

    companion object {
        const val FIELD_REPORT = "INFORME DE CAMPO Y AVANCE"
        const val SOCIAL_DIAGNOSIS = "DIAGNOSTICO SOCIAL"
        const val DOCX = "application/vnd.openxmlformats-officedocument.wordprocessingml.document"

        // el modelo congelado de prueba: JSON canónico (claves ordenadas, sin espacios), en UTF-8
        val MODEL: ByteArray = """{"nivel":"INTERNO","tipo":"INFORME DE CAMPO Y AVANCE","version":1}""".toByteArray()
        private val TEMPLATE: ByteArray = "plantilla base de SGSPE".toByteArray()

        // el modelo de un borrador de prueba (readyDraft): un ReportModel canónico, que la emisión lee (revisión y plantilla)
        val READY_MODEL: ByteArray =
            CanonicalJson.bytes(
                ReportModel(
                    ReportMeta(
                        type = FIELD_REPORT,
                        typeVersion = 1,
                        code = "ENT-PRUEBA",
                        title = "Informe de campo y avance",
                        draftRevision = "A",
                        issueRevision = "0",
                        project = "Proyecto Ejemplo A",
                        dateLabel = "Setiembre, 2026",
                        templateSha256 = sha256(TEMPLATE)
                    ),
                    emptyList()
                )
            )

        fun sha256(bytes: ByteArray): String = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes))
    }
}
