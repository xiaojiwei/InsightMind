"""Load process-level settings from a local ``.env`` file."""

from __future__ import annotations

import os
import re
from collections.abc import MutableMapping
from pathlib import Path


_ASSIGNMENT = re.compile(
    r"^(?:export\s+)?(?P<key>[A-Za-z_][A-Za-z0-9_]*)\s*=\s*(?P<value>.*)$"
)
_QUOTED_VALUE = re.compile(
    r"^(?P<quote>['\"])(?P<value>.*)(?P=quote)(?:\s+#.*)?$"
)


def _parse_value(raw_value: str) -> str:
    value = raw_value.strip()
    quoted = _QUOTED_VALUE.fullmatch(value)
    if quoted is not None:
        return quoted.group("value")
    inline_comment = re.search(r"\s+#", value)
    if inline_comment is not None:
        value = value[: inline_comment.start()].rstrip()
    return value


def load_runtime_env(
    path: Path,
    *,
    environ: MutableMapping[str, str] | None = None,
) -> set[str]:
    """Load missing variables from *path* without overriding the process env.

    The project intentionally keeps its local ``.env`` dependency-free. This
    parser supports the assignment forms used by the checked-in example while
    preserving explicitly supplied service-manager or shell variables.
    """

    target = os.environ if environ is None else environ
    if not path.is_file():
        return set()

    protected_keys = set(target)
    loaded: set[str] = set()
    for raw_line in path.read_text(encoding="utf-8").splitlines():
        line = raw_line.strip()
        if not line or line.startswith("#"):
            continue
        match = _ASSIGNMENT.fullmatch(line)
        if match is None:
            continue

        key = match.group("key")
        if key in protected_keys:
            continue
        target[key] = _parse_value(match.group("value"))
        loaded.add(key)

    return loaded
