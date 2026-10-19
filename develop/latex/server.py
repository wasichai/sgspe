#!/usr/bin/env python3
"""sgspe-latex: compone con LuaLaTeX el paquete de un entregable (ADR-0030, ASM-139). Solo biblioteca estándar.

POST /render recibe un tar (application/x-tar) con main.tex, la clase y las imágenes que arma LatexWriter, y responde un
tar con candidata.pdf (la composición completa) y borrador.pdf (una pasada más sobre los auxiliares de la candidata, con
\\sgspeborrador definido, que pone la marca BORRADOR). GET /health responde 200.

- El tar tiene topes de bytes y de entradas, solo archivos regulares y rutas relativas sin «..» con letras, cifras, «.»,
  «_», «-» y «/»; debe traer main.tex.
- Compone en una carpeta temporal, sin shell-escape, con --nosocket y openout_any=p, un plazo por pedido y una
  composición a la vez.
- Un error responde JSON con su código y, si lo hay, la línea de main.tex; nunca texto del documento. Los logs llevan
  el id de la corrida (cabecera X-Trace), el estado y la duración, nunca contenido.
- La cabecera X-Latex-Overfull dice cuántas cajas desbordadas tuvo la candidata.

Variables: LATEX_PORT (8080), LATEX_MAX_BYTES (104857600), LATEX_MAX_ENTRIES (2000), LATEX_TIMEOUT (170, segundos).
"""
import io
import json
import os
import re
import shutil
import subprocess
import sys
import tarfile
import tempfile
import threading
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

MAX_BYTES = int(os.environ.get("LATEX_MAX_BYTES", str(100 * 1024 * 1024)))
MAX_ENTRIES = int(os.environ.get("LATEX_MAX_ENTRIES", "2000"))
TIMEOUT = int(os.environ.get("LATEX_TIMEOUT", "170"))
NAME = re.compile(r"^[A-Za-z0-9._-]+(?:/[A-Za-z0-9._-]+)*$")
LINE = re.compile(r"^(?:\./)?main\.tex:(\d+):", re.MULTILINE)
AUXILIARY = ("aux", "toc", "lot", "lof", "out")
# la caché de luaotfload armada en la imagen (de solo lectura) y su copia escribible en el tmpfs
CACHE_IMAGE = "/opt/sgspe-latex/texmf-var"
CACHE = os.environ.get("TEXMFVAR", "/tmp/texmf-var")
LOCK = threading.Lock()


class Failure(Exception):
    """Un pedido que no se compone: estado HTTP, código y, si se sabe, la línea de main.tex."""

    def __init__(self, status, code, line=None):
        super().__init__(code)
        self.status, self.code, self.line = status, code, line


def unpack(body, target):
    """Extrae el tar en `target` validando cada entrada; devuelve los nombres extraídos."""
    try:
        archive = tarfile.open(fileobj=io.BytesIO(body), mode="r:")
    except tarfile.TarError:
        raise Failure(400, "NOT_A_TAR")
    names = []
    with archive:
        members = archive.getmembers()
        if len(members) > MAX_ENTRIES:
            raise Failure(413, "TOO_MANY_ENTRIES")
        for member in members:
            if member.isdir():
                continue
            if not member.isfile() or not NAME.match(member.name) or ".." in member.name.split("/"):
                raise Failure(400, "BAD_ENTRY")
            path = os.path.join(target, member.name)
            os.makedirs(os.path.dirname(path), exist_ok=True)
            source = archive.extractfile(member)
            with open(path, "wb") as out:
                shutil.copyfileobj(source, out)
            names.append(member.name)
    if "main.tex" not in names:
        raise Failure(400, "NO_MAIN")
    return names


def environment():
    """El entorno de TeX: sin shell-escape y sin escribir fuera de la carpeta de trabajo."""
    return {
        "PATH": os.environ.get("PATH", "/usr/bin:/bin"),
        "HOME": os.environ.get("HOME", "/home/tex"),
        "LANG": "C.UTF-8",
        "TEXMFVAR": CACHE,
        "TEXMFCACHE": CACHE,
        "shell_escape": "f",
        "openout_any": "p",
    }


def run(command, cwd, deadline):
    """Corre un comando con lo que queda del plazo; un plazo vencido es TIMEOUT."""
    remaining = deadline - time.monotonic()
    if remaining <= 0:
        raise Failure(504, "TIMEOUT")
    try:
        return subprocess.run(command, cwd=cwd, env=environment(), stdout=subprocess.DEVNULL,
                              stderr=subprocess.DEVNULL, stdin=subprocess.DEVNULL, timeout=remaining).returncode
    except subprocess.TimeoutExpired:
        raise Failure(504, "TIMEOUT")


def error_line(log_path):
    """La línea de main.tex del primer error del log, sin su texto."""
    try:
        with open(log_path, encoding="utf-8", errors="replace") as f:
            match = LINE.search(f.read())
        return int(match.group(1)) if match else None
    except OSError:
        return None


def overfull(log_path):
    try:
        with open(log_path, encoding="utf-8", errors="replace") as f:
            return sum(1 for line in f if line.startswith("Overfull \\hbox"))
    except OSError:
        return 0


def compose(body):
    """Compone la candidata y el borrador; devuelve (tar con los dos PDF, cajas desbordadas)."""
    deadline = time.monotonic() + TIMEOUT
    work = tempfile.mkdtemp(prefix="sgspe-latex-")
    try:
        unpack(body, work)
        lualatex = "lualatex --nosocket -file-line-error %O %S"
        code = run(["latexmk", "-lualatex=" + lualatex, "-interaction=nonstopmode", "-halt-on-error",
                    "-no-shell-escape", "main.tex"], work, deadline)
        if code != 0 or not os.path.exists(os.path.join(work, "main.pdf")):
            raise Failure(422, "LATEX_ERROR", error_line(os.path.join(work, "main.log")))
        for ext in AUXILIARY:
            source = os.path.join(work, "main." + ext)
            if os.path.exists(source):
                shutil.copyfile(source, os.path.join(work, "borrador." + ext))
        code = run(["lualatex", "--nosocket", "-file-line-error", "-interaction=nonstopmode", "-halt-on-error",
                    "-no-shell-escape", "-jobname=borrador", r"\def\sgspeborrador{1}\input{main.tex}"], work, deadline)
        if code != 0 or not os.path.exists(os.path.join(work, "borrador.pdf")):
            raise Failure(422, "LATEX_ERROR", error_line(os.path.join(work, "borrador.log")))
        out = io.BytesIO()
        with tarfile.open(fileobj=out, mode="w") as archive:
            for name, path in (("candidata.pdf", "main.pdf"), ("borrador.pdf", "borrador.pdf")):
                archive.add(os.path.join(work, path), arcname=name)
        return out.getvalue(), overfull(os.path.join(work, "main.log"))
    finally:
        shutil.rmtree(work, ignore_errors=True)


def log(**fields):
    print(json.dumps(fields, ensure_ascii=False), file=sys.stderr, flush=True)


class Handler(BaseHTTPRequestHandler):
    server_version = "sgspe-latex"
    sys_version = ""

    def log_message(self, format, *args):
        pass

    def reply(self, status, body, content_type, headers=None):
        self.send_response(status)
        self.send_header("Content-Type", content_type)
        self.send_header("Content-Length", str(len(body)))
        for k, v in (headers or {}).items():
            self.send_header(k, v)
        self.end_headers()
        self.wfile.write(body)

    def fail(self, failure):
        payload = {"code": failure.code}
        if failure.line is not None:
            payload["line"] = failure.line
        self.reply(failure.status, json.dumps(payload).encode(), "application/json")

    def do_GET(self):
        if self.path == "/health":
            self.reply(200, b'{"status":"up"}', "application/json")
        else:
            self.fail(Failure(404, "NOT_FOUND"))

    def do_POST(self):
        trace = self.headers.get("X-Trace") or "-"
        started = time.monotonic()
        status = 500
        try:
            if self.path != "/render":
                raise Failure(404, "NOT_FOUND")
            length = int(self.headers.get("Content-Length") or "0")
            if length <= 0:
                raise Failure(400, "EMPTY")
            if length > MAX_BYTES:
                raise Failure(413, "TOO_LARGE")
            body = self.rfile.read(length)
            with LOCK:
                pdfs, boxes = compose(body)
            status = 200
            self.reply(200, pdfs, "application/x-tar", {"X-Latex-Overfull": str(boxes)})
        except Failure as failure:
            status = failure.status
            self.fail(failure)
        except Exception:  # noqa: BLE001 un error inesperado no deja texto del documento en la respuesta
            status = 500
            self.fail(Failure(500, "INTERNAL"))
        finally:
            log(trace=trace, status=status, ms=int((time.monotonic() - started) * 1000))


def prepare_cache():
    """Copia la caché de luaotfload de la imagen al tmpfs, una vez: luaotfload no corre sin una caché escribible."""
    if os.path.isdir(CACHE_IMAGE) and not os.path.exists(CACHE):
        shutil.copytree(CACHE_IMAGE, CACHE)


def main():
    prepare_cache()
    port = int(os.environ.get("LATEX_PORT", "8080"))
    server = ThreadingHTTPServer(("0.0.0.0", port), Handler)
    log(event="listening", port=port)
    server.serve_forever()


if __name__ == "__main__":
    main()
