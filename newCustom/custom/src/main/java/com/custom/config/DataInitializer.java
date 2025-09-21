package com.custom.config;

import com.custom.marketdata.manager.UserManager;
import com.custom.marketdata.entity.User;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {
    
    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);
    private final UserManager userManager;
    
    @Override
    public void run(String... args) throws Exception {
        try {
            // Check if test user exists, create if not
            User testUser = userManager.getUser("test@test.com", "12345678");
            if (testUser == null) {
                log.info("Creating test user: test@test.com");
                userManager.createUser("test@test.com", "12345678");
                log.info("Test user created successfully");
            } else {
                log.info("Test user already exists: test@test.com");
            }
        } catch (Exception e) {
            if (e.getMessage().contains("duplicate email address")) {
                log.info("Test user already exists: test@test.com");
            } else {
                log.error("Error creating test user", e);
            }
        }
    }
}
