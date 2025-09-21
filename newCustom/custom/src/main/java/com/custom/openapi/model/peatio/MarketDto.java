package com.custom.openapi.model.peatio;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class MarketDto {
    private String id;
    private String name;
    private String base_unit;
    private String quote_unit;
    private BigDecimal ask_fee;
    private BigDecimal bid_fee;
    private BigDecimal min_price;
    private BigDecimal max_price;
    private BigDecimal min_amount;
    private Integer amount_precision;
    private Integer price_precision;
    private String state;
}
