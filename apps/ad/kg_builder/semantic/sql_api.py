"""MySQL-protocol SQL facade for the AD semantic API."""

from __future__ import annotations

import asyncio
from dataclasses import dataclass
from typing import Any, Callable

import sqlglot
from sqlglot import exp

from .ad_api import AdSemanticService, normalize_member_key


class AdSqlError(ValueError):
    """Raised when a SQL statement cannot be mapped to AD QuerySpec."""


@dataclass
class AdSqlEngine:
    service_factory: Callable[[], AdSemanticService]
    database: str = "ad"
    table: str = "semantic"

    def schema(self) -> dict[str, dict[str, dict[str, str]]]:
        service = self.service_factory()
        catalog = service.catalog
        columns: dict[str, str] = {}
        for item in catalog.get("dimensions") or []:
            key = normalize_member_key(item.get("code") or "")
            columns[key] = "DATETIME" if item.get("isTime") else "TEXT"
        for item in catalog.get("measures") or []:
            key = normalize_member_key(item.get("code") or "")
            columns[key] = "DOUBLE"
        return {self.database: {self.table: columns}}

    def query(self, sql: str) -> tuple[list[tuple[Any, ...]], list[str]]:
        expression = sqlglot.parse_one(sql, read="mysql")
        if not isinstance(expression, exp.Select):
            raise AdSqlError("AD SQL API 当前只支持 SELECT 查询")
        self._validate_from(expression)
        query_spec, output_columns = self.to_query_spec(expression)
        result = self.service_factory().load(query_spec)
        rows = [
            tuple(row.get(member_name) for member_name, _label in output_columns)
            for row in result.get("data") or []
        ]
        return rows, [label for _member_name, label in output_columns]

    def to_query_spec(self, expression: exp.Select) -> tuple[dict[str, Any], list[tuple[str, str]]]:
        service = self.service_factory()
        measures: list[str] = []
        dimensions: list[str] = []
        output_columns: list[tuple[str, str]] = []
        aliases: dict[str, dict[str, Any]] = {}

        for projection in expression.expressions:
            member, label = self._projection_member(projection, service)
            if not member:
                continue
            aliases[label.lower()] = member
            member_name = service._code_to_member_name(member["code"])
            output_columns.append((member_name, label))
            if member["code"].startswith("MEAS_"):
                if member_name not in measures:
                    measures.append(member_name)
            elif member_name not in dimensions:
                dimensions.append(member_name)

        if not measures:
            raise AdSqlError("SELECT 中至少需要包含一个指标列")

        filters = self._where_filters(expression, service)
        order = self._order(expression, service, aliases)
        limit = self._limit(expression)
        query_spec: dict[str, Any] = {
            "measures": measures,
            "dimensions": dimensions,
            "filters": filters,
            "order": order,
            "limit": limit,
        }
        return query_spec, output_columns

    def _validate_from(self, expression: exp.Select) -> None:
        tables = list(expression.find_all(exp.Table))
        if not tables:
            return
        table_names = {str(table.name).lower() for table in tables}
        if table_names - {self.table.lower(), self.database.lower()}:
            raise AdSqlError(f"AD SQL API 只支持查询虚拟表 {self.database}.{self.table}")

    def _projection_member(
        self,
        projection: exp.Expression,
        service: AdSemanticService,
    ) -> tuple[dict[str, Any] | None, str]:
        alias = projection.alias_or_name
        node = projection.this if isinstance(projection, exp.Alias) else projection
        if isinstance(node, exp.Star):
            raise AdSqlError("AD SQL API 暂不支持 SELECT *，请显式选择指标和维度")
        if isinstance(node, exp.Column):
            member = self._resolve_column(node, service)
        elif isinstance(node, exp.Func):
            first = node.this or (node.expressions[0] if node.expressions else None)
            if not isinstance(first, exp.Column):
                raise AdSqlError("聚合函数当前只支持直接包裹一个语义列")
            member = self._resolve_column(first, service)
        else:
            raise AdSqlError(f"不支持的 SELECT 表达式: {node.sql(dialect='mysql')}")
        label = alias or normalize_member_key(member["code"])
        return member, label

    def _where_filters(self, expression: exp.Select, service: AdSemanticService) -> list[dict[str, Any]]:
        where = expression.args.get("where")
        if not where:
            return []
        return self._predicate_filters(where.this, service)

    def _predicate_filters(self, node: exp.Expression, service: AdSemanticService) -> list[dict[str, Any]]:
        if isinstance(node, exp.And):
            return self._predicate_filters(node.left, service) + self._predicate_filters(node.right, service)
        if isinstance(node, exp.Or):
            raise AdSqlError("AD SQL API 当前只支持 AND 连接的过滤条件")
        if isinstance(node, exp.Between):
            member = self._resolve_column(node.this, service)
            return [self._filter(member, "between", [self._literal(node.args["low"]), self._literal(node.args["high"])])]
        if isinstance(node, exp.In):
            member = self._resolve_column(node.this, service)
            return [self._filter(member, "equals", [self._literal(item) for item in node.expressions])]
        if isinstance(node, exp.Like):
            member = self._resolve_column(node.left, service)
            return [self._filter(member, "contains", [self._literal(node.right)])]
        comparisons = {
            exp.EQ: "equals",
            exp.NEQ: "notEquals",
            exp.GT: "gt",
            exp.GTE: "gte",
            exp.LT: "lt",
            exp.LTE: "lte",
        }
        for klass, operator in comparisons.items():
            if isinstance(node, klass):
                member = self._resolve_column(node.left, service)
                return [self._filter(member, operator, [self._literal(node.right)])]
        raise AdSqlError(f"不支持的 WHERE 条件: {node.sql(dialect='mysql')}")

    def _filter(self, member: dict[str, Any], operator: str, values: list[Any]) -> dict[str, Any]:
        return {
            "member": f"ad.{normalize_member_key(member['code'])}",
            "operator": operator,
            "values": [str(value) for value in values if value is not None],
        }

    def _order(
        self,
        expression: exp.Select,
        service: AdSemanticService,
        aliases: dict[str, dict[str, Any]],
    ) -> dict[str, str]:
        order = expression.args.get("order")
        if not order:
            return {}
        result = {}
        for ordered in order.expressions:
            target = ordered.this
            if not isinstance(target, exp.Column):
                continue
            member = aliases.get(target.name.lower()) or self._resolve_column(target, service)
            direction = "desc" if ordered.args.get("desc") else "asc"
            result[service._code_to_member_name(member["code"])] = direction
        return result

    def _limit(self, expression: exp.Select) -> int:
        limit = expression.args.get("limit")
        if not limit:
            return 1000
        value = limit.expression
        return max(1, min(int(self._literal(value) or 1000), 10000))

    def _resolve_column(self, column: exp.Expression, service: AdSemanticService) -> dict[str, Any]:
        if not isinstance(column, exp.Column):
            raise AdSqlError(f"预期语义列，实际为 {column.sql(dialect='mysql')}")
        member = service._resolve_member(column.name)
        if not member:
            raise AdSqlError(f"无法识别语义列 {column.name}")
        return member

    @staticmethod
    def _literal(node: exp.Expression) -> Any:
        if isinstance(node, exp.Literal):
            return node.to_py()
        if isinstance(node, exp.Boolean):
            return node.this
        if isinstance(node, exp.Null):
            return None
        return node.sql(dialect="mysql").strip("'")


def create_mysql_session_class(engine: AdSqlEngine):
    from mysql_mimic import Session

    class AdSqlSession(Session):
        async def query(self, expression: exp.Expression, sql: str, attrs: dict[str, str]):
            rows, columns = await asyncio.to_thread(engine.query, sql)
            return rows, columns

        async def schema(self):
            return engine.schema()

    return AdSqlSession


async def serve_mysql(engine: AdSqlEngine, host: str = "127.0.0.1", port: int = 13306) -> None:
    from mysql_mimic import MysqlServer

    server = MysqlServer(session_factory=create_mysql_session_class(engine))
    await server.serve_forever(host=host, port=port)
