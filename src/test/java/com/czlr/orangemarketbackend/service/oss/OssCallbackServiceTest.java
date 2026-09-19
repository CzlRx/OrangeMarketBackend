package com.czlr.orangemarketbackend.service.oss;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OssCallbackServiceTest {

    @Test
    void parseFormDecodesSceneAndUserId() {
        byte[] body = "bucket=orange-market&object=avatars%2F10001%2Fa.png&scene=avatar&userId=10001"
                .getBytes(StandardCharsets.UTF_8);
        Map<String, String> form = OssCallbackService.parseForm(body);
        assertEquals("orange-market", form.get("bucket"));
        assertEquals("avatars/10001/a.png", form.get("object"));
        assertEquals("avatar", form.get("scene"));
        assertEquals("10001", form.get("userId"));
    }
}
