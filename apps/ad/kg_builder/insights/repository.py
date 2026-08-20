"""MySQL persistence for forecast runs, candidate relationships, and insight facts."""

from __future__ import annotations

import json
import os
import threading
from contextlib import contextmanager
from dataclasses import dataclass
from datetime import datetime, timezone
from pathlib import Path
from typing import Any, Callable, Iterator

import pymysql
import yaml
from pymysql.cursors import DictCursor

from .contracts import ForecastResult, InsightFact, compact_json


@dataclass(frozen=True)
class InsightMySQLConfig:
    host: str
    port: int
    user: str
    password: str
    database: str
    charset: str = "utf8mb4"


def load_insight_mysql_config(app_dir: Path | None = None) -> InsightMySQLConfig:
    app_dir = app_dir or Path(__file__).resolve().parents[2]
    configured: dict[str, Any] = {}
    for path in (app_dir / "config.local.yaml", app_dir / "config.yaml"):
        if not path.exists():
            continue
        try:
            payload = yaml.safe_load(path.read_text(encoding="utf-8")) or {}
        except (OSError, yaml.YAMLError):
            continue
        configured = ((payload.get("insights") or {}).get("mysql") or {})
        if not configured:
            configured = ((payload.get("feedback") or {}).get("mysql") or {})
        if not configured:
            configured = next((item for item in payload.get("datasources") or [] if str(item.get("type") or "").lower() == "mysql"), {})
        if configured:
            break
    database = os.getenv("INSIGHTS_DB_NAME", str(configured.get("database") or configured.get("db") or ""))
    if not database:
        raise ValueError("洞察 MySQL 未配置：请设置 INSIGHTS_DB_NAME 或 insights.mysql.database")
    return InsightMySQLConfig(
        host=os.getenv("INSIGHTS_DB_HOST", str(configured.get("host") or "localhost")),
        port=int(os.getenv("INSIGHTS_DB_PORT", str(configured.get("port") or 3306))),
        user=os.getenv("INSIGHTS_DB_USER", str(configured.get("user") or configured.get("username") or "root")),
        password=os.getenv("INSIGHTS_DB_PASSWORD", os.getenv("MYSQL_PWD", str(configured.get("password") or ""))),
        database=database,
        charset=os.getenv("INSIGHTS_DB_CHARSET", str(configured.get("charset") or "utf8mb4")),
    )


def _db_time(value: datetime | None = None) -> datetime:
    value = value or datetime.now(timezone.utc)
    return value.astimezone(timezone.utc).replace(tzinfo=None) if value.tzinfo else value


def _api_row(row: dict[str, Any]) -> dict[str, Any]:
    result = dict(row)
    for key in ("metric_codes_json", "payload_json", "evidence_json", "common_grains_json", "common_dimensions_json", "common_tables_json", "result_json"):
        if key in result:
            try:
                result[key.removesuffix("_json")] = json.loads(result.pop(key) or "null")
            except (TypeError, json.JSONDecodeError):
                result[key.removesuffix("_json")] = None
    for key, value in list(result.items()):
        if isinstance(value, datetime):
            result[key] = value.replace(tzinfo=timezone.utc).isoformat()
    return result


class InsightStore:
    def __init__(
        self,
        config: InsightMySQLConfig | None = None,
        connection_factory: Callable[[], Any] | None = None,
    ) -> None:
        self.config = config
        self.connection_factory = connection_factory
        self._lock = threading.RLock()
        self._initialized = False

    def _connect(self):
        if self.connection_factory:
            return self.connection_factory()
        config = self.config or load_insight_mysql_config()
        self.config = config
        return pymysql.connect(
            host=config.host,
            port=config.port,
            user=config.user,
            password=config.password,
            database=config.database,
            charset=config.charset,
            autocommit=False,
            cursorclass=DictCursor,
        )

    @contextmanager
    def _connection(self) -> Iterator[Any]:
        connection = self._connect()
        try:
            yield connection
            connection.commit()
        except Exception:
            connection.rollback()
            raise
        finally:
            connection.close()

    def init(self) -> None:
        if self._initialized:
            return
        with self._lock:
            if self._initialized:
                return
            statements = (
                """
                CREATE TABLE IF NOT EXISTS insight_fact (
                    fact_id VARCHAR(100) PRIMARY KEY,
                    fact_type VARCHAR(80) NOT NULL,
                    metric_codes_json TEXT NOT NULL,
                    permission_scope_hash CHAR(64) NOT NULL,
                    metric_scope_hash CHAR(64) NOT NULL,
                    grain VARCHAR(20) NOT NULL,
                    window_start VARCHAR(40) NOT NULL,
                    window_end VARCHAR(40) NOT NULL,
                    status VARCHAR(40) NOT NULL,
                    impact_score DECIMAL(10,6) NOT NULL,
                    confidence DECIMAL(10,6) NOT NULL,
                    payload_json LONGTEXT NOT NULL,
                    evidence_json LONGTEXT NOT NULL,
                    model_version VARCHAR(80) NOT NULL,
                    dedupe_key CHAR(64) NOT NULL,
                    generated_at DATETIME(3) NOT NULL,
                    expires_at DATETIME(3) NULL,
                    UNIQUE KEY uk_insight_fact_dedupe (dedupe_key),
                    KEY idx_insight_fact_metric (fact_type,status,generated_at),
                    KEY idx_insight_fact_scope (permission_scope_hash,generated_at)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """,
                """
                CREATE TABLE IF NOT EXISTS forecast_run (
                    run_id VARCHAR(100) PRIMARY KEY,
                    metric_code VARCHAR(255) NOT NULL,
                    permission_scope_hash CHAR(64) NOT NULL,
                    status VARCHAR(40) NOT NULL,
                    model_name VARCHAR(80) NOT NULL,
                    model_version VARCHAR(80) NOT NULL,
                    horizon INT NOT NULL,
                    confidence DECIMAL(10,6) NOT NULL,
                    result_json LONGTEXT NOT NULL,
                    generated_at DATETIME(3) NOT NULL,
                    KEY idx_forecast_metric (metric_code,generated_at),
                    KEY idx_forecast_scope (permission_scope_hash,generated_at)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """,
                """
                CREATE TABLE IF NOT EXISTS metric_relation_candidate (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    metric_a VARCHAR(255) NOT NULL,
                    metric_b VARCHAR(255) NOT NULL,
                    relation_source VARCHAR(80) NOT NULL DEFAULT 'manual',
                    common_grains_json TEXT NOT NULL,
                    common_dimensions_json TEXT NOT NULL,
                    common_tables_json TEXT NOT NULL,
                    kg_path TEXT NOT NULL,
                    business_priority DECIMAL(8,6) NOT NULL DEFAULT 1.0,
                    enabled TINYINT NOT NULL DEFAULT 1,
                    created_at DATETIME(3) NOT NULL,
                    updated_at DATETIME(3) NOT NULL,
                    UNIQUE KEY uk_metric_relation_pair (metric_a,metric_b,relation_source)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """,
            )
            with self._connection() as connection:
                with connection.cursor() as cursor:
                    for statement in statements:
                        cursor.execute(statement)
                    cursor.execute("SHOW COLUMNS FROM insight_fact")
                    columns = {row["Field"] if isinstance(row, dict) else row[0] for row in cursor.fetchall()}
                    if "metric_scope_hash" not in columns:
                        cursor.execute(
                            "ALTER TABLE insight_fact ADD COLUMN metric_scope_hash CHAR(64) NOT NULL DEFAULT '' AFTER permission_scope_hash"
                        )
            self._initialized = True

    def save_fact(self, fact: InsightFact) -> str:
        self.init()
        payload = fact.to_dict()
        sql = """
            INSERT INTO insight_fact (
                fact_id,fact_type,metric_codes_json,permission_scope_hash,metric_scope_hash,grain,
                window_start,window_end,status,impact_score,confidence,payload_json,
                evidence_json,model_version,dedupe_key,generated_at,expires_at
            ) VALUES (%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s)
            ON DUPLICATE KEY UPDATE
                fact_id=VALUES(fact_id),status=VALUES(status),impact_score=VALUES(impact_score),
                confidence=VALUES(confidence),payload_json=VALUES(payload_json),
                evidence_json=VALUES(evidence_json),generated_at=VALUES(generated_at),
                expires_at=VALUES(expires_at)
        """
        with self._connection() as connection:
            with connection.cursor() as cursor:
                cursor.execute(sql, (
                    fact.fact_id, fact.fact_type, compact_json(fact.metric_codes),
                    fact.permission_scope_hash, fact.metric_scope_hash, fact.grain, fact.window_start, fact.window_end,
                    fact.status, fact.impact_score, fact.confidence, compact_json(payload["payload"]),
                    compact_json(payload["evidence"]), fact.model_version, fact.dedupe_key,
                    _db_time(fact.generated_at), _db_time(fact.expires_at) if fact.expires_at else None,
                ))
        return fact.fact_id

    def save_forecast(self, forecast: ForecastResult, permission_scope_hash: str) -> str:
        self.init()
        sql = """
            INSERT INTO forecast_run (
                run_id,metric_code,permission_scope_hash,status,model_name,model_version,
                horizon,confidence,result_json,generated_at
            ) VALUES (%s,%s,%s,%s,%s,%s,%s,%s,%s,%s)
            ON DUPLICATE KEY UPDATE status=VALUES(status),confidence=VALUES(confidence),
                result_json=VALUES(result_json),generated_at=VALUES(generated_at)
        """
        with self._connection() as connection:
            with connection.cursor() as cursor:
                cursor.execute(sql, (
                    forecast.run_id, forecast.metric_code, permission_scope_hash,
                    forecast.status, forecast.model_name, forecast.model_version,
                    forecast.horizon, forecast.confidence, compact_json(forecast.to_dict()),
                    _db_time(forecast.generated_at),
                ))
        return forecast.run_id

    def list_facts(
        self,
        *,
        permission_scope_hash: str,
        fact_type: str = "",
        metric_code: str = "",
        limit: int = 100,
    ) -> list[dict[str, Any]]:
        self.init()
        clauses = ["permission_scope_hash=%s"]
        params: list[Any] = [permission_scope_hash]
        if fact_type:
            clauses.append("fact_type=%s")
            params.append(fact_type)
        if metric_code:
            clauses.append("metric_codes_json LIKE %s")
            params.append(f'%"{metric_code}"%')
        params.append(max(1, min(int(limit), 500)))
        with self._connection() as connection:
            with connection.cursor() as cursor:
                cursor.execute(
                    f"SELECT * FROM insight_fact WHERE {' AND '.join(clauses)} ORDER BY impact_score DESC,generated_at DESC LIMIT %s",
                    params,
                )
                return [_api_row(row) for row in cursor.fetchall()]

    def get_fact(self, fact_id: str, permission_scope_hash: str) -> dict[str, Any] | None:
        self.init()
        with self._connection() as connection:
            with connection.cursor() as cursor:
                cursor.execute(
                    "SELECT * FROM insight_fact WHERE fact_id=%s AND permission_scope_hash=%s",
                    (fact_id, permission_scope_hash),
                )
                row = cursor.fetchone()
                return _api_row(row) if row else None

    def list_candidates(self, metric_codes: Iterable[str]) -> list[dict[str, Any]]:
        self.init()
        codes = sorted({str(code) for code in metric_codes if code})
        if len(codes) < 2:
            return []
        placeholders = ",".join(["%s"] * len(codes))
        with self._connection() as connection:
            with connection.cursor() as cursor:
                cursor.execute(
                    f"SELECT * FROM metric_relation_candidate WHERE enabled=1 AND metric_a IN ({placeholders}) AND metric_b IN ({placeholders})",
                    codes + codes,
                )
                rows = []
                for row in cursor.fetchall():
                    parsed = _api_row(row)
                    rows.append({
                        "metricA": parsed.get("metric_a"),
                        "metricB": parsed.get("metric_b"),
                        "relationSource": parsed.get("relation_source"),
                        "commonGrains": parsed.get("common_grains") or [],
                        "commonDimensions": parsed.get("common_dimensions") or [],
                        "commonTables": parsed.get("common_tables") or [],
                        "kgPath": parsed.get("kg_path") or "",
                        "businessPriority": float(parsed.get("business_priority") or 1.0),
                    })
                return rows

    def save_candidate(self, candidate: dict[str, Any]) -> dict[str, Any]:
        self.init()
        left, right = sorted([
            str(candidate.get("metricA") or "").strip(),
            str(candidate.get("metricB") or "").strip(),
        ])
        if not left or not right or left == right:
            raise ValueError("metricA 和 metricB 必须是两个不同指标")
        now = _db_time()
        sql = """
            INSERT INTO metric_relation_candidate (
                metric_a,metric_b,relation_source,common_grains_json,
                common_dimensions_json,common_tables_json,kg_path,
                business_priority,enabled,created_at,updated_at
            ) VALUES (%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s)
            ON DUPLICATE KEY UPDATE
                common_grains_json=VALUES(common_grains_json),
                common_dimensions_json=VALUES(common_dimensions_json),
                common_tables_json=VALUES(common_tables_json),
                kg_path=VALUES(kg_path),business_priority=VALUES(business_priority),
                enabled=VALUES(enabled),updated_at=VALUES(updated_at)
        """
        with self._connection() as connection:
            with connection.cursor() as cursor:
                cursor.execute(sql, (
                    left, right, str(candidate.get("relationSource") or "manual"),
                    compact_json(candidate.get("commonGrains") or []),
                    compact_json(candidate.get("commonDimensions") or []),
                    compact_json(candidate.get("commonTables") or []),
                    str(candidate.get("kgPath") or ""),
                    max(0.0, min(1.0, float(candidate.get("businessPriority") or 1.0))),
                    1 if candidate.get("enabled", True) else 0,
                    now, now,
                ))
        return {**candidate, "metricA": left, "metricB": right}


_store = InsightStore()


def get_insight_store() -> InsightStore:
    return _store


def init_insight_store() -> None:
    _store.init()
