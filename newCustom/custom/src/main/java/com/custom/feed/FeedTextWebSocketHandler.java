package com.custom.feed;

import com.alibaba.fastjson.JSON;
import com.custom.feed.message.Request;
import com.custom.feed.message.SubscribeRequest;
import com.custom.feed.message.UnsubscribeRequest;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

@Slf4j
@Component
@RequiredArgsConstructor
public class FeedTextWebSocketHandler extends TextWebSocketHandler {
    private final SessionManager sessionManager;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        // Handle query parameter-based subscriptions (Peatio/Finex style)
        String query = session.getUri().getQuery();
        if (query != null && query.contains("stream=")) {
            // Parse stream parameters from query string
            String[] params = query.split("&");
            java.util.List<String> channels = new java.util.ArrayList<>();
            java.util.List<String> productIds = new java.util.ArrayList<>();
            
            for (String param : params) {
                if (param.startsWith("stream=")) {
                    String stream = param.substring(7); // Remove "stream="
                    channels.add(stream);
                    
                    // Extract product ID if it's a market-specific stream
                    if (stream.contains(".")) {
                        String productId = stream.split("\\.")[0];
                        if (!productIds.contains(productId)) {
                            productIds.add(productId);
                        }
                    }
                }
            }
            
            if (!channels.isEmpty()) {
                sessionManager.subOrUnSub(session, productIds, new java.util.ArrayList<>(), channels, true);
            }
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        sessionManager.removeSession(session);
    }

    @Override
    @SneakyThrows
    public void handleTextMessage(WebSocketSession session, TextMessage message) {
        // Handle null or empty messages gracefully
        String payload = message.getPayload();
        if (payload == null || payload.trim().isEmpty()) {
            logger.debug("Received null or empty message from session: {}", session.getId());
            return;
        }
        
        logger.info("📥 WebSocket message received from session {}: {}", session.getId(), payload);
        
        try {
            Request request = JSON.parseObject(payload, Request.class);
            if (request == null || request.getType() == null) {
                logger.debug("Invalid request format from session: {} - payload: {}", session.getId(), payload);
                return;
            }

            logger.info("🔍 Parsed request type: {} from session: {}", request.getType(), session.getId());

            switch (request.getType()) {
                case "subscribe": {
                    SubscribeRequest subscribeRequest = JSON.parseObject(payload, SubscribeRequest.class);
                    logger.info("📡 Subscribe request: productIds={}, channels={}, currencyIds={}", 
                               subscribeRequest.getProductIds(), 
                               subscribeRequest.getChannels(), 
                               subscribeRequest.getCurrencyIds());
                    sessionManager.subOrUnSub(session, subscribeRequest.getProductIds(), subscribeRequest.getCurrencyIds(),
                            subscribeRequest.getChannels(), true);
                    break;
                }
                case "unsubscribe": {
                    UnsubscribeRequest unsubscribeRequest = JSON.parseObject(payload, UnsubscribeRequest.class);
                    logger.info("🔌 Unsubscribe request: productIds={}, channels={}, currencyIds={}", 
                               unsubscribeRequest.getProductIds(), 
                               unsubscribeRequest.getChannels(), 
                               unsubscribeRequest.getCurrencyIds());
                    sessionManager.subOrUnSub(session, unsubscribeRequest.getProductIds(),
                            unsubscribeRequest.getCurrencyIds(),
                            unsubscribeRequest.getChannels(), false);
                    break;
                }
                case "ping":
                    logger.info("🏓 Ping received from session: {}", session.getId());
                    sessionManager.sendPong(session);
                    break;
                default:
                    logger.debug("Unknown request type: {} from session: {}", request.getType(), session.getId());
                    break;
            }
        } catch (Exception e) {
            logger.debug("Error processing WebSocket message from session: {} - message: {} - error: {}", 
                        session.getId(), payload, e.getMessage());
        }
    }

}
