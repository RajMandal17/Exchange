package com.custom.openapi.controller.kyc;

import com.custom.marketdata.entity.Profile;
import com.custom.marketdata.entity.Document;
import com.custom.openapi.service.kyc.ProfileService;
import com.custom.openapi.service.kyc.DocumentService;
import com.custom.openapi.model.kyc.AdminReviewRequest;
import com.custom.openapi.model.kyc.ProfileResponse;
import com.custom.openapi.model.kyc.DocumentResponse;
import com.custom.barong.dto.ErrorResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/v2/admin/kyc")
public class AdminKycController {
    
    @Autowired
    private ProfileService profileService;
    
    @Autowired
    private DocumentService documentService;
    
    /**
     * Get profiles pending review
     * GET /v2/admin/kyc/profiles/pending
     */
    @GetMapping("/profiles/pending")
    public ResponseEntity<?> getPendingProfiles() {
        try {
            List<Profile> profiles = profileService.getProfilesByState("submitted");
            List<ProfileResponse> responses = profiles.stream()
                    .map(this::toProfileResponse)
                    .collect(Collectors.toList());
            
            return ResponseEntity.ok(responses);
            
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("internal_error", "Failed to get pending profiles"));
        }
    }
    
    /**
     * Review profile
     * POST /v2/admin/kyc/profiles/{userId}/review
     */
    @PostMapping("/profiles/{userId}/review")
    public ResponseEntity<?> reviewProfile(@PathVariable String userId, 
                                          @RequestBody AdminReviewRequest request) {
        try {
            if (!"verified".equals(request.getState()) && !"rejected".equals(request.getState())) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(new ErrorResponse("invalid_request", "State must be 'verified' or 'rejected'"));
            }
            
            Profile profile = profileService.updateProfileState(userId, request.getState());
            
            return ResponseEntity.ok(toProfileResponse(profile));
            
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ErrorResponse("invalid_request", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("internal_error", "Failed to review profile"));
        }
    }
    
    /**
     * Get documents pending review
     * GET /v2/admin/kyc/documents/pending
     */
    @GetMapping("/documents/pending")
    public ResponseEntity<?> getPendingDocuments() {
        try {
            List<Document> documents = documentService.getDocumentsByState("pending");
            List<DocumentResponse> responses = documents.stream()
                    .map(this::toDocumentResponse)
                    .collect(Collectors.toList());
            
            return ResponseEntity.ok(responses);
            
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("internal_error", "Failed to get pending documents"));
        }
    }
    
    /**
     * Review document
     * POST /v2/admin/kyc/documents/{documentId}/review
     */
    @PostMapping("/documents/{documentId}/review")
    public ResponseEntity<?> reviewDocument(@PathVariable String documentId, 
                                           @RequestBody AdminReviewRequest request) {
        try {
            if (!"verified".equals(request.getState()) && !"rejected".equals(request.getState())) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(new ErrorResponse("invalid_request", "State must be 'verified' or 'rejected'"));
            }
            
            if ("rejected".equals(request.getState()) && 
                (request.getRejectedReason() == null || request.getRejectedReason().trim().isEmpty())) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(new ErrorResponse("invalid_request", "Rejected reason is required when rejecting"));
            }
            
            // TODO: Get admin user ID from authentication context
            String reviewedBy = "admin"; // Placeholder
            
            Document document = documentService.reviewDocument(
                documentId, 
                request.getState(), 
                request.getRejectedReason(),
                reviewedBy
            );
            
            return ResponseEntity.ok(toDocumentResponse(document));
            
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ErrorResponse("invalid_request", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("internal_error", "Failed to review document"));
        }
    }
    
    /**
     * Get all profiles by state
     * GET /v2/admin/kyc/profiles?state={state}
     */
    @GetMapping("/profiles")
    public ResponseEntity<?> getProfilesByState(@RequestParam(defaultValue = "submitted") String state) {
        try {
            List<Profile> profiles = profileService.getProfilesByState(state);
            List<ProfileResponse> responses = profiles.stream()
                    .map(this::toProfileResponse)
                    .collect(Collectors.toList());
            
            return ResponseEntity.ok(responses);
            
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("internal_error", "Failed to get profiles"));
        }
    }
    
    /**
     * Get all documents by state
     * GET /v2/admin/kyc/documents?state={state}
     */
    @GetMapping("/documents")
    public ResponseEntity<?> getDocumentsByState(@RequestParam(defaultValue = "pending") String state) {
        try {
            List<Document> documents = documentService.getDocumentsByState(state);
            List<DocumentResponse> responses = documents.stream()
                    .map(this::toDocumentResponse)
                    .collect(Collectors.toList());
            
            return ResponseEntity.ok(responses);
            
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("internal_error", "Failed to get documents"));
        }
    }
    
    // Helper methods to convert entities to DTOs
    
    private ProfileResponse toProfileResponse(Profile profile) {
        ProfileResponse response = new ProfileResponse();
        response.setId(profile.getId());
        response.setFirstName(profile.getFirstName());
        response.setLastName(profile.getLastName());
        response.setDob(profile.getDob());
        response.setAddress(profile.getAddress());
        response.setPostcode(profile.getPostcode());
        response.setCity(profile.getCity());
        response.setCountry(profile.getCountry());
        response.setState(profile.getState());
        response.setCreatedAt(profile.getCreatedAt() != null ? profile.getCreatedAt().toString() : null);
        response.setUpdatedAt(profile.getUpdatedAt() != null ? profile.getUpdatedAt().toString() : null);
        return response;
    }
    
    private DocumentResponse toDocumentResponse(Document document) {
        DocumentResponse response = new DocumentResponse();
        response.setId(document.getId());
        response.setDocType(document.getDocType());
        response.setDocNumber(document.getDocNumber());
        response.setFileName(document.getFileName());
        response.setState(document.getState());
        response.setRejectedReason(document.getRejectedReason());
        response.setCreatedAt(document.getCreatedAt() != null ? document.getCreatedAt().toString() : null);
        response.setUpdatedAt(document.getUpdatedAt() != null ? document.getUpdatedAt().toString() : null);
        return response;
    }
}
