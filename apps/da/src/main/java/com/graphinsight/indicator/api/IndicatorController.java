package com.graphinsight.indicator.api;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.graphinsight.indicator.auto.entity.Employee;
import com.graphinsight.indicator.auto.entity.OperateGrantConfig;
import com.graphinsight.indicator.auto.entity.Organization;
import com.graphinsight.indicator.auto.entity.User;
import com.graphinsight.indicator.auto.service.IEmployeeService;
import com.graphinsight.indicator.auto.service.IOperateGrantConfigService;
import com.graphinsight.indicator.constant.CacheConstant;
import com.graphinsight.indicator.controller.LoginController;
import com.graphinsight.indicator.enums.OrganizationType;
import com.graphinsight.indicator.exception.IndicatorParamNotValidException;
import com.graphinsight.indicator.manager.COALoginManager;
import com.graphinsight.indicator.manager.OrganizationManager;
import com.graphinsight.indicator.manager.UserGrantContextManager;
import com.graphinsight.indicator.manager.UserManager;
import com.graphinsight.indicator.model.IDaaSUserInfo;
import com.graphinsight.indicator.model.Response;
import com.graphinsight.indicator.model.dto.CoaUserInfo;
import com.graphinsight.indicator.model.dto.OperateGrantValue;
import com.graphinsight.indicator.model.dto.UserQuery;
import com.graphinsight.indicator.model.vo.EmployeeVO;
import com.graphinsight.indicator.model.vo.OrganizationTree;
import com.graphinsight.indicator.service.IDaaSLoginService;
import com.graphinsight.indicator.service.RedisCacheService;
import com.graphinsight.indicator.service.UserService;
import com.graphinsight.indicator.util.UserThreadLocalUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.util.CollectionUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import javax.annotation.Resource;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Author: lixiaolong
 * Date: 2022/6/27
 * Desc: 指标平台对外接口
 */
@Slf4j
@RestController
@RequestMapping("/indicator/api/v1")
public class IndicatorController {

    @Autowired
    IOperateGrantConfigService operateGrantConfigService;
    @Autowired
    UserGrantContextManager userGrantContextManager;
    @Resource
    OrganizationManager organizationManager;
    @Resource
    UserManager userManager;

    @Autowired
    UserService userService;

    @Autowired
    private RestTemplate httpRestTemplate;

    @Value("${idassHost}")
    private String idaasHost;

    @Autowired
    RedisCacheService redisCacheService;

    @Autowired
    COALoginManager coaLoginManager;

    @Autowired
    IDaaSLoginService iDaaSLoginService;

    @GetMapping("/token-exchange")
    public Response exchange(@RequestParam(value = "token",required = true) String token,
                             @RequestParam(value = "userCredentialType",required = false) String userCredentialType){

        User user = null;
        String errorMsg = "";
        if (userCredentialType != null){
            if (userCredentialType.equals("coa")){
                try {
                    CoaUserInfo coaUserInfo = coaLoginManager.checkJwtToken(token);
                    user = userManager.regist(coaUserInfo);
                }catch (Exception e) {
                    errorMsg += "coa解析token失败, errorMsg: " + e.getMessage() + "，";
                    log.info("coa解析token失败,token:{}", token, e);
                }
            }

            if (userCredentialType.equals("IDaaS")){
                try {
                    IDaaSUserInfo iDaaSUserInfo = iDaaSLoginService.getIdpUserInfo(new LoginController.IDaaSLoginReq(token,null));
                    user = userService.idaasRegist(iDaaSUserInfo);
                }catch (Exception exception){
                    log.error("解析token失败，token:{}",token,exception);
                    errorMsg += "IDaaS解析token失败，errorMsg：" + exception.getMessage() + "\n";
                }
            }
        }

        String predix = CacheConstant.TOKEN_EXCHANGE_FOR_PROJECT;
        long st = new Date().getTime();
        redisCacheService.put(predix+st,null,24*60*60);

        Map<String,Object> res = new HashMap<>();
        res.put("stToken",st);

        if (user != null){
            res.put("username",user.getUsername());
            res.put("nickname",user.getNickname());
            res.put("avatar",user.getAvatar());
        }
        return Response.ok(res);
    }


    @RequestMapping(value = "/user/list", method = {RequestMethod.POST})
    public Response<List<User>> listUser(@RequestBody UserQuery userQuery) {
        List<User> users = userManager.listUserByUsernames(userQuery.getUsernames());
        return Response.ok(users);
    }

    @RequestMapping(value = "/emoloyee/getByCode/{code}", method = {RequestMethod.GET})
    public Response<EmployeeVO> getByCode(@PathVariable("code") String code) {
        EmployeeVO vo = organizationManager.getByCode(code);
        return Response.ok(vo);
    }

    @RequestMapping(value = "/emoloyee/search/{searchText}", method = {RequestMethod.GET})
    public Response<List<EmployeeVO>> getTree(@PathVariable("searchText") String searchText) {
        List<EmployeeVO> employeeVOS = organizationManager.searchEmployee(searchText);
        return Response.ok(employeeVOS);
    }

    @RequestMapping(value = "/org/listSuperiorByOrg/{orgCode}/{orgType}", method = {RequestMethod.GET})
    public Response<String> listSuperiorByOrg(@PathVariable("orgCode") String orgCode,
                                              @PathVariable("orgType") Integer orgType) {
        OrganizationType organizationType = OrganizationType.findByInt(orgType).orElse(null);
        if (organizationType == null) {
            return Response.ok();
        }
        LinkedList<Organization> organizations = organizationManager.listSuperiorOrg(orgCode, organizationType);
        if (Objects.equals(OrganizationType.OPERATE, organizationType)) {
            Organization organization = organizations.get(0);
            organization.setOrgName("运营架构");
        }

        if (CollectionUtils.isEmpty(organizations)) {
            return Response.ok();
        }
        Map<String, Object> result = new HashMap<>();
        String namePath = organizations.stream().map(Organization::getOrgName).collect(Collectors.joining("-"));
        result.put("namePath", namePath);
        return Response.ok(result);
    }


    @RequestMapping(value = "/emoloyee/listByOrg/{orgCode}/{orgType}", method = {RequestMethod.GET})
    public Response<List<EmployeeVO>> listByOrg(@PathVariable("orgCode") String orgCode,
                                                @PathVariable("orgType") Integer orgType) {
        OrganizationType organizationType = OrganizationType.findByInt(orgType).orElseThrow(() -> IndicatorParamNotValidException.error("组织类型不合法"));
        List<EmployeeVO> employeeVOS = organizationManager.listEmployeeByOrgCode(orgCode, organizationType);
        return Response.ok(employeeVOS);
    }

    @RequestMapping(value = "/org/tree/{orgType}", method = {RequestMethod.GET})
    public Response<OrganizationTree> getTree(@PathVariable("orgType") Integer orgType) {
        OrganizationType organizationType = OrganizationType.findByInt(orgType).orElseThrow(() -> IndicatorParamNotValidException.error("组织类型不合法"));
        OrganizationTree tree = organizationManager.getTree(organizationType);
        return Response.ok(tree);
    }

    @GetMapping("/org/tree/{orgType}/{orgCode}")
    public Response<List<Organization>> getTree(@PathVariable("orgCode") String orgCode, @PathVariable("orgType") Integer orgType){
        OrganizationType organizationType = OrganizationType.findByInt(orgType).orElseThrow(() -> IndicatorParamNotValidException.error("组织类型不合法"));
        List<Organization> organizations;
        try {
            organizations = organizationManager.listSuperiorOrg(orgCode,organizationType);
        }catch (RuntimeException e){
            log.error("接口异常:", e);
            return Response.error(e.getMessage());
        }
        return Response.ok(organizations);
    }

    private static Cache<Object, Object> MEM_CACHE = CacheBuilder.newBuilder()
            .initialCapacity(10000)
            .concurrencyLevel(20)
            .expireAfterAccess(8, TimeUnit.HOURS)
            .build();

    @RequestMapping(value = "/list/user/variable", method = {RequestMethod.GET, RequestMethod.POST})
    public Response<List<OperateGrantConfig>> listVariable() {

        String key = "operateGrantConfigs";
        Object operaValue = MEM_CACHE.getIfPresent(key);
        List<OperateGrantConfig> operateGrantConfigs = null;
        try {
            if (null == operaValue) {
                operateGrantConfigs = operateGrantConfigService.list();
                MEM_CACHE.put(key, operateGrantConfigs);
            } else {
                operateGrantConfigs = (List<OperateGrantConfig>) operaValue;
            }
            if (CollectionUtils.isEmpty(operateGrantConfigs)) {
                return Response.ok(Collections.EMPTY_LIST);
            }
        } catch (RuntimeException e) {
            log.error("接口异常:", e);
            return Response.error(e.getMessage());
        }
        return Response.ok(operateGrantConfigs);
    }


    @GetMapping(value = "/list/user/context/{username}")
    public Response<List<OperateGrantValue>> listUserContext(@PathVariable("username") String username) {
        if (username.contains("@")) {
            username = username.substring(0, username.indexOf('@'));
        }
        log.info("username is {}", username);
        String longUserName = UserThreadLocalUtil.getUserName();
        log.info("loging user is {}", longUserName);
//        if (null == longUserName || "anonymous".equalsIgnoreCase(longUserName) ) {
//            return Response.error("用户未登录");
//        }
        List<OperateGrantConfig> operateGrantConfigs = operateGrantConfigService.list();
        if (CollectionUtils.isEmpty(operateGrantConfigs)) {
            return Response.ok(Collections.EMPTY_LIST);
        }
        List<OperateGrantValue> operateGrantValues = null;
        try {

            String finalUsername = username;
            operateGrantValues = operateGrantConfigs.stream()
                    .map(operateGrantConfig -> userGrantContextManager.getOperateGrantValue(finalUsername, operateGrantConfig.getId()))
                    .filter(operateGrantValue -> Objects.nonNull(operateGrantValue))
                    .collect(Collectors.toList());
        } catch (RuntimeException e) {
            log.error("接口异常:", e);
            return Response.error(e.getMessage());
        }
        return Response.ok(operateGrantValues);
    }

    @GetMapping(value = "/get/user/context/{username}/{id}")
    public Response<OperateGrantValue> getUserContextById(@PathVariable("username") String username,
                                                          @PathVariable("id") Long id) {
        log.info("username is {} id {}", username, id);
        String longUserName = UserThreadLocalUtil.getUserName();
        if (null == longUserName) {
            return Response.error("用户未登录");
        }

        if (username.contains("@")) {
            username = username.substring(0, username.indexOf('@'));
        }
        OperateGrantValue result = new OperateGrantValue();
        OperateGrantConfig operateGrantConfig = operateGrantConfigService.getById(id);
        if (Objects.isNull(operateGrantConfig)) {
            return Response.ok(result);
        }
        try {
            result = userGrantContextManager.getOperateGrantValue(username, operateGrantConfig.getId());
        } catch (RuntimeException e) {
            log.error("接口异常:", e);
            return Response.error(e.getMessage());
        }
        return Response.ok(result);
    }


    @Autowired
    IEmployeeService iEmployeeService;

    @PostMapping(value = "/update/employee")
    public Response updateEmployee(@RequestBody JSONObject jsonObject){

        new Thread(new Runnable() {
            @Override
            public void run() {
                HashMap<String,Organization> orgs = new HashMap<>();
                List<String> oldInfos = new LinkedList<>();
                List<Employee> newInfos = new LinkedList<>();
                JSONArray employeeList = jsonObject.getJSONArray("employee");
                for (Object obj:employeeList){
                    try {
                        JSONObject user = JSONObject.parseObject(JSONObject.toJSONString(obj));
                        oldInfos.add(user.getString("employeePbAccountId"));
                        JSONArray deptInfos = user.getJSONArray("deptPbResList");
                        if (deptInfos != null) {
                            for (Object dept : deptInfos) {
                                JSONObject deptInfo = JSONObject.parseObject(JSONObject.toJSONString(dept));
                                Employee employee = new Employee();
                                employee.setUsername(user.getString("employeePbAccountId"));
                                employee.setNickname(user.getString("employeeName"));
                                employee.setEmail(user.getString("employeeEmail"));
                                employee.setAvatar(user.getString("avatar"));
                                if (employee.getAvatar() == null) employee.setAvatar("");
                                employee.setEmployeeType(user.getInteger("employeePbType"));
                                employee.setJobNum(user.getString("employeeNo"));

                                employee.setAvailable(1);
                                employee.setBizType(0);
                                employee.setOffduty(0);

                                employee.setOrgCode(deptInfo.getString("deptId"));
                                employee.setOrgType(deptInfo.getInteger("orgPbType"));


                                if (!orgs.containsKey(deptInfo.getString("deptId"))) {
                                    Organization organization = new Organization();
                                    organization.setBizType(0);
                                    organization.setOrgType(1);
                                    organization.setParentCode(deptInfo.getString("parentId"));
                                    organization.setOrgName(deptInfo.getString("deptName"));
                                    organization.setOrgCode(deptInfo.getString("deptId"));
                                    organization.setDeptType(0);
                                    if (organization.getOrgName()!=null && organization.getOrgCode()!=null){
                                        orgs.put(organization.getOrgCode(), organization);
                                    }
                                }
                                if (employee.getUsername()!=null && !employee.getUsername().startsWith("xxx")
                                        && deptInfo.getString("deptName")!=null && deptInfo.getString("deptId")!=null
                                        && employee.getEmployeeType()!=null
                                ){
                                    newInfos.add(employee);
                                }
                            }
                        }
                    }catch (Exception e){
                        log.error("处理用户信息失败，employee",obj,e);
                    }
                }
                iEmployeeService.updateEmployee(oldInfos,newInfos);
                iEmployeeService.updateOrganization(orgs);
            }
        }).start();

        return Response.ok();
    }






    public static void main(String[] args) {
        String a = "abc@111";

        if (a.contains("@")) {
            a = a.substring(0, a.indexOf('@'));
        }
        System.out.println(a);
    }

}
