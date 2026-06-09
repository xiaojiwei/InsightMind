package com.graphinsight.indicator.util;

import org.springframework.util.CollectionUtils;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * Author: lixiaolong
 * Date: 2022/4/19
 * Desc:
 */
public class IndicatorCollectionUtil {

    /**
     * 判断两个集合是否有交叉
     * @param c1
     * @param c2
     * @return
     */
    public static boolean hasCross(Collection c1, Collection c2){
        if (c1 == null || c2 == null){
            return false;
        }
        Set set1 = new HashSet<>(c1);
        Set set2 = new HashSet<>(c2);
        set1.retainAll(set2);
        return !CollectionUtils.isEmpty(set1);
    }
}
