package com.czlr.orangemarketbackend.service.oss;

import com.aliyun.sts20150401.Client;
import com.aliyun.sts20150401.models.AssumeRoleRequest;
import com.aliyun.sts20150401.models.AssumeRoleResponse;
import com.aliyun.sts20150401.models.AssumeRoleResponseBody;
import com.aliyun.tea.TeaException;
import com.aliyun.teautil.models.RuntimeOptions;
import com.czlr.orangemarketbackend.common.ResultCode;
import com.czlr.orangemarketbackend.common.exception.BusinessException;
import com.czlr.orangemarketbackend.config.AliyunOssProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

@Component
public class AliyunOssStsCredentialProvider implements OssStsCredentialProvider {

    private static final Logger log = LoggerFactory.getLogger(AliyunOssStsCredentialProvider.class);

    private final Client stsClient;
    private final AliyunOssProperties properties;

    public AliyunOssStsCredentialProvider(
            @Qualifier("aliyunStsClient") Client stsClient,
            AliyunOssProperties properties) {
        this.stsClient = stsClient;
        this.properties = properties;
    }

    @Override
    public OssStsCredentials assumeRole(String roleSessionName, long durationSeconds) {
        AssumeRoleRequest request = new AssumeRoleRequest()
                .setRoleArn(properties.getStsRoleArn())
                .setRoleSessionName(roleSessionName)
                .setDurationSeconds(Math.min(Math.max(durationSeconds, 900L), 3600L));
        try {
            AssumeRoleResponse response = stsClient.assumeRoleWithOptions(request, new RuntimeOptions());
            AssumeRoleResponseBody.AssumeRoleResponseBodyCredentials credentials =
                    response.getBody() == null ? null : response.getBody().getCredentials();
            if (credentials == null
                    || isBlank(credentials.getAccessKeyId())
                    || isBlank(credentials.getAccessKeySecret())
                    || isBlank(credentials.getSecurityToken())) {
                throw new BusinessException(ResultCode.INTERNAL_SERVER_ERROR, "获取对象存储临时凭证失败");
            }
            return new OssStsCredentials(
                    credentials.getAccessKeyId(),
                    credentials.getAccessKeySecret(),
                    credentials.getSecurityToken());
        } catch (BusinessException e) {
            throw e;
        } catch (TeaException e) {
            log.error("STS AssumeRole 失败 code={} message={}", e.getCode(), e.getMessage());
            throw new BusinessException(ResultCode.INTERNAL_SERVER_ERROR, "获取对象存储临时凭证失败");
        } catch (Exception e) {
            log.error("STS AssumeRole 异常", e);
            throw new BusinessException(ResultCode.INTERNAL_SERVER_ERROR, "获取对象存储临时凭证失败");
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
