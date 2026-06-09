package com.graphinsight.indicator.dao;

import com.graphinsight.indicator.model.Auth;
import com.graphinsight.indicator.model.CacheReloadTask;
import com.graphinsight.indicator.model.SuperAdmin;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public interface SuperAdminDao extends JpaSpecificationExecutor, JpaRepository<SuperAdmin, Long> {

    List<SuperAdmin> findByEmpCode(String key);

}