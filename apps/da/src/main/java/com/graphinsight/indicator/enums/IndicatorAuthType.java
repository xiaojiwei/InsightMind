package com.graphinsight.indicator.enums;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Author: lixiaolong
 * Date: 2022/11/28
 * Desc: 权限类型
 */
@Slf4j
public enum IndicatorAuthType {

    /**
     * [10,20,25,30,40]
     */
    READ_ONLY(0,  "查看"),
    EXPORT(1,  "导出"),
    EDIT(2,  "编辑"),
    MANAGE(3,  "管理");

    public static final List<IndicatorAuthType> MANAGER_AUTH_SET;
    public static final String MANAGER_AUTH_SET_STR;
    public static final String COMMA = ",";

    static {
        MANAGER_AUTH_SET = new ArrayList<>();
        MANAGER_AUTH_SET.add(READ_ONLY);
        MANAGER_AUTH_SET.add(EXPORT);
        MANAGER_AUTH_SET.add(EDIT);
        MANAGER_AUTH_SET.add(MANAGE);
        MANAGER_AUTH_SET_STR = MANAGER_AUTH_SET.stream().map(type -> type.getCode().toString()).collect(Collectors.joining(COMMA));
    }

    private Integer code;


    private String desc;

    IndicatorAuthType(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public static List<IndicatorAuthType> listByCodes(String codes) {
        List<IndicatorAuthType> types = new ArrayList<>();
        try {
            if (codes == null) {
                return types;
            }
            String[] codeSet = codes.split(COMMA);
            if (codeSet.length == 0){
                IndicatorAuthType authType = getByCode(Integer.valueOf(codes));
                if (authType != null){
                    types.add(authType);
                }
            }
            for (String s : codeSet) {
                IndicatorAuthType authType = getByCode(Integer.valueOf(s));
                if (authType != null){
                    types.add(authType);
                }
            }
            return types;
        } catch (Exception e) {
            log.error("根据code转换权限类型异常:",e);
            return types;
        }
    }


    public static IndicatorAuthType getByCode(Integer code) {
        if (code == null) {
            return null;
        }
        IndicatorAuthType[] values = IndicatorAuthType.values();
        for (IndicatorAuthType value : values) {
            if (value.code.intValue() == code.intValue()) {
                return value;
            }
        }
        return null;
    }

    @JsonValue
    public Integer getCode() {
        return code;
    }

    public String getDesc() {
        return desc;
    }


}
