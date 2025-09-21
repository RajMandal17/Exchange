package com.custom.marketdata.binance.service;

import com.custom.enums.OrderSide;
import com.custom.marketdata.binance.config.BinanceOrderbookConfig;
import com.custom.marketdata.binance.dto.OrderbookDiff;
import com.custom.marketdata.binance.dto.OrderbookEntry;
import com.custom.marketdata.binance.repository.BinanceBotOrderRepository;
import com.custom.marketdata.entity.OrderEntity;
import com.custom.marketdata.entity.User;
import com.custom.marketdata.manager.UserManager;
import com.custom.marketdata.repository.OrderRepository;
import com.custom.openapi.controller.OrderController;
import com.custom.openapi.model.OrderDto;
import com.custom.openapi.model.PlaceOrderRequest;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class BotOrderManagementService {
    private static final Logger logger = LoggerFactory.getLogger(BotOrderManagementService.class);
    private final OrderController orderController;
    private final OrderRepository orderRepository;
    private final BinanceBotOrderRepository botOrderRepository;
    private final BinanceOrderbookConfig config;
    private final AsyncOrderExecutorService asyncOrderExecutor;
    private final UserManager userManager;

    // Thread-safe storage for bot orders with read-write locks per product
    private final Map<String, Map<String, String>> botOrderCache = new ConcurrentHashMap<>();
    private final Map<String, ReentrantReadWriteLock> productLocks = new ConcurrentHashMap<>();

    /**
     * Apply orderbook changes from Binance to local bot orders
     * This method is thread-safe and uses async processing for better performance
     */
    public CompletableFuture<Void> applyOrderbookChanges(String productId, String binanceSymbol, OrderbookDiff diff) {
        long startTime = System.nanoTime(); // TIME A: Start of orderbook changes

        // Get or create lock for this product
        ReentrantReadWriteLock productLock = productLocks.computeIfAbsent(productId,
                k -> new ReentrantReadWriteLock());

        // Submit async operations for parallel processing
        List<CompletableFuture<Void>> futures = new ArrayList<>();

        // Process bid orders asynchronously
        if (!diff.getBidsToAdd().isEmpty()) {
            CompletableFuture<Void> bidFuture = asyncOrderExecutor.submitOrderOperation(
                    productId, "PROCESS_BIDS",
                    p -> processBidOrdersThreadSafe(p, diff.getBidsToAdd(), productLock)
            );
            futures.add(bidFuture);
        }

        // Process ask orders asynchronously
        if (!diff.getAsksToAdd().isEmpty()) {
            CompletableFuture<Void> askFuture = asyncOrderExecutor.submitOrderOperation(
                    productId, "PROCESS_ASKS",
                    p -> processAskOrdersThreadSafe(p, diff.getAsksToAdd(), productLock)
            );
            futures.add(askFuture);
        }

        // Cancel orders asynchronously
        if (!diff.getBidsToRemove().isEmpty() || !diff.getAsksToRemove().isEmpty()) {
            CompletableFuture<Void> cancelFuture = asyncOrderExecutor.submitOrderOperation(
                    productId, "CANCEL_ORDERS",
                    p -> cancelOrdersThreadSafe(p, diff.getBidsToRemove(), diff.getAsksToRemove(), productLock)
            );
            futures.add(cancelFuture);
        }

        long enqueueTime = System.nanoTime();
        logger.debug("TIME_A: Orderbook changes enqueued for {} in {} ns",
                productId, (enqueueTime - startTime));

        // Return a future that completes when all operations are done
        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
    }

    /**
     * Thread-safe processing of bid orders
     */
    private void processBidOrdersThreadSafe(String productId, Map<String, BigDecimal> bidsToAdd,
                                            ReentrantReadWriteLock productLock) {
        productLock.writeLock().lock();
        try {
            Map<String, String> orderIdCache = botOrderCache.computeIfAbsent(productId, k -> new HashMap<>());

            for (Map.Entry<String, BigDecimal> entry : bidsToAdd.entrySet()) {
                String price = entry.getKey();
                BigDecimal size = entry.getValue();
                String cacheKey = "BID_" + price;

                try {
                    processSingleBidOrder(productId, price, size, cacheKey, orderIdCache);
                } catch (Exception e) {
                    logger.error("Failed to process bot BID order for {} at price {}", productId, price, e);
                }
            }
        } finally {
            productLock.writeLock().unlock();
        }
    }

    /**
     * Thread-safe processing of ask orders
     */
    private void processAskOrdersThreadSafe(String productId, Map<String, BigDecimal> asksToAdd,
                                            ReentrantReadWriteLock productLock) {
        productLock.writeLock().lock();
        try {
            Map<String, String> orderIdCache = botOrderCache.computeIfAbsent(productId, k -> new HashMap<>());

            for (Map.Entry<String, BigDecimal> entry : asksToAdd.entrySet()) {
                String price = entry.getKey();
                BigDecimal size = entry.getValue();
                String cacheKey = "ASK_" + price;

                try {
                    processSingleAskOrder(productId, price, size, cacheKey, orderIdCache);
                } catch (Exception e) {
                    logger.error("Failed to process bot ASK order for {} at price {}", productId, price, e);
                }
            }
        } finally {
            productLock.writeLock().unlock();
        }
    }

    /**
     * Thread-safe order cancellation
     */
    private void cancelOrdersThreadSafe(String productId, Set<String> bidsToRemove, Set<String> asksToRemove,
                                        ReentrantReadWriteLock productLock) {
        productLock.writeLock().lock();
        try {
            Map<String, String> orderIdCache = botOrderCache.get(productId);
            if (orderIdCache == null) {
                return;
            }

            // Cancel bid orders
            for (String price : bidsToRemove) {
                String cacheKey = "BID_" + price;
                cancelSingleOrder(productId, price, cacheKey, orderIdCache, "BID");
            }

            // Cancel ask orders
            for (String price : asksToRemove) {
                String cacheKey = "ASK_" + price;
                cancelSingleOrder(productId, price, cacheKey, orderIdCache, "ASK");
            }
        } finally {
            productLock.writeLock().unlock();
        }
    }

    /**
     * Process a single bid order: create or update as needed.
     */
    private void processSingleBidOrder(String productId, String price, BigDecimal size, String cacheKey,
                                       Map<String, String> orderIdCache) {
        // check cache first
        String cachedOrderId = orderIdCache.get(cacheKey);
        if (cachedOrderId != null) {
            // load from repository to verify size
            OrderEntity existingOrder = orderRepository.findByOrderId(cachedOrderId);
            if (existingOrder != null) {
                if (existingOrder.getSize().equals(size)) {
                    return; // nothing to do
                }
                // size differs - cancel and recreate
                cancelBotOrder(existingOrder.getId());
                orderIdCache.remove(cacheKey);
            }
        }

        // check DB for any order at this price/side
        OrderEntity dbOrder = botOrderRepository.findBotOrderByPriceAndSide(productId, price, OrderSide.BUY.name(), true);
        if (dbOrder != null) {
            if (dbOrder.getSize().equals(size)) {
                orderIdCache.put(cacheKey, dbOrder.getId());
                return;
            }
            cancelBotOrder(dbOrder.getId());
        }

        // place new bot order
        OrderDto newOrderId = placeBotOrder(productId, "BUY", price, size.toString());
        orderIdCache.put(cacheKey, newOrderId.getId());
    }

    /**
     * Process a single ask order: create or update as needed.
     */
    private void processSingleAskOrder(String productId, String price, BigDecimal size, String cacheKey,
                                       Map<String, String> orderIdCache) {
        // check cache first
        String cachedOrderId = orderIdCache.get(cacheKey);
        if (cachedOrderId != null) {
            OrderEntity existingOrder = orderRepository.findByOrderId(cachedOrderId);
            if (existingOrder != null) {
                if (existingOrder.getSize().equals(size)) {
                    return; // nothing to do
                }
                cancelBotOrder(existingOrder.getId());
                orderIdCache.remove(cacheKey);
            }
        }

        OrderEntity dbOrder = botOrderRepository.findBotOrderByPriceAndSide(productId, price, OrderSide.SELL.name(), true);
        if (dbOrder != null) {
            if (dbOrder.getSize().equals(size)) {
                orderIdCache.put(cacheKey, dbOrder.getId());
                return;
            }
            cancelBotOrder(dbOrder.getId());
        }

        OrderDto newOrderId = placeBotOrder(productId, "SELL", price, size.toString());
        orderIdCache.put(cacheKey, newOrderId.getId());
    }

    /**
     * Cancel a single order identified by cacheKey or DB lookup.
     */
    private void cancelSingleOrder(String productId, String price, String cacheKey,
                                   Map<String, String> orderIdCache, String side) {
        String orderId = (orderIdCache != null) ? orderIdCache.get(cacheKey) : null;
        if (orderId != null) {
            cancelBotOrder(orderId);
            if (orderIdCache != null) orderIdCache.remove(cacheKey);
            return;
        }

        // fallback to DB lookup
        OrderEntity order = botOrderRepository.findBotOrderByPriceAndSide(productId, price, side.equals("BID") ? OrderSide.BUY.name() : OrderSide.SELL.name(), true);
        if (order != null) {
            cancelBotOrder(order.getId());
            if (orderIdCache != null) orderIdCache.remove(cacheKey);
        }
    }

    /**
     * Process cancellations for bot orders that are no longer needed
     */
    public void processBotOrderCancellations(String productId, Set<String> pricesToRemove,
                                             String side, Map<String, String> orderIdCache) {
        if (pricesToRemove == null || pricesToRemove.isEmpty()) {
            return;
        }

        try {
            // Find bot orders to cancel (at specified prices)
            for (String price : pricesToRemove) {
                String cacheKey = (side.equals(OrderSide.BUY.name()) ? "BID_" : "ASK_") + price;

                // Try to get from cache first
                String orderId = orderIdCache.get(cacheKey);
                if (orderId != null) {
                    // Order is in cache, cancel it
                    cancelBotOrder(orderId);
                    orderIdCache.remove(cacheKey);
                    logger.debug("Cancelled {} bot order {} for {} at price {} from cache",
                            side, orderId, productId, price);
                } else {
                    // Order might be in database but not cache
                    OrderEntity order = botOrderRepository.findBotOrderByPriceAndSide(
                            productId, price, side, true);

                    if (order != null) {
                        cancelBotOrder(order.getId());
                        logger.debug("Cancelled {} bot order {} for {} at price {} from database",
                                side, order.getId(), productId, price);
                    }
                }
            }

            // Also find any other bot orders for this product/side that might not be in the price list
            // This helps clean up stale orders that might have been missed in previous syncs
            List<String> pricesToKeep = new ArrayList<>(pricesToRemove);

            List<OrderEntity> staleBotOrders = botOrderRepository.findBotOrdersToCancel(
                    productId, pricesToKeep, side, true);

            for (OrderEntity order : staleBotOrders) {
                cancelBotOrder(order.getId());
                String price = order.getPrice().toString();
                String cacheKey = (side.equals(OrderSide.BUY.name()) ? "BID_" : "ASK_") + price;
                orderIdCache.remove(cacheKey);
                logger.debug("Cancelled stale {} bot order {} for {} at price {}",
                        side, order.getId(), productId, price);
            }
        } catch (Exception e) {
            logger.error("Error cancelling bot orders for product {}: {}", productId, e.getMessage(), e);
        }
    }

    /**
     * Clean up stale bot orders for all products
     */
    public void cleanupStaleOrders(Map<String, String> productMapping, Map<String, Map<String, OrderbookEntry>> orderbooks) {
        try {
            logger.debug("Running stale bot order cleanup...");

            for (Map.Entry<String, String> entry : productMapping.entrySet()) {
                String productId = entry.getKey();
                String binanceSymbol = entry.getValue();

                Map<String, OrderbookEntry> binanceOrderbook = orderbooks.get(binanceSymbol);
                if (binanceOrderbook == null || binanceOrderbook.isEmpty()) {
                    continue; // Skip if we don't have data for this product
                }

                // Extract active bid and ask prices
                List<String> activeBidPrices = new ArrayList<>();
                List<String> activeAskPrices = new ArrayList<>();

                for (OrderbookEntry entry2 : binanceOrderbook.values()) {
                    if (entry2.isBid()) {
                        activeBidPrices.add(entry2.getPrice());
                    } else {
                        activeAskPrices.add(entry2.getPrice());
                    }
                }

                // Find and cancel any stale bid orders
                List<OrderEntity> staleBidOrders = botOrderRepository.findBotOrdersToCancel(
                        productId, activeBidPrices, OrderSide.BUY.name(), true);

                for (OrderEntity order : staleBidOrders) {
                    cancelBotOrder(order.getId());
                    logger.debug("Cleanup: Cancelled stale BID bot order {} for {} at price {}",
                            order.getId(), productId, order.getPrice());
                }

                // Find and cancel any stale ask orders
                List<OrderEntity> staleAskOrders = botOrderRepository.findBotOrdersToCancel(
                        productId, activeAskPrices, OrderSide.SELL.name(), true);

                for (OrderEntity order : staleAskOrders) {
                    cancelBotOrder(order.getId());
                    logger.debug("Cleanup: Cancelled stale ASK bot order {} for {} at price {}",
                            order.getId(), productId, order.getPrice());
                }
            }
        } catch (Exception e) {
            logger.error("Error during stale bot order cleanup: {}", e.getMessage(), e);
        }
    }

    /**
     * Cancel a bot order
     */
    private void cancelBotOrder(String orderId) {

        User user = userManager.getUser("test@test.com", "12345678");
        try {
            orderController.cancelOrder(
                    orderId   ,user
            );
        } catch (Exception e) {
            logger.error("Failed to cancel bot order {}: {}", orderId, e.getMessage());
            throw e;
        }
    }

    /**
     * Place a bot order
     */
    private OrderDto placeBotOrder(String productId, String side, String price, String size) {
        PlaceOrderRequest request = new PlaceOrderRequest();
        request.setProductId(productId);
        request.setSide(side);
        request.setPrice(String.valueOf(new BigDecimal(price)));  // Convert String to BigDecimal
        request.setSize(size);
        request.setType("LIMIT");
        request.setTimeInForce("GTC"); // Good Till Cancelled
        request.setIsBot(true);        // Mark as a bot order
        request.setFrom("BOT");
        User user = userManager.getUser("test@test.com", "12345678");
        try {
            // Use the existing placeOrder method
            return orderController.placeOrderBot(request, user);
        } catch (Exception e) {
            logger.error("Error placing bot order", e);
            throw e;
        }
    }

    /**
     * Clean up stale orders for a specific product
     * @param productId The product ID to clean up
     */
    public void cleanupStaleOrders(String productId) {
        logger.info("Cleaning up stale orders for product: {}", productId);
        try {
            // Cancel all bid orders
            List<OrderEntity> staleBidOrders = botOrderRepository.findBotOrdersToCancel(
                    productId, Collections.emptyList(), OrderSide.BUY.name(), true);

            for (OrderEntity order : staleBidOrders) {
                cancelBotOrder(order.getId());
                logger.debug("Cancelled stale BID bot order {} for {} at price {}",
                        order.getId(), productId, order.getPrice());
            }

            // Cancel all ask orders
            List<OrderEntity> staleAskOrders = botOrderRepository.findBotOrdersToCancel(
                    productId, Collections.emptyList(), OrderSide.SELL.name(), true);

            for (OrderEntity order : staleAskOrders) {
                cancelBotOrder(order.getId());
                logger.debug("Cancelled stale ASK bot order {} for {} at price {}",
                        order.getId(), productId, order.getPrice());
            }

            logger.info("Cleaned up {} bid orders and {} ask orders for {}",
                    staleBidOrders.size(), staleAskOrders.size(), productId);
        } catch (Exception e) {
            logger.error("Error during stale bot order cleanup for {}: {}", productId, e.getMessage(), e);
        }
    }
}
