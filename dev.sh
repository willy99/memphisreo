#!/usr/bin/env bash
# Запускає все локальне оточення одним скриптом:
#   Docker (Postgres + S3/RustFS + Mailpit) → бекенд (http://localhost:8080) → фронт (http://localhost:5173).
# Ctrl+C зупиняє бекенд і фронт; контейнери лишаються (docker compose stop — щоб зупинити).
#
#   ./dev.sh           — звичайний запуск
#   ./dev.sh --reset   — спершу видалити локальні дані Postgres/S3 (docker compose down -v)
#   ./dev.sh --skip-build — не перезбирати бекенд (використати наявний jar)
set -euo pipefail

cd "$(dirname "$0")"

RESET=false
SKIP_BUILD=false
for arg in "$@"; do
    case "$arg" in
        --reset) RESET=true ;;
        --skip-build) SKIP_BUILD=true ;;
        -h|--help) sed -n '2,9p' "$0"; exit 0 ;;
        *) echo "Невідомий аргумент: $arg (див. --help)"; exit 1 ;;
    esac
done

# Локальні секрети (gitignored): супер-адмін платформи. Генеруються один раз;
# бекенд створює адміна з них, якщо його ще немає (тож і після --reset).
# Видалиш файл без --reset — новий пароль розійдеться з тим, що вже в БД.
ENV_FILE=".env.local"
if [ ! -f "$ENV_FILE" ]; then
    cat > "$ENV_FILE" <<ENVEOF
# Згенеровано dev.sh — лише для локальної розробки, не комітити.
MEMPHISREO_BOOTSTRAP_ADMIN_EMAIL=willy2005@gmail.com
MEMPHISREO_BOOTSTRAP_ADMIN_PASSWORD=$(LC_ALL=C tr -dc 'A-Za-z0-9' </dev/urandom | head -c 20)
ENVEOF
    chmod 600 "$ENV_FILE"
    echo "==> Створено $ENV_FILE з паролем супер-адміна"
fi
set -a
# shellcheck disable=SC1090
source "$ENV_FILE"
set +a

LOG_DIR=".dev-logs"
mkdir -p "$LOG_DIR"
BACKEND_LOG="$LOG_DIR/backend.log"
FRONTEND_LOG="$LOG_DIR/frontend.log"

step() { echo; echo "==> $*"; }

wait_for() {  # wait_for <опис> <таймаут, с> <команда...>
    local what="$1" timeout="$2"; shift 2
    local waited=0
    until "$@" >/dev/null 2>&1; do
        if [ "$waited" -ge "$timeout" ]; then
            echo "    $what не готовий за ${timeout}с"
            return 1
        fi
        sleep 2; waited=$((waited + 2))
    done
}

free_port() {
    local pids
    pids=$(lsof -ti:"$1" || true)
    if [ -n "$pids" ]; then
        echo "    порт $1 зайнятий ($pids) — гашу"
        kill $pids 2>/dev/null || true
        sleep 1
        kill -9 $pids 2>/dev/null || true
    fi
}

BACKEND_PID=""
FRONTEND_PID=""
cleanup() {
    echo
    echo "==> Зупиняю бекенд і фронт..."
    [ -n "$FRONTEND_PID" ] && kill "$FRONTEND_PID" 2>/dev/null || true
    [ -n "$BACKEND_PID" ] && kill "$BACKEND_PID" 2>/dev/null || true
    wait 2>/dev/null || true
    echo "    Готово. Контейнери працюють далі (зупинити: docker compose stop)."
}
trap cleanup EXIT INT TERM

# --- Docker ---------------------------------------------------------------
step "Docker"
if ! docker info >/dev/null 2>&1; then
    if [ "$(uname)" = "Darwin" ]; then
        echo "    Docker не запущений — запускаю Docker Desktop..."
        open -a Docker
        wait_for "Docker" 120 docker info || exit 1
    else
        echo "    Docker не запущений. Запусти його й повтори."; exit 1
    fi
fi
echo "    ок"

if $RESET; then
    step "Видаляю локальні дані (docker compose down -v)"
    docker compose down -v
fi

step "Postgres + S3 (RustFS) + Mailpit"
if ! docker compose up -d; then
    echo "    docker compose не зміг підняти контейнери — див. помилку вище"; exit 1
fi
wait_for "Postgres" 90 sh -c '[ "$(docker inspect --format={{.State.Health.Status}} memphisreo-postgres)" = healthy ]' || exit 1
wait_for "S3 (RustFS)" 60 sh -c '[ "$(docker inspect --format={{.State.Health.Status}} memphisreo-s3)" = healthy ]' || exit 1
wait_for "Mailpit" 60 sh -c '[ "$(docker inspect --format={{.State.Health.Status}} memphisreo-mailpit)" = healthy ]' || exit 1
echo "    ок"

# Логін застосунку створює init-скрипт лише на СВІЖОМУ томі (ADR-001).
if [ -z "$(docker exec memphisreo-postgres psql -U memphisreo -d memphisreo -tAc \
        "SELECT 1 FROM pg_roles WHERE rolname = 'memphisreo_app_user'")" ]; then
    echo
    echo "!!! У базі немає логіну застосунку memphisreo_app_user — том створено"
    echo "    до переходу на shared schema + RLS. Перезапусти з очищенням даних:"
    echo "        ./dev.sh --reset"
    exit 1
fi

# --- Бекенд ---------------------------------------------------------------
if [ -f "$HOME/.sdkman/bin/sdkman-init.sh" ]; then
    # sdkman-init.sh не "nounset-safe" — тимчасово вимикаю -u
    set +u
    source "$HOME/.sdkman/bin/sdkman-init.sh"
    sdk env >/dev/null
    set -u
fi

JAR="platform-app/target/platform-app-exec.jar"
if $SKIP_BUILD && [ -f "$JAR" ]; then
    step "Бекенд: збірку пропущено (--skip-build)"
else
    step "Бекенд: збірка (mvn clean package, без тестів)"
    mvn -q -pl platform-app -am clean package -DskipTests
fi

step "Бекенд: запуск (лог — $BACKEND_LOG)"
free_port 8080
java -jar "$JAR" >"$BACKEND_LOG" 2>&1 &
BACKEND_PID=$!
if ! wait_for "Бекенд" 120 curl -sf http://localhost:8080/actuator/health; then
    echo "    Останні рядки логу:"; tail -n 30 "$BACKEND_LOG"; exit 1
fi
echo "    ок — http://localhost:8080"

# --- Фронт ----------------------------------------------------------------
step "Фронт"
if [ ! -d web/node_modules ]; then
    echo "    npm install..."
    npm --prefix web install --silent
fi
free_port 5173
npm --prefix web run dev -- --port 5173 --strictPort >"$FRONTEND_LOG" 2>&1 &
FRONTEND_PID=$!
if ! wait_for "Фронт" 60 curl -sf http://localhost:5173; then
    echo "    Останні рядки логу:"; tail -n 30 "$FRONTEND_LOG"; exit 1
fi

cat <<EOF

==================================================================
  Все запущено:
    Фронт         http://localhost:5173
    Бекенд        http://localhost:8080   (лог: $BACKEND_LOG)
    Супер-адмін   http://localhost:5173/admin/login
                  $MEMPHISREO_BOOTSTRAP_ADMIN_EMAIL / пароль у $ENV_FILE
    Пошта (dev)   http://localhost:8025   (листи скидання пароля)
    S3 console    http://localhost:9001/rustfs/console/   (memphisreo / memphisreo123)
    Postgres      localhost:5432          (memphisreo / memphisreo)

  Ctrl+C — зупинити бекенд і фронт.
==================================================================
EOF

# Чекаємо, поки один із процесів не завершиться (або Ctrl+C).
while kill -0 "$BACKEND_PID" 2>/dev/null && kill -0 "$FRONTEND_PID" 2>/dev/null; do
    sleep 2
done
echo "!!! Один із процесів завершився — див. логи в $LOG_DIR"
exit 1
