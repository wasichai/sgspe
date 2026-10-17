"""Pruebas del conjunto ficticio de model/demo/ (épica #102): valida contra model.json y tiene el volumen de DOC-01.

Correr: cd model && python3 -m unittest -v test_demo_data
"""
import json
import os
import unittest

import demo_check
import load_demo

HERE = os.path.dirname(os.path.abspath(__file__))


class DemoDataTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.dataset = load_demo.read_dataset(os.path.join(HERE, "demo"))
        with open(os.path.join(HERE, "model.json"), encoding="utf-8") as f:
            cls.model = json.load(f)

    def records(self, obj):
        return [r for c in self.dataset.collections if c["object"] == obj for r in c["records"]]

    def test_valida_contra_el_modelo(self):
        self.assertEqual(demo_check.validate(self.dataset, self.model), [])

    def test_quince_localidades_cuatro_en_el_aids(self):
        self.assertEqual(len({r["key"] for r in self.records("locality")}), 15)
        aids = [r for r in self.records("area_of_influence_member") if r["attributes"]["area_of_influence"] == "@aids"]
        self.assertEqual(sum(1 for r in aids if "locality" in r["attributes"]), 4)

    def test_aiis_con_once_localidades_y_el_distrito(self):
        aiis = [r for r in self.records("area_of_influence_member") if r["attributes"]["area_of_influence"] == "@aiis"]
        self.assertEqual(sum(1 for r in aiis if "locality" in r["attributes"]), 11)
        self.assertTrue(any(r["attributes"].get("administrative_unit") == "@au-distrito" for r in aiis))

    def test_las_dos_areas_se_aprueban(self):
        approved = {r["key"] for r in self.records("area_of_influence")
                    if any(c["command"] == "approve_area" for c in r.get("commands", []))}
        self.assertEqual(approved, {"aids", "aiis"})

    PALLCA = {"loc-santarosa", "loc-esperanza", "loc-cruzpata", "loc-antaccasa"}

    @staticmethod
    def inside(point, polygon):
        """Punto dentro de un polígono simple, en km (cruce de rayos)."""
        x, y = point
        hit = False
        for (x1, y1), (x2, y2) in zip(polygon, polygon[1:] + polygon[:1]):
            if (y1 > y) != (y2 > y) and x < (x2 - x1) * (y - y1) / (y2 - y1) + x1:
                hit = not hit
        return hit

    def test_cada_localidad_cae_en_su_microcuenca(self):
        basins = {r["key"]: r["geometries"]["boundary"]["polygon"] for r in self.records("environmental_feature")}
        for r in self.records("locality"):
            basin = "mc-pallca" if r["key"] in self.PALLCA else "mc-ichu"
            self.assertTrue(self.inside(r["geometries"]["location"]["point"], basins[basin]), r["key"])

    def test_una_asignacion_por_usuario(self):
        users = {u["key"] for u in self.dataset.users}
        assigned = {r["attributes"]["member_user"] for r in self.records("project_assignment")}
        self.assertEqual(len(users), 6)
        self.assertEqual(assigned, users)

    def test_un_perfil_por_localidad_del_estudio(self):
        profiles = self.records("locality_profile")
        self.assertEqual({r["attributes"]["locality"] for r in profiles}, {"@" + r["key"] for r in self.records("locality")})
        self.assertTrue(all(r["attributes"]["study"] == "@study" for r in profiles))

    def test_tablas_del_capitulo_5_con_volumen(self):
        self.assertGreaterEqual(len(self.records("school")), 20)
        self.assertGreaterEqual(len(self.records("festivity")), 15)
        self.assertGreaterEqual(len(self.records("road_access")), 8)
        local = [r for r in self.records("organization") if "locality" in r["attributes"]]
        self.assertGreaterEqual(len(local), 50)

    def test_todo_perfil_lleva_cultivos(self):
        for r in self.records("locality_profile"):
            self.assertTrue(r["attributes"].get("crops"), r["key"])

    def test_programas_y_conflictos(self):
        self.assertEqual(len(self.records("state_program")), 4)
        conflicts = self.records("local_conflict")
        self.assertEqual(sorted(r["attributes"]["conflict_type"] for r in conflicts), ["AGUA", "LIMITES", "SEPARACION"])

    def test_cada_localidad_tiene_una_via(self):
        self.assertEqual({r["attributes"]["locality"] for r in self.records("road_access")},
                         {"@" + r["key"] for r in self.records("locality")})

    # ---- 04-grupos.json: grupos de interés, percepciones y temas clave ------------------------------------------

    GROUPS = {
        "AIDS": {"ESTRATEGICO": 12, "POLITICO": 4, "PARA MONITOREO": 5, "VULNERABLE": 1},
        "AIIS": {"ESTRATEGICO": 21, "POLITICO": 14, "PARA MONITOREO": 14, "VULNERABLE": 7},
        "REGIONAL": {"ESTRATEGICO": 1, "POLITICO": 4, "PARA MONITOREO": 2},
    }

    @staticmethod
    def assessments(record):
        return [c["attributes"] for c in record.get("commands", []) if c["command"] == "assessment"]

    def test_grupos_por_ambito_y_por_grupo_de_influencia(self):
        stakeholders = self.records("stakeholder")
        by = {}
        for r in stakeholders:
            key = (r["attributes"]["stakeholder_scope"], r["_expect"]["influence_group"])
            by[key] = by.get(key, 0) + 1
        self.assertEqual(by[("AIDS", "ESTRATEGICO")], 12)
        self.assertEqual(by[("AIIS", "ESTRATEGICO")], 21)
        self.assertEqual(by[("REGIONAL", "ESTRATEGICO")], 1)
        self.assertEqual(len(stakeholders), 85)
        expected = {(scope, group): n for scope, groups in self.GROUPS.items() for group, n in groups.items()}
        self.assertEqual(by, expected)

    def test_seguimiento_con_cambios_de_postura(self):
        changed = 0
        for r in self.records("stakeholder"):
            stances = [c["attributes"].get("stance") for c in r.get("commands", []) if c["command"] == "assessment"]
            changed += any(a != b for a, b in zip(stances, stances[1:]))
        self.assertGreaterEqual(changed, 8)

    def test_cada_persona_con_cargo_vigente(self):
        holders = {r["attributes"]["person"] for r in self.records("position") if r["attributes"]["position_status"] == "VIGENTE"}
        people = {r["attributes"]["person"] for r in self.records("stakeholder")}
        self.assertEqual(people - holders, set())

    def test_un_solo_cargo_vigente_por_persona(self):
        vigentes = [r["attributes"]["person"] for r in self.records("position") if r["attributes"]["position_status"] == "VIGENTE"]
        self.assertEqual(len(vigentes), len(set(vigentes)))

    def test_personas_con_al_menos_un_tercio_de_mujeres(self):
        people = self.records("person")
        women = sum(1 for r in people if r["attributes"]["sex"] == "FEMENINO")
        self.assertGreaterEqual(len(people), 85)
        self.assertGreaterEqual(women / len(people), 0.30)

    def test_linea_de_base_en_el_trabajo_de_campo(self):
        statuses = {}
        for r in self.records("stakeholder"):
            first = self.assessments(r)[0]
            self.assertTrue("2026-04-14" <= first["assessment_date"] <= "2026-04-29", r["key"])
            status = r["attributes"]["interview_status"]
            statuses[status] = statuses.get(status, 0) + 1
            self.assertEqual(first["basis"], "ENTREVISTA" if status == "ENTREVISTADO" else "INFORMACION SECUNDARIA", r["key"])
            self.assertEqual(r["attributes"]["owner_user"], "relacionista")
            self.assertEqual("locality" in r["attributes"], r["attributes"]["stakeholder_scope"] != "REGIONAL", r["key"])
        self.assertEqual(statuses, {"ENTREVISTADO": 77, "NO UBICADO": 5, "SE NEGO": 3})

    def test_treinta_con_seguimiento_mensual(self):
        months = ["2026-05-20", "2026-06-20", "2026-07-20", "2026-08-20", "2026-09-20"]
        followed = [r for r in self.records("stakeholder") if len(self.assessments(r)) > 1]
        self.assertEqual(len(followed), 30)
        for r in followed:
            self.assertEqual([a["assessment_date"] for a in self.assessments(r)[1:]], months, r["key"])

    def test_mas_oposicion_en_el_aiis_que_en_el_aids(self):
        def share(scope):
            rows = [r for r in self.records("stakeholder") if r["attributes"]["stakeholder_scope"] == scope]
            against = sum(1 for r in rows if self.assessments(r)[0]["stance"] == "EN CONTRA")
            return against / len(rows)
        self.assertGreater(share("AIIS"), share("AIDS"))

    def test_doce_reglas_de_priorizacion_sin_tipo(self):
        rules = self.records("prioritization_rule")
        self.assertEqual(len(rules), 12)
        combos = {(r["attributes"]["influence_group"], r["attributes"]["stance"], r["attributes"]["stakeholder_scope"]) for r in rules}
        self.assertEqual(len(combos), 12)
        self.assertTrue(all("stakeholder_type" not in r["attributes"] for r in rules))

    def test_percepciones_por_tema_con_citas(self):
        perceptions = self.records("perception")
        self.assertEqual(len(perceptions), 24)
        for subject in ("MINERIA", "PROYECTO", "EMPRESA"):
            rows = [r["attributes"] for r in perceptions if r["attributes"]["subject"] == subject]
            self.assertEqual(len(rows), 8, subject)
            self.assertEqual({a["polarity"] for a in rows}, {"POSITIVA", "NEGATIVA"}, subject)
        quoted = [r["attributes"] for r in perceptions if r["attributes"].get("quote")]
        self.assertEqual(len(quoted), 12)
        self.assertTrue(all(a.get("attributed_role") for a in quoted))

    def test_cuatro_temas_clave(self):
        themes = self.records("key_theme")
        self.assertEqual(len(themes), 4)
        for r in themes:
            a = r["attributes"]
            self.assertEqual((a["project"], a["study"]), ("@proj", "@study"))
            self.assertTrue(a["topic"].startswith("@topic-") and a["description"] and a["risks"] and a["opportunities"])
