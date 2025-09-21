package com.custom.marketdata.binance.service;

import com.custom.marketdata.binance.dto.OrderbookDiff;
import com.custom.marketdata.binance.dto.OrderbookEntry;
import com.custom.marketdata.binance.dto.OrderbookState;
import com.custom.marketdata.repository.OrderRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderbookComparisonService {
    private final OrderRepository orderRepository;
    private final ObjectMapper objectMapper;
    
    /**
     * Parses local orderbook JSON into a structured format
     */
    public OrderbookState parseLocalOrderbook(String orderbookJson) throws JsonProcessingException {
        if (orderbookJson == null || orderbookJson.isEmpty()) {
            return new OrderbookState(new HashMap<>(), new HashMap<>());
        }
        
        JsonNode root = objectMapper.readTree(orderbookJson);
        
        Map<String, BigDecimal> bids = new HashMap<>();
        Map<String, BigDecimal> asks = new HashMap<>();
        
        // Parse bids
        if (root.has("bids") && root.get("bids").isArray()) {
            for (JsonNode bid : root.get("bids")) {
                if (bid.isArray() && bid.size() >= 2) {
                    String price = bid.get(0).asText();
                    BigDecimal size = new BigDecimal(bid.get(1).asText());
                    bids.put(price, size);
                }
            }
        }
        
        // Parse asks
        if (root.has("asks") && root.get("asks").isArray()) {
            for (JsonNode ask : root.get("asks")) {
                if (ask.isArray() && ask.size() >= 2) {
                    String price = ask.get(0).asText();
                    BigDecimal size = new BigDecimal(ask.get(1).asText());
                    asks.put(price, size);
                }
            }
        }
        
        return new OrderbookState(bids, asks);
    }
    
    /**
     * Generates a diff between local and Binance orderbooks
     */
    public OrderbookDiff generateOrderbookDiff(OrderbookState local, Map<String, OrderbookEntry> binance) {
        Map<String, BigDecimal> bidsToAdd = new HashMap<>();
        Map<String, BigDecimal> asksToAdd = new HashMap<>();
        Set<String> bidsToRemove = new HashSet<>();
        Set<String> asksToRemove = new HashSet<>();
        
        // Process bids
        for (Map.Entry<String, OrderbookEntry> entry : binance.entrySet()) {
            OrderbookEntry binanceEntry = entry.getValue();
            if (binanceEntry.isBid()) {
                String price = binanceEntry.getPrice();
                BigDecimal binanceSize = binanceEntry.getSize();
                BigDecimal localSize = local.getBids().getOrDefault(price, BigDecimal.ZERO);
                
                if (binanceSize.compareTo(BigDecimal.ZERO) > 0 && 
                    (localSize.compareTo(BigDecimal.ZERO) == 0 || 
                     !binanceSize.equals(localSize))) {
                    // Add or update bid
                    bidsToAdd.put(price, binanceSize);
                }
            }
        }
        
        // Find bids to remove
        for (String price : local.getBids().keySet()) {
            boolean exists = false;
            for (OrderbookEntry entry : binance.values()) {
                if (entry.isBid() && entry.getPrice().equals(price) && 
                    entry.getSize().compareTo(BigDecimal.ZERO) > 0) {
                    exists = true;
                    break;
                }
            }
            if (!exists) {
                bidsToRemove.add(price);
            }
        }
        
        // Process asks
        for (Map.Entry<String, OrderbookEntry> entry : binance.entrySet()) {
            OrderbookEntry binanceEntry = entry.getValue();
            if (!binanceEntry.isBid()) {
                String price = binanceEntry.getPrice();
                BigDecimal binanceSize = binanceEntry.getSize();
                BigDecimal localSize = local.getAsks().getOrDefault(price, BigDecimal.ZERO);
                
                if (binanceSize.compareTo(BigDecimal.ZERO) > 0 && 
                    (localSize.compareTo(BigDecimal.ZERO) == 0 || 
                     !binanceSize.equals(localSize))) {
                    // Add or update ask
                    asksToAdd.put(price, binanceSize);
                }
            }
        }
        
        // Find asks to remove
        for (String price : local.getAsks().keySet()) {
            boolean exists = false;
            for (OrderbookEntry entry : binance.values()) {
                if (!entry.isBid() && entry.getPrice().equals(price) && 
                    entry.getSize().compareTo(BigDecimal.ZERO) > 0) {
                    exists = true;
                    break;
                }
            }
            if (!exists) {
                asksToRemove.add(price);
            }
        }
        
        return new OrderbookDiff(bidsToAdd, asksToAdd, bidsToRemove, asksToRemove);
    }
}
