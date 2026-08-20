"""Small, deterministic privacy filters for feedback observation records."""

from __future__ import annotations

import hashlib
import json
import re
from typing import Any


_SENSITIVE_KEYS = {
    "authorization", "cookie", "dbpassword", "password", "passwd", "pwd",
    "api_key", "apikey", "secret", "token", "access_token", "refresh_token",
}
_PHONE_RE = re.compile(r"(?<!\d)1[3-9]\d{9}(?!\d)")
_EMAIL_RE = re.compile(r"(?i)\b[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}\b")
_BEARER_RE = re.compile(r"(?i)\bBearer\s+[A-Za-z0-9._~+/=-]+")
_PASSWORD_RE = re.compile(
    r"(?i)(password|passwd|pwd|api[_-]?key|access[_-]?token)\s*[:=]\s*([^\s,;]+)"
)


def sha256_text(value: str) -> str:
    return hashlib.sha256(str(value or "").encode("utf-8")).hexdigest()


def sanitize_text(value: Any, max_chars: int = 1000) -> str:
    text = str(value or "")
    text = _BEARER_RE.sub("Bearer ***", text)
    text = _PASSWORD_RE.sub(lambda m: f"{m.group(1)}=***", text)
    text = _PHONE_RE.sub("1**********", text)
    text = _EMAIL_RE.sub("***@***", text)
    return text[:max_chars]


def sanitize_payload(value: Any, *, depth: int = 0) -> Any:
    """Return a JSON-safe payload with credentials and large values removed."""
    if depth > 6:
        return "[truncated]"
    if isinstance(value, dict):
        result = {}
        for key, item in value.items():
            key_text = str(key)
            normalized = key_text.lower().replace("-", "_")
            if normalized in _SENSITIVE_KEYS or any(
                marker in normalized for marker in ("password", "secret", "token", "api_key")
            ):
                continue
            result[key_text[:100]] = sanitize_payload(item, depth=depth + 1)
        return result
    if isinstance(value, (list, tuple, set)):
        return [sanitize_payload(item, depth=depth + 1) for item in list(value)[:100]]
    if value is None or isinstance(value, (bool, int, float)):
        return value
    return sanitize_text(value, max_chars=2000)


def compact_json(value: Any, max_chars: int = 32768) -> str:
    text = json.dumps(sanitize_payload(value), ensure_ascii=False, default=str)
    if len(text) <= max_chars:
        return text
    # Keep the database value valid JSON even when a browser submits an
    # unexpectedly large payload. A sliced JSON string cannot be decoded.
    preview_size = max(0, max_chars - 100)
    return json.dumps(
        {"_truncated": True, "preview": text[:preview_size]},
        ensure_ascii=False,
    )


def json_loads(value: Any, fallback: Any) -> Any:
    if not value:
        return fallback
    try:
        return json.loads(value)
    except (TypeError, ValueError):
        return fallback
