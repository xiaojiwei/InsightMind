package com.graphinsight.indicator.graph;

/**
 * Namespace constants for the Indicator Platform OWL ontology.
 * <p>
 * Ontology base: {@value #NS}
 * Instance base: {@value #INST}
 */
public final class IndicatorOntology {

    /** Ontology namespace (TBox) */
    public static final String NS   = "http://indicator.xiaojw.com/ontology#";
    /** Instance namespace (ABox) */
    public static final String INST = "http://indicator.xiaojw.com/instance/";

    // ── Classes ─────────────────────────────────────────────────────────────

    public static final String CLASS_MEASURE         = NS + "Measure";
    public static final String CLASS_DIMENSION       = NS + "Dimension";
    public static final String CLASS_DW_TABLE        = NS + "DwTable";
    public static final String CLASS_CATEGORY        = NS + "Category";
    public static final String CLASS_MEASURE_APP     = NS + "MeasureApp";
    public static final String CLASS_DIMENSION_APP   = NS + "DimensionApp";
    public static final String CLASS_TABLE_HISTOGRAM = NS + "TableHistogram";
    public static final String CLASS_DIM_HISTOGRAM   = NS + "DimHistogram";

    // ── Shared Datatype Properties ───────────────────────────────────────────

    /** Unique business code (Measure, Dimension, Category) */
    public static final String CODE             = NS + "code";
    /** Chinese name */
    public static final String CN_NAME          = NS + "cnName";
    /** English name / alias */
    public static final String EN_NAME          = NS + "enName";
    /** Display caption */
    public static final String CAPTION          = NS + "caption";
    /** Definition text */
    public static final String DEFINITION       = NS + "definition";
    /** Description text */
    public static final String DESCRIPTION      = NS + "description";

    // ── Measure Properties ───────────────────────────────────────────────────

    /**
     * MeasureType code: 0=ORIGIN, 1=DERIVED, 2=EXTENDED, 3=GROUP
     */
    public static final String MEAS_TYPE_CODE   = NS + "measTypeCode";
    /** ViewType code on Measure (e.g. 0=CHARACTER, 1=DAY …) */
    public static final String VIEW_TYPE_CODE   = NS + "viewTypeCode";

    // ── MeasureApp Properties ────────────────────────────────────────────────

    /**
     * Apply-type code on MeasureApp: 0=ORIGIN, 1=DERIVED, 2=EXTENDED
     */
    public static final String APPLY_TYPE_CODE  = NS + "applyTypeCode";
    /** Aggregation / formula expression */
    public static final String EXPRESSION       = NS + "expression";
    /** Fact-table column name (metric value column) */
    public static final String FACT_COLUMN      = NS + "factColumn";
    /** WHERE clause condition string */
    public static final String WHERE_CONDITION  = NS + "whereCondition";
    /** Whether the fact table has a date partition column */
    public static final String HAS_COLUMN_DT    = NS + "hasColumnDT";

    // ── DimensionApp Properties ──────────────────────────────────────────────

    /**
     * DimType code on Dimension: 0=DEGENERATE, 1=STD_WITHOUT_TABLE,
     * 2=STD_WITH_TABLE, 4=CUSTOM
     */
    public static final String DIM_TYPE_CODE    = NS + "dimTypeCode";
    /** Column in fact table used for this dimension */
    public static final String DIM_FACT_COLUMN  = NS + "dimFactColumn";
    /** Primary key column in the dimension table */
    public static final String DIM_PRIMARY_KEY  = NS + "dimPrimaryKey";
    /** Display column in the dimension table */
    public static final String DIM_COLUMN       = NS + "dimColumn";
    /** PK of the master dimension (used when this is a slave dim-app) */
    public static final String MASTER_PRIMARY_KEY = NS + "masterPrimaryKey";
    /** Pre-computed: true when this DimensionApp represents a master dimension */
    public static final String IS_MASTER_APP    = NS + "isMasterApp";
    /** Whether the dimension should be joined at root level */
    public static final String IS_ROOT_JOIN     = NS + "isRootJoin";
    /** Hierarchy code of the Level this app belongs to */
    public static final String HIERARCHY_CODE   = NS + "hierarchyCode";
    /** Sequence number inside the hierarchy */
    public static final String LEVEL_SEQUENCE   = NS + "levelSequence";
    /** Level business code */
    public static final String LEVEL_CODE       = NS + "levelCode";

    // ── DwTable Properties ───────────────────────────────────────────────────

    public static final String SCHEMA_NAME      = NS + "schemaName";
    public static final String TABLE_NAME       = NS + "tableName";
    /** SourceType code: 0=MYSQL, 1=DORIS */
    public static final String SOURCE_TYPE_CODE = NS + "sourceTypeCode";

    // ── Category Properties ──────────────────────────────────────────────────

    /** Long ID of the category */
    public static final String CAT_ID           = NS + "id";
    /** Category display name */
    public static final String NAME             = NS + "name";

    // ── Histogram Properties ─────────────────────────────────────────────────

    public static final String TABLE_ROW_NUM    = NS + "tableRowNum";
    public static final String DIM_ROW_NUM      = NS + "dimensionRowNum";
    public static final String MAX_SCAN_NUM     = NS + "maxScanNum";

    // ── Object Properties ────────────────────────────────────────────────────

    /** Measure → MeasureApp */
    public static final String HAS_MEASURE_APP  = NS + "hasMeasureApp";
    /** MeasureApp → DwTable (fact table) */
    public static final String APPLIES_TO_TABLE = NS + "appliesToTable";
    /**
     * MeasureApp → MeasureApp (direct dependency).
     * Transitive closure ({@code ind:dependsOnMeasApp+}) is used in SPARQL to
     * walk all levels of DERIVED/EXTENDED hierarchies.
     */
    public static final String DEPENDS_ON_APP   = NS + "dependsOnMeasApp";
    /** Dimension → DimensionApp */
    public static final String HAS_DIM_APP      = NS + "hasDimApp";
    /** DimensionApp → DwTable (fact table side) */
    public static final String DIM_FACT_TABLE   = NS + "dimFactTable";
    /** DimensionApp → DwTable (dimension table, STD_WITH_TABLE only) */
    public static final String DIM_TABLE        = NS + "dimTable";
    /** Measure → Category */
    public static final String BELONGS_TO_CAT   = NS + "belongsToCategory";
    /** Category → Category (parent link, used for transitive category lookup) */
    public static final String CAT_PARENT       = NS + "categoryParent";
    /** Measure → Dimension, materialized by AD business KG reasoning. */
    public static final String COMPATIBLE_DIMENSION = NS + "compatibleDimension";
    /** Measure → Measure dependency closure: current measure depends on upstream measure. */
    public static final String UPSTREAM_MEASURE = NS + "upstreamMeasure";
    /** Measure → Measure dependency closure: current measure is used by downstream measure. */
    public static final String DOWNSTREAM_MEASURE = NS + "downstreamMeasure";
    /** Reasoning rule identifier on inference evidence nodes. */
    public static final String INFERRED_BY_RULE = NS + "inferredByRule";
    /** Confidence score on inference evidence nodes. */
    public static final String CONFIDENCE = NS + "confidence";
    /** Human-readable evidence path on inference evidence nodes. */
    public static final String EVIDENCE_PATH = NS + "evidencePath";
    /** DwTable → TableHistogram */
    public static final String HAS_TBL_HIST     = NS + "hasTableHistogram";
    /** DimHistogram dimCode property */
    public static final String HIST_DIM_CODE    = NS + "histDimCode";
    /** DimHistogram tableName property */
    public static final String HIST_TABLE_NAME  = NS + "histTableName";

    // ── SPARQL PREFIX block ──────────────────────────────────────────────────

    public static final String PREFIXES =
            "PREFIX ind:  <" + NS   + ">\n" +
            "PREFIX inst: <" + INST + ">\n" +
            "PREFIX xsd:  <http://www.w3.org/2001/XMLSchema#>\n" +
            "PREFIX rdf:  <http://www.w3.org/1999/02/22-rdf-syntax-ns#>\n" +
            "PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>\n" +
            "PREFIX owl:  <http://www.w3.org/2002/07/owl#>\n";

    private IndicatorOntology() {}
}
