"""HTTP helpers that keep loopback service calls off system proxies."""

from __future__ import annotations

import socket
import urllib.request
from typing import Any, Optional
from urllib.parse import urlsplit


_LOOPBACK_HOSTS = {"localhost", "127.0.0.1", "::1"}
_DIRECT_LLM_HOSTS = {"api.deepseek.com"}
_DIRECT_OPENER = urllib.request.build_opener(urllib.request.ProxyHandler({}))


def _target_url(target: Any) -> str:
    if isinstance(target, urllib.request.Request):
        return target.full_url
    return str(target)


def is_loopback_url(target: Any) -> bool:
    """Return whether an HTTP request targets the local machine."""
    try:
        return (urlsplit(_target_url(target)).hostname or "").lower() in _LOOPBACK_HOSTS
    except (TypeError, ValueError):
        return False


def should_bypass_proxy(target: Any) -> bool:
    """Return whether a request must avoid system/VPN proxy configuration."""
    try:
        host = (urlsplit(_target_url(target)).hostname or "").lower()
        return host in _LOOPBACK_HOSTS or host in _DIRECT_LLM_HOSTS
    except (TypeError, ValueError):
        return False


def urlopen(
    target: Any,
    data: Optional[bytes] = None,
    timeout: Any = socket._GLOBAL_DEFAULT_TIMEOUT,
    *,
    context: Any = None,
):
    """Open a URL, bypassing HTTP/SOCKS proxies for loopback destinations.

    macOS proxy/VPN applications can leave dynamic proxy settings active while
    their local proxy listener is being stopped or restarted.  AD-to-DA calls
    must remain local and deterministic during those transitions.
    """
    if should_bypass_proxy(target):
        return _DIRECT_OPENER.open(target, data=data, timeout=timeout)
    return urllib.request.urlopen(
        target,
        data=data,
        timeout=timeout,
        context=context,
    )
