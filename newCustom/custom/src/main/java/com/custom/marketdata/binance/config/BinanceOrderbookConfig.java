package com.custom.marketdata.binance.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "binance.orderbook")
@Data
public class BinanceOrderbookConfig {
    private boolean enabled = false;
    private int syncInterval = 5000;
    private int productRefreshInterval = 300000;
    private String websocketUrl = "wss://stream.binance.com/stream?streams=";
    private String botUserId = "000000";
}
