#!/usr/bin/env bash

set -euo pipefail

usage() {
  cat <<'EOF'
uso: rollout_stg_service.sh --service <auth|identidade|thimisu-backend> --image <ecr-image:tag> [opcoes]

opcoes:
  --cluster <nome>             cluster ECS (default: eickrono-stg)
  --region <aws-region>        regiao AWS (default: sa-east-1)
  --ecs-service <nome>         nome do service ECS; default = family do task definition
  --db-overrides-file <arq>    carrega overrides de banco a partir de arquivo shell
  --render-output <arquivo>    persiste o task definition renderizado no caminho informado
  --no-wait                    nao aguarda service stable
  --dry-run                    nao executa comandos AWS; apenas imprime o plano
  --help                       mostra esta ajuda
EOF
}

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROD_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
LOG_WRAPPER="${PROD_DIR}/log_comando_runbook.sh"

cluster="eickrono-stg"
region="sa-east-1"
service_key=""
image=""
ecs_service=""
render_output=""
db_overrides_file=""
dry_run="false"
wait_for_stable="true"

while [ "$#" -gt 0 ]; do
  case "$1" in
    --service)
      service_key="${2:-}"
      shift 2
      ;;
    --image)
      image="${2:-}"
      shift 2
      ;;
    --cluster)
      cluster="${2:-}"
      shift 2
      ;;
    --region)
      region="${2:-}"
      shift 2
      ;;
    --ecs-service)
      ecs_service="${2:-}"
      shift 2
      ;;
    --db-overrides-file)
      db_overrides_file="${2:-}"
      shift 2
      ;;
    --render-output)
      render_output="${2:-}"
      shift 2
      ;;
    --no-wait)
      wait_for_stable="false"
      shift
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

if [ -z "$service_key" ] || [ -z "$image" ]; then
  usage >&2
  exit 2
fi

if [ -n "$db_overrides_file" ]; then
  if [ ! -f "$db_overrides_file" ]; then
    echo "arquivo de overrides de banco nao encontrado: ${db_overrides_file}" >&2
    exit 2
  fi
  set -a
  # shellcheck disable=SC1090
  source "$db_overrides_file"
  set +a
fi

stg_shared_db_host="${STG_SHARED_DB_HOST:-eickrono-stg-postgres.cdu8yi4qkl16.sa-east-1.rds.amazonaws.com}"
stg_shared_db_port="${STG_SHARED_DB_PORT:-5432}"
stg_shared_db_password_secret_arn="${STG_SHARED_DB_PASSWORD_SECRET_ARN:-arn:aws:secretsmanager:sa-east-1:531708494702:secret:rds!db-7df15f56-c831-40b7-be42-ebd935108b06-22Dwvf:password::}"

auth_kc_db_host="${AUTH_KC_DB_HOST:-$stg_shared_db_host}"
auth_kc_db_port="${AUTH_KC_DB_PORT:-$stg_shared_db_port}"
auth_kc_db_name="${AUTH_KC_DB_NAME:-keycloak_stg}"
auth_kc_db_username="${AUTH_KC_DB_USERNAME:-eickrono_admin}"
auth_kc_db_password_secret_arn="${AUTH_KC_DB_PASSWORD_SECRET_ARN:-$stg_shared_db_password_secret_arn}"

identidade_db_host="${IDENTIDADE_DB_HOST:-$stg_shared_db_host}"
identidade_db_port="${IDENTIDADE_DB_PORT:-$stg_shared_db_port}"
identidade_db_name="${IDENTIDADE_DB_NAME:-eickrono_identidade_stg}"
identidade_db_username="${IDENTIDADE_DB_USERNAME:-eickrono_admin}"
identidade_db_password_secret_arn="${IDENTIDADE_DB_PASSWORD_SECRET_ARN:-$stg_shared_db_password_secret_arn}"

thimisu_db_host="${THIMISU_DB_HOST:-$stg_shared_db_host}"
thimisu_db_port="${THIMISU_DB_PORT:-$stg_shared_db_port}"
thimisu_db_name="${THIMISU_DB_NAME:-eickrono_thimisu_stg}"
thimisu_db_username="${THIMISU_DB_USERNAME:-eickrono_admin}"
thimisu_db_password_secret_arn="${THIMISU_DB_PASSWORD_SECRET_ARN:-$stg_shared_db_password_secret_arn}"

identidade_db_url="jdbc:postgresql://${identidade_db_host}:${identidade_db_port}/${identidade_db_name}"
thimisu_db_url="jdbc:postgresql://${thimisu_db_host}:${thimisu_db_port}/${thimisu_db_name}"

case "$service_key" in
  auth)
    template="${SCRIPT_DIR}/auth-task-definition.stg.json"
    ;;
  identidade)
    template="${SCRIPT_DIR}/identidade-task-definition.stg.json"
    ;;
  thimisu-backend)
    template="${SCRIPT_DIR}/thimisu-backend-task-definition.stg.json"
    ;;
  *)
    echo "service invalido: ${service_key}" >&2
    echo "valores aceitos: auth, identidade, thimisu-backend" >&2
    exit 2
    ;;
esac

if [ ! -f "$template" ]; then
  echo "template nao encontrado: ${template}" >&2
  exit 2
fi

family="$(jq -r '.family' "$template")"
container_name="$(jq -r '.containerDefinitions[0].name' "$template")"
ecs_service="${ecs_service:-$family}"

if [ -n "$render_output" ]; then
  rendered_task="$render_output"
  mkdir -p "$(dirname "$rendered_task")"
else
  rendered_task="$(mktemp)"
fi

jq \
  --arg image "$image" \
  --arg container "$container_name" \
  --arg auth_kc_db_host "$auth_kc_db_host" \
  --arg auth_kc_db_name "$auth_kc_db_name" \
  --arg auth_kc_db_username "$auth_kc_db_username" \
  --arg auth_kc_db_password_secret_arn "$auth_kc_db_password_secret_arn" \
  --arg identidade_db_url "$identidade_db_url" \
  --arg identidade_db_username "$identidade_db_username" \
  --arg identidade_db_password_secret_arn "$identidade_db_password_secret_arn" \
  --arg thimisu_db_url "$thimisu_db_url" \
  --arg thimisu_db_username "$thimisu_db_username" \
  --arg thimisu_db_password_secret_arn "$thimisu_db_password_secret_arn" '
  .containerDefinitions |= map(
    if .name == $container then
      .image = $image
    else
      .
    end
  )
  | .containerDefinitions |= map(
      .environment |= map(
        if .value == "__AUTH_KC_DB_HOST__" then
          .value = $auth_kc_db_host
        elif .value == "__AUTH_KC_DB_NAME__" then
          .value = $auth_kc_db_name
        elif .value == "__AUTH_KC_DB_USERNAME__" then
          .value = $auth_kc_db_username
        elif .value == "__IDENTIDADE_DB_URL__" then
          .value = $identidade_db_url
        elif .value == "__IDENTIDADE_DB_USERNAME__" then
          .value = $identidade_db_username
        elif .value == "__THIMISU_DB_URL__" then
          .value = $thimisu_db_url
        elif .value == "__THIMISU_DB_USERNAME__" then
          .value = $thimisu_db_username
        else
          .
        end
      )
    )
  | .containerDefinitions |= map(
      .secrets |= map(
        if .valueFrom == "__AUTH_KC_DB_PASSWORD_SECRET_ARN__" then
          .valueFrom = $auth_kc_db_password_secret_arn
        elif .valueFrom == "__IDENTIDADE_DB_PASSWORD_SECRET_ARN__" then
          .valueFrom = $identidade_db_password_secret_arn
        elif .valueFrom == "__THIMISU_DB_PASSWORD_SECRET_ARN__" then
          .valueFrom = $thimisu_db_password_secret_arn
        else
          .
        end
      )
    )
' "$template" >"$rendered_task"

cleanup() {
  if [ -z "$render_output" ] && [ -f "$rendered_task" ]; then
    rm -f "$rendered_task"
  fi
}
trap cleanup EXIT

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
echo "CLUSTER=${cluster}"
echo "REGION=${region}"
echo "TASK_FAMILY=${family}"
echo "CONTAINER_NAME=${container_name}"
echo "ECS_SERVICE=${ecs_service}"
echo "IMAGE=${image}"
echo "TASK_DEFINITION_TEMPLATE=${template}"
echo "TASK_DEFINITION_RENDERED=${rendered_task}"
echo "DB_OVERRIDES_FILE=${db_overrides_file:-<none>}"
echo "WAIT_FOR_STABLE=${wait_for_stable}"
echo "AUTH_KC_DB_HOST=${auth_kc_db_host}"
echo "AUTH_KC_DB_NAME=${auth_kc_db_name}"
echo "AUTH_KC_DB_USERNAME=${auth_kc_db_username}"
echo "AUTH_KC_DB_PASSWORD_SECRET_ARN=${auth_kc_db_password_secret_arn}"
echo "IDENTIDADE_DB_URL=${identidade_db_url}"
echo "IDENTIDADE_DB_USERNAME=${identidade_db_username}"
echo "IDENTIDADE_DB_PASSWORD_SECRET_ARN=${identidade_db_password_secret_arn}"
echo "THIMISU_DB_URL=${thimisu_db_url}"
echo "THIMISU_DB_USERNAME=${thimisu_db_username}"
echo "THIMISU_DB_PASSWORD_SECRET_ARN=${thimisu_db_password_secret_arn}"

if [ "$dry_run" = "true" ]; then
  jq -r '.containerDefinitions[0].image' "$rendered_task" | sed 's/^/RENDERED_IMAGE=/'
  run_cmd aws ecs register-task-definition --region "$region" --cli-input-json "file://${rendered_task}"
  run_cmd aws ecs update-service --region "$region" --cluster "$cluster" --service "$ecs_service" \
    --task-definition "${family}:<revision>" --force-new-deployment
  if [ "$wait_for_stable" = "true" ]; then
    run_cmd aws ecs wait services-stable --region "$region" --cluster "$cluster" --services "$ecs_service"
  fi
  run_cmd aws ecs describe-services --region "$region" --cluster "$cluster" --services "$ecs_service"
  exit 0
fi

task_definition_arn="$(
  run_cmd aws ecs register-task-definition \
    --region "$region" \
    --cli-input-json "file://${rendered_task}" \
    --query 'taskDefinition.taskDefinitionArn' \
    --output text
)"

run_cmd aws ecs update-service \
  --region "$region" \
  --cluster "$cluster" \
  --service "$ecs_service" \
  --task-definition "$task_definition_arn" \
  --force-new-deployment >/dev/null

if [ "$wait_for_stable" = "true" ]; then
  run_cmd aws ecs wait services-stable \
    --region "$region" \
    --cluster "$cluster" \
    --services "$ecs_service"
fi

run_cmd aws ecs describe-services \
  --region "$region" \
  --cluster "$cluster" \
  --services "$ecs_service" \
  --query 'services[0].{Service:serviceName,TaskDefinition:taskDefinition,Running:runningCount,Status:status,RolloutState:deployments[0].rolloutState}' \
  --output table
