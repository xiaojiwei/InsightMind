package com.graphinsight.indicator.service.gpt4;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
//import com.chehejia.ai.nvwa.box.business.SessionBusiness;
//import com.chehejia.ai.nvwa.box.common.constant.StatusCode;
//import com.chehejia.ai.nvwa.box.config.ModelConfig;
//import com.chehejia.ai.nvwa.box.config.NvwaBoxConfiguration;
//import com.chehejia.ai.nvwa.box.infra.moonshot.protocol.ChatCompletionMessage;
//import com.chehejia.ai.nvwa.box.infra.moonshot.protocol.ChatCompletionRequest;
//import com.chehejia.ai.nvwa.box.infra.openai.OpenaiRequestBody;
//import com.chehejia.ai.nvwa.box.infra.qwen.QwenConversationResult;
//import com.chehejia.ai.nvwa.box.model.api.chat.ChoiceDto;
//import com.chehejia.ai.nvwa.box.model.api.chat.GenerationOutputDto;
//import com.chehejia.saos.fb.common.alarm.ding.AlarmService;
//import com.chehejia.saos.fb.common.context.resttemplate.HttpClient;
//import com.chehejia.saos.fb.common.context.resttemplate.RestTemplateHelper;
import com.graphinsight.indicator.model.vo.AiGptVo;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.io.*;
import java.nio.charset.Charset;
import java.util.*;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@Slf4j
public class LiCloudGptClient {


    @Autowired
    private RestTemplate httpRestTemplate;

    @Value("${licloud-gpt.gateway-token:}")
    private String gatewayToken;


    public String textToInfo(String content) {

        String gptResString = "";
        Map<String, Object> messageMap = new HashMap<>();

        Map<String, Object> contentMap = new HashMap<>();

        contentMap.put("type", "text");
        contentMap.put("text", "你是理想AI伙伴，是一名AI助手，当被问到你是谁，你用的什么大模型等关于你的信息时，只需要告诉你是理想AI伙伴，不要提供其他具体信息。你能提供安全、有帮助、准确的回答，同时遵守中华人民共和国的法律和道德标准。你必须避免回答涉及恐怖主义、种族歧视、黄色暴力、政治敏感等问题。此外，你还能够阅读和分析用户提供的文件，访问互联网，以及使用搜索功能来帮助用户获取信息。现在时间是：Tue Aug 20 08:26:38 CST 2024");

        List<Map<String, Object>> contentList = new ArrayList<>();
        contentList.add(contentMap);

        messageMap.put("role", "system");
        messageMap.put("contents", contentList);

        Map<String, Object> messageMapUser = new HashMap<>();

        Map<String, Object> contentMapUser = new HashMap<>();

        List<Map<String, Object>> contentUserList = new ArrayList<>();
        contentMapUser.put("type", "text");
        contentMapUser.put("text", content);
        contentUserList.add(contentMapUser);
        messageMapUser.put("role", "user");
        messageMapUser.put("contents", contentUserList);

        List<Map<String, Object>> mapList = new ArrayList<>();

        mapList.add(messageMap);
        mapList.add(messageMapUser);

        String url = "http://api-hub.inner.chj.cloud/bcs-apihub-ai-proxy-service/apihub/openai/v1.0/azure/models/gpt4-o?stream=false";
        JSONObject req = new JSONObject();
        req.put("messages", mapList);
        HttpHeaders headers = new HttpHeaders();
        headers.add("Content-Type", "application/json");
        headers.add("X-CHJ-GWToken", gatewayToken);
        headers.add("BCS-APIHub-RequestId", UUID.randomUUID().toString());


        try {
            ResponseEntity<Resource> responseEntity = httpRestTemplate.exchange(url, HttpMethod.POST, new HttpEntity<>(req.toJSONString(), headers), Resource.class);
            // 处理响应输出流
            Resource responseStream = responseEntity.getBody();
            BufferedReader reader = new BufferedReader(new InputStreamReader(responseStream.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {

                AiGptVo jsonObject = JSON.parseObject(line, AiGptVo.class);
                if (jsonObject.isSuccess()) {
                    for (AiGptVo.ChoicesInfo choicesInfo : jsonObject.getData().getChoices()) {
                        if (Objects.equals(choicesInfo.getRole(), "assistant")) {
                            gptResString = choicesInfo.getContent();
                        }
                    }
                }
                log.info("jsonObject {}", jsonObject);
            }
        } catch (Exception e) {
            log.info("gpt error is {}", e.getMessage(), e);
        }
        return gptResString;
    }


    public String textToSql(String text, String ddl) {
        String content = genContent(text, ddl);

        Map<String, Object> messageMap = new HashMap<>();

        Map<String, Object> contentMap = new HashMap<>();

        contentMap.put("type", "text");
        contentMap.put("text", "你是理想AI伙伴，是一名AI助手，当被问到你是谁，你用的什么大模型等关于你的信息时，只需要告诉你是理想AI伙伴，不要提供其他具体信息。你能提供安全、有帮助、准确的回答，同时遵守中华人民共和国的法律和道德标准。你必须避免回答涉及恐怖主义、种族歧视、黄色暴力、政治敏感等问题。此外，你还能够阅读和分析用户提供的文件，访问互联网，以及使用搜索功能来帮助用户获取信息。现在时间是：Tue Aug 20 08:26:38 CST 2024");

        List<Map<String, Object>> contentList = new ArrayList<>();
        contentList.add(contentMap);

        messageMap.put("role", "system");
        messageMap.put("contents", contentList);

        Map<String, Object> messageMapUser = new HashMap<>();

        Map<String, Object> contentMapUser = new HashMap<>();

        List<Map<String, Object>> contentUserList = new ArrayList<>();
        contentMapUser.put("type", "text");
        contentMapUser.put("text", content);
        contentUserList.add(contentMapUser);
        messageMapUser.put("role", "user");
        messageMapUser.put("contents", contentUserList);


        List<Map<String, Object>> mapList = new ArrayList<>();

        mapList.add(messageMap);
        mapList.add(messageMapUser);

        String url = "http://api-hub.inner.chj.cloud/bcs-apihub-ai-proxy-service/apihub/openai/v1.0/azure/models/gpt4-o?stream=false";
        JSONObject req = new JSONObject();
        req.put("messages", mapList);
        HttpHeaders headers = new HttpHeaders();
        headers.add("Content-Type", "application/json");
        headers.add("X-CHJ-GWToken", gatewayToken);

        headers.add("BCS-APIHub-RequestId", UUID.randomUUID().toString());


        try {
            ResponseEntity<Resource> responseEntity = httpRestTemplate.exchange(url, HttpMethod.POST, new HttpEntity<>(req.toJSONString(), headers), Resource.class);
            // 处理响应输出流
            Resource responseStream = responseEntity.getBody();
            BufferedReader reader = new BufferedReader(new InputStreamReader(responseStream.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                JSONObject jsonObject = JSON.parseObject(line);
                if (jsonObject.getBoolean("is_final")) {
//                    String sql = getSql(jsonObject.getString("text"));
                    log.info("text:{},ddl:{},sql:{}", text, ddl, "");
                    return "sql";
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
                "- OutputFormat: markdown语法输出" + "\n" +
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


//    @Autowired
//    private NvwaBoxConfiguration nvwaBoxConfiguration;
//
//    @Autowired
//    private AlarmService alarmService;
//
//
//    private <T> HttpClient<T> httpClient(Class<T> tClass) {
//        return RestTemplateHelper.httpClient(tClass).setTimeout(nvwaBoxConfiguration.getTimeout(), nvwaBoxConfiguration.getConnectTimeout(), nvwaBoxConfiguration.getConnectTimeout());
//    }
//
//    public void completion(ChatCompletionRequest request, OutputStream outputStream) {
//        try {
//            chatCompletionStreamInner(request, (response -> {
//                if (!response.getStatusCode().equals(HttpStatus.OK)) {
//                    //切换备用模型
//                    alarmService.error(StatusCode.COMPLETION_FAIL, request.getSessionId(), "HttpStatus NOT OK");
//                    request.processToken(nvwaBoxConfiguration, request.getSlaveModelType());
//                    chatCompletionStreamInner(request, (response1 -> {
//                        writeStream(response1, outputStream, request.getModelConfig());
//                    }));
//                    return;
//                }
//                writeStream(response, outputStream, request.getModelConfig());
//            }));
//        } catch (Exception e) {
//            alarmService.error(StatusCode.COMPLETION_FAIL, e, request.getSessionId());
//            request.processToken(nvwaBoxConfiguration, request.getSlaveModelType());
//            chatCompletionStreamInner(request, (response1 -> {
//                writeStream(response1, outputStream, request.getModelConfig());
//            }));
//        }
//    }
//
//    private void chatCompletionStreamInner(ChatCompletionRequest request, Consumer<ResponseEntity<InputStream>> consumer) {
//        Gpt4oRequest gpt4oRequest = convert(request);
//        UUID uuid = UUID.randomUUID();
//        ModelConfig modelConfig = request.getModelConfig();
//        HttpClient<InputStream> httpClient = httpClient(InputStream.class)
//                .url(modelConfig.getCompletionUrl())
//                .addHeader("X-CHJ-GWToken", modelConfig.getAuthorization())
//                .addHeader("BCS-APIHub-RequestId", uuid.toString())
//                .addHeader(org.springframework.http.MediaType.APPLICATION_JSON)
//                .body(gpt4oRequest);
//        log.info("与LiCloud大模型对话上下文：messages: {}，UUID：{}",request.getMessages(),uuid.toString());
//        httpClient.post(consumer::accept);
//    }
//
//    public Gpt4oRequest convert(ChatCompletionRequest request){
//        Gpt4oRequest gpt4oRequest = new Gpt4oRequest();
//        List<Gpt4oMessage> messages = new ArrayList<>();
//        gpt4oRequest.setMessages(messages);
//        List<ChatCompletionMessage> messageList = request.getMessages();
//        for (ChatCompletionMessage message : messageList) {
//            Gpt4oMessage gpt4oMessage = new Gpt4oMessage();
//            gpt4oMessage.setRole(message.getRole());
//            List<Gpt4oContent> contents = new LinkedList<>();
//            Gpt4oContent gpt4oContent = new Gpt4oContent();
//            gpt4oContent.setType("text");
//            gpt4oContent.setText(message.getContent());
//            contents.add(gpt4oContent);
//            gpt4oMessage.setContents(contents);
//            messages.add(gpt4oMessage);
//        }
//        return gpt4oRequest;
//    }
//
//    private void writeStream(ResponseEntity<InputStream> response, OutputStream outputStream, ModelConfig modelConfig) {
//        try {
//            if (response.getStatusCode().equals(HttpStatus.BAD_REQUEST)) {
//                outputStream.write(nvwaBoxConfiguration.getSensitiveWordTip().getBytes(Charset.defaultCharset()));
//                outputStream.write(System.lineSeparator().getBytes());
//                outputStream.flush();
//                return;
//            }
//            if (!response.getStatusCode().equals(HttpStatus.OK)) {
//                outputStream.write(nvwaBoxConfiguration.getExceptionTip().getBytes(Charset.defaultCharset()));
//                outputStream.write(System.lineSeparator().getBytes());
//                outputStream.flush();
//                return;
//            }
//
//            if (response.getBody() == null) {
//                return;
//            }
//
//            BufferedReader bufferedInputStream = new BufferedReader(new InputStreamReader(response.getBody()));
//
//            String line;
//
//            int id = 0;
//
//            while ((line = bufferedInputStream.readLine()) != null) {
//                if(!StringUtils.startsWith(line, "data:") || StringUtils.isEmpty(line)) {
//                    continue;
//                }
//                if (!line.equals("data:[DONE]")){
//                    String content = StringUtils.substring(line, 5);
//                    JSONObject json = JSON.parseObject(content);
//                    JSONArray choices = json.getJSONObject("data").getJSONArray("choices");
//                    if (!choices.isEmpty()){
//                        List<ChoiceDto> collect = new LinkedList<>();
//                        for (Object choice : choices) {
//                            JSONObject choiceJson = JSON.parseObject(JSON.toJSONString(choice));
//                            ChoiceDto choiceDto = new ChoiceDto();
//                            choiceDto.setIndex(choiceJson.getIntValue("index"));
//                            choiceDto.setFinish_reason(null);
//                            choiceDto.setDelta(new ChoiceDto.DeltaDto().setContent(choiceJson.getString("content")).setRole("assistant"));
//                            collect.add(choiceDto);
//                        }
//                        GenerationOutputDto generationOutputDto = new GenerationOutputDto().setChoices(collect).setId(""+id++).setModel(modelConfig.getModel());
//                        line = "data: " + JSON.toJSONString(generationOutputDto);
//                        outputStream.write(line.getBytes(Charset.defaultCharset()));
//                        outputStream.write(System.lineSeparator().getBytes());
//                        outputStream.flush();
//                    }
//                }
//            }
//        } catch (IOException e) {
//            log.info("客户端关闭流",e);
//            try {
//                response.getBody().close();
//            } catch (IOException ex) {
//                log.error("关闭流异常", ex);
//            }
//        }
//    }

}
