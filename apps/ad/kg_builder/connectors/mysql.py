"""MySQL / Doris / StarRocks connector (pymysql driver)."""
from __future__ import annotations

from sqlalchemy.engine import URL

from .base import BaseConnector, DataSourceConfig


class MySQLConnector(BaseConnector):
    """Connector for MySQL / MariaDB / Apache Doris / StarRocks databases.

    When *config.database* is empty the URL is built without a database
    segment so that the connection can enumerate all available databases.

    Uses ``sqlalchemy.engine.URL.create()`` so that special characters in
    username / password (e.g. ``@``, ``#``, ``%``) are handled correctly.
    """

    def _build_url(self) -> URL:
        cfg = self.config
        return URL.create(
            drivername="mysql+pymysql",
            username=cfg.username,
            password=cfg.password,
            host=cfg.host,
            port=cfg.port,
            database=cfg.database or None,
            query={"charset": "utf8mb4", "connect_timeout": "10", "autocommit": "true"},
        )


# Doris/StarRocks use the MySQL wire protocol — reuse the same connector.
DorisConnector = MySQLConnector


def from_config(cfg: DataSourceConfig) -> MySQLConnector:
    return MySQLConnector(cfg)
