#!/usr/bin/env python3
"""Da de alta un tenant cliente de SGSPE y lo aprovisiona (security.md §4.1, ASM-109).

1. Con las credenciales del operador (una persona del rol que tiene MANAGE_TENANTS en la organización operadora, nunca una
   cuenta de servicio), llama POST /api/organizations con el nombre, el slug y el primer administrador.
2. Con las credenciales del administrador nuevo corre, en este orden, apply.py, apply_roles.py, apply.py --workflows,
   load_ubigeo.py y publish_layers.py (tenant_steps.py). publish_layers.py toma las credenciales de GeoServer del entorno
   (WASICHAI_GIS_GEOSERVER_URL, _USERNAME y _PASSWORD); un despliegue sin GeoServer lo omite con --skip-layers.
3. Termina recordando que el cliente debe cambiar la contraseña inicial de su administrador.

Las contraseñas llegan por variable de entorno (SGSPE_OPERATOR_PASSWORD y SGSPE_TENANT_ADMIN_PASSWORD) o por consola, nunca
por argumento ni archivo, y no se imprimen.

Correr: python3 provision_tenant.py --core URL --operator-email E --name N --slug S --admin-email A [--admin-name D]
        [--skip-layers]
Salida: 0 si va bien; 1 si core rechaza el alta o falla un paso (lo nombra); 2 si faltan datos.
"""
import argparse
import getpass
import os
import sys

import tenant_steps
from core_client import Client, CoreError

OPERATOR_PASSWORD = "SGSPE_OPERATOR_PASSWORD"
ADMIN_PASSWORD = "SGSPE_TENANT_ADMIN_PASSWORD"


def _parse_args(argv):
    p = argparse.ArgumentParser(description="Da de alta un tenant de SGSPE y lo aprovisiona.")
    p.add_argument("--core", default=os.environ.get("WASICHAI_CORE", "http://localhost:8094"))
    p.add_argument("--operator-email", required=True)
    p.add_argument("--name", required=True)
    p.add_argument("--slug", required=True)
    p.add_argument("--admin-email", required=True)
    p.add_argument("--admin-name", default="Administración del tenant")
    p.add_argument("--skip-layers", action="store_true", help="sin publish_layers.py: un despliegue sin GeoServer")
    return p.parse_args(argv)


def secret(variable, prompt, ask=getpass.getpass):
    """La contraseña de la variable de entorno o, si no está, la que se escribe en la consola (sin eco)."""
    value = os.environ.get(variable)
    return value if value else ask(prompt)


def provision(client, args, admin_password):
    """POST /api/organizations como el operador ya autenticado; lanza CoreError si core lo rechaza."""
    client.post("/api/organizations", {
        "name": args.name,
        "slug": args.slug,
        "adminEmail": args.admin_email,
        "adminPassword": admin_password,
        "adminDisplayName": args.admin_name,
    })


def main(argv=None, steps=tenant_steps.STEPS, ask=getpass.getpass):
    try:
        args = _parse_args(sys.argv[1:] if argv is None else argv)
    except SystemExit:
        return 2
    operator_password = secret(OPERATOR_PASSWORD, f"Contraseña de {args.operator_email}: ", ask)
    admin_password = secret(ADMIN_PASSWORD, f"Contraseña inicial de {args.admin_email}: ", ask)
    if not operator_password or not admin_password:
        print("error: faltan las contraseñas del operador o del administrador nuevo", file=sys.stderr)
        return 2
    client = Client(args.core)
    try:
        client.login(args.operator_email, operator_password)
        provision(client, args, admin_password)
    except CoreError as e:
        print(f"error: el alta del tenant {args.slug} falló: {e.status}", file=sys.stderr)
        return 1
    print(f"tenant {args.slug} creado; se aprovisiona como {args.admin_email}", flush=True)
    failed = tenant_steps.run_steps(args.core, args.admin_email, admin_password,
                                    tenant_steps.selected(steps, args.skip_layers))
    if failed:
        layers = " --skip-layers" if args.skip_layers else ""
        print(f"error: el tenant {args.slug} quedó a medias: falló {failed}; corrija y corra upgrade_tenants.py{layers}", file=sys.stderr)
        return 1
    print(f"listo: el administrador {args.admin_email} debe cambiar la contraseña inicial en su primer acceso "
          "(security.md §4.1)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
