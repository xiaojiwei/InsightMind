// package com.graphinsight.indicator.manager;
//
// import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
// import com.baomidou.mybatisplus.core.toolkit.Wrappers;
// import com.graphinsight.indicator.auto.entity.Portal;
// import com.graphinsight.indicator.auto.entity.PortalMenu;
// import com.graphinsight.indicator.auto.service.IPortalMenuService;
// import com.graphinsight.indicator.auto.service.IPortalService;
// import com.graphinsight.indicator.enums.YesNoType;
// import com.graphinsight.indicator.exception.IndicatorParamNotValidException;
// import com.graphinsight.indicator.model.vo.PortalMenuVO;
// import com.graphinsight.indicator.model.vo.PortalQuery;
// import com.graphinsight.indicator.model.vo.PortalVO;
// import com.graphinsight.indicator.model.vo.TreeNode;
// import com.graphinsight.indicator.util.IndicatorAssert;
// import com.graphinsight.indicator.util.UserThreadLocalUtil;
// import lombok.extern.slf4j.Slf4j;
// import org.springframework.beans.BeanUtils;
// import org.springframework.stereotype.Component;
// import org.springframework.transaction.annotation.Transactional;
// import org.springframework.util.CollectionUtils;
// import org.springframework.util.StringUtils;
//
// import javax.annotation.Resource;
// import java.sql.Timestamp;
// import java.util.Collections;
// import java.util.Comparator;
// import java.util.List;
// import java.util.Objects;
// import java.util.stream.Collectors;
//
// /**
//  * Author: lixiaolong
//  * Date: 2022/9/1
//  * Desc:
//  */
// @Slf4j
// @Component
// public class PortalManager {
//
//     @Resource
//     IPortalService portalService;
//     @Resource
//     IPortalMenuService portalMenuService;
//     @Resource
//     UserManager userManager;
//
//     public List<PortalVO> list(PortalQuery portalQuery){
//         QueryWrapper<Portal> queryWrapper = new QueryWrapper<>();
//         queryWrapper.eq("space_id", portalQuery.getSpaceId())
//                 .eq("is_delete",YesNoType.NO.getCode())
//                 .eq(portalQuery.getIsMine(), "creator", UserThreadLocalUtil.getUserName())
//                 .and(StringUtils.hasLength(portalQuery.getKeyword()), query -> query.like("name", portalQuery.getKeyword()))
//         ;
//         List<Portal> portals = portalService.list(queryWrapper);
//         if (CollectionUtils.isEmpty(portals)){
//             return Collections.EMPTY_LIST;
//         }
//         List<PortalVO> vos = portals.stream().map(p -> convert(p,null)).collect(Collectors.toList());
//         return vos;
//
//     }
//
//     public PortalVO detail(Long portalId){
//         Portal portal = portalService.getById(portalId);
//         if (portal == null){
//             throw IndicatorParamNotValidException.error("门户不存在");
//         }
//         List<PortalMenu> menus = portalMenuService.list(Wrappers.<PortalMenu>lambdaQuery().eq(PortalMenu::getPortalId, portalId));
//         return convert(portal,menus);
//     }
//
//
//     private PortalVO convert(Portal portal,List<PortalMenu> menus){
//         PortalVO portalVO = new PortalVO();
//         BeanUtils.copyProperties(portal,portalVO);
//         portalVO.setCreator(userManager.getUserByName(portal.getCreator()));
//         portalVO.setUpdater(userManager.getUserByName(portal.getUpdater()));
//         portalVO.setCreateTime(Timestamp.valueOf(portal.getCreateTime()).getTime());
//         portalVO.setUpdateTime(Timestamp.valueOf(portal.getUpdateTime()).getTime());
//         if (! CollectionUtils.isEmpty(menus)){
//             List<PortalMenuVO> vos = findSubMenus(menus);
//             portalVO.setMenus(vos);
//         }
//         return portalVO;
//     }
//
//     private <T> TreeNode<T> convertTree(T t) {
//         TreeNode<T> treeNode = new TreeNode<>();
//         treeNode.setData(t);
//         return treeNode;
//     }
//
//     private List<PortalMenuVO> findSubMenus(List<PortalMenu> menus) {
//         if (CollectionUtils.isEmpty(menus)) {
//             return Collections.EMPTY_LIST;
//         }
//         List<PortalMenu> rootMenus = menus.stream().filter(m -> Objects.isNull(m.getParentId())).collect(Collectors.toList());
//         List<PortalMenuVO> roots = rootMenus.stream().map(menu -> convert(menu)).collect(Collectors.toList());
//         List<PortalMenuVO> vos = menus.stream().map(m -> convert(m)).collect(Collectors.toList());
//         roots.forEach(r -> findChildren(r, vos));
//         return roots;
//     }
//
//     private void findChildren(PortalMenuVO parent, List<PortalMenuVO> menus) {
//         List<PortalMenuVO> children = menus.stream()
//                 .filter(folder -> Objects.equals(folder.getParentId(), parent.getId()))
//                 .sorted(Comparator.comparing(c -> c.getSeq()))
//                 .collect(Collectors.toList());
//         if (!CollectionUtils.isEmpty(children)) {
//             children.forEach(c -> findChildren(c, menus));
//         }
//         parent.getChildren().addAll(children);
//     }
//     private PortalMenuVO convert(PortalMenu menu){
//         PortalMenuVO portalMenuVO = new PortalMenuVO();
//         BeanUtils.copyProperties(menu,portalMenuVO);
//         return portalMenuVO;
//     }
//
//
//
//     /**
//      * 保存/更新门户
//      */
//     @Transactional(rollbackFor = Exception.class)
//     public PortalVO saveOrUpdate(PortalVO portalVO){
//         boolean nameRepeat = nameRepeat(portalVO);
//         if (nameRepeat) {
//             throw IndicatorParamNotValidException.error("名称【" + portalVO.getName() + "】重复");
//         }
//         Portal portal = null;
//         if (portalVO.getId() == null) {
//             // 创建
//             portal = new Portal();
//             portal.initCreate();
//             BeanUtils.copyProperties(portalVO, portal);
//         } else {
//             // 更新
//             portal = portalService.getById(portalVO.getId());
//             IndicatorAssert.indicatorAssert(portal == null, "门户不存在");
//             portal.initUpdate();
//             BeanUtils.copyProperties(portalVO, portal);
//             // 删除原有菜单
//             removeMenus(portal.getId());
//         }
//         // 保存门户
//         //TODO 保存即生效,后续可能补发布流程
//         portal.setStatus(1);
//         portalService.saveOrUpdate(portal);
//         saveMenus(portalVO.getMenus(),portal.getId(),null);
//         PortalVO vo = detail(portal.getId());
//         return vo;
//     }
//
//
//     private boolean nameRepeat(PortalVO portalVO){
//         List<Portal> portals = portalService.list(Wrappers.<Portal>lambdaQuery()
//                 .eq(Portal::getName, portalVO.getName())
//                 .eq(Portal::getSpaceId, portalVO.getSpaceId())
//                 .ne(Objects.nonNull(portalVO.getId()), Portal::getId, portalVO.getId()));
//         return ! CollectionUtils.isEmpty(portals);
//     }
//
//     private void saveMenus(List<PortalMenuVO> menuVOS,Long portalId,Long parentId) {
//         if (! CollectionUtils.isEmpty(menuVOS)){
//             for (int i = 0; i < menuVOS.size(); i++) {
//                 PortalMenuVO vo = menuVOS.get(i);
//                 PortalMenu portalMenu = convert(vo);
//                 portalMenu.setParentId(parentId);
//                 portalMenu.setPortalId(portalId);
//                 portalMenu.setSeq(i);
//                 portalMenuService.save(portalMenu);
//                 saveMenus(vo.getChildren(),portalId,portalMenu.getId());
//             }
//         }
//     }
//
//     private PortalMenu convert(PortalMenuVO portalMenuVO){
//         PortalMenu menu = new PortalMenu();
//         BeanUtils.copyProperties(portalMenuVO,menu);
//         return menu;
//     }
//
//
//     /**
//      * 删除所有的菜单
//      */
//     private void removeMenus(Long portalId) {
//         portalMenuService.remove(Wrappers.<PortalMenu>lambdaQuery().eq(PortalMenu::getPortalId, portalId));
//     }
//
//
//
//     @Transactional(rollbackFor = Exception.class)
//     public void delete(Long id) {
//         Portal portal = portalService.getById(id);
//         if (portal == null){
//             throw IndicatorParamNotValidException.error("门户不存在");
//         }
//         portal.setIsDelete(YesNoType.YES.getCode());
//         portalService.updateById(portal);
//     }
// }
