"""Read graph release identity without mutating graph files."""

from __future__ import annotations

import hashlib
import re
import threading
from collections import OrderedDict
from pathlib import Path
from typing import Any


_CACHE_LIMIT = 16
_cache: OrderedDict[tuple[str, int, int, int, int], dict[str, str]] = OrderedDict()
_lock = threading.Lock()
_VERSION_RE = re.compile(r'owl:versionInfo\s+["\']([^"\']+)["\']')


def graph_identity(path: Path) -> dict[str, str]:
    if not path.exists() or not path.is_file():
        return {"filename": "", "sha256": "", "ontologyVersion": ""}
    stat = path.stat()
    key = (
        str(path.resolve()),
        stat.st_mtime_ns,
        stat.st_ctime_ns,
        int(getattr(stat, "st_ino", 0) or 0),
        stat.st_size,
    )
    with _lock:
        cached = _cache.get(key)
        if cached:
            _cache.move_to_end(key)
            return dict(cached)
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    version = ""
    try:
        head = path.read_text(encoding="utf-8", errors="replace")[:200000]
        match = _VERSION_RE.search(head)
        version = match.group(1) if match else ""
    except OSError:
        pass
    identity = {
        "filename": path.name,
        "sha256": digest.hexdigest(),
        "ontologyVersion": version,
    }
    with _lock:
        for stale_key in [item for item in _cache if item[0] == key[0] and item != key]:
            _cache.pop(stale_key, None)
        _cache[key] = dict(identity)
        _cache.move_to_end(key)
        while len(_cache) > _CACHE_LIMIT:
            _cache.popitem(last=False)
    return identity
