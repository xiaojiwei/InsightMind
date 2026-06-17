"""Deterministic reasoning rules for the indicator business knowledge graph."""

from __future__ import annotations

import hashlib
from datetime import datetime, timezone
from pathlib import Path
from typing import Callable, Iterable, Optional, Set, Tuple, Union

from rdflib import Graph, Literal, Namespace, RDF, URIRef
from rdflib.namespace import RDFS, XSD


IND = Namespace("http://indicator.insightmind.com/ontology#")
INST = Namespace("http://indicator.insightmind.com/instance/")


Triple = Tuple[URIRef, URIRef, URIRef]


class BusinessKGReasoner:
    """Materialize high-confidence business reasoning triples.

    The reasoner keeps direct relation triples easy to query and adds a compact
    evidence node for each inferred relation so UI/API layers can explain why a
    result exists.
    """

    def __init__(self, log_cb: Optional[Callable[[str], None]] = None):
        self._log = log_cb or (lambda _msg: None)

    def infer_from_turtle(self, turtle: str) -> Graph:
        graph = Graph()
        graph.parse(data=turtle, format="turtle")
        return self.infer(graph)

    def infer_file(self, source_path: Union[str, Path], output_path: Union[str, Path]) -> Graph:
        graph = Graph()
        graph.parse(str(source_path), format="turtle")
        inferred = self.infer(graph)
        output = Path(output_path)
        output.parent.mkdir(parents=True, exist_ok=True)
        output.write_text(inferred.serialize(format="turtle"), encoding="utf-8")
        self._log(f"[推理] 已生成 {output.name}: {len(inferred)} 条三元组")
        return inferred

    def infer(self, graph: Graph) -> Graph:
        inferred = Graph()
        inferred.bind("ind", IND)
        inferred.bind("inst", INST)
        inferred.bind("rdf", RDF)
        inferred.bind("xsd", XSD)
        inferred.bind("rdfs", RDFS)

        self._declare_reasoning_terms(inferred)

        relation_count = 0
        relation_count += self._infer_compatible_dimensions(graph, inferred)
        relation_count += self._infer_measure_dependencies(graph, inferred)
        self._log(f"[推理] 物化业务规则关系 {relation_count} 条")
        return inferred

    def _declare_reasoning_terms(self, out: Graph) -> None:
        out.add((IND.Inference, RDF.type, RDFS.Class))
        for prop in (
            IND.compatibleDimension,
            IND.upstreamMeasure,
            IND.downstreamMeasure,
            IND.inferredByRule,
            IND.confidence,
            IND.evidencePath,
            IND.generatedAt,
        ):
            out.add((prop, RDF.type, RDF.Property))

    def _infer_compatible_dimensions(self, graph: Graph, out: Graph) -> int:
        count = 0
        seen: Set[Triple] = set()

        for measure, dim in graph.subject_objects(IND.availableDimension):
            triple = (measure, IND.compatibleDimension, dim)
            if triple in seen:
                continue
            seen.add(triple)
            count += self._add_inference(
                out,
                triple,
                "compatible_dimension.available_dimension",
                1.0,
                "existing ind:availableDimension",
            )

        table_to_dims = {}
        for dim in graph.subjects(RDF.type, IND.Dimension):
            for dim_app in graph.objects(dim, IND.hasDimApp):
                for table in graph.objects(dim_app, IND.dimFactTable):
                    table_to_dims.setdefault(table, set()).add((dim, dim_app))

        for measure in graph.subjects(RDF.type, IND.Measure):
            for meas_app in graph.objects(measure, IND.hasMeasureApp):
                for table in graph.objects(meas_app, IND.appliesToTable):
                    for dim, dim_app in table_to_dims.get(table, set()):
                        triple = (measure, IND.compatibleDimension, dim)
                        if triple in seen:
                            continue
                        seen.add(triple)
                        count += self._add_inference(
                            out,
                            triple,
                            "compatible_dimension.shared_fact_table",
                            1.0,
                            f"{self._name(measure)} -> {self._name(meas_app)} -> {self._name(table)} <- {self._name(dim_app)} <- {self._name(dim)}",
                        )
        return count

    def _infer_measure_dependencies(self, graph: Graph, out: Graph) -> int:
        app_to_measures = {}
        for measure in graph.subjects(RDF.type, IND.Measure):
            for app in graph.objects(measure, IND.hasMeasureApp):
                app_to_measures.setdefault(app, set()).add(measure)

        count = 0
        seen: Set[Triple] = set()
        for measure in graph.subjects(RDF.type, IND.Measure):
            for app in graph.objects(measure, IND.hasMeasureApp):
                for dep_app in self._walk_dep_apps(graph, app):
                    for upstream in app_to_measures.get(dep_app, set()):
                        if upstream == measure:
                            continue
                        up_triple = (measure, IND.upstreamMeasure, upstream)
                        down_triple = (upstream, IND.downstreamMeasure, measure)
                        evidence = f"{self._name(measure)} -> {self._name(app)} -> dependsOnMeasApp+ -> {self._name(dep_app)} -> {self._name(upstream)}"
                        if up_triple not in seen:
                            seen.add(up_triple)
                            count += self._add_inference(
                                out,
                                up_triple,
                                "measure_dependency.transitive_upstream",
                                1.0,
                                evidence,
                            )
                        if down_triple not in seen:
                            seen.add(down_triple)
                            count += self._add_inference(
                                out,
                                down_triple,
                                "measure_dependency.transitive_downstream",
                                1.0,
                                evidence,
                            )
        return count

    def _walk_dep_apps(self, graph: Graph, start: URIRef) -> Iterable[URIRef]:
        visited = set()
        stack = list(graph.objects(start, IND.dependsOnMeasApp))
        while stack:
            app = stack.pop()
            if app in visited:
                continue
            visited.add(app)
            yield app
            stack.extend(graph.objects(app, IND.dependsOnMeasApp))

    def _add_inference(
        self,
        out: Graph,
        triple: Triple,
        rule_id: str,
        confidence: float,
        evidence_path: str,
    ) -> int:
        subject, predicate, obj = triple
        out.add(triple)
        node = INST[f"inference_{self._hash_triple(triple)}"]
        out.add((node, RDF.type, IND.Inference))
        out.add((node, RDF.subject, subject))
        out.add((node, RDF.predicate, predicate))
        out.add((node, RDF.object, obj))
        out.add((node, IND.inferredByRule, Literal(rule_id)))
        out.add((node, IND.confidence, Literal(confidence, datatype=XSD.decimal)))
        out.add((node, IND.evidencePath, Literal(evidence_path)))
        out.add((node, IND.generatedAt, Literal(datetime.now(timezone.utc).isoformat(), datatype=XSD.dateTime)))
        return 1

    @staticmethod
    def _hash_triple(triple: Triple) -> str:
        raw = "|".join(str(part) for part in triple).encode("utf-8")
        return hashlib.sha1(raw).hexdigest()[:16]

    @staticmethod
    def _name(node: URIRef) -> str:
        text = str(node)
        if "#" in text:
            return text.rsplit("#", 1)[-1]
        return text.rstrip("/").rsplit("/", 1)[-1]
