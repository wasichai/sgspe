"""Los pasos de security.md §4.1 sobre un tenant, en orden, como su administrador (ASM-109): el modelo, los roles, los
workflows (con los tipos de documento y las automatizaciones), el ubigeo, desde F8 el catálogo de indicadores y, desde F6,
las capas públicas de GeoServer.

Los comparten provision_tenant.py (alta) y upgrade_tenants.py (subida de fase de cada tenant). Cada paso es la función
main(argv) de su script, llamada en el mismo proceso: la contraseña viaja en argv solo dentro de este proceso, nunca en la
línea de comandos ni en la salida. publish_layers.py toma las credenciales de GeoServer del entorno
(WASICHAI_GIS_GEOSERVER_*); un despliegue sin GeoServer lo omite con --skip-layers. Solo biblioteca estándar.
"""
import apply
import apply_roles
import load_indicators
import load_ubigeo
import publish_layers

# el paso de las capas: el último, y el único que se omite (--skip-layers)
LAYERS = "publish_layers.py"

# (nombre que se informa, función que recibe el argv de conexión y devuelve el código de salida)
STEPS = (
    ("apply.py", lambda argv: apply.main(argv)),
    ("apply_roles.py", lambda argv: apply_roles.main(argv)),
    ("apply.py --workflows", lambda argv: apply.main(argv + ["--workflows"])),
    ("load_ubigeo.py", lambda argv: load_ubigeo.main(argv)),
    ("load_indicators.py", lambda argv: load_indicators.main(argv)),
    (LAYERS, lambda argv: publish_layers.main(argv)),
)


def selected(steps, skip_layers):
    """Los pasos que se corren: todos, o todos menos el de las capas con --skip-layers."""
    return tuple(step for step in steps if not (skip_layers and step[0] == LAYERS))


def run_steps(core, email, password, steps=STEPS):
    """Corre los pasos en orden y se detiene en el primero que falla: su nombre, o None si todos terminaron en 0."""
    argv = ["--core", core, "--email", email, "--password", password]
    for name, step in steps:
        print(f"== {name} ({email})", flush=True)
        if step(list(argv)) != 0:
            return name
    return None
