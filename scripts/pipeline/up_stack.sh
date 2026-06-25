#!/usr/bin/env bash
set -euo pipefail

if [[ $# -ne 1 ]]; then
  echo "Uso: $0 <dev|hml|stg>" >&2
  exit 1
fi

AMBIENTE="$1"
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
COMPOSE_DIR="${ROOT_DIR}/infraestrutura/${AMBIENTE}"
INCLUIR_API_CONTAS="${INCLUIR_API_CONTAS:-false}"

if [[ ! -d "${COMPOSE_DIR}" ]]; then
  echo "Ambiente invalido: ${AMBIENTE}" >&2
  exit 1
fi

"${ROOT_DIR}/scripts/pipeline/package_servicos.sh"
"${ROOT_DIR}/scripts/pipeline/compose_config.sh" "${AMBIENTE}"

echo "==> Subindo stack ${AMBIENTE}"
(
  cd "${COMPOSE_DIR}"
  SERVICOS_DISPONIVEIS=()
  while IFS= read -r servico; do
    SERVICOS_DISPONIVEIS+=("${servico}")
  done < <(docker compose config --services)
  servico_existe() {
    local servico="$1"
    local item
    for item in "${SERVICOS_DISPONIVEIS[@]}"; do
      [[ "${item}" == "${servico}" ]] && return 0
    done
    return 1
  }

  SERVICOS=()
  servico_existe smtp-teste && SERVICOS+=(smtp-teste)
  servico_existe otel-collector && SERVICOS+=(otel-collector)
  SERVICOS+=(eickrono-keycloak eickrono-autenticacao identidade-servidor)
  if [[ "${INCLUIR_API_CONTAS}" == "true" ]]; then
    SERVICOS+=(api-contas-eickrono)
  fi
  docker compose up -d --build --remove-orphans "${SERVICOS[@]}"
)

echo "Stack ${AMBIENTE} iniciada."
