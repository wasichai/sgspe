#!/usr/bin/env python3
"""Crea o sincroniza en un wasichai Core los roles de SGSPE que declara model/roles.json.

Copiado de caja-backend@dc9e391 (model/apply_roles.py) y extendido con las reglas de security.md §6.3:

- 15 roles declarados. SUPER_ADMIN y ORGANIZATION_ADMIN no se declaran: son el ADMIN de wasichai y van en la lista
  mappedToAdmin, que con los 15 suma los 17 roles del prompt. Declarar ADMIN, SUPER_ADMIN u ORGANIZATION_ADMIN es un error.
- Cada rol dice, por objeto del modelo, sus acciones: READ, CREATE, UPDATE, DELETE, una acción que el modelo declara para
  ese objeto, o `u`. La clave "*" es el permiso de todo el tenant (objectName null en wasichai) y solo admite READ.
- `u` no es un permiso de wasichai: se expande en UPDATE más una entrada de campo de solo lectura (read sí, write no) para
  cada campo del objeto sin "derived": true y para el campo de cada relación que sale del objeto. Un permiso de campo es
  una restricción, así que un campo nuevo nunca queda escribible por defecto: se regenera en cada fase.
- Ningún rol lleva ownRecordsOnly (ASM-069): roles.json lo rechaza, y un rol de core que lo tenga se corrige.
- Ningún rol recibe MANAGE_METADATA ni MANAGE_ORGANIZATION.

Un rol que falta se crea (POST /api/roles) y recibe sus permisos (PUT /api/roles/{name}/permissions) y, si usa `u`, sus
permisos de campo (PUT /api/roles/{name}/field-permissions). Uno que ya existe queda con lo que dice roles.json, ni uno
más ni uno menos: lo que se edite a mano en /admin se pierde en la corrida siguiente. Es idempotente: la segunda corrida
no escribe nada ("done: 0 created, 0 updated, 15 skipped"). Un rol de core que roles.json no declara se avisa en stderr
(una línea por rol) y no se borra; el ADMIN de wasichai no se avisa.

Permisos de campo en wasichai 0.3.2 (comprobado contra la app): el PUT reemplaza las entradas de cada objeto que nombra y
deja las de los demás; una lista vacía no borra nada y no hay DELETE. Quitar una restricción es mandar el campo con lectura y
escritura (entrada neutra, que la comparación ignora); borrar el objeto borra sus entradas.

Correr: python3 apply_roles.py [--dry-run] [--roles R] [--model M] [--core URL] [--email E] [--password P]
Salida: 0 si va bien, 1 si core rechaza algo o no responde, 2 si roles.json no es válido, o si roles.json o model.json
faltan o no son JSON.
"""
import argparse
import json
import os
import re
import sys

from core_client import Client, CoreError

HERE = os.path.dirname(os.path.abspath(__file__))
# las de core (wasichai.core.common.Actions) que se dan sobre un objeto
ACTIONS = ("READ", "CREATE", "UPDATE", "DELETE")
# AdminService.createRole
ROLE_NAME = re.compile(r"^[A-Z][A-Z0-9_]{1,48}$")
# el ADMIN de wasichai y los dos roles del prompt que son ese ADMIN (security.md §6.2): nunca se declaran
ADMIN_ROLES = ("ADMIN", "SUPER_ADMIN", "ORGANIZATION_ADMIN")
MAPPED_TO_ADMIN = ["SUPER_ADMIN", "ORGANIZATION_ADMIN"]
# el permiso de todo el tenant: wasichai lo guarda sin objeto (objectName null)
TENANT = "*"
# UPDATE sin campos de negocio escribibles (security.md §7.1)
LINK_ONLY = "u"


def _model_fields(model):
    """Por objeto, sus campos sin "derived": true en el orden de model.json, y después el campo de cada relación
    MANY_TO_ONE que sale de él (una MANY_TO_MANY es una tabla de unión, sin campo)."""
    fields = {}
    for obj in model.get("objects", []):
        fields[obj["name"]] = [f["name"] for f in obj.get("fields", []) if not f.get("derived", False)]
    for rel in model.get("relationships", []):
        if rel.get("fieldName"):
            fields.setdefault(rel["source"], []).append(rel["fieldName"])
    return fields


def validate(roles, model):
    """Los errores de roles.json frente a model.json; vacía si es válido."""
    errors = []
    if not isinstance(roles, dict):
        return ["roles.json must be a JSON object with mappedToAdmin and roles"]
    # las acciones que cada objeto acepta: las de core y las que el modelo declara para él
    objects = {o["name"]: ACTIONS + tuple(a["name"] for a in o.get("actions", [])) for o in model.get("objects", [])}
    mapped = roles.get("mappedToAdmin")
    if mapped != MAPPED_TO_ADMIN:
        errors.append(f"mappedToAdmin must be {MAPPED_TO_ADMIN}, not {mapped}")
    declared = roles.get("roles")
    if not isinstance(declared, list):
        errors.append("roles must be a list")
        declared = []
    seen = set()
    for role in declared:
        if not isinstance(role, dict):
            errors.append(f"role {role!r}: must be an object with name, label and permisos")
            continue
        name = role.get("name", "")
        if not isinstance(name, str):
            # una lista o un objeto no son hashables: se descarta aquí, antes de `seen`
            errors.append(f"role {name!r}: name must match {ROLE_NAME.pattern}")
            continue
        if not ROLE_NAME.fullmatch(name):
            errors.append(f"role '{name}': name must match {ROLE_NAME.pattern}")
        if name in ADMIN_ROLES:
            errors.append(f"role {name}: is wasichai's ADMIN, never declared (mappedToAdmin)")
        if name in seen:
            errors.append(f"role {name}: duplicate role name")
        seen.add(name)
        if not role.get("label"):
            errors.append(f"role {name}: label is missing")
        if "ownRecordsOnly" in role:
            errors.append(f"role {name}: ownRecordsOnly is not used (ASM-069): EDIT_ANY and EditAnyGuard decide")
        permisos = role.get("permisos")
        if not isinstance(permisos, dict):
            errors.append(f"role {name}: permisos must be an object (use {{}} for no permissions)")
            continue
        for obj, actions in permisos.items():
            if not isinstance(actions, list) or not all(isinstance(a, str) for a in actions):
                errors.append(f"role {name}: permisos of '{obj}' must be a list of action names, not {actions!r}")
                continue
            if obj == TENANT:
                if actions != ["READ"]:
                    errors.append(f"role {name}: '{TENANT}' only takes [\"READ\"], not {actions}")
                continue
            if obj not in objects:
                errors.append(f"role {name}: object '{obj}' is not in model.json")
                continue
            if LINK_ONLY in actions and "UPDATE" in actions:
                errors.append(f"role {name}: '{LINK_ONLY}' and UPDATE on {obj}: '{LINK_ONLY}' is UPDATE without editable fields")
            for action in actions:
                if action != LINK_ONLY and action not in objects[obj]:
                    errors.append(f"role {name}: action '{action}' on {obj} must be one of "
                                  f"{', '.join(objects[obj] + (LINK_ONLY,))}")
    return errors


def permissions_payload(role):
    """El cuerpo de PUT /api/roles/{name}/permissions, en el orden de roles.json: "*" va sin objeto y `u` es UPDATE."""
    return {"permissions": [{"objectName": None if obj == TENANT else obj,
                             "action": "UPDATE" if action == LINK_ONLY else action}
                            for obj, actions in role["permisos"].items() for action in actions]}


def field_permissions_payload(role, model):
    """El cuerpo de PUT /api/roles/{name}/field-permissions: solo lectura para cada campo no derivado de cada objeto en el
    que el rol tiene `u`."""
    fields = _model_fields(model)
    return {"fields": [{"objectName": obj, "fieldName": field, "read": True, "write": False}
                       for obj, actions in role["permisos"].items() if LINK_ONLY in actions
                       for field in fields.get(obj, [])]}


def _granted(permissions):
    """Lo que un rol de core concede, como pares (objeto, acción)."""
    return {(p.get("objectName"), p["action"]) for p in permissions or [] if p.get("allowed", True)}


def _wanted(role):
    return {(p["objectName"], p["action"]) for p in permissions_payload(role)["permissions"]}


def _restrictions(entries):
    """Las restricciones de campo como tuplas (objeto, campo, lectura, escritura), sin importar el orden. Una entrada
    con lectura y escritura es neutra, igual que no tener entrada: wasichai 0.3.2 no tiene cómo borrarla."""
    return {(f["objectName"], f["fieldName"], f.get("read", True), f.get("write", True)) for f in entries or []
            if not (f.get("read", True) and f.get("write", True))}


def field_permissions_sync_payload(current, wanted):
    """El cuerpo de PUT .../field-permissions que deja las restricciones del rol como `wanted`.

    wasichai 0.3.2 reemplaza las entradas de cada objeto que el cuerpo nombra y no toca las de los demás; una lista vacía
    no borra nada y no hay DELETE. Para quitar una restricción de un objeto que roles.json ya no restringe, se manda cada
    campo de ese objeto con lectura y escritura: queda una entrada neutra, que `_restrictions` ignora."""
    kept = {f["objectName"] for f in wanted["fields"]}
    neutral = [{"objectName": f["objectName"], "fieldName": f["fieldName"], "read": True, "write": True}
               for f in current or [] if f["objectName"] not in kept and not (f.get("read", True) and f.get("write", True))]
    return {"fields": wanted["fields"] + neutral}


def _fatal(method, path, err):
    print(f"error {method} {path} -> {err.status}\n{err.body}", file=sys.stderr)


def do_apply(client, roles, model):
    created = updated = skipped = 0
    try:
        existing = {r["name"]: r for r in client.get("/api/roles") or []}
    except CoreError as e:
        _fatal("GET", "/api/roles", e)
        return 1
    declared = {role["name"] for role in roles["roles"]}
    for name in existing:
        if name not in declared and name not in ADMIN_ROLES:
            print(f"warning: role {name} is in core but not in roles.json; left as is (delete it in /admin if it is a leftover)",
                  file=sys.stderr)
    for role in roles["roles"]:
        name = role["name"]
        current = existing.get(name)
        fields = field_permissions_payload(role, model)
        writes = []
        if current is None:
            writes.append(("POST", "/api/roles", {"name": name, "label": role["label"]}, f"create role {name}"))
            writes.append(("PUT", f"/api/roles/{name}/permissions", permissions_payload(role), None))
            if fields["fields"]:
                writes.append(("PUT", f"/api/roles/{name}/field-permissions", fields, None))
        else:
            # la etiqueta de roles.json y nunca ownRecordsOnly, aunque alguien lo haya activado en /admin
            if current.get("label") != role["label"] or current.get("ownRecordsOnly", False):
                writes.append(("PUT", f"/api/roles/{name}", {"label": role["label"], "ownRecordsOnly": False},
                               f"update role {name} (label, ownRecordsOnly)"))
            if _granted(current.get("permissions")) != _wanted(role):
                writes.append(("PUT", f"/api/roles/{name}/permissions", permissions_payload(role),
                               f"update role {name} (permissions)"))
            if _restrictions(current.get("fieldPermissions")) != _restrictions(fields["fields"]):
                writes.append(("PUT", f"/api/roles/{name}/field-permissions",
                               field_permissions_sync_payload(current.get("fieldPermissions"), fields),
                               f"update role {name} (field permissions)"))
        for method, path, body, message in writes:
            try:
                (client.post if method == "POST" else client.put)(path, body)
            except CoreError as e:
                _fatal(method, path, e)
                return 1
            if message:
                print(message)
        if current is None:
            created += 1
        elif writes:
            updated += 1
        else:
            print(f"skip   role {name} (up to date)")
            skipped += 1
    print(f"done: {created} created, {updated} updated, {skipped} skipped")
    return 0


def _print_dry_run(roles, model):
    for role in roles["roles"]:
        print("# POST /api/roles")
        print(json.dumps({"name": role["name"], "label": role["label"]}, indent=2, ensure_ascii=False))
        print(f"# PUT /api/roles/{role['name']}/permissions")
        print(json.dumps(permissions_payload(role), indent=2, ensure_ascii=False))
        fields = field_permissions_payload(role, model)
        if fields["fields"]:
            print(f"# PUT /api/roles/{role['name']}/field-permissions")
            print(json.dumps(fields, indent=2, ensure_ascii=False))


def _parse_args(argv):
    p = argparse.ArgumentParser(description="Crea o sincroniza los roles de SGSPE (roles.json) en wasichai Core.")
    p.add_argument("--roles", default=os.path.join(HERE, "roles.json"))
    p.add_argument("--model", default=os.path.join(HERE, "model.json"))
    p.add_argument("--core", default=os.environ.get("WASICHAI_CORE", "http://localhost:8094"))
    p.add_argument("--email", default=os.environ.get("WASICHAI_EMAIL", "admin@wasichai.local"))
    p.add_argument("--password", default=os.environ.get("WASICHAI_PASSWORD", "admin"))
    p.add_argument("--dry-run", action="store_true", help="imprime lo que mandaría a un core vacío; no llama a core")
    return p.parse_args(argv)


def _load(path):
    """El json de `path`, o None tras decir en stderr por qué no se pudo leer (sin traza)."""
    try:
        with open(path, encoding="utf-8") as f:
            return json.load(f)
    except OSError as e:
        print(f"error: {path}: {e.strerror or e}", file=sys.stderr)
    except ValueError as e:
        # json.JSONDecodeError y UnicodeDecodeError son ValueError
        print(f"error: {path}: no es JSON válido: {e}", file=sys.stderr)
    return None


def main(argv=None):
    args = _parse_args(sys.argv[1:] if argv is None else argv)
    roles = _load(args.roles)
    model = _load(args.model)
    if roles is None or model is None:
        return 2
    errors = validate(roles, model)
    if errors:
        for e in errors:
            print(f"roles: {args.roles}: {e}")
        return 2
    if args.dry_run:
        _print_dry_run(roles, model)
        return 0
    client = Client(args.core)
    try:
        client.login(args.email, args.password)
    except CoreError as e:
        _fatal("POST", "/api/auth/login", e)
        return 1
    return do_apply(client, roles, model)


if __name__ == "__main__":
    sys.exit(main())
