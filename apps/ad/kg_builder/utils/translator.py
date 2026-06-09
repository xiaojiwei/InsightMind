"""LLM-based Chinese name translator for database identifiers.

Translates English table/column names to Chinese using an LLM.
Supports OpenAI-compatible APIs (GPT55_* or LLM_* env vars)
as well as the Anthropic SDK (ANTHROPIC_AUTH_TOKEN / ANTHROPIC_API_KEY).
Results are cached locally to avoid repeated API calls.
"""
from __future__ import annotations

import json
import logging
import os
import re
from pathlib import Path
from typing import Dict, List, Optional

from kg_builder.utils.llm_config import (
    DEFAULT_LLM_MODEL,
    llm_config_from_env,
    validate_llm_config,
)

logger = logging.getLogger(__name__)

_ZH_RE = re.compile(r'[\u4e00-\u9fff]')
_CACHE_DIR = Path(__file__).parent.parent.parent / ".cache"
_BATCH_SIZE = 80   # names per LLM call
_ENV_FILE   = Path(__file__).parent.parent.parent / ".env"


def _load_dotenv() -> None:
    """Load .env file into os.environ (minimal implementation, no deps)."""
    if not _ENV_FILE.exists():
        return
    for line in _ENV_FILE.read_text(encoding="utf-8").splitlines():
        line = line.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, _, val = line.partition("=")
        key = key.strip()
        val = val.strip().strip('"').strip("'")
        if key and key not in os.environ:
            os.environ[key] = val


def _has_chinese(s: str) -> bool:
    return bool(_ZH_RE.search(s or ""))


def _cache_slug(model: str) -> str:
    slug = re.sub(r"[^A-Za-z0-9_.-]+", "-", model.strip() or DEFAULT_LLM_MODEL)
    return slug.strip("-") or "default"


def _cache_file_for_model(model: str) -> Path:
    return _CACHE_DIR / f"translations.{_cache_slug(model)}.json"


def _load_cache(cache_file: Path) -> Dict[str, str]:
    try:
        if cache_file.exists():
            return json.loads(cache_file.read_text(encoding="utf-8"))
    except Exception:
        pass
    return {}


def _save_cache(cache: Dict[str, str], cache_file: Path) -> None:
    try:
        cache_file.parent.mkdir(parents=True, exist_ok=True)
        cache_file.write_text(
            json.dumps(cache, ensure_ascii=False, indent=2), encoding="utf-8"
        )
    except Exception as exc:
        logger.warning("Failed to save translation cache: %s", exc)


def _split_identifier(name: str) -> str:
    """Convert snake_case / CamelCase to readable English."""
    s = name.replace("_", " ").replace("-", " ")
    s = re.sub(r"([a-z])([A-Z])", r"\1 \2", s)
    return s.strip()


def _build_prompt(items: list) -> str:
    return (
        "你是一个数据库中文命名专家。\n"
        "下面是一批数据库表名或列名（英文标识符），请把每个名称翻译成简洁、"
        "准确的中文业务名称（2-8个汉字）。\n"
        "规则：\n"
        "1. 只输出 JSON，格式为 {\"原始名\": \"中文名\", ...}\n"
        "2. 中文名要体现业务含义，不要直译每个单词\n"
        "3. 常见后缀：_id→编号, _at/_time→时间, _no→编号, _flag/_status→状态, "
        "_cnt/_count→数量, _amt/_amount→金额, _list→列表\n"
        "4. 不要在中文名中出现英文\n\n"
        f"待翻译列表：\n{json.dumps(items, ensure_ascii=False, indent=2)}"
    )


class LLMTranslator:
    """Translate English DB identifiers to Chinese via LLM.

    Config priority (reads .env automatically):
      1. GPT55_* or LLM_* env vars                    (OpenAI-compatible)
      2. ANTHROPIC_AUTH_TOKEN / ANTHROPIC_API_KEY       (Anthropic SDK)
    """

    def __init__(self, model: Optional[str] = None) -> None:
        _load_dotenv()
        self._model = model  # override; None → read from env
        cfg = llm_config_from_env(model_override=self._model or "")
        self._cache_file = _cache_file_for_model(cfg["model"])
        self._cache = _load_cache(self._cache_file)

    # ------------------------------------------------------------------ #
    # Public
    # ------------------------------------------------------------------ #

    def translate(
        self,
        names: List[str],
        hints: Optional[Dict[str, str]] = None,
    ) -> Dict[str, str]:
        """Translate a list of English DB identifiers to Chinese.

        Already-cached or already-Chinese names are returned immediately.
        New names are batch-sent to the LLM and cached.

        Args:
            names:  Raw DB identifiers, e.g. ['order_value_list', 'user_id']
            hints:  Optional {name: comment} for extra context.

        Returns:
            Dict mapping each name to its Chinese translation.
        """
        hints = hints or {}
        result: Dict[str, str] = {}

        to_translate: List[str] = []
        for name in names:
            if not name:
                continue
            if _has_chinese(name):
                result[name] = name
            elif name in self._cache:
                result[name] = self._cache[name]
            else:
                to_translate.append(name)

        if not to_translate:
            return result

        cfg = self._get_config()

        logger.info("Translating %d identifiers via LLM ...", len(to_translate))
        total_batches = (len(to_translate) + _BATCH_SIZE - 1) // _BATCH_SIZE
        for i in range(0, len(to_translate), _BATCH_SIZE):
            batch = to_translate[i: i + _BATCH_SIZE]
            batch_no = i // _BATCH_SIZE + 1
            logger.info("  LLM 翻译批次 %d/%d（%d 个）…", batch_no, total_batches, len(batch))
            try:
                translations = self._call_llm(batch, hints, cfg)
                for name, zh in translations.items():
                    self._cache[name] = zh
                    result[name] = zh
                _save_cache(self._cache, self._cache_file)
                logger.info("  批次 %d/%d 完成", batch_no, total_batches)
            except Exception as exc:
                raise RuntimeError(f"LLM translation failed at batch {batch_no}/{total_batches}: {exc}") from exc

        return result

    # ------------------------------------------------------------------ #
    # Config detection
    # ------------------------------------------------------------------ #

    def _get_config(self) -> Dict[str, str]:
        """Return {api_key, base_url, model, backend}; raise if unusable."""
        # Priority 1: LLM_* env vars (OpenAI-compatible gateway)
        cfg = llm_config_from_env(model_override=self._model or "")
        llm_key = cfg["api_key"]
        llm_url = cfg["base_url"]
        llm_model = cfg["model"]
        try:
            validate_llm_config(cfg, purpose="LLM translation")
            return {
                "api_key":  llm_key,
                "base_url": llm_url,
                "model":    llm_model,
                "backend":  "openai_compat",
            }
        except RuntimeError as openai_error:
            openai_config_error = openai_error

        # Priority 2: Anthropic SDK env vars
        anth_key = (
            os.environ.get("ANTHROPIC_API_KEY")
            or os.environ.get("ANTHROPIC_AUTH_TOKEN")
            or ""
        )
        if anth_key:
            return {
                "api_key":  anth_key,
                "base_url": os.environ.get("ANTHROPIC_BASE_URL", ""),
                "model":    self._model or os.environ.get("LLM_MODEL_NAME", DEFAULT_LLM_MODEL),
                "backend":  "anthropic",
            }

        raise openai_config_error

    # ------------------------------------------------------------------ #
    # LLM call
    # ------------------------------------------------------------------ #

    def _call_llm(
        self,
        names: List[str],
        hints: Dict[str, str],
        cfg: Dict[str, str],
    ) -> Dict[str, str]:
        items = []
        for name in names:
            entry: dict = {"name": name, "readable": _split_identifier(name)}
            comment = hints.get(name, "")
            if comment and not _has_chinese(comment):
                entry["comment"] = comment
            items.append(entry)

        prompt = _build_prompt(items)

        if cfg["backend"] == "openai_compat":
            raw = self._call_openai_compat(prompt, cfg)
        else:
            raw = self._call_anthropic(prompt, cfg)

        # Parse JSON from response
        json_match = re.search(r"\{[\s\S]*\}", raw)
        if not json_match:
            raise ValueError(f"No JSON in LLM response: {raw[:200]}")

        mapping = json.loads(json_match.group())
        result: Dict[str, str] = {}
        for name in names:
            zh = mapping.get(name, "")
            result[name] = zh if zh and _has_chinese(zh) else name
        return result

    def _call_openai_compat(self, prompt: str, cfg: Dict[str, str]) -> str:
        """Call an OpenAI-compatible /chat/completions endpoint via httpx."""
        import httpx

        url = cfg["base_url"].rstrip("/") + "/chat/completions"
        headers = {
            "Authorization": f"Bearer {cfg['api_key']}",
            "Content-Type":  "application/json",
        }
        payload = {
            "model": cfg["model"],
            "max_tokens": 4096,
            "messages": [{"role": "user", "content": prompt}],
        }
        resp = httpx.post(url, headers=headers, json=payload, timeout=60)
        try:
            resp.raise_for_status()
        except httpx.HTTPStatusError as exc:
            body = exc.response.text[:1000]
            raise RuntimeError(f"{exc.response.status_code} {exc.response.reason_phrase}: {body}") from exc
        data = resp.json()
        choice = data["choices"][0]
        message = choice.get("message", {})
        content = message.get("content") or ""
        if not content:
            finish_reason = choice.get("finish_reason", "")
            reasoning_len = len(message.get("reasoning_content") or "")
            raise RuntimeError(
                f"empty LLM content (finish_reason={finish_reason}, reasoning_len={reasoning_len})"
            )
        return content

    def _call_anthropic(self, prompt: str, cfg: Dict[str, str]) -> str:
        """Call the Anthropic Messages API via the SDK."""
        from anthropic import Anthropic
        kwargs: dict = {"api_key": cfg["api_key"]}
        if cfg.get("base_url"):
            kwargs["base_url"] = cfg["base_url"]
        client = Anthropic(**kwargs)
        msg = client.messages.create(
            model=cfg["model"],
            max_tokens=2048,
            messages=[{"role": "user", "content": prompt}],
        )
        return msg.content[0].text
