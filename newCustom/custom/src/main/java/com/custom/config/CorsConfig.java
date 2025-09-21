package com.custom.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import java.util.Arrays;

@Configuration
public class CorsConfig {
    
    @Bean
    public CorsFilter corsFilter() {
        CorsConfiguration config = new CorsConfiguration();
        
        // Allow specific origins (Baseapp frontend)
        config.setAllowedOriginPatterns(Arrays.asList(
                "http://localhost:3000",
                "http://localhost:3001", 
                "http://localhost:3002",
                "http://localhost:3003",
                "http://localhost:4000",
                "http://localhost:8080",
                "http://localhost:8081",
                "http://127.0.0.1:3000",
                "http://127.0.0.1:3001",
                "http://127.0.0.1:3002", 
                "http://127.0.0.1:3003",
                "http://127.0.0.1:4000",
                "http://127.0.0.1:8080",
                "http://127.0.0.1:8081"
        ));
        
        // Allow specific methods
        config.setAllowedMethods(Arrays.asList(
                "GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"
        ));
        
        // Allow all headers
        config.setAllowedHeaders(Arrays.asList("*"));
        
        // Allow credentials (cookies, authorization headers)
        config.setAllowCredentials(true);
        
        // Expose headers that frontend needs
        config.setExposedHeaders(Arrays.asList(
                "Authorization", 
                "X-CSRF-Token",
                "Content-Type",
                "Access-Control-Allow-Origin",
                "Access-Control-Allow-Credentials"
        ));
        
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        
        return new CorsFilter(source);
    }
}
