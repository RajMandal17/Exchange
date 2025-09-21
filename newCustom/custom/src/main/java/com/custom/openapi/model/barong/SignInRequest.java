package com.custom.openapi.model.barong;

import lombok.Data;

import javax.validation.constraints.Email;
import javax.validation.constraints.NotBlank;

@Data
public class SignInRequest {
    @NotBlank
    @Email
    private String email;
    
    @NotBlank
    private String password;
    
    private String otpCode;  // Optional for 2FA
}
