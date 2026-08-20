#!/usr/bin/env python3
"""Run a reproducible semantic-retrieval quality gate against a business KG."""

from __future__ import annotations

import argparse
from dataclasses import replace
import json
from pathlib import Path
import sys
from typing import Any


APP_DIR = Path(__file__).resolve().parents[1]
if str(APP_DIR) not in sys.path:
    sys.path.insert(0, str(APP_DIR))

from kg_builder.semantic_retrieval import SemanticMappingConfig, SemanticMappingService


def _case_result(service: SemanticMappingService, case: dict[str, Any], include_vector: bool) -> dict[str, Any]:
    result = service.map(str(case.get("question") or ""), include_vector=include_vector, top_k=10)
    actual_measure = result.measure_candidates[0].code if result.measure_candidates else ""
    top3_measures = [candidate.code for candidate in result.measure_candidates[:3]]
    actual_dimensions = [candidate.code for candidate in result.dimension_candidates]
    actual_values = [
        f"{binding.dimension_code}={binding.canonical_value}"
        for binding in result.value_bindings
    ]
    expected_measure = str(case.get("expectedMeasure") or "")
    expected_dimensions = [str(value) for value in case.get("expectedDimensions") or []]
    expected_values = [str(value) for value in case.get("expectedValues") or []]
    expected_decision = str(case.get("expectedDecision") or "")
    expected_match_type = str(case.get("expectedTopMatchType") or "")
    expected_vector_used = case.get("expectedVectorUsed")
    expected_filter_safe = case.get("expectedFilterSafe")
    allow_extra_dimensions = bool(case.get("allowAdditionalDimensions"))
    allow_extra_values = bool(case.get("allowAdditionalValues"))
    checks = {
        "measureTop1": not expected_measure or actual_measure == expected_measure,
        "measureTop3": not expected_measure or expected_measure in top3_measures,
        "dimensions": (
            all(code in actual_dimensions for code in expected_dimensions)
            if allow_extra_dimensions
            else set(actual_dimensions) == set(expected_dimensions)
        ) if "expectedDimensions" in case else True,
        "values": (
            all(value in actual_values for value in expected_values)
            if allow_extra_values
            else set(actual_values) == set(expected_values)
        ) if "expectedValues" in case else True,
        "decision": not expected_decision or result.decision == expected_decision,
        "topMatchType": (
            not expected_match_type
            or bool(result.measure_candidates)
            and result.measure_candidates[0].match_type == expected_match_type
        ),
        "vectorUsed": (
            True if expected_vector_used is None
            else bool(result.diagnostics.get("vectorUsed")) is bool(expected_vector_used)
        ),
        "filterSafe": (
            True if expected_filter_safe is None
            else bool(result.value_bindings)
            and all(binding.filter_safe for binding in result.value_bindings)
            is bool(expected_filter_safe)
        ),
    }
    return {
        "id": case.get("id") or "",
        "question": case.get("question") or "",
        "passed": all(checks.values()),
        "checks": checks,
        "actual": {
            "measureTop1": actual_measure,
            "measureTop3": top3_measures,
            "dimensions": actual_dimensions,
            "values": actual_values,
            "decision": result.decision,
            "confidence": result.confidence,
            "topMatchType": (
                result.measure_candidates[0].match_type if result.measure_candidates else ""
            ),
            "vectorUsed": bool(result.diagnostics.get("vectorUsed")),
            "filterSafe": (
                bool(result.value_bindings)
                and all(binding.filter_safe for binding in result.value_bindings)
            ),
            "elapsedMs": result.elapsed_ms,
        },
    }


def evaluate(service: SemanticMappingService, cases: list[dict[str, Any]], include_vector: bool) -> dict[str, Any]:
    rows = [_case_result(service, case, include_vector) for case in cases]
    total = len(rows)

    def rate(check: str) -> float:
        applicable = [
            row for row, case in zip(rows, cases)
            if (
                case.get("expectedMeasure") if check.startswith("measure")
                else "expectedDimensions" in case if check == "dimensions"
                else "expectedValues" in case if check == "values"
                else case.get("expectedTopMatchType") if check == "topMatchType"
                else "expectedVectorUsed" in case if check == "vectorUsed"
                else "expectedFilterSafe" in case if check == "filterSafe"
                else case.get("expectedDecision")
            )
        ]
        if not applicable:
            return 1.0
        return sum(bool(row["checks"][check]) for row in applicable) / len(applicable)

    elapsed = [row["actual"]["elapsedMs"] for row in rows]
    elapsed_sorted = sorted(elapsed)
    p95_index = max(0, min(len(elapsed_sorted) - 1, int(len(elapsed_sorted) * 0.95))) if elapsed_sorted else 0
    value_positive_cases = sum(bool(case.get("expectedValues")) for case in cases)
    value_negative_cases = sum(
        "expectedValues" in case and not case.get("expectedValues") for case in cases
    )
    coverage_warnings = []
    if value_positive_cases == 0:
        coverage_warnings.append(
            "当前数据集没有正向维值案例；valueBindingAccuracy 仅验证无误过滤，不能证明线上维值召回率"
        )
    if value_negative_cases == 0:
        coverage_warnings.append(
            "当前数据集没有维值负向案例，不能证明系统会抑制误过滤"
        )
    vector_required_cases = sum(
        bool(case.get("expectedVectorUsed")) for case in cases
    )
    if include_vector and vector_required_cases == 0:
        coverage_warnings.append(
            "向量门禁未包含必须使用 vector 的案例"
        )
    coverage_complete = bool(
        value_positive_cases > 0
        and value_negative_cases > 0
        and (not include_vector or vector_required_cases > 0)
    )
    return {
        "ok": all(row["passed"] for row in rows) and coverage_complete,
        "summary": {
            "total": total,
            "passed": sum(row["passed"] for row in rows),
            "failed": sum(not row["passed"] for row in rows),
            "measureTop1Accuracy": round(rate("measureTop1"), 4),
            "measureTop3Recall": round(rate("measureTop3"), 4),
            "dimensionRecall": round(rate("dimensions"), 4),
            "valueBindingAccuracy": round(rate("values"), 4),
            "valuePositiveCases": value_positive_cases,
            "valueNegativeCases": value_negative_cases,
            "valueCoverageComplete": coverage_complete,
            "valueFilterSafetyAccuracy": round(rate("filterSafe"), 4),
            "decisionAccuracy": round(rate("decision"), 4),
            "topMatchTypeAccuracy": round(rate("topMatchType"), 4),
            "vectorUsageAccuracy": round(rate("vectorUsed"), 4),
            "latencyP95Ms": elapsed_sorted[p95_index] if elapsed_sorted else 0,
            "coverageWarnings": coverage_warnings,
        },
        "cases": rows,
    }


def evaluate_execution(
    service: SemanticMappingService,
    cases: list[dict[str, Any]],
    data_agent_url: str,
) -> dict[str, Any]:
    """Execute explicitly marked cases through NLQ -> DA as an integration gate."""
    from kg_builder.nlq.service import NaturalLanguageQueryService

    executable_cases = [case for case in cases if case.get("requiresExecution")]
    nlq = NaturalLanguageQueryService(
        service.ttl_path,
        data_agent_url,
        source_ttl_path=service.source_ttl_path,
        semantic_mapping_service=service,
    )
    nlq._resolve_question_intent = lambda _question, _mode: {"mode": "aggregate"}
    rows: list[dict[str, Any]] = []
    for case in executable_cases:
        response = nlq.query(
            str(case.get("question") or ""),
            execute=True,
            page_size=20,
        )
        payload = response.get("daPayload") if isinstance(response, dict) else {}
        filters = payload.get("filterList") if isinstance(payload, dict) else []
        actual_filters = {
            f"{item.get('code')}={value}"
            for item in (filters or [])
            if isinstance(item, dict)
            for operator in (item.get("operatorList") or [])
            if isinstance(operator, dict)
            for value in (operator.get("dataList") or [])
        }
        expected_values = {
            str(value) for value in case.get("expectedValues") or []
        }
        result = response.get("result") if isinstance(response, dict) else {}
        data = result.get("data") if isinstance(result, dict) else {}
        review_sql = str(data.get("reviewSql") or "") if isinstance(data, dict) else ""
        cell_list = data.get("cellList") if isinstance(data, dict) else None
        expected_column = str(case.get("expectedFilterColumn") or "")
        checks = {
            "executionOk": bool(response.get("ok")),
            "filterPayload": expected_values.issubset(actual_filters),
            "filterColumn": not expected_column or expected_column in review_sql,
            "resultRows": bool(cell_list),
        }
        rows.append({
            "id": case.get("id") or "",
            "passed": all(checks.values()),
            "checks": checks,
            "actualFilters": sorted(actual_filters),
            "resultCode": result.get("code") if isinstance(result, dict) else None,
            "reviewSqlHasExpectedColumn": checks["filterColumn"],
        })
    return {
        "ok": bool(rows) and all(row["passed"] for row in rows),
        "total": len(rows),
        "passed": sum(row["passed"] for row in rows),
        "failed": sum(not row["passed"] for row in rows),
        "cases": rows,
    }


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--ttl",
        type=Path,
        default=APP_DIR / "output" / "business_kg" / "indicator-data.ttl",
    )
    parser.add_argument("--source-ttl", type=Path, default=APP_DIR / "output" / "kg.ttl")
    parser.add_argument(
        "--dictionary", type=Path, default=APP_DIR / "semantic_dictionary.yaml"
    )
    parser.add_argument(
        "--cases", type=Path, default=APP_DIR / "evals" / "semantic_retrieval_cases.json"
    )
    parser.add_argument("--output", type=Path)
    parser.add_argument("--with-vector", action="store_true")
    parser.add_argument(
        "--da-url",
        default="",
        help="Optional DA query endpoint for marked NLQ execution cases",
    )
    parser.add_argument(
        "--require-execution",
        action="store_true",
        help="Fail unless at least one marked NLQ -> DA execution case passes",
    )
    args = parser.parse_args()
    if args.require_execution and not args.da_url:
        parser.error("--require-execution requires --da-url")

    cases = [
        case for case in json.loads(args.cases.read_text(encoding="utf-8"))
        if args.with_vector or not case.get("requiresVector")
    ]
    config = replace(
        SemanticMappingConfig.from_env(APP_DIR),
        dictionary_paths=(args.dictionary,),
        vector_enabled=bool(args.with_vector),
        feedback_dictionary_enabled=False,
    )
    service = SemanticMappingService(
        args.ttl,
        source_ttl_path=args.source_ttl if args.source_ttl.exists() else None,
        config=config,
    )
    report = evaluate(service, cases, include_vector=bool(args.with_vector))
    if args.da_url:
        execution = evaluate_execution(service, cases, args.da_url)
        report["execution"] = execution
        if args.require_execution:
            report["ok"] = bool(report["ok"] and execution["ok"])
    elif args.require_execution:
        report["ok"] = False
    rendered = json.dumps(report, ensure_ascii=False, indent=2)
    if args.output:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(rendered + "\n", encoding="utf-8")
    print(rendered)
    return 0 if report["ok"] else 1


if __name__ == "__main__":
    raise SystemExit(main())
