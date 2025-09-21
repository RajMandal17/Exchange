package com.custom.feed.message;

import com.custom.marketdata.orderbook.L2OrderBook;
import lombok.Getter;
import lombok.Setter;
import org.springframework.beans.BeanUtils;

@Getter
@Setter
public class L2SnapshotFeedMessage extends L2OrderBook {
    private String type = "snapshot";

    public L2SnapshotFeedMessage(L2OrderBook snapshot) {
        BeanUtils.copyProperties(snapshot, this);
    }
}
