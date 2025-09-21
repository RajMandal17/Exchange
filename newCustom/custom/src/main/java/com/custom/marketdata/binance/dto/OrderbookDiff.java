package com.custom.marketdata.binance.dto;

import lombok.Value;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Set;

@Value
public class OrderbookDiff {
    Map<String, BigDecimal> bidsToAdd;
    Map<String, BigDecimal> asksToAdd;
    Set<String> bidsToRemove;
    Set<String> asksToRemove;
    
    public boolean hasChanges() {
        return !bidsToAdd.isEmpty() || !asksToAdd.isEmpty() || 
               !bidsToRemove.isEmpty() || !asksToRemove.isEmpty();
    }
}
