#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
IDENTIDADE_DIR="${EICKRONO_IDENTIDADE_DIR:-${ROOT_DIR}/../eickrono-identidade-servidor}"
CONTAS_DIR="${EICKRONO_CONTAS_DIR:-${ROOT_DIR}/../eickrono-contas-servidor}"
INCLUIR_API_CONTAS="${INCLUIR_API_CONTAS:-false}"

empacotar() {
  local nome="$1"
  local diretorio="$2"

  if [[ ! -d "${diretorio}" ]]; then
    echo "Repositorio ausente: ${diretorio}" >&2
    exit 1
  fi

  echo "==> Empacotando ${nome}"
  (
    cd "${diretorio}"
    make package
  )
}

empacotar "eickrono-identidade-servidor" "${IDENTIDADE_DIR}"
if [[ "${INCLUIR_API_CONTAS}" == "true" ]]; then
  empacotar "eickrono-contas-servidor" "${CONTAS_DIR}"
fi
empacotar "eickrono-autenticacao-servidor" "${ROOT_DIR}"

echo "Empacotacao concluida."
