"""Abstract base connector and shared data config."""
from __future__ import annotations

import fnmatch
from abc import ABC, abstractmethod
from dataclasses import dataclass, field
from typing import List, Optional

from sqlalchemy import Engine, create_engine, text
from sqlalchemy import inspect as sa_inspect


@dataclass
class DataSourceConfig:
    """Unified data source configuration."""
    name: str
    db_type: str                         # mysql | mssql | oracle | sqlite
    host: str = "localhost"
    port: int = 3306
    database: str = ""
    username: str = ""
    password: str = ""
    # Oracle-specific
    service_name: str = ""
    sid: str = ""
    # MSSQL-specific
    windows_auth: bool = False
    driver: str = ""
    # Sampling
    sample_limit: int = 1000
    exclude_tables: List[str] = field(default_factory=list)
    # Extra SQLAlchemy kwargs
    connect_args: dict = field(default_factory=dict)

    def matches_exclude(self, table_name: str) -> bool:
        """Return True if the table should be excluded (supports glob patterns)."""
        return any(fnmatch.fnmatch(table_name, pat) for pat in self.exclude_tables)


class BaseConnector(ABC):
    """Abstract connector — every DB driver inherits from this."""

    def __init__(self, config: DataSourceConfig) -> None:
        self.config = config
        self._engine: Optional[Engine] = None

    # ------------------------------------------------------------------ #
    # Lifecycle
    # ------------------------------------------------------------------ #

    @abstractmethod
    def _build_url(self) -> str:
        """Return SQLAlchemy connection URL string."""

    def connect(self) -> Engine:
        if self._engine is None:
            url = self._build_url()
            self._engine = create_engine(
                url,
                connect_args=self.config.connect_args,
                pool_pre_ping=True,
                pool_recycle=1800,   # recycle before MySQL wait_timeout (default 8 h)
            )
        return self._engine

    def close(self) -> None:
        if self._engine:
            self._engine.dispose()
            self._engine = None

    def test_connection(self) -> bool:
        try:
            engine = self.connect()
            with engine.connect() as conn:
                conn.execute(text("SELECT 1"))
            return True
        except Exception:
            return False

    # ------------------------------------------------------------------ #
    # Schema discovery helpers (delegate to SQLAlchemy Inspector)
    # ------------------------------------------------------------------ #

    def get_inspector(self):
        return sa_inspect(self.connect())

    def get_schemas(self) -> List[str]:
        """Return all available schema names."""
        insp = self.get_inspector()
        try:
            return insp.get_schema_names()
        except Exception:
            return [self.config.database]

    def get_tables(self, schema: Optional[str] = None) -> List[str]:
        """Return table names in *schema*, honouring exclude_tables."""
        insp = self.get_inspector()
        tables = insp.get_table_names(schema=schema)
        return [t for t in tables if not self.config.matches_exclude(t)]

    def get_views(self, schema: Optional[str] = None) -> List[str]:
        """Return view names in *schema*, honouring exclude_tables."""
        insp = self.get_inspector()
        try:
            views = insp.get_view_names(schema=schema)
        except Exception:
            return []
        return [v for v in views if not self.config.matches_exclude(v)]

    def __enter__(self):
        self.connect()
        return self

    def __exit__(self, *_):
        self.close()
