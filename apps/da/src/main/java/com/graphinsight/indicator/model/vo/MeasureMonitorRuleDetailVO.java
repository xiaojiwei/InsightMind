package com.graphinsight.indicator.model.vo;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.graphinsight.indicator.enums.CompareWayEnum;
import com.graphinsight.indicator.exception.IndicatorParamNotValidException;
import com.graphinsight.indicator.model.DimWithValues;
import com.graphinsight.indicator.model.Filter;
import com.graphinsight.indicator.util.NumberFormatUtil;
import io.swagger.annotations.ApiModelProperty;
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
    @ApiModelProperty(value = "逻辑关系 0-AND 1-OR")
    private Integer logicType = 0;

    /**
     * 指标code
     */
    @NotNull(message = "指标Code不能为空")
    @ApiModelProperty(value = "指标Code")
    private String measCode;

    /**
     * 时间维度code
     */
    @NotNull(message = "维度Code不能为空")
    @ApiModelProperty(value = "时间维度Code")
    private String dimCode;


    @ApiModelProperty(value = "维度分组")
    private List<DimWithValues> dimGroup;


    @ApiModelProperty(value = "过滤器")
    private List<Filter> filters;

    @NotNull(message = "统计周期不能为空")
    @ApiModelProperty(value = "统计周期，0上周期，1本周期")
    private Integer statPeriod;

    /**
     * 同环比类型参考枚举类 RatioType
     */
    @NotNull(message = "同环比类型不能为空")
    @ApiModelProperty(value = "同环比类型参考 RatioType")
    private Integer ratioType;

    /**
     * 阈值类型0-固定值 1-目标值 2-预测值
     */
    @ApiModelProperty(value = "阈值类型 目前就传0即可")
    private Integer thresholdType;

    /**
     * 阈值
     */
    @NotNull(message = "阈值不能为空")
    @ApiModelProperty(value = "阈值，如果比较方式是区间，startValue和endValue用逗号隔开比如: 1,2 表示大于等于1小于等于2")
    private String thresholdValue;

    /**
     * 比较方式
     */
    @NotNull(message = "比较方式不能为空")
    @ApiModelProperty(value = "参见枚举值 CompareWayEnum")
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
