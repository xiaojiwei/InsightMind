package com.graphinsight.indicator.manager;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.google.common.base.Charsets;
import com.graphinsight.indicator.auto.entity.Department;
import com.graphinsight.indicator.auto.entity.User;
import com.graphinsight.indicator.constant.IndicatorConstant;
import com.graphinsight.indicator.enums.YesNoType;
import com.graphinsight.indicator.model.dto.CoaUserInfo;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSVerifier;
import com.nimbusds.jose.Payload;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.SignedJWT;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;
import org.springframework.web.client.RestTemplate;

import java.io.UnsupportedEncodingException;
import java.text.ParseException;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * @Author: lixiaolong
 * @Description: COA登录
 * @Date: 2021/12/13
 */
@Slf4j
@Service
public class COALoginManager {

    private static final String coaTokenUrl = "https://coa-it.chehejia.com:9528/oauth/token";

    private static final String coaUserInfoUrl = "https://coa-it.chehejia.com:9528/api/info/user/";

    private static final String coaGetUserByJobNumUrl = "https://coa-it.chehejia.com:9528/api/info/user_by_job_number/";

    private static final String coaTicketInfoUrl = "https://coa-it.chehejia.com:9528/api/sso/ticket";

    @Value("${coa.client_id:15}")
    private int client_id;


    @Value("${coa.client_secret:Xgs5ZWBkaKBEwk0Z4iprixhRnd2Q39pA5mZ9vfQv}")
    private String client_secret;

    @Autowired
    private RestTemplate httpRestTemplate;

    public CoaUserInfo checkJwtToken(String token) throws Exception {
        final String accessToken = this.getAccessToken();
        final JSONObject ticketObj = this.getTicket(accessToken);
        //验证coaToken
        String ticket1 = ticketObj.getString("ticket1");
        String ticket2 = ticketObj.getString("ticket2");
        String emailName = null;
        try {
            emailName = this.getEmailName(token, ticket1);
        } catch (Exception e) {
            try {
                emailName = this.getEmailName(token, ticket2);
            } catch (ParseException | JOSEException ex) {
                throw new RuntimeException("无权限");
            }
        }
        final JSONObject userInfoObj = this.getUserInfoByEmailPrefix(emailName);
        final CoaUserInfo coaUserInfo = JSON.parseObject(userInfoObj.toJSONString(), CoaUserInfo.class);
        return coaUserInfo;
    }

    /**
     * 获取用户信息
     *
     * @param emailName
     * @return <pre>
     *
     * </pre>
     */
    public JSONObject getUserInfoByEmailPrefix(String emailName) {
        HttpHeaders headers = new HttpHeaders();
        headers.add("Content-Type", "application/json");
        headers.add("Authorization", getAccessToken());
        String url = coaUserInfoUrl + emailName;
        final ResponseEntity<JSONObject> exchange = httpRestTemplate.exchange(url, HttpMethod.GET, new HttpEntity<>(headers), JSONObject.class);
        final JSONObject res = exchange.getBody();
        if (res == null || res.getInteger("code") != 0) {
            throw new RuntimeException("请求" + url + "接口异常:" + res.toJSONString());
        }
        return res.getJSONObject("data");
    }


    public User getUserInfo(String jobNum){
        HttpHeaders headers = new HttpHeaders();
        headers.add("Content-Type", "application/json");
        headers.add("Authorization", getAccessToken());
        String url = coaGetUserByJobNumUrl + jobNum;
        final ResponseEntity<JSONObject> exchange = httpRestTemplate.exchange(url, HttpMethod.GET, new HttpEntity<>(headers), JSONObject.class);
        final JSONObject res = exchange.getBody();
        if (res == null || res.getInteger("code") != 0) {
            throw new RuntimeException("请求" + url + "接口异常:" + res.toJSONString());
        }
        return Optional.ofNullable(res.getJSONObject("data"))
                .map(data -> data.toJSONString())
                .map(s -> JSON.parseObject(s,CoaUserInfo.class))
                .map(u -> {
                    User user = new User();
                    BeanUtils.copyProperties(u,user);
                    user.setAvailable(u.getIsLeft() ? YesNoType.NO.getCode() : YesNoType.YES.getCode());
                    user.setNickname(u.getName());
                    return user;
                })
                .orElse(null);
    }

    /**
     * 通过token获取emailName
     *
     * @param token
     * @param ticket
     * @return
     * @throws ParseException
     * @throws JOSEException
     */
    private String getEmailName(String token, String ticket) throws ParseException, JOSEException {
        SignedJWT signedJWT = SignedJWT.parse(token);
        JWSVerifier verifier = new MACVerifier(ticket);
        boolean result = signedJWT.verify(verifier);
        if (!result) {
            throw new RuntimeException("token已失效");
        }
        Payload payload = signedJWT.getPayload();
        return JSON.parseObject(payload.toString()).getString("sub");
    }


    /**
     * 获取部门成员
     */
    public List<User> getUsersByDepartment(String departmentId) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.add("Authorization", getAccessToken());
            headers.add("Accept", "application/json");
            headers.add("Content-Type", "application/json");
            HttpEntity httpEntity = new HttpEntity(headers);
            ResponseEntity<JSONObject> js = httpRestTemplate.exchange("https://coa-it.chehejia.com:9528/api/info/department_users/" + departmentId, HttpMethod.GET, httpEntity, JSONObject.class);
            List<CoaUserInfo> userInfos = Optional.ofNullable(js)
                    .map(j -> j.getBody())
                    .map(body -> body.getJSONArray("data"))
                    .map(j -> JSON.parseArray(j.toJSONString(), CoaUserInfo.class))
                    .map(list -> list.stream()
                            .filter(coaUserInfo -> coaUserInfo.getEmail().endsWith("@graphinsight.com") && Boolean.FALSE.equals(coaUserInfo.getIsLeft()))
                            .collect(Collectors.toList()))
                    .orElse(null);
            return userInfos.stream().map(u -> {
                User user = new User();
                BeanUtils.copyProperties(u,user);
                user.setAvailable(u.getIsLeft() ? YesNoType.NO.getCode() : YesNoType.YES.getCode());
                user.setNickname(u.getName());
                return user;
            }).collect(Collectors.toList());
        } catch (Exception e) {
            log.error("同步部门用户异常,部门ID:{}",departmentId,e);
            throw e;
        }
    }

    /**
     * 获取部门信息
     */
    public List<Department> getDepartments() {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.add("Authorization", getAccessToken());
            headers.add("Accept", "application/json");
            headers.add("Content-Type", "application/json");
            HttpEntity httpEntity = new HttpEntity(headers);
            ResponseEntity<JSONObject> js = httpRestTemplate.exchange("https://coa-it.chehejia.com:9528/api/info/departments", HttpMethod.GET, httpEntity, JSONObject.class);
            log.info("获取部门信息调用结果:{}",js.getBody());
            JSONObject cache = js.getBody();
            JSONArray data = cache.getJSONArray("data");

            List<Department> departments = JSON.parseArray(data.toJSONString(), Department.class);
            departments.forEach(department -> {
                Integer deptLevel = Optional.ofNullable(department.getIdPath())
                        .map(path -> path.split("-"))
                        .map(arr -> arr.length)
                        .orElse(null);

                Integer departmentId = Optional.ofNullable(department.getIdPath())
                        .map(path -> path.split("-"))
                        .map(arr -> arr[arr.length - 1])
                        .map(s -> Integer.valueOf(s))
                        .orElse(null);

                Integer parentId = Optional.ofNullable(department.getIdPath())
                        .map(path -> path.split("-"))
                        .filter(arr -> arr.length >= 2)
                        .map(arr -> arr[arr.length - 2])
                        .map(s -> Integer.valueOf(s))
                        .orElse(IndicatorConstant.TOP_DEPT_ID);
                department.setDeptLevel(deptLevel);
                department.setDepartmentId(departmentId);
                department.setParentId(parentId);
                try {
                    department.setCode(DigestUtils.md5DigestAsHex(department.getIdPath().getBytes(Charsets.UTF_8.name())));
                } catch (UnsupportedEncodingException e) {
                    log.error("生成code失败,department:{}",department);
                }
            });
            Department company = new Department();
            company.setDepartmentId(IndicatorConstant.TOP_DEPT_ID);
            company.setFullname("演示汽车公司");
            company.setNamePath("演示汽车公司");
            company.setCompanyId(48);
            company.setDeptLevel(IndicatorConstant.TOP_DEPT_LEVEL);
            company.setIdPath(IndicatorConstant.TOP_DEPT_ID.toString());
            company.setCode(DigestUtils.md5DigestAsHex(company.getIdPath().getBytes(Charsets.UTF_8.name())));
            departments.add(company);
            return departments;
        } catch (Exception e) {
            log.error("获取部门信息异常:{}",e);
            return null;
        }
    }


    /**
     * 获取access_token,在后续的每个api调用时，将 access token放置到 请求的 header 部分
     *
     * @return
     */
    private String getAccessToken() {
        JSONObject req = new JSONObject();
        req.fluentPut("grant_type", "client_credentials").fluentPut("client_id", client_id).fluentPut("client_secret", client_secret).fluentPut("scope", "*");
        HttpHeaders headers = new HttpHeaders();
        headers.add("Content-Type", "application/json");
        final ResponseEntity<JSONObject> exchange = httpRestTemplate.exchange(coaTokenUrl, HttpMethod.POST, new HttpEntity<>(req.toJSONString(), headers), JSONObject.class);
        final JSONObject res = exchange.getBody();
        if (res == null) {
            throw new RuntimeException("请求" + coaTokenUrl + "接口异常:" + res.toJSONString());
        }
        return res.getString("token_type") + " " + res.getString("access_token");
    }

    /**
     * 获取ticket
     *
     * @param coaToken
     * @return <pre>
     *     {
     *         "ticket1": "XXW3j4TNzeY0NGo9c9IEBOzNHxLLBVhtx96RMDtehMYyrCG4WTjFbmlB8ctG61j2QxzVdhLea8VNmv5GhfBM2L",
     *         "ticket2": "XXW3j4TNzeY0NGo9c9IEBOzNHxLLBVhtx96RMDtehMYyrCG4WTjFbmlB8ctG61j2QxzVdhLea8VNmv5GhfBM2L"
     *     }
     * </pre>
     */
    private JSONObject getTicket(String coaToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.add("Content-Type", "application/json");
        headers.add("Authorization", coaToken);
        final ResponseEntity<JSONObject> exchange = httpRestTemplate.exchange(coaTicketInfoUrl, HttpMethod.GET, new HttpEntity<>(headers), JSONObject.class);
        final JSONObject res = exchange.getBody();
        if (res == null || res.getInteger("code") != 0) {
            throw new RuntimeException("请求" + coaTicketInfoUrl + "接口异常:" + res.toJSONString());
        }
        return res.getJSONObject("data");
    }

}
