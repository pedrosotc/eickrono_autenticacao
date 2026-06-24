#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SCRIPT_UNDER_TEST="${ROOT_DIR}/secrets/upsert_stg_secret.sh"

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
  assert_contains "$output" "uso: upsert_stg_secret.sh"
}

test_missing_secret_file() {
  local output
  set +e
  output="$("$SCRIPT_UNDER_TEST" --secret-id /eickrono/stg/test/nao-existe --value-file /tmp/inexistente 2>&1)"
  local status=$?
  set -e
  assert_equals "2" "$status"
  assert_contains "$output" "arquivo de segredo nao encontrado"
}

test_dry_run_with_literal_reports_metadata_without_exposing_value() {
  local output
  output="$("$SCRIPT_UNDER_TEST" \
    --secret-id /eickrono/stg/test/secret-nao-existente-username \
    --value-literal usuario@example.com \
    --profile Codex-cli_aws \
    --dry-run)"

  assert_contains "$output" "SECRET_ID=/eickrono/stg/test/secret-nao-existente-username"
  assert_contains "$output" "VALUE_MODE=literal"
  assert_contains "$output" "VALUE_LENGTH=19"
  assert_contains "$output" "ACTION=create-secret"
  assert_contains "$output" "aws --profile Codex-cli_aws secretsmanager create-secret"
  if [[ "$output" == *"usuario@example.com"* ]]; then
    fail "o valor secreto nao deveria aparecer na saida do dry-run"
  fi
}

test_dry_run_with_file_reports_length_and_does_not_expose_secret() {
  local tmpfile output
  tmpfile="$(mktemp)"
  printf 'senha-app-exemplo-01' >"$tmpfile"
  output="$("$SCRIPT_UNDER_TEST" \
    --secret-id /eickrono/stg/test/secret-nao-existente-password \
    --value-file "$tmpfile" \
    --dry-run)"

  assert_contains "$output" "SECRET_ID=/eickrono/stg/test/secret-nao-existente-password"
  assert_contains "$output" "VALUE_MODE=file"
  assert_contains "$output" "VALUE_LENGTH=20"
  assert_contains "$output" "ACTION=create-secret"
  if [[ "$output" == *"senha-app-exemplo-01"* ]]; then
    fail "o valor secreto nao deveria aparecer na saida do dry-run"
  fi
  rm -f "$tmpfile"
}

main() {
  test_missing_required_args
  test_missing_secret_file
  test_dry_run_with_literal_reports_metadata_without_exposing_value
  test_dry_run_with_file_reports_length_and_does_not_expose_secret
  echo "ok"
}

main "$@"
