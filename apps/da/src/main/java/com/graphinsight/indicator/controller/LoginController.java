package com.graphinsight.indicator.controller;

import com.alibaba.fastjson.JSONObject;
import com.graphinsight.indicator.annotation.AuthIgnore;
import com.graphinsight.indicator.annotation.IgnoreWebLog;
import com.graphinsight.indicator.auto.entity.User;
import com.graphinsight.indicator.auto.entity.UserLoginResult;
import com.graphinsight.indicator.manager.COALoginManager;
import com.graphinsight.indicator.manager.UserManager;
import com.graphinsight.indicator.model.IDaaSUserInfo;
import com.graphinsight.indicator.model.Response;
import com.graphinsight.indicator.model.dto.CoaUserInfo;
import com.graphinsight.indicator.model.vo.UserVO;
import com.graphinsight.indicator.service.IDaaSLoginService;
import com.graphinsight.indicator.service.UserService;
import com.graphinsight.indicator.util.TokenUtils;
import io.swagger.annotations.ApiOperation;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.rmi.ServerException;
import java.util.Objects;

/**
 * @Description: 登录控制器
 * @Date: 2021/12/13
 */
@RequestMapping("/login")
@RestController
@Slf4j
public class LoginController {

    @Autowired
    COALoginManager coaLoginManager;
    @Autowired
    UserManager userManager;

    @Autowired
    private Environment environment;

    @Autowired
    IDaaSLoginService idaasLoginService;

    @Autowired
    private TokenUtils tokenUtils;

    @Autowired
    private UserService userService;

    @GetMapping("/coa/{coaToken}")
    public Response<UserVO> coaLogin(@PathVariable("coaToken") String coaToken){
        if (Objects.isNull(coaToken)){
            return Response.error("token为空");
        }
        CoaUserInfo coaUserInfo = null;
        try {
            coaUserInfo = coaLoginManager.checkJwtToken(coaToken);
        } catch (Exception e) {
            log.error("token校验异常:{}",e);
            return Response.unauthorized("token无效,请重新登录");
        }
        User user = userManager.regist(coaUserInfo);
        String currToken = TokenUtils.generateToken(user);
        UserVO userVO = new UserVO();
        BeanUtils.copyProperties(user,userVO);
        userVO.setToken(currToken);
        return Response.ok(userVO);
    }

    @ApiOperation(value = "idass Login")
    @AuthIgnore
    @IgnoreWebLog
    @PostMapping(value = "IDaaS", consumes = MediaType.ALL_VALUE, produces = MediaType.APPLICATION_JSON_UTF8_VALUE)
    public Response<UserLoginResult> idaasLogin(@RequestBody IDaaSLoginReq req){
        //从idaas服务器获取对应用户信息
        IDaaSUserInfo userInfo = idaasLoginService.getIdpUserInfo(req);
        //新增或更新用户
        User user = null;
        try {
            user = userService.idaasRegist(userInfo);
        } catch (ServerException e) {
            throw new RuntimeException(e);
        }
        //生成token，用于后续登陆
        String currToken = tokenUtils.generateToken(user);
//        //初次登陆，为用户关联组织
//        userService.activateUserNoLogin(currToken, null);

        //返回登陆结果
        UserLoginResult userLoginResult = new UserLoginResult(user);
        String statistic_open = environment.getProperty("statistic.enable");
        if ("true".equalsIgnoreCase(statistic_open)) {
            userLoginResult.setStatisticOpen(true);
        }
        userLoginResult.setToken(currToken);
        return Response.ok(userLoginResult);
//        return ResponseEntity.ok(new ResultMap().success(currToken).payload(userLoginResult));
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class IDaaSLoginReq{
        private String token;

        private JSONObject userInfo;
    }
}
