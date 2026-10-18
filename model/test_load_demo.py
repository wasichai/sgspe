"""Pruebas de load_demo.py: claves, geometrías, usuarios, idempotencia y comandos de la plataforma (épica #102).

Correr: cd model && python3 -m unittest -v test_load_demo
"""
import hashlib
import io
import json
import os
import subprocess
import sys
import tempfile
import threading
import time
import unittest
from unittest import mock
from contextlib import redirect_stderr, redirect_stdout

import demo_check
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
            {"key": "au-departamento", "attributes": {"ubigeo_code": "090000", "name": "HUANCAVELICA", "level": "DEPARTAMENTO"}},
            {"key": "au-provincia", "attributes": {"ubigeo_code": "090400", "name": "CASTROVIRREYNA", "level": "PROVINCIA",
                                                   "parent_unit": "@au-departamento"}},
            {"key": "au-distrito", "attributes": {"ubigeo_code": "090410", "name": "SAN JUAN", "level": "DISTRITO",
                                                  "parent_unit": "@au-provincia"}}]},
        {"object": "project", "match": ["code"], "records": [
            {"key": "proj", "attributes": {"code": "DEMO-T1", "name": "Proyecto Wayra Ejemplo"},
             "commands": [{"command": "close_period", "indicator": "KPI-STK-05", "optional": True,
                           "period_start_date": "2026-04-01", "period_end_date": "2026-09-30"}]}]},
        {"object": "locality", "match": ["name"], "records": [
            {"key": "loc-a", "attributes": {"name": "Caserío Huaylla Alto", "locality_type": "CASERIO",
                                            "administrative_unit": "@au-distrito"},
             "geometries": {"location": {"point": [1.0, 0.0]}}}]},
        {"object": "organization", "match": ["name"], "records": [
            {"key": "org-a", "attributes": {"name": "Asociación Ejemplo de Regantes"}}]},
        {"object": "stakeholder", "match": ["project", "organization"], "as": "social", "records": [
            {"key": "stk-a", "attributes": {"project": "@proj", "organization": "@org-a", "owner_user": "social"},
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

    def run_loader(self, workers=1):
        client = Client(self.core.base_url)
        client.login("admin@wasichai.local", "admin")
        return load_demo.Loader(client, load_demo.read_dataset(self.dir), users_password="clave-demo-123",
                                out=io.StringIO(), workers=workers).run()

    def test_resuelve_referencias_y_expande_el_usuario(self):
        self.run_loader()
        locality = self.core.records["locality"][0]
        district = next(r for r in self.core.records["administrative_unit"] if r["attributes"]["level"] == "DISTRITO")
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

    def test_un_campo_polygon_recibe_un_polygon(self):
        # locality.boundary es POLYGON en model.json: core rechaza un MultiPolygon ("must be a Polygon")
        self.assertEqual(load_demo.geometry_types("locality")["boundary"], "POLYGON")
        square = load_demo.geometry({"square": [0, 0, 0.5]}, ANCHOR, "POLYGON")
        self.assertEqual(square["type"], "Polygon")
        self.assertEqual(square["coordinates"][0][0], square["coordinates"][0][-1])

    def test_recargar_no_duplica(self):
        first = self.run_loader()
        second = self.run_loader()
        self.assertGreater(first.created, 0)
        self.assertEqual(second.created, 0)
        self.assertEqual(len(self.core.records["locality"]), 1)
        self.assertEqual(len(self.core.assessments), 1)

    def test_con_varios_hilos_carga_lo_mismo_y_no_duplica(self):
        first = self.run_loader(workers=4)
        second = self.run_loader(workers=4)
        self.assertGreater(first.created, 0)
        self.assertEqual(second.created, 0)
        self.assertEqual(len(self.core.records["locality"]), 1)
        self.assertEqual(len(self.core.assessments), 1)

    def many_organizations(self, then=None):
        """BASE con cuatro organizaciones independientes (un lote de cuatro) y, si `then`, un registro que nombra a la
        primera (el lote siguiente)."""
        data = json.loads(json.dumps(BASE))
        orgs = data["collections"][3]["records"]
        orgs += [{"key": f"org-{n}", "attributes": {"name": f"Organización {n}"}} for n in ("b", "c", "d")]
        if then:
            orgs.append({"key": "org-e", "attributes": {"name": "Organización e", "affiliated_to": "@org-a"}})
        write(self.dir, "00-base.json", data)

    def test_un_lote_de_varios_registros_pasa_por_el_pool(self):
        self.many_organizations(then=True)
        threads = []
        original = load_demo.Loader.load_record

        def spy(loader, client, collection, record):
            if collection["object"] == "organization":
                threads.append((record["key"], threading.current_thread().name))
            return original(loader, client, collection, record)

        with mock.patch.object(load_demo.Loader, "load_record", spy):
            counts = self.run_loader(workers=4)
        pooled = {key for key, name in threads if name.startswith("ThreadPoolExecutor")}
        self.assertEqual(pooled, {"org-a", "org-b", "org-c", "org-d"})
        self.assertEqual(sorted(r["attributes"]["name"] for r in self.core.records["organization"]),
                         ["Asociación Ejemplo de Regantes", "Organización b", "Organización c", "Organización d",
                          "Organización e"])
        serial_core = FakeCore(workflow_objects={"stakeholder": "identified"})
        serial_core.transition_targets["profile"] = "profiled"
        self.addCleanup(serial_core.stop)
        serial_core.add_record("indicator", {"code": "KPI-STK-05"})
        serial_core.refuse_command("/measurements", 422)
        client = Client(serial_core.base_url)
        client.login("admin@wasichai.local", "admin")
        serial = load_demo.Loader(client, load_demo.read_dataset(self.dir), users_password="clave-demo-123",
                                  out=io.StringIO(), workers=1).run()
        self.assertEqual((counts.created, counts.skipped, counts.commands, counts.warnings),
                         (serial.created, serial.skipped, serial.commands, serial.warnings))

    def test_el_error_de_un_hilo_nombra_su_clave_y_corta_la_carga(self):
        self.many_organizations(then=True)
        self.core.fail_on_value = ("organization", "name", "Organización b")
        with self.assertRaisesRegex(load_demo.LoadError, r"^org-b: "):
            self.run_loader(workers=4)
        names = {r["attributes"]["name"] for r in self.core.records.get("organization", [])}
        self.assertNotIn("Organización e", names)  # el lote siguiente no corre
        self.assertNotIn("stakeholder", self.core.records)  # ni la colección siguiente

    def test_un_lote_se_corta_antes_de_una_referencia_a_su_propia_coleccion(self):
        records = [
            {"key": "a", "attributes": {}},
            {"key": "b", "attributes": {"x": "@otro"}},
            {"key": "c", "attributes": {"parent": "@a"}},
            {"key": "d", "attributes": {}, "children": [{"key": "d1", "attributes": {}}]},
            {"key": "e", "attributes": {"y": "@d1"}},
        ]
        batches = load_demo.batches(records)
        self.assertEqual([[r["key"] for r in b] for b in batches], [["a", "b"], ["c", "d"], ["e"]])

    def test_un_estado_distinto_al_esperado_falla_con_su_clave(self):
        self.run_loader()
        self.core.records["stakeholder"][0]["state"] = "identified"
        with self.assertRaisesRegex(load_demo.LoadError, "stk-a: state identified, expected profiled"):
            self.run_loader()

    def test_reutiliza_el_ubigeo_ya_cargado(self):
        # las tres unidades como las guarda load_ubigeo.py: departamento code[:2] + "0000" y provincia code[:4] + "00"
        department = self.core.add_record("administrative_unit",
                                          {"ubigeo_code": "090000", "name": "HUANCAVELICA", "level": "DEPARTAMENTO"})
        province = self.core.add_record("administrative_unit", {"ubigeo_code": "090400", "name": "CASTROVIRREYNA",
                                                                "level": "PROVINCIA", "parent_unit": department["id"]})
        self.core.add_record("administrative_unit", {"ubigeo_code": "090410", "name": "SAN JUAN", "level": "DISTRITO",
                                                     "parent_unit": province["id"]})
        self.run_loader()
        units = self.core.records["administrative_unit"]
        self.assertEqual(sorted(u["attributes"]["ubigeo_code"] for u in units), ["090000", "090400", "090410"])
        self.assertFalse([r for r in self.core.requests
                          if r["method"] == "POST" and r["path"] == "/api/objects/administrative_unit/records"])

    def test_load_demo_se_importa_en_un_interprete_limpio(self):
        # sin ciclo con demo_check: una herramienta que importe load_demo primero no se rompe
        done = subprocess.run([sys.executable, "-c", "import load_demo"], cwd=os.path.dirname(os.path.abspath(__file__)),
                              capture_output=True, text=True)
        self.assertEqual(done.returncode, 0, done.stderr)

    def test_al_recargar_avisa_si_faltan_hijos_o_vinculos(self):
        data = json.loads(json.dumps(BASE))
        data["collections"].insert(3, {"object": "state_program", "match": ["name"], "records": [
            {"key": "prog", "attributes": {"name": "Programa"}}]})
        data["collections"][2]["records"][0]["children"] = [
            {"object": "engagement_participant", "parent": "locality", "attributes": {"attended": True}},
            {"object": "engagement_participant", "parent": "locality", "attributes": {"attended": False}}]
        data["collections"].insert(4, {"object": "locality", "match": ["name"], "records": [
            {"key": "loc-b", "attributes": {"name": "Caserío Ejemplo Bajo", "locality_type": "CASERIO"},
             "links": {"state_program_locality": ["@prog"]}}]})
        write(self.dir, "00-base.json", data)
        self.run_loader()
        out = io.StringIO()
        client = Client(self.core.base_url)
        client.login("admin@wasichai.local", "admin")
        clean = load_demo.Loader(client, load_demo.read_dataset(self.dir), users_password="clave-demo-123", out=out).run()
        self.assertEqual(clean.warnings, 1, out.getvalue())  # solo el cierre opcional rechazado: nada falta
        self.core.records["engagement_participant"].pop()
        self.core.links.clear()
        out = io.StringIO()
        broken = load_demo.Loader(client, load_demo.read_dataset(self.dir), users_password="clave-demo-123", out=out).run()
        self.assertEqual(broken.warnings, 3, out.getvalue())
        self.assertIn("warning: loc-a has 1 engagement_participant, declares 2", out.getvalue())
        self.assertIn("warning: loc-b has 0 state_program_locality, declares 1", out.getvalue())
        self.assertEqual(broken.created, 0)  # avisa, no repara

    def test_el_primer_error_cancela_lo_que_falta_del_lote(self):
        self.many_organizations()
        self.core.fail_on_value = ("organization", "name", "Asociación Ejemplo de Regantes")
        started = []
        original = load_demo.Loader.load_record

        def spy(loader, client, collection, record):
            if collection["object"] == "organization":
                started.append(record["key"])
                if record["key"] != "org-a":
                    time.sleep(0.5)  # los dos hilos quedan ocupados mientras org-a falla
            return original(loader, client, collection, record)

        with mock.patch.object(load_demo.Loader, "load_record", spy):
            with self.assertRaisesRegex(load_demo.LoadError, r"^org-a: "):
                self.run_loader(workers=2)
        self.assertNotIn("org-d", started)

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

    def test_la_clave_de_un_hijo_se_recupera_al_recargar(self):
        # una recarga salta los hijos de un padre que ya existe, pero su clave tiene que seguir resolviéndose
        data = json.loads(json.dumps(BASE))
        data["collections"][2]["records"][0]["children"] = [
            {"key": "kid", "object": "engagement_participant", "parent": "locality",
             "attributes": {"attended": True, "name": "Participante uno"}},
            {"key": "kid2", "object": "engagement_participant", "parent": "locality",
             "attributes": {"attended": True, "name": "Participante dos"}}]
        data["collections"][3]["records"][0]["attributes"]["note"] = "@kid2"
        write(self.dir, "00-base.json", data)
        self.run_loader()
        second = self.run_loader()
        self.assertEqual(second.created, 0)
        kid2 = next(r for r in self.core.records["engagement_participant"] if r["attributes"]["name"] == "Participante dos")
        self.assertEqual(self.core.records["organization"][0]["attributes"]["note"], kid2["id"])

    def keyed_children(self, *children):
        data = json.loads(json.dumps(BASE))
        data["collections"][2]["records"][0]["children"] = [
            {"key": k, "object": "engagement_participant", "parent": "locality", "attributes": a} for k, a in children]
        write(self.dir, "00-base.json", data)

    def test_al_recargar_los_hijos_de_un_padre_se_listan_una_vez(self):
        self.keyed_children(("kid", {"attended": True, "name": "Uno"}), ("kid2", {"attended": True, "name": "Dos"}))
        self.run_loader()
        before = len(self.core.requests)
        self.run_loader()
        lists = [r for r in self.core.requests[before:]
                 if r["method"] == "GET" and "/engagement_participant/records" in r["path"]]
        self.assertEqual(len(lists), 1, lists)  # una página basta: dos hijos, una sola lista

    def test_un_hijo_con_clave_sin_texto_para_buscarlo_es_error_del_conjunto(self):
        self.keyed_children(("kid", {"attended": True}))
        with self.assertRaisesRegex(load_demo.DatasetError, "kid has no name or text attribute"):
            load_demo.read_dataset(self.dir)

    def test_dos_hijos_con_clave_y_el_mismo_texto_son_error_del_conjunto(self):
        self.keyed_children(("kid", {"name": "Uno"}), ("kid2", {"name": "Uno"}))
        with self.assertRaisesRegex(load_demo.DatasetError, "kid2 matches the same text as kid"):
            load_demo.read_dataset(self.dir)

    def test_un_hijo_que_coincide_con_dos_al_recargar_es_error_del_conjunto(self):
        self.keyed_children(("kid", {"attended": True, "name": "Uno"}))
        self.run_loader()
        locality = self.core.records["locality"][0]["id"]
        self.core.add_record("engagement_participant", {"name": "Uno", "locality": locality})
        with self.assertRaisesRegex(load_demo.DatasetError, "kid matches 2 records"):
            self.run_loader()

    def assessment_keys(self):
        return [r["headers"].get("Idempotency-Key") for r in self.core.requests if r["path"].endswith("/assessments")]

    def test_la_clave_de_idempotencia_sale_del_contenido_del_comando(self):
        self.run_loader()
        command = BASE["collections"][4]["records"][0]["commands"][0]
        digest = hashlib.sha256(json.dumps(command, sort_keys=True, ensure_ascii=False).encode("utf-8")).hexdigest()[:12]
        self.assertEqual(self.assessment_keys(), [f"demo-stk-a-{digest}"])

    def test_un_comando_insertado_antes_no_cambia_la_clave_de_otro(self):
        self.run_loader()
        first = self.assessment_keys()[0]
        data = json.loads(json.dumps(BASE))
        commands = data["collections"][4]["records"][0]["commands"]
        commands.insert(0, {"command": "assessment", "attributes": {"assessment_date": "2026-04-01", "basis": "ENTREVISTA"}})
        write(self.dir, "00-base.json", data)
        self.core.requests.clear()
        self.run_loader()
        keys = self.assessment_keys()
        self.assertEqual(len(keys), 1)  # la del 15 de abril ya está: solo se manda la nueva
        self.assertNotEqual(keys[0], first)

    def test_dos_comandos_iguales_no_comparten_la_clave(self):
        loader = load_demo.Loader.__new__(load_demo.Loader)
        loader.command_count, loader.lock = {}, threading.Lock()
        command = {"command": "draft"}
        self.assertNotEqual(loader.idempotency_key("del", command), loader.idempotency_key("del", command))

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

    def test_una_clave_repetida_en_una_reapertura_es_error_de_archivo(self):
        write(self.dir, "01-cierre.json", {"collections": [
            {"object": "project", "reopen": True, "records": [{"key": "proj", "commands": []},
                                                               {"key": "proj", "commands": []}]}]})
        with self.assertRaisesRegex(load_demo.DatasetError, "proj repeated in the same collection"):
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
            {"key": "kid", "object": "engagement_participant", "parent": "locality", "attributes": {"attended": True, "name": "Uno"}}]
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


class ValidateTests(unittest.TestCase):
    def setUp(self):
        with open(os.path.join(os.path.dirname(__file__), "model.json"), encoding="utf-8") as f:
            self.model = json.load(f)

    def dataset(self, records, obj="stakeholder", extra=None):
        data = json.loads(json.dumps(BASE))
        data["collections"].append({"object": obj, "match": ["project"], "records": records})
        for collection in extra or []:
            data["collections"].append(collection)
        d = tempfile.mkdtemp()
        write(d, "00-base.json", data)
        return load_demo.read_dataset(d)

    def errors(self, records, obj="stakeholder", extra=None):
        return demo_check.validate(self.dataset(records, obj, extra), self.model)

    def assessed(self, attributes, expect=None, project=None):
        """Un grupo con una evaluación; `project` son atributos extra del proyecto (umbrales)."""
        data = json.loads(json.dumps(BASE))
        data["collections"][1]["records"][0]["attributes"].update(project or {})
        record = {"key": "stk-b", "attributes": {"project": "@proj", "person": "@per-a", "owner_user": "social"},
                  "commands": [{"command": "assessment", "attributes": attributes}]}
        if expect:
            record["_expect"] = {"influence_group": expect}
        data["collections"].append({"object": "person", "match": ["given_names"], "records": [
            {"key": "per-a", "attributes": {"given_names": "Persona Ficticia"}}]})
        data["collections"].append({"object": "stakeholder", "match": ["project", "person"], "records": [record]})
        return demo_check.validate(load_demo.read_dataset_from(data), self.model)

    def test_un_ubigeo_tiene_seis_digitos(self):
        # como el CODE de load_ubigeo.py: con otro formato, el match no reusa la unidad ya cargada y la duplica
        errors = self.errors([{"key": "x", "attributes": {"ubigeo_code": "0904", "name": "X", "level": "PROVINCIA"}}],
                             obj="administrative_unit")
        self.assertTrue(any("x" in e and "ubigeo_code '0904' must have 6 digits" in e for e in errors), errors)

    def test_rechaza_un_campo_derivado(self):
        errors = self.errors([{"key": "x", "attributes": {"project": "@proj", "influence_group": "ESTRATEGICO"}}])
        self.assertTrue(any("derived" in e and "influence_group" in e for e in errors))

    def test_rechaza_una_opcion_que_no_existe(self):
        errors = self.errors([{"key": "x", "attributes": {"name": "L", "locality_type": "ALDEA"}}], obj="locality")
        self.assertTrue(any("locality_type" in e for e in errors))

    def test_rechaza_un_objeto_solo_por_api(self):
        self.assertTrue(any("apiOnly" in e for e in self.errors([{"key": "x", "attributes": {}}], obj="risk_assessment")))

    def test_rechaza_un_correo_real(self):
        errors = self.errors([{"key": "x", "attributes": {"project": "@proj", "owner_user_email": "a@gmail.com"}}])
        self.assertTrue(any("@sgspe.test" in e for e in errors))

    def test_rechaza_el_correo_de_un_usuario(self):
        data = json.loads(json.dumps(BASE))
        data["users"][0]["email"] = "real@gmail.com"
        errors = demo_check.validate(load_demo.read_dataset_from(data), self.model)
        self.assertTrue(any("@sgspe.test" in e and "social" in e for e in errors))

    def test_el_grupo_esperado_sale_de_los_indicadores(self):
        high = {k: 4 for k in demo_check.INDICATORS}
        self.assertEqual(demo_check.influence_group(high), "ESTRATEGICO")
        low_interest = dict(high, affect_environment=1, affect_livelihood=1, affect_property_status=1,
                            opportunity_work=2, opportunity_development=2, opportunity_benefits=2)
        self.assertEqual(demo_check.influence_group(low_interest), "POLITICO")

    def test_el_redondeo_es_half_up_en_cada_nivel(self):
        # afectación (3+4+4)/3 = 3.67; oportunidad (2+3)/2 = 2.50; interés (3.67+2.50)/2 = 3.085 -> 3.09 (HALF_UP;
        # HALF_EVEN daría 3.08). poder: solo un indicador, 5
        ind = {"affect_environment": 3, "affect_livelihood": 4, "affect_property_status": 4,
               "opportunity_work": 2, "opportunity_development": 3, "power_legal_action": 5}
        self.assertEqual(demo_check.influence_group(ind, 3.09, 5), "ESTRATEGICO")
        self.assertEqual(demo_check.influence_group(ind, 3.10, 5), "POLITICO")

    def test_sin_indicadores_de_un_lado_no_hay_grupo(self):
        self.assertIsNone(demo_check.influence_group({"affect_environment": 3}))

    def test_el_conjunto_base_es_valido(self):
        self.assertEqual(demo_check.validate(load_demo.read_dataset_from(BASE), self.model), [])

    def test_rechaza_un_objeto_que_no_existe(self):
        self.assertTrue(any("objeto" in e or "object" in e for e in self.errors([{"key": "x", "attributes": {}}],
                                                                                   obj="no_existe")))

    def test_rechaza_un_atributo_que_no_es_del_objeto(self):
        errors = self.errors([{"key": "x", "attributes": {"project": "@proj", "inventado": 1}}])
        self.assertTrue(any("inventado" in e for e in errors))

    def test_rechaza_un_obligatorio_ausente(self):
        errors = self.errors([{"key": "x", "attributes": {"name": "L"}}], obj="locality")
        self.assertTrue(any("locality_type" in e and "required" in e for e in errors))

    def test_el_padre_de_un_hijo_no_es_obligatorio(self):
        engagement = {"key": "eng", "attributes": {"project": "@proj"},
                      "children": [{"object": "engagement_participant", "parent": "engagement",
                                    "attributes": {"represented_people_count": 1, "attended": True}}]}
        errors = self.errors([engagement], obj="engagement")
        self.assertFalse(any("engagement_participant" in e and "required" in e for e in errors), errors)

    def test_rechaza_un_vinculo_que_no_es_muchos_a_muchos(self):
        errors = self.errors([{"key": "x", "attributes": {"project": "@proj"}, "links": {"stakeholder_project": ["@proj"]}}])
        self.assertTrue(any("stakeholder_project" in e and "MANY_TO_MANY" in e for e in errors))

    def test_acepta_un_vinculo_muchos_a_muchos_del_objeto(self):
        records = [{"key": "con-1", "attributes": {"code": "C-1"}}]
        project = {"key": "p2", "attributes": {"code": "P2", "name": "Otro"}, "links": {"project_concession": ["@con-1"]}}
        data = json.loads(json.dumps(BASE))
        data["collections"].append({"object": "mining_concession", "match": ["code"], "records": records})
        data["collections"].append({"object": "project", "match": ["code"], "records": [project]})
        self.assertEqual(demo_check.validate(load_demo.read_dataset_from(data), self.model), [])

    def test_rechaza_un_comando_sobre_otro_objeto(self):
        errors = self.errors([{"key": "x", "attributes": {"project": "@proj"},
                               "commands": [{"command": "approve_area"}]}])
        self.assertTrue(any("approve_area" in e for e in errors))

    def test_rechaza_una_referencia_a_otro_tipo_de_objeto(self):
        errors = self.errors([{"key": "x", "attributes": {"project": "@au-distrito"}}])
        self.assertTrue(any("project" in e and "administrative_unit" in e for e in errors))

    def test_las_claves_de_una_evaluacion_son_las_permitidas(self):
        errors = self.assessed({"assessment_date": "2026-04-15", "basis": "ENTREVISTA", "interest_index": 3})
        self.assertTrue(any("interest_index" in e for e in errors))

    def test_un_indicador_va_de_1_a_5(self):
        errors = self.assessed({"assessment_date": "2026-04-15", "basis": "ENTREVISTA", "affect_environment": 6})
        self.assertTrue(any("affect_environment" in e for e in errors))
        errors = self.assessed({"assessment_date": "2026-04-15", "basis": "ENTREVISTA", "affect_environment": "3"})
        self.assertTrue(any("affect_environment" in e for e in errors))

    def test_ninguna_evaluacion_es_posterior_al_corte(self):
        errors = self.assessed({"assessment_date": "2026-10-01", "basis": "ENTREVISTA"})
        self.assertTrue(any("2026-09-30" in e for e in errors))

    def test_el_grupo_esperado_coincide_con_la_ultima_evaluacion(self):
        high = {k: 4 for k in demo_check.INDICATORS}
        attributes = {"assessment_date": "2026-04-15", "basis": "ENTREVISTA", **high}
        self.assertEqual(self.assessed(attributes, expect="ESTRATEGICO"), [])
        errors = self.assessed(attributes, expect="VULNERABLE")
        self.assertTrue(any("VULNERABLE" in e and "ESTRATEGICO" in e for e in errors))

    def test_el_grupo_esperado_usa_los_umbrales_del_proyecto(self):
        high = {k: 4 for k in demo_check.INDICATORS}
        attributes = {"assessment_date": "2026-04-15", "basis": "ENTREVISTA", **high}
        self.assertEqual(self.assessed(attributes, expect="VULNERABLE",
                                       project={"interest_threshold": 4.5, "power_threshold": 4.5}), [])


class ValidateCliTests(unittest.TestCase):
    def test_validate_only_sale_con_2_y_lista_todos_los_errores(self):
        data = json.loads(json.dumps(BASE))
        data["collections"].append({"object": "locality", "match": ["name"], "records": [
            {"key": "l1", "attributes": {"name": "A", "locality_type": "ALDEA"}},
            {"key": "l2", "attributes": {"name": "B", "locality_type": "BOSQUE"}}]})
        d = tempfile.mkdtemp()
        write(d, "00-base.json", data)
        out, err = io.StringIO(), io.StringIO()
        with redirect_stdout(out), redirect_stderr(err):
            code = load_demo.main(["--dir", d, "--validate-only"])
        self.assertEqual(code, 2)
        self.assertIn("00-base.json:l1:", err.getvalue())
        self.assertIn("00-base.json:l2:", err.getvalue())

    def test_dry_run_imprime_los_conteos_por_objeto(self):
        d = tempfile.mkdtemp()
        write(d, "00-base.json", BASE)
        out = io.StringIO()
        with redirect_stdout(out):
            code = load_demo.main(["--dir", d, "--dry-run"])
        self.assertEqual(code, 0)
        self.assertIn("locality: 1", out.getvalue())
        self.assertIn("stakeholder: 1", out.getvalue())
