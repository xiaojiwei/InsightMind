"""
web_app.py — FastAPI web interface for the KG Builder.

Usage:
  pip install fastapi uvicorn[standard] jinja2 python-multipart
  python web_app.py
  # Open http://localhost:8000
"""
from __future__ import annotations

import asyncio
import functools
import json
import logging
import queue
import threading
import time
import uuid
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

# ── App setup ───────────────────────────────────────────────────────────── #

BASE_DIR  = Path(__file__).parent
TEMPLATES = Jinja2Templates(directory=str(BASE_DIR / "kg_builder" / "web" / "templates"))
OUTPUT_DIR = BASE_DIR / "output"
OUTPUT_DIR.mkdir(exist_ok=True)
BKG_DIR = OUTPUT_DIR / "business_kg"
BKG_DIR.mkdir(exist_ok=True)
ADHOC_DIR = OUTPUT_DIR / "adhoc"
ADHOC_DIR.mkdir(exist_ok=True)
DASHBOARD_DIR = OUTPUT_DIR / "dashboards"
DASHBOARD_DIR.mkdir(exist_ok=True)
# KG files are named kg_YYYYMMDD_NNN.ttl; legacy kg.ttl is still auto-detected
_current_kg_path: Optional[Path] = None


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
    return archive


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

app = FastAPI(title="KG Builder Web UI")

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
             "kg_builder.parsers.data_sampler"):
    logging.getLogger(_mod).addHandler(_queue_handler)
    logging.getLogger(_mod).setLevel(logging.INFO)


# ── Pydantic models ─────────────────────────────────────────────────────── #

class DSConfig(BaseModel):
    name:            str  = "mysql_local"
    db_type:         str  = "mysql"
    host:            str  = "localhost"
    port:            int  = 3306
    database:        str  = "tpcds"
    username:        str  = "root"
    password:        str  = "root"
    schema_name:     str  = ""
    service_name:    str  = ""
    sid:             str  = ""
    windows_auth:    bool = False
    driver:          str  = ""
    sample_limit:    int  = 1000
    exclude_tables:  list[str] = []
    all_databases:   bool = True   # scan every non-system database on the server

class BuildRequest(BaseModel):
    datasource:             DSConfig
    enable_sampling:        bool  = True
    enable_implicit:        bool  = True
    enable_reasoning:       bool  = False
    similarity_threshold:   float = 0.85
    synonyms_path:          str   = "synonyms.yaml"
    st_model:               str   = "paraphrase-multilingual-MiniLM-L12-v2"

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

class EntityLookupRequest(BaseModel):
    question: str
    pageSize: int = 500
    pageNum: int = 1


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

        # ── Step 11: 语义隐式关系 ──────────────────────────────────────── #
        if req.enable_implicit:
            _log("发现隐式关系（语义相似度）…")
            _set_state("running", "语义分析…", 70)
            implicit_extractor = ImplicitRelationExtractor(
                model_name=req.st_model,
                similarity_threshold=req.similarity_threshold,
            )
            implicit_rels = implicit_extractor.extract(entity_graph)
            relations.extend(implicit_rels)
            step(11, f"{len(implicit_rels)} 条隐式关系")
        else:
            skip(11)

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

        connector.close()
        _log("=== 构建完成 ===")
        _set_state("done", f"完成，共 {triple_count} 条三元组", 100)

    except Exception as exc:
        _log(f"[错误] {exc}")
        _set_state("error", str(exc), 0)


# ── Routes ───────────────────────────────────────────────────────────────── #

@app.get("/", response_class=HTMLResponse)
async def index(request: Request):
    return TEMPLATES.TemplateResponse(request, "index.html")


@app.get("/dashboard/view/{item_id}", response_class=HTMLResponse)
async def dashboard_view(request: Request, item_id: str):
    return TEMPLATES.TemplateResponse(request, "index.html")


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

_DATA_AGENT_URL = "http://localhost:8091/bi/v1/datasource/query"


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

    IND = _NS("http://indicator.lixiang.com/ontology#")

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
    IND = _NS("http://indicator.lixiang.com/ontology#")
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
            with _ureq.urlopen(req, timeout=15) as resp:
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

    IND = _NS("http://indicator.lixiang.com/ontology#")
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

    IND = Namespace("http://indicator.lixiang.com/ontology#")
    INST = Namespace("http://indicator.lixiang.com/instance/")
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

    IND = Namespace("http://indicator.lixiang.com/ontology#")
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

            turtle_str, ok = builder.build(
                summary,
                domain_hint=domain_hint,
                pattern_context=pattern_context,
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
        _current_bkg_path = save_path
        _bkg_meta         = {
            "gen_time":  time.strftime("%Y-%m-%d %H:%M:%S"),
            "source_kg": active.name if active else "",
            "source_schema": source_schema or "",
        }
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
        return {"ok": True, "message": "已是当前版本", "active": target.name}
    # 直接复制历史版本覆盖当前，不归档旧版本
    target.write_text(src.read_text(encoding="utf-8"), encoding="utf-8")
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
    from kg_builder.utils.llm_config import llm_config_from_env, validate_llm_config

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
            cfg["base_url"].rstrip("/") + "/chat/completions",
            data=body, method="POST",
            headers={"Content-Type": "application/json",
                     "Authorization": f"Bearer {cfg['api_key']}"},
        )
        t0 = _time.time()
        try:
            with urllib.request.urlopen(req2, timeout=1200) as resp:
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
        IND_NS = "http://indicator.lixiang.com/ontology#"
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
            "   PREFIX ind:  <http://indicator.lixiang.com/ontology#>\n"
            "   PREFIX inst: <http://indicator.lixiang.com/instance/>\n"
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
    IND = "http://indicator.lixiang.com/ontology#"
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
    else:
        return JSONResponse(status_code=404, content={"error": "尚未生成业务图谱"})

    import json as _json
    from rdflib import Graph, URIRef, RDF

    IND = "http://indicator.lixiang.com/ontology#"

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

    IND = "http://indicator.lixiang.com/ontology#"

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
        dims.append({
            "code": code,
            "name": _val(d, "cnName") or _val(d, "enName") or code,
            "tables": tables,
        })

    measures.sort(key=lambda x: x["name"])
    dims.sort(key=lambda x: x["name"])

    primary_measure = measures[0] if measures else {"name": "核心指标", "tables": set()}
    second_measure = measures[1] if len(measures) > 1 else primary_measure
    compatible_dims = [d for d in dims if d["tables"] & primary_measure["tables"]] or dims
    first_dim = compatible_dims[0] if compatible_dims else {"name": "业务维度"}
    second_dim = compatible_dims[1] if len(compatible_dims) > 1 else first_dim

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
        candidates.sort(key=_entity_sort_key)
        used_entity = set()
        for c in candidates:
            label = str(c.get("_display_label") or "").strip()
            if not label or label in used_entity:
                continue
            source_table = str(c.get("sourceTableName") or c.get("tableName") or "").lower()
            column_name = str(c.get("columnName") or "").lower()
            value = ""
            for (tbl, col, val), _rows in service._source_rows_by_table_col_value.items():
                if tbl == source_table and col == column_name and str(val).strip():
                    value = str(val)
                    break
            if not value:
                continue
            used_entity.add(label)
            entity_examples.append(f"{label} ： {value}")
            if len(entity_examples) >= 4:
                break
    except Exception:
        entity_examples = []

    # 用实体例子生成维度过滤的明细查询提示
    detail_examples = [
        f"查询最近3个月{primary_measure['name']}的明细",
        f"分析最近3个月{second_measure['name']}的明细数据",
    ]
    for ee in entity_examples[:2]:
        parts = ee.split(" ： ", 1)
        if len(parts) == 2:
            dim_label, dim_value = parts[0].strip(), parts[1].strip()
            # 维度过滤例子交替使用不同指标
            m = primary_measure['name'] if len(detail_examples) % 2 == 0 else second_measure['name']
            detail_examples.append(
                f"查询最近3个月{dim_label}{dim_value}的{m}明细"
            )

    category_defs = [
        ("指标汇总", [
            f"查询最近3个月{primary_measure['name']}",
            f"统计最近3个月{second_measure['name']}",
        ]),
        ("维度分析", [
            f"最近3个月按{first_dim['name']}分析{primary_measure['name']}",
            f"按{second_dim['name']}对比{second_measure['name']}",
        ]),
        ("明细检索", detail_examples),
        ("属性检索", entity_examples[:4]),
        ("图谱解释", [
            f"{primary_measure['name']}有哪些可分析维度",
            f"{second_measure['name']}的口径是什么",
        ]),
        ("深度洞察", [
            f"为什么最近3个月{primary_measure['name']}变化",
            f"哪个维度对{second_measure['name']}影响最大",
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

    return {"file": p.name, "categories": categories, "questions": flat}


@app.get("/api/business-kg/ontology")
async def business_kg_ontology():
    """Return the fixed RDFS/OWL ontology preamble (classes + properties)."""
    from kg_builder.business_kg.llm_builder import _ONTOLOGY_PREAMBLE
    return {"content": _ONTOLOGY_PREAMBLE}


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
    from kg_builder.utils.llm_config import llm_config_from_env
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
        f"{base_url}/chat/completions", data=payload,
        headers={"Content-Type": "application/json",
                 "Authorization": f"Bearer {api_key}"},
        method="POST",
    )
    try:
        with _ureq.urlopen(req, timeout=120) as resp:
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
    files = [BKG_DIR / "indicator-data.ttl"]
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

    IND = Namespace("http://indicator.lixiang.com/ontology#")

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
    if file:
        p = (BKG_DIR / Path(file).name).resolve()
        if str(p).startswith(str(BKG_DIR.resolve())) and p.exists():
            return p
        return None
    if _current_bkg_path and _current_bkg_path.exists():
        return _current_bkg_path
    p = BKG_DIR / "indicator-data.ttl"
    return p if p.exists() else None


def _validate_bkg_iter(ttl_path: Path):
    """逐指标校验生成器，先 yield init/measure/summary 事件。"""
    from rdflib import Graph, Namespace
    import urllib.request as _ureq

    IND = Namespace("http://indicator.lixiang.com/ontology#")
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
            with _ureq.urlopen(req, timeout=20) as resp:
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
                    with _ureq.urlopen(req, timeout=20) as resp:
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

    IND = Namespace("http://indicator.lixiang.com/ontology#")
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
        with _ureq.urlopen(dr, timeout=20) as resp:
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

_insight_log_queue: queue.Queue = queue.Queue()
_insight_state: dict = {"status": "idle"}
_insight_generation: int = 0
_insight_ack_event: threading.Event = threading.Event()
_insight_ack_info: dict = {"success": True, "error": ""}


def _insight_worker(
    question: str,
    generation: int,
    conversation_id: str = "",
    context: Optional[dict[str, Any]] = None,
) -> None:
    global _insight_state, _insight_generation

    from kg_builder.utils.llm_config import llm_config_from_env
    llm_config = llm_config_from_env(BASE_DIR)

    def ilog(msg: str) -> None:
        if _insight_generation != generation:
            return
        _insight_log_queue.put(json.dumps({"log": msg}))
        try:
            with open("/tmp/insight_debug.log", "a", encoding="utf-8") as _f:
                _f.write(msg + "\n")
        except Exception:
            pass

    try:
        from kg_builder.analysis.insight_analyzer import InsightAnalyzer
        analyzer = InsightAnalyzer(
            data_agent_url=_DATA_AGENT_URL,
            ttl_path=str(BKG_DIR / "indicator-data.ttl"),
            llm_config=llm_config,
            log_cb=ilog,
            cancel_cb=lambda: _insight_generation != generation,
            context=context,
        )
        for step_result in analyzer.analyze(question):
            if _insight_generation != generation:
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
                _insight_ack_event.clear()
            _insight_log_queue.put(msg)
            if needs_ack:
                ack_received = _insight_ack_event.wait(timeout=60)
                if _insight_generation != generation:
                    return
                if not ack_received:
                    ilog(f"⚠ Part {part_key or 'report'} 前端60s内未确认，继续执行")
                elif not _insight_ack_info.get("success", True):
                    err = _insight_ack_info.get("error", "未知错误")
                    ilog(f"✗ Part {part_key or 'report'} 前端渲染失败: {err}")
    except Exception as e:
        if _insight_generation == generation:
            ilog(f"✗ Insight 分析失败: {e}")
            import traceback
            ilog(traceback.format_exc())
    finally:
        if _insight_generation == generation:
            _insight_state["status"] = "done"
            _insight_log_queue.put("__DONE__")


@app.post("/api/insight/start")
async def insight_start(request: Request):
    global _insight_generation, _insight_state, _insight_ack_event, _insight_ack_info
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

    _insight_generation += 1
    gen = _insight_generation
    # 清空队列残留
    while not _insight_log_queue.empty():
        try:
            _insight_log_queue.get_nowait()
        except Exception:
            break
    # 重置 ACK 状态
    _insight_ack_event.clear()
    _insight_ack_info["success"] = True
    _insight_ack_info["error"] = ""
    _insight_state["status"] = "running"
    threading.Thread(
        target=_insight_worker,
        args=(question, gen, conversation_id, request_context),
        daemon=True,
    ).start()
    return {"status": "started", "conversationId": conversation_id}


@app.get("/api/insight/log")
async def insight_log():
    """SSE stream for insight analysis progress and results."""
    async def gen():
        while True:
            try:
                msg = _insight_log_queue.get_nowait()
                yield f"data: {msg}\n\n"
                if msg == "__DONE__":
                    break
            except queue.Empty:
                if _insight_state.get("status") not in ("idle", "running"):
                    yield "data: __DONE__\n\n"
                    break
                yield ": ping\n\n"
                await asyncio.sleep(0.4)
    return StreamingResponse(gen(), media_type="text/event-stream")


@app.post("/api/insight/ack")
async def insight_ack(request: Request):
    """前端渲染完成后调用，通知后端继续执行下一个 Part。"""
    global _insight_ack_info
    body = await request.json()
    _insight_ack_info["success"] = body.get("success", True)
    _insight_ack_info["error"]   = body.get("error", "")
    _insight_ack_event.set()
    return {"status": "ok"}


# ── NLQ API ──────────────────────────────────────────────────────────────── #

_nlq_context_store: dict[str, dict[str, Any]] = {}
_nlq_context_lock = threading.Lock()


@app.post("/api/nlq/query")
async def nlq_query(req: NLQRequest):
    """Natural language -> business KG match -> DA query payload/result."""
    question = (req.question or "").strip()
    if not question:
        return JSONResponse({"ok": False, "error": "question 不能为空"}, status_code=400)
    ttl_path = BKG_DIR / "indicator-data.ttl"
    if not ttl_path.exists():
        return JSONResponse({
            "ok": False,
            "error": "尚未生成业务图谱，请先在「业务图谱」生成 indicator-data.ttl",
        }, status_code=404)

    conversation_id = (req.conversationId or "").strip() or str(uuid.uuid4())
    with _nlq_context_lock:
        if req.resetContext:
            _nlq_context_store.pop(conversation_id, None)
        stored_context = dict(_nlq_context_store.get(conversation_id) or {})
    request_context = dict(stored_context)
    if req.context:
        request_context.update(req.context)

    def _run():
        from kg_builder.nlq import NaturalLanguageQueryService
        source_ttl_path = _get_active_path()
        service = NaturalLanguageQueryService(
            ttl_path=ttl_path,
            data_agent_url=_DATA_AGENT_URL,
            source_ttl_path=source_ttl_path,
            log_cb=lambda msg: logging.getLogger("uvicorn").info(msg),
        )
        return service.query(
            question,
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
        result["conversationId"] = conversation_id
        resolved_context = result.get("resolvedContext")
        if isinstance(resolved_context, dict):
            with _nlq_context_lock:
                if len(_nlq_context_store) >= 200 and conversation_id not in _nlq_context_store:
                    _nlq_context_store.pop(next(iter(_nlq_context_store)), None)
                _nlq_context_store[conversation_id] = dict(resolved_context)
        return JSONResponse(result)
    except Exception as e:
        return JSONResponse({"ok": False, "error": str(e)}, status_code=500)


@app.post("/api/nlq/entity-lookup")
async def nlq_entity_lookup(req: EntityLookupRequest):
    """Attribute-value detail lookup with peer analysis from business/source KG."""
    question = (req.question or "").strip()
    if not question:
        return JSONResponse({"ok": False, "error": "question 不能为空"}, status_code=400)
    ttl_path = BKG_DIR / "indicator-data.ttl"
    if not ttl_path.exists():
        return JSONResponse({
            "ok": False,
            "error": "尚未生成业务图谱，请先在「业务图谱」生成 indicator-data.ttl",
        }, status_code=404)

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
        status = 200 if result.get("ok") else 400
        return JSONResponse(result, status_code=status)
    except Exception as e:
        return JSONResponse({"ok": False, "error": str(e)}, status_code=500)


# ── 透视分析 API ────────────────────────────────────────────────────────── #

def _pivot_catalog() -> dict[str, Any]:
    """Read pivot-ready measures and dimensions from the active business KG."""
    from rdflib import Graph, Namespace, RDF

    ttl_path = BKG_DIR / "indicator-data.ttl"
    if not ttl_path.exists():
        raise FileNotFoundError("尚未生成业务图谱，请先生成 indicator-data.ttl")

    graph = Graph()
    graph.parse(str(ttl_path), format="turtle")
    ind = Namespace("http://indicator.lixiang.com/ontology#")

    def val(node, prop) -> str:
        value = graph.value(node, prop)
        return str(value) if value is not None else ""

    dimensions = []
    dimension_tables: dict[str, set[str]] = {}
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
        compatible = [
            dim["code"] for dim in dimensions
            if tables & dimension_tables.get(dim["code"], set())
        ]
        measures.append({
            "code": code,
            "name": val(node, ind.cnName) or code,
            "unit": val(node, ind.unit),
            "caliber": val(node, ind.caliber) or val(node, ind.definition),
            "tables": sorted(tables),
            "dimensionCodes": compatible,
        })

    return {
        "measures": sorted(measures, key=lambda item: item["name"]),
        "dimensions": sorted(dimensions, key=lambda item: (not item["isTime"], item["name"])),
    }


def _pivot_da_query(payload: dict[str, Any]) -> dict[str, Any]:
    return _pivot_da_post(_DATA_AGENT_URL, payload)


def _ad_semantic_service():
    from kg_builder.semantic import AdSemanticService

    return AdSemanticService(
        catalog=_pivot_catalog(),
        da_query=_pivot_da_query,
        da_filter_builder=_pivot_da_filters,
    )


def _pivot_da_post(url: str, payload: dict[str, Any]) -> dict[str, Any]:
    import urllib.error as url_error
    import urllib.request as url_request

    request = url_request.Request(
        url,
        data=json.dumps(payload, ensure_ascii=False).encode("utf-8"),
        headers={"Content-Type": "application/json"},
        method="POST",
    )
    try:
        with url_request.urlopen(request, timeout=30) as response:
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


# ── AD Semantic API (Cube-like facade over DA) ───────────────────────────── #

@app.get("/api/ad/v1/meta")
async def ad_semantic_meta():
    try:
        return _ad_semantic_service().meta()
    except FileNotFoundError as exc:
        return JSONResponse({"error": str(exc)}, status_code=404)
    except Exception as exc:
        return JSONResponse({"error": str(exc)}, status_code=500)


@app.post("/api/ad/v1/load")
async def ad_semantic_load(request: Request):
    try:
        body = await request.json()
        result = await asyncio.to_thread(_ad_semantic_service().load, body)
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
        service = _ad_semantic_service()
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
        result = await asyncio.to_thread(_ad_semantic_service().chart, body)
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
        service = _ad_semantic_service()
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
        return {
            "columns": columns,
            "records": records,
            "pageInfo": da_data.get("pageInfo") or {},
            "daPayload": payload,
            "diagnostics": {"reviewSql": da_data.get("reviewSql") or "", "elapsedMs": da_data.get("cost")},
        }
    except FileNotFoundError as exc:
        return JSONResponse({"error": str(exc)}, status_code=404)
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


def _save_json_artifact(base_dir: Path, body: dict[str, Any], kind: str) -> dict[str, Any]:
    now = time.strftime("%Y-%m-%d %H:%M:%S")
    item_id = _artifact_safe_id(body.get("id") or body.get("name") or f"{kind}_{uuid.uuid4().hex[:8]}")
    data = {**body, "id": item_id, "kind": kind, "updatedAt": now}
    path = _artifact_path(base_dir, item_id)
    path.write_text(json.dumps(data, ensure_ascii=False, indent=2), encoding="utf-8")
    return data


@app.get("/api/adhoc/v1/list")
async def adhoc_list():
    return {"items": _list_json_artifacts(ADHOC_DIR)}


@app.get("/api/adhoc/v1/{item_id}")
async def adhoc_get(item_id: str):
    try:
        return _read_json_artifact(_artifact_path(ADHOC_DIR, item_id))
    except FileNotFoundError:
        return JSONResponse({"error": "Ad-Hoc 组件不存在"}, status_code=404)


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
    return {"items": _list_json_artifacts(DASHBOARD_DIR)}


@app.get("/api/dashboard/v1/{item_id}")
async def dashboard_get(item_id: str):
    try:
        return _read_json_artifact(_artifact_path(DASHBOARD_DIR, item_id))
    except FileNotFoundError:
        return JSONResponse({"error": "Dashboard 不存在"}, status_code=404)


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


def _pivot_path(axis: list[dict[str, str]], values: dict[str, dict[str, Any]]) -> list[dict[str, Any]]:
    return [
        {
            "code": item["code"],
            "name": item["name"],
            "value": values.get(item["code"], {}).get("value", ""),
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
    return labels


def _pivot_detail_column_labels() -> dict[str, str]:
    """Return English column name -> Chinese KG label for drill detail headers."""
    ttl_path = _get_active_path()
    if not ttl_path or not ttl_path.exists():
        return {}
    return _pivot_detail_column_labels_cached(str(ttl_path), ttl_path.stat().st_mtime_ns)


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
        configure = [{"code": item["code"]} for item in measures]
        configure.extend({
            "code": item["code"],
            "order": {"sortType": 1 if item.get("isTime") else 0},
            "alias": "",
            "hasSubtotal": False,
        } for item in selected_dims)
        payload = {
            "configureList": configure,
            "filterList": _pivot_da_filters(_pivot_resolve_filters_for_measures(filters, measure_metas, catalog)),
            "pageSize": max(1, min(int(body.get("limit") or 1000), 10000)),
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
        return {
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
        return {
            "columns": columns,
            "records": records,
            "pageInfo": da_data.get("pageInfo") or {},
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
    IND = Namespace("http://indicator.lixiang.com/ontology#")

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
    from kg_builder.analysis.stats_analyzer import run_analysis

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

if __name__ == "__main__":
    import uvicorn
    uvicorn.run("web_app:app", host="0.0.0.0", port=8080, reload=False)
