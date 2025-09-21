package com.custom.barong.dto;

import com.custom.marketdata.entity.User;
import java.time.LocalDateTime;

public class UserResponse {
    private String uid;
    private String email;
    private String role;
    private String level;
    private boolean otpEnabled;
    private String state;
    private String referralUid;
    private String language;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String firstName;
    private String lastName;

    public UserResponse() {}

    public UserResponse(User user) {
        this.uid = user.getUid();
        this.email = user.getEmail(); // Using email field
        this.role = user.getRole();
        this.level = user.getLevel() != null ? user.getLevel().toString() : "1";
        this.otpEnabled = user.getOtpEnabled() != null ? user.getOtpEnabled() : false;
        this.state = user.getState();
        this.referralUid = user.getReferralUid();
        this.language = user.getLanguage();
        this.createdAt = user.getCreatedAtLocal();
        this.updatedAt = user.getUpdatedAtLocal();
        this.firstName = user.getNickName(); // Use nickName as firstName for now
        this.lastName = null; // Not available in current User entity
    }

    // Getters and setters
    public String getUid() { return uid; }
    public void setUid(String uid) { this.uid = uid; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public String getLevel() { return level; }
    public void setLevel(String level) { this.level = level; }

    public boolean isOtpEnabled() { return otpEnabled; }
    public void setOtpEnabled(boolean otpEnabled) { this.otpEnabled = otpEnabled; }

    public String getState() { return state; }
    public void setState(String state) { this.state = state; }

    public String getReferralUid() { return referralUid; }
    public void setReferralUid(String referralUid) { this.referralUid = referralUid; }

    public String getLanguage() { return language; }
    public void setLanguage(String language) { this.language = language; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    public String getFirstName() { return firstName; }
    public void setFirstName(String firstName) { this.firstName = firstName; }

    public String getLastName() { return lastName; }
    public void setLastName(String lastName) { this.lastName = lastName; }
}
