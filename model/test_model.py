"""model.json, model/workflows/, model/automations/ y model/document-types/ frente a los documentos de diseño
(database-model.md §24, «Cómo se prueba», y workflows.md, «Pruebas del modelo»).

Cada objeto, campo, relación, ENUM, flag, único, índice y acción de las fichas de la fase está en model.json con su tipo,
y nada de una fase posterior; cada workflow tiene los estados de domain-model.md §7.3 y las reglas del motor; cada
automatización y cada tipo de documento de la fase está, con su disparador, sin escribir derivados y sin congelar campos
restringidos.

Correr: cd model && python3 -m unittest -v test_model
"""
import json
import os
import re
import unittest

import design_docs

HERE = os.path.dirname(os.path.abspath(__file__))
PHASE = "F8"
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
        self.assertEqual(len(wanted), 91)

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
                self.assertEqual(got.get("maxBytes"), card.max_bytes.get(got["name"]), where)
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
            self.assertEqual([a["name"] for a in obj.get("actions", [])],
                             [a for a in card.actions if design_docs.action_phase(card, a) <= PHASE], name)
            # "`unique` en `x`" de la ficha: un campo con unique: true o, si x es el campo de una relación (delivery desde
            # F5), la relación con unique: true, que apply.py pone en la fase 3 (wasichai no admite un uniqueConstraints de
            # un solo campo)
            relations = {r["fieldName"]: r for r in self.model["relationships"] if r["source"] == name and r.get("fieldName")}
            for field in card.unique:
                holder = relations.get(field) or next(f for f in obj["fields"] if f["name"] == field)
                self.assertIs(holder.get("unique"), True, f"{name}.{field}")
            for field, rel in relations.items():
                self.assertEqual(rel.get("unique", False), field in card.unique, f"{name}.{field}")

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

    # B-01 (U-42, wasichai 1.4.0): cada estado y cada transición tienen su etiqueta en español, que es la de siempre, y
    # en inglés, para que el portal en inglés no muestre los estados en español
    def test_cada_estado_y_transicion_tiene_etiqueta_en_espanol_y_en_ingles(self):
        for obj, wf in self.workflows.items():
            for item in wf["definition"]["states"] + wf["definition"]["transitions"]:
                labels = item.get("labels", {})
                self.assertEqual(sorted(labels), ["en", "es"], (obj, item["name"]))
                self.assertEqual(labels["es"], item["label"], (obj, item["name"]))
                self.assertTrue(labels["en"].strip() and labels["en"] == labels["en"].strip(), (obj, item["name"]))

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
        # AgreementWriteGuard, los listeners y los destinatarios por rol (verify y approve_senior), filas de F2 y F3; desde
        # F4, las de AttentionWriteGuard, RiskWriteGuard y WarningWriteGuard, los comandos de reclasificación, aceptación
        # y revisiones, RequestClosureListener, QcsTransitionDatesListener y los avisos (verify de WF-E1,
        # resolve_high_severity, start_triage y las de tratamiento de WF-F); desde F5, las de InvestmentWriteGuard (P1 a P9 y
        # G1 a G4), los avisos de WF-G y WF-P, TransitionReasonGuard y RequestClosureListener (close de WF-P)
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
            "grievance": {"acknowledge", "investigate", "start_action", "start_action_after_investigation", "resolve",
                          "resolve_high_severity", "reopen", "verify", "close_resolved", "close", "reject_received",
                          "reject_acknowledged", "reject_in_assessment", "withdraw_received", "withdraw_acknowledged",
                          "withdraw_in_assessment", "withdraw_in_investigation", "withdraw_in_action",
                          "close_reclassified_received", "close_reclassified_acknowledged",
                          "close_reclassified_in_assessment"},
            "inquiry": {"answer", "refer", "close", "close_referred", "close_reclassified"},
            "stakeholder_request": {"accept", "decline", "close_declined", "close_accepted", "close_unfulfilled",
                                    "close_reclassified_received", "close_reclassified_under_evaluation"},
            "social_risk": {"assess", "decide_mitigation", "decide_monitoring", "start_monitoring", "resume_mitigation",
                            "close_identified", "close_assessed", "close_mitigating", "close_monitoring"},
            "early_warning": {"start_triage", "confirm", "dismiss"},
            "incident": {"close"},
            "social_program": {"submit", "return_to_draft", "approve", "plan_direct", "close", "suspend",
                               "cancel_draft", "cancel_pending_approval", "cancel_planned", "cancel_suspended"},
            "social_contribution": {"approve", "approve_direct", "reject", "register_procurement", "register_delivery",
                                    "close", "cancel_approved", "cancel_procured"},
            # F8: DeliverableWriteGuard, la emisión (CommandTransitionGuard) y el aviso de aprobación pendiente de WF-H
            "deliverable": {"submit_for_review", "resubmit", "approve", "return_to_draft", "issue", "start_revision"},
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

class SubmitRolesReadAssignmentsTests(unittest.TestCase):
    """Quien envía a aprobación lee project_assignment: los destinatarios del aviso salen de esa lectura como quien envía,
    y sin READ el aviso se pierde (ApprovalNoticeListener, Forbidden)."""

    def test_todo_rol_de_una_transicion_submit_lee_project_assignment(self):
        roles = {r["name"]: r.get("permisos", {}) for r in load("roles.json")["roles"]}
        checked = 0
        for name in sorted(os.listdir(os.path.join(HERE, "workflows"))):
            definition = load(os.path.join("workflows", name))["definition"]
            for t in definition["transitions"]:
                if not t["name"].startswith("submit"):
                    continue
                for role in t["roles"]:
                    checked += 1
                    self.assertIn(
                        "READ",
                        roles.get(role, {}).get("project_assignment", []),
                        f"{name}: {role} aplica {t['name']} sin READ sobre project_assignment",
                    )
        self.assertGreater(checked, 0)


def definitions(kind):
    """{object: [definition]} of model/<kind>/<object>.json, in file order."""
    folder = os.path.join(HERE, kind)
    if not os.path.isdir(folder):
        return {}
    return {f[:-len(".json")]: load(os.path.join(kind, f)) for f in sorted(os.listdir(folder)) if f.endswith(".json")}


class AutomationsAndDocumentTypesTests(unittest.TestCase):
    """workflows.md, «Automatizaciones» y «Pruebas del modelo»: las automatizaciones y los tipos de documento de la fase,
    sus disparadores, sus acciones y lo que imprimen (security.md §11.4)."""

    @classmethod
    def setUpClass(cls):
        cls.model = load("model.json")
        cls.objects = {o["name"]: o for o in cls.model["objects"]}
        cls.cards = design_docs.cards()
        cls.automations = definitions("automations")
        cls.types = definitions("document-types")
        cls.workflows = {name: wf for name, wf in definitions("workflows").items()}
        cls.restricted = {(e["object"], f) for e in load("roles.json")["restrictedFields"] for f in e["fields"]}

    def test_las_automatizaciones_son_las_de_workflows_md_hasta_la_fase_salvo_las_que_esperan_una_brecha(self):
        doc = [a for a in design_docs.automations() if a["phase"] <= PHASE]
        wanted = sorted((a["object"], a["name"]) for a in doc if a["after"] is None)
        got = sorted((obj, a["name"]) for obj, items in self.automations.items() for a in items)
        self.assertEqual(got, wanted)
        # AUT-03 y AUT-04 esperan a UPS-23: las actas de entrega y de recepción congelarían los receptores de delivery;
        # AUT-06, AUT-07 y AUT-08 también, porque el cargo y las cartas congelarían la persona de la QCS (workflows.md,
        # decisión 3 de F4; §2.2 de la especificación de F5)
        waiting = {a["id"]: a["after"] for a in doc if a["after"]}
        self.assertEqual(waiting, {"AUT-03": "UPS-23", "AUT-04": "UPS-23", "AUT-06": "UPS-23", "AUT-07": "UPS-23",
                                   "AUT-08": "UPS-23"})
        self.assertEqual(wanted, [("commitment", "aut-01-commitment-execution-task"),
                                  ("deliverable", "aut-12-issue-certificate"),
                                  ("early_warning", "aut-09-warning-triage-task"),
                                  ("engagement", "aut-05-meeting-minutes"), ("engagement", "aut-10-engagement-reschedule-task"),
                                  ("grievance", "aut-02-grievance-ack-task"), ("social_risk", "aut-11-critical-risk-task")])

    def test_cada_disparador_es_el_de_workflows_md_y_nombra_un_estado_de_su_workflow(self):
        doc = {a["name"]: a for a in design_docs.automations()}
        for obj, items in self.automations.items():
            for automation in items:
                trigger = automation["definition"]["trigger"]
                wanted = doc[automation["name"]]
                self.assertEqual((trigger["type"], trigger.get("state"), trigger.get("transition")),
                                 (wanted["trigger"], wanted["state"], wanted["transition"]), automation["name"])
                if trigger.get("state"):
                    states = {st["name"] for st in self.workflows[obj]["definition"]["states"]}
                    self.assertIn(trigger["state"], states, automation["name"])

    def test_ninguna_escribe_un_derivado_y_cada_tarea_que_crea_lleva_proyecto_y_un_solo_origen(self):
        # workflows.md: ninguna automatización escribe un campo derivado; ExactlyOneGuard pide un único origen en task
        origins = self.cards["task"].exactly_one[0]
        for items in self.automations.values():
            for automation in items:
                for action in automation["definition"]["actions"]:
                    if action["type"] != "CREATE_RECORD":
                        continue
                    target = self.objects[action["targetObject"]]
                    derived = {f["name"] for f in target["fields"] if f.get("derived")}
                    self.assertEqual(set(action["values"]) & derived, set(), automation["name"])
                    if action["targetObject"] == "task":
                        self.assertEqual(action["values"]["project"], "{{project}}", automation["name"])
                        self.assertEqual(len([o for o in origins if o in action["values"]]), 1, automation["name"])

    def test_los_tipos_de_documento_son_los_de_workflows_md_hasta_la_fase_salvo_los_que_esperan_una_brecha(self):
        wanted = sorted((t["object"], t["name"], t["label"], t["prefix"]) for t in design_docs.document_types()
                        if t["phase"] <= PHASE and t["after"] is None)
        got = sorted((obj, t["name"], t["label"], t["prefix"]) for obj, items in self.types.items() for t in items)
        self.assertEqual(got, wanted)

    def test_cada_documento_generado_es_un_tipo_de_su_objeto(self):
        for obj, items in self.automations.items():
            names = {t["name"] for t in self.types.get(obj, [])}
            for automation in items:
                for action in automation["definition"]["actions"]:
                    if action["type"] == "GENERATE_DOCUMENT":
                        self.assertIn(action["documentType"], names, automation["name"])

    def has_restricted(self, obj):
        card = self.cards[obj]
        fields = {f["name"] for f in card.fields if f["restricted"]}
        fields |= {r["field"] for r in card.relationships if r["restricted"] and not r["inverse"]}
        return bool(fields) or any(o == obj for o, _ in self.restricted)

    def test_ningun_tipo_de_documento_congela_un_campo_restringido(self):
        # security.md §11.4 y UPS-23: la copia congelada lleva el registro entero y cada columna de las tablas
        # relacionadas que nombra la plantilla, sin filtrar por permisos de campo
        relationships = {r["name"]: r for r in self.model["relationships"]}

        def related(node):
            found = [node["attrs"]["relationship"]] if node["type"] == "relatedTable" else []
            for child in node.get("content", []):
                found.extend(related(child))
            return found

        for obj, items in self.types.items():
            self.assertFalse(self.has_restricted(obj), obj)
            for doc in items:
                for name in related(doc["template"]):
                    rel = relationships[name]
                    other = rel["source"] if rel["target"] == obj else rel["target"]
                    self.assertFalse(self.has_restricted(other), (doc["name"], name))

    def test_el_objeto_del_acta_de_entrega_tiene_campos_restringidos(self):
        # lo que espera UPS-23: si delivery dejara de restringir campos, el acta de entrega podría volver a F3
        self.assertTrue(self.has_restricted("delivery"))
        # AUT-04 (acta de recepción del aporte) también es de delivery: espera a UPS-23 con AUT-03
        self.assertNotIn("delivery", self.automations)

    def test_las_qcs_restringen_su_persona_y_no_tienen_tipos_de_documento(self):
        # lo que espera UPS-23 en F4 (decisión 3): el cargo de queja y las cartas de respuesta congelarían la persona que
        # plantea la QCS, que solo leen SOCIAL_MANAGER, COMMUNITY_RELATIONS_OFFICER y GRIEVANCE_MANAGER (security.md §7.9)
        for obj in ("grievance", "inquiry", "stakeholder_request"):
            self.assertTrue(self.has_restricted(obj), obj)
            self.assertNotIn(obj, self.types, obj)


class InvestmentModelTests(unittest.TestCase):
    """Lo que el modelo de F5 suma para las decisiones 1 a 3 de la especificación de F5 (§4.1)."""

    @classmethod
    def setUpClass(cls):
        cls.model = load("model.json")
        cls.objects = {o["name"]: o for o in cls.model["objects"]}
        cls.relationships = {r["name"]: r for r in cls.model["relationships"]}

    def field(self, obj, name):
        return next(f for f in self.objects[obj]["fields"] if f["name"] == name)

    def test_item_kind_tiene_sus_cuatro_valores_y_es_obligatorio_en_el_item(self):
        # decisión 3: los lineamientos se evalúan por tipo de ítem (ContributionGuidelines, plan 5.2a)
        self.assertEqual(self.model["enums"]["item_kind"], ["BIEN", "SERVICIO", "DINERO", "MANO DE OBRA"])
        kind = self.field("contribution_item", "item_kind")
        self.assertEqual((kind["type"], kind["enum"], kind.get("required")), ("ENUM", "item_kind", True))

    def test_una_sola_entrega_por_aporte_y_las_de_compromisos_no_chocan(self):
        # decisión 2: único sobre la relación opcional; las entregas de compromisos lo llevan vacío y un UNIQUE de
        # PostgreSQL no compara los NULL
        rel = self.relationships["delivery_social_contribution"]
        self.assertEqual((rel["source"], rel["fieldName"], rel["target"], rel.get("unique"), rel.get("required", False)),
                         ("delivery", "social_contribution", "social_contribution", True, False))
        self.assertNotIn("uniqueConstraints", self.objects["delivery"])

    def test_el_tema_del_aporte_y_los_temas_priorizados_del_proyecto(self):
        topic = self.relationships["contribution_topic"]
        self.assertEqual((topic["source"], topic["fieldName"], topic["target"]), ("social_contribution", "topic", "topic"))
        priority = self.relationships["project_priority_topic"]
        self.assertEqual((priority.get("type"), priority["source"], priority["target"]), ("MANY_TO_MANY", "project", "topic"))

    def test_los_vinculos_de_f5_en_objetos_anteriores(self):
        wanted = {"request_created_contribution": ("stakeholder_request", "created_contribution", "social_contribution"),
                  "task_social_program": ("task", "social_program", "social_program"),
                  "delivery_social_contribution": ("delivery", "social_contribution", "social_contribution")}
        for name, (source, field, target) in wanted.items():
            rel = self.relationships[name]
            self.assertEqual((rel["source"], rel["fieldName"], rel["target"]), (source, field, target), name)
        for name in ("evidence_social_contribution", "evidence_social_program"):
            self.assertEqual((self.relationships[name].get("type"), self.relationships[name]["source"]),
                             ("MANY_TO_MANY", "evidence"), name)

    def test_las_mediciones_son_inmutables_y_unicas_por_periodo(self):
        measurement = self.objects["program_measurement"]
        self.assertIs(measurement.get("appendOnly"), True)
        self.assertEqual(measurement["uniqueConstraints"], [["program_indicator", "period_start_date"]])


# §3.5 de la especificación de F6: los objetos de LYR-01 a LYR-16 que se exportan (land_right nunca, ASM-124)
EXPORTABLE = ("administrative_unit", "locality", "road_access", "community", "community_sector", "mining_concession",
              "environmental_feature", "project", "project_component", "area_of_influence", "land_parcel", "school",
              "health_facility")
ORIGIN_FIELDS = ("code", "source_crs", "source_reference", "import_batch_id")


class TerritoryF6Tests(unittest.TestCase):
    """§3.4 y §4.1 de la especificación de F6: los destinos nuevos de la importación llevan código y campos de origen, y
    los objetos de las capas territoriales declaran EXPORT. Ningún objeto ni relación nuevos."""

    @classmethod
    def setUpClass(cls):
        cls.model = load("model.json")
        cls.objects = {o["name"]: o for o in cls.model["objects"]}

    def test_comunidad_y_elemento_ambiental_traen_codigo_unico_opcional_y_campos_de_origen(self):
        for name in ("community", "environmental_feature"):
            fields = {f["name"]: f for f in self.objects[name]["fields"]}
            code = fields["code"]
            self.assertEqual((code["type"], code.get("unique", False), code.get("required", False)), ("TEXT", True, False),
                             name)
            self.assertEqual((fields["source_crs"]["type"], fields["source_crs"]["enum"]), ("ENUM", "source_crs"), name)
            self.assertEqual(fields["source_reference"]["type"], "TEXT", name)
            self.assertEqual(fields["import_batch_id"]["type"], "UUID", name)
            # como mining_concession, el destino que ya se reconcilia por código (gis.md §4.4)
            self.assertEqual([f for f in ORIGIN_FIELDS if f not in fields], [], name)

    def test_las_capas_territoriales_declaran_export_y_land_right_no(self):
        for name in EXPORTABLE:
            self.assertIn({"name": "EXPORT", "label": "Exportar"}, self.objects[name].get("actions", []), name)
        self.assertNotIn("EXPORT", [a["name"] for a in self.objects["land_right"].get("actions", [])])

    def test_f6_no_trae_objetos_ni_relaciones(self):
        # lo que había hasta F6, por las fases de las fichas: F7 suma sus objetos y relaciones (AiModelTests)
        cards = design_docs.cards()
        self.assertEqual(len([n for n in self.objects if cards[n].phase <= "F6"]), 82)
        relationships = [r for r in self.model["relationships"]
                         if design_docs.relationship_phase(cards, cards[r["source"]],
                                                           next(x for x in cards[r["source"]].relationships
                                                                if x["name"] == r["name"])) <= "F6"]
        self.assertEqual(len(relationships), 272)
        self.assertEqual(sum(len(o.get("actions", [])) for o in self.model["objects"] if cards[o["name"]].phase <= "F6"), 39)


class AiModelTests(unittest.TestCase):
    """§4 de la especificación de F7: ai_suggestion (apiOnly, WF-K) y ai_interaction (apiOnly y appendOnly), el catálogo
    «Tipo de sugerencia de IA» y la llamada a la IA de commitment_candidate. Ninguna acción declarada nueva."""

    @classmethod
    def setUpClass(cls):
        cls.model = load("model.json")
        cls.objects = {o["name"]: o for o in cls.model["objects"]}
        cls.relationships = {r["name"]: r for r in cls.model["relationships"]}
        cls.roles = load("roles.json")

    def test_f7_trae_dos_objetos_y_cuatro_relaciones(self):
        # lo que había hasta F7, por las fases de las fichas: cada fase cuenta solo lo suyo (8.2a suma los indicadores en
        # IndicatorModelTests; los totales absolutos viven en ModelAgainstCardsTests y en test_apply.ModelOfF8Tests)
        cards = design_docs.cards()
        upto = [o for o in self.model["objects"] if cards[o["name"]].phase <= "F7"]
        self.assertEqual(len(upto), 84)
        relationships = [r for r in self.model["relationships"]
                         if design_docs.relationship_phase(cards, cards[r["source"]],
                                                           next(x for x in cards[r["source"]].relationships
                                                                if x["name"] == r["name"])) <= "F7"]
        self.assertEqual(len(relationships), 276)
        self.assertEqual(sum(len(o.get("actions", [])) for o in upto), 39)

    def test_la_sugerencia_es_api_only_y_la_interaccion_ademas_inmutable(self):
        suggestion, interaction = self.objects["ai_suggestion"], self.objects["ai_interaction"]
        self.assertEqual((suggestion.get("apiOnly"), suggestion.get("appendOnly", False)), (True, False))
        self.assertEqual((interaction.get("apiOnly"), interaction.get("appendOnly")), (True, True))
        # WF-K va solo en la sugerencia: un objeto appendOnly no tiene workflow (WAS-ADR-0040)
        self.assertTrue(os.path.isfile(os.path.join(HERE, "workflows", "ai_suggestion.json")))
        self.assertFalse(os.path.isfile(os.path.join(HERE, "workflows", "ai_interaction.json")))

    def test_el_tipo_de_sugerencia_es_el_catalogo_del_analisis(self):
        self.assertEqual(self.model["enums"]["ai_suggestion_kind"],
                         ["EXTRACCION", "BORRADOR DE ACTA", "RESUMEN EJECUTIVO", "BORRADOR DE SECCION", "DUPLICADO",
                          "CLASIFICACION"])

    def test_la_sugerencia_cuelga_de_su_interaccion_y_las_dos_pueden_ser_del_tenant(self):
        wanted = {"ai_suggestion_ai_interaction": ("ai_suggestion", "ai_interaction", "ai_interaction", True),
                  "ai_suggestion_project": ("ai_suggestion", "project", "project", False),
                  "ai_interaction_project": ("ai_interaction", "project", "project", False)}
        for name, (source, field, target, required) in wanted.items():
            rel = self.relationships[name]
            self.assertEqual((rel["source"], rel["fieldName"], rel["target"], rel.get("required", False)),
                             (source, field, target, required), name)

    def test_el_candidato_apunta_a_su_interaccion_y_a_su_evidencia_de_origen(self):
        for name, field, target in (("candidate_ai_interaction", "ai_interaction", "ai_interaction"),
                                    ("candidate_source_evidence", "source_evidence", "evidence")):
            rel = self.relationships[name]
            self.assertEqual((rel["source"], rel["fieldName"], rel["target"], rel.get("required", False)),
                             ("commitment_candidate", field, target, False), name)

    def test_la_interaccion_no_guarda_la_entrada(self):
        # ai-architecture.md §9.2 y §12: solo la referencia a la entrada; el único texto largo es el resumen de la salida
        fields = {f["name"]: f for f in self.objects["ai_interaction"]["fields"]}
        self.assertEqual([n for n, f in fields.items() if f["type"] == "LONG_TEXT"], ["output_summary"])
        self.assertEqual(fields["input_reference"]["type"], "TEXT")
        self.assertIs(fields["run_by_platform"].get("derived"), True)

    def test_la_cita_es_el_unico_campo_restringido_de_las_salidas_de_la_ia(self):
        # security.md §7.9: la cita la leen solo los roles con DOWNLOAD_RESTRICTED sobre evidence
        restricted = {(e["object"], f) for e in self.roles["restrictedFields"] for f in e["fields"]
                      if e["object"] in ("ai_suggestion", "ai_interaction", "commitment_candidate")}
        self.assertEqual(restricted, {("ai_suggestion", "evidence_quote"), ("commitment_candidate", "evidence_quote")})


class IndicatorModelTests(unittest.TestCase):
    """§4.1 de la especificación de F8 (plan 8.2a): indicator, catálogo del tenant, e indicator_measurement (apiOnly y
    appendOnly, con project opcional), sus dos relaciones, los permisos de security.md §7.8 y ninguna acción, workflow ni
    campo restringido."""

    @classmethod
    def setUpClass(cls):
        cls.model = load("model.json")
        cls.objects = {o["name"]: o for o in cls.model["objects"]}
        cls.relationships = {r["name"]: r for r in cls.model["relationships"]}
        cls.roles = load("roles.json")

    def test_trae_sus_dos_objetos_y_sus_dos_relaciones(self):
        # solo lo suyo: los totales absolutos viven en ModelAgainstCardsTests y en test_apply.ModelOfF8Tests
        self.assertEqual([n for n in self.objects if n.startswith("indicator")], ["indicator", "indicator_measurement"])
        own = sorted(r["name"] for r in self.model["relationships"] if r["source"] == "indicator_measurement")
        self.assertEqual(own, ["indicator_measurement_indicator", "indicator_measurement_project"])

    def test_el_catalogo_es_editable_y_la_medicion_inmutable_y_solo_por_su_endpoint(self):
        indicator, measurement = self.objects["indicator"], self.objects["indicator_measurement"]
        self.assertEqual((indicator.get("apiOnly", False), indicator.get("appendOnly", False)), (False, False))
        self.assertEqual((measurement.get("apiOnly"), measurement.get("appendOnly")), (True, True))
        self.assertEqual(measurement["uniqueConstraints"], [["indicator", "project", "period_start_date"]])
        code = next(f for f in indicator["fields"] if f["name"] == "code")
        self.assertEqual((code["type"], code.get("required"), code.get("unique")), ("TEXT", True, True))
        for name in ("indicator", "indicator_measurement"):
            self.assertFalse(os.path.isfile(os.path.join(HERE, "workflows", f"{name}.json")), name)
            self.assertNotIn("actions", self.objects[name], name)

    def test_la_medicion_cuelga_de_su_indicador_y_puede_ser_del_tenant(self):
        wanted = {"indicator_measurement_indicator": ("indicator", "indicator", True),
                  "indicator_measurement_project": ("project", "project", False)}
        for name, (field, target, required) in wanted.items():
            rel = self.relationships[name]
            self.assertEqual((rel["source"], rel["fieldName"], rel["target"], rel.get("required", False)),
                             ("indicator_measurement", field, target, required), name)

    def test_solo_social_manager_edita_el_catalogo_y_cierra_periodos(self):
        # security.md §7.8: el catálogo, maestro sin datos sensibles, lo leen todos (también PROJECT_ADMIN y FIELD_AGENT, para
        # la meta de KPI-ENG-02, R1); las mediciones, todos menos PROJECT_ADMIN y FIELD_AGENT; ADMIN, además, por ser ADMIN
        editors = {r["name"] for r in self.roles["roles"] if "UPDATE" in r["permisos"].get("indicator", [])}
        closers = {r["name"] for r in self.roles["roles"] if "CREATE" in r["permisos"].get("indicator_measurement", [])}
        self.assertEqual((editors, closers), ({"SOCIAL_MANAGER"}, {"SOCIAL_MANAGER"}))
        blind = {r["name"] for r in self.roles["roles"] if "indicator" not in r["permisos"] and "*" not in r["permisos"]}
        self.assertEqual(blind, set())
        unmeasured = {r["name"] for r in self.roles["roles"]
                      if "indicator_measurement" not in r["permisos"] and "*" not in r["permisos"]}
        self.assertEqual(unmeasured, {"PROJECT_ADMIN", "FIELD_AGENT"})

    def test_ningun_campo_restringido(self):
        # indicator_measurement la leen roles sin acceso a la postura: por eso el cierre no congela KPI-STK-02, 05 ni 06
        self.assertEqual([e for e in self.roles["restrictedFields"] if e["object"].startswith("indicator")], [])


DOCX = "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
DRAFT_FILES = ("draft_model", "draft_internal_docx", "draft_internal_pdf", "draft_external_pdf",
               "draft_candidate_internal_docx", "draft_candidate_internal_pdf", "draft_candidate_external_pdf")


class DeliverableModelTests(unittest.TestCase):
    """§4.2 de la especificación de F8: deliverable con los siete FILE derivados del borrador (ASM-133), deliverable_section,
    deliverable_version con sus FILE (sin *_storage_key), deliverable_template (apiOnly y appendOnly, ASM-135), los
    vínculos de export_log y de task, el catálogo «Tipo de entregable» con dos tipos, WF-H, AUT-12 y la constancia; y,
    desde ADR-0031, deliverable_presentation (apiOnly, con su especificación y su PDF derivados)."""

    @classmethod
    def setUpClass(cls):
        cls.model = load("model.json")
        cls.objects = {o["name"]: o for o in cls.model["objects"]}
        cls.relationships = {r["name"]: r for r in cls.model["relationships"]}
        cls.roles = load("roles.json")

    def fields(self, name):
        return {f["name"]: f for f in self.objects[name]["fields"]}

    def test_f8_trae_cinco_objetos_dieciocho_relaciones_y_cinco_acciones(self):
        cards = design_docs.cards()
        # solo lo suyo (los indicadores de 8.2a los cuenta IndicatorModelTests; los totales absolutos viven en
        # ModelAgainstCardsTests y en test_apply.ModelOfF8Tests)
        own = [o["name"] for o in self.model["objects"]
               if cards[o["name"]].phase == "F8" and o["name"].startswith("deliverable")]
        self.assertEqual(own, ["deliverable", "deliverable_section", "deliverable_version", "deliverable_presentation",
                               "deliverable_template"])
        relationships = [r for r in self.model["relationships"]
                         if not r["source"].startswith("indicator")
                         and design_docs.relationship_phase(cards, cards[r["source"]],
                                                            next(x for x in cards[r["source"]].relationships
                                                                 if x["name"] == r["name"])) == "F8"]
        self.assertEqual(len(relationships), 18)
        self.assertEqual([a["name"] for a in self.objects["deliverable"]["actions"]],
                         ["APPROVE", "GENERATE", "ISSUE", "EXPORT", "DOWNLOAD_RESTRICTED"])

    def test_el_borrador_son_siete_archivos_derivados_y_ninguna_clave_propia(self):
        fields = self.fields("deliverable")
        self.assertEqual([n for n, f in fields.items() if f["type"] == "FILE"], list(DRAFT_FILES))
        for name in DRAFT_FILES:
            self.assertIs(fields[name].get("derived"), True, name)
            self.assertEqual(fields[name]["maxBytes"], 64 * 1024 * 1024, name)
        self.assertEqual(fields["draft_model"]["contentTypes"], ["text/plain"])
        self.assertEqual(fields["draft_internal_docx"]["contentTypes"], [DOCX])
        self.assertEqual(fields["draft_external_pdf"]["contentTypes"], ["application/pdf"])
        for name in ("draft_revision", "draft_warnings", "generation_requested_at", "current_revision"):
            self.assertIs(fields[name].get("derived"), True, name)
        self.assertFalse([n for n in fields if n.endswith("_storage_key")])

    def test_la_version_lleva_sus_archivos_y_sus_hashes(self):
        fields = self.fields("deliverable_version")
        files = {n: f.get("required", False) for n, f in fields.items() if f["type"] == "FILE"}
        self.assertEqual(files, {"docx": False, "pdf": True, "model": False})
        self.assertFalse([n for n in fields if n.endswith("_storage_key")])
        for name in ("pdf_sha256", "model_sha256", "template_sha256", "docx_sha256"):
            self.assertEqual(fields[name]["type"], "TEXT", name)
        version = self.objects["deliverable_version"]
        self.assertEqual((version.get("appendOnly"), version.get("apiOnly")), (True, True))

    def test_la_plantilla_es_un_objeto_inmutable_con_su_archivo_docx_de_20_mb(self):
        template = self.objects["deliverable_template"]
        self.assertEqual((template.get("appendOnly"), template.get("apiOnly")), (True, True))
        fields = self.fields("deliverable_template")
        self.assertEqual((fields["file"]["type"], fields["file"].get("required"), fields["file"]["maxBytes"],
                          fields["file"]["contentTypes"]), ("FILE", True, 20 * 1024 * 1024, [DOCX]))
        self.assertIs(fields["template_key"].get("required"), True)
        self.assertNotIn("uniqueConstraints", template)
        self.assertEqual([r for r in self.model["relationships"] if "deliverable_template" in (r["source"], r["target"])], [])

    def test_solo_dos_tipos_de_entregable(self):
        self.assertEqual(self.model["enums"]["deliverable_type"], ["DIAGNOSTICO SOCIAL", "INFORME DE CAMPO Y AVANCE"])

    def test_registro_de_salidas_y_tarea_apuntan_a_los_entregables(self):
        wanted = {"export_log_deliverable": ("export_log", "deliverable", "deliverable"),
                  "export_log_deliverable_version": ("export_log", "deliverable_version", "deliverable_version"),
                  "task_deliverable": ("task", "deliverable", "deliverable")}
        for name, (source, field, target) in wanted.items():
            rel = self.relationships[name]
            self.assertEqual((rel["source"], rel["fieldName"], rel["target"], rel.get("required", False)),
                             (source, field, target, False), name)

    def test_ningun_objeto_de_entregables_tiene_campos_restringidos(self):
        # la constancia de AUT-12 congela el registro y sus versiones (UPS-23, security.md §11.4)
        restricted = {e["object"] for e in self.roles["restrictedFields"]}
        self.assertEqual(restricted & set(self.objects) & {"deliverable", "deliverable_section", "deliverable_version",
                                                            "deliverable_presentation", "deliverable_template"}, set())

    def test_la_presentacion_es_api_only_con_sus_archivos_derivados_y_unica_por_version_publico_nivel_y_planificador(self):
        # ADR-0031 y deliverables.md §16.8: la crea el endpoint de pedido y la compone JOB-09 como la plataforma
        presentation = self.objects["deliverable_presentation"]
        self.assertEqual((presentation.get("apiOnly"), presentation.get("appendOnly", False)), (True, False))
        self.assertEqual(presentation["uniqueConstraints"],
                         [["deliverable_version", "audience", "disclosure_level", "planner_version"]])
        self.assertFalse(os.path.isfile(os.path.join(HERE, "workflows", "deliverable_presentation.json")))
        fields = self.fields("deliverable_presentation")
        self.assertEqual({n for n, f in fields.items() if not f.get("derived")}, {"audience", "disclosure_level"})
        self.assertEqual({n: (f["contentTypes"], f["maxBytes"]) for n, f in fields.items() if f["type"] == "FILE"},
                         {"spec": (["text/plain"], 64 * 1024 * 1024), "pdf": (["application/pdf"], 64 * 1024 * 1024)})
        self.assertEqual(self.model["enums"]["presentation_audience"],
                         ["DIRECCION", "CAMPO", "INVESTIGACION", "INSTITUCIONES", "COMUNIDAD"])
        self.assertEqual(self.model["enums"]["presentation_status"], ["EN COLA", "EN PROCESO", "LISTO", "FALLIDO"])
        wanted = {"presentation_deliverable": ("deliverable_presentation", "deliverable", "deliverable", True),
                  "presentation_version": ("deliverable_presentation", "deliverable_version", "deliverable_version", True),
                  "export_log_deliverable_presentation": ("export_log", "deliverable_presentation", "deliverable_presentation",
                                                          False)}
        for name, (source, field, target, required) in wanted.items():
            rel = self.relationships[name]
            self.assertEqual((rel["source"], rel["fieldName"], rel["target"], rel.get("required", False)),
                             (source, field, target, required), name)

    def test_quien_pide_una_presentacion_puede_descargarla_y_registrar_la_descarga(self):
        # ASM-147: el pedido exige CREATE y la acción de descarga del nivel; la descarga, CREATE sobre export_log
        roles = {r["name"]: r["permisos"] for r in self.roles["roles"]}
        creators = {name for name, grants in roles.items() if "CREATE" in grants.get("deliverable_presentation", [])}
        self.assertEqual(creators, {"PROJECT_MANAGER", "SOCIAL_MANAGER", "STAKEHOLDER_MANAGER", "ANALYST"})
        for name in creators:
            self.assertTrue({"EXPORT", "DOWNLOAD_RESTRICTED"} & set(roles[name]["deliverable"]), name)
            self.assertIn("CREATE", roles[name]["export_log"], name)

    def test_issue_es_la_unica_transicion_que_crea_versiones(self):
        # workflows.md, «Transiciones de comando»: issue de WF-H; quienes la aplican crean versiones y filas de export_log
        workflow = load(os.path.join("workflows", "deliverable.json"))
        roles = {r["name"]: r["permisos"] for r in self.roles["roles"]}
        issuers = next(t["roles"] for t in workflow["definition"]["transitions"] if t["name"] == "issue")
        self.assertEqual(sorted(issuers), ["SOCIAL_MANAGER", "STAKEHOLDER_MANAGER"])
        for role in issuers:
            self.assertIn("ISSUE", roles[role]["deliverable"], role)
            self.assertIn("CREATE", roles[role]["deliverable_version"], role)
            self.assertIn("CREATE", roles[role]["export_log"], role)
        creators = {name for name, grants in roles.items() if "CREATE" in grants.get("deliverable_version", [])}
        self.assertEqual(creators, set(issuers))


# la migración de la tabla fija de sgspe.ai (database-model.md §2.3: sgspe_ai, en classpath:db/sgspe/ai)
KNOWLEDGE_SQL = os.path.join(os.path.dirname(HERE), "src", "main", "resources", "db", "sgspe", "ai", "V1__knowledge_chunk.sql")


def sql_table(text, table):
    """{"columns": [(name, type, not_null)], "constraints": [...], "indexes": [...], "code": text} of `CREATE TABLE table
    (...)` and its `CREATE INDEX ... ON table ...` in a migration; "code" is the migration without its comments."""
    text = "\n".join(line.split("--")[0] for line in text.splitlines())
    body = re.search(rf"CREATE TABLE {re.escape(table)} \((.*?)\n\);", text, re.S).group(1)
    columns, constraints = [], []
    for line in (part.strip().rstrip(",") for part in body.split("\n")):
        if not line:
            continue
        if line.startswith("CONSTRAINT"):
            constraints.append(line)
        else:
            name, kind = line.split()[:2]
            columns.append((name, kind, "NOT NULL" in line))
    indexes = re.findall(rf"CREATE INDEX \w+ ON {re.escape(table)} (.*?);", text)
    return {"columns": columns, "constraints": constraints, "indexes": indexes, "code": text}


class FixedTablesTests(unittest.TestCase):
    """§4 de la especificación de F7 y ai-architecture.md §13.2: la migración de knowledge_chunk es la ficha de
    database-model.md §20 sin la columna embedding (etapa 2 de ADR-0005, CON-035), con su clave, su único, su índice
    compuesto y el GIN de texto completo en español, y sin claves foráneas entre esquemas (§2.3)."""

    @classmethod
    def setUpClass(cls):
        with open(KNOWLEDGE_SQL, encoding="utf-8") as f:
            cls.sql = sql_table(f.read(), "sgspe.knowledge_chunk")
        cls.card = design_docs.fixed_table("knowledge_chunk")

    def test_las_columnas_son_las_de_la_ficha_sin_embedding(self):
        wanted = [c for c in self.card["columns"] if c[0] != "embedding"]
        self.assertEqual(self.sql["columns"], wanted)
        self.assertNotIn("embedding", self.sql["code"])
        self.assertNotIn("vector", self.sql["code"].replace("tsvector", ""))

    def test_clave_unico_e_indices_de_la_ficha(self):
        constraints = " ".join(self.sql["constraints"])
        self.assertIn(f"PRIMARY KEY ({', '.join(self.card['primary_key'])})", constraints)
        for columns in self.card["uniques"]:
            self.assertIn(f"UNIQUE ({', '.join(columns)})", constraints)
        for columns in self.card["indexes"]:
            self.assertIn(f"({', '.join(columns)})", self.sql["indexes"])
        configuration, column = self.card["full_text"]
        self.assertIn(f"USING gin (to_tsvector('{configuration}', {column}))", self.sql["indexes"])

    def test_sin_claves_foraneas_y_el_esquema_si_falta(self):
        self.assertNotIn("REFERENCES", self.sql["code"])
        self.assertIn("CREATE SCHEMA IF NOT EXISTS sgspe;", self.sql["code"])


class PublicLayersTests(unittest.TestCase):
    """security.md §10.2 (reglas 3 y 8), gis.md §2.2 y §7 y §2.2 de la especificación de F6: GeoServer lee la base con
    sus credenciales, sin permisos ni alcance, así que model/layers.json solo publica las capas públicas, de maestros del
    tenant con una geometría declarada y sin campos restringidos (ASM-121)."""

    @classmethod
    def setUpClass(cls):
        cls.layers = load("layers.json")
        cls.objects = {o["name"]: o for o in load("model.json")["objects"]}
        cls.cards = design_docs.cards()
        cls.roles = load("roles.json")

    def test_son_las_capas_publicas_de_gis_md(self):
        wanted = sorted((layer, obj, field) for layer, pairs in design_docs.public_layers().items() for obj, field in pairs)
        self.assertEqual(sorted((e["layer"], e["object"], e["geometry"]) for e in self.layers), wanted)

    def test_cada_capa_es_un_maestro_del_tenant(self):
        tenant = set(design_docs.tenant_objects())
        for entry in self.layers:
            self.assertIn(entry["object"], tenant, entry["style"])

    def test_cada_capa_tiene_su_geometria_declarada(self):
        for entry in self.layers:
            field = next((f for f in self.objects[entry["object"]]["fields"] if f["name"] == entry["geometry"]), None)
            self.assertIsNotNone(field, entry["style"])
            self.assertEqual(field["type"], "GEOMETRY", entry["style"])
            self.assertTrue(field.get("geometryType"), entry["style"])

    def test_ninguna_capa_tiene_campos_restringidos(self):
        restricted = {e["object"] for e in self.roles["restrictedFields"]}
        for entry in self.layers:
            card = self.cards[entry["object"]]
            self.assertNotIn(entry["object"], restricted, entry["style"])
            self.assertEqual([f["name"] for f in card.fields if f["restricted"]], [], entry["style"])
            self.assertEqual([r["name"] for r in card.relationships if r["restricted"] and not r["inverse"]], [],
                             entry["style"])

    def test_los_nombres_de_distritos_localidades_y_comunidades_llevan_regla_de_texto(self):
        # gis.md §7.2: los nombres que muestran el Anexo 1 y el mapa de DOC-02 son reglas de texto de los SLD
        for entry in self.layers:
            if entry["layer"] in ("LYR-01", "LYR-02", "LYR-03", "LYR-05"):
                self.assertEqual(entry.get("label"), "name", entry["style"])

    def test_cada_estilo_tiene_su_sld_y_sus_colores(self):
        palette = load(os.path.join("styles", "palette.json"))["styles"]
        self.assertEqual(sorted(palette), sorted(e["style"] for e in self.layers))
        for entry in self.layers:
            self.assertTrue(os.path.isfile(os.path.join(HERE, "styles", f"{entry['style']}.sld")), entry["style"])
            self.assertEqual(palette[entry["style"]]["layer"], entry["layer"], entry["style"])


if __name__ == "__main__":
    unittest.main()
