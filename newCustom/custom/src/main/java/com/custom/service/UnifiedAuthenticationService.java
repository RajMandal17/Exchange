package com.custom.service;

import com.custom.marketdata.entity.User;
import com.custom.marketdata.manager.UserManager;
import com.custom.marketdata.repository.UserRepository;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.servlet.http.HttpServletRequest;
import java.security.Key;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class UnifiedAuthenticationService {

    private final UserManager userManager;
    private final UserRepository userRepository;

    @Value("${jwt.secret:defaultSecretKeyForDevelopmentPurposesOnly}")
    private String jwtSecret;

    @Value("${jwt.expiration:86400000}") // 24 hours in milliseconds
    private long jwtExpiration;

    private Key signingKey;

    @PostConstruct
    public void init() {
        // Use a secure key for production
        if ("defaultSecretKeyForDevelopmentPurposesOnly".equals(jwtSecret)) {
            System.out.println("WARNING: Using default JWT secret. Please configure jwt.secret in production!");
        }
        this.signingKey = Keys.hmacShaKeyFor(jwtSecret.getBytes());
    }

    /**
     * Unified authentication method that supports both existing session tokens and new JWT tokens
     */
    public User authenticate(HttpServletRequest request) {
        System.out.println("DEBUG: UnifiedAuthenticationService.authenticate() called");
        
        // Try JWT Bearer token first (for new Peatio endpoints)
        String authHeader = request.getHeader("Authorization");
        System.out.println("DEBUG: Authorization header: " + authHeader);
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String jwtToken = authHeader.substring(7);
            System.out.println("DEBUG: Found JWT token, validating...");
            User user = validateJwtAndGetUser(jwtToken);
            if (user != null) {
                System.out.println("DEBUG: JWT authentication successful for user: " + user.getEmail());
                return user;
            }
            System.out.println("DEBUG: JWT validation failed");
        }

        // Fall back to existing session-based authentication
        String accessToken = getAccessTokenFromRequest(request);
        System.out.println("DEBUG: Access token from cookies/params: " + (accessToken != null ? "found" : "not found"));
        if (accessToken != null) {
            User user = userManager.getUserByAccessToken(accessToken);
            if (user != null) {
                System.out.println("DEBUG: Session authentication successful for user: " + user.getEmail());
                return user;
            }
            System.out.println("DEBUG: Session authentication failed - invalid token");
        }

        System.out.println("DEBUG: No valid authentication found");
        return null;
    }

    /**
     * Extract access token from request (existing logic from AuthInterceptor)
     * Made public so AuthInterceptor can use it for backward compatibility
     */
    public String getAccessTokenFromRequest(HttpServletRequest request) {
        String tokenKey = "accessToken";
        String token = request.getParameter(tokenKey);
        System.out.println("DEBUG: Token from parameter: " + token);
        
        if (token == null && request.getCookies() != null) {
            System.out.println("DEBUG: Checking cookies, found " + request.getCookies().length + " cookies");
            for (javax.servlet.http.Cookie cookie : request.getCookies()) {
                System.out.println("DEBUG: Cookie: " + cookie.getName() + " = " + cookie.getValue());
                if (cookie.getName().equals(tokenKey)) {
                    token = cookie.getValue();
                    System.out.println("DEBUG: Found accessToken cookie: " + token);
                }
            }
        } else if (request.getCookies() == null) {
            System.out.println("DEBUG: No cookies in request");
        }
        
        System.out.println("DEBUG: Final token: " + (token != null ? "found" : "null"));
        return token;
    }

    /**
     * Validate JWT token and extract user information
     */
    public User validateJwtAndGetUser(String token) {
        try {
            Claims claims = Jwts.parserBuilder()
                    .setSigningKey(signingKey)
                    .build()
                    .parseClaimsJws(token)
                    .getBody();

            String userId = claims.getSubject();
            if (userId == null) {
                System.out.println("WARNING: JWT token missing subject (userId)");
                return null;
            }

            // Try to get user from existing UserManager first
            User user = userManager.getUserByAccessToken(userId + ":dummy:dummy");
            if (user == null) {
                // Try direct repository lookup if access token method doesn't work
                user = userRepository.findByUserId(userId);
            }
            if (user != null) {
                return user;
            }

            // If not found in existing system, create a user object from JWT claims
            user = new User();
            user.setId(userId);
            user.setEmail(claims.get("email", String.class));
            user.setUid(claims.get("uid", String.class));
            user.setRole(claims.get("role", String.class));
            user.setState(claims.get("state", String.class));

            System.out.println("DEBUG: Successfully validated JWT token for user: " + userId);
            return user;

        } catch (ExpiredJwtException e) {
            System.out.println("WARNING: JWT token expired: " + e.getMessage());
            return null;
        } catch (UnsupportedJwtException e) {
            System.out.println("WARNING: Unsupported JWT token: " + e.getMessage());
            return null;
        } catch (MalformedJwtException e) {
            System.out.println("WARNING: Malformed JWT token: " + e.getMessage());
            return null;
        } catch (SecurityException e) {
            System.out.println("WARNING: Invalid JWT signature: " + e.getMessage());
            return null;
        } catch (Exception e) {
            System.out.println("ERROR: Error validating JWT token: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Generate JWT token for a user (for testing purposes and new API integrations)
     */
    public String generateJwtToken(User user) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + jwtExpiration);

        Map<String, Object> claims = new HashMap<>();
        claims.put("email", user.getEmail());
        claims.put("uid", user.getUid());
        claims.put("role", user.getRole() != null ? user.getRole() : "member");
        claims.put("state", user.getState() != null ? user.getState() : "active");

        return Jwts.builder()
                .setSubject(user.getId())
                .addClaims(claims)
                .setIssuedAt(now)
                .setExpiration(expiryDate)
                .signWith(signingKey)
                .compact();
    }

    /**
     * Extract token from Authorization header
     */
    public String extractTokenFromHeader(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return null;
        }
        return authHeader.substring(7); // Remove "Bearer " prefix
    }
}
