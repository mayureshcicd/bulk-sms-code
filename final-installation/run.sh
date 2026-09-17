#!/bin/bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

echo "=========================================="
echo "Starting installation via start.sh..."
echo "=========================================="

bash "$SCRIPT_DIR/start.sh"

echo
echo "Verifying Docker containers..."

for container in openwa message-approve-postgres sms-api message-approve-api; do
    status="$(docker inspect -f '{{.State.Running}}' "$container" 2>/dev/null || true)"
    if [ "$status" != "true" ]; then
        echo "Container '$container' is not running. Keeping files." >&2
        exit 1
    fi
done

echo "Installation completed successfully."
echo "Removing project files to leave only the running containers..."

find "$SCRIPT_DIR" -mindepth 1 -maxdepth 1 \
    ! -name data \
    -exec rm -rf -- {} +

echo "Cleanup complete."
echo "Docker containers remain running."
echo "Persistent application data is preserved in:"
echo "$SCRIPT_DIR/data"
echo "PostgreSQL data is preserved in Docker volume:"
echo "final-installation_postgres-data"
echo "OpenWA data is preserved in Docker volume:"
echo "final-installation_openwa-data"
