package com.graphinsight.indicator.controller;

import com.graphinsight.indicator.annotation.IgnoreWebLog;
import com.graphinsight.indicator.annotation.OperateLog;
import com.graphinsight.indicator.manager.DashboardManager;
import com.graphinsight.indicator.model.Response;
import com.graphinsight.indicator.model.vo.DashboardCopy;
import com.graphinsight.indicator.model.vo.DashboardFolderCreate;
import com.graphinsight.indicator.model.vo.DashboardFolderVO;
import com.graphinsight.indicator.model.vo.DashboardVO;
import com.graphinsight.indicator.model.vo.FolderQueryVO;
import com.graphinsight.indicator.model.vo.TreeNode;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.List;

/**
 * Date: 2022/9/1
 * Desc:
 */
@Slf4j
@RestController
@RequestMapping("/dashboard")
public class DashboardController {

    @Resource
    DashboardManager dashboardManager;

    @ApiOperation("看板列表")
    @GetMapping("/dashboard/list/{spaceId}")
    public Response<List<DashboardVO>> listDashboard(@PathVariable("spaceId") Long spaceId ){
        List<DashboardVO> vos = dashboardManager.listDashboard(spaceId);
        return Response.ok(vos);
    }

    @ApiOperation("文件列表")
    @PostMapping("/folder/list")
    public Response<List<TreeNode>> listFloder(@RequestBody @Validated FolderQueryVO queryVO ){
        List<TreeNode<DashboardFolderVO>> treeNodes = dashboardManager.listFolder(queryVO);
        return Response.ok(treeNodes);
    }

    @OperateLog
    @ApiOperation("复制看板")
    @PostMapping("/copy")
    public Response copy(@RequestBody @Validated DashboardCopy dashboardCopy ){
        dashboardManager.copy(dashboardCopy.getVersionId(),dashboardCopy.getFolderId());
        return Response.ok();
    }


    @ApiOperation("看板详情")
    @GetMapping("/detail/{versionId}")
    @IgnoreWebLog
    public Response<DashboardVO> detail(@PathVariable Long versionId){
        DashboardVO detail = dashboardManager.dashboardDetail(versionId);
        return Response.ok(detail);
    }

    @ApiOperation("获取看板版本信息")
    @GetMapping("/get/versions/{id}")
    public Response<DashboardVO> getVersionInfo(@PathVariable Long id){
        DashboardVO vo = dashboardManager.getVersionInfo(id);
        return Response.ok(vo);
    }

    @OperateLog
    @ApiOperation("创建/更新 文件夹")
    @PostMapping("/folder/save")
    public Response saveFolder(@RequestBody @Validated DashboardFolderCreate folderCreate ){
        dashboardManager.saveOrUpdateFolder(folderCreate);
        return Response.ok();
    }

    @OperateLog
    @ApiOperation("更新 看板基础信息")
    @PostMapping("/update")
    public Response<DashboardVO> updateDashboard(@RequestBody @Validated DashboardVO dashboardVO ){
        dashboardManager.updateDashboard(dashboardVO);
        return Response.ok();
    }

    @OperateLog
    @ApiOperation("保存/更新 看板")
    @PostMapping("/save")
    public Response<DashboardVO> saveDashboard(@RequestBody @Validated DashboardVO dashboardVO ){
        Long versionId = dashboardManager.saveOrUpdateDashboard(dashboardVO);
        DashboardVO vo = dashboardManager.dashboardDetail(versionId);
        return Response.ok(vo);
    }

    @OperateLog
    @ApiOperation("发布看板")
    @PostMapping("/publish")
    public Response publishDashboard(@RequestBody @Validated DashboardVO dashboardVO ){
        dashboardManager.publish(dashboardVO);
        return Response.ok();
    }

    @OperateLog
    @ApiOperation("取消发布")
    @GetMapping("/unpublished/{id}")
    public Response<DashboardVO> unpublished(@PathVariable Long id){
        dashboardManager.unpublished(id);
        return Response.ok();
    }

    @OperateLog
    @ApiOperation("删除文件夹")
    @GetMapping("/folder/delete/{id}")
    public Response deleteFolder(@PathVariable Long id){
        dashboardManager.deleteFolder(id);
        return Response.ok();
    }

    @OperateLog
    @ApiOperation("删除看板")
    @GetMapping("/delete/{id}")
    public Response deleteDashboard(@PathVariable Long id){
        dashboardManager.deleteDashboard(id);
        return Response.ok();
    }
}
