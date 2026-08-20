# Metric Insights

This package owns deterministic Forecast, Pace to Goal, and cross-metric discovery.
It deliberately does not generate Ask/Agent narratives, notifications, or action plans.

## Runtime APIs

- `POST /api/insights/forecast`
- `POST /api/insights/goals/pace`
- `GET /api/insights/goals/{goalId}/pace`
- `POST /api/insights/cross-metric/discover`
- `GET|POST /api/insights/cross-metric/candidates`
- `GET /api/insights/facts`
- `GET /api/insights/{factId}`
- `POST /api/insights/jobs/run`

The production query path uses AD's semantic service and forwards the caller's
`Authorization` header to DA. Supplied `series` payloads are supported for tests,
offline evaluation, and controlled backfills.

Forecast model selection uses rolling-origin backtests. A Seasonal Naive or Naive
model is always retained as the baseline; Holt and ETS are selected only when their
backtest metrics are competitive. Cross-metric ranking uses change correlations,
coverage and window stability, followed by Benjamini-Hochberg FDR correction.

## Database setup

Insight tables are created automatically in the configured AD MySQL database. For
existing DA metadata databases, apply:

```bash
mysql ... < apps/da/migrations/20260819_goal_insights.sql
```

The goal migration adds period, aggregation, favorable-direction, calendar, filter,
timezone, forecast-enable, and seasonal-period metadata required by Pace to Goal.
