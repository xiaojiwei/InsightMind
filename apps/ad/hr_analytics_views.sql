-- HRRDB analytics views used by the InsightMind HR business knowledge graph.
-- The seven imported source tables remain unchanged; these views only flatten
-- joins and expose auditable flags for KPI calculation.

USE `HRRDB`;

CREATE OR REPLACE VIEW `vw_hr_employee_profile` AS
WITH RECURSIVE `org_tree` AS (
    SELECT e.employee_id, 1 AS org_level
    FROM employees e
    WHERE e.manager_id IS NULL

    UNION ALL

    SELECT e.employee_id, ot.org_level + 1
    FROM employees e
    JOIN org_tree ot ON ot.employee_id = e.manager_id
),
`direct_reports` AS (
    SELECT manager_id, COUNT(*) AS direct_report_count
    FROM employees
    WHERE manager_id IS NOT NULL
    GROUP BY manager_id
),
`mobility` AS (
    SELECT employee_id, COUNT(*) AS mobility_event_count
    FROM job_history
    GROUP BY employee_id
)
SELECT
    e.employee_id,
    CONCAT_WS(' ', e.first_name, e.last_name) AS employee_name,
    e.first_name,
    e.last_name,
    e.email,
    e.phone_number,
    e.hire_date,
    CURRENT_DATE() AS snapshot_date,
    ROUND(TIMESTAMPDIFF(DAY, e.hire_date, CURRENT_DATE()) / 365.25, 2) AS tenure_years,
    CASE
        WHEN TIMESTAMPDIFF(MONTH, e.hire_date, CURRENT_DATE()) < 24 THEN '<2年'
        WHEN TIMESTAMPDIFF(MONTH, e.hire_date, CURRENT_DATE()) < 60 THEN '2-5年'
        WHEN TIMESTAMPDIFF(MONTH, e.hire_date, CURRENT_DATE()) < 120 THEN '5-10年'
        ELSE '10年以上'
    END COLLATE utf8mb4_unicode_ci AS tenure_band,
    COALESCE(ot.org_level, 0) AS org_level,
    CASE
        WHEN COALESCE(ot.org_level, 0) <= 1 THEN 'L1'
        WHEN ot.org_level = 2 THEN 'L2'
        WHEN ot.org_level = 3 THEN 'L3'
        WHEN ot.org_level = 4 THEN 'L4'
        ELSE 'L5+'
    END COLLATE utf8mb4_unicode_ci AS org_level_band,
    e.job_id,
    j.job_title,
    j.min_salary AS job_min_salary,
    j.max_salary AS job_max_salary,
    e.salary,
    ROUND((j.min_salary + j.max_salary) / 2, 2) AS salary_midpoint,
    ROUND(e.salary / NULLIF((j.min_salary + j.max_salary) / 2, 0), 4) AS compa_ratio,
    CASE
        WHEN e.salary / NULLIF((j.min_salary + j.max_salary) / 2, 0) < 0.80 THEN '<0.80'
        WHEN e.salary / NULLIF((j.min_salary + j.max_salary) / 2, 0) < 0.90 THEN '0.80-0.89'
        WHEN e.salary / NULLIF((j.min_salary + j.max_salary) / 2, 0) <= 1.10 THEN '0.90-1.10'
        WHEN e.salary / NULLIF((j.min_salary + j.max_salary) / 2, 0) <= 1.20 THEN '1.11-1.20'
        ELSE '>1.20'
    END COLLATE utf8mb4_unicode_ci AS compa_ratio_band,
    ROUND((e.salary - j.min_salary) / NULLIF(j.max_salary - j.min_salary, 0) * 100, 2)
        AS salary_range_penetration_pct,
    CASE
        WHEN e.salary < j.min_salary THEN '低于薪酬带'
        WHEN e.salary > j.max_salary THEN '高于薪酬带'
        ELSE '薪酬带内'
    END COLLATE utf8mb4_unicode_ci AS salary_band_status,
    CASE WHEN e.salary BETWEEN j.min_salary AND j.max_salary THEN 1 ELSE 0 END AS salary_in_range_flag,
    CASE WHEN e.salary < j.min_salary THEN 1 ELSE 0 END AS salary_below_range_flag,
    CASE WHEN e.salary > j.max_salary THEN 1 ELSE 0 END AS salary_above_range_flag,
    e.commission_pct,
    CASE WHEN e.commission_pct IS NOT NULL THEN 1 ELSE 0 END AS commission_eligible_flag,
    CASE WHEN e.commission_pct IS NOT NULL THEN '计提佣金' ELSE '不计提佣金' END COLLATE utf8mb4_unicode_ci AS commission_eligibility,
    e.manager_id,
    CONCAT_WS(' ', mgr.first_name, mgr.last_name) AS manager_name,
    CASE WHEN e.manager_id IS NOT NULL THEN 1 ELSE 0 END AS has_manager_flag,
    COALESCE(dr.direct_report_count, 0) AS direct_report_count,
    CASE WHEN COALESCE(dr.direct_report_count, 0) > 0 THEN 1 ELSE 0 END AS is_manager_flag,
    CASE WHEN COALESCE(dr.direct_report_count, 0) > 0 THEN '管理者' ELSE '非管理者' END COLLATE utf8mb4_unicode_ci AS manager_status,
    CASE WHEN e.employee_id = d.manager_id THEN 1 ELSE 0 END AS is_department_manager_flag,
    e.department_id,
    COALESCE(d.department_name, '未分配部门') AS department_name,
    d.manager_id AS department_manager_id,
    CONCAT_WS(' ', dm.first_name, dm.last_name) AS department_manager_name,
    d.location_id,
    l.city,
    l.state_province,
    l.country_id,
    c.country_name,
    c.region_id,
    r.region_name,
    COALESCE(mob.mobility_event_count, 0) AS mobility_event_count,
    CASE WHEN COALESCE(mob.mobility_event_count, 0) > 0 THEN 1 ELSE 0 END AS has_mobility_history_flag,
    CASE WHEN COALESCE(mob.mobility_event_count, 0) > 0 THEN '有流动记录' ELSE '无流动记录' END COLLATE utf8mb4_unicode_ci AS mobility_history_status
FROM employees e
JOIN jobs j ON j.job_id = e.job_id
LEFT JOIN departments d ON d.department_id = e.department_id
LEFT JOIN employees mgr ON mgr.employee_id = e.manager_id
LEFT JOIN employees dm ON dm.employee_id = d.manager_id
LEFT JOIN locations l ON l.location_id = d.location_id
LEFT JOIN countries c ON c.country_id = l.country_id
LEFT JOIN regions r ON r.region_id = c.region_id
LEFT JOIN org_tree ot ON ot.employee_id = e.employee_id
LEFT JOIN direct_reports dr ON dr.manager_id = e.employee_id
LEFT JOIN mobility mob ON mob.employee_id = e.employee_id;

CREATE OR REPLACE VIEW `vw_hr_department_profile` AS
SELECT
    d.department_id,
    d.department_name,
    d.manager_id,
    CONCAT_WS(' ', mgr.first_name, mgr.last_name) AS manager_name,
    CASE WHEN d.manager_id IS NOT NULL THEN 1 ELSE 0 END AS has_manager_flag,
    d.location_id,
    l.city,
    l.state_province,
    l.country_id,
    c.country_name,
    c.region_id,
    r.region_name,
    COUNT(e.employee_id) AS headcount,
    COALESCE(SUM(e.salary), 0) AS monthly_base_payroll,
    ROUND(AVG(e.salary), 2) AS average_salary,
    CASE WHEN COUNT(e.employee_id) > 0 THEN 1 ELSE 0 END AS staffed_flag,
    CASE
        WHEN COUNT(e.employee_id) > 0 AND d.manager_id IS NOT NULL THEN 1 ELSE 0
    END AS staffed_with_manager_flag,
    CASE WHEN COUNT(e.employee_id) > 0 THEN '有员工' ELSE '空部门' END COLLATE utf8mb4_unicode_ci AS staffing_status,
    CASE WHEN d.manager_id IS NOT NULL THEN '已配置负责人' ELSE '未配置负责人' END COLLATE utf8mb4_unicode_ci AS manager_assignment_status,
    CASE WHEN COUNT(e.employee_id) = 0 THEN 1 ELSE 0 END AS empty_department_flag,
    CASE WHEN COUNT(e.employee_id) = 1 THEN 1 ELSE 0 END AS single_person_department_flag,
    CASE
        WHEN COUNT(e.employee_id) = 0 THEN '0人'
        WHEN COUNT(e.employee_id) = 1 THEN '1人'
        WHEN COUNT(e.employee_id) <= 5 THEN '2-5人'
        WHEN COUNT(e.employee_id) <= 10 THEN '6-10人'
        WHEN COUNT(e.employee_id) <= 20 THEN '11-20人'
        ELSE '20人以上'
    END COLLATE utf8mb4_unicode_ci AS department_size_band,
    CURRENT_DATE() AS snapshot_date
FROM departments d
LEFT JOIN employees e ON e.department_id = d.department_id
LEFT JOIN employees mgr ON mgr.employee_id = d.manager_id
LEFT JOIN locations l ON l.location_id = d.location_id
LEFT JOIN countries c ON c.country_id = l.country_id
LEFT JOIN regions r ON r.region_id = c.region_id
GROUP BY
    d.department_id, d.department_name, d.manager_id, mgr.first_name, mgr.last_name,
    d.location_id, l.city, l.state_province, l.country_id, c.country_name,
    c.region_id, r.region_name;

CREATE OR REPLACE VIEW `vw_hr_career_mobility` AS
WITH `history_ranked` AS (
    SELECT
        h.*,
        LEAD(h.job_id) OVER (
            PARTITION BY h.employee_id ORDER BY h.start_date, h.end_date
        ) AS next_history_job_id,
        LEAD(h.department_id) OVER (
            PARTITION BY h.employee_id ORDER BY h.start_date, h.end_date
        ) AS next_history_department_id
    FROM job_history h
)
SELECT
    CONCAT(hr.employee_id, '_', DATE_FORMAT(hr.start_date, '%Y%m%d')) AS mobility_event_id,
    hr.employee_id,
    CONCAT_WS(' ', e.first_name, e.last_name) AS employee_name,
    hr.start_date,
    hr.end_date,
    DATE(hr.end_date) AS event_date,
    DATEDIFF(hr.end_date, hr.start_date) AS assignment_duration_days,
    ROUND(DATEDIFF(hr.end_date, hr.start_date) / 365.25, 2) AS assignment_duration_years,
    hr.job_id AS from_job_id,
    from_job.job_title AS from_job_title,
    COALESCE(hr.next_history_job_id, e.job_id) AS to_job_id,
    to_job.job_title AS to_job_title,
    hr.department_id AS from_department_id,
    from_dept.department_name AS from_department_name,
    COALESCE(hr.next_history_department_id, e.department_id) AS to_department_id,
    to_dept.department_name AS to_department_name,
    CASE
        WHEN NOT (hr.job_id <=> COALESCE(hr.next_history_job_id, e.job_id))
         AND NOT (hr.department_id <=> COALESCE(hr.next_history_department_id, e.department_id))
            THEN '岗位及部门变动'
        WHEN NOT (hr.job_id <=> COALESCE(hr.next_history_job_id, e.job_id)) THEN '岗位变动'
        WHEN NOT (hr.department_id <=> COALESCE(hr.next_history_department_id, e.department_id)) THEN '部门调动'
        ELSE '续任/数据校正'
    END COLLATE utf8mb4_unicode_ci AS mobility_type,
    CASE WHEN NOT (hr.job_id <=> COALESCE(hr.next_history_job_id, e.job_id)) THEN 1 ELSE 0 END
        AS job_change_flag,
    CASE WHEN NOT (hr.department_id <=> COALESCE(hr.next_history_department_id, e.department_id)) THEN 1 ELSE 0 END
        AS department_change_flag,
    CASE WHEN to_job.min_salary > from_job.min_salary THEN 1 ELSE 0 END AS promotion_proxy_flag,
    from_dept.location_id,
    l.city,
    l.country_id,
    c.country_name,
    c.region_id,
    r.region_name
FROM history_ranked hr
JOIN employees e ON e.employee_id = hr.employee_id
JOIN jobs from_job ON from_job.job_id = hr.job_id
JOIN jobs to_job ON to_job.job_id = COALESCE(hr.next_history_job_id, e.job_id)
LEFT JOIN departments from_dept ON from_dept.department_id = hr.department_id
LEFT JOIN departments to_dept ON to_dept.department_id = COALESCE(hr.next_history_department_id, e.department_id)
LEFT JOIN locations l ON l.location_id = from_dept.location_id
LEFT JOIN countries c ON c.country_id = l.country_id
LEFT JOIN regions r ON r.region_id = c.region_id;
