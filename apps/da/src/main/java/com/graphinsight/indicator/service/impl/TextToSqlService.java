package com.graphinsight.indicator.service.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@Slf4j
public class TextToSqlService {

    @Autowired
    private RestTemplate httpRestTemplate;

    @Value("${text-to-sql.authorization:}")
    private String authorization;

    public String textToSql(String text, String ddl) {
        String content = genContent(text, ddl);
        String url = "http://mindgpt.ssai-apis.chj.cloud/open/v1/aglite/message";
        JSONObject req = new JSONObject();
        req.put("conversation_id", "416");
        JSONObject input = new JSONObject();
        input.put("text", content);
        req.put("input", input);
        HttpHeaders headers = new HttpHeaders();
        headers.add("Content-Type", "application/json");
        headers.add("Authorization", authorization);
        try {
            ResponseEntity<Resource> responseEntity = httpRestTemplate.exchange(url, HttpMethod.POST, new HttpEntity<>(req.toJSONString(), headers), Resource.class);
            // 处理响应输出流
            Resource responseStream = responseEntity.getBody();
            BufferedReader reader = new BufferedReader(new InputStreamReader(responseStream.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                JSONObject jsonObject = JSON.parseObject(line);
                if (jsonObject.getBoolean("is_final")) {
                    String sql = getSql(jsonObject.getString("text"));
                    log.info("text:{},ddl:{},sql:{}", text, ddl, sql);
                    return sql;
                }
            }
        } catch (Exception e) {
            log.error("textToSql失败，text:{},ddl:{}", text, ddl, e);
        }
        return null;
    }

    private String genContent(String text, String ddl) {
        String content = "- Role: 您是一个专业的数据分析师，帮助生成SQL语句\n" +
                "- Background: 用户需要使用你生成可执行的sql，用于数据查询\n" +
                "- Profile: 你是一个sql生成器，能根据用户输入的ddl和需求描述生成可执行的sql，首先sql需要高效执行，其次sql在数据库的严格模式下，仍可执行。比如，count 语句，必须有group by 的字段\n" +
                "- Goals: 生成满足用户需求的可执行sql\n" +
                "- Constrains: 第一只生成可执行sql；第二只生成sql，不得违背用户需求和ddl, sql严格执行，不得包含非法sql；\n" +
                "- OutputFormat: markdown语法输出" +
                "ddl: " + ddl + "\n" +
                "用户需求: " + text + "\n";
        return content;
    }

    private String getSql(String line) {
        System.out.println(line);
        Pattern pattern = Pattern.compile("(?<=```sql\n)(.*?)(?=\n```)", Pattern.DOTALL);
        Matcher matcher = pattern.matcher(line);
        // Find and print the matches
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

}
