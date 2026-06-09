package com.graphinsight.indicator.controller;

import com.graphinsight.indicator.auto.entity.Hierarchy;
import com.graphinsight.indicator.auto.mapper.HierarchyMapper;
import com.graphinsight.indicator.model.Response;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Author: lixiaolong
 * Date: 2022/2/22
 * Desc:
 */

@RestController
@RequestMapping("/hierarchy")
public class HierarchyController {

    @Autowired
    private HierarchyMapper hierarchyMapper;

    @PostMapping("/list")
    @ApiOperation("层次列表接口")
    public Response<List<Hierarchy>> list() {
        List<Hierarchy> hierarchies = hierarchyMapper.selectList(null);
        return Response.ok(hierarchies);
    }
}
