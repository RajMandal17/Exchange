package com.custom.marketdata.entity;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class Label {
    private String id;
    private String userId;
    private String key;               // Label key (e.g., "email", "phone", "document")
    private String value;             // Label value
    private String scope = "private"; // private, public
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
