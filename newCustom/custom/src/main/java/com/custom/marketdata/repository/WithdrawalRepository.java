package com.custom.marketdata.repository;

import com.custom.marketdata.entity.WithdrawalEntity;
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
public class WithdrawalRepository {
    private final MongoCollection<WithdrawalEntity> collection;

    public WithdrawalRepository(MongoDatabase database) {
        this.collection = database.getCollection(WithdrawalEntity.class.getSimpleName().toLowerCase(), WithdrawalEntity.class);
        this.collection.createIndex(Indexes.descending("userId", "currency", "createdAt"));
    }

    public WithdrawalEntity findById(String id) {
        return this.collection
                .find(Filters.eq("_id", id))
                .first();
    }

    public PagedList<WithdrawalEntity> findByUserId(String userId, String currency, String state, int pageIndex, int pageSize) {
        var filter = Filters.eq("userId", userId);

        if (currency != null) {
            filter = Filters.and(filter, Filters.eq("currency", currency));
        }
        if (state != null) {
            filter = Filters.and(filter, Filters.eq("state", state));
        }

        long count = this.collection.countDocuments(filter);
        List<WithdrawalEntity> withdrawals = this.collection
                .find(filter)
                .sort(Sorts.descending("createdAt"))
                .skip((pageIndex - 1) * pageSize)
                .limit(pageSize)
                .into(new ArrayList<>());

        return new PagedList<>(withdrawals, count);
    }

    public void save(WithdrawalEntity withdrawal) {
        this.collection.insertOne(withdrawal);
    }

    public void update(WithdrawalEntity withdrawal) {
        this.collection.replaceOne(Filters.eq("_id", withdrawal.getId()), withdrawal);
    }
}
