"""MySQL persistence for feedback traces, events, and schema snapshots."""

from __future__ import annotations

import threading
import uuid
from contextlib import contextmanager
from datetime import datetime, timezone
from typing import Any, Callable, Iterator, Optional

import pymysql
from pymysql.cursors import DictCursor

from .mysql_config import FeedbackMySQLConfig, load_feedback_mysql_config
from .sanitizer import compact_json, json_loads


def utc_now() -> str:
    return datetime.now(timezone.utc).isoformat(timespec="milliseconds").replace("+00:00", "Z")


def _db_time(value: Any = None) -> datetime:
    if isinstance(value, datetime):
        parsed = value
    elif value:
        parsed = datetime.fromisoformat(str(value).replace("Z", "+00:00"))
    else:
        parsed = datetime.now(timezone.utc)
    if parsed.tzinfo is not None:
        parsed = parsed.astimezone(timezone.utc).replace(tzinfo=None)
    return parsed


def _api_time(value: Any) -> str:
    if not value:
        return ""
    if isinstance(value, datetime):
        return value.replace(tzinfo=timezone.utc).isoformat(timespec="milliseconds").replace(
            "+00:00", "Z"
        )
    text = str(value)
    return text if "T" in text else text.replace(" ", "T") + ("Z" if not text.endswith("Z") else "")


class FeedbackStore:
    """Small MySQL repository for durable feedback observation records."""

    def __init__(
        self,
        config: FeedbackMySQLConfig | None = None,
        connection_factory: Callable[[], Any] | None = None,
    ) -> None:
        self.config = config
        self._connection_factory = connection_factory
        self._lock = threading.RLock()
        self._initialized = False

    def _resolved_config(self) -> FeedbackMySQLConfig:
        if self.config is None:
            self.config = load_feedback_mysql_config()
        return self.config

    def _connect(self):
        if self._connection_factory is not None:
            return self._connection_factory()
        cfg = self._resolved_config()
        return pymysql.connect(
            host=cfg.host,
            port=cfg.port,
            user=cfg.user,
            password=cfg.password,
            database=cfg.database,
            charset=cfg.charset,
            connect_timeout=10,
            read_timeout=10,
            write_timeout=10,
            autocommit=False,
            cursorclass=DictCursor,
        )

    @contextmanager
    def _connection(self) -> Iterator[Any]:
        conn = self._connect()
        try:
            yield conn
            conn.commit()
        except Exception:
            conn.rollback()
            raise
        finally:
            conn.close()

    def init(self) -> None:
        if self._initialized:
            return
        with self._lock:
            if self._initialized:
                return
            statements = (
                """
                CREATE TABLE IF NOT EXISTS feedback_query_trace (
                    trace_id VARCHAR(100) PRIMARY KEY,
                    parent_trace_id VARCHAR(100) NOT NULL DEFAULT '',
                    conversation_id VARCHAR(100) NOT NULL DEFAULT '',
                    source VARCHAR(40) NOT NULL DEFAULT 'nlq',
                    question_text TEXT NOT NULL,
                    question_hash CHAR(64) NOT NULL DEFAULT '',
                    query_mode VARCHAR(40) NOT NULL DEFAULT '',
                    status VARCHAR(40) NOT NULL DEFAULT 'started',
                    diagnostic_code VARCHAR(100) NOT NULL DEFAULT '',
                    measure_codes_json TEXT NOT NULL,
                    dimension_codes_json TEXT NOT NULL,
                    fact_tables_json TEXT NOT NULL,
                    result_row_count BIGINT NULL,
                    elapsed_ms BIGINT NULL,
                    error_code VARCHAR(100) NOT NULL DEFAULT '',
                    error_message TEXT NOT NULL,
                    business_kg_hash CHAR(64) NOT NULL DEFAULT '',
                    business_kg_file VARCHAR(255) NOT NULL DEFAULT '',
                    ontology_version VARCHAR(100) NOT NULL DEFAULT '',
                    schema_snapshot_id VARCHAR(100) NOT NULL DEFAULT '',
                    created_at DATETIME(3) NOT NULL,
                    completed_at DATETIME(3) NULL,
                    KEY idx_feedback_trace_time (created_at)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """,
                """
                CREATE TABLE IF NOT EXISTS feedback_event (
                    event_id VARCHAR(100) PRIMARY KEY,
                    trace_id VARCHAR(100) NOT NULL DEFAULT '',
                    event_type VARCHAR(20) NOT NULL,
                    event_name VARCHAR(80) NOT NULL,
                    source VARCHAR(40) NOT NULL DEFAULT '',
                    payload_json LONGTEXT NOT NULL,
                    dedupe_key VARCHAR(191) NOT NULL,
                    created_at DATETIME(3) NOT NULL,
                    UNIQUE KEY uk_feedback_event_dedupe (dedupe_key),
                    KEY idx_feedback_event_time (created_at),
                    KEY idx_feedback_event_type (event_type,event_name),
                    KEY idx_feedback_event_trace (trace_id)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """,
                """
                CREATE TABLE IF NOT EXISTS feedback_schema_snapshot (
                    snapshot_id VARCHAR(100) PRIMARY KEY,
                    datasource_key CHAR(64) NOT NULL,
                    schema_hash CHAR(64) NOT NULL,
                    table_count INT NOT NULL DEFAULT 0,
                    column_count INT NOT NULL DEFAULT 0,
                    snapshot_json LONGTEXT NOT NULL,
                    created_at DATETIME(3) NOT NULL,
                    KEY idx_feedback_snapshot_source (datasource_key,created_at)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """,
                """
                CREATE TABLE IF NOT EXISTS semantic_query_plan (
                    plan_id VARCHAR(100) PRIMARY KEY,
                    trace_id VARCHAR(100) NOT NULL,
                    plan_version VARCHAR(20) NOT NULL DEFAULT '1.0',
                    source VARCHAR(40) NOT NULL DEFAULT '',
                    status VARCHAR(40) NOT NULL DEFAULT '',
                    plan_hash CHAR(64) NOT NULL,
                    plan_json LONGTEXT NOT NULL,
                    explain_json LONGTEXT NOT NULL,
                    business_kg_hash CHAR(64) NOT NULL DEFAULT '',
                    schema_snapshot_id VARCHAR(100) NOT NULL DEFAULT '',
                    ontology_version VARCHAR(100) NOT NULL DEFAULT '',
                    created_at DATETIME(3) NOT NULL,
                    updated_at DATETIME(3) NOT NULL,
                    UNIQUE KEY uk_semantic_plan_trace (trace_id),
                    KEY idx_semantic_plan_hash (plan_hash),
                    KEY idx_semantic_plan_time (created_at)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """,
                """
                CREATE TABLE IF NOT EXISTS semantic_correction_step (
                    step_id VARCHAR(100) PRIMARY KEY,
                    trace_id VARCHAR(100) NOT NULL,
                    plan_id VARCHAR(100) NOT NULL,
                    sequence_no INT NOT NULL DEFAULT 0,
                    corrector_code VARCHAR(100) NOT NULL,
                    status VARCHAR(40) NOT NULL DEFAULT 'observed',
                    reason_text TEXT NOT NULL,
                    evidence_json LONGTEXT NOT NULL,
                    before_hash CHAR(64) NOT NULL DEFAULT '',
                    after_hash CHAR(64) NOT NULL DEFAULT '',
                    patch_json LONGTEXT NOT NULL,
                    confidence DECIMAL(6,5) NULL,
                    elapsed_ms BIGINT NULL,
                    created_at DATETIME(3) NOT NULL,
                    UNIQUE KEY uk_semantic_correction_seq (trace_id,sequence_no),
                    KEY idx_semantic_correction_plan (plan_id)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """,
                """
                CREATE TABLE IF NOT EXISTS semantic_memory (
                    memory_id VARCHAR(100) PRIMARY KEY,
                    trace_id VARCHAR(100) NOT NULL,
                    plan_id VARCHAR(100) NOT NULL,
                    question_hash CHAR(64) NOT NULL DEFAULT '',
                    domain_code VARCHAR(100) NOT NULL DEFAULT 'default',
                    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
                    plan_hash CHAR(64) NOT NULL,
                    business_kg_hash CHAR(64) NOT NULL DEFAULT '',
                    schema_snapshot_id VARCHAR(100) NOT NULL DEFAULT '',
                    permission_scope_hash CHAR(64) NOT NULL DEFAULT '',
                    quality_score DECIMAL(8,5) NULL,
                    usage_count BIGINT NOT NULL DEFAULT 0,
                    created_at DATETIME(3) NOT NULL,
                    updated_at DATETIME(3) NOT NULL,
                    reviewed_at DATETIME(3) NULL,
                    UNIQUE KEY uk_semantic_memory_trace (trace_id),
                    KEY idx_semantic_memory_status (status,updated_at),
                    KEY idx_semantic_memory_compat (business_kg_hash,schema_snapshot_id)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """,
                """
                CREATE TABLE IF NOT EXISTS semantic_memory_review (
                    review_id VARCHAR(100) PRIMARY KEY,
                    memory_id VARCHAR(100) NOT NULL,
                    trace_id VARCHAR(100) NOT NULL,
                    action VARCHAR(20) NOT NULL,
                    reason_code VARCHAR(100) NOT NULL DEFAULT '',
                    comment_text TEXT NOT NULL,
                    actor VARCHAR(100) NOT NULL DEFAULT 'system',
                    payload_json LONGTEXT NOT NULL,
                    created_at DATETIME(3) NOT NULL,
                    KEY idx_semantic_review_memory (memory_id,created_at),
                    KEY idx_semantic_review_trace (trace_id)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """,
                """
                CREATE TABLE IF NOT EXISTS semantic_dictionary_entry (
                    entry_id VARCHAR(100) PRIMARY KEY,
                    semantic_type VARCHAR(20) NOT NULL,
                    term_text VARCHAR(500) NOT NULL,
                    normalized_term VARCHAR(191) NOT NULL,
                    canonical_code VARCHAR(191) NOT NULL DEFAULT '',
                    dimension_code VARCHAR(191) NOT NULL DEFAULT '',
                    canonical_value VARCHAR(500) NOT NULL DEFAULT '',
                    domain_code VARCHAR(100) NOT NULL DEFAULT 'default',
                    source VARCHAR(40) NOT NULL DEFAULT 'feedback',
                    source_trace_id VARCHAR(100) NOT NULL DEFAULT '',
                    source_review_id VARCHAR(100) NOT NULL DEFAULT '',
                    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
                    business_kg_hash CHAR(64) NOT NULL DEFAULT '',
                    schema_snapshot_id VARCHAR(100) NOT NULL DEFAULT '',
                    metadata_json LONGTEXT NOT NULL,
                    created_at DATETIME(3) NOT NULL,
                    updated_at DATETIME(3) NOT NULL,
                    reviewed_at DATETIME(3) NULL,
                    UNIQUE KEY uk_semantic_dictionary_target (
                        normalized_term,semantic_type,canonical_code,dimension_code,domain_code
                    ),
                    KEY idx_semantic_dictionary_status (status,updated_at),
                    KEY idx_semantic_dictionary_graph (business_kg_hash,status)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """,
                """
                CREATE TABLE IF NOT EXISTS semantic_eval_case (
                    case_id VARCHAR(100) PRIMARY KEY,
                    trace_id VARCHAR(100) NOT NULL DEFAULT '',
                    case_name VARCHAR(255) NOT NULL,
                    category VARCHAR(100) NOT NULL DEFAULT 'regression',
                    priority VARCHAR(20) NOT NULL DEFAULT 'P1',
                    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
                    input_json LONGTEXT NOT NULL,
                    expected_plan_json LONGTEXT NOT NULL,
                    expected_result_json LONGTEXT NOT NULL,
                    graph_hash CHAR(64) NOT NULL DEFAULT '',
                    created_at DATETIME(3) NOT NULL,
                    updated_at DATETIME(3) NOT NULL,
                    UNIQUE KEY uk_semantic_eval_trace_name (trace_id,case_name),
                    KEY idx_semantic_eval_category (category,priority,status)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """,
                """
                CREATE TABLE IF NOT EXISTS semantic_eval_run (
                    run_id VARCHAR(100) PRIMARY KEY,
                    suite_name VARCHAR(255) NOT NULL,
                    trigger_type VARCHAR(40) NOT NULL DEFAULT 'manual',
                    graph_hash CHAR(64) NOT NULL DEFAULT '',
                    status VARCHAR(20) NOT NULL DEFAULT 'RUNNING',
                    total_count INT NOT NULL DEFAULT 0,
                    passed_count INT NOT NULL DEFAULT 0,
                    failed_count INT NOT NULL DEFAULT 0,
                    drift_count INT NOT NULL DEFAULT 0,
                    infra_error_count INT NOT NULL DEFAULT 0,
                    started_at DATETIME(3) NOT NULL,
                    completed_at DATETIME(3) NULL,
                    KEY idx_semantic_eval_run_time (started_at)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """,
                """
                CREATE TABLE IF NOT EXISTS semantic_eval_result (
                    result_id VARCHAR(100) PRIMARY KEY,
                    run_id VARCHAR(100) NOT NULL,
                    case_id VARCHAR(100) NOT NULL,
                    trace_id VARCHAR(100) NOT NULL DEFAULT '',
                    outcome VARCHAR(40) NOT NULL,
                    actual_plan_json LONGTEXT NOT NULL,
                    diagnostics_json LONGTEXT NOT NULL,
                    elapsed_ms BIGINT NULL,
                    created_at DATETIME(3) NOT NULL,
                    UNIQUE KEY uk_semantic_eval_result (run_id,case_id),
                    KEY idx_semantic_eval_result_case (case_id,created_at)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """,
            )
            with self._connection() as conn, conn.cursor() as cursor:
                for statement in statements:
                    cursor.execute(statement)
            self._initialized = True

    def begin_trace(self, values: dict[str, Any]) -> None:
        self.init()
        with self._lock, self._connection() as conn, conn.cursor() as cursor:
            cursor.execute(
                """
                INSERT INTO feedback_query_trace (
                    trace_id,parent_trace_id,conversation_id,source,question_text,question_hash,
                    measure_codes_json,dimension_codes_json,fact_tables_json,error_message,
                    business_kg_hash,business_kg_file,ontology_version,created_at
                ) VALUES (%s,%s,%s,%s,%s,%s,'[]','[]','[]','',%s,%s,%s,%s)
                ON DUPLICATE KEY UPDATE trace_id=VALUES(trace_id)
                """,
                (
                    values["trace_id"], values.get("parent_trace_id", ""),
                    values.get("conversation_id", ""), values.get("source", "nlq"),
                    values.get("question_text", ""), values.get("question_hash", ""),
                    values.get("business_kg_hash", ""), values.get("business_kg_file", ""),
                    values.get("ontology_version", ""), _db_time(values.get("created_at")),
                ),
            )

    def complete_trace(self, trace_id: str, values: dict[str, Any]) -> None:
        self.init()
        self.begin_trace({"trace_id": trace_id, "created_at": utc_now()})
        with self._lock, self._connection() as conn, conn.cursor() as cursor:
            cursor.execute(
                """
                UPDATE feedback_query_trace SET
                    conversation_id=COALESCE(NULLIF(%s,''),conversation_id),
                    source=COALESCE(NULLIF(%s,''),source), query_mode=%s, status=%s,
                    diagnostic_code=%s, measure_codes_json=%s, dimension_codes_json=%s,
                    fact_tables_json=%s, result_row_count=%s, elapsed_ms=%s,
                    error_code=%s, error_message=%s, completed_at=%s
                WHERE trace_id=%s
                """,
                (
                    values.get("conversation_id", ""), values.get("source", ""),
                    values.get("query_mode", ""), values.get("status", "failed"),
                    values.get("diagnostic_code", ""), compact_json(values.get("measure_codes", [])),
                    compact_json(values.get("dimension_codes", [])), compact_json(values.get("fact_tables", [])),
                    values.get("result_row_count"), values.get("elapsed_ms"),
                    values.get("error_code", ""), values.get("error_message", ""),
                    _db_time(values.get("completed_at")), trace_id,
                ),
            )

    def add_event(self, values: dict[str, Any]) -> dict[str, Any]:
        self.init()
        event_id = str(values.get("event_id") or f"evt_{uuid.uuid4().hex}")[:100]
        created_at = _db_time(values.get("created_at"))
        dedupe_key = str(values.get("dedupe_key") or event_id)[:191]
        with self._lock, self._connection() as conn, conn.cursor() as cursor:
            cursor.execute(
                """
                INSERT INTO feedback_event (
                    event_id,trace_id,event_type,event_name,source,payload_json,dedupe_key,created_at
                ) VALUES (%s,%s,%s,%s,%s,%s,%s,%s)
                ON DUPLICATE KEY UPDATE event_id=event_id
                """,
                (
                    event_id, str(values.get("trace_id") or "")[:100],
                    str(values.get("event_type") or "")[:20], str(values.get("event_name") or "")[:80],
                    str(values.get("source") or "")[:40], compact_json(values.get("payload") or {}),
                    dedupe_key, created_at,
                ),
            )
            cursor.execute(
                "SELECT * FROM feedback_event WHERE event_id=%s OR dedupe_key=%s LIMIT 1",
                (event_id, dedupe_key),
            )
            row = cursor.fetchone()
        return self._event_row(row) if row else {"eventId": event_id}

    def list_events(
        self, *, event_type: str = "", event_name: str = "", error_code: str = "",
        search: str = "", limit: int = 50, offset: int = 0,
    ) -> dict[str, Any]:
        self.init()
        where = ["1=1"]
        params: list[Any] = []
        if event_type:
            where.append("e.event_type=%s")
            params.append(event_type)
        if event_name:
            where.append("e.event_name=%s")
            params.append(event_name)
        if error_code:
            where.append("t.error_code=%s")
            params.append(error_code)
        if search:
            where.append("(t.question_text LIKE %s OR t.trace_id LIKE %s OR t.measure_codes_json LIKE %s)")
            needle = f"%{search[:200]}%"
            params.extend([needle, needle, needle])
        where_sql = " AND ".join(where)
        query = f"""
            SELECT e.*,t.question_text,t.query_mode,t.status,t.diagnostic_code,t.error_code,
                   t.measure_codes_json,t.dimension_codes_json,t.fact_tables_json,t.elapsed_ms
            FROM feedback_event e
            LEFT JOIN feedback_query_trace t ON t.trace_id=e.trace_id
            WHERE {where_sql}
            ORDER BY e.created_at DESC LIMIT %s OFFSET %s
        """
        with self._connection() as conn, conn.cursor() as cursor:
            cursor.execute(
                f"SELECT COUNT(*) AS total FROM feedback_event e "
                f"LEFT JOIN feedback_query_trace t ON t.trace_id=e.trace_id WHERE {where_sql}",
                params,
            )
            total = int((cursor.fetchone() or {}).get("total") or 0)
            cursor.execute(query, [*params, int(limit), int(offset)])
            rows = cursor.fetchall()
        return {"items": [self._event_row(row) for row in rows], "total": total}

    def get_trace(self, trace_id: str) -> Optional[dict[str, Any]]:
        self.init()
        with self._connection() as conn, conn.cursor() as cursor:
            cursor.execute("SELECT * FROM feedback_query_trace WHERE trace_id=%s", (trace_id,))
            row = cursor.fetchone()
            cursor.execute(
                "SELECT * FROM feedback_event WHERE trace_id=%s ORDER BY created_at", (trace_id,)
            )
            events = cursor.fetchall()
            cursor.execute("SELECT * FROM semantic_query_plan WHERE trace_id=%s", (trace_id,))
            plan = cursor.fetchone()
            cursor.execute(
                "SELECT * FROM semantic_correction_step WHERE trace_id=%s ORDER BY sequence_no",
                (trace_id,),
            )
            corrections = cursor.fetchall()
            cursor.execute("SELECT * FROM semantic_memory WHERE trace_id=%s", (trace_id,))
            memory = cursor.fetchone()
            cursor.execute(
                "SELECT * FROM semantic_eval_case WHERE trace_id=%s ORDER BY created_at DESC",
                (trace_id,),
            )
            eval_cases = cursor.fetchall()
        if not row:
            return None
        result = dict(row)
        for key in ("measure_codes_json", "dimension_codes_json", "fact_tables_json"):
            result[key.removesuffix("_json")] = json_loads(result.pop(key, "[]"), [])
        result["events"] = [self._event_row(item) for item in events]
        result["semanticPlan"] = self._plan_row(plan) if plan else None
        result["corrections"] = [self._correction_row(item) for item in corrections]
        result["memory"] = self._memory_row(memory) if memory else None
        result["evalCases"] = [self._eval_case_row(item) for item in eval_cases]
        return self._camel_trace(result)

    def summary(self) -> dict[str, Any]:
        self.init()
        with self._connection() as conn, conn.cursor() as cursor:
            cursor.execute("SELECT status,COUNT(*) AS count FROM feedback_query_trace GROUP BY status")
            trace_counts = {row["status"]: int(row["count"]) for row in cursor.fetchall()}
            cursor.execute("SELECT event_type,COUNT(*) AS count FROM feedback_event GROUP BY event_type")
            type_counts = {row["event_type"]: int(row["count"]) for row in cursor.fetchall()}
            cursor.execute("SELECT COUNT(*) AS total FROM feedback_query_trace")
            total_traces = int((cursor.fetchone() or {}).get("total") or 0)
            cursor.execute("SELECT COUNT(*) AS total FROM feedback_event")
            total_events = int((cursor.fetchone() or {}).get("total") or 0)
            cursor.execute("SELECT COUNT(*) AS total FROM semantic_query_plan")
            total_plans = int((cursor.fetchone() or {}).get("total") or 0)
            cursor.execute("SELECT status,COUNT(*) AS count FROM semantic_memory GROUP BY status")
            memory_counts = {row["status"]: int(row["count"]) for row in cursor.fetchall()}
            cursor.execute("SELECT COUNT(*) AS total FROM semantic_correction_step")
            total_corrections = int((cursor.fetchone() or {}).get("total") or 0)
            cursor.execute("SELECT status,COUNT(*) AS count FROM semantic_dictionary_entry GROUP BY status")
            dictionary_counts = {row["status"]: int(row["count"]) for row in cursor.fetchall()}
            cursor.execute("SELECT COUNT(*) AS total FROM semantic_eval_case WHERE status='ACTIVE'")
            total_eval_cases = int((cursor.fetchone() or {}).get("total") or 0)
        return {
            "totalTraces": total_traces,
            "totalEvents": total_events,
            "traceCounts": trace_counts,
            "eventTypeCounts": type_counts,
            "negativeFeedback": self._count_event_names(("RESULT_UNHELPFUL", "RESULT_CORRECTION_SUBMITTED")),
            "schemaChanges": type_counts.get("data", 0),
            "semanticPlans": total_plans,
            "correctionSteps": total_corrections,
            "memoryCounts": memory_counts,
            "dictionaryCounts": dictionary_counts,
            "evalCases": total_eval_cases,
        }

    def _count_event_names(self, names: tuple[str, ...]) -> int:
        placeholders = ",".join("%s" for _ in names)
        with self._connection() as conn, conn.cursor() as cursor:
            cursor.execute(
                f"SELECT COUNT(*) AS total FROM feedback_event WHERE event_name IN ({placeholders})",
                names,
            )
            return int((cursor.fetchone() or {}).get("total") or 0)

    def latest_snapshot(self, datasource_key: str) -> Optional[dict[str, Any]]:
        self.init()
        with self._connection() as conn, conn.cursor() as cursor:
            cursor.execute(
                "SELECT * FROM feedback_schema_snapshot WHERE datasource_key=%s "
                "ORDER BY created_at DESC LIMIT 1",
                (datasource_key,),
            )
            row = cursor.fetchone()
        if not row:
            return None
        result = dict(row)
        result["snapshot"] = json_loads(result.pop("snapshot_json", "{}"), {})
        return result

    def save_snapshot(self, values: dict[str, Any]) -> str:
        self.init()
        snapshot_id = str(values.get("snapshot_id") or f"schema_{uuid.uuid4().hex}")[:100]
        with self._lock, self._connection() as conn, conn.cursor() as cursor:
            cursor.execute(
                """
                INSERT INTO feedback_schema_snapshot (
                    snapshot_id,datasource_key,schema_hash,table_count,column_count,snapshot_json,created_at
                ) VALUES (%s,%s,%s,%s,%s,%s,%s)
                """,
                (
                    snapshot_id, values["datasource_key"], values["schema_hash"],
                    values.get("table_count", 0), values.get("column_count", 0),
                    compact_json(values.get("snapshot") or {}, max_chars=10_000_000),
                    _db_time(values.get("created_at")),
                ),
            )
        return snapshot_id

    def save_semantic_plan(self, values: dict[str, Any]) -> str:
        self.init()
        plan_id = str(values.get("plan_id") or f"plan_{uuid.uuid4().hex}")[:100]
        now = _db_time(values.get("created_at"))
        plan = values.get("plan") or {}
        versions = plan.get("versions") if isinstance(plan, dict) else {}
        with self._lock, self._connection() as conn, conn.cursor() as cursor:
            cursor.execute(
                """
                INSERT INTO semantic_query_plan (
                    plan_id,trace_id,plan_version,source,status,plan_hash,plan_json,explain_json,
                    business_kg_hash,schema_snapshot_id,ontology_version,created_at,updated_at
                ) VALUES (%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s)
                ON DUPLICATE KEY UPDATE
                    plan_id=VALUES(plan_id),plan_version=VALUES(plan_version),source=VALUES(source),
                    status=VALUES(status),plan_hash=VALUES(plan_hash),plan_json=VALUES(plan_json),
                    explain_json=VALUES(explain_json),business_kg_hash=VALUES(business_kg_hash),
                    schema_snapshot_id=VALUES(schema_snapshot_id),ontology_version=VALUES(ontology_version),
                    updated_at=VALUES(updated_at)
                """,
                (
                    plan_id, str(values.get("trace_id") or "")[:100],
                    str(plan.get("version") or "1.0")[:20], str(values.get("source") or "")[:40],
                    str(values.get("status") or "")[:40], str(values.get("plan_hash") or "")[:64],
                    compact_json(plan, max_chars=2_000_000),
                    compact_json(values.get("explain_plan") or {}, max_chars=2_000_000),
                    str((versions or {}).get("businessKgHash") or "")[:64],
                    str((versions or {}).get("schemaSnapshotId") or "")[:100],
                    str((versions or {}).get("ontologyVersion") or "")[:100], now, now,
                ),
            )
        return plan_id

    def replace_correction_steps(
        self, trace_id: str, plan_id: str, steps: list[dict[str, Any]]
    ) -> None:
        self.init()
        with self._lock, self._connection() as conn, conn.cursor() as cursor:
            cursor.execute("DELETE FROM semantic_correction_step WHERE trace_id=%s", (trace_id,))
            for index, step in enumerate(steps[:20], 1):
                sequence_no = int(step.get("sequenceNo") or index)
                cursor.execute(
                    """
                    INSERT INTO semantic_correction_step (
                        step_id,trace_id,plan_id,sequence_no,corrector_code,status,reason_text,
                        evidence_json,before_hash,after_hash,patch_json,confidence,elapsed_ms,created_at
                    ) VALUES (%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s)
                    """,
                    (
                        f"step_{uuid.uuid4().hex}", trace_id[:100], plan_id[:100], sequence_no,
                        str(step.get("correctorCode") or "UNKNOWN")[:100],
                        str(step.get("status") or "observed")[:40],
                        str(step.get("reason") or "")[:4000],
                        compact_json(step.get("evidence") or {}),
                        str(step.get("beforeHash") or "")[:64],
                        str(step.get("afterHash") or "")[:64],
                        compact_json(step.get("patch") or {}), step.get("confidence"),
                        step.get("elapsedMs"), _db_time(step.get("createdAt")),
                    ),
                )

    def create_pending_memory(self, values: dict[str, Any]) -> str:
        self.init()
        memory_id = str(values.get("memory_id") or f"memory_{uuid.uuid4().hex}")[:100]
        now = _db_time(values.get("created_at"))
        with self._lock, self._connection() as conn, conn.cursor() as cursor:
            cursor.execute(
                """
                INSERT INTO semantic_memory (
                    memory_id,trace_id,plan_id,question_hash,domain_code,status,plan_hash,
                    business_kg_hash,schema_snapshot_id,permission_scope_hash,quality_score,
                    usage_count,created_at,updated_at
                ) VALUES (%s,%s,%s,%s,%s,'PENDING',%s,%s,%s,%s,%s,0,%s,%s)
                ON DUPLICATE KEY UPDATE
                    plan_id=VALUES(plan_id),plan_hash=VALUES(plan_hash),
                    business_kg_hash=VALUES(business_kg_hash),
                    schema_snapshot_id=VALUES(schema_snapshot_id),updated_at=VALUES(updated_at)
                """,
                (
                    memory_id, str(values.get("trace_id") or "")[:100],
                    str(values.get("plan_id") or "")[:100],
                    str(values.get("question_hash") or "")[:64],
                    str(values.get("domain_code") or "default")[:100],
                    str(values.get("plan_hash") or "")[:64],
                    str(values.get("business_kg_hash") or "")[:64],
                    str(values.get("schema_snapshot_id") or "")[:100],
                    str(values.get("permission_scope_hash") or "")[:64],
                    values.get("quality_score"), now, now,
                ),
            )
            cursor.execute("SELECT memory_id FROM semantic_memory WHERE trace_id=%s", (values.get("trace_id"),))
            row = cursor.fetchone() or {}
        return str(row.get("memory_id") or memory_id)

    def list_memories(
        self, *, status: str = "", search: str = "", limit: int = 50, offset: int = 0,
    ) -> dict[str, Any]:
        self.init()
        where = ["1=1"]
        params: list[Any] = []
        if status:
            where.append("m.status=%s")
            params.append(status.upper())
        if search:
            needle = f"%{search[:200]}%"
            where.append("(t.question_text LIKE %s OR m.trace_id LIKE %s OR p.plan_json LIKE %s)")
            params.extend([needle, needle, needle])
        where_sql = " AND ".join(where)
        base = (
            " FROM semantic_memory m "
            "LEFT JOIN feedback_query_trace t ON t.trace_id=m.trace_id "
            "LEFT JOIN semantic_query_plan p ON p.plan_id=m.plan_id "
            f"WHERE {where_sql}"
        )
        with self._connection() as conn, conn.cursor() as cursor:
            cursor.execute(f"SELECT COUNT(*) AS total{base}", params)
            total = int((cursor.fetchone() or {}).get("total") or 0)
            cursor.execute(
                "SELECT m.*,t.question_text,t.query_mode,t.source,p.plan_json,p.explain_json"
                + base + " ORDER BY m.updated_at DESC LIMIT %s OFFSET %s",
                [*params, int(limit), int(offset)],
            )
            rows = cursor.fetchall()
        return {"items": [self._memory_row(row) for row in rows], "total": total}

    def review_memory(
        self, memory_id: str, *, action: str, reason_code: str = "", comment: str = "",
        actor: str = "reviewer", payload: Optional[dict[str, Any]] = None,
    ) -> Optional[dict[str, Any]]:
        self.init()
        status = str(action or "").upper()
        if status not in {"PENDING", "ENABLED", "DISABLED", "STALE"}:
            raise ValueError("样本状态仅支持 PENDING/ENABLED/DISABLED/STALE")
        now = _db_time()
        with self._lock, self._connection() as conn, conn.cursor() as cursor:
            cursor.execute("SELECT * FROM semantic_memory WHERE memory_id=%s", (memory_id,))
            row = cursor.fetchone()
            if not row:
                return None
            cursor.execute(
                "UPDATE semantic_memory SET status=%s,updated_at=%s,reviewed_at=%s WHERE memory_id=%s",
                (status, now, now, memory_id),
            )
            cursor.execute(
                """
                INSERT INTO semantic_memory_review (
                    review_id,memory_id,trace_id,action,reason_code,comment_text,actor,payload_json,created_at
                ) VALUES (%s,%s,%s,%s,%s,%s,%s,%s,%s)
                """,
                (
                    f"review_{uuid.uuid4().hex}", memory_id,
                    str(row.get("trace_id") or "")[:100], status,
                    str(reason_code or "")[:100], str(comment or "")[:4000],
                    str(actor or "reviewer")[:100], compact_json(payload or {}), now,
                ),
            )
            cursor.execute("SELECT * FROM semantic_memory WHERE memory_id=%s", (memory_id,))
            updated = cursor.fetchone()
        return self._memory_row(updated) if updated else None

    def review_memory_by_trace(
        self, trace_id: str, *, action: str, reason_code: str = "", comment: str = "",
        actor: str = "system", payload: Optional[dict[str, Any]] = None,
    ) -> Optional[dict[str, Any]]:
        self.init()
        with self._connection() as conn, conn.cursor() as cursor:
            cursor.execute("SELECT memory_id FROM semantic_memory WHERE trace_id=%s", (trace_id,))
            row = cursor.fetchone()
        if not row:
            return None
        return self.review_memory(
            str(row["memory_id"]), action=action, reason_code=reason_code,
            comment=comment, actor=actor, payload=payload,
        )

    def create_dictionary_proposal(self, values: dict[str, Any]) -> dict[str, Any]:
        """Create a typed PENDING alias/value proposal; never auto-enable it."""
        self.init()
        semantic_type = str(values.get("semantic_type") or "").strip().lower()
        if semantic_type not in {"measure", "dimension", "value"}:
            raise ValueError("语义字典类型仅支持 measure/dimension/value")
        term_text = str(values.get("term_text") or "").strip()
        normalized_term = str(values.get("normalized_term") or "").strip()
        canonical_code = str(values.get("canonical_code") or "").strip()
        dimension_code = str(values.get("dimension_code") or "").strip()
        canonical_value = str(values.get("canonical_value") or "").strip()
        if not term_text or not normalized_term:
            raise ValueError("字典提案 term 不能为空")
        if semantic_type in {"measure", "dimension"} and not canonical_code:
            raise ValueError("指标/维度别名必须提供 canonicalCode")
        if semantic_type == "value" and (not dimension_code or not canonical_value):
            raise ValueError("维值别名必须提供 dimensionCode 和 canonicalValue")
        entry_id = str(values.get("entry_id") or f"dict_{uuid.uuid4().hex}")[:100]
        now = _db_time(values.get("created_at"))
        with self._lock, self._connection() as conn, conn.cursor() as cursor:
            cursor.execute(
                """
                INSERT INTO semantic_dictionary_entry (
                    entry_id,semantic_type,term_text,normalized_term,canonical_code,
                    dimension_code,canonical_value,domain_code,source,source_trace_id,
                    source_review_id,status,business_kg_hash,schema_snapshot_id,
                    metadata_json,created_at,updated_at
                ) VALUES (%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,'PENDING',%s,%s,%s,%s,%s)
                ON DUPLICATE KEY UPDATE
                    term_text=VALUES(term_text),source=VALUES(source),
                    source_trace_id=VALUES(source_trace_id),source_review_id=VALUES(source_review_id),
                    business_kg_hash=VALUES(business_kg_hash),
                    schema_snapshot_id=VALUES(schema_snapshot_id),metadata_json=VALUES(metadata_json),
                    status='PENDING',reviewed_at=NULL,updated_at=VALUES(updated_at)
                """,
                (
                    entry_id, semantic_type, term_text[:500], normalized_term[:191],
                    canonical_code[:191], dimension_code[:191], canonical_value[:500],
                    str(values.get("domain_code") or "default")[:100],
                    str(values.get("source") or "feedback")[:40],
                    str(values.get("source_trace_id") or "")[:100],
                    str(values.get("source_review_id") or "")[:100],
                    str(values.get("business_kg_hash") or "")[:64],
                    str(values.get("schema_snapshot_id") or "")[:100],
                    compact_json(values.get("metadata") or {}), now, now,
                ),
            )
            cursor.execute(
                """
                SELECT * FROM semantic_dictionary_entry
                WHERE normalized_term=%s AND semantic_type=%s AND canonical_code=%s
                  AND dimension_code=%s AND domain_code=%s LIMIT 1
                """,
                (
                    normalized_term[:191], semantic_type, canonical_code[:191],
                    dimension_code[:191], str(values.get("domain_code") or "default")[:100],
                ),
            )
            row = cursor.fetchone()
        return self._dictionary_row(row) if row else {"entryId": entry_id, "status": "PENDING"}

    def get_dictionary_entry(self, entry_id: str) -> Optional[dict[str, Any]]:
        self.init()
        with self._connection() as conn, conn.cursor() as cursor:
            cursor.execute(
                "SELECT * FROM semantic_dictionary_entry WHERE entry_id=%s",
                (entry_id,),
            )
            row = cursor.fetchone()
        return self._dictionary_row(row) if row else None

    def list_dictionary_entries(
        self, *, status: str = "", semantic_type: str = "", business_kg_hash: str = "",
        search: str = "", limit: int = 50, offset: int = 0,
    ) -> dict[str, Any]:
        self.init()
        where = ["1=1"]
        params: list[Any] = []
        if status:
            where.append("status=%s")
            params.append(status.upper())
        if semantic_type:
            where.append("semantic_type=%s")
            params.append(semantic_type.lower())
        if business_kg_hash:
            where.append("business_kg_hash=%s")
            params.append(business_kg_hash[:64])
        if search:
            where.append("(term_text LIKE %s OR canonical_code LIKE %s OR dimension_code LIKE %s)")
            needle = f"%{search[:200]}%"
            params.extend([needle, needle, needle])
        where_sql = " AND ".join(where)
        with self._connection() as conn, conn.cursor() as cursor:
            cursor.execute(
                f"SELECT COUNT(*) AS total FROM semantic_dictionary_entry WHERE {where_sql}",
                params,
            )
            total = int((cursor.fetchone() or {}).get("total") or 0)
            cursor.execute(
                f"SELECT * FROM semantic_dictionary_entry WHERE {where_sql} "
                "ORDER BY updated_at DESC LIMIT %s OFFSET %s",
                [*params, int(limit), int(offset)],
            )
            rows = cursor.fetchall()
        return {"items": [self._dictionary_row(row) for row in rows], "total": total}

    def review_dictionary_entry(
        self, entry_id: str, *, action: str, actor: str = "reviewer",
        comment: str = "",
    ) -> Optional[dict[str, Any]]:
        self.init()
        status = str(action or "").upper()
        if status not in {"PENDING", "ENABLED", "DISABLED", "STALE"}:
            raise ValueError("字典状态仅支持 PENDING/ENABLED/DISABLED/STALE")
        now = _db_time()
        with self._lock, self._connection() as conn, conn.cursor() as cursor:
            cursor.execute(
                "SELECT * FROM semantic_dictionary_entry WHERE entry_id=%s",
                (entry_id,),
            )
            row = cursor.fetchone()
            if not row:
                return None
            metadata = json_loads(row.get("metadata_json"), {})
            metadata["lastReview"] = {
                "actor": str(actor or "reviewer")[:100],
                "comment": str(comment or "")[:1000],
                "action": status,
            }
            cursor.execute(
                """
                UPDATE semantic_dictionary_entry SET status=%s,metadata_json=%s,
                    updated_at=%s,reviewed_at=%s WHERE entry_id=%s
                """,
                (status, compact_json(metadata), now, now, entry_id),
            )
            cursor.execute(
                "SELECT * FROM semantic_dictionary_entry WHERE entry_id=%s",
                (entry_id,),
            )
            updated = cursor.fetchone()
        return self._dictionary_row(updated) if updated else None

    def create_eval_case_from_trace(
        self, trace_id: str, *, case_name: str = "", category: str = "regression",
        priority: str = "P1",
    ) -> Optional[dict[str, Any]]:
        self.init()
        with self._lock, self._connection() as conn, conn.cursor() as cursor:
            cursor.execute(
                """
                SELECT t.*,p.plan_json,p.plan_hash,p.business_kg_hash
                FROM feedback_query_trace t
                JOIN semantic_query_plan p ON p.trace_id=t.trace_id
                WHERE t.trace_id=%s
                """,
                (trace_id,),
            )
            row = cursor.fetchone()
            if not row:
                return None
            name = str(case_name or row.get("question_text") or trace_id)[:255]
            case_id = f"case_{uuid.uuid4().hex}"
            now = _db_time()
            input_payload = {
                "question": row.get("question_text") or "",
                "queryMode": row.get("query_mode") or "",
                "traceId": trace_id,
            }
            expected_result = {
                "status": row.get("status") or "",
                "diagnosticCode": row.get("diagnostic_code") or "",
                "rowCount": row.get("result_row_count"),
            }
            cursor.execute(
                """
                INSERT INTO semantic_eval_case (
                    case_id,trace_id,case_name,category,priority,status,input_json,
                    expected_plan_json,expected_result_json,graph_hash,created_at,updated_at
                ) VALUES (%s,%s,%s,%s,%s,'ACTIVE',%s,%s,%s,%s,%s,%s)
                ON DUPLICATE KEY UPDATE
                    category=VALUES(category),priority=VALUES(priority),status='ACTIVE',
                    input_json=VALUES(input_json),expected_plan_json=VALUES(expected_plan_json),
                    expected_result_json=VALUES(expected_result_json),graph_hash=VALUES(graph_hash),
                    updated_at=VALUES(updated_at)
                """,
                (
                    case_id, trace_id, name, category[:100], priority[:20],
                    compact_json(input_payload), row.get("plan_json") or "{}",
                    compact_json(expected_result), str(row.get("business_kg_hash") or "")[:64],
                    now, now,
                ),
            )
            cursor.execute(
                "SELECT * FROM semantic_eval_case WHERE trace_id=%s AND case_name=%s",
                (trace_id, name),
            )
            created = cursor.fetchone()
        return self._eval_case_row(created) if created else None

    def list_eval_cases(
        self, *, category: str = "", priority: str = "", limit: int = 50, offset: int = 0,
    ) -> dict[str, Any]:
        self.init()
        where = ["status='ACTIVE'"]
        params: list[Any] = []
        if category:
            where.append("category=%s")
            params.append(category)
        if priority:
            where.append("priority=%s")
            params.append(priority.upper())
        where_sql = " AND ".join(where)
        with self._connection() as conn, conn.cursor() as cursor:
            cursor.execute(f"SELECT COUNT(*) AS total FROM semantic_eval_case WHERE {where_sql}", params)
            total = int((cursor.fetchone() or {}).get("total") or 0)
            cursor.execute(
                f"SELECT * FROM semantic_eval_case WHERE {where_sql} "
                "ORDER BY FIELD(priority,'P0','P1','P2','P3'),updated_at DESC LIMIT %s OFFSET %s",
                [*params, int(limit), int(offset)],
            )
            rows = cursor.fetchall()
        return {"items": [self._eval_case_row(row) for row in rows], "total": total}

    @staticmethod
    def _plan_row(row: dict[str, Any]) -> dict[str, Any]:
        data = dict(row)
        return {
            "planId": data.get("plan_id") or "",
            "traceId": data.get("trace_id") or "",
            "version": data.get("plan_version") or "",
            "source": data.get("source") or "",
            "status": data.get("status") or "",
            "planHash": data.get("plan_hash") or "",
            "plan": json_loads(data.get("plan_json"), {}),
            "explainPlan": json_loads(data.get("explain_json"), {}),
            "createdAt": _api_time(data.get("created_at")),
            "updatedAt": _api_time(data.get("updated_at")),
        }

    @staticmethod
    def _correction_row(row: dict[str, Any]) -> dict[str, Any]:
        data = dict(row)
        return {
            "stepId": data.get("step_id") or "",
            "sequenceNo": int(data.get("sequence_no") or 0),
            "correctorCode": data.get("corrector_code") or "",
            "status": data.get("status") or "",
            "reason": data.get("reason_text") or "",
            "evidence": json_loads(data.get("evidence_json"), {}),
            "beforeHash": data.get("before_hash") or "",
            "afterHash": data.get("after_hash") or "",
            "patch": json_loads(data.get("patch_json"), {}),
            "confidence": float(data["confidence"]) if data.get("confidence") is not None else None,
            "elapsedMs": data.get("elapsed_ms"),
            "createdAt": _api_time(data.get("created_at")),
        }

    @staticmethod
    def _memory_row(row: dict[str, Any]) -> dict[str, Any]:
        data = dict(row)
        result = {
            "memoryId": data.get("memory_id") or "",
            "traceId": data.get("trace_id") or "",
            "planId": data.get("plan_id") or "",
            "status": data.get("status") or "",
            "domainCode": data.get("domain_code") or "",
            "planHash": data.get("plan_hash") or "",
            "businessKgHash": data.get("business_kg_hash") or "",
            "schemaSnapshotId": data.get("schema_snapshot_id") or "",
            "qualityScore": float(data["quality_score"]) if data.get("quality_score") is not None else None,
            "usageCount": int(data.get("usage_count") or 0),
            "createdAt": _api_time(data.get("created_at")),
            "updatedAt": _api_time(data.get("updated_at")),
            "reviewedAt": _api_time(data.get("reviewed_at")),
        }
        if "question_text" in data:
            result.update({
                "question": data.get("question_text") or "",
                "queryMode": data.get("query_mode") or "",
                "source": data.get("source") or "",
                "plan": json_loads(data.get("plan_json"), {}),
                "explainPlan": json_loads(data.get("explain_json"), {}),
            })
        return result

    @staticmethod
    def _dictionary_row(row: dict[str, Any]) -> dict[str, Any]:
        data = dict(row)
        return {
            "entryId": data.get("entry_id") or "",
            "semanticType": data.get("semantic_type") or "",
            "term": data.get("term_text") or "",
            "normalizedTerm": data.get("normalized_term") or "",
            "canonicalCode": data.get("canonical_code") or "",
            "dimensionCode": data.get("dimension_code") or "",
            "canonicalValue": data.get("canonical_value") or "",
            "domainCode": data.get("domain_code") or "default",
            "source": data.get("source") or "",
            "sourceTraceId": data.get("source_trace_id") or "",
            "sourceReviewId": data.get("source_review_id") or "",
            "status": data.get("status") or "",
            "businessKgHash": data.get("business_kg_hash") or "",
            "schemaSnapshotId": data.get("schema_snapshot_id") or "",
            "metadata": json_loads(data.get("metadata_json"), {}),
            "createdAt": _api_time(data.get("created_at")),
            "updatedAt": _api_time(data.get("updated_at")),
            "reviewedAt": _api_time(data.get("reviewed_at")),
        }

    @staticmethod
    def _eval_case_row(row: dict[str, Any]) -> dict[str, Any]:
        data = dict(row)
        return {
            "caseId": data.get("case_id") or "",
            "traceId": data.get("trace_id") or "",
            "name": data.get("case_name") or "",
            "category": data.get("category") or "",
            "priority": data.get("priority") or "",
            "status": data.get("status") or "",
            "input": json_loads(data.get("input_json"), {}),
            "expectedPlan": json_loads(data.get("expected_plan_json"), {}),
            "expectedResult": json_loads(data.get("expected_result_json"), {}),
            "graphHash": data.get("graph_hash") or "",
            "createdAt": _api_time(data.get("created_at")),
            "updatedAt": _api_time(data.get("updated_at")),
        }

    @staticmethod
    def _event_row(row: dict[str, Any]) -> dict[str, Any]:
        data = dict(row)
        payload = json_loads(data.pop("payload_json", "{}"), {})
        result = {
            "eventId": data.pop("event_id", ""), "traceId": data.pop("trace_id", ""),
            "eventType": data.pop("event_type", ""), "eventName": data.pop("event_name", ""),
            "source": data.pop("source", ""), "createdAt": _api_time(data.pop("created_at", "")),
            "payload": payload,
            "status": payload.get("status") or "",
            "errorCode": payload.get("errorCode") or "",
            "elapsedMs": payload.get("elapsedMs"),
        }
        if data.get("question_text") is not None:
            result.update({
                "question": data.get("question_text") or "", "queryMode": data.get("query_mode") or "",
                "status": data.get("status") or "", "diagnosticCode": data.get("diagnostic_code") or "",
                "errorCode": data.get("error_code") or "", "elapsedMs": data.get("elapsed_ms"),
                "measureCodes": json_loads(data.get("measure_codes_json"), []),
                "dimensionCodes": json_loads(data.get("dimension_codes_json"), []),
                "factTables": json_loads(data.get("fact_tables_json"), []),
            })
        return result

    @staticmethod
    def _camel_trace(data: dict[str, Any]) -> dict[str, Any]:
        mapping = {
            "trace_id": "traceId", "parent_trace_id": "parentTraceId",
            "conversation_id": "conversationId", "question_text": "question",
            "question_hash": "questionHash", "query_mode": "queryMode",
            "diagnostic_code": "diagnosticCode", "measure_codes": "measureCodes",
            "dimension_codes": "dimensionCodes", "fact_tables": "factTables",
            "result_row_count": "resultRowCount", "elapsed_ms": "elapsedMs",
            "error_code": "errorCode", "error_message": "errorMessage",
            "business_kg_hash": "businessKgHash", "business_kg_file": "businessKgFile",
            "ontology_version": "ontologyVersion", "schema_snapshot_id": "schemaSnapshotId",
            "created_at": "createdAt", "completed_at": "completedAt",
        }
        result = {mapping.get(key, key): value for key, value in data.items()}
        result["createdAt"] = _api_time(result.get("createdAt"))
        result["completedAt"] = _api_time(result.get("completedAt"))
        return result


store = FeedbackStore()
