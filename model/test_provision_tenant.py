"""Pruebas de provision_tenant.py contra un FakeCore (security.md §4.1, ASM-109): el alta del tenant con las credenciales del
operador, los seis pasos en orden con las del administrador nuevo, las contraseñas fuera de la salida y un paso que falla.

Correr: cd model && python3 -m unittest -v test_provision_tenant
"""
import io
import os
import unittest
from contextlib import redirect_stderr, redirect_stdout
from unittest import mock

import provision_tenant
import tenant_steps
from fake_core import FakeCore

OPERATOR_SECRET = "clave-del-operador-ficticia"
ADMIN_SECRET = "clave-inicial-ficticia"


class ProvisionTenantTests(unittest.TestCase):
    def setUp(self):
        self.core = FakeCore()
        self.addCleanup(self.core.stop)
        self.calls = []

    def step(self, name, code=0):
        def run(argv):
            self.calls.append((name, argv))
            return code
        return (name, run)

    def steps(self, failing=None):
        names = ("apply.py", "apply_roles.py", "apply.py --workflows", "load_ubigeo.py", "load_indicators.py", "publish_layers.py")
        return tuple(self.step(n, 1 if n == failing else 0) for n in names)

    def run_cli(self, steps, env=None, core=None, extra=()):
        args = ["--core", (core or self.core).base_url, "--operator-email", "operador@sgspe.test", "--name", "Cliente Ejemplo",
                "--slug", "cliente-ejemplo", "--admin-email", "admin-cliente-ejemplo@sgspe.test", *extra]
        env = {"SGSPE_OPERATOR_PASSWORD": OPERATOR_SECRET, "SGSPE_TENANT_ADMIN_PASSWORD": ADMIN_SECRET} if env is None else env
        out, err = io.StringIO(), io.StringIO()
        with mock.patch.dict(os.environ, env, clear=False), redirect_stdout(out), redirect_stderr(err):
            code = provision_tenant.main(args, steps=steps, ask=lambda prompt: "")
        return code, out.getvalue(), err.getvalue()

    def test_crea_el_tenant_como_el_operador_y_corre_los_seis_pasos_como_su_administrador(self):
        code, out, err = self.run_cli(self.steps())
        self.assertEqual(code, 0, err)
        login = [b for m, p, _, b in self.core.requests if p == "/api/auth/login"]
        self.assertEqual(login, [{"email": "operador@sgspe.test", "password": OPERATOR_SECRET}])
        self.assertEqual(self.core.organizations, [{
            "name": "Cliente Ejemplo", "slug": "cliente-ejemplo", "adminEmail": "admin-cliente-ejemplo@sgspe.test",
            "adminPassword": ADMIN_SECRET, "adminDisplayName": "Administración del tenant"}])
        self.assertEqual([n for n, _ in self.calls], ["apply.py", "apply_roles.py", "apply.py --workflows", "load_ubigeo.py", "load_indicators.py", "publish_layers.py"])
        for _, argv in self.calls:
            self.assertEqual(argv[argv.index("--email") + 1], "admin-cliente-ejemplo@sgspe.test")
            self.assertEqual(argv[argv.index("--password") + 1], ADMIN_SECRET)
        self.assertIn("cambiar la contraseña inicial", out)

    def test_sin_geoserver_se_omite_solo_la_publicacion_de_las_capas(self):
        # §3.3 de la especificación de F6: un despliegue sin GeoServer aprovisiona igual, sin el último paso
        code, _, err = self.run_cli(self.steps(), extra=("--skip-layers",))
        self.assertEqual(code, 0, err)
        self.assertEqual([n for n, _ in self.calls], ["apply.py", "apply_roles.py", "apply.py --workflows", "load_ubigeo.py", "load_indicators.py"])

    def test_los_pasos_reales_terminan_con_la_publicacion_de_las_capas(self):
        names = ["apply.py", "apply_roles.py", "apply.py --workflows", "load_ubigeo.py", "load_indicators.py", "publish_layers.py"]
        self.assertEqual([n for n, _ in tenant_steps.STEPS], names)
        self.assertEqual(tenant_steps.LAYERS, "publish_layers.py")
        self.assertEqual([n for n, _ in tenant_steps.selected(tenant_steps.STEPS, True)], names[:-1])
        self.assertEqual(tenant_steps.selected(tenant_steps.STEPS, False), tenant_steps.STEPS)

    def test_el_aviso_de_fallo_repite_skip_layers_si_se_uso(self):
        code, _, err = self.run_cli(self.steps("apply_roles.py"), extra=("--skip-layers",))
        self.assertEqual(code, 1)
        self.assertIn("upgrade_tenants.py --skip-layers", err)
        _, _, err = self.run_cli(self.steps("apply_roles.py"))
        self.assertNotIn("--skip-layers", err)

    def test_ninguna_contrasena_sale_en_la_salida(self):
        for failing in (None, "apply_roles.py"):
            self.calls.clear()
            _, out, err = self.run_cli(self.steps(failing))
            for secret in (OPERATOR_SECRET, ADMIN_SECRET):
                self.assertNotIn(secret, out + err)

    def test_un_paso_que_falla_detiene_los_siguientes_y_lo_nombra(self):
        code, _, err = self.run_cli(self.steps("apply_roles.py"))
        self.assertEqual(code, 1)
        self.assertEqual([n for n, _ in self.calls], ["apply.py", "apply_roles.py"])
        self.assertIn("falló apply_roles.py", err)

    def test_sin_manage_tenants_no_crea_nada_ni_corre_pasos(self):
        refusing = FakeCore(refuse_organizations=True)
        self.addCleanup(refusing.stop)
        code, _, err = self.run_cli(self.steps(), core=refusing)
        self.assertEqual(code, 1)
        self.assertEqual(self.calls, [])
        self.assertIn("403", err)

    def test_sin_contrasenas_no_llama_a_core(self):
        code, _, _ = self.run_cli(self.steps(), env={"SGSPE_OPERATOR_PASSWORD": "", "SGSPE_TENANT_ADMIN_PASSWORD": ""})
        self.assertEqual(code, 2)
        self.assertEqual(self.core.requests, [])

    def test_la_contrasena_no_se_acepta_como_argumento(self):
        with redirect_stderr(io.StringIO()):
            code = provision_tenant.main(["--core", self.core.base_url, "--operator-email", "o@sgspe.test", "--name", "N",
                                          "--slug", "n", "--admin-email", "a@sgspe.test", "--password", "x"],
                                         steps=self.steps(), ask=lambda prompt: "")
        self.assertEqual(code, 2)
        self.assertEqual(self.core.requests, [])


if __name__ == "__main__":
    unittest.main()
