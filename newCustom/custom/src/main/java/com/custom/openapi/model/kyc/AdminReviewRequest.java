package com.custom.openapi.model.kyc;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AdminReviewRequest {
    private String state;         // verified, rejected
    private String rejectedReason; // Required if state is "rejected"
}
