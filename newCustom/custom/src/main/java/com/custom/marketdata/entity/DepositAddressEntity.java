package com.custom.marketdata.entity;

import lombok.Getter;
import lombok.Setter;

import java.util.Date;

@Getter
@Setter
public class DepositAddressEntity {
    private String id;
    private Date createdAt;
    private Date updatedAt;
    private String userId;
    private String currency;
    private String address;
    private String state; // active, disabled
    private String blockchain; // for crypto currencies
    private String notes;
}
