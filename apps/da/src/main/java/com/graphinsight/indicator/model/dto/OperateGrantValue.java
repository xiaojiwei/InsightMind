package com.graphinsight.indicator.model.dto;

import com.graphinsight.indicator.model.vo.IndicatorOperateTree;
import lombok.Data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Date: 2022/5/23
 * Desc: 运营架构授权维值
 */
@Data
public class OperateGrantValue {

    /**
     * 运营架构授权配置表主键
     */
    private Long operateGrantConfigId;

    /**
     * 上下文变量名
     */
    private String name;

    /**
     * 运营架构授权对应维值(key:、code、id等)
     */
    private Set<String> keys = new HashSet<>();


    /**
     * key: code
     * value: 对应中文名
     * 例：100100 - 售后服务
     */
    private Map<String,Object> kvMap = new HashMap<>();


    private List<IndicatorOperateTree> orgTree = new ArrayList<>();

}
