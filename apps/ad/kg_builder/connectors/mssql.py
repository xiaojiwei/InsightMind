"""SQL Server connector (pyodbc driver)."""
from __future__ import annotations

from urllib.parse import quote_plus

from .base import BaseConnector, DataSourceConfig


class MSSQLConnector(BaseConnector):
    """Connector for Microsoft SQL Server."""

    def _build_url(self) -> str:
        cfg = self.config
        driver = cfg.driver or "ODBC Driver 17 for SQL Server"

        if cfg.windows_auth:
            odbc = (
                f"DRIVER={{{driver}}};"
                f"SERVER={cfg.host},{cfg.port};"
                f"DATABASE={cfg.database};"
                f"Trusted_Connection=yes;"
            )
        else:
            odbc = (
                f"DRIVER={{{driver}}};"
                f"SERVER={cfg.host},{cfg.port};"
                f"DATABASE={cfg.database};"
                f"UID={cfg.username};"
                f"PWD={cfg.password};"
            )
        return f"mssql+pyodbc:///?odbc_connect={quote_plus(odbc)}"


def from_config(cfg: DataSourceConfig) -> MSSQLConnector:
    return MSSQLConnector(cfg)
