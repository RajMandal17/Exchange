package com.custom.marketdata.binance.scheduler;

import com.custom.marketdata.binance.config.BinanceOrderbookConfig;
import com.custom.marketdata.binance.dto.OrderbookDiff;
import com.custom.marketdata.binance.dto.OrderbookEntry;
import com.custom.marketdata.binance.dto.OrderbookState;
import com.custom.marketdata.binance.handler.BinanceWebSocketHandler;
import com.custom.marketdata.binance.service.BotOrderManagementService;
import com.custom.marketdata.binance.service.OrderbookComparisonService;
import com.custom.marketdata.binance.service.WebSocketConnectionService;
import com.custom.marketdata.orderbook.L2OrderBook;
import com.custom.marketdata.repository.OrderRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.custom.matchingengine.snapshot.EngineSnapshotManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class OrderbookSyncScheduler {
    private final BinanceOrderbookConfig config;
    private final WebSocketConnectionService connectionService;
    private final BinanceWebSocketHandler webSocketHandler;
    private final OrderbookComparisonService comparisonService;
    private final BotOrderManagementService botOrderManagementService;
    private final OrderRepository orderRepository;
    private final EngineSnapshotManager engineSnapshotManager;

    @Scheduled(fixedRateString = "${binance.orderbook.sync.interval}")
    public void syncOrderbooks() {
        if (!config.isEnabled()) {
            return;
        }
        
        Map<String, String> productMapping = connectionService.getProductToSymbolMap();
        Map<String, Map<String, OrderbookEntry>> binanceOrderbooks = webSocketHandler.getBinanceOrderbooks();
        
        for (Map.Entry<String, String> entry : productMapping.entrySet()) {
            String productId = entry.getKey();
            String binanceSymbol = entry.getValue();
            
            try {
                // Get the binance orderbook for this symbol
                Map<String, OrderbookEntry> binanceOrderbook = binanceOrderbooks.get(binanceSymbol);
                if (binanceOrderbook == null || binanceOrderbook.isEmpty()) {
                    logger.debug("No Binance orderbook data available for {}", binanceSymbol);
                    continue;
                }
                
                // Get the local orderbook from the database
                String orderbookJson = String.valueOf(engineSnapshotManager.getLocalOrderBookFromRedis(productId));
                OrderbookState localOrderbook = comparisonService.parseLocalOrderbook(orderbookJson);
                
                // Generate diff
                OrderbookDiff diff = comparisonService.generateOrderbookDiff(localOrderbook, binanceOrderbook);
                
                if (diff.hasChanges()) {
                    logger.debug("Syncing orderbook for {}: {} bids to add, {} asks to add, {} bids to remove, {} asks to remove", 
                            productId, diff.getBidsToAdd().size(), diff.getAsksToAdd().size(), 
                            diff.getBidsToRemove().size(), diff.getAsksToRemove().size());
                    
                    // Apply changes
                    botOrderManagementService.applyOrderbookChanges(productId, binanceSymbol, diff);
                }
            } catch (JsonProcessingException e) {
                logger.error("Error parsing orderbook JSON for product {}: {}", productId, e.getMessage());
            } catch (Exception e) {
                logger.error("Error syncing orderbook for product {}: {}", productId, e.getMessage(), e);
            }
        }
    }
}
