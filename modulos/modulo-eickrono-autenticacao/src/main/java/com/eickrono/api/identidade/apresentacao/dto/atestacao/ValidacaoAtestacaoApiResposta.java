package com.eickrono.api.identidade.apresentacao.dto.atestacao;

import com.eickrono.api.identidade.aplicacao.modelo.StatusValidacaoAtestacaoApp;
import com.eickrono.api.identidade.aplicacao.modelo.ValidacaoAtestacaoAppConcluida;
import com.eickrono.api.identidade.dominio.modelo.OperacaoAtestacaoApp;
import com.eickrono.api.identidade.dominio.modelo.PlataformaAtestacaoApp;
import com.eickrono.api.identidade.dominio.modelo.ProvedorAtestacaoApp;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ValidacaoAtestacaoApiResposta(
        String identificadorDesafio,
        StatusValidacaoAtestacaoApp statusValidacao,
        OperacaoAtestacaoApp operacao,
        PlataformaAtestacaoApp plataforma,
        ProvedorAtestacaoApp provedorEsperado,
        OffsetDateTime criadoEm,
        OffsetDateTime expiraEm,
        OffsetDateTime geradoEm,
        boolean validacaoOficialExecutada,
        String resumoValidacaoOficial,
        String appRecognitionVerdict,
        String appLicensingVerdict,
        List<String> deviceRecognitionVerdict,
        String chaveId
) {

    public ValidacaoAtestacaoApiResposta {
        deviceRecognitionVerdict = deviceRecognitionVerdict == null
                ? null
                : Collections.unmodifiableList(new ArrayList<>(deviceRecognitionVerdict));
    }

    public static ValidacaoAtestacaoApiResposta de(final ValidacaoAtestacaoAppConcluida validacao) {
        return new ValidacaoAtestacaoApiResposta(
                validacao.validacaoLocal().identificadorDesafio(),
                validacao.statusValidacao(),
                validacao.validacaoLocal().operacao(),
                validacao.validacaoLocal().plataforma(),
                validacao.validacaoLocal().provedorEsperado(),
                validacao.validacaoLocal().criadoEm(),
                validacao.validacaoLocal().expiraEm(),
                validacao.validacaoLocal().geradoEm(),
                validacao.validacaoOficial().executada(),
                validacao.validacaoOficial().resumo(),
                validacao.validacaoOficial().appRecognitionVerdict(),
                validacao.validacaoOficial().appLicensingVerdict(),
                validacao.validacaoOficial().deviceRecognitionVerdict(),
                validacao.validacaoLocal().chaveId()
        );
    }
}
