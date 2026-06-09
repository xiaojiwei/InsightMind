package com.graphinsight.indicator.auto.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.extension.activerecord.Model;
import lombok.Data;
import org.joda.time.DateTime;

@Data
public class MeasureMonitorAlertLog extends Model<MeasureMonitorAlertLog> {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long monitorId;

    private String content;

    private String alertTime;
}
