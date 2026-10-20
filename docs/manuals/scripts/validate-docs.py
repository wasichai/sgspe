#!/usr/bin/env python3
"""Valida las fuentes y las salidas de los manuales de SGSPE (solo biblioteca estándar; pdfinfo y pdffonts opcionales).

  python3 docs/manuals/scripts/validate-docs.py [--sources-only]

Comprueba:
  1. que reference/generated/ esté al día con model/ (generate-reference.py --check);
  2. que cada include:: e image:: apunte a un archivo que existe y que toda captura se use en algún manual;
  3. que no haya archivos .adoc vacíos, rutas absolutas locales, marcadores pendientes ni secretos aparentes;
  4. (salvo --sources-only) que el HTML de output/html no tenga anclas ni enlaces relativos rotos ni imágenes ausentes;
  5. (salvo --sources-only) que cada PDF exista, tenga páginas y lleve todas sus fuentes incrustadas.
Salida: 0 si todo está bien, 1 si hay problemas (se listan todos).
"""
import argparse
import glob
import html.parser
import os
import re
import shutil
import subprocess
import sys
import urllib.parse

HERE = os.path.dirname(os.path.abspath(__file__))
MANUALS = os.path.dirname(HERE)
BOOKS = ["user/manual-usuario", "administrator/manual-administrador", "system/manual-sistema",
         "reference/referencia-tecnica"]
INCLUDE = re.compile(r"^include::([^\[]+)\[", re.M)
IMAGE = re.compile(r"image::?([^\[\s]+)\[")
PENDING = re.compile(r"\b(TODO|FIXME|XXX|TBD|PENDIENTE DE REDACTAR)\b|lorem ipsum")
ABSOLUTE = re.compile(r"(/home/[a-z]|/tmp/claude|/Users/[A-Za-z]|[A-Z]:\\\\)")
SECRETS = [
    (re.compile(r"sk-ant-[A-Za-z0-9_-]{10,}"), "clave de Anthropic"),
    (re.compile(r"gh[pousr]_[A-Za-z0-9]{20,}"), "token de GitHub"),
    (re.compile(r"eyJ[A-Za-z0-9_-]{15,}\.[A-Za-z0-9_-]{15,}\."), "JWT"),
    (re.compile(r"AKIA[0-9A-Z]{16}"), "clave de AWS"),
    (re.compile(r"-----BEGIN [A-Z ]*PRIVATE KEY-----"), "clave privada"),
]

problems = []


def problem(msg):
    problems.append(msg)


def sources():
    for path in glob.glob(os.path.join(MANUALS, "**", "*.adoc"), recursive=True):
        if "/output/" in path or "/node_modules/" in path:
            continue
        yield path


def check_generated():
    out = subprocess.run([sys.executable, os.path.join(HERE, "generate-reference.py"), "--check"],
                         capture_output=True, text=True)
    if out.returncode != 0:
        problem(out.stdout.strip() or out.stderr.strip())


def check_sources():
    used_images = set()
    for path in sources():
        rel = os.path.relpath(path, MANUALS)
        text = open(path, encoding="utf-8").read()
        if not text.strip():
            problem(f"{rel}: archivo vacío")
        base = os.path.dirname(path)
        for inc in INCLUDE.findall(text):
            target = os.path.normpath(os.path.join(base, inc))
            if not os.path.exists(target):
                problem(f"{rel}: include inexistente {inc}")
        for img in IMAGE.findall(text):
            if img.startswith(("http", "{")):
                continue
            target = os.path.normpath(os.path.join(MANUALS, "assets", img))
            if not os.path.exists(target):
                problem(f"{rel}: imagen inexistente {img}")
            used_images.add(os.path.normpath(img))
        for n, line in enumerate(text.splitlines(), 1):
            if ABSOLUTE.search(line):
                problem(f"{rel}:{n}: ruta absoluta local")
            if PENDING.search(line) and not line.lstrip().startswith("//"):
                problem(f"{rel}:{n}: marcador pendiente «{PENDING.search(line).group(0)}»")
    for shot in glob.glob(os.path.join(MANUALS, "assets", "screenshots", "**", "*.png"), recursive=True):
        rel = os.path.relpath(shot, os.path.join(MANUALS, "assets"))
        if os.path.normpath(rel) not in used_images:
            problem(f"captura sin usar en ningún manual: {rel}")
    # secretos: en todo lo de texto que se versiona
    for path in glob.glob(os.path.join(MANUALS, "**", "*"), recursive=True):
        if any(p in path for p in ("/node_modules/", "/output/", "/fonts/")) or os.path.isdir(path):
            continue
        if not path.endswith((".adoc", ".md", ".json", ".yml", ".mjs", ".py", ".sh", ".css", ".html", ".mmd")):
            continue
        text = open(path, encoding="utf-8", errors="replace").read()
        for rx, what in SECRETS:
            if rx.search(text):
                problem(f"{os.path.relpath(path, MANUALS)}: posible {what}")


class Links(html.parser.HTMLParser):
    def __init__(self):
        super().__init__()
        self.ids, self.hrefs, self.srcs = set(), [], []

    def handle_starttag(self, tag, attrs):
        a = dict(attrs)
        if a.get("id"):
            self.ids.add(a["id"])
        if tag == "a" and a.get("name"):
            self.ids.add(a["name"])
        if tag == "a" and a.get("href"):
            self.hrefs.append(a["href"])
        if tag in ("img", "script") and a.get("src"):
            self.srcs.append(a["src"])
        if tag == "link" and a.get("href"):
            self.srcs.append(a["href"])


def parse(path):
    p = Links()
    p.feed(open(path, encoding="utf-8").read())
    return p


def check_html():
    root = os.path.join(MANUALS, "output", "html")
    pages = glob.glob(os.path.join(root, "**", "*.html"), recursive=True)
    if not pages:
        problem("no hay HTML en output/html: corra build-docs.sh html")
        return
    parsed = {os.path.normpath(p): parse(p) for p in pages}
    for page, info in parsed.items():
        rel = os.path.relpath(page, root)
        for src in info.srcs:
            if src.startswith(("http:", "https:", "data:")):
                continue
            target = os.path.normpath(os.path.join(os.path.dirname(page), urllib.parse.unquote(src.split("#")[0])))
            if not os.path.exists(target):
                problem(f"html {rel}: recurso inexistente {src}")
        for href in info.hrefs:
            if href.startswith(("http:", "https:", "mailto:")):
                continue
            path, _, frag = href.partition("#")
            target = page if not path else os.path.normpath(os.path.join(os.path.dirname(page), urllib.parse.unquote(path)))
            if target.endswith(".pdf"):
                if not os.path.exists(target.replace(os.path.join("output", "html"), os.path.join("output", "pdf"))) \
                        and not os.path.exists(target):
                    problem(f"html {rel}: PDF enlazado inexistente {href}")
                continue
            if not os.path.exists(target):
                problem(f"html {rel}: enlace roto {href}")
                continue
            if frag and target in parsed and urllib.parse.unquote(frag) not in parsed[target].ids:
                problem(f"html {rel}: ancla inexistente {href}")


def check_pdf():
    if not shutil.which("pdfinfo"):
        print("aviso: sin pdfinfo/pdffonts (poppler); se omite la revisión de los PDF")
        return
    for book in BOOKS:
        pdf = os.path.join(MANUALS, "output", "pdf", book + ".pdf")
        if not os.path.exists(pdf):
            problem(f"falta {os.path.relpath(pdf, MANUALS)}: corra build-docs.sh pdf")
            continue
        info = subprocess.run(["pdfinfo", pdf], capture_output=True, text=True).stdout
        pages = int(re.search(r"Pages:\s+(\d+)", info).group(1)) if "Pages:" in info else 0
        if pages < 2:
            problem(f"{book}.pdf: {pages} páginas")
        fonts = subprocess.run(["pdffonts", pdf], capture_output=True, text=True).stdout.splitlines()[2:]
        not_embedded = sorted({f.split()[0] for f in fonts if len(f.split()) > 4 and f.split()[-5] == "no"})
        if not_embedded:
            problem(f"{book}.pdf: fuentes sin incrustar {not_embedded}")
        print(f"  {book}.pdf: {pages} páginas, {len(fonts)} fuentes incrustadas")


def main():
    ap = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    ap.add_argument("--sources-only", action="store_true")
    args = ap.parse_args()
    check_generated()
    check_sources()
    if not args.sources_only:
        check_html()
        check_pdf()
    for p in problems:
        print(f"✗ {p}")
    print(f"validación: {len(problems)} problemas")
    return 1 if problems else 0


if __name__ == "__main__":
    sys.exit(main())
