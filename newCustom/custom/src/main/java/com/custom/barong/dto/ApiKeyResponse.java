package com.custom.barong.dto;

import com.custom.marketdata.entity.ApiKey;

import java.time.LocalDateTime;

public class ApiKeyResponse {
    private String id;
    private String kid;
    private String algorithm;
    private String scope;
    private String state;
    private String secret;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public ApiKeyResponse() {}

    public ApiKeyResponse(ApiKey apiKey) {
        this.id = apiKey.getId();
        this.kid = apiKey.getKid();
        this.algorithm = apiKey.getAlgorithm();
        this.scope = apiKey.getScope();
        this.state = apiKey.getState();
        // Don't include secret in response for security
        this.secret = null; 
        this.createdAt = apiKey.getCreatedAt();
        this.updatedAt = apiKey.getUpdatedAt();
    }

    // Include secret only for newly created keys
    public ApiKeyResponse(ApiKey apiKey, boolean includeSecret) {
        this(apiKey);
        if (includeSecret) {
            this.secret = apiKey.getSecret();
        }
    }

    // Getters and setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getKid() { return kid; }
    public void setKid(String kid) { this.kid = kid; }

    public String getAlgorithm() { return algorithm; }
    public void setAlgorithm(String algorithm) { this.algorithm = algorithm; }

    public String getScope() { return scope; }
    public void setScope(String scope) { this.scope = scope; }

    public String getState() { return state; }
    public void setState(String state) { this.state = state; }

    public String getSecret() { return secret; }
    public void setSecret(String secret) { this.secret = secret; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
