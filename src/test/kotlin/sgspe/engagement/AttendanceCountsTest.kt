package sgspe.engagement

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

// RF-ENG-004 y RQ-DSH-012: suma de represented_people_count de los que asistieron, por sexo y por ámbito
class AttendanceCountsTest {
    @Test
    fun `suma a los que asistieron, una fila de grupo cuenta por las personas que representa`() {
        val counts =
            AttendanceCounts.of(
                listOf(
                    mapOf("attended" to true, "represented_people_count" to 1, "sex" to "FEMENINO", "participant_scope" to "AIDS"),
                    mapOf("attended" to true, "represented_people_count" to 12, "sex" to null, "participant_scope" to "AIIS"),
                    mapOf("attended" to true, "represented_people_count" to 1L, "sex" to "MASCULINO", "participant_scope" to "REGIONAL"),
                    mapOf("attended" to false, "represented_people_count" to 5, "sex" to "FEMENINO", "participant_scope" to "AIDS"),
                    mapOf("attended" to null, "represented_people_count" to 3)
                )
            )
        assertEquals(14L, counts["attendees_total_count"])
        assertEquals(1L, counts["attendees_female_count"])
        assertEquals(1L, counts["attendees_male_count"])
        assertEquals(1L, counts["attendees_direct_area_count"])
        assertEquals(12L, counts["attendees_indirect_area_count"])
        assertEquals(1L, counts["attendees_regional_count"])
        assertEquals(0L, counts["attendees_national_count"])
    }

    @Test
    fun `solo se escriben los conteos que cambian`() {
        val counts = AttendanceCounts.of(emptyList())
        assertEquals(AttendanceCounts.FIELDS.toSet(), AttendanceCounts.changes(emptyMap(), counts).keys)
        assertTrue(AttendanceCounts.changes(AttendanceCounts.FIELDS.associateWith { 0L }, counts).isEmpty())
    }

    @Test
    fun `un conteo guardado como decimal o como cadena igual al calculado no cuenta como cambio`() {
        val counts = AttendanceCounts.of(listOf(mapOf("attended" to true, "represented_people_count" to 4, "participant_scope" to "AIDS")))
        val stored =
            counts.mapValues { (_, value) ->
                java.math.BigDecimal
                    .valueOf(value)
                    .setScale(2)
            }
        assertTrue(AttendanceCounts.changes(stored, counts).isEmpty())
        assertEquals(setOf("attendees_total_count"), AttendanceCounts.changes(stored + ("attendees_total_count" to "5"), counts).keys)
    }

    @Test
    fun `una fila sin personas representadas o con cero cuenta como una persona`() {
        val counts =
            AttendanceCounts.of(
                listOf(
                    mapOf("attended" to true, "represented_people_count" to null),
                    mapOf("attended" to true, "represented_people_count" to 0)
                )
            )
        assertEquals(2L, counts["attendees_total_count"])
    }

    @Test
    fun `un cambio del participante solo mueve los conteos si toca un campo del que dependen`() {
        val before =
            mapOf(
                "engagement" to "e1",
                "attended" to true,
                "represented_people_count" to 3,
                "sex" to "FEMENINO",
                "participant_scope" to "AIDS",
                "notes" to "a"
            )
        assertTrue(AttendanceCounts.affects(null, before))
        assertTrue(AttendanceCounts.affects(before, null))
        assertFalse(AttendanceCounts.affects(before, before + ("phone" to "b")))
        assertFalse(AttendanceCounts.affects(before, before + ("represented_people_count" to 3L)))
        assertTrue(AttendanceCounts.affects(before, before + ("attended" to false)))
        assertTrue(AttendanceCounts.affects(before, before + ("represented_people_count" to 4)))
        assertTrue(AttendanceCounts.affects(before, before + ("sex" to null)))
        assertTrue(AttendanceCounts.affects(before, before + ("participant_scope" to "AIIS")))
        assertTrue(AttendanceCounts.affects(before, before + ("engagement" to "e2")))
    }

    @Test
    fun `todos los conteos son derivados del modelo`() {
        val derived =
            sgspe.common.ModelCatalog
                .fromClasspath()
                .derivedFields(EngagementWriteGuard.ENGAGEMENT)
        assertTrue(derived.containsAll(AttendanceCounts.FIELDS), "faltan en el modelo: ${AttendanceCounts.FIELDS - derived}")
    }
}
