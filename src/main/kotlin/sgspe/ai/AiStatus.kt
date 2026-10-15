package sgspe.ai

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ServerWebExchange
import sgspe.common.Callers
import sgspe.common.StrictQuery
import wasichai.core.identity.AuthenticatedUser

data class AiStatusResponse(
    val enabled: Boolean,
    val providerAvailable: Boolean,
    val useCases: List<String>
)

// GET /api/sgspe/ai/status (api.md §4.14, ai-architecture.md §12.1): responde siempre a toda persona autenticada. enabled dice si
// su organización tiene la IA activada; providerAvailable, si hay proveedor; useCases, los casos que le ofrecen sus permisos:
// vacío si la organización no tiene la IA y, con ella, los mismos haya o no proveedor (el portal dice por qué no se usan)
@RestController
@RequestMapping("/api/sgspe/ai/status")
class AiStatus(
    private val callers: Callers,
    private val activation: AiActivation,
    private val offers: List<AiUseCaseOffer>
) {
    @GetMapping
    suspend fun status(exchange: ServerWebExchange): AiStatusResponse {
        StrictQuery.requireOnly(exchange.request.queryParams, emptySet())
        return compose(activation.organizationEnabled(), activation.providerAvailable(), callers.require(), offers)
    }

    companion object {
        suspend fun compose(
            enabled: Boolean,
            providerAvailable: Boolean,
            user: AuthenticatedUser,
            offers: List<AiUseCaseOffer>
        ): AiStatusResponse =
            AiStatusResponse(
                enabled,
                providerAvailable,
                // un caso con dos objetos (AI-04) se ofrece una vez
                if (enabled) {
                    offers
                        .filter { it.offered(user) }
                        .map { it.useCase }
                        .distinct()
                        .sorted()
                } else {
                    emptyList()
                }
            )
    }
}
