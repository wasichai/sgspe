"""Reads the design documents that model.json and roles.json implement, so the tests can hold them to the docs.

Stdlib only. What it reads, and from where:

- docs/database-model.md, the cards (§6 to §21): per object its label, description, fields (type, required, unique,
  derived, restricted, default, phase), relationships (type, field, target, required, phase), flags, uniques, declared
  indexes, declared actions, workflow and phase; §4, the key of each ENUM catalog;
- docs/requirements-analysis.md §10, the options of each catalog, in order, without the "(propuesto)" notes;
- docs/security.md §7.1 (the phase of each role), §7.2 to §7.8 (the matrix) and §7.9 (the restricted fields);
- docs/domain-model.md §7.3, the states of each workflow.

A phase is "F2", "F3"... and compares as text, which works from F1 to F8.
"""
import os
import re

HERE = os.path.dirname(os.path.abspath(__file__))
DOCS = os.path.join(os.path.dirname(HERE), "docs")

TICK = re.compile(r"`([^`]+)`")
PARENS = re.compile(r"\s*\([^()]*\)")
FROM_PHASE = re.compile(r"[Dd]esde (F\d)")
DEFAULT = re.compile(r"Por defecto: ([^.]+)\.")
SEPARATOR = re.compile(r"^\|\s*:?-{3,}")
DATA_ROW = re.compile(r"^\| `[a-z_0-9]+` \|")


def read(name):
    with open(os.path.join(DOCS, name), encoding="utf-8") as f:
        return f.read()


def table_rows(lines, start):
    """The rows of the markdown table that starts at lines[start] (its header), as lists of stripped cells."""
    if start + 1 >= len(lines) or not SEPARATOR.match(lines[start + 1]):
        raise ValueError(f"the table that starts with {lines[start]!r} has no |---| separator after its header")
    rows = []
    i = start + 2  # header and separator
    while i < len(lines) and lines[i].startswith("|"):
        cells = [c.strip() for c in lines[i].strip().strip("|").split("|")]
        rows.append(cells)
        i += 1
    return rows


def ticks(text):
    return TICK.findall(text)


def strip_parens(text):
    previous = None
    while previous != text:
        previous, text = text, PARENS.sub("", text)
    return text.strip()


# ---------------------------------------------------------------------------
# database-model.md: the cards
# ---------------------------------------------------------------------------

class Card:
    def __init__(self, name):
        self.name = name
        self.label = ""
        self.description = ""
        self.fields = []  # dicts: name, type, enum, geometryType, required, unique, composite, derived, restricted,
        #                   default, phase, description
        self.relationships = []  # dicts: name, type, field, target, required, note, phase, restricted
        self.flags = {}
        self.unique = []  # field names with unique: true
        self.unique_constraints = []  # lists of field names
        self.indexed = []  # field names with indexed: true
        self.indexes = []  # lists of field names
        self.actions = []
        self.workflow = None
        self.phase = None
        self.exactly_one = []  # lists of relation field names


def _bullets(lines, start):
    """The '- **Key:** value' bullets after a card's tables, continuation lines joined, until the next heading."""
    bullets = {}
    i = start
    current = None
    while i < len(lines) and not lines[i].startswith("#"):
        line = lines[i]
        match = re.match(r"^- \*\*([^*]+?):?\*\*:? ?(.*)$", line)
        if match:
            current = match.group(1).rstrip(":")
            bullets[current] = match.group(2)
        elif current and line.startswith("  ") and line.strip():
            bullets[current] += " " + line.strip()
        else:
            current = None
        i += 1
    return bullets


def _field_type(cell):
    """('ENUM', 'stance', None) from 'ENUM `stance`'; ('GEOMETRY', None, 'POINT') from 'GEOMETRY (POINT)'."""
    if cell.startswith("ENUM"):
        return "ENUM", ticks(cell)[0], None
    if cell.startswith("GEOMETRY"):
        return "GEOMETRY", None, re.search(r"\((\w+)\)", cell).group(1)
    return cell, None, None


def _sets(text):
    """[['a', 'b'], ['c', 'd']] from "(`a`, `b`); (`c`, `d`)"."""
    return [ticks(group) for group in re.findall(r"\(([^()]*)\)", text) if ticks(group)]


def cards():
    """Every card of database-model.md, by name, in document order (dicts keep it)."""
    lines = read("database-model.md").splitlines()
    found = {}
    i = 0
    while i < len(lines):
        heading = re.match(r"^### `([a-z_]+)`$", lines[i])
        if not heading:
            i += 1
            continue
        card = Card(heading.group(1))
        j = i + 1
        while not lines[j].strip():
            j += 1
        intro = []
        while lines[j].strip():
            intro.append(lines[j].strip())
            j += 1
        intro = " ".join(intro)
        card.label = re.match(r"^\*\*([^*]+)\*\*", intro).group(1)
        body = re.sub(r"^\*\*[^*]+\*\* \([^)]*\)\. ", "", intro)
        card.description = body.split(" Tabla física")[0].strip()
        while j < len(lines) and not lines[j].startswith("#"):
            if lines[j].startswith("| Campo |"):
                for name, ftype, req, uniq, desc, _src in table_rows(lines, j):
                    kind, enum, geometry = _field_type(ftype)
                    phase = FROM_PHASE.search(desc)
                    default = DEFAULT.search(desc)
                    card.fields.append({
                        "name": ticks(name)[0], "type": kind, "enum": enum, "geometryType": geometry,
                        "required": req == "sí", "unique": uniq == "sí", "composite": uniq == "compuesto",
                        "derived": "(derivado)" in desc, "restricted": "(restringido)" in desc,
                        "default": default.group(1).strip() if default else None,
                        "phase": phase.group(1) if phase else None, "description": desc,
                    })
            elif lines[j].startswith("| Relación |"):
                for name, rtype, field, target, req, note in table_rows(lines, j):
                    phase = FROM_PHASE.search(note)
                    card.relationships.append({
                        "name": ticks(name)[0], "type": rtype, "field": (ticks(field) or [None])[0],
                        "target": ticks(target)[0], "required": req == "sí", "note": note,
                        "phase": phase.group(1) if phase else None, "inverse": note.startswith("Inversa"),
                        "restricted": "restringido" in note,
                    })
            elif lines[j].startswith("- **"):
                bullets = _bullets(lines, j)
                flags = bullets.get("Flags", "")
                card.flags = {flag: bool(re.search(rf"`{flag}` sí", flags)) for flag in ("appendOnly", "apiOnly", "requiresReason")}
                uniques = bullets.get("Únicos", "ninguno")
                if uniques.startswith("`unique` en"):
                    card.unique = ticks(uniques)[1:]
                elif uniques.startswith("`uniqueConstraints`"):
                    card.unique_constraints = _sets(uniques)
                indexes = bullets.get("Índices declarados", "ninguno")
                if indexes.startswith("`indexed` en"):
                    card.indexed = ticks(indexes)[1:]
                elif indexes.startswith("`indexes`"):
                    card.indexes = _sets(indexes)
                card.actions = [a for a in ticks(bullets.get("Acciones declaradas", "")) if a.isupper()]
                workflow = re.match(r"(WF-[A-Z0-9]+)", bullets.get("Workflow", ""))
                card.workflow = workflow.group(1) if workflow else None
                phase = re.match(r"(F\d)", bullets.get("Fase", ""))
                card.phase = phase.group(1) if phase else None
                card.exactly_one = _sets(bullets.get("Exactamente uno de", ""))
                break
            j += 1
        # Fail closed: a renamed header, a dropped separator or a blank line inside a table would leave rows out.
        end = next((k for k in range(i + 1, len(lines)) if lines[k].startswith("#")), len(lines))
        rows = sum(1 for k in range(i + 1, end) if DATA_ROW.match(lines[k]))
        if not card.fields:
            raise ValueError(f"card {card.name}: no field table (a renamed header?)")
        if rows != len(card.fields) + len(card.relationships):
            raise ValueError(f"card {card.name}: {rows} table rows in the document, but {len(card.fields)} fields and "
                             f"{len(card.relationships)} relationships read")
        found[card.name] = card
        i = j
    return found


def field_phase(card, field):
    return max(card.phase, field["phase"] or card.phase)


def relationship_phase(all_cards, card, rel):
    """A relationship arrives when both ends exist, or later if its note says so."""
    return max(card.phase, all_cards[rel["target"]].phase, rel["phase"] or "F0")


def enum_keys():
    """{catalog name of §10: enums key} from database-model.md §4. A sub-list row maps to (catalog, index)."""
    lines = read("database-model.md").splitlines()
    start = next(i for i, line in enumerate(lines) if line.startswith("| Catálogo de §10 |"))
    keys = {}
    sub_count = {}
    for catalog, key, _uses in table_rows(lines, start):
        if not ticks(key):
            continue  # "no aplica": the document types are not an ENUM
        sub = re.match(r"^(.*) \(sub-lista de [^)]+\)$", catalog)
        if sub:
            base = sub.group(1)
            index = sub_count.get(base, 0)
            sub_count[base] = index + 1
            keys[ticks(key)[0]] = (base, index)
        else:
            keys[ticks(key)[0]] = (catalog, None)
    return keys


def _options(text):
    return [strip_parens(o).strip() for o in strip_parens(text).split(" · ")]


def catalogs():
    """{enums key: [options]} from requirements-analysis.md §10, for every key of database-model.md §4."""
    lines = read("requirements-analysis.md").splitlines()
    start = next(i for i, line in enumerate(lines) if line.startswith("| Catálogo | Valores |"))
    values = {row[0]: row[1] for row in table_rows(lines, start)}
    result = {}
    for key, (catalog, index) in enum_keys().items():
        text = values[catalog]
        if index is None:
            result[key] = _options(text)
            continue
        if ":" in text:  # "Sujeto: A · B. Polaridad: C · D"
            parts = [p.split(":", 1)[1] for p in re.split(r"\.\s+(?=[A-ZÁÉÍÓÚ][a-záéíóú]+:)", text)]
        else:  # "A · B; C · D; E · F"
            parts = text.split(";")
        result[key] = _options(parts[index].strip().rstrip("."))
    return result


# ---------------------------------------------------------------------------
# security.md: roles, matrix and restricted fields
# ---------------------------------------------------------------------------

def role_phases():
    """{role: phase} and {abbreviation: role} from security.md §7.1."""
    lines = read("security.md").splitlines()
    start = next(i for i, line in enumerate(lines) if line.startswith("| Abrev. | Rol |"))
    phases, roles = {}, {}
    for abbr, role, phase, _what in table_rows(lines, start):
        name = ticks(role)[0]
        phases[name] = phase
        roles[abbr] = name
    return phases, roles


def _grants(cell):
    """['READ', 'CREATE', 'u', 'APPROVE'] from 'CRu `APPROVE`'; [] from '—'."""
    if cell == "—":
        return []
    letters = cell.split("`")[0].strip()
    order = {"R": "READ", "C": "CREATE", "U": "UPDATE", "u": "u", "D": "DELETE"}
    unknown = set(letters) - set(order) - {" "}
    if unknown:
        raise ValueError(f"unknown permission letters {sorted(unknown)} in {cell!r}")
    grants = [order[k] for k in ("R", "C", "U", "u", "D") if k in letters]
    return grants + ticks(cell)


def matrix():
    """{object: {role: [grants]}} from every table of security.md §7.2 to §7.8, as written (every phase)."""
    lines = read("security.md").splitlines()
    _, roles = role_phases()
    result = {}
    for i, line in enumerate(lines):
        if not line.startswith("| Objeto | PAD |"):
            continue
        header = [c.strip() for c in line.strip().strip("|").split("|")]
        for cells in table_rows(lines, i):
            if len(cells) != len(header):
                raise ValueError(f"matrix row {cells[0]!r} has {len(cells) - 1} cells for {len(header) - 1} roles")
            obj = ticks(cells[0])[0]
            result[obj] = {roles[abbr]: _grants(cell) for abbr, cell in zip(header[1:], cells[1:])}
    return result


def restricted_fields():
    """[(object, field, [readers])] from security.md §7.9, in table order."""
    lines = read("security.md").splitlines()
    start = next(i for i, line in enumerate(lines) if line.startswith("| Campo | Dato | Roles que conservan acceso |"))
    declared, _ = role_phases()
    result = []
    for name, _what, readers, _source in table_rows(lines, start):
        names = ticks(name)
        # solo los roles: la fila de evidence_quote nombra además la acción DOWNLOAD_RESTRICTED
        roles = [r for r in ticks(readers) if r in declared]
        if "." not in names[0]:  # "`person` de `grievance`, `inquiry` y `stakeholder_request`"
            pairs = [(obj, names[0]) for obj in names[1:]]
        else:
            pairs, current = [], None
            for token in names:
                if "." in token:
                    current, field = token.split(".", 1)
                else:
                    field = token
                pairs.append((current, field))
        result.extend((obj, field, roles) for obj, field in pairs)
    return result


# ---------------------------------------------------------------------------
# domain-model.md §7.3: workflow states
# ---------------------------------------------------------------------------

def workflow_states():
    """{object: {"INITIAL": [...], "INTERMEDIATE": [...], "FINAL": [...]}} with (name, label) pairs."""
    lines = read("domain-model.md").splitlines()
    start = next(i for i, line in enumerate(lines) if line.startswith("| WF | Objeto | Inicial |"))
    result = {}

    def states(cell):
        found = [(m.group(1), m.group(2).split(";")[0].strip()) for m in re.finditer(r"`([a-z_]+)` \(([^)]*)\)", cell)]
        if not found and cell != "—":
            raise ValueError(f"state cell {cell!r} is not '—' and has no `name` (Label) state")
        return found

    for _wf, objects, initial, middle, final in table_rows(lines, start):
        for obj in ticks(objects):
            result[obj] = {"INITIAL": states(initial), "INTERMEDIATE": states(middle), "FINAL": states(final)}
    return result
