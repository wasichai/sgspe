package sgspe.common

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ServerWebExchange
import wasichai.core.common.FieldViolation
import wasichai.core.common.ValidationException
import java.time.Clock
import java.time.LocalDate
import java.util.UUID

// el código que el portal propone en un alta (decisión 6 de docs/ux-revision/plan.md): PREFIJO-AAAA-NNN con el año de Lima y
// el siguiente número de ese prefijo y ese año en el proyecto (COM-2026-031, design-system.md §3). es una propuesta: el campo
// sigue editable y `code` no es único en el modelo. se cuenta lo que quien llama lee (el alcance por proyecto y las QCS
// confidenciales): contar lo que no lee diría cuántos registros no ve
object NextCodes {
    val PREFIXES: Map<String, String> =
        mapOf(
            "commitment" to "COM",
            "agreement" to "ACU",
            "grievance" to "QUE",
            "inquiry" to "CON",
            "stakeholder_request" to "SOL",
            "social_risk" to "RSG",
            "incident" to "INC",
            "social_contribution" to "APO",
            "social_program" to "PRG"
        )

    // el mayor número de los códigos PREFIJO-AAAA-NNN más uno, con tres cifras como mínimo; los demás códigos no cuentan
    fun next(
        prefix: String,
        year: Int,
        codes: Collection<String?>
    ): String {
        val pattern = Regex("^${Regex.escape(prefix)}-$year-(\\d+)$")
        val max =
            codes
                .mapNotNull { code ->
                    code
                        ?.trim()
                        ?.let { pattern.find(it) }
                        ?.groupValues
                        ?.get(1)
                        ?.toIntOrNull()
                }.maxOrNull() ?: 0
        return "%s-%d-%03d".format(prefix, year, max + 1)
    }
}

data class NextCodeResponse(
    val code: String
)

// GET /api/sgspe/codes/next?object=…&project=… (api.md §4.1): el código propuesto para un alta del objeto en el proyecto. lee como
// quien llama: sin READ sobre el objeto, 403; un proyecto que no lee, 404 (ADR-0029). un objeto sin prefijo o un proyecto que no
// es un id, 400; otro parámetro, 422
@RestController
@RequestMapping("/api/sgspe/codes/next")
class NextCodesController(
    private val records: Records,
    private val clock: Clock
) {
    @GetMapping
    suspend fun next(exchange: ServerWebExchange): NextCodeResponse {
        val params = exchange.request.queryParams
        StrictQuery.requireOnly(params, setOf(OBJECT, PROJECT))
        val objectName = params.getFirst(OBJECT).orEmpty()
        val prefix =
            NextCodes.PREFIXES[objectName]
                ?: throw ValidationException("El objeto no tiene código propuesto", OBJECT, "se admiten ${NextCodes.PREFIXES.keys.sorted().joinToString(", ")}")
        val project =
            params.getFirst(PROJECT)?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                ?: throw ValidationException("Falta el proyecto", PROJECT, "envíe el id del proyecto")
        records.get(PROJECT, project)
        val codes = records.all(objectName, mapOf(PROJECT to project.toString())).map { it.attributes["code"] as? String }
        return NextCodeResponse(NextCodes.next(prefix, LocalDate.now(clock).year, codes))
    }

    companion object {
        private const val OBJECT = "object"
        private const val PROJECT = "project"
    }
}
