"""Shared LLM configuration for AD.

All OpenAI-compatible LLM calls should resolve through this module so model
changes are applied consistently across web, scripts, and analyzers.
"""
from __future__ import annotations

import os
import uuid
from pathlib import Path
from typing import Optional


DEFAULT_LLM_MODEL = "deepseek-chat"
DEFAULT_LLM_BASE_URL = "https://api.deepseek.com"


def _model_key(model: str) -> str:
    return model.upper().replace("-", "").replace("_", "").replace(" ", "")


def is_gpt55_model(model: str) -> bool:
    return _model_key(model) in {"GPT5.5", "GPT55"}


def is_deepseek_model(model: str) -> bool:
    return _model_key(model).startswith("DEEPSEEK")


def load_env(base_dir: Optional[Path] = None) -> dict[str, str]:
    """Parse project .env, then ~/.env as a fallback."""
    env: dict[str, str] = {}
    candidates: list[Path] = []
    if base_dir:
        candidates.append(base_dir / ".env")
    candidates.append(Path(__file__).parent.parent.parent / ".env")
    candidates.append(Path.home() / ".env")

    for p in candidates:
        if not p.exists():
            continue
        for line in p.read_text(encoding="utf-8").splitlines():
            line = line.strip()
            if not line or line.startswith("#") or "=" not in line:
                continue
            k, v = line.split("=", 1)
            env[k.strip()] = v.strip().strip('"').strip("'")
        break
    return env


def load_llm_config_file(base_dir: Optional[Path] = None) -> dict[str, str]:
    """Read non-committed local YAML LLM config when present.

    Supported shapes:
      llm: {api_key, base_url, model}
      deepseek: {api_key, base_url, model}
    """
    candidates: list[Path] = []
    if base_dir:
        candidates.extend([base_dir / "config.local.yaml", base_dir / "config.yaml"])
    candidates.extend([
        Path(__file__).parent.parent.parent / "config.local.yaml",
        Path(__file__).parent.parent.parent / "config.yaml",
    ])
    for p in candidates:
        if not p.exists():
            continue
        try:
            import yaml
            data = yaml.safe_load(p.read_text(encoding="utf-8")) or {}
        except Exception:
            continue
        if not isinstance(data, dict):
            continue
        deepseek = data.get("deepseek") if isinstance(data.get("deepseek"), dict) else {}
        llm = data.get("llm") if isinstance(data.get("llm"), dict) else {}
        merged = {**llm, **deepseek}
        if merged:
            return {
                "api_key": str(merged.get("api_key") or merged.get("apiKey") or "").strip(),
                "base_url": str(merged.get("base_url") or merged.get("baseUrl") or "").strip(),
                "model": str(merged.get("model") or merged.get("model_name") or merged.get("modelName") or "").strip(),
            }
    return {}


def llm_config_from_env(
    base_dir: Optional[Path] = None,
    model_override: str = "",
) -> dict[str, str]:
    """Return the unified LLM config.

    Provider-specific variables take priority. Existing LLM_* names are kept as
    fallbacks so old deployments do not need immediate secret renames.
    """
    env = {**os.environ, **load_env(base_dir)}
    file_cfg = load_llm_config_file(base_dir)
    model = (
        model_override.strip()
        or env.get("DEEPSEEK_MODEL_NAME", "").strip()
        or file_cfg.get("model", "").strip()
        or env.get("LLM_MODEL_NAME", "").strip()
        or env.get("GPT55_MODEL_NAME", "").strip()
        or env.get("OPENAI_MODEL_NAME", "").strip()
        or env.get("OPENAI_MODEL", "").strip()
        or DEFAULT_LLM_MODEL
    )
    api_key = (
        env.get("DEEPSEEK_API_KEY", "").strip()
        or file_cfg.get("api_key", "").strip()
        or env.get("LLM_API_KEY", "").strip()
        or env.get("GPT55_API_KEY", "").strip()
        or env.get("OPENAI_API_KEY", "").strip()
    )
    base_url = (
        env.get("DEEPSEEK_BASE_URL", "").strip()
        or file_cfg.get("base_url", "").strip()
        or env.get("LLM_BASE_URL", "").strip()
        or env.get("GPT55_BASE_URL", "").strip()
        or env.get("OPENAI_BASE_URL", "").strip()
        or DEFAULT_LLM_BASE_URL
    ).rstrip("/")

    return {
        "api_key": api_key,
        "base_url": base_url,
        "model": model,
        "provider": "deepseek" if "deepseek" in base_url.lower() or is_deepseek_model(model) else "openai_compatible",
    }


def chat_completions_url(base_url: str) -> str:
    """Return a usable OpenAI-compatible chat completions endpoint.

    Some internal gateways configure LLM_BASE_URL as the full
    /chat/completions/... endpoint instead of the API root. Accept both forms so
    all AD features share the same config safely.
    """
    url = (base_url or "").strip().rstrip("/")
    if "/chat/completions" in url:
        return url
    return f"{url}/chat/completions"


def llm_request_headers(cfg: dict[str, str]) -> dict[str, str]:
    """Return auth headers for the configured LLM gateway."""
    api_key = (cfg.get("api_key") or "").strip()
    base_url = (cfg.get("base_url") or "").strip().lower()
    headers = {"Content-Type": "application/json"}
    if "api-hub.inner.chj.cloud" in base_url or "/llm-gateway/" in base_url:
        headers["X-CHJ-GWToken"] = api_key
        headers["BCS-APIHub-RequestId"] = str(uuid.uuid4())
    else:
        headers["Authorization"] = f"Bearer {api_key}"
    return headers


def validate_llm_config(cfg: dict[str, str], purpose: str = "LLM") -> None:
    """Fail fast when the configured model cannot be called."""
    api_key = cfg.get("api_key", "").strip()
    base_url = cfg.get("base_url", "").strip().rstrip("/")
    model = cfg.get("model", "").strip() or DEFAULT_LLM_MODEL

    if not api_key or not base_url:
        raise RuntimeError(
            f"{purpose} requires a configured DeepSeek gateway. "
            "Set DEEPSEEK_API_KEY in apps/ad/.env, or configure deepseek.api_key in apps/ad/config.local.yaml."
        )

    if is_gpt55_model(model) and "minimax.chat" in base_url.lower():
        raise RuntimeError(
            f"{purpose} is configured with model {model}, but base_url points to MiniMax "
            f"({base_url}). Set GPT55_BASE_URL/GPT55_API_KEY to the GPT5.5 gateway, "
            "or change the model to one supported by the configured gateway."
        )

    if is_deepseek_model(model) and "minimax.chat" in base_url.lower():
        raise RuntimeError(
            f"{purpose} is configured with model {model}, but base_url points to MiniMax "
            f"({base_url}). Set DEEPSEEK_BASE_URL/DEEPSEEK_API_KEY to the DeepSeek gateway, "
            "or change the model to one supported by the configured gateway."
        )
