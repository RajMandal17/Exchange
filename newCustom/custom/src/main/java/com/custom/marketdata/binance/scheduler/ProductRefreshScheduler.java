package com.custom.marketdata.binance.scheduler;

import com.custom.marketdata.binance.config.BinanceOrderbookConfig;
import com.custom.marketdata.binance.mapper.ProductSymbolMapper;
import com.custom.marketdata.binance.service.WebSocketConnectionService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;

@Component
public class ProductRefreshScheduler {
    private static final Logger logger = LoggerFactory.getLogger(ProductRefreshScheduler.class);
    
    @Autowired
    private BinanceOrderbookConfig config;
    
    @Autowired
    private ProductSymbolMapper productMapper;
    
    @Autowired
    private WebSocketConnectionService connectionService;
    
    @Scheduled(fixedRateString = "${binance.orderbook.product.refresh.interval}")
    public void refreshProductMapping() {
        if (!config.isEnabled()) {
            return;
        }
        
        logger.debug("Refreshing product mapping...");
        Map<String, String> newMapping = productMapper.mapProductsToSymbols();
        Map<String, String> currentMapping = connectionService.getProductToSymbolMap();
        
        if (!newMapping.equals(currentMapping)) {
            logger.info("Product mapping changed. Previous: {} products, New: {} products. Reconnecting WebSocket...", 
                currentMapping.size(), newMapping.size());
            connectionService.disconnect();
            connectionService.initialize(newMapping);
        } else {
            logger.debug("Product mapping unchanged: {} products", currentMapping.size());
        }
    }
}
