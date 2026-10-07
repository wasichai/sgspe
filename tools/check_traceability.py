#!/usr/bin/env python3
"""Toda RF, RNF, RN y RQ del análisis tiene su fila en la matriz de trazabilidad (RNF-MNT-009, roadmap F1).

Lee docs/requirements-analysis.md, donde cada requisito se define como la primera celda de una fila de tabla, y
docs/requirements-traceability.md, y nombra los IDs del análisis que no tienen fila en la matriz. Solo biblioteca estándar.

Correr: python3 tools/check_traceability.py [--analysis RUTA] [--matrix RUTA]
Salida: 0 si cada requisito tiene su fila; 1 si falta alguno (los nombra); 2 si un archivo falta.
"""
import argparse
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ROW_ID = re.compile(r"^\| ((?:RF|RNF|RN|RQ)-[A-Z0-9]+-\d{3}) \|", re.MULTILINE)


def row_ids(text):
    """Los IDs de requisito que abren una fila de tabla."""
    return set(ROW_ID.findall(text))


def missing(analysis, matrix):
    """Los IDs definidos en el análisis sin fila en la matriz, ordenados."""
    return sorted(row_ids(analysis) - row_ids(matrix))


def _read(path):
    try:
        with open(path, encoding="utf-8") as f:
            return f.read()
    except OSError as e:
        print(f"error: {path}: {e.strerror or e}", file=sys.stderr)
        return None


def main(argv=None):
    p = argparse.ArgumentParser(description="Comprueba que cada requisito del análisis tenga su fila en la matriz.")
    p.add_argument("--analysis", default=os.path.join(ROOT, "docs", "requirements-analysis.md"))
    p.add_argument("--matrix", default=os.path.join(ROOT, "docs", "requirements-traceability.md"))
    args = p.parse_args(sys.argv[1:] if argv is None else argv)
    analysis, matrix = _read(args.analysis), _read(args.matrix)
    if analysis is None or matrix is None:
        return 2
    absent = missing(analysis, matrix)
    for requirement in absent:
        print(f"sin fila en la matriz: {requirement}")
    if absent:
        return 1
    print(f"ok: {len(row_ids(analysis))} requisitos, todos con su fila")
    return 0


if __name__ == "__main__":
    sys.exit(main())
