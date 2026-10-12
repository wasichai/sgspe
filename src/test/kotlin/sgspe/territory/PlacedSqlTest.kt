package sgspe.territory

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.UUID

// el SQL de una fuente de registros ubicados (gis.md §2.4 y §5.2 b): la geometría propia o, sin ella, el punto del primer
// destino con geometría; sin nada que dibujar no sale; el periodo por la fecha de cada fuente; el proyecto por el campo de
// una raíz o por el padre de un hijo; el recuadro sobre lo que se dibuja; y el corte por id
class PlacedSqlTest {
    private val tables =
        object : GateTables {
            override fun table(objectName: String) = "<$objectName>"

            override fun column(
                objectName: String,
                field: String
            ) = "\"$field\""

            override fun srid(
                objectName: String,
                field: String
            ) = 4326
        }

    private val october = Period(LocalDate.parse("2026-10-01"), LocalDate.parse("2026-10-31"))

    private fun source(
        layer: String,
        objectName: String
    ): PlacedSource =
        ManagementLayerCatalog.CATALOG
            .getValue(layer)
            .sources
            .single { it.objectName == objectName }

    private val commitment = source("LYR-19", "commitment")

    private fun render(
        source: PlacedSource,
        projects: List<UUID> = emptyList(),
        period: Period? = null,
        box: LayerBox? = null,
        limit: Int = 100
    ): Pair<String, List<Any>> {
        val values = mutableListOf<Any>()
        val sql =
            PlacedSql.sql(tables, { value ->
                values += value
                ":g${values.size - 1}"
            }, source, projects, period, box, limit)
        return sql to values
    }

    @Test
    fun `la geometria propia va primero, despues el punto de la localidad y al final la comunidad`() {
        val (sql, _) = render(commitment)
        assertTrue(sql.contains("LEFT JOIN <locality> r0 ON r0.\"id\" = e.\"locality\" LEFT JOIN <community> r1 ON r1.\"id\" = e.\"community\""), sql)
        assertTrue(
            sql.contains(
                "COALESCE(ST_Transform(e.\"location\", 4326), COALESCE(ST_Transform(r0.\"location\", 4326), " +
                    "ST_PointOnSurface(ST_Transform(r0.\"boundary\", 4326))), COALESCE(ST_PointOnSurface(ST_Transform(r1.\"territory\", 4326)))) AS g"
            ),
            sql
        )
        assertTrue(sql.contains("CASE WHEN ST_Transform(e.\"location\", 4326) IS NOT NULL THEN 'own' WHEN"), sql)
        assertTrue(sql.contains("THEN 'community' END AS kind"), sql)
        assertTrue(sql.contains("CASE WHEN ST_Transform(e.\"location\", 4326) IS NOT NULL THEN NULL::uuid WHEN"), sql)
        assertTrue(sql.contains("THEN r1.\"id\" END AS target"), sql)
    }

    @Test
    fun `un registro sin geometria ni destino con geometria no sale y el resto se corta en el limite por id`() {
        val (sql, values) = render(commitment, limit = 7)
        assertTrue(sql.endsWith("WHERE x.g IS NOT NULL ORDER BY x.id LIMIT :g0"), sql)
        assertEquals(listOf<Any>(7), values)
        // sin periodo ni proyecto la tabla del objeto no se filtra: solo la organización y los alcances de la compuerta
        assertFalse(sql.contains("e.\"community\" WHERE"), sql)
        // una fuente sin relaciones solo se dibuja con su geometría
        val (route, _) = render(source("LYR-18", "field_visit"))
        assertFalse(route.contains("LEFT JOIN"), route)
        assertTrue(route.contains("CASE WHEN ST_Transform(e.\"route\", 4326) IS NOT NULL THEN 'own' END AS kind"), route)
    }

    @Test
    fun `cada fuente acota el periodo por su fecha, en instantes de Lima, en dias o por un tramo que lo cruza`() {
        val (held, heldValues) = render(source("LYR-18", "engagement"), period = october)
        assertTrue(held.contains("WHERE e.\"held_at\" >= :g0 AND e.\"held_at\" < :g1"), held)
        assertEquals(listOf<Any>(OffsetDateTime.parse("2026-10-01T05:00Z"), OffsetDateTime.parse("2026-11-01T05:00Z")), heldValues.take(2))
        val (visit, visitValues) = render(source("LYR-18", "field_visit"), period = october)
        assertTrue(visit.contains("WHERE e.\"actual_start_date\" <= :g0 AND (e.\"actual_end_date\" IS NULL OR e.\"actual_end_date\" >= :g1)"), visit)
        assertEquals(listOf<Any>(LocalDate.parse("2026-10-31"), LocalDate.parse("2026-10-01")), visitValues.take(2))
        val (agreed, agreedValues) = render(commitment, period = october)
        assertTrue(agreed.contains("WHERE e.\"agreed_date\" >= :g0 AND e.\"agreed_date\" <= :g1"), agreed)
        assertEquals(listOf<Any>(LocalDate.parse("2026-10-01"), LocalDate.parse("2026-10-31")), agreedValues.take(2))
        // una fuente sin fecha no recibe periodo: ManagementLayers no se lo pasa (periodNotApplied)
        val (risk, _) = render(source("LYR-21", "social_risk"))
        assertFalse(risk.contains("e.\"social_risk\" WHERE"), risk)
    }

    @Test
    fun `una raiz se acota por su proyecto y un hijo por el proyecto de su padre`() {
        val project = UUID.randomUUID()
        val (own, ownValues) = render(commitment, projects = listOf(project))
        assertTrue(own.contains("WHERE e.\"project\" = ANY(:g0)"), own)
        assertEquals(listOf(project), (ownValues[0] as Array<*>).toList())
        val (child, _) = render(source("LYR-23", "delivery"), projects = listOf(project))
        assertTrue(
            child.contains(
                "WHERE (e.\"commitment\" IN (SELECT p.\"id\" FROM <commitment> p WHERE p.\"project\" = ANY(:g0)) OR " +
                    "e.\"social_contribution\" IN (SELECT p.\"id\" FROM <social_contribution> p WHERE p.\"project\" = ANY(:g1)))"
            ),
            child
        )
        val (observation, _) = render(source("LYR-18", "field_observation"), projects = listOf(project))
        assertTrue(observation.contains("(e.\"field_visit\" IN (SELECT p.\"id\" FROM <field_visit> p WHERE p.\"project\" = ANY(:g0)))"), observation)
    }

    @Test
    fun `el recuadro se aplica a lo que se dibuja, propio o del destino`() {
        val (sql, values) = render(commitment, box = LayerBox(-78.6, -8.0, -78.4, -7.8), limit = 5)
        assertTrue(sql.contains("WHERE x.g IS NOT NULL AND ST_Intersects(x.g, ST_MakeEnvelope(:g0, :g1, :g2, :g3, 4326)) ORDER BY x.id LIMIT :g4"), sql)
        assertEquals(listOf<Any>(-78.6, -8.0, -78.4, -7.8, 5), values)
    }

    @Test
    fun `lo que lee la consulta es el objeto como principal, los destinos y los padres solo con proyecto`() {
        val reads = PlacedSql.reads(commitment, listOf(UUID.randomUUID()), october)
        assertEquals(GateRead("commitment", setOf("location", "locality", "community", "agreed_date", "project"), principal = true), reads[0])
        assertEquals(listOf(GateRead("locality", setOf("location", "boundary")), GateRead("community", setOf("territory"))), reads.drop(1))
        val delivery = source("LYR-23", "delivery")
        assertEquals(listOf(GateRead("delivery", setOf("location"), principal = true)), PlacedSql.reads(delivery, emptyList(), null))
        val withProject = PlacedSql.reads(delivery, listOf(UUID.randomUUID()), null)
        assertEquals(setOf("location", "commitment", "social_contribution"), withProject[0].fields)
        assertEquals(listOf(GateRead("commitment", setOf("project")), GateRead("social_contribution", setOf("project"))), withProject.drop(1))
    }

    @Test
    fun `LayerBox lee el recuadro ya normalizado y nada si no hay`() {
        assertEquals(LayerBox(-78.6, -8.0, -78.4, -7.8), LayerBox.of(Layers.normalizeBbox("-78.6,-8,-78.4,-7.8")))
        assertNull(LayerBox.of(null))
        // un recuadro que cubre el globo no filtra (normalizeBbox devuelve null)
        assertNull(LayerBox.of(Layers.normalizeBbox("-425,-100,275,100")))
    }

    @Test
    fun `una evidencia sin proyecto no sale cuando se pide project`() {
        val project = UUID.randomUUID()
        val (sql, _) = render(source("LYR-24", "evidence"), projects = listOf(project))
        assertTrue(sql.contains("WHERE e.\"project\" = ANY(:g0)"), sql)
    }
}
