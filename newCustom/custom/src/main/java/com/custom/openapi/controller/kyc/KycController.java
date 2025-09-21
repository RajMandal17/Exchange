package com.custom.openapi.controller.kyc;

import com.custom.marketdata.entity.User;
import com.custom.marketdata.entity.Profile;
import com.custom.marketdata.entity.Document;
import com.custom.marketdata.entity.Phone;
import com.custom.openapi.service.kyc.ProfileService;
import com.custom.openapi.service.kyc.DocumentService;
import com.custom.openapi.service.kyc.PhoneService;
import com.custom.openapi.model.kyc.*;
import com.custom.barong.dto.ErrorResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpSession;
import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/v2/barong/resource/kyc")
public class KycController {
    
    @Autowired
    private ProfileService profileService;
    
    @Autowired
    private DocumentService documentService;
    
    @Autowired
    private PhoneService phoneService;
    
    /**
     * Get KYC status for current user
     * GET /v2/barong/resource/kyc/status
     */
    @GetMapping("/status")
    public ResponseEntity<?> getKycStatus(HttpSession session) {
        try {
            User user = (User) session.getAttribute("user");
            if (user == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(new ErrorResponse("unauthorized", "Authentication required"));
            }
            
            String userId = user.getUid();
            
            // Check verification status for each step
            boolean phoneVerified = phoneService.hasVerifiedPhone(userId);
            boolean profileSubmitted = profileService.getUserProfile(userId) != null;
            boolean profileVerified = profileService.hasVerifiedProfile(userId);
            boolean documentsSubmitted = !documentService.getUserDocuments(userId).isEmpty();
            boolean documentsVerified = documentService.hasVerifiedDocuments(userId);
            
            // Determine overall status
            String overallStatus = "incomplete";
            if (phoneVerified && profileVerified && documentsVerified) {
                overallStatus = "verified";
            } else if (phoneVerified || profileSubmitted || documentsSubmitted) {
                overallStatus = "pending";
            }
            
            KycStatusResponse response = new KycStatusResponse();
            response.setEmail(user.getEmail());
            response.setPhoneVerified(phoneVerified);
            response.setProfileSubmitted(profileSubmitted);
            response.setProfileVerified(profileVerified);
            response.setDocumentsSubmitted(documentsSubmitted);
            response.setDocumentsVerified(documentsVerified);
            response.setOverallStatus(overallStatus);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("internal_error", "Failed to get KYC status"));
        }
    }
    
    /**
     * Submit user profile
     * POST /v2/barong/resource/kyc/profile
     */
    @PostMapping("/profile")
    public ResponseEntity<?> submitProfile(@RequestBody ProfileSubmissionRequest request, HttpSession session) {
        try {
            User user = (User) session.getAttribute("user");
            if (user == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(new ErrorResponse("unauthorized", "Authentication required"));
            }
            
            Profile profile = new Profile();
            profile.setFirstName(request.getFirstName());
            profile.setLastName(request.getLastName());
            profile.setDob(request.getDob());
            profile.setAddress(request.getAddress());
            profile.setPostcode(request.getPostcode());
            profile.setCity(request.getCity());
            profile.setCountry(request.getCountry());
            
            Profile savedProfile = profileService.submitProfile(user.getUid(), profile);
            
            return ResponseEntity.status(HttpStatus.CREATED).body(toProfileResponse(savedProfile));
            
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ErrorResponse("invalid_request", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("internal_error", "Failed to submit profile"));
        }
    }
    
    /**
     * Get user profile
     * GET /v2/barong/resource/kyc/profile
     */
    @GetMapping("/profile")
    public ResponseEntity<?> getProfile(HttpSession session) {
        try {
            User user = (User) session.getAttribute("user");
            if (user == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(new ErrorResponse("unauthorized", "Authentication required"));
            }
            
            Profile profile = profileService.getUserProfile(user.getUid());
            if (profile == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(new ErrorResponse("not_found", "Profile not found"));
            }
            
            return ResponseEntity.ok(toProfileResponse(profile));
            
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("internal_error", "Failed to get profile"));
        }
    }
    
    /**
     * Upload document
     * POST /v2/barong/resource/kyc/documents
     */
    @PostMapping("/documents")
    public ResponseEntity<?> uploadDocument(
            @RequestParam("docType") String docType,
            @RequestParam("docNumber") String docNumber,
            @RequestParam("file") MultipartFile file,
            HttpSession session) {
        try {
            User user = (User) session.getAttribute("user");
            if (user == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(new ErrorResponse("unauthorized", "Authentication required"));
            }
            
            Document document = documentService.uploadDocument(user.getUid(), docType, docNumber, file);
            
            return ResponseEntity.status(HttpStatus.CREATED).body(toDocumentResponse(document));
            
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ErrorResponse("invalid_request", e.getMessage()));
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("upload_error", "Failed to upload document"));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("internal_error", "Failed to process document"));
        }
    }
    
    /**
     * Get user documents
     * GET /v2/barong/resource/kyc/documents
     */
    @GetMapping("/documents")
    public ResponseEntity<?> getDocuments(HttpSession session) {
        try {
            User user = (User) session.getAttribute("user");
            if (user == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(new ErrorResponse("unauthorized", "Authentication required"));
            }
            
            List<Document> documents = documentService.getUserDocuments(user.getUid());
            List<DocumentResponse> responses = documents.stream()
                    .map(this::toDocumentResponse)
                    .collect(Collectors.toList());
            
            return ResponseEntity.ok(responses);
            
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("internal_error", "Failed to get documents"));
        }
    }
    
    /**
     * Delete document
     * DELETE /v2/barong/resource/kyc/documents/{id}
     */
    @DeleteMapping("/documents/{id}")
    public ResponseEntity<?> deleteDocument(@PathVariable String id, HttpSession session) {
        try {
            User user = (User) session.getAttribute("user");
            if (user == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(new ErrorResponse("unauthorized", "Authentication required"));
            }
            
            documentService.deleteDocument(id, user.getUid());
            
            return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
            
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ErrorResponse("not_found", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("internal_error", "Failed to delete document"));
        }
    }
    
    /**
     * Add phone number
     * POST /v2/barong/resource/kyc/phones
     */
    @PostMapping("/phones")
    public ResponseEntity<?> addPhone(@RequestBody PhoneSubmissionRequest request, HttpSession session) {
        try {
            User user = (User) session.getAttribute("user");
            if (user == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(new ErrorResponse("unauthorized", "Authentication required"));
            }
            
            Phone phone = phoneService.addPhone(user.getUid(), request.getCountry(), request.getNumber());
            
            return ResponseEntity.status(HttpStatus.CREATED).body(toPhoneResponse(phone));
            
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ErrorResponse("invalid_request", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("internal_error", "Failed to add phone"));
        }
    }
    
    /**
     * Get user phones
     * GET /v2/barong/resource/kyc/phones
     */
    @GetMapping("/phones")
    public ResponseEntity<?> getPhones(HttpSession session) {
        try {
            User user = (User) session.getAttribute("user");
            if (user == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(new ErrorResponse("unauthorized", "Authentication required"));
            }
            
            List<Phone> phones = phoneService.getUserPhones(user.getUid());
            List<PhoneResponse> responses = phones.stream()
                    .map(this::toPhoneResponse)
                    .collect(Collectors.toList());
            
            return ResponseEntity.ok(responses);
            
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("internal_error", "Failed to get phones"));
        }
    }
    
    /**
     * Send verification code to phone
     * POST /v2/barong/resource/kyc/phones/{id}/send_code
     */
    @PostMapping("/phones/{id}/send_code")
    public ResponseEntity<?> sendVerificationCode(@PathVariable String id, HttpSession session) {
        try {
            User user = (User) session.getAttribute("user");
            if (user == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(new ErrorResponse("unauthorized", "Authentication required"));
            }
            
            String code = phoneService.sendVerificationCode(id, user.getUid());
            
            VerificationCodeResponse response = new VerificationCodeResponse();
            response.setMessage("Verification code sent successfully");
            response.setCode(code); // For demo purposes only
            
            return ResponseEntity.ok(response);
            
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ErrorResponse("invalid_request", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("internal_error", "Failed to send verification code"));
        }
    }
    
    /**
     * Verify phone with code
     * POST /v2/barong/resource/kyc/phones/{id}/verify
     */
    @PostMapping("/phones/{id}/verify")
    public ResponseEntity<?> verifyPhone(@PathVariable String id, 
                                        @RequestBody PhoneVerificationRequest request, 
                                        HttpSession session) {
        try {
            User user = (User) session.getAttribute("user");
            if (user == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(new ErrorResponse("unauthorized", "Authentication required"));
            }
            
            Phone phone = phoneService.verifyPhone(id, user.getUid(), request.getCode());
            
            return ResponseEntity.ok(toPhoneResponse(phone));
            
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ErrorResponse("invalid_request", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("internal_error", "Failed to verify phone"));
        }
    }
    
    /**
     * Delete phone
     * DELETE /v2/barong/resource/kyc/phones/{id}
     */
    @DeleteMapping("/phones/{id}")
    public ResponseEntity<?> deletePhone(@PathVariable String id, HttpSession session) {
        try {
            User user = (User) session.getAttribute("user");
            if (user == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(new ErrorResponse("unauthorized", "Authentication required"));
            }
            
            phoneService.deletePhone(id, user.getUid());
            
            return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
            
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ErrorResponse("not_found", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("internal_error", "Failed to delete phone"));
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
    
    private PhoneResponse toPhoneResponse(Phone phone) {
        PhoneResponse response = new PhoneResponse();
        response.setId(phone.getId());
        response.setCountry(phone.getCountry());
        response.setNumber(phone.getNumber());
        response.setValidatedAt(phone.getValidatedAt());
        response.setCreatedAt(phone.getCreatedAt() != null ? phone.getCreatedAt().toString() : null);
        response.setUpdatedAt(phone.getUpdatedAt() != null ? phone.getUpdatedAt().toString() : null);
        return response;
    }
}
