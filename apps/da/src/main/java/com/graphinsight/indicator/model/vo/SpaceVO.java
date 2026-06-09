package com.graphinsight.indicator.model.vo;

import com.fasterxml.jackson.annotation.JsonIncludeProperties;
import com.graphinsight.indicator.enums.AuthElementType;
import com.graphinsight.indicator.enums.AuthObjectType;
import com.graphinsight.indicator.enums.RoleType;
import com.graphinsight.indicator.model.*;
import lombok.Data;
import org.hibernate.annotations.Cascade;
import org.springframework.util.CollectionUtils;

import javax.persistence.*;
import java.util.*;

@Data
public class SpaceVO extends BaseModel {

    private Long id;

    private String name;

    private String remarks;

    private Set<KeyValueVO> classificationSet = new LinkedHashSet<>();

    /**
     * 空间拥有者
     */
    private Set<KeyValueVO> spaceOwnerSet = new LinkedHashSet<>();

    private Set<KeyValueVO> deptSet = new LinkedHashSet<>();

    private Set<RoleType> roleTypeSet = new HashSet<>();

    public static SpaceVO build(Space space) {

        SpaceVO spaceVO = new SpaceVO();

        spaceVO.setId(space.getId());
        spaceVO.setCode(space.getCode());
        spaceVO.setName(space.getName());
        spaceVO.setRemarks(space.getRemarks());

        Set<SpaceDepartment> deptSet = space.getDeptSet();
        if (!CollectionUtils.isEmpty(deptSet)) {
            for (SpaceDepartment dept : deptSet) {
                KeyValueVO keyValueVO = new KeyValueVO();
                keyValueVO.setName(dept.getName());
                keyValueVO.setCode(dept.getCode());
                keyValueVO.setId(dept.getId());
                spaceVO.getDeptSet().add(keyValueVO);
            }
        }

        Set<Classification> classificationSet = space.getClassificationSet();
        if (!CollectionUtils.isEmpty(classificationSet)) {
            for (Classification classif : classificationSet) {

                KeyValueVO keyValueVO = new KeyValueVO();
                keyValueVO.setName(classif.getName());
                keyValueVO.setCode(classif.getCode());
                keyValueVO.setId(classif.getId());
                spaceVO.getClassificationSet().add(keyValueVO);
            }
        }

        Set<SpaceOwner> spaceOwnerSet = space.getSpaceOwnerSet();
        if (!CollectionUtils.isEmpty(spaceOwnerSet)) {
            for (SpaceOwner spaceOwner : spaceOwnerSet) {

                KeyValueVO keyValueVO = new KeyValueVO();
                keyValueVO.setName(spaceOwner.getName());
                keyValueVO.setCode(spaceOwner.getCode());
                keyValueVO.setId(spaceOwner.getId());
                spaceVO.getSpaceOwnerSet().add(keyValueVO);
            }
        }
        spaceVO.setCreateDate(space.getCreateDate());
        spaceVO.setRoleTypeSet(space.getRoleTypeSet());

        return spaceVO;
    }

}