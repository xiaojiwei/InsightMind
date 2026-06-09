package com.graphinsight.indicator.service.impl;

import java.io.*;

import com.alibaba.fastjson.JSONObject;
import com.baidubce.auth.BceCredentials;
import com.baidubce.auth.DefaultBceSessionCredentials;
import com.baidubce.services.bos.BosClient;
import com.baidubce.services.bos.BosClientConfiguration;
import com.graphinsight.indicator.auto.entity.UploadFile;
import com.graphinsight.indicator.auto.mapper.UploadFileMapper;
import com.graphinsight.indicator.model.BosFileClientTuple;
import com.graphinsight.indicator.service.BosFileService;
import com.graphinsight.indicator.util.UserThreadLocalUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.HttpClientBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpServletResponse;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Slf4j
@Service
public class BosFileServiceImpl implements BosFileService {

    private static String identify;

    @Value("${ois.service.identify:old-data-indicator}")
    public void setIdentify(String bucket){
        identify = bucket;
    }

    private static String floder = "/indicator/file";    @Autowired
    UploadFileMapper uploadFileMapper;
    // private static final String URL = "https://bcs-api-boss-public-ontest.chehejia.com/chehejia-service-ois-app/ois/access/service/sts/write?identify=indicator&bucketType=1&fileKey=%2F&stsType=1&durationSeconds=3600";
    private static final String URL = "https://iot-api-boss-ontest.chehejia.com/chehejia-service-ois-app/ois/access/service/sts/write?identify=indicator&bucketType=1&fileKey=%2F&stsType=1&durationSeconds=3600";

    public static void main(String[] args) {
        try {
            BosFileServiceImpl bosFileService = new BosFileServiceImpl();
            bosFileService.downLoad();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void writeBosBak(String fileName) throws Exception {
        log.info("fileName is old {}", fileName);
        BosFileClientTuple tuple = buildBosClient();
        // 获取指定文件
        File file = new File(fileName);
        BosClient client = tuple.getBosClient();
        BosUtils.uploadFileToBos(client, file, tuple.getBucketName(),tuple.getFileKey() + fileName);

        // 关闭客户端
        client.shutdown();

    }


    public static String writeBos(String fileName) throws Exception {
        log.info("fileName is {}", fileName);
        try {
            writeBosBak(fileName);
        } catch (Exception e){
            log.error("上传BOS失败", e);
        }
        return "";
    }

    void saveFileKey(String dataId, String fileKey) {
        UploadFile uploadFile = new UploadFile();
        uploadFile.setDataId(dataId);
        uploadFile.setFileKey(fileKey);
        uploadFile.setCreator(UserThreadLocalUtil.getUserName());
        uploadFile.setUpdater(UserThreadLocalUtil.getUserName());
        uploadFile.setCreateDate(LocalDateTime.now());
        uploadFile.setUpdateDate(LocalDateTime.now());
        uploadFileMapper.insert(uploadFile);
    }

    private static BosFileClientTuple buildBosClient() {

        BosFileClientTuple tuple = new BosFileClientTuple();

        String result = null;
        try {
            result = get(URL);
        } catch (Exception e) {
            e.printStackTrace();
        }

        Object param = JSONObject.parse(result);
        Map paramMap = (Map) param;

        Map dataMap = (Map)paramMap.get("data");
        String accessKeyId = (String) dataMap.get("accessKeyId");
        String secret_access_key = (String) dataMap.get("accessKeySecret");
        String token = (String) dataMap.get("securityToken");
        String fileKey = (String) dataMap.get("fileKey");
        String bucketName = (String) dataMap.get("bucketName");
        String endpoint = (String) dataMap.get("endpoint");

        tuple.setFileKey(fileKey);
        tuple.setBucketName(bucketName);

        BceCredentials bosstsCredentials = new DefaultBceSessionCredentials(
                accessKeyId,
                secret_access_key,
                token);

        BosClientConfiguration config = new BosClientConfiguration();
        config.setCredentials(bosstsCredentials);
        config.setEndpoint(endpoint);
        BosClient client = new BosClient(config);
        tuple.setBosClient(client);

        return tuple;

    }

    public void downloadBosFile(HttpServletResponse response, String fileName) throws Exception {

        BosFileClientTuple tuple = buildBosClient();
        BosClient client = tuple.getBosClient();
        String bucketName = tuple.getBucketName();
        String fileKey = tuple.getFileKey();
        boolean isXls = fileName.indexOf("xls") >= 0;

        BosUtils.getObjectStream(client, bucketName, fileKey + fileName, fileName, isXls, response);

        // 关闭客户端
        client.shutdown();

    }


    public void getWriteAccess() throws Exception {

        BosFileClientTuple tuple = this.buildBosClient();
        // 获取指定文件
        File file = new File("Adhoc_ab3cbef0-8537-4f26-92b2-ea6d73e0e696_2021-12-10.csv");
        BosClient client = tuple.getBosClient();
        BosUtils.uploadFileToBos(client, file, tuple.getBucketName(),tuple.getFileKey() + "Adhoc_ab3cbef0-8537-4f26-92b2-ea6d73e0e696_2021-12-10.csv");

        // 关闭客户端
        client.shutdown();

    }


    public void downLoad() throws Exception {

        BosFileClientTuple tuple = this.buildBosClient();

        BosClient client = tuple.getBosClient();
        String bucketName = tuple.getBucketName();
        String fileKey = tuple.getFileKey();

        BosUtils.getObjectRequest(client, bucketName, fileKey + "Adhoc_ab3cbef0-8537-4f26-92b2-ea6d73e0e696_2021-12-10.csv", new File("ffsfsfsdfsf.csv"));
        // 关闭客户端
        client.shutdown();

    }

    public static String get(String url) throws Exception{

        HttpClient client = HttpClientBuilder.create().build();
        HttpGet httpGet = new HttpGet(url);
        // 设置连接超时
        final RequestConfig timeParams = RequestConfig.custom().setConnectTimeout(5000).build();
        httpGet.setConfig(timeParams);

        HttpResponse response = client.execute(httpGet);
        int statusCode = response.getStatusLine().getStatusCode();

        // 获取结果
        BufferedReader br = new BufferedReader( new InputStreamReader(response.getEntity().getContent()));
        StringBuffer result = new StringBuffer();
        String content = null;
        while ((content = br.readLine()) != null) {
            result.append(content);
        }
        br.close();

        return result.toString();

    }


    @Override
    public void downloadBosFile(HttpServletResponse response, String fileName, String downloadid) throws Exception {
        try {
            downloadBosFile(response, fileName);
        } catch (Exception e){
            log.error("下载数据有误", e);
        }
    }


    private String getFileName(String fileName) {


        try {
            Date dt = new Date();
            String year = String.format("%tY", dt);
            String mon = String.format("%tm", dt);
            String day = String.format("%td", dt);

            String dateStr = year + "_" + mon + "_" + day;
            fileName = "指标自助分析" + "_" + dateStr + "_" + System.currentTimeMillis() + ".xlsx";
            fileName = java.net.URLEncoder.encode(fileName, "utf-8");

        } catch (Exception ex) {
            fileName = System.currentTimeMillis() + ".xlsx";
            ex.printStackTrace();
        }
        return fileName;
    }
}
