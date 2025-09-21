package com.custom.marketdata.binance.service;

import com.custom.marketdata.binance.config.BinanceOrderbookConfig;
import com.custom.marketdata.binance.exception.BinanceIntegrationException;
import com.custom.marketdata.binance.handler.BinanceWebSocketHandler;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.concurrent.ListenableFuture;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.WebSocketClient;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class WebSocketConnectionService {
    private static final Logger logger = LoggerFactory.getLogger(WebSocketConnectionService.class);
    private final BinanceOrderbookConfig config;
    private final WebSocketClient webSocketClient;
    private final BinanceWebSocketHandler webSocketHandler;
    
    @Getter
    private Map<String, String> productToSymbolMap = new ConcurrentHashMap<>();
    private WebSocketSession webSocketSession;
    
    public void initialize(Map<String, String> productMapping) {
        this.productToSymbolMap.clear();
        this.productToSymbolMap.putAll(productMapping);
        connectToWebSocket();
    }
    
    public void connectToWebSocket() {
        try {
            // Get all binance symbols from our mapping
            List<String> symbols = new ArrayList<>(productToSymbolMap.values());
            
            if (symbols.isEmpty()) {
                logger.warn("No product mappings available. Cannot connect to Binance WebSocket.");
                return;
            }
            
            // Create stream names for each symbol (e.g., "btcusdt@depth20")
            String streams = String.join("/", symbols.stream()
                    .map(symbol -> symbol.toLowerCase() + "@depth20")
                    .collect(Collectors.toList()));
            
            String wsUrl = config.getWebsocketUrl() + streams;
            logger.info("Connecting to Binance WebSocket with {} products: {}", symbols.size(), wsUrl);
            
            ListenableFuture<WebSocketSession> future = webSocketClient.doHandshake(
                webSocketHandler, new WebSocketHttpHeaders(), URI.create(wsUrl));
                
            future.addCallback(session -> {
                    this.webSocketSession = session;
                    logger.info("Successfully connected to Binance WebSocket");
                }, 
                ex -> {
                    logger.error("Failed to connect to Binance WebSocket", ex);
                    // Attempt reconnect after delay
                    new Thread(() -> {
                        try {
                            Thread.sleep(5000);
                            connectToWebSocket();
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                    }).start();
                });
        } catch (Exception e) {
            logger.error("Error initializing Binance WebSocket connection", e);
            throw new BinanceIntegrationException("Failed to initialize WebSocket connection", e);
        }
    }
    
    public boolean isConnected() {
        return webSocketSession != null && webSocketSession.isOpen();
    }
    
    public void disconnect() {
        if (webSocketSession != null && webSocketSession.isOpen()) {
            try {
                webSocketSession.close();
                logger.info("Disconnected from Binance WebSocket");
            } catch (Exception e) {
                logger.warn("Error closing WebSocket session", e);
            } finally {
                webSocketSession = null;
            }
        }
    }
}
