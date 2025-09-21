package com.custom.marketdata.binance;

import com.custom.marketdata.binance.service.BinanceOrderbookService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Configuration class for Binance orderbook integration.
 * This class imports the binance-config.properties file and enables scheduling.
 */
@Configuration
@EnableScheduling
@PropertySource(value = "classpath:binance-config.properties", ignoreResourceNotFound = true)
public class BinanceOrderbookSetup {
    private static final Logger logger = LoggerFactory.getLogger(BinanceOrderbookSetup.class);
    
    @Autowired
    private BinanceOrderbookService binanceOrderbookService;
    
    /**
     * Bean that logs the integration setup.
     * This is just a placeholder to demonstrate integration.
     * 
     * @return A dummy bean to show initialization
     */
    @Bean
    public Object binanceOrderbookInitializer() {
        logger.info("Binance orderbook integration setup complete.");
        logger.info("To manually reconnect WebSockets: binanceOrderbookService.reconnectWebSockets()");
        return new Object();
    }
}
