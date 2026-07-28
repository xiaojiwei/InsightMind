package com.graphinsight.indicator.model.vo;

import lombok.Data;

import java.util.List;

/**
 * Date: 2022/11/28
 * Desc: 授权对象
 */
@Data
public class AiSplitTextVo {

    private List<Object> tokens;


    @Data
    public static class Tokens {
        private String word;
        private String showType;
        private String prop;
        private boolean lock;
        private int unitIdx;
        private int tokenSource;
    }

}
