package com.graphinsight.indicator.controller;


import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.graphinsight.indicator.annotation.OperateLog;
import com.graphinsight.indicator.auto.entity.Category;
import com.graphinsight.indicator.auto.mapper.CategoryMapper;
import com.graphinsight.indicator.auto.service.ICategoryService;
import com.graphinsight.indicator.manager.CategoryManager;
import com.graphinsight.indicator.model.Response;
import com.graphinsight.indicator.model.vo.CategoryCreateVO;
import com.graphinsight.indicator.model.vo.CategoryQueryVO;
import com.graphinsight.indicator.model.vo.CategorySeqUpdateVO;
import com.graphinsight.indicator.model.vo.CategoryTree;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.CollectionUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * <p>
 * 分类表 前端控制器
 * </p>
 *
 * @author lixiaolong
 * @since 2021-11-16
 */
@RestController
@RequestMapping("/category")
public class CategoryController {

    @Autowired
    CategoryManager categoryManager;
    @Autowired
    CategoryMapper categoryMapper;

    @OperateLog
    @PostMapping("/create")
    public Response<Category> create(@RequestBody CategoryCreateVO categoryCreateVO){
        Category category = new Category();
        if (!Objects.isNull(categoryCreateVO.getParentId())){
            //添加的不是一级分类
            Category parentId = categoryMapper.selectById(categoryCreateVO.getParentId());
            if (parentId == null){
                return Response.error("父分类不存在");
            }
            BeanUtils.copyProperties(categoryCreateVO,category);
            category.setRootId(parentId.getRootId());
            categoryMapper.insert(category);
        } else {
            // 添加的是一级分类
            BeanUtils.copyProperties(categoryCreateVO,category);
            categoryMapper.insert(category);
            category.setRootId(category.getId());
            categoryMapper.updateById(category);
        }
        return Response.ok(category);
    }

    @Autowired
    ICategoryService categoryService;

    @OperateLog
    @PostMapping("/sort")
    public Response<List<Category>> sort(@RequestBody CategorySeqUpdateVO categorySeqUpdateVO){
        List<Category> needUpdateList = new ArrayList<>();
        if (!CollectionUtils.isEmpty(categorySeqUpdateVO.getIds())){
            needUpdateList = categoryMapper.selectBatchIds(categorySeqUpdateVO.getIds());
            Map<Integer,Integer> map = new HashMap<>();
            for (int i = 0; i < categorySeqUpdateVO.getIds().size(); i++) {
                map.put(categorySeqUpdateVO.getIds().get(i),i);
            }
            needUpdateList.forEach(c -> {
                c.setSequence(map.get(c.getId()));
            });
        } else if (!CollectionUtils.isEmpty(categorySeqUpdateVO.getCnNames())){
            needUpdateList = categoryMapper.selectList(Wrappers.<Category>lambdaQuery().in(Category::getName,categorySeqUpdateVO.getCnNames()));
            Map<String,Integer> map = new HashMap<>();
            for (int i = 0; i < categorySeqUpdateVO.getCnNames().size(); i++) {
                map.put(categorySeqUpdateVO.getCnNames().get(i),i);
            }
            needUpdateList.forEach(c -> {
                c.setSequence(map.get(c.getName()));
            });
        }
        if (!CollectionUtils.isEmpty(needUpdateList)){
            categoryService.updateBatchById(needUpdateList);
        }
        return Response.ok();
    }

    @OperateLog
    @PostMapping("/update")
    public Response<Category> update(@RequestBody CategoryCreateVO categoryCreateVO){
        if (categoryCreateVO.getId() != null){
            Category category = new Category();
            BeanUtils.copyProperties(categoryCreateVO,category);
            categoryMapper.updateById(category);
            return Response.ok(category);
        }
        return Response.ok("分类ID不存在");
    }

    @OperateLog
    @GetMapping("/delete/{id}")
    public Response delete(@PathVariable("id") Integer id,@RequestParam(value = "traceId",required = false) String traceId){
        int i = categoryMapper.deleteById(id);
        return Response.ok(i);
    }

    @ApiOperation("获取分类信息")
    @PostMapping("/tree")
    public Response<List<CategoryTree>> listCategory(@RequestBody CategoryQueryVO categoryQueryVO){
        List<CategoryTree> result = categoryManager.getTree(categoryQueryVO);
        return Response.ok(result);
    }

    @GetMapping("/children/{id}")
    public Response<List<Integer>> getChildren(@PathVariable Integer id,@RequestParam(value = "traceId",required = false) String traceId){
        List<Integer> leafIdById = categoryManager.findLeafIdById(id);
        return Response.ok(leafIdById);
    }

}
