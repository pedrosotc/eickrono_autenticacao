package com.eickrono.api.identidade.aplicacao.servico;

import static org.assertj.core.api.Assertions.assertThat;

import com.eickrono.api.identidade.dominio.modelo.AuditoriaEventoIdentidade;
import com.eickrono.api.identidade.dominio.repositorio.AuditoriaEventoIdentidadeRepositorio;
import java.lang.reflect.Proxy;
import java.util.Objects;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AuditoriaServiceTest {

    private AuditoriaService auditoriaService;
    private AuditoriaEventoIdentidade ultimoEvento;

    private AuditoriaEventoIdentidadeRepositorio auditoriaRepositorio() {
        return (AuditoriaEventoIdentidadeRepositorio) Proxy.newProxyInstance(
                AuditoriaEventoIdentidadeRepositorio.class.getClassLoader(),
                new Class<?>[] {AuditoriaEventoIdentidadeRepositorio.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "save" -> {
                        ultimoEvento = (AuditoriaEventoIdentidade) Objects.requireNonNull(args)[0];
                        yield ultimoEvento;
                    }
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    case "toString" -> "AuditoriaEventoIdentidadeRepositorioFake";
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }

    /**
     * Instancia o serviço real com o repositório mockado para capturar exatamente o que é persistido.
     */
    private void inicializarServico() {
        auditoriaService = new AuditoriaService(auditoriaRepositorio());
    }

    /**
     * Valida que registrarEvento cria a entidade com tipo, sujeito, detalhes e timestamp preenchidos.
     * Utilizamos ArgumentCaptor para inspecionar o objeto entregue ao repositório.
     */
    @Test
    @DisplayName("deve persistir evento de auditoria com dados completos")
    void devePersistirEvento() {
        inicializarServico();

        auditoriaService.registrarEvento("PERFIL_CONSULTADO", "sub-123", "Consulta de perfil");

        AuditoriaEventoIdentidade salvo = Objects.requireNonNull(ultimoEvento);
        assertThat(salvo.getTipoEvento()).isEqualTo("PERFIL_CONSULTADO");
        assertThat(salvo.getSujeito()).isEqualTo("sub-123");
        assertThat(salvo.getDetalhes()).isEqualTo("Consulta de perfil");
        assertThat(salvo.getRegistradoEm()).isNotNull();
    }
}
