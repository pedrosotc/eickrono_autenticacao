#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
LAMBDA_SOURCE_DIR="${SCRIPT_DIR}/lambda/rds_rotation_ecs_redeploy"
LAMBDA_SOURCE_FILE="${LAMBDA_SOURCE_DIR}/handler.py"

usage() {
  cat <<'EOF'
uso: configure_stg_rds_rotation_redeploy.sh [opcoes]

Configura a automacao que observa a rotacao bem-sucedida do segredo RDS e
forca novo deploy dos servicos ECS dependentes da senha do banco.

Opcoes:
  --cluster <nome>                Cluster ECS alvo.
  --services <csv>                Lista CSV dos servicos ECS a redeployar.
  --secret-arn <arn>              ARN do segredo do RDS monitorado.
  --function-name <nome>          Nome da funcao Lambda.
  --rule-name <nome>              Nome da regra EventBridge.
  --role-name <nome>              Nome da role IAM da Lambda.
  --region <aws-region>           Regiao AWS.
  --profile <aws-profile>         Profile AWS CLI.
  --account-id <id>               Account ID AWS. Se omitido, resolve via STS.
  --memory-size <mb>              Memoria da Lambda.
  --timeout <segundos>            Timeout da Lambda.
  --waiter-delay <segundos>       Intervalo entre checagens de estabilidade ECS.
  --waiter-max-attempts <numero>  Maximo de checagens por servico ECS.
  --dry-run                       Apenas imprime os comandos planejados.
  -h, --help                      Mostra esta ajuda.
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

json_escape() {
  python3 - "$1" <<'PY'
import json
import sys

print(json.dumps(sys.argv[1]))
PY
}

CLUSTER="eickrono-stg"
SERVICES_CSV="autenticacao-api-stg,auth-stg,identidade-stg,thimisu-backend-stg"
SECRET_ARN="arn:aws:secretsmanager:sa-east-1:531708494702:secret:rds!db-7df15f56-c831-40b7-be42-ebd935108b06-22Dwvf"
FUNCTION_NAME="eickrono-stg-rds-rotation-ecs-redeploy"
RULE_NAME="eickrono-stg-rds-rotation-succeeded"
ROLE_NAME="eickrono-stg-rds-rotation-ecs-redeploy-role"
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
    --secret-arn)
      SECRET_ARN="$2"
      shift 2
      ;;
    --function-name)
      FUNCTION_NAME="$2"
      shift 2
      ;;
    --rule-name)
      RULE_NAME="$2"
      shift 2
      ;;
    --role-name)
      ROLE_NAME="$2"
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

if [ -z "$SERVICES_CSV" ]; then
  fail "--services nao pode ser vazio"
fi

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
SERVICES_ENV="${SERVICES_CSV}"
LAMBDA_ROLE_ARN="arn:aws:iam::${ACCOUNT_ID}:role/${ROLE_NAME}"
RULE_ARN="arn:aws:events:${REGION}:${ACCOUNT_ID}:rule/${RULE_NAME}"

ASSUME_ROLE_POLICY_FILE="$(mktemp)"
INLINE_POLICY_FILE="$(mktemp)"
EVENT_PATTERN_FILE="$(mktemp)"
LAMBDA_ENV_FILE="$(mktemp)"
ZIP_PATH="$(mktemp -u).zip"
trap 'rm -f "$ASSUME_ROLE_POLICY_FILE" "$INLINE_POLICY_FILE" "$EVENT_PATTERN_FILE" "$LAMBDA_ENV_FILE" "$ZIP_PATH"' EXIT

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

python3 - "$REGION" "$ACCOUNT_ID" "$CLUSTER" "$SERVICES_JSON" >"$INLINE_POLICY_FILE" <<'PY'
import json
import sys

region = sys.argv[1]
account_id = sys.argv[2]
cluster = sys.argv[3]
services = json.loads(sys.argv[4])

resources = [
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
            "Resource": resources
        }
    ]
}

print(json.dumps(policy))
PY

cat >"$EVENT_PATTERN_FILE" <<'EOF'
{
  "source": ["aws.secretsmanager"],
  "detail-type": [
    "AWS Service Event via CloudTrail",
    "AWS API Call via CloudTrail"
  ],
  "detail": {
    "eventSource": ["secretsmanager.amazonaws.com"],
    "eventName": ["RotationSucceeded"]
  }
}
EOF

python3 - \
  "$SECRET_ARN" \
  "$CLUSTER" \
  "$SERVICES_ENV" \
  "$WAITER_DELAY_SECONDS" \
  "$WAITER_MAX_ATTEMPTS" >"$LAMBDA_ENV_FILE" <<'PY'
import json
import sys

payload = {
    "Variables": {
        "TARGET_SECRET_ARN": sys.argv[1],
        "ECS_CLUSTER": sys.argv[2],
        "ECS_SERVICES": sys.argv[3],
        "SERVICE_STABLE_WAITER_DELAY_SECONDS": sys.argv[4],
        "SERVICE_STABLE_WAITER_MAX_ATTEMPTS": sys.argv[5],
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
SECRET_ARN=${SECRET_ARN}
FUNCTION_NAME=${FUNCTION_NAME}
RULE_NAME=${RULE_NAME}
ROLE_NAME=${ROLE_NAME}
REGION=${REGION}
ACCOUNT_ID=${ACCOUNT_ID}
MEMORY_SIZE=${MEMORY_SIZE}
TIMEOUT=${TIMEOUT}
WAITER_DELAY_SECONDS=${WAITER_DELAY_SECONDS}
WAITER_MAX_ATTEMPTS=${WAITER_MAX_ATTEMPTS}
LAMBDA_SOURCE=${LAMBDA_SOURCE_FILE}
LAMBDA_ROLE_ARN=${LAMBDA_ROLE_ARN}
RULE_ARN=${RULE_ARN}

aws iam create-role --role-name ${ROLE_NAME} --assume-role-policy-document file://${ASSUME_ROLE_POLICY_FILE}
aws iam attach-role-policy --role-name ${ROLE_NAME} --policy-arn arn:aws:iam::aws:policy/service-role/AWSLambdaBasicExecutionRole
aws iam put-role-policy --role-name ${ROLE_NAME} --policy-name ${ROLE_NAME}-ecs-redeploy --policy-document file://${INLINE_POLICY_FILE}
aws lambda create-function|update-function-code --function-name ${FUNCTION_NAME}
aws lambda update-function-configuration --function-name ${FUNCTION_NAME} --environment file://${LAMBDA_ENV_FILE}
aws events put-rule --name ${RULE_NAME} --event-pattern file://${EVENT_PATTERN_FILE}
aws lambda add-permission --function-name ${FUNCTION_NAME} --statement-id AllowExecutionFrom${RULE_NAME} --principal events.amazonaws.com --source-arn ${RULE_ARN}
aws events put-targets --rule ${RULE_NAME} --targets Id=1,Arn=<lambda-arn>
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
  --policy-name "${ROLE_NAME}-ecs-redeploy" \
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

"${AWS_CMD[@]}" events put-rule \
  --name "$RULE_NAME" \
  --event-pattern "file://${EVENT_PATTERN_FILE}" >/dev/null

FUNCTION_ARN="$("${AWS_CMD[@]}" lambda get-function --function-name "$FUNCTION_NAME" --query 'Configuration.FunctionArn' --output text)"

set +e
"${AWS_CMD[@]}" lambda add-permission \
  --function-name "$FUNCTION_NAME" \
  --statement-id "AllowExecutionFrom${RULE_NAME}" \
  --action lambda:InvokeFunction \
  --principal events.amazonaws.com \
  --source-arn "$RULE_ARN" >/dev/null 2>&1
permission_status=$?
set -e
if [ "$permission_status" -ne 0 ]; then
  :
fi

"${AWS_CMD[@]}" events put-targets \
  --rule "$RULE_NAME" \
  --targets "Id=1,Arn=${FUNCTION_ARN}" >/dev/null

cat <<EOF
ok
FUNCTION_NAME=${FUNCTION_NAME}
FUNCTION_ARN=${FUNCTION_ARN}
RULE_NAME=${RULE_NAME}
RULE_ARN=${RULE_ARN}
ROLE_NAME=${ROLE_NAME}
SECRET_ARN=${SECRET_ARN}
CLUSTER=${CLUSTER}
SERVICES=${SERVICES_CSV}
EOF
