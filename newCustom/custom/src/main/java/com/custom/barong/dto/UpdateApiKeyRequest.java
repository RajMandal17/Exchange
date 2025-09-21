package com.custom.barong.dto;

public class UpdateApiKeyRequest {
    
    private String scope;
    private String state; // active, inactive
    private String note;

    public UpdateApiKeyRequest() {}

    // Getters and setters
    public String getScope() { return scope; }
    public void setScope(String scope) { this.scope = scope; }

    public String getState() { return state; }
    public void setState(String state) { this.state = state; }

    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
}
