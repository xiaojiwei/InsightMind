"""Periodic alert scanner.

Runs in a background asyncio task, queries the AD semantic API for each
enabled rule, feeds results through the detection engine, and dispatches
notifications when thresholds are breached.

Detection methods (encoded in rule.operator):
  - "statistical"        → full statistical engine (z-score, IQR, path, expression)
  - "fluctuation"        → period-over-period change detection
  - "zero"               → near-zero / drop-to-zero detection
  - "gt"/"lt"/"gte"/"lte"/"eq" → simple value threshold
  - "between"/"outside"  → range threshold
  - "always"             → fire on any data returned (demo/testing)
"""

from __future__ import annotations

import asyncio
import inspect
import json
import logging
from datetime import datetime, timezone
from typing import Any, Callable, Optional

from . import models, notify

logger = logging.getLogger(__name__)

# Seconds between scan cycles
DEFAULT_SCAN_INTERVAL = 300  # 5 minutes

_scan_task: Optional[asyncio.Task] = None
_load_fn: Optional[Callable] = None

# ── Operators that use the full detection engine ─────────────────────────
_ENGINE_METHODS = {"statistical", "fluctuation", "zero"}
# Operators that use simple threshold comparison
_THRESHOLD_METHODS = {"gt", "lt", "gte", "lte", "eq", "between", "outside", "always"}


def init_scan_loader(fn: Callable) -> None:
    """Register the function used to execute AD semantic queries.

    The function must accept a dict (the query payload) and return a dict
    (the raw query result).  Typically wraps :func:`web_app._execute_semantic_load`.
    """
    global _load_fn
    _load_fn = fn


async def _execute_load(query: dict) -> dict:
    if _load_fn is None:
        return {}
    result = _load_fn(query)
    if inspect.isawaitable(result):
        result = await result
    return result or {}


async def start(interval: int = DEFAULT_SCAN_INTERVAL) -> None:
    global _scan_task
    if _scan_task is not None:
        return
    logger.info("Alert scanner starting (interval=%ds)", interval)
    _scan_task = asyncio.create_task(_scan_loop(interval))


async def stop() -> None:
    global _scan_task
    if _scan_task is None:
        return
    _scan_task.cancel()
    try:
        await _scan_task
    except asyncio.CancelledError:
        pass
    _scan_task = None
    logger.info("Alert scanner stopped")


async def _scan_loop(interval: int) -> None:
    while True:
        try:
            await _run_scan_cycle()
        except asyncio.CancelledError:
            raise
        except Exception:
            logger.exception("Alert scan cycle failed")
        await asyncio.sleep(interval)


async def _run_scan_cycle() -> None:
    if _load_fn is None:
        logger.warning("Alert scanner has no load function; skipping cycle")
        return

    rules = models.list_enabled_rules()
    if not rules:
        return

    now = datetime.now(timezone.utc)

    for rule in rules:
        rule_id = rule["id"]

        # Cooldown check
        cooldown = int(rule.get("cooldown_minutes") or 15)
        last_triggered = models.last_trigger_time_for_rule(rule_id)
        if last_triggered:
            elapsed = (now - last_triggered.replace(tzinfo=timezone.utc)).total_seconds()
            if elapsed < cooldown * 60:
                continue

        method = str(rule.get("operator") or "always")

        if method in _ENGINE_METHODS:
            await _scan_with_engine(rule, now)
        elif method in _THRESHOLD_METHODS:
            await _scan_threshold(rule, now, method)


# ── Full engine scan (statistical / fluctuation / zero) ──────────────────

async def _scan_with_engine(rule: dict, now: datetime) -> None:
    """Run the full detection engine for one rule."""
    measure_code = str(rule.get("measure_code") or "")
    if not measure_code:
        return

    measure_member = _code_to_member(measure_code)
    query = {
        "measures": [measure_member],
        "dimensions": [],
        "limit": 200,
        "alertRuleIds": [rule["id"]],
    }

    try:
        result = await _execute_load(query)
    except Exception as exc:
        logger.warning("Engine scan skipped for rule %s: %s; query=%s", rule["id"], exc, query)
        return

    alert_summary = result.get("alerts") or {}
    for alert_item in alert_summary.get("items", []):
        severity_label = alert_item.get("severity", "warning")

        models.insert_alert_log(
            rule_id=rule["id"],
            rule_name=rule.get("name", ""),
            measure_code=measure_code,
            measure_name=rule.get("name", ""),
            dim_values=_alert_dim_values(alert_item),
            actual_value=_alert_actual_value(alert_item),
            threshold_desc=alert_item.get("reason", "detection engine"),
            severity=severity_label,
            assignee_id=rule.get("assignee_id") or "",
            assignee_name=rule.get("assignee_name") or "",
        )

        if rule.get("notify_enabled"):
            n = notify.AlertNotification(
                alert_log_id=0,
                rule_name=rule.get("name", ""),
                measure_name=rule.get("name", ""),
                measure_code=measure_code,
                actual_value=str(alert_item.get("measure", "")),
                threshold_desc=alert_item.get("reason", ""),
                severity_label=severity_label,
                severity_color=alert_item.get("color", "#d99042"),
                reason=alert_item.get("reason", ""),
                assignee_id=rule.get("assignee_id") or "",
                assignee_name=rule.get("assignee_name") or "",
                triggered_at=now.isoformat(),
            )
            await notify.send_alert(n)

        models.update_rule(rule["id"], {
            "last_triggered_at": now.strftime("%Y-%m-%d %H:%M:%S"),
            "trigger_count": (rule.get("trigger_count") or 0) + 1,
        })


# ── Threshold scan (gt / lt / between / etc.) ───────────────────────────

async def _scan_threshold(rule: dict, now: datetime, operator: str) -> None:
    """Evaluate a simple threshold-based rule."""
    measure_code = str(rule.get("measure_code") or "")
    if not measure_code:
        return

    measure_member = _code_to_member(measure_code)
    dimensions_json = rule.get("dimensions_json")
    dims = json.loads(dimensions_json) if dimensions_json else {}

    query = {
        "measures": [measure_member],
        "dimensions": [_code_to_member(str(dim)) for dim in dims.keys()],
        "limit": 200,
        "alertRuleIds": [rule["id"]],
    }

    try:
        result = await _execute_load(query)
    except Exception as exc:
        logger.warning("Threshold scan skipped for rule %s: %s; query=%s", rule["id"], exc, query)
        return

    alert_summary = result.get("alerts") or {}
    for alert_item in alert_summary.get("items", []):
        severity_label = alert_item.get("severity", "warning")

        models.insert_alert_log(
            rule_id=rule["id"],
            rule_name=rule.get("name", ""),
            measure_code=measure_code,
            measure_name=rule.get("name", ""),
            dim_values=_alert_dim_values(alert_item),
            actual_value=_alert_actual_value(alert_item),
            threshold_desc=alert_item.get("reason", "threshold rule"),
            severity=severity_label,
            assignee_id=rule.get("assignee_id") or "",
            assignee_name=rule.get("assignee_name") or "",
        )

        if rule.get("notify_enabled"):
            n = notify.AlertNotification(
                alert_log_id=0,
                rule_name=rule.get("name", ""),
                measure_name=rule.get("name", ""),
                measure_code=measure_code,
                actual_value=str(alert_item.get("measure", "")),
                threshold_desc=alert_item.get("reason", ""),
                severity_label=severity_label,
                severity_color={"notice": "#f3c969", "warning": "#d99042", "critical": "#c84b4b"}.get(severity_label, "#d99042"),
                reason=alert_item.get("reason", ""),
                assignee_id=rule.get("assignee_id") or "",
                assignee_name=rule.get("assignee_name") or "",
                triggered_at=now.isoformat(),
            )
            await notify.send_alert(n)

        models.update_rule(rule["id"], {
            "last_triggered_at": now.strftime("%Y-%m-%d %H:%M:%S"),
            "trigger_count": (rule.get("trigger_count") or 0) + 1,
        })


# ── Helpers ──────────────────────────────────────────────────────────────

def _code_to_member(code: str) -> str:
    """MEAS_SALES → ad.sales"""
    if code.startswith("MEAS_"):
        return f"ad.{code[5:].lower()}"
    if code.startswith("DIM_"):
        return f"ad.{code[4:].lower()}"
    return code


def _alert_actual_value(alert_item: dict) -> str:
    row = alert_item.get("row") or {}
    measure = alert_item.get("measure") or ""
    if isinstance(row, dict) and measure in row:
        return str(row.get(measure))
    return str(alert_item.get("value") or "")


def _alert_dim_values(alert_item: dict) -> str:
    row = alert_item.get("row") or {}
    if not isinstance(row, dict):
        return "{}"
    dims = {
        key: value
        for key, value in row.items()
        if str(key).startswith("ad.") and key != alert_item.get("measure")
    }
    return json.dumps(dims, ensure_ascii=False)
