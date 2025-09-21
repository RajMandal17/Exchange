package com.custom.barong.service;

import com.custom.barong.dto.ApiKeyResponse;
import com.custom.barong.dto.CreateApiKeyRequest;
import com.custom.barong.dto.UpdateApiKeyRequest;
import com.custom.marketdata.entity.ApiKey;
import com.custom.repository.barong.ApiKeyRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class ApiKeyService {

    @Autowired
    private ApiKeyRepository apiKeyRepository;

    private final SecureRandom secureRandom = new SecureRandom();

    /**
     * Get all API keys for a user
     */
    public List<ApiKeyResponse> getUserApiKeys(String userId) {
        List<ApiKey> apiKeys = apiKeyRepository.findByUserId(userId);
        return apiKeys.stream()
                .map(ApiKeyResponse::new)
                .collect(Collectors.toList());
    }

    /**
     * Get a specific API key by ID for a user
     */
    public ApiKey getApiKey(String id, String userId) {
        return apiKeyRepository.findByIdAndUserId(id, userId);
    }

    /**
     * Create a new API key
     */
    public ApiKey createApiKey(String userId, CreateApiKeyRequest request) {
        if (request.getAlgorithm() == null || request.getAlgorithm().trim().isEmpty()) {
            throw new IllegalArgumentException("Algorithm is required");
        }

        // Validate algorithm
        if (!isValidAlgorithm(request.getAlgorithm())) {
            throw new IllegalArgumentException("Invalid algorithm. Supported: HS256, HS384, HS512");
        }

        // Validate scope
        if (request.getScope() != null && !isValidScope(request.getScope())) {
            throw new IllegalArgumentException("Invalid scope. Supported: read, trade");
        }

        ApiKey apiKey = new ApiKey();
        apiKey.setId(UUID.randomUUID().toString());
        apiKey.setUserId(userId);
        apiKey.setKid(generateKid());
        apiKey.setAlgorithm(request.getAlgorithm());
        apiKey.setScope(request.getScope() != null ? request.getScope() : "read");
        apiKey.setState("active");
        apiKey.setSecret(generateSecret());
        apiKey.setNote(request.getNote());
        apiKey.setCreatedAt(LocalDateTime.now());
        apiKey.setUpdatedAt(LocalDateTime.now());

        return apiKeyRepository.save(apiKey);
    }

    /**
     * Update an existing API key
     */
    public ApiKey updateApiKey(String id, String userId, UpdateApiKeyRequest request) {
        ApiKey apiKey = apiKeyRepository.findByIdAndUserId(id, userId);
        if (apiKey == null) {
            return null;
        }

        boolean updated = false;

        if (request.getScope() != null) {
            if (!isValidScope(request.getScope())) {
                throw new IllegalArgumentException("Invalid scope. Supported: read, trade");
            }
            apiKey.setScope(request.getScope());
            updated = true;
        }

        if (request.getState() != null) {
            if (!isValidState(request.getState())) {
                throw new IllegalArgumentException("Invalid state. Supported: active, inactive");
            }
            apiKey.setState(request.getState());
            updated = true;
        }

        if (request.getNote() != null) {
            apiKey.setNote(request.getNote());
            updated = true;
        }

        if (updated) {
            apiKey.setUpdatedAt(LocalDateTime.now());
            return apiKeyRepository.save(apiKey);
        }

        return apiKey;
    }

    /**
     * Delete an API key
     */
    public boolean deleteApiKey(String id, String userId) {
        ApiKey apiKey = apiKeyRepository.findByIdAndUserId(id, userId);
        if (apiKey != null) {
            apiKeyRepository.delete(apiKey);
            return true;
        }
        return false;
    }

    /**
     * Generate a random KID (Key ID)
     */
    private String generateKid() {
        byte[] randomBytes = new byte[16];
        secureRandom.nextBytes(randomBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    }

    /**
     * Generate a secure random secret
     */
    private String generateSecret() {
        byte[] randomBytes = new byte[32]; // 256 bits
        secureRandom.nextBytes(randomBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    }

    /**
     * Validate algorithm
     */
    private boolean isValidAlgorithm(String algorithm) {
        return "HS256".equals(algorithm) || "HS384".equals(algorithm) || "HS512".equals(algorithm);
    }

    /**
     * Validate scope
     */
    private boolean isValidScope(String scope) {
        return "read".equals(scope) || "trade".equals(scope);
    }

    /**
     * Validate state
     */
    private boolean isValidState(String state) {
        return "active".equals(state) || "inactive".equals(state);
    }
}
