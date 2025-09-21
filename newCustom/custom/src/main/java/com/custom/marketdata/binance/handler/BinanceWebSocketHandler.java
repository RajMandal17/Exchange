package com.custom.marketdata.binance.handler;

import com.custom.marketdata.binance.dto.BinanceOrderbookData;
import com.custom.marketdata.binance.dto.OrderbookEntry;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@RequiredArgsConstructor
public class BinanceWebSocketHandler extends TextWebSocketHandler {
    private static final Logger logger = LoggerFactory.getLogger(BinanceWebSocketHandler.class);
    private final ObjectMapper objectMapper;
    private final Map<String, Map<String, OrderbookEntry>> binanceOrderbooks = new ConcurrentHashMap<>();
    
    @Override
    public void handleMessage(WebSocketSession session, WebSocketMessage<?> message) {
        try {
            String payload = (String) message.getPayload();
            JsonNode rootNode = objectMapper.readTree(payload);
            
            // Extract the stream name to determine the symbol
            if (rootNode.has("stream") && rootNode.has("data")) {
                String stream = rootNode.get("stream").asText();
                JsonNode data = rootNode.get("data");
                
                // Extract symbol from stream name (format: symbol@depth)
                String[] streamParts = stream.split("@");
                if (streamParts.length >= 1) {
                    String symbol = streamParts[0];
                    processDepthData(symbol, data);
                }
            }
        } catch (Exception e) {
            logger.error("Error processing WebSocket message", e);
        }
    }
    
    private void processDepthData(String symbol, JsonNode data) {
        try {
            Map<String, OrderbookEntry> orderbook = binanceOrderbooks.get(symbol);
            if (orderbook == null) {
                orderbook = new HashMap<>();
                binanceOrderbooks.put(symbol, orderbook);
            } else {
                // Clear the previous state to avoid stale data
                orderbook.clear();
            }
            
            // Process bids
            if (data.has("bids") && data.get("bids").isArray()) {
                for (JsonNode bidNode : data.get("bids")) {
                    if (bidNode.isArray() && bidNode.size() >= 2) {
                        String price = bidNode.get(0).asText();
                        BigDecimal size = new BigDecimal(bidNode.get(1).asText());
                        orderbook.put("BID_" + price, new OrderbookEntry(price, size, true));
                    }
                }
            }
            
            // Process asks
            if (data.has("asks") && data.get("asks").isArray()) {
                for (JsonNode askNode : data.get("asks")) {
                    if (askNode.isArray() && askNode.size() >= 2) {
                        String price = askNode.get(0).asText();
                        BigDecimal size = new BigDecimal(askNode.get(1).asText());
                        orderbook.put("ASK_" + price, new OrderbookEntry(price, size, false));
                    }
                }
            }
            
            logger.debug("Updated Binance orderbook for {}: {} bids, {} asks",
                    symbol, 
                    binanceOrderbooks.containsKey(symbol) ? binanceOrderbooks.get(symbol).size() : 0,
                    binanceOrderbooks.containsKey(symbol) ? binanceOrderbooks.get(symbol).size() : 0);        } catch (Exception e) {
            logger.error("Error processing depth data for symbol {}", symbol, e);
        }
    }
    
    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        logger.info("Binance WebSocket connection established");
    }
    
    public Map<String, Map<String, OrderbookEntry>> getBinanceOrderbooks() {
        return binanceOrderbooks;
    }
}
