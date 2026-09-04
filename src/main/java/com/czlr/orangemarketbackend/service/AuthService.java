package com.czlr.orangemarketbackend.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.IService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.czlr.orangemarketbackend.common.ResultCode;
import com.czlr.orangemarketbackend.common.enums.CommonStatus;
import com.czlr.orangemarketbackend.common.enums.ValueEnumUtils;
import com.czlr.orangemarketbackend.common.exception.BusinessException;
import com.czlr.orangemarketbackend.entity.dto.*;
import com.czlr.orangemarketbackend.entity.po.UserAccount;
import com.czlr.orangemarketbackend.mapper.UserAccountMapper;
import com.czlr.orangemarketbackend.utils.JwtUtil;
import com.czlr.orangemarketbackend.utils.AuthRedisKey;
import com.google.code.kaptcha.Producer;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import tools.jackson.databind.ObjectMapper;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
public class AuthService extends ServiceImpl<UserAccountMapper,UserAccount> {

    private final RedisTemplate<String, Object> redisTemplate;

    private final Producer captchaProducer;

    private final UserAccountMapper userAccountMapper;

    private final JwtUtil jwtUtil;

    public AuthService(
            @Qualifier("redisTemplate") RedisTemplate<String, Object> redisTemplate,
            Producer captchaProducer, UserAccountMapper userAccountMapper, JwtUtil jwtUtil) {
        this.redisTemplate = redisTemplate;
        this.captchaProducer = captchaProducer;
        this.userAccountMapper = userAccountMapper;
        this.jwtUtil = jwtUtil;
    }

    public CaptchaDTO getCaptcha() {
        String captchaKey = UUID.randomUUID().toString();
        String captchaText = captchaProducer.createText();
        BufferedImage bufferedImage = captchaProducer.createImage(captchaText);

        String redisKey = "auth:captcha:" + captchaKey;
        redisTemplate.opsForValue().set(redisKey, captchaText, 30, TimeUnit.SECONDS);

        String imageBase64 = bufferedImageToBase64(bufferedImage);

        return new CaptchaDTO(imageBase64, captchaKey);
    }

    private String bufferedImageToBase64(BufferedImage image) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try {
            ImageIO.write(image, "png", baos);
            byte[] imageBytes = baos.toByteArray();
            return "data:image/png;base64," + Base64.getEncoder().encodeToString(imageBytes);
        } catch (Exception e) {
            throw new RuntimeException("Failed to convert image to base64", e);
        }
    }

    public SmsInfo sendSms(SmsRequest smsRequest){
        String s = (String) redisTemplate.opsForValue().get("auth:captcha:" + smsRequest.getCaptchaKey());
        if(s == null){
            throw new BusinessException(ResultCode.NOT_FOUND,"图形验证码已过期");
        }else {
            if(s.equals(smsRequest.getCaptchaCode())){
                throw new BusinessException(ResultCode.BAD_REQUEST,"图形验证码错误");
            }
        }
        if(redisTemplate.opsForValue().get("auth:sms:limit:" + smsRequest.getPhone()) != null){
            throw new BusinessException(ResultCode.TOO_MANY_REQUESTS,"验证码请求过于频繁");
        }
        redisTemplate.opsForValue().set("auth:sms:limit:"+smsRequest.getPhone(),smsRequest.getPhone(),60,TimeUnit.SECONDS);
        //目前验证码先都默认为1234
        redisTemplate.opsForValue().set("auth:sms:1234",smsRequest.getPhone(),300,TimeUnit.SECONDS);
        return new SmsInfo(60,300);
    }


    /**
     * 登录接口
     * 功能：验证短信验证码后，生成 JWT token 并返回
     *
     * 流程：
     * 1. 验证短信验证码是否正确
     * 2. 根据手机号查询用户，如果不存在则自动注册
     * 3. 生成 UUID 作为 sessionId
     * 4. 将用户信息存入 Redis（key=auth:login:{userId}{sessionId}）
     * 5. 使用 JwtUtil 生成 JWT token（包含 sessionId + userId + phone）
     * 6. 返回 JWT token 给前端
     *
     * 改造前：返回裸的 UUID
     * 改造后：返回 JWT 包装后的 token（更安全，防篡改）
     *
     * @param loginRequest 登录请求（phone + smsCode）
     * @return LoginDTO（包含 JWT token、过期时间、是否新用户）
     */
    public LoginDTO login(LoginRequest loginRequest){
        // 1. 验证短信验证码
        if (checkSms(loginRequest.getSmsCode())){
            // 2. 生成 sessionId（用于 Redis 存储）
            String sessionId = UUID.randomUUID().toString();
            boolean isNewUser;
            Map<String, Object> map = new HashMap<>();
            ObjectMapper objectMapper = new ObjectMapper();
            LambdaQueryWrapper<UserAccount> lambdaQueryWrapper = new LambdaQueryWrapper<>();
            lambdaQueryWrapper.eq(UserAccount::getPhone,loginRequest.getPhone());

            UserAccount userAccount = getOne(lambdaQueryWrapper);

            // 3. 查询或创建用户
            if(userAccount != null){
                // 老用户：直接读取用户信息
                isNewUser = false;
                map = objectMapper.convertValue(userAccount, Map.class);
            }else{
                // 新用户：自动注册
                UserAccount userAccount1 = new UserAccount();
                userAccount1.setPhone(loginRequest.getPhone());
                userAccount1.setRole("USER");
                String phone = loginRequest.getPhone();
                String phoneSuffix = phone.length() > 4 ? phone.substring(phone.length() - 4) : phone;
                userAccount1.setNickname("橙子用户" + phoneSuffix);
                save(userAccount1);
                map = objectMapper.convertValue(userAccount1, Map.class);
                isNewUser = true;
                userAccount = userAccount1;
            }

            // 4. 将用户信息存入 Redis
            // Redis key 格式：auth:login:{userId}{sessionId}
            // 存储格式：Hash（方便单独更新某个字段）
            Long userId = userAccount.getId();
            String redisKey = AuthRedisKey.login(userId, sessionId);
            redisTemplate.opsForHash().putAll(redisKey, toRedisHash(map));
            redisTemplate.expire(redisKey, 86400, TimeUnit.SECONDS);  // 24 小时过期

            // 5. 生成 JWT token
            // JWT 包含：sessionId（用于查 Redis）、userId、phone
            // JWT 本身有签名和过期时间，防止篡改
            String jwtToken = jwtUtil.generateToken(sessionId, userId, loginRequest.getPhone());

            // 6. 返回 JWT token
            Instant expiretime = Instant.now().plusSeconds(86400);
            String isotime = expiretime.toString();
            return new LoginDTO(jwtToken, isotime, isNewUser);
        }

        throw new BusinessException(ResultCode.SMS_CODE_INVALID);

    }

    private Map<String, String> toRedisHash(Map<?, ?> source) {
        Map<String, String> stringValues = new HashMap<>();
        source.forEach((key, value) -> {
            if (key != null && value != null) {
                stringValues.put(String.valueOf(key), String.valueOf(value));
            }
        });
        return stringValues;
    }

    public boolean checkSms(String smscode){
        String s = (String) redisTemplate.opsForValue().get("auth:sms:" + smscode);
        if(s == null){
            throw new BusinessException(ResultCode.SMS_CODE_INVALID);
        }
        return true;
    }

    public UserAccount getMe(String sessionId, Long userId) {
        // 1. 从 Redis 中获取用户信息
        String redisKey = AuthRedisKey.login(userId, sessionId);
        Map<Object, Object> userInfoMap = redisTemplate.opsForHash().entries(redisKey);

        if (userInfoMap == null || userInfoMap.isEmpty()) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "未登录或会话已过期");
        }

        // 2. 将 Map 转换为 UserAccount 对象
        UserAccount userAccount = new UserAccount();
        userInfoMap.forEach((key, value) -> {
            switch (key.toString()) {
                case "id" -> userAccount.setId(Long.parseLong(value.toString()));
                case "phone" -> userAccount.setPhone(value.toString());
                case "nickname" -> userAccount.setNickname(value.toString());
                case "avatarUrl" -> userAccount.setAvatarUrl(value.toString());
                case "gender" -> userAccount.setGender(Integer.parseInt(value.toString()));
                case "birthday" -> userAccount.setBirthday(value == null ? null : java.time.LocalDate.parse(value.toString()));
                case "status" -> userAccount.setStatus(value == null ? null : ValueEnumUtils.fromValue(CommonStatus.class,value.toString()));
                case "role" -> userAccount.setRole(value.toString());
                case "lastLoginAt" -> userAccount.setLastLoginAt(value == null ? null : java.time.LocalDateTime.parse(value.toString()));
            }
        });
        return userAccount;
    }


    public void logout(String sessionId, Long userId) {
        // 1. 删除 Redis 中的用户信息
        String redisKey = AuthRedisKey.login(userId, sessionId);
        redisTemplate.delete(redisKey);
    }
}
