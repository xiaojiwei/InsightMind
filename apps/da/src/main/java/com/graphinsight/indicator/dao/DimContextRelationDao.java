package com.graphinsight.indicator.dao;

import com.graphinsight.indicator.model.DataSource;
import com.graphinsight.indicator.model.DimContextRelation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public interface DimContextRelationDao extends JpaSpecificationExecutor, JpaRepository<DimContextRelation, Long> {

    List<DimContextRelation> findAllByCode(String code);

}