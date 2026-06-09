package com.graphinsight.indicator.manager;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.google.common.base.Charsets;
import com.graphinsight.indicator.auto.entity.Category;
import com.graphinsight.indicator.auto.service.ICategoryService;
import com.graphinsight.indicator.constant.IndicatorConstant;
import com.graphinsight.indicator.model.cache.MetadataCache;
import com.graphinsight.indicator.model.cache.SpaceContext;
import com.graphinsight.indicator.model.vo.CategoryNodeItem;
import com.graphinsight.indicator.model.vo.CategoryQueryVO;
import com.graphinsight.indicator.model.vo.CategoryTree;
import com.graphinsight.indicator.model.vo.CategoryTreeNode;
import com.graphinsight.indicator.model.vo.CategoryVO;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.DigestUtils;

import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @Author: lixiaolong
 * @Description:
 * @Date: 2021/11/30
 */
@Service
@DS("mysql")
public class CategoryManager {

    @Autowired
    private ICategoryService categoryService;

    public boolean hasChildCategory(Integer categoryId) {
        if (categoryId != null) {
            List<Category> categoryList = categoryService.list(Wrappers.<Category>lambdaQuery().eq(Category::getParentId, categoryId));
            if (!CollectionUtils.isEmpty(categoryList)) {
                return true;
            }
        } else {
            throw new RuntimeException("分类不存在");
        }
        return false;
    }

    /**
     * 查找分类下所有的子分类
     *
     * @param parentId
     * @return
     */
    public List<Category> findAllChildren(Integer parentId) {
        List<Category> result = new ArrayList<>();
        Category category = categoryService.getById(parentId);
        if (Objects.isNull(category)) {
            return result;
        }
        result.add(category);
        List<Category> allCategoryList = categoryService.list();
        findChildren(parentId, allCategoryList, result);
        return result;
    }

    private void findChildren(Integer parentId, Collection<Category> allCategoryList, List<Category> result) {
        List<Category> children = allCategoryList.stream().filter(c -> Objects.equals(c.getParentId(), parentId)).collect(Collectors.toList());
        if (!CollectionUtils.isEmpty(children)) {
            result.addAll(children);
            children.forEach(c -> {
                findChildren(c.getId(), allCategoryList, result);
            });
        }
    }

    public Set<Integer> findChildrenFromCache(Collection<Integer> ids) {
        if (CollectionUtils.isEmpty(ids)) {
            return Collections.EMPTY_SET;
        }
        Map<Integer, Category> categoryMap = cacheManager.getMetadataCache().getCategoryMap();
        Set<Integer> children = new HashSet<>();
        ids.forEach(id -> {
            Category category = categoryMap.get(id);
            if (category != null) {
                findChildren(category.getId(), categoryMap.values(), children);
            }
        });
        return children;
    }

    private void findChildren(Integer parentId, Collection<Category> all, Set<Integer> children) {
        if (parentId != null && !CollectionUtils.isEmpty(all)) {
            children.add(parentId);
            List<Category> subs = all.stream().filter(c -> Objects.equals(c.getParentId(), parentId)).collect(Collectors.toList());
            Set<Integer> ids = subs.stream().map(Category::getId).collect(Collectors.toSet());
            children.addAll(ids);
            ids.forEach(id -> findChildren(id, all, children));
        }
    }

    /**
     * 根据父节点找所有的叶子节点
     *
     * @param parentId
     * @return
     */
    public List<Integer> findLeafIdById(Integer parentId) {
        List<Integer> result = new ArrayList<>();
        if (!hasChildCategory(parentId)) {
            result.add(parentId);
        } else {
            Category parentCategory = categoryService.getById(parentId);
            if (parentCategory == null) {
                return Collections.EMPTY_LIST;
            }
            Integer rootId = parentCategory.getRootId();
            List<Category> categoryList = categoryService.list(Wrappers.<Category>lambdaQuery().eq(Category::getRootId, rootId));
            CategoryTree categoryTree = new CategoryTree();
            categoryTree.setId(parentId);
            List<CategoryTree> treeList = categoryList.stream().map(c -> {
                CategoryTree tree = new CategoryTree<>();
                BeanUtils.copyProperties(c, tree);
                return tree;
            }).collect(Collectors.toList());
            findChild(categoryTree, treeList);
            findLeafIdsByParentId(categoryTree.getChildren(), result);
        }
        return result;
    }

    private void findLeafIdsByParentId(List<CategoryTree> target, List<Integer> result) {
        target.forEach(tree -> {
            if (CollectionUtils.isEmpty(tree.getChildren())) {
                result.add(tree.getId());
            } else {
                findLeafIdsByParentId(tree.getChildren(), result);
            }
        });
    }


    /**
     * 根据叶子节点找到双亲
     *
     * @param leafId
     * @return
     */
    public List<CategoryVO> findParentsByLeaf(Integer leafId) {
        Category leaf = categoryService.getById(leafId);
        if (Objects.isNull(leaf)) {
            return Collections.EMPTY_LIST;
        }
        Integer rootId = leaf.getRootId();
        List<Category> categoryList = categoryService.list(Wrappers.<Category>lambdaQuery().eq(Category::getRootId, rootId));
        List<CategoryVO> result = new LinkedList<>();
        CategoryVO categoryVO = new CategoryVO();
        BeanUtils.copyProperties(leaf, categoryVO);
        result.add(categoryVO);
        findParent(result, leaf, categoryList);
        Collections.reverse(result);
        return result;
    }

    public Map<Integer, List<CategoryVO>> findParentsByLeaf(Set<Integer> leafIdList) {
        if (CollectionUtils.isEmpty(leafIdList)) {
            return Collections.EMPTY_MAP;
        }
        List<Category> list = categoryService.list(Wrappers.<Category>lambdaQuery().in(Category::getId, leafIdList));
        if (CollectionUtils.isEmpty(list)) {
            return Collections.EMPTY_MAP;
        }
        Set<Integer> rootIdSet = list.stream().map(Category::getRootId).collect(Collectors.toSet());
        List<Category> categorieWithSameRootId = categoryService.list(Wrappers.<Category>lambdaQuery().in(Category::getRootId, rootIdSet));
        Map<Integer, List<CategoryVO>> result = new HashMap<>();
        list.forEach(c -> {
            List<CategoryVO> voList = new LinkedList<>();
            CategoryVO categoryVO = new CategoryVO();
            BeanUtils.copyProperties(c, categoryVO);
            findParent(voList, c, categorieWithSameRootId);
            Collections.reverse(voList);
            voList.add(categoryVO);
            result.put(c.getId(), voList);
        });
        return result;
    }


    private void findParent(List<CategoryVO> result, Category target, List<Category> list) {
        Category category = list.stream().filter(c -> Objects.equals(target.getParentId(), c.getId())).findFirst().orElse(null);
        if (category != null) {
            CategoryVO vo = new CategoryVO();
            vo.setId(category.getId());
            vo.setName(category.getName());
            result.add(vo);
            findParent(result, category, list);
        }
    }

    @DS("mysql")
    public List<CategoryTree> getTree(CategoryQueryVO categoryQueryVO) {
        List<Category> list = categoryService.list(Wrappers.<Category>lambdaQuery()
                .eq(categoryQueryVO.isMeas(), Category::getMeasApplicable, 1)
                .or()
                .eq(categoryQueryVO.isDim(), Category::getDimApplicable, 1)
                .or()
                .eq(categoryQueryVO.isModel(), Category::getModelApplicable, 1)
        );
        List<CategoryTree> trees = getTree(list);
        return trees;
    }

    public List<CategoryTree> getTreeBySpaceId(Long spaceId, boolean currentSpace) {
        Map<Long, SpaceContext> spaceContextMap = cacheManager.getMetadataCache().getSpaceContextMap();
        SpaceContext spaceContext = spaceContextMap.get(spaceId);
        if (spaceContext == null) {
            return Collections.EMPTY_LIST;
        }
        Set<Integer> ids = new HashSet<>();
        Map<Integer, Category> categoryMap = cacheManager.getMetadataCache().getCategoryMap();
        if (currentSpace) {
            ids.addAll(spaceContext.getMeasCategoryIdsWithChildren());
        } else {
            ids.addAll(categoryMap.keySet());
            ids.removeAll(spaceContext.getMeasCategoryIdsWithChildren());
        }
        Set<Integer> categoryIds = new HashSet<>();
        ids.forEach(id -> findParent(categoryMap.get(id), categoryMap.values(), categoryIds));
        List<Category> categories = categoryIds.stream().map(id -> categoryMap.get(id)).collect(Collectors.toList());
        return getTree(categories);
    }

    public Set<Integer> getCateIdBySpaceId(Long spaceId, boolean currentSpace) {
        Map<Long, SpaceContext> spaceContextMap = cacheManager.getMetadataCache().getSpaceContextMap();
        SpaceContext spaceContext = spaceContextMap.get(spaceId);
        if (spaceContext == null) {
            return Collections.EMPTY_SET;
        }
        Set<Integer> ids = new HashSet<>();
        Map<Integer, Category> categoryMap = cacheManager.getMetadataCache().getCategoryMap();
        if (currentSpace) {
            ids.addAll(spaceContext.getMeasCategoryIdsWithChildren());
        } else {
            ids.addAll(categoryMap.keySet());
            ids.removeAll(spaceContext.getMeasCategoryIdsWithChildren());
        }

        return ids;
    }

    public void findParent(Category target, Collection<Category> all, Set<Integer> categoryIds) {
        if (target != null) {
            categoryIds.add(target.getId());
            Category category = all.stream().filter(c -> Objects.equals(c.getId(), target.getParentId())).findFirst().orElse(null);
            if (category != null) {
                findParent(category, all, categoryIds);
                categoryIds.add(category.getId());
            }
        }
    }

    public List<CategoryTree> getTree(List<Category> categories) {
        List<CategoryTree> treeList = categories.stream().map(c -> {
            CategoryTree categoryTree = new CategoryTree();
            BeanUtils.copyProperties(c, categoryTree);
            return categoryTree;
        }).collect(Collectors.toList());

        List<CategoryTree> resultList = treeList.stream().filter(c -> c.getParentId() == null).sorted(Comparator.comparing(CategoryTree::getSequence)).collect(Collectors.toList());
        resultList.forEach(c -> findChild(c, treeList));
        return resultList;
    }

    public void checkAndSetCategoryCode() {
        List<Category> list = categoryService.list();
        if (!CollectionUtils.isEmpty(list)) {
            List<Category> categorys = list.stream().filter(c -> Objects.isNull(c.getCode())).map(c -> {
                try {
                    c.setCode(IndicatorConstant.CATEGORY_CODE_PREFIX + DigestUtils.md5DigestAsHex(c.getId().toString().getBytes(Charsets.UTF_8.name())));
                } catch (UnsupportedEncodingException e) {
                }
                return c;
            }).collect(Collectors.toList());
            categoryService.updateBatchById(categorys);
        }

    }

    @Autowired
    CacheManager cacheManager;
    @Autowired
    SpaceManager spaceManager;

    public List<CategoryTreeNode<CategoryNodeItem>> getCategoryTreeNodes(CategoryQueryVO categoryQueryVO, MetadataCache metdataCache) {
        List<Category> list = categoryService.list(Wrappers.<Category>lambdaQuery()
                .eq(categoryQueryVO.isMeas(), Category::getMeasApplicable, 1)
                .or()
                .eq(categoryQueryVO.isDim(), Category::getDimApplicable, 1)
                .or()
                .eq(categoryQueryVO.isModel(), Category::getModelApplicable, 1)
        );

        List<CategoryTreeNode<CategoryNodeItem>> treeList = list.stream().map(c -> {
            CategoryTreeNode<CategoryNodeItem> categoryTree = new CategoryTreeNode();
            CategoryNodeItem item = new CategoryNodeItem();
            item.setCnName(c.getName());
            item.setType("category");
            item.setId(c.getId());
            if (Objects.nonNull(categoryQueryVO.getSpaceId())) {
                item.setBelongSpace(belongSpace(item.getId(), categoryQueryVO.getSpaceId(), metdataCache));
            }
            BeanUtils.copyProperties(c, item);
            categoryTree.setData(item);
            return categoryTree;
        }).collect(Collectors.toList());
        List<CategoryTreeNode<CategoryNodeItem>> resultList = treeList.stream()
                .filter(c -> c.getData().getParentId() == null)
                .sorted(Comparator.comparing(c -> c.getData().getSequence()))
                .collect(Collectors.toList());
        resultList.forEach(c -> findNodeChild(c, treeList));

        return resultList;
    }


    private boolean belongSpace(Integer categoryId, Long spaceId, MetadataCache metdataCache) {
        Map<Long, SpaceContext> spaceContextMap = metdataCache.getSpaceContextMap();
        Map<Integer, Category> categoryMap = metdataCache.getCategoryMap();
        // 先查缓存 缓存为空再读数据库
        SpaceContext spaceContext = spaceContextMap.get(spaceId);
        if (Objects.isNull(spaceContext)) {
            spaceContext = spaceManager.getSpaceContext(spaceId);
        }
        Set<Integer> measCategoryIdsWithChildren = spaceContext == null ? Collections.EMPTY_SET : spaceContext.getMeasCategoryIdsWithChildren();
        List<Category> children = new ArrayList<>();
        findChildren(categoryId, categoryMap.values(), children);
        List<Integer> subIds = children.stream().map(Category::getId).collect(Collectors.toList());
        boolean containsAny = false;
        for (Integer subId : subIds) {
            if (measCategoryIdsWithChildren.contains(subId)) {
                containsAny = true;
            }
        }
        return measCategoryIdsWithChildren.contains(categoryId) || containsAny;
    }

    public List<CategoryTreeNode<CategoryNodeItem>> getCategoryTreeNodesBySpace(CategoryQueryVO categoryQueryVO) {
        Map<Long, SpaceContext> spaceContextMap = cacheManager.getMetadataCache().getSpaceContextMap();
        SpaceContext spaceContext = spaceContextMap.get(categoryQueryVO.getSpaceId());
        if (Objects.isNull(spaceContext)) {
            spaceContext = spaceManager.getSpaceContext(categoryQueryVO.getSpaceId());
        }
        if (spaceContext == null) {
            return Collections.EMPTY_LIST;
        }
        Set<Integer> ids = new HashSet<>();
        Map<Integer, Category> categoryMap = cacheManager.getMetadataCache().getCategoryMap();
        if (categoryQueryVO.getCurrentSpace()) {
            ids.addAll(spaceContext.getMeasCategoryIdsWithChildren());
        } else {
            ids.addAll(categoryMap.keySet());
            ids.removeAll(spaceContext.getMeasCategoryIdsWithChildren());
        }
        List<Category> categoryList = ids.stream().map(id -> categoryMap.get(id)).collect(Collectors.toList());
        List<CategoryTreeNode<CategoryNodeItem>> treeList = categoryList.stream().map(c -> {
            CategoryTreeNode<CategoryNodeItem> categoryTree = new CategoryTreeNode();
            CategoryNodeItem item = new CategoryNodeItem();
            item.setCnName(c.getName());
            item.setType("category");
            item.setId(c.getId());
            BeanUtils.copyProperties(c, item);
            categoryTree.setData(item);
            return categoryTree;
        }).collect(Collectors.toList());
        List<CategoryTreeNode<CategoryNodeItem>> resultList = treeList.stream()
                .filter(c -> c.getData().getParentId() == null)
                .sorted(Comparator.comparing(c -> c.getData().getSequence()))
                .collect(Collectors.toList());
        resultList.forEach(c -> findNodeChild(c, treeList));
        return resultList;
    }

    private void findNodeChild(CategoryTreeNode<CategoryNodeItem> target, List<CategoryTreeNode<CategoryNodeItem>> categoryTreeNodeList) {
        List<CategoryTreeNode<CategoryNodeItem>> childList = categoryTreeNodeList.stream().filter((c) -> Objects.equals(c.getData().getParentId(), target.getData().getId())).sorted(Comparator.comparing(c -> c.getData().getSequence())).collect(Collectors.toList());
        if (!CollectionUtils.isEmpty(childList)) {
            target.setChildren(childList);
        }
        childList.forEach(c -> findNodeChild(c, categoryTreeNodeList));
        // if(!CollectionUtils.isEmpty(childList)){
        //     target.setChildren(childList);
        // }
    }

    private void findChild(CategoryTree target, List<CategoryTree> categoryTreeList) {
        List<CategoryTree> childList = categoryTreeList.stream().filter((c) -> Objects.equals(c.getParentId(), target.getId())).sorted(Comparator.comparing(CategoryTree::getSequence)).collect(Collectors.toList());
        childList.forEach(c -> findChild(c, categoryTreeList));
        if (!CollectionUtils.isEmpty(childList)) {
            target.setChildren(childList);
        }
    }


}
