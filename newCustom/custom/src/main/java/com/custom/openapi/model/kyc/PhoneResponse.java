package com.custom.openapi.model.kyc;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PhoneResponse {
    private String id;
    private String country;
    private String number;
    private String validatedAt;   // ISO timestamp when validated
    private String createdAt;
    private String updatedAt;
}
