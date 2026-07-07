from __future__ import annotations

import argparse
import json
import sys
import urllib.request
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
GOLDEN_PATH = ROOT / "tests" / "golden_nlq_cases.json"


def _post_json(url: str, payload: dict) -> dict:
    req = urllib.request.Request(
        url,
        data=json.dumps(payload, ensure_ascii=False).encode("utf-8"),
        headers={"Content-Type": "application/json"},
        method="POST",
    )
    with urllib.request.urlopen(req, timeout=90) as resp:
        return json.loads(resp.read().decode("utf-8"))


def _match_expected(response: dict, expected: dict) -> tuple[bool, str]:
    if expected.get("queryMode"):
        mode = response.get("queryMode")
        exp = expected["queryMode"]
        if exp == "detail_or_analyze_detail":
            if mode not in {"detail", "analyze_detail"}:
                return False, f"queryMode={mode}, expected detail/analyze_detail"
        elif mode != exp:
            return False, f"queryMode={mode}, expected {exp}"
    if expected.get("measureCode"):
        got = (response.get("matched") or {}).get("measureCode")
        if got != expected["measureCode"]:
            return False, f"measureCode={got}, expected {expected['measureCode']}"
    if expected.get("dimensionName"):
        dims = (response.get("matched") or {}).get("dimensions") or []
        names = {d.get("name") for d in dims if isinstance(d, dict)}
        if expected["dimensionName"] not in names:
            return False, f"dimensionName missing {expected['dimensionName']}"
    if expected.get("trace") and not response.get("traceId"):
        return False, "missing traceId"
    if expected.get("evidence") and not response.get("evidence"):
        return False, "missing evidence"
    if expected.get("validation") and not response.get("validation"):
        return False, "missing validation"
    return True, ""


def main() -> int:
    parser = argparse.ArgumentParser(description="Run NLQ golden regression cases against a running AD service.")
    parser.add_argument("--base-url", default="http://localhost:8080")
    parser.add_argument("--category", default="")
    parser.add_argument("--limit", type=int, default=0)
    args = parser.parse_args()

    cases = json.loads(GOLDEN_PATH.read_text(encoding="utf-8"))
    if args.category:
        cases = [case for case in cases if case.get("category") == args.category]
    if args.limit:
        cases = cases[: args.limit]

    passed = 0
    failed = []
    for case in cases:
        payload = {
            "question": case["question"],
            "execute": True,
            "pageSize": 100,
            "pageNum": 1,
            "queryMode": "auto",
            "resetContext": True,
        }
        try:
            response = _post_json(f"{args.base_url.rstrip('/')}/api/nlq/query", payload)
            ok, reason = _match_expected(response, case.get("expected") or {})
            if ok:
                passed += 1
            else:
                failed.append({"id": case["id"], "question": case["question"], "reason": reason})
        except Exception as exc:
            failed.append({"id": case["id"], "question": case["question"], "reason": str(exc)})

    print(json.dumps({"total": len(cases), "passed": passed, "failed": failed}, ensure_ascii=False, indent=2))
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main())
