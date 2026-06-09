package com.graphinsight.indicator.dao;

import com.graphinsight.indicator.model.JavaInfo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Component;

@Component
public interface JavaInfoDao extends JpaSpecificationExecutor, JpaRepository<JavaInfo, Long> {

}