#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROLLOUT_SCRIPT="${SCRIPT_DIR}/rollout_stg_service.sh"

usage() {
  cat <<'EOF'
uso: validate_stg_task_templates.sh [opcoes]

opcoes:
  --db-overrides-file <arq>    carrega overrides de banco para a validacao
  --region <aws-region>        regiao usada apenas nos nomes de imagem simulados
  --account-id <id>            conta usada apenas nos nomes de imagem simulados
  --help                       mostra esta ajuda
EOF
}

db_overrides_file=""
region="sa-east-1"
account_id="531708494702"

while [ "$#" -gt 0 ]; do
  case "$1" in
    --db-overrides-file)
      db_overrides_file="${2:-}"
      shift 2
      ;;
    --region)
      region="${2:-}"
      shift 2
      ;;
    --account-id)
      account_id="${2:-}"
      shift 2
      ;;
    --help|-h)
      usage
      exit 0
      ;;
    *)
      echo "argumento invalido: $1" >&2
      usage >&2
      exit 2
      ;;
  esac
done

if [ -n "$db_overrides_file" ] && [ ! -f "$db_overrides_file" ]; then
  echo "arquivo de overrides de banco nao encontrado: ${db_overrides_file}" >&2
  exit 2
fi

tmpdir="$(mktemp -d)"
cleanup() {
  rm -rf "$tmpdir"
}
trap cleanup EXIT

render_service() {
  local service_key="$1"
  local image="$2"
  local rendered="$3"
  local args=(
    --service "$service_key"
    --image "$image"
    --render-output "$rendered"
    --dry-run
  )

  if [ -n "$db_overrides_file" ]; then
    args+=(--db-overrides-file "$db_overrides_file")
  fi

  bash "$ROLLOUT_SCRIPT" "${args[@]}" >/dev/null
}

assert_no_placeholder() {
  local file="$1"
  if rg -n '__[A-Z0-9_]+__' "$file" >/dev/null; then
    echo "placeholders restantes no arquivo renderizado: $file" >&2
    rg -n '__[A-Z0-9_]+__' "$file" >&2
    exit 1
  fi
}

env_value() {
  local file="$1"
  local name="$2"
  jq -r --arg name "$name" '.containerDefinitions[0].environment[]? | select(.name == $name) | .value' "$file"
}

assert_env_equals() {
  local file="$1"
  local name="$2"
  local expected="$3"
  local actual
  actual="$(env_value "$file" "$name")"
  if [ "$actual" != "$expected" ]; then
    echo "valor invalido para ${name} em ${file}: esperado '${expected}', obtido '${actual}'" >&2
    exit 1
  fi
}

assert_no_bare_cloudmap_hosts() {
  local file="$1"
  if rg -n 'https?://(auth-stg-interno|id-stg-interno|thimisu-backend-stg-interno)(:|/)' "$file" >/dev/null; then
    echo "use o FQDN completo do Cloud Map em ${file}" >&2
    rg -n 'https?://(auth-stg-interno|id-stg-interno|thimisu-backend-stg-interno)(:|/)' "$file" >&2
    exit 1
  fi
}

render_service "auth" "${account_id}.dkr.ecr.${region}.amazonaws.com/eickrono-autenticacao-servidor:validate-auth" "${tmpdir}/auth.json"
render_service "identidade" "${account_id}.dkr.ecr.${region}.amazonaws.com/eickrono-identidade-servidor:validate-identidade" "${tmpdir}/identidade.json"
render_service "thimisu-backend" "${account_id}.dkr.ecr.${region}.amazonaws.com/eickrono-thimisu-backend:validate-thimisu" "${tmpdir}/thimisu.json"

assert_no_placeholder "${tmpdir}/auth.json"
assert_no_placeholder "${tmpdir}/identidade.json"
assert_no_placeholder "${tmpdir}/thimisu.json"

assert_no_bare_cloudmap_hosts "${tmpdir}/auth.json"
assert_no_bare_cloudmap_hosts "${tmpdir}/identidade.json"
assert_no_bare_cloudmap_hosts "${tmpdir}/thimisu.json"

assert_env_equals "${tmpdir}/identidade.json" "INTEGRACAO_PERFIL_DISPONIBILIDADE_URL_BASE" "http://auth-stg-interno.stg.eickrono.internal:8080"
assert_env_equals "${tmpdir}/identidade.json" "INTEGRACAO_PERFIL_JWT_INTERNO_URL_BASE" "http://auth-stg-interno.stg.eickrono.internal:8080"
assert_env_equals "${tmpdir}/identidade.json" "INTEGRACAO_PERFIL_URL_BASE" "https://thimisu-backend-stg-interno.stg.eickrono.internal:8443"
assert_env_equals "${tmpdir}/thimisu.json" "INTEGRACAO_AUTENTICACAO_JWT_INTERNO_URL_BASE" "http://auth-stg-interno.stg.eickrono.internal:8080"

echo "ok"
