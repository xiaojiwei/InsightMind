"""Schema parser — extracts structured metadata from a relational database."""
from __future__ import annotations

import re
from dataclasses import dataclass, field
from typing import Any, Dict, List, Optional

from sqlalchemy import inspect as sa_inspect

from kg_builder.connectors.base import BaseConnector


def _fix_encoding(s: Optional[str]) -> Optional[str]:
    """Fix mojibake caused by UTF-8 bytes mis-decoded as Windows-1252/cp1252.

    Reverses the corruption by re-encoding to cp1252 bytes then decoding as
    UTF-8.  Returns the original string unchanged if the round-trip fails,
    which safely handles already-correct ASCII / Latin text.
    """
    if not s:
        return s
    try:
        return s.encode("cp1252").decode("utf-8")
    except (UnicodeEncodeError, UnicodeDecodeError):
        return s


_DDL_COMMENT_RE = re.compile(r"""COMMENT\s+["']([^"']+)["']""", re.IGNORECASE)

# Matches a single column definition line, capturing col name and its COMMENT
# e.g.:  `dept_name` varchar(256) NULL COMMENT "部门名称",
_COL_COMMENT_RE = re.compile(
    r'`([^`]+)`\s+\S.*?COMMENT\s+"([^"]*)"',
    re.IGNORECASE,
)
# Table-level COMMENT at the end of DDL: ) COMMENT "xxx" DUPLICATE KEY ...
_TBL_COMMENT_RE = re.compile(
    r'\)\s*COMMENT\s+"([^"]*)"',
    re.IGNORECASE,
)


def _extract_table_comment(raw: Optional[str]) -> Optional[str]:
    """Return a clean table comment from *raw*.

    StarRocks/Doris returns the full DDL string (including DUPLICATE KEY,
    DISTRIBUTED BY, PROPERTIES …) from get_table_comment().  We extract
    just the COMMENT "…" value.  If *raw* looks like a normal short comment
    (no DDL keywords) it is returned as-is after encoding repair.
    """
    if not raw:
        return None
    # Short string with no DDL keywords → already a real comment
    if len(raw) < 200 and not re.search(
        r"DUPLICATE KEY|DISTRIBUTED BY|PROPERTIES|PRIMARY KEY", raw, re.I
    ):
        return _fix_encoding(raw.strip())
    # Extract first COMMENT "..." from DDL
    m = _DDL_COMMENT_RE.search(raw)
    if m:
        return _fix_encoding(m.group(1).strip())
    return None


@dataclass
class ColumnInfo:
    name: str
    data_type: str
    is_nullable: bool
    default: Optional[str]
    comment: Optional[str]
    is_pk: bool = False
    autoincrement: bool = False
    # filled by DataSampler later
    stats: Optional[Any] = None


@dataclass
class FKInfo:
    name: Optional[str]
    constrained_columns: List[str]
    referred_schema: Optional[str]
    referred_table: str
    referred_columns: List[str]


@dataclass
class IndexInfo:
    name: Optional[str]
    columns: List[str]
    is_unique: bool


@dataclass
class TableInfo:
    name: str
    schema: str
    comment: Optional[str]
    is_view: bool = False
    columns: List[ColumnInfo] = field(default_factory=list)
    primary_keys: List[str] = field(default_factory=list)
    foreign_keys: List[FKInfo] = field(default_factory=list)
    indexes: List[IndexInfo] = field(default_factory=list)
    # Preview/ABox rows. Keep this small because it is rendered in the UI and
    # turned into RDF individuals.
    sample_rows: List[Dict[str, Any]] = field(default_factory=list)
    # Full sampled rows for value-based relation detection and quality checks.
    value_sample_rows: List[Dict[str, Any]] = field(default_factory=list)


@dataclass
class SchemaInfo:
    db_name: str
    schema_name: str
    db_type: str
    tables: List[TableInfo] = field(default_factory=list)
    # Connection details (passed through to DatabaseEntity)
    host: str = ""
    port: int = 0
    username: str = ""
    password: str = ""
    database: str = ""     # actual DB/schema name (cfg.database)

    def get_table(self, name: str) -> Optional[TableInfo]:
        for t in self.tables:
            if t.name == name:
                return t
        return None


class SchemaParser:
    """Parse database schema metadata using SQLAlchemy Inspector."""

    # Databases that are internal/system and should not be analysed.
    _SYSTEM_DBS = {
        "information_schema", "mysql", "performance_schema", "sys",
        # Doris / StarRocks internal databases
        "_statistics_", "__internal_schema", "starrocks_monitor",
        "doris_monitor", "_doris_meta_",
    }

    def __init__(self, connector: BaseConnector) -> None:
        self.connector = connector

    def parse(self, schema_name: Optional[str] = None) -> SchemaInfo:
        """
        Parse all tables in *schema_name* and return a SchemaInfo object.
        If schema_name is None, uses the database name from config.
        """
        cfg = self.connector.config
        schema = schema_name or cfg.database or None
        insp = self.connector.get_inspector()

        schema_info = SchemaInfo(
            db_name=cfg.name,
            schema_name=schema or "",
            db_type=cfg.db_type,
            host=cfg.host,
            port=cfg.port,
            username=cfg.username,
            password=cfg.password,
            database=cfg.database,
        )

        table_names = self.connector.get_tables(schema=schema)
        for tname in table_names:
            table_info = self._parse_table(insp, tname, schema, is_view=False)
            schema_info.tables.append(table_info)

        view_names = self.connector.get_views(schema=schema)
        for vname in view_names:
            view_info = self._parse_table(insp, vname, schema, is_view=True)
            schema_info.tables.append(view_info)

        # Doris/StarRocks: SQLAlchemy can't parse double-quoted COMMENTs.
        # Enrich table/column comments from SHOW CREATE TABLE DDL.
        if cfg.db_type.lower() in ("doris", "starrocks", "mysql"):
            self._enrich_comments_from_ddl(schema_info, schema)

        return schema_info

    def parse_all_databases(self, log_fn=None) -> SchemaInfo:
        """
        Enumerate ALL accessible (non-system) databases on the server and
        merge their tables into a single SchemaInfo object.

        *log_fn* — optional callable(str) for progress messages.
        """
        cfg = self.connector.config

        all_dbs = self.connector.get_schemas()
        user_dbs = [
            db for db in all_dbs
            if db.lower() not in self._SYSTEM_DBS
        ]

        if log_fn:
            log_fn(f"共发现 {len(all_dbs)} 个数据库，过滤系统库后剩余 {len(user_dbs)} 个：{user_dbs}")

        master = SchemaInfo(
            db_name=cfg.name,
            schema_name="*",          # marker: all databases
            db_type=cfg.db_type,
            host=cfg.host,
            port=cfg.port,
            username=cfg.username,
            password=cfg.password,
            database="*",
        )

        for db in user_dbs:
            try:
                if log_fn:
                    log_fn(f"  解析数据库 [{db}] …")
                sub = self.parse(schema_name=db)
                master.tables.extend(sub.tables)
                if log_fn:
                    log_fn(f"  [{db}] → {len(sub.tables)} 张表/视图")
            except Exception as exc:
                if log_fn:
                    log_fn(f"  [{db}] 跳过（{exc}）")

        return master

    # ------------------------------------------------------------------ #
    # Private helpers
    # ------------------------------------------------------------------ #

    def _enrich_comments_from_ddl(self, schema_info: SchemaInfo, schema: Optional[str]) -> None:
        """Use SHOW CREATE TABLE to fill in missing table/column comments.

        Doris and StarRocks use double-quoted COMMENT strings which SQLAlchemy's
        MySQL dialect silently drops.  This method fetches the raw DDL and
        patches every TableInfo / ColumnInfo that has comment=None.
        """
        from sqlalchemy import text as _text
        engine = self.connector.connect()
        for table in schema_info.tables:
            fqn = f"`{schema}`.`{table.name}`" if schema else f"`{table.name}`"
            try:
                with engine.connect() as conn:
                    row = conn.execute(_text(f"SHOW CREATE TABLE {fqn}")).fetchone()
                if not row:
                    continue
                ddl = row[1]

                # Table-level COMMENT (appears after closing paren, before DUPLICATE KEY etc.)
                if table.comment is None:
                    m = _TBL_COMMENT_RE.search(ddl)
                    if m:
                        table.comment = _fix_encoding(m.group(1).strip())

                # Column-level COMMENTs
                col_map = {c.name: c for c in table.columns}
                for line in ddl.splitlines():
                    m = _COL_COMMENT_RE.search(line)
                    if not m:
                        continue
                    col_name, comment_text = m.group(1), m.group(2).strip()
                    col = col_map.get(col_name)
                    if col is not None and not col.comment and comment_text:
                        col.comment = _fix_encoding(comment_text)
            except Exception:
                pass

    def _parse_table(self, insp, tname: str, schema: Optional[str], is_view: bool = False) -> TableInfo:
        # Table comment
        # StarRocks/Doris quirk: get_table_comment() may return the full DDL
        # statement instead of just the COMMENT string.  Extract it manually.
        try:
            tcomment_row = insp.get_table_comment(tname, schema=schema)
            raw_comment = tcomment_row.get("text") if tcomment_row else None
            tcomment = _extract_table_comment(raw_comment)
        except Exception:
            tcomment = None

        # Primary keys
        try:
            pk_info = insp.get_pk_constraint(tname, schema=schema)
            pk_cols: List[str] = pk_info.get("constrained_columns", [])
        except Exception:
            pk_cols = []

        # Columns
        columns: List[ColumnInfo] = []
        try:
            raw_cols = insp.get_columns(tname, schema=schema)
        except Exception:
            raw_cols = []

        for col in raw_cols:
            col_name = col["name"]
            columns.append(
                ColumnInfo(
                    name=col_name,
                    data_type=str(col.get("type", "UNKNOWN")),
                    is_nullable=bool(col.get("nullable", True)),
                    default=str(col["default"]) if col.get("default") is not None else None,
                    comment=_fix_encoding(col.get("comment")),
                    is_pk=col_name in pk_cols,
                    autoincrement=bool(col.get("autoincrement", False)),
                )
            )

        # Foreign keys
        fks: List[FKInfo] = []
        try:
            for fk in insp.get_foreign_keys(tname, schema=schema):
                fks.append(
                    FKInfo(
                        name=fk.get("name"),
                        constrained_columns=fk.get("constrained_columns", []),
                        referred_schema=fk.get("referred_schema"),
                        referred_table=fk.get("referred_table", ""),
                        referred_columns=fk.get("referred_columns", []),
                    )
                )
        except Exception:
            pass

        # Indexes
        idxs: List[IndexInfo] = []
        try:
            for idx in insp.get_indexes(tname, schema=schema):
                idxs.append(
                    IndexInfo(
                        name=idx.get("name"),
                        columns=idx.get("column_names", []),
                        is_unique=bool(idx.get("unique", False)),
                    )
                )
        except Exception:
            pass

        return TableInfo(
            name=tname,
            schema=schema or "",
            comment=tcomment,
            is_view=is_view,
            columns=columns,
            primary_keys=pk_cols,
            foreign_keys=fks,
            indexes=idxs,
        )
