package com.custom.matchingengine.snapshot;

import com.alibaba.fastjson.JSON;
import com.custom.enums.OrderStatus;
import com.custom.matchingengine.Account;
import com.custom.matchingengine.Order;
import com.custom.matchingengine.Product;
import com.mongodb.ClientSessionOptions;
import com.mongodb.client.ClientSession;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.*;
import lombok.extern.slf4j.Slf4j;
import org.bson.conversions.Bson;
import org.springframework.stereotype.Component;

import com.custom.marketdata.orderbook.L2OrderBook;
import org.redisson.api.RedissonClient;
import org.redisson.api.RBucket;
import org.redisson.client.codec.StringCodec;
import com.alibaba.fastjson.JSON;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;

@Slf4j
@Component
public class EngineSnapshotManager {
    private final MongoCollection<EngineState> engineStateCollection;
    private final MongoCollection<Account> accountCollection;
    private final MongoCollection<Order> orderCollection;
    private final MongoCollection<Product> productCollection;
    private final MongoClient mongoClient;

    private final RedissonClient redissonClient;

    public EngineSnapshotManager(MongoClient mongoClient, MongoDatabase database, RedissonClient redissonClient) {
        this.mongoClient = mongoClient;
        this.engineStateCollection = database.getCollection("snapshot_engine", EngineState.class);
        this.accountCollection = database.getCollection("snapshot_account", Account.class);
        this.orderCollection = database.getCollection("snapshot_order", Order.class);
        this.orderCollection.createIndex(Indexes.descending("product_id", "sequence"), new IndexOptions().unique(true));
        this.productCollection = database.getCollection("snapshot_product", Product.class);
        this.redissonClient = redissonClient;
    }

    /**
     * Get the local orderbook from Redis for a given productId.
     * @param productId the product id
     * @return L2OrderBook or null if not found
     */
    public L2OrderBook getLocalOrderBookFromRedis(String productId) {
        String redisKey = "orderbook:l2:" + productId;
        RBucket<String> bucket = redissonClient.getBucket(redisKey, StringCodec.INSTANCE);
        String json = bucket.get();
        if (json == null) {
            return null;
        }
        return JSON.parseObject(json, L2OrderBook.class);
    }

    public void runInSession(Consumer<ClientSession> consumer) {
        try (ClientSession session = mongoClient.startSession(ClientSessionOptions.builder().snapshot(true).build())) {
            consumer.accept(session);
        }
    }

    public List<Product> getProducts(ClientSession session) {
        return this.productCollection
                .find(session)
                .into(new ArrayList<>());
    }

    public List<Account> getAccounts(ClientSession session) {
        return this.accountCollection
                .find(session)
                .into(new ArrayList<>());
    }

    public List<Order> getOrders(ClientSession session, String productId) {
        return this.orderCollection
                .find(session, Filters.eq("productId", productId))
                .sort(Sorts.ascending("sequence"))
                .into(new ArrayList<>());
    }

    public EngineState getEngineState(ClientSession session) {
        return engineStateCollection
                .find(session, Filters.eq("_id", "default"))
                .first();
    }

    public void save(EngineState engineState,
                     Collection<Account> accounts,
                     Collection<Order> orders,
                     Collection<Product> products) {
        logger.info("saving snapshot: state={}, {} account(s), {} order(s), {} products",
                JSON.toJSONString(engineState), accounts.size(), orders.size(), products.size());

        List<WriteModel<Account>> accountWriteModels = buildAccountWriteModels(accounts);
        List<WriteModel<Product>> productWriteModels = buildProductWriteModels(products);
        List<WriteModel<Order>> orderWriteModels = buildOrderWriteModels(orders);
        try (ClientSession session = mongoClient.startSession()) {
            session.startTransaction();
            try {
                engineStateCollection.replaceOne(session, Filters.eq("_id", engineState.getId()), engineState,
                        new ReplaceOptions().upsert(true));

                if (!accountWriteModels.isEmpty()) {
                    accountCollection.bulkWrite(session, accountWriteModels, new BulkWriteOptions().ordered(false));
                }

                if (!productWriteModels.isEmpty()) {
                    productCollection.bulkWrite(session, productWriteModels, new BulkWriteOptions().ordered(false));
                }

                if (!orderWriteModels.isEmpty()) {
                    orderCollection.bulkWrite(session, orderWriteModels, new BulkWriteOptions().ordered(false));
                }

                session.commitTransaction();
            } catch (Exception e) {
                session.abortTransaction();
                throw new RuntimeException(e);
            }
        }
    }

    private List<WriteModel<Product>> buildProductWriteModels(Collection<Product> products) {
        List<WriteModel<Product>> writeModels = new ArrayList<>();
        if (products.isEmpty()) {
            return writeModels;
        }
        for (Product item : products) {
            Bson filter = Filters.eq("_id", item.getId());
            WriteModel<Product> writeModel = new ReplaceOneModel<>(filter, item, new ReplaceOptions().upsert(true));
            writeModels.add(writeModel);
        }
        return writeModels;
    }

    private List<WriteModel<Order>> buildOrderWriteModels(Collection<Order> orders) {
        List<WriteModel<Order>> writeModels = new ArrayList<>();
        if (orders.isEmpty()) {
            return writeModels;
        }
        for (Order item : orders) {
            Bson filter = Filters.eq("_id", item.getId());
            WriteModel<Order> writeModel;
            if (item.getStatus() == OrderStatus.OPEN) {
                writeModel = new ReplaceOneModel<>(filter, item, new ReplaceOptions().upsert(true));
            } else {
                writeModel = new DeleteOneModel<>(filter);
            }
            writeModels.add(writeModel);
        }
        return writeModels;
    }

    private List<WriteModel<Account>> buildAccountWriteModels(Collection<Account> accounts) {
        List<WriteModel<Account>> writeModels = new ArrayList<>();
        if (accounts.isEmpty()) {
            return writeModels;
        }
        for (Account item : accounts) {
            Bson filter = Filters.eq("_id", item.getId());
            WriteModel<Account> writeModel = new ReplaceOneModel<>(filter, item, new ReplaceOptions().upsert(true));
            writeModels.add(writeModel);
        }
        return writeModels;
    }

}
