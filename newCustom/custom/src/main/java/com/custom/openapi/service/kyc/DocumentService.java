package com.custom.openapi.service.kyc;

import com.custom.marketdata.entity.Document;
import com.custom.marketdata.repository.DocumentRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Service
public class DocumentService {
    
    @Autowired
    private DocumentRepository documentRepository;
    
    private static final String UPLOAD_DIR = "uploads/documents/";
    private static final List<String> ALLOWED_EXTENSIONS = Arrays.asList("jpg", "jpeg", "png", "pdf");
    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024; // 10MB
    private static final List<String> VALID_DOC_TYPES = Arrays.asList("passport", "driver_license", "identity_card");
    
    /**
     * Get user's documents
     * @param userId The user ID
     * @return List of documents
     */
    public List<Document> getUserDocuments(String userId) {
        return documentRepository.findByUserId(userId);
    }
    
    /**
     * Upload document for user
     * @param userId The user ID
     * @param docType The document type
     * @param docNumber The document number
     * @param file The uploaded file
     * @return Saved document
     * @throws IllegalArgumentException if validation fails
     * @throws IOException if file upload fails
     */
    public Document uploadDocument(String userId, String docType, String docNumber, MultipartFile file) 
            throws IOException {
        validateDocumentUpload(docType, docNumber, file);
        
        String fileName = saveFile(file);
        
        Document document = new Document();
        document.setUserId(userId);
        document.setDocType(docType);
        document.setDocNumber(docNumber);
        document.setFileName(file.getOriginalFilename());
        document.setFilePath(fileName);
        document.setState("pending");
        
        return documentRepository.save(document);
    }
    
    /**
     * Get documents by state for admin review
     * @param state The document state
     * @return List of documents
     */
    public List<Document> getDocumentsByState(String state) {
        return documentRepository.findByState(state);
    }
    
    /**
     * Review document (admin action)
     * @param documentId The document ID
     * @param state The new state (verified, rejected)
     * @param rejectedReason The reason for rejection (if rejected)
     * @param reviewedBy The admin user ID
     * @return Updated document
     * @throws IllegalArgumentException if document not found or invalid state
     */
    public Document reviewDocument(String documentId, String state, String rejectedReason, String reviewedBy) {
        if (!isValidState(state)) {
            throw new IllegalArgumentException("Invalid state: " + state);
        }
        
        Document document = documentRepository.findById(documentId);
        if (document == null) {
            throw new IllegalArgumentException("Document not found: " + documentId);
        }
        
        document.setState(state);
        document.setReviewedBy(reviewedBy);
        if ("rejected".equals(state)) {
            document.setRejectedReason(rejectedReason);
        } else {
            document.setRejectedReason(null);
        }
        
        return documentRepository.save(document);
    }
    
    /**
     * Check if user has verified documents
     * @param userId The user ID
     * @return true if at least one document is verified
     */
    public boolean hasVerifiedDocuments(String userId) {
        List<Document> documents = documentRepository.findByUserId(userId);
        return documents.stream().anyMatch(doc -> "verified".equals(doc.getState()));
    }
    
    /**
     * Get document by ID
     * @param documentId The document ID
     * @return Document or null
     */
    public Document getDocument(String documentId) {
        return documentRepository.findById(documentId);
    }
    
    /**
     * Delete document
     * @param documentId The document ID
     * @param userId The user ID (for security check)
     * @throws IllegalArgumentException if document not found or not owned by user
     */
    public void deleteDocument(String documentId, String userId) {
        Document document = documentRepository.findById(documentId);
        if (document == null) {
            throw new IllegalArgumentException("Document not found: " + documentId);
        }
        if (!document.getUserId().equals(userId)) {
            throw new IllegalArgumentException("Access denied: document not owned by user");
        }
        
        // Delete file from filesystem
        try {
            Path filePath = Paths.get(UPLOAD_DIR, document.getFilePath());
            Files.deleteIfExists(filePath);
        } catch (IOException e) {
            // Log error but continue with database deletion
        }
        
        documentRepository.deleteById(documentId);
    }
    
    /**
     * Get file path for document
     * @param documentId The document ID
     * @param userId The user ID (for security check)
     * @return File path
     * @throws IllegalArgumentException if document not found or not owned by user
     */
    public String getDocumentFilePath(String documentId, String userId) {
        Document document = documentRepository.findById(documentId);
        if (document == null) {
            throw new IllegalArgumentException("Document not found: " + documentId);
        }
        if (!document.getUserId().equals(userId)) {
            throw new IllegalArgumentException("Access denied: document not owned by user");
        }
        
        return Paths.get(UPLOAD_DIR, document.getFilePath()).toString();
    }
    
    /**
     * Validate document upload
     * @param docType The document type
     * @param docNumber The document number
     * @param file The uploaded file
     * @throws IllegalArgumentException if validation fails
     */
    private void validateDocumentUpload(String docType, String docNumber, MultipartFile file) {
        if (!VALID_DOC_TYPES.contains(docType)) {
            throw new IllegalArgumentException("Invalid document type: " + docType + ". Valid types: " + VALID_DOC_TYPES);
        }
        
        if (docNumber == null || docNumber.trim().isEmpty()) {
            throw new IllegalArgumentException("Document number is required");
        }
        
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("File is required");
        }
        
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new IllegalArgumentException("File size exceeds maximum limit of 10MB");
        }
        
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null) {
            throw new IllegalArgumentException("Invalid file name");
        }
        
        String extension = getFileExtension(originalFilename).toLowerCase();
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new IllegalArgumentException("Invalid file type. Allowed types: " + ALLOWED_EXTENSIONS);
        }
    }
    
    /**
     * Save uploaded file to filesystem
     * @param file The uploaded file
     * @return Saved file name
     * @throws IOException if file save fails
     */
    private String saveFile(MultipartFile file) throws IOException {
        // Create upload directory if it doesn't exist
        Path uploadPath = Paths.get(UPLOAD_DIR);
        if (!Files.exists(uploadPath)) {
            Files.createDirectories(uploadPath);
        }
        
        // Generate unique file name
        String originalFilename = file.getOriginalFilename();
        String extension = getFileExtension(originalFilename);
        String fileName = UUID.randomUUID().toString() + "." + extension;
        
        Path filePath = uploadPath.resolve(fileName);
        Files.copy(file.getInputStream(), filePath);
        
        return fileName;
    }
    
    /**
     * Get file extension from filename
     * @param filename The filename
     * @return File extension
     */
    private String getFileExtension(String filename) {
        int lastDotIndex = filename.lastIndexOf('.');
        if (lastDotIndex == -1) {
            return "";
        }
        return filename.substring(lastDotIndex + 1);
    }
    
    /**
     * Check if state is valid
     * @param state The state to check
     * @return true if valid
     */
    private boolean isValidState(String state) {
        return "pending".equals(state) || "verified".equals(state) || "rejected".equals(state);
    }
}
