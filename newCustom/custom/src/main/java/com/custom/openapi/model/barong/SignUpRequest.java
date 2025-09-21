package com.custom.openapi.model.barong;

import lombok.Data;

import javax.validation.constraints.Email;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

@Data
public class SignUpRequest {
    @NotBlank
    @Email
    private String email;
    
    @NotBlank
    @Size(min = 8)
    private String password;
    
    // Optional - frontend handles password confirmation validation
    private String passwordConfirmation;
    
    // Additional fields from frontend
    private String captchaResponse;
    private String referralUid;
    private String refid; // Alternative name for referralUid
    private String data; // JSON string with additional data like language
}
