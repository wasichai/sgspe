"""Pruebas de load_demo.py: claves, geometrías, usuarios, idempotencia y comandos de la plataforma (épica #102).

Correr: cd model && python3 -m unittest -v test_load_demo
"""
import io
import json
import os
import tempfile
import unittest
from unittest import mock
from contextlib import redirect_stderr, redirect_stdout

import load_demo
from core_client import Client
from fake_core import FakeCore

ANCHOR = {"lat": -13.2, "lon": -75.65}
BASE = {
    "anchor": ANCHOR,
    "users": [{"key": "social", "email": "gestion.social.demo@sgspe.test", "displayName": "Gestión social (demo)",
               "roles": ["SOCIAL_MANAGER"]}],
    "collections": [
        {"object": "administrative_unit", "match": ["ubigeo_code"], "records": [
            {"key": "au-distrito", "attributes": {"ubigeo_code": "090410", "name": "SAN JUAN", "level": "DISTRITO"}}]},
        {"object": "project", "match": ["code"], "records": [
            {"key": "proj", "attributes": {"code": "DEMO-T1", "name": "Proyecto Wayra Ejemplo"},
             "commands": [{"command": "close_period", "indicator": "KPI-STK-05", "optional": True,
                           "period_start_date": "2026-04-01", "period_end_date": "2026-09-30"}]}]},
        {"object": "locality", "match": ["name"], "records": [
            {"key": "loc-a", "attributes": {"name": "Caserío Huaylla Alto", "locality_type": "CASERIO",
                                            "administrative_unit": "@au-distrito"},
             "geometries": {"location": {"point": [1.0, 0.0]}}}]},
        {"object": "stakeholder", "match": ["project", "organization"], "as": "social", "records": [
            {"key": "stk-a", "attributes": {"project": "@proj", "organization": "@au-distrito", "owner_user": "social"},
             "transitions": ["profile"], "state": "profiled",
             "commands": [{"command": "assessment", "attributes": {"assessment_date": "2026-04-15", "basis": "ENTREVISTA"}}]}]},
    ],
}


def write(folder, name, data):
    with open(os.path.join(folder, name), "w", encoding="utf-8") as f:
        json.dump(data, f, ensure_ascii=False)


class LoaderTests(unittest.TestCase):
    def setUp(self):
        self.core = FakeCore(workflow_objects={"stakeholder": "identified"})
        self.core.transition_targets["profile"] = "profiled"
        self.addCleanup(self.core.stop)
        self.dir = tempfile.mkdtemp()
        write(self.dir, "00-base.json", BASE)
        self.core.add_record("indicator", {"code": "KPI-STK-05"})
        self.core.refuse_command("/measurements", 422)  # STANCE: no se cierra

    def run_loader(self):
        client = Client(self.core.base_url)
        client.login("admin@wasichai.local", "admin")
        return load_demo.Loader(client, load_demo.read_dataset(self.dir), users_password="clave-demo-123",
                                out=io.StringIO()).run()

    def test_resuelve_referencias_y_expande_el_usuario(self):
        self.run_loader()
        locality = self.core.records["locality"][0]
        district = self.core.records["administrative_unit"][0]
        self.assertEqual(locality["attributes"]["administrative_unit"], district["id"])
        stakeholder = self.core.records["stakeholder"][0]["attributes"]
        self.assertEqual(stakeholder["owner_user_email"], "gestion.social.demo@sgspe.test")
        self.assertEqual(stakeholder["owner_user_id"], self.core.users["gestion.social.demo@sgspe.test"]["id"])

    def test_geometria_en_km_desde_el_ancla(self):
        point = load_demo.geometry({"point": [1.0, 0.0]}, ANCHOR)
        self.assertEqual(point["type"], "Point")
        self.assertAlmostEqual(point["coordinates"][1], -13.2, places=6)
        self.assertGreater(point["coordinates"][0], -75.65)  # 1 km al este
        square = load_demo.geometry({"square": [0, 0, 0.5]}, ANCHOR)
        self.assertEqual(square["type"], "MultiPolygon")
        ring = square["coordinates"][0][0]
        self.assertEqual(ring[0], ring[-1])

    def test_recargar_no_duplica(self):
        first = self.run_loader()
        second = self.run_loader()
        self.assertGreater(first.created, 0)
        self.assertEqual(second.created, 0)
        self.assertEqual(len(self.core.records["locality"]), 1)
        self.assertEqual(len(self.core.assessments), 1)

    def test_un_estado_distinto_al_esperado_falla_con_su_clave(self):
        self.run_loader()
        self.core.records["stakeholder"][0]["state"] = "identified"
        with self.assertRaisesRegex(load_demo.LoadError, "stk-a: state identified, expected profiled"):
            self.run_loader()

    def test_reutiliza_el_ubigeo_ya_cargado(self):
        self.core.add_record("administrative_unit", {"ubigeo_code": "090410", "name": "SAN JUAN", "level": "DISTRITO"})
        self.run_loader()
        self.assertEqual(len(self.core.records["administrative_unit"]), 1)

    def test_un_cierre_opcional_rechazado_avisa_y_sigue(self):
        counts = self.run_loader()
        self.assertEqual(self.core.records["locality"][0]["attributes"]["name"], "Caserío Huaylla Alto")
        self.assertEqual(counts.warnings, 1)

    def test_cada_escritura_lleva_motivo(self):
        self.run_loader()
        writes = [r for r in self.core.requests if r["method"] in ("POST", "PUT") and r["path"].startswith("/api/objects")]
        self.assertTrue(writes)
        self.assertTrue(all(r["headers"].get("X-Change-Reason", "").startswith("UTF-8''") for r in writes))

    def test_una_referencia_sin_definir_es_error_de_archivo(self):
        broken = json.loads(json.dumps(BASE))
        broken["collections"][2]["records"][0]["attributes"]["administrative_unit"] = "@no-existe"
        write(self.dir, "00-base.json", broken)
        with self.assertRaisesRegex(load_demo.DatasetError, "@no-existe"):
            load_demo.read_dataset(self.dir)

    def test_geometrias_y_vinculos_van_en_el_alta(self):
        data = json.loads(json.dumps(BASE))
        data["collections"].insert(2, {"object": "state_program", "match": ["name"], "records": [
            {"key": "prog", "attributes": {"name": "Programa"}}]})
        data["collections"][3]["records"][0]["links"] = {"state_program_locality": ["@prog"]}
        write(self.dir, "00-base.json", data)
        self.run_loader()
        locality = self.core.records["locality"][0]
        self.assertEqual(locality["geometries"]["location"]["type"], "Point")
        self.assertEqual(self.core.links[0][2:], ("state_program_locality", self.core.records["state_program"][0]["id"]))

    def test_los_hijos_llevan_el_padre_y_se_saltan_al_recargar(self):
        data = json.loads(json.dumps(BASE))
        data["collections"][2]["records"][0]["children"] = [
            {"object": "engagement_participant", "parent": "locality", "attributes": {"attended": True}}]
        write(self.dir, "00-base.json", data)
        self.run_loader()
        self.run_loader()
        self.assertEqual(len(self.core.records["engagement_participant"]), 1)
        self.assertEqual(self.core.records["engagement_participant"][0]["attributes"]["locality"],
                         self.core.records["locality"][0]["id"])

    def test_la_clave_de_idempotencia_es_estable(self):
        self.run_loader()
        keys = [r["headers"].get("Idempotency-Key") for r in self.core.requests if r["path"].endswith("/assessments")]
        self.assertEqual(keys[0], "demo-stk-a-0")

    def test_el_usuario_existente_se_reutiliza(self):
        self.core.users["gestion.social.demo@sgspe.test"] = {
            "id": "11111111-1111-1111-1111-111111111111", "email": "gestion.social.demo@sgspe.test"}
        self.run_loader()
        self.assertEqual(self.core.records["stakeholder"][0]["attributes"]["owner_user_id"],
                         "11111111-1111-1111-1111-111111111111")

    def test_el_registro_se_escribe_con_el_usuario_de_la_coleccion(self):
        self.run_loader()
        post = next(r for r in self.core.requests if r["method"] == "POST" and r["path"] == "/api/objects/stakeholder/records")
        self.assertEqual(post["auth"], "Bearer t-gestion.social.demo@sgspe.test")

    def test_reabrir_una_clave_corre_sus_comandos(self):
        data = json.loads(json.dumps(BASE))
        data["collections"][2]["records"][0]["commands"] = []
        data["collections"].insert(3, {"object": "area_of_influence", "match": ["version_number"], "as": "social", "records": [
            {"key": "aids", "attributes": {"version_number": 1}}]})
        write(self.dir, "00-base.json", data)
        write(self.dir, "01-cierre.json", {"collections": [
            {"object": "area_of_influence", "reopen": True, "as": "social", "records": [
                {"key": "aids", "commands": [{"command": "approve_area"}]}]}]})
        self.run_loader()
        self.assertEqual(self.core.records["area_of_influence"][0]["state"], "approved")
        approve = next(r for r in self.core.requests if r["path"].endswith("/approve"))
        self.assertTrue(approve["headers"]["If-Match"].startswith('"'))
        self.assertEqual(approve["auth"], "Bearer t-gestion.social.demo@sgspe.test")
        count = len(self.core.requests)
        self.run_loader()  # ya aprobada: no vuelve a llamar a approve
        self.assertEqual(len([r for r in self.core.requests[count:] if r["path"].endswith("/approve")]), 0)

    def test_reabrir_una_clave_de_otro_objeto_es_error_de_archivo(self):
        write(self.dir, "01-cierre.json", {"collections": [
            {"object": "project", "reopen": True, "records": [{"key": "loc-a", "commands": []}]}]})
        with self.assertRaisesRegex(load_demo.DatasetError, "loc-a"):
            load_demo.read_dataset(self.dir)

    def test_una_clave_repetida_es_error_de_archivo(self):
        write(self.dir, "01-otra.json", {"collections": [
            {"object": "project", "match": ["code"], "records": [{"key": "proj", "attributes": {"code": "X"}}]}]})
        with self.assertRaisesRegex(load_demo.DatasetError, "proj"):
            load_demo.read_dataset(self.dir)

    def test_las_secciones_se_escriben_una_vez(self):
        deliverable = self.core.add_record("deliverable", {"title": "T1"})
        self.core.add_record("deliverable_section", {"deliverable": deliverable["id"], "section_key": "intro"})
        self.core.add_record("deliverable_section", {"deliverable": deliverable["id"], "section_key": "fin", "content": "ya"})
        data = json.loads(json.dumps(BASE))
        data["collections"].append({"object": "deliverable", "match": ["title"], "records": [
            {"key": "del", "attributes": {"title": "T1"},
             "commands": [{"command": "sections", "sections": {"intro": "Hola [[person:@stk-a]]", "fin": "nuevo"}}]}]})
        write(self.dir, "00-base.json", data)
        self.run_loader()
        sections = {r["attributes"]["section_key"]: r["attributes"] for r in self.core.records["deliverable_section"]}
        self.assertEqual(sections["intro"]["content"], f"Hola [[person:{self.core.records['stakeholder'][0]['id']}]]")
        self.assertEqual(sections["intro"]["content_origin"], "HUMANO")
        self.assertEqual(sections["fin"]["content"], "ya")

    def test_una_coleccion_sin_match_es_error_de_archivo(self):
        data = json.loads(json.dumps(BASE))
        del data["collections"][2]["match"]
        write(self.dir, "00-base.json", data)
        with self.assertRaisesRegex(load_demo.DatasetError, "loc-a collection without match"):
            load_demo.read_dataset(self.dir)

    def test_la_clave_de_un_hijo_se_referencia_y_es_unica(self):
        data = json.loads(json.dumps(BASE))
        data["collections"][2]["records"][0]["children"] = [
            {"key": "kid", "object": "engagement_participant", "parent": "locality", "attributes": {"attended": True}}]
        data["collections"][3]["records"][0]["attributes"]["note"] = "@kid"
        write(self.dir, "00-base.json", data)
        load_demo.read_dataset(self.dir)
        write(self.dir, "01-otra.json", {"collections": [{"object": "x", "match": ["a"], "records": [
            {"key": "kid", "attributes": {"a": 1}}]}]})
        with self.assertRaisesRegex(load_demo.DatasetError, "kid repeated key"):
            load_demo.read_dataset(self.dir)

    def test_una_respuesta_que_no_es_json_es_error_de_core(self):
        response = mock.MagicMock()
        response.__enter__.return_value = response
        response.status, response.read.return_value = 200, b"<html>"
        with mock.patch("urllib.request.urlopen", return_value=response):
            with self.assertRaises(load_demo.CoreError):
                load_demo.ReasonClient("http://x")._call("GET", "/api/x")

    def test_las_secciones_no_reenvian_los_campos_derived(self):
        deliverable = self.core.add_record("deliverable", {"title": "T1"})
        self.core.add_record("deliverable_section", {"deliverable": deliverable["id"], "section_key": "intro",
                                                     "generated_at": "x"})
        data = json.loads(json.dumps(BASE))
        data["collections"].append({"object": "deliverable", "match": ["title"], "records": [
            {"key": "del", "attributes": {"title": "T1"}, "commands": [{"command": "sections", "sections": {"intro": "t"}}]}]})
        write(self.dir, "00-base.json", data)
        with mock.patch.object(load_demo, "derived_fields", lambda name: {"generated_at"} if name == "deliverable_section" else set()):
            self.run_loader()
        put = next(r for r in self.core.requests if r["method"] == "PUT")
        self.assertNotIn("generated_at", put["body"]["attributes"])
        self.assertEqual(put["body"]["attributes"]["content"], "t")

    def test_derived_fields_lee_model_json(self):
        self.assertIn("calculated_area_ha", load_demo.derived_fields("mining_concession"))
        self.assertEqual(load_demo.derived_fields("no_existe"), set())

    def test_el_borrador_solo_se_pide_con_draft(self):
        self.core.add_record("deliverable", {"title": "T1"})
        data = json.loads(json.dumps(BASE))
        data["collections"].append({"object": "deliverable", "match": ["title"], "records": [
            {"key": "del", "attributes": {"title": "T1"}, "commands": [{"command": "draft"}]}]})
        write(self.dir, "00-base.json", data)
        self.run_loader()
        self.assertFalse([r for r in self.core.requests if r["path"].endswith("/drafts")])
        client = Client(self.core.base_url)
        client.login("admin@wasichai.local", "admin")
        loader = lambda: load_demo.Loader(client, load_demo.read_dataset(self.dir), users_password="x", draft=True,
                                          out=io.StringIO()).run()
        loader()
        loader()  # EN COLA: no se repite
        self.assertEqual(len([r for r in self.core.requests if r["path"].endswith("/drafts")]), 1)

    def test_main_valida_y_sale_con_el_codigo_que_toca(self):
        with redirect_stdout(io.StringIO()):
            self.assertEqual(load_demo.main(["--dir", self.dir, "--validate-only"]), 0)
            self.assertEqual(load_demo.main(["--dir", self.dir, "--dry-run"]), 0)
            self.assertEqual(self.core.requests, [])
            self.assertEqual(load_demo.main(["--dir", self.dir, "--core", self.core.base_url,
                                             "--users-password", "clave-demo-123"]), 0)
        write(self.dir, "01-roto.json", {"collections": [{"object": "x", "records": [{"key": "a", "attributes": {"r": "@no"}}]}]})
        with redirect_stderr(io.StringIO()):
            self.assertEqual(load_demo.main(["--dir", self.dir, "--validate-only"]), 2)

    def test_core_que_rechaza_sale_con_1_y_la_clave(self):
        self.core.fail_on_record = "locality"
        err = io.StringIO()
        with redirect_stdout(io.StringIO()), redirect_stderr(err):
            code = load_demo.main(["--dir", self.dir, "--core", self.core.base_url, "--users-password", "clave-demo-123"])
        self.assertEqual(code, 1)
        self.assertIn("loc-a", err.getvalue())
