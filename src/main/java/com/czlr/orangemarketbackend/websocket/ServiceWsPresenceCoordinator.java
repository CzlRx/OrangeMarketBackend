package com.czlr.orangemarketbackend.websocket;

import com.czlr.orangemarketbackend.service.ServiceSessionService;
import com.czlr.orangemarketbackend.service.ServiceSessionService.WsPresenceKind;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * 根据「该身份是否还有打开的 WS」发进入/离开/回来系统消息。
 *
 * <p>断开后延迟确认，避免握手关闭与立刻重连交叉。
 * 仅在确实写入过系统消息时才更新宣告状态，避免无会话时的断开把下次连上当成「重新进入」。
 */
@Component
public class ServiceWsPresenceCoordinator {

    private static final Logger log = LoggerFactory.getLogger(ServiceWsPresenceCoordinator.class);
    private static final long LEAVE_DELAY_MS = 800;

    private final ServiceSessionService serviceSessionService;
    private final ServiceWebSocketSessionRegistry sessionRegistry;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread thread = new Thread(r, "service-ws-presence");
        thread.setDaemon(true);
        return thread;
    });
    /** true=已宣告进入/在线，false=已宣告离开 */
    private final ConcurrentHashMap<String, Boolean> announcedOnline = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ScheduledFuture<?>> pendingLeave = new ConcurrentHashMap<>();

    public ServiceWsPresenceCoordinator(
            ServiceSessionService serviceSessionService,
            ServiceWebSocketSessionRegistry sessionRegistry) {
        this.serviceSessionService = serviceSessionService;
        this.sessionRegistry = sessionRegistry;
    }

    public void onConnected(Long userId, boolean agent) {
        if (userId == null) {
            return;
        }
        String key = presenceKey(userId, agent);
        cancelPendingLeave(key);
        Boolean previous = announcedOnline.get(key);
        if (Boolean.TRUE.equals(previous)) {
            return;
        }
        WsPresenceKind kind = Boolean.FALSE.equals(previous)
                ? WsPresenceKind.REJOIN
                : WsPresenceKind.JOIN;
        if (notifyQuietly(userId, agent, kind)) {
            announcedOnline.put(key, true);
        }
    }

    public void onDisconnected(Long userId, boolean agent, boolean wentOffline) {
        if (userId == null || !wentOffline) {
            return;
        }
        String key = presenceKey(userId, agent);
        ScheduledFuture<?> next = scheduler.schedule(() -> {
            pendingLeave.remove(key);
            if (sessionRegistry.isOnline(userId, agent)) {
                return;
            }
            if (notifyQuietly(userId, agent, WsPresenceKind.LEAVE)) {
                announcedOnline.put(key, false);
            }
        }, LEAVE_DELAY_MS, TimeUnit.MILLISECONDS);
        ScheduledFuture<?> old = pendingLeave.put(key, next);
        if (old != null) {
            old.cancel(false);
        }
    }

    @PreDestroy
    public void shutdown() {
        scheduler.shutdownNow();
    }

    private void cancelPendingLeave(String key) {
        ScheduledFuture<?> pending = pendingLeave.remove(key);
        if (pending != null) {
            pending.cancel(false);
        }
    }

    private boolean notifyQuietly(Long userId, boolean agent, WsPresenceKind kind) {
        try {
            return serviceSessionService.notifyWsPresence(userId, agent, kind);
        } catch (Exception ex) {
            log.warn("notify ws presence failed, userId={}, agent={}, kind={}",
                    userId, agent, kind, ex);
            return false;
        }
    }

    private String presenceKey(Long userId, boolean agent) {
        return (agent ? "a:" : "u:") + userId;
    }
}
