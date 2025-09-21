package com.custom.openapi.model.kyc;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PhoneVerificationRequest {
    private String code;          // Verification code
}
