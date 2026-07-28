package com.graphinsight.indicator.controller;


import com.graphinsight.indicator.auto.entity.OperateGrantConfig;
import com.graphinsight.indicator.auto.service.IOperateGrantConfigService;
import com.graphinsight.indicator.model.Response;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * <p>
 *  前端控制器
 * </p>
 *
 * @since 2022-05-23
 */
@RestController
@RequestMapping("/operateGrantConfig/")
public class OperateGrantConfigController {

    @Autowired
    IOperateGrantConfigService operateGrantConfigService;

    @ApiOperation(value = "获取列表,前端只展示name即可")
    @GetMapping("/list")
    public Response<List<OperateGrantConfig>> list(){
        return Response.ok(operateGrantConfigService.list());
    }

    @ApiOperation(value = "保存")
    @PostMapping("/save")
    public Response save(@RequestBody OperateGrantConfig operateGrantConfig){
        return Response.ok(operateGrantConfigService.save(operateGrantConfig));
    }

    @GetMapping("/delete/{id}")
    public Response del(@PathVariable("id") Long id){
        return Response.ok(operateGrantConfigService.removeById(id));
    }

    @ApiOperation(value = "更新")
    @PostMapping("/update")
    public Response update(@RequestBody OperateGrantConfig operateGrantConfig){
        return Response.ok(operateGrantConfigService.updateById(operateGrantConfig));
    }


}
