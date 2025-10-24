package com.eickrono.api.identidade.servico;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.eickrono.api.identidade.dominio.modelo.AuditoriaEventoIdentidade;
import com.eickrono.api.identidade.dominio.repositorio.AuditoriaEventoIdentidadeRepositorio;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AuditoriaServiceTest {

    @Mock
    private AuditoriaEventoIdentidadeRepositorio auditoriaRepositorio;

    private AuditoriaService auditoriaService;

    /**
     * Instancia o serviço real com o repositório mockado para capturar exatamente o que é persistido.
     */
    @BeforeEach
    void setUp() {
        auditoriaService = new AuditoriaService(auditoriaRepositorio);
    }

    /**
     * Valida que registrarEvento cria a entidade com tipo, sujeito, detalhes e timestamp preenchidos.
     * Utilizamos ArgumentCaptor para inspecionar o objeto entregue ao repositório.
     */
    @Test
    @DisplayName("deve persistir evento de auditoria com dados completos")
    void devePersistirEvento() {
        when(auditoriaRepositorio.save(any())).thenAnswer(invoc -> invoc.getArgument(0));

        auditoriaService.registrarEvento("PERFIL_CONSULTADO", "sub-123", "Consulta de perfil");

        ArgumentCaptor<AuditoriaEventoIdentidade> captor = ArgumentCaptor.forClass(AuditoriaEventoIdentidade.class);
        verify(auditoriaRepositorio).save(captor.capture());
        AuditoriaEventoIdentidade salvo = captor.getValue();
        assertThat(salvo.getTipoEvento()).isEqualTo("PERFIL_CONSULTADO");
        assertThat(salvo.getSujeito()).isEqualTo("sub-123");
        assertThat(salvo.getDetalhes()).isEqualTo("Consulta de perfil");
        assertThat(salvo.getRegistradoEm()).isNotNull();
    }
}
