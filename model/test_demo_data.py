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
