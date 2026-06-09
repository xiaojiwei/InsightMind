package com.graphinsight.indicator.dao;

import com.graphinsight.indicator.model.Filter;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Component;


@Component
public interface FilterDao extends JpaSpecificationExecutor, JpaRepository<Filter, Long> {
}
