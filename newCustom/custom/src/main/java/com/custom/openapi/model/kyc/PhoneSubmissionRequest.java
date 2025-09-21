package com.custom.openapi.model.kyc;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PhoneSubmissionRequest {
    private String country;       // Country code (e.g., "US", "UA")
    private String number;        // Phone number
}
