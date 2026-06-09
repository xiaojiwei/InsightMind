package com.graphinsight.indicator.manager;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.graphinsight.indicator.exception.IndicatorParamNotValidException;
import com.graphinsight.indicator.model.post.Post;
import com.graphinsight.indicator.model.post.PostEmp;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Date: 2023/10/30
 * Desc:
 * @author lixiaolong5
 */
@Slf4j
@Service
public class PostManager {

    @Value("${post.domain}")
    private String postDomain;

    @Value("${post.gateway-token:}")
    private String postGatewayToken;

    private static final String POST_LIST_URL = "saos-upm-api/v1/upm-post/v1-1/find-page";
    private static final String POST_FIND_BY_CODE_URL = "saos-upm-api/v1/upm-post/v1-1/find-post-by-code";
//    private static final String POST_EMP_URL = "saos-upm-api/v1/upm-employee/v1-0/find-emp-by-id";
    private static final String POST_EMP_URL = " /saos-upm-api/v1/upm-employee/v1-2/find-emp-by-param";
    private static final String POST_LIST_EMP_BY_POST_URL = "saos-upm-api/v1/upm-employee/v1-1/page-emp-by-post-code";
    @Resource
    RestTemplate restTemplate;


    public List<Post> listAvaiablePost(String searchText) {
        List<Post> posts = httpListPost();
        List<Post> postList = posts.stream().filter(post -> Objects.equals(post.getStatus(), 1)).filter(a-> StringUtils.isEmpty(searchText) || a.getPostName().contains(searchText)).collect(Collectors.toList());
        return postList;
    }

    public Post getPostByCode(String postCode) {
        return httpGetPostInfo(postCode);
    }

    public List<Post> listAllPost() {
        return httpListPost();
    }


    public PostEmp getPostInfo(String userNo) {
        return httpPostInfo(userNo);
    }

    public List<PostEmp> listPostEmpInfo(String postCode) {
        return httpListEmpByPostCode(postCode);
    }

    private Post httpGetPostInfo(String postCode) {
        HttpHeaders headers = new HttpHeaders();
        headers.add("X-CHJ-GWToken", postGatewayToken);
        String url = postDomain + POST_FIND_BY_CODE_URL + "?code=" + postCode;
        try {
            final ResponseEntity<JSONObject> res = restTemplate.exchange(url, HttpMethod.GET, new HttpEntity<>(headers), JSONObject.class);
            Post post = Optional.ofNullable(res)
                    .map(r -> r.getBody())
                    .map(json -> json.getObject("data", Post.class))
                    .orElse(new Post());
            return post;
        } catch (RestClientException e) {
            log.error("根据员工标识获取其岗位信息接口异常:", e);
            IndicatorParamNotValidException.error("根据员工标识获取其岗位信息接口异常：" + e);
        }

        return new Post();
    }

    private PostEmp httpPostInfo(String userNo) {
        HttpHeaders headers = new HttpHeaders();
        headers.add("X-CHJ-GWToken", TOKEN);

        Map<String,Object> param = new HashMap<>();
        param.put("employeeNo", userNo);
        param.put("status", 0);

        String url = postDomain + POST_EMP_URL;
        try {
            final ResponseEntity<JSONObject> res = restTemplate.exchange(url, HttpMethod.POST, new HttpEntity<>(param, headers), JSONObject.class);
            List<PostEmp> postEmps = Optional.ofNullable(res)
                    .map(r -> r.getBody())
                    .map(json -> json.getJSONArray("data").toJavaList(PostEmp.class))
                    .orElse(Collections.emptyList());
            if (postEmps.isEmpty()) {
                return null;
            }
            return postEmps.get(0);
        } catch (RestClientException e) {
            log.error("根据员工标识获取其岗位信息接口异常:", e);
            IndicatorParamNotValidException.error("根据员工标识获取其岗位信息接口异常：" + e);
        }

        return new PostEmp();
    }

    private List<Post> httpListPost() {
        HttpHeaders headers = new HttpHeaders();
        headers.add("Content-Type", "application/json");
        headers.add("X-CHJ-GWToken", TOKEN);

        Map<String,Object> param = new HashMap<>();
        param.put("pageNo", 1);
        param.put("pageSize", 1000);
        try {
            final ResponseEntity<JSONObject> res = restTemplate.exchange(postDomain + POST_LIST_URL, HttpMethod.POST, new HttpEntity<>(param, headers), JSONObject.class );
            log.info("返回结果:{}",res);
            List<Post> posts = Optional.ofNullable(res)
                    .map(r -> r.getBody())
                    .map(json -> json.getJSONObject("data"))
                    .map(data -> data.getJSONArray("results"))
                    .map(array -> array.stream()
                            .map(item -> JSON.parseObject(JSON.toJSONString(item), Post.class))
                            .collect(Collectors.toList()))
                    .orElse(new ArrayList<Post>());
            return posts;
        } catch (RestClientException e) {
            log.error("获取岗位信息接口异常:", e);
            IndicatorParamNotValidException.error("获取岗位信息接口异常：" + e);
        }

        return Collections.emptyList();
    }

    private List<PostEmp> httpListEmpByPostCode(String postCode) {
        HttpHeaders headers = new HttpHeaders();
        headers.add("X-CHJ-GWToken", TOKEN);

        JSONObject body = new JSONObject();
        body.put("pageNo", 1);
        body.put("pageSize", 10000);

        JSONObject param = new JSONObject();
        param.put("postCode", postCode);
        param.put("status", 0);

        body.put("param", param);

        try {
            final ResponseEntity<JSONObject> res = restTemplate.exchange(postDomain + POST_LIST_EMP_BY_POST_URL, HttpMethod.POST, new HttpEntity<>(body, headers), JSONObject.class);
            List<PostEmp> postEmp = Optional.ofNullable(res)
                    .map(r -> r.getBody())
                    .map(json -> json.getJSONObject("data"))
                    .map(data -> data.getJSONArray("results"))
                    .map(a -> a.toJavaList(PostEmp.class))
                    .orElse(Collections.emptyList());
            return postEmp;
        } catch (RestClientException e) {
            log.error("根据岗位Code获取员工列表接口异常:", e);
            IndicatorParamNotValidException.error("根据岗位Code获取员工列表接口异常：" + e);
        }

        return Collections.emptyList();
    }
}
