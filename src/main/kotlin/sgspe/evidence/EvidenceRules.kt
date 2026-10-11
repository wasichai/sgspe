package sgspe.evidence

import sgspe.common.Records
import sgspe.common.Values
import java.util.UUID

// las reglas de una evidencia, puras para las pruebas unitarias (RF-DOC-001, RF-DOC-003, ASM-071). desde F3 el archivo es un
// campo FILE de wasichai-files (decisión D1, WAS-ADR-0061): el registro guarda el descriptor {id, name, contentType, size,
// sha256}, nunca los bytes
object EvidenceRules {
    const val EVIDENCE = "evidence"
    const val FILE = "file"
    const val CLASSIFICATION = "classification"
    const val DOWNLOAD_RESTRICTED = "DOWNLOAD_RESTRICTED"

    // las clasificaciones cuya descarga exige DOWNLOAD_RESTRICTED (RF-DOC-003)
    val RESTRICTED = setOf("CONFIDENCIAL", "DATOS PERSONALES")

    // lo que nunca cambia después del alta: el archivo, la versión a la que reemplaza y la importación de origen. una
    // corrección es una evidencia nueva que reemplaza a la anterior (ASM-013)
    val IMMUTABLE = listOf(FILE, "replaces", "import_batch_id")

    // lo que PATCH /api/sgspe/evidence/{id} cambia (api.md §4.9): los datos descriptivos, nunca el archivo
    val DESCRIPTIVE = setOf("title", CLASSIFICATION, "document_date", "author", "captured_at", "people_identifiable", "photo_consent_given")

    // el id del archivo de un valor FILE: el descriptor que da una lectura, o el id tal cual lo manda un alta
    fun fileId(value: Any?): String? =
        when (value) {
            is Map<*, *> -> Values.text(value["id"])
            else -> Values.text(value)
        }

    fun restricted(classification: Any?): Boolean = Values.text(classification) in RESTRICTED

    // si descargar exige DOWNLOAD_RESTRICTED, con el registro como lo lee quien llama: falla cerrado, así que una
    // clasificación que quien llama no lee (el permiso de campo la quita del registro) o que falta cuenta como restringida
    fun downloadRestricted(attributes: Map<String, Any?>): Boolean {
        val classification = attributes[CLASSIFICATION]
        return Values.text(classification) == null || restricted(classification)
    }

    // lo que del descriptor de un campo FILE no lee quien no descarga una evidencia restringida (UPS-24): el nombre original,
    // que suele llevar el nombre o el documento de una persona, y la huella SHA-256, que deja confirmar si alguien tiene un
    // archivo concreto. quedan el id (la plataforma lo exige), el tipo y el tamaño
    val IDENTIFYING = setOf("name", "sha256")

    fun reserved(descriptor: Map<String, Any?>): Map<String, Any?> = descriptor.filterKeys { it !in IDENTIFYING }

    // bajar la clasificación: de una restringida a una que no lo es (ASM-071)
    fun downgrade(
        before: Any?,
        after: Any?
    ): Boolean = restricted(before) && !restricted(after)

    // los campos inmutables que la escritura cambia
    fun changedImmutable(
        before: Map<String, Any?>,
        after: Map<String, Any?>
    ): List<String> =
        IMMUTABLE.filter { field ->
            if (field == FILE) fileId(before[field]) != fileId(after[field]) else !Values.same(before[field], after[field])
        }

    // la relación MANY_TO_MANY de evidence hacia un objeto (database-model.md §16): evidence_<objeto>
    fun linkRelationship(target: String): String = "${EVIDENCE}_$target"

    const val DELIVERY = "delivery"

    // la clave del candado del padre de una entrega (Transactions, «el orden acordado»): el compromiso (DELIVERY_FROZEN de WF-D)
    // o, desde F5, el aporte (DELIVERY_FROZEN de WF-P). null sin padre. los nombres son literales: sgspe.evidence no depende de
    // sgspe.commitment ni de sgspe.investment
    fun deliveryLock(attributes: Map<String, Any?>): String? =
        Records.uuid(attributes["commitment"])?.let { "commitment:$it" }
            ?: Records.uuid(attributes["social_contribution"])?.let { "social_contribution:$it" }

    // el orden en que el alta vincula: vincular una entrega toma el candado de su padre hasta el fin de la transacción, así que
    // dos altas con entregas de los mismos padres en orden inverso se interbloquearían. primero va lo que no toma candado, en el
    // orden pedido (los demás vínculos y una entrega sin padre conocido, cuyo vínculo falla por su cuenta), y después las
    // entregas por la clave ascendente del candado de su padre y, dentro de una, por su id. `locks`: esa clave, por entrega
    fun lockOrder(
        links: List<EvidenceLink>,
        locks: Map<UUID, String>
    ): List<EvidenceLink> {
        val (locking, free) = links.partition { it.objectName == DELIVERY && it.recordId in locks }
        return free + locking.sortedWith(compareBy({ locks.getValue(it.recordId) }, { it.recordId.toString() }))
    }
}
