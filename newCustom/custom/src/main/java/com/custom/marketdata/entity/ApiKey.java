package com.custom.marketdata.entity;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class ApiKey {
    private String id;
    private String userId;
    private String kid;               // Key ID (unique identifier)
    private String algorithm = "HS256"; // Encryption algorithm
    private String scope = "read";    // read, trade, withdraw
    private String state = "active";  // active, inactive
    private String secret;            // API secret key
    private String note;              // Optional note/description
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
