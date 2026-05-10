#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
IDENTIDADE_DIR="${EICKRONO_IDENTIDADE_DIR:-${ROOT_DIR}/../eickrono-identidade-servidor}"
CONTAS_DIR="${EICKRONO_CONTAS_DIR:-${ROOT_DIR}/../eickrono-contas-servidor}"
INCLUIR_API_CONTAS="${INCLUIR_API_CONTAS:-false}"

if ! docker info >/dev/null 2>&1; then
  echo "Docker daemon indisponivel. O alvo test-servicos-completo exige Docker acessivel porque o eickrono-identidade-servidor usa Testcontainers." >&2
  exit 2
fi

testar() {
  local nome="$1"
  local diretorio="$2"

  if [[ ! -d "${diretorio}" ]]; then
    echo "Repositorio ausente: ${diretorio}" >&2
    exit 1
  fi

  echo "==> Testando suite completa de ${nome}"
  (
    cd "${diretorio}"
    make test
  )
}

testar "eickrono-identidade-servidor" "${IDENTIDADE_DIR}"
if [[ "${INCLUIR_API_CONTAS}" == "true" ]]; then
  testar "eickrono-contas-servidor" "${CONTAS_DIR}"
fi
testar "eickrono-autenticacao-servidor" "${ROOT_DIR}"

echo "Suite completa concluida."
