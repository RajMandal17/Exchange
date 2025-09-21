package com.custom.openapi.service.barong;

import com.custom.marketdata.entity.User;
import com.custom.marketdata.manager.UserManager;
import com.custom.openapi.model.barong.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.UUID;

/**
 * Barong Authentication Service - adapts existing UserManager for Barong API compatibility
 */
@Service
@RequiredArgsConstructor
public class BarongAuthenticationService {
    
    private final UserManager userManager;
    
    /**
     * Authenticate user for Barong API - reuses existing UserManager logic
     */
    public AuthenticationResponse authenticateUser(SignInRequest request, HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        // Use existing authentication logic
        User user = userManager.getUser(request.getEmail(), request.getPassword());
        if (user == null) {
            throw new IllegalArgumentException("Invalid credentials");
        }
        
        // TODO: Add 2FA check if otpCode provided
        if (request.getOtpCode() != null && user.getOtpEnabled()) {
            // Validate OTP code here
        }
        
        // Generate access token using existing logic and store in session
        String accessToken = userManager.generateAccessToken(user, httpRequest.getSession().getId());
        
        // Set access token cookie for frontend authentication
        addAccessTokenCookie(httpResponse, accessToken);
        
        // Store user in session for resource endpoints
        httpRequest.getSession().setAttribute("user", user);
        
        // Generate CSRF token
        String csrfToken = UUID.randomUUID().toString();
        // TODO: Store CSRF token in session or Redis for validation
        
        // Convert to Barong response format
        return convertToAuthenticationResponse(user, csrfToken);
    }
    
    /**
     * Register new user - reuses existing UserManager logic
     */
    public SignUpResponse registerUser(SignUpRequest request) {
        // Validate password confirmation only if provided (frontend handles this validation)
        if (request.getPasswordConfirmation() != null && 
            !request.getPassword().equals(request.getPasswordConfirmation())) {
            throw new IllegalArgumentException("Password confirmation does not match");
        }
        
        // Use existing user creation logic
        User user = userManager.createUser(request.getEmail(), request.getPassword());
        
        // Set additional Barong fields
        user.setState("pending"); // Set to pending until email verification
        
        // Use refid if referralUid is not provided (frontend compatibility)
        String referralId = request.getReferralUid() != null ? 
                           request.getReferralUid() : request.getRefid();
        user.setReferralUid(referralId);
        
        // TODO: Send verification email
        
        return convertToSignUpResponse(user);
    }
    
    /**
     * Sign out user - reuses existing logic
     */
    public void signOutUser(String accessToken) {
        if (accessToken != null) {
            userManager.deleteAccessToken(accessToken);
        }
    }
    
    /**
     * Get current user info
     */
    public AuthenticationResponse getCurrentUser(String accessToken) {
        User user = userManager.getUserByAccessToken(accessToken);
        if (user == null) {
            throw new IllegalArgumentException("Invalid or expired token");
        }
        
        return convertToAuthenticationResponse(user, null);
    }
    
    private AuthenticationResponse convertToAuthenticationResponse(User user, String csrfToken) {
        AuthenticationResponse response = new AuthenticationResponse();
        response.setEmail(user.getEmail());
        response.setUid(user.getUid());
        response.setRole(user.getRole());
        response.setLevel(user.getLevel());
        response.setOtp(user.getOtpEnabled());
        response.setState(user.getState());
        response.setReferralUid(user.getReferralUid());
        response.setData(user.getData() != null ? user.getData() : "{\"language\":\"" + user.getLanguage() + "\"}");
        response.setCreatedAt(user.getCreatedAtLocal());
        response.setUpdatedAt(user.getUpdatedAtLocal());
        response.setCsrfToken(csrfToken);
        
        // TODO: Load and set labels, phones, profiles
        // For now, return empty lists to match API spec
        response.setLabels(java.util.Collections.emptyList());
        response.setPhones(java.util.Collections.emptyList());
        response.setProfiles(java.util.Collections.emptyList());
        
        return response;
    }
    
    private SignUpResponse convertToSignUpResponse(User user) {
        SignUpResponse response = new SignUpResponse();
        response.setEmail(user.getEmail());
        response.setUid(user.getUid());
        response.setState(user.getState());
        response.setCreatedAt(user.getCreatedAtLocal());
        return response;
    }
    
    /**
     * Add access token cookie for session-based authentication
     */
    private void addAccessTokenCookie(HttpServletResponse response, String accessToken) {
        Cookie cookie = new Cookie("accessToken", accessToken);
        cookie.setPath("/");
        cookie.setMaxAge(7 * 24 * 60 * 60); // 7 days
        cookie.setSecure(false); // Set to true in production with HTTPS
        cookie.setHttpOnly(false); // Frontend needs to read this
        response.addCookie(cookie);
    }
}
