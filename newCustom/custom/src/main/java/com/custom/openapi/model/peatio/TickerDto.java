package com.custom.openapi.model.peatio;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class TickerDto {
    private BigDecimal amount;
    private BigDecimal avg_price;
    private BigDecimal high;
    private BigDecimal last;
    private BigDecimal low;
    private BigDecimal open;
    private String price_change_percent;
    private BigDecimal volume;
}
