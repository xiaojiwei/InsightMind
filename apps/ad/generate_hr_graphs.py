#!/usr/bin/env python3
"""Build deterministic HRRDB source and business knowledge graphs.

The business graph follows the ISO 30414:2025 human-capital-area structure,
but only activates metrics that the imported HR sample can calculate. Missing
recruiting, turnover, diversity, learning, performance, engagement, and
well-being domains remain explicit roadmap categories rather than fabricated
measures.
"""
from __future__ import annotations

import argparse
import json
import shutil
import time
from dataclasses import dataclass
from pathlib import Path
from typing import Any

import yaml
from rdflib import Graph, Literal, Namespace, RDF, XSD
from rdflib.namespace import OWL, RDFS
from sqlalchemy import text

from kg_builder.analytics.table_classifier import classify
from kg_builder.business_kg.llm_builder import _ONTOLOGY_PREAMBLE
from kg_builder.connectors.base import DataSourceConfig
from kg_builder.connectors.mysql import MySQLConnector
from kg_builder.entities.extractor import EntityExtractor
from kg_builder.ontology.rdf_builder import RDFBuilder
from kg_builder.parsers.data_sampler import DataSampler
from kg_builder.parsers.schema_parser import SchemaParser
from kg_builder.relations.explicit import ExplicitRelationExtractor


BASE_DIR = Path(__file__).resolve().parent
OUTPUT_DIR = BASE_DIR / "output"
BKG_DIR = OUTPUT_DIR / "business_kg"
SOURCE_OUTPUT = OUTPUT_DIR / "hr-source-kg.ttl"
ACTIVE_SOURCE_OUTPUT = OUTPUT_DIR / "kg.ttl"
BUSINESS_OUTPUT = BKG_DIR / "indicator-data.ttl"
INFERRED_OUTPUT = BKG_DIR / "indicator-inferred.ttl"
SOURCE_MANIFEST = BKG_DIR / "indicator-data.source.json"

IND = Namespace("http://indicator.insightmind.com/ontology#")
INST = Namespace("http://indicator.insightmind.com/instance/")

FACT_EMPLOYEE = "vw_hr_employee_profile"
FACT_DEPARTMENT = "vw_hr_department_profile"
FACT_MOBILITY = "vw_hr_career_mobility"
FACT_TABLES = (FACT_EMPLOYEE, FACT_DEPARTMENT, FACT_MOBILITY)


@dataclass(frozen=True)
class Metric:
    code: str
    cn_name: str
    unit: str
    category: str
    role: str
    table: str
    definition: str
    caliber: str
    column: str = ""
    operator: str = ""
    numerator: str = ""
    denominator: str = ""
    target: str = ""
    quality_note: str = ""
    north_star: int = 0

    @property
    def derived(self) -> bool:
        return bool(self.numerator and self.denominator)


@dataclass(frozen=True)
class Dimension:
    code: str
    cn_name: str
    definition: str
    apps: tuple[tuple[str, str], ...]
    category: str = "CAT_HR_WORKFORCE"
    view_type: int = 0
    hierarchy: str = ""
    level_code: str = ""
    level_sequence: int = 0


def atom(
    code: str,
    cn_name: str,
    unit: str,
    category: str,
    role: str,
    table: str,
    column: str,
    operator: str,
    definition: str,
    caliber: str,
    *,
    target: str = "",
    quality_note: str = "",
    north_star: int = 0,
) -> Metric:
    return Metric(
        code, cn_name, unit, category, role, table, definition, caliber,
        column=column, operator=operator, target=target,
        quality_note=quality_note, north_star=north_star,
    )


def ratio(
    code: str,
    cn_name: str,
    unit: str,
    category: str,
    role: str,
    table: str,
    numerator: str,
    denominator: str,
    definition: str,
    caliber: str,
    *,
    target: str = "",
    quality_note: str = "",
) -> Metric:
    return Metric(
        code, cn_name, unit, category, role, table, definition, caliber,
        numerator=numerator, denominator=denominator, target=target,
        quality_note=quality_note,
    )


CATEGORIES = (
    (1, "CAT_HR", "人力资本指标", "Human Capital Metrics", "", "HR 指标总分类，按 ISO 30414:2025 人力资本领域组织。"),
    (10, "CAT_HR_WORKFORCE", "劳动力构成", "Workforce Composition", "CAT_HR", "在册规模、任期、组织层级和人员构成。"),
    (20, "CAT_HR_DIVERSITY", "多元化", "Diversity", "CAT_HR", "路线图：需补充性别、年龄、残障、国籍等合规人口属性。"),
    (30, "CAT_HR_COST", "人力成本与薪酬", "Costs and Reward", "CAT_HR", "基本薪酬、薪酬带位置和佣金适用性。"),
    (40, "CAT_HR_PRODUCTIVITY", "生产力", "Productivity", "CAT_HR", "路线图：需补充业务产出、工时或收入数据。"),
    (50, "CAT_HR_WELLBEING", "健康安全与福祉", "Health Safety and Well-being", "CAT_HR", "路线图：需补充缺勤、工伤、健康与福祉数据。"),
    (60, "CAT_HR_LEADERSHIP", "领导力与组织健康", "Leadership Culture and Engagement", "CAT_HR", "管理覆盖、管理幅度、部门配置和组织结构健康。"),
    (70, "CAT_HR_COMPLIANCE", "合规伦理与劳资关系", "Compliance Ethics and Workforce Relations", "CAT_HR", "路线图：需补充申诉、纪律、合规与劳资关系数据。"),
    (80, "CAT_HR_RECRUITMENT", "招聘", "Recruitment", "CAT_HR", "路线图：需补充职位申请、候选人、招聘成本、录用与到岗日期。"),
    (90, "CAT_HR_MOBILITY", "流动与继任", "Mobility and Succession", "CAT_HR", "历史任职、岗位变动、部门调动与晋升代理。"),
    (100, "CAT_HR_TURNOVER", "人员流失", "Workforce Turnover", "CAT_HR", "路线图：需补充在离职状态、离职日期、原因和可避免性。"),
    (110, "CAT_HR_SKILLS", "技能能力与发展", "Skills Capabilities and Development", "CAT_HR", "路线图：需补充技能、培训、认证、绩效与继任准备度。"),
)


METRICS = (
    atom("MEAS_workforce_headcount", "在册人数", "人", "CAT_HR_WORKFORCE", "primary", FACT_EMPLOYEE,
         "employee_id", "distinct_count", "当前人员快照中的唯一员工数。",
         "COUNT(DISTINCT employee_id)；employees 被视为当前在册快照。",
         target="按月与编制预算和业务需求比较，不设置跨行业通用人数目标。",
         quality_note="源表缺少在离职状态；当前口径默认 employees 中全部记录均在册。", north_star=1),
    atom("MEAS_monthly_base_payroll", "月度基本薪酬总额", "薪酬单位/月", "CAT_HR_COST", "primary", FACT_EMPLOYEE,
         "salary", "sum", "当前在册人员的基本薪酬总额。",
         "SUM(salary)；未包含奖金、福利、雇主税费及佣金实际金额。",
         target="与批准的人力成本预算、编制和业务产出联动管理。",
         quality_note="salary 的币种和支付周期未在源表中显式记录。", north_star=1),
    atom("MEAS_average_base_salary", "平均基本薪酬", "薪酬单位/月", "CAT_HR_COST", "driver", FACT_EMPLOYEE,
         "salary", "avg", "当前在册人员的平均基本薪酬。", "AVG(salary)。"),
    atom("MEAS_average_tenure_years", "平均司龄", "年", "CAT_HR_WORKFORCE", "driver", FACT_EMPLOYEE,
         "tenure_years", "avg", "员工自 hire_date 起至当前日期的平均年数。", "AVG(tenure_years)。",
         quality_note="4 条历史任职记录早于员工 hire_date，使用前应核实历史日期口径。"),
    atom("MEAS_average_org_level", "平均组织层级", "层", "CAT_HR_LEADERSHIP", "driver", FACT_EMPLOYEE,
         "org_level", "avg", "从最高负责人开始计算的平均汇报层级。", "AVG(org_level)；根节点层级为1。"),
    atom("MEAS_manager_headcount", "管理者人数", "人", "CAT_HR_LEADERSHIP", "driver", FACT_EMPLOYEE,
         "is_manager_flag", "sum", "至少拥有一名直接下属的员工数。", "SUM(is_manager_flag)。"),
    atom("MEAS_managed_employee_headcount", "有直属经理人数", "人", "CAT_HR_LEADERSHIP", "driver", FACT_EMPLOYEE,
         "has_manager_flag", "sum", "manager_id 非空的员工数。", "SUM(has_manager_flag)。"),
    atom("MEAS_total_direct_reports", "直属下属总数", "人次", "CAT_HR_LEADERSHIP", "driver", FACT_EMPLOYEE,
         "direct_report_count", "sum", "所有管理者的直属下属数之和。", "SUM(direct_report_count)。"),
    atom("MEAS_salary_in_range_headcount", "薪酬带内人数", "人", "CAT_HR_COST", "driver", FACT_EMPLOYEE,
         "salary_in_range_flag", "sum", "基本薪酬位于岗位最低与最高薪酬之间的人数。", "SUM(salary_in_range_flag)。"),
    atom("MEAS_salary_below_range_headcount", "低于薪酬带人数", "人", "CAT_HR_COST", "guardrail", FACT_EMPLOYEE,
         "salary_below_range_flag", "sum", "基本薪酬低于岗位最低薪酬的人数。", "SUM(salary_below_range_flag)。", target="目标为0；例外需有审批与整改计划。"),
    atom("MEAS_salary_above_range_headcount", "高于薪酬带人数", "人", "CAT_HR_COST", "guardrail", FACT_EMPLOYEE,
         "salary_above_range_flag", "sum", "基本薪酬高于岗位最高薪酬的人数。", "SUM(salary_above_range_flag)。", target="目标为0；例外需有审批与岗位评估。"),
    atom("MEAS_average_compa_ratio", "平均薪酬中位比", "比值", "CAT_HR_COST", "driver", FACT_EMPLOYEE,
         "compa_ratio", "avg", "员工薪酬相对岗位薪酬带中点的平均比值。", "AVG(salary / salary_midpoint)。",
         target="按岗位族、职级和绩效分层解释，不使用单一全员硬目标。"),
    atom("MEAS_average_salary_range_penetration", "平均薪酬带渗透率", "%", "CAT_HR_COST", "driver", FACT_EMPLOYEE,
         "salary_range_penetration_pct", "avg", "员工薪酬在岗位薪酬带中的平均位置。",
         "AVG((salary-min_salary)/(max_salary-min_salary)*100)。"),
    atom("MEAS_commission_eligible_headcount", "佣金适用人数", "人", "CAT_HR_COST", "driver", FACT_EMPLOYEE,
         "commission_eligible_flag", "sum", "commission_pct 非空的员工数。", "SUM(commission_eligible_flag)。"),
    atom("MEAS_employees_with_mobility_history", "有流动记录人数", "人", "CAT_HR_MOBILITY", "driver", FACT_EMPLOYEE,
         "has_mobility_history_flag", "sum", "至少有一条 job_history 记录的当前员工数。", "SUM(has_mobility_history_flag)。",
         quality_note="只反映已加载的10条历史记录，不等同于标准年度内部流动率。"),
    atom("MEAS_recorded_mobility_event_count", "当前人员历史流动记录数", "次", "CAT_HR_MOBILITY", "driver", FACT_EMPLOYEE,
         "mobility_event_count", "sum", "按当前员工汇总的历史任职记录数。", "SUM(mobility_event_count)。"),
    atom("MEAS_department_manager_headcount", "部门负责人数量", "人", "CAT_HR_LEADERSHIP", "driver", FACT_EMPLOYEE,
         "is_department_manager_flag", "sum", "被配置为部门 manager_id 的当前员工数。", "SUM(is_department_manager_flag)。"),
    ratio("MEAS_manager_ratio", "管理者比例", "%", "CAT_HR_LEADERSHIP", "guardrail", FACT_EMPLOYEE,
          "MEAS_manager_headcount", "MEAS_workforce_headcount", "管理者人数占在册人数的比例。",
          "管理者人数 / 在册人数；结果为比率。",
          target="按业务模式和组织层级设置控制带，优先采用自身历史分位数。"),
    ratio("MEAS_manager_coverage_rate", "直属经理覆盖率", "%", "CAT_HR_LEADERSHIP", "guardrail", FACT_EMPLOYEE,
          "MEAS_managed_employee_headcount", "MEAS_workforce_headcount", "具有直属经理的员工占比。",
          "有直属经理人数 / 在册人数；最高负责人无经理属于合理例外。",
          target="除最高负责人和批准例外外应接近100%。"),
    ratio("MEAS_average_span_of_control", "平均管理幅度", "人/管理者", "CAT_HR_LEADERSHIP", "guardrail", FACT_EMPLOYEE,
          "MEAS_total_direct_reports", "MEAS_manager_headcount", "每名管理者平均直接下属数。",
          "直属下属总数 / 管理者人数。",
          target="按管理层级和工作复杂度设上下限；先以自身历史P25-P75建立控制带。"),
    ratio("MEAS_salary_band_compliance_rate", "薪酬带合规率", "%", "CAT_HR_COST", "guardrail", FACT_EMPLOYEE,
          "MEAS_salary_in_range_headcount", "MEAS_workforce_headcount", "基本薪酬位于批准岗位薪酬带内的人员占比。",
          "薪酬带内人数 / 在册人数。", target="临时治理阈值≥98%，正式目标应以薪酬政策为准。"),
    ratio("MEAS_commission_eligibility_rate", "佣金适用率", "%", "CAT_HR_COST", "driver", FACT_EMPLOYEE,
          "MEAS_commission_eligible_headcount", "MEAS_workforce_headcount", "佣金适用人员占在册人员的比例。",
          "佣金适用人数 / 在册人数。"),
    ratio("MEAS_internal_mobility_coverage_rate", "内部流动记录覆盖率", "%", "CAT_HR_MOBILITY", "driver", FACT_EMPLOYEE,
          "MEAS_employees_with_mobility_history", "MEAS_workforce_headcount", "当前员工中存在历史任职记录的占比。",
          "有流动记录人数 / 在册人数；这是数据覆盖与历史流动代理，不是年度内部流动率。",
          quality_note="缺少完整生效日期、离职与全部任职版本，禁止解释为标准内部流动率。"),
    ratio("MEAS_average_recorded_mobility_events", "人均历史流动记录数", "次/人", "CAT_HR_MOBILITY", "driver", FACT_EMPLOYEE,
          "MEAS_recorded_mobility_event_count", "MEAS_workforce_headcount", "每名当前员工对应的历史任职记录数。",
          "历史流动记录数 / 在册人数。"),

    atom("MEAS_department_count", "部门总数", "个", "CAT_HR_WORKFORCE", "driver", FACT_DEPARTMENT,
         "department_id", "distinct_count", "组织架构中的唯一部门数。", "COUNT(DISTINCT department_id)。"),
    atom("MEAS_staffed_department_count", "有员部门数", "个", "CAT_HR_WORKFORCE", "driver", FACT_DEPARTMENT,
         "staffed_flag", "sum", "至少配置一名员工的部门数。", "SUM(staffed_flag)。"),
    atom("MEAS_staffed_managed_department_count", "有员且有负责人部门数", "个", "CAT_HR_LEADERSHIP", "driver", FACT_DEPARTMENT,
         "staffed_with_manager_flag", "sum", "有员工且已配置部门负责人的部门数。", "SUM(staffed_with_manager_flag)。"),
    atom("MEAS_empty_department_count", "空部门数", "个", "CAT_HR_LEADERSHIP", "guardrail", FACT_DEPARTMENT,
         "empty_department_flag", "sum", "当前没有员工的部门数。", "SUM(empty_department_flag)。",
         target="逐个确认是否为规划编制、冻结组织或待清理主数据。"),
    atom("MEAS_single_person_department_count", "单人部门数", "个", "CAT_HR_LEADERSHIP", "guardrail", FACT_DEPARTMENT,
         "single_person_department_flag", "sum", "当前仅一名员工的部门数。", "SUM(single_person_department_flag)。"),
    atom("MEAS_department_monthly_payroll", "部门月度基本薪酬总额", "薪酬单位/月", "CAT_HR_COST", "driver", FACT_DEPARTMENT,
         "monthly_base_payroll", "sum", "按部门快照汇总的基本薪酬总额。", "SUM(monthly_base_payroll)。"),
    atom("MEAS_average_department_size", "平均部门规模", "人/部门", "CAT_HR_LEADERSHIP", "driver", FACT_DEPARTMENT,
         "headcount", "avg", "所有组织部门的平均人数，包含空部门。", "AVG(headcount)。"),
    ratio("MEAS_staffed_department_rate", "部门启用率", "%", "CAT_HR_WORKFORCE", "driver", FACT_DEPARTMENT,
          "MEAS_staffed_department_count", "MEAS_department_count", "至少有一名员工的部门占全部部门的比例。",
          "有员部门数 / 部门总数。"),
    ratio("MEAS_staffed_department_manager_rate", "有员部门负责人配置率", "%", "CAT_HR_LEADERSHIP", "guardrail", FACT_DEPARTMENT,
          "MEAS_staffed_managed_department_count", "MEAS_staffed_department_count", "有员部门中已配置负责人的比例。",
          "有员且有负责人部门数 / 有员部门数。", target="目标100%，批准的临时代理管理需单独记录。"),
    ratio("MEAS_payroll_per_staffed_department", "每有员部门平均薪酬总额", "薪酬单位/月/部门", "CAT_HR_COST", "driver", FACT_DEPARTMENT,
          "MEAS_department_monthly_payroll", "MEAS_staffed_department_count", "每个有员部门承载的平均月度基本薪酬。",
          "部门月度基本薪酬总额 / 有员部门数。"),

    atom("MEAS_mobility_event_count", "内部流动事件数", "次", "CAT_HR_MOBILITY", "driver", FACT_MOBILITY,
         "mobility_event_id", "distinct_count", "job_history 中可映射到下一任岗位的历史事件数。",
         "COUNT(DISTINCT mobility_event_id)。",
         quality_note="源数据仅10条历史记录，且4条开始日期早于员工hire_date。"),
    atom("MEAS_employees_with_mobility_event", "发生流动员工数", "人", "CAT_HR_MOBILITY", "driver", FACT_MOBILITY,
         "employee_id", "distinct_count", "至少有一条内部流动事件的唯一员工数。", "COUNT(DISTINCT employee_id)。"),
    atom("MEAS_average_assignment_duration_years", "平均历史任职时长", "年", "CAT_HR_MOBILITY", "driver", FACT_MOBILITY,
         "assignment_duration_years", "avg", "历史任职区间的平均持续年数。", "AVG(assignment_duration_years)。"),
    atom("MEAS_job_change_event_count", "岗位变动事件数", "次", "CAT_HR_MOBILITY", "driver", FACT_MOBILITY,
         "job_change_flag", "sum", "下一任岗位与当前历史岗位不同的事件数。", "SUM(job_change_flag)。"),
    atom("MEAS_department_transfer_event_count", "部门调动事件数", "次", "CAT_HR_MOBILITY", "driver", FACT_MOBILITY,
         "department_change_flag", "sum", "下一任部门与当前历史部门不同的事件数。", "SUM(department_change_flag)。"),
    atom("MEAS_promotion_proxy_event_count", "晋升代理事件数", "次", "CAT_HR_MOBILITY", "driver", FACT_MOBILITY,
         "promotion_proxy_flag", "sum", "下一岗位最低薪酬高于上一岗位最低薪酬的流动事件数。",
         "SUM(promotion_proxy_flag)；仅为岗位薪酬带升级代理。",
         quality_note="没有职级、绩效或正式晋升动作字段，不能作为真实晋升率。"),
    ratio("MEAS_promotion_proxy_share", "晋升代理事件占比", "%", "CAT_HR_MOBILITY", "driver", FACT_MOBILITY,
          "MEAS_promotion_proxy_event_count", "MEAS_mobility_event_count", "晋升代理事件占全部历史流动事件的比例。",
          "晋升代理事件数 / 内部流动事件数。",
          quality_note="仅用于发现可能的向上岗位流动，不是正式晋升率。"),
    ratio("MEAS_department_transfer_share", "跨部门流动占比", "%", "CAT_HR_MOBILITY", "driver", FACT_MOBILITY,
          "MEAS_department_transfer_event_count", "MEAS_mobility_event_count", "跨部门调动事件占历史流动事件的比例。",
          "部门调动事件数 / 内部流动事件数。"),
)


DIMENSIONS = (
    Dimension("DIM_snapshot_year", "快照年", "人员与部门快照的年粒度。", ((FACT_EMPLOYEE, "snapshot_date"), (FACT_DEPARTMENT, "snapshot_date")),
              view_type=5, hierarchy="HIER_HR_SNAPSHOT_DATE", level_code="year", level_sequence=1),
    Dimension("DIM_snapshot_month", "快照月", "人员与部门快照的月粒度。", ((FACT_EMPLOYEE, "snapshot_date"), (FACT_DEPARTMENT, "snapshot_date")),
              view_type=3, hierarchy="HIER_HR_SNAPSHOT_DATE", level_code="month", level_sequence=2),
    Dimension("DIM_snapshot_day", "快照日", "人员与部门快照的日粒度。", ((FACT_EMPLOYEE, "snapshot_date"), (FACT_DEPARTMENT, "snapshot_date")),
              view_type=1, hierarchy="HIER_HR_SNAPSHOT_DATE", level_code="day", level_sequence=3),
    Dimension("DIM_hire_year", "入职年", "员工入职日期的年粒度。", ((FACT_EMPLOYEE, "hire_date"),),
              view_type=5, hierarchy="HIER_HR_HIRE_DATE", level_code="year", level_sequence=1),
    Dimension("DIM_hire_month", "入职月", "员工入职日期的月粒度。", ((FACT_EMPLOYEE, "hire_date"),),
              view_type=3, hierarchy="HIER_HR_HIRE_DATE", level_code="month", level_sequence=2),
    Dimension("DIM_mobility_event_year", "流动事件年", "历史任职结束日期的年粒度。", ((FACT_MOBILITY, "event_date"),),
              category="CAT_HR_MOBILITY", view_type=5, hierarchy="HIER_HR_EVENT_DATE", level_code="year", level_sequence=1),
    Dimension("DIM_mobility_event_month", "流动事件月", "历史任职结束日期的月粒度。", ((FACT_MOBILITY, "event_date"),),
              category="CAT_HR_MOBILITY", view_type=3, hierarchy="HIER_HR_EVENT_DATE", level_code="month", level_sequence=2),
    Dimension("DIM_mobility_event_day", "流动事件日", "历史任职结束日期的日粒度。", ((FACT_MOBILITY, "event_date"),),
              category="CAT_HR_MOBILITY", view_type=1, hierarchy="HIER_HR_EVENT_DATE", level_code="day", level_sequence=3),
    Dimension("DIM_employee", "员工", "员工姓名，用于受控明细分析。", ((FACT_EMPLOYEE, "employee_name"), (FACT_MOBILITY, "employee_name"))),
    Dimension("DIM_department", "部门", "当前组织部门。", ((FACT_EMPLOYEE, "department_name"), (FACT_DEPARTMENT, "department_name")),
              hierarchy="HIER_HR_ORG", level_code="department", level_sequence=1),
    Dimension("DIM_job", "岗位", "员工当前岗位。", ((FACT_EMPLOYEE, "job_title"),)),
    Dimension("DIM_manager", "直属经理", "员工当前直属经理。", ((FACT_EMPLOYEE, "manager_name"),), category="CAT_HR_LEADERSHIP"),
    Dimension("DIM_region", "区域", "组织所在地理区域。", ((FACT_EMPLOYEE, "region_name"), (FACT_DEPARTMENT, "region_name"), (FACT_MOBILITY, "region_name")),
              hierarchy="HIER_HR_GEOGRAPHY", level_code="region", level_sequence=1),
    Dimension("DIM_country", "国家", "组织所在国家。", ((FACT_EMPLOYEE, "country_name"), (FACT_DEPARTMENT, "country_name"), (FACT_MOBILITY, "country_name")),
              hierarchy="HIER_HR_GEOGRAPHY", level_code="country", level_sequence=2),
    Dimension("DIM_city", "城市", "组织所在城市。", ((FACT_EMPLOYEE, "city"), (FACT_DEPARTMENT, "city"), (FACT_MOBILITY, "city")),
              hierarchy="HIER_HR_GEOGRAPHY", level_code="city", level_sequence=3),
    Dimension("DIM_tenure_band", "司龄段", "按当前司龄划分的员工区间。", ((FACT_EMPLOYEE, "tenure_band"),)),
    Dimension("DIM_org_level_band", "组织层级", "按汇报链深度划分的组织层级。", ((FACT_EMPLOYEE, "org_level_band"),), category="CAT_HR_LEADERSHIP"),
    Dimension("DIM_salary_band_status", "薪酬带状态", "员工薪酬相对岗位薪酬带的状态。", ((FACT_EMPLOYEE, "salary_band_status"),), category="CAT_HR_COST"),
    Dimension("DIM_compa_ratio_band", "薪酬中位比分段", "员工 compa-ratio 的区间分组。", ((FACT_EMPLOYEE, "compa_ratio_band"),), category="CAT_HR_COST"),
    Dimension("DIM_manager_status", "管理者身份", "按是否拥有直接下属区分管理者与非管理者。", ((FACT_EMPLOYEE, "manager_status"),), category="CAT_HR_LEADERSHIP"),
    Dimension("DIM_commission_eligibility", "佣金适用状态", "按 commission_pct 是否配置区分。", ((FACT_EMPLOYEE, "commission_eligibility"),), category="CAT_HR_COST"),
    Dimension("DIM_mobility_history_status", "流动记录状态", "按是否存在历史任职记录区分。", ((FACT_EMPLOYEE, "mobility_history_status"),), category="CAT_HR_MOBILITY"),
    Dimension("DIM_department_size_band", "部门规模段", "按当前部门人数划分的规模区间。", ((FACT_DEPARTMENT, "department_size_band"),), category="CAT_HR_LEADERSHIP"),
    Dimension("DIM_department_staffing_status", "部门启用状态", "按部门是否配置员工区分。", ((FACT_DEPARTMENT, "staffing_status"),), category="CAT_HR_LEADERSHIP"),
    Dimension("DIM_department_manager_status", "部门负责人配置状态", "按部门是否配置负责人区分。", ((FACT_DEPARTMENT, "manager_assignment_status"),), category="CAT_HR_LEADERSHIP"),
    Dimension("DIM_mobility_type", "流动类型", "岗位变动、部门调动或两者同时发生。", ((FACT_MOBILITY, "mobility_type"),), category="CAT_HR_MOBILITY"),
    Dimension("DIM_from_job", "流动前岗位", "历史任职事件的原岗位。", ((FACT_MOBILITY, "from_job_title"),), category="CAT_HR_MOBILITY"),
    Dimension("DIM_to_job", "流动后岗位", "历史任职事件映射到的下一岗位。", ((FACT_MOBILITY, "to_job_title"),), category="CAT_HR_MOBILITY"),
    Dimension("DIM_from_department", "流动前部门", "历史任职事件的原部门。", ((FACT_MOBILITY, "from_department_name"),), category="CAT_HR_MOBILITY"),
    Dimension("DIM_to_department", "流动后部门", "历史任职事件映射到的下一部门。", ((FACT_MOBILITY, "to_department_name"),), category="CAT_HR_MOBILITY"),
)


TABLE_CN_NAMES = {
    FACT_EMPLOYEE: "HR员工分析宽表",
    FACT_DEPARTMENT: "HR部门分析宽表",
    FACT_MOBILITY: "HR职业流动分析宽表",
}

CATEGORY_STANDARD_AREAS = {
    "CAT_HR_WORKFORCE": "ISO 30414:2025 workforce composition",
    "CAT_HR_COST": "ISO 30414:2025 costs",
    "CAT_HR_LEADERSHIP": "ISO 30414:2025 leadership, culture and engagement",
    "CAT_HR_MOBILITY": "ISO 30414:2025 mobility and succession planning",
}


def _safe(value: str) -> str:
    return "".join(ch.lower() if ch.isalnum() else "_" for ch in value).strip("_")


def _uri_for_code(code: str):
    return INST[code.lower()]


def _table_uri(table: str):
    return INST[f"tbl_hrrdb__{_safe(table)}"]


def _measure_app_uri(code: str):
    return INST[f"ma_{code.removeprefix('MEAS_').lower()}_origin"]


def _dimension_app_uri(code: str, table: str):
    return INST[f"da_{code.removeprefix('DIM_').lower()}__{_safe(table)}"]


def _literal(value: Any):
    if isinstance(value, bool):
        return Literal(value, datatype=XSD.boolean)
    if isinstance(value, int):
        return Literal(value, datatype=XSD.integer)
    return Literal(value)


def _add(graph: Graph, subject, **predicates: Any) -> None:
    for predicate, value in predicates.items():
        if value is None or value == "":
            continue
        values = value if isinstance(value, (list, tuple, set)) else (value,)
        for item in values:
            graph.add((subject, IND[predicate], item if hasattr(item, "n3") else _literal(item)))


def _archive(path: Path) -> None:
    if not path.exists():
        return
    stamp = time.strftime("%Y%m%d-%H%M%S")
    candidate = path.with_name(f"{path.stem}-archive-{stamp}{path.suffix}")
    seq = 1
    while candidate.exists():
        candidate = path.with_name(f"{path.stem}-archive-{stamp}-{seq:02d}{path.suffix}")
        seq += 1
    shutil.copy2(path, candidate)


def load_datasource(config_path: Path) -> tuple[dict[str, Any], DataSourceConfig]:
    payload = yaml.safe_load(config_path.read_text(encoding="utf-8"))
    raw = payload["datasources"][0]
    if str(raw.get("database", "")).lower() != "hrrdb":
        raise ValueError(f"Expected HRRDB datasource, found {raw.get('database')!r}")
    ds = DataSourceConfig(
        name=raw["name"], db_type=raw["type"], host=raw.get("host", "127.0.0.1"),
        port=int(raw.get("port", 3306)), database=raw["database"],
        username=raw.get("username", ""), password=raw.get("password", ""),
        sample_limit=int(raw.get("sample_limit", 1000)),
        exclude_tables=list(raw.get("exclude_tables", [])),
    )
    return payload, ds


def build_source_graph(config_path: Path, ds: DataSourceConfig) -> tuple[Graph, MySQLConnector]:
    payload = yaml.safe_load(config_path.read_text(encoding="utf-8"))
    settings = payload.get("settings", {})
    connector = MySQLConnector(ds)
    if not connector.test_connection():
        raise RuntimeError("Cannot connect to HRRDB")
    schema = SchemaParser(connector).parse(schema_name=ds.database)
    schema = DataSampler(connector, limit=ds.sample_limit).sample_schema(schema)
    entities = EntityExtractor(
        synonyms_path=settings.get("synonyms_path", "synonyms.yaml"),
        translate=False,
    ).extract(schema)
    classify(entities)
    relations = ExplicitRelationExtractor().extract(entities)
    builder = RDFBuilder(include_owl_schema=True)
    builder.build(entities, relations)

    OUTPUT_DIR.mkdir(parents=True, exist_ok=True)
    _archive(ACTIVE_SOURCE_OUTPUT)
    builder.save(str(SOURCE_OUTPUT), fmt="turtle")
    builder.save(str(ACTIVE_SOURCE_OUTPUT), fmt="turtle")
    print(
        f"Source KG: {len(entities.tables)} tables/views, {len(entities.columns)} columns, "
        f"{len(builder.graph)} triples -> {SOURCE_OUTPUT}"
    )
    return builder.graph, connector


def _physical_catalog(connector: MySQLConnector) -> tuple[dict[str, list[dict[str, Any]]], dict[str, int]]:
    inspector = connector.get_inspector()
    available = set(inspector.get_view_names(schema=connector.config.database))
    missing = sorted(set(FACT_TABLES) - available)
    if missing:
        raise RuntimeError(
            "Missing HR analytics views: " + ", ".join(missing)
            + ". Apply hr_analytics_views.sql first."
        )
    columns = {
        table: inspector.get_columns(table, schema=connector.config.database)
        for table in FACT_TABLES
    }
    row_counts: dict[str, int] = {}
    with connector.connect().connect() as connection:
        for table in FACT_TABLES:
            row_counts[table] = int(
                connection.execute(text(f"SELECT COUNT(*) FROM `{table}`")).scalar_one()
            )
    return columns, row_counts


def _validate_definitions(columns: dict[str, list[dict[str, Any]]]) -> None:
    physical = {table: {str(col["name"]) for col in cols} for table, cols in columns.items()}
    metric_codes = {metric.code for metric in METRICS}
    errors: list[str] = []
    for metric in METRICS:
        if metric.table not in physical:
            errors.append(f"{metric.code}: unknown table {metric.table}")
        elif not metric.derived and metric.column not in physical[metric.table]:
            errors.append(f"{metric.code}: missing column {metric.table}.{metric.column}")
        if metric.derived:
            for dependency in (metric.numerator, metric.denominator):
                if dependency not in metric_codes:
                    errors.append(f"{metric.code}: unknown dependency {dependency}")
    for dimension in DIMENSIONS:
        for table, column in dimension.apps:
            if table not in physical or column not in physical[table]:
                errors.append(f"{dimension.code}: missing column {table}.{column}")
    if errors:
        raise RuntimeError("HR metric definition validation failed:\n- " + "\n- ".join(errors))


def build_business_graph(ds: DataSourceConfig, connector: MySQLConnector) -> Graph:
    columns, row_counts = _physical_catalog(connector)
    _validate_definitions(columns)

    graph = Graph()
    graph.parse(data=_ONTOLOGY_PREAMBLE, format="turtle")
    graph.bind("ind", IND)
    graph.bind("inst", INST)

    extension_properties = {
        "metricRole": "指标角色",
        "targetGuidance": "目标与阈值建议",
        "dataQualityNote": "数据质量说明",
        "standardReference": "标准参考",
        "measurementCadence": "建议复盘频率",
    }
    for name, label in extension_properties.items():
        graph.add((IND[name], RDF.type, OWL.DatatypeProperty))
        graph.add((IND[name], RDFS.label, Literal(label, lang="zh")))
        graph.add((IND[name], RDFS.range, XSD.string))

    connection_uri = INST.conn_mysql_localhost_hrrdb
    graph.add((connection_uri, RDF.type, IND.DataConnection))
    _add(
        graph,
        connection_uri,
        dbType="mysql",
        host=ds.host,
        port=ds.port,
        dbUser=ds.username,
        dbPassword=ds.password,
        dbName=ds.database,
    )

    for table in FACT_TABLES:
        table_uri = _table_uri(table)
        column_uris = [INST[f"col_hrrdb__{_safe(table)}__{_safe(str(col['name']))}"] for col in columns[table]]
        histogram_uri = INST[f"hist_tbl_{_safe(table)}"]
        graph.add((table_uri, RDF.type, IND.DwTable))
        _add(
            graph,
            table_uri,
            schemaName=ds.database,
            tableName=table,
            cnName=TABLE_CN_NAMES[table],
            description="HRRDB 原始主数据经只读视图展开形成的指标事实视图。",
            sourceTypeCode=0,
            hasConnection=connection_uri,
            hasTableHistogram=histogram_uri,
            hasColumn=column_uris,
        )
        graph.add((histogram_uri, RDF.type, IND.TableHistogram))
        _add(
            graph,
            histogram_uri,
            tableRowNum=row_counts[table],
            maxScanNum=max(row_counts[table], 1000),
        )
        for position, column in enumerate(columns[table], start=1):
            name = str(column["name"])
            column_uri = INST[f"col_hrrdb__{_safe(table)}__{_safe(name)}"]
            graph.add((column_uri, RDF.type, IND.DwColumn))
            _add(
                graph,
                column_uri,
                columnName=name,
                columnType=str(column["type"]),
                cnName=name,
                columnComment=f"{TABLE_CN_NAMES[table]}字段：{name}",
                isPrimaryKey=False,
                isNullable=bool(column.get("nullable", True)),
                ordinalPosition=position,
            )

    for category_id, code, cn_name, en_name, parent, description in CATEGORIES:
        category_uri = _uri_for_code(code)
        graph.add((category_uri, RDF.type, IND.Category))
        _add(
            graph,
            category_uri,
            id=category_id,
            code=code,
            name=cn_name,
            cnName=cn_name,
            enName=en_name,
            description=description,
            categoryParent=_uri_for_code(parent) if parent else None,
            standardReference="ISO 30414:2025",
        )

    metric_by_code = {metric.code: metric for metric in METRICS}
    for metric in METRICS:
        measure_uri = _uri_for_code(metric.code)
        app_uri = _measure_app_uri(metric.code)
        graph.add((measure_uri, RDF.type, IND.Measure))
        _add(
            graph,
            measure_uri,
            code=metric.code,
            cnName=metric.cn_name,
            enName=metric.code,
            measTypeCode=1 if metric.derived else 0,
            unit=metric.unit,
            caliber=metric.caliber,
            definition=metric.definition,
            northStar=metric.north_star,
            online=1,
            belongsToCategory=_uri_for_code(metric.category),
            hasMeasureApp=app_uri,
            metricRole=metric.role,
            targetGuidance=metric.target,
            dataQualityNote=metric.quality_note,
            standardReference=CATEGORY_STANDARD_AREAS.get(metric.category, "ISO 30414:2025"),
            measurementCadence="月度；组织调整或薪酬周期后复核",
        )
        graph.add((app_uri, RDF.type, IND.MeasureApp))
        if metric.derived:
            expression = [
                {"operatingType": "operand", "operand": {"measCode": metric.numerator}},
                {"operatingType": "operator", "operator": "/"},
                {"operatingType": "operand", "operand": {"measCode": metric.denominator}},
            ]
            dependencies = [_measure_app_uri(metric.numerator), _measure_app_uri(metric.denominator)]
            _add(
                graph,
                app_uri,
                applyTypeCode=1,
                expression=json.dumps(expression, ensure_ascii=False, separators=(",", ":")),
                hasColumnDT=False,
                available=1,
                appliesToTable=_table_uri(metric.table),
                dependsOnMeasApp=dependencies,
            )
        else:
            expression = [{"operatingType": "operator", "operator": metric.operator}]
            _add(
                graph,
                app_uri,
                applyTypeCode=0,
                factColumn=metric.column,
                expression=json.dumps(expression, ensure_ascii=False, separators=(",", ":")),
                hasColumnDT=False,
                available=1,
                appliesToTable=_table_uri(metric.table),
            )

        natural_column = "event_date" if metric.table == FACT_MOBILITY else "snapshot_date"
        natural_hierarchy = "HIER_HR_EVENT_DATE" if metric.table == FACT_MOBILITY else "HIER_HR_SNAPSHOT_DATE"
        ndm_uri = INST[f"ndm_{metric.code.removeprefix('MEAS_').lower()}_date"]
        _add(graph, app_uri, hasNaturalDimMapping=ndm_uri)
        graph.add((ndm_uri, RDF.type, IND.NaturalDimMapping))
        _add(graph, ndm_uri, naturalHierarchyCode=natural_hierarchy, physicalColumn=natural_column)

        if metric.derived:
            for dependency_code in (metric.numerator, metric.denominator):
                if metric_by_code[dependency_code].table != metric.table:
                    raise RuntimeError(f"Derived metric {metric.code} crosses fact tables")

    for dimension in DIMENSIONS:
        dimension_uri = _uri_for_code(dimension.code)
        app_uris = [_dimension_app_uri(dimension.code, table) for table, _ in dimension.apps]
        graph.add((dimension_uri, RDF.type, IND.Dimension))
        _add(
            graph,
            dimension_uri,
            code=dimension.code,
            cnName=dimension.cn_name,
            enName=dimension.code,
            dimTypeCode=0,
            viewTypeCode=dimension.view_type,
            isHyper=False,
            hierarchyCode=dimension.hierarchy,
            levelCode=dimension.level_code,
            levelSequence=dimension.level_sequence or None,
            definition=dimension.definition,
            belongsToCategory=_uri_for_code(dimension.category),
            hasDimApp=app_uris,
        )
        for table, column in dimension.apps:
            app_uri = _dimension_app_uri(dimension.code, table)
            graph.add((app_uri, RDF.type, IND.DimensionApp))
            is_root = bool(dimension.hierarchy and dimension.level_sequence == 1)
            _add(
                graph,
                app_uri,
                dimFactColumn=column,
                masterPrimaryKey=column,
                isMasterApp=is_root or not dimension.hierarchy,
                isRootJoin=is_root,
                available=1,
                dimFactTable=_table_uri(table),
                hierarchyCode=dimension.hierarchy,
                levelCode=dimension.level_code,
            )

    BKG_DIR.mkdir(parents=True, exist_ok=True)
    _archive(BUSINESS_OUTPUT)
    graph.serialize(destination=str(BUSINESS_OUTPUT), format="turtle")

    roundtrip = Graph()
    roundtrip.parse(str(BUSINESS_OUTPUT), format="turtle")
    measure_count = len(set(roundtrip.subjects(RDF.type, IND.Measure)))
    dimension_count = len(set(roundtrip.subjects(RDF.type, IND.Dimension)))
    table_count = len(set(roundtrip.subjects(RDF.type, IND.DwTable)))
    if (measure_count, dimension_count, table_count) != (len(METRICS), len(DIMENSIONS), len(FACT_TABLES)):
        raise RuntimeError("Business KG round-trip counts do not match definitions")

    try:
        from kg_builder.business_kg.reasoner import BusinessKGReasoner

        inferred = BusinessKGReasoner().infer_from_turtle(BUSINESS_OUTPUT.read_text(encoding="utf-8"))
        inferred.serialize(destination=str(INFERRED_OUTPUT), format="turtle")
    except Exception as exc:
        print(f"Warning: inferred graph was not generated: {exc}")

    from kg_builder.feedback.graph_version import graph_identity

    manifest = {
        "businessKg": BUSINESS_OUTPUT.name,
        "sourceKg": SOURCE_OUTPUT.name,
        "businessKgSha256": graph_identity(BUSINESS_OUTPUT).get("sha256") or "",
        "sourceKgSha256": graph_identity(SOURCE_OUTPUT).get("sha256") or "",
        "boundAt": time.strftime("%Y-%m-%d %H:%M:%S"),
    }
    SOURCE_MANIFEST.write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(
        f"Business KG: {measure_count} measures, {dimension_count} dimensions, "
        f"{table_count} fact views, {len(roundtrip)} triples -> {BUSINESS_OUTPUT}"
    )
    return roundtrip


def main() -> None:
    parser = argparse.ArgumentParser(description="Build HRRDB source and business knowledge graphs")
    parser.add_argument("--config", default="config.local.yaml", help="Local AD datasource config")
    args = parser.parse_args()
    config_path = (BASE_DIR / args.config).resolve() if not Path(args.config).is_absolute() else Path(args.config)
    _, ds = load_datasource(config_path)
    _, connector = build_source_graph(config_path, ds)
    try:
        build_business_graph(ds, connector)
    finally:
        connector.close()


if __name__ == "__main__":
    main()
