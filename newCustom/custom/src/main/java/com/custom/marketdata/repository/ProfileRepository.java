package com.custom.marketdata.repository;

import com.custom.marketdata.entity.Profile;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Indexes;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class ProfileRepository {
    private final MongoCollection<Profile> collection;

    public ProfileRepository(MongoDatabase database) {
        this.collection = database.getCollection(Profile.class.getSimpleName().toLowerCase(), Profile.class);
        this.collection.createIndex(Indexes.descending("userId"), new IndexOptions().unique(true));
    }

    /**
     * Find profile by user ID
     * @param userId The user ID
     * @return Profile or null
     */
    public Profile findByUserId(String userId) {
        return this.collection
                .find(Filters.eq("userId", userId))
                .first();
    }
    
    /**
     * Find all profiles by state
     * @param state The profile state (submitted, verified, rejected)
     * @return List of profiles
     */
    public List<Profile> findByState(String state) {
        List<Profile> profiles = new ArrayList<>();
        this.collection
                .find(Filters.eq("state", state))
                .into(profiles);
        return profiles;
    }
    
    /**
     * Check if profile exists for user
     * @param userId The user ID
     * @return true if exists
     */
    public boolean existsByUserId(String userId) {
        return this.collection
                .countDocuments(Filters.eq("userId", userId)) > 0;
    }
    
    /**
     * Save or update profile
     * @param profile The profile to save
     * @return Saved profile
     */
    public Profile save(Profile profile) {
        if (profile.getId() == null) {
            profile.setId(java.util.UUID.randomUUID().toString());
            profile.setCreatedAt(java.time.LocalDateTime.now());
        }
        profile.setUpdatedAt(java.time.LocalDateTime.now());
        
        this.collection.replaceOne(
                Filters.eq("_id", profile.getId()),
                profile,
                new com.mongodb.client.model.ReplaceOptions().upsert(true)
        );
        return profile;
    }
    
    /**
     * Delete profile by user ID
     * @param userId The user ID
     */
    public void deleteByUserId(String userId) {
        this.collection.deleteOne(Filters.eq("userId", userId));
    }
}
