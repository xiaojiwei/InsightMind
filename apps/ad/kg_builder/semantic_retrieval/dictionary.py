"""Governed semantic dictionary with legacy synonym compatibility."""

from __future__ import annotations

from dataclasses import dataclass, field
from pathlib import Path
from typing import Any, Iterable

import yaml

from .normalizer import normalize_text, stable_hash, unique_strings


_ENABLED_STATUSES = {"ENABLED", "APPROVED", "ACTIVE", "PUBLISHED"}


@dataclass(frozen=True)
class DictionaryAlias:
    term: str
    semantic_type: str
    canonical_code: str
    source: str = "manual"
    status: str = "ENABLED"
    domain_code: str = "default"
    weight: float = 0.96
    metadata: dict[str, Any] = field(default_factory=dict, compare=False, hash=False)


@dataclass(frozen=True)
class DictionaryValue:
    term: str
    dimension_code: str
    canonical_value: str
    source: str = "manual"
    status: str = "ENABLED"
    domain_code: str = "default"
    weight: float = 0.97
    metadata: dict[str, Any] = field(default_factory=dict, compare=False, hash=False)


@dataclass(frozen=True)
class LegacySynonymGroup:
    canonical_term: str
    aliases: tuple[str, ...]
    source: str = "legacy_synonym"


@dataclass
class DictionaryBundle:
    aliases: list[DictionaryAlias] = field(default_factory=list)
    values: list[DictionaryValue] = field(default_factory=list)
    value_policies: dict[str, str] = field(default_factory=dict)
    value_policy_metadata: dict[str, dict[str, Any]] = field(default_factory=dict)
    legacy_groups: list[LegacySynonymGroup] = field(default_factory=list)
    warnings: list[str] = field(default_factory=list)
    version: str = "1"

    @property
    def dictionary_hash(self) -> str:
        return stable_hash({
            "version": self.version,
            "aliases": [
                {
                    "term": row.term,
                    "semanticType": row.semantic_type,
                    "canonicalCode": row.canonical_code,
                    "source": row.source,
                    "status": row.status,
                    "domainCode": row.domain_code,
                    "weight": row.weight,
                    "metadata": row.metadata,
                }
                for row in self.aliases
            ],
            "values": [
                {
                    "term": row.term,
                    "dimensionCode": row.dimension_code,
                    "canonicalValue": row.canonical_value,
                    "source": row.source,
                    "status": row.status,
                    "domainCode": row.domain_code,
                    "weight": row.weight,
                    "metadata": row.metadata,
                }
                for row in self.values
            ],
            "valuePolicies": dict(sorted(self.value_policies.items())),
            "valuePolicyMetadata": {
                code: self.value_policy_metadata[code]
                for code in sorted(self.value_policy_metadata)
            },
            "legacy": [
                {
                    "canonical": row.canonical_term,
                    "aliases": list(row.aliases),
                    "source": row.source,
                }
                for row in self.legacy_groups
            ],
        })


def _field(row: dict[str, Any], *names: str, default: Any = "") -> Any:
    for name in names:
        if name in row and row[name] is not None:
            return row[name]
    return default


def _enabled(row: dict[str, Any], *, default: str = "ENABLED") -> tuple[bool, str]:
    status = str(_field(row, "status", default=default) or default).strip().upper()
    return status in _ENABLED_STATUSES, status


def _parse_alias(row: Any, *, source: str) -> DictionaryAlias | None:
    if not isinstance(row, dict):
        return None
    enabled, status = _enabled(row, default="PENDING")
    if not enabled:
        return None
    term = str(_field(row, "term", "termText", "term_text") or "").strip()
    semantic_type = str(_field(row, "semanticType", "semantic_type") or "").strip().lower()
    code = str(_field(row, "canonicalCode", "canonical_code", "code") or "").strip()
    if semantic_type not in {"measure", "dimension"}:
        if code.upper().startswith("MEAS_"):
            semantic_type = "measure"
        elif code.upper().startswith("DIM_"):
            semantic_type = "dimension"
    if not term or semantic_type not in {"measure", "dimension"} or not code:
        return None
    try:
        weight = float(_field(row, "weight", default=0.96))
    except (TypeError, ValueError):
        weight = 0.96
    nested_metadata = row.get("metadata") if isinstance(row.get("metadata"), dict) else {}
    metadata = dict(nested_metadata)
    metadata.update({
        key: value for key, value in row.items()
        if key not in {
            "term", "termText", "term_text", "semanticType", "semantic_type",
            "canonicalCode", "canonical_code", "code", "source", "status",
            "domainCode", "domain_code", "weight", "metadata",
        }
    })
    return DictionaryAlias(
        term=term,
        semantic_type=semantic_type,
        canonical_code=code,
        source=str(_field(row, "source", default=source) or source),
        status=status,
        domain_code=str(_field(row, "domainCode", "domain_code", default="default") or "default"),
        weight=max(0.0, min(weight, 1.0)),
        metadata=metadata,
    )


def _parse_value(row: Any, *, source: str) -> DictionaryValue | None:
    if not isinstance(row, dict):
        return None
    enabled, status = _enabled(row, default="PENDING")
    if not enabled:
        return None
    term = str(_field(row, "term", "termText", "term_text") or "").strip()
    dimension_code = str(_field(row, "dimensionCode", "dimension_code") or "").strip()
    canonical = str(_field(row, "canonicalValue", "canonical_value", "value") or "").strip()
    if not term or not dimension_code.upper().startswith("DIM_") or not canonical:
        return None
    try:
        weight = float(_field(row, "weight", default=0.97))
    except (TypeError, ValueError):
        weight = 0.97
    nested_metadata = row.get("metadata") if isinstance(row.get("metadata"), dict) else {}
    metadata = dict(nested_metadata)
    metadata.update({
        key: value for key, value in row.items()
        if key not in {
            "term", "termText", "term_text", "dimensionCode", "dimension_code",
            "canonicalValue", "canonical_value", "value", "source", "status",
            "domainCode", "domain_code", "weight", "metadata", "semanticType",
            "semantic_type", "canonicalCode", "canonical_code",
        }
    })
    return DictionaryValue(
        term=term,
        dimension_code=dimension_code,
        canonical_value=canonical,
        source=str(_field(row, "source", default=source) or source),
        status=status,
        domain_code=str(_field(row, "domainCode", "domain_code", default="default") or "default"),
        weight=max(0.0, min(weight, 1.0)),
        metadata=metadata,
    )


def _parse_payload(payload: Any, bundle: DictionaryBundle, *, source: str) -> None:
    if not isinstance(payload, dict):
        return
    if payload.get("version") is not None:
        bundle.version = str(payload.get("version") or bundle.version)

    raw_policies = payload.get("valuePolicies") or payload.get("value_policies") or {}
    policy_rows: list[tuple[str, Any, dict[str, Any]]] = []
    if isinstance(raw_policies, dict):
        for code, raw_policy in raw_policies.items():
            if isinstance(raw_policy, dict):
                enabled, _status = _enabled(raw_policy, default="PENDING")
                if not enabled:
                    continue
                metadata = {
                    key: value for key, value in raw_policy.items()
                    if key not in {"policy", "status"}
                }
                policy_rows.append((
                    str(code),
                    _field(raw_policy, "policy", default="UNKNOWN"),
                    metadata,
                ))
            else:
                policy_rows.append((str(code), raw_policy, {}))
    elif isinstance(raw_policies, list):
        for row in raw_policies:
            if not isinstance(row, dict):
                continue
            enabled, _status = _enabled(row, default="PENDING")
            if not enabled:
                continue
            code = str(_field(row, "dimensionCode", "dimension_code") or "")
            metadata = {
                key: value for key, value in row.items()
                if key not in {
                    "dimensionCode", "dimension_code", "policy", "status",
                }
            }
            policy_rows.append((
                code,
                _field(row, "policy", default="UNKNOWN"),
                metadata,
            ))
    for code, raw_policy, metadata in policy_rows:
        policy = str(raw_policy or "UNKNOWN").strip().upper()
        if code.upper().startswith("DIM_") and policy in {
            "PUBLIC_ENUM", "INTERNAL_ENUM", "PII", "UNKNOWN"
        }:
            bundle.value_policies[code] = policy
            bundle.value_policy_metadata[code] = metadata
        else:
            bundle.warnings.append(f"忽略无效维值策略: {code or '<empty>'}")

    structured = any(
        key in payload
        for key in ("entries", "values", "aliases", "valuePolicies", "value_policies")
    )
    raw_alias_rows = [
        *(payload.get("entries") or []),
        *(payload.get("aliases") or []),
    ]
    for row in raw_alias_rows:
        parsed = _parse_alias(row, source=source)
        if parsed:
            bundle.aliases.append(parsed)
        elif isinstance(row, dict):
            enabled, _status = _enabled(row, default="PENDING")
            if enabled:
                bundle.warnings.append(f"忽略无效语义别名: {row.get('term') or row.get('termText') or '<empty>'}")
    for row in payload.get("values") or []:
        parsed = _parse_value(row, source=source)
        if parsed:
            bundle.values.append(parsed)
        elif isinstance(row, dict):
            enabled, _status = _enabled(row, default="PENDING")
            if enabled:
                bundle.warnings.append(f"忽略无效维值别名: {row.get('term') or row.get('termText') or '<empty>'}")

    if structured:
        return

    reserved = {"version", "entries", "aliases", "values", "valuePolicies", "value_policies"}
    for canonical, aliases in payload.items():
        if canonical in reserved or not isinstance(aliases, (list, tuple, set)):
            continue
        terms = unique_strings(aliases)
        if terms:
            bundle.legacy_groups.append(LegacySynonymGroup(
                canonical_term=str(canonical),
                aliases=tuple(terms),
                source=source,
            ))


def load_dictionary(
    paths: Iterable[str | Path] = (),
    *,
    extra_entries: Iterable[dict[str, Any]] = (),
) -> DictionaryBundle:
    bundle = DictionaryBundle()
    for raw_path in paths:
        path = Path(raw_path)
        if not path.exists():
            continue
        try:
            payload = yaml.safe_load(path.read_text(encoding="utf-8")) or {}
        except (OSError, yaml.YAMLError) as exc:
            bundle.warnings.append(f"语义字典加载失败 {path.name}: {exc}")
            continue
        _parse_payload(payload, bundle, source=f"file:{path.name}")

    for row in extra_entries:
        if not isinstance(row, dict):
            continue
        semantic_type = str(_field(row, "semanticType", "semantic_type") or "").lower()
        if semantic_type == "value" or _field(row, "dimensionCode", "dimension_code"):
            parsed_value = _parse_value(row, source="feedback")
            if parsed_value:
                bundle.values.append(parsed_value)
        else:
            parsed_alias = _parse_alias(row, source="feedback")
            if parsed_alias:
                bundle.aliases.append(parsed_alias)

    alias_seen: set[tuple[str, str, str]] = set()
    deduped_aliases: list[DictionaryAlias] = []
    for row in sorted(bundle.aliases, key=lambda item: (-item.weight, item.canonical_code, item.term)):
        key = (normalize_text(row.term), row.semantic_type, row.canonical_code)
        if not key[0] or key in alias_seen:
            continue
        alias_seen.add(key)
        deduped_aliases.append(row)
    bundle.aliases = deduped_aliases

    value_seen: set[tuple[str, str, str]] = set()
    deduped_values: list[DictionaryValue] = []
    for row in sorted(bundle.values, key=lambda item: (-item.weight, item.dimension_code, item.term)):
        key = (normalize_text(row.term), row.dimension_code, normalize_text(row.canonical_value))
        if not key[0] or not key[2] or key in value_seen:
            continue
        value_seen.add(key)
        deduped_values.append(row)
    bundle.values = deduped_values
    return bundle
