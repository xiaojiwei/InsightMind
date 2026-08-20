-- Goal semantics required by Forecast and Pace to Goal.
-- Apply once to existing DA metadata databases before enabling the AD insight APIs.
ALTER TABLE goal
  ADD COLUMN period_start DATE DEFAULT NULL AFTER real_num,
  ADD COLUMN period_end DATE DEFAULT NULL AFTER period_start,
  ADD COLUMN aggregation_type VARCHAR(20) NOT NULL DEFAULT 'SUM' AFTER period_end,
  ADD COLUMN favorable_direction VARCHAR(20) NOT NULL DEFAULT 'HIGHER' AFTER aggregation_type,
  ADD COLUMN lower_bound DECIMAL(20,4) DEFAULT NULL AFTER favorable_direction,
  ADD COLUMN upper_bound DECIMAL(20,4) DEFAULT NULL AFTER lower_bound,
  ADD COLUMN calendar_code VARCHAR(64) NOT NULL DEFAULT 'NATURAL' AFTER upper_bound,
  ADD COLUMN filters_json TEXT AFTER calendar_code,
  ADD COLUMN timezone VARCHAR(64) NOT NULL DEFAULT 'Asia/Shanghai' AFTER filters_json,
  ADD COLUMN forecast_enabled TINYINT(1) NOT NULL DEFAULT 1 AFTER timezone,
  ADD COLUMN seasonal_period INT DEFAULT NULL AFTER forecast_enabled;
