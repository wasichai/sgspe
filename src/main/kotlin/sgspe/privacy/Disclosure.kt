package sgspe.privacy

// los niveles de divulgación (catálogo «Nivel de divulgación», ASM-037, deliverables.md §9): INTERNO nominal, EXTERNO
// con las personas por cargo o código y sin datos personales, PUBLICO solo con agregados. en ningún nivel salen el
// documento de identidad ni los teléfonos ni los correos de las personas (RQ-REP-014, RQ-REP-022)
enum class Disclosure {
    INTERNO,
    EXTERNO,
    PUBLICO;

    companion object {
        fun parse(raw: Any?): Disclosure? = entries.firstOrNull { it.name == raw?.toString() }
    }
}

// el valor que se escribe donde quien exporta no puede leer el campo: el mismo criterio que los indicadores (RQ-DSH-032)
const val NOT_AVAILABLE = "no disponible"
