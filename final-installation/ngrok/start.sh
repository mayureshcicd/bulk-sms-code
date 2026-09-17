#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

if [[ ! -f .env ]]; then
  echo "Missing ngrok/.env. Copy .env.example to .env and add your token and assigned domain." >&2
  exit 1
fi

if ! curl --fail --silent --show-error --max-time 5 "http://127.0.0.1:8080/bulk-sms/" >/dev/null; then
  echo "Warning: Bulk Message Composer is not responding on localhost:8080." >&2
fi
if ! curl --fail --silent --show-error --max-time 5 "http://127.0.0.1:8081/" >/dev/null; then
  echo "Warning: Message Approval System is not responding on localhost:8081." >&2
fi

docker compose up -d

echo "Waiting for ngrok..."
for _ in {1..20}; do
  if curl --fail --silent http://127.0.0.1:4040/api/tunnels >/dev/null; then
    break
  fi
  sleep 1
done

docker compose ps
echo
echo "Configured public URLs:"
set -a
source .env
set +a
echo "Bulk Message Composer: https://${NGROK_DOMAIN}/bulk-sms/"
echo "Message Approval System: https://${NGROK_DOMAIN}/"
echo
echo "Local ngrok inspector: http://127.0.0.1:4040"
