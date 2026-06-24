#!/usr/bin/env bash

set -euo pipefail

usage() {
  cat <<'EOF'
uso: upsert_cloudflare_txt_record.sh --zone <dominio> --name <nome> --content <texto> [opcoes]

opcoes:
  --ttl <segundos>             ttl do registro (default: 300)
  --comment <texto>            comentario opcional do registro
  --api-token-env <nome>       nome da variavel de ambiente com o token (default: CLOUDFLARE_API_TOKEN)
  --dry-run                    nao chama a API; apenas imprime o plano
  --help                       mostra esta ajuda

pre-requisito:
  export CLOUDFLARE_API_TOKEN=...
EOF
}

zone_name=""
record_name=""
record_content=""
ttl="300"
comment=""
token_env="CLOUDFLARE_API_TOKEN"
dry_run="false"

while [ "$#" -gt 0 ]; do
  case "$1" in
    --zone)
      zone_name="${2:-}"
      shift 2
      ;;
    --name)
      record_name="${2:-}"
      shift 2
      ;;
    --content)
      record_content="${2:-}"
      shift 2
      ;;
    --ttl)
      ttl="${2:-}"
      shift 2
      ;;
    --comment)
      comment="${2:-}"
      shift 2
      ;;
    --api-token-env)
      token_env="${2:-}"
      shift 2
      ;;
    --dry-run)
      dry_run="true"
      shift
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

if [ -z "$zone_name" ] || [ -z "$record_name" ] || [ -z "$record_content" ]; then
  usage >&2
  exit 2
fi

if ! [[ "$ttl" =~ ^[0-9]+$ ]]; then
  echo "ttl invalido: ${ttl}" >&2
  exit 2
fi

token_value="${!token_env:-}"
if [ -z "$token_value" ] && [ "$dry_run" != "true" ]; then
  echo "variavel de token ausente: ${token_env}" >&2
  exit 2
fi

zone_lookup_url="https://api.cloudflare.com/client/v4/zones?name=${zone_name}&status=active"

echo "ZONE_NAME=${zone_name}"
echo "RECORD_NAME=${record_name}"
echo "TTL=${ttl}"
echo "TOKEN_ENV=${token_env}"

if [ "$dry_run" = "true" ]; then
  echo "ACTION=lookup-zone"
  echo "[dry-run] GET ${zone_lookup_url}"
  echo "ACTION=upsert-txt-record"
  echo "[dry-run] UPSERT TXT ${record_name}.${zone_name} ttl=${ttl}"
  echo "CONTENT_PREVIEW=${record_content}"
  exit 0
fi

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROD_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
LOG_WRAPPER="${PROD_DIR}/log_comando_runbook.sh"

cf_api() {
  curl -fsS \
    -H "Authorization: Bearer ${token_value}" \
    -H "Content-Type: application/json" \
    "$@"
}

run_logged_curl() {
  if [ -n "${EICKRONO_STG_HISTORICO:-}" ] && [ -f "$LOG_WRAPPER" ]; then
    "$LOG_WRAPPER" "$EICKRONO_STG_HISTORICO" "$@"
    return $?
  fi
  "$@"
}

zone_response="$(cf_api "$zone_lookup_url")"
zone_id="$(printf '%s' "$zone_response" | jq -r '.result[0].id // empty')"

if [ -z "$zone_id" ]; then
  echo "zona nao encontrada ou token sem acesso: ${zone_name}" >&2
  exit 1
fi

record_fqdn="${record_name}.${zone_name}"
list_url="https://api.cloudflare.com/client/v4/zones/${zone_id}/dns_records?type=TXT&name=${record_fqdn}"
record_response="$(cf_api "$list_url")"
record_id="$(printf '%s' "$record_response" | jq -r '.result[0].id // empty')"

tmp_payload="$(mktemp)"
cleanup() {
  rm -f "$tmp_payload"
}
trap cleanup EXIT

jq -n \
  --arg type "TXT" \
  --arg name "$record_name" \
  --arg content "$record_content" \
  --argjson ttl "$ttl" \
  --arg comment "$comment" '
    {
      type: $type,
      name: $name,
      content: $content,
      ttl: $ttl
    }
    + (if $comment != "" then {comment: $comment} else {} end)
  ' >"$tmp_payload"

if [ -n "$record_id" ]; then
  echo "ACTION=update-record"
  run_logged_curl curl -fsS -X PUT \
    -H "Authorization: Bearer ${token_value}" \
    -H "Content-Type: application/json" \
    --data @"$tmp_payload" \
    "https://api.cloudflare.com/client/v4/zones/${zone_id}/dns_records/${record_id}"
else
  echo "ACTION=create-record"
  run_logged_curl curl -fsS -X POST \
    -H "Authorization: Bearer ${token_value}" \
    -H "Content-Type: application/json" \
    --data @"$tmp_payload" \
    "https://api.cloudflare.com/client/v4/zones/${zone_id}/dns_records"
fi
