#!/usr/bin/env python3
"""Run InsightMind evaluation cases against local HTTP APIs.

The first milestone focuses on conformance: action, diagnostic code, metric,
dimension, and KG fact-table alignment. Numeric truth runners can layer on top
of this result format without changing case IDs or output layout.
"""

from __future__ import annotations

import argparse
import datetime as dt
import json
import sys
import time
import urllib.error
import urllib.request
from pathlib import Path
from typing import Any


ROOT = Path(__file__).resolve().parents[3]
DEFAULT_CASES = ROOT / "apps/ad/evals/conformance/cases.jsonl"
DEFAULT_OUTPUT_DIR = ROOT / "apps/ad/output/evals/latest"


def _read_cases(path: Path) -> list[dict[str, Any]]:
    cases: list[dict[str, Any]] = []
    for line_no, raw in enumerate(path.read_text(encoding="utf-8").splitlines(), start=1):
        line = raw.strip()
        if not line or line.startswith("#"):
            continue
        try:
            case = json.loads(line)
        except json.JSONDecodeError as exc:
            raise SystemExit(f"{path}:{line_no}: invalid JSONL case: {exc}") from exc
        case.setdefault("surface", "nlq")
        case.setdefault("tier", "p0")
        cases.append(case)
    return cases


def _post_json(url: str, payload: dict[str, Any], timeout: float) -> tuple[int, dict[str, Any]]:
    data = json.dumps(payload, ensure_ascii=False).encode("utf-8")
    request = urllib.request.Request(
        url,
        data=data,
        headers={"Content-Type": "application/json"},
        method="POST",
    )
    try:
        with urllib.request.urlopen(request, timeout=timeout) as response:
            body = response.read().decode("utf-8")
            return response.status, json.loads(body or "{}")
    except urllib.error.HTTPError as exc:
        body = exc.read().decode("utf-8")
        try:
            parsed = json.loads(body or "{}")
        except json.JSONDecodeError:
            parsed = {"error": body}
        return exc.code, parsed


def _nlq_action(response: dict[str, Any]) -> str:
    if response.get("action"):
        return str(response["action"])
    if response.get("ok") is True:
        return "answer"
    if response.get("needsClarification"):
        return "clarify"
    return "error"


def _as_set(values: Any) -> set[str]:
    if values is None:
        return set()
    if isinstance(values, (str, int, float)):
        return {str(values)}
    return {str(item) for item in values if item is not None}


def _matched_measure_codes(response: dict[str, Any]) -> set[str]:
    matched = response.get("matched") or {}
    return _as_set(matched.get("measureCode") or matched.get("measureCodes"))


def _matched_dimension_codes(response: dict[str, Any]) -> set[str]:
    matched = response.get("matched") or {}
    codes = _as_set(matched.get("dimensionCodes"))
    for dim in matched.get("dimensions") or []:
        if isinstance(dim, dict) and dim.get("code"):
            codes.add(str(dim["code"]))
    return codes


def _matched_fact_tables(response: dict[str, Any]) -> set[str]:
    matched = response.get("matched") or {}
    return _as_set(matched.get("factTables"))


def _grade_nlq(case: dict[str, Any], response: dict[str, Any]) -> tuple[bool, list[str], dict[str, Any]]:
    failures: list[str] = []
    observed = {
        "action": _nlq_action(response),
        "diagnosticCode": response.get("diagnosticCode"),
        "measureCodes": sorted(_matched_measure_codes(response)),
        "dimensionCodes": sorted(_matched_dimension_codes(response)),
        "factTables": sorted(_matched_fact_tables(response)),
        "ok": response.get("ok"),
    }

    expected_action = case.get("expected_action")
    if expected_action and observed["action"] != expected_action:
        failures.append(f"action expected {expected_action!r}, got {observed['action']!r}")

    expected_diag = case.get("expected_diagnostic_code")
    if expected_diag and observed["diagnosticCode"] != expected_diag:
        failures.append(f"diagnosticCode expected {expected_diag!r}, got {observed['diagnosticCode']!r}")

    expected_measures = _as_set(case.get("expected_measure_codes"))
    observed_measures = set(observed["measureCodes"])
    if expected_measures and not expected_measures.issubset(observed_measures):
        failures.append(f"missing measure codes {sorted(expected_measures - observed_measures)}")

    expected_dims = _as_set(case.get("expected_dimension_codes"))
    observed_dims = set(observed["dimensionCodes"])
    if expected_dims and not expected_dims.issubset(observed_dims):
        failures.append(f"missing dimension codes {sorted(expected_dims - observed_dims)}")

    expected_tables = _as_set(case.get("expected_fact_tables"))
    observed_tables = set(observed["factTables"])
    if expected_tables and not expected_tables.issubset(observed_tables):
        failures.append(f"missing fact tables {sorted(expected_tables - observed_tables)}")

    forbidden_measures = _as_set(case.get("forbidden_measure_codes"))
    forbidden_hit = forbidden_measures & observed_measures
    if forbidden_hit:
        failures.append(f"forbidden measure codes present {sorted(forbidden_hit)}")

    return not failures, failures, observed


def _run_case(case: dict[str, Any], base_url: str, timeout: float, execute: bool) -> dict[str, Any]:
    started = time.time()
    surface = case.get("surface", "nlq")
    if surface != "nlq":
        return {
            "caseId": case.get("id"),
            "surface": surface,
            "passed": False,
            "classification": "infra_error",
            "failures": [f"unsupported eval surface {surface!r}"],
            "elapsedMs": 0,
        }

    status, response = _post_json(
        f"{base_url.rstrip('/')}/api/nlq/query",
        {
            "question": case["question"],
            "execute": bool(case.get("execute", execute)),
            "queryMode": case.get("query_mode") or "",
            "maxDimensions": int(case.get("max_dimensions", 5)),
            "pageSize": int(case.get("page_size", 100)),
            "resetContext": True,
        },
        timeout=timeout,
    )
    elapsed_ms = int((time.time() - started) * 1000)
    if status >= 500:
        return {
            "caseId": case.get("id"),
            "surface": surface,
            "passed": False,
            "classification": "infra_error",
            "status": status,
            "failures": [response.get("error") or response.get("detail") or f"HTTP {status}"],
            "elapsedMs": elapsed_ms,
            "response": response,
        }
    passed, failures, observed = _grade_nlq(case, response)
    return {
        "caseId": case.get("id"),
        "tier": case.get("tier"),
        "surface": surface,
        "question": case.get("question"),
        "passed": passed,
        "classification": "stable_pass" if passed else "stable_fail",
        "status": status,
        "failures": failures,
        "observed": observed,
        "elapsedMs": elapsed_ms,
        "response": response if not passed else None,
    }


def _classify_rounds(rounds: list[dict[str, Any]]) -> str:
    if any(item["classification"] == "infra_error" for item in rounds):
        return "infra_error"
    passed = [bool(item["passed"]) for item in rounds]
    if all(passed):
        return "stable_pass"
    if not any(passed):
        return "stable_fail"
    return "drift"


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="Run InsightMind eval suite")
    parser.add_argument("--cases", type=Path, default=DEFAULT_CASES)
    parser.add_argument("--base-url", default="http://127.0.0.1:8080")
    parser.add_argument("--output-dir", type=Path, default=DEFAULT_OUTPUT_DIR)
    parser.add_argument("--rounds", type=int, default=1)
    parser.add_argument("--timeout", type=float, default=20.0)
    parser.add_argument("--execute", action="store_true", help="Execute DA queries, not only NLQ planning")
    args = parser.parse_args(argv)

    cases = _read_cases(args.cases)
    args.output_dir.mkdir(parents=True, exist_ok=True)
    suite_started = dt.datetime.now(dt.timezone.utc).isoformat()
    results: list[dict[str, Any]] = []
    for case in cases:
        rounds = [
            _run_case(case, args.base_url, args.timeout, args.execute)
            for _ in range(max(1, args.rounds))
        ]
        results.append({
            "caseId": case.get("id"),
            "tier": case.get("tier"),
            "surface": case.get("surface", "nlq"),
            "question": case.get("question"),
            "classification": _classify_rounds(rounds),
            "passed": all(item.get("passed") for item in rounds),
            "rounds": rounds,
        })

    summary = {
        "startedAt": suite_started,
        "caseFile": str(args.cases),
        "baseUrl": args.base_url,
        "rounds": args.rounds,
        "total": len(results),
        "passed": sum(1 for item in results if item["passed"]),
        "failed": sum(1 for item in results if not item["passed"]),
        "classifications": {},
        "results": results,
    }
    for item in results:
        summary["classifications"][item["classification"]] = summary["classifications"].get(item["classification"], 0) + 1

    output_file = args.output_dir / "conformance-result.json"
    output_file.write_text(json.dumps(summary, ensure_ascii=False, indent=2), encoding="utf-8")
    print(json.dumps({k: summary[k] for k in ["total", "passed", "failed", "classifications"]}, ensure_ascii=False, indent=2))
    print(f"wrote {output_file}")
    return 0 if summary["failed"] == 0 else 1


if __name__ == "__main__":
    raise SystemExit(main())
