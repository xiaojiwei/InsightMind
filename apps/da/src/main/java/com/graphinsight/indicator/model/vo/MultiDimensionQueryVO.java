package com.graphinsight.indicator.model.vo;

import com.graphinsight.indicator.model.Filter;
import lombok.Data;

import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import java.util.LinkedList;
import java.util.Set;

/**
 * Date: 2022/7/8
 * Desc:
 */
@Data
public class MultiDimensionQueryVO {



    @NotNull(message = "任务ID不能为空")
    private Long taskId;

    /**
     * 指标code
     */
    @NotNull(message = "指标code不能为空")
    private String measCode;

    /**
     * 分组维度code
     */
    @NotEmpty(message = "维度列不能为空")
    private Set<String> colDimCodes;

    private LinkedList<Filter> filterList = new LinkedList<>();

    /**
     * 过滤维度code
     */
    @NotNull(message = "过滤维度不能为空")
    private String filterDimCode;

    /**
     * 本期
     */
    @NotNull(message = "本期值不能为空")
    private String currentDate;

    /**
     * 基期
     */
    @NotNull(message = "基期值不能为空")
    private String baseDate;

    /**
     * 空间ID
     */
    @NotNull(message = "空间ID不能为空")
    private Long spaceId;

    private boolean downloadFile;

    private Integer pageNo = 1;

    private Integer pageSize = 20;

    private LinkedList<OrderVO> orderList = new LinkedList<>();

    // public LinkedList<OrderVO> getOrderList() {
    //     if (CollectionUtils.isEmpty(orderList)){
    //         OrderVO o1 = new OrderVO();
    //         o1.setCode(IndicatorConstant.DELTA_VALUE_RATE_CODE);
    //         o1.setSortType(SortType.ASC.getCode());
    //         OrderVO o2 = new OrderVO();
    //         o2.setCode(IndicatorConstant.CONTRIBUION_CODE);
    //         o2.setSortType(SortType.ASC.getCode());
    //
    //         OrderVO o3 = new OrderVO();
    //         o3.setCode(IndicatorConstant.BASE_VALUE_CODE);
    //         o3.setSortType(SortType.DESC.getCode());
    //         orderList.add(o1);
    //         orderList.add(o2);
    //         orderList.add(o3);
    //     }
    //     return orderList;
    // }
    //
    // public void setOrderList(LinkedList<OrderVO> orderList) {
    //     this.orderList = orderList;
    // }
}
