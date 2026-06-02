#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SCRIPT_UNDER_TEST="${ROOT_DIR}/ecs/configure_hml_rds_password_auth_failure_fallback.sh"

fail() {
  echo "FAIL: $*" >&2
  exit 1
}

assert_contains() {
  local haystack="$1"
  local needle="$2"
  if [[ "$haystack" != *"$needle"* ]]; then
    fail "nao encontrou trecho esperado: ${needle}"
  fi
}

assert_equals() {
  local expected="$1"
  local actual="$2"
  if [ "$expected" != "$actual" ]; then
    fail "esperado '${expected}', obtido '${actual}'"
  fi
}

test_dry_run_prints_expected_plan() {
  local output
  output="$("$SCRIPT_UNDER_TEST" \
    --cluster eickrono-hml \
    --services autenticacao-api-hml,auth-hml,identidade-hml,thimisu-backend-hml \
    --log-groups /ecs/hml/autenticacao,/ecs/hml/auth,/ecs/hml/identidade,/ecs/hml/thimisu-backend \
    --function-name eickrono-hml-rds-password-auth-failure-fallback \
    --role-name eickrono-hml-rds-password-auth-failure-fallback-role \
    --filter-name eickrono-hml-rds-password-auth-failure-fallback \
    --validation-task-definition eickrono-hml-db-query-codex:1 \
    --validation-container-name psql \
    --validation-subnets subnet-1,subnet-2 \
    --validation-security-groups sg-1 \
    --validation-database eickrono_identidade_hml \
    --cooldown-parameter-name /eickrono/hml/rds-password-auth-failure-fallback/last-run \
    --cooldown-seconds 900 \
    --account-id 531708494702 \
    --dry-run)"

  assert_contains "$output" "CLUSTER=eickrono-hml"
  assert_contains "$output" "SERVICES=autenticacao-api-hml,auth-hml,identidade-hml,thimisu-backend-hml"
  assert_contains "$output" "LOG_GROUPS=/ecs/hml/autenticacao,/ecs/hml/auth,/ecs/hml/identidade,/ecs/hml/thimisu-backend"
  assert_contains "$output" "FUNCTION_NAME=eickrono-hml-rds-password-auth-failure-fallback"
  assert_contains "$output" "ROLE_NAME=eickrono-hml-rds-password-auth-failure-fallback-role"
  assert_contains "$output" 'FILTER_PATTERN="password authentication failed" "eickrono_admin"'
  assert_contains "$output" "VALIDATION_TASK_DEFINITION=eickrono-hml-db-query-codex:1"
  assert_contains "$output" "VALIDATION_CONTAINER_NAME=psql"
  assert_contains "$output" "VALIDATION_SUBNETS=subnet-1,subnet-2"
  assert_contains "$output" "VALIDATION_SECURITY_GROUPS=sg-1"
  assert_contains "$output" "VALIDATION_DATABASE=eickrono_identidade_hml"
  assert_contains "$output" "COOLDOWN_PARAMETER_NAME=/eickrono/hml/rds-password-auth-failure-fallback/last-run"
  assert_contains "$output" "COOLDOWN_SECONDS=900"
  assert_contains "$output" "TIMEOUT=900"
  assert_contains "$output" "WAITER_DELAY_SECONDS=15"
  assert_contains "$output" "WAITER_MAX_ATTEMPTS=40"
  assert_contains "$output" "aws iam create-role"
  assert_contains "$output" "aws lambda create-function|update-function-code"
  assert_contains "$output" "aws lambda add-permission"
  assert_contains "$output" "aws logs put-subscription-filter"
}

test_unknown_option_fails() {
  local output
  set +e
  output="$("$SCRIPT_UNDER_TEST" --opcao-invalida 2>&1)"
  local status=$?
  set -e
  assert_equals "2" "$status"
  assert_contains "$output" "opcao desconhecida"
}

main() {
  test_dry_run_prints_expected_plan
  test_unknown_option_fails
  echo "ok"
}

main "$@"
