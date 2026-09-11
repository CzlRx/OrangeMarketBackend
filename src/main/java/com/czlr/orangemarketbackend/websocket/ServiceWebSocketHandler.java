package com.czlr.orangemarketbackend.websocket;

import com.czlr.orangemarketbackend.common.exception.BusinessException;
import com.czlr.orangemarketbackend.service.ServiceSessionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.io.IOException;

/**
 * 客服通道 WebSocket 处理器：连接登记、心跳、聊天落库并推送。
 */
@Component
public class ServiceWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(ServiceWebSocketHandler.class);

    private final ServiceWebSocketSessionRegistry sessionRegistry;
    private final ObjectMapper objectMapper;
    private final ServiceSessionService serviceSessionService;

    public ServiceWebSocketHandler(
            ServiceWebSocketSessionRegistry sessionRegistry,
            ObjectMapper objectMapper,
            ServiceSessionService serviceSessionService) {
        this.sessionRegistry = sessionRegistry;
        this.objectMapper = objectMapper;
        this.serviceSessionService = serviceSessionService;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        Long userId = (Long) session.getAttributes().get(ServiceWebSocketAttributes.USER_ID);
        Boolean agent = (Boolean) session.getAttributes().get(ServiceWebSocketAttributes.IS_AGENT);
        if (userId == null || agent == null) {
            session.close(CloseStatus.NOT_ACCEPTABLE.withReason("unauthorized"));
            return;
        }

        sessionRegistry.register(userId, agent, session);
        log.info("service ws connected, userId={}, agent={}, sessionId={}",
                userId, agent, session.getId());

        ObjectNode connected = objectMapper.createObjectNode();
        connected.put("type", "connected");
        connected.put("userId", userId);
        connected.put("agent", agent);
        sendJson(session, connected);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        Long userId = (Long) session.getAttributes().get(ServiceWebSocketAttributes.USER_ID);
        Boolean agent = (Boolean) session.getAttributes().get(ServiceWebSocketAttributes.IS_AGENT);
        if (userId == null || agent == null) {
            session.close(CloseStatus.NOT_ACCEPTABLE.withReason("unauthorized"));
            return;
        }

        JsonNode root;
        try {
            root = objectMapper.readTree(message.getPayload());
        } catch (Exception ex) {
            sendError(session, "消息格式必须是 JSON");
            return;
        }

        String type = textOrNull(root, "type");
        if (type == null || type.isBlank()) {
            sendError(session, "缺少 type");
            return;
        }

        switch (type) {
            case "ping" -> {
                ObjectNode pong = objectMapper.createObjectNode();
                pong.put("type", "pong");
                sendJson(session, pong);
            }
            case "chat" -> handleChat(session, userId, agent, root);
            default -> sendError(session, "不支持的 type: " + type);
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessionRegistry.unregister(session);
        Long userId = (Long) session.getAttributes().get(ServiceWebSocketAttributes.USER_ID);
        log.info("service ws closed, userId={}, sessionId={}, status={}",
                userId, session.getId(), status);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        log.warn("service ws transport error, sessionId={}", session.getId(), exception);
        sessionRegistry.unregister(session);
    }

    /**
     * 聊天：先落库，再推给会话双方。
     */
    private void handleChat(WebSocketSession session, Long userId, boolean agent, JsonNode root)
            throws IOException {
        Long serviceSessionId = longOrNull(root, "sessionId");
        String content = textOrNull(root, "content");
        if (serviceSessionId == null) {
            sendError(session, "chat 缺少 sessionId");
            return;
        }
        try {
            serviceSessionService.sendChat(userId, agent, serviceSessionId, content);
        } catch (BusinessException ex) {
            sendError(session, ex.getMessage());
        }
    }

    private void sendError(WebSocketSession session, String message) throws IOException {
        ObjectNode error = objectMapper.createObjectNode();
        error.put("type", "error");
        error.put("message", message);
        sendJson(session, error);
    }

    private void sendJson(WebSocketSession session, ObjectNode node) throws IOException {
        if (!session.isOpen()) {
            return;
        }
        synchronized (session) {
            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(node)));
        }
    }

    private String textOrNull(JsonNode root, String field) {
        JsonNode node = root.get(field);
        if (node == null || node.isNull()) {
            return null;
        }
        return node.asString();
    }

    private Long longOrNull(JsonNode root, String field) {
        JsonNode node = root.get(field);
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isNumber()) {
            return node.asLong();
        }
        String text = node.asString();
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(text.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
