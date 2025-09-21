package com.custom.marketdata.binance.dto;

import lombok.Value;

import java.math.BigDecimal;
import java.util.Map;

@Value
public class OrderbookState {
    Map<String, BigDecimal> bids;
    Map<String, BigDecimal> asks;
}
