"""SPARQL query API — curated query templates over the knowledge graph."""
from __future__ import annotations

from pathlib import Path
from typing import Any, Dict, List, Optional

from rdflib import Graph

from kg_builder.ontology.owl_schema import DB


# ── Shared SPARQL prefix block ──────────────────────────────────────── #
_PREFIX = """\
PREFIX db:   <http://kg.local/db#>
PREFIX inst: <http://kg.local/instance/>
PREFIX rdf:  <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
PREFIX xsd:  <http://www.w3.org/2001/XMLSchema#>
"""

# ── English SPARQL value → Chinese display ──────────────────────────── #
_REL_ZH = {
    "FK":           "外键",
    "FK_REVERSE":   "反向外键",
    "SIMILAR":      "相似列",
    "POTENTIAL_FK": "潜在外键",
}
_TYPE_ZH = {
    "Table":  "表",
    "Column": "列",
}


class SPARQLApi:
    """
    Convenience wrapper around rdflib.Graph.query with domain-specific
    template queries.  All preset queries return results with Chinese keys.
    """

    def __init__(self, graph: Graph) -> None:
        self._g = graph

    # ── Public interface ────────────────────────────────────────────── #

    def find_related_tables(self, table_name: str) -> List[Dict[str, str]]:
        """Return tables reachable from *table_name* via FK (one hop each direction)."""
        q = _PREFIX + """
        SELECT DISTINCT ?relTable ?relName ?relComment WHERE {
          ?t a db:Table ; db:tableName ?tname .
          FILTER(LCASE(STR(?tname)) = LCASE("%s"))
          {
            ?col db:belongsToTable ?t .
            ?col db:references ?relTable .
          } UNION {
            ?otherCol db:references ?t .
            ?otherCol db:belongsToTable ?relTable .
          }
          ?relTable db:tableName ?relName .
          OPTIONAL { ?relTable db:comment ?relComment }
        }
        """ % table_name
        rows = self._run(q, ["relName", "relComment"])
        return [{"相关表": r["relName"], "说明": r.get("relComment", "")} for r in rows]

    def find_similar_columns(self, column_name: str) -> List[Dict[str, str]]:
        """Find columns similar to *column_name* via db:similarTo."""
        q = _PREFIX + """
        SELECT ?srcName ?simName ?simComment ?simTableName WHERE {
          ?srcCol a db:Column ; db:name ?srcName .
          FILTER(LCASE(STR(?srcName)) = LCASE("%s"))
          ?srcCol db:similarTo ?simCol .
          ?simCol db:name ?simName .
          ?simCol db:belongsToTable ?simTable .
          ?simTable db:tableName ?simTableName .
          OPTIONAL { ?simCol db:comment ?simComment }
        }
        """ % column_name
        rows = self._run(q, ["srcName", "simName", "simComment", "simTableName"])
        return [
            {
                "源列名":  r["srcName"],
                "相似列名": r["simName"],
                "注释":    r.get("simComment", ""),
                "所属表":  r["simTableName"],
            }
            for r in rows
        ]

    def get_table_schema(self, table_name: str) -> List[Dict[str, str]]:
        """Return all columns of *table_name* with Chinese display names."""
        q = _PREFIX + """
        SELECT ?colName ?zhName ?colType ?isNullable ?isPK ?comment WHERE {
          ?t a db:Table ; db:tableName ?tname .
          FILTER(LCASE(STR(?tname)) = LCASE("%s"))
          ?t db:containsColumn ?col .
          ?col db:name ?colName ;
               db:columnType ?colType ;
               db:isNullable ?isNullable ;
               db:isPrimaryKey ?isPK .
          OPTIONAL { ?col db:comment ?comment }
          OPTIONAL { ?col rdfs:label ?zhName . FILTER(LANG(?zhName) = "zh") }
        }
        ORDER BY ?colName
        """ % table_name
        rows = self._run(q, ["colName", "zhName", "colType", "isNullable", "isPK", "comment"])
        result = []
        for r in rows:
            display_name = r.get("zhName") or r["colName"]
            result.append({
                "列名":   display_name,
                "原始名": r["colName"],
                "类型":   r["colType"],
                "可空":   "是" if r.get("isNullable", "").lower() == "true" else "否",
                "主键":   "✓" if r.get("isPK", "").lower() == "true" else "",
                "注释":   r.get("comment", ""),
            })
        return result

    def find_potential_joins(self, table1: str, table2: str) -> List[Dict[str, str]]:
        """Find column pairs that could form a JOIN between *table1* and *table2*."""
        q = _PREFIX + """
        SELECT DISTINCT ?col1Name ?col1Comment ?rel ?col2Name ?col2Comment WHERE {
          ?t1 a db:Table ; db:tableName ?t1name .
          ?t2 a db:Table ; db:tableName ?t2name .
          FILTER(LCASE(STR(?t1name)) = LCASE("%s"))
          FILTER(LCASE(STR(?t2name)) = LCASE("%s"))
          ?col1 db:belongsToTable ?t1 ; db:name ?col1Name .
          ?col2 db:belongsToTable ?t2 ; db:name ?col2Name .
          OPTIONAL { ?col1 db:comment ?col1Comment }
          OPTIONAL { ?col2 db:comment ?col2Comment }
          {
            ?col1 db:references ?t2 . BIND("FK" AS ?rel)
          } UNION {
            ?col2 db:references ?t1 . BIND("FK_REVERSE" AS ?rel)
          } UNION {
            ?col1 db:similarTo ?col2 . BIND("SIMILAR" AS ?rel)
          } UNION {
            ?col1 db:potentialFK ?col2 . BIND("POTENTIAL_FK" AS ?rel)
          }
        }
        """ % (table1, table2)
        rows = self._run(q, ["col1Name", "col1Comment", "rel", "col2Name", "col2Comment"])
        return [
            {
                f"{table1}列": r["col1Name"],
                f"{table1}注释": r.get("col1Comment", ""),
                "关系类型": _REL_ZH.get(r.get("rel", ""), r.get("rel", "")),
                f"{table2}列": r["col2Name"],
                f"{table2}注释": r.get("col2Comment", ""),
            }
            for r in rows
        ]

    def get_fk_graph(self, schema_name: Optional[str] = None) -> List[Dict[str, str]]:
        """Return the full FK dependency graph."""
        schema_filter = (
            f'?schema db:name "{schema_name}" . ?srcTable db:belongsToSchema ?schema .'
            if schema_name else ""
        )
        q = _PREFIX + """
        SELECT ?srcTableName ?srcColName ?srcComment ?tgtTableName WHERE {
          %s
          ?srcTable a db:Table ; db:tableName ?srcTableName .
          ?srcCol db:belongsToTable ?srcTable ; db:name ?srcColName .
          ?srcCol db:references ?tgtTable .
          ?tgtTable db:tableName ?tgtTableName .
          OPTIONAL { ?srcCol db:comment ?srcComment }
        }
        ORDER BY ?srcTableName ?srcColName
        """ % schema_filter
        rows = self._run(q, ["srcTableName", "srcColName", "srcComment", "tgtTableName"])
        return [
            {
                "来源表":  r["srcTableName"],
                "外键列":  r["srcColName"],
                "列注释":  r.get("srcComment", ""),
                "目标表":  r["tgtTableName"],
            }
            for r in rows
        ]

    def search_by_comment(self, keyword: str) -> List[Dict[str, str]]:
        """Full-text search across table and column comments."""
        kw = keyword.lower()
        q = _PREFIX + """
        SELECT ?entityName ?zhName ?entityType ?comment WHERE {
          {
            ?e a db:Table ; db:tableName ?entityName ; db:comment ?comment .
            BIND("Table" AS ?entityType)
          } UNION {
            ?e a db:Column ; db:name ?entityName ; db:comment ?comment .
            BIND("Column" AS ?entityType)
          }
          FILTER(CONTAINS(LCASE(STR(?comment)), "%s"))
          OPTIONAL { ?e rdfs:label ?zhName . FILTER(LANG(?zhName) = "zh") }
        }
        ORDER BY ?entityType ?entityName
        """ % kw
        rows = self._run(q, ["entityName", "zhName", "entityType", "comment"])
        return [
            {
                "名称":   r.get("zhName") or r["entityName"],
                "原始名": r["entityName"],
                "类型":   _TYPE_ZH.get(r.get("entityType", ""), r.get("entityType", "")),
                "注释":   r.get("comment", ""),
            }
            for r in rows
        ]

    def find_columns_by_pattern(self, pattern: str) -> List[Dict[str, str]]:
        """Find all columns whose detected data pattern matches *pattern*."""
        q = _PREFIX + """
        SELECT ?colName ?zhName ?tableName ?colType WHERE {
          ?col a db:Column ;
               db:name ?colName ;
               db:columnType ?colType ;
               db:detectedPattern "%s" ;
               db:belongsToTable ?t .
          ?t db:tableName ?tableName .
          OPTIONAL { ?col rdfs:label ?zhName . FILTER(LANG(?zhName) = "zh") }
        }
        ORDER BY ?tableName ?colName
        """ % pattern
        rows = self._run(q, ["colName", "zhName", "tableName", "colType"])
        return [
            {
                "列名":   r.get("zhName") or r["colName"],
                "原始名": r["colName"],
                "所属表": r["tableName"],
                "数据类型": r["colType"],
            }
            for r in rows
        ]

    def list_views(self, schema_name: Optional[str] = None) -> List[Dict[str, str]]:
        """Return all views in the graph."""
        schema_filter = (
            f'?schema db:name "{schema_name}" . ?t db:belongsToSchema ?schema .'
            if schema_name else ""
        )
        q = _PREFIX + """
        SELECT ?viewName ?zhName ?comment WHERE {
          %s
          ?t a db:Table ;
             db:isView true ;
             db:tableName ?viewName .
          OPTIONAL { ?t db:comment ?comment }
          OPTIONAL { ?t rdfs:label ?zhName . FILTER(LANG(?zhName) = "zh") }
        }
        ORDER BY ?viewName
        """ % schema_filter
        rows = self._run(q, ["viewName", "zhName", "comment"])
        return [
            {
                "视图名": r["viewName"],
                "中文名": r.get("zhName", ""),
                "注释":  r.get("comment", ""),
            }
            for r in rows
        ]

    def find_individuals_by_value(
        self, keyword: str, table_name: Optional[str] = None
    ) -> List[Dict[str, str]]:
        """Search ABox individuals whose column values contain *keyword*.

        Useful for answering queries like "张三的订单有哪些":
          find_individuals_by_value("张三")        → all rows matching 张三
          find_individuals_by_value("张三", "customers")  → only in customers
        """
        table_filter = (
            f'?t db:tableName "{table_name}" .' if table_name else ""
        )
        q = _PREFIX + """
        SELECT DISTINCT ?ind ?label ?tableName ?colName ?value WHERE {
          %s
          ?t a db:Table ; db:tableName ?tableName .
          ?t db:hasIndividual ?ind .
          ?ind rdfs:label ?label .
          ?col a db:Column ;
               db:name ?colName ;
               db:belongsToTable ?t .
          ?ind ?col ?value .
          FILTER(CONTAINS(LCASE(STR(?value)), LCASE("%s")))
        }
        ORDER BY ?tableName ?label
        LIMIT 200
        """ % (table_filter, keyword.replace('"', '\\"'))
        rows = self._run(q, ["ind", "label", "tableName", "colName", "value"])
        return [
            {
                "个体URI": r["ind"],
                "标签":    r["label"],
                "所属表":  r["tableName"],
                "匹配列":  r["colName"],
                "匹配值":  r["value"],
            }
            for r in rows
        ]

    def get_individual_detail(self, individual_uri: str) -> List[Dict[str, str]]:
        """Return all column values of a single ABox individual."""
        q = _PREFIX + """
        SELECT ?colName ?zhColName ?value WHERE {
          BIND(<%s> AS ?ind)
          ?col a db:Column ; db:name ?colName .
          ?ind ?col ?value .
          OPTIONAL { ?col rdfs:label ?zhColName . FILTER(LANG(?zhColName) = "zh") }
        }
        ORDER BY ?colName
        """ % individual_uri
        rows = self._run(q, ["colName", "zhColName", "value"])
        return [
            {
                "列名": r.get("zhColName") or r["colName"],
                "原始列名": r["colName"],
                "值":  r["value"],
            }
            for r in rows
        ]

    def find_linked_individuals(
        self, individual_uri: str
    ) -> List[Dict[str, str]]:
        """Return all individuals linked via db:fkLink from *individual_uri*."""
        q = _PREFIX + """
        SELECT ?linked ?linkedLabel ?linkedTable WHERE {
          BIND(<%s> AS ?ind)
          ?ind db:fkLink ?linked .
          ?linked rdfs:label ?linkedLabel .
          ?linkedTable db:hasIndividual ?linked .
          ?linkedTable db:tableName ?linkedTableName .
          BIND(?linkedTableName AS ?linkedTable)
        }
        """ % individual_uri
        rows = self._run(q, ["linked", "linkedLabel", "linkedTable"])
        return [
            {
                "关联个体URI": r["linked"],
                "标签":       r["linkedLabel"],
                "所属表":     r["linkedTable"],
            }
            for r in rows
        ]

    def run_raw(self, sparql: str) -> List[Dict[str, Any]]:
        """Execute a raw SPARQL SELECT query and return rows as dicts."""
        results = self._g.query(_PREFIX + "\n" + sparql)
        return [
            {str(var): str(row[var]) for var in results.vars if row[var] is not None}
            for row in results
        ]

    # ------------------------------------------------------------------ #
    # Helpers
    # ------------------------------------------------------------------ #

    def _run(self, query: str, vars: List[str]) -> List[Dict[str, str]]:
        results = self._g.query(query)
        rows = []
        for row in results:
            d: Dict[str, str] = {}
            for v in vars:
                val = getattr(row, v, None)
                d[v] = str(val) if val is not None else ""
            rows.append(d)
        return rows

    # ── Class method: load from file ─────────────────────────────────── #

    @classmethod
    def from_file(cls, path: str) -> "SPARQLApi":
        """Load a serialized graph (Turtle/JSON-LD) and return a SPARQLApi."""
        g = Graph()
        suffix = Path(path).suffix.lower()
        fmt = "json-ld" if suffix in (".jsonld", ".json") else "turtle"
        g.parse(path, format=fmt)
        return cls(g)
