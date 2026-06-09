package com.graphinsight.indicator.auto.entity;

import com.baomidou.mybatisplus.extension.activerecord.Model;
import lombok.Data;

@Data
public class OperateAuthBroad extends Model<OperateAuthBroad> {

    private String positionCode;

    private Integer DeptType;

    private Integer level;

}
