#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SCRIPT_UNDER_TEST="${ROOT_DIR}/ecs/build_push_stg_image.sh"

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

test_missing_required_args() {
  local output
  set +e
  output="$("$SCRIPT_UNDER_TEST" 2>&1)"
  local status=$?
  set -e
  assert_equals "2" "$status"
  assert_contains "$output" "uso: build_push_stg_image.sh"
}

test_invalid_service() {
  local output
  set +e
  output="$("$SCRIPT_UNDER_TEST" --service invalido --tag stg-test 2>&1)"
  local status=$?
  set -e
  assert_equals "2" "$status"
  assert_contains "$output" "service invalido: invalido"
}

test_dry_run_auth_includes_prebuild_and_push() {
  local output
  output="$("$SCRIPT_UNDER_TEST" \
    --service auth \
    --tag stg-20260429-001 \
    --profile Codex-cli_aws \
    --dry-run)"

  assert_contains "$output" "SERVICE_KEY=auth"
  assert_contains "$output" "REPOSITORY_NAME=eickrono-autenticacao-servidor"
  assert_contains "$output" "PREBUILD_CMD=mvn -q -DskipTests package"
  assert_contains "$output" "[dry-run] (cd "
  assert_contains "$output" "aws --profile Codex-cli_aws ecr get-login-password --region sa-east-1"
  assert_contains "$output" "docker buildx build"
  assert_contains "$output" "531708494702.dkr.ecr.sa-east-1.amazonaws.com/eickrono-autenticacao-servidor:stg-20260429-001"
}

test_dry_run_thimisu_backend_skips_prebuild_command() {
  local output
  output="$("$SCRIPT_UNDER_TEST" \
    --service thimisu-backend \
    --tag stg-20260429-002 \
    --dry-run)"

  assert_contains "$output" "SERVICE_KEY=thimisu-backend"
  assert_contains "$output" "REPOSITORY_NAME=eickrono-thimisu-backend"
  assert_contains "$output" "PREBUILD_CMD=<none>"
  if [[ "$output" == *"mvn -q -DskipTests package"* ]]; then
    fail "nao deveria executar prebuild explicito para thimisu-backend"
  fi
}

main() {
  test_missing_required_args
  test_invalid_service
  test_dry_run_auth_includes_prebuild_and_push
  test_dry_run_thimisu_backend_skips_prebuild_command
  echo "ok"
}

main "$@"
