#!/bin/bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

if [ "$(id -u)" -ne 0 ]; then
    SUDO="sudo"
else
    SUDO=""
fi

check_docker() {
    command -v docker >/dev/null 2>&1 && docker --version >/dev/null 2>&1
}

check_compose() {
    docker compose version >/dev/null 2>&1
}

echo "Checking Docker installation..."

if ! check_docker; then
    echo "Docker is not installed. Installing Docker..."
    $SUDO apt update
    $SUDO apt install -y ca-certificates curl gnupg
    $SUDO install -m 0755 -d /etc/apt/keyrings
    curl -fsSL https://download.docker.com/linux/ubuntu/gpg | $SUDO tee /etc/apt/keyrings/docker.asc > /dev/null

    echo \
      "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.asc] https://download.docker.com/linux/ubuntu \
      $(. /etc/os-release && echo "$VERSION_CODENAME") stable" | \
      $SUDO tee /etc/apt/sources.list.d/docker.list > /dev/null

    $SUDO apt update
    $SUDO apt install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
    $SUDO systemctl enable docker
    $SUDO systemctl start docker
else
    echo "Docker is already installed."
fi

if ! check_compose; then
    echo "Docker Compose plugin is not available. Installing it..."
    $SUDO apt update
    $SUDO apt install -y docker-compose-plugin
else
    echo "Docker Compose is already available."
fi

echo "Verifying Docker and Compose..."
docker --version
docker compose version

CURRENT_USER="$(whoami)"

echo "Current user: $CURRENT_USER"

if [ "$CURRENT_USER" != "root" ]; then
    $SUDO usermod -aG docker "$CURRENT_USER"
    echo "Added $CURRENT_USER to the docker group."
    echo "Run this command once to refresh your shell:"
    echo "  newgrp docker"
else
    echo "Current user is root; no extra docker-group change is needed."
fi

echo "Docker installation completed."
