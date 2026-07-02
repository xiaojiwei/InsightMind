# Ad-Hoc 热力图浏览器验收用例

## 用例信息

- 用例 ID：ADHOC-HEATMAP-001
- 用例名称：Ad-Hoc 热力图组件配置、运行与保存组件验证
- 测试对象：Ad-Hoc 图表类型 `heatmap`
- 测试组件：`demo_web_sales_month_item_heatmap`
- 测试页面：`http://127.0.0.1:8080/`
- 测试日期：2026-07-01

## 前置条件

- AD 服务运行在 `http://127.0.0.1:8080`
- DA 服务运行在 `http://127.0.0.1:8091`
- 已存在 Ad-Hoc 组件配置：
  - ID：`demo_web_sales_month_item_heatmap`
  - 名称：`月网络销售商品热力透视`
  - 图表类型：`heatmap`
  - 指标：`ad.web_sales_amount`
  - 行维度：`ad.date_month`
  - 列维度：`ad.web_sales_item`

## 测试步骤与预期

| 步骤 | 操作 | 预期结果 |
| --- | --- | --- |
| 1 | 打开首页并进入 `Ad-Hoc` 页签 | 页面显示 Ad-Hoc 编辑器、语义字段、已保存组件列表 |
| 2 | 在已保存组件中打开 `月网络销售商品热力透视` | 组件名称回填，图表类型为 `热力图`，指标和维度区域显示对应字段 |
| 3 | 点击 `运行` | 预览区生成热力图，不出现错误提示 |
| 4 | 检查热力图结构 | 行标题为 `月`，列标题为 `网络销售商品`；至少有 7 行月份、12 列以上商品 |
| 5 | 检查热力图数据 | 单元格存在非零值，颜色按相对高低显示，底部显示低值到高值图例 |
| 6 | 检查状态栏 | 状态栏显示返回行数和查询耗时 |
| 7 | 点击 `保存组件` | 页面提示保存成功，组件仍可通过 `/api/adhoc/v1/demo_web_sales_month_item_heatmap` 读取 |

## 通过标准

- `heatmap` 可以作为 Ad-Hoc 图表类型被选择和保存。
- 组件配置来自 Ad-Hoc 配置，不依赖 Dashboard 写死逻辑。
- 浏览器运行后热力图至少生成：
  - 7 行月份
  - 12 列以上商品
  - 至少 1 个非零单元格
- 无前端错误弹窗，无 `#adhoc-preview .alert-danger`。

## 实际验证记录

- 步骤 1：通过。已打开首页并进入 `Ad-Hoc` 页签；页面显示 Ad-Hoc 编辑器、已保存组件列表，并且图表类型下拉包含 `热力图`。
- 步骤 2：通过。打开 `demo_web_sales_month_item_heatmap` 后，组件名称为 `月网络销售商品热力透视`，图表类型为 `heatmap`，指标为 `网络销售金额`，维度为 `月`、`网络销售商品`。
- 步骤 3：通过。点击 `运行` 后生成热力图；预览区标题为 `月 × 网络销售商品 热力图`，无错误提示。
- 步骤 4：通过。热力图结构为 7 行月份、16 列商品；行头为 `月`，首个列头为 `网络销售商品 37`。
- 步骤 5：通过。热力图共有 112 个单元格，其中 83 个非零单元格，最大值 9,779；颜色包含绿色高值、红色低值和空值背景，并显示低值到高值图例。
- 步骤 6：通过。状态栏显示 `1000 行 · 509 ms`。
- 步骤 7：通过。点击 `保存组件` 后接口校验成功：`/api/adhoc/v1/demo_web_sales_month_item_heatmap` 返回 `chartType=heatmap`，指标为 `ad.web_sales_amount`，维度为 `ad.date_month`、`ad.web_sales_item`。

结论：通过。

## Dashboard 回归验证记录

- 验证页面：`http://127.0.0.1:8080/dashboard/view/dash_executive_sales_report_demo`
- 验证日期：2026-07-01
- 验证目标：确认 Ad-Hoc 保存的热力图组件可以在 Dashboard 中复用，并且预警规则提示不依赖 Dashboard 写死逻辑。

| 组件 | 验证结果 |
| --- | --- |
| `全国·网络销售经营分析报告` | 通过。报告式经营分析组件正常渲染，无错误提示。 |
| `月网络销售商品热力透视` | 通过。热力图正常渲染，行标签为 `2000-01` 至 `2000-07`，共 7 行、16 列，无加载残留和错误提示。 |
| `周网络销售商品预警热力图` | 通过。热力图正常渲染，行标签为 `200030`，共 1 行、16 列；顶部显示 `9 个智能预警`，异常单元格 3 个。 |

周预警热力图悬停提示验证：

- 商品 12：命中 `固定阈值｜严重`、`自动统计异常｜异常`。
- 商品 14：命中 `固定阈值｜异常`、`环比波动｜严重`、`业务规则｜关注`。
- 商品 8：命中 `固定阈值｜严重`、`自动统计异常｜异常`、`环比波动｜异常`、`同比波动｜异常`。

结论：Dashboard 可以读取 Ad-Hoc 热力图配置并展示监控预警规则命中结果，预警颜色、边框和悬停说明均来自规则结果。

## Dashboard 组件 Ad-Hoc 化回归记录

- 验证页面：`http://127.0.0.1:8080/dashboard/view/dash_executive_sales_report_demo`
- 验证日期：2026-07-02
- 验证目标：报告式经营分析演示看板的每个主要组件都必须在 Ad-Hoc 中有独立定义，Dashboard 只负责布局和引用。

已保存的 Ad-Hoc 组件：

| Ad-Hoc ID | 组件名称 | 类型 |
| --- | --- | --- |
| `demo_exec_sales_total_kpi` | 经营报告｜KPI｜累计网络销售金额 | KPI |
| `demo_exec_profit_total_kpi` | 经营报告｜KPI｜累计网络销售利润 | KPI |
| `demo_exec_sales_month_line` | 经营报告｜月度销售趋势 | 折线图 |
| `demo_exec_sales_month_donut` | 经营报告｜月度销售结构 | 环图 |
| `demo_exec_product_sales_bar` | 经营报告｜商品销售贡献 Top | 柱状图 |
| `demo_exec_profit_attention_bar` | 经营报告｜利润关注商品 | 柱状图 |
| `demo_exec_month_item_pivot` | 经营报告｜透视表｜月商品销售矩阵 | 透视表 |
| `demo_exec_kg_attribution_dashboard` | 经营报告｜知识图谱异常归因 | 知识图谱归因 |
| `demo_web_sales_month_item_heatmap` | 月网络销售商品热力透视 | 热力图 |
| `demo_web_sales_week_item_heatmap_alerts` | 周网络销售商品预警热力图 | 预警热力图 |

浏览器验证结果：

- Dashboard 返回 10 个 widget，全部通过 `adhocId` 引用 Ad-Hoc 配置。
- KPI、折线图、环图、柱状图、透视表、热力图均无错误提示。
- 月热力图渲染为 7 行、16 列。
- 周预警热力图渲染为 1 行、16 列，3 个异常单元格，顶部显示 `9 个智能预警`。
- 知识图谱归因组件为看板模式，能从当前 Dashboard 的普通图表预警徽标中汇总出 5 条规则链路。

结论：通过。Dashboard 不再依赖一个大块报告式组件承载所有内容，主要分析块都可以在 Ad-Hoc 中单独配置、单独保存、再组合到 Dashboard。

## 透视表热力图效果回归记录

- 验证页面：`http://127.0.0.1:8080/dashboard/view/dash_executive_sales_report_demo`
- 验证日期：2026-07-02
- 验证目标：Ad-Hoc 透视表支持保存“热力图效果”配置，并在 Dashboard 中按单元格数值深浅渲染底色。

配置验证：

- 组件：`demo_exec_month_item_pivot`
- 配置字段：`view.encoding.pivotHeatmapEnabled=true`
- 列轴配置：`view.encoding.pivotColumns=["ad.web_sales_item"]`

浏览器验证结果：

- `月商品销售透视表` 正常渲染，无错误提示。
- 透视表共 50 个数据单元格，50 个单元格均带 `pivot-heatmap-cell`。
- 单元格背景按数值产生 45 种不同深浅的绿色系底色。
- 预警/单据异常样式优先级高于热力底色，不被覆盖。

结论：通过。透视表热力图效果来自 Ad-Hoc 配置，可保存并在 Dashboard 复用。
