"""Pruebas de apply.py contra un FakeCore: el modelo vacío de F1, un modelo de prueba, el segundo pase (--workflows) y
el aprovisionamiento de un tenant nuevo en su orden (security.md §4.1 y §14, «Alta de un tenant»).

Correr: cd model && python3 -m unittest -v
"""
import copy
import io
import json
import os
import tempfile
import unittest
from contextlib import redirect_stderr, redirect_stdout

import apply
import apply_roles
from fake_core import FakeCore
from fixture_model import F2_FIXTURE_MODEL, FIXTURE_MODEL

HERE = os.path.dirname(os.path.abspath(__file__))
MODEL_PATH = os.path.join(HERE, "model.json")
ROLES_PATH = os.path.join(HERE, "roles.json")


def load(path):
    with open(path, encoding="utf-8") as f:
        return json.load(f)


def quiet(main, args):
    """Corre main(args) y devuelve (código, salida estándar, salida de errores)."""
    out, err = io.StringIO(), io.StringIO()
    with redirect_stdout(out), redirect_stderr(err):
        code = main(args)
    return code, out.getvalue(), err.getvalue()


def write_model(folder, model):
    path = os.path.join(folder, "model.json")
    with open(path, "w", encoding="utf-8") as f:
        json.dump(model, f, ensure_ascii=False)
    return path


class ModelOfF1Tests(unittest.TestCase):
    def test_f1_has_no_objects_and_is_valid(self):
        model = load(MODEL_PATH)
        self.assertEqual(model, {"enums": {}, "objects": [], "relationships": []})
        self.assertEqual(apply.validate(model), [])


class ValidationTests(unittest.TestCase):
    def mutate(self, fn):
        model = copy.deepcopy(FIXTURE_MODEL)
        fn(model)
        return apply.validate(model)

    def test_the_fixture_is_valid(self):
        self.assertEqual(apply.validate(FIXTURE_MODEL), [])

    def test_objects_must_be_a_list(self):
        self.assertIn("objects must be a list", self.mutate(lambda m: m.update({"objects": {}})))

    def test_derived_must_be_a_boolean(self):
        errors = self.mutate(lambda m: m["objects"][0]["fields"][2].update({"derived": "si"}))
        self.assertTrue(any("zone field area_ha: derived must be true or false" in e for e in errors), errors)

    def test_editable_is_refused(self):
        errors = self.mutate(lambda m: m["objects"][0]["fields"][2].update({"editable": False}))
        self.assertTrue(any("zone field area_ha: editable is not used" in e for e in errors), errors)


class F2ValidationTests(unittest.TestCase):
    def mutate(self, fn):
        model = copy.deepcopy(F2_FIXTURE_MODEL)
        fn(model)
        return apply.validate(model)

    def test_the_f2_fixture_is_valid_with_a_self_reference_a_cycle_and_a_many_to_many(self):
        self.assertEqual(apply.validate(F2_FIXTURE_MODEL), [])

    def test_a_many_to_many_takes_no_field(self):
        errors = self.mutate(lambda m: m["relationships"][2].update({"fieldName": "zones"}))
        self.assertTrue(any("visit_zones: a MANY_TO_MANY relationship has no fieldName" in e for e in errors), errors)

    def test_an_unknown_relationship_type_is_refused(self):
        errors = self.mutate(lambda m: m["relationships"][0].update({"type": "ONE_TO_ONE"}))
        self.assertTrue(any("visit_zone: type must be one of MANY_TO_ONE, MANY_TO_MANY" in e for e in errors), errors)

    def test_a_default_must_be_an_option_of_its_enum_and_a_boolean_true_or_false(self):
        errors = self.mutate(lambda m: (m["objects"][1]["fields"][1].update({"defaultValue": "OTRA"}),
                                        m["objects"][1]["fields"][2].update({"defaultValue": "si"})))
        self.assertTrue(any("zone_type: defaultValue 'OTRA' is not an option" in e for e in errors), errors)
        self.assertTrue(any("active: defaultValue of a BOOLEAN must be 'true' or 'false'" in e for e in errors), errors)

    def test_a_long_text_is_never_indexed(self):
        errors = self.mutate(lambda m: m["objects"][0]["fields"].append(
            {"name": "notes", "label": "Notas", "type": "LONG_TEXT", "indexed": True}))
        self.assertTrue(any("visit field notes: a LONG_TEXT field cannot be indexed" in e for e in errors), errors)

    def test_an_index_set_needs_two_known_fields(self):
        errors = self.mutate(lambda m: m["objects"][0].update({"indexes": [["visit_date"], ["zone", "nowhere"]]}))
        self.assertTrue(any("indexes ['visit_date'] needs at least 2 fields" in e for e in errors), errors)
        self.assertTrue(any("names 'nowhere', which is not a field of visit" in e for e in errors), errors)


class ApplyTestCase(unittest.TestCase):
    def setUp(self):
        self.core = FakeCore()
        self.addCleanup(self.core.stop)

    def run_apply(self, model_path, *extra):
        return quiet(apply.main, ["--model", model_path, "--core", self.core.base_url,
                                  "--email", "admin@wasichai.local", "--password", "admin", *extra])

    def writes(self):
        return [(m, p, b) for m, p, _, b in self.core.requests if m in ("POST", "PUT", "DELETE") and p != "/api/auth/login"]


class EmptyModelTests(ApplyTestCase):
    def test_the_f1_model_writes_nothing(self):
        code, out, err = self.run_apply(MODEL_PATH)
        self.assertEqual(code, 0, err)
        self.assertIn("done: 0 created, 0 updated, 0 skipped", out)
        self.assertEqual(self.writes(), [])

    def test_dry_run_has_nothing_to_send(self):
        code, out, err = self.run_apply(MODEL_PATH, "--dry-run")
        self.assertEqual(code, 0, err)
        self.assertEqual(out, "")
        self.assertEqual(self.core.requests, [])


class FixtureApplyTests(ApplyTestCase):
    def test_creates_objects_action_and_relationship_in_order(self):
        with tempfile.TemporaryDirectory() as folder:
            code, out, err = self.run_apply(write_model(folder, FIXTURE_MODEL))
        self.assertEqual(code, 0, err)
        posts = [(p, b) for m, p, b in self.writes() if m == "POST"]
        self.assertEqual([p for p, _ in posts],
                         ["/api/objects", "/api/objects", "/api/metadata/objects/visit/actions", "/api/relationships"])
        self.assertEqual([b["name"] for p, b in posts if p == "/api/objects"], ["zone", "visit"])
        self.assertTrue(next(b for p, b in posts if p == "/api/objects" and b["name"] == "visit")["requiresReason"])
        self.assertIn("done: 4 created, 0 updated, 0 skipped", out)

    def test_derived_never_reaches_wasichai(self):
        with tempfile.TemporaryDirectory() as folder:
            code, out, err = self.run_apply(write_model(folder, FIXTURE_MODEL))
        self.assertEqual(code, 0, err)
        zone = next(b for m, p, b in self.writes() if p == "/api/objects" and b["name"] == "zone")
        self.assertEqual([f["name"] for f in zone["fields"]], ["code", "zone_type", "area_ha"])
        for field in zone["fields"]:
            keys = {"name", "label", "type", "required", "unique"} | ({"enumOptions"} if field["type"] == "ENUM" else set())
            self.assertEqual(set(field), keys, field)


class F2FixtureApplyTests(ApplyTestCase):
    def apply_f2_fixture(self, *extra):
        with tempfile.TemporaryDirectory() as folder:
            return self.run_apply(write_model(folder, F2_FIXTURE_MODEL), *extra)

    def test_every_object_goes_before_the_first_relationship_whatever_their_order(self):
        code, out, err = self.apply_f2_fixture()
        self.assertEqual(code, 0, err)
        posts = [(p, b) for m, p, b in self.writes() if m == "POST"]
        self.assertEqual([(p, b["name"]) for p, b in posts],
                         [("/api/objects", "visit"), ("/api/objects", "zone"), ("/api/relationships", "visit_zone"),
                          ("/api/relationships", "zone_parent_zone"), ("/api/relationships", "visit_zones")])

    def test_a_many_to_many_goes_without_field_and_a_self_reference_with_its_field(self):
        code, out, err = self.apply_f2_fixture()
        self.assertEqual(code, 0, err)
        relationships = {b["name"]: b for m, p, b in self.writes() if p == "/api/relationships"}
        self.assertEqual(relationships["visit_zones"]["type"], "MANY_TO_MANY")
        self.assertNotIn("fieldName", relationships["visit_zones"])
        self.assertEqual((relationships["zone_parent_zone"]["source"], relationships["zone_parent_zone"]["target"],
                          relationships["zone_parent_zone"]["fieldName"]), ("zone", "zone", "parent_zone"))
        self.assertIn(("PUT", "/api/metadata/objects/visit/fields/zone", {"required": True}), self.writes())

    def test_indexed_and_default_value_go_in_the_field_and_derived_never(self):
        code, out, err = self.apply_f2_fixture()
        self.assertEqual(code, 0, err)
        objects = {b["name"]: b for m, p, b in self.writes() if p == "/api/objects" and m == "POST"}
        visit_date = objects["visit"]["fields"][0]
        self.assertTrue(visit_date["indexed"])
        zone = {f["name"]: f for f in objects["zone"]["fields"]}
        self.assertEqual((zone["zone_type"]["defaultValue"], zone["active"]["defaultValue"]), ("DIRECTA", "true"))
        self.assertNotIn("indexed", zone["code"])

    def test_an_index_over_a_relationship_field_waits_for_phase_3(self):
        code, out, err = self.apply_f2_fixture()
        self.assertEqual(code, 0, err)
        objects = {b["name"]: b for m, p, b in self.writes() if p == "/api/objects" and m == "POST"}
        self.assertNotIn("indexes", objects["visit"])
        puts = [b for m, p, b in self.writes() if m == "PUT" and p == "/api/objects/visit"]
        self.assertEqual([b["indexes"] for b in puts], [[["zone", "visit_date"]]])
        self.assertEqual(self.core.object_flags["visit"]["indexes"], [["zone", "visit_date"]])

    def test_a_second_run_puts_no_field_set_again(self):
        self.apply_f2_fixture()
        self.core.requests.clear()
        code, out, err = self.apply_f2_fixture()
        self.assertEqual(code, 0, err)
        self.assertEqual([p for m, p, b in self.writes() if m == "PUT" and p.startswith("/api/objects/")], [])


class WorkflowsPassTests(ApplyTestCase):
    def test_f1_has_no_definitions_and_calls_nothing(self):
        code, out, err = self.run_apply(MODEL_PATH, "--workflows")
        self.assertEqual(code, 0, err)
        self.assertIn("done: 0 workflows, 0 document types, 0 automations, 0 views, 0 forms, 0 pages", out)
        self.assertEqual(self.core.requests, [])

    def test_a_definition_before_its_phase_stops_with_2(self):
        with tempfile.TemporaryDirectory() as folder:
            path = write_model(folder, load(MODEL_PATH))
            os.mkdir(os.path.join(folder, "workflows"))
            with open(os.path.join(folder, "workflows", "project.json"), "w", encoding="utf-8") as f:
                f.write("{}")
            code, out, err = self.run_apply(path, "--workflows")
        self.assertEqual(code, 2)
        self.assertIn("model/workflows/project.json", err)
        self.assertEqual(self.core.requests, [])


class NewTenantTests(unittest.TestCase):
    """security.md §4.1: un tenant nuevo se prepara con apply.py, apply_roles.py y apply.py --workflows, en ese orden."""

    def test_a_new_tenant_is_provisioned_in_order_and_again_is_a_no_op(self):
        core = FakeCore()
        self.addCleanup(core.stop)
        login = ["--core", core.base_url, "--email", "admin-tenant@sgspe.test", "--password", "clave-del-tenant"]
        runs = [
            quiet(apply.main, ["--model", MODEL_PATH, *login]),
            quiet(apply_roles.main, ["--roles", ROLES_PATH, "--model", MODEL_PATH, *login]),
            quiet(apply.main, ["--model", MODEL_PATH, "--workflows", *login]),
        ]
        self.assertEqual([code for code, _, _ in runs], [0, 0, 0], runs)
        self.assertIn("done: 15 created, 0 updated, 0 skipped", runs[1][1])
        self.assertEqual(sorted(core.roles), sorted(r["name"] for r in load(ROLES_PATH)["roles"]))
        again = quiet(apply_roles.main, ["--roles", ROLES_PATH, "--model", MODEL_PATH, *login])
        self.assertEqual(again[0], 0, again)
        self.assertIn("done: 0 created, 0 updated, 15 skipped", again[1])


class ProvisioningOrderTests(unittest.TestCase):
    """Con objetos de verdad: wasichai rechaza un rol que nombra un objeto que no existe, así que el modelo va primero."""

    def test_the_first_object_is_created_before_the_first_role(self):
        core = FakeCore()
        self.addCleanup(core.stop)
        login = ["--core", core.base_url, "--email", "admin-tenant@sgspe.test", "--password", "clave-del-tenant"]
        with tempfile.TemporaryDirectory() as folder:
            model_path = write_model(folder, FIXTURE_MODEL)
            runs = [quiet(apply.main, ["--model", model_path, *login]),
                    quiet(apply_roles.main, ["--roles", ROLES_PATH, "--model", model_path, *login]),
                    quiet(apply.main, ["--model", model_path, "--workflows", *login])]
        self.assertEqual([code for code, _, _ in runs], [0, 0, 0], runs)
        posts = [p for m, p, _, _ in core.requests if m == "POST"]
        self.assertLess(posts.index("/api/objects"), posts.index("/api/roles"))


if __name__ == "__main__":
    unittest.main()
