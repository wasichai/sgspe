package sgspe.privacy

import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.KotlinModule
import java.security.MessageDigest
import java.util.HexFormat

// la forma canónica del modelo del informe (especificación de F8 §3.2): claves ordenadas, UTF-8, sin espacios, sin
// entradas nulas y los decimales como texto (el modelo no lleva números de coma flotante: uno es un error, no se redondea).
// su SHA-256 es draft_model_sha256, y el archivo draft_model guarda estos mismos bytes, así que el SHA-256 de su descriptor
// es igual. `read` vuelve al modelo: JOB-05 genera desde lo leído, la prueba de que un solo archivo basta para volver a
// generar (RQ-REP-026)
object CanonicalJson {
    private val mapper: JsonMapper = JsonMapper.builder().addModule(KotlinModule.Builder().build()).build()

    fun bytes(model: ReportModel): ByteArray = write(mapper.convertValue(model, Map::class.java)).toByteArray(Charsets.UTF_8)

    fun read(bytes: ByteArray): ReportModel = mapper.readValue(bytes, ReportModel::class.java)

    // los bytes canónicos de cualquier otro valor con la misma forma (la especificación de una presentación, ADR-0031)
    fun bytesOf(value: Any): ByteArray = write(mapper.convertValue(value, Map::class.java)).toByteArray(Charsets.UTF_8)

    fun sha256(bytes: ByteArray): String = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes))

    // el texto canónico de un árbol de mapas, listas y valores simples
    fun write(value: Any?): String = StringBuilder().also { append(it, value) }.toString()

    private fun append(
        out: StringBuilder,
        value: Any?
    ) {
        when (value) {
            null -> out.append("null")
            is String -> string(out, value)
            is Boolean -> out.append(value)
            is Int, is Long, is Short, is Byte -> out.append(value)
            is Map<*, *> -> {
                out.append('{')
                value.entries
                    .filter { it.value != null }
                    .sortedBy { it.key.toString() }
                    .forEachIndexed { i, (key, item) ->
                        if (i > 0) out.append(',')
                        string(out, key.toString())
                        out.append(':')
                        append(out, item)
                    }
                out.append('}')
            }
            is Iterable<*> -> {
                out.append('[')
                value.forEachIndexed { i, item ->
                    if (i > 0) out.append(',')
                    append(out, item)
                }
                out.append(']')
            }
            else -> throw IllegalArgumentException("El modelo del informe no admite ${value.javaClass.simpleName}: los decimales van como texto")
        }
    }

    private fun string(
        out: StringBuilder,
        text: String
    ) {
        out.append('"')
        for (c in text) {
            when {
                c == '"' -> out.append("\\\"")
                c == '\\' -> out.append("\\\\")
                c == '\n' -> out.append("\\n")
                c == '\r' -> out.append("\\r")
                c == '\t' -> out.append("\\t")
                c < ' ' -> out.append("\\u%04x".format(c.code))
                else -> out.append(c)
            }
        }
        out.append('"')
    }
}
