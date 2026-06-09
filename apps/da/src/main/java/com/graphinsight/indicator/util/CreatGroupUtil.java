package com.graphinsight.indicator.util;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.graphinsight.indicator.auto.entity.Customer;
import com.graphinsight.indicator.auto.entity.Portal;
import com.graphinsight.indicator.auto.entity.User;
import com.graphinsight.indicator.auto.service.ICustomerService;
import com.graphinsight.indicator.auto.service.IPortalService;
import com.graphinsight.indicator.auto.service.IUserService;
import com.graphinsight.indicator.model.vo.PortalVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Component
public class CreatGroupUtil {
    @Autowired
    RestTemplate restTemplate = new RestTemplate();

    @Autowired
    IUserService userService;

    @Autowired
    IPortalService portalService;

    @Autowired
    ICustomerService customerService;

    public void creatFeishuGroup(Long id, User user) {
        String str = "创建群聊成功";
        Portal portal = portalService.getById(id);
        if (portal == null) {
            log.info("该目录id不存在");
            return;
        }
        String msg = portal.getMsg();
        List<Customer> customers = customerService.list(Wrappers.<Customer>lambdaQuery().eq(Customer ::getPortalId, id));
        List<String> list = new ArrayList<>();
        if(!CollectionUtils.isEmpty(customers)) {
            list = customers.stream().map(a -> a.getName()).collect(Collectors.toList());
        }
        List<User> users = userService.list(Wrappers.<User>lambdaQuery().in(User ::getUsername, list));
        users.add(user);
        List<String> userIds = users.stream().map(a -> a.getFeishuUserId()).collect(Collectors.toList());
        creatGroupAndsendMsg(msg,userIds);
    }

    public void creatGroupAndsendMsg(String msg, List<String> customers) {
        String chatId = creatGroup();
//        List<String> userId = getUserId(customers);
        invitMembers(chatId, customers);
        if (StringUtils.hasLength(msg)) {
            sendMsg(chatId, msg);
        }
    }

    public String creatGroup() {
        log.info("开始创建群聊");
        String url = "https://open.feishu.cn/open-apis/im/v1/chats";
        HttpHeaders headers = new HttpHeaders();
        JSONObject params = new JSONObject();
        String token = getToken();
        Random random=new Random();
        int rannum= (int)(random.nextDouble()*(99999-10000 + 1))+ 10000;
        headers.add("Authorization", "Bearer " + token);
        headers.add("Content-Type", "application/json; charset=utf-8");
        params.put("avatar", "05e9a67d-cfc6-4693-a204-ab99120e188g");
        params.put("name", "数据门户-人工服务-"+ rannum);
        params.put("description", "数据门户-人工服务。本群致力于为您解决在使用过程中遇到的问题");
        HttpEntity<Map> httpEntity = new HttpEntity<>(params, headers);
        ResponseEntity<JSONObject> responseEntity = restTemplate.exchange(url, HttpMethod.POST, httpEntity, JSONObject.class);
        log.info("完成创建群聊");
        return responseEntity.getBody().getJSONObject("data").getString("chat_id");
    }

    public String getToken() {
        String upurl = "https://open.feishu.cn/open-apis/auth/v3/app_access_token/internal/";
        String app_id = "cli_a5ac9d93fefad00e";
        String app_secret = "Ur5GBLIOyjZlJmP9oA3POsSCH0bUhMBW";
        //返回
        ResponseEntity<JSONObject> responseEntity;
        //构造请求头
        HttpHeaders headers = new HttpHeaders();
        MultiValueMap<String, Object> params = new LinkedMultiValueMap<>();
        params.add("app_id", app_id);
        params.add("app_secret", app_secret);
        HttpEntity<Map> httpEntity = new HttpEntity<>(params, headers);
        responseEntity = restTemplate.exchange(upurl, HttpMethod.POST, httpEntity, JSONObject.class);
        return responseEntity.getBody().getString("tenant_access_token");
    }

    public void invitMembers(String chatId, List<String> userId) {
        log.info("开始邀请群成员");
        String url = "https://open.feishu.cn/open-apis/im/v1/chats/" + chatId + "/members" + "?member_id_type=user_id";
        HttpHeaders headers = new HttpHeaders();
        JSONObject params = new JSONObject();
        String token = getToken();
        headers.add("Authorization", "Bearer " + token);
        headers.add("Content-Type", "application/json; charset=utf-8");
        params.put("id_list", userId);
        log.info(params.toJSONString());
        HttpEntity<Map> httpEntity = new HttpEntity<>(params, headers);
        ResponseEntity<JSONObject> responseEntity = restTemplate.exchange(url, HttpMethod.POST, httpEntity, JSONObject.class);
        log.info(responseEntity.getBody().toJSONString());
    }

    public void sendMsg(String chatId, String msg) {
        log.info("开始发送消息");
        String url = "https://open.feishu.cn/open-apis/im/v1/messages" + "?receive_id_type=chat_id";
        HttpHeaders headers = new HttpHeaders();
        JSONObject params = new JSONObject();
        String token = getToken();
        headers.add("Authorization", "Bearer " + token);
        headers.add("Content-Type", "application/json; charset=utf-8");
        params.put("receive_id", chatId);
        params.put("content", new JSONObject().fluentPut("text", msg).toJSONString());
        params.put("msg_type", "text");
        HttpEntity<Map> httpEntity = new HttpEntity<>(params, headers);
        log.info(params.toJSONString());
        ResponseEntity<JSONObject> responseEntity = restTemplate.exchange(url, HttpMethod.POST, httpEntity, JSONObject.class);
        log.info(responseEntity.getBody().toJSONString());
    }

//    public List<String> getUserId(List<String> emails) {
//        log.info("开始取得userid");
//        StringBuffer sb = new StringBuffer();
//        for(String email : emails) {
//            sb.append("mobiles=" + email + "&");
//        }
//        List<String> userIds = new ArrayList<>();
//        String url = "https://open.feishu.cn/open-apis/user/v1/batch_get_id?" + sb;
//        HttpHeaders headers = new HttpHeaders();
//        JSONObject params = new JSONObject();
//        String token = getToken();
//        headers.add("Authorization", "Bearer " + token);
//        headers.add("Content-Type", "application/json; charset=utf-8");
//        HttpEntity<Map> httpEntity = new HttpEntity<>(params, headers);
//        ResponseEntity<JSONObject> responseEntity = restTemplate.exchange(url, HttpMethod.GET, httpEntity, JSONObject.class);
//        log.info(responseEntity.getBody().toJSONString());
//        JSONObject res = responseEntity.getBody();
//        JSONObject data = res.getJSONObject("data");
//        JSONObject emailUsers = data.getJSONObject("mobile_users");
//        for(String email : emails) {
//            if(!CollectionUtils.isEmpty(emailUsers.getJSONArray(email))) {
//                userIds.add(emailUsers.getJSONArray(email).getJSONObject(0).getString("user_id"));
//            }
//        }
//        JSONArray emailsNotExist = data.getJSONArray("mobiles_not_exist");
//        log.info("未成功转换邮箱信息{}", emailsNotExist);
//        return userIds;
//    }
}
