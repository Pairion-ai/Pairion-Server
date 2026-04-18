package com.pairion.gateway.config;

import com.pairion.gateway.ws.PairionWebSocketHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * Configures the raw WebSocket endpoint at {@code /ws/v1}.
 *
 * <p>Uses raw {@link org.springframework.web.socket.handler.BinaryWebSocketHandler} (not STOMP) to
 * support both JSON text frames and binary audio frames.
 */
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final PairionWebSocketHandler handler;

    /**
     * Constructs the WebSocket configuration.
     *
     * @param handler the Pairion WebSocket handler
     */
    public WebSocketConfig(PairionWebSocketHandler handler) {
        this.handler = handler;
    }

    /**
     * Registers the WebSocket handler at {@code /ws/v1} with unrestricted origins.
     *
     * @param registry the handler registry
     */
    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(handler, "/ws/v1").setAllowedOrigins("*");
    }
}
