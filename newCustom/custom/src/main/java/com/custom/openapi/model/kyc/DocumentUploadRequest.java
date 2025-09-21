package com.custom.openapi.model.kyc;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class DocumentUploadRequest {
    private String docType;       // passport, driver_license, identity_card
    private String docNumber;
}
