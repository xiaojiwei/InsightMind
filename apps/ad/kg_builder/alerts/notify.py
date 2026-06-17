"""Notification dispatch to Feishu / Lark.

Uses httpx for async HTTP calls. The card template is designed to
mirror the established DA FeishuCardMessage shape but lives entirely
inside AD for this module.
"""

from __future__ import annotations

import json
import logging
from dataclasses import dataclass, field
from datetime import datetime, timezone
from typing import Any, Optional

import httpx

from . import models

logger = logging.getLogger(__name__)

# ---------------------------------------------------------------------------
# Defaults / env-aware config
# ---------------------------------------------------------------------------

FEISHU_APP_ID = ""
FEISHU_APP_SECRET = ""
FEISHU_BASE = "https://open.feishu.cn"


def configure_feishu(app_id: str, app_secret: str, base_url: str = "") -> None:
    """Update module-level Feishu credentials at runtime."""
    global FEISHU_APP_ID, FEISHU_APP_SECRET, FEISHU_BASE
    FEISHU_APP_ID = app_id
    FEISHU_APP_SECRET = app_secret
    if base_url:
        FEISHU_BASE = base_url.rstrip("/")


# ---------------------------------------------------------------------------
# Notification envelope
# ---------------------------------------------------------------------------

@dataclass
class AlertNotification:
    alert_log_id: int
    rule_name: str
    measure_name: str
    measure_code: str
    actual_value: str
    threshold_desc: str
    severity_label: str
    severity_color: str
    reason: str
    assignee_name: str = ""
    assignee_id: str = ""
    triggered_at: str = ""
    detail_url: str = ""

    def card_title(self) -> str:
        emoji = {0: "🔔", 1: "🔔", 2: "⚠️", 3: "🚨"}.get(self.level, "🔔")
        return f"{emoji} [{self.severity_label}] {self.measure_name}"

    @property
    def level(self) -> int:
        lookup = {"notice": 1, "warning": 2, "critical": 3}
        return lookup.get(self.severity_label, 1)


# ---------------------------------------------------------------------------
# Feishu client (internal)
# ---------------------------------------------------------------------------

class _FeishuClient:
    """Minimal Feishu Open API client with token caching."""

    def __init__(self) -> None:
        self._token: Optional[str] = None
        self._expires_at: float = 0.0

    async def _ensure_token(self) -> str:
        now = datetime.now(timezone.utc).timestamp()
        if self._token and now < self._expires_at - 60:
            return self._token
        if not FEISHU_APP_ID or not FEISHU_APP_SECRET:
            raise RuntimeError("Feishu credentials not configured")
        async with httpx.AsyncClient(timeout=15) as client:
            resp = await client.post(
                f"{FEISHU_BASE}/open-apis/auth/v3/tenant_access_token/internal",
                json={"app_id": FEISHU_APP_ID, "app_secret": FEISHU_APP_SECRET},
            )
            data = resp.json()
            code = data.get("code")
            if code != 0:
                raise RuntimeError(f"Feishu token error {code}: {data.get('msg')}")
            self._token = data["tenant_access_token"]
            self._expires_at = now + int(data.get("expire", 3600))
            return self._token

    async def send_card(self, open_ids: list[str], card: dict[str, Any]) -> dict[str, Any]:
        token = await self._ensure_token()
        results: dict[str, Any] = {"success": [], "failed": []}
        async with httpx.AsyncClient(timeout=15) as client:
            for oid in open_ids:
                try:
                    resp = await client.post(
                        f"{FEISHU_BASE}/open-apis/im/v1/messages",
                        params={"receive_id_type": "open_id"},
                        headers={"Authorization": f"Bearer {token}"},
                        json={
                            "receive_id": oid,
                            "msg_type": "interactive",
                            "content": json.dumps(card),
                        },
                    )
                    data = resp.json()
                    if data.get("code") == 0:
                        results["success"].append(oid)
                    else:
                        logger.warning(f"Feishu send to {oid} failed: {data}")
                        results["failed"].append({"id": oid, "error": str(data)})
                except Exception as exc:
                    logger.error(f"Feishu send to {oid} error: {exc}")
                    results["failed"].append({"id": oid, "error": str(exc)})
        return results


# ---------------------------------------------------------------------------
# Card builder
# ---------------------------------------------------------------------------

def build_alert_card(notification: AlertNotification) -> dict[str, Any]:
    """Build a Feishu interactive card for a single alert."""
    return {
        "config": {"wide_screen_mode": True},
        "header": {
            "title": {"tag": "plain_text", "content": notification.card_title()},
            "template": notification.severity_color,
        },
        "elements": [
            {
                "tag": "div",
                "text": {
                    "tag": "lark_md",
                    "content": (
                        f"**指标：** {notification.measure_name}（{notification.measure_code}）\n"
                        f"**当前值：** {notification.actual_value}\n"
                        f"**触发条件：** {notification.threshold_desc}\n"
                        f"**原因：** {notification.reason}\n"
                        f"**触发时间：** {notification.triggered_at}"
                    ),
                },
            },
            {"tag": "hr"},
            {
                "tag": "note",
                "elements": [
                    {"tag": "plain_text", "content": notification.assignee_name and f"责任人：{notification.assignee_name}" or "未指定责任人"}
                ],
            },
        ],
    }


# ---------------------------------------------------------------------------
# Public API
# ---------------------------------------------------------------------------

_feishu: Optional[_FeishuClient] = None


def _get_feishu() -> _FeishuClient:
    global _feishu
    if _feishu is None:
        _feishu = _FeishuClient()
    return _feishu


async def send_alert(notification: AlertNotification) -> bool:
    """Send one alert notification via configured channels. Returns True if at least one channel succeeded."""
    
    # Persist log entry first
    log_id = models.insert_notify_log(
        alert_log_id=notification.alert_log_id,
        channel="feishu",
        receiver_id=notification.assignee_id,
        receiver_name=notification.assignee_name,
        content=json.dumps(build_alert_card(notification), ensure_ascii=False),
        status="pending",
    )
    
    if not FEISHU_APP_ID:
        models.update_notify_log(log_id, "skipped", error_msg="Feishu not configured")
        return False

    # Resolve receiver -> open_id list
    open_ids: list[str] = []
    if notification.assignee_id:
        open_ids.append(notification.assignee_id)
    
    if not open_ids:
        models.update_notify_log(log_id, "skipped", error_msg="No receivers")
        return False

    try:
        card = build_alert_card(notification)
        result = await _get_feishu().send_card(open_ids, card)
        success = bool(result["success"])
        models.update_notify_log(
            log_id,
            "sent" if success else "failed",
            error_msg=json.dumps(result["failed"]) if result["failed"] else None,
        )
        return success
    except Exception as exc:
        models.update_notify_log(log_id, "failed", error_msg=str(exc))
        return False


async def send_test_notification(feishu_open_id: str) -> dict[str, Any]:
    """Send a test card to verify Feishu connectivity."""
    card = {
        "config": {"wide_screen_mode": True},
        "header": {
            "title": {"tag": "plain_text", "content": "🔔 InsightMind 预警测试"},
            "template": "blue",
        },
        "elements": [
            {
                "tag": "div",
                "text": {
                    "tag": "lark_md",
                    "content": "这是一条测试消息，确认飞书通知通道配置正确。",
                },
            },
        ],
    }
    return await _get_feishu().send_card([feishu_open_id], card)
