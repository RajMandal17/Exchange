package com.custom.marketdata.binance.service;

import com.custom.marketdata.binance.config.BinanceOrderbookConfig;
import com.custom.marketdata.binance.scheduler.ProductRefreshScheduler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

/**
 * Main service that orchestrates Binance orderbook integration.
 * This service initializes the WebSocket connections and manages
 * the synchronization of orderbooks between Binance and the local system.
 */
@Service
public class BinanceOrderbookService {
    private static final Logger logger = LoggerFactory.getLogger(BinanceOrderbookService.class);

    @Autowired
    private BinanceOrderbookConfig config;
    
    @Autowired
    private WebSocketConnectionService connectionService;
    
    @Autowired
    private BotOrderManagementService botOrderManagementService;
    
    @Autowired
    private ProductRefreshScheduler productRefreshScheduler;

    /**
     * Initializes the Binance orderbook service.
     * This method is called after all dependencies are injected.
     */
    @PostConstruct
    public void initialize() {
        if (!config.isEnabled()) {
            logger.info("Binance orderbook integration is disabled");
            return;
        }
        
        logger.info("Initializing Binance orderbook service");
        
        try {
            // Initialize product mapping
            productRefreshScheduler.refreshProductMapping();
            
            // The connection is initialized as part of the product refresh
            logger.info("Binance orderbook service initialized successfully");
        } catch (Exception e) {
            logger.error("Failed to initialize Binance orderbook service: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Cleans up resources when the service is being shutdown.
     */
    @PreDestroy
    public void cleanup() {
        if (!config.isEnabled()) {
            return;
        }
        
        logger.info("Shutting down Binance orderbook service");
        
        try {
            connectionService.disconnect();
            
            // Cancel all bot orders by cleaning up orderbooks
            cleanupAllBotOrders();
            
            logger.info("Binance orderbook service shutdown complete");
        } catch (Exception e) {
            logger.error("Error during Binance orderbook service shutdown: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Manually triggers a reconnection to Binance WebSocket.
     * This can be useful for recovery after network issues.
     */
    public void reconnectWebSockets() {
        logger.info("Manually triggering WebSocket reconnection");
        connectionService.disconnect();
        productRefreshScheduler.refreshProductMapping();
    }
    
    /**
     * Clean up all bot orders by canceling them.
     */
    private void cleanupAllBotOrders() {
        logger.info("Cleaning up all bot orders");
        try {
            for (String productId : connectionService.getProductToSymbolMap().keySet()) {
                botOrderManagementService.cleanupStaleOrders(productId);
            }
        } catch (Exception e) {
            logger.error("Error cleaning up bot orders: {}", e.getMessage(), e);
        }
    }
}
