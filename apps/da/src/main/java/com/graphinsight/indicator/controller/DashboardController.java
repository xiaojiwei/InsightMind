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

    @GetMapping("/dashboard/list/{spaceId}")
    public Response<List<DashboardVO>> listDashboard(@PathVariable("spaceId") Long spaceId ){
        List<DashboardVO> vos = dashboardManager.listDashboard(spaceId);
        return Response.ok(vos);
    }

    @PostMapping("/folder/list")
    public Response<List<TreeNode>> listFloder(@RequestBody @Validated FolderQueryVO queryVO ){
        List<TreeNode<DashboardFolderVO>> treeNodes = dashboardManager.listFolder(queryVO);
        return Response.ok(treeNodes);
    }

    @OperateLog
    @PostMapping("/copy")
    public Response copy(@RequestBody @Validated DashboardCopy dashboardCopy ){
        dashboardManager.copy(dashboardCopy.getVersionId(),dashboardCopy.getFolderId());
        return Response.ok();
    }


    @GetMapping("/detail/{versionId}")
    @IgnoreWebLog
    public Response<DashboardVO> detail(@PathVariable Long versionId){
        DashboardVO detail = dashboardManager.dashboardDetail(versionId);
        return Response.ok(detail);
    }

    @GetMapping("/get/versions/{id}")
    public Response<DashboardVO> getVersionInfo(@PathVariable Long id){
        DashboardVO vo = dashboardManager.getVersionInfo(id);
        return Response.ok(vo);
    }

    @OperateLog
    @PostMapping("/folder/save")
    public Response saveFolder(@RequestBody @Validated DashboardFolderCreate folderCreate ){
        dashboardManager.saveOrUpdateFolder(folderCreate);
        return Response.ok();
    }

    @OperateLog
    @PostMapping("/update")
    public Response<DashboardVO> updateDashboard(@RequestBody @Validated DashboardVO dashboardVO ){
        dashboardManager.updateDashboard(dashboardVO);
        return Response.ok();
    }

    @OperateLog
    @PostMapping("/save")
    public Response<DashboardVO> saveDashboard(@RequestBody @Validated DashboardVO dashboardVO ){
        Long versionId = dashboardManager.saveOrUpdateDashboard(dashboardVO);
        DashboardVO vo = dashboardManager.dashboardDetail(versionId);
        return Response.ok(vo);
    }

    @OperateLog
    @PostMapping("/publish")
    public Response publishDashboard(@RequestBody @Validated DashboardVO dashboardVO ){
        dashboardManager.publish(dashboardVO);
        return Response.ok();
    }

    @OperateLog
    @GetMapping("/unpublished/{id}")
    public Response<DashboardVO> unpublished(@PathVariable Long id){
        dashboardManager.unpublished(id);
        return Response.ok();
    }

    @OperateLog
    @GetMapping("/folder/delete/{id}")
    public Response deleteFolder(@PathVariable Long id){
        dashboardManager.deleteFolder(id);
        return Response.ok();
    }

    @OperateLog
    @GetMapping("/delete/{id}")
    public Response deleteDashboard(@PathVariable Long id){
        dashboardManager.deleteDashboard(id);
        return Response.ok();
    }
}
