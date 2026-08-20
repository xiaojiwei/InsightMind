"""Deterministic text normalization and privacy guards."""

from __future__ import annotations

import hashlib
import json
import re
import unicodedata
from typing import Any, Iterable


_CODE_RE = re.compile(r"(?:MEAS|DIM)_[A-Za-z0-9_]+", re.I)
_TOKEN_RE = re.compile(r"(?:MEAS|DIM)_[A-Za-z0-9_]+|[A-Za-z0-9_]+|[\u4e00-\u9fff]+", re.I)
_SENSITIVE_DIMENSION_RE = re.compile(
    r"手机号|手机号码|电话|邮箱|邮件|身份证|证件|详细地址|住址|银行卡|"
    r"姓名|名字|联系人|员工|顾问|客户名称|用户名称|编号|单号|流水号|唯一标识|"
    r"phone|mobile|email|id[_ ]?card|address|bank[_ ]?card|"
    r"专家|专员|销售员|车架号|车辆识别|车牌|护照|微信|设备号|IP地址|"
    r"employee|advisor|consultant|expert|specialist|sales[_ ]?(?:person|rep)|"
    r"contact[_ ]?name|customer[_ ]?name|user[_ ]?name|vin|license[_ ]?plate|"
    r"passport|wechat|device[_ ]?id|ip[_ ]?address|"
    r"(?:^|[_ ])(?:[a-z]+_)?id(?:$|[_ ])",
    re.I,
)
_SENSITIVE_VALUE_RE = re.compile(
    r"(?:\b1[3-9]\d{9}\b)|"
    r"(?:\b\d{15}(?:\d{2}[0-9Xx])?\b)|"
    r"(?:\b[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}\b)",
    re.I,
)
_UNSAFE_VALUE_SYNTAX_RE = re.compile(
    r"[\x00-\x08\x0b\x0c\x0e-\x1f\x7f]|--|/\*|\*/|;",
    re.I,
)
_EXPRESSION_VALUE_RE = re.compile(r"\b(?:THEN|ELSE)\s+(['\"])(.*?)\1", re.I | re.S)


def normalize_text(value: Any) -> str:
    text = unicodedata.normalize("NFKC", str(value or "")).lower().strip()
    return re.sub(r"[^0-9a-z\u4e00-\u9fff]+", "", text)


def tokenize(value: Any, *, chinese_ngrams: bool = True) -> list[str]:
    text = unicodedata.normalize("NFKC", str(value or ""))
    result: list[str] = []
    seen: set[str] = set()
    for raw in _TOKEN_RE.findall(text):
        norm = normalize_text(raw)
        if len(norm) >= 2 and norm not in seen:
            seen.add(norm)
            result.append(norm)
        if chinese_ngrams and re.fullmatch(r"[\u4e00-\u9fff]+", raw) and len(raw) > 2:
            max_n = min(6, len(raw))
            for size in range(2, max_n + 1):
                for start in range(len(raw) - size + 1):
                    part = normalize_text(raw[start:start + size])
                    if part not in seen:
                        seen.add(part)
                        result.append(part)
        if "_" in raw:
            for part in raw.split("_"):
                part = normalize_text(part)
                if len(part) >= 2 and part not in seen:
                    seen.add(part)
                    result.append(part)
    return result


def explicit_codes(value: Any, prefix: str) -> set[str]:
    wanted = prefix.upper() + "_"
    return {match.upper() for match in _CODE_RE.findall(str(value or "")) if match.upper().startswith(wanted)}


def stable_hash(value: Any) -> str:
    payload = json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(",", ":"), default=str)
    return hashlib.sha256(payload.encode("utf-8")).hexdigest()


def file_sha256(path: Any) -> str:
    digest = hashlib.sha256()
    with open(path, "rb") as handle:
        for block in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def is_sensitive_dimension(*parts: Any) -> bool:
    return bool(_SENSITIVE_DIMENSION_RE.search(" ".join(str(part or "") for part in parts)))


def is_safe_dimension_value(value: Any, *, max_length: int = 80) -> bool:
    text = str(value or "").strip()
    if (
        not text
        or len(text) > max_length
        or _SENSITIVE_VALUE_RE.search(text)
        or _UNSAFE_VALUE_SYNTAX_RE.search(text)
    ):
        return False
    if text.lower() in {"null", "none", "nan", "unknown", "未知", "空"}:
        return False
    if re.fullmatch(r"\d{8,}", text):
        return False
    return True


def expression_values(expression: Any) -> list[str]:
    values: list[str] = []
    for _quote, raw in _EXPRESSION_VALUE_RE.findall(str(expression or "")):
        value = raw.strip()
        if value and value not in values:
            values.append(value)
    return values


def unique_strings(values: Iterable[Any]) -> list[str]:
    result: list[str] = []
    seen: set[str] = set()
    for value in values:
        text = str(value or "").strip()
        norm = normalize_text(text)
        if text and norm and norm not in seen:
            seen.add(norm)
            result.append(text)
    return result
