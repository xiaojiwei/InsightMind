"""FastAPI router for the alert management module.

Mount under ``/api/alerts`` in web_app.py via ``app.include_router(alerts_router)``.
"""

from __future__ import annotations

import json
import logging
from datetime import datetime
from pathlib import Path
from typing import Any, Optional

from fastapi import APIRouter, HTTPException, Query
from rdflib import Graph, Namespace, RDF
from sqlalchemy import text

from . import models, notify, rules as rule_lib

logger = logging.getLogger(__name__)

router = APIRouter(prefix="/api/alerts", tags=["alerts"])

# ── Business KG paths (mirrors web_app.py layout) ────────────────────────

_BKG_DIR = Path(__file__).resolve().parent.parent.parent / "output" / "business_kg"
_IND = Namespace("http://indicator.insightmind.com/ontology#")


# ---------------------------------------------------------------------------
# Measures (from business KG)
# ---------------------------------------------------------------------------

@router.get("/measures")
def list_measures(search: Optional[str] = Query(None)):
    """Return available measure codes + names from the business knowledge graph."""
    measures: list[dict[str, str]] = []
    ttl_path = _BKG_DIR / "indicator-data.ttl"
    inferred_path = _BKG_DIR / "indicator-inferred.ttl"

    if not ttl_path.exists():
        return {"measures": [], "hint": "尚未生成业务图谱"}

    try:
        g = Graph()
        g.parse(str(ttl_path), format="turtle")
        if inferred_path.exists():
            g.parse(str(inferred_path), format="turtle")
    except Exception:
        return {"measures": [], "hint": "业务图谱解析失败"}

    seen = set()
    for s in g.subjects(RDF.type, _IND.Measure):
        code = str(g.value(s, _IND.code) or "")
        if not code or not code.startswith("MEAS_") or code in seen:
            continue
        seen.add(code)

        cn_name = str(g.value(s, _IND.cnName) or "")
        en_name = str(g.value(s, _IND.enName) or "")
        name = cn_name or en_name or code

        # Resolve the first fact table
        table = ""
        for app in g.objects(s, _IND.hasMeasureApp):
            for tbl in g.objects(app, _IND.appliesToTable):
                table = str(g.value(tbl, _IND.tableName) or "")
                break
            if not table:
                for tbl in g.objects(app, _IND.measFactTable):
                    table = str(g.value(tbl, _IND.tableName) or "")
                    break
            if table:
                break

        if search:
            q = search.lower()
            if q not in code.lower() and q not in name.lower() and q not in table.lower():
                continue

        measures.append({
            "code": code,
            "name": name,
            "table": table,
        })

    measures.sort(key=lambda m: m["code"])
    return {"measures": measures}


@router.get("/measures/{code}/dimensions")
def list_measure_dimensions(code: str):
    """Return available dimension codes/names for a given measure from the business KG."""
    dimensions: list[dict[str, str]] = []
    ttl_path = _BKG_DIR / "indicator-data.ttl"
    inferred_path = _BKG_DIR / "indicator-inferred.ttl"

    if not ttl_path.exists():
        return {"dimensions": [], "hint": "尚未生成业务图谱"}

    try:
        g = Graph()
        g.parse(str(ttl_path), format="turtle")
        if inferred_path.exists():
            g.parse(str(inferred_path), format="turtle")
    except Exception:
        return {"dimensions": [], "hint": "业务图谱解析失败"}

    # Find the measure URI by code
    measure_uri = None
    for s in g.subjects(RDF.type, _IND.Measure):
        if str(g.value(s, _IND.code) or "") == code:
            measure_uri = s
            break

    if not measure_uri:
        return {"dimensions": [], "hint": f"指标 {code} 不存在"}

    # Collect the fact tables for this measure
    fact_tables: set = set()
    for app in g.objects(measure_uri, _IND.hasMeasureApp):
        for tbl in g.objects(app, _IND.appliesToTable):
            fact_tables.add(tbl)
        for tbl in g.objects(app, _IND.measFactTable):
            fact_tables.add(tbl)

    # Find dimensions that share a fact table with this measure
    seen = set()
    for dim in g.subjects(RDF.type, _IND.Dimension):
        dim_code = str(g.value(dim, _IND.code) or "")
        if not dim_code or dim_code in seen:
            continue

        # Check if any of this dimension's apps point to one of our fact tables
        for dim_app in g.objects(dim, _IND.hasDimApp):
            for tbl in g.objects(dim_app, _IND.dimFactTable):
                if tbl in fact_tables:
                    seen.add(dim_code)
                    cn_name = str(g.value(dim, _IND.cnName) or "")
                    en_name = str(g.value(dim, _IND.enName) or "")
                    name = cn_name or en_name or dim_code
                    dimensions.append({
                        "code": dim_code,
                        "name": name,
                        "table": str(g.value(tbl, _IND.tableName) or ""),
                    })
                    break
            if dim_code in seen:
                break

    dimensions.sort(key=lambda d: d["code"])
    return {"dimensions": dimensions}


@router.get("/dimensions/{code}/values")
def list_dimension_values(code: str, q: Optional[str] = Query(None)):
    """Return distinct values for a dimension by querying the source database."""
    ttl_path = _BKG_DIR / "indicator-data.ttl"
    if not ttl_path.exists():
        return {"values": [], "hint": "尚未生成业务图谱"}

    try:
        g = Graph()
        g.parse(str(ttl_path), format="turtle")
    except Exception:
        return {"values": [], "hint": "业务图谱解析失败"}

    # Find dimension → DimApp → dimFactTable + dimFactColumn
    dim_uri = None
    for s in g.subjects(RDF.type, _IND.Dimension):
        if str(g.value(s, _IND.code) or "") == code:
            dim_uri = s
            break
    if not dim_uri:
        return {"values": [], "hint": f"维度 {code} 不存在"}

    fact_table_name = ""
    fact_column = ""
    for app in g.objects(dim_uri, _IND.hasDimApp):
        fact_column = str(g.value(app, _IND.dimFactColumn) or "")
        tbl_uri = g.value(app, _IND.dimFactTable)
        if tbl_uri:
            fact_table_name = str(g.value(tbl_uri, _IND.tableName) or "")
        if fact_table_name and fact_column:
            break

    if not fact_table_name or not fact_column:
        return {"values": [], "hint": "无法解析维度表/列"}

    # Try to resolve FK → lookup table with human-readable column
    # Load data source KG
    ds_kg = _load_datasource_kg()
    lookup_table, lookup_column = _resolve_fk_target(ds_kg, fact_column)

    # Connect to MySQL and query
    try:
        conn = models._get_engine().connect()
        if lookup_table and lookup_column:
            sql = f"SELECT DISTINCT `{lookup_column}` AS val FROM `{lookup_table}`"
        else:
            sql = f"SELECT DISTINCT `{fact_column}` AS val FROM `{fact_table_name}`"
        if q:
            sql += f" WHERE `{lookup_column or fact_column}` LIKE :q"
        sql += f" ORDER BY 1 LIMIT 200"
        rows = conn.execute(
            text(sql), {"q": f"%{q}%" if q else None}
        ).mappings().all()
        conn.close()
        values = [str(r["val"]) for r in rows if r["val"] is not None]
        return {"values": values, "table": lookup_table or fact_table_name, "column": lookup_column or fact_column}
    except Exception as e:
        return {"values": [], "hint": f"数据库查询失败: {e}"}


def _load_datasource_kg():
    """Load the most recent data source KG file."""
    output_dir = _BKG_DIR.parent  # apps/ad/output/
    kg_files = sorted(output_dir.glob("kg_*.ttl"), key=lambda p: p.stat().st_mtime)
    if not kg_files:
        legacy = output_dir / "kg.ttl"
        if legacy.exists():
            kg_files = [legacy]
    if not kg_files:
        return None
    g = Graph()
    g.parse(str(kg_files[-1]), format="turtle")
    return g


def _resolve_fk_target(ds_kg, col_name: str):
    """Given a FK column like 'cs_call_center_sk', resolve to (lookup_table, display_col).

    Strategy: parse column naming convention {prefix}_{dim}_sk → {dim} table.
    Then find a display column (preferring *name, *desc) in that lookup table.
    """
    if ds_kg is None:
        return None, None

    DB = Namespace("http://kg.local/db#")

    # 1. Try db:references first
    for col_uri, cname in ds_kg.subject_objects(DB.name):
        if str(cname) == col_name:
            ref = ds_kg.value(col_uri, DB.references)
            if ref is not None:
                ref_table_uri = ds_kg.value(ref, DB.belongsToTable)
                if ref_table_uri:
                    ref_table = str(ds_kg.value(ref_table_uri, DB.tableName) or
                                   ds_kg.value(ref_table_uri, DB.name) or "")
                    disp_col = _pick_display_column(ds_kg, ref_table_uri, str(ds_kg.value(ref, DB.name) or ""))
                    return ref_table, disp_col
            break

    # 2. Parse naming convention: cs_call_center_sk → call_center
    import re
    # Strip known fact table prefixes and _sk suffix
    trimmed = re.sub(r'^(cs_|ws_|ss_|cr_|sr_|wr_|hd_|cd_|ib_|wp_)', '', col_name)
    trimmed = re.sub(r'_(sk|id|pk)$', '', trimmed)

    if trimmed != col_name:
        # Check if this table exists in the KG
        for tbl_uri, tbl_name in ds_kg.subject_objects(DB.tableName):
            if str(tbl_name).lower() == trimmed.lower():
                disp_col = _pick_display_column(ds_kg, tbl_uri)
                return trimmed, disp_col

    return None, None


def _pick_display_column(ds_kg, table_uri, skip_column: str = ""):
    """Pick the best human-readable display column from a table."""
    DB = Namespace("http://kg.local/db#")
    PREFERRED = ["name", "desc", "description", "id", "title", "label"]
    candidates = []
    for col in ds_kg.objects(table_uri, DB.containsColumn):
        cname = str(ds_kg.value(col, DB.name) or "")
        if cname == skip_column:
            continue
        score = 0
        for i, sfx in enumerate(PREFERRED):
            if cname.lower().endswith(sfx):
                score = len(PREFERRED) - i
                break
        # Bonus for columns with 'name' in them
        if 'name' in cname.lower():
            score += 5
        candidates.append((score, cname))
    candidates.sort(key=lambda x: -x[0])
    return candidates[0][1] if candidates else ""

def _builtin_defaults_by_type():
    """Return {type: {param_name: default_value}} from the flat BUILTIN_DEFAULTS."""
    result: dict[str, dict[str, Any]] = {}
    for full_key, info in rule_lib.BUILTIN_DEFAULTS.items():
        if "." in full_key:
            rt, param = full_key.split(".", 1)
            result.setdefault(rt, {})[param] = info["param_value"]
    return result


# ---------------------------------------------------------------------------
# Builtin defaults
# ---------------------------------------------------------------------------

@router.get("/builtin/defaults")
def get_builtin_defaults():
    """Return code-defined defaults for builtin rule types, grouped by type."""
    return {"defaults": _builtin_defaults_by_type()}


@router.get("/builtin/config")
def get_builtin_config():
    """Get the currently active builtin parameter overrides."""
    defaults_by_type = _builtin_defaults_by_type()
    result = {}
    for rt in ["self","path","expression"]:
        cfg = models.get_builtin_config(rt)
        for param, default_val in defaults_by_type.get(rt, {}).items():
            cfg.setdefault(param, default_val)
        result[rt] = cfg
    return {"builtins": result}


@router.put("/builtin/config/{rule_type}")
def update_builtin_config(rule_type: str, body: dict[str, Any]):
    """Update builtin rule parameters for a given type (self|path|expression)."""
    if rule_type not in ["self","path","expression"]:
        raise HTTPException(400, f"Unknown builtin type: {rule_type}")
    valid_keys = set(_builtin_defaults_by_type().get(rule_type, {}).keys())
    valid_keys.update({"enabled", "cooldown_minutes"})
    for key, value in body.items():
        if key not in valid_keys:
            raise HTTPException(400, f"Unknown param '{key}' for {rule_type}")
        models.upsert_builtin_config(rule_type, key, json.dumps(value) if not isinstance(value, (int, float, bool, str)) else value)
    return {"ok": True}


@router.post("/builtin/reset/{rule_type}")
def reset_builtin_config(rule_type: str):
    """Reset builtin config to code defaults."""
    if rule_type not in ["self","path","expression"]:
        raise HTTPException(400, f"Unknown builtin type: {rule_type}")
    models.delete_builtin_config(rule_type)
    return {"ok": True}


# ---------------------------------------------------------------------------
# Custom rules CRUD
# ---------------------------------------------------------------------------

@router.get("/rules")
def list_rules(
    page: int = Query(1, ge=1),
    page_size: int = Query(20, ge=1, le=200),
    status: Optional[str] = None,
    severity: Optional[str] = None,
    measure_code: Optional[str] = None,
    search: Optional[str] = None,
):
    """Paginated rule list with optional filters."""
    rows, total = models.list_rules(
        page=page, page_size=page_size,
        status=status, severity=severity,
        measure_code=measure_code, search=search,
    )
    return {
        "items": [row for row in rows],
        "total": total,
        "page": page,
        "pageSize": page_size,
    }


@router.get("/rules/{rule_id}")
def get_rule(rule_id: int):
    row = models.get_rule(rule_id)
    if not row:
        raise HTTPException(404, "Rule not found")
    return row


@router.post("/rules")
def create_rule(body: dict[str, Any]):
    try:
        rule = body
    except ValueError as e:
        raise HTTPException(400, str(e))
    rule_id = models.insert_rule(rule)
    return {"ok": True, "id": rule_id}


@router.put("/rules/{rule_id}")
def update_rule(rule_id: int, body: dict[str, Any]):
    existing = models.get_rule(rule_id)
    if not existing:
        raise HTTPException(404, "Rule not found")
    try:
        rule = body
    except ValueError as e:
        raise HTTPException(400, str(e))
    models.update_rule(rule_id, rule)
    return {"ok": True}


@router.delete("/rules/{rule_id}")
def delete_rule(rule_id: int):
    existing = models.get_rule(rule_id)
    if not existing:
        raise HTTPException(404, "Rule not found")
    models.delete_rule(rule_id)
    return {"ok": True}


@router.patch("/rules/{rule_id}/toggle")
def toggle_rule(rule_id: int):
    existing = models.get_rule(rule_id)
    if not existing:
        raise HTTPException(404, "Rule not found")
    new_status = "disabled" if existing.get("enabled") else "enabled"
    models.update_rule(rule_id, {"enabled": new_status == "enabled"})
    return {"ok": True, "enabled": new_status == "enabled"}


# ---------------------------------------------------------------------------
# Alert logs
# ---------------------------------------------------------------------------

@router.get("/logs")
def list_logs(
    page: int = Query(1, ge=1),
    page_size: int = Query(20, ge=1, le=200),
    status: Optional[str] = None,
    severity: Optional[str] = None,
    measure_code: Optional[str] = None,
    rule_id: Optional[int] = None,
    from_time: Optional[str] = None,
    to_time: Optional[str] = None,
):
    rows, total = models.list_alert_logs(
        page=page, page_size=page_size,
        status=status, severity=severity,
        measure_code=measure_code, rule_id=rule_id,
        from_time=from_time, to_time=to_time,
    )
    return {
        "items": rows,
        "total": total,
        "page": page,
        "pageSize": page_size,
    }


@router.get("/logs/{log_id}")
def get_log(log_id: int):
    row = models.get_alert_log(log_id)
    if not row:
        raise HTTPException(404, "Alert log not found")
    return row


@router.post("/logs/{log_id}/ack")
def ack_log(log_id: int):
    existing = models.get_alert_log(log_id)
    if not existing:
        raise HTTPException(404, "Alert log not found")
    models.update_alert_log(log_id, {"status": "acknowledged"})
    return {"ok": True}


@router.post("/logs/{log_id}/resolve")
def resolve_log(log_id: int, body: dict[str, Any]):
    existing = models.get_alert_log(log_id)
    if not existing:
        raise HTTPException(404, "Alert log not found")
    note = str(body.get("note") or "")
    resolved_by = str(body.get("resolved_by") or "")
    models.update_alert_log(log_id, {
        "status": "closed",
        "resolve_note": note,
        "resolved_by": resolved_by,
        "resolved_at": datetime.utcnow().isoformat(),
    })
    return {"ok": True}


@router.post("/logs/{log_id}/reassign")
def reassign_log(log_id: int, body: dict[str, Any]):
    existing = models.get_alert_log(log_id)
    if not existing:
        raise HTTPException(404, "Alert log not found")
    assignee_id = str(body.get("assignee_id") or "")
    assignee_name = str(body.get("assignee_name") or "")
    models.update_alert_log(log_id, {
        "assignee_id": assignee_id,
        "assignee_name": assignee_name,
    })
    return {"ok": True}


# ---------------------------------------------------------------------------
# Statistics
# ---------------------------------------------------------------------------

@router.get("/stats")
def get_stats(days: int = Query(30, ge=1, le=365)):
    return models.get_alert_stats(days)


# ---------------------------------------------------------------------------
# Severity levels
# ---------------------------------------------------------------------------

@router.get("/levels")
def list_levels():
    return {"levels": models.list_severity_levels()}


@router.put("/levels/{level_id}")
def update_level(level_id: int, body: dict[str, Any]):
    updates = {}
    for key in ("label", "color", "sort_order"):
        if key in body:
            updates[key] = body[key]
    if updates:
        models.update_severity_level(level_id, updates)
    return {"ok": True}


# ---------------------------------------------------------------------------
# Notification config
# ---------------------------------------------------------------------------

@router.get("/notify/config")
def get_notify_config():
    return models.get_notify_config_dict()


@router.put("/notify/config")
def update_notify_config(body: dict[str, Any]):
    for key, value in body.items():
        if key in ("app_id", "app_secret", "base_url"):
            notify.configure_feishu(
                app_id=body.get("app_id", ""),
                app_secret=body.get("app_secret", ""),
                base_url=body.get("base_url", ""),
            )
        models.upsert_notify_config(key, json.dumps(value) if not isinstance(value, (int, float, bool, str)) else value)
    return {"ok": True}


@router.post("/notify/test")
async def test_notify(body: dict[str, Any]):
    open_id = str(body.get("open_id") or "")
    if not open_id:
        raise HTTPException(400, "open_id is required")
    # Ensure notify module has current config
    cfg = models.get_notify_config_dict()
    notify.configure_feishu(
        app_id=cfg.get("app_id", ""),
        app_secret=cfg.get("app_secret", ""),
        base_url=cfg.get("base_url", ""),
    )
    result = await notify.send_test_notification(open_id)
    return {"ok": True, "result": result}


# ---------------------------------------------------------------------------
# Summary for dashboard widget
# ---------------------------------------------------------------------------

@router.get("/summary")
def get_summary():
    return models.get_alert_summary()
