package com.custom.openapi.model.peatio;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class PeatioBalanceDto {
    private String currency;
    private BigDecimal balance;
    private BigDecimal locked;
}
