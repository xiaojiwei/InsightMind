#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SUITE="${1:-conformance}"
shift || true

case "$SUITE" in
  conformance|all)
    PYTHONPATH="$ROOT/apps/ad" python3 "$ROOT/apps/ad/evals/run_suite.py" "$@"
    ;;
  *)
    echo "Unknown eval suite: $SUITE" >&2
    echo "Usage: ./scripts/eval.sh [conformance|all] [run_suite.py args...]" >&2
    exit 2
    ;;
esac
