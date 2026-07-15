"""Call SOP checkpoint analysis for DA-TMS call quality records."""

from __future__ import annotations

import json
import re
from dataclasses import dataclass
from typing import Any, Iterable


SOP_VERSION = "call_sop_v1.1"

SOP_GRADE_LABELS = {
    "high": "高质量达成",
    "standard": "标准达成",
    "basic": "基础达成",
    "miss": "未达成",
}


def overall_grade_label(rate: float, connected: bool = True) -> str:
    """Return the single-call SOP grade used by diagnosis and workbench views."""
    if not connected:
        return SOP_GRADE_LABELS["miss"]
    if rate >= 0.75:
        return SOP_GRADE_LABELS["high"]
    if rate >= 0.55:
        return SOP_GRADE_LABELS["standard"]
    if rate >= 0.35:
        return SOP_GRADE_LABELS["basic"]
    return SOP_GRADE_LABELS["miss"]


def overall_grade_level(label: Any, rate: float = 0, connected: bool = True) -> str:
    """Normalize a stored grade label, falling back to the shared rate thresholds."""
    text = str(label or "").strip()
    for level, grade_label in SOP_GRADE_LABELS.items():
        if text == grade_label:
            return level
    return next(
        level
        for level, grade_label in SOP_GRADE_LABELS.items()
        if grade_label == overall_grade_label(rate, connected)
    )


@dataclass(frozen=True)
class SopCheckpoint:
    code: str
    name: str
    description: str


@dataclass(frozen=True)
class SopCategory:
    code: str
    name: str
    description: str
    checkpoints: tuple[SopCheckpoint, ...]


SOP_CATALOG: tuple[SopCategory, ...] = (
    SopCategory(
        "RULE_000",
        "自我介绍",
        "清晰说明产品专家身份、门店与来电原因",
        (
            SopCheckpoint("RULE_000_01", "是否介绍自己", "说明自己是产品专家/顾问，而不是泛泛说这边。"),
            SopCheckpoint("RULE_000_02", "是否说明来自哪里", "说明品牌、门店或线索来源，降低客户陌生感。"),
            SopCheckpoint("RULE_000_03", "是否表明来意", "说清楚本通电话是围绕试驾邀约或到店安排。"),
        ),
    ),
    SopCategory(
        "RULE_001",
        "回忆唤起",
        "帮助客户回忆线索来源、关注车型或上次沟通背景",
        (
            SopCheckpoint("RULE_001_01", "是否提及线索来源", "说明客户曾留资、关注车型或咨询入口。"),
            SopCheckpoint("RULE_001_02", "是否唤起关注车型", "提及客户看过或关注过的车型。"),
            SopCheckpoint("RULE_001_03", "是否承接上次沟通", "承接客户此前表达过的需求或顾虑。"),
        ),
    ),
    SopCategory(
        "RULE_002",
        "信息建立",
        "围绕客户关注点补充关键信息，降低试驾决策成本",
        (
            SopCheckpoint("RULE_002_01", "是否回应客户关注点", "围绕空间、智驾、补能等关注点补充信息。"),
            SopCheckpoint("RULE_002_02", "是否建立试驾价值", "说明为什么需要到店体验，而非只催促到店。"),
            SopCheckpoint("RULE_002_03", "是否结合客户场景", "把信息与家庭、通勤、长途等用车场景关联。"),
        ),
    ),
    SopCategory(
        "RULE_003",
        "封闭式邀约",
        "使用明确、可选择的邀约方式推进试驾",
        (
            SopCheckpoint("RULE_003_01", "是否给出明确时间", "提供具体日期或时间段，而非只说有空。"),
            SopCheckpoint("RULE_003_02", "是否使用二选一推进", "用两个可选时间降低客户决策成本。"),
            SopCheckpoint("RULE_003_03", "是否绑定试驾动作", "明确邀请客户到店试驾，而非泛泛来看看。"),
        ),
    ),
    SopCategory(
        "RULE_004",
        "异议处理",
        "识别客户顾虑并给出具体回应",
        (
            SopCheckpoint("RULE_004_01", "是否识别异议类型", "区分忙、远、价格、竞品、暂不考虑等原因。"),
            SopCheckpoint("RULE_004_02", "是否追问真实原因", "客户拒绝时继续澄清背后的阻碍。"),
            SopCheckpoint("RULE_004_03", "是否给出替代方案", "提供改约、线上讲解、短时体验等方案。"),
        ),
    ),
    SopCategory(
        "RULE_005",
        "约定确认",
        "确认到店时间、门店地址、联系人与后续动作",
        (
            SopCheckpoint("RULE_005_01", "是否确认到店时间", "明确客户计划到店的日期和时间段。"),
            SopCheckpoint("RULE_005_02", "是否确认门店地址", "同步门店地址或导航信息。"),
            SopCheckpoint("RULE_005_03", "是否说明后续动作", "说明加微、发定位、再次联系等下一步。"),
        ),
    ),
    SopCategory(
        "RULE_006",
        "探需覆盖",
        "覆盖购车时间、预算、场景、竞品、充电条件等",
        (
            SopCheckpoint("RULE_006_01", "是否覆盖购车时间", "了解客户计划什么时候买车或换车。"),
            SopCheckpoint("RULE_006_02", "是否覆盖预算", "了解客户预算或价格敏感点。"),
            SopCheckpoint("RULE_006_03", "是否覆盖用车场景", "了解家庭人数、通勤、长途、补能等场景。"),
        ),
    ),
    SopCategory(
        "RULE_007",
        "邀约结果",
        "明确本通电话的邀约结果，形成可追踪的下一步状态",
        (
            SopCheckpoint("RULE_007_01", "是否明确结果状态", "标明邀约成功或邀约失败。"),
            SopCheckpoint("RULE_007_02", "是否记录失败原因", "邀约失败时记录客户拒绝原因。"),
            SopCheckpoint("RULE_007_03", "是否记录下一步动作", "明确后续联系、发资料、改约或无需跟进。"),
        ),
    ),
)


def catalog_payload() -> list[dict[str, Any]]:
    return [
        {
            "code": category.code,
            "name": category.name,
            "description": category.description,
            "checkpoints": [
                {
                    "code": checkpoint.code,
                    "name": checkpoint.name,
                    "description": checkpoint.description,
                }
                for checkpoint in category.checkpoints
            ],
        }
        for category in SOP_CATALOG
    ]


def _normalize_text(value: Any) -> str:
    text = str(value or "")
    text = re.sub(r"\[时间:[^\]]+\]", " ", text)
    return re.sub(r"\s+", " ", text.replace("｜", " ")).strip()


def _speaker_text(content: str, speaker: str) -> str:
    parts = re.findall(r"(专家|客户):([^｜]+)", str(content or ""))
    return _normalize_text(" ".join(text for who, text in parts if who == speaker))


def _has_any(text: str, keywords: Iterable[str]) -> bool:
    return any(keyword and keyword in text for keyword in keywords)


def _has_pattern(text: str, pattern: str) -> bool:
    return re.search(pattern, text, re.IGNORECASE) is not None


def _evidence(texts: list[str], keywords: Iterable[str], fallback: str = "") -> str:
    keywords = [keyword for keyword in keywords if keyword]
    for text in texts:
        if not text:
            continue
        for keyword in keywords:
            idx = text.find(keyword)
            if idx >= 0:
                start = max(0, idx - 22)
                end = min(len(text), idx + len(keyword) + 42)
                return text[start:end].strip()
    return fallback[:80].strip()


def _ratio_level(ratio: float) -> str:
    # Each category currently has three checkpoints. Use exact thirds so a
    # 1/3 and 2/3 result land in the intended basic and standard bands.
    if ratio >= 1 - 1e-9:
        return "high"
    if ratio >= (2 / 3) - 1e-9:
        return "standard"
    if ratio >= (1 / 3) - 1e-9:
        return "basic"
    return "miss"


def analyze_call_sop_record(record: dict[str, Any]) -> dict[str, Any]:
    content = str(record.get("aggregated_content") or "")
    actual_next_action = str(record.get("actual_next_action") or "")
    validation_notes = str(record.get("validation_notes") or "")
    intent_name = str(record.get("intent_name") or "")
    expert = _speaker_text(content, "专家")
    customer = _speaker_text(content, "客户")
    all_text = _normalize_text(" ".join([content, actual_next_action, validation_notes, intent_name]))
    expert_all = _normalize_text(" ".join([expert, actual_next_action, validation_notes]))
    customer_all = _normalize_text(" ".join([customer, intent_name]))

    unconnected_terms = ("无法接听", "语音留言", "未接通", "未建立对话", "空号", "关机")
    connected = not _has_any(all_text, unconnected_terms)

    time_terms = ("今天", "明天", "后天", "上午", "下午", "晚上", "周一", "周二", "周三", "周四", "周五", "周六", "周日", "星期", "号", "点", "分钟", "提前")
    model_terms = ("G6", "G9", "X9", "P7i", "MONA", "车型", "max", "pro")
    concern_terms = ("价格", "优惠", "补贴", "贷款", "全款", "分期", "现车", "提车", "配置", "颜色", "空间", "智驾", "续航", "充电", "门店", "地址", "试驾")
    scene_terms = ("家人", "孩子", "对象", "老婆", "媳妇", "家庭", "上班", "通勤", "长途", "旅游", "老人", "小孩", "父亲", "姑娘", "工作", "房贷", "社保")
    objection_terms = ("没有", "不考虑", "再说", "没时间", "忙", "太远", "贵", "价格", "竞品", "奥迪", "宝马", "极狐", "不买", "打扰", "拒绝", "退", "维权", "已买")
    next_action_terms = ("加微信", "发定位", "备注", "帮您", "联系", "回访", "提前联系", "发给", "群里", "到时候", "申请", "预约", "来店", "来门店")

    checks: dict[str, tuple[bool, list[str], str]] = {
        "RULE_000_01": (
            connected and _has_pattern(
                expert_all,
                r"(?:我叫|我是|我这边是).{0,8}(?:产品专家|顾问|销售|小[\u4e00-\u9fff]{1,3})",
            ),
            ["我叫", "我是", "我这边是", "产品专家", "顾问", "销售"],
            "专家自我介绍",
        ),
        "RULE_000_02": (
            connected and _has_any(expert_all, ("小鹏汽车", "门店", "官网", "杭州")),
            ["小鹏汽车", "门店", "官网", "杭州"],
            "品牌或门店来源",
        ),
        "RULE_000_03": (
            connected and _has_any(expert_all, ("试驾", "到店", "体验", "邀请", "来店", "过来", "安排", "咨询", "联系", "回访", "问问")),
            ["试驾", "到店", "体验", "邀请", "安排", "咨询"],
            "来电目的",
        ),
        "RULE_001_01": (
            connected and _has_any(expert_all, ("官网", "关注", "留资", "收到", "看您", "预约", "咨询", "刚刚", "前两天")),
            ["官网", "关注", "留资", "收到", "看您", "预约", "咨询"],
            "线索来源",
        ),
        "RULE_001_02": (
            connected and _has_any(all_text, model_terms),
            list(model_terms),
            "关注车型",
        ),
        "RULE_001_03": (
            connected and _has_any(all_text, ("上次", "刚才", "那天", "昨天", "之前", "上回", "你不是说", "咱俩", "试驾感觉")),
            ["上次", "刚才", "那天", "之前", "试驾感觉"],
            "历史沟通背景",
        ),
        "RULE_003_01": (
            connected and _has_any(expert_all, time_terms),
            list(time_terms),
            "明确时间",
        ),
        "RULE_003_02": (
            connected and (_has_pattern(expert_all, r"(今天|明天|上午|下午|周.|星期.).{0,12}(还是|或者|要不|都可以)")
                           or _has_any(expert_all, ("二选一", "两个时间", "上午还是下午"))),
            ["还是", "或者", "要不", "二选一", "两个时间"],
            "二选一推进",
        ),
        "RULE_003_03": (
            connected and _has_any(expert_all, ("试驾", "试乘", "体验一下", "开一圈", "上门试驾", "到店体验")),
            ["试驾", "试乘", "体验一下", "上门试驾", "到店体验"],
            "绑定试驾",
        ),
        "RULE_002_01": (
            connected and bool(set(term for term in concern_terms if term in customer_all) & set(term for term in concern_terms if term in expert_all)),
            list(concern_terms),
            "回应客户关注点",
        ),
        "RULE_002_02": (
            connected and _has_any(expert_all, ("体验", "试驾完", "感受", "开一下", "空间", "智驾", "舒适", "对比", "上门")),
            ["体验", "试驾完", "感受", "空间", "智驾", "对比"],
            "建立试驾价值",
        ),
        "RULE_002_03": (
            connected and _has_any(all_text, scene_terms),
            list(scene_terms),
            "客户用车场景",
        ),
        "RULE_004_01": (
            connected and _has_any(all_text, objection_terms),
            list(objection_terms),
            "异议类型",
        ),
        "RULE_004_02": (
            connected and _has_any(expert_all, ("为什么", "啥原因", "是因为", "怎么", "担心", "考虑", "哪方面", "为啥", "咋")),
            ["为什么", "啥原因", "是因为", "担心", "考虑", "哪方面", "为啥"],
            "追问原因",
        ),
        "RULE_004_03": (
            connected and _has_any(expert_all, ("要不", "改天", "后续", "上门", "线上", "微信", "发给您", "联系", "再约", "到时候", "帮您", "来店里", "群里")),
            ["要不", "改天", "后续", "上门", "微信", "联系", "再约", "帮您"],
            "替代方案",
        ),
        "RULE_005_01": (
            connected and _has_any(expert_all, time_terms) and _has_any(expert_all, ("到", "来", "去", "预约", "试驾", "提车", "联系")),
            list(time_terms),
            "确认到店时间",
        ),
        "RULE_005_02": (
            connected and _has_any(expert_all, ("地址", "位置", "导航", "定位", "西溪", "文一西路", "门店", "店里", "这边")),
            ["地址", "位置", "导航", "定位", "西溪", "文一西路", "门店"],
            "确认门店地址",
        ),
        "RULE_005_03": (
            connected and (actual_next_action.strip() not in {"", "无", "none", "None"} or _has_any(expert_all, next_action_terms)),
            list(next_action_terms),
            "说明后续动作",
        ),
        "RULE_006_01": (
            connected and _has_any(all_text, ("什么时候买", "多久", "以后", "毕业", "月底", "提车", "订车", "购车", "换车", "再说", "未来", "号")),
            ["什么时候买", "多久", "以后", "毕业", "月底", "提车", "订车", "换车"],
            "购车时间",
        ),
        "RULE_006_02": (
            connected and _has_any(all_text, ("预算", "价格", "优惠", "多少钱", "首付", "月供", "贷款", "全款", "补贴", "钱", "万", "贵")),
            ["预算", "价格", "优惠", "多少钱", "首付", "月供", "贷款", "全款", "补贴"],
            "预算价格",
        ),
        "RULE_006_03": (
            connected and _has_any(all_text, scene_terms),
            list(scene_terms),
            "用车场景",
        ),
        "RULE_007_01": (
            connected and (_has_any(all_text, ("好嘞", "没问题", "行", "可以", "不考虑", "再说", "没需求", "已买", "打扰"))
                           or actual_next_action.strip() not in {"", "无", "none", "None"}),
            ["好嘞", "没问题", "行", "可以", "不考虑", "再说", "已买"],
            "邀约结果状态",
        ),
        "RULE_007_02": (
            connected and _has_any(all_text, ("不考虑", "再说", "没需求", "没时间", "忙", "太远", "已买", "打扰", "拒绝", "不买", "以后")),
            ["不考虑", "再说", "没需求", "没时间", "忙", "太远", "已买", "拒绝"],
            "失败原因",
        ),
        "RULE_007_03": (
            connected and (actual_next_action.strip() not in {"", "无", "none", "None"} or _has_any(expert_all, next_action_terms)),
            list(next_action_terms),
            "记录下一步",
        ),
    }

    category_rows: list[dict[str, Any]] = []
    total_hits = 0
    total_checks = 0
    for category in SOP_CATALOG:
        checkpoint_rows = []
        hit_count = 0
        for checkpoint in category.checkpoints:
            hit, keywords, fallback = checks.get(checkpoint.code, (False, [], checkpoint.name))
            evidence = _evidence([expert_all, all_text], keywords, fallback if hit else "")
            checkpoint_rows.append(
                {
                    "code": checkpoint.code,
                    "name": checkpoint.name,
                    "description": checkpoint.description,
                    "hit": bool(hit),
                    "evidence": evidence if hit else "",
                    "method": "keyword_rule",
                }
            )
            hit_count += 1 if hit else 0
        checkpoint_total = len(category.checkpoints)
        coverage_rate = hit_count / checkpoint_total if checkpoint_total else 0
        category_rows.append(
            {
                "code": category.code,
                "name": category.name,
                "description": category.description,
                "hit_count": hit_count,
                "total_count": checkpoint_total,
                "coverage_rate": round(coverage_rate, 4),
                "level": _ratio_level(coverage_rate),
                "checkpoints": checkpoint_rows,
            }
        )
        total_hits += hit_count
        total_checks += checkpoint_total

    coverage_rate = total_hits / total_checks if total_checks else 0
    return {
        "version": SOP_VERSION,
        "connected": connected,
        "analysis_method": "deterministic_keyword_rule",
        "analysis_basis": ["aggregated_content", "actual_next_action", "intent_name", "validation_notes"],
        "hit_checkpoint_count": total_hits,
        "total_checkpoint_count": total_checks,
        "coverage_rate": round(coverage_rate, 4),
        "categories": category_rows,
    }


def category_summary(analysis: dict[str, Any]) -> list[dict[str, Any]]:
    return [
        {
            "code": item.get("code"),
            "name": item.get("name"),
            "hit_count": item.get("hit_count") or 0,
            "total_count": item.get("total_count") or 0,
            "coverage_rate": item.get("coverage_rate") or 0,
            "level": item.get("level") or "miss",
        }
        for item in analysis.get("categories") or []
        if isinstance(item, dict)
    ]


def json_dumps(value: Any) -> str:
    return json.dumps(value, ensure_ascii=False, separators=(",", ":"))
