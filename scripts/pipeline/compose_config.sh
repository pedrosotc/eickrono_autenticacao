#!/usr/bin/env bash
set -euo pipefail

if [[ $# -ne 1 ]]; then
  echo "Uso: $0 <dev|stg>" >&2
  exit 1
fi

AMBIENTE="$1"
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
COMPOSE_DIR="${ROOT_DIR}/infraestrutura/${AMBIENTE}"

if [[ ! -d "${COMPOSE_DIR}" ]]; then
  echo "Ambiente invalido: ${AMBIENTE}" >&2
  exit 1
fi

echo "==> Validando docker compose de ${AMBIENTE}"
(
  cd "${COMPOSE_DIR}"
  docker compose config >/dev/null
)

echo "docker compose ${AMBIENTE} valido."
