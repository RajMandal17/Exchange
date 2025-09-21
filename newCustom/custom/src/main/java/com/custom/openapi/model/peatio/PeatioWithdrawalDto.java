package com.custom.openapi.model.peatio;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class PeatioWithdrawalDto {
    private String id;
    private String currency;
    private BigDecimal amount;
    private BigDecimal fee;
    private String txid;
    private String rid;
    private String state;
    private String created_at;
    private String updated_at;
}
