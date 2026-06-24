import importlib.util
import os
from pathlib import Path
import sys
import types
import unittest


MODULE_PATH = (
    Path(__file__).resolve().parent.parent
    / "ecs"
    / "lambda"
    / "rds_rotation_ecs_redeploy"
    / "handler.py"
)
SPEC = importlib.util.spec_from_file_location("rds_rotation_ecs_redeploy_handler", MODULE_PATH)
handler = importlib.util.module_from_spec(SPEC)
assert SPEC is not None and SPEC.loader is not None
SPEC.loader.exec_module(handler)


class EcsClientFake:
    def __init__(self) -> None:
        self.calls = []
        self.operations = []

    def update_service(self, *, cluster, service, forceNewDeployment):
        self.calls.append(
            {
                "cluster": cluster,
                "service": service,
                "forceNewDeployment": forceNewDeployment,
            }
        )
        self.operations.append(("update", service))

    def get_waiter(self, name):
        if name != "services_stable":
            raise AssertionError(name)
        return ServicesStableWaiterFake(self)


class ServicesStableWaiterFake:
    def __init__(self, ecs_client) -> None:
        self.ecs_client = ecs_client

    def wait(self, *, cluster, services, WaiterConfig):
        self.ecs_client.operations.append(
            (
                "wait",
                cluster,
                tuple(services),
                WaiterConfig["Delay"],
                WaiterConfig["MaxAttempts"],
            )
        )


class RotationRedeployLambdaTest(unittest.TestCase):
    def test_redeploys_all_services_when_rotation_succeeds_for_target_secret(self):
        ecs_client = EcsClientFake()
        event = {
            "detail-type": "AWS Service Event via CloudTrail",
            "resources": [
                "arn:aws:secretsmanager:sa-east-1:531708494702:secret:rds!db-abc",
            ],
            "detail": {
                "eventSource": "secretsmanager.amazonaws.com",
                "eventName": "RotationSucceeded",
                "responseElements": {
                    "arn": "arn:aws:secretsmanager:sa-east-1:531708494702:secret:rds!db-abc",
                },
            },
        }

        result = handler.processar_evento(
            event,
            ecs_client=ecs_client,
            secret_arn="arn:aws:secretsmanager:sa-east-1:531708494702:secret:rds!db-abc",
            cluster="eickrono-stg",
            services=["auth-stg", "identidade-stg", "thimisu-backend-stg"],
        )

        self.assertTrue(result["matched"])
        self.assertEqual(
            ["auth-stg", "identidade-stg", "thimisu-backend-stg"],
            result["servicesRedeployed"],
        )
        self.assertEqual(
            [
                {
                    "cluster": "eickrono-stg",
                    "service": "auth-stg",
                    "forceNewDeployment": True,
                },
                {
                    "cluster": "eickrono-stg",
                    "service": "identidade-stg",
                    "forceNewDeployment": True,
                },
                {
                    "cluster": "eickrono-stg",
                    "service": "thimisu-backend-stg",
                    "forceNewDeployment": True,
                },
            ],
            ecs_client.calls,
        )
        self.assertEqual(
            [
                ("update", "auth-stg"),
                ("wait", "eickrono-stg", ("auth-stg",), 15, 40),
                ("update", "identidade-stg"),
                ("wait", "eickrono-stg", ("identidade-stg",), 15, 40),
                ("update", "thimisu-backend-stg"),
                ("wait", "eickrono-stg", ("thimisu-backend-stg",), 15, 40),
            ],
            ecs_client.operations,
        )

    def test_redeploys_when_rds_rotation_reports_secret_in_additional_event_data(self):
        ecs_client = EcsClientFake()
        event = {
            "detail-type": "AWS Service Event via CloudTrail",
            "resources": [],
            "detail": {
                "eventSource": "secretsmanager.amazonaws.com",
                "eventName": "RotationSucceeded",
                "responseElements": None,
                "additionalEventData": {
                    "SecretId": (
                        "arn:aws:secretsmanager:sa-east-1:531708494702:"
                        "secret:rds!db-abc"
                    ),
                },
            },
        }

        result = handler.processar_evento(
            event,
            ecs_client=ecs_client,
            secret_arn="arn:aws:secretsmanager:sa-east-1:531708494702:secret:rds!db-abc",
            cluster="eickrono-stg",
            services=["auth-stg", "identidade-stg", "thimisu-backend-stg"],
        )

        self.assertTrue(result["matched"])
        self.assertEqual(
            ["auth-stg", "identidade-stg", "thimisu-backend-stg"],
            result["servicesRedeployed"],
        )
        self.assertEqual(
            [
                {
                    "cluster": "eickrono-stg",
                    "service": "auth-stg",
                    "forceNewDeployment": True,
                },
                {
                    "cluster": "eickrono-stg",
                    "service": "identidade-stg",
                    "forceNewDeployment": True,
                },
                {
                    "cluster": "eickrono-stg",
                    "service": "thimisu-backend-stg",
                    "forceNewDeployment": True,
                },
            ],
            ecs_client.calls,
        )
        self.assertEqual(
            [
                ("update", "auth-stg"),
                ("wait", "eickrono-stg", ("auth-stg",), 15, 40),
                ("update", "identidade-stg"),
                ("wait", "eickrono-stg", ("identidade-stg",), 15, 40),
                ("update", "thimisu-backend-stg"),
                ("wait", "eickrono-stg", ("thimisu-backend-stg",), 15, 40),
            ],
            ecs_client.operations,
        )

    def test_ignores_event_for_other_secret(self):
        ecs_client = EcsClientFake()
        event = {
            "detail": {
                "eventSource": "secretsmanager.amazonaws.com",
                "eventName": "RotationSucceeded",
                "responseElements": {
                    "aRN": "arn:aws:secretsmanager:sa-east-1:531708494702:secret:rds!db-outra",
                },
            },
        }

        result = handler.processar_evento(
            event,
            ecs_client=ecs_client,
            secret_arn="arn:aws:secretsmanager:sa-east-1:531708494702:secret:rds!db-abc",
            cluster="eickrono-stg",
            services=["auth-stg"],
        )

        self.assertFalse(result["matched"])
        self.assertEqual([], ecs_client.calls)

    def test_lambda_handler_reads_environment(self):
        previous_secret = os.environ.get("TARGET_SECRET_ARN")
        previous_cluster = os.environ.get("ECS_CLUSTER")
        previous_services = os.environ.get("ECS_SERVICES")
        previous_waiter_delay = os.environ.get("SERVICE_STABLE_WAITER_DELAY_SECONDS")
        previous_waiter_attempts = os.environ.get("SERVICE_STABLE_WAITER_MAX_ATTEMPTS")
        try:
            os.environ["TARGET_SECRET_ARN"] = (
                "arn:aws:secretsmanager:sa-east-1:531708494702:secret:rds!db-abc"
            )
            os.environ["ECS_CLUSTER"] = "eickrono-stg"
            os.environ["ECS_SERVICES"] = "auth-stg,identidade-stg"
            os.environ["SERVICE_STABLE_WAITER_DELAY_SECONDS"] = "3"
            os.environ["SERVICE_STABLE_WAITER_MAX_ATTEMPTS"] = "4"

            class Boto3Fake:
                def __init__(self):
                    self.ecs = EcsClientFake()

                def client(self, name):
                    self.assertEqual("ecs", name)
                    return self.ecs

                def assertEqual(self, expected, actual):
                    if expected != actual:
                        raise AssertionError((expected, actual))

            boto3_fake = Boto3Fake()
            boto3_module = types.SimpleNamespace(client=boto3_fake.client)
            boto3_original = sys.modules.get("boto3")
            sys.modules["boto3"] = boto3_module
            try:
                result = handler.lambda_handler(
                    {
                        "resources": [
                            "arn:aws:secretsmanager:sa-east-1:531708494702:secret:rds!db-abc",
                        ],
                        "detail": {
                            "eventSource": "secretsmanager.amazonaws.com",
                            "eventName": "RotationSucceeded",
                        },
                    },
                    None,
                )
            finally:
                if boto3_original is None:
                    sys.modules.pop("boto3", None)
                else:
                    sys.modules["boto3"] = boto3_original

            self.assertTrue(result["matched"])
            self.assertEqual(
                ["auth-stg", "identidade-stg"],
                result["servicesRedeployed"],
            )
            self.assertEqual(
                [
                    ("update", "auth-stg"),
                    ("wait", "eickrono-stg", ("auth-stg",), 3, 4),
                    ("update", "identidade-stg"),
                    ("wait", "eickrono-stg", ("identidade-stg",), 3, 4),
                ],
                boto3_fake.ecs.operations,
            )
        finally:
            if previous_secret is None:
                os.environ.pop("TARGET_SECRET_ARN", None)
            else:
                os.environ["TARGET_SECRET_ARN"] = previous_secret
            if previous_cluster is None:
                os.environ.pop("ECS_CLUSTER", None)
            else:
                os.environ["ECS_CLUSTER"] = previous_cluster
            if previous_services is None:
                os.environ.pop("ECS_SERVICES", None)
            else:
                os.environ["ECS_SERVICES"] = previous_services
            if previous_waiter_delay is None:
                os.environ.pop("SERVICE_STABLE_WAITER_DELAY_SECONDS", None)
            else:
                os.environ["SERVICE_STABLE_WAITER_DELAY_SECONDS"] = previous_waiter_delay
            if previous_waiter_attempts is None:
                os.environ.pop("SERVICE_STABLE_WAITER_MAX_ATTEMPTS", None)
            else:
                os.environ["SERVICE_STABLE_WAITER_MAX_ATTEMPTS"] = previous_waiter_attempts


if __name__ == "__main__":
    unittest.main()
