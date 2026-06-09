# InsightMind

InsightMind 是一个面向数据分析和指标治理的智能语义层工作空间。它把两个服务整合在同一个仓库中：

- `apps/ad`：AD 知识图谱构建器，负责从关系型数据库抽取元数据、识别表关系、构建数据源知识图谱，并生成 DA 可消费的业务知识图谱。
- `apps/da`：DA 指标服务，负责指标/维度元数据管理、基于图谱的指标查询、SQL 生成、仪表盘、监控和分析服务。

两者通过一个 Turtle 图谱文件集成：

```text
apps/ad/output/business_kg/indicator-data.ttl
```

AD 生成该文件，DA 通过 Jena `GraphStore` 热加载该文件，并将指标、维度、事实表、日期维度和口径关系转化为可查询、可生成 SQL 的语义模型。

## 业务能力

InsightMind 的目标不是只展示数据库结构，而是把数据资产变成可以被业务、分析师和智能体共同理解的指标语义层。

- 数据资产盘点：连接 MySQL、SQL Server、Oracle、SQLite 等关系型数据库，解析库、表、字段、注释、样本值和主外键。
- 关系发现：结合显式外键、字段命名、注释引用、包含依赖、枚举值对齐和语义相似度，识别潜在 JOIN 路径。
- 知识图谱构建：生成 RDF/OWL 数据源知识图谱，支持 Turtle/JSON-LD 导出、SPARQL 查询和图谱浏览。
- 指标语义建模：生成或导入指标、维度、事实表、维表、日期维度、指标应用和维度应用关系。
- 指标平台迁移：有参考指标平台数据库时，可通过确定性 ETL 方式导出业务知识图谱，减少 LLM 不确定性。
- LLM 业务建模：无参考库时，可使用 OpenAI 兼容 LLM 根据元数据摘要推断指标和维度，并自动修复不合法 Turtle。
- Ad-Hoc 查询：基于业务知识图谱选择指标、维度、过滤条件，生成可执行 SQL，支持公共日期维度和跨事实表共享维度。
- 自然语言查询：NLQ 服务将自然语言问题映射到候选指标、维度、过滤器和查询计划。
- 仪表盘预览：提供本地 Dashboard 预览、查询结果展示和分析入口。
- 数据质量辅助：检查外键完整性、孤立指标、不可查询维度、JOIN 路径和变更影响。
- DA 指标服务：提供指标元数据 API、维度 SQL 生成、数据源查询、监控、决策树、血缘和 AI/DataGPT 相关服务。

## 架构概览

```text
Relational DB
   |
   v
AD schema parser / sampler / relation detectors
   |
   v
Data-source KG: apps/ad/output/kg.ttl
   |
   +--> Business KG ETL from reference indicator DB
   |
   +--> Business KG LLM generation and repair
   |
   v
Business KG: apps/ad/output/business_kg/indicator-data.ttl
   |
   v
DA GraphStore + SPARQL services
   |
   v
Indicator query APIs / SQL generation / dashboards / monitoring
```

默认本地服务地址：

```text
AD: http://localhost:8080/
DA: http://localhost:8091/
```

## 一键安装与启动

### 给 OpenClaw / Claude Code / Codex 的一键提示词

把下面这段发给 OpenClaw、Claude Code 或 Codex，即可让智能体在当前机器上完成依赖安装、构建和启动：

```text
请在 InsightMind 仓库根目录执行本地安装和启动。

安全要求：
- 不要读取、打印、提交真实数据库密码、API token、LLM key 或 OAuth secret。
- 不要提交 apps/ad/config.local.yaml、apps/da/src/main/resources/application*.yml、logs、venv、target。
- 如果需要真实数据库密码或 LLM key，要求用户通过环境变量或命令行参数提供。

安装：
1. 确认当前目录是 InsightMind 仓库根目录。
2. 为 AD 创建虚拟环境并安装依赖：
   cd apps/ad
   python3 -m venv venv
   source venv/bin/activate
   pip install -r requirements.txt
3. 构建 DA：
   cd ../da
   mvn -DskipTests package
4. 回到仓库根目录启动服务：
   cd ../..
   ./scripts/insightmind.sh start
5. 验证：
   ./scripts/insightmind.sh status
   打开 http://localhost:8080/ 和 http://localhost:8091/
```

### 手工一键命令

从仓库根目录执行：

```bash
cd /path/to/InsightMind

cd apps/ad
python3 -m venv venv
source venv/bin/activate
pip install -r requirements.txt

cd ../da
mvn -DskipTests package

cd ../..
./scripts/insightmind.sh start
```

只启动、停止或查看状态：

```bash
./scripts/insightmind.sh start
./scripts/insightmind.sh stop
./scripts/insightmind.sh restart
./scripts/insightmind.sh status
```

只管理单个服务：

```bash
./scripts/insightmind.sh restart ad
./scripts/insightmind.sh restart da
```

日志位置：

```text
logs/ad.log
logs/da.log
```

## 环境要求

- macOS。本仓库的 `scripts/insightmind.sh` 使用 `launchctl` 管理本地服务。
- Python 3.9+，用于 AD/FastAPI 服务。
- JDK 8 或兼容 JDK，JDK 11 也可用于当前本地脚本。
- Maven 3.6+，用于构建 DA。
- MySQL 5.7/8.0 兼容数据库，用于 DA 元数据库。
- 可选：Redis，用于 DA 中依赖缓存的能力。
- 可选：OpenAI 兼容 LLM 网关，用于 NLQ、翻译和无参考库时的业务知识图谱生成。

脚本支持用环境变量覆盖运行时：

```bash
export INSIGHTMIND_AD_PYTHON=/path/to/python
export INSIGHTMIND_JAVA=/path/to/java
export INSIGHTMIND_AD_PORT=8080
export INSIGHTMIND_DA_PORT=8091
```

## 配置说明

AD 默认读取示例配置：

```text
apps/ad/config.yaml
```

真实本地配置请复制到未跟踪文件：

```bash
cp apps/ad/config.yaml apps/ad/config.local.yaml
```

LLM 配置建议通过环境变量提供：

```bash
export LLM_BASE_URL="https://your-openai-compatible-gateway"
export LLM_API_KEY="YOUR_API_KEY"
export LLM_MODEL_NAME="YOUR_MODEL_NAME"
```

DA 默认使用 `dev` profile。初始化本地元数据数据库：

```bash
mysql -u YOUR_DB_USER -p -e \
  "CREATE DATABASE IF NOT EXISTS indbtest DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"

mysql -u YOUR_DB_USER -p indbtest < apps/da/schema.sql
```

真实数据库密码优先通过命令行参数覆盖，不要写入已提交的 YAML 文件：

```bash
java -jar apps/da/target/da-indicator-0.0.1-SNAPSHOT.jar \
  --spring.profiles.active=dev \
  --spring.datasource.dynamic.datasource.mysql.username=YOUR_DB_USER \
  --spring.datasource.dynamic.datasource.mysql.password=YOUR_DB_PASSWORD \
  --indicator.graph.data-path=/path/to/InsightMind/apps/ad/output/business_kg/indicator-data.ttl
```

## 常用开发命令

AD：

```bash
cd apps/ad
source venv/bin/activate
python web_app.py
PYTHONPATH=. pytest
```

DA：

```bash
cd apps/da
mvn -DskipTests package
mvn test -DskipTests=false
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

## 项目结构

```text
InsightMind/
  apps/
    ad/      # Python/FastAPI KG builder, NLQ, Ad-Hoc, dashboard preview
    da/      # Spring Boot indicator service and SQL generation backend
  logs/      # local runtime logs, ignored by git
  scripts/
    insightmind.sh
```

更多实现细节见：

- `apps/ad/README.md`
- `apps/ad/AGENTS.md`
- `apps/da/README.md`
- `apps/da/AGENTS.md`

## 安全提示

- 不要提交真实数据库凭据、LLM API key、OAuth secret、cookie 或 token。
- 本地密钥使用环境变量、命令行参数或 git 忽略的本地配置文件。
- `apps/da/src/main/resources/application*.yml` 只作为本地/历史环境参考，不应提交真实改动。
- 生成目录、日志、虚拟环境、Maven target 和本地运行缓存不应进入 Git。
