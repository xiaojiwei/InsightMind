"""Cube-like semantic query facade for AD/DataAgent.

This module keeps the public API shape friendly for dashboards while preserving
the existing DA payload contract under the hood.
"""

from __future__ import annotations

from dataclasses import dataclass
from typing import Any, Callable

from .formula_registry import FormulaValidationError, compile_formula


TIME_GRANULARITY_VIEW_TYPES = {
    "day": 1,
    "date": 1,
    "week": 2,
    "month": 3,
    "quarter": 4,
    "year": 5,
}

CUBE_OPERATOR_TO_AD = {
    "equals": "in",
    "notEquals": "not_in",
    "contains": "like",
    "notContains": "not_like",
    "gt": "greater_than",
    "gte": "greater_than_or_equal",
    "lt": "less_than",
    "lte": "less_than_or_equal",
    "inDateRange": "between",
    "beforeDate": "less_than",
    "beforeOrOnDate": "less_than_or_equal",
    "afterDate": "greater_than",
    "afterOrOnDate": "greater_than_or_equal",
    "set": "not_equal",
    "notSet": "equal",
}


def normalize_member_key(code: str) -> str:
    """Turn MEAS_/DIM_ codes into stable front-end member keys."""
    value = str(code or "").strip()
    if value.startswith("MEAS_"):
        value = value[5:]
    elif value.startswith("DIM_"):
        value = value[4:]
    return value.lower()


def _member_name(model: str, code: str) -> str:
    return f"{model}.{normalize_member_key(code)}"


def _dimension_group_key(item: dict[str, Any]) -> tuple[Any, ...]:
    """Group DA dimension applications that represent the same business field."""
    name = str(item.get("name") or "").strip()
    if item.get("isTime"):
        hierarchy = str(item.get("hierarchyCode") or "").strip()
        level = str(item.get("levelCode") or "").strip()
        view_type = int(item.get("viewType") or 0)
        return ("time", hierarchy or name, level or view_type or name, view_type)
    hierarchy = str(item.get("hierarchyCode") or "").strip()
    level = str(item.get("levelCode") or "").strip()
    return ("dimension", name, hierarchy, level)


def _public_dimension_code(items: list[dict[str, Any]]) -> str:
    """Choose a stable public code for a group of compatible DA dimension codes."""
    first = items[0]
    if first.get("isTime"):
        level = str(first.get("levelCode") or "").strip().lower()
        hierarchy = str(first.get("hierarchyCode") or "").strip().lower()
        if hierarchy and level:
            base = hierarchy[2:] if hierarchy.startswith("h_") else hierarchy
            return "DIM_" + base + "_" + level
    return min((str(item.get("code") or "") for item in items), key=len)


def _logical_dimension_items(dimensions: list[dict[str, Any]]) -> list[dict[str, Any]]:
    groups: dict[tuple[Any, ...], list[dict[str, Any]]] = {}
    for item in dimensions:
        groups.setdefault(_dimension_group_key(item), []).append(item)

    result = []
    for group_items in groups.values():
        group_items = sorted(group_items, key=lambda item: str(item.get("code") or ""))
        public_code = _public_dimension_code(group_items)
        source_codes = [str(item.get("code") or "") for item in group_items if item.get("code")]
        tables = sorted({table for item in group_items for table in (item.get("tables") or [])})
        merged = {
            **group_items[0],
            "code": public_code,
            "publicCode": public_code,
            "sourceCodes": source_codes,
            "tables": tables,
            "hasDimColumnExpr": any(bool(item.get("hasDimColumnExpr")) for item in group_items),
        }
        result.append(merged)
    return sorted(result, key=lambda item: (not item.get("isTime"), item.get("name") or ""))


def build_meta(catalog: dict[str, Any], model_name: str = "ad") -> dict[str, Any]:
    """Build Cube-like metadata from the existing AD pivot catalog."""
    logical_dimensions = _logical_dimension_items(catalog.get("dimensions") or [])

    def expanded_dimension_codes(item: dict[str, Any]) -> list[str]:
        codes = {str(code) for code in (item.get("dimensionCodes") or []) if code}
        for dimension in logical_dimensions:
            source_codes = {str(code) for code in (dimension.get("sourceCodes") or []) if code}
            if codes & source_codes:
                codes.add(str(dimension.get("code") or ""))
        return sorted(codes)

    def expanded_dimension_reasons(item: dict[str, Any]) -> dict[str, Any]:
        reasons = dict(item.get("dimensionReasons") or {})
        for dimension in logical_dimensions:
            public_code = str(dimension.get("code") or "")
            source_codes = [str(code) for code in (dimension.get("sourceCodes") or []) if code]
            for source_code in source_codes:
                if source_code in reasons and public_code not in reasons:
                    reasons[public_code] = reasons[source_code]
                    break
        return reasons

    measures = []
    for item in catalog.get("measures") or []:
        code = str(item.get("code") or "")
        measures.append({
            "name": _member_name(model_name, code),
            "title": item.get("name") or code,
            "shortTitle": item.get("name") or code,
            "type": "number",
            "aggType": "sum",
            "code": code,
            "unit": item.get("unit") or "",
            "caliber": item.get("caliber") or "",
            "tables": item.get("tables") or [],
            "dimensionCodes": expanded_dimension_codes(item),
            "dimensionReasons": expanded_dimension_reasons(item),
            "formula": bool(item.get("formula")),
            "expression": item.get("expression") or "",
            "dependencies": item.get("dependencies") or [],
            "lod": item.get("lod") or {"type": "none", "dimensions": []},
        })

    dimensions = []
    time_dimensions = []
    for item in logical_dimensions:
        code = str(item.get("code") or "")
        target = time_dimensions if item.get("isTime") else dimensions
        target.append({
            "name": _member_name(model_name, code),
            "title": item.get("name") or code,
            "shortTitle": item.get("name") or code,
            "type": "time" if item.get("isTime") else "string",
            "code": code,
            "viewType": item.get("viewType") or 0,
            "levelCode": item.get("levelCode") or "",
            "hierarchyCode": item.get("hierarchyCode") or "",
            "granularities": _granularities_for_dimension(item),
            "tables": item.get("tables") or [],
            "sourceCodes": item.get("sourceCodes") or [code],
        })

    return {
        "models": [{
            "name": model_name,
            "title": "AD 语义模型",
            "measures": measures,
            "dimensions": dimensions,
            "timeDimensions": time_dimensions,
        }],
        "cubes": [{
            "name": model_name,
            "title": "AD 语义模型",
            "measures": measures,
            "dimensions": dimensions + time_dimensions,
            "segments": [],
        }],
    }


def _granularities_for_dimension(item: dict[str, Any]) -> list[str]:
    if not item.get("isTime"):
        return []
    view_type = int(item.get("viewType") or 0)
    names = {1: "day", 2: "week", 3: "month", 4: "quarter", 5: "year"}
    return [names[view_type]] if view_type in names else []


@dataclass
class AdSemanticService:
    catalog: dict[str, Any]
    da_query: Callable[[dict[str, Any]], dict[str, Any]]
    da_filter_builder: Callable[[Any], list[dict[str, Any]]]
    model_name: str = "ad"

    def meta(self) -> dict[str, Any]:
        return build_meta(self.catalog, self.model_name)

    def translate_query(self, query: dict[str, Any]) -> dict[str, Any]:
        query = dict(query or {})
        measures = self._resolve_members(query.get("measures"), "measure")
        dimensions = self._resolve_dimension_members(query.get("dimensions"), measures)
        filters = self._convert_filters(query.get("filters") or [], measures)

        time_dimensions = []
        for item in query.get("timeDimensions") or []:
            if not isinstance(item, dict):
                continue
            time_dimensions.extend(self._convert_time_dimension(item, measures))

        configure_items = self._configure_items(measures, dimensions, time_dimensions, query)
        da_filters = self.da_filter_builder(filters + (query.get("_adFiltersForDa") or []))
        page_size = int(query.get("limit") or query.get("pageSize") or 1000)
        page_num = int(query.get("pageNum") or query.get("pageNo") or 1)
        payload = {
            "configureList": configure_items,
            "filterList": da_filters,
            "pageSize": max(1, min(page_size, 10000)),
            "pageNum": max(1, page_num),
        }
        if query.get("useCache") is not None:
            payload["useCache"] = bool(query.get("useCache"))
        if query.get("cacheStrategy") is not None:
            payload["cacheStrategy"] = query.get("cacheStrategy")
        return payload

    def load(self, query: dict[str, Any]) -> dict[str, Any]:
        formula_measures = [
            item for item in self._resolve_members((query or {}).get("measures"), "measure")
            if item.get("formula")
        ]
        if formula_measures:
            return self._load_with_formulas(query, formula_measures)
        payload = self.translate_query(query)
        result = self.da_query(payload)
        return self.normalize_result(query, payload, result)

    def _load_with_formulas(self, query: dict[str, Any], formula_measures: list[dict[str, Any]]) -> dict[str, Any]:
        query = dict(query or {})
        requested_measures = self._resolve_members(query.get("measures"), "measure")
        dependency_codes: list[str] = []
        requested_non_formula_codes: list[str] = []
        for measure in requested_measures:
            code = measure.get("code")
            if not code:
                continue
            if measure.get("formula"):
                for dep_code in measure.get("dependencies") or []:
                    if dep_code not in dependency_codes:
                        dependency_codes.append(dep_code)
            else:
                requested_non_formula_codes.append(code)
                if code not in dependency_codes:
                    dependency_codes.append(code)

        if not dependency_codes:
            raise FormulaValidationError("公式指标缺少依赖指标")

        aliases = self._formula_aliases()
        base_query = {
            **query,
            "measures": [self._code_to_member_name(code) for code in dependency_codes],
            "order": self._base_order_for_formula_query(query.get("order") or {}, set(dependency_codes)),
        }
        payload = self.translate_query(base_query)
        da_result = self.da_query(payload)
        result = self.normalize_result(base_query, payload, da_result)

        requested_name_by_code = self._requested_measure_name_by_code(query)
        member_name_by_code = {code: self._code_to_member_name(code) for code in dependency_codes}
        keep_measure_names = {self._code_to_member_name(code) for code in requested_non_formula_codes}
        for formula in formula_measures:
            compiled = compile_formula(str(formula.get("expression") or ""), aliases)
            output_name = requested_name_by_code.get(formula["code"]) or self._code_to_member_name(formula["code"])
            keep_measure_names.add(output_name)
            lod = formula.get("lod") or {}
            lod_type = str(lod.get("type") or "none").lower()
            if lod_type in {"fixed", "exclude"}:
                self._apply_lod_formula(query, result, formula, compiled, output_name)
            else:
                for row in result.get("data") or []:
                    row[output_name] = compiled.evaluate(row, member_name_by_code)

        dependency_member_names = {self._code_to_member_name(code) for code in dependency_codes}
        for row in result.get("data") or []:
            for key in list(row.keys()):
                if key.startswith(f"{self.model_name}.") and key in dependency_member_names and key not in keep_measure_names:
                    row.pop(key, None)

        result["query"] = query
        result["formulaDiagnostics"] = {
            "formulaMeasures": [item.get("code") for item in formula_measures],
            "expandedMeasures": dependency_codes,
        }
        result["annotation"] = self._annotation()
        return result

    def _apply_lod_formula(
        self,
        query: dict[str, Any],
        result: dict[str, Any],
        formula: dict[str, Any],
        compiled: Any,
        output_name: str,
    ) -> None:
        dependency_codes = list(formula.get("dependencies") or [])
        lod = formula.get("lod") or {}
        lod_dimensions = [str(value) for value in (lod.get("dimensions") or []) if value]
        current_dimensions = [str(value) for value in (query.get("dimensions") or []) if value]
        formula_dimensions = self._formula_lod_dimensions(str(lod.get("type") or "none"), lod_dimensions, current_dimensions)
        formula_query = {
            **query,
            "measures": [self._code_to_member_name(code) for code in dependency_codes],
            "dimensions": formula_dimensions,
            "order": {},
            "limit": query.get("limit") or 10000,
        }
        payload = self.translate_query(formula_query)
        lod_result = self.normalize_result(formula_query, payload, self.da_query(payload))
        member_name_by_code = {code: self._code_to_member_name(code) for code in dependency_codes}
        key_members = formula_dimensions
        values_by_key: dict[tuple[str, ...], float | None] = {}
        fallback = None
        for lod_row in lod_result.get("data") or []:
            value = compiled.evaluate(lod_row, member_name_by_code)
            key = tuple(str(lod_row.get(member, "")) for member in key_members)
            values_by_key[key] = value
            if fallback is None:
                fallback = value

        for row in result.get("data") or []:
            if key_members and all(member in row for member in key_members):
                key = tuple(str(row.get(member, "")) for member in key_members)
                row[output_name] = values_by_key.get(key)
            elif len(values_by_key) == 1:
                row[output_name] = fallback
            else:
                row[output_name] = None

    def _formula_lod_dimensions(
        self,
        lod_type: str,
        lod_dimensions: list[str],
        current_dimensions: list[str],
    ) -> list[str]:
        lod_members = []
        for value in lod_dimensions:
            member = self._resolve_dimension_member(value, [])
            if member:
                lod_members.append(self._code_to_member_name(member["code"]))
        if lod_type == "fixed":
            return self._dedupe_strings(lod_members)
        if lod_type == "exclude":
            excluded = {self._resolve_dimension_member(value, [])["code"] for value in lod_dimensions if self._resolve_dimension_member(value, [])}
            kept = []
            for value in current_dimensions:
                member = self._resolve_dimension_member(value, [])
                if not member or member.get("code") in excluded:
                    continue
                kept.append(self._code_to_member_name(member["code"]))
            return self._dedupe_strings(kept)
        return current_dimensions

    def normalize_result(
        self,
        query: dict[str, Any],
        payload: dict[str, Any],
        da_result: dict[str, Any],
    ) -> dict[str, Any]:
        da_data = da_result.get("data") or {}
        rows = []
        for raw_row in da_data.get("cellList") or []:
            if not isinstance(raw_row, list):
                continue
            row = {}
            for cell in raw_row:
                if not isinstance(cell, dict):
                    continue
                code = str(cell.get("code") or "")
                if not code:
                    continue
                member_name = self._result_member_name_by_code(query).get(code) or self._code_to_member_name(code)
                row[member_name] = cell.get("data")
                if cell.get("type") == "DIMENSION" and cell.get("id") not in (None, ""):
                    row.setdefault("__filterValues", {})[member_name] = cell.get("id")
            if row:
                rows.append(row)
        return {
            "data": rows,
            "annotation": self._annotation(),
            "query": query,
            "daPayload": payload,
            "diagnostics": {
                "elapsedMs": da_data.get("cost"),
                "rowCount": len(rows),
                "reviewSql": da_data.get("reviewSql") or "",
                "cacheHit": da_data.get("cacheHit"),
            },
        }

    def chart(self, query: dict[str, Any]) -> dict[str, Any]:
        result = self.load(query)
        result["chart"] = {
            "chartType": query.get("chartType") or self._recommend_chart(query),
            "encoding": self._chart_encoding(query),
        }
        return result

    def _resolve_members(self, values: Any, kind: str) -> list[dict[str, Any]]:
        result = []
        for value in values if isinstance(values, list) else []:
            resolved = self._resolve_member(value, kind)
            if resolved and resolved["code"] not in {item["code"] for item in result}:
                result.append(resolved)
        return result

    def _resolve_dimension_members(self, values: Any, measures: list[dict[str, Any]]) -> list[dict[str, Any]]:
        result = []
        seen = set()
        for value in values if isinstance(values, list) else []:
            resolved = self._resolve_dimension_member(value, measures)
            if resolved and resolved["code"] not in seen:
                seen.add(resolved["code"])
                result.append(resolved)
        return result

    def _resolve_member(self, value: Any, kind: str | None = None) -> dict[str, Any] | None:
        if isinstance(value, dict):
            raw = str(value.get("name") or value.get("member") or value.get("code") or "").strip()
        else:
            raw = str(value or "").strip()
        if not raw:
            return None
        items = (self.catalog.get("measures") or []) + (self.catalog.get("dimensions") or [])
        if kind == "measure":
            items = self.catalog.get("measures") or []
        elif kind == "dimension":
            items = self.catalog.get("dimensions") or []
        aliases = [raw]
        if "." in raw:
            aliases.append(raw.rsplit(".", 1)[-1])
        for item in items:
            code = str(item.get("code") or "")
            names = {
                code,
                _member_name(self.model_name, code),
                normalize_member_key(code),
                str(item.get("name") or ""),
            }
            if raw in names or any(alias in names for alias in aliases):
                return item
        return None

    def _formula_aliases(self) -> dict[str, str]:
        aliases: dict[str, str] = {}
        for item in self.catalog.get("measures") or []:
            code = str(item.get("code") or "")
            if not code:
                continue
            for alias in {
                code,
                _member_name(self.model_name, code),
                normalize_member_key(code),
                str(item.get("name") or ""),
                str(item.get("title") or ""),
            }:
                if alias:
                    aliases[alias] = code
                    aliases[alias.lower()] = code
        return aliases

    def _requested_measure_name_by_code(self, query: dict[str, Any]) -> dict[str, str]:
        mapping = {}
        for value in query.get("measures") if isinstance(query.get("measures"), list) else []:
            member = self._resolve_member(value, "measure")
            if member:
                mapping[member["code"]] = str(
                    value if not isinstance(value, dict)
                    else value.get("name") or value.get("member") or value.get("code")
                )
        return mapping

    def _base_order_for_formula_query(self, order: Any, dependency_codes: set[str]) -> Any:
        if isinstance(order, dict):
            result = {}
            for key, value in order.items():
                member = self._resolve_member(key)
                if member and member.get("code", "") not in dependency_codes and member.get("code", "").startswith("MEAS_"):
                    continue
                result[key] = value
            return result
        return order

    def _logical_dimensions(self) -> list[dict[str, Any]]:
        return _logical_dimension_items(self.catalog.get("dimensions") or [])

    def _resolve_dimension_member(self, value: Any, measures: list[dict[str, Any]] | None = None) -> dict[str, Any] | None:
        raw = str(
            (value.get("name") or value.get("member") or value.get("code") if isinstance(value, dict) else value) or ""
        ).strip()
        if not raw:
            return None
        aliases = [raw]
        if "." in raw:
            aliases.append(raw.rsplit(".", 1)[-1])

        source_items = self.catalog.get("dimensions") or []
        logical_items = self._logical_dimensions()
        for logical in logical_items:
            public_code = str(logical.get("code") or "")
            names = {
                public_code,
                _member_name(self.model_name, public_code),
                normalize_member_key(public_code),
                str(logical.get("name") or ""),
            }
            names.update(str(code) for code in logical.get("sourceCodes") or [])
            names.update(_member_name(self.model_name, str(code)) for code in logical.get("sourceCodes") or [])
            names.update(normalize_member_key(str(code)) for code in logical.get("sourceCodes") or [])
            if raw in names or any(alias in names for alias in aliases):
                return self._choose_dimension_source(logical, source_items, measures or [])
        return self._resolve_member(raw, "dimension")

    def _choose_dimension_source(
        self,
        logical: dict[str, Any],
        source_items: list[dict[str, Any]],
        measures: list[dict[str, Any]],
    ) -> dict[str, Any]:
        by_code = {str(item.get("code") or ""): item for item in source_items}
        candidates = [by_code[code] for code in (logical.get("sourceCodes") or []) if code in by_code]
        if not candidates:
            return logical
        if not measures:
            return candidates[0]

        compatible_sets = [set(measure.get("dimensionCodes") or []) for measure in measures]
        common_codes = set.intersection(*compatible_sets) if compatible_sets else set()
        for candidate in candidates:
            if candidate.get("code") in common_codes:
                return candidate
        if len(measures) > 1:
            source_codes = {str(candidate.get("code") or "") for candidate in candidates}
            if all(source_codes & set(measure.get("dimensionCodes") or []) for measure in measures):
                return logical
        for candidate in candidates:
            if any(candidate.get("code") in codes for codes in compatible_sets):
                return candidate
        return candidates[0]

    def _convert_filters(self, filters: list[Any], measures: list[dict[str, Any]] | None = None) -> list[dict[str, Any]]:
        converted = []
        for item in filters:
            if not isinstance(item, dict) or "member" not in item:
                continue
            member = self._resolve_dimension_member(item.get("member"), measures or []) or self._resolve_member(item.get("member"))
            if not member:
                continue
            operator = CUBE_OPERATOR_TO_AD.get(str(item.get("operator") or "equals"), "in")
            values = item.get("values") or []
            if not isinstance(values, list):
                values = [values]
            converted.append({
                "code": member["code"],
                "operator": operator,
                "values": [str(value) for value in values if value is not None],
                "viewType": member.get("viewType") or 0,
                "filterMode": "time" if member.get("isTime") else "enum",
            })
        return converted

    def _convert_time_dimension(
        self,
        item: dict[str, Any],
        measures: list[dict[str, Any]],
    ) -> list[dict[str, Any]]:
        member = self._resolve_dimension_member(item.get("dimension"), measures)
        if not member:
            return []
        selected = []
        granularity = str(item.get("granularity") or "").strip()
        if granularity:
            grouped_member = self._find_time_dimension_for_granularity(member, granularity, measures)
            if grouped_member:
                selected.append(grouped_member)
        date_range = item.get("dateRange")
        if date_range:
            values = date_range if isinstance(date_range, list) else [date_range]
            if len(values) == 1:
                values = [values[0], values[0]]
            filter_member = self._find_time_dimension_for_granularity(member, "day", measures) or member
            selected.append({
                **filter_member,
                "_asFilter": {
                    "code": filter_member["code"],
                    "operator": "between",
                    "values": [str(values[0]), str(values[1])],
                    "viewType": filter_member.get("viewType") or 0,
                    "filterMode": "date",
                },
            })
        return selected

    def _find_time_dimension_for_granularity(
        self,
        base: dict[str, Any],
        granularity: str,
        measures: list[dict[str, Any]],
    ) -> dict[str, Any] | None:
        view_type = TIME_GRANULARITY_VIEW_TYPES.get(granularity)
        if not view_type:
            return base
        base_hierarchy = base.get("hierarchyCode")
        base_tables = set(base.get("tables") or [])
        measure_tables = set()
        for measure in measures:
            measure_tables.update(measure.get("tables") or [])
        for candidate in self.catalog.get("dimensions") or []:
            if not candidate.get("isTime") or int(candidate.get("viewType") or 0) != view_type:
                continue
            if base_hierarchy and candidate.get("hierarchyCode") != base_hierarchy:
                continue
            candidate_tables = set(candidate.get("tables") or [])
            if measure_tables and not (candidate_tables & measure_tables):
                continue
            if base_tables and not (candidate_tables & base_tables):
                continue
            return candidate
        return base if int(base.get("viewType") or 0) == view_type else None

    def _configure_items(
        self,
        measures: list[dict[str, Any]],
        dimensions: list[dict[str, Any]],
        time_dimensions: list[dict[str, Any]],
        query: dict[str, Any],
    ) -> list[dict[str, Any]]:
        time_filters = [item.get("_asFilter") for item in time_dimensions if item.get("_asFilter")]
        query["_adInternalFilters"] = (query.get("_adInternalFilters") or []) + [f for f in time_filters if f]
        selected_dimensions = dimensions + [item for item in time_dimensions if not item.get("_asFilter")]
        selected_dimensions = self._dedupe_by_code(selected_dimensions)
        self._validate_compatibility(measures, selected_dimensions)

        order = query.get("order") or {}
        configure = []
        for item in measures:
            configure.append({
                "code": item["code"],
                "order": {"sortType": self._sort_type_for_member(item["code"], order, default=0)},
                "ratioList": [],
                "alias": "",
            })
        for item in selected_dimensions:
            configure.append({
                "code": item["code"],
                "order": {"sortType": self._sort_type_for_member(item["code"], order, default=1 if item.get("isTime") else 0)},
                "ratioList": [],
                "alias": "",
                "hasSubtotal": False,
            })
        query_filters = query.get("_adInternalFilters") or []
        query["_adFiltersForDa"] = query_filters
        return configure

    def _validate_compatibility(self, measures: list[dict[str, Any]], dimensions: list[dict[str, Any]]) -> None:
        for measure in measures:
            bad = [
                dim.get("name") or dim.get("code")
                for dim in dimensions
                if not self._dimension_compatible_with_measure(dim, measure)
            ]
            if bad:
                raise ValueError(f"指标「{measure.get('name') or measure['code']}」与维度 {', '.join(bad)} 没有共用事实表")

    def _dimension_compatible_with_measure(self, dimension: dict[str, Any], measure: dict[str, Any]) -> bool:
        compatible = {str(code) for code in (measure.get("dimensionCodes") or []) if code}
        dimension_codes = {str(dimension.get("code") or "")}
        dimension_codes.update(str(code) for code in (dimension.get("sourceCodes") or []) if code)
        if compatible & dimension_codes:
            return True

        measure_tables = {str(table) for table in (measure.get("tables") or []) if table}
        dimension_tables = {str(table) for table in (dimension.get("tables") or []) if table}
        return bool(measure_tables & dimension_tables)

    def _sort_type_for_member(self, code: str, order: Any, default: int = 0) -> int:
        if isinstance(order, dict):
            value = order.get(code) or order.get(self._code_to_member_name(code))
            if str(value).lower() == "desc":
                return 0
            if str(value).lower() == "asc":
                return 1
        if isinstance(order, list):
            for entry in order:
                if isinstance(entry, list) and len(entry) >= 2:
                    member, direction = entry[0], entry[1]
                elif isinstance(entry, dict):
                    member, direction = entry.get("member"), entry.get("direction")
                else:
                    continue
                resolved = self._resolve_member(member)
                if resolved and resolved.get("code") == code:
                    return 1 if str(direction).lower() == "asc" else 0
        return default

    def _annotation(self) -> dict[str, Any]:
        meta = self.meta()["models"][0]
        return {
            "measures": {item["name"]: item for item in meta["measures"]},
            "dimensions": {item["name"]: item for item in meta["dimensions"]},
            "timeDimensions": {item["name"]: item for item in meta["timeDimensions"]},
        }

    def _code_to_member_name(self, code: str) -> str:
        public_code = self._public_code_for_source_code(code) or code
        return _member_name(self.model_name, public_code)

    def _public_code_for_source_code(self, code: str) -> str | None:
        code = str(code or "")
        for item in self._logical_dimensions():
            if code == item.get("code") or code in (item.get("sourceCodes") or []):
                return str(item.get("code") or "")
        return None

    def _result_member_name_by_code(self, query: dict[str, Any]) -> dict[str, str]:
        mapping = {}
        measures = self._resolve_members(query.get("measures"), "measure")
        for value in query.get("measures") if isinstance(query.get("measures"), list) else []:
            member = self._resolve_member(value, "measure")
            if member:
                mapping[member["code"]] = str(value if not isinstance(value, dict) else value.get("name") or value.get("member") or value.get("code"))
        for value in query.get("dimensions") if isinstance(query.get("dimensions"), list) else []:
            member = self._resolve_dimension_member(value, measures)
            if member:
                mapping[member["code"]] = str(value if not isinstance(value, dict) else value.get("name") or value.get("member") or value.get("code"))
        for item in query.get("timeDimensions") or []:
            if not isinstance(item, dict):
                continue
            for member in self._convert_time_dimension(item, measures):
                if member.get("_asFilter"):
                    continue
                mapping[member["code"]] = self._code_to_member_name(member["code"])
        return mapping

    def _recommend_chart(self, query: dict[str, Any]) -> str:
        dimensions = query.get("dimensions") or []
        time_dimensions = [item for item in query.get("timeDimensions") or [] if isinstance(item, dict) and item.get("granularity")]
        measures = query.get("measures") or []
        if len(measures) == 1 and not dimensions and not time_dimensions:
            return "kpi"
        if time_dimensions:
            return "line"
        if len(dimensions) == 1:
            return "bar"
        if len(dimensions) >= 2:
            return "heatmap"
        return "table"

    def _chart_encoding(self, query: dict[str, Any]) -> dict[str, Any]:
        measures = query.get("measures") or []
        dimensions = query.get("dimensions") or []
        time_dimensions = query.get("timeDimensions") or []
        x_axis = None
        if time_dimensions:
            first_time = next((item for item in time_dimensions if isinstance(item, dict)), {})
            x_axis = first_time.get("dimension")
        elif dimensions:
            x_axis = dimensions[0]
        return {
            "x": x_axis,
            "y": measures[0] if measures else None,
            "series": dimensions[1] if len(dimensions) > 1 else None,
        }

    @staticmethod
    def _dedupe_by_code(items: list[dict[str, Any]]) -> list[dict[str, Any]]:
        result = []
        seen = set()
        for item in items:
            code = item.get("code")
            if code and code not in seen:
                seen.add(code)
                result.append(item)
        return result

    @staticmethod
    def _dedupe_strings(items: list[str]) -> list[str]:
        result = []
        seen = set()
        for item in items:
            if item and item not in seen:
                seen.add(item)
                result.append(item)
        return result
