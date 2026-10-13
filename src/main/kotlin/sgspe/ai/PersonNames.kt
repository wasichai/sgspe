package sgspe.ai

import org.springframework.stereotype.Component
import sgspe.common.Records
import sgspe.common.Values
import wasichai.core.common.ForbiddenException
import wasichai.core.common.NotFoundException
import java.util.UUID

// el diccionario de nombres de quien pregunta (especificación de F7 §2.2.4, ai-architecture.md §12.2): las person que lee, con
// Records y como la persona, con sus nombres y apellidos. sin READ sobre person, o sin esos campos (security.md §7.9), queda vacío:
// AI-05 no exige leerlos, y lo que la persona no lee las herramientas tampoco lo devuelven
@Component
class PersonNames(
    private val records: Records
) {
    suspend fun readable(): NameDictionary {
        val people =
            try {
                records.all(PERSON)
            } catch (_: ForbiddenException) {
                return NameDictionary.EMPTY
            } catch (_: NotFoundException) {
                return NameDictionary.EMPTY
            }
        return of(people.map { it.id to it.attributes })
    }

    companion object {
        private const val PERSON = "person"

        // (id, atributos) de cada person leída
        fun of(rows: List<Pair<String, Map<String, Any?>>>): NameDictionary =
            NameDictionary(
                rows.map { (id, attributes) ->
                    KnownPerson(runCatching { UUID.fromString(id) }.getOrNull(), Values.text(attributes["given_names"]), Values.text(attributes["surnames"]))
                }
            )
    }
}
