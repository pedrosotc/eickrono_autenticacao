package com.eickrono.api.identidade.configuracao;

import java.time.Clock;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Configura beans fundamentais compartilhados pelo domínio.
 */
@Configuration
@EnableConfigurationProperties(DispositivoProperties.class)
@EnableScheduling
public class InfraestruturaBasicaConfiguracao {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
