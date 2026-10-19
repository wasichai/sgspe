"""Pruebas del envoltorio de sgspe-latex que no necesitan TeX: la validación del tar y los códigos de error.

Correr: cd develop/latex && python3 -m unittest -v
"""
import io
import os
import tarfile
import tempfile
import unittest

import server


def tar(entries):
    out = io.BytesIO()
    with tarfile.open(fileobj=out, mode="w") as archive:
        for name, data, kind in entries:
            info = tarfile.TarInfo(name)
            info.type = kind
            if kind == tarfile.SYMTYPE:
                info.linkname = "/etc/passwd"
            else:
                info.size = len(data)
            archive.addfile(info, io.BytesIO(data) if kind == tarfile.REGTYPE else None)
    return out.getvalue()


def regular(name, data=b"x"):
    return (name, data, tarfile.REGTYPE)


class UnpackTests(unittest.TestCase):
    def unpack(self, body):
        with tempfile.TemporaryDirectory() as work:
            names = server.unpack(body, work)
            return names, sorted(os.listdir(work))

    def code(self, body):
        with self.assertRaises(server.Failure) as ctx:
            self.unpack(body)
        return ctx.exception.code

    def test_extrae_main_la_clase_y_las_imagenes(self):
        names, top = self.unpack(tar([regular("main.tex"), regular("sgspe-informe.cls"), regular("img/foto-1.jpg")]))
        self.assertEqual(["main.tex", "sgspe-informe.cls", "img/foto-1.jpg"], names)
        self.assertEqual(["img", "main.tex", "sgspe-informe.cls"], top)

    def test_rechaza_rutas_absolutas_subidas_enlaces_y_nombres_raros(self):
        self.assertEqual("BAD_ENTRY", self.code(tar([regular("main.tex"), regular("/etc/x.tex")])))
        self.assertEqual("BAD_ENTRY", self.code(tar([regular("main.tex"), regular("img/../../x.tex")])))
        self.assertEqual("BAD_ENTRY", self.code(tar([regular("main.tex"), ("img/l.png", b"", tarfile.SYMTYPE)])))
        self.assertEqual("BAD_ENTRY", self.code(tar([regular("main.tex"), regular("img/a b.png")])))

    def test_sin_main_o_sin_tar(self):
        self.assertEqual("NO_MAIN", self.code(tar([regular("otro.tex")])))
        self.assertEqual("NOT_A_TAR", self.code(b"no es un tar"))

    def test_tope_de_entradas(self):
        original = server.MAX_ENTRIES
        server.MAX_ENTRIES = 2
        try:
            self.assertEqual("TOO_MANY_ENTRIES", self.code(tar([regular("main.tex"), regular("a.png"), regular("b.png")])))
        finally:
            server.MAX_ENTRIES = original


class LogTests(unittest.TestCase):
    def write(self, text):
        f = tempfile.NamedTemporaryFile("w", delete=False, suffix=".log")
        f.write(text)
        f.close()
        self.addCleanup(os.unlink, f.name)
        return f.name

    def test_la_linea_del_error_sin_su_texto(self):
        log = self.write("algo\n./main.tex:42: Undefined control sequence.\nl.42 \\foo texto privado\n")
        self.assertEqual(42, server.error_line(log))
        self.assertIsNone(server.error_line(self.write("sin errores\n")))

    def test_cuenta_las_cajas_desbordadas(self):
        self.assertEqual(2, server.overfull(self.write("Overfull \\hbox (1pt)\nx\nOverfull \\hbox (2pt)\nUnderfull \\hbox\n")))

    def test_el_entorno_no_permite_shell_escape_ni_escribir_fuera(self):
        env = server.environment()
        self.assertEqual("f", env["shell_escape"])
        self.assertEqual("p", env["openout_any"])
        self.assertNotIn("TEXMFOUTPUT", env)


if __name__ == "__main__":
    unittest.main()
