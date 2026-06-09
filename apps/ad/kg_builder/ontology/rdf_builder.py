"""RDF graph builder — maps entities + relations → rdflib.Graph."""
from __future__ import annotations

import re
from pathlib import Path
from typing import List, Optional

from rdflib import Graph, Literal, URIRef, RDF, XSD
from rdflib.namespace import RDFS

_ZH_RE = re.compile(r'[\u4e00-\u9fff]')

def _has_chinese(s: str) -> bool:
    return bool(_ZH_RE.search(s))

from kg_builder.entities.models import (
    EntityGraph, DatabaseEntity, SchemaEntity,
    TableEntity, ColumnEntity, ConstraintEntity, IndexEntity,
    IndividualEntity,
)
from kg_builder.relations.explicit import Relation
from kg_builder.ontology.owl_schema import (
    DB, OWL_SCHEMA_TRIPLES,
    CLASS_DATABASE, CLASS_SCHEMA, CLASS_TABLE, CLASS_COLUMN,
    CLASS_CONSTRAINT, CLASS_INDEX, CLASS_INDIVIDUAL,
    PROP_CONTAINS_SCHEMA, PROP_CONTAINS_TABLE, PROP_CONTAINS_COLUMN,
    PROP_BELONGS_TO_TABLE, PROP_BELONGS_TO_SCHEMA,
    PROP_REFERENCES, PROP_HAS_PK, PROP_HAS_INDEX, PROP_COVERS_COLUMN,
    PROP_HAS_INDIVIDUAL, PROP_FK_LINK,
    PROP_SIMILAR_TO, PROP_POTENTIAL_FK, PROP_CO_OCCURS_WITH, PROP_SHARED_ENUM,
    DPROP_NAME, DPROP_DB_TYPE, DPROP_TABLE_NAME, DPROP_COLUMN_TYPE,
    DPROP_IS_NULLABLE, DPROP_IS_PK, DPROP_IS_UNIQUE, DPROP_IS_VIEW,
    DPROP_COMMENT, DPROP_CONFIDENCE, DPROP_NULL_RATE, DPROP_CARDINALITY,
    DPROP_MIN_VAL, DPROP_MAX_VAL, DPROP_AVG_VAL,
    DPROP_AVG_LENGTH, DPROP_MAX_LENGTH,
    DPROP_TOP_VALUE, DPROP_PATTERN, DPROP_NORMALIZED, DPROP_ROW_INDEX,
    DPROP_TABLE_CATEGORY, DPROP_ROW_COUNT, DPROP_FK_OUT_COUNT, DPROP_FK_IN_COUNT,
    DPROP_HOST, DPROP_DATABASE, DPROP_PORT, DPROP_USERNAME, DPROP_PASSWORD,
)


# Map from relation predicate string → OWL property URI
_PRED_MAP = {
    "containsSchema":   PROP_CONTAINS_SCHEMA,
    "containsTable":    PROP_CONTAINS_TABLE,
    "containsColumn":   PROP_CONTAINS_COLUMN,
    "belongsToTable":   PROP_BELONGS_TO_TABLE,
    "belongsToSchema":  PROP_BELONGS_TO_SCHEMA,
    "references":       PROP_REFERENCES,
    "hasPrimaryKey":    PROP_HAS_PK,
    "hasIndex":         PROP_HAS_INDEX,
    "coversColumn":     PROP_COVERS_COLUMN,
    "similarTo":        PROP_SIMILAR_TO,
    "potentialFK":      PROP_POTENTIAL_FK,
    "coOccursWith":     PROP_CO_OCCURS_WITH,
    "sharedEnum":       PROP_SHARED_ENUM,
}


def _uri(entity_id: str) -> URIRef:
    """Convert an entity id string to a URI-safe URIRef."""
    safe = entity_id.replace("::", "/").replace(" ", "_")
    return URIRef(f"http://kg.local/instance/{safe}")


def _column_id_for_table(table_id: str, column_name: str) -> str:
    """Build a schema-qualified column id for a given table id."""
    if table_id.startswith("table::"):
        return table_id.replace("table::", "col::", 1) + f"::{column_name}"
    return f"col::{table_id}::{column_name}"


class RDFBuilder:
    """Build and manage an rdflib Knowledge Graph."""

    def __init__(self, include_owl_schema: bool = True) -> None:
        self._g = Graph()
        self._g.bind("db", DB)
        self._g.bind("xsd", XSD)
        if include_owl_schema:
            for triple in OWL_SCHEMA_TRIPLES:
                self._g.add(triple)

    @property
    def graph(self) -> Graph:
        return self._g

    # ------------------------------------------------------------------ #
    # Build
    # ------------------------------------------------------------------ #

    def build(
        self,
        entity_graph: EntityGraph,
        relations: List[Relation],
    ) -> Graph:
        """Populate RDF graph from entities and relations."""
        self._add_entities(entity_graph)
        self._add_relations(relations)
        self._add_individuals(entity_graph)
        return self._g

    # ------------------------------------------------------------------ #
    # Entity → triples
    # ------------------------------------------------------------------ #

    def _add_entities(self, eg: EntityGraph) -> None:
        for db in eg.databases:
            self._add_database(db)
        for s in eg.schemas:
            self._add_schema(s)
        for t in eg.tables:
            self._add_table(t)
        for c in eg.columns:
            self._add_column(c)
        for con in eg.constraints:
            self._add_constraint(con)
        for idx in eg.indexes:
            self._add_index(idx)

    def _add_database(self, db: DatabaseEntity) -> None:
        u = _uri(db.id)
        self._g.add((u, RDF.type, CLASS_DATABASE))
        self._g.add((u, DPROP_NAME,       Literal(db.name)))
        self._g.add((u, DPROP_DB_TYPE,    Literal(db.db_type)))
        self._g.add((u, DPROP_NORMALIZED, Literal(db.normalized_name)))
        self._g.add((u, RDFS.label,       Literal(db.name)))
        if db.host:
            self._g.add((u, DPROP_HOST, Literal(db.host)))
        if db.database:
            self._g.add((u, DPROP_DATABASE, Literal(db.database)))
        if db.port:
            self._g.add((u, DPROP_PORT, Literal(db.port, datatype=XSD.integer)))
        if db.username:
            self._g.add((u, DPROP_USERNAME, Literal(db.username)))
        if db.password:
            self._g.add((u, DPROP_PASSWORD, Literal(db.password)))

    def _add_schema(self, s: SchemaEntity) -> None:
        u = _uri(s.id)
        self._g.add((u, RDF.type, CLASS_SCHEMA))
        self._g.add((u, DPROP_NAME, Literal(s.name)))
        self._g.add((u, DPROP_NORMALIZED, Literal(s.normalized_name)))
        self._g.add((u, RDFS.label, Literal(s.name)))
        # Database → containsSchema → Schema  (correct hierarchy)
        self._g.add((_uri(s.db_id), PROP_CONTAINS_SCHEMA, u))

    def _add_table(self, t: TableEntity) -> None:
        u = _uri(t.id)
        self._g.add((u, RDF.type, CLASS_TABLE))
        self._g.add((u, DPROP_TABLE_NAME, Literal(t.name)))
        self._g.add((u, DPROP_NORMALIZED, Literal(t.normalized_name)))
        self._g.add((u, RDFS.label, Literal(t.name)))
        self._g.add((u, DPROP_IS_VIEW, Literal(t.is_view, datatype=XSD.boolean)))
        # Warehouse category + row count
        self._g.add((u, DPROP_TABLE_CATEGORY, Literal(t.table_category)))
        if t.row_count is not None:
            self._g.add((u, DPROP_ROW_COUNT, Literal(t.row_count, datatype=XSD.integer)))
        # FK counts (set by table_classifier via side-channel attribute)
        fk_out = getattr(t, "_fk_out_count", 0)
        fk_in  = getattr(t, "_fk_in_count",  0)
        self._g.add((u, DPROP_FK_OUT_COUNT, Literal(fk_out, datatype=XSD.integer)))
        self._g.add((u, DPROP_FK_IN_COUNT,  Literal(fk_in,  datatype=XSD.integer)))
        if t.comment:
            self._g.add((u, DPROP_COMMENT, Literal(t.comment)))
            if _has_chinese(t.comment):
                self._g.add((u, RDFS.label, Literal(t.comment, lang="zh")))
        for alias in t.synonyms:
            self._g.add((u, RDFS.label, Literal(alias)))
            if _has_chinese(alias):
                self._g.add((u, RDFS.label, Literal(alias, lang="zh")))

    def _add_column(self, c: ColumnEntity) -> None:
        u = _uri(c.id)
        self._g.add((u, RDF.type, CLASS_COLUMN))
        self._g.add((u, DPROP_NAME, Literal(c.name)))
        self._g.add((u, DPROP_COLUMN_TYPE, Literal(c.data_type)))
        self._g.add((u, DPROP_IS_NULLABLE, Literal(c.is_nullable, datatype=XSD.boolean)))
        self._g.add((u, DPROP_IS_PK, Literal(c.is_pk, datatype=XSD.boolean)))
        self._g.add((u, DPROP_NORMALIZED, Literal(c.normalized_name)))
        self._g.add((u, RDFS.label, Literal(c.name)))
        if c.comment:
            self._g.add((u, DPROP_COMMENT, Literal(c.comment)))
            if _has_chinese(c.comment):
                self._g.add((u, RDFS.label, Literal(c.comment, lang="zh")))
        if c.null_rate:
            self._g.add((u, DPROP_NULL_RATE, Literal(c.null_rate, datatype=XSD.float)))
        if c.cardinality:
            self._g.add((u, DPROP_CARDINALITY, Literal(c.cardinality, datatype=XSD.integer)))
        if c.min_val is not None:
            self._g.add((u, DPROP_MIN_VAL, Literal(c.min_val, datatype=XSD.float)))
        if c.max_val is not None:
            self._g.add((u, DPROP_MAX_VAL, Literal(c.max_val, datatype=XSD.float)))
        if c.avg_val is not None:
            self._g.add((u, DPROP_AVG_VAL, Literal(c.avg_val, datatype=XSD.float)))
        if c.avg_length is not None:
            self._g.add((u, DPROP_AVG_LENGTH, Literal(c.avg_length, datatype=XSD.float)))
        if c.max_length is not None:
            self._g.add((u, DPROP_MAX_LENGTH, Literal(c.max_length, datatype=XSD.integer)))
        # top_values: one triple per value (enables SPARQL exact matching)
        for val in c.top_values:
            self._g.add((u, DPROP_TOP_VALUE, Literal(str(val))))
        # detected_patterns: one triple per pattern
        for pat in c.detected_patterns:
            self._g.add((u, DPROP_PATTERN, Literal(pat)))
        for alias in c.synonyms:
            self._g.add((u, RDFS.label, Literal(alias)))
            if _has_chinese(alias):
                self._g.add((u, RDFS.label, Literal(alias, lang="zh")))

    def _add_constraint(self, con: ConstraintEntity) -> None:
        u = _uri(con.id)
        self._g.add((u, RDF.type, CLASS_CONSTRAINT))
        self._g.add((u, DPROP_NAME, Literal(con.constraint_type)))
        if con.name:
            self._g.add((u, RDFS.label, Literal(con.name)))
        # coversColumn: one triple per constrained column
        for col_name in con.constrained_columns:
            col_uri = _uri(_column_id_for_table(con.table_id, col_name))
            self._g.add((u, PROP_COVERS_COLUMN, col_uri))
        # For FK constraints, also record the referred table
        if con.constraint_type == "FOREIGN" and con.referred_table:
            if con.referred_schema:
                ref_id = f"table::{con.referred_schema}::{con.referred_table}"
            else:
                ref_id = f"table::external::{con.referred_table}"
            ref_uri = _uri(ref_id)
            self._g.add((u, PROP_REFERENCES, ref_uri))
            # Also emit col → references → table directly so SPARQL/graph queries work
            for col_name in con.constrained_columns:
                col_uri = _uri(_column_id_for_table(con.table_id, col_name))
                self._g.add((col_uri, PROP_REFERENCES, ref_uri))

    def _add_index(self, idx: IndexEntity) -> None:
        u = _uri(idx.id)
        self._g.add((u, RDF.type, CLASS_INDEX))
        self._g.add((u, DPROP_IS_UNIQUE, Literal(idx.is_unique, datatype=XSD.boolean)))
        if idx.name:
            self._g.add((u, RDFS.label, Literal(idx.name)))
        if idx.columns:
            self._g.add((u, DPROP_NAME, Literal(",".join(idx.columns))))

    # ------------------------------------------------------------------ #
    # ABox individuals → triples
    # ------------------------------------------------------------------ #

    def _add_individuals(self, eg: EntityGraph) -> None:
        """Add row-level ABox individuals to the graph.

        Each IndividualEntity becomes:
          ind  rdf:type  CLASS_INDIVIDUAL
          ind  rdf:type  <table URI>          (typed as its table class)
          ind  rdfs:label  <label>
          ind  db:rowIndex  <int>
          ind  <col URI>  <value literal>     (one triple per column value)
          table  db:hasIndividual  ind

        FK links between individuals are resolved after all individuals are
        added using a (schema_name, table_name, pk_value) lookup.
        """
        if not eg.individuals:
            return

        # Build (schema_name, table_name, pk_value) → individual URI for FK resolution
        pk_lookup: dict = {}
        for ind in eg.individuals:
            pk_lookup[(ind.schema_name, ind.table_name, ind.pk_value)] = _uri(ind.id)

        # Build (schema_name, table_name) → set of FK refs
        # from ConstraintEntity list
        fk_map: dict = {}
        for con in eg.constraints:
            if con.constraint_type == "FOREIGN" and con.referred_table:
                parts = con.table_id.split("::")
                schema_name = parts[-2] if len(parts) >= 3 else ""
                tname = parts[-1]
                fk_map.setdefault((schema_name, tname), []).append(
                    (con.constrained_columns, con.referred_schema or schema_name,
                     con.referred_table, con.referred_columns)
                )

        for ind in eg.individuals:
            ind_uri = _uri(ind.id)
            table_uri = _uri(ind.table_id)

            # Type assertions
            self._g.add((ind_uri, RDF.type, CLASS_INDIVIDUAL))
            self._g.add((ind_uri, RDF.type, table_uri))

            # Label and row index
            if ind.label:
                self._g.add((ind_uri, RDFS.label, Literal(ind.label)))
            self._g.add((ind_uri, DPROP_ROW_INDEX, Literal(ind.row_index, datatype=XSD.integer)))

            # Table → hasIndividual → individual
            self._g.add((table_uri, PROP_HAS_INDIVIDUAL, ind_uri))

            # Column values as typed literals
            for col_name, val in ind.values.items():
                if val is None:
                    continue
                col_uri = _uri(_column_id_for_table(ind.table_id, col_name))
                # Try numeric literal first, fall back to string
                try:
                    float_val = float(val)
                    if isinstance(val, int) or (isinstance(val, str) and "." not in val):
                        self._g.add((ind_uri, col_uri, Literal(int(float_val), datatype=XSD.integer)))
                    else:
                        self._g.add((ind_uri, col_uri, Literal(float_val, datatype=XSD.float)))
                except (TypeError, ValueError):
                    self._g.add((ind_uri, col_uri, Literal(str(val))))

            # FK links: individual → fkLink → referred individual
            for fk_cols, ref_schema, ref_table, ref_cols in fk_map.get((ind.schema_name, ind.table_name), []):
                if not fk_cols or not ref_cols:
                    continue
                fk_val = "_".join(str(ind.values.get(c, "")) for c in fk_cols)
                ref_uri = pk_lookup.get((ref_schema or ind.schema_name, ref_table, fk_val))
                if ref_uri:
                    self._g.add((ind_uri, PROP_FK_LINK, ref_uri))

    # ------------------------------------------------------------------ #
    # Relation → triples
    # ------------------------------------------------------------------ #

    def _add_relations(self, relations: List[Relation]) -> None:
        for rel in relations:
            pred_uri = _PRED_MAP.get(rel.predicate)
            if pred_uri is None:
                continue
            s = _uri(rel.subject_id)
            o = _uri(rel.object_id)
            self._g.add((s, pred_uri, o))

            # Attach confidence as reified triple annotation using a blank node
            if rel.confidence < 1.0:
                from rdflib import BNode
                stmt = BNode()
                self._g.add((stmt, RDF.type, RDF.Statement))
                self._g.add((stmt, RDF.subject, s))
                self._g.add((stmt, RDF.predicate, pred_uri))
                self._g.add((stmt, RDF.object, o))
                self._g.add((stmt, DPROP_CONFIDENCE,
                              Literal(rel.confidence, datatype=XSD.float)))

    # ------------------------------------------------------------------ #
    # Reasoning
    # ------------------------------------------------------------------ #

    def apply_reasoning(self) -> "RDFBuilder":
        """Apply OWL-RL forward-chaining reasoning (owlrl) in-place."""
        try:
            import owlrl
            owlrl.DeductiveClosure(owlrl.OWLRL_Semantics).expand(self._g)
        except ImportError:
            pass
        return self

    # ------------------------------------------------------------------ #
    # Serialization
    # ------------------------------------------------------------------ #

    def save(self, path: str, fmt: str = "turtle") -> None:
        """Serialize graph to file.  fmt: 'turtle' | 'n3' | 'nt' | 'json-ld'"""
        Path(path).parent.mkdir(parents=True, exist_ok=True)
        self._g.serialize(destination=path, format=fmt)

    def to_string(self, fmt: str = "turtle") -> str:
        return self._g.serialize(format=fmt)

    def to_jsonld(self) -> str:
        return self._g.serialize(format="json-ld", indent=2)
