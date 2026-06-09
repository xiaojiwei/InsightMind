"""OWL ontology schema definitions for the database knowledge graph."""
from __future__ import annotations

from rdflib import Graph, Literal, Namespace, RDF, RDFS, OWL, XSD
from rdflib.namespace import NamespaceManager


# ── Primary namespace ───────────────────────────────────────────────── #
DB = Namespace("http://kg.local/db#")

# ── Class URIs ──────────────────────────────────────────────────────── #
CLASS_DATABASE    = DB.Database
CLASS_SCHEMA      = DB.Schema
CLASS_TABLE       = DB.Table
CLASS_COLUMN      = DB.Column
CLASS_CONSTRAINT  = DB.Constraint
CLASS_INDEX       = DB.Index
CLASS_INDIVIDUAL  = DB.Individual      # ABox row individual

# ── Object property URIs ────────────────────────────────────────────── #
PROP_CONTAINS_SCHEMA   = DB.containsSchema    # Database → Schema
PROP_CONTAINS_TABLE    = DB.containsTable     # Schema   → Table
PROP_CONTAINS_COLUMN   = DB.containsColumn    # Table    → Column
PROP_BELONGS_TO_TABLE  = DB.belongsToTable
PROP_BELONGS_TO_SCHEMA = DB.belongsToSchema
PROP_REFERENCES        = DB.references
PROP_HAS_PK            = DB.hasPrimaryKey
PROP_HAS_INDEX         = DB.hasIndex
PROP_COVERS_COLUMN     = DB.coversColumn      # Constraint → Column
PROP_HAS_INDIVIDUAL    = DB.hasIndividual     # Table → Individual
PROP_FK_LINK           = DB.fkLink            # Individual → Individual (via FK)
PROP_SIMILAR_TO        = DB.similarTo
PROP_POTENTIAL_FK      = DB.potentialFK
PROP_CO_OCCURS_WITH    = DB.coOccursWith
PROP_SHARED_ENUM       = DB.sharedEnum    # Column ↔ Column (symmetric, shared enum domain)

# ── Datatype property URIs ───────────────────────────────────────────── #
DPROP_NAME          = DB.name
DPROP_DB_TYPE       = DB.dbType
DPROP_TABLE_NAME    = DB.tableName
DPROP_COLUMN_TYPE   = DB.columnType
DPROP_IS_NULLABLE   = DB.isNullable
DPROP_IS_PK         = DB.isPrimaryKey
DPROP_IS_UNIQUE     = DB.isUnique
DPROP_IS_VIEW       = DB.isView
DPROP_COMMENT       = DB.comment
DPROP_CONFIDENCE    = DB.confidence
DPROP_NULL_RATE     = DB.nullRate
DPROP_CARDINALITY   = DB.cardinality
DPROP_MIN_VAL       = DB.minValue
DPROP_MAX_VAL       = DB.maxValue
DPROP_AVG_VAL       = DB.avgValue
DPROP_AVG_LENGTH    = DB.avgLength
DPROP_MAX_LENGTH    = DB.maxLength
DPROP_TOP_VALUE     = DB.topValue             # singular — one triple per value
DPROP_PATTERN       = DB.detectedPattern      # singular — one triple per pattern
DPROP_NORMALIZED    = DB.normalizedName
DPROP_ROW_INDEX     = DB.rowIndex             # ABox row position
# Table warehouse classification
DPROP_TABLE_CATEGORY = DB.tableCategory      # fact | dimension | bridge | lookup | unknown
DPROP_ROW_COUNT      = DB.rowCount           # estimated row count
DPROP_FK_OUT_COUNT   = DB.fkOutCount         # how many tables this table references
DPROP_FK_IN_COUNT    = DB.fkInCount          # how many tables reference this table
# Database provenance
DPROP_HOST           = DB.host               # datasource host
DPROP_DATABASE       = DB.database           # actual database/schema name
DPROP_PORT           = DB.port               # connection port
DPROP_USERNAME       = DB.username           # connection username
DPROP_PASSWORD       = DB.password           # connection password


def build_owl_schema() -> Graph:
    """Return a new rdflib.Graph containing the full OWL TBox (ontology)."""
    g = Graph()
    g.bind("db",  DB)
    g.bind("owl", OWL)
    g.bind("rdfs", RDFS)
    g.bind("xsd", XSD)

    # ── OWL Ontology declaration ─────────────────────────────────────── #
    ontology_uri = DB[""]
    g.add((ontology_uri, RDF.type, OWL.Ontology))
    g.add((ontology_uri, RDFS.label, Literal("Database Knowledge Graph Ontology")))

    # ── Classes ──────────────────────────────────────────────────────── #
    for cls, label in [
        (CLASS_DATABASE,   "Database"),
        (CLASS_SCHEMA,     "Schema"),
        (CLASS_TABLE,      "Table"),
        (CLASS_COLUMN,     "Column"),
        (CLASS_CONSTRAINT, "Constraint"),
        (CLASS_INDEX,      "Index"),
        (CLASS_INDIVIDUAL, "Individual"),
    ]:
        g.add((cls, RDF.type, OWL.Class))
        g.add((cls, RDFS.label, Literal(label)))

    # Disjoint classes
    for a, b in [
        (CLASS_TABLE, CLASS_COLUMN),
        (CLASS_DATABASE, CLASS_TABLE),
        (CLASS_SCHEMA, CLASS_COLUMN),
    ]:
        g.add((a, OWL.disjointWith, b))

    # ── Object properties ────────────────────────────────────────────── #
    obj_props = [
        # Hierarchy traversal
        (PROP_CONTAINS_SCHEMA,   CLASS_DATABASE,   CLASS_SCHEMA,      "containsSchema"),
        (PROP_CONTAINS_TABLE,    CLASS_SCHEMA,     CLASS_TABLE,       "containsTable"),
        (PROP_CONTAINS_COLUMN,   CLASS_TABLE,      CLASS_COLUMN,      "containsColumn"),
        # Reverse traversal
        (PROP_BELONGS_TO_TABLE,  CLASS_COLUMN,     CLASS_TABLE,       "belongsToTable"),
        (PROP_BELONGS_TO_SCHEMA, CLASS_TABLE,      CLASS_SCHEMA,      "belongsToSchema"),
        # Constraints & indexes
        (PROP_REFERENCES,        CLASS_COLUMN,     CLASS_TABLE,       "references"),
        (PROP_HAS_PK,            CLASS_TABLE,      CLASS_COLUMN,      "hasPrimaryKey"),
        (PROP_HAS_INDEX,         CLASS_TABLE,      CLASS_INDEX,       "hasIndex"),
        (PROP_COVERS_COLUMN,     CLASS_CONSTRAINT, CLASS_COLUMN,      "coversColumn"),
        # ABox
        (PROP_HAS_INDIVIDUAL,    CLASS_TABLE,      CLASS_INDIVIDUAL,  "hasIndividual"),
        (PROP_FK_LINK,           CLASS_INDIVIDUAL, CLASS_INDIVIDUAL,  "fkLink"),
        # Implicit relations
        (PROP_SIMILAR_TO,        CLASS_COLUMN,     CLASS_COLUMN,      "similarTo"),
        (PROP_POTENTIAL_FK,      CLASS_COLUMN,     CLASS_COLUMN,      "potentialFK"),
        (PROP_CO_OCCURS_WITH,    CLASS_COLUMN,     CLASS_COLUMN,      "coOccursWith"),
        (PROP_SHARED_ENUM,       CLASS_COLUMN,     CLASS_COLUMN,      "sharedEnum"),
    ]
    for prop, domain, rng, label in obj_props:
        g.add((prop, RDF.type, OWL.ObjectProperty))
        g.add((prop, RDFS.domain, domain))
        g.add((prop, RDFS.range, rng))
        g.add((prop, RDFS.label, Literal(label)))

    # Symmetric properties
    g.add((PROP_SIMILAR_TO,     RDF.type, OWL.SymmetricProperty))
    g.add((PROP_CO_OCCURS_WITH, RDF.type, OWL.SymmetricProperty))
    g.add((PROP_SHARED_ENUM,    RDF.type, OWL.SymmetricProperty))

    # Inverse pairs
    g.add((PROP_CONTAINS_COLUMN, OWL.inverseOf, PROP_BELONGS_TO_TABLE))
    g.add((PROP_BELONGS_TO_TABLE, OWL.inverseOf, PROP_CONTAINS_COLUMN))
    g.add((PROP_CONTAINS_TABLE,  OWL.inverseOf, PROP_BELONGS_TO_SCHEMA))
    g.add((PROP_BELONGS_TO_SCHEMA, OWL.inverseOf, PROP_CONTAINS_TABLE))

    # ── Datatype (functional) properties ─────────────────────────────── #
    dp_defs = [
        (DPROP_NAME,       XSD.string,  True,  CLASS_DATABASE,   "name"),
        (DPROP_DB_TYPE,    XSD.string,  True,  CLASS_DATABASE,   "dbType"),
        (DPROP_TABLE_NAME, XSD.string,  True,  CLASS_TABLE,      "tableName"),
        (DPROP_COLUMN_TYPE,XSD.string,  True,  CLASS_COLUMN,     "columnType"),
        (DPROP_IS_NULLABLE,XSD.boolean, True,  CLASS_COLUMN,     "isNullable"),
        (DPROP_IS_PK,      XSD.boolean, True,  CLASS_COLUMN,     "isPrimaryKey"),
        (DPROP_IS_UNIQUE,  XSD.boolean, True,  CLASS_INDEX,      "isUnique"),
        (DPROP_IS_VIEW,    XSD.boolean, False, CLASS_TABLE,      "isView"),
        (DPROP_COMMENT,    XSD.string,  False, None,             "comment"),
        (DPROP_CONFIDENCE, XSD.float,   False, None,             "confidence"),
        (DPROP_NULL_RATE,  XSD.float,   False, CLASS_COLUMN,     "nullRate"),
        (DPROP_CARDINALITY,XSD.integer, False, CLASS_COLUMN,     "cardinality"),
        (DPROP_MIN_VAL,    XSD.float,   False, CLASS_COLUMN,     "minValue"),
        (DPROP_MAX_VAL,    XSD.float,   False, CLASS_COLUMN,     "maxValue"),
        (DPROP_AVG_VAL,    XSD.float,   False, CLASS_COLUMN,     "avgValue"),
        (DPROP_AVG_LENGTH, XSD.float,   False, CLASS_COLUMN,     "avgLength"),
        (DPROP_MAX_LENGTH, XSD.integer, False, CLASS_COLUMN,     "maxLength"),
        (DPROP_TOP_VALUE,  XSD.string,  False, CLASS_COLUMN,     "topValue"),
        (DPROP_PATTERN,    XSD.string,  False, CLASS_COLUMN,     "detectedPattern"),
        (DPROP_NORMALIZED, XSD.string,  False, None,             "normalizedName"),
        (DPROP_ROW_INDEX,  XSD.integer, False, CLASS_INDIVIDUAL, "rowIndex"),
        (DPROP_TABLE_CATEGORY, XSD.string,  False, CLASS_TABLE,  "tableCategory"),
        (DPROP_ROW_COUNT,      XSD.integer, False, CLASS_TABLE,  "rowCount"),
        (DPROP_FK_OUT_COUNT,   XSD.integer, False, CLASS_TABLE,  "fkOutCount"),
        (DPROP_FK_IN_COUNT,    XSD.integer, False, CLASS_TABLE,  "fkInCount"),
        (DPROP_HOST,           XSD.string,  False, CLASS_DATABASE,"host"),
        (DPROP_DATABASE,       XSD.string,  False, CLASS_DATABASE,"database"),
        (DPROP_PORT,           XSD.integer, False, CLASS_DATABASE,"port"),
        (DPROP_USERNAME,       XSD.string,  False, CLASS_DATABASE,"username"),
        (DPROP_PASSWORD,       XSD.string,  False, CLASS_DATABASE,"password"),
    ]
    for prop, xsd_type, functional, domain, label in dp_defs:
        g.add((prop, RDF.type, OWL.DatatypeProperty))
        if functional:
            g.add((prop, RDF.type, OWL.FunctionalProperty))
        if domain:
            g.add((prop, RDFS.domain, domain))
        g.add((prop, RDFS.range, xsd_type))
        g.add((prop, RDFS.label, Literal(label)))

    return g


# Pre-built schema for import use
OWL_SCHEMA_TRIPLES = build_owl_schema()
