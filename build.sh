#!/usr/bin/env bash
# Збірка й запуск бекенду. Гасить усе, що вже висить на 8080 (типова
# причина "Port 8080 was already in use" — забутий процес з попереднього
# запуску), піднімає Postgres, білдить і стартує platform-app.
set -euo pipefail

cd "$(dirname "$0")"

echo "==> Звільняю порт 8080, якщо зайнятий..."
PID=$(lsof -ti:8080 || true)
if [ -n "$PID" ]; then
    echo "    знайдено процес(и): $PID — гашу"
    kill -9 $PID
    sleep 1
else
    echo "    порт вільний"
fi

if [ -f "$HOME/.sdkman/bin/sdkman-init.sh" ]; then
    echo "==> Перемикаю Java/Maven на версії з .sdkmanrc..."
    # sdkman-init.sh сам не "nounset-safe" — тимчасово вимикаю -u навколо нього
    set +u
    source "$HOME/.sdkman/bin/sdkman-init.sh"
    sdk env
    set -u
fi

echo "==> Піднімаю Postgres (docker compose)..."
docker compose up -d

echo "==> Чекаю, поки Postgres стане healthy..."
until [ "$(docker inspect --format='{{.State.Health.Status}}' memphisreo-postgres 2>/dev/null || echo starting)" = "healthy" ]; do
    sleep 2
done

echo "==> Збираю проєкт (без тестів)..."
mvn -q -pl platform-app -am clean package -DskipTests

echo "==> Запускаю бекенд на http://localhost:8080 ..."
exec java -jar platform-app/target/platform-app-exec.jar
