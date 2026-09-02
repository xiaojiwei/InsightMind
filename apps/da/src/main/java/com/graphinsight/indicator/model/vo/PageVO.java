package com.graphinsight.indicator.model.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * @Description:
 * @Date: 2021/11/16
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class PageVO<T> extends BaseVO {
    private Long total;
    private List<T> data;
}
