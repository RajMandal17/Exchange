package com.custom.feed.message;

import lombok.Getter;
import lombok.Setter;

import java.util.HashMap;
import java.util.Map;

/**
 * Peatio-compatible message wrapper that wraps messages with routing keys
 * as expected by the frontend
 */
@Getter
@Setter
public class PeatioMessageWrapper {
    
    /**
     * Creates a Peatio-style message with routing key
     * 
     * @param routingKey The routing key (channel name)
     * @param message The actual message data
     * @return Map with routing key as key and message as value
     */
    public static Map<String, Object> wrap(String routingKey, Object message) {
        Map<String, Object> wrapper = new HashMap<>();
        wrapper.put(routingKey, message);
        return wrapper;
    }
}
