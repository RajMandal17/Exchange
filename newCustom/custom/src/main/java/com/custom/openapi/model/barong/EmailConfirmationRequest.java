package com.custom.openapi.model.barong;

import lombok.Data;

import javax.validation.constraints.NotBlank;

@Data
public class EmailConfirmationRequest {
    @NotBlank
    private String token;
}
