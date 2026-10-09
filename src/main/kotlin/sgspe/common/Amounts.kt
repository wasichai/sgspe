package sgspe.common

import org.springframework.http.HttpStatus
import java.math.BigDecimal

// un monto o una cantidad que llega en el cuerpo de un comando, con cotas antes de hacer cuentas con él (la lección de la
// Tarea 17 de F2: un número con un exponente enorme agota la memoria al sumarlo). hasta 15 dígitos enteros y 4 decimales, y
// nunca negativo: un monto de compromiso o de entrega no pasa de ahí. un texto de más de 40 caracteres ni se convierte
object Amounts {
    const val MAX_TEXT = 40
    const val MAX_INTEGER_DIGITS = 15
    const val MAX_SCALE = 4

    // null si no vino; 400 INVALID_NUMBER si no es un número y OUT_OF_RANGE si se sale de las cotas, sobre `field`
    fun parse(
        value: Any?,
        field: String
    ): BigDecimal? {
        if (value == null || (value is String && value.isBlank())) return null
        val text = value.toString().trim()
        val number =
            if (text.length > MAX_TEXT) {
                null
            } else {
                Values.decimal(if (value is Number) value else text)
                    ?: throw Finding("INVALID_NUMBER", "$field debe ser un número", field, status = HttpStatus.BAD_REQUEST).toCodedException()
            }
        if (number == null || number.signum() < 0 || number.precision() - number.scale() > MAX_INTEGER_DIGITS || number.scale() > MAX_SCALE) {
            throw Finding(
                "OUT_OF_RANGE",
                "$field debe ser un número de 0 a ${"9".repeat(MAX_INTEGER_DIGITS)} con a lo sumo $MAX_SCALE decimales",
                field,
                status = HttpStatus.BAD_REQUEST
            ).toCodedException()
        }
        return number
    }
}
