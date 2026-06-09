"""Entity extractor — builds EntityGraph from parsed SchemaInfo."""
from __future__ import annotations

import re
from pathlib import Path
from typing import Dict, List, Optional

import yaml

from kg_builder.parsers.schema_parser import SchemaInfo, TableInfo, ColumnInfo
from kg_builder.entities.models import (
    DatabaseEntity, SchemaEntity, TableEntity,
    ColumnEntity, ConstraintEntity, IndexEntity, IndividualEntity, EntityGraph,
)

_ZH_RE = re.compile(r'[\u4e00-\u9fff]')


def _has_chinese(s: str) -> bool:
    return bool(_ZH_RE.search(s or ""))


def _snake(name: str) -> str:
    """Normalize identifier to lowercase snake_case."""
    name = name.strip()
    name = re.sub(r"([a-z\d])([A-Z])", r"\1_\2", name)
    name = re.sub(r"[^a-zA-Z0-9\u4e00-\u9fff]+", "_", name)
    return name.lower().strip("_")


def _pinyin_initials(text: str) -> str:
    """Extract pinyin initials for Chinese characters (best-effort)."""
    try:
        from pypinyin import lazy_pinyin, Style
        initials = lazy_pinyin(text, style=Style.FIRST_LETTER)
        return "".join(initials)
    except ImportError:
        return ""


class SynonymMapper:
    """Map column/table names to canonical business terms via a YAML file."""

    def __init__(self, synonyms_path: Optional[str] = None) -> None:
        self._map: Dict[str, str] = {}   # alias → canonical
        path = synonyms_path or str(Path(__file__).parent.parent.parent / "synonyms.yaml")
        if Path(path).exists():
            self._load(path)

    def _load(self, path: str) -> None:
        with open(path, encoding="utf-8") as f:
            data = yaml.safe_load(f) or {}
        for canonical, aliases in data.items():
            self._map[canonical.lower()] = canonical
            for alias in (aliases or []):
                self._map[str(alias).lower()] = canonical

    def resolve(self, name: str) -> Optional[str]:
        key = _snake(name)
        return self._map.get(key) or self._map.get(name.lower())

    def aliases_for(self, name: str) -> List[str]:
        canonical = self.resolve(name) or name
        return [k for k, v in self._map.items() if v == canonical and k != canonical]


class EntityExtractor:
    """Convert parsed SchemaInfo into a fully normalised EntityGraph.

    When *translate=True* (default), calls LLMTranslator to fill in Chinese
    names for any entity that has no Chinese comment or synonym.
    Requires ANTHROPIC_API_KEY env var; silently falls back to original names
    if the key is missing or the API call fails.
    """

    def __init__(
        self,
        synonyms_path: Optional[str] = None,
        translate: bool = True,
    ) -> None:
        self.synonym_mapper = SynonymMapper(synonyms_path)
        self.translate = translate

    def extract(self, schema_info: SchemaInfo) -> EntityGraph:
        eg = EntityGraph()

        # ── Database entity ──────────────────────────────────────────── #
        db_id = f"db::{schema_info.db_name}"
        eg.databases.append(DatabaseEntity(
            id=db_id,
            name=schema_info.db_name,
            db_type=schema_info.db_type,
            host=schema_info.host,
            normalized_name=_snake(schema_info.db_name),
            database=schema_info.database,
            port=schema_info.port,
            username=schema_info.username,
            password=schema_info.password,
        ))

        # ── Schema entities ──────────────────────────────────────────── #
        schema_names = []
        seen_schema_names = set()
        for table in schema_info.tables:
            schema_name = table.schema or schema_info.schema_name or ""
            if schema_name not in seen_schema_names:
                seen_schema_names.add(schema_name)
                schema_names.append(schema_name)
        if not schema_names:
            schema_names = [schema_info.schema_name]

        schema_ids: Dict[str, str] = {}
        for schema_name in schema_names:
            schema_id = f"schema::{schema_info.db_name}::{schema_name}"
            schema_ids[schema_name] = schema_id
            eg.schemas.append(SchemaEntity(
                id=schema_id,
                name=schema_name,
                db_id=db_id,
                normalized_name=_snake(schema_name),
            ))

        # ── Tables, Columns, Constraints, Indexes ────────────────────── #
        for table in schema_info.tables:
            schema_name = table.schema or schema_info.schema_name or ""
            schema_id = schema_ids.setdefault(schema_name, f"schema::{schema_info.db_name}::{schema_name}")
            table_id = f"table::{schema_name}::{table.name}"
            te = self._build_table_entity(table, table_id, schema_id)
            eg.tables.append(te)

            for col in table.columns:
                col_id = f"col::{schema_name}::{table.name}::{col.name}"
                ce = self._build_column_entity(col, col_id, table_id)
                eg.columns.append(ce)

            if table.primary_keys:
                eg.constraints.append(ConstraintEntity(
                    id=f"pk::{schema_name}::{table.name}",
                    name=f"pk_{schema_name}_{table.name}",
                    table_id=table_id,
                    constraint_type="PRIMARY",
                    constrained_columns=table.primary_keys,
                ))

            for i, fk in enumerate(table.foreign_keys):
                eg.constraints.append(ConstraintEntity(
                    id=f"fk::{schema_name}::{table.name}::{i}",
                    name=fk.name,
                    table_id=table_id,
                    constraint_type="FOREIGN",
                    constrained_columns=fk.constrained_columns,
                    referred_schema=fk.referred_schema or schema_name,
                    referred_table=fk.referred_table,
                    referred_columns=fk.referred_columns,
                ))

            for idx in table.indexes:
                eg.indexes.append(IndexEntity(
                    id=f"idx::{schema_name}::{table.name}::{idx.name}",
                    name=idx.name,
                    table_id=table_id,
                    columns=idx.columns,
                    is_unique=idx.is_unique,
                ))

        # ── LLM translation pass ─────────────────────────────────────── #
        if self.translate:
            self._fill_chinese_labels(eg)

        # ── ABox: row-level individuals ───────────────────────────────── #
        self._build_individuals(eg, schema_info)

        return eg

    # ------------------------------------------------------------------ #
    # LLM translation
    # ------------------------------------------------------------------ #

    def _fill_chinese_labels(self, eg: EntityGraph) -> None:
        """For entities without any Chinese label, call LLMTranslator to fill in."""
        from kg_builder.utils.translator import LLMTranslator
        import logging as _logging
        _log = _logging.getLogger(__name__)

        translator = LLMTranslator()

        # Collect names that need translation and their comments as hints
        names_to_translate: List[str] = []
        hints: Dict[str, str] = {}

        for t in eg.tables:
            if not self._has_chinese_label(t.name, t.comment, t.synonyms):
                names_to_translate.append(t.name)
                if t.comment:
                    hints[t.name] = t.comment

        for c in eg.columns:
            if not self._has_chinese_label(c.name, c.comment, c.synonyms):
                names_to_translate.append(c.name)
                if c.comment:
                    hints[c.name] = c.comment

        if not names_to_translate:
            return

        # Deduplicate (same column name in multiple tables → translate once)
        unique_names = list(dict.fromkeys(names_to_translate))
        _log.info("LLM 翻译：共 %d 个唯一标识符（表名+列名）需要翻译…", len(unique_names))
        mapping = translator.translate(unique_names, hints=hints)

        # Apply translations: add Chinese name to synonyms so rdf_builder
        # picks it up as rdfs:label@zh
        for t in eg.tables:
            zh = mapping.get(t.name, "")
            if zh and _has_chinese(zh) and zh not in t.synonyms:
                t.synonyms.insert(0, zh)   # prepend so it's picked first

        for c in eg.columns:
            zh = mapping.get(c.name, "")
            if zh and _has_chinese(zh) and zh not in c.synonyms:
                c.synonyms.insert(0, zh)

    @staticmethod
    def _has_chinese_label(name: str, comment: Optional[str], synonyms: List[str]) -> bool:
        """Return True if this entity already has Chinese content anywhere."""
        return (
            _has_chinese(name)
            or _has_chinese(comment or "")
            or any(_has_chinese(s) for s in synonyms)
        )

    # ------------------------------------------------------------------ #
    # ABox: row-level individuals
    # ------------------------------------------------------------------ #

    _NAME_KEYWORDS = ("name", "title", "label", "desc", "称", "名", "标题", "描述")
    _SKIP_TYPES    = ("blob", "binary", "text", "longtext", "mediumtext", "json",
                      "tinyblob", "mediumblob", "longblob")

    def _build_individuals(self, eg: EntityGraph, schema_info) -> None:
        """Convert sample_rows into IndividualEntity objects in eg.individuals."""
        # Build table_name → TableEntity lookup
        table_map = {
            (t.schema_id.split("::")[-1], t.name): t
            for t in eg.tables
        }
        # Build col_name set per table (for skip-type filtering)
        col_type_map: dict = {}
        for col in eg.columns:
            parts = col.table_id.split("::")
            schema_name = parts[-2] if len(parts) >= 3 else ""
            tname = parts[-1]
            col_type_map.setdefault((schema_name, tname), {})[col.name] = col.data_type

        for table_info in schema_info.tables:
            if not table_info.sample_rows:
                continue
            schema_name = table_info.schema or schema_info.schema_name or ""
            te = table_map.get((schema_name, table_info.name))
            if te is None:
                continue

            pk_cols = table_info.primary_keys
            skip_cols = {
                cname
                for cname, dtype in col_type_map.get((schema_name, table_info.name), {}).items()
                if any(t in dtype.lower() for t in self._SKIP_TYPES)
            }

            for row_idx, row in enumerate(table_info.sample_rows[:20]):
                # Build PK value string
                if pk_cols:
                    pk_val = "_".join(str(row.get(c, "")) for c in pk_cols)
                else:
                    pk_val = str(row_idx)

                # Filter out skip-type columns and None values
                values = {
                    k: v for k, v in row.items()
                    if v is not None and k not in skip_cols
                }

                # Choose a human-readable label: prefer name-like columns
                label = pk_val
                for kw in self._NAME_KEYWORDS:
                    for col_name, val in values.items():
                        if kw in col_name.lower() and val:
                            label = str(val)
                            break
                    else:
                        continue
                    break

                ind = IndividualEntity(
                    id=f"individual::{schema_name}::{table_info.name}::{pk_val}",
                    table_id=te.id,
                    table_name=table_info.name,
                    pk_cols=pk_cols,
                    pk_value=pk_val,
                    row_index=row_idx,
                    schema_name=schema_name,
                    values=values,
                    label=label,
                )
                eg.individuals.append(ind)


    # ------------------------------------------------------------------ #
    # Entity builders
    # ------------------------------------------------------------------ #

    def _build_table_entity(
        self, table: TableInfo, table_id: str, schema_id: str
    ) -> TableEntity:
        norm = _snake(table.name)
        aliases = self.synonym_mapper.aliases_for(table.name)
        pinyin = _pinyin_initials(table.name)
        if pinyin and pinyin not in aliases:
            aliases.append(pinyin)
        return TableEntity(
            id=table_id,
            name=table.name,
            schema_id=schema_id,
            comment=table.comment,
            normalized_name=norm,
            synonyms=aliases,
            is_view=table.is_view,
        )

    def _build_column_entity(
        self, col: ColumnInfo, col_id: str, table_id: str
    ) -> ColumnEntity:
        norm = _snake(col.name)
        aliases = self.synonym_mapper.aliases_for(col.name)
        pinyin = _pinyin_initials(col.name)
        if pinyin and pinyin not in aliases:
            aliases.append(pinyin)

        stats = col.stats
        return ColumnEntity(
            id=col_id,
            name=col.name,
            table_id=table_id,
            data_type=col.data_type,
            is_nullable=col.is_nullable,
            is_pk=col.is_pk,
            default_value=col.default,
            comment=col.comment,
            normalized_name=norm,
            synonyms=aliases,
            null_rate=stats.null_rate if stats else 0.0,
            cardinality=stats.cardinality if stats else 0,
            min_val=stats.min_val if stats else None,
            max_val=stats.max_val if stats else None,
            avg_val=stats.avg_val if stats else None,
            avg_length=stats.avg_length if stats else None,
            max_length=stats.max_length if stats else None,
            top_values=stats.top_values if stats else [],
            detected_patterns=stats.detected_patterns if stats else [],
        )
