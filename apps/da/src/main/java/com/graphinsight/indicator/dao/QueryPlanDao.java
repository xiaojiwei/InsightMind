package com.graphinsight.indicator.dao;

import com.graphinsight.indicator.model.QueryPlan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Component;

@Component
public interface QueryPlanDao extends JpaSpecificationExecutor, JpaRepository<QueryPlan, Long> {

    QueryPlan findByKey(String key);

}