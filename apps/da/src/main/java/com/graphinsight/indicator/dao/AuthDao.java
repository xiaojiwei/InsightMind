package com.graphinsight.indicator.dao;

import com.graphinsight.indicator.model.Auth;
import com.graphinsight.indicator.model.Space;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Component;

@Component
public interface AuthDao extends JpaSpecificationExecutor, JpaRepository<Auth, Long> {

}