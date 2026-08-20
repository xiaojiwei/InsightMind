"""Resolve the MySQL target used by the feedback observation store."""

from __future__ import annotations

import os
from dataclasses import dataclass
from pathlib import Path
from typing import Any

import yaml


@dataclass(frozen=True)
class FeedbackMySQLConfig:
    host: str
    port: int
    user: str
    password: str
    database: str
    charset: str = "utf8mb4"


def _config_payloads(app_dir: Path):
    for path in (app_dir / "config.local.yaml", app_dir / "config.yaml"):
        if not path.exists():
            continue
        try:
            yield yaml.safe_load(path.read_text(encoding="utf-8")) or {}
        except (OSError, yaml.YAMLError):
            continue


def _configured_mysql(payload: dict[str, Any]) -> dict[str, Any]:
    feedback_mysql = (payload.get("feedback") or {}).get("mysql") or {}
    if isinstance(feedback_mysql, dict) and feedback_mysql:
        return feedback_mysql
    for datasource in payload.get("datasources") or []:
        if (
            isinstance(datasource, dict)
            and str(datasource.get("type") or "").strip().lower() == "mysql"
        ):
            return datasource
    return {}


def load_feedback_mysql_config(app_dir: Path | None = None) -> FeedbackMySQLConfig:
    """Prefer feedback.mysql, then the first AD MySQL datasource, then env defaults."""
    app_dir = app_dir or Path(__file__).resolve().parents[2]
    configured: dict[str, Any] = {}
    for payload in _config_payloads(app_dir):
        configured = _configured_mysql(payload)
        if configured:
            break
    host = os.getenv("FEEDBACK_DB_HOST", str(configured.get("host") or "localhost"))
    port = int(os.getenv("FEEDBACK_DB_PORT", str(configured.get("port") or 3306)))
    user = os.getenv(
        "FEEDBACK_DB_USER",
        str(configured.get("user") or configured.get("username") or "root"),
    )
    password = os.getenv(
        "FEEDBACK_DB_PASSWORD",
        os.getenv("MYSQL_PWD", str(configured.get("password") or "")),
    )
    database = os.getenv(
        "FEEDBACK_DB_NAME",
        str(configured.get("database") or configured.get("db") or ""),
    )
    charset = os.getenv(
        "FEEDBACK_DB_CHARSET", str(configured.get("charset") or "utf8mb4")
    )
    if not database:
        raise ValueError(
            "反馈 MySQL 数据库未配置：请设置 FEEDBACK_DB_NAME，或在 "
            "config.local.yaml 中配置 feedback.mysql/database"
        )
    return FeedbackMySQLConfig(
        host=host,
        port=port,
        user=user,
        password=password,
        database=database,
        charset=charset,
    )
