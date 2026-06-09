"""Entity dataclasses — the six core entity types for the knowledge graph."""
from __future__ import annotations

from dataclasses import dataclass, field
from typing import Any, Dict, List, Optional


@dataclass
class DatabaseEntity:
    id: str                        # e.g. "db::prod_mysql"
    name: str                      # config.name (label)
    db_type: str                   # mysql | mssql | oracle
    host: str
    normalized_name: str = ""
    database: str = ""             # actual database/schema name (config.database)
    port: int = 0
    username: str = ""
    password: str = ""


@dataclass
class SchemaEntity:
    id: str                        # "schema::prod_mysql::sales_db"
    name: str
    db_id: str
    normalized_name: str = ""


@dataclass
class TableEntity:
    id: str                        # "table::sales_db::orders"
    name: str
    schema_id: str
    comment: Optional[str] = None
    row_count: Optional[int] = None
    normalized_name: str = ""
    synonyms: List[str] = field(default_factory=list)
    is_view: bool = False
    table_category: str = "unknown"   # fact | dimension | bridge | lookup | unknown


@dataclass
class ColumnEntity:
    id: str                        # "col::orders::order_id"
    name: str
    table_id: str
    data_type: str
    is_nullable: bool
    is_pk: bool
    default_value: Optional[str] = None
    comment: Optional[str] = None
    normalized_name: str = ""
    synonyms: List[str] = field(default_factory=list)
    # Stats from DataSampler
    null_rate: float = 0.0
    cardinality: int = 0
    min_val: Optional[float] = None
    max_val: Optional[float] = None
    avg_val: Optional[float] = None
    avg_length: Optional[float] = None
    max_length: Optional[int] = None
    top_values: List[Any] = field(default_factory=list)
    detected_patterns: List[str] = field(default_factory=list)


@dataclass
class ConstraintEntity:
    id: str
    name: Optional[str]
    table_id: str
    constraint_type: str           # PRIMARY | FOREIGN | UNIQUE | CHECK
    constrained_columns: List[str] = field(default_factory=list)
    referred_schema: Optional[str] = None
    referred_table: Optional[str] = None
    referred_columns: List[str] = field(default_factory=list)


@dataclass
class IndexEntity:
    id: str
    name: Optional[str]
    table_id: str
    columns: List[str] = field(default_factory=list)
    is_unique: bool = False


@dataclass
class IndividualEntity:
    """One row of data as an RDF individual (ABox instance)."""
    id: str                         # "individual::orders::1001"
    table_id: str                   # "table::schema::orders"
    table_name: str                 # "orders"
    pk_cols: List[str]              # PK column names (may be empty)
    pk_value: str                   # stringified composite PK
    row_index: int                  # 0-based position within sample
    schema_name: str = ""
    values: Dict[str, Any] = field(default_factory=dict)   # col_name → raw value
    label: str = ""                 # human-readable label (best name-like column)


@dataclass
class EntityGraph:
    """Container for all extracted entities."""
    databases: List[DatabaseEntity] = field(default_factory=list)
    schemas: List[SchemaEntity] = field(default_factory=list)
    tables: List[TableEntity] = field(default_factory=list)
    columns: List[ColumnEntity] = field(default_factory=list)
    constraints: List[ConstraintEntity] = field(default_factory=list)
    indexes: List[IndexEntity] = field(default_factory=list)
    individuals: List[IndividualEntity] = field(default_factory=list)

    def table_by_id(self, tid: str) -> Optional[TableEntity]:
        for t in self.tables:
            if t.id == tid:
                return t
        return None

    def columns_of(self, table_id: str) -> List[ColumnEntity]:
        return [c for c in self.columns if c.table_id == table_id]
