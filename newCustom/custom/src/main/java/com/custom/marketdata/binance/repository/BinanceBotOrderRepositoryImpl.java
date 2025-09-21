package com.custom.marketdata.binance.repository;

import com.custom.marketdata.entity.OrderEntity;
import com.custom.marketdata.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class BinanceBotOrderRepositoryImpl implements BinanceBotOrderRepository {
    
    private final OrderRepository orderRepository;
    
    @Override
    public OrderEntity findBotOrderByPriceAndSide(String productId, String price, String side, boolean isBot) {
        return orderRepository.findBotOrderByPriceAndSide(productId, price, side, isBot);
    }
    
    @Override
    public List<OrderEntity> findBotOrdersToCancel(String productId, List<String> activePrices, String side, boolean isBot) {
        return orderRepository.findBotOrdersToCancel(productId, activePrices, side, isBot);
    }
}
