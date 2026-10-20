"""Cuánto usan las personas cada acción de una ficha, para decidir qué va a la vista y qué en «Más acciones».

El rediseño de UX del portal (sgspe-ui#75, 2026-10-20) dejó a la vista una sola primaria por ficha, la transición del camino
del objeto, y llevó el resto a «Más acciones»: las otras transiciones, las destructivas y lo poco frecuente (reclasificar,
crear un compromiso desde una queja, informar un riesgo de atraso). Este informe lee la auditoría de un periodo
(GET /api/audit por cursor, WAS-ADR-0052) y cuenta, por objeto, cuántas veces una persona dio cada transición o acción y
dónde la ofrece hoy el portal. Si algo de «Más acciones» se usa tanto como la primaria, conviene que vuelva a la vista.

- Solo cuenta lo que hizo una persona (`source` api): un trabajo programado o una automatización no eligen un botón.
- La transición sale del workflow del repositorio (`workflows/*.json`) por su estado de origen y de destino.
- CAMINO es copia de `CAMINO_DE` de sgspe-ui (`src/portal/fichas/siguienteAccion.ts`): si cambia allá, se cambia aquí.
- Necesita leer la auditoría de todo el tenant: una cuenta ADMIN o AUDITOR.

Uso: python3 uso_de_acciones.py --core URL --email … --password … [--desde 2026-07-01] [--hasta 2026-10-01]
"""
import argparse
import datetime
import json
import os
import sys
import urllib.error
import urllib.parse
import urllib.request

from core_client import Client, CoreError

HERE = os.path.dirname(os.path.abspath(__file__))

# las transiciones del camino de cada objeto, en su orden: la primaria de su ficha (copia de CAMINO_DE de sgspe-ui)
CAMINO = {
    "commitment": ["submit", "approve", "approve_senior", "start", "unblock", "register_fulfillment", "verify", "close"],
    "grievance": ["acknowledge", "assess", "start_action", "investigate", "start_action_after_investigation", "resolve",
                  "resolve_high_severity", "verify", "close", "close_resolved"],
    "inquiry": ["answer", "refer", "close", "close_referred"],
    "stakeholder_request": ["start_evaluation", "accept", "close_accepted", "close_declined"],
    "engagement": ["schedule", "register_held", "register_held_unplanned", "draft_minutes", "submit_minutes",
                   "approve_minutes", "close", "reschedule"],
    "stakeholder": ["profile", "assess"],
    "social_risk": ["assess", "decide_mitigation", "decide_monitoring", "start_monitoring"],
    "agreement": ["start_negotiation", "activate", "activate_from_draft", "resume", "conclude"],
    "social_contribution": ["approve", "register_procurement", "register_delivery", "close"],
    "social_program": ["submit", "approve", "start_execution", "start_monitoring", "start_evaluation", "close", "resume"],
    "field_visit": ["start", "complete"],
    "area_of_influence": ["approve"],
    "project": ["submit_for_review", "approve", "resume"],
}
# a dónde llevan las destructivas: van al final de «Más acciones» (BotonesDeTransicion de sgspe-ui)
DESTINOS_DESTRUCTIVOS = {"cancelled", "voided", "retired", "terminated"}
A_LA_VISTA = "a la vista"
EN_EL_MENU = "Más acciones"
DESTRUCTIVA = "Más acciones (destructiva)"
# reclasificar es un comando que cierra el registro con estas transiciones
RECLASIFICAR = "close_reclassified"
ETIQUETA_RECLASIFICAR = "Reclasificar"
DESDE_UNA_QUEJA = "Crear compromiso desde una queja"
RIESGO_DE_ATRASO = "Informar riesgo de atraso"
PAGINA = 500  # el tope de /api/audit por página


def leer_workflows(carpeta=os.path.join(HERE, "workflows")):
    """Las transiciones de cada objeto, de los workflows del repositorio."""
    workflows = {}
    for archivo in sorted(os.listdir(carpeta)):
        if archivo.endswith(".json"):
            with open(os.path.join(carpeta, archivo), encoding="utf-8") as f:
                workflows[archivo[:-5]] = json.load(f)["definition"]
    return workflows


def transicion_de(workflows, objeto, antes, despues):
    """El nombre de la transición que lleva de un estado al otro, o None si el workflow no tiene ninguna."""
    for t in workflows.get(objeto, {}).get("transitions", []):
        if t["from"] == antes and t["to"] == despues:
            return t["name"]
    return None


def lugar(objeto, nombre, destino):
    """Dónde la ofrece la ficha del portal: la del camino a la vista; las demás en «Más acciones», las destructivas al final."""
    if nombre.startswith(RECLASIFICAR):
        return EN_EL_MENU
    if destino in DESTINOS_DESTRUCTIVOS:
        return DESTRUCTIVA
    return A_LA_VISTA if nombre in CAMINO.get(objeto, []) else EN_EL_MENU


def _cambio(entrada, campo):
    return next((c for c in entrada.get("changes") or [] if c.get("field") == campo), None)


def contar(entradas, workflows):
    """Las filas del informe: objeto, acción, lugar y veces, de lo que hicieron personas."""
    cuentas = {}

    def sumar(objeto, accion, donde):
        fila = cuentas.setdefault((objeto, accion), {"objeto": objeto, "accion": accion, "lugar": donde, "veces": 0})
        fila["veces"] += 1

    for entrada in entradas:
        if entrada.get("source") != "api":
            continue
        objeto = entrada.get("objectName")
        cambio = _cambio(entrada, "state")
        if entrada.get("operation") == "UPDATE" and cambio and cambio.get("before") and cambio.get("after"):
            nombre = transicion_de(workflows, objeto, cambio["before"], cambio["after"])
            if nombre:
                accion = ETIQUETA_RECLASIFICAR if nombre.startswith(RECLASIFICAR) else nombre
                sumar(objeto, accion, lugar(objeto, nombre, cambio["after"]))
            continue
        if objeto == "commitment" and entrada.get("operation") == "CREATE":
            origen = _cambio(entrada, "origin_grievance")
            if origen and origen.get("after"):
                sumar(objeto, DESDE_UNA_QUEJA, EN_EL_MENU)
            continue
        riesgo = _cambio(entrada, "delay_risk_reported")
        if objeto == "commitment" and riesgo and riesgo.get("after") is True:
            sumar(objeto, RIESGO_DE_ATRASO, EN_EL_MENU)
    return sorted(cuentas.values(), key=lambda f: (f["objeto"], -f["veces"], f["accion"]))


def leer_auditoria(llamar, objeto, desde, hasta):
    """Todas las entradas de un objeto en el periodo, siguiendo X-Next-Cursor hasta la página que no lo trae."""
    entradas, cursor = [], None
    while True:
        consulta = {"objectName": objeto, "from": desde, "to": hasta, "limit": PAGINA}
        if cursor:
            consulta["after"] = cursor
        pagina, cabeceras = llamar(f"/api/audit?{urllib.parse.urlencode(consulta)}")
        entradas.extend(pagina or [])
        cursor = cabeceras.get("X-Next-Cursor")
        if not cursor:
            return entradas


def imprimir(filas):
    """Una tabla Markdown con la parte de cada acción entre las de su objeto."""
    totales = {}
    for f in filas:
        totales[f["objeto"]] = totales.get(f["objeto"], 0) + f["veces"]
    print("| Objeto | Acción | Dónde la ofrece la ficha | Veces | Parte |")
    print("|---|---|---|---|---|")
    for f in filas:
        parte = round(100 * f["veces"] / totales[f["objeto"]])
        print(f"| {f['objeto']} | {f['accion']} | {f['lugar']} | {f['veces']} | {parte} % |")


def _llamador(client):
    def llamar(ruta):
        pedido = urllib.request.Request(client.base_url + ruta, headers={"Authorization": f"Bearer {client.token}"})
        try:
            with urllib.request.urlopen(pedido, timeout=60) as respuesta:
                return json.loads(respuesta.read() or b"[]"), dict(respuesta.headers)
        except urllib.error.HTTPError as e:
            raise CoreError(e.code, e.read().decode("utf-8", errors="replace")) from e

    return llamar


def _parse_args(argv):
    hoy = datetime.date.today()
    p = argparse.ArgumentParser(description="Cuánto se usa cada acción de las fichas, según la auditoría de un periodo.")
    p.add_argument("--core", default=os.environ.get("WASICHAI_CORE", "http://localhost:8094"))
    p.add_argument("--email", default=os.environ.get("WASICHAI_EMAIL", "admin@wasichai.local"))
    p.add_argument("--password", default=os.environ.get("WASICHAI_PASSWORD", "admin"))
    p.add_argument("--desde", default=(hoy - datetime.timedelta(days=90)).isoformat(), help="día de inicio (incluido)")
    p.add_argument("--hasta", default=(hoy + datetime.timedelta(days=1)).isoformat(), help="día de fin (excluido)")
    return p.parse_args(argv)


def main(argv=None):
    args = _parse_args(sys.argv[1:] if argv is None else argv)
    client = Client(args.core)
    try:
        client.login(args.email, args.password)
        workflows = leer_workflows()
        llamar = _llamador(client)
        entradas = []
        for objeto in CAMINO:
            entradas.extend(leer_auditoria(llamar, objeto, f"{args.desde}T00:00:00Z", f"{args.hasta}T00:00:00Z"))
    except CoreError as e:
        print(f"error: {e}", file=sys.stderr)
        return 1
    print(f"Auditoría del {args.desde} al {args.hasta} (sin incluir): {len(entradas)} entradas leídas.\n")
    imprimir(contar(entradas, workflows))
    return 0


if __name__ == "__main__":
    sys.exit(main())
