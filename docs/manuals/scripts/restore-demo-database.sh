#!/usr/bin/env bash
# Restaura la base de demostración de SGSPE desde los artefactos de export-demo-database.sh, en una base NUEVA.
#
# Uso (desde cualquier carpeta):
#   docs/manuals/scripts/restore-demo-database.sh [--db sgspe_demo_restore] [--from DIR] [--format sql|dump] [--replace]
#
#   --db       base destino; debe empezar por sgspe_demo (por defecto sgspe_demo_restore)
#   --from     carpeta de los artefactos (por defecto docs/manuals/database/backups)
#   --format   sql: sgspe-demo.sql.gz con psql en una sola transacción (por defecto); dump: sgspe-demo.dump con pg_restore
#   --replace  BORRA antes la base destino si existe (irreversible; solo bases sgspe_demo*)
#
# Pasos: comprueba las sumas SHA-256 del manifiesto, crea la base vacía, carga el volcado (que trae la extensión
# PostGIS, los esquemas, las tablas, las restricciones, las secuencias y los datos), extrae los archivos en
# $SGSPE_DEMO_STATE_DIR/files/<base> y verifica el resultado contra database/manifests/sgspe-demo.json.
# Requisitos: psql (y pg_restore con --format dump) de una versión mayor o igual a la del volcado, gzip, tar y python3.
source "$(dirname "${BASH_SOURCE[0]}")/demo-env.sh"

DB="sgspe_demo_restore" FROM="$DEMO_DATABASE_DIR/backups" FORMAT="sql" REPLACE=0
while [[ $# -gt 0 ]]; do
  case "$1" in
    --db) DB="$2"; shift 2 ;;
    --from) FROM="$2"; shift 2 ;;
    --format) FORMAT="$2"; shift 2 ;;
    --replace) REPLACE=1; shift ;;
    -h | --help) sed -n '2,16p' "$0"; exit 0 ;;
    *) demo_die "argumento desconocido: $1" ;;
  esac
done
[[ "$FORMAT" == "sql" || "$FORMAT" == "dump" ]] || demo_die "--format debe ser sql o dump"

MANIFEST="$DEMO_DATABASE_DIR/manifests/sgspe-demo.json"
demo_require psql gzip tar python3
[[ "$FORMAT" == "dump" ]] && demo_require pg_restore
[[ -f "$MANIFEST" ]] || demo_die "falta el manifiesto $MANIFEST"
demo_assert_safe_db "$DB"
demo_server_check

demo_log "comprobando las sumas SHA-256 de los artefactos"
python3 "$DEMO_SCRIPTS_DIR/demo_manifest.py" checksums --manifest "$MANIFEST" --dir "$FROM" ||
  demo_die "los artefactos no coinciden con el manifiesto: no se restaura nada"

if demo_db_exists "$DB"; then
  [[ "$REPLACE" == "1" ]] || demo_die "la base $DB ya existe: elija otra con --db o use --replace para borrarla"
  demo_drop_db "$DB"
fi
demo_create_db "$DB"

started=$(date +%s)
if [[ "$FORMAT" == "sql" ]]; then
  demo_log "cargando sgspe-demo.sql.gz en una sola transacción"
  gunzip -c "$FROM/sgspe-demo.sql.gz" | psql -X -q -v ON_ERROR_STOP=1 --single-transaction -d "$DB" >/dev/null
else
  demo_log "restaurando sgspe-demo.dump con pg_restore"
  pg_restore --no-owner --no-privileges --exit-on-error --single-transaction -d "$DB" "$FROM/sgspe-demo.dump"
fi
demo_log "carga terminada en $(($(date +%s) - started)) s"

FILES_DIR="$(demo_files_dir "$DB")"
rm -rf "${FILES_DIR:?}"
mkdir -p "$FILES_DIR"
tar -xzf "$FROM/sgspe-demo-files.tar.gz" -C "$FILES_DIR"
demo_log "archivos restaurados en $FILES_DIR"

demo_log "verificando la base restaurada contra el manifiesto"
python3 "$DEMO_SCRIPTS_DIR/demo_manifest.py" verify --db "$DB" --manifest "$MANIFEST" --files-dir "$FILES_DIR"

cat >&2 <<EOF

Para usarla, arranque el servidor contra la base restaurada (no hace falta aprovisionar: el modelo ya está dentro):
  WASICHAI_DB_NAME=$DB WASICHAI_DB_PORT=$PGPORT WASICHAI_FILES_STORE=local WASICHAI_FILES_LOCAL_PATH=$FILES_DIR \\
    java -jar build/libs/app.jar
y compruébela por la API:
  SGSPE_DEMO_PASSWORD=… python3 docs/manuals/scripts/demo_manifest.py verify --db $DB \\
    --manifest docs/manuals/database/manifests/sgspe-demo.json --api http://localhost:8094
EOF
