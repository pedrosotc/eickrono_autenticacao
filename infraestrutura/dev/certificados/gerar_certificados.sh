#!/usr/bin/env bash
set -euo pipefail

# Script para gerar certificados autoassinados de desenvolvimento.
NOME_BASE=${1:-"dev-eickrono"}
DIAS_VALIDOS=${2:-365}

openssl req -x509 -newkey rsa:4096 \
  -keyout "${NOME_BASE}.key" \
  -out "${NOME_BASE}.crt" \
  -days "${DIAS_VALIDOS}" \
  -nodes \
  -subj "/CN=${NOME_BASE}.local/O=Eickrono/OU=Desenvolvimento"

openssl pkcs12 -export \
  -inkey "${NOME_BASE}.key" \
  -in "${NOME_BASE}.crt" \
  -out "${NOME_BASE}.p12" \
  -password pass:senhaCertificadoDev

echo "Certificados gerados em $(pwd)"
