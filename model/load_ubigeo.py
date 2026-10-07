#!/usr/bin/env python3
"""Carga el ubigeo del INEI en administrative_unit (RF-TER-001, ASM-016): departamentos, provincias y distritos con su
jerarquía en parent_unit.

Solo biblioteca estándar. Lee model/data/ubigeo.csv (columnas codigo, departamento, provincia y distrito, una fila por
distrito; copiado de srtm-backend, model/data/ubigeo.csv) y deriva de cada código de seis dígitos el departamento
(<2 dígitos>0000) y la provincia (<4 dígitos>00). Una fila cuyo código no tiene seis dígitos se informa y se salta.

Es idempotente (security.md §4.1, paso 5): una unidad que core no tiene se crea, una cuyo nombre, nivel o unidad
superior cambió se reemplaza con un PUT de sus cuatro atributos (el límite, una geometría, no se toca) y las demás se
saltan. Nunca borra. Corre después de apply.py, con el administrador del tenant.

Correr: python3 load_ubigeo.py [--csv C] [--dry-run] [--core URL] [--email E] [--password P]
Salida: 0 si va bien, 1 si core rechaza algo o no responde, 2 si el CSV falta o no tiene las columnas.
"""
import argparse
import csv
import os
import re
import sys

from core_client import Client, CoreError

HERE = os.path.dirname(os.path.abspath(__file__))
OBJECT = "administrative_unit"
COLUMNS = ("codigo", "departamento", "provincia", "distrito")
CODE = re.compile(r"^\d{6}$")
LEVELS = ("DEPARTAMENTO", "PROVINCIA", "DISTRITO")


class CsvError(Exception):
    pass


def read_units(path):
    """Las unidades del CSV, departamentos primero, después provincias y después distritos, cada nivel por código, y las
    filas saltadas como (número de línea, código). Una unidad es {ubigeo_code, name, level, parent}."""
    try:
        with open(path, encoding="utf-8", newline="") as f:
            reader = csv.DictReader(f)
            if reader.fieldnames is None or not set(COLUMNS) <= set(reader.fieldnames):
                raise CsvError(f"{path}: needs the columns {', '.join(COLUMNS)}")
            rows = list(enumerate(reader, start=2))
    except OSError as e:
        raise CsvError(f"{path}: {e.strerror or e}") from e
    units, skipped = {}, []
    for line, row in rows:
        code = (row["codigo"] or "").strip()
        if not CODE.fullmatch(code):
            skipped.append((line, code))
            continue
        department, province = code[:2] + "0000", code[:4] + "00"
        units.setdefault(department, {"ubigeo_code": department, "name": row["departamento"].strip(),
                                      "level": "DEPARTAMENTO", "parent": None})
        units.setdefault(province, {"ubigeo_code": province, "name": row["provincia"].strip(),
                                    "level": "PROVINCIA", "parent": department})
        units[code] = {"ubigeo_code": code, "name": row["distrito"].strip(), "level": "DISTRITO", "parent": province}
    ordered = sorted(units.values(), key=lambda u: (LEVELS.index(u["level"]), u["ubigeo_code"]))
    return ordered, skipped


def load(client, units):
    """Crea, reemplaza o salta cada unidad en orden, para que la superior ya tenga id. Devuelve (creadas,
    actualizadas, saltadas)."""
    existing = {r["attributes"]["ubigeo_code"]: r for r in client.list_all(OBJECT)}
    ids = {code: record["id"] for code, record in existing.items()}
    created = updated = skipped = 0
    for unit in units:
        attributes = {"ubigeo_code": unit["ubigeo_code"], "name": unit["name"], "level": unit["level"],
                      "parent_unit": ids.get(unit["parent"]) if unit["parent"] else None}
        current = existing.get(unit["ubigeo_code"])
        if current is None:
            _, body = client.post(f"/api/objects/{OBJECT}/records", {"attributes": attributes})
            ids[unit["ubigeo_code"]] = body["id"]
            created += 1
        elif any(current["attributes"].get(k) != v for k, v in attributes.items()):
            client.put(f"/api/objects/{OBJECT}/records/{current['id']}", {"attributes": attributes})
            print(f"update {unit['level'].lower()} {unit['ubigeo_code']} {unit['name']}")
            updated += 1
        else:
            skipped += 1
    return created, updated, skipped


def _parse_args(argv):
    p = argparse.ArgumentParser(description="Carga el ubigeo del INEI en administrative_unit.")
    p.add_argument("--csv", default=os.path.join(HERE, "data", "ubigeo.csv"))
    p.add_argument("--core", default=os.environ.get("WASICHAI_CORE", "http://localhost:8094"))
    p.add_argument("--email", default=os.environ.get("WASICHAI_EMAIL", "admin@wasichai.local"))
    p.add_argument("--password", default=os.environ.get("WASICHAI_PASSWORD", "admin"))
    p.add_argument("--dry-run", action="store_true", help="cuenta lo que leería del CSV; no llama a core")
    return p.parse_args(argv)


def main(argv=None):
    args = _parse_args(sys.argv[1:] if argv is None else argv)
    try:
        units, skipped_rows = read_units(args.csv)
    except CsvError as e:
        print(f"error: {e}", file=sys.stderr)
        return 2
    for line, code in skipped_rows:
        print(f"skip   row {line}: code {code!r} is not a six-digit ubigeo", file=sys.stderr)
    counts = {level: sum(1 for u in units if u["level"] == level) for level in LEVELS}
    print(f"ubigeo: {counts['DEPARTAMENTO']} departamentos, {counts['PROVINCIA']} provincias, "
          f"{counts['DISTRITO']} distritos")
    if args.dry_run:
        return 0
    client = Client(args.core)
    try:
        client.login(args.email, args.password)
        created, updated, skipped = load(client, units)
    except CoreError as e:
        print(f"error -> {e.status}\n{e.body}", file=sys.stderr)
        return 1
    print(f"done: {created} created, {updated} updated, {skipped} skipped")
    return 0


if __name__ == "__main__":
    sys.exit(main())
