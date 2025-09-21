package com.custom.matchingengine.message;

import com.custom.matchingengine.Trade;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class TradeMessage extends Message {
    private Trade trade;

    public TradeMessage() {
        this.setMessageType(MessageType.TRADE);
    }
}
