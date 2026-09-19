package com.czlr.orangemarketbackend.config;

import com.aliyun.teaopenapi.models.Config;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AliyunOssConfig {

    @Bean(name = "aliyunStsClient")
    public com.aliyun.sts20150401.Client aliyunStsClient(AliyunOssProperties properties) throws Exception {
        String accessKeyId = properties.getAccessKeyId() == null ? "" : properties.getAccessKeyId();
        String accessKeySecret = properties.getAccessKeySecret() == null ? "" : properties.getAccessKeySecret();
        Config config = new Config()
                .setAccessKeyId(accessKeyId)
                .setAccessKeySecret(accessKeySecret);
        config.endpoint = properties.getStsEndpoint();
        return new com.aliyun.sts20150401.Client(config);
    }
}
