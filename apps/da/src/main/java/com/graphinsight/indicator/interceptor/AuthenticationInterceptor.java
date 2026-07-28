package com.graphinsight.indicator.interceptor;

import cn.hutool.jwt.JWT;
import com.alibaba.druid.util.StringUtils;
import com.alibaba.fastjson.JSON;
import com.graphinsight.indicator.auto.entity.User;
import com.graphinsight.indicator.constant.IndicatorConstant;
import com.graphinsight.indicator.constant.TokenConstant;
import com.graphinsight.indicator.manager.BaseTokenService;
import com.graphinsight.indicator.manager.UserManager;
import com.graphinsight.indicator.model.Response;
import com.graphinsight.indicator.model.vo.AiVUserVO;
import com.graphinsight.indicator.util.IDaaSValidateUtilAi;
import com.graphinsight.indicator.util.TokenUtils;
import com.graphinsight.indicator.util.UserThreadLocalUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * @Description:
 * @Date: 2021/12/13
 */
@Slf4j
@Component
public class AuthenticationInterceptor implements HandlerInterceptor {

    @Autowired
    private UserManager userManager;
    @Value("${authSwitch}")
    private String authSwitch;
    @Autowired
    private IDaaSValidateUtilAi iDaaSValidateUtilAi;

    @Autowired
    private BaseTokenService baseTokenService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if (Objects.equals(authSwitch, "off")) {
            User devUser = userManager.getUserByName("dev_user");
            if (devUser == null) {
                devUser = new User();
                devUser.setUsername("dev_user");
                devUser.setNickname("dev_user");
            }
            UserThreadLocalUtil.set(devUser);
            return true;
        }

        String url = request.getRequestURI();
        log.info("拦截URL:{}", request.getRequestURI());

        //对项目系统开放，st校验
        if ("/user/list".equals(url) && request.getHeader("stToken") != null) {
            return true;
        }

        if ("/v3/api-docs.yaml".equals(url)) {
            return true;
        }

        //提供给前端，jiangyunpeng
        if ("/agent/query/data/frontend".equals(url) || "/agent/query/datasource/frontend".equals(url)) {
            return true;
        }

        //对项目系统开放，st校验
        if ("/user/lists".equals(url) && request.getHeader("stToken") != null) {
            return true;
        }

        //对项目系统开放，st校验
        if ("/user/get".equals(url) && request.getHeader("stToken") != null) {
            return true;
        }

        //对性能监控需要的请求不需要鉴权
        if ("/bi/v1/start".equalsIgnoreCase(url) || "/bi/v1/current".equalsIgnoreCase(url) || "/bi/v1/dbsql".equalsIgnoreCase(url)) {
            return true;
        }

        //上传excel模版不需要鉴权
        if ("/Excel/api/downloadFile".equalsIgnoreCase(url)) {
            return true;
        }

        response.setCharacterEncoding("utf-8");
        String token = request.getHeader(TokenConstant.TOKEN_HEADER_STRING);
        if (StringUtils.isEmpty(token) || !token.startsWith(TokenConstant.TOKEN_PREFIX)) {
            response.getWriter().print(JSON.toJSONString(Response.unauthorized()));
            return false;
        }
        String username = "";
        AiVUserVO aiVUserVO = null;
        // 如果url是dataGpt开头的，则使用AI工具箱的鉴权
        if (url.startsWith(IndicatorConstant.DATA_GPT_AI)) {
            aiVUserVO = iDaaSValidateUtilAi.IDaaSUserInfo(token);
            username = aiVUserVO.getLdap_name();
            log.info("user name is:{} ", username);
        } else {
            username = TokenUtils.getUsername(token);
        }

        User user = userManager.getUserByName(username);
        if (null == user) {
            if (null != aiVUserVO && !"".equals(username)) {

                user = new User();
                user.setUsername(username);
                user.setNickname(aiVUserVO.getNickname());
                user.setAvatar(aiVUserVO.getPicture());
                user.setEmail(aiVUserVO.getEmail());
                //
                List<String> userList = Arrays.asList(username.split("_"));
                String userIdStr = "";
                if (userList.size() == 2) {
                    userIdStr = "100" + userList.get(1);
                }
//                user.setUserType("vendor");
                user.setId(Integer.valueOf(userIdStr));
            } else {
                response.getWriter().print(JSON.toJSONString(Response.unauthorized()));
                return false;
            }

        }

        if (token.length() <= 300) {
            if (!TokenUtils.validateToken(token, user)) {
                response.getWriter().print(JSON.toJSONString(Response.unauthorized("token失效")));
                return false;
            }
        }
        UserThreadLocalUtil.set(user);
        request.setAttribute(TokenConstant.CURRENT_USER, user);

        return true;
    }

}
