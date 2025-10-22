package com.eickrono.api.contas;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Aplicação principal da API de Contas da Eickrono.
 */
@SpringBootApplication
public class AplicacaoApiContas {

    private AplicacaoApiContas() {
        // Classe utilitária: construtor privado para evitar instâncias.
    }

    public static void main(String[] args) {
        SpringApplication.run(AplicacaoApiContas.class, args);
    }
}
