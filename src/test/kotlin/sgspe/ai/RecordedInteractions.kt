package sgspe.ai

import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList

// el registro de interacciones de las pruebas unitarias: anota cada entrada y le da un id, sin base
class RecordedInteractions : InteractionLog {
    val entries = CopyOnWriteArrayList<InteractionEntry>()

    override suspend fun record(entry: InteractionEntry): UUID = UUID.randomUUID().also { entries += entry }
}
