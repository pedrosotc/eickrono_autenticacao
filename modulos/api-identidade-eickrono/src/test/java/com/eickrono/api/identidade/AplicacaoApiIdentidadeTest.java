package com.eickrono.api.identidade;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

@SpringBootTest
@ActiveProfiles("test")
class AplicacaoApiIdentidadeTest {

    private static PostgreSQLContainer<?> postgres;

    @BeforeAll
    static void iniciarContainer() {
        try {
            postgres = new PostgreSQLContainer<>("postgres:15.5")
                    .withDatabaseName("eickrono_identidade_test")
                    .withUsername("test")
                    .withPassword("test");
            postgres.start();
        } catch (Throwable ex) {
            postgres = null;
            Assumptions.assumeTrue(false, "Docker não está disponível para executar Testcontainers");
        }
    }

    @AfterAll
    static void pararContainer() {
        if (postgres != null && postgres.isRunning()) {
            postgres.stop();
        }
    }

    @DynamicPropertySource
    static void registrarPropriedadesDinamicas(DynamicPropertyRegistry registry) {
        if (postgres != null && postgres.isRunning()) {
            registry.add("spring.datasource.url", postgres::getJdbcUrl);
            registry.add("spring.datasource.username", postgres::getUsername);
            registry.add("spring.datasource.password", postgres::getPassword);
            registry.add("spring.datasource.driver-class-name", postgres::getDriverClassName);
            registry.add("spring.flyway.locations", () -> "classpath:db/migration");
            registry.add("spring.flyway.enabled", () -> true);
        } else {
            registry.add("spring.datasource.url", () -> "jdbc:h2:mem:eickrono_identidade_test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1");
            registry.add("spring.datasource.username", () -> "sa");
            registry.add("spring.datasource.password", () -> "");
            registry.add("spring.datasource.driver-class-name", () -> "org.h2.Driver");
            registry.add("spring.flyway.enabled", () -> false);
        }
    }

    @Test
    void deveCarregarContexto() {
        // Teste simples para validar a subida do contexto Spring.
    }
}
