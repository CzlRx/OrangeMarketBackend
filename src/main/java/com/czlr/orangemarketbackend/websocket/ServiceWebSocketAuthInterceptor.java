package com.czlr.orangemarketbackend.websocket;

import com.czlr.orangemarketbackend.utils.AuthRedisKey;
import com.czlr.orangemarketbackend.utils.JwtUtil;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

/**
 * WebSocket 握手鉴权：校验 JWT + Redis 登录会话，并将身份写入连接 attributes。
 *
 * <p>浏览器原生 WebSocket 不便自定义 Header，因此优先读 query 参数 {@code token}，
 * 同时兼容 {@code Authorization: Bearer ...}。
 */
@Component
public class ServiceWebSocketAuthInterceptor implements HandshakeInterceptor {

    private final JwtUtil jwtUtil;
    private final RedisTemplate<String, Object> redisTemplate;

    public ServiceWebSocketAuthInterceptor(
            JwtUtil jwtUtil,
            @Qualifier("redisTemplate") RedisTemplate<String, Object> redisTemplate) {
        this.jwtUtil = jwtUtil;
        this.redisTemplate = redisTemplate;
    }

    @Override
    public boolean beforeHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler wsHandler,
            Map<String, Object> attributes) {
        String token = resolveToken(request);
        if (token == null || token.isBlank()) {
            return false;
        }
        if (!jwtUtil.validateToken(token)) {
            return false;
        }

        Long userId = jwtUtil.getUserIdFromToken(token);
        String loginSessionId = jwtUtil.getSessionIdFromToken(token);
        if (userId == null || loginSessionId == null || loginSessionId.isBlank()) {
            return false;
        }

        String redisKey = AuthRedisKey.login(userId, loginSessionId);
        Boolean hasKey = redisTemplate.hasKey(redisKey);
        if (hasKey == null || !hasKey) {
            return false;
        }

        Object roleValue = redisTemplate.opsForHash().get(redisKey, "role");
        String role = roleValue == null ? "USER" : String.valueOf(roleValue);
        boolean agent = isAdmin(role);

        attributes.put(ServiceWebSocketAttributes.USER_ID, userId);
        attributes.put(ServiceWebSocketAttributes.LOGIN_SESSION_ID, loginSessionId);
        attributes.put(ServiceWebSocketAttributes.ROLE, role);
        attributes.put(ServiceWebSocketAttributes.IS_AGENT, agent);
        return true;
    }

    @Override
    public void afterHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler wsHandler,
            Exception exception) {
        // no-op
    }

    private String resolveToken(ServerHttpRequest request) {
        if (request instanceof ServletServerHttpRequest servletRequest) {
            String queryToken = servletRequest.getServletRequest().getParameter("token");
            if (queryToken != null && !queryToken.isBlank()) {
                return queryToken;
            }
        }

        String authorization = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authorization != null && authorization.startsWith("Bearer ")) {
            return authorization.substring(7);
        }
        return null;
    }

    private boolean isAdmin(String role) {
        return role != null && "admin".equalsIgnoreCase(role.trim());
    }
}
