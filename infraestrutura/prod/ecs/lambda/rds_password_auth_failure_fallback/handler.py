import base64
import gzip
import json
import logging
import os
import time
from typing import Any, Optional


LOGGER = logging.getLogger(__name__)
LOGGER.setLevel(logging.INFO)

ERRO_PADRAO = 'password authentication failed for user "eickrono_admin"'


def _csv(nome_variavel: str) -> list[str]:
    return [
        item.strip()
        for item in os.environ.get(nome_variavel, "").split(",")
        if item.strip()
    ]


def _servicos_configurados() -> list[str]:
    servicos = _csv("ECS_SERVICES")
    if not servicos:
        raise RuntimeError("ECS_SERVICES nao configurado.")
    return servicos


def _decodificar_evento_cloudwatch_logs(
    evento: dict[str, Any],
) -> Optional[dict[str, Any]]:
    awslogs = evento.get("awslogs")
    if not isinstance(awslogs, dict):
        return None

    data = awslogs.get("data")
    if not isinstance(data, str) or not data:
        return None

    payload = gzip.decompress(base64.b64decode(data))
    decodificado = json.loads(payload.decode("utf-8"))
    if not isinstance(decodificado, dict):
        return None
    return decodificado


def _extrair_registros_log(evento: dict[str, Any]) -> list[dict[str, Any]]:
    payload_logs = _decodificar_evento_cloudwatch_logs(evento)
    if payload_logs is not None:
        log_group = payload_logs.get("logGroup")
        log_stream = payload_logs.get("logStream")
        registros = []
        for log_event in payload_logs.get("logEvents", []):
            if not isinstance(log_event, dict):
                continue
            mensagem = log_event.get("message")
            if not isinstance(mensagem, str):
                continue
            registros.append(
                {
                    "message": mensagem,
                    "logGroup": log_group,
                    "logStream": log_stream,
                    "timestamp": log_event.get("timestamp"),
                }
            )
        return registros

    # Permite teste manual/sintetico pelo console da Lambda.
    mensagem = evento.get("message")
    if isinstance(mensagem, str):
        return [
            {
                "message": mensagem,
                "logGroup": evento.get("logGroup"),
                "logStream": evento.get("logStream"),
                "timestamp": evento.get("timestamp"),
            }
        ]

    detalhe = evento.get("detail")
    if isinstance(detalhe, dict):
        mensagem_detalhe = detalhe.get("message")
        if isinstance(mensagem_detalhe, str):
            return [
                {
                    "message": mensagem_detalhe,
                    "logGroup": detalhe.get("logGroup"),
                    "logStream": detalhe.get("logStream"),
                    "timestamp": detalhe.get("timestamp"),
                }
            ]

    return []


def _registros_com_erro_senha(evento: dict[str, Any], erro_padrao: str) -> list[dict[str, Any]]:
    return [
        registro
        for registro in _extrair_registros_log(evento)
        if erro_padrao in registro["message"]
    ]


def _ler_ultimo_redeploy(ssm_client: Any, parametro: str) -> Optional[float]:
    try:
        resposta = ssm_client.get_parameter(Name=parametro)
    except Exception as exc:
        codigo = getattr(getattr(exc, "response", None), "get", lambda *_: None)("Error", {})
        if isinstance(codigo, dict) and codigo.get("Code") == "ParameterNotFound":
            return None
        nome = exc.__class__.__name__
        if nome == "ParameterNotFound":
            return None
        raise

    valor = resposta.get("Parameter", {}).get("Value")
    if not isinstance(valor, str) or not valor.strip():
        return None
    return float(valor)


def _salvar_ultimo_redeploy(ssm_client: Any, parametro: str, timestamp: float) -> None:
    ssm_client.put_parameter(
        Name=parametro,
        Value=str(timestamp),
        Type="String",
        Overwrite=True,
    )


def _em_cooldown(
    *,
    ssm_client: Any,
    parametro: str,
    cooldown_segundos: int,
    agora: float,
) -> bool:
    ultimo_redeploy = _ler_ultimo_redeploy(ssm_client, parametro)
    if ultimo_redeploy is None:
        return False
    return agora - ultimo_redeploy < cooldown_segundos


def _validar_secret_com_task_ecs(
    *,
    ecs_client: Any,
    cluster: str,
    task_definition: str,
    container_name: str,
    subnets: list[str],
    security_groups: list[str],
    database_name: str,
) -> bool:
    if not task_definition:
        raise RuntimeError("VALIDATION_TASK_DEFINITION nao configurado.")
    if not container_name:
        raise RuntimeError("VALIDATION_CONTAINER_NAME nao configurado.")
    if not subnets:
        raise RuntimeError("VALIDATION_SUBNETS nao configurado.")
    if not security_groups:
        raise RuntimeError("VALIDATION_SECURITY_GROUPS nao configurado.")
    if not database_name:
        raise RuntimeError("VALIDATION_DATABASE nao configurado.")

    comando = (
        'PGPASSWORD="$PGPASSWORD" psql '
        '-h "$PGHOST" -p "$PGPORT" -U "$PGUSER" '
        '-d "$VALIDATION_DATABASE" '
        '-v ON_ERROR_STOP=1 -c "SELECT 1;"'
    )
    resposta = ecs_client.run_task(
        cluster=cluster,
        launchType="FARGATE",
        taskDefinition=task_definition,
        count=1,
        networkConfiguration={
            "awsvpcConfiguration": {
                "subnets": subnets,
                "securityGroups": security_groups,
                "assignPublicIp": "DISABLED",
            }
        },
        overrides={
            "containerOverrides": [
                {
                    "name": container_name,
                    "command": ["sh", "-lc", comando],
                    "environment": [
                        {
                            "name": "VALIDATION_DATABASE",
                            "value": database_name,
                        }
                    ],
                }
            ]
        },
        startedBy="rds-password-auth-failure-fallback",
    )

    falhas = resposta.get("failures", [])
    if falhas:
        LOGGER.error("fallback_validacao_task_falhou_ao_iniciar failures=%s", falhas)
        return False

    tasks = resposta.get("tasks", [])
    if not tasks:
        LOGGER.error("fallback_validacao_task_sem_task")
        return False

    task_arn = tasks[0]["taskArn"]
    waiter = ecs_client.get_waiter("tasks_stopped")
    waiter.wait(cluster=cluster, tasks=[task_arn])

    descricao = ecs_client.describe_tasks(cluster=cluster, tasks=[task_arn])
    task = descricao.get("tasks", [{}])[0]
    for container in task.get("containers", []):
        if container.get("name") == container_name:
            return container.get("exitCode") == 0

    return False


def _redeploy_services(
    *,
    ecs_client: Any,
    cluster: str,
    services: list[str],
    waiter_delay_seconds: int,
    waiter_max_attempts: int,
) -> list[str]:
    redeployados: list[str] = []
    waiter = ecs_client.get_waiter("services_stable")
    for service in services:
        LOGGER.info(
            "fallback_redeploy_servico_iniciado cluster=%s service=%s",
            cluster,
            service,
        )
        ecs_client.update_service(
            cluster=cluster,
            service=service,
            forceNewDeployment=True,
        )
        waiter.wait(
            cluster=cluster,
            services=[service],
            WaiterConfig={
                "Delay": waiter_delay_seconds,
                "MaxAttempts": waiter_max_attempts,
            },
        )
        LOGGER.info(
            "fallback_redeploy_servico_estavel cluster=%s service=%s",
            cluster,
            service,
        )
        redeployados.append(service)
    return redeployados


def processar_evento(
    evento: dict[str, Any],
    *,
    ecs_client: Any,
    ssm_client: Any,
    cluster: str,
    services: list[str],
    cooldown_parameter_name: str,
    cooldown_segundos: int,
    validation_task_definition: str,
    validation_container_name: str,
    validation_subnets: list[str],
    validation_security_groups: list[str],
    validation_database: str,
    waiter_delay_seconds: int = 15,
    waiter_max_attempts: int = 40,
    agora: Optional[float] = None,
    erro_padrao: str = ERRO_PADRAO,
) -> dict[str, Any]:
    registros = _registros_com_erro_senha(evento, erro_padrao)
    if not registros:
        return {
            "matched": False,
            "reason": "evento_sem_erro_monitorado",
        }

    instante = time.time() if agora is None else agora
    if _em_cooldown(
        ssm_client=ssm_client,
        parametro=cooldown_parameter_name,
        cooldown_segundos=cooldown_segundos,
        agora=instante,
    ):
        LOGGER.info(
            "fallback_password_auth_failure_ignorado_cooldown parametro=%s",
            cooldown_parameter_name,
        )
        return {
            "matched": True,
            "reason": "cooldown_ativo",
            "services": services,
            "logEvents": len(registros),
        }

    LOGGER.info(
        "fallback_password_auth_failure_recebido logGroup=%s logStream=%s eventos=%s",
        registros[0].get("logGroup"),
        registros[0].get("logStream"),
        len(registros),
    )

    secret_valido = _validar_secret_com_task_ecs(
        ecs_client=ecs_client,
        cluster=cluster,
        task_definition=validation_task_definition,
        container_name=validation_container_name,
        subnets=validation_subnets,
        security_groups=validation_security_groups,
        database_name=validation_database,
    )
    if not secret_valido:
        LOGGER.error("fallback_secret_atual_invalido_sem_redeploy cluster=%s", cluster)
        return {
            "matched": True,
            "reason": "secret_atual_nao_validado",
            "redeployed": False,
            "services": services,
            "logEvents": len(registros),
        }

    redeployados = _redeploy_services(
        ecs_client=ecs_client,
        cluster=cluster,
        services=services,
        waiter_delay_seconds=waiter_delay_seconds,
        waiter_max_attempts=waiter_max_attempts,
    )
    _salvar_ultimo_redeploy(ssm_client, cooldown_parameter_name, instante)

    LOGGER.info(
        "fallback_redeploy_concluido cluster=%s services=%s logEvents=%s",
        cluster,
        ",".join(redeployados),
        len(registros),
    )
    return {
        "matched": True,
        "reason": "password_auth_failure",
        "redeployed": True,
        "servicesRedeployed": redeployados,
        "logEvents": len(registros),
    }


def lambda_handler(event: dict[str, Any], context: Any) -> dict[str, Any]:
    del context
    import boto3

    ecs_client = boto3.client("ecs")
    ssm_client = boto3.client("ssm")
    return processar_evento(
        event,
        ecs_client=ecs_client,
        ssm_client=ssm_client,
        cluster=os.environ["ECS_CLUSTER"],
        services=_servicos_configurados(),
        cooldown_parameter_name=os.environ["COOLDOWN_PARAMETER_NAME"],
        cooldown_segundos=int(os.environ.get("COOLDOWN_SECONDS", "900")),
        validation_task_definition=os.environ["VALIDATION_TASK_DEFINITION"],
        validation_container_name=os.environ["VALIDATION_CONTAINER_NAME"],
        validation_subnets=_csv("VALIDATION_SUBNETS"),
        validation_security_groups=_csv("VALIDATION_SECURITY_GROUPS"),
        validation_database=os.environ["VALIDATION_DATABASE"],
        waiter_delay_seconds=int(
            os.environ.get("SERVICE_STABLE_WAITER_DELAY_SECONDS", "15")
        ),
        waiter_max_attempts=int(
            os.environ.get("SERVICE_STABLE_WAITER_MAX_ATTEMPTS", "40")
        ),
    )
