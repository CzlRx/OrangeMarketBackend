package com.czlr.orangemarketbackend.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.IService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.czlr.orangemarketbackend.common.ResultCode;
import com.czlr.orangemarketbackend.common.exception.BusinessException;
import com.czlr.orangemarketbackend.entity.dto.*;
import com.czlr.orangemarketbackend.entity.po.UserAccount;
import com.czlr.orangemarketbackend.mapper.UserAccountMapper;
import com.google.code.kaptcha.Producer;
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

    private final RedisTemplate<String, String> redisTemplate;

    private final Producer captchaProducer;

    private final UserAccountMapper userAccountMapper;

    public AuthService(RedisTemplate<String, String> redisTemplate, Producer captchaProducer,UserAccountMapper userAccountMapper) {
        this.redisTemplate = redisTemplate;
        this.captchaProducer = captchaProducer;
        this.userAccountMapper = userAccountMapper;

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
        String s = redisTemplate.opsForValue().get("auth:captcha:" + smsRequest.getCaptchaKey());
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

    @PostMapping
    public LoginDTO login(@RequestBody LoginRequest loginRequest){
        if (checkSms(loginRequest.getSmsCode())){
            String token = UUID.randomUUID().toString();
            boolean isNewUser;
            Map<String, String> map = new HashMap<>();
            ObjectMapper objectMapper = new ObjectMapper();
            LambdaQueryWrapper<UserAccount> lambdaQueryWrapper = new LambdaQueryWrapper<>();
            lambdaQueryWrapper.eq(UserAccount::getPhone,loginRequest.getPhone());

            UserAccount userAccount = getOne(lambdaQueryWrapper);

            if(userAccount != null){
                isNewUser = false;
                map = objectMapper.convertValue(userAccount, Map.class);
            }else{
                UserAccount userAccount1 = new UserAccount();
                userAccount1.setPhone(loginRequest.getPhone());
                String phone = loginRequest.getPhone();
                String phoneSuffix = phone.length() > 4 ? phone.substring(phone.length() - 4) : phone;
                userAccount1.setNickname("橙子用户" + phoneSuffix);
                save(userAccount1);
                map = objectMapper.convertValue(userAccount1, Map.class);
                isNewUser = true;
            }

            String redisKey = "auth:login:"+ token;

            redisTemplate.opsForHash().putAll(redisKey,toRedisHash(map));
            Instant expiretime = Instant.now().plusSeconds(86400);
            String isotime = expiretime.toString();
            return new LoginDTO(token,isotime,isNewUser);
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
        String s = redisTemplate.opsForValue().get("auth:sms:" + smscode);
        if(s == null){
            throw new BusinessException(ResultCode.SMS_CODE_INVALID);
        }
        return true;
    }
}
