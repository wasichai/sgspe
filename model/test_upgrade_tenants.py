"""Pruebas de upgrade_tenants.py (security.md §4.1, ASM-109): cada tenant del inventario, con su administrador y en orden;
uno que falla no detiene a los demás, el resumen lo nombra y la salida es 1; las contraseñas no salen.

Correr: cd model && python3 -m unittest -v test_upgrade_tenants
"""
import io
import json
import os
import tempfile
import unittest
from contextlib import redirect_stderr, redirect_stdout
from unittest import mock

import upgrade_tenants

INVENTORY = {"tenants": [{"slug": "cliente-uno", "adminEmail": "admin-cliente-uno@sgspe.test"},
                         {"slug": "cliente-dos", "adminEmail": "admin-cliente-dos@sgspe.test"},
                         {"slug": "cliente-tres", "adminEmail": "admin-cliente-tres@sgspe.test"}]}
SECRETS = {"SGSPE_TENANT_PASSWORD_CLIENTE_UNO": "clave-uno-ficticia", "SGSPE_TENANT_PASSWORD_CLIENTE_DOS": "clave-dos-ficticia",
           "SGSPE_TENANT_PASSWORD_CLIENTE_TRES": "clave-tres-ficticia"}


class UpgradeTenantsTests(unittest.TestCase):
    def setUp(self):
        self.calls = []

    def inventory(self, data=INVENTORY):
        handle = tempfile.NamedTemporaryFile("w", suffix=".json", delete=False, encoding="utf-8")
        with handle:
            json.dump(data, handle)
        self.addCleanup(os.unlink, handle.name)
        return handle.name

    def steps(self, failing=None):
        """Los cuatro pasos; `failing` es (correo, paso) que responde 1."""
        def make(name):
            def run(argv):
                email = argv[argv.index("--email") + 1]
                self.calls.append((email, name))
                return 1 if failing == (email, name) else 0
            return (name, run)
        return tuple(make(n) for n in ("apply.py", "apply_roles.py", "apply.py --workflows", "load_ubigeo.py"))

    def run_cli(self, path, steps):
        out, err = io.StringIO(), io.StringIO()
        with mock.patch.dict(os.environ, SECRETS, clear=False), redirect_stdout(out), redirect_stderr(err):
            code = upgrade_tenants.main(["--inventory", path, "--core", "http://127.0.0.1:9"], steps=steps, ask=lambda p: "")
        return code, out.getvalue(), err.getvalue()

    def test_recorre_cada_tenant_con_su_administrador_y_en_orden(self):
        code, out, err = self.run_cli(self.inventory(), self.steps())
        self.assertEqual(code, 0, err)
        self.assertEqual(len(self.calls), 12)
        self.assertEqual([n for e, n in self.calls[:4]], ["apply.py", "apply_roles.py", "apply.py --workflows", "load_ubigeo.py"])
        self.assertEqual([e for e, _ in self.calls[::4]], [t["adminEmail"] for t in INVENTORY["tenants"]])
        self.assertIn("resumen: 3 de 3 tenants al día", out)

    def test_un_tenant_que_falla_no_detiene_a_los_demas_y_el_resumen_lo_nombra(self):
        code, out, err = self.run_cli(self.inventory(), self.steps(("admin-cliente-dos@sgspe.test", "apply_roles.py")))
        self.assertEqual(code, 1)
        self.assertEqual([e for e, n in self.calls if n == "apply.py"], [t["adminEmail"] for t in INVENTORY["tenants"]])
        self.assertIn("resumen: 2 de 3 tenants al día", out)
        self.assertIn("cliente-dos: falló apply_roles.py", out)
        for secret in SECRETS.values():
            self.assertNotIn(secret, out + err)

    def test_un_inventario_invalido_no_corre_nada(self):
        for data in ({}, {"tenants": []}, {"tenants": [{"slug": "Con Espacios", "adminEmail": "a@sgspe.test"}]},
                     {"tenants": [{"slug": "bien", "adminEmail": "sin-arroba"}]}):
            code, _, _ = self.run_cli(self.inventory(data), self.steps())
            self.assertEqual(code, 2, data)
        self.assertEqual(self.calls, [])

    def test_la_variable_de_la_contrasena_sale_del_slug(self):
        self.assertEqual(upgrade_tenants.password_variable("cliente-uno"), "SGSPE_TENANT_PASSWORD_CLIENTE_UNO")

    def test_el_ejemplo_versionado_es_un_inventario_valido_y_ficticio(self):
        tenants, errors = upgrade_tenants.load_inventory(os.path.join(os.path.dirname(os.path.abspath(__file__)), "tenants.example.json"))
        self.assertEqual(errors, [])
        self.assertTrue(all(email.endswith("@sgspe.test") for _, email in tenants))


if __name__ == "__main__":
    unittest.main()
