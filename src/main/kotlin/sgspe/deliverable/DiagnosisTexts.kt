package sgspe.deliverable

// los textos fijos de T1 (especificación de F8 §3.8; deliverables.md §5): src/main/resources/sgspe/deliverable/t1/<nombre>.md,
// sin el resto de otra plantilla que trae DOC-01 (CON-012) y con AIDS y AIIS canónicos (CON-001)
object DiagnosisTexts {
    fun text(name: String): String =
        DiagnosisTexts::class.java
            .getResourceAsStream("/sgspe/deliverable/t1/$name.md")
            ?.use { String(it.readAllBytes(), Charsets.UTF_8) }
            ?.trim()
            ?: error("falta el texto fijo t1/$name.md")
}
