"""Pruebas de design_docs.py: lee de los documentos lo que dicen, con casos que se comprueban a mano en cada documento.

Correr: cd model && python3 -m unittest -v test_design_docs
"""
import os
import shutil
import tempfile
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


class TotalsTests(unittest.TestCase):
    """Totales fijos sobre los documentos reales: si una tabla cambia de forma, el lector devuelve menos y esto falla."""

    @classmethod
    def setUpClass(cls):
        cls.cards = design_docs.cards()

    def test_totales_de_las_fichas(self):
        cards = self.cards
        self.assertEqual(len(cards), 93)
        f2 = [c for c in cards.values() if c.phase == "F2"]
        fields = [f for c in f2 for f in c.fields if design_docs.field_phase(c, f) == "F2"]
        rels = [r for c in f2 for r in c.relationships
                if design_docs.relationship_phase(cards, c, r) == "F2" and not r["inverse"]]
        self.assertEqual((len(fields), len(rels)), (390, 113))
        # 78 claves en §4; las 45 de F2 son las de los campos ENUM que llegan en F2
        self.assertEqual(len(design_docs.enum_keys()), 78)
        self.assertEqual(len({f["enum"] for f in fields if f["type"] == "ENUM"}), 45)
        self.assertTrue(all(c.phase for c in cards.values()))
        self.assertTrue(all(c.fields for c in cards.values()))

    def test_totales_de_seguridad(self):
        phases, _ = design_docs.role_phases()
        self.assertEqual(len(phases), 14)
        self.assertEqual(len(design_docs.restricted_fields()), 24)
        for obj, row in design_docs.matrix().items():
            self.assertEqual(set(row), set(phases), obj)

    def test_totales_de_workflows(self):
        states = design_docs.workflow_states()
        self.assertEqual(len(states), 19)
        self.assertEqual(sum(len(v) for per in states.values() for v in per.values()), 113)
        for card in self.cards.values():
            if card.workflow:
                self.assertIn(card.name, states)


class WorkflowTransitionsTests(unittest.TestCase):
    def test_transiciones_de_workflows_md(self):
        self.assertEqual([len(design_docs.workflow_transitions(letter)) for letter in "ABCLO"], [9, 6, 14, 4, 2])
        assess = next(t for t in design_docs.workflow_transitions("B") if t["name"] == "assess")
        self.assertEqual((assess["label"], assess["from"], assess["to"]), ("Evaluar", "profiled", "assessed"))
        self.assertEqual(assess["roles"], ["STAKEHOLDER_MANAGER", "SOCIAL_MANAGER"])
        cancel = {t["name"]: t["roles"] for t in design_docs.workflow_transitions("C")}
        self.assertEqual(cancel["cancel_postponed"], cancel["cancel_planned"])
        self.assertEqual(len(cancel["cancel_postponed"]), 4)


class BrokenDocsTests(unittest.TestCase):
    """El lector falla, no devuelve de menos, cuando una tabla de un documento cambia de forma."""

    def setUp(self):
        self.tmp = tempfile.mkdtemp()
        self.addCleanup(shutil.rmtree, self.tmp)
        self.original = design_docs.DOCS
        self.addCleanup(setattr, design_docs, "DOCS", self.original)
        for name in os.listdir(self.original):
            if name.endswith(".md"):
                shutil.copy(os.path.join(self.original, name), self.tmp)
        design_docs.DOCS = self.tmp

    def edit(self, name, change):
        path = os.path.join(self.tmp, name)
        with open(path, encoding="utf-8") as f:
            lines = f.read().split("\n")
        change(lines)
        with open(path, "w", encoding="utf-8") as f:
            f.write("\n".join(lines))

    def index(self, lines, prefix, after=0):
        return next(i for i, line in enumerate(lines) if i >= after and line.startswith(prefix))

    def test_las_tablas_rotas_hacen_fallar_al_lector(self):
        design_docs.cards()  # la copia sin tocar se lee

        def rename_header(lines):
            lines[self.index(lines, "| Campo |")] = "| Campos |"

        def blank_inside(lines):
            start = self.index(lines, "| Campo |", self.index(lines, "### `engagement`"))
            lines.insert(start + 4, "")

        def drop_separator(lines):
            del lines[self.index(lines, "| Campo |") + 1]

        for name, change in (("renamed header", rename_header), ("blank line", blank_inside),
                             ("dropped separator", drop_separator)):
            with self.subTest(name):
                self.edit("database-model.md", change)
                with self.assertRaises(ValueError):
                    design_docs.cards()
                shutil.copy(os.path.join(self.original, "database-model.md"), self.tmp)

        def short_row(lines):
            i = self.index(lines, "| Objeto | PAD |")
            lines[i + 2] = lines[i + 2].rsplit("|", 2)[0] + "|"

        self.edit("security.md", short_row)
        with self.assertRaises(ValueError):
            design_docs.matrix()

        def bad_state(lines):
            i = self.index(lines, "| WF | Objeto | Inicial |")
            lines[i + 2] = lines[i + 2].replace("`draft`", "draft")  # el estado inicial pierde sus comillas

        self.edit("domain-model.md", bad_state)
        with self.assertRaises(ValueError):
            design_docs.workflow_states()

    def test_las_tablas_de_transiciones_rotas_hacen_fallar_al_lector(self):
        design_docs.workflow_transitions("A")  # la copia sin tocar se lee

        def rename_header(lines):
            lines[self.index(lines, "| Transición | De | A | Roles |", self.index(lines, "## WF-A ·"))] = "| Transición | De |"

        def drop_row_cell(lines):
            i = self.index(lines, "| `approve` (Aprobar) |")
            lines[i] = lines[i].replace("| `PROJECT_ADMIN` |", "|", 1)

        def empty_roles(lines):
            i = self.index(lines, "| `close` (Cerrar) |", self.index(lines, "## WF-A ·"))
            lines[i] = lines[i].replace("`PROJECT_ADMIN`", "Los administradores", 1)

        def bad_name(lines):
            i = self.index(lines, "| `close` (Cerrar) |", self.index(lines, "## WF-A ·"))
            lines[i] = lines[i].replace("`close` (Cerrar)", "close (Cerrar)", 1)

        def rename_section(lines):
            lines[self.index(lines, "## WF-A ·")] = "## WF-A - Proyecto"

        for name, change in (("renamed header", rename_header), ("short row", drop_row_cell), ("no roles", empty_roles),
                             ("bad name", bad_name), ("renamed section", rename_section)):
            with self.subTest(name):
                self.edit("workflows.md", change)
                with self.assertRaises(ValueError):
                    design_docs.workflow_transitions("A")
                shutil.copy(os.path.join(self.original, "workflows.md"), self.tmp)

    def test_una_letra_de_permiso_desconocida_falla(self):
        with self.assertRaises(ValueError):
            design_docs._grants("RX")


if __name__ == "__main__":
    unittest.main()
