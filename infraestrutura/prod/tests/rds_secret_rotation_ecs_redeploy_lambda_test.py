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

    def update_service(self, *, cluster, service, forceNewDeployment):
        self.calls.append(
            {
                "cluster": cluster,
                "service": service,
                "forceNewDeployment": forceNewDeployment,
            }
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
            cluster="eickrono-hml",
            services=["auth-hml", "identidade-hml", "thimisu-backend-hml"],
        )

        self.assertTrue(result["matched"])
        self.assertEqual(
            ["auth-hml", "identidade-hml", "thimisu-backend-hml"],
            result["servicesRedeployed"],
        )
        self.assertEqual(
            [
                {
                    "cluster": "eickrono-hml",
                    "service": "auth-hml",
                    "forceNewDeployment": True,
                },
                {
                    "cluster": "eickrono-hml",
                    "service": "identidade-hml",
                    "forceNewDeployment": True,
                },
                {
                    "cluster": "eickrono-hml",
                    "service": "thimisu-backend-hml",
                    "forceNewDeployment": True,
                },
            ],
            ecs_client.calls,
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
            cluster="eickrono-hml",
            services=["auth-hml"],
        )

        self.assertFalse(result["matched"])
        self.assertEqual([], ecs_client.calls)

    def test_lambda_handler_reads_environment(self):
        previous_secret = os.environ.get("TARGET_SECRET_ARN")
        previous_cluster = os.environ.get("ECS_CLUSTER")
        previous_services = os.environ.get("ECS_SERVICES")
        try:
            os.environ["TARGET_SECRET_ARN"] = (
                "arn:aws:secretsmanager:sa-east-1:531708494702:secret:rds!db-abc"
            )
            os.environ["ECS_CLUSTER"] = "eickrono-hml"
            os.environ["ECS_SERVICES"] = "auth-hml,identidade-hml"

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
                ["auth-hml", "identidade-hml"],
                result["servicesRedeployed"],
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


if __name__ == "__main__":
    unittest.main()
