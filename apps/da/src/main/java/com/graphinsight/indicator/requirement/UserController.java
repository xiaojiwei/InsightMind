package com.graphinsight.indicator.requirement;


import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.dynamic.datasource.toolkit.DynamicDataSourceContextHolder;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.graphinsight.indicator.auto.entity.SearchUser;
import com.graphinsight.indicator.auto.entity.User;
import com.graphinsight.indicator.auto.mapper.UserMapper;
import com.graphinsight.indicator.constant.CacheConstant;
import com.graphinsight.indicator.enums.JdbcDataSourceType;
import com.graphinsight.indicator.manager.COALoginManager;
import com.graphinsight.indicator.manager.UserManager;
import com.graphinsight.indicator.model.Page;
import com.graphinsight.indicator.model.Response;
import com.graphinsight.indicator.model.dto.CoaUserInfo;
import com.graphinsight.indicator.model.vo.UserQueryVO;
import com.graphinsight.indicator.service.RedisCacheService;
import com.graphinsight.indicator.util.NumberFormatUtil;
import com.graphinsight.indicator.util.StringUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.*;
import java.util.stream.Collectors;

/**
 * <p>
 *  前端控制器
 * </p>
 *
 * @author lixiaolong
 * @since 2021-12-13
 */
@RestController
@RequestMapping("/user")
@Slf4j
public class UserController {


    @Autowired
    private UserMapper userMapper;

    @Autowired
    private RedisTemplate redisTemplate;

    @Autowired
    private RestTemplate httpRestTemplate;

    @PostMapping("/list")
    public Response<List<User>> listUser(@RequestBody UserQueryVO userQueryVO,
                                         HttpServletRequest request){

        if (userQueryVO == null){
            return Response.ok(Collections.EMPTY_LIST);
        }

        //提供给项目系统调用，st鉴权
        String st = request.getHeader("stToken");
//        if (st != null){
//            String key  = CacheConstant.TOKEN_EXCHANGE_FOR_PROJECT+st;
//            Object o = redisTemplate.boundValueOps(key).get();
//            if (Objects.isNull(o)) {
//                log.info("stToken已失效，stToken:{}",st);
//                return Response.error("stToken失效",400,null);
//            }
//        }

        List<User> users = userMapper.selectList(Wrappers.<User>lambdaQuery()
                .like(User::getNickname, userQueryVO.getUsername())
                .or()
                .like(User::getUsername, userQueryVO.getUsername()));
        return Response.ok(users);
    }

    @Resource
    UserManager userManager;

    @Autowired
    COALoginManager coaLoginManager;

    @GetMapping("/get")
    public Response<SearchUser> getUser(
                                        @RequestParam("identifier") String identifier,
                                        @RequestParam(value = "userCredentialType") String userCredentialType,
                                        HttpServletRequest request) throws Exception {
        Response response = null;
        try {
            if (userCredentialType.equalsIgnoreCase("email")){
                List<SearchUser> res = getUserByType(identifier,"email");
                if (res.size()==0){
                    return Response.error(403,"查询不到用户");
                }
                if (res.size()>1){
                    return Response.error("无法唯一确定该用户",403,res);
                }
                return Response.ok(res.get(0));
            }else{
                User user = getUser(userCredentialType,identifier);
                SearchUser searchUser = getSearchUserFromCoa(user);
                if (searchUser == null) return Response.error(403,"查询不到用户");
                return Response.ok(searchUser);
            }
        } catch (Exception ex) {
            log.error("查询用户信息失败,identifier:{},userCredentialType:{}",identifier,userCredentialType,ex);
            ex.printStackTrace();
            response = Response.error("查询用户信息失败");
            response.setErrorStackTrace(ex.getStackTrace());
            response.setErrorMessage(ex.toString());
        }
        return response;
    }

    private User getUser(String type, String identifier) throws Exception {
        if (type.equalsIgnoreCase("IDaaS")) {
            // IDaaS SDK 已移除，直接返回 null
            return null;
        }
        if (type.equalsIgnoreCase("coa")) {
            CoaUserInfo coaUserInfo = coaLoginManager.checkJwtToken(identifier);
            return userManager.regist(coaUserInfo);
        }
        return null;
    }

    private SearchUser getSearchUserFromCoa(User user){
        if (user == null) return null;
        String nickname = user.getNickname();
        String username = user.getUsername();
        String email = user.getEmail();
        JSONArray jsonArray = searchUserByType(nickname,"name");
        log.info("根据中文名查询，coa返回用户列表，users：{}",jsonArray);
        //先用域账号确定用户
        List<SearchUser> users = jsonArray.stream().map(e->buildSearchUser(e)).collect(Collectors.toList());
        for (SearchUser searchUser : users){
            if (searchUser.getUsername().equals(username)){
                return searchUser;
            }
        }
        //用email确定用户
        for (SearchUser searchUser : users){
            if (StringUtils.isNotBlank(email) && StringUtils.isNotBlank(searchUser.getEmail()) && email.equals(searchUser.getEmail())){
                return searchUser;
            }
        }
        log.info("无法成功确定用户，nickname:{},username:{},email:{}",nickname,username,email);
        return null;
    }



    @PostMapping("/lists")
    public Response<Set<SearchUser>> listUserByRe(@RequestBody UserQueryVO userQueryVO,
                                         HttpServletRequest request){

        if (userQueryVO == null){
            return Response.ok(Collections.EMPTY_LIST);
        }

        //提供给项目系统调用，st鉴权
        String st = request.getHeader("stToken");
//        if (st != null){
//            String key  = CacheConstant.TOKEN_EXCHANGE_FOR_PROJECT+st;
//            Object o = redisTemplate.boundValueOps(key).get();
//            if (Objects.isNull(o)) {
//                log.info("stToken已失效，stToken:{}",st);
//                return Response.error("stToken失效",400,null);
//            }
//        }
        //从用户表里模糊搜索正式员工、V外援
        List<User> users = userMapper.selectList(Wrappers.<User>lambdaQuery()
                .like(User::getNickname, userQueryVO.getUsername())
                .or()
                .like(User::getUsername, userQueryVO.getUsername()))
                .stream().filter(e->e.getEmail().contains("@graphinsight.com"))
                .collect(Collectors.toList());

        if (users.size() > 60) {
            users = users.stream().limit(10).collect(Collectors.toList());
        }

        //从coa精确查找外援用户
        Set<SearchUser> searchUserSet = searchVendor(userQueryVO.getUsername());

        for (User user : users) {
            SearchUser searchUser = SearchUser.build(user);
            searchUserSet.add(searchUser);
        }

        int i = 1;
        for (SearchUser searchUser : searchUserSet){
            searchUser.setId(i++);
        }
        return Response.ok(searchUserSet);
    }

    private SearchUser buildSearchUser(Object user){
        JSONObject obj = JSON.parseObject(JSONObject.toJSONString(user));
        SearchUser searchUser = new SearchUser();
        searchUser.setUsername(obj.getString("ldap_name"));
        searchUser.setNickname(obj.getString("name"));
        searchUser.setEmail(obj.getString("email"));
        searchUser.setAvatar(obj.getString("avatar"));
        searchUser.setFeishuUserId(obj.getString("feishu_user_id"));
        searchUser.setDepartmentNamePath(obj.getString("department_name_path"));
        searchUser.setDepartmentId(obj.getInteger("department_id"));
        searchUser.setJobNumber(obj.getString("job_number"));
        searchUser.setType(obj.getInteger("user_type"));
        searchUser.setDeptPath(obj.getString("department_id_path"));
        return searchUser;
    }


    private Set<SearchUser> searchVendor(String keyword){
        Set<SearchUser> res = new HashSet<>();
        try {
            JSONArray users = searchUserByType(keyword,"name");
            users.addAll(searchUserByType(keyword,"email"));
            for (Object user : users){
                JSONObject obj = JSON.parseObject(JSONObject.toJSONString(user));
                if (obj.getInteger("user_type") != 1 && !obj.getString("job_number").startsWith("V")){
                    SearchUser searchUser = buildSearchUser(user);
                    res.add(searchUser);
                }
            }
            return res;
        }catch (Exception e){
            log.error("从coa搜索用户失败，keyword：{}",keyword,e);
        }
        return res;
    }

    private String getCoaToken(){
        String coaTokenUrl = "https://coa-api.it.lixiangoa.com/oauth/token";
        String clientId = "176";
        String clientSecret = "coqot2hJvSIo9MOINm4sDYUGBcpTHFVTPA6e33go";
        String token;
        JSONObject req = new JSONObject();
        req.fluentPut("grant_type", "client_credentials").fluentPut("client_id", clientId).fluentPut("client_secret", clientSecret).fluentPut("scope", "*");
        HttpHeaders headers = new HttpHeaders();
        headers.add("Content-Type", "application/json");
        final ResponseEntity<JSONObject> exchange = httpRestTemplate.exchange(coaTokenUrl, HttpMethod.POST, new HttpEntity<>(req.toJSONString(), headers), JSONObject.class);
        final JSONObject res = exchange.getBody();
        if (res == null) {
            throw new RuntimeException("请求" + coaTokenUrl + "接口异常:" + res.toJSONString());
        }
        token = res.getString("token_type") + " " + res.getString("access_token");
        return token;
    }

    private JSONArray searchUserByType(String keyword,String type){
        HttpHeaders headers = new HttpHeaders();
        headers.add("Content-Type", "application/json");
        headers.add("Authorization", getCoaToken());
        String url = "https://coa-api.it.lixiangoa.com/api/info/search_in_all_user?" + "search_type=" + type + "&search_text=" + keyword;
        final ResponseEntity<JSONObject> exchange = httpRestTemplate.exchange(url, HttpMethod.GET, new HttpEntity<>(headers), JSONObject.class);
        final JSONObject res = exchange.getBody();
        if (res == null) {
            throw new RuntimeException("请求" + url + "接口异常:" + res.toJSONString());
        }
        JSONArray jsonArray = res.getJSONArray("data");
        return jsonArray;
    }

    private List<SearchUser> getUserByType(String keyword,String type){
        List<SearchUser> res = new LinkedList<>();
        try {
            JSONArray jsonArray = searchUserByType(keyword,type);
            for (Object user : jsonArray){
                SearchUser searchUser = buildSearchUser(user);
                res.add(searchUser);
            }
        }catch (Exception e){
            log.error("从coa查询用户失败，keyword:{},type:{}",keyword,type,e);
        }
        return res;
    }

}
