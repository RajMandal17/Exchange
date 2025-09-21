package com.custom.openapi.model.kyc;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class VerificationCodeResponse {
    private String message;
    private String code;          // For demo purposes only - remove in production
}
