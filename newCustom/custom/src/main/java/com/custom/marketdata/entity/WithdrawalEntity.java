package com.custom.marketdata.entity;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.Date;

@Getter
@Setter
public class WithdrawalEntity {
    private String id;
    private Date createdAt;
    private Date updatedAt;
    private String userId;
    private String currency;
    private BigDecimal amount;
    private BigDecimal fee;
    private String txid;
    private String rid; // recipient address for crypto withdrawals
    private String state; // pending, submitted, rejected, accepted, skipped, processing, succeed, failed, confirming
    private String type; // fiat or crypto
    private String notes;
    private boolean settled;
}
