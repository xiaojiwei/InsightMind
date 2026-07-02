"""Registered semantic formula measures for AD-Hoc/Dashboard.

The registry deliberately supports a small, auditable DSL instead of raw SQL:
measure references plus arithmetic operators. It is meant to bridge the current
KG metadata with user-configured derived metrics without broadening execution
to arbitrary expressions.
"""

from __future__ import annotations

import ast
import json
import re
import time
import uuid
from dataclasses import dataclass
from pathlib import Path
from typing import Any


REFERENCE_RE = re.compile(r"\[([A-Za-z0-9_.:-]+)\]|(?<![A-Za-z0-9_])(MEAS_[A-Za-z0-9_]+)(?![A-Za-z0-9_])")
CODE_RE = re.compile(r"[^A-Za-z0-9_]+")


class FormulaValidationError(ValueError):
    """Raised when a registered formula is outside the supported DSL."""


def _safe_code(value: str) -> str:
    cleaned = CODE_RE.sub("_", str(value or "").strip()).strip("_").lower()
    return cleaned[:72] or uuid.uuid4().hex[:12]


def _to_number(value: Any) -> float | None:
    if value in (None, ""):
        return None
    if isinstance(value, (int, float)):
        return float(value)
    text = str(value).replace(",", "").strip()
    if text in {"", "-", "nan", "None"}:
        return None
    try:
        return float(text)
    except ValueError:
        return None


def _eval_ast(node: ast.AST, values: dict[str, float | None]) -> float | None:
    if isinstance(node, ast.Expression):
        return _eval_ast(node.body, values)
    if isinstance(node, ast.Constant):
        if isinstance(node.value, (int, float)):
            return float(node.value)
        raise FormulaValidationError("公式仅支持数值常量")
    if isinstance(node, ast.Name):
        if node.id not in values:
            raise FormulaValidationError(f"未知公式变量: {node.id}")
        return values[node.id]
    if isinstance(node, ast.UnaryOp) and isinstance(node.op, (ast.UAdd, ast.USub)):
        value = _eval_ast(node.operand, values)
        if value is None:
            return None
        return value if isinstance(node.op, ast.UAdd) else -value
    if isinstance(node, ast.BinOp) and isinstance(node.op, (ast.Add, ast.Sub, ast.Mult, ast.Div)):
        left = _eval_ast(node.left, values)
        right = _eval_ast(node.right, values)
        if left is None or right is None:
            return None
        if isinstance(node.op, ast.Add):
            return left + right
        if isinstance(node.op, ast.Sub):
            return left - right
        if isinstance(node.op, ast.Mult):
            return left * right
        if right == 0:
            return None
        return left / right
    raise FormulaValidationError("公式仅支持 + - * / 和括号")


@dataclass
class CompiledFormula:
    expression: str
    dependencies: list[str]
    python_expression: str
    variable_by_code: dict[str, str]
    tree: ast.Expression

    def evaluate(self, row: dict[str, Any], member_name_by_code: dict[str, str]) -> float | None:
        values = {
            variable: _to_number(row.get(member_name_by_code.get(code, code)))
            for code, variable in self.variable_by_code.items()
        }
        return _eval_ast(self.tree, values)


class FormulaRegistry:
    def __init__(self, path: str | Path):
        self.path = Path(path)

    def list(self) -> list[dict[str, Any]]:
        if not self.path.exists():
            return []
        data = json.loads(self.path.read_text(encoding="utf-8") or "{}")
        formulas = data.get("formulas") if isinstance(data, dict) else []
        return [item for item in formulas if isinstance(item, dict)]

    def save(self, payload: dict[str, Any], catalog: dict[str, Any]) -> dict[str, Any]:
        formula = self.normalize(payload, catalog)
        formulas = [item for item in self.list() if item.get("code") != formula["code"]]
        formulas.append(formula)
        self.path.parent.mkdir(parents=True, exist_ok=True)
        self.path.write_text(json.dumps({"formulas": formulas}, ensure_ascii=False, indent=2), encoding="utf-8")
        return formula

    def delete(self, code: str) -> bool:
        formulas = self.list()
        kept = [item for item in formulas if item.get("code") != code]
        if len(kept) == len(formulas):
            return False
        self.path.parent.mkdir(parents=True, exist_ok=True)
        self.path.write_text(json.dumps({"formulas": kept}, ensure_ascii=False, indent=2), encoding="utf-8")
        return True

    def enrich_catalog(self, catalog: dict[str, Any]) -> dict[str, Any]:
        result = {
            **(catalog or {}),
            "measures": list((catalog or {}).get("measures") or []),
            "dimensions": list((catalog or {}).get("dimensions") or []),
        }
        for item in self.list():
            try:
                result["measures"].append(self.normalize(item, result, persist=False))
            except FormulaValidationError:
                continue
        return result

    def normalize(self, payload: dict[str, Any], catalog: dict[str, Any], persist: bool = True) -> dict[str, Any]:
        name = str(payload.get("name") or payload.get("title") or "").strip()
        expression = str(payload.get("expression") or "").strip()
        if not name:
            raise FormulaValidationError("公式指标名称不能为空")
        if not expression:
            raise FormulaValidationError("公式表达式不能为空")

        base_measures = [m for m in (catalog.get("measures") or []) if not m.get("formula")]
        by_code = {str(item.get("code") or ""): item for item in base_measures}
        by_alias: dict[str, str] = {}
        for item in base_measures:
            code = str(item.get("code") or "")
            if not code:
                continue
            aliases = {
                code,
                code.removeprefix("MEAS_").lower(),
                str(item.get("name") or ""),
                str(item.get("title") or ""),
            }
            for alias in aliases:
                if alias:
                    by_alias[alias] = code
                    by_alias[alias.lower()] = code

        compiled = compile_formula(expression, by_alias)
        missing = [code for code in compiled.dependencies if code not in by_code]
        if missing:
            raise FormulaValidationError(f"公式引用了不存在的指标: {', '.join(missing)}")

        code = str(payload.get("code") or "").strip()
        if not code:
            code = "MEAS_formula_" + _safe_code(name)
        if not code.startswith("MEAS_"):
            code = "MEAS_" + _safe_code(code)

        dep_items = [by_code[code] for code in compiled.dependencies]
        dim_sets = [set(item.get("dimensionCodes") or []) for item in dep_items]
        common_dims = sorted(set.intersection(*dim_sets)) if dim_sets else []
        tables = sorted({table for item in dep_items for table in (item.get("tables") or [])})
        unit = str(payload.get("unit") or "").strip()
        if not unit and len({str(item.get("unit") or "") for item in dep_items}) == 1:
            unit = str(dep_items[0].get("unit") or "")

        lod = payload.get("lod") if isinstance(payload.get("lod"), dict) else {}
        lod_type = str(lod.get("type") or "none").lower()
        if lod_type not in {"none", "fixed", "exclude"}:
            raise FormulaValidationError("LOD 类型仅支持 none/fixed/exclude")

        now = time.strftime("%Y-%m-%d %H:%M:%S")
        created = payload.get("createdAt") or now
        formula = {
            "code": code,
            "name": name,
            "title": name,
            "shortTitle": name,
            "type": "number",
            "unit": unit,
            "caliber": payload.get("caliber") or f"注册公式指标：{expression}",
            "tables": tables,
            "dimensionCodes": common_dims,
            "dimensionReasons": _merge_dimension_reasons(dep_items, common_dims),
            "formula": True,
            "expression": expression,
            "dependencies": compiled.dependencies,
            "lod": {"type": lod_type, "dimensions": list(lod.get("dimensions") or [])},
            "owner": payload.get("owner") or "",
            "status": payload.get("status") or "active",
            "createdAt": created,
            "updatedAt": now if persist else payload.get("updatedAt") or now,
        }
        return formula


def _merge_dimension_reasons(measures: list[dict[str, Any]], dimension_codes: list[str]) -> dict[str, Any]:
    result = {}
    for code in dimension_codes:
        reasons = [m.get("dimensionReasons", {}).get(code) for m in measures if isinstance(m.get("dimensionReasons"), dict)]
        reasons = [r for r in reasons if r]
        result[code] = reasons[0] if reasons else {
            "ruleId": "formula.common_dependency_dimension",
            "confidence": "1.0",
            "evidencePath": "公式依赖指标共同支持该维度",
        }
    return result


def compile_formula(expression: str, aliases: dict[str, str] | None = None) -> CompiledFormula:
    aliases = aliases or {}
    dependencies: list[str] = []
    variable_by_code: dict[str, str] = {}

    def replace(match: re.Match[str]) -> str:
        raw = (match.group(1) or match.group(2) or "").strip()
        key = raw.rsplit(".", 1)[-1] if "." in raw else raw
        code = aliases.get(raw) or aliases.get(raw.lower()) or aliases.get(key) or aliases.get(key.lower()) or raw
        if not str(code).startswith("MEAS_"):
            code = "MEAS_" + _safe_code(str(code))
        if code not in dependencies:
            dependencies.append(code)
        variable = variable_by_code.setdefault(code, f"m{len(variable_by_code)}")
        return variable

    python_expr = REFERENCE_RE.sub(replace, expression)
    try:
        tree = ast.parse(python_expr, mode="eval")
        _eval_ast(tree, {variable: 1.0 for variable in variable_by_code.values()})
    except FormulaValidationError:
        raise
    except Exception as exc:
        raise FormulaValidationError(f"公式语法不合法: {exc}") from exc
    if not dependencies:
        raise FormulaValidationError("公式至少需要引用一个指标，格式如 [MEAS_sales_amount]")
    return CompiledFormula(expression, dependencies, python_expr, variable_by_code, tree)
