# InsightMind Eval Harness

This directory contains executable evaluation contracts for InsightMind.

The first milestone is conformance: whether the system chooses the right action
(`answer`, `clarify`, `reject`, or `error`) and aligns to the expected KG
surface: measure, dimension, and fact table.

## Run

From the repository root:

```bash
./scripts/eval.sh conformance
./scripts/eval.sh conformance --rounds 5
```

Results are written to:

```text
apps/ad/output/evals/latest/conformance-result.json
```

## Case Format

Cases live in `conformance/cases.jsonl`. Each line is one JSON object.

Supported fields:

- `id`: stable case ID.
- `tier`: `p0`, `p1`, or `p2`.
- `surface`: currently `nlq`.
- `question`: user question sent to `/api/nlq/query`.
- `expected_action`: `answer`, `clarify`, `reject`, or `error`.
- `expected_diagnostic_code`: required for structured refusal/error cases.
- `expected_measure_codes`: subset of measure codes that must be matched.
- `expected_dimension_codes`: subset of dimension codes that must be matched.
- `expected_fact_tables`: subset of fact tables that must appear in KG evidence.
- `forbidden_measure_codes`: measure codes that must not be matched.

Dimension and fact-table checks are subset checks by design. Some planners add
recommended drill dimensions; those should not fail a conformance case unless a
forbidden item is present.

## Current Scope

The current runner intentionally does not grade numeric truth. It answers:

- Is the system allowed to answer the question?
- Did it match the expected metric?
- Did it include the expected dimensions?
- Did it trace the metric to the expected fact table?
- Did unsupported questions produce a structured rejection?

## Next Milestones

1. Add numeric truth specs with independent SQL and result fixtures.
2. Add baseline comparison for multi-round runs.
3. Add SQL API and Ad-Hoc surfaces.
4. Add KG path grading for expected fact table, physical column, and join path.
