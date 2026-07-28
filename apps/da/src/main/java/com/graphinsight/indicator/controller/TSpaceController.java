package com.graphinsight.indicator.controller;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.dynamic.datasource.toolkit.DynamicDataSourceContextHolder;
import com.graphinsight.indicator.auto.entity.TSpace;
import com.graphinsight.indicator.auto.service.ITSpaceService;
import com.graphinsight.indicator.enums.JdbcDataSourceType;
import com.graphinsight.indicator.model.Response;
import com.graphinsight.indicator.model.vo.TSpaceVO;
import com.graphinsight.indicator.service.SpaceService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;

/**
 * Date: 2022/9/19
 * Desc:
 */
@Slf4j
@RestController
@RequestMapping("/space")
public class TSpaceController {

    @Resource
    ITSpaceService tSpaceService;
    @Resource
    SpaceService spaceService;

    @DS("mysql")
    @GetMapping("/list")
    public Response listSpace(){
        DynamicDataSourceContextHolder.push(JdbcDataSourceType.MYSQL.getDesc());
        List<TSpace> spaces = tSpaceService.list();

        List<TSpaceVO> result = new ArrayList<>();
        return Response.ok(spaces);
    }
}
