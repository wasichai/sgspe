package sgspe.common

import kotlinx.coroutines.currentCoroutineContext

// si la escritura en curso la hace el código de sgspe (lleva la marca SgspeWrite). fuera de sgspe.common nadie nombra la
// marca (ArchitectureTest): las guardas y los listeners de los paquetes de dominio preguntan aquí
object WriteMark {
    suspend fun present(): Boolean = currentCoroutineContext()[SgspeWrite.Key] != null
}
