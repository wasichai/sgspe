package sgspe.common

// el texto de una celda de una exportación XLSX o CSV (RQ-SEC-020, security.md §11.4): lo que empieza con «=», «+», «-»,
// «@», tabulador o retorno de carro lo toma una hoja de cálculo como fórmula al editarlo o al convertirlo, y `code` y `name`
// vienen de archivos subidos. se antepone «'» para que sea texto. solo para cadenas: los números se escriben como números
fun safeCell(value: String): String = if (value.isNotEmpty() && value[0] in FORMULA_PREFIXES) "'$value" else value

private val FORMULA_PREFIXES = charArrayOf('=', '+', '-', '@', '\t', '\r')
