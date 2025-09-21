package com.custom.marketdata.entity;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
public class BarongUser {
    private String id;                    // UUID
    private String email;
    private String passwordHash;
    private String uid;                   // Unique ID for external reference
    private String role = "member";       // member, admin, trader
    private Integer level = 1;            // User level (1-5)
    private Boolean otpEnabled = false;   // 2FA enabled
    private String state = "pending";     // pending, active, banned, suspended
    private String data;                  // JSON data for user preferences
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    
    // Additional Barong fields
    private String referralUid;
    private String language = "en";
    
    // Relations
    private List<Profile> profiles;
    private List<Phone> phones;
    private List<Document> documents;
    private List<Label> labels;
    private List<ApiKey> apiKeys;
}
