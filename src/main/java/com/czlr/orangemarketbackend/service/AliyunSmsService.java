package com.czlr.orangemarketbackend.service;

import com.aliyun.dypnsapi20170525.Client;
import com.aliyun.dypnsapi20170525.models.CheckSmsVerifyCodeRequest;
import com.aliyun.dypnsapi20170525.models.CheckSmsVerifyCodeResponse;
import com.aliyun.dypnsapi20170525.models.SendSmsVerifyCodeRequest;
import com.aliyun.dypnsapi20170525.models.SendSmsVerifyCodeResponse;
import com.czlr.orangemarketbackend.common.ResultCode;
import com.czlr.orangemarketbackend.common.exception.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * 阿里云号码认证服务（Dypnsapi）短信认证
 *
 * 使用 SendSmsVerifyCode 发送验证码（验证码由阿里云服务端生成并托管），
 * 使用 CheckSmsVerifyCode 校验验证码（无需本地存储验证码）。
 */
@Service
public class AliyunSmsService {

    private static final Logger log = LoggerFactory.getLogger(AliyunSmsService.class);

    private final Client dypnsApiClient;

    @Value("${aliyun.sms.sign-name}")
    private String signName;

    @Value("${aliyun.sms.template-code}")
    private String templateCode;

    public AliyunSmsService(Client dypnsApiClient) {
        this.dypnsApiClient = dypnsApiClient;
    }

    /**
     * 发送短信验证码（由阿里云按 CodeType/CodeLength 规则生成，有效期 300 秒）
     *
     * @param phone 手机号
     */
    public void sendVerifyCode(String phone) {
        SendSmsVerifyCodeRequest request = new SendSmsVerifyCodeRequest()
                .setPhoneNumber(phone)
                .setSignName(signName)
                .setTemplateCode(templateCode)
                // ##code## 占位符：由阿里云服务端生成验证码
                .setTemplateParam("{\"code\":\"##code##\",\"min\":\"5\"}")
                .setCodeType(1L)          // 纯数字
                .setCodeLength(6L)       // 6 位
                .setValidTime(300L)      // 有效期 5 分钟
                .setInterval(60L)        // 同号码 60 秒内只能发一次
                .setDuplicatePolicy(1L); // 新验证码发送后旧验证码立即失效
        try {
            SendSmsVerifyCodeResponse response = dypnsApiClient.sendSmsVerifyCode(request);
            String code = response.getBody().getCode();
            if (!"OK".equals(code)) {
                log.error("阿里云短信验证码发送失败 phone={} code={} message={}", phone, code, response.getBody().getMessage());
                throw new BusinessException(ResultCode.INTERNAL_SERVER_ERROR, "短信发送失败，请稍后重试");
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("阿里云短信验证码发送异常 phone={}", phone, e);
            throw new BusinessException(ResultCode.INTERNAL_SERVER_ERROR, "短信发送失败，请稍后重试");
        }
    }

    /**
     * 校验短信验证码（由阿里云服务端核验）
     *
     * @param phone     手机号
     * @param verifyCode 用户输入的验证码
     * @return 是否通过
     */
    public boolean checkVerifyCode(String phone, String verifyCode) {
        CheckSmsVerifyCodeRequest request = new CheckSmsVerifyCodeRequest()
                .setPhoneNumber(phone)
                .setVerifyCode(verifyCode);
        try {
            CheckSmsVerifyCodeResponse response = dypnsApiClient.checkSmsVerifyCode(request);
            String code = response.getBody().getCode();
            String message = response.getBody().getMessage();
            Boolean success = response.getBody().getSuccess();
            // 该 SDK 版本的响应体不含 requestId 字段，RequestId 在 HTTP 响应头中
            String requestId = response.getHeaders() != null
                    ? response.getHeaders().get("x-acs-request-id") : null;
            String verifyResult = response.getBody().getModel() != null
                    ? response.getBody().getModel().getVerifyResult() : null;
            if (!"OK".equals(code)) {
                log.warn("阿里云短信验证码核验失败 phone={} code={} message={} requestId={}",
                        phone, code, message, requestId);
                return false;
            }
            // 阿里云号码认证服务核验通过时返回 "PASS"（部分版本/场景返回 "true"），两者都视为通过
            boolean ok = "PASS".equalsIgnoreCase(verifyResult) || "true".equalsIgnoreCase(verifyResult);
            if (!ok) {
                // 阿里云接口调用成功但判定验证码无效：通常是验证码过期、已被新验证码作废、或输入不匹配
                log.warn("阿里云短信验证码核验未通过 phone={} verifyResult={} success={} message={} requestId={}",
                        phone, verifyResult, success, message, requestId);
            }
            return ok;
        } catch (Exception e) {
            log.error("阿里云短信验证码核验异常 phone={}", phone, e);
            return false;
        }
    }
}
