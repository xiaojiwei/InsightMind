import json
from pathlib import Path

import yaml
from rdflib import Graph


AD_DIR = Path(__file__).parents[1]
ROOT_DIR = AD_DIR.parents[1]
DEMO_OUTPUT = ROOT_DIR / "demo" / "default" / "ad" / "output"


def test_default_ad_datasource_targets_hr_demo():
    config = yaml.safe_load((AD_DIR / "config.yaml").read_text(encoding="utf-8"))
    datasource = config["datasources"][0]

    assert datasource["database"] == "HRRDB"
    assert datasource["schema"] == "HRRDB"


def test_database_bootstrap_uses_only_hr_seed_and_views():
    script = (ROOT_DIR / "scripts" / "init-demo-db.sh").read_text(encoding="utf-8")

    assert 'HR_DEMO_DB="${HR_DEMO_DB:-HRRDB}"' in script
    assert "demo_hr_data.py" in script
    assert "hr_analytics_views.sql" in script
    assert "from kg_builder.alerts.models import init_db; init_db()" in script
    assert "It was not changed. Update it before starting the HR demo." in script
    assert "tpcds_data.py" not in script
    assert "demo_call_sop_data.py" not in script
    assert "demo_celn_data.py" not in script


def test_asset_bootstrap_uses_hr_assets_without_legacy_sanitizer():
    script = (ROOT_DIR / "scripts" / "init-demo-assets.sh").read_text(encoding="utf-8")

    assert 'cp -R "$DEMO_OUTPUT_DIR/business_kg" "$AD_OUTPUT_DIR/business_kg"' in script
    assert "sanitize_demo_business_kg.py" not in script
    assert "kg_20260901_003.ttl" in script
    assert "kg_tpcds.ttl" not in script
    assert "MYSQL_PASSWORD=\"${MYSQL_PASSWORD:-root}\"" in script
    assert "dbPassword" in script


def test_checked_in_business_graph_has_only_default_demo_credentials():
    turtle = (DEMO_OUTPUT / "business_kg" / "indicator-data.ttl").read_text(
        encoding="utf-8"
    )

    assert 'ind:dbUser "root"' in turtle
    assert 'ind:dbPassword "root"' in turtle
    assert "123456" not in turtle


def test_checked_in_hr_assets_are_parseable_and_have_two_dashboards():
    source_graph = Graph().parse(DEMO_OUTPUT / "kg_20260901_003.ttl", format="turtle")
    business_graph = Graph().parse(
        DEMO_OUTPUT / "business_kg" / "indicator-data.ttl", format="turtle"
    )
    dashboard_ids = sorted(
        path.stem for path in (DEMO_OUTPUT / "dashboards").glob("*.json")
    )

    assert len(source_graph) > 0
    assert len(business_graph) > 0
    assert dashboard_ids == [
        "dash_hr_human_capital_panorama",
        "dash_hr_talent_vitality_pulse",
    ]

    for dashboard_id in dashboard_ids:
        payload = json.loads(
            (DEMO_OUTPUT / "dashboards" / f"{dashboard_id}.json").read_text(
                encoding="utf-8"
            )
        )
        assert payload["id"] == dashboard_id


def test_documentation_and_verifier_name_the_two_hr_dashboards():
    documentation = "\n".join(
        path.read_text(encoding="utf-8")
        for path in (
            ROOT_DIR / "README.md",
            AD_DIR / "README.md",
            ROOT_DIR / "demo" / "default" / "README.md",
        )
    )

    assert "dash_hr_human_capital_panorama" in documentation
    assert "dash_hr_talent_vitality_pulse" in documentation
    assert "three call-quality dashboards" not in documentation

    verifier = (ROOT_DIR / "scripts" / "verify-hr-demo.sh").read_text(encoding="utf-8")
    assert "dash_hr_human_capital_panorama" in verifier
    assert "dash_hr_talent_vitality_pulse" in verifier
