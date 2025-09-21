package com.custom.openapi.model.barong;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class SignUpResponse {
    private String email;
    private String uid;
    private String state;
    private LocalDateTime createdAt;
}
