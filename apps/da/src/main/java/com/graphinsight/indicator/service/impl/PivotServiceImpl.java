package com.graphinsight.indicator.service.impl;

import com.graphinsight.indicator.constant.IndicatorConstant;
import com.graphinsight.indicator.enums.*;
import com.graphinsight.indicator.model.*;
import com.graphinsight.indicator.service.BuildSqlService;
import com.graphinsight.indicator.service.PivotService;
import com.graphinsight.indicator.util.CloneUtils;
import com.graphinsight.indicator.util.StringUtil;
import org.apache.calcite.jdbc.CalciteConnection;
import org.apache.calcite.schema.SchemaPlus;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.sql.*;
import java.text.Collator;
import java.text.DecimalFormat;
import java.util.*;

@Service
public class PivotServiceImpl implements PivotService {

    private final static String DIM_COLUMN_PREFIX = IndicatorConstant.DIM_COLUMN_PREFIX;

    private final static String MEASS_COLUMN_PREFIX = IndicatorConstant.MEASS_COLUMN_PREFIX;

    @Override
    public CellSet buildCellSet(BuildSqlTuple tuple, DataTuple cubeTuple, DataTuple orgTuple) {

        Set<BaseConfigure> columnAxisSet = tuple.getColumnAxisSet();
        Set<BaseConfigure> rowAxisSet = tuple.getRowAxisSet();

        List<Object> columnAxisList = this.stdcdu(tuple, columnAxisSet);
        List<Object> rowAxisList = this.stdcdu(tuple, rowAxisSet);

        CellSet cellSet = new CellSet();

        Axis columnAxis = new Axis();
        cellSet.setColumnAxis(columnAxis);

        Axis rowAxis = new Axis();
        cellSet.setRowAxis(rowAxis);

        this.buildAxisMember(columnAxis, columnAxisList, columnAxisSet, cubeTuple, orgTuple, tuple, AxisType.COLUMN);
        this.buildAxisMember(rowAxis, rowAxisList, rowAxisSet, cubeTuple, orgTuple, tuple, AxisType.ROW);

        return cellSet;

    }

    private void buildAxisMember(Axis axis, List<Object> axisList, Set<BaseConfigure> axisSet, DataTuple cubeTuple, DataTuple orgTuple, BuildSqlTuple tuple, AxisType axisType) {

        Integer firstDimIdx = this.getFirstDimPosition(axisList);
        List<CubeMember> dimMemberList = this.initCubeMember(axisType, axis, new LinkedList<>(), null, axisList, firstDimIdx, cubeTuple, orgTuple, tuple);
        List<CubeMember> measCubeMemberList = this.buildMeasureMemberList(axisList, tuple);

        //判断轴第一个元素是否以维度开头
        boolean isDimFirst = this.isFirstDim(axisSet);
        if (isDimFirst) {

            //维度最后一级元素
            List<CubeMember> deepestList = new LinkedList<CubeMember>();
            this.findAllDeepest(dimMemberList, deepestList);

            if (measCubeMemberList.size() > 0) {

                for (CubeMember deepestCubeMember : deepestList) {
                    List<CubeMember> measCubeMemberLinkedList = CloneUtils.clone((LinkedList) measCubeMemberList);
                    deepestCubeMember.getChildMemberList().addAll(measCubeMemberLinkedList);
                    for (CubeMember measCubeMember : measCubeMemberLinkedList) {
                        measCubeMember.setParentMember(deepestCubeMember);
                    }
                }

            }

            axis.setMemberList(dimMemberList);

        } else {
            //指标最后一级元素
            List<CubeMember> deepestList = new LinkedList<CubeMember>();
            this.findAllDeepest(measCubeMemberList, deepestList);

            if (dimMemberList.size() > 0) {

                for (CubeMember deepestCubeMember : deepestList) {
                    List<CubeMember> dimCubeMemberLinkedList = CloneUtils.clone((LinkedList) dimMemberList);
                    deepestCubeMember.getChildMemberList().addAll(dimCubeMemberLinkedList);
                    for (CubeMember dimCubeMember : dimCubeMemberLinkedList) {
                        dimCubeMember.setParentMember(deepestCubeMember);
                    }
                }

            }

            axis.setMemberList(measCubeMemberList);

        }
    }

    private List<CubeMember> findAllDeepest(List<CubeMember> memberList, List<CubeMember> deepestList) {

        for (CubeMember cubeMember : memberList) {

            Integer depth = cubeMember.getDepth();
            if (depth == 1) {
                deepestList.add(cubeMember);
            } else {
                this.findAllDeepest(cubeMember.getChildMemberList(), deepestList);
            }

        }

        return deepestList;

    }

    private String getDimName(Dimension dim, String columnNameId, String id, TempTable tempTable) {

        if (IndicatorConstant.ROLLUP_CUBE_ALL.equalsIgnoreCase(id)) {
            return id;
        }

        Map<Map<Integer, String>, Set<Object[]>> hashIndex = tempTable.getHashIndexMap();

        Map<Integer, String> dimFilterMap = new HashMap<>();
        List<ColumnTypeInfo> columnTypeInfoList = tempTable.getColumnTypeInfoList();
        List<Object[]> dataList = tempTable.getDataList();

        Integer idx = this.getColumnIdx(dim, columnTypeInfoList);

        String targetName = DIM_COLUMN_PREFIX + dim.getName();
        Integer targetIdx = this.getColumnIdx(targetName, columnTypeInfoList);
        dimFilterMap.put(idx, id);

        Set<Object[]> rowList = hashIndex.get(dimFilterMap);
        String dimName = "";
        if (!CollectionUtils.isEmpty(rowList)) {

            for (Object[] objects : rowList) {
                dimName = String.valueOf(objects[targetIdx]);
                break;
            }

        } else {
            for (Object[] objects : dataList) {
                boolean pass = this.filterRow(dimFilterMap, objects);
                if (pass) {
                    dimName = String.valueOf(objects[targetIdx]);
                    break;
                }
            }
        }

        return dimName;

    }

    private String getDimName(Dimension dim, String columnNameId, String id, Connection orgConn) {

        String dimName = "";
        String columnName = DIM_COLUMN_PREFIX + dim.getName();
        String findNameSql = "select " + columnName +" from TEMP.TEMP_TABLE where " + columnNameId + "='" + id + "' limit 1";

        List<Object[]> dataNameList = this.query(findNameSql, orgConn);

        for (Object[] objects : dataNameList) {
            dimName = String.valueOf(objects[0]);
        }

        return dimName;

    }

    private String buildFilter(CubeMember member) {

        String sql = "";
        Dimension dim = member.getDimension();
        String columnId = DIM_COLUMN_PREFIX + dim.getName() + "ID";
        String columnCode = member.getCode();

        sql += " and " + columnId + "='" + columnCode + "'";
        CubeMember parentMember = member.getParentMember();
        if (null != parentMember) {
            sql += this.buildFilter(parentMember);
        }

        return sql;

    }

    private String getDimOrder(String columnId) {

        String orderSql = "";
        if (null != columnId)  {
            orderSql += " order by " + columnId + " desc ";
        }

        return orderSql;

    }

    private String getDimWhere(CubeMember member) {

        String whereSql = "";
        if (null != member)  {

            String dimWhere = this.buildFilter(member);
            dimWhere = dimWhere.replaceFirst(" and ", " ");
            whereSql = " where " + dimWhere;

        }

        return whereSql;

    }

    private Integer getFirstDimPosition(List<Object> axisList) {
        Integer idx = 0;
        for (int i = 0; i < axisList.size(); i++) {
            Object column = axisList.get(i);
            if (column instanceof Dimension) {
                idx = i;
                break;
            }
        }
        return idx;
    }

    /**
     * 查找X轴或Y轴的下级唯一id列表
     * @param findDimTuple
     * @param dataTuple
     * @param dim
     * @param tuple
     * @return
     */
    private Set<String> find(AxisType axisType, FindDimensionTuple findDimTuple, DataTuple dataTuple, Dimension dim, BuildSqlTuple tuple, DataTuple orgTuple, String columnNameId) {

        LinkedList<String> dimIdList = new LinkedList<>();
        TempTable tempTable = dataTuple.getTempTable();

        List<ColumnTypeInfo> columnTypeInfoList = tempTable.getColumnTypeInfoList();
        List<Object[]> dataList = tempTable.getDataList();

        Dimension targetDim = findDimTuple.getTargetDimension();
        Set<CubeMember> whereParentDimSet = findDimTuple.getWhereParentDimSet();

        Integer dimIdx = this.getColumnIdx(targetDim, columnTypeInfoList);
        Map<Integer, String> dimFilterMap = this.findDimFilterMapByMember(whereParentDimSet, columnTypeInfoList);

        Set<Object[]> objectsList = tempTable.getHashIndexMap().get(dimFilterMap);

        BaseConfigure subtotalConfigure = this.hasSubtotal(tuple, dim.getCode());
        //维度被设置成全部 或 者筛选条件中有全部
        boolean hasSubtotal = (null != subtotalConfigure) || this.hasSubtotal(tuple, dimFilterMap);

        if (!CollectionUtils.isEmpty(objectsList)) {

            for (Object[] objects : objectsList) {
                String dimValue = String.valueOf(objects[dimIdx]);
                if (hasSubtotal) {
                    dimIdList.add(dimValue);
                } else {
                    if (IndicatorConstant.ROLLUP_CUBE_ALL.equalsIgnoreCase(dimValue)) {
//                      不含全部的维度跳过
                    } else {
                        dimIdList.add(dimValue);
                    }
                }

            }

        } else {

            for (Object[] objects : dataList) {

                boolean pass = this.filterRow(dimFilterMap, objects);
                if (pass) {
                    String dimValue = String.valueOf(objects[dimIdx]);
                    if (hasSubtotal) {
                        dimIdList.add(dimValue);
                    } else {
                        if (IndicatorConstant.ROLLUP_CUBE_ALL.equalsIgnoreCase(dimValue)) {
//                      不含全部的维度跳过
                        } else {
                            dimIdList.add(dimValue);
                        }
                    }
                }

            }

        }

        //因为维度有存在于column的情况，必须在此处提前进行处理维度的排序。
        List orderDimList = this.orderDimGroup(dim, tuple, dimIdList, orgTuple, columnNameId);

        return new LinkedHashSet<>(orderDimList);

    }

    private Order get(Dimension dim, BuildSqlTuple tuple) {

        Order order = null;
        QueryParam queryParam = tuple.getQueryParam();
        if (null != queryParam) {
            List<Order> orderList = queryParam.getOrderList();
            if (!CollectionUtils.isEmpty(orderList)) {

                for (Order paramOrder : orderList) {
                    String orderCode = paramOrder.getCode();
                    if (dim.getCode().equalsIgnoreCase(orderCode)) {
                        order = paramOrder;
                        break;
                    }

                }
            }
        }

        return order;

    }

    /**
     * 是否是数字判断
     * @param str
     * @return
     */
    public static boolean isNumeric(String str) {
        try {
            Double.valueOf(str);
        } catch (Exception ex) {
            return false;
        }
        return true;
    }

    /**
     * 判断是否含有有效的输入order，如果只存在一个null,则说明这个对象仅负责排序全部置顶。
     * 这样无需全排序，只需要单独操作即可。
     * @param orderMap
     * @return
     */
    private boolean hasOrder(Map<Integer, Order> orderMap) {

        boolean hasOrder = true;
        Integer[] idx = orderMap.keySet().toArray(new Integer[orderMap.size()]);
        if (idx.length == 1) {
            Order order = orderMap.get(0);
            hasOrder = (null != order);
        }

        if (idx.length == 0) {
            hasOrder = false;
        }

        return hasOrder;

    }

    /**
     * 只将全部置顶，其它排序忽略
     * @param obList
     */
    private void sortByOnlyTopAll(LinkedList<String[]> obList) {

        Optional<String[]> tempOpt = obList.stream().filter(ob -> IndicatorConstant.ROLLUP_CUBE_ALL.equalsIgnoreCase(ob[0])).findFirst();
        if (tempOpt.isPresent()) {

            String[] temp = tempOpt.get();
            obList.remove(temp);
            obList.addFirst(temp);

        }

        /*
        for (int i = 0; i < obList.size(); i++) {

            String[] robs = obList.get(i);
            String id = robs[0];
            if (IndicatorConstant.ROLLUP_CUBE_ALL.equalsIgnoreCase(id)) {
                String[] temp = obList.get(0);
                obList.set(0, robs);
                obList.set(i, robs);
                //一个分组里只可能有一个全部
                break;
            }

        }
         */

    }

    private void sortByColumn(LinkedList<String[]> obList, final Map<Integer, Order> orderMap) {

        //排序顺序
        Integer[] idx = orderMap.keySet().toArray(new Integer[orderMap.size()]);
        Long begin = System.currentTimeMillis();
        //判断是否含有有效的order操作集合，还是只有全部排到首位的诉求。
        boolean hasOrder = this.hasOrder(orderMap);
        if (!hasOrder) {
            //默认分组下全部最多只可能有一个，此方法用来将全部置顶。
            this.sortByOnlyTopAll(obList);
        } else {
            obList.sort(new Comparator<Object>() {

                public int compare(Object o1, Object o2) {

                    String[] one = (String[]) o1;
                    String[] two = (String[]) o2;

                    for (int i = 0; i < idx.length; i++) {

                        int k = idx[i];
                        Order order = orderMap.get(k);

                        //默认按倒序
                        SortType sortType = SortType.DESC;

                        //begin 初始化排序相关信息
                        LinkedList<String> topValueList = new LinkedList<>();
                        topValueList.add(IndicatorConstant.ROLLUP_CUBE_ALL);

                        if (null != order) {

                            sortType = order.getSortType();

                            //是否含有自定义排序值
                            List<String> valueList = order.getValueList();
                            if (!CollectionUtils.isEmpty(valueList)) {
                                topValueList.addAll(valueList);
                            }

                        }
                        //end 初始化排序相关信息

                        String oneIdStr = one[0];
                        String twoIdStr = two[0];

                        String oneStr = one[1];
                        String twoStr = two[1];

                        Integer topLen = topValueList.size();
                        Integer topOneIdx = getIdx(topValueList, oneIdStr);
                        Integer topTwoIdx = getIdx(topValueList, twoIdStr);

                        //命中top 值
                        if ((topOneIdx >= 0 && topOneIdx <= topLen) || (topTwoIdx >= 0 && topTwoIdx <= topLen)) {

                            Integer topOneInt = topLen - topOneIdx;
                            Integer topTwoInt = topLen - topTwoIdx;

                            return topOneInt > topTwoInt ? -1 : 1;

                        }

                        //无命中固定头值
                        if (isNumeric(oneStr) || isNumeric(twoStr)) {

                            Double oneDbl = Double.valueOf(one[k]);
                            Double twoDbl = Double.valueOf(two[k]);

                            if (oneStr.compareTo(twoStr) > 0) {
                                return SortType.DESC.equals(sortType) ? -1 : 1;
                            } else if (oneStr.compareTo(twoStr) < 0) {
                                return SortType.DESC.equals(sortType) ? 1 : -1;
                            } else {
                                continue;
                            }

                        } else {

                            if (Collator.getInstance(Locale.CHINESE).compare(oneStr, twoStr) > 0) {
                                return SortType.DESC.equals(sortType) ? -1 : 1;
                            } else if (Collator.getInstance(Locale.CHINESE).compare(oneStr, twoStr) < 0) {
                                return SortType.DESC.equals(sortType) ? 1 : -1;
                            } else if (Collator.getInstance(Locale.CHINESE).compare(oneStr, twoStr) == 0) {
                                continue;
                            }

                        }

                    }
                    return 0;
                }
            });

        }

        Long cost = System.currentTimeMillis() - begin;

        if (cost > 1000) {
            this.println(" sort cost : " + cost);
        }

    }

    private static Integer getIdx(LinkedList<String> linkedList, String name) {

        Integer idx = linkedList.size() + 1;
        for (int i = 0; i < linkedList.size(); i++) {
            if (name.equalsIgnoreCase(linkedList.get(i))) {
                idx = i;
                break;
            }
        }

        return idx;
    }

    /**
     * 维度组内排序
     * @param dim
     * @param tuple
     * @param dimIdList
     * @return
     */
    private List orderDimGroup(Dimension dim, BuildSqlTuple tuple, LinkedList<String> dimIdList, DataTuple orgTuple, String columnNameId) {

        final Integer idIdx = 0;
        final Integer nameIdx = 1;

        /*
        String[][] dimNames = new String[dimIdList.size()][2];
        for (int i = 0; i < dimNames.length; i++) {

            String id = dimIdList.get(i);
            dimNames[i][idIdx] = id;

            String dimName = this.getDimName(dim, columnNameId, id, orgTuple.getTempTable());
            dimNames[i][nameIdx] = dimName;

        }
        */

        LinkedList<String[]> dimNameList = new LinkedList<>();
        for (int i = 0; i < dimIdList.size(); i++) {

            String[] dimNames = new String[2];
            String id = dimIdList.get(i);
            dimNames[idIdx] = id;

            String dimName = this.getDimName(dim, columnNameId, id, orgTuple.getTempTable());
            dimNames[nameIdx] = dimName;

            dimNameList.add(dimNames);

        }

        final Order order = this.get(dim, tuple);

        //order map按名称排序.
        Map<Integer, Order> orderMap = new LinkedHashMap<>();
        orderMap.put(nameIdx, order);
        //排序
        this.sortByColumn(dimNameList, orderMap);

        LinkedList<String> tempDimIdList = new LinkedList<>();
        for (String[] dimValue : dimNameList) {
            tempDimIdList.add(dimValue[0]);
        }

        return tempDimIdList;

    }

    private List<CubeMember> initCubeMember(AxisType axisType, Axis axis, List<CubeMember> memberList, CubeMember parentMember, List<Object> axisList, Integer idx, DataTuple cubeTuple, DataTuple orgTuple, BuildSqlTuple tuple) {

        //记录轴最大深度
        if (axisList.size() <= idx) {
            return memberList;
        }

        Object column = axisList.get(idx);
        if (column instanceof Dimension) {

            FindDimensionTuple findDimTuple = new FindDimensionTuple();

            Dimension dim = (Dimension) column;
            findDimTuple.setTargetDimension(dim);
            this.findParent(parentMember, findDimTuple.getWhereParentDimSet());
            String columnNameId = DIM_COLUMN_PREFIX + dim.getName() + "ID";
            LinkedHashSet<String> dataIdSet = (LinkedHashSet)this.find(axisType, findDimTuple, cubeTuple, dim, tuple, orgTuple, columnNameId);

            LinkedList<String> datas = new LinkedList<>(dataIdSet);
            Boolean rowSum = tuple.getQueryParam().isRowSum();
            Boolean colSum = tuple.getQueryParam().isColSum();
            //默认行轴排到第一位，列轴排到最后一位。
            if (dataIdSet.contains(IndicatorConstant.BI_PIVOT_NULL)
                        && AxisType.COLUMN.equals(axisType)) {
                //全部是放到头还是放到尾
                datas.remove(IndicatorConstant.BI_PIVOT_NULL);
                datas.addFirst(IndicatorConstant.BI_PIVOT_NULL);
            }

            if ((!rowSum && AxisType.COLUMN.equals(axisType)) || (!colSum && AxisType.ROW.equals(axisType))) {
                datas.remove(IndicatorConstant.BI_PIVOT_NULL);
            }

//            String nullObj = null;
//            for (String data : datas) {
//                if (IndicatorConstant.BI_PIVOT_NULL.equalsIgnoreCase(data)) {
//                    nullObj = data;
//                    datas.remove(data);
//                    datas.addFirst(nullObj);
//                    break;
//                }
//            }

            for (String value : datas) {

                CubeMember cubeMember = new CubeMember();
                cubeMember.setMemberType(MemberType.DIMENSION);
                cubeMember.setDimension(dim);
                cubeMember.setCode(value);
                String dimName = this.getDimName(dim, columnNameId, value, orgTuple.getTempTable());

                if (StringUtil.isEmpty(dimName)) {
                    cubeMember.setValue(value);
                } else {
                    cubeMember.setValue(dimName);
                }

                if (null != parentMember) {
                    cubeMember.setParentMember(parentMember);
                }
                memberList.add(cubeMember);

                this.initCubeMember(axisType, axis, cubeMember.getChildMemberList(), cubeMember, axisList, idx + 1, cubeTuple, orgTuple, tuple);

            }

        }

        return memberList;

    }

    private Set<CubeMember> findParent(CubeMember member, Set<CubeMember> allWhereParentDimSet) {

        if (null != member)  {

            allWhereParentDimSet.add(member);
            CubeMember parentMember = member.getParentMember();

            this.findParent(parentMember, allWhereParentDimSet);

        }

        return allWhereParentDimSet;
    }

    /**
     * 获取指标原始数据
     * @param axisList
     * @return
     */
    private Set<CubeMember> getTempMeasCubeMemberSet(List<Object> axisList, BuildSqlTuple tuple) {
        //默认未添加分组的度量分组
        //此处必须保证指标分组必须在指标之上,后端会通过本类stdcdu() 方法二次排序一次，以此保证顺序。
        CubeMember totalMeasureGroupMember = new CubeMember();
        //指标度量
        totalMeasureGroupMember.setCode("measgroup_meass");
        totalMeasureGroupMember.setValue(IndicatorConstant.BI_NULL);
        totalMeasureGroupMember.setMemberType(MemberType.MEASURE_GROUP);

        Set<CubeMember> tempMeasCubeMemberSet = new LinkedHashSet<>();
        boolean hasMeasGroup = false;
        //获取输入的数据结构
        for (Object column : axisList) {
            if (column instanceof BaseConfigure) {
                BaseConfigure columnInfo = (BaseConfigure) column;
                if (this.isMeasureGroup(columnInfo)) {
                    CubeMember axisMeasure = this.buildMeasGroupMember(columnInfo, null, tuple);
                    tempMeasCubeMemberSet.add(axisMeasure);
                    hasMeasGroup = true;
                }
            } else if (column instanceof Measure) {

                Measure measure = (Measure) column;
                //是指标则直接添加到指标分组中
                CubeMember measCubeMember = new CubeMember();
                measCubeMember.setCode(measure.getCode());
                measCubeMember.setValue(this.getMeasureName(measure));
                measCubeMember.setMemberType(MemberType.MEASURE);
                measCubeMember.setMeasure(measure);
                //同环比比率
                measCubeMember.setRatioType(measure.getRatioType());
                //同环比内容
                measCubeMember.setRatioValueType(measure.getRatioValueType());

                if (hasMeasGroup) {
                    //如果存在分组情况，将指标划归默认的度量分组
                    measCubeMember.setParentMember(totalMeasureGroupMember);
                    totalMeasureGroupMember.getChildMemberList().add(measCubeMember);

                    if (!tempMeasCubeMemberSet.contains(totalMeasureGroupMember)) {
                        tempMeasCubeMemberSet.add(totalMeasureGroupMember);
                    }

                } else {
                    tempMeasCubeMemberSet.add(measCubeMember);
                }
            }
        }

        return tempMeasCubeMemberSet;

    }

    private String getMeasureName(Measure measure) {

        RatioType ratioType = measure.getRatioType();
        RatioValueType ratioValueType = measure.getRatioValueType();
        String name = measure.getName();
        String typeAlias = "";
        String valueTypeAlias = "";

        if (null != ratioType) {
            typeAlias = "-" + (RatioType.MONTHONMONTH.equals(ratioType) ? "同比" : "环比");
        }

        if (null != ratioValueType) {
            valueTypeAlias = "" + (RatioValueType.VALUE.equals(ratioValueType) ? "" : "");
        }

        return name + typeAlias + valueTypeAlias;

    }

    /**
     * 构建指标分组、指标矩阵
     * @param axisList
     * @return
     */
    public List<CubeMember> buildMeasureMemberList(List<Object> axisList, BuildSqlTuple tuple) {

        //最大深
        Integer max = this.deepMax(axisList);

        //页面输入的原始指标分组、指标情况
        Set<CubeMember> tempMeasCubeMemberSet = this.getTempMeasCubeMemberSet(axisList, tuple);

        //对指标分组进行等高处理
        List<CubeMember> measCubeMemberList = this.alignmentDepth(tempMeasCubeMemberSet, max);

        return measCubeMemberList;

    }

    /**
     * 拉齐指标分组的高度
     * @param tempList
     * @param max
     * @return
     */
    private List<CubeMember> alignmentDepth(Set<CubeMember> tempList, Integer max) {

        List<CubeMember> alignmentDepth = new LinkedList<>();
        for (CubeMember cubeMember : tempList) {

            Integer depth = cubeMember.getDepth();
            if (depth < max) {
                //补齐
                CubeMember parentRoot = null;
                for (int i = max; i > depth; i--) {

                    CubeMember nullCubeMember = new CubeMember();

                    nullCubeMember.setCode(" ");
                    nullCubeMember.setValue(IndicatorConstant.BI_NULL);
                    nullCubeMember.setDepth(i);
                    nullCubeMember.setMemberType(MemberType.MEASURE_GROUP);

                    if (null != parentRoot) {
                        nullCubeMember.setParentMember(parentRoot);
                        parentRoot.getChildMemberList().add(nullCubeMember);

                    } else {
                        //第一次进入时，需要把根对象放入集合中。
                        alignmentDepth.add(nullCubeMember);
                    }

                    parentRoot = nullCubeMember;

                }

                parentRoot.getChildMemberList().add(cubeMember);
                cubeMember.setParentMember(parentRoot);


            } else {
                alignmentDepth.add(cubeMember);
            }

        }

        return alignmentDepth;

    }

    private CubeMember buildMeasGroupMember(BaseConfigure measGroup, CubeMember parentMember, BuildSqlTuple tuple) {

        CubeMember cubeGroupMember = new CubeMember();
        if (null != parentMember) {
            cubeGroupMember.setParentMember(parentMember);
            parentMember.getChildMemberList().add(cubeGroupMember);
        }

        cubeGroupMember.setCode(measGroup.getCode());
        cubeGroupMember.setValue(measGroup.getName());
        cubeGroupMember.setMemberType(MemberType.MEASURE_GROUP);

        List<BaseConfigure> measureSet = measGroup.getMeasGroupSet();

        if (!CollectionUtils.isEmpty(measureSet)) {

            for (BaseConfigure measureInfo : measureSet) {

                if (this.isMeasure(measureInfo)) {

                    String measCode = measureInfo.getCode();
                    Measure measure = tuple.findMeasure(measCode);

                    //是指标则直接添加到指标分组中
                    CubeMember measCubeMember = new CubeMember();
                    measCubeMember.setCode(measCode);
                    measCubeMember.setValue(measure.getName());
                    measCubeMember.setMemberType(MemberType.MEASURE);
                    measCubeMember.setMeasure(measure);

                    //将指标划归指标分组
                    measCubeMember.setParentMember(cubeGroupMember);
                    cubeGroupMember.getChildMemberList().add(measCubeMember);

                } else {
                    //如果是指标分组
                    this.buildMeasGroupMember(measureInfo, cubeGroupMember, tuple);
                }
            }
        }

        return cubeGroupMember;

    }

    private Integer deepMax(List<Object> axisList) {

        Integer max = 0;
        for (Object column : axisList) {
            if (column instanceof BaseConfigure) {
                BaseConfigure measGroup = (BaseConfigure) column;
                max = this.deepMax(measGroup, 1);
            }
        }

        return max;

    }

    private Integer deepMax(BaseConfigure measGroup, Integer max) {

        List<BaseConfigure> childMeasGroupList = measGroup.getMeasGroupSet();

        if (!CollectionUtils.isEmpty(childMeasGroupList)) {
            List<Integer> childDeepList = new ArrayList<>();
            for (BaseConfigure baseConfigure : childMeasGroupList) {
                Integer childDeep = this.deepMax(baseConfigure, max + 1);
                childDeepList.add(childDeep);
            }
            max = this.getMax(childDeepList);
        }

        return max;

    }

    private Integer getMax(List<Integer> childDeepMap) {

        Integer max = childDeepMap.get(0);
        for (int i = 1; i < childDeepMap.size(); i++) {
            Integer value = childDeepMap.size();
            if (max < value) {
                max = value;
            }
        }

        return max;

    }
    
    private void println(String v) {
        System.out.println(v);
    }

    /**
     * 根据筛选条件里的内容看是否含有全部
     * @param tuple
     * @param dimFilterMap
     * @return
     */
    private boolean hasSubtotal(BuildSqlTuple tuple, Map<Integer, String> dimFilterMap) {

        boolean hasSubtotal = false;
        Set<Integer> entrySet = dimFilterMap.keySet();
        for (Integer key : entrySet) {

            String value = dimFilterMap.get(key);
            if (IndicatorConstant.ROLLUP_CUBE_ALL.equalsIgnoreCase(value)) {
                hasSubtotal = true;
                break;
            }

        }

        return hasSubtotal;

    }

    /**
     * 判断指定的维度code是否设置了显示分组小计
     * @param tuple
     * @param dimCode
     * @return
     */
    private BaseConfigure hasSubtotal(BuildSqlTuple tuple, String dimCode) {

        //参数集合
        List<BaseConfigure> allConfigureList = new ArrayList<>();
        Set<BaseConfigure> columnAxisSet = tuple.getColumnAxisSet();
        Set<BaseConfigure> rowAxisSet = tuple.getRowAxisSet();

        if (!CollectionUtils.isEmpty(columnAxisSet)) {
            allConfigureList.addAll(columnAxisSet);
        }

        if (!CollectionUtils.isEmpty(rowAxisSet)) {
            allConfigureList.addAll(rowAxisSet);
        }

        BaseConfigure hasSubtotal = null;

        for (BaseConfigure baseConfigure : allConfigureList) {

            if (null != baseConfigure.getHasSubtotal() && baseConfigure.getHasSubtotal() && baseConfigure.getCode().equalsIgnoreCase(dimCode)) {
                hasSubtotal = baseConfigure;
                break;
            }

        }

        return hasSubtotal;
    }

    /**
     * 判断是否含有分组小计
     * @param tuple
     * @return
     */
    public boolean hasSubtotal(BuildSqlTuple tuple) {

        //参数集合
        List<BaseConfigure> allConfigureList = new ArrayList<>();
        Set<BaseConfigure> columnAxisSet = tuple.getColumnAxisSet();
        Set<BaseConfigure> rowAxisSet = tuple.getRowAxisSet();
        if (!CollectionUtils.isEmpty(columnAxisSet)) {
            allConfigureList.addAll(columnAxisSet);
        }

        if (!CollectionUtils.isEmpty(rowAxisSet)) {
            allConfigureList.addAll(rowAxisSet);
        }

        boolean hasSubtotal = false;

        for (BaseConfigure baseConfigure : allConfigureList) {

            if (null != baseConfigure.getHasSubtotal() && baseConfigure.getHasSubtotal()) {
                hasSubtotal = true;
                break;
            }

        }

        return hasSubtotal;
    }

    private boolean isNull(QueryResult result) {
        List<Map<String, Object>> orgDataList = result.getValueMap();
        return CollectionUtils.isEmpty(orgDataList);
    }
 
    @Override
    public Matrix buildMatrix(BuildSqlTuple tuple, QueryResult result) {

        if (this.isNull(result)) {
            return Matrix.buildNull();
        }

        Long begin = System.currentTimeMillis();
        TempTable tempTable = buildResultTable(tuple, result);
        tempTable.buildHashIndex();
        this.println("build templTable : " + (System.currentTimeMillis() - begin));

        TempTable rollupTable = null;
        TempTable cubeTable = null;

        Connection orgConn = this.buildConnection(tempTable);
        Connection rollupConn = null;
        Connection cubeConn = null;

        //是否含有分組小计
        boolean hasSubtotal = this.hasSubtotal(tuple);

        if (hasSubtotal) {

            rollupTable = this.buildRollupOrCubeTable(tuple, tempTable, "rollup");
            rollupTable.buildHashIndex();
            this.println("build rollupTable : " + (System.currentTimeMillis() - begin));

            cubeTable = this.buildRollupOrCubeTable(tuple, tempTable, "cube");
            cubeTable.buildHashIndex();
            this.println("build cubeTable : " + (System.currentTimeMillis() - begin));

            rollupConn = this.buildConnection(rollupTable);
            cubeConn = this.buildConnection(cubeTable);

        } else {

//            rollupTable = this.buildRollupOrCubeTable(tuple, tempTable, "");
            rollupTable = this.buildRollupByTempTable(tuple, tempTable, "");
            rollupTable.buildHashIndex();

            this.println("build A : " + (System.currentTimeMillis() - begin));

            cubeTable = rollupTable;

            rollupConn = this.buildConnection(rollupTable);
            cubeConn = rollupConn;

            this.println("build B : " + (System.currentTimeMillis() - begin));

        }

        this.println("Connection C : " + (System.currentTimeMillis() - begin));

        DataTuple tempDataTuple = new DataTuple(tempTable, orgConn);
        this.println("Connection tempDataTuple : " + (System.currentTimeMillis() - begin));

        DataTuple rollupDataTuple = new DataTuple(rollupTable, rollupConn);
        this.println("Connection rollupDataTuple : " + (System.currentTimeMillis() - begin));

        DataTuple cubeDataTuple = new DataTuple(cubeTable, cubeConn);
        this.println("Connection cubeDataTuple : " + (System.currentTimeMillis() - begin));

        CellSet cellSet = this.buildCellSet(tuple, rollupDataTuple, tempDataTuple);
        this.println("Connection cellSet : " + (System.currentTimeMillis() - begin));

        Matrix matrix = this.buildMatrix(cellSet, cubeDataTuple, tempDataTuple, tuple);
        this.println("Connection matrix : " + (System.currentTimeMillis() - begin));

        //排序
        Matrix orderMatrix = this.buildOrder(matrix, tuple);
        this.println("Connection matrix : " + (System.currentTimeMillis() - begin));
        //设置colspan
        this.resetCoorColSpan(orderMatrix);
        this.println("Connection CC : " + (System.currentTimeMillis() - begin));
        //格式化输出值内容
        this.formatValue(orderMatrix, tuple);
        this.println("Connection DD : " + (System.currentTimeMillis() - begin));
//        this.print(orderMatrix);

        return orderMatrix;
    }

    /**
     * 格式化输出值内容 1.小计别名
     * @param matrix
     * @param tuple
     */
    private void formatValue(Matrix matrix, BuildSqlTuple tuple) {
        //替换
        this.replace(matrix, tuple);
    }

    private void replace(Matrix matrix, BuildSqlTuple tuple) {

        //begin 排序矩阵构建
        Integer width = matrix.getWidth();
        Integer height = matrix.getHeight();
        for (Integer i = 0; i < height; i++) {
            for (Integer j = 0; j < width; j++) {

                Matrix.Cell cell = matrix.get(i, j);
                this.replaceSubtotalAlias(cell, tuple);
                this.replaceMeasure(cell, tuple);

            }
        }

    }

    /**
     * 增加指标百分比字符 %
     * @param cell
     * @param tuple
     */
    private void replaceMeasure(Matrix.Cell cell, BuildSqlTuple tuple) {

        CubeMember cubeMember = cell.getCubeMember();
        if (null != cubeMember) {

            Measure measure = cubeMember.getMeasure();
            if (null != measure) {

                ValueFormat valueFormat = measure.getValueFormat();

                if (null != valueFormat) {
                    FormatType formatType = valueFormat.getFormatType();

                    if (FormatType.PERCENT.equals(formatType) || FormatType.PERCENT1.equals(formatType) || FormatType.PERCENT2.equals(formatType)) {
                        MemberType memberType = cell.getMemberType();
                        if (MemberType.MEASURE_VALUE.equals(memberType)) {
                            cell.setValue(cell.getValue() + "%");
                        }

                    }

                }
            }
        }
    }

    /**
     * 替换小计别名
     * @param cell
     * @param tuple
     */
    private void replaceSubtotalAlias(Matrix.Cell cell, BuildSqlTuple tuple) {

        CubeMember cubeMember = cell.getCubeMember();
        if (null != cubeMember) {

            Dimension dimension = cubeMember.getDimension();
            if (null != dimension) {
                BaseConfigure baseConfigure = this.hasSubtotal(tuple, dimension.getCode());
                if (null != baseConfigure && IndicatorConstant.ROLLUP_CUBE_ALL.equalsIgnoreCase(cubeMember.getCode())) {
                    String alias = baseConfigure.getSubtotalAlias();
                    if (!StringUtil.isEmpty(alias)) {
                        cell.setValue(alias);
                    }
                }
            }
        }

    }

    /**
     * 重新设置colspan
     * @param matrix
     */
    public void resetCoorColSpan(Matrix matrix) {

        Integer width = matrix.getWidth();
        Integer height = matrix.getHeight();

        CellSet cellSet = matrix.getCellSet();

        Axis rowAxis = cellSet.getRowAxis();
        Axis columnAxis = cellSet.getColumnAxis();

        //切割child矩阵
        Integer rowMaxDeep = rowAxis.getMaxDeep();
        Integer columnMaxDeep = columnAxis.getMaxDeep();

        Integer childHeight = height - columnMaxDeep;
        Integer childWidth = rowMaxDeep;

        Integer correctHeight = matrix.getCorrectHeight();
        Integer correctWidth = matrix.getCorrectWidth();

        //准备左下child矩阵
        Matrix.Cell[][] leftDownCells = new Matrix.Cell[childHeight][childWidth];
        for (int i = correctHeight; i < childHeight; i++) {
            for (int j = correctWidth; j < childWidth; j++) {

                Matrix.Cell cell = matrix.get(i + columnMaxDeep, j);
                cell.setRowspan(0);
                cell.setColspan(0);
                leftDownCells[i][j] = cell;

            }
        }

        //矩阵处理colspan
        for (int i = correctHeight; i < childHeight; i++) {
            for (int j = correctWidth; j < childWidth; j++) {

                //当前单元格
                Matrix.Cell cell = leftDownCells[i][j];
                //上一行同列单元格
                Matrix.Cell prevCell = this.getPrefCell(i, j, leftDownCells);

                if (null == prevCell) {
                    //第一行
                    cell.setColspan(1);
                    cell.setRowspan(1);

                    /**
                     * 左列设置
                     */
                    if (j > 0) {
                        Matrix.Cell leftCell = leftDownCells[i][j - 1];
                        cell.setLeftCell(leftCell);
                    }

                } else {

                    //当前元素
                    CubeMember ctMember = cell.getCubeMember();
                    //上一元素
                    CubeMember prevMember = prevCell.getCubeMember();

                    CubeMember ctParentMember = ctMember.getParentMember();
                    CubeMember prevParentMember = prevMember.getParentMember();

                    if (this.isEquals(ctMember, prevMember)) {
                        //与上一行是否是同一元素进行判断，当前值一致，同时父对象也一致。设置上级rolspan + 1
//                        Integer prevRow = i - 1;//上一行的行号
//                        this.setCellRolSpan(prevCell, prevRow, j, leftDownCells);

                        /**
                         * debug
                         * */

                        Matrix.Cell orgPrevCell = prevCell.getPrevCell();

                        if (null != orgPrevCell) {
                            cell.setPrevCell(orgPrevCell);
                        } else {
                            cell.setPrevCell(prevCell);
                        }

                        if (j > 0) {

                            Matrix.Cell leftCell = prevCell.getLeftCell();
                            if (null != leftCell) {
                                cell.setLeftCell(leftCell);
                            }

                        }
                        this.setCellRolDirectlySpan(cell);
                        //end

                    } else if (null != ctParentMember && this.isEquals(ctParentMember, prevParentMember)) {
                        //当前不一致，但父对象一致
                        cell.setColspan(1);
                        cell.setRowspan(1);
                    } else {
                        //当前不一致，父对象也不一致
                        //新cell
                        cell.setColspan(1);
                        cell.setRowspan(1);
                    }
                }
            }
        }

        //矩阵回置,把计算好的数据再放回去
        for (int i = correctHeight; i < childHeight; i++) {
            for (int j = correctWidth; j < childWidth; j++) {
                Matrix.Cell cell = leftDownCells[i][j];
                matrix.set(i + columnMaxDeep, j, cell);
            }
        }
        //end 左下矩阵完成
    }

    /**
     * 通过Cell直接定位父对象链路直接分析
     * @param cell
     */
    private void setCellRolDirectlySpan(Matrix.Cell cell) {

        Matrix.Cell prevCell = cell.getPrevCell();

        if (null != prevCell) {

            Integer rowSpan = prevCell.getRowspan();
            prevCell.setRowspan(rowSpan + 1);

        }

        Matrix.Cell leftCell = cell.getLeftCell();
//        this.setLeftCellSpan(leftCell);

    }

    private void setLeftCellSpan(Matrix.Cell cell) {
        if (null != cell) {
            Matrix.Cell leftCell = cell.getLeftCell();
            if (null != leftCell) {

                Integer rowSpan = leftCell.getRowspan();
                leftCell.setRowspan(rowSpan + 1);

                this.setLeftCellSpan(leftCell);
            }
        }
    }

    private void setCellRolSpan(Matrix.Cell cell, Integer ctRow, Integer column, Matrix.Cell[][] leftDownCells) {

        CubeMember ctCubeMember = cell.getCubeMember();

        Integer prevRow = ctRow - 1;

        if (prevRow >= 0) {
            //小于0则意味着超出顶行
            Matrix.Cell prevCell = leftDownCells[prevRow][column];
            CubeMember prevCubeMember = prevCell.getCubeMember();

            if (isEquals(ctCubeMember, prevCubeMember)) {
                this.setCellRolSpan(prevCell, prevRow, column, leftDownCells);
            } else {
                Integer rowSpan = cell.getRowspan();
                cell.setRowspan(rowSpan + 1);
            }

        } else {
            Integer rowSpan = cell.getRowspan();
            cell.setRowspan(rowSpan + 1);
        }

    }

    /**
     * 判断两个对象是否一致，包括父级
     * @param ctMember
     * @param prevMember
     * @return
     */
    private boolean isEquals(CubeMember ctMember, CubeMember prevMember) {

        boolean isPass = false;
        CubeMember ctParentMember = ctMember.getParentMember();
        CubeMember prevParentMember = prevMember.getParentMember();

        if (null == ctParentMember && ctMember.getCode().equalsIgnoreCase(prevMember.getCode())) {
            isPass = true;
        } else if (null != ctParentMember && ctMember.getCode().equalsIgnoreCase(prevMember.getCode())) {
            isPass = this.isEquals(ctParentMember, prevParentMember);
        }

        return isPass;

    }

    private Matrix.Cell getPrefCell(int x, int y, Matrix.Cell[][] leftDownCells) {

        Matrix.Cell cell = null;
        if (x > 0) {
            cell = leftDownCells[x - 1][y];
        }

        return cell;

    }

    /**
     * @param orderList
     * @param sortScope
     * @return
     */
    private List<Order> findScopeOrder(List<Order> orderList, SortScope sortScope) {

//        return orderList.stream().filter(order -> sortScope.equals(order.getSortScope())).collect(Collectors.toList());
        List<Order> tagetOrderList = new LinkedList<>();
        if (null != sortScope && !CollectionUtils.isEmpty(orderList)) {
            for (Order order : orderList) {

                SortScope scope = order.getSortScope();
                if (sortScope.equals(scope)) {
                    tagetOrderList.add(order);
                }
            }
        }

        return tagetOrderList;

    }

    private boolean hasMeas(BuildSqlTuple tuple) {
        boolean hasMeas = false;
        Set<BaseConfigure> rowAxisSet = tuple.getRowAxisSet();
        if (!CollectionUtils.isEmpty(rowAxisSet)) {
            for (BaseConfigure baseConfigure : rowAxisSet) {
                if (this.isMeasure(baseConfigure)) {
                    hasMeas = true;
                    break;
                }

            }
        }
        return hasMeas;
    }

    public Matrix buildOrder(Matrix matrix, BuildSqlTuple tuple) {

        if (true) {
            return matrix;
        }

        Integer deviation = matrix.getColumnMaxDeep();

        int correctHeight = matrix.getCorrectHeight();
        int correctWidth = matrix.getCorrectWidth();

        boolean rowHasMeas = this.hasMeas(tuple);

        //begin 排序矩阵构建
        Integer width = matrix.getWidth();
        Integer height = matrix.getHeight() - deviation;
        Matrix.Cell[][] cells = new Matrix.Cell[height - correctHeight][width - correctWidth];
        for (int i = correctHeight; i < height; i++) {
            for (int j = correctWidth; j < width; j++) {

                int x = i + deviation;
                int y = j;

                cells[i - correctHeight][j - correctWidth] = matrix.get(x, y);

            }
        }
        //end 排序矩阵构建

        //begin 排序
        QueryParam queryParam = tuple.getQueryParam();

        Matrix.Cell[] rowCells = cells[0];

        //获取所有排序信息
        if (null != queryParam) {

            List<Order> allOrderList = queryParam.getOrderList();
            // allScope原则上只允许有一个，但此处按多个实现。
            // 排序参数集合,先放入map的对象优先排序,此顺序需要保持。
            Map<Integer, Order> orderAllMap = new LinkedHashMap<>();
            List<Order> allScopeOrderList = this.findScopeOrder(allOrderList, SortScope.ALL);
            this.suppOrderMap(allScopeOrderList, rowCells, orderAllMap);
            //全排序
            if (!rowHasMeas || true) {
                cells = this.sortAll(orderAllMap, cells);
                //其它原则上应都为组内排序
                Map<Integer, Order> orderGroupMap = new LinkedHashMap<>();
                List<Order> groupScopeOrderList = this.findScopeOrder(allOrderList, SortScope.GROUP);
                this.suppOrderMap(groupScopeOrderList, rowCells, orderGroupMap);
                //组内排序
                cells = this.sortGroup(orderGroupMap, cells, matrix.getRowMaxDeep());
            }

        }
        //end 排序

        //排序后的数据矩阵更新到总矩阵中;
        for (int i = 0; i < height - correctHeight; i++) {
            for (int j = 0; j < width - correctWidth; j++) {

                int x = i + deviation + correctHeight;
                int y = j + correctWidth;

                matrix.set(x, y, cells[i][j]);

            }
        }

        return matrix;

    }

    private Matrix.Cell[][] sortAll(Map<Integer, Order> orderMap, Matrix.Cell[][] cells) {

        //所有需要排序的数据,转换为list结构
        LinkedList<Matrix.Cell[]> allList = new LinkedList<>();

        for (int i = 0; i < cells.length; i++) {
            allList.add(cells[i]);
        }

        this.sortAll(allList, cells, orderMap);

        return cells;

    }

    /**
     * 寻找指标分组排序时的维度列，默认按第二列，但当有指定分组的列后，按最后出现的列。
     * @param orderMap
     * @return
     */
    private Integer getDimLastGroupIdx(Map<Integer, Order> orderMap) {

        //指标分组默认从第二列开始，保留第一列分组。
        Integer dimIdx = 1;
        Set<Map.Entry<Integer, Order>> entrySet = orderMap.entrySet();
        for (Map.Entry<Integer, Order> integerOrderEntry : entrySet) {

            Integer idx = integerOrderEntry.getKey();
            Order order = integerOrderEntry.getValue();
            if (this.isDimension(order.getCode()) && idx > dimIdx) {
                dimIdx = idx;
            }

        }

        return dimIdx;

    }

    /**
     * 组内排序实现
     * @param orderMap
     * @param cells
     * @param rowMaxDeep
     * @return
     */
    private Matrix.Cell[][] sortGroup(Map<Integer, Order> orderMap, Matrix.Cell[][] cells, Integer rowMaxDeep) {

        //需要排序的字段
        Integer[] orderIdx = orderMap.keySet().toArray(new Integer[orderMap.keySet().size()]);

        // k 需要组内排序的总个数
        for (int k = 0; k < orderIdx.length; k++) {

            //需要排序的列号
            int j = orderIdx[k];
            Order order = orderMap.get(j);
            boolean isDimOrder = this.isDimension(order.getCode());

            //开始截取排序数据段
            LinkedList<Matrix.Cell[]> childGroupList = new LinkedList<>();

            //以上一行父对象相同为判别是否是同一组的标准，如果跟上一行父对象不一致，则截断。
            CubeMember parentMember = null;
            //获取child集合维度
            Integer dimColumn = j;
            //非维度排序,指标排序。
            if (!isDimOrder) {
                //指标并且列深大于1，则以第二列划取child组内数据集合
                if (rowMaxDeep > 1) {
                    //或者取排序字段中组内最大的元素
                    dimColumn = this.getDimLastGroupIdx(orderMap);
                } else {
                    //组内指标只有一个元素,无需排序,直接忽略。
                    continue;
                }

            }

            for (int i = 0; i < cells.length; i++) {

                //当前单元格
                Matrix.Cell rowCell = cells[i][dimColumn];
                CubeMember cubeMember = rowCell.getCubeMember();
                CubeMember currentParentMember = cubeMember.getParentMember();

                if (i == 0 && null == parentMember && null == currentParentMember) {
                    childGroupList = new LinkedList<>();
                } else if (null == parentMember && null != currentParentMember) {
                    this.sortChildGroup(childGroupList, cells, i, j, dimColumn, order);
                    childGroupList = new LinkedList<>();
                } else if (null != parentMember && (parentMember != currentParentMember || !parentMember.equals(currentParentMember))) {
                    this.sortChildGroup(childGroupList, cells, i, j, dimColumn, order);
                    childGroupList = new LinkedList<>();
                }

                childGroupList.add(cells[i]);
                parentMember = currentParentMember;

            }

            this.sortChildGroup(childGroupList, cells, cells.length, j, dimColumn, order);

        }

        return cells;

    }

    /**
     * 组内排序
     * @param childGroupList
     * @param cells
     * @param row 当前行
     * @param column
     * @param dimColumn
     * @param order
     */
    private void sortChildGroup(LinkedList<Matrix.Cell[]> childGroupList, Matrix.Cell[][] cells, final int row, final int column, final int dimColumn, final Order order) {

        //为null
        if (CollectionUtils.isEmpty(childGroupList) || null == order || childGroupList.size() < 2) {
            return;
        }

        childGroupList.sort(new Comparator<Matrix.Cell[]>() {

            public int compare(Matrix.Cell[] rowCell1, Matrix.Cell[] rowCell2) {

                Matrix.Cell cell1 = rowCell1[dimColumn];
                Matrix.Cell cell2 = rowCell2[dimColumn];

                //begin 初始化排序相关信息
                LinkedList<String> topValueList = new LinkedList<>();
                //全部默认放到头上.
                topValueList.add(IndicatorConstant.ROLLUP_CUBE_ALL);

                SortType sortType = order.getSortType();

                //是否含有自定义排序值
                List<String> valueList = order.getValueList();
                if (!CollectionUtils.isEmpty(valueList)) {
                    topValueList.addAll(valueList);
                }

                //end 初始化排序相关信息

                CubeMember cubeMember1 = cell1.getCubeMember();
                CubeMember cubeMember2 = cell2.getCubeMember();

                String oneStr = cubeMember1.getCode();
                String twoStr = cubeMember2.getCode();

                Integer topLen = topValueList.size();
                Integer topOneIdx = getIdx(topValueList, oneStr);
                Integer topTwoIdx = getIdx(topValueList, twoStr);

                //命中自定义top值
                if ((topOneIdx >= 0 && topOneIdx <= topLen) || (topTwoIdx >= 0 && topTwoIdx <= topLen)) {

                    Integer topOneInt = topLen - topOneIdx;
                    Integer topTwoInt = topLen - topTwoIdx;

                    return topOneInt > topTwoInt ? -1 : 1;

                }

                //是否是维度
                boolean isDim = isDimension(order.getCode());

                if (isDim) {

                    oneStr = cubeMember1.getValue();
                    twoStr = cubeMember2.getValue();

                    if (Collator.getInstance(Locale.CHINESE).compare(oneStr, twoStr) > 0) {
                        return SortType.DESC.equals(sortType) ? -1 : 1;
                    } else if (Collator.getInstance(Locale.CHINESE).compare(oneStr, twoStr) < 0) {
                        return SortType.DESC.equals(sortType) ? 1 : -1;
                    } else {
                        return 0;
                    }

                } else {
                    //指标排序
                    cell1 = rowCell1[column];
                    cell2 = rowCell2[column];

                    if (StringUtil.isEmpty(cell1.getValue()) || StringUtil.isEmpty(cell2.getValue())) {
                        return 0;
                    }

                    Double oneDtl = Double.valueOf(cell1.getValue());
                    Double twoDtl = Double.valueOf(cell2.getValue());

                    if (oneDtl.compareTo(twoDtl) > 0) {
                        return SortType.DESC.equals(sortType) ? 1 : -1;
                    } else if (oneDtl.compareTo(twoDtl) < 0) {
                        return SortType.DESC.equals(sortType) ? -1 : 1;
                    } else {
                        return 0;
                    }

                }
            }

        });

        //排序内容替换
        Integer childLen = childGroupList.size();
        Integer beginRow = row - childLen;
        for (int i = beginRow; i < row; i++) {

            Integer childIdx = i - beginRow;
            Matrix.Cell[] orderCell = childGroupList.get(childIdx);

            cells[i] = orderCell;

        }

    }

    /**
     * 所有排序
     * @param childGroupList
     * @param cells
     */
    private void sortAll(LinkedList<Matrix.Cell[]> childGroupList, Matrix.Cell[][] cells, final Map<Integer, Order> orderAllMap) {

        //为null
        childGroupList.sort(new Comparator<Matrix.Cell[]>() {

            public int compare(Matrix.Cell[] rowCell1, Matrix.Cell[] rowCell2) {

                //需要排序的字段,大排序原则上只有一个，但此处为了兼容，以多个方式处理。
                Integer[] orderIdx = orderAllMap.keySet().toArray(new Integer[orderAllMap.keySet().size()]);

                // k 需要组内排序的总个数
                for (int k = 0; k < orderIdx.length; k++) {

                    //需要排序的列号
                    int column = orderIdx[k];
                    Order order = orderAllMap.get(column);

                    Matrix.Cell cell1 = rowCell1[column];
                    Matrix.Cell cell2 = rowCell2[column];

                    //默认按倒序
                    SortType sortType = SortType.DESC;
                    //begin 初始化排序相关信息
                    LinkedList<String> topValueList = new LinkedList<>();
                    //全部默认放到头上.
                    topValueList.add(IndicatorConstant.ROLLUP_CUBE_ALL);

                    if (null != order) {

                        sortType = order.getSortType();

                        //是否含有自定义排序值
                        List<String> valueList = order.getValueList();
                        if (!CollectionUtils.isEmpty(valueList)) {
                            topValueList.addAll(valueList);
                        }

                    }
                    //end 初始化排序相关信息

                    CubeMember cubeMember1 = cell1.getCubeMember();
                    CubeMember cubeMember2 = cell2.getCubeMember();

                    MemberType memberType = cell1.getMemberType();
                    if (MemberType.DIMENSION.equals(memberType)) {

                        String oneIdStr = cubeMember1.getCode();
                        String twoIdStr = cubeMember2.getCode();

                        Integer topLen = topValueList.size();
                        Integer topOneIdx = getIdx(topValueList, oneIdStr);
                        Integer topTwoIdx = getIdx(topValueList, twoIdStr);

                        //命中自定义top值
                        if ((topOneIdx >= 0 && topOneIdx <= topLen) || (topTwoIdx >= 0 && topTwoIdx <= topLen)) {

                            Integer topOneInt = topLen - topOneIdx;
                            Integer topTwoInt = topLen - topTwoIdx;

                            return topOneInt > topTwoInt ? -1 : 1;

                        }

                        String oneStr = cubeMember1.getValue();
                        String twoStr = cubeMember2.getValue();

                        if (Collator.getInstance(Locale.CHINESE).compare(oneStr, twoStr) > 0) {
                            return SortType.DESC.equals(sortType) ? -1 : 1;
                        } else if (Collator.getInstance(Locale.CHINESE).compare(oneStr, twoStr) < 0) {
                            return SortType.DESC.equals(sortType) ? 1 : -1;
                        } else {
                            return 0;
                        }

                    } else {
                        //指标排序
                        cell1 = rowCell1[column];
                        cell2 = rowCell2[column];

                        if (StringUtil.isEmpty(cell1.getValue()) || StringUtil.isEmpty(cell2.getValue())) {
                            return 0;
                        }

                        Double value1Dtl = Double.valueOf(cell1.getValue());
                        Double value2Dtl = Double.valueOf(cell2.getValue());

                        if (value1Dtl.compareTo(value2Dtl) > 0) {
                            return SortType.DESC.equals(sortType) ? -1 : 1;
                        } else if (value1Dtl.compareTo(value2Dtl) < 0) {
                            return SortType.DESC.equals(sortType) ? 1 : -1;
                        } else {
                            return 0;
                        }

                    }

                }

                return 0;

            }
        });

        //排序内容替换
        Integer childLen = childGroupList.size();
        Integer beginRow = 0;
        for (int i = beginRow; i < childLen; i++) {
            Integer childIdx = i - beginRow;
            Matrix.Cell[] orderCell = childGroupList.get(childIdx);
            cells[i] = orderCell;
        }

    }

    /**
     *  补充orderMap
     *  先放入orderMap的对象优先排序
     * @param allScopeOrderList
     * @param rowCells
     * @param orderMap
     */
    private void suppOrderMap(List<Order> allScopeOrderList, Matrix.Cell[] rowCells, Map<Integer, Order> orderMap) {

        for (Order order : allScopeOrderList) {

            String orderCode = order.getCode();
            for (int i = 0; i < rowCells.length; i++) {

                Matrix.Cell rowCell = rowCells[i];
                BaseModel mode = rowCell.getBaseModel();

                if (mode.getCode().equalsIgnoreCase(orderCode)) {
                    //设置数据坐标位置
                    orderMap.put(i, order);
                }
            }
        }

    }

    private boolean isFirstDim(Set<BaseConfigure> metaAxisSet) {
        boolean isDim = false;
        for (BaseConfigure baseConfigure : metaAxisSet) {
            isDim = this.isDimension(baseConfigure);
            break;
        }
        return isDim;
    }

    private boolean isDimension(BaseConfigure configure) {
        return ChartQueryServiceImpl.isDimension(configure);
    }

    public boolean isDimension(String code) {
        return ChartQueryServiceImpl.isDimension(code);
    }

    public boolean isMeasure(String code) {
        return ChartQueryServiceImpl.isMeasure(code);
    }

    private boolean isMeasure(BaseConfigure configure) {
        return ChartQueryServiceImpl.isMeasure(configure);
    }

    private boolean isMeasureGroup(BaseConfigure configure) {
        return ChartQueryServiceImpl.isMeasureGroup(configure);
    }

    private List<Object> getAxisMeta(BuildSqlTuple tuple, Set<BaseConfigure> metaAxisSet, boolean isDim, boolean isMeasGroup) {

        List<Object> objectSet = new LinkedList<>();
        Set<String> displayCodeSet = tuple.getDisplayDimensionCodeSet();
        Set<String> measCodeSet = tuple.getDisplayMeasureCodeSet();

        for (BaseConfigure baseConfigure : metaAxisSet) {
            String code = baseConfigure.getCode();
            if (isDim && this.isDimension(baseConfigure)) {
                //获取维度
                if (displayCodeSet.contains(code)) {
                    Dimension dimension = tuple.findDimension(code);
                    objectSet.add(dimension);
                }

            } else if (isMeasGroup && this.isMeasureGroup(baseConfigure)) {
                //指标分组
                objectSet.add(baseConfigure);
            } else if (!isMeasGroup && !isDim && this.isMeasure(baseConfigure)) {
                //指标
                if (measCodeSet.contains(code)) {

                    //同环比相关设置
                    Measure measure = tuple.findChoiceMeasure(code, baseConfigure.getRatioType()
                            , baseConfigure.getRatioColumnType(), baseConfigure.getRatioValueType());

                    objectSet.add(measure);
                }

            }
        }
        return objectSet;
    }

    /**
     * 标准化轴上信息  级指标分组、级指标项必须相邻
     * @param metaAxisSet
     * @return
     */
    private List<Object> stdcdu(BuildSqlTuple tuple, Set<BaseConfigure> metaAxisSet) {
        List<Object> axisSet = new LinkedList<>();
        boolean isDim = this.isFirstDim(metaAxisSet);

        if (isDim) {

            List<Object> dimMetaSet = this.getAxisMeta(tuple, metaAxisSet, true, false);
            List<Object> measGroupSet = this.getAxisMeta(tuple, metaAxisSet, false, true);
            List<Object> measSet = this.getAxisMeta(tuple, metaAxisSet, false, false);

            axisSet.addAll(dimMetaSet);
            axisSet.addAll(measGroupSet);
            axisSet.addAll(measSet);

        } else {

            List<Object> measGroupSet = this.getAxisMeta(tuple, metaAxisSet, false, true);
            List<Object> measSet = this.getAxisMeta(tuple, metaAxisSet, false, false);
            List<Object> dimMetaSet = this.getAxisMeta(tuple, metaAxisSet, true, false);

            axisSet.addAll(measGroupSet);
            axisSet.addAll(measSet);
            axisSet.addAll(dimMetaSet);

        }

        return axisSet;
    }

    private void setRightDown(Matrix matrix, CellSet cellSet, DataTuple cubeTuple, DataTuple tempDataTuple) {

        Axis rowAxis = cellSet.getRowAxis();
        Axis columnAxis = cellSet.getColumnAxis();

        //计算矩阵
        Integer rowMaxDeep = rowAxis.getMaxDeep();
        Integer columnMaxDeep = columnAxis.getMaxDeep();

        Integer correctHeight = matrix.getCorrectHeight();
        Integer correctWidth = matrix.getCorrectWidth();

        this.setRightDownRow(columnMaxDeep + correctHeight, rowMaxDeep + correctWidth, matrix, cubeTuple, tempDataTuple);

    }

    private Matrix buildMatrix(CellSet cellSet, DataTuple cubeTuple, DataTuple tempDataTuple, BuildSqlTuple tuple) {

        Matrix matrix = new Matrix();
        matrix.setCellSet(cellSet);

        Axis rowAxis = cellSet.getRowAxis();
        Axis columnAxis = cellSet.getColumnAxis();

        //计算矩阵
        Integer rowMaxDeep = rowAxis.getMaxDeep();
        Integer rowDeepes = rowAxis.getAllDeepesMember();

        matrix.setRowMaxDeep(rowMaxDeep);

        Integer columnMaxDeep = columnAxis.getMaxDeep();
        Integer columnDeepes = columnAxis.getAllDeepesMember();

        matrix.setColumnMaxDeep(columnMaxDeep);

        int correctHeight = matrix.getCorrectHeight();
        int correctWidth = matrix.getCorrectWidth();

        Integer width = columnDeepes + rowMaxDeep;//矩阵宽
        Integer height = rowDeepes + columnMaxDeep;//矩阵高

        if (columnDeepes == 0) {
            width++;
        }

        if (rowDeepes == 0) {
            height++;
        }

        matrix.setWidth(width);
        matrix.setHeight(height);

        //构建矩阵元素
        for (int i = 0; i < height; i++) {
            for (int j = 0; j < width; j++) {

                if (i < (columnMaxDeep + correctHeight) && j < (rowMaxDeep + correctHeight)) {
                    //左上区域
                    List<Integer> coorList = new LinkedList<Integer>();
                    coorList.add(i);
                    coorList.add(j);

                    Matrix.Cell cell = matrix.getOrBuild(coorList);
                    cell.setValue(IndicatorConstant.BI_NULL);
                    cell.setRowspan(1);
                    cell.setColspan(1);

                    matrix.getMap().put(coorList, cell);

                } else if (i < (columnMaxDeep + correctHeight) && j >= (rowMaxDeep + correctHeight)) {
                    //column Axis 区域
                    //右上区域
                    List<Integer> coorList = new LinkedList<Integer>();
                    coorList.add(i);
                    coorList.add(j);

                    Matrix.Cell cell = Matrix.buildCell();
                    cell.setX(i);
                    cell.setY(j);
                    cell.setValue("CA:");

                    matrix.getMap().put(coorList, cell);

                } else if (i >= (columnMaxDeep + correctHeight) && j < (rowMaxDeep + correctHeight)) {
                    //row Axis 区域

                    //左下区域
                    List<Integer> coorList = new LinkedList<Integer>();
                    coorList.add(i);
                    coorList.add(j);

                    Matrix.Cell cell = Matrix.buildCell();
                    cell.setX(i);
                    cell.setY(j);
                    cell.setValue("RA");

                    matrix.getMap().put(coorList, cell);

                } else if (i >= (columnMaxDeep + correctHeight) && j >= (rowMaxDeep + correctHeight)) {
                    // data 区域

                    //右下区域
                    List<Integer> coorList = new LinkedList<Integer>();
                    coorList.add(i);
                    coorList.add(j);

                    Matrix.Cell cell = Matrix.buildCell();
                    cell.setX(i);
                    cell.setY(j);
                    cell.setValue(" ");

                    matrix.getMap().put(coorList, cell);

                }
            }
        }

        this.setLeftUp(matrix, cellSet, tuple);
        this.setRightUp(matrix, cellSet);
        this.setLeftDown(matrix, cellSet);
        this.setRightDown(matrix, cellSet, cubeTuple, tempDataTuple);

        return matrix;

    }

    private List buildCoor(Integer x, Integer y) {

        List coorList = new LinkedList();
        coorList.add(x);
        coorList.add(y);

        return coorList;

    }

    private void setRightDownRow(Integer i, Integer j, Matrix matrix, DataTuple cubeTuple, DataTuple tempDataTuple) {

        Integer width = matrix.getWidth();
        Integer height = matrix.getHeight();
        int k = j;
        for (; i < height; i++) {

            for (; k < width; k++) {

                List coorList = this.buildCoor(i, k);
                Matrix.Cell cell = matrix.getOrBuild(coorList);
                FindMeasureTuple findMeasTuple = matrix.buildQuerySqlByDimAndMeas(coorList);
                Matrix.Cell targetMeasCell = findMeasTuple.getTargetMeasCell();
                //设置指标数据, 含同环比
                this.suppCellData(findMeasTuple, cell, cubeTuple, tempDataTuple);

                cell.setRowspan(1);
                cell.setColspan(1);

                cell.setMemberType(MemberType.MEASURE_VALUE);
                cell.setCubeMember(targetMeasCell.getCubeMember());

            }
            k = j;
        }

    }

    private String buildFindMeasSql(FindMeasureTuple measTuple) {

        String sql = "select ";
        Matrix.Cell targetMeasCell = measTuple.getTargetMeasCell();
        Measure targetMeasure = targetMeasCell.getMeasure();
        String column = MEASS_COLUMN_PREFIX + targetMeasure.getName();
        sql += column;
        sql += " from TEMP.TEMP_TABLE where ";

        List<Matrix.Cell> dimCellList = measTuple.getDimCellList();

        String where = "";
        for (Matrix.Cell cell : dimCellList) {

            Dimension dimension = cell.getDimension();
            String dimColumn = DIM_COLUMN_PREFIX + dimension.getName() + "ID";
            String value = cell.getCubeMember().getCode();
            where += " and " +  dimColumn + "='" + value + "'";

        }

        where = where.replaceFirst(" and ", "");
        sql += where;
        sql += " limit 1";

        return sql;

    }

    /**
     * 设置同环比
     * @param measTuple
     * @param tempTable
     * @param cell
     */
    private void setRatioValue(FindMeasureTuple measTuple, TempTable tempTable, Matrix.Cell cell) {

        List<ColumnTypeInfo> columnTypeInfoList = tempTable.getColumnTypeInfoList();
        List<Object[]> dataList = tempTable.getDataList();

        Matrix.Cell targetMeasCell = measTuple.getTargetMeasCell();
        List<Matrix.Cell> dimCellList = measTuple.getDimCellList();

        //定位指标出现在列中的位置
        Integer measIdx = this.getColumnIdx(targetMeasCell, columnTypeInfoList);
        //第一个的索引文件目标值
        Map<Integer, String> dimFilterMap = this.findDimFilterMap(dimCellList, columnTypeInfoList, true);
        //所有文件的目标值
        Map<Integer, String> dimAllFilterMap = this.findDimFilterMap(dimCellList, columnTypeInfoList);

        Set<Object[]> objectsList = tempTable.getHashIndexMap().get(dimFilterMap);
        Measure measure = targetMeasCell.getMeasure();
        RatioType ratioType = measure.getRatioType();
        if (null == ratioType) {
            List<Ratio> ratioList = measure.getRatioList();
            if (!CollectionUtils.isEmpty(ratioList) && !CollectionUtils.isEmpty(objectsList)) {

                for (Object[] objects : objectsList) {

                    boolean pass = this.filterRow(dimAllFilterMap, objects);
                    if (pass) {
                        for (Ratio ratio : ratioList) {

                            ratioType = ratio.getRatioType();
                            Matrix.Cell.Ratio cellRatio = cell.buildRatio();
                            cellRatio.setRatioType(ratioType);
                            //原始值
                            cellRatio.setValue(String.valueOf(objects[++measIdx]));
                            //比率值
                            cellRatio.setRatio(String.valueOf(objects[++measIdx]) + "%");
                            //将节点挂到matrix中
                            cell.getRatioList().add(cellRatio);

                        }
                        break;
                    }

                }

            }
        }

    }

    private String findValue(FindMeasureTuple measTuple, TempTable tempTable) {

        String measValue = IndicatorConstant.BI_MEASURE_NULL;
        List<ColumnTypeInfo> columnTypeInfoList = tempTable.getColumnTypeInfoList();
        List<Object[]> dataList = tempTable.getDataList();

        Matrix.Cell targetMeasCell = measTuple.getTargetMeasCell();
        List<Matrix.Cell> dimCellList = measTuple.getDimCellList();

        //定位指标出现在列中的位置
        Integer measIdx = this.getColumnIdx(targetMeasCell, columnTypeInfoList);
        //索引文件目标值
        Map<Integer, String> dimFilterMap = this.findDimFilterMap(dimCellList, columnTypeInfoList);

        Set<Object[]> objectsList = tempTable.getHashIndexMap().get(dimFilterMap);

        if (!CollectionUtils.isEmpty(objectsList)) {

            for (Object[] objects : objectsList) {
                measValue = String.valueOf(objects[measIdx]);
                break;
            }

        } else {

            /*
            for (Object[] objects : dataList) {

                boolean pass = this.filterRow(dimFilterMap, objects);
                if (pass) {
                    measValue = String.valueOf(objects[measIdx]);
                    break;
                }

            }

            if (!IndicatorConstant.BI_MEASURE_NULL.equalsIgnoreCase(measValue)) {
                this.println("SSs");
            }
            */

        }

        return measValue;

    }

    private boolean filterRow(Map<Integer, String> dimFilterMap, Object[] objects) {

        Set<Map.Entry<Integer, String>> entrySet = dimFilterMap.entrySet();
        boolean pass = true;
        for (Map.Entry<Integer, String> entry : entrySet) {

            Integer idx = entry.getKey();
            String value = entry.getValue();

            pass = pass && value.equalsIgnoreCase(String.valueOf(objects[idx]));

        }

        return pass;
    }

    /**
     * 环比别名
     * @param ratioType
     * @return
     */
    private String buildRatioAlias(RatioType ratioType, RatioValueType ratioValueType) {

        String alias = "";
        if (RatioType.YEARYEMOM.equals(ratioType)) {
            alias = "Y_O_Y";
        } else if (RatioType.MONTHONMONTH.equals(ratioType)) {
            alias = "M_O_M";
        }

        if (RatioValueType.VALUE.equals(ratioValueType)) {
            alias = "ORG_" + alias;
        }

        return alias;

    }

    private String buildMeasColumnName(Matrix.Cell cell) {
        CubeMember cubeMember = cell.getCubeMember();
        Measure measure = cubeMember.getMeasure();
        RatioType ratioType = measure.getRatioType();
        String name = this.buildMeasColumnName(measure.getName());
        if (null != ratioType) {
            name += this.buildRatioAlias(ratioType, measure.getRatioValueType());
        }
        return name;
    }

    private String buildMeasColumnName(String name) {
        return MEASS_COLUMN_PREFIX + name;
    }

    private String buildDimColumnName(String name) {
        return DIM_COLUMN_PREFIX + name + "ID";
    }

    private String buildDegenerateDimColumnName(String name) {
        return DIM_COLUMN_PREFIX + name;
    }

    /**
     * 获取维度筛选条件
     * @param dimCubeMemberList
     * @param columnTypeInfoList
     * @return
     */
    private Map<Integer, String> findDimFilterMapByMember(Set<CubeMember> dimCubeMemberList, List<ColumnTypeInfo> columnTypeInfoList) {

        Map<Integer, String> queryMap = new LinkedHashMap<>();
        for (CubeMember dimMember : dimCubeMemberList) {

            Dimension dim =  dimMember.getDimension();
            Integer idx = this.getColumnIdx(dim, columnTypeInfoList);
            String code = dimMember.getCode();

            queryMap.put(idx, code);

        }

        return queryMap;

    }

    /**
     * 获取行数据的维度筛选条件
     * @param dimCellList
     * @param columnTypeInfoList
     * @return
     */
    private Map<Integer, String> findDimFilterMap(List<Matrix.Cell> dimCellList, List<ColumnTypeInfo> columnTypeInfoList, boolean isFirst) {

        Map<Integer, String> queryMap = new HashMap<>();
        for (Matrix.Cell cell : dimCellList) {

            Integer idx = this.getColumnIdx(cell, columnTypeInfoList);
            CubeMember cubeMember = cell.getCubeMember();
            String code = cubeMember.getCode();

            queryMap.put(idx, code);

            if (isFirst) {
                break;
            }

        }

        return queryMap;

    }

    /**
     * 获取行数据的维度筛选条件
     * @param dimCellList
     * @param columnTypeInfoList
     * @return
     */
    private Map<Integer, String> findDimFilterMap(List<Matrix.Cell> dimCellList, List<ColumnTypeInfo> columnTypeInfoList) {
        return this.findDimFilterMap(dimCellList, columnTypeInfoList, false);
    }

    private Integer getColumnIdx(String name, List<ColumnTypeInfo> columnTypeInfoList) {

        Integer idx = 0;

        for (int i = 0; i < columnTypeInfoList.size(); i++) {
            ColumnTypeInfo info = columnTypeInfoList.get(i);
            if (name.equalsIgnoreCase(info.getName())) {
                idx = i;
                break;
            }
        }

        return idx;

    }

    private Integer getColumnIdx(Dimension dim, List<ColumnTypeInfo> columnTypeInfoList) {

        Integer idx = 0;
        DimType dimType = dim.getDimType();
        String name = this.buildDimColumnName(dim.getName());
        if (DimType.DEGENERATE_DIM.equals(dimType)) {
            name = this.buildDegenerateDimColumnName(dim.getName());
        }

        for (int i = 0; i < columnTypeInfoList.size(); i++) {
            ColumnTypeInfo info = columnTypeInfoList.get(i);
            if (name.equalsIgnoreCase(info.getName())) {
                idx = i;
                break;
            }
        }

        return idx;

    }

    private Integer getColumnIdx(Matrix.Cell cell, List<ColumnTypeInfo> columnTypeInfoList) {

        Integer idx = 0;
        MemberType cellMemberType = cell.getMemberType();

        for (int i = 0; i < columnTypeInfoList.size(); i++) {

            ColumnTypeInfo info = columnTypeInfoList.get(i);
            String name = "";
            if (MemberType.MEASURE.equals(cellMemberType)) {
                Measure measure = cell.getMeasure();
                name = this.buildMeasColumnName(cell);
            } else if (MemberType.DIMENSION.equals(cellMemberType)) {

                Dimension dim = cell.getDimension();
                DimType dimType = dim.getDimType();

                if (DimType.DEGENERATE_DIM.equals(dimType)) {
                    name = this.buildDegenerateDimColumnName(dim.getName());
                } else {
                    name = this.buildDimColumnName(dim.getName());
                }

            }

            if (name.equalsIgnoreCase(info.getName())) {
                idx = i;
                break;
            }

        }

        return idx;

    }

    private void suppCellData(FindMeasureTuple measTuple, Matrix.Cell cell, DataTuple cubeDataTuple, DataTuple tempDataTuple) {

        String vs = this.findValue(measTuple, cubeDataTuple.getTempTable());
        cell.setValue(vs);

        /**
         * 设置同环比
         */
        this.setRatioValue(measTuple, tempDataTuple.getTempTable(), cell);

    }

    private void setLeftDown(Matrix matrix, CellSet cellSet) {

        Axis rowAxis = cellSet.getRowAxis();
        Axis columnAxis = cellSet.getColumnAxis();

        //计算矩阵
        Integer rowMaxDeep = rowAxis.getMaxDeep();
        Integer rowDeepes = rowAxis.getAllDeepesMember();

        Integer columnMaxDeep = columnAxis.getMaxDeep();
        Integer columnDeepes = columnAxis.getAllDeepesMember();

        Integer width = columnDeepes + rowMaxDeep;//矩阵宽
        Integer height = rowDeepes + columnMaxDeep;//矩阵高

        Integer correctHeight = matrix.getCorrectHeight();

        this.setLeftDownRow(columnMaxDeep + correctHeight, 0, rowAxis.getMemberList(), matrix);
    }

    private void setLeftUp(Matrix matrix, CellSet cellSet, BuildSqlTuple tuple) {

        Axis rowAxis = cellSet.getRowAxis();
        Axis columnAxis = cellSet.getColumnAxis();

        Integer rowMaxDeep = rowAxis.getMaxDeep();
        Integer colMaxDeep = columnAxis.getMaxDeep();

        int correctHeight = 0;
        int correctWidth = 0;

        if (colMaxDeep == 0) {
            correctHeight++;
        }

        if (rowMaxDeep == 0) {
            correctWidth++;
        }

        Set<BaseConfigure> columnAxisSet = tuple.getColumnAxisSet();
        Set<BaseConfigure> rowAxisSet = tuple.getRowAxisSet();

        for (int i = 0; i < colMaxDeep; i++) {

            List coorList = this.buildCoor(i, correctWidth + rowMaxDeep - 1);
            CubeMember cubeMember = new CubeMember();
            String findTitle = this.findTilte(tuple, tuple.getColumnAxisSet(), i);
            if (StringUtil.isEmpty(cubeMember.getValue())) {
                cubeMember.setValue(findTitle);
            } else {
                cubeMember.setValue(cubeMember.getValue() + "|" + findTitle);
            }

            Matrix.Cell cell = matrix.getOrBuild(coorList, cubeMember);

        }

        for (int j = 0; j < rowMaxDeep; j++) {

            List coorList = this.buildCoor(correctHeight + colMaxDeep - 1, j);
            Matrix.Cell cell = matrix.get(coorList);
            CubeMember cubeMember = cell.getCubeMember();
            if (null == cubeMember) {
                cubeMember = new CubeMember();
            }
            String findTitle = this.findTilte(tuple, tuple.getRowAxisSet(), j);
            if (StringUtil.isEmpty(cubeMember.getValue())) {
                cubeMember.setValue(findTitle);
            } else {
                cubeMember.setValue(findTitle + "|" + cubeMember.getValue());
            }
            matrix.getOrBuild(coorList, cubeMember);

        }

    }

    private String findTilte(BuildSqlTuple tuple, Set<BaseConfigure> axisSet, int idx) {

        Set<Dimension> dimensionSet = tuple.getDimensionSet();
        String title = "数值";
        boolean firstMeasure = false;

        int point = 0;
        for (BaseConfigure baseConfigure : axisSet) {

            String code = baseConfigure.getCode();

            if (idx == point) {
                for (Dimension dim : dimensionSet) {
                    if (code.equalsIgnoreCase(dim.getCode())) {
                        title = dim.getName();
                    }
                }
            }

            if (this.isDimension(code)) {
                point++;
            }

            if (this.isMeasure(code) && !firstMeasure) {
                firstMeasure = true;
                point++;
            }

        }

        return title;

    }

    private void setRightUp(Matrix matrix, CellSet cellSet) {

        Axis rowAxis = cellSet.getRowAxis();
        Axis columnAxis = cellSet.getColumnAxis();

        //计算矩阵
        Integer rowMaxDeep = rowAxis.getMaxDeep();
        Integer correctWidth = matrix.getCorrectWidth();

        this.setRightUpRow(0, rowMaxDeep + correctWidth, columnAxis.getMemberList(), matrix);

    }

    private void setLeftDownRow(Integer i, Integer j, List<CubeMember> memberList, Matrix matrix) {

        for (CubeMember cubeMember : memberList) {

            Integer countRows = cubeMember.getChildSum();

            for (int k = 0; k < countRows; k++) {

                List coorList = this.buildCoor(i, j);
                Matrix.Cell cell = matrix.getOrBuild(coorList, cubeMember);

                List<CubeMember> childMemberList = cubeMember.getChildMemberList();
                //含有下级对象并且是首次输出时，打印下级对象
                if (!CollectionUtils.isEmpty(childMemberList) && k == 0) {

                    cell.setColspan(1);
                    cell.setRowspan(countRows);

                    this.setLeftDownRow(i, j + 1, childMemberList, matrix);

                } else if (k == 0) {
                    cell.setColspan(1);
                    cell.setRowspan(1);
                }

                i++;

            }

        }
    }

    private void setRightUpRow(Integer i, Integer j, List<CubeMember> memberList, Matrix matrix) {

        for (CubeMember cubeMember : memberList) {

            Integer countColumn = cubeMember.getChildSum();

            for (int k = 0; k < countColumn; k++) {

                List coorList = this.buildCoor(i, j);
                Matrix.Cell cell = matrix.getOrBuild(coorList, cubeMember);

                List<CubeMember> childMemberList = cubeMember.getChildMemberList();
                //含有下级对象并且是首次输出时，打印下级对象
                if (!CollectionUtils.isEmpty(childMemberList) && k == 0) {

                    cell.setColspan(countColumn);
                    cell.setRowspan(1);

                    this.setRightUpRow(i + 1, j, childMemberList, matrix);

                } else if (k == 0) {
                    cell.setColspan(1);
                    cell.setRowspan(1);
                }

                j++;

            }

        }
    }

    private void print(Matrix matrix) {

        Integer width = matrix.getWidth();
        Integer height = matrix.getHeight();

        for (int i = 0; i < height; i++) {
            for (int j = 0; j < width; j++) {

                List<Integer> coorList = new LinkedList<>();
                coorList.add(i);
                coorList.add(j);

                Matrix.Cell cell = matrix.getMap().get(coorList);

                if (null != cell && null != cell.getValue()) {
                    Integer len = cell.getValue().length();
                    String suppStr = this.suppFormatTable(10 - len);
                    this.println(suppStr + cell.getValue());
                    this.println(" | ");
                }

            }
            this.println("");
        }
    }

    private String suppFormatTable(Integer dev) {

        String suppStr = "";
        for (int i = 0; i < dev; i++) {
            suppStr += " ";
        }

        return suppStr;

    }

    private String buildRollupOrCubeSql(BuildSqlTuple tuple, String fun) {

        String sql = "select ";
        String column = "";
        String rollupSql = "";
        Set<Dimension> choiceDimensionSet = tuple.getChoiceDimensionSet();
        Set<String> displayDimCodeSet = tuple.getDisplayDimensionCodeSet();

        for (Dimension dim : choiceDimensionSet) {

            if (!displayDimCodeSet.contains(dim.getCode())) {
                continue;
            }

            //退化维只有一列描述
            if (DimType.DEGENERATE_DIM.equals(dim.getDimType())) {

                String columnName = DIM_COLUMN_PREFIX + dim.getName();
                column += "," + columnName;
                rollupSql += "," + columnName;

            } else {
                //其它维度同时含有两列,但此处只按id求rollup
                String columnId = DIM_COLUMN_PREFIX + dim.getName() + "ID";
                column += "," + columnId;
                rollupSql += "," + columnId;

            }
        }

        Set<String> displayMeasCodeSet = tuple.getDisplayMeasureCodeSet();
        //页面选择的指标
        Set<Measure> choiceMeasureSet = tuple.getChoiceMeasureSet();
        for (Measure measure : choiceMeasureSet) {

            if (!displayMeasCodeSet.contains(measure.getCode())) {
                continue;
            }

            String columnName = null;
            RatioType ratioType = measure.getRatioType();
            if (null == ratioType) {
                columnName = MEASS_COLUMN_PREFIX + measure.getName();
            } else {
                RatioValueType ratioValueType = measure.getRatioValueType();
                String ratioAlias = RatioType.MONTHONMONTH.equals(ratioType) ? "M_O_M" : "Y_O_Y";
                if (RatioValueType.VALUE.equals(ratioValueType)) {
                    columnName = MEASS_COLUMN_PREFIX + measure.getName() + "ORG_" + ratioAlias;
                } else {
                    columnName = MEASS_COLUMN_PREFIX + measure.getName() + ratioAlias;
                }
            }

            column += ", sum(" + columnName + ")";

        }

        column = column.replaceFirst(",", "");
        rollupSql = rollupSql.replaceFirst(",", "");

        String orderBy = this.buildOrderBy(tuple, "_talis_");
        if (StringUtil.isNotEmpty(orderBy)) {
            orderBy = " order by " + orderBy;
        }

        sql += column + " from TEMP.TEMP_TABLE group by " + fun + "(" + rollupSql + ")" + orderBy;// + _d_alis_日期, _d_alis_城市)

        return sql;

    }

    /**
     * 排序方法
     * @param tuple
     * @param alias
     * @return
     */
    private String buildOrderBy(BuildSqlTuple tuple, String alias) {
        //order by 处理
        String orderBy = "";
        QueryParam queryParam = tuple.getQueryParam();
        List<Order> orderList = queryParam.getOrderList();
        Set<Dimension> dimensionSet = tuple.getDimensionSet();
        Set<Measure> measureSet = tuple.getChoiceMeasureSet();

        for (Order order : orderList) {

            Dimension dimension = BuildSqlServiceImpl.getDimension(order.getCode(), dimensionSet);
            if (null != dimension && !dimension.isAll()) {
                orderBy = buildDimOrder(orderBy, order, alias, dimension);
            }

            Measure measure = BuildSqlServiceImpl.getMeasure(order.getCode(), measureSet);
            if (null != measure) {
                orderBy = buildMeasOrder(orderBy, order, measure);
            }

        }

        orderBy = orderBy.replaceFirst("," , "");
        orderBy = orderBy.replaceAll("_talis_\\.", "");
        orderBy = orderBy.replaceAll("#q#", "");
        orderBy = orderBy.toUpperCase();

        return orderBy;

    }

    public final static String buildDimOrder(String orderBy, Order order, String alias, Dimension dimension) {

        List<String> valueList =  order.getValueList();
        String orderType = "asc";

        String defaultNumber = "99999";
        if (SortType.DESC.equals(order.getSortType())) {
            orderType = "desc";
        } else {
            defaultNumber = "0";
        }

        String idStr = "id";
        if (DimType.DEGENERATE_DIM.equals(dimension.getDimType())) {
            idStr = "";
        }
        //统一以id作为对比条件自定义排序
        if (valueList.size() > 0) {

            int idx = 1;
            orderBy += ", case";
            for (String value : valueList) {
                value = BuildSqlServiceImpl.formatSqlValue(value);
                orderBy += " when " + alias + ".#q#" + BuildSqlServiceImpl.getColumnAlias(dimension) + idStr + "#q#='" + value + "' then " + (idx++);
            }
            orderBy += " when true then 9999 end " + orderType;

        } else {

            ViewType viewType = dimension.getViewType();
            if (ViewType.NUMBER.equals(viewType)) {
                orderBy += ", cast(" + alias + ".#q#" + BuildSqlServiceImpl.getColumnAlias(dimension) + idStr + "#q# as DECIMAL(22, 2))" + orderType;
            } else {
                orderBy += ", case when " + alias + ".#q#" + BuildSqlServiceImpl.getColumnAlias(dimension) + idStr + "#q#=null then " + defaultNumber + " when " + alias + ".#q#" + BuildSqlServiceImpl.getColumnAlias(dimension) + idStr + "#q#<>null then " + alias + ".#q#" + BuildSqlServiceImpl.getColumnAlias(dimension) + idStr + "#q# end " + orderType;
            }

        }

        return orderBy;

    }

    /**
     * 指标排序处理
     * @param orderBy
     * @param order
     * @param measure
     * @return
     */
    public String buildMeasOrder(String orderBy, Order order, Measure measure) {

        List<String> valueList =  order.getValueList();
        String orderType = "asc";

        if (SortType.DESC.equals(order.getSortType())) {
            orderType = "desc";
        }

        orderBy += ", sum(" + BuildSqlServiceImpl.getColumnAlias(measure) + ") " + orderType;

        return orderBy;

    }

    private TempTable buildRollupByTempTable(BuildSqlTuple tuple, TempTable tempTable, String fun) {

        String rollupSql = this.buildRollupByTempTableSql(tuple, fun);
        List<Object[]> rollupDataList = this.query(rollupSql, tempTable);

        TempTable cubeTable = new TempTable();
        cubeTable.setDataList(rollupDataList);

        //虚拟主键
        List<ColumnTypeInfo> columnTypeInfoList = cubeTable.getColumnTypeInfoList();
        ColumnTypeInfo columnTypeInfo = ColumnTypeInfo.build("TEMP_ID", "12");

        columnTypeInfoList.add(columnTypeInfo);

        //页面选择的维度
        Set<Dimension> choiceDimensionSet = tuple.getChoiceDimensionSet();
        List<String> columnNameList = new LinkedList<>();
        List<String> columnTypeList = new LinkedList<>();

        Set<String> displayDimCodeSet = tuple.getDisplayDimensionCodeSet();
        for (Dimension dim : choiceDimensionSet) {

            if (!displayDimCodeSet.contains(dim.getCode())) {
                continue;
            }

            //退化维值是自身，不需要有id
            if (DimType.DEGENERATE_DIM.equals(dim.getDimType())) {

                String columnName = DIM_COLUMN_PREFIX + dim.getName();
                columnNameList.add(columnName);
                columnTypeList.add("12");

            } else {
                //其它维度加ID
                String columnId = DIM_COLUMN_PREFIX + dim.getName() + "ID";
                columnNameList.add(columnId);
                columnTypeList.add("12");
            }
        }

        Set<String> displayMeasCodeSet = tuple.getDisplayMeasureCodeSet();
        //页面选择的指标
        Set<Measure> choiceMeasureSet = tuple.getChoiceMeasureSet();
        for (Measure measure : choiceMeasureSet) {

            if (!displayMeasCodeSet.contains(measure.getCode())) {
                continue;
            }

            this.buildMeasColumn(columnNameList, columnTypeList, measure);

        }

        String[] columnNames = columnNameList.toArray(new String[columnNameList.size()]);
        String[] columnTypes = columnTypeList.toArray(new String[columnTypeList.size()]);

        for (int idx = 0; idx < columnNames.length; idx++) {

            String name = columnNames[idx];
            String type = String.valueOf(columnTypes[idx]);

            columnTypeInfo = ColumnTypeInfo.build(name.toUpperCase(), type);
            columnTypeInfoList.add(columnTypeInfo);

        }

        return cubeTable;

    }

    private String buildRollupByTempTableSql(BuildSqlTuple tuple, String fun) {

        String sql = "select ";
        String column = "";
        String rollupSql = "";
        Set<Dimension> choiceDimensionSet = tuple.getChoiceDimensionSet();
        Set<String> displayDimCodeSet = tuple.getDisplayDimensionCodeSet();

        for (Dimension dim : choiceDimensionSet) {

            if (!displayDimCodeSet.contains(dim.getCode())) {
                continue;
            }

            //退化维只有一列描述
            if (DimType.DEGENERATE_DIM.equals(dim.getDimType())) {

                String columnName = DIM_COLUMN_PREFIX + dim.getName();
                column += "," + columnName;
                rollupSql += "," + columnName;

            } else {
                //其它维度同时含有两列,但此处只按id求rollup
                String columnId = DIM_COLUMN_PREFIX + dim.getName() + "ID";
                column += "," + columnId;
                rollupSql += "," + columnId;

            }
        }

        Set<String> displayMeasCodeSet = tuple.getDisplayMeasureCodeSet();
        //页面选择的指标
        Set<Measure> choiceMeasureSet = tuple.getChoiceMeasureSet();
        for (Measure measure : choiceMeasureSet) {

            if (!displayMeasCodeSet.contains(measure.getCode())) {
                continue;
            }

            String columnName = null;
            RatioType ratioType = measure.getRatioType();
            if (null == ratioType) {
                columnName = MEASS_COLUMN_PREFIX + measure.getName();
            } else {
                RatioValueType ratioValueType = measure.getRatioValueType();
                String ratioAlias = RatioType.MONTHONMONTH.equals(ratioType) ? "M_O_M" : "Y_O_Y";
                if (RatioValueType.VALUE.equals(ratioValueType)) {
                    columnName = MEASS_COLUMN_PREFIX + measure.getName() + "ORG_" + ratioAlias;
                } else {
                    columnName = MEASS_COLUMN_PREFIX + measure.getName() + ratioAlias;
                }
            }

            column += ", " + columnName + "";

        }

        column = column.replaceFirst(",", "");
        rollupSql = rollupSql.replaceFirst(",", "");

        sql += column + " from TEMP.TEMP_TABLE";// + _d_alis_日期, _d_alis_城市)

        return sql;

    }

    /**
     * 构建rollupTable(cubeTable)
     * @param tuple
     * @param tempTable
     * @return
     */
    private TempTable buildRollupOrCubeTable(BuildSqlTuple tuple, TempTable tempTable, String fun) {

        //rollup sql
        String rollupSql = this.buildRollupOrCubeSql(tuple, fun);
        List<Object[]> rollupDataList = this.query(rollupSql, tempTable);

        TempTable cubeTable = new TempTable();
        cubeTable.setDataList(rollupDataList);
        //虚拟主键
        List<ColumnTypeInfo> columnTypeInfoList = cubeTable.getColumnTypeInfoList();
        ColumnTypeInfo columnTypeInfo = ColumnTypeInfo.build("TEMP_ID", "12");

        columnTypeInfoList.add(columnTypeInfo);

        //页面选择的维度
        Set<Dimension> choiceDimensionSet = tuple.getChoiceDimensionSet();
        List<String> columnNameList = new LinkedList<>();
        List<String> columnTypeList = new LinkedList<>();

        Set<String> displayDimCodeSet = tuple.getDisplayDimensionCodeSet();
        for (Dimension dim : choiceDimensionSet) {

            if (!displayDimCodeSet.contains(dim.getCode())) {
                continue;
            }

            //退化维值是自身，不需要有id
            if (DimType.DEGENERATE_DIM.equals(dim.getDimType())) {

                String columnName = DIM_COLUMN_PREFIX + dim.getName();
                columnNameList.add(columnName);
                columnTypeList.add("12");

            } else {
                //其它维度加ID
                String columnId = DIM_COLUMN_PREFIX + dim.getName() + "ID";
                columnNameList.add(columnId);
                columnTypeList.add("12");
            }
        }

        Set<String> displayMeasCodeSet = tuple.getDisplayMeasureCodeSet();
        //页面选择的指标
        Set<Measure> choiceMeasureSet = tuple.getChoiceMeasureSet();
        for (Measure measure : choiceMeasureSet) {

            if (!displayMeasCodeSet.contains(measure.getCode())) {
                continue;
            }

            this.buildMeasColumn(columnNameList, columnTypeList, measure);
            /*
            String columnName = MEASS_COLUMN_PREFIX + measure.getName();
            columnNameList.add(columnName);
            columnTypeList.add("-6");
            */

        }

        String[] columnNames = columnNameList.toArray(new String[columnNameList.size()]);
        String[] columnTypes = columnTypeList.toArray(new String[columnTypeList.size()]);

        for (int idx = 0; idx < columnNames.length; idx++) {

            String name = columnNames[idx];
            String type = String.valueOf(columnTypes[idx]);

            columnTypeInfo = ColumnTypeInfo.build(name.toUpperCase(), type);
            columnTypeInfoList.add(columnTypeInfo);

        }

        return cubeTable;

    }

    private List<Object[]> query(String sql, Connection connection) throws RuntimeException {

        Long begin = System.currentTimeMillis();
        List<Object[]> dataList = new LinkedList<>();
        try {
            Statement statement = connection.createStatement();
            ResultSet resultSet = statement.executeQuery(sql);

            while (resultSet.next()) {
                Object[] rows = new Object[1];
                String value = resultSet.getString(1);
                if (value == null) {
                    value = "全部";
                }
                rows[0] = value;
                dataList.add(rows);
            }

        } catch (Exception ex) {
            ex.printStackTrace();
            throw new RuntimeException(ex);
        }

        this.println("query cost : " + (System.currentTimeMillis() - begin));

        return dataList;

    }

    private Connection buildConnection(TempTable tempTable) throws RuntimeException {

        MemorySchema memorySchema = new MemorySchema();
        Statement statement = null;
        ResultSet resultSet = null;
        Connection connection = null;
        try {

            Class.forName("org.apache.calcite.jdbc.Driver");
            connection = DriverManager.getConnection("jdbc:calcite:");
            CalciteConnection calciteConnection =  connection.unwrap(CalciteConnection.class);
            SchemaPlus rootSchema = calciteConnection.getRootSchema();

            memorySchema.addTable("TEMP_TABLE", tempTable);

            memorySchema.build();
            rootSchema.add("TEMP", memorySchema);

        } catch (Exception ex) {
            ex.printStackTrace();
            throw new RuntimeException(ex);
        }

        return connection;

    }

    private List<Object[]> query(String sql, TempTable tempTable) throws RuntimeException {

        MemorySchema memorySchema = new MemorySchema();
        Statement statement = null;
        ResultSet resultSet = null;
        Connection connection = null;
        List<Object[]> dataList = new LinkedList<>();
        try {

            Class.forName("org.apache.calcite.jdbc.Driver");
            connection = DriverManager.getConnection("jdbc:calcite:");
            CalciteConnection calciteConnection =  connection.unwrap(CalciteConnection.class);
            SchemaPlus rootSchema = calciteConnection.getRootSchema();

            memorySchema.addTable("TEMP_TABLE", tempTable);

            memorySchema.build();
            rootSchema.add("TEMP", memorySchema);
            statement = connection.createStatement();

            resultSet = statement.executeQuery(sql);
            int k = resultSet.getMetaData().getColumnCount();
            for (int i = 1; i <= k; i++) {
                String name = resultSet.getMetaData().getColumnLabel(i);
                QueryResultColumnInfo info = new QueryResultColumnInfo();
                info.setName(name);
            }

            while (resultSet.next()) {
                int n = resultSet.getMetaData().getColumnCount();
                Object[] rows = new Object[n + 1];
                rows[0] = UUID.randomUUID().toString();
                for (int i = 1; i <= n; i++) {

                    int type = resultSet.getMetaData().getColumnType(i);
                    String value = resultSet.getString(i);
                    if (value == null) {
                        value = IndicatorConstant.ROLLUP_CUBE_ALL;
                    }
                    rows[i] = value;

                    if (Types.DOUBLE == type) {

                        try {
                            Double dv = Double.valueOf(value);
                            Long l1 = Math.round(dv * 100000);
                            String tv = String.valueOf(l1 / 100000.0);
                            int pint = tv.lastIndexOf(".0");
                            int len = tv.length();
                            if ((pint + 2) == len) {
                                tv = tv.replaceFirst("\\.0", "");
                            }
                            rows[i] = tv;
                        } catch (Exception ex) {
                            ex.printStackTrace();
                        }

                    } else if (Types.VARCHAR == type) {
                        if (value == null) {
                            value = IndicatorConstant.ROLLUP_CUBE_ALL;
                        }
                    } else if (Types.INTEGER == type) {

                    }

                }
                dataList.add(rows);
            }

        } catch (Exception ex) {
            ex.printStackTrace();
            throw new RuntimeException(ex);
        } finally {
            try {
                memorySchema = null;
                statement.close();
                connection.close();
                resultSet.close();
            } catch (Exception ex) {
                ex.printStackTrace();
            }

        }

        return dataList;

    }

    private void buildMeasColumn(List<String> columnNameList, List<String> columnTypeList, Measure measure) {
        //同环比类型
        RatioType ratioType = measure.getRatioType();
        String columnName = MEASS_COLUMN_PREFIX + measure.getName();

        if (null == ratioType) {

            columnNameList.add(columnName);
            columnTypeList.add("-6");

            /**
             * 指标同环比内容设置，为同环比原始值、比率。
             * 此处相当于 measure.getRatioColumnType() 默认为IN。
             */
            List<Ratio> ratioList = measure.getRatioList();
            if (!CollectionUtils.isEmpty(ratioList)) {
                for (Ratio ratio : ratioList) {
                    ratioType = ratio.getRatioType();
                    if (RatioType.MONTHONMONTH.equals(ratioType) || RatioType.YEARYEMOM.equals(ratioType)) {
                        String ratioAlias = RatioType.MONTHONMONTH.equals(ratioType) ? "M_O_M" : "Y_O_Y";
                        columnNameList.add(columnName + "ORG_" + ratioAlias);
                        columnTypeList.add("-6");

                        columnNameList.add(columnName + ratioAlias);
                        columnTypeList.add("-6");

                    }
                }
            }

        } else {

            String ratioAlias = RatioType.MONTHONMONTH.equals(ratioType) ? "M_O_M" : "Y_O_Y";
            RatioValueType rvType = measure.getRatioValueType();
            if (RatioType.MONTHONMONTH.equals(ratioType) || RatioType.YEARYEMOM.equals(ratioType)) {

                if (RatioValueType.VALUE.equals(rvType)) {
                    columnNameList.add(columnName + "ORG_" + ratioAlias);
                } else {
                    columnNameList.add(columnName + ratioAlias);
                }

                columnTypeList.add("-6");

            }

        }
    }

    private TempTable buildResultTable(BuildSqlTuple tuple, QueryResult result) {

        List<Map<String, Object>> orgDataList = result.getValueMap();
        TempTable tempTable = new TempTable();
        //虚拟主键
        List<ColumnTypeInfo> columnTypeInfoList = tempTable.getColumnTypeInfoList();
        ColumnTypeInfo columnTypeInfo = ColumnTypeInfo.build("TEMP_ID", "12");

        columnTypeInfoList.add(columnTypeInfo);

        //页面选择的维度
        Set<Dimension> choiceDimensionSet = tuple.getChoiceDimensionSet();
        List<String> columnNameList = new LinkedList<>();
        List<String> columnTypeList = new LinkedList<>();

        int columnCount = 1;
        Set<String> displayDimCodeSet = tuple.getDisplayDimensionCodeSet();
        for (Dimension dim : choiceDimensionSet) {

            if (!displayDimCodeSet.contains(dim.getCode())) {
                continue;
            }

            //退化维只有一列描述
            if (DimType.DEGENERATE_DIM.equals(dim.getDimType())) {

                String columnName = DIM_COLUMN_PREFIX + dim.getName();
                columnNameList.add(columnName);
                columnTypeList.add("12");
                columnCount++;

            } else {
                //其它维度同时含有两列
                String columnId = DIM_COLUMN_PREFIX + dim.getName() + "ID";
                columnNameList.add(columnId);
                columnTypeList.add("12");
                columnCount++;

                String columnName = DIM_COLUMN_PREFIX + dim.getName();
                columnNameList.add(columnName);
                columnTypeList.add("12");
                columnCount++;
            }
        }

        Set<String> displayMeasCodeSet = tuple.getDisplayMeasureCodeSet();
        //页面选择的指标
        Set<Measure> choiceMeasureSet = tuple.getChoiceMeasureSet();
        for (Measure measure : choiceMeasureSet) {

            if (!displayMeasCodeSet.contains(measure.getCode())) {
                continue;
            }

            this.buildMeasColumn(columnNameList, columnTypeList, measure);
            /*

            //同环比类型
            RatioType ratioType = measure.getRatioType();
            String columnName = MEASS_COLUMN_PREFIX + measure.getName();

            if (null == ratioType) {

                columnNameList.add(columnName);
                columnTypeList.add("-6");

                 // 指标同环比内容设置，为同环比原始值、比率。
                 // 此处相当于 measure.getRatioColumnType() 默认为IN。
                List<Ratio> ratioList = measure.getRatioList();
                if (!CollectionUtils.isEmpty(ratioList)) {
                    for (Ratio ratio : ratioList) {
                        ratioType = ratio.getRatioType();
                        if (RatioType.MONTHONMONTH.equals(ratioType) || RatioType.YEARONYEAR.equals(ratioType)) {
                            String ratioAlias = RatioType.MONTHONMONTH.equals(ratioType) ? "M_O_M" : "Y_O_Y";
                            columnNameList.add(columnName + "ORG_" + ratioAlias);
                            columnTypeList.add("-6");

                            columnNameList.add(columnName + ratioAlias);
                            columnTypeList.add("-6");

                        }
                    }
                }

            } else {

                String ratioAlias = RatioType.MONTHONMONTH.equals(ratioType) ? "M_O_M" : "Y_O_Y";
                RatioValueType rvType = measure.getRatioValueType();
                if (RatioType.MONTHONMONTH.equals(ratioType) || RatioType.YEARONYEAR.equals(ratioType)) {

                    if (RatioValueType.VALUE.equals(rvType)) {
                        columnNameList.add(columnName + "ORG_" + ratioAlias);
                    } else {
                        columnNameList.add(columnName + ratioAlias);
                    }

                    columnTypeList.add("-6");

                }

            }
        */

        }

        String[] columnNames = columnNameList.toArray(new String[columnNameList.size()]);
        String[] columnTypes = columnTypeList.toArray(new String[columnTypeList.size()]);

        for (int idx = 0; idx < columnNames.length; idx++) {

            String name = columnNames[idx];
            String type = String.valueOf(columnTypes[idx]);

            columnTypeInfo = ColumnTypeInfo.build(name.toUpperCase(), type);
            columnTypeInfoList.add(columnTypeInfo);

        }

        Integer columSize = columnTypeInfoList.size();
        List<Object[]> dataList = tempTable.getDataList();

        for (Map<String, Object> stringObjectMap : orgDataList) {

            Object[] objs = new Object[columSize];
            objs[0] = UUID.randomUUID().toString();

            Collection vs = stringObjectMap.values();
            Object[] datas = vs.toArray();

            for (int i = 0; i < datas.length; i++) {

                //此处对null 维度处理为 BI_NULL，指标处理为0.
                if (i < (columnCount - 1) && null == datas[i]) {
                    //维度处理
                    datas[i] = IndicatorConstant.BI_PIVOT_NULL;
                } else if (i >= (columnCount - 1)) {
                    //指标处理
                    String vds = String.valueOf(datas[i]);
                    vds = vds.replaceAll(",", "");
                    if (vds.indexOf("%") >= 0) {
                        //百分比
                        vds = vds.replaceAll("%", "");
                        Double vdd = Double.parseDouble(vds) * 100;
                        datas[i] = vdd.intValue();
                    } else {
                        //千分位
                        try {
                            vds = vds.replaceAll(",", "");
                            Double d1 = new DecimalFormat().parse(vds).doubleValue();
                            datas[i] = d1;
                        } catch (Exception ex) {
//                            ex.printStackTrace();
                        }

                    }

                    if (!isNumeric(vds)) {
                        datas[i] = 0;
                    }
                }
                String type = columnTypes[i];
                if ("-6".equalsIgnoreCase(type)) {
                    try {
                        String vds = String.valueOf(datas[i]);
                        Double d1 = new DecimalFormat().parse(vds).doubleValue();
                        objs[i + 1] = d1;
                    } catch (Exception ex) {
                        ex.printStackTrace();
                    }
                } else {
                    objs[i + 1] = datas[i];
                }

            }
            dataList.add(objs);
        }

        return tempTable;

    }

}
