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

    @Test
    fun `el memo de la peticion guarda lo de quien llama y no lo de un comando`() {
        var caller = 0
        var command = 0
        runBlocking {
            RequestMemo.forRequest {
                repeat(3) {
                    RequestMemo.rememberForRequest<Boolean>("permisos") {
                        caller++
                        true
                    }
                    // lo que un comando no cambia pero una petición sí puede cambiar: sin un comando abierto, cada vez
                    RequestMemo.remember<Boolean>("asignacion") {
                        command++
                        true
                    }
                }
                // un comando dentro de la petición: su memo dura lo que el comando y ve el de la petición
                RequestMemo.within {
                    repeat(2) {
                        RequestMemo.remember<Boolean>("asignacion") {
                            command++
                            true
                        }
                        RequestMemo.rememberForRequest<Boolean>("permisos") {
                            caller++
                            true
                        }
                    }
                }
            }
        }
        assertEquals(1, caller)
        assertEquals(4, command)
    }

    @Test
    fun `sin memo de la peticion, lo de quien llama se guarda en el del comando`() {
        var calls = 0
        runBlocking {
            RequestMemo.within {
                repeat(3) {
                    RequestMemo.rememberForRequest<Boolean>("permisos") {
                        calls++
                        true
                    }
                }
            }
            RequestMemo.rememberForRequest<Boolean>("permisos") {
                calls++
                true
            }
        }
        assertEquals(2, calls)
    }
}
