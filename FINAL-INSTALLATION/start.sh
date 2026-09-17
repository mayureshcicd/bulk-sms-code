#!/bin/bash

set -e

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd -P)"
cd "$SCRIPT_DIR"

COMPOSE_FILE="setup.yml"
INSTALL_DIR="$SCRIPT_DIR"

# Bind-mount directories live beside setup.yml and are created on every start.
mkdir -p \
    data/sms \
    data/messages \
    data/uploads

read_env_value() {
    local env_key="$1"
    awk -v key="$env_key" 'index($0, key "=") == 1 {sub(/^[^=]*=/, ""); print; exit}' .env 2>/dev/null || true
}

upsert_env_value() {
    local env_key="$1"
    local env_value="$2"
    local env_tmp
    env_tmp="$(mktemp .env.tmp.XXXXXX)"
    awk -v key="$env_key" -v value="$env_value" '
        BEGIN { found = 0 }
        index($0, key "=") == 1 {
            if (!found) print key "=" value
            found = 1
            next
        }
        { print }
        END { if (!found) print key "=" value }
    ' .env 2>/dev/null > "$env_tmp"
    mv "$env_tmp" .env
}

ensure_env_value() {
    local env_key="$1"
    local default_value="$2"
    if [ -z "$(read_env_value "$env_key")" ]; then
        upsert_env_value "$env_key" "$default_value"
    fi
}

touch .env

WEBHOOK_SECRET="$(read_env_value OPENWA_WEBHOOK_SECRET)"
COMPANY_NAME="$(read_env_value COMPANY_NAME)"
if [ -z "$COMPANY_NAME" ]; then
    COMPANY_NAME="ATOZ GUMMING WORK INDIA PVT. LTD."
fi
if [ -z "$WEBHOOK_SECRET" ]; then
    WEBHOOK_SECRET="$(openssl rand -hex 32)"
fi

read_dmi_value() {
    local dmi_file="$1"
    local dmi_value

    dmi_value="$(cat "$dmi_file" 2>/dev/null | tr -d '\r\n' || true)"
    if [ -z "$dmi_value" ] && command -v sudo >/dev/null 2>&1; then
        dmi_value="$(sudo cat "$dmi_file" | tr -d '\r\n' || true)"
    fi
    printf '%s' "$dmi_value"
}

MOTHERBOARD_VALUE="$(read_dmi_value /sys/class/dmi/id/board_serial)"
case "${MOTHERBOARD_VALUE,,}" in
    ""|none|unknown|"not specified"|"to be filled by o.e.m.")
        MOTHERBOARD_VALUE="$(read_dmi_value /sys/class/dmi/id/product_uuid)"
        ;;
esac
if [ -z "$MOTHERBOARD_VALUE" ]; then
    echo "ERROR: Motherboard serial/product UUID is not available. Installation stopped."
    exit 1
fi

for registration_file in data/sms/registered-system.dat data/messages/registered-system.dat
do
    if [ -s "$registration_file" ]; then
        REGISTERED_VALUE="$(tr -d '\r\n' < "$registration_file")"
        if [ "$REGISTERED_VALUE" != "$MOTHERBOARD_VALUE" ]; then
            echo "ERROR: This installation is registered for another computer motherboard."
            exit 1
        fi
    else
        printf '%s\n' "$MOTHERBOARD_VALUE" > "$registration_file"
    fi
done

# Preserve all existing settings (including ALLOW_INCOMING_MESSAGE) and only
# add/update installation-managed values.
upsert_env_value OPENWA_WEBHOOK_SECRET "$WEBHOOK_SECRET"
upsert_env_value COMPANY_NAME "$COMPANY_NAME"
upsert_env_value HARDWARE_BINDING_ENABLED true
ensure_env_value OPENWA_BASE_URL http://openwa-api:2785/api
ensure_env_value MESSAGE_APPROVAL_URL http://message-approve-api:8081
ensure_env_value MESSAGE_INTEGRATION_KEY message-approval-internal-key
ensure_env_value ALLOW_INCOMING_MESSAGE true
ensure_env_value check_days true
ensure_env_value DB_HOST postgres
ensure_env_value DB_PORT 5432
ensure_env_value DB_NAME message_approval
ensure_env_value DB_USER postgres
ensure_env_value DB_PASS 'mvr#2023'

echo "=========================================="
echo "Starting OpenWA..."
echo "=========================================="

docker compose -f "$COMPOSE_FILE" up -d openwa-api

echo
echo "Waiting for OpenWA API Key..."

API_KEY=""
API_KEY_ATTEMPTS=0
while [ -z "$API_KEY" ]
do
    sleep 2
    API_KEY_ATTEMPTS=$((API_KEY_ATTEMPTS + 1))

    # The current OpenWA image persists its authoritative key in /app/data.
    # Read that first so a newly-created volume cannot leave applications using
    # a stale key from an older .env file.
    API_KEY="$(docker exec openwa sh -c 'cat /app/data/.api-key 2>/dev/null' 2>/dev/null \
        | tr -d '\r\n' || true)"

    if [[ ! "$API_KEY" =~ ^owa_k1_[A-Za-z0-9]+$ ]]; then
        API_KEY="$(docker logs openwa 2>&1 \
            | grep -oE 'owa_k1_[A-Za-z0-9]+' \
            | tail -1)"
    fi

    if [ "$API_KEY_ATTEMPTS" -ge 60 ] && [ -z "$API_KEY" ]; then
        echo "ERROR: OpenWA API key was not available after 120 seconds."
        exit 1
    fi
done

upsert_env_value API_KEY "$API_KEY"

echo "OpenWA API key detected; .env updated."

echo "Starting PostgreSQL..."

docker compose -f "$COMPOSE_FILE" up -d postgres

echo "Starting Spring Boot applications..."

docker compose -f "$COMPOSE_FILE" up -d sms-api message-approve-api

echo "Done."
