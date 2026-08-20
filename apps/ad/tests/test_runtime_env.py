from pathlib import Path

from kg_builder.utils.runtime_env import load_runtime_env


def test_load_runtime_env_supports_project_assignment_forms(tmp_path: Path) -> None:
    env_file = tmp_path / ".env"
    env_file.write_text(
        """
# comment
PLAIN_TOKEN=plain-value
QUOTED_TOKEN="quoted value"
export LEGACY_TOKEN='legacy value'
DUPLICATE_TOKEN=old-value
DUPLICATE_TOKEN=new-value
INLINE_COMMENT=value-before-comment # comment
QUOTED_COMMENT="value # kept" # comment
URL_FRAGMENT=https://example.test/#fragment
INVALID LINE
""",
        encoding="utf-8",
    )
    environ: dict[str, str] = {}

    loaded = load_runtime_env(env_file, environ=environ)

    assert loaded == {
        "PLAIN_TOKEN",
        "QUOTED_TOKEN",
        "LEGACY_TOKEN",
        "DUPLICATE_TOKEN",
        "INLINE_COMMENT",
        "QUOTED_COMMENT",
        "URL_FRAGMENT",
    }
    assert environ == {
        "PLAIN_TOKEN": "plain-value",
        "QUOTED_TOKEN": "quoted value",
        "LEGACY_TOKEN": "legacy value",
        "DUPLICATE_TOKEN": "new-value",
        "INLINE_COMMENT": "value-before-comment",
        "QUOTED_COMMENT": "value # kept",
        "URL_FRAGMENT": "https://example.test/#fragment",
    }


def test_load_runtime_env_preserves_explicit_process_values(tmp_path: Path) -> None:
    env_file = tmp_path / ".env"
    env_file.write_text(
        "INSIGHTMIND_FEEDBACK_API_TOKEN=file-token\nNEW_SETTING=from-file\n",
        encoding="utf-8",
    )
    environ = {"INSIGHTMIND_FEEDBACK_API_TOKEN": "process-token"}

    loaded = load_runtime_env(env_file, environ=environ)

    assert loaded == {"NEW_SETTING"}
    assert environ["INSIGHTMIND_FEEDBACK_API_TOKEN"] == "process-token"
    assert environ["NEW_SETTING"] == "from-file"


def test_load_runtime_env_ignores_a_missing_file(tmp_path: Path) -> None:
    environ: dict[str, str] = {}

    loaded = load_runtime_env(tmp_path / "missing.env", environ=environ)

    assert loaded == set()
    assert environ == {}
