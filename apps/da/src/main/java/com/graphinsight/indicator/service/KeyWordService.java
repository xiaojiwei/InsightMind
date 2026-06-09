package com.graphinsight.indicator.service;

import com.graphinsight.indicator.model.PageData;

public interface KeyWordService {

    /**
     * 开始执行
     * @param word
     */
    PageData doAction(String word);

    PageData doAction2(String word);

}
