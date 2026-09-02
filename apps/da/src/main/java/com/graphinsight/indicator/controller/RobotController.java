package com.graphinsight.indicator.controller;

import com.alibaba.fastjson.JSONObject;
import com.graphinsight.indicator.annotation.*;
import com.graphinsight.indicator.auto.entity.*;
import com.graphinsight.indicator.constant.IndicatorConstant;
import com.graphinsight.indicator.model.Response;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/**
 * <p>
 * 指标表 前端控制器
 * </p>
 *
 * @since 2021-11-15
 */
@Slf4j
@RestController
@RequestMapping(IndicatorConstant.API_V1 + "/robot")
public class RobotController {

    @Value("${pbHost}")
    private String pbHost;

    @Value("${pbApiSalt:XEfjXmit7vRi}")
    private String pbApiSalt;

    @GetMapping("/group/create")
    public Response createExp(@CurrentUser User user) {
        try {
            String userName = user.getNickname();
            String email = user.getEmail();
            Integer platform = 2;
            Long timestamp = System.currentTimeMillis();
            String salt = pbApiSalt;
            String authStr = userName + email + platform + timestamp + salt;
            //生成加密参数
            String token = getMD5(authStr, true, 64);

            RestTemplate restTemplate = new RestTemplate();
            ResponseEntity<JSONObject> exchange = restTemplate.exchange(pbHost + "/api/v3/robot/third/group/create?" +
                            "userName=" + user.getNickname() + "&email=" + user.getEmail() + "&platform=" + platform + "&timestamp=" + timestamp + "&token=" + token
                    , HttpMethod.GET, new HttpEntity<>(new HttpHeaders()), JSONObject.class);
            if (exchange.getStatusCode().value() == 200) {
                return Response.ok();
            }
            log.info("拉群失败:{}", exchange);
            return Response.error("拉群失败，请重试");
        } catch (Exception e) {
            log.error("拉群失败，请重试。：{}", e);
        }
        return Response.error("拉群失败，请重试!");
    }


    private static String getMD5(String src, boolean isUpper, Integer bit) {
        String md5 = "";
        try {
            // 创建加密对象
            MessageDigest md = MessageDigest.getInstance("md5");
            if (bit == 64) {
                Base64.Encoder encoder = Base64.getEncoder();
                md5 = encoder.encodeToString(md.digest(src.getBytes(StandardCharsets.UTF_8)));
            } else {
                // 计算MD5函数
                md.update(src.getBytes(StandardCharsets.UTF_8));
                byte b[] = md.digest();
                md5 = byteToString(b);
                if (bit == 16) {
                    String md16 = md5.substring(8, 24);
                    md5 = md16;
                    if (isUpper) {
                        md5 = md5.toUpperCase();
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        if (isUpper) {
            md5 = md5.toUpperCase();
        }
        return md5;
    }

    private static String byteToString(byte[] bytes) {
        int i;
        StringBuffer buffer = new StringBuffer("");
        for (int offset = 0; offset < bytes.length; offset++) {
            i = bytes[offset];
            if (i < 0) {
                i += 256;
            }
            if (i < 16) {
                buffer.append("0");
            }
            buffer.append(Integer.toHexString(i));
        }
        return buffer.toString();
    }
}
