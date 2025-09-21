package com.custom.marketdata.entity;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class BeneficiaryEntity {
    private Long id;
    private String userId;
    private String currency;
    private String name;
    private String description;
    private String state; // pending, active, etc.
    private String data; // JSON for crypto or bank details
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}