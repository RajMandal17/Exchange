package com.custom.marketdata.entity;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class Session {
    private String sessionId;         // Primary key
    private String userId;
    private String ipAddress;
    private String userAgent;
    private String csrfToken;
    private LocalDateTime expiresAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
