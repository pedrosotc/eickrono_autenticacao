#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SCRIPT_UNDER_TEST="${ROOT_DIR}/ecs/configure_hml_rds_rotation_redeploy.sh"

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
    --services auth-hml,identidade-hml,thimisu-backend-hml \
    --secret-arn arn:aws:secretsmanager:sa-east-1:531708494702:secret:rds!db-abc \
    --function-name eickrono-hml-rds-rotation-ecs-redeploy \
    --rule-name eickrono-hml-rds-rotation-succeeded \
    --role-name eickrono-hml-rds-rotation-ecs-redeploy-role \
    --account-id 531708494702 \
    --dry-run)"

  assert_contains "$output" "CLUSTER=eickrono-hml"
  assert_contains "$output" "SERVICES=auth-hml,identidade-hml,thimisu-backend-hml"
  assert_contains "$output" "SECRET_ARN=arn:aws:secretsmanager:sa-east-1:531708494702:secret:rds!db-abc"
  assert_contains "$output" "FUNCTION_NAME=eickrono-hml-rds-rotation-ecs-redeploy"
  assert_contains "$output" "RULE_NAME=eickrono-hml-rds-rotation-succeeded"
  assert_contains "$output" "ROLE_NAME=eickrono-hml-rds-rotation-ecs-redeploy-role"
  assert_contains "$output" "aws iam create-role"
  assert_contains "$output" "aws lambda create-function|update-function-code"
  assert_contains "$output" "aws events put-rule"
  assert_contains "$output" "aws events put-targets"
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
