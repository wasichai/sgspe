package sgspe.common

import wasichai.core.common.ForbiddenException
import wasichai.core.data.RecordResponse
import java.util.UUID

// lo que una regla no puede leer como quien escribe (P12): un objeto sin READ es un bloqueo OBJECT_NOT_READABLE, nunca un
// pase en silencio. vive en common desde F4: lo usan las reglas de compromisos y ProjectLinkGuard (sgspe.scope)
class NotReadable(
    val objectName: String
) : RuntimeException("sin lectura de $objectName")

// lee como quien escribe; sin READ sobre el objeto, NotReadable
suspend fun <T> readAs(
    objectName: String,
    block: suspend () -> T
): T =
    try {
        block()
    } catch (_: ForbiddenException) {
        throw NotReadable(objectName)
    }

// el registro `id` de `objectName` leído como quien escribe. falla cerrada (P12): sin READ, fuera del alcance o inexistente es
// NotReadable, y el mensaje no distingue «no existe» de «no se lee» para no revelar si existe
suspend fun Records.readable(
    objectName: String,
    id: UUID
): RecordResponse = readAs(objectName) { find(objectName, id) } ?: throw NotReadable(objectName)
