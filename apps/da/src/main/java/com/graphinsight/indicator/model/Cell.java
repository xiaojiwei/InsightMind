package com.graphinsight.indicator.model;

import com.graphinsight.indicator.enums.*;
import com.graphinsight.indicator.util.StringUtil;
import lombok.Data;

import java.util.LinkedList;
import java.util.List;

/**
 * 前端数据单元格内容
 */
@Data
public class Cell {

    /**
     * 单元格内容
     * @see CellType
     *  DIMENSION("0"),
     *  MEASURE("1"),
     *  MEASURE_GROUP("2");
     */
    private CellType type;

    /**
     * ViewType
     * @see ViewType
     */
    private ViewType viewType;

    /**
     * 根据CellType类型标识维度或指标code
     */
    private String code;

    /**
     * 名称
     */
    private String name;

    /**
     * @see DimType
     * 如果是维度则表示维度类型
     */
    private DimType dimType;

    /**
     * @see MeasureType
     * 如果是指标则表示指标类型
     */
    private MeasureType measureType;

    /**
     * 维度数据的唯一Id，cell为指标时此项为null。
     */
    private String id;

    /**
     * 维度或指标的数据值
     */
    private String data;

    /**
     * 比率
     */
    private List ratioList = new LinkedList<Ratio>();

    public static Ratio buildRatio() {
        return new Ratio();
    }

    @Data
    public static class Ratio {

        public Ratio() {};

        /**
         * 同环比类型
         */
        private String ratioType;

        /**
         * 值
         */
        private String value;

        /**
         * 比率
         */
        private String ratio;

        public String getValue() {
            if (null == this.value || StringUtil.isEmpty(this.value) || this.value.indexOf("NaN") == 0 || this.value.indexOf("Infinity") == 0) {
                this.value = "-";
            }
            return value;
        }

        public String getRatio() {
            if (null == this.ratio || StringUtil.isEmpty(this.value) || this.ratio.indexOf("NaN") == 0 || this.ratio.indexOf("Infinity") == 0) {
                this.ratio = "-";
            }
            return ratio;
        }
    }
    public void setData(String data) {

        if (StringUtil.isEmpty(data)) {
            this.data = "-";
        } else {
            this.data = data;
        }

    }

    public String getData() {

        if ("NaN".equalsIgnoreCase(this.data) || "Infinity".equalsIgnoreCase(this.data)) {
            this.data = "-";
        }
        return data;
    }

    /**
     * 行坐标
     */
    private Integer row;

    /**
     * 列坐标
     */
    private Integer column;

    /**
     * 行span
     */
    private Integer rowSpan;

    /**
     * 列span
     */
    private Integer colSpan;

    private String relateFactorNum;

    private String info;

}
