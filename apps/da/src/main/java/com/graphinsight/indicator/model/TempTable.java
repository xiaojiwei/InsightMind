package com.graphinsight.indicator.model;

import lombok.Data;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 执行回来后的临时表
 */
@Data
public class TempTable {

    /**
     * 列描述信息
     */
    private List<ColumnTypeInfo> columnTypeInfoList = new ArrayList<ColumnTypeInfo>();

    /**
     * 数据描述信息
     */
    private List<Object[]> dataList = new ArrayList<Object[]>()  ;

    /**
     * hashIndex
     */
    private Map<Map<Integer, String>, Set<Object[]>> hashIndexMap = null;

    public Map<Map<Integer, String>, Set<Object[]>> getHashIndexMap() {
        return hashIndexMap;
    }

    public void setHashIndexMap(Map<Map<Integer, String>, Set<Object[]>> hashIndexMap) {
        this.hashIndexMap = hashIndexMap;
    }

    /**
     * 构建hash索引
     */
    public void buildHashIndex() {
        this.buildHashIndex2();
//        this.buildHashIndexORG();
    }
    /**
     * 构建hash索引
     */
    public void buildHashIndexORG() {

        AtomicInteger sum = new AtomicInteger();
        Long begin = System.currentTimeMillis();
        Map<Map<Integer, String>, Set<Object[]>> hashIndexMap = new HashMap<>();
        List<Object[]> dataList = this.getDataList();
        for (Object[] objects : dataList) {
            sum.getAndIncrement();
            Map<Integer, String> allHashMetaMap = new HashMap<>();

            Set<Object[]> allSet = hashIndexMap.get(allHashMetaMap);
            if (null == allSet) {
                allSet = new LinkedHashSet<>();
                hashIndexMap.put(allHashMetaMap, allSet);
            }
            allSet.add(objects);

            for (int i = 0; i < objects.length; i++) {

                //当前单元素构建
                Map<Integer, String> rowHashMetaMap = new HashMap<>();
                rowHashMetaMap.put(i, String.valueOf(objects[i]));

                Set<Object[]> objectSet = hashIndexMap.get(rowHashMetaMap);
                if (null == objectSet) {
                    objectSet = new LinkedHashSet<>();
                    hashIndexMap.put(rowHashMetaMap, objectSet);
                }

                objectSet.add(objects);

                //构建含有之前项的复核索引
                if (i > 0) {
                    Map<Integer, String> compositeHashMetaMap = new HashMap<>();
                    for (int j = i; j >= 1; j--) {
                        compositeHashMetaMap.put(j, String.valueOf(objects[j]));
                    }
                    Set<Object[]> compositeObjectSet = hashIndexMap.get(compositeHashMetaMap);
                    if (null == compositeObjectSet) {
                        compositeObjectSet = new LinkedHashSet<>();
                        hashIndexMap.put(compositeHashMetaMap, compositeObjectSet);
                    }

                    compositeObjectSet.add(objects);
                }

            }

        }

        System.out.println(" buildHashInde cost: " + (System.currentTimeMillis() - begin) + " org cost : " + sum.intValue());
        this.hashIndexMap = hashIndexMap;

    }

    /**
     * 构建hash索引
     */
    public void buildHashIndex2() {

        Long begin = System.currentTimeMillis();
        Map<Map<Integer, String>, Set<Object[]>> hashIndexMap = new HashMap<>();
        List<Object[]> dataList = this.getDataList();
//        dataList.parallelStream().forEachOrdered(objects -> {
        dataList.stream().forEach(objects -> {

            Map<Integer, String> allHashMetaMap = new HashMap<>();

            Set<Object[]> allSet = hashIndexMap.get(allHashMetaMap);
            if (null == allSet) {
                allSet = new LinkedHashSet<>();
                hashIndexMap.put(allHashMetaMap, allSet);
            }
            allSet.add(objects);

            for (int i = 0; i < objects.length; i++) {

                //当前单元素构建
                Map<Integer, String> rowHashMetaMap = new HashMap<>();
                rowHashMetaMap.put(i, String.valueOf(objects[i]));

                Set<Object[]> objectSet = hashIndexMap.get(rowHashMetaMap);
                if (null == objectSet) {
                    objectSet = new LinkedHashSet<>();
                    hashIndexMap.put(rowHashMetaMap, objectSet);
                }

                objectSet.add(objects);

                //构建含有之前项的复核索引
                if (i > 0) {
                    Map<Integer, String> compositeHashMetaMap = new HashMap<>();
                    for (int j = i; j >= 1; j--) {
                        compositeHashMetaMap.put(j, String.valueOf(objects[j]));
                    }
                    Set<Object[]> compositeObjectSet = hashIndexMap.get(compositeHashMetaMap);
                    if (null == compositeObjectSet) {
                        compositeObjectSet = new LinkedHashSet<>();
                        hashIndexMap.put(compositeHashMetaMap, compositeObjectSet);
                    }

                    compositeObjectSet.add(objects);
                }

            }

        });

        System.out.println(" buildHashInde cost: " + (System.currentTimeMillis() - begin));

        this.hashIndexMap = hashIndexMap;

    }



}
