package com.custom.marketdata.entity;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class Phone {
    private String id;
    private String userId;
    private String country;           // Country code (e.g., "US", "UA")
    private String number;            // Phone number
    private String validatedAt;       // ISO timestamp when validated
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
