"""Derived call-workbench fields for trial invitation diagnosis dashboards."""

from __future__ import annotations

import json
import re
from typing import Any

from kg_builder.call_sop import analyze_call_sop_record, overall_grade_label


WORKSPACE_VERSION = "call_quality_workspace_v1.0"


def _normalize_text(value: Any) -> str:
    return re.sub(r"\s+", " ", str(value or "").replace("｜", " ")).strip()


def _plain_call_text(content: Any) -> str:
    text = re.sub(r"\[时间:[^\]]+\]", " ", str(content or ""))
    text = re.sub(r"(专家|客户):", " ", text)
    return _normalize_text(text)


def _format_duration(seconds: int) -> str:
    seconds = max(0, int(seconds or 0))
    return f"{seconds // 60:02d}:{seconds % 60:02d}"


def _parse_segments(content: Any) -> list[dict[str, Any]]:
    raw = str(content or "")
    parts = re.findall(r"(专家|客户):([^｜]+)", raw)
    if not parts and raw.strip():
        parts = [("专家", raw)]
    plain_lengths = [max(1, len(_normalize_text(text))) for _role, text in parts]
    total_len = sum(plain_lengths) or 1
    duration = estimate_duration_seconds(content)
    offset = 0
    segments: list[dict[str, Any]] = []
    for idx, ((role, text), length) in enumerate(zip(parts, plain_lengths)):
        if idx:
            offset = min(duration - 1, offset + max(2, round(length / total_len * duration)))
        clean = _normalize_text(text)
        segments.append(
            {
                "index": idx,
                "role": "expert" if role == "专家" else "customer",
                "roleName": role,
                "text": clean,
                "offsetSeconds": max(0, offset),
                "offsetLabel": _format_duration(offset),
            }
        )
    return segments


def estimate_duration_seconds(content: Any) -> int:
    plain = _plain_call_text(content)
    char_count = len(plain)
    segment_count = len(re.findall(r"(专家|客户):", str(content or "")))
    return int(min(420, max(24, round(char_count * 0.22 + segment_count * 1.8))))


def _customer_display_name(customer_account_id: Any) -> str:
    raw = str(customer_account_id or "").strip()
    return f"客户{raw[-4:]}" if raw else "未知客户"


def _grade_label(rate: float, connected: bool) -> str:
    return overall_grade_label(rate, connected)


def _invite_result(analysis: dict[str, Any], actual_next_action: Any, content: Any) -> str:
    text = f"{actual_next_action or ''} {content or ''}"
    if not analysis.get("connected"):
        return "未接通"
    if any(key in text for key in ("不考虑", "无需求", "再说", "已买", "打扰", "拒绝", "没时间")):
        return "邀约失败"
    if any(key in text for key in ("试驾", "到店", "预约", "来店", "发定位", "联系", "加微信", "提前联系")):
        return "邀约成功"
    if str(actual_next_action or "").strip() and str(actual_next_action or "").strip() != "无":
        return "待跟进"
    return "邀约失败"


def _primary_sop(categories: list[dict[str, Any]]) -> str:
    connected = [item for item in categories if int(item.get("total_count") or 0) > 0]
    if not connected:
        return "未识别"
    weakest = min(
        connected,
        key=lambda item: (
            float(item.get("coverage_rate") or 0),
            -int(item.get("hit_count") or 0),
        ),
    )
    return str(weakest.get("name") or "未识别")


def _segment_for_evidence(segments: list[dict[str, Any]], evidence: str) -> dict[str, Any] | None:
    if not evidence:
        return None
    for segment in segments:
        text = segment.get("text") or ""
        if evidence in text or text in evidence:
            return segment
    terms = [term for term in re.split(r"[，。,. ]+", evidence) if len(term) >= 2]
    for segment in segments:
        text = segment.get("text") or ""
        if any(term in text for term in terms[:4]):
            return segment
    return None


def _sop_evidence(analysis: dict[str, Any], segments: list[dict[str, Any]]) -> list[dict[str, Any]]:
    rows = []
    for category in analysis.get("categories") or []:
        hit_checks = [item for item in category.get("checkpoints") or [] if item.get("hit")]
        evidence = next((item.get("evidence") for item in hit_checks if item.get("evidence")), "")
        segment = _segment_for_evidence(segments, evidence) or next((item for item in segments if item.get("role") == "expert"), None)
        coverage = float(category.get("coverage_rate") or 0)
        rows.append(
            {
                "code": category.get("code"),
                "name": category.get("name"),
                "level": category.get("level"),
                "hitCount": category.get("hit_count") or 0,
                "totalCount": category.get("total_count") or 0,
                "coverageRate": coverage,
                "startLabel": (segment or {}).get("offsetLabel") or "00:00",
                "endLabel": _format_duration(((segment or {}).get("offsetSeconds") or 0) + max(6, len(str((segment or {}).get("text") or "")) // 5)),
                "asrText": evidence or (segment or {}).get("text") or "",
                "goodPoint": f"已命中 {len(hit_checks)} 个「{category.get('name')}」小达成点。" if hit_checks else "未识别到稳定命中证据。",
                "badPoint": "仍需补齐未命中的小达成点，并结合客户场景表达。" if coverage < 1 else "表达完整，可作为复盘口径样本。",
                "checkpoints": category.get("checkpoints") or [],
            }
        )
    return rows


def build_workspace_payload(record: dict[str, Any]) -> dict[str, Any]:
    content = record.get("aggregated_content") or ""
    analysis = record.get("sop_analysis")
    if not isinstance(analysis, dict):
        analysis = analyze_call_sop_record(record)
    segments = _parse_segments(content)
    duration_seconds = estimate_duration_seconds(content)
    plain = _plain_call_text(content)
    connected = bool(analysis.get("connected"))
    coverage_rate = float(analysis.get("coverage_rate") or 0)
    grade = _grade_label(coverage_rate, connected)
    result = _invite_result(analysis, record.get("actual_next_action"), content)
    evidence = _sop_evidence(analysis, segments)
    detail = {
        "version": WORKSPACE_VERSION,
        "customerDisplayName": _customer_display_name(record.get("customer_account_id")),
        "wordCount": len(plain),
        "durationSeconds": duration_seconds,
        "durationLabel": _format_duration(duration_seconds),
        "inviteResultLabel": result,
        "sopGradeLabel": grade,
        "primarySopCategory": _primary_sop(analysis.get("categories") or []),
        "goodPoints": [
            item["goodPoint"]
            for item in evidence
            if float(item.get("coverageRate") or 0) >= 0.67
        ][:3],
        "badPoints": [
            item["badPoint"]
            for item in evidence
            if float(item.get("coverageRate") or 0) < 0.67
        ][:3],
    }
    return {
        "segments": segments,
        "sopEvidence": evidence,
        "detail": detail,
    }


def json_dumps(value: Any) -> str:
    return json.dumps(value, ensure_ascii=False, separators=(",", ":"))
