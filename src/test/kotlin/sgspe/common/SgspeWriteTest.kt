package sgspe.common

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

// la marca vive en la corrutina: dentro de withContext(SgspeWrite) se ve, fuera no
class SgspeWriteTest {
    @Test
    fun `la marca se ve dentro de su contexto y no fuera`() {
        runBlocking {
            assertNull(currentCoroutineContext()[SgspeWrite.Key])
            withContext(SgspeWrite) { assertSame(SgspeWrite, currentCoroutineContext()[SgspeWrite.Key]) }
            assertNull(currentCoroutineContext()[SgspeWrite.Key])
        }
    }
}
