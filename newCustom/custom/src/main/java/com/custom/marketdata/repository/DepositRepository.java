package com.custom.marketdata.repository;

import com.custom.marketdata.entity.DepositEntity;
import com.custom.openapi.model.PagedList;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Indexes;
import com.mongodb.client.model.Sorts;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class DepositRepository {
    private final MongoCollection<DepositEntity> collection;

    public DepositRepository(MongoDatabase database) {
        this.collection = database.getCollection(DepositEntity.class.getSimpleName().toLowerCase(), DepositEntity.class);
        this.collection.createIndex(Indexes.descending("userId", "currency", "createdAt"));
    }

    public DepositEntity findById(String id) {
        return this.collection
                .find(Filters.eq("_id", id))
                .first();
    }

    public PagedList<DepositEntity> findByUserId(String userId, String currency, String state, int pageIndex, int pageSize) {
        var filter = Filters.eq("userId", userId);

        if (currency != null) {
            filter = Filters.and(filter, Filters.eq("currency", currency));
        }
        if (state != null) {
            filter = Filters.and(filter, Filters.eq("state", state));
        }

        long count = this.collection.countDocuments(filter);
        List<DepositEntity> deposits = this.collection
                .find(filter)
                .sort(Sorts.descending("createdAt"))
                .skip((pageIndex - 1) * pageSize)
                .limit(pageSize)
                .into(new ArrayList<>());

        return new PagedList<>(deposits, count);
    }

    public void save(DepositEntity deposit) {
        this.collection.insertOne(deposit);
    }

    public void update(DepositEntity deposit) {
        this.collection.replaceOne(Filters.eq("_id", deposit.getId()), deposit);
    }
}
