package com.graphinsight.indicator.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Date: 2022/5/17
 * Desc:
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class UserDTO {

    private Integer id;

    private String username;

    private String nickname;

    private String email;

    private DepartmentDTO department;

    private String jobNumber;

    private String departmentNamePath;

    private String avatar;
}
