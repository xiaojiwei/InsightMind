package com.graphinsight.indicator.enums;

import java.util.Arrays;
import java.util.List;

/**
 * @Author: lixiaolong
 * @Description: Doris数据类型枚举
 * @Date: 2021/12/3
 */
public enum  DorisDataTypeEnum {

    NUMBER(Arrays.asList("BIGINT","LARGEINT","SMALLINT","TINYINT","BOOLEAN","DECIMAL","DOUBLE","FLOAT","INT","BITMAP")),
    STRING(Arrays.asList("CHAR","VARCHAR","STRING","HLL")),
    DATE(Arrays.asList("DATE","DATETIME"));

    private List<String> type;

    DorisDataTypeEnum(List<String> type) {
        this.type = type;
    }

    public List<String> getType() {
        return type;
    }

    public static String getDataType(String dorisDataType){
        if (dorisDataType == null){
            return null;
        }
        DorisDataTypeEnum[] values = DorisDataTypeEnum.values();
        for (DorisDataTypeEnum value : values) {
            List<String> type = value.getType();
            if (type.contains(dorisDataType.toUpperCase())){
                return value.name();
            }
        }
        return null;
    }
}
