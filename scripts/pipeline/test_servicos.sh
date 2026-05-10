#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
IDENTIDADE_DIR="${EICKRONO_IDENTIDADE_DIR:-${ROOT_DIR}/../eickrono-identidade-servidor}"
CONTAS_DIR="${EICKRONO_CONTAS_DIR:-${ROOT_DIR}/../eickrono-contas-servidor}"
INCLUIR_API_CONTAS="${INCLUIR_API_CONTAS:-false}"

testar() {
  local nome="$1"
  local diretorio="$2"
  local alvo="$3"

  if [[ ! -d "${diretorio}" ]]; then
    echo "Repositorio ausente: ${diretorio}" >&2
    exit 1
  fi

  echo "==> Testando ${nome}"
  (
    cd "${diretorio}"
    make "${alvo}"
  )
}

testar \
  "eickrono-identidade-servidor" \
  "${IDENTIDADE_DIR}" \
  "test-rapido"
if [[ "${INCLUIR_API_CONTAS}" == "true" ]]; then
  testar \
    "eickrono-contas-servidor" \
    "${CONTAS_DIR}" \
    "test-rapido"
fi
testar \
  "eickrono-autenticacao-servidor" \
  "${ROOT_DIR}" \
  "test"

echo "Testes concluidos."
