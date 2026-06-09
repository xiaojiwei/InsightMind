package com.graphinsight.indicator.model;

import lombok.Data;

import java.util.*;

@Data
public class QueryResult {

    /**
     * 下载唯一标识
     */
    String downloadId;

    /**
     * 列信息
     */
    private List<QueryResultColumnInfo> infos;

    /**
     * 数据结果集
     */
    private List<List<String>> values = new ArrayList<>();

    /**
     * 数据结果集 Map形式（含 key）
     */
    private List<Map<String, Object>> valueMap;

    /**
     * 指标明细下的column name
     */
    private String[] columnNames;

    /**
     * 列类型集合
     */
    private Map<String, String> colTypeMap = new HashMap<>();

    public void setValueMap(List<Map<String, Object>> valueMap) {

        for (Map<String, Object> strObjMap : valueMap) {

            List<String> rowValueList = new LinkedList<String>();
            Set<String> keySet = strObjMap.keySet();

            for (String key : keySet) {
                String value = String.valueOf(strObjMap.get(key));
                rowValueList.add(value);
            }

            this.values.add(rowValueList);

        }

        this.valueMap = valueMap;

    }

}
