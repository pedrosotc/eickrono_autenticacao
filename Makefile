ROOT_DIR := $(dir $(abspath $(lastword $(MAKEFILE_LIST))))

export EICKRONO_IDENTIDADE_DIR ?= $(abspath $(ROOT_DIR)/../eickrono-identidade-servidor)
export EICKRONO_CONTAS_DIR ?= $(abspath $(ROOT_DIR)/../eickrono-contas-servidor)
INCLUIR_API_CONTAS ?= false

PIPELINE_DIR := $(ROOT_DIR)scripts/pipeline

.PHONY: ajuda package test test-rapido verificar-servicos package-servicos test-servicos test-servicos-completo compose-config-dev compose-config-hml compose-config up-dev up-hml

ajuda:
	@printf '%s\n' \
		'Alvos disponíveis:' \
		'  make package             # empacota o proprio eickrono-autenticacao-servidor' \
		'  make test                # roda a suite do proprio eickrono-autenticacao-servidor' \
		'  make test-rapido         # alias local para a suite do proprio eickrono-autenticacao-servidor' \
		'  make package-servicos    # empacota identidade + autenticacao; use INCLUIR_API_CONTAS=true para incluir contas' \
		'  make test-servicos       # roda a bateria representativa estavel de autenticacao + identidade; use INCLUIR_API_CONTAS=true para incluir contas' \
		'  make test-servicos-completo # roda a suite completa; exige Docker acessivel; use INCLUIR_API_CONTAS=true para incluir contas' \
		'  make compose-config-dev  # valida docker compose de dev' \
		'  make compose-config-hml  # valida docker compose de hml' \
		'  make compose-config      # valida dev e hml' \
		'  make up-dev              # sobe dev auth-only por padrão; use INCLUIR_API_CONTAS=true para incluir contas' \
		'  make up-hml              # sobe hml auth-only por padrão; use INCLUIR_API_CONTAS=true para incluir contas'

package:
	mvn -q -DskipTests package

test:
	mvn -q test

test-rapido: test

verificar-servicos:
	@test -d "$(EICKRONO_IDENTIDADE_DIR)" || (echo "Repositorio ausente: $(EICKRONO_IDENTIDADE_DIR)" && exit 1)
	@if [ "$(INCLUIR_API_CONTAS)" = "true" ]; then \
		test -d "$(EICKRONO_CONTAS_DIR)" || (echo "Repositorio ausente: $(EICKRONO_CONTAS_DIR)" && exit 1); \
	fi

package-servicos: verificar-servicos
	@"$(PIPELINE_DIR)/package_servicos.sh"

test-servicos: verificar-servicos
	@"$(PIPELINE_DIR)/test_servicos.sh"

test-servicos-completo: verificar-servicos
	@"$(PIPELINE_DIR)/test_servicos_completo.sh"

compose-config-dev: verificar-servicos
	@"$(PIPELINE_DIR)/compose_config.sh" dev

compose-config-hml: verificar-servicos
	@"$(PIPELINE_DIR)/compose_config.sh" hml

compose-config: compose-config-dev compose-config-hml

up-dev: verificar-servicos
	@"$(PIPELINE_DIR)/up_stack.sh" dev

up-hml: verificar-servicos
	@"$(PIPELINE_DIR)/up_stack.sh" hml
