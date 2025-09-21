package com.custom.feed;

import com.alibaba.fastjson.JSON;
import com.custom.feed.message.L2SnapshotFeedMessage;
import com.custom.feed.message.L2UpdateFeedMessage;
import com.custom.feed.message.PeatioMessageWrapper;
import com.custom.feed.message.PongFeedMessage;
import com.custom.feed.message.TickerFeedMessage;
import com.custom.marketdata.entity.Ticker;
import com.custom.marketdata.manager.TickerManager;
import com.custom.marketdata.orderbook.L2OrderBook;
import com.custom.marketdata.orderbook.L2OrderBookChange;
import com.custom.marketdata.orderbook.OrderBookSnapshotManager;
import com.custom.stripexecutor.StripedExecutorService;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListSet;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListSet;

@Component
@Slf4j
@RequiredArgsConstructor
public class SessionManager {
    private final ConcurrentHashMap<String, ConcurrentSkipListSet<String>> sessionIdsByChannel
            = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ConcurrentSkipListSet<String>> channelsBySessionId
            = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, WebSocketSession> sessionById = new ConcurrentHashMap<>();
    private final OrderBookSnapshotManager orderBookSnapshotManager;
    private final TickerManager tickerManager;
    private final StripedExecutorService messageSenderExecutor =
            new StripedExecutorService(Runtime.getRuntime().availableProcessors());

    @SneakyThrows
    public void subOrUnSub(WebSocketSession session, List<String> productIds, List<String> currencies,
                           List<String> channels, boolean isSub) {
        logger.info("📢 {} request - channels: {}, products: {}, currencies: {}", 
                 isSub ? "Subscribe" : "Unsubscribe", 
                 channels, productIds, currencies);
                 
        for (String channel : channels) {
            switch (channel) {
                case "level2":
                    for (String productId : productIds) {
                        // Map to frontend expected channel names
                        String frontendChannel = productId + ".update";
                        String productChannel = productId + "." + channel;

                        logger.info("{} level2 {} -> {}", (isSub ? "SUB" : "UNSUB"), productId, frontendChannel);
                        if (isSub) {
                            subscribeChannel(session, productChannel);
                            // Also subscribe to the frontend channel for broadcasting
                            subscribeChannel(session, frontendChannel);
                            sendL2OrderBookSnapshot(session, productId);
                        } else {
                            String key = "LAST_L2_ORDER_BOOK:" + productId;
                            session.getAttributes().remove(key);
                            unsubscribeChannel(session, productChannel);
                            unsubscribeChannel(session, frontendChannel);
                        }
                    }
                    break;
                case "ticker":
                    for (String productId : productIds) {
                        String productChannel = productId + "." + channel;

                        logger.info("{} ticker {} -> global.tickers", (isSub ? "SUB" : "UNSUB"), productId);
                        if (isSub) {
                            subscribeChannel(session, productChannel);
                            // Also subscribe to global.tickers channel
                            subscribeChannel(session, "global.tickers");
                            sendTicker(session, productId);
                        } else {
                            unsubscribeChannel(session, productChannel);
                            unsubscribeChannel(session, "global.tickers");
                        }
                    }
                    break;
                case "match":
                    for (String productId : productIds) {
                        // Map to frontend expected channel names
                        String frontendChannel = productId + ".trades";
                        String productChannel = productId + "." + channel;
                        
                        logger.info("{} match {} -> {}", (isSub ? "SUB" : "UNSUB"), productId, frontendChannel);
                        if (isSub) {
                            subscribeChannel(session, productChannel);
                            // Also subscribe to the frontend channel for broadcasting
                            subscribeChannel(session, frontendChannel);
                        } else {
                            unsubscribeChannel(session, productChannel);
                            unsubscribeChannel(session, frontendChannel);
                        }
                    }
                    break;
                case "order": {
                    String userId = getUserId(session);
                    if (userId == null) {
                        return;
                    }

                    for (String productId : productIds) {
                        String orderChanel = userId + "." + productId + "." + channel;
                        if (isSub) {
                            subscribeChannel(session, orderChanel);
                            // Also subscribe to the general order channel
                            subscribeChannel(session, "order");
                        } else {
                            unsubscribeChannel(session, orderChanel);
                            unsubscribeChannel(session, "order");
                        }
                    }
                    break;
                }
                case "funds": {
                    String userId = getUserId(session);
                    if (userId == null) {
                        return;
                    }

                    if (currencies != null) {
                        for (String currency : currencies) {
                            String accountChannel = userId + "." + currency + "." + channel;
                            if (isSub) {
                                subscribeChannel(session, accountChannel);
                                // Also subscribe to the balances channel
                                subscribeChannel(session, "balances");
                            } else {
                                unsubscribeChannel(session, accountChannel);
                                unsubscribeChannel(session, "balances");
                            }
                        }
                    }
                    break;
                }
                case "full":
                    for (String productId : productIds) {
                        String productChannel = productId + "." + channel;
                        String frontendChannel = productId + ".full";
                        
                        logger.info("{} full {} -> {}", (isSub ? "SUB" : "UNSUB"), productId, frontendChannel);
                        if (isSub) {
                            subscribeChannel(session, productChannel);
                            // Also subscribe to the frontend channel for broadcasting  
                            subscribeChannel(session, frontendChannel);
                        } else {
                            unsubscribeChannel(session, productChannel);
                            unsubscribeChannel(session, frontendChannel);
                        }
                    }
                    break;

                default:
            }
        }
    }

    public void broadcast(String channel, Object message) {
        Set<String> sessionIds = sessionIdsByChannel.get(channel);
        logger.info("Broadcasting to channel: {} with {} sessions", channel, sessionIds != null ? sessionIds.size() : 0);
        
        if (sessionIds == null || sessionIds.isEmpty()) {
            return;
        }

        sessionIds.forEach(sessionId -> {
            messageSenderExecutor.execute(sessionId, () -> {
                try {
                    WebSocketSession session = sessionById.get(sessionId);
                    if (session == null) {
                        return;
                    }
                    logger.info("Sending message to session {} on channel {}", sessionId, channel);
                    if (message instanceof L2OrderBook) {
                        doSendL2OrderBook(session, (L2OrderBook) message, channel);
                    } else {
                        doSendJsonWithChannel(session, message, channel);
                    }
                } catch (Exception e) {
                    logger.error("send error: {}", e.getMessage());
                }
            });
        });
    }

    private void sendL2OrderBookSnapshot(WebSocketSession session, String productId) {
        messageSenderExecutor.execute(session.getId(), () -> {
            try {
                L2OrderBook l2OrderBook = orderBookSnapshotManager.getL2BatchOrderBook(productId);
                if (l2OrderBook != null) {
                    doSendL2OrderBook(session, l2OrderBook);
                }
            } catch (Exception e) {
                logger.error("send level2 snapshot error: {}", e.getMessage(), e);
            }
        });
    }

    private void doSendL2OrderBook(WebSocketSession session, L2OrderBook l2OrderBook, String channel) throws IOException {
        String key = "LAST_L2_ORDER_BOOK:" + l2OrderBook.getProductId();

        if (!session.getAttributes().containsKey(key)) {
            L2SnapshotFeedMessage snapshotMessage = new L2SnapshotFeedMessage(l2OrderBook);
            doSendJsonWithChannel(session, snapshotMessage, channel);
            session.getAttributes().put(key, l2OrderBook);
            return;
        }

        L2OrderBook lastL2OrderBook = (L2OrderBook) session.getAttributes().get(key);
        if (lastL2OrderBook.getSequence() >= l2OrderBook.getSequence()) {
            logger.warn("discard l2 order book, too old: last={} new={}", lastL2OrderBook.getSequence(),
                    l2OrderBook.getSequence());
            return;
        }

        List<L2OrderBookChange> changes = lastL2OrderBook.diff(l2OrderBook);
        if (changes != null && !changes.isEmpty()) {
            L2UpdateFeedMessage l2UpdateFeedMessage = new L2UpdateFeedMessage(l2OrderBook.getProductId(), changes);
            doSendJsonWithChannel(session, l2UpdateFeedMessage, channel);
        }

        session.getAttributes().put(key, l2OrderBook);
    }

    private void doSendL2OrderBook(WebSocketSession session, L2OrderBook l2OrderBook) throws IOException {
        String key = "LAST_L2_ORDER_BOOK:" + l2OrderBook.getProductId();

        if (!session.getAttributes().containsKey(key)) {
            doSendJson(session, new L2SnapshotFeedMessage(l2OrderBook));
            session.getAttributes().put(key, l2OrderBook);
            return;
        }

        L2OrderBook lastL2OrderBook = (L2OrderBook) session.getAttributes().get(key);
        if (lastL2OrderBook.getSequence() >= l2OrderBook.getSequence()) {
            logger.warn("discard l2 order book, too old: last={} new={}", lastL2OrderBook.getSequence(),
                    l2OrderBook.getSequence());
            return;
        }

        List<L2OrderBookChange> changes = lastL2OrderBook.diff(l2OrderBook);
        if (changes != null && !changes.isEmpty()) {
            L2UpdateFeedMessage l2UpdateFeedMessage = new L2UpdateFeedMessage(l2OrderBook.getProductId(), changes);
            doSendJson(session, l2UpdateFeedMessage);
        }

        session.getAttributes().put(key, l2OrderBook);
    }

    private void sendTicker(WebSocketSession session, String productId) {
        messageSenderExecutor.execute(session.getId(), () -> {
            try {
                Ticker ticker = tickerManager.getTicker(productId);
                if (ticker != null) {
                    // For direct subscription (legacy), send without channel wrapper
                    doSendJson(session, new TickerFeedMessage(ticker));
                }
            } catch (Exception e) {
                logger.error("send ticker error: {}", e.getMessage(), e);
            }
        });
    }

    public void sendPong(WebSocketSession session) {
        messageSenderExecutor.execute(session.getId(), () -> {
            try {
                PongFeedMessage pongFeedMessage = new PongFeedMessage();
                pongFeedMessage.setType("pong");
                session.sendMessage(new TextMessage(JSON.toJSONString(pongFeedMessage)));
            } catch (Exception e) {
                logger.error("send pong error: {}", e.getMessage());
            }
        });
    }

    private void doSendJson(WebSocketSession session, Object msg) {
        try {
            session.sendMessage(new TextMessage(JSON.toJSONString(msg)));
        } catch (Exception e) {
            logger.error("send websocket message error: {}", e.getMessage());
        }
    }

    private void doSendJsonWithChannel(WebSocketSession session, Object msg, String channel) {
        try {
            // Wrap message with Peatio-style routing key for frontend compatibility
            Map<String, Object> wrappedMessage = PeatioMessageWrapper.wrap(channel, msg);
            session.sendMessage(new TextMessage(JSON.toJSONString(wrappedMessage)));
        } catch (Exception e) {
            logger.error("send websocket message with channel error: {}", e.getMessage());
        }
    }

    private void subscribeChannel(WebSocketSession session, String channel) {
        logger.info("Subscribing session {} to channel {}", session.getId(), channel);
        sessionIdsByChannel
                .computeIfAbsent(channel, k -> new ConcurrentSkipListSet<>())
                .add(session.getId());
        channelsBySessionId.computeIfAbsent(session.getId(), k -> new ConcurrentSkipListSet<>())
                .add(channel);
        sessionById.put(session.getId(), session);
    }

    public void unsubscribeChannel(WebSocketSession session, String channel) {
        if (sessionIdsByChannel.containsKey(channel)) {
            sessionIdsByChannel.get(channel).remove(session.getId());
        }
        channelsBySessionId.computeIfPresent(session.getId(), (k, v) -> {
            v.remove(channel);
            return v;
        });
    }

    public void removeSession(WebSocketSession session) {
        ConcurrentSkipListSet<String> channels = channelsBySessionId.remove(session.getId());
        if (channels != null) {
            for (String channel : channels) {
                ConcurrentSkipListSet<String> sessionIds = sessionIdsByChannel.get(channel);
                if (sessionIds != null) {
                    sessionIds.remove(session.getId());
                }
            }
        }
        sessionById.remove(session.getId());
    }

    public String getUserId(WebSocketSession session) {
        Object val = session.getAttributes().get("CURRENT_USER_ID");
        return val != null ? val.toString() : null;
    }

}
