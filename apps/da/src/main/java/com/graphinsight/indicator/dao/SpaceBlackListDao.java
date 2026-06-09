package com.graphinsight.indicator.dao;

import com.graphinsight.indicator.model.Space;
import com.graphinsight.indicator.model.SpaceBlacklist;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Component;

@Component
public interface SpaceBlackListDao extends JpaSpecificationExecutor, JpaRepository<SpaceBlacklist, Long> {

    @Query(value = "select count(1) from space_blacklist as sbl where sbl.employee_code=? and sbl.space_id=?", nativeQuery = true)
    Integer findByEmpCodeAndSpaceId(String empCode, Long spaceId);

}