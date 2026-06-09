"""Explicit relationship extraction from schema constraints."""
from __future__ import annotations

from dataclasses import dataclass
from typing import Dict, List, Optional

from kg_builder.entities.models import EntityGraph


# Relation type constants — mirror the OWL property names
REL_CONTAINS_SCHEMA  = "containsSchema"
REL_CONTAINS_TABLE   = "containsTable"
REL_CONTAINS_COLUMN  = "containsColumn"
REL_BELONGS_TO_TABLE   = "belongsToTable"
REL_BELONGS_TO_SCHEMA  = "belongsToSchema"
REL_REFERENCES       = "references"
REL_HAS_PK           = "hasPrimaryKey"
REL_HAS_INDEX        = "hasIndex"
REL_COVERS_COLUMN    = "coversColumn"


@dataclass
class Relation:
    """A directed relationship between two entities in the graph."""
    subject_id: str       # entity.id
    predicate: str        # relation type constant
    object_id: str        # entity.id
    confidence: float = 1.0
    properties: dict = None   # extra metadata (e.g. fk column names)

    def __post_init__(self):
        if self.properties is None:
            self.properties = {}


class ExplicitRelationExtractor:
    """
    Extract all relations that can be read directly from the schema:
      - Database CONTAINS_SCHEMA Schema
      - Schema CONTAINS_TABLE Table
      - Table CONTAINS_COLUMN Column
      - Column BELONGS_TO_TABLE Table
      - Table BELONGS_TO_SCHEMA Schema
      - Column REFERENCES Table  (foreign keys)
      - Table HAS_PK Column (single PK) / Table HAS_PK Constraint (composite PK)
      - Constraint COVERS_COLUMN Column
      - Table HAS_INDEX Index
    """

    def extract(self, entity_graph: EntityGraph) -> List[Relation]:
        relations: List[Relation] = []

        # ── Database → Schema ────────────────────────────────────────── #
        for schema in entity_graph.schemas:
            relations.append(Relation(
                subject_id=schema.db_id,
                predicate=REL_CONTAINS_SCHEMA,
                object_id=schema.id,
            ))

        # ── Schema → Table ───────────────────────────────────────────── #
        for table in entity_graph.tables:
            relations.append(Relation(
                subject_id=table.schema_id,
                predicate=REL_CONTAINS_TABLE,
                object_id=table.id,
            ))
            relations.append(Relation(
                subject_id=table.id,
                predicate=REL_BELONGS_TO_SCHEMA,
                object_id=table.schema_id,
            ))

        # ── Table → Column (CONTAINS / BELONGS) ─────────────────────── #
        for col in entity_graph.columns:
            relations.append(Relation(
                subject_id=col.table_id,
                predicate=REL_CONTAINS_COLUMN,
                object_id=col.id,
            ))
            relations.append(Relation(
                subject_id=col.id,
                predicate=REL_BELONGS_TO_TABLE,
                object_id=col.table_id,
            ))

        # ── Constraints ──────────────────────────────────────────────── #
        # Build lookup: table name (lower) → table_id, supports schema-qualified keys
        name_to_table_id: Dict[str, str] = {}
        for t in entity_graph.tables:
            name_to_table_id.setdefault(t.name.lower(), t.id)
            name_to_table_id.setdefault(t.normalized_name, t.id)
            # Also index under schema-qualified form: "schema.table"
            schema_name = t.schema_id.split("::")[-1].lower() if "::" in t.schema_id else ""
            if schema_name:
                name_to_table_id[f"{schema_name}.{t.name.lower()}"] = t.id
                name_to_table_id[f"{schema_name}.{t.normalized_name}"] = t.id

        for constraint in entity_graph.constraints:
            table_name = self._table_name(constraint.table_id)
            table_schema = self._table_schema(constraint.table_id)

            if constraint.constraint_type == "PRIMARY":
                constrained_col_ids = [
                    self._column_id(constraint.table_id, col_name)
                    for col_name in constraint.constrained_columns
                ]

                if len(constrained_col_ids) == 1:
                    # Single-column PK: direct Table → Column for simplicity
                    relations.append(Relation(
                        subject_id=constraint.table_id,
                        predicate=REL_HAS_PK,
                        object_id=constrained_col_ids[0],
                    ))
                else:
                    # Composite PK: Table → ConstraintEntity → Columns
                    # Table still gets hasPrimaryKey → each col for easy querying
                    for col_id in constrained_col_ids:
                        relations.append(Relation(
                            subject_id=constraint.table_id,
                            predicate=REL_HAS_PK,
                            object_id=col_id,
                        ))
                    # Additionally link Table → Constraint (to preserve joint semantics)
                    relations.append(Relation(
                        subject_id=constraint.table_id,
                        predicate=REL_HAS_PK,
                        object_id=constraint.id,
                        properties={"composite": True, "columns": constraint.constrained_columns},
                    ))

                # Constraint → coversColumn for each PK column
                for col_id in constrained_col_ids:
                    relations.append(Relation(
                        subject_id=constraint.id,
                        predicate=REL_COVERS_COLUMN,
                        object_id=col_id,
                    ))

            elif constraint.constraint_type == "FOREIGN":
                ref_table_name = (constraint.referred_table or "").lower()
                ref_schema     = (getattr(constraint, "referred_schema", None) or table_schema or "").lower()

                # Try schema-qualified lookup first, then bare name
                ref_table_id: Optional[str] = None
                if ref_schema:
                    ref_table_id = name_to_table_id.get(f"{ref_schema}.{ref_table_name}")
                if ref_table_id is None:
                    ref_table_id = name_to_table_id.get(ref_table_name)

                # Build a fallback external URI when the referred table is in another schema
                if ref_table_id is None and ref_table_name:
                    if ref_schema:
                        ref_table_id = f"table::{ref_schema}::{constraint.referred_table}"
                    else:
                        ref_table_id = f"table::external::{constraint.referred_table}"

                if ref_table_id:
                    for src_col_name in constraint.constrained_columns:
                        src_col_id = self._column_id(constraint.table_id, src_col_name)
                        relations.append(Relation(
                            subject_id=src_col_id,
                            predicate=REL_REFERENCES,
                            object_id=ref_table_id,
                            properties={
                                "referred_columns": constraint.referred_columns,
                                "referred_schema":  ref_schema or None,
                                "fk_name":          constraint.name,
                            },
                        ))

                # Constraint → coversColumn for each FK column
                for src_col_name in constraint.constrained_columns:
                    src_col_id = self._column_id(constraint.table_id, src_col_name)
                    relations.append(Relation(
                        subject_id=constraint.id,
                        predicate=REL_COVERS_COLUMN,
                        object_id=src_col_id,
                    ))

        # ── Indexes ──────────────────────────────────────────────────── #
        for idx in entity_graph.indexes:
            relations.append(Relation(
                subject_id=idx.table_id,
                predicate=REL_HAS_INDEX,
                object_id=idx.id,
                properties={"is_unique": idx.is_unique, "columns": idx.columns},
            ))

        return relations

    @staticmethod
    def _table_name(table_id: str) -> str:
        """Extract table name from id like 'table::schema::orders'."""
        parts = table_id.split("::")
        return parts[-1] if parts else table_id

    @staticmethod
    def _table_schema(table_id: str) -> str:
        """Extract schema name from id like 'table::schema::orders'."""
        parts = table_id.split("::")
        return parts[-2] if len(parts) >= 3 else ""

    @staticmethod
    def _column_id(table_id: str, column_name: str) -> str:
        """Build a column id that preserves the table schema."""
        if table_id.startswith("table::"):
            return table_id.replace("table::", "col::", 1) + f"::{column_name}"
        return f"col::{table_id}::{column_name}"
