package com.graphinsight.indicator.manager;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.graphinsight.indicator.auto.entity.Dashboard;
import com.graphinsight.indicator.auto.entity.DashboardFolder;
import com.graphinsight.indicator.auto.entity.DashboardVersion;
import com.graphinsight.indicator.auto.entity.Widget;
import com.graphinsight.indicator.auto.entity.WidgetDetail;
import com.graphinsight.indicator.auto.service.IDashboardFolderService;
import com.graphinsight.indicator.auto.service.IDashboardService;
import com.graphinsight.indicator.auto.service.IDashboardVersionService;
import com.graphinsight.indicator.auto.service.IWidgetDetailService;
import com.graphinsight.indicator.auto.service.IWidgetService;
import com.graphinsight.indicator.enums.DashboardStatus;
import com.graphinsight.indicator.enums.FieldType;
import com.graphinsight.indicator.enums.YesNoType;
import com.graphinsight.indicator.exception.IndicatorParamNotValidException;
import com.graphinsight.indicator.model.vo.DashboardFolderCreate;
import com.graphinsight.indicator.model.vo.DashboardFolderVO;
import com.graphinsight.indicator.model.vo.DashboardVO;
import com.graphinsight.indicator.model.vo.FolderQueryVO;
import com.graphinsight.indicator.model.vo.TreeNode;
import com.graphinsight.indicator.model.vo.WidgetDetailVO;
import com.graphinsight.indicator.model.vo.WidgetVO;
import com.graphinsight.indicator.util.IndicatorAssert;
import com.graphinsight.indicator.util.UserThreadLocalUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import javax.annotation.Resource;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Date: 2022/9/1
 * Desc:
 */
@Slf4j
@Component
@DS("mysql")
public class DashboardManager {

    @Resource
    IDashboardService dashboardService;
    @Resource
    IDashboardFolderService dashboardFolderService;
    @Resource
    IDashboardVersionService dashboardVersionService;
    @Resource
    IWidgetService widgetService;
    @Resource
    IWidgetDetailService widgetDetailService;
    @Resource
    CacheManager cacheManager;

    /**
     * 获取看板基础信息
     * @param spaceId
     * @return
     */
    public List<DashboardVO> listDashboard(Long spaceId){
        List<Dashboard> dashboards = dashboardService.list(Wrappers.<Dashboard>lambdaQuery().eq(Dashboard::getSpaceId, spaceId).eq(Dashboard::getIsDelete, YesNoType.NO.getCode()));
        if (CollectionUtils.isEmpty(dashboards)){
            return Collections.EMPTY_LIST;
        }
        List<DashboardVO> vos = dashboards.stream().map(d -> convertBaseInfoOnly(d)).collect(Collectors.toList());
        return vos;
    }

    public DashboardVO getVersionInfo(Long id) {
        Dashboard dashboard = dashboardService.getById(id);
        IndicatorAssert.indicatorAssert(dashboard == null, "看板不存在");
        DashboardVO dashboardVO = new DashboardVO();
        dashboardVO.setOnlineVersionId(dashboard.getOnlineVersionId());
        dashboardVO.setLatestVersionId(dashboard.getLatestVersionId());
        List<DashboardVersion> dashboardVersions = dashboardVersionService.list(Wrappers.<DashboardVersion>lambdaQuery().eq(DashboardVersion::getDashboardId, id));
        if (!CollectionUtils.isEmpty(dashboardVersions)) {
            dashboardVO.setVersionIds(dashboardVersions.stream().map(DashboardVersion::getId).collect(Collectors.toList()));
        }
        return dashboardVO;
    }


    @Transactional(rollbackFor = Exception.class)
    public void copy(Long versionId,Long folderId){
        DashboardVersion version = dashboardVersionService.getById(versionId);
        IndicatorAssert.indicatorAssert(version == null,"版本不存在");
        Long dashboardId = version.getDashboardId();
        Dashboard dashboard = dashboardService.getById(dashboardId);
        IndicatorAssert.indicatorAssert(dashboard == null,"看板不存在");

        // 保存新看板
        Dashboard newDashboard = new Dashboard();
        newDashboard.initCreate();
        BeanUtil.copyProperties(dashboard,newDashboard,CopyOptions.create().setIgnoreProperties("creator","updater","id"));
        newDashboard.setFolderId(folderId);
        dashboardService.save(newDashboard);

        // 保存新版版
        DashboardVersion newDashboardVersion = new DashboardVersion();
        newDashboardVersion.initCreate();
        BeanUtil.copyProperties(version,newDashboardVersion,CopyOptions.create().setIgnoreProperties("creator","updater","id","dashboardId","publisher","publishTime"));
        newDashboardVersion.setDashboardId(newDashboard.getId());
        newDashboardVersion.setName(version.getName() + "_副本");
        dashboardVersionService.save(newDashboardVersion);

        // 更新看板版本信息
        dashboard.setOnlineVersionId(newDashboardVersion.getId());
        dashboard.setLatestVersionId(newDashboardVersion.getId());
        dashboardService.updateById(dashboard);

        // 保存widget
       copyWidgets(version.getId(),newDashboardVersion.getId());
    }

    private void copyWidgets(Long sourceVersionId,Long targetVersionId){
        List<Widget> widgets = widgetService.list(Wrappers.<Widget>lambdaQuery().eq(Widget::getDashboardVersionId,sourceVersionId));
        if (! CollectionUtils.isEmpty(widgets)){
            widgets.forEach(widget -> {
                Widget w = new Widget();
                BeanUtil.copyProperties(widget,w);
                w.setDashboardVersionId(targetVersionId);
                widgetService.save(w);
                List<WidgetDetail> widgetDetails = widgetDetailService.list(Wrappers.<WidgetDetail>lambdaQuery().eq(WidgetDetail::getWidgetId, widget.getId()));
                if (! CollectionUtils.isEmpty(widgetDetails)){
                    List<WidgetDetail> newWidgets = new ArrayList<>();
                    widgetDetails.forEach(detail -> {
                        WidgetDetail widgetDetail = new WidgetDetail();
                        BeanUtil.copyProperties(detail,widgetDetail,"id","widgetId");
                        widgetDetail.setWidgetId(w.getId());
                        newWidgets.add(widgetDetail);
                    });
                    widgetDetailService.saveBatch(newWidgets);
                }
            });
        }
    }

    /**
     * 发布看板
     */
    @Transactional(rollbackFor = Exception.class)
    public void unpublished(Long id) {
        Dashboard dashboard = dashboardService.getById(id);
        IndicatorAssert.indicatorAssert(dashboard == null, "看板");
        dashboard.initUpdate();
        dashboard.setStatus(DashboardStatus.OFFLINE.getCode());
        dashboardService.updateById(dashboard);
    }

    /**
     * 发布看板
     */
    @Transactional(rollbackFor = Exception.class)
    public void publish(DashboardVO dashboardVO) {
        Dashboard dashboard = dashboardService.getById(dashboardVO.getId());
        IndicatorAssert.indicatorAssert(dashboard == null, "看板");
        Long sourceVersionId = dashboard.getLatestVersionId();
        dashboard.initUpdate();
        // 设置发版信息
        dashboard.setOnlineVersionId(dashboardVO.getPublishVersionId());
        dashboard.setStatus(DashboardStatus.ONLINE.getCode());
        // 获取最新版本，用于创建一个新版版
        DashboardVersion dashboardVersion = dashboardVersionService.getById(dashboard.getLatestVersionId());
        IndicatorAssert.indicatorAssert(dashboardVersion == null, "版本不存在");
        dashboardVersion.initCreate();
        dashboardVersion.setPublisher(UserThreadLocalUtil.getUserName());
        // 新增版本
        dashboardVersion.setId(null);
        dashboardVersionService.save(dashboardVersion);

        // 更新最新草稿版本号
        dashboard.setLatestVersionId(dashboardVersion.getId());
        dashboardService.updateById(dashboard);
        // 用最新的看板和版本保存widgets
        copyWidgets(sourceVersionId,dashboard.getLatestVersionId());
    }

    private void saveWidgets(DashboardVO dashboardVO) {
        List<WidgetVO> rootWidgets = dashboardVO.getWidgets();
        List<WidgetVO> vos = new ArrayList<>();
        // 打平widget
        flatList(rootWidgets, vos);
        vos.forEach(vo -> {
            Widget widget = convert(dashboardVO, vo);
            // 保存widget
            widgetService.save(widget);

            // 保存widgetDetail
            List<WidgetDetailVO> details = vo.getDetails();
            List<WidgetDetail> widgetDetails = details.stream().map(d -> {
                WidgetDetail widgetDetail = convert(d);
                widgetDetail.setWidgetId(widget.getId());
                return widgetDetail;
            }).collect(Collectors.toList());
            if (!CollectionUtils.isEmpty(widgetDetails)) {
                widgetDetailService.saveBatch(widgetDetails);
            }
        });
    }

    /**
     * 更新看板基础信息
     * @param dashboardVO
     */
    @Transactional(rollbackFor = Exception.class)
    public void updateDashboard(DashboardVO dashboardVO){
        Dashboard dashboard;
        // 更新
        dashboard = dashboardService.getById(dashboardVO.getId());
        IndicatorAssert.indicatorAssert(dashboard == null, "看板");
        BeanUtils.copyProperties(dashboardVO,dashboard,"creator","updater","createTime","updateTime","status");
        dashboardService.updateById(dashboard);
    }

    /**
     * 创建/更新 看板
     *
     * @param dashboardVO
     */
    @Transactional(rollbackFor = Exception.class)
    public Long saveOrUpdateDashboard(DashboardVO dashboardVO) {
        DashboardVersion dashboardVersion;
        Dashboard dashboard;
        if (dashboardVO.getId() == null) {
            // 创建
            dashboardVersion = new DashboardVersion();
            dashboardVersion.initCreate();
            dashboard = new Dashboard();
            dashboard.setStatus(DashboardStatus.OFFLINE.getCode());
            dashboard.initCreate();
        } else {
            // 更新
            dashboard = dashboardService.getById(dashboardVO.getId());
            IndicatorAssert.indicatorAssert(dashboard == null, "看板");
            dashboardVersion = dashboardVersionService.getById(dashboard.getLatestVersionId());
            IndicatorAssert.indicatorAssert(dashboardVersion == null, "版本不存在");
            dashboardVersion.initUpdate();
            // 删除原有widgets
            removeWidgets(Arrays.asList(dashboardVersion.getId()));
        }
        BeanUtils.copyProperties(dashboardVO,dashboard,"creator","updater","createTime","updateTime","status");
        BeanUtils.copyProperties(dashboardVO,dashboardVersion,"id","creator","updater","publisher","createTime","updateTime");
        // 先保存看板 拿到看板ID，再保存版本
        dashboardService.saveOrUpdate(dashboard);
        dashboardVersion.setDashboardId(dashboard.getId());
        dashboardVersionService.saveOrUpdate(dashboardVersion);

        dashboard.setLatestVersionId(dashboardVersion.getId());
        // dashboard.setOnlineVersionId(dashboardVersion.getId());
        // 拿到最新版本号，再次更新dashboard
        dashboardService.saveOrUpdate(dashboard);

        // 用最新的看板和版本保存widgets
        dashboardVO.setId(dashboard.getId());
        dashboardVO.setLatestVersionId(dashboard.getLatestVersionId());
        saveWidgets(dashboardVO);
        return dashboard.getLatestVersionId();
    }

    private List<WidgetDetail> listDetails(List<WidgetVO> vos) {
        List<WidgetDetail> widgetDetails = new ArrayList<>();
        for (WidgetVO vo : vos) {
            List<WidgetDetailVO> details = vo.getDetails();
            if (!CollectionUtils.isEmpty(details)) {
                List<WidgetDetail> detailList = details.stream().map(detail -> convert(detail)).collect(Collectors.toList());
                widgetDetails.addAll(detailList);
            }
        }
        return widgetDetails;
    }

    private WidgetDetail convert(WidgetDetailVO widgetDetailVO) {
        WidgetDetail widgetDetail = new WidgetDetail();
        BeanUtils.copyProperties(widgetDetailVO, widgetDetail);
        if (widgetDetailVO.getCode().startsWith("MEAS_")) {
            widgetDetail.setType(FieldType.MEASURE.getCode());
        } else {
            widgetDetail.setType(FieldType.DIMENSION.getCode());
        }
        return widgetDetail;
    }

    private Widget convert(DashboardVO dashboardVO, WidgetVO vo) {
        Widget widget = new Widget();
        BeanUtils.copyProperties(vo, widget);
        widget.setDashboardId(dashboardVO.getId());
        widget.setDashboardVersionId(dashboardVO.getLatestVersionId());
        return widget;
    }

    public void flatList(List<WidgetVO> parents, List<WidgetVO> widgets) {
        if (!CollectionUtils.isEmpty(parents)) {
            widgets.addAll(parents);
            parents.forEach(vo -> {
                flatList(vo.getWidgets(), widgets);
                if (!CollectionUtils.isEmpty(vo.getWidgets())){
                    widgets.addAll(vo.getWidgets());
                }
            });
        }
    }


    /**
     * 根据版本号获取看板详情
     *
     * @param dashboardVersionId
     * @return
     */
    public DashboardVO dashboardDetail(Long dashboardVersionId) {
        DashboardVO dashboardVO = new DashboardVO();
        DashboardVersion dashboardVersion = dashboardVersionService.getById(dashboardVersionId);
        IndicatorAssert.indicatorAssert(dashboardVersion == null, "版本不存在");
        Dashboard dashboard = dashboardService.getById(dashboardVersion.getDashboardId());
        fillBaseInfo(dashboard, dashboardVersion, dashboardVO);
        List<Widget> widgets = widgetService.list(Wrappers.<Widget>lambdaQuery().eq(Widget::getDashboardVersionId, dashboardVersionId));
        if (!CollectionUtils.isEmpty(widgets)) {
            List<WidgetVO> vos = widgets.stream().map(w -> convert(w)).collect(Collectors.toList());
            List<WidgetVO> rootWidgets = widgets.stream()
                    .filter(w -> w.getParentId() == null)
                    .map(w -> convert(w))
                    .collect(Collectors.toList());
            rootWidgets.forEach(root -> findChildren(root, vos));
            dashboardVO.setWidgets(rootWidgets);
        }
        return dashboardVO;
    }

    @Autowired
    UserManager userManager;

    private void fillBaseInfo(Dashboard dashboard, DashboardVersion dashboardVersion, DashboardVO dashboardVO) {
        BeanUtil.copyProperties(dashboard, dashboardVO, CopyOptions.create().setIgnoreError(true));
        dashboardVO.setName(dashboard.getName());
        dashboardVO.setCreator(userManager.getUserByName(dashboard.getCreator()));
        dashboardVO.setRemark(dashboard.getRemark());
        dashboardVO.setCreateTime(Timestamp.valueOf(dashboard.getCreateTime()).getTime());
        if (dashboardVersion != null){
            dashboardVO.setUpdater(userManager.getUserByName(dashboardVersion.getUpdater()));
            dashboardVO.setPublisher(userManager.getUserByName(dashboardVersion.getPublisher()));
            dashboardVO.setPublishTime(Optional.ofNullable(dashboardVersion.getPublishTime()).map(time -> Timestamp.valueOf(time).getTime()).orElse(null));
            dashboardVO.setUpdateTime(Timestamp.valueOf(dashboardVersion.getUpdateTime()).getTime());
        }

    }

    private void findChildren(WidgetVO parent, List<WidgetVO> widgetVOList) {
        List<WidgetVO> widgetVOS = widgetVOList.stream()
                .filter(w -> Objects.equals(w.getParentId(), parent.getId()))
                .collect(Collectors.toList());
        if (!CollectionUtils.isEmpty(widgetVOS)) {
            widgetVOS.forEach(vo -> findChildren(vo, widgetVOList));
            parent.setWidgets(widgetVOS);
        }
    }

    private WidgetVO convert(Widget widget) {
        if (widget == null) {
            return null;
        }
        WidgetVO widgetVO = new WidgetVO();
        BeanUtils.copyProperties(widget, widgetVO);
        return widgetVO;
    }


    public void saveOrUpdateFolder(DashboardFolderCreate dashboardFolderCreate) {
        DashboardFolder dashboardFolder;
        if (dashboardFolderCreate.getId() == null) {
            // 新增
            dashboardFolder = new DashboardFolder();
            dashboardFolder.initCreate();
        } else {
            // 更新
            dashboardFolder = dashboardFolderService.getById(dashboardFolderCreate.getId());
            IndicatorAssert.indicatorAssert(dashboardFolder == null, "文件夹不存在");
            dashboardFolderCreate.setCreator(null);
            dashboardFolderCreate.setUpdater(null);
            dashboardFolder.initUpdate();
        }
        BeanUtil.copyProperties(dashboardFolderCreate, dashboardFolder, CopyOptions.create().ignoreNullValue().setIgnoreError(true));
        dashboardFolderService.saveOrUpdate(dashboardFolder);
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteFolder(Long folderId) {
        List<DashboardFolder> children = dashboardFolderService.list(Wrappers.<DashboardFolder>lambdaQuery().eq(DashboardFolder::getParentId, folderId));
        List<Dashboard> dashboards = dashboardService
                .list(Wrappers.<Dashboard>lambdaQuery()
                .eq(Dashboard::getFolderId, folderId)
                .eq(Dashboard::getIsDelete, YesNoType.NO.getCode()));

        if (!CollectionUtils.isEmpty(children) || !CollectionUtils.isEmpty(dashboards)) {
            throw IndicatorParamNotValidException.error("文件夹下有内容，请先清空文件夹");
        }
        if (!CollectionUtils.isEmpty(dashboards)) {
            dashboards.forEach(d -> deleteDashboard(d.getId()));
        }
        dashboardFolderService.removeById(folderId);
        // 递归删除子文件夹
        children.forEach(f -> deleteFolder(f.getId()));
    }


    @Transactional(rollbackFor = Exception.class)
    public void deleteDashboard(Long dashboardId) {
        // List<DashboardVersion> dashboardVersions = dashboardVersionService.list(Wrappers.<DashboardVersion>lambdaQuery().eq(DashboardVersion::getDashboardId, dashboardId));
        // if (!CollectionUtils.isEmpty(dashboardVersions)) {
        //     Set<Long> vIds = dashboardVersions.stream().map(DashboardVersion::getId).collect(Collectors.toSet());
        //     removeWidgets(vIds);
        //     dashboardVersionService.removeByIds(vIds);
        // }
        // dashboardService.removeById(dashboardId);
        Dashboard dashboard = dashboardService.getById(dashboardId);
        if (dashboard != null){
            dashboard.setIsDelete(YesNoType.YES.getCode());
            dashboardService.updateById(dashboard);
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void removeWidgets(Collection versionIds) {
        List<Widget> widgets = widgetService.list(Wrappers.<Widget>lambdaQuery().in(Widget::getDashboardVersionId, versionIds));
        if (!CollectionUtils.isEmpty(widgets)) {
            List<Long> wIds = widgets.stream().map(Widget::getId).collect(Collectors.toList());
            widgetDetailService.remove(Wrappers.<WidgetDetail>lambdaQuery().in(WidgetDetail::getWidgetId, wIds));
            widgetService.removeByIds(wIds);
        }
    }


    public List<TreeNode<DashboardFolderVO>> listFolder(FolderQueryVO queryVO) {
        QueryWrapper<DashboardFolder> folderQueryWrapper = new QueryWrapper<>();
        folderQueryWrapper.eq("space_id", queryVO.getSpaceId())
                .eq(queryVO.getIsMine(), "creator", UserThreadLocalUtil.getUserName())
                .and(StringUtils.hasLength(queryVO.getKeyword()), query -> query.like("name", queryVO.getKeyword()))
        ;
        List<DashboardFolder> folders = dashboardFolderService.list(folderQueryWrapper);
        List<TreeNode<DashboardFolderVO>> treeNodes = convertTree(folders, new HashMap<>());
        if (queryVO.getFolderOnly()) {
            return treeNodes;
        }
        QueryWrapper<Dashboard> dashboardQueryWrapper = new QueryWrapper<>();
        if (!CollectionUtils.isEmpty(folders)) {
            Set<Long> folderIds = folders.stream().map(DashboardFolder::getId).collect(Collectors.toSet());
            dashboardQueryWrapper.and(query -> query.in("folder_id", folderIds).or().isNull("folder_id"));
        }
        dashboardQueryWrapper
                .eq("space_id",queryVO.getSpaceId())
                .eq(queryVO.getStatus() != null, "status", queryVO.getStatus())
                .eq("is_delete", YesNoType.NO.getCode())
                .eq(queryVO.getIsMine(), "creator", UserThreadLocalUtil.getUserName());
        if (StringUtils.hasLength(queryVO.getKeyword())){
            List<Long> versionIds = dashboardVersionService.list(Wrappers.<DashboardVersion>lambdaQuery()
                    .like(DashboardVersion::getName, queryVO.getKeyword()))
                    .stream()
                    .map(DashboardVersion::getId)
                    .collect(Collectors.toList());
            if (! CollectionUtils.isEmpty(versionIds)){
                dashboardQueryWrapper.in("online_version_id",versionIds);
            }
        }
        List<Dashboard> dashboards = dashboardService.list(dashboardQueryWrapper);
        List<Dashboard> rootDashboards = dashboards.stream().filter(dashboard -> dashboard.getFolderId() == null).collect(Collectors.toList());
        List<Dashboard> folderDashboards = dashboards.stream().filter(dashboard -> dashboard.getFolderId() != null).collect(Collectors.toList());
        List<Long> folderIds = folderDashboards.stream().map(Dashboard::getFolderId).collect(Collectors.toList());
        List<DashboardFolder> allFolders = new ArrayList<>();
        allFolders.addAll(folders);
        if (! CollectionUtils.isEmpty(folderIds)){
            List<DashboardFolder> folderList = dashboardFolderService.listByIds(folderIds);
            Set<Long> ids = folders.stream().map(DashboardFolder::getId).collect(Collectors.toSet());
            folderList.forEach(f -> {
                if (! ids.contains(f.getId())){
                    allFolders.add(f);
                }
            });
        }
        Map<Long, List<Dashboard>> folderDashboardMap = folderDashboards.stream().collect(Collectors.groupingBy(Dashboard::getFolderId));
        List<TreeNode<DashboardFolderVO>> folderTree = convertTree(allFolders, folderDashboardMap);
        List<TreeNode<DashboardFolderVO>> roots = rootDashboards.stream().map(dashboard -> {
            TreeNode<DashboardFolderVO> treeNode = new TreeNode<>();
            DashboardVO dashboardVO = convert(dashboard);
            treeNode.setData(dashboardVO);
            return treeNode;
        }).collect(Collectors.toList());
        List<TreeNode<DashboardFolderVO>> result = new ArrayList<>();
        result.addAll(roots);
        result.addAll(folderTree);
        return  result;
    }

    private List<TreeNode<DashboardFolderVO>> convertTree(List<DashboardFolder> folders, Map<Long, List<Dashboard>> folderDashboardMap) {
        if (CollectionUtils.isEmpty(folders)) {
            return Collections.EMPTY_LIST;
        }
        List<TreeNode<DashboardFolderVO>> folderNodes = folders.stream().map(f -> convert(f)).map(f -> convertTree(f)).collect(Collectors.toList());
        folderNodes.forEach(f -> {
            List<Dashboard> dashboards = folderDashboardMap.get(f.getData().getId());
            if (!CollectionUtils.isEmpty(dashboards)) {
                List<TreeNode<DashboardFolderVO>> vos = dashboards.stream()
                        .map(d -> convert(d))
                        .map(d -> {
                            TreeNode<DashboardFolderVO> treeNode = new TreeNode<>();
                            treeNode.setData(d);
                            return treeNode;
                        })
                        .collect(Collectors.toList());
                f.getChildren().addAll(vos);
            }
        });

        folderNodes.forEach(r -> findChildren(r, folderNodes));
        List<TreeNode<DashboardFolderVO>> rootNodes = folderNodes.stream().filter(f -> f.getData().getParentId() == null).collect(Collectors.toList());
        return rootNodes;
    }

    private DashboardVO convertBaseInfoOnly(Dashboard dashboard) {
        DashboardVO vo = new DashboardVO();
        fillBaseInfo(dashboard, null, vo);
        return vo;
    }

    private DashboardVO convert(Dashboard dashboard) {
        DashboardVO vo = new DashboardVO();
        DashboardVersion dashboardVersion = dashboardVersionService.getById(dashboard.getOnlineVersionId());
        if (dashboardVersion == null){
            // 没有线上版本，说明还未发版，用最新版本代替
            dashboardVersion = dashboardVersionService.getById(dashboard.getLatestVersionId());
        }
        if (dashboardVersion != null){
            fillBaseInfo(dashboard, dashboardVersion, vo);
        }
        return vo;
    }

    private void findChildren(TreeNode<DashboardFolderVO> parent, List<TreeNode<DashboardFolderVO>> folders) {
        List<TreeNode<DashboardFolderVO>> children = folders.stream()
                .filter(folder -> Objects.equals(folder.getData().getParentId(), parent.getData().getId()))
                .collect(Collectors.toList());
        if (!CollectionUtils.isEmpty(children)) {
            children.forEach(c -> findChildren(c, folders));
        }
        parent.getChildren().addAll(children);
    }

    private DashboardFolderVO convert(DashboardFolder dashboardFolder) {
        if (dashboardFolder == null) {
            return null;
        }
        DashboardFolderVO vo = new DashboardFolderVO();
        BeanUtils.copyProperties(dashboardFolder, vo);
        vo.setCreator(userManager.getUserByName(dashboardFolder.getCreator()));
        vo.setUpdater(userManager.getUserByName(dashboardFolder.getUpdater()));
        return vo;
    }

    private <T> TreeNode<T> convertTree(T t) {
        TreeNode<T> treeNode = new TreeNode<>();
        treeNode.setData(t);
        return treeNode;
    }
}
