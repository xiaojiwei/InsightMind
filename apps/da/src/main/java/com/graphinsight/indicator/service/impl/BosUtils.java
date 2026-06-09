package com.graphinsight.indicator.service.impl;

import com.baidubce.auth.DefaultBceCredentials;
import com.baidubce.services.bos.BosClient;
import com.baidubce.services.bos.BosClientConfiguration;
import com.baidubce.services.bos.model.*;

import javax.servlet.http.HttpServletResponse;
import javax.xml.crypto.Data;
import java.io.*;
import java.net.URL;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;


/**
 * 百度上传的工具类
 *
 * @author EraJieZhang
 * @date 2018年12月25日
 */
public class BosUtils {

    /**
     * 获取BosClient对象
     *
     * @param accessKeyId     ak
     * @param secretAccessKey sk
     * @param endpoint        根节点
     */
    public static BosClient getBosClient(String accessKeyId, String secretAccessKey, String endpoint) {
        BosClientConfiguration config = new BosClientConfiguration();
        config.setMaxConnections(10);
        config.setCredentials(new DefaultBceCredentials(accessKeyId, secretAccessKey));
        config.setEndpoint(endpoint);
        return new BosClient(config);
    }

    /**
     * 百度bos以file形式上传文件（不超过5GB）
     *
     * @param client     BosClient链接对象
     * @param file       要上传的文件
     * @param bucketName 上传到那个文件夹（newsurvey下的文件夹，如果没有会自动创建，不能用“/” 创建多层）
     * @param objectKey  文件路径/文件名（可以用“/”来创建多层文件夹）
     * @return 上传成功后的tag
     */
    public static PutObjectResponse uploadFileToBos(BosClient client, File file,
                                                    String bucketName, String objectKey) {
        return client.putObject(bucketName, objectKey, file);
    }

    /**
     * 以数据流形式上传Object（不超过5GB）
     *
     * @param client      BosClient链接对象
     * @param inputStream 要上传的数据流    InputStream inputStream = new FileInputStream("/path/test.zip");
     * @param bucketName  上传到那个文件夹（newsurvey下的文件夹，如果没有会自动创建，不能用“/” 创建多层）
     * @param objectKey   文件路径/文件名（可以用“/”来创建多层文件夹）
     * @return 上传成功后的tag
     */
    public static PutObjectResponse uploadInputStreamToBos(BosClient client, InputStream inputStream,
                                                           String bucketName, String objectKey) {
        return client.putObject(bucketName, objectKey, inputStream);
    }

    /**
     * 以二进制串上传Object（不超过5GB）
     *
     * @param client     BosClient链接对象
     * @param file       要上传的byte
     * @param bucketName 上传到那个文件夹（newsurvey下的文件夹，如果没有会自动创建，不能用“/” 创建多层）
     * @param objectKey  文件路径/文件名（可以用“/”来创建多层文件夹）
     * @return 上传成功后的tag
     */
    public static PutObjectResponse uploadByteToBos(BosClient client, byte[] file,
                                                    String bucketName, String objectKey) {
        return client.putObject(bucketName, objectKey, file);
    }


    /**
     * 以字符串上传Object（不超过5GB）
     *
     * @param client     BosClient链接对象
     * @param file       要上传的string
     * @param bucketName 上传到那个文件夹（newsurvey下的文件夹，如果没有会自动创建，不能用“/” 创建多层）
     * @param objectKey  文件路径/文件名（可以用“/”来创建多层文件夹）
     * @return 上传成功后的tag
     */
    public static PutObjectResponse uploadStringToBos(BosClient client, String file,
                                                      String bucketName, String objectKey) {
        return client.putObject(bucketName, objectKey, file);
    }

    /**
     * 删除已经上传的Object
     *
     * @param client     BosClient链接对象
     * @param bucketName 文件夹（newsurvey下的文件夹，如果没有会自动创建，不能用“/” 创建多层）
     * @param objectKey  文件路径/文件名（可以用“/”来创建多层文件夹）
     * @return 上传成功后的tag
     */
    public static void deleteObject(BosClient client, String bucketName, String objectKey) {
        client.deleteObject(bucketName, objectKey);
    }


    /**
     * 批量删除Object(以Json格式的字符串)
     * 支持一次请求内最多删除1000个Object。
     * 消息体（body）不超过2M。
     * 返回的消息体中只包含删除过程中出错的Object结果；如果所有Object都删除都成功的话，则没有消息体。
     *
     * @param client         BosClient链接对象
     * @param bucketName     文件夹（newsurvey下的文件夹，如果没有会自动创建，不能用“/” 创建多层）
     * @param jsonObjectKeys 文件路径/文件名（可以用“/”来创建多层文件夹）        String jsonObjectKeys = "{\"objects\": [" + "{\"key\": \"token1.h\"}," + "{\"key\": \"token2.h\"}" + "]}";
     * @return 返回的消息体中只包含删除过程中出错的Object结果；如果所有Object都删除都成功的话，则没有消息体。
     */
    public static DeleteMultipleObjectsResponse deleteObjectListUseJson(BosClient client, String bucketName, String jsonObjectKeys) {


        DeleteMultipleObjectsRequest request = new DeleteMultipleObjectsRequest();
        request.setBucketName(bucketName);
        request.setJsonDeleteObjects(jsonObjectKeys);
        return client.deleteMultipleObjects(request);

    }

    /**
     * 批量删除Object(用户只需指定指定参数即可)
     * 支持一次请求内最多删除1000个Object。
     * 消息体（body）不超过2M。
     *
     * <p>
     * List<String> objectKeys = new ArrayList<String>();
     * objectKeys.add("object1");
     * objectKeys.add("object2");
     *
     * @param client     BosClient链接对象
     * @param bucketName 文件夹（newsurvey下的文件夹，如果没有会自动创建，不能用“/” 创建多层）
     * @param objectKeys 文件路径/文件名（可以用“/”来创建多层文件夹）
     * @return 返回的消息体中只包含删除过程中出错的Object结果；如果所有Object都删除都成功的话，则没有消息体。
     */
    public static DeleteMultipleObjectsResponse deleteObjectList(BosClient client, String bucketName, List<String> objectKeys) {

        DeleteMultipleObjectsRequest request = new DeleteMultipleObjectsRequest();
        request.setBucketName(bucketName);
        request.setObjectKeys(objectKeys);
        return client.deleteMultipleObjects(request);
    }

    /**
     * 获取一个分块上传事件-使用Multipart 上传文件
     *
     * @param client     BosClient链接对象
     * @param bucketName 件夹（newsurvey下的文件夹，如果没有会自动创建，不能用“/” 创建多层）
     * @param objectKey  文件路径/文件名（可以用“/”来创建多层文件夹）
     * @return 分块上传事件
     */
    public static InitiateMultipartUploadResponse getMultipartUploadID(BosClient client,
                                                                       String bucketName, String objectKey) {
        InitiateMultipartUploadRequest initiateMultipartUploadRequest =
                new InitiateMultipartUploadRequest(bucketName, objectKey);
        return client.initiateMultipartUpload(initiateMultipartUploadRequest);
    }


    /**
     * 使用Multipart 上传文件 应用场景
     * 1.需要支持断点上传。
     * 2.上传超过5GB大小的文件。
     * 3.网络条件较差，和BOS的服务器之间的连接经常断开。
     * 4.需要流式地上传文件。
     * 5.上传文件之前，无法确定上传文件的大小。
     *
     * @param client     BosClient链接对象
     * @param file
     * @param bucketName 件夹（newsurvey下的文件夹，如果没有会自动创建，不能用“/” 创建多层）
     * @param objectKey  文件路径/文件名（可以用“/”来创建多层文件夹）
     */
    public static void uploadMultipartToBos(BosClient client, File file,
                                            String bucketName, String objectKey) {
        InitiateMultipartUploadRequest initiateMultipartUploadRequest =
                new InitiateMultipartUploadRequest(bucketName, objectKey);
        InitiateMultipartUploadResponse initiateMultipartUploadResponse = client.initiateMultipartUpload(initiateMultipartUploadRequest);


        // 设置每块为 5MB
        final long partSize = 1024 * 1024 * 5L;


        // 计算分块数目
        int partCount = (int) (file.length() / partSize);
        if (file.length() % partSize != 0) {
            partCount++;
        }
        // 新建一个List保存每个分块上传后的ETag和PartNumber
        List<PartETag> partETags = new ArrayList<PartETag>();
        for (int i = 0; i < partCount; i++) {
            // 获取文件流
            FileInputStream fis = null;
            try {
                fis = new FileInputStream(file);

                // 跳到每个分块的开头
                long skipBytes = partSize * i;
                fis.skip(skipBytes);

                // 计算每个分块的大小
                long size = partSize < file.length() - skipBytes ?
                        partSize : file.length() - skipBytes;

                // 创建UploadPartRequest，上传分块
                UploadPartRequest uploadPartRequest = new UploadPartRequest();
                uploadPartRequest.setBucketName(bucketName);
                uploadPartRequest.setKey(objectKey);
                uploadPartRequest.setUploadId(initiateMultipartUploadResponse.getUploadId());
                uploadPartRequest.setInputStream(fis);
                uploadPartRequest.setPartSize(size);
                uploadPartRequest.setPartNumber(i + 1);
                UploadPartResponse uploadPartResponse = client.uploadPart(uploadPartRequest);

                // 将返回的PartETag保存到List中。
                partETags.add(uploadPartResponse.getPartETag());

                // 关闭文件
                fis.close();
            } catch (FileNotFoundException e) {
                e.printStackTrace();
                System.out.println("上传异常1");
            } catch (IOException e) {
                e.printStackTrace();
                System.out.println("上传异常2");
            }
        }


        CompleteMultipartUploadRequest completeMultipartUploadRequest =
                new CompleteMultipartUploadRequest(bucketName, objectKey, initiateMultipartUploadResponse.getUploadId(), partETags);

        // 完成分块上传
        CompleteMultipartUploadResponse completeMultipartUploadResponse =
                client.completeMultipartUpload(completeMultipartUploadRequest);

        // 打印Object的ETag
        System.out.println("ETag getETag:" + completeMultipartUploadResponse.getETag());
        System.out.println("ETag getBucketName:" + completeMultipartUploadResponse.getBucketName());
        System.out.println("ETag getKey:" + completeMultipartUploadResponse.getKey());
        System.out.println("ETag getLocation:" + completeMultipartUploadResponse.getLocation());
        System.out.println("ETag list:" + partETags.toString());


    }


    /**
     * 取消分块上传事件
     *
     * @param client
     * @param bucketName
     * @param objectKey
     * @param uploadId
     */
    public static void cancelMultipart(BosClient client, String bucketName, String objectKey, String uploadId) {
        AbortMultipartUploadRequest abortMultipartUploadRequest =
                new AbortMultipartUploadRequest(bucketName, objectKey, uploadId);

        // 取消分块上传
        client.abortMultipartUpload(abortMultipartUploadRequest);
    }

    /**
     * 获取未完成的分块上传事件
     *
     * @param client
     * @param bucketName
     * @return
     */
    public static ListMultipartUploadsResponse getBreakMultipart(BosClient client, String bucketName) {
        ListMultipartUploadsRequest listMultipartUploadsRequest =
                new ListMultipartUploadsRequest(bucketName);

        // 获取Bucket内所有上传事件
        ListMultipartUploadsResponse listing = client.listMultipartUploads(listMultipartUploadsRequest);

        // 遍历所有上传事件
        for (MultipartUploadSummary multipartUpload : listing.getMultipartUploads()) {
            System.out.println("Key: " + multipartUpload.getKey() + " UploadId: " + multipartUpload.getUploadId());
        }
        return listing;

    }


    /**
     * 获取所有已上传的块信息
     *
     * @param client
     * @param bucketName
     * @param objectKey
     * @param uploadId
     * @return
     */
    public static ListPartsResponse getRequestMultipartMsg(BosClient client,
                                                           String bucketName, String objectKey, String uploadId) {
        ListPartsRequest listPartsRequest = new ListPartsRequest(bucketName, objectKey, uploadId);

// 获取上传的所有Part信息
        ListPartsResponse partListing = client.listParts(listPartsRequest);

// 遍历所有Part
        for (PartSummary part : partListing.getParts()) {
            System.out.println("PartNumber: " + part.getPartNumber() + " ETag: " + part.getETag());
        }
        return partListing;
    }


    /**
     * 上传低频存储类型Object的初始化
     *
     * @param client
     * @param bucketName
     * @param objectKey
     */
    public static void putMultiUploadStorageClassStandard(BosClient client,
                                                          String bucketName, String objectKey) {
        InitiateMultipartUploadRequest iniReq = new InitiateMultipartUploadRequest(bucketName, objectKey);
        iniReq.withStorageClass(BosClient.STORAGE_CLASS_STANDARD_IA);
        client.initiateMultipartUpload(iniReq);
    }

    /**
     * 上传冷存储类型Object的初始化
     *
     * @param client
     * @param bucketName
     * @param objectKey
     */
    public static void putMultiUploadStorageClassCold(BosClient client,
                                                      String bucketName, String objectKey) {
        InitiateMultipartUploadRequest iniReq = new InitiateMultipartUploadRequest(bucketName, objectKey);
        iniReq.withStorageClass(BosClient.STORAGE_CLASS_COLD);
        client.initiateMultipartUpload(iniReq);
    }

    /**
     * 检查指定的文件夹是否存在
     *
     * @param client
     * @param bucketName
     */
    public static void checkBucketExist(BosClient client,
                                        String bucketName) {
        client.doesBucketExist(bucketName);
    }

    /**
     * 拷贝一个文件
     *
     * @param client
     * @param srcBucketName
     * @param srcKey
     * @param destBucketName
     * @param destKey
     */
    public static void copyObject(BosClient client, String srcBucketName, String srcKey, String destBucketName, String destKey) {

        // 拷贝Object
        CopyObjectResponse copyObjectResponse = client.copyObject(srcBucketName, srcKey, destBucketName, destKey);

        // 打印结果
        System.out.println("ETag: " + copyObjectResponse.getETag() + " LastModified: " + copyObjectResponse.getLastModified());
    }

    /*----------------------文件下载STARA-------------------------------*/

    /**
     * 简单流式下载
     *
     * @param client     链接bos
     * @param bucketName 主目录
     * @param objectKey  文件目录以及文件名（用“/”分开 ）
     */
    public static void getObjectStream(BosClient client, String bucketName, String objectKey, String fileName, boolean isXls, HttpServletResponse response) {

        // 清空response
        response.reset();
        response.setHeader("Access-Control-Allow-Origin", "*");
        response.setHeader("Access-Control-Allow-Credentials", "true");
        response.setHeader("Access-Control-Allow-Methods", "POST, GET, PATCH, DELETE, PUT");
        response.setHeader("Access-Control-Max-Age", "3600");
        response.setHeader("Access-Control-Allow-Headers", "*");
        // 设置response的Header
        try {
            Date dt = new Date();
            String year = String.format("%tY", dt);
            String mon = String.format("%tm", dt);
            String day = String.format("%td", dt);

            String dateStr = year + "_" + mon + "_" + day;
            fileName = "指标自助分析" + "_" + dateStr + "_" + System.currentTimeMillis() + ".xlsx";
            fileName = java.net.URLEncoder.encode(fileName, "utf-8");

        } catch (Exception ex) {
            ex.printStackTrace();
        }

        response.addHeader("Content-Disposition", "attachment;filename=" + fileName);
        response.addHeader("Access-Control-Expose-Headers", "Content-Disposition");


        // 获取Object，返回结果为BosObject对象
        BosObject object = client.getObject(bucketName, objectKey);
        // 获取ObjectMeta
        ObjectMetadata meta = object.getObjectMetadata();
        // 获取Object的输入流
        InputStream objectContent = object.getObjectContent();
        // 实现文件下载
        byte[] buffer = new byte[1024];
        BufferedInputStream bis = null;
        try {
            bis = new BufferedInputStream(objectContent);
            // 获取字节流
            OutputStream os = response.getOutputStream();
            if (isXls) {
                response.setContentType("application/vnd.ms-excel;charset=gb2312");
            }

            int i = bis.read(buffer);
            while (i != -1) {
                os.write(buffer, 0, i);
                i = bis.read(buffer);
            }

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (bis != null) {
                try {
                    bis.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            if (objectContent != null) {
                try {
                    objectContent.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        // 关闭流
        try {
            response.getOutputStream();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    public static void getObjectStream(Object oisClient, String fileKey, String fileName, boolean isXls, HttpServletResponse response) {
        // OIS 已移除，此方法暂不支持
        try {
            response.sendError(HttpServletResponse.SC_NOT_IMPLEMENTED, "OIS 文件存储已禁用");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * 简单流式下载
     *
     * @param client     链接bos
     * @param bucketName 主目录
     * @param objectKey  文件目录以及文件名（用“/”分开 ）
     * @param file       下载后的文件
     */
    public static void getObject(BosClient client, String bucketName, String objectKey, File file) {
        // 获取Object，返回结果为BosObject对象
        BosObject object = client.getObject(bucketName, objectKey);
        // 获取ObjectMeta
        ObjectMetadata meta = object.getObjectMetadata();
        // 获取Object的输入流
        InputStream objectContent = object.getObjectContent();
        // 处理Object
//        FileUtils.writeFile(objectContent, file);
        // 关闭流
        try {
            objectContent.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * 直接下载Object到文件
     *
     * @param client     链接bos
     * @param bucketName 主目录
     * @param objectKey  文件目录以及文件名（用“/”分开 ）
     * @param file       下载后的文件
     */
    public static void getObjectRequest(BosClient client, String bucketName, String objectKey, File file) {
        // 新建GetObjectRequest
        GetObjectRequest getObjectRequest = new GetObjectRequest(bucketName, objectKey);
        //下载Object到文件
        /*ObjectMetadata objectMetadata = client.getObject(getObjectRequest, new File("/path/to/file","filename"));*/
        ObjectMetadata objectMetadata = client.getObject(getObjectRequest, file);
    }

    /**
     * 范围下载
     * 为了实现更多的功能，可以通过使用GetObjectRequest来指定下载范围，实现更精细化地获取Object。如果指定的下载范围是0 - 100，
     * 则返回第0到第100个字节的数据，包括第100个，共101字节的数据，即[0, 100]。
     * 可以用此功能实现文件的分段下载和断点续传
     *
     * @param client     链接bos
     * @param bucketName 主目录
     * @param objectKey  文件目录以及文件名（用“/”分开 ）
     * @return 目标字节的数据
     */
    public static BosObject getObjectByteRequest(BosClient client, String bucketName, String objectKey) {
        // 新建GetObjectRequest
        GetObjectRequest getObjectRequest = new GetObjectRequest(bucketName, objectKey);

        // 获取0~100字节范围内的数据
        getObjectRequest.setRange(0, 100);

        // 获取Object，返回结果为BosObject对象
        return client.getObject(getObjectRequest);
    }

    /**
     * 只获取ObjectMetadata而不获取Object的实体
     *
     * @param client     链接
     * @param bucketName 主文件夹
     * @param objectKey  文件夹和文件名
     * @return 文件信息
     * <p>
     * contentType    Object的类型
     * contentLength    Object的大小
     * contentMd5   Object的MD5
     * etag Object的HTTP协议实体标签
     * storageClass Object的存储类型
     * userMetadata 如果在PutObject指定了userMetadata自定义meta，则返回此项
     * xBceCrc  如果在PutObject指定了object的CRC值(循环冗余校验码)，则返回此项
     */
    public static ObjectMetadata getObjectMetadata(BosClient client,
                                                   String bucketName, String objectKey) {
        ObjectMetadata objectMetadata = client.getObjectMetadata(bucketName, objectKey);
        return objectMetadata;
    }
    /*----------------------文件下载END-------------------------------*/

    /**
     * 标准存储转为低频存储
     */


    /**
     * @param client           链接
     * @param sourceBucketName 文件所在的BucketName
     * @param sourceKey        所在BucketName的key
     * @param bucketName       新位置的BucketName
     * @param key              新位置的BucketNameKEY
     * @param storageType      想要转换的存储类型    STANDARD(标准存储), STANDARD_IA(低频存储)和COLD(冷存储)
     */
    public static void changeStorageClass(BosClient client,
                                          String sourceBucketName, String sourceKey, String bucketName, String key, String storageType) {
        CopyObjectRequest copyObjectRequest = new CopyObjectRequest(sourceBucketName, sourceKey, bucketName, key);
        copyObjectRequest.setStorageClass(storageType);
        client.copyObject(copyObjectRequest);
    }

    /**
     * 获取文件下载URL
     *
     * @param client              链接
     * @param bucketName          主文件夹
     * @param objectKey           文件夹和文件名
     * @param expirationInSeconds 有效期（默认1800，永久有效为-1）
     * @return 目标文件的下载url
     */
    public static String generatePresignedUrl(BosClient client, String bucketName, String objectKey, int expirationInSeconds) {
        URL url = client.generatePresignedUrl(bucketName, objectKey, expirationInSeconds);
        return url.toString();
    }

    /**
     * 修改文件元信息
     * BOS修改Object的Metadata通过拷贝Object实现。即拷贝Object的时候，把目的Bucket设置为源Bucket，目的Object设置为源Object，
     * 并设置新的Metadata，通过拷贝自身实现修改Metadata的目的。如果不设置新的Metadata，则报错。
     *
     * @param client
     * @param bucketName
     * @param objectKey
     * @param newObjectMetadata
     */
    public void setObjectMeta(BosClient client, String bucketName, String objectKey, ObjectMetadata newObjectMetadata) {

        CopyObjectRequest request = new CopyObjectRequest(bucketName, objectKey, bucketName, objectKey);

        // 设置新的ObjectMetadata
        request.setNewObjectMetadata(newObjectMetadata);

        // 拷贝Object
        CopyObjectResponse copyObjectResponse = client.copyObject(request);

        // 打印结果
        System.out.println("ETag: " + copyObjectResponse.getETag() + " LastModified: " + copyObjectResponse.getLastModified());
    }

}