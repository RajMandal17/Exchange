package com.custom.repository.barong;

import com.custom.marketdata.entity.ApiKey;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ApiKeyRepository {
    
    List<ApiKey> findByUserId(String userId);
    
    ApiKey findByIdAndUserId(String id, String userId);
    
    List<ApiKey> findByUserIdAndState(String userId, String state);
    
    ApiKey findByKid(String kid);
    
    long countByUserId(String userId);
    
    ApiKey save(ApiKey apiKey);
    
    void delete(ApiKey apiKey);
}
