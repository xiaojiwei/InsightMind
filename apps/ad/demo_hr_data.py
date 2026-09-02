#!/usr/bin/env python3
"""Seed deterministic, fully synthetic HR data for the bundled dashboards."""

from __future__ import annotations

import argparse
import re
from datetime import date, timedelta
from typing import Any

import pymysql


_SAFE_DATABASE_NAME = re.compile(r"^[A-Za-z0-9_]+$")

_DDL_STATEMENTS = (
    """
    CREATE TABLE IF NOT EXISTS regions (
      region_id INT PRIMARY KEY,
      region_name VARCHAR(64) NOT NULL
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
    """,
    """
    CREATE TABLE IF NOT EXISTS countries (
      country_id CHAR(2) PRIMARY KEY,
      country_name VARCHAR(64) NOT NULL,
      region_id INT NOT NULL,
      KEY idx_countries_region (region_id)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
    """,
    """
    CREATE TABLE IF NOT EXISTS locations (
      location_id INT PRIMARY KEY,
      street_address VARCHAR(128),
      postal_code VARCHAR(24),
      city VARCHAR(64) NOT NULL,
      state_province VARCHAR(64),
      country_id CHAR(2) NOT NULL,
      KEY idx_locations_country (country_id)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
    """,
    """
    CREATE TABLE IF NOT EXISTS departments (
      department_id INT PRIMARY KEY,
      department_name VARCHAR(96) NOT NULL,
      manager_id INT NULL,
      location_id INT NOT NULL,
      KEY idx_departments_manager (manager_id),
      KEY idx_departments_location (location_id)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
    """,
    """
    CREATE TABLE IF NOT EXISTS jobs (
      job_id VARCHAR(16) PRIMARY KEY,
      job_title VARCHAR(96) NOT NULL,
      min_salary DECIMAL(12,2) NOT NULL,
      max_salary DECIMAL(12,2) NOT NULL
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
    """,
    """
    CREATE TABLE IF NOT EXISTS employees (
      employee_id INT PRIMARY KEY,
      first_name VARCHAR(48) NOT NULL,
      last_name VARCHAR(48) NOT NULL,
      email VARCHAR(96) NOT NULL UNIQUE,
      phone_number VARCHAR(32),
      hire_date DATE NOT NULL,
      salary DECIMAL(12,2) NOT NULL,
      job_id VARCHAR(16) NOT NULL,
      manager_id INT NULL,
      department_id INT NOT NULL,
      commission_pct DECIMAL(5,4) NULL,
      KEY idx_employees_job (job_id),
      KEY idx_employees_manager (manager_id),
      KEY idx_employees_department (department_id)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
    """,
    """
    CREATE TABLE IF NOT EXISTS job_history (
      employee_id INT NOT NULL,
      start_date DATE NOT NULL,
      end_date DATE NOT NULL,
      job_id VARCHAR(16) NOT NULL,
      department_id INT NOT NULL,
      PRIMARY KEY (employee_id, start_date),
      KEY idx_job_history_end_date (end_date)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
    """,
)

_INSERT_SQL = {
    "regions": "INSERT INTO regions (region_id, region_name) VALUES (%s, %s)",
    "countries": "INSERT INTO countries (country_id, country_name, region_id) VALUES (%s, %s, %s)",
    "locations": """
        INSERT INTO locations (location_id, street_address, postal_code, city, state_province, country_id)
        VALUES (%s, %s, %s, %s, %s, %s)
    """,
    "departments": """
        INSERT INTO departments (department_id, department_name, manager_id, location_id)
        VALUES (%s, %s, %s, %s)
    """,
    "jobs": "INSERT INTO jobs (job_id, job_title, min_salary, max_salary) VALUES (%s, %s, %s, %s)",
    "employees": """
        INSERT INTO employees
          (employee_id, first_name, last_name, email, phone_number, hire_date, salary, job_id, manager_id, department_id, commission_pct)
        VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s)
    """,
    "job_history": """
        INSERT INTO job_history (employee_id, start_date, end_date, job_id, department_id)
        VALUES (%s, %s, %s, %s, %s)
    """,
}


def validate_database_name(database: str) -> str:
    """Return a database identifier safe to interpolate into MySQL DDL."""
    if not _SAFE_DATABASE_NAME.fullmatch(database):
        raise ValueError("database must contain only letters, digits, and underscores")
    return database


def _employee_row(index: int, manager_ids: tuple[int, ...]) -> tuple[Any, ...]:
    employee_id = 100 + index
    department_id = 10 * (((index - 1) % 10) + 1)
    jobs = (
        ("AD_PRES", 24000, 40000),
        ("AD_VP", 17000, 28000),
        ("HR_MGR", 11000, 18000),
        ("FIN_MGR", 12000, 19000),
        ("SALES_MGR", 12000, 20000),
        ("IT_PROG", 7500, 15000),
        ("HR_REP", 5500, 10500),
        ("SA_REP", 5000, 12000),
        ("MK_REP", 5000, 11000),
        ("FI_ACCOUNT", 6500, 12000),
    )
    if index == 1:
        job_id, min_salary, max_salary = jobs[0]
        manager_id = None
        department_id = 10
    elif index <= 11:
        job_id, min_salary, max_salary = jobs[1]
        manager_id = 101
    elif index <= 21:
        job_id, min_salary, max_salary = jobs[(index - 12) % 5 + 2]
        manager_id = 102 + ((index - 12) % 10)
    else:
        job_id, min_salary, max_salary = jobs[(index - 22) % (len(jobs) - 2) + 2]
        manager_id = manager_ids[(index - 22) % len(manager_ids)]

    salary = min_salary + ((index * 479) % (max_salary - min_salary + 1))
    if index % 17 == 0:
        salary = max_salary + 900
    elif index % 13 == 0:
        salary = min_salary - 650

    hire_date = date(2011, 1, 10) + timedelta(days=(index * 43) % 4920)
    commission_pct = 0.12 if job_id == "SA_REP" and index % 3 else None
    return (
        employee_id,
        f"Demo{index:03d}",
        f"Employee{index:03d}",
        f"demo{index:03d}@example.test",
        f"+1.555.{index:04d}",
        hire_date,
        salary,
        job_id,
        manager_id,
        department_id,
        commission_pct,
    )


def build_demo_rows() -> dict[str, list[tuple[Any, ...]]]:
    """Build a fixed, realistic-enough HR dataset without database access."""
    rows: dict[str, list[tuple[Any, ...]]] = {
        "regions": [(1, "Americas"), (2, "Europe"), (3, "Asia Pacific")],
        "countries": [
            ("US", "United States", 1),
            ("CA", "Canada", 1),
            ("GB", "United Kingdom", 2),
            ("DE", "Germany", 2),
            ("CN", "China", 3),
        ],
        "locations": [
            (1000, "1 Demo Plaza", "94105", "San Francisco", "California", "US"),
            (1100, "2 Demo Way", "10001", "New York", "New York", "US"),
            (1200, "3 Demo Road", "M5V", "Toronto", "Ontario", "CA"),
            (1300, "4 Demo Street", "EC1A", "London", "England", "GB"),
            (1400, "5 Demo Allee", "10115", "Berlin", "Berlin", "DE"),
            (1500, "6 Demo Avenue", "200000", "Shanghai", "Shanghai", "CN"),
        ],
        "departments": [
            (10, "Executive", 101, 1000),
            (20, "Finance", 102, 1100),
            (30, "Human Resources", 103, 1000),
            (40, "Information Technology", 104, 1200),
            (50, "Sales", 105, 1300),
            (60, "Marketing", 106, 1300),
            (70, "Operations", 107, 1400),
            (80, "Customer Success", 108, 1500),
            (90, "Product", 109, 1500),
            (100, "Legal", 110, 1100),
            (110, "Future Workforce", None, 1500),
        ],
        "jobs": [
            ("AD_PRES", "President", 24000, 40000),
            ("AD_VP", "Vice President", 17000, 28000),
            ("HR_MGR", "HR Manager", 11000, 18000),
            ("FIN_MGR", "Finance Manager", 12000, 19000),
            ("SALES_MGR", "Sales Manager", 12000, 20000),
            ("IT_PROG", "Application Developer", 7500, 15000),
            ("HR_REP", "HR Specialist", 5500, 10500),
            ("SA_REP", "Sales Representative", 5000, 12000),
            ("MK_REP", "Marketing Specialist", 5000, 11000),
            ("FI_ACCOUNT", "Financial Analyst", 6500, 12000),
        ],
    }
    manager_ids = tuple(range(102, 122))
    rows["employees"] = [_employee_row(index, manager_ids) for index in range(1, 108)]
    rows["job_history"] = [
        (
            112 + offset,
            date(2013, 1, 1) + timedelta(days=offset * 91),
            date(2015, 1, 1) + timedelta(days=offset * 91),
            "HR_REP" if offset % 2 else "IT_PROG",
            30 if offset % 3 else 40,
        )
        for offset in range(16)
    ]
    return rows


def seed_database(*, host: str, port: int, user: str, password: str, database: str) -> None:
    """Create the HR source schema and insert deterministic synthetic rows."""
    database = validate_database_name(database)
    rows = build_demo_rows()
    connection = pymysql.connect(
        host=host,
        port=port,
        user=user,
        password=password,
        database=database,
        charset="utf8mb4",
    )
    try:
        with connection.cursor() as cursor:
            for statement in _DDL_STATEMENTS:
                cursor.execute(statement)
            for table_name in reversed(tuple(rows)):
                cursor.execute(f"DELETE FROM `{table_name}`")
            for table_name, table_rows in rows.items():
                cursor.executemany(_INSERT_SQL[table_name], table_rows)
        connection.commit()
    finally:
        connection.close()


def _arguments() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Seed the synthetic InsightMind HR demo database")
    parser.add_argument("--host", default="127.0.0.1")
    parser.add_argument("--port", type=int, default=3306)
    parser.add_argument("--user", default="root")
    parser.add_argument("--password", default="root")
    parser.add_argument("--database", default="HRRDB")
    return parser.parse_args()


def main() -> None:
    args = _arguments()
    seed_database(
        host=args.host,
        port=args.port,
        user=args.user,
        password=args.password,
        database=args.database,
    )
    print(f"Synthetic HR demo data installed in {args.database}: 107 employees")


if __name__ == "__main__":
    main()
