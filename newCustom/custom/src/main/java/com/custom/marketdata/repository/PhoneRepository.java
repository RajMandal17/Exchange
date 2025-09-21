package com.custom.marketdata.repository;

import com.custom.marketdata.entity.Phone;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Indexes;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class PhoneRepository {
    private final MongoCollection<Phone> collection;

    public PhoneRepository(MongoDatabase database) {
        this.collection = database.getCollection(Phone.class.getSimpleName().toLowerCase(), Phone.class);
        this.collection.createIndex(Indexes.descending("userId"));
        this.collection.createIndex(Indexes.compoundIndex(Indexes.descending("country"), Indexes.descending("number")), 
                                   new IndexOptions().unique(true));
    }

    /**
     * Find phones by user ID
     * @param userId The user ID
     * @return List of phones
     */
    public List<Phone> findByUserId(String userId) {
        List<Phone> phones = new ArrayList<>();
        this.collection
                .find(Filters.eq("userId", userId))
                .into(phones);
        return phones;
    }
    
    /**
     * Find phone by ID
     * @param id The phone ID
     * @return Phone or null
     */
    public Phone findById(String id) {
        return this.collection
                .find(Filters.eq("_id", id))
                .first();
    }
    
    /**
     * Find phone by country and number
     * @param country The country code
     * @param number The phone number
     * @return Phone or null
     */
    public Phone findByCountryAndNumber(String country, String number) {
        return this.collection
                .find(Filters.and(
                        Filters.eq("country", country),
                        Filters.eq("number", number)
                ))
                .first();
    }
    
    /**
     * Check if phone exists for user
     * @param userId The user ID
     * @return true if exists
     */
    public boolean existsByUserId(String userId) {
        return this.collection
                .countDocuments(Filters.eq("userId", userId)) > 0;
    }
    
    /**
     * Save or update phone
     * @param phone The phone to save
     * @return Saved phone
     */
    public Phone save(Phone phone) {
        if (phone.getId() == null) {
            phone.setId(java.util.UUID.randomUUID().toString());
            phone.setCreatedAt(java.time.LocalDateTime.now());
        }
        phone.setUpdatedAt(java.time.LocalDateTime.now());
        
        this.collection.replaceOne(
                Filters.eq("_id", phone.getId()),
                phone,
                new com.mongodb.client.model.ReplaceOptions().upsert(true)
        );
        return phone;
    }
    
    /**
     * Delete phone by ID
     * @param id The phone ID
     */
    public void deleteById(String id) {
        this.collection.deleteOne(Filters.eq("_id", id));
    }
    
    /**
     * Delete phones by user ID
     * @param userId The user ID
     */
    public void deleteByUserId(String userId) {
        this.collection.deleteMany(Filters.eq("userId", userId));
    }
}
