package sgspe.deliverable

// lo que una plantilla debe traer y lo único que puede traer (especificación de F8 §2.2.12, ASM-135): los estilos que usa
// DocxWriter, por su nombre integrado de Word (el id cambia con el idioma de Word: «Ttulo1» en español, «Heading1» en
// inglés; el nombre «heading 1» no), las fuentes libres que Gotenberg trae y los marcadores fijos de la portada, el
// encabezado y el pie, sin lenguaje de expresiones
object TemplateStyles {
    const val NORMAL = "Normal"
    const val TITLE = "Title"
    const val HEADING_1 = "heading 1"
    const val HEADING_2 = "heading 2"
    const val HEADING_3 = "heading 3"

    // opcional: la historia de cada localidad (5.1); una plantilla sin él usa el de nivel 3
    const val HEADING_4 = "heading 4"
    const val CAPTION = "caption"
    const val LIST = "List Paragraph"

    val REQUIRED: List<String> = listOf(NORMAL, TITLE, HEADING_1, HEADING_2, HEADING_3, CAPTION, LIST)

    val FONTS: Set<String> = setOf("Carlito", "Caladea", "Liberation Sans", "Liberation Serif")

    val PLACEHOLDERS: Set<String> =
        setOf("titulo", "proyecto", "preparado_para", "suscrito_por", "direccion_autora", "fecha", "codigo", "revision", "nivel", "mapa_portada")

    val PLACEHOLDER = Regex("""\{\{\s*([^{}]*?)\s*}}""")

    const val COVER_MAP = "mapa_portada"
}
