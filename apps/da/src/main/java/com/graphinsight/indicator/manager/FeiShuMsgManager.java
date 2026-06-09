package com.graphinsight.indicator.manager;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.graphinsight.indicator.model.feishu.*;
import com.graphinsight.indicator.service.RedisCacheService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * @Author: lixiaolong
 * @Description: 飞书消息
 * @Date: 2021/10/13
 */
@Slf4j
@Service
public class FeiShuMsgManager {

    @Autowired
    private RedisCacheService redisCacheService;

    @Autowired
    private RestTemplate restTemplate;

    @Value("${feishu-robot.appId}")
    private String appId;
    @Value("${feishu-robot.appSecret}")
    private String appSecret;
    private final static String PB_ROBOT_TOKEN_REDIS_KEY = "feishu_robot_token_redis_key";
    public final static int TOKEN_INVALID_CODE = 99991663;


    public FeishuCardMessage buildMsg(String title,String context){
        FeishuCardMessage cardMessage = FeishuCardMessage.builder()
                .header(Header.builder().template("green").title(Text.builder().content(title).build()).build())
                .elements(listElements(context))
                .build();
        return cardMessage;
    }

    private List<Field> listFields(String content){
        List<Field> fields = new ArrayList<>();
        Field field = Field.builder()
                .text(Text.builder().content(content)
                        .tag(TagType.LARD_MD.getCode()).build())
                .is_short(true)
                .build();
        fields.add(field);
        return fields;
    }

    private List<Element> listElements(String context){
        List<Element> result = new ArrayList<>();

        Element element = Element.builder().tag(TagType.DIV.getCode())
                .fields(listFields(context))
                .build();

        result.add(element);
        return result;
    }

    public void sendMsgToFeiShuChatGroup(String chatId,String content,boolean retry) throws Exception{
        String url="https://open.feishu.cn/open-apis/im/v1/messages?receive_id_type=chat_id";
        Map<String,Object> param = new HashMap<>();
        param.put("msg_type","interactive");
        param.put("content",content);
        param.put("receive_id",chatId);
        HttpHeaders headers = new HttpHeaders();
        headers.add("Content-Type", "application/json");
        Object token = redisCacheService.get(PB_ROBOT_TOKEN_REDIS_KEY,String.class);
        if (Objects.isNull(token)){
            token = refreshToken();
        }
        headers.add("Authorization",token.toString());
        String s = JSON.toJSONString(param);
        JSONObject jsonObject = restTemplate.postForObject(url, new HttpEntity<>(param, headers), JSONObject.class);
        if (jsonObject == null){
            throw new RuntimeException("发送消息出现异常");
        }
        if (Objects.equals(jsonObject.getInteger("code"),TOKEN_INVALID_CODE)){
            refreshToken();
            log.warn("token失效,请重新刷新token");
            if (retry){
                log.info("token失效,重新发送消息");
                sendTextMessageByEmail(chatId,content,false);
            }
        } else if(!Objects.equals(jsonObject.getInteger("code"),0)){
            log.error("发送消息失败:{}", JSON.toJSONString(jsonObject));
        }
    }

    public void sendTextMessageByEmail(String email,String context,boolean retry) throws Exception {
//        // TODO 测试，暂时写死
//        email = "lixiaolong5@graphinsight.com";
        String url="https://open.feishu.cn/open-apis/im/v1/messages?receive_id_type=email";
        Map<String,Object> param = new HashMap<>();
        param.put("msg_type","interactive");
        param.put("content",context);
        param.put("receive_id",email);
        HttpHeaders headers = new HttpHeaders();
        headers.add("Content-Type", "application/json");
        Object token = redisCacheService.get(PB_ROBOT_TOKEN_REDIS_KEY,String.class);
        if (Objects.isNull(token)){
            token = refreshToken();
        }
        headers.add("Authorization",token.toString());
        String s = JSON.toJSONString(param);
        JSONObject jsonObject = restTemplate.postForObject(url, new HttpEntity<>(param, headers), JSONObject.class);
        if (jsonObject == null){
            throw new RuntimeException("发送消息出现异常");
        }
        if (Objects.equals(jsonObject.getInteger("code"),TOKEN_INVALID_CODE)){
            refreshToken();
            log.warn("token失效,请重新刷新token");
            if (retry){
                log.info("token失效,重新发送消息");
                sendTextMessageByEmail(email,context,false);
            }
        } else if(!Objects.equals(jsonObject.getInteger("code"),0)){
            log.error("发送消息失败:{}", JSON.toJSONString(jsonObject));
        }
    }

    public void sendTextMessageByEmail(String email,String context, String msgType ,boolean retry) throws Exception {
//        // TODO 测试，暂时写死
//        email = "lixiaolong5@graphinsight.com";
        String url="https://open.feishu.cn/open-apis/im/v1/messages?receive_id_type=email";
        Map<String,Object> param = new HashMap<>();
        param.put("msg_type",msgType);
        param.put("content",context);
        param.put("receive_id",email);
        HttpHeaders headers = new HttpHeaders();
        headers.add("Content-Type", "application/json");
        Object token = redisCacheService.get(PB_ROBOT_TOKEN_REDIS_KEY,String.class);
        if (Objects.isNull(token)){
            token = refreshToken();
        }
        headers.add("Authorization",token.toString());
        String s = JSON.toJSONString(param);
        JSONObject jsonObject = restTemplate.postForObject(url, new HttpEntity<>(param, headers), JSONObject.class);
        if (jsonObject == null){
            throw new RuntimeException("发送消息出现异常");
        }
        if (Objects.equals(jsonObject.getInteger("code"),TOKEN_INVALID_CODE)){
            refreshToken();
            log.warn("token失效,请重新刷新token");
            if (retry){
                log.info("token失效,重新发送消息");
                sendTextMessageByEmail(email,context,false);
            }
        } else if(!Objects.equals(jsonObject.getInteger("code"),0)){
            log.error("发送消息失败:{}", JSON.toJSONString(jsonObject));
        }
    }

    public List<ChatGroup> getGroups(){
        String token = refreshToken();
        List<ChatGroup> res = new LinkedList<>();

        String url = "https://open.feishu.cn/open-apis/im/v1/chats";
        HttpHeaders headers = new HttpHeaders();
        headers.add("Content-Type", "application/json");
        headers.add("Authorization",token);

        final ResponseEntity<JSONObject> result = restTemplate.exchange(url, HttpMethod.GET,new HttpEntity<>(headers),JSONObject.class);
        List<JSONObject> groups =  result.getBody().getJSONObject("data").getJSONArray("items").toJavaList(JSONObject.class);
        groups.stream().forEach(e->{
            ChatGroup chatGroup = new ChatGroup();
            chatGroup.setName(e.getString("name"));
            chatGroup.setChatId(e.getString("chat_id"));
            chatGroup.setAvatar(e.getString("avatar"));
            chatGroup.setDescription(e.getString("description"));
            res.add(chatGroup);
        });


        return res;
    }


    public String refreshToken(){
        String url="https://open.feishu.cn/open-apis/auth/v3/tenant_access_token/internal";
        Map<String,String> param = new HashMap();
        param.put("app_id",appId);
        param.put("app_secret",appSecret);
        HttpHeaders headers = new HttpHeaders();
        headers.add("Content-Type", "application/json");
        final ResponseEntity<JSONObject> result = restTemplate.postForEntity(url,new HttpEntity<>(param,headers), JSONObject.class);
        JSONObject resultJSONObject = Optional.ofNullable(result)
                .filter(r -> r.getStatusCodeValue() == 200)
                .map(r -> r.getBody())
                .orElseThrow(() -> new RuntimeException("获取token异常"));
        if (Objects.equals(resultJSONObject.getInteger("code"),0)){
            String tenant_access_token = "Bearer " + resultJSONObject.getString("tenant_access_token");
            Integer expire = resultJSONObject.getInteger("expire");
            // 为了避免网络请求延迟造成的缓存时间差，过期时间缩短5分钟
            expire = expire - 60 * 5;
            redisCacheService.put(PB_ROBOT_TOKEN_REDIS_KEY,tenant_access_token,Long.valueOf(expire), TimeUnit.SECONDS);
            return tenant_access_token;
        } else {
            log.error("获取token失败：", JSON.toJSONString(result));
            throw new RuntimeException("获取token失败:");
        }
    }


}
