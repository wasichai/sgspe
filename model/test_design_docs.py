"""Pruebas de design_docs.py: lee de los documentos lo que dicen, con casos que se comprueban a mano en cada documento.

Correr: cd model && python3 -m unittest -v test_design_docs
"""
import unittest

import design_docs


class CardsTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.cards = design_docs.cards()

    def test_lee_las_fichas_de_los_89_objetos_y_las_tablas_fijas(self):
        catalog = design_docs.matrix()
        self.assertEqual(len(catalog), 89)
        self.assertTrue(set(catalog) <= set(self.cards))
        self.assertEqual(sum(1 for name in catalog if self.cards[name].phase == "F2"), 48)

    def test_la_ficha_de_project(self):
        card = self.cards["project"]
        self.assertEqual((card.label, card.phase, card.workflow, card.actions), ("Proyecto", "F2", "WF-A", ["APPROVE"]))
        self.assertTrue(card.description.startswith("Proyecto extractivo (o prospecto)"))
        fields = {f["name"]: f for f in card.fields}
        self.assertEqual((fields["code"]["type"], fields["code"]["required"], fields["code"]["unique"]), ("TEXT", True, True))
        self.assertEqual((fields["working_crs"]["type"], fields["working_crs"]["enum"]), ("ENUM", "working_crs"))
        self.assertEqual((fields["interest_area"]["type"], fields["interest_area"]["geometryType"]), ("GEOMETRY", "MULTIPOLYGON"))
        self.assertEqual(fields["interest_threshold"]["default"], "3")
        self.assertIsNone(fields["interest_threshold"]["phase"])
        self.assertEqual((fields["due_soon_days"]["phase"], fields["grievance_ack_days"]["phase"]), ("F3", "F4"))
        self.assertEqual(card.unique, ["code"])
        rel = card.relationships[0]
        self.assertEqual((rel["name"], rel["type"], rel["field"], rel["target"]), ("project_concession", "MANY_TO_MANY", None, "mining_concession"))

    def test_derivados_restringidos_unicos_compuestos_e_indices(self):
        stakeholder = {f["name"]: f for f in self.cards["stakeholder"].fields}
        self.assertTrue(stakeholder["stance"]["derived"] and stakeholder["stance"]["restricted"])
        self.assertEqual(self.cards["stakeholder"].unique_constraints, [["project", "person"], ["project", "organization"]])
        self.assertEqual(self.cards["stakeholder"].exactly_one, [["person", "organization"]])
        # el bullet de únicos de area_of_influence_member sigue en la línea siguiente
        self.assertEqual(len(self.cards["area_of_influence_member"].unique_constraints), 3)
        self.assertEqual(self.cards["engagement"].indexed, ["scheduled_at", "held_at"])
        self.assertEqual(self.cards["stakeholder_assessment"].indexes, [["stakeholder", "assessment_date"]])
        self.assertEqual(self.cards["stakeholder_assessment"].flags, {"appendOnly": True, "apiOnly": True, "requiresReason": True})

    def test_las_relaciones_llevan_su_fase_y_las_inversas_se_marcan(self):
        cards = self.cards
        alliance = {r["name"]: r for r in cards["strategic_alliance"].relationships}
        self.assertEqual(design_docs.relationship_phase(cards, cards["strategic_alliance"], alliance["strategic_alliance_agreement"]), "F3")
        self.assertEqual(design_docs.relationship_phase(cards, cards["strategic_alliance"], alliance["strategic_alliance_organization"]), "F2")
        self.assertTrue(next(r for r in cards["topic"].relationships if r["name"] == "engagement_topic")["inverse"])
        self.assertTrue(next(r for r in cards["land_right"].relationships if r["field"] == "holder_person")["restricted"])


class CatalogTests(unittest.TestCase):
    def test_las_opciones_salen_del_analisis_sin_notas(self):
        catalogs = design_docs.catalogs()
        self.assertEqual(catalogs["stakeholder_scope"], ["AIDS", "AIIS", "REGIONAL", "NACIONAL"])
        self.assertEqual(catalogs["working_crs"], ["EPSG 32717", "EPSG 32718", "EPSG 32719"])
        self.assertEqual(catalogs["relationship_status"],
                         ["SIN CONTACTO", "CONTACTO ESTABLECIDO", "RELACION ACTIVA", "RELACION DETERIORADA", "INACTIVA"])
        self.assertEqual((catalogs["perception_subject"], catalogs["perception_polarity"]),
                         (["MINERIA", "PROYECTO", "EMPRESA"], ["POSITIVA", "NEGATIVA"]))
        self.assertEqual(catalogs["impact_sign"], ["POSITIVO", "NEGATIVO"])
        for key, options in catalogs.items():
            for option in options:
                self.assertNotIn("(", option, key)


class SecurityTests(unittest.TestCase):
    def test_fases_de_los_roles(self):
        phases, roles = design_docs.role_phases()
        self.assertEqual(phases["FIELD_AGENT"], "F2")
        self.assertEqual(phases["APPROVER"], "F3")
        self.assertEqual(roles["GAN"], "GIS_ANALYST")
        self.assertNotIn("AUDITOR", phases)

    def test_celdas_de_la_matriz(self):
        matrix = design_docs.matrix()
        self.assertEqual(matrix["project"]["PROJECT_ADMIN"], ["READ", "CREATE", "UPDATE", "APPROVE"])
        self.assertEqual(matrix["project"]["GIS_ANALYST"], ["READ", "u"])
        self.assertEqual(matrix["area_of_influence"]["PROJECT_MANAGER"], ["READ", "u", "APPROVE"])
        self.assertEqual(matrix["engagement_participant"]["FIELD_AGENT"], ["READ", "CREATE", "UPDATE", "DELETE"])
        self.assertEqual(matrix["person"]["PROJECT_ADMIN"], [])
        self.assertEqual(matrix["export_log"]["SOCIAL_MANAGER"], ["READ", "CREATE"])

    def test_campos_restringidos(self):
        restricted = {(obj, field): readers for obj, field, readers in design_docs.restricted_fields()}
        self.assertEqual(restricted[("stakeholder", "stance")], ["SOCIAL_MANAGER", "STAKEHOLDER_MANAGER"])
        self.assertEqual(len(restricted[("person", "given_names")]), 11)
        self.assertIn(("grievance", "person"), restricted)
        # DOWNLOAD_RESTRICTED es una acción, no un rol
        self.assertNotIn("DOWNLOAD_RESTRICTED", restricted[("ai_suggestion", "evidence_quote")])


class WorkflowStatesTests(unittest.TestCase):
    def test_estados_de_domain_model(self):
        states = design_docs.workflow_states()
        self.assertEqual(states["project"]["INITIAL"], [("draft", "Borrador")])
        self.assertEqual(states["stakeholder"]["FINAL"], [])
        self.assertEqual(states["stakeholder"]["INTERMEDIATE"][-1], ("retired", "Retirado"))
        self.assertEqual(states["ai_suggestion"], states["commitment_candidate"])


if __name__ == "__main__":
    unittest.main()
