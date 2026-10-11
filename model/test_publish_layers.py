"""Pruebas de publish_layers.py (gis.md §7.1 y §7.2, security.md §10.2): la lista de capas, sus SLD y la paleta se validan
sin red; las capas que faltan se publican por core como el administrador del tenant y los estilos se crean o actualizan
en GeoServer con las credenciales del despliegue, que llegan solo por el entorno y nunca salen; una segunda corrida no
escribe.

Correr: cd model && python3 -m unittest -v test_publish_layers
"""
import copy
import io
import json
import os
import shutil
import tempfile
import unittest
from contextlib import redirect_stderr, redirect_stdout
from unittest import mock

import publish_layers
from fake_core import FakeCore
from fake_geoserver import FakeGeoServer

HERE = os.path.dirname(os.path.abspath(__file__))
STYLES_DIR = os.path.join(HERE, "styles")
USER = "geoserver-admin-ficticio"
SECRET = "clave-geoserver-ficticia"


def load(*parts):
    with open(os.path.join(HERE, *parts), encoding="utf-8") as f:
        return json.load(f)


LAYERS = load("layers.json")
MODEL = load("model.json")
ROLES = load("roles.json")
PALETTE = load("styles", "palette.json")
PAIRS = list(dict.fromkeys((e["object"], e["geometry"]) for e in LAYERS))
STYLES = [e["style"] for e in LAYERS]


def sld(style):
    with open(os.path.join(STYLES_DIR, f"{style}.sld"), encoding="utf-8") as f:
        return f.read()


class ValidationTests(unittest.TestCase):
    def errors(self, layers=None, palette=None, styles_dir=STYLES_DIR):
        return publish_layers.validate(LAYERS if layers is None else layers, MODEL, ROLES,
                                       PALETTE if palette is None else palette, styles_dir)

    def mutated(self, index, **changes):
        layers = copy.deepcopy(LAYERS)
        layers[index].update(changes)
        return layers

    def styles_copy(self):
        folder = os.path.join(tempfile.mkdtemp(), "styles")
        self.addCleanup(shutil.rmtree, os.path.dirname(folder))
        shutil.copytree(STYLES_DIR, folder)
        return folder

    def test_las_capas_y_los_estilos_versionados_son_validos(self):
        self.assertEqual(self.errors(), [])
        self.assertEqual((len(PAIRS), len(STYLES)), (8, 9))

    def test_la_particion_del_modelo_es_la_de_project_scope(self):
        # Decisión 7 del plan 6.1: lo que no llega a project por sus campos RELATION son los 20 maestros del tenant
        names = {o["name"] for o in MODEL["objects"]}
        tenant = names - publish_layers.project_bound(MODEL)
        self.assertEqual(len(tenant), 20)
        self.assertLessEqual({e["object"] for e in LAYERS}, tenant)

    def test_un_objeto_de_proyecto_no_se_publica(self):
        for name, geometry in (("area_of_influence", "boundary"), ("project", "interest_area"),
                               ("project_component", "footprint"), ("grievance", "location")):
            with self.subTest(name):
                errors = self.errors(self.mutated(0, object=name, geometry=geometry))
                self.assertTrue(any("objeto de proyecto" in e for e in errors), errors)

    def test_un_objeto_con_campos_restringidos_no_se_publica(self):
        errors = self.errors(self.mutated(0, object="land_parcel", geometry="boundary"))
        self.assertTrue(any("campos restringidos" in e for e in errors), errors)
        self.assertFalse(any("objeto de proyecto" in e for e in errors), errors)

    def test_la_geometria_y_la_etiqueta_deben_existir_en_el_objeto(self):
        self.assertTrue(any("no es una geometría" in e for e in self.errors(self.mutated(0, geometry="name"))))
        self.assertTrue(any("campo TEXT" in e for e in self.errors(self.mutated(0, label="boundary"))))

    def test_un_estilo_repetido_o_mal_nombrado_una_clave_desconocida_o_un_id_malo_se_rechazan(self):
        repeated = copy.deepcopy(LAYERS)
        repeated[1]["style"] = repeated[0]["style"]
        self.assertTrue(any("se repite" in e for e in self.errors(repeated)))
        self.assertTrue(any("nombre de estilo" in e for e in self.errors(self.mutated(0, style="Estilo 1"))))
        self.assertTrue(any("claves" in e for e in self.errors(self.mutated(0, filter="name = 'x'"))))
        self.assertTrue(any("LYR-NN" in e for e in self.errors(self.mutated(0, layer="LYR-1"))))

    def test_un_sld_que_falta_no_es_xml_no_se_llama_como_su_estilo_o_no_dibuja_su_geometria_se_rechaza(self):
        folder = self.styles_copy()
        os.remove(os.path.join(folder, "sgspe-lyr-04.sld"))
        with open(os.path.join(folder, "sgspe-lyr-05.sld"), "w", encoding="utf-8") as f:
            f.write("<StyledLayerDescriptor")
        with open(os.path.join(folder, "sgspe-lyr-01.sld"), "w", encoding="utf-8") as f:
            f.write(sld("sgspe-lyr-01").replace("<Name>sgspe-lyr-01</Name>", "<Name>otro</Name>"))
        with open(os.path.join(folder, "sgspe-lyr-07.sld"), "w", encoding="utf-8") as f:
            f.write(sld("sgspe-lyr-07").replace("PolygonSymbolizer", "LineSymbolizer"))
        errors = self.errors(styles_dir=folder)
        for expected in ("falta model/styles/sgspe-lyr-04.sld", "sgspe-lyr-05.sld no es XML",
                         "el UserStyle de sgspe-lyr-01.sld", "sgspe-lyr-07.sld no dibuja"):
            self.assertTrue(any(expected in e for e in errors), (expected, errors))

    def test_cada_sld_usa_exactamente_los_colores_y_la_etiqueta_de_su_estilo(self):
        folder = self.styles_copy()
        with open(os.path.join(folder, "sgspe-lyr-07.sld"), "w", encoding="utf-8") as f:
            f.write(sld("sgspe-lyr-07").replace("#9a6a00", "#000000"))
        with open(os.path.join(folder, "sgspe-lyr-05.sld"), "w", encoding="utf-8") as f:
            f.write(sld("sgspe-lyr-05").replace("<ogc:PropertyName>name</ogc:PropertyName>",
                                                "<ogc:PropertyName>property_record</ogc:PropertyName>"))
        errors = self.errors(styles_dir=folder)
        self.assertTrue(any("sgspe-lyr-07.sld usa los colores" in e for e in errors), errors)
        self.assertTrue(any("sgspe-lyr-05.sld rotula" in e for e in errors), errors)

    def test_la_paleta_y_las_capas_van_de_la_mano(self):
        palette = copy.deepcopy(PALETTE)
        del palette["styles"]["sgspe-lyr-04"]
        palette["styles"]["sgspe-lyr-99"] = {"layer": "LYR-99", "stroke": "#000000"}
        palette["styles"]["sgspe-lyr-05"]["layer"] = "LYR-06"
        errors = self.errors(palette=palette)
        for expected in ("palette.json no tiene los colores de sgspe-lyr-04", "sgspe-lyr-99 no es el estilo de ninguna capa",
                         "palette.json da sgspe-lyr-05 a LYR-06"):
            self.assertTrue(any(expected in e for e in errors), (expected, errors))


class PublishTests(unittest.TestCase):
    def setUp(self):
        # una capa que no es pública (project.interest_area) también existe en el tenant: nunca se publica
        self.core = FakeCore(gis_layers=PAIRS + [("project", "interest_area")])
        self.addCleanup(self.core.stop)
        self.geoserver = FakeGeoServer(USER, SECRET)
        self.addCleanup(self.geoserver.stop)

    def env(self, **overrides):
        values = {"WASICHAI_GIS_GEOSERVER_URL": self.geoserver.base_url, "WASICHAI_GIS_GEOSERVER_USERNAME": USER,
                  "WASICHAI_GIS_GEOSERVER_PASSWORD": SECRET}
        values.update(overrides)
        return {k: v for k, v in values.items() if v is not None}

    def run_cli(self, *extra, env=None, core=None):
        args = ["--core", (core or self.core).base_url, "--email", "admin-tenant@sgspe.test", "--password",
                "clave-del-tenant", *extra]
        out, err = io.StringIO(), io.StringIO()
        with mock.patch.dict(os.environ, self.env() if env is None else env, clear=True), \
                redirect_stdout(out), redirect_stderr(err):
            code = publish_layers.main(args)
        return code, out.getvalue(), err.getvalue()

    def published(self, core=None):
        return [p for m, p, _, _ in (core or self.core).requests if m == "POST" and p.startswith("/api/gis/")]

    def test_publica_las_capas_que_faltan_y_carga_los_estilos(self):
        code, out, err = self.run_cli()
        self.assertEqual(code, 0, err)
        self.assertEqual(self.published(), [f"/api/gis/layers/{o}/{g}" for o, g in PAIRS])
        self.assertFalse(self.core.gis_layers[("project", "interest_area")])
        self.assertEqual(self.geoserver.styles, {style: sld(style) for style in STYLES})
        self.assertTrue(all(ct == "application/vnd.ogc.sld+xml" for m, _, _, ct, _ in self.geoserver.requests if m != "GET"))
        self.assertIn("done: 8 layers (8 published, 0 skipped), 9 styles (9 created, 0 updated, 0 skipped)", out)

    def test_una_segunda_corrida_no_escribe(self):
        self.assertEqual(self.run_cli()[0], 0)
        self.core.requests.clear()
        self.geoserver.requests.clear()
        code, out, err = self.run_cli()
        self.assertEqual(code, 0, err)
        self.assertEqual(self.published(), [])
        self.assertEqual(self.geoserver.writes(), [])
        self.assertIn("done: 8 layers (0 published, 8 skipped), 9 styles (0 created, 0 updated, 9 skipped)", out)

    def test_un_estilo_distinto_se_actualiza_y_uno_igual_con_otro_formato_no(self):
        self.geoserver.styles[STYLES[0]] = sld(STYLES[1])  # otro contenido
        # el mismo SLD reindentado y sin su comentario: GeoServer pudo reescribirlo al guardarlo
        same = sld(STYLES[2]).replace("\n  ", "\n    ")
        self.geoserver.styles[STYLES[2]] = same.split("-->", 1)[1]
        code, out, err = self.run_cli()
        self.assertEqual(code, 0, err)
        self.assertIn(("PUT", f"/geoserver/rest/styles/{STYLES[0]}?raw=true"), self.geoserver.writes())
        self.assertNotIn(STYLES[2], [p.split("/")[-1].split("?")[0] for _, p in self.geoserver.writes()])
        self.assertIn("9 styles (7 created, 1 updated, 1 skipped)", out)

    def test_dry_run_consulta_y_no_escribe(self):
        code, out, err = self.run_cli("--dry-run")
        self.assertEqual(code, 0, err)
        self.assertEqual(self.published(), [])
        self.assertEqual(self.geoserver.writes(), [])
        self.assertIn("dry-run: 8 layers (8 published, 0 skipped), 9 styles (9 created, 0 updated, 0 skipped)", out)

    def test_validate_only_no_usa_la_red_ni_las_credenciales(self):
        code, out, err = self.run_cli("--validate-only", env={})
        self.assertEqual(code, 0, err)
        self.assertIn("valid: 8 layers, 9 styles", out)
        self.assertEqual((self.core.requests, self.geoserver.requests), ([], []))

    def test_las_credenciales_de_geoserver_llegan_solo_por_el_entorno_y_no_salen(self):
        # sin la contraseña: 2, antes de llamar a nadie, nombrando la variable y la salida sin GeoServer
        code, _, err = self.run_cli(env=self.env(WASICHAI_GIS_GEOSERVER_PASSWORD=None))
        self.assertEqual(code, 2)
        self.assertIn("WASICHAI_GIS_GEOSERVER_PASSWORD", err)
        self.assertIn("--skip-layers", err)
        self.assertEqual((self.core.requests, self.geoserver.requests), ([], []))
        # nunca por argumento
        code, out, err = self.run_cli("--geoserver-password", SECRET)
        self.assertEqual(code, 2)
        self.assertNotIn(SECRET, out + err)
        # con ellas, cada llamada a GeoServer lleva la autenticación básica y la contraseña no sale
        code, out, err = self.run_cli()
        self.assertEqual(code, 0, err)
        self.assertTrue(all(auth == self.geoserver.expected for _, _, auth, _, _ in self.geoserver.requests))
        self.assertNotIn(SECRET, out + err)
        # una contraseña equivocada: 1, sin la contraseña en la salida
        code, out, err = self.run_cli(env=self.env(WASICHAI_GIS_GEOSERVER_PASSWORD="otra-clave-ficticia"))
        self.assertEqual(code, 1)
        self.assertIn("401", err)
        self.assertNotIn("otra-clave-ficticia", out + err)

    def test_core_sin_geoserver_falla_nombrando_la_capa(self):
        core = FakeCore(gis_layers=PAIRS, fail_on_publish="community")
        self.addCleanup(core.stop)
        code, _, err = self.run_cli(core=core)
        self.assertEqual(code, 1)
        self.assertIn("community.territory", err)
        self.assertIn("502", err)
        self.assertEqual(self.geoserver.writes(), [])

    def test_un_tenant_sin_la_geometria_falla_antes_de_publicar(self):
        core = FakeCore(gis_layers=PAIRS[1:])
        self.addCleanup(core.stop)
        code, _, err = self.run_cli(core=core)
        self.assertEqual(code, 1)
        self.assertIn(f"{PAIRS[0][0]}.{PAIRS[0][1]}", err)
        self.assertIn("apply.py", err)
        self.assertEqual(self.published(core), [])

    def test_geoserver_caido_falla_sin_traza(self):
        code, _, err = self.run_cli(env=self.env(WASICHAI_GIS_GEOSERVER_URL="http://127.0.0.1:9/geoserver"))
        self.assertEqual(code, 1)
        self.assertIn("GeoServer no responde", err)
        self.assertNotIn("Traceback", err)

    def test_una_url_de_geoserver_con_usuario_o_sin_esquema_sale_con_2_sin_filtrar_ni_traza(self):
        host = self.geoserver.base_url.split("//", 1)[1]
        for url in (f"http://usuario:{SECRET}@{host}", f"http://usuario:{SECRET}@127.0.0.1:9/geoserver",
                    f"127.0.0.1:{self.geoserver.port}/geoserver", "ftp://127.0.0.1/geoserver", "http:///geoserver"):
            with self.subTest(url.replace(SECRET, "***")):
                code, out, err = self.run_cli(env=self.env(WASICHAI_GIS_GEOSERVER_URL=url))
                self.assertEqual(code, 2)
                self.assertIn("WASICHAI_GIS_GEOSERVER_URL", err)
                self.assertNotIn(SECRET, out + err)
                self.assertNotIn("Traceback", out + err)
        self.assertEqual((self.core.requests, self.geoserver.requests), ([], []))

    def test_una_url_con_caracter_de_control_sale_con_1_sin_traza(self):
        url = self.geoserver.base_url + "/geo\x01server"
        code, out, err = self.run_cli(env=self.env(WASICHAI_GIS_GEOSERVER_URL=url))
        self.assertIn(code, (1, 2))
        self.assertNotIn("Traceback", out + err)
        self.assertNotIn(SECRET, out + err)

    def test_la_contrasena_de_geoserver_no_se_recorta(self):
        code, _, err = self.run_cli(env=self.env(WASICHAI_GIS_GEOSERVER_PASSWORD=" " + SECRET + " "))
        self.assertEqual(code, 1)
        self.assertIn("401", err)

    def test_un_core_sin_wasichai_gis_lo_dice(self):
        class Missing(FakeCore):
            def _gis(self, method, path):
                return 404, {"detail": "not found"}

        core = Missing()
        self.addCleanup(core.stop)
        code, _, err = self.run_cli(core=core)
        self.assertEqual(code, 1)
        self.assertIn("/api/gis/layers", err)
        self.assertIn("wasichai-gis", err)
        self.assertNotIn("apply.py", err)

    def test_un_layers_json_con_tipos_malos_sale_con_2_sin_traza(self):
        for change in ({"object": 7}, {"label": 3}, {"geometry": ["boundary"]}):
            with self.subTest(change):
                bad = copy.deepcopy(LAYERS)
                bad[0].update(change)
                self.assertTrue(publish_layers.validate(bad, MODEL, ROLES, PALETTE, STYLES_DIR))
        model = copy.deepcopy(MODEL)
        field = next(f for o in model["objects"] if o["name"] == LAYERS[0]["object"] for f in o["fields"]
                     if f["name"] == LAYERS[0]["geometry"])
        del field["geometryType"]
        errors = publish_layers.validate(LAYERS, model, ROLES, PALETTE, STYLES_DIR)
        self.assertTrue(any("no es una geometría" in e for e in errors), errors)

    def test_un_layers_json_invalido_sale_con_2_sin_llamar(self):
        layers = copy.deepcopy(LAYERS)
        layers.append({"layer": "LYR-14", "object": "land_parcel", "geometry": "boundary", "style": "sgspe-lyr-14"})
        with tempfile.TemporaryDirectory() as folder:
            path = os.path.join(folder, "layers.json")
            with open(path, "w", encoding="utf-8") as f:
                json.dump(layers, f)
            code, out, _ = self.run_cli("--layers", path)
        self.assertEqual(code, 2)
        self.assertIn("campos restringidos", out)
        self.assertEqual((self.core.requests, self.geoserver.requests), ([], []))


if __name__ == "__main__":
    unittest.main()
