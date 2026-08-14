"""General Chinese time-expression parsing for Insight analysis.

The parser is intentionally independent from the KG and DA query layers.  It
normalizes a user expression into one current period, an equally comparable
previous period, and one of the time granularities supported by DA.
"""
from __future__ import annotations

import datetime
import re
from typing import Optional


_CN_DIGITS = {
    "零": 0,
    "〇": 0,
    "一": 1,
    "二": 2,
    "两": 2,
    "三": 3,
    "四": 4,
    "五": 5,
    "六": 6,
    "七": 7,
    "八": 8,
    "九": 9,
}
_CN_UNITS = {"十": 10, "百": 100, "千": 1000}
_COUNT_PATTERN = r"(?:\d{1,3}|[零〇一二两三四五六七八九十百千]+)"


def _parse_count(value: str) -> Optional[int]:
    value = str(value or "").strip()
    if value.isdigit():
        return int(value)
    total = 0
    current = 0
    found = False
    for char in value:
        if char in _CN_DIGITS:
            current = _CN_DIGITS[char]
            found = True
        elif char in _CN_UNITS:
            total += (current or 1) * _CN_UNITS[char]
            current = 0
            found = True
        else:
            return None
    return total + current if found else None


def _shift_month_start(value: datetime.date, months: int) -> datetime.date:
    month_index = value.year * 12 + value.month - 1 + months
    return datetime.date(month_index // 12, month_index % 12 + 1, 1)


def _month_end(value: datetime.date) -> datetime.date:
    return _shift_month_start(value.replace(day=1), 1) - datetime.timedelta(days=1)


def _requested_granularity(question: str, default: str) -> str:
    """Map explicit grouping language to the granularities DA exposes."""
    if re.search(r"按日|逐日|每日|每天|日趋势|天趋势", question):
        return "day"
    if re.search(r"按周|逐周|每周|周趋势|星期趋势", question):
        return "week"
    if re.search(r"按月|逐月|每月|月趋势|按季|逐季|每季|季度趋势|按年|逐年|每年|年度趋势", question):
        return "month"
    return default


def _equal_period_result(
    start: datetime.date,
    end: datetime.date,
    gran: str,
    desc: str,
) -> dict[str, str]:
    if end < start:
        start, end = end, start
    prev_end = start - datetime.timedelta(days=1)
    prev_start = prev_end - (end - start)
    return {
        "time_start": start.isoformat(),
        "time_end": end.isoformat(),
        "prev_start": prev_start.isoformat(),
        "prev_end": prev_end.isoformat(),
        "gran": gran,
        "time_desc": desc.strip(),
    }


def _calendar_month_result(
    start: datetime.date,
    end: datetime.date,
    gran: str,
    desc: str,
) -> dict[str, str]:
    start = start.replace(day=1)
    end = _month_end(end)
    month_count = (end.year - start.year) * 12 + end.month - start.month + 1
    prev_start = _shift_month_start(start, -month_count)
    prev_end = start - datetime.timedelta(days=1)
    return {
        "time_start": start.isoformat(),
        "time_end": end.isoformat(),
        "prev_start": prev_start.isoformat(),
        "prev_end": prev_end.isoformat(),
        "gran": gran,
        "time_desc": desc.strip(),
    }


def _safe_date(year: int, month: int, day: int) -> Optional[datetime.date]:
    try:
        return datetime.date(year, month, day)
    except ValueError:
        return None


def _shift_year(value: datetime.date, years: int) -> datetime.date:
    try:
        return value.replace(year=value.year + years)
    except ValueError:
        # 2月29日同比到非闰年时按自然月末处理。
        return value.replace(year=value.year + years, day=28)


def _apply_comparison_semantics(result: dict[str, str], question: str) -> dict[str, str]:
    """Use a same-period-last-year baseline when the question asks for同比."""
    if not re.search(r"同比|去年同期|上年同期|对比去年|与去年", question):
        return result
    try:
        start = datetime.date.fromisoformat(result["time_start"])
        end = datetime.date.fromisoformat(result["time_end"])
    except (KeyError, ValueError):
        return result
    return {
        **result,
        "prev_start": _shift_year(start, -1).isoformat(),
        "prev_end": _shift_year(end, -1).isoformat(),
    }


def _full_date_mentions(question: str) -> list[tuple[re.Match[str], datetime.date]]:
    pattern = re.compile(
        r"(?<!\d)(\d{4})\s*(?:[-/.年])\s*(\d{1,2})\s*(?:[-/.月])\s*"
        r"(\d{1,2})\s*(?:日|号)?(?!\d)"
        r"|(?<!\d)(\d{4})(\d{2})(\d{2})(?!\d)"
    )
    result: list[tuple[re.Match[str], datetime.date]] = []
    for match in pattern.finditer(question):
        groups = match.groups()
        if groups[0] is not None:
            values = groups[0:3]
        else:
            values = groups[3:6]
        parsed = _safe_date(*(int(value) for value in values))
        if parsed:
            result.append((match, parsed))
    return result


def _parse_explicit_dates(question: str, today: datetime.date) -> Optional[dict[str, str]]:
    mentions = _full_date_mentions(question)
    if len(mentions) >= 2:
        first_match, start = mentions[0]
        second_match, end = mentions[1]
        desc = question[first_match.start():second_match.end()]
        return _equal_period_result(start, end, _requested_granularity(question, "day"), desc)

    if len(mentions) == 1:
        match, start = mentions[0]
        # The second endpoint may inherit the first endpoint's year.
        partial = re.match(
            r"\s*(?:至|到|~|～|—|–|\s-\s|和|与|及|vs\.?|VS\.?)\s*"
            r"(\d{1,2})\s*(?:[-/.月])\s*(\d{1,2})\s*(?:日|号)?",
            question[match.end():],
        )
        if partial:
            end_year = start.year
            end_month = int(partial.group(1))
            end_day = int(partial.group(2))
            end = _safe_date(end_year, end_month, end_day)
            if end and end < start:
                end = _safe_date(end_year + 1, end_month, end_day)
            if end:
                desc_end = match.end() + partial.end()
                return _equal_period_result(
                    start,
                    end,
                    _requested_granularity(question, "day"),
                    question[match.start():desc_end],
                )
        return _equal_period_result(
            start, start, _requested_granularity(question, "day"), match.group(0)
        )

    named_year_date = re.search(
        r"(今年|本年|去年|上年)\s*(\d{1,2})月(\d{1,2})[日号]?",
        question,
    )
    if named_year_date:
        year = today.year - (1 if named_year_date.group(1) in ("去年", "上年") else 0)
        start = _safe_date(year, int(named_year_date.group(2)), int(named_year_date.group(3)))
        if start:
            partial = re.match(
                r"\s*(?:至|到|~|～|—|–|\s-\s|和|与|及|vs\.?|VS\.?)\s*"
                r"(\d{1,2})月(\d{1,2})[日号]?",
                question[named_year_date.end():],
            )
            if partial:
                end_year = year
                end = _safe_date(end_year, int(partial.group(1)), int(partial.group(2)))
                if end and end < start:
                    end = _safe_date(end_year + 1, int(partial.group(1)), int(partial.group(2)))
                if end:
                    desc_end = named_year_date.end() + partial.end()
                    return _equal_period_result(
                        start,
                        end,
                        _requested_granularity(question, "day"),
                        question[named_year_date.start():desc_end],
                    )
            return _equal_period_result(
                start,
                start,
                _requested_granularity(question, "day"),
                named_year_date.group(0),
            )

    # Month/day ranges without a year inherit the current year and may cross
    # New Year, e.g. “12月25日至1月5日”.
    partial_range = re.search(
        r"(?<!\d)(\d{1,2})月(\d{1,2})[日号]?\s*"
        r"(?:至|到|~|～|—|–|\s-\s|和|与|及|vs\.?|VS\.?)\s*"
        r"(\d{1,2})月(\d{1,2})[日号]?",
        question,
    )
    if partial_range:
        start = _safe_date(today.year, int(partial_range.group(1)), int(partial_range.group(2)))
        end = _safe_date(today.year, int(partial_range.group(3)), int(partial_range.group(4)))
        if start and end and end < start:
            end = _safe_date(today.year + 1, int(partial_range.group(3)), int(partial_range.group(4)))
        if start and end:
            return _equal_period_result(
                start, end, _requested_granularity(question, "day"), partial_range.group(0)
            )

    single_partial = re.search(r"(?<!\d)(\d{1,2})月(\d{1,2})[日号]", question)
    if single_partial:
        target = _safe_date(today.year, int(single_partial.group(1)), int(single_partial.group(2)))
        if target:
            return _equal_period_result(
                target, target, _requested_granularity(question, "day"), single_partial.group(0)
            )
    return None


def _parse_relative(question: str, today: datetime.date) -> Optional[dict[str, str]]:
    half_year = re.search(r"(?:最近|近|过去|前)\s*半年", question)
    if half_year:
        start = _shift_month_start(today.replace(day=1), -5)
        return _calendar_month_result(start, today, "month", half_year.group(0))

    relative = re.search(
        rf"(?:最近|近|过去|前)\s*({_COUNT_PATTERN})\s*(?:个)?\s*(?:完整|自然)?\s*(天|日|周|星期|月|季度|季|年)",
        question,
    )
    if not relative:
        return None
    count = _parse_count(relative.group(1))
    if not count or count < 1:
        return None
    unit = relative.group(2)
    desc = relative.group(0)
    if unit in ("天", "日"):
        start = today - datetime.timedelta(days=count - 1)
        return _equal_period_result(start, today, _requested_granularity(question, "day"), desc)
    if unit in ("周", "星期"):
        complete_weeks = bool(re.search(r"(?:完整|自然)\s*(?:个)?(?:周|星期)", question))
        week_start = today - datetime.timedelta(days=today.weekday())
        if complete_weeks:
            end = week_start - datetime.timedelta(days=1)
            start = end - datetime.timedelta(days=count * 7 - 1)
        else:
            # “最近 N 周”按截至今天的滚动窗口解释，不能包含未来日期。
            end = today
            start = end - datetime.timedelta(days=count * 7 - 1)
        return _equal_period_result(start, end, _requested_granularity(question, "week"), desc)
    months = count
    if unit in ("季度", "季"):
        months *= 3
    elif unit == "年":
        months *= 12
    start = _shift_month_start(today.replace(day=1), -(months - 1))
    return _calendar_month_result(start, today, _requested_granularity(question, "month"), desc)


def _parse_named_period(question: str, today: datetime.date) -> Optional[dict[str, str]]:
    named_days = {
        "今天": 0,
        "今日": 0,
        "昨天": -1,
        "昨日": -1,
        "前天": -2,
    }
    for name, offset in named_days.items():
        if name in question:
            target = today + datetime.timedelta(days=offset)
            return _equal_period_result(target, target, "day", name)

    if re.search(r"本周|这周|本星期|这星期", question):
        start = today - datetime.timedelta(days=today.weekday())
        return _equal_period_result(start, start + datetime.timedelta(days=6), "week", "本周")
    if re.search(r"上周|上星期", question):
        end = today - datetime.timedelta(days=today.weekday() + 1)
        return _equal_period_result(end - datetime.timedelta(days=6), end, "week", "上周")

    if re.search(r"本月|这个月|当月", question):
        return _calendar_month_result(today, today, "month", "本月")
    if re.search(r"上月|上个月", question):
        start = _shift_month_start(today.replace(day=1), -1)
        return _calendar_month_result(start, start, "month", "上月")

    current_quarter_month = ((today.month - 1) // 3) * 3 + 1
    current_quarter = datetime.date(today.year, current_quarter_month, 1)
    if re.search(r"本季度|本季|当季", question):
        return _calendar_month_result(
            current_quarter, _shift_month_start(current_quarter, 2), "month", "本季度"
        )
    if re.search(r"上季度|上季", question):
        start = _shift_month_start(current_quarter, -3)
        return _calendar_month_result(start, _shift_month_start(start, 2), "month", "上季度")

    if re.search(r"今年|本年|本年度", question):
        start = datetime.date(today.year, 1, 1)
        return _calendar_month_result(start, datetime.date(today.year, 12, 1), "month", "今年")
    if re.search(r"去年|上年|上年度", question):
        start = datetime.date(today.year - 1, 1, 1)
        return _calendar_month_result(start, datetime.date(today.year - 1, 12, 1), "month", "去年")
    return None


def _parse_week(question: str, today: datetime.date) -> Optional[dict[str, str]]:
    patterns = [
        re.compile(r"(?:(\d{4})\s*年?\s*)?第\s*(\d{1,2})\s*周"),
        re.compile(r"(?:(\d{4})\s*[-/]?\s*)?W\s*(\d{1,2})(?!\d)", re.IGNORECASE),
    ]
    for pattern in patterns:
        match = pattern.search(question)
        if not match:
            continue
        year = int(match.group(1) or today.isocalendar()[0])
        week = int(match.group(2))
        try:
            start = datetime.date.fromisocalendar(year, week, 1)
            end = datetime.date.fromisocalendar(year, week, 7)
        except ValueError:
            return None
        return _equal_period_result(start, end, "week", match.group(0))
    return None


def _parse_quarter_month_year(question: str, today: datetime.date) -> Optional[dict[str, str]]:
    named_year = re.search(r"(今年|本年|本年度|去年|上年|上年度)", question)
    named_year_value = None
    if named_year:
        named_year_value = today.year - (1 if named_year.group(1) in ("去年", "上年", "上年度") else 0)

        named_month = re.search(r"(?:今年|本年|本年度|去年|上年|上年度)\s*(\d{1,2})\s*月", question)
        if named_month:
            try:
                start = datetime.date(named_year_value, int(named_month.group(1)), 1)
            except ValueError:
                start = None
            if start:
                return _calendar_month_result(start, start, "month", named_month.group(0))

        named_quarter = re.search(
            r"(?:今年|本年|本年度|去年|上年|上年度)\s*(?:第?\s*([一二三四1234])\s*季度|Q\s*([1-4]))",
            question,
            re.IGNORECASE,
        )
        if named_quarter:
            raw_quarter = named_quarter.group(1) or named_quarter.group(2)
            quarter_num = _CN_DIGITS.get(
                raw_quarter,
                int(raw_quarter) if raw_quarter.isdigit() else 0,
            )
            if 1 <= quarter_num <= 4:
                start = datetime.date(named_year_value, (quarter_num - 1) * 3 + 1, 1)
                return _calendar_month_result(
                    start, _shift_month_start(start, 2), "month", named_quarter.group(0)
                )

        named_half = re.search(
            r"(?:今年|本年|本年度|去年|上年|上年度)\s*(上半年|下半年)", question
        )
        if named_half:
            start_month = 1 if named_half.group(1) == "上半年" else 7
            start = datetime.date(named_year_value, start_month, 1)
            return _calendar_month_result(
                start, _shift_month_start(start, 5), "month", named_half.group(0)
            )

    numeric_half = re.search(r"(?:(\d{4})\s*年\s*)?(上半年|下半年)", question)
    if numeric_half:
        year_value = int(numeric_half.group(1) or today.year)
        start_month = 1 if numeric_half.group(2) == "上半年" else 7
        start = datetime.date(year_value, start_month, 1)
        return _calendar_month_result(
            start, _shift_month_start(start, 5), "month", numeric_half.group(0)
        )

    quarter = re.search(
        r"(?:(\d{4})\s*年?\s*)?(?:第?\s*([一二三四1234])\s*季度|Q\s*([1-4]))",
        question,
        re.IGNORECASE,
    )
    if quarter:
        year = int(quarter.group(1) or today.year)
        raw_quarter = quarter.group(2) or quarter.group(3)
        quarter_num = _CN_DIGITS.get(raw_quarter, int(raw_quarter) if raw_quarter.isdigit() else 0)
        if 1 <= quarter_num <= 4:
            start = datetime.date(year, (quarter_num - 1) * 3 + 1, 1)
            return _calendar_month_result(
                start, _shift_month_start(start, 2), "month", quarter.group(0)
            )

    month_pattern = re.compile(
        r"(?<!\d)(?:(\d{4})\s*年\s*(\d{1,2})\s*月|"
        r"(\d{4})\s*[-/.]\s*(\d{1,2}))"
        r"(?!\s*(?:[-/.]\s*\d|\d{1,2}\s*[日号]))"
    )
    month_mentions: list[tuple[re.Match[str], datetime.date]] = []
    for match in month_pattern.finditer(question):
        try:
            year_value = int(match.group(1) or match.group(3))
            month_value = int(match.group(2) or match.group(4))
            month_mentions.append((match, datetime.date(year_value, month_value, 1)))
        except ValueError:
            continue
    if len(month_mentions) >= 2:
        first_match, start = month_mentions[0]
        second_match, end = month_mentions[1]
        return _calendar_month_result(
            start,
            end,
            "month",
            question[first_match.start():second_match.end()],
        )
    if len(month_mentions) == 1:
        match, start = month_mentions[0]
        # “2026年1月至3月” inherits the first endpoint's year.
        partial = re.match(
            r"\s*(?:至|到|~|～|—|–|\s-\s|和|与|及|vs\.?|VS\.?)\s*(\d{1,2})\s*月",
            question[match.end():],
        )
        if partial:
            end_year = start.year + (1 if int(partial.group(1)) < start.month else 0)
            try:
                end = datetime.date(end_year, int(partial.group(1)), 1)
            except ValueError:
                end = None
            if end:
                desc_end = match.end() + partial.end()
                return _calendar_month_result(start, end, "month", question[match.start():desc_end])
        return _calendar_month_result(start, start, "month", match.group(0))

    partial_month = re.search(r"(?<!\d)(\d{1,2})月(?!\d)", question)
    if partial_month:
        try:
            start = datetime.date(today.year, int(partial_month.group(1)), 1)
        except ValueError:
            start = None
        if start:
            return _calendar_month_result(start, start, "month", partial_month.group(0))

    year = re.search(r"(?<!\d)(\d{4})\s*年(?!\s*(?:第?[一二三四1234]|Q?[1-4])?\s*(?:月|季度|季))", question)
    if year:
        start = datetime.date(int(year.group(1)), 1, 1)
        return _calendar_month_result(start, datetime.date(start.year, 12, 1), "month", year.group(0))
    return None


def parse_question_time(question: str, today: datetime.date) -> Optional[dict[str, str]]:
    """Parse common absolute and relative Chinese business time expressions.

    Explicit dates have the highest precedence, followed by relative and named
    periods, ISO weeks, and calendar quarter/month/year expressions.  ``None``
    means the caller should use context or its product-specific default.
    """
    question = str(question or "").strip()
    if not question:
        return None
    for parser in (
        _parse_explicit_dates,
        _parse_relative,
        _parse_week,
        _parse_quarter_month_year,
        _parse_named_period,
    ):
        parsed = parser(question, today)
        if parsed:
            return _apply_comparison_semantics(parsed, question)
    return None
