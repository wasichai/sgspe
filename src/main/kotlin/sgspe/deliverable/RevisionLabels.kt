package sgspe.deliverable

// las etiquetas de revisión (RQ-REP-016, ASM-027): los borradores llevan letras (A, B… Z, AA, AB…) y las emisiones números
// (0, 1…). la letra sigue a la del borrador anterior y vuelve a la primera en cada revisión nueva (`reset`: hubo una
// emisión después del borrador anterior); el número sigue a current_revision
object RevisionLabels {
    fun nextDraft(
        previous: String?,
        reset: Boolean,
        scheme: DeliverableProperties.Scheme
    ): String {
        if (previous == null || reset || !previous.all { it in 'A'..'Z' }) return scheme.draftStart
        return letters(number(previous) + 1)
    }

    fun nextIssue(
        current: String?,
        scheme: DeliverableProperties.Scheme
    ): String = current?.toIntOrNull()?.let { (it + 1).toString() } ?: scheme.issueStart.toString()

    // A = 1, Z = 26, AA = 27 (base 26 sin cero)
    private fun number(label: String): Int = label.fold(0) { n, c -> n * 26 + (c - 'A' + 1) }

    private fun letters(n: Int): String {
        var rest = n
        val out = StringBuilder()
        while (rest > 0) {
            val digit = (rest - 1) % 26
            out.insert(0, 'A' + digit)
            rest = (rest - 1) / 26
        }
        return out.toString()
    }
}
