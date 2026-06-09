package com.graphinsight.indicator.lax.filter.function.mode;

import com.graphinsight.indicator.enums.LodType;
import lombok.Data;

import java.util.List;

@Data
public class LodDim {

    private LodType lodType;

    private List<String> dimCodeList;

}
