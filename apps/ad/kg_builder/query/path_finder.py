"""
P0: JOIN Path Finder

BFS over the FK graph (db:references triples) to find the shortest
JOIN paths between any two tables.  Returns up to max_paths results
with generated SQL templates.
"""
from __future__ import annotations

from collections import deque
from dataclasses import dataclass, field
from typing import Dict, Iterator, List, Optional, Set, Tuple

from rdflib import Graph, URIRef

from kg_builder.ontology.owl_schema import DB


@dataclass
class JoinStep:
    src_table: str
    src_col:   str
    dst_table: str
    direction: str   # "→" (FK forward) | "←" (FK reverse)

    def __str__(self) -> str:
        if self.direction == "→":
            return f"{self.src_table}.{self.src_col} → {self.dst_table}"
        else:
            return f"{self.src_table} ← {self.dst_table}.{self.src_col}"


@dataclass
class JoinPath:
    tables:     List[str]
    steps:      List[JoinStep]
    hops:       int
    score:      float = 1.0   # 1.0 = direct FK; lower for indirect

    def to_sql(self, select: str = "*") -> str:
        if not self.tables:
            return ""
        lines = [f"SELECT {select}", f"FROM `{self.tables[0]}`"]
        for step in self.steps:
            if step.direction == "→":
                lines.append(
                    f"  JOIN `{step.dst_table}`"
                    f" ON `{step.src_table}`.`{step.src_col}`"
                    f" = `{step.dst_table}`.`id`"
                )
            else:
                lines.append(
                    f"  JOIN `{step.dst_table}`"
                    f" ON `{step.dst_table}`.`{step.src_col}`"
                    f" = `{step.src_table}`.`id`"
                )
        return "\n".join(lines)

    def to_dict(self) -> dict:
        return {
            "跳数":  self.hops,
            "路径":  " → ".join(self.tables),
            "步骤":  [str(s) for s in self.steps],
            "SQL":   self.to_sql(),
        }


class JoinPathFinder:
    """
    Builds an adjacency list from db:references triples in the RDF graph,
    then uses BFS to find shortest JOIN paths between two tables.
    """

    def __init__(self, rdf_graph: Graph) -> None:
        self._g   = rdf_graph
        self._adj: Optional[Dict[str, List[Tuple[str, str, str]]]] = None
        # adj[table] = [(col_name, neighbor_table, direction), ...]

    # ------------------------------------------------------------------ #

    def _build(self) -> None:
        adj: Dict[str, List[Tuple[str, str, str]]] = {}

        for col_uri, tgt_uri in self._g.subject_objects(predicate=DB.references):
            col_s = str(col_uri)
            if "/col/" not in col_s:
                continue  # skip constraint-level references

            parts    = col_s.rstrip("/").split("/")
            col_name = parts[-1]
            src_tbl  = parts[-2]

            tgt_name_lit = self._g.value(URIRef(str(tgt_uri)), DB.tableName)
            dst_tbl = str(tgt_name_lit) if tgt_name_lit else str(tgt_uri).rstrip("/").split("/")[-1]

            if src_tbl == dst_tbl:
                continue

            adj.setdefault(src_tbl, []).append((col_name, dst_tbl, "→"))
            adj.setdefault(dst_tbl, []).append((col_name, src_tbl, "←"))

        self._adj = adj

    # ------------------------------------------------------------------ #

    def find_paths(
        self,
        from_table: str,
        to_table:   str,
        max_hops:   int = 5,
        max_paths:  int = 5,
    ) -> List[JoinPath]:
        if self._adj is None:
            self._build()

        from_l = from_table.lower()
        to_l   = to_table.lower()
        if from_l == to_l:
            return []

        # Normalise adj keys to lowercase for lookup
        adj_lower: Dict[str, List[Tuple[str, str, str]]] = {
            k.lower(): [(c, n.lower(), d) for c, n, d in v]
            for k, v in self._adj.items()
        }

        # BFS: state = (current_table_lower, path_of_steps, visited_set)
        queue:   deque = deque()
        queue.append((from_l, [], {from_l}))
        found: List[JoinPath] = []

        while queue and len(found) < max_paths:
            curr, steps, visited = queue.popleft()

            if len(steps) >= max_hops:
                continue

            for col_name, neighbor, direction in adj_lower.get(curr, []):
                step = JoinStep(
                    src_table=curr,
                    src_col=col_name,
                    dst_table=neighbor,
                    direction=direction,
                )
                new_steps   = steps + [step]
                new_visited = visited | {neighbor}

                if neighbor == to_l:
                    all_tables = [from_l] + [s.dst_table for s in new_steps]
                    found.append(JoinPath(
                        tables=all_tables,
                        steps=new_steps,
                        hops=len(new_steps),
                        score=round(1.0 / len(new_steps), 3),
                    ))
                elif neighbor not in visited:
                    queue.append((neighbor, new_steps, new_visited))

        found.sort(key=lambda p: p.hops)
        return found

    # ------------------------------------------------------------------ #

    def get_neighbors(self, table: str) -> List[dict]:
        """Return all direct FK neighbors of a table (1-hop)."""
        if self._adj is None:
            self._build()

        tbl_lower = table.lower()
        adj_lower = {
            k.lower(): [(c, n, d) for c, n, d in v]
            for k, v in self._adj.items()
        }

        result = []
        for col_name, neighbor, direction in adj_lower.get(tbl_lower, []):
            result.append({
                "table":     neighbor,
                "column":    col_name,
                "direction": direction,
            })
        return result

    # ------------------------------------------------------------------ #

    def get_impact(
        self,
        table: str,
        max_depth: int = 6,
    ) -> dict:
        """
        BFS reverse traversal: find all tables that depend on *table*
        (i.e., have a FK pointing to it, directly or transitively).

        Returns a tree dict: {table: {depth, dependents: [...]}}
        """
        if self._adj is None:
            self._build()

        tbl_lower = table.lower()
        adj_lower = {
            k.lower(): [(c, n, d) for c, n, d in v]
            for k, v in self._adj.items()
        }

        # Reverse adjacency: tables that *point to* each table
        rev_adj: Dict[str, List[Tuple[str, str]]] = {}
        for src, edges in adj_lower.items():
            for col_name, dst, direction in edges:
                if direction == "→":   # src.col → dst (FK)
                    rev_adj.setdefault(dst, []).append((col_name, src))

        # BFS from target table
        visited  = {tbl_lower: {"depth": 0, "via_col": None, "dependents": []}}
        frontier = deque([tbl_lower])

        while frontier:
            curr = frontier.popleft()
            curr_depth = visited[curr]["depth"]
            if curr_depth >= max_depth:
                continue

            for col_name, dependent in rev_adj.get(curr, []):
                if dependent not in visited:
                    visited[dependent] = {
                        "depth":      curr_depth + 1,
                        "via_col":    col_name,
                        "dependents": [],
                    }
                    visited[curr]["dependents"].append(dependent)
                    frontier.append(dependent)

        # Flatten to list for API
        deps = [
            {
                "依赖表":    t,
                "跳数":      info["depth"],
                "通过外键":  info["via_col"],
            }
            for t, info in visited.items()
            if t != tbl_lower
        ]
        deps.sort(key=lambda x: x["跳数"])
        return {
            "目标表":   table,
            "影响范围": deps,
            "总计":    len(deps),
        }
