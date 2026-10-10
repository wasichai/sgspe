"""Pruebas de roles.json y de apply_roles.py contra un FakeCore (security.md §6.3 y §14, filas de F1 a F4).

Desde F2, roles.json se contrasta con la matriz de security.md §7.2 a §7.8 y con los campos restringidos de §7.9, que
lee design_docs.py.

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
import design_docs
from fake_core import FakeCore
from fixture_model import F2_FIXTURE_MODEL, FIXTURE_MODEL

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
        # "*" READ no concede acciones declaradas: la de leer todos los proyectos va aparte (ASM-106)
        self.assertEqual(self.roles["AUDITOR"]["permisos"], {"*": ["READ"], "project": ["READ_ALL_PROJECTS"]})

    def test_leen_todos_los_proyectos_solo_auditor_y_project_admin(self):
        # decisión 2 de la especificación 4.4 (ASM-106), confirmada por el usuario el 2026-10-10: SOCIAL_MANAGER, ANALYST,
        # GIS_ANALYST y READ_ONLY leen solo sus proyectos. sumar un rol es agregarle la acción aquí, sin tocar el código
        holders = {(name, obj) for name, role in self.roles.items() for obj, grants in role["permisos"].items()
                   if "READ_ALL_PROJECTS" in grants}
        self.assertEqual(holders, {("AUDITOR", "project"), ("PROJECT_ADMIN", "project")})

    def test_quien_crea_proyectos_lee_todos_los_proyectos(self):
        # el alta de un proyecto todavía no tiene equipo: sin READ_ALL_PROJECTS, wasichai respondería el alta con un 404
        # porque ProjectReadScope no le dejaría leer lo que acaba de crear (security.md §9.7)
        creators = {name for name, role in self.roles.items() if "CREATE" in role["permisos"].get("project", [])}
        self.assertTrue(creators)
        for name in creators:
            self.assertIn("READ_ALL_PROJECTS", self.roles[name]["permisos"]["project"], name)

    def test_los_permisos_son_los_de_la_matriz_de_security_para_los_objetos_del_modelo(self):
        # security.md §7.1: un rol recibe un permiso desde la fase más tardía entre la suya y la del objeto
        phases, _ = design_docs.role_phases()
        cards = design_docs.cards()
        matrix = design_docs.matrix()
        phase = max(cards[o["name"]].phase for o in load(MODEL_PATH)["objects"])
        for name, role in self.roles.items():
            if name == "AUDITOR":
                continue
            wanted = {obj: matrix[obj][name] for obj in (o["name"] for o in load(MODEL_PATH)["objects"])
                      if max(phases[name], cards[obj].phase) <= phase and matrix[obj][name]}
            self.assertEqual(role["permisos"], wanted, name)

    def test_los_roles_de_una_fase_posterior_siguen_sin_permisos(self):
        # la fase del modelo es la de su objeto más tardío: F4 desde el plan 4.1, así que solo SOCIAL_INVESTMENT_MANAGER
        # (F5) sigue con {}
        phases, _ = design_docs.role_phases()
        cards = design_docs.cards()
        phase = max(cards[o["name"]].phase for o in load(MODEL_PATH)["objects"])
        self.assertEqual(phase, "F4")
        later = sorted(name for name in self.roles if name != "AUDITOR" and phases[name] > phase)
        self.assertEqual(later, ["SOCIAL_INVESTMENT_MANAGER"])
        for name in later:
            self.assertEqual(self.roles[name]["permisos"], {}, name)

    def test_los_campos_restringidos_son_los_de_security_7_9_para_los_objetos_del_modelo(self):
        objects = {o["name"] for o in load(MODEL_PATH)["objects"]}
        declared = set(self.roles)
        wanted = {(obj, field, tuple(r for r in readers if r in declared))
                  for obj, field, readers in design_docs.restricted_fields() if obj in objects}
        got = {(e["object"], field, tuple(e["readers"])) for e in self.file["restrictedFields"] for field in e["fields"]}
        self.assertEqual(got, wanted)

    def test_todo_campo_restringido_de_las_fichas_esta_en_restricted_fields(self):
        cards = design_docs.cards()
        listed = {(e["object"], f) for e in self.file["restrictedFields"] for f in e["fields"]}
        for obj in load(MODEL_PATH)["objects"]:
            card = cards[obj["name"]]
            marked = {f["name"] for f in card.fields if f["restricted"]}
            marked |= {r["field"] for r in card.relationships if r["restricted"] and not r["inverse"]}
            for field in marked:
                self.assertIn((obj["name"], field), listed, f"{obj['name']}.{field}")

    def test_quien_conserva_una_relacion_restringida_a_person_lee_sus_nombres(self):
        # security.md §7.9: quien vincula a una persona la identifica por su nombre
        model = load(MODEL_PATH)
        targets = {(r["source"], r["fieldName"]): r["target"] for r in model["relationships"] if r.get("fieldName")}
        names = next(e["readers"] for e in self.file["restrictedFields"]
                     if e["object"] == "person" and "given_names" in e["fields"])
        for entry in self.file["restrictedFields"]:
            for field in entry["fields"]:
                if targets.get((entry["object"], field)) == "person":
                    self.assertTrue(set(entry["readers"]) <= set(names), (entry, names))

    def test_los_roles_de_cada_transicion_tienen_update_y_leen_las_asignaciones(self):
        # workflows.md, «Mecánica de wasichai» 4, y security.md §5.10: el aviso resuelve destinatarios con project_assignment
        for path in sorted(os.listdir(os.path.join(HERE, "workflows"))):
            obj = path[:-len(".json")]
            workflow = load(os.path.join(HERE, "workflows", path))
            for transition in workflow["definition"]["transitions"]:
                for role in transition["roles"]:
                    grants = self.roles[role]["permisos"]
                    self.assertTrue({"UPDATE", "u"} & set(grants.get(obj, [])), (obj, transition["name"], role))
                    self.assertIn("READ", grants.get("project_assignment", []), role)

    def test_los_roles_de_cada_aprobacion_son_exactamente_los_que_tienen_approve(self):
        # security.md §5.3, «Doble llave en las decisiones»: en WF-D, approve (APPROVER) y approve_senior
        # (PROJECT_MANAGER) son las dos aprobaciones, y verify la verificación (D3, D11); en WF-E1, resolve_high_severity
        # resuelve las quejas graves (RN-GRV-002), y en WF-J confirm y dismiss deciden la alerta (RN-EWS-001)
        for obj, transitions, action in (("project", ("approve",), "APPROVE"), ("area_of_influence", ("approve",), "APPROVE"),
                                         ("area_of_influence", ("supersede",), "APPROVE"),
                                         ("commitment", ("approve", "approve_senior"), "APPROVE"),
                                         ("commitment", ("verify",), "VERIFY"),
                                         ("grievance", ("resolve_high_severity",), "APPROVE"),
                                         ("early_warning", ("confirm", "dismiss"), "CONFIRM_ALERT")):
            workflow = load(os.path.join(HERE, "workflows", f"{obj}.json"))
            roles = {r for t in workflow["definition"]["transitions"] if t["name"] in transitions for r in t["roles"]}
            holders = {name for name, role in self.roles.items() if action in role["permisos"].get(obj, [])}
            self.assertEqual(roles, holders, (obj, transitions))

    def test_solo_grievance_manager_lee_las_qcs_confidenciales(self):
        # decisión 2 de F4 (ASM-095): READ_CONFIDENTIAL es una acción declarada de las tres QCS, que en roles.json solo
        # tiene GRIEVANCE_MANAGER; quitarla aquí retira la capacidad sin tocar el código (ConfidentialAttentionScope)
        holders = {(name, obj) for name, role in self.roles.items() for obj, grants in role["permisos"].items()
                   if "READ_CONFIDENTIAL" in grants}
        self.assertEqual(holders, {("GRIEVANCE_MANAGER", "grievance"), ("GRIEVANCE_MANAGER", "inquiry"),
                                   ("GRIEVANCE_MANAGER", "stakeholder_request")})

    def test_quien_cierra_un_compromiso_cierra_su_solicitud(self):
        # workflows.md, WF-E3: RequestClosureListener aplica close_accepted como quien aplica close de WF-D, que necesita
        # su rol en close_accepted y READ y UPDATE sobre stakeholder_request; si no, el cierre del compromiso se revierte
        commitment = load(os.path.join(HERE, "workflows", "commitment.json"))
        request = load(os.path.join(HERE, "workflows", "stakeholder_request.json"))
        closers = next(set(t["roles"]) for t in commitment["definition"]["transitions"] if t["name"] == "close")
        accepted = next(set(t["roles"]) for t in request["definition"]["transitions"] if t["name"] == "close_accepted")
        self.assertLessEqual(closers, accepted)
        for name in closers:
            self.assertLessEqual({"READ", "UPDATE"}, set(self.roles[name]["permisos"]["stakeholder_request"]), name)

    def test_los_roles_de_cada_comando_de_f4_crean_lo_que_el_comando_escribe(self):
        # security.md §8.2: un endpoint de comando exige los permisos de todo lo que escribe. accept de WF-E3 crea el
        # compromiso en draft; las reclasificaciones crean la QCS del otro tipo; assess de WF-F crea la revisión
        writes = {("stakeholder_request", "accept"): ["commitment"],
                  ("grievance", "close_reclassified_received"): ["inquiry", "stakeholder_request"],
                  ("inquiry", "close_reclassified"): ["grievance", "stakeholder_request"],
                  ("stakeholder_request", "close_reclassified_received"): ["grievance", "inquiry"],
                  ("social_risk", "assess"): ["risk_assessment"]}
        for (obj, name), created in writes.items():
            workflow = load(os.path.join(HERE, "workflows", f"{obj}.json"))
            roles = next(t["roles"] for t in workflow["definition"]["transitions"] if t["name"] == name)
            for role in roles:
                for target in created:
                    self.assertIn("CREATE", self.roles[role]["permisos"].get(target, []), (obj, name, role, target))

    def test_quien_supera_el_tope_tiene_la_aprobacion_superior(self):
        # workflows.md, WF-D (D7): APPROVE_OVER_CAP solo sirve con una transición de aprobación; en commitment la tiene
        # PROJECT_MANAGER, el rol de approve_senior, y ningún rol de approve ni de approve_direct
        workflow = load(os.path.join(HERE, "workflows", "commitment.json"))
        roles = {t["name"]: set(t["roles"]) for t in workflow["definition"]["transitions"]}
        holders = {name for name, role in self.roles.items() if "APPROVE_OVER_CAP" in role["permisos"].get("commitment", [])}
        self.assertEqual(holders, roles["approve_senior"])
        self.assertEqual(holders & (roles["approve"] | roles["approve_direct"]), set())

    def test_quien_crea_un_objeto_con_exactamente_uno_de_lee_algun_destino_de_cada_grupo(self):
        # ExactlyOneGuard pide un destino de cada grupo y wasichai responde 400 a un valor RELATION que quien escribe
        # no lee (security.md §5.6): quien crea debe leer al menos uno de los que existen en la fase
        model = load(MODEL_PATH)
        cards = design_docs.cards()
        targets = {(r["source"], r["fieldName"]): r["target"] for r in model["relationships"] if r.get("fieldName")}
        for name, role in self.roles.items():
            for obj in (o["name"] for o in model["objects"]):
                if "CREATE" not in role["permisos"].get(obj, []):
                    continue
                for group in cards[obj].exactly_one:
                    present = [targets[(obj, f)] for f in group if (obj, f) in targets]
                    readable = [t for t in present if "READ" in role["permisos"].get(t, [])]
                    self.assertTrue(readable, (name, obj, group))

    def test_quien_crea_un_objeto_lee_los_destinos_de_sus_relaciones_requeridas(self):
        # wasichai 0.3.2 responde 400 a un valor RELATION que quien escribe no puede leer (security.md §5.6)
        model = load(MODEL_PATH)
        required = [(r["source"], r["target"]) for r in model["relationships"] if r.get("required")]
        for name, role in self.roles.items():
            for source, target in required:
                if "CREATE" in role["permisos"].get(source, []):
                    self.assertIn("READ", role["permisos"].get(target, []), (name, source, target))

    def test_quien_crea_un_objeto_puede_escribir_sus_campos_requeridos(self):
        # wasichai 0.3.2 rechaza el alta si un campo requerido no es escribible para quien crea
        model = load(MODEL_PATH)
        required = {o["name"]: {f["name"] for f in o["fields"] if f.get("required")} for o in model["objects"]}
        for name, role in self.roles.items():
            denied = {(e["object"], f) for e in self.file["restrictedFields"] if name not in e["readers"]
                      for f in e["fields"]}
            for obj, grants in role["permisos"].items():
                if "CREATE" in grants:
                    for field in required.get(obj, ()):
                        self.assertNotIn((obj, field), denied, (name, obj, field))

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
        errors = self.mutate(lambda r: r["roles"][0]["permisos"].update({"social_contribution": ["READ"]}))
        self.assertTrue(any("object 'social_contribution' is not in model.json" in e for e in errors), errors)

    def test_una_accion_declarada_solo_vale_sobre_su_objeto(self):
        def only(permisos):
            return {"mappedToAdmin": MAPPED, "roles": [{"name": "PRUEBA", "label": "x", "permisos": permisos}]}
        self.assertEqual(apply_roles.validate(only({"visit": ["READ", "EDIT_ANY"]}), FIXTURE_MODEL), [])
        errors = apply_roles.validate(only({"zone": ["EDIT_ANY"]}), FIXTURE_MODEL)
        self.assertTrue(any("action 'EDIT_ANY' on zone" in e for e in errors), errors)

    def test_restricted_fields_nombra_objetos_campos_y_lectores_que_existen(self):
        errors = self.mutate(lambda r: r["restrictedFields"].extend([
            {"object": "social_contribution", "fields": ["description"], "readers": []},
            {"object": "person", "fields": ["nickname"], "readers": ["SOCIAL_MANAGER"]},
            {"object": "topic", "fields": ["code"], "readers": ["INTRUSO"]},
            {"object": "stakeholder", "fields": ["stance"], "readers": ["SOCIAL_MANAGER"]}]))
        self.assertTrue(any("object 'social_contribution' is not in model.json" in e for e in errors), errors)
        self.assertTrue(any("'nickname' is not a field of person" in e for e in errors), errors)
        self.assertTrue(any("reader 'INTRUSO' of topic is not a declared role" in e for e in errors), errors)
        self.assertTrue(any("stakeholder.stance is listed twice" in e for e in errors), errors)

    def test_u_y_update_juntos_se_rechazan(self):
        errors = self.mutate(lambda r: r["roles"][0]["permisos"].update({"zone": ["READ", "u", "UPDATE"]}), FIXTURE_MODEL)
        self.assertTrue(any("'u' and UPDATE on zone" in e for e in errors), errors)

    def test_un_nombre_que_wasichai_no_acepta_y_un_rol_repetido_se_rechazan(self):
        errors = self.mutate(lambda r: r["roles"].append(self.role("prueba")))
        self.assertTrue(any("role 'prueba': name must match" in e for e in errors), errors)
        errors = self.mutate(lambda r: r["roles"].append(copy.deepcopy(r["roles"][0])))
        self.assertTrue(any("duplicate role name" in e for e in errors), errors)


class StructureTests(unittest.TestCase):
    """Un roles.json mal formado sale con 2 y un mensaje, nunca con un KeyError (el docstring de apply_roles.py)."""

    def check(self, roles):
        return apply_roles.validate(roles, load(MODEL_PATH))

    def test_roles_es_obligatorio_y_es_una_lista(self):
        for roles in ({"mappedToAdmin": MAPPED}, {"mappedToAdmin": MAPPED, "roles": {}}, []):
            errors = self.check(roles)
            self.assertTrue(errors, roles)
        self.assertIn("roles must be a list", self.check({"mappedToAdmin": MAPPED}))

    def test_cada_rol_lleva_permisos_aunque_sea_vacio(self):
        errors = self.check({"mappedToAdmin": MAPPED, "roles": [{"name": "PRUEBA", "label": "Rol de prueba"}]})
        self.assertTrue(any("role PRUEBA: permisos must be an object" in e for e in errors), errors)
        self.assertEqual(self.check({"mappedToAdmin": MAPPED, "roles": [{"name": "PRUEBA", "label": "x", "permisos": {}}]}), [])

    def test_cada_valor_de_permisos_es_una_lista_de_acciones(self):
        for bad in ("READ", None, {"READ": True}, [1]):
            errors = self.check({"mappedToAdmin": MAPPED,
                                 "roles": [{"name": "PRUEBA", "label": "x", "permisos": {"*": bad}}]})
            self.assertTrue(any("permisos of '*' must be a list of action names" in e for e in errors), (bad, errors))

    def test_un_rol_que_no_es_un_objeto_se_rechaza(self):
        errors = self.check({"mappedToAdmin": MAPPED, "roles": ["PRUEBA"]})
        self.assertTrue(any("must be an object" in e for e in errors), errors)

    def test_un_nombre_que_no_es_texto_se_rechaza_sin_traza(self):
        for name in (["PRUEBA"], {"a": 1}, 7, None):
            errors = self.check({"mappedToAdmin": MAPPED, "roles": [{"name": name, "label": "x", "permisos": {}}]})
            self.assertTrue(any("name must match" in e for e in errors), (name, errors))


MAPPED = ["SUPER_ADMIN", "ORGANIZATION_ADMIN"]


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

    def test_una_relacion_many_to_many_no_tiene_campo_que_restringir(self):
        # visit_zones es una tabla de unión: solo visit_date y el campo de visit_zone quedan de solo lectura
        role = {"name": "PRUEBA", "label": "Rol de prueba", "permisos": {"visit": ["READ", "u"]}}
        fields = apply_roles.field_permissions_payload(role, F2_FIXTURE_MODEL)["fields"]
        self.assertEqual([f["fieldName"] for f in fields], ["visit_date", "zone"])

    def test_el_permiso_de_todo_el_tenant_va_sin_objeto(self):
        auditor = {"name": "AUDITOR", "label": "Auditor", "permisos": {"*": ["READ"]}}
        self.assertEqual(apply_roles.permissions_payload(auditor), {"permissions": [{"objectName": None, "action": "READ"}]})
        self.assertEqual(apply_roles.field_permissions_payload(auditor, FIXTURE_MODEL), {"fields": []})


class RestrictedExpansionTests(unittest.TestCase):
    """security.md §7.9: un campo restringido lleva, para todo rol que no lo lee, una entrada sin lectura ni escritura."""

    def setUp(self):
        self.file = load(ROLES_PATH)
        self.model = load(MODEL_PATH)
        self.roles = {r["name"]: r for r in self.file["roles"]}

    def entries(self, name):
        payload = apply_roles.field_permissions_payload(self.roles[name], self.model, self.file["restrictedFields"])
        return {(f["objectName"], f["fieldName"]): (f["read"], f["write"]) for f in payload["fields"]}

    def test_field_agent_no_ve_la_postura_y_lo_demas_de_stakeholder_es_de_solo_lectura(self):
        entries = self.entries("FIELD_AGENT")
        self.assertEqual(entries[("stakeholder", "stance")], (False, False))
        self.assertEqual(entries[("stakeholder", "stakeholder_type")], (True, False))
        self.assertEqual(entries[("stakeholder", "project")], (True, False))
        self.assertNotIn(("stakeholder", "influence_group"), entries)
        # captura a los asistentes: lee y escribe sus datos (security.md §7.9)
        self.assertNotIn(("engagement_participant", "participant_name"), entries)
        self.assertEqual(entries[("person", "id_document_number")], (False, False))

    def test_auditor_no_ve_ningun_campo_restringido(self):
        entries = self.entries("AUDITOR")
        for entry in self.file["restrictedFields"]:
            for field in entry["fields"]:
                self.assertEqual(entries[(entry["object"], field)], (False, False), (entry["object"], field))

    def test_un_lector_no_recibe_entrada_del_campo_que_lee(self):
        entries = self.entries("STAKEHOLDER_MANAGER")
        self.assertNotIn(("stakeholder", "stance"), entries)
        self.assertNotIn(("person", "given_names"), entries)
        self.assertEqual(entries[("land_parcel", "registry_record")], (False, False))

    def test_cada_campo_va_una_sola_vez(self):
        for name, role in self.roles.items():
            payload = apply_roles.field_permissions_payload(role, self.model, self.file["restrictedFields"])
            keys = [(f["objectName"], f["fieldName"]) for f in payload["fields"]]
            self.assertEqual(len(keys), len(set(keys)), name)


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
        self.assertEqual(puts["/api/roles/AUDITOR/permissions"],
                         {"permissions": [{"objectName": None, "action": "READ"},
                                          {"objectName": "project", "action": "READ_ALL_PROJECTS"}]})
        field_agent = next(r for r in load(ROLES_PATH)["roles"] if r["name"] == "FIELD_AGENT")
        self.assertEqual(puts["/api/roles/FIELD_AGENT/permissions"], apply_roles.permissions_payload(field_agent))
        self.assertIn({"objectName": "stakeholder", "fieldName": "stance", "read": False, "write": False},
                      puts["/api/roles/AUDITOR/field-permissions"]["fields"])
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
        field_agent = next(r for r in load(ROLES_PATH)["roles"] if r["name"] == "FIELD_AGENT")
        self.assertEqual(self.writes(), [
            ("PUT", "/api/roles/FIELD_AGENT", {"label": "Agente de campo", "ownRecordsOnly": False}),
            ("PUT", "/api/roles/FIELD_AGENT/permissions", apply_roles.permissions_payload(field_agent))])
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
        roles["roles"][0]["permisos"]["social_contribution"] = ["READ"]
        with tempfile.TemporaryDirectory() as folder:
            path = os.path.join(folder, "roles.json")
            with open(path, "w", encoding="utf-8") as f:
                json.dump(roles, f)
            code, out, err = self.run_cli(roles_path=path)
        self.assertEqual(code, 2)
        self.assertIn("social_contribution", out)
        self.assertEqual(self.core.requests, [])

    def test_un_roles_json_mal_formado_sale_con_2_y_sin_traza(self):
        malformed = {"mappedToAdmin": MAPPED, "roles": [{"name": "PRUEBA", "label": "Rol de prueba"}]}
        with tempfile.TemporaryDirectory() as folder:
            path = os.path.join(folder, "roles.json")
            with open(path, "w", encoding="utf-8") as f:
                json.dump(malformed, f)
            code, out, err = self.run_cli(roles_path=path)
        self.assertEqual(code, 2)
        self.assertIn("permisos must be an object", out)
        self.assertNotIn("Traceback", err)
        self.assertEqual(self.core.requests, [])

    def test_un_nombre_de_rol_que_es_una_lista_sale_con_2_y_sin_traza(self):
        malformed = {"mappedToAdmin": MAPPED, "roles": [{"name": ["PRUEBA"], "label": "x", "permisos": {}},
                                                       {"name": {"a": 1}, "label": "x", "permisos": {}}]}
        with tempfile.TemporaryDirectory() as folder:
            path = os.path.join(folder, "roles.json")
            with open(path, "w", encoding="utf-8") as f:
                json.dump(malformed, f)
            code, out, err = self.run_cli(roles_path=path)
        self.assertEqual(code, 2)
        self.assertIn("name must match", out)
        self.assertNotIn("Traceback", err)
        self.assertEqual(self.core.requests, [])

    def test_avisa_de_un_rol_de_core_que_roles_json_no_declara_y_no_lo_borra(self):
        self.run_cli()
        self.core.roles["ADMIN"] = {"name": "ADMIN", "label": "Admin", "permissions": [], "fieldPermissions": []}
        for extra in ("ROL_A_MANO", "OTRO_A_MANO"):
            self.core.roles[extra] = {"name": extra, "label": "A mano", "permissions": [], "fieldPermissions": []}
        self.core.requests.clear()
        code, out, err = self.run_cli()
        self.assertEqual(code, 0, err)
        warnings = [line for line in err.splitlines() if line.startswith("warning:")]
        self.assertEqual(len(warnings), 2, err)
        self.assertTrue(any("ROL_A_MANO" in w for w in warnings) and any("OTRO_A_MANO" in w for w in warnings), warnings)
        self.assertNotIn("role ADMIN", err)
        self.assertEqual(self.writes(), [])
        self.assertIn("ROL_A_MANO", self.core.roles)
        self.assertIn("done: 0 created, 0 updated, 15 skipped", out)

    def test_un_permiso_de_campo_a_mano_se_neutraliza_y_la_corrida_siguiente_no_escribe(self):
        # wasichai 0.3.2: PUT [] no borra nada y no hay DELETE; se manda el campo con lectura y escritura (entrada neutra)
        self.run_cli()
        self.core.roles["ANALYST"]["fieldPermissions"].append(
            {"objectName": "topic", "fieldName": "code", "read": True, "write": False})
        self.core.requests.clear()
        code, out, err = self.run_cli()
        self.assertEqual(code, 0, err)
        roles = load(ROLES_PATH)
        analyst = next(r for r in roles["roles"] if r["name"] == "ANALYST")
        wanted = apply_roles.field_permissions_payload(analyst, load(MODEL_PATH), roles["restrictedFields"])["fields"]
        neutral = {"objectName": "topic", "fieldName": "code", "read": True, "write": True}
        self.assertEqual(self.writes(), [("PUT", "/api/roles/ANALYST/field-permissions", {"fields": wanted + [neutral]})])
        self.assertIn("done: 0 created, 1 updated, 14 skipped", out)
        self.assertIn(neutral, self.core.roles["ANALYST"]["fieldPermissions"])
        self.core.requests.clear()
        self.assertIn("done: 0 created, 0 updated, 15 skipped", self.run_cli()[1])
        self.assertEqual(self.writes(), [])

    def test_u_reemplaza_las_restricciones_del_objeto_y_neutraliza_las_de_otro(self):
        roles = {"mappedToAdmin": MAPPED,
                 "roles": [{"name": "PRUEBA", "label": "Rol de prueba", "permisos": {"zone": ["READ", "u"]}}]}
        with tempfile.TemporaryDirectory() as folder:
            roles_path = os.path.join(folder, "roles.json")
            model_path = os.path.join(folder, "model.json")
            for path, data in ((roles_path, roles), (model_path, FIXTURE_MODEL)):
                with open(path, "w", encoding="utf-8") as f:
                    json.dump(data, f, ensure_ascii=False)
            self.run_cli(roles_path=roles_path, model_path=model_path)
            self.core.roles["PRUEBA"]["fieldPermissions"] += [
                {"objectName": "visit", "fieldName": "visit_date", "read": False, "write": False}]
            self.core.requests.clear()
            code, out, err = self.run_cli(roles_path=roles_path, model_path=model_path)
            self.assertEqual(code, 0, err)
            self.assertEqual(self.writes(), [("PUT", "/api/roles/PRUEBA/field-permissions", {"fields": [
                {"objectName": "zone", "fieldName": "code", "read": True, "write": False},
                {"objectName": "zone", "fieldName": "zone_type", "read": True, "write": False},
                {"objectName": "visit", "fieldName": "visit_date", "read": True, "write": True}]})])
            self.core.requests.clear()
            code, out, err = self.run_cli(roles_path=roles_path, model_path=model_path)
        self.assertEqual(self.writes(), [])
        self.assertIn("done: 0 created, 0 updated, 1 skipped", out)

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
