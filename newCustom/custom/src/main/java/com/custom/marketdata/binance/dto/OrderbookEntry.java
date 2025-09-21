package com.custom.marketdata.binance.dto;

import lombok.Value;

import java.math.BigDecimal;

@Value
public class OrderbookEntry {
    String price;
    BigDecimal size;
    boolean isBid;
}
