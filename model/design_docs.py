"""Reads the design documents that model.json and roles.json implement, so the tests can hold them to the docs.

Stdlib only. What it reads, and from where:

- docs/database-model.md, the cards (§6 to §21): per object its label, description, fields (type, required, unique,
  derived, restricted, default, phase), relationships (type, field, target, required, phase), flags, uniques, declared
  indexes, declared actions (and the phase of one written "`EXPORT` (desde F6)"), the media types and the byte limit of
  each FILE field (several per card since F8), workflow and phase; §4, the key of each ENUM catalog; and, of a fixed table of the sgspe schema (knowledge_chunk, F7),
  its columns with their PostgreSQL type, its primary key, its uniques, its indexes and its full-text index;
- docs/requirements-analysis.md §10, the options of each catalog, in order, without the "(propuesto)" notes;
- docs/security.md §7.1 (the phase of each role), §7.2 to §7.8 (the matrix), §7.9 (the restricted fields) and §9.7 (the
  masters of ProjectScope.TENANT);
- docs/domain-model.md §7.3, the states of each workflow;
- docs/gis.md §2.2, the layers whose sensitivity is "Pública" and the object.field each one draws.

A phase is "F2", "F3"... and compares as text, which works from F1 to F8.
"""
import os
import re

HERE = os.path.dirname(os.path.abspath(__file__))
DOCS = os.path.join(os.path.dirname(HERE), "docs")

TICK = re.compile(r"`([^`]+)`")
PARENS = re.compile(r"\s*\([^()]*\)")
FROM_PHASE = re.compile(r"[Dd]esde (F\d)")
# "`EXPORT` (desde F6)" in a card's "Acciones declaradas": the action and the phase it arrives in
ACTION = re.compile(r"`([A-Z][A-Z0-9_]*)`(?:\s*\(?[Dd]esde (F\d)\)?)?")
DEFAULT = re.compile(r"Por defecto: ([^.]+)\.")
SEPARATOR = re.compile(r"^\|\s*:?-{3,}")
DATA_ROW = re.compile(r"^\| `[a-z_0-9]+` \|")
# the "; " before the second form of a Únicos or Índices bullet, never the one between two sets of the same form
BOTH_FORMS = re.compile(r"; (?=`(?:unique|uniqueConstraints|indexed|indexes)`)")
# "`a` y `b` admiten `application/pdf`" in a card's "Tipos de archivo" bullet, one clause per group of FILE fields
FILE_TYPES = re.compile(r"((?:`[a-z][a-z0-9_]*`(?:, | y )?)+) admiten? ((?:`[^`\s]+/[^`\s]+`(?:, | y )?)+)")
# "`a`, `b` y `c`: 67108864 bytes" in a card's "Tope de archivo" bullet, one clause per group of FILE fields
FILE_LIMIT = re.compile(r"((?:`[a-z][a-z0-9_]*`(?:, | y )?)+): (\d+) bytes")


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
        self.action_phases = {}  # action -> the phase its "(desde Fn)" names; the others arrive with the card
        self.workflow = None
        self.phase = None
        self.exactly_one = []  # lists of relation field names
        self.content_types = {}  # FILE field name -> the media types it takes (contentTypes)
        self.max_bytes = {}  # FILE field name -> its maxBytes, when the card gives one (since F8)


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


def file_clauses(types, limits):
    """({field: [media types]}, {field: maxBytes}) from the text of a card's "Tipos de archivo" and "Tope de archivo"
    bullets. One clause per group of FILE fields: "`file` admite `application/pdf`, `image/*` y `text/csv`" or, with
    several (deliverable, F8), "`a` y `b` admiten `application/pdf`; `c` admite `text/plain`"; and "`a`, `b` y `c`:
    67108864 bytes". A field without a limit takes the server ceiling (wasichai.files.max-bytes)."""
    content_types, max_bytes = {}, {}
    for names, kinds in FILE_TYPES.findall(types):
        content_types.update({name: ticks(kinds) for name in ticks(names)})
    for names, limit in FILE_LIMIT.findall(limits):
        max_bytes.update({name: int(limit) for name in ticks(names)})
    return content_types, max_bytes


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
                # a bullet may carry both forms: "`indexed` en `a`, `b`; `indexes` (`c`, `d`)"
                for part in BOTH_FORMS.split(bullets.get("Únicos", "ninguno")):
                    if part.startswith("`unique` en"):
                        card.unique = ticks(part)[1:]
                    elif part.startswith("`uniqueConstraints`"):
                        card.unique_constraints = _sets(part)
                for part in BOTH_FORMS.split(bullets.get("Índices declarados", "ninguno")):
                    if part.startswith("`indexed` en"):
                        card.indexed = ticks(part)[1:]
                    elif part.startswith("`indexes`"):
                        card.indexes = _sets(part)
                card.actions = [a for a in ticks(bullets.get("Acciones declaradas", "")) if a.isupper()]
                card.action_phases = {m.group(1): m.group(2) for m in ACTION.finditer(bullets.get("Acciones declaradas", ""))
                                      if m.group(2)}
                workflow = re.match(r"(WF-[A-Z0-9]+)", bullets.get("Workflow", ""))
                card.workflow = workflow.group(1) if workflow else None
                phase = re.match(r"(F\d)", bullets.get("Fase", ""))
                card.phase = phase.group(1) if phase else None
                card.exactly_one = _sets(bullets.get("Exactamente uno de", ""))
                card.content_types, card.max_bytes = file_clauses(bullets.get("Tipos de archivo", ""),
                                                                  bullets.get("Tope de archivo", ""))
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
        file_fields = {f["name"] for f in card.fields if f["type"] == "FILE"}
        if set(card.content_types) != file_fields:
            raise ValueError(f"card {card.name}: FILE fields {sorted(file_fields)}, but the 'Tipos de archivo' bullet "
                             f"names {sorted(card.content_types)}")
        if not set(card.max_bytes) <= file_fields:
            raise ValueError(f"card {card.name}: the 'Tope de archivo' bullet names {sorted(set(card.max_bytes) - file_fields)}, "
                             f"which are not FILE fields")
        found[card.name] = card
        i = j
    return found


# "GIN de texto completo sobre `to_tsvector('spanish', content)`" in the Índices bullet of a fixed table
FULL_TEXT = re.compile(r"GIN de texto completo sobre `to_tsvector\('(\w+)', (\w+)\)`")


def fixed_table(name):
    """A fixed table of the sgspe schema from its card (database-model.md §2.3 and §20): {"columns": [(name, type,
    required)], "primary_key": [...], "uniques": [[...]], "indexes": [[...]], "full_text": (configuration, column)}. Its
    Únicos bullet reads "clave primaria `id`; (`a`, `b`)" and its Índices bullet "(`a`, `b`); GIN de texto completo sobre
    `to_tsvector('spanish', content)`". Fails if the card is an object's or a bullet lost its shape."""
    lines = read("database-model.md").splitlines()
    card = cards().get(name)
    if card is None:
        raise ValueError(f"database-model.md has no card for {name}")
    start = next(i for i, line in enumerate(lines) if line == f"### `{name}`")
    end = next((k for k in range(start + 1, len(lines)) if lines[k].startswith("#")), len(lines))
    bullets = _bullets(lines, next(k for k in range(start, end) if lines[k].startswith("- **")))
    uniques, indexes = bullets.get("Únicos", ""), bullets.get("Índices", "")
    if not uniques.startswith("clave primaria") or not indexes:
        raise ValueError(f"card {name}: not a fixed table (no 'clave primaria' in Únicos or no Índices bullet)")
    primary, _, rest = uniques.partition(";")
    full_text = FULL_TEXT.search(indexes)
    return {
        "columns": [(f["name"], f["type"], f["required"]) for f in card.fields],
        "primary_key": ticks(primary),
        "uniques": _sets(rest),
        "indexes": _sets(FULL_TEXT.sub("", indexes)),
        "full_text": full_text.groups() if full_text else None,
    }


def field_phase(card, field):
    return max(card.phase, field["phase"] or card.phase)


def relationship_phase(all_cards, card, rel):
    """A relationship arrives when both ends exist, or later if its note says so."""
    return max(card.phase, all_cards[rel["target"]].phase, rel["phase"] or "F0")


def action_phase(card, action):
    """A declared action arrives with its card, or later if the card writes "(desde Fn)" after it (EXPORT from F6)."""
    return max(card.phase, card.action_phases.get(action) or card.phase)


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


# ---------------------------------------------------------------------------
# workflows.md: the transitions of a workflow
# ---------------------------------------------------------------------------

TRANSITION_NAME = re.compile(r"^`([a-z_]+)` \(([^()]+)\)$")
ROLE = re.compile(r"`([A-Z][A-Z0-9_]*)`")
SAME_ROLES = re.compile(r"^Los mismos de `([a-z_]+)`$")
# a cell that names the section's own list, as WF-N does: "- **Roles operativos:** `A`, `B`…"
ROLE_LIST = re.compile(r"^Roles ([a-záéíóú]+)$")
SUBJECT = re.compile(r"^\*\*Objeto:\*\* `([a-z_]+)`$")


def _section_bullet(lines, start, end, name):
    """The text of the '- **<name>:**' bullet of lines[start:end], continuation lines joined, or None."""
    for i in range(start, end):
        if lines[i].startswith(f"- **{name}:**"):
            text = lines[i]
            j = i + 1
            while j < end and lines[j].startswith("  ") and lines[j].strip():
                text += " " + lines[j].strip()
                j += 1
            return text
    return None


def workflow_transitions(letter, obj=None):
    """The transitions of the WF-<letter> section of workflows.md: [{name, label, from, to, roles}] in table order.

    Fails (ValueError) when the section, its table or a cell does not have the expected shape, so a changed document
    cannot make a test compare against less than the document says. The cell "Los mismos de `x`" takes the roles of x;
    the cell "Roles <adjective>" takes the roles of the section's bullet "- **Roles <adjective>:**". A section with one
    table per object (WF-K) needs `obj`: the table that follows its "**Objeto:** `obj`" line. A letter with a number
    (WF-E1, WF-E2, WF-E3) is a "### WF-E1 ·" subsection of its letter's section, and ends at the next heading of its
    level or above; "E" alone is the whole section, with its three tables, so it needs `obj`.
    """
    lines = read("workflows.md").splitlines()
    heading = re.compile(rf"^(##|###) WF-{re.escape(letter)} ·")
    starts = [i for i, line in enumerate(lines) if heading.match(line)]
    if len(starts) != 1:
        raise ValueError(f"workflows.md has {len(starts)} sections that start with 'WF-{letter} ·', expected 1")
    # a "## " section ends at the next "## "; a "### " subsection also at the next "### "
    level = heading.match(lines[starts[0]]).group(1)
    stops = ("## ",) if level == "##" else ("## ", "### ")
    end = next((i for i in range(starts[0] + 1, len(lines)) if lines[i].startswith(stops)), len(lines))
    start = starts[0]
    if obj is not None:
        subjects = [i for i in range(start, end) if SUBJECT.match(lines[i]) and SUBJECT.match(lines[i]).group(1) == obj]
        if len(subjects) != 1:
            raise ValueError(f"section WF-{letter} has {len(subjects)} '**Objeto:** `{obj}`' lines, expected 1")
        start = subjects[0]
        end = next((i for i in range(start + 1, end) if SUBJECT.match(lines[i])), end)
    tables = [i for i in range(start, end) if lines[i].startswith("| Transición | De | A | Roles |")]
    if len(tables) != 1:
        where = f"WF-{letter}" + (f" ({obj})" if obj else "")
        raise ValueError(f"section {where} has {len(tables)} transition tables, expected 1")
    rows = table_rows(lines, tables[0])
    if not rows:
        raise ValueError(f"the transition table of WF-{letter} has no rows")
    result = []
    for row in rows:
        if len(row) != 8:
            raise ValueError(f"WF-{letter}: a transition row has {len(row)} cells, expected 8: {row[0]!r}")
        name = TRANSITION_NAME.match(row[0])
        if not name:
            raise ValueError(f"WF-{letter}: {row[0]!r} is not '`name` (Label)'")
        source, target = ticks(row[1]), ticks(row[2])
        if len(source) != 1 or len(target) != 1 or row[1] != f"`{source[0]}`" or row[2] != f"`{target[0]}`":
            raise ValueError(f"WF-{letter}: {name.group(1)} has From {row[1]!r} / To {row[2]!r}, expected one `state` each")
        same = SAME_ROLES.match(row[3])
        listed = ROLE_LIST.match(row[3])
        if same:
            donor = next((t for t in result if t["name"] == same.group(1)), None)
            if donor is None:
                raise ValueError(f"WF-{letter}: {name.group(1)} takes its roles from {same.group(1)}, which is not above it")
            roles = list(donor["roles"])
        elif listed:
            bullet = _section_bullet(lines, starts[0], end, row[3])
            if bullet is None:
                raise ValueError(f"WF-{letter}: {name.group(1)} names {row[3]!r}, but the section has no such bullet")
            roles = ROLE.findall(bullet)
        else:
            roles = ROLE.findall(row[3])
        if not roles:
            raise ValueError(f"WF-{letter}: {name.group(1)} has no roles in {row[3]!r}")
        result.append({"name": name.group(1), "label": name.group(2), "from": source[0], "to": target[0], "roles": roles})
    return result


# ---------------------------------------------------------------------------
# workflows.md: automations and document types
# ---------------------------------------------------------------------------

AFTER_GAP = re.compile(r"tras (UPS-\d+)")


def _phase_cell(cell):
    """('F3', 'UPS-23') from 'F3, tras UPS-23'; ('F4', None) from 'F4'."""
    phase = re.match(r"(F\d)", cell)
    if not phase:
        raise ValueError(f"phase cell {cell!r} does not start with a phase")
    rest = cell[phase.end():].strip()
    gap = AFTER_GAP.fullmatch(rest.lstrip(",").strip()) if rest else None
    if rest and not gap:
        raise ValueError(f"phase cell {cell!r}: after the phase only ', tras UPS-n' is understood")
    return phase.group(1), gap.group(1) if gap else None


def automations():
    """The rows of the AUT-NN table of workflows.md: [{id, name, object, trigger, state, transition, phase, after}].

    `trigger` is the trigger type (`STATE_ENTERED`, `RECORD_CREATED`…); `state` and `transition` the state or the
    transition it names, if any; `after` the upstream gap the row waits for ("F3, tras UPS-23"), or None."""
    lines = read("workflows.md").splitlines()
    start = next((i for i, line in enumerate(lines) if line.startswith("| ID | Nombre técnico | Objeto | Disparador |")),
                 None)
    if start is None:
        raise ValueError("workflows.md has no automations table (a renamed header?)")
    result = []
    for row in table_rows(lines, start):
        if len(row) != 8:
            raise ValueError(f"automation row {row[0]!r} has {len(row)} cells, expected 8")
        names = ticks(row[1])
        objects = ticks(row[2])
        trigger = ticks(row[3])
        if not re.fullmatch(r"AUT-\d{2}", row[0]) or not names or len(objects) != 1 or not trigger:
            raise ValueError(f"automation row {row[0]!r} is not 'AUT-NN | `name` | `object` | `TRIGGER` …'")
        phase, after = _phase_cell(row[6])
        kind = trigger[0]
        named = trigger[1] if len(trigger) > 1 else None
        result.append({
            "id": row[0], "name": names[0], "object": objects[0], "trigger": kind,
            "state": named if kind == "STATE_ENTERED" else None,
            # no automation uses TRANSITION_APPLIED yet, but workflows.md lists it among the triggers
            "transition": named if kind == "TRANSITION_APPLIED" else None,
            "phase": phase, "after": after,
        })
    in_document = sum(1 for line in lines if re.match(r"^\| AUT-\d{2} \|", line))
    if in_document != len(result):
        raise ValueError(f"workflows.md has {in_document} AUT-NN rows, but {len(result)} read (a blank line inside the table?)")
    return result


def document_types():
    """The rows of the document types table of workflows.md: [{name, label, prefix, object, phase, after}]."""
    lines = read("workflows.md").splitlines()
    start = next((i for i, line in enumerate(lines)
                  if line.startswith("| Tipo de documento | Nombre técnico | Prefijo | Objeto |")), None)
    if start is None:
        raise ValueError("workflows.md has no document types table (a renamed header?)")
    result = []
    for row in table_rows(lines, start):
        if len(row) != 6:
            raise ValueError(f"document type row {row[0]!r} has {len(row)} cells, expected 6")
        names, prefixes, objects = ticks(row[1]), ticks(row[2]), ticks(row[3])
        if len(names) != 1 or len(prefixes) != 1 or len(objects) != 1:
            raise ValueError(f"document type row {row[0]!r} is not 'Label | `name` | `PREFIX` | `object` …'")
        phase, after = _phase_cell(row[4])
        result.append({"label": row[0], "name": names[0], "prefix": prefixes[0], "object": objects[0], "phase": phase,
                       "after": after})
    in_document = sum(1 for line in lines if re.match(r"^\| [^|`]+ \| `[a-z-]+` \| `[A-Z]+` \| `[a-z_]+` \|", line))
    if in_document != len(result):
        raise ValueError(f"workflows.md has {in_document} document type rows, but {len(result)} read "
                         f"(a blank line inside the table?)")
    return result


def tenant_objects():
    """The masters of ProjectScope.TENANT that security.md §9.7 lists in its "Maestros del tenant" bullet, in order."""
    lines = read("security.md").splitlines()
    start = next((i for i, line in enumerate(lines) if line.startswith("### 9.7 ")), None)
    if start is None:
        raise ValueError("security.md has no §9.7")
    bullets = _bullets(lines, start + 1)
    key = next((k for k in bullets if k.startswith("Maestros del tenant")), None)
    if key is None:
        raise ValueError("security.md §9.7 has no 'Maestros del tenant' bullet")
    names = [name for name in ticks(bullets[key]) if re.fullmatch(r"[a-z][a-z0-9_]*", name)]
    if not names:
        raise ValueError("the 'Maestros del tenant' bullet of security.md §9.7 names no object")
    return names


# ---------------------------------------------------------------------------
# gis.md §2.2: the public layers
# ---------------------------------------------------------------------------

def public_layers():
    """{LYR-NN: [(object, field)]} of the layers of gis.md §2.2 whose sensitivity is "Pública", in table order: the only
    ones GeoServer publishes (security.md §10.2)."""
    lines = read("gis.md").splitlines()
    start = next((i for i, line in enumerate(lines) if line.startswith("| ID | Capa | Objeto.campo |")), None)
    if start is None:
        raise ValueError("gis.md has no layer table (a renamed header?)")
    header = [c.strip() for c in lines[start].strip().strip("|").split("|")]
    if "Objeto.campo" not in header or "Sensibilidad" not in header:
        raise ValueError(f"the layer table of gis.md lacks the Objeto.campo or Sensibilidad column: {header}")
    fields, sensitivity = header.index("Objeto.campo"), header.index("Sensibilidad")
    result = {}
    for cells in table_rows(lines, start):
        if len(cells) != len(header):
            raise ValueError(f"layer row {cells[0]!r} has {len(cells)} cells for {len(header)} columns")
        if cells[sensitivity].startswith("Pública"):
            result[cells[0]] = [tuple(t.split(".", 1)) for t in ticks(cells[fields])
                                if re.fullmatch(r"[a-z_]+\.[a-z_]+", t)]
    return result
