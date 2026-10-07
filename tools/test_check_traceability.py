"""Pruebas de check_traceability.py.

Correr: python3 -m unittest discover -s tools -v
"""
import io
import os
import re
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
MATRIX_OK = (
    "# Matriz\n\n## 1. Cómo leer la matriz\n\n## 2. Requisitos\n\n| Requirement | Source |\n|---|---|\n"
    "| RF-PRJ-001 | x |\n| RNF-I18N-001 | x |\n| RN-COM-001 | x |\n| RQ-SEC-001 | x |\n\n"
    "## 6. Trazabilidad inversa por fuente\n\n## 7. Huérfanos\n"
)
MATRIX_HEAD = "## 2. Requisitos\n\n"
MATRIX_TAIL = "\n## 6. Trazabilidad inversa por fuente\n\n## 7. Huérfanos\n\n"


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
        matrix = MATRIX_HEAD + "| RQ-SEC-001 | x |\n" + MATRIX_TAIL
        self.assertEqual(check_traceability.missing(ANALYSIS, matrix), ["RF-PRJ-001", "RN-COM-001", "RNF-I18N-001"])

    def test_un_id_citado_en_otra_fila_no_cuenta(self):
        matrix = MATRIX_OK.replace("| RF-PRJ-001 | x |", "| RQ-SEC-001 | cita RF-PRJ-001 |")
        self.assertEqual(check_traceability.missing(ANALYSIS, matrix), ["RF-PRJ-001"])

    def test_una_fila_fuera_de_las_secciones_principales_no_cuenta(self):
        matrix = MATRIX_OK.replace("| RF-PRJ-001 | x |\n", "") + "| RF-PRJ-001 | API | No aplica |\n"
        self.assertEqual(check_traceability.missing(ANALYSIS, matrix), ["RF-PRJ-001"])

    def test_una_fila_antes_de_la_seccion_2_tampoco_cuenta(self):
        matrix = "| RF-PRJ-001 | x |\n" + MATRIX_OK.replace("| RF-PRJ-001 | x |\n", "")
        self.assertEqual(check_traceability.missing(ANALYSIS, matrix), ["RF-PRJ-001"])

    def test_la_fila_sin_espacios_en_las_barras_cuenta(self):
        self.assertEqual(check_traceability.row_ids("|RF-PRJ-001|Registrar|\n| RN-COM-001|x|\n"), {"RF-PRJ-001", "RN-COM-001"})

    def test_un_analisis_sin_ids_sale_con_1(self):
        with tempfile.TemporaryDirectory() as folder:
            analysis, matrix = os.path.join(folder, "a.md"), os.path.join(folder, "m.md")
            with open(analysis, "w", encoding="utf-8") as f:
                f.write("# Análisis sin tablas de requisitos\n")
            with open(matrix, "w", encoding="utf-8") as f:
                f.write(MATRIX_OK)
            code, out, err = run(["--analysis", analysis, "--matrix", matrix])
        self.assertEqual(code, 1)
        self.assertIn("ningún requisito", err)

    def test_la_cli_sale_con_1_y_nombra_los_que_faltan(self):
        with tempfile.TemporaryDirectory() as folder:
            analysis, matrix = os.path.join(folder, "a.md"), os.path.join(folder, "m.md")
            with open(analysis, "w", encoding="utf-8") as f:
                f.write(ANALYSIS)
            with open(matrix, "w", encoding="utf-8") as f:
                f.write(MATRIX_HEAD + "| RF-PRJ-001 | x |\n" + MATRIX_TAIL)
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
        self.assertEqual(out, "ok: 298 requisitos, todos con su fila\n")

    def test_los_ids_del_analisis_coinciden_con_las_familias_del_20_1(self):
        with open(os.path.join(check_traceability.ROOT, "docs", "requirements-analysis.md"), encoding="utf-8") as f:
            text = f.read()
        # CON, GAP, UPS y ASM también están en la tabla, pero no son requisitos de la matriz
        declared = {
            family: int(count)
            for family, count in re.findall(r"^\| `((?:RF|RNF|RN|RQ)-[A-Z0-9]+)` \| (\d+) \|$", text, re.MULTILINE)
        }
        found = {}
        for requirement in check_traceability.row_ids(text):
            family = requirement.rsplit("-", 1)[0]
            found[family] = found.get(family, 0) + 1
        self.assertEqual(found, declared)
        self.assertEqual(sum(declared.values()), 298)


if __name__ == "__main__":
    unittest.main()
