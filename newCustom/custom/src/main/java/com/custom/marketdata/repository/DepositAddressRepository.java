package com.custom.marketdata.repository;

import com.custom.marketdata.entity.DepositAddressEntity;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Indexes;
import org.springframework.stereotype.Component;

@Component
public class DepositAddressRepository {
    private final MongoCollection<DepositAddressEntity> collection;

    public DepositAddressRepository(MongoDatabase database) {
        this.collection = database.getCollection(DepositAddressEntity.class.getSimpleName().toLowerCase(), DepositAddressEntity.class);
        this.collection.createIndex(Indexes.compoundIndex(Indexes.ascending("userId", "currency")));
    }

    public DepositAddressEntity findByUserIdAndCurrency(String userId, String currency) {
        return this.collection
                .find(Filters.and(
                    Filters.eq("userId", userId),
                    Filters.eq("currency", currency)
                ))
                .first();
    }

    public void save(DepositAddressEntity depositAddress) {
        this.collection.insertOne(depositAddress);
    }

    public void update(DepositAddressEntity depositAddress) {
        this.collection.replaceOne(
            Filters.and(
                Filters.eq("userId", depositAddress.getUserId()),
                Filters.eq("currency", depositAddress.getCurrency())
            ),
            depositAddress
        );
    }
}
