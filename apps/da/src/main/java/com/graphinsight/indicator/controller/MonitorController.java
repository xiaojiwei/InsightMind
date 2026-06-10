package com.graphinsight.indicator.controller;


import com.alibaba.fastjson.JSONObject;
import com.baomidou.dynamic.datasource.toolkit.DynamicDataSourceContextHolder;
import com.graphinsight.indicator.constant.IndicatorConstant;
import com.graphinsight.indicator.dao.JavaInfoDao;
import com.graphinsight.indicator.enums.JdbcDataSourceType;
import com.graphinsight.indicator.model.JavaInfo;
import com.graphinsight.indicator.model.Request;
import com.graphinsight.indicator.model.Response;
import junit.extensions.TestSetup;
import lombok.Data;
import net.bull.javamelody.internal.model.*;
import net.bull.javamelody.internal.web.SerializableController;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.conn.ClientConnectionManager;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.params.HttpParams;
import org.apache.http.protocol.HttpContext;
import org.apache.http.HttpEntity;
import org.apache.http.NameValuePair;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;

@RestController
@RequestMapping(IndicatorConstant.API_V1)
public class MonitorController {

    static int excInfo = 0;

    /**
     *
     */
    @Autowired
    private JavaInfoDao javaInfoDao;

    private static final Map<String, Method> METHOD_MAP = new HashMap<String, Method>();

    static {
        for (final Method method : SerializableController.class.getDeclaredMethods()) {
            method.setAccessible(true);
            METHOD_MAP.put(method.getName(), method);
        }
    }
    static final String FILTER_CONTEXT_KEY = "javamelody.filterContext";


    private List<JavaInfo> buildJavaInfoList(HttpServletRequest httpRequest) throws Exception {

        List<JavaInfo> javaInfoList = null;
        ServletContext servletContext = httpRequest.getServletContext();
        Object filterContext = servletContext.getAttribute(FILTER_CONTEXT_KEY);

        Map<String, Object> filterContextBeanMap = objectToMap(filterContext);
        Collector collector = (Collector)filterContextBeanMap.get("collector");
        List<Counter> counterList = collector.getCounters();


        JavaInformations javaInformations = new JavaInformations(servletContext, true);

        Method method = METHOD_MAP.get("getRangeForSerializable");
        method.setAccessible(true);

        final SerializableController serializableController = new SerializableController(collector);
        final Range range = (Range) method.invoke(serializableController, httpRequest);
        List<JavaInformations> javaInformationsList = Collections.singletonList(javaInformations);

        //xml序列化接口
        Method createCurrentMethod = METHOD_MAP.get("getCurrentRequests");
        createCurrentMethod.setAccessible(true);
        List<CounterRequestContext> requestContextList = (List<CounterRequestContext>)createCurrentMethod.invoke(serializableController);
        final Map<JavaInformations, List<CounterRequestContext>> result = new HashMap<JavaInformations, List<CounterRequestContext>>();
        result.put(javaInformationsList.get(0), requestContextList);

        for (final Map.Entry<JavaInformations, List<CounterRequestContext>> entry : result.entrySet()) {

            final JavaInformations javaInformations1 = entry.getKey();
            final List<CounterRequestContext> rootCurrentContexts = entry.getValue();

            if (rootCurrentContexts.size() > 0) {

                javaInfoList = new ArrayList<JavaInfo>();
                for (CounterRequestContext currentContext : rootCurrentContexts) {

                    JavaInfo javaInfo = new JavaInfo();
                    javaInfo.setHost(javaInformations.getHost());
                    javaInfo.initCreate();
                    //javaInfo.setThreadName();
                    Long threadId = currentContext.getThreadId();

                    ThreadInformations threadInformations = null;
                    List<ThreadInformations> threadInformationsList = javaInformations1.getThreadInformationsList();
                    for (ThreadInformations threadInformation : threadInformationsList) {
                        if (null != threadId && threadId.equals(threadInformation.getId())) {
                            threadInformations = threadInformation;
                            break;
                        }

                    }

                    javaInfo.setThreadId(threadInformations.getId());
                    Long now = System.currentTimeMillis();
                    javaInfo.setDuration(currentContext.getDuration(now));
                    javaInfo.setThreadName(threadInformations.getName());
                    javaInfo.setThreadStack(threadInformations.getStackTrace().toString());
                    StackTraceElement stackTrace = threadInformations.getStackTrace().get(0);
                    javaInfo.setExecMethod("FileName:" +stackTrace.getFileName() + " ClassName:" + stackTrace.getClassName()  + "." +  stackTrace.getMethodName() + " LineNumber:" + stackTrace.getLineNumber());
                    javaInfo.setStartAllocatedBytes(String.valueOf(currentContext.getAllocatedKBytes()));

                    List<CounterRequestContext> allContext = new ArrayList<CounterRequestContext>();

                    allContext.add(currentContext);
                    allContext.addAll(currentContext.getChildContexts());

                    boolean isHas = false;
                    for (CounterRequestContext context : allContext) {
                        //获取当前请求耗时
                        String requestName = context.getRequestName();
                        if (requestName.indexOf("monitor") < 0 && requestName.indexOf("getJava") < 0 && requestName.indexOf("/bi/v1/start") < 0) {
                            buildRequest(context, counterList, javaInfo, now);
                            isHas = true;
                        }

                    }

                    if (isHas) {
                        javaInfoList.add(javaInfo);
                    }

                }
            }

            final List<ThreadInformations> threadInformationsList = javaInformations1.getThreadInformationsList();
            final boolean stackTraceEnabled = javaInformations1.isStackTraceEnabled();

        }

        return javaInfoList;

    }

    @GetMapping("/current")
    public Response getJavaInfo(HttpServletRequest httpRequest, HttpServletResponse httpResponse) {


        try {
            String res = testIPaas();
            return Response.ok(res);
        } catch (Exception e) {
            e.printStackTrace();
            return Response.ok(e);
        }

//        try {
//            List<JavaInfo> javaInfoList = buildJavaInfoList(httpRequest);
//            return Response.ok(javaInfoList);
//        } catch (Exception e) {
//            e.printStackTrace();
//        }


    }

    @GetMapping("/iPaas")
    public Response sendPost(HttpServletRequest httpRequest, HttpServletResponse httpResponse) {


        try {
            String res = testIPaas();
            return Response.ok(res);
        } catch (Exception e) {
            e.printStackTrace();
            return Response.ok(e);
        }

    }

    public static String testIPaas() {
        try {
            CloseableHttpClient client = HttpClients.createDefault();    //创建一个http客户端
            HttpPost httpPost = new HttpPost("http://ipaas-gateway.inner.chj.cloud/limos-wms/api/v1/sip/dn-del-check");
            RequestConfig requestConfig = RequestConfig.custom()
                    .setConnectionRequestTimeout(1000)
                    .setSocketTimeout(1000) // 服务端相应超时
                    .setConnectTimeout(2000) // 建立socket链接超时时间
                    .build();
            httpPost.setConfig(requestConfig);
            //head
            httpPost.addHeader("Content-Type", "application/json");

            //form 格式 body
            String body = "{\n" +
                    "    \"request\": {\n" +
                    "        \"header\": {\n" +
                    "            \"factoryCode\": \"1229\",\n" +
                    "            \"count\": \"1\",\n" +
                    "            \"transactionId\": \"CHJ_SIP_193_20250509154453259484719\",\n" +
                    "            \"customer\": \"SIP\"\n" +
                    "        },\n" +
                    "        \"list\": {\n" +
                    "            \"DNVAL\": \"JIT250515S00462\"\n" +
                    "        }\n" +
                    "    }\n" +
                    "}";

            JSONObject request = new JSONObject();
            JSONObject header = new JSONObject();
            JSONObject headerObj = new JSONObject();
            headerObj.put("factoryCode", "1229");
            headerObj.put("count", "1");
            headerObj.put("transactionId", "CHJ_SIP_193_20250509154453259484719");
            headerObj.put("customer", "SIP");

            header.put("header" , headerObj);

            JSONObject list = new JSONObject();
            list.put("DNVAL", "JIT250515S00462");
            header.put("list", list);
            request.put("request", header);


            String requestParams = request.toString();
            StringEntity postingString = new StringEntity(requestParams, "utf-8");
            httpPost.setEntity(postingString);

            CloseableHttpResponse Response = client.execute(httpPost); // 通过client调用execute方法，得到我们的执行结果就是一个response，所有的数据都封装在response里面了

            String v = String.valueOf(Response.getProtocolVersion());
            String sc = String.valueOf(Response.getStatusLine().getStatusCode());    //打印捕获的状态码
            String bodyAsString = EntityUtils.toString(Response.getEntity(), "UTF-8");
            Response.close();

            return "v=" + v + " sc=" + sc + " body=" + bodyAsString;



        } catch (Exception e) {
            return e.toString();
        }

    }


    @GetMapping("/start")
    public Response getJava(HttpServletRequest httpRequest, HttpServletResponse httpResponse) {

        if (MonitorController.excInfo > 0) {
            return Response.ok("ok");
        }

        MonitorController.excInfo++;

        boolean isRun = false;
        try {
            while (true) {


                if (!isRun) {
                    isRun = true;
                    // TestSend.start(null); // removed: class missing in repo
                    isRun = false;
                }
//                try {
//                    List<JavaInfo> javaInfoList = buildJavaInfoList(httpRequest);
//                    for (JavaInfo javaInfo : javaInfoList) {
//                        Integer duration = javaInfo.getDuration();
//                        if (duration > 3000) {
//                            DynamicDataSourceContextHolder.push(JdbcDataSourceType.MYSQL.getDesc());
//                            this.javaInfoDao.save(javaInfo);
//                        }
//
//                    }
//                } catch (Exception ex) {
//                    ex.printStackTrace();
//                }

                Thread.sleep(60000l);

            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        return Response.ok("success");

    }

    private void buildRequest(CounterRequestContext context, List<Counter> counterList, JavaInfo javaInfo, Long timeOfSnapshot) {

        Request request = new Request();
        //获取当前请求耗时
        CounterRequest counterRequest = this.getCounterRequest(counterList, context.getRequestName());

        //cpu时间
        Integer cupTime = context.getCpuTime();
        //持续时间
        Integer duration = context.getDuration(timeOfSnapshot);

        if (null != counterRequest) {
            //平均cpu时间
            Integer cpuMean = counterRequest.getCpuTimeMean();
            //平均持续时间
            Integer durationMean = counterRequest.getMean();
            request.setRequest(counterRequest.getName());
            request.setCpuMean(String.valueOf(cpuMean));
            request.setDurationMean(String.valueOf(durationMean));
            request.setResponseSizeMean(String.valueOf(counterRequest.getResponseSizeMean()));

        }

        request.setCpu(String.valueOf(cupTime));
        request.setDuration(String.valueOf(duration));
        request.setEndTime(timeOfSnapshot.toString());
        request.setCompleteRequest(context.getCompleteRequestName());

        javaInfo.getRequestList().add(request);

    }

    public static Map<String, Object> objectToMap(Object obj) throws IllegalAccessException {

        Map<String, Object> map = new HashMap<String, Object>();
        Class<?> clazz = obj.getClass();

        for (Field field : clazz.getDeclaredFields()) {

            field.setAccessible(true);

            String fieldName = field.getName();

            Object value = field.get(obj);
            map.put(fieldName, value);

        }

        return map;

    }

    private CounterRequest getCounterRequest(List<Counter> counterList, String requestName) {

        for (Counter counter : counterList) {
            List<CounterRequest> counterRequestList = counter.getRequests();
            for (CounterRequest counterRequest : counterRequestList) {

                if (requestName.equalsIgnoreCase(counterRequest.getName())) {
                    return counterRequest;
                }

            }
        }

        return null;

    }
}
