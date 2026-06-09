package com.graphinsight.indicator.dao;

import com.graphinsight.indicator.model.CacheReloadTask;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public interface CacheReloadTaskDao extends JpaSpecificationExecutor, JpaRepository<CacheReloadTask, Long> {

    @Query(value = "select crt.cache_reload_status from cache_reload_task as crt where crt.v_key=? order by crt.update_date desc limit 1  for update", nativeQuery = true)
    Integer findByLockKey(String key);

    List<CacheReloadTask> findByKey(String key);

}