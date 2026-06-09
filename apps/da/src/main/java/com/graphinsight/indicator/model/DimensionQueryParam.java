package com.graphinsight.indicator.model;

import com.graphinsight.indicator.enums.CacheStrategy;
import lombok.Data;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * 维度查询参数
 */
@Data
public class DimensionQueryParam extends BaseModel {

    /**
     * 唯一请求标识
     */
    private String traceId;

    /**
     * 是否从缓存中获取,默认从缓存结果中查询
     */
    private Boolean cacheTable = true;

    private String md5Key;

    /**
     * 缓存策略
     */
    private CacheStrategy cacheStrategy = CacheStrategy.QUERY_UPDATE;

    /**
     * 维度筛选项
     */
    private List<Filter> filterList = new ArrayList<Filter>();

    /**
     * 当前页
     */
    private Integer pageNo = 1;

    /**
     * 页大小
     */
    private Integer pageSize = 200;

    /**
     * 空间Id
     */
    private Long spaceId;

    /**
     * 查询身份
     */
    private String username;

    /**
     * 是否限制查询条件
     */
    private boolean isAuth = true;

    /**
     * 是否授权页面
     */
    private boolean isGrade = false;

    public void setIsGrade(boolean grade) {
        isGrade = grade;
    }

    public void setIsAuth(boolean auth) {
        isAuth = auth;
    }

    public String getkey() {
        StringBuilder keyBuilder = new StringBuilder();
        keyBuilder.append(this.cacheTable)
                    .append(this.cacheStrategy)
                .append(this.pageNo)
                .append(this.pageSize)
                .append(spaceId)
                .append(username)
                .append(this.isAuth)
                .append(isGrade);
        if (!CollectionUtils.isEmpty(this.filterList)) {
            for (Filter filter : this.filterList) {
                if (null != filter) {
                    keyBuilder.append(filter.getFilterKey());
                }

            }

        }

        return keyBuilder.toString();
    }

}
