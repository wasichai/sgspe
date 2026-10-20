# shellcheck shell=bash
# Configuración y salvaguardas comunes de los scripts de la base de demostración (se carga con `source`).
#
# Variables (todas opcionales):
#   SGSPE_DEMO_PGHOST       servidor PostgreSQL con PostGIS            (localhost)
#   SGSPE_DEMO_PGPORT       puerto                                     (5435, el de compose.yml)
#   SGSPE_DEMO_PGUSER       usuario con permiso para crear bases       (sgspe)
#   SGSPE_DEMO_PGPASSWORD   su contraseña                              (sgspe, la de desarrollo de compose.yml)
#   SGSPE_DEMO_DB           base de demostración                       (sgspe_demo)
#   SGSPE_DEMO_STATE_DIR    archivos y registros locales de la demo    ($XDG_STATE_HOME/sgspe-demo o ~/.local/state/sgspe-demo)
#   SGSPE_DEMO_ALLOW_REMOTE 1 para permitir un servidor que no es local (nunca uno de producción)
#
# Salvaguardas: los scripts solo crean, borran o restauran bases cuyo nombre empieza por `sgspe_demo` y solo en un
# servidor local, salvo SGSPE_DEMO_ALLOW_REMOTE=1. Nunca tocan la base `sgspe` de desarrollo ni la de las pruebas.

set -euo pipefail

DEMO_SCRIPTS_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DEMO_MANUALS_DIR="$(cd "$DEMO_SCRIPTS_DIR/.." && pwd)"
DEMO_REPO_DIR="$(cd "$DEMO_MANUALS_DIR/../.." && pwd)"
DEMO_DATABASE_DIR="$DEMO_MANUALS_DIR/database"

: "${SGSPE_DEMO_PGHOST:=localhost}"
: "${SGSPE_DEMO_PGPORT:=5435}"
: "${SGSPE_DEMO_PGUSER:=sgspe}"
: "${SGSPE_DEMO_PGPASSWORD:=sgspe}"
: "${SGSPE_DEMO_DB:=sgspe_demo}"
: "${SGSPE_DEMO_STATE_DIR:=${XDG_STATE_HOME:-$HOME/.local/state}/sgspe-demo}"

export PGHOST="$SGSPE_DEMO_PGHOST" PGPORT="$SGSPE_DEMO_PGPORT" PGUSER="$SGSPE_DEMO_PGUSER"
export PGPASSWORD="$SGSPE_DEMO_PGPASSWORD"

demo_log() { printf '\033[1;32m▸\033[0m %s\n' "$*" >&2; }
demo_warn() { printf '\033[1;33m!\033[0m %s\n' "$*" >&2; }
demo_die() { printf '\033[1;31m✗\033[0m %s\n' "$*" >&2; exit 1; }

demo_require() {
  local tool
  for tool in "$@"; do
    command -v "$tool" >/dev/null 2>&1 || demo_die "falta la herramienta «$tool» en el PATH"
  done
}

# demo_assert_safe_db NOMBRE: aborta si la base no es de demostración o si el servidor no es local.
demo_assert_safe_db() {
  local db="$1"
  [[ "$db" =~ ^sgspe_demo[a-z0-9_]*$ ]] ||
    demo_die "la base «$db» no es de demostración: los scripts solo operan sobre bases sgspe_demo*"
  case "$SGSPE_DEMO_PGHOST" in
    localhost | 127.0.0.1 | ::1) ;;
    *)
      [[ "${SGSPE_DEMO_ALLOW_REMOTE:-0}" == "1" ]] ||
        demo_die "el servidor $SGSPE_DEMO_PGHOST no es local; use SGSPE_DEMO_ALLOW_REMOTE=1 solo con uno desechable"
      ;;
  esac
}

demo_psql() { psql -X -q -v ON_ERROR_STOP=1 "$@"; }

demo_db_exists() {
  [[ "$(demo_psql -d postgres -tAc "SELECT 1 FROM pg_database WHERE datname = '$1'")" == "1" ]]
}

demo_server_check() {
  demo_psql -d postgres -tAc "SELECT 1" >/dev/null 2>&1 ||
    demo_die "no hay conexión con PostgreSQL en $PGHOST:$PGPORT como $PGUSER (¿está levantado? ¿túnel abierto?)"
  local avail
  avail="$(demo_psql -d postgres -tAc "SELECT count(*) FROM pg_available_extensions WHERE name = 'postgis'")"
  [[ "$avail" == "1" ]] || demo_die "el servidor $PGHOST:$PGPORT no tiene la extensión PostGIS instalada"
}

# demo_create_db NOMBRE: crea la base vacía con PostGIS (falla si ya existe).
demo_create_db() {
  local db="$1"
  demo_assert_safe_db "$db"
  demo_log "creando la base vacía $db con PostGIS"
  demo_psql -d postgres -c "CREATE DATABASE \"$db\" ENCODING 'UTF8' TEMPLATE template0"
  demo_psql -d "$db" -c "CREATE EXTENSION IF NOT EXISTS postgis"
}

# demo_drop_db NOMBRE: borra una base de demostración y cierra antes sus conexiones (irreversible).
demo_drop_db() {
  local db="$1"
  demo_assert_safe_db "$db"
  demo_warn "borrando la base $db (irreversible)"
  demo_psql -d postgres -c "SELECT pg_terminate_backend(pid) FROM pg_stat_activity WHERE datname = '$db' AND pid <> pg_backend_pid()" >/dev/null
  demo_psql -d postgres -c "DROP DATABASE IF EXISTS \"$db\""
}

demo_files_dir() { printf '%s/files/%s' "$SGSPE_DEMO_STATE_DIR" "$1"; }
