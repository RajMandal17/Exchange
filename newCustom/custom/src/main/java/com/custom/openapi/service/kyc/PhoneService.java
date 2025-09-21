package com.custom.openapi.service.kyc;

import com.custom.marketdata.entity.Phone;
import com.custom.marketdata.repository.PhoneRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Random;
import java.util.regex.Pattern;

@Service
public class PhoneService {
    
    @Autowired
    private PhoneRepository phoneRepository;
    
    private static final Pattern PHONE_PATTERN = Pattern.compile("^[0-9]{7,15}$");
    private static final Pattern COUNTRY_PATTERN = Pattern.compile("^[A-Z]{2}$");
    
    // Simple in-memory verification code storage (in production, use Redis)
    private final java.util.Map<String, String> verificationCodes = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.Map<String, LocalDateTime> codeExpirations = new java.util.concurrent.ConcurrentHashMap<>();
    
    /**
     * Get user's phones
     * @param userId The user ID
     * @return List of phones
     */
    public List<Phone> getUserPhones(String userId) {
        return phoneRepository.findByUserId(userId);
    }
    
    /**
     * Add phone for user
     * @param userId The user ID
     * @param country The country code
     * @param number The phone number
     * @return Saved phone
     * @throws IllegalArgumentException if validation fails
     */
    public Phone addPhone(String userId, String country, String number) {
        validatePhone(country, number);
        
        // Check if phone already exists
        Phone existingPhone = phoneRepository.findByCountryAndNumber(country, number);
        if (existingPhone != null) {
            throw new IllegalArgumentException("Phone number already registered");
        }
        
        Phone phone = new Phone();
        phone.setUserId(userId);
        phone.setCountry(country);
        phone.setNumber(number);
        // Phone is not validated until verification code is confirmed
        
        return phoneRepository.save(phone);
    }
    
    /**
     * Send verification code to phone
     * @param phoneId The phone ID
     * @param userId The user ID (for security check)
     * @return Verification code (for demo purposes - in production, send via SMS)
     * @throws IllegalArgumentException if phone not found or not owned by user
     */
    public String sendVerificationCode(String phoneId, String userId) {
        Phone phone = phoneRepository.findById(phoneId);
        if (phone == null) {
            throw new IllegalArgumentException("Phone not found: " + phoneId);
        }
        if (!phone.getUserId().equals(userId)) {
            throw new IllegalArgumentException("Access denied: phone not owned by user");
        }
        
        // Generate 6-digit verification code
        String code = String.format("%06d", new Random().nextInt(999999));
        String key = phoneId + ":" + userId;
        
        verificationCodes.put(key, code);
        codeExpirations.put(key, LocalDateTime.now().plusMinutes(10)); // 10 minutes expiration
        
        // TODO: In production, send SMS via SMS provider
        // For demo, we'll return the code
        return code;
    }
    
    /**
     * Verify phone with code
     * @param phoneId The phone ID
     * @param userId The user ID (for security check)
     * @param code The verification code
     * @return Updated phone
     * @throws IllegalArgumentException if verification fails
     */
    public Phone verifyPhone(String phoneId, String userId, String code) {
        Phone phone = phoneRepository.findById(phoneId);
        if (phone == null) {
            throw new IllegalArgumentException("Phone not found: " + phoneId);
        }
        if (!phone.getUserId().equals(userId)) {
            throw new IllegalArgumentException("Access denied: phone not owned by user");
        }
        
        String key = phoneId + ":" + userId;
        String storedCode = verificationCodes.get(key);
        LocalDateTime expiration = codeExpirations.get(key);
        
        if (storedCode == null || expiration == null) {
            throw new IllegalArgumentException("No verification code found. Please request a new code.");
        }
        
        if (LocalDateTime.now().isAfter(expiration)) {
            verificationCodes.remove(key);
            codeExpirations.remove(key);
            throw new IllegalArgumentException("Verification code has expired. Please request a new code.");
        }
        
        if (!storedCode.equals(code)) {
            throw new IllegalArgumentException("Invalid verification code");
        }
        
        // Code is valid, mark phone as verified
        phone.setValidatedAt(LocalDateTime.now().toString());
        verificationCodes.remove(key);
        codeExpirations.remove(key);
        
        return phoneRepository.save(phone);
    }
    
    /**
     * Check if user has verified phone
     * @param userId The user ID
     * @return true if at least one phone is verified
     */
    public boolean hasVerifiedPhone(String userId) {
        List<Phone> phones = phoneRepository.findByUserId(userId);
        return phones.stream().anyMatch(phone -> phone.getValidatedAt() != null);
    }
    
    /**
     * Delete phone
     * @param phoneId The phone ID
     * @param userId The user ID (for security check)
     * @throws IllegalArgumentException if phone not found or not owned by user
     */
    public void deletePhone(String phoneId, String userId) {
        Phone phone = phoneRepository.findById(phoneId);
        if (phone == null) {
            throw new IllegalArgumentException("Phone not found: " + phoneId);
        }
        if (!phone.getUserId().equals(userId)) {
            throw new IllegalArgumentException("Access denied: phone not owned by user");
        }
        
        phoneRepository.deleteById(phoneId);
        
        // Clean up verification codes
        String key = phoneId + ":" + userId;
        verificationCodes.remove(key);
        codeExpirations.remove(key);
    }
    
    /**
     * Get primary phone for user (first verified phone)
     * @param userId The user ID
     * @return Primary phone or null
     */
    public Phone getPrimaryPhone(String userId) {
        List<Phone> phones = phoneRepository.findByUserId(userId);
        return phones.stream()
                .filter(phone -> phone.getValidatedAt() != null)
                .findFirst()
                .orElse(null);
    }
    
    /**
     * Validate phone data
     * @param country The country code
     * @param number The phone number
     * @throws IllegalArgumentException if validation fails
     */
    private void validatePhone(String country, String number) {
        if (country == null || country.trim().isEmpty()) {
            throw new IllegalArgumentException("Country code is required");
        }
        if (!COUNTRY_PATTERN.matcher(country.toUpperCase()).matches()) {
            throw new IllegalArgumentException("Invalid country code format. Must be 2 uppercase letters (e.g., US, UA)");
        }
        
        if (number == null || number.trim().isEmpty()) {
            throw new IllegalArgumentException("Phone number is required");
        }
        
        // Remove any non-digit characters for validation
        String cleanNumber = number.replaceAll("[^0-9]", "");
        if (!PHONE_PATTERN.matcher(cleanNumber).matches()) {
            throw new IllegalArgumentException("Invalid phone number format. Must be 7-15 digits");
        }
    }
}
