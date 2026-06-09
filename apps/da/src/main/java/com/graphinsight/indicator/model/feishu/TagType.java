package com.graphinsight.indicator.model.feishu;

/**
 * Author: lixiaolong
 * Date: 2022/10/14
 * Desc:
 */
public enum TagType {

    TEXT("plain_text","文本"),
    ACTION("ACTION","动作"),
    BUTTON("button","按钮"),
    LARD_MD("lark_md","MD格式"),
    DIV("div","DIV");

    private String code;
    private String desc;

    TagType(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getDesc() {
        return desc;
    }

    public void setDesc(String desc) {
        this.desc = desc;
    }
}
