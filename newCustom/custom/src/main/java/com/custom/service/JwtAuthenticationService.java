package com.custom.service;

import com.custom.marketdata.entity.User;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.security.Key;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@Service
@Slf4j
public class JwtAuthenticationService {

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
     * Validate JWT token and extract user information
     */
    public User validateTokenAndGetUser(String token) {
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

            // For now, create a mock user since we don't have full user repository implementation
            // In production, this should fetch from database
            User user = new User();
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
     * Generate JWT token for a user (for testing purposes)
     */
    public String generateToken(User user) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + jwtExpiration);

        Map<String, Object> claims = new HashMap<>();
        claims.put("email", user.getEmail());
        claims.put("uid", user.getUid());
        claims.put("role", user.getRole());
        claims.put("state", user.getState());

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
