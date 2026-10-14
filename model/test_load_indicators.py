"""Pruebas de load_indicators.py y de model/indicators.json (RQ-DSH-037, ASM-136; dashboards.md §4 y §10): el catálogo
tiene cada KPI de dashboards.md §4 con las metas y frecuencias de DOC-01, y la carga crea lo que falta, actualiza solo los
textos de documentación y nunca sobrescribe la meta ni la frecuencia que el cliente editó.

Correr: cd model && python3 -m unittest -v test_load_indicators
"""
import io
import json
import os
import re
import tempfile
import unittest
from contextlib import redirect_stderr, redirect_stdout

import load_indicators
from fake_core import FakeCore

HERE = os.path.dirname(os.path.abspath(__file__))
DASHBOARDS = os.path.join(os.path.dirname(HERE), "docs", "dashboards.md")
SAMPLE = {"indicators": [
    {"code": "KPI-ENG-02", "name": "Reuniones sostenidas al mes", "definition": "Reuniones frente a la meta",
     "formula": "Por mes", "disaggregation": "Proyecto, periodo", "verification_means": "Reporte de reuniones mensuales",
     "source_reference": "dashboards.md §4.3; DOC-01 Tabla 9.5, #5", "frequency": "Mensual", "target_value": 2},
    {"code": "KPI-COM-02", "name": "Compromisos abiertos", "definition": "Los que no se cierran", "formula": "Número",
     "disaggregation": "Proyecto", "verification_means": None, "source_reference": "dashboards.md §4.4",
     "frequency": None, "target_value": None},
]}


def doc_kpis():
    """Los KPI de dashboards.md §4 (primera celda de cada fila) y {KPI: (número, medio de verificación)} de §4.9."""
    with open(DASHBOARDS, encoding="utf-8") as f:
        lines = f.read().splitlines()
    body = lines[lines.index("## 4. Catálogo de KPIs"):lines.index("## 5. Tableros")]
    codes = [line.split("|")[1].strip() for line in body if line.startswith("| KPI-")]
    table = {}
    for line in body:
        if re.match(r"^\| \d+ \|", line):
            cells = [c.strip() for c in line.strip().strip("|").split("|")]
            table[cells[2]] = (int(cells[0]), cells[4])
    return codes, table


def write(folder, data):
    path = os.path.join(folder, "indicators.json")
    with open(path, "w", encoding="utf-8") as f:
        json.dump(data, f, ensure_ascii=False)
    return path


class CatalogFileTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.items = load_indicators.read_catalog(os.path.join(HERE, "indicators.json"))
        cls.by_code = {i["code"]: i for i in cls.items}
        cls.codes, cls.table95 = doc_kpis()

    def test_trae_cada_kpi_de_dashboards_4_en_su_orden(self):
        self.assertEqual([i["code"] for i in self.items], self.codes)
        self.assertEqual(len(self.items), 53)

    def test_las_trece_filas_de_la_tabla_9_5_llevan_su_medio_de_verificacion_y_su_numero(self):
        self.assertEqual(len(self.table95), 13)
        for code, (number, means) in self.table95.items():
            self.assertEqual(self.by_code[code]["verification_means"], means, code)
            self.assertIn(f"DOC-01 Tabla 9.5, #{number}", self.by_code[code]["source_reference"], code)
        others = [c for c, i in self.by_code.items() if c not in self.table95 and i["verification_means"] is not None]
        self.assertEqual(others, [])

    def test_las_metas_y_frecuencias_son_las_de_doc_01(self):
        # ASM-136: KPI-ENG-02, dos al mes; evaluaciones de grupos de interés y Tabla 9.5, mensuales; riesgos, semanales
        self.assertEqual({c: i["target_value"] for c, i in self.by_code.items() if i["target_value"] is not None},
                         {"KPI-ENG-02": 2})
        monthly = set(self.table95) | {"KPI-STK-01", "KPI-STK-02", "KPI-STK-03", "KPI-STK-08"}
        weekly = {f"KPI-RSK-0{n}" for n in range(1, 6)}
        self.assertEqual({c for c, i in self.by_code.items() if i["frequency"] == "Mensual"}, monthly)
        self.assertEqual({c for c, i in self.by_code.items() if i["frequency"] == "Semanal"}, weekly)
        self.assertEqual({c for c, i in self.by_code.items() if i["frequency"] is None},
                         set(self.by_code) - monthly - weekly)

    def test_ningun_nombre_ni_texto_real_de_persona(self):
        # RQ-SEC-018: solo textos de dashboards.md, sin correos ni nombres
        text = json.dumps(self.items, ensure_ascii=False)
        self.assertNotIn("@", text)


class ReadCatalogTests(unittest.TestCase):
    def refused(self, data):
        with tempfile.TemporaryDirectory() as folder:
            with self.assertRaises(load_indicators.CatalogError) as raised:
                load_indicators.read_catalog(write(folder, data))
        return str(raised.exception)

    def test_rechaza_un_codigo_mal_formado_o_repetido_una_meta_negativa_y_una_clave_desconocida(self):
        item = SAMPLE["indicators"][0]
        self.assertIn("KPI-XXX-NN", self.refused({"indicators": [{**item, "code": "KPI-1"}]}))
        self.assertIn("repeated", self.refused({"indicators": [item, item]}))
        self.assertIn("target_value", self.refused({"indicators": [{**item, "target_value": -1}]}))
        self.assertIn("target_value", self.refused({"indicators": [{**item, "target_value": "2"}]}))
        self.assertIn("unknown keys", self.refused({"indicators": [{**item, "unit": "reuniones"}]}))
        self.assertIn("name is required", self.refused({"indicators": [{**item, "name": " "}]}))
        self.assertIn("non-empty", self.refused({"indicators": []}))


class LoadTests(unittest.TestCase):
    def setUp(self):
        self.core = FakeCore()
        self.addCleanup(self.core.stop)
        self.folder = tempfile.TemporaryDirectory()
        self.addCleanup(self.folder.cleanup)
        self.path = write(self.folder.name, SAMPLE)

    def run_cli(self, *extra):
        args = ["--file", self.path, "--core", self.core.base_url, "--email", "admin@wasichai.local",
                "--password", "admin", *extra]
        out, err = io.StringIO(), io.StringIO()
        with redirect_stdout(out), redirect_stderr(err):
            code = load_indicators.main(args)
        return code, out.getvalue(), err.getvalue()

    def stored(self):
        return {r["attributes"]["code"]: r["attributes"] for r in self.core.records.get("indicator", [])}

    def writes(self):
        return [(m, p) for m, p, _, _ in self.core.requests if m in ("POST", "PUT") and p != "/api/auth/login"]

    def test_crea_los_que_faltan_con_su_meta_y_frecuencia_iniciales_y_sin_los_null(self):
        code, out, err = self.run_cli()
        self.assertEqual(code, 0, err)
        self.assertIn("done: 2 created, 0 updated, 0 skipped", out)
        stored = self.stored()
        self.assertEqual((stored["KPI-ENG-02"]["target_value"], stored["KPI-ENG-02"]["frequency"]), (2, "Mensual"))
        self.assertNotIn("target_value", stored["KPI-COM-02"])

    def test_la_segunda_corrida_no_escribe_nada(self):
        self.run_cli()
        self.core.requests.clear()
        code, out, err = self.run_cli()
        self.assertEqual(code, 0, err)
        self.assertIn("done: 0 created, 0 updated, 2 skipped", out)
        self.assertEqual(self.writes(), [])

    def test_una_meta_o_una_frecuencia_editada_por_el_cliente_no_se_toca_ni_cuando_cambian_los_textos(self):
        # Review Focus: el ADMIN subió la meta a 3, borró la frecuencia y la documentación de dashboards.md cambió
        self.run_cli()
        ours = self.stored()["KPI-ENG-02"]
        ours["target_value"] = 3
        ours.pop("frequency")
        changed = json.loads(json.dumps(SAMPLE))
        changed["indicators"][0]["definition"] = "Reuniones realizadas frente a la meta del mes"
        self.path = write(self.folder.name, changed)
        self.core.requests.clear()
        code, out, err = self.run_cli()
        self.assertEqual(code, 0, err)
        self.assertIn("done: 0 created, 1 updated, 1 skipped", out)
        after = self.stored()["KPI-ENG-02"]
        self.assertEqual(after["definition"], "Reuniones realizadas frente a la meta del mes")
        self.assertEqual(after["target_value"], 3)
        self.assertIsNone(after.get("frequency"))
        self.assertEqual(len(self.writes()), 1)

    def test_un_indicador_que_el_cliente_agrego_no_se_borra(self):
        self.core.add_record("indicator", {"code": "KPI-XYZ-01", "name": "Indicador propio del cliente"})
        code, out, err = self.run_cli()
        self.assertEqual(code, 0, err)
        self.assertIn("KPI-XYZ-01", self.stored())
        self.assertEqual([m for m, _, _, _ in self.core.requests if m == "DELETE"], [])

    def test_un_rechazo_de_core_sale_con_1(self):
        self.core.fail_on_record = "indicator"
        code, _, err = self.run_cli()
        self.assertEqual(code, 1)
        self.assertIn("error -> 400", err)

    def test_dry_run_y_validate_only_no_llaman_a_core(self):
        for flag in ("--dry-run", "--validate-only"):
            code, out, err = self.run_cli(flag)
            self.assertEqual(code, 0, err)
        self.assertEqual(self.core.requests, [])

    def test_un_archivo_invalido_sale_con_2_sin_llamar_a_core(self):
        self.path = write(self.folder.name, {"indicators": [{"code": "x"}]})
        code, _, err = self.run_cli()
        self.assertEqual(code, 2)
        self.assertIn("KPI-XXX-NN", err)
        self.assertEqual(self.core.requests, [])


if __name__ == "__main__":
    unittest.main()
