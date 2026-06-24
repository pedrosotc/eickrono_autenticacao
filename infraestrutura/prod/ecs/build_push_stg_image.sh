#!/usr/bin/env bash

set -euo pipefail

usage() {
  cat <<'EOF'
uso: build_push_stg_image.sh --service <auth|identidade|thimisu-backend> --tag <stg-YYYYMMDD-NN> [opcoes]

opcoes:
  --account-id <id>            conta AWS/ECR (default: 531708494702)
  --region <aws-region>        regiao AWS (default: sa-east-1)
  --platform <platform>        plataforma docker buildx (default: linux/arm64)
  --profile <nome>             profile AWS para login no ECR
  --dry-run                    nao executa build/push; apenas imprime o plano
  --help                       mostra esta ajuda
EOF
}

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
AUTH_REPO_ROOT="$(cd "${SCRIPT_DIR}/../../.." && pwd)"
WORKSPACE_ROOT="$(cd "${AUTH_REPO_ROOT}/.." && pwd)"
PROD_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
LOG_WRAPPER="${PROD_DIR}/log_comando_runbook.sh"

service_key=""
tag=""
account_id="531708494702"
region="sa-east-1"
platform="linux/arm64"
aws_profile=""
dry_run="false"

while [ "$#" -gt 0 ]; do
  case "$1" in
    --service)
      service_key="${2:-}"
      shift 2
      ;;
    --tag)
      tag="${2:-}"
      shift 2
      ;;
    --account-id)
      account_id="${2:-}"
      shift 2
      ;;
    --region)
      region="${2:-}"
      shift 2
      ;;
    --platform)
      platform="${2:-}"
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

if [ -z "$service_key" ] || [ -z "$tag" ]; then
  usage >&2
  exit 2
fi

prebuild_cmd=""
case "$service_key" in
  auth)
    repository_name="eickrono-autenticacao-servidor"
    context_dir="$AUTH_REPO_ROOT"
    dockerfile_path="${AUTH_REPO_ROOT}/infraestrutura/prod/docker/Dockerfile.keycloak-stg"
    prebuild_cmd="mvn -q -DskipTests package"
    ;;
  identidade)
    repository_name="eickrono-identidade-servidor"
    context_dir="${WORKSPACE_ROOT}/eickrono-identidade-servidor"
    dockerfile_path="${context_dir}/Dockerfile"
    prebuild_cmd="mvn -q -DskipTests package"
    ;;
  thimisu-backend)
    repository_name="eickrono-thimisu-backend"
    context_dir="${WORKSPACE_ROOT}/eickrono-thimisu-backend"
    dockerfile_path="${context_dir}/modulos/thimisu-backend/Dockerfile"
    ;;
  *)
    echo "service invalido: ${service_key}" >&2
    echo "valores aceitos: auth, identidade, thimisu-backend" >&2
    exit 2
    ;;
esac

if [ ! -d "$context_dir" ]; then
  echo "contexto nao encontrado: ${context_dir}" >&2
  exit 2
fi

if [ ! -f "$dockerfile_path" ]; then
  echo "dockerfile nao encontrado: ${dockerfile_path}" >&2
  exit 2
fi

full_image="${account_id}.dkr.ecr.${region}.amazonaws.com/${repository_name}:${tag}"

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

echo "SERVICE_KEY=${service_key}"
echo "REPOSITORY_NAME=${repository_name}"
echo "CONTEXT_DIR=${context_dir}"
echo "DOCKERFILE=${dockerfile_path}"
echo "FULL_IMAGE=${full_image}"
echo "PLATFORM=${platform}"
echo "PREBUILD_CMD=${prebuild_cmd:-<none>}"
echo "AWS_PROFILE=${aws_profile:-<default>}"

if [ -n "$prebuild_cmd" ]; then
  if [ "$dry_run" = "true" ]; then
    echo "[dry-run] (cd ${context_dir} && ${prebuild_cmd})"
  else
    run_cmd bash -lc "cd $(printf '%q' "$context_dir") && ${prebuild_cmd}"
  fi
fi

login_command="docker login --username AWS --password-stdin ${account_id}.dkr.ecr.${region}.amazonaws.com"
if [ "$dry_run" = "true" ]; then
  printf '[dry-run] '
  printf '%q ' "${aws_cmd[@]}" ecr get-login-password --region "$region"
  printf '| '
  printf '%s\n' "$login_command"
else
  "${aws_cmd[@]}" ecr get-login-password --region "$region" | run_cmd docker login --username AWS --password-stdin "${account_id}.dkr.ecr.${region}.amazonaws.com"
fi

run_cmd docker buildx build \
  --platform "$platform" \
  --file "$dockerfile_path" \
  --tag "$full_image" \
  --push \
  "$context_dir"
