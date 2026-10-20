#!/usr/bin/env python3
"""Manifiesto y verificación de la base de demostración de SGSPE (solo biblioteca estándar; consulta con psql).

  demo_manifest.py write  --db BASE --artifacts A [A …] --files-dir DIR --out MANIFIESTO
      describe la base: versiones, historia de migraciones, filas por tabla, secuencias, restricciones, geometrías,
      resumen del dominio y la suma SHA-256 de cada artefacto.

  demo_manifest.py verify --db BASE --manifest MANIFIESTO [--files-dir DIR] [--api URL --email CORREO]
                         [--allow-growth wasichai.audit_log …]
      compara una base (normalmente la restaurada) con el manifiesto: filas por tabla, secuencias, restricciones
      validadas, claves foráneas sin huérfanos, geometrías inválidas y migraciones. Con --api, además inicia sesión
      con un usuario ficticio (contraseña en SGSPE_DEMO_PASSWORD) y lee el proyecto y los grupos de interés por la API.

  demo_manifest.py checksums --manifest MANIFIESTO --dir DIR
      comprueba la suma SHA-256 de los artefactos antes de restaurarlos.

La conexión sale de las variables estándar de libpq (PGHOST, PGPORT, PGUSER, PGPASSWORD), que fija demo-env.sh.
Salida: 0 si todo coincide, 1 si algo difiere, 2 si faltan datos o herramientas.
"""
import argparse
import datetime
import hashlib
import json
import os
import re
import subprocess
import sys
import urllib.error
import urllib.request

HERE = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.dirname(os.path.dirname(os.path.dirname(HERE)))
SYSTEM_SCHEMAS = ("pg_catalog", "information_schema", "pg_toast", "topology", "tiger", "tiger_data")
DEMO_USER = "gestion.social.demo@sgspe.test"


def psql(db, sql):
    """Corre una consulta y devuelve la primera columna de la primera fila como texto."""
    out = subprocess.run(["psql", "-X", "-q", "-tA", "-v", "ON_ERROR_STOP=1", "-d", db, "-c", sql],
                         capture_output=True, text=True)
    if out.returncode != 0:
        raise SystemExit(f"psql falló en {db}: {out.stderr.strip()}")
    return out.stdout.strip()


def psql_json(db, sql):
    text = psql(db, f"SELECT coalesce(json_agg(t), '[]'::json) FROM ({sql}) t")
    return json.loads(text or "[]")


def ident(name):
    return '"' + name.replace('"', '""') + '"'


def sha256(path):
    h = hashlib.sha256()
    with open(path, "rb") as f:
        for chunk in iter(lambda: f.read(1 << 20), b""):
            h.update(chunk)
    return h.hexdigest()


def git(*args):
    try:
        return subprocess.run(["git", "-C", REPO, *args], capture_output=True, text=True, check=True).stdout.strip()
    except (OSError, subprocess.CalledProcessError):
        return None


def read_versions():
    app = wasichai = None
    try:
        app = re.search(r"^version=(.+)$", open(os.path.join(REPO, "gradle.properties")).read(), re.M).group(1)
        wasichai = re.search(r'wasichaiVersion = "([^"]+)"', open(os.path.join(REPO, "build.gradle.kts")).read()).group(1)
    except (OSError, AttributeError):
        pass
    return app, wasichai


def tables(db):
    rows = psql_json(db, f"""
        SELECT n.nspname AS schema, c.relname AS name FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace
        WHERE c.relkind IN ('r', 'p') AND n.nspname NOT IN {SYSTEM_SCHEMAS} ORDER BY 1, 2""")
    counts = {}
    for r in rows:
        counts[f"{r['schema']}.{r['name']}"] = int(psql(db, f"SELECT count(*) FROM {ident(r['schema'])}.{ident(r['name'])}"))
    return counts


def sequences(db):
    rows = psql_json(db, f"""
        SELECT schemaname AS schema, sequencename AS name, last_value FROM pg_sequences
        WHERE schemaname NOT IN {SYSTEM_SCHEMAS} ORDER BY 1, 2""")
    return {f"{r['schema']}.{r['name']}": r["last_value"] for r in rows}


def sequence_owners(db):
    """Columnas cuyo valor por defecto sale de una secuencia: (secuencia, tabla, columna)."""
    return psql_json(db, f"""
        SELECT s.schemaname || '.' || s.sequencename AS seq, tn.nspname AS tschema, t.relname AS tname, a.attname AS col
        FROM pg_sequences s
        JOIN pg_class sc ON sc.relname = s.sequencename
        JOIN pg_namespace sn ON sn.oid = sc.relnamespace AND sn.nspname = s.schemaname
        JOIN pg_depend d ON d.objid = sc.oid AND d.deptype IN ('a', 'i')
        JOIN pg_class t ON t.oid = d.refobjid
        JOIN pg_namespace tn ON tn.oid = t.relnamespace
        JOIN pg_attribute a ON a.attrelid = t.oid AND a.attnum = d.refobjsubid
        WHERE s.schemaname NOT IN {SYSTEM_SCHEMAS}""")


def constraints(db):
    rows = psql_json(db, f"""
        SELECT c.contype AS type, count(*) AS n, sum(CASE WHEN c.convalidated THEN 0 ELSE 1 END) AS not_validated
        FROM pg_constraint c JOIN pg_namespace n ON n.oid = c.connamespace
        WHERE n.nspname NOT IN {SYSTEM_SCHEMAS} GROUP BY 1 ORDER BY 1""")
    names = {"p": "primary_key", "f": "foreign_key", "u": "unique", "c": "check", "x": "exclusion", "t": "trigger",
             "n": "not_null"}
    return {names.get(r["type"], r["type"]): {"count": r["n"], "not_validated": r["not_validated"]} for r in rows}


def not_validated(db):
    """Las restricciones creadas NOT VALID a propósito (p. ej. las de audit_log de la migración V10 de wasichai)."""
    rows = psql_json(db, f"""
        SELECT n.nspname || '.' || c.conrelid::regclass::text || '.' || c.conname AS name
        FROM pg_constraint c JOIN pg_namespace n ON n.oid = c.connamespace
        WHERE NOT c.convalidated AND n.nspname NOT IN {SYSTEM_SCHEMAS} ORDER BY 1""")
    return [r["name"] for r in rows]


def foreign_key_orphans(db):
    """Cuenta, por cada clave foránea, las filas que apuntan a una fila inexistente (debería ser 0 en todas)."""
    fks = psql_json(db, f"""
        SELECT c.conname, sn.nspname AS sschema, s.relname AS stable, tn.nspname AS tschema, t.relname AS ttable,
               (SELECT json_agg(a.attname ORDER BY k.ord) FROM unnest(c.conkey) WITH ORDINALITY k(attnum, ord)
                  JOIN pg_attribute a ON a.attrelid = c.conrelid AND a.attnum = k.attnum) AS scols,
               (SELECT json_agg(a.attname ORDER BY k.ord) FROM unnest(c.confkey) WITH ORDINALITY k(attnum, ord)
                  JOIN pg_attribute a ON a.attrelid = c.confrelid AND a.attnum = k.attnum) AS tcols
        FROM pg_constraint c
        JOIN pg_class s ON s.oid = c.conrelid JOIN pg_namespace sn ON sn.oid = s.relnamespace
        JOIN pg_class t ON t.oid = c.confrelid JOIN pg_namespace tn ON tn.oid = t.relnamespace
        WHERE c.contype = 'f' AND sn.nspname NOT IN {SYSTEM_SCHEMAS}""")
    orphans = {}
    for fk in fks:
        notnull = " AND ".join(f"s.{ident(c)} IS NOT NULL" for c in fk["scols"])
        join = " AND ".join(f"t.{ident(tc)} = s.{ident(sc)}" for sc, tc in zip(fk["scols"], fk["tcols"]))
        n = int(psql(db, f"""SELECT count(*) FROM {ident(fk['sschema'])}.{ident(fk['stable'])} s WHERE {notnull}
                             AND NOT EXISTS (SELECT 1 FROM {ident(fk['tschema'])}.{ident(fk['ttable'])} t WHERE {join})"""))
        if n:
            orphans[f"{fk['sschema']}.{fk['stable']}.{fk['conname']}"] = n
    return len(fks), orphans


def geometries(db):
    cols = psql_json(db, """SELECT f_table_schema AS schema, f_table_name AS name, f_geometry_column AS col
                            FROM geometry_columns ORDER BY 1, 2, 3""")
    out = {}
    for c in cols:
        q = (f"SELECT count({ident(c['col'])}) || ',' || count(*) FILTER (WHERE {ident(c['col'])} IS NOT NULL "
             f"AND NOT ST_IsValid({ident(c['col'])})) FROM {ident(c['schema'])}.{ident(c['name'])}")
        present, invalid = psql(db, q).split(",")
        out[f"{c['schema']}.{c['name']}.{c['col']}"] = {"non_null": int(present), "invalid": int(invalid)}
    return out


def migrations(db):
    hist = psql_json(db, """SELECT table_schema AS schema, table_name AS name FROM information_schema.tables
                            WHERE table_name LIKE 'flyway\\_history\\_%' ORDER BY 2""")
    out = {}
    for h in hist:
        row = psql_json(db, f"""SELECT max(version) AS version, count(*) AS applied,
                                       sum(CASE WHEN success THEN 0 ELSE 1 END) AS failed
                                FROM {ident(h['schema'])}.{ident(h['name'])} WHERE version IS NOT NULL""")[0]
        out[h["name"].removeprefix("flyway_history_")] = row
    return out


def domain_summary(db):
    """Resumen legible del contenido: organizaciones, usuarios y registros por objeto del modelo."""
    summary = {}
    try:
        summary["organizations"] = psql_json(db, "SELECT name, slug FROM wasichai.organizations ORDER BY name")
        summary["users"] = int(psql(db, "SELECT count(*) FROM wasichai.users"))
        objs = psql_json(db, """SELECT o.name, o.physical_table AS table_name FROM wasichai.custom_objects o ORDER BY o.name""")
        per_object = {}
        for o in objs:
            per_object[o["name"]] = per_object.get(o["name"], 0) + int(
                psql(db, f"SELECT count(*) FROM app_data.{ident(o['table_name'])}"))
        summary["records_by_object"] = per_object
        summary["records_total"] = sum(per_object.values())
    except SystemExit as e:  # el esquema de wasichai cambió: el resumen es informativo, no bloquea
        summary["error"] = str(e)
    return summary


def describe(db):
    return {
        "server_version": psql(db, "SHOW server_version"),
        "postgis_version": psql(db, "SELECT postgis_lib_version()"),
        "tables": tables(db),
        "sequences": sequences(db),
        "constraints": constraints(db),
        "constraints_not_validated": not_validated(db),
        "geometries": geometries(db),
        "migrations": migrations(db),
        "domain": domain_summary(db),
    }


def files_summary(path):
    count = size = 0
    for root, _, names in os.walk(path or ""):
        for n in names:
            count += 1
            size += os.path.getsize(os.path.join(root, n))
    return {"count": count, "bytes": size}


def cmd_write(args):
    app, wasichai = read_versions()
    facts = describe(args.db)
    dump_version = subprocess.run(["pg_dump", "--version"], capture_output=True, text=True).stdout.strip()
    manifest = {
        "name": "sgspe-demo",
        "generated_at": datetime.datetime.now(datetime.timezone.utc).replace(microsecond=0).isoformat(),
        "source_database": args.db,
        "application": {"sgspe": app, "wasichai": wasichai, "git_commit": git("rev-parse", "HEAD"),
                        "git_dirty": bool(git("status", "--porcelain", "--", "src", "model"))},
        "database": {"server_version": facts["server_version"], "postgis_version": facts["postgis_version"],
                     "pg_dump": dump_version, "encoding": "UTF8", "timezone_note": "timestamptz en UTC; la app usa America/Lima"},
        "dataset": {"source": "model/demo/*.json cargado con model/load_demo.py",
                    "users_password": "SGSPE_DEMO_PASSWORD (nunca en un archivo)",
                    "demo_users": ["gestion.social.demo@sgspe.test", "actores.demo@sgspe.test", "riesgos.demo@sgspe.test",
                                   "campo.demo@sgspe.test", "compromisos.demo@sgspe.test",
                                   "relacionista.demo@sgspe.test"],
                    "admin": "admin@wasichai.local (solo con WASICHAI_SEED_DEV=true)"},
        "artifacts": [{"file": os.path.basename(p), "bytes": os.path.getsize(p), "sha256": sha256(p)} for p in args.artifacts],
        "files_store": files_summary(args.files_dir),
        "restore_order": [
            "1. crear una base vacía sgspe_demo_* (UTF8, template0) en un PostgreSQL con PostGIS disponible",
            "2. psql --single-transaction -f <(gunzip -c sgspe-demo.sql.gz)  — o pg_restore --exit-on-error -d BASE sgspe-demo.dump",
            "3. extraer sgspe-demo-files.tar.gz en WASICHAI_FILES_LOCAL_PATH del servidor",
            "4. demo_manifest.py verify contra este manifiesto",
            "5. arrancar el servidor con WASICHAI_DB_NAME=BASE y WASICHAI_FILES_LOCAL_PATH=… (no hace falta aprovisionar)",
        ],
        **{k: facts[k] for k in ("migrations", "constraints", "constraints_not_validated", "sequences", "geometries",
                                         "domain", "tables")},
    }
    with open(args.out, "w", encoding="utf-8") as f:
        json.dump(manifest, f, ensure_ascii=False, indent=2)
        f.write("\n")
    print(f"manifiesto: {len(manifest['tables'])} tablas, {sum(manifest['tables'].values())} filas, "
          f"{len(manifest['sequences'])} secuencias, {manifest['files_store']['count']} archivos", file=sys.stderr)
    return 0


def check(results, ok, label, detail=""):
    results.append((ok, label, detail))


def smoke_api(results, api, email):
    password = os.environ.get("SGSPE_DEMO_PASSWORD")
    if not password:
        check(results, False, "API: falta SGSPE_DEMO_PASSWORD para iniciar sesión")
        return

    def call(method, path, body=None, token=None):
        req = urllib.request.Request(api.rstrip("/") + path, method=method,
                                     data=json.dumps(body).encode() if body is not None else None)
        req.add_header("Content-Type", "application/json")
        if token:
            req.add_header("Authorization", f"Bearer {token}")
        with urllib.request.urlopen(req, timeout=30) as resp:
            return json.loads(resp.read() or b"null")

    try:
        health = call("GET", "/api/health")
        check(results, health.get("status") == "UP", "API: /api/health responde UP", str(health))
        token = call("POST", "/api/auth/login", {"email": email, "password": password})["token"]
        check(results, True, f"API: inicio de sesión de {email}")
        projects = call("GET", "/api/objects/project/records?page=0&size=50", token=token)
        codes = [p["attributes"].get("code") for p in projects["content"]]
        check(results, "DEMO-T1" in codes, "API: el proyecto DEMO-T1 se lee por la API", f"códigos: {codes}")
        stk = call("GET", "/api/objects/stakeholder/records?page=0&size=1", token=token)
        total = stk.get("totalElements")
        check(results, bool(total), f"API: hay grupos de interés legibles ({total})", f"total: {total}")
    except (urllib.error.URLError, KeyError, ValueError, AttributeError) as e:
        check(results, False, "API: la prueba de humo falló", str(e))


def cmd_verify(args):
    manifest = json.load(open(args.manifest, encoding="utf-8"))
    db = args.db
    results = []
    got = tables(db)
    want = manifest["tables"]
    grow = set(args.allow_growth or [])
    diff = {t: (want.get(t), got.get(t)) for t in sorted(set(want) | set(got))
            if want.get(t) != got.get(t) and not (t in grow and (got.get(t) or 0) >= (want.get(t) or 0))}
    check(results, not diff, f"filas por tabla ({len(want)} tablas, {sum(want.values())} filas)",
          "; ".join(f"{t}: esperado {a}, hay {b}" for t, a, b in ((t, *v) for t, v in diff.items()))[:2000])
    seqs = sequences(db)
    sdiff = {s: (v, seqs.get(s)) for s, v in manifest["sequences"].items() if seqs.get(s) != v}
    check(results, not sdiff, f"valor de las secuencias ({len(manifest['sequences'])})", str(sdiff)[:1000])
    behind = []
    for o in sequence_owners(db):
        mx = psql(db, f"SELECT coalesce(max({ident(o['col'])})::text, '') FROM {ident(o['tschema'])}.{ident(o['tname'])}")
        last = seqs.get(o["seq"])
        if mx and (last is None or int(last) < int(mx)):
            behind.append(f"{o['seq']} ({last}) < max({o['tname']}.{o['col']}) ({mx})")
    check(results, not behind, "ninguna secuencia queda por detrás de su columna", "; ".join(behind))
    cons = constraints(db)
    check(results, cons == manifest["constraints"], "restricciones: mismas cantidades por tipo",
          f"esperado {manifest['constraints']}, hay {cons}")
    nv = not_validated(db)
    check(results, nv == manifest["constraints_not_validated"],
          f"restricciones sin validar: solo las del origen ({len(nv)}, creadas NOT VALID a propósito)", str(nv))
    nfk, orphans = foreign_key_orphans(db)
    check(results, not orphans, f"claves foráneas sin huérfanos ({nfk} revisadas)", str(orphans))
    geo = geometries(db)
    check(results, geo == manifest["geometries"], f"geometrías: mismas cantidades e inválidas ({len(geo)} columnas)",
          str({k: (manifest['geometries'].get(k), v) for k, v in geo.items() if manifest['geometries'].get(k) != v})[:1000])
    mig = migrations(db)
    check(results, mig == manifest["migrations"], f"historia de migraciones ({len(mig)} módulos)",
          str({k: (manifest['migrations'].get(k), v) for k, v in mig.items() if manifest['migrations'].get(k) != v}))
    dom = domain_summary(db)
    check(results, dom.get("records_by_object") == manifest["domain"].get("records_by_object"),
          f"registros por objeto del modelo ({dom.get('records_total')} registros)")
    if args.files_dir:
        fs = files_summary(args.files_dir)
        check(results, fs == manifest["files_store"], f"archivos restaurados ({fs['count']})",
              f"esperado {manifest['files_store']}, hay {fs}")
    if args.api:
        smoke_api(results, args.api, args.email)
    width = max(len(label) for _, label, _ in results)
    for ok, label, detail in results:
        print(f"  {'✓' if ok else '✗'} {label.ljust(width)}" + (f"  — {detail}" if detail and not ok else ""))
    failed = sum(1 for ok, _, _ in results if not ok)
    print(f"verificación de {db}: {len(results) - failed} correctas, {failed} con diferencias")
    return 1 if failed else 0


def cmd_checksums(args):
    manifest = json.load(open(args.manifest, encoding="utf-8"))
    bad = 0
    for a in manifest["artifacts"]:
        path = os.path.join(args.dir, a["file"])
        if not os.path.exists(path):
            print(f"  ✗ falta {a['file']}")
            bad += 1
        elif sha256(path) != a["sha256"]:
            print(f"  ✗ {a['file']}: la suma SHA-256 no coincide con el manifiesto")
            bad += 1
        else:
            print(f"  ✓ {a['file']} ({a['bytes']} bytes)")
    return 1 if bad else 0


def main():
    ap = argparse.ArgumentParser(description="Manifiesto y verificación de la base de demostración de SGSPE")
    sub = ap.add_subparsers(dest="cmd", required=True)
    w = sub.add_parser("write")
    w.add_argument("--db", required=True)
    w.add_argument("--artifacts", nargs="+", required=True)
    w.add_argument("--files-dir")
    w.add_argument("--out", required=True)
    v = sub.add_parser("verify")
    v.add_argument("--db", required=True)
    v.add_argument("--manifest", required=True)
    v.add_argument("--files-dir")
    v.add_argument("--api", help="URL del servidor arrancado contra la base, para la prueba de humo")
    v.add_argument("--email", default=DEMO_USER)
    v.add_argument("--allow-growth", nargs="*", metavar="TABLA",
                   help="tablas que pueden tener MÁS filas que el manifiesto (una base ya usada: wasichai.audit_log…)")
    c = sub.add_parser("checksums")
    c.add_argument("--manifest", required=True)
    c.add_argument("--dir", required=True)
    args = ap.parse_args()
    return {"write": cmd_write, "verify": cmd_verify, "checksums": cmd_checksums}[args.cmd](args)


if __name__ == "__main__":
    sys.exit(main())
