package com.eickrono.api.contas.servico;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.eickrono.api.contas.dominio.modelo.AuditoriaAcessoContas;
import com.eickrono.api.contas.dominio.modelo.AuditoriaEventoContas;
import com.eickrono.api.contas.dominio.repositorio.AuditoriaAcessoContasRepositorio;
import com.eickrono.api.contas.dominio.repositorio.AuditoriaEventoContasRepositorio;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AuditoriaContasServiceTest {

    @Mock
    private AuditoriaEventoContasRepositorio eventoRepositorio;
    @Mock
    private AuditoriaAcessoContasRepositorio acessoRepositorio;

    private AuditoriaContasService auditoriaContasService;

    /** Instancia o serviço com repositórios mockados para inspecionar dados persistidos. */
    @BeforeEach
    void setUp() {
        auditoriaContasService = new AuditoriaContasService(eventoRepositorio, acessoRepositorio);
    }

    @Nested
    @DisplayName("Caso de uso: registrar eventos")
    class RegistrarEventos {

        /** Capturamos o objeto persistido garantindo tipo, sujeito e detalhes. */
        @Test
        @DisplayName("deve criar auditoria de evento com todos os campos")
        void deveSalvarEvento() {
            when(eventoRepositorio.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

            auditoriaContasService.registrarEvento("PIX_ENVIADO", "sub-123", "Transferência PIX");

            ArgumentCaptor<AuditoriaEventoContas> captor = ArgumentCaptor.forClass(AuditoriaEventoContas.class);
            verify(eventoRepositorio).save(captor.capture());
            AuditoriaEventoContas evento = captor.getValue();
            assertThat(evento.getTipoEvento()).isEqualTo("PIX_ENVIADO");
            assertThat(evento.getSujeito()).isEqualTo("sub-123");
            assertThat(evento.getDetalhes()).isEqualTo("Transferência PIX");
            assertThat(evento.getRegistradoEm()).isNotNull();
        }
    }

    @Nested
    @DisplayName("Caso de uso: registrar acessos")
    class RegistrarAcessos {

        /** Garante que o endpoint e o sujeito sejam registrados com timestamp atual. */
        @Test
        @DisplayName("deve criar auditoria de acesso")
        void deveSalvarAcesso() {
            when(acessoRepositorio.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

            auditoriaContasService.registrarAcesso("sub-123", "/contas", "Listagem");

            ArgumentCaptor<AuditoriaAcessoContas> captor = ArgumentCaptor.forClass(AuditoriaAcessoContas.class);
            verify(acessoRepositorio).save(captor.capture());
            AuditoriaAcessoContas acesso = captor.getValue();
            assertThat(acesso.getSujeito()).isEqualTo("sub-123");
            assertThat(acesso.getEndpoint()).isEqualTo("/contas");
            assertThat(acesso.getDetalhes()).isEqualTo("Listagem");
            assertThat(acesso.getRegistradoEm()).isNotNull();
        }
    }
}
