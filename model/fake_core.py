"""A scripted fake wasichai Core for the tests: metadata routes plus in-memory records.

Copied from caja-backend@dc9e391 (model/fake_core.py); roles carry ownRecordsOnly and fieldPermissions.
From F2 it keeps an object's indexes beside its uniqueConstraints and stores one workflow per object, refusing one that
names a role it lacks, as wasichai does. From F3 it keeps document types and automations by object and name, answers
them with the null keys and the ids wasichai adds, and refuses a GENERATE_DOCUMENT naming a type the object lacks.
From F6 it answers wasichai-gis: GET /api/gis/layers lists the geometries it was given and POST
/api/gis/layers/{object}/{geometry} publishes one.
Test helper only.
"""
import json
import re
import threading
import urllib.parse
import uuid
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

OBJECT = re.compile(r"^/api/objects/([a-z0-9_]+)$")
RECORDS = re.compile(r"^/api/objects/([a-z0-9_]+)/records$")
RECORD = re.compile(r"^/api/objects/([a-z0-9_]+)/records/([0-9a-f-]+)$")
GIS_LAYER = re.compile(r"^/api/gis/layers/([a-z0-9_]+)/([a-z0-9_]+)$")
ACTIONS = re.compile(r"^/api/metadata/objects/([a-z0-9_]+)/actions$")
FIELD = re.compile(r"^/api/metadata/objects/([a-z0-9_]+)/fields/([a-z0-9_]+)$")
FIELDS = re.compile(r"^/api/metadata/objects/([a-z0-9_]+)/fields$")
ROLE = re.compile(r"^/api/roles/([A-Z0-9_]+)$")
ROLE_PERMISSIONS = re.compile(r"^/api/roles/([A-Z0-9_]+)/permissions$")
ROLE_FIELD_PERMISSIONS = re.compile(r"^/api/roles/([A-Z0-9_]+)/field-permissions$")
WORKFLOW = re.compile(r"^/api/objects/([a-z0-9_]+)/workflow$")
DOCUMENT_TYPES = re.compile(r"^/api/objects/([a-z0-9_]+)/document-types$")
DOCUMENT_TYPE = re.compile(r"^/api/objects/([a-z0-9_]+)/document-types/([a-z0-9_-]+)$")
AUTOMATIONS = re.compile(r"^/api/objects/([a-z0-9_]+)/automations$")
AUTOMATION = re.compile(r"^/api/objects/([a-z0-9_]+)/automations/([a-z0-9_-]+)$")
# what wasichai-automation answers for every action, whatever its type (AutomationAction)
ACTION_KEYS = {"field": None, "value": None, "targetObject": None, "values": {}, "url": None, "documentType": None}

# the keys of an object GET /api/objects tells besides its name, when they are set
OBJECT_KEYS = ("apiOnly", "appendOnly", "requiresReason", "uniqueConstraints", "indexes")


def _stored_field(field):
    """What GET .../fields tells of a field once Core has it: the keys the scripts compare, a description trimmed."""
    stored = {k: v for k, v in field.items() if k in ("name", "label", "type", "required", "unique", "indexed",
                                                         "description", "enumOptions")}
    if isinstance(stored.get("description"), str):
        stored["description"] = stored["description"].strip()
    return stored


class FakeCoreHandler(BaseHTTPRequestHandler):
    def log_message(self, *args):
        pass  # keep test output quiet

    def _body(self):
        length = int(self.headers.get("Content-Length", 0))
        raw = self.rfile.read(length) if length else b""
        return json.loads(raw) if raw else None

    def _handle(self, method):
        server = self.server
        body = self._body()
        with server.lock:
            server.requests.append((method, self.path, self.headers.get("Authorization"), body))
            status, payload = server.script(method, self.path, body)
        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        self.end_headers()
        if payload is not None:
            self.wfile.write(json.dumps(payload).encode("utf-8"))

    def do_GET(self):
        self._handle("GET")

    def do_POST(self):
        self._handle("POST")

    def do_PUT(self):
        self._handle("PUT")

    def do_DELETE(self):
        self._handle("DELETE")


class FakeCore:
    """existing_objects/existing_relationships mark names that 409 on create; records live in memory."""

    def __init__(self, existing_objects=(), existing_relationships=(), fail_on_post_object=None,
                 fail_put=False, fail_put_status=500, login_response=None, fail_on_record=None, existing_fields=None,
                 fail_on_update=None, fail_on_delete=None, fail_on_role=None, object_flags=None, fail_put_object=None,
                 existing_actions=(), refuse_organizations=False,
                 gis_layers=(), fail_on_publish=None):
        self.existing_objects = set(existing_objects)
        self.existing_fields = existing_fields or {}  # object name -> list of {"name", "enumOptions"?}
        self.existing_relationships = set(existing_relationships)
        self.actions = set(existing_actions)  # (object name, action name) already declared; a repeat is a 409
        self.object_flags = dict(object_flags or {})  # object name -> {"apiOnly"?, "appendOnly"?, "requiresReason"?}, as GET /api/objects tells them
        self.fail_on_post_object = fail_on_post_object  # object name -> triggers 500
        self.fail_put = fail_put  # PUT .../fields/... -> fail_put_status
        self.fail_put_status = fail_put_status
        self.login_response = login_response  # override the default {"token": "t"}
        self.fail_on_record = fail_on_record  # object name -> its record POSTs answer 400
        self.fail_on_update = fail_on_update  # object name -> its record PUTs answer 400
        self.fail_on_delete = fail_on_delete  # object name -> its record DELETEs answer 409
        self.fail_put_object = fail_put_object  # object name -> its PUT /api/objects/{name} answers 409
        self.fail_on_role = fail_on_role  # role name -> its POST /api/roles answers 500
        self.records = {}  # object name -> list of {"id", "attributes"}
        self.roles = {}  # role name -> {"name", "label", "permissions": [{"objectName", "action", "allowed"}]}
        self.workflows = {}  # object name -> the body of its last PUT .../workflow
        self.document_types = {}  # object name -> {type name -> {label, prefix, template}}
        self.automations = {}  # object name -> {automation name -> {label, enabled, definition}}
        self.refuse_organizations = refuse_organizations  # POST /api/organizations answers 403 (sin MANAGE_TENANTS)
        self.organizations = []  # the bodies of POST /api/organizations
        # wasichai-gis: (objeto, geometría) -> publicada; lo que GET /api/gis/layers lista. un dict fija capas ya publicadas
        self.gis_layers = dict(gis_layers) if isinstance(gis_layers, dict) else {tuple(p): False for p in gis_layers}
        self.fail_on_publish = fail_on_publish  # objeto -> su POST /api/gis/layers/... responde 503 (GeoServer apagado)
        self.requests = []
        self.server = ThreadingHTTPServer(("127.0.0.1", 0), FakeCoreHandler)
        self.server.requests = self.requests
        self.server.script = self._script
        self.server.lock = threading.Lock()
        self.port = self.server.server_address[1]
        self.base_url = f"http://127.0.0.1:{self.port}"
        self.thread = threading.Thread(target=self.server.serve_forever, daemon=True)
        self.thread.start()

    def stop(self):
        self.server.shutdown()
        self.server.server_close()

    def add_record(self, object_name, attributes, geometries=None, state=None):
        record = {"id": str(uuid.uuid4()), "attributes": dict(attributes)}
        if state is not None:
            record["state"] = state  # the workflow state, a key of the record itself as wasichai tells it
        if geometries is not None:
            record["geometries"] = dict(geometries)
        self.records.setdefault(object_name, []).append(record)
        return record

    def _records(self, method, path, query, body):
        name = RECORDS.match(path).group(1)
        if method == "POST":
            if self.fail_on_record == name:
                return 400, {"detail": "Invalid option", "errors": [{"field": "uso", "message": "boom-record"}]}
            return 201, self.add_record(name, body["attributes"], body.get("geometries"))
        params = {k: v[0] for k, v in urllib.parse.parse_qs(query).items()}
        page = int(params.pop("page", 0))
        size = int(params.pop("size", 25))
        params.pop("sort", None)
        params.pop("dir", None)
        rows = [r for r in self.records.get(name, []) if all(str(r["attributes"].get(k)) == v for k, v in params.items())]
        total_pages = (len(rows) + size - 1) // size
        return 200, {
            "content": rows[page * size:(page + 1) * size],
            "page": page,
            "size": size,
            "totalElements": len(rows),
            "totalPages": total_pages,
        }

    def _update(self, path, body):
        """A record's PUT: Core replaces every field with what is sent."""
        name, record_id = RECORD.match(path).groups()
        if self.fail_on_update == name:
            return 400, {"detail": "Invalid option", "errors": [{"field": "tipo_via", "message": "boom-update"}]}
        record = next((r for r in self.records.get(name, []) if r["id"] == record_id), None)
        if record is None:
            return 404, {"detail": "not found"}
        record["attributes"] = dict(body["attributes"])
        return 200, record

    def _delete(self, path):
        """A record's DELETE: it leaves the records."""
        name, record_id = RECORD.match(path).groups()
        if self.fail_on_delete == name:
            return 409, {"detail": "In use", "errors": [{"field": "id", "message": "boom-delete"}]}
        records = self.records.get(name, [])
        if not any(r["id"] == record_id for r in records):
            return 404, {"detail": "not found"}
        self.records[name] = [r for r in records if r["id"] != record_id]
        return 204, None

    def _roles(self, method, path, body):
        """Core's role routes, addressed by name: list, create, relabel (and ownRecordsOnly), and replace the
        permissions or the field permissions."""
        if path == "/api/roles" and method == "GET":
            return 200, list(self.roles.values())
        if path == "/api/roles" and method == "POST":
            name = body["name"]
            if name == self.fail_on_role:
                return 500, {"message": "boom-role"}
            if name in self.roles:
                return 409, {"detail": f"Role '{name}' already exists"}
            self.roles[name] = {"name": name, "label": body["label"], "ownRecordsOnly": body.get("ownRecordsOnly", False),
                                "permissions": [], "fieldPermissions": []}
            return 201, self.roles[name]
        match = ROLE_FIELD_PERMISSIONS.match(path) or ROLE_PERMISSIONS.match(path) or ROLE.match(path)
        role = self.roles.get(match.group(1)) if match and method == "PUT" else None
        if role is None:
            return 404, {"detail": "not found"}
        if ROLE_FIELD_PERMISSIONS.match(path):
            # like wasichai 0.3.2: the entries of each object the body names are replaced, the others stay; [] clears nothing
            named = {f["objectName"] for f in body["fields"]}
            role["fieldPermissions"] = [f for f in role["fieldPermissions"] if f["objectName"] not in named] + [
                {**f, "read": f.get("read", True), "write": f.get("write", True)} for f in body["fields"]]
        elif ROLE_PERMISSIONS.match(path):
            role["permissions"] = [{**p, "allowed": p.get("allowed", True)} for p in body["permissions"]]
        else:
            role["label"] = body.get("label", role["label"])
            role["ownRecordsOnly"] = body.get("ownRecordsOnly", role.get("ownRecordsOnly", False))
        return 200, role

    @staticmethod
    def _node(node):
        """A template node as wasichai-documents answers it: every key, null when the file left it out."""
        return {"type": node["type"], "attrs": node.get("attrs"), "marks": node.get("marks"), "text": node.get("text"),
                "content": [FakeCore._node(child) for child in node["content"]] if "content" in node else None}

    def _document_types(self, method, path, body):
        """GET (a list or one, 404 when missing), POST (409 when the name or the prefix exists) and PUT (404 when
        missing) of an object's document types, answered as wasichai-documents answers them."""
        match = DOCUMENT_TYPES.match(path) or DOCUMENT_TYPE.match(path)
        name = match.group(1)
        if name not in self.existing_objects:
            return 404, {"detail": "not found"}
        types = self.document_types.get(name, {}) if method == "GET" else self.document_types.setdefault(name, {})

        def answer(type_name):
            stored = types[type_name]
            return {"id": f"dt-{type_name}", "name": type_name, "label": stored["label"], "prefix": stored["prefix"],
                    "objectName": name, "template": self._node(stored["template"])}

        if DOCUMENT_TYPES.match(path):
            if method == "GET":
                return 200, [answer(t) for t in types]
            if body["name"] in types:
                return 409, {"detail": f"Document type '{body['name']}' already exists"}
            if any(t["prefix"] == body["prefix"] for per in self.document_types.values() for t in per.values()):
                return 409, {"detail": f"Prefix '{body['prefix']}' already belongs to another type"}
            types[body["name"]] = {k: body[k] for k in ("label", "prefix", "template")}
            return 201, answer(body["name"])
        type_name = match.group(2)
        if type_name not in types:
            return 404, {"detail": f"Document type '{type_name}' does not exist"}
        if method == "PUT":
            types[type_name] = {k: body[k] for k in ("label", "prefix", "template")}
        return 200, answer(type_name)

    def _automations(self, method, path, body):
        """GET (a list or one, 404 when missing), POST (409 when the name exists in the organization) and PUT (404 when
        missing) of an object's automations, answered as wasichai-automation answers them. A GENERATE_DOCUMENT naming a
        document type the object lacks is a 400, as wasichai checks it when the rule is saved."""
        match = AUTOMATIONS.match(path) or AUTOMATION.match(path)
        name = match.group(1)
        if name not in self.existing_objects:
            return 404, {"detail": "not found"}
        rules = self.automations.get(name, {}) if method == "GET" else self.automations.setdefault(name, {})

        def answer(rule_name):
            stored = rules[rule_name]
            definition = stored["definition"]
            return {"id": f"au-{rule_name}", "objectName": name, "name": rule_name, "label": stored["label"],
                    "enabled": stored["enabled"],
                    "definition": {"trigger": {"type": definition["trigger"]["type"],
                                               "transition": definition["trigger"].get("transition"),
                                               "state": definition["trigger"].get("state")},
                                   "conditions": [{"value": None, **c} for c in definition.get("conditions", [])],
                                   "actions": [{**ACTION_KEYS, **a} for a in definition["actions"]]}}

        def refused(definition):
            for action in definition["actions"]:
                if action["type"] == "GENERATE_DOCUMENT" and action.get("documentType") not in self.document_types.get(name, {}):
                    return 400, {"detail": f"Unknown document type '{action.get('documentType')}'"}
            return None

        if AUTOMATIONS.match(path):
            if method == "GET":
                return 200, [answer(r) for r in rules]
            if any(body["name"] in per for per in self.automations.values()):
                return 409, {"detail": f"Automation '{body['name']}' already exists"}
            refusal = refused(body["definition"])
            if refusal:
                return refusal
            rules[body["name"]] = {k: body[k] for k in ("label", "enabled", "definition")}
            return 201, answer(body["name"])
        rule_name = match.group(2)
        if rule_name not in rules:
            return 404, {"detail": f"Automation '{rule_name}' does not exist"}
        if method == "PUT":
            refusal = refused(body["definition"])
            if refusal:
                return refusal
            rules[rule_name] = {k: body[k] for k in ("label", "enabled", "definition")}
        return 200, answer(rule_name)

    def _gis(self, method, path):
        """GET /api/gis/layers y POST /api/gis/layers/{objeto}/{geometría} como wasichai-gis: la capa se llama
        <tabla física>__<columna>; publicar una ya publicada es idempotente; una geometría que el tenant no tiene es 400."""
        def status(pair, published):
            name, geometry = pair
            return {"objectName": name, "geometryName": geometry, "label": name, "layerName": f"{name}__0a1b2c3d__{geometry}",
                    "geometryType": "GEOMETRY", "srid": 4326, "published": published, "wms": "", "wfs": ""}

        if path == "/api/gis/layers" and method == "GET":
            return 200, [status(pair, published) for pair, published in self.gis_layers.items()]
        match = GIS_LAYER.match(path)
        if match and method == "POST":
            pair = match.groups()
            if pair not in self.gis_layers:
                return 400, {"detail": f"Unknown geometry '{pair[1]}'"}
            if self.fail_on_publish == pair[0]:
                return 503, {"detail": "GeoServer integration is disabled"}
            self.gis_layers[pair] = True
            return 200, status(pair, True)
        return 404, {"detail": "not found"}

    def _script(self, method, full_path, body):
        path, _, query = full_path.partition("?")
        if path.startswith("/api/gis/"):
            return self._gis(method, path)
        if path == "/api/roles" or path.startswith("/api/roles/"):
            return self._roles(method, path, body)
        if path == "/api/auth/login" and method == "POST":
            if self.login_response is not None:
                return 200, self.login_response
            return 200, {"token": "t"}
        if path == "/api/organizations" and method == "POST":
            if self.refuse_organizations:
                return 403, {"detail": "Missing permission MANAGE_TENANTS"}
            self.organizations.append(body)
            return 201, {"id": str(uuid.uuid4()), "slug": body.get("slug"), "name": body.get("name")}
        if RECORDS.match(path) and method in ("GET", "POST"):
            return self._records(method, path, query, body)
        if RECORD.match(path) and method == "PUT":
            return self._update(path, body)
        if RECORD.match(path) and method == "DELETE":
            return self._delete(path)
        if path == "/api/objects" and method == "GET":
            return 200, [{"name": n, **self.object_flags.get(n, {})} for n in self.existing_objects]
        if path == "/api/objects" and method == "POST":
            name = body["name"]
            if self.fail_on_post_object and name == self.fail_on_post_object:
                return 500, {"message": "boom"}
            if name in self.existing_objects:
                return 409, {"message": "exists"}
            # what GET /api/objects tells of it from now on: its write rules and, when it has any, its constraints
            self.existing_objects.add(name)
            self.object_flags[name] = {k: body[k] for k in OBJECT_KEYS if body.get(k)}
            # and its fields, as GET .../fields tells them from now on (a description is trimmed, like wasichai)
            self.existing_fields.setdefault(name, [_stored_field(f) for f in body.get("fields", [])])
            return 201, {"name": name}
        if OBJECT.match(path) and method == "PUT":
            name = OBJECT.match(path).group(1)
            if name not in self.existing_objects:
                return 404, {"detail": "not found"}
            if name == self.fail_put_object:
                return 409, {"detail": "repeats"}
            # Core replaces the object: the flags it was not sent keep their value, as its null does. an empty
            # uniqueConstraints and indexes are keys GET leaves out
            flags = {**self.object_flags.get(name, {}), **{k: v for k, v in body.items() if k in OBJECT_KEYS}}
            self.object_flags[name] = {k: v for k, v in flags.items() if v != [] or k not in ("uniqueConstraints", "indexes")}
            return 200, {"name": name, **self.object_flags[name]}
        if DOCUMENT_TYPES.match(path) or DOCUMENT_TYPE.match(path):
            return self._document_types(method, path, body)
        if AUTOMATIONS.match(path) or AUTOMATION.match(path):
            return self._automations(method, path, body)
        workflow = WORKFLOW.match(path)
        if workflow and method == "GET":
            stored = self.workflows.get(workflow.group(1))
            return (200, stored) if stored is not None else (404, {"detail": "no workflow"})
        if workflow and method == "PUT":
            if workflow.group(1) not in self.existing_objects:
                return 404, {"detail": "not found"}
            unknown = [r for t in body["definition"]["transitions"] for r in t["roles"] if r not in self.roles]
            if unknown:
                return 400, {"detail": f"Unknown role '{unknown[0]}'"}
            self.workflows[workflow.group(1)] = body
            return 200, body
        if path == "/api/relationships" and method == "POST":
            name = body["name"]
            if name in self.existing_relationships:
                return 409, {"message": "exists"}
            self.existing_relationships.add(name)
            if body.get("fieldName"):
                # a MANY_TO_ONE puts a RELATION field on its source; a MANY_TO_MANY puts none
                self.existing_fields.setdefault(body["source"], []).append(
                    _stored_field({"name": body["fieldName"], "label": body["label"], "type": "RELATION"}))
            return 201, {"name": name}
        actions = ACTIONS.match(path)
        if actions and method == "POST":
            if actions.group(1) not in self.existing_objects:
                return 404, {"detail": "not found"}
            key = (actions.group(1), body["name"])
            if key in self.actions:
                return 409, {"detail": "repeats"}
            self.actions.add(key)
            return 201, {"name": body["name"], "label": body.get("label")}
        fields = FIELDS.match(path)
        if fields and method == "GET":
            return 200, self.existing_fields.get(fields.group(1), [])
        if fields and method == "POST":
            self.existing_fields.setdefault(fields.group(1), []).append(_stored_field(body))
            return 201, {"name": body["name"]}
        if path.startswith("/api/metadata/objects/") and method == "PUT":
            if self.fail_put:
                return self.fail_put_status, {"message": "boom-put"}
            field = FIELD.match(path)
            if field:
                stored = next((f for f in self.existing_fields.get(field.group(1), []) if f["name"] == field.group(2)), None)
                if stored is not None:
                    stored.update(_stored_field({**stored, **body}))
            return 200, {"required": True}
        if path.startswith("/api/metadata/objects/") and "/fields/" in path and method == "DELETE":
            return 204, None
        if path.startswith("/api/relationships/") and method == "DELETE":
            return 204, None
        if path.startswith("/api/objects/") and method == "DELETE":
            return 204, None
        return 500, {"message": "boom"}
