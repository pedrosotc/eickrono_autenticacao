#!/usr/bin/env bash

set -euo pipefail

usage() {
  cat <<'EOF'
uso: summarize_stg_db_config.sh [opcoes]

opcoes:
  --db-overrides-file <arq>    carrega overrides de banco para a renderizacao
  --region <aws-region>        regiao usada apenas nos nomes de imagem simulados
  --account-id <id>            conta usada apenas nos nomes de imagem simulados
  --help                       mostra esta ajuda
EOF
}

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROLLOUT_SCRIPT="${SCRIPT_DIR}/rollout_stg_service.sh"

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

parse_jdbc_url() {
  local jdbc_url="$1"
  local without_prefix without_db host_port db_name host port
  without_prefix="${jdbc_url#jdbc:postgresql://}"
  without_db="${without_prefix%%/*}"
  db_name="${without_prefix#*/}"
  host="${without_db%%:*}"
  port="${without_db##*:}"
  printf '%s|%s|%s\n' "$host" "$port" "$db_name"
}

print_row() {
  local service="$1"
  local host="$2"
  local port="$3"
  local db_name="$4"
  local username="$5"
  local password_secret="$6"
  printf '%-18s %-50s %-6s %-28s %-18s %s\n' \
    "$service" "$host" "$port" "$db_name" "$username" "$password_secret"
}

render_service "auth" "${account_id}.dkr.ecr.${region}.amazonaws.com/eickrono-autenticacao-servidor:summary-auth" "${tmpdir}/auth.json"
render_service "identidade" "${account_id}.dkr.ecr.${region}.amazonaws.com/eickrono-identidade-servidor:summary-identidade" "${tmpdir}/identidade.json"
render_service "thimisu-backend" "${account_id}.dkr.ecr.${region}.amazonaws.com/eickrono-thimisu-backend:summary-thimisu" "${tmpdir}/thimisu.json"

printf '%-18s %-50s %-6s %-28s %-18s %s\n' \
  "SERVICE" "DB_HOST" "PORT" "DB_NAME" "USERNAME" "PASSWORD_SECRET_ARN"

auth_host="$(jq -r '.containerDefinitions[0].environment[] | select(.name == "KC_DB_URL_HOST") | .value' "${tmpdir}/auth.json")"
auth_db_name="$(jq -r '.containerDefinitions[0].environment[] | select(.name == "KC_DB_URL_DATABASE") | .value' "${tmpdir}/auth.json")"
auth_username="$(jq -r '.containerDefinitions[0].environment[] | select(.name == "KC_DB_USERNAME") | .value' "${tmpdir}/auth.json")"
auth_password_secret="$(jq -r '.containerDefinitions[0].secrets[] | select(.name == "KC_DB_PASSWORD") | .valueFrom' "${tmpdir}/auth.json")"
auth_port="${STG_SHARED_DB_PORT:-5432}"
print_row "auth" "$auth_host" "$auth_port" "$auth_db_name" "$auth_username" "$auth_password_secret"

identidade_jdbc_url="$(jq -r '.containerDefinitions[0].environment[] | select(.name == "SPRING_DATASOURCE_URL") | .value' "${tmpdir}/identidade.json")"
IFS='|' read -r identidade_host identidade_port identidade_db_name <<<"$(parse_jdbc_url "$identidade_jdbc_url")"
identidade_username="$(jq -r '.containerDefinitions[0].environment[] | select(.name == "SPRING_DATASOURCE_USERNAME") | .value' "${tmpdir}/identidade.json")"
identidade_password_secret="$(jq -r '.containerDefinitions[0].secrets[] | select(.name == "SPRING_DATASOURCE_PASSWORD") | .valueFrom' "${tmpdir}/identidade.json")"
print_row "identidade" "$identidade_host" "$identidade_port" "$identidade_db_name" "$identidade_username" "$identidade_password_secret"

thimisu_jdbc_url="$(jq -r '.containerDefinitions[0].environment[] | select(.name == "SPRING_DATASOURCE_URL") | .value' "${tmpdir}/thimisu.json")"
IFS='|' read -r thimisu_host thimisu_port thimisu_db_name <<<"$(parse_jdbc_url "$thimisu_jdbc_url")"
thimisu_username="$(jq -r '.containerDefinitions[0].environment[] | select(.name == "SPRING_DATASOURCE_USERNAME") | .value' "${tmpdir}/thimisu.json")"
thimisu_password_secret="$(jq -r '.containerDefinitions[0].secrets[] | select(.name == "SPRING_DATASOURCE_PASSWORD") | .valueFrom' "${tmpdir}/thimisu.json")"
print_row "thimisu-backend" "$thimisu_host" "$thimisu_port" "$thimisu_db_name" "$thimisu_username" "$thimisu_password_secret"
