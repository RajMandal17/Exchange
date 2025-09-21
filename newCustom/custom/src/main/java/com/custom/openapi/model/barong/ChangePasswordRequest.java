package com.custom.openapi.model.barong;

import lombok.Data;

import javax.validation.constraints.NotBlank;

@Data
public class ChangePasswordRequest {
    @NotBlank
    private String oldPassword;
    
    @NotBlank
    private String newPassword;
    
    @NotBlank
    private String confirmPassword;
}
