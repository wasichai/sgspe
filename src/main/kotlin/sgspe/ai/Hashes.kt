package sgspe.ai

import java.security.MessageDigest

// la huella SHA-256 en hexadecimal: los prompts publicados (PromptManifestTest) y la referencia de una pregunta al asistente
// (input_reference de AI-05, ai-architecture.md §9.2)
object Hashes {
    fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    fun sha256(text: String): String = sha256(text.toByteArray(Charsets.UTF_8))
}
