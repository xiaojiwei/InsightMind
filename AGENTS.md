# AGENTS.md

本文件为 Codex（Codex.ai/code）在此仓库中工作时提供指引。

## 项目概览

InsightMind 将两个服务整合在一个工作空间中：

- **`apps/ad`** — Python/FastAPI 知识图谱构建器。连接关系型数据库，构建 RDF/OWL 数据源知识图谱和业务知识图谱（Turtle 格式），提供 Web UI 用于图谱浏览、NLQ 自然语言查询、Ad-Hoc 查询和仪表盘预览。
- **`apps/da`** — Spring Boot（2.5.6，Java 8）指标服务。提供指标元数据、维度 SQL 生成、数据源查询 API、仪表盘和监控。它消费 AD 生成的业务知识图谱。

两者的集成方式：AD 生成 `apps/ad/output/business_kg/indicator-data.ttl`；DA 通过 `GraphStore`（Apache Jena）加载该文件，用于基于 SPARQL 的指标查询和 SQL 生成。

各子项目有自己的文档：
- `apps/ad/README.md` 和 `apps/ad/AGENTS.md`
- `apps/da/README.md` 和 `apps/da/AGENTS.md`

## 服务管理

在项目根目录下：

```bash
./scripts/insightmind.sh start          # 同时启动 AD（端口 8080）和 DA（端口 8091）
./scripts/insightmind.sh restart ad     # 仅重启 AD
./scripts/insightmind.sh status         # 查看两个服务的运行状态
```

脚本在 macOS 上使用 `launchctl` 管理进程。日志写入 `logs/ad.log` 和 `logs/da.log`。脚本会自动发现 Python venv 和 Java 运行时；可通过环境变量覆盖：`INSIGHTMIND_AD_PYTHON`、`INSIGHTMIND_JAVA`、`INSIGHTMIND_AD_PORT`、`INSIGHTMIND_DA_PORT`。

## 构建、测试和常用命令

### AD（Python）

```bash
cd apps/ad && python -m venv venv && source venv/bin/activate
pip install -r requirements.txt
PYTHONPATH=. pytest                                    # 运行全部测试
PYTHONPATH=. pytest tests/test_public_date_dimensions.py  # 运行单个测试文件
python web_app.py                                      # 单独启动 AD
```

### DA（Java）

```bash
cd apps/da && mvn -DskipTests package                  # 构建（pom.xml 默认跳过测试）
mvn test -DskipTests=false                             # 运行全部测试
mvn test -DskipTests=false -Dtest=ClassName            # 运行单个测试类
mvn spring-boot:run -Dspring-boot.run.profiles=dev     # 以 dev profile 启动
```

## 核心架构：AD → DA 知识图谱流水线

两个服务通过一个 Turtle 文件连接：

1. **AD 构建数据源知识图谱**，从数据库元数据（schema、采样、外键检测、隐式关系）中提取。Web UI 的 `POST /api/build` 在 `web_app.py:_build_worker` 中运行一个 15 步的流水线。

2. **AD 生成业务知识图谱**，有两种路径：
   - **ETL 路径**（有参考库时优先使用）：`data_exporter.py` 直接从参考指标平台 MySQL 数据库导出指标/维度数据。确定性过程，无需 LLM。
   - **LLM 路径**（无参考库时回退）：`llm_builder.py` 将元数据摘要发送给 OpenAI 兼容的 LLM，推断指标、维度及其关系。构建器会自动将查询失败信息反馈给 LLM 进行修复（最多 3 轮修复迭代）。

3. **DA 加载业务知识图谱** — `GraphStore` 监控 `indicator.graph.data-path`，在文件变更时热加载 Jena RDF 模型。`GraphIndicatorServiceImpl` 对其执行 SPARQL 查询。`IndicatorOntology` 类定义了 OWL 常量（Measure、Dimension、DwTable 等）。

此流水线中的关键文件：
- `apps/ad/web_app.py` — FastAPI 应用，构建编排器，业务知识图谱构建/验证/修复循环
- `apps/ad/kg_builder/business_kg/data_exporter.py` — 从指标平台数据库 ETL 导出
- `apps/ad/kg_builder/business_kg/llm_builder.py` — 基于 LLM 的业务知识图谱生成 + 修复
- `apps/ad/kg_builder/business_kg/pattern_extractor.py` — 从参考库提炼指标/维度规律，辅助 LLM 生成
- `apps/da/.../graph/GraphStore.java` — 将 TTL 加载到 Jena，支持热加载
- `apps/da/.../graph/IndicatorOntology.java` — OWL 常量定义

## AD 包结构（`apps/ad/kg_builder/`）

- `connectors/` — 数据库连接器（MySQL、MSSQL、Oracle、SQLite），基于 SQLAlchemy
- `parsers/` — Schema 解析（`schema_parser.py`）和数据采样（`data_sampler.py`）
- `entities/` — `models.py` 定义数据类（Table、Column、EntityGraph）；`extractor.py` 构建实体图
- `relations/` — 外键检测（`explicit.py`、`fk_detector.py`）、隐式/语义相似关系（`implicit.py`）、注释引用检测、包含依赖检测、枚举值对齐
- `ontology/` — RDF/OWL 构建器（`rdf_builder.py`）、OWL schema 常量（`owl_schema.py`）
- `query/` — `sparql_api.py`（SPARQL 查询门面）、`path_finder.py`（JOIN 路径和变更影响分析）
- `business_kg/` — 所有业务知识图谱逻辑（提取、ETL 导出、LLM 生成、模式提炼）
- `semantic/` — `ad_api.py` 提供类似 Cube.js 的语义查询门面；`sql_api.py` 处理 SQL 生成
- `analysis/` — 统计分析和洞察分析
- `analytics/` — 表分类
- `nlq/` — 自然语言查询服务（基于 LLM）
- `quality/` — 外键完整性检测
- `utils/` — LLM 配置和中文翻译

## DA 架构（`apps/da/src/main/java/com/graphinsight/indicator/`）

DA 遵循分层 Spring Boot 架构。以下是不容易从文件列表看出的关键点：

- **多数据源路由**：在 mapper/service 方法上使用 `@DS` 注解，在 MySQL（元数据）和 Doris（分析）之间路由查询，由 `dynamic-datasource` + Druid 驱动。
- **Manager 层**位于 Service 和 Controller 之间 — `SQLGenerateManager`、`MeasureManager`、`DimensionManager`、`DismantlingTreeManager`、`DecisionTreeManager`、`BloodManager`（数据血缘）等，负责编排复杂的业务逻辑。
- **自定义表达式 DSL（LAX/XLax）**：基于 ANTLR 4 语法的领域特定语言，用于过滤表达式、指标优化、变量引用和 if-else 逻辑。`.g4` 文件是唯一真实来源；生成的 lexer/parser 代码也一并提交在仓库中。
- **知识图谱**：`graph/` 包 — `GraphStore` 将 Turtle 加载到 Jena 模型并支持热加载；`IndicatorOntology` 定义 OWL 常量；`GraphIndicatorServiceImpl` 对内存中的模型执行 SPARQL 查询。
- **自动生成代码**：`auto/` 包含 MyBatis-Plus 代码生成器的输出（entities、mappers、services、controllers）。手写的 DAO 在 `dao/` 中用于自定义查询。使用 `MybatisGenerator.java` 重新生成。
- **AOP 切面**：`@AuthCheck` 用于 JWT 认证，`@CheckCacheVersion`/`@ReloadCache` 用于缓存，`@OperateLog` 用于操作审计日志。
- **测试**在根测试包中（`src/test/java/.../indicator/`），部分在子目录中（`measure/`、`dimension/` 等）。测试资源与 `src/main/resources` 共用。

## 凭据与安全

- 切勿提交真实数据库凭据、LLM API 密钥或 OAuth2 密钥。
- AD：使用 `config.local.yaml`（已 git 忽略）替代 `config.yaml`。LLM 密钥通过环境变量 `LLM_BASE_URL`、`LLM_API_KEY`、`LLM_MODEL_NAME` 设置。
- DA：`application-*.yml` 文件包含硬编码的开发环境凭据 — 不要提交对这些文件的修改。本地凭据优先使用命令行参数覆盖（`--spring.datasource.dynamic.datasource.mysql.password=...`）。
