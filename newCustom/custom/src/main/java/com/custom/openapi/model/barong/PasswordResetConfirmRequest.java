package com.custom.openapi.model.barong;

import lombok.Data;

import javax.validation.constraints.NotBlank;

@Data
public class PasswordResetConfirmRequest {
    @NotBlank
    private String token;
    
    @NotBlank
    private String password;
    
    @NotBlank
    private String passwordConfirmation;
}
