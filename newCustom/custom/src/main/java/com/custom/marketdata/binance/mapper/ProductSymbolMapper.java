package com.custom.marketdata.binance.mapper;

import com.custom.openapi.controller.ProductController;
import com.custom.openapi.model.ProductDto;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class ProductSymbolMapper {
    private static final Logger logger = LoggerFactory.getLogger(ProductSymbolMapper.class);
    
    @Autowired
    private ProductController productController; //BTC_USDT  - > btcusdt
    
    public Map<String, String> mapProductsToSymbols() {
        Map<String, String> mapping = new HashMap<>();
        List<ProductDto> products = productController.getProducts();
        
        if (products == null || products.isEmpty()) {
            logger.warn("No products found in the database. Binance orderbook integration will not work properly.");
            return mapping;
        }
        
        for (ProductDto product : products) {
            String productId = product.getId();
            if (productId != null && !productId.isEmpty()) {
                String binanceSymbol = convertProductIdToBinanceSymbol(productId);
                mapping.put(productId, binanceSymbol);
            }
        }
        
        logger.info("Product to Binance mapping initialized with {} products: {}", 
            mapping.size(), mapping);
        
        return mapping;
    }
    
    public String convertProductIdToBinanceSymbol(String productId) {
        return productId.replace("-", "").toLowerCase();
    }
}
