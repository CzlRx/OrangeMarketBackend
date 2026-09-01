package com.czlr.orangemarketbackend.service;

import com.czlr.orangemarketbackend.entity.dto.CaptchaDTO;
import com.google.code.kaptcha.Producer;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.Base64;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
public class AuthService {

    private final RedisTemplate<String, String> redisTemplate;

    private final Producer captchaProducer;

    public AuthService(RedisTemplate<String, String> redisTemplate, Producer captchaProducer) {
        this.redisTemplate = redisTemplate;
        this.captchaProducer = captchaProducer;
    }

    public CaptchaDTO getCaptcha() {
        String captchaKey = UUID.randomUUID().toString();
        String captchaText = captchaProducer.createText();
        BufferedImage bufferedImage = captchaProducer.createImage(captchaText);

        String redisKey = "auth:captcha:" + captchaKey;
        redisTemplate.opsForValue().set(redisKey, captchaText, 5, TimeUnit.MINUTES);

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
}
