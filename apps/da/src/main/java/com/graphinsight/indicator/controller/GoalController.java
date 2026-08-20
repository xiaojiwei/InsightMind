package com.graphinsight.indicator.controller;

import com.graphinsight.indicator.annotation.OperateLog;
import com.graphinsight.indicator.auto.entity.Dimension;
import com.graphinsight.indicator.auto.mapper.GoalMapper;
import com.graphinsight.indicator.enums.ViewType;
import com.graphinsight.indicator.exception.GoalNotUniqueException;
import com.graphinsight.indicator.exception.GoalValidateException;
import com.graphinsight.indicator.manager.DimensionManager;
import com.graphinsight.indicator.model.Response;
import com.graphinsight.indicator.model.dto.GoalDTO;
import com.graphinsight.indicator.model.dto.GoalDateDimDTO;
import com.graphinsight.indicator.model.dto.GoalMeasureBaseInfo;
import com.graphinsight.indicator.model.vo.BaseInfo;
import com.graphinsight.indicator.model.vo.GoalAddVO;
import com.graphinsight.indicator.model.vo.GoalQueryVO;
import com.graphinsight.indicator.service.ChartQueryService;
import com.graphinsight.indicator.service.GoalService;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedList;
import java.util.List;

@RestController
@RequestMapping("/goalManagement")
public class GoalController {

    @Autowired
    GoalService goalService;

    @Autowired
    GoalMapper goalMapper;

    @Autowired
    ChartQueryService chartQueryService;

    @Autowired
    DimensionManager dimensionManager;

    @OperateLog
    @ApiOperation("新增目标")
    @PostMapping("/add")
    public Response add(@RequestBody @Validated GoalAddVO goalAddVo){
        try {
            List<GoalDTO> list = goalService.add(goalAddVo);
            return Response.ok(list);
        }catch (GoalNotUniqueException e){
            return Response.error(e.getMessage());
        }
    }

    @GetMapping("/getNaturalDate")
    public Response getNaturalDate(){
        List<Dimension> list = dimensionManager.listNaturalDimension();
        List<BaseInfo> res = new LinkedList<>();
        for (Dimension dimension:list){
            BaseInfo baseInfo = new BaseInfo();
            BeanUtils.copyProperties(dimension,baseInfo);
            baseInfo.setCnName(ViewType.findByInt(baseInfo.getViewType()).get().getName());
            res.add(baseInfo);
        }
        return Response.ok(res);
    }

    @ApiOperation("筛选器获取日期维度")
    @GetMapping("{spaceId}/getDateDim")
    public Response getDateDim(@PathVariable Integer spaceId,@RequestParam(required = false) String measureCode){
        List<GoalDateDimDTO> res = goalService.getDateDim(spaceId,measureCode);
        return Response.ok(res);
    }

    @ApiOperation("筛选器，日期维度获取指标")
    @GetMapping("{spaceId}/getMeasure")
    public Response getMeasure(@PathVariable Integer spaceId,
                               @RequestParam(required = false) Integer dateType,
                               @RequestParam(required = false) String dateValue){
        List<GoalMeasureBaseInfo> res = goalService.getMeasure(spaceId,dateType,dateValue);
        return Response.ok(res);
    }


    @ApiOperation("新增子目标获取维度")
    @GetMapping("/{goalId}/dimension")
    public Response goalDimension(@PathVariable Integer goalId){
        List<BaseInfo> res = goalService.getDimForSubGoal(goalId);
        return Response.ok(res);
    }

    @ApiOperation("查询目标")
    @GetMapping("/{spaceId}/query")
    public Response queryGoal(@PathVariable Integer spaceId,
                              @RequestParam(value = "measureCode",required = false) String measureCode,
                              @RequestParam(value = "dateType",required = false) Integer dateType,
                              @RequestParam(value = "dateValue",required = false) String dateValue) {
        GoalQueryVO goalQueryVO = new GoalQueryVO(spaceId,measureCode,dateType,dateValue);
        List<GoalDTO> data = goalService.query(goalQueryVO);
        return Response.ok(data);
    }

    @ApiOperation("按ID查询目标")
    @GetMapping("/detail/{goalId}")
    public Response detail(@PathVariable Long goalId) {
        GoalDTO data = goalService.detail(goalId);
        if (data == null) {
            return Response.error("目标不存在");
        }
        return Response.ok(data);
    }

    @OperateLog
    @ApiOperation("更新目标")
    @PostMapping("/update")
    public Response update(@RequestBody GoalDTO goalDTO) throws Exception {
        try {
            GoalDTO res = goalService.update(goalDTO);
            return Response.ok(res);
        }catch (GoalValidateException e){
            return Response.error(402,e.getMessage());
        }
    }

    @OperateLog
    @ApiOperation("删除目标")
    @GetMapping("/{goalId}/delete")
    public Response delete(@PathVariable Long goalId){
        goalService.delete(goalId);
        return Response.ok();
    }
}
