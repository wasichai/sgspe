#!/usr/bin/env python3
"""Genera las referencias de los manuales a partir de la fuente de verdad del modelo (solo biblioteca estándar).

Lee model/model.json, model/roles.json, model/workflows/*.json, model/automations/*.json, model/document-types/*.json e
model/indicators.json, y escribe en docs/manuals/reference/generated/:

  objects.adoc       catálogo de los 90 objetos por contexto, con sus campos y relaciones
  permissions.adoc   matriz rol × objeto con las acciones de roles.json y los campos restringidos
  workflows.adoc     estados y transiciones de cada workflow, con los roles que pueden aplicarlas
  automations.adoc   automatizaciones y tipos de documento
  indicators.adoc    catálogo de indicadores (KPI)
  glossary*.adoc     glosario completo y esencial, de docs/glossary.md

Así la referencia no se escribe a mano y no puede divergir del modelo. Se corre desde cualquier carpeta:

  python3 docs/manuals/scripts/generate-reference.py [--check]

Con --check no escribe: sale con 1 si algún archivo generado no coincide con lo que produciría (para la validación).
"""
import argparse
import glob
import json
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
MANUALS = os.path.dirname(HERE)
REPO = os.path.dirname(os.path.dirname(MANUALS))
MODEL = os.path.join(REPO, "model")
OUT = os.path.join(MANUALS, "reference", "generated")

# contextos delimitados de docs/domain-model.md §6 (model.json no declara módulos)
CONTEXTS = [
    ("Proyectos", ["project", "project_party", "exploration_campaign", "project_assignment", "mining_concession"]),
    ("Territorio y SIG", ["administrative_unit", "locality", "community", "community_sector", "area_of_influence",
                          "area_of_influence_member", "project_component", "land_parcel", "land_right",
                          "environmental_feature"]),
    ("Grupos de interés", ["person", "organization", "contact_method", "position", "stakeholder",
                           "stakeholder_assessment", "prioritization_rule", "actor_relationship", "community_network",
                           "network_node"]),
    ("Relacionamiento y campo", ["engagement", "engagement_participant", "engagement_question", "field_visit",
                                 "field_observation", "engagement_plan", "key_message", "aligned_answer",
                                 "strategic_alliance", "planned_activity"]),
    ("Compromisos", ["commitment", "commitment_action", "commitment_milestone", "commitment_reschedule",
                     "commitment_dependency", "delivery", "commitment_candidate"]),
    ("Acuerdos y convenios", ["agreement", "agreement_party", "agreement_clause"]),
    ("Quejas, consultas y solicitudes", ["grievance", "inquiry", "stakeholder_request"]),
    ("Riesgos sociales", ["social_risk", "risk_assessment", "mitigation_measure", "potential_impact"]),
    ("Alerta temprana e incidentes", ["incident", "media_mention", "early_warning_signal", "early_warning_rule",
                                      "early_warning"]),
    ("Inversión social y programas", ["social_contribution", "contribution_item", "receipt", "social_program",
                                      "social_initiative", "beneficiary_group", "program_budget",
                                      "program_indicator", "program_measurement", "program_milestone"]),
    ("Evidencias, tareas y catálogos", ["evidence", "task", "indicator", "topic"]),
    ("Reportes, entregables, auditoría e IA", ["indicator_measurement", "deliverable", "deliverable_section",
                                               "deliverable_version", "deliverable_template", "export_log",
                                               "ai_suggestion", "ai_interaction"]),
    ("Línea base social", ["study", "study_instrument", "locality_profile", "school", "health_facility",
                           "road_access", "festivity", "state_program", "local_conflict", "perception", "key_theme"]),
]

TYPE_LABELS = {
    "TEXT": "Texto", "LONG_TEXT": "Texto largo", "ENUM": "Opción", "DATE": "Fecha", "DATETIME": "Fecha y hora",
    "INTEGER": "Entero", "DECIMAL": "Decimal", "BOOLEAN": "Sí/No", "GEOMETRY": "Geometría", "UUID": "UUID",
    "EMAIL": "Correo", "FILE": "Archivo", "URL": "URL",
}
ACTION_ABBR = {"READ": "R", "CREATE": "C", "UPDATE": "U", "DELETE": "D", "u": "u"}

HEADER = "// GENERADO por docs/manuals/scripts/generate-reference.py a partir de model/. No editar a mano.\n"


def load(path):
    with open(path, encoding="utf-8") as f:
        return json.load(f)


def esc(text):
    """Escapa lo que AsciiDoc interpretaría dentro de una celda de tabla."""
    if text is None:
        return ""
    return str(text).replace("|", "\\|").replace("\n", " ").strip()


def objects_doc(model, roles, workflows):
    objs = {o["name"]: o for o in model["objects"]}
    mapped = [n for _, names in CONTEXTS for n in names]
    missing = sorted(set(objs) - set(mapped))
    unknown = sorted(set(mapped) - set(objs))
    if unknown:
        raise SystemExit(f"generate-reference: objetos del índice que no están en model.json: {unknown}")
    contexts = CONTEXTS + ([("Otros", missing)] if missing else [])
    restricted = {}
    for r in roles.get("restrictedFields", []):
        for f in r["fields"]:
            restricted[(r["object"], f)] = r["readers"]
    rel_by_source = {}
    for r in model["relationships"]:
        rel_by_source.setdefault(r["source"], []).append(r)

    out = [HEADER]
    out.append(f"El modelo declara *{len(model['objects'])} objetos*, *{len(model['enums'])} listas de opciones* y "
               f"*{len(model['relationships'])} relaciones* (`model/model.json`). Los objetos se agrupan por el "
               "contexto de `docs/domain-model.md` §6; el modelo no declara módulos.\n")
    out.append("Leyenda de las marcas: *obl.* obligatorio; *único* valor único; *der.* derivado (lo calcula el "
               "servidor y no se escribe); *restr.* campo restringido a ciertos roles.\n")
    for title, names in contexts:
        out.append(f"=== {title}\n")
        for name in names:
            o = objs[name]
            flags = []
            if name in workflows:
                flags.append(f"workflow `{workflows[name]['name']}`")
            for key, label in (("apiOnly", "solo por API"), ("appendOnly", "solo inserción"),
                               ("requiresReason", "exige motivo")):
                if o.get(key):
                    flags.append(label)
            if o.get("actions"):
                flags.append("acciones: " + ", ".join(f"`{a['name']}`" for a in o["actions"]))
            out.append(f"[[obj-{name}]]\n==== {o['label']} (`{name}`)\n")
            out.append(esc(o.get("description")) + "\n")
            if flags:
                out.append("[.small]\n_" + "; ".join(flags) + "._\n")
            out.append('[%autowidth.stretch,cols="2m,3,1,2",options="header"]\n|===\n|Campo |Etiqueta |Tipo |Marcas\n')
            for f in o["fields"]:
                marks = []
                if f.get("required"):
                    marks.append("obl.")
                if f.get("unique"):
                    marks.append("único")
                if f.get("derived"):
                    marks.append("der.")
                if (name, f["name"]) in restricted:
                    marks.append("restr.")
                kind = TYPE_LABELS.get(f["type"], f["type"])
                if f["type"] == "GEOMETRY":
                    kind += f" ({f.get('geometryType', '').lower()})"
                out.append(f"|{f['name']} |{esc(f['label'])} |{kind} |{' '.join(marks)}\n")
            for r in rel_by_source.get(name, []):
                if r.get("type") == "MANY_TO_MANY":
                    out.append(f"|_{r['name']}_ |{esc(r['label'])} |Varios a varios → `{r['target']}` |\n")
                else:
                    marks = "obl." if r.get("required") else ""
                    out.append(f"|{r['fieldName']} |{esc(r['label'])} |Relación → `{r['target']}` |{marks}\n")
            out.append("|===\n")
    return "\n".join(out)


def permissions_doc(model, roles):
    objs = [o["name"] for o in model["objects"]]
    labels = {o["name"]: o["label"] for o in model["objects"]}
    rs = roles["roles"]
    out = [HEADER]
    out.append(f"Matriz de `model/roles.json`: {len(rs)} roles declarados más {len(roles['mappedToAdmin'])} que "
               f"equivalen al administrador de wasichai ({', '.join('`' + r + '`' for r in roles['mappedToAdmin'])}), "
               "que puede todo dentro de su organización.\n")
    out.append("Abreviaturas: *R* leer, *C* crear, *U* actualizar, *D* borrar, *u* actualizar sin campos editables "
               "(solo para aplicar transiciones); las acciones especiales se escriben completas. `*` en la columna de "
               "objeto significa _todos los objetos_.\n")
    out.append("=== Roles\n")
    out.append('[cols="3m,3,6",options="header"]\n|===\n|Rol |Nombre |Descripción\n')
    for r in rs:
        out.append(f"|{r['name']} |{esc(r['label'])} |{esc(r.get('description'))}\n")
    for r in roles["mappedToAdmin"]:
        out.append(f"|{r} |Administrador (wasichai `ADMIN`) |Rol de la plataforma; no se declara en `roles.json`.\n")
    out.append("|===\n")
    out.append("=== Permisos por rol\n")
    for r in rs:
        perms = r.get("permisos", {})
        out.append(f"[[rol-{r['name'].lower()}]]\n==== {esc(r['label'])} (`{r['name']}`)\n")
        if not perms:
            out.append("_Sin permisos declarados._\n")
            continue
        out.append('[cols="3,1m,4",options="header"]\n|===\n|Objeto |Básicos |Acciones especiales\n')
        for obj in (["*"] if "*" in perms else []) + [o for o in objs if o in perms]:
            acts = perms[obj]
            basic = "".join(ACTION_ABBR[a] for a in ["READ", "CREATE", "UPDATE", "u", "DELETE"] if a in acts)
            special = ", ".join(f"`{a}`" for a in acts if a not in ACTION_ABBR)
            name = "Todos los objetos" if obj == "*" else f"{labels.get(obj, obj)} (`{obj}`)"
            out.append(f"|{name} |{basic} |{special}\n")
        out.append("|===\n")
    out.append("=== Campos restringidos\n")
    out.append("Solo los roles de la columna _Lectores_ (y el administrador) ven estos campos; para los demás el "
               "servidor los omite.\n")
    out.append('[cols="2m,3m,4m",options="header"]\n|===\n|Objeto |Campos |Lectores\n')
    for rf in roles.get("restrictedFields", []):
        out.append(f"|{rf['object']} |{', '.join(rf['fields'])} |{', '.join(rf['readers'])}\n")
    out.append("|===\n")
    return "\n".join(out)


def workflows_doc(model, workflows):
    labels = {o["name"]: o["label"] for o in model["objects"]}
    out = [HEADER]
    out.append(f"`model/workflows/` declara {len(workflows)} workflows. Cada transición lista los roles que pueden "
               "aplicarla; además rigen las guardas del servidor (alcance por proyecto y reglas de negocio de "
               "`docs/workflows.md`), y el administrador puede aplicar cualquiera.\n")
    for obj in sorted(workflows, key=lambda n: workflows[n]["name"]):
        wf = workflows[obj]
        states = {s["name"]: s for s in wf["definition"]["states"]}
        out.append(f"[[wf-{obj}]]\n=== {esc(labels.get(obj, obj))} (`{wf['name']}`)\n")
        st = []
        for s in wf["definition"]["states"]:
            kind = {"INITIAL": " (inicial)", "FINAL": " (final)"}.get(s.get("type"), "")
            st.append(f"{s['label']} `{s['name']}`{kind}")
        out.append("*Estados:* " + " · ".join(st) + ".\n")
        out.append('[cols="3,2,2,4m",options="header"]\n|===\n|Transición |Desde |Hacia |Roles\n')
        for t in wf["definition"]["transitions"]:
            frm = states.get(t["from"], {}).get("label", t["from"])
            to = states.get(t["to"], {}).get("label", t["to"])
            out.append(f"|{esc(t['label'])} (`{t['name']}`) |{esc(frm)} |{esc(to)} |{', '.join(t.get('roles', []))}\n")
        out.append("|===\n")
    return "\n".join(out)


def automations_doc():
    out = [HEADER]
    out.append("=== Automatizaciones\n")
    out.append('[cols="3m,2m,3,4",options="header"]\n|===\n|Nombre |Objeto |Disparador |Acciones\n')
    for path in sorted(glob.glob(os.path.join(MODEL, "automations", "*.json"))):
        obj = os.path.basename(path)[:-5]
        for a in load(path):
            d = a["definition"]
            trig = d["trigger"]
            when = {"STATE_ENTERED": "entra en el estado", "RECORD_CREATED": "al crear el registro",
                    "RECORD_UPDATED": "al actualizar el registro"}.get(trig["type"], trig["type"])
            if trig.get("state"):
                when += f" `{trig['state']}`"
            conds = [f"`{c['field']}` {c['operator']}" + (f" `{c['value']}`" if "value" in c else "")
                     for c in d.get("conditions", [])]
            if conds:
                when += " si " + " y ".join(conds)
            acts = []
            for act in d["actions"]:
                if act["type"] == "CREATE_RECORD":
                    title = act.get("values", {}).get("title")
                    acts.append(f"crea `{act['targetObject']}`" + (f" «+{title}+»" if title else ""))
                elif act["type"] == "GENERATE_DOCUMENT":
                    acts.append(f"genera el documento `{act['documentType']}`")
                else:
                    acts.append(act["type"])
            out.append(f"|{a['name']} |{obj} |{when} |{esc(a.get('label'))}: {esc('; '.join(acts))}\n")
    out.append("|===\n")
    out.append("=== Tipos de documento\n")
    out.append('[cols="3m,2m,3,1m",options="header"]\n|===\n|Tipo |Objeto |Nombre |Prefijo\n')
    for path in sorted(glob.glob(os.path.join(MODEL, "document-types", "*.json"))):
        obj = os.path.basename(path)[:-5]
        for d in load(path):
            out.append(f"|{d.get('name')} |{obj} |{esc(d.get('label'))} |{d.get('prefix', '')}\n")
    out.append("|===\n")
    return "\n".join(out)


def indicators_doc():
    data = load(os.path.join(MODEL, "indicators.json"))["indicators"]
    out = [HEADER]
    out.append(f"`model/indicators.json` declara {len(data)} indicadores; `load_indicators.py` los carga en el objeto "
               "`indicator` de cada organización.\n")
    out.append('[cols="2m,4,5,2",options="header"]\n|===\n|Código |Nombre |Definición |Frecuencia\n')
    for i in data:
        out.append(f"|{i['code']} |{esc(i['name'])} |{esc(i.get('definition'))} |{esc(i.get('frequency') or '—')}\n")
    out.append("|===\n")
    return "\n".join(out)


GLOSSARY_SECTIONS_SKIP = ("Distinciones obligatorias", "Índice EN → ES del prompt")
# términos del glosario esencial del Manual de usuario (por su texto en español en docs/glossary.md)
ESSENTIAL_TERMS = [
    "Proyecto", "Asignación a proyecto", "Área de influencia", "Localidad", "Comunidad campesina o nativa", "Grupo de interés",
    "Persona", "Organización (del dominio)", "Interacción", "Visita de campo", "Compromiso", "Acuerdo", "Entrega",
    "Quejas, consultas y solicitudes (QCS)", "Queja", "Consulta", "Solicitud", "QCS confidencial", "Riesgo social",
    "Alerta temprana", "Aporte social", "Programa social", "Evidencia", "Tarea", "Entregable", "Nivel de divulgación",
    "Sugerencia de IA",
]


def _strip_md(text):
    import re
    text = re.sub(r"\[(?:DOC|PRM|USR)-[^\]]*\]", "", text)          # citas de fuente
    text = re.sub(r"\[([^\]]+)\]\([^)]*\)", r"\1", text)            # enlaces markdown
    text = re.sub(r"\(?\b(?:ASM|CON|GAP|RN|RF|RQ|UPS)-[A-Z]*-?\d+[a-z]?\b\)?", "", text)  # ids del análisis
    text = re.sub(r" (?:de|del análisis) §[\d.]+", "", text)              # secciones del análisis
    text = text.replace("**", "").replace(" ;", ";").replace("( )", "").replace("()", "")
    return " ".join(text.split()).strip(" ;,")


def glossary_entries():
    """Lee las tablas de docs/glossary.md: (sección, término, identificador, definición, no confundir con)."""
    path = os.path.join(REPO, "docs", "glossary.md")
    entries, section, header = [], None, None
    for line in open(path, encoding="utf-8"):
        line = line.rstrip("\n")
        if line.startswith("## "):
            section, header = line[3:].strip(), None
            continue
        if not line.startswith("|") or section is None or section in GLOSSARY_SECTIONS_SKIP:
            continue
        cells = [c.strip() for c in line.strip().strip("|").split("|")]
        if header is None:
            header = cells
            continue
        if set(cells[0]) <= set("-: "):
            continue
        row = dict(zip(header, cells))
        entries.append((section, row.get("Término (ES)", ""), row.get("Identificador", ""),
                        _strip_md(row.get("Definición", "")), _strip_md(row.get("No confundir con", ""))))
    return entries


def _glossary_item(term, ident, definition, confuse):
    text = f"{esc(_strip_md(term))}:: {definition.rstrip('.')}."
    if ident and ident not in ("—", "-"):
        text += f" _Identificador:_ {ident}."
    if confuse and confuse not in ("—", "-"):
        text += f" _No confundir con:_ {confuse.rstrip('.')}."
    return text + "\n"


def glossary_doc():
    out = [HEADER, "Términos del negocio en español con su identificador técnico, tomados de `docs/glossary.md` "
           "(fuente de verdad del vocabulario). Las citas de fuente y los identificadores de requisitos se omiten.\n"]
    current = None
    for section, term, ident, definition, confuse in glossary_entries():
        if section != current:
            out.append(f"=== {section}\n")
            current = section
        out.append(_glossary_item(term, ident, definition, confuse))
    return "\n".join(out)


def glossary_essentials_doc():
    by_term = {e[1]: e for e in glossary_entries()}
    missing = [t for t in ESSENTIAL_TERMS if t not in by_term]
    if missing:
        raise SystemExit(f"generate-reference: términos esenciales que no están en docs/glossary.md: {missing}")
    out = [HEADER]
    for term in sorted(ESSENTIAL_TERMS, key=str.lower):
        _, t, ident, definition, confuse = by_term[term]
        out.append(_glossary_item(t, "", definition, confuse))
    return "\n".join(out)


def main():
    ap = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    ap.add_argument("--check", action="store_true", help="no escribe; sale con 1 si algo está desactualizado")
    args = ap.parse_args()
    model = load(os.path.join(MODEL, "model.json"))
    roles = load(os.path.join(MODEL, "roles.json"))
    workflows = {}
    for path in sorted(glob.glob(os.path.join(MODEL, "workflows", "*.json"))):
        workflows[os.path.basename(path)[:-5]] = load(path)
    docs = {
        "objects.adoc": objects_doc(model, roles, workflows),
        "permissions.adoc": permissions_doc(model, roles),
        "workflows.adoc": workflows_doc(model, workflows),
        "automations.adoc": automations_doc(),
        "indicators.adoc": indicators_doc(),
        "glossary.adoc": glossary_doc(),
        "glossary-essentials.adoc": glossary_essentials_doc(),
    }
    os.makedirs(OUT, exist_ok=True)
    stale = []
    for name, text in docs.items():
        path = os.path.join(OUT, name)
        current = open(path, encoding="utf-8").read() if os.path.exists(path) else None
        if current != text:
            stale.append(name)
            if not args.check:
                with open(path, "w", encoding="utf-8") as f:
                    f.write(text)
    if args.check:
        if stale:
            print("referencias desactualizadas: " + ", ".join(stale) + " (correr generate-reference.py)")
            return 1
        print(f"referencias al día ({len(docs)})")
        return 0
    print(f"referencias generadas en {os.path.relpath(OUT, REPO)}: {len(docs)} archivos, {len(stale)} cambiados")
    return 0


if __name__ == "__main__":
    sys.exit(main())
