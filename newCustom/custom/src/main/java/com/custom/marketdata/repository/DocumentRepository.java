package com.custom.marketdata.repository;

import com.custom.marketdata.entity.Document;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Indexes;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class DocumentRepository {
    private final MongoCollection<Document> collection;

    public DocumentRepository(MongoDatabase database) {
        this.collection = database.getCollection(Document.class.getSimpleName().toLowerCase(), Document.class);
        this.collection.createIndex(Indexes.descending("userId"));
    }

    /**
     * Find documents by user ID
     * @param userId The user ID
     * @return List of documents
     */
    public List<Document> findByUserId(String userId) {
        List<Document> documents = new ArrayList<>();
        this.collection
                .find(Filters.eq("userId", userId))
                .into(documents);
        return documents;
    }
    
    /**
     * Find document by ID
     * @param id The document ID
     * @return Document or null
     */
    public Document findById(String id) {
        return this.collection
                .find(Filters.eq("_id", id))
                .first();
    }
    
    /**
     * Find all documents by state
     * @param state The document state (pending, verified, rejected)
     * @return List of documents
     */
    public List<Document> findByState(String state) {
        List<Document> documents = new ArrayList<>();
        this.collection
                .find(Filters.eq("state", state))
                .into(documents);
        return documents;
    }
    
    /**
     * Find documents by user ID and document type
     * @param userId The user ID
     * @param docType The document type
     * @return List of documents
     */
    public List<Document> findByUserIdAndDocType(String userId, String docType) {
        List<Document> documents = new ArrayList<>();
        this.collection
                .find(Filters.and(
                        Filters.eq("userId", userId),
                        Filters.eq("docType", docType)
                ))
                .into(documents);
        return documents;
    }
    
    /**
     * Save or update document
     * @param document The document to save
     * @return Saved document
     */
    public Document save(Document document) {
        if (document.getId() == null) {
            document.setId(java.util.UUID.randomUUID().toString());
            document.setCreatedAt(java.time.LocalDateTime.now());
        }
        document.setUpdatedAt(java.time.LocalDateTime.now());
        
        this.collection.replaceOne(
                Filters.eq("_id", document.getId()),
                document,
                new com.mongodb.client.model.ReplaceOptions().upsert(true)
        );
        return document;
    }
    
    /**
     * Delete document by ID
     * @param id The document ID
     */
    public void deleteById(String id) {
        this.collection.deleteOne(Filters.eq("_id", id));
    }
}
