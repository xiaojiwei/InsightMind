"""
web_app.py — FastAPI web interface for the KG Builder.

Usage:
  pip install fastapi uvicorn[standard] jinja2 python-multipart
  python web_app.py
  # Open http://localhost:8000
"""
from __future__ import annotations

import asyncio
import datetime
import math
import functools
import json
import logging
import os
import queue
import re
import shutil
import threading
import time
import uuid
from collections import deque
from pathlib import Path
from typing import Any, Optional

import yaml
from fastapi import FastAPI, Request
from fastapi.responses import (
    FileResponse,
    HTMLResponse,
    JSONResponse,
    StreamingResponse,
)
from fastapi.templating import Jinja2Templates
from pydantic import BaseModel, Field

from kg_builder.utils.runtime_env import load_runtime_env

# ── App setup ───────────────────────────────────────────────────────────── #

BASE_DIR  = Path(__file__).parent
load_runtime_env(BASE_DIR / ".env")
TEMPLATES = Jinja2Templates(directory=str(BASE_DIR / "kg_builder" / "web" / "templates"))
# ── Alert management ──────────────────────────────────────────────────────── #
from kg_builder.alerts import alerts_router, init_db, get_db
from kg_builder.alerts import scheduler as alert_scheduler
from kg_builder.feedback import (
    FeedbackObservationMiddleware,
    begin_query_trace as _feedback_begin_query_trace,
    complete_query_trace as _feedback_complete_query_trace,
    feedback_router,
    init_feedback_store,
    record_schema_snapshot as _feedback_record_schema_snapshot,
)
from kg_builder.insights import (
    configure_insight_runtime,
    init_insight_store,
    insights_router,
)
from kg_builder.semantic_retrieval import get_semantic_mapping_service
from kg_builder.semantic_retrieval.router import create_semantic_retrieval_router
from kg_builder.utils.http_client import urlopen as _urlopen

# app.include_router(alerts_router)  -- deferred after app creation

OUTPUT_DIR = BASE_DIR / "output"
OUTPUT_DIR.mkdir(exist_ok=True)
BKG_DIR = OUTPUT_DIR / "business_kg"
BKG_DIR.mkdir(exist_ok=True)
DEFAULT_BKG_SCENARIO_PATH = (
    BASE_DIR / "kg_builder" / "business_kg" / "default-business-scenario.ttl"
)
ADHOC_DIR = OUTPUT_DIR / "adhoc"
ADHOC_DIR.mkdir(exist_ok=True)
DASHBOARD_DIR = OUTPUT_DIR / "dashboards"
DASHBOARD_DIR.mkdir(exist_ok=True)
INSIGHT_ACTION_DIR = OUTPUT_DIR / "insight_actions"
INSIGHT_ACTION_DIR.mkdir(exist_ok=True)
SEMANTIC_DIR = OUTPUT_DIR / "semantic"
SEMANTIC_DIR.mkdir(exist_ok=True)
FORMULA_REGISTRY_PATH = SEMANTIC_DIR / "formulas.json"
SEMANTIC_SOURCE_MANIFEST_PATH = BKG_DIR / "indicator-data.source.json"
DEMO_OUTPUT_DIR = BASE_DIR.parents[1] / "demo" / "default" / "ad" / "output"
# KG files are named kg_YYYYMMDD_NNN.ttl; legacy kg.ttl is still auto-detected
_current_kg_path: Optional[Path] = None


def _bkg_inferred_path() -> Path:
    return BKG_DIR / "indicator-inferred.ttl"


def _materialize_business_inferences(turtle_str: str, log_cb=None):
    """Generate deterministic inferred triples for the active business KG."""
    from kg_builder.business_kg.reasoner import BusinessKGReasoner

    log = log_cb or (lambda _msg: None)
    inferred = BusinessKGReasoner(log_cb=log).infer_from_turtle(turtle_str)
    out = _bkg_inferred_path()
    out.write_text(inferred.serialize(format="turtle"), encoding="utf-8")
    log(f"[推理] 已保存 {out.name}: {len(inferred)} 条三元组")
    return inferred


def _load_business_graph(file: str = "", include_inferred: bool = True):
    """Load the active business KG, optionally merged with materialized inferences."""
    from rdflib import Graph

    if file:
        p = (BKG_DIR / Path(file).name).resolve()
        if not str(p).startswith(str(BKG_DIR.resolve())) or not p.exists():
            raise FileNotFoundError("业务图谱文件不存在")
    else:
        p = BKG_DIR / "indicator-data.ttl"
        if not p.exists():
            raise FileNotFoundError("尚未生成业务图谱")

    graph = Graph()
    graph.parse(str(p), format="turtle")
    inferred = _bkg_inferred_path()
    if include_inferred and inferred.exists():
        graph.parse(str(inferred), format="turtle")
    return graph


def _seed_demo_assets() -> None:
    """Restore the checked-in default demo into runtime output when missing."""
    if not DEMO_OUTPUT_DIR.exists():
        return

    for src in DEMO_OUTPUT_DIR.rglob("*"):
        if not src.is_file():
            continue
        rel = src.relative_to(DEMO_OUTPUT_DIR)
        dst = OUTPUT_DIR / rel
        if dst.exists():
            continue
        dst.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(src, dst)


_seed_demo_assets()


def _next_kg_path() -> Path:
    """Return next available filename: kg_YYYYMMDD_NNN.ttl"""
    from datetime import date
    today = date.today().strftime("%Y%m%d")
    existing = sorted(OUTPUT_DIR.glob(f"kg_{today}_*.ttl"))
    seq = (int(existing[-1].stem.rsplit("_", 1)[-1]) + 1) if existing else 1
    return OUTPUT_DIR / f"kg_{today}_{seq:03d}.ttl"


def _next_bkg_path() -> Path:
    """Return the fixed business KG filename: business_kg/indicator-data.ttl"""
    return BKG_DIR / "indicator-data.ttl"


def _archive_bkg() -> Optional[Path]:
    """将当前 indicator-data.ttl 归档为带时间戳的历史版本，返回归档路径。"""
    current = BKG_DIR / "indicator-data.ttl"
    if not current.exists():
        return None
    today = time.strftime("%Y%m%d")
    existing = sorted(BKG_DIR.glob(f"indicator-data-{today}-*.ttl"))
    seq = (int(existing[-1].stem.rsplit("-", 1)[-1]) + 1) if existing else 1
    archive = BKG_DIR / f"indicator-data-{today}-{seq:03d}.ttl"
    current.rename(archive)
    if SEMANTIC_SOURCE_MANIFEST_PATH.exists():
        archive_manifest = BKG_DIR / f"{archive.stem}.source.json"
        SEMANTIC_SOURCE_MANIFEST_PATH.replace(archive_manifest)
    return archive


def _semantic_source_path() -> Optional[Path]:
    """Resolve the source KG explicitly bound to the active business KG.

    A newest-file heuristic can cross business domains after a cold restart, so
    an unbound legacy BKG deliberately gets no source samples.
    """
    configured = os.getenv("INSIGHTMIND_SEMANTIC_SOURCE_TTL", "").strip()
    if configured:
        candidate = Path(configured).expanduser().resolve()
        return candidate if candidate.is_file() else None
    if not SEMANTIC_SOURCE_MANIFEST_PATH.exists():
        return None
    try:
        payload = json.loads(SEMANTIC_SOURCE_MANIFEST_PATH.read_text(encoding="utf-8"))
        source_name = str(payload.get("sourceKg") or "").strip()
        expected_business_hash = str(payload.get("businessKgSha256") or "").strip()
        expected_source_hash = str(payload.get("sourceKgSha256") or "").strip()
    except (OSError, ValueError, TypeError):
        return None
    if not source_name or not expected_business_hash or not expected_source_hash:
        return None
    candidate = (OUTPUT_DIR / Path(source_name).name).resolve()
    if candidate.parent != OUTPUT_DIR.resolve() or not candidate.is_file():
        return None
    business_path = BKG_DIR / "indicator-data.ttl"
    if not business_path.is_file():
        return None
    try:
        from kg_builder.feedback.graph_version import graph_identity

        business_hash = str(graph_identity(business_path).get("sha256") or "")
        source_hash = str(graph_identity(candidate).get("sha256") or "")
    except Exception:
        return None
    if business_hash != expected_business_hash or source_hash != expected_source_hash:
        logging.getLogger("uvicorn").warning(
            "[Semantic] BKG/source manifest hash mismatch; source value samples disabled"
        )
        return None
    return candidate


def _write_semantic_source_manifest(source_path: Optional[Path]) -> None:
    if source_path is None:
        SEMANTIC_SOURCE_MANIFEST_PATH.unlink(missing_ok=True)
        return
    business_path = BKG_DIR / "indicator-data.ttl"
    if not business_path.is_file() or not source_path.is_file():
        SEMANTIC_SOURCE_MANIFEST_PATH.unlink(missing_ok=True)
        return
    from kg_builder.feedback.graph_version import graph_identity

    payload = {
        "businessKg": "indicator-data.ttl",
        "sourceKg": source_path.name,
        "businessKgSha256": graph_identity(business_path).get("sha256") or "",
        "sourceKgSha256": graph_identity(source_path).get("sha256") or "",
        "boundAt": time.strftime("%Y-%m-%d %H:%M:%S"),
    }
    SEMANTIC_SOURCE_MANIFEST_PATH.write_text(
        json.dumps(payload, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )


def _get_active_path() -> Optional[Path]:
    """Return the currently selected KG file, or auto-detect the most recent one."""
    global _current_kg_path
    if _current_kg_path and _current_kg_path.exists():
        return _current_kg_path
    files = sorted(OUTPUT_DIR.glob("kg_*.ttl"), key=lambda p: p.stat().st_mtime)
    if files:
        _current_kg_path = files[-1]
        return _current_kg_path
    legacy = OUTPUT_DIR / "kg.ttl"
    if legacy.exists():
        _current_kg_path = legacy
        return legacy
    return None


def _semantic_mapping_service(
    ttl_path: Path | None = None,
    source_ttl_path: Path | None = None,
):
    """Return the shared, mtime-aware semantic index used by NLQ and Insight."""
    return get_semantic_mapping_service(
        ttl_path or (BKG_DIR / "indicator-data.ttl"),
        source_ttl_path if source_ttl_path is not None else _semantic_source_path(),
        log_cb=lambda message: logging.getLogger("uvicorn").info(message),
    )

app = FastAPI(title="KG Builder Web UI")
app.add_middleware(FeedbackObservationMiddleware)
app.include_router(alerts_router)
app.include_router(feedback_router)
app.include_router(insights_router)
app.include_router(create_semantic_retrieval_router(_semantic_mapping_service))

# ── Shared state ────────────────────────────────────────────────────────── #

_build_state: dict[str, Any] = {
    "status": "idle",       # idle | running | done | error
    "message": "",
    "progress": 0,
    "kg_file": "",          # filename of the last successfully built KG
}
_log_queue: queue.Queue = queue.Queue()
_sparql_api = None          # SPARQLApi instance after successful build
_path_finder = None         # JoinPathFinder instance (lazy, rebuilt after each build)
_quality_results = None     # List[dict] from last FK integrity check
_state_lock  = threading.Lock()

# Business KG state
_bkg_state: dict[str, Any] = {"status": "idle", "message": ""}
_bkg_log_queue: queue.Queue = queue.Queue()
_bkg_turtle: str = ""          # Last generated business KG turtle
_bkg_graph = None              # rdflib.Graph of business KG
_current_bkg_path: Optional[Path] = None  # Active business KG file
_last_datasource: Optional[dict] = None   # Last successful data-source DSConfig
_last_ref_cfg: Optional[dict] = None      # Last used reference indicator DB config
_bkg_meta: dict[str, Any] = {}            # Last build: gen_time, source_kg

def _set_state(status: str, message: str = "", progress: int = 0) -> None:
    with _state_lock:
        _build_state["status"]   = status
        _build_state["message"]  = message
        _build_state["progress"] = progress


def _log(msg: str) -> None:
    """Push a log line to the SSE queue."""
    _log_queue.put(msg)


class _QueueLogHandler(logging.Handler):
    """Forward Python logging records to the SSE log queue."""
    def emit(self, record: logging.LogRecord) -> None:
        try:
            _log_queue.put(self.format(record))
        except Exception:
            pass

# Attach handler to modules that produce progress logs
_queue_handler = _QueueLogHandler()
_queue_handler.setFormatter(logging.Formatter("%(message)s"))
for _mod in ("kg_builder.utils.translator", "kg_builder.entities.extractor",
             "kg_builder.parsers.data_sampler", "kg_builder.relations.implicit"):
    logging.getLogger(_mod).addHandler(_queue_handler)
    logging.getLogger(_mod).setLevel(logging.INFO)


# ── Pydantic models ─────────────────────────────────────────────────────── #

class DSConfig(BaseModel):
    name:            str  = "da_tms_local"
    db_type:         str  = "mysql"
    host:            str  = "127.0.0.1"
    port:            int  = 3306
    database:        str  = "da_tms"
    username:        str  = "root"
    password:        str  = "123456"
    schema_name:     str  = "da_tms"
    service_name:    str  = ""
    sid:             str  = ""
    windows_auth:    bool = False
    driver:          str  = ""
    sample_limit:    int  = 1000
    exclude_tables:  list[str] = []
    all_databases:   bool = False  # keep the demo scoped to the selected database/schema

class BuildRequest(BaseModel):
    datasource:             DSConfig
    enable_sampling:        bool  = True
    enable_implicit:        bool  = False
    enable_reasoning:       bool  = False
    similarity_threshold:   float = Field(default=0.85, ge=0.0, le=1.0, allow_inf_nan=False)
    synonyms_path:          str   = "synonyms.yaml"

class PresetQueryRequest(BaseModel):
    query_type: str   # find_related | similar_columns | table_schema | fk_graph | potential_joins | search_comment | find_individuals
    param:      str = ""
    param2:     str = ""

class SparqlRequest(BaseModel):
    sparql: str

class SelectGraphRequest(BaseModel):
    filename: str

class NLQRequest(BaseModel):
    question: str
    execute: bool = True
    pageSize: int = 100
    pageNum: int = 1
    maxDimensions: int = 3
    queryMode: str = "auto"  # auto | aggregate | detail | explain | analyze_detail
    conversationId: str = ""
    context: dict[str, Any] = Field(default_factory=dict)
    isFollowUp: bool = False
    resetContext: bool = False
    parentTraceId: str = ""
    source: str = "nlq"

class EntityLookupRequest(BaseModel):
    question: str
    pageSize: int = 500
    pageNum: int = 1

class NLQInterpretRequest(BaseModel):
    question: str = ""
    queryMode: str = ""
    matched: dict[str, Any] = Field(default_factory=dict)
    resultSummary: dict[str, Any] = Field(default_factory=dict)
    graphContext: dict[str, Any] = Field(default_factory=dict)
    validation: dict[str, Any] = Field(default_factory=dict)
    evidence: list[dict[str, Any]] = Field(default_factory=list)
    crossValidation: dict[str, Any] = Field(default_factory=dict)


# ── Helper: make connector ───────────────────────────────────────────────── #

def _make_connector(ds: DSConfig):
    from kg_builder.connectors.base import DataSourceConfig

    cfg = DataSourceConfig(
        name=ds.name,
        db_type=ds.db_type,
        host=ds.host,
        port=ds.port,
        database=ds.database,
        username=ds.username,
        password=ds.password,
        service_name=ds.service_name,
        sid=ds.sid,
        windows_auth=ds.windows_auth,
        driver=ds.driver,
        sample_limit=ds.sample_limit,
        exclude_tables=ds.exclude_tables,
    )

    db_type = ds.db_type.lower()
    if db_type in ("mysql", "doris", "starrocks"):
        from kg_builder.connectors.mysql import MySQLConnector
        return MySQLConnector(cfg)
    elif db_type in ("mssql", "sqlserver"):
        from kg_builder.connectors.mssql import MSSQLConnector
        return MSSQLConnector(cfg)
    elif db_type == "oracle":
        from kg_builder.connectors.oracle import OracleConnector
        return OracleConnector(cfg)
    elif db_type == "sqlite":
        from sqlalchemy import create_engine
        from kg_builder.connectors.base import BaseConnector

        class _SQLiteConnector(BaseConnector):
            def _build_url(self):
                return f"sqlite:///{self.config.database}"

        return _SQLiteConnector(cfg)
    else:
        raise ValueError(f"Unsupported db_type: {ds.db_type}")


# ── Background build worker ──────────────────────────────────────────────── #

def _build_worker(req: BuildRequest) -> None:
    global _sparql_api, _current_kg_path, _path_finder, _quality_results

    # ── Step helpers ──────────────────────────────────────────────────── #
    STEPS = [
        "连接数据库",
        "解析 Schema（表结构）",
        "数据采样",
        "提取实体",
        "表类型分类",
        "逻辑外键检测",
        "注释引用扫描",
        "IND 包含依赖检测",
        "枚举值对齐",
        "提取显式关系",
        "语义隐式关系",
        "构建 RDF/OWL 图谱",
        "保存图谱文件",
        "加载 SPARQL 引擎",
        "FK 完整性检测",
    ]

    def plan():
        import time
        _log("__PLAN__:" + "|".join(STEPS))
        _log(f"── 本次构建共 {len(STEPS)} 步 ──")
        for i, s in enumerate(STEPS, 1):
            _log(f"  {i:2d}. {s}")
        _log("─" * 30)
        time.sleep(0.5)  # ensure frontend renders all steps before first __STEP__

    def step(n: int, detail: str = ""):
        _log(f"__STEP__:{n}" + (f":{detail}" if detail else ""))

    def skip(n: int):
        _log(f"__STEP__:{n}:已跳过（未启用）")

    try:
        _set_state("running", "开始构建…", 5)
        plan()

        # Imports
        from kg_builder.parsers.schema_parser import SchemaParser
        from kg_builder.parsers.data_sampler  import DataSampler
        from kg_builder.entities.extractor    import EntityExtractor
        from kg_builder.relations.explicit    import ExplicitRelationExtractor
        from kg_builder.relations.implicit    import ImplicitRelationExtractor
        from kg_builder.ontology.rdf_builder  import RDFBuilder
        from kg_builder.query.sparql_api      import SPARQLApi

        ds = req.datasource

        # ── Step 1: 连接数据库 ─────────────────────────────────────────── #
        db_label = ds.database or "（全部数据库）"
        _log(f"连接 [{ds.db_type}] {ds.host}:{ds.port}/{db_label} …")
        _set_state("running", "连接数据库…", 10)
        connector = _make_connector(ds)
        if not connector.test_connection():
            raise RuntimeError("数据库连接失败，请检查配置。")
        step(1, f"{ds.db_type} {ds.host}")

        # ── Step 2: 解析 Schema ────────────────────────────────────────── #
        _set_state("running", "解析 Schema…", 20)
        parser = SchemaParser(connector)
        if ds.all_databases:
            _log("模式：扫描连接中所有非系统数据库 …")
            schema_info = parser.parse_all_databases(log_fn=_log)
            step(2, f"{len(schema_info.tables)} 张表（全库扫描）")
        else:
            schema_name = ds.schema_name or None
            _log(f"解析 Schema '{schema_name or ds.database}' …")
            schema_info = parser.parse(schema_name=schema_name)
            step(2, f"{len(schema_info.tables)} 张表")

        # ── Step 3: 数据采样 ───────────────────────────────────────────── #
        if req.enable_sampling:
            n_tables = len(schema_info.tables)
            _log(f"采样数据并统计列信息（共 {n_tables} 张表）…")
            _set_state("running", "数据采样…", 30)
            sampler = DataSampler(connector, limit=ds.sample_limit)
            # Patch sampler to stream per-table progress to log
            _orig_sample = sampler._sample_table
            _counter = [0]
            def _logged_sample(table, schema):
                _counter[0] += 1
                _log(f"  [{_counter[0]}/{n_tables}] 采样 {schema or ''}.{table.name}")
                _orig_sample(table, schema)
            sampler._sample_table = _logged_sample
            schema_info = sampler.sample_schema(schema_info)
            step(3, f"limit={ds.sample_limit}")
        else:
            skip(3)

        # ── Step 4: 提取实体 ───────────────────────────────────────────── #
        _log(f"提取实体（{len(schema_info.tables)} 张表，LLM 翻译表名中…）…")
        _set_state("running", "提取实体…", 40)
        entity_extractor = EntityExtractor(synonyms_path=req.synonyms_path)
        entity_graph     = entity_extractor.extract(schema_info)
        step(4, f"{len(entity_graph.tables)} 表  {len(entity_graph.columns)} 列  {len(entity_graph.individuals)} 个体")

        # ── Step 5: 表类型分类 ─────────────────────────────────────────── #
        from kg_builder.analytics.table_classifier import classify as classify_tables
        classify_tables(entity_graph)
        from collections import Counter
        cat_counts = Counter(t.table_category for t in entity_graph.tables)
        step(5, "  ".join(f"{k}={v}" for k, v in sorted(cat_counts.items())))

        # ── Step 6: 逻辑外键检测 ───────────────────────────────────────── #
        _log("检测逻辑外键（名称+值采样）…")
        _set_state("running", "逻辑外键检测…", 50)
        from kg_builder.relations.fk_detector import LogicalFKDetector, inject_logical_fks
        detector     = LogicalFKDetector()
        detected_fks = detector.detect(entity_graph, schema_info)
        injected     = inject_logical_fks(detected_fks, entity_graph)
        if detected_fks:
            for fk in detected_fks[:5]:
                _log(f"  [{fk.method} {fk.confidence:.0%}] {fk.from_table}.{fk.from_column} → {fk.to_table}.{fk.to_column}")
            if len(detected_fks) > 5:
                _log(f"  … 共 {len(detected_fks)} 条，仅显示前5条")
        step(6, f"{len(detected_fks)} 候选，注入 {injected} 条")

        # ── Step 7: 注释引用扫描 ───────────────────────────────────────── #
        from kg_builder.relations.comment_ref_detector import (
            CommentRefDetector, comment_refs_to_detected_fks,
        )
        comment_refs     = CommentRefDetector().detect(entity_graph)
        comment_fks      = comment_refs_to_detected_fks(comment_refs, entity_graph)
        comment_injected = inject_logical_fks(comment_fks, entity_graph)
        col_refs = [r for r in comment_refs if r.from_column]
        tbl_refs = [r for r in comment_refs if not r.from_column]
        step(7, f"{len(col_refs)} 列级引用，{len(tbl_refs)} 表级引用，注入 {comment_injected} 条FK")

        # ── Step 8: IND 包含依赖检测 ───────────────────────────────────── #
        from kg_builder.relations.inclusion_dep import InclusionDepDetector
        ind_fks      = InclusionDepDetector().detect(entity_graph, schema_info)
        ind_injected = inject_logical_fks(ind_fks, entity_graph)
        step(8, f"{len(ind_fks)} 候选，注入 {ind_injected} 条")

        # ── Step 9: 枚举值对齐 ────────────────────────────────────────── #
        from kg_builder.relations.enum_detector import EnumDetector, enum_alignments_to_relations
        enum_alignments = EnumDetector().detect(entity_graph)
        enum_rels       = enum_alignments_to_relations(enum_alignments)
        relations_base  = []
        relations_base.extend(enum_rels)
        if enum_alignments:
            for aln in enum_alignments[:3]:
                _log(f"  [{aln.relation_type} {aln.confidence:.0%}] "
                     f"{aln.table_a}.{aln.col_a_name} ↔ {aln.table_b}.{aln.col_b_name}")
        step(9, f"{len(enum_alignments)} 对，{len(enum_rels)} 条 sharedEnum 关系")

        # ── Step 10: 提取显式关系 ──────────────────────────────────────── #
        _log("提取显式关系（外键等）…")
        _set_state("running", "提取关系…", 60)
        explicit_extractor = ExplicitRelationExtractor()
        relations          = explicit_extractor.extract(entity_graph)
        relations.extend(relations_base)
        step(10, f"{len(relations)} 条显式关系")

        # ── Step 11: 隐式关系（确定性规则 + 可选 LLM）────────────────── #
        _log("发现隐式关系（命名与统计规则）…")
        if req.enable_implicit:
            _log("通过已配置大模型补充 AI 语义关系…")
        _set_state("running", "关系分析…", 70)
        implicit_extractor = ImplicitRelationExtractor(
            similarity_threshold=req.similarity_threshold,
            enable_llm_semantics=req.enable_implicit,
        )
        implicit_rels = implicit_extractor.extract(entity_graph)
        relations.extend(implicit_rels)
        step(11, f"{len(implicit_rels)} 条隐式关系")

        # ── Step 12: 构建 RDF/OWL 图谱 ────────────────────────────────── #
        _log("构建 RDF/OWL 图谱…")
        _set_state("running", "构建 RDF 图…", 78)
        builder = RDFBuilder(include_owl_schema=True)
        builder.build(entity_graph, relations)
        if req.enable_reasoning:
            _log("应用 OWL-RL 推理…")
            _set_state("running", "OWL 推理…", 85)
            builder.apply_reasoning()
        step(12, f"{len(builder.graph)} 条三元组")

        # ── Step 13: 保存图谱文件 ──────────────────────────────────────── #
        kg_path = _next_kg_path()
        _log(f"保存 → {kg_path.name} …")
        _set_state("running", "保存文件…", 90)
        builder.save(str(kg_path), fmt="turtle")
        builder.save(str(OUTPUT_DIR / "kg.ttl"), fmt="turtle")
        triple_count = len(builder.graph)
        step(13, kg_path.name)

        # ── Step 14: 加载 SPARQL 引擎 ─────────────────────────────────── #
        _log("加载 SPARQL 查询引擎…")
        _sparql_api      = SPARQLApi.from_file(str(kg_path))
        _path_finder     = None
        _current_kg_path = kg_path
        with _state_lock:
            _build_state["kg_file"] = kg_path.name
        # Persist datasource config for business KG ETL step
        global _last_datasource
        _last_datasource = {
            "host": ds.host, "port": ds.port, "database": ds.database,
            "username": ds.username, "password": ds.password,
            "all_databases": ds.all_databases,
        }
        step(14)

        # ── Step 15: FK 完整性检测 ─────────────────────────────────────── #
        _log("FK 完整性检测（基于采样数据）…")
        from kg_builder.quality.fk_checker import FKIntegrityChecker, violations_to_dict
        violations       = FKIntegrityChecker().check(entity_graph, schema_info)
        _quality_results = violations_to_dict(violations)
        if violations:
            for v in violations[:3]:
                _log(f"  ⚠ {v.from_table}.{v.from_column} → {v.to_table}"
                     f" 违规率 {v.violation_rate:.1%}")
            if len(violations) > 3:
                _log(f"  … 共 {len(violations)} 条")
        step(15, f"{len(violations)} 处问题" if violations else "无问题")

        # Feedback observation is deliberately stored outside the RDF graphs.
        # Only a credential-free structural snapshot is persisted here.
        feedback_snapshot = _feedback_record_schema_snapshot(schema_info)
        if feedback_snapshot.get("ok") and not feedback_snapshot.get("disabled"):
            _log(
                "[反馈观测] 元数据快照已记录"
                + (f"，发现 {feedback_snapshot.get('changeCount', 0)} 处结构变化" if not feedback_snapshot.get("unchanged") else "，结构无变化")
            )

        connector.close()
        _log("=== 构建完成 ===")
        _set_state("done", f"完成，共 {triple_count} 条三元组", 100)

    except Exception as exc:
        _log(f"[错误] {exc}")
        _set_state("error", str(exc), 0)


# ── Routes ───────────────────────────────────────────────────────────────── #

@app.get("/", response_class=HTMLResponse)
async def index(request: Request):
    response = TEMPLATES.TemplateResponse(request, "index.html")
    # The UI contains inline JavaScript. Never serve a stale validation client
    # after a deploy/restart, otherwise an old selector can keep sending the
    # source KG filename to business-KG endpoints.
    response.headers["Cache-Control"] = "no-store, no-cache, must-revalidate, max-age=0"
    response.headers["Pragma"] = "no-cache"
    return response



@app.get("/alerts", response_class=HTMLResponse)
async def alerts_page(request: Request):
    return TEMPLATES.TemplateResponse(request, "alerts.html")


@app.get("/feedback", response_class=HTMLResponse)
async def feedback_page(request: Request):
    response = TEMPLATES.TemplateResponse(request, "feedback.html")
    response.headers["Cache-Control"] = "no-store, no-cache, must-revalidate, max-age=0"
    return response


@app.get("/dashboard/view/{item_id}", response_class=HTMLResponse)
async def dashboard_view(request: Request, item_id: str):
    if not _artifact_path(DASHBOARD_DIR, item_id).exists():
        return HTMLResponse("Dashboard 不存在或不属于当前激活图谱", status_code=404)
    # The dashboard template contains its interaction logic inline.  Serving a
    # stale copy leaves users on an older drill-down flow after a local restart.
    return TEMPLATES.TemplateResponse(
        request,
        "index.html",
        headers={"Cache-Control": "no-store, max-age=0"},
    )


@app.post("/api/connect-test")
async def connect_test(ds: DSConfig):
    try:
        connector = _make_connector(ds)
        ok = connector.test_connection()
        connector.close()
        if ok:
            return {"ok": True, "message": "连接成功"}
        return JSONResponse(status_code=400, content={"ok": False, "message": "连接失败"})
    except Exception as e:
        return JSONResponse(status_code=400, content={"ok": False, "message": str(e)})


@app.post("/api/build")
async def start_build(req: BuildRequest):
    with _state_lock:
        if _build_state["status"] == "running":
            return JSONResponse(status_code=409,
                                content={"error": "构建正在进行中，请等待完成。"})

    # Clear queue
    while not _log_queue.empty():
        try:
            _log_queue.get_nowait()
        except queue.Empty:
            break

    _set_state("running", "启动中…", 0)
    thread = threading.Thread(target=_build_worker, args=(req,), daemon=True)
    thread.start()
    return {"ok": True, "message": "构建已启动"}


@app.get("/api/build/status")
async def build_status():
    with _state_lock:
        return dict(_build_state)


@app.get("/api/build/stream")
async def build_stream():
    """SSE endpoint for real-time log streaming."""
    async def event_generator():
        while True:
            # Drain all pending log lines
            lines = []
            try:
                while True:
                    lines.append(_log_queue.get_nowait())
            except queue.Empty:
                pass

            for line in lines:
                yield f"data: {json.dumps({'log': line})}\n\n"

            # Also push current state
            with _state_lock:
                state = dict(_build_state)
            yield f"data: {json.dumps({'state': state})}\n\n"

            if state["status"] in ("done", "error"):
                yield "data: {\"done\": true}\n\n"
                return

            await asyncio.sleep(0.4)

    return StreamingResponse(event_generator(),
                             media_type="text/event-stream",
                             headers={"Cache-Control": "no-cache",
                                      "X-Accel-Buffering": "no"})


@app.get("/api/graph/download")
async def graph_download():
    active = _get_active_path()
    if not active:
        return JSONResponse(status_code=404, content={"error": "图谱文件不存在，请先构建。"})
    return FileResponse(str(active), filename=active.name, media_type="text/turtle")


@app.get("/api/graph/nodes")
async def graph_nodes():
    """Return vis.js-compatible nodes and edges (max 200 nodes)."""
    global _sparql_api
    if _sparql_api is None:
        active = _get_active_path()
        if active:
            from kg_builder.query.sparql_api import SPARQLApi
            _sparql_api = SPARQLApi.from_file(str(active))
        else:
            return JSONResponse(status_code=404,
                                content={"error": "图谱尚未构建。"})

    from rdflib import URIRef, RDF
    from rdflib.namespace import RDFS
    from kg_builder.ontology.owl_schema import DB
    import re
    _ZH_RE = re.compile(r'[\u4e00-\u9fff]')

    g = _sparql_api._g
    nodes: list[dict] = []
    edges: list[dict] = []
    node_ids: set[str] = set()
    MAX_TABLES = 300      # show all tables
    MAX_COLUMNS = 100     # limit columns to keep graph readable
    MAX_IND_PER_TABLE = 3 # show up to 3 individuals per table (covers all data tables)

    def _uri_tail(uri) -> str:
        s = str(uri)
        return s.split("#")[-1].split("/")[-1]

    def _zh_label(uri):
        """Return best Chinese label: rdfs:label@zh → Chinese-char rdfs:label → None."""
        for lbl in g.objects(uri, RDFS.label):
            if getattr(lbl, "language", None) == "zh":
                return str(lbl)
        for lbl in g.objects(uri, RDFS.label):
            if _ZH_RE.search(str(lbl)):
                return str(lbl)
        return None

    def _display_label(uri, name_prop=None) -> str:
        """Chinese label when available, fallback to name property or URI tail."""
        zh = _zh_label(uri)
        if zh:
            return zh
        if name_prop:
            v = g.value(uri, name_prop)
            if v:
                return str(v)
        return _uri_tail(uri)

    def _node_title(uri, zh_lbl, orig_name) -> str:
        """Tooltip: 中文名 | 原始名 | 注释"""
        parts = []
        if zh_lbl:
            parts.append(zh_lbl)
        if orig_name and orig_name != zh_lbl:
            parts.append(orig_name)
        comment = g.value(uri, DB.comment)
        if comment and str(comment) not in parts:
            parts.append(str(comment))
        return " | ".join(parts) if parts else str(uri)

    # Tables — prioritise tables that have individual nodes
    # First pass: collect all table URIs and which ones have individuals
    all_table_uris = list(g.subjects(RDF.type, DB.Table))
    tables_with_ind = set()
    for ind_uri in g.subjects(RDF.type, DB.Individual):
        for tbl_uri in g.subjects(DB.hasIndividual, ind_uri):
            tables_with_ind.add(str(tbl_uri))

    # Sort: tables with individuals first, then the rest
    all_table_uris.sort(key=lambda s: (0 if str(s) in tables_with_ind else 1))

    # Pre-build schema→db lookup for provenance
    schema_to_db: dict[str, str] = {}
    db_names: dict[str, str] = {}
    for db_uri in g.subjects(RDF.type, DB.Database):
        db_name_val = str(g.value(db_uri, DB.name) or _uri_tail(db_uri))
        db_names[str(db_uri)] = db_name_val
        for schema_uri in g.objects(db_uri, DB.containsSchema):
            schema_to_db[str(schema_uri)] = str(db_uri)
    schema_names: dict[str, str] = {}
    for schema_uri in g.subjects(RDF.type, DB.Schema):
        schema_names[str(schema_uri)] = str(g.value(schema_uri, DB.name) or _uri_tail(schema_uri))
    # table→schema lookup
    table_to_schema: dict[str, str] = {}
    for schema_uri in g.subjects(RDF.type, DB.Schema):
        for tbl_uri in g.objects(schema_uri, DB.containsTable):
            table_to_schema[str(tbl_uri)] = str(schema_uri)

    for s in all_table_uris[:MAX_TABLES]:
        nid = str(s)
        if nid not in node_ids:
            node_ids.add(nid)
            orig = str(g.value(s, DB.tableName) or g.value(s, DB.name) or _uri_tail(s))
            zh = _zh_label(s)
            label = zh if zh else orig
            category = str(g.value(s, DB.tableCategory) or "unknown")
            row_count = g.value(s, DB.rowCount)
            fk_out = g.value(s, DB.fkOutCount)
            fk_in  = g.value(s, DB.fkInCount)
            schema_uri = table_to_schema.get(nid, "")
            schema_name = schema_names.get(schema_uri, "")
            db_uri = schema_to_db.get(schema_uri, "")
            db_name = db_names.get(db_uri, "")
            source = {
                "数据库": db_name,
                "Schema": schema_name,
                "表名": orig,
                "类别": category,
                "行数": int(row_count) if row_count is not None else None,
                "外键出": int(fk_out) if fk_out is not None else 0,
                "外键入": int(fk_in) if fk_in is not None else 0,
            }
            nodes.append({
                "id": nid,
                "label": label,
                "orig": orig,
                "group": f"table_{category}" if category != "unknown" else "table",
                "category": category,
                "source": source,
                "title": _node_title(s, zh, orig),
            })

    # Columns (limited to keep graph readable)
    col_count = 0
    for s in g.subjects(RDF.type, DB.Column):
        if col_count >= MAX_COLUMNS:
            break
        nid = str(s)
        if nid not in node_ids:
            node_ids.add(nid)
            orig = str(g.value(s, DB.name) or _uri_tail(s))
            zh = _zh_label(s)
            label = zh if zh else orig
            nodes.append({
                "id": nid,
                "label": label,
                "orig": orig,
                "group": "column",
                "title": _node_title(s, zh, orig),
            })
            col_count += 1

    # Individuals — per-table even distribution so every data table is represented.
    # Collect all individuals grouped by their parent table URI.
    from collections import defaultdict
    table_to_inds: dict = defaultdict(list)
    for ind_uri in g.subjects(RDF.type, DB.Individual):
        parent = None
        for p in g.subjects(DB.hasIndividual, ind_uri):
            parent = str(p)
            break
        if parent is None:
            parts = str(ind_uri).split("/")
            try:
                idx = parts.index("individual")
                parent = parts[idx + 1] if idx + 1 < len(parts) else "unknown"
            except ValueError:
                parent = "unknown"
        table_to_inds[parent].append(ind_uri)

    # Tables with individuals come first (already sorted above), then others
    ordered_keys = (
        [k for k in table_to_inds if k in tables_with_ind] +
        [k for k in table_to_inds if k not in tables_with_ind]
    )
    for tbl_key in ordered_keys:
        for ind_uri in table_to_inds[tbl_key][:MAX_IND_PER_TABLE]:
            nid = str(ind_uri)
            if nid in node_ids:
                continue
            node_ids.add(nid)
            lbl_val = g.value(ind_uri, RDFS.label)
            orig = str(lbl_val) if lbl_val else nid.split("/")[-1]
            zh = _zh_label(ind_uri)
            label = zh if zh else orig
            nodes.append({
                "id": nid,
                "label": label,
                "orig": orig,
                "group": "individual",
                "title": orig,
            })

    # Build col→table parent lookup for FK edge promotion
    col_to_table: dict[str, str] = {}
    for col_uri in g.subjects(RDF.type, DB.Column):
        parent = g.value(col_uri, DB.belongsToTable)
        if parent:
            col_to_table[str(col_uri)] = str(parent)

    # Edges: explicit relations
    # For db:references (col→table FK), promote to table→table when the column
    # is not in node_ids, so FK relationships always show up on the graph.
    seen_table_fk: set[tuple] = set()
    edge_id = 0

    for s, o in g.subject_objects(predicate=DB.references):
        sid, oid = str(s), str(o)
        if sid in node_ids and oid in node_ids:
            # Column is visible — draw col→table edge
            edges.append({"id": f"e{edge_id}", "from": sid, "to": oid,
                          "label": "外键", "color": "#7f8d9f"})
            edge_id += 1
        elif oid in node_ids:
            # Column not visible — promote to table→table edge
            parent = col_to_table.get(sid)
            if parent and parent in node_ids and (parent, oid) not in seen_table_fk:
                seen_table_fk.add((parent, oid))
                edges.append({"id": f"e{edge_id}", "from": parent, "to": oid,
                              "label": "外键", "color": "#7f8d9f",
                              "dashes": False, "width": 2})
                edge_id += 1

    EDGE_PROPS = {
        str(DB.similarTo):       {"label": "相似",    "color": "#8ca0b4"},
        str(DB.potentialFK):     {"label": "潜在外键", "color": "#a58b61"},
        str(DB.containsColumn):  {"label": "包含",    "color": "#aeb8c4"},
        str(DB.hasIndividual):   {"label": "数据行",  "color": "#6e9280"},
        str(DB.fkLink):          {"label": "关联",    "color": "#817991"},
    }
    for prop_uri, style in EDGE_PROPS.items():
        pred = URIRef(prop_uri)
        for s, o in g.subject_objects(predicate=pred):
            sid, oid = str(s), str(o)
            if sid in node_ids and oid in node_ids:
                edges.append({
                    "id": f"e{edge_id}",
                    "from": sid,
                    "to": oid,
                    **style,
                })
                edge_id += 1

    return {"nodes": nodes, "edges": edges}


@app.post("/api/query/preset")
async def query_preset(req: PresetQueryRequest):
    global _sparql_api
    if _sparql_api is None:
        active = _get_active_path()
        if active:
            from kg_builder.query.sparql_api import SPARQLApi
            _sparql_api = SPARQLApi.from_file(str(active))
        else:
            return JSONResponse(status_code=404,
                                content={"error": "图谱尚未构建。"})
    try:
        qt = req.query_type
        if qt == "find_related":
            rows = _sparql_api.find_related_tables(req.param)
        elif qt == "similar_columns":
            rows = _sparql_api.find_similar_columns(req.param)
        elif qt == "table_schema":
            rows = _sparql_api.get_table_schema(req.param)
        elif qt == "fk_graph":
            rows = _sparql_api.get_fk_graph(req.param or None)
        elif qt == "potential_joins":
            t1, t2 = req.param, req.param2
            rows = _sparql_api.find_potential_joins(t1, t2)
        elif qt == "search_comment":
            rows = _sparql_api.search_by_comment(req.param)
        elif qt == "find_individuals":
            rows = _sparql_api.find_individuals_by_value(req.param, req.param2 or None)
        elif qt == "individual_detail":
            rows = _sparql_api.get_individual_detail(req.param)
        else:
            return JSONResponse(status_code=400,
                                content={"error": f"未知查询类型: {qt}"})
        return {"rows": rows}
    except Exception as e:
        return JSONResponse(status_code=500, content={"error": str(e)})


@app.post("/api/query/sparql")
async def query_sparql(req: SparqlRequest):
    global _sparql_api
    if _sparql_api is None:
        active = _get_active_path()
        if active:
            from kg_builder.query.sparql_api import SPARQLApi
            _sparql_api = SPARQLApi.from_file(str(active))
        else:
            return JSONResponse(status_code=404,
                                content={"error": "图谱尚未构建。"})
    try:
        rows = _sparql_api.run_raw(req.sparql)
        return {"rows": rows}
    except Exception as e:
        return JSONResponse(status_code=500, content={"error": str(e)})


@app.get("/api/graph/tables")
async def graph_tables():
    """Return list of table entries {name, label} for autocomplete."""
    global _sparql_api
    if _sparql_api is None:
        active = _get_active_path()
        if active:
            from kg_builder.query.sparql_api import SPARQLApi
            _sparql_api = SPARQLApi.from_file(str(active))
        else:
            return {"tables": []}
    from rdflib import RDF
    from rdflib.namespace import RDFS
    from kg_builder.ontology.owl_schema import DB
    import re
    _ZH_RE = re.compile(r'[\u4e00-\u9fff]')
    g = _sparql_api._g
    tables = []
    seen = set()
    for s in g.subjects(RDF.type, DB.Table):
        orig = g.value(s, DB.tableName) or g.value(s, DB.name)
        if not orig:
            continue
        orig = str(orig)
        if orig in seen:
            continue
        seen.add(orig)
        # Prefer @zh label, then any Chinese rdfs:label
        zh = None
        for lbl in g.objects(s, RDFS.label):
            if getattr(lbl, "language", None) == "zh":
                zh = str(lbl)
                break
        if not zh:
            for lbl in g.objects(s, RDFS.label):
                if _ZH_RE.search(str(lbl)):
                    zh = str(lbl)
                    break
        tables.append({"name": orig, "label": zh or orig})
    tables.sort(key=lambda x: x["name"])
    return {"tables": tables}


@app.get("/api/query/example")
async def query_example():
    """Generate an executable SPARQL example based on actual graph data."""
    global _sparql_api
    if _sparql_api is None:
        active = _get_active_path()
        if active:
            from kg_builder.query.sparql_api import SPARQLApi
            _sparql_api = SPARQLApi.from_file(str(active))
        else:
            return {"sparql": "# 请先构建图谱\nSELECT ?t WHERE { ?t a <http://kg.local/db#Table> }"}
    from rdflib import RDF
    from kg_builder.ontology.owl_schema import DB

    tables = sorted({
        str(v)
        for s in _sparql_api._g.subjects(RDF.type, DB.Table)
        if (v := _sparql_api._g.value(s, DB.name))
    })
    if not tables:
        return {"sparql": "SELECT ?t WHERE { ?t a <http://kg.local/db#Table> }"}

    table_name = tables[0]
    sparql = (
        "PREFIX db:   <http://kg.local/db#>\n"
        "PREFIX inst: <http://kg.local/instance/>\n\n"
        f"# 查询表 [{table_name}] 的所有字段及类型\n"
        f'SELECT ?colName ?colType ?isPK ?isNullable WHERE {{\n'
        f'  ?t a db:Table ; db:tableName "{table_name}" .\n'
        f"  ?t db:containsColumn ?col .\n"
        f"  ?col db:name ?colName ;\n"
        f"       db:columnType ?colType ;\n"
        f"       db:isPrimaryKey ?isPK ;\n"
        f"       db:isNullable ?isNullable .\n"
        f"}}\n"
        f"ORDER BY DESC(?isPK) ?colName"
    )
    return {"sparql": sparql, "table": table_name}


@app.get("/api/graph/list")
async def graph_list():
    """Return list of available KG files (kg_*.ttl + indicator-data.ttl), newest first."""
    files = []
    for p in sorted(OUTPUT_DIR.glob("kg_*.ttl"), key=lambda p: p.stat().st_mtime, reverse=True):
        s = p.stat()
        files.append({"filename": p.name, "size_mb": round(s.st_size / 1_048_576, 1), "type": "datasource"})
    # Include legacy kg.ttl if it exists and isn't already listed
    legacy = OUTPUT_DIR / "kg.ttl"
    if legacy.exists() and not any(f["filename"] == "kg.ttl" for f in files):
        s = legacy.stat()
        files.append({"filename": "kg.ttl", "size_mb": round(s.st_size / 1_048_576, 1), "type": "datasource"})
    # Business KG file
    bz_files = []
    bz_path = BKG_DIR / "indicator-data.ttl"
    if bz_path.exists():
        s = bz_path.stat()
        bz_files.append({"filename": bz_path.name, "size_mb": round(s.st_size / 1_048_576, 1), "type": "business"})
    active = _get_active_path()
    return {"files": files, "bz_files": bz_files, "active": active.name if active else None}


@app.post("/api/graph/select")
async def graph_select(req: SelectGraphRequest):
    """Load a specific KG file as the active graph for queries and visualization."""
    global _sparql_api, _current_kg_path
    name = Path(req.filename).name
    # Try OUTPUT_DIR first, then BKG_DIR
    path = (OUTPUT_DIR / name).resolve()
    if not path.exists():
        path = (BKG_DIR / name).resolve()
        if not str(path).startswith(str(BKG_DIR.resolve())):
            return JSONResponse(status_code=400, content={"error": "非法路径"})
    else:
        if not str(path).startswith(str(OUTPUT_DIR.resolve())):
            return JSONResponse(status_code=400, content={"error": "非法路径"})
    if not path.exists():
        return JSONResponse(status_code=404, content={"error": "文件不存在"})
    from kg_builder.query.sparql_api import SPARQLApi
    _sparql_api  = SPARQLApi.from_file(str(path))
    _path_finder = None   # reset path finder for new graph
    _current_kg_path = path
    return {"ok": True, "filename": path.name}


# ── P0: JOIN 路径推荐 ────────────────────────────────────────────────────── #

@app.get("/api/join-path")
async def join_path(from_table: str, to_table: str, max_hops: int = 5):
    """Find shortest JOIN paths between two tables."""
    global _sparql_api, _path_finder
    if _sparql_api is None:
        active = _get_active_path()
        if active:
            from kg_builder.query.sparql_api import SPARQLApi
            _sparql_api = SPARQLApi.from_file(str(active))
        else:
            return JSONResponse(status_code=404, content={"error": "图谱尚未构建"})

    if _path_finder is None:
        from kg_builder.query.path_finder import JoinPathFinder
        _path_finder = JoinPathFinder(_sparql_api._g)

    try:
        paths = _path_finder.find_paths(from_table, to_table, max_hops=max_hops)
        return {
            "from_table": from_table,
            "to_table":   to_table,
            "paths":      [p.to_dict() for p in paths],
            "count":      len(paths),
        }
    except Exception as e:
        return JSONResponse(status_code=500, content={"error": str(e)})


# ── P1: 变更影响分析 ─────────────────────────────────────────────────────── #

@app.get("/api/impact")
async def impact_analysis(table: str, max_depth: int = 6):
    """Return all tables that depend on *table* via FK (direct + transitive)."""
    global _sparql_api, _path_finder
    if _sparql_api is None:
        active = _get_active_path()
        if active:
            from kg_builder.query.sparql_api import SPARQLApi
            _sparql_api = SPARQLApi.from_file(str(active))
        else:
            return JSONResponse(status_code=404, content={"error": "图谱尚未构建"})

    if _path_finder is None:
        from kg_builder.query.path_finder import JoinPathFinder
        _path_finder = JoinPathFinder(_sparql_api._g)

    try:
        result = _path_finder.get_impact(table, max_depth=max_depth)
        return result
    except Exception as e:
        return JSONResponse(status_code=500, content={"error": str(e)})


# ── P2: 数据质量 FK 完整性 ────────────────────────────────────────────────── #

@app.get("/api/quality/fk")
async def quality_fk():
    """Return FK integrity check results from the last build."""
    if _quality_results is None:
        return JSONResponse(
            status_code=404,
            content={"error": "尚无质量检测结果，请先构建图谱。"},
        )
    return {
        "count":      len(_quality_results),
        "violations": _quality_results,
    }


# ── Business KG ──────────────────────────────────────────────────────────── #

_DATA_AGENT_URL = os.getenv(
    "DATA_AGENT_URL",
    "http://127.0.0.1:8091/bi/v1/datasource/query",
).rstrip("/")


def _build_valid_test_cases(turtle_str: str, blog) -> list:
    """
    解析 TTL，按共同事实表关系构建有效测试用例列表。

    关系链：
      Measure -hasMeasureApp-> MeasureApp -appliesToTable-> DwTable
      Dimension -hasDimApp-> DimensionApp -dimFactTable-> DwTable

    只有指标和维度共享至少一张 DwTable，才生成对应测试用例。
    每个指标同时生成一条"无维度"的单独测试。

    返回 [(meas_code, dim_code_or_empty), ...]
    """
    from rdflib import Graph as _Graph, Namespace as _NS

    try:
        g = _Graph()
        g.parse(data=turtle_str, format="turtle")
    except Exception as e:
        blog(f"[验证] TTL 解析失败: {e}")
        return []

    IND = _NS("http://indicator.insightmind.com/ontology#")

    # URI → code
    uri_to_code: dict = {}
    for _s, _p, _o in g:
        if str(_p) == str(IND.code):
            uri_to_code[str(_s)] = str(_o)

    # Measure URI → set of DwTable URIs
    # 路径：Measure -hasMeasureApp-> MeasureApp -appliesToTable/measFactTable-> DwTable
    _TABLE_PROPS = {str(IND.appliesToTable), str(IND.measFactTable)}
    meas_uri_to_tables: dict = {}
    for _s, _p, _o in g:
        if str(_p) == str(IND.hasMeasureApp):
            app_uri = _o
            for _, _p2, _tbl in g.triples((app_uri, None, None)):
                if str(_p2) in _TABLE_PROPS:
                    meas_uri_to_tables.setdefault(str(_s), set()).add(str(_tbl))

    # Dimension URI → set of DwTable URIs
    # 路径：Dimension -hasDimApp-> DimensionApp -dimFactTable-> DwTable
    dim_uri_to_tables: dict = {}
    for _s, _p, _o in g:
        if str(_p) == str(IND.hasDimApp):
            app_uri = _o
            for _, _p2, _tbl in g.triples((app_uri, None, None)):
                if str(_p2) == str(IND.dimFactTable):
                    dim_uri_to_tables.setdefault(str(_s), set()).add(str(_tbl))

    # code → tables
    meas_code_tables: dict = {}
    dim_code_tables: dict = {}
    for uri, code in uri_to_code.items():
        if code.startswith("MEAS_") and uri in meas_uri_to_tables:
            meas_code_tables[code] = meas_uri_to_tables[uri]
        elif code.startswith("DIM_") and uri in dim_uri_to_tables:
            dim_code_tables[code] = dim_uri_to_tables[uri]

    # 构建测试用例
    test_cases: list = []
    for meas_code in sorted(meas_code_tables):
        test_cases.append((meas_code, ""))   # 单独测试
        for dim_code in sorted(dim_code_tables):
            if meas_code_tables[meas_code] & dim_code_tables[dim_code]:
                test_cases.append((meas_code, dim_code))

    n_pairs = sum(1 for _, d in test_cases if d)
    blog(f"[验证] {len(meas_code_tables)} 个指标，{len(dim_code_tables)} 个维度"
         f" → {len(meas_code_tables)} 条单独测试 + {n_pairs} 条有效组合（共同事实表）"
         f" = 共 {len(test_cases)} 条")
    return test_cases


def _extract_bkg_codes(turtle_str: str, blog):
    """从 TTL 中提取所有 MEAS_* 和 DIM_* code，返回 (meas_codes, dim_codes)。"""
    from rdflib import Graph as _Graph, Namespace as _NS
    try:
        g = _Graph()
        g.parse(data=turtle_str, format="turtle")
    except Exception as e:
        blog(f"[验证] TTL 解析失败: {e}")
        return [], []
    IND = _NS("http://indicator.insightmind.com/ontology#")
    meas_codes, dim_codes = [], []
    for _s, _p, _o in g:
        if str(_p) == str(IND.code):
            code = str(_o)
            if code.startswith("MEAS_"):
                meas_codes.append(code)
            elif code.startswith("DIM_"):
                dim_codes.append(code)
    return sorted(set(meas_codes)), sorted(set(dim_codes))


def _test_bkg_queries(turtle_str: str, blog,
                      test_cases: list = None,
                      meas_codes: list = None,
                      dim_codes: list = None,
                      data_agent_url: str = _DATA_AGENT_URL,
                      stop_on_first: bool = True):
    """
    逐条测试查询组合，详细记录每个错误到 blog。

    优先使用 test_cases（由 _build_valid_test_cases 生成的有效组合列表）。
    stop_on_first=True：发现第一个错误立即返回。
    返回 (meas_code, dim_code, error_msg) 的列表。
    """
    import json as _json
    import urllib.request as _ureq
    import urllib.error as _uerr

    # 构建测试列表
    if test_cases is not None:
        cases = test_cases
    elif meas_codes is not None:
        # 兼容旧调用方式（单独重验某个组合）
        _dims = dim_codes if dim_codes else [""]
        cases = [(m, d) for m in meas_codes for d in _dims]
    else:
        blog("[验证] 未提供测试用例，跳过")
        return []

    total  = len(cases)
    tested = 0
    errors = []

    blog(f"[验证] 开始测试，共 {total} 条")

    for meas, dim in cases:
        configure: list = [{"code": meas}]
        if dim:
            configure.append({"code": dim})

        body = _json.dumps({
            "configureList": configure,
            "pageSize": 3,
            "pageNum": 1,
        }).encode("utf-8")
        req = _ureq.Request(
            data_agent_url,
            data=body,
            headers={"Content-Type": "application/json"},
            method="POST",
        )
        err_msg = None
        try:
            with _urlopen(req, timeout=15) as resp:
                raw_body = resp.read().decode("utf-8")
                result   = _json.loads(raw_body)
                if result.get("code") != 200:
                    detail = (result.get("msg") or result.get("message")
                              or result.get("error") or "")
                    if not detail or detail in ("查询失败", "error", "fail"):
                        detail = raw_body[:600]
                    err_msg = detail[:600]
                    blog(f"[验证] ✗ 指标={meas}  维度={dim or '(无)'}")
                    blog(f"         HTTP code={result.get('code')}")
                    blog(f"         错误信息: {err_msg}")
                else:
                    rows = (result.get("data") or {}).get("list") or []
                    if rows:
                        first = rows[0]
                        vals  = list(first.values()) if isinstance(first, dict) else []
                        if vals and all(str(v) in ("-", "null", "None", "") for v in vals):
                            err_msg = "所有行返回空值(-)，可能是 dimTypeCode/JOIN 条件配置错误"
                            blog(f"[验证] ✗ 指标={meas}  维度={dim or '(无)'}")
                            blog(f"         {err_msg}")
                            blog(f"         首行数据: {_json.dumps(first, ensure_ascii=False)}")
                    else:
                        blog(f"[验证] ✓ 指标={meas}  维度={dim or '(无)'}  (返回空列表，视为通过)")
        except _uerr.HTTPError as e:
            err_body = e.read().decode("utf-8", errors="replace")[:400]
            err_msg = f"HTTP {e.code} {e.reason}: {err_body}"
            blog(f"[验证] ✗ 指标={meas}  维度={dim or '(无)'}")
            blog(f"         {err_msg}")
        except Exception as e:
            if "Connection refused" in str(e) or "connection refused" in str(e):
                blog("[验证] dataAgent 未运行（Connection refused），跳过查询验证")
                return []
            err_msg = str(e)[:300]
            blog(f"[验证] ✗ 指标={meas}  维度={dim or '(无)'}")
            blog(f"         异常: {err_msg}")

        if err_msg:
            errors.append((meas, dim, err_msg))
            if stop_on_first:
                blog(f"[验证] 发现错误，立即进入修复流程（已测 {tested + 1}/{total} 条）")
                return errors

        tested += 1
        if tested % 20 == 0:
            blog(f"[验证] 进度 {tested}/{total}")

    blog(f"[验证] 全部测试完成，{tested} 条，发现 {len(errors)} 个错误")
    return errors


def _prune_unqueryable_bkg(turtle_str: str, blog) -> str:
    """Remove graph entities that DA cannot query because their app links are missing."""
    from rdflib import Graph as _Graph, Namespace as _NS, RDF as _RDF

    IND = _NS("http://indicator.insightmind.com/ontology#")
    try:
        g = _Graph()
        g.parse(data=turtle_str, format="turtle")
    except Exception as e:
        blog(f"[清理] TTL 解析失败，跳过孤立实体清理: {e}")
        return turtle_str

    def remove_subject(subject) -> None:
        for triple in list(g.triples((subject, None, None))):
            g.remove(triple)
        for triple in list(g.triples((None, None, subject))):
            g.remove(triple)

    removed_measures = 0
    for meas in list(g.subjects(_RDF.type, IND.Measure)):
        valid_apps = [
            app for app in g.objects(meas, IND.hasMeasureApp)
            if (app, _RDF.type, IND.MeasureApp) in g
            and (list(g.objects(app, IND.appliesToTable)) or list(g.objects(app, IND.measFactTable)))
        ]
        if not valid_apps:
            remove_subject(meas)
            removed_measures += 1

    removed_dims = 0
    for dim in list(g.subjects(_RDF.type, IND.Dimension)):
        valid_apps = [
            app for app in g.objects(dim, IND.hasDimApp)
            if (app, _RDF.type, IND.DimensionApp) in g
            and list(g.objects(app, IND.dimFactTable))
        ]
        if not valid_apps:
            remove_subject(dim)
            removed_dims += 1

    removed_apps = 0
    for app in list(g.subjects(_RDF.type, IND.MeasureApp)):
        if not list(g.subjects(IND.hasMeasureApp, app)):
            remove_subject(app)
            removed_apps += 1
    for app in list(g.subjects(_RDF.type, IND.DimensionApp)):
        if not list(g.subjects(IND.hasDimApp, app)):
            remove_subject(app)
            removed_apps += 1

    removed_ndm = 0
    for ndm in list(g.subjects(_RDF.type, IND.NaturalDimMapping)):
        if not list(g.subjects(IND.hasNaturalDimMapping, ndm)):
            remove_subject(ndm)
            removed_ndm += 1

    if removed_measures or removed_dims or removed_apps or removed_ndm:
        blog(
            f"[清理] 移除不可查询实体：孤立指标 {removed_measures}，"
            f"孤立维度 {removed_dims}，孤立应用 {removed_apps}，孤立公共维度映射 {removed_ndm}"
        )
        return g.serialize(format="turtle")
    return turtle_str


def _ensure_public_date_dimensions(turtle_str: str, blog) -> str:
    """Ensure h_date exposes real DIM_date_* dimensions for DA grouping."""
    from rdflib import Graph, Literal, Namespace, RDF

    IND = Namespace("http://indicator.insightmind.com/ontology#")
    INST = Namespace("http://indicator.insightmind.com/instance/")
    try:
        g = Graph()
        g.parse(data=turtle_str, format="turtle")
    except Exception as exc:
        blog(f"[公共日期] Turtle 解析失败，跳过公共日期维度补齐: {exc}")
        return turtle_str

    levels = {
        "year": ("年", 5, 1),
        "quarter": ("季度", 4, 2),
        "month": ("月", 3, 3),
        "week": ("周", 2, 4),
        "day": ("日", 1, 5),
    }
    apps_by_level: dict[str, list[Any]] = {level: [] for level in levels}
    for dim in g.subjects(RDF.type, IND.Dimension):
        if str(g.value(dim, IND.hierarchyCode) or "") != "h_date":
            continue
        level = str(g.value(dim, IND.levelCode) or "").strip().lower()
        if level not in apps_by_level:
            continue
        code = str(g.value(dim, IND.code) or "")
        if code.startswith("DIM_date_") and code == f"DIM_date_{level}":
            continue
        for app in g.objects(dim, IND.hasDimApp):
            if (app, RDF.type, IND.DimensionApp) in g and list(g.objects(app, IND.dimFactTable)):
                if app not in apps_by_level[level]:
                    apps_by_level[level].append(app)

    added = 0
    updated = 0
    for level, (label, view_type, sequence) in levels.items():
        apps = apps_by_level[level]
        if not apps:
            continue
        dim_uri = INST[f"dim_date_{level}"]
        code = f"DIM_date_{level}"
        if (dim_uri, RDF.type, IND.Dimension) not in g:
            g.add((dim_uri, RDF.type, IND.Dimension))
            g.add((dim_uri, IND.cnName, Literal(label)))
            g.add((dim_uri, IND.code, Literal(code)))
            g.add((dim_uri, IND.definition, Literal(f"公共日期{label}维度，按 h_date 映射到各指标事实表的日期列")))
            g.add((dim_uri, IND.dimTypeCode, Literal(0)))
            g.add((dim_uri, IND.enName, Literal(code)))
            g.add((dim_uri, IND.hierarchyCode, Literal("h_date")))
            g.add((dim_uri, IND.isHyper, Literal(True)))
            g.add((dim_uri, IND.levelCode, Literal(level)))
            g.add((dim_uri, IND.levelSequence, Literal(sequence)))
            g.add((dim_uri, IND.viewTypeCode, Literal(view_type)))
            added += 1
        for app in apps:
            if (dim_uri, IND.hasDimApp, app) not in g:
                g.add((dim_uri, IND.hasDimApp, app))
                updated += 1

    if added or updated:
        blog(f"[公共日期] 补齐公共日期维度 {added} 个，关联 DimApp {updated} 条")
        return g.serialize(format="turtle")
    return turtle_str


def _ensure_public_shared_dimensions(turtle_str: str, blog) -> str:
    """Attach same-name dimension apps to one public dimension across fact tables."""
    from rdflib import Graph, Literal, Namespace, RDF

    IND = Namespace("http://indicator.insightmind.com/ontology#")
    try:
        g = Graph()
        g.parse(data=turtle_str, format="turtle")
    except Exception as exc:
        blog(f"[公共维度] Turtle 解析失败，跳过公共维度补齐: {exc}")
        return turtle_str

    def val(node, prop) -> str:
        value = g.value(node, prop)
        return str(value) if value is not None else ""

    def app_fact_table(app) -> str:
        table = g.value(app, IND.dimFactTable)
        return val(table, IND.tableName) if table else ""

    groups: dict[tuple[str, str, str], list[Any]] = {}
    for dim in g.subjects(RDF.type, IND.Dimension):
        code = val(dim, IND.code)
        if not code.startswith("DIM_"):
            continue
        view_type = int(val(dim, IND.viewTypeCode) or 0)
        if 1 <= view_type <= 6:
            continue
        name = (val(dim, IND.cnName) or val(dim, IND.enName) or code).strip()
        if not name:
            continue
        key = (
            name,
            val(dim, IND.hierarchyCode).strip(),
            val(dim, IND.levelCode).strip(),
        )
        groups.setdefault(key, []).append(dim)

    updated = 0
    for dims in groups.values():
        fact_tables = {
            table
            for dim in dims
            for app in g.objects(dim, IND.hasDimApp)
            if (app, RDF.type, IND.DimensionApp) in g
            for table in [app_fact_table(app)]
            if table
        }
        if len(fact_tables) <= 1:
            continue

        public_dim = min(dims, key=lambda dim: len(val(dim, IND.code)) or 9999)
        public_code = val(public_dim, IND.code)
        if not public_code:
            continue
        if not val(public_dim, IND.definition):
            public_name = val(public_dim, IND.cnName) or public_code
            g.add((public_dim, IND.definition, Literal(f"公共{public_name}维度，映射到各指标事实表对应的维度应用")))

        seen_apps = set(g.objects(public_dim, IND.hasDimApp))
        for dim in dims:
            for app in g.objects(dim, IND.hasDimApp):
                if (app, RDF.type, IND.DimensionApp) not in g or app in seen_apps:
                    continue
                if not app_fact_table(app):
                    continue
                g.add((public_dim, IND.hasDimApp, app))
                seen_apps.add(app)
                updated += 1

    if updated:
        blog(f"[公共维度] 补齐跨事实表公共维度关联 DimApp {updated} 条")
        return g.serialize(format="turtle")
    return turtle_str


class BusinessKGBuildRequest(BaseModel):
    domain_hint: str = ""
    model: str = ""          # kept for backward compatibility; AD uses unified GPT5.5 config
    source_kg_file: str = "" # kg_*.ttl to use as input; falls back to active KG
    source_schema: str = ""  # optional schema/database name inside a multi-schema source KG
    # Optional: reference indicator DB for pattern extraction (persisted across builds)
    etl_host:     str = ""
    etl_port:     int = 0
    etl_database: str = ""
    etl_username: str = ""
    etl_password: str = ""


def _bkg_worker(domain_hint: str, model_override: str, source_kg_file: str,
                source_schema: str = "",
                ref_cfg: Optional[dict] = None) -> None:
    global _bkg_state, _bkg_turtle, _bkg_graph, _current_bkg_path, _last_ref_cfg, _bkg_meta

    def blog(msg: str) -> None:
        _bkg_log_queue.put(msg)

    # Use ETL path when reference DB is configured; LLM path otherwise.
    _use_etl = bool(ref_cfg and ref_cfg.get("database"))

    if _use_etl:
        STEPS = [
            "连接参考数据库",
            "ETL 导出指标/维度数据",
            "校验 Turtle 语法",
            "保存业务图谱文件",
            "验证 dataAgent 查询",
        ]
    else:
        STEPS = [
            "解析数据源图谱文件",
            "提取元数据摘要",
            "从参考数据库提炼指标/维度规律",
            "规划 LLM 生成批次",
            "分批调用 LLM 生成实例",
            "合并清洗业务本体",
            "校验 Turtle 语法",
            "保存业务图谱文件",
            "验证 dataAgent 查询",
        ]

    def plan():
        import time
        blog("__PLAN__:" + "|".join(STEPS))
        blog(f"── 本次构建共 {len(STEPS)} 步 ──")
        for i, s in enumerate(STEPS, 1):
            blog(f"  {i:2d}. {s}")
        blog("─" * 30)
        time.sleep(0.5)  # ensure frontend renders all steps before first __STEP__

    def step(n: int, detail: str = ""):
        blog(f"__STEP__:{n}" + (f":{detail}" if detail else ""))

    try:
        _bkg_state = {"status": "running", "message": "准备中…"}
        plan()

        from rdflib import Graph as RGraph
        from kg_builder.business_kg.llm_builder import _ONTOLOGY_PREAMBLE

        active = None   # will be set in LLM path; guard for ETL path meta

        if _use_etl:
            # ════════════════════════════════════════════════════════════════
            # ETL 路径：直接从指标平台数据库导出真实数据，无需 LLM
            # 适用场景：参考数据库 = 指标平台 MySQL，需要将其完整迁移到图谱
            # ════════════════════════════════════════════════════════════════

            # ── Step 1: 连接参考数据库 ──────────────────────────────────── #
            from kg_builder.business_kg.data_exporter import IndicatorDataExporter
            exporter = IndicatorDataExporter(
                host=ref_cfg["host"],
                port=int(ref_cfg.get("port") or 3306),
                database=ref_cfg["database"],
                username=ref_cfg["username"],
                password=ref_cfg["password"],
                log_cb=blog,
            )
            _last_ref_cfg = ref_cfg   # persist for subsequent builds
            step(1, f"已连接 {ref_cfg['host']}:{ref_cfg.get('port', 3306)}/{ref_cfg['database']}")

            # ── Step 2: ETL 导出指标/维度数据 ───────────────────────────── #
            instances_str = exporter.export()
            if not instances_str.strip():
                _bkg_state = {"status": "error", "message": "ETL 导出结果为空"}
                return
            turtle_str = _ONTOLOGY_PREAMBLE + "\n# ═══ 数据实例 ═══\n\n" + instances_str
            step(2, f"{len(instances_str)} 字符实例数据")

            # ── Step 3: 校验 Turtle 语法 ────────────────────────────────── #
            bkg = RGraph()
            try:
                bkg.parse(data=turtle_str, format="turtle")
                step(3, f"{len(bkg)} 条三元组")
            except Exception as e:
                blog(f"[警告] Turtle 校验失败: {e}，继续保存原始内容")
                bkg = None
                step(3, "校验失败，文件仍将保存")

            # ── Step 4: 保存业务图谱文件 ─────────────────────────────────── #
            save_path = _next_bkg_path()
            _archive_bkg()
            save_path.write_text(turtle_str, encoding="utf-8")
            step(4, save_path.name)

        else:
            # ════════════════════════════════════════════════════════════════
            # LLM 路径：从数据源图谱元数据 + 参考规律，让 LLM 推断指标/维度
            # 适用场景：新接入数据库，无直接指标平台数据，需 LLM 理解业务语义
            # ════════════════════════════════════════════════════════════════

            # ── Step 1: 解析数据源图谱 ──────────────────────────────────── #
            if source_kg_file:
                active = (OUTPUT_DIR / Path(source_kg_file).name).resolve()
                if not Path(active).exists():
                    blog(f"[错误] 指定的图谱文件不存在: {source_kg_file}")
                    _bkg_state = {"status": "error", "message": "源文件不存在"}
                    return
                active = Path(active)
            else:
                active = _get_active_path()
            if active is None or not active.exists():
                blog("[错误] 尚未构建数据源图谱，请先在「连接配置」标签页构建图谱。")
                _bkg_state = {"status": "error", "message": "无数据源图谱"}
                return
            blog(f"加载: {active.name}")
            from rdflib import Graph
            g = Graph()
            g.parse(str(active), format="turtle")
            step(1, f"{len(g)} 条三元组")

            # ── Step 2: 提取元数据摘要 ──────────────────────────────────── #
            from kg_builder.business_kg.extractor import MetadataSummaryExtractor
            target_schema = (source_schema or "").strip()
            if target_schema:
                blog(f"仅提取业务库/schema: {target_schema}")
            summary = MetadataSummaryExtractor(
                g,
                domain_hint=domain_hint,
                target_schema=target_schema,
            ).extract()
            step(2, f"{len(summary)} 字符" + (f"，schema={target_schema}" if target_schema else ""))

            # ── Step 3: 从参考数据库提炼指标/维度规律 ───────────────────── #
            pattern_context = ""
            ref_source = _last_ref_cfg
            if ref_source and ref_source.get("database"):
                try:
                    from kg_builder.business_kg.pattern_extractor import IndicatorPatternExtractor
                    extractor = IndicatorPatternExtractor(
                        host=ref_source["host"],
                        port=int(ref_source.get("port") or 3306),
                        database=ref_source["database"],
                        username=ref_source["username"],
                        password=ref_source["password"],
                        log_cb=blog,
                    )
                    pattern_context = extractor.extract()
                    step(3, f"提炼规律 {len(pattern_context)} 字符")
                except Exception as ref_exc:
                    blog(f"[提示] 参考库提炼跳过: {ref_exc}")
                    step(3, "跳过（参考库连接失败）")
            else:
                blog("  未配置参考数据库，跳过规律提炼步骤")
                step(3, "跳过（无参考库配置）")

            # ── Step 4~6: LLM 分批生成业务本体 ─────────────────────────── #
            from kg_builder.business_kg.llm_builder import BusinessKGBuilder
            builder = BusinessKGBuilder.from_env(
                base_dir=Path(__file__).parent,
                log_cb=blog,
            )

            scenario_context = ""
            if DEFAULT_BKG_SCENARIO_PATH.exists():
                scenario_context = DEFAULT_BKG_SCENARIO_PATH.read_text(encoding="utf-8")
                blog(
                    f"加载默认业务场景: {DEFAULT_BKG_SCENARIO_PATH.name} "
                    f"（{len(scenario_context):,} 字符）"
                )

            turtle_str, ok = builder.build(
                summary,
                domain_hint=domain_hint,
                pattern_context=pattern_context,
                scenario_context=scenario_context,
                progress_cb=step,
                preserve_all_tables=bool(target_schema),
            )
            if not ok or not turtle_str:
                _bkg_state = {"status": "error", "message": "LLM 生成失败"}
                return

            # ── Step 7: 校验 Turtle 语法（含自动修复）───────────────────── #
            bkg = RGraph()
            try:
                bkg.parse(data=turtle_str, format="turtle")
                step(7, f"{len(bkg)} 条三元组")
            except Exception as e:
                blog(f"[警告] Turtle 校验失败: {e}")
                blog("  尝试逐块修复，丢弃无效三元组…")
                turtle_str, repaired_ok = builder._repair_drop_bad_triples(turtle_str)
                bkg2 = RGraph()
                try:
                    bkg2.parse(data=turtle_str, format="turtle")
                    bkg = bkg2
                    blog(f"  修复完成，保留 {len(bkg)} 条三元组")
                    step(7, f"{len(bkg)} 条三元组（修复后）")
                except Exception as e2:
                    blog(f"[警告] 修复后仍有错误: {e2}，继续保存原始内容")
                    bkg = None
                    step(7, "校验失败，文件仍将保存")

            # ── Step 8: 保存业务图谱文件 ─────────────────────────────────── #
            save_path = _next_bkg_path()
            _archive_bkg()
            save_path.write_text(turtle_str, encoding="utf-8")
            step(8, save_path.name)

        # ── Step N: 验证 dataAgent 查询（发现错误立即修复）──────────────── #
        validate_step_n = 5 if _use_etl else 9
        turtle_str = _prune_unqueryable_bkg(turtle_str, blog)
        turtle_str = _ensure_public_date_dimensions(turtle_str, blog)
        turtle_str = _ensure_public_shared_dimensions(turtle_str, blog)
        _archive_bkg()
        save_path.write_text(turtle_str, encoding="utf-8")
        step(validate_step_n, "开始测试指标×维度查询…")
        _MAX_REPAIR = 3
        _test_cases = _build_valid_test_cases(turtle_str, blog)

        for _repair_iter in range(_MAX_REPAIR + 1):
            blog(f"[验证] ── 第 {_repair_iter + 1} 轮验证{'（修复后重新验证）' if _repair_iter else ''} ──")
            _errors = _test_bkg_queries(
                turtle_str, blog,
                test_cases=_test_cases,
                stop_on_first=True,
            )
            if not _errors:
                _pass_msg = "全部通过" + (f"（经 {_repair_iter} 次修复）" if _repair_iter else "")
                blog(f"[验证] ✓ {_pass_msg}")
                step(validate_step_n, _pass_msg)
                break

            meas_err, dim_err, err_detail = _errors[0]
            blog(f"[验证] ══ 错误详情 ══")
            blog(f"[验证]   指标 code : {meas_err}")
            blog(f"[验证]   维度 code : {dim_err or '(无维度)'}")
            blog(f"[验证]   完整错误  : {err_detail}")

            transient_validation_error = any(
                s in str(err_detail).lower()
                for s in ("timed out", "timeout", "connection reset", "connection aborted")
            )
            if transient_validation_error:
                blog("[验证] 查询超时/网络中断属于 dataAgent 执行侧临时错误，跳过 LLM 修复以避免长时间阻塞")
                step(validate_step_n, "查询验证超时，已跳过自动修复")
                break

            if _repair_iter >= _MAX_REPAIR:
                blog(f"[验证] 已达最大修复次数 {_MAX_REPAIR}，停止")
                step(validate_step_n, f"仍有错误（已达最大修复次数 {_MAX_REPAIR}）")
                break

            blog(f"[修复] ══ 第 {_repair_iter + 1} 次 LLM 修复 ══")
            from kg_builder.business_kg.llm_builder import BusinessKGBuilder as _BKGBuilder
            _repair_builder = _BKGBuilder.from_env(
                base_dir=Path(__file__).parent,
                log_cb=blog,
            )
            turtle_str, _repair_ok = _repair_builder.repair(turtle_str, _errors)
            if not _repair_ok:
                blog("[修复] LLM 修复失败，保留当前文件，停止迭代")
                step(validate_step_n, "修复失败")
                break

            _archive_bkg()
            save_path.write_text(turtle_str, encoding="utf-8")
            blog(f"[修复] 已重新保存: {save_path.name}")

            # ── 立即重验刚才失败的那个组合 ──────────────────────────────── #
            blog(f"[修复] ── 验证修复是否有效：指标={meas_err}  维度={dim_err or '(无)'} ──")
            _recheck = _test_bkg_queries(
                turtle_str, blog,
                test_cases=[(meas_err, dim_err)],
                stop_on_first=True,
            )
            if not _recheck:
                blog(f"[修复] ✓ 该组合已修复通过，继续全量验证")
            else:
                blog(f"[修复] ✗ 该组合修复后仍有错误: {_recheck[0][2]}")

            # 修复后重新构建测试用例（实例可能发生变化）
            _test_cases = _build_valid_test_cases(turtle_str, blog)

        _bkg_turtle       = turtle_str
        _bkg_graph        = bkg
        try:
            _materialize_business_inferences(turtle_str, blog)
        except Exception as infer_exc:
            blog(f"[推理] 生成推理图谱失败: {infer_exc}")
        _current_bkg_path = save_path
        _bkg_meta         = {
            "gen_time":  time.strftime("%Y-%m-%d %H:%M:%S"),
            "source_kg": active.name if active else "",
            "source_schema": source_schema or "",
        }
        _write_semantic_source_manifest(active)
        _bkg_state  = {"status": "done", "message": "业务图谱生成完成"}
        blog("=== 业务图谱构建完成 ===")

    except Exception as exc:
        blog(f"[错误] {exc}")
        _bkg_state = {"status": "error", "message": str(exc)}


@app.post("/api/business-kg/build")
async def build_business_kg(req: BusinessKGBuildRequest):
    global _bkg_turtle, _bkg_graph
    already_running = _bkg_state.get("status") == "running"
    if already_running:
        # Don't start a new build; caller will subscribe to the ongoing SSE stream
        return {"ok": True, "already_running": True}
    # clear old log
    while not _bkg_log_queue.empty():
        try:
            _bkg_log_queue.get_nowait()
        except Exception:
            break
    _bkg_turtle = ""
    _bkg_graph  = None
    # Build reference indicator DB config from request fields (if provided)
    ref_cfg = None
    if req.etl_host and req.etl_database:
        ref_cfg = {
            "host": req.etl_host,
            "port": req.etl_port or 3306,
            "database": req.etl_database,
            "username": req.etl_username,
            "password": req.etl_password,
        }
    t = threading.Thread(
        target=_bkg_worker,
        args=(req.domain_hint, req.model, req.source_kg_file, req.source_schema, ref_cfg),
        daemon=True,
    )
    t.start()
    return {"ok": True}


@app.get("/api/business-kg/log")
async def business_kg_log():
    """SSE stream of business KG build log."""
    def gen():
        while True:
            try:
                msg = _bkg_log_queue.get(timeout=0.4)
                yield f"data: {msg}\n\n"
            except queue.Empty:
                if _bkg_state.get("status") not in ("idle", "running"):
                    yield f"data: __DONE__\n\n"
                    break
                yield ": ping\n\n"
    return StreamingResponse(gen(), media_type="text/event-stream")


@app.get("/api/business-kg/status")
async def business_kg_status():
    return _bkg_state


@app.get("/api/business-kg/meta")
async def business_kg_meta():
    """Return last successful build metadata: gen_time and source_kg filename."""
    return _bkg_meta


@app.get("/api/business-kg/list")
async def business_kg_list():
    """Return list of generated business KG files (indicator-data.ttl), newest first."""
    files = []
    p = BKG_DIR / "indicator-data.ttl"
    if p.exists():
        s = p.stat()
        files.append({"filename": p.name, "size_mb": round(s.st_size / 1_048_576, 2)})
    active = _current_bkg_path.name if _current_bkg_path and _current_bkg_path.exists() else (
        p.name if p.exists() else None
    )
    # Also list historical versions
    for p in sorted(BKG_DIR.glob("indicator-data-*.ttl"), reverse=True):
        s = p.stat()
        files.append({"filename": p.name, "size_mb": round(s.st_size / 1_048_576, 2), "mtime": s.st_mtime})
    return {"files": files, "active": active}


class ActivateRequest(BaseModel):
    file: str

@app.post("/api/business-kg/activate")
async def business_kg_activate(req: ActivateRequest):
    """将某个历史版本激活为当前 indicator-data.ttl。"""
    file = req.file
    if not file:
        return JSONResponse(status_code=400, content={"error": "缺少 file 参数"})
    src = (BKG_DIR / Path(file).name).resolve()
    if not str(src).startswith(str(BKG_DIR.resolve())) or not src.exists():
        return JSONResponse(status_code=404, content={"error": "文件不存在"})
    target = BKG_DIR / "indicator-data.ttl"
    global _current_bkg_path
    if src == target:
        _current_bkg_path = target
        _materialize_business_inferences(target.read_text(encoding="utf-8"))
        return {"ok": True, "message": "已是当前版本", "active": target.name}
    # 直接复制历史版本覆盖当前，不归档旧版本
    target.write_text(src.read_text(encoding="utf-8"), encoding="utf-8")
    source_manifest = BKG_DIR / f"{src.stem}.source.json"
    if source_manifest.exists():
        SEMANTIC_SOURCE_MANIFEST_PATH.write_text(
            source_manifest.read_text(encoding="utf-8"), encoding="utf-8"
        )
    else:
        SEMANTIC_SOURCE_MANIFEST_PATH.unlink(missing_ok=True)
    _materialize_business_inferences(target.read_text(encoding="utf-8"))
    _current_bkg_path = target
    return {"ok": True, "active": target.name, "archived_from": src.name}


class RefineRequest(BaseModel):
    prompt: str = ""
    target: str = ""  # business KG file to refine; defaults to active
    auto_empty_dim: bool = False  # quick mode: target all measures with empty dimensions

@app.post("/api/business-kg/refine")
async def business_kg_refine(req: RefineRequest):
    """根据用户提示词，对当前业务图谱做增量修订（LLM 输出 SPARQL UPDATE patch）。"""
    prompt = (req.prompt or "").strip()
    if not prompt and not req.auto_empty_dim:
        return JSONResponse(status_code=400, content={"error": "缺少 prompt 或选择快捷模式"})
    if _bkg_state.get("status") == "running":
        return {"ok": True, "already_running": True}
    while not _bkg_log_queue.empty():
        try:
            _bkg_log_queue.get_nowait()
        except Exception:
            break
    t = threading.Thread(
        target=_bkg_refine_worker,
        args=(prompt, (req.target or "").strip(), bool(req.auto_empty_dim)),
        daemon=True,
    )
    t.start()
    return {"ok": True}


def _bkg_refine_worker(user_prompt: str, target_file: str, auto_empty_dim: bool = False) -> None:
    global _bkg_state, _bkg_turtle, _bkg_graph, _current_bkg_path
    import time as _time
    import os as _os
    import urllib.request, urllib.error, json as _json
    import re as _re
    from rdflib import Graph
    from kg_builder.utils.llm_config import chat_completions_url, llm_config_from_env, llm_request_headers, validate_llm_config

    def blog(msg: str) -> None:
        _bkg_log_queue.put(msg)

    def call_llm(messages: list, label: str, max_tokens: int = 4096) -> Optional[str]:
        """POST to /chat/completions and return content, or None on failure (logs included)."""
        cfg = call_llm._cfg
        body = _json.dumps({
            "model": cfg["model"],
            "max_tokens": max_tokens,
            "temperature": 0.1,
            "messages": messages,
        }).encode("utf-8")
        blog(f"  → {label}（请求体 {len(body):,} 字节）")
        req2 = urllib.request.Request(
            chat_completions_url(cfg["base_url"]),
            data=body, method="POST",
            headers=llm_request_headers(cfg),
        )
        t0 = _time.time()
        try:
            with _urlopen(req2, timeout=1200) as resp:
                data = _json.loads(resp.read().decode("utf-8"))
        except urllib.error.HTTPError as he:
            err_body = he.read().decode("utf-8", errors="replace")[:600]
            blog(f"  [错误] {label} HTTP {he.code}: {err_body}")
            return None
        except Exception as he:
            blog(f"  [错误] {label} 异常: {he}")
            return None
        elapsed = int(_time.time() - t0)
        msg = (data.get("choices") or [{}])[0].get("message") or {}
        content = (msg.get("content") or "").strip()
        if not content:
            content = (msg.get("reasoning_content") or "").strip()
        blog(f"  ← {label} 完成（{elapsed}s，{len(content)} 字符）")
        return content or None

    try:
        _bkg_state.update({"status": "running", "message": "提示词调整中…"})
        STEPS = ["加载业务图谱", "LLM 识别待调整目标", "逐个修复", "校验并保存", "激活新版本"]
        blog("__PLAN__:" + "|".join(STEPS))
        blog(f"── 提示词调整 共 {len(STEPS)} 步 ──")
        for i, s in enumerate(STEPS, 1):
            blog(f"  {i:2d}. {s}")
        blog("─" * 30)

        # ── Step 1: load business KG ─────────────────────────────────────── #
        blog("__STEP__:1")
        if target_file:
            biz_path = (BKG_DIR / Path(target_file).name).resolve()
            if not str(biz_path).startswith(str(BKG_DIR.resolve())) or not biz_path.exists():
                blog(f"[错误] 业务图谱不存在: {target_file}")
                _bkg_state.update({"status": "error", "message": "业务图谱不存在"})
                return
        else:
            biz_path = BKG_DIR / "indicator-data.ttl"
            if not biz_path.exists():
                blog("[错误] 当前没有激活的业务图谱，请先生成")
                _bkg_state.update({"status": "error", "message": "尚未生成业务图谱"})
                return
        active_bkg_path = (BKG_DIR / "indicator-data.ttl").resolve()
        bound_source = (
            _semantic_source_path()
            if biz_path.resolve() == active_bkg_path else None
        )
        biz_ttl = biz_path.read_text(encoding="utf-8")
        blog(f"  当前业务图谱: {biz_path.name}（{len(biz_ttl):,} 字符）")

        g = Graph()
        try:
            g.parse(data=biz_ttl, format="turtle")
        except Exception as e:
            blog(f"[错误] 业务图谱解析失败: {e}")
            _bkg_state.update({"status": "error", "message": "业务图谱解析失败"})
            return
        triples_before = len(g)

        # Build a structured measure summary so the identifier LLM gets clean
        # facts instead of 200KB of Turtle.
        from rdflib import URIRef as _URIRef, RDF as _RDF
        IND_NS = "http://indicator.insightmind.com/ontology#"
        def _u(n): return _URIRef(IND_NS + n)
        def _val(s, p):
            v = g.value(s, _u(p))
            return str(v) if v is not None else ""

        # Pre-index DimensionApp → fact table → dim labels (mirrors measures API).
        tbl_dims: dict[str, list[str]] = {}
        for dapp in g.subjects(_RDF.type, _u("DimensionApp")):
            tbl = g.value(dapp, _u("dimFactTable"))
            dim = g.value(predicate=_u("hasDimApp"), object=dapp)
            if not tbl or not dim:
                continue
            lbl = _val(dim, "cnName") or _val(dim, "code") or str(dim).rsplit("/", 1)[-1]
            tbl_dims.setdefault(str(tbl), [])
            if lbl not in tbl_dims[str(tbl)]:
                tbl_dims[str(tbl)].append(lbl)

        tbl_name: dict[str, str] = {}
        for tbl in g.subjects(_RDF.type, _u("DwTable")):
            tbl_name[str(tbl)] = _val(tbl, "tableName") or str(tbl).rsplit("/", 1)[-1]

        measure_summaries: list[dict] = []
        for meas in g.subjects(_RDF.type, _u("Measure")):
            code = _val(meas, "code")
            cn   = _val(meas, "cnName")
            unit = _val(meas, "unit")
            cali = _val(meas, "caliber") or _val(meas, "definition")
            facts: list[str] = []
            dims: list[str] = []
            for mapp in g.objects(meas, _u("hasMeasureApp")):
                tbl = g.value(mapp, _u("appliesToTable"))
                if not tbl:
                    continue
                tn = tbl_name.get(str(tbl), str(tbl).rsplit("/", 1)[-1])
                if tn and tn not in facts:
                    facts.append(tn)
                for d in tbl_dims.get(str(tbl), []):
                    if d not in dims:
                        dims.append(d)
            measure_summaries.append({
                "subject": str(meas),
                "code":    code,
                "cnName":  cn,
                "unit":    unit,
                "caliber": cali,
                "factTables": facts,
                "dimensions": dims,
            })
        measure_summaries.sort(key=lambda x: x.get("code") or "")
        blog(f"  共 {len(measure_summaries)} 个指标已构建结构化摘要")

        # Pre-compute structural candidates for quick-mode and LLM fallback.
        empty_dim_codes  = [m["code"] for m in measure_summaries if not m.get("dimensions")]
        empty_fact_codes = [m["code"] for m in measure_summaries if not m.get("factTables")]
        if empty_dim_codes:
            preview = ", ".join(empty_dim_codes[:8]) + ("…" if len(empty_dim_codes) > 8 else "")
            blog(f"  结构性发现: {len(empty_dim_codes)} 个指标维度为空 ({preview})")
        if empty_fact_codes:
            blog(f"  结构性发现: {len(empty_fact_codes)} 个指标无事实表")

        # Set up LLM config
        cfg = llm_config_from_env(
            model_override=_os.environ.get("BUSINESS_KG_MODEL", "").strip(),
        )
        validate_llm_config(cfg, purpose="Business KG refinement")
        call_llm._cfg = cfg
        blog(f"  调用模型: {cfg['model']}（{cfg['base_url']}）")

        # ── Step 2: identify candidate measures ──────────────────────────── #
        blog("__STEP__:2")

        # Quick mode: bypass LLM identification entirely.
        if auto_empty_dim:
            if not empty_dim_codes:
                blog("✅ 快捷模式：当前没有维度为空的指标，无需调整")
                _bkg_state.update({"status": "done", "message": "无需调整"})
                return
            blog(f"  快捷模式：直接使用结构性发现的 {len(empty_dim_codes)} 个空维度指标作为目标")
            targets = [
                {"subject": m["subject"], "code": m["code"], "cnName": m["cnName"],
                 "issue": "dimensions 数组为空（快捷模式）"}
                for m in measure_summaries if not m.get("dimensions")
            ]
            if not user_prompt:
                user_prompt = "对关联维度为空的指标补充合适的维度。"
        else:
            identify_sys = (
                "你是知识图谱审核助手。给定一份指标摘要 JSON 列表 + 用户的修订意图，"
                "请找出所有需要按用户意图调整的指标，并以 JSON 数组形式输出。\n"
                "严格要求：\n"
                "1. 只输出一段被 ```json ... ``` 包裹的 JSON 数组，不要解释。\n"
                "2. 数组中每个对象包含 subject、code、cnName、issue 四个字段。\n"
                "3. 如果某个指标已经满足要求，不要列出。\n"
                "4. 若用户问 '关联维度为空的指标'，请返回所有 dimensions 数组为空 [] 的指标。\n"
                "5. 若用户问 '没有事实表的指标'，请返回 factTables 为空的指标。\n"
                "6. 数组按 code 升序。无目标时输出 `[]`。"
            )
            identify_user = (
                f"## 用户修订意图\n{user_prompt}\n\n"
                f"## 指标摘要（共 {len(measure_summaries)} 个）\n"
                f"```json\n{_json.dumps(measure_summaries, ensure_ascii=False, indent=2)}\n```\n"
            )

            identify_resp = call_llm(
                [{"role": "system", "content": identify_sys},
                 {"role": "user", "content": identify_user}],
                label="识别待调整目标",
                max_tokens=4096,
            )
            if identify_resp is None:
                _bkg_state.update({"status": "error", "message": "识别阶段 LLM 失败"})
                return

            snippet = identify_resp.replace("\n", " ⏎ ")[:400]
            blog(f"  LLM 原始响应: {snippet}{'…' if len(identify_resp) > 400 else ''}")

            m = _re.search(r"```json\s*(.+?)```", identify_resp, _re.DOTALL | _re.IGNORECASE)
            json_text = (m.group(1) if m else identify_resp).strip()
            try:
                targets = _json.loads(json_text)
                if not isinstance(targets, list):
                    raise ValueError("expected JSON array")
            except Exception as e:
                blog(f"[错误] 识别结果不是合法 JSON: {e}")
                blog(f"--- 原始响应（前 800 字）---\n{identify_resp[:800]}")
                _bkg_state.update({"status": "error", "message": "识别结果格式错误"})
                return

            # Fallback: keyword-based override when LLM misjudges empty-dim cases.
            norm = _re.sub(r"[\"'`「」『』《》【】（）()]", "", user_prompt)
            norm_lc = norm.lower()
            mentions_dim = ("维度" in norm) or ("dimension" in norm_lc)
            mentions_empty = any(k in norm for k in (
                "为空", "为null", "为 null", "缺", "无", "没有", "补充", "完整", "添加",
            )) or any(k in norm_lc for k in ("null", "empty", "missing"))
            wants_empty_dim = mentions_dim and mentions_empty
            if not targets and wants_empty_dim and empty_dim_codes:
                blog(f"  ⚠️ LLM 返回 []，但结构上检测到 {len(empty_dim_codes)} 个空维度指标 — 启用兜底列表")
                targets = [
                    {"subject": m["subject"], "code": m["code"], "cnName": m["cnName"],
                     "issue": "dimensions 数组为空（结构兜底）"}
                    for m in measure_summaries if not m.get("dimensions")
                ]

        if not targets:
            blog("✅ 当前业务图谱已满足要求，无待调整目标")
            _bkg_state.update({"status": "done", "message": "无需调整"})
            return

        blog(f"  共识别出 {len(targets)} 个待调整指标:")
        for i, tgt in enumerate(targets, 1):
            code   = (tgt.get("code") or "").strip() or "?"
            cnName = (tgt.get("cnName") or "").strip()
            issue  = (tgt.get("issue") or "").strip()
            blog(f"    {i:2d}. {code} {cnName}  — {issue}")

        # ── Step 3: fix each target one by one ───────────────────────────── #
        blog("__STEP__:3")
        fix_sys = (
            "你是知识图谱编辑助手。给定业务 KG（Turtle）以及一个待调整的 Measure 目标，"
            "请输出一段 SPARQL 1.1 UPDATE 来修复该目标。严格要求：\n"
            "1. 只输出一段被 ```sparql ... ``` 包裹的 SPARQL UPDATE，不要解释。\n"
            "2. 必须重新声明前缀：\n"
            "   PREFIX ind:  <http://indicator.insightmind.com/ontology#>\n"
            "   PREFIX inst: <http://indicator.insightmind.com/instance/>\n"
            "   PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>\n"
            "   PREFIX xsd:  <http://www.w3.org/2001/XMLSchema#>\n"
            "3. 仅围绕给定 subject 修订；不要重写整张图。\n"
            "4. 新增 Dimension/DimensionApp 时复用现有 inst:tbl_xxx / inst:col_xxx URI。\n"
            "5. 优先 INSERT DATA / DELETE DATA；批量条件删除用 DELETE WHERE。\n"
            "6. 若需要为某个 Measure 补维度：给该 Measure 对应的事实表（DwTable）"
            "新增 DimensionApp 三元组，并通过 ind:hasDimApp 把已有 Dimension 关联到该 DimensionApp，"
            "DimensionApp 必须设置 ind:dimFactTable（事实表 URI）和 ind:dimFactColumn（事实表中的关联列名）。"
        )

        def _dims_for_measure(meas_uri: str) -> list[str]:
            """Re-derive the dimension labels of a Measure from the current graph state."""
            subj = _URIRef(meas_uri)
            facts = []
            for mapp in g.objects(subj, _u("hasMeasureApp")):
                tbl = g.value(mapp, _u("appliesToTable"))
                if tbl:
                    facts.append(str(tbl))
            # Dynamically rebuild tbl→dims since the graph may have new DimensionApps.
            dims: list[str] = []
            for tbl_uri in facts:
                tbl_ref = _URIRef(tbl_uri)
                for dapp in g.subjects(_u("dimFactTable"), tbl_ref):
                    dim = g.value(predicate=_u("hasDimApp"), object=dapp)
                    if dim:
                        lbl = _val(dim, "cnName") or _val(dim, "code") or str(dim).rsplit("/", 1)[-1]
                        if lbl not in dims:
                            dims.append(lbl)
            return dims

        def _attempt_fix(subject: str, code: str, retry_hint: str = "") -> tuple[bool, list[str]]:
            """Run one LLM→SPARQL apply cycle. Returns (sparql_applied_ok, dims_after)."""
            label = f"修复 {code}" + (" (重试)" if retry_hint else "")
            user_extra = f"\n\n## 上一次失败原因\n{retry_hint}\n请针对此问题重新生成 SPARQL。" if retry_hint else ""
            fix_user = (
                f"## 用户原始意图\n{user_prompt}\n\n"
                f"## 待修复目标\nsubject: {subject}\ncode: {code}\ncnName: {tgt.get('cnName','')}\n"
                f"issue: {tgt.get('issue','')}\n\n"
                f"## 业务 KG\n```turtle\n{biz_ttl}\n```{user_extra}\n"
            )
            fix_resp = call_llm(
                [{"role": "system", "content": fix_sys},
                 {"role": "user", "content": fix_user}],
                label=label,
                max_tokens=4096,
            )
            if not fix_resp:
                blog(f"  [跳过] {code} LLM 未返回内容")
                return False, []
            mm = _re.search(r"```sparql\s*(.+?)```", fix_resp, _re.DOTALL | _re.IGNORECASE)
            sparql = (mm.group(1) if mm else fix_resp).strip()
            if not sparql:
                blog(f"  [跳过] {code} 未解析到 SPARQL")
                return False, []
            tb = len(g)
            try:
                g.update(sparql)
            except Exception as e:
                blog(f"  [失败] {code} SPARQL UPDATE 出错: {e}")
                blog(f"    --- SPARQL ---\n{sparql[:600]}")
                return False, _dims_for_measure(subject)
            ta = len(g)
            blog(f"  · {code} 三元组 {tb} → {ta}（净 {'+' if ta >= tb else ''}{ta - tb}）")
            return True, _dims_for_measure(subject)

        ok_count = 0
        fail_count = 0
        for i, tgt in enumerate(targets, 1):
            subject = (tgt.get("subject") or "").strip() or (tgt.get("uri") or "").strip()
            code    = (tgt.get("code") or "").strip() or "?"
            cnName  = (tgt.get("cnName") or "").strip()
            issue   = (tgt.get("issue") or "").strip()
            blog(f"  ── 修复 {i}/{len(targets)}: {code} {cnName} ──")
            blog(f"     问题: {issue}")
            dims_before = _dims_for_measure(subject) if subject else []
            blog(f"     修复前维度: {len(dims_before)} 个" + (f" ({', '.join(dims_before[:5])})" if dims_before else ""))

            applied, dims_after = _attempt_fix(subject, code)
            if applied and len(dims_after) > len(dims_before):
                added = [d for d in dims_after if d not in dims_before]
                blog(f"  ✅ {code} 修复完成，维度 {len(dims_before)} → {len(dims_after)}（新增: {', '.join(added) or '无名称'}）")
                ok_count += 1
                continue

            # No improvement → diagnose and retry once
            if not applied:
                reason = "LLM 未生成可执行 SPARQL"
            elif len(dims_after) == len(dims_before) == 0:
                reason = ("SPARQL 已执行但目标 Measure 关联的事实表上仍没有 DimensionApp。"
                          "可能 LLM 漏写 ind:dimFactTable，或挂在了错误的事实表上。")
            else:
                reason = f"SPARQL 已执行但维度数未增加（仍 {len(dims_after)} 个）"
            blog(f"  ⚠️ {code} 修复后仍不达标，原因: {reason}")
            blog(f"     重试一次…")

            applied2, dims_after2 = _attempt_fix(subject, code, retry_hint=reason)
            if applied2 and len(dims_after2) > len(dims_before):
                added = [d for d in dims_after2 if d not in dims_before]
                blog(f"  ✅ {code} 重试成功，维度 {len(dims_before)} → {len(dims_after2)}（新增: {', '.join(added) or '无名称'}）")
                ok_count += 1
            else:
                blog(f"  ❌ {code} 重试后仍未达标（维度 {len(dims_before)} → {len(dims_after2)}）")
                fail_count += 1

        blog(f"  汇总: 成功 {ok_count} / 失败 {fail_count} / 共 {len(targets)}")
        if ok_count == 0:
            blog("[错误] 没有任何指标修复成功")
            _bkg_state.update({"status": "error", "message": "没有指标修复成功"})
            return

        # ── Step 4: validate and save ────────────────────────────────────── #
        blog("__STEP__:4")
        new_ttl = g.serialize(format="turtle")
        try:
            Graph().parse(data=new_ttl, format="turtle")
        except Exception as e:
            blog(f"[错误] 修订后 Turtle 校验失败: {e}")
            _bkg_state.update({"status": "error", "message": "修订后 Turtle 校验失败"})
            return
        archive = _archive_bkg()
        if archive:
            blog(f"  归档原版本: {archive.name}")
        out = BKG_DIR / "indicator-data.ttl"
        out.write_text(new_ttl, encoding="utf-8")
        _write_semantic_source_manifest(bound_source)
        triples_after = len(g)
        blog(f"  写入新版本: {out.name}（{len(new_ttl):,} 字符，三元组 {triples_before} → {triples_after}）")

        # ── Step 5: activate ─────────────────────────────────────────────── #
        blog("__STEP__:5")
        _current_bkg_path = out
        _bkg_turtle = new_ttl
        _bkg_graph = g
        _bkg_state.update({"status": "done", "message": f"调整完成（{ok_count}/{len(targets)}）"})
        blog(f"✅ 提示词调整完成，成功 {ok_count}/{len(targets)} 个指标")
    except Exception as e:
        blog(f"[错误] {e}")
        _bkg_state.update({"status": "error", "message": str(e)})


@app.get("/api/business-kg/stats")
async def business_kg_stats(file: str = ""):
    """Return instance counts by ind: class for a given TTL file."""
    if file:
        p = (BKG_DIR / Path(file).name).resolve()
        if not str(p).startswith(str(BKG_DIR.resolve())) or not p.exists():
            return JSONResponse(status_code=404, content={"error": "文件不存在"})
        content = p.read_text(encoding="utf-8")
    elif _bkg_turtle:
        content = _bkg_turtle
    elif _current_bkg_path and _current_bkg_path.exists():
        content = _current_bkg_path.read_text(encoding="utf-8")
    elif (BKG_DIR / "indicator-data.ttl").exists():
        content = (BKG_DIR / "indicator-data.ttl").read_text(encoding="utf-8")
    else:
        return JSONResponse(status_code=404, content={"error": "尚未生成业务图谱"})

    from rdflib import Graph, URIRef, RDF
    IND = "http://indicator.insightmind.com/ontology#"
    CLASS_LABELS = {
        "Measure":                  "指标",
        "Dimension":                "维度",
        "DwTable":                  "数仓表",
        "DwColumn":                 "数仓字段",
        "Category":                 "分类",
        "Hierarchy":                "层次",
        "Level":                    "维度级别",
        "MeasureApplication":       "指标应用",
        "DimensionApplication":     "维度应用",
        "DimensionDimtableConnect": "维表关联",
    }
    g = Graph()
    try:
        g.parse(data=content, format="turtle")
    except Exception as e:
        return JSONResponse(status_code=500, content={"error": str(e)})

    stats = []
    for cls_name, label in CLASS_LABELS.items():
        cls_uri = URIRef(IND + cls_name)
        count = sum(1 for _ in g.subjects(RDF.type, cls_uri))
        if count > 0:
            stats.append({"class": cls_name, "label": label, "count": count})
    total = sum(s["count"] for s in stats)
    return {"stats": stats, "total": total, "triples": len(g)}


@app.get("/api/business-kg/measures")
async def business_kg_measures(file: str = ""):
    """Return all ind:Measure instances with lineage: related dimensions and fact tables."""
    if file:
        p = (BKG_DIR / Path(file).name).resolve()
        if not str(p).startswith(str(BKG_DIR.resolve())) or not p.exists():
            return JSONResponse(status_code=404, content={"error": "文件不存在"})
        content = p.read_text(encoding="utf-8")
    elif _bkg_turtle:
        content = _bkg_turtle
    elif _current_bkg_path and _current_bkg_path.exists():
        content = _current_bkg_path.read_text(encoding="utf-8")
    elif (BKG_DIR / "indicator-data.ttl").exists():
        content = (BKG_DIR / "indicator-data.ttl").read_text(encoding="utf-8")
    else:
        return JSONResponse(status_code=404, content={"error": "尚未生成业务图谱"})

    import json as _json
    from rdflib import Graph, URIRef, RDF

    IND = "http://indicator.insightmind.com/ontology#"

    def _u(name): return URIRef(IND + name)

    g = Graph()
    try:
        g.parse(data=content, format="turtle")
    except Exception as e:
        return JSONResponse(status_code=500, content={"error": str(e)})

    def _val(subj, pred):
        v = g.value(subj, _u(pred))
        return str(v) if v is not None else ""

    # ── 预构建: tbl_uri → {tableName, fullName} ──────────────────────────── #
    tbl_info: dict = {}
    for tbl in g.subjects(RDF.type, _u("DwTable")):
        schema = _val(tbl, "schemaName")
        table  = _val(tbl, "tableName")
        full   = f"{schema}.{table}" if schema else (table or "")
        tbl_info[str(tbl)] = {"tableName": table, "fullName": full}

    # ── 预构建: tbl_uri → [(dim_label, dimFactColumn)] ───────────────────── #
    # 遍历 DimensionApp，通过 dimFactTable 关联事实表，通过 hasDimApp 反向找父 Dimension
    tbl_dim_cols: dict[str, list] = {}
    tbl_dims: dict[str, list[str]] = {}
    tbl_dim_codes: dict[str, list[str]] = {}
    tbl_dim_items: dict[str, list[dict]] = {}
    for dapp in g.subjects(RDF.type, _u("DimensionApp")):
        tbl = g.value(dapp, _u("dimFactTable"))
        if not tbl:
            continue
        dim = g.value(predicate=_u("hasDimApp"), object=dapp)
        if not dim:
            continue
        fc       = _val(dapp, "dimFactColumn")
        dim_cn   = _val(dim, "cnName")
        dim_code = _val(dim, "code")
        dim_lbl  = dim_cn or _val(dim, "enName") or dim_code
        tbl_key  = str(tbl)
        # tbl_dims
        tbl_dims.setdefault(tbl_key, [])
        if dim_lbl and dim_lbl not in tbl_dims[tbl_key]:
            tbl_dims[tbl_key].append(dim_lbl)
        # tbl_dim_codes
        tbl_dim_codes.setdefault(tbl_key, [])
        if dim_code and dim_code not in tbl_dim_codes[tbl_key]:
            tbl_dim_codes[tbl_key].append(dim_code)
        tbl_dim_items.setdefault(tbl_key, [])
        if dim_code and not any(x.get("code") == dim_code for x in tbl_dim_items[tbl_key]):
            tbl_dim_items[tbl_key].append({"code": dim_code, "name": dim_lbl or dim_code})
        # tbl_dim_cols
        if fc:
            tbl_dim_cols.setdefault(tbl_key, [])
            entry = (dim_lbl, fc)
            if entry not in tbl_dim_cols[tbl_key]:
                tbl_dim_cols[tbl_key].append(entry)

    # ── 预构建: meas_uri → first MeasureApp info ─────────────────────────── #
    # 使用新属性名: hasMeasureApp / appliesToTable / applyTypeCode / hasColumnDT / whereCondition
    mapp_first: dict = {}
    for mapp in g.subjects(RDF.type, _u("MeasureApp")):
        meas = g.value(predicate=_u("hasMeasureApp"), object=mapp)
        if not meas:
            continue
        key = str(meas)
        if key in mapp_first:
            continue
        tbl = g.value(mapp, _u("appliesToTable"))
        mapp_first[key] = {
            "factColumn":    _val(mapp, "factColumn"),
            "expression":    _val(mapp, "expression"),
            "applyTypeCode": _val(mapp, "applyTypeCode"),
            "hasColumnDT":   _val(mapp, "hasColumnDT").lower() in ("true", "1"),
            "whereCondition": _val(mapp, "whereCondition"),
            "tableUri":      str(tbl) if tbl else "",
        }

    # ── 解析 JSON 算子数组 → SQL 聚合表达式 ─────────────────────────────── #
    def _agg_from_expression(expr: str, fact_col: str) -> str:
        try:
            ops = _json.loads(expr)
            if isinstance(ops, list) and ops:
                op = ops[0].get("operator", "")
                col = fact_col or "1"
                op_map = {
                    "distinct_count": f"COUNT(DISTINCT {col})",
                    "count":          f"COUNT({col})",
                    "sum":            f"SUM({col})",
                    "avg":            f"AVG({col})",
                    "max":            f"MAX({col})",
                    "min":            f"MIN({col})",
                }
                return op_map.get(op, f"SUM({col})")
        except Exception:
            pass
        # fallback：expr 本身不是 JSON（衍生指标公式）
        return expr or (f"SUM({fact_col})" if fact_col else "COUNT(1)")

    # ── Logic SQL generator ──────────────────────────────────────────────── #
    def _gen_logic_sql(meas_uri: str, en_name: str, cn_name: str) -> str:
        info = mapp_first.get(str(meas_uri))
        if not info:
            return ""
        apply_type  = info.get("applyTypeCode", "0")
        fact_col    = info.get("factColumn", "")
        expression  = info.get("expression", "")
        has_col_dt  = info.get("hasColumnDT", False)
        where_cond  = info.get("whereCondition", "")
        tbl_uri     = info.get("tableUri", "")
        full_tbl    = tbl_info.get(tbl_uri, {}).get("fullName", "")
        alias       = en_name or cn_name or "metric"

        # 衍生指标（applyTypeCode=1）：expression 是公式字符串，无单一事实表
        if apply_type == "1":
            return (
                f"-- 衍生指标，由多个原子指标组合计算\n"
                f"-- 计算公式: {expression}\n"
                f"SELECT\n"
                f"    {expression} AS {alias}\n"
                f"FROM (\n"
                f"    -- 子查询请参考各原子指标的逻辑 SQL\n"
                f") t"
            )

        if not full_tbl:
            return ""

        agg_expr = _agg_from_expression(expression, fact_col)
        dim_cols = tbl_dim_cols.get(tbl_uri, [])[:6]

        select_parts: list[str] = []
        group_parts:  list[str] = []

        if has_col_dt:
            select_parts.append("    dt")
            group_parts.append("dt")

        for dim_lbl, fc in dim_cols:
            comment = f"  -- {dim_lbl}" if dim_lbl else ""
            select_parts.append(f"    {fc}{comment}")
            group_parts.append(fc)

        select_parts.append(f"    {agg_expr} AS {alias}")

        sql = "SELECT\n" + ",\n".join(select_parts)
        sql += f"\nFROM {full_tbl}"

        where_parts = []
        if has_col_dt:
            where_parts.append("dt = '${dt}'")
        if where_cond:
            where_parts.append(where_cond)
        if where_parts:
            sql += "\nWHERE " + "\n  AND ".join(where_parts)

        if group_parts:
            sql += "\nGROUP BY " + ", ".join(group_parts)

        return sql

    # ── Build measure list ───────────────────────────────────────────────── #
    measures = []
    for meas in g.subjects(RDF.type, _u("Measure")):
        cn_name     = _val(meas, "cnName")
        en_name     = _val(meas, "enName")
        code        = _val(meas, "code")
        definition  = _val(meas, "definition")
        description = _val(meas, "description")
        meas_type   = _val(meas, "measTypeCode")

        # ── 数据来源表：hasMeasureApp → appliesToTable ───────────────────── #
        fact_tables: list[str] = []
        for mapp in g.objects(meas, _u("hasMeasureApp")):
            tbl = g.value(mapp, _u("appliesToTable"))
            if tbl:
                lbl = tbl_info.get(str(tbl), {}).get("tableName") or str(tbl).rsplit("/", 1)[-1]
                if lbl and lbl not in fact_tables:
                    fact_tables.append(lbl)

        # ── 关联维度：hasMeasureApp → appliesToTable → tbl_dims ─────────── #
        dim_names: list[str] = []
        dim_codes_for_meas: list[str] = []
        dim_items_for_meas: list[dict] = []
        for mapp in g.objects(meas, _u("hasMeasureApp")):
            tbl = g.value(mapp, _u("appliesToTable"))
            if tbl:
                for dn in tbl_dims.get(str(tbl), []):
                    if dn not in dim_names:
                        dim_names.append(dn)
                for dc in tbl_dim_codes.get(str(tbl), []):
                    if dc not in dim_codes_for_meas:
                        dim_codes_for_meas.append(dc)
                for item in tbl_dim_items.get(str(tbl), []):
                    if item.get("code") and not any(x.get("code") == item.get("code") for x in dim_items_for_meas):
                        dim_items_for_meas.append(item)

        measures.append({
            "id":             code,
            "cnName":         cn_name,
            "enName":         en_name,
            "code":           code,
            "unit":           _val(meas, "unit"),
            "caliber":        _val(meas, "caliber") or definition or description,
            "definition":     definition,
            "measType":       meas_type,
            "factTables":     fact_tables,
            "dimensions":     dim_names,
            "dimensionCodes": dim_codes_for_meas,
            "dimensionItems": dim_items_for_meas,
            "logicSql":       _gen_logic_sql(str(meas), en_name, cn_name),
        })

    measures.sort(key=lambda m: m.get("code", "") or "")
    return {"measures": measures, "total": len(measures)}


def _select_nlq_suggestion_candidates(
    items: list[dict[str, Any]],
    live_tables: set[str],
    preferred_codes: tuple[str, ...],
) -> list[dict[str, Any]]:
    """Prefer candidates backed by live tables, then stable demo-friendly codes."""
    selected = items
    if live_tables:
        live_items = [item for item in items if set(item.get("tables") or ()) & live_tables]
        if live_items:
            selected = live_items

    preferred_rank = {code: index for index, code in enumerate(preferred_codes)}
    return sorted(
        selected,
        key=lambda item: (
            preferred_rank.get(str(item.get("code") or ""), len(preferred_rank)),
            str(item.get("name") or ""),
        ),
    )


def _build_nlq_attribution_questions(
    primary_measure: dict[str, Any],
    second_measure: dict[str, Any],
    business_dims: list[dict[str, Any]],
    time_dims: list[dict[str, Any]],
) -> list[str]:
    """Build attribution prompts only from the active graph's compatible members."""
    primary_name = str(primary_measure.get("name") or "核心指标")
    second_name = str(second_measure.get("name") or primary_name)
    questions = [f"分析{primary_name}变化原因"]
    if business_dims:
        questions.append(f"分析不同{business_dims[0]['name']}的{second_name}差异原因")
    elif time_dims:
        questions.append(f"分析{second_name}在{time_dims[0]['name']}上的波动原因")
    elif second_name != primary_name:
        questions.append(f"分析{primary_name}与{second_name}变化差异的原因")
    return questions[:2]


@app.get("/api/nlq/suggestions")
async def nlq_suggestions(file: str = ""):
    """Generate default natural-language questions from the selected business KG TTL."""
    if file:
        p = (BKG_DIR / Path(file).name).resolve()
        if not str(p).startswith(str(BKG_DIR.resolve())) or not p.exists():
            return JSONResponse(status_code=404, content={"error": "文件不存在"})
    else:
        p = BKG_DIR / "indicator-data.ttl"
    if not p.exists():
        return JSONResponse(status_code=404, content={"error": "尚未生成业务图谱"})

    from rdflib import Graph, URIRef, RDF

    IND = "http://indicator.insightmind.com/ontology#"

    def _u(name): return URIRef(IND + name)

    g = Graph()
    try:
        g.parse(str(p), format="turtle")
    except Exception as e:
        return JSONResponse(status_code=500, content={"error": str(e)})

    def _val(subj, pred):
        v = g.value(subj, _u(pred))
        return str(v) if v is not None else ""

    measures = []
    for m in g.subjects(RDF.type, _u("Measure")):
        code = _val(m, "code")
        if not code.startswith("MEAS_"):
            continue
        tables = set()
        for app_node in g.objects(m, _u("hasMeasureApp")):
            tbl = g.value(app_node, _u("appliesToTable")) or g.value(app_node, _u("measFactTable"))
            table_name = _val(tbl, "tableName") if tbl else ""
            if table_name:
                tables.add(table_name)
        measures.append({
            "code": code,
            "name": _val(m, "cnName") or _val(m, "enName") or code,
            "tables": tables,
            "northStar": _val(m, "northStar").lower() in {"1", "true", "yes"},
        })

    dims = []
    for d in g.subjects(RDF.type, _u("Dimension")):
        code = _val(d, "code")
        if not code.startswith("DIM_"):
            continue
        tables = set()
        for app_node in g.objects(d, _u("hasDimApp")):
            tbl = g.value(app_node, _u("dimFactTable"))
            table_name = _val(tbl, "tableName") if tbl else ""
            if table_name:
                tables.add(table_name)
        view_type_raw = _val(d, "viewTypeCode")
        try:
            view_type = int(view_type_raw or 0)
        except (TypeError, ValueError):
            view_type = 0
        level = _val(d, "levelCode")
        hierarchy = _val(d, "hierarchyCode")
        dims.append({
            "code": code,
            "name": _val(d, "cnName") or _val(d, "enName") or code,
            "tables": tables,
            "viewType": view_type,
            "level": level,
            "hierarchy": hierarchy,
            "isTime": bool(view_type or level or "time" in hierarchy.lower()),
        })

    live_tables: set[str] = set()
    live_table_check_succeeded = False
    table_groups: dict[tuple[str, int, str, str, str], set[str]] = {}
    for tbl in g.subjects(RDF.type, _u("DwTable")):
        table_name = _val(tbl, "tableName")
        schema_name = _val(tbl, "schemaName")
        conn_node = g.value(tbl, _u("hasConnection"))
        if not table_name or conn_node is None:
            continue
        host = _val(conn_node, "host") or "127.0.0.1"
        try:
            port = int(_val(conn_node, "port") or 3306)
        except (TypeError, ValueError):
            port = 3306
        user = _val(conn_node, "dbUser") or "root"
        password = _val(conn_node, "dbPassword")
        database = _val(conn_node, "dbName") or schema_name
        if database:
            table_groups.setdefault((host, port, user, password, database), set()).add(table_name)

    try:
        import pymysql
        for (host, port, user, password, database), candidate_tables in table_groups.items():
            conn = None
            try:
                conn = pymysql.connect(
                    host=host,
                    port=port,
                    user=user,
                    password=password,
                    database=database,
                    charset="utf8mb4",
                    connect_timeout=2,
                    read_timeout=3,
                    write_timeout=3,
                )
                with conn.cursor() as cur:
                    cur.execute(
                        "SELECT TABLE_NAME FROM information_schema.TABLES WHERE TABLE_SCHEMA = %s",
                        (database,),
                    )
                    existing = {str(row[0]) for row in cur.fetchall()}
                live_tables.update(candidate_tables & existing)
                live_table_check_succeeded = True
            except Exception:
                continue
            finally:
                if conn is not None:
                    conn.close()
    except Exception:
        pass

    if not live_table_check_succeeded:
        live_tables = set()

    measures = _select_nlq_suggestion_candidates(
        measures,
        live_tables,
        (),
    )
    dims = _select_nlq_suggestion_candidates(
        dims,
        live_tables,
        (),
    )
    measures.sort(key=lambda item: (not bool(item.get("northStar")), str(item.get("code") or "")))

    primary_measure = measures[0] if measures else {"name": "核心指标", "tables": set()}
    second_measure = measures[1] if len(measures) > 1 else primary_measure
    compatible_dims = [d for d in dims if d["tables"] & primary_measure["tables"]] or dims
    second_compatible_dims = [d for d in dims if d["tables"] & second_measure["tables"]] or dims
    time_dims = sorted(
        [d for d in compatible_dims if d.get("isTime")],
        key=lambda item: (0 if item.get("viewType") == 3 else 1, int(item.get("viewType") or 0), str(item.get("code") or "")),
    )
    business_dims = sorted(
        [d for d in compatible_dims if not d.get("isTime")],
        key=lambda item: str(item.get("code") or ""),
    )
    second_business_dims = sorted(
        [d for d in second_compatible_dims if not d.get("isTime")],
        key=lambda item: str(item.get("code") or ""),
    )
    first_dim = business_dims[0] if business_dims else {"name": "业务维度"}
    second_dim = second_business_dims[0] if second_business_dims else first_dim
    time_dim = time_dims[0] if time_dims else {"name": "时间"}
    attribution_questions = _build_nlq_attribution_questions(
        primary_measure,
        second_measure,
        business_dims,
        time_dims,
    )

    entity_examples: list[str] = []
    try:
        from kg_builder.nlq import NaturalLanguageQueryService
        service = NaturalLanguageQueryService(
            ttl_path=p,
            data_agent_url=_DATA_AGENT_URL,
            source_ttl_path=_get_active_path(),
        )
        service._load_if_needed()
        candidates = service._entity_field_candidates()
        # 常见列名→中文标签的启发式映射
        _COLUMN_LABEL_MAP = {
            "order_number": "订单编号", "order": "订单号", "order_id": "订单ID",
            "customer": "客户", "warehouse": "仓库", "promotion": "促销",
            "item": "商品", "ship": "运输", "date": "日期",
            "call_center": "呼叫中心", "catalog_page": "目录页",
            "ship_mode": "运输方式", "return": "退货",
        }
        def _guess_label(col_name: str, comment: str) -> str:
            col_lower = (col_name or "").lower().lstrip("cswr_")
            for key, label in _COLUMN_LABEL_MAP.items():
                if key in col_lower:
                    return label
            if comment and not any("一" <= ch <= "鿿" for ch in comment):
                eng_map = {"order number": "订单编号", "order": "订单", "item": "商品",
                          "customer": "客户", "warehouse": "仓库", "promotion": "促销",
                          "ship": "运输", "date": "日期", "quantity": "数量",
                          "price": "价格", "amount": "金额", "discount": "折扣"}
                cl = comment.lower().strip()
                for eng, cn in eng_map.items():
                    if eng in cl:
                        return cn
            return col_name
        # 先给每个候选推断中文标签
        for c in candidates:
            raw_label = str(c.get("label") or "").strip()
            if raw_label in {"", "字段", "关联键"}:
                c["_display_label"] = _guess_label(
                    str(c.get("columnName") or ""),
                    str(c.get("comment") or ""),
                )
            else:
                c["_display_label"] = raw_label
        # 优先维度字段 + 订单/编号类字段排前面
        def _entity_sort_key(c: dict) -> tuple:
            label = str(c.get("_display_label") or "")
            is_dim = 0 if c.get("filterType") == "dimension" else 1
            is_order_like = 0 if any(kw in label for kw in ("订单", "编号", "号")) else 1
            return (is_order_like, is_dim, label)
        def _verified_entity_value(c: dict) -> str:
            """Return a value that is known to hit the live entity lookup query."""
            conn = None
            try:
                import pymysql
                conn_cfg = service._db_connection_for_table(
                    str(c.get("tableName") or ""),
                    str(c.get("schema") or ""),
                )
                conn = pymysql.connect(
                    host=conn_cfg["host"],
                    port=conn_cfg["port"],
                    user=conn_cfg["user"],
                    password=conn_cfg["password"],
                    database=conn_cfg["database"],
                    charset="utf8mb4",
                )
                if c.get("filterType") == "dimension":
                    fact_table = service._sql_table(str(c.get("tableName") or ""), "")
                    dim_table = service._sql_table(str(c.get("dimTable") or ""), "")
                    fact_col = str(c.get("dimFactColumn") or "")
                    dim_pk = str(c.get("dimPrimaryKey") or "")
                    dim_col = str(c.get("dimColumn") or "")
                    if not fact_col or not dim_pk or not dim_col:
                        return ""
                    sql = (
                        f"SELECT d.`{dim_col}` AS v FROM {fact_table} f "
                        f"JOIN {dim_table} d ON f.`{fact_col}` = d.`{dim_pk}` "
                        f"WHERE d.`{dim_col}` IS NOT NULL AND d.`{dim_col}` <> '' "
                        f"LIMIT 1"
                    )
                else:
                    table = service._sql_table(str(c.get("tableName") or ""), "")
                    col = str(c.get("columnName") or "")
                    if not col:
                        return ""
                    sql = (
                        f"SELECT `{col}` AS v FROM {table} "
                        f"WHERE `{col}` IS NOT NULL AND `{col}` <> '' LIMIT 1"
                    )
                with conn.cursor(pymysql.cursors.DictCursor) as cur:
                    cur.execute(sql)
                    row = cur.fetchone()
                return str(row.get("v")).strip() if row and row.get("v") not in (None, "") else ""
            except Exception:
                return ""
            finally:
                try:
                    if conn:
                        conn.close()
                except Exception:
                    pass
        def _entity_prompt_label(c: dict, label: str) -> str:
            table_label = service._table_business_label(str(c.get("sourceTableName") or c.get("tableName") or ""))
            return f"{table_label}-{label}" if table_label and table_label not in label else label
        candidates.sort(key=_entity_sort_key)
        used_entity = set()
        checked_candidates = 0
        for c in candidates:
            if checked_candidates >= 24:
                break
            checked_candidates += 1
            label = str(c.get("_display_label") or "").strip()
            if not label:
                continue
            value = _verified_entity_value(c)
            if not value:
                continue
            prompt_label = _entity_prompt_label(c, label)
            entity_key = (
                str(c.get("sourceTableName") or c.get("tableName") or "").lower(),
                str(c.get("columnName") or "").lower(),
                value,
            )
            if entity_key in used_entity:
                continue
            used_entity.add(entity_key)
            entity_examples.append(f"{prompt_label} ： {value}")
            if len(entity_examples) >= 4:
                break
    except Exception:
        entity_examples = []

    category_defs = [
        ("指标汇总", [
            f"查询{primary_measure['name']}",
            f"统计{second_measure['name']}",
        ]),
        ("指标分析", [
            f"查看{primary_measure['name']}整体情况",
            f"查看{second_measure['name']}整体情况",
            f"对比{primary_measure['name']}与{second_measure['name']}的变化",
        ]),
        ("时间分析", [
            f"按{time_dim['name']}查看{primary_measure['name']}趋势",
            f"按{time_dim['name']}统计{second_measure['name']}",
        ]),
        ("维度分析", [
            f"按{first_dim['name']}查看{primary_measure['name']}",
            f"按{second_dim['name']}统计{second_measure['name']}",
        ]),
        ("结构分析", [
            f"查看{primary_measure['name']}在{first_dim['name']}的贡献结构",
            f"比较{second_measure['name']}在{second_dim['name']}间的结构差异",
        ]),
        ("洞察归因", attribution_questions),
        ("属性检索", entity_examples[:4]),
        ("图谱解释", [
            f"解释{primary_measure['name']}有哪些可分析维度",
            f"解释{second_measure['name']}的口径",
        ]),
    ]

    categories: list[dict[str, Any]] = []
    flat: list[dict[str, str]] = []
    seen_text: set[str] = set()
    for name, texts in category_defs:
        items = []
        for text in texts:
            text = (text or "").strip()
            if not text or text in seen_text:
                continue
            seen_text.add(text)
            item = {"text": text, "kind": name}
            items.append(item)
            flat.append(item)
        if items:
            categories.append({"name": name, "questions": items})

    if not flat:
        item = {"text": "有哪些可查询的指标", "kind": "图谱解释"}
        categories.append({"name": "图谱解释", "questions": [item]})
        flat.append(item)

    return {
        "file": p.name,
        "source": {
            "type": "active_business_kg",
            "mtime": p.stat().st_mtime,
        },
        "categories": categories,
        "questions": flat,
    }


@app.get("/api/business-kg/ontology")
async def business_kg_ontology():
    """Return the fixed RDFS/OWL ontology preamble (classes + properties)."""
    from kg_builder.business_kg.llm_builder import _ONTOLOGY_PREAMBLE
    return {"content": _ONTOLOGY_PREAMBLE}


@app.get("/api/business-kg/default-scenario")
async def business_kg_default_scenario():
    """Return the checked-in default business scenario Turtle source."""
    if not DEFAULT_BKG_SCENARIO_PATH.exists():
        return JSONResponse(status_code=404, content={"error": "默认业务场景源文件不存在"})
    return {
        "filename": DEFAULT_BKG_SCENARIO_PATH.name,
        "content": DEFAULT_BKG_SCENARIO_PATH.read_text(encoding="utf-8"),
    }


@app.get("/api/business-kg/prompt")
async def business_kg_prompt():
    """Return the LLM system prompt used for business KG generation."""
    from kg_builder.business_kg.llm_builder import _SYSTEM_PROMPT
    return {"content": _SYSTEM_PROMPT}


@app.get("/api/business-kg/generate-hint")
async def business_kg_generate_hint(file: str = ""):
    """
    根据数据源图谱生成业务领域提示词。
    读取选中的数据源 KG，提取表/列/关系摘要，结合本体结构和提示词模板，
    调用 LLM 生成包含指标、维度和示例逻辑的中文领域提示。
    """
    global _current_kg_path
    from rdflib import Graph, RDF, RDFS, Namespace
    import re as _re

    # 确定数据源 KG 文件
    if file:
        src_path = (OUTPUT_DIR / Path(file).name).resolve()
    elif _current_kg_path and _current_kg_path.exists():
        src_path = _current_kg_path
    else:
        files = sorted(OUTPUT_DIR.glob("kg_*.ttl"), key=lambda p: p.stat().st_mtime)
        src_path = files[-1] if files else None
    if not src_path or not src_path.exists():
        return JSONResponse(status_code=404, content={"error": "未找到数据源图谱文件"})

    # 读取数据源 KG
    g = Graph()
    try:
        g.parse(str(src_path), format="turtle")
    except Exception as e:
        return JSONResponse(status_code=500, content={"error": f"KG 解析失败: {e}"})

    DB = Namespace("http://kg.local/db#")

    # 提取表摘要：表名 + 中文 label + 列数
    tables_info: list[dict] = []
    _zh_re = _re.compile(r'[一-鿿]')
    for t in g.subjects(RDF.type, DB.Table):
        name = g.value(t, DB.tableName) or g.value(t, DB.name)
        if not name:
            continue
        name = str(name)
        # 优先取中文 label
        zh = None
        for lbl in g.objects(t, RDFS.label):
            if getattr(lbl, "language", None) == "zh" or _zh_re.search(str(lbl)):
                zh = str(lbl)
                break
        # 统计列数
        cols = list(g.objects(t, DB.containsColumn))
        pk_cols = []
        fk_cols = []
        for col in cols:
            col_name = g.value(col, DB.name)
            if not col_name:
                continue
            cn = str(col_name)
            # 检查是否是主键
            is_pk = any(str(g.value(c, DB.name)) == "PRIMARY"
                       for c in g.subjects(DB.coversColumn, col)
                       if g.value(c, None) == col)
            # 检查是否是外键
            is_fk = g.value(col, DB.references) is not None
            if is_pk:
                pk_cols.append(cn)
            elif is_fk:
                fk_cols.append(cn)
        # 取有注释的列
        commented_cols = []
        for col in cols:
            cn = g.value(col, DB.name)
            if not cn:
                continue
            cn = str(cn)
            if cn in pk_cols or cn in fk_cols:
                continue
            comment = g.value(col, RDFS.comment)
            if comment:
                commented_cols.append(f"{cn}({str(comment)[:20]})")
        # 采样一些列名
        sample_cols = [str(g.value(c, DB.name)) for c in cols[:8] if g.value(c, DB.name)]
        tables_info.append({
            "name": name,
            "label": zh or name,
            "col_count": len(cols),
            "pk": pk_cols,
            "fk": fk_cols[:5],
            "sample_cols": sample_cols,
            "commented": commented_cols[:5],
        })

    if not tables_info:
        return JSONResponse(status_code=404, content={"error": "数据源图谱中未找到表信息"})

    # 构建表摘要文本
    lines = []
    for t in tables_info[:30]:  # 最多30张表
        parts = [f"- {t['name']}（{t['label']}），{t['col_count']} 列"]
        if t["pk"]:
            parts.append(f"主键: {', '.join(t['pk'])}")
        if t["fk"]:
            parts.append(f"外键: {', '.join(t['fk'])}")
        if t["commented"]:
            parts.append(f"注释列: {', '.join(t['commented'])}")
        elif t["sample_cols"]:
            parts.append(f"列: {', '.join(t['sample_cols'][:6])}")
        lines.append("；".join(parts))

    table_summary = "\n".join(lines)

    # 获取本体结构摘要（关键类）
    ontology_classes = [
        "ind:Measure（指标）— 含 code/cnName/measTypeCode/definition/hasMeasureApp",
        "ind:Dimension（维度）— 含 code/cnName/dimTypeCode/hasDimApp",
        "ind:MeasureApp（指标应用）— 指标在事实表上的聚合定义，含 expression/factColumn/appliesToTable",
        "ind:DimensionApp（维度应用）— 维度在事实表上的列映射，含 dimColumn/dimFactColumn/dimPrimaryKey/dimTable/dimColumnExpr；dimTypeCode=2 且 dimColumn≠dimPrimaryKey 时必须填写 dimColumnExpr={d}.<dimColumn>",
        "ind:DwTable（数仓表）— 含 schemaName/tableName/sourceTypeCode",
        "ind:DwColumn（数仓字段）— 含 columnName/columnType/cnName/columnComment/isPrimaryKey/isNullable，并由 DwTable hasColumn 关联",
        "ind:Category（分类）— 指标分类",
    ]

    # 构建 LLM prompt
    from kg_builder.utils.llm_config import chat_completions_url, llm_config_from_env, llm_request_headers
    llm_config = llm_config_from_env(BASE_DIR)
    api_key = llm_config["api_key"]
    base_url = llm_config["base_url"]
    model = llm_config["model"]

    system = (
        "你是一位数据库知识图谱专家。请根据给定的数据源表结构摘要，"
        "生成一段中文「业务领域提示」，帮助 LLM 理解这个数据库的业务含义。\n\n"
        "提示应包含：\n"
        "1. **业务域概述**：1-2句描述这个数据库所属的业务领域\n"
        "2. **核心指标建议**：列出 5-8 个可以从这些表中推导的有业务意义的指标"
        "（如总数、金额、比率、平均值等），说明每个指标的计算逻辑和涉及的表\n"
        "3. **核心维度建议**：列出 4-6 个重要的分析维度（时间、分类、状态等），"
        "说明每个维度的来源表和字段\n"
        "4. **数据关系要点**：说明表之间的关键关联关系\n"
        "5. **示例指标定义**：给出 2 个完整的指标示例，包含指标名称、计算口径、"
        "数据来源表、关联维度\n\n"
        "如果维度来自维表属性列（例如仓库城市、客户等级、促销类型），请明确说明事实表外键、"
        "维表主键和属性列；这类 dimTypeCode=2 维度在业务图谱中必须用 dimColumnExpr={d}.<属性列> "
        "按真实属性列分组，不能只按共享外键分组。\n\n"
        "用自然段落，不要列表式。要基于给定的表名/列名，推测真实业务含义。"
        "不要编造不存在的表名或列名。全程中文。"
    )

    user = (
        f"数据源图谱文件：{src_path.name}，共 {len(tables_info)} 张表\n\n"
        f"本体类定义：\n" + "\n".join(ontology_classes) + "\n\n"
        f"数据库表结构：\n{table_summary}\n\n"
        "请根据以上信息，生成业务领域提示。"
    )

    import json as _json, urllib.request as _ureq
    combined = system + "\n\n" + user
    payload = _json.dumps({
        "model": model, "max_tokens": 2048,
        "messages": [{"role": "user", "content": combined}],
    }).encode("utf-8")
    req = _ureq.Request(
        chat_completions_url(base_url), data=payload,
        headers=llm_request_headers(llm_config),
        method="POST",
    )
    try:
        with _urlopen(req, timeout=120) as resp:
            data = _json.loads(resp.read().decode("utf-8"))
        content = data["choices"][0]["message"]["content"]
        # 去除部分模型的思考标签
        content = _re.sub(r"<think>.*?</think>", "", content, flags=_re.DOTALL)
        content = _re.sub(r"<thinking>.*?</thinking>", "", content, flags=_re.DOTALL)
        content = content.strip()
    except Exception as e:
        # LLM 不可用时返回基于表结构的简单摘要
        content = _fallback_hint(tables_info)

    return {"content": content.strip()}


def _fallback_hint(tables_info: list[dict]) -> str:
    """LLM 不可用时的简单领域提示。"""
    fact_tables = [t for t in tables_info if t["fk"]]
    dim_tables = [t for t in tables_info if not t["fk"] and t["pk"]]
    lines = [
        f"这是一个包含 {len(tables_info)} 张表的数据库。",
        f"事实表：{', '.join(t['name'] for t in fact_tables[:8])}。",
        f"维度表：{', '.join(t['name'] for t in dim_tables[:8])}。",
        "请分析销售、退货等业务流程，生成有业务意义的指标和维度。",
    ]
    return "\n".join(lines)


@app.delete("/api/business-kg/clear")
async def business_kg_clear():
    """Delete the indicator-data.ttl file in the business KG output directory."""
    global _bkg_turtle, _bkg_graph, _current_bkg_path
    if _bkg_state.get("status") == "running":
        return JSONResponse(status_code=409, content={"error": "图谱正在生成中，请稍后再试"})
    files = [
        BKG_DIR / "indicator-data.ttl",
        _bkg_inferred_path(),
        SEMANTIC_SOURCE_MANIFEST_PATH,
    ]
    deleted = 0
    for f in files:
        try:
            f.unlink()
            deleted += 1
        except Exception:
            pass
    # Reset in-memory state
    _bkg_turtle = ""
    _bkg_graph = None
    _current_bkg_path = None
    return {"deleted": deleted, "ok": True}


@app.post("/api/business-kg/reasoning/build")
async def business_kg_reasoning_build():
    """Rebuild deterministic reasoning triples for the active business KG."""
    p = BKG_DIR / "indicator-data.ttl"
    if not p.exists():
        return JSONResponse(status_code=404, content={"error": "尚未生成业务图谱"})
    try:
        inferred = _materialize_business_inferences(p.read_text(encoding="utf-8"))
        return {"ok": True, "file": _bkg_inferred_path().name, "triples": len(inferred)}
    except Exception as exc:
        return JSONResponse(status_code=500, content={"error": str(exc)})


@app.get("/api/business-kg/reasoning/turtle")
async def business_kg_reasoning_turtle():
    p = _bkg_inferred_path()
    if not p.exists():
        return JSONResponse(status_code=404, content={"error": "尚未生成推理图谱"})
    return HTMLResponse(p.read_text(encoding="utf-8"), media_type="text/plain")


def _bkg_val(graph, node, ns, prop: str) -> str:
    value = graph.value(node, ns[prop])
    return str(value) if value is not None else ""


def _bkg_entity_label(graph, node, ind) -> dict[str, Any]:
    return {
        "uri": str(node),
        "code": _bkg_val(graph, node, ind, "code"),
        "name": _bkg_val(graph, node, ind, "cnName") or _bkg_val(graph, node, ind, "enName") or _bkg_val(graph, node, ind, "tableName") or str(node).rsplit("/", 1)[-1],
    }


def _bkg_evidence(graph, subject, predicate, obj, ind) -> dict[str, Any]:
    from rdflib.namespace import RDF

    for inf in graph.subjects(RDF.subject, subject):
        if graph.value(inf, RDF.predicate) == predicate and graph.value(inf, RDF.object) == obj:
            return {
                "ruleId": _bkg_val(graph, inf, ind, "inferredByRule"),
                "confidence": _bkg_val(graph, inf, ind, "confidence"),
                "evidencePath": _bkg_val(graph, inf, ind, "evidencePath"),
                "generatedAt": _bkg_val(graph, inf, ind, "generatedAt"),
            }
    return {}


def _business_reasoning_for_measure(code: str) -> dict[str, Any]:
    from rdflib import Namespace, RDF

    graph = _load_business_graph(include_inferred=True)
    ind = Namespace("http://indicator.insightmind.com/ontology#")
    measure = next((node for node in graph.subjects(RDF.type, ind.Measure) if _bkg_val(graph, node, ind, "code") == code), None)
    if not measure:
        raise KeyError(f"指标不存在: {code}")

    def relation(prop_name: str):
        prop = ind[prop_name]
        rows = []
        for target in graph.objects(measure, prop):
            item = _bkg_entity_label(graph, target, ind)
            item.update(_bkg_evidence(graph, measure, prop, target, ind))
            rows.append(item)
        return sorted(rows, key=lambda x: x.get("code") or x.get("name") or "")

    apps = []
    for app in graph.objects(measure, ind.hasMeasureApp):
        table = graph.value(app, ind.appliesToTable)
        apps.append({
            "uri": str(app),
            "factColumn": _bkg_val(graph, app, ind, "factColumn"),
            "expression": _bkg_val(graph, app, ind, "expression"),
            "whereCondition": _bkg_val(graph, app, ind, "whereCondition"),
            "table": _bkg_entity_label(graph, table, ind) if table else None,
        })

    return {
        "measure": _bkg_entity_label(graph, measure, ind),
        "definition": _bkg_val(graph, measure, ind, "definition"),
        "caliber": _bkg_val(graph, measure, ind, "caliber"),
        "measureApps": apps,
        "compatibleDimensions": relation("compatibleDimension"),
        "upstreamMeasures": relation("upstreamMeasure"),
        "downstreamMeasures": relation("downstreamMeasure"),
    }


@app.get("/api/business-kg/reasoning/measure/{code}")
async def business_kg_reasoning_measure(code: str):
    try:
        return _business_reasoning_for_measure(code)
    except FileNotFoundError as exc:
        return JSONResponse(status_code=404, content={"error": str(exc)})
    except KeyError as exc:
        return JSONResponse(status_code=404, content={"error": str(exc)})
    except Exception as exc:
        return JSONResponse(status_code=500, content={"error": str(exc)})


@app.get("/api/business-kg/reasoning/impact")
async def business_kg_reasoning_impact(target: str, target_type: str = "auto"):
    """Analyze business KG impact before changing a table, field, or measure caliber."""
    from rdflib import Namespace, RDF

    try:
        graph = _load_business_graph(include_inferred=True)
    except FileNotFoundError as exc:
        return JSONResponse(status_code=404, content={"error": str(exc)})

    ind = Namespace("http://indicator.insightmind.com/ontology#")
    target = (target or "").strip()
    if not target:
        return JSONResponse(status_code=400, content={"error": "target 不能为空"})

    def norm(s: str) -> str:
        return str(s or "").strip().lower()

    def table_matches(table) -> bool:
        names = [_bkg_val(graph, table, ind, "tableName"), _bkg_val(graph, table, ind, "schemaName") + "." + _bkg_val(graph, table, ind, "tableName"), str(table).rsplit("/", 1)[-1]]
        return norm(target) in {norm(x) for x in names if x}

    def measure_matches(measure) -> bool:
        values = [_bkg_val(graph, measure, ind, "code"), _bkg_val(graph, measure, ind, "cnName"), _bkg_val(graph, measure, ind, "enName")]
        return norm(target) in {norm(x) for x in values if x}

    def field_matches(app, *props) -> bool:
        return any(norm(_bkg_val(graph, app, ind, prop)) == norm(target) for prop in props)

    impacted_measures: dict[str, dict] = {}
    impacted_dimensions: dict[str, dict] = {}
    impacted_tables: dict[str, dict] = {}
    paths: list[dict] = []

    def add_measure(measure, reason: str, path: str):
        if not measure:
            return
        item = _bkg_entity_label(graph, measure, ind)
        impacted_measures[item["code"] or item["uri"]] = item
        paths.append({"type": "measure", "code": item["code"], "name": item["name"], "reason": reason, "path": path})
        for downstream in graph.objects(measure, ind.downstreamMeasure):
            ds = _bkg_entity_label(graph, downstream, ind)
            impacted_measures[ds["code"] or ds["uri"]] = ds
            evidence = _bkg_evidence(graph, measure, ind.downstreamMeasure, downstream, ind)
            paths.append({
                "type": "measure",
                "code": ds["code"],
                "name": ds["name"],
                "reason": "下游指标依赖",
                "path": evidence.get("evidencePath") or f"{item['code']} -> downstreamMeasure -> {ds['code']}",
            })

    resolved_type = target_type
    if target_type == "auto":
        if any(measure_matches(m) for m in graph.subjects(RDF.type, ind.Measure)):
            resolved_type = "measure"
        elif any(table_matches(t) for t in graph.subjects(RDF.type, ind.DwTable)):
            resolved_type = "table"
        else:
            resolved_type = "field"

    if resolved_type in ("table", "auto"):
        for table in graph.subjects(RDF.type, ind.DwTable):
            if not table_matches(table):
                continue
            table_item = _bkg_entity_label(graph, table, ind)
            impacted_tables[table_item["name"] or table_item["uri"]] = table_item
            for app in graph.subjects(ind.appliesToTable, table):
                add_measure(graph.value(predicate=ind.hasMeasureApp, object=app), "事实表变更", f"{table_item['name']} -> appliesToTable <- {str(app).rsplit('/', 1)[-1]}")
            for app in graph.subjects(ind.dimFactTable, table):
                dim = graph.value(predicate=ind.hasDimApp, object=app)
                if dim:
                    item = _bkg_entity_label(graph, dim, ind)
                    impacted_dimensions[item["code"] or item["uri"]] = item
                    paths.append({"type": "dimension", "code": item["code"], "name": item["name"], "reason": "维度事实表变更", "path": f"{table_item['name']} -> dimFactTable <- {str(app).rsplit('/', 1)[-1]}"})

    if resolved_type in ("field", "auto"):
        for app in graph.subjects(RDF.type, ind.MeasureApp):
            if field_matches(app, "factColumn"):
                add_measure(graph.value(predicate=ind.hasMeasureApp, object=app), "指标聚合字段变更", f"{target} -> factColumn <- {str(app).rsplit('/', 1)[-1]}")
        for app in graph.subjects(RDF.type, ind.DimensionApp):
            if field_matches(app, "dimFactColumn", "dimPrimaryKey", "dimColumn", "masterPrimaryKey"):
                dim = graph.value(predicate=ind.hasDimApp, object=app)
                if dim:
                    item = _bkg_entity_label(graph, dim, ind)
                    impacted_dimensions[item["code"] or item["uri"]] = item
                    paths.append({"type": "dimension", "code": item["code"], "name": item["name"], "reason": "维度映射字段变更", "path": f"{target} -> DimensionApp {str(app).rsplit('/', 1)[-1]}"})
                    for measure in graph.subjects(ind.compatibleDimension, dim):
                        add_measure(measure, "可分析维度受影响", f"{_bkg_val(graph, measure, ind, 'code')} -> compatibleDimension -> {item['code']}")

    if resolved_type in ("measure", "caliber", "auto"):
        for measure in graph.subjects(RDF.type, ind.Measure):
            if measure_matches(measure):
                add_measure(measure, "指标口径变更", f"{target} -> Measure")

    return {
        "target": target,
        "targetType": resolved_type,
        "summary": {
            "measureCount": len(impacted_measures),
            "dimensionCount": len(impacted_dimensions),
            "tableCount": len(impacted_tables),
            "pathCount": len(paths),
        },
        "measures": sorted(impacted_measures.values(), key=lambda x: x.get("code") or ""),
        "dimensions": sorted(impacted_dimensions.values(), key=lambda x: x.get("code") or ""),
        "tables": sorted(impacted_tables.values(), key=lambda x: x.get("name") or ""),
        "paths": paths[:200],
    }


@app.get("/api/business-kg/turtle")
async def business_kg_turtle(file: str = ""):
    # If a specific file is requested, load it directly
    if file:
        p = (BKG_DIR / Path(file).name).resolve()
        if str(p).startswith(str(BKG_DIR.resolve())) and p.exists():
            return {"turtle": p.read_text(encoding="utf-8")}
        return JSONResponse(status_code=404, content={"error": "文件不存在"})
    if _bkg_turtle:
        return {"turtle": _bkg_turtle}
    # Fallback: load from active file
    if _current_bkg_path and _current_bkg_path.exists():
        return {"turtle": _current_bkg_path.read_text(encoding="utf-8")}
    # Last resort: indicator-data.ttl
    files = [BKG_DIR / "indicator-data.ttl"]
    if files[0].exists():
        return {"turtle": files[0].read_text(encoding="utf-8")}
    return JSONResponse(status_code=404, content={"error": "尚未生成业务图谱"})


@app.get("/api/business-kg/graph/nodes")
async def business_kg_nodes(file: str = ""):
    """Return vis-network nodes/edges from the business KG."""
    global _bkg_graph
    from rdflib import Graph, RDF, RDFS, OWL, URIRef, Namespace
    import re

    IND = Namespace("http://indicator.insightmind.com/ontology#")

    # ── Group config for ind: classes ──────────────────────────────────── #
    IND_CLASS_GROUPS = {
        str(IND.Measure):                   ("biz_measure",   "#7f8d9f"),
        str(IND.Dimension):                 ("biz_dimension",  "#6e9280"),
        str(IND.DwTable):                   ("biz_dwtable",    "#5f7f9f"),
        str(IND.Category):                  ("biz_category",   "#817991"),
        str(IND.Hierarchy):                 ("biz_hierarchy",  "#6a9a95"),
        str(IND.MeasureApplication):        ("biz_mapp",       "#a58b61"),
        str(IND.DimensionApplication):      ("biz_dapp",       "#8ca0b4"),
        str(IND.DimensionDimtableConnect):  ("biz_dconn",      "#8f9aaa"),
        str(IND.Level):                     ("biz_level",      "#9b8f7a"),
        str(IND.NaturalDateMapping):        ("biz_ndmap",      "#5f6b78"),
        str(IND.MeasureAppFilter):          ("biz_mfilt",      "#9b7c7c"),
    }

    g = None
    if file:
        p = (BKG_DIR / Path(file).name).resolve()
        if str(p).startswith(str(BKG_DIR.resolve())) and p.exists():
            g = Graph()
            g.parse(str(p), format="turtle")
    if g is None:
        g = _bkg_graph
    if g is None:
        # Try loading from indicator-data.ttl
        candidates = [BKG_DIR / "indicator-data.ttl"]
        if not candidates[0].exists():
            return JSONResponse(status_code=404, content={"error": "尚未生成业务图谱"})
        g = Graph()
        g.parse(str(candidates[0]), format="turtle")
        _bkg_graph = g

    _ZH_RE = re.compile(r"[\u4e00-\u9fff]")

    def _tail(uri: str) -> str:
        return uri.split("#")[-1].split("/")[-1]

    def _label(uri) -> str:
        for lbl in g.objects(uri, RDFS.label):
            if getattr(lbl, "language", None) == "zh":
                return str(lbl)
        for lbl in g.objects(uri, RDFS.label):
            if _ZH_RE.search(str(lbl)):
                return str(lbl)
        v = g.value(uri, RDFS.label)
        return str(v) if v else _tail(str(uri))

    def _comment(uri) -> str:
        c = g.value(uri, RDFS.comment)
        return str(c) if c else ""

    nodes: list[dict] = []
    edges: list[dict] = []
    node_ids: set[str] = set()
    edge_id = 0

    # OWL Classes
    for cls_uri in set(g.subjects(RDF.type, OWL.Class)):
        nid = str(cls_uri)
        if nid in node_ids:
            continue
        node_ids.add(nid)
        lbl     = _label(cls_uri)
        comment = _comment(cls_uri)
        # Check skos:relatedMatch for source table annotation
        SKOS_RELATED = URIRef("http://www.w3.org/2004/02/skos/core#relatedMatch")
        sources = [str(s) for s in g.objects(cls_uri, SKOS_RELATED)]
        see_also = [str(s) for s in g.objects(cls_uri, RDFS.seeAlso)]
        sources += see_also
        title = lbl
        if comment:
            title += f"\n{comment}"
        if sources:
            title += f"\n来源: {', '.join(sources[:3])}"
        nodes.append({
            "id": nid, "label": lbl, "group": "biz_class",
            "title": title, "orig": _tail(nid),
        })

    # OWL ObjectProperties → edges between classes
    for prop_uri in set(g.subjects(RDF.type, OWL.ObjectProperty)):
        domain = g.value(prop_uri, RDFS.domain)
        rng    = g.value(prop_uri, RDFS.range)
        if domain and rng:
            sid, oid = str(domain), str(rng)
            if sid in node_ids and oid in node_ids:
                lbl = _label(prop_uri)
                edges.append({
                    "id": f"ep{edge_id}", "from": sid, "to": oid,
                    "label": lbl, "color": "#a58b61",
                })
                edge_id += 1
        # Also add as node for detail viewing
        nid = str(prop_uri)
        if nid not in node_ids:
            node_ids.add(nid)
            lbl = _label(prop_uri)
            comment = _comment(prop_uri)
            dom_lbl = _label(domain) if domain else "?"
            rng_lbl = _label(rng)    if rng    else "?"
            nodes.append({
                "id": nid, "label": lbl, "group": "biz_prop",
                "title": f"{comment}\n{dom_lbl} → {rng_lbl}" if comment else f"{dom_lbl} → {rng_lbl}",
                "orig": _tail(nid),
            })

    # OWL DatatypeProperties
    for prop_uri in set(g.subjects(RDF.type, OWL.DatatypeProperty)):
        nid = str(prop_uri)
        if nid in node_ids:
            continue
        node_ids.add(nid)
        lbl     = _label(prop_uri)
        comment = _comment(prop_uri)
        domain  = g.value(prop_uri, RDFS.domain)
        rng     = g.value(prop_uri, RDFS.range)
        dom_lbl = _label(domain) if domain else "?"
        rng_str = _tail(str(rng)) if rng else "literal"
        nodes.append({
            "id": nid, "label": lbl, "group": "biz_dprop",
            "title": f"{comment}\n域: {dom_lbl}  值类型: {rng_str}" if comment else f"域: {dom_lbl}  值类型: {rng_str}",
            "orig": _tail(nid),
        })
        # Draw edge domain → dprop
        if domain and str(domain) in node_ids:
            edges.append({
                "id": f"ed{edge_id}", "from": str(domain), "to": nid,
                "label": lbl, "color": "#aeb8c4", "dashes": True,
            })
            edge_id += 1

    # rdfs:subClassOf hierarchy
    for sub, sup in g.subject_objects(RDFS.subClassOf):
        sid, oid = str(sub), str(sup)
        if sid in node_ids and oid in node_ids:
            edges.append({
                "id": f"es{edge_id}", "from": sid, "to": oid,
                "label": "子类", "color": "#5f7f9f", "dashes": False,
            })
            edge_id += 1


    # owl:NamedIndividual — legacy support
    for ind_uri in set(g.subjects(RDF.type, OWL.NamedIndividual)):
        nid = str(ind_uri)
        if nid in node_ids:
            continue
        node_ids.add(nid)
        lbl     = _label(ind_uri)
        comment = _comment(ind_uri)
        cls_uris = [str(t) for t in g.objects(ind_uri, RDF.type)
                    if str(t) != str(OWL.NamedIndividual)
                    and (t, RDF.type, OWL.Class) in g]
        cls_lbl = _label(URIRef(cls_uris[0])) if cls_uris else ""
        title = lbl
        if comment:
            title += f"\n{comment}"
        if cls_lbl:
            title += f"\n类型: {cls_lbl}"
        nodes.append({
            "id": nid, "label": lbl, "group": "biz_individual",
            "title": title, "orig": _tail(nid),
        })
        for cls_uri_str in cls_uris:
            if cls_uri_str in node_ids:
                edges.append({
                    "id": f"ei{edge_id}", "from": nid, "to": cls_uri_str,
                    "label": "instanceOf", "color": "#8f9aaa", "dashes": True,
                })
                edge_id += 1

    # ind: typed instances (new indicator-platform ontology)
    for cls_uri_str, (group, color) in IND_CLASS_GROUPS.items():
        cls_uri = URIRef(cls_uri_str)
        for inst_uri in set(g.subjects(RDF.type, cls_uri)):
            nid = str(inst_uri)
            if nid in node_ids:
                continue
            node_ids.add(nid)
            lbl     = _label(inst_uri)
            comment = _comment(inst_uri)
            # Build tooltip from key datatype properties
            code_v  = g.value(inst_uri, IND.code)
            cn_v    = g.value(inst_uri, IND.cnName)
            title   = str(cn_v) if cn_v else lbl
            if code_v:
                title += f"\n编码: {code_v}"
            if comment:
                title += f"\n{comment}"
            nodes.append({
                "id": nid, "label": lbl, "group": group,
                "color": color, "title": title, "orig": _tail(nid),
            })

    # ind: ObjectProperty edges between instances
    for prop_uri in set(g.subjects(RDF.type, OWL.ObjectProperty)):
        for subj, obj in g.subject_objects(prop_uri):
            sid, oid = str(subj), str(obj)
            if sid in node_ids and oid in node_ids:
                lbl = _label(prop_uri)
                edges.append({
                    "id": f"ep{edge_id}", "from": sid, "to": oid,
                    "label": lbl, "color": "#aeb8c4", "dashes": False,
                })
                edge_id += 1

    return {"nodes": nodes, "edges": edges}


@app.post("/api/business-kg/validate")
async def business_kg_validate(file: str = ""):
    """校验业务图谱中每个指标+日期维度的查询有效性（一次性返回完整结果）"""
    ttl_path = _resolve_bkg_path(file)
    if not ttl_path:
        return JSONResponse(status_code=404, content={"error": "未找到业务图谱文件"})
    results = []
    total = 0
    summary = {"passed": 0, "failed": 0}
    for ev in _validate_bkg_iter(ttl_path):
        if ev["type"] == "init":
            total = ev["total"]
        elif ev["type"] == "measure":
            results.append(ev["result"])
        elif ev["type"] == "summary":
            summary = {"passed": ev["passed"], "failed": ev["failed"]}
    return {"file": str(ttl_path.name), "total": total,
            "passed": summary["passed"], "failed": summary["failed"], "results": results}


@app.get("/api/business-kg/validate/stream")
async def business_kg_validate_stream(file: str = ""):
    """SSE 流式校验：每完成一个指标即推送一条事件。"""
    ttl_path = _resolve_bkg_path(file)
    if not ttl_path:
        return JSONResponse(status_code=404, content={"error": "未找到业务图谱文件"})

    def gen():
        yield f"data: {json.dumps({'type': 'file', 'file': ttl_path.name})}\n\n"
        try:
            for ev in _validate_bkg_iter(ttl_path):
                yield f"data: {json.dumps(ev, ensure_ascii=False)}\n\n"
        except Exception as e:
            yield f"data: {json.dumps({'type': 'error', 'error': str(e)[:300]}, ensure_ascii=False)}\n\n"
        yield "data: __DONE__\n\n"
    return StreamingResponse(gen(), media_type="text/event-stream")


def _resolve_bkg_path(file: str) -> Optional[Path]:
    def active_business_graph() -> Optional[Path]:
        if _current_bkg_path and _current_bkg_path.exists():
            try:
                _current_bkg_path.resolve().relative_to(BKG_DIR.resolve())
                return _current_bkg_path
            except ValueError:
                pass
        default = BKG_DIR / "indicator-data.ttl"
        return default if default.exists() else None

    if file:
        filename = Path(file).name
        p = (BKG_DIR / filename).resolve()
        if str(p).startswith(str(BKG_DIR.resolve())) and p.exists():
            return p

        # Backward compatibility for a cached frontend that used to send the
        # selected source KG (for example kg_tpcds.ttl). A source KG cannot be
        # accuracy-validated as an indicator graph, so transparently use the
        # active business KG instead of returning an immediate 404.
        source_kg = (OUTPUT_DIR / filename).resolve()
        if (
            str(source_kg).startswith(str(OUTPUT_DIR.resolve()))
            and source_kg.parent == OUTPUT_DIR.resolve()
            and source_kg.exists()
        ):
            return active_business_graph()
        return None
    return active_business_graph()


def _validate_bkg_iter(ttl_path: Path):
    """逐指标校验生成器，先 yield init/measure/summary 事件。"""
    from rdflib import Graph, Namespace
    import urllib.request as _ureq

    IND = Namespace("http://indicator.insightmind.com/ontology#")
    g = Graph()
    g.parse(str(ttl_path), format="turtle")

    # ── 从 TTL 动态推导每个指标的维度关联 ──────────────────────────────── #
    dimapp_to_dim = {}
    for dim_inst, _, code_o in g.triples((None, IND.code, None)):
        code_s = str(code_o)
        if not code_s.startswith("DIM_"):
            continue
        for dapp in g.objects(dim_inst, IND.hasDimApp):
            dimapp_to_dim[str(dapp)] = code_s

    dimapp_to_table = {}
    for s, p, o in g.triples((None, IND.dimFactTable, None)):
        da_uri = str(s)
        if da_uri in dimapp_to_dim:
            tbl_name = str(g.value(o, IND.tableName) or "")
            if tbl_name:
                dimapp_to_table[da_uri] = tbl_name

    seen = set()
    measures = []
    for inst in g.subjects(None, None):
        code = g.value(inst, IND.code)
        if not code or not str(code).startswith("MEAS_"):
            continue
        code = str(code)
        if code in seen:
            continue
        seen.add(code)
        mtype = g.value(inst, IND.measTypeCode)
        is_derived = mtype and int(float(str(mtype))) == 1

        tables = set()
        for mapp in g.objects(inst, IND.hasMeasureApp):
            tbl = g.value(mapp, IND.appliesToTable)
            if tbl:
                tables.add(str(g.value(tbl, IND.tableName) or ""))

        date_dims = []
        biz_dims = []
        seen_dim = set()
        for da_uri, tbl_name in dimapp_to_table.items():
            if tbl_name in tables:
                dim_code = dimapp_to_dim.get(da_uri, "")
                if dim_code and dim_code not in seen_dim:
                    seen_dim.add(dim_code)
                    dim_uri = None
                    for _di, _, _dc in g.triples((None, IND.code, None)):
                        if str(_dc) == dim_code:
                            dim_uri = _di
                            break
                    dim_lvl = str(g.value(dim_uri, IND.levelCode) or "") if dim_uri else ""
                    dim_cn = str(g.value(dim_uri, IND.cnName) or "") if dim_uri else ""
                    if dim_lvl in ("day", "week", "month", "quarter", "year"):
                        date_dims.append((dim_code, dim_cn, dim_lvl))
                    else:
                        biz_dims.append((dim_code, dim_cn))

        lvl_order = {"day": 0, "week": 1, "month": 2, "quarter": 3, "year": 4}
        date_dims.sort(key=lambda x: lvl_order.get(x[2], 99))

        measures.append({
            "code": code,
            "cnName": str(g.value(inst, IND.cnName) or ""),
            "dateDims": [{"code": c, "cnName": n, "level": l} for c, n, l in date_dims],
            "bizDims": [{"code": c, "cnName": n} for c, n in biz_dims],
            "isDerived": is_derived,
        })

    yield {"type": "init", "total": len(measures), "file": str(ttl_path.name)}

    def _fmt_date(val, level):
        if not val or level not in ("day", "week", "month", "quarter", "year"):
            return val
        try:
            from datetime import datetime
            dt = datetime.strptime(str(val)[:10], "%Y-%m-%d")
            if level == "day":    return dt.strftime("%Y-%m-%d")
            if level == "week":
                iso = dt.isocalendar()
                return f"{iso[0]}{iso[1]:02d}"
            if level == "month":  return dt.strftime("%Y%m")
            if level == "quarter":
                q = (dt.month - 1) // 3 + 1
                return f"{dt.year}{q}"
            if level == "year":   return dt.strftime("%Y")
        except Exception:
            return val
        return val

    def _query_da(meas_code, dim_code):
        payload = json.dumps({
            "configureList": [
                {"code": meas_code, "order": {"sortType": 0}, "ratioList": [], "alias": "v"},
                {"code": dim_code, "order": {"sortType": 1}, "ratioList": [], "alias": "d"},
            ],
            "filterList": [], "pageSize": 1, "pageNum": 1,
        }).encode("utf-8")
        try:
            req = _ureq.Request(_DATA_AGENT_URL, data=payload,
                                headers={"Content-Type": "application/json"}, method="POST")
            with _urlopen(req, timeout=20) as resp:
                data = json.loads(resp.read().decode("utf-8"))
            if data.get("code") == 200 and data.get("data") and data["data"].get("cellList"):
                row = data["data"]["cellList"][0]
                sample = {}
                for cell in row:
                    sample[cell.get("alias") or cell.get("code", "")] = cell.get("data", "-")
                return {"ok": True, "rows": len(data["data"]["cellList"]), "sample": sample}
            return {"ok": False, "error": (data.get("errorMessage") or data.get("message") or "未知错误")[:200]}
        except Exception as e:
            return {"ok": False, "error": str(e)[:200]}

    passed_all = 0
    failed_any = 0
    for idx, m in enumerate(measures, 1):
        date_results = []
        for dd in m["dateDims"]:
            r = _query_da(m["code"], dd["code"])
            if r.get("ok") and r.get("sample") and dd["code"] in r["sample"]:
                r["sample"][dd["code"]] = _fmt_date(r["sample"][dd["code"]], dd["level"])
            date_results.append({"dim": dd["cnName"], "dimCode": dd["code"], "level": dd["level"], **r})

        biz_results = []
        day_code = next((dd["code"] for dd in m["dateDims"] if dd["level"] == "day"), "")
        for bd in m["bizDims"]:
            payload = json.dumps({
                "configureList": [
                    {"code": m["code"], "order": {"sortType": 0}, "ratioList": [], "alias": "v"},
                    {"code": bd["code"], "order": {"sortType": 0}, "ratioList": [], "alias": "b"},
                    {"code": day_code, "order": {"sortType": 1}, "ratioList": [], "alias": "d"},
                ],
                "filterList": [], "pageSize": 1, "pageNum": 1,
            }).encode("utf-8") if day_code else None
            if payload:
                try:
                    req = _ureq.Request(_DATA_AGENT_URL, data=payload,
                                        headers={"Content-Type": "application/json"}, method="POST")
                    with _urlopen(req, timeout=20) as resp:
                        data = json.loads(resp.read().decode("utf-8"))
                    if data.get("code") == 200 and data.get("data") and data["data"].get("cellList"):
                        row = data["data"]["cellList"][0]
                        sample = {}
                        for cell in row:
                            sample[cell.get("alias") or cell.get("code", "")] = cell.get("data", "-")
                        if day_code and day_code in sample:
                            sample[day_code] = _fmt_date(sample[day_code], "day")
                        biz_results.append({"dim": bd["cnName"], "dimCode": bd["code"], "ok": True, "rows": len(data["data"]["cellList"]), "sample": sample})
                    else:
                        biz_results.append({"dim": bd["cnName"], "dimCode": bd["code"], "ok": False, "error": (data.get("errorMessage") or data.get("message") or "未知错误")[:200]})
                except Exception as e:
                    biz_results.append({"dim": bd["cnName"], "dimCode": bd["code"], "ok": False, "error": str(e)[:200]})
            else:
                biz_results.append({"dim": bd["cnName"], "dimCode": bd["code"], "ok": False, "error": "缺少日期-日维度"})

        has_date_dim = len(date_results) > 0
        date_ok = has_date_dim and all(r["ok"] for r in date_results)
        biz_ok = all(r["ok"] for r in biz_results) if biz_results else True
        all_ok = date_ok and biz_ok
        if not has_date_dim:
            date_results.append({"dim": "(缺少日期维度)", "dimCode": "", "level": "", "ok": False,
                                 "error": "该指标在业务图谱中未关联任何日期维度，请检查 TTL"})

        result = {
            "code": m["code"], "cnName": m["cnName"],
            "dateDims": date_results,
            "bizDims": biz_results,
            "allOk": all_ok,
        }
        if all_ok:
            passed_all += 1
        else:
            failed_any += 1
        yield {"type": "measure", "index": idx, "total": len(measures), "result": result}

    yield {"type": "summary", "total": len(measures), "passed": passed_all, "failed": failed_any}


class BkgFixRequest(BaseModel):
    measCode: str
    dimCode: str
    error: str = ""


def _fmt_ttl_val(o) -> str:
    s = str(o)
    if s.startswith("http"):
        return f"inst:{s.split('#')[-1]}"
    if any(s.startswith(p) for p in ("MEAS_", "DIM_", "HIER_", "ma_", "da_", "ndm_")):
        return f'"{s}"'
    if s in ("true", "false") or s.replace(".", "").isdigit():
        return s
    return f'"{s}"'


@app.post("/api/business-kg/validate-fix")
async def business_kg_validate_fix(req: BkgFixRequest):
    """LLM 分析失败原因并尝试修复 TTL，然后重新测试"""
    from rdflib import Graph, Namespace
    import urllib.request as _ureq
    import re
    from kg_builder.business_kg.llm_builder import _load_env

    ttl_path = _current_bkg_path
    if not ttl_path or not ttl_path.exists():
        existing = sorted(BKG_DIR.glob("indicator-data.ttl"))
        if existing:
            ttl_path = existing[-1]
    if not ttl_path:
        return JSONResponse(status_code=404, content={"error": "未找到 TTL"})
    active_bkg_path = (BKG_DIR / "indicator-data.ttl").resolve()
    bound_source = (
        _semantic_source_path()
        if ttl_path.resolve() == active_bkg_path else None
    )

    IND = Namespace("http://indicator.insightmind.com/ontology#")
    g = Graph()
    g.parse(str(ttl_path), format="turtle")

    # ── 1. 提取该指标相关 TTL 片段 ───────────────────────────────────── #
    snippets = []
    fact_tables = set()
    for inst in g.subjects(None, None):
        c = g.value(inst, IND.code)
        if c and str(c) == req.measCode:
            for mapp in g.objects(inst, IND.hasMeasureApp):
                tbl = g.value(mapp, IND.appliesToTable)
                if tbl:
                    fact_tables.add(str(g.value(tbl, IND.tableName) or ""))
                lines = [f"inst:{str(mapp).split('#')[-1]} a ind:MeasureApp ;"]
                for p, o in g.predicate_objects(mapp):
                    lines.append(f"    ind:{str(p).split('#')[-1]} {_fmt_ttl_val(o)} ;")
                lines.append("    .")
                snippets.append("\n".join(lines))
                for ndm in g.objects(mapp, IND.hasNaturalDimMapping):
                    nl = [f"inst:{str(ndm).split('#')[-1]} a ind:NaturalDimMapping ;"]
                    for p, o in g.predicate_objects(ndm):
                        nl.append(f"    ind:{str(p).split('#')[-1]} {_fmt_ttl_val(o)} ;")
                    nl.append("    .")
                    snippets.append("\n".join(nl))

    # 查对应 DimApp 配置
    for da in g.subjects(None, None):
        da_code = g.value(da, IND.code)
        if da_code and str(da_code).startswith("da_"):
            tbl = g.value(da, IND.dimFactTable)
            if tbl and str(g.value(tbl, IND.tableName) or "") in fact_tables:
                for dim_inst in g.subjects(IND.hasDimApp, da):
                    if str(g.value(dim_inst, IND.code) or "") == req.dimCode:
                        dl = [f"inst:{str(da).split('#')[-1]} a ind:DimensionApp ;"]
                        for p, o in g.predicate_objects(da):
                            dl.append(f"    ind:{str(p).split('#')[-1]} {_fmt_ttl_val(o)} ;")
                        dl.append("    .")
                        snippets.append("\n".join(dl))
                        break

    ttl_snippet = "\n\n".join(snippets) if snippets else "(未找到配置)"

    # ── 2. 获取实际 MySQL 列名 ───────────────────────────────────────── #
    table_cols_cache = {}
    if fact_tables:
        try:
            import pymysql as _pm
            _c = _pm.connect(host="127.0.0.1", port=3306, user="root", password="root", database="tpcds", charset="utf8mb4")
            with _c.cursor() as cur:
                for ft in fact_tables:
                    cur.execute(f"SELECT COLUMN_NAME FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA='tpcds' AND TABLE_NAME='{ft}' ORDER BY ORDINAL_POSITION")
                    cols = [r[0].lower() for r in cur.fetchall()]
                    table_cols_cache[ft] = set(cols)
            _c.close()
        except Exception:
            pass

    # ── 3. 程序化修复：补齐该指标所有缺失维度 ────────────────────────── #
    content = ttl_path.read_text(encoding="utf-8")
    fixed_count = 0

    fk_prefixes = {"store_sales": "ss_", "catalog_sales": "cs_", "web_sales": "ws_",
                   "store_returns": "sr_", "catalog_returns": "cr_", "web_returns": "wr_"}

    # 收集现有 DimApp 模板（每个 dimCode + table → DimApp URI）
    dim_templates = {}  # dimCode → [(dimapp_uri, fact_table, fk, dimcol, dimtbl, mpk)]
    for di_s, _, di_o in g.triples((None, IND.code, None)):
        di_code = str(di_o)
        if not di_code.startswith("DIM_"):
            continue
        for da in g.objects(di_s, IND.hasDimApp):
            fc = str(g.value(da, IND.dimFactColumn) or "")
            ft = g.value(da, IND.dimFactTable)
            ft_name = str(g.value(ft, IND.tableName) or "") if ft else ""
            dc = str(g.value(da, IND.dimColumn) or "")
            dt = g.value(da, IND.dimTable)
            dt_name = str(g.value(dt, IND.tableName) or "") if dt else ""
            mpk = str(g.value(da, IND.masterPrimaryKey) or "")
            dim_templates.setdefault(di_code, []).append((da, ft_name, fc, dc, dt_name, mpk))

    for ft in fact_tables:
        if ft not in table_cols_cache:
            continue
        cols = table_cols_cache[ft]
        prefix = fk_prefixes.get(ft, "")
        is_return = "return" in ft

        # ── 1. 补日期维度 DimApp ──
        date_fk = None
        for c in cols:
            if c.endswith("_date_sk") and c.startswith(prefix):
                date_fk = c
                break
        # 退货表可能用 _returned_date_sk 或 _return_time_sk
        if not date_fk and is_return:
            for c in cols:
                if ("returned_date_sk" in c or "return_time_sk" in c) and c.startswith(prefix):
                    date_fk = c
                    break

        if date_fk:
            date_dim_codes = ["DIM_date_day", "DIM_date_week", "DIM_date_month",
                              "DIM_date_quarter", "DIM_date_year"]
            for dd_code in date_dim_codes:
                existing = any(d[1] == ft for d in dim_templates.get(dd_code, []))
                if existing:
                    continue
                lvl = dd_code.rsplit("_", 1)[-1]
                da_name = f"da_date_{lvl}_{ft}"
                block = (
                    f"\ninst:{da_name} a ind:DimensionApp ;\n"
                    f"    ind:available 1 ;\n"
                    f'    ind:dimColumn "d_date" ;\n'
                    f'    ind:dimFactColumn "{date_fk}" ;\n'
                    f'    ind:dimFactTable inst:tbl_tpcds__{ft} ;\n'
                    f'    ind:dimTable inst:tbl_tpcds__date_dim ;\n'
                    f'    ind:dimPrimaryKey "d_date_sk" ;\n'
                    f"    ind:isMasterApp false ;\n"
                    f"    ind:isRootJoin false ;\n"
                    f'    ind:masterPrimaryKey "{date_fk}" .\n'
                )
                if da_name not in content:
                    content += block
                    fixed_count += 1
                # 加到 Dimension 的 hasDimApp
                dd_uri = None
                for _s, _p, _o in g.triples((None, IND.code, None)):
                    if str(_o) == dd_code:
                        dd_uri = _s
                        break
                if dd_uri:
                    dd_str = str(dd_uri)
                    idx2 = content.find(dd_str)
                    if idx2 >= 0:
                        block_end2 = content.find('\n.', idx2)
                        dim_block2 = content[idx2:block_end2] if block_end2 > 0 else content[idx2:idx2+2000]
                        for ln in dim_block2.split('\n'):
                            if 'ind:hasDimApp ' in ln:
                                old_ln = ln
                                new_ln = ln.rstrip().replace(' ;', '') + f',\n        inst:{da_name} ;'
                                content = content.replace(old_ln, new_ln)
                                break

        # ── 2. 补业务维度 DimApp ──
        biz_dim_codes = [dc for dc in dim_templates if "date" not in dc.lower()]
        for bd_code in biz_dim_codes:
            existing = any(d[1] == ft for d in dim_templates.get(bd_code, []))
            if existing:
                continue
            # 用 store_sales 的模板
            store_tpls = [d for d in dim_templates.get(bd_code, []) if d[1] == "store_sales"]
            if not store_tpls:
                continue
            tpl = store_tpls[0]
            new_fk = tpl[2]
            for old_pf, new_pf in fk_prefixes.items():
                if tpl[2].startswith(old_pf):
                    new_fk = new_pf + tpl[2][len(old_pf):]
                    break
            if new_fk.lower() not in cols:
                suffix = tpl[2].split("_", 1)[-1] if "_" in tpl[2] else tpl[2]
                new_fk = prefix + suffix
            if new_fk.lower() not in cols:
                continue

            da_name = f"da_{bd_code.split('_',2)[-1] if '_' in bd_code else bd_code}_{ft}"
            block = (
                f"\ninst:{da_name} a ind:DimensionApp ;\n"
                f"    ind:available 1 ;\n"
                f'    ind:dimColumn "{tpl[3]}" ;\n'
                f'    ind:dimColumnExpr "{{d}}.{tpl[3]}" ;\n'
                f'    ind:dimFactColumn "{new_fk}" ;\n'
                f'    ind:dimFactTable inst:tbl_tpcds__{ft} ;\n'
                f'    ind:dimTable inst:tbl_tpcds__{tpl[4]} ;\n'
                f'    ind:dimPrimaryKey "{tpl[5]}" ;\n'
                f"    ind:isMasterApp false ;\n"
                f"    ind:isRootJoin false ;\n"
                f'    ind:masterPrimaryKey "{new_fk}" .\n'
            )
            if da_name not in content:
                content += block
                fixed_count += 1
                # 加到 Dimension 的 hasDimApp
                dd_uri = None
                for _s, _p, _o in g.triples((None, IND.code, None)):
                    if str(_o) == bd_code:
                        dd_uri = _s
                        break
                if dd_uri:
                    dd_str = str(dd_uri)
                    idx3 = content.find(dd_str)
                    if idx3 >= 0:
                        block_end3 = content.find('\n.', idx3)
                        dim_block3 = content[idx3:block_end3] if block_end3 > 0 else content[idx3:idx3+2000]
                        for ln2 in dim_block3.split('\n'):
                            if 'ind:hasDimApp ' in ln2:
                                old_ln2 = ln2
                                new_ln2 = ln2.rstrip().replace(' ;', '') + f',\n        inst:{da_name} ;'
                                content = content.replace(old_ln2, new_ln2)
                                break

        # ── 3. 如果没有 NDM，补一个 ──
        meas_short = req.measCode.replace("MEAS_", "")
        ndm_name = f"ndm_{meas_short.lower()}_date"
        has_ndm = any(ndm_name in str(s) for s in g.subjects(None, None))
        if not has_ndm and date_fk and ndm_name not in content:
            ndm_block = (
                f"\ninst:{ndm_name} a ind:NaturalDimMapping ;\n"
                f'    ind:naturalHierarchyCode "h_date" ;\n'
                f'    ind:physicalColumn "{date_fk}" .\n'
            )
            content += ndm_block
            fixed_count += 1

    if fixed_count > 0:
        ttl_path.write_text(content, encoding="utf-8")
        if ttl_path.resolve() == active_bkg_path:
            _write_semantic_source_manifest(bound_source)

    # ── 6. 重测 ──────────────────────────────────────────────────────── #
    time.sleep(4)
    payload = json.dumps({
        "configureList": [
            {"code": req.measCode, "order": {"sortType": 0}, "ratioList": [], "alias": "v"},
            {"code": req.dimCode, "order": {"sortType": 1}, "ratioList": [], "alias": "d"},
        ],
        "filterList": [], "pageSize": 1, "pageNum": 1,
    }).encode("utf-8")
    try:
        dr = _ureq.Request(_DATA_AGENT_URL, data=payload,
                           headers={"Content-Type": "application/json"}, method="POST")
        with _urlopen(dr, timeout=20) as resp:
            d = json.loads(resp.read().decode("utf-8"))
        if d.get("code") == 200 and d.get("data") and d["data"].get("cellList"):
            row = d["data"]["cellList"][0]
            sample = {}
            for cell in row:
                sample[cell.get("alias") or cell.get("code", "")] = cell.get("data", "-")
            return {"ok": True, "message": f"修复成功 (补充{fixed_count}项配置)", "sample": sample, "blocks": fixed_count}
        return {"ok": False, "message": "修复后仍失败: " +
                (d.get("errorMessage") or d.get("message", "未知"))[:200]}
    except Exception as e:
        return {"ok": False, "message": f"重测失败: {e}"}


# ── Analysis API ─────────────────────────────────────────────────────────── #

_analysis_log_queue: queue.Queue = queue.Queue()
_analysis_state: dict = {"status": "idle"}
_analysis_generation: int = 0  # 每次新分析 +1，用于取消旧线程写入
_analysis_ack_event: threading.Event = threading.Event()
_analysis_ack_info: dict = {"success": True, "error": ""}


class _DecimalEncoder(json.JSONEncoder):
    def default(self, obj):
        import decimal, datetime, numpy as np
        if isinstance(obj, decimal.Decimal):
            return float(obj)
        if isinstance(obj, (datetime.date, datetime.datetime)):
            return str(obj)
        if isinstance(obj, (np.integer,)):
            return int(obj)
        if isinstance(obj, (np.floating,)):
            return float(obj)
        if isinstance(obj, np.ndarray):
            return obj.tolist()
        return super().default(obj)


def _analysis_worker(params: dict, generation: int) -> None:
    global _analysis_state, _analysis_generation

    from kg_builder.utils.llm_config import llm_config_from_env
    llm_config = llm_config_from_env(BASE_DIR)

    def blog(msg: str) -> None:
        if _analysis_generation != generation:
            return  # 旧线程不再写入
        _analysis_log_queue.put(json.dumps({"log": msg}))
        # 同步写入文件日志，便于调试
        try:
            with open("/tmp/analysis_debug.log", "a", encoding="utf-8") as _f:
                _f.write(msg + "\n")
        except Exception:
            pass

    try:
        from kg_builder.analysis.analyzer import IndicatorAnalyzer
        analyzer = IndicatorAnalyzer(
            data_agent_url=_DATA_AGENT_URL,
            ttl_path=str(BKG_DIR / "indicator-data.ttl"),
            llm_config=llm_config,
            log_cb=blog,
            cancel_cb=lambda: _analysis_generation != generation,
        )
        for step_result in analyzer.analyze(params):
            if _analysis_generation != generation:
                return  # 新分析已启动，停止旧线程写入
            msg = json.dumps(step_result, cls=_DecimalEncoder)
            part_key = step_result.get("part")
            # 仅对主结果消息等待前端 ACK（数字 part + result，或最终 report）
            needs_ack = (
                (isinstance(part_key, int) and "result" in step_result) or
                ("report" in step_result and "part" not in step_result)
            )
            if needs_ack:
                _analysis_ack_event.clear()
            _analysis_log_queue.put(msg)
            if needs_ack:
                ack_received = _analysis_ack_event.wait(timeout=60)
                if _analysis_generation != generation:
                    return
                if not ack_received:
                    blog(f"⚠ Part {part_key or 'report'} 前端60s内未确认，继续执行")
                elif not _analysis_ack_info.get("success", True):
                    err = _analysis_ack_info.get("error", "未知错误")
                    blog(f"✗ Part {part_key or 'report'} 前端渲染失败: {err}")
    except Exception as e:
        if _analysis_generation == generation:
            blog(f"✗ 分析失败: {e}")
    finally:
        if _analysis_generation == generation:
            _analysis_state["status"] = "done"
            _analysis_log_queue.put("__DONE__")


@app.post("/api/analysis/ack")
async def analysis_ack(request: Request):
    """前端渲染完成后调用，通知后端继续执行下一个 Part。"""
    global _analysis_ack_info
    body = await request.json()
    _analysis_ack_info["success"] = body.get("success", True)
    _analysis_ack_info["error"]   = body.get("error", "")
    _analysis_ack_event.set()
    return {"status": "ok"}


@app.post("/api/analysis/start")
async def analysis_start(request: Request):
    global _analysis_generation, _analysis_state
    body = await request.json()
    import logging
    logging.getLogger("uvicorn").info(f"[analysis/start] body={json.dumps(body, ensure_ascii=False)}")
    # 递增 generation ID，旧线程检测到不匹配后自动停止写入
    _analysis_generation += 1
    gen = _analysis_generation
    # 清空队列残留
    while not _analysis_log_queue.empty():
        try:
            _analysis_log_queue.get_nowait()
        except Exception:
            break
    # 重置 ACK 状态
    _analysis_ack_event.clear()
    _analysis_ack_info["success"] = True
    _analysis_ack_info["error"] = ""
    _analysis_state["status"] = "running"
    threading.Thread(target=_analysis_worker, args=(body, gen), daemon=True).start()
    return {"status": "started"}


@app.get("/api/analysis/log")
async def analysis_log():
    """SSE stream for analysis progress and results."""
    async def gen():
        while True:
            try:
                msg = _analysis_log_queue.get_nowait()
                yield f"data: {msg}\n\n"
                if msg == "__DONE__":
                    break
            except queue.Empty:
                if _analysis_state.get("status") not in ("idle", "running"):
                    yield "data: __DONE__\n\n"
                    break
                yield ": ping\n\n"
                await asyncio.sleep(0.4)
    return StreamingResponse(gen(), media_type="text/event-stream")


# ── Insight API ──────────────────────────────────────────────────────────── #

_insight_tasks: dict[str, dict[str, Any]] = {}
_insight_tasks_lock = threading.Lock()
_INSIGHT_TASK_TTL_SECONDS = 3600
_insight_agent_runner = None
_insight_agent_runner_lock = threading.Lock()


def _get_insight_agent_runner():
    """Create the small agent runtime lazily after AD configuration is loaded."""
    global _insight_agent_runner
    with _insight_agent_runner_lock:
        if _insight_agent_runner is None:
            from kg_builder.agent_runtime import (
                InsightAgentRunner,
                InsightRuntimeDependencies,
            )
            from kg_builder.utils.llm_config import llm_config_from_env

            _insight_agent_runner = InsightAgentRunner(InsightRuntimeDependencies(
                data_agent_url=_DATA_AGENT_URL,
                ttl_path=str(BKG_DIR / "indicator-data.ttl"),
                llm_config_factory=lambda: llm_config_from_env(BASE_DIR),
                semantic_mapping_service_factory=_semantic_mapping_service,
            ))
        return _insight_agent_runner


def _insight_task(task_id: str) -> Optional[dict[str, Any]]:
    with _insight_tasks_lock:
        return _insight_tasks.get(task_id)


def _sync_insight_conversation_context(task: dict[str, Any], legacy_event: dict[str, Any]) -> None:
    """Keep the legacy Smart NLQ conversation state while it uses the new runner."""
    if legacy_event.get("step") != "kg_match":
        return
    conversation_id = str(task.get("conversation_id") or "")
    if not conversation_id:
        return
    match = legacy_event.get("result") or {}
    measure_code = str(match.get("meas_code") or "").strip()
    table_name = str(match.get("table_name") or "").strip()
    all_measures = [measure_code] if measure_code else []
    for secondary in match.get("secondary") or []:
        code = str(secondary.get("meas_code") or "").strip()
        if code and code not in all_measures:
            all_measures.append(code)
    with _nlq_context_lock:
        resolved = dict(_nlq_context_store.get(conversation_id) or {})
        if measure_code:
            resolved["activeMeasureCode"] = measure_code
            resolved["measureCodes"] = all_measures
        if table_name:
            resolved["factTables"] = [table_name]
        resolved["lastQueryMode"] = "insight"
        _nlq_context_store[conversation_id] = resolved


def _cleanup_insight_tasks() -> None:
    cutoff = time.time() - _INSIGHT_TASK_TTL_SECONDS
    with _insight_tasks_lock:
        expired = [
            task_id for task_id, task in _insight_tasks.items()
            if task.get("status") not in ("running", "cancelling") and task.get("updated_at", 0) < cutoff
        ]
        for task_id in expired:
            _insight_tasks.pop(task_id, None)


def _insight_worker(
    question: str,
    task_id: str,
    conversation_id: str = "",
    context: Optional[dict[str, Any]] = None,
    analysis_mode: str = "",
) -> None:
    task = _insight_task(task_id)
    if not task:
        return

    from kg_builder.utils.llm_config import llm_config_from_env
    llm_config = llm_config_from_env(BASE_DIR)

    def ilog(msg: str) -> None:
        if task["cancel_event"].is_set():
            return
        task["queue"].put(json.dumps({"log": msg}))
        task["updated_at"] = time.time()
        try:
            with open("/tmp/insight_debug.log", "a", encoding="utf-8") as _f:
                _f.write(msg + "\n")
        except Exception:
            pass

    try:
        mode = (analysis_mode or (context or {}).get("analysisMode") or "").strip()
        if mode == "document_trace":
            from kg_builder.analysis.document_trace_insight import DocumentTraceInsightAnalyzer

            analyzer = DocumentTraceInsightAnalyzer(
                llm_config=llm_config,
                log_cb=ilog,
                cancel_cb=task["cancel_event"].is_set,
                context=context,
            )
        else:
            from kg_builder.analysis.insight_analyzer import InsightAnalyzer

            analyzer = InsightAnalyzer(
                data_agent_url=_DATA_AGENT_URL,
                ttl_path=str(BKG_DIR / "indicator-data.ttl"),
                llm_config=llm_config,
                log_cb=ilog,
                cancel_cb=task["cancel_event"].is_set,
                context=context,
                semantic_mapping_service=_semantic_mapping_service(),
            )
        for step_result in analyzer.analyze(question):
            if task["cancel_event"].is_set():
                return
            if conversation_id and step_result.get("step") == "kg_match":
                match = step_result.get("result") or {}
                measure_code = str(match.get("meas_code") or "").strip()
                table_name = str(match.get("table_name") or "").strip()
                # 收集所有指标代码（primary + secondary）用于上下文传递
                all_meas = [measure_code] if measure_code else []
                for sec in match.get("secondary") or []:
                    sc = str(sec.get("meas_code") or "").strip()
                    if sc and sc not in all_meas:
                        all_meas.append(sc)
                with _nlq_context_lock:
                    resolved = dict(_nlq_context_store.get(conversation_id) or context or {})
                    if measure_code:
                        resolved["activeMeasureCode"] = measure_code
                        resolved["measureCodes"] = all_meas
                    if table_name:
                        resolved["factTables"] = [table_name]
                    resolved["lastQuestion"] = question
                    resolved["lastQueryMode"] = "insight"
                    _nlq_context_store[conversation_id] = resolved
            msg = json.dumps(step_result, cls=_DecimalEncoder)

            # 对主结果消息（数字 part + result，或 report）等待前端 ACK
            part_key = step_result.get("part")
            needs_ack = (
                (isinstance(part_key, int) and "result" in step_result) or
                ("report" in step_result and "part" not in step_result)
            )
            if needs_ack:
                task["ack_event"].clear()
            task["queue"].put(msg)
            task["updated_at"] = time.time()
            if needs_ack:
                ack_received = task["ack_event"].wait(timeout=8)
                if task["cancel_event"].is_set():
                    return
                if not ack_received:
                    ilog(f"⚠ Part {part_key or 'report'} 前端8s内未确认，继续执行")
                elif not task["ack_info"].get("success", True):
                    err = task["ack_info"].get("error", "未知错误")
                    ilog(f"✗ Part {part_key or 'report'} 前端渲染失败: {err}")
    except Exception as e:
        if not task["cancel_event"].is_set():
            ilog(f"✗ Insight 分析失败: {e}")
            import traceback
            ilog(traceback.format_exc())
    finally:
        task["status"] = "cancelled" if task["cancel_event"].is_set() else "done"
        task["updated_at"] = time.time()
        task["queue"].put("__CANCELLED__" if task["status"] == "cancelled" else "__DONE__")


@app.post("/api/insight/start")
async def insight_start(request: Request):
    body = await request.json()
    question = (body.get("question") or "").strip()
    if not question:
        return JSONResponse({"error": "question 不能为空"}, status_code=400)

    conversation_id = (body.get("conversationId") or "").strip() or str(uuid.uuid4())
    with _nlq_context_lock:
        stored_context = dict(_nlq_context_store.get(conversation_id) or {})
    request_context = dict(stored_context)
    if isinstance(body.get("context"), dict):
        request_context.update(body["context"])
    analysis_mode = str(body.get("analysisMode") or request_context.get("analysisMode") or "").strip()

    _cleanup_insight_tasks()
    task_id = uuid.uuid4().hex
    from kg_builder.agent_runtime import AgentRequest

    agent_run = _get_insight_agent_runner().start(AgentRequest(
        question=question,
        context=request_context,
        analysis_mode=analysis_mode,
        conversation_id=conversation_id,
        user_id=str(body.get("userId") or request.headers.get("x-user-id") or ""),
        tenant_id=str(body.get("tenantId") or request.headers.get("x-tenant-id") or ""),
        permission_scope_hash=str(body.get("permissionScopeHash") or ""),
        semantic_token=str(body.get("semanticToken") or ""),
        graph_version=str(body.get("graphVersion") or ""),
    ))
    task = {
        "id": task_id,
        "agent_run_id": agent_run.context.run_id,
        "trace_id": agent_run.context.trace_id,
        "status": "running",
        "ack_event": threading.Event(),
        "ack_info": {"success": True, "error": ""},
        "created_at": time.time(),
        "updated_at": time.time(),
        "conversation_id": conversation_id,
    }
    with _insight_tasks_lock:
        _insight_tasks[task_id] = task
    return {
        "status": "started",
        "taskId": task_id,
        "runId": agent_run.context.run_id,
        "traceId": agent_run.context.trace_id,
        "conversationId": conversation_id,
        "analysisMode": analysis_mode or "metric_fluctuation",
    }


@app.get("/api/insight/{task_id}/log")
async def insight_log(task_id: str):
    """Legacy SSE stream backed by the Agent Runtime event store."""
    task = _insight_task(task_id)
    if not task:
        return JSONResponse({"error": "Insight 任务不存在或已过期"}, status_code=404)
    run = _get_insight_agent_runner().store.get(str(task.get("agent_run_id") or ""))
    if run is None:
        return JSONResponse({"error": "Insight Agent 任务不存在或已过期"}, status_code=404)

    async def gen():
        from kg_builder.agent_runtime import RunStatus, legacy_insight_sse_payload

        last_sequence = 0
        while True:
            events = run.events_after(last_sequence)
            for event in events:
                last_sequence = event.sequence
                legacy = event.payload.get("legacy")
                if isinstance(legacy, dict):
                    _sync_insight_conversation_context(task, legacy)
                msg = legacy_insight_sse_payload(event)
                if msg is None:
                    continue
                yield f"data: {msg}\n\n"
                if msg in ("__DONE__", "__CANCELLED__"):
                    task["status"] = "cancelled" if msg == "__CANCELLED__" else "done"
                    task["updated_at"] = time.time()
                    return
            if run.status in {RunStatus.SUCCEEDED, RunStatus.FAILED, RunStatus.CANCELLED}:
                task["status"] = "cancelled" if run.status is RunStatus.CANCELLED else "done"
                task["updated_at"] = time.time()
                terminal = "__CANCELLED__" if run.status is RunStatus.CANCELLED else "__DONE__"
                yield f"data: {terminal}\n\n"
                return
            yield ": ping\n\n"
            await asyncio.sleep(0.25)
    return StreamingResponse(gen(), media_type="text/event-stream")


@app.post("/api/insight/{task_id}/ack")
async def insight_ack(task_id: str, request: Request):
    """Record a legacy client rendering acknowledgement as runtime telemetry."""
    task = _insight_task(task_id)
    if not task:
        return JSONResponse({"error": "Insight 任务不存在或已过期"}, status_code=404)
    body = await request.json()
    task["ack_info"]["success"] = body.get("success", True)
    task["ack_info"]["error"] = body.get("error", "")
    task["ack_event"].set()
    run = _get_insight_agent_runner().store.get(str(task.get("agent_run_id") or ""))
    if run is not None:
        from kg_builder.agent_runtime import StreamEventType
        run.publish(StreamEventType.TRACE, {
            "clientAck": {
                "success": bool(task["ack_info"]["success"]),
                "error": str(task["ack_info"]["error"] or ""),
            },
        })
    return {"status": "ok"}


@app.post("/api/insight/{task_id}/cancel")
async def insight_cancel(task_id: str):
    task = _insight_task(task_id)
    if not task:
        return JSONResponse({"error": "Insight 任务不存在或已过期"}, status_code=404)
    if task.get("status") in ("done", "cancelled"):
        return {"status": task["status"], "taskId": task_id}
    run = _get_insight_agent_runner().cancel(str(task.get("agent_run_id") or ""))
    if run is None:
        return JSONResponse({"error": "Insight Agent 任务不存在或已过期"}, status_code=404)
    task["ack_event"].set()
    task["status"] = "cancelling"
    task["updated_at"] = time.time()
    return {"status": "cancelling", "taskId": task_id}


@app.post("/api/agent/runs")
async def agent_run_start(request: Request):
    """Start a governed Insight Agent run using the V1 event protocol."""
    body = await request.json()
    question = str(body.get("question") or "").strip()
    if not question:
        return JSONResponse({"error": "question 不能为空"}, status_code=400)
    from kg_builder.agent_runtime import AgentRequest

    context = dict(body.get("context") or {}) if isinstance(body.get("context"), dict) else {}
    run = _get_insight_agent_runner().start(AgentRequest(
        question=question,
        context=context,
        analysis_mode=str(body.get("analysisMode") or context.get("analysisMode") or ""),
        conversation_id=str(body.get("conversationId") or "") or str(uuid.uuid4()),
        user_id=str(body.get("userId") or request.headers.get("x-user-id") or ""),
        tenant_id=str(body.get("tenantId") or request.headers.get("x-tenant-id") or ""),
        permission_scope_hash=str(body.get("permissionScopeHash") or ""),
        semantic_token=str(body.get("semanticToken") or ""),
        graph_version=str(body.get("graphVersion") or ""),
    ))
    return {
        "status": "started",
        "runId": run.context.run_id,
        "traceId": run.context.trace_id,
        "conversationId": run.context.conversation_id,
        "eventsUrl": f"/api/agent/runs/{run.context.run_id}/events",
    }


@app.post("/api/agent/route")
async def agent_route(request: Request):
    """Return the shared deterministic Smart Insight route without executing it."""
    body = await request.json()
    question = str(body.get("question") or "").strip()
    if not question:
        return JSONResponse({"error": "question 不能为空"}, status_code=400)
    from kg_builder.agent_runtime import SmartIntentRouter

    context = dict(body.get("context") or {}) if isinstance(body.get("context"), dict) else {}
    decision = SmartIntentRouter().decide(question, context)
    return {"ok": True, "decision": decision.to_dict()}


@app.get("/api/agent/runs/{run_id}/events")
async def agent_run_events(run_id: str, request: Request):
    """SSE stream for the versioned Agent Runtime event contract."""
    run = _get_insight_agent_runner().store.get(run_id)
    if run is None:
        return JSONResponse({"error": "Agent 任务不存在或已过期"}, status_code=404)
    try:
        last_sequence = max(0, int(request.headers.get("last-event-id") or 0))
    except ValueError:
        last_sequence = 0

    async def gen():
        from kg_builder.agent_runtime import RunStatus

        sequence = last_sequence
        while True:
            for event in run.events_after(sequence):
                sequence = event.sequence
                yield f"id: {event.sequence}\nevent: {event.event_type.value}\ndata: {json.dumps(event.to_dict(), ensure_ascii=False, cls=_DecimalEncoder)}\n\n"
            if run.status in {RunStatus.SUCCEEDED, RunStatus.FAILED, RunStatus.CANCELLED}:
                return
            yield ": ping\n\n"
            await asyncio.sleep(0.25)
    return StreamingResponse(gen(), media_type="text/event-stream")


@app.post("/api/agent/runs/{run_id}/cancel")
async def agent_run_cancel(run_id: str):
    run = _get_insight_agent_runner().cancel(run_id)
    if run is None:
        return JSONResponse({"error": "Agent 任务不存在或已过期"}, status_code=404)
    return {"status": "cancelling", "runId": run_id}


@app.post("/api/insight/explain-cell")
async def insight_explain_cell(request: Request):
    """Explain a selected pivot metric cell with document evidence and drill recommendations."""
    body = await request.json()
    try:
        from kg_builder.analysis.cell_insight import CellInsightService

        result = await asyncio.to_thread(CellInsightService(_pivot_catalog()).explain, body)
        return {"success": True, "data": result}
    except FileNotFoundError as exc:
        return JSONResponse({"success": False, "error": str(exc)}, status_code=404)
    except Exception as exc:
        return JSONResponse({"success": False, "error": str(exc)}, status_code=500)


def _insight_action_text(value: Any, default: str = "") -> str:
    if value is None:
        return default
    text = str(value).strip()
    return text or default


def _insight_action_evidence(context: dict[str, Any]) -> list[dict[str, Any]]:
    cell = context.get("cellInsight") if isinstance(context.get("cellInsight"), dict) else {}
    measure = cell.get("measure") if isinstance(cell.get("measure"), dict) else {}
    cell_context = cell.get("cellContext") if isinstance(cell.get("cellContext"), dict) else {}
    anomaly = cell.get("anomaly") if isinstance(cell.get("anomaly"), dict) else {}
    diagnosis = cell.get("diagnosis") if isinstance(cell.get("diagnosis"), dict) else {}
    docs = cell.get("documents") if isinstance(cell.get("documents"), list) else []
    contributions = cell.get("contributions") if isinstance(cell.get("contributions"), list) else []
    selected = context.get("selectedDocument") if isinstance(context.get("selectedDocument"), dict) else (docs[0] if docs else {})
    evidence: list[dict[str, Any]] = []
    evidence.append({
        "id": "G1",
        "type": "metric",
        "title": "图谱指标",
        "detail": f"{measure.get('name') or measure.get('code') or '当前指标'}，当前值 {cell.get('cellValue', '-')}",
        "raw": measure,
    })
    evidence.append({
        "id": "G2",
        "type": "slice",
        "title": "图谱切片",
        "detail": cell_context.get("label") or "当前单元格切片",
        "raw": cell_context,
    })
    if anomaly:
        evidence.append({
            "id": "G3",
            "type": "anomaly",
            "title": "异常画像",
            "detail": "；".join(filter(None, [
                _insight_action_text(anomaly.get("title")),
                _insight_action_text(anomaly.get("reason")),
                f"来源={diagnosis.get('source') or anomaly.get('source') or '-'}",
                f"置信度={diagnosis.get('confidence') or anomaly.get('confidence') or '-'}",
            ])),
            "raw": {"anomaly": anomaly, "diagnosis": diagnosis},
        })
    if selected:
        evidence.append({
            "id": "G4",
            "type": "document",
            "title": "代表性异常单据",
            "detail": (
                f"{selected.get('documentNo') or '-'}，"
                f"{selected.get('fieldName') or selected.get('field') or '异常字段'}={selected.get('value', '-')}"
            ),
            "raw": selected,
        })
    if docs:
        evidence.append({
            "id": "G5",
            "type": "document_count",
            "title": "单据命中规模",
            "detail": f"共命中 {len(docs)} 条异常单据，规则：{'、'.join(sorted({str(x.get('ruleName') or '单据追踪') for x in docs})[:4])}",
            "raw": docs[:12],
        })
    if contributions:
        evidence.append({
            "id": "G6",
            "type": "graph_drill",
            "title": "图谱推荐下钻维度",
            "detail": "；".join(
                f"{x.get('dimensionName') or x.get('dimensionCode')}({x.get('score', '-')})"
                for x in contributions[:5]
                if isinstance(x, dict)
            ),
            "raw": contributions[:8],
        })
    return evidence


def _insight_action_draft(body: dict[str, Any]) -> dict[str, Any]:
    context = body.get("context") if isinstance(body.get("context"), dict) else {}
    question = _insight_action_text(body.get("question"), "单据异常处理")
    cell = context.get("cellInsight") if isinstance(context.get("cellInsight"), dict) else {}
    measure = cell.get("measure") if isinstance(cell.get("measure"), dict) else {}
    cell_context = cell.get("cellContext") if isinstance(cell.get("cellContext"), dict) else {}
    anomaly = cell.get("anomaly") if isinstance(cell.get("anomaly"), dict) else {}
    docs = cell.get("documents") if isinstance(cell.get("documents"), list) else []
    contributions = cell.get("contributions") if isinstance(cell.get("contributions"), list) else []
    selected = context.get("selectedDocument") if isinstance(context.get("selectedDocument"), dict) else (docs[0] if docs else {})
    measure_name = measure.get("name") or measure.get("code") or "当前指标"
    slice_label = cell_context.get("label") or "当前切片"
    field_name = selected.get("fieldName") or selected.get("field") or "异常字段"
    doc_no = selected.get("documentNo") or "代表性单据"
    rule_name = selected.get("ruleName") or (docs[0].get("ruleName") if docs else "") or "单据追踪规则"
    top_dim = next((x for x in contributions if isinstance(x, dict) and x.get("dimensionName")), {})
    top_dim_name = top_dim.get("dimensionName") or top_dim.get("dimensionCode") or "推荐维度"
    evidence = _insight_action_evidence(context)
    suggestions = [
        {
            "id": "S1",
            "title": "核对异常单据和规则命中条件",
            "priority": "高",
            "owner": "业务运营",
            "dueDays": 1,
            "action": f"复核规则「{rule_name}」命中的 {len(docs)} 条单据，优先确认 {doc_no} 的 {field_name}={selected.get('value', '-')} 是否符合真实业务口径。",
            "successMetric": "确认异常单据是否真实、规则是否过宽或过窄",
            "evidenceIds": ["G4", "G5"],
        },
        {
            "id": "S2",
            "title": "按图谱维度定位影响范围",
            "priority": "中",
            "owner": "数据分析",
            "dueDays": 2,
            "action": f"围绕「{measure_name}」在「{slice_label}」下继续按「{top_dim_name}」下钻，确认异常是否集中在特定商品、渠道、仓库、促销或时间段。",
            "successMetric": "找出贡献最大的异常维度成员和可解释范围",
            "evidenceIds": ["G1", "G2", "G6"],
        },
        {
            "id": "S3",
            "title": "形成规则或流程改进方案",
            "priority": "中",
            "owner": "规则负责人",
            "dueDays": 3,
            "action": f"根据异常画像「{anomaly.get('title') or '当前异常'}」调整监控条件、阈值或业务处理流程，并记录调整前后的规则版本。",
            "successMetric": "新规则能减少误报，同时不漏掉真实异常",
            "evidenceIds": ["G3", "G5"],
        },
        {
            "id": "S4",
            "title": "建立追盯和复盘节奏",
            "priority": "中",
            "owner": "运营负责人",
            "dueDays": 7,
            "action": f"以「{measure_name}」和当前图谱切片为追踪对象，每日/每周回看异常单据数、异常金额或命中率，观察改进后是否下降。",
            "successMetric": "连续观察周期内异常命中率下降，且关键指标未出现新的异常偏移",
            "evidenceIds": ["G1", "G2", "G5"],
        },
    ]
    return {
        "id": f"draft_{uuid.uuid4().hex[:8]}",
        "question": question,
        "status": "draft",
        "createdAt": time.strftime("%Y-%m-%d %H:%M:%S"),
        "subject": f"{measure_name} · {slice_label}",
        "stateAnalysis": {
            "title": "现状分析",
            "summary": (
                f"基于图谱指标「{measure_name}」、切片「{slice_label}」和单据追踪证据，"
                f"当前发现 {len(docs)} 条异常单据。{anomaly.get('reason') or '建议先确认异常是否集中于特定规则、维度或业务流程。'}"
            ),
            "evidenceIds": [item["id"] for item in evidence[:6]],
        },
        "evidence": evidence,
        "suggestions": suggestions,
        "tracking": {
            "baseline": f"{measure_name} 当前值 {cell.get('cellValue', '-')}",
            "frequency": "每日跟进，连续 7 天复盘",
            "successCriteria": "异常命中数量下降、规则误报减少、相关指标未出现新的异常偏移",
        },
    }


@app.post("/api/insight/action-plan/draft")
async def insight_action_plan_draft(request: Request):
    body = await request.json()
    try:
        return {"ok": True, "plan": _insight_action_draft(body)}
    except Exception as exc:
        return JSONResponse({"ok": False, "error": str(exc)}, status_code=500)


@app.post("/api/insight/action-plan/submit")
async def insight_action_plan_submit(request: Request):
    body = await request.json()
    try:
        plan = body.get("plan") if isinstance(body.get("plan"), dict) else body
        now = time.strftime("%Y-%m-%d %H:%M:%S")
        item_id = _artifact_safe_id(plan.get("id") or f"insight_action_{uuid.uuid4().hex[:8]}")
        suggestions = plan.get("suggestions") if isinstance(plan.get("suggestions"), list) else []
        tasks = []
        for idx, item in enumerate(suggestions, start=1):
            if not isinstance(item, dict) or item.get("disabled"):
                continue
            tasks.append({
                "id": f"T{idx}",
                "title": item.get("title") or f"追盯任务 {idx}",
                "owner": item.get("owner") or "待分配",
                "priority": item.get("priority") or "中",
                "dueDays": item.get("dueDays") or 3,
                "status": "待处理",
                "action": item.get("action") or "",
                "successMetric": item.get("successMetric") or "",
                "evidenceIds": item.get("evidenceIds") or [],
            })
        data = {
            **plan,
            "id": item_id,
            "kind": "insight_action_plan",
            "status": "tracking",
            "submittedAt": now,
            "updatedAt": now,
            "tasks": tasks,
            "feedback": plan.get("feedback") if isinstance(plan.get("feedback"), list) else [],
        }
        path = _artifact_path(INSIGHT_ACTION_DIR, item_id)
        path.write_text(json.dumps(data, ensure_ascii=False, indent=2, default=str), encoding="utf-8")
        return {"ok": True, "plan": data}
    except Exception as exc:
        return JSONResponse({"ok": False, "error": str(exc)}, status_code=500)


@app.post("/api/insight/action-plan/{item_id}/feedback")
async def insight_action_plan_feedback(item_id: str, request: Request):
    body = await request.json()
    try:
        path = _artifact_path(INSIGHT_ACTION_DIR, item_id)
        data = _read_json_artifact(path)
        feedback = data.get("feedback") if isinstance(data.get("feedback"), list) else []
        feedback.append({
            "id": f"F{len(feedback) + 1}",
            "createdAt": time.strftime("%Y-%m-%d %H:%M:%S"),
            "status": body.get("status") or "跟进中",
            "metricValue": body.get("metricValue") or "",
            "comment": body.get("comment") or "",
            "nextAction": body.get("nextAction") or "",
        })
        data["feedback"] = feedback
        data["status"] = body.get("planStatus") or data.get("status") or "tracking"
        data["updatedAt"] = time.strftime("%Y-%m-%d %H:%M:%S")
        path.write_text(json.dumps(data, ensure_ascii=False, indent=2, default=str), encoding="utf-8")
        return {"ok": True, "plan": data}
    except FileNotFoundError:
        return JSONResponse({"ok": False, "error": "追盯方案不存在"}, status_code=404)
    except Exception as exc:
        return JSONResponse({"ok": False, "error": str(exc)}, status_code=500)


@app.get("/api/insight/action-plan/list")
async def insight_action_plan_list():
    return {"items": _list_json_artifacts(INSIGHT_ACTION_DIR)}


def _integration_url_candidates(system_url: str) -> list[str]:
    from urllib.parse import urlparse, urlunparse

    parsed = urlparse(system_url.strip())
    if parsed.scheme not in ("http", "https") or not parsed.netloc:
        raise ValueError("处理系统地址必须是 http 或 https URL")
    base = urlunparse((parsed.scheme, parsed.netloc, "", "", "", ""))
    candidates = [system_url.strip()]
    for suffix in ("/openapi.json", "/swagger.json", "/v3/api-docs", "/api-docs"):
        candidate = base + suffix
        if candidate not in candidates:
            candidates.append(candidate)
    return candidates[:5]


def _fetch_integration_schema(system_url: str) -> dict[str, Any]:
    import urllib.request

    errors: list[str] = []
    for url in _integration_url_candidates(system_url):
        try:
            req = urllib.request.Request(
                url,
                headers={"Accept": "application/json", "User-Agent": "InsightMind-Integration-Discovery/1.0"},
                method="GET",
            )
            with _urlopen(req, timeout=6) as resp:
                content_type = resp.headers.get("content-type", "")
                raw = resp.read(300_000)
            text = raw.decode("utf-8", errors="replace")
            data = json.loads(text)
            return {"ok": True, "url": url, "contentType": content_type, "schema": data}
        except Exception as exc:
            errors.append(f"{url}: {exc}")
    return {"ok": False, "url": system_url, "errors": errors, "schema": {}}


def _extract_integration_endpoints(schema: dict[str, Any]) -> list[dict[str, Any]]:
    paths = schema.get("paths") if isinstance(schema, dict) else {}
    if not isinstance(paths, dict):
        return []
    endpoints: list[dict[str, Any]] = []
    for path, methods in paths.items():
        if not isinstance(methods, dict):
            continue
        for method, op in methods.items():
            if str(method).lower() not in {"get", "post", "put", "patch", "delete"}:
                continue
            op = op if isinstance(op, dict) else {}
            endpoints.append({
                "method": str(method).upper(),
                "path": str(path),
                "operationId": op.get("operationId") or "",
                "summary": op.get("summary") or op.get("description") or "",
                "tags": op.get("tags") or [],
                "hasRequestBody": bool(op.get("requestBody")),
            })
    return endpoints[:80]


def _choose_task_endpoint(endpoints: list[dict[str, Any]]) -> dict[str, Any]:
    keywords = ("task", "ticket", "issue", "work", "todo", "follow", "order", "工单", "任务", "问题", "跟进", "追踪")
    write_methods = {"POST": 5, "PUT": 3, "PATCH": 3}
    scored = []
    for ep in endpoints:
        hay = " ".join([
            str(ep.get("path") or ""),
            str(ep.get("operationId") or ""),
            str(ep.get("summary") or ""),
            " ".join(str(x) for x in ep.get("tags") or []),
        ]).lower()
        keyword_hits = sum(1 for key in keywords if key.lower() in hay)
        if keyword_hits <= 0:
            continue
        score = write_methods.get(str(ep.get("method") or "").upper(), 0) + keyword_hits * 2
        if score:
            scored.append((score, ep))
    scored.sort(key=lambda x: x[0], reverse=True)
    return scored[0][1] if scored else {}


def _local_integration_plan(plan: dict[str, Any], system_url: str, discovery: dict[str, Any]) -> dict[str, Any]:
    endpoints = _extract_integration_endpoints(discovery.get("schema") or {})
    selected = _choose_task_endpoint(endpoints)
    tasks = plan.get("tasks") if isinstance(plan.get("tasks"), list) else []
    evidence = plan.get("evidence") if isinstance(plan.get("evidence"), list) else []
    return {
        "mode": "dry_run",
        "systemUrl": system_url,
        "discovery": {
            "ok": bool(discovery.get("ok")),
            "schemaUrl": discovery.get("url") or system_url,
            "endpointCount": len(endpoints),
            "errors": discovery.get("errors") or [],
        },
        "recommendedEndpoint": selected,
        "payloadMapping": {
            "title": "task.title",
            "description": "task.action + task.successMetric + evidence",
            "owner": "task.owner",
            "priority": "task.priority",
            "dueDate": "submittedAt + dueDays",
            "externalKey": "plan.id + task.id",
            "evidence": "G1-G6 图谱和单据证据",
        },
        "samplePayload": {
            "title": tasks[0].get("title") if tasks else plan.get("subject") or "Insight 追盯任务",
            "description": tasks[0].get("action") if tasks else plan.get("stateAnalysis", {}).get("summary", ""),
            "owner": tasks[0].get("owner") if tasks else "待分配",
            "priority": tasks[0].get("priority") if tasks else "中",
            "source": "InsightMind",
            "sourcePlanId": plan.get("id") or "",
            "evidence": [
                {"id": item.get("id"), "title": item.get("title"), "detail": item.get("detail")}
                for item in evidence[:6] if isinstance(item, dict)
            ],
        },
        "requiredUserInputs": [
            "认证方式或 Token",
            "目标项目/空间/队列 ID",
            "字段映射确认",
            "是否允许系统真正调用写接口",
        ],
        "safety": [
            "当前只生成连接方案，不自动创建外部任务。",
            "大模型只能基于接口描述生成映射建议，不能直接提升为可执行写操作。",
            "正式推送前需要用户确认接口、鉴权、字段映射和样例载荷。",
        ],
    }


def _llm_integration_plan(plan: dict[str, Any], system_url: str, discovery: dict[str, Any]) -> dict[str, Any]:
    import os
    import urllib.request

    from kg_builder.utils.llm_config import chat_completions_url, llm_config_from_env, llm_request_headers, validate_llm_config

    local = _local_integration_plan(plan, system_url, discovery)
    cfg = llm_config_from_env(BASE_DIR, model_override=os.environ.get("BUSINESS_KG_MODEL", "").strip())
    validate_llm_config(cfg, purpose="处理系统连接方案生成")
    endpoints = _extract_integration_endpoints(discovery.get("schema") or {})
    prompt = {
        "instruction": (
            "你是企业系统集成架构师。请基于 Insight 追盯方案、OpenAPI 端点和样例证据，"
            "生成处理系统连接方案。只允许生成方案和字段映射，不能声称已经调用接口，不能要求自动写入。"
            "必须输出 JSON，字段包括 mode,dryRunReason,recommendedEndpoint,payloadMapping,samplePayload,requiredUserInputs,safety。"
        ),
        "systemUrl": system_url,
        "plan": {
            "id": plan.get("id"),
            "subject": plan.get("subject"),
            "tasks": (plan.get("tasks") or [])[:6],
            "evidence": (plan.get("evidence") or [])[:8],
        },
        "endpoints": endpoints[:40],
        "fallbackPlan": local,
    }
    base_url = cfg.get("base_url", "").rstrip("/")
    api_key = cfg.get("api_key", "")
    model = cfg.get("model", "GPT5.5")
    body = json.dumps({
        "model": model,
        "max_tokens": 1600,
        "messages": [{"role": "user", "content": json.dumps(prompt, ensure_ascii=False, default=str)}],
    }).encode("utf-8")
    req = urllib.request.Request(
        chat_completions_url(base_url),
        data=body,
        headers=llm_request_headers(cfg),
        method="POST",
    )
    with _urlopen(req, timeout=50) as resp:
        data = json.loads(resp.read().decode("utf-8"))
    text = data.get("choices", [{}])[0].get("message", {}).get("content", "")
    try:
        parsed = json.loads(text)
        return {**local, **parsed, "mode": "dry_run", "generatedBy": "llm"}
    except Exception:
        return {**local, "generatedBy": "local", "llmText": text[:4000]}


@app.post("/api/insight/action-plan/integration/draft")
async def insight_action_plan_integration_draft(request: Request):
    body = await request.json()
    try:
        system_url = _insight_action_text(body.get("systemUrl"))
        if not system_url:
            return JSONResponse({"ok": False, "error": "请输入处理系统地址"}, status_code=400)
        plan = body.get("plan") if isinstance(body.get("plan"), dict) else {}
        if not plan and body.get("planId"):
            plan = _read_json_artifact(_artifact_path(INSIGHT_ACTION_DIR, str(body.get("planId"))))
        discovery = await asyncio.to_thread(_fetch_integration_schema, system_url)
        try:
            integration = await asyncio.to_thread(_llm_integration_plan, plan, system_url, discovery)
        except Exception as exc:
            integration = _local_integration_plan(plan, system_url, discovery)
            integration["generatedBy"] = "local"
            integration["warning"] = str(exc)
        return {"ok": True, "integration": integration}
    except ValueError as exc:
        return JSONResponse({"ok": False, "error": str(exc)}, status_code=400)
    except FileNotFoundError:
        return JSONResponse({"ok": False, "error": "追盯方案不存在"}, status_code=404)
    except Exception as exc:
        return JSONResponse({"ok": False, "error": str(exc)}, status_code=500)


# ── NLQ API ──────────────────────────────────────────────────────────────── #

_nlq_context_store: dict[str, dict[str, Any]] = {}
_nlq_context_lock = threading.Lock()
_nlq_result_history: dict[str, deque[dict[str, Any]]] = {}
_nlq_result_history_lock = threading.Lock()
_nlq_trace_store: deque[dict[str, Any]] = deque(maxlen=200)
_nlq_trace_lock = threading.Lock()


def _safe_float(value: Any) -> Optional[float]:
    if value is None or isinstance(value, bool):
        return None
    if isinstance(value, (int, float)):
        return float(value) if math.isfinite(float(value)) else None
    text = str(value).strip()
    if not text or text in {"-", "—", "null", "None", "NaN", "nan"}:
        return None
    text = (
        text.replace(",", "")
        .replace("￥", "")
        .replace("¥", "")
        .replace("%", "")
        .strip()
    )
    try:
        num = float(text)
        return num if math.isfinite(num) else None
    except Exception:
        return None


def _quantile(sorted_values: list[float], q: float) -> float:
    if not sorted_values:
        return 0.0
    if len(sorted_values) == 1:
        return sorted_values[0]
    pos = (len(sorted_values) - 1) * q
    lo = math.floor(pos)
    hi = math.ceil(pos)
    if lo == hi:
        return sorted_values[lo]
    return sorted_values[lo] * (hi - pos) + sorted_values[hi] * (pos - lo)


def _median(values: list[float]) -> float:
    if not values:
        return 0.0
    vals = sorted(values)
    mid = len(vals) // 2
    if len(vals) % 2:
        return vals[mid]
    return (vals[mid - 1] + vals[mid]) / 2


def _nlq_result_history_key(result: dict[str, Any]) -> str:
    matched = result.get("matched") or {}
    measure = str(matched.get("measureCode") or "")
    dims = ",".join(sorted(str(x) for x in (matched.get("dimensionCodes") or []) if x))
    mode = str(result.get("queryMode") or "")
    return f"{mode}|{measure}|{dims}"


def _extract_aggregate_numeric_series(result: dict[str, Any]) -> list[dict[str, Any]]:
    mode = str(result.get("queryMode") or "")
    if mode not in {"aggregate"}:
        return []
    matched = result.get("matched") or {}
    measure_code = str(matched.get("measureCode") or "")
    cell_list = (((result.get("result") or {}).get("data") or {}).get("cellList") or [])
    if not isinstance(cell_list, list):
        return []
    series: list[dict[str, Any]] = []
    for row_idx, row in enumerate(cell_list):
        if not isinstance(row, list):
            continue
        dim_labels: list[str] = []
        for cell in row:
            if not isinstance(cell, dict):
                continue
            code = str(cell.get("code") or "")
            typ = str(cell.get("type") or "").upper()
            if typ == "DIMENSION" or code.startswith("DIM_"):
                dim_labels.append(str(cell.get("data") or cell.get("id") or ""))
        label = " / ".join(x for x in dim_labels if x) or f"row-{row_idx + 1}"
        for cell in row:
            if not isinstance(cell, dict):
                continue
            code = str(cell.get("code") or "")
            typ = str(cell.get("type") or "").upper()
            if measure_code and code != measure_code:
                continue
            if not measure_code and typ != "MEASURE" and not code.startswith("MEAS_"):
                continue
            value = _safe_float(cell.get("data"))
            if value is None:
                continue
            series.append({
                "label": label,
                "code": code,
                "name": cell.get("name") or code,
                "value": value,
            })
    return series


def _is_cross_level_funnel_result(result: dict[str, Any]) -> bool:
    matched = result.get("matched") or {}
    measure_text = " ".join([
        str(matched.get("measureCode") or ""),
        str(matched.get("measureName") or ""),
    ]).lower()
    dim_texts: list[str] = []
    for dim in matched.get("dimensions") or []:
        if isinstance(dim, dict):
            dim_texts.append(str(dim.get("code") or ""))
            dim_texts.append(str(dim.get("name") or ""))
    dim_texts.extend(str(code) for code in (matched.get("dimensionCodes") or []))
    dim_text = " ".join(dim_texts).lower()
    return (
        ("celn" in measure_text or "celn" in dim_text)
        and ("funnel" in measure_text or "funnel" in dim_text or "漏斗" in measure_text or "漏斗" in dim_text)
        and ("stage" in dim_text or "阶段" in dim_text or "group" in dim_text or "分组" in dim_text)
    )


def _aggregate_statistical_checks(result: dict[str, Any], series: list[dict[str, Any]]) -> list[dict[str, Any]]:
    if not _is_cross_level_funnel_result(result):
        return _statistical_checks_for_values(series)
    values = [float(item["value"]) for item in series if _safe_float(item.get("value")) is not None]
    if not values:
        return [_stat_check(
            "numeric_values_present",
            "数值可解析",
            "warning",
            "当前结果没有可用于统计校验的数值列。",
            severity="major",
        )]
    return [
        _stat_check(
            "numeric_values_present",
            "数值可解析",
            "passed",
            f"已解析 {len(values)} 个指标数值。",
            evidence={"count": len(values), "zeroCount": sum(1 for v in values if abs(v) < 1e-12)},
        ),
        _stat_check(
            "cross_level_funnel_iqr_skipped",
            "跨层级漏斗异常校验",
            "passed",
            "当前结果包含 CELN 漏斗分组、阶段、总量或转化等不同层级节点，已跳过跨层级 IQR 异常比较。",
        ),
    ]


def _stat_check(
    check_id: str,
    name: str,
    status: str,
    message: str,
    *,
    severity: str = "minor",
    evidence: Optional[dict[str, Any]] = None,
) -> dict[str, Any]:
    return {
        "id": check_id,
        "name": name,
        "status": status,
        "severity": severity,
        "message": message,
        "evidence": evidence or {},
    }


def _statistical_checks_for_values(series: list[dict[str, Any]]) -> list[dict[str, Any]]:
    values = [float(item["value"]) for item in series if _safe_float(item.get("value")) is not None]
    checks: list[dict[str, Any]] = []
    if not values:
        checks.append(_stat_check(
            "numeric_values_present",
            "数值可解析",
            "warning",
            "当前结果没有可用于统计校验的数值列。",
            severity="major",
        ))
        return checks

    n = len(values)
    zero_count = sum(1 for v in values if abs(v) < 1e-12)
    checks.append(_stat_check(
        "numeric_values_present",
        "数值可解析",
        "passed",
        f"已解析 {n} 个指标数值。",
        evidence={"count": n, "zeroCount": zero_count},
    ))
    if n >= 3 and zero_count == n:
        checks.append(_stat_check(
            "all_zero",
            "全零检测",
            "warning",
            "当前指标结果全部为 0，可能需要确认筛选条件或数据口径。",
            severity="major",
        ))

    vals_sorted = sorted(values)
    if n >= 4:
        q1 = _quantile(vals_sorted, 0.25)
        q3 = _quantile(vals_sorted, 0.75)
        iqr = q3 - q1
        if abs(iqr) > 1e-12:
            lower = q1 - 1.5 * iqr
            upper = q3 + 1.5 * iqr
            far_lower = q1 - 3 * iqr
            far_upper = q3 + 3 * iqr
            outliers = [item for item in series if item["value"] < lower or item["value"] > upper]
            far_outliers = [item for item in series if item["value"] < far_lower or item["value"] > far_upper]
            if outliers:
                top = sorted(outliers, key=lambda x: abs(float(x["value"])), reverse=True)[:5]
                checks.append(_stat_check(
                    "iqr_outlier",
                    "IQR 四分位距异常",
                    "warning",
                    f"发现 {len(outliers)} 个分组值超出 IQR 正常区间。",
                    severity="critical" if far_outliers else "major",
                    evidence={
                        "q1": q1,
                        "q3": q3,
                        "iqr": iqr,
                        "lower": lower,
                        "upper": upper,
                        "outlierCount": len(outliers),
                        "topOutliers": top,
                    },
                ))
            else:
                checks.append(_stat_check(
                    "iqr_outlier",
                    "IQR 四分位距异常",
                    "passed",
                    "未发现 IQR 离群分组。",
                    evidence={"q1": q1, "q3": q3, "iqr": iqr},
                ))

    if n >= 4:
        med = _median(values)
        deviations = [abs(v - med) for v in values]
        mad = _median(deviations)
        if mad > 1e-12:
            robust = [
                {
                    **item,
                    "robustZ": abs(0.6745 * (float(item["value"]) - med) / mad),
                }
                for item in series
            ]
            flagged = [item for item in robust if item["robustZ"] > 3.5]
            if flagged:
                top = sorted(flagged, key=lambda x: x["robustZ"], reverse=True)[:5]
                max_z = max(item["robustZ"] for item in flagged)
                checks.append(_stat_check(
                    "mad_robust_zscore",
                    "MAD Robust Z-score",
                    "warning",
                    f"发现 {len(flagged)} 个分组值 robust z-score > 3.5。",
                    severity="critical" if max_z > 5 else "major",
                    evidence={
                        "median": med,
                        "mad": mad,
                        "maxRobustZ": max_z,
                        "topOutliers": top,
                    },
                ))
            else:
                checks.append(_stat_check(
                    "mad_robust_zscore",
                    "MAD Robust Z-score",
                    "passed",
                    "未发现 MAD robust z-score 异常。",
                    evidence={"median": med, "mad": mad},
                ))

    if n >= 3:
        abs_values = [abs(v) for v in values]
        total_abs = sum(abs_values)
        if total_abs > 1e-12:
            max_idx = max(range(n), key=lambda idx: abs_values[idx])
            top_share = abs_values[max_idx] / total_abs
            if top_share >= 0.8:
                checks.append(_stat_check(
                    "top_share_concentration",
                    "Top Share 集中度",
                    "warning",
                    f"最大分组占总体绝对值的 {top_share * 100:.1f}%，集中度偏高。",
                    severity="critical" if top_share >= 0.9 else "major",
                    evidence={
                        "topShare": top_share,
                        "topItem": series[max_idx],
                        "totalAbs": total_abs,
                    },
                ))
            else:
                checks.append(_stat_check(
                    "top_share_concentration",
                    "Top Share 集中度",
                    "passed",
                    f"最大分组占比 {top_share * 100:.1f}%，未超过集中度阈值。",
                    evidence={"topShare": top_share},
                ))

            probs = [v / total_abs for v in abs_values if v > 0]
            if len(probs) >= 2:
                entropy = -sum(p * math.log(p) for p in probs) / math.log(len(probs))
                if entropy < 0.35 and n >= 5:
                    checks.append(_stat_check(
                        "entropy_distribution",
                        "Entropy 分布熵",
                        "warning",
                        f"分布熵 {entropy:.2f}，说明结果高度集中在少数分组。",
                        severity="major",
                        evidence={"entropy": entropy},
                    ))
                else:
                    checks.append(_stat_check(
                        "entropy_distribution",
                        "Entropy 分布熵",
                        "passed",
                        f"分布熵 {entropy:.2f}，未发现明显低熵集中。",
                        evidence={"entropy": entropy},
                    ))

    if n >= 3:
        mean = sum(values) / n
        if abs(mean) > 1e-12:
            variance = sum((v - mean) ** 2 for v in values) / max(n - 1, 1)
            cv = math.sqrt(variance) / abs(mean)
            if cv >= 2:
                checks.append(_stat_check(
                    "coefficient_of_variation",
                    "Coefficient of Variation 变异系数",
                    "warning",
                    f"变异系数 {cv:.2f}，分组间波动较大。",
                    severity="critical" if cv >= 5 else "major",
                    evidence={"mean": mean, "cv": cv},
                ))
            else:
                checks.append(_stat_check(
                    "coefficient_of_variation",
                    "Coefficient of Variation 变异系数",
                    "passed",
                    f"变异系数 {cv:.2f}，分组波动未超过阈值。",
                    evidence={"mean": mean, "cv": cv},
                ))

    return checks


def _history_checks_for_result(result: dict[str, Any], series: list[dict[str, Any]]) -> list[dict[str, Any]]:
    if not series:
        return []
    key = _nlq_result_history_key(result)
    current_total = sum(float(item["value"]) for item in series)
    now = time.time()
    with _nlq_result_history_lock:
        history = _nlq_result_history.setdefault(key, deque(maxlen=50))
        previous = list(history)
        history.append({
            "ts": now,
            "question": result.get("question") or "",
            "total": current_total,
            "rowCount": len(series),
        })

    if len(previous) < 5:
        return [_stat_check(
            "history_baseline",
            "最近 50 次相关查询历史基线",
            "passed",
            f"当前相关历史仅 {len(previous)} 次，暂不做历史偏离告警。",
            evidence={"historyCount": len(previous), "currentTotal": current_total},
        )]

    totals = [float(item.get("total") or 0) for item in previous]
    med = _median(totals)
    mad = _median([abs(v - med) for v in totals])
    status = "passed"
    severity = "minor"
    message = "当前汇总值未显著偏离最近相关查询历史。"
    evidence: dict[str, Any] = {
        "historyCount": len(previous),
        "currentTotal": current_total,
        "median": med,
        "mad": mad,
    }
    if mad > 1e-12:
        robust_z = abs(0.6745 * (current_total - med) / mad)
        evidence["robustZ"] = robust_z
        if robust_z > 3.5:
            status = "warning"
            severity = "critical" if robust_z > 5 else "major"
            message = f"当前汇总值相对最近 {len(previous)} 次相关查询偏离较大，历史 robust z-score={robust_z:.2f}。"
    else:
        vals_sorted = sorted(totals)
        q1 = _quantile(vals_sorted, 0.25)
        q3 = _quantile(vals_sorted, 0.75)
        iqr = q3 - q1
        evidence.update({"q1": q1, "q3": q3, "iqr": iqr})
        if iqr > 1e-12 and (current_total < q1 - 1.5 * iqr or current_total > q3 + 1.5 * iqr):
            status = "warning"
            severity = "major"
            message = f"当前汇总值超出最近 {len(previous)} 次相关查询的 IQR 历史区间。"
    return [_stat_check(
        "history_deviation",
        "最近 50 次相关查询历史偏离",
        status,
        message,
        severity=severity,
        evidence=evidence,
    )]


def _detail_result_checks(result: dict[str, Any]) -> tuple[list[dict[str, Any]], list[dict[str, Any]]]:
    detail = result.get("detailData") or {}
    records = detail.get("records") or []
    columns = detail.get("columns") or []
    checks: list[dict[str, Any]] = []
    statistical: list[dict[str, Any]] = []
    if not isinstance(records, list):
        records = []
    if not isinstance(columns, list):
        columns = []

    if not columns:
        checks.append(_stat_check(
            "detail_columns_present",
            "明细字段存在",
            "failed",
            "明细结果没有返回字段列表。",
            severity="blocking",
        ))
    else:
        checks.append(_stat_check(
            "detail_columns_present",
            "明细字段存在",
            "passed",
            f"明细结果包含 {len(columns)} 个字段。",
            evidence={"columnCount": len(columns)},
        ))
    if not records:
        checks.append(_stat_check(
            "detail_records_present",
            "明细记录存在",
            "warning",
            "查询成功，但没有返回明细记录。",
            severity="major",
        ))
    else:
        checks.append(_stat_check(
            "detail_records_present",
            "明细记录存在",
            "passed",
            f"返回 {len(records)} 条明细记录。",
            evidence={"rowCount": len(records)},
        ))

    if records:
        keys = set()
        for record in records[:20]:
            if isinstance(record, dict):
                keys.update(record.keys())
        has_business_key = any(re.search(r"(order|订单|单号|id|编号|number)", str(key), re.I) for key in keys)
        checks.append(_stat_check(
            "business_key_present",
            "业务键识别",
            "passed" if has_business_key else "warning",
            "已识别订单号/编号/ID 类业务键。" if has_business_key else "未明显识别订单号、单号或 ID 类业务键，明细追溯性较弱。",
            severity="minor" if has_business_key else "major",
        ))

        numeric_columns: list[tuple[str, list[float]]] = []
        for key in sorted(keys):
            vals = [
                _safe_float(record.get(key))
                for record in records
                if isinstance(record, dict)
            ]
            nums = [v for v in vals if v is not None]
            if len(nums) >= max(4, min(10, len(records) // 3)):
                numeric_columns.append((str(key), nums))
        if numeric_columns:
            key, nums = max(numeric_columns, key=lambda item: len(item[1]))
            series = [
                {"label": f"row-{idx + 1}", "code": key, "name": key, "value": value}
                for idx, value in enumerate(nums[:500])
            ]
            statistical = _statistical_checks_for_values(series)
            for item in statistical:
                item["id"] = f"detail_{item['id']}"
                item["name"] = f"明细数值列 {key} · {item['name']}"
        else:
            statistical.append(_stat_check(
                "detail_numeric_scan",
                "明细数值列扫描",
                "passed",
                "未找到足够样本的数值列，跳过明细统计异常检验。",
            ))
    return checks, statistical


def _relationship_result_checks(result: dict[str, Any]) -> list[dict[str, Any]]:
    analysis = result.get("relationshipAnalysis") or {}
    sample = analysis.get("sample") or {}
    valid_rows = int(sample.get("validRowCount") or sample.get("rowCount") or 0)
    checks: list[dict[str, Any]] = []
    if valid_rows <= 0:
        checks.append(_stat_check(
            "relationship_sample_present",
            "关系分析样本",
            "failed",
            "关系分析没有可用样本，结果不足以支撑判断。",
            severity="blocking",
        ))
    elif valid_rows < 30:
        checks.append(_stat_check(
            "relationship_sample_present",
            "关系分析样本",
            "warning",
            f"关系分析样本仅 {valid_rows} 行，相关性和差异判断稳定性较弱。",
            severity="major",
            evidence={"validRowCount": valid_rows},
        ))
    else:
        checks.append(_stat_check(
            "relationship_sample_present",
            "关系分析样本",
            "passed",
            f"关系分析使用 {valid_rows} 行有效样本。",
            evidence={"validRowCount": valid_rows},
        ))

    correlations = analysis.get("correlations") or []
    if isinstance(correlations, list) and correlations:
        weak = [
            item for item in correlations
            if isinstance(item, dict) and int(item.get("sampleCount") or 0) < 30
        ]
        if weak:
            checks.append(_stat_check(
                "relationship_correlation_sample",
                "相关性样本量",
                "warning",
                f"{len(weak)} 个相关性结果样本量不足 30，建议谨慎解读。",
                severity="major",
            ))
        else:
            checks.append(_stat_check(
                "relationship_correlation_sample",
                "相关性样本量",
                "passed",
                "相关性结果样本量满足快速校验要求。",
            ))
    return checks


def _attach_result_validation(result: dict[str, Any]) -> dict[str, Any]:
    if not isinstance(result, dict):
        return result
    mode = str(result.get("queryMode") or "")
    checks: list[dict[str, Any]] = []
    statistical_checks: list[dict[str, Any]] = []
    history_checks: list[dict[str, Any]] = []

    if mode == "aggregate":
        da_result = result.get("result") or {}
        if da_result.get("ok") is False:
            checks.append(_stat_check(
                "da_ok",
                "DA 查询成功",
                "failed",
                str(da_result.get("error") or "DA 查询失败"),
                severity="blocking",
            ))
        else:
            checks.append(_stat_check("da_ok", "DA 查询成功", "passed", "DA 返回成功。"))
        series = _extract_aggregate_numeric_series(result)
        statistical_checks = _aggregate_statistical_checks(result, series)
        history_checks = _history_checks_for_result(result, series) if result.get("ok") else []
    elif mode in {"detail", "analyze_detail", "entity_lookup"}:
        checks, statistical_checks = _detail_result_checks(result)
        if mode == "analyze_detail":
            graph = result.get("graphContext") or {}
            if not graph.get("joinPaths") and not graph.get("detailColumns"):
                checks.append(_stat_check(
                    "detail_graph_context",
                    "明细图谱上下文",
                    "warning",
                    "明细结果缺少可用图谱关联路径或字段注释，增强分析边界较大。",
                    severity="major",
                ))
            else:
                checks.append(_stat_check(
                    "detail_graph_context",
                    "明细图谱上下文",
                    "passed",
                    "明细结果已关联图谱上下文。",
                ))
    elif mode == "relationship_analysis":
        checks = _relationship_result_checks(result)
    elif mode == "problem_orders":
        summary = result.get("summary") or {}
        if result.get("problemOrderMode") == "specific_document":
            if int(summary.get("documentRows") or 0) <= 0:
                checks.append(_stat_check(
                    "specific_document_detail",
                    "指定单据明细",
                    "failed",
                    "没有返回指定单据明细，无法继续做单据追踪校验。",
                    severity="blocking",
                ))
            elif int(summary.get("scannedRules") or 0) <= 0:
                checks.append(_stat_check(
                    "document_rules_scanned",
                    "单据追踪规则扫描",
                    "failed",
                    "没有扫描到可用的单据追踪规则。",
                    severity="blocking",
                ))
            elif int(summary.get("returnedRows") or 0) <= 0:
                checks.append(_stat_check(
                    "specific_document_trace",
                    "指定单据追踪",
                    "passed",
                    "指定单据明细已返回，单据追踪规则已扫描；当前单据未命中已返回的异常规则样本。",
                ))
            else:
                checks.append(_stat_check(
                    "specific_document_trace",
                    "指定单据追踪",
                    "passed",
                    f"指定单据命中 {summary.get('returnedRows')} 条单据追踪规则样本。",
                ))
        elif int(summary.get("scannedRules") or 0) <= 0:
            checks.append(_stat_check(
                "document_rules_scanned",
                "单据追踪规则扫描",
                "failed",
                "没有扫描到可用的单据追踪规则。",
                severity="blocking",
            ))
        elif int(summary.get("returnedRows") or 0) <= 0:
            checks.append(_stat_check(
                "problem_orders_returned",
                "问题订单返回",
                "warning",
                "规则已扫描，但当前结果没有返回问题订单样本。",
                severity="major",
            ))
        else:
            checks.append(_stat_check(
                "problem_orders_returned",
                "问题订单返回",
                "passed",
                f"返回 {summary.get('returnedRows')} 条问题订单样本。",
            ))

    all_checks = checks + statistical_checks + history_checks
    has_blocking = any(c.get("status") == "failed" and c.get("severity") == "blocking" for c in all_checks)
    has_critical = any(c.get("status") == "warning" and c.get("severity") == "critical" for c in all_checks)
    has_major = any(c.get("status") == "warning" and c.get("severity") in {"major", "critical"} for c in all_checks)
    if has_blocking:
        status, confidence = "failed", "low"
        summary = "结果校验失败，当前结果不足以支撑回答。"
    elif has_critical:
        status, confidence = "warning", "medium"
        summary = "结果可返回，但统计校验发现明显异常，请结合业务判断。"
    elif has_major:
        status, confidence = "warning", "medium"
        summary = "结果可返回，但存在统计或样本边界。"
    else:
        status, confidence = "passed", "high"
        summary = "结果校验通过，未发现明显统计异常。"

    result["validation"] = {
        "status": status,
        "confidence": confidence,
        "canAnswer": not has_blocking,
        "shouldReject": has_blocking,
        "summary": summary,
        "checks": checks,
        "statisticalChecks": statistical_checks,
        "historyChecks": history_checks,
        "warnings": [c for c in all_checks if c.get("status") == "warning"],
        "blockingErrors": [c for c in all_checks if c.get("status") == "failed"],
    }
    return result


def _build_result_evidence(result: dict[str, Any]) -> list[dict[str, Any]]:
    evidence: list[dict[str, Any]] = []
    mode = str(result.get("queryMode") or "")
    matched = result.get("matched") or {}
    validation = result.get("validation") or {}

    def add(kind: str, title: str, detail: str, payload: Optional[dict[str, Any]] = None) -> None:
        evidence.append({
            "id": f"E{len(evidence) + 1}",
            "kind": kind,
            "title": title,
            "detail": detail,
            "payload": payload or {},
        })

    if matched.get("measureName") or matched.get("measureCode"):
        add(
            "semantic_match",
            "指标匹配",
            f"匹配指标：{matched.get('measureName') or matched.get('measureCode')}",
            {"matched": matched},
        )
    if mode == "aggregate":
        series = _extract_aggregate_numeric_series(result)
        if series:
            values = [float(item["value"]) for item in series]
            add(
                "result_rows",
                "聚合结果",
                f"返回 {len(series)} 个指标数值，合计 {sum(values):.4g}。",
                {"rowCount": len(series), "total": sum(values)},
            )
    elif mode in {"detail", "analyze_detail", "entity_lookup"}:
        detail = result.get("detailData") or {}
        records = detail.get("records") or []
        columns = detail.get("columns") or []
        add(
            "detail_rows",
            "明细结果",
            f"返回 {len(records) if isinstance(records, list) else 0} 条明细、{len(columns) if isinstance(columns, list) else 0} 个字段。",
            {"rowCount": len(records) if isinstance(records, list) else 0, "columnCount": len(columns) if isinstance(columns, list) else 0},
        )
    elif mode == "problem_orders":
        summary = result.get("summary") or {}
        if result.get("problemOrderMode") == "specific_document":
            add(
                "document_detail",
                "指定单据明细",
                f"指定单据 {result.get('documentNo') or ''} 返回 {summary.get('documentRows', 0)} 行明细。",
                {"documentNo": result.get("documentNo"), "rowCount": summary.get("documentRows", 0)},
            )
        add(
            "problem_orders",
            "单据追踪",
            f"扫描 {summary.get('scannedRules', 0)} 条规则，命中 {summary.get('matchedRows', 0)} 条明细，返回 {summary.get('returnedRows', 0)} 条样本。",
            {"summary": summary},
        )
    elif mode == "relationship_analysis":
        sample = ((result.get("relationshipAnalysis") or {}).get("sample") or {})
        add(
            "relationship_sample",
            "关系分析样本",
            f"关系分析使用 {sample.get('validRowCount') or 0} 行有效样本。",
            {"sample": sample},
        )

    for idx, item in enumerate(validation.get("warnings") or [], 1):
        evidence.append({
            "id": f"V{idx}",
            "kind": "validation_warning",
            "title": item.get("name") or item.get("id") or "校验提示",
            "detail": item.get("message") or "",
            "payload": item.get("evidence") or {},
        })
    return evidence[:20]


def _attach_trace(
    result: dict[str, Any],
    trace_id: str = "",
    conversation_id: str = "",
    source: str = "nlq",
) -> dict[str, Any]:
    if not isinstance(result, dict):
        return result
    trace_id = trace_id or str(uuid.uuid4())
    trace = {
        "traceId": trace_id,
        "conversationId": conversation_id,
        "ts": time.time(),
        "question": result.get("question") or "",
        "queryMode": result.get("queryMode") or "",
        "ok": bool(result.get("ok")),
        "matched": result.get("matched") or {},
        "intent": result.get("intent") or {},
        "daPayload": result.get("daPayload"),
        "validation": result.get("validation") or {},
        "evidence": result.get("evidence") or [],
        "crossValidation": result.get("crossValidation") or {},
        "diagnostics": result.get("diagnostics") or {},
        "elapsedMs": result.get("elapsedMs"),
    }
    with _nlq_trace_lock:
        _nlq_trace_store.append(trace)
    result["traceId"] = trace_id
    result["trace"] = trace
    _feedback_complete_query_trace(
        trace_id,
        result,
        conversation_id=conversation_id,
        source=source,
    )
    return result


async def _attach_cross_validation(result: dict[str, Any]) -> dict[str, Any]:
    if not isinstance(result, dict) or str(result.get("queryMode") or "") != "aggregate":
        result.setdefault("crossValidation", {})
        return result
    validation = result.get("validation") or {}
    if not validation.get("warnings"):
        result["crossValidation"] = {"status": "skipped", "reason": "统计校验未发现异常，未触发自动交叉验证。"}
        return result

    series = _extract_aggregate_numeric_series(result)
    values_abs = [(item, abs(float(item["value"]))) for item in series]
    total_abs = sum(v for _item, v in values_abs)
    top_contributors = []
    if total_abs > 1e-12:
        for item, abs_value in sorted(values_abs, key=lambda x: x[1], reverse=True)[:5]:
            top_contributors.append({
                "label": item.get("label"),
                "value": item.get("value"),
                "sharePct": round(abs_value / total_abs * 100, 2),
            })

    anomaly_rows = []
    for warning in validation.get("warnings") or []:
        evidence = warning.get("evidence") or {}
        for item in evidence.get("topOutliers") or []:
            if isinstance(item, dict):
                anomaly_rows.append({
                    "label": item.get("label"),
                    "value": item.get("value"),
                    "source": warning.get("name") or warning.get("id"),
                })

    detail_sample = {"status": "skipped", "reason": "当前查询没有可复用的 DA payload。"}
    payload = result.get("daPayload")
    if isinstance(payload, dict) and payload.get("configureList"):
        try:
            detail_payload = {
                **payload,
                "measureDetail": True,
                "pageSize": 5,
                "pageNum": 1,
            }
            da_result = await asyncio.to_thread(_pivot_da_query, detail_payload)
            columns, records, da_data = await asyncio.to_thread(_detail_records_from_da_result, da_result)
            detail_sample = {
                "status": "ok",
                "columns": columns[:12],
                "records": records[:5],
                "rowCount": len(records),
                "reviewSql": da_data.get("reviewSql") or "",
            }
        except Exception as exc:
            detail_sample = {"status": "error", "error": str(exc)}

    document_trace = {"status": "skipped", "reason": "未找到可用于单据追踪的启用规则。"}
    measure_code = str((result.get("matched") or {}).get("measureCode") or "")
    if measure_code:
        try:
            from kg_builder.alerts import models as alert_models

            rules, _total = await asyncio.to_thread(alert_models.list_rules, 1, 100, "enabled")
            doc_rules = [
                rule for rule in rules
                if str(rule.get("measure_code") or "") == measure_code
                and (str(rule.get("builtin_type") or "").lower() == "document"
                     or str(rule.get("operator") or "").lower() == "document"
                     or _parse_rule_document_config(rule))
            ][:3]
            hits = []
            for rule in doc_rules:
                conditions = _document_rule_conditions(rule)
                if not conditions:
                    continue
                scan = await _run_alert_document_scan({
                    "measureCode": measure_code,
                    "conditions": conditions,
                    "pageSize": 100,
                    "maxRows": 500,
                    "maxMatches": 5,
                })
                summary = scan.get("summary") or {}
                if int(summary.get("matchedRows") or 0) > 0:
                    hits.append({
                        "ruleId": rule.get("id"),
                        "ruleName": rule.get("name") or "单据追踪",
                        "matchedRows": summary.get("matchedRows") or 0,
                        "matches": (scan.get("matches") or [])[:3],
                    })
            document_trace = {
                "status": "ok",
                "scannedRules": len(doc_rules),
                "hitRules": len(hits),
                "hits": hits,
            }
        except Exception as exc:
            document_trace = {"status": "error", "error": str(exc)}

    history = validation.get("historyChecks") or []
    result["crossValidation"] = {
        "status": "ok",
        "reason": "统计校验发现异常，已自动补充轻量交叉验证。",
        "topContributors": top_contributors,
        "anomalyRows": anomaly_rows[:10],
        "detailSample": detail_sample,
        "documentTrace": document_trace,
        "historyChecks": history,
    }
    return result


def _is_problem_order_question(question: str) -> bool:
    q = re.sub(r"\s+", "", question or "")
    if not q:
        return False
    return bool(re.search(r"(有问题|问题|异常|风险|预警|告警).*(订单|单据)|(订单|单据).*(有问题|问题|异常|风险|预警|告警)|单据追踪|问题订单|异常订单", q))


def _parse_specific_document_lookup(question: str) -> dict[str, str]:
    q = re.sub(r"\s+", " ", question or "").strip()
    if not q:
        return {}
    patterns = [
        r"(?P<field>[\u4e00-\u9fa5A-Za-z_][\u4e00-\u9fa5A-Za-z0-9_\-\s]{0,60})\s*(?:为|是|=|:|：)\s*[\"'“”]?(?P<value>[A-Za-z0-9_.\-]+)[\"'“”]?",
        r"(?:查|查询|查看)?\s*(?P<field>订单编号|订单号|订单ID|单据编号|单据号|退货单号|销售单号|order[_\s-](?:number|no|id))\s*(?:为|是|=|:|：)?\s*[\"'“”]?(?P<value>[A-Za-z0-9_.\-]+)[\"'“”]?",
    ]
    for pat in patterns:
        m = re.search(pat, q, re.I)
        if not m:
            continue
        field = str(m.group("field") or "").strip()
        value = str(m.group("value") or "").strip().strip("'\"“”")
        if value and re.search(r"订单编号|订单号|订单ID|单据编号|单据号|退货单号|销售单号|order|bill|document", field, re.I):
            return {"fieldText": field, "value": value}
    return {}


def _is_specific_document_lookup(question: str) -> bool:
    return bool(_parse_specific_document_lookup(question))


def _parse_rule_document_config(rule: dict[str, Any]) -> dict[str, Any]:
    dims = _json_dict(rule.get("dimensions_json"))
    doc = dims.get("__document") if isinstance(dims, dict) else {}
    return doc if isinstance(doc, dict) else {}


def _rule_recent_at(rule: dict[str, Any]) -> Any:
    for key in ("last_triggered_at", "updated_at", "created_at"):
        value = rule.get(key)
        if value:
            return value
    return None


def _is_recent_rule(rule: dict[str, Any], days: int = 92) -> bool:
    from datetime import datetime, timedelta

    value = _rule_recent_at(rule)
    if not value:
        return False
    if isinstance(value, datetime):
        dt = value
    else:
        text = str(value).strip().replace("Z", "+00:00")
        try:
            dt = datetime.fromisoformat(text)
        except Exception:
            return False
    if dt.tzinfo is not None:
        dt = dt.replace(tzinfo=None)
    return dt >= datetime.now() - timedelta(days=days)


def _document_rule_conditions(rule: dict[str, Any]) -> list[dict[str, Any]]:
    doc = _parse_rule_document_config(rule)
    raw_conditions = doc.get("conditions")
    conditions = raw_conditions if isinstance(raw_conditions, list) else []
    out: list[dict[str, Any]] = []
    for item in conditions:
        if not isinstance(item, dict):
            continue
        out.append({
            "columnCode": str(item.get("columnCode") or item.get("column") or "").strip(),
            "operator": str(item.get("operator") or "eq").lower(),
            "value": item.get("value", 0),
            "source": str(item.get("source") or "rule"),
        })
    if not out and (doc.get("columnCode") or doc.get("value") is not None):
        out.append({
            "columnCode": str(doc.get("columnCode") or "").strip(),
            "operator": str(doc.get("operator") or "eq").lower(),
            "value": doc.get("value", 0),
            "source": "rule",
        })
    return out


def _document_rule_max_rows(rule: dict[str, Any]) -> int:
    doc = _parse_rule_document_config(rule)
    try:
        return max(100, min(int(doc.get("maxRows") or 500), 5000))
    except Exception:
        return 500


def _condition_text(condition: dict[str, Any]) -> str:
    labels = {"eq": "=", "lt": "<", "lte": "<=", "gt": ">", "gte": ">="}
    name = condition.get("columnName") or condition.get("columnCode") or "自动识别列"
    op = labels.get(str(condition.get("operator") or "eq").lower(), str(condition.get("operator") or "eq"))
    return f"{name} {op} {condition.get('value', '')}"


async def _query_problem_orders(question: str, page_size: int, page_num: int) -> dict[str, Any]:
    from kg_builder.alerts import models as alert_models

    rules, _total = await asyncio.to_thread(alert_models.list_rules, 1, 200, "enabled")
    document_rules = [
        rule for rule in rules
        if (str(rule.get("builtin_type") or "").lower() == "document"
            or str(rule.get("operator") or "").lower() == "document"
            or _parse_rule_document_config(rule))
        and str(rule.get("measure_code") or "").strip()
    ]
    recent_rules = [rule for rule in document_rules if _is_recent_rule(rule)]
    scan_rules = recent_rules or document_rules
    severity_order = {"critical": 0, "warning": 1, "notice": 2, "info": 3}
    scan_rules = sorted(
        scan_rules,
        key=lambda r: (
            severity_order.get(str(r.get("severity") or "").lower(), 9),
            -int(r.get("id") or 0),
        ),
    )[:8]

    per_rule_limit = max(5, min(page_size, 80))
    problem_orders: list[dict[str, Any]] = []
    rule_results: list[dict[str, Any]] = []
    seen: set[tuple[str, int, str]] = set()

    for rule in scan_rules:
        conditions = _document_rule_conditions(rule)
        if not conditions:
            continue
        body = {
            "measureCode": rule.get("measure_code"),
            "conditions": conditions,
            "pageSize": 200,
            "maxRows": _document_rule_max_rows(rule),
            "maxMatches": per_rule_limit,
        }
        try:
            result = await _run_alert_document_scan(body)
        except Exception as exc:
            rule_results.append({
                "ruleId": rule.get("id"),
                "ruleName": rule.get("name") or "单据追踪",
                "severity": rule.get("severity") or "",
                "measureCode": rule.get("measure_code") or "",
                "error": str(exc),
            })
            continue
        summary = result.get("summary") or {}
        resolved_conditions = result.get("conditions") or []
        condition_label = " 且 ".join(_condition_text(item) for item in resolved_conditions) or "单据追踪规则"
        rule_results.append({
            "ruleId": rule.get("id"),
            "ruleName": rule.get("name") or "单据追踪",
            "severity": rule.get("severity") or "",
            "measureCode": rule.get("measure_code") or "",
            "measureName": (result.get("measure") or {}).get("name") or rule.get("measure_code") or "",
            "condition": condition_label,
            "matchedRows": summary.get("matchedRows") or 0,
            "returnedRows": summary.get("returnedRows") or 0,
            "scannedRows": summary.get("scannedRows") or 0,
        })
        for idx, match in enumerate(result.get("matches") or []):
            order_no = str(match.get("orderNumber") or "").strip()
            target_column = str(result.get("targetColumn") or "")
            key = (order_no or f"row-{idx}", int(rule.get("id") or 0), target_column)
            if key in seen:
                continue
            seen.add(key)
            problem_orders.append({
                "orderNumber": order_no,
                "ruleId": rule.get("id"),
                "ruleName": rule.get("name") or "单据追踪",
                "severity": rule.get("severity") or "",
                "measureCode": rule.get("measure_code") or "",
                "measureName": (result.get("measure") or {}).get("name") or rule.get("measure_code") or "",
                "targetColumn": target_column,
                "targetColumnName": result.get("targetColumnName") or target_column,
                "targetValue": match.get("targetValue"),
                "condition": condition_label,
                "conditionValues": match.get("conditionValues") or {},
                "record": match.get("record") or {},
            })
            if len(problem_orders) >= max(1, min(page_size, 200)):
                break
        if len(problem_orders) >= max(1, min(page_size, 200)):
            break

    total_matched = sum(int(item.get("matchedRows") or 0) for item in rule_results)
    hit_rules = [item for item in rule_results if int(item.get("matchedRows") or 0) > 0]
    columns = [
        {"code": "orderNumber", "name": "订单编号"},
        {"code": "ruleName", "name": "命中规则"},
        {"code": "severity", "name": "严重等级"},
        {"code": "measureName", "name": "关联指标"},
        {"code": "targetColumnName", "name": "判定字段"},
        {"code": "targetValue", "name": "命中值"},
        {"code": "condition", "name": "判定条件"},
    ]
    return {
        "ok": True,
        "question": question,
        "queryMode": "problem_orders",
        "matched": {
            "measureCode": "alert_document_rules",
            "measureName": "监控预警单据追踪规则",
            "dimensionCodes": [],
            "dimensions": [],
        },
        "periodLabel": "最近三个月启用、更新或触发的单据追踪规则",
        "problemOrders": problem_orders,
        "ruleResults": rule_results,
        "detailData": {
            "columns": columns,
            "records": problem_orders,
            "rowCount": len(problem_orders),
        },
        "summary": {
            "scannedRules": len(rule_results),
            "hitRules": len(hit_rules),
            "matchedRows": total_matched,
            "returnedRows": len(problem_orders),
            "pageNum": max(1, page_num),
            "pageSize": max(1, min(page_size, 200)),
        },
        "explain": "基于监控预警中启用的单据追踪规则扫描明细，返回命中规则的问题订单样本。",
        "resolvedContext": {
            "queryMode": "problem_orders",
            "analysisMode": "document_trace",
            "lastQuestion": question,
        },
        "suggestedNextQuestions": [
            "这些问题订单按商品分布如何",
            "解释负利润订单的共同特征",
            "查看订单 8000000 的明细",
        ],
    }


def _same_document_no(a: Any, b: Any) -> bool:
    left = re.sub(r"\D+", "", str(a or ""))
    right = re.sub(r"\D+", "", str(b or ""))
    if left and right:
        return left == right
    return str(a or "").strip() == str(b or "").strip()


def _record_contains_document_no(record: dict[str, Any], document_no: str) -> bool:
    if not isinstance(record, dict) or not document_no:
        return False
    for key, value in record.items():
        if not re.search(r"订单|单据|单号|order|bill|document", str(key), re.I):
            continue
        if _same_document_no(value, document_no):
            return True
    return False


async def _query_specific_document_trace(
    question: str,
    page_size: int,
    page_num: int,
    ttl_path: Path,
) -> dict[str, Any]:
    from kg_builder.alerts import models as alert_models
    from kg_builder.nlq import NaturalLanguageQueryService

    parsed = _parse_specific_document_lookup(question)
    document_no = parsed.get("value") or ""
    service = NaturalLanguageQueryService(
        ttl_path=ttl_path,
        data_agent_url=_DATA_AGENT_URL,
        source_ttl_path=_get_active_path(),
        log_cb=lambda msg: logging.getLogger("uvicorn").info(msg),
    )
    entity_result = await asyncio.to_thread(
        service.entity_lookup,
        question,
        page_size=max(1, min(page_size, 10000)),
        page_num=max(1, page_num),
        intent={"mode": "entity_lookup", "entity": parsed},
    )
    if not entity_result.get("ok"):
        entity_result.setdefault("queryMode", "problem_orders")
        return entity_result

    rules, _total = await asyncio.to_thread(alert_models.list_rules, 1, 200, "enabled")
    document_rules = [
        rule for rule in rules
        if (str(rule.get("builtin_type") or "").lower() == "document"
            or str(rule.get("operator") or "").lower() == "document"
            or _parse_rule_document_config(rule))
        and str(rule.get("measure_code") or "").strip()
    ]
    recent_rules = [rule for rule in document_rules if _is_recent_rule(rule)]
    severity_order = {"critical": 0, "warning": 1, "notice": 2, "info": 3}
    scan_rules = sorted(
        recent_rules or document_rules,
        key=lambda r: (
            severity_order.get(str(r.get("severity") or "").lower(), 9),
            -int(r.get("id") or 0),
        ),
    )[:8]

    rule_results: list[dict[str, Any]] = []
    problem_orders: list[dict[str, Any]] = []
    seen: set[tuple[int, str]] = set()
    for rule in scan_rules:
        conditions = _document_rule_conditions(rule)
        if not conditions:
            continue
        try:
            scan = await _run_alert_document_scan({
                "measureCode": rule.get("measure_code"),
                "conditions": conditions,
                "pageSize": 200,
                "maxRows": _document_rule_max_rows(rule),
                "maxMatches": 1000,
            })
        except Exception as exc:
            rule_results.append({
                "ruleId": rule.get("id"),
                "ruleName": rule.get("name") or "单据追踪",
                "severity": rule.get("severity") or "",
                "measureCode": rule.get("measure_code") or "",
                "error": str(exc),
                "matchedRows": 0,
                "scannedRows": 0,
            })
            continue
        summary = scan.get("summary") or {}
        resolved_conditions = scan.get("conditions") or []
        condition_label = " 且 ".join(_condition_text(item) for item in resolved_conditions) or "单据追踪规则"
        specific_matches = []
        for match in scan.get("matches") or []:
            order_no = match.get("orderNumber")
            record = match.get("record") or {}
            if _same_document_no(order_no, document_no) or _record_contains_document_no(record, document_no):
                specific_matches.append(match)
        rule_results.append({
            "ruleId": rule.get("id"),
            "ruleName": rule.get("name") or "单据追踪",
            "severity": rule.get("severity") or "",
            "measureCode": rule.get("measure_code") or "",
            "measureName": (scan.get("measure") or {}).get("name") or rule.get("measure_code") or "",
            "condition": condition_label,
            "matchedRows": len(specific_matches),
            "globalMatchedRows": summary.get("matchedRows") or 0,
            "returnedRows": len(specific_matches),
            "scannedRows": summary.get("scannedRows") or 0,
        })
        for idx, match in enumerate(specific_matches):
            key = (int(rule.get("id") or 0), str(match.get("orderNumber") or idx))
            if key in seen:
                continue
            seen.add(key)
            first_condition = resolved_conditions[0] if resolved_conditions else {}
            target_column = str(scan.get("targetColumn") or first_condition.get("columnCode") or "")
            problem_orders.append({
                "orderNumber": match.get("orderNumber") or document_no,
                "ruleId": rule.get("id"),
                "ruleName": rule.get("name") or "单据追踪",
                "severity": rule.get("severity") or "",
                "measureCode": rule.get("measure_code") or "",
                "measureName": (scan.get("measure") or {}).get("name") or rule.get("measure_code") or "",
                "targetColumn": target_column,
                "targetColumnName": scan.get("targetColumnName") or first_condition.get("columnName") or target_column,
                "targetValue": match.get("targetValue"),
                "condition": condition_label,
                "conditionValues": match.get("conditionValues") or {},
                "record": match.get("record") or {},
            })

    hit_rules = [item for item in rule_results if int(item.get("matchedRows") or 0) > 0]
    detail = entity_result.get("detailData") or {}
    detail_rows = int(detail.get("rowCount") or len(detail.get("records") or []))
    explain = (
        f"已按「{parsed.get('fieldText') or '单据编号'} = {document_no}」获取单据明细，"
        f"并扫描启用的单据追踪规则；当前单据命中 {len(problem_orders)} 条规则样本。"
        if problem_orders
        else f"已按「{parsed.get('fieldText') or '单据编号'} = {document_no}」获取单据明细，"
             "并扫描启用的单据追踪规则；当前单据未命中已返回的规则样本。"
    )
    return {
        "ok": True,
        "question": question,
        "queryMode": "problem_orders",
        "problemOrderMode": "specific_document",
        "documentNo": document_no,
        "entity": entity_result.get("entity") or {},
        "documentDetail": detail,
        "joinedDimensions": entity_result.get("joinedDimensions") or [],
        "peerAnalysis": entity_result.get("peerAnalysis") or {},
        "graphContext": entity_result.get("graphContext") or {},
        "matched": entity_result.get("matched") or {
            "measureCode": "alert_document_rules",
            "measureName": "指定单据追踪",
            "dimensionCodes": [],
            "dimensions": [],
        },
        "periodLabel": "指定单据追踪：明细检索 + 启用规则扫描",
        "problemOrders": problem_orders,
        "ruleResults": rule_results,
        "detailData": {
            "columns": [
                {"code": "orderNumber", "name": "订单编号"},
                {"code": "ruleName", "name": "命中规则"},
                {"code": "severity", "name": "严重等级"},
                {"code": "measureName", "name": "关联指标"},
                {"code": "targetColumnName", "name": "判定字段"},
                {"code": "targetValue", "name": "命中值"},
                {"code": "condition", "name": "判定条件"},
            ],
            "records": problem_orders,
            "rowCount": len(problem_orders),
        },
        "summary": {
            "scannedRules": len(rule_results),
            "hitRules": len(hit_rules),
            "matchedRows": len(problem_orders),
            "returnedRows": len(problem_orders),
            "documentRows": detail_rows,
            "pageNum": max(1, page_num),
            "pageSize": max(1, min(page_size, 200)),
        },
        "explain": explain,
        "resolvedContext": {
            "queryMode": "problem_orders",
            "analysisMode": "document_trace",
            "lastQuestion": question,
            "documentNo": document_no,
        },
        "suggestedNextQuestions": [
            "解释这张单据的异常判定原因",
            "查看这张单据的同类对象分析",
            "这些问题订单按商品分布如何",
        ],
    }


@app.post("/api/nlq/query")
async def nlq_query(req: NLQRequest, request: Request):
    """Natural language -> business KG match -> DA query payload/result."""
    question = (req.question or "").strip()
    if not question:
        return JSONResponse({"ok": False, "error": "question 不能为空"}, status_code=400)
    request_started = time.time()
    conversation_id = (req.conversationId or "").strip() or str(uuid.uuid4())
    trace_id = str(uuid.uuid4())
    trace_source = re.sub(r"[^A-Za-z0-9_\-]", "", (req.source or "nlq"))[:40] or "nlq"
    _feedback_begin_query_trace(
        trace_id,
        question,
        conversation_id=conversation_id,
        parent_trace_id=(req.parentTraceId or "").strip(),
        source=trace_source,
    )

    def _finish_result(result: dict[str, Any]) -> dict[str, Any]:
        result.setdefault("question", question)
        result.setdefault("queryMode", req.queryMode)
        result.setdefault("elapsedMs", int((time.time() - request_started) * 1000))
        result["conversationId"] = conversation_id
        _attach_trace(
            result,
            trace_id=trace_id,
            conversation_id=conversation_id,
            source=trace_source,
        )
        return result

    ttl_path = BKG_DIR / "indicator-data.ttl"
    if not ttl_path.exists():
        result = _finish_result({
            "ok": False,
            "diagnosticCode": "BUSINESS_KG_NOT_FOUND",
            "error": "尚未生成业务图谱，请先在「业务图谱」生成 indicator-data.ttl",
        })
        return JSONResponse(result, status_code=404)

    with _nlq_context_lock:
        if req.resetContext:
            _nlq_context_store.pop(conversation_id, None)
        stored_context = dict(_nlq_context_store.get(conversation_id) or {})
    request_context = dict(stored_context)
    if req.context:
        request_context.update(req.context)

    if _is_specific_document_lookup(question):
        try:
            result = await _query_specific_document_trace(
                question,
                page_size=max(1, min(req.pageSize, 10000)),
                page_num=max(1, req.pageNum),
                ttl_path=ttl_path,
            )
            _attach_result_validation(result)
            await _attach_cross_validation(result)
            result["evidence"] = _build_result_evidence(result)
            _finish_result(result)
            resolved_context = result.get("resolvedContext")
            if isinstance(resolved_context, dict):
                with _nlq_context_lock:
                    if len(_nlq_context_store) >= 200 and conversation_id not in _nlq_context_store:
                        _nlq_context_store.pop(next(iter(_nlq_context_store)), None)
                    _nlq_context_store[conversation_id] = dict(resolved_context)
            return JSONResponse(result)
        except Exception as e:
            result = _finish_result({"ok": False, "error": str(e)})
            return JSONResponse(result, status_code=500)

    if _is_problem_order_question(question):
        try:
            result = await _query_problem_orders(
                question,
                page_size=max(1, min(req.pageSize, 10000)),
                page_num=max(1, req.pageNum),
            )
            _attach_result_validation(result)
            await _attach_cross_validation(result)
            result["evidence"] = _build_result_evidence(result)
            _finish_result(result)
            resolved_context = result.get("resolvedContext")
            if isinstance(resolved_context, dict):
                with _nlq_context_lock:
                    if len(_nlq_context_store) >= 200 and conversation_id not in _nlq_context_store:
                        _nlq_context_store.pop(next(iter(_nlq_context_store)), None)
                    _nlq_context_store[conversation_id] = dict(resolved_context)
            return JSONResponse(result)
        except Exception as e:
            result = _finish_result({"ok": False, "error": str(e)})
            return JSONResponse(result, status_code=500)

    def _run():
        from kg_builder.nlq import NaturalLanguageQueryService
        source_ttl_path = _semantic_source_path()
        service = NaturalLanguageQueryService(
            ttl_path=ttl_path,
            data_agent_url=_DATA_AGENT_URL,
            source_ttl_path=source_ttl_path,
            log_cb=lambda msg: logging.getLogger("uvicorn").info(msg),
            semantic_mapping_service=_semantic_mapping_service(ttl_path, source_ttl_path),
            authorization=str(request.headers.get("authorization") or ""),
        )
        return service.query(
            question,
            trace_id=trace_id,
            execute=req.execute,
            page_size=max(1, min(req.pageSize, 10000)),
            page_num=max(1, req.pageNum),
            max_dimensions=max(0, min(req.maxDimensions, 5)),
            query_mode=req.queryMode,
            context=request_context,
            is_follow_up=req.isFollowUp,
        )

    try:
        result = await asyncio.to_thread(_run)
        _attach_result_validation(result)
        await _attach_cross_validation(result)
        result["evidence"] = _build_result_evidence(result)
        _finish_result(result)
        resolved_context = result.get("resolvedContext")
        if isinstance(resolved_context, dict):
            with _nlq_context_lock:
                if len(_nlq_context_store) >= 200 and conversation_id not in _nlq_context_store:
                    _nlq_context_store.pop(next(iter(_nlq_context_store)), None)
                _nlq_context_store[conversation_id] = dict(resolved_context)
        return JSONResponse(result)
    except Exception as e:
        result = _finish_result({"ok": False, "error": str(e)})
        return JSONResponse(result, status_code=500)


@app.get("/api/nlq/traces")
async def nlq_traces(limit: int = 20):
    limit = max(1, min(int(limit or 20), 100))
    with _nlq_trace_lock:
        items = list(_nlq_trace_store)[-limit:]
    return {"items": items[::-1], "total": len(items)}


@app.get("/api/nlq/traces/{trace_id}")
async def nlq_trace_detail(trace_id: str):
    with _nlq_trace_lock:
        for item in reversed(_nlq_trace_store):
            if item.get("traceId") == trace_id:
                return item
    return JSONResponse({"error": "trace 不存在或已过期"}, status_code=404)


@app.post("/api/nlq/interpret")
async def nlq_interpret(req: NLQInterpretRequest):
    """Use the configured LLM to interpret a completed NLQ result."""

    def _compact(obj: Any, max_chars: int = 16000) -> str:
        text = json.dumps(obj, ensure_ascii=False, default=str)
        return text[:max_chars]

    def _strip_reasoning(text: str) -> str:
        text = str(text or "").strip()
        text = re.sub(r"(?is)<think>.*?</think>", "", text).strip()
        text = re.sub(r"(?is)^```(?:markdown|md)?\s*|\s*```$", "", text).strip()
        return text

    def _validate_interpretation_output(text: str) -> dict[str, Any]:
        checks: list[dict[str, Any]] = []
        evidence_ids = {
            str(item.get("id"))
            for item in (req.evidence or [])
            if item.get("id")
        }
        warning_count = len((req.validation or {}).get("warnings") or [])
        has_refs = bool(re.search(r"\[(?:E|V|C)\d+\]", text or ""))
        checks.append({
            "id": "evidence_refs_present",
            "status": "passed" if has_refs or not evidence_ids else "warning",
            "message": "解读已引用证据编号。" if has_refs else "解读未引用证据编号，建议补充 [E1]/[V1]/[C1]。",
        })
        strong_causal = bool(re.search(r"(导致|证明|必然|一定|根因|唯一原因)", text or ""))
        guarded = bool(re.search(r"(可能|相关|推测|需要进一步|不足以|不能证明|边界|样本)", text or ""))
        checks.append({
            "id": "causality_guard",
            "status": "warning" if strong_causal and not guarded else "passed",
            "message": "存在强因果表述但缺少不确定性边界。" if strong_causal and not guarded else "未发现无边界强因果表述。",
        })
        has_boundary = bool(re.search(r"(风险|边界|样本|不足|谨慎|不能|无法|校验)", text or ""))
        checks.append({
            "id": "warning_boundary_mentioned",
            "status": "passed" if warning_count == 0 or has_boundary else "warning",
            "message": "校验存在 warning，解读已说明边界。" if warning_count and has_boundary else (
                "校验存在 warning，但解读未明确说明边界。" if warning_count else "结果无校验 warning。"
            ),
        })
        status = "warning" if any(c["status"] == "warning" for c in checks) else "passed"
        return {
            "status": status,
            "checks": checks,
        }

    def _fallback_interpretation(reason: str) -> str:
        evidence = req.evidence or []
        validation = req.validation or {}
        result_summary = req.resultSummary or {}
        evidence_lines = []
        for item in evidence[:6]:
            eid = item.get("id") or f"E{len(evidence_lines) + 1}"
            title = item.get("title") or item.get("kind") or "证据"
            detail = item.get("detail") or ""
            evidence_lines.append(f"- [{eid}] {title}：{detail}")
        if not evidence_lines:
            evidence_lines.append("- 暂无结构化证据，当前解读只基于返回结果摘要。")

        row_count = (
            result_summary.get("rowCount")
            or (result_summary.get("summary") or {}).get("returnedRows")
            or (result_summary.get("summary") or {}).get("documentRows")
            or 0
        )
        warnings = validation.get("warnings") or []
        blockers = validation.get("blockingErrors") or []
        boundary = validation.get("summary") or "当前结果已完成后置校验。"
        if reason:
            boundary = f"{boundary} DeepSeek 当前未完成调用：{reason}"

        return "\n".join([
            "1. 核心结论",
            f"- 当前查询已返回结构化结果，样本/明细规模为 {row_count}。结论必须以已返回数据和校验项为准。",
            "",
            "2. 关键数据观察",
            *evidence_lines,
            "",
            "3. 可能业务含义",
            "- 当前结果可以作为问题定位或下一步追问的依据，但不单独证明强因果关系。",
            "",
            "4. 风险与边界",
            f"- {boundary}",
            f"- 校验 warning 数：{len(warnings)}；blocking 数：{len(blockers)}。如存在 warning，需要结合业务口径复核。",
            "- 当前为本地证据解读兜底；配置 DeepSeek 后将生成更完整的行业分析文本。",
            "",
            "5. 建议下一步分析",
            "- 优先查看命中规则、异常字段、明细样本和历史对比；若是单据问题，继续下钻同类订单和规则阈值。",
        ])

    def _call_llm() -> str:
        import re
        import urllib.error
        import urllib.request
        from kg_builder.utils.llm_config import chat_completions_url, llm_config_from_env, llm_request_headers

        cfg = llm_config_from_env(BASE_DIR)
        api_key = (cfg.get("api_key") or "").strip()
        base_url = (cfg.get("base_url") or "").strip().rstrip("/")
        model = (cfg.get("model") or "").strip()
        if not api_key or not base_url or not model:
            raise ValueError("DeepSeek 未配置：请设置 DEEPSEEK_API_KEY，或在 apps/ad/config.local.yaml 配置 deepseek.api_key")

        system = (
            "你是一名资深行业数据分析专家，擅长从指标查询结果中提炼业务含义。"
            "必须只基于用户提供的数据摘要进行解读，不要编造不存在的事实、原因或外部行业数值。"
            "如果样本量不足、缺少时间对比或不能证明因果，要明确说明边界。"
            "如果 validation 中包含 warning 或 failed，必须在风险与边界中说明这些校验提示，不得把异常结果包装成确定结论。"
            "核心结论和关键数据观察中的每条判断必须引用证据编号，例如 [E1]、[V1] 或 [C1]；没有证据不得下结论。"
            "输出中文 Markdown，结构固定为："
            "1. 核心结论；2. 关键数据观察；3. 可能业务含义；4. 风险与边界；5. 建议下一步分析。"
            "每条结论尽量引用具体数值、维度或样本量。"
        )
        user = {
            "question": req.question,
            "queryMode": req.queryMode,
            "matched": req.matched,
            "resultSummary": req.resultSummary,
            "graphContext": req.graphContext,
            "validation": req.validation,
            "evidence": req.evidence,
            "crossValidation": req.crossValidation,
        }
        is_anthropic = "anthropic" in base_url.lower()
        if is_anthropic:
            payload = {
                "model": model,
                "max_tokens": 1600,
                "system": system,
                "messages": [{"role": "user", "content": _compact(user)}],
            }
            endpoint = base_url if base_url.endswith("/messages") else f"{base_url}/messages"
            request = urllib.request.Request(
                endpoint,
                data=json.dumps(payload, ensure_ascii=False).encode("utf-8"),
                headers={
                    "Content-Type": "application/json",
                    "x-api-key": api_key,
                    "anthropic-version": "2023-06-01",
                },
                method="POST",
            )
        else:
            payload = {
                "model": model,
                "messages": [
                    {"role": "system", "content": system},
                    {"role": "user", "content": _compact(user)},
                ],
                "temperature": 0.2,
                "max_tokens": 1200,
            }
            request = urllib.request.Request(
                chat_completions_url(base_url),
                data=json.dumps(payload, ensure_ascii=False).encode("utf-8"),
                headers=llm_request_headers(cfg),
                method="POST",
            )
        try:
            with _urlopen(request, timeout=90) as resp:
                data = json.loads(resp.read().decode("utf-8"))
        except urllib.error.HTTPError as exc:
            body = exc.read().decode("utf-8", errors="replace")[:800]
            raise ValueError(f"DeepSeek 调用失败 HTTP {exc.code}: {body}") from exc
        if "choices" in data:
            content = (
                data.get("choices", [{}])[0]
                .get("message", {})
                .get("content", "")
            )
        elif isinstance(data.get("content"), list):
            content = "\n".join(
                item.get("text", "")
                for item in data["content"]
                if isinstance(item, dict) and item.get("type") == "text" and item.get("text")
            )
        else:
            content = ""
        content = _strip_reasoning(content)
        if not content:
            raise ValueError("LLM 未返回有效解读")
        return content

    try:
        text = await asyncio.to_thread(_call_llm)
        return {"ok": True, "provider": "deepseek", "interpretation": text, "outputValidation": _validate_interpretation_output(text)}
    except Exception as e:
        text = _fallback_interpretation(str(e))
        return {
            "ok": True,
            "provider": "local_fallback",
            "configWarning": str(e),
            "interpretation": text,
            "outputValidation": _validate_interpretation_output(text),
        }


@app.post("/api/nlq/entity-lookup")
async def nlq_entity_lookup(req: EntityLookupRequest):
    """Attribute-value detail lookup with peer analysis from business/source KG."""
    question = (req.question or "").strip()
    if not question:
        return JSONResponse({"ok": False, "error": "question 不能为空"}, status_code=400)
    trace_id = str(uuid.uuid4())
    conversation_id = str(uuid.uuid4())
    started = time.time()
    _feedback_begin_query_trace(
        trace_id, question, conversation_id=conversation_id, source="entity_lookup"
    )

    def _finish(result: dict[str, Any]) -> dict[str, Any]:
        result.setdefault("question", question)
        result.setdefault("queryMode", "entity_lookup")
        result.setdefault("elapsedMs", int((time.time() - started) * 1000))
        result["conversationId"] = conversation_id
        _attach_trace(
            result, trace_id=trace_id, conversation_id=conversation_id, source="entity_lookup"
        )
        return result

    ttl_path = BKG_DIR / "indicator-data.ttl"
    if not ttl_path.exists():
        result = _finish({
            "ok": False,
            "diagnosticCode": "BUSINESS_KG_NOT_FOUND",
            "error": "尚未生成业务图谱，请先在「业务图谱」生成 indicator-data.ttl",
        })
        return JSONResponse(result, status_code=404)

    def _run():
        from kg_builder.nlq import NaturalLanguageQueryService
        service = NaturalLanguageQueryService(
            ttl_path=ttl_path,
            data_agent_url=_DATA_AGENT_URL,
            source_ttl_path=_get_active_path(),
            log_cb=lambda msg: logging.getLogger("uvicorn").info(msg),
        )
        return service.entity_lookup(
            question,
            page_size=max(1, min(req.pageSize, 10000)),
            page_num=max(1, req.pageNum),
        )

    try:
        result = await asyncio.to_thread(_run)
        _finish(result)
        status = 200 if result.get("ok") else 400
        return JSONResponse(result, status_code=status)
    except Exception as e:
        result = _finish({"ok": False, "error": str(e)})
        return JSONResponse(result, status_code=500)


# ── 透视分析 API ────────────────────────────────────────────────────────── #

def _pivot_catalog() -> dict[str, Any]:
    """Read pivot-ready measures and dimensions from the active business KG."""
    from rdflib import Graph, Namespace, RDF

    ttl_path = BKG_DIR / "indicator-data.ttl"
    if not ttl_path.exists():
        raise FileNotFoundError("尚未生成业务图谱，请先生成 indicator-data.ttl")

    graph = Graph()
    graph.parse(str(ttl_path), format="turtle")
    inferred_path = _bkg_inferred_path()
    if inferred_path.exists():
        graph.parse(str(inferred_path), format="turtle")
    ind = Namespace("http://indicator.insightmind.com/ontology#")

    def val(node, prop) -> str:
        value = graph.value(node, prop)
        return str(value) if value is not None else ""

    dimensions = []
    dimension_tables: dict[str, set[str]] = {}
    dimension_uri_by_code: dict[str, Any] = {}
    for node in graph.subjects(RDF.type, ind.Dimension):
        code = val(node, ind.code)
        if not code.startswith("DIM_"):
            continue
        tables = set()
        has_dim_column_expr = False
        for app in graph.objects(node, ind.hasDimApp):
            table = graph.value(app, ind.dimFactTable)
            table_name = val(table, ind.tableName) if table else ""
            if table_name:
                tables.add(table_name)
            if val(app, ind.dimColumnExpr):
                has_dim_column_expr = True
        if not tables:
            continue
        view_type = int(val(node, ind.viewTypeCode) or 0)
        dimension_uri_by_code[code] = node
        dimension_tables[code] = tables
        dimensions.append({
            "code": code,
            "name": val(node, ind.cnName) or code,
            "definition": val(node, ind.definition),
            "viewType": view_type,
            "levelCode": val(node, ind.levelCode),
            "hierarchyCode": val(node, ind.hierarchyCode),
            "isTime": 1 <= view_type <= 6,
            "hasDimColumnExpr": has_dim_column_expr,
            "tables": sorted(tables),
        })

    measures = []
    for node in graph.subjects(RDF.type, ind.Measure):
        code = val(node, ind.code)
        if not code.startswith("MEAS_"):
            continue
        tables = set()
        for app in graph.objects(node, ind.hasMeasureApp):
            table = graph.value(app, ind.appliesToTable) or graph.value(app, ind.measFactTable)
            table_name = val(table, ind.tableName) if table else ""
            if table_name:
                tables.add(table_name)
        if not tables:
            continue
        compatible = []
        dimension_reasons = {}
        for dim_node in graph.objects(node, ind.compatibleDimension):
            dim_code = val(dim_node, ind.code)
            if not dim_code:
                continue
            if dim_code not in compatible:
                compatible.append(dim_code)
            evidence = _bkg_evidence(graph, node, ind.compatibleDimension, dim_node, ind)
            if evidence:
                dimension_reasons[dim_code] = evidence
        if not compatible:
            compatible = [
                dim["code"] for dim in dimensions
                if tables & dimension_tables.get(dim["code"], set())
            ]
        for dim_code in compatible:
            if dim_code not in dimension_reasons:
                dim_tables = sorted(tables & dimension_tables.get(dim_code, set()))
                dimension_reasons[dim_code] = {
                    "ruleId": "compatible_dimension.shared_fact_table",
                    "confidence": "1.0",
                    "evidencePath": f"{code} 与 {dim_code} 共享事实表: {', '.join(dim_tables) if dim_tables else '未知'}",
                }
        measures.append({
            "code": code,
            "name": val(node, ind.cnName) or code,
            "unit": val(node, ind.unit),
            "caliber": val(node, ind.caliber) or val(node, ind.definition),
            "tables": sorted(tables),
            "dimensionCodes": compatible,
            "dimensionReasons": dimension_reasons,
        })

    catalog = {
        "measures": sorted(measures, key=lambda item: item["name"]),
        "dimensions": sorted(dimensions, key=lambda item: (not item["isTime"], item["name"])),
    }
    return _semantic_formula_registry().enrich_catalog(catalog)


def _pivot_da_query(payload: dict[str, Any]) -> dict[str, Any]:
    return _pivot_da_post(_DATA_AGENT_URL, payload)


def _ad_semantic_service(authorization: str = ""):
    from kg_builder.semantic import AdSemanticService

    auth_headers = {"Authorization": authorization} if authorization else {}
    return AdSemanticService(
        catalog=_pivot_catalog(),
        da_query=lambda payload: _pivot_da_post(_DATA_AGENT_URL, payload, auth_headers),
        da_filter_builder=_pivot_da_filters,
    )


def _semantic_formula_registry():
    from kg_builder.semantic import FormulaRegistry

    return FormulaRegistry(FORMULA_REGISTRY_PATH)


def _pivot_da_post(
    url: str,
    payload: dict[str, Any],
    extra_headers: Optional[dict[str, str]] = None,
) -> dict[str, Any]:
    import urllib.error as url_error
    import urllib.request as url_request

    headers = {"Content-Type": "application/json"}
    headers.update(extra_headers or {})
    request = url_request.Request(
        url,
        data=json.dumps(payload, ensure_ascii=False).encode("utf-8"),
        headers=headers,
        method="POST",
    )
    try:
        with _urlopen(request, timeout=120) as response:
            result = json.loads(response.read().decode("utf-8"))
    except url_error.HTTPError as exc:
        detail = exc.read().decode("utf-8", errors="replace")[:1000]
        raise ValueError(f"DA HTTP {exc.code}: {detail}") from exc
    except Exception as exc:
        raise ValueError(f"DA 查询失败: {exc}") from exc
    if result.get("code") != 200:
        raise ValueError(
            result.get("errorMessage") or result.get("message") or "DA 查询失败"
        )
    return result


def _insight_series_loader(query: dict[str, Any], authorization: str = "") -> dict[str, Any]:
    """Run a semantic metric-series query while preserving the DA user context."""
    from kg_builder.semantic import AdSemanticService

    auth_headers = {"Authorization": authorization} if authorization else {}
    service = AdSemanticService(
        catalog=_pivot_catalog(),
        da_query=lambda payload: _pivot_da_post(_DATA_AGENT_URL, payload, auth_headers),
        da_filter_builder=_pivot_da_filters,
    )
    return service.load(query)


def _insight_goal_loader(goal_id: str, authorization: str = "") -> dict[str, Any]:
    """Load one governed DA goal without exposing the caller's token."""
    import urllib.error as url_error
    import urllib.request as url_request

    da_base_url = os.getenv("INSIGHTMIND_DA_BASE_URL", "http://127.0.0.1:8091").rstrip("/")
    headers = {"Accept": "application/json"}
    if authorization:
        headers["Authorization"] = authorization
    request = url_request.Request(
        f"{da_base_url}/goalManagement/detail/{goal_id}",
        headers=headers,
        method="GET",
    )
    try:
        with _urlopen(request, timeout=30) as response:
            result = json.loads(response.read().decode("utf-8"))
    except url_error.HTTPError as exc:
        detail = exc.read().decode("utf-8", errors="replace")[:1000]
        raise ValueError(f"DA 目标查询 HTTP {exc.code}: {detail}") from exc
    if result.get("code") not in (None, 200):
        raise ValueError(result.get("message") or "DA 目标查询失败")
    return result


def _pivot_axis_items(items: Any) -> list[dict[str, str]]:
    result = []
    for item in items if isinstance(items, list) else []:
        if isinstance(item, str):
            code, name = item, item
        elif isinstance(item, dict):
            code = str(item.get("code") or "").strip()
            name = str(item.get("name") or code).strip()
        else:
            continue
        if code and code not in {entry["code"] for entry in result}:
            result.append({"code": code, "name": name})
    return result


def _pivot_resolve_dimensions_for_measures(
    items: list[dict[str, str]],
    measures: list[dict[str, Any]],
    catalog: dict[str, Any],
) -> list[dict[str, Any]]:
    from kg_builder.semantic import AdSemanticService

    service = AdSemanticService(catalog, _pivot_da_query, _pivot_da_filters)
    resolved = []
    for item in items:
        member = service._resolve_dimension_member(item["code"], measures)
        if not member:
            raise ValueError(f"业务图谱中不存在维度 {item['code']}")
        resolved.append({
            "code": str(member.get("code") or item["code"]),
            "name": item.get("name") or member.get("name") or item["code"],
            "viewType": member.get("viewType") or 0,
            "isTime": bool(member.get("isTime")),
            "tables": member.get("tables") or [],
            "sourceCodes": member.get("sourceCodes") or [member.get("code") or item["code"]],
        })
    return resolved


def _pivot_dimension_compatible_with_measure(
    dimension: dict[str, Any],
    measure: dict[str, Any],
    catalog: dict[str, Any],
) -> bool:
    from kg_builder.semantic import AdSemanticService

    service = AdSemanticService(catalog, _pivot_da_query, _pivot_da_filters)
    return service._dimension_compatible_with_measure(dimension, measure)


def _pivot_resolve_filters_for_measures(
    filters: Any,
    measures: list[dict[str, Any]],
    catalog: dict[str, Any],
) -> list[dict[str, Any]]:
    if not isinstance(filters, list):
        return []
    from kg_builder.semantic import AdSemanticService

    service = AdSemanticService(catalog, _pivot_da_query, _pivot_da_filters)
    resolved = []
    for item in filters:
        if not isinstance(item, dict):
            continue
        next_item = dict(item)
        code = str(next_item.get("code") or next_item.get("member") or "").strip()
        if code.startswith("DIM_") or code.startswith(f"{service.model_name}."):
            member = service._resolve_dimension_member(code, measures)
            if member:
                next_item["code"] = member["code"]
                next_item["viewType"] = member.get("viewType") or next_item.get("viewType") or 0
                next_item["filterMode"] = "time" if member.get("isTime") else next_item.get("filterMode") or "enum"
        resolved.append(next_item)
    return resolved


def _pivot_week_filter_value(value: Any) -> str:
    import re

    raw = str(value or "").strip()
    match = re.match(r"^(\d{4})(?:-?W?|第)?(\d{1,2})(?:周)?$", raw, re.IGNORECASE)
    if not match:
        return raw
    return f"{match.group(1)}{int(match.group(2)):02d}"


def _pivot_da_filters(filters: Any) -> list[dict[str, Any]]:
    sql_types = {
        "in": 0,
        "not_in": 1,
        "between": 2,
        "greater_than": 3,
        "less_than": 4,
        "greater_than_or_equal": 5,
        "less_than_or_equal": 6,
        "equal": 7,
        "not_equal": 8,
        "like": 9,
        "not_like": 10,
    }
    da_filters = []
    for item in filters if isinstance(filters, list) else []:
        if not isinstance(item, dict):
            continue
        code = str(item.get("code") or "").strip()
        view_type = int(item.get("viewType") or 0)
        is_week_filter = view_type == 2 or str(item.get("filterMode") or "").lower() == "week"
        is_time_filter = 1 <= view_type <= 6 or str(item.get("filterMode") or "").lower() in {"date", "week", "time"}
        values = item.get("values")
        values = values if isinstance(values, list) else [values]
        values = [str(value) for value in values if value not in (None, "")]
        if is_week_filter:
            values = [_pivot_week_filter_value(value) for value in values]
        if not code or not values:
            continue
        operator = str(item.get("operator") or "in").lower()
        sql_type = sql_types.get(operator, 0)
        if sql_type == 2 and len(values) < 2:
            continue
        operator_item: dict[str, Any] = {
            "sqlOprType": sql_type,
            "dataList": values,
            "sqlLogicalType": 0,
            "timeRange": 0,
        }
        if sql_type == 2:
            if not is_time_filter:
                operator_item["begin"] = values[0]
                operator_item["end"] = values[1]
            if not code.startswith("MEAS_"):
                operator_item["timeRange"] = 1
        elif is_time_filter and not code.startswith("MEAS_"):
            operator_item["timeRange"] = 1
        da_filters.append({
            "code": code,
            "operatorList": [operator_item],
            "internal": True,
        })
    return da_filters


def _pivot_query_formula_measures(
    measures: list[dict[str, str]],
    measure_metas: list[dict[str, Any]],
    rows: list[dict[str, Any]],
    columns: list[dict[str, Any]],
    filters: list[dict[str, Any]],
    catalog: dict[str, Any],
    limit: int,
) -> dict[str, Any]:
    """Run formula measures through the semantic layer before shaping a pivot.

    Formula measures live in AD's semantic registry and therefore cannot be
    sent directly to DA.  The semantic layer expands their dependencies,
    executes the base query, and evaluates the formula for each returned row.
    """
    from kg_builder.semantic import AdSemanticService

    service = AdSemanticService(catalog, _pivot_da_query, _pivot_da_filters)
    selected_dims = rows + columns
    measure_member_names = {
        meta["code"]: service._code_to_member_name(meta["code"])
        for meta in measure_metas
    }
    dimension_member_names = {
        dim["code"]: service._code_to_member_name(dim["code"])
        for dim in selected_dims
    }
    semantic_result = service.load({
        "measures": [measure_member_names[item["code"]] for item in measures],
        "dimensions": [dimension_member_names[dim["code"]] for dim in selected_dims],
        # Pivot filters have already been validated and resolved above; passing
        # their DA representation avoids losing filter IDs while evaluating a
        # formula measure.
        "_adFiltersForDa": _pivot_da_filters(filters),
        "limit": limit,
    })

    row_headers: dict[str, dict[str, Any]] = {}
    column_headers: dict[str, dict[str, Any]] = {}
    cells = []
    for row in semantic_result.get("data") or []:
        filter_values = row.get("__filterValues") or {}
        values = {
            code: {
                "value": row.get(member_name),
                "filterValue": filter_values.get(member_name, row.get(member_name)),
                "viewType": next((dim.get("viewType") or 0 for dim in selected_dims if dim["code"] == code), 0),
            }
            for code, member_name in dimension_member_names.items()
        }
        row_path = _pivot_path(rows, values)
        column_path = _pivot_path(columns, values)
        row_key = _pivot_key(row_path)
        column_key = _pivot_key(column_path)
        row_headers.setdefault(row_key, {"key": row_key, "path": row_path})
        column_headers.setdefault(column_key, {"key": column_key, "path": column_path})
        for measure in measures:
            code = measure["code"]
            meta = next(item for item in measure_metas if item["code"] == code)
            cells.append({
                "rowKey": row_key,
                "columnKey": column_key,
                "measureCode": code,
                "measureName": meta.get("name") or code,
                "value": row.get(measure_member_names[code]),
                "rowPath": row_path,
                "columnPath": column_path,
            })
    if not column_headers:
        column_headers[_pivot_key([])] = {"key": _pivot_key([]), "path": []}
    if not row_headers:
        row_headers[_pivot_key([])] = {"key": _pivot_key([]), "path": []}
    diagnostics = semantic_result.get("diagnostics") or {}
    return {
        "rows": list(row_headers.values()),
        "columns": list(column_headers.values()),
        "measures": [{**item, **next(meta for meta in measure_metas if meta["code"] == item["code"])} for item in measures],
        "cells": cells,
        "filters": filters,
        "diagnostics": {
            "elapsedMs": diagnostics.get("elapsedMs"),
            "rowCount": diagnostics.get("rowCount", len(cells)),
            "reviewSql": diagnostics.get("reviewSql") or "",
        },
    }


# ── AD Semantic API (Cube-like facade over DA) ───────────────────────────── #

@app.get("/api/ad/v1/formulas")
async def ad_semantic_formulas():
    try:
        return {"formulas": _semantic_formula_registry().list()}
    except Exception as exc:
        return JSONResponse({"error": str(exc)}, status_code=500)


@app.post("/api/ad/v1/formulas")
async def ad_semantic_formula_save(request: Request):
    try:
        body = await request.json()
        # Validate against the current physical KG catalog, before formulas are
        # merged back into the public semantic catalog.
        from kg_builder.semantic import FormulaValidationError

        catalog = {
            "measures": [item for item in _pivot_catalog().get("measures", []) if not item.get("formula")],
            "dimensions": _pivot_catalog().get("dimensions", []),
        }
        formula = _semantic_formula_registry().save(body, catalog)
        return formula
    except FormulaValidationError as exc:
        return JSONResponse({"error": str(exc)}, status_code=400)
    except FileNotFoundError as exc:
        return JSONResponse({"error": str(exc)}, status_code=404)
    except Exception as exc:
        return JSONResponse({"error": str(exc)}, status_code=500)


@app.delete("/api/ad/v1/formulas/{code}")
async def ad_semantic_formula_delete(code: str):
    try:
        return {"ok": _semantic_formula_registry().delete(code)}
    except Exception as exc:
        return JSONResponse({"error": str(exc)}, status_code=500)


@app.get("/api/ad/v1/meta")
async def ad_semantic_meta():
    try:
        return _ad_semantic_service().meta()
    except FileNotFoundError as exc:
        return JSONResponse({"error": str(exc)}, status_code=404)
    except Exception as exc:
        return JSONResponse({"error": str(exc)}, status_code=500)


def _semantic_simple_key(value: Any) -> str:
    text = str(value or "").strip()
    if "." in text:
        text = text.rsplit(".", 1)[-1]
    if text.upper().startswith("MEAS_"):
        text = text[5:]
    elif text.upper().startswith("DIM_"):
        text = text[4:]
    return text.lower()


def _semantic_member_aliases(item: dict[str, Any]) -> set[str]:
    aliases = {
        str(item.get("name") or ""),
        str(item.get("code") or ""),
        _semantic_simple_key(item.get("name")),
        _semantic_simple_key(item.get("code")),
    }
    for code in item.get("sourceCodes") or []:
        aliases.add(str(code))
        aliases.add(_semantic_simple_key(code))
        aliases.add(f"ad.{_semantic_simple_key(code)}")
    return {alias for alias in aliases if alias}


def _semantic_find_member(items: list[dict[str, Any]], member: Any) -> dict[str, Any] | None:
    raw = str(member or "").strip()
    simple = _semantic_simple_key(raw)
    for item in items:
        aliases = _semantic_member_aliases(item)
        if raw in aliases or simple in aliases:
            return item
    return None


def _semantic_dim_codes(dim: dict[str, Any]) -> set[str]:
    return {str(code) for code in [dim.get("code"), *(dim.get("sourceCodes") or [])] if code}


def _semantic_member_tables(item: dict[str, Any]) -> set[str]:
    return {str(table) for table in (item.get("tables") or []) if table}


def _semantic_measure_supports_dimension(
    measure: dict[str, Any],
    dim: dict[str, Any],
    allowed_codes: set[str] | None = None,
) -> bool:
    dim_codes = _semantic_dim_codes(dim)
    if allowed_codes is not None:
        return bool(dim_codes & allowed_codes)
    measure_codes = {str(code) for code in (measure.get("dimensionCodes") or []) if code}
    if dim_codes & measure_codes:
        return True
    # Some report dimensions are degenerate/derived columns that are queryable
    # through the same fact table even when the DA reasoning cache has not
    # materialized them as compatible dimensions yet.
    return bool(_semantic_member_tables(measure) & _semantic_member_tables(dim))


def _semantic_measure_supports_dimensions(
    measure: dict[str, Any],
    dims: list[dict[str, Any]],
    allowed_codes: set[str] | None = None,
) -> tuple[bool, list[str]]:
    bad = []
    for dim in dims:
        if not _semantic_measure_supports_dimension(measure, dim, allowed_codes):
            bad.append(dim.get("title") or dim.get("name") or dim.get("code") or "")
    return (not bad, bad)


def _semantic_dimension_reason(measure: dict[str, Any], dim: dict[str, Any]) -> dict[str, Any]:
    reasons = measure.get("dimensionReasons") or {}
    for code in [dim.get("code"), *(dim.get("sourceCodes") or [])]:
        if code and code in reasons:
            return reasons.get(code) or {}
    return {}


def _da_graph_base_url() -> str:
    base = os.getenv("DA_BASE_URL")
    if base:
        return base.rstrip("/")
    return _DATA_AGENT_URL.split("/bi/v1/datasource/query", 1)[0].rstrip("/")


def _da_compatible_dimension_relations(measure_code: str) -> dict[str, dict[str, Any]]:
    if not measure_code:
        return {}
    import urllib.error as _uerr
    import urllib.parse as _uparse
    import urllib.request as _ureq

    url = (
        f"{_da_graph_base_url()}/api/graph/reasoning/measure/"
        f"{_uparse.quote(str(measure_code), safe='')}/compatible-dimensions"
    )
    try:
        with _urlopen(url, timeout=4) as resp:
            payload = json.loads(resp.read().decode("utf-8"))
    except (_uerr.URLError, TimeoutError, json.JSONDecodeError, OSError) as exc:
        raise RuntimeError("DA 业务图谱推理服务不可用") from exc
    rows = payload.get("data") if isinstance(payload, dict) else None
    if not isinstance(rows, list):
        raise RuntimeError("DA 业务图谱推理服务返回了无效数据")
    relations: dict[str, dict[str, Any]] = {}
    for row in rows:
        if not isinstance(row, dict):
            continue
        code = str(row.get("targetCode") or "").strip()
        if code:
            relations[code] = row
    return relations


def _semantic_relation_for_dimension(
    relations: dict[str, dict[str, Any]],
    dim: dict[str, Any],
) -> dict[str, Any] | None:
    for code in [dim.get("code"), *(dim.get("sourceCodes") or [])]:
        if code and str(code) in relations:
            return relations[str(code)]
    return None


def _ad_drill_dimension_candidates(body: dict[str, Any]) -> dict[str, Any]:
    meta = _ad_semantic_service().meta()
    model = (meta.get("models") or [{}])[0]
    measures = model.get("measures") or []
    dimensions = (model.get("timeDimensions") or []) + (model.get("dimensions") or [])
    measure = _semantic_find_member(measures, body.get("measure") or body.get("measureCode"))
    if not measure:
        raise ValueError("缺少或无法识别 measure")

    filter_members = [
        item.get("member")
        for item in (body.get("filters") or [])
        if isinstance(item, dict) and (not item.get("kind") or item.get("kind") == "dimension") and item.get("member")
    ]
    context_members = [
        body.get("currentMember"),
        *(body.get("contextMembers") or []),
        *(body.get("selectedDimensions") or []),
    ]
    base_dims = []
    seen_base = set()
    for member in [*filter_members, *context_members]:
        dim = _semantic_find_member(dimensions, member)
        if not dim:
            continue
        key = dim.get("name") or dim.get("code")
        if key and key not in seen_base:
            seen_base.add(key)
            base_dims.append(dim)

    da_relations = _da_compatible_dimension_relations(str(measure.get("code") or ""))
    allowed_codes = set(da_relations.keys())
    result_source = "da_graph"

    supported_base, bad_base = _semantic_measure_supports_dimensions(measure, base_dims, allowed_codes)
    if not supported_base:
        return {
            "measure": measure,
            "items": [],
            "source": result_source,
            "error": f"当前指标与上下文维度不兼容：{'、'.join(bad_base)}",
        }

    preferred = {
        "sales_expert": (96, "按销售专家定位辅导对象和通话质量差异"),
        "intent": (94, "按客户意图拆解沟通场景，定位话术和槽位问题来源"),
        "quality_issue_category": (92, "按问题分类聚合异常，适合安排复盘动作"),
        "quality_score_level": (88, "按质量分层观察优秀、良好、合格和未通过结构"),
        "quality_rule": (86, "按质检规则定位缺失槽位和规则命中来源"),
        "call_flow_total": (84, "按电话流向总入口拆解通话来源路径"),
        "quality_pass": (82, "按通过/未通过区分质量达标情况"),
        "store_city": (76, "按城市对比区域门店质量表现"),
        "store_manager": (74, "按店长视角对比管理责任单元"),
        "store": (72, "按门店定位经营单元差异"),
        "date_day": (58, "按日期观察指标短期波动"),
    }
    excluded = {_semantic_simple_key(dim.get("name") or dim.get("code")) for dim in base_dims}
    rows = []
    for dim in dimensions:
        key = _semantic_simple_key(dim.get("name") or dim.get("code"))
        if key in excluded:
            continue
        if not _semantic_measure_supports_dimension(measure, dim, allowed_codes):
            continue
        supported, _bad = _semantic_measure_supports_dimensions(measure, [*base_dims, dim], allowed_codes)
        if not supported:
            continue
        score, default_reason = preferred.get(key, (70, "业务图谱中该维度与当前指标共享可查询关系"))
        reason = _semantic_dimension_reason(measure, dim)
        da_reason = _semantic_relation_for_dimension(da_relations, dim)
        rows.append({
            "member": dim.get("name"),
            "code": dim.get("code"),
            "title": dim.get("title") or dim.get("shortTitle") or dim.get("name") or dim.get("code"),
            "sourceCodes": dim.get("sourceCodes") or [dim.get("code")],
            "score": score,
            "reason": default_reason,
            "evidencePath": (da_reason or {}).get("evidencePath") or reason.get("evidencePath") or "",
            "ruleId": (da_reason or {}).get("ruleId") or reason.get("ruleId") or "business_kg.compatible_dimension",
            "confidence": (da_reason or {}).get("confidence") or reason.get("confidence") or "",
            "tables": sorted(set(measure.get("tables") or []) & set(dim.get("tables") or [])),
            "source": result_source,
        })
    rows.sort(key=lambda item: (-int(item.get("score") or 0), str(item.get("title") or "")))
    # A recommendation percentage is an ordering signal, not a categorical label.
    # Default graph-compatible dimensions may start with the same base score; make
    # their displayed ranks deterministic and distinct so the UI never presents a
    # misleading wall of identical recommendations.
    used_scores: set[int] = set()
    for row in rows:
        score = max(1, min(99, int(row.get("score") or 70)))
        while score in used_scores and score > 1:
            score -= 1
        if score in used_scores:
            while score in used_scores and score < 99:
                score += 1
        row["score"] = score
        used_scores.add(score)
    return {
        "measure": measure,
        "items": rows[: int(body.get("limit") or 10)],
        "source": result_source,
    }


@app.post("/api/ad/v1/drill-dimensions")
async def ad_semantic_drill_dimensions(request: Request):
    try:
        body = await request.json()
        return _ad_drill_dimension_candidates(body)
    except FileNotFoundError as exc:
        return JSONResponse({"error": str(exc)}, status_code=404)
    except ValueError as exc:
        return JSONResponse({"error": str(exc)}, status_code=400)
    except Exception as exc:
        return JSONResponse({"error": str(exc)}, status_code=500)


@app.post("/api/ad/v1/load")
async def ad_semantic_load(request: Request):
    try:
        body = await request.json()
        service = _ad_semantic_service(str(request.headers.get("authorization") or ""))
        result = await asyncio.to_thread(service.load, body)
        if body.get("enableAlerts", True) is not False:
            from kg_builder.alerts import annotate_semantic_result

            result = await asyncio.to_thread(
                annotate_semantic_result,
                result,
                body,
                _pivot_catalog(),
                BKG_DIR / "indicator-data.ttl",
                service.load,
            )
        return result
    except FileNotFoundError as exc:
        return JSONResponse({"error": str(exc)}, status_code=404)
    except ValueError as exc:
        return JSONResponse({"error": str(exc)}, status_code=400)
    except Exception as exc:
        return JSONResponse({"error": str(exc)}, status_code=500)


@app.post("/api/ad/v1/sql")
async def ad_semantic_sql(request: Request):
    try:
        body = await request.json()
        service = _ad_semantic_service(str(request.headers.get("authorization") or ""))
        payload = service.translate_query(dict(body))
        result: dict[str, Any] = {"daPayload": payload}
        if body.get("execute"):
            da_result = await asyncio.to_thread(_pivot_da_query, {**payload, "pageSize": 1, "pageNum": 1})
            da_data = da_result.get("data") or {}
            result["reviewSql"] = da_data.get("reviewSql") or ""
            result["diagnostics"] = {"elapsedMs": da_data.get("cost")}
        return result
    except FileNotFoundError as exc:
        return JSONResponse({"error": str(exc)}, status_code=404)
    except ValueError as exc:
        return JSONResponse({"error": str(exc)}, status_code=400)
    except Exception as exc:
        return JSONResponse({"error": str(exc)}, status_code=500)


@app.post("/api/ad/v1/chart")
async def ad_semantic_chart(request: Request):
    try:
        body = await request.json()
        service = _ad_semantic_service(str(request.headers.get("authorization") or ""))
        result = await asyncio.to_thread(service.chart, body)
        return result
    except FileNotFoundError as exc:
        return JSONResponse({"error": str(exc)}, status_code=404)
    except ValueError as exc:
        return JSONResponse({"error": str(exc)}, status_code=400)
    except Exception as exc:
        return JSONResponse({"error": str(exc)}, status_code=500)


@app.post("/api/ad/v1/drilldown")
async def ad_semantic_drilldown(request: Request):
    body = await request.json()
    try:
        service = _ad_semantic_service(str(request.headers.get("authorization") or ""))
        measure_member = body.get("measure") or body.get("measureCode")
        measure = service._resolve_member(measure_member, "measure")
        if not measure:
            return JSONResponse({"error": "缺少或无法识别 measure"}, status_code=400)

        measure_context = [measure]
        filters = service._convert_filters(body.get("filters") or [], measure_context)
        for key, value in (body.get("members") or {}).items():
            member = service._resolve_dimension_member(key, measure_context)
            if member and value not in (None, ""):
                filters.append({
                    "code": member["code"],
                    "operator": "in",
                    "values": [str(value)],
                    "viewType": member.get("viewType") or 0,
                    "filterMode": "time" if member.get("isTime") else "enum",
                })

        payload = {
            "chartType": 0,
            "sourceType": 0,
            "operaType": 1,
            "cacheStrategy": body.get("cacheStrategy", 1),
            "configureList": [{"code": measure["code"]}],
            "filterList": _pivot_da_filters(filters),
            "measureDetail": True,
            "pageNo": max(1, int(body.get("pageNum") or body.get("pageNo") or 1)),
            "pageSize": max(1, min(int(body.get("pageSize") or 50), 500)),
        }
        da_result = await asyncio.to_thread(_pivot_da_query, payload)
        da_data = da_result.get("data") or {}
        raw_rows = da_data.get("cellList") or []
        columns = []
        records = []
        column_labels = await asyncio.to_thread(_pivot_detail_column_labels)
        if raw_rows and isinstance(raw_rows[0], list):
            header = raw_rows[0]
            if all(isinstance(cell, dict) and str(cell.get("code") or "") == str(cell.get("data") or "") for cell in header):
                columns = [
                    {
                        "code": str(cell.get("code") or ""),
                        "name": column_labels.get(str(cell.get("code") or ""), str(cell.get("name") or cell.get("code") or "")),
                    }
                    for cell in header if isinstance(cell, dict)
                ]
                data_rows = raw_rows[1:]
            else:
                columns = [{"code": f"col_{idx}", "name": f"col_{idx}"} for idx in range(len(header))]
                data_rows = raw_rows
            for row in data_rows:
                records.append({
                    columns[idx]["code"]: cell.get("data")
                    for idx, cell in enumerate(row)
                    if idx < len(columns) and isinstance(cell, dict)
                })
        page_no = payload["pageNo"]
        page_size = payload["pageSize"]
        return {
            "columns": columns,
            "records": records,
            "pageInfo": _normalize_detail_page_info(
                da_data.get("pageInfo") or {},
                len(records),
                page_size,
                page_no,
            ),
            "daPayload": payload,
            "diagnostics": {"reviewSql": da_data.get("reviewSql") or "", "elapsedMs": da_data.get("cost")},
        }
    except FileNotFoundError as exc:
        return JSONResponse({"error": str(exc)}, status_code=404)
    except ValueError as exc:
        return JSONResponse({"error": str(exc)}, status_code=400)
    except Exception as exc:
        return JSONResponse({"error": str(exc)}, status_code=500)


def _da_tms_engine():
    from sqlalchemy import create_engine
    from sqlalchemy.engine import URL

    url = URL.create(
        "mysql+pymysql",
        username=os.getenv("DA_TMS_MYSQL_USER", "root"),
        password=os.getenv("DA_TMS_MYSQL_PASSWORD", "root"),
        host=os.getenv("DA_TMS_MYSQL_HOST", "127.0.0.1"),
        port=int(os.getenv("DA_TMS_MYSQL_PORT", "3306")),
        database=os.getenv("DA_TMS_MYSQL_DATABASE", "da_tms"),
        query={"charset": "utf8mb4"},
    )
    return create_engine(url, pool_pre_ping=True, pool_recycle=1800)


@functools.lru_cache(maxsize=1)
def _cached_da_tms_engine():
    return _da_tms_engine()


def _da_call_sop_records(payload: dict[str, Any]) -> list[dict[str, Any]]:
    """Read call-SOP source rows through the DA service boundary."""
    filters = list(payload.get("filters") or [])
    members = {
        str(item.get("member") or item.get("code") or "")
        for item in filters
        if isinstance(item, dict)
    }
    if payload.get("date") and "ad.date_day" not in members:
        filters.append({"member": "ad.date_day", "operator": "in", "values": [str(payload["date"])]})
    if payload.get("store") and "ad.store" not in members:
        filters.append({"member": "ad.store", "operator": "equals", "values": [str(payload["store"])]})
    url = f"{_da_graph_base_url()}/indicator/api/v1/call-sop/records"
    result = _pivot_da_post(url, {"filters": filters})
    data = result.get("data") if isinstance(result, dict) else None
    rows = data.get("rows") if isinstance(data, dict) else None
    if not isinstance(rows, list):
        raise ValueError("DA 通话 SOP 查询未返回 rows")
    return [dict(row) for row in rows if isinstance(row, dict)]


def _call_sop_filter_value(payload: dict[str, Any], member: str, fallback: str) -> str:
    for item in payload.get("filters") or []:
        if not isinstance(item, dict):
            continue
        if str(item.get("member") or item.get("code") or "") != member:
            continue
        values = item.get("values")
        if isinstance(values, list) and values:
            if member == "ad.date_day" and len(values) >= 2:
                return f"{values[0]} ~ {values[1]}"
            return str(values[0])
        if values not in (None, ""):
            return str(values)
    direct_key = "date" if member == "ad.date_day" else "store"
    return str(payload.get(direct_key) or fallback)


_CALL_SOP_RECORD_FILTER_COLUMNS = {
    "ad.date_day": "f.activity_date",
    "ad.store": "f.store_name",
    "ad.store_city": "f.store_city",
    "ad.store_manager": "f.manager_name",
    "ad.sales_expert": "f.expert_name",
    "ad.intent": "COALESCE(j.intent_name, f.intent_name)",
    "ad.quality_score_level": "f.quality_score_level",
    "ad.quality_issue_category": "f.issue_category",
    "ad.quality_pass": "f.quality_pass_label",
    "ad.call_flow_total": "f.call_flow_total",
    "ad.quality_rule": "COALESCE(r.sop_category_name, r.sop_category_code, f.rule_id)",
}


_CALL_SOP_EXPERT_NAME_STOPWORDS = {
    "理想汽车", "产品专家", "销售顾问", "销售专家", "门店顾问", "线上客服",
    "先生", "女士", "老师", "姐姐", "哥哥", "哥", "姐", "客户", "专家", "小鹏", "小米",
    "房子", "小伙儿", "满意", "谢谢", "辛苦", "那边", "妹妹", "好好",
    "问界", "智界", "领克", "特斯拉", "宝马", "奥迪",
}
_CALL_SOP_COMMON_SURNAMES = set(
    "赵钱孙李周吴郑王冯陈褚卫蒋沈韩杨朱秦尤许何吕施张孔曹严华金魏陶姜"
    "戚谢邹喻柏水窦章云苏潘葛奚范彭郎鲁韦昌马苗凤花方俞任袁柳鲍史唐"
    "费廉岑薛雷贺倪汤滕殷罗毕郝邬安常乐于时傅皮卞齐康伍余元卜顾孟平"
    "黄和穆萧尹姚邵湛汪祁毛禹狄米贝明臧计伏成戴谈宋茅庞熊纪舒屈项祝"
    "董梁杜阮蓝闵席季麻强贾路娄危江童颜郭梅盛林刁钟徐邱骆高夏蔡田樊"
    "胡凌霍虞万支柯管卢莫经房裘缪干解应宗丁宣邓郁单杭洪包诸左石崔吉"
    "钮龚程嵇邢滑裴陆荣翁荀羊甄曲封芮储靳汲邴糜松井段富巫乌焦巴弓牧隗"
    "山谷车侯宓蓬全郗班仰秋仲伊宫宁仇栾暴甘钭厉戎祖武符刘景詹束龙叶幸"
    "司韶黎乔苍双闻莘党翟谭贡劳逄姬申扶堵冉宰郦雍郤璩桑桂濮牛寿通边扈"
    "燕冀郏浦尚农温别庄晏柴瞿阎充慕连茹习艾鱼容向古易慎戈廖庾终暨居衡"
    "步都耿满弘匡国文寇广禄阙东欧殳沃利蔚越夔隆师巩厍聂晁勾敖融冷訾辛"
    "阚那简饶空曾毋沙乜养鞠须丰巢关蒯相查后荆红游竺权逯盖益桓公"
)


def _call_sop_source_expert_name(row: dict[str, Any]) -> str:
    """Extract a source-backed display name without changing the drill filter key.

    The imported call data only carries ``specialist_id`` as a structured field;
    real names are present in explicit self-introductions in the ASR text.
    Keep this deliberately conservative so the UI
    never turns ordinary dialogue into a fabricated employee name.
    """

    raw_name = str(row.get("expert_name") or "").strip()
    if raw_name and not re.fullmatch(r"\d+", raw_name):
        return raw_name

    text = str(row.get("aggregated_content") or "")
    if not text:
        return ""

    # Only inspect expert utterances.  Customer dialogue often contains phrases
    # such as “我是……” and must never be promoted to the employee label.
    expert_segments = []
    for segment in text.split("｜"):
        normalized = re.sub(r"^\[时间\s*[:：][^\]]+\]\s*", "", segment.strip())
        if normalized.startswith(("专家:", "专家：")):
            expert_segments.append(re.sub(r"^专家[:：]", "", normalized, count=1))
    expert_text = "｜".join(expert_segments)
    if not expert_text:
        return ""

    def clean(value: Any) -> str:
        candidate = re.sub(r"[^\u4e00-\u9fff]", "", str(value or ""))
        candidate = re.sub(r"^(?:那个|这个|阿)", "", candidate)
        candidate = re.sub(r"[的有是哎呀呢哈啊了吧嘛]+$", "", candidate)
        candidate = re.sub(r"(?:老师|经理|顾问|销售|专家|姐姐|哥哥|哥|姐|总)+$", "", candidate)
        if not 2 <= len(candidate) <= 4:
            return ""
        if candidate in _CALL_SOP_EXPERT_NAME_STOPWORDS:
            return ""
        if any(word in candidate for word in (
            "理想", "汽车", "专家", "销售", "门店", "客服", "小鹏", "小米", "问界", "智界", "领克", "宝马", "奥迪",
        )):
            return ""
        if candidate.startswith("小"):
            return candidate
        if len(candidate) == 2 and candidate[0] == candidate[1]:
            return candidate
        return candidate if candidate[0] in _CALL_SOP_COMMON_SURNAMES else ""

    patterns = (
        r"我姓([\u4e00-\u9fff])(?:[，,\s]*我)?([\u4e00-\u9fff]{2,3})",
        r"我是(?:那个)?理想汽车(?:的|那个)?([\u4e00-\u9fff]{2,4})(?=[，。！？｜]|看您|前一段|$)",
        r"我是理想汽车(?:产品专家|销售顾问|销售专家|顾问|销售|产品)[，,]?阿?([\u4e00-\u9fff]{2,4})(?=看|，|。|！|？|｜|$)",
        r"我是([小][\u4e00-\u9fff]{1,3})(?=[，。！？｜]|$)",
        r"我是([\u4e00-\u9fff]{2,4})[，,]理想汽车",
        r"我这边[^｜]{0,24}?理想汽车(?:的)?(?:那个)?(?:小王)?([\u4e00-\u9fff]{2,4})(?=[，。！？｜]|$)",
        r"我(?:这边)?(?:那个)?理想(?:汽车)?的(?:那个)?([小][\u4e00-\u9fff]{1,3})(?:哥|姐)?(?=[，。！？｜]|$)",
        r"我叫([\u4e00-\u9fff]{2,4})(?=[，。！？｜]|$)",
    )
    for pattern in patterns:
        match = re.search(pattern, expert_text)
        if not match:
            continue
        # “我姓丁，我丁帅” uses the complete second capture.
        value = match.group(2) if match.lastindex and match.lastindex >= 2 else match.group(1)
        candidate = clean(value)
        if candidate:
            return candidate

    return ""


def _call_sop_record_filter_conditions(payload: dict[str, Any]) -> tuple[list[str], dict[str, Any], bool]:
    """Build the same supported filter scope for diagnosis and workbench data."""
    where: list[str] = []
    params: dict[str, Any] = {}
    uses_rule = False
    for idx, item in enumerate(payload.get("filters") or []):
        if not isinstance(item, dict):
            continue
        member = str(item.get("member") or item.get("code") or "")
        column = _CALL_SOP_RECORD_FILTER_COLUMNS.get(member)
        if not column:
            continue
        uses_rule = uses_rule or member == "ad.quality_rule"
        values = item.get("values")
        if values is None and item.get("value") is not None:
            values = [item.get("value")]
        if not isinstance(values, list):
            values = [values]
        clean = [str(value) for value in values if value not in (None, "")]
        if not clean:
            continue
        operator = str(item.get("operator") or "in").lower()
        if operator in {"not_equals", "neq", "!="}:
            key = f"scope_{idx}_0"
            params[key] = clean[0]
            where.append(f"{column} <> :{key}")
        elif operator == "contains":
            key = f"scope_{idx}_0"
            params[key] = f"%{clean[0]}%"
            where.append(f"{column} LIKE :{key}")
        else:
            keys = []
            for pos, value in enumerate(clean):
                key = f"scope_{idx}_{pos}"
                params[key] = value
                keys.append(f":{key}")
            where.append(f"{column} IN ({', '.join(keys)})")

    seen = {
        str(item.get("member") or item.get("code") or "")
        for item in payload.get("filters") or []
        if isinstance(item, dict)
    }
    if "ad.date_day" not in seen:
        params["default_day"] = _call_sop_filter_value(payload, "ad.date_day", "2026-07-02")
        where.append("f.activity_date = :default_day")
    if "ad.store" not in seen:
        params["default_store"] = _call_sop_filter_value(payload, "ad.store", "理想汽车杭州演示体验中心")
        where.append("f.store_name = :default_store")
    return where, params, uses_rule


def _call_sop_json_load(value: Any) -> Any:
    if value is None:
        return None
    if isinstance(value, (dict, list)):
        return value
    try:
        return json.loads(str(value))
    except Exception:
        return None


def _call_sop_empty_category(item: dict[str, Any]) -> dict[str, Any]:
    checkpoints = item.get("checkpoints") or []
    return {
        "code": item.get("code"),
        "name": item.get("name"),
        "description": item.get("description"),
        "total": 0,
        "high": 0,
        "standard": 0,
        "basic": 0,
        "miss": 0,
        "hitCheckpointCount": 0,
        "totalCheckpointCount": 0,
        "coverageRate": 0,
        "checks": [
            {
                "code": checkpoint.get("code"),
                "name": checkpoint.get("name"),
                "description": checkpoint.get("description"),
                "total": 0,
                "hit": 0,
                "rate": 0,
                "evidence": "",
            }
            for checkpoint in checkpoints
        ],
    }


def _call_sop_add_category(target: dict[str, Any], category: dict[str, Any]) -> None:
    level = str(category.get("level") or "miss")
    if level not in {"high", "standard", "basic", "miss"}:
        level = "miss"
    target["total"] += 1
    target[level] += 1
    target["hitCheckpointCount"] += int(category.get("hit_count") or 0)
    target["totalCheckpointCount"] += int(category.get("total_count") or 0)
    check_map = {item.get("code"): item for item in target.get("checks") or []}
    for checkpoint in category.get("checkpoints") or []:
        check_stat = check_map.get(checkpoint.get("code"))
        if not check_stat:
            continue
        check_stat["total"] += 1
        if checkpoint.get("hit"):
            check_stat["hit"] += 1
            if not check_stat.get("evidence"):
                check_stat["evidence"] = str(checkpoint.get("evidence") or "")[:120]


def _call_sop_finalize_category(item: dict[str, Any]) -> dict[str, Any]:
    total = int(item.get("total") or 0)
    hit = int(item.get("hitCheckpointCount") or 0)
    check_total = int(item.get("totalCheckpointCount") or 0)
    item["achieved"] = int(item.get("high") or 0) + int(item.get("standard") or 0) + int(item.get("basic") or 0)
    item["achievedRate"] = (hit / check_total * 100) if check_total else 0
    item["coverageRate"] = hit / check_total if check_total else 0
    item["miss"] = total - item["achieved"] if total else int(item.get("miss") or 0)
    for checkpoint in item.get("checks") or []:
        c_total = int(checkpoint.get("total") or 0)
        c_hit = int(checkpoint.get("hit") or 0)
        checkpoint["rate"] = c_hit / c_total * 100 if c_total else 0
    return item


def _call_sop_diagnosis(payload: dict[str, Any]) -> dict[str, Any]:
    from kg_builder.call_sop import (
        SOP_VERSION,
        analyze_call_sop_record,
        catalog_payload,
        overall_grade_level,
    )

    day = _call_sop_filter_value(payload, "ad.date_day", "2026-07-02")
    store = _call_sop_filter_value(payload, "ad.store", "理想汽车杭州演示体验中心")
    catalog = catalog_payload()
    category_template = {item["code"]: item for item in catalog}
    categories = {item["code"]: _call_sop_empty_category(item) for item in catalog}
    experts: dict[str, dict[str, Any]] = {}
    total_calls = 0
    connected_calls = 0
    hit_total = 0
    checkpoint_total = 0
    fallback_count = 0
    problem_call_count = 0
    low_quality_call_count = 0
    low_coverage_call_count = 0
    overlapping_problem_call_count = 0
    invite_success_call_count = 0
    invite_eligible_call_count = 0
    next_action_done_call_count = 0
    issue_counts: dict[str, int] = {}
    quality_score_total = 0.0
    quality_score_count = 0
    slot_coverage_total = 0.0
    slot_coverage_count = 0
    rows = _da_call_sop_records(payload)
    for row in rows:
        total_calls += 1
        analysis = _call_sop_json_load(row.get("sop_checkpoints_json"))
        if (
            not isinstance(analysis, dict)
            or str(row.get("sop_analysis_version") or "") != SOP_VERSION
            or str(analysis.get("version") or "") != SOP_VERSION
        ):
            analysis = analyze_call_sop_record(row)
            fallback_count += 1
        if not analysis.get("connected"):
            continue
        connected_calls += 1
        invite_result = str(row.get("invite_result_label") or "").strip()
        if invite_result and invite_result != "未接通":
            invite_eligible_call_count += 1
            invite_success_call_count += 1 if invite_result == "邀约成功" else 0
        next_action = str(row.get("actual_next_action") or "").strip()
        next_action_done_call_count += 1 if next_action and next_action not in {"无", "再次外呼"} else 0
        hit_total += int(analysis.get("hit_checkpoint_count") or 0)
        checkpoint_total += int(analysis.get("total_checkpoint_count") or 0)
        if row.get("total_score") is not None:
            quality_score_total += float(row.get("total_score") or 0)
            quality_score_count += 1
        if row.get("slot_coverage_rate") is not None:
            slot_coverage_total += float(row.get("slot_coverage_rate") or 0)
            slot_coverage_count += 1
        is_low_quality = int(row.get("low_quality_call_count") or 0) > 0
        is_low_coverage = int(row.get("low_coverage_call_count") or 0) > 0
        low_quality_call_count += 1 if is_low_quality else 0
        low_coverage_call_count += 1 if is_low_coverage else 0
        overlapping_problem_call_count += 1 if is_low_quality and is_low_coverage else 0
        is_problem_call = is_low_quality or is_low_coverage
        if is_problem_call:
            problem_call_count += 1
            issue_name = str(row.get("issue_category") or "未分类")
            issue_counts[issue_name] = issue_counts.get(issue_name, 0) + 1
        expert_name = str(row.get("expert_name") or row.get("specialist_id") or "-")
        source_expert_name = _call_sop_source_expert_name(row)
        if expert_name not in experts:
            experts[expert_name] = {
                "name": expert_name,
                "specialistId": row.get("specialist_id"),
                "sourceNameCounts": {},
                "total": 0,
                "hitCheckpointCount": 0,
                "totalCheckpointCount": 0,
                "coverageRate": 0,
                "high": 0,
                "standard": 0,
                "basic": 0,
                "miss": 0,
                "categories": {item["code"]: _call_sop_empty_category(item) for item in catalog},
            }
        expert = experts[expert_name]
        if source_expert_name:
            source_counts = expert["sourceNameCounts"]
            source_counts[source_expert_name] = source_counts.get(source_expert_name, 0) + 1
        expert["total"] += 1
        expert["hitCheckpointCount"] += int(analysis.get("hit_checkpoint_count") or 0)
        expert["totalCheckpointCount"] += int(analysis.get("total_checkpoint_count") or 0)
        overall_level = overall_grade_level(
            row.get("sop_grade_label"),
            float(analysis.get("coverage_rate") or 0),
            True,
        )
        expert[overall_level] += 1
        for category in analysis.get("categories") or []:
            code = category.get("code")
            if code not in category_template:
                continue
            _call_sop_add_category(categories[code], category)
            _call_sop_add_category(expert["categories"][code], category)

    category_rows = [_call_sop_finalize_category(categories[item["code"]]) for item in catalog]
    expert_rows = []
    for expert in experts.values():
        source_counts = expert.pop("sourceNameCounts", {})
        for longer_name in list(source_counts):
            for shorter_name in list(source_counts):
                if (
                    longer_name != shorter_name
                    and len(longer_name) > len(shorter_name)
                    and longer_name.endswith(shorter_name)
                    and longer_name[0] in _CALL_SOP_COMMON_SURNAMES
                ):
                    source_counts[longer_name] += source_counts.pop(shorter_name, 0)
        display_name = max(source_counts.items(), key=lambda item: item[1])[0] if source_counts else ""
        expert["displayName"] = display_name or expert.get("name") or "-"
        expert["sourceNameMatchedCalls"] = int(source_counts.get(display_name, 0)) if display_name else 0
        check_total = int(expert.get("totalCheckpointCount") or 0)
        hit = int(expert.get("hitCheckpointCount") or 0)
        expert["coverageRate"] = hit / check_total if check_total else 0
        expert["achievedRate"] = expert["coverageRate"] * 100
        expert["categories"] = [
            _call_sop_finalize_category(expert["categories"][item["code"]])
            for item in catalog
        ]
        expert_rows.append(expert)
    expert_rows.sort(key=lambda item: (-float(item.get("coverageRate") or 0), -int(item.get("total") or 0), str(item.get("name") or "")))
    return {
        "source": "DA /indicator/api/v1/call-sop/records",
        "version": SOP_VERSION,
        "day": day,
        "store": store,
        "fallbackComputedRows": fallback_count,
        "summary": {
            "totalCalls": total_calls,
            "connectedCalls": connected_calls,
            "connectRate": connected_calls / total_calls if total_calls else 0,
            "hitCheckpointCount": hit_total,
            "totalCheckpointCount": checkpoint_total,
            "coverageRate": hit_total / checkpoint_total if checkpoint_total else 0,
            "problemCallCount": problem_call_count,
            "lowQualityCallCount": low_quality_call_count,
            "lowCoverageCallCount": low_coverage_call_count,
            "overlappingProblemCallCount": overlapping_problem_call_count,
            "qualityIssueRate": problem_call_count / connected_calls if connected_calls else 0,
            "avgQualityScore": quality_score_total / quality_score_count if quality_score_count else 0,
            "avgSlotCoverageRate": slot_coverage_total / slot_coverage_count if slot_coverage_count else 0,
            "inviteSuccessCallCount": invite_success_call_count,
            "inviteEligibleCallCount": invite_eligible_call_count,
            "inviteSuccessRate": invite_success_call_count / invite_eligible_call_count if invite_eligible_call_count else 0,
            "nextActionDoneCallCount": next_action_done_call_count,
            "nextActionCompletionRate": next_action_done_call_count / connected_calls if connected_calls else 0,
        },
        "categories": category_rows,
        "experts": expert_rows,
        "issues": [
            {"name": name, "total": total}
            for name, total in sorted(issue_counts.items(), key=lambda item: (-item[1], item[0]))
        ],
    }


@app.post("/api/da-tms/call-sop/diagnosis")
async def da_tms_call_sop_diagnosis(request: Request):
    try:
        payload = await request.json()
        return await asyncio.to_thread(_call_sop_diagnosis, payload)
    except Exception as exc:
        return JSONResponse({"error": str(exc)}, status_code=500)


def _call_workbench_datetime_parts(value: Any) -> tuple[int, int, int, int, int, int] | None:
    if value in (None, ""):
        return None
    if isinstance(value, datetime.datetime):
        return value.year, value.month, value.day, value.hour, value.minute, value.second
    if isinstance(value, datetime.date):
        return value.year, value.month, value.day, 0, 0, 0
    if isinstance(value, (list, tuple)) and len(value) >= 3:
        try:
            parts = [int(item) for item in value[:6]]
            parts.extend([0] * (6 - len(parts)))
            return tuple(parts[:6])
        except (TypeError, ValueError):
            return None
    if isinstance(value, (int, float)) and not isinstance(value, bool):
        try:
            timestamp = float(value)
            if abs(timestamp) >= 100_000_000_000:
                timestamp /= 1000
            parsed = datetime.datetime.fromtimestamp(timestamp)
            return parsed.year, parsed.month, parsed.day, parsed.hour, parsed.minute, parsed.second
        except (OverflowError, OSError, ValueError):
            return None

    text = str(value).strip()
    if not text:
        return None
    if text[:1] in "[(":
        numbers = re.findall(r"\d+", text)
        if len(numbers) >= 3 and len(numbers[0]) == 4:
            parts = [int(item) for item in numbers[:6]]
            parts.extend([0] * (6 - len(parts)))
            return tuple(parts[:6])
    try:
        parsed = datetime.datetime.fromisoformat(text.replace("Z", "+00:00"))
        return parsed.year, parsed.month, parsed.day, parsed.hour, parsed.minute, parsed.second
    except ValueError:
        return None


def _call_workbench_iso_date(value: Any) -> str:
    parts = _call_workbench_datetime_parts(value)
    if not parts:
        return str(value or "").strip()
    year, month, day, *_ = parts
    return f"{year:04d}-{month:02d}-{day:02d}"


def _call_workbench_date_label(value: Any) -> str:
    parts = _call_workbench_datetime_parts(value)
    if not parts:
        return "-"
    _, month, day, *_ = parts
    return f"{month:02d}-{day:02d}"


def _call_workbench_time_label(value: Any) -> str:
    parts = _call_workbench_datetime_parts(value)
    if not parts:
        return ""
    *_, hour, minute, _ = parts
    return f"{hour:02d}:{minute:02d}"


def _call_workbench_duration_label(seconds: Any) -> str:
    try:
        duration = max(0, int(seconds or 0))
    except Exception:
        duration = 0
    return f"{duration // 60:02d}:{duration % 60:02d}"


def _call_sop_workbench(payload: dict[str, Any]) -> dict[str, Any]:
    from kg_builder.call_quality_workspace import WORKSPACE_VERSION, build_workspace_payload
    from kg_builder.call_sop import SOP_VERSION, catalog_payload

    day = _call_sop_filter_value(payload, "ad.date_day", "2026-07-02")
    store = _call_sop_filter_value(payload, "ad.store", "理想汽车杭州演示体验中心")
    catalog = catalog_payload()
    catalog_names = [str(item.get("name") or "") for item in catalog if item.get("name")]
    fallback_count = 0
    rows = _da_call_sop_records(payload)

    records: list[dict[str, Any]] = []
    result_counts: dict[str, int] = {}
    grade_counts: dict[str, int] = {}
    expert_counts: dict[str, int] = {}
    connected_calls = 0
    duration_total = 0
    word_total = 0

    for row in rows:
        segments = _call_sop_json_load(row.get("call_asr_segments_json"))
        evidence = _call_sop_json_load(row.get("call_sop_evidence_json"))
        detail = _call_sop_json_load(row.get("call_quality_detail_json"))
        analysis = _call_sop_json_load(row.get("sop_checkpoints_json"))
        analysis_is_current = (
            isinstance(analysis, dict)
            and str(row.get("sop_analysis_version") or "") == SOP_VERSION
            and str(analysis.get("version") or "") == SOP_VERSION
        )
        if (
            not isinstance(segments, list)
            or not isinstance(evidence, list)
            or not isinstance(detail, dict)
            or not analysis_is_current
            or str(row.get("call_workspace_version") or "") != WORKSPACE_VERSION
        ):
            source_record = dict(row)
            if analysis_is_current:
                source_record["sop_analysis"] = analysis
            computed = build_workspace_payload(source_record)
            segments = computed.get("segments") or []
            evidence = computed.get("sopEvidence") or []
            detail = computed.get("detail") or {}
            analysis = source_record.get("sop_analysis") or _call_sop_json_load(source_record.get("sop_checkpoints_json"))
            if not analysis_is_current:
                from kg_builder.call_sop import analyze_call_sop_record
                analysis = analyze_call_sop_record(source_record)
            fallback_count += 1

        duration = int(row.get("call_duration_seconds") or detail.get("durationSeconds") or 0)
        word_count = int(row.get("call_word_count") or detail.get("wordCount") or 0)
        result = str(row.get("invite_result_label") or detail.get("inviteResultLabel") or "未识别")
        grade = str(row.get("sop_grade_label") or detail.get("sopGradeLabel") or row.get("quality_score_level") or "未识别")
        primary_sop = str(row.get("primary_sop_category") or detail.get("primarySopCategory") or "")
        expert_name = str(row.get("expert_name") or row.get("specialist_id") or "-")
        expert_display_name = _call_sop_source_expert_name(row) or expert_name
        customer_id = str(row.get("customer_account_id") or "")
        customer_display_name = str(detail.get("customerDisplayName") or (f"客户{customer_id[-4:]}" if customer_id else "未知客户"))
        latest_time = row.get("latest_conversation_time")
        record = {
            "qualityId": row.get("quality_id"),
            "activityDate": _call_workbench_iso_date(row.get("activity_date")),
            "storeName": row.get("store_name"),
            "expertName": expert_name,
            "expertDisplayName": expert_display_name,
            "specialistId": row.get("specialist_id"),
            "customerAccountId": customer_id,
            "customerDisplayName": customer_display_name,
            "latestConversationTime": latest_time,
            "dateLabel": _call_workbench_date_label(latest_time or row.get("activity_date")),
            "timeLabel": _call_workbench_time_label(latest_time),
            "intentName": row.get("intent_name") or row.get("grouped_intent_name"),
            "intentOriginalName": row.get("intent_original_name"),
            "actualNextAction": row.get("actual_next_action"),
            "issueCategory": row.get("issue_category"),
            "qualityScore": float(row.get("total_score") or 0),
            "slotCoverageRate": float(row.get("slot_coverage_rate") or 0),
            "lowQualityCallCount": int(row.get("low_quality_call_count") or 0),
            "lowCoverageCallCount": int(row.get("low_coverage_call_count") or 0),
            "missingSlotCount": int(row.get("missing_slot_count") or 0),
            "connected": bool(analysis.get("connected")) if isinstance(analysis, dict) else result != "未接通",
            "wordCount": word_count,
            "durationSeconds": duration,
            "durationLabel": detail.get("durationLabel") or _call_workbench_duration_label(duration),
            "inviteResultLabel": result,
            "sopGradeLabel": grade,
            "primarySopCategory": primary_sop,
            "segments": segments,
            "sopEvidence": evidence,
            "detail": detail,
        }
        records.append(record)
        result_counts[result] = result_counts.get(result, 0) + 1
        grade_counts[grade] = grade_counts.get(grade, 0) + 1
        expert_counts[expert_name] = expert_counts.get(expert_name, 0) + 1
        if record["connected"]:
            connected_calls += 1
        duration_total += duration
        word_total += word_count

    result_order = ["全部结果", "邀约成功", "待跟进", "邀约失败", "未接通"]
    grade_order = ["全部达成", "高质量达成", "标准达成", "基础达成", "未达成"]
    return {
        "source": "DA /indicator/api/v1/call-sop/records",
        "version": WORKSPACE_VERSION,
        "day": day,
        "store": store,
        "fallbackComputedRows": fallback_count,
        "summary": {
            "totalCalls": len(records),
            "connectedCalls": connected_calls,
            "connectRate": connected_calls / len(records) if records else 0,
            "avgDurationSeconds": duration_total / len(records) if records else 0,
            "avgWordCount": word_total / len(records) if records else 0,
            "resultCounts": result_counts,
            "gradeCounts": grade_counts,
            "expertCounts": expert_counts,
        },
        "filters": {
            "resultOptions": [item for item in result_order if item == "全部结果" or item in result_counts],
            "gradeOptions": [item for item in grade_order if item == "全部达成" or item in grade_counts],
            "sopOptions": ["全部SOP", *catalog_names],
        },
        "records": records,
    }


@app.post("/api/da-tms/call-sop/workbench")
async def da_tms_call_sop_workbench(request: Request):
    try:
        payload = await request.json()
        return await asyncio.to_thread(_call_sop_workbench, payload)
    except Exception as exc:
        return JSONResponse({"error": str(exc)}, status_code=500)


_CALL_SOP_DRILL_DIMENSIONS = {
    "ad.date_day": ("f.activity_date", "日期"),
    "ad.store": ("f.store_name", "门店"),
    "ad.store_city": ("f.store_city", "城市"),
    "ad.store_manager": ("f.manager_name", "店长"),
    "ad.sales_expert": ("f.expert_name", "销售专家"),
    "ad.intent": ("f.intent_name", "客户意图"),
    "ad.quality_score_level": ("f.quality_score_level", "质量分层"),
    "ad.quality_issue_category": ("f.issue_category", "问题分类"),
    "ad.quality_pass": ("f.quality_pass_label", "质检通过"),
    "ad.call_flow_total": ("f.call_flow_total", "电话流向"),
    "ad.quality_rule": ("COALESCE(r.sop_category_name, r.sop_category_code, f.rule_id)", "质检规则"),
}


def _call_sop_filter_conditions(filters: list[dict[str, Any]]) -> tuple[list[str], dict[str, Any]]:
    where: list[str] = []
    params: dict[str, Any] = {}
    for idx, item in enumerate(filters or []):
        if not isinstance(item, dict):
            continue
        member = str(item.get("member") or item.get("code") or "")
        column = _CALL_SOP_DRILL_DIMENSIONS.get(member, ("", ""))[0]
        if not column:
            continue
        values = item.get("values")
        if values is None and item.get("value") is not None:
            values = [item.get("value")]
        if not isinstance(values, list):
            values = [values]
        clean = [str(value) for value in values if value not in (None, "")]
        if not clean:
            continue
        operator = str(item.get("operator") or "equals").lower()
        if operator in {"in", "equals", "eq", "="}:
            keys = []
            for pos, value in enumerate(clean):
                key = f"f_{idx}_{pos}"
                params[key] = value
                keys.append(f":{key}")
            where.append(f"{column} IN ({', '.join(keys)})")
        elif operator in {"not_equals", "neq", "!="}:
            key = f"f_{idx}_0"
            params[key] = clean[0]
            where.append(f"{column} <> :{key}")
    return where, params


def _call_sop_kpi_drill(payload: dict[str, Any]) -> dict[str, Any]:
    member = str(payload.get("dimension") or "")
    dimension = _CALL_SOP_DRILL_DIMENSIONS.get(member)
    if not dimension:
        raise ValueError(f"暂不支持按 {member or '-'} 计算有效接通率")
    _, dimension_label = dimension
    limit = max(1, min(int(payload.get("limit") or 200), 500))
    rows = _da_call_sop_records(payload)
    dimension_fields = {
        "ad.date_day": "activity_date",
        "ad.store": "store_name",
        "ad.store_city": "store_city",
        "ad.store_manager": "manager_name",
        "ad.sales_expert": "expert_name",
        "ad.intent": "intent_name",
        "ad.quality_score_level": "quality_score_level",
        "ad.quality_issue_category": "issue_category",
        "ad.quality_pass": "quality_pass_label",
        "ad.call_flow_total": "call_flow_total",
        "ad.quality_rule": "quality_rule",
    }
    field = dimension_fields[member]
    groups: dict[str, dict[str, float]] = {}
    unconnected_terms = ("无法接听", "语音留言", "未接通", "未建立对话", "空号", "关机")
    for row in rows:
        value = row.get(field)
        if member == "ad.intent" and not value:
            value = row.get("grouped_intent_name")
        key = str(value or "-")
        group = groups.setdefault(key, {
            "total_calls": 0,
            "connected_calls": 0,
            "quality_score_total": 0,
            "quality_score_count": 0,
            "slot_coverage_total": 0,
            "slot_coverage_count": 0,
            "sop_hit_checkpoint_count": 0,
            "sop_total_checkpoint_count": 0,
            "low_quality_call_count": 0,
            "low_coverage_call_count": 0,
            "quality_issue_call_count": 0,
            "missing_slot_count": 0,
        })
        group["total_calls"] += 1
        flag = row.get("sop_connected_flag")
        connected = bool(int(flag)) if flag is not None else not any(
            term in str(row.get("aggregated_content") or "") for term in unconnected_terms
        )
        if not connected:
            continue
        group["connected_calls"] += 1
        if row.get("total_score") is not None:
            group["quality_score_total"] += float(row.get("total_score") or 0)
            group["quality_score_count"] += 1
        if row.get("slot_coverage_rate") is not None:
            group["slot_coverage_total"] += float(row.get("slot_coverage_rate") or 0)
            group["slot_coverage_count"] += 1
        group["sop_hit_checkpoint_count"] += float(row.get("sop_hit_checkpoint_count") or 0)
        group["sop_total_checkpoint_count"] += float(row.get("sop_total_checkpoint_count") or 0)
        low_quality = float(row.get("low_quality_call_count") or 0) > 0
        low_coverage = float(row.get("low_coverage_call_count") or 0) > 0
        group["low_quality_call_count"] += 1 if low_quality else 0
        group["low_coverage_call_count"] += 1 if low_coverage else 0
        group["quality_issue_call_count"] += 1 if low_quality or low_coverage else 0
        group["missing_slot_count"] += float(row.get("missing_slot_count") or 0)

    grouped_rows = sorted(
        groups.items(),
        key=lambda item: (-item[1]["connected_calls"], -item[1]["total_calls"], item[0]),
    )[:limit]
    data = []
    for dim_value, row in grouped_rows:
        total = float(row["total_calls"])
        connected = float(row["connected_calls"])
        hit_total = float(row["sop_hit_checkpoint_count"])
        checkpoint_total = float(row["sop_total_checkpoint_count"])
        data.append({
            member: dim_value,
            "ad.sop_total_call_count": total,
            "ad.quality_record_count": total,
            "ad.effective_connect_rate": connected / total if total else 0,
            "ad.connected_call_count": connected,
            "ad.phone_call_count": total,
            "ad.sop_checkpoint_pass_rate": hit_total / checkpoint_total if checkpoint_total else 0,
            "ad.sop_hit_checkpoint_count": hit_total,
            "ad.sop_total_checkpoint_count": checkpoint_total,
            "ad.quality_issue_rate": min(
                1.0,
                float(row["quality_issue_call_count"]) / connected
            ) if connected else 0,
            "ad.quality_issue_call_count": float(row["quality_issue_call_count"]),
            "ad.avg_call_quality_score": (
                float(row["quality_score_total"]) / float(row["quality_score_count"])
                if row["quality_score_count"] else 0
            ),
            "ad.avg_slot_coverage_rate": (
                float(row["slot_coverage_total"]) / float(row["slot_coverage_count"])
                if row["slot_coverage_count"] else 0
            ),
            "ad.low_quality_call_count": float(row["low_quality_call_count"]),
            "ad.low_coverage_call_count": float(row["low_coverage_call_count"]),
            "ad.missing_slot_count": float(row["missing_slot_count"]),
        })
    return {
        "dimension": member,
        "dimensionLabel": dimension_label,
        "rows": data,
        "source": "DA /indicator/api/v1/call-sop/records",
    }


@app.post("/api/da-tms/call-sop/connect-rate-drill")
async def da_tms_call_sop_connect_rate_drill(request: Request):
    try:
        payload = await request.json()
        return await asyncio.to_thread(_call_sop_kpi_drill, payload)
    except ValueError as exc:
        return JSONResponse({"error": str(exc)}, status_code=400)
    except Exception as exc:
        return JSONResponse({"error": str(exc)}, status_code=500)


@app.post("/api/da-tms/call-sop/kpi-drill")
async def da_tms_call_sop_kpi_drill(request: Request):
    try:
        payload = await request.json()
        return await asyncio.to_thread(_call_sop_kpi_drill, payload)
    except ValueError as exc:
        return JSONResponse({"error": str(exc)}, status_code=400)
    except Exception as exc:
        return JSONResponse({"error": str(exc)}, status_code=500)


def _jsonable_value(value: Any) -> Any:
    if value is None:
        return None
    if hasattr(value, "isoformat"):
        return value.isoformat()
    if hasattr(value, "__float__") and value.__class__.__module__ == "decimal":
        return float(value)
    return value


def _rows_to_dicts(rows) -> list[dict[str, Any]]:
    return [{key: _jsonable_value(value) for key, value in row._mapping.items()} for row in rows]


def _celn_stage_code(raw: str) -> str:
    text = str(raw or "").strip().upper()
    if text in {"C", "E", "L", "N"}:
        return text
    for code in ("C", "E", "L", "N"):
        if text.endswith("_" + code) or text.endswith(code):
            return code
    return text[:1] if text else ""


def _celn_store_insight(payload: dict[str, Any]) -> dict[str, Any]:
    from sqlalchemy import text

    store = str(payload.get("store") or payload.get("storeName") or "理想汽车杭州演示体验中心").strip()
    day = str(payload.get("date") or payload.get("day") or "2026-07-02").strip()
    engine = _cached_da_tms_engine()
    stage_meta = {
        "C": {"name": "C-考虑", "theme": "Why Car", "focus": "确认购车动机与车型兴趣"},
        "E": {"name": "E-评估", "theme": "Why Energy", "focus": "对比竞品、补齐体验与价格信息"},
        "L": {"name": "品牌意向", "theme": "品牌意向", "focus": "推进试驾、报价、金融方案和下一步动作"},
        "N": {"name": "N-谈判", "theme": "Why Now", "focus": "锁定时效、政策、权益和成交动作"},
    }
    with engine.connect() as conn:
        dist_rows = _rows_to_dicts(conn.execute(text("""
            SELECT
              c.current_stage_code AS stage_code,
              COALESCE(s.stage_name_zh, c.current_stage_code) AS stage_name,
              COUNT(*) AS user_count,
              SUM(CASE WHEN c.is_fc_candidate = 1 THEN 1 ELSE 0 END) AS fc_candidate_count,
              COUNT(DISTINCT c.intended_model) AS model_count
            FROM celn_customer c
            JOIN celn_store st ON st.id = c.current_store_id
            LEFT JOIN celn_stage s ON s.stage_code = c.current_stage_code
            WHERE st.store_name = :store
              AND c.is_active = 1
              AND c.current_stage_code IN ('C','E','L','N')
            GROUP BY c.current_stage_code, s.stage_name_zh
        """), {"store": store}))
        latest_day = conn.execute(text("""
            SELECT MAX(activity_date)
            FROM im_celn_store_funnel_fact
            WHERE store_name = :store
              AND activity_date <= :day
              AND celn_funnel_group = 'CELN阶段推进'
        """), {"store": store, "day": day}).scalar()
        if latest_day is None:
            latest_day = day
        prev_day = conn.execute(text("""
            SELECT MAX(activity_date)
            FROM im_celn_store_funnel_fact
            WHERE store_name = :store
              AND activity_date < :day
              AND celn_funnel_group = 'CELN阶段推进'
        """), {"store": store, "day": latest_day}).scalar()
        trend_rows = _rows_to_dicts(conn.execute(text("""
            SELECT
              activity_date,
              celn_funnel_stage_code AS stage_code,
              celn_funnel_stage_name AS stage_name,
              celn_stage_order,
              funnel_count
            FROM im_celn_store_funnel_fact
            WHERE store_name = :store
              AND celn_funnel_group = 'CELN阶段推进'
              AND activity_date IN (:latest_day, :prev_day)
              AND celn_stage_order BETWEEN 1 AND 4
            ORDER BY activity_date, celn_stage_order
        """), {"store": store, "latest_day": latest_day, "prev_day": prev_day or latest_day}))
        recent_trend_rows = _rows_to_dicts(conn.execute(text("""
            SELECT
              activity_date,
              celn_funnel_stage_code AS stage_code,
              celn_funnel_stage_name AS stage_name,
              celn_stage_order,
              funnel_count
            FROM im_celn_store_funnel_fact
            WHERE store_name = :store
              AND celn_funnel_group = 'CELN阶段推进'
              AND activity_date IN (
                SELECT activity_date FROM (
                  SELECT DISTINCT activity_date
                  FROM im_celn_store_funnel_fact
                  WHERE store_name = :store
                    AND activity_date <= :latest_day
                    AND celn_funnel_group = 'CELN阶段推进'
                  ORDER BY activity_date DESC
                  LIMIT 7
                ) recent_days
              )
              AND celn_stage_order BETWEEN 1 AND 4
            ORDER BY activity_date, celn_stage_order
        """), {"store": store, "latest_day": latest_day}))
        flow_rows = _rows_to_dicts(conn.execute(text("""
            SELECT
              r.stage_before,
              r.stage_after,
              COUNT(*) AS flow_count
            FROM celn_follow_up_record r
            JOIN celn_customer c ON c.id = r.customer_id
            JOIN celn_store st ON st.id = c.current_store_id
            WHERE st.store_name = :store
              AND DATE(r.started_at) = :day
              AND r.stage_before IN ('C','E','L','N')
              AND r.stage_after IN ('C','E','L','N')
            GROUP BY r.stage_before, r.stage_after
        """), {"store": store, "day": latest_day}))
        recent_flow_rows = _rows_to_dicts(conn.execute(text("""
            SELECT
              DATE(r.started_at) AS flow_date,
              r.stage_before,
              r.stage_after,
              COUNT(*) AS flow_count
            FROM celn_follow_up_record r
            JOIN celn_customer c ON c.id = r.customer_id
            JOIN celn_store st ON st.id = c.current_store_id
            WHERE st.store_name = :store
              AND DATE(r.started_at) IN (
                SELECT activity_date FROM (
                  SELECT DISTINCT activity_date
                  FROM im_celn_store_funnel_fact
                  WHERE store_name = :store
                    AND activity_date <= :latest_day
                    AND celn_funnel_group = 'CELN阶段推进'
                  ORDER BY activity_date DESC
                  LIMIT 7
                ) recent_days
              )
              AND r.stage_before IN ('C','E','L','N')
              AND r.stage_after IN ('C','E','L','N')
            GROUP BY DATE(r.started_at), r.stage_before, r.stage_after
        """), {"store": store, "latest_day": latest_day}))
        manager_rows = _rows_to_dicts(conn.execute(text("""
            SELECT manager_name
            FROM celn_store_manager m
            JOIN celn_store st ON st.id = m.store_id
            WHERE st.store_name = :store
              AND m.is_active = 1
            ORDER BY m.id
            LIMIT 3
        """), {"store": store}))
        determination_rows = _rows_to_dicts(conn.execute(text("""
            SELECT
              sd.determined_stage_code AS stage_code,
              COUNT(DISTINCT sd.id) AS determination_count,
              AVG(sd.confidence_score) AS avg_confidence,
              COUNT(de.evidence_id) AS evidence_count
            FROM celn_stage_determination sd
            JOIN celn_customer c ON c.id = sd.customer_id
            JOIN celn_store st ON st.id = c.current_store_id
            LEFT JOIN celn_determination_evidence de ON de.determination_id = sd.id
            WHERE st.store_name = :store
              AND DATE(sd.determination_time) = :day
              AND sd.determined_stage_code IN ('C','E','L','N')
            GROUP BY sd.determined_stage_code
        """), {"store": store, "day": latest_day}))
        task_rows = _rows_to_dicts(conn.execute(text("""
            SELECT
              c.current_stage_code AS stage_code,
              COUNT(*) AS next_task_count
            FROM celn_follow_up_task t
            JOIN celn_customer c ON c.id = t.customer_id
            JOIN celn_store st ON st.id = c.current_store_id
            WHERE st.store_name = :store
              AND DATE(t.next_plan_time) = :day
              AND c.current_stage_code IN ('C','E','L','N')
            GROUP BY c.current_stage_code
        """), {"store": store, "day": latest_day}))
        conversion_rows = _rows_to_dicts(conn.execute(text("""
            SELECT
              c.current_stage_code AS stage_code,
              COUNT(*) AS conversion_count
            FROM celn_conversion_event ce
            JOIN celn_customer c ON c.id = ce.customer_id
            JOIN celn_store st ON st.id = ce.store_id
            WHERE st.store_name = :store
              AND DATE(ce.conversion_time) = :day
              AND c.current_stage_code IN ('C','E','L','N')
            GROUP BY c.current_stage_code
        """), {"store": store, "day": latest_day}))
        tag_rows = _rows_to_dicts(conn.execute(text("""
            SELECT
              t.tag_type,
              COUNT(*) AS tag_count
            FROM celn_customer_tag ct
            JOIN celn_tag t ON t.id = ct.tag_id
            JOIN celn_customer c ON c.id = ct.customer_id
            JOIN celn_store st ON st.id = c.current_store_id
            WHERE st.store_name = :store
              AND ct.is_active = 1
            GROUP BY t.tag_type
        """), {"store": store}))
        review_rows = _rows_to_dicts(conn.execute(text("""
            SELECT
              dr.review_date,
              dr.review_conclusion,
              dr.strategy_adjustment
            FROM celn_daily_review dr
            JOIN celn_store st ON st.id = dr.store_id
            WHERE st.store_name = :store
              AND dr.review_date <= :day
            ORDER BY dr.review_date DESC, dr.id DESC
            LIMIT 3
        """), {"store": store, "day": latest_day}))

    dist_by_stage = {row["stage_code"]: row for row in dist_rows}
    determination_by_stage = {_celn_stage_code(row.get("stage_code")): row for row in determination_rows}
    task_by_stage = {_celn_stage_code(row.get("stage_code")): row for row in task_rows}
    conversion_by_stage = {_celn_stage_code(row.get("stage_code")): row for row in conversion_rows}
    trend_map: dict[tuple[str, str], float] = {}
    for row in trend_rows:
        stage = _celn_stage_code(row.get("stage_code"))
        trend_map[(str(row.get("activity_date")), stage)] = float(row.get("funnel_count") or 0)
    recent_dates = sorted({str(row.get("activity_date")) for row in recent_trend_rows if row.get("activity_date")})
    recent_trend_map: dict[tuple[str, str], float] = {}
    for row in recent_trend_rows:
        stage = _celn_stage_code(row.get("stage_code"))
        recent_trend_map[(str(row.get("activity_date")), stage)] = float(row.get("funnel_count") or 0)
    recent_net_map: dict[tuple[str, str], int] = {}
    for date_value in recent_dates:
        for code in ("C", "E", "L", "N"):
            recent_net_map[(date_value, code)] = 0
    for row in recent_flow_rows:
        date_value = str(row.get("flow_date"))
        before = _celn_stage_code(row.get("stage_before"))
        after = _celn_stage_code(row.get("stage_after"))
        count = int(row.get("flow_count") or 0)
        if before == after:
            continue
        if before in {"C", "E", "L", "N"}:
            recent_net_map[(date_value, before)] = recent_net_map.get((date_value, before), 0) - count
        if after in {"C", "E", "L", "N"}:
            recent_net_map[(date_value, after)] = recent_net_map.get((date_value, after), 0) + count
    total = sum(int((dist_by_stage.get(code) or {}).get("user_count") or 0) for code in ("C", "E", "L", "N"))
    stage_net = {code: 0 for code in ("C", "E", "L", "N")}
    stage_flow = []
    for row in flow_rows:
        before = _celn_stage_code(row.get("stage_before"))
        after = _celn_stage_code(row.get("stage_after"))
        count = int(row.get("flow_count") or 0)
        if before != after:
            if before in stage_net:
                stage_net[before] -= count
            if after in stage_net:
                stage_net[after] += count
        stage_flow.append({"from": before, "to": after, "count": count})
    distribution = []
    latest_key = str(latest_day)
    prev_key = str(prev_day) if prev_day else ""
    for code in ("C", "E", "L", "N"):
        row = dist_by_stage.get(code) or {}
        count = int(row.get("user_count") or 0)
        determination_row = determination_by_stage.get(code) or {}
        determination_count = int(determination_row.get("determination_count") or 0)
        cur_snapshot = trend_map.get((latest_key, code), count)
        prev_snapshot = trend_map.get((prev_key, code), cur_snapshot)
        snapshot_change = cur_snapshot - prev_snapshot
        change = stage_net.get(code) if any(stage_net.values()) else snapshot_change
        distribution.append({
            "stageCode": code,
            "stageName": row.get("stage_name") or stage_meta[code]["name"],
            "theme": stage_meta[code]["theme"],
            "focus": stage_meta[code]["focus"],
            "ontologyIndividual": {
                "C": "celn:Demo-C-Consider",
                "E": "celn:Demo-E-Evaluate",
                "L": "celn:Demo-L-Lead",
                "N": "celn:Demo-N-Negotiate",
            }[code],
            "ontologyClass": "celn:Demo-CELNStage",
            "userCount": count,
            "ratio": (count / total) if total else 0,
            "fcCandidateCount": int(row.get("fc_candidate_count") or 0),
            "modelCount": int(row.get("model_count") or 0),
            "determinationCount": determination_count,
            "evidenceCount": int(determination_row.get("evidence_count") or 0),
            "avgConfidence": (
                float(determination_row.get("avg_confidence"))
                if determination_count and determination_row.get("avg_confidence") is not None
                else None
            ),
            "nextTaskCount": int((task_by_stage.get(code) or {}).get("next_task_count") or 0),
            "conversionCount": int((conversion_by_stage.get(code) or {}).get("conversion_count") or 0),
            "snapshotCount": cur_snapshot,
            "previousSnapshotCount": prev_snapshot,
            "change": change,
            "snapshotChange": snapshot_change,
            "flowNetChange": stage_net.get(code, 0),
            "recentTrend": [
                {"date": date_value, "value": recent_net_map.get((date_value, code), 0)}
                for date_value in recent_dates
            ] or [{"date": latest_key, "value": change}],
            "recentTrendMetric": "stage_net_change",
        })
    ln_current = sum(item["snapshotCount"] for item in distribution if item["stageCode"] in {"L", "N"})
    ln_prev = sum(item["previousSnapshotCount"] for item in distribution if item["stageCode"] in {"L", "N"})
    flow_ln_change = sum(stage_net.get(code, 0) for code in ("L", "N"))
    ln_change = flow_ln_change if any(stage_net.values()) else ln_current - ln_prev
    severity = "critical" if ln_change <= -10 else "warning" if ln_change < 0 else "notice"
    direction = "增加" if ln_change > 0 else "减少" if ln_change < 0 else "持平"
    return {
        "store": store,
        "date": str(day),
        "snapshotDate": latest_key,
        "previousDate": prev_key or None,
        "totalUsers": total,
        "distribution": distribution,
        "stageFlows": stage_flow,
        "ln": {"current": ln_current, "previous": ln_prev, "change": ln_change, "direction": direction},
        "businessGraph": {
            "ontology": "理想汽车 CELN 用户分层与跟进闭环业务场景本体",
            "storeManagers": [row.get("manager_name") for row in manager_rows if row.get("manager_name")],
            "coreQuestion": "查询某用户当前处于 CELN 的哪个阶段，并追溯阶段判断、证据、标签、跟进闭环和转化结果。",
            "path": [
                {"node": "core:Demo-Store", "label": "门店", "relation": "celn:Demo-CoversStore / celn:Demo-BelongsToStore", "table": "celn_store", "count": 1},
                {"node": "core:Demo-Customer", "label": "用户", "relation": "celn:Demo-HasStage", "table": "celn_customer", "count": total},
                {"node": "celn:Demo-CELNStage", "label": "CELN阶段", "relation": "celn:Demo-DeterminesStage", "table": "celn_stage / celn_stage_determination", "count": sum(int((r or {}).get("determination_count") or 0) for r in determination_rows)},
                {"node": "celn:Demo-Evidence", "label": "判断证据", "relation": "celn:Demo-BasedOnEvidence", "table": "celn_evidence / celn_determination_evidence", "count": sum(int((r or {}).get("evidence_count") or 0) for r in determination_rows)},
                {"node": "celn:Demo-Tag", "label": "标签", "relation": "celn:Demo-HasTag / celn:Demo-TagAppliesToStage", "table": "celn_tag / celn_customer_tag", "count": sum(int(row.get("tag_count") or 0) for row in tag_rows)},
                {"node": "celn:Demo-FollowUpTask", "label": "跟进任务", "relation": "celn:Demo-AssignedTo / celn:Demo-HasFollowUpRecord", "table": "celn_follow_up_task", "count": sum(int((r or {}).get("next_task_count") or 0) for r in task_rows)},
                {"node": "celn:Demo-FollowUpRecord", "label": "跟进记录", "relation": "celn:Demo-TriggersStageChange", "table": "celn_follow_up_record", "count": sum(int(row.get("count") or 0) for row in stage_flow)},
                {"node": "celn:Demo-ConversionEvent", "label": "转化事件", "relation": "celn:Demo-TracesTo / celn:Demo-LeadsToConversion", "table": "celn_conversion_event / celn_conversion_trace", "count": sum(int((r or {}).get("conversion_count") or 0) for r in conversion_rows)},
                {"node": "celn:Demo-DailyReview", "label": "每日复盘", "relation": "celn:Demo-HasInventory / celn:Demo-ProducesKnowledge", "table": "celn_daily_review / celn_customer_inventory", "count": len(review_rows)},
            ],
            "tagBreakdown": tag_rows,
            "dailyReviews": review_rows,
        },
        "agentPush": {
            "title": f"{store} CELN每日变化",
            "severity": severity,
            "message": f"{latest_key} L/N 合计 {int(ln_current)}；基于 celn:Demo-TriggersStageChange 统计今日阶段流转，L净{stage_net.get('L', 0):+d}、N净{stage_net.get('N', 0):+d}，L/N净{int(ln_change):+d}。请重点查看 L-意向与 N-谈判用户名单、阶段判断证据、跟进记录和下一步计划。",
            "schedule": "每日 09:00 以 celn:Demo-DailyReview 生成主动推送；外部通道可接飞书/企微/Webhook。",
        },
    }


@app.post("/api/da-tms/celn/store-insight")
async def da_tms_celn_store_insight(request: Request):
    try:
        payload = await request.json()
        return await asyncio.to_thread(_celn_store_insight, payload)
    except Exception as exc:
        return JSONResponse({"error": str(exc)}, status_code=500)


@app.get("/api/da-tms/celn/customers")
async def da_tms_celn_customers(store: str = "理想汽车杭州演示体验中心", stage: str = "", limit: int = 100):
    from sqlalchemy import text

    try:
        stage_code = _celn_stage_code(stage)
        page_size = max(1, min(int(limit or 100), 300))
        with _cached_da_tms_engine().connect() as conn:
            rows = _rows_to_dicts(conn.execute(text("""
                SELECT
                  c.id,
                  c.customer_code,
                  c.customer_name,
                  c.phone,
                  c.source_type,
                  c.current_stage_code AS stage_code,
                  COALESCE(s.stage_name_zh, c.current_stage_code) AS stage_name,
                  c.city,
                  c.intended_model,
                  c.is_fc_candidate,
                  c.first_visit_time,
                  c.updated_at,
                  (
                    SELECT pe.expert_name
                    FROM celn_follow_up_task t
                    LEFT JOIN celn_product_expert pe ON pe.id = t.assigned_expert_id
                    WHERE t.customer_id = c.id
                    ORDER BY t.updated_at DESC, t.id DESC
                    LIMIT 1
                  ) AS assigned_expert,
                  (
                    SELECT COUNT(*)
                    FROM celn_follow_up_record r
                    WHERE r.customer_id = c.id
                  ) AS follow_up_count,
                  (
                    SELECT MAX(COALESCE(r.ended_at, r.started_at))
                    FROM celn_follow_up_record r
                    WHERE r.customer_id = c.id
                  ) AS last_follow_up_time
                FROM celn_customer c
                JOIN celn_store st ON st.id = c.current_store_id
                LEFT JOIN celn_stage s ON s.stage_code = c.current_stage_code
                WHERE st.store_name = :store
                  AND c.is_active = 1
                  AND (:stage = '' OR c.current_stage_code = :stage)
                ORDER BY FIELD(c.current_stage_code, 'N','L','E','C'), c.is_fc_candidate DESC, c.updated_at DESC, c.id DESC
                LIMIT :limit
            """), {"store": store, "stage": stage_code, "limit": page_size}))
        return {"store": store, "stage": stage_code or None, "records": rows, "totalShown": len(rows)}
    except Exception as exc:
        return JSONResponse({"error": str(exc)}, status_code=500)


@app.get("/api/da-tms/celn/customer/{customer_id}")
async def da_tms_celn_customer_detail(customer_id: int):
    from sqlalchemy import text

    try:
        with _cached_da_tms_engine().connect() as conn:
            customer_rows = _rows_to_dicts(conn.execute(text("""
                SELECT
                  c.*,
                  st.store_name,
                  st.city AS store_city,
                  COALESCE(s.stage_name_zh, c.current_stage_code) AS stage_name
                FROM celn_customer c
                LEFT JOIN celn_store st ON st.id = c.current_store_id
                LEFT JOIN celn_stage s ON s.stage_code = c.current_stage_code
                WHERE c.id = :customer_id
                LIMIT 1
            """), {"customer_id": customer_id}))
            if not customer_rows:
                return JSONResponse({"error": "用户不存在"}, status_code=404)
            tags = _rows_to_dicts(conn.execute(text("""
                SELECT t.tag_code, t.tag_name, t.tag_type, ct.tagged_at
                FROM celn_customer_tag ct
                JOIN celn_tag t ON t.id = ct.tag_id
                WHERE ct.customer_id = :customer_id AND ct.is_active = 1
                ORDER BY ct.tagged_at DESC
                LIMIT 20
            """), {"customer_id": customer_id}))
            followups = _rows_to_dicts(conn.execute(text("""
                SELECT
                  r.id,
                  pe.expert_name,
                  r.follow_up_method,
                  r.stage_before,
                  r.stage_after,
                  r.follow_up_result,
                  r.started_at,
                  r.ended_at
                FROM celn_follow_up_record r
                LEFT JOIN celn_product_expert pe ON pe.id = r.expert_id
                WHERE r.customer_id = :customer_id
                ORDER BY COALESCE(r.ended_at, r.started_at) DESC, r.id DESC
                LIMIT 10
            """), {"customer_id": customer_id}))
            tasks = _rows_to_dicts(conn.execute(text("""
                SELECT
                  t.id,
                  pe.expert_name,
                  t.task_status,
                  t.next_plan_time,
                  t.priority,
                  t.source_type,
                  t.updated_at
                FROM celn_follow_up_task t
                LEFT JOIN celn_product_expert pe ON pe.id = t.assigned_expert_id
                WHERE t.customer_id = :customer_id
                ORDER BY t.updated_at DESC, t.id DESC
                LIMIT 8
            """), {"customer_id": customer_id}))
            fc = _rows_to_dicts(conn.execute(text("""
                SELECT f.id, r.rule_name, f.fc_status, f.identification_time, f.remark
                FROM celn_fc_identification f
                LEFT JOIN celn_fc_rule r ON r.id = f.fc_rule_id
                WHERE f.customer_id = :customer_id
                ORDER BY f.identification_time DESC, f.id DESC
                LIMIT 5
            """), {"customer_id": customer_id}))
            determinations = _rows_to_dicts(conn.execute(text("""
                SELECT
                  sd.id,
                  sd.determined_stage_code,
                  COALESCE(s.stage_name_zh, sd.determined_stage_code) AS stage_name,
                  sd.confidence_score,
                  sd.judged_by_type,
                  sd.is_reviewable,
                  sd.determination_time,
                  sd.remark,
                  GROUP_CONCAT(CONCAT(e.evidence_name, IFNULL(CONCAT('：', de.evidence_value), '')) SEPARATOR '；') AS evidence_summary
                FROM celn_stage_determination sd
                LEFT JOIN celn_stage s ON s.stage_code = sd.determined_stage_code
                LEFT JOIN celn_determination_evidence de ON de.determination_id = sd.id
                LEFT JOIN celn_evidence e ON e.id = de.evidence_id
                WHERE sd.customer_id = :customer_id
                GROUP BY sd.id, sd.determined_stage_code, s.stage_name_zh, sd.confidence_score,
                         sd.judged_by_type, sd.is_reviewable, sd.determination_time, sd.remark
                ORDER BY sd.determination_time DESC, sd.id DESC
                LIMIT 5
            """), {"customer_id": customer_id}))
        return {"customer": customer_rows[0], "tags": tags, "followups": followups, "tasks": tasks, "fcIdentifications": fc, "determinations": determinations}
    except Exception as exc:
        return JSONResponse({"error": str(exc)}, status_code=500)


def _detail_records_from_da_result(da_result: dict[str, Any]) -> tuple[list[dict[str, str]], list[dict[str, Any]], dict[str, Any]]:
    da_data = da_result.get("data") or {}
    raw_rows = da_data.get("cellList") or []
    columns: list[dict[str, str]] = []
    records: list[dict[str, Any]] = []
    column_labels = _pivot_detail_column_labels()
    if raw_rows and isinstance(raw_rows[0], list):
        header = raw_rows[0]
        if all(isinstance(cell, dict) and str(cell.get("code") or "") == str(cell.get("data") or "") for cell in header):
            columns = [
                {
                    "code": str(cell.get("code") or ""),
                    "name": column_labels.get(str(cell.get("code") or ""), str(cell.get("name") or cell.get("code") or "")),
                }
                for cell in header if isinstance(cell, dict)
            ]
            data_rows = raw_rows[1:]
        else:
            columns = [{"code": f"col_{idx}", "name": f"col_{idx}"} for idx in range(len(header))]
            data_rows = raw_rows
        for row in data_rows:
            records.append({
                columns[idx]["code"]: cell.get("data")
                for idx, cell in enumerate(row)
                if idx < len(columns) and isinstance(cell, dict)
            })
    return columns, records, da_data


def _detail_column_score(column: dict[str, str], measure: dict[str, Any]) -> int:
    code = str(column.get("code") or "").lower()
    name = str(column.get("name") or "").lower()
    measure_code = str(measure.get("code") or "").lower()
    measure_name = str(measure.get("name") or measure.get("title") or "").lower()
    score = 0
    if name and (name in measure_name or measure_name in name):
        score += 60
    if "金额" in str(column.get("name") or "") and "金额" in str(measure.get("name") or measure.get("title") or ""):
        score += 35
    for token in [t for t in measure_code.replace("meas_", "").split("_") if len(t) > 2]:
        if token in code:
            score += 8
    if any(key in code for key in ("sales_price", "net_paid", "amount", "amt", "price")):
        score += 10
    return score


def _infer_detail_value_column(columns: list[dict[str, str]], measure: dict[str, Any]) -> str:
    if not columns:
        return ""
    ranked = sorted(columns, key=lambda col: _detail_column_score(col, measure), reverse=True)
    return str(ranked[0].get("code") or "")


def _infer_order_column(columns: list[dict[str, str]]) -> str:
    for col in columns:
        code = str(col.get("code") or "").lower()
        name = str(col.get("name") or "")
        if "order" in code or "订单" in name:
            return str(col.get("code") or "")
    return ""


def _document_dimension_columns_for_measure(measure_code: str, columns: list[dict[str, str]] | None = None) -> list[dict[str, str]]:
    from rdflib import Namespace, RDF

    detail_names = {str(col.get("code") or ""): str(col.get("name") or col.get("code") or "") for col in (columns or [])}
    try:
        graph = _load_business_graph()
    except Exception:
        return []
    ind = Namespace("http://indicator.insightmind.com/ontology#")

    def val(node, prop) -> str:
        value = graph.value(node, prop)
        return str(value) if value is not None else ""

    measure_node = next((node for node in graph.subjects(RDF.type, ind.Measure) if val(node, ind.code) == measure_code), None)
    if not measure_node:
        return []
    measure_tables = set()
    for app in graph.objects(measure_node, ind.hasMeasureApp):
        table = graph.value(app, ind.appliesToTable) or graph.value(app, ind.measFactTable)
        if table:
            measure_tables.add(table)

    out: list[dict[str, str]] = []
    seen: set[tuple[str, str]] = set()
    for dim in graph.subjects(RDF.type, ind.Dimension):
        dim_code = val(dim, ind.code)
        if not dim_code:
            continue
        dim_name = val(dim, ind.cnName) or val(dim, ind.enName) or dim_code
        for app in graph.objects(dim, ind.hasDimApp):
            table = graph.value(app, ind.dimFactTable)
            if measure_tables and table not in measure_tables:
                continue
            fact_column = val(app, ind.dimFactColumn) or val(app, ind.dimColumn) or val(app, ind.dimPrimaryKey)
            if not fact_column:
                continue
            key = (dim_code, fact_column)
            if key in seen:
                continue
            seen.add(key)
            out.append({
                "code": dim_code,
                "name": dim_name,
                "detailColumn": fact_column,
                "detailColumnName": detail_names.get(fact_column, fact_column),
            })
    return sorted(out, key=lambda item: (item["name"], item["detailColumn"]))


def _resolve_document_target_column_alias(target_column: str, measure: dict[str, Any], columns: list[dict[str, str]]) -> str:
    raw = str(target_column or "").strip()
    if not raw:
        return ""
    detail_codes = {str(col.get("code") or "") for col in columns}
    if raw in detail_codes:
        return raw
    raw_lower = raw.lower()
    for item in _document_dimension_columns_for_measure(str(measure.get("code") or ""), columns):
        if raw in {item.get("code"), item.get("name"), item.get("detailColumn")}:
            return str(item.get("detailColumn") or raw)
        if raw_lower in {str(item.get("code") or "").lower(), str(item.get("name") or "").lower()}:
            return str(item.get("detailColumn") or raw)
    return raw


def _numeric_compare(actual: Any, operator: str, expected: Any) -> bool:
    try:
        left = float(actual)
        right = float(expected)
    except Exception:
        return str(actual) == str(expected) if operator == "eq" else False
    if operator == "eq":
        return left == right
    if operator == "lt":
        return left < right
    if operator == "lte":
        return left <= right
    if operator == "gt":
        return left > right
    if operator == "gte":
        return left >= right
    return False


def _json_dict(raw: Any) -> dict[str, Any]:
    if isinstance(raw, dict):
        return dict(raw)
    if isinstance(raw, str) and raw.strip():
        try:
            parsed = json.loads(raw)
            return parsed if isinstance(parsed, dict) else {}
        except Exception:
            return {}
    return {}


def _document_scan_conditions_from_body(body: dict[str, Any]) -> list[dict[str, Any]]:
    raw_conditions = body.get("conditions")
    conditions: list[dict[str, Any]] = []
    if isinstance(raw_conditions, list):
        for item in raw_conditions:
            if not isinstance(item, dict):
                continue
            column_code = str(item.get("columnCode") or item.get("column") or "").strip()
            value = item.get("value", "")
            if not column_code and value in ("", None):
                continue
            conditions.append({
                "columnCode": column_code,
                "operator": str(item.get("operator") or "eq").lower(),
                "value": value,
                "source": str(item.get("source") or "manual"),
            })
    if not conditions:
        conditions.append({
            "columnCode": str(body.get("columnCode") or "").strip(),
            "operator": str(body.get("operator") or "eq").lower(),
            "value": body.get("value", 0),
            "source": "legacy",
        })
    return conditions


def _resolve_document_scan_conditions(
    conditions: list[dict[str, Any]],
    measure: dict[str, Any],
    columns: list[dict[str, str]],
) -> list[dict[str, Any]]:
    resolved: list[dict[str, Any]] = []
    for idx, item in enumerate(conditions):
        column_code = str(item.get("columnCode") or "").strip()
        if column_code and columns:
            column_code = _resolve_document_target_column_alias(column_code, measure, columns)
        if not column_code and idx == 0 and columns:
            column_code = _infer_detail_value_column(columns, measure)
        column_name = next((col["name"] for col in columns if col.get("code") == column_code), column_code)
        resolved.append({
            **item,
            "columnCode": column_code,
            "columnName": column_name,
            "operator": str(item.get("operator") or "eq").lower(),
        })
    return resolved


async def _run_alert_document_scan(body: dict[str, Any]) -> dict[str, Any]:
    service = _ad_semantic_service()
    measure_member = body.get("measure") or body.get("measureCode")
    measure = service._resolve_member(measure_member, "measure")
    if not measure:
        raise ValueError("缺少或无法识别 measureCode")

    page_size = max(1, min(int(body.get("pageSize") or 500), 500))
    max_rows = max(1, min(int(body.get("maxRows") or 5000), 50000))
    max_matches = max(1, min(int(body.get("maxMatches") or 200), 1000))
    requested_conditions = _document_scan_conditions_from_body(body)
    resolved_conditions: list[dict[str, Any]] = []
    filters = service._convert_filters(body.get("filters") or [], [measure])
    pivot_paths = body.get("pivotPaths")
    pivot_filter_list = None
    if isinstance(pivot_paths, list) and pivot_paths:
        pivot_filter_list = _pivot_drill_filter_list(body.get("pivotFilters") or [], pivot_paths, measure["code"])

    matches: list[dict[str, Any]] = []
    matched_rows = 0
    columns: list[dict[str, str]] = []
    order_column = ""
    scanned = 0
    page_no = 1
    review_sql = ""

    while scanned < max_rows:
        payload = {
            "chartType": 0,
            "sourceType": 0,
            "operaType": 1,
            "cacheStrategy": body.get("cacheStrategy", 1),
            "configureList": [{"code": measure["code"]}],
            "filterList": pivot_filter_list if pivot_filter_list is not None else _pivot_da_filters(filters),
            "measureDetail": True,
            "pageNo": page_no,
            "pageSize": min(page_size, max_rows - scanned),
        }
        da_result = await asyncio.to_thread(_pivot_da_query, payload)
        page_columns, records, da_data = await asyncio.to_thread(_detail_records_from_da_result, da_result)
        if page_columns:
            columns = page_columns
        if columns:
            resolved_conditions = _resolve_document_scan_conditions(requested_conditions, measure, columns)
        if not order_column and columns:
            order_column = _infer_order_column(columns)
        review_sql = da_data.get("reviewSql") or review_sql
        scanned += len(records)

        for record in records:
            condition_values = {
                item["columnCode"]: record.get(item["columnCode"])
                for item in resolved_conditions
                if item.get("columnCode")
            }
            is_match = bool(resolved_conditions) and all(
                item.get("columnCode") and _numeric_compare(record.get(item["columnCode"]), item["operator"], item.get("value"))
                for item in resolved_conditions
            )
            if is_match:
                matched_rows += 1
                if len(matches) < max_matches:
                    first_column = str(resolved_conditions[0].get("columnCode") or "") if resolved_conditions else ""
                    matches.append({
                        "orderNumber": record.get(order_column) if order_column else "",
                        "targetValue": record.get(first_column) if first_column else "",
                        "conditionValues": condition_values,
                        "record": record,
                    })
        if len(records) < payload["pageSize"]:
            break
        page_no += 1

    first_condition = resolved_conditions[0] if resolved_conditions else {}
    target_column = str(first_condition.get("columnCode") or "")
    operator = str(first_condition.get("operator") or "eq")
    expected = first_condition.get("value", 0)
    return {
        "measure": {"code": measure["code"], "name": measure.get("name") or measure.get("title") or measure["code"]},
        "targetColumn": target_column,
        "targetColumnName": first_condition.get("columnName") or next((col["name"] for col in columns if col.get("code") == target_column), target_column),
        "orderColumn": order_column,
        "orderColumnName": next((col["name"] for col in columns if col.get("code") == order_column), order_column),
        "operator": operator,
        "value": expected,
        "conditions": resolved_conditions,
        "columns": columns,
        "matches": matches,
        "summary": {
            "scannedRows": scanned,
            "matchedRows": matched_rows,
            "returnedRows": len(matches),
            "maxRows": max_rows,
            "maxMatches": max_matches,
        },
        "diagnostics": {"reviewSql": review_sql},
    }


@app.post("/api/alerts/document-scan")
async def alert_document_scan(request: Request):
    body = await request.json()
    try:
        return await _run_alert_document_scan(body)
    except ValueError as exc:
        return JSONResponse({"error": str(exc)}, status_code=400)
    except Exception as exc:
        return JSONResponse({"error": str(exc)}, status_code=500)


@app.get("/api/alerts/document-columns")
async def alert_document_columns(request: Request):
    try:
        service = _ad_semantic_service()
        measure_member = request.query_params.get("measureCode") or request.query_params.get("measure")
        measure = service._resolve_member(measure_member, "measure")
        if not measure:
            return JSONResponse({"error": "缺少或无法识别 measureCode"}, status_code=400)

        page_size = max(1, min(int(request.query_params.get("pageSize") or 50), 200))
        payload = {
            "chartType": 0,
            "sourceType": 0,
            "operaType": 1,
            "cacheStrategy": 1,
            "configureList": [{"code": measure["code"]}],
            "filterList": [],
            "measureDetail": True,
            "pageNo": 1,
            "pageSize": page_size,
        }
        da_result = await asyncio.to_thread(_pivot_da_query, payload)
        columns, records, da_data = await asyncio.to_thread(_detail_records_from_da_result, da_result)
        inferred = _infer_detail_value_column(columns, measure)
        order_column = _infer_order_column(columns)
        return {
            "measure": {"code": measure["code"], "name": measure.get("name") or measure.get("title") or measure["code"]},
            "columns": columns,
            "dimensions": _document_dimension_columns_for_measure(measure["code"], columns),
            "inferredColumn": inferred,
            "inferredColumnName": next((col["name"] for col in columns if col.get("code") == inferred), inferred),
            "orderColumn": order_column,
            "orderColumnName": next((col["name"] for col in columns if col.get("code") == order_column), order_column),
            "sampleRows": len(records),
            "diagnostics": {"reviewSql": da_data.get("reviewSql") or ""},
        }
    except ValueError as exc:
        return JSONResponse({"error": str(exc)}, status_code=400)
    except Exception as exc:
        return JSONResponse({"error": str(exc)}, status_code=500)


# ── Ad-Hoc / Dashboard persistence API ──────────────────────────────────── #

def _artifact_safe_id(value: str) -> str:
    import re

    cleaned = re.sub(r"[^A-Za-z0-9_\-]+", "_", str(value or "").strip())
    return cleaned.strip("_")[:80] or uuid.uuid4().hex


def _artifact_path(base_dir: Path, item_id: str) -> Path:
    return base_dir / f"{_artifact_safe_id(item_id)}.json"


def _read_json_artifact(path: Path) -> dict[str, Any]:
    if not path.exists():
        raise FileNotFoundError(path.stem)
    return json.loads(path.read_text(encoding="utf-8"))


def _list_json_artifacts(base_dir: Path) -> list[dict[str, Any]]:
    items = []
    for path in sorted(base_dir.glob("*.json"), key=lambda p: p.stat().st_mtime, reverse=True):
        try:
            data = _read_json_artifact(path)
        except Exception:
            continue
        items.append({
            "id": data.get("id") or path.stem,
            "name": data.get("name") or path.stem,
            "updatedAt": data.get("updatedAt") or "",
            "summary": data.get("summary") or "",
            "spec": data,
        })
    return items


def _artifact_json_response(content: dict[str, Any], status_code: int = 200) -> JSONResponse:
    return JSONResponse(
        content,
        status_code=status_code,
        headers={"Cache-Control": "no-store, no-cache, must-revalidate, max-age=0"},
    )


def _save_json_artifact(base_dir: Path, body: dict[str, Any], kind: str) -> dict[str, Any]:
    now = time.strftime("%Y-%m-%d %H:%M:%S")
    item_id = _artifact_safe_id(body.get("id") or body.get("name") or f"{kind}_{uuid.uuid4().hex[:8]}")
    data = {**body, "id": item_id, "kind": kind, "updatedAt": now}
    path = _artifact_path(base_dir, item_id)
    path.write_text(json.dumps(data, ensure_ascii=False, indent=2), encoding="utf-8")
    return data


@app.get("/api/adhoc/v1/list")
async def adhoc_list():
    return _artifact_json_response({"items": _list_json_artifacts(ADHOC_DIR)})


@app.get("/api/adhoc/v1/{item_id}")
async def adhoc_get(item_id: str):
    try:
        return _artifact_json_response(_read_json_artifact(_artifact_path(ADHOC_DIR, item_id)))
    except FileNotFoundError:
        return _artifact_json_response({"error": "Ad-Hoc 组件不存在"}, status_code=404)


@app.post("/api/adhoc/v1/save")
async def adhoc_save(request: Request):
    body = await request.json()
    try:
        return _save_json_artifact(ADHOC_DIR, body, "adhoc")
    except Exception as exc:
        return JSONResponse({"error": str(exc)}, status_code=500)


@app.delete("/api/adhoc/v1/{item_id}")
async def adhoc_delete(item_id: str):
    path = _artifact_path(ADHOC_DIR, item_id)
    if path.exists():
        path.unlink()
    return {"ok": True}


@app.get("/api/dashboard/v1/list")
async def dashboard_list():
    return _artifact_json_response({"items": _list_json_artifacts(DASHBOARD_DIR)})


@app.get("/api/dashboard/v1/{item_id}")
async def dashboard_get(item_id: str):
    try:
        return _artifact_json_response(_read_json_artifact(_artifact_path(DASHBOARD_DIR, item_id)))
    except FileNotFoundError:
        return _artifact_json_response({"error": "Dashboard 不存在"}, status_code=404)


@app.post("/api/dashboard/v1/save")
async def dashboard_save(request: Request):
    body = await request.json()
    try:
        return _save_json_artifact(DASHBOARD_DIR, body, "dashboard")
    except Exception as exc:
        return JSONResponse({"error": str(exc)}, status_code=500)


@app.delete("/api/dashboard/v1/{item_id}")
async def dashboard_delete(item_id: str):
    path = _artifact_path(DASHBOARD_DIR, item_id)
    if path.exists():
        path.unlink()
    return {"ok": True}


def _dashboard_ai_local_interpretation(payload: dict[str, Any]) -> str:
    widgets = payload.get("widgets") or []
    def _num(value: Any) -> float | None:
        try:
            text = str(value if value is not None else "").replace(",", "").replace("%", "").strip()
            if text in {"", "-", "None", "null"}:
                return None
            return float(text)
        except Exception:
            return None

    def _biz_measure(name: Any) -> str:
        text = str(name or "")
        rules = [
            ("CELN阶段用户数", "各 CELN 阶段的客户人数"),
            ("CELN漏斗计数", "漏斗节点人数"),
            ("经营闭环承接数", "跟进承接量"),
            ("成交结果承接数", "成交推进结果"),
            ("L/N用户数", "高意向和临门成交客户"),
            ("质检通话", "已质检电话"),
            ("通过率", "电话质量达标率"),
            ("平均质量分", "电话沟通质量"),
            ("低覆盖", "关键信息缺口"),
            ("缺失槽位", "话术漏问项"),
        ]
        for key, label in rules:
            if key in text:
                return label
        return text or "当前指标"

    def _row_label(row: dict[str, Any], dimensions: list[str]) -> str:
        parts = []
        for dim in dimensions:
            value = row.get(dim)
            if value not in (None, ""):
                parts.append(str(value))
        if parts:
            return " / ".join(parts[:2])
        for key, value in row.items():
            if _num(value) is None and value not in (None, ""):
                return str(value)
        return "当前门店"

    def _rank(rows: list[dict[str, Any]], measure: str, dimensions: list[str]) -> tuple[tuple[str, float] | None, tuple[str, float] | None, list[tuple[str, float]]]:
        values: list[tuple[str, float]] = []
        for row in rows:
            value = _num(row.get(measure))
            if value is None:
                continue
            values.append((_row_label(row, dimensions), value))
        if not values:
            return None, None, []
        return max(values, key=lambda item: item[1]), min(values, key=lambda item: item[1]), values

    def _widget_focus(name: str) -> str:
        if "异常节点" in name:
            return "列出需要店长当天介入复盘的漏斗断点。"
        if "桑基" in name or "流向" in name:
            return "看客户从兴趣到跟进、再到成交的流向是否顺畅。"
        if "透视" in name:
            return "把阶段推进、经营承接和成交结果放在一起对比，方便定位卡点。"
        if "总览" in name or "异常卡片" in name:
            return "先看当前门店客户主要卡在哪个 CELN 阶段，以及后段 L/N 是否足够。"
        if "电话" in name or "质检" in name:
            return "用电话质量解释跟进承接为什么好或不好。"
        return "用于辅助店长判断当前经营动作是否有效。"

    lines = ["### 现状"]
    if not widgets:
        return "### 现状\n暂无可解读的组件数据。\n\n### 趋势\n暂无趋势数据。\n\n### 异常\n暂无异常信号。\n\n### 建议\n请先刷新看板并确保组件有返回数据。"

    for widget in widgets[:8]:
        name = widget.get("name") or "未命名组件"
        measures = widget.get("measures") or []
        dimensions = widget.get("dimensions") or []
        rows = widget.get("rows") or []
        measure = measures[0] if measures else ""
        high, low, values = _rank(rows, measure, dimensions)
        if values:
            key_measure = "、".join(_biz_measure(m) for m in measures[:3]) or "当前指标"
            main = high[0] if high else "当前节点"
            lines.append(f"- **{name}**：重点看{key_measure}。{_widget_focus(name)} 当前最突出的节点是「{main}」，需要结合下钻名单看具体客户和跟进动作。")
        else:
            lines.append(f"- **{name}**：这个组件主要用于补充经营判断，当前没有足够数据形成明确结论。")

    lines.append("\n### 趋势")
    for widget in widgets[:8]:
        name = widget.get("name") or "未命名组件"
        measures = widget.get("measures") or []
        dimensions = widget.get("dimensions") or []
        rows = widget.get("rows") or []
        measure = measures[0] if measures else ""
        if len(rows) >= 2 and measure:
            try:
                first = _num(rows[0].get(measure))
                last = _num(rows[-1].get(measure))
                if first is None or last is None:
                    raise ValueError("empty trend value")
                delta = last - first
                pct = (delta / first * 100) if first else 0
                direction = "上升" if delta > 0 else "下降" if delta < 0 else "持平"
                if "CELN" in name or "漏斗" in name:
                    if delta < 0:
                        lines.append(f"- **{name}**：从前段兴趣到后段承接呈收窄，说明客户在推进过程中有流失。重点看 E 到 L、L 到 N 之间是否缺少试驾、报价或下一步动作。")
                    elif delta > 0:
                        lines.append(f"- **{name}**：后段承接人数增加，说明有客户被推进到更接近成交的阶段。需要确认这些客户是否已有明确跟进人和下一步计划。")
                    else:
                        lines.append(f"- **{name}**：各节点变化不大，适合重点复盘停留时间长、未推进的客户。")
                else:
                    lines.append(f"- **{name}**：当前走势{direction}，变化约 {pct:.1f}%。建议结合明细确认是正常业务节奏，还是某类客户/销售动作导致。")
            except Exception:
                lines.append(f"- **{name}**：当前数据不适合判断趋势，建议继续看下钻明细。")
        else:
            lines.append(f"- **{name}**：当前更适合看结构和明细，不建议直接判断趋势。")

    lines.append("\n### 异常")
    found = False
    for widget in widgets[:8]:
        name = widget.get("name") or "未命名组件"
        measures = widget.get("measures") or []
        dimensions = widget.get("dimensions") or []
        rows = widget.get("rows") or []
        measure = measures[0] if measures else ""
        high, low, values = _rank(rows, measure, dimensions)
        nums = [v for _, v in values]
        if len(nums) >= 3:
            avg = sum(nums) / len(nums)
            if avg and high and high[1] > avg * 1.5:
                if "异常" in name:
                    lines.append(f"- **{name}**：「{high[0]}」问题最集中，建议店长优先打开名单，确认涉及哪些客户和销售专家。")
                else:
                    lines.append(f"- **{name}**：「{high[0]}」占比较高，说明这类客户或节点是当前经营重点，需要确认承接动作是否跟上。")
                found = True
            if avg and low and low[1] < avg * 0.5:
                if any(key in str(low[0]) for key in ("FC", "确认", "转化", "交付", "N Why Now", "品牌意向")):
                    lines.append(f"- **{name}**：「{low[0]}」偏弱，可能是客户临门推进不足，需要补齐试驾、报价、权益确认或成交下一步。")
                else:
                    lines.append(f"- **{name}**：「{low[0]}」偏低，建议下钻看是否存在客户未跟进、话术缺口或责任人不清。")
                found = True
    if not found:
        lines.append("- 暂未看到特别突出的断点，但仍建议关注 L/N 客户、FC 识别和电话质检低覆盖客户。")

    lines.append("\n### 建议")
    lines.append("- 先看 L-意向、N-谈判和 FC 确认相关节点，优先处理最接近成交但推进慢的客户。")
    lines.append("- 对异常节点直接下钻到客户名单，确认客户当前顾虑、负责销售专家、最近一次跟进和下一步计划。")
    lines.append("- 把电话质检中的低分、低覆盖和缺失槽位同步给销售专家，第二天复盘这些客户是否有阶段推进。")
    return "\n".join(lines)


def _dashboard_ai_call_llm(payload: dict[str, Any]) -> str:
    import os
    import urllib.request as _ureq

    from kg_builder.utils.llm_config import chat_completions_url, llm_config_from_env, llm_request_headers, validate_llm_config

    cfg = llm_config_from_env(BASE_DIR, model_override=os.environ.get("BUSINESS_KG_MODEL", "").strip())
    validate_llm_config(cfg, purpose="Dashboard AI 解读")
    base_url = cfg.get("base_url", "").rstrip("/")
    api_key = cfg.get("api_key", "")
    model = cfg.get("model", "GPT5.5")
    is_anthropic = "anthropic" in base_url.lower()
    system = (
        "你是一名门店经营分析顾问，读者是门店店长。请基于 Dashboard 中每个组件的指标、筛选条件和结果数据，"
        "输出中文 Markdown，必须分为四段：现状、趋势、异常、建议。"
        "表达要业务化，围绕客户处在哪个 CELN 阶段、哪里卡住、该看哪些客户名单、该让销售专家补什么动作。"
        "不要使用样本、均值、维度成员、贡献点、置信区间、环比同比、字段名等技术表达；"
        "不要复述技术字段名，不能编造数据中不存在的事实。"
    )
    user = json.dumps(payload, ensure_ascii=False)[:30000]
    if is_anthropic:
        body = json.dumps({
            "model": model,
            "max_tokens": 1600,
            "system": system,
            "messages": [{"role": "user", "content": user}],
        }).encode("utf-8")
        headers = {"Content-Type": "application/json", "x-api-key": api_key, "anthropic-version": "2023-06-01"}
        req = _ureq.Request(f"{base_url}/messages", data=body, headers=headers, method="POST")
    else:
        body = json.dumps({
            "model": model,
            "max_tokens": 1600,
            "messages": [{"role": "user", "content": system + "\n\n" + user}],
        }).encode("utf-8")
        req = _ureq.Request(chat_completions_url(base_url), data=body, headers=llm_request_headers(cfg), method="POST")
    with _urlopen(req, timeout=60) as resp:
        data = json.loads(resp.read().decode("utf-8"))
    if "choices" in data:
        return data["choices"][0]["message"]["content"]
    if "content" in data and data["content"]:
        text_blocks = [
            item.get("text", "")
            for item in data["content"]
            if isinstance(item, dict) and item.get("type") == "text" and item.get("text")
        ]
        return "\n".join(text_blocks).strip()
    return str(data)


@app.post("/api/dashboard/v1/ai-interpret")
async def dashboard_ai_interpret(request: Request):
    body = await request.json()
    try:
        text = await asyncio.to_thread(_dashboard_ai_call_llm, body)
        return {"text": text, "source": "llm"}
    except Exception as exc:
        return {
            "text": _dashboard_ai_local_interpretation(body),
            "source": "local",
            "warning": str(exc),
        }


_PIVOT_CALL_SOP_RULE_LABELS = {
    "RULE_000": "自我介绍",
    "RULE_001": "回忆唤起",
    "RULE_002": "信息建立",
    "RULE_003": "封闭式邀约",
    "RULE_004": "异议处理",
    "RULE_005": "约定确认",
    "RULE_006": "探需覆盖",
    "RULE_007": "邀约结果",
}

_PIVOT_QUALITY_LEVEL_LABELS = {
    "优秀 >80": "高质量达成",
    "良好 61-80": "标准达成",
    "合格 51-60": "基础达成",
    "未通过 <=50": "未达成",
}


def _pivot_dimension_display_value(code: str, value: Any) -> str:
    """Keep pivot labels business-readable while preserving raw filter values."""
    raw = str(value or "")
    if code == "DIM_quality_rule":
        return _PIVOT_CALL_SOP_RULE_LABELS.get(raw, raw)
    if code == "DIM_quality_score_level":
        return _PIVOT_QUALITY_LEVEL_LABELS.get(raw, raw)
    return raw


def _pivot_path(axis: list[dict[str, str]], values: dict[str, dict[str, Any]]) -> list[dict[str, Any]]:
    return [
        {
            "code": item["code"],
            "name": item["name"],
            "value": _pivot_dimension_display_value(
                item["code"], values.get(item["code"], {}).get("value", "")
            ),
            "filterValue": values.get(item["code"], {}).get("filterValue", ""),
            "viewType": values.get(item["code"], {}).get("viewType", 0),
        }
        for item in axis
    ]


def _pivot_key(path: list[dict[str, Any]]) -> str:
    return json.dumps([item["value"] for item in path], ensure_ascii=False)


def _pivot_time_range(value: str, view_type: int) -> tuple[str, str]:
    import calendar
    import re
    from datetime import date, timedelta

    if view_type == 1:
        return value, value
    if view_type == 2:
        match = re.match(r"^(\d{4})-?W?(\d{1,2})$", value)
        if not match:
            raise ValueError(f"无法识别周维度值 {value}")
        start = date.fromisocalendar(int(match.group(1)), int(match.group(2)), 1)
        return start.isoformat(), (start + timedelta(days=6)).isoformat()
    if view_type == 3:
        year, month = (int(part) for part in value.split("-", 1))
        return f"{year:04d}-{month:02d}-01", f"{year:04d}-{month:02d}-{calendar.monthrange(year, month)[1]:02d}"
    if view_type == 4:
        match = re.match(r"^(\d{4})-?Q([1-4])$", value, re.IGNORECASE)
        if not match:
            raise ValueError(f"无法识别季度维度值 {value}")
        year, quarter = int(match.group(1)), int(match.group(2))
        first_month = (quarter - 1) * 3 + 1
        last_month = first_month + 2
        return f"{year:04d}-{first_month:02d}-01", f"{year:04d}-{last_month:02d}-{calendar.monthrange(year, last_month)[1]:02d}"
    if view_type == 5:
        year = int(value)
        return f"{year:04d}-01-01", f"{year:04d}-12-31"
    raise ValueError(f"不支持的时间维度类型 {view_type}")


def _pivot_drill_filter_list(body_filters: Any, paths: list[dict[str, Any]], measure_code: str) -> list[dict[str, Any]]:
    """Build DA detail filters: active dimension member codes plus selected-field context."""
    catalog = _pivot_catalog()
    dimension_by_code = {item["code"]: item for item in catalog["dimensions"]}
    active_filters = [
        item for item in body_filters if isinstance(item, dict)
        and str(item.get("code") or "").startswith("DIM_")
    ] if isinstance(body_filters, list) else []
    time_paths = []
    for item in paths:
        if not isinstance(item, dict):
            continue
        code = str(item.get("code") or "").strip()
        filter_value = item.get("filterValue")
        if not code or filter_value in (None, ""):
            continue
        meta = dimension_by_code.get(code, {})
        view_type = int(item.get("viewType", meta.get("viewType", 0)) or 0)
        if meta.get("isTime"):
            time_paths.append({**item, "code": code, "filterValue": str(filter_value), "viewType": view_type})
        else:
            active_filters.append({"code": code, "operator": "in", "values": [filter_value], "viewType": view_type})

    if time_paths:
        finest = min(time_paths, key=lambda item: item["viewType"])
        if finest["viewType"] == 1:
            active_filters.append({"code": finest["code"], "operator": "in", "values": [finest["filterValue"]], "viewType": 1})
        else:
            source_meta = dimension_by_code.get(finest["code"], {})
            day_dimension = next((
                item for item in catalog["dimensions"]
                if item.get("isTime") and item.get("viewType") == 1
                and item.get("hierarchyCode") == source_meta.get("hierarchyCode")
                and set(item.get("tables") or []) & set(source_meta.get("tables") or [])
            ), None)
            if not day_dimension:
                raise ValueError(f"未找到维度「{finest.get('name') or finest['code']}」对应的日维度")
            begin, end = _pivot_time_range(finest["filterValue"], finest["viewType"])
            active_filters.append({
                "code": day_dimension["code"],
                "operator": "between",
                "values": [begin, end],
                "viewType": 1,
            })

    da_filters = _pivot_da_filters(active_filters)
    for item in da_filters:
        code = item["code"]
        item["viewType"] = dimension_by_code.get(code, {}).get("viewType", 0)
        for operator in item["operatorList"]:
            operator.setdefault("sqlLogicalType", 0)
            operator.setdefault("timeRange", 0)

    context_codes = list(dict.fromkeys([
        str(item.get("code") or "") for item in paths
        if isinstance(item, dict) and item.get("code")
    ] + [
        str(item.get("code") or "") for item in active_filters
        if isinstance(item, dict) and item.get("code")
    ]))
    da_filters.append({
        "code": measure_code,
        "viewType": None,
        "internal": False,
        "operatorList": [],
    })
    da_filters.extend({
        "code": code,
        "viewType": dimension_by_code.get(code, {}).get("viewType", 0),
        "internal": False,
        "operatorList": [],
    } for code in context_codes if code)
    return da_filters


@functools.lru_cache(maxsize=4)
def _pivot_detail_column_labels_cached(ttl_path: str, modified_ns: int) -> dict[str, str]:
    from rdflib import Graph, Namespace, RDF, RDFS

    graph = Graph()
    graph.parse(ttl_path, format="turtle")
    db = Namespace("http://kg.local/db#")
    ind = Namespace("http://indicator.insightmind.com/ontology#")
    labels = {}
    for column in graph.subjects(RDF.type, db.Column):
        name = graph.value(column, db.name)
        if name is None:
            continue
        code = str(name)
        candidates = [str(label) for label in graph.objects(column, RDFS.label)]
        zh_label = next((
            str(label) for label in graph.objects(column, RDFS.label)
            if getattr(label, "language", None) == "zh"
        ), "")
        if not zh_label:
            zh_label = next((
                label for label in candidates
            if label != code and any("\u4e00" <= char <= "\u9fff" for char in label)
        ), "")
        labels.setdefault(code, zh_label or code)
    for column in graph.subjects(RDF.type, ind.DwColumn):
        name = graph.value(column, ind.columnName)
        if name is None:
            continue
        code = str(name)
        label = graph.value(column, ind.cnName) or graph.value(column, ind.columnComment)
        if label is not None:
            labels[code] = str(label)
        else:
            labels.setdefault(code, code)
    return labels


def _pivot_detail_column_labels() -> dict[str, str]:
    """Return English column name -> Chinese KG label for drill detail headers."""
    ttl_path = _get_active_path()
    labels = {}
    if ttl_path and ttl_path.exists():
        labels.update(_pivot_detail_column_labels_cached(str(ttl_path), ttl_path.stat().st_mtime_ns))
    bkg_path = BKG_DIR / "indicator-data.ttl"
    if bkg_path.exists():
        labels.update(_pivot_detail_column_labels_cached(str(bkg_path), bkg_path.stat().st_mtime_ns))
    return labels


def _normalize_detail_page_info(
    page_info: Any,
    record_count: int,
    page_size: int,
    current_page: int,
) -> dict[str, Any]:
    """Align detail pagination totals with the rows returned by DA detail queries."""
    info = dict(page_info or {}) if isinstance(page_info, dict) else {}
    try:
        page = max(1, int(info.get("currentPage") or current_page or 1))
    except Exception:
        page = max(1, int(current_page or 1))
    try:
        size = max(1, int(page_size or info.get("pageSize") or 50))
    except Exception:
        size = 50
    count = max(0, int(record_count or 0))
    has_next = bool(info.get("hasNextPage"))
    if not has_next and count <= size:
        total_rows = (page - 1) * size + count
        info.update({
            "totalRows": total_rows,
            "pageRecorders": count,
            "totalPages": page if total_rows else 0,
            "pageStartRow": (page - 1) * size,
            "pageEndRow": total_rows,
            "currentPage": page,
            "hasNextPage": False,
            "hasPreviousPage": page > 1,
        })
    return info


def _semantic_member_alias(code: str) -> str:
    raw = str(code or "").strip()
    if raw.startswith("DIM_"):
        return f"ad.{raw[4:].lower()}"
    if raw.startswith("MEAS_"):
        return f"ad.{raw[5:].lower()}"
    return raw


def _pivot_dimension_values_fallback(
    code: str,
    keyword: str,
    page_size: int,
    catalog: dict[str, Any],
) -> list[dict[str, str]]:
    """Fallback for degenerate dimensions when DA's value-list endpoint returns null."""
    measure = next(
        (
            item for item in catalog.get("measures", [])
            if code in (item.get("dimensionCodes") or [])
        ),
        None,
    )
    if not measure:
        return []
    dimension_member = _semantic_member_alias(code)
    query: dict[str, Any] = {
        "measures": [_semantic_member_alias(measure.get("code") or "")],
        "dimensions": [dimension_member],
        "filters": [],
        "order": {},
        "limit": max(1, min(int(page_size or 100), 500)),
        "enableAlerts": False,
    }
    if keyword:
        query["filters"].append({
            "kind": "dimension",
            "member": dimension_member,
            "operator": "contains",
            "values": [keyword],
        })
    result = _ad_semantic_service().load(query)
    options = []
    seen = set()
    for row in result.get("data") or []:
        if not isinstance(row, dict):
            continue
        value = row.get(dimension_member)
        if value in (None, ""):
            continue
        value = str(value)
        if value in seen:
            continue
        seen.add(value)
        options.append({
            "id": value,
            "data": value,
            "label": value,
            "value": value,
        })
    return options


@app.get("/api/pivot/catalog")
async def pivot_catalog():
    try:
        return _pivot_catalog()
    except FileNotFoundError as exc:
        return JSONResponse({"error": str(exc)}, status_code=404)
    except Exception as exc:
        return JSONResponse({"error": str(exc)}, status_code=500)


@app.post("/api/pivot/dimension-values")
async def pivot_dimension_values(request: Request):
    body = await request.json()
    code = str(body.get("code") or "").strip()
    keyword = str(body.get("keyword") or "")
    if not code.startswith("DIM_"):
        return JSONResponse({"error": "请选择非日期维度"}, status_code=400)
    try:
        catalog = _pivot_catalog()
        dimension_by_code = {item["code"]: item for item in catalog["dimensions"]}
        meta = dimension_by_code.get(code)
        if not meta:
            raise ValueError(f"业务图谱中不存在维度 {code}")
        if meta.get("isTime"):
            raise ValueError("日期维度过滤器暂不使用枚举值接口")
        payload = {
            "isGrade": False,
            "isAuth": True,
            "cacheStrategy": 1,
            "pageNo": max(1, int(body.get("pageNo") or 1)),
            "pageSize": max(1, min(int(body.get("pageSize") or 100), 500)),
            "code": code,
            "filterList": [{
                "code": code,
                "operatorList": [{
                    "dataList": [keyword],
                    "sqlOprType": 9,
                    "sqlLogicalType": 0,
                    "timeRange": 0,
                }],
                "internal": True,
            }],
        }
        value_url = _DATA_AGENT_URL.replace("/datasource/query", "/dimension/value/list")
        da_result = await asyncio.to_thread(_pivot_da_post, value_url, payload)
        da_data = da_result.get("data") or {}
        options = []
        seen = set()
        use_data_as_filter = bool(meta.get("hasDimColumnExpr"))
        for row in da_data.get("cellList") or []:
            if not isinstance(row, list) or not row:
                continue
            cell = row[0] if isinstance(row[0], dict) else {}
            raw_id = str(cell.get("id") if cell.get("id") is not None else "")
            raw_data = str(cell.get("data") if cell.get("data") is not None else "")
            label = raw_data or raw_id
            value = raw_data if use_data_as_filter else (raw_id or raw_data)
            if not value:
                continue
            dedupe_key = (value, label)
            if dedupe_key in seen:
                continue
            seen.add(dedupe_key)
            options.append({
                "id": raw_id,
                "data": raw_data,
                "label": label,
                "value": value,
            })
        if not options:
            options = await asyncio.to_thread(
                _pivot_dimension_values_fallback,
                code,
                keyword,
                int(body.get("pageSize") or 100),
                catalog,
            )
        return {
            "options": options,
            "pageInfo": da_data.get("pageInfo") or {},
            "useDataAsFilter": use_data_as_filter,
            "diagnostics": {"reviewSql": da_data.get("reviewSql") or ""},
        }
    except ValueError as exc:
        return JSONResponse({"error": str(exc)}, status_code=400)
    except Exception as exc:
        return JSONResponse({"error": str(exc)}, status_code=500)


@app.post("/api/pivot/query")
async def pivot_query(request: Request):
    body = await request.json()
    measures = _pivot_axis_items(body.get("measures"))
    rows = _pivot_axis_items(body.get("rows"))
    columns = _pivot_axis_items(body.get("columns"))
    filters = body.get("filters") or []
    if not measures:
        return JSONResponse({"error": "请至少选择一个指标"}, status_code=400)

    try:
        catalog = _pivot_catalog()
        measure_by_code = {item["code"]: item for item in catalog["measures"]}
        measure_metas = []
        for measure in measures:
            meta = measure_by_code.get(measure["code"])
            if not meta:
                raise ValueError(f"业务图谱中不存在指标 {measure['code']}")
            measure_metas.append(meta)
        rows = _pivot_resolve_dimensions_for_measures(rows, measure_metas, catalog)
        columns = _pivot_resolve_dimensions_for_measures(columns, measure_metas, catalog)
        selected_dims = rows + columns
        for meta in measure_metas:
            incompatible = [
                dim["name"] for dim in selected_dims
                if not _pivot_dimension_compatible_with_measure(dim, meta, catalog)
            ]
            if incompatible:
                raise ValueError(
                    f"指标「{meta['name']}」与维度 {', '.join(incompatible)} 没有共用事实表"
                )
        limit = max(1, min(int(body.get("limit") or 1000), 10000))
        resolved_filters = _pivot_resolve_filters_for_measures(
            filters, measure_metas, catalog,
        )
        if any(meta.get("formula") for meta in measure_metas):
            result = _pivot_query_formula_measures(
                measures, measure_metas, rows, columns, resolved_filters, catalog, limit,
            )
            from kg_builder.alerts import annotate_pivot_result

            return annotate_pivot_result(result, measures, rows, columns)
        configure = [{"code": item["code"]} for item in measures]
        configure.extend({
            "code": item["code"],
            "order": {"sortType": 1 if item.get("isTime") else 0},
            "alias": "",
            "hasSubtotal": False,
        } for item in selected_dims)
        payload = {
            "configureList": configure,
            "filterList": _pivot_da_filters(resolved_filters),
            "pageSize": limit,
            "pageNum": 1,
        }
        da_result = await asyncio.to_thread(_pivot_da_query, payload)
        da_data = da_result.get("data") or {}
        raw_rows = da_data.get("cellList") or []

        row_headers: dict[str, dict[str, Any]] = {}
        column_headers: dict[str, dict[str, Any]] = {}
        cells = []
        for raw_row in raw_rows:
            if not isinstance(raw_row, list):
                continue
            dim_values = {
                str(cell.get("code") or ""): {
                    "value": str(cell.get("data") or cell.get("id") or ""),
                    "filterValue": str(cell.get("id") or ""),
                    "viewType": cell.get("viewType") or 0,
                }
                for cell in raw_row if isinstance(cell, dict) and cell.get("type") == "DIMENSION"
            }
            row_path = _pivot_path(rows, dim_values)
            column_path = _pivot_path(columns, dim_values)
            row_key = _pivot_key(row_path)
            column_key = _pivot_key(column_path)
            row_headers.setdefault(row_key, {"key": row_key, "path": row_path})
            column_headers.setdefault(column_key, {"key": column_key, "path": column_path})
            for cell in raw_row:
                if not isinstance(cell, dict) or cell.get("type") != "MEASURE":
                    continue
                cells.append({
                    "rowKey": row_key,
                    "columnKey": column_key,
                    "measureCode": str(cell.get("code") or ""),
                    "measureName": str(cell.get("name") or cell.get("code") or ""),
                    "value": cell.get("data"),
                    "rowPath": row_path,
                    "columnPath": column_path,
                })
        if not column_headers:
            column_headers[_pivot_key([])] = {"key": _pivot_key([]), "path": []}
        if not row_headers:
            row_headers[_pivot_key([])] = {"key": _pivot_key([]), "path": []}
        result = {
            "rows": list(row_headers.values()),
            "columns": list(column_headers.values()),
            "measures": [
                {**item, **measure_by_code.get(item["code"], {})} for item in measures
            ],
            "cells": cells,
            "filters": filters,
            "diagnostics": {
                "elapsedMs": da_data.get("cost"),
                "rowCount": len(raw_rows),
                "reviewSql": da_data.get("reviewSql") or "",
            },
        }
        from kg_builder.alerts import annotate_pivot_result

        return annotate_pivot_result(result, measures, rows, columns)
    except ValueError as exc:
        return JSONResponse({"error": str(exc)}, status_code=400)
    except Exception as exc:
        return JSONResponse({"error": str(exc)}, status_code=500)


@app.post("/api/pivot/drill")
async def pivot_drill(request: Request):
    body = await request.json()
    measure_code = str(body.get("measureCode") or "").strip()
    if not measure_code:
        return JSONResponse({"error": "缺少 measureCode"}, status_code=400)
    paths = (body.get("rowPath") or []) + (body.get("columnPath") or [])
    filters = _pivot_drill_filter_list(body.get("filters") or [], paths, measure_code)
    payload = {
        "chartType": 0,
        "sourceType": 0,
        "operaType": 1,
        "cacheStrategy": 1,
        "configureList": [{"code": measure_code}],
        "filterList": filters,
        "measureDetail": True,
        "pageNo": max(1, int(body.get("pageNum") or body.get("pageNo") or 1)),
        "pageSize": max(1, min(int(body.get("pageSize") or 50), 500)),
    }
    try:
        da_result = await asyncio.to_thread(_pivot_da_query, payload)
        da_data = da_result.get("data") or {}
        raw_rows = da_data.get("cellList") or []
        columns = []
        records = []
        column_labels = await asyncio.to_thread(_pivot_detail_column_labels)
        if raw_rows and isinstance(raw_rows[0], list):
            header = raw_rows[0]
            if all(str(cell.get("code") or "") == str(cell.get("data") or "") for cell in header):
                columns = [
                    {
                        "code": str(cell.get("code") or ""),
                        "name": column_labels.get(
                            str(cell.get("code") or ""),
                            str(cell.get("name") or cell.get("code") or ""),
                        ),
                    }
                    for cell in header
                ]
                data_rows = raw_rows[1:]
            else:
                columns = [{"code": f"col_{idx}", "name": f"col_{idx}"} for idx in range(len(header))]
                data_rows = raw_rows
            for row in data_rows:
                records.append({
                    columns[idx]["code"]: cell.get("data")
                    for idx, cell in enumerate(row)
                    if idx < len(columns) and isinstance(cell, dict)
                })
        page_no = payload["pageNo"]
        page_size = payload["pageSize"]
        return {
            "columns": columns,
            "records": records,
            "pageInfo": _normalize_detail_page_info(
                da_data.get("pageInfo") or {},
                len(records),
                page_size,
                page_no,
            ),
            "filters": filters,
            "diagnostics": {"reviewSql": da_data.get("reviewSql") or "", "elapsedMs": da_data.get("cost")},
        }
    except ValueError as exc:
        return JSONResponse({"error": str(exc)}, status_code=400)
    except Exception as exc:
        return JSONResponse({"error": str(exc)}, status_code=500)


# ── 统计分析工作台 API ───────────────────────────────────────────────────── #

@app.get("/api/stats/tables")
async def stats_tables():
    """从 TTL 中解析所有事实表及连接信息，供统计分析选择数据源。"""
    from rdflib import Graph, Namespace, RDF
    ttl_p = BKG_DIR / "indicator-data.ttl"
    if not ttl_p.exists():
        return JSONResponse({"error": "TTL 文件不存在"}, status_code=404)
    g = Graph()
    g.parse(str(ttl_p), format="turtle")
    IND = Namespace("http://indicator.insightmind.com/ontology#")

    def _v(uri, prop):
        v = g.value(uri, prop)
        return str(v) if v else None

    tables = {}
    for tbl_uri in g.subjects(RDF.type, IND.DwTable):
        tbl_name = _v(tbl_uri, IND.tableName)
        if not tbl_name:
            continue
        conn_uri = g.value(tbl_uri, IND.hasConnection)
        if conn_uri:
            tables[tbl_name] = {
                "tableName": tbl_name,
                "cnName":    _v(tbl_uri, IND.cnName) or tbl_name,
                "schema":    _v(tbl_uri, IND.schemaName) or "",
                "host":      _v(conn_uri, IND.host) or "127.0.0.1",
                "port":      int(_v(conn_uri, IND.port) or 3306),
                "dbName":    _v(conn_uri, IND.dbName) or "",
                "dbUser":    _v(conn_uri, IND.dbUser) or "root",
                "dbPassword": _v(conn_uri, IND.dbPassword) or "",
            }
    return list(tables.values())


@app.get("/api/stats/columns")
async def stats_columns(table: str, host: str = "127.0.0.1",
                         port: int = 3306, db: str = "",
                         user: str = "root", password: str = ""):
    """查询指定表的列信息（名称、类型）。"""
    import pymysql
    try:
        conn = pymysql.connect(host=host, port=port, user=user,
                               password=password, database=db, charset="utf8mb4")
        with conn.cursor(pymysql.cursors.DictCursor) as cur:
            cur.execute(f"DESCRIBE `{table}`")
            cols = cur.fetchall()
        conn.close()
        result = []
        for c in cols:
            dtype = str(c.get("Type", "")).lower()
            kind = ("numeric" if any(t in dtype for t in
                    ["int", "float", "double", "decimal", "bigint", "numeric"])
                    else "text")
            result.append({"name": c["Field"], "type": dtype, "kind": kind})
        return result
    except Exception as e:
        return JSONResponse({"error": str(e)}, status_code=500)


@app.post("/api/stats/analyze")
async def stats_analyze(request: Request):
    """
    运行统计分析。
    Body: {
      "table": "...", "host": "...", "port": 3306, "db": "...",
      "user": "...", "password": "...",
      "method": "linear_regression",
      "params": { "y_col": "...", "x_cols": ["..."], ... },
      "limit": 50000,
      "where": ""
    }
    """
    import pymysql
    import pandas as pd_
    try:
        from kg_builder.analysis.stats_analyzer import run_analysis
    except ImportError as e:
        return JSONResponse(
            {
                "error": "统计分析依赖未安装，请执行 pip install -r requirements-analysis.txt 后重试",
                "detail": str(e),
            },
            status_code=503,
        )

    body = await request.json()
    table     = body.get("table", "")
    host      = body.get("host", "127.0.0.1")
    port      = int(body.get("port", 3306))
    db        = body.get("db", "")
    user      = body.get("user", "root")
    password  = body.get("password", "")
    method    = body.get("method", "")
    params    = body.get("params", {})
    limit     = int(body.get("limit", 50000))
    where     = body.get("where", "").strip()

    if not table or not method:
        return JSONResponse({"error": "缺少 table 或 method"}, status_code=400)

    try:
        conn = pymysql.connect(host=host, port=port, user=user,
                               password=password, database=db, charset="utf8mb4",
                               init_command="SET SESSION sql_mode=(SELECT REPLACE(@@sql_mode,'ONLY_FULL_GROUP_BY',''))")
        where_clause = f"WHERE {where}" if where else ""
        sql = f"SELECT * FROM `{table}` {where_clause} LIMIT {limit}"
        df_ = pd_.read_sql(sql, conn)
        conn.close()
    except Exception as e:
        return JSONResponse({"error": f"数据获取失败: {e}"}, status_code=500)

    result = run_analysis(df_, method, params)
    return JSONResponse(result)


# ── Entry point ─────────────────────────────────────────────────────────── #


@app.on_event("startup")
async def on_startup():
    """Initialize alert DB and start the background scanner."""
    configure_insight_runtime(
        series_loader=_insight_series_loader,
        goal_loader=_insight_goal_loader,
        catalog_loader=_pivot_catalog,
    )

    try:
        init_insight_store()
    except Exception as e:
        logging.getLogger(__name__).warning("Insight DB init failed: %s", e)

    try:
        init_feedback_store()
    except Exception as e:
        logging.getLogger(__name__).warning("Feedback DB init failed: %s", e)

    try:
        init_db()
    except Exception as e:
        logging.getLogger(__name__).warning("Alert DB init failed: %s", e)

    try:
        # Wire the scheduler's load function to the internal semantic API
        from kg_builder.alerts.scheduler import init_scan_loader
        init_scan_loader(_execute_semantic_load_for_alerts)
        await alert_scheduler.start(interval=300)
    except Exception as e:
        logging.getLogger(__name__).warning("Alert scheduler start failed: %s", e)


async def _execute_semantic_load_for_alerts(query: dict):
    import asyncio
    service = _ad_semantic_service()
    result = await asyncio.to_thread(service.load, query)
    if query.get('enableAlerts', True) is not False:
        from kg_builder.alerts import annotate_semantic_result
        result = await asyncio.to_thread(
            annotate_semantic_result, result, query,
            _pivot_catalog(), BKG_DIR / 'indicator-data.ttl', service.load)
    return result

if __name__ == "__main__":
    import uvicorn
    uvicorn.run("web_app:app", host="0.0.0.0", port=8080, reload=False)
