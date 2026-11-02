#!/usr/bin/env bash
# E2E da compra paga com resposta perdida: tela real do Terminal, Spring, MySQL e Redis.
# Mercado Pago é stub; nenhuma chamada financeira real acontece. Exige o jar do backend.
#   ./mvnw -q -DskipTests package && ./scripts/e2e-lost-payment-response.sh
# Variáveis: E2E_BACKEND_JAR, TERMINAL_PYTHON_ROOT, TERMINAL_PYTHON.
set -euo pipefail

ROOT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
COMPOSE_FILE="$ROOT_DIR/docker-compose.e2e.yml"
TERMINAL_PYTHON_ROOT=${TERMINAL_PYTHON_ROOT:-"$(cd "$ROOT_DIR/../TerminalPython" && pwd)"}
TERMINAL_PYTHON=${TERMINAL_PYTHON:-"$TERMINAL_PYTHON_ROOT/.venv/bin/python"}
BACKEND_JAR=${E2E_BACKEND_JAR:-"$ROOT_DIR/target/app247-0.0.1-SNAPSHOT.jar"}
E2E_RUNTIME_DIR=$(mktemp -d -t app247-e2e-lost-XXXXXXXX)
BACKEND_PID=""
API_PORT=38080
PROXY_PORT=38081

export E2E_DB_PASSWORD=$(openssl rand -hex 18)
export E2E_DB_ROOT_PASSWORD=$(openssl rand -hex 18)
export E2E_MP_WEBHOOK_SECRET=$(openssl rand -hex 32)
export E2E_RECEIPT_TOKEN=$(openssl rand -hex 24)
export E2E_COMPOSE_FILE="$COMPOSE_FILE"
export E2E_RUNTIME_DIR TERMINAL_PYTHON_ROOT
export E2E_BACKEND_URL="http://127.0.0.1:$API_PORT"
export E2E_PROXY_PORT="$PROXY_PORT"
export POINT_STUB_URL=http://127.0.0.1:39091
export APP247_API_URL="http://127.0.0.1:$PROXY_PORT"
export APP247_DATA_DIR="$E2E_RUNTIME_DIR"
export APP247_DEVICE_CREDENTIAL_PATH="$E2E_RUNTIME_DIR/device-credential"
export APP247_PAYMENT_READ_TIMEOUT_SECONDS=2
export QT_QPA_PLATFORM=offscreen

cleanup() {
  status=$?
  [[ -n "$BACKEND_PID" ]] && kill "$BACKEND_PID" 2>/dev/null || true
  if [[ $status -ne 0 && -f "$E2E_RUNTIME_DIR/backend.log" ]]; then
    echo "[E2E] backend log (últimas linhas):" >&2
    tail -n 40 "$E2E_RUNTIME_DIR/backend.log" >&2
  fi
  docker compose -f "$COMPOSE_FILE" down -v --remove-orphans >/dev/null 2>&1 || true
  rm -rf -- "$E2E_RUNTIME_DIR"
  exit "$status"
}
trap cleanup EXIT INT TERM

[[ -f "$BACKEND_JAR" ]] || { echo "Jar não encontrado: $BACKEND_JAR" >&2; exit 1; }
[[ -x "$TERMINAL_PYTHON" ]] || { echo "Python do Terminal não encontrado: $TERMINAL_PYTHON" >&2; exit 1; }

docker compose -f "$COMPOSE_FILE" up -d --wait mysql-e2e redis-e2e point-stub

env -i PATH="$PATH" HOME="$HOME" SPRING_PROFILES_ACTIVE=prod PORT="$API_PORT" \
  DB_URL='jdbc:mysql://127.0.0.1:33306/app247_e2e?connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true' \
  DB_USERNAME=app247_e2e DB_PASSWORD="$E2E_DB_PASSWORD" \
  REDIS_HOST=127.0.0.1 REDIS_PORT=36379 \
  JWT="$(openssl rand -hex 32)" IDENTITY_HASH_SECRET="$(openssl rand -hex 32)" \
  MP_API_BASE_URL="$POINT_STUB_URL" MP_WEBHOOK_SECRET="$E2E_MP_WEBHOOK_SECRET" \
  MP_TOKEN_REFRESH_ENABLED=false TEST_ENDPOINTS_ENABLED=false \
  COMPROVANTE_SERVICE_URL=http://127.0.0.1:39092 COMPROVANTE_INTERNAL_TOKEN="$E2E_RECEIPT_TOKEN" \
  SMTP_HOST=127.0.0.1 SMTP_PORT=1 MANAGEMENT_HEALTH_MAIL_ENABLED=false \
  WEBSOCKET_ALLOWED_ORIGINS="$E2E_BACKEND_URL" HTTP_CONNECT_TIMEOUT=1s HTTP_READ_TIMEOUT=5s \
  PAYMENT_RECONCILIATION_DELAY_MS=600000 PAYMENT_RECONCILIATION_INITIAL_DELAY_MS=600000 \
  java -jar "$BACKEND_JAR" >"$E2E_RUNTIME_DIR/backend.log" 2>&1 </dev/null &
BACKEND_PID=$!

for _ in $(seq 1 120); do
  curl -fsS --max-time 2 "$E2E_BACKEND_URL/" >/dev/null 2>&1 && break
  sleep 1
done
curl -fsS --max-time 10 "$E2E_BACKEND_URL/actuator/health" | grep -q '"status":"UP"' \
  || { echo "[E2E] backend não ficou UP" >&2; exit 1; }

credential="tdc_e2e_$(openssl rand -hex 24)"
( umask 077; printf '%s' "$credential" >"$APP247_DEVICE_CREDENTIAL_PATH" )
credential_hash=$(printf '%s' "$credential" | sha256sum | cut -d' ' -f1)
{
  printf "SET @mp_access='%s'; SET @mp_refresh='%s';\n" "$(openssl rand -hex 24)" "$(openssl rand -hex 24)"
  cat "$ROOT_DIR/scripts/e2e/fixtures.sql"
  printf "UPDATE terminal SET device_credential_hash='%s', device_credential_version=1, device_credential_created_at=UTC_TIMESTAMP(6);\n" "$credential_hash"
} | docker compose -f "$COMPOSE_FILE" exec -T mysql-e2e mysql -uapp247_e2e -p"$E2E_DB_PASSWORD" app247_e2e

"$TERMINAL_PYTHON" "$ROOT_DIR/scripts/e2e/lost_payment_response_harness.py"

if grep -Fq -- "$credential" "$E2E_RUNTIME_DIR/backend.log"; then
  echo "[E2E] credencial encontrada no log do backend" >&2
  exit 1
fi
echo "[E2E] COMPRA PAGA COM RESPOSTA PERDIDA: aprovado"
