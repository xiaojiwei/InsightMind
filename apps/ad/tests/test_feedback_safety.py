from dataclasses import dataclass, field

from kg_builder.feedback.classifier import classify_error
from kg_builder.feedback import service as feedback_service
from kg_builder.feedback.sanitizer import compact_json, json_loads, sanitize_payload, sanitize_text
from kg_builder.feedback.schema_snapshot import build_snapshot, diff_snapshots


@dataclass
class Column:
    name: str
    data_type: str = "varchar"
    is_nullable: bool = True
    is_pk: bool = False
    comment: str = ""


@dataclass
class Table:
    name: str
    schema: str = "sales"
    comment: str = ""
    is_view: bool = False
    primary_keys: list[str] = field(default_factory=list)
    columns: list[Column] = field(default_factory=list)


@dataclass
class Schema:
    db_type: str = "mysql"
    host: str = "secret-host"
    port: int = 3306
    database: str = "sales"
    schema_name: str = "sales"
    username: str = "root"
    password: str = "do-not-store"
    tables: list[Table] = field(default_factory=list)


class SnapshotFeedbackStore:
    def __init__(self):
        self.snapshots = []
        self.events = []

    def latest_snapshot(self, datasource_key):
        matches = [item for item in self.snapshots if item["datasource_key"] == datasource_key]
        return matches[-1] if matches else None

    def save_snapshot(self, values):
        snapshot_id = f"snapshot-{len(self.snapshots) + 1}"
        self.snapshots.append({**values, "snapshot_id": snapshot_id})
        return snapshot_id

    def add_event(self, values):
        event = {
            "eventType": values["event_type"],
            "eventName": values["event_name"],
            "payload": values.get("payload", {}),
        }
        self.events.append(event)
        return event

    def list_events(self, *, event_type="", event_name="", **_filters):
        items = [
            event for event in self.events
            if (not event_type or event["eventType"] == event_type)
            and (not event_name or event["eventName"] == event_name)
        ]
        return {"items": items, "total": len(items)}


def test_error_classifier_normalizes_missing_column() -> None:
    assert classify_error("Unknown column 'orders.channel_code'") == "COLUMN_NOT_FOUND"
    assert classify_error(
        "Unknown column 'orders.channel_code'", "query_execution_error"
    ) == "COLUMN_NOT_FOUND"
    assert classify_error("anything", "metric_not_found") == "METRIC_NOT_FOUND"


def test_sanitizer_removes_credentials_and_personal_values() -> None:
    payload = sanitize_payload({
        "password": "secret",
        "authorization": "Bearer abc",
        "comment": "联系 13800138000 或 test@example.com",
    })

    assert "password" not in payload
    assert "authorization" not in payload
    assert payload["comment"] == "联系 1********** 或 ***@***"
    assert "secret" not in sanitize_text("password=secret")


def test_compact_json_remains_decodable_when_truncated() -> None:
    encoded = compact_json({"comment": "x" * 200}, max_chars=80)

    assert json_loads(encoded, {})["_truncated"] is True


def test_schema_snapshot_is_whitelisted_and_diff_detects_removed_column() -> None:
    before_schema = Schema(tables=[Table(name="orders", columns=[
        Column(name="id", data_type="bigint", is_pk=True),
        Column(name="channel_code"),
    ])])
    after_schema = Schema(tables=[Table(name="orders", columns=[
        Column(name="id", data_type="bigint", is_pk=True),
    ])])

    datasource_key, before = build_snapshot(before_schema)
    _, after = build_snapshot(after_schema)
    changes = diff_snapshots(before, after)

    assert datasource_key
    assert "password" not in str(before).lower()
    assert "secret-host" not in str(before)
    assert changes == [{
        "eventName": "COLUMN_REMOVED",
        "table": "orders",
        "column": "channel_code",
        "before": {
            "dataType": "varchar",
            "nullable": True,
            "primaryKey": False,
            "comment": "",
        },
    }]


def test_recorded_schema_snapshots_emit_removed_column_event(monkeypatch) -> None:
    feedback_store = SnapshotFeedbackStore()
    monkeypatch.setattr(feedback_service, "store", feedback_store)
    before = Schema(tables=[Table(name="orders", columns=[
        Column(name="id", data_type="bigint", is_pk=True),
        Column(name="channel_code"),
    ])])
    after = Schema(tables=[Table(name="orders", columns=[
        Column(name="id", data_type="bigint", is_pk=True),
    ])])

    feedback_service.record_schema_snapshot(before)
    result = feedback_service.record_schema_snapshot(after)
    events = feedback_store.list_events(event_type="data", event_name="COLUMN_REMOVED")

    assert result["changeCount"] == 1
    assert events["total"] == 1
    assert events["items"][0]["payload"]["table"] == "orders"
    assert events["items"][0]["payload"]["column"] == "channel_code"
