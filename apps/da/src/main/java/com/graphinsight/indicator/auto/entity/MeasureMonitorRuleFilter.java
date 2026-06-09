package com.graphinsight.indicator.auto.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.extension.activerecord.Model;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MeasureMonitorRuleFilter extends Model<MeasureMonitorRuleFilter> {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long filterId;

    private Long ruleDetailId;

    private Integer seq;
}
