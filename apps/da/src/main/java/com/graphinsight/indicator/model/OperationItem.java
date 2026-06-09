package com.graphinsight.indicator.model;

import com.graphinsight.indicator.enums.ItemType;
import lombok.Data;

@Data
public class OperationItem extends BaseModel {

    /**
     * 操作类型
     */
    public static final String OPERATOR = ItemType.OPERATOR.getName();//"operator";

    /**
     * 指标类型
     */
    public static final String OPERAND = ItemType.OPERAND.getName();//"operand";

    /**
     * 常数项
     */
    public static final String CONSTANT = ItemType.CONSTANT.getName();//"constant";

    /**
     * 本运算项类型, operator-运算符,operand-运算数,constant-常数
     */
    private String operatingType;

    /**
     * 操作符，当operatingType为operator时，非空
     */
    private String operator;

    /**
     * 操作数，当operatingType为operand时，非空
     */
    private MeasureBasicInfo operand;

    /**
     * 常数，当operationType为constant时，非空
     */
    private Double constant;

    public static OperationItem buildOperatingItem(String operatingType, Object value) {
        if (value == null) { return null; }
        OperationItem item = new OperationItem();
        item.setOperatingType(operatingType);
        if (OPERATOR.equals(operatingType)) {
            item.setOperator((String) value);
        } else if (OPERAND.equals(operatingType)) {
            item.setOperand((MeasureBasicInfo) value);
        } else if (CONSTANT.equals(operatingType)) {
            item.setConstant((Double) value);
        } else {
            throw new RuntimeException("not recognized operatingType: " + operatingType);
        }
        return item;
    }

    /**
     * 0-指标，1-常数，2-操作符
     * @see ItemType
     * @param type
     * @return
     */
    public static String typeIntToString(Integer type) {
        if (ItemType.OPERAND.getValue().equals(type)) {
            return OPERAND;
        } else if (ItemType.CONSTANT.getValue().equals(type)) {
            return CONSTANT;
        } else if (ItemType.OPERATOR.getValue().equals(type)) {
            return OPERATOR;
        } else {
            throw new RuntimeException("not recognized operatingType: " + type);
        }
    }

    /**
     * 0-指标，1-常数，2-操作符
     * @see ItemType
     * @param type
     * @return
     */
    public static Integer typeStringToInt(String type) {
        if (OPERATOR.equals(type)) {
            return ItemType.OPERATOR.getValue();
        } else if (OPERAND.equals(type)) {
            return ItemType.OPERAND.getValue();
        } else if (CONSTANT.equals(type)) {
            return ItemType.CONSTANT.getValue();
        } else {
            throw new RuntimeException("not recognized operatingType: " + type);
        }

    }

    @Data
    public static class MeasureBasicInfo extends BaseModel {

        /**
         * 指标Code
         */
        private String measCode;

        /**
         * 指标名称
         */
        private String measName;

        public MeasureBasicInfo(Long id, String measCode, String measName) {
            this.id = id;
            this.measCode = measCode;
            this.measName = measName;
        }
    }
}
