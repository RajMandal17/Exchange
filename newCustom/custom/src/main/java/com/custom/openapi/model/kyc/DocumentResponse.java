package com.custom.openapi.model.kyc;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class DocumentResponse {
    private String id;
    private String docType;
    private String docNumber;
    private String fileName;
    private String state;         // pending, verified, rejected
    private String rejectedReason;
    private String createdAt;
    private String updatedAt;
}
