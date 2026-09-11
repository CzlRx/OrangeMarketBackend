package com.czlr.orangemarketbackend.websocket;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * 客服 WebSocket 在线连接表（内存）。
 *
 * <p>业务会话在数据库；这里只记录「谁当前连着哪条 WS」。
 */
@Component
public class ServiceWebSocketSessionRegistry {

    private final Map<Long, Set<WebSocketSession>> userSessions = new ConcurrentHashMap<>();
    private final Map<Long, Set<WebSocketSession>> agentSessions = new ConcurrentHashMap<>();

    public void register(Long userId, boolean agent, WebSocketSession session) {
        Map<Long, Set<WebSocketSession>> target = agent ? agentSessions : userSessions;
        target.computeIfAbsent(userId, id -> new CopyOnWriteArraySet<>()).add(session);
    }

    public void unregister(WebSocketSession session) {
        Long userId = (Long) session.getAttributes().get(ServiceWebSocketAttributes.USER_ID);
        Boolean agent = (Boolean) session.getAttributes().get(ServiceWebSocketAttributes.IS_AGENT);
        if (userId == null || agent == null) {
            return;
        }
        Map<Long, Set<WebSocketSession>> target = agent ? agentSessions : userSessions;
        Set<WebSocketSession> sessions = target.get(userId);
        if (sessions == null) {
            return;
        }
        sessions.remove(session);
        if (sessions.isEmpty()) {
            target.remove(userId, sessions);
        }
    }

    public void sendToUser(Long userId, String payload) {
        send(userSessions.get(userId), payload);
    }

    public void sendToAgent(Long agentId, String payload) {
        send(agentSessions.get(agentId), payload);
    }

    /** 向所有在线客服广播（大厅通知预留）。 */
    public void broadcastToAgents(String payload) {
        for (Set<WebSocketSession> sessions : agentSessions.values()) {
            send(sessions, payload);
        }
    }

    private void send(Set<WebSocketSession> sessions, String payload) {
        if (sessions == null || sessions.isEmpty()) {
            return;
        }
        TextMessage message = new TextMessage(payload);
        for (WebSocketSession session : sessions) {
            if (session == null || !session.isOpen()) {
                continue;
            }
            try {
                synchronized (session) {
                    session.sendMessage(message);
                }
            } catch (IOException ignored) {
                // 发送失败时由后续关闭回调清理连接
            }
        }
    }
}
