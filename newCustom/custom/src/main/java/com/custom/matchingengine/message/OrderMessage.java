package com.custom.matchingengine.message;

import com.custom.matchingengine.Order;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class OrderMessage extends Message {
    private long orderBookSequence;
    private Order order;

    public OrderMessage() {
        this.setMessageType(MessageType.ORDER);
    }
}
