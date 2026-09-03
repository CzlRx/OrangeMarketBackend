package com.czlr.orangemarketbackend.utils;

public final class AuthRedisKey {

    private static final String LOGIN_KEY_PREFIX = "auth:login:";

    private AuthRedisKey() {
    }

    public static String login(Long userId, String sessionId) {
        return LOGIN_KEY_PREFIX + userId + sessionId;
    }
}
