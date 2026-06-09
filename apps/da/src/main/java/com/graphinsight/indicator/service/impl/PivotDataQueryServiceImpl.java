package com.graphinsight.indicator.service.impl;

import com.graphinsight.indicator.constant.IndicatorConstant;
import com.graphinsight.indicator.enums.CellType;
import com.graphinsight.indicator.enums.MemberType;
import com.graphinsight.indicator.model.*;
import com.graphinsight.indicator.service.DataQueryService;
import com.graphinsight.indicator.service.PivotService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 透视表
 */
@Service("pivotDataQuery")
public class PivotDataQueryServiceImpl extends DataQueryService {

    @Autowired
    PivotService pivotService;

    @Override
    public PageData queryData(BuildSqlTuple tuple, PageData pageData) {

//        pageData = new PageData();
//        Integer count = super.baseCountQuery(tuple, new PageData());
        tuple.setPivot(true);
//        QueryResult result = super.baseListQuery(tuple, pageData);
        QueryResult result = super.baseTableQuery(tuple, pageData);

        QueryParam queryParam = tuple.getQueryParam();
        Boolean pageable = false;
        if (null != queryParam) {
            pageable = queryParam.isPageable();
        }

        if (true) {
            //如果不分页，默认走list逻辑.
            //页面选择的维度
            Set<Dimension> choiceDimensionSet = tuple.getChoiceDimensionSet();
            //页面选择的指标
            Set<Measure> choiceMeasureSet = tuple.getChoiceMeasureSet();
            List<List<Cell>> cellTableList = super.buildCell(result.getValues(), choiceDimensionSet, choiceMeasureSet, tuple);

            pageData.setCellList(cellTableList);

        } else {

            Matrix matrix = pivotService.buildMatrix(tuple, result);
            Matrix pageMatrix = this.paging(matrix, tuple, pageData);
            this.buildCountInfo(matrix, tuple, pageData);

            List<List<Cell>> cellTableList = this.buildCellPivotList(pageMatrix);
            pageData.setCellList(cellTableList);

        }

//        try {
//            Matrix matrix = pivotService.buildMatrix(tuple, result);
//            String fileName = FileTask.writePivotXls(matrix);
//        } catch (Exception ex) {
//            ex.printStackTrace();
//            pageData.setMessageInfo(ex.getMessage());
//        }

        //分页后的数据
        /*
        try {

            Long begin = System.currentTimeMillis();
            Matrix matrix = pivotService.buildMatrix(tuple, result);
            System.out.println("AAA" + (System.currentTimeMillis() - begin));

            Matrix pageMatrix = this.paging(matrix, tuple, pageData);
            System.out.println("BBB" + (System.currentTimeMillis() - begin));

            this.buildCountInfo(matrix, tuple, pageData);
            System.out.println("CCC" + (System.currentTimeMillis() - begin));
            //重新计算总数

            List<List<Cell>> orgCellList = this.buildCellList(pageMatrix);
            pageData.setCellList(orgCellList);

            System.out.println("DDD" + (System.currentTimeMillis() - begin));

            pivotService.resetCoorColSpan(pageMatrix);
//            List<List<Cell>> cellTableList = this.buildCellPivotList(pageMatrix);
//            pageData.setCellList(cellTableList);
            String fileName1 = FileTask.writePivotXls(pageMatrix);

            System.out.println("EEE" + (System.currentTimeMillis() - begin));

        } catch (Exception ex) {
            ex.printStackTrace();
            pageData.setMessageInfo(ex.getMessage());
        }
        */
//        List<List<Cell>> cellTableList = this.buildCellPivotList(pageMatrix);
//        pageData.setCellList(cellTableList);
        return pageData;


    }

    private List<List<Cell>> buildCellPivotList(Matrix matrix) {

        List<List<Cell>> rowCellList = new LinkedList<>();

        Integer rows = matrix.getHeight();
        Integer column = matrix.getWidth();

        for (int i = 0; i < rows; i++) {

            List<Cell> columnCellList = new LinkedList<>();
            for (int j = 0; j < column; j++) {

                Matrix.Cell matrixCell = matrix.get(i, j);

                Cell cell = this.buildCell(matrixCell);
                cell.setRow(i);
                cell.setColumn(j);

                cell.setRowSpan(matrixCell.getRowspan());
                cell.setColSpan(matrixCell.getColspan());

                columnCellList.add(cell);

            }

            rowCellList.add(columnCellList);

        }

        return rowCellList;
    }

    private List<List<Cell>> buildCellList(Matrix matrix) {

        List<List<Cell>> rowCellList = new LinkedList<>();
        List<Cell> tempDimList = null;
        List<Cell> columnCellList = null;

        Integer rows = matrix.getHeight();
        Integer column = matrix.getWidth();

        Map<String, List<Cell>> cellMap = new HashMap<>();
        for (int i = 0; i < rows; i++) {

            for (int j = 0; j < column; j++) {

                Matrix.Cell matrixCell = matrix.get(i, j);
                if (MemberType.MEASURE_VALUE.equals(matrixCell.getMemberType())) {

                    if (IndicatorConstant.BI_MEASURE_NULL.equals(matrixCell.getValue())) {
                        continue;
                    }

//                    List<Cell> dimCells = this.getDimCells(matrix, i, j);
                    List<Cell> dimCells = this.getDimCells(cellMap, matrix, i, j);
                    if (null == tempDimList) {
                        //第一行
                        tempDimList = dimCells;
                        columnCellList = new LinkedList<>();
                        columnCellList.addAll(dimCells);
                        columnCellList.add(this.buildCell(matrixCell));

                    } else if (this.equalsDimCells(dimCells, tempDimList)) {
                        //同行指标
                        columnCellList.add(this.buildCell(matrixCell));
                    } else {
                        //将之前的行放入结果集
                        rowCellList.add(columnCellList);

                        tempDimList = dimCells;
                        columnCellList = new LinkedList<>();
                        columnCellList.addAll(dimCells);
                        columnCellList.add(this.buildCell(matrixCell));
                    }

                }
            }

        }

        //最后一行
        rowCellList.add(columnCellList);

        return rowCellList;
    }

    private List<Cell>  getDimCells(Map<String, List<Cell>> cellMap, Matrix matrix, int i, int j) {

        List<Cell> allCellList = new ArrayList<>();
        List<Cell> rowCellList = cellMap.get("row" + i);
        if (null == rowCellList) {
            rowCellList = this.getRowDimCells(matrix, i, j);
            cellMap.put("row" + i, rowCellList);
        }
        allCellList.addAll(rowCellList);

        List<Cell> colCellList = cellMap.get("col" + j);
        if (null == colCellList) {
            colCellList = this.getColDimCells(matrix, i, j);
            cellMap.put("col" + j, colCellList);
        }
        allCellList.addAll(colCellList);

        return allCellList;

    }

    /**
     * 判断指标所拥有的维度头是否是一个
     * @param befores
     * @param currents
     * @return
     */
    private boolean equalsDimCells(List<Cell> befores, List<Cell> currents) {

        boolean equals = true;
        if (befores.size() == 0 && currents.size() == 0) {
            //equals = true;
        } else {

            int len = befores.size();
            for (int i = 0; i < len; i++) {

                Cell beforeCell = befores.get(i);
                Cell currentCell = currents.get(i);
                CellType beforeType = beforeCell.getType();
                CellType currentType = currentCell.getType();

                if (CellType.DIMENSION.equals(beforeType) && CellType.DIMENSION.equals(currentType)) {

                    String beforeDimId = beforeCell.getId();
                    String currentDimId = currentCell.getId();

                    if (!beforeDimId.equalsIgnoreCase(currentDimId)) {
                        equals = false;
                        break;
                    }

                }

            }

        }

        return equals;

    }

    private List<Cell> getColDimCells(Matrix matrix, int pi, int pj) {

        List<Cell> dimCellList = new LinkedList<>();
        for (int i = (pi - 1); i >= 0; i--) {

            Matrix.Cell cell = matrix.get(i, pj);
            MemberType memberType = cell.getMemberType();

            if (MemberType.DIMENSION.equals(memberType)) {

                Cell dimCell = this.buildCell(cell);
                dimCellList.add(dimCell);

            }

        }

        return dimCellList;

    }

    private List<Cell> getRowDimCells(Matrix matrix, int pi, int pj) {

        List<Cell> dimCellList = new LinkedList<>();
        for (int j = (pj - 1); j >= 0; j--) {

            Matrix.Cell cell = matrix.get(pi, j);
            MemberType memberType = cell.getMemberType();

            if (MemberType.DIMENSION.equals(memberType)) {

                Cell dimCell = this.buildCell(cell);
                dimCellList.add(dimCell);

            }

        }

        return dimCellList;

    }

    private List<Cell> getDimCells(Matrix matrix, int pi, int pj) {

        List<Cell> dimCellList = new LinkedList<>();
        for (int i = (pi - 1); i >= 0; i--) {

            Matrix.Cell cell = matrix.get(i, pj);
            MemberType memberType = cell.getMemberType();

            if (MemberType.DIMENSION.equals(memberType)) {

                Cell dimCell = this.buildCell(cell);
                dimCellList.add(dimCell);

            }

        }

        for (int j = (pj - 1); j >= 0; j--) {

            Matrix.Cell cell = matrix.get(pi, j);
            MemberType memberType = cell.getMemberType();

            if (MemberType.DIMENSION.equals(memberType)) {

                Cell dimCell = this.buildCell(cell);
                dimCellList.add(dimCell);

            }

        }

        return dimCellList;

    }

    /**
     * 根据CubeCell 构建 前端标准Cell
     * @param matrixCell
     * @return
     */
    private Cell buildCell(Matrix.Cell matrixCell) {

        Cell cell = new Cell();
        MemberType memberType = matrixCell.getMemberType();
        CubeMember cubeMember = matrixCell.getCubeMember();

        if (MemberType.MEASURE_VALUE.equals(memberType)) {

            Measure measure = cubeMember.getMeasure();
            cell.setMeasureType(measure.getMeasType());
            cell.setType(CellType.MEASURE_VALUE);
            cell.setCode(measure.getCode());
            cell.setName(measure.getName());

        } else if (MemberType.MEASURE.equals(memberType)) {

            Measure measure = cubeMember.getMeasure();
            cell.setMeasureType(measure.getMeasType());
            cell.setType(CellType.MEASURE);
            cell.setName(measure.getName());
            cell.setCode(measure.getCode());

        } else if (MemberType.DIMENSION.equals(memberType)) {

            Dimension dimension = cubeMember.getDimension();
            cell.setDimType(dimension.getDimType());
            cell.setId(cubeMember.getCode());
            cell.setType(CellType.DIMENSION);
            cell.setName(dimension.getName());
            cell.setCode(dimension.getCode());

        } else if (MemberType.MEASURE_GROUP.equals(memberType)) {

            BaseConfigure measureGroup = cubeMember.getMeasureGroup();
            cell.setId(measureGroup.getCode());
            cell.setType(CellType.MEASURE_GROUP);
            cell.setName(measureGroup.getName());
            cell.setCode(measureGroup.getCode());

        }

        cell.setData(matrixCell.getValue());

        return cell;

    }



}
