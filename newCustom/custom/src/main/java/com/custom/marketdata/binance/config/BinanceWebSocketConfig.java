package com.custom.marketdata.binance.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.client.WebSocketClient;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;

@Configuration
@RequiredArgsConstructor
public class BinanceWebSocketConfig {
    private final BinanceOrderbookConfig config;
    
    @Bean
    public WebSocketClient binanceWebSocketClient() {
        return new StandardWebSocketClient();
    }
}
