"""Pruebas del conjunto ficticio de model/demo/ (épica #102): valida contra model.json y tiene el volumen de DOC-01. El T2
(#153, 07-entregable-t2.json) va después del corte del T1, así que sus registros no cambian lo que el T1 cuenta.

Correr: cd model && python3 -m unittest -v test_demo_data
"""
import json
import os
import re
import unittest

import demo_check
import load_demo

HERE = os.path.dirname(os.path.abspath(__file__))


class DemoDataTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.dataset = load_demo.read_dataset(os.path.join(HERE, "demo"))
        with open(os.path.join(HERE, "model.json"), encoding="utf-8") as f:
            cls.model = json.load(f)

    T2_FILE = "07-entregable-t2.json"

    def records(self, obj):
        return [r for c in self.dataset.collections if c["object"] == obj for r in c["records"]]

    def t1_records(self, obj):
        """Los registros del conjunto del T1: todos menos los que el T2 suma."""
        return [r for c in self.dataset.collections if c["object"] == obj and c["_file"] != self.T2_FILE
                for r in c["records"]]

    def t2_records(self, obj):
        return [r for c in self.dataset.collections if c["object"] == obj and c["_file"] == self.T2_FILE
                for r in c["records"]]

    def deliverable(self, key):
        return next(r for r in self.records("deliverable") if r["key"] == key)

    def test_valida_contra_el_modelo(self):
        self.assertEqual(demo_check.validate(self.dataset, self.model), [])

    def test_quince_localidades_cuatro_en_el_aids(self):
        self.assertEqual(len({r["key"] for r in self.records("locality")}), 15)
        aids = [r for r in self.records("area_of_influence_member") if r["attributes"]["area_of_influence"] == "@aids"]
        self.assertEqual(sum(1 for r in aids if "locality" in r["attributes"]), 4)

    def test_aiis_con_once_localidades_y_el_distrito(self):
        aiis = [r for r in self.records("area_of_influence_member") if r["attributes"]["area_of_influence"] == "@aiis"]
        self.assertEqual(sum(1 for r in aiis if "locality" in r["attributes"]), 11)
        self.assertTrue(any(r["attributes"].get("administrative_unit") == "@au-distrito" for r in aiis))

    def test_las_dos_areas_se_aprueban(self):
        approved = {r["key"] for r in self.records("area_of_influence")
                    if any(c["command"] == "approve_area" for c in r.get("commands", []))}
        self.assertEqual(approved, {"aids", "aiis"})

    PALLCA = {"loc-santarosa", "loc-esperanza", "loc-cruzpata", "loc-antaccasa"}

    @staticmethod
    def inside(point, polygon):
        """Punto dentro de un polígono simple, en km (cruce de rayos)."""
        x, y = point
        hit = False
        for (x1, y1), (x2, y2) in zip(polygon, polygon[1:] + polygon[:1]):
            if (y1 > y) != (y2 > y) and x < (x2 - x1) * (y - y1) / (y2 - y1) + x1:
                hit = not hit
        return hit

    def test_cada_localidad_cae_en_su_microcuenca(self):
        basins = {r["key"]: r["geometries"]["boundary"]["polygon"] for r in self.records("environmental_feature")}
        for r in self.records("locality"):
            basin = "mc-pallca" if r["key"] in self.PALLCA else "mc-ichu"
            self.assertTrue(self.inside(r["geometries"]["location"]["point"], basins[basin]), r["key"])

    def test_una_asignacion_por_usuario(self):
        users = {u["key"] for u in self.dataset.users}
        assigned = {r["attributes"]["member_user"] for r in self.records("project_assignment")}
        self.assertEqual(len(users), 6)
        self.assertEqual(assigned, users)

    def test_un_perfil_por_localidad_del_estudio(self):
        profiles = self.records("locality_profile")
        self.assertEqual({r["attributes"]["locality"] for r in profiles}, {"@" + r["key"] for r in self.records("locality")})
        self.assertTrue(all(r["attributes"]["study"] == "@study" for r in profiles))

    def test_tablas_del_capitulo_5_con_volumen(self):
        self.assertGreaterEqual(len(self.records("school")), 20)
        self.assertGreaterEqual(len(self.records("festivity")), 15)
        self.assertGreaterEqual(len(self.records("road_access")), 8)
        local = [r for r in self.records("organization") if "locality" in r["attributes"]]
        self.assertGreaterEqual(len(local), 50)

    def test_todo_perfil_lleva_cultivos(self):
        for r in self.records("locality_profile"):
            self.assertTrue(r["attributes"].get("crops"), r["key"])

    def test_programas_y_conflictos(self):
        self.assertEqual(len(self.records("state_program")), 4)
        conflicts = self.records("local_conflict")
        self.assertEqual(sorted(r["attributes"]["conflict_type"] for r in conflicts), ["AGUA", "LIMITES", "SEPARACION"])

    def test_cada_localidad_tiene_una_via(self):
        self.assertEqual({r["attributes"]["locality"] for r in self.records("road_access")},
                         {"@" + r["key"] for r in self.records("locality")})

    # ---- 04-grupos.json: grupos de interés, percepciones y temas clave ------------------------------------------

    GROUPS = {
        "AIDS": {"ESTRATEGICO": 12, "POLITICO": 4, "PARA MONITOREO": 5, "VULNERABLE": 1},
        "AIIS": {"ESTRATEGICO": 21, "POLITICO": 14, "PARA MONITOREO": 14, "VULNERABLE": 7},
        "REGIONAL": {"ESTRATEGICO": 1, "POLITICO": 4, "PARA MONITOREO": 2},
    }

    @staticmethod
    def assessments(record):
        return [c["attributes"] for c in record.get("commands", []) if c["command"] == "assessment"]

    def test_grupos_por_ambito_y_por_grupo_de_influencia(self):
        stakeholders = self.records("stakeholder")
        by = {}
        for r in stakeholders:
            key = (r["attributes"]["stakeholder_scope"], r["_expect"]["influence_group"])
            by[key] = by.get(key, 0) + 1
        self.assertEqual(by[("AIDS", "ESTRATEGICO")], 12)
        self.assertEqual(by[("AIIS", "ESTRATEGICO")], 21)
        self.assertEqual(by[("REGIONAL", "ESTRATEGICO")], 1)
        self.assertEqual(len(stakeholders), 85)
        expected = {(scope, group): n for scope, groups in self.GROUPS.items() for group, n in groups.items()}
        self.assertEqual(by, expected)

    def test_seguimiento_con_cambios_de_postura(self):
        changed = 0
        for r in self.records("stakeholder"):
            stances = [c["attributes"].get("stance") for c in r.get("commands", []) if c["command"] == "assessment"]
            changed += any(a != b for a, b in zip(stances, stances[1:]))
        self.assertGreaterEqual(changed, 8)

    def test_cada_persona_con_cargo_vigente(self):
        holders = {r["attributes"]["person"] for r in self.records("position") if r["attributes"]["position_status"] == "VIGENTE"}
        people = {r["attributes"]["person"] for r in self.records("stakeholder")}
        self.assertEqual(people - holders, set())

    def test_un_solo_cargo_vigente_por_persona(self):
        vigentes = [r["attributes"]["person"] for r in self.records("position") if r["attributes"]["position_status"] == "VIGENTE"]
        self.assertEqual(len(vigentes), len(set(vigentes)))

    def test_personas_con_al_menos_un_tercio_de_mujeres(self):
        people = self.records("person")
        women = sum(1 for r in people if r["attributes"]["sex"] == "FEMENINO")
        self.assertGreaterEqual(len(people), 85)
        self.assertGreaterEqual(women / len(people), 0.30)

    def test_linea_de_base_en_el_trabajo_de_campo(self):
        statuses = {}
        for r in self.records("stakeholder"):
            first = self.assessments(r)[0]
            self.assertTrue("2026-04-14" <= first["assessment_date"] <= "2026-04-29", r["key"])
            status = r["attributes"]["interview_status"]
            statuses[status] = statuses.get(status, 0) + 1
            self.assertEqual(first["basis"], "ENTREVISTA" if status == "ENTREVISTADO" else "INFORMACION SECUNDARIA", r["key"])
            self.assertEqual(r["attributes"]["owner_user"], "relacionista")
            self.assertEqual("locality" in r["attributes"], r["attributes"]["stakeholder_scope"] != "REGIONAL", r["key"])
        self.assertEqual(statuses, {"ENTREVISTADO": 77, "NO UBICADO": 5, "SE NEGO": 3})

    def test_treinta_con_seguimiento_mensual(self):
        months = ["2026-05-20", "2026-06-20", "2026-07-20", "2026-08-20", "2026-09-20"]
        followed = [r for r in self.records("stakeholder") if len(self.assessments(r)) > 1]
        self.assertEqual(len(followed), 30)
        for r in followed:
            self.assertEqual([a["assessment_date"] for a in self.assessments(r)[1:]], months, r["key"])

    def test_mas_oposicion_en_el_aiis_que_en_el_aids(self):
        def share(scope):
            rows = [r for r in self.records("stakeholder") if r["attributes"]["stakeholder_scope"] == scope]
            against = sum(1 for r in rows if self.assessments(r)[0]["stance"] == "EN CONTRA")
            return against / len(rows)
        self.assertGreater(share("AIIS"), share("AIDS"))

    def test_doce_reglas_de_priorizacion_sin_tipo(self):
        rules = self.records("prioritization_rule")
        self.assertEqual(len(rules), 12)
        combos = {(r["attributes"]["influence_group"], r["attributes"]["stance"], r["attributes"]["stakeholder_scope"]) for r in rules}
        self.assertEqual(len(combos), 12)
        self.assertTrue(all("stakeholder_type" not in r["attributes"] for r in rules))

    def test_percepciones_por_tema_con_citas(self):
        perceptions = self.records("perception")
        self.assertEqual(len(perceptions), 24)
        for subject in ("MINERIA", "PROYECTO", "EMPRESA"):
            rows = [r["attributes"] for r in perceptions if r["attributes"]["subject"] == subject]
            self.assertEqual(len(rows), 8, subject)
            self.assertEqual({a["polarity"] for a in rows}, {"POSITIVA", "NEGATIVA"}, subject)
        quoted = [r["attributes"] for r in perceptions if r["attributes"].get("quote")]
        self.assertEqual(len(quoted), 12)
        self.assertTrue(all(a.get("attributed_role") for a in quoted))

    def test_cuatro_temas_clave(self):
        themes = self.records("key_theme")
        self.assertEqual(len(themes), 4)
        for r in themes:
            a = r["attributes"]
            self.assertEqual((a["project"], a["study"]), ("@proj", "@study"))
            self.assertTrue(a["topic"].startswith("@topic-") and a["description"] and a["risks"] and a["opportunities"])

    # ---- 05-estrategia.json: estrategia, interacciones, compromisos, riesgos y cierres ----------------------------

    AIDS_LOCALITIES = {"@loc-huaylla", "@loc-quishuar", "@loc-llamapampa", "@loc-ccollpa"}

    def interviews(self):
        return [r for r in self.records("engagement") if r["attributes"]["engagement_type"] == "ENTREVISTA"]

    def test_entrevistas_del_trabajo_de_campo(self):
        interviews = self.interviews()
        self.assertEqual(len(interviews), 72)
        self.assertTrue(all("2026-04-13" <= r["attributes"]["held_at"][:10] <= "2026-04-30" for r in interviews))
        self.assertTrue(all(r["state"] == "held" and r["children"] for r in interviews))

    def test_entrevistas_por_ambito(self):
        by = {"AIDS": 0, "AIIS": 0, "SIN LOCALIDAD": 0}
        for r in self.interviews():
            locality = r["attributes"].get("locality")
            by["SIN LOCALIDAD" if locality is None else "AIDS" if locality in self.AIDS_LOCALITIES else "AIIS"] += 1
        self.assertEqual(by, {"AIDS": 21, "AIIS": 39, "SIN LOCALIDAD": 12})

    def test_cada_entrevistado_una_vez_en_la_fecha_de_su_linea_de_base(self):
        stakeholders = {"@" + r["key"]: r for r in self.records("stakeholder")}
        seen = []
        for r in self.interviews():
            a = r["attributes"]
            self.assertEqual((a["study"], r["transitions"]), ("@study", ["register_held_unplanned"]), r["key"])
            self.assertTrue(a["study_instrument"].startswith("@inst-ent-"), r["key"])
            for child in r["children"]:
                c = child["attributes"]
                self.assertTrue(c["attended"] and c["represented_people_count"] == 1, r["key"])
                stakeholder = stakeholders[c["stakeholder"]]
                self.assertEqual(stakeholder["attributes"]["interview_status"], "ENTREVISTADO", r["key"])
                self.assertEqual(self.assessments(stakeholder)[0]["assessment_date"], a["held_at"][:10], r["key"])
                if a.get("locality") is not None:
                    self.assertEqual(a["locality"], stakeholder["attributes"]["locality"], r["key"])
                seen.append(c["stakeholder"])
        self.assertEqual(len(seen), len(set(seen)))
        interviewed = {k for k, r in stakeholders.items() if r["attributes"]["interview_status"] == "ENTREVISTADO"}
        self.assertEqual(set(seen), interviewed)

    def test_fichas_de_observacion_en_el_aids(self):
        sheets = [r for r in self.records("engagement") if r["attributes"]["engagement_type"] == "FICHA DE OBSERVACION"]
        self.assertEqual({r["attributes"]["locality"] for r in sheets}, self.AIDS_LOCALITIES)
        self.assertEqual(len(sheets), 4)

    def test_interacciones_del_periodo(self):
        rows = [r for r in self.t1_records("engagement") if r["attributes"].get("study") is None]
        self.assertEqual(len(rows), 24)
        for r in rows:
            self.assertTrue("2026-05-01" <= r["attributes"]["held_at"][:10] <= "2026-09-30", r["key"])
            self.assertTrue(r["links"]["engagement_stakeholder"], r["key"])
            self.assertTrue(any(c["attributes"].get("stakeholder") for c in r["children"]), r["key"])
            self.assertEqual(r["state"], "held", r["key"])
        months = {r["attributes"]["held_at"][:7] for r in rows}
        self.assertEqual(months, {"2026-05", "2026-06", "2026-07", "2026-08", "2026-09"})

    def test_plan_con_diez_actividades(self):
        plans = self.records("engagement_plan")
        self.assertEqual(len(plans), 1)
        self.assertEqual(plans[0]["attributes"]["meetings_per_month_target"], 4)
        children = plans[0]["children"]
        activities = [c["attributes"] for c in children if c["object"] == "planned_activity"]
        self.assertEqual(len(activities), 10)
        self.assertEqual(sum(1 for a in activities if a["completed"]), 6)
        self.assertTrue(all("2026-05-01" <= a["planned_date"] <= "2026-11-30" for a in activities))
        self.assertTrue(all(a["planned_date"] <= "2026-09-30" for a in activities if a["completed"]))

    def test_respuestas_alineadas_aprobadas_y_pendientes(self):
        # el informe publica solo las aprobadas (DiagnosisStrategy.alignedAnswers): hay de las dos
        answers = [r["attributes"] for r in self.records("aligned_answer")]
        self.assertGreaterEqual(len(answers), 4)
        self.assertTrue(any(a["approved"] for a in answers))
        self.assertTrue(any(not a["approved"] for a in answers))
        self.assertTrue(all(a["approved_date"] for a in answers if a["approved"]))

    def test_mensajes_alianzas_e_impactos(self):
        self.assertEqual(len(self.records("key_message")), 6)
        alliances = self.records("strategic_alliance")
        self.assertEqual({r["attributes"]["organization"] for r in alliances},
                         {"@org-diocesis", "@org-municipalidad-provincial", "@org-universidad", "@org-gobierno-regional",
                          "@org-ong"})
        self.assertTrue(all("formalized" not in r["attributes"] for r in alliances))
        impacts = {(r["attributes"]["activity_type"], r["attributes"]["impact_type"]) for r in self.records("potential_impact")}
        self.assertEqual(impacts, {(a, t) for a in ("CATEO", "PERFORACION") for t in ("AMBIENTAL", "SOCIAL")})

    def test_visitas_de_campo(self):
        visits = sorted(self.t1_records("field_visit"), key=lambda r: r["attributes"]["actual_start_date"])
        self.assertEqual([(r["attributes"]["actual_start_date"], r["attributes"]["actual_end_date"]) for r in visits],
                         [("2026-04-13", "2026-04-20"), ("2026-04-22", "2026-04-30")])
        self.assertTrue(all(r["transitions"] == ["start", "complete"] and r["state"] == "completed" for r in visits))

    def test_compromisos_en_varios_estados(self):
        states = sorted({r.get("state", "draft") for r in self.records("commitment")})
        self.assertEqual(states, ["approved", "blocked", "cancelled", "draft", "in_progress"])

    def test_compromisos_con_la_matriz_y_bajo_el_tope(self):
        commitments = self.records("commitment")
        self.assertEqual(len(commitments), 12)
        counts = {}
        approved = 0
        for r in commitments:
            a = r["attributes"]
            counts[r["state"]] = counts.get(r["state"], 0) + 1
            for name in ("description", "objective", "resource_type", "quantity", "unit", "request_medium", "agreed_date",
                         "due_date", "beneficiaries_count", "amount"):
                self.assertIn(name, a, r["key"])
            self.assertEqual((a["currency"], a["owner_user"], a["exploration_campaign"]), ("PEN", "compromisos", "@camp-perf"))
            self.assertTrue(r["links"]["commitment_stakeholder"], r["key"])
            if "approve_direct" in r.get("transitions", []):
                approved += a["amount"]
        self.assertEqual(counts, {"draft": 3, "approved": 4, "in_progress": 3, "blocked": 1, "cancelled": 1})
        self.assertLessEqual(approved, 60000)

    def test_cinco_riesgos_con_evaluacion_inherente_y_residual(self):
        risks = self.records("social_risk")
        self.assertEqual(len(risks), 5)
        for r in risks:
            residual = [c["attributes"].get("residual", False) for c in r["commands"]]
            self.assertFalse(residual[0])
            self.assertIn(True, residual)

    def test_riesgos_con_revisiones_en_orden_y_dos_medidas(self):
        risks = sorted(self.records("social_risk"), key=lambda r: r["attributes"]["code"])
        self.assertEqual([r["attributes"]["code"] for r in risks], [f"DEMO-R-0{i}" for i in range(1, 6)])
        decisions = sorted(r["attributes"]["treatment_decision"] for r in risks)
        self.assertEqual(decisions, ["MITIGAR"] * 3 + ["MONITOREAR"] * 2)
        for r in risks:
            dates = [c["attributes"]["assessment_date"] for c in r["commands"]]
            self.assertEqual(dates, ["2026-05-05", "2026-07-05", "2026-09-05", "2026-09-10"], r["key"])
            for c in r["commands"]:
                self.assertEqual(set(c["attributes"]) - {"assessment_date", "probability", "impact", "residual", "notes"}, set())
                self.assertTrue(1 <= c["attributes"]["probability"] <= 5 and 1 <= c["attributes"]["impact"] <= 5)
            measures = [c for c in r["children"] if c["object"] == "mitigation_measure"]
            self.assertEqual(len(measures), 2, r["key"])
            self.assertTrue(all(m["attributes"]["owner_user"] == "riesgos" for m in measures))

    def test_cierres_de_la_tabla_9_5(self):
        with open(os.path.join(HERE, "indicators.json"), encoding="utf-8") as f:
            catalog = {i["code"] for i in json.load(f)["indicators"]}
        closes = [c for coll in self.dataset.collections if coll["object"] == "project" and coll.get("reopen")
                  for r in coll["records"] for c in r["commands"]]
        codes = {c["indicator"] for c in closes}
        table = {"KPI-STK-04", "KPI-STK-07", "KPI-ENG-02", "KPI-GRV-07", "KPI-ENG-03", "KPI-ENG-04", "KPI-ENG-05",
                 "KPI-COM-08", "KPI-ENG-06", "KPI-ENG-07", "KPI-ENG-08"}
        self.assertEqual(codes, table)
        self.assertTrue(codes <= catalog)
        self.assertTrue(all((c["period_start_date"], c["period_end_date"], c["optional"]) == ("2026-04-01", "2026-09-30", True)
                            for c in closes))

    def test_cada_transicion_la_puede_hacer_quien_escribe(self):
        roles = {u["key"]: set(u["roles"]) for u in self.dataset.users}
        problems = []
        for collection in self.dataset.collections:
            writer = collection.get("as", "admin")
            for record in collection["records"]:
                if not record.get("transitions") or writer == "admin":
                    continue
                with open(os.path.join(HERE, "workflows", collection["object"] + ".json"), encoding="utf-8") as f:
                    allowed = {t["name"]: set(t.get("roles", [])) for t in json.load(f)["definition"]["transitions"]}
                for transition in record["transitions"]:
                    if not allowed[transition] & roles[writer]:
                        problems.append(f"{record['key']}: {transition} as {writer}")
        self.assertEqual(problems, [])

    HUMAN_SECTIONS = {
        "introduccion", "objetivos", "metodologia", "alcance", "diseno", "trabajo_campo",
        "areas", "aids", "aiis",
        "contexto", "historia", "poblacion", "economia", "educacion_salud", "proceso_electoral", "costumbres",
        "grupos_interes", "perfiles", "caracterizacion", "percepciones", "percepciones_mineria", "percepciones_proyecto",
        "percepciones_empresa", "clasificacion", "grupos_estrategicos",
        "grupos_politicos", "grupos_monitoreo",
        "estrategias", "contactos_regulares", "reuniones_informativas", "reuniones_politicas", "comunicacion", "alianzas", "gestion_riesgos", "necesidades",
    }

    def sections(self, key="t1"):
        return next(c for c in self.deliverable(key)["commands"] if c["command"] == "sections")["sections"]

    def test_narrativa_para_cada_seccion_humana(self):
        sections = self.sections()
        self.assertGreaterEqual(len(sections), 35)
        self.assertNotIn("portada", sections)
        self.assertEqual(set(sections), self.HUMAN_SECTIONS)
        for key, text in sections.items():
            paragraphs = [p for p in text.split("\n\n") if p.strip()]
            self.assertTrue(3 <= len(paragraphs) <= 6, f"{key}: {len(paragraphs)} párrafos")

    def test_la_narrativa_nombra_a_las_personas_solo_por_mencion(self):
        people = {r["key"]: r["attributes"] for r in self.records("person")}
        sections = self.sections()
        mentioned = set()
        for key, text in sections.items():
            for person in load_demo.MENTION.findall(text):
                self.assertIn(person, people, f"{key}: {person}")
                mentioned.add(person)
            for person in people.values():
                self.assertNotIn(person["surnames"], text, key)
                # el nombre de pila completo como palabra: «Rosa» sola chocaría con «Santa Rosa de Pallca»
                self.assertIsNone(re.search(rf"\b{re.escape(person['given_names'])}\b", text), f"{key}: {person['given_names']}")
        for key in ("grupos_estrategicos", "grupos_politicos", "grupos_monitoreo"):
            self.assertTrue(load_demo.MENTION.search(sections[key]), key)
        self.assertFalse(load_demo.MENTION.search(sections["perfiles"]), "perfiles sale también en EXTERNO")

    def test_entregable_t1_del_periodo(self):
        deliverable = self.deliverable("t1")["attributes"]
        self.assertEqual(deliverable["document_code"], "DEMO-T1-001")
        self.assertEqual(deliverable["deliverable_type"], "DIAGNOSTICO SOCIAL")
        self.assertEqual((deliverable["period_start_date"], deliverable["period_end_date"], deliverable["cutoff_date"]),
                         ("2026-04-01", "2026-09-30", "2026-09-30"))
        self.assertEqual(deliverable["area_of_influence"], "@aids")

    def test_quien_escribe_cada_coleccion_tiene_el_permiso(self):
        with open(os.path.join(HERE, "roles.json"), encoding="utf-8") as f:
            permissions = {r["name"]: r["permisos"] for r in json.load(f)["roles"]}
        roles = {u["key"]: u["roles"] for u in self.dataset.users}

        def has(user, obj, action):
            return any(action in permissions[role].get(obj, []) for role in roles[user])

        problems = []
        for collection in self.dataset.collections:
            writer = collection.get("as", "admin")
            if writer == "admin":
                continue
            for record in collection["records"]:
                if not collection.get("reopen") and not has(writer, collection["object"], "CREATE"):
                    problems.append(f"{record['key']}: CREATE {collection['object']} as {writer}")
                for child in record.get("children", []):
                    if not has(writer, child["object"], "CREATE"):
                        problems.append(f"{record['key']}: CREATE {child['object']} as {writer}")
        self.assertEqual(sorted(set(problems)), [])

    def test_compromiso_de_reunion_informativa_con_justificacion(self):
        # RN-ENG-001 (CommitmentRecordRules): un taller o una reunión informativa como origen exige origin_justification
        types = {r["key"]: r["attributes"].get("engagement_type") for r in self.records("engagement")}
        for r in self.records("commitment"):
            origin = (r["attributes"].get("origin_engagement") or "@")[1:]
            if types.get(origin) in ("TALLER", "REUNION INFORMATIVA"):
                self.assertTrue(r["attributes"].get("origin_justification"), r["key"])

    def test_compromiso_aprobado_sin_solicitante_politico(self):
        # RN-ENG-002 (SeniorApproval): un solicitante POLITICO exige approve_senior; approve_direct lo rechaza
        groups = {r["key"]: r.get("_expect", {}).get("influence_group") for r in self.records("stakeholder")}
        for r in self.records("commitment"):
            if "approve_direct" in r.get("transitions", []):
                requesters = [k[1:] for k in r.get("links", {}).get("commitment_stakeholder", [])]
                self.assertNotIn("POLITICO", [groups.get(k) for k in requesters], r["key"])

    def test_la_gestion_del_riesgo_cuenta_bien_los_residuales_medios(self):
        # umbrales por defecto del proyecto (model.json): MEDIO desde 5, ALTO desde 10
        words = {1: "uno", 2: "dos", 3: "tres", 4: "cuatro", 5: "cinco"}
        risks = self.records("social_risk")
        medium = 0
        for r in risks:
            last = [c["attributes"] for c in r["commands"] if c["attributes"].get("residual")][-1]
            medium += 5 <= last["probability"] * last["impact"] < 10
        self.assertIn(f"{words[medium]} de los {words[len(risks)]}", self.sections()["gestion_riesgos"])

    # ---- T2 (#153) -----------------------------------------------------------------------------------------------

    T2_SECTIONS = {"antecedentes", "estrategias", "actividades", "redes", "relaciones", "apoyo", "conclusiones",
                   "recomendaciones"}

    def test_entregable_t2_de_la_comunidad_despues_del_corte_del_t1(self):
        t1, t2 = self.deliverable("t1")["attributes"], self.deliverable("t2")["attributes"]
        self.assertEqual((t2["document_code"], t2["deliverable_type"]), ("DEMO-T2-001", "INFORME DE CAMPO Y AVANCE"))
        self.assertEqual((t2["project"], t2["community"]), ("@proj", "@com-sanisidro"))
        self.assertGreater(t2["period_start_date"], t1["cutoff_date"])
        self.assertLessEqual(t2["period_end_date"], t2["cutoff_date"])

    def test_lo_que_suma_el_t2_cae_en_su_periodo(self):
        # el T1 cuenta las interacciones y visitas del proyecto en su periodo: las del T2 quedan después de su corte
        t2 = self.deliverable("t2")["attributes"]
        dates = [r["attributes"].get("held_at") or r["attributes"]["scheduled_at"] for r in self.t2_records("engagement")]
        dates += [r["attributes"]["actual_start_date"] for r in self.t2_records("field_visit")]
        self.assertTrue(dates)
        self.assertTrue(all(t2["period_start_date"] <= d[:10] <= t2["period_end_date"] for d in dates), dates)
        self.assertTrue(all(r["attributes"]["community"] == "@com-sanisidro" for r in self.t2_records("engagement")))

    def test_el_t2_cubre_lo_de_la_revision_visual(self):
        # lo que DeckDemo.t2 arma a mano: visitas, interacciones de varios tipos (una cancelada), redes con integrantes,
        # contactos cara a cara, un incidente de antecedentes y los sectores del predio
        engagements = self.t2_records("engagement")
        types = {r["attributes"]["engagement_type"] for r in engagements}
        self.assertGreaterEqual(len(types), 5)
        self.assertIn("CONTACTO CARA A CARA", types)
        self.assertEqual(sum(r["state"] == "cancelled" for r in engagements), 1)
        self.assertTrue(all(r["children"] for r in engagements if r["state"] == "held"))
        self.assertEqual(len(self.t2_records("field_visit")), 2)
        networks = self.t2_records("community_network")
        self.assertEqual(len(networks), 2)
        self.assertTrue(all(len(n["children"]) >= 4 for n in networks))
        incidents = self.t2_records("incident")
        self.assertTrue(any(i["attributes"]["incident_type"] in ("EXPULSION", "RETENCION DE VEHICULO O EQUIPOS")
                            and i["attributes"]["occurred_at"][:10] <= self.deliverable("t2")["attributes"]["cutoff_date"]
                            for i in incidents))
        self.assertEqual(len(self.t2_records("community_sector")), 3)

    def test_la_narrativa_del_t2_cuenta_lo_mismo_que_los_datos(self):
        sections = self.sections("t2")
        self.assertEqual(set(sections), self.T2_SECTIONS)
        engagements = self.t2_records("engagement")
        held = sum(r["state"] == "held" for r in engagements)
        cancelled = len(engagements) - held
        nodes = sum(len(n["children"]) for n in self.t2_records("community_network"))
        words = {1: "una", 2: "dos", 7: "siete", 8: "ocho", 9: "nueve"}
        self.assertIn(f"{words[len(self.t2_records('field_visit'))]} visitas de campo y {words[len(engagements)]} "
                      f"interacciones", sections["actividades"])
        self.assertIn(f"{words[held]} realizadas y {words[cancelled]} cancelada", sections["actividades"])
        self.assertIn(f"{words[nodes]} integrantes", sections["conclusiones"])
        for key in ("conclusiones", "recomendaciones"):
            self.assertTrue(sections[key].startswith("1. "), key)

    def test_la_narrativa_del_t2_nombra_a_las_personas_solo_por_mencion(self):
        people = {r["key"]: r["attributes"] for r in self.records("person")}
        sections = self.sections("t2")
        mentioned = set()
        for key, text in sections.items():
            for person in load_demo.MENTION.findall(text):
                self.assertIn(person, people, f"{key}: {person}")
                mentioned.add(person)
            for person in people.values():
                self.assertNotIn(person["surnames"], text, key)
                self.assertIsNone(re.search(rf"\b{re.escape(person['given_names'])}\b", text), f"{key}: {person['given_names']}")
        self.assertTrue(mentioned)

    def test_los_entregables_se_emiten_con_la_aprobacion_de_otra_persona(self):
        # WF-H: envía y emite quien escribe la colección, y aprueba otro usuario con el rol y la acción APPROVE
        with open(os.path.join(HERE, "workflows", "deliverable.json"), encoding="utf-8") as f:
            transitions = {t["name"]: set(t["roles"]) for t in json.load(f)["definition"]["transitions"]}
        with open(os.path.join(HERE, "roles.json"), encoding="utf-8") as f:
            permissions = {r["name"]: r["permisos"].get("deliverable", []) for r in json.load(f)["roles"]}
        roles = {u["key"]: set(u["roles"]) for u in self.dataset.users}
        collections = [c for c in self.dataset.collections if c["object"] == "deliverable"]
        self.assertEqual(sorted(r["key"] for c in collections for r in c["records"]), ["t1", "t2"])
        for collection in collections:
            writer = collection["as"]
            for record in collection["records"]:
                commands = [c["command"] for c in record["commands"]]
                self.assertEqual(commands[-2:], ["draft", "issue"], record["key"])
                approver = record["commands"][-1]["approve_as"]
                self.assertNotEqual(approver, writer, record["key"])
                for transition in ("submit_for_review", "issue"):
                    self.assertTrue(transitions[transition] & roles[writer], (record["key"], transition))
                self.assertTrue(transitions["approve"] & roles[approver], record["key"])
                self.assertTrue(any("ISSUE" in permissions[r] for r in roles[writer]), record["key"])
                self.assertTrue(any("APPROVE" in permissions[r] for r in roles[approver]), record["key"])
