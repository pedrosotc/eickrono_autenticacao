package com.eickrono.api.identidade;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Aplicação principal da API de Identidade da Eickrono.
 */
@SpringBootApplication
public class AplicacaoApiIdentidade {

    private AplicacaoApiIdentidade() {
        // Classe utilitária: construtor privado para evitar instâncias.
    }

    public static void main(String[] args) {
        SpringApplication.run(AplicacaoApiIdentidade.class, args);
    }
}
