"""Normalize heterogeneous NLQ and database errors into stable feedback codes."""

from __future__ import annotations

import re
from typing import Any


_RULES = (
    ("COLUMN_NOT_FOUND", re.compile(r"unknown column|column .+ (?:not found|does not exist)|字段.+不存在", re.I)),
    ("TABLE_NOT_FOUND", re.compile(r"table .+ (?:doesn't exist|not found|does not exist)|表.+不存在", re.I)),
    ("QUERY_TIMEOUT", re.compile(r"timed?\s*out|timeout|超时", re.I)),
    ("CONNECTION_ERROR", re.compile(r"connection refused|connection reset|could not connect|连接失败|网络中断", re.I)),
    ("DIMENSION_NOT_COMPATIBLE", re.compile(r"没有共用事实表|not compatible|incompatible dimension", re.I)),
    ("SQL_SYNTAX_ERROR", re.compile(r"sql syntax|parse error|语法错误", re.I)),
)


def classify_error(error: Any, diagnostic_code: str = "") -> str:
    diagnostic = str(diagnostic_code or "").strip().upper()
    text = str(error or "")
    for code, pattern in _RULES:
        if pattern.search(text):
            return code
    if diagnostic:
        return diagnostic
    return "UNKNOWN_EXECUTION_ERROR"
