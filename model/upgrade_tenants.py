#!/usr/bin/env python3
"""Sube de fase cada tenant de un inventario: repite en cada uno los pasos 2 a 6 de security.md §4.1 (ASM-109).

wasichai no lista organizaciones, así que el inventario es del operador: un JSON que no se versiona, con la forma de
tenants.example.json ({"tenants": [{"slug": ..., "adminEmail": ...}]}). La contraseña del administrador de cada tenant llega
por la variable SGSPE_TENANT_PASSWORD_<SLUG> (el slug en mayúsculas, con guiones como guiones bajos) o por consola; nunca por
argumento ni archivo, y no se imprime. Un tenant que falla se informa y no detiene a los demás.

Correr: python3 upgrade_tenants.py --inventory RUTA [--core URL] [--skip-layers]
Salida: 0 si todos terminaron; 1 si alguno falló (el resumen dice qué paso en cuál); 2 si el inventario no es válido.
"""
import argparse
import getpass
import json
import os
import re
import sys

import tenant_steps

SLUG = re.compile(r"^[a-z0-9][a-z0-9-]{0,62}$")


def _parse_args(argv):
    p = argparse.ArgumentParser(description="Sube de fase cada tenant de un inventario.")
    p.add_argument("--inventory", required=True)
    p.add_argument("--core", default=os.environ.get("WASICHAI_CORE", "http://localhost:8094"))
    p.add_argument("--skip-layers", action="store_true", help="sin publish_layers.py: un despliegue sin GeoServer")
    return p.parse_args(argv)


def load_inventory(path):
    """Los tenants del inventario como (slug, correo), o una lista de errores."""
    try:
        with open(path, encoding="utf-8") as f:
            data = json.load(f)
    except (OSError, ValueError) as e:
        return None, [f"{path}: {e}"]
    tenants = data.get("tenants") if isinstance(data, dict) else None
    if not isinstance(tenants, list) or not tenants:
        return None, [f"{path}: debe tener una lista 'tenants' no vacía"]
    errors, result = [], []
    for i, tenant in enumerate(tenants):
        slug = tenant.get("slug") if isinstance(tenant, dict) else None
        email = tenant.get("adminEmail") if isinstance(tenant, dict) else None
        if not isinstance(slug, str) or not SLUG.fullmatch(slug):
            errors.append(f"tenants[{i}]: slug inválido")
        if not isinstance(email, str) or "@" not in email:
            errors.append(f"tenants[{i}]: adminEmail inválido")
        result.append((slug, email))
    return (None, errors) if errors else (result, [])


def password_variable(slug):
    return "SGSPE_TENANT_PASSWORD_" + slug.upper().replace("-", "_")


def main(argv=None, steps=tenant_steps.STEPS, ask=getpass.getpass):
    try:
        args = _parse_args(sys.argv[1:] if argv is None else argv)
    except SystemExit:
        return 2
    tenants, errors = load_inventory(args.inventory)
    if errors:
        for e in errors:
            print(f"error: {e}", file=sys.stderr)
        return 2
    steps = tenant_steps.selected(steps, args.skip_layers)
    failures = []
    for slug, email in tenants:
        password = os.environ.get(password_variable(slug)) or ask(f"Contraseña de {email} ({slug}): ")
        failed = "login" if not password else tenant_steps.run_steps(args.core, email, password, steps)
        if failed:
            failures.append((slug, failed))
            print(f"error: el tenant {slug} falló en {failed}; se sigue con los demás", file=sys.stderr)
    print(f"resumen: {len(tenants) - len(failures)} de {len(tenants)} tenants al día")
    for slug, step in failures:
        print(f"  {slug}: falló {step}")
    return 1 if failures else 0


if __name__ == "__main__":
    sys.exit(main())
