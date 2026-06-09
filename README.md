# InsightMind

## 你的数据报表，真的把数据用起来了吗？

我们见过太多公司的数据大屏：销售额、订单量、同比增长率一应俱全。

但当经营指标出现明显波动时，看板只能告诉你"变了多少"，不能告诉你"**为什么变**"。

更隐蔽的是——在看似平稳的指标下，A 城市暴跌、B 城市暴涨、高毛利产品被低毛利产品替代……这些问题在看板上互相抵消，管理者看到的只是"一切正常"。

**看板呈现结果，不呈现原因。** 多数企业的数据报表止步于"销售额 3.2 亿""订单量 +5%"。业务指标波动时，看板能告诉你变了多少，却不能告诉你为什么变。

**平稳指标下的盲区。** 总销售额持平，可能掩盖了 A 城市暴跌、B 城市暴涨、退款率翻倍、高毛利产品占比下滑——这些问题在看板上往往互相抵消，看起来一切正常。

**看板的深度，取决于做看板的人。** 看板能拆到什么维度，受限于制作人对业务的理解深度、项目排期和精力投入。一个销售看板上线后，很少有人持续迭代它的分析路径。

---

## 看看怎么解决

InsightMind 把数据库里的表、字段、关系自动变成**业务人员看得懂的指标和维度**，然后让任何人都能用自然语言问出"为什么"。

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

InsightMind 是一个**面向数据分析和指标治理的智能语义层工作空间**，由两个服务组成：

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

---

## 一键安装与启动

### 环境要求

- macOS（服务管理脚本基于 `launchctl`）
- Python 3.9+ 和 JDK 8/11
- Maven 3.6+
- MySQL 5.7/8.0（用于 DA 元数据库）
- 可选：Redis、OpenAI 兼容 LLM 网关

### 给 Claude Code 的一键提示词

把下面这段发给 Claude Code，让它自动完成安装和启动：

```text
在 InsightMind 仓库根目录完成本地安装和启动。

安全要求：
- 不要读取、打印或提交真实数据库密码、API token、LLM key。
- 如果需要数据库密码或 LLM key，要求我通过环境变量或命令行参数提供。

步骤：
1. 确认当前目录是 InsightMind 仓库根目录。
2. 安装 AD 依赖：
   cd apps/ad
   python3 -m venv venv
   source venv/bin/activate
   pip install -r requirements.txt
3. 构建 DA：
   cd ../da
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
  cd apps/ad
  python3 -m venv venv && source venv/bin/activate && pip install -r requirements.txt
  cd ../da
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

1. apps/ad 下创建 Python venv，安装 requirements.txt 依赖。
2. apps/da 下执行 mvn -DskipTests package。
3. 回到仓库根目录，执行 ./scripts/insightmind.sh start 启动全部服务。
4. 验证服务状态并打开浏览器确认 AD:8080 和 DA:8091 可访问。

不要读取、提交或覆盖任何凭据文件。凭据类信息通过环境变量传入。
```

### 手工安装

```bash
cd /path/to/InsightMind

# 安装 AD
cd apps/ad
python3 -m venv venv
source venv/bin/activate
pip install -r requirements.txt

# 构建 DA
cd ../da
mvn -DskipTests package

# 启动
cd ../..
./scripts/insightmind.sh start
```

### 服务管理

```bash
./scripts/insightmind.sh start          # 启动全部服务
./scripts/insightmind.sh stop           # 停止全部服务
./scripts/insightmind.sh restart        # 重启全部服务
./scripts/insightmind.sh status         # 查看运行状态
./scripts/insightmind.sh restart ad     # 仅重启 AD
./scripts/insightmind.sh restart da     # 仅重启 DA
```

日志位置：

```
logs/ad.log
logs/da.log
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
