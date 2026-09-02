from demo_hr_data import build_demo_rows, validate_database_name


def test_build_demo_rows_is_deterministic_and_complete():
    first = build_demo_rows()

    assert first == build_demo_rows()
    assert set(first) == {
        "regions",
        "countries",
        "locations",
        "departments",
        "jobs",
        "employees",
        "job_history",
    }
    assert len(first["employees"]) == 107
    assert len(first["job_history"]) >= 12


def test_employees_reference_known_departments_jobs_and_managers():
    rows = build_demo_rows()
    department_ids = {row[0] for row in rows["departments"]}
    job_ids = {row[0] for row in rows["jobs"]}
    employee_ids = {row[0] for row in rows["employees"]}

    for employee in rows["employees"]:
        assert employee[7] in job_ids
        assert employee[9] in department_ids
        assert employee[8] is None or employee[8] in employee_ids


def test_database_name_is_limited_to_safe_mysql_identifiers():
    assert validate_database_name("HRRDB") == "HRRDB"
    assert validate_database_name("hr_demo_2026") == "hr_demo_2026"

    for invalid in ("", "hrrdb; DROP DATABASE mysql", "hr-demo", "hrrdb name"):
        try:
            validate_database_name(invalid)
        except ValueError:
            continue
        raise AssertionError(f"unsafe database name accepted: {invalid!r}")
