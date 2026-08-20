"""Build a privacy-safe semantic catalog from the business/source graphs."""

from __future__ import annotations

from collections import defaultdict
from datetime import datetime, timezone
import re
from typing import Any, Iterable

from rdflib import Graph, Namespace, RDF, RDFS
from rdflib.namespace import SKOS

from .dictionary import DictionaryBundle
from .models import AliasRecord, CatalogItem, CatalogSnapshot, CatalogValue
from .normalizer import (
    expression_values,
    is_safe_dimension_value,
    is_sensitive_dimension,
    normalize_text,
    unique_strings,
)


IND = Namespace("http://indicator.insightmind.com/ontology#")
LEGACY_IND = Namespace("http://indicator.lixiang.com/ontology#")
DB = Namespace("http://kg.local/db#")
_IND_NAMESPACES = (IND, LEGACY_IND)
_SNAPSHOT_VERSION = "1.2-scoped-public-enum"


def _predicates(local_name: str) -> tuple[Any, ...]:
    return tuple(namespace[local_name] for namespace in _IND_NAMESPACES)


def _objects(graph: Graph, subject: Any, *local_names: str) -> list[Any]:
    values: list[Any] = []
    seen: set[str] = set()
    for local_name in local_names:
        for predicate in _predicates(local_name):
            for value in graph.objects(subject, predicate):
                marker = str(value)
                if marker not in seen:
                    seen.add(marker)
                    values.append(value)
    return values


def _value(graph: Graph, subject: Any, *local_names: str) -> Any:
    values = _objects(graph, subject, *local_names)
    return values[0] if values else None


def _texts(graph: Graph, subject: Any, *local_names: str) -> list[str]:
    return unique_strings(_objects(graph, subject, *local_names))


def _bool_literal(value: Any, *, default: bool = True) -> bool:
    if value is None:
        return default
    return str(value).strip().lower() not in {"0", "false", "no", "off", "disabled"}


def _int_literal(value: Any) -> int | None:
    if value is None:
        return None
    try:
        return int(float(str(value)))
    except (TypeError, ValueError):
        return None


def _metadata_tables(metadata: dict[str, Any], *keys: str) -> set[str]:
    raw: Any = None
    for key in keys:
        if key in metadata:
            raw = metadata.get(key)
            break
    if isinstance(raw, str):
        values = re.split(r"[,;\s]+", raw)
    elif isinstance(raw, (list, tuple, set, frozenset)):
        values = raw
    else:
        values = ()
    return {str(value).strip() for value in values if str(value).strip()}


def _preferred_name(values: Iterable[str], fallback: str) -> tuple[str, list[str]]:
    names = unique_strings(values)
    if not names:
        return fallback, []
    chinese = [name for name in names if re.search(r"[\u4e00-\u9fff]", name)]
    primary = sorted(chinese or names, key=lambda value: (len(value), value))[0]
    return primary, [name for name in names if normalize_text(name) != normalize_text(primary)]


def _typed_subjects(graph: Graph, class_name: str) -> list[Any]:
    result: list[Any] = []
    seen: set[str] = set()
    for namespace in _IND_NAMESPACES:
        for subject in graph.subjects(RDF.type, namespace[class_name]):
            marker = str(subject)
            if marker not in seen:
                seen.add(marker)
                result.append(subject)
    return sorted(result, key=str)


def _table_name(graph: Graph, table: Any) -> str:
    return str(_value(graph, table, "tableName", "name") or "").strip()


def _enabled_apps(graph: Graph, node: Any, *relation_names: str) -> list[Any]:
    apps = _objects(graph, node, *relation_names)
    return [app for app in apps if _bool_literal(_value(graph, app, "available"), default=True)]


def _measure_tables(graph: Graph, node: Any) -> set[str]:
    tables: set[str] = set()
    for app in _enabled_apps(graph, node, "hasMeasureApp", "hasApplication"):
        table = _value(graph, app, "appliesToTable", "measFactTable", "onFactTable")
        name = _table_name(graph, table) if table is not None else ""
        if name:
            tables.add(name)
    return tables


def _dimension_apps(graph: Graph, node: Any) -> list[Any]:
    return _enabled_apps(graph, node, "hasDimApp", "hasApplication")


def _dimension_tables(graph: Graph, node: Any) -> set[str]:
    tables: set[str] = set()
    for app in _dimension_apps(graph, node):
        table = _value(graph, app, "dimFactTable", "onFactTable", "appliesToTable")
        name = _table_name(graph, table) if table is not None else ""
        if name:
            tables.add(name)
    for connection in _objects(graph, node, "hasDimtableConnect"):
        raw = str(_value(graph, connection, "tableName") or "").strip()
        if raw:
            tables.add(raw.rsplit(".", 1)[-1])
    return tables


def _graph_aliases(graph: Graph, node: Any) -> list[str]:
    values: list[Any] = []
    values.extend(_objects(graph, node, "alias", "synonym", "caption"))
    values.extend(graph.objects(node, SKOS.altLabel))
    values.extend(graph.objects(node, RDFS.label))
    return unique_strings(values)


def _category_text(graph: Graph, node: Any) -> str:
    categories = _objects(graph, node, "belongsToCategory", "inCategory")
    names: list[str] = []
    for category in categories:
        names.extend(_texts(graph, category, "name", "categoryName", "cnName"))
        names.extend(str(value) for value in graph.objects(category, RDFS.label))
    return "、".join(unique_strings(names))


def _build_items(graph: Graph) -> tuple[dict[str, CatalogItem], dict[str, Any]]:
    items: dict[str, CatalogItem] = {}
    nodes_by_code: dict[str, Any] = {}

    for semantic_type, class_name, prefix in (
        ("measure", "Measure", "MEAS_"),
        ("dimension", "Dimension", "DIM_"),
    ):
        for node in _typed_subjects(graph, class_name):
            codes = _texts(graph, node, "code")
            code = next((value for value in codes if value.upper().startswith(prefix)), "")
            if not code or not _bool_literal(_value(graph, node, "online"), default=True):
                continue
            tables = _measure_tables(graph, node) if semantic_type == "measure" else _dimension_tables(graph, node)
            if not tables:
                continue

            cn_values = _texts(graph, node, "cnName")
            en_values = _texts(graph, node, "enName")
            caption_values = _texts(graph, node, "caption")
            cn_name, alternate_names = _preferred_name(cn_values or caption_values, code)
            en_name = sorted(en_values, key=lambda value: (len(value), value))[0] if en_values else ""
            graph_aliases = unique_strings([
                *alternate_names,
                *en_values,
                *caption_values,
                *_graph_aliases(graph, node),
            ])
            definition_values = _texts(graph, node, "definition")
            description_values = _texts(graph, node, "description")
            item = CatalogItem(
                code=code,
                semantic_type=semantic_type,
                cn_name=cn_name,
                en_name=en_name,
                definition="；".join(definition_values),
                description="；".join(description_values),
                aliases=tuple(
                    value for value in graph_aliases
                    if normalize_text(value) not in {
                        normalize_text(code), normalize_text(cn_name), normalize_text(en_name)
                    }
                ),
                tables=frozenset(tables),
                hierarchy_code=str(_value(graph, node, "hierarchyCode") or ""),
                level_code=str(_value(graph, node, "levelCode") or ""),
                view_type=_int_literal(_value(graph, node, "viewTypeCode", "viewType")),
                unit=str(_value(graph, node, "unit") or ""),
                caliber="；".join(_texts(graph, node, "caliber")),
                metadata={
                    "category": _category_text(graph, node),
                    "rdfUri": str(node),
                },
            )
            if code in items:
                raise ValueError(
                    f"业务图谱存在重复语义编码 {code}: "
                    f"{items[code].metadata.get('rdfUri')} 与 {node}"
                )
            items[code] = item
            nodes_by_code[code] = node
    return items, nodes_by_code


def _table_column_samples(graph: Graph) -> dict[tuple[str, str], list[str]]:
    result: dict[tuple[str, str], list[str]] = defaultdict(list)
    for table in _typed_subjects(graph, "DwTable"):
        table_name = _table_name(graph, table)
        if not table_name:
            continue
        for column in _objects(graph, table, "hasColumn"):
            column_name = str(_value(graph, column, "columnName", "name") or "").strip()
            if not column_name:
                continue
            result[(table_name.lower(), column_name.lower())].extend(
                _texts(graph, column, "sampleValue")
            )
    return result


def _source_column_samples(
    source_graph: Graph | None,
    *,
    max_cardinality: int,
) -> dict[tuple[str, str], list[str]]:
    result: dict[tuple[str, str], list[str]] = defaultdict(list)
    if source_graph is None:
        return result
    table_by_column: dict[Any, Any] = {}
    for table in source_graph.subjects(RDF.type, DB.Table):
        for column in source_graph.objects(table, DB.containsColumn):
            table_by_column[column] = table
    for column in source_graph.subjects(RDF.type, DB.Column):
        table = source_graph.value(column, DB.belongsToTable) or table_by_column.get(column)
        table_name = str(
            source_graph.value(table, DB.tableName) or source_graph.value(table, DB.name) or ""
        ).strip() if table is not None else ""
        column_name = str(source_graph.value(column, DB.name) or "").strip()
        if not table_name or not column_name:
            continue
        cardinality = _int_literal(source_graph.value(column, DB.cardinality))
        if cardinality is not None and cardinality > max_cardinality:
            continue
        result[(table_name.lower(), column_name.lower())].extend(
            str(value) for value in source_graph.objects(column, DB.topValue)
        )
    return result


def _dimension_cardinalities(graph: Graph) -> dict[tuple[str, str], int]:
    result: dict[tuple[str, str], int] = {}
    for histogram in _typed_subjects(graph, "DimHistogram"):
        code = str(_value(graph, histogram, "histDimCode") or "").strip()
        table = str(_value(graph, histogram, "histTableName") or "").strip()
        count = _int_literal(_value(graph, histogram, "dimensionRowNum"))
        if code and table and count is not None:
            result[(code, table.lower())] = count
    return result


def _dimension_sample_values(
    graph: Graph,
    node: Any,
    item: CatalogItem,
    business_samples: dict[tuple[str, str], list[str]],
    source_samples: dict[tuple[str, str], list[str]],
    cardinalities: dict[tuple[str, str], int],
    *,
    max_values: int,
    max_cardinality: int,
) -> list[tuple[str, set[str], set[str]]]:
    if item.is_time or is_sensitive_dimension(item.code, item.cn_name, item.en_name):
        return []
    candidates: list[tuple[str, set[str], set[str]]] = [
        (value, set(item.tables), set()) for value in _texts(graph, node, "sampleValue")
    ]
    for app in _dimension_apps(graph, node):
        fact_table = _value(graph, app, "dimFactTable", "onFactTable", "appliesToTable")
        fact_table_name = _table_name(graph, fact_table) if fact_table is not None else ""
        applicable_tables = {fact_table_name} if fact_table_name else set()
        if fact_table_name:
            cardinality = cardinalities.get((item.code, fact_table_name.lower()))
            if cardinality is not None and cardinality > max_cardinality:
                continue
        dim_table = _value(graph, app, "dimTable")
        dim_table_name = _table_name(graph, dim_table) if dim_table is not None else ""
        fact_column = str(_value(graph, app, "dimFactColumn") or "").strip()
        display_column = str(_value(graph, app, "dimColumn") or "").strip()
        target_table = dim_table_name if dim_table_name and display_column else fact_table_name
        target_column = display_column or fact_column
        filter_safe = bool(fact_table_name and fact_column and not (dim_table_name and display_column))
        if target_table and target_column and not is_sensitive_dimension(
            item.code, item.cn_name, target_column
        ):
            key = (target_table.lower(), target_column.lower())
            safe_tables = set(applicable_tables) if filter_safe else set()
            candidates.extend(
                (value, set(applicable_tables), set(safe_tables))
                for value in business_samples.get(key, [])
            )
            candidates.extend(
                (value, set(applicable_tables), set(safe_tables))
                for value in source_samples.get(key, [])
            )
        candidates.extend(
            (value, set(applicable_tables), set())
            for value in expression_values(_value(graph, app, "dimColumnExpr"))
        )

    result_by_norm: dict[str, tuple[str, set[str], set[str]]] = {}
    for value, applicable_tables, safe_tables in candidates:
        norm = normalize_text(value)
        if not norm or not is_safe_dimension_value(value):
            continue
        previous = result_by_norm.get(norm)
        representative = min(
            (str(previous[0]), str(value)) if previous else (str(value),),
            key=lambda candidate: (normalize_text(candidate), candidate),
        )
        result_by_norm[norm] = (
            representative,
            set(applicable_tables) | (set(previous[1]) if previous else set()),
            set(safe_tables) | (set(previous[2]) if previous else set()),
        )
    return [
        row for _norm, row in sorted(
            result_by_norm.items(),
            key=lambda pair: (pair[0], pair[1][0]),
        )[:max_values]
    ]


def build_catalog_snapshot(
    graph: Graph,
    *,
    graph_hash: str,
    source_graph_hash: str = "",
    dictionary: DictionaryBundle,
    source_graph: Graph | None = None,
    max_values_per_dimension: int = 200,
    max_dimension_cardinality: int = 500,
) -> CatalogSnapshot:
    """Build a whitelisted catalog; connection/password triples are never traversed."""
    items, nodes_by_code = _build_items(graph)
    warnings = list(dictionary.warnings)
    alias_records: list[AliasRecord] = []

    def add_alias(term: str, item: CatalogItem, source: str, weight: float) -> None:
        norm = normalize_text(term)
        if len(norm) < 2:
            return
        alias_records.append(AliasRecord(
            term=term,
            normalized_term=norm,
            semantic_type=item.semantic_type,
            canonical_code=item.code,
            source=source,
            weight=weight,
            domain_code=item.domain_code,
        ))

    for item in items.values():
        add_alias(item.code, item, "kg_code", 1.0)
        add_alias(item.cn_name, item, "kg_name", 0.98)
        if item.en_name:
            add_alias(item.en_name, item, "kg_name", 0.97)
        for alias in item.aliases:
            add_alias(alias, item, "kg_alias", 0.94)

    item_aliases: dict[str, list[str]] = defaultdict(list)
    for row in dictionary.aliases:
        item = items.get(row.canonical_code)
        if item is None or item.semantic_type != row.semantic_type:
            warnings.append(f"语义别名目标不存在或类型不匹配: {row.term} -> {row.canonical_code}")
            continue
        add_alias(row.term, item, row.source, row.weight)
        item_aliases[item.code].append(row.term)

    for group in dictionary.legacy_groups:
        canonical = normalize_text(group.canonical_term)
        if not canonical:
            continue
        for item in items.values():
            searchable = normalize_text(" ".join((item.code, item.cn_name, item.en_name)))
            if canonical not in searchable:
                continue
            for alias in group.aliases:
                add_alias(alias, item, group.source, 0.86)
                item_aliases[item.code].append(alias)

    for code, aliases in item_aliases.items():
        item = items[code]
        items[code] = CatalogItem(
            **{
                **item.__dict__,
                "aliases": tuple(unique_strings([*item.aliases, *aliases])),
            }
        )

    business_samples = _table_column_samples(graph)
    source_samples = _source_column_samples(
        source_graph,
        max_cardinality=max_dimension_cardinality,
    )
    cardinalities = _dimension_cardinalities(graph)
    values_by_key: dict[tuple[str, str], CatalogValue] = {}
    active_value_policies: dict[str, str] = {}

    for code, item in items.items():
        if item.semantic_type != "dimension":
            continue
        policy_metadata = dictionary.value_policy_metadata.get(code) or {}
        required_tables = _metadata_tables(policy_metadata, "requiredTables", "required_tables")
        expected_graph_hash = str(
            policy_metadata.get("businessKgHash")
            or policy_metadata.get("business_kg_hash")
            or ""
        ).strip()
        if required_tables and not required_tables.issubset(set(item.tables)):
            warnings.append(f"维值策略事实表范围不匹配，已禁用: {code}")
            continue
        if expected_graph_hash and expected_graph_hash != graph_hash:
            warnings.append(f"维值策略业务图谱版本不匹配，已禁用: {code}")
            continue
        policy = str(
            dictionary.value_policies.get(code)
            or _value(graph, nodes_by_code[code], "valueIndexPolicy")
            or "UNKNOWN"
        ).strip().upper()
        active_value_policies[code] = policy
        if policy != "PUBLIC_ENUM":
            continue
        node = nodes_by_code[code]
        for value, applicable_tables, safe_tables in _dimension_sample_values(
            graph,
            node,
            item,
            business_samples,
            source_samples,
            cardinalities,
            max_values=max_values_per_dimension,
            max_cardinality=max_dimension_cardinality,
        ):
            values_by_key[(code, normalize_text(value))] = CatalogValue(
                dimension_code=code,
                canonical_value=value,
                source="kg_sample",
                tables=frozenset(applicable_tables),
                metadata={
                    "filterSafe": bool(
                        applicable_tables and applicable_tables.issubset(safe_tables)
                    ),
                    "safeTables": sorted(safe_tables),
                },
            )

    for row in dictionary.values:
        item = items.get(row.dimension_code)
        if item is None or item.semantic_type != "dimension":
            warnings.append(f"维值别名目标不存在: {row.term} -> {row.dimension_code}")
            continue
        policy = active_value_policies.get(row.dimension_code, "UNKNOWN")
        if policy != "PUBLIC_ENUM":
            warnings.append(f"维度未声明 PUBLIC_ENUM，忽略维值条目: {row.dimension_code}")
            continue
        if item.is_time or is_sensitive_dimension(item.code, item.cn_name):
            warnings.append(f"敏感或时间维度不允许写入维值字典: {row.dimension_code}")
            continue
        if not is_safe_dimension_value(row.term) or not is_safe_dimension_value(row.canonical_value):
            warnings.append(f"维值别名因隐私规则被拒绝: {row.dimension_code}")
            continue
        key = (row.dimension_code, normalize_text(row.canonical_value))
        existing = values_by_key.get(key)
        aliases = unique_strings([*(existing.aliases if existing else ()), row.term])
        alias_weights = dict((existing.metadata.get("aliasWeights") or {}) if existing else {})
        alias_sources = dict((existing.metadata.get("aliasSources") or {}) if existing else {})
        alias_tables = dict((existing.metadata.get("aliasTables") or {}) if existing else {})
        alias_safe_tables = dict(
            (existing.metadata.get("aliasSafeTables") or {}) if existing else {}
        )
        alias_key = normalize_text(row.term)
        alias_weights[alias_key] = max(
            float(alias_weights.get(alias_key) or 0.0), float(row.weight)
        )
        alias_sources[alias_key] = row.source
        row_filter_safe = _bool_literal(row.metadata.get("filterSafe"), default=False)
        existing_safe_tables = set(
            (existing.metadata.get("safeTables") or []) if existing else []
        )
        declared_tables = _metadata_tables(
            row.metadata,
            "tables",
            "factTables",
            "fact_tables",
        )
        if declared_tables:
            row_tables = declared_tables & set(item.tables)
            if not row_tables:
                warnings.append(
                    f"维值条目事实表范围不匹配，已忽略: {row.dimension_code}={row.term}"
                )
                continue
        elif existing:
            row_tables = set(existing.tables)
        else:
            row_tables = set(item.tables)
        row_safe_tables = existing_safe_tables & row_tables
        if row_filter_safe and (declared_tables or len(item.tables) == 1):
            row_safe_tables |= row_tables
        previous_alias_tables = set(alias_tables.get(alias_key) or [])
        previous_alias_safe_tables = set(alias_safe_tables.get(alias_key) or [])
        alias_tables[alias_key] = sorted(previous_alias_tables | row_tables)
        alias_safe_tables[alias_key] = sorted(
            previous_alias_safe_tables | row_safe_tables
        )
        applicable_tables = set(existing.tables) | row_tables if existing else row_tables
        safe_tables = existing_safe_tables | row_safe_tables
        values_by_key[key] = CatalogValue(
            dimension_code=row.dimension_code,
            canonical_value=row.canonical_value,
            aliases=tuple(aliases),
            source=row.source,
            tables=frozenset(applicable_tables),
            metadata={
                "weight": max(
                    float((existing.metadata.get("weight") or 0.0) if existing else 0.0),
                    float(row.weight),
                ),
                "aliasWeights": alias_weights,
                "aliasSources": alias_sources,
                "aliasTables": alias_tables,
                "aliasSafeTables": alias_safe_tables,
                **row.metadata,
                "filterSafe": bool(
                    applicable_tables and applicable_tables.issubset(safe_tables)
                ),
                "safeTables": sorted(safe_tables),
            },
        )

    if any(item.semantic_type == "dimension" for item in items.values()) and not values_by_key:
        warnings.append("业务图谱和语义字典中没有可安全索引的维值样本，维值召回已降级为空")

    deduped_aliases: list[AliasRecord] = []
    seen_aliases: set[tuple[str, str, str]] = set()
    for row in sorted(alias_records, key=lambda value: (-value.weight, value.canonical_code, value.term)):
        key = (row.normalized_term, row.semantic_type, row.canonical_code)
        if key not in seen_aliases:
            seen_aliases.add(key)
            deduped_aliases.append(row)

    return CatalogSnapshot(
        version=_SNAPSHOT_VERSION,
        graph_hash=graph_hash,
        source_graph_hash=source_graph_hash,
        dictionary_hash=dictionary.dictionary_hash,
        items=items,
        values=sorted(
            values_by_key.values(),
            key=lambda row: (row.dimension_code, normalize_text(row.canonical_value)),
        ),
        aliases=deduped_aliases,
        generated_at=datetime.now(timezone.utc).isoformat(timespec="seconds").replace("+00:00", "Z"),
        warnings=warnings,
    )
