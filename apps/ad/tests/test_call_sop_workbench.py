from web_app import _call_workbench_date_label, _call_workbench_time_label


def test_workbench_formats_da_datetime_arrays():
    value = [2026, 7, 2, 20, 28, 37]

    assert _call_workbench_date_label(value) == "07-02"
    assert _call_workbench_time_label(value) == "20:28"


def test_workbench_keeps_iso_datetime_formatting():
    value = "2026-07-02T20:28:37"

    assert _call_workbench_date_label(value) == "07-02"
    assert _call_workbench_time_label(value) == "20:28"
