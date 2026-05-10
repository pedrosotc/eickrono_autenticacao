package com.eickrono.api.identidade.apresentacao.dto.atestacao;

import com.eickrono.api.identidade.aplicacao.modelo.ComprovanteAtestacaoAppEntrada;
import com.eickrono.api.identidade.dominio.modelo.PlataformaAtestacaoApp;
import com.eickrono.api.identidade.dominio.modelo.ProvedorAtestacaoApp;
import com.eickrono.api.identidade.dominio.modelo.TipoComprovanteAtestacaoApp;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.OffsetDateTime;

public record ValidarAtestacaoApiRequest(
        @NotNull PlataformaAtestacaoApp plataforma,
        @NotNull ProvedorAtestacaoApp provedor,
        @NotNull TipoComprovanteAtestacaoApp tipoComprovante,
        @NotBlank String identificadorDesafio,
        @NotBlank String desafioBase64,
        @NotBlank String conteudoComprovante,
        @NotNull OffsetDateTime geradoEm,
        String chaveId
) {

    public ComprovanteAtestacaoAppEntrada paraEntradaAplicacao() {
        return new ComprovanteAtestacaoAppEntrada(
                plataforma,
                provedor,
                tipoComprovante,
                identificadorDesafio,
                desafioBase64,
                conteudoComprovante,
                geradoEm,
                chaveId
        );
    }
}
