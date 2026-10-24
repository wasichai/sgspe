#!/usr/bin/env bash
# Construye la base de demostración de SGSPE desde una base vacía, solo con los mecanismos oficiales del proyecto:
#
#   1. crea la base sgspe_demo vacía con PostGIS (con --reset la borra antes; solo bases sgspe_demo*)
#   2. arranca el servidor contra ella: las migraciones Flyway de wasichai y de SGSPE crean el esquema
#   3. aprovisiona el tenant: apply.py → apply_roles.py → apply.py --workflows → load_ubigeo.py → load_indicators.py
#      (publish_layers.py se omite: necesita GeoServer)
#   4. carga el conjunto ficticio oficial de model/demo con load_demo.py (idempotente: repetirlo no duplica registros)
#   5. salvo --minimal, carga el conjunto ampliado de docs/manuals/database/seeds/ampliado (QCS, aportes, programas,
#      incidentes, alertas, tareas y 4 usuarios más) y sube 4 evidencias sintéticas (seed-demo-evidence.py)
#   6. detiene el servidor, salvo --keep-running
#
# Uso (desde cualquier carpeta):
#   SGSPE_DEMO_PASSWORD='…' docs/manuals/scripts/seed-demo-data.sh [--reset] [--keep-running] [--minimal] [--draft]
#
# Requisitos: PostgreSQL con PostGIS (SGSPE_DEMO_PG*, ver demo-env.sh), JDK 25, Python 3.11+, curl y el jar del
# servidor (SGSPE_JAR, por defecto build/libs/app.jar: ./gradlew bootJar). La contraseña de los usuarios ficticios
# llega solo por SGSPE_DEMO_PASSWORD; nunca se escribe en un archivo.
#
# Efectos: crea o reutiliza la base $SGSPE_DEMO_DB, escribe los archivos de las evidencias y los registros del
# servidor en $SGSPE_DEMO_STATE_DIR y, con --reset, BORRA la base de demostración anterior y sus archivos.
source "$(dirname "${BASH_SOURCE[0]}")/demo-env.sh"

# DRAFT se expande con ${DRAFT[@]+…}: en bash 3.2 (macOS), un arreglo vacío con `set -u` aborta
RESET=0 KEEP=0 MINIMAL=0 DRAFT=()
for arg in "$@"; do
  case "$arg" in
    --reset) RESET=1 ;;
    --keep-running) KEEP=1 ;;
    --minimal) MINIMAL=1 ;;
    --draft) DRAFT=(--draft) ;;
    -h | --help) sed -n '2,23p' "$0"; exit 0 ;;
    *) demo_die "argumento desconocido: $arg" ;;
  esac
done

: "${SGSPE_JAR:=$DEMO_REPO_DIR/build/libs/app.jar}"
: "${SGSPE_DEMO_PORT:=8094}"
: "${SGSPE_DEMO_JAVA:=java}"
[[ -n "${SGSPE_DEMO_PASSWORD:-}" ]] || demo_die "falta SGSPE_DEMO_PASSWORD (contraseña de los usuarios ficticios; 12+ caracteres)"
demo_require psql curl python3 "$SGSPE_DEMO_JAVA"
[[ -f "$SGSPE_JAR" ]] || demo_die "no existe el jar $SGSPE_JAR: constrúyalo con ./gradlew bootJar o indique SGSPE_JAR"
demo_assert_safe_db "$SGSPE_DEMO_DB"
demo_server_check

CORE="http://localhost:$SGSPE_DEMO_PORT"
if curl -fsS -o /dev/null "$CORE/api/health" 2>/dev/null; then
  demo_die "ya hay un servidor en $CORE: deténgalo o use otro SGSPE_DEMO_PORT (no se sabe a qué base apunta)"
fi

FILES_DIR="$(demo_files_dir "$SGSPE_DEMO_DB")"
LOG_DIR="$SGSPE_DEMO_STATE_DIR/logs"
mkdir -p "$LOG_DIR"
if demo_db_exists "$SGSPE_DEMO_DB"; then
  if [[ "$RESET" == "1" ]]; then
    demo_drop_db "$SGSPE_DEMO_DB"
    rm -rf "${FILES_DIR:?}"
    demo_create_db "$SGSPE_DEMO_DB"
  else
    demo_log "la base $SGSPE_DEMO_DB ya existe: se completa (la carga es idempotente; --reset empieza de cero)"
  fi
else
  demo_create_db "$SGSPE_DEMO_DB"
fi
mkdir -p "$FILES_DIR"

LOG="$LOG_DIR/server-$SGSPE_DEMO_DB.log"
demo_log "arrancando el servidor en $CORE contra $SGSPE_DEMO_DB (registro en $LOG)"
(
  export WASICHAI_DB_HOST="$SGSPE_DEMO_PGHOST" WASICHAI_DB_PORT="$SGSPE_DEMO_PGPORT" WASICHAI_DB_NAME="$SGSPE_DEMO_DB"
  export WASICHAI_DB_USERNAME="$SGSPE_DEMO_PGUSER" WASICHAI_DB_PASSWORD="$SGSPE_DEMO_PGPASSWORD"
  export WASICHAI_SEED_DEV=true WASICHAI_FILES_STORE=local WASICHAI_FILES_LOCAL_PATH="$FILES_DIR"
  export WASICHAI_GIS_GEOSERVER_ENABLED=false SERVER_PORT="$SGSPE_DEMO_PORT"
  # sin clave del proveedor ni organizaciones con IA: la demostración no llama a ningún servicio externo
  unset ANTHROPIC_API_KEY SGSPE_AI_ENABLED_ORGANIZATIONS
  exec "$SGSPE_DEMO_JAVA" -jar "$SGSPE_JAR"
) >"$LOG" 2>&1 &
SERVER_PID=$!
# conserva el código de salida: en bash 3.2 (macOS) el de la trampa reemplaza al del error que la disparó
stop_server() {
  local rc=$?
  if [[ "$KEEP" == "0" ]] && kill -0 "$SERVER_PID" 2>/dev/null; then
    demo_log "deteniendo el servidor (pid $SERVER_PID)"
    kill "$SERVER_PID" && wait "$SERVER_PID" 2>/dev/null || true
  fi
  exit "$rc"
}
trap stop_server EXIT

for _ in $(seq 1 180); do
  if curl -fsS "$CORE/actuator/health/readiness" 2>/dev/null | grep -q '"UP"'; then break; fi
  kill -0 "$SERVER_PID" 2>/dev/null || demo_die "el servidor terminó al arrancar; vea $LOG"
  sleep 1
done
curl -fsS "$CORE/actuator/health/readiness" 2>/dev/null | grep -q '"UP"' || demo_die "el servidor no quedó listo en 180 s; vea $LOG"
demo_log "servidor listo"

export WASICHAI_CORE="$CORE" WASICHAI_EMAIL="admin@wasichai.local" WASICHAI_PASSWORD="admin"
cd "$DEMO_REPO_DIR/model"
demo_log "1/5 apply.py (objetos, campos y relaciones)";   python3 apply.py
demo_log "2/5 apply_roles.py (roles y permisos)";        python3 apply_roles.py
demo_log "3/5 apply.py --workflows";                    python3 apply.py --workflows
demo_log "4/5 load_ubigeo.py";                          python3 load_ubigeo.py
demo_log "5/5 load_indicators.py";                      python3 load_indicators.py
demo_log "cargando el conjunto ficticio oficial (load_demo.py)"
SGSPE_DEMO_PASSWORD="$SGSPE_DEMO_PASSWORD" python3 load_demo.py ${DRAFT[@]+"${DRAFT[@]}"}
if [[ "$MINIMAL" == "0" ]]; then
  demo_log "cargando el conjunto ampliado (database/seeds/ampliado)"
  SGSPE_DEMO_PASSWORD="$SGSPE_DEMO_PASSWORD" python3 load_demo.py --dir "$DEMO_DATABASE_DIR/seeds/ampliado"
  demo_log "subiendo las evidencias sintéticas"
  SGSPE_DEMO_PASSWORD="$SGSPE_DEMO_PASSWORD" python3 "$DEMO_SCRIPTS_DIR/seed-demo-evidence.py" --core "$CORE"
fi

demo_log "base de demostración lista: $SGSPE_DEMO_DB en $PGHOST:$PGPORT; archivos en $FILES_DIR"
if [[ "$KEEP" == "1" ]]; then
  demo_log "el servidor sigue en $CORE (pid $SERVER_PID); deténgalo con: kill $SERVER_PID"
  trap - EXIT
fi
