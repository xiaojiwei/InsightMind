package com.graphinsight.indicator.dao;

import com.graphinsight.indicator.model.Space;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Component;

@Component
public interface SpaceDao extends JpaSpecificationExecutor, JpaRepository<Space, Long> {

    @Query(value = "select t.id from t_space as t where t.id=? limit 1", nativeQuery = true)
    Long findId(Long id);

}