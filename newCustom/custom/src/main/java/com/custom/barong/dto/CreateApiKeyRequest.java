package com.custom.barong.dto;

import javax.validation.constraints.NotBlank;

public class CreateApiKeyRequest {
    
    @NotBlank(message = "Algorithm is required")
    private String algorithm = "HS256";
    
    private String scope = "read";
    
    // Optional description/note for the API key
    private String note;

    public CreateApiKeyRequest() {}

    // Getters and setters
    public String getAlgorithm() { return algorithm; }
    public void setAlgorithm(String algorithm) { this.algorithm = algorithm; }

    public String getScope() { return scope; }
    public void setScope(String scope) { this.scope = scope; }

    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
}
