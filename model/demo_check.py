"""Validación del conjunto de demostración del T1 contra model/model.json (épica #102), sin llamar a core.

`validate(dataset, model)` devuelve la lista de problemas (vacía si todo va bien); cada mensaje empieza con
`<archivo>:<clave>:`. `influence_group` es un puerto exacto de src/main/kotlin/sgspe/stakeholder/InfluenceClassifier.kt:
cada promedio se redondea a 10 decimales y cada valor a 2, ambos HALF_UP, y cada nivel se calcula desde el anterior ya
redondeado. Solo biblioteca estándar.

Aquí viven también `REFERENCE` y `COMMANDS`, que load_demo.py importa: este módulo no importa load_demo, así que
cualquiera de los dos se puede importar primero.
"""
import re
from decimal import ROUND_HALF_UP, Decimal

EMAIL_DOMAIN = "@sgspe.test"
CUTOFF = "2026-09-30"
DEFAULT_THRESHOLD = Decimal(3)

INTEREST = {
    "interest_affectation": ["affect_environment", "affect_livelihood", "affect_property_status"],
    "interest_opportunity": ["opportunity_work", "opportunity_development", "opportunity_benefits"],
}
POWER = {
    "power_resource_control": ["power_legal_action", "power_credibility", "power_permit_control"],
    "power_action_capacity": ["power_action_scope", "power_organization_level", "power_convening_capacity"],
    "power_network_mobilization": ["power_network_membership", "power_group_mobilization", "power_other_pressure"],
}
INDICATORS = [name for names in (*INTEREST.values(), *POWER.values()) for name in names]

# las claves que acepta el comando de evaluación (AssessmentInput.ALLOWED en stakeholder/Assessments.kt)
TEXTS = ["stance_reasons", "concerns", "expectations", "project_knowledge", "company_knowledge", "team_closeness",
         "override_reason"]
BOOLEANS = ["open_to_dialogue", "communication_sufficient", "information_adequate"]
ASSESSMENT_KEYS = {"assessment_date", "basis", "stance", "trust_score", "position", "influence_group",
                   "group_overridden", *TEXTS, *BOOLEANS, *INDICATORS}

# el objeto sobre el que va cada comando, y los comandos que entiende el cargador
COMMAND_OBJECT = {"assessment": "stakeholder", "risk_assessment": "social_risk", "approve_area": "area_of_influence",
                  "close_period": "project", "sections": "deliverable", "draft": "deliverable"}
COMMANDS = tuple(COMMAND_OBJECT)
REFERENCE = re.compile(r"^@([A-Za-z0-9_-]+)$")
# el formato con el que load_ubigeo.py guarda todo ubigeo_code (su CODE): el departamento y la provincia se completan
# con ceros, así que "09" o "0904" no encontrarían la unidad ya cargada y la duplicarían
UBIGEO = re.compile(r"^\d{6}$")


def _round(value):
    return value.quantize(Decimal("0.01"), rounding=ROUND_HALF_UP)


def _average(values):
    if not values:
        return None
    return (sum(values, Decimal(0)) / Decimal(len(values))).quantize(Decimal("0.0000000001"), rounding=ROUND_HALF_UP)


def _scores(indicators):
    """(índice de interés, índice de poder) como en InfluenceClassifier.scores; None si a un índice no le llega nada."""
    components = {}
    for name, names in {**INTEREST, **POWER}.items():
        average = _average([Decimal(indicators[i]) for i in names if indicators.get(i) is not None])
        components[name] = None if average is None else _round(average)

    def index(group):
        average = _average([components[c] for c in group if components[c] is not None])
        return None if average is None else _round(average)

    interest, power = index(INTEREST), index(POWER)
    return None if interest is None or power is None else (interest, power)


def influence_group(indicators, interest_threshold=3, power_threshold=3):
    """El grupo de influencia de una evaluación; None si no hay al menos un indicador de interés y uno de poder."""
    scores = _scores(indicators)
    if scores is None:
        return None
    interest, power = scores
    interest_high = interest >= Decimal(str(interest_threshold))
    power_high = power >= Decimal(str(power_threshold))
    if interest_high and power_high:
        return "ESTRATEGICO"
    if power_high:
        return "POLITICO"
    if interest_high:
        return "PARA MONITOREO"
    return "VULNERABLE"


class _Checker:
    def __init__(self, dataset, model):
        self.dataset = dataset
        self.enums = model.get("enums", {})
        self.objects = {o["name"]: o for o in model["objects"]}
        self.relations = {}  # (objeto, fieldName) -> relación N1
        self.many = {}  # (objeto, nombre) -> relación MANY_TO_MANY
        for rel in model.get("relationships", []):
            if rel.get("type") == "MANY_TO_MANY":
                self.many[(rel["source"], rel["name"])] = rel
            elif rel.get("fieldName"):
                self.relations[(rel["source"], rel["fieldName"])] = rel
        self.errors = []
        self.defined = {}  # clave -> objeto
        self.attributes = {}  # clave -> atributos del registro

    def error(self, file, key, message):
        self.errors.append(f"{file}:{key}: {message}")

    def run(self):
        for user in self.dataset.users:
            if not str(user.get("email", "")).endswith(EMAIL_DOMAIN):
                self.error("users", user.get("key"), f"email {user.get('email')} must end with {EMAIL_DOMAIN}")
        for collection in self.dataset.collections:
            self.collection(collection)
        return self.errors

    def collection(self, collection):
        file, name = collection.get("_file", "dataset"), collection.get("object")
        obj = self.objects.get(name)
        for record in collection.get("records", []):
            key = record.get("key")
            if obj is None:
                self.error(file, key, f"object {name} is not in model.json")
                continue
            if obj.get("apiOnly"):
                self.error(file, key, f"{name} is apiOnly: it loads only through commands")
                continue
            if not collection.get("reopen"):
                self.record(file, key, name, obj, record)
            self.commands(file, key, name, record)
            self.defined.setdefault(key, name)

    # ---- atributos ---------------------------------------------------------------------------------------------

    def expand(self, attributes):
        """Los atributos con `<x>_user` expandido a `<x>_user_id` y `<x>_user_email`, como lo hace el cargador."""
        expanded = {}
        emails = {"admin": "admin" + EMAIL_DOMAIN, **{u.get("key"): u.get("email") for u in self.dataset.users}}
        for name, value in attributes.items():
            if name.endswith("_user") and isinstance(value, str) and not REFERENCE.match(value):
                expanded[name + "_id"] = value
                expanded[name + "_email"] = emails.get(value, value)
            else:
                expanded[name] = value
        return expanded

    def record(self, file, key, name, obj, record, parent=None, label=None):
        who = f"{label} " if label else ""
        attributes = self.expand(record.get("attributes") or {})
        geometries = record.get("geometries") or {}
        fields = {f["name"]: f for f in obj.get("fields", [])}
        for attr, value in attributes.items():
            field = fields.get(attr)
            relation = self.relations.get((name, attr))
            if field is None and relation is None:
                self.error(file, key, f"{who}{name}.{attr} is not a field or relation of {name}")
                continue
            if field is not None:
                self.field_value(file, key, who, name, field, value)
            if relation is not None:
                self.reference(file, key, who, name, attr, value, relation["target"])
        for geometry in geometries:
            if fields.get(geometry, {}).get("type") != "GEOMETRY":
                self.error(file, key, f"{who}{name}.{geometry} is not a GEOMETRY field")
        for field in fields.values():
            if field.get("required") and not field.get("derived") and field["name"] not in attributes \
                    and field["name"] not in geometries and "defaultValue" not in field and field["name"] != parent:
                self.error(file, key, f"{who}{name}.{field['name']} is required")
        for (source, attr), relation in self.relations.items():
            if source == name and relation.get("required") and attr not in attributes and attr != parent:
                self.error(file, key, f"{who}{name}.{attr} is required")
        if not label:
            self.attributes[key] = record.get("attributes") or {}
            for relationship, others in (record.get("links") or {}).items():
                self.links(file, key, name, relationship, others)
            for child in record.get("children", []):
                self.child(file, key, child)

    def field_value(self, file, key, who, name, field, value):
        attr = field["name"]
        if field.get("derived"):
            self.error(file, key, f"{who}{name}.{attr} is derived: core calculates it")
        if field.get("type") == "GEOMETRY":
            self.error(file, key, f"{who}{name}.{attr} is a geometry: use geometries")
        if field.get("type") == "ENUM" and isinstance(value, str) and not REFERENCE.match(value):
            options = self.enums.get(field.get("enum"), [])
            if value not in options:
                self.error(file, key, f"{who}{name}.{attr} {value!r} is not an option of {field.get('enum')}")
        if name == "administrative_unit" and attr == "ubigeo_code" and not UBIGEO.fullmatch(str(value)):
            self.error(file, key, f"{who}{name}.{attr} {value!r} must have 6 digits, as load_ubigeo.py stores it")
        if field.get("type") == "EMAIL" and not str(value).endswith(EMAIL_DOMAIN):
            self.error(file, key, f"{who}{name}.{attr} {value!r} must end with {EMAIL_DOMAIN}")

    def reference(self, file, key, who, name, attr, value, target):
        whole = REFERENCE.match(value) if isinstance(value, str) else None
        if whole is None:
            return
        actual = self.defined.get(whole.group(1))
        if actual is not None and actual != target:
            self.error(file, key, f"{who}{name}.{attr} points to {actual} @{whole.group(1)}, expected {target}")

    def links(self, file, key, name, relationship, others):
        relation = self.many.get((name, relationship))
        if relation is None:
            self.error(file, key, f"link {relationship} is not a MANY_TO_MANY relationship with source {name}")
            return
        for other in others:
            self.reference(file, key, "", name, relationship, other, relation["target"])

    def child(self, file, key, child):
        name = child.get("object")
        obj = self.objects.get(name)
        if obj is None:
            self.error(file, key, f"child object {name} is not in model.json")
        elif obj.get("apiOnly"):
            self.error(file, key, f"child {name} is apiOnly: it loads only through commands")
        else:
            self.record(file, key, name, obj, child, parent=child.get("parent"), label=f"child {name}")
        if child.get("key"):
            self.defined.setdefault(child["key"], name)

    # ---- comandos ----------------------------------------------------------------------------------------------

    def commands(self, file, key, name, record):
        for command in record.get("commands", []):
            kind = command.get("command")
            if kind not in COMMANDS:
                self.error(file, key, f"unknown command {kind}")
            elif COMMAND_OBJECT[kind] != name:
                self.error(file, key, f"command {kind} goes on {COMMAND_OBJECT[kind]}, not {name}")
            elif kind == "assessment":
                self.assessment(file, key, command.get("attributes") or {})
        if name == "stakeholder":
            self.expected_group(file, key, record)

    def assessment(self, file, key, attributes):
        for attr in attributes:
            if attr not in ASSESSMENT_KEYS:
                self.error(file, key, f"assessment.{attr} is not an allowed key")
        for required in ("assessment_date", "basis"):
            if required not in attributes:
                self.error(file, key, f"assessment.{required} is required")
        date = attributes.get("assessment_date")
        if date is not None and (not isinstance(date, str) or not re.fullmatch(r"\d{4}-\d{2}-\d{2}", date)
                                 or date > CUTOFF):
            self.error(file, key, f"assessment.assessment_date {date!r} must be a date up to {CUTOFF}")
        for attr in (*INDICATORS, "trust_score"):
            value = attributes.get(attr)
            if attr in attributes and (isinstance(value, bool) or not isinstance(value, int) or not 1 <= value <= 5):
                self.error(file, key, f"assessment.{attr} {value!r} must be an integer from 1 to 5")
        fields = {f["name"]: f for f in self.objects.get("stakeholder_assessment", {}).get("fields", [])}
        for attr, value in attributes.items():
            field = fields.get(attr)
            if field and field.get("type") == "ENUM" and isinstance(value, str) \
                    and value not in self.enums.get(field.get("enum"), []):
                self.error(file, key, f"assessment.{attr} {value!r} is not an option of {field.get('enum')}")

    def expected_group(self, file, key, record):
        expected = (record.get("_expect") or {}).get("influence_group")
        if expected is None:
            return
        last = next((c for c in reversed(record.get("commands", [])) if c.get("command") == "assessment"), None)
        if last is None:
            self.error(file, key, f"_expect.influence_group {expected} without an assessment command")
            return
        attributes = last.get("attributes") or {}
        if attributes.get("influence_group") and attributes.get("group_overridden") is not False:
            calculated = attributes["influence_group"]  # una anulación manual manda sobre el cálculo
        else:
            project = self.attributes.get(self.project_key(record), {})
            calculated = influence_group(
                {i: attributes.get(i) for i in INDICATORS},
                project.get("interest_threshold", DEFAULT_THRESHOLD), project.get("power_threshold", DEFAULT_THRESHOLD))
        if calculated != expected:
            self.error(file, key, f"_expect.influence_group {expected}, the last assessment gives {calculated}")

    @staticmethod
    def project_key(record):
        value = (record.get("attributes") or {}).get("project")
        whole = REFERENCE.match(value) if isinstance(value, str) else None
        return whole.group(1) if whole else None


def validate(dataset, model):
    """Los problemas del conjunto frente al modelo, uno por línea; vacía si todo va bien."""
    return _Checker(dataset, model).run()
