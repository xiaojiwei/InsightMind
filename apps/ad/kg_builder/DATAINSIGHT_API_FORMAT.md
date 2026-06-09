# DataInsight API 格式文档 / DataInsight API Format

## ⚠️ 重要：API 请求格式规范 (CRITICAL: API Request Format)

本文档记录了 DataAgent 指标分析 API 的正确请求格式，避免 AI 分析时猜测错误格式。

---

## API 端点 (Endpoint)

```
POST https://da-indicator.prod.k8s.chehejia.com/bi/v1/datasource/query
Content-Type: application/json
Authorization: Bearer <token>
```

---

## 核心请求结构 (Core Request Structure)

```json
{
  "chartType": 0,
  "sourceType": 0,
  "operaType": 1,
  "cacheStrategy": 3,
  "id": 1029,
  "code": "DataSource_xxxxxxxxxxxx",
  "creator": "<username>",
  "updater": "<username>",
  "spaceId": 4,
  "pageNo": 1,
  "pageSize": 50,
  "configureList": [...],
  "filterList": [...],
  "filterTreeList": [],
  "detailColumnList": [],
  "directQuery": false,
  "dsl": false,
  "routeType": "nlp",
  "tableId": 0,
  "useCache": true,
  "limitNum": 1000,
  "dataRange": true,
  "dataAllRange": true
}
```

---

## configureList 结构 (configureList Structure)

**作用**: 定义指标和维度配置

**格式要求**:
- **指标 (Measure)**: `MEAS_` 前缀 + 指标代码，例如 `MEAS_9e0af3b5cc9f443985d41859ebfa8fe2`
- **维度 (Dimension)**: `DIM_` 前缀 + 维度代码，例如 `DIM_7721e0c391ec4330a5721fd87db31316`

```json
"configureList": [
  {
    "code": "MEAS_<指标代码>",
    "order": { "sortType": 0 },
    "ratioList": [],
    "alias": ""
  },
  {
    "code": "DIM_<维度代码>",
    "order": { "sortType": 1 },
    "ratioList": [],
    "alias": "",
    "hasSubtotal": false
  }
]
```

### configureList 字段说明

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| code | string | ✅ | **必须包含 MEAS_ 或 DIM_ 前缀** |
| order | object | ✅ | 排序配置，默认 `{ "sortType": 0 }` |
| ratioList | array | ✅ | 空数组 `[]` |
| alias | string | ✅ | 别名，空字符串 `""` |
| hasSubtotal | boolean | DIM必填 | 维度是否显示小计，默认 `false` |

---

## filterList 结构 (filterList Structure)

**作用**: 定义时间维度过滤器

```json
"filterList": [
  {
    "code": "DIM_<时间维度代码>",
    "operatorList": [
      {
        "sqlOprType": 2,
        "dataList": ["2026-05-18", "2026-05-18"],
        "timeRange": 1
      }
    ],
    "internal": true
  }
]
```

### filterList 字段说明

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| code | string | ✅ | 时间维度代码，**必须包含 DIM_ 前缀** |
| operatorList | array | ✅ | 操作符列表 |
| internal | boolean | ✅ | 默认 `true` |

### operatorList 内嵌结构

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| sqlOprType | integer | ✅ | 操作类型，`2` 表示范围查询 |
| dataList | array | ✅ | **日期格式必须为 YYYY-MM-DD**，如 `["2026-05-18", "2026-05-18"]` |
| timeRange | integer | ✅ | 时间范围类型，`1` 表示日期范围 |

### sqlOprType 值说明

| 值 | 含义 |
|----|------|
| 0 | IN (包含) |
| 1 | = (等于) |
| 2 | BETWEEN (范围) |
| 3 | > (大于) |
| 4 | < (小于) |
| 5 | >= (大于等于) |
| 6 | <= (小于等于) |

---

## ⚠️ 常见错误 (Common Mistakes)

### ❌ 错误1：缺少前缀
```json
// 错误 - 缺少 MEAS_ 或 DIM_ 前缀
{ "code": "catalog_sales" }

// 正确
{ "code": "MEAS_catalog_sales" }
```

### ❌ 错误2：日期格式错误
```json

// 错误 - 使用时间戳或 YYYYMMDD 格式
"dataList": [20260518, 20260518]
"dataList": ["20260518", "20260518"]

// 正确 - YYYY-MM-DD 格式
"dataList": ["2026-05-18", "2026-05-18"]
```

### ❌ 错误3：剥离了前缀
```python
# 错误 - 在发送前剥离了前缀
code = code.replace("MEAS_", "").replace("DIM_", "")

// 正确 - 保留前缀
code = f"MEAS_{code}"
```

### ❌ 错误4：filterList 中放错了内容
```json
// 错误 - 把 configureList 的内容放到了 filterList
"filterList": [
  { "code": "MEAS_catalog_sales", "order": {...} }
]

// 正确 - filterList 只放时间维度过滤条件
"filterList": [
  { "code": "DIM_dim_date_week", "operatorList": [...] }
]
```

---

## 完整请求示例 (Complete Request Example)

```json
{
  "chartType": 0,
  "sourceType": 0,
  "operaType": 1,
  "cacheStrategy": 3,
  "id": 1029,
  "code": "DataSource_fb40bb8c65eb4752952cc02f1c2df770",
  "creator": "dongzelong",
  "updater": "dongzelong",
  "spaceId": 4,
  "pageNo": 1,
  "pageSize": 50,
  "configureList": [
    {
      "code": "MEAS_9e0af3b5cc9f443985d41859ebfa8fe2",
      "order": { "sortType": 0 },
      "ratioList": [],
      "alias": ""
    },
    {
      "code": "DIM_7721e0c391ec4330a5721fd87db31316",
      "order": { "sortType": 1 },
      "ratioList": [],
      "alias": "",
      "hasSubtotal": false
    }
  ],
  "filterList": [
    {
      "code": "DIM_a15f9bcd0235428fbaf164b584f8055f",
      "operatorList": [
        {
          "sqlOprType": 2,
          "dataList": ["2026-05-18", "2026-05-18"],
          "timeRange": 1
        }
      ],
      "internal": true
    }
  ],
  "filterTreeList": [],
  "detailColumnList": [],
  "directQuery": false,
  "dsl": false,
  "routeType": "nlp",
  "tableId": 0,
  "useCache": true,
  "limitNum": 1000,
  "dataRange": true,
  "dataAllRange": true
}
```

---

## 代码调用示例 (Code Examples)

### Python (Flask Backend)

```python
import requests

def call_data_agent_api(indicator_code, dimension_codes, start_date, end_date):
    url = "https://da-indicator.prod.k8s.chehejia.com/bi/v1/datasource/query"
    headers = {
        "Content-Type": "application/json",
        "Authorization": f"Bearer {token}"
    }

    configure_list = [
        {"code": f"MEAS_{indicator_code}", "order": {"sortType": 0}, "ratioList": [], "alias": ""}
    ]

    for dim_code in dimension_codes:
        configure_list.append({
            "code": f"DIM_{dim_code}",
            "order": {"sortType": 1},
            "ratioList": [],
            "alias": "",
            "hasSubtotal": False
        })

    filter_list = []
    if start_date and end_date:
        filter_list.append({
            "code": f"DIM_{dimension_codes[0]}",
            "operatorList": [{
                "sqlOprType": 2,
                "dataList": [start_date, end_date],  # YYYY-MM-DD format
                "timeRange": 1
            }],
            "internal": True
        })

    payload = {
        "chartType": 0,
        "sourceType": 0,
        "operaType": 1,
        "cacheStrategy": 3,
        "code": "DataSource_xxx",
        "spaceId": 4,
        "pageNo": 1,
        "pageSize": 50,
        "configureList": configure_list,
        "filterList": filter_list,
        "useCache": True,
        "limitNum": 1000,
        "dataRange": True,
        "dataAllRange": True,
        "routeType": "nlp"
    }

    response = requests.post(url, json=payload, headers=headers)
    return response.json()
```

### JavaScript (Frontend)

```javascript
function buildAnalysisRequest(indicatorCode, dimensionCodes, startDate, endDate) {
    const configureList = [
        { code: `MEAS_${indicatorCode}`, order: { sortType: 0 }, ratioList: [], alias: "" }
    ];

    dimensionCodes.forEach((dc, index) => {
        configureList.push({
            code: `DIM_${dc}`,
            order: { sortType: index + 1 },
            ratioList: [],
            alias: "",
            hasSubtotal: false
        });
    });

    const filterList = [];
    if (startDate && endDate) {
        filterList.push({
            code: `DIM_${dimensionCodes[0]}`,
            operatorList: [{
                sqlOprType: 2,
                dataList: [startDate, endDate],  // YYYY-MM-DD format
                timeRange: 1
            }],
            internal: true
        });
    }

    return {
        chartType: 0,
        sourceType: 0,
        operaType: 1,
        cacheStrategy: 3,
        code: "DataSource_xxx",
        spaceId: 4,
        pageNo: 1,
        pageSize: 50,
        configureList,
        filterList,
        filterTreeList: [],
        detailColumnList: [],
        directQuery: false,
        dsl: false,
        routeType: "nlp",
        tableId: 0,
        useCache: true,
        limitNum: 1000,
        dataRange: true,
        dataAllRange: true
    };
}
```

---

## 关键规则总结 (Key Rules Summary)

1. **指标代码必须以 `MEAS_` 开头**
2. **维度代码必须以 `DIM_` 开头**
3. **时间过滤日期格式必须是 `YYYY-MM-DD`**
4. **`configureList` 放指标和维度配置**
5. **`filterList` 只放时间维度过滤条件**
6. **不要在发送前剥离前缀**
7. **`sqlOprType: 2` 表示范围查询**
8. **`timeRange: 1` 表示日期范围**

---

*本文档由 AI 根据生产环境实际请求格式生成，请勿猜测格式。*
