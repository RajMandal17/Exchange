package com.custom.marketdata.binance.dto;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;

@Data
public class BinanceOrderbookData {
    private String stream;
    private JsonNode data;
}
