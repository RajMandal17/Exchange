package com.custom.marketdata.repository;

import com.custom.marketdata.entity.Label;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Indexes;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class LabelRepository {
    private final MongoCollection<Label> collection;

    public LabelRepository(MongoDatabase database) {
        this.collection = database.getCollection(Label.class.getSimpleName().toLowerCase(), Label.class);
        this.collection.createIndex(Indexes.descending("userId"));
    }

    /**
     * Find labels by user ID
     * @param userId The user ID
     * @return List of labels
     */
    public List<Label> findByUserId(String userId) {
        List<Label> labels = new ArrayList<>();
        this.collection
                .find(Filters.eq("userId", userId))
                .into(labels);
        return labels;
    }
    
    /**
     * Find label by user ID and key
     * @param userId The user ID
     * @param key The label key
     * @return Label or null
     */
    public Label findByUserIdAndKey(String userId, String key) {
        return this.collection
                .find(Filters.and(
                        Filters.eq("userId", userId),
                        Filters.eq("key", key)
                ))
                .first();
    }
    
    /**
     * Find all labels by key
     * @param key The label key
     * @return List of labels
     */
    public List<Label> findByKey(String key) {
        List<Label> labels = new ArrayList<>();
        this.collection
                .find(Filters.eq("key", key))
                .into(labels);
        return labels;
    }
    
    /**
     * Find all labels by scope
     * @param scope The label scope
     * @return List of labels
     */
    public List<Label> findByScope(String scope) {
        List<Label> labels = new ArrayList<>();
        this.collection
                .find(Filters.eq("scope", scope))
                .into(labels);
        return labels;
    }
    
    /**
     * Save or update label
     * @param label The label to save
     * @return Saved label
     */
    public Label save(Label label) {
        if (label.getId() == null) {
            label.setId(java.util.UUID.randomUUID().toString());
            label.setCreatedAt(java.time.LocalDateTime.now());
        }
        label.setUpdatedAt(java.time.LocalDateTime.now());
        
        this.collection.replaceOne(
                Filters.eq("_id", label.getId()),
                label,
                new com.mongodb.client.model.ReplaceOptions().upsert(true)
        );
        return label;
    }
    
    /**
     * Delete label by ID
     * @param id The label ID
     */
    public void deleteById(String id) {
        this.collection.deleteOne(Filters.eq("_id", id));
    }
    
    /**
     * Delete labels by user ID
     * @param userId The user ID
     */
    public void deleteByUserId(String userId) {
        this.collection.deleteMany(Filters.eq("userId", userId));
    }
}
