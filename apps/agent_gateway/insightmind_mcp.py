"""MCP server for exposing InsightMind graph, semantic query, and DA DSL tools.

The server is intentionally a thin proxy. It does not reimplement AD/DA query
logic; it gives agents a stable tool surface over the existing HTTP APIs.
"""

from __future__ import annotations

import os
import re
from typing import Any, Literal
from urllib.parse import quote

import httpx
from mcp.server.fastmcp import FastMCP


AD_BASE_URL = os.getenv("INSIGHTMIND_AD_BASE_URL", "http://localhost:8080").rstrip("/")
DA_BASE_URL = os.getenv("INSIGHTMIND_DA_BASE_URL", "http://localhost:8091").rstrip("/")
REQUEST_TIMEOUT_SECONDS = float(os.getenv("INSIGHTMIND_MCP_TIMEOUT", "60"))
MAX_PAGE_SIZE = int(os.getenv("INSIGHTMIND_MCP_MAX_PAGE_SIZE", "1000"))
ALLOW_RAW_SPARQL = os.getenv("INSIGHTMIND_MCP_ALLOW_RAW_SPARQL", "false").lower() in {"1", "true", "yes"}


mcp = FastMCP(
    "InsightMind",
    instructions=(
        "Use these tools to query InsightMind's knowledge graph, metric catalog, "
        "semantic API, and DA DSL-backed data service. Prefer catalog/DSL explain "
        "tools before executing data queries. Queries are read-only and page sizes "
        "are capped by the server."
    ),
    json_response=True,
)


def _headers(prefix: str) -> dict[str, str]:
    headers: dict[str, str] = {"Accept": "application/json"}
    token = os.getenv(f"INSIGHTMIND_{prefix}_BEARER_TOKEN", "").strip()
    if token:
        headers["Authorization"] = f"Bearer {token}"
    username = os.getenv(f"INSIGHTMIND_{prefix}_USERNAME", "").strip()
    if username:
        headers["X-InsightMind-User"] = username
    return headers


def _cap_page_size(value: int | None) -> int:
    try:
        page_size = int(value or 100)
    except Exception:
        page_size = 100
    return max(1, min(page_size, MAX_PAGE_SIZE))


def _request(
    service: Literal["ad", "da"],
    method: Literal["GET", "POST"],
    path: str,
    *,
    json_body: dict[str, Any] | None = None,
    params: dict[str, Any] | None = None,
) -> dict[str, Any]:
    base_url = AD_BASE_URL if service == "ad" else DA_BASE_URL
    prefix = "AD" if service == "ad" else "DA"
    url = f"{base_url}/{path.lstrip('/')}"
    with httpx.Client(timeout=REQUEST_TIMEOUT_SECONDS, headers=_headers(prefix)) as client:
        response = client.request(method, url, json=json_body, params=params)
    try:
        payload = response.json()
    except Exception:
        payload = {"text": response.text}
    if response.status_code >= 400:
        return {
            "ok": False,
            "statusCode": response.status_code,
            "service": service,
            "url": url,
            "error": payload,
        }
    if isinstance(payload, dict):
        payload.setdefault("ok", True)
        return payload
    return {"ok": True, "data": payload}


def _list_items(meta: dict[str, Any], key: str) -> list[dict[str, Any]]:
    value = meta.get(key)
    if isinstance(value, list):
        return [item for item in value if isinstance(item, dict)]
    data = meta.get("data")
    if isinstance(data, dict) and isinstance(data.get(key), list):
        return [item for item in data[key] if isinstance(item, dict)]
    return []


def _compact_items(items: list[dict[str, Any]], limit: int) -> list[dict[str, Any]]:
    compact: list[dict[str, Any]] = []
    for item in items[:limit]:
        compact.append(
            {
                "code": item.get("code") or item.get("measureCode") or item.get("dimCode"),
                "name": item.get("name") or item.get("cnName") or item.get("alias"),
                "alias": item.get("alias"),
                "description": item.get("description") or item.get("comment") or item.get("bizDesc"),
                "expression": item.get("expression"),
                "raw": item,
            }
        )
    return compact


def _contains_keyword(item: dict[str, Any], keyword: str) -> bool:
    haystack = " ".join(
        str(item.get(key) or "")
        for key in ("code", "measureCode", "dimCode", "name", "cnName", "alias", "description", "comment", "bizDesc")
    ).lower()
    return keyword.lower() in haystack


_FUNCTION_RE = re.compile(r"\b([A-Za-z][A-Za-z0-9_]*)\s*\(")
_MEASURE_RE = re.compile(r"\[MEAS_[-A-Za-z0-9_]+\]")
_DIMENSION_RE = re.compile(r"\[DIM_[-A-Za-z0-9_]+\]")
_FILTER_BLOCK_RE = re.compile(r"filters:\((.*?)\)", re.IGNORECASE)
_ALLOWED_FUNCTIONS = {"Calculate", "Concatenate", "Ttest", "Workday", "Format", "SelectColumns", "CDP", "ER", "if"}


def _strip_brackets(code: str) -> str:
    return code.strip("[]")


def _balance_warnings(expression: str) -> list[str]:
    warnings: list[str] = []
    pairs = [("(", ")"), ("[", "]")]
    for left, right in pairs:
        if expression.count(left) != expression.count(right):
            warnings.append(f"{left}{right} 数量不匹配")
    single_quotes = expression.count("'") - expression.count("\\'")
    double_quotes = expression.count('"') - expression.count('\\"')
    if single_quotes % 2 != 0:
        warnings.append("单引号数量不匹配")
    if double_quotes % 2 != 0:
        warnings.append("双引号数量不匹配")
    return warnings


@mcp.tool()
def health() -> dict[str, Any]:
    """Check whether the configured AD and DA services are reachable."""
    ad = _request("ad", "GET", "/api/build/status")
    da = _request("da", "GET", "/ai/allMeasure")
    return {
        "ok": bool(ad.get("ok")) and bool(da.get("ok")),
        "adBaseUrl": AD_BASE_URL,
        "daBaseUrl": DA_BASE_URL,
        "ad": {"ok": ad.get("ok"), "statusCode": ad.get("statusCode"), "error": ad.get("error")},
        "da": {"ok": da.get("ok"), "statusCode": da.get("statusCode"), "error": da.get("error")},
    }


@mcp.tool()
def get_semantic_meta() -> dict[str, Any]:
    """Return AD semantic metadata, including measures and dimensions when available."""
    return _request("ad", "GET", "/api/ad/v1/meta")


@mcp.tool()
def search_catalog(keyword: str, limit: int = 20) -> dict[str, Any]:
    """Search measure and dimension catalogs by keyword."""
    limit = max(1, min(int(limit or 20), 100))
    meta = get_semantic_meta()
    measures = [item for item in _list_items(meta, "measures") if _contains_keyword(item, keyword)]
    dimensions = [item for item in _list_items(meta, "dimensions") if _contains_keyword(item, keyword)]

    if not measures and not dimensions:
        da_measures = _request("da", "GET", "/ai/allMeasure")
        da_dimensions = _request("da", "GET", "/ai/allDimension")
        raw_measures = da_measures.get("data") if isinstance(da_measures.get("data"), list) else []
        raw_dimensions = da_dimensions.get("data") if isinstance(da_dimensions.get("data"), list) else []
        measures = [item for item in raw_measures if isinstance(item, dict) and _contains_keyword(item, keyword)]
        dimensions = [item for item in raw_dimensions if isinstance(item, dict) and _contains_keyword(item, keyword)]

    return {
        "ok": True,
        "keyword": keyword,
        "measures": _compact_items(measures, limit),
        "dimensions": _compact_items(dimensions, limit),
    }


@mcp.tool()
def nlq_query(
    question: str,
    execute: bool = True,
    page_size: int = 100,
    page_num: int = 1,
    max_dimensions: int = 3,
    query_mode: str = "auto",
    conversation_id: str = "",
    context: dict[str, Any] | None = None,
    is_follow_up: bool = False,
    reset_context: bool = False,
) -> dict[str, Any]:
    """Ask a natural-language data question through AD's NLQ service."""
    body = {
        "question": question,
        "execute": bool(execute),
        "pageSize": _cap_page_size(page_size),
        "pageNum": max(1, int(page_num or 1)),
        "maxDimensions": max(0, min(int(max_dimensions or 3), 5)),
        "queryMode": query_mode,
        "conversationId": conversation_id,
        "context": context or {},
        "isFollowUp": bool(is_follow_up),
        "resetContext": bool(reset_context),
    }
    return _request("ad", "POST", "/api/nlq/query", json_body=body)


@mcp.tool()
def semantic_query(query: dict[str, Any]) -> dict[str, Any]:
    """Run AD's structured semantic query API."""
    safe_query = dict(query or {})
    safe_query["limit"] = _cap_page_size(safe_query.get("limit") or safe_query.get("pageSize"))
    safe_query["pageSize"] = safe_query["limit"]
    return _request("ad", "POST", "/api/ad/v1/load", json_body=safe_query)


@mcp.tool()
def semantic_sql(query: dict[str, Any], execute_review: bool = False) -> dict[str, Any]:
    """Translate an AD semantic query into the DA payload and optionally review SQL."""
    safe_query = dict(query or {})
    safe_query["execute"] = bool(execute_review)
    safe_query["limit"] = _cap_page_size(safe_query.get("limit") or safe_query.get("pageSize"))
    return _request("ad", "POST", "/api/ad/v1/sql", json_body=safe_query)


@mcp.tool()
def graph_query_preset(query_type: str, param: str = "", param2: str = "") -> dict[str, Any]:
    """Run a curated AD source-graph query preset."""
    body = {"query_type": query_type, "param": param, "param2": param2}
    return _request("ad", "POST", "/api/query/preset", json_body=body)


@mcp.tool()
def related_codes(measure_codes: list[str] | None = None, dimension_codes: list[str] | None = None) -> dict[str, Any]:
    """Return DA measure/dimension codes related to the provided code sets."""
    body = {
        "measureSet": list(measure_codes or []),
        "dimensionSet": list(dimension_codes or []),
    }
    return _request("da", "POST", "/ai/relation", json_body=body)


@mcp.tool()
def find_dimensions_by_value(value: str) -> dict[str, Any]:
    """Find possible DA dimensions for a human-entered dimension value."""
    return _request("da", "GET", f"/ai/getDimensionsByValue/{quote(value, safe='')}")


@mcp.tool()
def raw_sparql_select(sparql: str) -> dict[str, Any]:
    """Run raw SPARQL SELECT against AD only when explicitly enabled by environment."""
    if not ALLOW_RAW_SPARQL:
        return {
            "ok": False,
            "error": "raw SPARQL is disabled. Set INSIGHTMIND_MCP_ALLOW_RAW_SPARQL=true to enable it.",
        }
    if not re.match(r"(?is)^\s*(?:prefix\s+\w+:\s*<[^>]+>\s*)*select\b", sparql or ""):
        return {"ok": False, "error": "Only SPARQL SELECT queries are allowed."}
    return _request("ad", "POST", "/api/query/sparql", json_body={"sparql": sparql})


@mcp.tool()
def da_ai_query(word: str, username: str = "", is_data: bool = True, use_cache: bool = True) -> dict[str, Any]:
    """Run DA DataGPT natural-language query."""
    body = {
        "word": word,
        "username": username or os.getenv("INSIGHTMIND_DA_USERNAME", ""),
        "isData": bool(is_data),
        "useCache": bool(use_cache),
    }
    return _request("da", "POST", "/data/gpt/v1/query", json_body=body)


@mcp.tool()
def da_text_to_sql(word: str, username: str = "") -> dict[str, Any]:
    """Ask DA DataGPT to translate text to SQL."""
    body = {"word": word, "username": username or os.getenv("INSIGHTMIND_DA_USERNAME", "")}
    return _request("da", "POST", "/data/gpt/v1/query/toSql", json_body=body)


@mcp.tool()
def da_datasource_query(data_source: dict[str, Any]) -> dict[str, Any]:
    """Run DA's DataSource query API with a caller-provided DataSource payload."""
    safe_source = dict(data_source or {})
    if "pageSize" in safe_source:
        safe_source["pageSize"] = _cap_page_size(safe_source.get("pageSize"))
    if "username" not in safe_source and os.getenv("INSIGHTMIND_DA_USERNAME"):
        safe_source["username"] = os.getenv("INSIGHTMIND_DA_USERNAME")
    return _request("da", "POST", "/bi/v1/datasource/query", json_body=safe_source)


@mcp.tool()
def dsl_explain(expression: str) -> dict[str, Any]:
    """Statically explain a DA DSL expression without executing it."""
    expression = expression or ""
    functions = _FUNCTION_RE.findall(expression)
    unknown_functions = sorted({name for name in functions if name not in _ALLOWED_FUNCTIONS})
    measures = [_strip_brackets(code) for code in _MEASURE_RE.findall(expression)]
    dimensions = [_strip_brackets(code) for code in _DIMENSION_RE.findall(expression)]
    lod_modes = sorted(set(re.findall(r"\b(fixed|exclude)\s*:\s*\(", expression, re.IGNORECASE)))
    filter_blocks = _FILTER_BLOCK_RE.findall(expression)
    warnings = _balance_warnings(expression)
    if unknown_functions:
        warnings.append(f"未知函数: {', '.join(unknown_functions)}")
    return {
        "ok": not warnings,
        "expression": expression,
        "functions": functions,
        "measures": sorted(set(measures)),
        "dimensions": sorted(set(dimensions)),
        "lodModes": lod_modes,
        "filterBlocks": filter_blocks,
        "warnings": warnings,
        "notes": [
            "This is a static explanation. DA remains the source of truth for full ANTLR validation.",
            "Use da_query_with_dsl_expression to execute through DA's runtime parser.",
        ],
    }


@mcp.tool()
def dsl_validate(expression: str) -> dict[str, Any]:
    """Validate a DA DSL expression with the gateway's static checks."""
    explanation = dsl_explain(expression)
    return {
        "ok": bool(explanation.get("ok")),
        "valid": bool(explanation.get("ok")),
        "warnings": explanation.get("warnings") or [],
        "explain": explanation,
    }


@mcp.tool()
def da_query_with_dsl_expression(
    data_source: dict[str, Any],
    expression: str,
    virtual_measure_code: str = "MEAS_MCP_DSL",
    virtual_measure_name: str = "MCP DSL Metric",
) -> dict[str, Any]:
    """Inject a DA DSL expression as a virtual measure and run DA's datasource query."""
    if not expression:
        return {"ok": False, "error": "expression is required"}
    explanation = dsl_explain(expression)
    if explanation.get("warnings"):
        return {"ok": False, "error": "DSL expression has static warnings", "explain": explanation}

    safe_source = dict(data_source or {})
    configure_list = list(safe_source.get("configureList") or [])
    configure_list.append(
        {
            "code": virtual_measure_code,
            "name": virtual_measure_name,
            "expression": expression,
            "measureTypeFlag": "virtual",
        }
    )
    safe_source["configureList"] = configure_list
    if "pageSize" in safe_source:
        safe_source["pageSize"] = _cap_page_size(safe_source.get("pageSize"))
    if "username" not in safe_source and os.getenv("INSIGHTMIND_DA_USERNAME"):
        safe_source["username"] = os.getenv("INSIGHTMIND_DA_USERNAME")

    result = da_datasource_query(safe_source)
    result["dslExplain"] = explanation
    return result


if __name__ == "__main__":
    transport = os.getenv("INSIGHTMIND_MCP_TRANSPORT", "stdio")
    if transport not in {"stdio", "streamable-http", "sse"}:
        raise SystemExit(f"Unsupported INSIGHTMIND_MCP_TRANSPORT={transport!r}")
    mcp.run(transport=transport)
