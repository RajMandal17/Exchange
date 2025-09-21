package com.custom.marketdata.repository;

import com.custom.marketdata.entity.AppEntity;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class AppRepository {

    public List<AppEntity> findByUserId(String userId) {
        return null;
    }

    public AppEntity findByAppId(String appId) {
        return null;
    }

    public void save(AppEntity appEntity) {

    }

    public void deleteById(String id) {

    }
}
