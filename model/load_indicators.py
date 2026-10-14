#!/usr/bin/env python3
"""Carga el catálogo de indicadores en indicator (RQ-DSH-037, ASM-136; dashboards.md §10).

Solo biblioteca estándar, con el patrón de load_ubigeo.py. Lee model/indicators.json ({"indicators": [...]}, un elemento
por KPI de dashboards.md §4 con code, name, definition, formula, disaggregation, verification_means, source_reference,
frequency y target_value) y, por código:

- un indicador que core no tiene se crea con todos sus campos, también la meta y la frecuencia iniciales de DOC-01;
- uno que ya existe se actualiza solo si cambió un texto de documentación (name, definition, formula, disaggregation,
  verification_means, source_reference), con un PUT que repite lo guardado de target_value y frequency: la meta y la
  frecuencia son del cliente desde que se cargan, y el script nunca las sobrescribe ni las borra;
- los demás se saltan. Nunca borra un indicador ni toca indicator_measurement.

Corre después de load_ubigeo.py (security.md §4.1), con el administrador del tenant.

Correr: python3 load_indicators.py [--file F] [--dry-run | --validate-only] [--core URL] [--email E] [--password P]
Salida: 0 si va bien, 1 si core rechaza algo o no responde, 2 si el archivo falta o no es válido.
"""
import argparse
import json
import os
import re
import sys

from core_client import Client, CoreError

HERE = os.path.dirname(os.path.abspath(__file__))
OBJECT = "indicator"
CODE = re.compile(r"^KPI-[A-Z]{3}-\d{2}$")
DOCUMENTATION = ("name", "definition", "formula", "disaggregation", "verification_means", "source_reference")
# del cliente desde que se cargan (ASM-136): solo van en el alta
OWNED = ("target_value", "frequency")
KEYS = {"code", *DOCUMENTATION, *OWNED}


class CatalogError(Exception):
    pass


def read_catalog(path):
    """Los indicadores del archivo en su orden, validados: código KPI-XXX-NN único, nombre, textos o null, una meta
    numérica no negativa o null y una frecuencia de texto o null. Lanza CatalogError con todos los problemas."""
    try:
        with open(path, encoding="utf-8") as f:
            data = json.load(f)
    except (OSError, ValueError) as e:
        raise CatalogError(f"{path}: {e}") from e
    items = data.get("indicators") if isinstance(data, dict) else None
    if not isinstance(items, list) or not items:
        raise CatalogError(f"{path}: needs a non-empty 'indicators' list")
    errors, seen = [], set()
    for i, item in enumerate(items):
        where = f"indicators[{i}]"
        if not isinstance(item, dict):
            errors.append(f"{where}: must be an object")
            continue
        unknown = sorted(set(item) - KEYS)
        if unknown:
            errors.append(f"{where}: unknown keys {unknown}")
        code = item.get("code")
        if not isinstance(code, str) or not CODE.fullmatch(code):
            errors.append(f"{where}: code must look like KPI-XXX-NN")
        elif code in seen:
            errors.append(f"{where}: code {code} is repeated")
        else:
            seen.add(code)
        if not isinstance(item.get("name"), str) or not item["name"].strip():
            errors.append(f"{where}: name is required")
        for key in (*DOCUMENTATION[1:], "frequency"):
            if item.get(key) is not None and not isinstance(item[key], str):
                errors.append(f"{where}: {key} must be text or null")
        target = item.get("target_value")
        if target is not None and (isinstance(target, bool) or not isinstance(target, (int, float)) or target < 0):
            errors.append(f"{where}: target_value must be a number >= 0 or null")
    if errors:
        raise CatalogError("; ".join(errors))
    return items


def attributes_of(item):
    """Lo que se manda en el alta: todos los campos, sin los null."""
    return {k: item.get(k) for k in ("code", *DOCUMENTATION, *OWNED) if item.get(k) is not None}


def load(client, items):
    """Crea, actualiza o salta cada indicador. Devuelve (creados, actualizados, saltados)."""
    existing = {r["attributes"].get("code"): r for r in client.list_all(OBJECT)}
    created = updated = skipped = 0
    for item in items:
        current = existing.get(item["code"])
        if current is None:
            client.post(f"/api/objects/{OBJECT}/records", {"attributes": attributes_of(item)})
            print(f"create {item['code']}")
            created += 1
            continue
        stored = current["attributes"]
        if all(stored.get(k) == item.get(k) for k in DOCUMENTATION):
            skipped += 1
            continue
        # el PUT de wasichai reemplaza cada campo: se repite lo guardado de la meta y la frecuencia, tal cual
        attributes = {"code": item["code"], **{k: item.get(k) for k in DOCUMENTATION}, **{k: stored.get(k) for k in OWNED}}
        client.put(f"/api/objects/{OBJECT}/records/{current['id']}", {"attributes": attributes})
        print(f"update {item['code']} (documentation)")
        updated += 1
    return created, updated, skipped


def _parse_args(argv):
    p = argparse.ArgumentParser(description="Carga el catálogo de indicadores en indicator.")
    p.add_argument("--file", default=os.path.join(HERE, "indicators.json"))
    p.add_argument("--core", default=os.environ.get("WASICHAI_CORE", "http://localhost:8094"))
    p.add_argument("--email", default=os.environ.get("WASICHAI_EMAIL", "admin@wasichai.local"))
    p.add_argument("--password", default=os.environ.get("WASICHAI_PASSWORD", "admin"))
    mode = p.add_mutually_exclusive_group()
    mode.add_argument("--dry-run", action="store_true", help="cuenta lo que cargaría; no llama a core")
    mode.add_argument("--validate-only", action="store_true", help="solo valida el archivo; no llama a core")
    return p.parse_args(argv)


def main(argv=None):
    args = _parse_args(sys.argv[1:] if argv is None else argv)
    try:
        items = read_catalog(args.file)
    except CatalogError as e:
        print(f"error: {e}", file=sys.stderr)
        return 2
    if args.validate_only:
        return 0
    targets = sum(1 for i in items if i.get("target_value") is not None)
    print(f"indicators: {len(items)} ({targets} with a target)")
    if args.dry_run:
        return 0
    client = Client(args.core)
    try:
        client.login(args.email, args.password)
        created, updated, skipped = load(client, items)
    except CoreError as e:
        print(f"error -> {e.status}\n{e.body}", file=sys.stderr)
        return 1
    print(f"done: {created} created, {updated} updated, {skipped} skipped")
    return 0


if __name__ == "__main__":
    sys.exit(main())
