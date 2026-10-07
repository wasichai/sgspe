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
        self.assertEqual(counts, {"DEPARTAMENTO": 25, "PROVINCIA": 196, "DISTRITO": 1892})
        self.assertEqual(skipped, [(1894, "NA")])
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

    def test_un_padre_equivocado_o_vacio_se_corrige(self):
        self.run_cli()
        units = self.units()
        units["130601"]["attributes"]["parent_unit"] = units["130100"]["id"]  # otra provincia
        units["130602"]["attributes"]["parent_unit"] = None  # sin padre
        code, out, err = self.run_cli()
        self.assertEqual(code, 0, err)
        self.assertIn("done: 0 created, 2 updated, 4 skipped", out)
        units = self.units()
        self.assertEqual(units["130601"]["attributes"]["parent_unit"], units["130600"]["id"])
        self.assertEqual(units["130602"]["attributes"]["parent_unit"], units["130600"]["id"])

    def test_retoma_cuando_solo_faltan_los_distritos(self):
        self.run_cli()
        self.core.records["administrative_unit"] = [
            r for r in self.core.records["administrative_unit"] if r["attributes"]["level"] != "DISTRITO"]
        parents = {code: r["id"] for code, r in self.units().items()}
        self.core.requests.clear()
        code, out, err = self.run_cli()
        self.assertEqual(code, 0, err)
        self.assertIn("done: 3 created, 0 updated, 3 skipped", out)
        units = self.units()
        self.assertEqual({c: units[c]["id"] for c in parents}, parents)  # los padres no se tocaron
        self.assertEqual(units["130104"]["attributes"]["parent_unit"], parents["130100"])
        self.assertEqual(units["130602"]["attributes"]["parent_unit"], parents["130600"])
        writes = [(m, p) for m, p, _, _ in self.core.requests if m in ("POST", "PUT") and p != "/api/auth/login"]
        self.assertEqual(writes, [("POST", "/api/objects/administrative_unit/records")] * 3)

    def test_informa_el_avance_por_nivel(self):
        code, out, err = self.run_cli()
        self.assertEqual(code, 0, err)
        lines = out.splitlines()
        for line in ("departamentos: 1/1", "provincias: 2/2", "distritos: 3/3"):
            self.assertIn(line, lines)
        self.assertLess(lines.index("departamentos: 1/1"), lines.index("provincias: 2/2"))
        self.assertLess(lines.index("provincias: 2/2"), lines.index("distritos: 3/3"))
        self.assertTrue(lines[-1].startswith("done: "))

    def test_informa_cada_200_registros_dentro_de_un_nivel(self):
        text = "codigo,departamento,provincia,distrito\n" + "".join(
            f"13{p:02d}{d:02d},LA LIBERTAD,PROVINCIA {p},DISTRITO {p}-{d}\n" for p in range(1, 6) for d in range(1, 91))
        self.csv = write_csv(self.folder.name, text)  # 450 distritos en 5 provincias
        code, out, err = self.run_cli()
        self.assertEqual(code, 0, err)
        lines = out.splitlines()
        self.assertEqual([l for l in lines if l.startswith("distritos:")],
                         ["distritos: 200/450", "distritos: 400/450", "distritos: 450/450"])
        self.assertEqual([l for l in lines if l.startswith("provincias:")], ["provincias: 5/5"])
        self.assertIn("done: 456 created, 0 updated, 0 skipped", out)

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
