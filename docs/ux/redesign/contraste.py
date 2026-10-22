#!/usr/bin/env python3
"""Contraste WCAG 2.2 de los pares de tokens --sg-* que la interfaz usa, en claro y en oscuro.

Lee los valores de docs/ux/tokens.css (o del archivo que se pase) y falla si un par queda bajo su mínimo:
4.5:1 para texto, 3:1 para componentes de interfaz y foco (WCAG 1.4.3 y 1.4.11). Un alias
(--sg-border-control: var(--sg-border-strong)) se mide con el valor de su tema. Los pares informativos se
imprimen sin mínimo: ninguna regla de WCAG los pide.
Uso: python3 docs/ux/redesign/contraste.py [ruta/a/tokens.css]
"""
import re
import sys
from pathlib import Path

ruta = Path(sys.argv[1]) if len(sys.argv) > 1 else Path(__file__).resolve().parent.parent / 'tokens.css'
css = ruta.read_text(encoding='utf-8')


def bloque(selector_inicio):
    i = css.index(selector_inicio)
    return dict(re.findall(r'--(sg-[a-z0-9-]+):\s*(#[0-9a-fA-F]{6}|var\(--sg-[a-z0-9-]+\))', css[i:css.index('}', i)]))


def resolver(tema):
    alias = re.compile(r'var\(--(sg-[a-z0-9-]+)\)')
    return {k: tema[alias.fullmatch(v).group(1)] if alias.fullmatch(v) else v for k, v in tema.items()}


claro = resolver(bloque(':root {'))
oscuro = resolver({**bloque(':root {'), **bloque(':root[data-theme="dark"]')})


def luminancia(hexa):
    canales = [int(hexa[i:i + 2], 16) / 255 for i in (1, 3, 5)]
    lin = [c / 12.92 if c <= 0.04045 else ((c + 0.055) / 1.055) ** 2.4 for c in canales]
    return 0.2126 * lin[0] + 0.7152 * lin[1] + 0.0722 * lin[2]


def contraste(a, b):
    la, lb = sorted((luminancia(a), luminancia(b)), reverse=True)
    return (la + 0.05) / (lb + 0.05)


TEXTO, UI = 4.5, 3.0
PARES = [
    ('sg-text', 'sg-bg', TEXTO), ('sg-text', 'sg-surface', TEXTO),
    ('sg-text-secondary', 'sg-surface', TEXTO), ('sg-text-secondary', 'sg-bg', TEXTO),
    # sg-text-muted / sg-surface es también el texto de ejemplo de los campos, sin opacidad (U-41)
    ('sg-text-muted', 'sg-surface', TEXTO), ('sg-text-muted', 'sg-bg', TEXTO),
    ('sg-text-muted', 'sg-surface-sunken', TEXTO),
    ('sg-primary-text', 'sg-surface', TEXTO), ('sg-primary-text', 'sg-primary-soft', TEXTO),
    ('sg-on-primary', 'sg-primary', TEXTO), ('sg-on-primary', 'sg-primary-hover', TEXTO),
    ('sg-info', 'sg-info-soft', TEXTO), ('sg-success', 'sg-success-soft', TEXTO),
    ('sg-warning', 'sg-warning-soft', TEXTO), ('sg-danger', 'sg-danger-soft', TEXTO),
    ('sg-critical', 'sg-critical-soft', TEXTO), ('sg-neutral', 'sg-neutral-soft', TEXTO),
    ('sg-progress', 'sg-progress-soft', TEXTO), ('sg-ai', 'sg-ai-soft', TEXTO),
    ('sg-territory', 'sg-territory-soft', TEXTO), ('sg-danger', 'sg-surface', TEXTO),
    ('sg-focus', 'sg-surface', UI), ('sg-focus', 'sg-bg', UI),
    ('sg-border-strong', 'sg-surface', UI), ('sg-primary', 'sg-surface', UI),
    # DD-15: el límite de los campos sobre la superficie (su relleno) y sobre el fondo de la página (U-40)
    ('sg-border-control', 'sg-surface', UI), ('sg-border-control', 'sg-bg', UI),
    ('sg-border-control', 'sg-surface-sunken', UI),
    # el texto de lo que flota (menús, popovers, drawers y diálogos)
    ('sg-text', 'sg-surface-raised', TEXTO), ('sg-text-secondary', 'sg-surface-raised', TEXTO),
    ('sg-text-muted', 'sg-surface-raised', TEXTO), ('sg-primary-text', 'sg-surface-raised', TEXTO),
    ('sg-focus', 'sg-surface-raised', UI),
]

# lo que flota se separa de la página con su borde y su sombra; el tono solo ayuda en oscuro
INFORMATIVOS = [('sg-surface-raised', 'sg-surface'), ('sg-surface-raised', 'sg-bg')]

fallas = 0
print(f"{'par':44} {'claro':>7} {'oscuro':>7}  mínimo")
for a, b, minimo in PARES:
    fila = []
    for tema in (claro, oscuro):
        r = contraste(tema[a], tema[b])
        fila.append(r)
        fallas += r < minimo
    marca = '' if min(fila) >= minimo else '  <- bajo el mínimo'
    print(f'{a + " / " + b:44} {fila[0]:7.2f} {fila[1]:7.2f}  {minimo}:1{marca}')
for a, b in INFORMATIVOS:
    print(f'{a + " / " + b:44} {contraste(claro[a], claro[b]):7.2f} {contraste(oscuro[a], oscuro[b]):7.2f}  (informativo)')
print('ok' if not fallas else f'{fallas} par(es) bajo el mínimo')
sys.exit(1 if fallas else 0)
