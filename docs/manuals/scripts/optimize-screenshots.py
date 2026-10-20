#!/usr/bin/env python3
"""Reduce el peso de las capturas (assets/screenshots/**/*.png) con una paleta de 256 colores sin tramado: las pantallas
del portal tienen pocos colores y el texto queda nítido. Requiere Pillow; sin él, no hace nada y lo avisa.

  python3 docs/manuals/scripts/optimize-screenshots.py
"""
import glob
import os
import sys

try:
    from PIL import Image
except ImportError:
    print("optimize-screenshots: sin Pillow (pip install pillow); las capturas quedan sin optimizar")
    sys.exit(0)

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "assets", "screenshots")
before = after = 0
for path in sorted(glob.glob(os.path.join(ROOT, "**", "*.png"), recursive=True)):
    im = Image.open(path)
    before += os.path.getsize(path)
    if im.mode != "P":
        im = im.convert("RGB").quantize(colors=256, method=Image.Quantize.MEDIANCUT, dither=Image.Dither.NONE)
        im.save(path, optimize=True)
    after += os.path.getsize(path)
print(f"capturas: {before / 1e6:.1f} MB → {after / 1e6:.1f} MB")
