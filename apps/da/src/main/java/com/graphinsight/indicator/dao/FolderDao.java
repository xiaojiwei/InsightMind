package com.graphinsight.indicator.dao;

import com.graphinsight.indicator.model.Folder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Component;

@Component
public interface FolderDao extends JpaSpecificationExecutor, JpaRepository<Folder, Long> {

}