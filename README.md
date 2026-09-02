# InsightMind

> **面向 Codex 时代的数据分析平台生成器：用 Codex 快速完成平台搭建和个性化开发，用知识图谱与指标语义层保证分析结果准确、稳定、可复用。**

**让 Codex 搭平台，让语义层守口径，让数据自己说话。**

InsightMind 不是一个让大模型直接连接数据库、临时生成几张图表的聊天工具。它连接企业现有数据源，自动发现数据资产和关系，构建经过真实查询验证的指标语义层，再让 Codex、Claude Code 等 Agent 在这套可信的数据语言之上生成和迭代看板、分析应用与监控预警。

这样既能获得 AI 辅助开发的速度，也能保留数据平台必须具备的统一口径、稳定查询、复用能力和治理边界。

[阅读完整思考：Codex 时代，数据分析平台为什么不能只靠大模型画看板](docs/codex-era-data-analysis-platform.md)

## Codex 时代，数据分析的瓶颈变了

Codex 正在显著降低页面、接口、SQL 和图表的开发成本。过去需要排期数周的个性化分析需求，现在可以更快地实现。但数据分析最难的部分并没有消失：销售额究竟采用哪个口径、订单与客户应该如何关联、某个指标能否按地区下钻、生成的 SQL 是否真的返回正确结果。

如果把原始表结构直接交给大模型，这些问题只会从人工开发阶段转移到模型的上下文中。一次演示可能很快，长期运行却容易遇到口径漂移、错误 JOIN、结果不可复现和分析资产无法沉淀等问题。

| 直接让大模型分析数据库 | InsightMind 的方式 |
|---|---|
| 根据表名和字段名临时猜测业务含义 | 将表、字段和业务口径组织为持久化知识图谱 |
| 每轮对话重新理解指标 | 统一定义指标、维度、事实表和拆解关系 |
| 由模型同时决定 JOIN、公式和过滤条件 | 由语义层约束口径，DA 统一生成和执行 SQL |
| 生成结果后缺少系统性验证 | 用真实查询验证“指标 × 维度”组合并反馈修复 |
| 交付一次性答案或图表 | 沉淀可复用的组件、看板、下钻路径和预警规则 |

InsightMind 选择让不同部分各自做擅长的事：

- **Codex / Agent 负责搭建和变化**：安装配置、行业适配、页面与分析流程生成、持续迭代。
- **知识图谱与指标语义层负责口径**：说明指标是什么、如何计算、可以按哪些维度分析。
- **DA 查询引擎负责稳定执行**：根据确定的指标、维度和过滤条件规划查询并生成 SQL。
- **数据库负责事实，验证流程负责兜底**：最终结果来自真实数据，关键组合通过真实查询校验。

目标不是生成一次“看起来不错”的看板，而是把每一次分析需求沉淀为可持续运行、可解释、可治理的数据产品。

## 你的数据报表，真的把数据用起来了吗？

太多公司的数据大屏：销售额、订单量、同比增长率一应俱全。

但当经营指标出现明显波动时，看板只能告诉你"变了多少"，不能告诉你"**为什么变**"。

更隐蔽的是——在看似平稳的指标下，A 城市暴跌、B 城市暴涨、高毛利产品被低毛利产品替代……这些问题在看板上互相抵消，管理者看到的只是"一切正常"。

**看板呈现结果，不呈现原因。** 多数企业的数据报表止步于"销售额 3.2 亿""订单量 +5%"。业务指标波动时，看板能告诉你变了多少，却不能告诉你为什么变。

**平稳指标下的盲区。** 总销售额持平，可能掩盖了 A 城市暴跌、B 城市暴涨、退款率翻倍、高毛利产品占比下滑——这些问题在看板上往往互相抵消，看起来一切正常。

**看板的深度，取决于做看板的人。** 看板能拆到什么维度，受限于制作人对业务的理解深度、项目排期和精力投入。一个销售看板上线后，很少有人持续迭代它的分析路径。

---

## 看看怎么解决

企业的数据散落在 MySQL、SQL Server、Oracle 等不同系统中——有些在交易库，有些在数仓，有些在报表库。每个系统都有自己的表名、字段名、口径定义，彼此之间没有统一的语义。

InsightMind 的核心思路是**用知识图谱把分散在各系统的数据统一组织起来**，形成一套拥有完整清晰定义的指标语义层。不是再做一个看板，而是为数据分析和 LLM 应用铺设一套能快速应对数据变化的基建。

为什么这很重要？因为 LLM 在数据分析领域能不能正常工作，关键不仅在于模型本身有多强—更在于它拿到的 context 够不够清晰。表名 `ods_sales_fact_v2` 对 LLM 来说毫无意义，但"销售额，按日期、渠道、区域可拆解，口径为 `SUM(price × quantity)`"才是它能理解的语言。**InsightMind 做的，就是把前者自动翻译成后者。**

不是再做一个看板。是让数据自己会说话。

### 了解核心能力

- **自动盘点数据资产。** 连上数据库，自动解析所有表、字段、注释、主外键——不用翻文档，不用问 DBA。
- **发现你不知道的关系。** 显式外键只是冰山一角。InsightMind 从字段命名、注释引用、枚举值对齐、包含依赖和语义相似度中，找到那些"数据库里没写但业务上存在"的关联。
- **从表结构到业务指标。** 不是只告诉你"有张 sales 表"，而是告诉你"销售额 = 单价 × 数量，可以按日期、渠道、区域拆解"——这个转化过程可以用参考指标平台做确定性导出，也可以让 LLM 根据元数据自动推断。
- **谁都能问数据。** 输入"上周各渠道退款率"，NLQ 引擎自动匹配指标、维度、过滤条件，生成 SQL 并返回结果。不用写 SQL，不用等分析师排期。
- **指标口径自动校验。** 生成业务图谱后，自动用真实查询验证每个"指标 × 维度"组合能否正常返回数据。失败就自动修复，确保口径可用。
- **数据质量可视化。** 外键完整性、孤立指标、不可查询维度、JOIN 路径、变更影响——质量问题一目了然。
- **图表和仪表盘。** 支持 Ad-Hoc 查询、拖拽式仪表盘预览、多维下钻和统计概览。

---

## 怎么工作的

InsightMind 是一个**面向数据分析和指标治理的智能语义层工作空间**。它以知识图谱为核心，将不同数据源的表结构、字段关系和业务口径统一建模为可查询、可推理的语义网络——这是 LLM 能在数据分析领域正常工作的基础。

两个服务各司其职：

| 服务 | 角色 | 技术栈 |
|------|------|--------|
| **AD**（知识图谱构建器） | 连接数据库 → 抽取元数据 → 识别关系 → 构建知识图谱 → 生成业务指标模型 | Python / FastAPI / RDFlib / LLM |
| **DA**（指标服务） | 加载业务图谱 → 指标元数据管理 → SQL 生成 → 查询 API → 仪表盘 | Spring Boot / Jena / MyBatis-Plus |

两者通过一个 Turtle 图谱文件连接：

```
apps/ad/output/business_kg/indicator-data.ttl
```

```text
你的数据库
   |
   v
AD 自动解析表结构、采样数据、发现关系
   |
   v
数据源知识图谱（表、字段、外键、隐式关系）
   |
   +--> ETL 路径：从参考指标平台直接导出指标/维度
   |
   +--> LLM 路径：让大模型根据元数据推断业务语义
   |
   v
业务知识图谱（指标、维度、事实表、口径关系）
   |
   v
DA 加载图谱，提供查询 API / SQL 生成 / 仪表盘
   |
   v
你问："上周退款率最高的三个渠道是哪些？"
   → InsightMind 匹配指标、维度、过滤条件 → 生成 SQL → 返回结果
```

默认本地地址：

```
AD: http://localhost:8080/
DA: http://localhost:8091/
```

### MCP Server

仓库内置了一个只读 MCP Gateway，把 AD 的知识图谱、NLQ、语义查询能力和 DA 的指标推理、DataGPT、DSL 查询能力暴露为 21 个 MCP tools，可供 Codex、Claude Code 或其他 MCP 客户端使用。

```bash
./scripts/insightmind-mcp.sh setup
./scripts/insightmind-mcp.sh start
./scripts/insightmind-mcp.sh status
```

默认 Streamable HTTP 地址为 `http://localhost:8092/mcp`；本地客户端也可通过 `./scripts/insightmind-mcp.sh stdio` 接入。完整的工具清单、客户端配置和安全边界见 [`docs/agent_mcp_gateway.md`](docs/agent_mcp_gateway.md)。

---

## 一键安装与启动

### 环境要求

- macOS / Linux / Windows
- Python 3.9+ 和 JDK 8/11
- Maven 3.6+
- MySQL 5.7/8.0（用于 DA 元数据库）
- 可选：Redis、OpenAI 兼容 LLM 网关

> `scripts/insightmind.sh` 在 macOS 上使用 `launchctl` 管理后台服务。Linux 和 Windows 用户请参考下方「跨平台启动」方式。

### 给 Claude Code 的一键提示词

把下面这段发给 Claude Code，让它自动完成安装和启动：

```text
在 InsightMind 仓库根目录完成本地安装和启动。

安全要求：
- 不要读取、打印或提交真实数据库密码、API token、LLM key。
- 如果需要数据库密码或 LLM key，要求我通过环境变量或命令行参数提供。

步骤：
1. 确认当前目录是 InsightMind 仓库根目录。
2. 安装 AD 快速部署依赖：
   ./scripts/insightmind.sh setup
3. 构建 DA：
   cd apps/da
   mvn -DskipTests package
4. 回到根目录启动：
   cd ../..
   ./scripts/insightmind.sh start
5. 验证：
   ./scripts/insightmind.sh status
   打开 http://localhost:8080/ 和 http://localhost:8091/
```

### 给 OpenClaw 的一键提示词

```text
Work in the InsightMind repository root to install and start everything locally.

Security: Do not read, print, or commit real database passwords, API tokens,
LLM keys, or OAuth secrets. If credentials are needed, ask the user to provide
them via environment variables or command-line arguments.

Install / build:
  ./scripts/insightmind.sh setup
  cd apps/da
  mvn -DskipTests package

Run:
  cd ../..
  ./scripts/insightmind.sh start

Verify:
  ./scripts/insightmind.sh status
  Open http://localhost:8080/ and http://localhost:8091/
```

### 给 Codex 的一键提示词

```text
请在 InsightMind 仓库根目录执行本地安装：

1. 在仓库根目录执行 ./scripts/insightmind.sh setup 安装 AD 快速部署依赖。
2. apps/da 下执行 mvn -DskipTests package。
3. 回到仓库根目录，执行 ./scripts/insightmind.sh start 启动全部服务。
4. 验证服务状态并打开浏览器确认 AD:8080 和 DA:8091 可访问。

不要读取、提交或覆盖任何凭据文件。凭据类信息通过环境变量传入。
```

### 手工安装

```bash
cd /path/to/InsightMind

# 安装 AD 快速部署依赖（core）
./scripts/insightmind.sh setup

# 构建 DA
cd apps/da
mvn -DskipTests package

# 启动（macOS）
cd ../..
./scripts/insightmind.sh start

# 启动（Linux / Windows：开两个终端分别启动，见下方「跨平台启动」）
```

### 服务管理（macOS）

```bash
./scripts/insightmind.sh setup          # 安装 AD core 依赖，适合快速部署
./scripts/insightmind.sh setup full     # 安装 AD 全量依赖，适合开发/完整分析
./scripts/insightmind.sh start          # 启动全部服务
./scripts/insightmind.sh stop           # 停止全部服务
./scripts/insightmind.sh restart        # 重启全部服务
./scripts/insightmind.sh status         # 查看运行状态
./scripts/insightmind.sh restart ad     # 仅重启 AD
./scripts/insightmind.sh restart da     # 仅重启 DA
```

### AD 依赖 Profile

AD 默认安装 `apps/ad/requirements-core.txt`，用于快速启动 Web UI、业务图谱、
Ad-Hoc、Dashboard 和默认 demo。按需追加：

```bash
cd apps/ad
source venv/bin/activate
pip install -r requirements-analysis.txt   # 统计分析、完整 6-Part Insight、聚类/PCA
pip install -r requirements-db-extra.txt   # SQL Server / Oracle / MySQL 兼容协议
pip install -r requirements-dev.txt        # pytest 等开发测试工具
pip install -r requirements-full.txt       # 全量依赖
```

默认安装不包含 `sentence-transformers`、`transformers` 或 `torch`，也不会下载本地
embedding 模型。AI 语义关系发现为可选功能，仅在用户主动开启时调用
已配置的 OpenAI-compatible 大模型；未配置时会安全跳过。

### 跨平台启动（Linux / Windows）

不使用 `insightmind.sh` 时，直接启动两个服务即可：

```bash
# 终端 1：启动 AD（端口 8080）
cd apps/ad
source venv/bin/activate      # Windows: venv\Scripts\activate
python web_app.py

# 终端 2：启动 DA（端口 8091）
cd apps/da
java -jar target/da-indicator-0.0.1-SNAPSHOT.jar \
  --spring.profiles.active=dev \
  --server.port=8091 \
  --indicator.graph.data-path=../ad/output/business_kg/indicator-data.ttl
```

日志位置：

```
logs/ad.log
logs/da.log
```

---

## 默认演示环境：HR 人力资本分析

InsightMind 内置一套可直接恢复的 HR 演示案例。组织、员工、岗位、薪酬和任职历史均由程序确定性生成，不包含真实业务数据。仓库已经内置：

- 数据库脚本：`apps/ad/demo_hr_data.py`、`apps/ad/hr_analytics_views.sql`、`apps/da/schema.sql`
- 数据源知识图谱：`demo/default/ad/output/kg_20260901_003.ttl`
- 业务知识图谱：`demo/default/ad/output/business_kg/indicator-data.ttl`
- 已保存 Ad-Hoc 组件：`demo/default/ad/output/adhoc/*.json`
- 已保存 dashboard：`demo/default/ad/output/dashboards/*.json`

别人下载项目后，只要完成依赖安装、初始化 demo 数据库并启动服务，就可以直接看到：

- **数据图谱**：HR 组织、员工、岗位、地域和历史任职关系。
- **业务图谱**：预置 HR 指标、维度、事实视图和指标应用关系。
- **Ad-Hoc 组件**：人力资本规模、薪酬、组织层级、司龄和流动分析组件。
- **Dashboard**：组织人才全景、人才活力脉搏两张完整看板。

如果只启动 AD、不初始化数据库，也能看到图谱和 dashboard 配置；但 dashboard 要真正查询出数据，需要先初始化本地 MySQL 演示库。

AD 启动时会在运行目录缺失这些文件时自动恢复默认案例。也可以手动恢复：

```bash
./scripts/init-demo-assets.sh
```

### 一键初始化本地 demo 数据库

默认使用本地 MySQL `root/root`，创建并写入：

- `HRRDB`：107 名完全合成员工及其组织、岗位、薪酬、地域和任职历史。
- `indbtest`：DA 元数据库。

```bash
./scripts/init-demo-db.sh
```

如本机 MySQL 账号不同，通过环境变量覆盖：

```bash
MYSQL_USER=root MYSQL_PASSWORD=your_password ./scripts/init-demo-db.sh
```

> `init-demo-db.sh` 会重建 `HRRDB` 和 `indbtest` 演示库；不要把它指向已有生产或重要数据库。使用 `HR_DEMO_DB`、`DA_DB` 可改为其他专用本地库名。

### 启动并打开两张演示看板

完整 demo 启动流程：

```bash
./scripts/insightmind.sh setup full
(cd apps/da && mvn -DskipTests package)
./scripts/init-demo-db.sh
./scripts/init-demo-assets.sh
./scripts/insightmind.sh restart
./scripts/verify-hr-demo.sh
```

打开：

```text
http://localhost:8080/dashboard/view/dash_hr_human_capital_panorama
http://localhost:8080/dashboard/view/dash_hr_talent_vitality_pulse
```

这时可以演示人力规模、薪酬、组织层级、司龄、组织健康和内部流动，并从指标卡、指标单元格和图表进行明细钻取、业务解释和维度下钻。看板查询通过 DA 执行，业务口径来自随仓库发布的知识图谱。

首次显示两个看板不依赖 LLM。需要从 HR 数据库重新生成数据图谱和业务知识图谱时，再配置可用的大模型环境变量并运行：

```bash
cd apps/ad
source venv/bin/activate
python generate_hr_graphs.py --config config.yaml
```

---

## 配置

### AD 配置

复制示例配置并填入本地数据库信息：

```bash
cp apps/ad/config.yaml apps/ad/config.local.yaml
```

LLM 密钥通过环境变量提供：

```bash
export LLM_BASE_URL="https://your-openai-compatible-gateway"
export LLM_API_KEY="YOUR_API_KEY"
export LLM_MODEL_NAME="YOUR_MODEL_NAME"
```

### DA 配置

初始化元数据数据库：

```bash
mysql -u YOUR_DB_USER -p -e \
  "CREATE DATABASE IF NOT EXISTS indbtest DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"

mysql -u YOUR_DB_USER -p indbtest < apps/da/schema.sql
```

启动时通过命令行传入真实密码（不要写入 YAML 文件）：

```bash
java -jar apps/da/target/da-indicator-0.0.1-SNAPSHOT.jar \
  --spring.profiles.active=dev \
  --spring.datasource.dynamic.datasource.mysql.username=YOUR_DB_USER \
  --spring.datasource.dynamic.datasource.mysql.password=YOUR_DB_PASSWORD \
  --indicator.graph.data-path=/path/to/InsightMind/apps/ad/output/business_kg/indicator-data.ttl
```

---

## 开发命令

### AD

```bash
cd apps/ad
source venv/bin/activate
python web_app.py                   # 单独启动 AD
PYTHONPATH=. pytest                 # 运行全部测试
PYTHONPATH=. pytest tests/test_ad_semantic_api.py  # 单个测试文件
```

### DA

```bash
cd apps/da
mvn -DskipTests package             # 构建
mvn test -DskipTests=false          # 运行全部测试
mvn test -DskipTests=false -Dtest=ClassName  # 单个测试类
mvn spring-boot:run -Dspring-boot.run.profiles=dev  # 启动
```

---

## 项目结构

```text
InsightMind/
  apps/
    ad/      # Python/FastAPI 知识图谱构建器 & Web UI
    da/      # Spring Boot 指标服务 & SQL 生成引擎
  logs/      # 本地运行日志（git 忽略）
  scripts/
    insightmind.sh
```

更多实现细节见各子项目文档：

- `apps/ad/README.md` / `apps/ad/AGENTS.md`
- `apps/da/README.md` / `apps/da/CLAUDE.md`

---

## 安全提示

- **不要提交**真实数据库凭据、LLM API key、OAuth secret 或 token。
- AD 真实配置写入 `config.local.yaml`（已 git 忽略），LLM 密钥通过环境变量传入。
- DA 的 `application-*.yml` 仅作开发环境参考，真实密码通过命令行 `--spring.datasource...` 传入。
- 日志、虚拟环境、Maven target 和运行时输出不进 Git。
