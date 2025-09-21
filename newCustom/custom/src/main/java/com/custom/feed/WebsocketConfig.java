package com.custom.feed;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class WebsocketConfig implements WebSocketConfigurer {
    private final FeedTextWebSocketHandler myHandler;
    private final AuthHandshakeInterceptor authHandshakeInterceptor;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        // Register WebSocket handlers for different paths to match frontend expectations
        registry.addHandler(myHandler, "/ws")
                .addInterceptors(authHandshakeInterceptor)
                .setAllowedOrigins("*");
        
        // Add specific endpoints for frontend ranger URL (public and private streams only)
        registry.addHandler(myHandler, "/api/v1/stream/public/")
                .addInterceptors(authHandshakeInterceptor)
                .setAllowedOrigins("*");
                
        registry.addHandler(myHandler, "/api/v1/stream/private/")
                .addInterceptors(authHandshakeInterceptor)
                .setAllowedOrigins("*");
                
        // Add v2 ranger endpoints for frontend compatibility
        registry.addHandler(myHandler, "/api/v2/ranger/public/")
                .addInterceptors(authHandshakeInterceptor)
                .setAllowedOrigins("*");
                
        registry.addHandler(myHandler, "/api/v2/ranger/private/")
                .addInterceptors(authHandshakeInterceptor)
                .setAllowedOrigins("*");
                
        // Alternative paths without trailing slash
        registry.addHandler(myHandler, "/api/v2/ranger/public")
                .addInterceptors(authHandshakeInterceptor)
                .setAllowedOrigins("*");
                
        registry.addHandler(myHandler, "/api/v2/ranger/private")
                .addInterceptors(authHandshakeInterceptor)
                .setAllowedOrigins("*");
    }

}
