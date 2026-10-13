"""Pruebas de apply.py contra un FakeCore: el modelo vacío de F1, un modelo de prueba, el segundo pase (--workflows) con
los workflows, los tipos de documento y las automatizaciones, los campos FILE, el aprovisionamiento de un tenant nuevo en
su orden (security.md §4.1 y §14, «Alta de un tenant») y la subida de un tenant de F2 o de F3 a F4.

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


class ModelOfF7Tests(unittest.TestCase):
    def test_f7_has_its_84_objects_and_is_valid(self):
        model = load(MODEL_PATH)
        self.assertEqual(len(model["objects"]), 84)
        self.assertEqual(apply.validate(model), [])

    def test_the_f7_definitions_are_valid(self):
        workflows, types, automations, errors = apply.load_definitions(HERE, load(MODEL_PATH))
        self.assertEqual(errors, [])
        self.assertEqual(sorted(name for name, _ in workflows), F7_WORKFLOWS)
        # ningún tipo de documento ni automatización nuevos: el acta de recepción y AUT-04 esperan a UPS-23 (§2.2 de la
        # especificación de F5), como el cargo de queja y las cartas de respuesta (decisión 3 de F4)
        self.assertEqual([(obj, t["name"]) for obj, t in types],
                         [("engagement", "meeting-minutes"), ("engagement", "attendance-list")])
        self.assertEqual([(obj, a["name"]) for obj, a in automations],
                         [("commitment", "aut-01-commitment-execution-task"),
                          ("early_warning", "aut-09-warning-triage-task"), ("engagement", "aut-05-meeting-minutes"),
                          ("engagement", "aut-10-engagement-reschedule-task"), ("grievance", "aut-02-grievance-ack-task"),
                          ("social_risk", "aut-11-critical-risk-task")])


# F6 no trajo workflows; F7 suma WF-K de ai_suggestion
F7_WORKFLOWS = ["agreement", "ai_suggestion", "area_of_influence", "commitment", "commitment_candidate", "early_warning",
                "engagement", "field_visit", "grievance", "incident", "inquiry", "project", "social_contribution",
                "social_program", "social_risk", "stakeholder", "stakeholder_request", "task"]


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


class RelationshipUniqueTests(unittest.TestCase):
    """F5: una sola entrega por aporte (decisión 2 de la especificación de F5). wasichai 1.0.0 rechaza un
    uniqueConstraints de un solo campo (FieldSets: «make a single field unique with unique: true»): el único sobre una
    relación opcional es el `unique: true` de su campo, que apply.py pone en la fase 3, cuando la relación existe."""

    def unique_fixture(self):
        model = copy.deepcopy(F2_FIXTURE_MODEL)
        model["relationships"][1]["unique"] = True  # zone_parent_zone: opcional, como delivery.social_contribution
        return model

    def test_a_one_field_unique_constraint_is_refused_and_unique_true_on_a_relationship_is_valid(self):
        self.assertEqual(apply.validate(self.unique_fixture()), [])
        model = copy.deepcopy(F2_FIXTURE_MODEL)
        model["objects"][1]["uniqueConstraints"] = [["parent_zone"]]
        self.assertTrue(any("uniqueConstraints ['parent_zone'] needs at least 2 fields (one field is unique: true)" in e
                            for e in apply.validate(model)), apply.validate(model))

    def test_the_unique_of_a_relationship_field_is_put_in_phase_3_once(self):
        core = FakeCore()
        self.addCleanup(core.stop)
        login = ["--core", core.base_url, "--email", "admin@wasichai.local", "--password", "admin"]
        with tempfile.TemporaryDirectory() as folder:
            path = write_model(folder, self.unique_fixture())
            code, out, err = quiet(apply.main, ["--model", path, *login])
            self.assertEqual(code, 0, err)
            self.assertIn("update field zone.parent_zone (unique)", out)
            puts = [(p, b) for m, p, _, b in core.requests if m == "PUT" and p.endswith("/fields/parent_zone")]
            self.assertEqual(puts, [("/api/metadata/objects/zone/fields/parent_zone", {"unique": True})])
            # the relationship goes first: the field the PUT names only exists once it is created
            writes = [p for m, p, _, _ in core.requests if m in ("POST", "PUT")]
            self.assertLess(writes.index("/api/relationships"),
                            writes.index("/api/metadata/objects/zone/fields/parent_zone"))
            core.requests.clear()
            code, out, err = quiet(apply.main, ["--model", path, *login])
            self.assertEqual(code, 0, err)
            self.assertNotIn("(unique)", out)
            self.assertEqual([p for m, p, _, _ in core.requests if m == "PUT" and p.endswith("/fields/parent_zone")], [])


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
            os.mkdir(os.path.join(folder, "views"))
            with open(os.path.join(folder, "views", "project.json"), "w", encoding="utf-8") as f:
                f.write("{}")
            code, out, err = self.run_apply(path, "--workflows")
        self.assertEqual(code, 2)
        self.assertIn("model/views/project.json: apply.py --workflows does not apply this kind yet", err)
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
            os.mkdir(os.path.join(folder, "pages"))
            with open(os.path.join(folder, "pages", "project.json"), "w", encoding="utf-8") as f:
                f.write("{}")
            code, out, err = self.run_apply(path, "--validate-only")
        self.assertEqual(code, 2)
        self.assertIn("model/pages/project.json", out + err)


def write_list(folder, kind, object_name, items):
    os.makedirs(os.path.join(folder, kind), exist_ok=True)
    with open(os.path.join(folder, kind, f"{object_name}.json"), "w", encoding="utf-8") as f:
        json.dump(items, f, ensure_ascii=False)


ZONE_SHEET = {
    "name": "zone-sheet",
    "label": "Ficha de la zona",
    "prefix": "FZ",
    "template": {"type": "doc", "content": [
        {"type": "heading", "attrs": {"level": 1}, "content": [
            {"type": "platformValue", "attrs": {"value": "documentName"}}, {"type": "text", "text": " "},
            {"type": "platformValue", "attrs": {"value": "documentNumber"}}]},
        {"type": "paragraph", "content": [{"type": "text", "text": "Código: "},
                                          {"type": "objectField", "attrs": {"field": "code"}}]},
        {"type": "relatedTable", "attrs": {"relationship": "visit_zone"}}]},
}

ZONE_CLOSED = {
    "name": "aut-x-zone-closed",
    "label": "Visita y ficha de la zona cerrada",
    "enabled": True,
    "definition": {
        "trigger": {"type": "STATE_ENTERED", "state": "closed"},
        "conditions": [{"field": "zone_type", "operator": "EQUALS", "value": "DIRECTA"}],
        "actions": [
            {"type": "CREATE_RECORD", "targetObject": "visit", "values": {"visit_date": "{{today}}", "zone": "{{id}}"}},
            {"type": "GENERATE_DOCUMENT", "documentType": "zone-sheet"},
        ],
    },
}


class DefinitionsPassTests(ApplyTestCase):
    """F3: --workflows applies the document types and then the automations, after the workflows (database-model.md §24)."""

    def setUp(self):
        super().setUp()
        self.core.roles["READ_ONLY"] = {"name": "READ_ONLY"}

    def run_with(self, *extra, sheet=ZONE_SHEET, automation=ZONE_CLOSED, model=FIXTURE_MODEL):
        with tempfile.TemporaryDirectory() as folder:
            path = write_model(folder, model)
            write_workflow(folder, "zone", ZONE_WORKFLOW)
            write_list(folder, "document-types", "zone", [sheet])
            write_list(folder, "automations", "zone", [automation])
            if not extra or extra[0] != "--validate-only":
                self.run_apply(path)
            return self.run_apply(path, *extra)

    def test_creates_the_types_then_the_automations_after_the_workflow_and_skips_them_the_second_time(self):
        code, out, err = self.run_with("--workflows")
        self.assertEqual(code, 0, err)
        writes = [(m, p) for m, p, _ in self.writes() if "/workflow" in p or "/document-types" in p or "/automations" in p]
        self.assertEqual(writes, [("PUT", "/api/objects/zone/workflow"), ("POST", "/api/objects/zone/document-types"),
                                  ("POST", "/api/objects/zone/automations")])
        self.assertIn("done: 1 workflows (1 created, 0 updated, 0 skipped), 1 document types (1 created, 0 updated, "
                      "0 skipped), 1 automations (1 created, 0 updated, 0 skipped), 0 views, 0 forms, 0 pages", out)
        self.core.requests.clear()
        code, out, err = self.run_with("--workflows")
        self.assertEqual(code, 0, err)
        self.assertIn("skip   document type zone.zone-sheet (up to date)", out)
        self.assertIn("skip   automation zone.aut-x-zone-closed (up to date)", out)
        self.assertEqual([w for w in self.writes() if "/document-types" in w[1] or "/automations" in w[1]], [])

    def test_replaces_a_type_and_an_automation_edited_in_the_admin(self):
        self.run_with("--workflows")
        self.core.document_types["zone"]["zone-sheet"]["label"] = "Ficha cambiada a mano"
        self.core.automations["zone"]["aut-x-zone-closed"]["definition"]["conditions"] = []
        code, out, err = self.run_with("--workflows")
        self.assertEqual(code, 0, err)
        self.assertIn("update document type zone.zone-sheet", out)
        self.assertIn("update automation zone.aut-x-zone-closed", out)
        self.assertEqual(self.core.document_types["zone"]["zone-sheet"]["label"], "Ficha de la zona")
        self.assertEqual(len(self.core.automations["zone"]["aut-x-zone-closed"]["definition"]["conditions"]), 1)

    def test_an_automation_core_has_and_model_does_not_is_reported_and_kept(self):
        self.run_with("--workflows")
        self.core.automations["zone"]["aut-x-leftover"] = {"label": "Sobrante", "enabled": True,
                                                         "definition": ZONE_CLOSED["definition"]}
        code, out, err = self.run_with("--workflows")
        self.assertEqual(code, 0, err)
        self.assertIn("warning: automation aut-x-leftover of zone is in core but not in model/", err)
        self.assertIn("aut-x-leftover", self.core.automations["zone"])

    def test_a_document_type_core_has_and_model_does_not_is_reported_and_kept(self):
        self.run_with("--workflows")
        self.core.document_types["zone"]["ficha-a-mano"] = {"label": "A mano", "prefix": "MANO",
                                                            "template": ZONE_SHEET["template"]}
        code, out, err = self.run_with("--workflows")
        self.assertEqual(code, 0, err)
        self.assertIn("warning: document type ficha-a-mano of zone is in core but not in model/", err)
        self.assertNotIn("zone-sheet of zone is in core", err)
        self.assertIn("ficha-a-mano", self.core.document_types["zone"])

    def test_dry_run_prints_the_bodies_and_calls_nothing(self):
        code, out, err = self.run_with("--workflows", "--dry-run")
        self.assertEqual(code, 0, err)
        self.assertIn("# POST /api/objects/zone/document-types", out)
        self.assertIn("# POST /api/objects/zone/automations", out)
        self.assertEqual([p for _, p, _ in self.writes() if "/document-types" in p or "/automations" in p], [])

    def refused(self, *expected, sheet=ZONE_SHEET, automation=ZONE_CLOSED):
        code, out, err = self.run_with("--validate-only", sheet=sheet, automation=automation)
        self.assertEqual(code, 2, out + err)
        for text in expected:
            self.assertIn(text, out + err)
        self.assertEqual(self.core.requests, [])

    def test_a_type_that_names_a_field_or_a_relationship_the_object_lacks_is_refused(self):
        sheet = copy.deepcopy(ZONE_SHEET)
        sheet["template"]["content"][1]["content"][1]["attrs"]["field"] = "nowhere"
        sheet["template"]["content"][2]["attrs"]["relationship"] = "zone_nowhere"
        sheet["prefix"] = "fz"
        self.refused("objectField names 'nowhere'", "relatedTable names 'zone_nowhere'", "prefix must match", sheet=sheet)

    def test_an_automation_that_names_a_state_or_a_type_the_object_lacks_is_refused(self):
        automation = copy.deepcopy(ZONE_CLOSED)
        automation["definition"]["trigger"]["state"] = "archived"
        automation["definition"]["actions"][1]["documentType"] = "zone-letter"
        self.refused("STATE_ENTERED names 'archived'", "documentType 'zone-letter' is not a document type of zone",
                     automation=automation)

    def test_an_automation_that_writes_a_derived_field_or_leaves_out_a_required_one_is_refused(self):
        automation = copy.deepcopy(ZONE_CLOSED)
        automation["definition"]["actions"] = [
            {"type": "UPDATE_FIELD", "field": "area_ha", "value": "1"},
            {"type": "CREATE_RECORD", "targetObject": "visit", "values": {"zone": "{{id}}"}},
        ]
        self.refused("UPDATE_FIELD writes the derived field area_ha", "CREATE_RECORD leaves out visit.visit_date",
                     automation=automation)

    def test_webhook_and_notify_are_not_used(self):
        automation = copy.deepcopy(ZONE_CLOSED)
        automation["definition"]["actions"] = [{"type": "WEBHOOK", "url": "https://ejemplo.test/x"},
                                               {"type": "NOTIFY", "to": "role:READ_ONLY", "title": "Zona cerrada"}]
        self.refused("WEBHOOK is not used", "NOTIFY is not used: sgspe.notification tells people by project assignment",
                     automation=automation)

    def test_a_condition_value_the_field_can_never_hold_is_refused(self):
        # wasichai compara el valor de una condición como texto, o como número si los dos lados lo son
        # (AutomationRules.same): AUT-11 compara risk_level con CRITICO, y un «Critico» o un «CRÍTICO» harían que la
        # automatización se salte en cada corrida sin un error
        model = copy.deepcopy(FIXTURE_MODEL)
        model["objects"][0]["fields"].append({"name": "active", "label": "Vigente", "type": "BOOLEAN"})
        automation = copy.deepcopy(ZONE_CLOSED)
        automation["definition"]["conditions"] = [
            {"field": "zone_type", "operator": "EQUALS", "value": "Directa"},
            {"field": "zone_type", "operator": "NOT_EQUALS", "value": "LEJANA"},
            {"field": "area_ha", "operator": "GREATER_THAN", "value": "diez"},
            {"field": "active", "operator": "EQUALS", "value": "sí"},
            {"field": "state", "operator": "EQUALS", "value": "archived"}]
        code, out, err = self.run_with("--validate-only", automation=automation, model=model)
        self.assertEqual(code, 2, out + err)
        for text in ("zone_type EQUALS 'Directa' is not an option of zone_type",
                     "zone_type NOT_EQUALS 'LEJANA' is not an option of zone_type",
                     "area_ha GREATER_THAN takes a number, not 'diez'", "active EQUALS takes true or false, not 'sí'",
                     "state EQUALS names 'archived', which is not a state of zone"):
            self.assertIn(text, out + err)
        self.assertEqual(self.core.requests, [])

    def test_a_condition_value_the_field_holds_is_valid(self):
        model = copy.deepcopy(FIXTURE_MODEL)
        model["objects"][0]["fields"].append({"name": "active", "label": "Vigente", "type": "BOOLEAN"})
        automation = copy.deepcopy(ZONE_CLOSED)
        automation["definition"]["conditions"] = [
            {"field": "zone_type", "operator": "EQUALS", "value": "INDIRECTA"},
            {"field": "zone_type", "operator": "CHANGED"},
            {"field": "area_ha", "operator": "LESS_THAN", "value": "10.5"},
            {"field": "active", "operator": "NOT_EQUALS", "value": "false"},
            {"field": "code", "operator": "CONTAINS", "value": "Z-"},
            {"field": "state", "operator": "NOT_EQUALS", "value": "draft"}]
        code, out, err = self.run_with("--validate-only", automation=automation, model=model)
        self.assertEqual(code, 0, out + err)

    def test_a_prefix_or_an_automation_name_used_twice_is_refused(self):
        with tempfile.TemporaryDirectory() as folder:
            path = write_model(folder, FIXTURE_MODEL)
            write_workflow(folder, "zone", ZONE_WORKFLOW)
            other = {**ZONE_SHEET, "name": "zone-letter"}
            write_list(folder, "document-types", "zone", [ZONE_SHEET, other])
            write_list(folder, "automations", "zone", [ZONE_CLOSED, ZONE_CLOSED])
            code, out, err = self.run_apply(path, "--validate-only")
        self.assertEqual(code, 2)
        self.assertIn("prefix FZ is used by two types", out)
        self.assertIn("automation aut-x-zone-closed is declared twice", out)

    def test_a_type_as_wasichai_stores_it_is_still_up_to_date(self):
        # what GET answers adds the id, the object and every node key as null: none of it is a difference
        self.run_with("--workflows")
        self.core.requests.clear()
        code, out, err = self.run_with("--workflows")
        self.assertEqual(code, 0, err)
        stored = self.core.server.script("GET", "/api/objects/zone/document-types/zone-sheet", None)[1]
        self.assertIsNone(stored["template"]["content"][0]["marks"])
        self.assertIn("skip   document type zone.zone-sheet (up to date)", out)


class DefinitionsRobustnessTests(ApplyTestCase):
    """Fix round 1: stable compare, wrong JSON types as error lines, unknown keys, leftovers on any object."""

    def run_with(self, *extra, sheet=ZONE_SHEET, automation=ZONE_CLOSED):
        return DefinitionsPassTests.run_with(self, *extra, sheet=sheet, automation=automation)

    refused = DefinitionsPassTests.refused

    def setUp(self):
        super().setUp()
        self.core.roles["READ_ONLY"] = {"name": "READ_ONLY"}

    def test_an_automation_without_conditions_is_skipped_the_second_time(self):
        automation = copy.deepcopy(ZONE_CLOSED)
        del automation["definition"]["conditions"]
        self.run_with("--workflows", automation=automation)
        self.assertEqual(self.core.automations["zone"]["aut-x-zone-closed"]["definition"]["conditions"], [])
        self.core.requests.clear()
        code, out, err = self.run_with("--workflows", automation=automation)
        self.assertEqual(code, 0, err)
        self.assertIn("skip   automation zone.aut-x-zone-closed (up to date)", out)
        self.assertEqual([w for w in self.writes() if "/automations" in w[1]], [])

    def test_wrong_json_types_are_error_lines_not_tracebacks(self):
        sheet = copy.deepcopy(ZONE_SHEET)
        sheet["template"]["content"][1]["content"][1]["attrs"] = ["field"]
        sheet["template"]["content"][2]["attrs"]["relationship"] = ["visit_zone"]
        self.refused("objectField names None", "relatedTable names ['visit_zone']", sheet=sheet)
        listed = {**ZONE_SHEET, "name": ["zone-sheet"]}
        self.refused("name must match", sheet=listed)
        automation = copy.deepcopy(ZONE_CLOSED)
        automation["definition"]["trigger"]["state"] = ["closed"]
        automation["definition"]["conditions"][0]["field"] = ["zone_type"]
        automation["definition"]["actions"] = [{"type": ["CREATE_RECORD"]},
                                               {"type": "GENERATE_DOCUMENT", "documentType": ["zone-sheet"]},
                                               {"type": "CREATE_RECORD", "targetObject": ["visit"]}]
        self.refused("STATE_ENTERED names", "a condition names", "action type must be one of", "documentType",
                     "CREATE_RECORD targets", automation=automation)

    def test_a_state_without_a_name_is_an_error_line(self):
        workflow = copy.deepcopy(ZONE_WORKFLOW)
        workflow["definition"]["states"].append({"label": "Sin nombre"})
        with tempfile.TemporaryDirectory() as folder:
            path = write_model(folder, FIXTURE_MODEL)
            write_workflow(folder, "zone", workflow)
            write_list(folder, "automations", "zone", [ZONE_CLOSED])
            write_list(folder, "document-types", "zone", [ZONE_SHEET])
            code, out, err = self.run_apply(path, "--validate-only")
        self.assertNotIn("Traceback", out + err)

    def test_unknown_keys_in_a_node_or_an_action_are_refused(self):
        sheet = copy.deepcopy(ZONE_SHEET)
        sheet["template"]["content"][1]["bogus"] = 1
        automation = copy.deepcopy(ZONE_CLOSED)
        automation["definition"]["actions"][1]["bogus"] = 1
        self.refused("template node has unknown keys", "GENERATE_DOCUMENT has unknown keys", sheet=sheet,
                     automation=automation)

    def test_a_blank_condition_value_is_refused(self):
        automation = copy.deepcopy(ZONE_CLOSED)
        automation["definition"]["conditions"][0]["value"] = "  "
        self.refused("needs a value", automation=automation)

    def test_a_leftover_automation_on_an_object_without_a_file_is_reported(self):
        self.run_with("--workflows")
        self.core.automations["visit"] = {"aut-x-old": {"label": "Vieja", "enabled": True,
                                                         "definition": ZONE_CLOSED["definition"]}}
        code, out, err = self.run_with("--workflows")
        self.assertEqual(code, 0, err)
        self.assertIn("warning: automation aut-x-old of visit is in core but not in model/", err)


class FileFieldTests(unittest.TestCase):
    """F3: a FILE field of wasichai-files (WAS-ADR-0061) is uploaded, never unique, defaulted or indexed."""

    def mutate(self, **keys):
        model = copy.deepcopy(FIXTURE_MODEL)
        model["objects"][1]["fields"].append({"name": "scan", "label": "Archivo", "type": "FILE", **keys})
        return apply.validate(model)

    def test_a_required_file_is_valid_and_goes_with_its_type(self):
        self.assertEqual(self.mutate(required=True), [])
        model = copy.deepcopy(FIXTURE_MODEL)
        model["objects"][1]["fields"].append({"name": "scan", "label": "Archivo", "type": "FILE", "required": True})
        self.assertEqual(apply.field_payload(model, model["objects"][1]["fields"][-1]),
                         {"name": "scan", "label": "Archivo", "type": "FILE", "required": True, "unique": False})

    def test_its_allow_list_of_media_types_goes_with_it_and_a_bad_one_is_refused(self):
        model = copy.deepcopy(FIXTURE_MODEL)
        model["objects"][1]["fields"].append({"name": "scan", "label": "Archivo", "type": "FILE",
                                              "contentTypes": ["application/pdf", "image/*"]})
        self.assertEqual(apply.validate(model), [])
        self.assertEqual(apply.field_payload(model, model["objects"][1]["fields"][-1])["contentTypes"],
                         ["application/pdf", "image/*"])
        for bad in ([], ["pdf"], ["image/*", "Text/Plain"]):
            self.assertTrue(any("contentTypes must be a non-empty list" in e for e in self.mutate(contentTypes=bad)), bad)
        model["objects"][1]["fields"][0]["contentTypes"] = ["application/pdf"]
        self.assertTrue(any("only a FILE field takes contentTypes" in e for e in apply.validate(model)))

    def test_a_file_is_never_unique_defaulted_or_indexed(self):
        self.assertTrue(any("a file can not be unique" in e for e in self.mutate(unique=True)))
        self.assertTrue(any("a file takes no defaultValue" in e for e in self.mutate(defaultValue="x")))
        self.assertTrue(any("a FILE field cannot be indexed" in e for e in self.mutate(indexed=True)))


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
        self.assertIn("done: 399 created, 26 updated, 0 skipped", runs[0][1])
        self.assertIn("update field delivery.social_contribution (unique)", runs[0][1])
        self.assertIn("done: 15 created, 0 updated, 0 skipped", runs[1][1])
        self.assertIn("done: 18 workflows (18 created, 0 updated, 0 skipped), 2 document types (2 created, 0 updated, "
                      "0 skipped), 6 automations (6 created, 0 updated, 0 skipped)", runs[2][1])
        self.assertEqual(sorted(core.roles), sorted(r["name"] for r in load(ROLES_PATH)["roles"]))
        self.assertEqual(sorted(core.workflows), F7_WORKFLOWS)
        self.assertEqual(sorted(core.automations), ["commitment", "early_warning", "engagement", "grievance", "social_risk"])
        again = [quiet(apply.main, ["--model", MODEL_PATH, *login]),
                 quiet(apply_roles.main, ["--roles", ROLES_PATH, "--model", MODEL_PATH, *login]),
                 quiet(apply.main, ["--model", MODEL_PATH, "--workflows", *login])]
        self.assertEqual([code for code, _, _ in again], [0, 0, 0], again)
        self.assertIn("done: 0 created, 0 updated, 399 skipped", again[0][1])
        self.assertIn("done: 0 created, 0 updated, 15 skipped", again[1][1])
        self.assertIn("done: 18 workflows (0 created, 0 updated, 18 skipped), 2 document types (0 created, 0 updated, "
                      "2 skipped), 6 automations (0 created, 0 updated, 6 skipped)", again[2][1])

    def test_a_tenant_of_f4_moves_to_f5_adding_only_what_f5_brings(self):
        # Review Focus 3: el modelo de F4 sale de model.json con las fases de las fichas (333 creados y 21 conjuntos, como
        # el aprovisionamiento de F4); subirlo a F5 agrega 10 objetos, 3 acciones y 34 relaciones (las de BC-10 y las de
        # delivery, stakeholder_request, task, evidence y project), pone los únicos de 4 objetos nuevos y el único de
        # delivery.social_contribution, y no toca nada de F4: ningún campo nuevo en un objeto anterior
        core = FakeCore()
        self.addCleanup(core.stop)
        login = ["--core", core.base_url, "--email", "admin-tenant@sgspe.test", "--password", "clave-del-tenant"]
        with tempfile.TemporaryDirectory() as folder:
            f4 = quiet(apply.main, ["--model", write_model(folder, phase_subset(load(MODEL_PATH), "F4")), *login])
        self.assertEqual(f4[0], 0, f4)
        self.assertIn("done: 333 created, 21 updated, 0 skipped", f4[1])
        core.requests.clear()
        with tempfile.TemporaryDirectory() as folder:
            f5 = quiet(apply.main, ["--model", write_model(folder, phase_subset(load(MODEL_PATH), "F5")), *login])
        self.assertEqual(f5[0], 0, f5)
        self.assertIn("done: 47 created, 5 updated, 333 skipped", f5[1])
        touched = {p.split("/")[3] for m, p, _, _ in core.requests if m == "PUT" and p.startswith("/api/objects/")}
        self.assertEqual(touched, {"social_contribution", "social_program", "program_budget", "program_measurement"})
        self.assertEqual([line for line in f5[1].splitlines() if line.startswith("add    field")], [])
        self.assertIn("update field delivery.social_contribution (unique)", f5[1])

    def test_a_tenant_of_f2_moves_to_f7_in_one_run_and_a_second_run_changes_nothing(self):
        # un tenant que quedó en F2 sube de una vez, sin pasar por F3 a F6
        core = FakeCore()
        self.addCleanup(core.stop)
        login = ["--core", core.base_url, "--email", "admin-tenant@sgspe.test", "--password", "clave-del-tenant"]
        with tempfile.TemporaryDirectory() as folder:
            f2 = quiet(apply.main, ["--model", write_model(folder, phase_subset(load(MODEL_PATH), "F2")), *login])
        self.assertIn("done: 169 created, 11 updated, 0 skipped", f2[1])
        f7 = quiet(apply.main, ["--model", MODEL_PATH, *login])
        self.assertEqual(f7[0], 0, f7)
        self.assertIn("done: 262 created, 15 updated, 165 skipped", f7[1])
        again = quiet(apply.main, ["--model", MODEL_PATH, *login])
        self.assertEqual(again[0], 0, again)
        self.assertIn("done: 0 created, 0 updated, 399 skipped", again[1])

    def test_a_tenant_of_f5_moves_to_f6_adding_only_its_fields_and_export(self):
        # Review Focus 1 del plan 6.1: el tenant demo sube de F5 a F6 con 4 campos en community y 4 en
        # environmental_feature y EXPORT en los 13 objetos de las capas territoriales; ningún objeto, relación, único de
        # varios campos ni índice nuevo, así que ningún PUT sobre un objeto (los PUT de «requerido» de las relaciones se
        # repiten en cada corrida y no cuentan)
        core = FakeCore()
        self.addCleanup(core.stop)
        login = ["--core", core.base_url, "--email", "admin-tenant@sgspe.test", "--password", "clave-del-tenant"]
        with tempfile.TemporaryDirectory() as folder:
            f5 = quiet(apply.main, ["--model", write_model(folder, phase_subset(load(MODEL_PATH), "F5")), *login])
        self.assertEqual(f5[0], 0, f5)
        self.assertIn("done: 380 created, 26 updated, 0 skipped", f5[1])
        core.requests.clear()
        with tempfile.TemporaryDirectory() as folder:
            f6_model = write_model(folder, phase_subset(load(MODEL_PATH), "F6"))
            f6 = quiet(apply.main, ["--model", f6_model, *login])
            again = quiet(apply.main, ["--model", f6_model, *login])
        self.assertEqual(f6[0], 0, f6)
        self.assertIn("done: 21 created, 0 updated, 378 skipped", f6[1])
        added = sorted(line.split()[2] for line in f6[1].splitlines() if line.startswith("add    field"))
        self.assertEqual(added, sorted(f"{o}.{f}" for o in ("community", "environmental_feature")
                                       for f in ("code", "source_crs", "source_reference", "import_batch_id")))
        declared = sorted(line.split()[2] for line in f6[1].splitlines() if line.startswith("create action"))
        exportable = ("administrative_unit", "locality", "road_access", "community", "community_sector",
                      "mining_concession", "environmental_feature", "project", "project_component", "area_of_influence",
                      "land_parcel", "school", "health_facility")
        self.assertEqual(declared, sorted(f"{o}.EXPORT" for o in exportable))
        self.assertEqual([p for m, p, _, _ in core.requests if m == "PUT" and p.startswith("/api/objects/")], [])
        # el código es único desde que llega: la columna nueva solo tiene NULL, que PostgreSQL no compara
        stored = {f["name"]: f for f in core.existing_fields["community"]}
        self.assertIs(stored["code"]["unique"], True)
        self.assertIn("done: 0 created, 0 updated, 393 skipped", again[1])

    def test_a_tenant_of_f6_moves_to_f7_adding_only_the_two_objects_and_their_relationships(self):
        # Review Focus 2 del plan 7.1: el tenant demo sube de F6 a F7 con ai_suggestion, ai_interaction y sus cuatro
        # relaciones (las dos al proyecto, la de la sugerencia a su interacción y la del candidato a su interacción);
        # ningún campo nuevo en un objeto anterior, ningún PUT sobre un objeto, y una segunda corrida no escribe
        core = FakeCore()
        self.addCleanup(core.stop)
        login = ["--core", core.base_url, "--email", "admin-tenant@sgspe.test", "--password", "clave-del-tenant"]
        with tempfile.TemporaryDirectory() as folder:
            f6 = quiet(apply.main, ["--model", write_model(folder, phase_subset(load(MODEL_PATH), "F6")), *login])
        self.assertEqual(f6[0], 0, f6)
        self.assertIn("done: 393 created, 26 updated, 0 skipped", f6[1])
        core.requests.clear()
        f7 = quiet(apply.main, ["--model", MODEL_PATH, *login])
        self.assertEqual(f7[0], 0, f7)
        self.assertIn("done: 6 created, 0 updated, 393 skipped", f7[1])
        written = [line for line in f7[1].splitlines() if line.startswith(("create", "add", "update"))]
        self.assertEqual(written, ["create object ai_suggestion (201)", "create object ai_interaction (201)",
                                   "create relationship candidate_ai_interaction (201)",
                                   "create relationship ai_suggestion_project (201)",
                                   "create relationship ai_suggestion_ai_interaction (201)",
                                   "create relationship ai_interaction_project (201)"])
        self.assertEqual([p for m, p, _, _ in core.requests if m == "PUT" and p.startswith("/api/objects/")], [])
        again = quiet(apply.main, ["--model", MODEL_PATH, *login])
        self.assertIn("done: 0 created, 0 updated, 399 skipped", again[1])


def phase_subset(model, phase):
    """model.json as it was in `phase`: the objects, fields, relationships and ENUM keys whose phase is `phase` or an
    earlier one by the cards."""
    import design_docs
    cards = design_docs.cards()
    objects = []
    for obj in model["objects"]:
        card = cards[obj["name"]]
        if card.phase > phase:
            continue
        phases = {f["name"]: design_docs.field_phase(card, f) for f in card.fields}
        trimmed = {**obj, "fields": [f for f in obj["fields"] if phases[f["name"]] <= phase]}
        # una acción con «(desde Fn)» en la ficha (EXPORT de F6) no estaba en las fases anteriores
        actions = [a for a in obj.get("actions", []) if design_docs.action_phase(card, a["name"]) <= phase]
        if actions:
            trimmed["actions"] = actions
        else:
            trimmed.pop("actions", None)
        objects.append(trimmed)
    names = {o["name"] for o in objects}
    relationships = [r for r in model["relationships"] if r["source"] in names and r["target"] in names
                     and design_docs.relationship_phase(cards, cards[r["source"]],
                                                        next(x for x in cards[r["source"]].relationships
                                                             if x["name"] == r["name"])) <= phase]
    used = {f["enum"] for o in objects for f in o["fields"] if f["type"] == "ENUM"}
    return {"enums": {k: v for k, v in model["enums"].items() if k in used}, "objects": objects,
            "relationships": relationships}


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


class FileMaxBytesTests(unittest.TestCase):
    """wasichai-files reads maxBytes from the field definition (FileFieldType.maxBytesOf): a whole number >= 1."""

    def mutate(self, **keys):
        return FileFieldTests.mutate(self, **keys)

    def test_max_bytes_goes_with_a_file_and_a_bad_one_is_refused(self):
        model = copy.deepcopy(FIXTURE_MODEL)
        model["objects"][1]["fields"].append({"name": "scan", "label": "Archivo", "type": "FILE", "maxBytes": 1048576})
        self.assertEqual(apply.validate(model), [])
        self.assertEqual(apply.field_payload(model, model["objects"][1]["fields"][-1])["maxBytes"], 1048576)
        for bad in (0, -1, "1", 1.5, True):
            self.assertTrue(any("maxBytes must be a whole number" in e for e in self.mutate(maxBytes=bad)), bad)
        model["objects"][1]["fields"][0]["maxBytes"] = 10
        self.assertTrue(any("only a FILE field takes maxBytes" in e for e in apply.validate(model)))
