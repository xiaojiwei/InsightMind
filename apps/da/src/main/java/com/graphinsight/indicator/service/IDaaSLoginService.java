package com.graphinsight.indicator.service;

import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.graphinsight.indicator.controller.LoginController;
import com.graphinsight.indicator.model.IDaaSUserInfo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

@Service
@Slf4j
public class IDaaSLoginService {

//    @Value("${idassHost}")
//    private String idaasHost;

    @Autowired
    private RestTemplate httpRestTemplate;

    @Value("${idassHost}")
    private String idaasHost;

    public IDaaSUserInfo getIdpUserInfo(LoginController.IDaaSLoginReq req){

        String accessToken = req.getToken();
        HttpHeaders headers = new HttpHeaders();
        headers.add("Authorization","Bearer "+accessToken);
        log.info("尝试从idaas获取用户{}的信息",req.getUserInfo());
        log.info("url{}", idaasHost+"/bindings/LI_LDAP/");
        ResponseEntity<JSONObject> exchange;
        try {
            exchange = httpRestTemplate.exchange(idaasHost+"/bindings/LI_LDAP/", HttpMethod.GET, new HttpEntity<>(headers), JSONObject.class);
        }catch (HttpClientErrorException e){
            try {
                exchange = httpRestTemplate.exchange(idaasHost+"/bindings/LI_HELPER/", HttpMethod.GET, new HttpEntity<>(headers), JSONObject.class);
            }catch (HttpClientErrorException exception){
                exchange = httpRestTemplate.exchange(idaasHost+"/bindings/LI_PARTNER/", HttpMethod.GET, new HttpEntity<>(headers), JSONObject.class);
            }
        }
        JSONObject res = exchange.getBody();
        if (res == null) throw new RuntimeException("请求" + idaasHost+"/bindings/" + "接口异常:" + res.toJSONString());
        if (StringUtils.isBlank(res.getString("type"))){
            res.put("type",IDaaSUserInfo.LI_PARTNER);
        }else if(res.get("type").equals("staff")){
            res.put("type", IDaaSUserInfo.LI_LDAP);
        }else {
            res.put("type", IDaaSUserInfo.LI_HELPER);
        }
        // log.info("获取用户{}信息成功: {}",req.getUserInfo(),res.toJSONString());
        IDaaSUserInfo userInfo = res.toJavaObject(IDaaSUserInfo.class);
        log.info("获取用户{}信息成功: {} {}",req.getUserInfo(),userInfo.getJobNumber(),res.getString("ldap_name"));
        userInfo.setFeishuUserId(res.getString("feishu_user_id"));
        //分别处理正式员工与外援信息
        if(userInfo.getType() == IDaaSUserInfo.LI_LDAP){
            userInfo.setDepartmentNamePath(res.getJSONObject("department").getString("name_path"));
            userInfo.setUsername(res.getString("ldap_name"));
        }else if (userInfo.getType() == IDaaSUserInfo.LI_HELPER){
            //外援用idaas返回的id作为唯一标识，对应正式员工域账号
            userInfo.setUsername(res.getString("id"));
            userInfo.setEmail(res.getString("email"));
        }else {
            //合作伙伴用idaas返回的user_id作为唯一标识，对应正式员工域账号,加前缀区分外援用户
            userInfo.setUsername("2_"+res.getString("user_id"));
            if (res.getString("email") == null) userInfo.setEmail("");
        }
        if (userInfo.getType()==IDaaSUserInfo.LI_PARTNER){
            //为合作伙伴添加部门相关字段
            ResponseEntity<JSONObject> response = httpRestTemplate.exchange(
                    "https://da-indicator.prod.k8s.chehejia.com/indicator/api/v1/emoloyee/getByCode/"+res.getString("user_id"),
                    HttpMethod.GET,
                    new HttpEntity<>(headers), JSONObject.class);
            JSONObject body = response.getBody().getJSONObject("data");
//            System.out.println(body);
            if (body == null) throw new RuntimeException("请求indicator接口异常,用户信息为: "+userInfo);
            String namePath = body.getString("namePath");
            Integer orgCode = body.getInteger("orgCode");
            System.out.println(namePath);
            System.out.println(orgCode);
            if (StringUtils.isBlank(namePath) || orgCode == null) throw new RuntimeException("请求indicator接口异常,用户信息为: "+userInfo);
            userInfo.setDepartmentNamePath(namePath);
            userInfo.setDepartmentId(orgCode);
        }
        return userInfo;
    }

}

