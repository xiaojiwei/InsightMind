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
public class MeasureMonitorDimGroup extends Model<MeasureMonitorDimGroup> {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long ruleDetailId;

    private String dimensionCode;

    private Integer seq;
}
