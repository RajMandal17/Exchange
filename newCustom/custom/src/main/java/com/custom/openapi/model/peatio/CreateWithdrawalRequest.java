package com.custom.openapi.model.peatio;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class CreateWithdrawalRequest {
    private String currency;
    private BigDecimal amount;
    private String rid;
    private String otp;
}
