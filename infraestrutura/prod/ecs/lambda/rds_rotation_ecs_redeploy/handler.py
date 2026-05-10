import logging
import os
from typing import Any


LOGGER = logging.getLogger(__name__)
LOGGER.setLevel(logging.INFO)


def _servicos_configurados() -> list[str]:
    servicos = [
        item.strip()
        for item in os.environ.get("ECS_SERVICES", "").split(",")
        if item.strip()
    ]
    if not servicos:
        raise RuntimeError("ECS_SERVICES nao configurado.")
    return servicos


def _extrair_arns_segredo(evento: dict[str, Any]) -> set[str]:
    arns: set[str] = set()

    for recurso in evento.get("resources", []):
        if isinstance(recurso, str) and recurso:
            arns.add(recurso)

    detalhe = evento.get("detail")
    if isinstance(detalhe, dict):
        response_elements = detalhe.get("responseElements")
        if isinstance(response_elements, dict):
            for chave in ("arn", "aRN"):
                valor = response_elements.get(chave)
                if isinstance(valor, str) and valor:
                    arns.add(valor)
        for chave in ("arn", "aRN", "secretId"):
            valor = detalhe.get(chave)
            if isinstance(valor, str) and valor:
                arns.add(valor)

    return arns


def _evento_de_rotacao_bem_sucedida(evento: dict[str, Any], secret_arn: str) -> bool:
    detalhe = evento.get("detail")
    if not isinstance(detalhe, dict):
        return False
    if detalhe.get("eventSource") != "secretsmanager.amazonaws.com":
        return False
    if detalhe.get("eventName") != "RotationSucceeded":
        return False
    return secret_arn in _extrair_arns_segredo(evento)


def processar_evento(
    evento: dict[str, Any],
    *,
    ecs_client: Any,
    secret_arn: str,
    cluster: str,
    services: list[str],
) -> dict[str, Any]:
    if not _evento_de_rotacao_bem_sucedida(evento, secret_arn):
        return {
            "matched": False,
            "reason": "evento_ignorado",
            "services": services,
            "secretArn": secret_arn,
        }

    redeployados: list[str] = []
    for service in services:
        ecs_client.update_service(
            cluster=cluster,
            service=service,
            forceNewDeployment=True,
        )
        redeployados.append(service)

    LOGGER.info(
        "rds_rotation_ecs_redeploy_concluido secretArn=%s cluster=%s services=%s",
        secret_arn,
        cluster,
        ",".join(redeployados),
    )
    return {
        "matched": True,
        "reason": "rotation_succeeded",
        "secretArn": secret_arn,
        "cluster": cluster,
        "servicesRedeployed": redeployados,
    }


def lambda_handler(event: dict[str, Any], context: Any) -> dict[str, Any]:
    del context
    import boto3

    secret_arn = os.environ["TARGET_SECRET_ARN"]
    cluster = os.environ["ECS_CLUSTER"]
    services = _servicos_configurados()
    ecs_client = boto3.client("ecs")
    return processar_evento(
        event,
        ecs_client=ecs_client,
        secret_arn=secret_arn,
        cluster=cluster,
        services=services,
    )
