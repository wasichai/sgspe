"""model.json y model/workflows/ frente a los documentos de diseño (database-model.md §24, «Cómo se prueba», y
workflows.md, «Pruebas del modelo»).

Cada objeto, campo, relación, ENUM, flag, único, índice y acción de las fichas de la fase está en model.json con su tipo,
y nada de una fase posterior; cada workflow tiene los estados de domain-model.md §7.3 y las reglas del motor.

Correr: cd model && python3 -m unittest -v test_model
"""
import json
import os
import unittest

import design_docs

HERE = os.path.dirname(os.path.abspath(__file__))
PHASE = "F2"
# un estado nunca representa un plazo (RN-WFL-001): ninguno de estos nombres puede aparecer
DEADLINE_WORDS = ("overdue", "expired", "due", "late", "vencid")
# workflows.md, «Mecánica de wasichai» 4: nunca en una transición
NEVER_IN_TRANSITIONS = {"SUPER_ADMIN", "ORGANIZATION_ADMIN", "AUDITOR", "ANALYST", "READ_ONLY", "ADMIN"}


def load(name):
    with open(os.path.join(HERE, name), encoding="utf-8") as f:
        return json.load(f)


class ModelAgainstCardsTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.model = load("model.json")
        cls.cards = design_docs.cards()
        cls.objects = {o["name"]: o for o in cls.model["objects"]}

    def test_tiene_exactamente_los_objetos_de_las_fichas_hasta_la_fase(self):
        # las tablas fijas (notification, idempotency_key…) tienen ficha pero no son objetos: no están en la matriz
        catalog = design_docs.matrix()
        wanted = [name for name, card in self.cards.items() if card.phase <= PHASE and name in catalog]
        self.assertEqual([o["name"] for o in self.model["objects"]], wanted)
        self.assertEqual(len(wanted), 48)

    def test_cada_objeto_lleva_etiqueta_plural_descripcion_y_flags_de_su_ficha(self):
        for name, obj in self.objects.items():
            card = self.cards[name]
            self.assertEqual(obj["label"], card.label, name)
            self.assertTrue(obj["pluralLabel"] and obj["pluralLabel"] != obj["label"], name)
            self.assertTrue(obj.get("description"), name)
            for flag in ("appendOnly", "apiOnly", "requiresReason"):
                self.assertEqual(obj.get(flag, False), card.flags[flag], f"{name}.{flag}")

    def test_cada_campo_de_la_fase_con_su_tipo_requerido_unico_derivado_e_indice(self):
        for name, obj in self.objects.items():
            card = self.cards[name]
            wanted = [f for f in card.fields if design_docs.field_phase(card, f) <= PHASE]
            self.assertEqual([f["name"] for f in obj["fields"]], [f["name"] for f in wanted], name)
            for got, doc in zip(obj["fields"], wanted):
                where = f"{name}.{got['name']}"
                self.assertEqual(got["type"], doc["type"], where)
                self.assertEqual(got.get("enum"), doc["enum"], where)
                self.assertEqual(got.get("geometryType"), doc["geometryType"], where)
                self.assertEqual(got.get("required", False), doc["required"], where)
                self.assertEqual(got.get("unique", False), doc["unique"], where)
                self.assertEqual(got.get("derived", False), doc["derived"], where)
                self.assertEqual(got.get("indexed", False), got["name"] in card.indexed, where)
                self.assertEqual(got.get("defaultValue"), doc["default"], where)
                self.assertTrue(got.get("label"), where)
                self.assertNotIn("editable", got, where)

    def test_un_derivado_nunca_es_requerido(self):
        for name, obj in self.objects.items():
            for field in obj["fields"]:
                if field.get("derived"):
                    self.assertFalse(field.get("required", False), f"{name}.{field['name']}")

    def test_toda_geometria_es_4326_2d_y_nunca_requerida_ni_unica(self):
        for name, obj in self.objects.items():
            for field in obj["fields"]:
                if field["type"] == "GEOMETRY":
                    where = f"{name}.{field['name']}"
                    self.assertEqual((field["srid"], field["dimension"]), (4326, 2), where)
                    self.assertFalse(field.get("required") or field.get("unique"), where)

    def test_las_relaciones_de_la_fase_con_su_tipo_campo_destino_y_requerido(self):
        wanted = []
        for name in self.objects:
            card = self.cards[name]
            for rel in card.relationships:
                if not rel["inverse"] and design_docs.relationship_phase(self.cards, card, rel) <= PHASE:
                    wanted.append((rel["name"], rel["type"], name, rel["target"], rel["field"], rel["required"]))
        got = [(r["name"], r.get("type", "MANY_TO_ONE"), r["source"], r["target"], r.get("fieldName"),
                r.get("required", False)) for r in self.model["relationships"]]
        self.assertEqual(got, wanted)

    def test_cada_relacion_lleva_etiqueta_e_inversa_con_el_plural_del_origen(self):
        for rel in self.model["relationships"]:
            self.assertTrue(rel["label"], rel["name"])
            self.assertEqual(rel["inverseLabel"], self.objects[rel["source"]]["pluralLabel"], rel["name"])

    def test_unicos_indices_y_acciones_de_cada_ficha(self):
        for name, obj in self.objects.items():
            card = self.cards[name]
            self.assertEqual(obj.get("uniqueConstraints", []), card.unique_constraints, name)
            self.assertEqual(obj.get("indexes", []), card.indexes, name)
            self.assertEqual([a["name"] for a in obj.get("actions", [])], card.actions, name)
            for field in card.unique:
                self.assertTrue(next(f for f in obj["fields"] if f["name"] == field).get("unique"), f"{name}.{field}")

    def test_los_enum_son_los_catalogos_del_analisis_y_solo_los_usados(self):
        catalogs = design_docs.catalogs()
        used = {f["enum"] for o in self.model["objects"] for f in o["fields"] if f["type"] == "ENUM"}
        self.assertEqual(set(self.model["enums"]), used)
        for key, options in self.model["enums"].items():
            self.assertEqual(options, catalogs[key], key)

    def test_los_filtros_y_ordenes_que_usa_el_portal_existen(self):
        # plan 2.3, «Interfaces»: wasichai rechaza un filtro con un nombre que el objeto no tiene
        fields = {}
        for obj in self.model["objects"]:
            fields.update({f["name"]: True for f in obj["fields"]})
        fields.update({r["fieldName"]: True for r in self.model["relationships"] if r.get("fieldName")})
        portal = ["project", "member_user_id", "active", "role_name", "campaign_status", "area_type", "area_of_influence",
                  "community", "locality", "organization", "person", "stakeholder", "engagement", "field_visit", "study",
                  "month", "exporter_user_id", "from_person", "from_organization", "to_person", "to_organization", "name",
                  "code", "version_number", "scheduled_at", "data_date", "assessment_date", "observed_at",
                  "planned_start_date", "exported_at"]
        self.assertEqual([name for name in portal if name not in fields], [])

    def test_ningun_valor_enum_arrastra_notas_del_analisis(self):
        # Review Focus 5: "(propuesto)", "(propuestos, salvo …)" y las aclaraciones entre paréntesis de §10 no son valores
        import apply
        for key, options in self.model["enums"].items():
            for option in options:
                self.assertNotIn("(", option, key)
                self.assertNotIn("propuest", option.lower(), key)
                self.assertEqual(option, option.upper(), key)
                self.assertTrue(apply.enum_option_valid(option), (key, option))


if __name__ == "__main__":
    unittest.main()
