"""Rule CRUD and builtin configuration helpers."""

from __future__ import annotations

import json
from dataclasses import dataclass, asdict, field
from datetime import datetime, timezone
from typing import Any

from .models import get_db


# ── Builtin rule types ──
BUILTIN_RULE_TYPES = ["self", "path", "expression"]

# ── Builtin rule defaults ────────────────────────────────────────────────── #

BUILTIN_DEFAULTS: dict[str, dict[str, Any]] = {
    "self.zscore_critical": {
        "param_key": "self.zscore_critical",
        "param_value": 5.0,
        "description": "robust z-score 严重阈值 (>= 此值触发 critical)",
    },
    "self.zscore_warning": {
        "param_key": "self.zscore_warning",
        "param_value": 3.5,
        "description": "robust z-score 异常阈值 (>= 此值触发 warning)",
    },
    "self.iqr_multiplier": {
        "param_key": "self.iqr_multiplier",
        "param_value": 3.0,
        "description": "IQR 极值倍数 (超出 Q1/N * IQR 或 Q3 + N * IQR 触发)",
    },
    "self.drop_ratio": {
        "param_key": "self.drop_ratio",
        "param_value": -0.9,
        "description": "环比下降比例阈值 (≤ 此值触发 critical，例 -0.9 即下降 90%)",
    },
    "self.surge_ratio": {
        "param_key": "self.surge_ratio",
        "param_value": 3.0,
        "description": "环比暴涨比例阈值 (>= 此值触发 critical，例 3.0 即增长 300%)",
    },
    "self.fluctuation_ratio": {
        "param_key": "self.fluctuation_ratio",
        "param_value": 1.5,
        "description": "环比波动比例阈值 (绝对值 >= 此值触发 warning，例 1.5 即 150%)",
    },
    "self.near_zero_ratio": {
        "param_key": "self.near_zero_ratio",
        "param_value": 0.01,
        "description": "近零检测比例 (值 <= 基线中位数 * 此比例视为近零)",
    },
    "path.iqr_multiplier": {
        "param_key": "path.iqr_multiplier",
        "param_value": 2.5,
        "description": "路径异常 IQR 极值倍数",
    },
    "path.near_zero_ratio": {
        "param_key": "path.near_zero_ratio",
        "param_value": 0.02,
        "description": "路径异常跌零比例 (值 <= 中位数 * 此比例视为跌零)",
    },
    "path.surge_ratio": {
        "param_key": "path.surge_ratio",
        "param_value": 5.0,
        "description": "路径异常超高倍率 (值 >= 中位数 * 此倍数视为暴涨)",
    },
    "expression.enabled": {
        "param_key": "expression.enabled",
        "param_value": True,
        "description": "表达式内部异常检测开关 (true/false)",
    },
}


def get_builtin_config(db=None) -> dict[str, dict[str, Any]]:
    """Return merged builtin config: defaults overridden by DB values."""
    db = db or get_db()
    merged = {k: dict(v) for k, v in BUILTIN_DEFAULTS.items()}
    try:
        rows = db.execute("SELECT param_key, param_value FROM alert_builtin_config").fetchall()
    except Exception:
        return merged
    for key, raw in rows:
        if key in merged:
            try:
                merged[key]["param_value"] = json.loads(raw)
            except Exception:
                merged[key]["param_value"] = raw
    return merged


def update_builtin_config(key: str, value: Any, db=None) -> None:
    """Upsert a single builtin config entry."""
    db = db or get_db()
    raw = json.dumps(value)
    db.execute(
        """INSERT INTO alert_builtin_config (param_key, param_value, updated_at)
           VALUES (?, ?, ?)
           ON CONFLICT(param_key) DO UPDATE SET param_value=excluded.param_value,
           updated_at=excluded.updated_at""",
        (key, raw, datetime.now(timezone.utc).isoformat()),
    )
    db.commit()


def reset_builtin_config(db=None) -> None:
    """Reset all builtin config to defaults (delete overrides)."""
    db = db or get_db()
    db.execute("DELETE FROM alert_builtin_config")
    db.commit()


# ── Rule CRUD ────────────────────────────────────────────────────────────── #

@dataclass
class AlertRule:
    name: str
    rule_type: str  # "builtin" | "custom"
    measure_code: str
    operator: str = "always"
    threshold: float | None = None
    threshold2: float | None = None
    dimensions_json: str = "{}"
    severity_level: int = 2
    enabled: bool = True
    cooldown_minutes: int = 30
    builtin_type: str | None = None  # "self" | "path" | "expression" (for builtin)
    description: str = ""
    assignee_ids: str = ""  # comma-separated user ids
    notify_channels: str = "feishu"  # comma-separated
    id: int | None = None
    created_at: str = ""
    updated_at: str = ""

    def to_row(self) -> dict[str, Any]:
        data = asdict(self)
        data.pop("id", None)
        return data


def list_rules(
    db=None,
    rule_type: str | None = None,
    enabled: bool | None = None,
    search: str = "",
    offset: int = 0,
    limit: int = 50,
) -> tuple[list[dict[str, Any]], int]:
    db = db or get_db()
    wheres = []
    params: list[Any] = []
    if rule_type:
        wheres.append("rule_type = ?")
        params.append(rule_type)
    if enabled is not None:
        wheres.append("enabled = ?")
        params.append(1 if enabled else 0)
    if search:
        wheres.append("(name LIKE ? OR measure_code LIKE ? OR description LIKE ?)")
        like = f"%{search}%"
        params.extend([like, like, like])
    where_clause = ("WHERE " + " AND ".join(wheres)) if wheres else ""
    count = db.execute(
        f"SELECT COUNT(*) FROM alert_rule {where_clause}", params
    ).fetchone()[0]
    rows = db.execute(
        f"""SELECT * FROM alert_rule {where_clause}
            ORDER BY created_at DESC LIMIT ? OFFSET ?""",
        params + [limit, offset],
    ).fetchall()
    return [_row_to_dict(row) for row in rows], count


def get_rule(rule_id: int, db=None) -> dict[str, Any] | None:
    db = db or get_db()
    row = db.execute("SELECT * FROM alert_rule WHERE id = ?", (rule_id,)).fetchone()
    return _row_to_dict(row) if row else None


def create_rule(rule: AlertRule, db=None) -> dict[str, Any]:
    db = db or get_db()
    now = datetime.now(timezone.utc).isoformat()
    rule.created_at = now
    rule.updated_at = now
    data = rule.to_row()
    columns = ", ".join(data.keys())
    placeholders = ", ".join(["?" for _ in data])
    cursor = db.execute(
        f"INSERT INTO alert_rule ({columns}) VALUES ({placeholders})",
        list(data.values()),
    )
    db.commit()
    return get_rule(cursor.lastrowid, db)  # type: ignore[return-value]


def update_rule(rule_id: int, updates: dict[str, Any], db=None) -> dict[str, Any] | None:
    db = db or get_db()
    existing = get_rule(rule_id, db)
    if not existing:
        return None
    allowed = {
        "name", "measure_code", "operator", "threshold", "threshold2",
        "dimensions_json", "severity_level", "enabled", "cooldown_minutes",
        "builtin_type", "description", "assignee_ids", "notify_channels",
    }
    set_parts = []
    params = []
    for key, value in updates.items():
        if key in allowed:
            set_parts.append(f"{key} = ?")
            params.append(value)
    if not set_parts:
        return existing
    set_parts.append("updated_at = ?")
    params.append(datetime.now(timezone.utc).isoformat())
    params.append(rule_id)
    db.execute(
        f"UPDATE alert_rule SET {', '.join(set_parts)} WHERE id = ?", params
    )
    db.commit()
    return get_rule(rule_id, db)


def delete_rule(rule_id: int, db=None) -> bool:
    db = db or get_db()
    existing = get_rule(rule_id, db)
    if not existing or existing.get("rule_type") == "builtin":
        return False
    db.execute("DELETE FROM alert_rule WHERE id = ? AND rule_type != 'builtin'", (rule_id,))
    db.commit()
    return True


def toggle_rule(rule_id: int, enabled: bool, db=None) -> dict[str, Any] | None:
    return update_rule(rule_id, {"enabled": enabled}, db)


# ── Alert logs ───────────────────────────────────────────────────────────── #

def list_logs(
    db=None,
    rule_id: int | None = None,
    severity_level: int | None = None,
    status: str | None = None,
    search: str = "",
    offset: int = 0,
    limit: int = 50,
) -> tuple[list[dict[str, Any]], int]:
    db = db or get_db()
    wheres = []
    params: list[Any] = []
    if rule_id is not None:
        wheres.append("rule_id = ?")
        params.append(rule_id)
    if severity_level is not None:
        wheres.append("severity_level = ?")
        params.append(severity_level)
    if status:
        wheres.append("status = ?")
        params.append(status)
    if search:
        wheres.append("(measure_name LIKE ? OR dim_values_json LIKE ? OR resolve_note LIKE ?)")
        like = f"%{search}%"
        params.extend([like, like, like])
    where_clause = ("WHERE " + " AND ".join(wheres)) if wheres else ""
    count = db.execute(
        f"SELECT COUNT(*) FROM alert_log {where_clause}", params
    ).fetchone()[0]
    rows = db.execute(
        f"""SELECT * FROM alert_log {where_clause}
            ORDER BY triggered_at DESC LIMIT ? OFFSET ?""",
        params + [limit, offset],
    ).fetchall()
    return [_row_to_dict(row) for row in rows], count


def get_log(log_id: int, db=None) -> dict[str, Any] | None:
    db = db or get_db()
    row = db.execute("SELECT * FROM alert_log WHERE id = ?", (log_id,)).fetchone()
    return _row_to_dict(row) if row else None


def ack_log(log_id: int, db=None) -> dict[str, Any] | None:
    db = db or get_db()
    db.execute(
        "UPDATE alert_log SET status = 'ack', acked_at = ? WHERE id = ? AND status = 'open'",
        (datetime.now(timezone.utc).isoformat(), log_id),
    )
    db.commit()
    return get_log(log_id, db)


def resolve_log(log_id: int, note: str, db=None) -> dict[str, Any] | None:
    db = db or get_db()
    now = datetime.now(timezone.utc).isoformat()
    db.execute(
        """UPDATE alert_log SET status = 'closed', resolve_note = ?,
           resolved_at = ? WHERE id = ? AND status IN ('open', 'ack')""",
        (note, now, log_id),
    )
    db.commit()
    return get_log(log_id, db)


def reassign_log(log_id: int, assignee_id: str, assignee_name: str = "", db=None) -> dict[str, Any] | None:
    db = db or get_db()
    db.execute(
        "UPDATE alert_log SET assignee_id = ?, assignee_name = ? WHERE id = ?",
        (assignee_id, assignee_name, log_id),
    )
    db.commit()
    return get_log(log_id, db)


def create_log(
    rule_id: int,
    measure_code: str,
    measure_name: str,
    dim_values_json: str,
    actual_value: float,
    threshold_desc: str,
    severity_level: int,
    assignee_id: str = "",
    assignee_name: str = "",
    db=None,
) -> dict[str, Any]:
    db = db or get_db()
    now = datetime.now(timezone.utc).isoformat()
    db.execute(
        """INSERT INTO alert_log (rule_id, measure_code, measure_name, dim_values_json,
           actual_value, threshold_desc, severity_level, status, assignee_id,
           assignee_name, triggered_at)
           VALUES (?, ?, ?, ?, ?, ?, ?, 'open', ?, ?, ?)""",
        (rule_id, measure_code, measure_name, dim_values_json, actual_value,
         threshold_desc, severity_level, assignee_id, assignee_name, now),
    )
    db.commit()
    return _row_to_dict(
        db.execute("SELECT * FROM alert_log WHERE rowid = last_insert_rowid()").fetchone()
    )


# ── Notify logs ──────────────────────────────────────────────────────────── #

def create_notify_log(
    alert_log_id: int,
    channel: str,
    receiver_id: str,
    receiver_name: str,
    content: str,
    status: str = "sent",
    error_msg: str = "",
    db=None,
) -> dict[str, Any]:
    db = db or get_db()
    now = datetime.now(timezone.utc).isoformat()
    db.execute(
        """INSERT INTO alert_notify_log (alert_log_id, channel, receiver_id,
           receiver_name, content, status, error_msg, sent_at)
           VALUES (?, ?, ?, ?, ?, ?, ?, ?)""",
        (alert_log_id, channel, receiver_id, receiver_name, content, status, error_msg, now),
    )
    db.commit()
    return _row_to_dict(
        db.execute("SELECT * FROM alert_notify_log WHERE rowid = last_insert_rowid()").fetchone()
    )


# ── Stats ────────────────────────────────────────────────────────────────── #

def get_stats(db=None) -> dict[str, Any]:
    db = db or get_db()
    return {
        "activeAlerts": db.execute(
            "SELECT COUNT(*) FROM alert_log WHERE status IN ('open', 'ack')"
        ).fetchone()[0],
        "todayNew": db.execute(
            "SELECT COUNT(*) FROM alert_log WHERE date(triggered_at) = date('now')"
        ).fetchone()[0],
        "byLevel": {
            row[0]: row[1]
            for row in db.execute(
                "SELECT severity_level, COUNT(*) FROM alert_log WHERE status IN ('open', 'ack') GROUP BY 1"
            ).fetchall()
        },
        "totalRules": db.execute("SELECT COUNT(*) FROM alert_rule").fetchone()[0],
        "enabledRules": db.execute("SELECT COUNT(*) FROM alert_rule WHERE enabled = 1").fetchone()[0],
    }


# ── Helpers ──────────────────────────────────────────────────────────────── #

def _row_to_dict(row) -> dict[str, Any]:
    return {key: row[key] for key in row.keys()}
