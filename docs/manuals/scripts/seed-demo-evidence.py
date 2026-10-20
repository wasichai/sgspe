#!/usr/bin/env python3
"""Sube a la base de demostración unas evidencias sintéticas (PDF y PNG generados aquí, sin datos reales) y las vincula
a registros del conjunto ampliado, por la API real: POST /api/objects/evidence/files/file (archivo en espera) y
POST /api/sgspe/evidence (alta con vínculos), como lo hace el portal. Solo biblioteca estándar.

Idempotente: una evidencia cuyo título ya existe no se vuelve a subir. Corre después de load_demo.py con el conjunto
ampliado (docs/manuals/database/seeds/ampliado).

  SGSPE_DEMO_PASSWORD=… python3 docs/manuals/scripts/seed-demo-evidence.py [--core URL]

Salida: 0 si va bien, 1 si el servidor rechaza algo.
"""
import argparse
import hashlib
import json
import os
import struct
import sys
import urllib.error
import urllib.parse
import urllib.request
import uuid
import zlib

sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "..", "model"))
from core_client import Client, CoreError  # noqa: E402

USER = "gestion.social.demo@sgspe.test"
REASON = "Carga de evidencias sintéticas de la demostración"


def pdf(title, lines):
    """Un PDF mínimo y válido de una página, con texto en Helvetica (WinAnsi)."""
    def esc(s):
        return s.replace("\\", "\\\\").replace("(", "\\(").replace(")", "\\)")
    body = ["BT /F2 15 Tf 56 770 Td (" + esc(title) + ") Tj ET"]
    y = 740
    for line in lines:
        body.append(f"BT /F1 11 Tf 56 {y} Td (" + esc(line) + ") Tj ET")
        y -= 18
    body.append("BT /F1 8 Tf 56 60 Td (Documento ficticio generado para la demostracion de SGSPE.) Tj ET")
    stream = "\n".join(body).encode("cp1252")
    objs = [
        b"<< /Type /Catalog /Pages 2 0 R >>",
        b"<< /Type /Pages /Kids [3 0 R] /Count 1 >>",
        b"<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] /Resources << /Font << /F1 4 0 R /F2 5 0 R >> >> /Contents 6 0 R >>",
        b"<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica /Encoding /WinAnsiEncoding >>",
        b"<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica-Bold /Encoding /WinAnsiEncoding >>",
        b"<< /Length " + str(len(stream)).encode() + b" >>\nstream\n" + stream + b"\nendstream",
    ]
    out = bytearray(b"%PDF-1.4\n")
    offsets = []
    for i, o in enumerate(objs, 1):
        offsets.append(len(out))
        out += f"{i} 0 obj\n".encode() + o + b"\nendobj\n"
    xref = len(out)
    out += f"xref\n0 {len(objs) + 1}\n0000000000 65535 f \n".encode()
    for off in offsets:
        out += f"{off:010d} 00000 n \n".encode()
    out += f"trailer\n<< /Size {len(objs) + 1} /Root 1 0 R >>\nstartxref\n{xref}\n%%EOF\n".encode()
    return bytes(out)


def png(width, height, seed):
    """Un paisaje de puna sintético (cielo, cerros y pampa) en PNG RGB, determinista según `seed`."""
    h = int(hashlib.sha256(seed.encode()).hexdigest()[:8], 16)
    rows = []
    for y in range(height):
        row = bytearray([0])
        for x in range(width):
            ridge = height * (0.42 + 0.10 * ((x * 7 + h) % 97) / 97 - 0.08 * abs(((x + h) % 260) - 130) / 130)
            if y < ridge:
                t = y / max(ridge, 1)
                px = (int(120 + 90 * t), int(170 + 60 * t), int(220 + 25 * t))
            elif y < height * 0.72:
                px = (int(88 + (x % 13)), int(104 + (y % 9)), int(82 + (x % 7)))
            else:
                px = (int(150 + (x * y) % 30), int(140 + (x % 17)), int(90 + (y % 11)))
            row += bytes(px)
        rows.append(bytes(row))
    raw = zlib.compress(b"".join(rows), 9)

    def chunk(tag, data):
        return struct.pack(">I", len(data)) + tag + data + struct.pack(">I", zlib.crc32(tag + data) & 0xFFFFFFFF)
    return (b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", width, height, 8, 2, 0, 0, 0))
            + chunk(b"IDAT", raw) + chunk(b"IEND", b""))


EVIDENCES = [
    {
        "title": "Acta de inspección del canal principal de Huaylla Alto",
        "file": ("acta-inspeccion-canal.pdf", "application/pdf", lambda: pdf(
            "Acta de inspeccion del canal principal (ficticia)",
            ["Caserio Huaylla Alto, 17 de octubre de 2026.", "",
             "Participantes: junta directiva del comite de regantes, relacionista comunitario",
             "y supervisor de la contratista.", "",
             "Se recorrio el canal principal desde la toma hasta el reservorio. Se constato",
             "sedimento en dos tramos cercanos al cruce de la trocha. Se acordo una faena",
             "conjunta de limpieza y la instalacion de una alcantarilla provisional.", "",
             "Firmas: (ficticias)"])),
        "attributes": {"evidence_type": "ACTA", "classification": "INTERNA", "document_date": "2026-10-17",
                       "author": "Relaciones comunitarias (demo)"},
        "link": ("grievance", "DEMO-Q-01"),
    },
    {
        "title": "Foto del tramo con sedimento del canal principal",
        "file": ("foto-canal.png", "image/png", lambda: png(640, 400, "canal")),
        "attributes": {"evidence_type": "FOTO", "classification": "INTERNA", "document_date": "2026-10-17",
                       "author": "Relaciones comunitarias (demo)", "people_identifiable": False},
        "link": ("grievance", "DEMO-Q-01"),
    },
    {
        "title": "Carta de respuesta sobre el monitoreo de manantiales",
        "file": ("carta-respuesta-monitoreo.pdf", "application/pdf", lambda: pdf(
            "Carta de respuesta (ficticia)",
            ["San Juan, 15 de septiembre de 2026.", "",
             "Senores de la junta directiva de la comunidad:",
             "En respuesta a su consulta, el plan de monitoreo participativo de agua incluye",
             "los tres manantiales de la parte alta. Les invitamos a designar dos veedores.", "",
             "Atentamente, area de relaciones comunitarias del proyecto (ficticio)."])),
        "attributes": {"evidence_type": "CARTA", "classification": "PUBLICA", "document_date": "2026-09-15",
                       "author": "Atención de QCS (demo)"},
        "link": ("inquiry", "DEMO-C-03"),
        "as": "qcs.demo@sgspe.test",
    },
    {
        "title": "Foto de la asamblea informativa de la campaña",
        "file": ("foto-asamblea.png", "image/png", lambda: png(640, 400, "asamblea")),
        "attributes": {"evidence_type": "FOTO", "classification": "INTERNA", "document_date": "2026-09-30",
                       "author": "Gestión social (demo)", "people_identifiable": True, "photo_consent_given": False},
        "link": ("social_contribution", "DEMO-AP-02"),
    },
]


def multipart(field, filename, content_type, data):
    boundary = "----sgspe" + uuid.uuid4().hex
    head = (f"--{boundary}\r\nContent-Disposition: form-data; name=\"{field}\"; filename=\"{filename}\"\r\n"
            f"Content-Type: {content_type}\r\n\r\n").encode()
    return boundary, head + data + f"\r\n--{boundary}--\r\n".encode()


def stage(core, token, filename, content_type, data):
    boundary, body = multipart("file", filename, content_type, data)
    req = urllib.request.Request(f"{core}/api/objects/evidence/files/file", data=body, method="POST", headers={
        "Authorization": f"Bearer {token}", "Content-Type": f"multipart/form-data; boundary={boundary}"})
    with urllib.request.urlopen(req, timeout=60) as resp:
        return json.loads(resp.read())


def create(core, token, body, key):
    req = urllib.request.Request(f"{core}/api/sgspe/evidence", data=json.dumps(body).encode(), method="POST", headers={
        "Authorization": f"Bearer {token}", "Content-Type": "application/json",
        "X-Change-Reason": "UTF-8''" + urllib.parse.quote(REASON),
        "Idempotency-Key": key})
    with urllib.request.urlopen(req, timeout=60) as resp:
        return json.loads(resp.read())


def main():
    ap = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    ap.add_argument("--core", default=os.environ.get("WASICHAI_CORE", "http://localhost:8094"))
    args = ap.parse_args()
    password = os.environ.get("SGSPE_DEMO_PASSWORD")
    if not password:
        print("falta SGSPE_DEMO_PASSWORD", file=sys.stderr)
        return 2
    clients = {}

    def session(email):
        if email not in clients:
            c = Client(args.core)
            clients[email] = (c, c.login(email, password))
        return clients[email]

    try:
        client, _ = session(USER)
        projects = client.list_all("project", code="DEMO-T1")
        if not projects:
            raise CoreError(404, "el proyecto DEMO-T1 no existe: cargue antes model/demo")
        project_id = projects[0]["id"]
        created = skipped = 0
        for ev in EVIDENCES:
            # cada evidencia la sube quien puede editar el registro al que se vincula (permiso real del rol)
            uploader, token = session(ev.get("as", USER))
            if client.list_all("evidence", title=ev["title"]):
                skipped += 1
                continue
            obj, code = ev["link"]
            target = uploader.list_all(obj, code=code)
            if not target:
                raise CoreError(404, f"{obj} {code} no existe: cargue antes el conjunto ampliado")
            name, ctype, make = ev["file"]
            data = make()
            staged = stage(args.core, token, name, ctype, data)
            body = {"attributes": {**ev["attributes"], "title": ev["title"], "file": staged["id"],
                                   "project": project_id},
                    "links": [{"object": obj, "recordId": target[0]["id"]}]}
            key = "demo-evidence-" + hashlib.sha256(ev["title"].encode()).hexdigest()[:12]
            create(args.core, token, body, key)
            created += 1
            print(f"evidencia: {ev['title']} → {obj} {code}")
    except (CoreError, urllib.error.HTTPError) as e:
        detail = e.read().decode() if isinstance(e, urllib.error.HTTPError) else str(e)
        print(f"error: {detail}", file=sys.stderr)
        return 1
    print(f"done: {created} created, {skipped} skipped")
    return 0


if __name__ == "__main__":
    sys.exit(main())
