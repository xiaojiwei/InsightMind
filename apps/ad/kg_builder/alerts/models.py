"""MySQL models for alert management. Uses the existing AD MySQL datasource from config.yaml."""
from __future__ import annotations
import json, os, threading
from contextlib import contextmanager
from datetime import datetime, timezone
from typing import Any, Iterator
from sqlalchemy import create_engine, text
from sqlalchemy.pool import QueuePool

DB_CONFIG = {
    "host": os.getenv("ALERT_DB_HOST", "localhost"),
    "port": int(os.getenv("ALERT_DB_PORT", "3306")),
    "user": os.getenv("ALERT_DB_USER", "root"),
    "password": os.getenv("ALERT_DB_PASSWORD", os.getenv("MYSQL_PWD", "root")),
    "database": os.getenv("ALERT_DB_NAME", "tpcds"),
    "charset": os.getenv("ALERT_DB_CHARSET", "utf8mb4"),
}
URL = f"mysql+pymysql://{DB_CONFIG['user']}:{DB_CONFIG['password']}@{DB_CONFIG['host']}:{DB_CONFIG['port']}/{DB_CONFIG['database']}?charset=utf8mb4"
_lock = threading.Lock()
_engine = None

def _get_engine():
    global _engine
    if _engine is None: _engine = create_engine(URL, poolclass=QueuePool, pool_size=3, max_overflow=5, pool_pre_ping=True)
    return _engine

def _now(): return datetime.now(timezone.utc).strftime("%Y-%m-%d %H:%M:%S")

def get_db():
    engine = _get_engine()
    return engine.connect()

SCHEMA = """
CREATE TABLE IF NOT EXISTS alert_rule (
    id INT AUTO_INCREMENT PRIMARY KEY, name VARCHAR(255) NOT NULL, description TEXT,
    rule_type VARCHAR(32) NOT NULL DEFAULT 'custom', builtin_type VARCHAR(32) DEFAULT NULL,
    measure_code VARCHAR(255) DEFAULT '', operator VARCHAR(32) DEFAULT 'always',
    threshold DOUBLE DEFAULT NULL, threshold2 DOUBLE DEFAULT NULL,
    dimensions_json TEXT, severity VARCHAR(32) DEFAULT 'warning',
    enabled TINYINT DEFAULT 1, cooldown_minutes INT DEFAULT 15,
    notify_enabled TINYINT DEFAULT 1,
    assignee_id VARCHAR(255) DEFAULT '', assignee_name VARCHAR(255) DEFAULT '',
    trigger_count INT DEFAULT 0, last_triggered_at DATETIME DEFAULT NULL,
    created_at DATETIME NOT NULL, updated_at DATETIME NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS alert_builtin_config (
    id INT AUTO_INCREMENT PRIMARY KEY, param_key VARCHAR(255) NOT NULL UNIQUE,
    param_value TEXT NOT NULL, description TEXT, updated_at DATETIME NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS alert_log (
    id INT AUTO_INCREMENT PRIMARY KEY, rule_id INT DEFAULT NULL,
    rule_name VARCHAR(255) DEFAULT '', measure_code VARCHAR(255) DEFAULT '',
    measure_name VARCHAR(255) DEFAULT '', dim_values_json TEXT,
    actual_value TEXT, threshold_desc TEXT, severity VARCHAR(32) DEFAULT 'warning',
    status VARCHAR(32) NOT NULL DEFAULT 'open',
    assignee_id VARCHAR(255) DEFAULT '', assignee_name VARCHAR(255) DEFAULT '',
    resolve_note TEXT, resolved_by VARCHAR(255) DEFAULT '',
    triggered_at DATETIME NOT NULL, resolved_at DATETIME DEFAULT NULL, acked_at DATETIME DEFAULT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS alert_notify_log (
    id INT AUTO_INCREMENT PRIMARY KEY, alert_log_id INT DEFAULT NULL,
    channel VARCHAR(32) NOT NULL DEFAULT 'feishu',
    receiver_id VARCHAR(255) DEFAULT '', receiver_name VARCHAR(255) DEFAULT '',
    content TEXT, status VARCHAR(32) DEFAULT 'pending',
    error_msg TEXT, sent_at DATETIME NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS alert_notify_config (
    id INT AUTO_INCREMENT PRIMARY KEY, channel VARCHAR(32) NOT NULL UNIQUE,
    enabled TINYINT DEFAULT 1, config_json TEXT,
    quiet_start VARCHAR(8) DEFAULT '22:00', quiet_end VARCHAR(8) DEFAULT '08:00',
    updated_at DATETIME NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS alert_severity_level (
    id INT AUTO_INCREMENT PRIMARY KEY, code VARCHAR(32) NOT NULL UNIQUE,
    label VARCHAR(64) NOT NULL, color VARCHAR(16) NOT NULL DEFAULT '#d99042',
    sort_order INT DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
"""

DEMO_ALERT_RULES = [
    {
        "name": "Demo｜Wuhan 网络销售低于 40 万",
        "description": "演示规则：Wuhan 网络销售金额低于 40 万，标注为自身数据异常，使用底色提示。",
        "builtin_type": "self",
        "measure_code": "MEAS_web_sales_amount",
        "operator": "lte",
        "threshold": 400000,
        "threshold2": None,
        "dimensions_json": json.dumps({"DIM_warehouse_city": "Wuhan"}, ensure_ascii=False),
        "severity": "critical",
        "cooldown_minutes": 15,
    },
    {
        "name": "Demo｜Guangzhou 月度销售路径异常",
        "description": "演示规则：透视表中 Guangzhou 月度销售额超过 3 万，使用红色虚线边框。",
        "builtin_type": "path",
        "measure_code": "MEAS_web_sales_amount",
        "operator": "gte",
        "threshold": 30000,
        "threshold2": None,
        "dimensions_json": json.dumps({"DIM_warehouse_city": "Guangzhou"}, ensure_ascii=False),
        "severity": "warning",
        "cooldown_minutes": 15,
    },
    {
        "name": "Demo｜Guangzhou 利润结构异常",
        "description": "演示规则：父指标净利润整体正常，但假设内部收入/成本子项存在抵消，使用实线边框提示。",
        "builtin_type": "expression",
        "measure_code": "MEAS_web_net_profit",
        "operator": "gte",
        "threshold": 100000,
        "threshold2": None,
        "dimensions_json": json.dumps({"DIM_warehouse_city": "Guangzhou"}, ensure_ascii=False),
        "severity": "warning",
        "cooldown_minutes": 15,
    },
    {
        "name": "Demo｜Guangzhou 销售路径分化",
        "description": "演示规则：同一城市路径中 Guangzhou 明显高于其他路径，使用虚线边框提示路径异常。",
        "builtin_type": "path",
        "measure_code": "MEAS_web_sales_amount",
        "operator": "gte",
        "threshold": 500000,
        "threshold2": None,
        "dimensions_json": json.dumps({"DIM_warehouse_city": "Guangzhou"}, ensure_ascii=False),
        "severity": "warning",
        "cooldown_minutes": 15,
    },
]

def init_db():
    conn = get_db()
    try:
        # Execute SEQUENCE command
        conn.execute(text("CREATE DATABASE IF NOT EXISTS tpcds CHARACTER SET utf8mb4"))
        conn.commit()
        trans = conn.begin()
        try:
            for stmt in SCHEMA.split(";"):
                stmt = stmt.strip()
                if stmt:
                    conn.execute(text(stmt))
            trans.commit()
        except Exception:
            trans.rollback()
            raise
        # Seed
        if conn.execute(text("SELECT COUNT(*) FROM alert_severity_level")).scalar() == 0:
            conn.execute(text(
                "INSERT INTO alert_severity_level (id,code,label,color,sort_order) VALUES "
                "(1,'notice','关注','#f3c969',1),"
                "(2,'warning','异常','#d99042',2),"
                "(3,'critical','严重','#c84b4b',3)"
            ))
            conn.commit()
        now = _now()
        for k, v in [
            ("self.zscore_critical", "5.0"), ("self.zscore_warning", "3.5"),
            ("self.iqr_multiplier", "3.0"), ("self.drop_ratio", "-0.9"),
            ("self.surge_ratio", "3.0"), ("self.fluctuation_ratio", "1.5"),
            ("self.near_zero_ratio", "0.01"), ("self.enabled", "true"),
            ("path.iqr_multiplier", "2.5"), ("path.near_zero_ratio", "0.02"),
            ("path.surge_ratio", "5.0"), ("path.enabled", "true"),
            ("expression.enabled", "true"),
        ]:
            conn.execute(text(
                "INSERT IGNORE INTO alert_builtin_config (param_key,param_value,description,updated_at) "
                "VALUES (:k,:v,'',:now)"
            ), {"k": k, "v": v, "now": now})
        conn.commit()
        if conn.execute(text("SELECT COUNT(*) FROM alert_notify_config")).scalar() == 0:
            conn.execute(text(
                "INSERT INTO alert_notify_config(channel,enabled,config_json,quiet_start,quiet_end,updated_at) "
                "VALUES('feishu',0,'{}','22:00','08:00',:now)"
            ), {"now": _now()})
            conn.commit()
        _seed_demo_alert_rules(conn)
    finally:
        conn.close()


def _seed_demo_alert_rules(conn) -> None:
    now = _now()
    for rule in DEMO_ALERT_RULES:
        exists = conn.execute(
            text("SELECT COUNT(*) FROM alert_rule WHERE name=:name"),
            {"name": rule["name"]},
        ).scalar()
        if exists:
            continue
        conn.execute(text(
            "INSERT INTO alert_rule (name,description,rule_type,builtin_type,measure_code,operator,threshold,"
            "threshold2,dimensions_json,severity,enabled,cooldown_minutes,notify_enabled,assignee_id,assignee_name,"
            "created_at,updated_at) VALUES (:name,:desc,'custom',:btype,:mcode,:op,:th,:th2,:dims,:sev,1,:cool,1,'','',:now,:now)"
        ), {
            "name": rule["name"],
            "desc": rule["description"],
            "btype": rule["builtin_type"],
            "mcode": rule["measure_code"],
            "op": rule["operator"],
            "th": rule["threshold"],
            "th2": rule["threshold2"],
            "dims": rule["dimensions_json"],
            "sev": rule["severity"],
            "cool": rule["cooldown_minutes"],
            "now": now,
        })
    conn.commit()

# -- Rules CRUD --
def list_rules(page=1, page_size=20, status=None, severity=None, measure_code=None, search=None):
    conn = get_db()
    try:
        wh, pa = [], {}
        if status == "enabled": wh.append("enabled=1")
        elif status == "disabled": wh.append("enabled=0")
        if search:
            wh.append("(name LIKE :s OR measure_code LIKE :s2)")
            pa["s"] = f"%{search}%"; pa["s2"] = f"%{search}%"
        wc = "WHERE " + " AND ".join(wh) if wh else ""
        total = conn.execute(text(f"SELECT COUNT(*) FROM alert_rule {wc}"), pa).scalar()
        pa["lim"] = page_size; pa["off"] = (page - 1) * page_size
        rows = conn.execute(text(f"SELECT * FROM alert_rule {wc} ORDER BY id DESC LIMIT :lim OFFSET :off"), pa).mappings().all()
        return [dict(r) for r in rows], total
    finally:
        conn.close()

def get_rule(rule_id):
    conn = get_db()
    try:
        r = conn.execute(text("SELECT * FROM alert_rule WHERE id=:id"), {"id": rule_id}).mappings().first()
        return dict(r) if r else None
    finally:
        conn.close()

def insert_rule(rule):
    conn = get_db()
    try:
        result = conn.execute(text(
            "INSERT INTO alert_rule (name,description,rule_type,builtin_type,measure_code,operator,threshold,"
            "threshold2,dimensions_json,severity,enabled,cooldown_minutes,notify_enabled,assignee_id,assignee_name,"
            "created_at,updated_at) VALUES (:name,:desc,:type,:btype,:mcode,:op,:th,:th2,:dims,:sev,:en,:cool,:notify,"
            ":aid,:aname,:now,:now)"
        ), {
            "name": rule.get("name", ""), "desc": rule.get("description", ""),
            "type": rule.get("type", "custom"), "btype": rule.get("builtin_type"),
            "mcode": rule.get("measure_code", ""), "op": rule.get("operator", "always"),
            "th": rule.get("threshold"), "th2": rule.get("threshold2"),
            "dims": rule.get("dimensions_json", "{}"), "sev": rule.get("severity", "warning"),
            "en": 1 if rule.get("enabled", True) else 0, "cool": rule.get("cooldown_minutes", 15),
            "notify": 1 if rule.get("notify_enabled", True) else 0,
            "aid": rule.get("assignee_id", ""), "aname": rule.get("assignee_name", ""),
            "now": _now(),
        })
        conn.commit()
        return result.lastrowid
    finally:
        conn.close()

def update_rule(rule_id, data):
    conn = get_db()
    try:
        sets, pa = [], {"id": rule_id}
        for k in ("name","description","builtin_type","measure_code","operator","threshold",
                  "threshold2","dimensions_json","enabled","cooldown_minutes","notify_enabled",
                  "assignee_id","assignee_name","severity","last_triggered_at","trigger_count"):
            if k in data:
                v = data[k]
                if k in ("enabled", "notify_enabled"): v = 1 if v else 0
                sets.append(f"{k}=:{k}"); pa[k] = v
        if not sets: return
        pa["now"] = _now(); sets.append("updated_at=:now")
        conn.execute(text(f"UPDATE alert_rule SET {','.join(sets)} WHERE id=:id"), pa)
        conn.commit()
    finally:
        conn.close()

def delete_rule(rule_id):
    conn = get_db()
    try:
        conn.execute(text("DELETE FROM alert_rule WHERE id=:id"), {"id": rule_id})
        conn.commit()
    finally:
        conn.close()

# -- Alert logs --
def insert_alert_log(**kw):
    conn = get_db()
    try:
        conn.execute(text(
            "INSERT INTO alert_log (rule_id,rule_name,measure_code,measure_name,dim_values_json,"
            "actual_value,threshold_desc,severity,status,assignee_id,assignee_name,triggered_at) "
            "VALUES (:rid,:rn,:mc,:mn,:dv,:av,:td,:sv,:st,:ai,:an,:now)"
        ), {
            "rid": kw.get("rule_id"), "rn": kw.get("rule_name", ""),
            "mc": kw.get("measure_code", ""), "mn": kw.get("measure_name", ""),
            "dv": kw.get("dim_values", "{}"), "av": str(kw.get("actual_value", "")),
            "td": kw.get("threshold_desc", ""), "sv": kw.get("severity", "warning"),
            "st": kw.get("status", "open"),
            "ai": kw.get("assignee_id", ""), "an": kw.get("assignee_name", ""),
            "now": _now(),
        })
        conn.commit()
        r = conn.execute(text("SELECT * FROM alert_log ORDER BY id DESC LIMIT 1")).mappings().first()
        return dict(r) if r else None
    finally:
        conn.close()

def get_alert_log(log_id):
    conn = get_db()
    try:
        r = conn.execute(text("SELECT * FROM alert_log WHERE id=:id"), {"id": log_id}).mappings().first()
        return dict(r) if r else None
    finally:
        conn.close()

def update_alert_log(log_id, data):
    conn = get_db()
    try:
        sets, pa = [], {"id": log_id}
        for k in ("status","assignee_id","assignee_name","resolve_note","resolved_by","resolved_at","acked_at","severity"):
            if k in data: sets.append(f"{k}=:{k}"); pa[k] = data[k]
        if not sets: return
        conn.execute(text(f"UPDATE alert_log SET {','.join(sets)} WHERE id=:id"), pa)
        conn.commit()
    finally:
        conn.close()

def list_alert_logs(page=1, page_size=20, status=None, severity=None, measure_code=None, rule_id=None, from_time=None, to_time=None):
    conn = get_db()
    try:
        wh, pa = [], {}
        if status: wh.append("status=:st"); pa["st"] = status
        if severity: wh.append("severity=:sv"); pa["sv"] = severity
        if rule_id: wh.append("rule_id=:rid"); pa["rid"] = rule_id
        wc = "WHERE " + " AND ".join(wh) if wh else ""
        total = conn.execute(text(f"SELECT COUNT(*) FROM alert_log {wc}"), pa).scalar()
        pa["lim"] = page_size; pa["off"] = (page - 1) * page_size
        rows = conn.execute(text(f"SELECT * FROM alert_log {wc} ORDER BY triggered_at DESC LIMIT :lim OFFSET :off"), pa).mappings().all()
        return [dict(r) for r in rows], total
    finally:
        conn.close()

# -- Notify logs --
def insert_notify_log(**kw):
    conn = get_db()
    try:
        result = conn.execute(text(
            "INSERT INTO alert_notify_log(alert_log_id,channel,receiver_id,receiver_name,content,status,error_msg,sent_at) "
            "VALUES(:aid,:ch,:rid,:rn,:ct,:st,:em,:now)"
        ), {
            "aid": kw.get("alert_log_id"), "ch": kw.get("channel", "feishu"),
            "rid": kw.get("receiver_id", ""), "rn": kw.get("receiver_name", ""),
            "ct": kw.get("content", ""), "st": kw.get("status", "pending"),
            "em": kw.get("error_msg", ""), "now": _now(),
        })
        conn.commit()
        return result.lastrowid
    finally:
        conn.close()

def update_notify_log(log_id, status, error_msg=""):
    conn = get_db()
    try:
        conn.execute(text("UPDATE alert_notify_log SET status=:st,error_msg=:em WHERE id=:id"),
                     {"st": status, "em": error_msg, "id": log_id})
        conn.commit()
    finally:
        conn.close()

# -- Notify config --
def get_notify_config_dict():
    conn = get_db()
    try:
        r = conn.execute(text("SELECT * FROM alert_notify_config WHERE channel='feishu'")).mappings().first()
        if not r: return {}
        cfg = dict(r)
        try: cfg.update(json.loads(cfg.get("config_json", "{}")))
        except: pass
        return cfg
    finally:
        conn.close()

def upsert_notify_config(key, value):
    conn = get_db()
    try:
        existing = conn.execute(text("SELECT config_json FROM alert_notify_config WHERE channel='feishu'")).mappings().first()
        cfg = json.loads(existing["config_json"]) if existing and existing["config_json"] else {}
        if key in ("quiet_start", "quiet_end"):
            conn.execute(text(f"UPDATE alert_notify_config SET {key}=:v, updated_at=:now WHERE channel='feishu'"),
                         {"v": value, "now": _now()})
        else:
            cfg[key] = value
            conn.execute(text("UPDATE alert_notify_config SET config_json=:cfg, updated_at=:now WHERE channel='feishu'"),
                         {"cfg": json.dumps(cfg), "now": _now()})
        conn.commit()
    finally:
        conn.close()

# -- Builtin config --
def get_builtin_config(rule_type=None):
    conn = get_db()
    try:
        if rule_type:
            rows = conn.execute(text("SELECT param_key,param_value FROM alert_builtin_config WHERE param_key LIKE :k"),
                                {"k": f"{rule_type}.%"}).mappings().all()
        else:
            rows = conn.execute(text("SELECT param_key,param_value FROM alert_builtin_config")).mappings().all()
        return {r["param_key"]: r["param_value"] for r in rows}
    finally:
        conn.close()

def upsert_builtin_config(rule_type, key, value):
    conn = get_db()
    try:
        conn.execute(text(
            "REPLACE INTO alert_builtin_config(param_key,param_value,description,updated_at) VALUES(:k,:v,'',:now)"
        ), {"k": f"{rule_type}.{key}", "v": str(value), "now": _now()})
        conn.commit()
    finally:
        conn.close()

def delete_builtin_config(rule_type):
    conn = get_db()
    try:
        conn.execute(text("DELETE FROM alert_builtin_config WHERE param_key LIKE :k"), {"k": f"{rule_type}.%"})
        conn.commit()
    finally:
        conn.close()

# -- Summary / Stats --
def get_alert_summary():
    conn = get_db()
    try:
        return {
            "openCritical": conn.execute(text("SELECT COUNT(*) FROM alert_log WHERE status='open' AND severity='critical'")).scalar(),
            "openWarning": conn.execute(text("SELECT COUNT(*) FROM alert_log WHERE status='open' AND severity='warning'")).scalar(),
            "openNotice": conn.execute(text("SELECT COUNT(*) FROM alert_log WHERE status='open' AND severity='notice'")).scalar(),
        }
    finally:
        conn.close()

def get_alert_stats(days=30):
    conn = get_db()
    try:
        daily = conn.execute(text(
            "SELECT DATE(triggered_at) dt, severity, COUNT(*) cnt FROM alert_log "
            "WHERE triggered_at >= DATE_SUB(NOW(), INTERVAL :d DAY) GROUP BY 1,2 ORDER BY 1"
        ), {"d": days}).mappings().all()
        trend = {}
        for r in daily:
            trend.setdefault(str(r["dt"]), {"date": str(r["dt"]), "critical": 0, "warning": 0, "notice": 0})
            sev = r["severity"]
            if sev in ("critical", "warning", "notice"):
                trend[str(r["dt"])][sev] = r["cnt"]
        sev_map = {}
        for r in daily:
            sev_map[r["severity"]] = sev_map.get(r["severity"], 0) + r["cnt"]
        return {
            "dailyTrend": list(trend.values()),
            "bySeverity": sev_map,
            "openCritical": conn.execute(text("SELECT COUNT(*) FROM alert_log WHERE status='open' AND severity='critical'")).scalar(),
            "openWarning": conn.execute(text("SELECT COUNT(*) FROM alert_log WHERE status='open' AND severity='warning'")).scalar(),
            "openNotice": conn.execute(text("SELECT COUNT(*) FROM alert_log WHERE status='open' AND severity='notice'")).scalar(),
            "totalRules": conn.execute(text("SELECT COUNT(*) FROM alert_rule WHERE enabled=1")).scalar(),
        }
    finally:
        conn.close()

def list_severity_levels():
    conn = get_db()
    try:
        return [dict(r) for r in conn.execute(text("SELECT * FROM alert_severity_level ORDER BY sort_order")).mappings().all()]
    finally:
        conn.close()

def update_severity_level(level_id, updates):
    conn = get_db()
    try:
        sets, pa = [], {"id": level_id}
        for k in ("label", "color", "sort_order"):
            if k in updates: sets.append(f"{k}=:{k}"); pa[k] = updates[k]
        if sets: conn.execute(text(f"UPDATE alert_severity_level SET {','.join(sets)} WHERE id=:id"), pa); conn.commit()
    finally:
        conn.close()

def last_trigger_time_for_rule(rule_id):
    conn = get_db()
    try:
        r = conn.execute(text("SELECT triggered_at FROM alert_log WHERE rule_id=:id ORDER BY triggered_at DESC LIMIT 1"),
                         {"id": rule_id}).mappings().first()
        if r and r["triggered_at"]:
            try: return datetime.fromisoformat(str(r["triggered_at"]))
            except: return None
        return None
    finally:
        conn.close()

def list_enabled_rules():
    conn = get_db()
    try:
        return [dict(r) for r in conn.execute(text("SELECT * FROM alert_rule WHERE enabled=1 ORDER BY id")).mappings().all()]
    finally:
        conn.close()
