package com.custom.barong.controller;

import com.custom.barong.dto.*;
import com.custom.barong.service.ApiKeyService;
import com.custom.marketdata.entity.User;
import com.custom.marketdata.entity.ApiKey;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import java.util.List;

@RestController
@RequestMapping("/v2/barong/resource")
@CrossOrigin(origins = {"http://localhost:3000", "http://localhost:3001"})
public class BarongUserResourceController {

    @Autowired
    private ApiKeyService apiKeyService;

    /**
     * Get current user's profile
     */
    @GetMapping("/users/me")
    public ResponseEntity<?> getCurrentUser(HttpServletRequest request) {
        try {
            HttpSession session = request.getSession(false);
            if (session == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ErrorResponse("unauthorized", "Session required"));
            }

            User user = (User) session.getAttribute("user");
            if (user == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ErrorResponse("unauthorized", "Invalid session"));
            }

            // User is already fresh from session, no need to re-query
            UserResponse userResponse = new UserResponse(user);
            return ResponseEntity.ok(userResponse);

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse("internal_error", "Failed to get user profile"));
        }
    }

    /**
     * Update current user's profile
     */
    @PutMapping("/users/me")
    public ResponseEntity<?> updateCurrentUser(@RequestBody UpdateUserRequest updateRequest, 
                                               HttpServletRequest request) {
        try {
            HttpSession session = request.getSession(false);
            if (session == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ErrorResponse("unauthorized", "Session required"));
            }

            User user = (User) session.getAttribute("user");
            if (user == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ErrorResponse("unauthorized", "Invalid session"));
            }

            // Update user profile - using available fields
            if (updateRequest.getFirstName() != null) {
                user.setNickName(updateRequest.getFirstName()); // Use nickName as firstName
            }
            if (updateRequest.getLanguage() != null) {
                user.setLanguage(updateRequest.getLanguage());
            }
            // Note: lastName not available in current User entity

            // Update session with modified user
            session.setAttribute("user", user);

            UserResponse userResponse = new UserResponse(user);
            return ResponseEntity.ok(userResponse);

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse("internal_error", "Failed to update user profile"));
        }
    }

    /**
     * Get all API keys for current user
     */
    @GetMapping("/api_keys")
    public ResponseEntity<?> getApiKeys(HttpServletRequest request) {
        try {
            HttpSession session = request.getSession(false);
            if (session == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ErrorResponse("unauthorized", "Session required"));
            }

            User user = (User) session.getAttribute("user");
            if (user == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ErrorResponse("unauthorized", "Invalid session"));
            }

            List<ApiKeyResponse> apiKeys = apiKeyService.getUserApiKeys(user.getId());
            return ResponseEntity.ok(apiKeys);

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse("internal_error", "Failed to get API keys"));
        }
    }

    /**
     * Create new API key
     */
    @PostMapping("/api_keys")
    public ResponseEntity<?> createApiKey(@RequestBody CreateApiKeyRequest createRequest,
                                          HttpServletRequest request) {
        try {
            HttpSession session = request.getSession(false);
            if (session == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ErrorResponse("unauthorized", "Session required"));
            }

            User user = (User) session.getAttribute("user");
            if (user == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ErrorResponse("unauthorized", "Invalid session"));
            }

            // Check if user has reached maximum number of API keys (e.g., 5)
            List<ApiKeyResponse> existingKeys = apiKeyService.getUserApiKeys(user.getId());
            if (existingKeys.size() >= 5) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ErrorResponse("limit_exceeded", "Maximum number of API keys reached"));
            }

            ApiKey apiKey = apiKeyService.createApiKey(user.getId(), createRequest);
            ApiKeyResponse response = new ApiKeyResponse(apiKey, true); // Include secret for new keys
            return ResponseEntity.status(HttpStatus.CREATED).body(response);

        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse("invalid_request", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse("internal_error", "Failed to create API key"));
        }
    }

    /**
     * Update API key
     */
    @PatchMapping("/api_keys/{id}")
    public ResponseEntity<?> updateApiKey(@PathVariable String id,
                                          @RequestBody UpdateApiKeyRequest updateRequest,
                                          HttpServletRequest request) {
        try {
            HttpSession session = request.getSession(false);
            if (session == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ErrorResponse("unauthorized", "Session required"));
            }

            User user = (User) session.getAttribute("user");
            if (user == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ErrorResponse("unauthorized", "Invalid session"));
            }

            ApiKey apiKey = apiKeyService.updateApiKey(id, user.getId(), updateRequest);
            if (apiKey == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ErrorResponse("not_found", "API key not found"));
            }

            ApiKeyResponse response = new ApiKeyResponse(apiKey);
            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse("invalid_request", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse("internal_error", "Failed to update API key"));
        }
    }

    /**
     * Delete API key
     */
    @DeleteMapping("/api_keys/{id}")
    public ResponseEntity<?> deleteApiKey(@PathVariable String id, HttpServletRequest request) {
        try {
            HttpSession session = request.getSession(false);
            if (session == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ErrorResponse("unauthorized", "Session required"));
            }

            User user = (User) session.getAttribute("user");
            if (user == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ErrorResponse("unauthorized", "Invalid session"));
            }

            boolean deleted = apiKeyService.deleteApiKey(id, user.getId());
            if (!deleted) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ErrorResponse("not_found", "API key not found"));
            }

            return ResponseEntity.noContent().build();

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse("internal_error", "Failed to delete API key"));
        }
    }

    /**
     * Get API key by ID
     */
    @GetMapping("/api_keys/{id}")
    public ResponseEntity<?> getApiKey(@PathVariable String id, HttpServletRequest request) {
        try {
            HttpSession session = request.getSession(false);
            if (session == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ErrorResponse("unauthorized", "Session required"));
            }

            User user = (User) session.getAttribute("user");
            if (user == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ErrorResponse("unauthorized", "Invalid session"));
            }

            ApiKey apiKey = apiKeyService.getApiKey(id, user.getId());
            if (apiKey == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ErrorResponse("not_found", "API key not found"));
            }

            ApiKeyResponse response = new ApiKeyResponse(apiKey);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse("internal_error", "Failed to get API key"));
        }
    }
}
