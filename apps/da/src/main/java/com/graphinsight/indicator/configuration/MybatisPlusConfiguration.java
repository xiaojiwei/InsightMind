package com.graphinsight.indicator.configuration;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.handler.TableNameHandler;
import com.baomidou.mybatisplus.extension.plugins.inner.DynamicTableNameInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.graphinsight.indicator.constant.IndicatorConstant;
import com.graphinsight.indicator.interceptor.MybatisplusOperateInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

/**
 * @Author: lixiaolong
 * @Description:
 * @Date: 2021/11/16
 */
@Configuration
public class MybatisPlusConfiguration {

    @Value("${dimWithoutTableName}")
    private String dimWithoutTableName;
    @Value("${dimWithoutSchemaName}")
    private String dimWithoutSchemaName;

    /**
     * 新的分页插件,一缓和二缓遵循mybatis的规则,需要设置 MybatisConfiguration#useDeprecatedExecutor = false 避免缓存出现问题
     */
    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        interceptor.addInnerInterceptor(dynamicTableNameInterceptor());
        interceptor.addInnerInterceptor(mybatisplusOperateInterceptor());
        return interceptor;
    }

    @Bean
    public DynamicTableNameInnerInterceptor dynamicTableNameInterceptor(){
        DynamicTableNameInnerInterceptor dynamicTableNameInnerInterceptor = new DynamicTableNameInnerInterceptor();
        Map<String, TableNameHandler> tableNameHandlerMap = new HashMap<>();
        tableNameHandlerMap.put("dim_without_table", (sql, tableName) -> IndicatorConstant.DIM_WITHOUT_TABLE_DOIRS_SCHEMA + "." + dimWithoutTableName);
        dynamicTableNameInnerInterceptor.setTableNameHandlerMap(tableNameHandlerMap);
        return dynamicTableNameInnerInterceptor;
    }

    @Bean
    public MybatisplusOperateInterceptor mybatisplusOperateInterceptor(){
        MybatisplusOperateInterceptor interceptor = new MybatisplusOperateInterceptor();
        return interceptor;
    }

}
