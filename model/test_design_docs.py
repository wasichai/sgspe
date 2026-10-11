"""Pruebas de design_docs.py: lee de los documentos lo que dicen, con casos que se comprueban a mano en cada documento.

Correr: cd model && python3 -m unittest -v test_design_docs
"""
import os
import shutil
import tempfile
import unittest

import design_docs


class CardsTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.cards = design_docs.cards()

    def test_lee_las_fichas_de_los_89_objetos_y_las_tablas_fijas(self):
        catalog = design_docs.matrix()
        self.assertEqual(len(catalog), 89)
        self.assertTrue(set(catalog) <= set(self.cards))
        self.assertEqual(sum(1 for name in catalog if self.cards[name].phase == "F2"), 48)

    def test_la_ficha_de_project(self):
        card = self.cards["project"]
        self.assertEqual((card.label, card.phase, card.workflow, card.actions), ("Proyecto", "F2", "WF-A", ["APPROVE", "READ_ALL_PROJECTS"]))
        self.assertTrue(card.description.startswith("Proyecto extractivo (o prospecto)"))
        fields = {f["name"]: f for f in card.fields}
        self.assertEqual((fields["code"]["type"], fields["code"]["required"], fields["code"]["unique"]), ("TEXT", True, True))
        self.assertEqual((fields["working_crs"]["type"], fields["working_crs"]["enum"]), ("ENUM", "working_crs"))
        self.assertEqual((fields["interest_area"]["type"], fields["interest_area"]["geometryType"]), ("GEOMETRY", "MULTIPOLYGON"))
        self.assertEqual(fields["interest_threshold"]["default"], "3")
        self.assertIsNone(fields["interest_threshold"]["phase"])
        self.assertEqual((fields["due_soon_days"]["phase"], fields["grievance_ack_days"]["phase"]), ("F3", "F4"))
        self.assertEqual(card.unique, ["code"])
        rel = card.relationships[0]
        self.assertEqual((rel["name"], rel["type"], rel["field"], rel["target"]), ("project_concession", "MANY_TO_MANY", None, "mining_concession"))

    def test_derivados_restringidos_unicos_compuestos_e_indices(self):
        stakeholder = {f["name"]: f for f in self.cards["stakeholder"].fields}
        self.assertTrue(stakeholder["stance"]["derived"] and stakeholder["stance"]["restricted"])
        self.assertEqual(self.cards["stakeholder"].unique_constraints, [["project", "person"], ["project", "organization"]])
        self.assertEqual(self.cards["stakeholder"].exactly_one, [["person", "organization"]])
        # el bullet de únicos de area_of_influence_member sigue en la línea siguiente
        self.assertEqual(len(self.cards["area_of_influence_member"].unique_constraints), 3)
        self.assertEqual(self.cards["engagement"].indexed, ["scheduled_at", "held_at"])
        self.assertEqual(self.cards["stakeholder_assessment"].indexes, [["stakeholder", "assessment_date"]])
        self.assertEqual(self.cards["stakeholder_assessment"].flags, {"appendOnly": True, "apiOnly": True, "requiresReason": True})

    def test_un_bullet_con_indexed_e_indexes_y_un_unico_por_grupos(self):
        # commitment: «`indexed` en `due_date`, `owner_user_id`; `indexes` (`project`, `deadline_status`)»
        self.assertEqual(self.cards["commitment"].indexed, ["due_date", "owner_user_id"])
        self.assertEqual(self.cards["commitment"].indexes, [["project", "deadline_status"]])
        self.assertEqual(self.cards["commitment"].unique_constraints, [["project", "code"]])
        self.assertEqual(self.cards["task"].unique, ["generator_key"])
        self.assertEqual(self.cards["task"].indexed, ["assignee_user_id", "due_date"])

    def test_la_ficha_de_evidence_lleva_el_campo_file(self):
        # D1 (WAS-ADR-0061): el archivo es un campo FILE requerido; la clave, el nombre, el tipo, el tamaño y el hash los
        # guarda wasichai-files
        card = self.cards["evidence"]
        fields = {f["name"]: f for f in card.fields}
        self.assertEqual((fields["file"]["type"], fields["file"]["required"], fields["file"]["unique"]), ("FILE", True, False))
        self.assertTrue({"storage_key", "file_name", "mime_type", "size_bytes", "sha256"}.isdisjoint(fields))
        self.assertEqual((card.unique, card.indexed, card.actions), ([], ["import_batch_id"], ["DOWNLOAD_RESTRICTED", "EDIT_ANY"]))
        types = card.content_types["file"]
        self.assertEqual((len(types), types[:2]), (8, ["application/pdf", "image/*"]))

    def test_las_relaciones_llevan_su_fase_y_las_inversas_se_marcan(self):
        cards = self.cards
        alliance = {r["name"]: r for r in cards["strategic_alliance"].relationships}
        self.assertEqual(design_docs.relationship_phase(cards, cards["strategic_alliance"], alliance["strategic_alliance_agreement"]), "F3")
        self.assertEqual(design_docs.relationship_phase(cards, cards["strategic_alliance"], alliance["strategic_alliance_organization"]), "F2")
        self.assertTrue(next(r for r in cards["topic"].relationships if r["name"] == "engagement_topic")["inverse"])
        self.assertTrue(next(r for r in cards["land_right"].relationships if r["field"] == "holder_person")["restricted"])


class CatalogTests(unittest.TestCase):
    def test_las_opciones_salen_del_analisis_sin_notas(self):
        catalogs = design_docs.catalogs()
        self.assertEqual(catalogs["stakeholder_scope"], ["AIDS", "AIIS", "REGIONAL", "NACIONAL"])
        self.assertEqual(catalogs["working_crs"], ["EPSG 32717", "EPSG 32718", "EPSG 32719"])
        self.assertEqual(catalogs["relationship_status"],
                         ["SIN CONTACTO", "CONTACTO ESTABLECIDO", "RELACION ACTIVA", "RELACION DETERIORADA", "INACTIVA"])
        self.assertEqual((catalogs["perception_subject"], catalogs["perception_polarity"]),
                         (["MINERIA", "PROYECTO", "EMPRESA"], ["POSITIVA", "NEGATIVA"]))
        self.assertEqual(catalogs["impact_sign"], ["POSITIVO", "NEGATIVO"])
        for key, options in catalogs.items():
            for option in options:
                self.assertNotIn("(", option, key)


class SecurityTests(unittest.TestCase):
    def test_fases_de_los_roles(self):
        phases, roles = design_docs.role_phases()
        self.assertEqual(phases["FIELD_AGENT"], "F2")
        self.assertEqual(phases["APPROVER"], "F3")
        self.assertEqual(roles["GAN"], "GIS_ANALYST")
        self.assertNotIn("AUDITOR", phases)

    def test_celdas_de_la_matriz(self):
        matrix = design_docs.matrix()
        self.assertEqual(matrix["project"]["PROJECT_ADMIN"], ["READ", "CREATE", "UPDATE", "APPROVE", "READ_ALL_PROJECTS"])
        self.assertEqual(matrix["project"]["GIS_ANALYST"], ["READ", "u"])
        self.assertEqual(matrix["area_of_influence"]["PROJECT_MANAGER"], ["READ", "u", "APPROVE"])
        self.assertEqual(matrix["engagement_participant"]["FIELD_AGENT"], ["READ", "CREATE", "UPDATE", "DELETE"])
        self.assertEqual(matrix["person"]["PROJECT_ADMIN"], [])
        self.assertEqual(matrix["export_log"]["SOCIAL_MANAGER"], ["READ", "CREATE"])

    def test_campos_restringidos(self):
        restricted = {(obj, field): readers for obj, field, readers in design_docs.restricted_fields()}
        self.assertEqual(restricted[("stakeholder", "stance")], ["SOCIAL_MANAGER", "STAKEHOLDER_MANAGER"])
        self.assertEqual(len(restricted[("person", "given_names")]), 11)
        self.assertIn(("grievance", "person"), restricted)
        # DOWNLOAD_RESTRICTED es una acción, no un rol
        self.assertNotIn("DOWNLOAD_RESTRICTED", restricted[("ai_suggestion", "evidence_quote")])


class WorkflowStatesTests(unittest.TestCase):
    def test_estados_de_domain_model(self):
        states = design_docs.workflow_states()
        self.assertEqual(states["project"]["INITIAL"], [("draft", "Borrador")])
        self.assertEqual(states["stakeholder"]["FINAL"], [])
        self.assertEqual(states["stakeholder"]["INTERMEDIATE"][-1], ("retired", "Retirado"))
        self.assertEqual(states["ai_suggestion"], states["commitment_candidate"])


class TotalsTests(unittest.TestCase):
    """Totales fijos sobre los documentos reales: si una tabla cambia de forma, el lector devuelve menos y esto falla."""

    @classmethod
    def setUpClass(cls):
        cls.cards = design_docs.cards()

    def test_totales_de_las_fichas(self):
        cards = self.cards
        # 91 desde F3: las tablas de avisos de §18 se retiraron con la decisión D2 (ASM-012); 92 con la tabla fija
        # derived_version (ADR-0027)
        self.assertEqual(len(cards), 92)
        f2 = [c for c in cards.values() if c.phase == "F2"]
        fields = [f for c in f2 for f in c.fields if design_docs.field_phase(c, f) == "F2"]
        rels = [r for c in f2 for r in c.relationships
                if design_docs.relationship_phase(cards, c, r) == "F2" and not r["inverse"]]
        self.assertEqual((len(fields), len(rels)), (390, 113))
        # F3: 12 objetos y los 15 parámetros de project; sus relaciones, con strategic_alliance_agreement
        f3_fields = [f for c in cards.values() if c.phase <= "F3" for f in c.fields
                     if design_docs.field_phase(c, f) == "F3" and c.name in design_docs.matrix()]
        f3_rels = [r for c in cards.values() if c.phase <= "F3" for r in c.relationships
                   if design_docs.relationship_phase(cards, c, r) == "F3" and not r["inverse"]]
        self.assertEqual((len(f3_fields), len(f3_rels)), (114, 46))
        # 79 claves en §4 (item_kind desde el plan 5.0); las 45 de F2 son las de los campos ENUM que llegan en F2
        self.assertEqual(len(design_docs.enum_keys()), 79)
        self.assertEqual(len({f["enum"] for f in fields if f["type"] == "ENUM"}), 45)
        self.assertTrue(all(c.phase for c in cards.values()))
        self.assertTrue(all(c.fields for c in cards.values()))

    def test_totales_de_seguridad(self):
        phases, _ = design_docs.role_phases()
        self.assertEqual(len(phases), 14)
        self.assertEqual(len(design_docs.restricted_fields()), 24)
        for obj, row in design_docs.matrix().items():
            self.assertEqual(set(row), set(phases), obj)

    def test_totales_de_workflows(self):
        states = design_docs.workflow_states()
        self.assertEqual(len(states), 19)
        self.assertEqual(sum(len(v) for per in states.values() for v in per.values()), 113)
        for card in self.cards.values():
            if card.workflow:
                self.assertIn(card.name, states)


class WorkflowTransitionsTests(unittest.TestCase):
    def test_transiciones_de_workflows_md(self):
        self.assertEqual([len(design_docs.workflow_transitions(letter)) for letter in "ABCLO"], [9, 6, 14, 4, 2])
        assess = next(t for t in design_docs.workflow_transitions("B") if t["name"] == "assess")
        self.assertEqual((assess["label"], assess["from"], assess["to"]), ("Evaluar", "profiled", "assessed"))
        self.assertEqual(assess["roles"], ["STAKEHOLDER_MANAGER", "SOCIAL_MANAGER"])
        cancel = {t["name"]: t["roles"] for t in design_docs.workflow_transitions("C")}
        self.assertEqual(cancel["cancel_postponed"], cancel["cancel_planned"])
        self.assertEqual(len(cancel["cancel_postponed"]), 4)


class F3WorkflowsTests(unittest.TestCase):
    def test_wf_k_se_lee_por_objeto_y_wf_n_toma_sus_roles_operativos(self):
        candidate = design_docs.workflow_transitions("K", "commitment_candidate")
        self.assertEqual([t["name"] for t in candidate], ["accept", "reject", "mark_duplicate"])
        self.assertEqual([t["name"] for t in design_docs.workflow_transitions("K", "ai_suggestion")],
                         ["accept", "reject", "mark_duplicate"])
        with self.assertRaises(ValueError):
            design_docs.workflow_transitions("K")  # dos tablas: hay que nombrar el objeto
        task = design_docs.workflow_transitions("N")
        self.assertEqual(len(task), 8)
        self.assertEqual(len(task[0]["roles"]), 12)
        self.assertNotIn("READ_ONLY", task[0]["roles"])
        self.assertEqual([len(design_docs.workflow_transitions(letter)) for letter in "DI"], [17, 10])
        # todas las filas de WF-N comparten la lista del bullet
        self.assertTrue(all(t["roles"] == task[0]["roles"] for t in task))
        # transiciones reales fijadas
        wf_d = {t["name"]: t for t in design_docs.workflow_transitions("D")}
        self.assertEqual((wf_d["approve_direct"]["from"], wf_d["approve_direct"]["to"], wf_d["approve_direct"]["roles"]),
                         ("draft", "approved", ["COMMITMENT_MANAGER", "SOCIAL_MANAGER"]))
        self.assertEqual(wf_d["approve_senior"]["roles"], ["PROJECT_MANAGER"])
        start = design_docs.workflow_transitions("I")[0]
        self.assertEqual((start["name"], start["from"], start["to"], start["roles"]),
                         ("start_negotiation", "draft", "in_negotiation", ["SOCIAL_MANAGER", "COMMUNITY_RELATIONS_OFFICER"]))
        # WF-K: los roles de cada objeto difieren
        for obj, roles in (("commitment_candidate", ["COMMITMENT_MANAGER", "SOCIAL_MANAGER", "COMMUNITY_RELATIONS_OFFICER"]),
                           ("ai_suggestion", ["SOCIAL_MANAGER", "COMMUNITY_RELATIONS_OFFICER", "STAKEHOLDER_MANAGER",
                                              "COMMITMENT_MANAGER", "GRIEVANCE_MANAGER", "SOCIAL_RISK_MANAGER"])):
            self.assertTrue(all(t["roles"] == roles for t in design_docs.workflow_transitions("K", obj)))

    def test_automatizaciones_y_tipos_de_documento(self):
        automations = {a["id"]: a for a in design_docs.automations()}
        self.assertEqual(len(automations), 12)
        self.assertEqual((automations["AUT-01"]["object"], automations["AUT-01"]["trigger"], automations["AUT-01"]["state"]),
                         ("commitment", "STATE_ENTERED", "approved"))
        self.assertEqual((automations["AUT-03"]["phase"], automations["AUT-03"]["after"]), ("F3", "UPS-23"))
        # ASM-098: el cargo de queja y las cartas de respuesta esperan a UPS-23, porque las QCS restringen person
        for aut in ("AUT-06", "AUT-07", "AUT-08"):
            self.assertEqual((automations[aut]["phase"], automations[aut]["after"]), ("F4", "UPS-23"), aut)
        self.assertEqual(automations["AUT-11"]["trigger"], "RECORD_UPDATED")
        types = {t["name"]: t for t in design_docs.document_types()}
        self.assertEqual(types["meeting-minutes"], {"label": "Minuta de reunión", "name": "meeting-minutes", "prefix": "MR",
                                                    "object": "engagement", "phase": "F3", "after": None})
        self.assertEqual(types["delivery-certificate"]["after"], "UPS-23")
        self.assertEqual([(t["name"], t["prefix"], t["object"], t["phase"], t["after"]) for t in design_docs.document_types()],
                         [("delivery-certificate", "AE", "delivery", "F3", "UPS-23"),
                          ("meeting-minutes", "MR", "engagement", "F3", None),
                          ("attendance-list", "LA", "engagement", "F3", None),
                          ("grievance-receipt", "CQ", "grievance", "F4", "UPS-23"),
                          ("inquiry-answer-letter", "CC", "inquiry", "F4", "UPS-23"),
                          ("request-answer-letter", "CS", "stakeholder_request", "F4", "UPS-23")])

    def test_indexed_e_indexes_en_las_otras_tres_fichas(self):
        cards = design_docs.cards()
        for name in ("grievance", "inquiry", "stakeholder_request"):
            self.assertEqual((cards[name].indexed, cards[name].indexes), (["received_at"], [["project", "deadline_status"]]))


class F4WorkflowsTests(unittest.TestCase):
    def test_wf_e1_a_e3_se_leen_por_su_subseccion_y_wf_f_j_y_m_por_su_seccion(self):
        self.assertEqual([len(design_docs.workflow_transitions(letter)) for letter in ("E1", "E2", "E3", "F", "J", "M")],
                         [22, 5, 8, 9, 6, 3])
        grievance = {t["name"]: t for t in design_docs.workflow_transitions("E1", "grievance")}
        self.assertEqual((grievance["reopen"]["from"], grievance["reopen"]["to"]), ("resolved", "in_assessment"))
        self.assertEqual(grievance["resolve_high_severity"]["roles"], ["APPROVER"])
        self.assertEqual(grievance["verify"]["roles"], ["GRIEVANCE_MANAGER", "SOCIAL_MANAGER"])
        self.assertEqual(grievance["close_reclassified_in_assessment"]["label"], "Cerrar por reclasificación")
        # la consulta termina antes de la solicitud: la subsección de WF-E2 no se come la tabla de WF-E3
        self.assertEqual([t["name"] for t in design_docs.workflow_transitions("E2", "inquiry")],
                         ["answer", "refer", "close", "close_referred", "close_reclassified"])
        request = {t["name"]: t for t in design_docs.workflow_transitions("E3", "stakeholder_request")}
        # SOCIAL_INVESTMENT_MANAGER llega en F5: el lector lo devuelve si la celda lo nombra y test_model.py lo filtra por
        # fase, así que aquí no cuenta
        for name, roles in (("accept", ["SOCIAL_MANAGER", "COMMITMENT_MANAGER"]),
                            ("close_accepted", ["COMMITMENT_MANAGER", "SOCIAL_MANAGER"])):
            self.assertEqual([r for r in request[name]["roles"] if r != "SOCIAL_INVESTMENT_MANAGER"], roles, name)
        risk = {t["name"]: t for t in design_docs.workflow_transitions("F", "social_risk")}
        self.assertEqual((risk["assess"]["from"], risk["assess"]["roles"]), ("identified", ["SOCIAL_RISK_MANAGER"]))
        warning = {t["name"]: t for t in design_docs.workflow_transitions("J", "early_warning")}
        self.assertEqual(warning["confirm"]["roles"], ["SOCIAL_RISK_MANAGER", "SOCIAL_MANAGER"])
        self.assertEqual(warning["dismiss"]["roles"], warning["confirm"]["roles"])
        incident = design_docs.workflow_transitions("M", "incident")
        self.assertEqual([(t["name"], t["from"], t["to"]) for t in incident],
                         [("start_review", "reported", "under_review"), ("start_response", "under_review", "responding"),
                          ("close", "responding", "closed")])

    def test_la_seccion_comun_de_wf_e_pide_el_objeto_y_una_subseccion_que_no_existe_falla(self):
        with self.assertRaises(ValueError):
            design_docs.workflow_transitions("E")  # tres tablas: hay que nombrar el objeto
        self.assertEqual(len(design_docs.workflow_transitions("E", "inquiry")), 5)
        with self.assertRaises(ValueError):
            design_docs.workflow_transitions("E4")

    def test_las_fichas_de_f4_traen_lo_que_suma_el_plan_4_0(self):
        cards = design_docs.cards()
        self.assertEqual(cards["grievance"].actions, ["APPROVE", "EXPORT", "READ_CONFIDENTIAL"])
        for name in ("inquiry", "stakeholder_request"):
            self.assertEqual(cards[name].actions, ["EXPORT", "READ_CONFIDENTIAL"], name)
        self.assertEqual(cards["early_warning"].unique, ["generator_key"])
        self.assertEqual(cards["early_warning"].actions, ["CONFIRM_ALERT"])
        referred = next(f for f in cards["engagement_question"].fields if f["name"] == "referred_at")
        self.assertEqual((referred["type"], referred["derived"], design_docs.field_phase(cards["engagement_question"], referred)),
                         ("DATETIME", True, "F4"))
        self.assertEqual((cards["risk_assessment"].flags["appendOnly"], cards["risk_assessment"].flags["apiOnly"]), (True, True))
        self.assertNotIn("CAMBIO DE POSTURA", design_docs.catalogs()["warning_rule_kind"])
        self.assertEqual(len(design_docs.catalogs()["warning_rule_kind"]), 6)


class F5CardsTests(unittest.TestCase):
    """Lo que el plan 5.0 suma a los documentos y el plan 5.1 lleva al modelo (§4 de la especificación de F5)."""

    def test_las_fichas_de_f5_traen_lo_que_suma_el_plan_5_0(self):
        cards = design_docs.cards()
        # decisión 3 (ASM-112): el tipo de cada ítem es lo que leen los lineamientos al aprobar
        kind = next(f for f in cards["contribution_item"].fields if f["name"] == "item_kind")
        self.assertEqual((kind["type"], kind["enum"], kind["required"]), ("ENUM", "item_kind", True))
        self.assertEqual(design_docs.catalogs()["item_kind"], ["BIEN", "SERVICIO", "DINERO", "MANO DE OBRA"])
        topic = next(r for r in cards["social_contribution"].relationships if r["name"] == "contribution_topic")
        self.assertEqual((topic["type"], topic["field"], topic["target"], topic["required"]),
                         ("MANY_TO_ONE", "topic", "topic", False))
        # la M2M de los temas priorizados llega en F5 aunque project y topic sean de F2
        priority = next(r for r in cards["project"].relationships if r["name"] == "project_priority_topic")
        self.assertEqual((priority["type"], priority["target"], priority["inverse"]), ("MANY_TO_MANY", "topic", False))
        self.assertEqual(design_docs.relationship_phase(cards, cards["project"], priority), "F5")
        inverse = next(r for r in cards["topic"].relationships if r["name"] == "project_priority_topic")
        self.assertTrue(inverse["inverse"])
        # ASM-116: el acta de recepción espera a UPS-23, como el acta de entrega
        aut04 = next(a for a in design_docs.automations() if a["id"] == "AUT-04")
        self.assertEqual((aut04["object"], aut04["phase"], aut04["after"]), ("delivery", "F5", "UPS-23"))

    @classmethod
    def setUpClass(cls):
        cls.cards = design_docs.cards()

    def test_las_fichas_de_bc_10_traen_lo_que_suma_el_plan_5_0(self):
        kind = next(f for f in self.cards["contribution_item"].fields if f["name"] == "item_kind")
        self.assertEqual((kind["type"], kind["enum"], kind["required"]), ("ENUM", "item_kind", True))
        self.assertEqual(design_docs.catalogs()["item_kind"], ["BIEN", "SERVICIO", "DINERO", "MANO DE OBRA"])
        topic = next(r for r in self.cards["social_contribution"].relationships if r["name"] == "contribution_topic")
        self.assertEqual((topic["type"], topic["field"], topic["target"], topic["required"]),
                         ("MANY_TO_ONE", "topic", "topic", False))
        priority = next(r for r in self.cards["project"].relationships if r["name"] == "project_priority_topic")
        self.assertEqual((priority["type"], priority["target"]), ("MANY_TO_MANY", "topic"))
        self.assertEqual(design_docs.relationship_phase(self.cards, self.cards["project"], priority), "F5")
        self.assertEqual([c for c in self.cards if self.cards[c].phase == "F5"],
                         ["social_contribution", "contribution_item", "receipt", "social_program", "social_initiative",
                          "beneficiary_group", "program_budget", "program_indicator", "program_measurement",
                          "program_milestone"])

    def test_el_unico_de_la_entrega_es_unique_en_su_relacion_con_el_aporte(self):
        # wasichai 1.0.0 rechaza un uniqueConstraints de un solo campo (FieldSets.normalize, al menos dos) y apply.py
        # también: el único de delivery.social_contribution irá como «`unique` en», y apply.py lo pone en la relación.
        # el plan 5.0 lo dejó como texto «desde F5»; con el modelo de F5 (plan 5.1, Tarea 3) es la forma leída
        delivery = self.cards["delivery"]
        self.assertEqual((delivery.unique, delivery.unique_constraints), (["social_contribution"], []))
        self.assertEqual(delivery.exactly_one, [["commitment", "social_contribution"]])

    def test_wf_g_y_wf_p_y_los_roles_de_f5_en_wf_e3(self):
        self.assertEqual([len(design_docs.workflow_transitions(letter)) for letter in ("G", "P")], [14, 8])
        request = {t["name"]: t for t in design_docs.workflow_transitions("E3", "stakeholder_request")}
        for name in ("accept", "close_accepted"):
            self.assertIn("SOCIAL_INVESTMENT_MANAGER", request[name]["roles"], name)

    def test_la_matriz_corrige_los_permisos_que_no_cerraban(self):
        # elección 15 de la especificación de F5 y §3.1 (temas priorizados)
        matrix = design_docs.matrix()
        for role in ("SOCIAL_MANAGER", "COMMUNITY_RELATIONS_OFFICER", "SOCIAL_INVESTMENT_MANAGER"):
            self.assertEqual(matrix["contribution_item"][role], ["READ", "CREATE", "UPDATE", "DELETE"], role)
        self.assertEqual(matrix["receipt"]["COMMUNITY_RELATIONS_OFFICER"], ["READ", "CREATE", "UPDATE"])
        # u sobre topic para PROJECT_ADMIN y PROJECT_MANAGER llega a la celda con el modelo de F5 (plan 5.1, Tarea 3)
        for role in ("PROJECT_ADMIN", "PROJECT_MANAGER"):
            self.assertIn("u", matrix["topic"][role], role)
        self.assertEqual(matrix["social_contribution"]["SOCIAL_MANAGER"][-1], "APPROVE_OVER_CAP")


class BrokenDocsTests(unittest.TestCase):
    """El lector falla, no devuelve de menos, cuando una tabla de un documento cambia de forma."""

    def setUp(self):
        self.tmp = tempfile.mkdtemp()
        self.addCleanup(shutil.rmtree, self.tmp)
        self.original = design_docs.DOCS
        self.addCleanup(setattr, design_docs, "DOCS", self.original)
        for name in os.listdir(self.original):
            if name.endswith(".md"):
                shutil.copy(os.path.join(self.original, name), self.tmp)
        design_docs.DOCS = self.tmp

    def edit(self, name, change):
        path = os.path.join(self.tmp, name)
        with open(path, encoding="utf-8") as f:
            lines = f.read().split("\n")
        change(lines)
        with open(path, "w", encoding="utf-8") as f:
            f.write("\n".join(lines))

    def index(self, lines, prefix, after=0):
        return next(i for i, line in enumerate(lines) if i >= after and line.startswith(prefix))

    def test_las_tablas_rotas_hacen_fallar_al_lector(self):
        design_docs.cards()  # la copia sin tocar se lee

        def rename_header(lines):
            lines[self.index(lines, "| Campo |")] = "| Campos |"

        def blank_inside(lines):
            start = self.index(lines, "| Campo |", self.index(lines, "### `engagement`"))
            lines.insert(start + 4, "")

        def drop_separator(lines):
            del lines[self.index(lines, "| Campo |") + 1]

        for name, change in (("renamed header", rename_header), ("blank line", blank_inside),
                             ("dropped separator", drop_separator)):
            with self.subTest(name):
                self.edit("database-model.md", change)
                with self.assertRaises(ValueError):
                    design_docs.cards()
                shutil.copy(os.path.join(self.original, "database-model.md"), self.tmp)

        def short_row(lines):
            i = self.index(lines, "| Objeto | PAD |")
            lines[i + 2] = lines[i + 2].rsplit("|", 2)[0] + "|"

        self.edit("security.md", short_row)
        with self.assertRaises(ValueError):
            design_docs.matrix()

        def bad_state(lines):
            i = self.index(lines, "| WF | Objeto | Inicial |")
            lines[i + 2] = lines[i + 2].replace("`draft`", "draft")  # el estado inicial pierde sus comillas

        self.edit("domain-model.md", bad_state)
        with self.assertRaises(ValueError):
            design_docs.workflow_states()

    def test_las_tablas_de_transiciones_rotas_hacen_fallar_al_lector(self):
        design_docs.workflow_transitions("A")  # la copia sin tocar se lee

        def rename_header(lines):
            lines[self.index(lines, "| Transición | De | A | Roles |", self.index(lines, "## WF-A ·"))] = "| Transición | De |"

        def drop_row_cell(lines):
            i = self.index(lines, "| `approve` (Aprobar) |")
            lines[i] = lines[i].replace("| `PROJECT_ADMIN` |", "|", 1)

        def empty_roles(lines):
            i = self.index(lines, "| `close` (Cerrar) |", self.index(lines, "## WF-A ·"))
            lines[i] = lines[i].replace("`PROJECT_ADMIN`", "Los administradores", 1)

        def bad_name(lines):
            i = self.index(lines, "| `close` (Cerrar) |", self.index(lines, "## WF-A ·"))
            lines[i] = lines[i].replace("`close` (Cerrar)", "close (Cerrar)", 1)

        def rename_section(lines):
            lines[self.index(lines, "## WF-A ·")] = "## WF-A - Proyecto"

        for name, change in (("renamed header", rename_header), ("short row", drop_row_cell), ("no roles", empty_roles),
                             ("bad name", bad_name), ("renamed section", rename_section)):
            with self.subTest(name):
                self.edit("workflows.md", change)
                with self.assertRaises(ValueError):
                    design_docs.workflow_transitions("A")
                shutil.copy(os.path.join(self.original, "workflows.md"), self.tmp)

    def test_una_subseccion_de_wf_e_repetida_o_sin_su_titulo_hace_fallar_al_lector(self):
        design_docs.workflow_transitions("E1", "grievance")  # la copia sin tocar se lee

        def repeated(lines):
            lines.insert(self.index(lines, "### WF-E2 ·"), "### WF-E1 · Queja (otra vez)")

        def renamed(lines):
            lines[self.index(lines, "### WF-E1 ·")] = "### WF-E1 - Queja"

        for name, change in (("repeated", repeated), ("renamed", renamed)):
            with self.subTest(name):
                self.edit("workflows.md", change)
                with self.assertRaises(ValueError):
                    design_docs.workflow_transitions("E1", "grievance")
                shutil.copy(os.path.join(self.original, "workflows.md"), self.tmp)

    def test_las_tablas_de_automatizaciones_y_tipos_rotas_hacen_fallar_al_lector(self):
        design_docs.automations()
        design_docs.document_types()  # la copia sin tocar se lee

        def short_automation(lines):
            i = self.index(lines, "| AUT-01 |")
            lines[i] = lines[i].rsplit("|", 2)[0] + "|"

        def no_phase(lines):
            i = self.index(lines, "| Minuta de reunión |")
            lines[i] = lines[i].replace("| F3 |", "| tercera |", 1)

        def no_roles_bullet(lines):
            i = self.index(lines, "- **Roles operativos:**")
            lines[i] = lines[i].replace("Roles operativos", "Roles de la tarea", 1)

        def blank_in_automations(lines):
            lines.insert(self.index(lines, "| AUT-05 |"), "")

        def blank_in_types(lines):
            lines.insert(self.index(lines, "| Lista de asistentes |"), "")

        def rename_aut_header(lines):
            i = self.index(lines, "| ID | Nombre técnico | Objeto | Disparador |")
            lines[i] = lines[i].replace("| ID |", "| Id |", 1)

        def rename_types_header(lines):
            i = self.index(lines, "| Tipo de documento | Nombre técnico |")
            lines[i] = lines[i].replace("Tipo de documento", "Tipo", 1)

        def bad_phase_text(lines):
            i = self.index(lines, "| Minuta de reunión |")
            lines[i] = lines[i].replace("| F3 |", "| F3, luego algo |", 1)

        for name, change, reader in (("blank line in automations", blank_in_automations, design_docs.automations),
                                     ("blank line in document types", blank_in_types, design_docs.document_types),
                                     ("automations header renamed", rename_aut_header, design_docs.automations),
                                     ("document types header renamed", rename_types_header, design_docs.document_types),
                                     ("unknown text after the phase", bad_phase_text, design_docs.document_types),
                                     ("short automation row", short_automation, design_docs.automations),
                                     ("document type without phase", no_phase, design_docs.document_types),
                                     ("roles bullet renamed", no_roles_bullet, lambda: design_docs.workflow_transitions("N"))):
            with self.subTest(name):
                self.edit("workflows.md", change)
                with self.assertRaises(ValueError):
                    reader()
                shutil.copy(os.path.join(self.original, "workflows.md"), self.tmp)

    def test_el_bullet_de_tipos_de_archivo_y_los_campos_file_van_de_la_mano(self):
        design_docs.cards()

        def no_bullet(lines):
            i = self.index(lines, "- **Tipos de archivo:**")
            lines[i] = lines[i].replace("Tipos de archivo", "Tipos", 1)

        def wrong_field(lines):
            i = self.index(lines, "- **Tipos de archivo:** `file`")
            lines[i] = lines[i].replace("`file`", "`import_batch_id`", 1)

        for name, change in (("FILE field without bullet", no_bullet), ("bullet names a non-FILE field", wrong_field)):
            with self.subTest(name):
                self.edit("database-model.md", change)
                with self.assertRaises(ValueError):
                    design_docs.cards()
                shutil.copy(os.path.join(self.original, "database-model.md"), self.tmp)

    def test_una_letra_de_permiso_desconocida_falla(self):
        with self.assertRaises(ValueError):
            design_docs._grants("RX")


if __name__ == "__main__":
    unittest.main()
