package com.graphinsight.indicator.dao;

import com.graphinsight.indicator.model.DataSource;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public interface DataSourceDao extends JpaSpecificationExecutor, JpaRepository<DataSource, Long> {

    List<DataSource> findAllByName(String name);


    /**
     * 查询包含（维度|指标）code的数据源
     * @param codeList
     * @return
     */
    @Query(value = "select COUNT(DISTINCT t0.data_source_id) from base_configure as t0 where t0.code in :codeList", nativeQuery = true)
    Long getCountByDimCodeAndMeasCode(@Param("codeList")  List<String> codeList);

}