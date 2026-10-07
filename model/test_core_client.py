"""Pruebas del tiempo de espera de core_client.py: toda llamada lleva un timeout y uno vencido sale como CoreError
con la URL (lo usan apply.py, apply_roles.py y load_ubigeo.py).

Correr: cd model && python3 -m unittest -v test_core_client
"""
import socket
import unittest
import urllib.error
from unittest import mock

import core_client
from core_client import Client, CoreError


class FakeResponse:
    status = 200

    def __enter__(self):
        return self

    def __exit__(self, *exc):
        return False

    def read(self):
        return b'{"ok": true}'


class TimeoutTests(unittest.TestCase):
    def test_cada_llamada_lleva_un_timeout_de_60_segundos(self):
        self.assertEqual(core_client.TIMEOUT_SECONDS, 60)
        with mock.patch("urllib.request.urlopen", return_value=FakeResponse()) as urlopen:
            Client("http://core.test").get("/api/objects")
        self.assertEqual(urlopen.call_args.kwargs.get("timeout"), 60)

    def test_un_socket_timeout_sale_como_core_error_con_la_url(self):
        with mock.patch("urllib.request.urlopen", side_effect=socket.timeout("timed out")):
            with self.assertRaises(CoreError) as caught:
                Client("http://core.test").post("/api/objects/x/records", {})
        self.assertIn("timed out", str(caught.exception))
        self.assertIn("http://core.test/api/objects/x/records", str(caught.exception))

    def test_un_timeout_envuelto_en_url_error_sale_igual(self):
        with mock.patch("urllib.request.urlopen", side_effect=urllib.error.URLError(socket.timeout("timed out"))):
            with self.assertRaises(CoreError) as caught:
                Client("http://core.test").get("/api/objects")
        self.assertIn("timed out", str(caught.exception))
        self.assertIn("http://core.test/api/objects", str(caught.exception))

    def test_un_servidor_que_nunca_responde_vence_el_timeout(self):
        # acepta la conexión en el backlog del sistema pero no lee ni contesta
        with socket.socket() as silent:
            silent.bind(("127.0.0.1", 0))
            silent.listen(1)
            url = f"http://127.0.0.1:{silent.getsockname()[1]}"
            with mock.patch.object(core_client, "TIMEOUT_SECONDS", 0.3):
                with self.assertRaises(CoreError) as caught:
                    Client(url).get("/api/objects")
        self.assertIn("timed out", str(caught.exception))
        self.assertIn(url + "/api/objects", str(caught.exception))

    def test_otro_fallo_de_conexion_tambien_nombra_la_url(self):
        with mock.patch("urllib.request.urlopen", side_effect=urllib.error.URLError(ConnectionRefusedError("refused"))):
            with self.assertRaises(CoreError) as caught:
                Client("http://core.test").get("/api/objects")
        self.assertIn("connection failed", str(caught.exception))
        self.assertIn("http://core.test/api/objects", str(caught.exception))


if __name__ == "__main__":
    unittest.main()
