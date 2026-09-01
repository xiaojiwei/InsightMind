import pytest
from pydantic import ValidationError

from web_app import BuildRequest, DSConfig


def test_build_api_does_not_enable_ai_relation_discovery_by_default() -> None:
    request = BuildRequest(datasource=DSConfig())

    assert request.enable_implicit is False


@pytest.mark.parametrize("threshold", [float("nan"), float("inf"), -0.01, 1.01])
def test_build_api_rejects_invalid_similarity_threshold(threshold: float) -> None:
    with pytest.raises(ValidationError):
        BuildRequest(datasource=DSConfig(), similarity_threshold=threshold)
