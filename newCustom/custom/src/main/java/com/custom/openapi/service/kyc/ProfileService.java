package com.custom.openapi.service.kyc;

import com.custom.marketdata.entity.Profile;
import com.custom.marketdata.repository.ProfileRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.regex.Pattern;

@Service
public class ProfileService {
    
    @Autowired
    private ProfileRepository profileRepository;
    
    private static final Pattern DATE_PATTERN = Pattern.compile("^\\d{4}-\\d{2}-\\d{2}$");
    
    /**
     * Get user's profile
     * @param userId The user ID
     * @return Profile or null
     */
    public Profile getUserProfile(String userId) {
        return profileRepository.findByUserId(userId);
    }
    
    /**
     * Submit or update user's profile
     * @param userId The user ID
     * @param profile The profile data
     * @return Saved profile
     * @throws IllegalArgumentException if validation fails
     */
    public Profile submitProfile(String userId, Profile profile) {
        validateProfile(profile);
        
        Profile existingProfile = profileRepository.findByUserId(userId);
        if (existingProfile != null) {
            // Update existing profile
            existingProfile.setFirstName(profile.getFirstName());
            existingProfile.setLastName(profile.getLastName());
            existingProfile.setDob(profile.getDob());
            existingProfile.setAddress(profile.getAddress());
            existingProfile.setPostcode(profile.getPostcode());
            existingProfile.setCity(profile.getCity());
            existingProfile.setCountry(profile.getCountry());
            existingProfile.setState("submitted"); // Reset to submitted state
            return profileRepository.save(existingProfile);
        } else {
            // Create new profile
            profile.setUserId(userId);
            profile.setState("submitted");
            return profileRepository.save(profile);
        }
    }
    
    /**
     * Get profiles by state for admin review
     * @param state The profile state
     * @return List of profiles
     */
    public List<Profile> getProfilesByState(String state) {
        return profileRepository.findByState(state);
    }
    
    /**
     * Update profile state (admin action)
     * @param userId The user ID
     * @param state The new state (verified, rejected)
     * @return Updated profile
     * @throws IllegalArgumentException if profile not found or invalid state
     */
    public Profile updateProfileState(String userId, String state) {
        if (!isValidState(state)) {
            throw new IllegalArgumentException("Invalid state: " + state);
        }
        
        Profile profile = profileRepository.findByUserId(userId);
        if (profile == null) {
            throw new IllegalArgumentException("Profile not found for user: " + userId);
        }
        
        profile.setState(state);
        return profileRepository.save(profile);
    }
    
    /**
     * Check if user has verified profile
     * @param userId The user ID
     * @return true if profile is verified
     */
    public boolean hasVerifiedProfile(String userId) {
        Profile profile = profileRepository.findByUserId(userId);
        return profile != null && "verified".equals(profile.getState());
    }
    
    /**
     * Delete user's profile
     * @param userId The user ID
     */
    public void deleteProfile(String userId) {
        profileRepository.deleteByUserId(userId);
    }
    
    /**
     * Validate profile data
     * @param profile The profile to validate
     * @throws IllegalArgumentException if validation fails
     */
    private void validateProfile(Profile profile) {
        if (profile.getFirstName() == null || profile.getFirstName().trim().isEmpty()) {
            throw new IllegalArgumentException("First name is required");
        }
        if (profile.getLastName() == null || profile.getLastName().trim().isEmpty()) {
            throw new IllegalArgumentException("Last name is required");
        }
        if (profile.getDob() == null || profile.getDob().trim().isEmpty()) {
            throw new IllegalArgumentException("Date of birth is required");
        }
        if (!DATE_PATTERN.matcher(profile.getDob()).matches()) {
            throw new IllegalArgumentException("Date of birth must be in YYYY-MM-DD format");
        }
        if (profile.getAddress() == null || profile.getAddress().trim().isEmpty()) {
            throw new IllegalArgumentException("Address is required");
        }
        if (profile.getCity() == null || profile.getCity().trim().isEmpty()) {
            throw new IllegalArgumentException("City is required");
        }
        if (profile.getCountry() == null || profile.getCountry().trim().isEmpty()) {
            throw new IllegalArgumentException("Country is required");
        }
        
        // Validate date of birth (must be at least 18 years old)
        try {
            LocalDateTime dobDate = LocalDateTime.parse(profile.getDob() + "T00:00:00");
            LocalDateTime eighteenYearsAgo = LocalDateTime.now().minusYears(18);
            if (dobDate.isAfter(eighteenYearsAgo)) {
                throw new IllegalArgumentException("Must be at least 18 years old");
            }
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid date of birth format");
        }
    }
    
    /**
     * Check if state is valid
     * @param state The state to check
     * @return true if valid
     */
    private boolean isValidState(String state) {
        return "submitted".equals(state) || "verified".equals(state) || "rejected".equals(state);
    }
}
