package com.pairion.gateway.config;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pairion.gateway.ws.PairionWebSocketHandler;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistration;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/** Tests for {@link WebSocketConfig}. */
class WebSocketConfigTest {

    @Test
    void registerWebSocketHandlers() {
        PairionWebSocketHandler handler = mock(PairionWebSocketHandler.class);
        WebSocketConfig config = new WebSocketConfig(handler);

        WebSocketHandlerRegistry registry = mock(WebSocketHandlerRegistry.class);
        WebSocketHandlerRegistration registration = mock(WebSocketHandlerRegistration.class);
        when(registry.addHandler(any(), eq("/ws/v1"))).thenReturn(registration);
        when(registration.setAllowedOrigins("*")).thenReturn(registration);

        config.registerWebSocketHandlers(registry);

        verify(registry).addHandler(handler, "/ws/v1");
        verify(registration).setAllowedOrigins("*");
    }
}
