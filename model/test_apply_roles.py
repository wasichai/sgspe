"""Pruebas de roles.json y de apply_roles.py contra un FakeCore (security.md §6.3 y §14, filas de F1).

Correr: cd model && python3 -m unittest -v
"""
import copy
import io
import json
import os
import tempfile
import unittest
from contextlib import redirect_stderr, redirect_stdout

import apply_roles
from fake_core import FakeCore
from fixture_model import FIXTURE_MODEL

HERE = os.path.dirname(os.path.abspath(__file__))
ROLES_PATH = os.path.join(HERE, "roles.json")
MODEL_PATH = os.path.join(HERE, "model.json")

# los 15 roles declarados, en el orden de security.md §6.1, con su etiqueta en español
LABELS = {
    "PROJECT_ADMIN": "Administrador de proyectos",
    "PROJECT_MANAGER": "Jefe de proyecto",
    "SOCIAL_MANAGER": "Gerente social",
    "COMMUNITY_RELATIONS_OFFICER": "Relacionista comunitario",
    "STAKEHOLDER_MANAGER": "Gestor de grupos de interés",
    "COMMITMENT_MANAGER": "Gestor de compromisos",
    "GRIEVANCE_MANAGER": "Gestor de quejas, consultas y solicitudes",
    "SOCIAL_RISK_MANAGER": "Gestor de riesgos sociales",
    "SOCIAL_INVESTMENT_MANAGER": "Gestor de inversión social",
    "GIS_ANALYST": "Analista SIG",
    "FIELD_AGENT": "Agente de campo",
    "APPROVER": "Aprobador",
    "AUDITOR": "Auditor",
    "ANALYST": "Analista",
    "READ_ONLY": "Solo lectura",
}
ADMIN_ACTIONS = {"MANAGE_METADATA", "MANAGE_ORGANIZATION"}


def load(path):
    with open(path, encoding="utf-8") as f:
        return json.load(f)


def grants(role):
    """Los permisos de un rol de roles.json como pares (objeto, acción)."""
    return {(obj, action) for obj, actions in role["permisos"].items() for action in actions}


class RolesJsonTests(unittest.TestCase):
    def setUp(self):
        self.file = load(ROLES_PATH)
        self.roles = {r["name"]: r for r in self.file["roles"]}

    def test_declara_los_15_roles_en_orden_con_su_etiqueta_en_espanol(self):
        self.assertEqual([(r["name"], r["label"]) for r in self.file["roles"]], list(LABELS.items()))

    def test_dos_roles_son_el_admin_de_wasichai_y_suman_los_17_del_prompt(self):
        self.assertEqual(self.file["mappedToAdmin"], ["SUPER_ADMIN", "ORGANIZATION_ADMIN"])
        self.assertEqual(len(set(self.roles) | set(self.file["mappedToAdmin"])), 17)
        self.assertTrue(set(self.roles).isdisjoint({"ADMIN", "SUPER_ADMIN", "ORGANIZATION_ADMIN"}))

    def test_solo_auditor_tiene_un_permiso_de_todo_el_tenant_y_es_de_lectura(self):
        tenant = {name for name, role in self.roles.items() if "*" in role["permisos"]}
        self.assertEqual(tenant, {"AUDITOR"})
        self.assertEqual(self.roles["AUDITOR"]["permisos"], {"*": ["READ"]})

    def test_en_f1_los_demas_roles_existen_sin_permisos(self):
        for name, role in self.roles.items():
            if name != "AUDITOR":
                self.assertEqual(role["permisos"], {}, name)

    def test_ningun_rol_lleva_own_records_only(self):
        for name, role in self.roles.items():
            self.assertNotIn("ownRecordsOnly", role, name)

    def test_field_agent_no_tiene_edit_any(self):
        self.assertNotIn("EDIT_ANY", {action for _, action in grants(self.roles["FIELD_AGENT"])})

    def test_ningun_rol_tiene_permisos_de_administracion(self):
        for name, role in self.roles.items():
            self.assertTrue(ADMIN_ACTIONS.isdisjoint({action for _, action in grants(role)}), name)

    def test_valida_contra_el_modelo(self):
        self.assertEqual(apply_roles.validate(self.file, load(MODEL_PATH)), [])

    def test_ningun_derivado_del_modelo_usa_editable(self):
        # vacía en F1; desde F2 vigila que un derivado se marque con "derived": true y nunca con editable: false
        for obj in load(MODEL_PATH)["objects"]:
            for field in obj["fields"]:
                self.assertNotIn("editable", field, f"{obj['name']}.{field['name']}")


class ValidationTests(unittest.TestCase):
    def setUp(self):
        self.file = load(ROLES_PATH)

    def mutate(self, fn, model=None):
        roles = copy.deepcopy(self.file)
        fn(roles)
        return apply_roles.validate(roles, model or load(MODEL_PATH))

    @staticmethod
    def role(name):
        return {"name": name, "label": "Rol de prueba", "permisos": {}}

    def test_declarar_el_admin_o_sus_dos_roles_se_rechaza(self):
        for name in ("ADMIN", "SUPER_ADMIN", "ORGANIZATION_ADMIN"):
            errors = self.mutate(lambda r, n=name: r["roles"].append(self.role(n)))
            self.assertTrue(any(f"role {name}: is wasichai's ADMIN" in e for e in errors), errors)

    def test_mapped_to_admin_debe_ser_exactamente_los_dos(self):
        errors = self.mutate(lambda r: r.update({"mappedToAdmin": ["SUPER_ADMIN"]}))
        self.assertTrue(any(e.startswith("mappedToAdmin must be") for e in errors), errors)

    def test_own_records_only_se_rechaza(self):
        errors = self.mutate(lambda r: r["roles"][10].update({"ownRecordsOnly": True}))
        self.assertTrue(any("FIELD_AGENT: ownRecordsOnly is not used" in e for e in errors), errors)

    def test_el_permiso_de_todo_el_tenant_solo_admite_lectura(self):
        errors = self.mutate(lambda r: r["roles"][12]["permisos"].update({"*": ["READ", "UPDATE"]}))
        self.assertTrue(any("AUDITOR: '*' only takes" in e for e in errors), errors)

    def test_una_accion_de_administracion_se_rechaza(self):
        errors = self.mutate(lambda r: r["roles"][0]["permisos"].update({"zone": ["MANAGE_METADATA"]}), FIXTURE_MODEL)
        self.assertTrue(any("MANAGE_METADATA" in e for e in errors), errors)

    def test_un_objeto_que_el_modelo_no_tiene_se_rechaza(self):
        errors = self.mutate(lambda r: r["roles"][0]["permisos"].update({"project": ["READ"]}))
        self.assertTrue(any("object 'project' is not in model.json" in e for e in errors), errors)

    def test_una_accion_declarada_solo_vale_sobre_su_objeto(self):
        ok = self.mutate(lambda r: r["roles"][0]["permisos"].update({"visit": ["READ", "EDIT_ANY"]}), FIXTURE_MODEL)
        self.assertEqual(ok, [])
        errors = self.mutate(lambda r: r["roles"][0]["permisos"].update({"zone": ["EDIT_ANY"]}), FIXTURE_MODEL)
        self.assertTrue(any("action 'EDIT_ANY' on zone" in e for e in errors), errors)

    def test_u_y_update_juntos_se_rechazan(self):
        errors = self.mutate(lambda r: r["roles"][0]["permisos"].update({"zone": ["READ", "u", "UPDATE"]}), FIXTURE_MODEL)
        self.assertTrue(any("'u' and UPDATE on zone" in e for e in errors), errors)

    def test_un_nombre_que_wasichai_no_acepta_y_un_rol_repetido_se_rechazan(self):
        errors = self.mutate(lambda r: r["roles"].append(self.role("prueba")))
        self.assertTrue(any("role 'prueba': name must match" in e for e in errors), errors)
        errors = self.mutate(lambda r: r["roles"].append(copy.deepcopy(r["roles"][0])))
        self.assertTrue(any("duplicate role name" in e for e in errors), errors)


class ExpansionTests(unittest.TestCase):
    ROLE = {"name": "PRUEBA", "label": "Rol de prueba", "permisos": {"visit": ["READ", "u"], "zone": ["READ", "u"]}}

    def test_u_es_update_en_los_permisos(self):
        self.assertEqual(apply_roles.permissions_payload(self.ROLE), {"permissions": [
            {"objectName": "visit", "action": "READ"}, {"objectName": "visit", "action": "UPDATE"},
            {"objectName": "zone", "action": "READ"}, {"objectName": "zone", "action": "UPDATE"}]})

    def test_u_deja_de_solo_lectura_cada_campo_no_derivado_y_el_de_cada_relacion(self):
        # area_ha es derivado: queda escribible para los listeners y los endpoints
        self.assertEqual(apply_roles.field_permissions_payload(self.ROLE, FIXTURE_MODEL), {"fields": [
            {"objectName": "visit", "fieldName": "visit_date", "read": True, "write": False},
            {"objectName": "visit", "fieldName": "zone", "read": True, "write": False},
            {"objectName": "zone", "fieldName": "code", "read": True, "write": False},
            {"objectName": "zone", "fieldName": "zone_type", "read": True, "write": False}]})

    def test_el_permiso_de_todo_el_tenant_va_sin_objeto(self):
        auditor = {"name": "AUDITOR", "label": "Auditor", "permisos": {"*": ["READ"]}}
        self.assertEqual(apply_roles.permissions_payload(auditor), {"permissions": [{"objectName": None, "action": "READ"}]})
        self.assertEqual(apply_roles.field_permissions_payload(auditor, FIXTURE_MODEL), {"fields": []})


class ApplyRolesTestCase(unittest.TestCase):
    def setUp(self):
        self.core = FakeCore()
        self.addCleanup(self.core.stop)

    def run_cli(self, extra=(), roles_path=ROLES_PATH, model_path=MODEL_PATH):
        args = ["--roles", roles_path, "--model", model_path, "--core", self.core.base_url,
                "--email", "admin@wasichai.local", "--password", "admin", *extra]
        out, err = io.StringIO(), io.StringIO()
        with redirect_stdout(out), redirect_stderr(err):
            code = apply_roles.main(args)
        return code, out.getvalue(), err.getvalue()

    def writes(self):
        return [(m, p, b) for m, p, _, b in self.core.requests if m in ("POST", "PUT") and p != "/api/auth/login"]


class ApplyRolesTests(ApplyRolesTestCase):
    def test_dry_run_no_llama_a_core_e_imprime_los_15_roles(self):
        code, out, err = self.run_cli(["--dry-run"])
        self.assertEqual(code, 0, err)
        self.assertEqual(self.core.requests, [])
        self.assertEqual(out.count("# POST /api/roles\n"), 15)

    def test_crea_los_15_roles_y_auditor_lee_todo_el_tenant(self):
        code, out, err = self.run_cli()
        self.assertEqual(code, 0, err)
        self.assertEqual([b["name"] for m, p, b in self.writes() if m == "POST"], list(LABELS))
        puts = {p: b for m, p, b in self.writes() if m == "PUT"}
        self.assertEqual(puts["/api/roles/AUDITOR/permissions"], {"permissions": [{"objectName": None, "action": "READ"}]})
        self.assertEqual(puts["/api/roles/FIELD_AGENT/permissions"], {"permissions": []})
        self.assertFalse(any(p.endswith("/field-permissions") for p in puts), puts)
        for method, path, auth, body in self.core.requests:
            self.assertEqual(auth, None if path == "/api/auth/login" else "Bearer t")
        self.assertIn("done: 15 created, 0 updated, 0 skipped", out)

    def test_la_segunda_corrida_no_escribe_nada(self):
        self.run_cli()
        self.core.requests.clear()
        code, out, err = self.run_cli()
        self.assertEqual(code, 0, err)
        self.assertEqual(self.writes(), [])
        self.assertIn("done: 0 created, 0 updated, 15 skipped", out)

    def test_deshace_lo_editado_a_mano_en_el_admin(self):
        # Review Focus 3: alguien activó ownRecordsOnly, cambió la etiqueta y dio un permiso a mano en /admin
        self.run_cli()
        self.core.roles["FIELD_AGENT"].update({"label": "Otro", "ownRecordsOnly": True,
                                               "permissions": [{"objectName": None, "action": "READ", "allowed": True}]})
        self.core.requests.clear()
        code, out, err = self.run_cli()
        self.assertEqual(code, 0, err)
        self.assertEqual(self.writes(), [
            ("PUT", "/api/roles/FIELD_AGENT", {"label": "Agente de campo", "ownRecordsOnly": False}),
            ("PUT", "/api/roles/FIELD_AGENT/permissions", {"permissions": []})])
        self.assertIn("done: 0 created, 1 updated, 14 skipped", out)
        self.core.requests.clear()
        self.assertIn("done: 0 created, 0 updated, 15 skipped", self.run_cli()[1])

    def test_sincroniza_los_permisos_de_campo_de_u(self):
        roles = {"mappedToAdmin": ["SUPER_ADMIN", "ORGANIZATION_ADMIN"],
                 "roles": [{"name": "PRUEBA", "label": "Rol de prueba", "permisos": {"zone": ["READ", "u"]}}]}
        with tempfile.TemporaryDirectory() as folder:
            roles_path = os.path.join(folder, "roles.json")
            model_path = os.path.join(folder, "model.json")
            for path, data in ((roles_path, roles), (model_path, FIXTURE_MODEL)):
                with open(path, "w", encoding="utf-8") as f:
                    json.dump(data, f, ensure_ascii=False)
            code, out, err = self.run_cli(roles_path=roles_path, model_path=model_path)
            self.assertEqual(code, 0, err)
            self.assertIn(("PUT", "/api/roles/PRUEBA/field-permissions", {"fields": [
                {"objectName": "zone", "fieldName": "code", "read": True, "write": False},
                {"objectName": "zone", "fieldName": "zone_type", "read": True, "write": False}]}), self.writes())
            self.core.requests.clear()
            code, out, err = self.run_cli(roles_path=roles_path, model_path=model_path)
        self.assertEqual(code, 0, err)
        self.assertEqual(self.writes(), [])
        self.assertIn("done: 0 created, 0 updated, 1 skipped", out)

    def test_un_rechazo_de_core_detiene_la_corrida(self):
        self.core.fail_on_role = "SOCIAL_MANAGER"
        code, out, err = self.run_cli()
        self.assertEqual(code, 1)
        self.assertIn("error POST /api/roles", err)
        self.assertEqual([b["name"] for m, p, b in self.writes() if m == "POST"],
                         ["PROJECT_ADMIN", "PROJECT_MANAGER", "SOCIAL_MANAGER"])

    def test_un_roles_json_invalido_no_llama_a_core(self):
        roles = load(ROLES_PATH)
        roles["roles"][0]["permisos"]["project"] = ["READ"]
        with tempfile.TemporaryDirectory() as folder:
            path = os.path.join(folder, "roles.json")
            with open(path, "w", encoding="utf-8") as f:
                json.dump(roles, f)
            code, out, err = self.run_cli(roles_path=path)
        self.assertEqual(code, 2)
        self.assertIn("project", out)
        self.assertEqual(self.core.requests, [])

    def test_un_archivo_que_falta_o_que_no_es_json_sale_con_2_sin_traza(self):
        with tempfile.TemporaryDirectory() as folder:
            broken = os.path.join(folder, "roto.json")
            with open(broken, "w", encoding="utf-8") as f:
                f.write("{ no es json")
            missing = os.path.join(folder, "no-existe.json")
            for extra, name in (([], missing), ([], broken), (["--model", missing], missing), (["--model", broken], broken)):
                roles = name if not extra else ROLES_PATH
                code, out, err = self.run_cli(extra, roles_path=roles)
                self.assertEqual(code, 2, (extra, name, out, err))
                self.assertIn(name, err)
                self.assertNotIn("Traceback", err)
        self.assertEqual(self.core.requests, [])


if __name__ == "__main__":
    unittest.main()
