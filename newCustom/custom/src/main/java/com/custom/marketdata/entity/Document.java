package com.custom.marketdata.entity;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class Document {
    private String id;
    private String userId;
    private String docType;           // passport, driver_license, identity_card
    private String docNumber;
    private String fileName;
    private String filePath;
    private String state = "pending"; // pending, verified, rejected
    private String rejectedReason;
    private String reviewedBy;        // Admin user ID who reviewed
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
