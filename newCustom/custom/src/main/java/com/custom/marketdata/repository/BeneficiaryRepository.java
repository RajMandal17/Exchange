package com.custom.marketdata.repository;

import com.custom.marketdata.entity.BeneficiaryEntity;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.*;
import org.bson.conversions.Bson;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class BeneficiaryRepository {
    private final MongoCollection<BeneficiaryEntity> collection;

    public BeneficiaryRepository(MongoDatabase database) {
        this.collection = database.getCollection(BeneficiaryEntity.class.getSimpleName().toLowerCase(), BeneficiaryEntity.class);
        this.collection.createIndex(Indexes.descending("userId"), new IndexOptions());
    }

    public List<BeneficiaryEntity> findByUserId(String userId) {
        return collection
                .find(Filters.eq("userId", userId))
                .into(new ArrayList<>());
    }

    public BeneficiaryEntity findById(Long id) {
        return collection.find(Filters.eq("id", id)).first();
    }

    public void save(BeneficiaryEntity beneficiary) {
        Bson filter = Filters.eq("id", beneficiary.getId());
        collection.replaceOne(filter, beneficiary, new ReplaceOptions().upsert(true));
    }

    public void delete(Long id) {
        collection.deleteOne(Filters.eq("id", id));
    }
}