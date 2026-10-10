"""Los pasos 2 a 5 de security.md §4.1 sobre un tenant, en orden, como su administrador (ASM-109).

Los comparten provision_tenant.py (alta) y upgrade_tenants.py (subida de fase de cada tenant). Cada paso es la función
main(argv) de su script, llamada en el mismo proceso: la contraseña viaja en argv solo dentro de este proceso, nunca en la
línea de comandos ni en la salida. Solo biblioteca estándar.
"""
import apply
import apply_roles
import load_ubigeo

# (nombre que se informa, función que recibe el argv de conexión y devuelve el código de salida)
STEPS = (
    ("apply.py", lambda argv: apply.main(argv)),
    ("apply_roles.py", lambda argv: apply_roles.main(argv)),
    ("apply.py --workflows", lambda argv: apply.main(argv + ["--workflows"])),
    ("load_ubigeo.py", lambda argv: load_ubigeo.main(argv)),
)


def run_steps(core, email, password, steps=STEPS):
    """Corre los pasos en orden y se detiene en el primero que falla: su nombre, o None si todos terminaron en 0."""
    argv = ["--core", core, "--email", email, "--password", password]
    for name, step in steps:
        print(f"== {name} ({email})", flush=True)
        if step(list(argv)) != 0:
            return name
    return None
