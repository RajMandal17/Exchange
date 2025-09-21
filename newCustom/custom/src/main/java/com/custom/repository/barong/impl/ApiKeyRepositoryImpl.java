package com.custom.repository.barong.impl;

import com.custom.marketdata.entity.ApiKey;
import com.custom.repository.barong.ApiKeyRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Repository
public class ApiKeyRepositoryImpl implements ApiKeyRepository {
    
    // In-memory storage for now - you can replace with MongoDB later
    private final Map<String, ApiKey> apiKeys = new ConcurrentHashMap<>();
    
    @Override
    public List<ApiKey> findByUserId(String userId) {
        return apiKeys.values().stream()
                .filter(apiKey -> userId.equals(apiKey.getUserId()))
                .collect(Collectors.toList());
    }
    
    @Override
    public ApiKey findByIdAndUserId(String id, String userId) {
        ApiKey apiKey = apiKeys.get(id);
        if (apiKey != null && userId.equals(apiKey.getUserId())) {
            return apiKey;
        }
        return null;
    }
    
    @Override
    public List<ApiKey> findByUserIdAndState(String userId, String state) {
        return apiKeys.values().stream()
                .filter(apiKey -> userId.equals(apiKey.getUserId()) && state.equals(apiKey.getState()))
                .collect(Collectors.toList());
    }
    
    @Override
    public ApiKey findByKid(String kid) {
        return apiKeys.values().stream()
                .filter(apiKey -> kid.equals(apiKey.getKid()))
                .findFirst()
                .orElse(null);
    }
    
    @Override
    public long countByUserId(String userId) {
        return apiKeys.values().stream()
                .filter(apiKey -> userId.equals(apiKey.getUserId()))
                .count();
    }
    
    @Override
    public ApiKey save(ApiKey apiKey) {
        apiKeys.put(apiKey.getId(), apiKey);
        return apiKey;
    }
    
    @Override
    public void delete(ApiKey apiKey) {
        apiKeys.remove(apiKey.getId());
    }
}
