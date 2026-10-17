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
