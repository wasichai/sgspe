"""Un GeoServer de imitación para test_publish_layers.py: la API REST de estilos globales (GET .sld, POST con ?name= y PUT)
con autenticación básica, en memoria, como responde GeoServer: 401 con otras credenciales, 404 si el estilo no existe y
403 si el alta repite un nombre. Solo para las pruebas."""
import base64
import re
import threading
import urllib.parse
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

STYLES = "/geoserver/rest/styles"
STYLE = re.compile(r"^/geoserver/rest/styles/([A-Za-z0-9_.-]+?)(\.sld)?$")
SLD_TYPE = "application/vnd.ogc.sld+xml"


class FakeGeoServerHandler(BaseHTTPRequestHandler):
    def log_message(self, *args):
        pass  # keep test output quiet

    def _handle(self, method):
        length = int(self.headers.get("Content-Length", 0))
        body = self.rfile.read(length).decode("utf-8") if length else None
        authorization, content_type = self.headers.get("Authorization"), self.headers.get("Content-Type")
        with self.server.lock:
            self.server.requests.append((method, self.path, authorization, content_type, body))
            status, text = self.server.script(method, self.path, authorization, content_type, body)
        self.send_response(status)
        self.send_header("Content-Type", SLD_TYPE if method == "GET" and status == 200 else "text/plain")
        self.end_headers()
        if text:
            self.wfile.write(text.encode("utf-8"))

    def do_GET(self):
        self._handle("GET")

    def do_POST(self):
        self._handle("POST")

    def do_PUT(self):
        self._handle("PUT")


class FakeGeoServer:
    def __init__(self, username, password, styles=None):
        self.expected = "Basic " + base64.b64encode(f"{username}:{password}".encode("utf-8")).decode("ascii")
        self.styles = dict(styles or {})  # nombre -> SLD tal como se guardó
        self.requests = []  # (método, ruta, Authorization, Content-Type, cuerpo)
        self.server = ThreadingHTTPServer(("127.0.0.1", 0), FakeGeoServerHandler)
        self.server.requests = self.requests
        self.server.script = self._script
        self.server.lock = threading.Lock()
        self.port = self.server.server_address[1]
        self.base_url = f"http://127.0.0.1:{self.port}/geoserver"
        self.thread = threading.Thread(target=self.server.serve_forever, daemon=True)
        self.thread.start()

    def stop(self):
        self.server.shutdown()
        self.server.server_close()

    def writes(self):
        return [(method, path) for method, path, *_ in self.requests if method in ("POST", "PUT")]

    def _script(self, method, full_path, authorization, content_type, body):
        if authorization != self.expected:
            return 401, "HTTP Status 401 - Unauthorized"
        path, _, query = full_path.partition("?")
        params = dict(urllib.parse.parse_qsl(query))
        if path == STYLES and method == "POST":
            name = params.get("name")
            if content_type != SLD_TYPE or not name or params.get("raw") != "true":
                return 400, "an SLD body, ?name= and ?raw=true"
            if name in self.styles:
                return 403, f"Style {name} already exists"
            self.styles[name] = body
            return 201, name
        match = STYLE.match(path)
        if match and method == "GET" and match.group(2):
            name = match.group(1)
            return (200, self.styles[name]) if name in self.styles else (404, f"No such style: {name}")
        if match and method == "PUT" and not match.group(2):
            name = match.group(1)
            if name not in self.styles:
                return 404, f"No such style: {name}"
            if content_type != SLD_TYPE or params.get("raw") != "true":
                return 400, "an SLD body and ?raw=true"
            self.styles[name] = body
            return 200, ""
        return 405, "not scripted"
