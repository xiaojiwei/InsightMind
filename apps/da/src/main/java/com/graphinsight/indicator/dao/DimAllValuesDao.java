package com.graphinsight.indicator.dao;

import com.graphinsight.indicator.model.DimAllValues;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public interface DimAllValuesDao extends JpaSpecificationExecutor, JpaRepository<DimAllValues, Long> {

    @Transactional
    @Modifying
    @Query(value = "TRUNCATE dim_all_values", nativeQuery = true)
    void truncateAll();

}