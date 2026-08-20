from datetime import datetime

from kg_builder.feedback.mysql_config import FeedbackMySQLConfig, load_feedback_mysql_config
from kg_builder.feedback.store import FeedbackStore


class FakeCursor:
    def __init__(self, statements):
        self.statements = statements

    def __enter__(self):
        return self

    def __exit__(self, *_args):
        return False

    def execute(self, sql, params=None):
        self.statements.append((" ".join(sql.split()), params))
        return 1

    def fetchone(self):
        return None

    def fetchall(self):
        return []


class FakeConnection:
    def __init__(self, statements):
        self.statements = statements
        self.committed = False
        self.rolled_back = False
        self.closed = False

    def cursor(self):
        return FakeCursor(self.statements)

    def commit(self):
        self.committed = True

    def rollback(self):
        self.rolled_back = True

    def close(self):
        self.closed = True


def test_feedback_store_initializes_mysql_tables() -> None:
    statements = []
    connections = []

    def connect():
        connection = FakeConnection(statements)
        connections.append(connection)
        return connection

    store = FeedbackStore(
        FeedbackMySQLConfig("localhost", 3306, "user", "password", "insightmind"),
        connection_factory=connect,
    )
    store.init()

    ddl = "\n".join(sql for sql, _params in statements)
    assert "CREATE TABLE IF NOT EXISTS feedback_query_trace" in ddl
    assert "CREATE TABLE IF NOT EXISTS feedback_event" in ddl
    assert "CREATE TABLE IF NOT EXISTS feedback_schema_snapshot" in ddl
    assert "CREATE TABLE IF NOT EXISTS semantic_query_plan" in ddl
    assert "CREATE TABLE IF NOT EXISTS semantic_correction_step" in ddl
    assert "CREATE TABLE IF NOT EXISTS semantic_memory" in ddl
    assert "CREATE TABLE IF NOT EXISTS semantic_memory_review" in ddl
    assert "CREATE TABLE IF NOT EXISTS semantic_dictionary_entry" in ddl
    assert "CREATE TABLE IF NOT EXISTS semantic_eval_case" in ddl
    assert "CREATE TABLE IF NOT EXISTS semantic_eval_run" in ddl
    assert "CREATE TABLE IF NOT EXISTS semantic_eval_result" in ddl
    assert ddl.count("ENGINE=InnoDB") == 11
    assert "sqlite" not in ddl.lower()
    assert connections[0].committed is True
    assert connections[0].closed is True


def test_feedback_store_uses_mysql_parameter_binding_and_upsert() -> None:
    statements = []
    store = FeedbackStore(
        FeedbackMySQLConfig("localhost", 3306, "user", "password", "insightmind"),
        connection_factory=lambda: FakeConnection(statements),
    )
    store.init()
    store.begin_trace({
        "trace_id": "trace-1",
        "question_text": "按渠道查看订单量",
        "question_hash": "hash",
    })

    insert_sql, params = statements[-1]
    assert "INSERT INTO feedback_query_trace" in insert_sql
    assert "ON DUPLICATE KEY UPDATE" in insert_sql
    assert "%s" in insert_sql
    assert params[0] == "trace-1"
    assert isinstance(params[-1], datetime)


def test_semantic_plan_and_memory_use_mysql_upserts() -> None:
    statements = []
    store = FeedbackStore(
        FeedbackMySQLConfig("localhost", 3306, "user", "password", "insightmind"),
        connection_factory=lambda: FakeConnection(statements),
    )
    store.init()
    plan_id = store.save_semantic_plan({
        "trace_id": "trace-plan-1",
        "source": "dashboard",
        "status": "succeeded",
        "plan_hash": "a" * 64,
        "plan": {
            "version": "1.0",
            "versions": {"businessKgHash": "b" * 64, "ontologyVersion": "1.1.0"},
        },
        "explain_plan": {"version": "1.0"},
    })
    plan_sql, plan_params = statements[-1]

    assert plan_id.startswith("plan_")
    assert "INSERT INTO semantic_query_plan" in plan_sql
    assert "ON DUPLICATE KEY UPDATE" in plan_sql
    assert plan_params[1] == "trace-plan-1"

    store.create_pending_memory({
        "trace_id": "trace-plan-1",
        "plan_id": plan_id,
        "plan_hash": "a" * 64,
        "question_hash": "c" * 64,
    })
    memory_inserts = [item for item in statements if "INSERT INTO semantic_memory" in item[0]]
    assert len(memory_inserts) == 1
    assert "ON DUPLICATE KEY UPDATE" in memory_inserts[0][0]


def test_dictionary_resubmission_forces_a_new_pending_review() -> None:
    statements = []
    store = FeedbackStore(
        FeedbackMySQLConfig("localhost", 3306, "user", "password", "insightmind"),
        connection_factory=lambda: FakeConnection(statements),
    )
    store.init()

    store.create_dictionary_proposal({
        "semantic_type": "measure",
        "term_text": "成交额",
        "normalized_term": "成交额",
        "canonical_code": "MEAS_gmv",
    })

    insert_sql = next(
        sql for sql, _params in reversed(statements)
        if "INSERT INTO semantic_dictionary_entry" in sql
    )
    assert "status='PENDING'" in insert_sql
    assert "reviewed_at=NULL" in insert_sql


def test_backend_event_keeps_payload_status_without_query_trace() -> None:
    row = {
        "event_id": "event-1",
        "trace_id": "",
        "event_type": "execution",
        "event_name": "QUERY_API_SUCCEEDED",
        "source": "backend_middleware",
        "payload_json": '{"status":"succeeded","elapsedMs":23,"errorCode":""}',
        "created_at": datetime(2026, 8, 18, 10, 0, 0),
        "question_text": None,
        "query_mode": None,
        "status": None,
        "diagnostic_code": None,
        "error_code": None,
        "elapsed_ms": None,
        "measure_codes_json": None,
        "dimension_codes_json": None,
        "fact_tables_json": None,
    }

    event = FeedbackStore._event_row(row)

    assert event["status"] == "succeeded"
    assert event["elapsedMs"] == 23


def test_feedback_mysql_config_prefers_local_mysql_datasource(tmp_path, monkeypatch) -> None:
    (tmp_path / "config.local.yaml").write_text(
        """
datasources:
  - name: local
    type: mysql
    host: mysql.local
    port: 3307
    database: insightmind
    username: feedback_user
    password: local-secret
""",
        encoding="utf-8",
    )
    for name in (
        "FEEDBACK_DB_HOST", "FEEDBACK_DB_PORT", "FEEDBACK_DB_USER",
        "FEEDBACK_DB_PASSWORD", "FEEDBACK_DB_NAME", "MYSQL_PWD",
    ):
        monkeypatch.delenv(name, raising=False)

    config = load_feedback_mysql_config(tmp_path)

    assert config.host == "mysql.local"
    assert config.port == 3307
    assert config.database == "insightmind"
    assert config.user == "feedback_user"
    assert config.password == "local-secret"


def test_feedback_mysql_config_environment_overrides_datasource(tmp_path, monkeypatch) -> None:
    (tmp_path / "config.yaml").write_text(
        "datasources: [{type: mysql, database: fallback}]\n", encoding="utf-8"
    )
    monkeypatch.setenv("FEEDBACK_DB_HOST", "feedback-db")
    monkeypatch.setenv("FEEDBACK_DB_PORT", "3308")
    monkeypatch.setenv("FEEDBACK_DB_USER", "writer")
    monkeypatch.setenv("FEEDBACK_DB_PASSWORD", "env-secret")
    monkeypatch.setenv("FEEDBACK_DB_NAME", "feedback")

    config = load_feedback_mysql_config(tmp_path)

    assert config == FeedbackMySQLConfig(
        host="feedback-db",
        port=3308,
        user="writer",
        password="env-secret",
        database="feedback",
        charset="utf8mb4",
    )
