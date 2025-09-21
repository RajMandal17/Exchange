package com.custom.openapi.model.kyc;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class KycStatusResponse {
    private String email;
    private boolean phoneVerified;
    private boolean profileSubmitted;
    private boolean profileVerified;
    private boolean documentsSubmitted;
    private boolean documentsVerified;
    private String overallStatus;     // incomplete, pending, verified
}
