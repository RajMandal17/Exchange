package com.custom.openapi.controller.barong;

import com.custom.openapi.model.barong.*;
import com.custom.openapi.service.barong.BarongAuthenticationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.validation.Valid;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/v2/barong/identity")
@RequiredArgsConstructor
public class AuthenticationController {
    
    private final BarongAuthenticationService barongAuthenticationService;
    
    /**
     * POST /v2/barong/identity/sessions - Sign in user
     */
    @PostMapping("/sessions")
    public ResponseEntity<AuthenticationResponse> signIn(
            @Valid @RequestBody SignInRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {
        
        try {
            AuthenticationResponse response = barongAuthenticationService.authenticateUser(request, httpRequest, httpResponse);
            
            // Set CSRF token header for compatibility
            if (response.getCsrfToken() != null) {
                httpResponse.setHeader("X-CSRF-Token", response.getCsrfToken());
            }
            
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    /**
     * DELETE /v2/barong/identity/sessions - Sign out user
     */
    @DeleteMapping("/sessions")
    public ResponseEntity<Void> signOut(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestHeader(value = "X-CSRF-Token", required = false) String csrfToken,
            HttpServletRequest request) {
        
        try {
            // Extract token from Authorization header (Bearer token)
            String accessToken = null;
            if (authorization != null && authorization.startsWith("Bearer ")) {
                accessToken = authorization.substring(7);
            }
            
            barongAuthenticationService.signOutUser(accessToken);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    /**
     * POST /v2/barong/identity/users - Sign up new user
     */
    @PostMapping("/users")
    public ResponseEntity<SignUpResponse> signUp(
            @Valid @RequestBody SignUpRequest request) {
        
        try {
            SignUpResponse response = barongAuthenticationService.registerUser(request);
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).build();
        } catch (RuntimeException e) {
            // Handle duplicate email or other business logic errors
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).build();
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    /**
     * POST /v2/barong/identity/users/email/confirm_code - Confirm email
     */
    @PostMapping("/users/email/confirm_code")
    public ResponseEntity<Void> confirmEmail(
            @Valid @RequestBody EmailConfirmationRequest request) {
        
        // TODO: Implement email confirmation logic
        // 1. Validate confirmation token
        // 2. Update user state to active
        // 3. Add email label
        
        return ResponseEntity.ok().build();
    }
    
    /**
     * POST /v2/barong/identity/users/password/reset - Reset password
     */
    @PostMapping("/users/password/reset")
    public ResponseEntity<Void> resetPassword(
            @Valid @RequestBody PasswordResetRequest request) {
        
        // TODO: Implement password reset
        // 1. Generate reset token
        // 2. Send reset email
        
        return ResponseEntity.ok().build();
    }
    
    /**
     * POST /v2/barong/identity/users/password/confirm - Confirm password reset
     */
    @PostMapping("/users/password/confirm")
    public ResponseEntity<Void> confirmPasswordReset(
            @Valid @RequestBody PasswordResetConfirmRequest request) {
        
        // TODO: Implement password reset confirmation
        // 1. Validate reset token
        // 2. Update user password
        
        return ResponseEntity.ok().build();
    }

    /**
     * POST /v2/barong/identity/password/validate - Validate password strength
     */
    @PostMapping("/password/validate")
    public ResponseEntity<Map<String, Object>> validatePassword(
            @RequestBody Map<String, String> request) {
        
        String password = request.get("password");
        if (password == null || password.isEmpty()) {
            Map<String, Object> response = new HashMap<>();
            response.put("score", 0);
            response.put("feedback", "Password cannot be empty");
            return ResponseEntity.ok(response);
        }
        
        // Simple password validation - can be enhanced with more complex rules
        int score = calculatePasswordScore(password);
        String feedback = getPasswordFeedback(score);
        
        Map<String, Object> response = new HashMap<>();
        response.put("score", score);
        response.put("feedback", feedback);
        
        return ResponseEntity.ok(response);
    }
    
    private int calculatePasswordScore(String password) {
        int score = 0;
        if (password.length() >= 8) score += 25;
        if (password.matches(".*[a-z].*")) score += 25;
        if (password.matches(".*[A-Z].*")) score += 25;
        if (password.matches(".*[0-9].*")) score += 15;
        if (password.matches(".*[!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>\\/?].*")) score += 10;
        return Math.min(score, 100);
    }
    
    private String getPasswordFeedback(int score) {
        if (score < 25) return "Very weak password";
        if (score < 50) return "Weak password";
        if (score < 75) return "Fair password";
        if (score < 90) return "Good password";
        return "Strong password";
    }
}
