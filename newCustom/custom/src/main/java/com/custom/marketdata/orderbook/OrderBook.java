package com.custom.marketdata.orderbook;

import com.custom.enums.OrderSide;
import com.custom.matchingengine.Depth;
import com.custom.matchingengine.Order;
import lombok.Getter;
import lombok.Setter;

import java.util.Comparator;

@Getter
public class OrderBook {
    private final String productId;
    private final Depth asks = new Depth(Comparator.naturalOrder());
    private final Depth bids = new Depth(Comparator.reverseOrder());
    @Setter
    private long sequence;

    public OrderBook(String productId) {
        this.productId = productId;
    }

    public OrderBook(String productId, long sequence) {
        this.productId = productId;
        this.sequence = sequence;
    }

    public void addOrder(Order order) {
        var depth = order.getSide() == OrderSide.BUY ? bids : asks;
        depth.addOrder(order);
    }

    public void removeOrder(Order order) {
        var depth = order.getSide() == OrderSide.BUY ? bids : asks;
        depth.removeOrder(order);
    }
}
