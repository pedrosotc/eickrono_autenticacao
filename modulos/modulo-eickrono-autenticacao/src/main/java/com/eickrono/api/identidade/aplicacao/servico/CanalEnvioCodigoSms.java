package com.eickrono.api.identidade.aplicacao.servico;

import com.eickrono.api.identidade.infraestrutura.configuracao.DispositivoProperties;
import com.eickrono.api.identidade.dominio.modelo.CanalVerificacao;
import com.eickrono.api.identidade.dominio.modelo.RegistroDispositivo;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/**
 * Canal de envio SMS que delega para o fornecedor configurado por política.
 */
@Component
public class CanalEnvioCodigoSms implements CanalEnvioCodigo {

    private final DispositivoProperties properties;
    private final Map<String, FornecedorEnvioSms> fornecedores;

    public CanalEnvioCodigoSms(DispositivoProperties properties, List<FornecedorEnvioSms> fornecedores) {
        this.properties = properties;
        this.fornecedores = construirMapaFornecedores(fornecedores);
    }

    @Override
    public CanalVerificacao canal() {
        return CanalVerificacao.SMS;
    }

    @Override
    public void enviar(RegistroDispositivo registro, String destino, String codigo) {
        String identificador = properties.getOnboarding().getSmsFornecedor();
        FornecedorEnvioSms fornecedor = fornecedores.get(identificador);
        if (fornecedor == null) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Fornecedor de SMS não configurado: " + identificador);
        }
        fornecedor.enviarSms(registro, destino, codigo);
    }

    private Map<String, FornecedorEnvioSms> construirMapaFornecedores(List<FornecedorEnvioSms> fornecedores) {
        Map<String, FornecedorEnvioSms> mapa = new HashMap<>();
        for (FornecedorEnvioSms fornecedor : fornecedores) {
            mapa.put(fornecedor.identificador(), fornecedor);
        }
        return Map.copyOf(mapa);
    }
}
