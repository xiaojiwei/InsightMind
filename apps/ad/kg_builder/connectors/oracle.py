"""Oracle connector (cx_Oracle / oracledb driver)."""
from __future__ import annotations

from .base import BaseConnector, DataSourceConfig


class OracleConnector(BaseConnector):
    """Connector for Oracle Database.

    Supports both Service Name and SID connection modes.
    Prefers the thin python-oracledb driver; falls back to cx_Oracle.
    """

    def _build_url(self) -> str:
        cfg = self.config
        port = cfg.port or 1521

        # Try python-oracledb (thin mode, no instant client needed)
        try:
            import oracledb  # noqa: F401
            driver = "oracle+oracledb"
        except ImportError:
            driver = "oracle+cx_oracle"

        if cfg.service_name:
            dsn = f"{cfg.host}:{port}/?service_name={cfg.service_name}"
        elif cfg.sid:
            dsn = f"{cfg.host}:{port}/{cfg.sid}"
        else:
            dsn = f"{cfg.host}:{port}/{cfg.database}"

        return f"{driver}://{cfg.username}:{cfg.password}@{dsn}"


def from_config(cfg: DataSourceConfig) -> OracleConnector:
    return OracleConnector(cfg)
