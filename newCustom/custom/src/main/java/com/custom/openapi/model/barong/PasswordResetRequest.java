package com.custom.openapi.model.barong;

import lombok.Data;

import javax.validation.constraints.Email;
import javax.validation.constraints.NotBlank;

@Data
public class PasswordResetRequest {
    @NotBlank
    @Email
    private String email;
    
    private String captchaResponse;
}
