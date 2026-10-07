"""Pruebas de load_ubigeo.py contra un FakeCore (RF-TER-001, ASM-016): jerarquía, idempotencia y filas inválidas.

Correr: cd model && python3 -m unittest -v test_load_ubigeo
"""
import io
import os
import tempfile
import unittest
from contextlib import redirect_stderr, redirect_stdout

import load_ubigeo
from fake_core import FakeCore

HERE = os.path.dirname(os.path.abspath(__file__))

# topónimos públicos (database-model.md §25); "NA" es la fila sin código que trae el archivo real
SAMPLE = """codigo,departamento,provincia,distrito
130601,LA LIBERTAD,OTUZCO,OTUZCO
130602,LA LIBERTAD,OTUZCO,AGALLPAMPA
130104,LA LIBERTAD,TRUJILLO,HUANCHACO
NA,MOQUEGUA,MARISCAL NIETO,SAN ANTONIO
"""


def write_csv(folder, text):
    path = os.path.join(folder, "ubigeo.csv")
    with open(path, "w", encoding="utf-8") as f:
        f.write(text)
    return path


class ReadUnitsTests(unittest.TestCase):
    def test_deriva_departamento_y_provincias_y_ordena_por_nivel(self):
        with tempfile.TemporaryDirectory() as folder:
            units, skipped = load_ubigeo.read_units(write_csv(folder, SAMPLE))
        self.assertEqual([(u["ubigeo_code"], u["level"], u["parent"]) for u in units], [
            ("130000", "DEPARTAMENTO", None),
            ("130100", "PROVINCIA", "130000"), ("130600", "PROVINCIA", "130000"),
            ("130104", "DISTRITO", "130100"), ("130601", "DISTRITO", "130600"), ("130602", "DISTRITO", "130600")])
        self.assertEqual(units[0]["name"], "LA LIBERTAD")
        self.assertEqual(skipped, [(5, "NA")])

    def test_el_archivo_del_repositorio_tiene_los_niveles_del_ubigeo(self):
        units, skipped = load_ubigeo.read_units(os.path.join(HERE, "data", "ubigeo.csv"))
        counts = {level: sum(1 for u in units if u["level"] == level) for level in load_ubigeo.LEVELS}
        self.assertEqual(counts["DEPARTAMENTO"], 25)
        self.assertGreater(counts["PROVINCIA"], 190)
        self.assertGreater(counts["DISTRITO"], 1800)
        self.assertEqual(len({u["ubigeo_code"] for u in units}), len(units))

    def test_un_csv_sin_las_columnas_se_rechaza(self):
        with tempfile.TemporaryDirectory() as folder:
            with self.assertRaises(load_ubigeo.CsvError):
                load_ubigeo.read_units(write_csv(folder, "code,name\n130601,OTUZCO\n"))


class LoadTests(unittest.TestCase):
    def setUp(self):
        self.core = FakeCore()
        self.addCleanup(self.core.stop)
        self.folder = tempfile.TemporaryDirectory()
        self.addCleanup(self.folder.cleanup)
        self.csv = write_csv(self.folder.name, SAMPLE)

    def run_cli(self, *extra):
        args = ["--csv", self.csv, "--core", self.core.base_url, "--email", "admin@wasichai.local",
                "--password", "admin", *extra]
        out, err = io.StringIO(), io.StringIO()
        with redirect_stdout(out), redirect_stderr(err):
            code = load_ubigeo.main(args)
        return code, out.getvalue(), err.getvalue()

    def units(self):
        return {r["attributes"]["ubigeo_code"]: r for r in self.core.records.get("administrative_unit", [])}

    def test_crea_las_unidades_con_su_unidad_superior(self):
        code, out, err = self.run_cli()
        self.assertEqual(code, 0, err)
        self.assertIn("done: 6 created, 0 updated, 0 skipped", out)
        self.assertIn("skip   row 5: code 'NA'", err)
        units = self.units()
        self.assertEqual(units["130601"]["attributes"]["parent_unit"], units["130600"]["id"])
        self.assertEqual(units["130600"]["attributes"]["parent_unit"], units["130000"]["id"])
        self.assertIsNone(units["130000"]["attributes"]["parent_unit"])

    def test_la_segunda_corrida_no_escribe_nada(self):
        self.run_cli()
        self.core.requests.clear()
        code, out, err = self.run_cli()
        self.assertEqual(code, 0, err)
        self.assertIn("done: 0 created, 0 updated, 6 skipped", out)
        self.assertEqual([m for m, p, _, _ in self.core.requests if m in ("POST", "PUT") and p != "/api/auth/login"], [])

    def test_un_nombre_cambiado_se_reemplaza(self):
        self.run_cli()
        self.units()["130602"]["attributes"]["name"] = "OTRO NOMBRE"
        code, out, err = self.run_cli()
        self.assertEqual(code, 0, err)
        self.assertIn("done: 0 created, 1 updated, 5 skipped", out)
        self.assertEqual(self.units()["130602"]["attributes"]["name"], "AGALLPAMPA")

    def test_un_rechazo_de_core_sale_con_1(self):
        self.core.fail_on_record = "administrative_unit"
        code, out, err = self.run_cli()
        self.assertEqual(code, 1)
        self.assertIn("error -> 400", err)

    def test_dry_run_cuenta_sin_llamar_a_core(self):
        code, out, err = self.run_cli("--dry-run")
        self.assertEqual(code, 0, err)
        self.assertIn("ubigeo: 1 departamentos, 2 provincias, 3 distritos", out)
        self.assertEqual(self.core.requests, [])


if __name__ == "__main__":
    unittest.main()
