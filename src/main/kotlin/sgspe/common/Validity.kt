package sgspe.common

import java.time.LocalDate

// la vigencia de una asignación a un proyecto (security.md §9.2): active y, si tiene fechas, hoy entre ellas, con los
// extremos incluidos. vive en common porque el alcance (sgspe.scope) y las exportaciones (sgspe.privacy) la necesitan y
// ninguno de los dos puede depender del otro (architecture.md §5.3)
object Validity {
    fun inForce(
        active: Boolean,
        startDate: LocalDate?,
        endDate: LocalDate?,
        today: LocalDate
    ): Boolean = active && (startDate == null || !today.isBefore(startDate)) && (endDate == null || !today.isAfter(endDate))
}
