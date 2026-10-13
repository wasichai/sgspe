package sgspe.ai

import java.util.concurrent.CopyOnWriteArrayList

// los dobles del puerto StructuredModel (ai-architecture.md §15). FakeModel: salidas fijas según la entrada, sin Embabel.
// MockModel: anota todo lo que se le envía (lo que saldría hacia el proveedor) y contesta con otro doble. FailingModel: un
// proveedor que falla como `reason` después de gastar tokens. escritos a mano: el proyecto usa Mockito, no MockK (decisión 11)
class FakeModel(
    private val answer: (ModelRequest<*>) -> Any
) : StructuredModel {
    override suspend fun <T : Any> complete(request: ModelRequest<T>): ModelResult<T> =
        ModelResult(request.outputClass.cast(answer(request)), request.model, INPUT, OUTPUT)

    companion object {
        const val INPUT = 120
        const val OUTPUT = 30
    }
}

class MockModel(
    private val then: StructuredModel
) : StructuredModel {
    val sent = CopyOnWriteArrayList<ModelRequest<*>>()

    override suspend fun <T : Any> complete(request: ModelRequest<T>): ModelResult<T> {
        sent += request
        return then.complete(request)
    }
}

class FailingModel(
    private val reason: StructuredModelException.Reason
) : StructuredModel {
    override suspend fun <T : Any> complete(request: ModelRequest<T>): ModelResult<T> = throw StructuredModelException(reason, 50, 0)
}
