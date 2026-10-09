"""model.json y model/workflows/ frente a los documentos de diseño (database-model.md §24, «Cómo se prueba», y
workflows.md, «Pruebas del modelo»).

Cada objeto, campo, relación, ENUM, flag, único, índice y acción de las fichas de la fase está en model.json con su tipo,
y nada de una fase posterior; cada workflow tiene los estados de domain-model.md §7.3 y las reglas del motor.

Correr: cd model && python3 -m unittest -v test_model
"""
import json
import os
import unittest

import design_docs

HERE = os.path.dirname(os.path.abspath(__file__))
PHASE = "F3"
# un estado nunca representa un plazo (RN-WFL-001): ninguno de estos nombres puede aparecer
DEADLINE_WORDS = ("overdue", "expired", "due", "late", "vencid")
# workflows.md, «Mecánica de wasichai» 4: nunca en una transición
NEVER_IN_TRANSITIONS = {"SUPER_ADMIN", "ORGANIZATION_ADMIN", "AUDITOR", "ANALYST", "READ_ONLY", "ADMIN"}


def load(name):
    with open(os.path.join(HERE, name), encoding="utf-8") as f:
        return json.load(f)


class ModelAgainstCardsTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.model = load("model.json")
        cls.cards = design_docs.cards()
        cls.objects = {o["name"]: o for o in cls.model["objects"]}

    def test_tiene_exactamente_los_objetos_de_las_fichas_hasta_la_fase(self):
        # las tablas fijas (notification, idempotency_key…) tienen ficha pero no son objetos: no están en la matriz
        catalog = design_docs.matrix()
        wanted = [name for name, card in self.cards.items() if card.phase <= PHASE and name in catalog]
        self.assertEqual([o["name"] for o in self.model["objects"]], wanted)
        self.assertEqual(len(wanted), 60)

    def test_cada_objeto_lleva_etiqueta_plural_descripcion_y_flags_de_su_ficha(self):
        for name, obj in self.objects.items():
            card = self.cards[name]
            self.assertEqual(obj["label"], card.label, name)
            self.assertTrue(obj["pluralLabel"] and obj["pluralLabel"] != obj["label"], name)
            self.assertTrue(obj.get("description"), name)
            for flag in ("appendOnly", "apiOnly", "requiresReason"):
                self.assertEqual(obj.get(flag, False), card.flags[flag], f"{name}.{flag}")

    def test_cada_campo_de_la_fase_con_su_tipo_requerido_unico_derivado_e_indice(self):
        for name, obj in self.objects.items():
            card = self.cards[name]
            wanted = [f for f in card.fields if design_docs.field_phase(card, f) <= PHASE]
            self.assertEqual([f["name"] for f in obj["fields"]], [f["name"] for f in wanted], name)
            for got, doc in zip(obj["fields"], wanted):
                where = f"{name}.{got['name']}"
                self.assertEqual(got["type"], doc["type"], where)
                self.assertEqual(got.get("enum"), doc["enum"], where)
                self.assertEqual(got.get("geometryType"), doc["geometryType"], where)
                self.assertEqual(got.get("required", False), doc["required"], where)
                self.assertEqual(got.get("unique", False), doc["unique"], where)
                self.assertEqual(got.get("derived", False), doc["derived"], where)
                self.assertEqual(got.get("indexed", False), got["name"] in card.indexed, where)
                self.assertEqual(got.get("defaultValue"), doc["default"], where)
                self.assertEqual(got.get("contentTypes"), card.content_types.get(got["name"]), where)
                self.assertTrue(got.get("label"), where)
                self.assertNotIn("editable", got, where)

    def test_un_derivado_nunca_es_requerido(self):
        for name, obj in self.objects.items():
            for field in obj["fields"]:
                if field.get("derived"):
                    self.assertFalse(field.get("required", False), f"{name}.{field['name']}")

    def test_toda_geometria_es_4326_2d_y_nunca_requerida_ni_unica(self):
        for name, obj in self.objects.items():
            for field in obj["fields"]:
                if field["type"] == "GEOMETRY":
                    where = f"{name}.{field['name']}"
                    self.assertEqual((field["srid"], field["dimension"]), (4326, 2), where)
                    self.assertFalse(field.get("required") or field.get("unique"), where)

    def test_las_relaciones_de_la_fase_con_su_tipo_campo_destino_y_requerido(self):
        wanted = []
        for name in self.objects:
            card = self.cards[name]
            for rel in card.relationships:
                if not rel["inverse"] and design_docs.relationship_phase(self.cards, card, rel) <= PHASE:
                    wanted.append((rel["name"], rel["type"], name, rel["target"], rel["field"], rel["required"]))
        got = [(r["name"], r.get("type", "MANY_TO_ONE"), r["source"], r["target"], r.get("fieldName"),
                r.get("required", False)) for r in self.model["relationships"]]
        self.assertEqual(got, wanted)

    def test_cada_relacion_lleva_etiqueta_e_inversa_con_el_plural_del_origen(self):
        for rel in self.model["relationships"]:
            self.assertTrue(rel["label"], rel["name"])
            self.assertEqual(rel["inverseLabel"], self.objects[rel["source"]]["pluralLabel"], rel["name"])

    def test_unicos_indices_y_acciones_de_cada_ficha(self):
        for name, obj in self.objects.items():
            card = self.cards[name]
            self.assertEqual(obj.get("uniqueConstraints", []), card.unique_constraints, name)
            self.assertEqual(obj.get("indexes", []), card.indexes, name)
            self.assertEqual([a["name"] for a in obj.get("actions", [])], card.actions, name)
            for field in card.unique:
                self.assertTrue(next(f for f in obj["fields"] if f["name"] == field).get("unique"), f"{name}.{field}")

    def test_los_enum_son_los_catalogos_del_analisis_y_solo_los_usados(self):
        catalogs = design_docs.catalogs()
        used = {f["enum"] for o in self.model["objects"] for f in o["fields"] if f["type"] == "ENUM"}
        self.assertEqual(set(self.model["enums"]), used)
        for key, options in self.model["enums"].items():
            self.assertEqual(options, catalogs[key], key)

    def test_los_filtros_y_ordenes_que_usa_el_portal_existen(self):
        # plan 2.3, «Interfaces»: wasichai rechaza un filtro con un nombre que el objeto no tiene
        fields = {}
        for obj in self.model["objects"]:
            fields.update({f["name"]: True for f in obj["fields"]})
        fields.update({r["fieldName"]: True for r in self.model["relationships"] if r.get("fieldName")})
        portal = ["project", "member_user_id", "active", "role_name", "campaign_status", "area_type", "area_of_influence",
                  "community", "locality", "organization", "person", "stakeholder", "engagement", "field_visit", "study",
                  "month", "exporter_user_id", "from_person", "from_organization", "to_person", "to_organization", "name",
                  "code", "version_number", "scheduled_at", "data_date", "assessment_date", "observed_at",
                  "planned_start_date", "exported_at"]
        self.assertEqual([name for name in portal if name not in fields], [])

    def test_ningun_valor_enum_arrastra_notas_del_analisis(self):
        # Review Focus 5: "(propuesto)", "(propuestos, salvo …)" y las aclaraciones entre paréntesis de §10 no son valores
        import apply
        for key, options in self.model["enums"].items():
            for option in options:
                self.assertNotIn("(", option, key)
                self.assertNotIn("propuest", option.lower(), key)
                self.assertEqual(option, option.upper(), key)
                self.assertTrue(apply.enum_option_valid(option), (key, option))

    def test_los_objetos_con_workflow_son_los_de_las_fichas_y_ninguno_es_append_only(self):
        files = sorted(f[:-len(".json")] for f in os.listdir(os.path.join(HERE, "workflows")))
        self.assertEqual(files, sorted(n for n in self.objects if self.cards[n].workflow))
        for name in files:
            self.assertFalse(self.objects[name].get("appendOnly", False), name)

    def test_descripciones_de_objetos_y_campos_son_el_texto_de_las_fichas_sin_comillas_invertidas(self):
        for name, obj in self.objects.items():
            card = self.cards[name]
            self.assertEqual(obj["description"], card.description.replace("`", ""), name)
            docs = {f["name"]: f["description"] for f in card.fields}
            for field in obj["fields"]:
                self.assertEqual(field["description"], docs[field["name"]].replace("`", ""), f"{name}.{field['name']}")


class WorkflowTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.roles = {r["name"] for r in load("roles.json")["roles"]}
        cls.states = design_docs.workflow_states()
        cls.cards = design_docs.cards()
        folder = os.path.join(HERE, "workflows")
        cls.workflows = {f[:-len(".json")]: load(os.path.join("workflows", f)) for f in sorted(os.listdir(folder))}

    def test_nombre_y_etiqueta_del_workflow(self):
        for obj, wf in self.workflows.items():
            letter = self.cards[obj].workflow.split("-")[1].lower()
            self.assertEqual(wf["name"], f"wf-{letter}-{obj}")
            self.assertEqual(wf["label"], self.cards[obj].label)
            self.assertIs(wf["enabled"], True)

    def test_los_estados_son_los_de_domain_model_7_3_con_su_tipo_y_etiqueta(self):
        for obj, wf in self.workflows.items():
            doc = self.states[obj]
            wanted = [(n, label, kind) for kind in ("INITIAL", "INTERMEDIATE", "FINAL") for n, label in doc[kind]]
            got = [(s["name"], s["label"], s["type"]) for s in wf["definition"]["states"]]
            self.assertEqual(sorted(got), sorted(wanted), obj)

    def test_ningun_estado_representa_un_plazo(self):
        for obj, wf in self.workflows.items():
            for state in wf["definition"]["states"]:
                self.assertFalse(any(w in state["name"] for w in DEADLINE_WORDS), (obj, state["name"]))

    def test_roles_existentes_no_vacios_y_nunca_los_de_solo_lectura_ni_admin(self):
        for obj, wf in self.workflows.items():
            for t in wf["definition"]["transitions"]:
                self.assertTrue(t["roles"], (obj, t["name"]))
                self.assertTrue(set(t["roles"]) <= self.roles, (obj, t["name"]))
                self.assertFalse(set(t["roles"]) & NEVER_IN_TRANSITIONS, (obj, t["name"]))

    def test_todo_estado_es_alcanzable_y_todo_estado_no_final_tiene_salida(self):
        for obj, wf in self.workflows.items():
            states = wf["definition"]["states"]
            transitions = wf["definition"]["transitions"]
            initial = next(s["name"] for s in states if s["type"] == "INITIAL")
            reached, frontier = {initial}, [initial]
            while frontier:
                current = frontier.pop()
                for t in transitions:
                    if t["from"] == current and t["to"] not in reached:
                        reached.add(t["to"])
                        frontier.append(t["to"])
            self.assertEqual(reached, {s["name"] for s in states}, obj)
            for state in states:
                if state["type"] != "FINAL":
                    self.assertTrue(any(t["from"] == state["name"] for t in transitions), (obj, state["name"]))

    def test_las_transiciones_que_nombran_las_guardas_y_los_endpoints_existen(self):
        # workflows.md: «Transiciones de comando», «Transiciones con motivo obligatorio», las guardas D1 a D11 de WF-D, la de
        # AgreementWriteGuard, los listeners y los destinatarios por rol (verify y approve_senior), filas de F2 y F3
        named = {
            "project": {"approve", "resume", "return_to_draft", "suspend", "cancel_draft", "cancel_in_review"},
            "stakeholder": {"profile", "assess", "retire_identified", "retire_profiled", "retire_assessed", "reinstate"},
            "engagement": {"register_held", "register_held_unplanned", "close", "close_light", "postpone",
                           "return_minutes", "cancel_planned", "cancel_scheduled", "cancel_postponed", "approve_minutes"},
            "field_visit": {"cancel"},
            "area_of_influence": {"approve", "supersede"},
            "commitment": {"submit", "approve", "approve_senior", "approve_direct", "register_fulfillment",
                           "reopen_execution", "verify", "close", "cancel_draft", "cancel_pending_approval",
                           "cancel_approved", "cancel_in_progress", "cancel_blocked"},
            "agreement": {"activate", "activate_from_draft", "resume"},
            "commitment_candidate": {"accept", "reject", "mark_duplicate"},
            "task": {"cancel_open", "cancel_in_progress", "cancel_blocked"},
        }
        for obj, names in named.items():
            have = {t["name"] for t in self.workflows[obj]["definition"]["transitions"]}
            self.assertTrue(names <= have, (obj, names - have))

    def test_wf_b_es_el_unico_sin_estado_final(self):
        without = [obj for obj, wf in self.workflows.items()
                   if not any(s["type"] == "FINAL" for s in wf["definition"]["states"])]
        self.assertEqual(without, ["stakeholder"])

    def test_transiciones_nombre_etiqueta_origen_destino_y_roles_son_los_de_workflows_md(self):
        # un rol entra en una transición desde su fase (security.md §7.1): los «Roles operativos» de WF-N que reciben
        # permisos en F4 y F5 (GRIEVANCE_MANAGER, SOCIAL_RISK_MANAGER, SOCIAL_INVESTMENT_MANAGER) llegan con su fase
        phases, _ = design_docs.role_phases()
        for obj, wf in self.workflows.items():
            letter = self.cards[obj].workflow.split("-")[1]
            doc = design_docs.workflow_transitions(letter, obj)
            self.assertTrue(doc, obj)
            got = [(t["name"], t["label"], t["from"], t["to"], sorted(t["roles"])) for t in wf["definition"]["transitions"]]
            wanted = [(t["name"], t["label"], t["from"], t["to"], sorted(r for r in t["roles"] if phases[r] <= PHASE))
                      for t in doc]
            self.assertEqual(sorted(got), sorted(wanted), obj)

    def test_un_rol_de_una_fase_posterior_no_esta_en_ningun_workflow(self):
        phases, _ = design_docs.role_phases()
        for obj, wf in self.workflows.items():
            for t in wf["definition"]["transitions"]:
                self.assertEqual([r for r in t["roles"] if phases[r] > PHASE], [], (obj, t["name"]))


if __name__ == "__main__":
    unittest.main()
