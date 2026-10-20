#!/usr/bin/env python3
"""Fija la contraseña de los usuarios ficticios (@sgspe.test) de una base de demostración restaurada.

El respaldo guarda solo el hash de la contraseña que eligió quien lo generó, y esa contraseña nunca se escribe en un
archivo. Tras restaurar, este script inicia sesión con el administrador de desarrollo (admin@wasichai.local, que existe
porque la base se generó con WASICHAI_SEED_DEV=true) y pone a cada usuario @sgspe.test la contraseña de
SGSPE_DEMO_PASSWORD, con PUT /api/users/{id}. Solo toca correos @sgspe.test. Solo biblioteca estándar.

  SGSPE_DEMO_PASSWORD='…' python3 docs/manuals/scripts/reset-demo-passwords.py [--core URL]

Variables opcionales: SGSPE_ADMIN_EMAIL y SGSPE_ADMIN_PASSWORD (admin@wasichai.local / admin).
Salida: 0 si va bien, 1 si el servidor rechaza algo, 2 si falta la contraseña.
"""
import argparse
import os
import sys

sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "..", "model"))
from core_client import Client, CoreError  # noqa: E402

DOMAIN = "@sgspe.test"


def main():
    ap = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    ap.add_argument("--core", default=os.environ.get("WASICHAI_CORE", "http://localhost:8094"))
    args = ap.parse_args()
    password = os.environ.get("SGSPE_DEMO_PASSWORD")
    if not password:
        print("falta SGSPE_DEMO_PASSWORD", file=sys.stderr)
        return 2
    admin = Client(args.core)
    try:
        admin.login(os.environ.get("SGSPE_ADMIN_EMAIL", "admin@wasichai.local"), os.environ.get("SGSPE_ADMIN_PASSWORD", "admin"))
        users = [u for u in admin.get("/api/users") if str(u.get("email", "")).endswith(DOMAIN)]
        for user in users:
            admin.put(f"/api/users/{user['id']}", {"password": password})
            Client(args.core).login(user["email"], password)  # comprueba que la contraseña nueva entra
            print(f"contraseña fijada: {user['email']}")
    except CoreError as e:
        print(f"error: {e}", file=sys.stderr)
        return 1
    print(f"done: {len(users)} usuarios {DOMAIN}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
