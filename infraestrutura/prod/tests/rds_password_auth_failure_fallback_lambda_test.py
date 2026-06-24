import base64
import gzip
import importlib.util
import json
from pathlib import Path
import unittest


MODULE_PATH = (
    Path(__file__).resolve().parent.parent
    / "ecs"
    / "lambda"
    / "rds_password_auth_failure_fallback"
    / "handler.py"
)
SPEC = importlib.util.spec_from_file_location(
    "rds_password_auth_failure_fallback_handler",
    MODULE_PATH,
)
handler = importlib.util.module_from_spec(SPEC)
assert SPEC is not None and SPEC.loader is not None
SPEC.loader.exec_module(handler)


class ParameterNotFound(Exception):
    pass


class SsmClientFake:
    def __init__(self, ultimo_redeploy=None) -> None:
        self.ultimo_redeploy = ultimo_redeploy
        self.puts = []

    def get_parameter(self, *, Name):
        if self.ultimo_redeploy is None:
            raise ParameterNotFound()
        return {
            "Parameter": {
                "Name": Name,
                "Value": str(self.ultimo_redeploy),
            }
        }

    def put_parameter(self, *, Name, Value, Type, Overwrite):
        self.puts.append(
            {
                "Name": Name,
                "Value": Value,
                "Type": Type,
                "Overwrite": Overwrite,
            }
        )


class WaiterFake:
    def __init__(self, ecs_client, name) -> None:
        self.ecs_client = ecs_client
        self.name = name

    def wait(self, **kwargs):
        if self.name == "tasks_stopped":
            self.ecs_client.wait_calls.append(kwargs)
            self.ecs_client.operations.append(("wait_tasks", tuple(kwargs["tasks"])))
            return
        if self.name == "services_stable":
            self.ecs_client.wait_service_calls.append(kwargs)
            self.ecs_client.operations.append(
                (
                    "wait_service",
                    kwargs["cluster"],
                    tuple(kwargs["services"]),
                    kwargs["WaiterConfig"]["Delay"],
                    kwargs["WaiterConfig"]["MaxAttempts"],
                )
            )
            return
        raise AssertionError(self.name)


class EcsClientFake:
    def __init__(self, validation_exit_code=0, run_failures=None) -> None:
        self.validation_exit_code = validation_exit_code
        self.run_failures = [] if run_failures is None else run_failures
        self.run_task_calls = []
        self.wait_calls = []
        self.wait_service_calls = []
        self.update_service_calls = []
        self.operations = []

    def run_task(self, **kwargs):
        self.run_task_calls.append(kwargs)
        if self.run_failures:
            return {
                "failures": self.run_failures,
                "tasks": [],
            }
        return {
            "failures": [],
            "tasks": [
                {
                    "taskArn": "arn:aws:ecs:task/eickrono-stg/validacao-1",
                }
            ],
        }

    def get_waiter(self, name):
        if name not in ("tasks_stopped", "services_stable"):
            raise AssertionError(name)
        return WaiterFake(self, name)

    def describe_tasks(self, *, cluster, tasks):
        return {
            "tasks": [
                {
                    "containers": [
                        {
                            "name": "psql",
                            "exitCode": self.validation_exit_code,
                        }
                    ]
                }
            ]
        }

    def update_service(self, *, cluster, service, forceNewDeployment):
        self.update_service_calls.append(
            {
                "cluster": cluster,
                "service": service,
                "forceNewDeployment": forceNewDeployment,
            }
        )
        self.operations.append(("update", service))


def cloudwatch_logs_event(*messages):
    payload = {
        "messageType": "DATA_MESSAGE",
        "owner": "531708494702",
        "logGroup": "/ecs/stg/identidade",
        "logStream": "ecs/identidade/task-1",
        "logEvents": [
            {
                "id": str(index),
                "timestamp": 1780387338000 + index,
                "message": message,
            }
            for index, message in enumerate(messages)
        ],
    }
    compressed = gzip.compress(json.dumps(payload).encode("utf-8"))
    return {
        "awslogs": {
            "data": base64.b64encode(compressed).decode("ascii"),
        }
    }


def processar(evento, ecs_client, ssm_client, agora=1000.0):
    return handler.processar_evento(
        evento,
        ecs_client=ecs_client,
        ssm_client=ssm_client,
        cluster="eickrono-stg",
        services=["autenticacao-api-stg", "identidade-stg"],
        cooldown_parameter_name="/eickrono/stg/rds/fallback/last-run",
        cooldown_segundos=900,
        validation_task_definition="eickrono-stg-db-query-codex:1",
        validation_container_name="psql",
        validation_subnets=["subnet-1", "subnet-2"],
        validation_security_groups=["sg-1"],
        validation_database="eickrono_identidade_stg",
        waiter_delay_seconds=3,
        waiter_max_attempts=4,
        agora=agora,
    )


class RdsPasswordAuthFailureFallbackLambdaTest(unittest.TestCase):
    def test_ignores_event_without_monitored_error(self):
        ecs_client = EcsClientFake()
        ssm_client = SsmClientFake()
        event = cloudwatch_logs_event("qualquer outro erro")

        result = processar(event, ecs_client, ssm_client)

        self.assertFalse(result["matched"])
        self.assertEqual([], ecs_client.run_task_calls)
        self.assertEqual([], ecs_client.update_service_calls)

    def test_validates_secret_and_redeploys_services_when_error_matches(self):
        ecs_client = EcsClientFake(validation_exit_code=0)
        ssm_client = SsmClientFake()
        event = cloudwatch_logs_event(
            'FATAL: password authentication failed for user "eickrono_admin"',
        )

        result = processar(event, ecs_client, ssm_client)

        self.assertTrue(result["matched"])
        self.assertTrue(result["redeployed"])
        self.assertEqual(
            ["autenticacao-api-stg", "identidade-stg"],
            result["servicesRedeployed"],
        )
        self.assertEqual(1, len(ecs_client.run_task_calls))
        run_call = ecs_client.run_task_calls[0]
        self.assertEqual("eickrono-stg", run_call["cluster"])
        self.assertEqual("eickrono-stg-db-query-codex:1", run_call["taskDefinition"])
        self.assertEqual(
            ["subnet-1", "subnet-2"],
            run_call["networkConfiguration"]["awsvpcConfiguration"]["subnets"],
        )
        self.assertEqual(
            [
                {
                    "cluster": "eickrono-stg",
                    "service": "autenticacao-api-stg",
                    "forceNewDeployment": True,
                },
                {
                    "cluster": "eickrono-stg",
                    "service": "identidade-stg",
                    "forceNewDeployment": True,
                },
            ],
            ecs_client.update_service_calls,
        )
        self.assertEqual(
            [
                ("wait_tasks", ("arn:aws:ecs:task/eickrono-stg/validacao-1",)),
                ("update", "autenticacao-api-stg"),
                ("wait_service", "eickrono-stg", ("autenticacao-api-stg",), 3, 4),
                ("update", "identidade-stg"),
                ("wait_service", "eickrono-stg", ("identidade-stg",), 3, 4),
            ],
            ecs_client.operations,
        )
        self.assertEqual(
            [
                {
                    "Name": "/eickrono/stg/rds/fallback/last-run",
                    "Value": "1000.0",
                    "Type": "String",
                    "Overwrite": True,
                }
            ],
            ssm_client.puts,
        )

    def test_does_not_redeploy_when_validation_task_fails(self):
        ecs_client = EcsClientFake(validation_exit_code=1)
        ssm_client = SsmClientFake()
        event = cloudwatch_logs_event(
            'FATAL: password authentication failed for user "eickrono_admin"',
        )

        result = processar(event, ecs_client, ssm_client)

        self.assertTrue(result["matched"])
        self.assertFalse(result["redeployed"])
        self.assertEqual("secret_atual_nao_validado", result["reason"])
        self.assertEqual([], ecs_client.update_service_calls)
        self.assertEqual([], ssm_client.puts)

    def test_does_not_validate_or_redeploy_during_cooldown(self):
        ecs_client = EcsClientFake()
        ssm_client = SsmClientFake(ultimo_redeploy=500.0)
        event = cloudwatch_logs_event(
            'FATAL: password authentication failed for user "eickrono_admin"',
        )

        result = processar(event, ecs_client, ssm_client, agora=1000.0)

        self.assertTrue(result["matched"])
        self.assertEqual("cooldown_ativo", result["reason"])
        self.assertEqual([], ecs_client.run_task_calls)
        self.assertEqual([], ecs_client.update_service_calls)
        self.assertEqual([], ssm_client.puts)


if __name__ == "__main__":
    unittest.main()
