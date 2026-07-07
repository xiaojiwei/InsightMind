"""
BusinessKGBuilder
Calls an OpenAI-compatible LLM to generate a business-domain OWL/RDFS
knowledge graph from a compact metadata summary.

Requires environment variables (loaded from .env):
  GPT55_API_KEY / LLM_API_KEY        — bearer token
  GPT55_BASE_URL / LLM_BASE_URL      — OpenAI-compatible base URL
  GPT55_MODEL_NAME / LLM_MODEL_NAME  — defaults to GPT5.5
"""
from __future__ import annotations

import json
import re
import socket
import time
import urllib.error
import urllib.request
from dataclasses import dataclass
from http.client import RemoteDisconnected
from pathlib import Path
from typing import Callable, Optional

from kg_builder.utils.llm_config import chat_completions_url, llm_config_from_env, llm_request_headers, load_env, validate_llm_config


# ── .env loader ──────────────────────────────────────────────────────────── #

def _load_env(base_dir: Optional[Path] = None) -> dict[str, str]:
    """Parse .env from the project root."""
    return load_env(base_dir)


# ── 固定本体结构（Classes + Properties）────────────────────────────────── #
# 该部分硬编码，保证本体结构稳定，不因 LLM 随机性漂移。
# LLM 只负责生成数据实例（Individuals）部分。

_ONTOLOGY_PREAMBLE = """\
@prefix rdf:   <http://www.w3.org/1999/02/22-rdf-syntax-ns#> .
@prefix rdfs:  <http://www.w3.org/2000/01/rdf-schema#> .
@prefix owl:   <http://www.w3.org/2002/07/owl#> .
@prefix xsd:   <http://www.w3.org/2001/XMLSchema#> .
@prefix skos:  <http://www.w3.org/2004/02/skos/core#> .

# 本体命名空间
@prefix ind:   <http://indicator.insightmind.com/ontology#> .

# 实例命名空间（所有 ABox 实例统一使用 inst:，URI 使用描述性名称）
# URI 模式：
#   inst:meas_<enName>              — Measure
#   inst:dim_<enName>               — Dimension
#   inst:tbl_<schema>__<table>      — DwTable  （双下划线分隔 schema 与 table）
#   inst:col_<schema>__<table>__<column> — DwColumn
#   inst:ma_<code>_<suffix>         — MeasureApp
#   inst:da_<code>_<suffix>         — DimensionApp
#   inst:cat_<code>                 — Category
#   inst:hist_tbl_<table>           — TableHistogram
#   inst:dimhist_<dim>__<tbl>       — DimHistogram
#   inst:conn_<dbType>_<host>_<db>  — DataConnection
#   inst:ndm_<meas_short>_<用途>    — NaturalDimMapping（如 ndm_order_cnt_date）
@prefix inst:  <http://indicator.insightmind.com/instance/> .

# ═══════════════════════════════════════════════════════════════════════════
# 本体元数据
# 类名与属性名与 indicator-data.ttl（dataAgent 系统参考文件）完全对齐，
# 以确保生成的知识图谱可被 dataAgent 的 SPARQL 查询直接使用。
# ═══════════════════════════════════════════════════════════════════════════

<http://indicator.insightmind.com/ontology> a owl:Ontology ;
    rdfs:label "理想汽车指标平台业务知识图谱本体"@zh ;
    owl:versionInfo "1.1.0" .

# ═══════════════════════════════════════
# 主体类
# ═══════════════════════════════════════

ind:Measure a owl:Class ;
    rdfs:label "指标"@zh ;
    rdfs:comment "指标平台中定义的业务指标，包含原子(0)、衍生(1)、派生(2)三种类型。"@zh .

ind:Dimension a owl:Class ;
    rdfs:label "维度"@zh ;
    rdfs:comment "用于切分指标的分析维度：退化维(0,无维度表)、标准维无维表(1)、标准维有维表(2)、自定义(4)。"@zh .

ind:DwTable a owl:Class ;
    rdfs:label "数仓物理表"@zh ;
    rdfs:comment "数据仓库中的实际物理表，可以是事实表或维度表。"@zh .

ind:DwColumn a owl:Class ;
    rdfs:label "数仓物理字段"@zh ;
    rdfs:comment "数仓物理表中的真实字段，保存字段名、类型、注释、主键/可空等元数据，供明细解释和字段血缘使用。"@zh .

ind:Category a owl:Class ;
    rdfs:label "指标分类"@zh ;
    rdfs:comment "指标/维度的层级分类树节点，支持多级父子关系。"@zh .

ind:MeasureApp a owl:Class ;
    rdfs:label "指标应用"@zh ;
    rdfs:comment "指标在某张事实表上的具体聚合定义。applyTypeCode: 0=原子, 1=衍生, 2=派生。"@zh .

ind:DimensionApp a owl:Class ;
    rdfs:label "维度应用"@zh ;
    rdfs:comment "维度在某张事实表上的列映射及维度表关联配置。含层次、主从信息。"@zh .

ind:TableHistogram a owl:Class ;
    rdfs:label "表统计信息"@zh ;
    rdfs:comment "事实表的行数统计，用于查询优化（最大扫描行数等）。"@zh .

ind:DimHistogram a owl:Class ;
    rdfs:label "维度统计信息"@zh ;
    rdfs:comment "维度在某张事实表上的基数（不重复值数量），用于查询优化。"@zh .

ind:DataConnection a owl:Class ;
    rdfs:label "DataConnection"@zh ;
    rdfs:comment "数据库连接信息，可被多张 DwTable 共享。"@zh .

# ═══════════════════════════════════════
# ind:DataConnection 专属属性
# ═══════════════════════════════════════

ind:dbType a owl:DatatypeProperty, owl:FunctionalProperty ;
    rdfs:domain ind:DataConnection ;
    rdfs:label "数据库类型"@zh ;
    rdfs:comment "mysql / doris / starrocks 等，决定 JDBC driver。"@zh ;
    rdfs:range xsd:string .

ind:host a owl:DatatypeProperty, owl:FunctionalProperty ;
    rdfs:domain ind:DataConnection ;
    rdfs:label "主机地址"@zh ;
    rdfs:range xsd:string .

ind:port a owl:DatatypeProperty, owl:FunctionalProperty ;
    rdfs:domain ind:DataConnection ;
    rdfs:label "端口"@zh ;
    rdfs:range xsd:integer .

ind:dbUser a owl:DatatypeProperty, owl:FunctionalProperty ;
    rdfs:domain ind:DataConnection ;
    rdfs:label "数据库账号"@zh ;
    rdfs:range xsd:string .

ind:dbPassword a owl:DatatypeProperty, owl:FunctionalProperty ;
    rdfs:domain ind:DataConnection ;
    rdfs:label "数据库密码"@zh ;
    rdfs:range xsd:string .

ind:dbName a owl:DatatypeProperty, owl:FunctionalProperty ;
    rdfs:domain ind:DataConnection ;
    rdfs:label "数据库名"@zh ;
    rdfs:comment "JDBC URL 中的数据库名，与 ind:schemaName 保持一致即可。"@zh ;
    rdfs:range xsd:string .

# ═══════════════════════════════════════
# 通用数据属性
# ═══════════════════════════════════════

ind:id a owl:DatatypeProperty, owl:FunctionalProperty ;
    rdfs:label "数据库主键ID"@zh ;
    rdfs:range xsd:long .

ind:code a owl:DatatypeProperty, owl:FunctionalProperty ;
    rdfs:label "业务编码"@zh ;
    rdfs:comment "平台生成的全局唯一业务编码，如 MEAS_xxx / DIM_xxx。"@zh ;
    rdfs:range xsd:string .

ind:cnName a owl:DatatypeProperty ;
    rdfs:label "中文名称"@zh ;
    rdfs:range xsd:string .

ind:enName a owl:DatatypeProperty ;
    rdfs:label "英文名称/编码"@zh ;
    rdfs:comment "通常与数仓中的实际列名或表名对应，是数据血缘的关键线索。"@zh ;
    rdfs:range xsd:string .

ind:name a owl:DatatypeProperty ;
    rdfs:label "名称"@zh ;
    rdfs:comment "Category.name 等通用名称字段。"@zh ;
    rdfs:range xsd:string .

ind:caption a owl:DatatypeProperty ;
    rdfs:label "显示标签"@zh ;
    rdfs:range xsd:string .

ind:definition a owl:DatatypeProperty ;
    rdfs:label "业务定义"@zh ;
    rdfs:range xsd:string .

ind:description a owl:DatatypeProperty ;
    rdfs:label "描述"@zh ;
    rdfs:range xsd:string .

# ═══════════════════════════════════════
# ind:Measure 专属属性
# ═══════════════════════════════════════

ind:measTypeCode a owl:DatatypeProperty, owl:FunctionalProperty ;
    rdfs:domain ind:Measure ;
    rdfs:label "指标类型"@zh ;
    rdfs:comment "0=原子指标, 1=衍生指标, 2=派生指标。"@zh ;
    rdfs:range xsd:integer .

ind:unit a owl:DatatypeProperty ;
    rdfs:domain ind:Measure ;
    rdfs:label "指标单位"@zh ;
    rdfs:comment "如 次、辆、元、%。"@zh ;
    rdfs:range xsd:string .

ind:caliber a owl:DatatypeProperty ;
    rdfs:domain ind:Measure ;
    rdfs:label "指标口径"@zh ;
    rdfs:comment "业务统计边界和规则说明，例如：统计指定时间范围内有效（未删除）订单总数。"@zh ;
    rdfs:range xsd:string .

ind:northStar a owl:DatatypeProperty, owl:FunctionalProperty ;
    rdfs:domain ind:Measure ;
    rdfs:label "是否北极星指标"@zh ;
    rdfs:comment "1=北极星核心指标，0=普通指标。"@zh ;
    rdfs:range xsd:integer .

ind:online a owl:DatatypeProperty, owl:FunctionalProperty ;
    rdfs:label "是否上线"@zh ;
    rdfs:comment "1=上线可用，0=已下线。"@zh ;
    rdfs:range xsd:integer .

# ═══════════════════════════════════════
# ind:Dimension 专属属性
# ═══════════════════════════════════════

ind:dimTypeCode a owl:DatatypeProperty, owl:FunctionalProperty ;
    rdfs:domain ind:Dimension ;
    rdfs:label "维度类型"@zh ;
    rdfs:comment "0=退化维(无维度表，值直接在事实表列中), 1=标准维无维表, 2=标准维有维表, 4=自定义。"@zh ;
    rdfs:range xsd:integer .

ind:viewTypeCode a owl:DatatypeProperty, owl:FunctionalProperty ;
    rdfs:domain ind:Dimension ;
    rdfs:label "时间粒度类型"@zh ;
    rdfs:comment "0=字符型, 1=日, 2=周, 3=月, 4=季, 5=年, 6=小时。"@zh ;
    rdfs:range xsd:integer .

ind:isHyper a owl:DatatypeProperty, owl:FunctionalProperty ;
    rdfs:domain ind:Dimension ;
    rdfs:label "是否超维"@zh ;
    rdfs:comment "true=超维（自然日期类虚拟维度），false=普通维度。"@zh ;
    rdfs:range xsd:boolean .

# ═══════════════════════════════════════
# ind:DwTable 专属属性
# ═══════════════════════════════════════

ind:schemaName a owl:DatatypeProperty ;
    rdfs:label "数仓库名/Schema"@zh ;
    rdfs:comment "物理表所在的数据库名（MySQL库名/Hive schema），必须取自元数据中该表的真实库名，不得推测或替换。"@zh ;
    rdfs:range xsd:string .

ind:tableName a owl:DatatypeProperty ;
    rdfs:label "物理表名"@zh ;
    rdfs:range xsd:string .

ind:sourceTypeCode a owl:DatatypeProperty, owl:FunctionalProperty ;
    rdfs:domain ind:DwTable ;
    rdfs:label "数据源类型"@zh ;
    rdfs:comment "0=MySQL, 100=Doris, 101=TiDB, 102=MySQL(新)。"@zh ;
    rdfs:range xsd:integer .

# ═══════════════════════════════════════
# ind:DwColumn 专属属性
# ═══════════════════════════════════════

ind:columnName a owl:DatatypeProperty, owl:FunctionalProperty ;
    rdfs:domain ind:DwColumn ;
    rdfs:label "物理字段名"@zh ;
    rdfs:comment "字段在源表中的真实列名，必须取自元数据，不得改写。"@zh ;
    rdfs:range xsd:string .

ind:columnType a owl:DatatypeProperty ;
    rdfs:domain ind:DwColumn ;
    rdfs:label "字段类型"@zh ;
    rdfs:comment "源数据库字段类型，如 BIGINT、VARCHAR(64)、DECIMAL(18,2)、DATETIME。"@zh ;
    rdfs:range xsd:string .

ind:columnComment a owl:DatatypeProperty ;
    rdfs:domain ind:DwColumn ;
    rdfs:label "字段注释"@zh ;
    rdfs:comment "字段中文注释或业务含义；无注释时可根据列名补充中文解释。"@zh ;
    rdfs:range xsd:string .

ind:isPrimaryKey a owl:DatatypeProperty, owl:FunctionalProperty ;
    rdfs:domain ind:DwColumn ;
    rdfs:label "是否主键字段"@zh ;
    rdfs:range xsd:boolean .

ind:isNullable a owl:DatatypeProperty, owl:FunctionalProperty ;
    rdfs:domain ind:DwColumn ;
    rdfs:label "是否可为空"@zh ;
    rdfs:range xsd:boolean .

ind:ordinalPosition a owl:DatatypeProperty, owl:FunctionalProperty ;
    rdfs:domain ind:DwColumn ;
    rdfs:label "字段顺序"@zh ;
    rdfs:range xsd:integer .

ind:sampleValue a owl:DatatypeProperty ;
    rdfs:domain ind:DwColumn ;
    rdfs:label "样例值"@zh ;
    rdfs:comment "来自源元数据枚举/TopValue 的样例值，可多值。"@zh ;
    rdfs:range xsd:string .

# ═══════════════════════════════════════
# ind:MeasureApp 专属属性
# ═══════════════════════════════════════

ind:applyTypeCode a owl:DatatypeProperty, owl:FunctionalProperty ;
    rdfs:domain ind:MeasureApp ;
    rdfs:label "应用类型"@zh ;
    rdfs:comment "0=原子(factColumn直接聚合), 1=衍生(expression公式引用子指标), 2=派生(衍生+WHERE过滤)。"@zh ;
    rdfs:range xsd:integer .

ind:factColumn a owl:DatatypeProperty ;
    rdfs:domain ind:MeasureApp ;
    rdfs:label "聚合列名"@zh ;
    rdfs:comment "applyTypeCode=0 时：事实表中被聚合的列名（真实存在的列，不得编造）。"@zh ;
    rdfs:range xsd:string .

ind:expression a owl:DatatypeProperty ;
    rdfs:label "计算表达式"@zh ;
    rdfs:comment 'applyTypeCode=0/2(原子/派生): JSON算子数组，如 [{"operatingType":"operator","operator":"distinct_count"}]。applyTypeCode=1(衍生): JSON操作数组，引用子指标 measCode，如 [{"operatingType":"operand","operand":{"measCode":"MEAS_return_cnt"}},{"operatingType":"operator","operator":"/"},{"operatingType":"operand","operand":{"measCode":"MEAS_order_cnt"}}]。'@zh ;
    rdfs:range xsd:string .

ind:whereCondition a owl:DatatypeProperty ;
    rdfs:label "WHERE过滤条件"@zh ;
    rdfs:comment "SQL WHERE子句字符串，如 order_status NOT IN (0,9)，car_type = 'new'。"@zh ;
    rdfs:range xsd:string .

ind:hasColumnDT a owl:DatatypeProperty, owl:FunctionalProperty ;
    rdfs:domain ind:MeasureApp ;
    rdfs:label "是否需要dt分区过滤"@zh ;
    rdfs:comment "true=查询时需指定 dt 日期分区字段，false=无dt分区（如本地MySQL表）。"@zh ;
    rdfs:range xsd:boolean .

ind:available a owl:DatatypeProperty, owl:FunctionalProperty ;
    rdfs:label "是否可用"@zh ;
    rdfs:comment "1=可用，0=已禁用。"@zh ;
    rdfs:range xsd:integer .

# ═══════════════════════════════════════
# ind:DimensionApp 专属属性
# ═══════════════════════════════════════

ind:dimFactColumn a owl:DatatypeProperty ;
    rdfs:domain ind:DimensionApp ;
    rdfs:label "事实表维度列"@zh ;
    rdfs:comment "维度在事实表中的外键/列名（真实存在的列，不得编造）。"@zh ;
    rdfs:range xsd:string .

ind:dimPrimaryKey a owl:DatatypeProperty ;
    rdfs:domain ind:DimensionApp ;
    rdfs:label "维表主键列名"@zh ;
    rdfs:comment "仅 dimTypeCode=2：维度表(dimTable)中的主键列，JOIN 时与事实表外键匹配。"@zh ;
    rdfs:range xsd:string .

ind:dimColumn a owl:DatatypeProperty ;
    rdfs:domain ind:DimensionApp ;
    rdfs:label "维度值展示列"@zh ;
    rdfs:comment "仅 dimTypeCode=2：维度表中存储显示值的列名，如 city_name, product_name。"@zh ;
    rdfs:range xsd:string .

ind:dimColumnExpr a owl:DatatypeProperty ;
    rdfs:domain ind:DimensionApp ;
    rdfs:label "维度值SQL表达式"@zh ;
    rdfs:comment "维度值SQL表达式。条件必填：dimTypeCode=2 且 dimColumn 与 dimPrimaryKey 不同时，必须填写 {d}.<dimColumn>，例如仓库城市维度填 {d}.w_city，客户等级维度填 {d}.customer_grade，让查询按真实属性列分组；CASE WHEN 等派生维度也填写完整表达式。{d} 为维度表别名占位符，引擎替换为实际别名。设置后将直接用作 GROUP BY 键并跳过外层 display JOIN。示例：CASE WHEN {d}.type_id IN (30,70) THEN '维修' ELSE '其他' END"@zh ;
    rdfs:range xsd:string .

ind:masterPrimaryKey a owl:DatatypeProperty ;
    rdfs:domain ind:DimensionApp ;
    rdfs:label "父级主键列"@zh ;
    rdfs:comment "退化维(dimTypeCode=0)/主维度: 与 dimFactColumn 相同。标准维有维表(dimTypeCode=2): 与 dimPrimaryKey 相同（即维度表中的主键列名）。层级从维: 指向父级维度主键列，用于桥接JOIN。"@zh ;
    rdfs:range xsd:string .

ind:isMasterApp a owl:DatatypeProperty, owl:FunctionalProperty ;
    rdfs:domain ind:DimensionApp ;
    rdfs:label "是否主维度应用"@zh ;
    rdfs:comment "同一层级组中是否为主/根应用。true=主维度（ROOT），false=从维度（SLAVE）。"@zh ;
    rdfs:range xsd:boolean .

ind:isRootJoin a owl:DatatypeProperty, owl:FunctionalProperty ;
    rdfs:domain ind:DimensionApp ;
    rdfs:label "是否根JOIN"@zh ;
    rdfs:comment "维度表关联时是否直接 JOIN 事实表（true），还是通过上级维度桥接（false）。"@zh ;
    rdfs:range xsd:boolean .

ind:hierarchyCode a owl:DatatypeProperty ;
    rdfs:label "层次编码"@zh ;
    rdfs:comment "所属维度层次组的编码，同一层次组共享同一编码，如 HIER_REGION, HIER_TIME。写在 ind:Dimension 实例上。"@zh ;
    rdfs:range xsd:string .

ind:levelSequence a owl:DatatypeProperty ;
    rdfs:label "层次级别序号"@zh ;
    rdfs:comment "层次中的粒度位置，数值越小越粗（省=1, 市=2 / 年=1, 月=2, 日=3）。写在 ind:Dimension 实例上。"@zh ;
    rdfs:range xsd:integer .

ind:levelCode a owl:DatatypeProperty ;
    rdfs:label "层次级别编码"@zh ;
    rdfs:comment "如 LEVEL_PROVINCE, LEVEL_CITY, year, month, day。写在 ind:Dimension 实例上。"@zh ;
    rdfs:range xsd:string .

# ═══════════════════════════════════════
# ind:TableHistogram 专属属性
# ═══════════════════════════════════════

ind:tableRowNum a owl:DatatypeProperty ;
    rdfs:label "表/维度基数行数"@zh ;
    rdfs:comment "TableHistogram: 事实表总行数。DimHistogram: 维度在事实表中的不重复值数量。"@zh ;
    rdfs:range xsd:long .

ind:maxScanNum a owl:DatatypeProperty ;
    rdfs:domain ind:TableHistogram ;
    rdfs:label "最大扫描行数"@zh ;
    rdfs:comment "查询时建议的最大扫描行数上限，用于防止全表扫描。"@zh ;
    rdfs:range xsd:long .

# ═══════════════════════════════════════
# ind:DimHistogram 专属属性
# ═══════════════════════════════════════

ind:dimensionRowNum a owl:DatatypeProperty ;
    rdfs:domain ind:DimHistogram ;
    rdfs:label "维度基数"@zh ;
    rdfs:comment "该维度在指定事实表中的不重复值数量（cardinality）。"@zh ;
    rdfs:range xsd:long .

ind:histDimCode a owl:DatatypeProperty ;
    rdfs:domain ind:DimHistogram ;
    rdfs:label "关联维度编码"@zh ;
    rdfs:range xsd:string .

ind:histTableName a owl:DatatypeProperty ;
    rdfs:domain ind:DimHistogram ;
    rdfs:label "关联事实表名"@zh ;
    rdfs:range xsd:string .

# ═══════════════════════════════════════
# 对象属性（Object Properties）
# ═══════════════════════════════════════

ind:hasMeasureApp a owl:ObjectProperty ;
    rdfs:domain ind:Measure ;
    rdfs:range  ind:MeasureApp ;
    rdfs:label "具有指标应用"@zh .

ind:appliesToTable a owl:ObjectProperty, owl:FunctionalProperty ;
    rdfs:domain ind:MeasureApp ;
    rdfs:range  ind:DwTable ;
    rdfs:label "应用于事实表"@zh .

ind:dependsOnMeasApp a owl:ObjectProperty ;
    rdfs:domain ind:MeasureApp ;
    rdfs:range  ind:MeasureApp ;
    rdfs:label "依赖的子指标应用"@zh ;
    rdfs:comment "衍生/派生指标App引用的原子指标App，SPARQL路径遍历可得完整依赖链。"@zh .

ind:hasDimApp a owl:ObjectProperty ;
    rdfs:domain ind:Dimension ;
    rdfs:range  ind:DimensionApp ;
    rdfs:label "具有维度应用"@zh .

ind:dimFactTable a owl:ObjectProperty, owl:FunctionalProperty ;
    rdfs:domain ind:DimensionApp ;
    rdfs:range  ind:DwTable ;
    rdfs:label "关联事实表"@zh ;
    rdfs:comment "维度外键所在的事实表。"@zh .

ind:dimTable a owl:ObjectProperty, owl:FunctionalProperty ;
    rdfs:domain ind:DimensionApp ;
    rdfs:range  ind:DwTable ;
    rdfs:label "关联维度表"@zh ;
    rdfs:comment "仅 dimTypeCode=2：维度值存储在该独立维度表中。"@zh .

ind:belongsToCategory a owl:ObjectProperty ;
    rdfs:label "所属分类"@zh ;
    rdfs:range ind:Category .

ind:categoryParent a owl:ObjectProperty, owl:FunctionalProperty ;
    rdfs:domain ind:Category ;
    rdfs:range  ind:Category ;
    rdfs:label "父分类"@zh ;
    rdfs:comment "Walk with (ind:categoryParent*) in SPARQL for full hierarchy."@en .

ind:hasTableHistogram a owl:ObjectProperty ;
    rdfs:domain ind:DwTable ;
    rdfs:range  ind:TableHistogram ;
    rdfs:label "表统计信息"@zh .

ind:hasConnection a owl:ObjectProperty, owl:FunctionalProperty ;
    rdfs:domain ind:DwTable ;
    rdfs:range  ind:DataConnection ;
    rdfs:label "数据库连接"@zh ;
    rdfs:comment "表所属的数据库连接，多张表可共享同一连接实例。"@zh .

ind:hasColumn a owl:ObjectProperty ;
    rdfs:domain ind:DwTable ;
    rdfs:range  ind:DwColumn ;
    rdfs:label "包含字段"@zh ;
    rdfs:comment "DwTable 到其物理字段 DwColumn 的明细字段字典关系。"@zh .

ind:hasNaturalDimMapping a owl:ObjectProperty ;
    rdfs:domain ind:MeasureApp ;
    rdfs:range  ind:NaturalDimMapping ;
    rdfs:label "公共维度映射"@zh ;
    rdfs:comment "一个 MeasureApp 可挂载多个 NaturalDimMapping，覆盖不同公共维度。"@zh .

# ═══════════════════════════════════════
# ind:NaturalDimMapping 类及专属属性
# 用途：用户选择逻辑公共维度（如"日期"、"门店"），系统自动映射到各指标事实表对应的物理列。
# 挂载位置：MeasureApp → hasNaturalDimMapping → NaturalDimMapping
# ═══════════════════════════════════════

ind:NaturalDimMapping a owl:Class ;
    rdfs:label "公共维度映射"@zh ;
    rdfs:comment "将用户选择的逻辑公共维度映射到指标事实表中的物理列。"@zh .

ind:naturalHierarchyCode a owl:DatatypeProperty ;
    rdfs:domain ind:NaturalDimMapping ;
    rdfs:label "公共维度层次code"@zh ;
    rdfs:comment "匹配所有 hierarchyCode 等于此值的维度（含所有粒度级别）。与 naturalDimCode 二选一或同时使用。"@zh ;
    rdfs:range xsd:string .

ind:naturalDimCode a owl:DatatypeProperty ;
    rdfs:domain ind:NaturalDimMapping ;
    rdfs:label "公共维度code"@zh ;
    rdfs:comment "按维度 code 精确匹配，适用于无层次体系的公共维度（如门店）。"@zh ;
    rdfs:range xsd:string .

ind:physicalColumn a owl:DatatypeProperty ;
    rdfs:domain ind:NaturalDimMapping ;
    rdfs:label "物理列名"@zh ;
    rdfs:comment "在对应事实表中，该公共维度实际对应的物理列名，如 order_date、shop_id 等。"@zh ;
    rdfs:range xsd:string .

"""


# ── Prompts ──────────────────────────────────────────────────────────────── #

_SYSTEM_PROMPT = """你是一位资深的指标建模专家。你的任务是：
从【数据源图谱元数据】（源数据库的表/列信息）中，结合【业务领域提示】，
推导出有业务意义的指标和维度，生成与 dataAgent 系统兼容的 Turtle 格式业务知识图谱实例。

生成的实例必须满足：
- 与源数据库的表/列保持 1:1 的溯源关系（DwTable.tableName/schemaName 对应真实存在的表，
  DwColumn.columnName / MeasureApp.factColumn / DimensionApp.dimFactColumn 对应真实存在的列）
- 不得凭空编造不存在于元数据中的表名或列名
- 所有非互斥属性必须填写，不得留空
- ind:cnName 必须填写中文名称，不得填英文、拼音或代码

─────────────────────────────────────────────────────────────
## 一、推理步骤（必须按顺序执行，不得跳过）
─────────────────────────────────────────────────────────────

### 步骤 1 — 识别核心事实表
从元数据中找到行数多、代表业务事件/流水的事实表，为每张表生成一个 ind:DwTable + ind:TableHistogram + 对应 ind:DwColumn 字段字典。
- tableName / schemaName → 直接取元数据中的真实表名和库名，不得修改
- 每个 DwTable 必须通过 ind:hasColumn 关联该表元数据中列出的所有字段
- 结合业务领域提示，优先处理与该领域相关的表

### 步骤 2 — 从领域提示提炼业务指标
针对每张事实表，根据业务领域提示和列语义，确定：
- 需要统计哪些业务指标（数量/金额/时长/比率…）
- 每个指标用哪个列聚合（factColumn = 元数据中真实存在的列名）
- 聚合函数：COUNT_DISTINCT / COUNT / SUM / AVG / MAX / MIN
- 为每个指标生成 ind:Measure + ind:MeasureApp

### 步骤 3 — 识别维度
从同一事实表的列中识别切分维度：
- 【强制】日期维度 — 每张事实表必须生成 5 个日期粒度维度（日/周/月/季/年）：
  日期列优先级：updatetime > update_time > createtime > create_time > created_at >
               dt > date > order_date > operate_date > 其他含 time/date/dt 的列
  5 个维度共享同一 hierarchyCode（如 "h_date" 或 "HIER_<列名>"），同一 physicalColumn，
  不同 viewTypeCode + levelSequence + levelCode：
    年  viewTypeCode=5  levelSequence=1  levelCode="year"
    季  viewTypeCode=4  levelSequence=2  levelCode="quarter"
    月  viewTypeCode=3  levelSequence=3  levelCode="month"
    周  viewTypeCode=2  levelSequence=4  levelCode="week"
    日  viewTypeCode=1  levelSequence=5  levelCode="day"
  每个日期维度各生成一个 ind:DimensionApp，dimFactColumn = 同一日期物理列
  ⚠️ 日期维度必须使用 dimTypeCode=0（退化维），不得使用 dimTypeCode=2
     原因：Java 会对 dimFactColumn 直接应用 date_format() 生成分组值；
           若用 dimTypeCode=2 JOIN 日期维度表，JOIN 条件两侧类型不匹配（格式化字符串 vs 整数键），导致所有行返回 NULL
     DimensionApp 不填 dimTable / dimPrimaryKey / dimColumn（退化维不需要这些属性）
- 没有对应维度表、维度值直接存在于事实表列中 → dimTypeCode=0（退化维）
  例：order_status、order_no、flag 等直接写在事实表里的列
  dimFactColumn = 该列名，无需 dimTable/dimPrimaryKey/dimColumn
- 有对应维度表、通过外键 JOIN 获取维度值 → dimTypeCode=2（标准维，有维表）
  例：fact_orders.customer_id → dim_customer（customer_grade、customer_type 等属性在维度表里）
  · dimFactColumn = 事实表中的外键列名（如 "customer_id"）
  · dimPrimaryKey = 维度表的主键列名（如 "customer_id"）
  · dimColumn     = 维度表中要展示/分组的列名（如 "customer_name"、"customer_grade"、"category"）
  · dimTable      = 对应维度表的 DwTable 实例
  ⚠️ 条件必填：所有 dimTypeCode=2 且 dimColumn != dimPrimaryKey 的标准维，
     必须同时填写 ind:dimColumnExpr "{d}.<属性列>"，让查询按各自属性列分组，而不是按共享外键/主键分组。
     这不仅适用于 CASE WHEN，也适用于普通维表属性列，例如仓库名/仓库城市、客户等级/客户类型、促销类型。
     若省略 dimColumnExpr，DA 查询可能在外层引用不存在的维度别名，出现 Unknown column，或产生错误分组口径。
  ⚠️ 同一外键可生成多个维度（每个有意义的属性列各一个 Dimension+DimensionApp），
     dimFactColumn/dimPrimaryKey 相同，dimColumn 不同；每个属性维度都必须填写各自的 dimColumnExpr。
  ⚠️ dimFactColumn 必须是事实表里真实存在的列（外键），绝对不能填维度表里的属性列名
- 为每个维度生成 ind:Dimension + ind:DimensionApp

### 步骤 4 — 生成统计信息和分类
- 若有多张维度表，可为 dimTypeCode=2 的维度生成 ind:DimHistogram
- 若元数据中可识别业务分类，生成 ind:Category 及父子链

─────────────────────────────────────────────────────────────
## 二、各类完整属性清单（必须逐条填写，互斥项除外）
─────────────────────────────────────────────────────────────

### ind:DwTable（每张选中事实表/维度表各一个实例）
必填：
  ind:tableName     "原始表名"          ← 直接来自元数据 tableName，不得修改
  ind:schemaName    "库名/schema名"     ← 严格取自每张表标题/说明中的 schemaName=xxx
                                          不得推测、替换或使用其他库名；多库扫描时绝对不能填 "*"
                                          例如 "tpcds", "biz_db", "dw_sales", "ods_erp"
  ind:cnName        "中文名称"          ← 必须是中文，来自表注释；无注释时按业务语义起中文名，不得填英文
  ind:sourceTypeCode 0                 ← 0=MySQL, 100=Doris, 101=TiDB；无法判断时填 0
  ind:hasConnection inst:conn_<连接标识> ← 引用对应的 DataConnection 实例（见下方）
  ind:hasColumn inst:col_<schema>__<table>__<column> ← 关联该表所有 DwColumn；多个字段用逗号分隔或多条三元组
可选：
  ind:description   "..."

### ind:DwColumn（每张 DwTable 的字段字典，必须覆盖元数据中该表列出的所有字段）
URI 命名：inst:col_<schema>__<table>__<column>，例如 inst:col_biz_db__measure__created_at
必填：
  ind:columnName      "真实列名"        ← 直接来自元数据列名，不得修改大小写或拼写
  ind:columnType      "字段类型"        ← 直接来自元数据字段类型，如 BIGINT/VARCHAR/DATETIME；不知道时填 "UNKNOWN"
  ind:cnName          "中文字段名"      ← 优先来自列注释；无注释时按列名语义翻译成中文，不得填英文
  ind:columnComment   "字段注释/业务含义" ← 优先来自元数据注释；无注释时补充中文解释，不得留空
  ind:isPrimaryKey    true 或 false     ← 元数据标记 PK 时 true，否则 false
  ind:isNullable      true 或 false     ← 元数据标记 NOT NULL 时 false，否则 true
可选：
  ind:ordinalPosition 1                 ← 字段在表中的顺序，无法判断可省略
  ind:sampleValue     "枚举/样例值"      ← 来自元数据 [枚举值: ...]，可多条

DwColumn 生成约束：
- 只为本批输出的 DwTable 生成字段，不引用本批元数据之外的表/列
- factColumn、dimFactColumn、dimPrimaryKey、dimColumn、physicalColumn 涉及的列，必须能在对应表的 DwColumn 中找到
- 维度表被 dimTable 引用时，也必须为该维度表生成 DwTable + DwColumn 字段字典

### ind:DataConnection（每个唯一数据库连接一个实例，多张表可共享）
URI 命名：inst:conn_<dbType>_<host简称>_<dbName>，例如 inst:conn_mysql_10_0_0_1_biz_db
必填：
  ind:dbType     "mysql"              ← 来自元数据「类型」字段，如 mysql / doris / starrocks
  ind:host       "ip或域名"           ← 来自元数据「主机」字段
  ind:port       3306                 ← 来自元数据「端口」字段，默认 MySQL=3306
  ind:dbUser     "用户名"             ← 来自元数据「账号」字段
  ind:dbPassword "密码"               ← 来自元数据「密码」字段，直接照抄，不得留空
  ind:dbName     "库名"               ← 与该表的 ind:schemaName 相同；多库扫描时必须使用表所属库名

### ind:TableHistogram（每张 DwTable 生成一个）
  ind:tableRowNum   <行数>             ← 来自元数据 rowCount；不知道时填 1000000
  ind:maxScanNum    <最大扫描行数>      ← 建议填 tableRowNum / 20，最小 5000

### ind:Measure（每个业务指标一个实例）
必填：
  ind:code          "MEAS_xxx"         ← 全大写 MEAS_ 前缀 + 英文下划线命名
  ind:cnName        "指标中文名"        ← 必须是中文，不得填英文或拼音
  ind:enName        "MEAS_xxx"         ← 与 code 相同
  ind:measTypeCode  0                  ← 0=原子, 1=衍生, 2=派生
  ind:unit          "个"               ← 单位，不得为空；推断规则见下方
  ind:caliber       "..."              ← 计算口径，不得为空；推断规则见下方
  ind:definition    "..."              ← 业务定义，不得为空
可选：
  ind:description   "..."
  ind:northStar     0                  ← 北极星指标时填 1
  ind:belongsToCategory inst:cat_xxx

unit 推断规则（必须根据业务语义填写，不得留空）：
  COUNT/COUNT_DISTINCT(主键/ID) → 按实体名推断："个"/"次"(操作/请求)/"笔"(订单/交易)/"辆"(车辆)
  SUM(金额/amount/price/cost/revenue) → "元"
  SUM(数量/qty/count/num) → "个"
  AVG/SUM(时长/duration/minutes/seconds) → "分钟" 或 "秒"
  比率/rate/ratio/pct/percent → "%"
  无法判断 → 按指标中文名语义推断，默认 "个"

caliber 推断规则（必须根据业务语义填写，不得留空）：
  格式：「对[范围/过滤说明]内的[业务实体]进行[聚合方式]计算」
  例：COUNT_DISTINCT(id) WHERE is_delete=0 → "统计指定时间范围内有效（未删除）记录的总数"
  例：SUM(amount) → "对指定时间范围内所有交易的金额求和"

### ind:MeasureApp（每个指标在每张事实表上的聚合定义，与 ind:Measure 1:1 对应）
必填：
  ind:applyTypeCode  0                 ← 0=原子, 1=衍生, 2=派生
  ind:expression     "..."             ← 格式规则见下方，不得使用纯 SQL 字符串
  ind:hasColumnDT    true 或 false     ← 事实表有 dt 分区时填 true，否则 false
  ind:available      1
  ind:appliesToTable inst:tbl_xxx

expression 格式规则（重要，必须严格遵守）：
  applyTypeCode=0 原子指标 → JSON 算子数组，根据聚合函数选择 operator：
    COUNT(DISTINCT ...) → "[{\"operatingType\":\"operator\",\"operator\":\"distinct_count\"}]"
    COUNT(...)          → "[{\"operatingType\":\"operator\",\"operator\":\"count\"}]"
    SUM(...)            → "[{\"operatingType\":\"operator\",\"operator\":\"sum\"}]"
    AVG(...)            → "[{\"operatingType\":\"operator\",\"operator\":\"avg\"}]"
    MAX(...)            → "[{\"operatingType\":\"operator\",\"operator\":\"max\"}]"
    MIN(...)            → "[{\"operatingType\":\"operator\",\"operator\":\"min\"}]"
  applyTypeCode=1 衍生指标 → JSON操作数组，每个token对应一个OperationItem（operand=子指标引用，operator=运算符）：
    例："MEAS_sales_amount / MEAS_order_cnt" 写为：
    "[{\"operatingType\":\"operand\",\"operand\":{\"measCode\":\"MEAS_sales_amount\"}},{\"operatingType\":\"operator\",\"operator\":\"/\"},{\"operatingType\":\"operand\",\"operand\":{\"measCode\":\"MEAS_order_cnt\"}}]"
    支持的运算符：+ - * /（括号用operator类型的 ( ) 表示）
  applyTypeCode=2 派生指标 → 与 applyTypeCode=0 相同的 JSON 算子数组格式

互斥组 A（applyTypeCode=0 时必填，applyTypeCode=1/2 时必须留空）：
  ind:factColumn     "列名"            ← 元数据中真实存在的列名，不得编造

互斥组 B（applyTypeCode=1/2 时必填，applyTypeCode=0 时必须留空）：
  ind:dependsOnMeasApp inst:ma_xxx    ← 指向依赖的原子 MeasureApp 实例（可多个）

可选：
  ind:whereCondition "SQL WHERE子句"  ← 如 "order_status NOT IN (0,9)"，car_type = 'new'

必须：每个 MeasureApp 至少挂载一个日期公共维度映射（见 ind:NaturalDimMapping 节）：
  ind:hasNaturalDimMapping inst:ndm_<meas_short>_date

### ind:NaturalDimMapping（每个 MeasureApp 必须生成一个日期映射，其他公共维度可选追加）
【核心规则】一个 naturalHierarchyCode="h_date" 的映射可自动覆盖该 hierarchyCode 下的
所有粒度（日/周/月/季/年），无需为每个粒度各建一个映射实例。

URI 命名：inst:ndm_<meas_code_short>_<用途>，如 inst:ndm_order_cnt_date、inst:ndm_order_cnt_shop
必填（二选一）：
  ind:naturalHierarchyCode "h_date"   ← 日期类公共维度（自动覆盖日/周/月/季/年全部粒度）
  ind:naturalDimCode "DIM_xxx"        ← 精确匹配单一公共维度（如门店 DIM_shop）
必填：
  ind:physicalColumn "列名"           ← 事实表中对应的物理列名（必须来自元数据真实存在的列）

physicalColumn 日期列优先级（从高到低，取第一个元数据中真实存在的列）：
  updatetime → update_time → createtime → create_time → created_at →
  dt → date → order_date → operate_date → 其他含 time/date/dt 的列

同一 MeasureApp 可挂多个映射（日期+门店+其他）：
  ind:hasNaturalDimMapping inst:ndm_xxx_date ,
                           inst:ndm_xxx_shop .

### ind:Dimension（每个切分维度一个实例）
必填：
  ind:code          "DIM_xxx"         ← 全大写 DIM_ 前缀 + 英文下划线命名
  ind:cnName        "维度中文名"        ← 必须是中文，不得填英文或拼音
  ind:enName        "DIM_xxx"         ← 与 code 相同
  ind:dimTypeCode   0                 ← 0=退化维, 1=标准维(无维表), 2=标准维(有维表), 4=自定义
  ind:viewTypeCode  0                 ← 0=字符, 1=日, 2=周, 3=月, 4=季, 5=年, 6=小时
  ind:isHyper       false
  ind:definition    "..."
层级维度时在 ind:Dimension 实例上填写（同一字段派生多粒度，或省/市层级）：
  ind:hierarchyCode   "HIER_xxx"      ← 同组维度共享同一编码，如 HIER_operate_time
  ind:levelSequence   1/2/3           ← 年=1, 月=2, 日=3（数值越小粒度越粗）
  ind:levelCode       "year"/"month"/"day" 或 "LEVEL_PROVINCE"/"LEVEL_CITY"
可选：
  ind:description   "..."
  ind:belongsToCategory inst:cat_xxx

### ind:DimensionApp（每个维度在每张事实表上各一个实例）
必填（所有类型）：
  ind:dimFactColumn   "列名"           ← 维度在事实表中的外键/列名（真实存在的列，不得编造）
  ind:masterPrimaryKey "列名"          ← 三种情况：
                                          ① 退化维(dimTypeCode=0)/主维度: 与 dimFactColumn 相同
                                          ② 标准维有维表(dimTypeCode=2): 与 dimPrimaryKey 相同（维度表的主键列名）
                                          ③ 层级从维: 指向父级维度的主键列
                                          ⚠️ dimTypeCode=2 时 masterPrimaryKey ≠ dimFactColumn，必须等于 dimPrimaryKey
层级维度时填写（写在 DimensionApp 上，与 Dimension 上的 hierarchyCode 对应）：
  ind:isMasterApp     true 或 false   ← 同层级组中是否为根/主应用；无层级时填 true
  ind:isRootJoin      true 或 false   ← 仅多级层次维度的顶层 DimensionApp 填 true
                                          （如年>月>日中的"年"维度应用）；
                                          退化维、单独维度（无层次）均填 false
  ind:available       1
  ind:dimFactTable    inst:tbl_xxx

仅 dimTypeCode=2 时填写（有独立维度表）：
  ind:dimPrimaryKey   "主键列名"       ← 维度表中的主键列
  ind:dimColumn       "显示值列名"     ← 维度表中存储显示文本的列
  ind:dimTable        inst:tbl_xxx    ← 指向维度表 DwTable 实例
条件必填/可选：
  ind:dimColumnExpr   "{d}.<属性列>"   ← 条件必填：dimTypeCode=2 且 dimColumn != dimPrimaryKey 时必须填写。
                                        普通属性列也要填写，不只是 CASE WHEN。
                                        例如 dimColumn="w_city"、dimPrimaryKey="w_warehouse_sk" 时，
                                        必须填写 ind:dimColumnExpr "{d}.w_city"。
                                        仅当确实按维表主键分组且 dimColumn == dimPrimaryKey 时可省略。
                                        SQL 表达式会替代维表主键作为 GROUP BY 键。
                                        {d} 为维度表别名占位符，引擎自动替换为实际别名。
                                        设置后将跳过外层 display JOIN，直接用表达式结果作为维度值。
                                        当多个维度共享同一个维表外键，但分别代表维表中的不同属性列时，
                                        即使不是 CASE WHEN，也必须写成 "{d}.<属性列>"。
                                        示例：
                                          ind:dimColumnExpr "{d}.customer_grade" ;
                                          ind:dimColumnExpr "CASE WHEN {d}.type_id IN (30,70) THEN '维修'
                                                                  WHEN {d}.type_id IN (301) THEN '直营钣喷'
                                                                  ELSE '其他' END" ;
                                        注意：dimColumnExpr 引用了 {d}.type_id 时，getColumns() 会自动
                                        将 type_id 加入维度表 JOIN 子查询的 SELECT 列表。

### ind:Category（若能从元数据识别业务分类时生成，否则跳过）
  ind:code        "CAT_xxx"
  ind:id          <数字>
  ind:name        "分类名称"
可选：
  ind:categoryParent inst:cat_xxx

### ind:DimHistogram（仅 dimTypeCode=2 的维度，可选生成）
  ind:histDimCode     "DIM_xxx"       ← 对应维度的 code
  ind:histTableName   "fact_table"    ← 事实表名
  ind:dimensionRowNum <基数>          ← 维度不重复值数量，不确定时填 100
  ind:tableRowNum     <事实表行数>    ← 与对应 TableHistogram 保持一致

─────────────────────────────────────────────────────────────
## 三、输出格式规则
─────────────────────────────────────────────────────────────
1. 仅输出合法 Turtle 语法，不输出任何解释文字，不输出 Markdown 代码块标记
2. 不输出 @prefix 声明（已由外部自动添加）
3. 不输出 owl:Class / owl:ObjectProperty / owl:DatatypeProperty 定义（已固定）
4. 实例 URI = inst: + lowercase(code)，具体规则：
   - ind:Measure         URI: inst:<lowercase(code)>                       如 code="MEAS_total_cnt"  → inst:meas_total_cnt
   - ind:Dimension       URI: inst:<lowercase(code)>                       如 code="DIM_dim_dt"      → inst:dim_dim_dt
  - ind:DwTable         URI: inst:tbl_<schema>__<table>                  （双下划线分隔 schema 和 tableName）
   - ind:DwColumn        URI: inst:col_<schema>__<table>__<column>        （schema/table/column 均做 URI 安全化）
   - ind:MeasureApp      URI: inst:ma_<meas_code_lower_without_MEAS_>_<origin|derived|extended>
                              如 MEAS_order_cnt → inst:ma_order_cnt_origin
   - ind:DimensionApp    URI: inst:da_<dim_code_lower_without_DIM_>_<tableName>
                              如 DIM_dim_dt, 事实表 fact_order → inst:da_dim_dt_fact_order
   - ind:Category        URI: inst:<lowercase(code)>                       如 code="CAT_SALES" → inst:cat_sales
   - ind:TableHistogram  URI: inst:hist_tbl_<tableName>
   - ind:DimHistogram    URI: inst:dimhist_<dim_code_lower_without_DIM_>__<tableName>
   - ind:DataConnection  URI: inst:conn_<dbType>_<host简称>_<dbName>       如 inst:conn_mysql_10_0_0_1_biz_db
   - ind:NaturalDimMapping URI: inst:ndm_<meas_short>_<用途>               如 inst:ndm_order_cnt_date
5. ind:Measure.measTypeCode 与其 MeasureApp.applyTypeCode 必须保持一致（均为 0/1/2）
6. ind:Measure 通过 ind:hasMeasureApp 关联 ind:MeasureApp
   ind:Dimension 通过 ind:hasDimApp 关联 ind:DimensionApp
7. ind:MeasureApp 与 ind:DimensionApp 分开声明，不合并

─────────────────────────────────────────────────────────────
## 四、完整示例
─────────────────────────────────────────────────────────────

业务领域：统计指标数量、按创建月份分析
元数据：measure 表（列: id INT PK, cn_name VARCHAR, is_delete INT, create_month VARCHAR, created_at DATETIME）
# 数据库配置: 标签=biz_label  类型=mysql  主机=10.0.0.1:3306  账号=appuser  实际库名(schemaName)=biz_db
# ⚠ ind:schemaName 必须使用上面的「实际库名」: "biz_db"

inst:conn_mysql_10_0_0_1_biz_db a ind:DataConnection ;
    ind:dbType     "mysql" ;
    ind:host       "10.0.0.1" ;
    ind:port       3306 ;
    ind:dbUser     "appuser" ;
    ind:dbPassword "secret" ;
    ind:dbName     "biz_db" .

inst:tbl_biz_db__measure a ind:DwTable ;
    ind:schemaName    "biz_db" ;
    ind:tableName     "measure" ;
    ind:cnName        "指标表" ;
    ind:description   "指标平台中存储所有业务指标定义的核心表" ;
    ind:sourceTypeCode 0 ;
    ind:hasConnection inst:conn_mysql_10_0_0_1_biz_db ;
    ind:hasTableHistogram inst:hist_tbl_measure ;
    ind:hasColumn inst:col_biz_db__measure__id ,
                  inst:col_biz_db__measure__cn_name ,
                  inst:col_biz_db__measure__is_delete ,
                  inst:col_biz_db__measure__create_month ,
                  inst:col_biz_db__measure__created_at .

inst:col_biz_db__measure__id a ind:DwColumn ;
    ind:columnName    "id" ;
    ind:columnType    "INT" ;
    ind:cnName        "指标ID" ;
    ind:columnComment "指标表主键ID" ;
    ind:isPrimaryKey  true ;
    ind:isNullable    false ;
    ind:ordinalPosition 1 .

inst:col_biz_db__measure__cn_name a ind:DwColumn ;
    ind:columnName    "cn_name" ;
    ind:columnType    "VARCHAR" ;
    ind:cnName        "指标中文名" ;
    ind:columnComment "指标的中文展示名称" ;
    ind:isPrimaryKey  false ;
    ind:isNullable    true ;
    ind:ordinalPosition 2 .

inst:col_biz_db__measure__is_delete a ind:DwColumn ;
    ind:columnName    "is_delete" ;
    ind:columnType    "INT" ;
    ind:cnName        "是否删除" ;
    ind:columnComment "逻辑删除标识，0表示有效" ;
    ind:isPrimaryKey  false ;
    ind:isNullable    true ;
    ind:ordinalPosition 3 ;
    ind:sampleValue   "0" ,
                      "1" .

inst:col_biz_db__measure__create_month a ind:DwColumn ;
    ind:columnName    "create_month" ;
    ind:columnType    "VARCHAR" ;
    ind:cnName        "创建月份" ;
    ind:columnComment "指标创建月份" ;
    ind:isPrimaryKey  false ;
    ind:isNullable    true ;
    ind:ordinalPosition 4 .

inst:col_biz_db__measure__created_at a ind:DwColumn ;
    ind:columnName    "created_at" ;
    ind:columnType    "DATETIME" ;
    ind:cnName        "创建时间" ;
    ind:columnComment "指标记录创建时间" ;
    ind:isPrimaryKey  false ;
    ind:isNullable    true ;
    ind:ordinalPosition 5 .

inst:hist_tbl_measure a ind:TableHistogram ;
    ind:tableRowNum  10000 ;
    ind:maxScanNum   5000 .

inst:cat_root a ind:Category ;
    ind:code "CAT_ROOT" ;
    ind:id   1 ;
    ind:name "全部指标" .

inst:meas_total_cnt a ind:Measure ;
    ind:code         "MEAS_total_cnt" ;
    ind:cnName       "指标总数" ;
    ind:enName       "MEAS_total_cnt" ;
    ind:measTypeCode 0 ;
    ind:unit         "个" ;
    ind:caliber      "统计指定时间范围内有效（is_delete=0）的指标记录总数" ;
    ind:definition   "衡量指标平台中当前在线指标的建设规模" ;
    ind:belongsToCategory inst:cat_root ;
    ind:hasMeasureApp inst:ma_total_cnt_origin .

inst:ma_total_cnt_origin a ind:MeasureApp ;
    ind:applyTypeCode  0 ;
    ind:factColumn     "id" ;
    ind:expression     "[{\"operatingType\":\"operator\",\"operator\":\"distinct_count\"}]" ;
    ind:hasColumnDT    false ;
    ind:whereCondition "is_delete = 0" ;
    ind:available      1 ;
    ind:appliesToTable inst:tbl_biz_db__measure ;
    ind:hasNaturalDimMapping inst:ndm_total_cnt_date .

# NaturalDimMapping：一个实例覆盖日/周/月/季/年全部粒度
inst:ndm_total_cnt_date a ind:NaturalDimMapping ;
    ind:naturalHierarchyCode "h_date" ;
    ind:physicalColumn       "created_at" .   # 优先取 updatetime/createtime，此处示例用 created_at

# 5个日期维度（共享 hierarchyCode="h_date"，共享 dimFactColumn="created_at"）
inst:dim_date_year a ind:Dimension ;
    ind:code          "DIM_date_year" ;
    ind:cnName        "年" ;
    ind:enName        "DIM_date_year" ;
    ind:dimTypeCode   0 ;
    ind:viewTypeCode  5 ;
    ind:isHyper       false ;
    ind:hierarchyCode "h_date" ;
    ind:levelSequence 1 ;
    ind:levelCode     "year" ;
    ind:definition    "基于 created_at 派生的年粒度维度" ;
    ind:hasDimApp     inst:da_date_year_measure .

inst:dim_date_quarter a ind:Dimension ;
    ind:code          "DIM_date_quarter" ;
    ind:cnName        "季度" ;
    ind:enName        "DIM_date_quarter" ;
    ind:dimTypeCode   0 ;
    ind:viewTypeCode  4 ;
    ind:isHyper       false ;
    ind:hierarchyCode "h_date" ;
    ind:levelSequence 2 ;
    ind:levelCode     "quarter" ;
    ind:definition    "基于 created_at 派生的季度粒度维度" ;
    ind:hasDimApp     inst:da_date_quarter_measure .

inst:dim_date_month a ind:Dimension ;
    ind:code          "DIM_date_month" ;
    ind:cnName        "月" ;
    ind:enName        "DIM_date_month" ;
    ind:dimTypeCode   0 ;
    ind:viewTypeCode  3 ;
    ind:isHyper       false ;
    ind:hierarchyCode "h_date" ;
    ind:levelSequence 3 ;
    ind:levelCode     "month" ;
    ind:definition    "基于 created_at 派生的月粒度维度" ;
    ind:hasDimApp     inst:da_date_month_measure .

inst:dim_date_week a ind:Dimension ;
    ind:code          "DIM_date_week" ;
    ind:cnName        "周" ;
    ind:enName        "DIM_date_week" ;
    ind:dimTypeCode   0 ;
    ind:viewTypeCode  2 ;
    ind:isHyper       false ;
    ind:hierarchyCode "h_date" ;
    ind:levelSequence 4 ;
    ind:levelCode     "week" ;
    ind:definition    "基于 created_at 派生的周粒度维度" ;
    ind:hasDimApp     inst:da_date_week_measure .

inst:dim_date_day a ind:Dimension ;
    ind:code          "DIM_date_day" ;
    ind:cnName        "日" ;
    ind:enName        "DIM_date_day" ;
    ind:dimTypeCode   0 ;
    ind:viewTypeCode  1 ;
    ind:isHyper       false ;
    ind:hierarchyCode "h_date" ;
    ind:levelSequence 5 ;
    ind:levelCode     "day" ;
    ind:definition    "基于 created_at 派生的日粒度维度" ;
    ind:hasDimApp     inst:da_date_day_measure .

# 5个 DimensionApp — 全部指向同一物理列 "created_at"
inst:da_date_year_measure a ind:DimensionApp ;
    ind:dimFactColumn    "created_at" ;
    ind:masterPrimaryKey "created_at" ;
    ind:isMasterApp      true ;
    ind:isRootJoin       true ;
    ind:available        1 ;
    ind:dimFactTable     inst:tbl_biz_db__measure .

inst:da_date_quarter_measure a ind:DimensionApp ;
    ind:dimFactColumn    "created_at" ;
    ind:masterPrimaryKey "created_at" ;
    ind:isMasterApp      false ;
    ind:isRootJoin       false ;
    ind:available        1 ;
    ind:dimFactTable     inst:tbl_biz_db__measure .

inst:da_date_month_measure a ind:DimensionApp ;
    ind:dimFactColumn    "created_at" ;
    ind:masterPrimaryKey "created_at" ;
    ind:isMasterApp      false ;
    ind:isRootJoin       false ;
    ind:available        1 ;
    ind:dimFactTable     inst:tbl_biz_db__measure .

inst:da_date_week_measure a ind:DimensionApp ;
    ind:dimFactColumn    "created_at" ;
    ind:masterPrimaryKey "created_at" ;
    ind:isMasterApp      false ;
    ind:isRootJoin       false ;
    ind:available        1 ;
    ind:dimFactTable     inst:tbl_biz_db__measure .

inst:da_date_day_measure a ind:DimensionApp ;
    ind:dimFactColumn    "created_at" ;
    ind:masterPrimaryKey "created_at" ;
    ind:isMasterApp      false ;
    ind:isRootJoin       false ;
    ind:available        1 ;
    ind:dimFactTable     inst:tbl_biz_db__measure .

# ── 示例：dimTypeCode=2 标准维（有维表，外键 JOIN）──
# 事实表 measure 有 category_id 外键列，关联维表 category(id, name)
inst:dim_category a ind:Dimension ;
    ind:code          "DIM_category" ;
    ind:cnName        "业务分类" ;
    ind:enName        "DIM_category" ;
    ind:dimTypeCode   2 ;          ← 有独立维度表
    ind:viewTypeCode  0 ;
    ind:isHyper       false ;
    ind:definition    "指标所属业务分类，关联 category 维度表" ;
    ind:hasDimApp     inst:da_category_measure .

inst:da_category_measure a ind:DimensionApp ;
    ind:dimFactColumn    "category_id" ;   ← 事实表中的外键列名
    ind:masterPrimaryKey "id" ;            ← dimTypeCode=2 时 = dimPrimaryKey（维度表主键）⚠️ 不等于 dimFactColumn
    ind:isMasterApp      true ;
    ind:isRootJoin       false ;
    ind:available        1 ;
    ind:dimFactTable     inst:tbl_biz_db__measure ;
    ind:dimPrimaryKey    "id" ;            ← 维度表主键列（JOIN 匹配列）
    ind:dimColumn        "name" ;          ← 维度表显示值列
    ind:dimTable         inst:tbl_biz_db__category .  ← 维度表 DwTable

# ── 示例：同一外键派生多个维度属性（FK名=PK名场景）──
# 事实表 fact_orders 有 customer_id 外键列，关联维表 dim_customer(customer_id, customer_name, customer_grade, customer_type)
# 三个有业务价值的属性列 → 生成三个独立的 Dimension + DimensionApp，共享同一 FK

# 属性1：客户名称（主体维度，dimColumn=customer_name）
inst:dim_customer a ind:Dimension ;
    ind:code          "DIM_customer" ;
    ind:cnName        "客户" ;
    ind:dimTypeCode   2 ;
    ind:hasDimApp     inst:da_customer_fact_orders .

inst:da_customer_fact_orders a ind:DimensionApp ;
    ind:dimFactColumn    "customer_id" ;   ← 事实表外键列（FK）
    ind:masterPrimaryKey "customer_id" ;  ← FK名=PK名时与dimFactColumn相同
    ind:isMasterApp      true ;
    ind:isRootJoin       false ;
    ind:available        1 ;
    ind:dimFactTable     inst:tbl_db__fact_orders ;
    ind:dimPrimaryKey    "customer_id" ;  ← 维度表主键（与FK同名）
    ind:dimColumn        "customer_name" ; ← 展示列（主体维度取名称列）
    ind:dimColumnExpr    "{d}.customer_name" ; ← 取值列，避免与共享FK的其他属性维度混淆
    ind:dimTable         inst:tbl_db__dim_customer .

# 属性2：客户等级（dimColumn=customer_grade）—— 与属性1共享同一FK
inst:dim_customer_grade a ind:Dimension ;
    ind:code          "DIM_customer_grade" ;
    ind:cnName        "客户等级" ;
    ind:dimTypeCode   2 ;              ← 也是dimTypeCode=2，不是退化维(0)！列在维度表不在事实表
    ind:hasDimApp     inst:da_customer_grade_fact_orders .

inst:da_customer_grade_fact_orders a ind:DimensionApp ;
    ind:dimFactColumn    "customer_id" ;  ← 与属性1完全相同的FK
    ind:masterPrimaryKey "customer_id" ;
    ind:isMasterApp      true ;
    ind:isRootJoin       false ;
    ind:available        1 ;
    ind:dimFactTable     inst:tbl_db__fact_orders ;
    ind:dimPrimaryKey    "customer_id" ;
    ind:dimColumn        "customer_grade" ; ← 只有这里不同，指向维度表的属性列
    ind:dimColumnExpr    "{d}.customer_grade" ;
    ind:dimTable         inst:tbl_db__dim_customer .

# 属性3：客户类型（dimColumn=customer_type）—— 同理
inst:dim_customer_type a ind:Dimension ;
    ind:code          "DIM_customer_type" ;
    ind:cnName        "客户类型" ;
    ind:dimTypeCode   2 ;
    ind:hasDimApp     inst:da_customer_type_fact_orders .

inst:da_customer_type_fact_orders a ind:DimensionApp ;
    ind:dimFactColumn    "customer_id" ;
    ind:masterPrimaryKey "customer_id" ;
    ind:isMasterApp      true ;
    ind:isRootJoin       false ;
    ind:available        1 ;
    ind:dimFactTable     inst:tbl_db__fact_orders ;
    ind:dimPrimaryKey    "customer_id" ;
    ind:dimColumn        "customer_type" ;
    ind:dimColumnExpr    "{d}.customer_type" ;
    ind:dimTable         inst:tbl_db__dim_customer .
# ⚠️ 关键规则：customer_grade / customer_type 虽然是枚举值，但它们在维度表里，
#    不是退化维(dimTypeCode=0)，必须用dimTypeCode=2通过JOIN获取
#    同理，仓库与仓库城市可共享 ws_warehouse_sk → w_warehouse_sk，
#    但仓库维度必须写 ind:dimColumnExpr "{d}.w_warehouse_name"，
#    仓库城市维度必须写 ind:dimColumnExpr "{d}.w_city"。"""


_USER_TEMPLATE = """\
## 业务领域提示

{domain_section}

## 数据源图谱元数据（源数据库表/列信息，必须基于此生成实例，tableName/dimFactColumn/factColumn 必须来自此处）

{summary}

{pattern_section}## 输出要求
- 直接输出 Turtle 实例，第一行从 inst: 实例声明开始
- 不输出 @prefix 声明（已由外部添加）
- 不输出 owl:Class/Property 定义（已固定）
- URI 使用描述性 inst:xxx 命名，不使用数字编号
- 覆盖所有与业务领域相关的指标和维度，每个指标必须有对应的 MeasureApp
- 所有非互斥属性必须填写，不得留空
"""


@dataclass
class _SummaryChunk:
    text: str
    table_sections: list[str]
    table_names: list[str]


# ── Builder ──────────────────────────────────────────────────────────────── #

class BusinessKGBuilder:
    """Call LLM to produce a business OWL ontology from metadata summary."""

    def __init__(
        self,
        api_key: str,
        base_url: str,
        model: str,
        log_cb: Optional[Callable[[str], None]] = None,
    ) -> None:
        self._api_key  = api_key
        self._base_url = base_url.rstrip("/")
        self._model    = model
        self._log      = log_cb or (lambda msg: None)

    # ------------------------------------------------------------------ #

    @classmethod
    def from_env(cls, base_dir: Optional[Path] = None,
                 log_cb: Optional[Callable[[str], None]] = None) -> "BusinessKGBuilder":
        import os
        model_override = os.environ.get("BUSINESS_KG_MODEL", "").strip()
        cfg = llm_config_from_env(base_dir, model_override=model_override)
        validate_llm_config(cfg, purpose="Business KG generation")
        return cls(
            api_key  = cfg["api_key"],
            base_url = cfg["base_url"],
            model    = cfg["model"],
            log_cb   = log_cb,
        )

    # ------------------------------------------------------------------ #

    # 摘要最大字符数（约 7.5K tokens），超过后智能截断
    _MAX_SUMMARY_CHARS  = 30_000
    # 参考规律上下文最大字符数
    _MAX_PATTERN_CHARS  = 4_000
    # Per-request summary size for incremental generation. Keeping this well
    # below the one-shot prompt size reduces gateway resets and long tail waits.
    _CHUNK_MAX_CHARS    = 10_000
    _MAX_BATCH_OUTPUT_TOKENS = 8192

    def _trim_summary(self, summary: str, domain_hint: str) -> str:
        """
        若摘要超过 _MAX_SUMMARY_CHARS，按与 domain_hint 的相关性评分后截断，
        优先保留相关性高的表节。
        """
        if len(summary) <= self._MAX_SUMMARY_CHARS:
            return summary

        self._log(
            f"  [摘要截断] 原始摘要 {len(summary):,} 字符，超过上限 {self._MAX_SUMMARY_CHARS:,}，"
            "正在按业务相关性筛选…"
        )

        # 按 ### 分割成 header + 若干表节
        import re as _re
        parts = _re.split(r"(?=^### )", summary, flags=_re.MULTILINE)
        headers = [p for p in parts if not p.startswith("### ")]
        table_sections = [p for p in parts if p.startswith("### ")]

        # 给每个表节打分（domain_hint 关键词命中次数）
        hint_words = [w for w in domain_hint.lower().split() if len(w) > 1] if domain_hint else []

        def _score(sec: str) -> int:
            low = sec.lower()
            return sum(low.count(w) for w in hint_words)

        table_sections.sort(key=lambda s: -_score(s))

        # 贪心拼入，直到接近上限
        budget = self._MAX_SUMMARY_CHARS - sum(len(h) for h in headers)
        kept, dropped = [], 0
        for sec in table_sections:
            if len(sec) <= budget:
                kept.append(sec)
                budget -= len(sec)
            else:
                dropped += 1

        result = "\n".join(headers + kept)
        self._log(
            f"  [摘要截断] 保留 {len(kept)}/{len(table_sections)} 张表节，"
            f"丢弃 {dropped} 张，截断后 {len(result):,} 字符"
        )
        return result

    def build(
        self,
        summary: str,
        domain_hint: str = "",
        pattern_context: str = "",
        progress_cb: Optional[Callable[[int, str], None]] = None,
        preserve_all_tables: bool = False,
    ) -> tuple[str, bool]:
        """
        Call LLM and return (turtle_str, success).
        pattern_context: optional text summary of measure/dimension patterns
                         extracted from a reference indicator database.
        """
        # ── 1. 智能截断摘要，防止请求体过大 ───────────────────────────── #
        if preserve_all_tables:
            self._log("  [摘要] 已启用全表保留模式，跳过表节截断，后续按表分批调用 LLM")
        else:
            summary = self._trim_summary(summary, domain_hint)

        # ── 2. 截断模式上下文 ──────────────────────────────────────────── #
        if len(pattern_context) > self._MAX_PATTERN_CHARS:
            self._log(
                f"  [参考规律截断] {len(pattern_context):,} → {self._MAX_PATTERN_CHARS:,} 字符"
            )
            pattern_context = pattern_context[: self._MAX_PATTERN_CHARS] + "\n…（已截断）"

        domain_section = self._build_domain_section(domain_hint)
        pattern_section = self._build_pattern_section(pattern_context)

        chunks = self._split_summary(summary)
        total_chars = len(_SYSTEM_PROMPT) + len(summary) + len(domain_section) + len(pattern_section)
        est_tokens  = total_chars // 4
        self._log(f"调用 LLM 模型: {self._model}")
        self._log(f"业务领域提示: {domain_hint.strip() or '（未提供）'}")
        self._log(
            f"输入规模: 摘要 {len(summary):,} 字符 | "
            f"提示词合计 {total_chars:,} 字符 ≈ {est_tokens:,} tokens"
        )
        self._log(f"[分批] 已将业务本体生成拆成 {len(chunks)} 个 LLM 小批次")
        for i, chunk in enumerate(chunks, 1):
            names = ", ".join(chunk.table_names[:5]) or "无表名"
            if len(chunk.table_names) > 5:
                names += f" 等 {len(chunk.table_names)} 张表"
            self._log(f"[分批]   {i}/{len(chunks)}: {len(chunk.text):,} 字符，表: {names}")
        if progress_cb:
            progress_cb(4, f"{len(chunks)} 个批次")

        instance_parts: list[str] = []
        build_start = time.time()
        for i, chunk in enumerate(chunks, 1):
            tbl_label = ", ".join(chunk.table_names[:3]) or "无表名"
            if len(chunk.table_names) > 3:
                tbl_label += f" 等{len(chunk.table_names)}表"
            self._log(f"[分批] ▶ 开始第 {i}/{len(chunks)} 批（表: {tbl_label}）")
            batch_start = time.time()
            instances = self._generate_chunk(
                chunk=chunk,
                chunk_no=i,
                total=len(chunks),
                domain_section=domain_section,
                pattern_section=pattern_section,
            )
            if instances is None and len(chunk.table_sections) > 1:
                self._log(
                    f"[分批] 第 {i}/{len(chunks)} 批失败，继续拆成 "
                    f"{len(chunk.table_sections)} 个单表小批次重试…"
                )
                sub_parts: list[str] = []
                for j, section in enumerate(chunk.table_sections, 1):
                    name = self._section_table_name(section)
                    sub_chunk = _SummaryChunk(
                        text=self._chunk_text_from_sections([section]),
                        table_sections=[section],
                        table_names=[name] if name else [],
                    )
                    sub_instances = self._generate_chunk(
                        chunk=sub_chunk,
                        chunk_no=j,
                        total=len(chunk.table_sections),
                        domain_section=domain_section,
                        pattern_section=pattern_section,
                        label_prefix=f"{i}.{j}",
                    )
                    if sub_instances is None:
                        self._log(f"[错误] 单表小批次失败: {name or j}")
                        return "", False
                    sub_parts.append(sub_instances)
                instances = "\n\n".join(sub_parts)

            if instances is None:
                self._log(f"[错误] 第 {i}/{len(chunks)} 批 LLM 生成失败")
                return "", False

            counts = self._count_instances_by_type(instances)
            elapsed = int(time.time() - batch_start)
            total_elapsed = int(time.time() - build_start)
            summary_pairs = ", ".join(f"{k}={v}" for k, v in counts.items()) or "无新实例"
            self._log(
                f"[分批] ✅ 第 {i}/{len(chunks)} 批完成（表: {tbl_label}） "
                f"耗时 {elapsed}s / 累计 {total_elapsed}s | {summary_pairs}"
            )

            instance_parts.append(
                f"# ═══ LLM 分批实例 {i}/{len(chunks)} ═══\n{instances.strip()}"
            )
            if progress_cb:
                progress_cb(5, f"{i}/{len(chunks)} 批完成")

        if not instance_parts:
            return "", False

        instances_str = "\n\n".join(instance_parts).strip()
        if not instances_str:
            self._log("[错误] LLM 未返回实例内容")
            return "", False

        # 拼接：固定本体前缀 + LLM 实例
        turtle_str = _ONTOLOGY_PREAMBLE + "\n# ═══ 数据实例 ═══\n\n" + instances_str

        # 修复 LLM 生成的 code 缺失 MEAS_/DIM_ 前缀
        turtle_str = self._fix_code_prefixes(turtle_str)

        # Validate with rdflib
        ok, msg = self._validate_turtle(turtle_str)
        if not ok:
            self._log(f"[警告] Turtle 语法校验失败: {msg}")
            self._log("尝试修复并重新校验…")
            turtle_str, ok = self._repair_turtle(turtle_str)
            if not ok:
                # Last resort: try to salvage as much as possible
                turtle_str, ok = self._repair_drop_bad_triples(turtle_str)
            if not ok:
                self._log("[错误] 修复失败，返回原始内容（含语法问题）")
                # Still return True so user can see what was generated
                return turtle_str, True

        if progress_cb:
            progress_cb(6, f"{len(turtle_str)} 字符")
        self._log(f"✓ 业务图谱生成成功，Turtle 长度: {len(turtle_str)} 字符")
        return turtle_str, True

    def _build_domain_section(self, domain_hint: str) -> str:
        if domain_hint.strip():
            return (
                f"{domain_hint.strip()}\n\n"
                "请根据此业务领域提示，识别数据库中能支撑该分析的表和字段，"
                "生成有业务意义的指标（如总数、金额、比率等）和维度（如时间、状态、类别等）。\n\n"
                "本次采用分批生成：每批只基于本批元数据生成实例；同名 DataConnection/DwTable "
                "可重复输出，系统会在合并校验时去重。"
            )
        return (
            "（未提供业务领域提示）\n\n"
            "请根据数据库元数据，自动识别核心事实表，生成覆盖主要业务场景的指标和维度。\n\n"
            "本次采用分批生成：每批只基于本批元数据生成实例；同名 DataConnection/DwTable "
            "可重复输出，系统会在合并校验时去重。"
        )

    def _build_pattern_section(self, pattern_context: str) -> str:
        if not pattern_context.strip():
            return ""
        return (
            "## 同类指标平台建模参考\n\n"
            "以下是已有指标平台的建模规律，可作为命名风格和结构参考，"
            "但必须基于当前数据库的表/字段语义生成新实例，不得照抄已有指标名称：\n\n"
            + pattern_context.strip()
            + "\n\n"
        )

    def _split_summary(self, summary: str) -> list[_SummaryChunk]:
        header, tail, sections = self._summary_parts(summary)
        if not sections:
            return [_SummaryChunk(text=summary, table_sections=[], table_names=[])]

        # One table per chunk — improves reliability with reasoning models that
        # otherwise omit Measure/MeasureApp on long multi-table prompts.
        chunks: list[_SummaryChunk] = [self._make_chunk([section]) for section in sections]

        if not chunks:
            return [_SummaryChunk(text=summary, table_sections=[], table_names=[])]
        return chunks

    def _summary_parts(self, summary: str) -> tuple[str, str, list[str]]:
        lines = summary.splitlines()
        header_lines: list[str] = []
        tail_lines: list[str] = []
        sections: list[str] = []
        current: Optional[list[str]] = None
        current_category: list[str] = []
        in_tail = False
        saw_table = False
        table_category_hints = ("事实表", "维度表", "桥接表", "查找表", "视图", "分类未知")

        def finish_current() -> None:
            nonlocal current
            if current:
                sections.append("\n".join(current).strip())
                current = None

        for line in lines:
            if line.startswith("## "):
                finish_current()
                if any(hint in line for hint in table_category_hints):
                    current_category = [line]
                    in_tail = False
                elif saw_table:
                    current_category = []
                    in_tail = True
                    tail_lines.append(line)
                else:
                    header_lines.append(line)
                continue

            if line.startswith("### "):
                finish_current()
                saw_table = True
                in_tail = False
                current = []
                if current_category:
                    current.extend(current_category)
                    current.append("")
                current.append(line)
                continue

            if current is not None:
                current.append(line)
            elif in_tail:
                tail_lines.append(line)
            else:
                header_lines.append(line)

        finish_current()

        header = "\n".join(header_lines).strip()
        tail = "\n".join(tail_lines).strip()
        if len(tail) > 5_000:
            self._log(f"  [分批] 外键摘要 {len(tail):,} 字符，截断到 5,000 字符后随批次发送")
            tail = tail[:5_000] + "\n…（外键摘要已截断）"

        self._summary_header = header
        self._summary_tail = tail
        return header, tail, sections

    def _chunk_text_from_sections(self, sections: list[str]) -> str:
        header = getattr(self, "_summary_header", "")
        tail = getattr(self, "_summary_tail", "")
        parts = [p for p in (header, "\n\n".join(sections), tail) if p.strip()]
        return "\n\n".join(parts).strip()

    def _make_chunk(self, sections: list[str]) -> _SummaryChunk:
        table_names = [name for name in (self._section_table_name(s) for s in sections) if name]
        return _SummaryChunk(
            text=self._chunk_text_from_sections(sections),
            table_sections=list(sections),
            table_names=table_names,
        )

    @staticmethod
    def _section_table_name(section: str) -> str:
        m = re.search(r"^###\s+([^\n—]+)", section, flags=re.MULTILINE)
        return m.group(1).strip() if m else ""

    def _generate_chunk(
        self,
        chunk: _SummaryChunk,
        chunk_no: int,
        total: int,
        domain_section: str,
        pattern_section: str,
        label_prefix: str = "",
    ) -> Optional[str]:
        label = label_prefix or str(chunk_no)
        names = ", ".join(chunk.table_names[:6]) or "无表名"
        if len(chunk.table_names) > 6:
            names += f" 等 {len(chunk.table_names)} 张表"
        self._log(
            f"[分批] ▶ 开始 LLM 批次 {label}/{total}："
            f"{len(chunk.text):,} 字符，{len(chunk.table_names)} 张表（{names}）"
        )
        batch_note = (
            f"\n\n## 分批执行说明\n"
            f"这是第 {label}/{total} 个 LLM 小批次。请只围绕本批元数据中的表生成实例，"
            "不得引用本批元数据之外的表/列；如果本批只有维度表且无法形成指标，"
            "也要输出相关 DwTable / DwColumn / DataConnection / TableHistogram 实例。"
        )
        user_msg = _USER_TEMPLATE.format(
            domain_section=domain_section + batch_note,
            summary=chunk.text,
            pattern_section=pattern_section,
        )
        payload = {
            "model": self._model,
            "max_tokens": self._MAX_BATCH_OUTPUT_TOKENS,
            "temperature": 0.15,
            "messages": [
                {"role": "system", "content": _SYSTEM_PROMPT},
                {"role": "user", "content": user_msg},
            ],
        }

        raw_response = self._call_api(
            payload,
            request_label=f"业务本体批次 {label}/{total}",
            timeout=1200,
            max_retries=3,
        )
        if raw_response is None:
            return None

        instances = self._extract_instances(raw_response)
        if not instances:
            self._log(f"[分批] 第 {label}/{total} 批未返回有效实例")
            return None

        chunk_ttl = _ONTOLOGY_PREAMBLE + "\n# ═══ 数据实例 ═══\n\n" + instances
        ok, msg = self._validate_turtle(chunk_ttl)
        if not ok:
            self._log(f"[分批] 第 {label}/{total} 批 Turtle 校验失败: {msg}")
            self._log("[分批] 尝试逐块修复该批输出…")
            chunk_ttl, ok = self._repair_drop_bad_triples(chunk_ttl)
            if not ok:
                return None
            instances = self._extract_generated_instances(chunk_ttl)

        self._log(
            f"[分批] ✓ 批次 {label}/{total} 完成，实例 {len(instances):,} 字符"
        )
        return instances

    @staticmethod
    def _count_instances_by_type(turtle_str: str) -> dict[str, int]:
        """Count `a ind:Type` declarations in a Turtle fragment, ordered by interest."""
        import re
        counts: dict[str, int] = {}
        for m in re.finditer(r"\ba\s+ind:([A-Z][A-Za-z]+)\b", turtle_str):
            t = m.group(1)
            counts[t] = counts.get(t, 0) + 1
        order = ["Measure", "MeasureApp", "Dimension", "DimensionApp",
                 "Category", "DwTable", "DwColumn", "TableHistogram",
                 "DataConnection", "NaturalDimMapping"]
        ordered: dict[str, int] = {}
        for k in order:
            if k in counts:
                ordered[k] = counts.pop(k)
        for k, v in counts.items():
            ordered[k] = v
        return ordered

    @staticmethod
    def _extract_generated_instances(turtle_str: str) -> str:
        marker = "# ═══ 数据实例 ═══"
        if marker in turtle_str:
            return turtle_str.split(marker, 1)[1].strip()
        lines = []
        for line in turtle_str.splitlines():
            s = line.strip()
            if s.startswith("@prefix") or s.startswith("ind:") or s.startswith("<http://indicator.insightmind.com/ontology>"):
                continue
            lines.append(line)
        return "\n".join(lines).strip()

    # ------------------------------------------------------------------ #
    # Internal helpers
    # ------------------------------------------------------------------ #

    def _call_api(
        self,
        payload: dict,
        request_label: str = "LLM 请求",
        timeout: int = 600,
        max_retries: int = 3,
    ) -> Optional[str]:
        import threading

        url = chat_completions_url(self._base_url)
        body = json.dumps(payload).encode("utf-8")

        self._log(
            f"→ 发送 {request_label}（请求体 {len(body):,} 字节），"
            f"超时 {timeout}s，最多重试 {max_retries} 次"
        )

        transient_http = {408, 409, 425, 429, 500, 502, 503, 504}
        for attempt in range(1, max_retries + 1):
            headers = llm_request_headers({"api_key": self._api_key, "base_url": self._base_url})
            headers["Connection"] = "close"
            req = urllib.request.Request(
                url,
                data=body,
                headers=headers,
                method="POST",
            )

            start = time.time()
            done = threading.Event()

            def _heartbeat():
                while not done.wait(timeout=8):
                    elapsed = int(time.time() - start)
                    remaining = max(0, timeout - elapsed)
                    self._log(
                        f"  ⏳ {request_label} 响应中… "
                        f"第 {attempt}/{max_retries} 次，已等待 {elapsed}s，剩余超时 {remaining}s"
                    )

            threading.Thread(target=_heartbeat, daemon=True).start()

            try:
                if attempt > 1:
                    self._log(f"  ↻ 重试 {request_label}：第 {attempt}/{max_retries} 次")
                with urllib.request.urlopen(req, timeout=timeout) as resp:
                    data = json.loads(resp.read().decode("utf-8"))
                    choice = (data.get("choices") or [{}])[0]
                    message = choice.get("message") or {}
                    content = message.get("content") or ""
                    finish = choice.get("finish_reason", "")
                    elapsed = int(time.time() - start)
                    if not content.strip():
                        reasoning = message.get("reasoning_content") or ""
                        self._log(
                            f"[请求错误] {request_label} 返回空内容 "
                            f"(finish_reason={finish}, reasoning={len(reasoning)} 字符)"
                        )
                        if attempt < max_retries:
                            done.set()
                            time.sleep(min(2 ** attempt, 10))
                            continue
                        return None
                    self._log(
                        f"✓ {request_label} 响应接收（耗时 {elapsed}s），"
                        f"内容长度: {len(content)} 字符"
                    )
                    return content
            except urllib.error.HTTPError as e:
                body_txt = e.read().decode("utf-8", errors="replace")
                self._log(f"[HTTP错误] {request_label} {e.code} {e.reason}: {body_txt[:300]}")
                if e.code in transient_http and attempt < max_retries:
                    done.set()
                    time.sleep(min(2 ** attempt, 12))
                    continue
                return None
            except (
                urllib.error.URLError,
                ConnectionResetError,
                RemoteDisconnected,
                TimeoutError,
                socket.timeout,
            ) as e:
                self._log(f"[请求错误] {request_label} 第 {attempt}/{max_retries} 次失败: {e}")
                if attempt < max_retries:
                    done.set()
                    time.sleep(min(2 ** attempt, 12))
                    continue
                return None
            except Exception as e:
                self._log(f"[请求错误] {request_label}: {e}")
                if attempt < max_retries:
                    done.set()
                    time.sleep(min(2 ** attempt, 12))
                    continue
                return None
            finally:
                done.set()
        return None


    def _fix_code_prefixes(self, turtle_str: str) -> str:
        """Ensure ind:code values have correct prefixes (MEAS_ for Measure, DIM_ for Dimension).

        LLMs sometimes omit these prefixes despite prompt instructions.
        This post-processing step uses rdflib to fix them reliably.
        """
        from rdflib import Graph, RDF, Literal, URIRef
        from rdflib.namespace import Namespace

        IND = Namespace("http://indicator.insightmind.com/ontology#")
        MEASURE = IND.Measure
        DIMENSION = IND.Dimension
        CODE = IND.code
        EXPRESSION = IND.expression
        NATURAL_DIM_CODE = IND.naturalDimCode
        HIST_DIM_CODE = IND.histDimCode

        g = Graph()
        try:
            g.parse(data=turtle_str, format="turtle")
        except Exception:
            return turtle_str

        # Build old→new code mapping
        code_map: dict[str, str] = {}

        for s in g.subjects(RDF.type, MEASURE):
            code = g.value(s, CODE)
            if code is None:
                continue
            old = str(code).strip()
            if old and not old.startswith("MEAS_"):
                new = "MEAS_" + old
                code_map[old] = new
                # Update triples with same-subject ind:code
                g.remove((s, CODE, None))
                g.add((s, CODE, Literal(new)))
                # Also update ind:enName if it matches the old code
                en_name = g.value(s, IND.enName)
                if en_name and str(en_name) == old:
                    g.remove((s, IND.enName, None))
                    g.add((s, IND.enName, Literal(new)))

        for s in g.subjects(RDF.type, DIMENSION):
            code = g.value(s, CODE)
            if code is None:
                continue
            old = str(code).strip()
            if old and not old.startswith("DIM_"):
                new = "DIM_" + old
                code_map[old] = new
                g.remove((s, CODE, None))
                g.add((s, CODE, Literal(new)))
                en_name = g.value(s, IND.enName)
                if en_name and str(en_name) == old:
                    g.remove((s, IND.enName, None))
                    g.add((s, IND.enName, Literal(new)))

        # Fix code references in JSON expressions (measCode, dimCode)
        if code_map:
            self._fix_expression_refs(g, code_map, EXPRESSION)
            self._fix_code_refs(g, code_map, NATURAL_DIM_CODE)
            self._fix_code_refs(g, code_map, HIST_DIM_CODE)

        return g.serialize(format="turtle")

    @staticmethod
    def _fix_expression_refs(g, code_map: dict[str, str], prop):
        """Update measCode/dimCode references inside JSON expression strings."""
        import json as _json
        from rdflib import Literal

        for s, o in g.subject_objects(predicate=prop):
            try:
                expr = _json.loads(str(o))
            except Exception:
                continue
            changed = False
            if isinstance(expr, list):
                for item in expr:
                    if isinstance(item, dict):
                        operand = item.get("operand", {})
                        if isinstance(operand, dict):
                            for key in ("measCode", "dimCode"):
                                ref = operand.get(key)
                                if ref in code_map:
                                    operand[key] = code_map[ref]
                                    changed = True
            if changed:
                g.remove((s, prop, None))
                g.add((s, prop, Literal(_json.dumps(expr, ensure_ascii=False))))

    @staticmethod
    def _fix_code_refs(g, code_map: dict[str, str], prop):
        """Update simple code references like naturalDimCode / histDimCode."""
        from rdflib import Literal
        for s, o in g.subject_objects(predicate=prop):
            old = str(o).strip()
            if old in code_map:
                g.remove((s, prop, None))
                g.add((s, prop, Literal(code_map[old])))

    def _extract_instances(self, text: str) -> str:
        """Extract Turtle instance content from LLM response."""
        # Strip markdown code blocks if present
        block_re = re.compile(r"```(?:turtle|ttl|sparql)?\s*(.*?)```", re.DOTALL | re.IGNORECASE)
        m = block_re.search(text)
        if m:
            content = m.group(1).strip()
        else:
            content = text.strip()

        # Remove any @prefix lines the LLM mistakenly added
        lines = content.splitlines()
        filtered = [l for l in lines if not l.strip().startswith("@prefix")]
        content = "\n".join(filtered).strip()

        # Remove owl:Class / owl:ObjectProperty / owl:DatatypeProperty re-definitions
        content = re.sub(
            r'(?:ind:\w+\s+a\s+owl:(?:Class|ObjectProperty|DatatypeProperty|FunctionalProperty)[^.]*\.\s*)+',
            '',
            content,
            flags=re.DOTALL,
        )
        inst_blocks = self._extract_inst_subject_blocks(content)
        if inst_blocks:
            content = "\n\n".join(inst_blocks)
        return self._sanitize_inst_tokens(content.strip())

    @staticmethod
    def _extract_inst_subject_blocks(content: str) -> list[str]:
        """Keep only generated ABox subject blocks; drop repeated ontology text."""
        blocks: list[str] = []
        current: list[str] = []
        in_block = False

        for line in content.splitlines():
            stripped = line.strip()
            if stripped.startswith("inst:"):
                if current:
                    blocks.append("\n".join(current).strip())
                current = [line]
                in_block = True
                if stripped.endswith(" .") or stripped == ".":
                    blocks.append("\n".join(current).strip())
                    current = []
                    in_block = False
                continue

            if not in_block:
                continue

            current.append(line)
            if stripped.endswith(" .") or stripped == ".":
                blocks.append("\n".join(current).strip())
                current = []
                in_block = False

        if current:
            blocks.append("\n".join(current).strip())
        return [b for b in blocks if b]

    @staticmethod
    def _sanitize_inst_tokens(content: str) -> str:
        """Make prefixed inst: URI tokens Turtle-safe without touching literals."""
        token_re = re.compile(r"\binst:[^\s;,.\]\)]+")

        def repl(match: re.Match) -> str:
            token = match.group(0)
            local = token[5:].replace("*", "star")
            safe = re.sub(r"[^A-Za-z0-9_:-]", "_", local)
            safe = re.sub(r"_+", "_", safe).strip("_")
            return "inst:" + (safe or "item")

        return token_re.sub(repl, content)

    def _validate_turtle(self, turtle_str: str) -> tuple[bool, str]:
        """Try parsing with rdflib. Returns (ok, error_msg)."""
        from rdflib import Graph
        g = Graph()
        try:
            g.parse(data=turtle_str, format="turtle")
            return True, f"{len(g)} triples"
        except Exception as e:
            return False, str(e)

    def _repair_turtle(self, turtle_str: str) -> tuple[str, bool]:
        """
        Attempt basic repairs:
        1. Ensure all required prefixes are present
        2. Truncate after last complete triple
        """
        needed = {
            "@prefix rdf:":   "@prefix rdf:   <http://www.w3.org/1999/02/22-rdf-syntax-ns#> .",
            "@prefix rdfs:":  "@prefix rdfs:  <http://www.w3.org/2000/01/rdf-schema#> .",
            "@prefix owl:":   "@prefix owl:   <http://www.w3.org/2002/07/owl#> .",
            "@prefix xsd:":   "@prefix xsd:   <http://www.w3.org/2001/XMLSchema#> .",
            "@prefix ind:":   "@prefix ind:   <http://indicator.insightmind.com/ontology#> .",
            "@prefix inst:":  "@prefix inst:  <http://indicator.insightmind.com/instance/> .",
        }
        prefix_block = ""
        for check, declaration in needed.items():
            if check not in turtle_str:
                prefix_block += declaration + "\n"
        repaired = prefix_block + turtle_str

        # Truncate after last complete triple ending with ' .\n' or ' .'
        last_dot = repaired.rfind("\n.")
        if last_dot < 0:
            last_dot = repaired.rfind(" .")
        if last_dot > 0:
            repaired = repaired[: last_dot + 2]

        ok, _ = self._validate_turtle(repaired)
        return repaired, ok

    def repair(self, turtle_str: str, errors: list) -> tuple[str, bool]:
        """
        Ask LLM to fix the given TTL based on a list of query errors.

        Parameters
        ----------
        turtle_str : current Turtle content (full file)
        errors     : list of (meas_code, dim_code, error_msg) tuples

        Returns
        -------
        (fixed_turtle_str, success)
        """
        if not errors:
            return turtle_str, True

        error_lines = []
        for meas, dim, err in errors:
            dim_part = f" + {dim}" if dim else " (无维度)"
            error_lines.append(f"- 指标 {meas}{dim_part}：{err}")
        error_block = "\n".join(error_lines)

        # ── 打印发送给 LLM 的完整错误信息 ──────────────────────────────── #
        self._log("[修复] ══ 发送给 LLM 的错误详情 ══")
        for i, (meas, dim, err) in enumerate(errors, 1):
            self._log(f"[修复]  #{i}  指标: {meas}")
            self._log(f"[修复]      维度: {dim or '(无维度)'}")
            self._log(f"[修复]      错误: {err}")

        user_msg = (
            "以下是一段已生成的业务图谱 Turtle 文件，查询服务发现以下错误。\n"
            "请仅修改 Turtle 实例数据（inst: 三元组），不得修改任何源代码、Java 类或配置文件。\n\n"
            "## 查询错误列表\n\n"
            f"{error_block}\n\n"
            "## 常见错误原因及修复规则（只能修改 TTL 实例，不能改代码）\n\n"
            "1. 维度返回空值或 -（JOIN 条件错误）：\n"
            "   - 退化维（无维度表）→ dimTypeCode=0，DimensionApp 只填 dimFactColumn，\n"
            "     不得填 dimTable / dimPrimaryKey / dimColumn\n"
            "   - 标准维有维度表 → dimTypeCode=2，dimFactTable 必须指向事实表（fact_xxx），\n"
            "     需填 dimFactColumn（事实表外键）、dimPrimaryKey（维度表主键）、\n"
            "     dimColumn（展示列）、dimTable（维度表实例）\n"
            "   - 日期维度必须用 dimTypeCode=0，不得 JOIN 日期维度表\n"
            "2. 指标查询报错（SQL 执行失败）：\n"
            "   - factColumn 必须是事实表中真实存在的列名\n"
            "   - expression 格式：[{\"operatingType\":\"operator\",\"operator\":\"sum\"}]\n\n"
            "## 当前 Turtle 内容\n\n"
            "```turtle\n"
            f"{turtle_str}\n"
            "```\n\n"
            "请先用一段简短的中文说明你发现的问题和修复思路，然后再输出修复后的完整实例部分。\n"
            "修复后的实例部分（inst: 开头的三元组，不含 @prefix 行，不含 owl:Class 等本体定义）"
            "用 ```turtle ... ``` 包裹。不要输出任何代码建议或 Java 修改说明。"
        )

        payload = {
            "model": self._model,
            "max_tokens": 16384,
            "temperature": 0.1,
            "messages": [
                {"role": "system", "content": _SYSTEM_PROMPT},
                {"role": "user",   "content": user_msg},
            ],
        }

        self._log("[修复] 正在调用 LLM 进行修复…")
        raw = self._call_api(payload)
        if raw is None:
            self._log("[修复] LLM 调用失败")
            return turtle_str, False

        # ── 打印 LLM 返回的说明文字（代码块之外的部分）──────────────────── #
        explanation = self._extract_explanation(raw)
        if explanation:
            self._log("[修复] ══ LLM 修复说明 ══")
            for line in explanation.splitlines():
                if line.strip():
                    self._log(f"[修复]  {line}")

        instances_str = self._extract_instances(raw)
        if not instances_str:
            self._log("[修复] LLM 未返回有效的 Turtle 内容")
            return turtle_str, False

        fixed_turtle = _ONTOLOGY_PREAMBLE + "\n# ═══ 数据实例 ═══\n\n" + instances_str
        ok, msg = self._validate_turtle(fixed_turtle)
        if not ok:
            self._log(f"[修复] Turtle 语法校验失败: {msg}，尝试逐块修复…")
            fixed_turtle, ok = self._repair_drop_bad_triples(fixed_turtle)

        if not ok:
            self._log("[修复] 语法修复仍失败，保留原文件")
            return turtle_str, False

        # ── 打印修复前后的变更摘要 ───────────────────────────────────────── #
        self._log_repair_diff(turtle_str, fixed_turtle)
        self._log(f"[修复] ✓ 完成，修复后 Turtle {len(fixed_turtle)} 字符")
        return fixed_turtle, True

    def _extract_explanation(self, raw: str) -> str:
        """提取 LLM 响应中代码块之外的说明文字。"""
        block_re = re.compile(r"```(?:turtle|ttl|sparql)?.*?```", re.DOTALL | re.IGNORECASE)
        explanation = block_re.sub("", raw).strip()
        # 去掉多余空行
        lines = [l for l in explanation.splitlines() if l.strip()]
        return "\n".join(lines)

    def _log_repair_diff(self, old_turtle: str, new_turtle: str) -> None:
        """对比修复前后的实例主语，输出新增/删除/修改摘要。"""
        from rdflib import Graph, Namespace
        IND  = Namespace("http://indicator.insightmind.com/ontology#")
        INST = "http://indicator.insightmind.com/instance/"

        def _subj_triples(ttl: str) -> dict:
            """返回 {subject_uri: set(predicate_object_str)}"""
            g = Graph()
            try:
                g.parse(data=ttl, format="turtle")
            except Exception:
                return {}
            result: dict = {}
            for s, p, o in g:
                key = str(s)
                if not key.startswith(INST):
                    continue
                result.setdefault(key, set()).add(f"{p.n3(g.namespace_manager)}  {o.n3(g.namespace_manager)}")
            return result

        old_map = _subj_triples(old_turtle)
        new_map = _subj_triples(new_turtle)

        added   = sorted(set(new_map) - set(old_map))
        removed = sorted(set(old_map) - set(new_map))
        changed = []
        for s in sorted(set(old_map) & set(new_map)):
            if old_map[s] != new_map[s]:
                changed.append(s)

        self._log("[修复] ══ 变更摘要 ══")
        if not added and not removed and not changed:
            self._log("[修复]  无实例级变更（三元组内容已修改但主语不变，见上方说明）")
            return

        short = lambda uri: uri.replace(INST, "inst:")
        if added:
            self._log(f"[修复]  新增实例 ({len(added)}):")
            for s in added:
                self._log(f"[修复]    + {short(s)}")
        if removed:
            self._log(f"[修复]  删除实例 ({len(removed)}):")
            for s in removed:
                self._log(f"[修复]    - {short(s)}")
        if changed:
            self._log(f"[修复]  修改实例 ({len(changed)}):")
            for s in changed:
                old_props = old_map[s]
                new_props = new_map[s]
                prop_added   = new_props - old_props
                prop_removed = old_props - new_props
                self._log(f"[修复]    ~ {short(s)}")
                for p in sorted(prop_removed):
                    self._log(f"[修复]        旧: {p}")
                for p in sorted(prop_added):
                    self._log(f"[修复]        新: {p}")

    def _repair_drop_bad_triples(self, turtle_str: str) -> tuple[str, bool]:
        """
        Last-resort repair: split into subject blocks, drop invalid ones.
        """
        lines = turtle_str.splitlines()

        # Collect prefix lines
        prefix_lines = []
        body_lines_start = 0
        for i, line in enumerate(lines):
            s = line.strip()
            if s.startswith("@prefix") or s.startswith("#") or s == "":
                prefix_lines.append(line)
                body_lines_start = i + 1
            else:
                break

        prefix_str = "\n".join(prefix_lines) + "\n"
        body_lines = lines[body_lines_start:]

        # Split body into blocks ending with ' .' or standalone '.'
        blocks: list[str] = []
        current: list[str] = []
        for line in body_lines:
            current.append(line)
            s = line.strip()
            if s.endswith(" .") or s == ".":
                blocks.append("\n".join(current))
                current = []
        if current:
            blocks.append("\n".join(current))

        # Add blocks one by one, skip those that break parsing
        good_blocks: list[str] = []
        for block in blocks:
            candidate = prefix_str + "\n".join(good_blocks) + "\n" + block
            ok, _ = self._validate_turtle(candidate)
            if ok:
                good_blocks.append(block)
            else:
                self._log(f"[修复] 跳过无效块: {block[:60].strip()}…")

        result = prefix_str + "\n".join(good_blocks)
        ok, msg = self._validate_turtle(result)
        if ok:
            self._log(f"[修复] 成功，保留 {len(good_blocks)}/{len(blocks)} 个块")
        return result, ok
