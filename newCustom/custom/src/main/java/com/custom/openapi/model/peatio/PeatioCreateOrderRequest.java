package com.custom.openapi.model.peatio;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class PeatioCreateOrderRequest {
    private String market;
    private String side;
    private String volume;  // Standard Peatio field
    private String amount;  // Finex API compatibility field
    private String ord_type;
    private String type;    // Finex API compatibility field
    private BigDecimal price;
    private BigDecimal size;
    
    // Helper method to get volume from either field
    public String getVolumeOrAmount() {
        return volume != null ? volume : amount;
    }
    
    // Helper method to get order type from either field
    public String getOrderType() {
        return ord_type != null ? ord_type : type;
    }
}
