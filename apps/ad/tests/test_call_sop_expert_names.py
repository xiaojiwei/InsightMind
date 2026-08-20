from web_app import _call_sop_source_expert_name


def test_extracts_source_expert_names_from_explicit_introductions():
    cases = [
        ("专家:你好，我是那个理想汽车赵聪。｜客户:你好。", "赵聪"),
        ("专家:哎，我这边那个贵和领秀城理想汽车的那个小王王珊珊。", "王珊珊"),
        ("专家:那个我是小小苏。｜客户:嗯。", "小小苏"),
        ("专家:我给你发定位。我姓丁，我丁帅，之前一直是我跟你联系。", "丁帅"),
        ("专家:哎，徐总，我是小倩，理想汽车的。", "小倩"),
        ("专家:您好，我是理想汽车产品专家李晨晨看您关注L9。", "李晨晨"),
        ("客户:喂。｜专家:哎，我那个理想的小韩姐。｜客户:你好。", "小韩"),
        ("[时间: 2026-07-02 20:18:30]\n专家:我那个理想的小韩姐。｜客户:你好。", "小韩"),
        ("专家:您好，我是理想汽车产品专家陆小龙有看您预选了L8。", "陆小龙"),
        ("专家:您好，我是理想汽车产品专家张荣哎，打扰您了。", "张荣"),
    ]
    for content, expected in cases:
        assert _call_sop_source_expert_name(
            {"expert_name": "70959", "aggregated_content": content}
        ) == expected


def test_prefers_existing_structured_name_and_does_not_invent_one():
    assert _call_sop_source_expert_name({"expert_name": "顾晨", "aggregated_content": ""}) == "顾晨"
    assert _call_sop_source_expert_name(
        {"expert_name": "87971", "aggregated_content": "专家:您好，想邀请您到店试驾。"}
    ) == ""
    assert _call_sop_source_expert_name(
        {"expert_name": "87971", "aggregated_content": "专家:我这边是那个理想汽车的哥，打扰您。"}
    ) == ""
    assert _call_sop_source_expert_name(
        {"expert_name": "34176", "aggregated_content": "专家:我是小鹏哥，之前联系过您。"}
    ) == ""
    assert _call_sop_source_expert_name(
        {"expert_name": "34176", "aggregated_content": "客户:我是王珊珊。｜专家:您好。"}
    ) == ""
    assert _call_sop_source_expert_name(
        {"expert_name": "34176", "aggregated_content": "专家:我那个理想的小伙儿。"}
    ) == ""
