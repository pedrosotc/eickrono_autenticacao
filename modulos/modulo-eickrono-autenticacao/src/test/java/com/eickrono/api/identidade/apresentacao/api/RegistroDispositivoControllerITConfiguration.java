package com.eickrono.api.identidade.apresentacao.api;

import com.eickrono.api.identidade.dominio.modelo.CanalVerificacao;
import com.eickrono.api.identidade.dominio.modelo.RegistroDispositivo;
import com.eickrono.api.identidade.aplicacao.servico.CanalEnvioCodigo;
import com.eickrono.api.identidade.aplicacao.servico.FornecedorEnvioSms;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

@TestConfiguration(proxyBeanMethods = false)
class RegistroDispositivoControllerITConfiguration {

    @Bean
    RegistroDispositivoControllerIT.CodigoCapturador codigoCapturador() {
        return new RegistroDispositivoControllerIT.CodigoCapturador();
    }

    @Bean(name = "fornecedorEnvioSmsTeste")
    FornecedorEnvioSms fornecedorEnvioSms(RegistroDispositivoControllerIT.CodigoCapturador capturador) {
        return new FornecedorEnvioSms() {
            @Override
            public String identificador() {
                return "teste";
            }

            @Override
            public void enviarSms(RegistroDispositivo registro, String destino, String codigo) {
                capturador.registrar(registro.getId(), CanalVerificacao.SMS, codigo);
            }
        };
    }

    @Bean(name = "canalEnvioCodigoEmailLog")
    CanalEnvioCodigo canalEnvioEmail(RegistroDispositivoControllerIT.CodigoCapturador capturador) {
        return new CanalEnvioCodigo() {
            @Override
            public CanalVerificacao canal() {
                return CanalVerificacao.EMAIL;
            }

            @Override
            public void enviar(RegistroDispositivo registro, String destino, String codigo) {
                capturador.registrar(registro.getId(), CanalVerificacao.EMAIL, codigo);
            }
        };
    }
}
