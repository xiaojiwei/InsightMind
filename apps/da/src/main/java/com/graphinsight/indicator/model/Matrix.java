package com.graphinsight.indicator.model;

import com.graphinsight.indicator.constant.IndicatorConstant;
import com.graphinsight.indicator.enums.MemberType;
import com.graphinsight.indicator.enums.RatioType;
import lombok.Data;

import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

@Data
public class Matrix extends BaseModel {

    /**
     * 矩阵宽
     */
    private Integer width = 0;

    /**
     * 矩阵高
     */
    private Integer height = 0;

    /**
     * 列最大深
     */
    private Integer columnMaxDeep = 0;

    /**
     * 行最大深
     */
    private Integer rowMaxDeep = 0;

    /**
     * 依赖的数据源
     */
    private CellSet cellSet;

    public static Matrix buildNull() {
        Matrix matrix = new Matrix();
        matrix.setCellSet(CellSet.buildNull());
        return matrix;
    }

    /**
     * 矩阵内容
     */
    private Map<List<Integer>, Matrix.Cell> map = new LinkedHashMap<>();

    public static Cell buildCell(int x, int y) {
        Cell cell = new Cell();
        cell.setX(x);
        cell.setY(y);
        return cell;
    }

    public static Cell buildCell() {
        return new Cell();
    }

    public Cell get(Integer x, Integer y) {

        List coordinate = new LinkedList();

        coordinate.add(x);
        coordinate.add(y);

        return this.get(coordinate);

    }

    public void set(Integer x, Integer y, Cell cell) {

        List coordinate = new LinkedList();

        coordinate.add(x);
        coordinate.add(y);

        cell.setX(x);
        cell.setY(y);

        this.getMap().put(coordinate, cell);

    }

    public Cell get(List coordinate) {
        return this.getOrBuild(coordinate);
    }

    public FindMeasureTuple buildQuerySqlByDimAndMeas(List<Integer> coordinate) {

        FindMeasureTuple findMeasTuple = new FindMeasureTuple();
        String querySql = null;
        Integer row = coordinate.get(0);
        Integer column = coordinate.get(1);

        //for i
        Integer columnMaxDeep = this.getColumnMaxDeep();
        //for j
        Integer rowMaxDeep = this.getRowMaxDeep();

        //维度、指标List
        Cell targetMeasCell = null;
        //目标维度cell
        List<Cell> dimCellList = new LinkedList<>();

        //行不动列动
        for (int k = 0; k < rowMaxDeep; k ++) {

            Cell cell = this.get(row, k);
            //目标指标定位
            if (this.isMeasure(cell)) {
                targetMeasCell = cell;
            } else if (this.isDimension(cell)) {
                dimCellList.add(cell);
            }

        }

        //列不动行动
        for (int k = 0; k < columnMaxDeep; k ++) {

            Cell cell = this.get(k, column);

            //目标指标定位
            if (this.isMeasure(cell)) {
                targetMeasCell = cell;
            } else if (this.isDimension(cell)) {
                dimCellList.add(cell);
            }
        }

        findMeasTuple.setTargetMeasCell(targetMeasCell);
        findMeasTuple.setDimCellList(dimCellList);

        return findMeasTuple;

    }

    private boolean isDimension(Cell cell) {
        return MemberType.DIMENSION.equals(cell.getMemberType());
    }

    private boolean isMeasure(Cell cell) {
        return MemberType.MEASURE.equals(cell.getMemberType());
    }

    public Cell getOrBuild(List<Integer> coordinate, CubeMember cubeMember) {

        Cell cell = this.getOrBuild(coordinate);
        //成员显示名
        cell.setValue(cubeMember.getValue());
        /**
         * 成员类型
         */
        MemberType memberType = cubeMember.getMemberType();
        cell.setMemberType(memberType);

        if (MemberType.DIMENSION.equals(memberType)) {
            //维度
            cell.setDimension(cubeMember.getDimension());
        } else if (MemberType.MEASURE.equals(memberType)) {
            //指标
            cell.setMeasure(cubeMember.getMeasure());
        } else if (MemberType.MEASURE_GROUP.equals(memberType)) {
            //成员分组
            cell.setMeasGroup(cubeMember.getMeasureGroup());
        }

        cell.setCubeMember(cubeMember);

        return cell;

    }

    public Cell getOrBuild(List<Integer> coordinate) {
        Cell cell = this.map.get(coordinate);
        if (null == cell) {
            Integer x = coordinate.get(0);
            Integer y = coordinate.get(1);
            cell = buildCell(x, y);
            this.map.put(coordinate, cell);
        }
        return cell;
    }

    @Data
    public static class Cell {

        /**
         * x 轴
         */
        private int x;

        /**
         * y 轴
         */
        private int y;

        /**
         * 列span
         */
        private int colspan;

        /**
         * 行span
         */
        private int rowspan;

        public void setRowspan(int rowspan) {
            this.rowspan = rowspan;
        }

        /**
         * 显示名称|度量值
         */
        private String value = "";

        public String getValue() {

            if (IndicatorConstant.ROLLUP_CUBE_ALL.equalsIgnoreCase(this.value)) {
                return value.replaceAll("_", "");
            } else {
                return value;
            }

        }

        /**
         * 成员类型
         */
        private MemberType memberType;

        /**
         * memberType为DIMENSION(0, "维度")时，包含的维度。
         */
        private Dimension dimension;

        /**
         * memberType为MEASURE(1, "指标")时，包含的指标。
         */
        private Measure measure;

        /**
         * memberType为MEASURE_GROUP(2, "指标分组")时，包含的指标分组。
         */
        private BaseConfigure measGroup;

        public BaseModel getBaseModel() {
            BaseModel cellModel = null;
            if (MemberType.DIMENSION.equals(this.memberType)) {
                cellModel = this.cubeMember.getDimension();
            } else if (MemberType.MEASURE.equals(this.memberType)) {
                cellModel = new BaseModel();
                cellModel.setCode("");//指标不排序原则上不会出现
            } else if (MemberType.MEASURE_GROUP.equals(this.memberType)) {
                cellModel = new BaseModel();
                cellModel.setCode(this.cubeMember.getCode());
            } else if (MemberType.MEASURE_VALUE.equals(this.memberType)) {
                cellModel = this.cubeMember.getMeasure();
            }
            return cellModel;
        }

        /**
         * 数据来源于的member
         */
        private CubeMember cubeMember;

        /**
         * 父元素
         */
        private Cell prevCell;

        /**
         * 左边cell
         */
        private Cell leftCell;

        /**
         * 比率
         */
        private List ratioList = new LinkedList<Ratio>();

        public static Ratio buildRatio() {
            return new Ratio();
        }

        /**
         * 同环比数据
         */
        @Data
        public static class Ratio {

            public Ratio() {};

            /**
             * 同环比类型
             */
            private RatioType ratioType;

            /**
             * 值
             */
            private String value;

            /**
             * 比率
             */
            private String ratio;

        }

    }

    /**
     * 获得修正高
     * @return
     */
    public int getCorrectHeight() {

        Axis columnAxis = cellSet.getColumnAxis();
        Integer columnMaxDeep = columnAxis.getMaxDeep();
        return columnMaxDeep == 0 ? 1 : 0;

    }

    /**
     * 获得修正宽
     * @return
     */
    public int getCorrectWidth() {

        Axis rowAxis = cellSet.getRowAxis();
        Integer rowMaxDeep = rowAxis.getMaxDeep();
        return rowMaxDeep == 0 ? 1 : 0;

    }

}
