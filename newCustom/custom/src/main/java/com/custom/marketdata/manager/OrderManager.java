package com.custom.marketdata.manager;

import com.custom.marketdata.entity.OrderEntity;
import com.custom.marketdata.repository.FillRepository;
import com.custom.marketdata.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collection;

@Component
@Slf4j
@RequiredArgsConstructor
public class OrderManager {
    private final OrderRepository orderRepository;
    private final FillRepository fillRepository;

    public void saveAll(Collection<OrderEntity> orders) {
        if (orders.isEmpty()) {
            return;
        }
        long t1 = System.currentTimeMillis();
        orderRepository.saveAll(orders);
        logger.info("saved {} order(s) ({}ms)", orders.size(), System.currentTimeMillis() - t1);
    }
}
