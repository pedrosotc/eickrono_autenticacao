#!/usr/bin/env bash

set -euo pipefail

usage() {
  cat <<'EOF'
uso: upsert_stg_secret.sh --secret-id <id> [fonte-do-valor] [opcoes]

fonte do valor:
  --value-file <arquivo>       le o segredo do arquivo informado
  --value-literal <texto>      usa o valor literal informado
  --value-stdin                le o segredo da entrada padrao

opcoes:
  --description <texto>        descricao usada apenas em create-secret
  --kms-key-id <id>            kms key do create-secret
  --region <aws-region>        regiao AWS (default: sa-east-1)
  --profile <nome>             profile AWS
  --dry-run                    nao executa comandos AWS; apenas imprime o plano
  --help                       mostra esta ajuda
EOF
}

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROD_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
LOG_WRAPPER="${PROD_DIR}/log_comando_runbook.sh"

secret_id=""
description=""
kms_key_id=""
region="sa-east-1"
aws_profile=""
dry_run="false"
value_mode=""
value_source=""

while [ "$#" -gt 0 ]; do
  case "$1" in
    --secret-id)
      secret_id="${2:-}"
      shift 2
      ;;
    --value-file)
      value_mode="file"
      value_source="${2:-}"
      shift 2
      ;;
    --value-literal)
      value_mode="literal"
      value_source="${2:-}"
      shift 2
      ;;
    --value-stdin)
      value_mode="stdin"
      shift
      ;;
    --description)
      description="${2:-}"
      shift 2
      ;;
    --kms-key-id)
      kms_key_id="${2:-}"
      shift 2
      ;;
    --region)
      region="${2:-}"
      shift 2
      ;;
    --profile)
      aws_profile="${2:-}"
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

if [ -z "$secret_id" ] || [ -z "$value_mode" ]; then
  usage >&2
  exit 2
fi

read_secret_value() {
  case "$value_mode" in
    file)
      if [ ! -f "$value_source" ]; then
        echo "arquivo de segredo nao encontrado: ${value_source}" >&2
        exit 2
      fi
      cat "$value_source"
      ;;
    literal)
      printf '%s' "$value_source"
      ;;
    stdin)
      cat
      ;;
    *)
      echo "modo de valor invalido: ${value_mode}" >&2
      exit 2
      ;;
  esac
}

secret_value="$(read_secret_value)"
secret_length="${#secret_value}"

aws_cmd=(aws)
if [ -n "$aws_profile" ]; then
  aws_cmd+=(--profile "$aws_profile")
fi

run_cmd() {
  if [ "$dry_run" = "true" ]; then
    printf '[dry-run] '
    printf '%q ' "$@"
    printf '\n'
    return 0
  fi

  if [ -n "${EICKRONO_STG_HISTORICO:-}" ] && [ -f "$LOG_WRAPPER" ]; then
    "$LOG_WRAPPER" "$EICKRONO_STG_HISTORICO" "$@"
    return $?
  fi

  "$@"
}

tmp_payload="$(mktemp)"
cleanup() {
  rm -f "$tmp_payload"
}
trap cleanup EXIT

secret_exists="false"
if "${aws_cmd[@]}" secretsmanager describe-secret --region "$region" --secret-id "$secret_id" >/dev/null 2>&1; then
  secret_exists="true"
fi

echo "SECRET_ID=${secret_id}"
echo "REGION=${region}"
echo "AWS_PROFILE=${aws_profile:-<default>}"
echo "VALUE_MODE=${value_mode}"
echo "VALUE_LENGTH=${secret_length}"
echo "SECRET_EXISTS=${secret_exists}"

if [ "$secret_exists" = "true" ]; then
  jq -n \
    --arg secretId "$secret_id" \
    --arg secretString "$secret_value" \
    '{SecretId: $secretId, SecretString: $secretString}' >"$tmp_payload"

  if [ "$dry_run" = "true" ]; then
    echo "ACTION=put-secret-value"
  fi

  run_cmd "${aws_cmd[@]}" secretsmanager put-secret-value \
    --region "$region" \
    --cli-input-json "file://${tmp_payload}"
else
  jq -n \
    --arg name "$secret_id" \
    --arg description "$description" \
    --arg secretString "$secret_value" \
    --arg kmsKeyId "$kms_key_id" '
      {
        Name: $name,
        SecretString: $secretString
      }
      + (if $description != "" then {Description: $description} else {} end)
      + (if $kmsKeyId != "" then {KmsKeyId: $kmsKeyId} else {} end)
    ' >"$tmp_payload"

  if [ "$dry_run" = "true" ]; then
    echo "ACTION=create-secret"
  fi

  run_cmd "${aws_cmd[@]}" secretsmanager create-secret \
    --region "$region" \
    --cli-input-json "file://${tmp_payload}"
fi
