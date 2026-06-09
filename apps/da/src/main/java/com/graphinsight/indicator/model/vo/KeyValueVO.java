package com.graphinsight.indicator.model.vo;

import com.graphinsight.indicator.model.*;
import lombok.Data;

@Data
public class KeyValueVO extends BaseModel {

    private Long id;

    private String code;

    private String name;

    private String remarks;

}