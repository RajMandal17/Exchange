package com.custom.marketdata.binance.scheduler;

import com.custom.marketdata.binance.config.BinanceOrderbookConfig;
import com.custom.marketdata.binance.service.WebSocketConnectionService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ConnectionHealthScheduler {
    private static final Logger logger = LoggerFactory.getLogger(ConnectionHealthScheduler.class);
    
    @Autowired
    private BinanceOrderbookConfig config;
    
    @Autowired
    private WebSocketConnectionService connectionService;
    
    @Scheduled(fixedRate = 30000) // 30 seconds
    public void checkWebSocketConnection() {
        if (!config.isEnabled()) {
            return;
        }
        
        if (!connectionService.isConnected()) {
            logger.info("WebSocket connection check: Reconnecting to Binance WebSocket...");
            connectionService.connectToWebSocket();
        }
    }
}
