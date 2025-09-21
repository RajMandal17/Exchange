package com.custom.marketdata.binance.exception;

public class BinanceIntegrationException extends RuntimeException {
    
    public BinanceIntegrationException(String message) {
        super(message);
    }
    
    public BinanceIntegrationException(String message, Throwable cause) {
        super(message, cause);
    }
}
