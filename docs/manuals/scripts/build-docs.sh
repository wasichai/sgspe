#!/usr/bin/env bash
# Construye los manuales de SGSPE en HTML y PDF a partir de las fuentes AsciiDoc.
#
# Uso (desde cualquier carpeta):
#   docs/manuals/scripts/build-docs.sh [all|html|pdf|references|diagrams|clean]
#
#   all         referencias + diagramas + html + pdf (por defecto)
#   html        HTML5 en docs/manuals/output/html (abrir output/html/index.html)
#   pdf         PDF en docs/manuals/output/pdf/<manual>/<manual>.pdf
#   references  regenera reference/generated/ desde model/ (generate-reference.py)
#   diagrams    renderiza assets/diagrams/*.mmd que cambiaron (Node + Playwright)
#   clean       borra docs/manuals/output (solo artefactos generados)
#
# Requisitos: Ruby con las gemas del Gemfile (bundle install), Python 3; para diagramas, Node 22+ y `yarn install` en
# docs/manuals. La versión del producto sale de gradle.properties y la fecha de edición de SOURCE_DATE_EPOCH o de hoy.
# Termina con error ante cualquier advertencia de Asciidoctor (inclusión, imagen o referencia rota).
set -euo pipefail

MANUALS="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
REPO="$(cd "$MANUALS/../.." && pwd)"
OUT="$MANUALS/output"
TARGET="${1:-all}"

VERSION="$(sed -n 's/^version=//p' "$REPO/gradle.properties")"
if [[ -n "${SOURCE_DATE_EPOCH:-}" ]]; then DOC_DATE="$(date -u -d "@$SOURCE_DATE_EPOCH" +%F)"; else DOC_DATE="$(date +%F)"; fi
ATTRS=(-a "product-version=$VERSION" -a "doc-date=$DOC_DATE")

# manuales (ruta de la fuente relativa a docs/manuals)
BOOKS=(user/manual-usuario.adoc administrator/manual-administrador.adoc system/manual-sistema.adoc
  reference/referencia-tecnica.adoc)

log() { printf '\033[1;32m▸\033[0m %s\n' "$*" >&2; }

asciidoctor_cmd() {
  # usa las versiones fijadas del Gemfile si bundler las encuentra; si no, las gemas instaladas
  local tool="$1"
  shift
  if command -v bundle >/dev/null 2>&1 && BUNDLE_GEMFILE="$MANUALS/Gemfile" bundle check >/dev/null 2>&1; then
    BUNDLE_GEMFILE="$MANUALS/Gemfile" bundle exec "$tool" "$@"
  else
    "$tool" "$@"
  fi
}

references() {
  log "referencias desde model/"
  python3 "$MANUALS/scripts/generate-reference.py"
}

diagrams() {
  log "diagramas Mermaid"
  (cd "$MANUALS" && node scripts/render-diagrams.mjs)
}

copy_static() {
  mkdir -p "$OUT/html/styles/html" "$OUT/html/styles/fonts" "$OUT/html/assets"
  cp "$MANUALS/styles/html/documentation.css" "$OUT/html/styles/html/"
  rm -rf "${OUT:?}/html/styles/fonts/woff2" && cp -r "$MANUALS/styles/fonts/woff2" "$OUT/html/styles/fonts/"
  cp "$MANUALS/styles/fonts/OFL-IBM-Plex.txt" "$OUT/html/styles/fonts/"
  for d in screenshots diagrams branding; do
    rm -rf "${OUT:?}/html/assets/${d:?}"
    mkdir -p "$OUT/html/assets/$d"
    # sin cp --parents (solo GNU): el cp de macOS no lo tiene
    (cd "$MANUALS/assets/$d" && find . -type f \( -name '*.png' -o -name '*.svg' -o -name '*.jpg' \) |
      while IFS= read -r f; do mkdir -p "$OUT/html/assets/$d/$(dirname "$f")" && cp "$f" "$OUT/html/assets/$d/$f"; done)
  done
}

html() {
  log "HTML ($VERSION, $DOC_DATE)"
  copy_static
  asciidoctor_cmd asciidoctor "${ATTRS[@]}" --failure-level WARN \
    -D "$OUT/html" -o index.html "$MANUALS/index.adoc"
  for book in "${BOOKS[@]}"; do
    asciidoctor_cmd asciidoctor "${ATTRS[@]}" --failure-level WARN \
      -D "$OUT/html/$(dirname "$book")" "$MANUALS/$book"
  done
  log "HTML listo: $OUT/html/index.html"
}

pdf() {
  log "PDF ($VERSION, $DOC_DATE)"
  for book in "${BOOKS[@]}"; do
    local name
    name="$(basename "$book" .adoc)"
    asciidoctor_cmd asciidoctor-pdf "${ATTRS[@]}" --failure-level WARN \
      -D "$OUT/pdf/$(dirname "$book")" "$MANUALS/$book"
    log "  $(dirname "$book")/$name.pdf"
  done
}

case "$TARGET" in
  all) references; diagrams; html; pdf ;;
  html) html ;;
  pdf) pdf ;;
  references) references ;;
  diagrams) diagrams ;;
  clean) rm -rf "${OUT:?}"; log "borrado $OUT" ;;
  -h | --help) sed -n '2,17p' "$0" ;;
  *) echo "objetivo desconocido: $TARGET (all|html|pdf|references|diagrams|clean)" >&2; exit 2 ;;
esac
