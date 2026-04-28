package com.travel.intellitravel_pioneer.util;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.model.PutObjectRequest;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.Date;


@Component
@Getter
@Setter
@NoArgsConstructor
@ConfigurationProperties("aliyun.oss")  //作用是可以加载配置文件中的值到你的bean属性中
public class OssUtil {
    private String endpoint;
    private String accessKeyId;
    private String accessKeySecret;
    private String bucketName;

    public String upload(String objectName, InputStream inputStream) {
        OSS ossClient = new OSSClientBuilder().build(endpoint, accessKeyId, accessKeySecret);
        try {
            PutObjectRequest putObjectRequest = new PutObjectRequest(bucketName, objectName, inputStream);
            ossClient.putObject(putObjectRequest);
            long expireTime = System.currentTimeMillis() + 3600 * 1000;
            String rawUrl = ossClient.generatePresignedUrl(bucketName, objectName, new Date(expireTime))
                    .toString()
                    .split("\\?Expires")[0];
            return rawUrl.replaceFirst("^http://", "https://");
            
        } finally {
            if (ossClient != null) {
                ossClient.shutdown(); 
            }
        }
    }
}