"""Pruebas de check_traceability.py.

Correr: python3 -m unittest discover -s tools -v
"""
import io
import os
import tempfile
import unittest
from contextlib import redirect_stderr, redirect_stdout

import check_traceability

ANALYSIS = (
    "| ID | Requisito |\n|---|---|\n"
    "| RF-PRJ-001 | Registrar proyectos |\n"
    "| RNF-I18N-001 | Español e inglés |\n"
    "| RN-COM-001 | Cumplido con acta |\n"
    "| RQ-SEC-001 | JWT de wasichai |\n"
    "Un párrafo que cita RF-PRJ-002 sin definirlo.\n"
)
MATRIX_OK = "| Requirement | Source |\n|---|---|\n| RF-PRJ-001 | x |\n| RNF-I18N-001 | x |\n| RN-COM-001 | x |\n| RQ-SEC-001 | x |\n"


def run(args):
    out, err = io.StringIO(), io.StringIO()
    with redirect_stdout(out), redirect_stderr(err):
        code = check_traceability.main(args)
    return code, out.getvalue(), err.getvalue()


class CheckTraceabilityTests(unittest.TestCase):
    def test_solo_cuenta_los_ids_que_abren_una_fila(self):
        self.assertEqual(check_traceability.row_ids(ANALYSIS), {"RF-PRJ-001", "RNF-I18N-001", "RN-COM-001", "RQ-SEC-001"})

    def test_nada_falta_cuando_cada_id_tiene_su_fila(self):
        self.assertEqual(check_traceability.missing(ANALYSIS, MATRIX_OK), [])

    def test_nombra_los_que_faltan_en_orden(self):
        self.assertEqual(check_traceability.missing(ANALYSIS, "| RQ-SEC-001 | x |\n"), ["RF-PRJ-001", "RN-COM-001", "RNF-I18N-001"])

    def test_la_cli_sale_con_1_y_nombra_los_que_faltan(self):
        with tempfile.TemporaryDirectory() as folder:
            analysis, matrix = os.path.join(folder, "a.md"), os.path.join(folder, "m.md")
            with open(analysis, "w", encoding="utf-8") as f:
                f.write(ANALYSIS)
            with open(matrix, "w", encoding="utf-8") as f:
                f.write("| RF-PRJ-001 | x |\n")
            code, out, err = run(["--analysis", analysis, "--matrix", matrix])
        self.assertEqual(code, 1)
        self.assertIn("sin fila en la matriz: RN-COM-001", out)

    def test_un_archivo_que_falta_sale_con_2(self):
        code, out, err = run(["--analysis", "/no/existe.md"])
        self.assertEqual(code, 2)
        self.assertIn("/no/existe.md", err)

    def test_los_documentos_reales_estan_completos(self):
        code, out, err = run([])
        self.assertEqual(code, 0, out + err)
        self.assertTrue(out.startswith("ok: "), out)


if __name__ == "__main__":
    unittest.main()
