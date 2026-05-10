package com.eickrono.api.identidade.aplicacao.servico;

import com.eickrono.api.identidade.aplicacao.modelo.PessoaCanonicaConfirmada;
import java.time.OffsetDateTime;

public interface ConfirmadorPessoaCadastroServico {

    PessoaCanonicaConfirmada confirmarEmailCadastro(String sub,
                                                   String email,
                                                   String nomeCompleto,
                                                   OffsetDateTime confirmadoEm);
}
