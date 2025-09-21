package com.custom.openapi.model.peatio;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class PeatioOrderDto {
    private String id;
    private String market;
    private String side;
    private String ord_type;
    private BigDecimal price;
    private BigDecimal avg_price;
    private BigDecimal amount;
    private BigDecimal remaining_volume;
    private BigDecimal executed_volume;
    private String state;
    private String created_at;
    private String updated_at;
}
