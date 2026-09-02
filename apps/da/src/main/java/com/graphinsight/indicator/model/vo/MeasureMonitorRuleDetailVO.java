package com.graphinsight.indicator.model.vo;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.graphinsight.indicator.enums.CompareWayEnum;
import com.graphinsight.indicator.exception.IndicatorParamNotValidException;
import com.graphinsight.indicator.model.DimWithValues;
import com.graphinsight.indicator.model.Filter;
import com.graphinsight.indicator.util.NumberFormatUtil;
import lombok.Data;

import javax.validation.constraints.NotNull;
import java.util.List;

/**
 * Date: 2022/10/11
 * Desc:
 */
@Data
public class MeasureMonitorRuleDetailVO {

    /**
     * 主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 规则ID
     */
    private Long ruleId;

    /**
     * 0-AND 1-OR
     */
    @NotNull(message = "逻辑关系不能为空")
    private Integer logicType = 0;

    /**
     * 指标code
     */
    @NotNull(message = "指标Code不能为空")
    private String measCode;

    /**
     * 时间维度code
     */
    @NotNull(message = "维度Code不能为空")
    private String dimCode;


    private List<DimWithValues> dimGroup;


    private List<Filter> filters;

    @NotNull(message = "统计周期不能为空")
    private Integer statPeriod;

    /**
     * 同环比类型参考枚举类 RatioType
     */
    @NotNull(message = "同环比类型不能为空")
    private Integer ratioType;

    /**
     * 阈值类型0-固定值 1-目标值 2-预测值
     */
    private Integer thresholdType;

    /**
     * 阈值
     */
    @NotNull(message = "阈值不能为空")
    private String thresholdValue;

    /**
     * 比较方式
     */
    @NotNull(message = "比较方式不能为空")
    private Integer compareWay;

    /**
     * 父ID
     */
    private Long parentId;

    /**
     * 顺序
     */
    private Integer seq;

    /**
     * 规则详情
     */
    private List<MeasureMonitorRuleDetailVO> children;

    public void check(){
        if (thresholdValue == null){
            throw IndicatorParamNotValidException.error("阈值不能为空");
        }
        if (compareWay == CompareWayEnum.BETWEEN.getCode() || compareWay == CompareWayEnum.NOT_BETWEEN.getCode()){
            String[] split = thresholdValue.split(",");
            if (split.length != 2){
                throw IndicatorParamNotValidException.error("阈值格式错误" + thresholdValue);
            }
            if (! NumberFormatUtil.isNumberic(split[0]) || ! NumberFormatUtil.isNumberic(split[1])){
                throw IndicatorParamNotValidException.error("阈值格式错误" + thresholdValue);
            }
        } else {
            boolean numberic = NumberFormatUtil.isNumberic(thresholdValue);
            if (! numberic){
                throw IndicatorParamNotValidException.error("阈值格式错误" + thresholdValue);
            }
        }
    }
}
