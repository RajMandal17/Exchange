package com.custom.openapi.model.peatio;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class PeatioTradeDto {
    private Long id;
    private BigDecimal price;
    private BigDecimal amount;
    private BigDecimal total;
    private String market;
    private String created_at;
    private String taker_type;
}
