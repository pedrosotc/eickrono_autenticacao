#!/usr/bin/env bash
set -euo pipefail

# Gera certificados autoassinados para o ambiente de homologação.
NOME_BASE=${1:-"hml-eickrono"}
DIAS_VALIDOS=${2:-365}

openssl req -x509 -newkey rsa:4096   -keyout "${NOME_BASE}.key"   -out "${NOME_BASE}.crt"   -days "${DIAS_VALIDOS}"   -nodes   -subj "/CN=${NOME_BASE}.homologacao/O=Eickrono/OU=Homologacao"

openssl pkcs12 -export   -inkey "${NOME_BASE}.key"   -in "${NOME_BASE}.crt"   -out "${NOME_BASE}.p12"   -password pass:senhaCertificadoHml

echo "Certificados gerados em $(pwd)"
