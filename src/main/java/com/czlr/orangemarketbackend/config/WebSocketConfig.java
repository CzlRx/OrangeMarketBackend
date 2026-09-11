package com.czlr.orangemarketbackend.config;

import com.czlr.orangemarketbackend.websocket.ServiceWebSocketAuthInterceptor;
import com.czlr.orangemarketbackend.websocket.ServiceWebSocketHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * 注册客服 WebSocket 端点。
 *
 * <p>连接示例：{@code ws://host/ws/service?token={jwt}}
 */
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final ServiceWebSocketHandler serviceWebSocketHandler;
    private final ServiceWebSocketAuthInterceptor serviceWebSocketAuthInterceptor;

    public WebSocketConfig(
            ServiceWebSocketHandler serviceWebSocketHandler,
            ServiceWebSocketAuthInterceptor serviceWebSocketAuthInterceptor) {
        this.serviceWebSocketHandler = serviceWebSocketHandler;
        this.serviceWebSocketAuthInterceptor = serviceWebSocketAuthInterceptor;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(serviceWebSocketHandler, "/ws/service")
                .addInterceptors(serviceWebSocketAuthInterceptor)
                .setAllowedOriginPatterns("*");
    }
}
