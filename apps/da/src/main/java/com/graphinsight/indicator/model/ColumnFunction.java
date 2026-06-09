package com.graphinsight.indicator.model;

import org.apache.calcite.rel.type.RelDataType;
import org.apache.calcite.rel.type.RelDataTypeFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

public class ColumnFunction implements Function<RelDataTypeFactory, RelDataType> {

    private final static Map<String, Class> TYPE_MAP = new HashMap<String, Class>();
    static {
        TYPE_MAP.put("int", Integer.class);
        TYPE_MAP.put("string", String.class);
        TYPE_MAP.put("double", Double.class);
    }

    List<ColumnTypeInfo> columnTypeInfoList = null;

    public ColumnFunction(List<ColumnTypeInfo> columnTypeInfoList) {
        this.columnTypeInfoList = columnTypeInfoList;
    }

    private RelDataType transfrom(String type, RelDataTypeFactory factory) {
        Class clazz = TYPE_MAP.get(type);
        if (null == clazz) {
            clazz = TYPE_MAP.get("string");
        }
        return factory.createJavaType(clazz);
    }

    @Override
    public RelDataType apply(RelDataTypeFactory factory) {
        RelDataTypeFactory.Builder builder = new RelDataTypeFactory.Builder(factory);
        for (ColumnTypeInfo column : columnTypeInfoList) {
            builder.add(column.getName(), this.transfrom(column.getType(), factory));
        }

        return builder.build();
    }

}
