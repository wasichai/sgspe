#!/usr/bin/env bash
# Exporta la base de demostración y sus archivos a artefactos reproducibles, con su manifiesto.
#
# Uso (desde cualquier carpeta):
#   docs/manuals/scripts/export-demo-database.sh [--db sgspe_demo] [--out DIR] [--allow-active]
#
# Produce en DIR (por defecto docs/manuals/database/backups):
#   sgspe-demo.sql.gz        respaldo lógico COMPLETO en SQL plano: extensión, esquemas, tablas, índices, restricciones,
#                            secuencias y los datos como INSERT con nombres de columna (pg_dump --column-inserts)
#   sgspe-demo.dump          el mismo contenido en formato personalizado de pg_dump (pg_restore, restauración selectiva)
#   sgspe-demo-files.tar.gz  los archivos de wasichai-files (evidencias, borradores) del almacenamiento local
# y en docs/manuals/database/manifests/sgspe-demo.json el manifiesto: versiones, historia de migraciones, conteo de filas
# por tabla, secuencias, sumas SHA-256 y el orden de restauración.
#
# Requisitos: pg_dump y psql de una versión mayor o igual a la del servidor, python3, tar y gzip. No modifica la base.
# Para que el volcado y los conteos describan el mismo estado, se niega a exportar si hay otras sesiones abiertas en la
# base (el servidor de SGSPE escribe con sus trabajos programados); --allow-active omite esa comprobación.
source "$(dirname "${BASH_SOURCE[0]}")/demo-env.sh"

DB="$SGSPE_DEMO_DB" OUT="$DEMO_DATABASE_DIR/backups" ALLOW_ACTIVE=0
while [[ $# -gt 0 ]]; do
  case "$1" in
    --db) DB="$2"; shift 2 ;;
    --out) OUT="$2"; shift 2 ;;
    --allow-active) ALLOW_ACTIVE=1; shift ;;
    -h | --help) sed -n '2,19p' "$0"; exit 0 ;;
    *) demo_die "argumento desconocido: $1" ;;
  esac
done

demo_require psql pg_dump python3 tar gzip sha256sum
demo_assert_safe_db "$DB"
demo_server_check
demo_db_exists "$DB" || demo_die "no existe la base $DB (créela con seed-demo-data.sh)"

active="$(demo_psql -d postgres -tAc "SELECT count(*) FROM pg_stat_activity WHERE datname = '$DB' AND pid <> pg_backend_pid()")"
if [[ "$active" != "0" && "$ALLOW_ACTIVE" == "0" ]]; then
  demo_die "hay $active sesiones abiertas en $DB (¿el servidor sigue arriba?): deténgalo o use --allow-active"
fi

mkdir -p "$OUT" "$DEMO_DATABASE_DIR/manifests"
SQL="$OUT/sgspe-demo.sql.gz" DUMP="$OUT/sgspe-demo.dump" FILES="$OUT/sgspe-demo-files.tar.gz"
COMMON=(--no-owner --no-privileges --encoding=UTF8 --dbname="$DB")

demo_log "volcado SQL plano con INSERT → $SQL"
pg_dump "${COMMON[@]}" --format=plain --column-inserts --rows-per-insert=200 --quote-all-identifiers |
  gzip -n -9 >"$SQL.tmp"
mv "$SQL.tmp" "$SQL"

demo_log "volcado en formato personalizado → $DUMP"
pg_dump "${COMMON[@]}" --format=custom --compress=9 --file="$DUMP.tmp"
mv "$DUMP.tmp" "$DUMP"

FILES_DIR="$(demo_files_dir "$DB")"
demo_log "archivos de wasichai-files ($FILES_DIR) → $FILES"
mkdir -p "$FILES_DIR"
tar --sort=name --mtime='2000-01-01 00:00Z' --owner=0 --group=0 --numeric-owner -C "$FILES_DIR" -cf - . |
  gzip -n -9 >"$FILES.tmp"
mv "$FILES.tmp" "$FILES"

demo_log "manifiesto → database/manifests/sgspe-demo.json"
python3 "$DEMO_SCRIPTS_DIR/demo_manifest.py" write --db "$DB" --artifacts "$SQL" "$DUMP" "$FILES" \
  --files-dir "$FILES_DIR" --out "$DEMO_DATABASE_DIR/manifests/sgspe-demo.json"
ls -lh "$SQL" "$DUMP" "$FILES" >&2
demo_log "exportación terminada"
