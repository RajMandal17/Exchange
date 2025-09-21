package com.custom.feed;

import com.alibaba.fastjson.JSON;
import com.custom.feed.message.*;
import com.custom.marketdata.entity.Candle;
import com.custom.marketdata.orderbook.L2OrderBook;
import com.custom.matchingengine.Account;
import com.custom.matchingengine.Order;
import com.custom.matchingengine.Trade;
import com.custom.matchingengine.message.*;
import com.custom.stripexecutor.StripedExecutorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;

@Component
@Slf4j
@RequiredArgsConstructor
public class FeedMessageListener {
    private final RedissonClient redissonClient;
    private final SessionManager sessionManager;
    private final StripedExecutorService callbackExecutor =
            new StripedExecutorService(Runtime.getRuntime().availableProcessors());

    @PostConstruct
    public void run() {
        // Private user orders - Frontend expects: 'order' channel
        redissonClient.getTopic("order", StringCodec.INSTANCE).addListener(String.class, (c, msg) -> {
            OrderMessage orderMessage = JSON.parseObject(msg, OrderMessage.class);
            callbackExecutor.execute(orderMessage.getOrder().getUserId(), () -> {
                // Original channel for legacy support
                String legacyChannel = orderMessage.getOrder().getUserId() + "." + orderMessage.getOrder().getProductId() + ".order";
                sessionManager.broadcast(legacyChannel, orderFeedMessage(orderMessage));
                
                // Frontend expected channel
                String frontendChannel = "order";
                sessionManager.broadcast(frontendChannel, frontendOrderMessage(orderMessage));
            });
        });

        // Private user balances - Frontend expects: 'balances' channel
        redissonClient.getTopic("account", StringCodec.INSTANCE).addListener(String.class, (c, msg) -> {
            AccountMessage accountMessage = JSON.parseObject(msg, AccountMessage.class);
            callbackExecutor.execute(accountMessage.getAccount().getUserId(), () -> {
                // Original channel for legacy support
                String legacyChannel = accountMessage.getAccount().getUserId() + "." + accountMessage.getAccount().getCurrency() + ".funds";
                sessionManager.broadcast(legacyChannel, accountFeedMessage(accountMessage));
                
                // Frontend expected channel
                String frontendChannel = "balances";
                sessionManager.broadcast(frontendChannel, frontendBalancesMessage(accountMessage));
            });
        });

        // Public trades - Frontend expects: '{marketId}.trades' channel
        redissonClient.getTopic("trade", StringCodec.INSTANCE).addListener(String.class, (c, msg) -> {
            TradeMessage tradeMessage = JSON.parseObject(msg, TradeMessage.class);
            String productId = tradeMessage.getTrade().getProductId();
            
            // Original channel for legacy support
            String legacyChannel = productId + ".match";
            sessionManager.broadcast(legacyChannel, matchMessage(tradeMessage));
            
            // Frontend expected channel
            String frontendChannel = productId + ".trades";
            sessionManager.broadcast(frontendChannel, frontendTradesMessage(tradeMessage));
            
            // Private trade channel - Frontend expects: 'trade' channel for private trades
            String privateChannel = "trade";
            sessionManager.broadcast(privateChannel, frontendPrivateTradeMessage(tradeMessage));
        });

        // Global tickers - Frontend expects: 'global.tickers' channel
        redissonClient.getTopic("ticker", StringCodec.INSTANCE).addListener(String.class, (c, msg) -> {
            TickerMessage tickerMessage = JSON.parseObject(msg, TickerMessage.class);
            callbackExecutor.execute(tickerMessage.getProductId(), () -> {
                // Original channel for legacy support
                String legacyChannel = tickerMessage.getProductId() + ".ticker";
                sessionManager.broadcast(legacyChannel, tickerFeedMessage(tickerMessage));
                
                // Frontend expected global tickers channel
                String frontendChannel = "global.tickers";
                sessionManager.broadcast(frontendChannel, frontendGlobalTickersMessage(tickerMessage));
            });
        });

        // Kline/Candlestick data - Frontend expects: '{marketId}.kline-{period}' channel
        redissonClient.getTopic("candle", StringCodec.INSTANCE).addListener(String.class, (c, msg) -> {
            callbackExecutor.execute(() -> {
                Candle candle = JSON.parseObject(msg, Candle.class);
                // Original channel for legacy support
                String legacyChannel = candle.getProductId() + ".candle_" + candle.getGranularity() * 60;
                sessionManager.broadcast(legacyChannel, candleMessage(candle));
                
                // Frontend expected kline channel
                String period = mapGranularityToPeriod(candle.getGranularity());
                String frontendChannel = candle.getProductId() + ".kline-" + period;
                sessionManager.broadcast(frontendChannel, frontendKlineMessage(candle));
            });
        });

        // Order book updates - Frontend expects: '{marketId}.update' or '{marketId}.ob-inc' channel
        redissonClient.getTopic("l2_batch", StringCodec.INSTANCE).addListener(String.class, (c, msg) -> {
            L2OrderBook l2OrderBook = JSON.parseObject(msg, L2OrderBook.class);
            callbackExecutor.execute(l2OrderBook.getProductId(), () -> {
                // Original channel for legacy support
                String legacyChannel = l2OrderBook.getProductId() + ".level2";
                sessionManager.broadcast(legacyChannel, l2OrderBook);
                
                // Frontend expected order book update channel
                String frontendChannel = l2OrderBook.getProductId() + ".update";
                sessionManager.broadcast(frontendChannel, frontendOrderBookMessage(l2OrderBook));
            });
        });

        // Incremental order book updates - Frontend expects: '{marketId}.ob-inc' channel
        redissonClient.getTopic("l2_increment", StringCodec.INSTANCE).addListener(String.class, (c, msg) -> {
            // Parse incremental order book message
            @SuppressWarnings("unchecked")
            java.util.Map<String, Object> incrementMessage = JSON.parseObject(msg, java.util.Map.class);
            String productId = (String) incrementMessage.get("productId");
            if (productId != null) {
                callbackExecutor.execute(productId, () -> {
                    String frontendChannel = productId + ".ob-inc";
                    sessionManager.broadcast(frontendChannel, frontendIncrementalOrderBookMessage(incrementMessage));
                });
            }
        });

        // Deposit address updates - Frontend expects: 'deposit_address' channel
        redissonClient.getTopic("deposit_address", StringCodec.INSTANCE).addListener(String.class, (c, msg) -> {
            // Parse deposit address message (you may need to create this class)
            @SuppressWarnings("unchecked")
            java.util.Map<String, Object> depositMessage = JSON.parseObject(msg, java.util.Map.class);
            String userId = (String) depositMessage.get("userId");
            if (userId != null) {
                callbackExecutor.execute(userId, () -> {
                    String frontendChannel = "deposit_address";
                    sessionManager.broadcast(frontendChannel, depositMessage);
                });
            }
        });

        redissonClient.getTopic("orderBookLog", StringCodec.INSTANCE).addListener(String.class, (c, msg) -> {
            OrderBookMessage message = JSON.parseObject(msg, OrderBookMessage.class);
            callbackExecutor.execute(message.getProductId(), () -> {
                switch (message.getType()) {
                    case ORDER_RECEIVED:
                        OrderReceivedMessage orderReceivedMessage = JSON.parseObject(msg, OrderReceivedMessage.class);
                        sessionManager.broadcast(orderReceivedMessage.getProductId() + ".full",
                                (orderReceivedMessage(orderReceivedMessage)));
                        break;
                    case ORDER_OPEN:
                        OrderOpenMessage orderOpenMessage = JSON.parseObject(msg, OrderOpenMessage.class);
                        sessionManager.broadcast(orderOpenMessage.getProductId() + ".full",
                                (orderOpenMessage(orderOpenMessage)));
                        break;
                    case ORDER_DONE:
                        OrderDoneMessage orderDoneMessage = JSON.parseObject(msg, OrderDoneMessage.class);
                        sessionManager.broadcast(orderDoneMessage.getProductId() + ".full",
                                (orderDoneMessage(orderDoneMessage)));
                        break;
                    default:
                }
            });
        });
    }

    private OrderReceivedFeedMessage orderReceivedMessage(OrderReceivedMessage log) {
        OrderReceivedFeedMessage message = new OrderReceivedFeedMessage();
        message.setProductId(log.getProductId());
        message.setTime(log.getTime().toInstant().toString());
        message.setSequence(log.getSequence());
        message.setOrderId(log.getOrderId());
        message.setSize(log.getSize().stripTrailingZeros().toPlainString());
        message.setPrice(log.getPrice() != null ? log.getPrice().stripTrailingZeros().toPlainString() : null);
        message.setFunds(log.getFunds() != null ? log.getFunds().stripTrailingZeros().toPlainString() : null);
        message.setSide(log.getSide().name().toUpperCase());
        message.setOrderType(log.getType().name().toUpperCase());
        return message;
    }

    private OrderMatchFeedMessage matchMessage(TradeMessage tradeMessage) {
        Trade trade = tradeMessage.getTrade();
        OrderMatchFeedMessage message = new OrderMatchFeedMessage();
        message.setTradeId(trade.getSequence());
        message.setSequence(trade.getSequence());
        message.setTakerOrderId(trade.getTakerOrderId());
        message.setMakerOrderId(trade.getMakerOrderId());
        message.setTime(trade.getTime().toInstant().toString());
        message.setProductId(trade.getProductId());
        message.setSize(trade.getSize().stripTrailingZeros().toPlainString());
        message.setPrice(trade.getPrice().stripTrailingZeros().toPlainString());
        message.setSide(trade.getSide().name().toLowerCase());
        return message;
    }

    private OrderOpenFeedMessage orderOpenMessage(OrderOpenMessage log) {
        OrderOpenFeedMessage message = new OrderOpenFeedMessage();
        message.setSequence(log.getSequence());
        message.setTime(log.getTime().toInstant().toString());
        message.setProductId(log.getProductId());
        message.setPrice(log.getPrice().stripTrailingZeros().toPlainString());
        message.setSide(log.getSide().name().toLowerCase());
        message.setRemainingSize(log.getRemainingSize().toPlainString());
        return message;
    }

    private OrderDoneFeedMessage orderDoneMessage(OrderDoneMessage log) {
        OrderDoneFeedMessage message = new OrderDoneFeedMessage();
        message.setSequence(log.getSequence());
        message.setTime(log.getTime().toInstant().toString());
        message.setProductId(log.getProductId());
        if (log.getPrice() != null) {
            message.setPrice(log.getPrice().stripTrailingZeros().toPlainString());
        }
        message.setSide(log.getSide().name().toLowerCase());
        //message.setReason(log.getDoneReason().name().toUpperCase());
        if (log.getRemainingSize() != null) {
            message.setRemainingSize(log.getRemainingSize().stripTrailingZeros().toPlainString());
        }
        return message;
    }

    private CandleFeedMessage candleMessage(Candle candle) {
        CandleFeedMessage message = new CandleFeedMessage();
        message.setProductId(candle.getProductId());
        message.setGranularity(candle.getGranularity());
        message.setTime(candle.getTime());
        message.setOpen(candle.getOpen().stripTrailingZeros().toPlainString());
        message.setClose(candle.getClose().stripTrailingZeros().toPlainString());
        message.setHigh(candle.getHigh().stripTrailingZeros().toPlainString());
        message.setLow(candle.getLow().stripTrailingZeros().toPlainString());
        message.setVolume(candle.getVolume().stripTrailingZeros().toPlainString());
        return message;
    }

    private OrderFeedMessage orderFeedMessage(OrderMessage orderMessage) {
        Order order = orderMessage.getOrder();
        OrderFeedMessage message = new OrderFeedMessage();
        message.setUserId(order.getUserId());
        message.setProductId(order.getProductId());
        message.setId(order.getId());
        message.setPrice(order.getPrice().stripTrailingZeros().toPlainString());
        message.setSize(order.getSize().stripTrailingZeros().toPlainString());
        message.setFunds(order.getFunds().stripTrailingZeros().toPlainString());
        message.setSide(order.getSide().name().toLowerCase());
        message.setOrderType(order.getType().name().toLowerCase());
        message.setCreatedAt(order.getTime().toInstant().toString());
        //message.setFillFees(
        //       order.getFillFees() != null ? order.getFillFees().stripTrailingZeros().toPlainString() : "0");
        message.setFilledSize(order.getSize().subtract(order.getRemainingSize()).stripTrailingZeros().toPlainString());
        message.setExecutedValue(order.getFunds().subtract(order.getRemainingFunds()).stripTrailingZeros().toPlainString());
        message.setStatus(order.getStatus().name().toLowerCase());
        return message;
    }

    private AccountFeedMessage accountFeedMessage(AccountMessage accountMessage) {
        Account account = accountMessage.getAccount();
        AccountFeedMessage accountFeedMessage = new AccountFeedMessage();
        accountFeedMessage.setUserId(account.getUserId());
        accountFeedMessage.setCurrencyCode(account.getCurrency());
        accountFeedMessage.setAvailable(account.getAvailable().stripTrailingZeros().toPlainString());
        accountFeedMessage.setHold(account.getHold().stripTrailingZeros().toPlainString());
        return accountFeedMessage;
    }

    private TickerFeedMessage tickerFeedMessage(TickerMessage ticker) {
        TickerFeedMessage tickerFeedMessage = new TickerFeedMessage();
        tickerFeedMessage.setProductId(ticker.getProductId());
        tickerFeedMessage.setTradeId(ticker.getTradeId());
        tickerFeedMessage.setSequence(ticker.getSequence());
        tickerFeedMessage.setTime(ticker.getTime().toInstant().toString());
        tickerFeedMessage.setPrice(ticker.getPrice().stripTrailingZeros().toPlainString());
        tickerFeedMessage.setSide(ticker.getSide().name().toLowerCase());
        tickerFeedMessage.setLastSize(ticker.getLastSize().stripTrailingZeros().toPlainString());
        tickerFeedMessage.setClose24h(ticker.getClose24h().stripTrailingZeros().toPlainString());
        tickerFeedMessage.setOpen24h(ticker.getOpen24h().stripTrailingZeros().toPlainString());
        tickerFeedMessage.setHigh24h(ticker.getHigh24h().stripTrailingZeros().toPlainString());
        tickerFeedMessage.setLow24h(ticker.getLow24h().stripTrailingZeros().toPlainString());
        tickerFeedMessage.setVolume24h(ticker.getVolume24h().stripTrailingZeros().toPlainString());
        tickerFeedMessage.setVolume30d(ticker.getVolume30d().stripTrailingZeros().toPlainString());
        return tickerFeedMessage;
    }

    // Frontend-compatible message methods
    private Object frontendOrderMessage(OrderMessage orderMessage) {
        Order order = orderMessage.getOrder();
        java.util.Map<String, Object> message = new java.util.HashMap<>();
        message.put("id", order.getId());
        message.put("price", order.getPrice().stripTrailingZeros().toPlainString());
        message.put("state", order.getStatus().name().toLowerCase());
        message.put("remaining_volume", order.getRemainingSize().stripTrailingZeros().toPlainString());
        message.put("origin_volume", order.getSize().stripTrailingZeros().toPlainString());
        message.put("executed_volume", order.getSize().subtract(order.getRemainingSize()).stripTrailingZeros().toPlainString());
        message.put("side", order.getSide().name().toLowerCase());
        message.put("market", order.getProductId());
        message.put("ord_type", order.getType().name().toLowerCase());
        message.put("avg_price", order.getPrice().stripTrailingZeros().toPlainString());
        message.put("created_at", order.getTime().toInstant().toString());
        message.put("uuid", order.getId().toString());
        message.put("at", order.getTime().toInstant().toEpochMilli());
        return message;
    }

    private Object frontendBalancesMessage(AccountMessage accountMessage) {
        Account account = accountMessage.getAccount();
        java.util.Map<String, Object> message = new java.util.HashMap<>();
        java.util.Map<String, Object> balance = new java.util.HashMap<>();
        balance.put("available", account.getAvailable().stripTrailingZeros().toPlainString());
        balance.put("hold", account.getHold().stripTrailingZeros().toPlainString());
        message.put(account.getCurrency(), balance);
        return message;
    }

    private Object frontendTradesMessage(TradeMessage tradeMessage) {
        Trade trade = tradeMessage.getTrade();
        java.util.Map<String, Object> message = new java.util.HashMap<>();
        java.util.List<java.util.Map<String, Object>> trades = new java.util.ArrayList<>();
        java.util.Map<String, Object> tradeData = new java.util.HashMap<>();
        tradeData.put("tid", trade.getSequence());
        tradeData.put("taker_type", trade.getSide().name().toLowerCase());
        tradeData.put("date", trade.getTime().toInstant().getEpochSecond());
        tradeData.put("price", trade.getPrice().stripTrailingZeros().toPlainString());
        tradeData.put("amount", trade.getSize().stripTrailingZeros().toPlainString());
        tradeData.put("total", trade.getPrice().multiply(trade.getSize()).stripTrailingZeros().toPlainString());
        trades.add(tradeData);
        message.put("trades", trades);
        return message;
    }

    private Object frontendPrivateTradeMessage(TradeMessage tradeMessage) {
        Trade trade = tradeMessage.getTrade();
        java.util.Map<String, Object> message = new java.util.HashMap<>();
        message.put("id", trade.getSequence());
        message.put("price", trade.getPrice().stripTrailingZeros().toPlainString());
        message.put("total", trade.getPrice().multiply(trade.getSize()).stripTrailingZeros().toPlainString());
        message.put("amount", trade.getSize().stripTrailingZeros().toPlainString());
        message.put("market", trade.getProductId());
        message.put("created_at", trade.getTime().toInstant().toString());
        message.put("taker_type", trade.getSide().name().toLowerCase());
        message.put("side", trade.getSide().name().toLowerCase());
        return message;
    }

    private Object frontendGlobalTickersMessage(TickerMessage tickerMessage) {
        java.util.Map<String, Object> message = new java.util.HashMap<>();
        java.util.Map<String, Object> tickerData = new java.util.HashMap<>();
        tickerData.put("amount", tickerMessage.getVolume24h().stripTrailingZeros().toPlainString());
        tickerData.put("avg_price", tickerMessage.getPrice().stripTrailingZeros().toPlainString());
        tickerData.put("high", tickerMessage.getHigh24h().stripTrailingZeros().toPlainString());
        tickerData.put("last", tickerMessage.getPrice().stripTrailingZeros().toPlainString());
        tickerData.put("low", tickerMessage.getLow24h().stripTrailingZeros().toPlainString());
        tickerData.put("open", tickerMessage.getOpen24h().stripTrailingZeros().toPlainString());
        tickerData.put("price_change_percent", calculatePriceChangePercent(tickerMessage));
        tickerData.put("volume", tickerMessage.getVolume24h().stripTrailingZeros().toPlainString());
        tickerData.put("at", System.currentTimeMillis());
        message.put(tickerMessage.getProductId(), tickerData);
        return message;
    }

    private Object frontendKlineMessage(Candle candle) {
        java.util.List<Object> klineData = new java.util.ArrayList<>();
        klineData.add(candle.getTime());
        klineData.add(candle.getOpen().stripTrailingZeros().toPlainString());
        klineData.add(candle.getHigh().stripTrailingZeros().toPlainString());
        klineData.add(candle.getLow().stripTrailingZeros().toPlainString());
        klineData.add(candle.getClose().stripTrailingZeros().toPlainString());
        klineData.add(candle.getVolume().stripTrailingZeros().toPlainString());
        return klineData;
    }

    private Object frontendOrderBookMessage(L2OrderBook l2OrderBook) {
        java.util.Map<String, Object> message = new java.util.HashMap<>();
        message.put("asks", l2OrderBook.getAsks());
        message.put("bids", l2OrderBook.getBids());
        return message;
    }

    private Object frontendIncrementalOrderBookMessage(java.util.Map<String, Object> incrementMessage) {
        java.util.Map<String, Object> message = new java.util.HashMap<>();
        message.put("asks", incrementMessage.get("asks"));
        message.put("bids", incrementMessage.get("bids"));
        message.put("sequence", incrementMessage.get("sequence"));
        return message;
    }

    private String mapGranularityToPeriod(int granularity) {
        switch (granularity) {
            case 1: return "1m";
            case 5: return "5m";
            case 15: return "15m";
            case 30: return "30m";
            case 60: return "1h";
            case 120: return "2h";
            case 240: return "4h";
            case 360: return "6h";
            case 720: return "12h";
            case 1440: return "1d";
            case 4320: return "3d";
            case 10080: return "1w";
            default: return "1m";
        }
    }

    private String calculatePriceChangePercent(TickerMessage ticker) {
        try {
            java.math.BigDecimal current = ticker.getPrice();
            java.math.BigDecimal open = ticker.getOpen24h();
            if (open.compareTo(java.math.BigDecimal.ZERO) == 0) {
                return "0.00%";
            }
            java.math.BigDecimal change = current.subtract(open).divide(open, 4, java.math.RoundingMode.HALF_UP).multiply(java.math.BigDecimal.valueOf(100));
            return (change.compareTo(java.math.BigDecimal.ZERO) >= 0 ? "+" : "") + change.stripTrailingZeros().toPlainString() + "%";
        } catch (Exception e) {
            return "0.00%";
        }
    }

}
