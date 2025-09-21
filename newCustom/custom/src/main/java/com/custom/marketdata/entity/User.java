package com.custom.marketdata.entity;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.List;

@Getter
@Setter
public class User {
    // Existing fields - keep for backward compatibility
    private String id;
    private Date createdAt;
    private Date updatedAt;
    private String email;
    private String passwordHash;
    private String passwordSalt;
    private String twoStepVerificationType;
    private BigDecimal gotpSecret;
    private String nickName;
    
    // New Barong fields
    private String uid;                   // Unique ID for external reference (generate from id if null)
    private String role = "member";       // member, admin, trader  
    private Integer level = 1;            // User level (1-5)
    private Boolean otpEnabled = false;   // 2FA enabled (use twoStepVerificationType for backward compatibility)
    private String state = "active";     // pending, active, banned, suspended
    private String data;                  // JSON data for user preferences
    private String referralUid;
    private String language = "en";
    
    // Relations - will be loaded separately for performance
    private transient List<Profile> profiles;
    private transient List<Phone> phones;
    private transient List<Document> documents;
    private transient List<Label> labels;
    private transient List<ApiKey> apiKeys;
    
    // Helper methods for backward compatibility
    public String getUid() {
        if (uid != null) {
            return uid;
        }
        if (id != null && !id.isEmpty()) {
            return "ID" + id.substring(0, Math.min(12, id.length())).toUpperCase();
        }
        return null;
    }
    
    public Boolean getOtpEnabled() {
        return otpEnabled != null ? otpEnabled : 
               (twoStepVerificationType != null && !twoStepVerificationType.equals("none"));
    }
    
    public LocalDateTime getCreatedAtLocal() {
        return createdAt != null ? 
               LocalDateTime.ofInstant(createdAt.toInstant(), java.time.ZoneOffset.UTC) : null;
    }
    
    public LocalDateTime getUpdatedAtLocal() {
        return updatedAt != null ? 
               LocalDateTime.ofInstant(updatedAt.toInstant(), java.time.ZoneOffset.UTC) : null;
    }
}
