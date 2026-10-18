#!/usr/bin/env python3
"""Carga por REST el conjunto ficticio de demostración del T1 (épica #102).

Solo biblioteca estándar, con el patrón de load_indicators.py. Lee los archivos model/demo/NN-*.json en orden de nombre,
que comparten un único espacio de claves, y por cada registro: lo busca por su clave natural (`match`), lo crea si falta,
y después carga sus hijos, vínculos y transiciones y corre sus comandos (evaluaciones, aprobación de áreas, cierre de
periodo, secciones y borrador). Un registro que ya existe no se actualiza: sus hijos, vínculos y transiciones se saltan,
su estado declarado se comprueba y sus comandos corren siempre, porque cada uno es idempotente por su cuenta.

Las geometrías se declaran en km desde el ancla y se pasan a GeoJSON EPSG:4326. Cada escritura lleva X-Change-Reason y
cada comando una Idempotency-Key estable `demo-<clave>-<n>`. La contraseña de los usuarios de la demostración llega solo
por SGSPE_DEMO_PASSWORD o --users-password, nunca por un archivo.

Corre después de apply.py, apply_roles.py, load_ubigeo.py y load_indicators.py, con el administrador del tenant.

Los registros de una colección se cargan en lotes de hasta --workers a la vez (SGSPE_DEMO_WORKERS; 1 por defecto): un
lote se corta antes del registro que nombra a otro de su misma colección, así que cada referencia sigue existiendo antes de
usarse. Los hijos, vínculos, transiciones y comandos de un registro van siempre en orden, en su mismo hilo.

Correr: python3 load_demo.py [--dir D] [--core URL] [--email E] [--password P] [--users-password P] [--draft]
        [--workers N] [--dry-run | --validate-only]
Salida: 0 si va bien, 1 si core rechaza algo o no responde, 2 si los archivos faltan o no son válidos.
"""
import argparse
import glob
import json
import math
import os
import re
import sys
import threading
import urllib.error
import urllib.parse
import urllib.request
from concurrent.futures import ThreadPoolExecutor
from dataclasses import dataclass, field

import demo_check
from core_client import TIMEOUT_SECONDS, Client, CoreError

HERE = os.path.dirname(os.path.abspath(__file__))
REASON = "Carga del conjunto de demostración"
KM_PER_DEG_LAT = 110.574
REFERENCE = re.compile(r"^@([A-Za-z0-9_-]+)$")
MENTION = re.compile(r"\[\[person:@([A-Za-z0-9_-]+)\]\]")
FILE_KEYS = {"anchor", "users", "collections"}
DRAFT_BUSY = ("EN COLA", "EN PROCESO", "LISTO")
COMMANDS = ("assessment", "risk_assessment", "approve_area", "close_period", "sections", "draft")


def derived_fields(object_name, path=os.path.join(HERE, "model.json"), _cache={}):
    """Los campos `derived: true` de un objeto en model/model.json: core los calcula y rechaza que se manden."""
    if path not in _cache:
        with open(path, encoding="utf-8") as f:
            _cache[path] = {o["name"]: {x["name"] for x in o.get("fields", []) if x.get("derived")}
                            for o in json.load(f)["objects"]}
    return _cache[path].get(object_name, set())


def geometry_types(object_name, path=os.path.join(HERE, "model.json"), _cache={}):
    """El geometryType de cada campo GEOMETRY de un objeto en model/model.json (POINT, POLYGON, MULTIPOLYGON…)."""
    if path not in _cache:
        with open(path, encoding="utf-8") as f:
            _cache[path] = {o["name"]: {x["name"]: x.get("geometryType") for x in o.get("fields", []) if x.get("type") == "GEOMETRY"}
                            for o in json.load(f)["objects"]}
    return _cache[path].get(object_name, {})


class DatasetError(Exception):
    pass


class LoadError(Exception):
    pass


@dataclass
class Dataset:
    anchor: dict
    users: list
    collections: list


@dataclass
class Counts:
    created: int = 0
    skipped: int = 0
    commands: int = 0
    warnings: int = 0
    users: int = 0
    files: list = field(default_factory=list)


def geometry(spec, anchor, geometry_type=None):
    """Una geometría declarada en km desde el ancla, en GeoJSON EPSG:4326. Un área sale MultiPolygon, salvo en un campo
    POLYGON del modelo (`geometry_type`), donde sale Polygon."""
    lat0, lon0 = anchor["lat"], anchor["lon"]
    km_per_deg_lon = 111.320 * math.cos(math.radians(lat0))

    def at(x, y):
        return [round(lon0 + x / km_per_deg_lon, 7), round(lat0 + y / KM_PER_DEG_LAT, 7)]

    kind, value = next(iter(spec.items()))
    if kind == "point":
        return {"type": "Point", "coordinates": at(*value)}
    if kind == "line":
        return {"type": "LineString", "coordinates": [at(*p) for p in value]}
    if kind == "square":
        x, y, h = value
        value = [[x - h, y - h], [x + h, y - h], [x + h, y + h], [x - h, y + h]]
    if kind in ("square", "polygon"):
        ring = [at(*p) for p in value]
        if ring[0] != ring[-1]:
            ring.append(ring[0])
        if geometry_type == "POLYGON":
            return {"type": "Polygon", "coordinates": [ring]}
        return {"type": "MultiPolygon", "coordinates": [[ring]]}
    raise DatasetError(f"unknown geometry {kind}")


def batches(records):
    """Parte los registros de una colección en lotes que se pueden cargar a la vez: un lote se corta antes del registro que
    nombra una clave (la de un registro o la de un hijo) de su mismo lote."""
    out, current, keys = [], [], set()
    for record in records:
        refs = set(_references([record.get("attributes"), record.get("links"), record.get("children"),
                                record.get("commands")]))
        if refs & keys:
            out.append(current)
            current, keys = [], set()
        current.append(record)
        keys.add(record["key"])
        keys.update(c["key"] for c in record.get("children", []) if c.get("key"))
    if current:
        out.append(current)
    return out


def _references(value):
    """Las claves que un valor nombra con @clave o [[person:@clave]], en cualquier profundidad."""
    if isinstance(value, str):
        whole = REFERENCE.match(value)
        return [whole.group(1)] if whole else MENTION.findall(value)
    if isinstance(value, list):
        return [r for item in value for r in _references(item)]
    if isinstance(value, dict):
        return [r for item in value.values() for r in _references(item)]
    return []


def read_dataset(folder):
    """El conjunto de los archivos NN-*.json de la carpeta, en orden de nombre y validado: un único ancla, claves únicas,
    cada @referencia a un registro anterior, usuarios declarados y cada reapertura sobre una clave del mismo objeto.
    Lanza DatasetError con todos los problemas."""
    paths = sorted(glob.glob(os.path.join(folder, "[0-9][0-9]-*.json")))
    if not paths:
        raise DatasetError(f"{folder}: no NN-*.json files")
    errors, items = [], []
    for path in paths:
        name = os.path.basename(path)
        try:
            with open(path, encoding="utf-8") as f:
                items.append((name, json.load(f)))
        except (OSError, ValueError) as e:
            errors.append(f"{name}: {e}")
    return _build(items, errors)


def read_dataset_from(data, name="00-base.json"):
    """Como read_dataset, pero sobre un solo conjunto ya leído en memoria (el contenido de un archivo)."""
    return _build([(name, data)], [])


def _build(items, errors):
    anchors, users, collections = [], [], []
    for name, data in items:
        if not isinstance(data, dict):
            errors.append(f"{name}: must be an object")
            continue
        unknown = sorted(set(data) - FILE_KEYS)
        if unknown:
            errors.append(f"{name}: unknown keys {unknown}")
        if "anchor" in data:
            anchors.append(data["anchor"])
        users.extend(data.get("users", []))
        for collection in data.get("collections", []):
            collections.append({**collection, "_file": name})
    if len(anchors) != 1:
        errors.append(f"needs exactly one anchor, found {len(anchors)}")
    user_keys = {"admin"}
    for user in users:
        if user.get("key") in user_keys:
            errors.append(f"user {user.get('key')}: repeated key")
        user_keys.add(user.get("key"))
    defined = {}  # clave -> objeto
    for collection in collections:
        where = f"{collection['_file']} {collection.get('object')}"
        if collection.get("as", "admin") not in user_keys:
            errors.append(f"{where}: unknown user {collection.get('as')}")
        for record in collection.get("records", []):
            key = record.get("key")
            if not key:
                errors.append(f"{where}: record without key")
                continue
            if not collection.get("reopen") and not collection.get("match"):
                errors.append(f"{where}: {key} collection without match (a reload would duplicate it)")
            if collection.get("reopen"):
                if defined.get(key) != collection.get("object"):
                    errors.append(f"{where}: reopen of {key}, not defined before in {collection.get('object')}")
            elif key in defined:
                errors.append(f"{where}: {key} repeated key")
            for ref in _references([record.get("attributes"), record.get("links"), record.get("children"),
                                    record.get("commands")]):
                if ref not in defined:
                    errors.append(f"{where}: {key} references @{ref}, not defined before")
            for source in (record.get("attributes") or {}, *(c.get("attributes") or {} for c in record.get("children", [])),
                           *(c.get("attributes") or {} for c in record.get("commands", []))):
                for attr, value in source.items():
                    if attr.endswith("_user") and isinstance(value, str) and value not in user_keys:
                        errors.append(f"{where}: {key} {attr} names unknown user {value}")
            for command in record.get("commands", []):
                if command.get("command") not in COMMANDS:
                    errors.append(f"{where}: {key} unknown command {command.get('command')}")
            defined.setdefault(key, collection.get("object"))
            # los hijos con `key` también son claves del espacio compartido: únicas y alcanzables con @clave
            for child in record.get("children", []):
                child_key = child.get("key")
                if child_key:
                    if child_key in defined:
                        errors.append(f"{where}: {child_key} repeated key")
                    defined.setdefault(child_key, child.get("object"))
    if errors:
        raise DatasetError("\n".join(errors))
    return Dataset(anchors[0], users, collections)


class ReasonClient(Client):
    """Client con el motivo del cambio en cada escritura y una Idempotency-Key cuando se la pasan. core_client.py no
    manda cabeceras extra, así que esta subclase repite Client._call (core_client.py) con ellas: si aquel cambia, este
    también."""

    def _call(self, method, path, body=None, use_auth=True, headers=None):
        url = self.base_url + path
        sent = {"Content-Type": "application/json"}
        if use_auth and self.token:
            sent["Authorization"] = f"Bearer {self.token}"
        if method in ("POST", "PUT"):
            sent["X-Change-Reason"] = "UTF-8''" + urllib.parse.quote(REASON)
        sent.update(headers or {})
        data = json.dumps(body).encode("utf-8") if body is not None else None
        req = urllib.request.Request(url, data=data, method=method, headers=sent)
        try:
            with urllib.request.urlopen(req, timeout=TIMEOUT_SECONDS) as resp:
                status, raw = resp.status, resp.read()
        except urllib.error.HTTPError as e:
            raw = e.read()
            e.close()
            if method == "GET" and e.code == 404:
                return e.code, None
            raise CoreError(e.code, raw.decode("utf-8", errors="replace") if raw else "")
        except (urllib.error.URLError, ConnectionError, OSError) as e:
            reason = getattr(e, "reason", e)
            if isinstance(e, TimeoutError) or isinstance(reason, TimeoutError):
                raise CoreError(f"timed out after {TIMEOUT_SECONDS} s: {method} {url}", "") from e
            raise CoreError(f"connection failed: {reason} ({method} {url})", "") from e
        text = raw.decode("utf-8") if raw else ""
        try:
            return status, (json.loads(text) if text else None)
        except ValueError as e:
            raise CoreError(f"{status}: not JSON ({method} {url})", text[:200]) from e

    def post(self, path, body, headers=None):
        return self._call("POST", path, body, headers=headers)

    def put(self, path, body, headers=None):
        return self._call("PUT", path, body, headers=headers)


class Loader:
    def __init__(self, client, dataset, users_password, draft=False, out=None, workers=1):
        self.admin = ReasonClient(client.base_url)
        self.admin.token = client.token
        self.dataset = dataset
        self.users_password = users_password
        self.draft = draft
        self.out = out or sys.stdout
        self.counts = Counts()
        self.users = {}  # clave -> {"id", "email", "client"}
        self.ids = {}  # clave de registro -> id
        self.objects = {}  # clave de registro -> objeto
        self.command_count = {}  # clave de registro -> comandos ya numerados
        self.indicators = {}
        self.workers = max(1, workers)
        self.lock = threading.Lock()

    def say(self, line):
        with self.lock:
            print(line, file=self.out)

    def bump(self, name):
        with self.lock:
            setattr(self.counts, name, getattr(self.counts, name) + 1)

    # ---- usuarios ----------------------------------------------------------------------------------------------

    def load_users(self):
        me = self.admin.get("/api/auth/me")
        self.users["admin"] = {"id": me["userId"], "email": me["email"], "client": self.admin}
        for user in self.dataset.users:
            try:
                _, made = self.admin.post("/api/users", {"email": user["email"], "displayName": user["displayName"],
                                                         "password": self.users_password, "roles": user["roles"]})
                user_id = made["id"]
                self.counts.users += 1
                self.say(f"create user {user['key']}")
            except CoreError as e:
                if e.status != 409:
                    raise LoadError(f"user {user['key']}: {e}") from e
                listed = self.admin.get("/api/users")
                listed = listed.get("content", []) if isinstance(listed, dict) else listed
                user_id = next((u["id"] for u in listed if u.get("email") == user["email"]), None)
                if user_id is None:
                    raise LoadError(f"user {user['key']}: {e}") from e
            client = ReasonClient(self.admin.base_url)
            try:
                client.login(user["email"], self.users_password)
            except CoreError as e:
                raise LoadError(f"user {user['key']}: login {e}") from e
            self.users[user["key"]] = {"id": user_id, "email": user["email"], "client": client}

    # ---- valores -----------------------------------------------------------------------------------------------

    def indicator_id(self, code):
        if code not in self.indicators:
            found = [r for r in self.admin.list_all("indicator", code=code) if r["attributes"].get("code") == code]
            if not found:
                raise LoadError(f"indicator {code} not loaded")
            self.indicators[code] = found[0]["id"]
        return self.indicators[code]

    def resolve(self, value):
        """Reemplaza @clave por el id, {"indicator": código} por el del indicador y [[person:@clave]] por su mención."""
        if isinstance(value, str):
            whole = REFERENCE.match(value)
            if whole:
                return self.ref(whole.group(1))
            return MENTION.sub(lambda m: f"[[person:{self.ref(m.group(1))}]]", value)
        if isinstance(value, list):
            return [self.resolve(item) for item in value]
        if isinstance(value, dict):
            if set(value) == {"indicator"}:
                return self.indicator_id(value["indicator"])
            resolved = {}
            for name, item in value.items():
                resolved[name] = self.resolve(item)
                if name.endswith("_user") and isinstance(item, str) and not REFERENCE.match(item):
                    user = self.users.get(item)
                    if user is None:
                        raise LoadError(f"unknown user {item}")
                    resolved[name + "_id"], resolved[name + "_email"] = user["id"], user["email"]
                    del resolved[name]
            return resolved
        return value

    def ref(self, key):
        if key not in self.ids:
            raise LoadError(f"@{key} has no record yet")
        return self.ids[key]

    # ---- registros ---------------------------------------------------------------------------------------------

    def run(self):
        self.load_users()
        with ThreadPoolExecutor(max_workers=self.workers) as pool:
            for collection in self.dataset.collections:
                client = self.users[collection.get("as", "admin")]["client"]

                def one(record, client=client, collection=collection):
                    try:
                        if collection.get("reopen"):
                            self.reopen(client, collection["object"], record)
                        else:
                            self.load_record(client, collection, record)
                    except CoreError as e:
                        raise LoadError(f"{record['key']}: {e}") from e

                for batch in batches(collection["records"]):
                    if self.workers == 1 or len(batch) == 1:
                        for record in batch:
                            one(record)
                        continue
                    # todas terminan antes del lote siguiente; la primera que falla, en el orden del archivo, se informa
                    futures = [pool.submit(one, record) for record in batch]
                    for future in futures:
                        future.result()
        return self.counts

    def create(self, client, object_name, attributes, geometries):
        body = {"attributes": attributes}
        if geometries:
            types = geometry_types(object_name)
            body["geometries"] = {name: geometry(spec, self.dataset.anchor, types.get(name)) for name, spec in geometries.items()}
        _, made = client.post(f"/api/objects/{object_name}/records", body)
        return made["id"]

    def find(self, client, object_name, match, attributes):
        wanted = {m: attributes.get(m) for m in match}
        query = {m: (str(v).lower() if isinstance(v, bool) else v) for m, v in wanted.items() if v is not None}
        for found in client.list_all(object_name, **query):
            if all(found["attributes"].get(m) == v for m, v in wanted.items()):
                return found
        return None

    def load_record(self, client, collection, record):
        key, object_name = record["key"], collection["object"]
        attributes = self.resolve(record.get("attributes", {}))
        existing = self.find(client, object_name, collection.get("match", []), attributes) if collection.get("match") else None
        if existing is not None:
            record_id = existing["id"]
            self.bump("skipped")
        else:
            record_id = self.create(client, object_name, attributes, record.get("geometries"))
            self.bump("created")
            self.say(f"create {object_name} {key}")
        self.ids[key], self.objects[key] = record_id, object_name
        if existing is not None:
            self.recover_child_keys(client, key, record, record_id)
        if existing is None:
            for child in record.get("children", []):
                child_attributes = {**self.resolve(child.get("attributes", {})), child["parent"]: record_id}
                child_id = self.create(client, child["object"], child_attributes, child.get("geometries"))
                self.bump("created")
                self.say(f"create {child['object']} (child of {key})")
                if child.get("key"):
                    self.ids[child["key"]], self.objects[child["key"]] = child_id, child["object"]
            for relationship, others in record.get("links", {}).items():
                for other in others:
                    client.post(f"/api/objects/{object_name}/records/{record_id}/related/{relationship}",
                                {"otherId": self.resolve(other)})
            for transition in record.get("transitions", []):
                client.post(f"/api/objects/{object_name}/records/{record_id}/transitions/{transition}", None)
        expected = record.get("state")
        if expected is not None:
            state = (client.get(f"/api/objects/{object_name}/records/{record_id}") or {}).get("state")
            if state != expected:
                raise LoadError(f"{key}: state {state}, expected {expected}")
        self.run_commands(client, key, object_name, record_id, record.get("commands", []))

    def recover_child_keys(self, client, key, record, record_id):
        """Los hijos de un padre que ya existe no se cargan, pero sus claves se siguen nombrando con @clave: cada hijo con
        `key` se busca entre los de su padre por su nombre o, sin él, por sus atributos de texto."""
        keyed = [child for child in record.get("children", []) if child.get("key")]
        if not keyed:
            return
        for child in keyed:
            attributes = self.resolve(child.get("attributes", {}))
            # el nombre, si lo tiene, basta y no depende de cómo core devuelva fechas o textos largos
            wanted = ({"name": attributes["name"]} if isinstance(attributes.get("name"), str)
                      else {k: v for k, v in attributes.items() if isinstance(v, str)})
            found = [c for c in client.list_all(child["object"], **{child["parent"]: record_id})
                     if all(c["attributes"].get(k) == v for k, v in wanted.items())]
            if not found:
                raise LoadError(f"{key}: child {child['key']} not found under the existing record")
            self.ids[child["key"]], self.objects[child["key"]] = found[0]["id"], child["object"]

    def reopen(self, client, object_name, record):
        key = record["key"]
        if self.objects.get(key) != object_name:
            raise LoadError(f"{key}: reopen of a record not loaded as {object_name}")
        self.run_commands(client, key, object_name, self.ids[key], record.get("commands", []))

    # ---- comandos ----------------------------------------------------------------------------------------------

    def run_commands(self, client, key, object_name, record_id, commands):
        for command in commands:
            number = self.command_count.get(key, 0)
            self.command_count[key] = number + 1
            headers = {"Idempotency-Key": f"demo-{key}-{number}"}
            ran = getattr(self, "cmd_" + command["command"])(client, key, object_name, record_id, command, headers)
            if ran:
                self.bump("commands")
                self.say(f"command {command['command']} {key}")
            else:
                self.bump("skipped")

    def fresh(self, client, object_name, record_id):
        return client.get(f"/api/objects/{object_name}/records/{record_id}") or {"attributes": {}}

    def cmd_assessment(self, client, key, object_name, record_id, command, headers):
        attributes = self.resolve(command["attributes"])
        for found in client.list_all("stakeholder_assessment", stakeholder=record_id):
            if found["attributes"].get("assessment_date") == attributes.get("assessment_date"):
                return False
        client.post(f"/api/sgspe/stakeholders/{record_id}/assessments", {"attributes": attributes}, headers)
        return True

    def cmd_risk_assessment(self, client, key, object_name, record_id, command, headers):
        attributes = self.resolve(command["attributes"])
        for found in client.list_all("risk_assessment", social_risk=record_id):
            stored = found["attributes"]
            if (stored.get("assessment_date") == attributes.get("assessment_date")
                    and bool(stored.get("residual")) == bool(attributes.get("residual"))):
                return False
        client.post(f"/api/sgspe/social-risks/{record_id}/assessments", {"attributes": attributes}, headers)
        return True

    def cmd_approve_area(self, client, key, object_name, record_id, command, headers):
        current = self.fresh(client, object_name, record_id)
        if current.get("state") == "approved":
            return False
        client.post(f"/api/sgspe/areas-of-influence/{record_id}/approve", {},
                    {**headers, "If-Match": f'"{current.get("updatedAt")}"'})
        return True

    def cmd_close_period(self, client, key, object_name, record_id, command, headers):
        period = self.resolve(command.get("attributes", {}))
        start = period.get("period_start_date", command.get("period_start_date"))
        end = period.get("period_end_date", command.get("period_end_date"))
        indicator = self.indicator_id(command["indicator"])
        for found in client.list_all("indicator_measurement", indicator=indicator, project=record_id):
            stored = found["attributes"]
            if stored.get("period_start_date") == start and stored.get("period_end_date") == end:
                return False
        try:
            client.post(f"/api/sgspe/indicators/{indicator}/measurements",
                        {"project": record_id, "attributes": {"period_start_date": start, "period_end_date": end}},
                        headers)
        except CoreError as e:
            if command.get("optional") and isinstance(e.status, int) and 400 <= e.status < 500:
                self.bump("warnings")
                self.say(f"warning: {key} {command['indicator']} not closed: {e}")
                return False
            raise
        return True

    def cmd_sections(self, client, key, object_name, record_id, command, headers):
        sections = {s["attributes"].get("section_key"): s for s in client.list_all("deliverable_section",
                                                                                    deliverable=record_id)}
        wrote = False
        for section_key, text in command["sections"].items():
            section = sections.get(section_key)
            if section is None:
                raise CoreError(0, f"deliverable {key} has no section {section_key}")
            if section["attributes"].get("content"):
                continue
            kept = {k: v for k, v in section["attributes"].items() if k not in derived_fields("deliverable_section")}
            attributes = {**kept, "content": self.resolve(text), "content_origin": "HUMANO"}
            client.put(f"/api/objects/deliverable_section/records/{section['id']}", {"attributes": attributes}, headers)
            wrote = True
        return wrote

    def cmd_draft(self, client, key, object_name, record_id, command, headers):
        if not self.draft:
            return False
        if self.fresh(client, object_name, record_id)["attributes"].get("draft_generation_status") in DRAFT_BUSY:
            return False
        client.post(f"/api/sgspe/deliverables/{record_id}/drafts", {}, headers)
        return True


def _parse_args(argv):
    p = argparse.ArgumentParser(description="Carga el conjunto ficticio de demostración del T1.")
    p.add_argument("--dir", default=os.path.join(HERE, "demo"))
    p.add_argument("--core", default=os.environ.get("WASICHAI_CORE", "http://localhost:8094"))
    p.add_argument("--email", default=os.environ.get("WASICHAI_EMAIL", "admin@wasichai.local"))
    p.add_argument("--password", default=os.environ.get("WASICHAI_PASSWORD", "admin"))
    p.add_argument("--users-password", default=os.environ.get("SGSPE_DEMO_PASSWORD"),
                   help="contraseña de los usuarios de la demostración (o SGSPE_DEMO_PASSWORD)")
    p.add_argument("--draft", action="store_true", help="pide también el borrador de los entregables")
    p.add_argument("--workers", type=int, default=int(os.environ.get("SGSPE_DEMO_WORKERS", "1")),
                   help="registros de una colección que se cargan a la vez (o SGSPE_DEMO_WORKERS; 1 por defecto)")
    mode = p.add_mutually_exclusive_group()
    mode.add_argument("--dry-run", action="store_true", help="cuenta lo que cargaría; no llama a core")
    mode.add_argument("--validate-only", action="store_true", help="solo valida los archivos; no llama a core")
    return p.parse_args(argv)


def _counts_by_object(dataset):
    """Los registros que cargaría, por objeto: los de cada colección (menos las reaperturas) y sus hijos."""
    counts = {}
    for collection in dataset.collections:
        if collection.get("reopen"):
            continue
        for record in collection["records"]:
            counts[collection["object"]] = counts.get(collection["object"], 0) + 1
            for child in record.get("children", []):
                counts[child["object"]] = counts.get(child["object"], 0) + 1
    return counts


def main(argv=None):
    args = _parse_args(sys.argv[1:] if argv is None else argv)
    try:
        dataset = read_dataset(args.dir)
    except DatasetError as e:
        print(f"error: {e}", file=sys.stderr)
        return 2
    records = sum(len(c["records"]) for c in dataset.collections)
    print(f"dataset: {len(dataset.users)} users, {len(dataset.collections)} collections, {records} records")
    if args.validate_only or args.dry_run:
        with open(os.path.join(HERE, "model.json"), encoding="utf-8") as f:
            problems = demo_check.validate(dataset, json.load(f))
        if problems:
            print("\n".join(problems), file=sys.stderr)
            print(f"error: {len(problems)} problems", file=sys.stderr)
            return 2
        if args.dry_run:
            for name, count in sorted(_counts_by_object(dataset).items()):
                print(f"{name}: {count}")
        return 0
    if not args.users_password:
        print("error: needs SGSPE_DEMO_PASSWORD or --users-password", file=sys.stderr)
        return 2
    client = Client(args.core)
    try:
        client.login(args.email, args.password)
        counts = Loader(client, dataset, args.users_password, args.draft, workers=args.workers).run()
    except CoreError as e:
        print(f"error -> {e.status}\n{e.body}", file=sys.stderr)
        return 1
    except LoadError as e:
        print(f"error: {e}", file=sys.stderr)
        return 1
    print(f"done: {counts.created} created, {counts.skipped} skipped, {counts.commands} commands, "
          f"{counts.warnings} warnings")
    return 0


if __name__ == "__main__":
    sys.exit(main())
