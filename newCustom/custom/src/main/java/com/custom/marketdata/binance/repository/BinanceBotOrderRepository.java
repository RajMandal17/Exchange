package com.custom.marketdata.binance.repository;

import com.custom.marketdata.entity.OrderEntity;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository for managing Binance bot orders.
 */
@Repository
public interface BinanceBotOrderRepository {
    /**
     * Finds a bot order by product ID, price, and side
     * 
     * @param productId The product ID
     * @param price The order price
     * @param side The order side (BUY/SELL)
     * @param isBot Whether the order is a bot order
     * @return The order if found, null otherwise
     */
    OrderEntity findBotOrderByPriceAndSide(String productId, String price, String side, boolean isBot);
    
    /**
     * Finds bot orders that should be cancelled because they are not in the active prices list
     * 
     * @param productId The product ID
     * @param activePrices List of prices that should be kept
     * @param side The order side (BUY/SELL)
     * @param isBot Whether the order is a bot order
     * @return List of orders that should be cancelled
     */
    List<OrderEntity> findBotOrdersToCancel(String productId, List<String> activePrices, String side, boolean isBot);
}
