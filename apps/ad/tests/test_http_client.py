import urllib.request

from kg_builder.utils import http_client


class _FakeOpener:
    def __init__(self):
        self.calls = []

    def open(self, target, data=None, timeout=None):
        self.calls.append((target, data, timeout))
        return "direct"


def test_loopback_urls_bypass_system_proxy(monkeypatch):
    opener = _FakeOpener()
    monkeypatch.setattr(http_client, "_DIRECT_OPENER", opener)
    monkeypatch.setattr(
        urllib.request,
        "urlopen",
        lambda *_args, **_kwargs: (_ for _ in ()).throw(
            AssertionError("system-proxy urlopen must not be used")
        ),
    )

    assert http_client.urlopen("http://127.0.0.1:8091/health", timeout=3) == "direct"
    assert http_client.urlopen("http://localhost:8091/health", timeout=3) == "direct"
    assert http_client.urlopen("http://[::1]:8091/health", timeout=3) == "direct"
    assert len(opener.calls) == 3


def test_external_urls_keep_default_proxy_behavior(monkeypatch):
    calls = []

    def fake_urlopen(target, data=None, timeout=None, context=None):
        calls.append((target, data, timeout, context))
        return "default"

    monkeypatch.setattr(urllib.request, "urlopen", fake_urlopen)

    assert http_client.urlopen("https://api.example.com/v1", timeout=5) == "default"
    assert len(calls) == 1
