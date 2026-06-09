package com.graphinsight.indicator.model.vo;

import com.graphinsight.indicator.enums.DecimalFormatType;
import lombok.Data;

import java.io.Serializable;


@Data
public class AiFrontFormatVo implements Serializable {


    // 关键字
    private Integer type = 0;
    private Integer decimalPlaces;
    private DecimalFormatType dataScale;
    private Boolean useThousandths = true;

}
