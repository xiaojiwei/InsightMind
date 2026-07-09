# InsightMind Agent MCP Gateway

本文档说明如何把 InsightMind 的知识图谱、语义查询、DA DataGPT 查询和 DA DSL 能力暴露为 MCP 服务，供 Codex、Claude Code、OpenClaw 或其它 agent 直接调用。

## 设计目标

Gateway 是一个薄代理，不重写 AD/DA 的核心逻辑：

```text
Agent / IDE / CLI
  -> MCP tools
  -> apps/agent_gateway/insightmind_mcp.py
  -> AD FastAPI + DA Spring Boot
  -> RDF/OWL graph + DA query engine + DA DSL runtime
```

这样做的好处：

- AD/DA 仍是唯一真实查询执行方。
- agent 获取的是结构化工具，而不是页面 UI。
- DA DSL 作为一等能力暴露，agent 可以生成、解释和执行复杂派生指标表达式。
- MCP 是跨 agent 的协议；Codex、Claude Code 等可以通过同一套工具接入。

## 前置条件

在项目根目录启动 AD 和 DA：

```bash
./scripts/insightmind.sh start
./scripts/insightmind.sh status
```

默认地址：

- AD: `http://localhost:8080`
- DA: `http://localhost:8091`

## 安装

Gateway 使用独立依赖，不污染 AD/DA：

```bash
cd /Users/xiaojiwei/InsightMind
python3 -m venv .venv-mcp
source .venv-mcp/bin/activate
pip install -r apps/agent_gateway/requirements.txt
```

## 运行方式

### stdio 模式

stdio 适合本机 agent，例如 Codex CLI、Claude Code 本地连接：

```bash
cd /Users/xiaojiwei/InsightMind
source .venv-mcp/bin/activate
INSIGHTMIND_AD_BASE_URL=http://localhost:8080 \
INSIGHTMIND_DA_BASE_URL=http://localhost:8091 \
python apps/agent_gateway/insightmind_mcp.py
```

stdio 模式通常由 agent 自动拉起，不需要手工常驻。

### Streamable HTTP 模式

HTTP 模式适合多个 agent 或远程服务共享：

```bash
cd /Users/xiaojiwei/InsightMind
source .venv-mcp/bin/activate
INSIGHTMIND_MCP_TRANSPORT=streamable-http \
INSIGHTMIND_AD_BASE_URL=http://localhost:8080 \
INSIGHTMIND_DA_BASE_URL=http://localhost:8091 \
python apps/agent_gateway/insightmind_mcp.py
```

默认 MCP HTTP 地址由 Python MCP SDK 提供，通常是：

```text
http://localhost:8000/mcp
```

## 环境变量

| 变量 | 默认值 | 说明 |
|---|---:|---|
| `INSIGHTMIND_AD_BASE_URL` | `http://localhost:8080` | AD 服务地址 |
| `INSIGHTMIND_DA_BASE_URL` | `http://localhost:8091` | DA 服务地址 |
| `INSIGHTMIND_MCP_TRANSPORT` | `stdio` | `stdio`、`streamable-http` 或 `sse` |
| `INSIGHTMIND_MCP_TIMEOUT` | `60` | 后端请求超时秒数 |
| `INSIGHTMIND_MCP_MAX_PAGE_SIZE` | `1000` | 查询 page size 上限 |
| `INSIGHTMIND_MCP_ALLOW_RAW_SPARQL` | `false` | 是否允许 raw SPARQL SELECT |
| `INSIGHTMIND_AD_BEARER_TOKEN` | 空 | 转发给 AD 的 bearer token |
| `INSIGHTMIND_DA_BEARER_TOKEN` | 空 | 转发给 DA 的 bearer token |
| `INSIGHTMIND_DA_USERNAME` | 空 | 默认 DA 用户名 |

## Codex 接入

stdio 示例：

```bash
codex mcp add insightmind \
  --env INSIGHTMIND_AD_BASE_URL=http://localhost:8080 \
  --env INSIGHTMIND_DA_BASE_URL=http://localhost:8091 \
  -- /Users/xiaojiwei/InsightMind/.venv-mcp/bin/python \
     /Users/xiaojiwei/InsightMind/apps/agent_gateway/insightmind_mcp.py
```

HTTP 示例：

```toml
[mcp_servers.insightmind]
url = "http://localhost:8000/mcp"
tool_timeout_sec = 120
```

## Claude Code 接入

stdio 示例：

```bash
claude mcp add insightmind -- \
  /Users/xiaojiwei/InsightMind/.venv-mcp/bin/python \
  /Users/xiaojiwei/InsightMind/apps/agent_gateway/insightmind_mcp.py
```

HTTP 示例：

```bash
claude mcp add --transport http insightmind http://localhost:8000/mcp
```

## OpenClaw 或其它 agent 接入

如果 agent 支持 MCP，优先使用 HTTP：

```text
http://localhost:8000/mcp
```

如果只支持本地进程，则配置 stdio command：

```text
/Users/xiaojiwei/InsightMind/.venv-mcp/bin/python
/Users/xiaojiwei/InsightMind/apps/agent_gateway/insightmind_mcp.py
```

## 暴露的 MCP Tools

| Tool | 后端 | 作用 |
|---|---|---|
| `health` | AD + DA | 检查 AD/DA 是否可达 |
| `get_semantic_meta` | AD | 获取语义元数据 |
| `search_catalog` | AD/DA | 搜索指标和维度 |
| `nlq_query` | AD | 自然语言问数 |
| `semantic_query` | AD | 结构化语义查询 |
| `semantic_sql` | AD | 将 semantic query 翻译成 DA payload，可选 SQL review |
| `graph_query_preset` | AD | 源图谱预设查询 |
| `related_codes` | DA | 查询给定指标/维度相关的指标和维度 |
| `find_dimensions_by_value` | DA | 根据维值反查可能所属维度 |
| `raw_sparql_select` | AD | raw SPARQL SELECT，默认关闭 |
| `da_ai_query` | DA | DA DataGPT 自然语言查询 |
| `da_text_to_sql` | DA | DA DataGPT 文本转 SQL |
| `da_datasource_query` | DA | 直接调用 DA DataSource 查询 |
| `dsl_explain` | 本地静态分析 | 解释 DA DSL 依赖的函数、指标、维度、LOD、过滤 |
| `dsl_validate` | 本地静态分析 | 对 DA DSL 表达式做轻量静态校验 |
| `da_query_with_dsl_expression` | DA | 注入 DSL 虚拟指标并执行 DA 查询 |

## DA DSL 示例

### 格式化指标

```text
Concatenate(Format(Calculate([MEAS_d93e71a5fde84f968e3e2e6696297f6c]), '%,.2f'), '%')
```

### LOD 固定维度

```text
Calculate([MEAS_mmm],fixed:([DIM_aab],[DIM_aaa]))
```

### LOD + 过滤

```text
Calculate([MEAS_mmm],fixed:([DIM_aab],[DIM_aaa]),filters:(([DIM_bbb] in '北京')))
```

### 条件表达式

```text
if(Calculate([MEAS_a]) > 0, Calculate([MEAS_b]) / Calculate([MEAS_a]), 0)
```

## 推荐 agent 工作流

1. 先调用 `search_catalog` 找到候选指标和维度。
2. 调用 `get_semantic_meta` 或 `graph_query_preset` 确认可用关系。
3. 如果是普通问数，用 `nlq_query` 或 `semantic_query`。
4. 如果需要复杂派生指标，先让 agent 生成 DA DSL。
5. 调用 `dsl_explain` 做静态检查。
6. 调用 `da_query_with_dsl_expression` 或 `da_datasource_query` 执行。
7. 必要时调用 `semantic_sql` 或 `da_text_to_sql` 查看 SQL/查询计划。

## 安全边界

- Gateway 默认只读，不提供构建图谱、修改指标、写库等工具。
- raw SPARQL 默认关闭，只能通过 `INSIGHTMIND_MCP_ALLOW_RAW_SPARQL=true` 显式开启。
- raw SPARQL 即使开启也只允许 `SELECT`。
- page size 会被 `INSIGHTMIND_MCP_MAX_PAGE_SIZE` 限制。
- Gateway 不保存凭据；token 通过环境变量注入并转发。
- 不建议让外部公网直接访问 MCP HTTP 服务；生产环境应放在内网，并增加网关认证、审计和限流。

## 已知限制

- `dsl_explain` 是轻量静态分析，不等价于 DA ANTLR 完整校验；真正语义以 DA 执行为准。
- `da_query_with_dsl_expression` 需要传入可被 DA 接受的 `DataSource` payload，Gateway 只注入虚拟指标表达式。
- 如果 DA 需要登录态或 JWT，需要通过 `INSIGHTMIND_DA_BEARER_TOKEN` 或上游网关转发鉴权信息。
