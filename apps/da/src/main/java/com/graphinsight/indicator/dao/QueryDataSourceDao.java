package com.graphinsight.indicator.dao;

import com.graphinsight.indicator.model.DataSource;
import com.graphinsight.indicator.model.QueryDataSource;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public interface QueryDataSourceDao extends JpaSpecificationExecutor, JpaRepository<QueryDataSource, Long> {

}