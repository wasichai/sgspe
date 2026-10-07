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
EMPTY_MODEL = {"enums": {}, "objects": [], "relationships": []}


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


class ModelOfF2Tests(unittest.TestCase):
    def test_f2_has_its_48_objects_and_is_valid(self):
        model = load(MODEL_PATH)
        self.assertEqual(len(model["objects"]), 48)
        self.assertEqual(apply.validate(model), [])

    def test_the_f2_workflows_are_valid(self):
        workflows, errors = apply.load_workflows(HERE, load(MODEL_PATH))
        self.assertEqual(errors, [])
        self.assertEqual(sorted(name for name, _ in workflows),
                         ["area_of_influence", "engagement", "field_visit", "project", "stakeholder"])


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

    def test_a_repeated_set_is_refused_in_indexes_and_in_unique_constraints(self):
        errors = self.mutate(lambda m: m["objects"][1].update({
            "indexes": [["code", "zone_type"], ["code", "zone_type"]],
            "uniqueConstraints": [["code", "active"], ["code", "active"]]}))
        self.assertTrue(any("indexes ['code', 'zone_type'] is declared twice" in e for e in errors), errors)
        self.assertTrue(any("uniqueConstraints ['code', 'active'] is declared twice" in e for e in errors), errors)

    def test_a_geometry_or_a_long_text_is_in_neither_a_unique_constraint_nor_an_index(self):
        def add(m):
            m["objects"][1]["fields"].extend([
                {"name": "shape", "label": "Forma", "type": "GEOMETRY", "geometryType": "POINT"},
                {"name": "notes", "label": "Notas", "type": "LONG_TEXT"}])
            m["objects"][1]["uniqueConstraints"] = [["code", "shape"], ["code", "notes"]]
            m["objects"][1]["indexes"] = [["code", "shape"], ["code", "notes"]]
        errors = self.mutate(add)
        for key in ("uniqueConstraints", "indexes"):
            self.assertTrue(any(f"{key} ['code', 'shape'] names 'shape', a GEOMETRY field cannot be in it" in e
                                for e in errors), errors)
            self.assertTrue(any(f"{key} ['code', 'notes'] names 'notes', a LONG_TEXT field cannot be in it" in e
                                for e in errors), errors)


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
    def test_an_empty_model_writes_nothing(self):
        with tempfile.TemporaryDirectory() as folder:
            code, out, err = self.run_apply(write_model(folder, EMPTY_MODEL))
        self.assertEqual(code, 0, err)
        self.assertIn("done: 0 created, 0 updated, 0 skipped", out)
        self.assertEqual(self.writes(), [])

    def test_dry_run_has_nothing_to_send(self):
        with tempfile.TemporaryDirectory() as folder:
            code, out, err = self.run_apply(write_model(folder, EMPTY_MODEL), "--dry-run")
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

    def test_a_second_run_changes_nothing_and_reports_everything_skipped(self):
        self.apply_f2_fixture()
        self.core.requests.clear()
        code, out, err = self.apply_f2_fixture()
        self.assertEqual(code, 0, err)
        self.assertIn("done: 0 created, 0 updated, 5 skipped", out)
        # the relationship POSTs answer 409; the only other write is the required relationship's field, which Core
        # takes as many times as it is sent
        others = [w for w in self.writes() if not (w[0] == "POST" and w[1] == "/api/relationships")]
        self.assertEqual(others, [("PUT", "/api/metadata/objects/visit/fields/zone", {"required": True})])

    def described_model(self):
        model = copy.deepcopy(F2_FIXTURE_MODEL)
        model["objects"][0]["fields"][0]["description"] = "  Fecha de la visita "
        return model

    def apply_model(self, model):
        with tempfile.TemporaryDirectory() as folder:
            return self.run_apply(write_model(folder, model))

    def field_puts(self):
        return [(p, b) for m, p, b in self.writes() if m == "PUT" and "/fields/" in p and b != {"required": True}]

    def test_indexed_and_description_that_differ_are_put_and_then_settle(self):
        model = self.described_model()
        self.apply_model(model)
        stored = {f["name"]: f for f in self.core.existing_fields["visit"]}["visit_date"]
        self.assertEqual((stored["indexed"], stored["description"]), (True, "Fecha de la visita"))
        stored.update({"indexed": False, "description": "otra"})
        self.core.requests.clear()
        code, out, err = self.apply_model(model)
        self.assertEqual(code, 0, err)
        self.assertEqual(self.field_puts(), [("/api/metadata/objects/visit/fields/visit_date", {"indexed": True}),
                                             ("/api/metadata/objects/visit/fields/visit_date",
                                              {"description": "Fecha de la visita"})])
        self.assertIn("done: 0 created, 2 updated, 4 skipped", out)
        self.core.requests.clear()
        code, out, err = self.apply_model(model)
        self.assertEqual(code, 0, err)
        self.assertEqual(self.field_puts(), [])
        self.assertIn("done: 0 created, 0 updated, 5 skipped", out)

    def test_the_same_indexed_and_the_same_untrimmed_description_put_no_field(self):
        model = self.described_model()
        self.apply_model(model)
        self.core.requests.clear()
        code, out, err = self.apply_model(model)
        self.assertEqual(code, 0, err)
        self.assertEqual(self.field_puts(), [])
        self.assertEqual([p for m, p, b in self.writes() if m == "PUT" and p.startswith("/api/objects/")], [])

    def test_an_indexed_that_the_model_dropped_is_put_false(self):
        self.apply_f2_fixture()
        model = copy.deepcopy(F2_FIXTURE_MODEL)
        del model["objects"][0]["fields"][0]["indexed"]
        self.core.requests.clear()
        code, out, err = self.apply_model(model)
        self.assertEqual(code, 0, err)
        self.assertEqual(self.field_puts(), [("/api/metadata/objects/visit/fields/visit_date", {"indexed": False})])


def write_workflow(folder, object_name, workflow):
    os.makedirs(os.path.join(folder, "workflows"), exist_ok=True)
    with open(os.path.join(folder, "workflows", f"{object_name}.json"), "w", encoding="utf-8") as f:
        json.dump(workflow, f, ensure_ascii=False)


ZONE_WORKFLOW = {
    "name": "wf-x-zone",
    "label": "Zona",
    "enabled": True,
    "definition": {
        "states": [{"name": "draft", "label": "Borrador", "type": "INITIAL"},
                   {"name": "closed", "label": "Cerrada", "type": "FINAL"}],
        "transitions": [{"name": "close", "label": "Cerrar", "from": "draft", "to": "closed", "roles": ["READ_ONLY"]}],
    },
}


class WorkflowsPassTests(ApplyTestCase):
    def test_without_definitions_it_calls_nothing(self):
        with tempfile.TemporaryDirectory() as folder:
            code, out, err = self.run_apply(write_model(folder, EMPTY_MODEL), "--workflows")
        self.assertEqual(code, 0, err)
        self.assertIn("done: 0 workflows (0 created, 0 updated, 0 skipped), 0 document types", out)
        self.assertEqual(self.core.requests, [])

    def test_a_kind_before_its_phase_stops_with_2(self):
        with tempfile.TemporaryDirectory() as folder:
            path = write_model(folder, load(MODEL_PATH))
            os.mkdir(os.path.join(folder, "automations"))
            with open(os.path.join(folder, "automations", "project.json"), "w", encoding="utf-8") as f:
                f.write("{}")
            code, out, err = self.run_apply(path, "--workflows")
        self.assertEqual(code, 2)
        self.assertIn("model/automations/project.json", err)
        self.assertEqual(self.core.requests, [])

    def test_puts_a_missing_workflow_and_skips_it_the_second_time(self):
        self.core.roles["READ_ONLY"] = {"name": "READ_ONLY"}
        with tempfile.TemporaryDirectory() as folder:
            path = write_model(folder, FIXTURE_MODEL)
            write_workflow(folder, "zone", ZONE_WORKFLOW)
            self.run_apply(path)
            first = self.run_apply(path, "--workflows")
            second = self.run_apply(path, "--workflows")
        self.assertEqual(first[0], 0, first)
        self.assertIn("create workflow wf-x-zone", first[1])
        self.assertEqual(self.core.workflows["zone"]["definition"]["transitions"][0]["roles"], ["READ_ONLY"])
        self.assertEqual(second[0], 0, second)
        self.assertIn("skip   workflow wf-x-zone (up to date)", second[1])
        self.assertIn("done: 1 workflows (0 created, 0 updated, 1 skipped)", second[1])

    def test_replaces_a_workflow_edited_in_the_admin(self):
        self.core.roles["READ_ONLY"] = {"name": "READ_ONLY"}
        with tempfile.TemporaryDirectory() as folder:
            path = write_model(folder, FIXTURE_MODEL)
            write_workflow(folder, "zone", ZONE_WORKFLOW)
            self.run_apply(path)
            self.run_apply(path, "--workflows")
            self.core.workflows["zone"]["definition"]["transitions"][0]["roles"] = []
            code, out, err = self.run_apply(path, "--workflows")
        self.assertEqual(code, 0, err)
        self.assertIn("update workflow wf-x-zone", out)
        self.assertEqual(self.core.workflows["zone"]["definition"]["transitions"][0]["roles"], ["READ_ONLY"])

    def test_a_workflow_that_names_a_role_core_lacks_stops_with_1(self):
        with tempfile.TemporaryDirectory() as folder:
            path = write_model(folder, FIXTURE_MODEL)
            write_workflow(folder, "zone", ZONE_WORKFLOW)
            self.run_apply(path)
            code, out, err = self.run_apply(path, "--workflows")
        self.assertEqual(code, 1)
        self.assertIn("PUT /api/objects/zone/workflow -> 400", err)

    def test_an_invalid_workflow_stops_with_2_before_calling_core(self):
        broken = copy.deepcopy(ZONE_WORKFLOW)
        broken["definition"]["transitions"][0]["roles"] = []
        broken["definition"]["states"][1]["type"] = "INITIAL"
        with tempfile.TemporaryDirectory() as folder:
            path = write_model(folder, FIXTURE_MODEL)
            write_workflow(folder, "zone", broken)
            code, out, err = self.run_apply(path, "--workflows")
        self.assertEqual(code, 2)
        self.assertIn("exactly one state must be INITIAL", err)
        self.assertIn("transition close has no roles", err)
        self.assertEqual(self.core.requests, [])

    def test_validate_only_also_checks_the_workflows(self):
        broken = copy.deepcopy(ZONE_WORKFLOW)
        broken["name"] = "zona"
        with tempfile.TemporaryDirectory() as folder:
            path = write_model(folder, FIXTURE_MODEL)
            write_workflow(folder, "zone", broken)
            code, out, err = self.run_apply(path, "--validate-only")
        self.assertEqual(code, 2)
        self.assertIn("name must be wf-<letter>-zone", out)


DONE_WORKFLOW = {
    "name": "wf-x-zone",
    "label": "Zona",
    "enabled": True,
    "definition": {
        "states": [{"name": "draft", "label": "Borrador", "type": "INITIAL"},
                   {"name": "done", "label": "Hecha", "type": "FINAL"}],
        "transitions": [{"name": "finish", "label": "Terminar", "from": "draft", "to": "done", "roles": ["READ_ONLY"]}],
    },
}


class WorkflowRecordsTests(ApplyTestCase):
    """wasichai's PUT .../workflow checks no record: one that drops a state in use, or one that is created over records,
    leaves them with no transition."""

    def setUp(self):
        super().setUp()
        self.core.roles["READ_ONLY"] = {"name": "READ_ONLY"}

    def provision(self, folder, workflow=None):
        path = write_model(folder, FIXTURE_MODEL)
        self.run_apply(path)
        if workflow is not None:
            write_workflow(folder, "zone", workflow)
            self.assertEqual(self.run_apply(path, "--workflows")[0], 0)
        self.core.requests.clear()
        return path

    def test_a_replacement_that_drops_a_state_with_records_is_refused_naming_states_and_counts(self):
        with tempfile.TemporaryDirectory() as folder:
            path = self.provision(folder, ZONE_WORKFLOW)
            self.core.add_record("zone", {"code": "Z1"}, state="closed")
            self.core.add_record("zone", {"code": "Z2"}, state="closed")
            self.core.add_record("zone", {"code": "Z3"}, state="draft")
            write_workflow(folder, "zone", DONE_WORKFLOW)
            code, out, err = self.run_apply(path, "--workflows")
        self.assertEqual(code, 1)
        self.assertIn("zone", err)
        self.assertIn("closed (2 records)", err)
        self.assertNotIn("draft (", err)
        self.assertEqual(self.writes(), [])
        self.assertEqual(self.core.workflows["zone"]["definition"]["states"][1]["name"], "closed")

    def test_a_replacement_that_drops_a_state_nobody_uses_goes_on(self):
        with tempfile.TemporaryDirectory() as folder:
            path = self.provision(folder, ZONE_WORKFLOW)
            self.core.add_record("zone", {"code": "Z3"}, state="draft")
            write_workflow(folder, "zone", DONE_WORKFLOW)
            code, out, err = self.run_apply(path, "--workflows")
        self.assertEqual(code, 0, err)
        self.assertIn("update workflow wf-x-zone", out)
        self.assertEqual(self.core.workflows["zone"]["definition"]["states"][1]["name"], "done")

    def test_a_replacement_that_keeps_every_state_never_lists_the_records(self):
        changed = copy.deepcopy(ZONE_WORKFLOW)
        changed["definition"]["transitions"][0]["roles"] = ["READ_ONLY", "READ_ONLY"]
        with tempfile.TemporaryDirectory() as folder:
            path = self.provision(folder, ZONE_WORKFLOW)
            self.core.add_record("zone", {"code": "Z1"}, state="closed")
            write_workflow(folder, "zone", changed)
            code, out, err = self.run_apply(path, "--workflows")
        self.assertEqual(code, 0, err)
        self.assertEqual([p for m, p, _, _ in self.core.requests if "/records" in p], [])

    def test_a_record_count_that_is_missing_stops_the_pass_instead_of_counting_zero(self):
        with tempfile.TemporaryDirectory() as folder:
            path = self.provision(folder)
            self.core.add_record("zone", {"code": "Z1"})
            write_workflow(folder, "zone", ZONE_WORKFLOW)
            scripted = self.core.server.script

            def without_total(method, full_path, body):
                status, payload = scripted(method, full_path, body)
                if method == "GET" and "/records" in full_path and isinstance(payload, dict):
                    payload = {k: v for k, v in payload.items() if k != "totalElements"}
                return status, payload

            self.core.server.script = without_total
            code, out, err = self.run_apply(path, "--workflows")
        self.assertEqual(code, 1)
        self.assertIn("no record count", err)
        self.assertEqual(self.writes(), [])

    def test_a_workflow_created_over_records_is_refused_without_the_flag(self):
        with tempfile.TemporaryDirectory() as folder:
            path = self.provision(folder)
            self.core.add_record("zone", {"code": "Z1"})
            write_workflow(folder, "zone", ZONE_WORKFLOW)
            code, out, err = self.run_apply(path, "--workflows")
        self.assertEqual(code, 1)
        self.assertIn("zone has 1 record", err)
        self.assertIn("null state", err)
        self.assertIn("--allow-stateless-records", err)
        self.assertEqual(self.writes(), [])

    def test_the_flag_creates_it_and_warns(self):
        with tempfile.TemporaryDirectory() as folder:
            path = self.provision(folder)
            self.core.add_record("zone", {"code": "Z1"})
            self.core.add_record("zone", {"code": "Z2"})
            write_workflow(folder, "zone", ZONE_WORKFLOW)
            code, out, err = self.run_apply(path, "--workflows", "--allow-stateless-records")
        self.assertEqual(code, 0, err)
        self.assertIn("warning: zone has 2 records", err)
        self.assertIn("create workflow wf-x-zone", out)
        self.assertIn("wf-x-zone", self.core.workflows["zone"]["name"])

    def test_an_object_without_records_needs_no_flag(self):
        with tempfile.TemporaryDirectory() as folder:
            path = self.provision(folder)
            write_workflow(folder, "zone", ZONE_WORKFLOW)
            code, out, err = self.run_apply(path, "--workflows")
        self.assertEqual(code, 0, err)
        self.assertEqual(err, "")

    def test_the_help_documents_the_flag(self):
        out = io.StringIO()
        with redirect_stdout(out), self.assertRaises(SystemExit):
            apply.main(["--help"])
        self.assertIn("--allow-stateless-records", out.getvalue())

    def test_a_workflow_as_the_admin_stores_it_is_still_up_to_date(self):
        # what GET answers: the id, the object, and x/y on each state (null or placed on the canvas)
        with tempfile.TemporaryDirectory() as folder:
            path = self.provision(folder, ZONE_WORKFLOW)
            stored = copy.deepcopy(self.core.workflows["zone"])
            stored.update({"id": "0b5c1d6e-0000-4000-8000-000000000001", "objectName": "zone"})
            stored["definition"]["states"][0].update({"x": 40.5, "y": 80})
            stored["definition"]["states"][1].update({"x": None, "y": None})
            stored["definition"]["transitions"][0]["id"] = "t-1"
            self.core.workflows["zone"] = stored
            code, out, err = self.run_apply(path, "--workflows")
        self.assertEqual(code, 0, err)
        self.assertIn("skip   workflow wf-x-zone (up to date)", out)
        self.assertEqual(self.writes(), [])


class WorkflowValidationTests(ApplyTestCase):
    def run_with(self, workflow, *extra):
        with tempfile.TemporaryDirectory() as folder:
            path = write_model(folder, FIXTURE_MODEL)
            os.makedirs(os.path.join(folder, "workflows"))
            with open(os.path.join(folder, "workflows", "zone.json"), "w", encoding="utf-8") as f:
                json.dump(workflow, f)
            return self.run_apply(path, *extra)

    def test_roles_are_upper_case_names_and_labels_have_no_edge_spaces(self):
        broken = copy.deepcopy(ZONE_WORKFLOW)
        broken["label"] = "Zona "
        broken["definition"]["states"][0]["label"] = " Borrador"
        broken["definition"]["transitions"][0]["label"] = "Cerrar "
        broken["definition"]["transitions"][0]["roles"] = ["read_only", "FIELD AGENT"]
        code, out, err = self.run_with(broken, "--workflows")
        self.assertEqual(code, 2)
        self.assertIn("label must have no leading or trailing spaces", err)
        self.assertIn("state 'draft' label must have no leading or trailing spaces", err)
        self.assertIn("transition close label must have no leading or trailing spaces", err)
        self.assertIn("transition close role 'read_only' must match [A-Z0-9_]+", err)
        self.assertIn("transition close role 'FIELD AGENT' must match [A-Z0-9_]+", err)
        self.assertEqual(self.core.requests, [])

    def test_a_definition_that_is_not_an_object_exits_2_without_a_traceback(self):
        for bad in ([], "texto", 3, None):
            code, out, err = self.run_with(bad, "--workflows")
            self.assertEqual(code, 2, bad)
            self.assertIn("must be a JSON object", err)

    def test_a_state_a_transition_or_a_definition_that_is_not_an_object_exits_2(self):
        for mutate in (lambda w: w["definition"]["states"].append("texto"),
                       lambda w: w["definition"]["transitions"].append([]),
                       lambda w: w.update({"definition": []}),
                       lambda w: w["definition"].update({"states": {}}),
                       lambda w: w["definition"]["transitions"][0].update({"roles": "READ_ONLY"})):
            broken = copy.deepcopy(ZONE_WORKFLOW)
            mutate(broken)
            code, out, err = self.run_with(broken, "--workflows")
            self.assertEqual(code, 2, broken)
            self.assertIn("must be", err)
            self.assertEqual(self.core.requests, [])

    def test_validate_only_refuses_a_kind_before_its_phase_too(self):
        with tempfile.TemporaryDirectory() as folder:
            path = write_model(folder, FIXTURE_MODEL)
            os.mkdir(os.path.join(folder, "automations"))
            with open(os.path.join(folder, "automations", "project.json"), "w", encoding="utf-8") as f:
                f.write("{}")
            code, out, err = self.run_apply(path, "--validate-only")
        self.assertEqual(code, 2)
        self.assertIn("model/automations/project.json", out + err)


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
        self.assertIn("done: 5 workflows (5 created, 0 updated, 0 skipped)", runs[2][1])
        self.assertEqual(sorted(core.roles), sorted(r["name"] for r in load(ROLES_PATH)["roles"]))
        self.assertEqual(sorted(core.workflows), ["area_of_influence", "engagement", "field_visit", "project", "stakeholder"])
        again = quiet(apply_roles.main, ["--roles", ROLES_PATH, "--model", MODEL_PATH, *login])
        self.assertEqual(again[0], 0, again)
        self.assertIn("done: 0 created, 0 updated, 15 skipped", again[1])


class ProvisioningOrderTests(unittest.TestCase):
    """Con objetos de verdad: wasichai rechaza un rol que nombra un objeto que no existe, así que el modelo va primero."""

    def test_the_workflows_before_the_roles_fail_and_after_them_pass(self):
        core = FakeCore()
        self.addCleanup(core.stop)
        login = ["--core", core.base_url, "--email", "admin-tenant@sgspe.test", "--password", "clave-del-tenant"]
        model = quiet(apply.main, ["--model", MODEL_PATH, *login])
        early = quiet(apply.main, ["--model", MODEL_PATH, "--workflows", *login])
        roles = quiet(apply_roles.main, ["--roles", ROLES_PATH, "--model", MODEL_PATH, *login])
        late = quiet(apply.main, ["--model", MODEL_PATH, "--workflows", *login])
        self.assertEqual([model[0], early[0], roles[0], late[0]], [0, 1, 0, 0], [model, early, roles, late])

    def test_the_first_object_is_created_before_the_first_role(self):
        core = FakeCore()
        self.addCleanup(core.stop)
        login = ["--core", core.base_url, "--email", "admin-tenant@sgspe.test", "--password", "clave-del-tenant"]
        runs = [quiet(apply.main, ["--model", MODEL_PATH, *login]),
                quiet(apply_roles.main, ["--roles", ROLES_PATH, "--model", MODEL_PATH, *login]),
                quiet(apply.main, ["--model", MODEL_PATH, "--workflows", *login])]
        self.assertEqual([code for code, _, _ in runs], [0, 0, 0], runs)
        writes = [(m, p) for m, p, _, _ in core.requests if m in ("POST", "PUT")]
        self.assertLess(writes.index(("POST", "/api/objects")), writes.index(("POST", "/api/roles")))
        self.assertLess(writes.index(("POST", "/api/roles")), writes.index(("PUT", "/api/objects/project/workflow")))


if __name__ == "__main__":
    unittest.main()
