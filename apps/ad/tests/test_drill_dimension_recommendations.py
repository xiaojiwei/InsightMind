from web_app import _ad_drill_dimension_candidates


def test_drill_dimension_recommendation_scores_are_distinct():
    result = _ad_drill_dimension_candidates({
        "measure": "ad.quality_record_count",
        "currentMember": "ad.store_city",
        "contextMembers": ["ad.store_city"],
        "limit": 12,
    })

    items = result["items"]
    scores = [item["score"] for item in items]

    assert len(items) >= 5
    assert len(scores) == len(set(scores))
    assert scores == sorted(scores, reverse=True)


def test_city_drill_recommendations_keep_business_specific_reasons():
    result = _ad_drill_dimension_candidates({
        "measure": "ad.quality_record_count",
        "currentMember": "ad.store_city",
        "contextMembers": ["ad.store_city"],
        "limit": 12,
    })

    by_member = {item["member"]: item for item in result["items"]}

    assert by_member["ad.sales_expert"]["score"] > by_member["ad.store_manager"]["score"]
    assert by_member["ad.quality_issue_category"]["reason"] != by_member["ad.store"]["reason"]
    assert all("相近业务主题" not in item["reason"] for item in result["items"])
