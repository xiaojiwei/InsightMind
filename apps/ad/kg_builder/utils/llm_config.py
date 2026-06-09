"""Shared LLM configuration for AD.

All OpenAI-compatible LLM calls should resolve through this module so model
changes are applied consistently across web, scripts, and analyzers.
"""
from __future__ import annotations

import os
from pathlib import Path
from typing import Optional


DEFAULT_LLM_MODEL = "deepseek-v4-flash"
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


def llm_config_from_env(
    base_dir: Optional[Path] = None,
    model_override: str = "",
) -> dict[str, str]:
    """Return the unified LLM config.

    Provider-specific variables take priority. Existing LLM_* names are kept as
    fallbacks so old deployments do not need immediate secret renames.
    """
    env = {**os.environ, **load_env(base_dir)}
    model = (
        model_override.strip()
        or env.get("DEEPSEEK_MODEL_NAME", "").strip()
        or env.get("GPT55_MODEL_NAME", "").strip()
        or env.get("LLM_MODEL_NAME", "").strip()
        or DEFAULT_LLM_MODEL
    )
    api_key = (
        env.get("DEEPSEEK_API_KEY", "").strip()
        or env.get("GPT55_API_KEY", "").strip()
        or env.get("LLM_API_KEY", "").strip()
    )
    base_url = (
        env.get("DEEPSEEK_BASE_URL", "").strip()
        or env.get("GPT55_BASE_URL", "").strip()
        or env.get("LLM_BASE_URL", "").strip()
        or DEFAULT_LLM_BASE_URL
    ).rstrip("/")

    return {
        "api_key": api_key,
        "base_url": base_url,
        "model": model,
    }


def validate_llm_config(cfg: dict[str, str], purpose: str = "LLM") -> None:
    """Fail fast when the configured model cannot be called."""
    api_key = cfg.get("api_key", "").strip()
    base_url = cfg.get("base_url", "").strip().rstrip("/")
    model = cfg.get("model", "").strip() or DEFAULT_LLM_MODEL

    if not api_key or not base_url:
        raise RuntimeError(
            f"{purpose} requires a configured LLM gateway. "
            "Set DEEPSEEK_BASE_URL and DEEPSEEK_API_KEY, or LLM_BASE_URL and LLM_API_KEY, in .env."
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
