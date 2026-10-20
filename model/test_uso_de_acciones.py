"""Pruebas de uso_de_acciones.py: cuenta, desde la auditoría de un periodo, cuántas veces las personas usaron cada acción de
una ficha y si el portal la ofrece a la vista (la primaria) o en «Más acciones» (sgspe-ui, rediseño de UX del 2026-10-20).

Correr: cd model && python3 -m unittest -v test_uso_de_acciones
"""
import io
import unittest
from contextlib import redirect_stdout

import uso_de_acciones as uso

WORKFLOWS = {
    "grievance": {
        "transitions": [
            {"name": "acknowledge", "from": "received", "to": "acknowledged"},
            {"name": "reject_received", "from": "received", "to": "rejected"},
            {"name": "close_reclassified_received", "from": "received", "to": "closed"},
            {"name": "void", "from": "received", "to": "cancelled"},
        ]
    },
    "commitment": {"transitions": [{"name": "submit", "from": "draft", "to": "pending_approval"}]},
}


def entrada(objeto, operacion, cambios, fuente="api"):
    return {"objectName": objeto, "operation": operacion, "changes": cambios, "source": fuente}


def estado(antes, despues):
    return [{"field": "state", "before": antes, "after": despues}]


class Clasificar(unittest.TestCase):
    def test_la_del_camino_es_la_primaria_la_destructiva_va_al_final_del_menu_y_el_resto_en_el_menu(self):
        self.assertEqual(uso.lugar("grievance", "acknowledge", "acknowledged"), "a la vista")
        self.assertEqual(uso.lugar("grievance", "reject_received", "rejected"), "Más acciones")
        self.assertEqual(uso.lugar("grievance", "void", "cancelled"), "Más acciones (destructiva)")

    def test_la_transicion_sale_del_workflow_por_su_origen_y_su_destino(self):
        self.assertEqual(uso.transicion_de(WORKFLOWS, "grievance", "received", "acknowledged"), "acknowledge")
        self.assertIsNone(uso.transicion_de(WORKFLOWS, "grievance", "received", "verified"))


class Contar(unittest.TestCase):
    def test_cuenta_solo_lo_que_hicieron_personas_y_las_acciones_propias_de_la_ficha(self):
        entradas = [
            entrada("grievance", "UPDATE", estado("received", "acknowledged")),
            entrada("grievance", "UPDATE", estado("received", "acknowledged")),
            entrada("grievance", "UPDATE", estado("received", "rejected")),
            # un trabajo programado no es una persona eligiendo un botón
            entrada("grievance", "UPDATE", estado("received", "acknowledged"), fuente="job:deadline"),
            # reclasificar cierra la queja con su comando
            entrada("grievance", "UPDATE", estado("received", "closed")),
            # «Crear compromiso desde esta queja» nace con origin_grievance
            entrada("commitment", "CREATE", [{"field": "origin_grievance", "before": None, "after": "g1"}]),
            # «Informar riesgo de atraso»
            entrada("commitment", "UPDATE", [{"field": "delay_risk_reported", "before": False, "after": True}]),
            # un cambio sin estado ni acción conocida no cuenta
            entrada("commitment", "UPDATE", [{"field": "title", "before": "a", "after": "b"}]),
        ]
        filas = {(f["objeto"], f["accion"]): f for f in uso.contar(entradas, WORKFLOWS)}
        self.assertEqual(filas[("grievance", "acknowledge")]["veces"], 2)
        self.assertEqual(filas[("grievance", "acknowledge")]["lugar"], "a la vista")
        self.assertEqual(filas[("grievance", "reject_received")]["veces"], 1)
        self.assertEqual(filas[("grievance", "Reclasificar")]["lugar"], "Más acciones")
        self.assertEqual(filas[("commitment", "Crear compromiso desde una queja")]["veces"], 1)
        self.assertEqual(filas[("commitment", "Informar riesgo de atraso")]["veces"], 1)
        self.assertEqual(len(filas), 5)


class Leer(unittest.TestCase):
    def test_sigue_el_cursor_hasta_la_pagina_sin_cabecera(self):
        pedidas = []

        def llamar(ruta):
            pedidas.append(ruta)
            if "after=" not in ruta:
                return [entrada("grievance", "UPDATE", [])], {"X-Next-Cursor": "c1"}
            return [entrada("grievance", "UPDATE", [])], {}

        leidas = uso.leer_auditoria(llamar, "grievance", "2026-07-01T00:00:00Z", "2026-10-01T00:00:00Z")
        self.assertEqual(len(leidas), 2)
        self.assertIn("objectName=grievance", pedidas[0])
        self.assertIn("from=2026-07-01T00%3A00%3A00Z", pedidas[0])
        self.assertIn("after=c1", pedidas[1])


class Informe(unittest.TestCase):
    def test_la_tabla_dice_el_objeto_la_accion_donde_esta_y_su_parte(self):
        filas = uso.contar([entrada("grievance", "UPDATE", estado("received", "acknowledged"))] * 3
                           + [entrada("grievance", "UPDATE", estado("received", "rejected"))], WORKFLOWS)
        salida = io.StringIO()
        with redirect_stdout(salida):
            uso.imprimir(filas)
        texto = salida.getvalue()
        self.assertIn("| grievance | acknowledge | a la vista | 3 | 75 % |", texto)
        self.assertIn("| grievance | reject_received | Más acciones | 1 | 25 % |", texto)


if __name__ == "__main__":
    unittest.main()
