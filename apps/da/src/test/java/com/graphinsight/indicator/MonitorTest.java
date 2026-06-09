package com.graphinsight.indicator;


import java.io.IOException;
import java.util.List;

import com.alibaba.fastjson.JSON;
import com.graphinsight.indicator.model.JavaInfo;
import com.graphinsight.indicator.model.Response;
import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.util.EntityUtils;


public class MonitorTest {


    public static void main(String[] args) {

        HttpClient client = new DefaultHttpClient();

        //创建get请求实例
        HttpGet get = new HttpGet("http://127.0.0.1:8080/bi/v1/current");
        System.out.println("请求的uri为:" + get.getURI());

        try {
            // 客户端执行get请求 返回响应实体
            HttpResponse response = client.execute(get);

            //获取请求状态行
            System.out.println("请求状态行为:" + response.getStatusLine());

            //获取所有的请求头
//            Header[] headers = response.getAllHeaders();

//            for (Header header : headers) {
//                //遍历获取所有请求头的名称和值
//                System.out.println(header.getName() + " :--: " + header.getValue());
//            }
//            System.out.println("-----------------------------------------------");
            //获取响应的实体
            HttpEntity entity = response.getEntity();

            if (entity != null) {
                String str = EntityUtils.toString(entity, "UTF-8");
                System.out.println("entity:" + str);
                System.out.println("获取到的json为：" + str);

                Response<JavaInfo> javaInfoList = JSON.parseObject(str, Response.class);

                //System.out.println(EntityUtils.toString(entity,"UTF-8"));
                System.out.println("=================================");
                System.out.println("内容长度为:" + entity.getContentLength());
            }
        } catch (ClientProtocolException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            //释放连接
            client.getConnectionManager().shutdown();
        }
    }

}
