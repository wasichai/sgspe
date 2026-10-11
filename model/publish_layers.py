#!/usr/bin/env python3
"""Publica en GeoServer las capas públicas de SGSPE y carga sus estilos (gis.md §7.1 y §7.2, security.md §10.2).

1. Lee model/layers.json y lo valida contra model.json, roles.json, los SLD de model/styles y su palette.json: cada capa
   es una geometría declarada de un maestro del tenant (un objeto que no llega a `project` por sus campos RELATION, la
   misma partición que ProjectScope.TENANT) sin campos restringidos, y cada estilo tiene su SLD, que dibuja esa geometría,
   rotula el campo que pide la capa y usa exactamente los colores de la paleta. --validate-only termina aquí, sin red.
2. Como el administrador del tenant (--core, --email y --password, como apply.py), consulta GET /api/gis/layers y publica
   con POST /api/gis/layers/{objeto}/{geometría} las que falten. wasichai la nombra <tabla física>__<columna>. Una
   geometría que el tenant no tiene detiene todo antes del primer POST.
3. Con las credenciales del despliegue (WASICHAI_GIS_GEOSERVER_URL, _USERNAME y _PASSWORD, las mismas del backend; nunca
   por argumento ni archivo, y no se imprimen) crea o actualiza cada SLD por la API REST de GeoServer. Los estilos son
   globales, únicos por despliegue: el proxy WMS los pide por nombre (STYLES). Se suben con raw=true y se comparan por
   XML canónico, así que un reformateo no es un cambio.

Es idempotente: una segunda corrida no escribe nada. --dry-run consulta core y GeoServer y dice lo que haría.

Correr: python3 publish_layers.py [--dry-run | --validate-only] [--core URL] [--email E] [--password P]
Salida: 0 si va bien; 1 si core o GeoServer rechazan algo o no responden; 2 si layers.json, un SLD o la paleta no son
válidos, o si faltan las variables de GeoServer.
"""
import argparse
import base64
import http.client
import json
import os
import re
import sys
import urllib.error
import urllib.parse
import urllib.request
import xml.etree.ElementTree as ET

from core_client import Client, CoreError

HERE = os.path.dirname(os.path.abspath(__file__))
# las mismas variables con que el backend configura wasichai.gis.geoserver (application.yml)
GEOSERVER_VARIABLES = ("WASICHAI_GIS_GEOSERVER_URL", "WASICHAI_GIS_GEOSERVER_USERNAME", "WASICHAI_GIS_GEOSERVER_PASSWORD")
TIMEOUT_SECONDS = 60
SLD_TYPE = "application/vnd.ogc.sld+xml"
SLD = "{http://www.opengis.net/sld}"
OGC = "{http://www.opengis.net/ogc}"
LAYER_ID = re.compile(r"^LYR-\d{2}$")
STYLE_NAME = re.compile(r"^sgspe-lyr-\d{2}(-[a-z0-9]+)?$")
COLOR = re.compile(r"#[0-9a-fA-F]{6}\b")
REQUIRED_KEYS = ("layer", "object", "geometry", "style")
ENTRY_KEYS = frozenset(REQUIRED_KEYS + ("label",))
PALETTE_COLORS = ("stroke", "fill", "label", "halo")
SYMBOLIZERS = {"POINT": "PointSymbolizer", "MULTIPOINT": "PointSymbolizer", "LINESTRING": "LineSymbolizer",
               "MULTILINESTRING": "LineSymbolizer", "POLYGON": "PolygonSymbolizer", "MULTIPOLYGON": "PolygonSymbolizer"}


def redacted(url):
    """scheme://host[:port]/path de una URL, sin usuario, contraseña, consulta ni fragmento: es lo único que se imprime."""
    try:
        parts = urllib.parse.urlsplit(url)
        host = parts.hostname or ""
        port = f":{parts.port}" if parts.port else ""
        return f"{parts.scheme}://{host}{port}{parts.path}"
    except ValueError:
        return "(URL no válida)"


def url_problem(url):
    """Por qué una URL de GeoServer no sirve (sin repetirla), o None. Nunca lleva usuario ni contraseña."""
    try:
        parts = urllib.parse.urlsplit(url)
        parts.port  # noqa: B018  (valida el puerto)
    except ValueError:
        return "no es una URL válida"
    if parts.scheme not in ("http", "https") or not parts.netloc:
        return "debe ser http:// o https:// con servidor"
    if parts.username is not None or parts.password is not None:
        return "no puede llevar usuario ni contraseña: van en WASICHAI_GIS_GEOSERVER_USERNAME y _PASSWORD"
    return None


class PublishError(Exception):
    """Core o GeoServer rechazaron una llamada o no respondieron. El mensaje nunca lleva credenciales."""


def project_bound(model):
    """Los objetos que llegan a `project` por sus campos RELATION (project, sus raíces y sus hijos, a cualquier distancia):
    la partición de ProjectScope (security.md §9.7), que ArchitectureTest fija contra el código. Los demás son maestros del
    tenant, los únicos que GeoServer puede publicar (§10.2, regla 8)."""
    edges = [(r["source"], r["target"]) for r in model.get("relationships", []) if r.get("fieldName")]
    bound = {"project"}
    grew = True
    while grew:
        grew = False
        for source, target in edges:
            if target in bound and source not in bound:
                bound.add(source)
                grew = True
    return bound


def restricted_objects(roles):
    """Los objetos con algún campo restringido (roles.json, security.md §7.9): GeoServer los publicaría sin permisos."""
    return {entry["object"] for entry in roles.get("restrictedFields", [])}


def pairs(layers):
    """Las capas de GeoServer: una por (objeto, geometría), en el orden de layers.json."""
    return list(dict.fromkeys((entry["object"], entry["geometry"]) for entry in layers))


def validate(layers, model, roles, palette, styles_dir):
    """Los errores de layers.json, de sus SLD y de la paleta; [] si todo es válido."""
    if not isinstance(layers, list) or not layers:
        return ["layers.json debe ser una lista no vacía de capas"]
    styles = palette.get("styles") if isinstance(palette, dict) else None
    if not isinstance(styles, dict):
        return ["palette.json debe tener un objeto 'styles' con los colores de cada estilo"]
    objects = {o["name"]: o for o in model.get("objects", [])}
    bound = project_bound(model)
    closed = restricted_objects(roles)
    errors, seen = [], []
    for i, entry in enumerate(layers):
        where = f"layers[{i}]"
        if not isinstance(entry, dict):
            errors.append(f"{where}: debe ser un objeto")
            continue
        keys = set(entry)
        if keys - ENTRY_KEYS or not set(REQUIRED_KEYS) <= keys:
            errors.append(f"{where}: lleva las claves {', '.join(REQUIRED_KEYS)} y, si rotula, label; tiene {sorted(keys)}")
            continue
        layer, name, geometry, style, label = (entry["layer"], entry["object"], entry["geometry"], entry["style"],
                                               entry.get("label"))
        if not all(isinstance(v, str) for v in (name, geometry)) or not (label is None or isinstance(label, str)):
            errors.append(f"{where}: object, geometry y label deben ser texto")
            continue
        where = f"{where} ({layer} {name}.{geometry})"
        if not isinstance(layer, str) or not LAYER_ID.fullmatch(layer):
            errors.append(f"{where}: layer debe ser un id LYR-NN de gis.md §2.2")
        obj = objects.get(name)
        if obj is None:
            errors.append(f"{where}: {name} no es un objeto de model.json")
            continue
        if name in bound:
            errors.append(f"{where}: {name} es un objeto de proyecto; GeoServer solo publica maestros del tenant "
                          f"(security.md §10.2)")
        if name in closed:
            errors.append(f"{where}: {name} tiene campos restringidos (roles.json); GeoServer los publicaría sin permisos")
        fields = {f["name"]: f for f in obj["fields"]}
        field = fields.get(geometry)
        if field is None or field.get("type") != "GEOMETRY" or field.get("geometryType") not in SYMBOLIZERS:
            errors.append(f"{where}: {geometry} no es una geometría de {name} con geometryType")
            continue
        if label is not None and fields.get(label, {}).get("type") != "TEXT":
            errors.append(f"{where}: la etiqueta {label} debe ser un campo TEXT de {name}")
        if not isinstance(style, str) or not STYLE_NAME.fullmatch(style):
            errors.append(f"{where}: {style!r} no es un nombre de estilo sgspe-lyr-NN[-sufijo]")
            continue
        if style in seen:
            errors.append(f"{where}: el estilo {style} se repite: cada entrada tiene el suyo")
            continue
        seen.append(style)
        errors.extend(_sld_errors(where, layer, style, field["geometryType"], label, styles.get(style), styles_dir))
    for style in styles:
        if style not in seen:
            errors.append(f"palette.json: {style} no es el estilo de ninguna capa de layers.json")
    return errors


def _sld_errors(where, layer, style, geometry_type, label, colors, styles_dir):
    if not isinstance(colors, dict):
        return [f"{where}: palette.json no tiene los colores de {style}"]
    errors = []
    if colors.get("layer") != layer:
        errors.append(f"{where}: palette.json da {style} a {colors.get('layer')} y layers.json a {layer}")
    try:
        with open(os.path.join(styles_dir, f"{style}.sld"), encoding="utf-8") as f:
            text = f.read()
        root = ET.fromstring(text)
    except OSError:
        return errors + [f"{where}: falta model/styles/{style}.sld"]
    except ET.ParseError as e:
        return errors + [f"{where}: {style}.sld no es XML válido ({e})"]
    name = root.find(f".//{SLD}UserStyle/{SLD}Name")
    if name is None or (name.text or "").strip() != style:
        errors.append(f"{where}: el UserStyle de {style}.sld debe llamarse {style}")
    symbolizer = SYMBOLIZERS[geometry_type]
    if root.find(f".//{SLD}{symbolizer}") is None:
        errors.append(f"{where}: {style}.sld no dibuja la geometría {geometry_type} con un {symbolizer}")
    labels = {(p.text or "").strip() for t in root.iter(f"{SLD}TextSymbolizer") for p in t.iter(f"{OGC}PropertyName")}
    if labels != ({label} if label else set()):
        errors.append(f"{where}: {style}.sld rotula {sorted(labels)} y layers.json pide {label!r} (gis.md §7.2)")
    wanted = {str(colors[key]).lower() for key in PALETTE_COLORS if colors.get(key)}
    used = {color.lower() for color in COLOR.findall(text)}
    if used != wanted:
        errors.append(f"{where}: {style}.sld usa los colores {sorted(used)} y palette.json da {sorted(wanted)}")
    return errors


def same_sld(a, b):
    """Dos SLD iguales salvo espacios, comentarios y la declaración XML."""
    try:
        return ET.canonicalize(a, strip_text=True) == ET.canonicalize(b, strip_text=True)
    except ET.ParseError:
        return False


def _excerpt(text):
    return re.sub(r"\s+", " ", text or "").strip()[:200]


class GeoServer:
    """La API REST de estilos globales de GeoServer (rest/styles) con autenticación básica."""

    def __init__(self, base_url, username, password):
        self.base_url = base_url.rstrip("/")
        self._authorization = "Basic " + base64.b64encode(f"{username}:{password}".encode("utf-8")).decode("ascii")

    def _call(self, method, path, body=None):
        headers = {"Authorization": self._authorization}
        data = None
        if body is not None:
            headers["Content-Type"] = SLD_TYPE
            data = body.encode("utf-8")
        try:
            request = urllib.request.Request(self.base_url + path, data=data, method=method, headers=headers)
            with urllib.request.urlopen(request, timeout=TIMEOUT_SECONDS) as response:
                return response.status, response.read().decode("utf-8")
        except urllib.error.HTTPError as e:
            return e.code, e.read().decode("utf-8", errors="replace")
        except (urllib.error.URLError, OSError) as e:
            raise PublishError(f"GeoServer no responde en {redacted(self.base_url)}: {getattr(e, 'reason', e)}") from None
        except (ValueError, http.client.InvalidURL):
            raise PublishError(f"GeoServer: la URL {redacted(self.base_url)} no es válida") from None

    @staticmethod
    def _refused(what, status, text):
        hint = " (revise WASICHAI_GIS_GEOSERVER_USERNAME y WASICHAI_GIS_GEOSERVER_PASSWORD)" if status == 401 else ""
        return PublishError(f"GeoServer rechazó {what}: {status}{hint} {_excerpt(text)}".rstrip())

    def style(self, name):
        """El SLD guardado del estilo, o None si GeoServer no lo tiene."""
        status, text = self._call("GET", f"/rest/styles/{urllib.parse.quote(name)}.sld")
        if status == 404:
            return None
        if status != 200:
            raise self._refused(f"leer el estilo {name}", status, text)
        return text

    def create_style(self, name, sld):
        status, text = self._call("POST", f"/rest/styles?name={urllib.parse.quote(name)}&raw=true", sld)
        if status != 201:
            raise self._refused(f"el alta del estilo {name}", status, text)
        return status

    def update_style(self, name, sld):
        status, text = self._call("PUT", f"/rest/styles/{urllib.parse.quote(name)}?raw=true", sld)
        if status != 200:
            raise self._refused(f"el cambio del estilo {name}", status, text)
        return status


def publish(client, layers, dry_run):
    """Publica por core las capas de `layers` que faltan. Devuelve (publicadas, omitidas)."""
    status = client.get("/api/gis/layers")
    if status is None:
        raise PublishError("el core no tiene GET /api/gis/layers (404): le falta wasichai-gis")
    known = {(s["objectName"], s["geometryName"]): s for s in status}
    wanted = pairs(layers)
    missing = [f"{name}.{geometry}" for name, geometry in wanted if (name, geometry) not in known]
    if missing:
        raise PublishError(f"el tenant no tiene {', '.join(missing)}: corra apply.py en este tenant antes de publicar")
    published = skipped = 0
    for name, geometry in wanted:
        current = known[(name, geometry)]
        if current.get("published"):
            print(f"skip   layer {name}.{geometry} ({current.get('layerName')})")
            skipped += 1
            continue
        if dry_run:
            print(f"would publish layer {name}.{geometry}")
        else:
            path = f"/api/gis/layers/{name}/{geometry}"
            try:
                code, body = client.post(path, None)
            except CoreError as e:
                raise PublishError(f"publicar {name}.{geometry} (POST {path}): {e.status} {_excerpt(e.body)}") from None
            print(f"publish layer {name}.{geometry} -> {(body or {}).get('layerName')} ({code})")
        published += 1
    return published, skipped


def load_styles(geoserver, layers, styles_dir, dry_run):
    """Crea o actualiza en GeoServer el SLD de cada estilo. Devuelve (creados, actualizados, omitidos)."""
    created = updated = skipped = 0
    for entry in layers:
        name = entry["style"]
        with open(os.path.join(styles_dir, f"{name}.sld"), encoding="utf-8") as f:
            sld = f.read()
        current = geoserver.style(name)
        if current is not None and same_sld(current, sld):
            print(f"skip   style {name} (same)")
            skipped += 1
            continue
        if dry_run:
            print(f"would {'create' if current is None else 'update'} style {name}")
        elif current is None:
            print(f"create style {name} ({geoserver.create_style(name, sld)})")
        else:
            print(f"update style {name} ({geoserver.update_style(name, sld)})")
        if current is None:
            created += 1
        else:
            updated += 1
    return created, updated, skipped


class _QuietParser(argparse.ArgumentParser):
    """Un argumento rechazado puede ser una contraseña puesta por error: el mensaje no lo repite."""

    def parse_args(self, args=None, namespace=None):
        parsed, extra = self.parse_known_args(args, namespace)
        if extra:
            self.error("argumentos desconocidos: las credenciales de GeoServer van solo por variables de entorno")
        return parsed


def _parse_args(argv):
    p = _QuietParser(description="Publica las capas públicas de SGSPE en GeoServer y carga sus estilos.")
    p.add_argument("--layers", default=os.path.join(HERE, "layers.json"))
    p.add_argument("--styles", default=os.path.join(HERE, "styles"))
    p.add_argument("--model", default=os.path.join(HERE, "model.json"))
    p.add_argument("--roles", default=os.path.join(HERE, "roles.json"))
    p.add_argument("--core", default=os.environ.get("WASICHAI_CORE", "http://localhost:8094"))
    p.add_argument("--email", default=os.environ.get("WASICHAI_EMAIL", "admin@wasichai.local"))
    p.add_argument("--password", default=os.environ.get("WASICHAI_PASSWORD", "admin"))
    mode = p.add_mutually_exclusive_group()
    mode.add_argument("--dry-run", action="store_true", help="consulta core y GeoServer y dice lo que haría; no escribe")
    mode.add_argument("--validate-only", action="store_true", help="valida layers.json, los SLD y la paleta; sin red")
    return p.parse_args(argv)


def _load(path):
    try:
        with open(path, encoding="utf-8") as f:
            return json.load(f), None
    except (OSError, ValueError) as e:
        return None, f"{path}: {e}"


def main(argv=None):
    try:
        args = _parse_args(sys.argv[1:] if argv is None else argv)
    except SystemExit:
        return 2
    loaded = {}
    for key, path in (("layers", args.layers), ("model", args.model), ("roles", args.roles),
                      ("palette", os.path.join(args.styles, "palette.json"))):
        data, error = _load(path)
        if error:
            print(f"layers: {error}")
            return 2
        loaded[key] = data
    errors = validate(loaded["layers"], loaded["model"], loaded["roles"], loaded["palette"], args.styles)
    if errors:
        for e in errors:
            print(f"layers: {e}")
        return 2
    layers = loaded["layers"]
    if args.validate_only:
        print(f"valid: {len(pairs(layers))} layers, {len(layers)} styles")
        return 0
    # solo la URL y el usuario se recortan: una contraseña puede llevar espacios
    settings = tuple(os.environ.get(name) or "" for name in GEOSERVER_VARIABLES)
    settings = tuple(v if name.endswith("PASSWORD") else v.strip() for name, v in zip(GEOSERVER_VARIABLES, settings))
    if not all(settings):
        missing = ", ".join(name for name, value in zip(GEOSERVER_VARIABLES, settings) if not value)
        print(f"error: faltan {missing}: las credenciales de GeoServer van solo por variables de entorno; un despliegue "
              f"sin GeoServer aprovisiona con --skip-layers", file=sys.stderr)
        return 2
    problem = url_problem(settings[0])
    if problem:
        print(f"error: WASICHAI_GIS_GEOSERVER_URL {problem}", file=sys.stderr)
        return 2
    client = Client(args.core)
    try:
        client.login(args.email, args.password)
    except CoreError as e:
        print(f"error: POST /api/auth/login: {e.status}", file=sys.stderr)
        return 1
    try:
        published, skipped = publish(client, layers, args.dry_run)
        created, updated, kept = load_styles(GeoServer(*settings), layers, args.styles, args.dry_run)
    except CoreError as e:
        print(f"error: core: {e.status} {_excerpt(e.body)}", file=sys.stderr)
        return 1
    except PublishError as e:
        print(f"error: {e}", file=sys.stderr)
        return 1
    word = "dry-run" if args.dry_run else "done"
    print(f"{word}: {len(pairs(layers))} layers ({published} published, {skipped} skipped), {len(layers)} styles "
          f"({created} created, {updated} updated, {kept} skipped)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
