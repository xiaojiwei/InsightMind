package com.graphinsight.indicator.controller;

import com.graphinsight.indicator.enums.AuthObjectType;
import com.graphinsight.indicator.manager.PostManager;
import com.graphinsight.indicator.model.Response;
import com.graphinsight.indicator.model.post.Post;
import com.graphinsight.indicator.model.post.PostEmp;
import com.graphinsight.indicator.model.vo.OrganizationVO;
import io.swagger.annotations.ApiOperation;
import org.apache.commons.collections.CollectionUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Date: 2023/10/30
 * Desc: 岗位信息控制器
 */
@RestController
@RequestMapping("/post")
public class PostController {

    @Resource
    PostManager postManager;

    @ApiOperation("获取岗位列表信息")
    @GetMapping("/list")
    public Response<List<OrganizationVO>> pageObjects(String searchText){
        List<Post> posts = postManager.listAvaiablePost(searchText);
        if (CollectionUtils.isEmpty(posts)) {
            return Response.ok();
        }
        List<OrganizationVO> result = posts.stream().map(a -> new OrganizationVO(AuthObjectType.POST, a.getPostCode(), a.getPostName(), 0, "")).collect(Collectors.toList());
        return Response.ok(result);
    }


    @ApiOperation("获取岗位列表信息")
    @GetMapping("/get/postInfo/{username}")
    public Response<PostEmp> postInfo(@PathVariable String username){
        PostEmp postInfo = postManager.getPostInfo(username);
        return Response.ok(postInfo);
    }

    @ApiOperation("获取岗位用户列表")
    @GetMapping("/listEmpByPost")
    public Response<List<PostEmp>> empListByCode(String postCode){
        List<PostEmp> postEmps = postManager.listPostEmpInfo(postCode);
        return Response.ok(postEmps);
    }
}
