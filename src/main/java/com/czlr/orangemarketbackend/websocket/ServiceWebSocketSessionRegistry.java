package com.czlr.orangemarketbackend.websocket;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
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
    private final ConcurrentHashMap<String, Object> locks = new ConcurrentHashMap<>();

    /**
     * @return true 表示从离线变为在线（此前没有仍打开的连接）
     */
    public boolean register(Long userId, boolean agent, WebSocketSession session) {
        Object lock = lockOf(userId, agent);
        synchronized (lock) {
            Map<Long, Set<WebSocketSession>> target = agent ? agentSessions : userSessions;
            Set<WebSocketSession> sessions = target.computeIfAbsent(userId, id -> new CopyOnWriteArraySet<>());
            pruneClosed(sessions);
            closeOthers(sessions, session);
            boolean firstOnline = !hasOpenSession(sessions);
            sessions.add(session);
            return firstOnline;
        }
    }

    /**
     * @return true 表示从在线变为离线（没有仍打开的连接）
     */
    public boolean unregister(WebSocketSession session) {
        Long userId = (Long) session.getAttributes().get(ServiceWebSocketAttributes.USER_ID);
        Boolean agent = (Boolean) session.getAttributes().get(ServiceWebSocketAttributes.IS_AGENT);
        if (userId == null || agent == null) {
            return false;
        }
        Object lock = lockOf(userId, agent);
        synchronized (lock) {
            Map<Long, Set<WebSocketSession>> target = agent ? agentSessions : userSessions;
            Set<WebSocketSession> sessions = target.get(userId);
            if (sessions == null) {
                return false;
            }
            sessions.remove(session);
            pruneClosed(sessions);
            boolean wentOffline = !hasOpenSession(sessions);
            if (sessions.isEmpty()) {
                target.remove(userId, sessions);
            }
            return wentOffline;
        }
    }

    public boolean isOnline(Long userId, boolean agent) {
        Object lock = lockOf(userId, agent);
        synchronized (lock) {
            Map<Long, Set<WebSocketSession>> target = agent ? agentSessions : userSessions;
            Set<WebSocketSession> sessions = target.get(userId);
            pruneClosed(sessions);
            return hasOpenSession(sessions);
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

    private void closeOthers(Set<WebSocketSession> sessions, WebSocketSession current) {
        List<WebSocketSession> others = new ArrayList<>();
        for (WebSocketSession existing : sessions) {
            if (existing != null && existing != current) {
                others.add(existing);
            }
        }
        for (WebSocketSession existing : others) {
            sessions.remove(existing);
            if (existing.isOpen()) {
                try {
                    existing.close(CloseStatus.NORMAL);
                } catch (IOException ignored) {
                    // 关闭回调里会再走 unregister
                }
            }
        }
    }

    private void pruneClosed(Set<WebSocketSession> sessions) {
        if (sessions == null || sessions.isEmpty()) {
            return;
        }
        for (WebSocketSession session : sessions) {
            if (session == null || !session.isOpen()) {
                sessions.remove(session);
            }
        }
    }

    private boolean hasOpenSession(Set<WebSocketSession> sessions) {
        if (sessions == null || sessions.isEmpty()) {
            return false;
        }
        for (WebSocketSession session : sessions) {
            if (session != null && session.isOpen()) {
                return true;
            }
        }
        return false;
    }

    private void send(Set<WebSocketSession> sessions, String payload) {
        if (sessions == null || sessions.isEmpty()) {
            return;
        }
        TextMessage message = new TextMessage(payload);
        for (WebSocketSession session : List.copyOf(sessions)) {
            if (session == null || !session.isOpen()) {
                sessions.remove(session);
                continue;
            }
            try {
                synchronized (session) {
                    session.sendMessage(message);
                }
            } catch (IOException | IllegalStateException ex) {
                sessions.remove(session);
            }
        }
    }

    private Object lockOf(Long userId, boolean agent) {
        return locks.computeIfAbsent((agent ? "a:" : "u:") + userId, key -> new Object());
    }
}
