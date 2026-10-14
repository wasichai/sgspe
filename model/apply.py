#!/usr/bin/env python3
"""Apply model/model.json to a wasichai Core (SGSPE).

Copied from caja-backend@dc9e391 (model/apply.py), itself adapted from wasichai's
examples/gis-sample/perene/apply.py. Stdlib only.

Reads a JSON metadata model (objects, fields, relationships, enums), checks it against Core's rules (--validate-only
stops there) and creates it in Core via the REST API: every object first, then the relationships, marking each required
relationship's field required. A third phase, after the relationships, puts what needs them: each
object's uniqueConstraints and the `unique: true` of a relationship's field. An object may declare actions of its own
(POST .../actions, a 409 is "already there"): roles are granted them next to READ, CREATE, UPDATE and DELETE. From F2 a
relationship may be MANY_TO_MANY (a join table, no field), point at its own object or close a cycle: every object
exists before the first relationship, so the order of `objects` does not matter. A field may carry `indexed` and
`defaultValue` (since wasichai 0.5.0 it applies it on create to an omitted field, not on update nor to an explicit
null; UPS-15), and an object `indexes`; the sets that name a relationship's field wait for phase 3, like the
uniqueConstraints.

On a Core that already has the model it syncs instead: it adds the fields and the ENUM options model.json has and Core
lacks, declares the actions of each object, drops the ENUM options it no longer lists and no record uses, relaxes a
field model.json no longer requires, relabels one labelled differently and puts an object's write rules (apiOnly,
appendOnly, requiresReason) and its uniqueConstraints when they differ. It never renames, retypes, makes required or
removes a field or an option in use. --drop tears everything down in reverse order (data included; development only).

A field may carry "derived": true. Only SGSPE's tools read it (apply_roles.py leaves a derived field writable when it
expands `u`); this script never sends it, because it publishes a fixed set of keys. "editable" is refused: wasichai
skips non-editable fields on every write, the platform's too (security.md §6.3).

--workflows is the second pass of a tenant's provisioning, after apply_roles.py, because wasichai refuses a workflow
naming a role the tenant lacks: workflows, document types, automations, views, forms and pages, each from its phase.
F2 brings the workflows (model/workflows/<object>.json, PUT /api/objects/{object}/workflow); F3 the document types
(model/document-types/<object>.json, a list, /api/objects/{object}/document-types) and then the automations
(model/automations/<object>.json, a list, /api/objects/{object}/automations), which name states and document types: one
that Core lacks is created, one that differs is replaced, an equal one is skipped, and an automation Core has and
model/ does not is reported, never deleted. From F4 a condition's value must be one its field can hold (an option of
its ENUM, true or false, a number, a state of the workflow): wasichai compares it as text and would skip every run.
Views, forms and pages still refuse to run until their phase. wasichai
checks no record on that PUT, so nothing is written until every workflow is checked: one that drops
a state records sit in stops the pass with 1 (naming the states and counts), and so does one created on an object
that already has records, which wasichai leaves with a null state, unless --allow-stateless-records is given (then
it only warns).
"""
import argparse
import json
import os
import re
import sys
import urllib.parse

from core_client import Client, CoreError

# Keyword list copied verbatim from
# wasichai-core/src/main/kotlin/wasichai/core/platform/SqlIdentifier.kt (SQL_KEYWORDS).
SQL_KEYWORDS = frozenset({
    "all", "alter", "and", "any", "array", "as", "asc", "begin", "between",
    "by", "case", "cast", "check", "column", "commit", "constraint",
    "create", "cross", "current_date", "current_time", "current_timestamp",
    "current_user", "default", "delete", "desc", "distinct", "do", "drop",
    "else", "end", "except", "exists", "false", "fetch", "for", "foreign",
    "from", "full", "grant", "group", "having", "in", "index", "inner",
    "insert", "intersect", "into", "is", "join", "key", "left", "like",
    "limit", "not", "null", "offset", "on", "or", "order", "outer",
    "primary", "references", "returning", "right", "rollback", "select",
    "session_user", "set", "some", "table", "then", "to", "true", "union",
    "unique", "update", "user", "using", "values", "view", "when", "where",
    "with",
})

# Field names Core reserves: system columns + query-param names used on list endpoints.
RESERVED_FIELD_NAMES = frozenset({
    "id", "organization_id", "created_at", "updated_at", "created_by",
    "updated_by", "workflow_state", "version",
    "page", "size", "sort", "dir", "q", "bbox", "geometry", "limit", "count", "after",
})

# the write rules an object declares (ADR-040): both default to false
OBJECT_FLAGS = ("apiOnly", "appendOnly", "requiresReason")

# an action an object declares (ObjectActions of Core): roles are granted it next to READ, CREATE, UPDATE and DELETE
ACTION_NAME = re.compile(r"^[A-Z][A-Z0-9_]{1,48}$")
CRUD_ACTIONS = ("READ", "CREATE", "UPDATE", "DELETE")

VALID_NAME = re.compile(r"^[a-z][a-z0-9_]{0,48}$")

# Mirror Core's ENUM option regex (metadata/MetadataService.kt):
# ^[\p{L}0-9 _.-]{1,64}$ -- Unicode letters, ASCII digits, space, `_`, `.`, `-`.
# Checked per character: Python's `\w` tricks also match non-letter "word" characters.
ENUM_OPTION_EXTRA_CHARS = re.compile(r"[0-9 _.\-]")


def enum_option_valid(opt):
    if not isinstance(opt, str) or not (1 <= len(opt) <= 64):
        return False
    return all(c.isalpha() or ENUM_OPTION_EXTRA_CHARS.fullmatch(c) for c in opt)


MAX_OBJECT_NAME = 39
MAX_FIELD_NAME = 49
MAX_RELATIONSHIP_NAME = 35

# core's scalar types, plus GEOMETRY from wasichai-gis (SGSPE installs it in F2) and FILE from wasichai-files (F3,
# WAS-ADR-0061)
FIELD_TYPES = frozenset({
    "TEXT", "LONG_TEXT", "INTEGER", "DECIMAL", "BOOLEAN", "DATE", "DATETIME",
    "ENUM", "EMAIL", "URL", "UUID", "GEOMETRY", "FILE",
})

# the relationship types SGSPE uses (database-model.md §2.6): MANY_TO_ONE when the key is left out
RELATIONSHIP_TYPES = ("MANY_TO_ONE", "MANY_TO_MANY")

# types that take no declared index (database-model.md §2.7). a FILE column gets its own index from wasichai-files and
# cannot be filtered or sorted on, so it is in no set either
NOT_INDEXABLE = ("LONG_TEXT", "GEOMETRY", "FILE")

# mirror wasichai-files's FileFieldType: a media type of a FILE field's allow-list
MEDIA_TYPE = re.compile(r"^[a-z0-9][a-z0-9!#$&^_.+-]{0,62}/([a-z0-9][a-z0-9!#$&^_.+-]{0,62}|\*)$")

# mirror wasichai-gis's GeometryFieldType: a geometry names its shape; srid 1..999999 (default 4326), 2d or 3d
GEOMETRY_TYPES = frozenset({"POINT", "LINESTRING", "POLYGON", "MULTIPOINT", "MULTILINESTRING", "MULTIPOLYGON"})


# ---------------------------------------------------------------------------
# validation
# ---------------------------------------------------------------------------

def _check_identifier(name, kind, maxlen, label):
    """Mirror SqlIdentifier.requireValidName: shape, length, not a keyword."""
    errors = []
    if not isinstance(name, str) or not name:
        return [f"{label}: {kind} name is missing"]
    if len(name) > maxlen:
        errors.append(f"{label}: {kind} name '{name}' exceeds {maxlen} characters")
    if not VALID_NAME.fullmatch(name):
        errors.append(
            f"{label}: {kind} name '{name}' must match ^[a-z][a-z0-9_]{{0,48}}$"
        )
    if name in SQL_KEYWORDS:
        errors.append(f"{label}: {kind} name '{name}' is a reserved SQL keyword")
    return errors


def validate(model):
    """Pure structural + Core-mirroring validation. Returns a list of error strings."""
    errors = []

    enums = model.get("enums")
    if not isinstance(enums, dict):
        errors.append("enums must be an object")
        enums = {}
    else:
        for ename, opts in enums.items():
            if not isinstance(opts, list) or not opts or not all(isinstance(o, str) for o in opts):
                errors.append(f"enum {ename}: must be a non-empty list of strings")
                continue
            seen_opts = set()
            for opt in opts:
                if not enum_option_valid(opt):
                    errors.append(f"enum {ename}: option '{opt}' has invalid characters or length")
                if opt in seen_opts:
                    errors.append(f"enum {ename}: duplicate option '{opt}'")
                seen_opts.add(opt)

    # F1 has no objects yet: an empty list is a valid model
    objects = model.get("objects")
    if not isinstance(objects, list):
        errors.append("objects must be a list")
        objects = []

    relationships = model.get("relationships")
    if not isinstance(relationships, list):
        errors.append("relationships must be a list")
        relationships = []

    seen_objects = set()
    fields_by_object = {}
    types_by_object = {}

    for obj in objects:
        oname = obj.get("name", "")
        label = f"object {oname}"
        if oname in seen_objects:
            errors.append(f"{label}: duplicate object name")
        seen_objects.add(oname)
        errors.extend(_check_identifier(oname, "object", MAX_OBJECT_NAME, label))
        for flag in OBJECT_FLAGS:
            if flag in obj and not isinstance(obj[flag], bool):
                errors.append(f"{label}: {flag} must be true or false")

        field_names = set()
        for field in obj.get("fields", []):
            fname = field.get("name", "")
            ftype = field.get("type")
            flabel = f"{label} field {fname}"
            if fname in field_names:
                errors.append(f"{flabel}: duplicate field name")
            field_names.add(fname)

            errors.extend(_check_identifier(fname, "field", MAX_FIELD_NAME, flabel))
            if fname in RESERVED_FIELD_NAMES:
                errors.append(f"{flabel}: field name is reserved by Core")
            # a derived field says so with "derived": true, which only SGSPE's tools read; never editable: false, which
            # wasichai applies to every write, the platform's too (security.md §6.3)
            if "derived" in field and not isinstance(field["derived"], bool):
                errors.append(f"{flabel}: derived must be true or false")
            if "editable" in field:
                errors.append(f"{flabel}: editable is not used; a derived field is marked derived: true")

            if ftype == "RELATION":
                errors.append(f"{flabel}: type RELATION is not allowed here (relationships create it)")
            elif ftype not in FIELD_TYPES:
                errors.append(f"{flabel}: unknown type '{ftype}'")

            if ftype == "GEOMETRY":
                if str(field.get("geometryType", "")).upper() not in GEOMETRY_TYPES:
                    errors.append(f"{flabel}: geometryType must be one of {', '.join(sorted(GEOMETRY_TYPES))}")
                srid = field.get("srid", 4326)
                if not isinstance(srid, int) or not 1 <= srid <= 999999:
                    errors.append(f"{flabel}: srid must be an integer between 1 and 999999")
                if field.get("dimension", 2) not in (2, 3):
                    errors.append(f"{flabel}: dimension must be 2 or 3")
                if field.get("unique") or field.get("required"):
                    errors.append(f"{flabel}: a geometry can be neither unique nor required here")

            # mirror wasichai-files's FileFieldType: a file is uploaded, never unique nor defaulted, and takes an allow-list
            # of media types (type/subtype or type/*)
            if ftype == "FILE" and field.get("unique"):
                errors.append(f"{flabel}: a file can not be unique")
            if "maxBytes" in field:
                limit = field["maxBytes"]
                if ftype != "FILE":
                    errors.append(f"{flabel}: only a FILE field takes maxBytes")
                elif not isinstance(limit, int) or isinstance(limit, bool) or limit < 1:
                    errors.append(f"{flabel}: maxBytes must be a whole number of bytes of 1 or more (the server ceiling "
                                  f"wasichai.files.max-bytes still applies)")
            if "contentTypes" in field:
                types = field["contentTypes"]
                if ftype != "FILE":
                    errors.append(f"{flabel}: only a FILE field takes contentTypes")
                elif not isinstance(types, list) or not types or not all(isinstance(t, str) and MEDIA_TYPE.fullmatch(t)
                                                                        for t in types):
                    errors.append(f"{flabel}: contentTypes must be a non-empty list of media types such as image/png or image/*")

            if ftype == "ENUM":
                enum_name = field.get("enum")
                if not enum_name or enum_name not in enums:
                    errors.append(f"{flabel}: enum '{enum_name}' is not defined in enums")

            if "indexed" in field:
                if not isinstance(field["indexed"], bool):
                    errors.append(f"{flabel}: indexed must be true or false")
                elif field["indexed"] and ftype in NOT_INDEXABLE:
                    errors.append(f"{flabel}: a {ftype} field cannot be indexed")
            if "defaultValue" in field:
                errors.extend(_check_default(field, enums, flabel))

        fields_by_object[oname] = field_names
        types_by_object[oname] = {f.get("name"): f.get("type") for f in obj.get("fields", [])}

    rel_names = set()
    seen_source_field_names = set()
    for rel in relationships:
        rname = rel.get("name", "")
        label = f"relationship {rname}"
        if rname in rel_names:
            errors.append(f"{label}: duplicate relationship name")
        rel_names.add(rname)
        errors.extend(_check_identifier(rname, "relationship", MAX_RELATIONSHIP_NAME, label))

        source = rel.get("source")
        target = rel.get("target")
        if source not in seen_objects:
            errors.append(f"{label}: source '{source}' is not a known object")
        if target not in seen_objects:
            errors.append(f"{label}: target '{target}' is not a known object")

        # every object is created before the first relationship: a self-reference or a cycle is fine (§2.6)

        rtype = rel.get("type", "MANY_TO_ONE")
        if rtype not in RELATIONSHIP_TYPES:
            errors.append(f"{label}: type must be one of {', '.join(RELATIONSHIP_TYPES)}")
            continue
        if rtype == "MANY_TO_MANY":
            for key in ("fieldName", "required", "unique"):
                if key in rel:
                    errors.append(f"{label}: a MANY_TO_MANY relationship has no {key} (it is a join table)")
            continue

        if "required" in rel and not isinstance(rel["required"], bool):
            errors.append(f"{label}: required must be true or false")
        field_name = rel.get("fieldName")
        errors.extend(_check_identifier(field_name, "field", MAX_FIELD_NAME, label))
        if field_name in RESERVED_FIELD_NAMES:
            errors.append(f"{label}: fieldName '{field_name}' is reserved by Core")
        if source in fields_by_object and field_name in fields_by_object[source]:
            errors.append(f"{label}: fieldName '{field_name}' collides with an existing field of {source}")

        source_field_key = (source, field_name)
        if source_field_key in seen_source_field_names:
            errors.append(
                f"{label}: fieldName '{field_name}' duplicates another relationship from {source}"
            )
        seen_source_field_names.add(source_field_key)

        if "unique" in rel and not isinstance(rel["unique"], bool):
            errors.append(f"{label}: unique must be true or false")

    for obj in objects:
        label = f"object {obj.get('name', '')}"
        actions = obj.get("actions", [])
        if not isinstance(actions, list):
            errors.append(f"{label}: actions must be a list of {{name, label}}")
            continue
        seen_actions = set()
        for action in actions:
            aname = action.get("name") if isinstance(action, dict) else None
            if not isinstance(aname, str) or not ACTION_NAME.fullmatch(aname):
                errors.append(f"{label}: action name {aname!r} must match {ACTION_NAME.pattern}")
                continue
            if aname in CRUD_ACTIONS:
                errors.append(f"{label}: action {aname} is one of Core's own")
            if aname in seen_actions:
                errors.append(f"{label}: duplicate action {aname}")
            seen_actions.add(aname)
            if not action.get("label"):
                errors.append(f"{label}: action {aname} has no label")

    # a set may name a relationship's field: it is declared once the relationship exists (phase 3)
    relation_fields = {(r.get("source"), r.get("fieldName")) for r in relationships if r.get("fieldName")}
    for obj in objects:
        oname = obj.get("name", "")
        label = f"object {oname}"
        for key, single in (("uniqueConstraints", "unique: true"), ("indexes", "indexed: true")):
            sets = obj.get(key, [])
            if not isinstance(sets, list):
                errors.append(f"{label}: {key} must be a list of field lists")
                continue
            seen_sets = []
            for fields in sets:
                if not isinstance(fields, list) or not all(isinstance(n, str) for n in fields):
                    errors.append(f"{label}: a {key} entry must be a list of field names")
                    continue
                # wasichai keeps each set once: a repeat would never match what it stores and be put on every run
                if fields in seen_sets:
                    errors.append(f"{label}: {key} {fields} is declared twice")
                seen_sets.append(fields)
                if len(fields) < 2:
                    errors.append(f"{label}: {key} {fields} needs at least 2 fields (one field is {single})")
                if len(set(fields)) != len(fields):
                    errors.append(f"{label}: {key} {fields} names a field twice")
                for fname in fields:
                    ftype = types_by_object.get(oname, {}).get(fname)
                    if fname not in fields_by_object.get(oname, set()) and (oname, fname) not in relation_fields:
                        errors.append(f"{label}: {key} {fields} names '{fname}', which is not a field of {oname}")
                    elif ftype in NOT_INDEXABLE:
                        errors.append(f"{label}: {key} {fields} names '{fname}', a {ftype} field cannot be in it")

    return errors


def _check_default(field, enums, flabel):
    """A defaultValue is text Core keeps as given: an ENUM's must be one of its options, a BOOLEAN's true or false, a
    number's a number. A geometry takes none."""
    value = field["defaultValue"]
    ftype = field.get("type")
    if not isinstance(value, str) or not value:
        return [f"{flabel}: defaultValue must be a non-empty string"]
    if ftype == "GEOMETRY":
        return [f"{flabel}: a geometry takes no defaultValue"]
    if ftype == "FILE":
        return [f"{flabel}: a file takes no defaultValue"]
    if ftype == "ENUM" and value not in enums.get(field.get("enum"), []):
        return [f"{flabel}: defaultValue '{value}' is not an option of enum '{field.get('enum')}'"]
    if ftype == "BOOLEAN" and value not in ("true", "false"):
        return [f"{flabel}: defaultValue of a BOOLEAN must be 'true' or 'false'"]
    if ftype == "INTEGER" and not re.fullmatch(r"-?\d+", value):
        return [f"{flabel}: defaultValue of an INTEGER must be a whole number"]
    if ftype == "DECIMAL" and not re.fullmatch(r"-?\d+(\.\d+)?", value):
        return [f"{flabel}: defaultValue of a DECIMAL must be a number"]
    return []


# ---------------------------------------------------------------------------
# payload builders (pure)
# ---------------------------------------------------------------------------

def field_payload(model, f):
    field = {
        "name": f["name"],
        "label": f["label"],
        "type": f["type"],
        "required": f.get("required", False),
        "unique": f.get("unique", False),
    }
    if "description" in f:
        field["description"] = f["description"]
    if f.get("indexed"):
        field["indexed"] = True
    if "defaultValue" in f:
        field["defaultValue"] = f["defaultValue"]
    if f["type"] == "ENUM":
        field["enumOptions"] = model["enums"][f["enum"]]
    if f["type"] == "FILE" and "contentTypes" in f:
        field["contentTypes"] = f["contentTypes"]
    if f["type"] == "FILE" and "maxBytes" in f:
        field["maxBytes"] = f["maxBytes"]
    if f["type"] == "GEOMETRY":
        field["geometryType"] = f["geometryType"].upper()
        field["srid"] = f.get("srid", 4326)
        field["dimension"] = f.get("dimension", 2)
    return field


def object_flags(obj):
    """The write rules model.json gives an object, both always present."""
    return {flag: obj.get(flag, False) for flag in OBJECT_FLAGS}


def unique_constraints(obj):
    """The field sets model.json makes unique on an object, in its order."""
    return obj.get("uniqueConstraints", [])


def declared_indexes(obj):
    """The field sets model.json indexes together on an object, in its order."""
    return obj.get("indexes", [])


def _own(obj, sets):
    """The sets whose fields are all the object's own: Core takes those in the POST. One that names a relationship's
    field waits for the relationship (phase 3)."""
    own = {f["name"] for f in obj["fields"]}
    return [fields for fields in sets if all(name in own for name in fields)]


def _own_unique_constraints(obj):
    return _own(obj, unique_constraints(obj))


def object_payload(model, obj):
    uniques = _own_unique_constraints(obj)
    indexes = _own(obj, declared_indexes(obj))
    return {
        "name": obj["name"],
        "label": obj["label"],
        "pluralLabel": obj["pluralLabel"],
        "description": obj.get("description", ""),
        "fields": [field_payload(model, f) for f in obj["fields"]],
        **object_flags(obj),
        **({"uniqueConstraints": uniques} if uniques else {}),
        **({"indexes": indexes} if indexes else {}),
    }


def object_update_payload(obj, **flags):
    """The body of PUT /api/objects/{name}, which REPLACES the object: what it leaves out reverts (a missing
    pluralLabel takes the label, a missing description goes null), so the labels go every time. uniqueConstraints is
    left out: null keeps the ones Core has (a caller that changes them adds the key)."""
    return {
        "label": obj["label"],
        "pluralLabel": obj["pluralLabel"],
        "description": obj.get("description", ""),
        "enabled": True,
        **{**object_flags(obj), **flags},
    }


def missing_options(model, field, existing):
    """Options of an ENUM field in model.json that Core does not have yet, in model order."""
    if field["type"] != "ENUM":
        return []
    have = set(existing.get("enumOptions") or [])
    return [o for o in model["enums"][field["enum"]] if o not in have]


def dropped_options(model, field, existing):
    """Options Core has for an ENUM field that model.json no longer lists, in Core's order."""
    if field["type"] != "ENUM":
        return []
    keep = set(model["enums"][field["enum"]])
    return [o for o in existing.get("enumOptions") or [] if o not in keep]


def _records_using(client, object_name, field_name, option):
    """How many records of the object store the option in the field. An answer without that count (a 404, another
    shape) is an error, not a zero: the option is never dropped on a guess."""
    query = urllib.parse.urlencode({"page": 0, "size": 1, field_name: option})
    data = client.get(f"/api/objects/{object_name}/records?{query}")
    total = data.get("totalElements") if isinstance(data, dict) else None
    if not isinstance(total, int):
        raise CoreError("no record count", json.dumps(data))
    return total


def relationship_payload(rel):
    """POST /api/relationships: a MANY_TO_ONE names the field it creates on the source; a MANY_TO_MANY, a join table,
    names none."""
    rtype = rel.get("type", "MANY_TO_ONE")
    payload = {
        "name": rel["name"],
        "label": rel["label"],
        "inverseLabel": rel["inverseLabel"],
        "type": rtype,
        "source": rel["source"],
        "target": rel["target"],
    }
    if rtype == "MANY_TO_ONE":
        payload["fieldName"] = rel["fieldName"]
    return payload


# ---------------------------------------------------------------------------
# CLI
# ---------------------------------------------------------------------------

def _existing_objects(data):
    """The objects Core has, by name, with the flags and constraints GET /api/objects already carries."""
    if isinstance(data, dict):
        data = data.get("items", data.get("content", []))
    return {o["name"]: o for o in (data or [])}


def _fatal(method, path, err):
    print(f"error {method} {path} -> {err.status}\n{err.body}", file=sys.stderr)


def _apply_required_put(client, rel):
    """Returns True to keep going, False if this was fatal (caller must stop and exit 1).

    Unlike object/relationship POSTs (which tolerate 409 "ya existe"), this PUT tolerates
    nothing: every non-2xx response is fatal.
    """
    if not rel.get("required"):
        return True
    path = f"/api/metadata/objects/{rel['source']}/fields/{rel['fieldName']}"
    try:
        client.put(path, {"required": True})
    except CoreError as e:
        _fatal("PUT", path, e)
        return False
    return True


def _fields_by_name(data):
    if isinstance(data, dict):
        data = data.get("items", data.get("content", []))
    return {f["name"]: f for f in (data or [])}


def _put_field(client, path, body, what, detail):
    """PUT one field's metadata and report it as `update field <what> (<detail>)`. False when Core refused (already
    reported)."""
    try:
        client.put(path, body)
    except CoreError as e:
        _fatal("PUT", path, e)
        return False
    print(f"update field {what} ({detail})")
    return True


def sync_object(client, model, obj, current_object=None):
    """An object that already exists: put its write rules (apiOnly, appendOnly, requiresReason) when they differ from model.json's, add the fields model.json has and Core lacks, and the ENUM options
    it lacks, drop the ENUM options model.json no longer lists and no record uses, make optional what model.json no
    longer requires, and relabel what it labels differently. Nothing is renamed, retyped or made required, and no
    field or used option is removed, so imported records stay valid.
    Returns (added, updated), or None when Core refused (already reported)."""
    name = obj["name"]
    added = updated = 0
    wanted = object_flags(obj)
    if current_object is not None and any(bool(current_object.get(f)) != wanted[f] for f in OBJECT_FLAGS):
        object_path = f"/api/objects/{name}"
        try:
            client.put(object_path, object_update_payload(obj))
        except CoreError as e:
            _fatal("PUT", object_path, e)
            return None
        print(f"update object {name} (apiOnly={str(wanted['apiOnly']).lower()}, appendOnly={str(wanted['appendOnly']).lower()}, requiresReason={str(wanted['requiresReason']).lower()})")
        updated += 1
    path = f"/api/metadata/objects/{name}/fields"
    try:
        existing = _fields_by_name(client.get(path))
    except CoreError as e:
        _fatal("GET", path, e)
        return None
    for field in obj["fields"]:
        current = existing.get(field["name"])
        if current is None:
            try:
                status, _ = client.post(path, field_payload(model, field))
            except CoreError as e:
                _fatal("POST", path, e)
                return None
            print(f"add    field {name}.{field['name']} ({status})")
            added += 1
            continue
        extra = missing_options(model, field, current)
        dropped = []
        for option in dropped_options(model, field, current):
            try:
                using = _records_using(client, name, field["name"], option)
            except CoreError as e:
                _fatal("GET", f"/api/objects/{name}/records", e)
                return None
            if using:
                records = "1 record uses" if using == 1 else f"{using} records use"
                print(f"keep   option {name}.{field['name']} {option}: {records} it")
            else:
                dropped.append(option)
        if extra or dropped:
            # model.json's order (page 5 sorts the tipos de unidad urbana), then what it no longer lists and a record uses
            kept = [o for o in dropped_options(model, field, current) if o not in dropped]
            options = model["enums"][field["enum"]] + kept
            changes = ([f"+{', '.join(extra)}"] if extra else []) + ([f"-{', '.join(dropped)}"] if dropped else [])
            if not _put_field(client, f"{path}/{field['name']}", {"enumOptions": options}, f"{name}.{field['name']}",
                              "; ".join(changes)):
                return None
            updated += 1
        # a field model.json stopped requiring is relaxed (always safe); one it started requiring is left alone
        if current.get("required") and not field.get("required"):
            if not _put_field(client, f"{path}/{field['name']}", {"required": False}, f"{name}.{field['name']}",
                              "optional"):
                return None
            updated += 1
        # indexed and the description are only metadata and an index: model.json's win. wasichai trims a description
        # (and keeps none as null), so that is what is compared
        indexed = bool(field.get("indexed", False))
        if bool(current.get("indexed", False)) != indexed:
            if not _put_field(client, f"{path}/{field['name']}", {"indexed": indexed}, f"{name}.{field['name']}",
                              f"indexed={str(indexed).lower()}"):
                return None
            updated += 1
        description = field.get("description", "").strip() if "description" in field else None
        if description is not None and (current.get("description") or "") != description:
            if not _put_field(client, f"{path}/{field['name']}", {"description": description}, f"{name}.{field['name']}",
                              "description"):
                return None
            updated += 1
        # a label is only what the admin shows: model.json's wins
        if current.get("label") != field["label"]:
            if not _put_field(client, f"{path}/{field['name']}", {"label": field["label"]}, f"{name}.{field['name']}",
                              "label"):
                return None
            updated += 1
    # a field model.json no longer has is reported, never removed (it may hold data); one that is required is relaxed, so
    # a record written without it is still valid. a relationship's field is not model.json's field list: it is not extra
    wanted_names = {f["name"] for f in obj["fields"]} | {r["fieldName"] for r in model["relationships"]
                                                        if r["source"] == name and r.get("fieldName")}
    for field_name, current in existing.items():
        if field_name in wanted_names or current.get("type") == "RELATION":
            continue
        print(f"extra  field {name}.{field_name} (model.json no longer has it)")
        if current.get("required"):
            if not _put_field(client, f"{path}/{field_name}", {"required": False}, f"{name}.{field_name}", "optional"):
                return None
            updated += 1
    return added, updated


def do_apply(client, model):
    created = 0
    updated = 0
    skipped = 0

    existing = _existing_objects(client.get("/api/objects"))
    for obj in model["objects"]:
        name = obj["name"]
        if name in existing:
            synced = sync_object(client, model, obj, existing[name])
            if synced is None:
                return 1
            added, changed = synced
            created += added
            updated += changed
            if not added and not changed:
                print(f"skip   object {name} (exists)")
                skipped += 1
            continue
        try:
            status, _ = client.post("/api/objects", object_payload(model, obj))
            print(f"create object {name} ({status})")
            created += 1
        except CoreError as e:
            if e.status == 409:
                print(f"skip   object {name} (exists)")
                skipped += 1
            else:
                _fatal("POST", "/api/objects", e)
                return 1

    declared = _apply_actions(client, model)
    if declared is None:
        return 1
    created += declared[0]
    skipped += declared[1]

    for rel in model["relationships"]:
        name = rel["name"]
        try:
            status, _ = client.post("/api/relationships", relationship_payload(rel))
            print(f"create relationship {name} ({status})")
            created += 1
        except CoreError as e:
            if e.status == 409:
                print(f"skip   relationship {name} (exists)")
                skipped += 1
            else:
                _fatal("POST", "/api/relationships", e)
                return 1
        if not _apply_required_put(client, rel):
            return 1

    changed = _apply_field_sets(client, model)
    if changed is None:
        return 1
    updated += changed

    print(f"done: {created} created, {updated} updated, {skipped} skipped")
    return 0


def _apply_actions(client, model):
    """The declared actions of every object (POST /api/metadata/objects/{o}/actions): a role can be granted one only
    once it is declared. A repeat is a 409 and counts as already there. Returns (created, skipped), or None when Core
    refused."""
    created = skipped = 0
    for obj in model["objects"]:
        for action in obj.get("actions", []):
            path = f"/api/metadata/objects/{obj['name']}/actions"
            label = f"action {obj['name']}.{action['name']}"
            try:
                status, _ = client.post(path, {"name": action["name"], "label": action["label"]})
                print(f"create {label} ({status})")
                created += 1
            except CoreError as e:
                if e.status != 409:
                    _fatal("POST", path, e)
                    return None
                print(f"skip   {label} (exists)")
                skipped += 1
    return created, skipped


def _apply_field_sets(client, model):
    """Phase 3, once the relationships exist: the uniqueConstraints and the indexes of every object (the full lists, so
    the PUT carries the whole object) and the `unique: true` of a relationship's field. Each only when it differs from
    what Core has (Core leaves a key out of an object with none). Returns how many it put, or None when Core refused."""
    changed = 0
    stored = _existing_objects(client.get("/api/objects"))
    for obj in model["objects"]:
        name = obj["name"]
        wanted = {"uniqueConstraints": unique_constraints(obj), "indexes": declared_indexes(obj)}
        if name not in stored or all(stored[name].get(k, []) == v for k, v in wanted.items()):
            continue
        path = f"/api/objects/{name}"
        try:
            client.put(path, {**object_update_payload(obj), **wanted})
        except CoreError as e:
            _fatal("PUT", path, e)
            return None
        print(f"update object {name} (uniqueConstraints={json.dumps(wanted['uniqueConstraints'])}, "
              f"indexes={json.dumps(wanted['indexes'])})")
        changed += 1
    for rel in model["relationships"]:
        if not rel.get("unique"):
            continue
        path = f"/api/metadata/objects/{rel['source']}/fields"
        try:
            current = _fields_by_name(client.get(path)).get(rel["fieldName"], {})
            if current.get("unique"):
                continue
            client.put(f"{path}/{rel['fieldName']}", {"unique": True})
        except CoreError as e:
            _fatal("PUT", f"{path}/{rel['fieldName']}", e)
            return None
        print(f"update field {rel['source']}.{rel['fieldName']} (unique)")
        changed += 1
    return changed


def _delete_one(client, label, path):
    """Delete one entity; print the result. Returns 'deleted', 'skipped', or None (fatal, already reported)."""
    try:
        status, _ = client.delete(path)
        print(f"delete {label} ({status})")
        return "deleted"
    except CoreError as e:
        if e.status == 404:
            print(f"skip   {label} (not found)")
            return "skipped"
        _fatal("DELETE", path, e)
        return None


def do_drop(client, model):
    deleted = 0
    skipped = 0

    # an appendOnly object keeps its relationships: Core refuses to delete one whose field sits on it, or sits in a
    # uniqueConstraints set. the rules go first, with the labels the PUT replaces; an object already gone has nothing to
    # turn off
    for obj in model["objects"]:
        if not obj.get("appendOnly") and not unique_constraints(obj) and not declared_indexes(obj):
            continue
        path = f"/api/objects/{obj['name']}"
        try:
            client.put(path, {**object_update_payload(obj, appendOnly=False), "uniqueConstraints": [], "indexes": []})
            print(f"update object {obj['name']} (appendOnly=false, uniqueConstraints=[], indexes=[])")
        except CoreError as e:
            if e.status == 404:
                print(f"skip   object {obj['name']} (not found)")
                continue
            _fatal("PUT", path, e)
            return 1

    to_delete = [
        (f"relationship {rel['name']}", f"/api/relationships/{rel['name']}")
        for rel in reversed(model["relationships"])
    ] + [
        (f"object {obj['name']}", f"/api/objects/{obj['name']}")
        for obj in reversed(model["objects"])
    ]
    for label, path in to_delete:
        result = _delete_one(client, label, path)
        if result is None:
            return 1
        deleted += result == "deleted"
        skipped += result == "skipped"

    print(f"done: {deleted} deleted, {skipped} skipped")
    return 0


def _print_dry_run(model):
    for obj in model["objects"]:
        print("# POST /api/objects")
        print(json.dumps(object_payload(model, obj), indent=2, ensure_ascii=False))
    for obj in model["objects"]:
        for action in obj.get("actions", []):
            print(f"# POST /api/metadata/objects/{obj['name']}/actions")
            print(json.dumps({"name": action["name"], "label": action["label"]}, indent=2, ensure_ascii=False))
    for rel in model["relationships"]:
        print("# POST /api/relationships")
        print(json.dumps(relationship_payload(rel), indent=2, ensure_ascii=False))
    for obj in model["objects"]:
        # the sets over the object's own fields went in its POST
        if unique_constraints(obj) != _own_unique_constraints(obj) or declared_indexes(obj) != _own(obj, declared_indexes(obj)):
            print(f"# PUT /api/objects/{obj['name']}")
            print(json.dumps({**object_update_payload(obj), "uniqueConstraints": unique_constraints(obj),
                              "indexes": declared_indexes(obj)}, indent=2, ensure_ascii=False))
    for rel in model["relationships"]:
        if rel.get("unique"):
            print(f"# PUT /api/metadata/objects/{rel['source']}/fields/{rel['fieldName']}")
            print(json.dumps({"unique": True}))


def _print_drop_dry_run(model):
    for obj in model["objects"]:
        if obj.get("appendOnly") or unique_constraints(obj) or declared_indexes(obj):
            print(f"# PUT /api/objects/{obj['name']} (appendOnly=false, uniqueConstraints=[], indexes=[])")
    for rel in reversed(model["relationships"]):
        print(f"# DELETE /api/relationships/{rel['name']}")
    for obj in reversed(model["objects"]):
        print(f"# DELETE /api/objects/{obj['name']}")


# the second pass, after apply_roles.py (wasichai refuses a workflow naming a role the tenant lacks), each kind from
# its phase (architecture.md §5.4): the workflows from F2; the document types and the automations from F3; views, forms
# and pages when their phase brings them
DEFINITION_DIRS = ("workflows", "document-types", "automations", "views", "forms", "pages")
APPLIED_KINDS = ("workflows", "document-types", "automations")

# workflows.md, «Formato JSON de un workflow»: wf-<letter>-<object>; state and transition names as WorkflowService wants
WORKFLOW_NAME = re.compile(r"^wf-[a-z][a-z0-9]*-[a-z][a-z0-9_]*$")
STATE_NAME = re.compile(r"^[a-z][a-z0-9_-]{0,48}$")
STATE_TYPES = ("INITIAL", "INTERMEDIATE", "FINAL")


def definition_files(model_dir):
    """The definition files under model/, by kind and then by name."""
    found = []
    for kind in DEFINITION_DIRS:
        folder = os.path.join(model_dir, kind)
        if os.path.isdir(folder):
            found.extend(f"{kind}/{name}" for name in sorted(os.listdir(folder)) if name.endswith(".json"))
    return found


ROLE_NAME = re.compile(r"^[A-Z0-9_]+$")


def _structure_errors(label, workflow):
    """What would make validate_workflow trip over a shape that is not a workflow: it, its definition, each state and
    each transition must be a JSON object, and states, transitions and roles lists."""
    if not isinstance(workflow, dict):
        return [f"{label}: the file must be a JSON object"]
    definition = workflow.get("definition", {})
    if not isinstance(definition, dict):
        return [f"{label}: definition must be a JSON object"]
    errors = []
    for key, kind in (("states", "state"), ("transitions", "transition")):
        items = definition.get(key, [])
        if not isinstance(items, list):
            errors.append(f"{label}: {key} must be a list")
            continue
        for item in items:
            if not isinstance(item, dict):
                errors.append(f"{label}: a {kind} must be a JSON object")
            elif key == "transitions" and not isinstance(item.get("roles", []), list):
                errors.append(f"{label}: transition {item.get('name')} roles must be a list")
    return errors


def _edge_spaces(text):
    return isinstance(text, str) and text != text.strip()


def validate_workflow(model, object_name, workflow):
    """The errors of model/workflows/<object>.json against model.json and the engine's rules; [] when it is valid."""
    label = f"workflow {object_name}"
    objects = {o["name"]: o for o in model["objects"]}
    if object_name not in objects:
        return [f"{label}: object '{object_name}' is not in model.json"]
    structure = _structure_errors(label, workflow)
    if structure:
        return structure
    errors = []
    if objects[object_name].get("appendOnly"):
        errors.append(f"{label}: an appendOnly object cannot have a workflow")
    if not WORKFLOW_NAME.fullmatch(str(workflow.get("name", ""))) or not workflow["name"].endswith("-" + object_name):
        errors.append(f"{label}: name must be wf-<letter>-{object_name}")
    # wasichai trims a label and keeps a role as given: one with edge spaces or lower case would differ on every run
    if not workflow.get("label") or not isinstance(workflow["label"], str):
        errors.append(f"{label}: label is missing")
    elif _edge_spaces(workflow["label"]):
        errors.append(f"{label}: label must have no leading or trailing spaces")
    if workflow.get("enabled") is not True:
        errors.append(f"{label}: enabled must be true (ASM-010 is a variant of the definition, not a switch)")
    definition = workflow.get("definition") or {}
    states = definition.get("states") or []
    transitions = definition.get("transitions") or []
    names = [s.get("name") for s in states]
    for state in states:
        if not STATE_NAME.fullmatch(str(state.get("name", ""))) or not state.get("label"):
            errors.append(f"{label}: state {state.get('name')!r} needs a valid name and a label")
        elif _edge_spaces(state["label"]):
            errors.append(f"{label}: state {state.get('name')!r} label must have no leading or trailing spaces")
        if state.get("type") not in STATE_TYPES:
            errors.append(f"{label}: state {state.get('name')} type must be one of {', '.join(STATE_TYPES)}")
        if "x" in state or "y" in state:
            errors.append(f"{label}: state {state.get('name')} carries x or y; the canvas places it")
    if len(set(names)) != len(names):
        errors.append(f"{label}: a state name repeats")
    if sum(1 for s in states if s.get("type") == "INITIAL") != 1:
        errors.append(f"{label}: exactly one state must be INITIAL")
    finals = {s.get("name") for s in states if s.get("type") == "FINAL"}
    seen = set()
    for t in transitions:
        tname = t.get("name")
        if not STATE_NAME.fullmatch(str(tname or "")) or not t.get("label"):
            errors.append(f"{label}: transition {tname!r} needs a valid name and a label")
        elif _edge_spaces(t["label"]):
            errors.append(f"{label}: transition {tname} label must have no leading or trailing spaces")
        if tname in seen:
            errors.append(f"{label}: transition {tname} repeats")
        seen.add(tname)
        if t.get("from") not in names or t.get("to") not in names:
            errors.append(f"{label}: transition {tname} joins states that do not exist")
        if t.get("from") in finals:
            errors.append(f"{label}: transition {tname} leaves the final state {t.get('from')}")
        if not t.get("roles"):
            errors.append(f"{label}: transition {tname} has no roles (SGSPE never leaves them empty)")
        for role in t.get("roles") or []:
            if not isinstance(role, str) or not ROLE_NAME.fullmatch(role):
                errors.append(f"{label}: transition {tname} role {role!r} must match [A-Z0-9_]+")
    return errors


def _workflow_body(workflow):
    """What PUT /api/objects/{object}/workflow takes, and what GET answers once normalized: name, label, enabled and the
    definition without positions."""
    definition = workflow.get("definition") or {}
    return {
        "name": workflow["name"],
        "label": workflow["label"],
        "enabled": workflow.get("enabled", True),
        "definition": {
            "states": [{"name": s["name"], "label": s["label"], "type": s.get("type") or "INTERMEDIATE"}
                       for s in definition.get("states", [])],
            "transitions": [{"name": t["name"], "label": t["label"], "from": t["from"], "to": t["to"],
                             "roles": list(t.get("roles", []))} for t in definition.get("transitions", [])],
        },
    }


def load_workflows(model_dir, model):
    """(object, workflow) for each model/workflows/<object>.json, and the errors of all of them."""
    loaded, errors = [], []
    folder = os.path.join(model_dir, "workflows")
    for name in sorted(os.listdir(folder)) if os.path.isdir(folder) else []:
        if not name.endswith(".json"):
            continue
        object_name = name[:-len(".json")]
        try:
            with open(os.path.join(folder, name), encoding="utf-8") as f:
                workflow = json.load(f)
        except ValueError as e:
            errors.append(f"model/workflows/{name}: not valid JSON: {e}")
            continue
        found = validate_workflow(model, object_name, workflow)
        errors.extend(f"model/workflows/{name}: {e}" for e in found)
        if not found:
            loaded.append((object_name, workflow))
    return loaded, errors


# wasichai-documents 0.6.0 (DocumentTypeService): a type's name, its printed sigla and what a template may name
DOCUMENT_TYPE_NAME = re.compile(r"^[a-z][a-z0-9_-]{0,48}$")
DOCUMENT_PREFIX = re.compile(r"^[A-Z][A-Z0-9]{0,11}$")
PLATFORM_VALUES = ("today", "now", "user", "id", "documentName", "documentPrefix", "documentSerial", "documentNumber")
MAX_TEMPLATE_NODES = 5000
# the keys wasichai-documents keeps in a template node (TemplateNode), and those of an automation's parts: others are
# dropped by the server, so a file that carried them would differ from its own answer on every run
NODE_KEYS = frozenset({"type", "attrs", "content", "marks", "text"})
TRIGGER_KEYS = frozenset({"type", "transition", "state"})
CONDITION_KEYS = frozenset({"field", "operator", "value"})
ACTION_KEYS = frozenset({"type", "field", "value", "targetObject", "values", "url", "documentType"})

# wasichai-automation 0.6.0 (AutomationService): a name is unique in the organization, whatever its object
AUTOMATION_NAME = re.compile(r"^[a-z][a-z0-9_-]{0,48}$")
TRIGGER_TYPES = ("RECORD_CREATED", "RECORD_UPDATED", "RECORD_DELETED", "TRANSITION_APPLIED", "STATE_ENTERED")
OPERATORS = ("EQUALS", "NOT_EQUALS", "GREATER_THAN", "LESS_THAN", "CONTAINS", "IS_EMPTY", "IS_NOT_EMPTY", "CHANGED")
VALUELESS = ("IS_EMPTY", "IS_NOT_EMPTY", "CHANGED")
ACTION_TYPES = ("UPDATE_FIELD", "CREATE_RECORD", "GENERATE_DOCUMENT")
# workflows.md, «Automatizaciones»: no WEBHOOK in the MVP (GAP-020), and sgspe.notification decides who is told, by
# project assignment (ASM-070): a NOTIFY to a role would reach every holder of the role in the tenant
REFUSED_ACTIONS = {
    "WEBHOOK": "no WEBHOOK in the MVP (GAP-020)",
    "NOTIFY": "sgspe.notification tells people by project assignment (ASM-070), never NOTIFY",
}


# wasichai-automation 0.6.0 compares a condition's value as text, or as numbers when both sides are numbers
# (AutomationRules.same): a value the field can never hold makes every run skip, without an error
COMPARED = ("EQUALS", "NOT_EQUALS")
ORDERED = ("GREATER_THAN", "LESS_THAN")
NUMBER = re.compile(r"-?\d+(\.\d+)?")


def _condition_value_error(object_name, condition, fields, model, workflow):
    """Why the value of a condition with a value can never match its field, or None: an ENUM option that the catalog
    lacks, a BOOLEAN that is not true or false, a number that is not a number, a state the workflow lacks."""
    operator, value, name = condition["operator"], condition["value"], condition.get("field")
    if name == "state":
        if operator in COMPARED and value not in _state_names(workflow):
            return f"state {operator} names {value!r}, which is not a state of {object_name}"
        return None
    field = fields.get(name) if isinstance(name, str) else None
    if field is None:
        return None  # already reported: the condition names a field the object lacks
    kind = field.get("type")
    if kind == "ENUM" and operator in COMPARED and value not in model.get("enums", {}).get(field.get("enum"), []):
        return f"{name} {operator} {value!r} is not an option of {field.get('enum')}"
    if kind == "BOOLEAN" and operator in COMPARED and value not in ("true", "false"):
        return f"{name} {operator} takes true or false, not {value!r}"
    if kind in ("INTEGER", "DECIMAL") and operator in COMPARED + ORDERED and not NUMBER.fullmatch(value):
        return f"{name} {operator} takes a number, not {value!r}"
    return None


def _record_fields(model, object_name):
    """{name: field} of what a record of the object holds: its own fields and the field of each MANY_TO_ONE relationship
    it is the source of (type RELATION)."""
    obj = next(o for o in model["objects"] if o["name"] == object_name)
    fields = {f["name"]: f for f in obj["fields"]}
    for rel in model["relationships"]:
        if rel["source"] == object_name and rel.get("fieldName"):
            fields[rel["fieldName"]] = {"name": rel["fieldName"], "type": "RELATION", "required": rel.get("required", False)}
    return fields


def _list_of_objects(label, items):
    if not isinstance(items, list):
        return [f"{label}: the file must be a JSON list"]
    if not all(isinstance(item, dict) for item in items):
        return [f"{label}: every entry must be a JSON object"]
    return []


def _template_errors(label, root, fields, relationships):
    """What wasichai-documents would refuse in a template: a root that is not a doc, a node that is not an object, an
    objectField, relatedTable or platformValue naming something that does not exist, and more than 5000 nodes."""
    if not isinstance(root, dict) or root.get("type") != "doc":
        return [f"{label}: the template root must be a node of type doc"]
    errors, seen = [], 0
    stack = [root]
    while stack:
        node = stack.pop()
        seen += 1
        if not isinstance(node, dict) or not isinstance(node.get("type"), str):
            errors.append(f"{label}: every template node must be an object with a type")
            continue
        extra = sorted(set(node) - NODE_KEYS)
        if extra:
            errors.append(f"{label}: a template node has unknown keys {', '.join(extra)} (wasichai drops them)")
        attrs = node.get("attrs") or {}
        if not isinstance(attrs, dict):
            errors.append(f"{label}: the attrs of a {node['type']} node must be an object")
            attrs = {}
        if node["type"] == "objectField" and not (isinstance(attrs.get("field"), str) and attrs["field"] in fields):
            errors.append(f"{label}: objectField names {attrs.get('field')!r}, which is not a field of the object")
        if node["type"] == "relatedTable" and not (isinstance(attrs.get("relationship"), str)
                                                   and attrs["relationship"] in relationships):
            errors.append(f"{label}: relatedTable names {attrs.get('relationship')!r}, which is not a relationship of the object")
        if node["type"] == "platformValue" and not (isinstance(attrs.get("value"), str)
                                                    and attrs["value"] in PLATFORM_VALUES):
            errors.append(f"{label}: platformValue {attrs.get('value')!r} must be one of {', '.join(PLATFORM_VALUES)}")
        content = node.get("content") or []
        if not isinstance(content, list):
            errors.append(f"{label}: the content of a {node['type']} node must be a list")
            continue
        stack.extend(content)
    if seen > MAX_TEMPLATE_NODES:
        errors.append(f"{label}: the template holds {seen} nodes, more than {MAX_TEMPLATE_NODES}")
    return errors


def validate_document_types(model, object_name, types):
    """The errors of model/document-types/<object>.json against model.json and wasichai-documents's rules."""
    label = f"document types of {object_name}"
    if object_name not in {o["name"] for o in model["objects"]}:
        return [f"{label}: object '{object_name}' is not in model.json"]
    errors = _list_of_objects(label, types)
    if errors:
        return errors
    fields = _record_fields(model, object_name)
    relationships = {r["name"] for r in model["relationships"] if object_name in (r["source"], r["target"])}
    names = set()
    for doc in types:
        name = doc.get("name")
        where = f"document type {object_name}.{name}"
        if not isinstance(name, str) or not DOCUMENT_TYPE_NAME.fullmatch(name):
            errors.append(f"{where}: name must match {DOCUMENT_TYPE_NAME.pattern}")
        if isinstance(name, str) and name in names:
            errors.append(f"{where}: the name repeats")
        if isinstance(name, str):
            names.add(name)
        if not isinstance(doc.get("label"), str) or not doc["label"] or _edge_spaces(doc["label"]):
            errors.append(f"{where}: label is missing or has leading or trailing spaces")
        if not isinstance(doc.get("prefix"), str) or not DOCUMENT_PREFIX.fullmatch(doc["prefix"]):
            errors.append(f"{where}: prefix must match {DOCUMENT_PREFIX.pattern}")
        errors.extend(_template_errors(where, doc.get("template"), fields, relationships))
    return errors


def _state_names(workflow):
    return {s["name"] for s in (workflow or {}).get("definition", {}).get("states", [])
            if isinstance(s, dict) and isinstance(s.get("name"), str)}


def _transition_names(workflow):
    return {t["name"] for t in (workflow or {}).get("definition", {}).get("transitions", [])
            if isinstance(t, dict) and isinstance(t.get("name"), str)}


def _action_errors(where, action, model, object_name, fields, document_types):
    kind = action.get("type")
    if not isinstance(kind, str):
        return [f"{where}: action type must be one of {', '.join(ACTION_TYPES)}"]
    if kind in REFUSED_ACTIONS:
        return [f"{where}: {kind} is not used: {REFUSED_ACTIONS[kind]}"]
    if kind not in ACTION_TYPES:
        return [f"{where}: action type must be one of {', '.join(ACTION_TYPES)}"]
    extra = sorted(set(action) - ACTION_KEYS)
    if extra:
        return [f"{where}: {kind} has unknown keys {', '.join(extra)} (wasichai drops them)"]
    if kind == "GENERATE_DOCUMENT":
        if not isinstance(action.get("documentType"), str) or action["documentType"] not in document_types:
            return [f"{where}: documentType {action.get('documentType')!r} is not a document type of {object_name}"]
        return []
    if kind == "UPDATE_FIELD":
        field = fields.get(action.get("field")) if isinstance(action.get("field"), str) else None
        if field is None:
            return [f"{where}: UPDATE_FIELD names {action.get('field')!r}, which is not a field of {object_name}"]
        if field.get("derived"):
            return [f"{where}: UPDATE_FIELD writes the derived field {field['name']} (DerivedFieldsGuard refuses it)"]
        if not isinstance(action.get("value"), str):
            return [f"{where}: UPDATE_FIELD needs a text value"]
        return []
    target = action.get("targetObject")
    if not isinstance(target, str) or target not in {o["name"] for o in model["objects"]}:
        return [f"{where}: CREATE_RECORD targets {target!r}, which is not in model.json"]
    values = action.get("values")
    if not isinstance(values, dict) or not all(isinstance(v, str) for v in values.values()):
        return [f"{where}: CREATE_RECORD values must be an object of texts"]
    target_fields = _record_fields(model, target)
    errors = []
    for name in values:
        if name not in target_fields:
            errors.append(f"{where}: CREATE_RECORD sets {target}.{name}, which does not exist")
        elif target_fields[name].get("derived"):
            errors.append(f"{where}: CREATE_RECORD sets the derived field {target}.{name} (DerivedFieldsGuard refuses it)")
    for name, field in target_fields.items():
        if field.get("required") and "defaultValue" not in field and name not in values:
            errors.append(f"{where}: CREATE_RECORD leaves out {target}.{name}, which is required")
    return errors


def validate_automations(model, object_name, automations, workflow, document_types):
    """The errors of model/automations/<object>.json against model.json, the object's workflow and document types, and
    wasichai-automation's rules."""
    label = f"automations of {object_name}"
    if object_name not in {o["name"] for o in model["objects"]}:
        return [f"{label}: object '{object_name}' is not in model.json"]
    errors = _list_of_objects(label, automations)
    if errors:
        return errors
    fields = _record_fields(model, object_name)
    for automation in automations:
        name = automation.get("name")
        where = f"automation {name}"
        if not isinstance(name, str) or not AUTOMATION_NAME.fullmatch(name):
            errors.append(f"{where}: name must match {AUTOMATION_NAME.pattern}")
        if not isinstance(automation.get("label"), str) or not automation["label"] or _edge_spaces(automation["label"]):
            errors.append(f"{where}: label is missing or has leading or trailing spaces")
        if automation.get("enabled") is not True:
            errors.append(f"{where}: enabled must be true (an automation that waits is left out of model/)")
        definition = automation.get("definition")
        if not isinstance(definition, dict) or not isinstance(definition.get("trigger"), dict):
            errors.append(f"{where}: definition must be an object with a trigger")
            continue
        trigger = definition["trigger"]
        if set(trigger) - TRIGGER_KEYS:
            errors.append(f"{where}: the trigger has unknown keys {', '.join(sorted(set(trigger) - TRIGGER_KEYS))}")
        if trigger.get("type") not in TRIGGER_TYPES:
            errors.append(f"{where}: trigger type must be one of {', '.join(TRIGGER_TYPES)}")
        elif trigger["type"] == "STATE_ENTERED" and not (isinstance(trigger.get("state"), str)
                                                          and trigger["state"] in _state_names(workflow)):
            errors.append(f"{where}: STATE_ENTERED names {trigger.get('state')!r}, which is not a state of {object_name}")
        elif trigger["type"] == "TRANSITION_APPLIED" and trigger.get("transition") is not None \
                and not (isinstance(trigger["transition"], str) and trigger["transition"] in _transition_names(workflow)):
            errors.append(f"{where}: TRANSITION_APPLIED names {trigger['transition']!r}, which is not a transition of "
                          f"{object_name}")
        conditions = definition.get("conditions", [])
        actions = definition.get("actions")
        if not isinstance(conditions, list) or not all(isinstance(c, dict) for c in conditions):
            errors.append(f"{where}: conditions must be a list of objects")
            conditions = []
        for condition in conditions:
            if set(condition) - CONDITION_KEYS:
                errors.append(f"{where}: a condition has unknown keys {', '.join(sorted(set(condition) - CONDITION_KEYS))}")
            cfield = condition.get("field")
            if cfield != "state" and not (isinstance(cfield, str) and cfield in fields):
                errors.append(f"{where}: a condition names {condition.get('field')!r}, which is not a field of {object_name}")
            if not isinstance(condition.get("operator"), str) or condition["operator"] not in OPERATORS:
                errors.append(f"{where}: a condition operator must be one of {', '.join(OPERATORS)}")
            elif condition["operator"] not in VALUELESS and not (isinstance(condition.get("value"), str)
                                                                and condition["value"].strip()):
                errors.append(f"{where}: a {condition['operator']} condition needs a value")
            elif condition["operator"] not in VALUELESS:
                problem = _condition_value_error(object_name, condition, fields, model, workflow)
                if problem:
                    errors.append(f"{where}: {problem}")
        if not isinstance(actions, list) or not actions or not all(isinstance(a, dict) for a in actions):
            errors.append(f"{where}: actions must be a non-empty list of objects")
            continue
        for action in actions:
            errors.extend(_action_errors(where, action, model, object_name, fields, document_types))
    return errors


def _load_json(model_dir, path, errors):
    try:
        with open(os.path.join(model_dir, path), encoding="utf-8") as f:
            return json.load(f)
    except ValueError as e:
        errors.append(f"model/{path}: not valid JSON: {e}")
        return None


def load_definitions(model_dir, model):
    """Everything --workflows applies, checked: (workflows, document types, automations, errors), where the document
    types and the automations are (object, definition) pairs in file order. Names and prefixes that wasichai keeps
    unique in the organization are checked across files."""
    workflows, errors = load_workflows(model_dir, model)
    by_object = dict(workflows)
    types, automations = [], []
    for path in definition_files(model_dir):
        kind, name = path.split("/")
        if kind not in ("document-types", "automations"):
            continue
        object_name = name[:-len(".json")]
        loaded = _load_json(model_dir, path, errors)
        if loaded is None:
            continue
        if kind == "document-types":
            found = validate_document_types(model, object_name, loaded)
            errors.extend(f"model/{path}: {e}" for e in found)
            if not found:
                types.extend((object_name, doc) for doc in loaded)
    names_by_object = {}
    for object_name, doc in types:
        names_by_object.setdefault(object_name, set()).add(doc["name"])
    for path in definition_files(model_dir):
        kind, name = path.split("/")
        if kind != "automations":
            continue
        object_name = name[:-len(".json")]
        loaded = _load_json(model_dir, path, errors)
        if loaded is None:
            continue
        found = validate_automations(model, object_name, loaded, by_object.get(object_name),
                                     names_by_object.get(object_name, set()))
        errors.extend(f"model/{path}: {e}" for e in found)
        if not found:
            automations.extend((object_name, automation) for automation in loaded)
    prefixes = [doc["prefix"] for _, doc in types]
    errors.extend(f"model/document-types: prefix {p} is used by two types (wasichai keeps one per organization)"
                  for p in sorted({p for p in prefixes if prefixes.count(p) > 1}))
    names = [a["name"] for _, a in automations]
    errors.extend(f"model/automations: automation {n} is declared twice (wasichai keeps one per organization)"
                  for n in sorted({n for n in names if names.count(n) > 1}))
    return workflows, types, automations, errors


def _compact(value):
    """A definition as wasichai answers it, comparable with the file: no null keys and no empty objects (the keys an
    answer adds that the file leaves out). An empty list counts as missing too: wasichai defaults conditions, content and
    the like to []."""
    if isinstance(value, dict):
        return {k: _compact(v) for k, v in value.items() if v is not None and v != {} and v != []}
    if isinstance(value, list):
        return [_compact(v) for v in value]
    return value


def _document_type_body(doc):
    return {"label": doc["label"], "prefix": doc["prefix"], "template": doc["template"]}


def _automation_body(automation):
    definition = automation["definition"]
    return {"label": automation["label"], "enabled": automation["enabled"],
            "definition": {**definition, "conditions": definition.get("conditions", [])}}


def _sync_named(client, kind, object_name, name, wanted, collection):
    """Create (POST) the named definition Core lacks, replace (PUT) the one that differs, skip the equal one. Returns
    'created', 'updated', 'skipped', or None when Core refused (already reported)."""
    path = f"{collection}/{name}"
    method, target = "GET", path
    try:
        current = client.get(path)
        if current is not None and _compact({k: current.get(k) for k in wanted}) == _compact(wanted):
            print(f"skip   {kind} {object_name}.{name} (up to date)")
            return "skipped"
        if current is None:
            method, target = "POST", collection
            client.post(collection, {"name": name, **wanted})
            print(f"create {kind} {object_name}.{name}")
            return "created"
        method = "PUT"
        client.put(path, {"name": name, **wanted})
        print(f"update {kind} {object_name}.{name}")
        return "updated"
    except CoreError as e:
        _fatal(method, target, e)
        return None


def _warn_extra_definitions(client, kind, collection, entries, object_names):
    """A document type or automation Core has and model/ no longer declares keeps working: reported on stderr, never
    deleted. `kind` is the word for the message ('document type', 'automation'); `collection` the REST segment."""
    declared = {d["name"] for _, d in entries}
    for object_name in object_names:
        for current in client.get(f"/api/objects/{object_name}/{collection}") or []:
            if current.get("name") not in declared:
                print(f"warning: {kind} {current.get('name')} of {object_name} is in core but not in model/; left as is "
                      f"(delete it in /admin if it is a leftover)", file=sys.stderr)


def _kinds_before_their_phase(model_dir):
    """The definition files of a kind apply.py does not apply yet, reported on stderr; True when there are any."""
    early = [path for path in definition_files(model_dir) if path.split("/")[0] not in APPLIED_KINDS]
    for path in early:
        print(f"error: model/{path}: apply.py --workflows does not apply this kind yet; it arrives with its phase",
              file=sys.stderr)
    return bool(early)


def _record_count(client, object_name):
    """How many records an object has. An answer without that count is an error, not a zero (as in _records_using):
    a workflow is never created over records on a guess."""
    data = client.get(f"/api/objects/{object_name}/records?page=0&size=1")
    total = data.get("totalElements") if isinstance(data, dict) else None
    if not isinstance(total, int):
        raise CoreError("no record count", json.dumps(data))
    return total


def _records_by_state(client, object_name):
    """{state: records} of every record of an object (a record with no state counts under None)."""
    counts = {}
    for record in client.list_all(object_name):
        counts[record.get("state")] = counts.get(record.get("state"), 0) + 1
    return counts


def _plural(count, noun):
    return f"{count} {noun}" if count == 1 else f"{count} {noun}s"


def _workflow_hazards(client, object_name, wanted, current, allow_stateless):
    """wasichai's PUT .../workflow checks no record. A workflow created over records leaves them with a null state (no
    transition leaves it); one that drops a state records sit in strands them. Returns (refusals, warnings)."""
    refusals, warnings = [], []
    if current is None:
        total = _record_count(client, object_name)
        if total:
            text = (f"{object_name} has {_plural(total, 'record')} and wasichai leaves them with a null state when the "
                    f"workflow is created, so no transition is allowed on them")
            if allow_stateless:
                warnings.append(f"warning: {text}")
            else:
                refusals.append(f"{text}; pass --allow-stateless-records to create it anyway")
        return refusals, warnings
    kept = {s["name"] for s in wanted["definition"]["states"]}
    dropped = [s["name"] for s in current.get("definition", {}).get("states", []) if s["name"] not in kept]
    if dropped:
        counts = _records_by_state(client, object_name)
        stranded = [f"{name} ({_plural(counts[name], 'record')})" for name in dropped if counts.get(name)]
        if stranded:
            refusals.append(f"workflow {wanted['name']} drops the states {', '.join(stranded)} of {object_name}: "
                            f"move those records out of them before replacing it")
    return refusals, warnings


def do_workflows(model_dir, model, client_factory, dry_run=False, allow_stateless_records=False):
    """The workflows of model/workflows/, then the document types of model/document-types/ and the automations of
    model/automations/, in that order (an automation names states and document types): a missing one is created, one
    that differs is replaced, an equal one is skipped. A definition of a kind that has not arrived yet is an error, never
    a silent skip. Nothing is written until every workflow is checked against the records: one that would strand records
    (it drops a state they sit in) stops the pass with 1, and so does one created over records unless
    allow_stateless_records."""
    if _kinds_before_their_phase(model_dir):
        return 2
    workflows, types, automations, errors = load_definitions(model_dir, model)
    if errors:
        for error in errors:
            print(f"error: {error}", file=sys.stderr)
        return 2
    if dry_run:
        for object_name, workflow in workflows:
            print(f"# PUT /api/objects/{object_name}/workflow")
            print(json.dumps(_workflow_body(workflow), indent=2, ensure_ascii=False))
        for object_name, doc in types:
            print(f"# POST /api/objects/{object_name}/document-types")
            print(json.dumps({"name": doc["name"], **_document_type_body(doc)}, indent=2, ensure_ascii=False))
        for object_name, automation in automations:
            print(f"# POST /api/objects/{object_name}/automations")
            print(json.dumps({"name": automation["name"], **_automation_body(automation)}, indent=2, ensure_ascii=False))
        return 0
    client = client_factory() if workflows or types or automations else None
    plan = []
    refusals = []
    for object_name, workflow in workflows:
        path = f"/api/objects/{object_name}/workflow"
        wanted = _workflow_body(workflow)
        try:
            current = client.get(path)
            if current is not None and _workflow_body(current) == wanted:
                plan.append((path, wanted, current, True))
                continue
            found, warnings = _workflow_hazards(client, object_name, wanted, current, allow_stateless_records)
        except CoreError as e:
            _fatal("GET", path, e)
            return 1
        for warning in warnings:
            print(warning, file=sys.stderr)
        refusals.extend(found)
        plan.append((path, wanted, current, False))
    if refusals:
        for refusal in refusals:
            print(f"error: {refusal}", file=sys.stderr)
        return 1
    created = updated = skipped = 0
    for path, wanted, current, same in plan:
        if same:
            print(f"skip   workflow {wanted['name']} (up to date)")
            skipped += 1
            continue
        try:
            client.put(path, wanted)
        except CoreError as e:
            _fatal("PUT", path, e)
            return 1
        if current is None:
            print(f"create workflow {wanted['name']}")
            created += 1
        else:
            print(f"update workflow {wanted['name']}")
            updated += 1
    counts = {}
    for kind, entries, body, collection in (
            ("document type", types, _document_type_body, "document-types"),
            ("automation", automations, _automation_body, "automations")):
        tally = {"created": 0, "updated": 0, "skipped": 0}
        for object_name, definition in entries:
            outcome = _sync_named(client, kind, object_name, definition["name"], body(definition),
                                  f"/api/objects/{object_name}/{collection}")
            if outcome is None:
                return 1
            tally[outcome] += 1
        counts[kind] = tally
    for kind, collection, entries in (("document type", "document-types", types), ("automation", "automations", automations)):
        if not entries:
            continue
        try:
            _warn_extra_definitions(client, kind, collection, entries, [o["name"] for o in model["objects"]])
        except CoreError as e:
            _fatal("GET", f"/api/objects/{{object}}/{collection}", e)
            return 1

    def summary(count, noun, tally):
        return (f"{count} {noun} ({tally['created']} created, {tally['updated']} updated, {tally['skipped']} skipped)")

    print(f"done: {len(workflows)} workflows ({created} created, {updated} updated, {skipped} skipped), "
          f"{summary(len(types), 'document types', counts['document type'])}, "
          f"{summary(len(automations), 'automations', counts['automation'])}, 0 views, 0 forms, 0 pages")
    return 0


def default_model_path():
    return os.path.join(os.path.dirname(os.path.abspath(__file__)), "model.json")


def _parse_args(argv):
    p = argparse.ArgumentParser(description="Apply SGSPE's model.json to a wasichai Core.")
    p.add_argument("--model", default=default_model_path())
    p.add_argument("--core", default=os.environ.get("WASICHAI_CORE", "http://localhost:8094"))
    p.add_argument("--email", default=os.environ.get("WASICHAI_EMAIL", "admin@wasichai.local"))
    p.add_argument("--password", default=os.environ.get("WASICHAI_PASSWORD", "admin"))
    p.add_argument("--dry-run", action="store_true")
    p.add_argument("--drop", action="store_true")
    p.add_argument("--validate-only", action="store_true")
    p.add_argument("--workflows", action="store_true", help="the second pass, after apply_roles.py")
    p.add_argument("--allow-stateless-records", action="store_true",
                   help="with --workflows: create a workflow on an object that already has records, which wasichai "
                        "leaves with a null state (no transition is allowed on them); without it that stops with 1")
    return p.parse_args(argv)


def main(argv=None):
    argv = sys.argv[1:] if argv is None else argv
    args = _parse_args(argv)

    with open(args.model, encoding="utf-8") as f:
        model = json.load(f)

    errors = validate(model)
    if errors:
        for e in errors:
            print(f"model: {args.model}: {e}")
        return 2
    if args.validate_only:
        model_dir = os.path.dirname(os.path.abspath(args.model))
        if _kinds_before_their_phase(model_dir):
            return 2
        _, _, _, definition_errors = load_definitions(model_dir, model)
        for e in definition_errors:
            print(f"model: {e}")
        return 2 if definition_errors else 0
    if args.workflows:
        def connect():
            client = Client(args.core)
            client.login(args.email, args.password)
            return client
        try:
            return do_workflows(os.path.dirname(os.path.abspath(args.model)), model, connect, args.dry_run,
                                args.allow_stateless_records)
        except CoreError as e:
            _fatal("POST", "/api/auth/login", e)
            return 1

    if args.dry_run:
        if args.drop:
            _print_drop_dry_run(model)
        else:
            _print_dry_run(model)
        return 0

    client = Client(args.core)
    try:
        client.login(args.email, args.password)
    except CoreError as e:
        _fatal("POST", "/api/auth/login", e)
        return 1

    if args.drop:
        return do_drop(client, model)
    return do_apply(client, model)


if __name__ == "__main__":
    sys.exit(main())
