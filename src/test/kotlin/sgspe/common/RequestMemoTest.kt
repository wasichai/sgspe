package sgspe.common

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

// lo que un comando que escribe muchos registros pregunta una sola vez (la asignación de quien importa, ProjectScopeGuard)
class RequestMemoTest {
    @Test
    fun `dentro de within se calcula una vez por clave, y afuera cada vez`() {
        var calls = 0
        val compute: suspend () -> Boolean = {
            calls++
            true
        }
        runBlocking {
            RequestMemo.within {
                repeat(5) { RequestMemo.remember("a", compute) }
                RequestMemo.remember("b", compute)
            }
        }
        assertEquals(2, calls)
        runBlocking { repeat(3) { RequestMemo.remember("a", compute) } }
        assertEquals(5, calls)
    }

    @Test
    fun `un memo no se comparte entre dos comandos`() {
        var calls = 0
        runBlocking {
            repeat(2) {
                RequestMemo.within {
                    RequestMemo.remember("a") {
                        calls++
                        1
                    }
                }
            }
        }
        assertEquals(2, calls)
    }
}
