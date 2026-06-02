#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
LAMBDA_SOURCE_DIR="${SCRIPT_DIR}/lambda/rds_password_auth_failure_fallback"
LAMBDA_SOURCE_FILE="${LAMBDA_SOURCE_DIR}/handler.py"

usage() {
  cat <<'EOF'
uso: configure_hml_rds_password_auth_failure_fallback.sh [opcoes]

Configura o fallback que observa logs com erro de senha antiga do RDS e,
apos validar a senha atual por uma task Fargate de psql, forca novo deploy
dos servicos ECS dependentes da senha do banco.

Opcoes:
  --cluster <nome>                   Cluster ECS alvo.
  --services <csv>                   Lista CSV dos servicos ECS a redeployar.
  --log-groups <csv>                 Lista CSV dos log groups monitorados.
  --function-name <nome>             Nome da funcao Lambda.
  --role-name <nome>                 Nome da role IAM da Lambda.
  --filter-name <nome>               Nome do subscription filter.
  --filter-pattern <padrao>          Padrao CloudWatch Logs.
  --validation-task-definition <td>  Task definition Fargate que executa psql.
  --validation-container-name <nome> Container da task de validacao.
  --validation-subnets <csv>         Subnets privadas para a task de validacao.
  --validation-security-groups <csv> Security groups para a task de validacao.
  --validation-database <db>         Banco usado para SELECT 1.
  --cooldown-parameter-name <nome>   Parametro SSM que guarda ultimo redeploy.
  --cooldown-seconds <segundos>      Janela minima entre redeploys.
  --region <aws-region>              Regiao AWS.
  --profile <aws-profile>            Profile AWS CLI.
  --account-id <id>                  Account ID AWS. Se omitido, resolve via STS.
  --memory-size <mb>                 Memoria da Lambda.
  --timeout <segundos>               Timeout da Lambda.
  --waiter-delay <segundos>          Intervalo entre checagens de estabilidade ECS.
  --waiter-max-attempts <numero>     Maximo de checagens por servico ECS.
  --dry-run                          Apenas imprime os comandos planejados.
  -h, --help                         Mostra esta ajuda.
EOF
}

fail() {
  echo "erro: $*" >&2
  exit 2
}

require_file() {
  local path="$1"
  [ -f "$path" ] || fail "arquivo obrigatorio ausente: ${path}"
}

require_cmd() {
  local cmd="$1"
  command -v "$cmd" >/dev/null 2>&1 || fail "comando obrigatorio ausente: ${cmd}"
}

csv_to_json_array() {
  local csv="$1"
  python3 - "$csv" <<'PY'
import json
import sys

items = [item.strip() for item in sys.argv[1].split(",") if item.strip()]
print(json.dumps(items))
PY
}

sanitize_statement_id_suffix() {
  local raw="$1"
  python3 - "$raw" <<'PY'
import re
import sys

value = re.sub(r"[^A-Za-z0-9]", "", sys.argv[1])
print(value[:80] or "LogGroup")
PY
}

CLUSTER="eickrono-hml"
SERVICES_CSV="autenticacao-api-hml,auth-hml,identidade-hml,thimisu-backend-hml"
LOG_GROUPS_CSV="/ecs/hml/autenticacao,/ecs/hml/auth,/ecs/hml/identidade,/ecs/hml/thimisu-backend"
FUNCTION_NAME="eickrono-hml-rds-password-auth-failure-fallback"
ROLE_NAME="eickrono-hml-rds-password-auth-failure-fallback-role"
FILTER_NAME="eickrono-hml-rds-password-auth-failure-fallback"
FILTER_PATTERN='"password authentication failed" "eickrono_admin"'
VALIDATION_TASK_DEFINITION="eickrono-hml-db-query-codex:1"
VALIDATION_CONTAINER_NAME="psql"
VALIDATION_SUBNETS_CSV="subnet-064c1362d7b4635db,subnet-0d91dc50495fb52c9"
VALIDATION_SECURITY_GROUPS_CSV="sg-05d90b4911b4326b8"
VALIDATION_DATABASE="eickrono_identidade_hml"
COOLDOWN_PARAMETER_NAME="/eickrono/hml/rds-password-auth-failure-fallback/last-run"
COOLDOWN_SECONDS="900"
REGION="sa-east-1"
PROFILE=""
ACCOUNT_ID=""
MEMORY_SIZE="256"
TIMEOUT="900"
WAITER_DELAY_SECONDS="15"
WAITER_MAX_ATTEMPTS="40"
DRY_RUN="false"

while [ "$#" -gt 0 ]; do
  case "$1" in
    --cluster)
      CLUSTER="$2"
      shift 2
      ;;
    --services)
      SERVICES_CSV="$2"
      shift 2
      ;;
    --log-groups)
      LOG_GROUPS_CSV="$2"
      shift 2
      ;;
    --function-name)
      FUNCTION_NAME="$2"
      shift 2
      ;;
    --role-name)
      ROLE_NAME="$2"
      shift 2
      ;;
    --filter-name)
      FILTER_NAME="$2"
      shift 2
      ;;
    --filter-pattern)
      FILTER_PATTERN="$2"
      shift 2
      ;;
    --validation-task-definition)
      VALIDATION_TASK_DEFINITION="$2"
      shift 2
      ;;
    --validation-container-name)
      VALIDATION_CONTAINER_NAME="$2"
      shift 2
      ;;
    --validation-subnets)
      VALIDATION_SUBNETS_CSV="$2"
      shift 2
      ;;
    --validation-security-groups)
      VALIDATION_SECURITY_GROUPS_CSV="$2"
      shift 2
      ;;
    --validation-database)
      VALIDATION_DATABASE="$2"
      shift 2
      ;;
    --cooldown-parameter-name)
      COOLDOWN_PARAMETER_NAME="$2"
      shift 2
      ;;
    --cooldown-seconds)
      COOLDOWN_SECONDS="$2"
      shift 2
      ;;
    --region)
      REGION="$2"
      shift 2
      ;;
    --profile)
      PROFILE="$2"
      shift 2
      ;;
    --account-id)
      ACCOUNT_ID="$2"
      shift 2
      ;;
    --memory-size)
      MEMORY_SIZE="$2"
      shift 2
      ;;
    --timeout)
      TIMEOUT="$2"
      shift 2
      ;;
    --waiter-delay)
      WAITER_DELAY_SECONDS="$2"
      shift 2
      ;;
    --waiter-max-attempts)
      WAITER_MAX_ATTEMPTS="$2"
      shift 2
      ;;
    --dry-run)
      DRY_RUN="true"
      shift
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      fail "opcao desconhecida: $1"
      ;;
  esac
done

require_file "$LAMBDA_SOURCE_FILE"
require_cmd python3
require_cmd aws

[ -n "$SERVICES_CSV" ] || fail "--services nao pode ser vazio"
[ -n "$LOG_GROUPS_CSV" ] || fail "--log-groups nao pode ser vazio"
[ -n "$VALIDATION_TASK_DEFINITION" ] || fail "--validation-task-definition nao pode ser vazio"
[ -n "$VALIDATION_CONTAINER_NAME" ] || fail "--validation-container-name nao pode ser vazio"
[ -n "$VALIDATION_SUBNETS_CSV" ] || fail "--validation-subnets nao pode ser vazio"
[ -n "$VALIDATION_SECURITY_GROUPS_CSV" ] || fail "--validation-security-groups nao pode ser vazio"
[ -n "$VALIDATION_DATABASE" ] || fail "--validation-database nao pode ser vazio"
[ -n "$COOLDOWN_PARAMETER_NAME" ] || fail "--cooldown-parameter-name nao pode ser vazio"

AWS_CMD=(aws)
if [ -n "$PROFILE" ]; then
  AWS_CMD+=(--profile "$PROFILE")
fi
AWS_CMD+=(--region "$REGION")

if [ -z "$ACCOUNT_ID" ] && [ "$DRY_RUN" = "false" ]; then
  ACCOUNT_ID="$("${AWS_CMD[@]}" sts get-caller-identity --query Account --output text)"
fi
if [ -z "$ACCOUNT_ID" ]; then
  ACCOUNT_ID="000000000000"
fi

SERVICES_JSON="$(csv_to_json_array "$SERVICES_CSV")"
LOG_GROUPS_JSON="$(csv_to_json_array "$LOG_GROUPS_CSV")"
VALIDATION_TASK_FAMILY="${VALIDATION_TASK_DEFINITION%%:*}"
LAMBDA_ROLE_ARN="arn:aws:iam::${ACCOUNT_ID}:role/${ROLE_NAME}"

ASSUME_ROLE_POLICY_FILE="$(mktemp)"
INLINE_POLICY_FILE="$(mktemp)"
LAMBDA_ENV_FILE="$(mktemp)"
LOG_GROUPS_FILE="$(mktemp)"
ZIP_PATH="$(mktemp -u).zip"
trap 'rm -f "$ASSUME_ROLE_POLICY_FILE" "$INLINE_POLICY_FILE" "$LAMBDA_ENV_FILE" "$LOG_GROUPS_FILE" "$ZIP_PATH"' EXIT

cat >"$ASSUME_ROLE_POLICY_FILE" <<'EOF'
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Principal": {
        "Service": "lambda.amazonaws.com"
      },
      "Action": "sts:AssumeRole"
    }
  ]
}
EOF

python3 - \
  "$REGION" \
  "$ACCOUNT_ID" \
  "$CLUSTER" \
  "$SERVICES_JSON" \
  "$VALIDATION_TASK_FAMILY" \
  "$COOLDOWN_PARAMETER_NAME" >"$INLINE_POLICY_FILE" <<'PY'
import json
import sys

region = sys.argv[1]
account_id = sys.argv[2]
cluster = sys.argv[3]
services = json.loads(sys.argv[4])
validation_task_family = sys.argv[5]
cooldown_parameter_name = sys.argv[6]

service_resources = [
    f"arn:aws:ecs:{region}:{account_id}:service/{cluster}/{service}"
    for service in services
]

policy = {
    "Version": "2012-10-17",
    "Statement": [
        {
            "Sid": "AllowForceNewDeploymentOnTrackedServices",
            "Effect": "Allow",
            "Action": [
                "ecs:UpdateService",
                "ecs:DescribeServices"
            ],
            "Resource": service_resources,
        },
        {
            "Sid": "AllowRunValidationTask",
            "Effect": "Allow",
            "Action": [
                "ecs:RunTask"
            ],
            "Resource": [
                (
                    f"arn:aws:ecs:{region}:{account_id}:task-definition/"
                    f"{validation_task_family}:*"
                )
            ],
        },
        {
            "Sid": "AllowDescribeValidationTasks",
            "Effect": "Allow",
            "Action": [
                "ecs:DescribeTasks"
            ],
            "Resource": [
                f"arn:aws:ecs:{region}:{account_id}:task/{cluster}/*"
            ],
        },
        {
            "Sid": "AllowPassValidationTaskRoles",
            "Effect": "Allow",
            "Action": "iam:PassRole",
            "Resource": "*",
            "Condition": {
                "StringEquals": {
                    "iam:PassedToService": "ecs-tasks.amazonaws.com"
                }
            },
        },
        {
            "Sid": "AllowCooldownParameter",
            "Effect": "Allow",
            "Action": [
                "ssm:GetParameter",
                "ssm:PutParameter"
            ],
            "Resource": [
                f"arn:aws:ssm:{region}:{account_id}:parameter{cooldown_parameter_name}"
            ],
        },
    ],
}

print(json.dumps(policy))
PY

python3 - \
  "$CLUSTER" \
  "$SERVICES_CSV" \
  "$COOLDOWN_PARAMETER_NAME" \
  "$COOLDOWN_SECONDS" \
  "$VALIDATION_TASK_DEFINITION" \
  "$VALIDATION_CONTAINER_NAME" \
  "$VALIDATION_SUBNETS_CSV" \
  "$VALIDATION_SECURITY_GROUPS_CSV" \
  "$VALIDATION_DATABASE" \
  "$WAITER_DELAY_SECONDS" \
  "$WAITER_MAX_ATTEMPTS" >"$LAMBDA_ENV_FILE" <<'PY'
import json
import sys

payload = {
    "Variables": {
        "ECS_CLUSTER": sys.argv[1],
        "ECS_SERVICES": sys.argv[2],
        "COOLDOWN_PARAMETER_NAME": sys.argv[3],
        "COOLDOWN_SECONDS": sys.argv[4],
        "VALIDATION_TASK_DEFINITION": sys.argv[5],
        "VALIDATION_CONTAINER_NAME": sys.argv[6],
        "VALIDATION_SUBNETS": sys.argv[7],
        "VALIDATION_SECURITY_GROUPS": sys.argv[8],
        "VALIDATION_DATABASE": sys.argv[9],
        "SERVICE_STABLE_WAITER_DELAY_SECONDS": sys.argv[10],
        "SERVICE_STABLE_WAITER_MAX_ATTEMPTS": sys.argv[11],
    }
}

print(json.dumps(payload))
PY

build_zip() {
  local target_zip="$1"
  python3 - "$LAMBDA_SOURCE_DIR" "$target_zip" <<'PY'
from pathlib import Path
import sys
import zipfile

source_dir = Path(sys.argv[1])
target_zip = Path(sys.argv[2])

with zipfile.ZipFile(target_zip, "w", compression=zipfile.ZIP_DEFLATED) as archive:
    archive.write(source_dir / "handler.py", arcname="handler.py")
PY
}

create_lambda_with_retry() {
  local attempts=0
  local max_attempts=6
  while true; do
    set +e
    local output
    output="$("${AWS_CMD[@]}" lambda create-function \
      --function-name "$FUNCTION_NAME" \
      --runtime python3.12 \
      --handler handler.lambda_handler \
      --role "$LAMBDA_ROLE_ARN" \
      --memory-size "$MEMORY_SIZE" \
      --timeout "$TIMEOUT" \
      --zip-file "fileb://${ZIP_PATH}" \
      --environment "file://${LAMBDA_ENV_FILE}" 2>&1)"
    local status=$?
    set -e
    if [ "$status" -eq 0 ]; then
      return 0
    fi
    attempts=$((attempts + 1))
    if [[ "$output" != *"cannot be assumed by Lambda"* ]] || [ "$attempts" -ge "$max_attempts" ]; then
      echo "$output" >&2
      return "$status"
    fi
    sleep 10
  done
}

wait_lambda_updated() {
  "${AWS_CMD[@]}" lambda wait function-updated \
    --function-name "$FUNCTION_NAME"
}

print_plan() {
  cat <<EOF
CLUSTER=${CLUSTER}
SERVICES=${SERVICES_CSV}
LOG_GROUPS=${LOG_GROUPS_CSV}
FUNCTION_NAME=${FUNCTION_NAME}
ROLE_NAME=${ROLE_NAME}
FILTER_NAME=${FILTER_NAME}
FILTER_PATTERN=${FILTER_PATTERN}
VALIDATION_TASK_DEFINITION=${VALIDATION_TASK_DEFINITION}
VALIDATION_CONTAINER_NAME=${VALIDATION_CONTAINER_NAME}
VALIDATION_SUBNETS=${VALIDATION_SUBNETS_CSV}
VALIDATION_SECURITY_GROUPS=${VALIDATION_SECURITY_GROUPS_CSV}
VALIDATION_DATABASE=${VALIDATION_DATABASE}
COOLDOWN_PARAMETER_NAME=${COOLDOWN_PARAMETER_NAME}
COOLDOWN_SECONDS=${COOLDOWN_SECONDS}
REGION=${REGION}
ACCOUNT_ID=${ACCOUNT_ID}
MEMORY_SIZE=${MEMORY_SIZE}
TIMEOUT=${TIMEOUT}
WAITER_DELAY_SECONDS=${WAITER_DELAY_SECONDS}
WAITER_MAX_ATTEMPTS=${WAITER_MAX_ATTEMPTS}
LAMBDA_SOURCE=${LAMBDA_SOURCE_FILE}
LAMBDA_ROLE_ARN=${LAMBDA_ROLE_ARN}

aws iam create-role --role-name ${ROLE_NAME} --assume-role-policy-document file://${ASSUME_ROLE_POLICY_FILE}
aws iam attach-role-policy --role-name ${ROLE_NAME} --policy-arn arn:aws:iam::aws:policy/service-role/AWSLambdaBasicExecutionRole
aws iam put-role-policy --role-name ${ROLE_NAME} --policy-name ${ROLE_NAME}-fallback --policy-document file://${INLINE_POLICY_FILE}
aws lambda create-function|update-function-code --function-name ${FUNCTION_NAME}
aws lambda update-function-configuration --function-name ${FUNCTION_NAME} --environment file://${LAMBDA_ENV_FILE}
aws lambda add-permission --function-name ${FUNCTION_NAME} --principal logs.${REGION}.amazonaws.com --source-arn <log-group-arn>
aws logs put-subscription-filter --log-group-name <log-group> --filter-name ${FILTER_NAME} --filter-pattern '${FILTER_PATTERN}' --destination-arn <lambda-arn>
EOF
}

if [ "$DRY_RUN" = "true" ]; then
  print_plan
  exit 0
fi

build_zip "$ZIP_PATH"

if ! "${AWS_CMD[@]}" iam get-role --role-name "$ROLE_NAME" >/dev/null 2>&1; then
  "${AWS_CMD[@]}" iam create-role \
    --role-name "$ROLE_NAME" \
    --assume-role-policy-document "file://${ASSUME_ROLE_POLICY_FILE}" >/dev/null
fi

"${AWS_CMD[@]}" iam attach-role-policy \
  --role-name "$ROLE_NAME" \
  --policy-arn arn:aws:iam::aws:policy/service-role/AWSLambdaBasicExecutionRole >/dev/null

"${AWS_CMD[@]}" iam put-role-policy \
  --role-name "$ROLE_NAME" \
  --policy-name "${ROLE_NAME}-fallback" \
  --policy-document "file://${INLINE_POLICY_FILE}" >/dev/null

if ! "${AWS_CMD[@]}" lambda get-function --function-name "$FUNCTION_NAME" >/dev/null 2>&1; then
  create_lambda_with_retry >/dev/null
  wait_lambda_updated
else
  "${AWS_CMD[@]}" lambda update-function-code \
    --function-name "$FUNCTION_NAME" \
    --zip-file "fileb://${ZIP_PATH}" >/dev/null
  wait_lambda_updated
  "${AWS_CMD[@]}" lambda update-function-configuration \
    --function-name "$FUNCTION_NAME" \
    --role "$LAMBDA_ROLE_ARN" \
    --handler handler.lambda_handler \
    --runtime python3.12 \
    --memory-size "$MEMORY_SIZE" \
    --timeout "$TIMEOUT" \
    --environment "file://${LAMBDA_ENV_FILE}" >/dev/null
  wait_lambda_updated
fi

FUNCTION_ARN="$("${AWS_CMD[@]}" lambda get-function --function-name "$FUNCTION_NAME" --query 'Configuration.FunctionArn' --output text)"

python3 - "$LOG_GROUPS_JSON" <<'PY' > "$LOG_GROUPS_FILE"
import json
import sys

for item in json.loads(sys.argv[1]):
    print(item)
PY

while IFS= read -r log_group; do
  [ -n "$log_group" ] || continue
  statement_suffix="$(sanitize_statement_id_suffix "$log_group")"
  statement_id="AllowLogs${statement_suffix}"
  source_arn="arn:aws:logs:${REGION}:${ACCOUNT_ID}:log-group:${log_group}:*"

  set +e
  "${AWS_CMD[@]}" lambda add-permission \
    --function-name "$FUNCTION_NAME" \
    --statement-id "$statement_id" \
    --action lambda:InvokeFunction \
    --principal "logs.${REGION}.amazonaws.com" \
    --source-arn "$source_arn" >/dev/null 2>&1
  permission_status=$?
  set -e
  if [ "$permission_status" -ne 0 ]; then
    :
  fi

  "${AWS_CMD[@]}" logs put-subscription-filter \
    --log-group-name "$log_group" \
    --filter-name "$FILTER_NAME" \
    --filter-pattern "$FILTER_PATTERN" \
    --destination-arn "$FUNCTION_ARN" >/dev/null
done <"$LOG_GROUPS_FILE"

cat <<EOF
ok
FUNCTION_NAME=${FUNCTION_NAME}
FUNCTION_ARN=${FUNCTION_ARN}
ROLE_NAME=${ROLE_NAME}
CLUSTER=${CLUSTER}
SERVICES=${SERVICES_CSV}
LOG_GROUPS=${LOG_GROUPS_CSV}
FILTER_NAME=${FILTER_NAME}
VALIDATION_TASK_DEFINITION=${VALIDATION_TASK_DEFINITION}
COOLDOWN_PARAMETER_NAME=${COOLDOWN_PARAMETER_NAME}
EOF
